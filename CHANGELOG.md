# CHANGELOG

## [2.2.0] - 2026-10-21
### Fixed
- **App–Widget Synchronization & ON/OFF Contradiction (P0):**
  - Eliminated the root cause of the widget button displaying the opposite state of the toast / DataStore setting.
  - Previous implementation in `ClassNowWidget.kt` read DataStore and Room snapshots outside `provideContent` via `.first()`, capturing stale closure values during active Glance recomposition sessions.
  - Moved flow observation directly inside `provideContent { ... }` using `collectAsState` with cold-start fallbacks for `isSmartSilentModeEnabled`, `manualOverridePeriod`, `themeMode`, and `dao.getEntriesForDay(currentDay)`. State changes in DataStore and Room now immediately trigger reactive widget recomposition without stale closure variables.
  - Streamlined `ClassNowWidgetReceiver.executeMasterToggle`: removed redundant duplicate Glance update invocation that previously caused concurrent composition race conditions, and wrapped the pipeline in `try-catch` to ensure truthful toast reporting.
- **Timetable Mutation Visibility & Latency (P0):**
  - Because `dao.getEntriesForDay(currentDay)` is now collected via `collectAsState` inside the widget composition session, any timetable mutation (adding, updating, deleting, reordering, clearing, or CSV importing) immediately updates active widgets without delay or manual app restart.
- **Theme Propagation to Widgets (P0):**
  - `themeMode` is now collected reactively via `collectAsState` inside `provideContent`. Toggling between LIGHT, DARK, and SYSTEM immediately updates the widget's color palette, including wallpaper dynamic colors on Android 12+ (API 31+), without altering sound mode or timetable alarms.
- **Current-Class Highlighting in Timetable Screen (P0):**
  - Replaced ad-hoc local time parsing in `TimetableScreen.kt` with authoritative domain `ScheduleState` from `TimetableEngine.getScheduleState`.
  - Class active state is now strictly computed as `isToday && scheduleState.status == TimetableStatus.CURRENT_CLASS && scheduleState.currentClass?.id == entry.id`.
  - Redesigned `TimetableEntryCard` active styling: upgraded from washed-out 25% opacity to vibrant, accessible `primaryContainer.copy(alpha = 0.70f)` background, 2dp solid primary border, 6dp vertical accent pill, onPrimaryContainer text contrast, and a prominent "HAPPENING NOW" badge.
- **Widget Layout Quality & Next Class Countdown (P1):**
  - Enhanced Medium (4x2) layout to display upcoming class hint alongside progress percentage.
  - Enhanced Large (4x3) layout to display exact minutes countdown (`• in X min`) when in break or before classes.
  - Ensured empty upcoming sections are handled cleanly without blank gaps or misleading labels.

### Added
- **Synchronization Regression Test Suite (`AppWidgetSynchronizationRegressionTest.kt`):**
  - Added 6 automated regression tests verifying exact start/end boundary transitions, back-to-back class transitions, break vs lunch vs free period categorization, single-class emptiness, theme resolution, and CSV timetable replacement. All 57 tests passing.

## [2.1.0] - 2026-10-21
### Fixed
- **Manual Time Input Interference (Phase 1):**
  - Removed eager keystroke normalization (`normalizeHour`) from `onValueChange` in `TimeInputField` that previously caused backspace to unpredictably replace `"0"` with `"12"` or `"1"` with `"01"`.
  - Moved zero-padding and 24-hr normalization strictly to focus loss (`onFocusChanged`) and save submission (`TimeInputHelper.to24Hour`), allowing uninhibited backspace, natural digit editing, and reliable cursor behavior.
  - Replaced small clock icon with a prominent `FilledTonalIconButton` featuring a 48×48dp touch target, 24dp Material schedule vector icon, clear semantic accessibility label, and primary container contrast.
  - Added full clock picker support to custom schedule intervals.
- **Instant Widget State Synchronization (Phase 2):**
  - Resolved widget button state desynchronization on Xiaomi MIUI / HyperOS where the button remained in the previous visual state after tapping Smart Silent Mode.
  - Identified root cause: `pendingResult.finish()` terminated before asynchronous Glance updates completed, causing OEM process suspension before `RemoteViews` reached `AppWidgetManager`.
  - Created centralized, authoritative `ClassNowWidgetReceiver.executeMasterToggle()` pipeline with coroutine `Mutex` locking to serialize rapid taps and prevent race conditions.
  - Synchronously executed `ClassNowWidget().update(appContext, glanceId)` for all active widget instances before calling `pendingResult.finish()`.
  - Unified DataStore access across BroadcastReceivers and Activities using `context.applicationContext`.
- **Immediate Widget Theme Synchronization (Phase 3):**
  - Updated `WidgetUpdateHelper.updateGlanceWidgets` to query and synchronously push UI updates to all active `GlanceId`s upon theme change in `TimetableViewModel`.
  - Preserves sound automation states, alarm schedules, and current class progress without duplicate calculations or ringer mode changes.
- **True System / Wallpaper-Based Dynamic Theming (Phase 4):**
  - Restored Material 3 wallpaper dynamic colors (`dynamicDarkColorScheme(context)` and `dynamicLightColorScheme(context)`) on Android 12+ (API 31+) for both the in-app `ClassNowTheme` and Glance `WidgetTheme`.
  - Implemented graceful high-contrast Slate-Navy (Dark) and Clean-Slate (Light) fallbacks for API < 31 or when fixed theme is chosen.
- **Custom Sound Schedule Discoverability & Smooth Animation (Phase 5):**
  - Wrapped Custom Sound Schedule section in `AnimatedVisibility` (smooth 250ms vertical expansion and fade).
  - Implemented automatic scroll-into-view using `LaunchedEffect(enableCustomSchedule)` and `scrollState.animateScrollTo(scrollState.maxValue)` so newly revealed controls are immediately visible without confusing manual scrolling.
  - Added interval counter badge, interactive clock pickers for every interval start/end time, and clear guidance on post-class end actions.

## [2.0.0] - 2026-10-20
### Fixed
- **Startup Permission Dialog Loop:** Resolved asynchronous DataStore loading race condition in `TimetableViewModel.kt`. Initialized `hasSeenOnboarding` state as `null` rather than `false` to avoid false-positive launches during initial disk read. Auto-dismisses and marks seen when genuine permissions are already granted.
- **Widget Responsiveness on Xiaomi Phones (MIUI / HyperOS):**
  - Added `Intent.FLAG_RECEIVER_FOREGROUND` and explicit `package = context.packageName` to widget broadcast intents.
  - Added visible `lastUpdatedTime` ("Updated: hh:mm a") to all widget sizes (Compact, Small, Medium, Large, ExtraLarge) providing immediate visual confirmation upon refresh.
  - Enforced 48x48dp minimum touch targets on all interactive widget buttons.
  - Dispatched main-thread Toast feedback ("Timetable refreshed", "Smart Silent Mode: ON/OFF", "Do Not Disturb access is required") on every user interaction.
- **Timetable Time Display & Parsing:**
  - Upgraded display format to `"hh:mm a"` ensuring two-digit hour formatting (`07:15 AM – 07:16 AM`).
  - Added forgiving 24-hour hour normalization in `TimeInputHelper` (converting hours 13-23 to PM automatically).
  - Replaced custom time container boxes with high-contrast Material 3 `FilterChip` buttons with 40-48dp touch targets.

### Added
- **Custom Smart Silent Schedules:**
  - Support for custom sound intervals within classes (`CustomSoundInterval` with `SILENT` or `NORMAL` mode).
  - Configurable post-class end actions (`DEFAULT`, `FORCE_NORMAL`, `KEEP_AS_IS`).
  - Room Database v3 schema update with automated SQL migration `MIGRATION_2_3` (`customSilentSchedule TEXT`).
  - Interactive interval builder in `AddEditClassDialog` with time validation and overlap prevention.
  - Schedule indicator badges in `TimetableEntryCard`.
  - Pure Kotlin JSON serialization in `CustomScheduleHelper` with zero new dependencies.
  - Sub-class interval boundary calculation and transition alarm scheduling in `TimetableEngine`.
- **Expanded Test Suite:** 47 passing unit tests (100% pass rate) including `CustomScheduleHelperTest`, `TimeInputHelperTest`, and `TimetableEngineTest`.

---

## [1.3.0] - 2026-10-19
### Fixed
- **Battery Optimization False-Requirement Removed (Phase 1):** Removed the "Background Execution / Optimized" card and mandatory "Configure" action from `PermissionsReadinessDialog`. Smart Silent Mode and app setup are no longer blocked when standard battery saver is active. Retained required checks: Do Not Disturb Access, Exact Alarms, and Post Notifications.
- **Widget Button Touch Responsiveness (Phase 2):** Fixed non-responsive `Smart Silent` and `Refresh` widget buttons on OEM launchers (specifically Xiaomi MIUI/HyperOS):
  - Removed parent `Box` `.clickable` modifier in `ClassNowWidget.kt` that was swallowing child touch events in Android's `RemoteViews` hierarchy.
  - Replaced reflection-based Glance callback invocation with explicit `actionSendBroadcast` directly targeting `ClassNowWidgetReceiver` with asynchronous execution via `goAsync()`.
  - Added ProGuard/R8 keep rules for Glance `ActionCallback` and widget receiver to prevent bytecode stripping during minification.
- **Widget Design & Emoji Removal (Phase 2):** Replaced emojis (`🔕`, `🔔`) with vector drawables (`ic_volume_off`, `ic_volume_up`, `ic_refresh`). Redesigned buttons with modern rounded corners (`cornerRadius(12.dp)`), balanced padding, and clear state labels (`Smart Silent: ON` / `Smart Silent: OFF`).
- **Practical Widget Sizes (Phase 3):** Updated `classnow_widget_info.xml` dimensions (`minWidth="180dp"`, `minHeight="110dp"`, `minResizeWidth="110dp"`, `minResizeHeight="40dp"`, `targetCellWidth="4"`, `targetCellHeight="2"`, `resizeMode="horizontal|vertical"`) to allow real multi-row launcher resizing on Xiaomi and other OEM launchers. Verified Glance layout responsiveness across Compact (<95dp height), Small (2x2), Medium (4x2), Large (4x3/4x4), and Extra-Large (expanded).
- **Manage Timetable Entry Card Redesign (Phase 4):** Replaced awkward nested gray rectangles with a single polished Material 3 card surface. Added subtle vertical accent pill, 12-hour AM/PM time range, faculty label, compact sound rule badges, and 48x48dp overflow menu touch targets.
- **Manual 12-Hour Time Entry with AM/PM (Phase 5):** Added editable hour (1–12) and minute (00–59) text fields, independent AM/PM toggle button group, and clock picker dialog (`TimeInputField`, `TimeInputHelper`). Handled noon (12:00 PM), midnight (12:00 AM), afternoon, and morning conversions with complete roundtrip unit tests.

### Added
- `TimeInputHelper.kt` utility and `TimeInputHelperTest.kt` unit test suite (6 new unit tests, bringing total test suite to 40 passing unit tests).
- Clean vector assets: `ic_volume_off.xml`, `ic_volume_up.xml`, `ic_refresh.xml`, and `ic_schedule.xml`.

---

## [1.2.0] - 2026-10-19
### Added
- **Responsive 5-Tier Widget System:** Fully adaptive Glance widget layouts supporting Compact (<110dp), Small Square (2x2), Medium Horizontal (4x2), Large (4x3 / 4x4), and Extra-Large (4x5 / expanded) launcher grid cells with `minResizeHeight="50dp"`.
- **App & Widget Theme Synchronization:** Complete design token parity (`WidgetColors`, `WidgetThemeTokens`) allowing System, Light, and Dark mode selections inside the app to immediately sync to all home screen widgets via `WidgetUpdateHelper.updateWidgetUiOnly()`.
- **Isolated Widget Refresh Action:** Dedicated 40-48dp refresh button container with `RefreshCallback` preventing accidental app launches on refresh taps while refreshing class progress and intermediate alarms.
- **Direct Smart Silent Widget Control:** Interactive toggle button directly inside the widget enabling or disabling college sound automation without opening the app, safely verifying DND permissions and redirecting to system settings if access is required.
- **First-Run Permission & Readiness Dialog:** Comprehensive Material 3 onboarding sheet (`PermissionsReadinessDialog`) explaining and checking Do Not Disturb Access, Notifications, Exact Alarms, and Background Battery Optimization with real-time lifecycle resume checks.
- **Expanded Test Suite:** Added 16 new unit tests covering widget layout sizing categorization, theme preference mapping, Smart Silent Mode rules, and manual override persistence (34 total unit tests passing).

### Changed
- Refactored `ClassNowWidget` root click handling so main container opens `MainActivity` while internal controls trigger isolated Glance callbacks.
- Enhanced `SettingsRepository` with `HAS_SEEN_ONBOARDING` to ensure non-intrusive first-run onboarding.

---

## [1.1.0] - 2026-10-19
### Added
- **Smart Silent Mode Automation:** Automated phone silencing during college timetable hours (classes, breaks, lunch, and free periods) with automatic restoration after the final class ends.
- **Per-Class Sound Rules:** Allows individual timetable periods to enforce `Force Silent`, `Force Sound`, or `Ignore Sound`.
- **Single-Source Timetable Engine (`TimetableEngine`):** Central domain calculation layer providing synchronized status, progress percentages, and state transitions across the app, widget, and notifications.
- **Progress Checkpoint Alarms:** Schedules battery-conscious 5-minute intermediate updates during active lectures so home-screen widget progress stays fresh without continuous battery drain.
- **Native Glance Progress Indicator:** Integrated native Glance `LinearProgressIndicator` across Small, Medium, and Large widget form factors.
- **Modern UI Redesign & Theme Switching:** Professional restrained Aqua/Cyan identity palette with support for System Default, Light, and Dark appearance modes.
- **Database v2 Migration (`MIGRATION_1_2`):** Seamless Room database upgrade preserving existing user timetable data.
- **Comprehensive Unit Test Suite:** 13 engine unit tests and 5 CSV helper unit tests verifying state transitions, boundaries, progress calculations, and export roundtrips.

### Fixed
- **Stale Widget Progress Bar:** Resolved issue where widget progress remained at 0% throughout an entire lecture due to absent intermediate alarms.
- **Diverging State Calculations:** Replaced disparate UI and widget progress math with unified `TimetableEngine` outputs.
- **Deprecated Compose Icons:** Updated `Icons.Filled.ArrowBack` to `Icons.AutoMirrored.Filled.ArrowBack`.
- **JDK Compatibility:** Pinned `org.gradle.java.home` to Android Studio's bundled OpenJDK 21 to prevent AGP jdkImage transform failures.

---

## [1.0.1] - 2025-05-22
### Fixed
- **Immediate Widget Data Update:** Migrated widget modification propagation model to Glance's explicit `ClassNowWidget().updateAll(context)`.
- **Responsive Size Variations:** Converted widget configuration to `SizeMode.Exact` with `LocalSize.current` adaptive layouts.

---

## [1.0.0] - 2025-05-22
### Added
- Release configuration with minification (R8/ProGuard) and resource shrinking enabled.
- Versioning structure aligned to `1.0.0`.
- Verified adaptive and legacy launcher icons.

---

## [0.1.0] - 2025-05-22
### Added
- Repository initialization and baseline timetable CRUD.
