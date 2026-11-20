from __future__ import annotations

import json

import pytest
import torch

from kellvyn_brain.model import KellvynConfig, KellvynDecoderLM, save_checkpoint
from kellvyn_brain.tokenizer import ByteBPETokenizer
from kellvyn_brain.tokenizer_benchmark import benchmark_checkpoint, benchmark_tokenizer


def _make_checkpoint(path, *, external_pretrained_weights: bool = False) -> None:
    sample = "Kellvyn aprende português, lógica e programação. " * 20
    tokenizer = ByteBPETokenizer.train(sample, num_merges=48)
    model = KellvynDecoderLM(
        KellvynConfig(
            vocab_size=tokenizer.vocab_size,
            d_model=32,
            n_layers=1,
            n_heads=4,
            max_seq_len=16,
        )
    )
    save_checkpoint(
        path,
        model,
        step=0,
        tokenizer_state=tokenizer.to_dict(),
        metadata={
            "initialization": "random",
            "external_pretrained_weights": external_pretrained_weights,
            "dataset_sha256": "b" * 64,
        },
    )


def test_tokenizer_benchmark_reports_compression_and_exact_round_trip() -> None:
    text = (
        "Kellvyn entende, analisa e aprende português. " * 20
        + "\n\n"
        + "Ela também testa código e verifica resultados. " * 20
    )
    tokenizer = ByteBPETokenizer.train(text, num_merges=64)
    result = benchmark_tokenizer(tokenizer, text, repeats=2)

    assert result["corpus"]["document_count"] == 2
    assert result["corpus"]["payload_utf8_bytes"] > 0
    assert result["baseline"]["token_count"] == result["corpus"]["payload_utf8_bytes"]
    assert result["baseline"]["round_trip_exact"] is True
    assert result["bpe"]["round_trip_exact"] is True
    assert result["bpe"]["token_count"] < result["baseline"]["token_count"]
    assert result["comparison"]["token_reduction_percent"] > 0
    assert result["measurement"]["repeats"] == 2


def test_benchmark_checkpoint_writes_hashed_json_report(tmp_path) -> None:
    checkpoint = tmp_path / "model.pt"
    corpus = tmp_path / "evaluation.txt"
    output = tmp_path / "reports" / "tokenizer.json"
    _make_checkpoint(checkpoint)
    corpus.write_text(
        "Kellvyn cria sua própria inteligência.\n\n"
        "O tokenizer mede texto em português e código.",
        encoding="utf-8",
    )

    result = benchmark_checkpoint(
        checkpoint,
        corpus,
        output_path=output,
        repeats=1,
    )
    saved = json.loads(output.read_text(encoding="utf-8"))

    assert result["provenance"]["external_pretrained_weights"] is False
    assert len(result["provenance"]["checkpoint_sha256"]) == 64
    assert len(result["provenance"]["corpus_sha256"]) == 64
    assert len(result["provenance"]["tokenizer_sha256"]) == 64
    assert saved == result


def test_benchmark_rejects_empty_corpus() -> None:
    tokenizer = ByteBPETokenizer.train("some training text " * 4, num_merges=8)
    with pytest.raises(ValueError, match="at least one non-empty document"):
        benchmark_tokenizer(tokenizer, "\n\n  \n", repeats=1)


def test_benchmark_rejects_non_positive_repeats() -> None:
    tokenizer = ByteBPETokenizer.train("some training text " * 4, num_merges=8)
    with pytest.raises(ValueError, match="repeats must be positive"):
        benchmark_tokenizer(tokenizer, "valid text", repeats=0)


def test_benchmark_rejects_checkpoint_without_explicit_random_weight_provenance(tmp_path) -> None:
    checkpoint = tmp_path / "model.pt"
    corpus = tmp_path / "evaluation.txt"
    _make_checkpoint(checkpoint, external_pretrained_weights=True)
    corpus.write_text("a valid evaluation corpus", encoding="utf-8")

    with pytest.raises(ValueError, match="external_pretrained_weights=false"):
        benchmark_checkpoint(checkpoint, corpus, repeats=1)
