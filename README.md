# KydoPhone

A free, offline, privacy-focused **native Android phone app** (Kotlin, Jetpack Compose, Material 3).
It is a real default-dialer implementation: `RoleManager` (dialer role), `TelecomManager`, an `InCallService`,
the Contacts and CallLog providers, multi-SIM support and full-screen incoming-call notifications.

* Min SDK 30 (Android 11) · Target/compile SDK 36 · English + Arabic (real RTL)
* No Play Services, Firebase, analytics, ads, telemetry, accounts or `INTERNET` permission
* Only app-specific data stored: theme, language, dial-pad tones, speed dials (SharedPreferences, backup disabled)
* Dependencies: AndroidX core/activity/lifecycle/navigation, Compose (BOM), Material 3, kotlinx-coroutines

## Getting the APK

**Cloud build (no local setup):** push this folder to a GitHub repository. The workflow in
`.github/workflows/build.yml` builds the debug APK and attaches it to the run as the artifact
`KydoPhone-debug-apk`.

**Local build:** install JDK 17, the Android SDK (platform 36) and Gradle 8.11+, then

```
gradle :app:assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
gradle :app:testDebugUnitTest
```
(Run `gradle wrapper` once if you want a `gradlew` script.)

## Using it as the phone app

Open the app → "Set as default" → confirm Android's role dialog. Incoming/outgoing calls then use this app's
call screen. Without the role the app still works as a dialer/contact browser, but calls use the system UI.

## Architecture

```
core/     PhoneUtils (pure), Prefs, LocaleHelper, Format, Intents
data/     Contacts / CallLog repositories (Android providers only), ContactIndex (T9 + lookup), models
telecom/  DialerInCallService, CallManager (Call -> StateFlow), CallNotifications, CallPlacer (SIMs), RoleUtils
ui/       Compose screens: Home (4 tabs), DialPad, Recents, Contacts/Favorites/Detail, SpeedDial, Settings, Setup, InCall
```

## Notification actions (the usual crash source)

Missed-call "Call back" and "Message" are `PendingIntent.getActivity` with `FLAG_IMMUTABLE`, unique request
codes per number/action, launched directly by the system (no Android 12+ notification trampoline). "Call back"
opens `MainActivity` with `ACTION_CALL_BACK`, which goes through the same permission / SIM-choice path as the
dial pad. "Message" opens the user's SMS app via `ACTION_SENDTO smsto:`; no SMS permission is used.

## Known limitations

* T9 name matching uses Latin letters; Arabic names match by digits in the number and by text search.
* Contacts without a phone number are not listed. Creating/editing contacts uses the system contacts editor.
* Blocking, voicemail visual UI, video calls, RTT and call recording are not implemented.
* Recents groups all calls for the same contact (including different numbers) and supports local search and a missed-only filter.
* Default outgoing SIM is the one chosen in Android settings; with "ask every time" the app shows its own SIM picker.
