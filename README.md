# FLY8

A clean Android workspace for lightweight application virtualization, wireless debugging connectivity, and optional Shizuku integration.

## Current status

The repository starts with:

- FLY8-only branding and package identity
- Android 16-compatible build target
- Wireless Debugging settings entry point
- optional Shizuku connection path
- a real virtualization-engine contract instead of fake clone/task state
- reference behavior notes for process, activity, task, preview, and background handling

The virtualization engine itself is intentionally not represented as complete until the reference behavior has been mapped to independently maintained code.

## Build baseline

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- Kotlin 2.2.10
- compileSdk 36
- targetSdk 36
- minSdk 26

## Design rule

Wireless Debugging and Shizuku are separate connection methods.

The runtime must treat process, activity, and task state as the source of truth. A Recent Apps thumbnail is only a preview and is not a process-health signal.

## Reference use

The supplied APK and decompiled material are used to reconstruct observable behavior and identify architectural requirements. Vendor-specific branding, hosted activation flow, and vendor-specific package namespace are not part of FLY8.
