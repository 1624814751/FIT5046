# Verification and remaining checks

Verified 9 September 2026 against the local member-d-dev working tree based on 6534891.

| Check | Result |
|---|---|
| Debug application build | Passed |
| Android test APK build | Passed |
| JVM unit tests | 7 new model tests passed; existing example test also passed |
| Android lint | Passed, 0 errors; 13 warnings about inherited target/dependency versions, redundant activity label and unused template colors |
| Pixel 9 emulator | Android 17 / API 37, 1080 × 2424 |
| New instrumentation tests | All 5 passed after the final navigation and layout updates |
| Existing instrumentation example | Passed in the initial full connected test run |
| Git whitespace check | Passed |
| Branch isolation | Changes developed on member-d-dev, based on 6534891 from origin/main |

The five new emulator tests cover the main screen paths and screenshot capture; reminder settings after Activity recreation; rejecting an empty repeat schedule; search empty state and logout; and a forced dark Compose theme with 1.3× text including the time-input dialog and cancellation.

The seven new JVM tests cover Monday–Sunday boundaries and duplicate-day aggregation, an empty week, capped progress and zero goals, missing reminder name/repeat days, disabling incomplete reminders, midnight/invalid clock values, and fixture IDs/dates.

Sixteen genuine emulator screenshots accompany the deliverable. Some show transient save feedback or the system input toolbar. They are useful source material; the submitted report still needs its own all-screen navigation diagram and individual-screen layout. The dark/large-text captures are supplementary test configurations, not additional routes.

Not claimed as tested: every possible device size, landscape, full TalkBack traversal, full process-death recovery, real cloud authentication, databases, weather requests or scheduled notifications. Login passwords are deliberately not saved. The current demo's application data survives Activity recreation, but it is not durable storage across deliberate logout or a fresh session.

## Reproduce

Use JDK 21 and the installed Android SDK. Start an API 36+ emulator, then run from the project root:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

If the Kotlin daemon cannot write its local cache in a restricted environment, add `-Pkotlin.compiler.execution.strategy=in-process`. Do not change team-wide SDK or plugin versions just to silence update warnings.

For screenshot files retained after testing, install the two debug APKs and run the screenshot test directly, then pull the app's external screenshot directory before uninstalling. Gradle's managed test cleanup may remove that directory.

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class 'com.example.movesmart.PrototypeUiTest#allMajorScreensAndReturnPaths' com.example.movesmart.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/com.example.movesmart/files/a2-screenshots ./a2-screenshots
```

`gradlew` has also been made executable for macOS/Linux. The publication target is `origin/member-d-dev`; integration into `main` is a separate team review step.
