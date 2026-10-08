# Virtualization Engine Adoption Decision

## Decision

Evaluate the Apache-2.0 VirtualApp-derived source at repository commit:

- Repository: https://github.com/f0restw0w/VirtualApp_16
- Pinned commit: b3c634ad7941765df3da84a207aca94b7861afae
- Source module candidate: lib/
- Candidate's own application module is not FLY8 and must not replace FLY8's UI/application identity.

This is an **engine candidate selected for a compatibility/build evaluation**, not a claim that it already works inside FLY8 or that it has passed Android 16 runtime tests.

## Why this candidate

The inspected source tree contains a real VirtualApp-derived runtime rather than only a mock UI. Source inspection found VClientImpl code that binds a package/process, resolves installed virtual package information, establishes a virtual runtime, starts native engine routines, constructs a guest package context, and initializes instrumentation/hooks. The repository also includes native I/O redirection and Binder-related hook components.

Its latest inspected commit message describes Android 11–16 compatibility work. The same commit's evidence mentions a test on an API 35 emulator; that is not proof of a real Android 16/API 36 device test. Keep this distinction visible.

## Why not use a demonstration framework as the runtime

A candidate whose ActivityManager start method only logs a package and returns true does not start a guest Activity, create a guest process, attach a window, or prove rendering. Such an implementation is unsuitable for this project and must not be merged as a working engine.

## Integration gates

Before accepting this candidate into the app's build:
1. Pin the exact upstream commit and keep the source as a clearly separate third-party module.
2. Inspect the full Apache-2.0 license plus every nested native/library/dependency notice.
3. Confirm the selected library module can be compiled with the chosen JDK, Gradle, Android Gradle Plugin, SDK and NDK.
4. Determine whether the library needs classes/resources/manifest entries from its upstream host application or extension package.
5. Port legacy Gradle settings only where required; do not make blanket changes that obscure upstream behavior.
6. Add a narrow FLY8 adapter around real engine calls; do not make the engine interface return fabricated success.
7. First test install/register and launch of one ordinary APK with real process and Activity evidence.
8. Only after the one-app test passes, proceed to Recent Apps, multiple instances, guest data isolation, display/live feed, and background operation.
9. Test the target API and ABI combinations; do not infer support from a version label alone.
10. If the source cannot be built/licensed/integrated, record the exact failing gate and evaluate another real engine. Do not switch to fake placeholders.

## Target integration architecture

FLY8 public UI and connection modules remain independent of the engine:

- FLY8 UI: own package, label, theme and supplied artwork.
- Wireless Debugging / ADB: remains a first-class connection route.
- Shizuku: optional adapter only.
- Virtualization library: separate internal module.
- FLY8 engine adapter: maps real install/launch/stop/session APIs into the app's model.
- Session diagnostics: records actual package/process/Activity/task/preview facts independently.

## Current state

The source repository has not yet been linked to this candidate, compiled with it, or tested with a guest APK. Do not describe the candidate as integrated until those steps have passed.
