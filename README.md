# MoveSmart — Group 114

FIT5046 MoveSmart A4 development, based on the team's integrated A2 Compose prototype.

Member D milestone 1 adds persistent, user-scoped reminder settings, AlarmManager scheduling, notification permissions/channels, cancellation, calendar-based repeats and restore handling. Activity Details now links to Reminder Settings. Signed-in reminders use Member A's Firebase UID; Explore demo uses `local-demo`. See [the reminder implementation and verification notes](docs/A4_MEMBER_D_REMINDERS.md).

The `member-d-dev` branch integrates the team's A2 screens: member A's account/profile/preferences (`f611cd0`), member B's context-aware planning (`075a39c`), member C's Live Activity/History baseline (`6534891`), and member D's reminders/statistics/navigation (`59f6c39`). See the [integration record](docs/TEAM_INTEGRATION.md) for provenance and the small adaptations between modules.

Open this folder in Android Studio. On the Login screen, **Explore demo** enters the app without credentials. Use Home / History / Statistics / Profile in the bottom bar. Reminder settings are available from Home and Profile. Home → Find an activity → Activity details → Start activity → Live workout → History is the main demo flow. The context form includes a DatePicker, four dropdowns and a sample rain toggle. Recommendations respect the selected time, location and indoor/outdoor context. Saved profile intensity feeds the form; the weekly target and history records feed Statistics.

On the local `member-d-integration` branch, signed-in sessions use Member A authentication and preferences, and Member C Room records. Find an Activity loads real Open-Meteo weather for Melbourne city centre and the selected date, saves a user-scoped recommendation, then opens details by ID. Home reflects the loaded forecast. Reminder scope and Statistics use the Firebase UID; Explore demo is separate. Live workout/sensor playback still requires C integration. Sample completion is restricted to Explore demo; real accounts can record completed workouts manually in History.

Supply the team Firebase configuration as `app/google-services.json` to enable real Firebase login. Without it, the application builds with an explicit authentication configuration error and Explore demo remains available; no replacement Firebase project or credentials are created. Google sign-in also requires the team's client/SHA configuration. Device-level end-to-end verification remains pending.

- [Team integration, module sources and current report alignment](docs/TEAM_INTEGRATION.md)
- [Original A2 requirements, rubric, allocation audit and merge instructions](docs/A2_REVIEW_AND_HANDOFF.md)
- [Section 4/5/6 draft and six official references](docs/REPORT_SECTIONS_4_5_6.md)
- [Verification results and screenshot instructions](docs/VERIFICATION.md)
- [This session's AI usage record](docs/AI_USAGE_THIS_SESSION.md)

Build with JDK 21, compile SDK 37.1 and Gradle wrapper 9.5.0, following the original project's SDK settings. Minimum and target SDK remain 36.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

To run the UI checks, start an emulator and run `./gradlew :app:connectedDebugAndroidTest`.

The school GitLab repository defaults to `main`. For the shared baseline merge request, verify **target: main**, **source: member-d-dev**. Statistics work is on the independent `member-d-statistics` branch. When opening a request on either GitLab or GitHub, check the target and source explicitly; the two remotes may have different defaults.

## AI Assistance Declaration

I used AI (ChatGPT/Codex) to help design and build the architecture of the MoveSmart app prototype, including screen organisation, shared state and navigation. AI also assisted with generating and refining parts of the Jetpack Compose code, debugging, improving code structure and UI consistency, and writing and running unit and UI tests. These tools supported the development and testing of the A2 skeleton prototype. A more detailed account of the assistance is available in the [AI usage record](docs/AI_USAGE_THIS_SESSION.md).

## Local A/C/D integration verification

The integration branch merges school main `c429794` (C Room and the reviewed reminder fixes) and A branch `cde9ba3`. It preserves original author history. Supported real planning currently uses the Melbourne city-centre coordinates from A's interface guide; additional verified locations remain a follow-up. Every saved plan receives a unique session ID, avoiding overwrite for repeated inputs. Weather failure is not replaced with simulated weather; cached/stale forecasts remain labelled. The background interval and real Firebase sign-in still need device verification with team configuration.

Validation on 14 October 2026 after A integration: debug app and Android test APK built; all 176 host tests passed (zero failures, errors or skips); lint completed with zero errors, 25 warnings and 2 hints. New tests cover saved-plan persistence/failures/cancellation and account switching through Room-backed Statistics, reminders and logout. These are host/Robolectric results, not a real Firebase or device end-to-end run.

## Firebase configuration verification — 14 October 2026

The supplied team configuration has been installed locally at `app/google-services.json` and is excluded from Git. The configured package matches `com.example.movesmart`; Google Android and Web OAuth client entries are present. The packaged-resource test confirms selection of `FirebaseAuthRepository`. With this configuration, debug and Android test APKs build and all 177 host tests pass. This does not authenticate a real user or verify Google sign-in.

The refreshed team configuration supplied on 14 October now includes this checkout's debug signing SHA-1 (`26:54:D2:D8:3E:FA:51:10:93:C1:EC:E5:D8:B4:6A:57:C2:02:58:85`), the matching Android package and a Web OAuth client. It belongs to the same Firebase project as the original file. The local file was replaced and the full build/host-test/lint command succeeded (unchanged test outputs were up-to-date). This resolves the local configuration mismatch; actual Google and email/password sign-in still require device verification.

A host HTTP probe of the app's Open-Meteo request returned HTTP 200 with 384 hourly entries for Australia/Melbourne, including temperature, rain probability and wind speed. Android network and background execution remain device checks. The installed emulator exits with code 132 before boot; no Android device is currently available.

Remaining acceptance work:
- On a working API 36+ device, verify real sign-in, preferences, two-account isolation, logout and subsequent sign-in; run `connectedDebugAndroidTest` on a test device.
- Exercise Home → Find an Activity → saved details → reminder settings, and History add/edit/delete → day/week/month Statistics.
- Verify notification denial/regrant, actual inexact alarm delivery, notification taps, reboot/clock-change restore and sound/silent channels using the reminder guide.
- Integrate C's real movement/session implementation when available. Explore demo can save labelled sample sessions. Real accounts see an integration-pending message and can record completed workouts manually in History; no sample completion is written into their account.
- School `main` was subsequently fetched at `6eee53e` and merged locally, including A's latest auth/preferences/weather fixes. Both GitHub and school GitLab fetch authentication were verified. Publish the integration branch and review its diff against main before merging.
- Complete final assignment evidence and AI-use documentation with the team before submission.

### Latest school main integrated

School main `6eee53e` (including A follow-up `0ca0b66`) is merged into this local branch. A's preference session versioning, auth error handling and weather cache observation fixes are retained. D's Firebase-UID Room/Statistics/reminder integration replaces the earlier blanket restriction on signed-in workout history. Sample Live completion remains available only in Explore demo; signed-in users can manually add completed workouts in History. Weather session disposal now cancels both refresh and cache observation and invalidates retry state.

Final validation: 209 host tests passed with zero failures/errors/skips, debug APK and Android test APK built, lint zero errors (25 warnings and 2 hints). The Android onboarding test has been adapted to the real planning/UID statistics flow and compiled, but no device execution is claimed. Both remote fetches succeeded using existing macOS Keychain credentials. No remote main branch was modified by this local merge.
