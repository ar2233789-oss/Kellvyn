"""Testes da amostragem e da geração do Kellvyn Brain Lab."""

import pytest
import torch

from kellvyn_brain.chat_format import STOP_MARKERS, Turn
from kellvyn_brain.generate import chat_reply, generate_ids, generate_text, sample_next_token
from kellvyn_brain.model import KellvynConfig, KellvynDecoderLM, save_checkpoint
from kellvyn_brain.tokenizer import ByteBPETokenizer

PLAIN = dict(temperature=1.0, top_k=None, top_p=1.0, repetition_penalty=1.0)


def seeded(seed: int) -> torch.Generator:
    generator = torch.Generator()
    generator.manual_seed(seed)
    return generator


def test_temperature_zero_is_greedy():
    logits = torch.tensor([0.1, 2.0, 1.0])
    assert sample_next_token(logits, **{**PLAIN, "temperature": 0.0}) == 1


def test_top_k_one_always_returns_the_best_token():
    logits = torch.tensor([0.0, 5.0, 4.9, 4.8])
    for seed in range(10):
        assert sample_next_token(logits, **{**PLAIN, "top_k": 1}, generator=seeded(seed)) == 1


def test_top_p_keeps_the_dominant_token():
    logits = torch.tensor([10.0, 0.0, 0.0, 0.0])
    for seed in range(10):
        assert sample_next_token(logits, **{**PLAIN, "top_p": 0.5}, generator=seeded(seed)) == 0


def test_blocked_ids_are_never_sampled():
    logits = torch.tensor([0.0, 50.0, 0.0, 0.0])
    for seed in range(20):
        token = sample_next_token(logits, **PLAIN, blocked_ids=(1,), generator=seeded(seed))
        assert token != 1


def test_repetition_penalty_lowers_a_repeated_token():
    logits = torch.tensor([2.0, 1.9])
    greedy = {**PLAIN, "temperature": 0.0, "repetition_penalty": 2.0}
    assert sample_next_token(logits, **greedy, history=[0]) == 1
    assert sample_next_token(logits, **greedy, history=[]) == 0


def test_sampling_is_reproducible_with_the_same_seed():
    logits = torch.randn(50)
    first = [sample_next_token(logits, **PLAIN, generator=seeded(7)) for _ in range(3)]
    second = [sample_next_token(logits, **PLAIN, generator=seeded(7)) for _ in range(3)]
    assert first == second


@pytest.mark.parametrize(
    "overrides",
    [
        {"temperature": -1.0},
        {"top_p": 0.0},
        {"top_p": 1.5},
        {"repetition_penalty": 0.5},
        {"top_k": 0},
    ],
)
def test_invalid_sampling_arguments_are_rejected(overrides):
    with pytest.raises(ValueError):
        sample_next_token(torch.zeros(4), **{**PLAIN, **overrides})


def test_all_tokens_blocked_is_rejected():
    with pytest.raises(ValueError):
        sample_next_token(torch.zeros(3), **PLAIN, blocked_ids=(0, 1, 2))


def tiny_model(vocab: int = 259) -> KellvynDecoderLM:
    return KellvynDecoderLM(KellvynConfig(vocab_size=vocab, d_model=32, n_layers=1, n_heads=2, max_seq_len=16))


def force_logits(model: KellvynDecoderLM, favored: int):
    vocab = model.config.vocab_size

    def fake(ids: torch.Tensor) -> torch.Tensor:
        out = torch.zeros(ids.shape[0], ids.shape[1], vocab)
        out[..., favored] = 50.0
        return out

    return fake


def test_generation_stops_at_eos(monkeypatch):
    model = tiny_model()
    monkeypatch.setattr(model, "forward_logits", force_logits(model, ByteBPETokenizer.EOS_ID))
    assert generate_ids(model, [1, 5], max_new_tokens=10, temperature=0.0) == []


def test_generation_respects_max_new_tokens(monkeypatch):
    model = tiny_model()
    monkeypatch.setattr(model, "forward_logits", force_logits(model, 7))
    out = generate_ids(model, [1, 5], max_new_tokens=3, temperature=0.0, repetition_penalty=1.0)
    assert out == [7, 7, 7]


def test_generation_can_be_stopped_by_a_callback(monkeypatch):
    model = tiny_model()
    monkeypatch.setattr(model, "forward_logits", force_logits(model, 7))
    out = generate_ids(
        model, [1, 5], max_new_tokens=10, temperature=0.0, repetition_penalty=1.0,
        should_stop=lambda generated: len(generated) >= 2,
    )
    assert out == [7, 7]


def test_generation_rejects_empty_prompt_and_negative_budget():
    model = tiny_model()
    with pytest.raises(ValueError):
        generate_ids(model, [], max_new_tokens=3)
    with pytest.raises(ValueError):
        generate_ids(model, [1], max_new_tokens=-1)


def test_generate_text_and_chat_reply_from_a_checkpoint(tmp_path):
    tokenizer = ByteBPETokenizer.train("oi mundo oi mundo " * 10, num_merges=4)
    model = KellvynDecoderLM(
        KellvynConfig(vocab_size=tokenizer.vocab_size, d_model=32, n_layers=1, n_heads=2, max_seq_len=32)
    )
    path = tmp_path / "toy.pt"
    save_checkpoint(path, model, step=1, tokenizer_state=tokenizer.to_dict())

    text = generate_text(path, "oi", max_new_tokens=5, seed=3)
    assert isinstance(text, str)

    reply = chat_reply(path, [Turn("usuario", "oi")], max_new_tokens=8, seed=3)
    assert isinstance(reply, str)
    assert not any(marker in reply for marker in STOP_MARKERS)
