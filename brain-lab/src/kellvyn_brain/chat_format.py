"""Formato de conversa do Kellvyn Brain Lab (lógica pura, sem PyTorch).

Monta documentos de treino e prompts de inferência com marcadores de papel em
texto comum. O tokenizador é byte-level, então os marcadores não são tokens
especiais: o modelo aprende a respeitá-los durante o treino.

Regra do corpus: uma linha em branco separa documentos. Por isso o conteúdo de
cada turno nunca mantém linhas em branco.
"""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

SYSTEM = "<|sistema|>"
USER = "<|usuario|>"
ASSISTANT = "<|kellvyn|>"
ROLE_MARKERS = {"sistema": SYSTEM, "usuario": USER, "kellvyn": ASSISTANT}
MARKER_ROLES = {marker: role for role, marker in ROLE_MARKERS.items()}
STOP_MARKERS = (SYSTEM, USER, ASSISTANT)


@dataclass(frozen=True)
class Turn:
    role: str
    content: str


def clean_content(text: str) -> str:
    """Remove linhas em branco e espaços nas pontas: linha em branco separa documentos."""
    normalized = text.replace("\r\n", "\n").replace("\r", "\n")
    lines = [line.rstrip() for line in normalized.split("\n")]
    return "\n".join(line for line in lines if line.strip()).strip()


def _check(turns: Sequence[Turn], final_role: str) -> list[Turn]:
    items = list(turns)
    if not items:
        raise ValueError("a conversa precisa ter ao menos um turno")
    cleaned: list[Turn] = []
    for turn in items:
        if turn.role not in ROLE_MARKERS:
            raise ValueError(f"papel desconhecido: {turn.role!r}")
        content = clean_content(turn.content)
        if not content:
            raise ValueError("turno vazio")
        if any(marker in content for marker in ROLE_MARKERS.values()):
            raise ValueError("o conteúdo de um turno não pode conter marcadores de papel")
        cleaned.append(Turn(turn.role, content))

    start = 1 if cleaned[0].role == "sistema" else 0
    expected = "usuario"
    for turn in cleaned[start:]:
        if turn.role != expected:
            raise ValueError(f"ordem inválida: esperado {expected!r}, recebido {turn.role!r}")
        expected = "kellvyn" if expected == "usuario" else "usuario"
    if cleaned[-1].role != final_role:
        raise ValueError(f"a conversa deve terminar com o papel {final_role!r}")
    return cleaned


def _render(turns: Sequence[Turn]) -> str:
    return "\n".join(f"{ROLE_MARKERS[turn.role]}\n{turn.content}" for turn in turns)


def format_conversation(turns: Sequence[Turn]) -> str:
    """Documento completo de treino: termina com a resposta da Kellvyn."""
    return _render(_check(turns, "kellvyn"))


def format_prompt(turns: Sequence[Turn]) -> str:
    """Prompt de inferência: termina com a pergunta e abre o turno da Kellvyn."""
    return _render(_check(turns, "usuario")) + "\n" + ASSISTANT + "\n"


def parse_conversation(document: str) -> list[Turn]:
    """Inverso de format_conversation."""
    turns: list[Turn] = []
    role: str | None = None
    buffer: list[str] = []

    def flush() -> None:
        if role is not None:
            turns.append(Turn(role, "\n".join(buffer)))

    for line in document.split("\n"):
        if line in MARKER_ROLES:
            flush()
            role = MARKER_ROLES[line]
            buffer = []
        elif role is None:
            raise ValueError("o documento precisa começar com um marcador de papel")
        else:
            buffer.append(line)
    flush()
    return turns


def trim_reply(generated: str) -> str:
    """Corta a resposta no primeiro marcador de papel que o modelo gerar."""
    cut = len(generated)
    for marker in STOP_MARKERS:
        index = generated.find(marker)
        if index != -1:
            cut = min(cut, index)
    return generated[:cut].strip()


def jsonl_to_corpus(source: Path, destination: Path) -> int:
    """Converte JSONL {"turns": [{"role", "content"}, ...]} em corpus de treino.

    Remove duplicatas, separa documentos por linha em branco e devolve a quantidade.
    """
    documents: dict[str, None] = {}
    with source.open(encoding="utf-8") as handle:
        for number, line in enumerate(handle, start=1):
            if not line.strip():
                continue
            try:
                record = json.loads(line)
                turns = [Turn(item["role"], item["content"]) for item in record["turns"]]
                documents.setdefault(format_conversation(turns), None)
            except (KeyError, TypeError, ValueError) as error:
                raise ValueError(f"linha {number}: {error}") from error
    if len(documents) < 2:
        raise ValueError("são necessários pelo menos dois documentos distintos")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("\n\n".join(documents) + "\n", encoding="utf-8")
    return len(documents)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Convert chat JSONL into a Kellvyn training corpus")
    parser.add_argument("--input", required=True, type=Path, help="JSONL with one {'turns': [...]} per line")
    parser.add_argument("--output", required=True, type=Path, help="corpus .txt to write")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    count = jsonl_to_corpus(args.input, args.output)
    print(f"corpus gerado: {count} conversas em {args.output}")


if __name__ == "__main__":
    main()
