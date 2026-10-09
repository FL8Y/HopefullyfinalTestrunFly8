#!/usr/bin/env python3
"""Run deterministic FLY8 source-level invariants; no Android device is required."""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
COLORS = ROOT / "app/src/main/res/values/colors.xml"
ICON_ADAPTIVE = ROOT / "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml"
ICON_ADAPTIVE_ROUND = ROOT / "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml"
ICON_FOREGROUND = ROOT / "app/src/main/res/mipmap-nodpi/ic_launcher_foreground.png"
APP_GRADLE = ROOT / "app/build.gradle.kts"
SETTINGS = ROOT / "settings.gradle.kts"
ENGINE_GRADLE = ROOT / "vendor/virtualapp16/lib/build.gradle"
MODULES = ROOT / ".gitmodules"
MAIN = ROOT / "app/src/main/java/com/fl8y/lightcloner/MainActivity.kt"
SMOKE_TEST = ROOT / ".github/scripts/emulator-smoke.sh"
DIAGNOSTICS = ROOT / "app/src/main/java/com/fl8y/lightcloner/Fly8Diagnostics.kt"
APP_CLASS = ROOT / "app/src/main/java/com/fl8y/lightcloner/Fly8Application.kt"
ADB = ROOT / "app/src/main/java/com/fl8y/lightcloner/connection/WirelessAdbController.kt"
ADB_MANAGER = ROOT / "app/src/main/java/com/fl8y/lightcloner/connection/Fly8AdbManager.kt"
PAIRING_SERVICE = ROOT / "app/src/main/java/com/fl8y/lightcloner/connection/WirelessPairingService.kt"
SHIZUKU = ROOT / "app/src/main/java/com/fl8y/lightcloner/connection/ShizukuConnection.kt"
ENGINE_CONTRACT = ROOT / "app/src/main/java/com/fl8y/lightcloner/engine/VirtualizationEngine.kt"
ENGINE_RUNTIME = ROOT / "app/src/main/java/com/fl8y/lightcloner/engine/Fly8VirtualRuntime.kt"
ENGINE_ADAPTER = ROOT / "app/src/main/java/com/fl8y/lightcloner/engine/VirtualAppEngine.kt"
MATRIX = ROOT / "docs/FEATURE_PARITY_MATRIX.md"
MAP = ROOT / "reference/REFERENCE_IMPLEMENTATION_MAP.md"

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
TOOLS_NS = "{http://schemas.android.com/tools}"
EXPECTED_COLORS = {
    "fly8_background": "#000001",
    "fly8_surface": "#010205",
    "fly8_panel": "#01132A",
    "fly8_primary": "#176E94",
    "fly8_secondary": "#01304C",
    "fly8_text": "#FCFDFD",
    "fly8_pink": "#F5DADA",
    "fly8_muted": "#626773",
    "fly8_icon_bg": "#01132A",
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
    settings_source = read_text(SETTINGS)
    engine_gradle_source = read_text(ENGINE_GRADLE)
    modules_source = read_text(MODULES)
    main_source = read_text(MAIN)
    smoke_source = read_text(SMOKE_TEST)
    diagnostics_source = read_text(DIAGNOSTICS)
    app_class_source = read_text(APP_CLASS)
    adb_source = read_text(ADB)
    adb_manager_source = read_text(ADB_MANAGER)
    shizuku_source = read_text(SHIZUKU)
    engine_contract = read_text(ENGINE_CONTRACT)
    engine_runtime = read_text(ENGINE_RUNTIME)
    engine_adapter = read_text(ENGINE_ADAPTER)
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
                require(app.attrib.get(ANDROID_NS + "allowBackup") == "false",
                        "Host application backup must remain disabled for virtualized app data.")
                require(app.attrib.get(TOOLS_NS + "replace") == "android:allowBackup,android:label",
                        "Manifest must explicitly resolve dependency conflicts for FLY8 label/backup attributes.")
                app_name = app.attrib.get(ANDROID_NS + "name")
                require(app_name == ".Fly8Application", f"Expected .Fly8Application bootstrap, got {app_name!r}.")
                require(app.attrib.get(ANDROID_NS + "icon") == "@mipmap/ic_launcher", "Manifest launcher icon must reference @mipmap/ic_launcher.")
                require(app.attrib.get(ANDROID_NS + "roundIcon") == "@mipmap/ic_launcher_round", "Manifest round launcher icon must reference @mipmap/ic_launcher_round.")
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

            declared_permissions = {
                permission.attrib.get(ANDROID_NS + "name")
                for permission in manifest.findall("uses-permission")
            }
            require(
                "android.permission.FOREGROUND_SERVICE" in declared_permissions,
                "Host must declare FOREGROUND_SERVICE for the pinned engine daemon.",
            )
            require(
                "android.permission.FOREGROUND_SERVICE_DATA_SYNC" in declared_permissions,
                "Host must declare FOREGROUND_SERVICE_DATA_SYNC for the engine's dataSync service.",
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

    require(ICON_ADAPTIVE.is_file(), "Adaptive launcher icon XML is missing.")
    require(ICON_ADAPTIVE_ROUND.is_file(), "Adaptive round launcher icon XML is missing.")
    require(ICON_FOREGROUND.is_file() and ICON_FOREGROUND.stat().st_size > 1000,
            "Cyber-eye transparent launcher foreground is missing or unexpectedly small.")
    for icon_xml in (ICON_ADAPTIVE, ICON_ADAPTIVE_ROUND):
        if icon_xml.is_file():
            try:
                icon_root = ET.parse(icon_xml).getroot()
                require(icon_root.tag == "adaptive-icon", f"{icon_xml.name} is not an adaptive icon resource.")
                require(any(node.attrib.get(ANDROID_NS + "drawable") == "@mipmap/ic_launcher_foreground"
                            for node in icon_root), f"{icon_xml.name} lacks the FLY8 foreground artwork.")
                require(any(node.attrib.get(ANDROID_NS + "drawable") == "@color/fly8_icon_bg"
                            for node in icon_root), f"{icon_xml.name} lacks the FLY8 background color.")
            except ET.ParseError as exc:
                errors.append(f"Adaptive icon XML is invalid ({icon_xml.name}): {exc}")

    require('namespace = "com.fl8y.lightcloner"' in app_gradle, "Android namespace is not the FLY8 namespace.")
    require('applicationId = "com.fl8y.lightcloner"' in app_gradle, "Application ID is not the FLY8 application ID.")
    require('implementation(project(":virtual-engine"))' in app_gradle, "Virtualization engine module is not linked to the app.")
    require('implementation("com.github.MuntashirAkon:libadb-android:3.1.1")' in app_gradle, "LibADB 3.1.1 dependency missing.")
    require('implementation("dev.rikka.shizuku:api:13.1.5")' in app_gradle, "Optional Shizuku API dependency missing.")
    require('implementation("dev.rikka.shizuku:provider:13.1.5")' in app_gradle, "Optional Shizuku provider dependency missing.")
    require('include(":virtual-engine")' in settings_source, "Virtual engine project is not included by Gradle.")
    require('maven { url = uri("https://artifactory.appodeal.com/appodeal-public") }' in settings_source,
            "Root dependency repositories must include the JCenter artifact mirror.")
    require("aidl = true" in engine_gradle_source,
            "Virtual engine Gradle configuration must enable AIDL generation for Binder interfaces.")
    require("f0restw0w/VirtualApp_16.git" in modules_source, "Pinned VirtualApp-derived source is not registered as a submodule.")

    require("Wireless Debugging" in main_source, "Dashboard lacks Wireless Debugging path.")
    require("Shizuku" in main_source, "Dashboard lacks optional Shizuku path.")
    require("engine.createInstance(" in main_source, "Dashboard is not wired to create real virtual instances.")
    require("engine.launch(" in main_source, "Sessions UI is not wired to the real engine launch API.")
    require("manager.pair(" in adb_source, "Pairing controller does not call the real ADB pair API.")
    require("manager.connect(" in adb_source, "ADB controller does not call direct connection API.")
    require("manager.autoConnect(" in adb_source, "ADB controller does not call discovery API.")
    require("AndroidKeyStore" in adb_manager_source, "ADB auth key is not backed by Android Keystore.")
    require("Fly8AdbManager.get(applicationContext)" in pairing_service_source,
            "Pairing service must share the app's persistent ADB manager.")
    require('" _adb-tls-pairing._tcp"' not in pairing_service_source and '"_adb-tls-pairing._tcp"' in pairing_service_source,
            "Pairing discovery must target Android's dedicated TLS pairing mDNS service.")
    require('"_adb-tls-connect._tcp"' in pairing_service_source,
            "Pairing flow must separately discover the normal authenticated ADB endpoint.")
    require("RemoteInput.Builder(KEY_PAIRING_CODE)" in pairing_service_source,
            "Pairing notification lacks inline pairing-code entry.")
    require("ACTION_SUBMIT_CODE" in pairing_service_source and "adb.pair(host, port, code)" in pairing_service_source,
            "Notification code submission must call real LibADB pairing.")
    require("adb.isConnected" in pairing_service_source,
            "Connected status must be based on the ADB manager's actual connection state.")
    require("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" in manifest_source,
            "Notification-led pairing service requires connectedDevice foreground-service permission.")
    require('android:name=".connection.WirelessPairingService"' in manifest_source and
            'android:foregroundServiceType="connectedDevice"' in manifest_source,
            "Notification-led pairing service is not declared with its foreground service type.")
    require("POST_NOTIFICATIONS" in manifest_source,
            "Android 13+ notification permission must be declared.")
    require("beginWirelessPairing()" in main_source and "WirelessPairingService.ACTION_START" in main_source,
            "Dashboard does not offer the streamlined notification-led pairing flow.")
    require("Shizuku.pingBinder()" in shizuku_source, "Shizuku bridge does not check binder availability.")

    require("VirtualCore.get().startup(context)" in engine_runtime, "VirtualCore is not started from the FLY8 application lifecycle.")
    require("VirtualCore.get().waitForEngine()" in engine_runtime, "VirtualCore Binder service startup is not requested.")
    require("VUserManager.get().users" in engine_runtime, "Virtual user service is not checked before declaring engine ready.")
    require("class VirtualAppEngine" in engine_adapter, "Concrete VirtualAppEngine adapter is missing.")
    require("core.installPackage(" in engine_adapter, "Engine adapter does not call real virtual package installation.")
    require("VActivityManager.get().startActivity(" in engine_adapter, "Engine adapter does not route launches through virtual ActivityManager.")
    require("core.isAppRunning(" in engine_adapter, "Engine adapter does not query real virtual process liveness.")
    require("previewAvailable = null" in engine_adapter and "taskVisible = null" in engine_adapter,
            "Unverified task/preview state must remain unknown rather than fabricated.")

    require("MAX_ENTRIES = 300" in diagnostics_source, "Bounded diagnostics buffer must stay capped at 300 entries.")
    require("Pairing codes and private keys must never be passed to record()" in diagnostics_source,
            "Diagnostics must document the exclusion of pairing codes and private keys.")
    require("View diagnostic log" in main_source and "Copy diagnostic log" in main_source,
            "About screen must provide diagnostic log viewing and copy controls.")
    require("Fly8Diagnostics.record" in adb_source and "Fly8Diagnostics.record" in engine_runtime and "Fly8Diagnostics.record" in engine_adapter,
            "ADB, virtual-service startup and guest lifecycle must record diagnostic events.")
    require(smoke_source.startswith("#!/bin/sh"), "Emulator smoke test must be a standalone POSIX shell script.")
    require("Virtual service answered; virtual-user count=" in smoke_source,
            "Emulator smoke test must require real VirtualCore Binder/user-service readiness.")
    require("mResumedActivity" in smoke_source and "com.fl8y.lightcloner" in smoke_source,
            "Emulator smoke test must verify the actual FLY8 host Activity is resumed.")
    require("interface VirtualizationEngine" in engine_contract, "VirtualizationEngine contract missing.")
    warnings.append("Source checks are not Android build or device runtime proof; guest rendering/task/background parity remains unverified.")

    require("| # | Acceptance requirement |" in matrix_source, "76-requirement parity matrix is missing or malformed.")
    counted_rows = sum(
        1 for line in matrix_source.splitlines()
        if line.startswith("| ") and line.split("|")[1].strip().isdigit()
    )
    require(counted_rows == 76, f"Expected 76 tracked parity rows, found {counted_rows}.")
    require("Evidence still required" in map_source, "Reference evidence ledger lacks evidence gaps.")

    # Construct the forbidden label from pieces so this checker does not itself
    # embed the full vendor label as a searchable string.
    forbidden_brand = "Ey" + "sClone"
    for file_path in ROOT.rglob("*"):
        if not file_path.is_file() or ".git" in file_path.parts or "build" in file_path.parts:
            continue
        try:
            source = file_path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        if forbidden_brand.lower() in source.lower():
            errors.append(f"Reference vendor branding remains in {file_path.relative_to(ROOT)}.")
        if file_path.suffix.lower() in {".jks", ".keystore", ".pem", ".p12"}:
            errors.append(f"Potential secret/key material is tracked: {file_path.relative_to(ROOT)}")

    for warning in warnings:
        print(f"WARNING: {warning}")
    for error in errors:
        print(f"ERROR: {error}")

    if errors:
        print(f"Source audit failed with {len(errors)} error(s).")
        return 1
    print("Source audit passed. It checks repository invariants only; it does not prove Android build or runtime parity.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
