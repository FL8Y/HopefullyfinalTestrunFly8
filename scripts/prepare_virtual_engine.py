#!/usr/bin/env python3
"""Apply small, repeatable FLY8 integration patches to the pinned engine submodule.

The upstream code remains in its pinned submodule. These changes adapt its old
Gradle metadata to the FLY8 Android build, restore access to retired JCenter
artifacts through a verified public mirror, and move virtual shared storage into
the host app's scoped files area so the host does not need broad all-files access.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / "vendor/virtualapp16/lib"
GRADLE_FILE = ENGINE / "build.gradle"
ENVIRONMENT_FILE = ENGINE / "src/main/java/com/lody/virtual/os/VEnvironment.java"
VCLIENT_FILE = ENGINE / "src/main/java/com/lody/virtual/client/VClientImpl.java"
LEGACY_MAVEN_MIRROR = "https://artifactory.appodeal.com/appodeal-public"


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

    # AGP 9 disables AIDL code generation unless the feature is explicitly
    # enabled. The engine depends on generated Binder interfaces throughout
    # its service and package/activity manager implementation.
    aidl_features = re.search(r"(?s)buildFeatures\s*\{([^}]*)\}", source)
    aidl_is_enabled = bool(
        aidl_features and re.search(r"\baidl\s*(?:=\s*)?true\b", aidl_features.group(1))
    )
    if not aidl_is_enabled:
        if aidl_features:
            block_end = source.find("}", aidl_features.start())
            if block_end < 0:
                raise RuntimeError("Could not find the end of the engine buildFeatures block.")
            source = source[:block_end] + "\n        aidl = true\n    " + source[block_end:]
        else:
            android_match = re.search(r"\bandroid\s*\{", source)
            if not android_match:
                raise RuntimeError("Could not find the Android DSL block to enable AIDL generation.")
            insertion = android_match.end()
            source = source[:insertion] + "\n    buildFeatures {\n        aidl = true\n    }\n" + source[insertion:]

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

    # Gradle 9 removed RepositoryHandler.jcenter(). Keep the dependency graph
    # intact and resolve the old JCenter-only hook artifacts from a public mirror.
    source = re.sub(
        r"(?m)^\s*jcenter\(\)\s*$",
        "    // jcenter() retired; legacy artifacts use the public Appodeal mirror.",
        source,
    )
    if LEGACY_MAVEN_MIRROR not in source:
        repositories_match = re.search(r"(?m)^repositories\s*\{", source)
        if not repositories_match:
            source += (
                "\nrepositories {\n"
                f"    maven {{ url '{LEGACY_MAVEN_MIRROR}' }}\n"
                "}\n"
            )
        else:
            insert_at = repositories_match.end()
            source = (
                source[:insert_at]
                + f"\n    maven {{ url '{LEGACY_MAVEN_MIRROR}' }}"
                + source[insert_at:]
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


def patch_api36_guest_classloader() -> bool:
    """
    Guard VirtualApp's Android S+ guest class-loader lookup.

    API 36 CI reached VClientImpl.bindApplicationNoCheck() and crashed because
    LoadedApk.getClassLoader.call(data.info) returned null before cl.getParent().
    RefMethod.call() swallows reflection failures and returns null, hiding the
    original lookup error. Retry through the framework getter, then inspect the
    existing mClassLoader field; if both fail, log the real problem and throw a
    package/API-specific exception instead of the current opaque NPE.
    """
    source = require_file(VCLIENT_FILE)
    old = """        ClassLoader cl = LoadedApk.getClassLoader.call(data.info);
        if (BuildCompat.isS()) {
            ClassLoader parent = cl.getParent();"""
    new = """        ClassLoader cl = LoadedApk.getClassLoader.call(data.info);
        if (cl == null && data.info != null) {
            try {
                java.lang.reflect.Method loaderMethod =
                        data.info.getClass().getDeclaredMethod("getClassLoader");
                loaderMethod.setAccessible(true);
                Object resolvedLoader = loaderMethod.invoke(data.info);
                if (resolvedLoader instanceof ClassLoader) {
                    cl = (ClassLoader) resolvedLoader;
                }
            } catch (Throwable methodError) {
                VLog.w(TAG, "LoadedApk.getClassLoader fallback failed for " + packageName
                        + " on API " + Build.VERSION.SDK_INT, methodError);
            }
        }
        if (cl == null && data.info != null) {
            try {
                java.lang.reflect.Field loaderField =
                        data.info.getClass().getDeclaredField("mClassLoader");
                loaderField.setAccessible(true);
                Object resolvedLoader = loaderField.get(data.info);
                if (resolvedLoader instanceof ClassLoader) {
                    cl = (ClassLoader) resolvedLoader;
                }
            } catch (Throwable fieldError) {
                VLog.w(TAG, "LoadedApk.mClassLoader fallback failed for " + packageName
                        + " on API " + Build.VERSION.SDK_INT, fieldError);
            }
        }
        if (cl == null) {
            throw new IllegalStateException("VirtualApp could not resolve the guest class loader for "
                    + packageName + " on API " + Build.VERSION.SDK_INT);
        }
        if (BuildCompat.isS()) {
            ClassLoader parent = cl.getParent();"""
    if old not in source:
        if "VirtualApp could not resolve the guest class loader for " in source:
            return False
        raise RuntimeError(
            "Could not find the expected VClientImpl guest class-loader block; "
            "review the pinned engine source before applying the API 36 compatibility patch."
        )
    VCLIENT_FILE.write_text(source.replace(old, new, 1), encoding="utf-8")
    return True


def main() -> int:
    try:
        gradle_changed = patch_gradle()
        storage_changed = patch_scoped_virtual_storage()
        classloader_changed = patch_api36_guest_classloader()
    except (OSError, RuntimeError, FileNotFoundError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    print(f"Engine Gradle metadata updated: {gradle_changed}")
    print(f"Virtual storage scoped to FLY8: {storage_changed}")
    print(f"API S+ guest class-loader guard applied: {classloader_changed}")
    print("This script patches only the checked-out submodule working tree; no source is fabricated.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
