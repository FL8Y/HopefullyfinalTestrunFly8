# FLY8 Reference Blueprint

This file records behavior observed from the supplied reference APK without carrying its vendor branding, hosted licensing flow, or vendor-specific package namespace into FLY8.

## Core runtime clues

The reference includes a full Android virtualization layer around VirtualApp-style components. Important areas include:

- virtual ActivityManager and PackageManager hooks
- virtual users and UID mapping
- task and activity launching
- virtual process startup
- content-provider and broadcast virtualization
- display and input routing
- native hook support
- ADB and wireless pairing
- optional elevated-operation bridge
- long-running display/session service
- task/session recovery

## Lightweight clone model

The observed clone model is not equivalent to storing a complete APK copy for every instance. The implementation should determine which APK, code, and resources are shared and which state is isolated per virtual instance.

Isolation must cover the state that the guest app expects to be independent, while avoiding unnecessary storage duplication.

## Recent Apps behavior

A Recent Apps card is not sufficient to declare success.

The runtime needs separate state for:

1. package installed
2. virtual instance registered
3. process created
4. application initialized
5. activity created
6. window or surface attached
7. rendering available
8. task registered
9. preview or snapshot available

A black Recent Apps preview can coexist with a live virtual process. FLY8 therefore must not treat a black preview as automatic proof of failure.

## Background behavior

The reference keeps virtualized sessions alive after the physical display is turned off under supported conditions. The eventual implementation should reproduce the underlying service, process, and network lifecycle rather than keeping the screen permanently awake.

## Connection behavior

Wireless Debugging and ADB are the primary connection path.

An optional Shizuku path may coexist with it. Shizuku must not replace or remove Wireless Debugging.

The pairing implementation needs to be based on actual ADB transport and authentication behavior, including the reference use of pairing primitives where appropriate.

## Current failure signatures

Observed failures include:

- a ServiceSpecificException beginning with Cannot add
- launch arguments containing a virtual user value such as --user 16
- clones that appear in Recent Apps without reliably rendering the guest UI

These need root-cause tracing through user mapping, activity startup, process startup, task registration, and modern Android compatibility.

## Clean implementation rule

The supplied APK is used as a behavioral reference. FLY8 should implement the same externally observable capabilities using independently maintained code and properly licensed third-party components.

Vendor-specific application classes and branding are not carried into this source tree.
