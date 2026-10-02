# Validation and known limits

**Historical reference only:** [Samsung’s current rlottie README](https://github.com/Samsung/rlottie#readme) states that upstream is deprecated, no longer maintained and does not provide security support. The ordinary checks below do not establish production suitability. Use only self-authored test animations; do not open untrusted files or adopt this historical pin as a new project’s default dependency.

## Candidate identity

Developer/debug-signed APK SHA-256: `6706befc6892f6ff3cfa8015751358ed1baa06ce5001556a3d27016a95f55ed7`.

The source is self-contained and includes the native engine, app/adapters, pinned build definitions, wrapper/checksums, synthetic assets, regression tests, notices and source-transform verification. The release APK is intentionally unsigned; no production signing key is included.

## Completed build and packaging checks

- Debug APK, unsigned release APK, release engine AAR and Android instrumentation APK build with the documented pinned toolchain
- Ten JVM range/export-limit tests execute with zero failures, errors or skips; debug and release lint have no errors
- Offline source verifier reconstructs all 115 exact original upstream files: 60 unchanged, 55 leading-notice edits, and two separately recorded unused-include removals. It checks preserved algorithm bodies and copyright lines
- Actual APK packaging has 14 component notice/support files plus top-level NOTICE and LICENSE, 16 total entries; zero declared permissions; and nine native libraries across arm64-v8a, x86 and x86_64
- Every JNI library depends on a distinct `librlottie.so`. All packaged 64-bit native ELF load segments are 16 KiB aligned and the APK passes 16 KiB ZIP alignment. The NDK's 32-bit x86 C++ runtime uses 4 KiB ELF alignment

## Device acceptance

Final APK `6706befc…` passed 29 ordinary Android/JNI functional checks on Android 8.0/API 26 x86 software emulation: gzip/JSON input, multi-layer and reused precompositions, frame changes, channel/alpha layout, scaling, deterministic rendering, PNG round-trip, ZIP frames/timing/cancellation, render/sequence bounds, independent instances, close behavior and app launch.

Actual system-picker/UI checks passed:

- Real TGS document import, import cancellation, frame scrubbing/stepping and an invalid range error were checked before the two narrowly scoped export-message corrections; their capture identities are kept separate
- On the final APK, saved settings and frame 31 survived force-stop/relaunch; non-loop playback reached 59/59 and stopped
- Renamed SAF exports reported the actual chosen filenames. The PNG is 512×512 RGBA with alpha. The final ZIP contains 60 decoded alpha PNGs and a 30 fps/step 1 manifest; its frame 59 is pixel-identical to the standalone PNG
- Opening then cancelling another save returned to the unchanged animation without writing an output
- About exposes the supported subset and notices. Full-license navigation opened GPLv3 pages 1 and 2 of 5

Final media uses only final-APK captures, never relabeled older screenshots. It is an edited silent screenshot sequence, not a continuous playback recording.

The final functional-only run explicitly excludes adversarial-input fixtures. Broader independent malformed-input/security coverage remains incomplete. Clear-last-animation confirmation, process death while a save picker is open, all setting combinations, physical devices and newer Android versions are not claimed as tested.

## Scope

This is a local vector-subset workbench. It is not a full Lottie compatibility claim, hostile-file sandbox, real-time playback guarantee, video encoder or GIF encoder. See [input policy](INPUT-POLICY.md) for unsupported content and resource limits. Native cancellation cannot interrupt a rasterization already in progress.

Building arm64-v8a and x86_64 and checking their ELF layout is not equivalent to testing them on physical devices. Functional Android software-emulator coverage also does not establish Android 15 16 KiB page-size runtime acceptance or broad device compatibility. Public CI status will be reported separately after publication; a local pass is not a remote CI pass.

See [clean-source build and library replacement](BUILD-VERIFICATION.md) for the independent ordinary build/relink evidence and its limits.
