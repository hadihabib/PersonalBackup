# PBackup v3

PBackup keeps a local backup of SMS messages and call history.

## Automatic TXT files

The app now maintains exactly two visible text files:

- `Download/PBackup/messages.txt`
- `Download/PBackup/calls.txt`

New records are **appended to the same files automatically**. The app does not create a new TXT file for every event.

The SQLite database remains the primary local backup. If one of the TXT files is missing when the background service starts, PBackup recreates it from the local database.

## First run

1. Install the APK.
2. Open PBackup.
3. Tap **Grant permissions** and allow SMS, Call Log, Phone and Notifications permissions.
4. Tap **Start automatic backup**.
5. Keep the `PBackup active` notification enabled.
6. Optionally tap **Import existing records** once to copy existing SMS and call history into the backup.

## Xiaomi / HyperOS / MIUI

For reliable background operation, allow Auto-start for PBackup and set Battery Saver to **No restrictions**. Do not Force stop the app.

## Build APK with GitHub Actions

Open the repository on GitHub, go to **Actions → Build Android APK → Run workflow**. After a successful build, download the `PBackup-APK` artifact and install `app-debug.apk`.
