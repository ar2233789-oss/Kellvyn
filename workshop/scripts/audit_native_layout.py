#!/usr/bin/env python3
"""Structural audit for the existing native screen and reference-source lock."""
from __future__ import annotations
import argparse, hashlib, json, re, sys
from pathlib import Path
REQUIRED_NATIVE_MARKERS = {
    "thinking indicator": "private fun LovableThinkingBrain",
    "Compose frame clock": "withFrameNanos { frameTimeNanos ->",
    "elapsed animation clock": "rainElapsedNanos = frameTimeNanos - animationStart",
    "rain geometry spacing": "val spacing = 13.dp.toPx()",
    "rain trail length": "val dropLength = 11.dp.toPx()",
    "rain diagonal slant": "val horizontalSlant = 3.6.dp.toPx()",
    "rain phase formula": "elapsed / 2.2 * speed",
    "rain effect renderer": '"chuva" ->',
    "snow effect preserved": '"neve" ->',
    "leaf effect preserved": '"folhas" ->',
    "native Compose canvas": "Canvas(modifier =",
}
FORBIDDEN_UI_LABELS = ("modo leve", "modo pesado", "qwen code", "qwen coder", "núcleo variável")
def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--strict", action="store_true", help="Fail unless the exact Lovable source baseline is imported")
    parser.add_argument("--report", type=Path, help="Optional JSON report path")
    args = parser.parse_args()
    root = args.repo_root.resolve()
    kotlin_path = root / "app/src/main/java/com/kellvyn/assistant/MainActivity.kt"
    lock_path = root / "workshop/reference/source-lock.json"
    report = {"nativeChecks": [], "sourceLock": {}, "forbiddenVisibleLabels": [], "visualParity": "BLOCKED"}
    errors = 0
    if not kotlin_path.is_file():
        print(f"ERROR: native source not found: {kotlin_path}")
        return 2
    native = kotlin_path.read_text(encoding="utf-8")
    for label, marker in REQUIRED_NATIVE_MARKERS.items():
        passed = marker in native
        report["nativeChecks"].append({"check": label, "pass": passed, "marker": marker})
        print(f"{'PASS' if passed else 'FAIL'}  {label}")
        if not passed:
            errors += 1
    visible_texts = [m.group(1).strip().lower() for m in re.finditer(r"\bText\(\s*(?:text\s*=\s*)?\"([^\"]*)\"", native)]
    forbidden = [label for label in FORBIDDEN_UI_LABELS if any(label in value for value in visible_texts)]
    report["forbiddenVisibleLabels"] = forbidden
    if forbidden:
        print("FAIL  forbidden visible labels: " + ", ".join(forbidden))
        errors += 1
    else:
        print("PASS  no known unwanted mode/provider labels in literal Text() labels")
    lock = json.loads(lock_path.read_text(encoding="utf-8")) if lock_path.is_file() else {"status": "missing"}
    source_files = lock.get("sourceFiles") if isinstance(lock.get("sourceFiles"), list) else []
    file_checks = []
    all_files_valid = bool(source_files)
    for entry in source_files:
        if not isinstance(entry, dict) or not entry.get("path") or not entry.get("sha256"):
            file_checks.append({"path": entry.get("path") if isinstance(entry, dict) else None, "valid": False, "reason": "missing path or sha256"})
            all_files_valid = False
            continue
        source_path = root / entry["path"]
        exists = source_path.is_file()
        actual_hash = hashlib.sha256(source_path.read_bytes()).hexdigest() if exists else None
        valid = exists and actual_hash == entry["sha256"]
        file_checks.append({"path": entry["path"], "exists": exists, "sha256Matches": bool(exists and actual_hash == entry["sha256"]), "valid": valid})
        all_files_valid = all_files_valid and valid
    screenshot_rel = lock.get("baselineScreenshot")
    screenshot_path = root / screenshot_rel if isinstance(screenshot_rel, str) and screenshot_rel else None
    screenshot_exists = bool(screenshot_path and screenshot_path.is_file())
    screenshot_hash = hashlib.sha256(screenshot_path.read_bytes()).hexdigest() if screenshot_exists and screenshot_path else None
    screenshot_hash_valid = bool(screenshot_exists and lock.get("baselineScreenshotSha256") and screenshot_hash == lock.get("baselineScreenshotSha256"))
    report["sourceLock"] = {"status": lock.get("status"), "projectId": lock.get("projectId"), "sourceCommit": lock.get("sourceCommit"), "sourceFileChecks": file_checks, "baselineScreenshotExists": screenshot_exists, "baselineScreenshotHashMatches": screenshot_hash_valid}
    imported = lock.get("status") == "imported" and bool(lock.get("sourceCommit")) and all_files_valid and screenshot_hash_valid
    if imported:
        print("PASS  exact Lovable source files and screenshot match pinned SHA-256 hashes")
        report["visualParity"] = "READY_FOR_SCREENSHOT_COMPARISON"
    else:
        print(f"BLOCKED  exact Lovable layout is not verifiably imported (status={lock.get('status')!r}); visual parity is NOT confirmed")
        if args.strict:
            errors += 1
    if args.report:
        report_path = args.report if args.report.is_absolute() else root / args.report
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"Report: {report_path}")
    if errors:
        print(f"AUDIT FAILED: {errors} issue(s)")
        return 2
    print("STRUCTURAL AUDIT PASS; visual equivalence remains blocked until the source lock is imported.")
    return 0
if __name__ == "__main__":
    sys.exit(main())
