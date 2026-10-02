# Motion source distribution and rebuilding

## License boundary

The combined release uses GNU GPL version 3. New Oritwig components carry their own GPL-3.0-or-later notices. The Samsung and selected Nokia GNU references have been converted using LGPL-2.1 section 3; Mozilla's existing GPL-2.0-or-later alternative is selected as GPLv3. Retained FreeType, Pixman/X11, RapidJSON/BSD, STB, Abseil Apache-2.0 and runtime notices remain applicable to their components. Do not describe the entire tree as permissive, LGPL-only, or uniformly GPL-3.0-or-later.

The conversion is supported by [LGPL-2.1 section 3](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html) and the [FSF compatibility matrix, note 7](https://www.gnu.org/licenses/gpl-faq.html#AllCompatibility). The FSF identifies [Apache-2.0 as GPLv3-compatible](https://www.gnu.org/licenses/license-list.html#apache2); FreeType likewise identifies its [FTL credit license as GPLv3-compatible](https://freetype.org/license.html). These source/license facts are the basis for this packaging choice, not a blanket legal opinion about future dependencies or distribution terms.

`NOTICE.txt` is the complete component notice index. `LICENSE` and `provenance/licenses/` contain the license and notice texts for the source and binary packages. Original upstream headers are retained where their existing alternative or permissive grant is used. Historical alternative Qt and Mozilla notices do not override the selected combined distribution terms.

## Reproduce and verify the source changes

- `provenance/upstream-files.json` records the exact original Telegram pin, original URLs and 115 Git blob/SHA-256 identities
- `provenance/distribution-files.json` records final distributed hashes and the modification categories
- `provenance/license-only.patch` contains only leading notice/comment changes
- `provenance/integration-only-diff.json` separately records two unused Telegram logging includes removed to make the renderer standalone
- `provenance/apply-distribution-notices.py` applies the documented transformations to exact original files, offline; it also verifies already-transformed files without changing them
- `provenance/verify-distribution.py` independently reverses the notice patch in a temporary directory, restores the two includes, checks every original blob hash and verifies that no algorithm body or original copyright line changed

Run `python3 provenance/verify-distribution.py` before each binary/source release. The verifier requires Python 3 and the standard `patch` utility. It needs no network and never persists a second original source mirror in the distributed project.

## Release contents

Every binary download must have clear, equivalent access to the exact corresponding source, preferably a matching source archive beside the APK. Under the [GPLv3 source-distribution requirements](https://www.gnu.org/licenses/gpl-3.0.html#section6), a link only to unmodified Telegram is insufficient. Include:

1. The selected-license renderer source, public headers, native JNI source, Java source, resources and manifests
2. Complete CMake and Gradle definitions, wrapper, dependency verification/lock information, build scripts, patches and any generated source inputs needed to reproduce the release
3. Exact JDK, Android SDK, Build Tools, NDK and CMake versions and accessible tool acquisition instructions
4. This document, the main build instructions, `NOTICE.txt`, `LICENSE`, every file in `provenance/`, and all original component license files
5. ABI list and a release manifest connecting APK checksums, native-library checksums and the source revision

Do not include private signing keys, credential files, absolute local SDK configuration or unrelated build caches. A source archive must be self-contained as source; a fetch script alone is not a substitute for the source of libraries actually distributed.

The APK must carry offline copies of `NOTICE.txt`, `LICENSE` and `provenance/licenses/`, with an accessible About/Licenses entry. Merely storing notices on a website does not make them accompany each APK. Preserve the FreeType credit, exact Pixman file notices, selected Qt/Mozilla attribution, Abseil Apache notice and NDK runtime notices.

## Shared-library structure

For each supported ABI the package should contain `librlottie.so`, a separate `liboritwig_motion_jni.so`, and the required `libc++_shared.so`. The JNI ELF must list `librlottie.so` as `DT_NEEDED`. Avoid embedding rlottie objects into JNI through whole-archive/static linking or cross-library LTO. Preserve the public API and SONAME when replacing the library. Archive an ELF dependency report for the exact released native files.

Separating the shared libraries is useful engineering, but by itself is not proof of license compliance or an Android replacement workflow. Even under an LGPL distribution, shipping the LGPL binary would still require corresponding library source and notices; [the FSF FAQ explains the static/dynamic distinction](https://www.gnu.org/licenses/gpl-faq.html#LGPLStaticVsDynamic). This release additionally supplies the complete GPLv3 combined-work source.

## Practical replacement and installation

Document and test these steps for the exact release toolchain:

1. Build the provided source archive with its pinned tools
2. Make a small interface-compatible renderer change in a private working copy and rebuild `librlottie.so` for the desired ABI, using the same C++ runtime/ABI; no modification to the production source is needed for this test
3. Repackage through the normal Gradle APK build, with the replacement shared library and the original public API/JNI contract
4. Sign the rebuilt APK with the user's own local key, then install and render a fixture to show that the changed renderer actually runs
5. Prefer a documented alternate application ID for a side-by-side user build. If uninstalling the official build is necessary, first preserve/export user data and clearly explain the data-loss risk

Android requires signed APKs and generally requires the same certificate for an in-place update. A differently signed build can need a different application ID; see [Android signing considerations](https://developer.android.com/studio/publish/app-signing#considerations). Publishing the distributor's private key is unnecessary when user-built packages run with user-owned keys. Do not add signature pinning, DRM, server approval, integrity checks or contractual prohibitions that prevent such user modifications.

GPLv3's additional Installation Information rule depends on the distribution circumstances described in section 6; it is not automatically triggered by every standalone APK download. The documented user-build/install route should nevertheless work. Device/store terms or locked-device distribution may need separate review.

## Required pre-release evidence

- All provenance hashes and comment/body invariants pass
- Native libraries are genuinely separate for every shipped ABI
- The source archive builds without a private mirror, secret key or unpublished dependency
- The actual APK contains and exposes every required notice
- A modified-library user build can be signed, installed and exercised
- The final APK and source archive correspond to the same release revision

Until these release-specific checks pass, source review alone does not establish that an APK is ready to publish. This document covers only the selected historical renderer and its current dependencies; animation asset rights and future additions need their own review.
