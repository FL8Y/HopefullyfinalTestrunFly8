#!/usr/bin/env python3
"""Apply small, repeatable FLY8 integration patches to the pinned engine submodule.

The upstream code remains in its pinned submodule. These changes adapt its old
Gradle metadata to the FLY8 Android build and move virtual shared storage into
the host app's scoped files area so the host does not need all-files access.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / "vendor/virtualapp16/lib"
GRADLE_FILE = ENGINE / "build.gradle"
ENVIRONMENT_FILE = ENGINE / "src/main/java/com/lody/virtual/os/VEnvironment.java"


def require_file(path: Path) -> str:
    if not path.is_file():
        raise FileNotFoundError(
            f"Pinned engine source is missing: {path.relative_to(ROOT)}. "
            "Check out the virtualapp16 submodule recursively."
        )
    return path.read_text(encoding="utf-8")


def patch_gradle() -> bool:
    source = require_file(GRADLE_FILE)
    original = source

    android_match = re.search(r"\bandroid\s*\{", source)
    if not android_match:
        raise RuntimeError("Could not find the Android DSL block in engine lib/build.gradle.")

    if not re.search(r"\bnamespace\s+['\"]", source):
        insertion = android_match.end()
        source = source[:insertion] + "\n    namespace 'com.lody.virtual'\n" + source[insertion:]

    source = re.sub(r"compileSdkVersion\s+\d+", "compileSdkVersion 36", source, count=1)
    source = re.sub(
        r"buildToolsVersion\s+['\"][^'\"]+['\"]",
        "buildToolsVersion '36.0.0'",
        source,
        count=1,
    )

    # AGP 9 rejects the legacy ProGuard default because it disables R8
    # optimization. Point the engine module to the supported optimized baseline.
    source = re.sub(
        r"getDefaultProguardFile\(\s*['\"]proguard-android\.txt['\"]\s*\)",
        "getDefaultProguardFile('proguard-android-optimize.txt')",
        source,
    )

    if source != original:
        GRADLE_FILE.write_text(source, encoding="utf-8")
    return source != original


def patch_scoped_virtual_storage() -> bool:
    source = require_file(ENVIRONMENT_FILE)
    start_marker = "    public static File getVirtualStorageBaseDir() {"
    end_marker = "    public static File getVirtualStorageDir("
    start = source.find(start_marker)
    if start < 0:
        raise RuntimeError("Could not find getVirtualStorageBaseDir() in VEnvironment.java.")
    end = source.find(end_marker, start)
    if end < 0:
        raise RuntimeError("Could not find getVirtualStorageDir() after getVirtualStorageBaseDir().")

    existing = source[start:end]
    if "fly8-virtual-storage" in existing:
        return False

    replacement = """    public static File getVirtualStorageBaseDir() {
        // Keep virtual shared storage scoped to FLY8's own app files. Do not
        // require broad MANAGE_EXTERNAL_STORAGE access or create a public
        // root-level directory belonging to another app.
        try {
            Context context = VirtualCore.get().getContext();
            File externalFiles = context.getExternalFilesDir(null);
            if (externalFiles != null) {
                File root = ensureCreated(new File(externalFiles, "fly8-virtual-storage"));
                File shared = ensureCreated(new File(root, "vsdcard"));
                if (shared.isDirectory()) {
                    return shared;
                }
            }

            File internal = ensureCreated(new File(context.getFilesDir(), "fly8-virtual-storage"));
            File shared = ensureCreated(new File(internal, "vsdcard"));
            if (shared.isDirectory()) {
                return shared;
            }
        } catch (Throwable error) {
            VLog.w(TAG, "Could not create FLY8 virtual shared storage: " + error.getMessage());
        }
        return null;
    }

"""

    source = source[:start] + replacement + source[end:]
    ENVIRONMENT_FILE.write_text(source, encoding="utf-8")
    return True


def main() -> int:
    try:
        gradle_changed = patch_gradle()
        storage_changed = patch_scoped_virtual_storage()
    except (OSError, RuntimeError, FileNotFoundError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    print(f"Engine Gradle metadata updated: {gradle_changed}")
    print(f"Virtual storage scoped to FLY8: {storage_changed}")
    print("This script patches only the checked-out submodule working tree; no source is fabricated.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
