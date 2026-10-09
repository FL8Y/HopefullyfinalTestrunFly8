# FLY8 Feature-Parity Matrix (76 Requirements)

This matrix carries the complete acceptance scope forward. A status applies to evidence currently present in the repository, not to intention. No row is complete merely because a class, screen, dependency, or workflow exists.

**Status key:** `PARTIAL` = some groundwork exists; `MISSING` = implementation absent; `UNVERIFIED` = code or claims exist but no device/runtime proof; `DOCUMENTED` = behavior recorded but not implemented; `BLOCKED` = needs required evidence/tooling before implementation can be chosen. Statuses must be revised with test evidence.

| # | Acceptance requirement | Current status | Evidence / next proof required |
|---:|---|---|---|
| 1 | Practical 1:1 behavioral parity with the reference while keeping FLY8 branding. | PARTIAL | Project and palette exist; end-to-end parity is not implemented. Use this matrix as acceptance control. |
| 2 | Wireless Debugging/ADB remains primary; Shizuku is optional and independent. | PARTIAL | Separate ADB and Shizuku code paths exist. Test both with Shizuku absent/present. |
| 3 | Apply exact dark palette: #000001, #010205, #01132A, #176E94, #01304C, #FCFDFD, #F5DADA, #626773. | PARTIAL | Color resources exist. Audit every screen and dialog for usage and contrast. |
| 4 | Package supplied cyber-eye artwork as a proper adaptive launcher icon and use it where permitted. | PARTIAL | Manifest points to FLY8 adaptive and round icons, with the supplied cyber-eye foreground and #01132A background. CI/source checks exist; installed-launcher appearance and APK packaging still require a successful build/device check. |
| 5 | Complete browse/manage, clone, launch, multi-instance and background workflow. | PARTIAL | APK picker, package controls and session actions are now wired to the VirtualApp adapter; full guest runtime behavior remains unverified. |
| 6 | Real clone launches its application, Activity, UI and input rather than a fake task. | UNVERIFIED | The adapter calls the virtual ActivityManager, but no test APK launch/render/input result has passed yet. |
| 7 | Use a compatible, licensed VirtualApp-style virtualization architecture. | UNVERIFIED | A pinned Apache-2.0 VirtualApp-derived module is linked and has a FLY8 adapter; CI currently fails during legacy ProGuard configuration before compilation. |
| 8 | Support independent instances of the same package. | UNVERIFIED | The adapter creates a separate VirtualApp user per instance and validates package registration in source; independent guest processes/data/tasks are not device-verified. |
| 9 | Guest can continue operating in the background/screen-off conditions supported by the reference. | MISSING | Inspect service/lifecycle architecture; validate screen-off operation on device. |
| 10 | Maintain appropriate Wi-Fi/network behavior when screen is off. | MISSING | Real guest networking and background/service behavior are not yet implemented or tested. |
| 11 | Make the Wireless Debugging workflow quick and understandable. | PARTIAL | Direct settings button and manual pairing/connect dialogs exist. Match the reference workflow and test on-device. |
| 12 | Open Android Wireless Debugging settings directly when the system supports it. | PARTIAL | An intent and fallback are present, but the behavior has not been device-verified. |
| 13 | Pair via Android's pairing port and six-digit code, then establish ADB correctly. | PARTIAL | LibADB pairing call exists behind a manual dialog. Validate pairing result, failure, timeout, retry and reconnect. |
| 14 | Correctly integrate LibADB 3.1.1, key handling, trust and bounded cleanup. | PARTIAL | Dependency and KeyStore draft are present. Compile, test handshake and review key lifetime/cleanup. |
| 15 | Reproduce the actual SPAKE2-Java/native pairing security flow where required. | UNVERIFIED | No direct FLY8 SPAKE2-native integration or end-to-end pairing verification is present. |
| 16 | Diagnose the observed Android ServiceSpecificException beginning “Cannot add…” at its source. | UNVERIFIED | Original trace is incomplete and no runtime reproduction is available here. Capture full stack, service and arguments. |
| 17 | Identify whether ID 16 is a virtual user, system user, profile or internal identifier. | UNVERIFIED | Do not assume its meaning. Trace value creation and its destination in reference and FLY8. |
| 18 | Never fake connection, clone, process, Activity, task, pairing, running state or background state. | PARTIAL | Engine contract explicitly rejects synthetic tasks, but runtime has not been built. Audit new code against this rule. |
| 19 | Continue the existing repository and preserve working changes. | PARTIAL | Work is being added to this repository. Review branches/history and build status before major refactors. |
| 20 | Inspect decompiled manifest, DEX, resources, native libraries, permissions and component names. | PARTIAL | Manifest/resources/native libraries and component inventory are mapped; supplied pseudo-smali lacks method bodies, so JADX method-level tracing remains. |
| 21 | Maintain feature-to-class/method/resource-to-FLY8 implementation mapping. | PARTIAL | The implementation map now records VirtualCore startup, package install, virtual-user creation and ActivityManager launch APIs; exact original-app call paths still need true method decompilation. |
| 22 | Target 100% functional parity where technically possible; document proven platform limitations. | PARTIAL | The target is recorded; acceptance needs measured feature-by-feature results. |
| 23 | Do not remove reference functionality to simplify Shizuku integration. | PARTIAL | Separate code paths exist. Regression tests must prove ADB still works with Shizuku unavailable. |
| 24 | Connection UI clearly separates Wireless Debugging from optional Shizuku. | PARTIAL | Both cards exist in the starter dashboard. Refine screen flow and status/error states. |
| 25 | Give meaningful operational errors and preserve diagnostic details. | PARTIAL | Some ADB errors are translated to readable messages. Add structured logs and full exception context without credentials. |
| 26 | Add logs for connection, pairing, engine, package, clone, Activity, process, task and service states. | MISSING | No unified, bounded diagnostic logger/log export exists. Never log pairing codes or key material. |
| 27 | Implement required background/foreground-service lifecycle, notification channels and modern Android requirements. | MISSING | No session/virtualization service is implemented. Determine exact requirements from source and platform API. |
| 28 | Run the Roblox end-to-end test including UI, Recent Apps, multiple instances and screen-off. | UNVERIFIED | No FLY8 virtualization runtime is available to test yet. Do not claim pass. |
| 29 | Use AccuBattery (com.digibites.accubattery) to diagnose the launch/user/service path. | UNVERIFIED | Reproduce the original failure with complete logs and test the package on the target device. |
| 30 | Recent Apps must reopen and resume the real guest Activity. | MISSING | No virtual Activity/task integration is present. |
| 31 | Support Android 12 behavior and modern Android through Android 16 with deliberate compatibility code. | UNVERIFIED | SDK 36 build configuration alone proves nothing. Build and test per API/device. |
| 32 | Preserve all required third-party licenses, attributions and source obligations. | PARTIAL | Initial notices exist. Verify every exact dependency and copied component before shipping. |
| 33 | Work in the FLY8 GitHub playground rather than starting an unrelated project. | PARTIAL | The current repository is the working tree. Keep future source changes here. |
| 34 | Use original FLY8 app identity, package, branding and supplied cyber-eye icon. | PARTIAL | FLY8 package/label exist; adaptive icon and full brand audit remain. |
| 35 | Keep cloned apps storage/memory-light in line with the reference architecture. | MISSING | Virtualization engine absent. Measure APK storage, data size, process count and memory after runtime exists. |
| 36 | Determine exactly why the reference's clones are lightweight rather than guessing. | DOCUMENTED | Virtualization can reuse code/resources and isolate state, but exact mechanism remains to be established from source. |
| 37 | Map shared code/APK/resources versus isolated databases, preferences, files, cache and identity. | MISSING | No guest filesystem/storage implementation or isolation tests exist. |
| 38 | Treat black Recent Apps previews as a possible snapshot limitation, not automatic death. | DOCUMENTED | Recorded in engine contract/blueprint. Implement state model and verify live instances independently. |
| 39 | Distinguish running process with black preview from a genuinely stopped/crashed process. | PARTIAL | Contract contains state fields, but no runtime process/preview probes are connected. |
| 40 | Reproduce live-feed/display capability when desired, based on observed reference architecture. | MISSING | Extracted class/native inventory suggests display-session components; method-level inspection and implementation are outstanding. |
| 41 | Use process/Activity/window/surface/task evidence to debug black screen and launch failures. | PARTIAL | State categories are documented. Add actual instrumentation and test output. |
| 42 | Represent installed, starting, running, background, resumed, stopping, stopped and failed states separately. | PARTIAL | State enum is a draft. Connect transitions to real runtime events and crash recovery. |
| 43 | Validate process, Activity, render, task, re-entry and background independently. | DOCUMENTED | Validation plan must record concrete evidence for each test. |
| 44 | Validate separate actual tasks/instances, not merely multiple cards. | MISSING | No real guest task architecture yet. |
| 45 | Trace instance identity, virtual user, package, process ID, task and data mappings. | MISSING | Build an explicit mapping and verify no IDs are passed as Android system users incorrectly. |
| 46 | Preserve virtual sessions after host UI is left, under supported Android conditions. | MISSING | No session service/runtime lifecycle exists. |
| 47 | Validate screen-off / screen-on resume without forcing display permanently awake. | UNVERIFIED | No device-tested implementation exists. |
| 48 | Validate Wi-Fi/network sockets, callbacks and guest network continuity. | UNVERIFIED | No running guest exists yet. |
| 49 | Keep ADB and Shizuku completely separate so each can operate without the other. | PARTIAL | Separate code paths exist; dependency injection and isolated regression tests are still needed. |
| 50 | Make pairing a minimal, guided flow with accurate status feedback. | PARTIAL | Manual IP/port/code entry works only as a code path; no live UX validation. |
| 51 | Prefer direct Wireless Debugging settings access with safe fallback. | PARTIAL | Implemented as a starter Intent/fallback, not yet verified on vendor ROMs. |
| 52 | Use proper ADB pairing/authentication instead of a fake success state. | PARTIAL | LibADB API is called, but the callback currently treats completion as success without target-device evidence. Verify library semantics and tests. |
| 53 | Audit LibADB's local sockets, mDNS, Keystore, peer trust and cleanup integration. | PARTIAL | Dependency/API source reviewed; FLY8 adaptations and lifecycle need tests. |
| 54 | Verify SPAKE2 and native library use and retain applicable licenses. | UNVERIFIED | Reference contains native library/license, but FLY8 does not yet integrate this component directly. |
| 55 | Reproduce the service exception and capture its actual throwing method/arguments. | UNVERIFIED | Need complete Logcat/stack trace from device and a matching reproduction. |
| 56 | Trace command arguments containing `--user 16` end-to-end. | UNVERIFIED | No source evidence yet establishes the meaning/value origin. |
| 57 | Retest AccuBattery after the root-cause fix. | UNVERIFIED | Blocked on reproducing and fixing guest launch. |
| 58 | Retest Roblox UI/input, instance switching, tasks and screen-off after engine integration. | UNVERIFIED | Blocked on a real runtime. |
| 59 | Measure CPU/RAM/storage/threads and remove unnecessary duplication/leaks without removing functionality. | MISSING | No baseline or performance harness exists. |
| 60 | Lightweight behavior must come from real virtualization, never fake app/task records. | PARTIAL | Policy is documented; enforcement depends on implementation review and runtime tests. |
| 61 | Provide safe diagnostic visibility into live guest state/feed when supported. | MISSING | No live display path or debug session panel exists. |
| 62 | Never mask a black preview with a fake screenshot. | PARTIAL | Policy is documented. Enforce in review and session-state code. |
| 63 | Model runtime lifecycle and independent preview availability. | PARTIAL | Data contract exists; transitions/probes are missing. |
| 64 | Do not declare a clone dead because a thumbnail is black or missing. | PARTIAL | Intended contract exists; it needs real process/task signal integration. |
| 65 | Inspect changes, build, test and commit coherently in the existing repository. | PARTIAL | The source audit passes. The retired JCenter dependencies now resolve via the root mirror. Enabling AIDL produced successful `:virtual-engine:compileDebugAidl` and `:virtual-engine:compileDebugJavaWithJavac`; the next run found app-side Kotlin result-type, installed-user array handling, and Developer Settings action compile errors. Fix commits `7d7fd117` and `4ee570d9` await the next CI verdict; no successful APK build yet. |
| 66 | Read decompiled source for relevant classes, JNI/native libraries, resources and permissions. | PARTIAL | Binary/resource inventory was inspected; complete Java decompile is still needed. |
| 67 | Ensure cloned package initialization, lifecycle, rendering, input, resume and multi-instance behavior are real. | UNVERIFIED | Real package/launch API calls are wired, but no guest process has been launched and validated from a FLY8 build yet. |
| 68 | Preserve resource-light package sharing and per-instance isolation. | MISSING | Engine/storage design and measurements remain. |
| 69 | Map each Recent Apps card to real virtual Activity, process, task and guest state. | MISSING | No task integration. |
| 70 | Meet background/AFK requirement where the device/platform permits. | MISSING | Service/process/network runtime absent and untested. |
| 71 | Maintain primary Wireless Debugging and optional Shizuku paths with independent handling. | PARTIAL | Starter routes exist; run both-path regression tests. |
| 72 | Detect pairing/connection failures and provide retry/cancel/timeout/cleanup behavior. | PARTIAL | Basic validation and messages exist. Add operation cancellation, timeout cleanup, and tested callbacks. |
| 73 | Verify actual LibADB/SPAKE2 transport security, persistent keys, trusted peer and reconnect. | UNVERIFIED | Current code is a draft and has not passed a real device pair/connect cycle. |
| 74 | Root-cause clone-start failures through user, package, process, Binder and task layers. | UNVERIFIED | The FLY8 adapter uses VirtualApp user/package APIs rather than constructing a shell command with --user; the original full stack trace and device reproduction are still unavailable. |
| 75 | Build and test compatibility on Android 12 and Android 16, documenting ROM-specific differences. | UNVERIFIED | Target SDK 36 is not proof of API compatibility. |
| 76 | Use reference → exact evidence → FLY8 implementation → runtime test → parity result; no deliberately omitted capability. | PARTIAL | This matrix and implementation map establish tracking; functional work and test results remain. |

## Exit criteria

Do not label the project 100% while any required row remains `MISSING`, `UNVERIFIED`, or `BLOCKED`. When a row changes, cite the source/class, commit, build/test output, Android version and device result in the accompanying implementation map or validation record.
