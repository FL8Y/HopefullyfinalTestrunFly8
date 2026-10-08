# FLY8

FLY8 is an Android application-virtualization project. Its goal is to run guest applications inside a lightweight virtualized environment while preserving independently tracked package, process, Activity, task, storage and session state.

## Current status — development baseline

Implemented groundwork:
- FLY8 package/application identity and requested dark color palette.
- Main dashboard with a first-class Wireless Debugging / ADB route.
- Separate optional Shizuku bridge.
- LibADB pairing, direct-connection and mDNS-discovery API integration draft.
- Android Keystore-backed ADB key identity draft.
- A source-invariant audit and CI build workflow.
- A 76-row parity matrix and evidence map for the supplied reference artifacts.
- A commit review cadence: inspect the guide/requirements and latest Actions after every 6 commits; refresh and reread the guide after 8–9 commits.

Not yet demonstrated:
- A successful Android build from Actions.
- End-to-end Wireless Debugging pairing/connection on a device.
- A concrete, integrated virtualization runtime.
- Real guest APK installation, process/Activity creation, rendering and input.
- Real independent multiple instances, Recent Apps resume, live feed, or AFK background operation.
- Android 12 / Android 16 device validation.

Do not interpret a UI element, interface contract, dependency declaration or build as proof of guest virtualization.

## Core rules

1. Wireless Debugging / ADB remains first-class. Shizuku is optional and must never replace ADB.
2. No fake success states, fake tasks, simulated guest processes, or fabricated Recent Apps cards.
3. Black Recent Apps thumbnails, process liveness, Activity liveness, task existence and rendering availability are separate facts.
4. Track every requested requirement in [the 76-row parity matrix](docs/FEATURE_PARITY_MATRIX.md).
5. Use [the reference implementation map](reference/REFERENCE_IMPLEMENTATION_MAP.md) to separate artifact evidence from hypotheses.
6. Follow [the development protocol](docs/DEVELOPMENT_PROTOCOL.md) and [validation plan](docs/VALIDATION_PLAN.md).
7. Keep FLY8's application/package identity and own visual assets; do not bring the original APK, its branding, activation assets, or original app namespace into this repository.
8. Preserve applicable licenses and notices for all third-party code that is actually integrated.

## Build

The project targets SDK 36 and uses Java 17/Kotlin. The checked-in workflow currently:
- installs Android SDK 36,
- runs scripts/audit_project.py,
- builds the debug APK,
- uploads a debug artifact on success,
- emits a source/requirements reminder at every 6-commit checkpoint,
- fetches the external guiding prompt as an artifact at every 9-commit checkpoint.

The Actions workflow has been committed, but a successful workflow run has not yet been observed. See the Actions tab before relying on a build result.

Once the virtualization module is integrated, the required SDK, NDK and build-tool versions will be documented here and pinned in CI.

## Repository layout

- app/ — FLY8 application, connection adapters and current UI.
- app/src/main/java/com/fl8y/lightcloner/engine/ — runtime contract; not a concrete virtualization implementation yet.
- docs/FEATURE_PARITY_MATRIX.md — all 76 acceptance requirements with current status.
- docs/DEVELOPMENT_PROTOCOL.md — no-fakes rule, cadence and commit discipline.
- docs/VALIDATION_PLAN.md — build, connection, clone, multi-instance, task and background tests.
- reference/REFERENCE_IMPLEMENTATION_MAP.md — observed symbols, evidence gaps and implementation mapping.
- scripts/audit_project.py — deterministic source-level repository checks.
- .github/workflows/android-ci.yml — Android build, audit and review cadence.

## Review cadence

The authoritative continuation guide is maintained at:
https://github.com/FL8Y/readthischatgpt/tree/main

Review it before major architecture decisions, at the latest every 8–9 commits. Review the last commit diffs, the parity matrix, implementation map and Actions results every 6 commits. Do not reset the cadence anchor merely to silence a reminder.
