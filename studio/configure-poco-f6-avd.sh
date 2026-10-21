#!/usr/bin/env bash
set -euo pipefail

AVD_CONFIG="$HOME/.android/avd/studio-poco-f6-virtual.avd/config.ini"
if [[ ! -f "$AVD_CONFIG" ]]; then
  echo "Poco F6 AVD config not found at $AVD_CONFIG" >&2
  find "$HOME/.android/avd" -maxdepth 3 -name config.ini -print >&2 || true
  exit 1
fi

python3 - "$AVD_CONFIG" <<'PY'
import pathlib, sys

config_path = pathlib.Path(sys.argv[1])
lines = config_path.read_text().splitlines()
settings = {
    "hw.lcd.width": "1220",
    "hw.lcd.height": "2712",
    "hw.lcd.density": "440",
    "hw.lcd.depth": "32",
}
seen = set()
updated = []
for line in lines:
    key, sep, value = line.partition("=")
    key = key.strip()
    if sep and key in settings:
        updated.append(f"{key}={settings[key]}")
        seen.add(key)
    else:
        updated.append(line)
for key, value in settings.items():
    if key not in seen:
        updated.append(f"{key}={value}")
config_path.write_text("\n".join(updated) + "\n")
print("Configured virtual Poco F6 display:")
for key, value in settings.items():
    print(f"  {key}={value}")
PY

grep -E '^hw\.lcd\.(width|height|density|depth)=' "$AVD_CONFIG"
