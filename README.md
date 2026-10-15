# MoveSmart — Group 114

FIT5046 MoveSmart A4 is a Kotlin / Jetpack Compose app for activity planning, dataset-based workout sessions, local history, statistics and reminders.

## Current integration

The current integration combines A `52f8cd9` with C/D `7be25a6` on `member-d-integration`. It requires Firebase login; Explore demo has been removed by team decision. Legacy `local-demo` records are retained separately and are not exposed to signed-in users. See [the A/C/D review fixes and validation status](docs/A4_A_C_D_REVIEW_FIXES.md). This branch still requires review before merging to school `main`.

Home and Find an activity use saved preferences, a verified selected location and Open-Meteo weather. Recommendation details explain the chosen activity. Weather failures are not replaced by fake forecasts; cached and stale data remain labelled. Location changes invalidate previous recommendation context.

Live workout replays the attributed UCI HAPT dataset, with pause/resume and elapsed-time recording. Finish saves a UID-scoped Room record and updates History and day/week/month Statistics. Dataset records retain `DataSource.DEMO` because the samples are published research data, not measurements of the signed-in user. History also supports manual add, edit, delete and search.

Reminder settings use Firebase UID ownership, local persistence, sound/silent notification channels and inexact AlarmManager scheduling. Restoring the app preserves posted notifications and recovers overdue occurrences. Android can delay delivery; no exact delivery deadline is promised. Back confirmation protects unsaved Live sessions, and notification taps wait while a Live session or record editor is open.

## Run and verify

Open this folder in Android Studio. Supply the team's Firebase configuration locally as `app/google-services.json` (excluded from Git). Google sign-in also requires the matching client and signing certificate configuration. Without Firebase configuration, the app displays an authentication configuration error; there is no guest fallback.

Use JDK 21, compile SDK 37.1 and Gradle wrapper 9.5.0. Minimum and target Android SDK are 36.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

For device tests, connect an API 36+ test device or emulator and run `./gradlew :app:connectedDebugAndroidTest`. Instrumentation tests can alter reminder test state; use a test installation and account. Building the Android-test APK does not mean those tests were executed.

## Repository and review

School GitLab: https://git.infotech.monash.edu/fit5046/fit5046-s2-2026/114

The school default branch is `main`. Current review: `member-d-integration` → `main`, MR !7. Verify the source and target before merging. GitHub is a secondary mirror; school GitLab is the contribution record for submission.

- [A/C/D review fixes and current validation](docs/A4_A_C_D_REVIEW_FIXES.md)
- [Reminder implementation and historical verification](docs/A4_MEMBER_D_REMINDERS.md)
- [Statistics implementation](docs/A4_MEMBER_D_STATISTICS.md)
- [C sensor dataset handoff](docs/A4_MEMBER_C_SENSOR_HANDOFF.md)
- [Historical A2 integration provenance](docs/TEAM_INTEGRATION.md)

## AI assistance

ChatGPT/Codex assisted with code integration, debugging, tests and documentation. The team must review the generated changes and complete the A4 AI acknowledgement with exact prompts, responses, human adjustments and evidence. The older [A2 AI usage record](docs/AI_USAGE_THIS_SESSION.md) is historical and does not constitute the complete A4 declaration.
