#!/usr/bin/env python3
"""Render a diagnostic rain loop mirroring the native Compose Canvas formula."""
from __future__ import annotations
import argparse
from pathlib import Path
from PIL import Image, ImageDraw
NIGHT = (11, 20, 19, 255)
RAIN = (215, 229, 236)
def render_frame(frame: int, frame_count: int, width: int, height: int, density: float) -> Image.Image:
    elapsed = frame / frame_count * 2.2
    image = Image.new("RGBA", (width, height), NIGHT)
    draw = ImageDraw.Draw(image, "RGBA")
    spacing = 13.0 * density
    drop_length = 11.0 * density
    drop_columns = max(1, int(width / spacing))
    drop_count = drop_columns * 2 + 12
    horizontal_slant = 3.6 * density
    for drop in range(drop_count):
        lane = (drop * 53) % drop_count
        x = (lane / drop_count) * width
        speed = 0.65 + ((drop * 17) % 55) / 100.0
        phase = (elapsed / 2.2 * speed + ((drop * 37) % 97) / 97.0) % 1.0
        y = phase * (height + drop_length + 10.0 * density) - drop_length
        if -drop_length <= y <= height:
            opacity = 0.28 + ((drop * 13) % 20) / 100.0
            alpha = round(opacity * 255)
            slant = horizontal_slant * (0.7 + (drop % 4) * 0.18)
            width_px = max(1, round((0.8 + (drop % 3) * 0.15) * density))
            draw.line((x, y, x - slant, y + drop_length), fill=(*RAIN, alpha), width=width_px)
    return image.convert("RGB")
def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--frames", type=int, default=24)
    parser.add_argument("--width", type=int, default=393)
    parser.add_argument("--height", type=int, default=873)
    parser.add_argument("--density", type=float, default=1.0)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    if args.frames < 2 or args.width <= 0 or args.height <= 0 or args.density <= 0:
        parser.error("frames must be >= 2; dimensions and density must be positive")
    frames_dir = args.output_dir / "frames"
    frames_dir.mkdir(parents=True, exist_ok=True)
    for i in range(args.frames):
        render_frame(i, args.frames, args.width, args.height, args.density).save(frames_dir / f"frame-{i:03d}.png")
    print(f"Rendered {args.frames} diagnostic frames at {args.width}x{args.height}: {frames_dir}")
    print("Diagnostic preview only; verify the real Android capture separately.")
    return 0
if __name__ == "__main__":
    raise SystemExit(main())
