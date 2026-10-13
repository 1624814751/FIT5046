# Member A Phase 1 testing

Use a Google Play-enabled API 36+ emulator or device. Complete Firebase setup before testing real accounts. Do not use a real personal password in screenshots or test reports.

## Firebase setup check

- [x] Firebase has an Android app with package name `com.example.movesmart`.
- [x] The team's `google-services.json` is stored as `app/google-services.json`.
- [x] Google Services Gradle plugin 4.5.0 is added and applied through the version catalog.
- [x] Email/Password is enabled in Firebase Authentication (confirmed by Member A).
- [x] Google is enabled in Firebase Authentication (confirmed by Member A).
- [ ] Debug and release SHA fingerprints are registered where needed.
- [x] `default_web_client_id` is generated from the Web OAuth client by `processDebugGoogleServices`.
- [ ] The emulator/device has network access and Google Play services.

## Sign Up

- [ ] Open Sign Up from Login.
- [ ] Submit an empty name and check that the name error appears.
- [ ] Enter an invalid email and check that the email error appears.
- [ ] Enter a password shorter than 12 characters and check the message.
- [ ] Enter different password and confirmation values and check the mismatch message.
- [ ] Create a new account with valid values.
- [ ] Check that the button cannot start another request while loading.
- [ ] Check that Home opens, then open Profile and confirm the name/email.
- [ ] Confirm the new account exists once in Firebase Authentication.

## Email Login

- [ ] Log out, then log in with the new email and password.
- [ ] Check that an incorrect password shows an error and stays on Login.
- [ ] Check behaviour with network disabled, then retry after network returns.
- [ ] Check that repeated taps do not send duplicate login requests.

## Google Login

- [ ] Tap Continue with Google.
- [ ] Select an account and confirm successful login.
- [ ] Cancel the account sheet and confirm loading stops with a clear message.
- [ ] Test on a device with no suitable Google credential and check the error.
- [ ] Test with network disabled and check that retry is possible.
- [ ] Confirm the Profile email/name match the selected account.

## Sign Out and session restoration

- [ ] Sign out from Profile and confirm Login opens.
- [ ] Confirm a signed-out user cannot return to Profile with the system Back button.
- [ ] Log in, close the app, and open it again.
- [ ] Confirm the Firebase session returns to the app without entering the password again.
- [ ] Confirm the Firebase UID remains the same before and after restart.

## Profile and preferences

- [ ] Open Profile and check account name, email, goal, intensity, activities, weekly minutes and environment.
- [ ] Open Edit Preferences.
- [ ] Change intensity, activities, goal, weekly minutes and environment.
- [ ] Save and check that the screen closes with the existing success message.
- [ ] Open Preferences again and check that every value was restored.
- [ ] Close and reopen the app and check the same values again.
- [ ] Confirm Home receives the saved intensity and goal through the existing callback.
- [ ] Confirm Statistics receives the saved weekly minute target.

## Account switching

- [ ] Save different preferences for account A.
- [ ] Sign out and log in as account B.
- [ ] Confirm account B does not see account A's preferences.
- [ ] Save different values for account B.
- [ ] Return to account A and confirm account A's original values return.

## Explore demo and existing navigation

- [ ] Sign out and tap Explore demo.
- [ ] Confirm demo mode opens without a Firebase account.
- [ ] Save demo preferences and confirm the existing Home/Statistics demo flow still works.
- [ ] Confirm demo values do not replace a real user's saved DataStore values.
- [ ] Check Home → Find an activity → Activity details → Live workout → History.
- [ ] Check Profile → Reminder settings and Profile → Statistics.
- [ ] Check demo logout returns to Login and resets the demo state.

## Automated checks

Run from the project root with JDK 21:

```sh
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

Record the date, command result, test count, warnings and device/API used. A successful JVM build does not prove Firebase or Google login works on a device.

### Automated result — 11 October 2026

- `:app:compileDebugKotlin`: passed.
- `:app:testDebugUnitTest`: passed, 38 tests, 0 failures and 0 errors.
- `:app:assembleDebug`: passed; debug APK was created.
- `:app:lintDebug`: passed with 0 errors, 21 warnings and 2 hints.
- The warnings are non-blocking SDK/dependency update suggestions and existing project cleanup suggestions. Firebase BoM 34.19.0 and Google ID 1.2.0 are intentionally pinned because their newest releases require Kotlin metadata newer than this project's Kotlin 2.2.10 compiler can read.
- No emulator/device authentication test was run. Firebase and Google setup is still missing.
