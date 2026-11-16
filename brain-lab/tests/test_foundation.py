from __future__ import annotations

import torch

from kellvyn_brain.model import (
    KellvynConfig,
    KellvynDecoderLM,
    load_checkpoint,
    load_tokenizer_state,
    save_checkpoint,
)
from kellvyn_brain.tokenizer import ByteBPETokenizer, ByteTokenizer


def tiny_model(vocab_size: int = ByteTokenizer().VOCAB_SIZE) -> KellvynDecoderLM:
    return KellvynDecoderLM(
        KellvynConfig(
            vocab_size=vocab_size,
            d_model=32,
            n_layers=2,
            n_heads=4,
            max_seq_len=16,
            dropout=0.0,
        )
    )


def test_byte_tokenizer_round_trip_portuguese() -> None:
    tokenizer = ByteTokenizer()
    sample = "Kellvyn: cérebro próprio — ação, razão e criação."
    ids = tokenizer.encode(sample)
    assert tokenizer.decode(ids) == sample
    assert min(ids) >= tokenizer.BOS_ID
    assert max(ids) < tokenizer.VOCAB_SIZE


def test_bpe_tokenizer_round_trip_and_compression() -> None:
    sample = ("Kellvyn aprende linguagem, lógica e programação. " * 20)
    tokenizer = ByteBPETokenizer.train(sample, num_merges=48)
    ids = tokenizer.encode(sample)
    assert tokenizer.decode(ids) == sample
    assert tokenizer.merge_count > 0
    assert len(ids) < len(ByteTokenizer().encode(sample))
    assert max(ids) < tokenizer.vocab_size


def test_bpe_tokenizer_serialization() -> None:
    sample = "cérebro próprio Kellvyn aprende e testa"
    trained = ByteBPETokenizer.train(sample * 10, num_merges=24)
    restored = ByteBPETokenizer.from_dict(trained.to_dict())
    assert restored.to_dict() == trained.to_dict()
    assert restored.decode(restored.encode(sample)) == sample


def test_model_forward_returns_finite_loss() -> None:
    model = tiny_model()
    input_ids = torch.randint(0, model.config.vocab_size, (2, 12))
    targets = torch.randint(0, model.config.vocab_size, (2, 12))
    logits, loss = model(input_ids, targets)
    assert logits.shape == (2, 12, model.config.vocab_size)
    assert loss is not None
    assert torch.isfinite(loss)


def test_attention_is_causal() -> None:
    torch.manual_seed(3)
    model = tiny_model().eval()
    original = torch.randint(3, model.config.vocab_size, (1, 10))
    changed_future = original.clone()
    changed_future[:, 1:] = torch.randint(
        3, model.config.vocab_size, changed_future[:, 1:].shape
    )
    with torch.no_grad():
        first_logits, _ = model(original)
        changed_logits, _ = model(changed_future)
    torch.testing.assert_close(first_logits[:, :1], changed_logits[:, :1])


def test_checkpoint_round_trip_with_tokenizer() -> None:
    tokenizer = ByteBPETokenizer.train("Kellvyn cria, testa e aprende. " * 10, num_merges=16)
    model = tiny_model(vocab_size=tokenizer.vocab_size).eval()
    sample = torch.tensor([tokenizer.encode("Kellvyn")[:8]], dtype=torch.long)
    with torch.no_grad():
        expected, _ = model(sample)
    return model, sample, expected, tokenizer


def test_checkpoint_round_trip(tmp_path) -> None:
    model, sample, expected, tokenizer = test_checkpoint_round_trip_with_tokenizer()
    path = tmp_path / "kellvyn-test.pt"
    save_checkpoint(path, model, step=7, tokenizer_state=tokenizer.to_dict())
    restored, step = load_checkpoint(path)
    restored.eval()
    with torch.no_grad():
        actual, _ = restored(sample)
    assert step == 7
    torch.testing.assert_close(expected, actual)
    saved_tokenizer = ByteBPETokenizer.from_dict(load_tokenizer_state(path))
    assert saved_tokenizer.to_dict() == tokenizer.to_dict()


def test_model_rejects_too_long_input() -> None:
    model = tiny_model()
    too_long = torch.zeros((1, model.config.max_seq_len + 1), dtype=torch.long)
    try:
        model(too_long)
    except ValueError as error:
        assert "max_seq_len" in str(error)
    else:
        raise AssertionError("expected max sequence length validation to fail")
