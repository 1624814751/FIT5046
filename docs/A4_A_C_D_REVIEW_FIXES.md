# A/C/D review fixes — 16 October 2026

## Scope and branch provenance

This integration combines D/C `7be25a6` with A `52f8cd9` (Shiya_dev). The fetched school target is `main@3d33297`. The merge includes A's verified-location UI and account protection; it is not solely a navigation patch. Main has not been changed by this work.

The team owner explicitly chose a login-required application on 16 October. Explore demo is removed. Legacy `local-demo` Room rows remain untouched and are excluded from signed-in users. UCI HAPT playback remains available within authenticated Live sessions and its records still carry `DataSource.DEMO`; this is a data provenance label, not guest access or physical sensor measurement.

## Review findings addressed

- **A compatibility:** retain A's synchronous UID checks, inactive ViewModel write rejection, pending-write cancellation, account-scoped navigation, recommendation and location invalidation. Resolve the overlapping files while retaining C's label-based warm-up hint and timer pause during CSV failures.
- **Reminder restoration:** ordinary restoration re-arms the persisted occurrence without cancelling an already-posted notification. An overdue occurrence is delivered, then the next calendar occurrence is scheduled. Multiple missed occurrences coalesce into one reminder. Explicit edits, disabling and logout still cancel obsolete reminders.
- **Interrupted notification delivery:** persist a pending occurrence before consuming the alarm. A later restore retries it if permission prevented posting or the process stopped. Reposting the same occurrence uses the same notification ID and suppresses an additional alert; a later occurrence can alert normally. Android may still delay inexact alarms.
- **Repeat exit:** the Leave callback checks the current dialog state, pending-save state and navigation entry before popping. The first callback clears the state synchronously, so another callback in the same frame cannot pop a second screen.
- **Deferred notification taps:** retain handling outside the account-keyed composition and defer navigation during Live and record editing. Successful save completes before opening Reminder settings.

## Regression coverage

New host tests cover overdue restore, cold-start activation before delivery, preservation of delivered notifications, future occurrence restoration, pending delivery after permission recovery, disabling and account switching. Navigation tests cover double Leave in one frame, duplicate Finish during a blocked write, blocked Back during save, and deferred notification navigation after both Live and editor saves.

A's account-boundary, location and recommendation tests remain. C's classifier, playback and Room tests remain. Former guest-flow tests now exercise authenticated flows, signed-out access rejection and preservation of legacy demo data. D's handled-notification account-switch test and UID-scoped sample-save test are retained.

## Validation status

Final host verification on 16 October passed: 275 tests, zero failures/errors/skips; debug APK and Android-test APK built; lint zero errors, 26 warnings and 2 hints. The build completed in 1 minute 10 seconds. ReminderRecoveryTest (7), AuthenticatedWorkoutNavigationTest (4), the editor-save/deferred-notification regression, A account/location tests and C playback tests passed. Two old test-fixture adaptation failures in the initial run were fixed before this complete rerun. No device execution of this combined revision is claimed.

Before approving, A/C should review the combined Changes, including the login-only decision. Perform device checks for actual inexact-alarm delivery while backgrounded, cold start with a posted notification, restored overdue delivery, account switching and saving after a delayed notification tap. Device reboot and OS scheduling behavior are not established by host tests.
