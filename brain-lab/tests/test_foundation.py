from __future__ import annotations

from collections import Counter

import torch

from kellvyn_brain.model import (
    KellvynConfig,
    KellvynDecoderLM,
    load_checkpoint,
    load_checkpoint_metadata,
    load_tokenizer_state,
    load_training_state,
    restore_optimizer_state,
    save_checkpoint,
)
from kellvyn_brain.tokenizer import ByteBPETokenizer, ByteTokenizer
from kellvyn_brain.train import encode_documents, split_documents


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


def _reference_bpe_train(text: str, num_merges: int, min_frequency: int) -> list[tuple[int, int]]:
    symbols = [byte + ByteBPETokenizer.BYTE_OFFSET for byte in text.encode("utf-8")]
    merges: list[tuple[int, int]] = []
    for _ in range(num_merges):
        pair_counts = Counter(zip(symbols, symbols[1:]))
        if not pair_counts:
            break
        pair, frequency = min(
            pair_counts.items(),
            key=lambda item: (-item[1], item[0][0], item[0][1]),
        )
        if frequency < min_frequency:
            break
        merges.append(pair)
        merged_id = ByteBPETokenizer.BASE_VOCAB_SIZE + len(merges) - 1
        merged: list[int] = []
        index = 0
        while index < len(symbols):
            if (
                index + 1 < len(symbols)
                and symbols[index] == pair[0]
                and symbols[index + 1] == pair[1]
            ):
                merged.append(merged_id)
                index += 2
            else:
                merged.append(symbols[index])
                index += 1
        symbols = merged
    return merges


def _reference_bpe_encode(
    text: str,
    merges: list[tuple[int, int]],
    *,
    add_bos: bool = True,
    add_eos: bool = True,
) -> list[int]:
    symbols = [byte + ByteBPETokenizer.BYTE_OFFSET for byte in text.encode("utf-8")]
    for merge_index, pair in enumerate(merges):
        merged_id = ByteBPETokenizer.BASE_VOCAB_SIZE + merge_index
        merged: list[int] = []
        index = 0
        while index < len(symbols):
            if (
                index + 1 < len(symbols)
                and symbols[index] == pair[0]
                and symbols[index + 1] == pair[1]
            ):
                merged.append(merged_id)
                index += 2
            else:
                merged.append(symbols[index])
                index += 1
        symbols = merged
    if add_bos:
        symbols.insert(0, ByteBPETokenizer.BOS_ID)
    if add_eos:
        symbols.append(ByteBPETokenizer.EOS_ID)
    return symbols


def make_bpe_fixture():
    sample = "Kellvyn cria, testa e aprende. "
    tokenizer = ByteBPETokenizer.train(sample * 20, num_merges=16)
    model = tiny_model(vocab_size=tokenizer.vocab_size).eval()
    sample_ids = tokenizer.encode("Kellvyn")
    inputs = torch.tensor([sample_ids[:8]], dtype=torch.long)
    with torch.no_grad():
        expected, _ = model(inputs)
    return model, inputs, expected, tokenizer


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


def test_incremental_bpe_matches_reference_for_overlapping_and_utf8_text() -> None:
    training_samples = [
        "aaaaaaaaaaaaabaaaaaaaaaaaaab",
        "abababababababababababababab",
        "Kellvyn cria, testa e aprende: ação, cérebro, lógica e programação. " * 5,
        "abcabcabdabcabcabdabcabcabd",
    ]
    probe_samples = [
        "",
        "aaaaabaaaaab",
        "abababa",
        "Kellvyn: ação, coração e raciocínio — português.",
        "xyzxyzabcabc",
    ]
    for training_text in training_samples:
        expected_merges = _reference_bpe_train(
            training_text,
            num_merges=40,
            min_frequency=2,
        )
        tokenizer = ByteBPETokenizer.train(
            training_text,
            num_merges=40,
            min_frequency=2,
        )
        assert list(tokenizer.merges) == expected_merges
        for probe in probe_samples:
            assert tokenizer.encode(probe) == _reference_bpe_encode(probe, expected_merges)
            assert tokenizer.decode(tokenizer.encode(probe)) == probe


def test_bpe_tokenizer_serialization() -> None:
    sample = "cérebro próprio Kellvyn aprende e testa"
    trained = ByteBPETokenizer.train(sample * 10, num_merges=24)
    restored = ByteBPETokenizer.from_dict(trained.to_dict())
    assert restored.to_dict() == trained.to_dict()
    assert restored.decode(restored.encode(sample)) == sample


def test_document_split_is_reproducible_disjoint_and_deduplicated() -> None:
    documents = [f"document-{index}" for index in range(20)] + ["document-3", "document-8"]
    corpus = "\n\n".join(documents)
    train_a, validation_a, removed_a = split_documents(
        corpus, validation_fraction=0.2, seed=7
    )
    train_b, validation_b, removed_b = split_documents(
        corpus, validation_fraction=0.2, seed=7
    )
    assert train_a == train_b
    assert validation_a == validation_b
    assert removed_a == removed_b == 2
    assert set(train_a).isdisjoint(validation_a)
    assert len(train_a) + len(validation_a) == 20


def test_document_split_rejects_one_document() -> None:
    try:
        split_documents("only one document", validation_fraction=0.1, seed=1)
    except ValueError as error:
        assert "two documents separated by blank lines" in str(error)
    else:
        raise AssertionError("expected a one-document corpus to be rejected")


def test_documents_encode_with_bos_and_eos_boundaries() -> None:
    tokenizer = ByteBPETokenizer.train("first document second document " * 5, num_merges=8)
    encoded = encode_documents(["first document", "second document"], tokenizer).tolist()
    assert encoded[0] == tokenizer.BOS_ID
    assert encoded.count(tokenizer.BOS_ID) == 2
    assert encoded.count(tokenizer.EOS_ID) == 2


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
    for left, right in zip(first.parameters(), second.parameters()):
        torch.testing.assert_close(left, right)


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


def test_checkpoint_round_trip_and_optimizer_resume(tmp_path) -> None:
    model, inputs, _, tokenizer = make_bpe_fixture()
    optimizer = torch.optim.AdamW(model.parameters(), lr=1e-3)
    model.train()
    targets = torch.randint(0, model.config.vocab_size, inputs.shape)
    _, loss = model(inputs, targets)
    assert loss is not None
    loss.backward()
    optimizer.step()
    model.eval()
    with torch.no_grad():
        expected, _ = model(inputs)

    metadata = {
        "external_pretrained_weights": False,
        "dataset_sha256": "a" * 64,
        "train_document_count": 9,
        "validation_document_count": 1,
        "duplicate_document_count_removed": 2,
    }
    checkpoint = tmp_path / "kellvyn-test.pt"
    expected_rng_state = torch.get_rng_state().clone()
    save_checkpoint(
        checkpoint,
        model,
        step=7,
        tokenizer_state=tokenizer.to_dict(),
        optimizer_state=optimizer.state_dict(),
        metadata=metadata,
        training_state={
            "version": 1,
            "step": 7,
            "rng": {
                "version": 1,
                "python_random": __import__("random").getstate(),
                "torch_cpu": expected_rng_state,
                "torch_cuda_all": [],
                "torch_mps": None,
            },
        },
    )

    restored, step = load_checkpoint(checkpoint)
    restored.eval()
    with torch.no_grad():
        actual, _ = restored(inputs)
    assert step == 7
    torch.testing.assert_close(expected, actual)
    saved_training_state = load_training_state(checkpoint)
    assert saved_training_state["step"] == 7
    assert saved_training_state["rng"]["version"] == 1
    assert isinstance(saved_training_state["rng"]["python_random"], tuple)
    assert torch.equal(saved_training_state["rng"]["torch_cpu"], expected_rng_state)

    restored_optimizer = torch.optim.AdamW(restored.parameters(), lr=1e-3)
    restore_optimizer_state(checkpoint, restored_optimizer)
    assert len(restored_optimizer.state) > 0
    assert ByteBPETokenizer.from_dict(load_tokenizer_state(checkpoint)).to_dict() == tokenizer.to_dict()
    assert load_checkpoint_metadata(checkpoint) == metadata


def test_model_rejects_too_long_input() -> None:
    model = tiny_model()
    too_long = torch.zeros((1, model.config.max_seq_len + 1), dtype=torch.long)
    try:
        model(too_long)
    except ValueError as error:
        assert "max_seq_len" in str(error)
    else:
        raise AssertionError("expected max sequence length validation to fail")


def test_generate_returns_prompt_and_new_tokens() -> None:
    model = tiny_model().eval()
    prompt = torch.tensor([[1, 5, 6]], dtype=torch.long)
    generated = model.generate(prompt, max_new_tokens=4, temperature=1.0, top_k=8)
    assert generated.shape == (1, 7)
    assert torch.equal(generated[:, :3], prompt)



def test_checkpoint_without_training_state_is_not_resumable(tmp_path) -> None:
    model, _, _, tokenizer = make_bpe_fixture()
    checkpoint = tmp_path / "non-resumable.pt"
    save_checkpoint(
        checkpoint,
        model,
        step=0,
        tokenizer_state=tokenizer.to_dict(),
        metadata={"external_pretrained_weights": False},
    )
    try:
        load_training_state(checkpoint)
    except ValueError as error:
        assert "resumable training state" in str(error)
    else:
        raise AssertionError("expected checkpoint without RNG state to be rejected")


def test_interrupted_training_resumes_to_same_cpu_weights_as_continuous_run(tmp_path) -> None:
    import json
    from kellvyn_brain.train import build_parser, train

    corpus = tmp_path / "toy.txt"
    corpus.write_text(
        "alpha document with several words\n\n"
        "beta document with several words\n\n"
        "gamma document with several words\n\n"
        "delta document with several words\n\n"
        "epsilon document with several words",
        encoding="utf-8",
    )

    common = [
        "--data", str(corpus),
        "--allow-unverified-data",
        "--batch-size", "1",
        "--seq-len", "4",
        "--width", "16",
        "--layers", "1",
        "--heads", "2",
        "--learning-rate", "0.001",
        "--weight-decay", "0.0",
        "--grad-clip", "1.0",
        "--eval-every", "1",
        "--checkpoint-every", "2",
        "--validation-fraction", "0.2",
        "--bpe-merges", "8",
        "--seed", "13",
        "--device", "cpu",
    ]

    continuous_path = tmp_path / "continuous.pt"
    train(build_parser().parse_args(common + [
        "--checkpoint", str(continuous_path),
        "--steps", "4",
    ]))

    resumed_path = tmp_path / "resumed.pt"
    train(build_parser().parse_args(common + [
        "--checkpoint", str(resumed_path),
        "--steps", "2",
    ]))
    train(build_parser().parse_args(common + [
        "--checkpoint", str(resumed_path),
        "--resume-from", str(resumed_path),
        "--steps", "4",
    ]))

    continuous_model, continuous_step = load_checkpoint(continuous_path)
    resumed_model, resumed_step = load_checkpoint(resumed_path)
    assert continuous_step == resumed_step == 4
    for expected, actual in zip(continuous_model.parameters(), resumed_model.parameters()):
        torch.testing.assert_close(expected, actual, rtol=0, atol=0)

    metrics = resumed_path.with_suffix(resumed_path.suffix + ".metrics.jsonl")
    records = [json.loads(line) for line in metrics.read_text(encoding="utf-8").splitlines()]
    assert records[-1]["step"] == 4
    assert records[-1]["tokens_seen"] == 16
