"""Gera o manifesto de procedência de um corpus de uma fonte e valida na hora.

Uso (exemplo com um conto de domínio público):
    python -m kellvyn_brain.build_manifest \
      --text ./data/contos.txt \
      --source-id machado-contos-001 \
      --reference "https://exemplo.org/fonte" \
      --license "dominio-publico" \
      --rights-basis "Obra de Machado de Assis em domínio público desde 1958" \
      --language pt-BR --domain literatura --collected-at 2026-11-21 \
      --manifest ./data/manifest.json

O script calcula SHA-256, tamanho e quantidade de documentos. Não decide
juridicamente se a licença permite treino: esses campos vêm de você.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

from .corpus_manifest import validate_corpus_manifest


def _document_count(text: str) -> int:
    normalized = text.replace("\r\n", "\n").replace("\r", "\n")
    return sum(1 for part in re.split(r"\n\s*\n", normalized) if part.strip())


def build_manifest(
    *,
    text_path: Path,
    manifest_path: Path,
    corpus_id: str,
    source_id: str,
    reference: str,
    license_name: str,
    rights_basis: str,
    language: str,
    domain: str,
    collected_at: str,
    preparation_notes: str,
) -> dict:
    """Escreve o manifesto para um arquivo e devolve o relatório do validador."""
    if not text_path.is_file():
        raise FileNotFoundError(f"text file not found: {text_path}")
    raw = text_path.read_bytes()
    decoded = raw.decode("utf-8")  # falha cedo se não for UTF-8
    sha = hashlib.sha256(raw).hexdigest()
    size = len(raw)
    documents = _document_count(decoded)
    if documents < 2:
        raise ValueError("o arquivo precisa de pelo menos dois documentos separados por linha em branco")

    # Os caminhos do manifesto são relativos à pasta do manifesto.
    root = manifest_path.parent.resolve()
    relative = text_path.resolve().relative_to(root).as_posix()

    source = {
        "source_id": source_id,
        "file": relative,
        "reference": reference,
        "license": license_name,
        "rights_basis": rights_basis,
        "language": language,
        "domain": domain,
        "collected_at": collected_at,
        "sha256": sha,
        "bytes": size,
        "document_count": documents,
    }
    manifest = {
        "schema_version": 1,
        "corpus_id": corpus_id,
        "sources": [source],
        "training_corpus": {
            "file": relative,
            "sha256": sha,
            "bytes": size,
            "document_count": documents,
            "derived_from": [source_id],
            "preparation_notes": preparation_notes,
        },
    }
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return validate_corpus_manifest(manifest_path, root_dir=root)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Create and validate a single-source Kellvyn corpus manifest")
    parser.add_argument("--text", required=True, type=Path, help="UTF-8 file; documents separated by blank lines")
    parser.add_argument("--manifest", required=True, type=Path, help="manifest JSON to write")
    parser.add_argument("--corpus-id", default="kellvyn-pt-v1")
    parser.add_argument("--source-id", required=True)
    parser.add_argument("--reference", required=True, help="URL ou referência bibliográfica da fonte")
    parser.add_argument("--license", dest="license_name", required=True, help="licença explícita, nunca 'unknown'")
    parser.add_argument("--rights-basis", required=True)
    parser.add_argument("--language", default="pt-BR")
    parser.add_argument("--domain", required=True)
    parser.add_argument("--collected-at", required=True, help="YYYY-MM-DD")
    parser.add_argument(
        "--preparation-notes",
        default="Arquivo único usado sem alteração de conteúdo.",
    )
    return parser


def main() -> None:
    args = build_parser().parse_args()
    report = build_manifest(
        text_path=args.text,
        manifest_path=args.manifest,
        corpus_id=args.corpus_id,
        source_id=args.source_id,
        reference=args.reference,
        license_name=args.license_name,
        rights_basis=args.rights_basis,
        language=args.language,
        domain=args.domain,
        collected_at=args.collected_at,
        preparation_notes=args.preparation_notes,
    )
    print(f"manifesto validado: {report['corpus_id']} ({report['total_documents']} documentos, {report['total_bytes']} bytes)")


if __name__ == "__main__":
    main()
