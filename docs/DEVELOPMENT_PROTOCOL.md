# FLY8 Development Protocol

## Non-negotiable project goal

Continue the existing FLY8 repository. The aim is behavioral parity with the supplied reference wherever technically possible, while keeping the FLY8 application, package identity, assets, and user-facing text original. Never represent a design-only placeholder as a working feature.

The 76-row matrix in [FEATURE_PARITY_MATRIX.md](FEATURE_PARITY_MATRIX.md) is the acceptance checklist. The technical mapping in [REFERENCE_IMPLEMENTATION_MAP.md](../reference/REFERENCE_IMPLEMENTATION_MAP.md) records evidence and unresolved questions. Update both whenever implementation changes alter a feature's status.

## Required rules

- Wireless Debugging / ADB remains a first-class connection route. Shizuku is optional and must not replace it.
- Do not fake a connection, pairing success, virtual process, guest Activity, Recent Apps task, live feed, or AFK session.
- A black Recent Apps thumbnail alone does not prove that a guest process has stopped.
- Keep package, activity, process, task, data, and preview state as separate runtime facts.
- Diagnose the service exception and `--user 16` launch arguments at their source. Do not suppress errors without explaining and fixing their root cause.
- Measure actual resource use before claiming clones are lightweight.
- Maintain the requested FLY8 palette and original cyber-eye adaptive icon.
- Preserve required licenses and notices for every third-party component actually shipped.
- Do not add the reference APK, decompiled binaries, vendor branding, vendor support links, activation assets, or vendor namespace to the FLY8 application/repository.
- A successful compile is not runtime validation. Record device/API, exact test, result, logs, and remaining limitation.
- Never claim Android 12 or Android 16 runtime success without test evidence from those environments.

## Source-of-truth refresh cadence

The guide prompt is maintained outside this repository at:

https://github.com/FL8Y/readthischatgpt/tree/main

**Review it before every major architecture decision, and refresh your understanding every 8–9 commits at latest.** The Android CI workflow also fetches the guide as an artifact when the commit cadence reaches a multiple of 9. That automation is a reminder and copy of the current guide, not a substitute for actually reading it.

**Every 6 commits** after the cadence anchor, review this protocol, the 76-row feature matrix, the reference map, recent commit diffs, and the latest GitHub Actions result. The Android CI workflow computes the cadence and writes a visible job-summary reminder at those checkpoints.

The cadence anchor is stored in [COMMIT_CADENCE_ANCHOR.txt](COMMIT_CADENCE_ANCHOR.txt). When deliberately resetting the cadence after a release or a large milestone, update the anchor and explain why in the commit message. Do not reset it merely to silence a reminder.

## Commit discipline

Prefer one coherent change per commit. Each change should include relevant tests or an explicit note about what could not be tested. Before a commit:
1. Inspect the current source and history.
2. Read the relevant guide/matrix requirements.
3. Make the smallest coherent change.
4. Run source-level checks and the available build.
5. Update parity status and validation notes.
6. Inspect the diff for unintended branding, secrets, fake status, and license omissions.

## Feature completion definition

A requirement is complete only when its intended behavior is implemented and validated. Source that only defines an interface, a TODO, a UI card, a success return value, or a fake task is not completion.

For virtualization, the end-to-end proof must include a real guest package load, process initialization, Activity creation, rendering and input, task/resume behavior, independent instances where supported, correct data isolation, and screen-off behavior under documented Android conditions.

## Current truth at cadence-anchor creation

A complete debug APK build succeeded in Actions run `37912492731` (commit `f92c7985`). Subsequent diagnostics work added a bounded in-memory log and About-screen view/copy controls; its first build exposed a `Result<Int>` vs `Result<Unit>` mismatch in service startup because `Log.i` returns an integer. The current pending validation commit adds an explicit `Unit` return and task-unknown diagnostics. API 31/API 36 emulator smoke checks are running. The first host smoke attempt exposed a POSIX-shell issue, now fixed; the next test also requires positive VirtualCore Binder/user-service readiness. FLY8 declares the base and `dataSync` foreground-service permissions used by the pinned engine daemon. No guest package launch, rendered guest UI, Recent Apps resume, independent-data or screen-off test has been recorded. Keep those statuses unverified until evidence exists.
