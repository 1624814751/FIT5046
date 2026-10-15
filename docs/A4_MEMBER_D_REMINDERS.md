# Member D reminder milestone

The A2 Reminder Settings screen only updated temporary UI state. This change persists settings and registers an Android alarm after an enabled reminder is saved. Disabling it or logging out cancels the pending alarm and any delivered notification. Activity Details now offers a Set reminder action.

## Scope and interfaces

`shared/SharedModels.kt` and `shared/ModuleContracts.kt` are copied unchanged from the supplied `MoveSmart_A4_Group114_Phase1_Starter.zip`. `ReminderStore` implements the shared `ReminderRepository`; `AndroidReminderScheduler` implements `ReminderScheduler`. Settings are keyed by `userId`, with ISO weekdays and an IANA time zone. This does not implement or change A's authentication/weather or C's Room/sensor modules.

The original milestone used an explicit `local-demo` adapter. On `member-d-integration`, `MoveSmartApp` now uses A's `UserAccount.userId` for real sessions, activates that user's reminders on sign-in, and calls `ReminderService.logout()` on sign-out. Explore demo retains its separate `local-demo` scope. An active session is persisted for background restore. Settings are kept locally after logout but their alarm is cancelled; signing back in restores an enabled schedule. Host tests cover account changes and reminder isolation; real Firebase sign-in and on-device delivery remain unverified.

## Scheduling and delivery

- The UI starts with reminders off and requests notification permission only through user action. A blocked app/channel prevents an enabled schedule and displays an actionable error. Settings can be saved even if scheduling later fails; the UI distinguishes saved settings from an active alarm.
- Each user has an immutable PendingIntent with a distinct URI. Saving edits replaces the previous alarm. A persisted trigger timestamp rejects obsolete/duplicate deliveries, and a process-wide mutex serializes edits, logout, restore and delivery.
- `setAndAllowWhileIdle` schedules an inexact, battery-aware fitness reminder. No exact-alarm permission is requested. Android can delay delivery, which is stated in the UI. The next selected calendar day is calculated after each trigger instead of using a fixed 24-hour interval.
- The saved time zone stays fixed and is displayed on the screen. A daylight-saving gap resolves forward; an overlap uses its earlier occurrence once. Clock and time-zone changes trigger recalculation using the stored zone. If the device is off at a scheduled time, restore chooses the next future occurrence rather than replaying a missed alert.
- Separate sound and silent channels preserve Android's user-controlled channel settings. A notification tap opens reminders after the current demo sign-in flow, or immediately when already in the app.
- Boot, app replacement and clock/time-zone broadcasts restore the active user's enabled reminder. Corrupt saved settings are rejected; restore cancels their pending alarm. As with Android alarms generally, force-stopping the app requires reopening it before scheduling resumes.
- Logcat tag: `MoveSmartReminder`. Events include `Alarm scheduled`, `Alarm triggered`, `Notification sent` and `Alarm cancelled`; labels/account IDs are not logged.

## Validation on 8 October 2026

The app and Android test APK compile. All 22 JVM tests pass (the 11 existing tests and 11 new reminder tests). Android lint has zero errors; it reports the baseline warnings plus three suggestions to use SharedPreferences KTX convenience methods. The synchronous `commit()` return value is deliberately checked for storage failure.

New JVM tests cover same-day scheduling, past/equal trigger times, weekday rollover, midnight, disabled/invalid settings, Melbourne daylight-saving transitions and an independent saved time zone. The existing UI tests were updated for the new default-off and persistent reminder behavior.

On 14 October 2026, all six `ReminderIntegrationTest` tests passed on a clean Pixel 9 (2) API 37 emulator launched through Android Studio's Running Devices panel: persistence/user isolation, edit/cancel, account switching/logout, restore path, direct PendingIntent receiver/notification delivery, and silent channel. Earlier command-line emulator launches failed before boot; the Android Studio launch succeeded. This is not evidence of actual timed AlarmManager delivery or a physical reboot. The separate `WorkoutDatabaseIntegrationTest` also passed, checking persistence after reopening and rejection of cross-user reads/updates/deletes.

To repeat on a working API 36+ emulator/device:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

Before treating the milestone as device-verified, also check permission denial/regrant, a physical/emulated reboot, changing time zone, and tapping a delivered notification with the app both open and closed. The automated restore test invokes the same restore service; it is not a reboot test. Android's inexact-alarm delay should be allowed for during demo preparation.

### Separate receiver tests from real alarm delivery

The receiver test sends the production PendingIntent directly and checks notification content, rearming and duplicate rejection. Its 10-second monotonic timeout bounds asynchronous receiver processing only; it does not claim that AlarmManager delivers within 10 seconds. The production scheduler remains inexact.

Real AlarmManager delivery is a separate manual device check:

1. Enable notifications and the relevant channel. Save a reminder for an upcoming time and the correct weekday/time zone using the app.
2. Record the requested time, device/API version, battery saver and idle state. Confirm `Alarm scheduled` in `MoveSmartReminder` logcat; do not manually send the PendingIntent during this check.
3. Observe `Alarm triggered` and `Notification sent`, record the actual delivery time and inspect the notification. Confirm a tap opens reminders and the next occurrence is scheduled.
4. Repeat with the device idle. Android may defer inexact alarms; no fixed 60-second pass/fail deadline applies. If the observation session ends before delivery, record **inconclusive / not observed**, retain logs and investigate permissions, force-stop state and system scheduling before diagnosing a failure.

The real alarm check remains pending until delivery is observed on a working device. Compiling or passing the direct receiver test is not evidence that this check passed.

## Remaining D work

Home/planning/details now use A's weather/recommendation contracts, and daily/weekly/monthly Statistics consumes C's Room records by Firebase UID on the local integration branch. Device acceptance still remains: actual login, notification permission/channel handling, alarm delivery and taps, reboot/clock-change restore. C's live sensor/session integration and final submission evidence are also pending. See README for the 14 October configuration and build results.

## Sources

- [Android alarm scheduling](https://developer.android.com/develop/background-work/services/alarms)
- [Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- Supplied Group 114 three-member responsibilities document and Phase 1 shared contracts.

## Local navigation follow-up — 16 October 2026

The Android Studio `member-d-integration` checkout now includes the fetched school main `3d33297`.
Notification requests are consumed above the account-scoped navigation tree so a handled tap is not replayed after account switching. A tap during Live workout or a record editor stays pending until that screen is left, preserving the in-progress flow.

At commit `fa9dd64`, the demo Live route confirms toolbar/system Back before discarding an unsaved session. Stay keeps the session open; Leave does not write a record. Signed-in Live was not yet available at that milestone; the integration follow-up below extends the guard.

Regression coverage adds account-change notification handling, deferred notification navigation/discard, and toolbar/system Back followed by successful workout save. The initial two failures exposed handler ordering and a missing second-user test fixture; both were corrected.

Verified on 16 October 2026 at approximately 00:42 Melbourne time after the user ran the build in Terminal: BUILD SUCCESSFUL, all 231 host tests across 27 suites passed (zero failures, errors or skips), both debug and Android test APKs built, lint reported zero errors, 25 warnings and 2 hints. The XML reports were inspected directly. These are host/build results; the physical follow-ups below separately record the revised navigation checks.
To repeat from the task workspace, run `sh work/run-d-navigation.sh`; this builds both APKs, runs the host tests and lint. Remaining device checks include Live-workout notification deferral, idle/cold-start delivery, reboot restore and real two-account isolation; the awake-device editor flow is verified below. The physical phone's retained Outdoor Walk test record must not be deleted.

Physical-device follow-up at 00:44–00:46: Samsung SM-F971B API 37 showed the exit confirmation for both toolbar and system Back. Stay retained the advancing timer; Leave returned to Activity details. No session save/delete was performed. At this stage notification-tap deferral and account-change non-replay had only host-test evidence; see the subsequent physical notification follow-up below.

Physical notification follow-up at 00:52–00:56 on 16 October: on Samsung SM-F971B API 37, Demo reminder was saved for Friday 00:53 Australia/Sydney. The app displayed the next occurrence and scheduled confirmation. With the device awake and USB charging, an actual Time to move notification (timestamp 00:53) was observed at 00:54 without manually invoking the receiver. Exact delivery latency, idle behavior and battery-saver configuration were not measured. Tapping it while Add workout was open kept the editor visible; Cancel then navigated to Reminder settings. The next occurrence displayed Friday 23 October 00:53. The test reminder was disabled and saved; cancellation text and Profile Off were verified. No workout was saved or deleted. Logout returned to Login. At 00:59, after the user completed real-account login, the physical device showed the personalized Home screen with Home selected, rather than Reminder settings. This Demo-to-real session transition did not visibly replay the previously handled notification; it is not a two-real-account isolation test. Live-workout notification deferral has not yet been manually exercised on the phone.

## Member C/D integration follow-up — 16 October 2026

Integrates Member C's `f619337` Live workout flow with D's `fa9dd64` navigation changes. The shared test fixture retains both C's deterministic sensor playback and D's notification request injection/second-account fixture. The authenticated UID-save test, notification non-replay test and demo separation checks remain; logout-to-demo coverage is retained as a separate test.

Toolbar and system Back now confirm leaving every Live workout, including authenticated sessions. Added host regression cases cover authenticated Stay/Discard, notification deferral until discard, and two rapid save callbacks while a database write is pending. The latter also checks blocked Back navigation and exactly one account-owned record with no demo-account write.

Member C's motion readings remain disclosed UCI HAPT dataset playback and saved records retain `DataSource.DEMO`, even when owned by a Firebase UID. This is not live phone-sensor capture. Verified on 16 October 2026 at approximately 01:49 Melbourne time using the user-run Terminal build: all 236 host tests across 28 suites passed (zero failures, errors or skips), debug APK and Android-test APK builds succeeded, and lint reported zero errors, 25 warnings and 2 hints. XML test and lint reports were inspected directly. The first run exposed two test-helper errors (attempting to scroll dialog buttons); the helper was corrected and the complete suite rerun successfully. These are host/build checks; the combined authenticated Live flow has not yet been retested on a physical device. The earlier physical checks above apply to the earlier revision only.
