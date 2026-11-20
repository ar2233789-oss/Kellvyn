"""Document-aware next-token trainer for the from-scratch Kellvyn prototype."""

from __future__ import annotations

import argparse
import hashlib
import json
import random
from pathlib import Path
from typing import Sequence

import torch

from .corpus_manifest import validate_corpus_manifest
from .model import KellvynConfig, KellvynDecoderLM, save_checkpoint
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


def train(args: argparse.Namespace) -> None:
    manifest_report = None
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

    if args.steps <= 0 or args.batch_size <= 0 or args.seq_len <= 1:
        raise ValueError("steps/batch-size must be positive and seq-len must exceed 1")
    if args.eval_every <= 0 or args.learning_rate <= 0 or args.grad_clip <= 0:
        raise ValueError("eval-every, learning-rate and grad-clip must be positive")
    if args.weight_decay < 0:
        raise ValueError("weight-decay cannot be negative")

    random.seed(args.seed)
    torch.manual_seed(args.seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(args.seed)

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

    # Fit the tokenizer only on training documents. Validation text cannot
    # influence BPE merge frequencies or vocabulary construction.
    train_text = "\n\n".join(train_documents)
    tokenizer = ByteBPETokenizer.train(
        train_text,
        num_merges=args.bpe_merges,
        min_frequency=args.bpe_min_frequency,
    )
    train_tokens = encode_documents(train_documents, tokenizer)
    validation_tokens = encode_documents(validation_documents, tokenizer)
    if train_tokens.numel() < args.seq_len + 2:
        raise ValueError("training documents do not contain enough tokens for seq_len")
    if validation_tokens.numel() < args.seq_len + 2:
        raise ValueError("validation documents do not contain enough tokens for seq_len")

    device = choose_device(args.device)
    config = KellvynConfig(
        vocab_size=tokenizer.vocab_size,
        d_model=args.width,
        n_layers=args.layers,
        n_heads=args.heads,
        max_seq_len=args.seq_len,
        dropout=args.dropout,
    )
    config.validate()
    model = KellvynDecoderLM(config).to(device)
    optimizer = torch.optim.AdamW(
        model.parameters(),
        lr=args.learning_rate,
        weight_decay=args.weight_decay,
    )

    print(f"device={device}")
    print(f"parameters={model.parameter_count():,}")
    print(f"tokenizer=bpe; merges={tokenizer.merge_count}; vocab={tokenizer.vocab_size}")
    print(f"train_documents={len(train_documents):,}")
    print(f"validation_documents={len(validation_documents):,}")
    print(f"duplicate_documents_removed={duplicate_count:,}")
    print(f"training_tokens={train_tokens.numel():,}")
    print(f"validation_tokens={validation_tokens.numel():,}")
    print("initialization=random; external_weights=none")

    for step in range(1, args.steps + 1):
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

        if step == 1 or step % args.eval_every == 0 or step == args.steps:
            validation_loss = estimate_loss(
                model,
                validation_tokens,
                batch_size=args.batch_size,
                seq_len=args.seq_len,
                device=device,
            )
            print(
                f"step={step}/{args.steps} "
                f"train_loss={loss.item():.4f} "
                f"validation_loss={validation_loss:.4f}"
            )

    tokenizer_state = tokenizer.to_dict()
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
        "tokenizer_sha256": hashlib.sha256(
            json.dumps(tokenizer_state, sort_keys=True, separators=(",", ":")).encode("utf-8")
        ).hexdigest(),
        "tokenizer_merge_count": tokenizer.merge_count,
        "validation_fraction": args.validation_fraction,
        "seed": int(args.seed),
        "device": str(device),
        "training_arguments": vars(args).copy(),
        "note": "Experimental baseline only; BPE algorithm is not optimized for large corpora.",
    }
    save_checkpoint(
        args.checkpoint,
        model,
        step=args.steps,
        tokenizer_state=tokenizer_state,
        optimizer_state=optimizer.state_dict(),
        metadata=metadata,
    )
    print(f"checkpoint_saved={args.checkpoint}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Train the Kellvyn prototype from random initialization"
    )
    parser.add_argument("--data", required=True, help="UTF-8 prepared corpus; documents separated by blank lines")
    parser.add_argument("--manifest", default=None, help="required provenance manifest for production training")
    parser.add_argument("--manifest-root", default=None, help="root used to resolve files in the manifest")
    parser.add_argument(
        "--allow-unverified-data",
        action="store_true",
        help="TEST ONLY: bypass the manifest requirement for synthetic smoke tests",
    )
    parser.add_argument("--checkpoint", default="./runs/kellvyn-prototype.pt")
    parser.add_argument("--steps", type=int, default=200)
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
