from __future__ import annotations

import torch

from kellvyn_brain.model import (
    KellvynConfig,
    KellvynDecoderLM,
    load_checkpoint,
    save_checkpoint,
)
from kellvyn_brain.tokenizer import ByteTokenizer


def tiny_model() -> KellvynDecoderLM:
    return KellvynDecoderLM(
        KellvynConfig(
            vocab_size=ByteTokenizer().VOCAB_SIZE,
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


def test_checkpoint_round_trip(tmp_path) -> None:
    torch.manual_seed(11)
    model = tiny_model().eval()
    sample = torch.randint(0, model.config.vocab_size, (1, 8))
    with torch.no_grad():
        expected, _ = model(sample)
    path = tmp_path / "kellvyn-test.pt"
    save_checkpoint(path, model, step=7)
    restored, step = load_checkpoint(path)
    restored.eval()
    with torch.no_grad():
        actual, _ = restored(sample)
    assert step == 7
    torch.testing.assert_close(expected, actual)


def test_model_rejects_too_long_input() -> None:
    model = tiny_model()
    too_long = torch.zeros((1, model.config.max_seq_len + 1), dtype=torch.long)
    try:
        model(too_long)
    except ValueError as error:
        assert "max_seq_len" in str(error)
    else:
        raise AssertionError("expected max sequence length validation to fail")
