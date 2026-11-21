import pytest
import torch

from kellvyn_brain.generate import generate_text
from kellvyn_brain.model import (
    KellvynConfig,
    KellvynDecoderLM,
    load_checkpoint,
    save_checkpoint,
)
from kellvyn_brain.tokenizer import ByteBPETokenizer, ByteTokenizer
from kellvyn_brain.train import split_documents


def tiny_config(vocab_size: int = 259, max_seq_len: int = 16) -> KellvynConfig:
    return KellvynConfig(
        vocab_size=vocab_size,
        d_model=32,
        n_layers=1,
        n_heads=2,
        max_seq_len=max_seq_len,
    )


def test_byte_tokenizer_roundtrip_preserves_utf8():
    tok = ByteTokenizer()
    text = "Olá, Kellvyn! ção"
    assert tok.decode(tok.encode(text)) == text


def test_bpe_learns_merges_and_roundtrips():
    tok = ByteBPETokenizer.train("banana bandana banana " * 20, num_merges=8, min_frequency=2)
    assert tok.merge_count > 0
    for doc in ["banana", "bandana ção", ""]:
        assert tok.decode(tok.encode(doc)) == doc


def test_bpe_compresses_repeated_text():
    tok = ByteBPETokenizer.train("banana bandana banana " * 20, num_merges=8, min_frequency=2)
    baseline = ByteTokenizer().encode("banana banana")
    assert len(tok.encode("banana banana")) < len(baseline)


def test_bpe_state_roundtrip_gives_same_encoding():
    tok = ByteBPETokenizer.train("abab abab abab " * 10, num_merges=4)
    again = ByteBPETokenizer.from_dict(tok.to_dict())
    assert again.encode("abab") == tok.encode("abab")


def test_split_rejects_single_document():
    with pytest.raises(ValueError):
        split_documents("um único documento sem linha em branco")


def test_split_removes_duplicates_and_keeps_sets_disjoint():
    text = "\n\n".join(f"documento {i}" for i in range(10)) + "\n\ndocumento 0"
    train, validation, duplicates = split_documents(text, validation_fraction=0.2)
    assert duplicates == 1
    assert not set(train) & set(validation)
    assert len(validation) == 2


def test_forward_shapes_and_finite_loss():
    model = KellvynDecoderLM(tiny_config())
    x = torch.randint(3, 259, (2, 8))
    logits, loss = model(x, x)
    assert logits.shape == (2, 8, 259)
    assert loss is not None and torch.isfinite(loss)


def test_attention_is_causal():
    torch.manual_seed(0)
    model = KellvynDecoderLM(tiny_config()).eval()
    a = torch.randint(3, 259, (1, 10))
    b = a.clone()
    b[0, -1] = 3 if a[0, -1].item() != 3 else 4
    with torch.no_grad():
        la = model.forward_logits(a)
        lb = model.forward_logits(b)
    assert torch.allclose(la[:, :-1], lb[:, :-1], atol=1e-5)


def test_rejects_token_outside_vocabulary():
    model = KellvynDecoderLM(tiny_config())
    with pytest.raises(ValueError):
        model(torch.tensor([[999]]))


def test_checkpoint_roundtrip_and_generation(tmp_path):
    tok = ByteBPETokenizer.train("oi mundo oi mundo " * 10, num_merges=4)
    config = tiny_config(vocab_size=tok.vocab_size, max_seq_len=32)
    model = KellvynDecoderLM(config)
    path = tmp_path / "toy.pt"
    save_checkpoint(path, model, step=3, tokenizer_state=tok.to_dict())

    loaded, step = load_checkpoint(path)
    assert step == 3
    assert loaded.config == config

    text = generate_text(path, "oi", max_new_tokens=5, seed=1)
    assert isinstance(text, str)
