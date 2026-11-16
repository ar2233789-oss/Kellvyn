from __future__ import annotations

import torch

from kellvyn_brain.model import (
    KellvynConfig,
    KellvynDecoderLM,
    load_checkpoint,
    load_checkpoint_metadata,
    restore_optimizer_state,
    save_checkpoint,
)
from kellvyn_brain.tokenizer import ByteTokenizer
from kellvyn_brain.train import split_documents


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


def test_document_split_is_reproducible_and_disjoint() -> None:
    documents = [f"document-{index}" for index in range(20)]
    train_a, validation_a = split_documents(
        documents, validation_fraction=0.2, seed=7
    )
    train_b, validation_b = split_documents(
        documents, validation_fraction=0.2, seed=7
    )
    assert train_a == train_b
    assert validation_a == validation_b
    assert set(train_a).isdisjoint(validation_a)
    assert set(train_a) | set(validation_a) == set(documents)


def test_document_split_rejects_one_document() -> None:
    try:
        split_documents(["one"], validation_fraction=0.1, seed=1)
    except ValueError as error:
        assert "two non-empty documents" in str(error)
    else:
        raise AssertionError("expected one-document corpus to be rejected")


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


def test_model_initialization_is_seed_reproducible() -> None:
    torch.manual_seed(91)
    first = tiny_model()
    torch.manual_seed(91)
    second = tiny_model()
    for first_parameter, second_parameter in zip(first.parameters(), second.parameters()):
        torch.testing.assert_close(first_parameter, second_parameter)


def test_training_step_updates_parameters() -> None:
    torch.manual_seed(13)
    model = tiny_model()
    optimizer = torch.optim.AdamW(model.parameters(), lr=1e-3)
    inputs = torch.randint(0, model.config.vocab_size, (2, 12))
    targets = torch.randint(0, model.config.vocab_size, (2, 12))
    before = [parameter.detach().clone() for parameter in model.parameters()]
    _, loss = model(inputs, targets)
    assert loss is not None
    loss.backward()
    optimizer.step()
    assert any(
        not torch.equal(old, new.detach())
        for old, new in zip(before, model.parameters())
    )


def test_checkpoint_round_trip_and_optimizer_metadata(tmp_path) -> None:
    torch.manual_seed(11)
    model = tiny_model().eval()
    optimizer = torch.optim.AdamW(model.parameters(), lr=1e-3)
    sample = torch.randint(0, model.config.vocab_size, (1, 8))
    with torch.no_grad():
        expected, _ = model(sample)

    # Populate optimizer state so the checkpoint test covers a resumable optimizer.
    model.train()
    _, loss = model(sample, torch.randint(0, model.config.vocab_size, sample.shape))
    assert loss is not None
    loss.backward()
    optimizer.step()
    model.eval()
    with torch.no_grad():
        expected, _ = model(sample)

    path = tmp_path / "kellvyn-test.pt"
    metadata = {"dataset_sha256": "test-hash", "external_pretrained_weights": False}
    save_checkpoint(path, model, step=7, optimizer=optimizer, metadata=metadata)
    restored, step = load_checkpoint(path)
    restored.eval()
    with torch.no_grad():
        actual, _ = restored(sample)
    assert step == 7
    torch.testing.assert_close(expected, actual)

    restored_optimizer = torch.optim.AdamW(restored.parameters(), lr=1e-3)
    restore_optimizer_state(path, restored_optimizer)
    assert len(restored_optimizer.state) > 0
    assert load_checkpoint_metadata(path) == metadata


def test_model_rejects_too_long_input() -> None:
    model = tiny_model()
    too_long = torch.zeros((1, model.config.max_seq_len + 1), dtype=torch.long)
    try:
        model(too_long)
    except ValueError as error:
        assert "max_seq_len" in str(error)
    else:
        raise AssertionError("expected max sequence length validation to fail")


def test_generate_returns_prompt_plus_new_tokens() -> None:
    model = tiny_model().eval()
    prompt = torch.tensor([[1, 5, 6]], dtype=torch.long)
    result = model.generate(prompt, max_new_tokens=4, temperature=1.0, top_k=8)
    assert result.shape == (1, 7)
    assert torch.equal(result[:, :3], prompt)
