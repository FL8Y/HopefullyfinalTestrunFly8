# FLY8 agent and contributor instructions

## Mandatory source-of-truth checks

- Continue work in this repository: `FL8Y/HopefullyfinalTestrunFly8`. Do not create a replacement project.
- Before any major architecture change, read the full current guide in `https://github.com/FL8Y/readthischatgpt/tree/main`, specifically the file named `here is your guiding prompt`. It contains 76 requirements. Do not rely on a summary or memory alone.
- Treat `docs/FEATURE_PARITY_MATRIX.md` as the acceptance checklist. Update evidence/status when behavior changes; never mark a requirement complete from a declaration, UI, or source call alone.
- Review `reference/REFERENCE_IMPLEMENTATION_MAP.md` before altering the virtualization engine. Distinguish observed evidence from inference.
- Every 6 commits after `docs/COMMIT_CADENCE_ANCHOR.txt`, review the last six diffs, the complete 76-row matrix, the reference map, the latest Actions logs, and unresolved failures. Read the guide file again as part of this checkpoint.
- Every 8–9 commits at the latest, re-read the entire guide before further architecture work. Do not reset the cadence anchor to avoid a review.
- GitHub Actions must produce visible review reminders/artifacts at these checkpoints. These are prompts to actually read and review the material, not proof that the review happened.

## Non-negotiable product constraints

- Wireless Debugging/ADB is first-class. Shizuku is optional and must not replace it.
- FLY8 owns its app ID, branding, UI, theme, and icon. Do not add the reference vendor's branding, package namespace, activation/license-key flow, or bundled reference APK.
- No fake pairing, connection, package installation, process, Activity, task, Recent Apps entry, preview, or background state.
- A black Recent Apps thumbnail is not proof that the guest stopped.
- Trace the `Cannot add...` service exception and `--user 16` path to their source; do not suppress them.
- Preserve lightweight virtualization by evidence-based sharing and real per-instance isolation.
- Background/AFK, display/live feed, network persistence, Android 12/API 31 and Android 16/API 36 behavior require runtime evidence.
- Respect every license applicable to source and binary components actually included.

## Completion and communication

A requirement is complete only after implementation plus its acceptance evidence. Be explicit about blockers and never call an unbuilt or untested APK working. Prioritize a real guest launch/render/input proof before expanding into multi-instance, Recent Apps, and AFK claims.
