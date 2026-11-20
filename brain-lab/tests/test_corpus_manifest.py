from __future__ import annotations

import hashlib
import json

import pytest

from kellvyn_brain.corpus_manifest import validate_corpus_manifest


def _write_manifest(tmp_path, *, content: bytes | None = None, **overrides):
    root = tmp_path / "corpus"
    root.mkdir(exist_ok=True)
    payload = content if content is not None else "primeiro documento\n\nsegundo documento".encode("utf-8")
    data_file = root / "source.txt"
    data_file.write_bytes(payload)
    source = {
        "source_id": "pt-education-001",
        "file": "source.txt",
        "reference": "https://example.org/dataset-card",
        "license": "CC-BY-4.0",
        "rights_basis": "CC-BY-4.0; attribution retained",
        "language": "pt-BR",
        "domain": "education",
        "collected_at": "2026-11-20",
        "sha256": hashlib.sha256(payload).hexdigest(),
        "bytes": len(payload),
        "document_count": 2,
    }
    source.update(overrides)
    manifest = {
        "schema_version": 1,
        "corpus_id": "kellvyn-evaluation-pt-v1",
        "sources": [source],
    }
    manifest_file = root / "manifest.json"
    manifest_file.write_text(json.dumps(manifest), encoding="utf-8")
    return manifest_file, root


def test_manifest_validates_provenance_hash_bytes_and_documents(tmp_path) -> None:
    manifest, root = _write_manifest(tmp_path)
    result = validate_corpus_manifest(manifest, root_dir=root)
    assert result["verified"] is True
    assert result["source_count"] == 1
    assert result["total_bytes"] == len("primeiro documento\n\nsegundo documento".encode("utf-8"))
    assert result["total_documents"] == 2
    assert result["sources"][0]["integrity_verified"] is True
    assert len(result["manifest_sha256"]) == 64


def test_manifest_rejects_path_traversal(tmp_path) -> None:
    manifest, root = _write_manifest(tmp_path, **{"file": "../secret.txt"})
    with pytest.raises(ValueError, match="relative path inside the corpus root"):
        validate_corpus_manifest(manifest, root_dir=root)


def test_manifest_rejects_hash_mismatch(tmp_path) -> None:
    manifest, root = _write_manifest(tmp_path, **{"sha256": "0" * 64})
    with pytest.raises(ValueError, match="sha256 mismatch"):
        validate_corpus_manifest(manifest, root_dir=root)


def test_manifest_rejects_missing_license_metadata(tmp_path) -> None:
    manifest, root = _write_manifest(tmp_path, **{"license": ""})
    with pytest.raises(ValueError, match="license"):
        validate_corpus_manifest(manifest, root_dir=root)


def test_manifest_rejects_unverifiable_license_label(tmp_path) -> None:
    manifest, root = _write_manifest(tmp_path, **{"license": "unknown"})
    with pytest.raises(ValueError, match="license must be explicit"):
        validate_corpus_manifest(manifest, root_dir=root)


def test_manifest_rejects_incorrect_document_count(tmp_path) -> None:
    manifest, root = _write_manifest(tmp_path, **{"document_count": 9})
    with pytest.raises(ValueError, match="document count mismatch"):
        validate_corpus_manifest(manifest, root_dir=root)


def test_manifest_rejects_invalid_utf8_source(tmp_path) -> None:
    manifest, root = _write_manifest(tmp_path, content=b"valid\xffinvalid")
    with pytest.raises(ValueError, match="valid UTF-8"):
        validate_corpus_manifest(manifest, root_dir=root)
