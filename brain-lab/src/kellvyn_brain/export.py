"""Export a trained Kellvyn checkpoint to a portable torch.export .pt2 graph.

This is an intermediate PyTorch export artifact, not yet an ExecuTorch .pte file
and not an Android runtime package.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

import torch

from .model import KellvynExportModule, load_checkpoint, load_checkpoint_metadata, load_tokenizer_state


def file_sha256(path: Path, chunk_bytes: int = 4 * 1024 * 1024) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while chunk := handle.read(chunk_bytes):
            digest.update(chunk)
    return digest.hexdigest()


def export_checkpoint(
    checkpoint_path: str | Path,
    output_path: str | Path,
    *,
    sequence_length: int | None = None,
    batch_size: int = 1,
    device: str = "cpu",
) -> dict[str, Any]:
    checkpoint = Path(checkpoint_path)
    output = Path(output_path)
    if not checkpoint.is_file():
        raise FileNotFoundError(f"checkpoint not found: {checkpoint}")
    if batch_size <= 0:
        raise ValueError("batch_size must be positive")
    target_device = torch.device(device)
    model, step = load_checkpoint(checkpoint, map_location=target_device)
    metadata = load_checkpoint_metadata(checkpoint)
    tokenizer_state = load_tokenizer_state(checkpoint)

    selected_length = sequence_length or min(model.config.max_seq_len, 16)
    if selected_length < 1 or selected_length > model.config.max_seq_len:
        raise ValueError("sequence_length must be within the checkpoint context limit")

    model.eval()
    export_module = KellvynExportModule(model).to(target_device).eval()
    sample = torch.zeros(
        (batch_size, selected_length),
        dtype=torch.long,
        device=target_device,
    )

    with torch.no_grad():
        eager_logits = export_module(sample)
    exported = torch.export.export(export_module, (sample,))
    with torch.no_grad():
        exported_logits = exported.module()(sample)
    torch.testing.assert_close(eager_logits, exported_logits, rtol=1e-5, atol=1e-6)

    output.parent.mkdir(parents=True, exist_ok=True)
    torch.export.save(exported, output)

    tokenizer_json = json.dumps(
        tokenizer_state,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    export_metadata = {
        "format": "kellvyn-torch-export-v1",
        "artifact": output.name,
        "artifact_sha256": file_sha256(output),
        "checkpoint_sha256": file_sha256(checkpoint),
        "checkpoint_step": step,
        "weights_origin": "random_initialization_and_kellvyn_training_only",
        "external_pretrained_weights": metadata.get("external_pretrained_weights") is False,
        "tokenizer_format": tokenizer_state.get("format"),
        "tokenizer_sha256": hashlib.sha256(tokenizer_json).hexdigest(),
        "model_config": {
            "vocab_size": model.config.vocab_size,
            "d_model": model.config.d_model,
            "n_layers": model.config.n_layers,
            "n_heads": model.config.n_heads,
            "max_seq_len": model.config.max_seq_len,
        },
        "input_shape": [batch_size, selected_length],
        "output_shape": list(exported_logits.shape),
        "device_used_for_export": str(target_device),
        "eager_export_logits_match": True,
        "runtime_status": "torch.export graph only; ExecuTorch conversion and Android execution not yet verified",
    }
    metadata_path = output.with_suffix(output.suffix + ".json")
    metadata_path.write_text(
        json.dumps(export_metadata, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return export_metadata


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Export a trained Kellvyn checkpoint to a torch.export .pt2 graph"
    )
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--output", required=True, help="destination .pt2 path")
    parser.add_argument("--sequence-length", type=int, default=None)
    parser.add_argument("--batch-size", type=int, default=1)
    parser.add_argument("--device", default="cpu")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    result = export_checkpoint(
        args.checkpoint,
        args.output,
        sequence_length=args.sequence_length,
        batch_size=args.batch_size,
        device=args.device,
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
