"""Geração de texto do Kellvyn Brain Lab (experimental).

Usa só `forward_logits` do modelo e faz a amostragem aqui: temperatura, top-k,
top-p (nucleus), penalidade de repetição, bloqueio de tokens especiais e parada
no fim de frase (EOS) ou quando o modelo tenta abrir outro turno de conversa.

Uso:
    python -m kellvyn_brain.generate --checkpoint ./runs/kellvyn-toy.pt --prompt "oi"
    python -m kellvyn_brain.generate --checkpoint ./runs/kellvyn-toy.pt --chat --prompt "Quem criou você?"

Este modelo é um baseline pequeno treinado do zero. A saída não representa
conversa útil nem qualidade de produção.
"""

from __future__ import annotations

import argparse
from collections.abc import Callable, Sequence
from pathlib import Path

import torch

from .chat_format import STOP_MARKERS, Turn, format_prompt, trim_reply
from .model import KellvynDecoderLM, load_checkpoint, load_tokenizer_state
from .tokenizer import ByteBPETokenizer
from .train import choose_device

SPECIAL_BLOCKED = (ByteBPETokenizer.PAD_ID, ByteBPETokenizer.BOS_ID)


def _check_sampling(
    *,
    temperature: float,
    top_k: int | None,
    top_p: float,
    repetition_penalty: float,
) -> None:
    if temperature < 0:
        raise ValueError("temperature cannot be negative (use 0 for greedy decoding)")
    if top_k is not None and top_k <= 0:
        raise ValueError("top_k must be positive")
    if not 0.0 < top_p <= 1.0:
        raise ValueError("top_p must be in (0, 1]")
    if repetition_penalty < 1.0:
        raise ValueError("repetition_penalty must be at least 1.0")


def sample_next_token(
    logits: torch.Tensor,
    *,
    temperature: float = 0.8,
    top_k: int | None = 40,
    top_p: float = 0.9,
    repetition_penalty: float = 1.1,
    history: Sequence[int] = (),
    blocked_ids: Sequence[int] = SPECIAL_BLOCKED,
    generator: torch.Generator | None = None,
) -> int:
    """Escolhe o próximo token a partir de logits de formato [vocab]."""
    _check_sampling(
        temperature=temperature,
        top_k=top_k,
        top_p=top_p,
        repetition_penalty=repetition_penalty,
    )
    if logits.ndim != 1:
        raise ValueError("logits must have shape [vocab]")

    scores = logits.detach().float().cpu().clone()
    vocab = scores.numel()
    for token_id in blocked_ids:
        if 0 <= token_id < vocab:
            scores[token_id] = float("-inf")
    if not bool(torch.isfinite(scores).any()):
        raise ValueError("all tokens are blocked")

    if repetition_penalty != 1.0 and history:
        seen = sorted({token for token in history if 0 <= token < vocab})
        if seen:
            index = torch.tensor(seen, dtype=torch.long)
            picked = scores[index]
            scores[index] = torch.where(picked > 0, picked / repetition_penalty, picked * repetition_penalty)

    if temperature == 0:
        return int(torch.argmax(scores))
    scores = scores / temperature

    if top_k is not None and top_k < vocab:
        threshold = torch.topk(scores, top_k).values[-1]
        scores = scores.masked_fill(scores < threshold, float("-inf"))

    if top_p < 1.0:
        sorted_scores, sorted_index = torch.sort(scores, descending=True)
        probabilities = torch.softmax(sorted_scores, dim=-1)
        # Remove um token só se a massa acumulada ANTES dele já alcançou top_p;
        # assim o melhor token sempre fica.
        remove = (probabilities.cumsum(dim=-1) - probabilities) >= top_p
        sorted_scores = sorted_scores.masked_fill(remove, float("-inf"))
        scores = torch.full_like(scores, float("-inf")).scatter(0, sorted_index, sorted_scores)

    probabilities = torch.softmax(scores, dim=-1)
    return int(torch.multinomial(probabilities, 1, generator=generator))


@torch.no_grad()
def generate_ids(
    model: KellvynDecoderLM,
    prompt_ids: Sequence[int],
    *,
    max_new_tokens: int = 64,
    temperature: float = 0.8,
    top_k: int | None = 40,
    top_p: float = 0.9,
    repetition_penalty: float = 1.1,
    repetition_window: int = 64,
    eos_id: int = ByteBPETokenizer.EOS_ID,
    blocked_ids: Sequence[int] = SPECIAL_BLOCKED,
    should_stop: Callable[[list[int]], bool] | None = None,
    generator: torch.Generator | None = None,
) -> list[int]:
    """Devolve só os tokens novos. Para no EOS, em max_new_tokens ou se should_stop() disser."""
    if max_new_tokens < 0:
        raise ValueError("max_new_tokens cannot be negative")
    if not prompt_ids:
        raise ValueError("prompt_ids must not be empty")
    _check_sampling(
        temperature=temperature,
        top_k=top_k,
        top_p=top_p,
        repetition_penalty=repetition_penalty,
    )

    was_training = model.training
    model.eval()
    device = next(model.parameters()).device
    ids = list(prompt_ids)
    generated: list[int] = []
    try:
        for _ in range(max_new_tokens):
            context = torch.tensor([ids[-model.config.max_seq_len :]], dtype=torch.long, device=device)
            logits = model.forward_logits(context)[0, -1]
            token = sample_next_token(
                logits,
                temperature=temperature,
                top_k=top_k,
                top_p=top_p,
                repetition_penalty=repetition_penalty,
                history=ids[-repetition_window:],
                blocked_ids=blocked_ids,
                generator=generator,
            )
            if token == eos_id:
                break
            ids.append(token)
            generated.append(token)
            if should_stop is not None and should_stop(generated):
                break
    finally:
        model.train(was_training)
    return generated


def _load(checkpoint: str | Path, device_name: str) -> tuple[KellvynDecoderLM, ByteBPETokenizer]:
    device = choose_device(device_name)
    model, _ = load_checkpoint(checkpoint, map_location="cpu")
    model = model.to(device).eval()
    tokenizer = ByteBPETokenizer.from_dict(load_tokenizer_state(checkpoint, map_location="cpu"))
    return model, tokenizer


def _generator(seed: int | None) -> torch.Generator | None:
    if seed is None:
        return None
    generator = torch.Generator()
    generator.manual_seed(seed)
    return generator


def generate_text(
    checkpoint: str | Path,
    prompt: str,
    *,
    max_new_tokens: int = 64,
    temperature: float = 0.8,
    top_k: int | None = 40,
    top_p: float = 0.9,
    repetition_penalty: float = 1.1,
    device: str = "cpu",
    seed: int | None = None,
) -> str:
    """Continua o texto do prompt e devolve somente o trecho novo."""
    model, tokenizer = _load(checkpoint, device)
    prompt_ids = tokenizer.encode(prompt, add_bos=True, add_eos=False)
    new_ids = generate_ids(
        model,
        prompt_ids,
        max_new_tokens=max_new_tokens,
        temperature=temperature,
        top_k=top_k,
        top_p=top_p,
        repetition_penalty=repetition_penalty,
        generator=_generator(seed),
    )
    return tokenizer.decode(new_ids)


def chat_reply(
    checkpoint: str | Path,
    turns: Sequence[Turn],
    *,
    max_new_tokens: int = 160,
    temperature: float = 0.7,
    top_k: int | None = 40,
    top_p: float = 0.9,
    repetition_penalty: float = 1.1,
    device: str = "cpu",
    seed: int | None = None,
) -> str:
    """Responde à última mensagem do usuário usando o formato de conversa do projeto."""
    model, tokenizer = _load(checkpoint, device)
    prompt_ids = tokenizer.encode(format_prompt(turns), add_bos=True, add_eos=False)

    def opened_another_turn(generated: list[int]) -> bool:
        text = tokenizer.decode(generated)
        return any(marker in text for marker in STOP_MARKERS)

    new_ids = generate_ids(
        model,
        prompt_ids,
        max_new_tokens=max_new_tokens,
        temperature=temperature,
        top_k=top_k,
        top_p=top_p,
        repetition_penalty=repetition_penalty,
        should_stop=opened_another_turn,
        generator=_generator(seed),
    )
    return trim_reply(tokenizer.decode(new_ids))


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Sample text from a Kellvyn brain-lab checkpoint")
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--prompt", default="")
    parser.add_argument("--chat", action="store_true", help="treat --prompt as one user message in chat format")
    parser.add_argument("--max-new-tokens", type=int, default=160)
    parser.add_argument("--temperature", type=float, default=0.7, help="0 = greedy")
    parser.add_argument("--top-k", type=int, default=40)
    parser.add_argument("--top-p", type=float, default=0.9)
    parser.add_argument("--repetition-penalty", type=float, default=1.1)
    parser.add_argument("--seed", type=int, default=None)
    parser.add_argument("--device", default="cpu", help="auto, cpu, cuda or mps")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    common = dict(
        max_new_tokens=args.max_new_tokens,
        temperature=args.temperature,
        top_k=args.top_k,
        top_p=args.top_p,
        repetition_penalty=args.repetition_penalty,
        device=args.device,
        seed=args.seed,
    )
    if args.chat:
        print(chat_reply(args.checkpoint, [Turn("usuario", args.prompt)], **common))
    else:
        print(generate_text(args.checkpoint, args.prompt, **common))


if __name__ == "__main__":
    main()
