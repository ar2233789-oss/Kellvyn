"""Strict provenance and integrity validation for Kellvyn training corpora."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from datetime import date
from pathlib import Path
from typing import Any


_REQUIRED_TEXT_FIELDS = (
    "source_id", "file", "reference", "license", "rights_basis",
    "language", "domain", "collected_at", "sha256",
)
_SHA256_RE = re.compile(r"^[0-9a-fA-F]{64}$")


def _document_count(text: str) -> int:
    normalized = text.replace("\r\n", "\n").replace("\r", "\n")
    return sum(1 for part in re.split(r"\n\s*\n", normalized) if part.strip())


def _required_text(record: dict[str, Any], field: str, source_label: str) -> str:
    value = record.get(field)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{source_label}: required field '{field}' is missing or blank")
    return value.strip()


def validate_corpus_manifest(
    manifest_path: str | Path,
    *,
    root_dir: str | Path | None = None,
) -> dict[str, Any]:
    """Validate source provenance and file integrity without fetching external URLs."""
    manifest_file = Path(manifest_path)
    if not manifest_file.is_file():
        raise FileNotFoundError(f"corpus manifest not found: {manifest_file}")
    manifest_bytes = manifest_file.read_bytes()
    try:
        manifest = json.loads(manifest_bytes.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError(f"manifest must be valid UTF-8 JSON: {error}") from error

    if not isinstance(manifest, dict) or manifest.get("schema_version") != 1:
        raise ValueError("manifest schema_version must be 1")
    corpus_id = manifest.get("corpus_id")
    if not isinstance(corpus_id, str) or not corpus_id.strip():
        raise ValueError("manifest corpus_id is required")
    sources = manifest.get("sources")
    if not isinstance(sources, list) or not sources:
        raise ValueError("manifest sources must be a non-empty list")

    data_root = Path(root_dir) if root_dir is not None else manifest_file.parent
    if not data_root.exists() or not data_root.is_dir():
        raise NotADirectoryError(f"corpus root is not a directory: {data_root}")
    resolved_root = data_root.resolve()

    source_ids: set[str] = set()
    resolved_files: set[Path] = set()
    verified_sources: list[dict[str, Any]] = []
    total_bytes = 0
    total_documents = 0

    for index, raw_source in enumerate(sources):
        label = f"source[{index}]"
        if not isinstance(raw_source, dict):
            raise ValueError(f"{label}: source entry must be an object")
        values = {
            field: _required_text(raw_source, field, label)
            for field in _REQUIRED_TEXT_FIELDS
        }
        source_id = values["source_id"]
        if source_id in source_ids:
            raise ValueError(f"{label}: duplicate source_id '{source_id}'")
        source_ids.add(source_id)

        if values["license"].lower() in {"unknown", "unspecified", "none", "n/a", "tbd"}:
            raise ValueError(f"{label}: license must be explicit, not '{values['license']}'")
        try:
            collected = date.fromisoformat(values["collected_at"])
        except ValueError as error:
            raise ValueError(f"{label}: collected_at must use YYYY-MM-DD") from error
        if collected.isoformat() != values["collected_at"]:
            raise ValueError(f"{label}: collected_at must use YYYY-MM-DD")

        relative_path = Path(values["file"])
        if relative_path.is_absolute() or ".." in relative_path.parts:
            raise ValueError(f"{label}: file must be a relative path inside the corpus root")
        candidate = (resolved_root / relative_path).resolve()
        if candidate != resolved_root and resolved_root not in candidate.parents:
            raise ValueError(f"{label}: file resolves outside the corpus root")
        if candidate in resolved_files:
            raise ValueError(f"{label}: duplicate file path '{values['file']}'")
        resolved_files.add(candidate)
        if not candidate.is_file():
            raise FileNotFoundError(f"{label}: corpus source file not found: {candidate}")

        expected_hash = values["sha256"].lower()
        if not _SHA256_RE.fullmatch(expected_hash):
            raise ValueError(f"{label}: sha256 must contain exactly 64 hexadecimal characters")
        raw_data = candidate.read_bytes()
        actual_hash = hashlib.sha256(raw_data).hexdigest()
        if actual_hash != expected_hash:
            raise ValueError(f"{label}: sha256 mismatch for '{values['file']}'")

        declared_bytes = raw_source.get("bytes")
        if isinstance(declared_bytes, bool) or not isinstance(declared_bytes, int) or declared_bytes < 0:
            raise ValueError(f"{label}: bytes must be a non-negative integer")
        if declared_bytes != len(raw_data):
            raise ValueError(f"{label}: byte count mismatch for '{values['file']}'")

        try:
            decoded = raw_data.decode("utf-8")
        except UnicodeDecodeError as error:
            raise ValueError(f"{label}: source file must be valid UTF-8") from error
        declared_documents = raw_source.get("document_count")
        if (
            isinstance(declared_documents, bool)
            or not isinstance(declared_documents, int)
            or declared_documents <= 0
        ):
            raise ValueError(f"{label}: document_count must be a positive integer")
        actual_documents = _document_count(decoded)
        if declared_documents != actual_documents:
            raise ValueError(
                f"{label}: document count mismatch for '{values['file']}': "
                f"declared {declared_documents}, found {actual_documents}"
            )

        total_bytes += len(raw_data)
        total_documents += actual_documents
        verified_sources.append({
            "source_id": source_id,
            "file": relative_path.as_posix(),
            "reference": values["reference"],
            "license": values["license"],
            "rights_basis": values["rights_basis"],
            "language": values["language"],
            "domain": values["domain"],
            "collected_at": values["collected_at"],
            "sha256": actual_hash,
            "bytes": len(raw_data),
            "document_count": actual_documents,
            "integrity_verified": True,
        })

    training = manifest.get("training_corpus")
    if not isinstance(training, dict):
        raise ValueError("manifest training_corpus must be an object")
    training_file = _required_text(training, "file", "training_corpus")
    preparation_notes = _required_text(training, "preparation_notes", "training_corpus")
    derived_from = training.get("derived_from")
    if not isinstance(derived_from, list) or not derived_from:
        raise ValueError("training_corpus derived_from must list source IDs")
    if not all(isinstance(item, str) and item.strip() for item in derived_from):
        raise ValueError("training_corpus derived_from must list source IDs")
    if len(set(derived_from)) != len(derived_from):
        raise ValueError("training_corpus derived_from contains duplicate source IDs")
    unknown_sources = set(derived_from).difference(source_ids)
    if unknown_sources:
        raise ValueError(f"training_corpus derived_from references unknown sources: {sorted(unknown_sources)}")

    training_relative = Path(training_file)
    if training_relative.is_absolute() or ".." in training_relative.parts:
        raise ValueError("training_corpus file must be a relative path inside the corpus root")
    training_path = (resolved_root / training_relative).resolve()
    if training_path != resolved_root and resolved_root not in training_path.parents:
        raise ValueError("training_corpus file resolves outside the corpus root")
    if not training_path.is_file():
        raise FileNotFoundError(f"training_corpus file not found: {training_path}")
    training_hash = _required_text(training, "sha256", "training_corpus").lower()
    if not _SHA256_RE.fullmatch(training_hash):
        raise ValueError("training_corpus sha256 must contain exactly 64 hexadecimal characters")
    training_raw = training_path.read_bytes()
    actual_training_hash = hashlib.sha256(training_raw).hexdigest()
    if actual_training_hash != training_hash:
        raise ValueError("training_corpus: sha256 mismatch")
    expected_training_bytes = training.get("bytes")
    if isinstance(expected_training_bytes, bool) or not isinstance(expected_training_bytes, int) or expected_training_bytes <= 0:
        raise ValueError("training_corpus bytes must be a positive integer")
    if expected_training_bytes != len(training_raw):
        raise ValueError("training_corpus: byte count mismatch")
    try:
        decoded_training = training_raw.decode("utf-8")
    except UnicodeDecodeError as error:
        raise ValueError("training_corpus file must be valid UTF-8") from error
    expected_training_documents = training.get("document_count")
    if isinstance(expected_training_documents, bool) or not isinstance(expected_training_documents, int) or expected_training_documents <= 0:
        raise ValueError("training_corpus document_count must be a positive integer")
    actual_training_documents = sum(
        1 for part in re.split(r"\\n\\s*\\n", decoded_training) if part.strip()
    )
    if expected_training_documents != actual_training_documents:
        raise ValueError("training_corpus: document count mismatch")

    return {
        "schema_version": 1,
        "corpus_id": corpus_id.strip(),
        "manifest_sha256": hashlib.sha256(manifest_bytes).hexdigest(),
        "source_count": len(verified_sources),
        "total_bytes": total_bytes,
        "total_documents": total_documents,
        "training_corpus": {
            "file": training_relative.as_posix(),
            "sha256": actual_training_hash,
            "bytes": len(training_raw),
            "document_count": actual_training_documents,
            "derived_from": derived_from,
            "preparation_notes": preparation_notes,
            "integrity_verified": True,
        },
        "verified": True,
        "sources": verified_sources,
        "legal_note": (
            "This validator checks that provenance and rights fields are explicit; "
            "it does not determine whether a license or rights basis legally permits training."
        ),
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate provenance, hashes and UTF-8 document counts for a Kellvyn corpus"
    )
    parser.add_argument("--manifest", required=True, help="corpus manifest JSON")
    parser.add_argument("--root", default=None, help="directory used to resolve source files; defaults to manifest directory")
    parser.add_argument("--output", default=None, help="optional JSON report destination")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    result = validate_corpus_manifest(args.manifest, root_dir=args.root)
    rendered = json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
    if args.output:
        destination = Path(args.output)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(rendered, encoding="utf-8")
    print(rendered, end="")


if __name__ == "__main__":
    main()
