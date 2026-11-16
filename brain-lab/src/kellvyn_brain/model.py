"""Small causal language model implemented for the Kellvyn project.

No pretrained checkpoint is used. All trainable tensors are initialized here.
The module is a baseline experiment, not a claim of a novel Transformer design.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

import torch
import torch.nn as nn
import torch.nn.functional as F


@dataclass(frozen=True)
class KellvynConfig:
    vocab_size: int = 259
    d_model: int = 128
    n_layers: int = 2
    n_heads: int = 4
    max_seq_len: int = 128
    dropout: float = 0.0
    rope_base: float = 10_000.0
    rms_eps: float = 1e-6

    def validate(self) -> None:
        if self.vocab_size < 4:
            raise ValueError("vocab_size must be at least 4")
        if self.d_model <= 0 or self.n_layers <= 0 or self.n_heads <= 0:
            raise ValueError("model dimensions and layer count must be positive")
        if self.d_model % self.n_heads:
            raise ValueError("d_model must be divisible by n_heads")
        if (self.d_model // self.n_heads) % 2:
            raise ValueError("head dimension must be even for rotary embeddings")
        if self.max_seq_len < 2:
            raise ValueError("max_seq_len must be at least 2")
        if not 0.0 <= self.dropout < 1.0:
            raise ValueError("dropout must be in [0, 1)")
        if self.rope_base <= 0 or self.rms_eps <= 0:
            raise ValueError("rope_base and rms_eps must be positive")


class RMSNorm(nn.Module):
    def __init__(self, width: int, eps: float) -> None:
        super().__init__()
        self.weight = nn.Parameter(torch.ones(width))
        self.eps = eps

    def forward(self, value: torch.Tensor) -> torch.Tensor:
        scale = torch.rsqrt(value.float().pow(2).mean(dim=-1, keepdim=True) + self.eps)
        return value * scale.to(dtype=value.dtype) * self.weight


def apply_rope(
    query: torch.Tensor,
    key: torch.Tensor,
    *,
    base: float,
) -> tuple[torch.Tensor, torch.Tensor]:
    """Apply rotary position embeddings to [batch, heads, time, head_dim] tensors."""
    seq_len = query.shape[-2]
    head_dim = query.shape[-1]
    device = query.device
    frequencies = 1.0 / (
        base ** (torch.arange(0, head_dim, 2, device=device, dtype=torch.float32) / head_dim)
    )
    positions = torch.arange(seq_len, device=device, dtype=torch.float32)
    angles = torch.outer(positions, frequencies)
    cos = angles.cos().to(dtype=query.dtype)[None, None, :, :]
    sin = angles.sin().to(dtype=query.dtype)[None, None, :, :]

    def rotate(value: torch.Tensor) -> torch.Tensor:
        even = value[..., 0::2]
        odd = value[..., 1::2]
        rotated_even = even * cos - odd * sin
        rotated_odd = even * sin + odd * cos
        return torch.stack((rotated_even, rotated_odd), dim=-1).flatten(-2)

    return rotate(query), rotate(key)


class CausalSelfAttention(nn.Module):
    def __init__(self, config: KellvynConfig) -> None:
        super().__init__()
        self.n_heads = config.n_heads
        self.head_dim = config.d_model // config.n_heads
        self.dropout = config.dropout
        self.rope_base = config.rope_base
        self.qkv = nn.Linear(config.d_model, 3 * config.d_model, bias=False)
        self.output = nn.Linear(config.d_model, config.d_model, bias=False)
        self.residual_dropout = nn.Dropout(config.dropout)

    def forward(self, value: torch.Tensor) -> torch.Tensor:
        batch, seq_len, width = value.shape
        qkv = self.qkv(value).view(batch, seq_len, 3, self.n_heads, self.head_dim)
        query, key, content = qkv.permute(2, 0, 3, 1, 4).unbind(dim=0)
        query, key = apply_rope(query, key, base=self.rope_base)
        attended = F.scaled_dot_product_attention(
            query,
            key,
            content,
            dropout_p=self.dropout if self.training else 0.0,
            is_causal=True,
        )
        merged = attended.transpose(1, 2).contiguous().view(batch, seq_len, width)
        return self.residual_dropout(self.output(merged))


class SwiGLU(nn.Module):
    def __init__(self, width: int, dropout: float) -> None:
        super().__init__()
        hidden = (8 * width) // 3
        self.gate = nn.Linear(width, hidden, bias=False)
        self.up = nn.Linear(width, hidden, bias=False)
        self.down = nn.Linear(hidden, width, bias=False)
        self.dropout = nn.Dropout(dropout)

    def forward(self, value: torch.Tensor) -> torch.Tensor:
        hidden = F.silu(self.gate(value)) * self.up(value)
        return self.dropout(self.down(hidden))


class TransformerBlock(nn.Module):
    def __init__(self, config: KellvynConfig) -> None:
        super().__init__()
        self.attention_norm = RMSNorm(config.d_model, config.rms_eps)
        self.attention = CausalSelfAttention(config)
        self.mlp_norm = RMSNorm(config.d_model, config.rms_eps)
        self.mlp = SwiGLU(config.d_model, config.dropout)

    def forward(self, value: torch.Tensor) -> torch.Tensor:
        value = value + self.attention(self.attention_norm(value))
        value = value + self.mlp(self.mlp_norm(value))
        return value


class KellvynDecoderLM(nn.Module):
    """Decoder-only language model initialized entirely from random parameters."""

    def __init__(self, config: KellvynConfig) -> None:
        super().__init__()
        config.validate()
        self.config = config
        self.token_embedding = nn.Embedding(config.vocab_size, config.d_model)
        self.blocks = nn.ModuleList([TransformerBlock(config) for _ in range(config.n_layers)])
        self.final_norm = RMSNorm(config.d_model, config.rms_eps)
        self.lm_head = nn.Linear(config.d_model, config.vocab_size, bias=False)
        self.apply(self._initialize_parameters)

    @staticmethod
    def _initialize_parameters(module: nn.Module) -> None:
        if isinstance(module, nn.Linear):
            nn.init.normal_(module.weight, mean=0.0, std=0.02)
            if module.bias is not None:
                nn.init.zeros_(module.bias)
        elif isinstance(module, nn.Embedding):
            nn.init.normal_(module.weight, mean=0.0, std=0.02)

    def forward(
        self,
        input_ids: torch.Tensor,
        targets: torch.Tensor | None = None,
    ) -> tuple[torch.Tensor, torch.Tensor | None]:
        if input_ids.ndim != 2:
            raise ValueError("input_ids must have shape [batch, time]")
        if input_ids.shape[1] > self.config.max_seq_len:
            raise ValueError("sequence exceeds configured max_seq_len")
        if input_ids.numel() and (
            int(input_ids.min()) < 0 or int(input_ids.max()) >= self.config.vocab_size
        ):
            raise ValueError("input token ID outside configured vocabulary")

        value = self.token_embedding(input_ids)
        for block in self.blocks:
            value = block(value)
        logits = self.lm_head(self.final_norm(value))

        loss = None
        if targets is not None:
            if targets.shape != input_ids.shape:
                raise ValueError("targets must have the same shape as input_ids")
            loss = F.cross_entropy(
                logits.reshape(-1, self.config.vocab_size),
                targets.reshape(-1),
            )
        return logits, loss

    @torch.no_grad()
    def generate(
        self,
        input_ids: torch.Tensor,
        *,
        max_new_tokens: int = 64,
        temperature: float = 1.0,
        top_k: int | None = None,
    ) -> torch.Tensor:
        if temperature <= 0:
            raise ValueError("temperature must be positive")
        if input_ids.ndim != 2 or input_ids.shape[1] == 0:
            raise ValueError("input_ids must be a non-empty [batch, time] tensor")
        was_training = self.training
        self.eval()
        try:
            for _ in range(max_new_tokens):
                context = input_ids[:, -self.config.max_seq_len :]
                logits, _ = self(context)
                next_logits = logits[:, -1, :] / temperature
                if top_k is not None:
                    if top_k <= 0:
                        raise ValueError("top_k must be positive")
                    k = min(top_k, next_logits.shape[-1])
                    threshold = torch.topk(next_logits, k, dim=-1).values[:, -1, None]
                    next_logits = next_logits.masked_fill(next_logits < threshold, float("-inf"))
                probabilities = torch.softmax(next_logits, dim=-1)
                next_id = torch.multinomial(probabilities, num_samples=1)
                input_ids = torch.cat((input_ids, next_id), dim=1)
            return input_ids
        finally:
            self.train(was_training)

    def parameter_count(self) -> int:
        return sum(parameter.numel() for parameter in self.parameters() if parameter.requires_grad)


def save_checkpoint(
    path: str | Path,
    model: KellvynDecoderLM,
    *,
    step: int,
    tokenizer_state: dict[str, Any] | None = None,
    optimizer_state: dict[str, Any] | None = None,
    metadata: dict[str, Any] | None = None,
) -> None:
    """Persist weights, tokenizer, optimizer and auditable training provenance."""
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    payload: dict[str, Any] = {
        "format": "kellvyn-brain-lab-v2",
        "step": int(step),
        "config": asdict(model.config),
        "tokenizer": tokenizer_state,
        "state_dict": model.state_dict(),
        "optimizer_state": optimizer_state,
        "metadata": metadata or {},
    }
    torch.save(payload, destination)


def load_checkpoint(path: str | Path, *, map_location: str | torch.device = "cpu") -> tuple[KellvynDecoderLM, int]:
    payload = torch.load(Path(path), map_location=map_location, weights_only=True)
    if not isinstance(payload, dict) or payload.get("format") not in {"kellvyn-brain-lab-v1", "kellvyn-brain-lab-v2"}:
        raise ValueError("unsupported or invalid Kellvyn checkpoint")
    config = KellvynConfig(**payload["config"])
    model = KellvynDecoderLM(config)
    model.load_state_dict(payload["state_dict"], strict=True)
    return model, int(payload["step"])


def load_tokenizer_state(
    path: str | Path,
    *,
    map_location: str | torch.device = "cpu",
) -> dict[str, Any] | None:
    payload = torch.load(Path(path), map_location=map_location, weights_only=True)
    if not isinstance(payload, dict) or payload.get("format") != "kellvyn-brain-lab-v1":
        raise ValueError("unsupported or invalid Kellvyn checkpoint")
    tokenizer_state = payload.get("tokenizer")
    if tokenizer_state is not None and not isinstance(tokenizer_state, dict):
        raise ValueError("checkpoint tokenizer state is malformed")
    return tokenizer_state
