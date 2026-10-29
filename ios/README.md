# Azanify for iOS

iPhone port of Azanify (Android 1.21.1). Built **without a Mac**: the macOS
runner in GitHub Actions compiles the Kotlin core and the SwiftUI app, boots an
iOS Simulator, launches the app and uploads screenshots as artifacts.

```
ios/                     standalone Kotlin Multiplatform build (Android build untouched)
  shared/                commonMain: prayer-time engine, city catalogue
iosApp/                  SwiftUI app + XcodeGen spec (no .xcodeproj in git)
.github/workflows/ios.yml   macOS runner: framework -> xcodebuild -> simctl screenshot
```

## Why a separate Gradle build

`ios/` has its own `settings.gradle.kts`. The Android modules (`:app`) are never
configured, so an iOS experiment can never break the Play Store build. The
Kotlin core in `ios/shared` is a dependency-free port of
`app/src/main/java/com/quransanctuary/app/domain/PrayerTimes.kt`.

## What works today (Phase 0)

| Piece | Status |
|---|---|
| Prayer-time engine in Kotlin/Native (Fajr 18°, Isha 17°, Asr 1×) | ✅ |
| 18 Azerbaijani cities, persisted selection | ✅ |
| SwiftUI shell: home + next prayer + countdown, 114-surah browser, settings | ✅ |
| CI: build → simulator → screenshot artifact | ✅ |
| Reader, audio (AVPlayer), downloads, azan notifications, share video | ⛔ next phases |

## Run it

### On CI (the normal path)

Push a change under `ios/` or `iosApp/` (or run the workflow manually):

```bash
gh workflow run iOS
gh run watch
gh run download --name ios-screenshots   # -> ios-home.png
```

### On a Mac (optional)

```bash
cd ios
gradle :shared:linkDebugFrameworkIosSimulatorArm64
mkdir -p ../iosApp/Frameworks
cp -R shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework ../iosApp/Frameworks/
cd ../iosApp
brew install xcodegen && xcodegen generate
open Azanify.xcodeproj
```

## Store submission (later)

1. Apple Developer Program — 99 USD/year.
2. `EAS`-style signing is not used here; add `xcodebuild -exportArchive` with an
   App Store provisioning profile, or switch to `fastlane` (`gym` + `pilot`).
3. Bundle id is already the Android one: `com.quransanctuary.app`.

## Conventions

- Every screen must survive a cold start with no network — the Android app is
  offline-first and iOS has to match.
- Shared logic lives in `ios/shared/commonMain`. Do not duplicate prayer math in
  Swift.
- `iosApp/Frameworks/` is generated; never commit it.
