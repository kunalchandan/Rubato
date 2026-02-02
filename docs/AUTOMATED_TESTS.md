# Automated Test Coverage

This repository includes a mix of UI smoke tests, interaction tests, Android Auto checks, and macrobenchmarks. The focus is on core user flows while keeping tests fast and deterministic (no network dependence).

## Core UI / User Interaction (Instrumentation)

- `MainActivityLaunchTest`
  - Ensures the main activity launches and stays alive.
- `SmokeTest`
  - Verifies app startup and core navigation tabs.
- `UserInteractionTest`
  - Search UI: opens the Search panel and suggestions container.
  - Downloads UI: validates shuffle, filter, and metadata status controls or empty state.
  - Settings search: live filtering for preferences (theme entry).
  - Music sources: Subsonic/Jellyfin/Local sections + add buttons.
- `ThemeSelectorDialogTest`
  - Validates the theme picker dialog renders.

## Android Auto / Automotive

- `AutomotiveMediaBrowserTest`
  - Confirms MediaLibraryService can connect, load root/children, and handle search in a controller-friendly manner.
  - Uses a dummy server config to avoid network failures.

## Performance / Macrobenchmark

- `benchmark/HomeStartupBenchmark`
  - Startup timing for cold and warm starts.
  - Scroll benchmarks for Home, Library, and Downloaded screens.

## How to Run

Instrumentation (connected device/emulator):
```
./gradlew :app:connectedDebugAndroidTest
```

Automotive only (AAOS emulator, exclude arm64-only ffmpeg libs):
```
./gradlew :app:connectedDebugAndroidTest -PexcludeFfmpeg "-Pandroid.testInstrumentationRunnerArguments.class=one.chandan.rubato.automotive.AutomotiveMediaBrowserTest"
```

Macrobenchmarks:
```
./gradlew :benchmark:connectedDebugAndroidTest
```

## Performance/Flakiness Notes

- All interaction tests avoid network calls by using a dummy server config.
- AAOS emulator is x86_64; pass `-PexcludeFfmpeg` for test installs.
- Macrobenchmark results are written to `benchmark/build/outputs/connected_android_test_additional_output/`.
