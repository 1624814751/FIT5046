# Member D reminder milestone

The A2 Reminder Settings screen only updated temporary UI state. This change persists settings and registers an Android alarm after an enabled reminder is saved. Disabling it or logging out cancels the pending alarm and any delivered notification. Activity Details now offers a Set reminder action.

## Scope and interfaces

`shared/SharedModels.kt` and `shared/ModuleContracts.kt` are copied unchanged from the supplied `MoveSmart_A4_Group114_Phase1_Starter.zip`. `ReminderStore` implements the shared `ReminderRepository`; `AndroidReminderScheduler` implements `ReminderScheduler`. Settings are keyed by `userId`, with ISO weekdays and an IANA time zone. This does not implement or change A's authentication/weather or C's Room/sensor modules.

The current A2 application has no authenticated user ID. `MoveSmartApp` therefore uses the explicit `local-demo` adapter. A's integration must replace this with `UserAccount.userId`, activate that user's reminder session after sign-in and call `ReminderService.logout()` at sign-out. An active session is persisted for background restore. Settings are kept locally after logout but their alarm is cancelled; signing back in restores an enabled schedule. This is not a claim that multi-user authentication is complete.

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

Six device integration tests compile and are ready in `ReminderIntegrationTest`: persistence/user isolation; edit/cancel; account switching/logout; the restore path; explicit PendingIntent delivery through the receiver and notification manager (independent of inexact alarm timing); and the silent channel. They have NOT been executed in this environment. The installed emulator fails before Android boots (`Illegal instruction` in headless mode and a Qt `neon` compatibility error in windowed mode). The failure is in the host emulator, not evidence of an application test failure or success.

Run on an API 36+ emulator/device after resolving that host issue:

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

Connect Home/planning/details to A's real weather/recommendation contracts, connect Statistics to C's persisted records with daily/monthly/date filtering, and complete integrated device tests. Real reminders do not make the other demo modules production implementations.

## Sources

- [Android alarm scheduling](https://developer.android.com/develop/background-work/services/alarms)
- [Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- Supplied Group 114 three-member responsibilities document and Phase 1 shared contracts.
