# Rebuilding and checking Motion

The generated distribution source is self-contained. Builds do not fetch Telegram, and do not need a private source directory.

## Pinned build tools

The accepted Linux build uses Eclipse Temurin JDK `21.0.12.1+1`, Gradle `8.11.1`, AGP `8.9.3`, Android platform `35`, Build Tools `35.0.0`, NDK `27.2.12479018` and CMake `3.22.1`. The wrapper verifies the Gradle distribution checksum; `tools/verify-wrapper.sh` also verifies the wrapper JAR. `gradle/verification-metadata.xml` pins downloaded build dependencies.

Install Android's command-line tools from the [official Android Studio download page](https://developer.android.com/studio#command-tools), then set `ANDROID_HOME` to that SDK directory and run:

```sh
sdkmanager --install "platforms;android-35" "build-tools;35.0.0" "ndk;27.2.12479018" "cmake;3.22.1" "platform-tools"
```

The SDK manager may ask you to review and accept Google's tool agreements. For the exact Linux x86_64 JDK, `tools/setup-jdk.sh /absolute/empty/jdk-directory` downloads the official Eclipse Temurin archive and verifies its recorded SHA-256; set `JAVA_HOME` and add its `bin` directory to `PATH`. For other hosts, use a JDK 21 from [Eclipse Adoptium](https://adoptium.net/temurin/releases/?version=21). Source builds need no credentials. First builds require access to the public Gradle, Google Maven and Maven Central endpoints; subsequent builds can use a populated Gradle cache with `--offline`.

## Android

```sh
./gradlew :engine:assembleRelease :app:assembleDebug :app:assembleRelease
./gradlew :engine:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest
python3 provenance/verify-distribution.py
python3 tools/check-packaging.py
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e functionalOnly true dev.oritwig.motion.test/dev.oritwig.motion.MotionInstrumentation
```

The command above selects the benign functional subset. The source retains input-validation regressions separately; their presence is not a claim that every final security check was executed.

The debug build is installable and signs with the builder's debug key. A release build is intentionally unsigned: use your own key. A differently signed build cannot update an existing installation in place; uninstalling first removes app-private state, so preserve any needed input/export documents beforehand. The app imports ordinary JSON/TGS and exports ordinary PNG/ZIP through the system picker.

The build workflow assembles debug and unsigned release APKs, the release engine AAR, and the instrumentation APK; checks debug/release lint, ten executed JVM tests, native packaging and source provenance; and pairs the developer APK with complete source from the same commit. It does not claim to run the Android device checks. See the release acceptance report for actual device coverage.

## Native host test

```sh
cmake -S engine/src/main/cpp -B build/host -DCMAKE_BUILD_TYPE=RelWithDebInfo
cmake --build build/host -j2
build/host/motion_native_test app/src/main/assets/test.json
```

Host GCC receives standard `<climits>`/`<limits>` declarations through build flags; Android Clang already receives them transitively. This does not alter the renderer. The test uses an independently authored synthetic red rectangle, checking metadata, transparency, movement, repeated rasterization and frame endpoint pixels.

## Relinking / reuse

`:engine` packages distinct `librlottie.so`, `liboritwig_motion_jni.so` and the matching NDK `libc++_shared.so`. The full source and CMake source list are included. To modify rlottie, edit its supplied source, rebuild the AAR and app, then install an APK signed with your own key. To replace the library independently, keep the public C++ ABI and shared C++ runtime consistent and rebuild the packaging project. JNI code contains only parameter checks, allocation/lifecycle and Android Bitmap locking.

No cloud service, Telegram credential or proprietary engine binary is required. Android SDK/NDK/Gradle are standard external build tools; their distribution agreements are separate. `provenance/licenses` includes the notices relevant to the packaged runtime. Instrumentation is synthetic and separate from real system-picker/UI acceptance.

For a side-by-side modified build, unpack the corresponding source into a separate directory, change only `applicationId 'dev.oritwig.motion'` in `app/build.gradle` to a personal ID such as `dev.oritwig.motion.userbuild`, and keep `namespace` unchanged. Make your renderer changes in that copy, then run `./gradlew :app:assembleDebug`. The standard Android plugin signs with your local debug key, without any distributor key. Install that copy with `adb install app/build/outputs/apk/debug/app-debug.apk`; it keeps the original app and its private data intact. Use a release key you control for longer-term distribution. Do not try to overwrite a differently signed official package.

## Optional upstream reproduction

`python3 tools/fetch-upstream.py --output /path/outside/this/repository` downloads each exact original file, validates its recorded Git blob and SHA-256, applies the documented notice/include transformation in memory, and compares the result with every distributed vendor file. It never overwrites the distribution tree. This optional check needs network access; ordinary builds and the offline source gate do not.
