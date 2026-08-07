# Kaptor iOS sample

A minimal SwiftUI app that presents the Kaptor inspector on iOS, backed by a MockEngine client
(offline, deterministic — the iOS analogue of `:sample-android`).

- `:sample-ios-shared` — Kotlin Multiplatform module that exports a **`Shared.framework`**. It owns
  the Darwin-free `MockEngine` backend, the Ktor client (with the Kaptor plugin), the rerunner, and
  the mock requests, and exposes `KaptorProvider.viewController()`.
- `sample-ios/` — the Xcode app (SwiftUI) that links `Shared.framework` and shows the inspector.

## Build & run on a simulator

```bash
# 1. Build the Kotlin framework
./gradlew :sample-ios-shared:linkDebugFrameworkIosSimulatorArm64

# 2. Generate the Xcode project (needs `brew install xcodegen`)
cd sample-ios && xcodegen generate

# 3. Build the app
xcodebuild -project KaptorSampleIos.xcodeproj -scheme KaptorSampleIos \
  -configuration Debug -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -derivedDataPath build build

# 4. Install & launch
UDID=$(xcrun simctl list devices available | grep -m1 'iPhone 17 Pro' | grep -oE '[0-9A-F-]{36}')
xcrun simctl boot "$UDID" 2>/dev/null
xcrun simctl install "$UDID" build/Build/Products/Debug-iphonesimulator/KaptorSampleIos.app
xcrun simctl launch "$UDID" com.akardas.kaptor.sampleios
```

The app seeds a little traffic on launch (`KaptorProvider.sendSample()`); tap **+** in the header to
fire more mock requests.

## Two required bits of wiring (already set in `project.yml`)

1. **`-lsqlite3`** in `OTHER_LDFLAGS` — SQLDelight's native driver references the system SQLite; the
   app must link it, or you get `Undefined symbols: _sqlite3_*` at link time.
2. **`CADisableMinimumFrameDurationOnPhone = true`** in `Info.plist` — Compose Multiplatform aborts
   on launch (Compose's `PlistSanityCheck`) without it.
