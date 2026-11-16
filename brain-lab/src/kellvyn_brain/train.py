"""Minimal next-token trainer for the from-scratch Kellvyn prototype."""

from __future__ import annotations

import argparse
import random
from pathlib import Path

import torch

from .model import KellvynConfig, KellvynDecoderLM, save_checkpoint
from .tokenizer import ByteTokenizer


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
    model.eval()
    losses = []
    for _ in range(batches):
        inputs, targets = make_batch(
            tokens, batch_size=batch_size, seq_len=seq_len, device=device
        )
        _, loss = model(inputs, targets)
        if loss is None:
            raise RuntimeError("model did not return a loss")
        losses.append(float(loss.item()))
    model.train()
    return sum(losses) / len(losses)


def train(args: argparse.Namespace) -> None:
    if args.steps <= 0 or args.batch_size <= 0 or args.seq_len <= 1:
        raise ValueError("steps/batch-size must be positive and seq-len must exceed 1")
    if args.eval_every <= 0 or args.learning_rate <= 0:
        raise ValueError("eval-every and learning-rate must be positive")

    random.seed(args.seed)
    torch.manual_seed(args.seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(args.seed)

    source = Path(args.data)
    if not source.is_file():
        raise FileNotFoundError(f"training text file not found: {source}")
    text = source.read_text(encoding="utf-8")
    tokenizer = ByteTokenizer()
    encoded = torch.tensor(tokenizer.encode(text), dtype=torch.long)
    split = int(encoded.numel() * 0.9)
    train_tokens, validation_tokens = encoded[:split], encoded[split:]
    device = choose_device(args.device)

    config = KellvynConfig(
        vocab_size=tokenizer.VOCAB_SIZE,
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

    save_checkpoint(args.checkpoint, model, step=args.steps)
    print(f"checkpoint_saved={args.checkpoint}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Train the Kellvyn prototype from random initialization")
    parser.add_argument("--data", required=True, help="UTF-8 text file with authorized training content")
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
    parser.add_argument("--seed", type=int, default=17)
    parser.add_argument("--device", default="auto", help="auto, cpu, cuda, or mps")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    train(args)


if __name__ == "__main__":
    main()
