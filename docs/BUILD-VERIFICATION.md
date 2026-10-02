# Clean-source build and library replacement

The ordinary verification covers the final developer APK:

- Debug APK: `6706befc6892f6ff3cfa8015751358ed1baa06ce5001556a3d27016a95f55ed7`
- Unsigned release APK: `0c3e3a5a2077e30cf0f0a7b50086bade1e4b17026da39ef30ed362fbe7ae29d4`
- Release engine AAR: `1d3f6f380a3c55961ca73c7573ee10d1863bfb05552f5373e741ae5420f7933c`
- Independently built compiled-source snapshot: `f9ff81b0a654bd4b71763b27c7de83b718335375e02862e89aad3f6383f449a8`

A fresh source extraction, without a project build cache, passed `:engine:assembleRelease :app:assembleDebug :app:assembleRelease` with `--offline --no-daemon --no-build-cache`: 151 tasks executed. The existing public-tool/dependency cache supplied the documented pinned tools; this is not a first-ever offline bootstrap claim. Every one of the 207 snapshot files matched its source manifest, and the build left those files unchanged. The eventual delivery archive adds only completed documentation/media to those compiled inputs.

Both rebuilt APKs and the AAR have the same entry names and payloads as their frozen counterparts, except native GNU build IDs. Masking only `.note.gnu.build-id` makes each rebuilt native file byte-identical. Dex, assets, resources, notices and the shared C++ runtime match exactly. This is source/binary correspondence, not bit-for-bit reproducibility of whole signed APK/AAR files.

The actual APK has separate JNI and rlottie shared libraries for all three ABIs, correct `DT_NEEDED` and `SONAME`, complete matching notice assets and no declared permissions. Source provenance, wrapper checksum, alignment and existing debug-signature identity passed independently.

## Benign replacement proof

A separate source copy used application ID `dev.oritwig.motion.relinkcheck`. An interface-compatible rlottie edit marked pixel (0,0) green after ordinary rendering; a separate 64×64 synthetic-fixture test checked frames 0 and 59 and the retained moving-red geometry. No delivery file or production key was modified, and no new key was generated. Standard Gradle signing reused the existing local debug certificate.

The modified app installed side-by-side on Android 8.0/API 26 x86. The marker executed on both frames, ordinary geometry remained correct, and the resulting PNG was pulled and independently decoded. The original app remained installed. All three modified renderer ABIs retained the original public exports and SONAME; only x86 was exercised on a device.

The final app's nine native files are byte-identical to that proof's unmodified baseline. Later app changes only corrected export-message wording and actual saved-document naming, so they did not change the verified library-replacement interface. The marker library was never copied into the delivery app.

This ordinary build/notice/relink verification is not broad malformed-input testing or a general security or legal clearance. See [validation scope](VALIDATION.md) and [rebuild instructions](rebuild.md).
