#!/usr/bin/env python3
"""Compare reference and Android screenshots without silently changing geometry."""
from __future__ import annotations
import argparse, json
from pathlib import Path
import numpy as np
from PIL import Image, ImageChops, ImageEnhance
try:
    from skimage.metrics import structural_similarity
except ImportError:
    structural_similarity = None
def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", required=True, type=Path)
    parser.add_argument("--candidate", required=True, type=Path, help="Android native screenshot")
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--pixel-threshold", type=int, default=16, help="Per-channel threshold for changed-pixel ratio (0-255)")
    args = parser.parse_args()
    if not args.reference.is_file() or not args.candidate.is_file():
        parser.error("both screenshot files must exist")
    if not 0 <= args.pixel_threshold <= 255:
        parser.error("--pixel-threshold must be between 0 and 255")
    reference = Image.open(args.reference).convert("RGB")
    candidate = Image.open(args.candidate).convert("RGB")
    if reference.size != candidate.size:
        print(f"ERROR: sizes differ: reference={reference.size}, candidate={candidate.size}. Capture both at the same viewport; no automatic resize was applied.")
        return 2
    ref_arr = np.asarray(reference, dtype=np.float32)
    can_arr = np.asarray(candidate, dtype=np.float32)
    abs_diff = np.abs(ref_arr - can_arr)
    mae = float(abs_diff.mean())
    rmse = float(np.sqrt(np.mean(np.square(ref_arr - can_arr))))
    changed = np.any(abs_diff > args.pixel_threshold, axis=2)
    changed_percent = float(changed.mean() * 100.0)
    ssim = None
    if structural_similarity is not None:
        ssim = float(structural_similarity(np.asarray(reference), np.asarray(candidate), channel_axis=2, data_range=255))
    args.output_dir.mkdir(parents=True, exist_ok=True)
    difference = ImageChops.difference(reference, candidate)
    ImageEnhance.Contrast(difference).enhance(4.0).save(args.output_dir / "difference-amplified.png")
    Image.blend(reference, candidate, 0.5).save(args.output_dir / "overlay.png")
    metrics = {"reference": str(args.reference), "candidate": str(args.candidate), "size": list(reference.size), "pixelThreshold": args.pixel_threshold, "meanAbsoluteError": mae, "rootMeanSquareError": rmse, "changedPixelsPercent": changed_percent, "structuralSimilarity": ssim, "note": "Metrics are interpretable only when captures show the same screen state, orientation, viewport, and content."}
    (args.output_dir / "metrics.json").write_text(json.dumps(metrics, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(metrics, indent=2))
    print(f"Saved visual diff and overlay under: {args.output_dir}")
    return 0
if __name__ == "__main__":
    raise SystemExit(main())
