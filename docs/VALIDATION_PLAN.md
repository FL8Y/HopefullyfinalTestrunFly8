# FLY8 Validation Plan

## Rule

No feature is considered complete because a screen exists or a build succeeds. Each test records the exact FLY8 commit, APK checksum, device model, Android/API version, test app/version, steps, result, logs, and known limitation. Never record "pass" without evidence.

No Android device is attached to this workspace, so all hardware/runtime rows remain pending until a real tester runs them.

## Build and source checks

1. Run scripts/audit_project.py.
2. Run the Android CI debug build.
3. Verify package identity, launcher label and adaptive icon from the built APK.
4. Verify the APK is signed and installable.
5. Audit the built manifest and DEX/resource strings for unintended branding, support/update links, activation requirements and package identity leakage.
6. Preserve dependency notices and produce a dependency/license inventory.

## Wireless Debugging / ADB

1. Test with Shizuku not installed or stopped.
2. Open the direct Wireless Debugging shortcut, with fallback to Developer Options.
3. Pair using the exact IP/port shown by Android and the six-digit code.
4. Verify actual pairing result, not just a UI message.
5. Connect to the ADB endpoint, verify actual authenticated connection state and run a harmless diagnostic service call.
6. Test wrong code, wrong port, expired pairing, network change, cancellation, timeout and already-paired state.
7. Disconnect/reconnect and verify the Android Keystore identity survives an Activity recreation.
8. Test mDNS discovery, multicast behavior and cleanup.
9. Confirm no pairing codes, private keys or authentication payloads enter logs.
10. Repeat on Android 12 and Android 16 where available.

## Shizuku

1. Test with Wireless Debugging unavailable; Shizuku remains optional.
2. Test Shizuku absent, stopped, permission denied, permission granted, and binder-dead/restarted.
3. Register and remove binder/permission listeners correctly.
4. Verify an operation only reports success after the actual operation returns a valid result.
5. Verify disabling the Shizuku route leaves ADB functionality intact.

## Clone install and runtime

Use a harmless ordinary test APK first, then AccuBattery (package com.digibites.accubattery), then Roblox.

For each test package record:
- APK version/signature and ABI/split layout.
- Whether package metadata was parsed and virtual package registration succeeded.
- Data directory/instance identity and storage delta.
- Process ID/name and actual guest application initialization.
- Activity created/resumed, window/surface attached, visible rendering, input response.
- Exit/crash cause and logs if it fails.
- No fake task entries or simulated runtime status.

## Recent Apps and live display

1. Launch a real guest.
2. Open system Recent Apps and record whether a task/card appears.
3. Select that card and verify that the real guest Activity returns and accepts input.
4. Distinguish process-alive/activity-alive/task-exists/rendering/preview-available states.
5. When the thumbnail is black, verify guest process/activity state independently; do not infer death from the image.
6. If a live-feed feature is implemented, verify frame delivery and input forwarding against the guest's actual screen.

## Multiple instances and isolation

1. Launch two instances of the same test package where the engine supports this.
2. Record virtual instance/user identifiers, host process IDs, task IDs and package identity mapping.
3. Test concurrent use and task switching.
4. Verify each instance's preferences, databases, files and state are separate where expected.
5. Verify APK code/resources are reused when supported rather than unnecessarily duplicating the entire package.
6. Stop/remove one instance and prove it does not kill or erase another.

## Background / AFK

1. Start the guest and leave the host app.
2. Confirm service notification and any requested foreground-service type.
3. Turn screen off while Wi-Fi remains connected.
4. After defined intervals, verify guest process/activity and network operation.
5. Turn screen on, resume the guest and compare its state.
6. Repeat with battery saver/background restrictions and document ROM-specific differences.
7. Verify service stop/unload releases handlers, threads, ImageReaders, sockets and wake locks.

## Error investigation: service exception / virtual user

For the "Cannot add mor..." failure capture the complete exception message and stack trace plus device API, guest package, precise operation and timestamp.

For the command containing --user 16 capture the callsite and each argument before and after any virtual-user mapping. Determine whether 16 is virtual, system, profile or internal. Do not change the ID blindly or suppress the exception.

## Performance

For one installed package and 1/2/3 instances, record:
- APK bytes stored by the host and incremental per-instance bytes.
- Process count and PSS/RSS per process and total.
- CPU / startup time / thread count.
- Guest data separation.
- resource/file-descriptor/thread cleanup after stop.
- background energy impact.

Compare like-for-like states rather than asserting "lightweight" without measurements.

## Evidence record template

- Commit:
- APK SHA-256:
- Device / ROM:
- Android / API:
- Test package / version:
- Preconditions:
- Steps:
- Expected:
- Actual:
- Logcat / diagnostic record:
- Result: PASS / FAIL / BLOCKED
- Known limitations:
- Follow-up issue/commit:
