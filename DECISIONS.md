# DECISIONS

## Baseline Architecture
- **Decision:** Room database for data persistence, DataStore for lightweight preferences, Jetpack Compose for UI.
- **Reason:** Fits standard modern Android development patterns for structured query capabilities and reactive state.
- **Date:** 2025-05-22

## Widget Progress Synchronization Strategy
- **Decision:** Do NOT use a permanent foreground service or second-by-second background polling to animate widget progress. Instead, use exact boundary alarms (`AlarmManager.RTC_WAKEUP`) for class start/end and state changes, combined with battery-conscious 5-minute intermediate progress checkpoints during ongoing classes. In addition, recalculate state dynamically on every app launch, manual refresh, and system time change.
- **Reason:** Android severely restricts widget redraws while the device is inactive and battery optimization kills excessive background loops. A 5-minute checkpoint gives students an accurate, smoothly advancing progress indicator during lectures without battery penalties.
- **Date:** 2026-10-19

## Single-Source-of-Truth Calculation Engine
- **Decision:** All timetable state determinations (period status, progress percentage, remaining minutes, next class, and silent mode decision) are computed by `TimetableEngine.getScheduleState(...)`.
- **Reason:** Previously, `HomeScreen.kt` and `ClassNowWidget.kt` had independent, diverging progress calculation logic. Centralizing this into a pure Kotlin domain engine ensures 100% state parity across the app UI, home-screen widget, persistent notification, and sound automation.
- **Date:** 2026-10-19

## Smart Silent Mode Implementation & Sound Semantics
- **Decision:** Utilize Android's `AudioManager.ringerMode` (`RINGER_MODE_SILENT` / `RINGER_MODE_VIBRATE`) backed by `NotificationManager.isNotificationPolicyAccessGranted()` (`ACCESS_NOTIFICATION_POLICY`).
- **Reason:** Android 7.0+ (API 24+) prevents apps from setting ringer mode to Silent without Do Not Disturb access. Checking this access and directing users to system DND settings prevents `SecurityException` crashes.
- **Decision:** Safe restoration and manual override policy:
  1. Save original ringer mode when Smart Silent Mode activates for the first period of the day.
  2. Restore original ringer mode immediately after the final class ends.
  3. If the user manually turns sound ON during a lecture, record a temporary override for that `periodKey`. Automation will not re-silence the phone during that period. When the next period starts, automation resumes automatically.
- **Date:** 2026-10-19

## Database Migration Strategy
- **Decision:** Use explicit `MIGRATION_1_2` (`ALTER TABLE timetable_entries ADD COLUMN silentModeRule TEXT NOT NULL DEFAULT 'DEFAULT'`) when upgrading database from version 1 to 2.
- **Reason:** Prevents destructive deletion of students' existing timetable data upon updating to the new version.
- **Date:** 2026-10-19

## Responsive 5-Tier Glance Widget Sizing
- **Decision:** Use `GlanceAppWidget.sizeMode = SizeMode.Exact` with `LocalSize.current` branching across 5 distinct layout thresholds: Compact (<110dp), Small Square (<200dp x <180dp), Medium Horizontal (height <180dp), Large (height <300dp), and Extra-Large (height >= 300dp).
- **Reason:** Different Android launcher OEM skins (Pixel, Samsung OneUI, Xiaomi MIUI/HyperOS, Nova Launcher) calculate widget cell bounds differently. Responsive thresholds prevent text overflow and layout truncation while maintaining optimal content density.
- **Date:** 2026-10-19

## App and Widget Theme Synchronization Decoupling
- **Decision:** Widget theme synchronization is handled through explicit design tokens (`WidgetColors`, `WidgetThemeTokens`) populated based on the single persistent source of truth (`SettingsRepository.THEME_MODE`). When theme preference changes in the app, call `WidgetUpdateHelper.updateWidgetUiOnly(context)`.
- **Reason:** Glance RemoteViews do not inherit Compose MaterialTheme contexts directly. Decoupled UI-only updates prevent unnecessary audio manager changes, database queries, or alarm cancellations when only the appearance mode is modified.
- **Date:** 2026-10-19

## Isolated Widget Action Touch Boundaries
- **Decision:** Separate the root container's `actionStartActivity<MainActivity>()` from child interactive controls by wrapping Refresh and Smart Silent buttons in dedicated `Box` containers with explicit `.clickable(actionRunCallback<...>())` and minimum 40–48dp touch targets.
- **Reason:** Glance/RemoteViews propagate touch events to parent click handlers if child click targets are too small or ambiguously nested. Clear separation ensures clicking the refresh icon or silent switch executes their respective background callbacks without unexpectedly launching `MainActivity`.
- **Date:** 2026-10-19

## Feature Readiness and Permissions Flow
- **Decision:** Instead of displaying sequential runtime permission popups on startup, display a unified Feature Readiness Dialog (`PermissionsReadinessDialog`) that explains the user benefit of each permission (DND, Notifications, Exact Alarms, Battery Optimization) with real-time lifecycle resume re-checking.
- **Reason:** Android users often dismiss sequential unexpected permission popups. Presenting contextual explanations with real-time status badges respects user autonomy and allows graceful degradation (the timetable remains fully usable even if sound automation is not granted).
- **Date:** 2026-10-19

