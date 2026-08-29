# Grok Bar

Android home-screen widget that tracks your weekly Grok usage the same way Settings → Usage does on grok.com.

It shows:

- Percent of the weekly pool used
- Reset date and time
- A segmented progress bar for **Grok Build**, **Chat**, **Voice**, **Imagine**, and **Automations**

The in-app screen uses the same gauge so you can confirm data before pinning the widget.

## Setup

1. Build and install:

   ```powershell
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

2. Open **Grok Bar** and tap **Sign in**. Complete login on grok.com.

3. Long-press the home screen → **Widgets** → **Grok Bar** → **Grok usage**.

Optional: in Settings, paste a Grok Build access token from `~/.grok/auth.json` (after `grok login`). That path is more reliable for background widget refreshes.

Until you sign in, the widget shows sample data so you can preview the layout.

## How it works

Paid Grok plans share one weekly usage pool. This app reads the same billing config the website uses (`GetGrokCreditsConfig` / CLI credits JSON), then draws the official Usage bar:

- Electric blue `#1A5EFF` with stepped opacity per product
- 1px gaps between segments
- Unused remainder track
- Reset timestamp from the current weekly period

The widget refreshes about every 15–30 minutes while signed in, and whenever you open the app and tap Refresh.

## Notes

- Your grok.com cookie or Build token stays on the device (DataStore). Nothing is sent to third-party servers.
- grok.com may change its internal billing API. If the gauge stops updating, sign in again or refresh the Build token.

## Stack

Kotlin, Jetpack Compose, Material 3, Glance widgets, WorkManager, DataStore. Bootstrapped from the sibling `_template` Android project.
