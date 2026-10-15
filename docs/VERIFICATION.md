# Verification — integrated A2 build, 11 September 2026

## A4 local verification update — 15 October 2026

On `member-d-integration` based on `1f54f5c`, with the local Google sign-in button fix:

- Debug and Android test APK builds, lint, and all 209 JVM tests passed.
- On Pixel 9 (2), API 37, real Google consent and Firebase login reached the signed-in Home screen. The explicit sign-in button now uses `GetSignInWithGoogleOption`; the previous bottom-sheet request timed out despite a Google account being present.
- A fresh account showed zero sessions/minutes in weekly and monthly Statistics, without demo records. Saving default preferences enabled Find an activity.
- Live Melbourne forecast loaded (14.9 °C, rain 0, wind 1.9 km/h in this run); the activity details showed a 30-minute indoor Swimming recommendation and its rationale.
- Activity Details opened Reminder Settings. Saving the default Mon/Wed/Fri 18:30 reminder displayed “Reminder saved and scheduled” and the next occurrence. Disabling and saving displayed “Scheduled notifications cancelled”; reminders were left disabled after the check.
- The separate device runs passed all six `ReminderIntegrationTest` cases and the Room persistence/user-isolation test. Receiver-triggered notification checks are not evidence of real timed AlarmManager delivery.

Not yet verified for a final recording: actual timed notification delivery and notification-tap navigation, a populated signed-in History/Statistics UI flow, and switching between two real accounts. Real-account live workout tracking remains unconnected in this integration; demo workout playback must not be presented as real tracking. These checks do not certify the whole assignment as complete.

The historical A2 results below describe the older prototype, not the current A4 acceptance status.

Verified on `member-d-dev` after integrating A `f611cd0`, B `075a39c`, C `6534891`, and D `59f6c39`. See [TEAM_INTEGRATION.md](TEAM_INTEGRATION.md) for exact source handling.

| Check | Final result |
|---|---|
| Debug application build | Passed |
| Android test APK build | Passed |
| JVM unit tests | 11 passed: 7 statistics/reminder/fixture tests, 3 context recommendation tests, 1 existing example |
| Android instrumentation | 8 passed: 7 prototype UI tests and 1 existing example |
| Android lint | 0 errors, 16 warnings, 2 hints |
| Emulator | Pixel 9, Android 17 / API 37, 1080 × 2424 |
| Whitespace/conflict markers | Passed |
| Main UI screenshots | Captured from the integrated app; 18 selected images, including supplementary form and dark/large-text views |

The emulator checks cover all major screens and return paths; saved reminders after Activity recreation; rejection of an empty repeat schedule; search empty state and logout; dark/large-text reminder controls; A's signup validation and preferences flowing into B's recommendation and D's weekly goal; and a 15-minute rainy-weather recommendation flowing through C's live session into shared History/Statistics. B's context selections are also checked after Activity recreation.

During integration testing, a previous page's save snackbar obscured the next page's recommendation button. Navigation now dismisses stale snackbars; the full final instrumentation run passed after that fix.

Lint warnings concern inherited SDK/dependency versions, template resources, A's redundant `v26` launcher resource qualifier and mutable collection state that is replaced rather than mutated in place. The two hints recommend primitive state types. Dependencies/SDK versions were not broadly upgraded for this A2 integration.

Not claimed as verified: every device size, landscape, full TalkBack traversal, full process-death recovery, real authentication, Room, Retrofit, dataset playback, weather services or scheduled notifications. These remain A4 work. Passwords are neither saved nor transmitted. Demo state survives Activity recreation and resets on logout.

## Reproduce

Use JDK 21 and the installed Android SDK (compile SDK 37.1; minimum/target 36 inherited from the base project):

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

Start an API 36+ emulator and run `./gradlew :app:connectedDebugAndroidTest`. If the Kotlin daemon cannot write its cache in a restricted environment, use `-Pkotlin.compiler.execution.strategy=in-process`.

To keep screenshots after a direct test run, install both debug APKs and run the instrumentation runner before pulling the screenshot directory. Gradle's test cleanup may otherwise uninstall the app.

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.example.movesmart.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/com.example.movesmart/files/a2-screenshots ./a2-screenshots
```

Only use the latest selected integrated screenshots in the report. Older local screenshot files can remain on the emulator from previous versions. The shared Google Drive report was not edited during this integration.
