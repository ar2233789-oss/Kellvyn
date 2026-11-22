"""Testes do formato de conversa e do corpus semente (sem PyTorch no caminho do formato)."""

import json
import re
from pathlib import Path

import pytest

from kellvyn_brain.chat_format import (
    ASSISTANT,
    ROLE_MARKERS,
    USER,
    Turn,
    format_conversation,
    format_prompt,
    jsonl_to_corpus,
    parse_conversation,
    trim_reply,
)

SEED = Path(__file__).resolve().parents[1] / "data" / "seed_pt" / "kellvyn_seed_v1.txt"


def test_conversation_is_canonical_and_has_no_blank_lines():
    doc = format_conversation([Turn("usuario", "Oi\n\n\n  tudo bem?  "), Turn("kellvyn", "Oi! Tudo.")])
    assert doc == "<|usuario|>\nOi\n  tudo bem?\n<|kellvyn|>\nOi! Tudo."
    assert "\n\n" not in doc
    assert format_conversation(parse_conversation(doc)) == doc


def test_prompt_opens_the_assistant_turn():
    prompt = format_prompt([Turn("sistema", "regras"), Turn("usuario", "oi")])
    assert prompt == "<|sistema|>\nregras\n<|usuario|>\noi\n<|kellvyn|>\n"


@pytest.mark.parametrize(
    "turns",
    [
        [Turn("usuario", "oi")],
        [Turn("kellvyn", "oi")],
        [Turn("usuario", "a"), Turn("usuario", "b"), Turn("kellvyn", "c")],
        [Turn("usuario", "a <|kellvyn|> b"), Turn("kellvyn", "c")],
        [Turn("usuario", "a"), Turn("robo", "c")],
        [Turn("usuario", "   "), Turn("kellvyn", "c")],
        [],
    ],
)
def test_invalid_conversations_are_rejected(turns):
    with pytest.raises(ValueError):
        format_conversation(turns)


def test_prompt_must_end_with_user_message():
    with pytest.raises(ValueError):
        format_prompt([Turn("usuario", "a"), Turn("kellvyn", "b")])


def test_trim_reply_cuts_at_next_role_marker():
    assert trim_reply("Olá!\n<|usuario|>\nmais") == "Olá!"
    assert trim_reply("  sem marcador  ") == "sem marcador"
    assert trim_reply("<|kellvyn|>\nrepetiu") == ""


def test_jsonl_to_corpus_deduplicates_and_separates_documents(tmp_path):
    rows = [
        {"turns": [{"role": "usuario", "content": "Oi"}, {"role": "kellvyn", "content": "Olá!"}]},
        {"turns": [{"role": "usuario", "content": "Tchau"}, {"role": "kellvyn", "content": "Até logo."}]},
        {"turns": [{"role": "usuario", "content": "Oi"}, {"role": "kellvyn", "content": "Olá!"}]},
    ]
    source = tmp_path / "c.jsonl"
    source.write_text("\n".join(json.dumps(r, ensure_ascii=False) for r in rows) + "\n", encoding="utf-8")
    destination = tmp_path / "out" / "corpus.txt"
    assert jsonl_to_corpus(source, destination) == 2
    parts = [p for p in re.split(r"\n\s*\n", destination.read_text(encoding="utf-8")) if p.strip()]
    assert len(parts) == 2


def test_jsonl_error_reports_the_line_number(tmp_path):
    source = tmp_path / "bad.jsonl"
    source.write_text('{"turns": [{"role": "usuario", "content": "x"}]}\n{"turns": []}\n', encoding="utf-8")
    with pytest.raises(ValueError, match="linha 1"):
        jsonl_to_corpus(source, tmp_path / "b.txt")


def test_seed_corpus_is_well_formed_and_canonical():
    text = SEED.read_text(encoding="utf-8")
    documents = [part for part in re.split(r"\n\s*\n", text) if part.strip()]
    assert len(documents) >= 40
    assert len(set(documents)) == len(documents), "o corpus semente não pode ter conversas repetidas"
    markers = set(ROLE_MARKERS.values())
    for document in documents:
        turns = parse_conversation(document.strip("\n"))
        assert turns[0].role == "usuario" and turns[-1].role == "kellvyn"
        assert format_conversation(turns) == document.strip("\n"), document
        assert document.count(USER) >= 1 and document.count(ASSISTANT) >= 1
    # Nenhum marcador solto fora das linhas de papel.
    for line in text.split("\n"):
        if "<|" in line:
            assert line in markers, line


def test_seed_corpus_never_claims_to_be_a_third_party_model():
    text = SEED.read_text(encoding="utf-8").lower()
    for sentence in re.findall(r"<\|kellvyn\|>\n([^\n]+)", text):
        assert "sou o chatgpt" not in sentence
        assert "sou o gemma" not in sentence
        assert "fui criada pela openai" not in sentence
