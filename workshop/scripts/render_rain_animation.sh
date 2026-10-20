#!/usr/bin/env bash
set -euo pipefail
OUTPUT_DIR="/tmp/kellvyn-rain-calibration"
FRAMES=24
FPS=12
if [ "$#" -ge 1 ]; then OUTPUT_DIR="$1"; fi
if [ "$#" -ge 2 ]; then FRAMES="$2"; fi
if [ "$#" -ge 3 ]; then FPS="$3"; fi
ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
command -v ffmpeg >/dev/null || { echo 'FFmpeg is required.' >&2; exit 2; }
python3 "$ROOT_DIR/workshop/scripts/render_rain_preview.py" --frames "$FRAMES" --output-dir "$OUTPUT_DIR"
ffmpeg -y -hide_banner -loglevel error -framerate "$FPS" -i "$OUTPUT_DIR/frames/frame-%03d.png" -vf "split[s0][s1];[s0]palettegen=max_colors=96[p];[s1][p]paletteuse" -loop 0 "$OUTPUT_DIR/rain-calibration.gif"
printf 'Rendered %s diagnostic frames to %s\n' "$FRAMES" "$OUTPUT_DIR/rain-calibration.gif"
