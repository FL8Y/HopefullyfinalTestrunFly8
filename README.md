# FLY8

FLY8 is an Android application-virtualization project. Its goal is to run guest applications inside a lightweight virtualized environment while preserving separately tracked package, virtual-user, process, Activity, task, storage and session state.

## Current status

The repository now contains:
- FLY8 application identity and requested dark color palette.
- First-class Wireless Debugging / ADB controls and a separate optional Shizuku path.
- LibADB pairing, direct-connect and mDNS-discovery integration draft.
- Android Keystore-backed ADB identity draft.
- A pinned VirtualApp-derived engine submodule at an explicit commit.
- An Application lifecycle bootstrap that calls the real VirtualCore startup/initialization entry points.
- A concrete engine adapter that calls the virtual package manager, creates per-instance virtual users, starts guest Activities through the virtual ActivityManager, and queries guest process liveness.
- A dashboard APK picker and package/session controls wired to the engine adapter.
- A 76-row parity matrix, a reference evidence map, a validation plan, and source-audit scripts.
- CI source checks, Android build steps, and a six/nine-commit reminder cadence.

**Build status:** GitHub Actions run `37912492731` built the debug APK successfully at commit `f92c7985`, and its artifact was uploaded. A later diagnostics enhancement (`c1df5660`) exposed a Kotlin return-type error in virtual service startup; this pending change adds the explicit `Unit` return fix and the next CI run will verify it. No real-device guest launch or runtime parity has been confirmed.

Not yet demonstrated:
- Successful installation, application startup and virtual-service bootstrap on Android 12/API 31 and Android 16/API 36 devices.
- End-to-end Wireless Debugging pair/connect on a device.
- A test APK's complete virtual installation, process initialization, Activity creation, rendering and input.
- Actual task/Recent Apps resume, independent instance data, display/live feed, and AFK/screen-off operation.
- Android 12 / Android 16 device validation.

Do not interpret a UI element, interface contract, dependency declaration, source-level API invocation, or successful build as proof of full guest virtualization.

## Core rules

1. Wireless Debugging / ADB remains first-class. Shizuku is optional and must never replace it.
2. No fake success states, fake tasks, simulated guest processes, or fabricated Recent Apps cards.
3. Black Recent Apps thumbnails, process liveness, Activity liveness, task existence and rendering availability are separate facts.
4. Track every requested requirement in [the 76-row parity matrix](docs/FEATURE_PARITY_MATRIX.md).
5. Use [the reference implementation map](reference/REFERENCE_IMPLEMENTATION_MAP.md) to separate artifact evidence from hypotheses.
6. Follow [the development protocol](docs/DEVELOPMENT_PROTOCOL.md) and [validation plan](docs/VALIDATION_PLAN.md).
7. Keep FLY8's application/package identity and original visual assets; do not bring the reference APK, its branding, activation assets, or app namespace into the FLY8 host.
8. Preserve applicable licenses and notices for all third-party code that is actually integrated.

## Build

The project targets SDK 36 and uses Java 17/Kotlin. The checked-in workflow:
- checks out the pinned engine submodule recursively,
- installs Android SDK 36 and the engine NDK,
- applies repeatable FLY8 compatibility patches to the pinned library working tree,
- runs `scripts/audit_project.py`,
- builds the debug APK,
- uploads the APK artifact on success,
- runs API 31/API 36 emulator smoke checks from a standalone POSIX shell script, validates host Activity/process and VirtualCore Binder/user-service readiness, and uploads evidence (not guest rendering/task parity),
- emits a source/requirements reminder every 6 commits,
- fetches the external guiding prompt as an artifact every 9 commits.

The project currently patches the upstream library's old Gradle metadata to build with the selected SDK, and relocates its virtual shared storage into FLY8's app-scoped files area so the host does not need broad all-files access. The pinned engine source is maintained as a submodule; initialize it recursively for local builds.

The workflow produced a successful APK in run `37912492731`. The latest diagnostics enhancement requires the current build to pass before using its APK. Emulator smoke testing is being added for API 31 and API 36; physical-device guest virtualization, rendering/input, Recent Apps and screen-off behavior still need separate validation.

## Repository layout

- `app/` — FLY8 UI, ADB/Shizuku adapters, app lifecycle and virtual-runtime adapter.
- `vendor/virtualapp16` — pinned third-party VirtualApp-derived source submodule.
- `docs/FEATURE_PARITY_MATRIX.md` — all 76 acceptance requirements with current status.
- `docs/DEVELOPMENT_PROTOCOL.md` — no-fakes rule, cadence and commit discipline.
- `docs/VALIDATION_PLAN.md` — build, connection, guest-app, multi-instance, task and background tests.
- `docs/ENGINE_ADOPTION_DECISION.md` — selected engine candidate and integration gates.
- `reference/REFERENCE_IMPLEMENTATION_MAP.md` — observed symbols, evidence gaps and implementation mapping.
- `scripts/prepare_virtual_engine.py` — reproducible source/build compatibility patch for the pinned engine.
- `scripts/audit_project.py` — deterministic source-level repository checks.
- `.github/workflows/android-ci.yml` — Android build, audit and review cadence.

## Review cadence

The authoritative continuation guide is maintained at:
https://github.com/FL8Y/readthischatgpt/tree/main

Review it before major architecture decisions, at the latest every 8–9 commits. Review the last commit diffs, the parity matrix, implementation map and Actions results every 6 commits. Do not reset the cadence anchor merely to silence a reminder.
