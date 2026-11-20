"""Reproducible, resumable next-token trainer for the Kellvyn prototype."""

from __future__ import annotations

import argparse
import hashlib
import json
import random
import time
from pathlib import Path
from typing import Any, Sequence

import torch

from .corpus_manifest import validate_corpus_manifest
from .model import (
    KellvynConfig,
    KellvynDecoderLM,
    load_training_checkpoint,
    save_checkpoint,
)
from .tokenizer import ByteBPETokenizer


def choose_device(requested: str) -> torch.device:
    if requested != "auto":
        device = torch.device(requested)
        if device.type == "cuda" and not torch.cuda.is_available():
            raise RuntimeError("CUDA was requested but is unavailable")
        if device.type == "mps" and not torch.backends.mps.is_available():
            raise RuntimeError("MPS was requested but is unavailable")
        return device
    if torch.cuda.is_available():
        return torch.device("cuda")
    if hasattr(torch.backends, "mps") and torch.backends.mps.is_available():
        return torch.device("mps")
    return torch.device("cpu")


def split_documents(
    text: str,
    *,
    validation_fraction: float = 0.1,
    seed: int = 17,
) -> tuple[list[str], list[str], int]:
    """Deduplicate, shuffle and split complete documents before tokenization."""
    if not 0.0 < validation_fraction < 1.0:
        raise ValueError("validation_fraction must be between 0 and 1")
    normalized = text.replace("\r\n", "\n").replace("\r", "\n")
    raw_documents = [
        part.strip()
        for part in __import__("re").split(r"\n\s*\n", normalized)
        if part.strip()
    ]
    if len(raw_documents) < 2:
        raise ValueError(
            "At least two documents separated by blank lines are required; "
            "a character-level split would leak document context."
        )

    unique_documents = list(dict.fromkeys(raw_documents))
    duplicate_count = len(raw_documents) - len(unique_documents)
    if len(unique_documents) < 2:
        raise ValueError("At least two distinct documents are required after deduplication")

    shuffled = unique_documents.copy()
    random.Random(seed).shuffle(shuffled)
    validation_count = max(
        1,
        min(len(shuffled) - 1, round(len(shuffled) * validation_fraction)),
    )
    validation_documents = shuffled[:validation_count]
    training_documents = shuffled[validation_count:]
    if set(training_documents).intersection(validation_documents):
        raise RuntimeError("training and validation documents overlap")
    return training_documents, validation_documents, duplicate_count


def encode_documents(
    documents: Sequence[str],
    tokenizer: ByteBPETokenizer,
) -> torch.Tensor:
    """Keep explicit BOS/EOS boundaries for every independently split document."""
    encoded: list[int] = []
    for document in documents:
        encoded.extend(tokenizer.encode(document, add_bos=True, add_eos=True))
    if len(encoded) < 3:
        raise ValueError("encoded document set is too small")
    return torch.tensor(encoded, dtype=torch.long)


def make_batch(
    tokens: torch.Tensor,
    *,
    batch_size: int,
    seq_len: int,
    device: torch.device,
) -> tuple[torch.Tensor, torch.Tensor]:
    max_start = tokens.numel() - seq_len - 1
    if max_start < 1:
        raise ValueError(
            f"dataset split needs at least {seq_len + 2} tokens; got {tokens.numel()}"
        )
    starts = torch.randint(0, max_start + 1, (batch_size,))
    inputs = torch.stack([tokens[start : start + seq_len] for start in starts])
    targets = torch.stack([tokens[start + 1 : start + seq_len + 1] for start in starts])
    return inputs.to(device), targets.to(device)


@torch.no_grad()
def estimate_loss(
    model: KellvynDecoderLM,
    tokens: torch.Tensor,
    *,
    batch_size: int,
    seq_len: int,
    device: torch.device,
    batches: int = 4,
) -> float:
    if batches <= 0:
        raise ValueError("batches must be positive")
    previous_mode = model.training
    model.eval()
    losses: list[float] = []
    try:
        for _ in range(batches):
            inputs, targets = make_batch(
                tokens,
                batch_size=batch_size,
                seq_len=seq_len,
                device=device,
            )
            _, loss = model(inputs, targets)
            if loss is None or not torch.isfinite(loss):
                raise RuntimeError("validation produced a non-finite loss")
            losses.append(float(loss.item()))
    finally:
        model.train(previous_mode)
    return sum(losses) / len(losses)


def _training_fingerprint(args: argparse.Namespace, device: torch.device) -> dict[str, Any]:
    keys = (
        "batch_size", "seq_len", "width", "layers", "heads", "dropout",
        "learning_rate", "weight_decay", "grad_clip", "validation_fraction",
        "bpe_merges", "bpe_min_frequency", "seed",
    )
    return {key: getattr(args, key) for key in keys} | {"device": str(device)}


def _capture_rng_state(device: torch.device) -> dict[str, Any]:
    state: dict[str, Any] = {
        "version": 1,
        "python_random": random.getstate(),
        "torch_cpu": torch.get_rng_state(),
        "torch_cuda_all": torch.cuda.get_rng_state_all() if torch.cuda.is_available() else [],
        "torch_mps": None,
    }
    if device.type == "mps" and hasattr(torch, "mps") and hasattr(torch.mps, "get_rng_state"):
        state["torch_mps"] = torch.mps.get_rng_state()
    return state


def _restore_rng_state(state: dict[str, Any], device: torch.device) -> None:
    if state.get("version") != 1:
        raise ValueError("unsupported checkpoint RNG state version")
    python_state = state.get("python_random")
    cpu_state = state.get("torch_cpu")
    if not isinstance(python_state, tuple) or not isinstance(cpu_state, torch.Tensor):
        raise ValueError("checkpoint RNG state is incomplete")
    random.setstate(python_state)
    torch.set_rng_state(cpu_state.cpu())

    if device.type == "cuda":
        cuda_states = state.get("torch_cuda_all")
        if not isinstance(cuda_states, list) or not cuda_states:
            raise ValueError("checkpoint has no CUDA RNG state for CUDA resume")
        if len(cuda_states) != torch.cuda.device_count():
            raise ValueError("CUDA device count differs from checkpoint RNG state")
        torch.cuda.set_rng_state_all(cuda_states)
    elif device.type == "mps":
        mps_state = state.get("torch_mps")
        if not isinstance(mps_state, torch.Tensor) or not hasattr(torch.mps, "set_rng_state"):
            raise ValueError("checkpoint has no compatible MPS RNG state")
        torch.mps.set_rng_state(mps_state)


def _append_metric(path: Path, record: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(record, ensure_ascii=False, allow_nan=False) + "\n")


def train(args: argparse.Namespace) -> None:
    if args.steps <= 0 or args.batch_size <= 0 or args.seq_len <= 1:
        raise ValueError("steps/batch-size must be positive and seq-len must exceed 1")
    if args.eval_every <= 0 or args.checkpoint_every <= 0 or args.learning_rate <= 0 or args.grad_clip <= 0:
        raise ValueError("eval-every, checkpoint-every, learning-rate and grad-clip must be positive")
    if args.weight_decay < 0:
        raise ValueError("weight-decay cannot be negative")

    manifest_report = None
    manifest_root: Path | None = None
    if args.manifest:
        if args.allow_unverified_data:
            raise ValueError("--manifest and --allow-unverified-data cannot be used together")
        manifest_root = Path(args.manifest_root) if args.manifest_root else Path(args.manifest).parent
        manifest_report = validate_corpus_manifest(args.manifest, root_dir=manifest_root)
        expected_data = (manifest_root / manifest_report["training_corpus"]["file"]).resolve()
        if Path(args.data).resolve() != expected_data:
            raise ValueError("--data must match the exact training_corpus.file in --manifest")
    elif not args.allow_unverified_data:
        raise ValueError(
            "production training requires --manifest; use --allow-unverified-data only "
            "for synthetic smoke tests and local pipeline checks"
        )

    source = Path(args.data)
    if not source.is_file():
        raise FileNotFoundError(f"training text file not found: {source}")
    raw_data = source.read_bytes()
    actual_dataset_sha256 = hashlib.sha256(raw_data).hexdigest()
    if manifest_report is not None and actual_dataset_sha256 != manifest_report["training_corpus"]["sha256"]:
        raise ValueError("training data changed after manifest validation")
    text = raw_data.decode("utf-8")

    train_documents, validation_documents, duplicate_count = split_documents(
        text,
        validation_fraction=args.validation_fraction,
        seed=args.seed,
    )
    train_text = "\n\n".join(train_documents)

    device = choose_device(args.device)
    resume_path = Path(args.resume_from) if args.resume_from else None

    if resume_path is None:
        random.seed(args.seed)
        torch.manual_seed(args.seed)
        if torch.cuda.is_available():
            torch.cuda.manual_seed_all(args.seed)
        tokenizer = ByteBPETokenizer.train(
            train_text,
            num_merges=args.bpe_merges,
            min_frequency=args.bpe_min_frequency,
        )
        model_config = KellvynConfig(
            vocab_size=tokenizer.vocab_size,
            d_model=args.width,
            n_layers=args.layers,
            n_heads=args.heads,
            max_seq_len=args.seq_len,
            dropout=args.dropout,
        )
        model_config.validate()
        model = KellvynDecoderLM(model_config).to(device)
        optimizer = torch.optim.AdamW(
            model.parameters(),
            lr=args.learning_rate,
            weight_decay=args.weight_decay,
        )
        start_step = 0
        last_validation_loss: float | None = None
        resumed_from = None
    else:
        if not resume_path.is_file():
            raise FileNotFoundError(f"resume checkpoint not found: {resume_path}")
        (
            model,
            start_step,
            optimizer_state,
            metadata,
            tokenizer_state,
            saved_training_state,
            weights_origin,
        ) = load_training_checkpoint(resume_path, map_location="cpu")
        model = model.to(device)
        tokenizer = ByteBPETokenizer.from_dict(tokenizer_state)

        if weights_origin != "random_initialization_and_kellvyn_training_only":
            raise ValueError("resume checkpoint has an untrusted weights origin")
        if metadata.get("external_pretrained_weights") is not False:
            raise ValueError("resume checkpoint does not prove random initialization/no external weights")
        if metadata.get("dataset_sha256") != actual_dataset_sha256:
            raise ValueError("resume checkpoint dataset hash does not match the current corpus")
        expected_manifest_hash = manifest_report["manifest_sha256"] if manifest_report else None
        if metadata.get("corpus_manifest_sha256") != expected_manifest_hash:
            raise ValueError("resume checkpoint manifest hash does not match the current manifest")
        if metadata.get("corpus_manifest_verified") is not (manifest_report is not None):
            raise ValueError("resume checkpoint manifest verification mode does not match")
        if metadata.get("training_fingerprint") != _training_fingerprint(args, device):
            raise ValueError("resume training configuration/device differs from the checkpoint")
        if saved_training_state.get("step") != start_step:
            raise ValueError("checkpoint step and RNG training state disagree")
        if start_step >= args.steps:
            raise ValueError("--steps is the total target step count and must exceed the resume step")
        actual_tokenizer_hash = hashlib.sha256(
            json.dumps(tokenizer_state, sort_keys=True, separators=(",", ":")).encode("utf-8")
        ).hexdigest()
        if metadata.get("tokenizer_sha256") != actual_tokenizer_hash:
            raise ValueError("resume checkpoint tokenizer hash is inconsistent")

        expected_config = KellvynConfig(
            vocab_size=tokenizer.vocab_size,
            d_model=args.width,
            n_layers=args.layers,
            n_heads=args.heads,
            max_seq_len=args.seq_len,
            dropout=args.dropout,
        )
        if model.config != expected_config:
            raise ValueError("resume checkpoint model configuration differs from requested configuration")

        optimizer = torch.optim.AdamW(
            model.parameters(),
            lr=args.learning_rate,
            weight_decay=args.weight_decay,
        )
        optimizer.load_state_dict(optimizer_state)
        _restore_rng_state(saved_training_state["rng"], device)
        last_validation_loss = saved_training_state.get("last_validation_loss")
        resumed_from = str(resume_path)

    train_tokens = encode_documents(train_documents, tokenizer)
    validation_tokens = encode_documents(validation_documents, tokenizer)
    if train_tokens.numel() < args.seq_len + 2:
        raise ValueError("training documents do not contain enough tokens for seq_len")
    if validation_tokens.numel() < args.seq_len + 2:
        raise ValueError("validation documents do not contain enough tokens for seq_len")

    tokenizer_state = tokenizer.to_dict()
    tokenizer_sha256 = hashlib.sha256(
        json.dumps(tokenizer_state, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()
    metadata = {
        "initialization": "random",
        "external_pretrained_weights": False,
        "dataset_filename": source.name,
        "dataset_sha256": actual_dataset_sha256,
        "corpus_manifest_verified": manifest_report is not None,
        "corpus_manifest_sha256": manifest_report["manifest_sha256"] if manifest_report else None,
        "corpus_id": manifest_report["corpus_id"] if manifest_report else None,
        "corpus_source_ids": (
            [item["source_id"] for item in manifest_report["sources"]]
            if manifest_report else []
        ),
        "document_count_before_deduplication": len(train_documents) + len(validation_documents) + duplicate_count,
        "duplicate_document_count_removed": duplicate_count,
        "train_document_count": len(train_documents),
        "validation_document_count": len(validation_documents),
        "train_token_count": int(train_tokens.numel()),
        "validation_token_count": int(validation_tokens.numel()),
        "tokenizer_sha256": tokenizer_sha256,
        "tokenizer_merge_count": tokenizer.merge_count,
        "validation_fraction": args.validation_fraction,
        "seed": int(args.seed),
        "device": str(device),
        "training_fingerprint": _training_fingerprint(args, device),
        "training_arguments": vars(args).copy(),
        "resumed_from": resumed_from,
        "note": "Experimental baseline only; not a production-scale conversational model.",
    }

    checkpoint_path = Path(args.checkpoint)
    metrics_path = (
        Path(args.metrics_file)
        if args.metrics_file
        else checkpoint_path.with_suffix(checkpoint_path.suffix + ".metrics.jsonl")
    )
    metrics_path.parent.mkdir(parents=True, exist_ok=True)
    if resume_path is not None and args.metrics_file is None:
        source_metrics = resume_path.with_suffix(resume_path.suffix + ".metrics.jsonl")
        if source_metrics.is_file() and source_metrics.resolve() != metrics_path.resolve() and not metrics_path.exists():
            metrics_path.write_bytes(source_metrics.read_bytes())

    print(f"device={device}")
    print(f"parameters={model.parameter_count():,}")
    print(f"tokenizer=bpe; merges={tokenizer.merge_count}; vocab={tokenizer.vocab_size}")
    print(f"train_documents={len(train_documents):,}")
    print(f"validation_documents={len(validation_documents):,}")
    print(f"duplicate_documents_removed={duplicate_count:,}")
    print(f"training_tokens={train_tokens.numel():,}")
    print(f"validation_tokens={validation_tokens.numel():,}")
    print(f"start_step={start_step}; target_steps={args.steps}")
    print("initialization=random; external_weights=none")

    last_checkpoint_step = start_step
    last_metric_step = start_step
    last_metric_time = time.perf_counter()

    def save_training_checkpoint(step: int) -> None:
        nonlocal last_checkpoint_step, last_validation_loss
        state = {
            "version": 1,
            "step": step,
            "last_validation_loss": last_validation_loss,
            "tokens_seen": step * args.batch_size * args.seq_len,
            "rng": _capture_rng_state(device),
        }
        save_checkpoint(
            checkpoint_path,
            model,
            step=step,
            tokenizer_state=tokenizer_state,
            optimizer_state=optimizer.state_dict(),
            metadata=metadata,
            training_state=state,
        )
        last_checkpoint_step = step
        print(f"checkpoint_saved={checkpoint_path}; step={step}")

    model.train()
    for step in range(start_step + 1, args.steps + 1):
        step_started = time.perf_counter()
        inputs, targets = make_batch(
            train_tokens,
            batch_size=args.batch_size,
            seq_len=args.seq_len,
            device=device,
        )
        _, loss = model(inputs, targets)
        if loss is None or not torch.isfinite(loss):
            raise RuntimeError(f"non-finite training loss at step {step}")
        optimizer.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), args.grad_clip)
        optimizer.step()
        train_loss = float(loss.item())

        if step == 1 or step % args.eval_every == 0 or step == args.steps:
            last_validation_loss = estimate_loss(
                model,
                validation_tokens,
                batch_size=args.batch_size,
                seq_len=args.seq_len,
                device=device,
            )
            elapsed = max(time.perf_counter() - last_metric_time, 1e-9)
            metric = {
                "step": step,
                "train_loss": train_loss,
                "validation_loss": last_validation_loss,
                "tokens_seen": step * args.batch_size * args.seq_len,
                "tokens_per_second_since_last_metric": (
                    (step - last_metric_step) * args.batch_size * args.seq_len / elapsed
                ),
                "metric_interval_seconds": elapsed,
            }
            _append_metric(metrics_path, metric)
            print(
                f"step={step}/{args.steps} train_loss={train_loss:.4f} "
                f"validation_loss={last_validation_loss:.4f} "
                f"tokens_seen={metric['tokens_seen']}"
            )
            last_metric_step = step
            last_metric_time = time.perf_counter()

        if step % args.checkpoint_every == 0 or step == args.steps:
            save_training_checkpoint(step)

    if last_checkpoint_step != args.steps:
        save_training_checkpoint(args.steps)
    print(f"metrics_saved={metrics_path}")


def _append_metric(path: Path, record: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(record, ensure_ascii=False, allow_nan=False) + "\n")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Train or resume the Kellvyn prototype from random initialization"
    )
    parser.add_argument("--data", required=True, help="UTF-8 prepared corpus; documents separated by blank lines")
    parser.add_argument("--manifest", default=None, help="required provenance manifest for production training")
    parser.add_argument("--manifest-root", default=None, help="root used to resolve files in the manifest")
    parser.add_argument(
        "--allow-unverified-data",
        action="store_true",
        help="TEST ONLY: bypass the manifest requirement for synthetic smoke tests",
    )
    parser.add_argument("--checkpoint", default="./runs/kellvyn-prototype.pt", help="latest resumable checkpoint destination")
    parser.add_argument("--resume-from", default=None, help="resume model/optimizer/RNG state from an existing checkpoint")
    parser.add_argument("--metrics-file", default=None, help="JSONL metrics path; defaults beside the checkpoint")
    parser.add_argument("--checkpoint-every", type=int, default=1000, help="save latest resumable checkpoint every N steps")
    parser.add_argument("--steps", type=int, default=200, help="total target step count, including already completed steps")
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--seq-len", type=int, default=64)
    parser.add_argument("--width", type=int, default=128)
    parser.add_argument("--layers", type=int, default=2)
    parser.add_argument("--heads", type=int, default=4)
    parser.add_argument("--dropout", type=float, default=0.0)
    parser.add_argument("--learning-rate", type=float, default=3e-4)
    parser.add_argument("--weight-decay", type=float, default=0.01)
    parser.add_argument("--grad-clip", type=float, default=1.0)
    parser.add_argument("--eval-every", type=int, default=20)
    parser.add_argument("--validation-fraction", type=float, default=0.1)
    parser.add_argument("--bpe-merges", type=int, default=64)
    parser.add_argument("--bpe-min-frequency", type=int, default=2)
    parser.add_argument("--seed", type=int, default=17)
    parser.add_argument("--device", default="auto", help="auto, cpu, cuda, or mps")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    train(args)


if __name__ == "__main__":
    main()
