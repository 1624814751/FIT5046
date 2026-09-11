# MoveSmart

FIT5046 Assessment 2 Compose skeleton prototype for a context-aware fitness recommendation app.

The `member-d-dev` branch integrates the team's A2 screens: member A's account/profile/preferences (`f611cd0`), member B's context-aware planning (`075a39c`), member C's Live Activity/History baseline (`6534891`), and member D's reminders/statistics/navigation (`59f6c39`). See the [integration record](docs/TEAM_INTEGRATION.md) for provenance and the small adaptations between modules.

Open this folder in Android Studio. On the Login screen, **Explore demo** enters the app without credentials. Use Home / History / Statistics / Profile in the bottom bar. Reminder settings are available from Home and Profile. Home → Find an activity → Activity details → Start activity → Live workout → History is the main demo flow. The context form includes a DatePicker, four dropdowns and a sample rain toggle. Recommendations respect the selected time, location and indoor/outdoor context. Saved profile intensity feeds the form; the weekly target and history records feed Statistics.

This A2 build has no real authentication, Room, Retrofit, WorkManager, AlarmManager or notification delivery. The sample screens and callback interfaces are intended for A4 extension.

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

Before opening a PR, verify **base: main**, **compare: member-d-dev**. The repository currently defaults to `member-c-dev`; do not accidentally target that branch.

## AI Assistance Declaration

I used AI (ChatGPT/Codex) to help design and build the architecture of the MoveSmart app prototype, including screen organisation, shared state and navigation. AI also assisted with generating and refining parts of the Jetpack Compose code, debugging, improving code structure and UI consistency, and writing and running unit and UI tests. These tools supported the development and testing of the A2 skeleton prototype. A more detailed account of the assistance is available in the [AI usage record](docs/AI_USAGE_THIS_SESSION.md).
