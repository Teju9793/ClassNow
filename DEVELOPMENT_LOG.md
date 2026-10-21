# DEVELOPMENT_LOG

# 2026-10-21 — ClassNow V2.2: P0 App–Widget Synchronization, Theme Propagation & Current-Class UI

## Root Causes Identified & Engineering Fixes
1. **Widget Button ON/OFF Contradiction with Toast:**
   - *Root Cause:* In `ClassNowWidget.kt`, `provideGlance(context, id)` read `isSmartSilentEnabled`, `manualOverridePeriod`, `themeMode`, and `entries` as one-off snapshot values using `.first()` outside `provideContent { ... }`. When an active Glance session recomposed, `provideGlance` was not re-executed; instead, the recomposition reused the stale captured closure variables. Consequently, even though DataStore was successfully written and the Toast announced "Smart Silent Mode: ON", the widget recomposed with the old captured boolean (`false`), displaying "Smart Silent: OFF".
   - *Fix:* Replaced static closure captures with live flow observation inside `provideContent { ... }` using `collectAsState`:
     - `val isSmartSilentEnabled by settings.isSmartSilentModeEnabled.collectAsState(initial = initialSilent)`
     - `val manualOverridePeriod by settings.manualOverridePeriod.collectAsState(initial = initialOverride)`
     - `val themeMode by settings.themeMode.collectAsState(initial = initialTheme)`
     - `val entries by dao.getEntriesForDay(currentDay).collectAsState(initial = initialEntries)`
     Every mutation in DataStore or Room now directly triggers a reactive recomposition in the Glance session with authoritative state.
2. **Concurrent Conflicting Glance Invocations:**
   - *Root Cause:* In `ClassNowWidgetReceiver.executeMasterToggle`, `WidgetUpdateHelper.updateGlanceWidgets` was called, immediately followed by `WidgetUpdateHelper.triggerUpdate`, which also called `updateGlanceWidgets`. This initiated duplicate concurrent composition sessions in Glance, leading to race conditions.
   - *Fix:* Streamlined `executeMasterToggle` into a single authoritative pipeline: persist state -> `triggerUpdate(appContext)` (which updates Glance, audio mode, alarms, and notifications) -> display truthful Toast. Wrapped the entire pipeline in `try-catch` to report errors truthfully.
3. **Delayed Widget Timetable Mutations & Theme Updates:**
   - *Root Cause:* Timetable edits and theme mode changes were relying on queued broadcasts that did not guarantee active Glance session re-evaluation without an explicit trigger.
   - *Fix:* Observing `dao.getEntriesForDay` and `settings.themeMode` directly within `provideContent` via `collectAsState` makes all mutations (add, edit, delete, clear, CSV import, theme change) immediately visible across all active widgets.
4. **Current-Class Highlighting Missing / Ineffective in Timetable Screen:**
   - *Root Cause:* `TimetableScreen.kt` was attempting to parse local times ad-hoc inside `remember(entry, currentTime, isToday)` instead of using the shared domain engine `TimetableEngine.getScheduleState`. Furthermore, the visual styling was set to `primaryContainer.copy(alpha = 0.25f)` with a 1.5dp border, rendering it faint and washed out against the surface in both light and dark themes.
   - *Fix:*
     1. Collected `scheduleState by viewModel.scheduleState.collectAsState()` in `TimetableScreen.kt`.
     2. Computed active status as `isToday && scheduleState.status == TimetableStatus.CURRENT_CLASS && scheduleState.currentClass?.id == entry.id`.
     3. Redesigned `TimetableEntryCard` active styling: prominent `primaryContainer.copy(alpha = 0.70f)` background, 2dp solid primary border, 6dp vertical accent strip, onPrimaryContainer text contrast, and a prominent "HAPPENING NOW" pill badge.
5. **Widget Layout Polish (P1):**
   - Added next class hint in Medium layout (`4x2`).
   - Added countdown to next class (`• in X min`) in Large layout (`4x3`).
   - Cleaned empty states so no blank cards or misleading sections appear when classes are finished or absent.

## Verification
- **Unit Tests:** `BUILD SUCCESSFUL` (57/57 passed, 100% pass rate).
- **Debug APK:** `BUILD SUCCESSFUL` (`app-debug.apk` compiled and assembled).

---

# 2026-10-20 — ClassNow V2: Critical Bug Fixes, Custom Smart Silent Schedule & Polish

## Problem Statements & Root Causes Discovered
1. **Repeated Feature Readiness Dialog on Startup**:
   - *Problem:* Every time the app was opened, the Feature Readiness dialog popped up, even when all permissions were already granted.
   - *Root Cause:* In `TimetableViewModel.kt`, `hasSeenOnboarding` was declared as `settingsRepository.hasSeenOnboarding.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)`. During the initial DataStore disk read on app launch, it temporarily emitted `false` for several frames. `HomeScreen`'s `LaunchedEffect(hasSeenOnboarding)` immediately triggered on `false` and displayed the dialog before the actual value loaded.
   - *Fix:* Changed `hasSeenOnboarding` in `TimetableViewModel` to start with initial value `null` (`StateFlow<Boolean?>`). In `HomeScreen.kt`, `LaunchedEffect` only runs when `hasSeenOnboarding == false`. Furthermore, if all genuine permissions (`hasDndPermission && hasExactAlarmPermission && hasNotificationPermission`) are already granted, it automatically marks the onboarding as seen in DataStore and bypasses the dialog entirely. Manual access from Settings is preserved.
2. **Widget Buttons Unresponsiveness & Lack of Visual Feedback on Xiaomi Phone**:
   - *Problem:* On Xiaomi MIUI/HyperOS, tapping widget buttons appeared dead/unresponsive.
   - *Root Cause:* Compact, Small, and Medium layouts did not display any timestamp or indication that a refresh had completed. Moreover, broadcast intents without explicit foreground flags or application package targeting were subject to background execution throttling on HyperOS.
   - *Fix:*
     1. Added `Intent.FLAG_RECEIVER_FOREGROUND` and explicit `package = context.packageName` to all widget button broadcast intents.
     2. Enforced 48x48dp minimum touch target sizes on `WidgetRefreshButton` and `WidgetSilentToggleButton`.
     3. Added visible `lastUpdatedTime` ("Updated: hh:mm a") to all 5 widget sizes (Compact, Small, Medium, Large, ExtraLarge) so user immediately sees the refreshed time.
     4. Dispatched main-thread Toast feedback ("Timetable refreshed", "Smart Silent Mode: ON/OFF", "Do Not Disturb access is required") from `ClassNowWidgetReceiver` and Glance action callbacks.
3. **Incorrect Timetable Time Display & Conversion**:
   - *Problem:* A 1-minute class showed `7:15 AM – 7:16 PM` (11 hours 57 min remaining instead of 1 minute) and times lacked two-digit hour formatting.
   - *Root Cause:* `TimeUtils` used single-digit hour pattern `"h:mm a"`. Time entry AM/PM toggle was a custom box without prominent contrast, and hours entered in 24-hour format (e.g. 19:00) were rejected instead of normalized.
   - *Fix:*
     1. Updated `TimeUtils.kt` to use `"hh:mm a"` ensuring two-digit hour formatting (`07:15 AM – 07:16 AM`).
     2. Updated `TimeInputHelper.kt` with `normalizeHour` and forgiving 24-hour hour conversion (hours 13–23 auto-convert to 12-hour and set `isPm = true`; hour 0 converts to 12 AM).
     3. Upgraded `TimeInputField` in `TimetableScreen.kt` with Material 3 `FilterChip` buttons for AM/PM selection with high contrast and proper touch targets.
4. **Custom Smart Silent Schedules (Sub-Class Intervals)**:
   - *Requirement:* Allow users to define custom sound intervals inside a class (e.g. 10:00–10:30 Silent, 10:30–11:00 Normal Sound) and configure post-class sound behavior (Default, Force Normal Sound, Keep Current Sound).
   - *Implementation:*
     1. Created `CustomSoundInterval` and `CustomClassSchedule` models.
     2. Created `CustomScheduleHelper` with pure Kotlin JSON serializer and validator (zero new dependencies, zero JVM stub crashes).
     3. Added Room Database v3 with automated migration `MIGRATION_2_3` (`ALTER TABLE timetable_entries ADD COLUMN customSilentSchedule TEXT DEFAULT NULL`).
     4. Updated `TimetableEngine.kt` to resolve active sub-class intervals, calculate interval boundary alarms, transition ringer state between intervals, and apply post-class end actions while preserving manual overrides.
     5. Added interactive interval builder and end-action selector in `AddEditClassDialog` with visual pills in `TimetableEntryCard`.

## Verification Results
- **Unit Tests:** `BUILD SUCCESSFUL` (47/47 unit tests passing, 0 failures).
- **Debug APK:** `BUILD SUCCESSFUL` (`app-debug.apk` built).
- **Release APK:** `BUILD SUCCESSFUL` (`app-release.apk` built with R8 minification and resource shrinking).

---

# 2026-10-19 — Master Bug Fix, UI/UX Redesign & Widget Upgrade (Phases 1 - 7)

## Problem Statements & Root Causes Discovered
1. **Background Execution / Battery Saver Requirement**:
   - *Problem:* Feature Readiness screen showed a red "Background Execution" card with an "Optimized" status and a "Configure" button, falsely presenting battery optimization exemption as mandatory.
   - *Root Cause:* The UI checked `PowerManager.isIgnoringBatteryOptimizations` and treated standard optimization as an error. ClassNow uses `AlarmManager.setExactAndAllowWhileIdle()` (`RTC_WAKEUP`), which Android Doze accommodates without exempting the entire app.
   - *Fix:* Completely removed the Background Execution card from `PermissionsReadinessDialog`. Eliminated any setup or automation blockage tied to battery optimization. Retained genuine permissions: DND Access, Exact Alarms, and Post Notifications.
2. **Widget Buttons Not Working on Xiaomi Phone**:
   - *Problem:* Home-screen widget displayed plain rectangular blocks with emojis (`🔕`, `🔔`). Tapping "Silent ON" and "Refresh" buttons was completely unresponsive.
   - *Root Causes:*
     1. The root `Box` in `ClassNowWidget.kt` had `.clickable(actionStartActivity<MainActivity>())`. On Xiaomi MIUI/HyperOS launcher, assigning a click action to the root `AppWidgetHostView` intercepted child touch events.
     2. Reflection-based `actionRunCallback` uses internal Glance receivers that can be blocked or stripped by R8 minification without explicit keep rules.
   - *Fix:*
     1. Removed the root `Box` click listener. Attached `actionStartActivity<MainActivity>()` strictly to the timetable content cards.
     2. Switched button actions to explicit `actionSendBroadcast` directly targeting `ClassNowWidgetReceiver` with `ACTION_REFRESH_WIDGET` and `ACTION_TOGGLE_SMART_SILENT`, processed asynchronously via `goAsync()`.
     3. Added ProGuard/R8 keep rules in `proguard-rules.pro` for Glance action callbacks and widget receiver.
     4. Removed all emojis from the widget buttons. Created clean vector drawables (`ic_volume_off`, `ic_volume_up`, `ic_refresh`).
     5. Added rounded styling (`cornerRadius(12.dp)`), balanced padding, and clear text labels (`Smart Silent: ON` / `Smart Silent: OFF`).
3. **Widget Sizing Limited on Xiaomi Launcher**:
   - *Problem:* Xiaomi launcher only exposed single-row (4x1) widget size.
   - *Root Cause:* `classnow_widget_info.xml` specified `minHeight="50dp"` and `minResizeHeight="50dp"`, which MIUI launcher interpreted as a single-row widget and locked out multi-row expansion.
   - *Fix:* Configured `minWidth="180dp"`, `minHeight="110dp"`, `minResizeWidth="110dp"`, `minResizeHeight="40dp"`, `targetCellWidth="4"`, `targetCellHeight="2"`, and `resizeMode="horizontal|vertical"`. Verified all 5 Glance layout tiers (Compact, Small, Medium, Large, Extra-Large) adapt cleanly without clipping.
4. **Manage Timetable Card Visuals**:
   - *Problem:* Cards had awkward nested gray rectangles and excessive empty space.
   - *Fix:* Redesigned `TimetableEntryCard` in `TimetableScreen.kt` using a single polished Material 3 surface with a vertical accent bar, 12-hour AM/PM time display, subtle silent rule pills, and accessible 48x48dp overflow touch targets.
5. **Manual 12-Hour Time Entry with AM/PM**:
   - *Problem:* Time input previously required using a clock dial exclusively and lacked manual typing with independent AM/PM selection.
   - *Fix:* Built `TimeInputField` and `TimeInputHelper`. Provides editable hour (1–12) and minute (00–59) text fields, independent AM/PM toggle button group, and a convenient clock icon button opening `M3TimePickerDialog`. Internal storage in 24-hour "HH:mm" is cleanly maintained, preserving full database and CSV compatibility. Added comprehensive unit tests in `TimeInputHelperTest.kt`.

## Verification Results
- **Unit Tests:** `BUILD SUCCESSFUL` (40/40 unit tests passing, 0 failures).
- **Debug APK:** `BUILD SUCCESSFUL` (`app-debug.apk` built).
- **Release APK:** `BUILD SUCCESSFUL` (`app-release.apk` built with R8 minification and resource shrinking).

---

# 2026-10-19 — Master Development Upgrade (Phases 1 - 10)

## Implemented Work
1. **Responsive 5-Tier Widget System (Phase 1)**:
   - Configured `classnow_widget_info.xml` with `minResizeHeight="50dp"`, `minResizeWidth="110dp"`, and `targetCellWidth="4"` to support 2x1, 2x2, 4x2, 4x3, 4x4, and 4x5 grid sizes across launchers.
   - Built 5 dedicated responsive layouts in `ClassNowWidget.kt`: Compact, Small Square (2x2), Medium Horizontal (4x2), Large (4x3/4x4), and Extra-Large (4x5/Expanded).
2. **App and Widget Theme Synchronization (Phase 2)**:
   - Built `WidgetTheme.kt` with explicit design tokens matching the app's Aqua/Cyan brand identity.
   - Handled System, Light, and Dark modes across `HomeScreen`, `TimetableScreen`, `PermissionsReadinessDialog`, and `ClassNowWidget`.
3. **Widget Refresh Button Redesign (Phase 3)**:
   - Replaced ambiguous touch targets with an isolated button container.
4. **Smart Silent Mode Widget Control (Phase 4)**:
   - Added direct interactive toggle inside the widget using `ToggleSilentActionCallback`.
5. **First-Run Permission and Onboarding Experience (Phase 5)**:
   - Built `PermissionsReadinessDialog.kt` and `util/PermissionHelper.kt`.
6. **Smart Silent Mode Reliability & Per-Class Rules (Phase 6)**:
   - Supported rules: `RULE_DEFAULT`, `RULE_FORCE_SILENT`, `RULE_FORCE_SOUND`, `RULE_IGNORE`.
7. **Expanded Unit Test Suite (Phase 9)**:
   - Total test suite expanded to 34 unit tests: 34/34 PASS.
8. **Build & Quality Verification**:
   - `.\gradlew test --no-daemon`: PASS.
   - `.\gradlew assembleDebug --no-daemon`: PASS.
   - `.\gradlew assembleRelease --no-daemon`: PASS.
