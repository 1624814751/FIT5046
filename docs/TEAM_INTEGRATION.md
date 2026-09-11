# Team A2 integration — 11 September 2026

## Sources and scope

Integration was performed on `member-d-dev` after fetching all remote branches. No member A, B, C or main branch was edited.

| Module | Remote source | Treatment |
|---|---|---|
| A: Login, Sign Up, Profile, Preferences | `origin/member-a-dev`, `f611cd0` | Merged with history; retain the four screen files, field/card layout, chips and goal radio buttons. |
| B: Home, Find an activity, Activity details | `origin/codex/b-context-module`, `075a39c` | This branch has an independent root and `com.example.adaptivemove` package. Imported its two Kotlin module files under MoveSmart's `ui/context`, adapting their names and callbacks. Its separate app, Gradle configuration and navigation shell were not imported. |
| C: Live Activity and History | `origin/member-c-dev` and `origin/main`, `6534891` | No newer remote commits. Keep the already integrated C screens; pass the selected activity, intensity and planned duration into Live Activity. |
| D: Reminder Settings, Statistics and integration | `59f6c39` | Retain D's page layouts and shared history/statistics model. Replace D's temporary A/B screens with the real member files. |

B's source commit is recorded above because its independent history cannot be combined by a normal fast-forward/pull. Source files were imported deliberately; no unrelated-history or force push was used. A's temporary navigation host was replaced by the existing shared host to avoid duplicate routes and bottom bars.

## Limited integration changes

- One app entry, one theme and one Navigation Compose graph. Bottom tabs: **Home / History / Statistics / Profile**. The original violet/lime palette and A/B card layouts remain.
- A's UI colours now use the common Material light/dark theme. The original forms retain their layout; demo entry, email checks, a 12-character signup passphrase requirement and password visibility controls align with the proposed password guidelines. No credentials are sent or stored.
- Profile keeps A's activity chips, goal and intensity. It also exposes D's reminders/statistics. Saving preferences forwards intensity to B and the weekly minute goal to D. Name/email entered during demo signup appear in Profile; demo profile fields survive Activity recreation.
- B's recommendation cards read the selected time, intensity, location and indoor/outdoor preference. Rain demonstrates an indoor alternative with an explicit explanation. The displayed duration never exceeds available time. Weather and movement are labelled as sample values; no wearable connection or live safety assessment is implied.
- B's `WorkoutViewModel` retains form selections through navigation/recreation using `SavedStateHandle`. DatePicker calendar dates use UTC consistently.
- B's Start activity callback launches C's screen. Saving a sample workout writes the same record used by History and Statistics, capped to the selected duration.
- Old snackbar messages are dismissed on navigation so a previous save message cannot cover the next screen's bottom action.
- Logout resets demo profile, records, reminders and context, and clears saved navigation stacks.

## Main screens and paths

Login ↔ Sign Up; Login/Explore demo → Home; successful Sign Up → Preferences → Home.

Home → Find an activity → Activity details → Live workout → History.

Home → Preferences (Adjust), Search, Reminder Settings.

History → Add workout / Edit workout / Search / Statistics. Search can open an existing record or start a sample activity.

Profile → Preferences / Reminder Settings / Statistics / Log out. Statistics → History.

There are 13 route patterns. Add and Edit share `record/{id}`, resulting in 14 report screen states. Dark/large-text captures and scrolled form captures are supplementary evidence, not extra routes.

## Report alignment for the team

The Google Drive report has not been edited by this code integration. Use screenshots from this integrated build when updating Section 3, the screen list and the navigation diagram. Previous screenshots from separate A/B/D prototypes show different navigation and should not be mixed into the final flow.

- Statistics supplies the proposed chart/progress visualisation. There is no map screen in this integrated build; align any map claims in the proposal with the team's final design choice.
- The two password guidelines can refer to signup passphrase validation and password visibility. Form examples include the context DatePicker/dropdowns and reminder labels, time and repeat-day validation. Check the exact Week 6 guidance before finalising the wording.
- Room, Retrofit, real authentication, sensor dataset playback, WorkManager, AlarmManager and delivered notifications remain A4 plans. The A2 skeleton uses demo data and UI state only.
- Final report, APA references, real AI history, cover details and completed A4 Task Allocation Form remain separate submission work.

## Verification

See [VERIFICATION.md](VERIFICATION.md) for the final checks and reproduction commands. Unit tests cover recommendation duration/context and existing statistics/reminder rules. Emulator tests cover all main destinations, account onboarding → preference → recommendation → statistics, and recommendation → live workout → shared history/statistics.
