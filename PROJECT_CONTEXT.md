# PROJECT_CONTEXT

## Project Purpose
ClassNow is a lightweight, reliable Android timetable application designed for students to answer one primary question: "What class do I have RIGHT NOW?"

## Current Architecture
- **UI:** Jetpack Compose with Material 3 (Main application) & Jetpack Glance (Home-screen widget).
- **Pattern:** UI / Widget -> ViewModel / BroadcastReceiver -> Central Engine (`TimetableEngine`) -> Repository (`TimetableRepository`, `SettingsRepository`) -> Room Database / Preferences DataStore.
- **Language:** Kotlin 2.0.21
- **Minimum SDK:** 26 (Android 8.0)
- **Target / Compile SDK:** 35 (Android 15)
- **Package Name / Application ID:** `com.teju.classnow`

## Technology Stack
- **Jetpack Compose & Material 3:** Modern UI with adaptive Light, Dark, and System theme modes and a restrained Aqua/Cyan identity palette.
- **Room Database 2.6.1:** Structured offline-first storage for timetable entries. Version 3 with automated SQL migrations (`MIGRATION_1_2` and `MIGRATION_2_3`).
- **Jetpack Glance 1.1.1:** Responsive home-screen widgets (`SizeMode.Exact`) rendering 5 distinct layouts: Compact, Small Square (2x2), Medium Horizontal (4x2), Large (4x3/4x4), and Extra-Large (4x5/expanded) with native progress indicators, direct sound mode controls, visible last-updated timestamps on all sizes, and foreground broadcast delivery with tactile toast feedback.
- **AlarmManager:** Exact transition alarms (`RTC_WAKEUP`) for class boundaries, custom sound interval boundaries within classes, break ends, midnight recalculation, and battery-conscious intermediate progress checkpoints (every 5 minutes during active classes). Inexact fallback for Android 12+ without exact alarm permissions.
- **Smart Silent Mode:** Automation layer controlling Android ringer mode during college timetable hours, with full DND permission handling, per-period overrides, custom sub-class sound schedules, and configurable end-of-class sound restoration.
- **Permission Readiness UX:** Comprehensive Material 3 sheet checking DND Access, Post Notifications, and Exact Alarms. Auto-dismisses when all permissions are granted; initial state starts as null to avoid launch flicker.

## Database Structure
- **Table:** `timetable_entries`
  - `id`: Long (Primary Key, Auto-generate)
  - `dayOfWeek`: String ("Monday", "Tuesday", etc.)
  - `subject`: String
  - `startTime`: String ("HH:mm", 24-hour format)
  - `endTime`: String ("HH:mm", 24-hour format)
  - `faculty`: String? (Optional)
  - `silentModeRule`: String ("DEFAULT", "FORCE_SILENT", "FORCE_SOUND", "IGNORE")
  - `customSilentSchedule`: String? (Optional JSON representation of `CustomClassSchedule` containing sound intervals and end action)

## Core Calculation Engine (`TimetableEngine`)
Single source of truth used across the Main Compose UI, Glance Widget, Persistent Live Notification, and Smart Silent Mode automation. Computes:
- Current date and local time.
- Schedule status: `CURRENT_CLASS`, `BREAK`, `LUNCH`, `FREE_PERIOD`, `BEFORE_CLASSES`, `NO_MORE_CLASSES`, `NO_CLASSES_TODAY`.
- Elapsed minutes, remaining minutes, total class duration.
- Progress fraction (0.0f - 1.0f) and progress percentage (0 - 100%).
- Next state transition local time and unique `periodKey` (accounting for custom sound intervals inside active classes).
- Smart Silent Mode decision (`SILENT`, `NORMAL`, `IGNORE`) respecting master toggle, per-period rule, custom interval schedule, and manual temporary overrides.

## Important File Locations
- **Main Activity:** `app/src/main/java/com/teju/classnow/MainActivity.kt`
- **Domain Engine:** `app/src/main/java/com/teju/classnow/domain/timetable/TimetableEngine.kt`
- **Widget Flow:** `app/src/main/java/com/teju/classnow/widget/ClassNowWidget.kt`, `WidgetTheme.kt`, `WidgetAlarmScheduler.kt`, `WidgetUpdateHelper.kt`, `ClassNowWidgetReceiver.kt`
- **Permissions & Readiness:** `app/src/main/java/com/teju/classnow/ui/permissions/PermissionsReadinessDialog.kt`, `app/src/main/java/com/teju/classnow/util/PermissionHelper.kt`
- **Sound Control:** `app/src/main/java/com/teju/classnow/util/SmartSilentManager.kt`
- **Database:** `app/src/main/java/com/teju/classnow/data/local/AppDatabase.kt`, `TimetableDao.kt`
- **Preferences:** `app/src/main/java/com/teju/classnow/data/local/SettingsRepository.kt`
- **Screens:** `app/src/main/java/com/teju/classnow/ui/home/HomeScreen.kt`, `ui/timetable/TimetableScreen.kt`
- **Theme:** `app/src/main/java/com/teju/classnow/ui/theme/Color.kt`, `Theme.kt`

