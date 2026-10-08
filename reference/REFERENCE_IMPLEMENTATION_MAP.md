# Reference Implementation Map

This is an evidence ledger for the supplied reference APK extraction. It separates **observed artifact facts**, **behavior reported by the tester**, **engineering hypotheses**, and **verified FLY8 implementation**. Do not promote a hypothesis into a fact without method-level source or runtime evidence.

## Input corpus and decompilation quality

The local extraction inspected for this project contains two DEX files, compiled Android resources, bundled text notices, ABI-specific native libraries, and pseudo-smali stubs for roughly 40 app-owned classes. Its inventory reports 4,592 classes / 33,633 methods in the primary DEX and 1,211 classes / 13,013 methods in the secondary DEX.

**Important limitation:** the included pseudo-smali has method signatures and fields but not full method bodies. Its instructions say to run JADX on the APK for actual Java output. Therefore, this inventory is sufficient for system discovery and targeted searches but not to prove exact method behavior. Full DEX decompilation and method-level tracing remain required.

The original reference APK/decompilation is investigative material only. Do not add the APK, extracted binaries, original assets, activation resources, vendor identity, support URLs, or original package namespace to the FLY8 application or source repository.

## Evidence ledger

| Reference feature | Observed symbol/artifact | What can currently be concluded | FLY8 location / status | Evidence still required |
|---|---|---|---|---|
| App initialization / dashboard | NewHomeActivity, LoadingActivity, DashboardActivity | These screen class names appear in the extraction inventory. | MainActivity.kt; PARTIAL | Full method decompile; reproduce navigation and state flow in FLY8. |
| App browser / install flow | ListAppActivity, InstallerActivity | The reference exposes app browsing and an installer screen. | No app browser or installer; MISSING | Trace installed-package queries, APK/split handling, permissions and install flow. |
| Clone/session management | AppManageActivity, TaskManageActivity | Separate app management and task management screens are named. | VirtualizationEngine.kt contract only; MISSING | Trace data source, instance keys, task model, install/remove/launch methods. |
| Virtual engine | com.lody.virtual package family in the extracted inventory | The architecture appears VirtualApp-derived. | Engine interface only; MISSING | Full decompile of VirtualCore, VAppManagerService, VClientImpl, activity/task proxies, service brokers and hooks. |
| Guest package parsing | VirtualApp package parsing/cache components described in upstream architecture documentation | Guest package metadata must be parsed and registered for virtual queries. | No implementation; MISSING | Match reference parser/version and hidden-API handling to actual DEX. |
| Virtual PackageManager | PackageManager hooks / Binder service stubs | Requests from a guest may require a translated package/user/UID view. | No implementation; MISSING | Enumerate exact hooked methods, argument rewrites and return transforms. |
| Virtual ActivityManager | Activity/ActivityTask Manager hooks, transaction/lifecycle proxies | Virtual Activity launch and task restoration are core engine functions. | No implementation; MISSING | Trace startActivity flow, stub activities, task records, callback handling and Android 12–16 variants. |
| Guest process entry | VirtualApp client/process family | Separate guest processes are a fundamental design element of this engine family. | No implementation; MISSING | Trace process bootstrap, application binding, classloader, fake ApplicationInfo/UID and hook initialization. |
| Virtual user model | User IDs shown in launch logs, including value 16 | The number alone does not prove that it is a real Android user. | No implementation; UNVERIFIED | Trace all readers/writers of the value; map user ID to virtual instance, package, process and task. |
| File/data isolation | Native libva++.so; extracted architecture description mentions I/O redirection | A native virtualization layer exists and is associated with path/identity handling. | No implementation; MISSING | Audit native exports and callers; map guest root, user data, external storage, cache and cleanup. |
| Display/session control | ActiveDisplayActivity, ActiveDisplayService | A separate display/session feature exists in the extraction. | No implementation; MISSING | Decompile service/activity methods; identify capture projection, ImageReader, frame delivery, input forwarding, start/stop. |
| Display service clues | Extracted stubs list ImageReader, PowerManager.WakeLock, ExecutorService, handlers/threads, maps | These field types are clues consistent with frame/session management, not proof of exact control flow. | No implementation; UNVERIFIED | Inspect full JADX output and native/Java IPC; test device behavior. |
| Device pairing | PairingService | A dedicated pairing-related service exists in the extracted class inventory. | WirelessAdbController.kt; PARTIAL | Map service callbacks/notification/input flow to LibADB pairing method. |
| ADB pairing API | LibADB 3.1.1 API includes pair(host, port, sixDigitCode) | Current FLY8 uses a real library API rather than a success-only placeholder. This does not prove device interoperability. | Fly8AdbManager.kt, WirelessAdbController.kt; PARTIAL | Build; pair on Android 12/16; verify pairing result, cleanup, failures and reconnect. |
| ADB endpoint discovery | LibADB autoConnect supports mDNS discovery of ADB/TLS endpoints | FLY8 has an auto-discovery call. | WirelessAdbController.autoConnect; PARTIAL | Ensure multicast lock/lifecycle requirements and endpoint selection match target ROMs. |
| Persistent ADB identity | Android Keystore key and certificate overrides in FLY8 manager | Key material is intended to persist by alias. | Fly8AdbManager.kt; UNVERIFIED | Review library close() behavior versus disconnect(); test signature creation and key retention. |
| SPAKE2 | libspake2.so, SPAKE2-related license texts | The reference packages native SPAKE2 and associated license notices. | No direct FLY8 native integration; MISSING | Trace JNI entry points and Java callers; verify direct use required beyond LibADB pairing. |
| Hook runtime | libepic.so plus Java/native hook descriptions in inventory | A runtime hook framework is shipped. | No equivalent guest hook stack; MISSING | Determine actual use, required architecture/ABIs and compatible maintained implementation. |
| Native core / ABI | libva++.so appears under ARM64, ARM32 and x86_64 | Native engine code is present for multiple ABIs. | No native engine; MISSING | Verify supported ABIs and 16 KB page-size compatibility; preserve notices if ported. |
| Shizuku gateway | ShizukuDisplayGatewayActivity, HelperMain, HelperProvider, bridge/user-service class names | An auxiliary privilege/display bridge exists in the extracted class inventory. | Optional Shizuku connection card only; PARTIAL | Inspect helper protocol; keep separate from wireless ADB and add only required permissions. |
| Background/foreground service | Extracted manifest inventory reports foreground-service permissions/types and battery optimization request | The reference declares a service-oriented background design. | No session service; MISSING | Decode manifest, inspect service types, notification IDs/channels, locks, process recovery and API-specific restrictions. |
| Live feed and Recent Apps | Tester observes black Recent Apps preview while the app can still be running and says a live feed can be viewed on demand | Preview availability and guest liveness must be distinct state variables. | Data contract has previewAvailable; PARTIAL | Trace actual snapshot/capture path and verify on device; don't use preview appearance to infer process state. |
| Multiple instances | Tester observes multiple entries for the same game in Recent Apps | Multiple task/card entries were observed, but underlying IDs/processes must be verified independently. | No multi-instance runtime; MISSING | Map each card to actual virtual user/instance, process, task and data directory. |
| Lightweight storage | Tester reports clones are very lightweight | This is a valuable observation, not a measured claim in FLY8. | No engine/storage measurement; DOCUMENTED | Compare host APK storage, per-instance data deltas, PSS/RSS, process count and shared code/resource paths. |
| License notices | Extracted notice files for LibADB, SPAKE2, BouncyCastle and VirtualApp | Notice files are packaged in the reference extraction. | THIRD_PARTY_NOTICES.md; PARTIAL | Audit exact FLY8 dependency graph and all redistributed source/native artifacts; preserve required texts. |
| Launch error | Runtime error beginning ServiceSpecificException: Cannot add mor... | Only a partial failure message is available. | No reproduction; UNVERIFIED | Capture complete exception class/message/stack, device/API, package, timing and service transaction. |
| AccuBattery command | Arguments include --user 16 and package com.digibites.accubattery | The command text is a diagnostic symptom; it doesn't establish cause or semantics of ID 16. | No reproduction; UNVERIFIED | Log argument builder and map ID through package manager, virtual user manager, task/process launcher. |
| Android compatibility | Reference observed on Android 12 and Android 16 | These are tester observations for the reference environment. | FLY8 compileSdk = 36; UNVERIFIED | Real build and smoke tests at API 31/32 and API 36 device; note OEM differences. |

## Key architectural interpretation

A VirtualApp-style app is not simply an APK-copy manager. The host supplies a virtual package/service view, maps requests from guests, starts virtual processes, dispatches Activities through host-controlled components, rewrites package/UID/data identity where required, and may use native hooks for operations unavailable to Java hooks alone.

The current FLY8 project does not contain these runtime layers yet. The VirtualizationEngine interface is a contract and should stay clearly labeled as such until a compatible implementation is integrated and exercised.

## Integration decision rules

1. Prefer a complete, buildable, appropriately licensed VirtualApp-derived engine over independently recreating dozens of interdependent Binder and native-hook components.
2. Do not adopt a demonstration engine whose activity launcher merely logs and returns success. Such code does not satisfy the no-fake requirement.
3. Before adding a third-party engine, pin the exact commit, review license and included native submodules, establish required ABIs/NDK/Gradle versions, and make a compatibility branch.
4. Keep FLY8's UI/package identity separate from the engine's internal namespace and branding.
5. Preserve the LibADB connection path and optional Shizuku bridge while engine work proceeds.
6. Do not report 100% parity until the matrix has runtime evidence for every required row.
