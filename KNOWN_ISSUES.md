# KNOWN_ISSUES & PLATFORM CONSTRAINTS

## Resolved Issues
- **Fixed (v2.0.0):** Repeated Feature Readiness dialog appearing on every app launch. Root cause was asynchronous DataStore read initialization in `TimetableViewModel.kt`, which temporarily emitted `false` for `hasSeenOnboarding` before reading disk values. In `HomeScreen.kt`, `LaunchedEffect` triggered instantly on `false`. Fixed by changing initial state to `null` (`StateFlow<Boolean?>`), triggering only on `false`, and automatically marking onboarding as seen when all genuine permissions (DND, Alarms, Notifications) are already granted.
- **Fixed (v2.0.0):** Widget buttons unresponsiveness and lack of visual feedback on Xiaomi HyperOS. Fixed by adding `Intent.FLAG_RECEIVER_FOREGROUND` and explicit `package = context.packageName` to broadcast intents, enforcing 48x48dp minimum touch targets, displaying visible `lastUpdatedTime` ("Updated: hh:mm a") on all layout tiers, and dispatching immediate main-thread Toast notifications.
- **Fixed (v2.0.0):** Incorrect timetable time display and conversion (e.g. `7:15 AM - 7:16 PM` remaining 11h 57m instead of 1 minute, and single-digit hours). Fixed by updating time formatting to `"hh:mm a"` (`07:15 AM - 07:16 AM`), adding forgiving 24-hour hour conversion (hours 13-23 auto-normalize to PM), and replacing custom container boxes with high-contrast Material 3 `FilterChip` buttons.
- **Fixed (v2.0.0):** Inflexible full-period Smart Silent automation. Added Custom Smart Silent Schedules allowing students to configure specific silent and normal intervals inside a class (e.g., 10:00-10:30 Silent, 10:30-11:00 Normal) and choose end-of-class sound restoration behavior. Supported via Room Database v3 automated migration (`MIGRATION_2_3`).
- **Fixed (v1.3.0):** Battery optimization false-requirement in Feature Readiness dialog. Previously, the app checked `isIgnoringBatteryOptimizations` and displayed a red "Optimized" warning with a "Configure" button, leading users to believe battery exemption was mandatory. In reality, ClassNow uses `AlarmManager.setExactAndAllowWhileIdle()`, which Android's Doze mode already accommodates without exempting the entire app from battery optimization. Removed the card and eliminated any blocking checks.
- **Fixed (v1.3.0):** Widget buttons unresponsiveness on OEM launchers (specifically Xiaomi MIUI/HyperOS).
  1. *Root Cause 1:* The root `Box` in `ClassNowWidget.kt` had `.clickable(actionStartActivity<MainActivity>())`. On Android's `RemoteViews` implementation in MIUI/HyperOS launcher, attaching an `onClickPendingIntent` to the root view consumed or dropped touch events destined for nested child views. Fixed by removing root clickability and attaching `actionStartActivity` strictly to inner content cards.
  2. *Root Cause 2:* Glance's reflection-based `actionRunCallback` calls internal classes that can fail when target classes are renamed or when launcher sandbox restrictions limit intent redirection. Fixed by using explicit `actionSendBroadcast` directly targeting `ClassNowWidgetReceiver` with `goAsync()` execution, and adding ProGuard keep rules for Glance action callbacks.
  3. *Root Cause 3:* Buttons lacked rounded corners and used emojis (`🔕`, `🔔`). Replaced with crisp vector drawables (`ic_volume_off`, `ic_volume_up`, `ic_refresh`) and `cornerRadius(12.dp)`.
- **Fixed (v1.3.0):** Widget resizing limited on Xiaomi launcher. Previously, `classnow_widget_info.xml` specified `minHeight="50dp"` and `minResizeHeight="50dp"`. MIUI's launcher interpreted this as a single-row widget (4x1 or 2x1) and restricted vertical resizing options. Fixed by adjusting metadata to `minWidth="180dp"`, `minHeight="110dp"`, `minResizeWidth="110dp"`, `minResizeHeight="40dp"`, `targetCellWidth="4"`, and `targetCellHeight="2"`.
- **Fixed (v1.3.0):** Manage Timetable awkward nested card styling and time input. Redesigned entries into a single polished Material 3 card surface and added manual 12-hour time entry with AM/PM toggle.
- **Fixed (v1.1.0):** Stale widget progress bar during active classes. Previously, alarms were only set at class `endTime`, leaving progress at 0% throughout the entire lecture unless refreshed manually. Resolved by establishing 5-minute intermediate progress checkpoints and native Glance `LinearProgressIndicator`.
- **Fixed (v1.1.0):** App failing to launch on physical devices due to `ClassNotFoundException: MainActivity`. Corrected missing Kotlin/KSP plugin configurations.
- **Fixed (v1.1.0):** Widget showing "Can't show content" error. Handled `SecurityException` during exact alarm scheduling inside `WidgetAlarmScheduler` for Android 12+ devices.
- **Fixed (v1.1.0):** Outdated JDK compatibility error during `compileDebugJavaWithJavac` / `jlink`. Explicitly pinned `org.gradle.java.home` to Android Studio's bundled JDK 21 (JBR) in `gradle.properties`.

## Known Android & OEM Platform Constraints
1. **Home-Screen Widget Redraw Limits**:
   - Android does not permit continuous 60fps or second-by-second redraws of home-screen widgets without excessive battery drain.
   - Updates occur at:
     - Class start and end boundaries (exact AlarmManager trigger).
     - 5-minute intermediate progress checkpoints during active classes.
     - Timetable CRUD actions (immediate).
     - Manual widget refresh tap (immediate).
     - App launch / resume (immediate).
     - System events (`BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`).
2. **Exact Alarm Restrictions (Android 12+)**:
   - `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` can be revoked by the user or OS battery manager.
   - If revoked, the app falls back to `setAndAllowWhileIdle()`. Because `TimetableEngine` recalculates state from the local clock on every execution, the widget and sound mode self-correct immediately when the device wakes up.
3. **Do Not Disturb / Notification Policy Access (`ACCESS_NOTIFICATION_POLICY`)**:
   - Modifying ringer mode to Silent on Android 7.0+ (API 24+) requires DND permission.
   - If not granted, `SmartSilentManager` safely catches `SecurityException`, does not crash, and prompts the user with an in-app permission banner.
4. **Xiaomi / MIUI / HyperOS Launcher Grid Discretization**:
   - MIUI/HyperOS launcher quantizes widget resize handles based on home-screen grid settings (e.g., 4x6 vs 5x6).
   - ClassNow uses `SizeMode.Exact` and 5 adaptive layout buckets (Compact, Small, Medium, Large, Extra-Large) rather than rigid cell assumptions. This guarantees content never clips or overlaps, even when launchers introduce non-standard padding or rounding.
