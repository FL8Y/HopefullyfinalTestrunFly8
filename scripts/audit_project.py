#!/usr/bin/env python3
"""Run deterministic source-level checks for FLY8 without building Android code."""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
COLORS = ROOT / "app/src/main/res/values/colors.xml"
APP_GRADLE = ROOT / "app/build.gradle.kts"
MAIN = ROOT / "app/src/main/java/com/fl8y/lightcloner/MainActivity.kt"
ADB = ROOT / "app/src/main/java/com/fl8y/lightcloner/connection/WirelessAdbController.kt"
ADB_MANAGER = ROOT / "app/src/main/java/com/fl8y/lightcloner/connection/Fly8AdbManager.kt"
SHIZUKU = ROOT / "app/src/main/java/com/fl8y/lightcloner/connection/ShizukuConnection.kt"
ENGINE = ROOT / "app/src/main/java/com/fl8y/lightcloner/engine/VirtualizationEngine.kt"
MATRIX = ROOT / "docs/FEATURE_PARITY_MATRIX.md"
MAP = ROOT / "reference/REFERENCE_IMPLEMENTATION_MAP.md"

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
EXPECTED_COLORS = {
    "fly8_background": "#000001",
    "fly8_surface": "#010205",
    "fly8_panel": "#01132A",
    "fly8_primary": "#176E94",
    "fly8_secondary": "#01304C",
    "fly8_text": "#FCFDFD",
    "fly8_pink": "#F5DADA",
    "fly8_muted": "#626773",
}
errors: list[str] = []
warnings: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


def read_text(path: Path) -> str:
    if not path.is_file():
        errors.append(f"Missing required file: {path.relative_to(ROOT)}")
        return ""
    try:
        return path.read_text(encoding="utf-8")
    except OSError as exc:
        errors.append(f"Cannot read {path.relative_to(ROOT)}: {exc}")
        return ""


def main() -> int:
    app_gradle = read_text(APP_GRADLE)
    main_source = read_text(MAIN)
    adb_source = read_text(ADB)
    adb_manager_source = read_text(ADB_MANAGER)
    shizuku_source = read_text(SHIZUKU)
    engine_source = read_text(ENGINE)
    matrix_source = read_text(MATRIX)
    map_source = read_text(MAP)

    if MANIFEST.exists():
        try:
            manifest = ET.parse(MANIFEST).getroot()
            app = manifest.find("application")
            require(app is not None, "Manifest has no application node.")
            if app is not None:
                label = app.attrib.get(ANDROID_NS + "label")
                require(label == "FLY8", f"Expected application label FLY8, got {label!r}.")
            activity_names = {
                node.attrib.get(ANDROID_NS + "name", "")
                for node in manifest.findall("./application/activity")
            }
            require(".MainActivity" in activity_names, "Launcher activity .MainActivity is missing.")
            providers = manifest.findall("./application/provider")
            require(
                any("rikka.shizuku.ShizukuProvider" == p.attrib.get(ANDROID_NS + "name") for p in providers),
                "Optional Shizuku provider is missing.",
            )
        except ET.ParseError as exc:
            errors.append(f"Manifest XML is invalid: {exc}")

    if COLORS.exists():
        try:
            colors_root = ET.parse(COLORS).getroot()
            actual = {
                node.attrib.get("name", ""): (node.text or "").strip()
                for node in colors_root.findall("color")
            }
            for name, value in EXPECTED_COLORS.items():
                require(actual.get(name) == value, f"Color {name} should be {value}, got {actual.get(name)!r}.")
        except ET.ParseError as exc:
            errors.append(f"Color XML is invalid: {exc}")

    require('namespace = "com.fl8y.lightcloner"' in app_gradle, "Android namespace is not the FLY8 namespace.")
    require('applicationId = "com.fl8y.lightcloner"' in app_gradle, "Application ID is not the FLY8 application ID.")
    require('implementation("com.github.MuntashirAkon:libadb-android:3.1.1")' in app_gradle, "LibADB 3.1.1 dependency missing.")
    require('implementation("dev.rikka.shizuku:api:13.1.5")' in app_gradle, "Optional Shizuku API dependency missing.")
    require('implementation("dev.rikka.shizuku:provider:13.1.5")' in app_gradle, "Optional Shizuku provider dependency missing.")

    require("Wireless Debugging" in main_source, "Dashboard lacks Wireless Debugging path.")
    require("Shizuku" in main_source, "Dashboard lacks optional Shizuku path.")
    require("manager.pair(" in adb_source, "Pairing controller does not call the real ADB pair API.")
    require("manager.connect(" in adb_source, "ADB controller does not call direct connection API.")
    require("manager.autoConnect(" in adb_source, "ADB controller does not call discovery API.")
    require("AndroidKeyStore" in adb_manager_source, "ADB auth key is not backed by Android Keystore.")
    require("Shizuku.pingBinder()" in shizuku_source, "Shizuku bridge does not check binder availability.")

    require("interface VirtualizationEngine" in engine_source, "VirtualizationEngine contract missing.")
    if "interface VirtualizationEngine" in engine_source and "class " not in engine_source.replace("interface VirtualizationEngine", ""):
        warnings.append("VirtualizationEngine is currently a contract only; no concrete guest runtime is implemented.")
    else:
        warnings.append("Inspect concrete engine state manually; source-level presence is not runtime proof.")

    require("| # | Acceptance requirement |" in matrix_source, "76-requirement parity matrix is missing or malformed.")
    counted_rows = sum(1 for line in matrix_source.splitlines() if line.startswith("| ") and line.split("|")[1].strip().isdigit())
    require(counted_rows == 76, f"Expected 76 tracked parity rows, found {counted_rows}.")
    require("Evidence still required" in map_source, "Reference evidence ledger lacks evidence gaps.")

    for file_path in ROOT.rglob("*"):
        if not file_path.is_file() or ".git" in file_path.parts or "build" in file_path.parts:
            continue
        try:
            file_path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        if file_path.suffix.lower() in {".jks", ".keystore", ".pem", ".p12"}:
            errors.append(f"Potential secret/key material is tracked: {file_path.relative_to(ROOT)}")

    for warning in warnings:
        print(f"WARNING: {warning}")
    for error in errors:
        print(f"ERROR: {error}")

    if errors:
        print(f"Source audit failed with {len(errors)} error(s).")
        return 1
    print("Source audit passed. This checks repository invariants only; it does not prove Android build or runtime parity.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
