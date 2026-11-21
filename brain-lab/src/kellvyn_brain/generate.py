"""Gera texto a partir de um checkpoint do Kellvyn Brain Lab (experimental).

Uso:
    python -m kellvyn_brain.generate --checkpoint ./runs/kellvyn-toy.pt --prompt "oi"

Este modelo é um baseline pequeno treinado do zero. A saída não representa
conversa útil nem qualidade de produção.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import torch

from .model import load_checkpoint, load_tokenizer_state
from .tokenizer import ByteBPETokenizer
from .train import choose_device


def generate_text(
    checkpoint: str | Path,
    prompt: str,
    *,
    max_new_tokens: int = 64,
    temperature: float = 0.8,
    top_k: int | None = 40,
    device: str = "cpu",
    seed: int | None = None,
) -> str:
    """Devolve somente o texto gerado, sem repetir o prompt."""
    if seed is not None:
        torch.manual_seed(seed)
    target = choose_device(device)
    model, _ = load_checkpoint(checkpoint, map_location="cpu")
    model = model.to(target)
    tokenizer = ByteBPETokenizer.from_dict(
        load_tokenizer_state(checkpoint, map_location="cpu")
    )
    prompt_ids = tokenizer.encode(prompt, add_bos=True, add_eos=False)
    ids = torch.tensor([prompt_ids], dtype=torch.long, device=target)
    out = model.generate(
        ids,
        max_new_tokens=max_new_tokens,
        temperature=temperature,
        top_k=top_k,
    )
    return tokenizer.decode(out[0, len(prompt_ids):].tolist())


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Sample text from a Kellvyn brain-lab checkpoint")
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--prompt", default="")
    parser.add_argument("--max-new-tokens", type=int, default=64)
    parser.add_argument("--temperature", type=float, default=0.8)
    parser.add_argument("--top-k", type=int, default=40)
    parser.add_argument("--seed", type=int, default=None)
    parser.add_argument("--device", default="cpu", help="cpu, cuda or mps")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    text = generate_text(
        args.checkpoint,
        args.prompt,
        max_new_tokens=args.max_new_tokens,
        temperature=args.temperature,
        top_k=args.top_k,
        device=args.device,
        seed=args.seed,
    )
    print(text)


if __name__ == "__main__":
    main()
