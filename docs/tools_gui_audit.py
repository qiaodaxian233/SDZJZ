#!/usr/bin/env python3
"""Audit SDZJZ GUI assets, palette discipline, localization, and layout risks."""

from __future__ import annotations

import argparse
import json
import re
import struct
from pathlib import Path

# Minimum GUI-space viewport the skill requires screens to survive.
MIN_VIEWPORT_W = 320
MIN_VIEWPORT_H = 240
SAFE_MARGIN = 8

EXPECTED_ASSETS = {
    "slot.png": (18, 18),
    "button.png": (200, 32),
    "data_panel_gui.png": None,
    "trade_center_gui.png": None,
    "structure_core_canvas.png": None,
    "structure_core_gui.png": None,
    "super_bench_gui.png": None,
}

TRANSLATABLE_RE = re.compile(r'Text\.translatable\(\s*"([^"]+)"')
LITERAL_RE = re.compile(r"Text\.literal\(")
ARGB_RE = re.compile(r"0x[0-9A-Fa-f]{8}")
SKIN_CONST_RE = re.compile(r"public\s+static\s+final\s+int\s+\w+\s*=\s*(0x[0-9A-Fa-f]{8})")
FIXED_BUTTON_RE = re.compile(
    r"new\s+(?:Sci|Term)Button\(\s*(\d+)\s*,\s*([^,]+?)\s*,\s*(\d+)\s*,\s*(\d+)\s*,"  # m203 尺子跟上 TermButton
)


def png_size(path: Path) -> tuple[int, int]:
    with path.open("rb") as handle:
        signature = handle.read(8)
        if signature != b"\x89PNG\r\n\x1a\n":
            raise ValueError("not a PNG")
        length = struct.unpack(">I", handle.read(4))[0]
        chunk = handle.read(4)
        if chunk != b"IHDR" or length < 8:
            raise ValueError("missing IHDR")
        return struct.unpack(">II", handle.read(8))


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def load_skin_palette(skin_path: Path) -> set[str]:
    """RGB values (lowercase 6-hex) declared in SciSkin; the only sanctioned colors."""
    palette: set[str] = set()
    if skin_path.exists():
        for value in SKIN_CONST_RE.findall(read_text(skin_path)):
            palette.add(value[-6:].lower())
    return palette


def is_grayscale(rgb: str) -> bool:
    return rgb[0:2] == rgb[2:4] == rgb[4:6]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("project", nargs="?", default=".")
    parser.add_argument("--json", action="store_true", dest="as_json")
    args = parser.parse_args()

    root = Path(args.project).expanduser().resolve()
    # m406 多源集：客户端屏已迁 xplat，取存在的那个（路径逻辑见 docs/srcroots.py）
    src = root / "xplat/src/main/java/com/sdzjz"
    if not (src / "client").is_dir():
        src = root / "src/main/java/com/sdzjz"
    client = src / "client"
    resources = root / "src/main/resources/assets/sdzjz"
    gui = resources / "textures/gui"
    lang = resources / "lang"

    findings: dict = {
        "project": str(root),
        "errors": [],
        "warnings": [],
        "assets": {},
        "screens": [],
        "screen_literal_calls": 0,
        "other_literal_calls": 0,
        "translatable_calls": 0,
        "missing_translation_keys": [],
        "off_palette_colors": [],
        "fixed_button_overflow": [],
        "fixed_button_candidates": [],
    }
    errors: list[str] = findings["errors"]
    warnings: list[str] = findings["warnings"]

    mod_json = root / "src/main/resources/fabric.mod.json"
    if not mod_json.exists():
        errors.append("Missing src/main/resources/fabric.mod.json")
    else:
        try:
            mod = json.loads(read_text(mod_json))
            if mod.get("id") != "sdzjz":
                warnings.append(f"Unexpected mod id: {mod.get('id')!r}")
        except (OSError, json.JSONDecodeError) as exc:
            errors.append(f"Invalid fabric.mod.json: {exc}")

    skin_path = client / "SciSkin.java"
    if not skin_path.exists():
        errors.append("Missing client/SciSkin.java")
    palette = load_skin_palette(skin_path)

    used_keys: set[str] = set()

    if not client.exists():
        errors.append("Missing SDZJZ client package")
    else:
        for path in sorted(client.glob("*Screen.java")):
            findings["screens"].append(path.name)
            text = read_text(path)
            findings["screen_literal_calls"] += len(LITERAL_RE.findall(text))
            for line_number, line in enumerate(text.splitlines(), 1):
                # Palette discipline: any ARGB literal whose RGB is not in
                # SciSkin and not pure grayscale bypasses the design system.
                for token in ARGB_RE.findall(line):
                    rgb = token[-6:].lower()
                    if palette and rgb not in palette and not is_grayscale(rgb):
                        findings["off_palette_colors"].append(
                            f"{path.name}:{line_number}: {token}"
                        )
                # Fixed-coordinate buttons: flag all, and prove overflow
                # against the minimum supported viewport where possible.
                for match in FIXED_BUTTON_RE.finditer(line):
                    x, w = int(match.group(1)), int(match.group(3))
                    entry = f"{path.name}:{line_number}: {line.strip()}"
                    findings["fixed_button_candidates"].append(entry)
                    if x + w > MIN_VIEWPORT_W - SAFE_MARGIN:
                        findings["fixed_button_overflow"].append(
                            f"{path.name}:{line_number}: right edge {x + w}px exceeds "
                            f"{MIN_VIEWPORT_W - SAFE_MARGIN}px usable width at {MIN_VIEWPORT_W}px viewport"
                        )

    # Whole-source localization scan: player-facing text is not confined to screens.
    if src.exists():
        for path in src.rglob("*.java"):
            text = read_text(path)
            findings["translatable_calls"] += len(TRANSLATABLE_RE.findall(text))
            used_keys.update(TRANSLATABLE_RE.findall(text))
            if client not in path.parents or not path.name.endswith("Screen.java"):
                findings["other_literal_calls"] += len(LITERAL_RE.findall(text))

    for name, expected in EXPECTED_ASSETS.items():
        path = gui / name
        if not path.exists():
            errors.append(f"Missing GUI asset: {name}")
            continue
        try:
            size = png_size(path)
            findings["assets"][name] = {"width": size[0], "height": size[1]}
            if expected and size != expected:
                errors.append(
                    f"{name} is {size[0]}x{size[1]}, expected {expected[0]}x{expected[1]}"
                )
        except (OSError, ValueError, struct.error) as exc:
            errors.append(f"Cannot inspect {name}: {exc}")

    lang_keys: dict[str, set[str]] = {}
    for name in ("zh_cn.json", "en_us.json"):
        path = lang / name
        if not path.exists():
            errors.append(f"Missing language file: {name}")
            continue
        try:
            data = json.loads(read_text(path))
            lang_keys[name] = set(data)
        except (OSError, json.JSONDecodeError) as exc:
            errors.append(f"Invalid {name}: {exc}")

    if len(lang_keys) == 2:
        zh_only = sorted(lang_keys["zh_cn.json"] - lang_keys["en_us.json"])
        en_only = sorted(lang_keys["en_us.json"] - lang_keys["zh_cn.json"])
        if zh_only:
            errors.append(f"Keys only in zh_cn.json: {zh_only[:20]}")
        if en_only:
            errors.append(f"Keys only in en_us.json: {en_only[:20]}")
        # Mod-namespace keys referenced in code must exist in both languages.
        # Skip trailing-dot literals (dynamic prefix concatenation) and
        # vanilla keys, which resolve through Minecraft's own lang files.
        all_keys = lang_keys["zh_cn.json"] | lang_keys["en_us.json"]
        for key in sorted(used_keys):
            if key.endswith(".") or "sdzjz" not in key:
                continue
            if key not in all_keys:
                findings["missing_translation_keys"].append(key)
        if findings["missing_translation_keys"]:
            errors.append(
                f"Text.translatable keys missing from lang files: "
                f"{findings['missing_translation_keys'][:20]}"
            )

    if findings["fixed_button_overflow"]:
        errors.append(
            f"{len(findings['fixed_button_overflow'])} fixed-coordinate buttons overflow the "
            f"{MIN_VIEWPORT_W}x{MIN_VIEWPORT_H} minimum viewport"
        )
    if findings["off_palette_colors"]:
        warnings.append(
            f"{len(findings['off_palette_colors'])} color literals in screens bypass SciSkin; "
            "extend SciSkin instead of hardcoding (baseline ratchet: do not let this number grow)"
        )
    if findings["screen_literal_calls"]:
        warnings.append(
            f"Client screens contain {findings['screen_literal_calls']} Text.literal calls; "
            "new code should use Text.translatable (ratchet: do not let this number grow)"
        )
    if findings["other_literal_calls"]:
        warnings.append(
            f"{findings['other_literal_calls']} Text.literal calls outside client screens "
            "(commands, feedback, tooltips); review which are player-facing"
        )
    if findings["fixed_button_candidates"]:
        warnings.append(
            f"{len(findings['fixed_button_candidates'])} buttons with fixed leading coordinates; "
            "review narrow-screen layout"
        )

    if args.as_json:
        print(json.dumps(findings, ensure_ascii=False, indent=2))
    else:
        print(f"Project: {root}")
        print(f"Screens: {', '.join(findings['screens']) or 'none'}")
        print(
            f"Localization: {findings['translatable_calls']} translatable / "
            f"{findings['screen_literal_calls']} literal in screens / "
            f"{findings['other_literal_calls']} literal elsewhere"
        )
        print("Assets:")
        for name, size in findings["assets"].items():
            print(f"  {name}: {size['width']}x{size['height']}")
        if findings["fixed_button_overflow"]:
            print("Fixed-button overflow (fails 320x240):")
            for item in findings["fixed_button_overflow"]:
                print(f"  {item}")
        if findings["off_palette_colors"]:
            print("Off-palette colors (first 20):")
            for item in findings["off_palette_colors"][:20]:
                print(f"  {item}")
        print("Warnings:")
        for warning in warnings:
            print(f"  WARN: {warning}")
        print("Errors:")
        for error in errors:
            print(f"  ERROR: {error}")

    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
