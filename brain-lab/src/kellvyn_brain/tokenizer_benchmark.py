"""Reproducible BPE-vs-byte tokenizer benchmark for Kellvyn checkpoints."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import statistics
import time
from pathlib import Path
from typing import Callable

from .model import load_checkpoint_metadata, load_tokenizer_state
from .tokenizer import ByteBPETokenizer, ByteTokenizer


def _documents(text: str) -> list[str]:
    normalized = text.replace("\r\n", "\n").replace("\r", "\n")
    return [
        part.strip()
        for part in re.split(r"\n\s*\n", normalized)
        if part.strip()
    ]


def _median_encode_time(
    encoder: Callable[[], list[list[int]]],
    repeats: int,
) -> float:
    samples: list[float] = []
    for _ in range(repeats):
        started = time.perf_counter()
        encoder()
        samples.append(time.perf_counter() - started)
    return statistics.median(samples)


def benchmark_tokenizer(
    tokenizer: ByteBPETokenizer,
    text: str,
    *,
    repeats: int = 3,
) -> dict[str, object]:
    """Compare a trained BPE tokenizer with one-token-per-UTF-8-byte baseline."""
    if not isinstance(tokenizer, ByteBPETokenizer):
        raise TypeError("tokenizer must be a ByteBPETokenizer")
    if not isinstance(text, str):
        raise TypeError("text must be str")
    if repeats <= 0:
        raise ValueError("repeats must be positive")

    documents = _documents(text)
    if not documents:
        raise ValueError("benchmark corpus must contain at least one non-empty document")

    payload_bytes = sum(len(document.encode("utf-8")) for document in documents)
    if payload_bytes == 0:
        raise ValueError("benchmark corpus contains no UTF-8 payload bytes")

    byte_tokenizer = ByteTokenizer()

    def encode_bpe() -> list[list[int]]:
        return [
            tokenizer.encode(document, add_bos=False, add_eos=False)
            for document in documents
        ]

    def encode_bytes() -> list[list[int]]:
        return [
            byte_tokenizer.encode(document, add_bos=False, add_eos=False)
            for document in documents
        ]

    bpe_ids = encode_bpe()
    byte_ids = encode_bytes()

    round_trip_exact = all(
        tokenizer.decode(ids) == document
        for ids, document in zip(bpe_ids, documents)
    )
    if not round_trip_exact:
        raise RuntimeError("BPE tokenizer failed exact UTF-8 document round-trip")

    byte_round_trip_exact = all(
        byte_tokenizer.decode(ids) == document
        for ids, document in zip(byte_ids, documents)
    )
    if not byte_round_trip_exact:
        raise RuntimeError("byte baseline failed exact UTF-8 document round-trip")

    bpe_token_count = sum(len(ids) for ids in bpe_ids)
    byte_token_count = sum(len(ids) for ids in byte_ids)
    bpe_seconds = _median_encode_time(encode_bpe, repeats)
    byte_seconds = _median_encode_time(encode_bytes, repeats)

    reduction = 100.0 * (1.0 - bpe_token_count / byte_token_count)
    return {
        "schema_version": 1,
        "corpus": {
            "document_count": len(documents),
            "payload_utf8_bytes": payload_bytes,
            "document_boundaries_preserved": True,
        },
        "baseline": {
            "tokenizer": "byte-level",
            "token_count": byte_token_count,
            "tokens_per_byte": byte_token_count / payload_bytes,
            "median_encode_ms": byte_seconds * 1000.0,
            "bytes_per_second": payload_bytes / byte_seconds if byte_seconds > 0 else None,
            "round_trip_exact": byte_round_trip_exact,
        },
        "bpe": {
            "tokenizer_format": tokenizer.FORMAT,
            "vocab_size": tokenizer.vocab_size,
            "merge_count": tokenizer.merge_count,
            "token_count": bpe_token_count,
            "tokens_per_byte": bpe_token_count / payload_bytes,
            "bytes_per_token": payload_bytes / bpe_token_count if bpe_token_count else None,
            "median_encode_ms": bpe_seconds * 1000.0,
            "bytes_per_second": payload_bytes / bpe_seconds if bpe_seconds > 0 else None,
            "round_trip_exact": round_trip_exact,
        },
        "comparison": {
            "token_reduction_percent": reduction,
            "compression_ratio_vs_byte_baseline": (
                byte_token_count / bpe_token_count if bpe_token_count else None
            ),
        },
        "measurement": {
            "repeats": repeats,
            "timing_note": "Throughput depends on the host CPU and runtime; compare runs on the same machine.",
        },
    }


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def benchmark_checkpoint(
    checkpoint_path: str | Path,
    corpus_path: str | Path,
    *,
    output_path: str | Path | None = None,
    repeats: int = 3,
) -> dict[str, object]:
    checkpoint = Path(checkpoint_path)
    corpus_file = Path(corpus_path)
    if not checkpoint.is_file():
        raise FileNotFoundError(f"checkpoint not found: {checkpoint}")
    if not corpus_file.is_file():
        raise FileNotFoundError(f"corpus file not found: {corpus_file}")

    checkpoint_metadata = load_checkpoint_metadata(checkpoint)
    if checkpoint_metadata.get("external_pretrained_weights") is not False:
        raise ValueError(
            "checkpoint metadata must explicitly declare external_pretrained_weights=false"
        )
    tokenizer_state = load_tokenizer_state(checkpoint)
    tokenizer = ByteBPETokenizer.from_dict(tokenizer_state)

    raw_corpus = corpus_file.read_bytes()
    text = raw_corpus.decode("utf-8")
    result = benchmark_tokenizer(tokenizer, text, repeats=repeats)
    tokenizer_bytes = json.dumps(
        tokenizer_state,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")

    result["provenance"] = {
        "checkpoint_sha256": _sha256_file(checkpoint),
        "corpus_sha256": hashlib.sha256(raw_corpus).hexdigest(),
        "tokenizer_sha256": hashlib.sha256(tokenizer_bytes).hexdigest(),
        "external_pretrained_weights": False,
        "weights_origin": "random_initialization_and_kellvyn_training_only",
    }

    if output_path is not None:
        destination = Path(output_path)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(
            json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False) + "\n",
            encoding="utf-8",
        )
    return result


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Benchmark a Kellvyn checkpoint tokenizer against byte-level encoding"
    )
    parser.add_argument("--checkpoint", required=True, help="Kellvyn .pt checkpoint")
    parser.add_argument("--data", required=True, help="UTF-8 corpus; documents separated by blank lines")
    parser.add_argument("--output", default="./runs/tokenizer-benchmark.json")
    parser.add_argument("--repeats", type=int, default=3)
    return parser


def main() -> None:
    args = build_parser().parse_args()
    result = benchmark_checkpoint(
        args.checkpoint,
        args.data,
        output_path=args.output,
        repeats=args.repeats,
    )
    print(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False))


if __name__ == "__main__":
    main()
