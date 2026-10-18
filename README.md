# ONCE IN A WHILE — Android MVP source project

A native Android task/reminder app starter project for the Samsung Galaxy S23.

## Included in this MVP
- Material 3 / Jetpack Compose UI, follows system light/dark theme.
- Local task storage using SharedPreferences (JSON).
- Add tasks with category, date, time, recurrence (once/daily/weekly/monthly/yearly).
- Local notifications with a notification channel and a "Concluir" action.
- Repeating reminders every 5 minutes while a task is pending, capped at 12 reminders per task occurrence.
- Boot receiver to rebuild scheduled task reminders after device restart.
- No account, network permission, or remote push service.

## Not yet implemented
- Morning/evening daily summaries and their configurable times.
- Custom weekday selection, custom recurrence intervals, and a full overdue-management screen.
- Backup/export/import and an app-specific custom sound asset.
These are next-stage features; the current MVP is a starting implementation, not the complete final specification.

## Build
Requirements: Android Studio (recent stable), JDK 17 or 21, Android SDK Platform 35+.

Open this folder in Android Studio and allow Gradle sync. Then use:
**Build > Build Bundle(s) / APK(s) > Build APK(s)**

Expected APK path after a successful build:
`app/build/outputs/apk/debug/app-debug.apk`

This archive does not include a Gradle wrapper JAR. If you have Gradle installed, run `gradle wrapper` first, or simply open the project in Android Studio.

## Important limitations
- This source project has not been compiled or tested on a physical device in this environment.
- Android may delay alarms because of Doze, battery optimization, permissions, or manufacturer policies.
- Exact alarms require user approval on Android versions that restrict them. The app checks availability and uses inexact alarms when needed.
- Android 13+ requires notification permission.
- The 5-minute retry loop is capped at 12 reminders per task occurrence to avoid notification spam.
- Data stays local. Uninstalling the app removes app-private data.
