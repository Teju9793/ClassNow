package com.teju.classnow.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.teju.classnow.MainActivity
import com.teju.classnow.R
import com.teju.classnow.data.local.AppDatabase
import com.teju.classnow.data.local.SettingsRepository
import com.teju.classnow.domain.timetable.ScheduleState
import com.teju.classnow.domain.timetable.TimetableEngine
import com.teju.classnow.domain.timetable.TimetableStatus
import com.teju.classnow.util.SmartSilentManager
import com.teju.classnow.util.TimeUtils
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTimeTextStyle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import java.util.Locale

class ClassNowWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val dao = AppDatabase.getDatabase(appContext).timetableDao()
        val settings = SettingsRepository(appContext)

        // Snapshot initial values for instant cold-start rendering without flicker
        val initialSilent = settings.isSmartSilentModeEnabled.first()
        val initialOverride = settings.manualOverridePeriod.first()
        val initialTheme = settings.themeMode.first()
        val initialDay = LocalDate.now().dayOfWeek.getDisplayName(DateTimeTextStyle.FULL, Locale.US)
        val initialEntries = dao.getEntriesForDayList(initialDay)
        val initialRefreshTrigger = settings.widgetRefreshTrigger.first()

        provideContent {
            // Observes authoritative refresh events from receiver, alarms, mutations, and user taps
            val refreshTrigger by settings.widgetRefreshTrigger.collectAsState(initial = initialRefreshTrigger)
            val currentDay = LocalDate.now().dayOfWeek.getDisplayName(DateTimeTextStyle.FULL, Locale.US)
            val isSmartSilentEnabled by settings.isSmartSilentModeEnabled.collectAsState(initial = initialSilent)
            val manualOverridePeriod by settings.manualOverridePeriod.collectAsState(initial = initialOverride)
            val themeMode by settings.themeMode.collectAsState(initial = initialTheme)
            val entries by dao.getEntriesForDay(currentDay).collectAsState(initial = initialEntries)

            // Dynamic schedule state recalculation on every composition pass (never cached with stale remember keys)
            val scheduleState = TimetableEngine.getScheduleState(
                currentTime = LocalTime.now(),
                entries = entries,
                smartSilentMasterEnabled = isSmartSilentEnabled,
                manualOverridePeriodKey = manualOverridePeriod
            )

            // Dynamic theme and update timestamp resolution
            val colors = WidgetTheme.getColors(appContext, themeMode)
            val lastUpdatedTime = TimeUtils.formatLocalTimeTo12Hour(LocalTime.now())

            GlanceTheme {
                WidgetRoot(
                    state = scheduleState,
                    colors = colors,
                    isSmartSilentEnabled = isSmartSilentEnabled,
                    lastUpdatedTime = lastUpdatedTime
                )
            }
        }
    }

    @Composable
    private fun WidgetRoot(
        state: ScheduleState,
        colors: WidgetColors,
        isSmartSilentEnabled: Boolean,
        lastUpdatedTime: String
    ) {
        val size = LocalSize.current
        val width = size.width
        val height = size.height

        // 5 Layout Categories based on real launcher bounds:
        // 1. Compact: height < 95.dp (e.g. 2x1, 4x1)
        // 2. Small: narrow width < 220.dp (e.g. 2x2, 2x3)
        // 3. Medium: horizontal banner height < 170.dp (e.g. 4x2)
        // 4. Large: standard card height < 270.dp (e.g. 4x3, 4x4)
        // 5. Extra-Large: height >= 270.dp (e.g. 4x4+, expanded dashboard)
        val isWide = width >= 220.dp
        val isTall = height >= 170.dp

        android.util.Log.d(
            "ClassNowWidget",
            "WidgetRoot size: ${width.value}x${height.value}dp -> isWide=$isWide, isTall=$isTall"
        )

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .background(colors.background)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            when {
                height < 55.dp -> {
                    CompactLayout(state, colors, isSmartSilentEnabled, width, lastUpdatedTime)
                }
                !isWide -> {
                    SmallLayout(state, colors, isSmartSilentEnabled, lastUpdatedTime)
                }
                else -> {
                    HorizontalDualCardLayout(
                        state = state,
                        colors = colors,
                        isSmartSilentEnabled = isSmartSilentEnabled,
                        lastUpdatedTime = lastUpdatedTime,
                        isTall = isTall
                    )
                }
            }
        }
    }

    private fun calculateMinutesUntil(timeStr: String?): Long? {
        if (timeStr.isNullOrBlank()) return null
        return try {
            val target = LocalTime.parse(timeStr.trim(), DateTimeFormatter.ofPattern("HH:mm", Locale.US))
            val now = LocalTime.now()
            Duration.between(now, target).toMinutes().coerceAtLeast(0)
        } catch (e: Exception) {
            null
        }
    }

    // -------------------------------------------------------------
    // 1. COMPACT LAYOUT (1-row emergency ribbon — height < 55.dp)
    // -------------------------------------------------------------
    @Composable
    private fun CompactLayout(
        state: ScheduleState,
        colors: WidgetColors,
        isSmartSilentEnabled: Boolean,
        width: androidx.compose.ui.unit.Dp,
        lastUpdatedTime: String
    ) {
        val isWide = width >= 250.dp

        Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Main clickable info area (tapping opens app)
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity<MainActivity>())
            ) {
                val title = when (state.status) {
                    TimetableStatus.CURRENT_CLASS -> state.currentClass?.subject ?: "Class"
                    TimetableStatus.BREAK -> "Short Break"
                    TimetableStatus.LUNCH -> "Lunch Break"
                    TimetableStatus.FREE_PERIOD -> "Free Period"
                    TimetableStatus.BEFORE_CLASSES -> state.nextClass?.let { "Next: ${it.subject}" } ?: "Before Classes"
                    TimetableStatus.NO_MORE_CLASSES -> "Done for Today"
                    TimetableStatus.NO_CLASSES_TODAY -> "No Classes"
                }
                val nextSnippet = state.nextClass?.let { " • Next: ${it.subject}" } ?: ""

                Text(
                    text = title,
                    style = TextStyle(color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )

                if (state.status == TimetableStatus.CURRENT_CLASS) {
                    Text(
                        text = "${state.progressPercentage}% • ${state.timeSummary}$nextSnippet • $lastUpdatedTime",
                        style = TextStyle(color = colors.primaryCyan, fontSize = 9.sp),
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = "${state.timeSummary}$nextSnippet • $lastUpdatedTime",
                        style = TextStyle(color = colors.textSecondary, fontSize = 9.sp),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(6.dp))

            // On wider horizontal sizes (e.g. 4x1), show both Silent and Refresh
            if (isWide) {
                WidgetSilentToggleButton(
                    isSmartSilentEnabled = isSmartSilentEnabled,
                    colors = colors,
                    isCompact = true,
                    heightDp = 26
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
            }

            WidgetRefreshButton(colors = colors, sizeDp = 26)
        }
    }

    // -------------------------------------------------------------
    // 2. SMALL SQUARE LAYOUT (2x2 — width < 220.dp, height >= 95.dp)
    // -------------------------------------------------------------
    @Composable
    private fun SmallLayout(
        state: ScheduleState,
        colors: WidgetColors,
        isSmartSilentEnabled: Boolean,
        lastUpdatedTime: String
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(2.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Header Row: Brand, Updated Time, Silent Toggle, Refresh
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "ClassNow",
                        style = TextStyle(color = colors.primaryCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Upd: $lastUpdatedTime",
                        style = TextStyle(color = colors.textSecondary, fontSize = 8.sp)
                    )
                }

                WidgetSilentToggleButton(
                    isSmartSilentEnabled = isSmartSilentEnabled,
                    colors = colors,
                    isCompact = true,
                    heightDp = 24
                )

                Spacer(modifier = GlanceModifier.width(4.dp))

                WidgetRefreshButton(colors = colors, sizeDp = 24)
            }

            Spacer(modifier = GlanceModifier.height(3.dp))

            // Card 1: CURRENT / ACTIVE CLASS (Clickable to open app)
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(8.dp)
                    .background(colors.currentCardBg)
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                when (state.status) {
                    TimetableStatus.CURRENT_CLASS -> {
                        val current = state.currentClass
                        Column(modifier = GlanceModifier.fillMaxSize()) {
                            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "CURRENT",
                                    style = TextStyle(color = colors.currentAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                    modifier = GlanceModifier.defaultWeight()
                                )
                                Text(
                                    text = "${TimeUtils.formatTo12Hour(current?.startTime)} – ${TimeUtils.formatTo12Hour(current?.endTime)}",
                                    style = TextStyle(color = colors.textSecondary, fontSize = 8.sp)
                                )
                            }
                            Text(
                                text = current?.subject ?: "Class",
                                style = TextStyle(color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            LinearProgressIndicator(
                                progress = state.progressFraction,
                                modifier = GlanceModifier.fillMaxWidth().height(3.dp),
                                color = colors.currentAccent,
                                backgroundColor = colors.progressTrack
                            )
                            Spacer(modifier = GlanceModifier.height(1.dp))
                            Text(
                                text = "${state.progressPercentage}% • ${state.timeSummary}",
                                style = TextStyle(color = colors.currentAccent, fontSize = 8.sp, fontWeight = FontWeight.Medium),
                                maxLines = 1
                            )
                        }
                    }
                    TimetableStatus.BREAK,
                    TimetableStatus.LUNCH,
                    TimetableStatus.FREE_PERIOD -> {
                        Column(modifier = GlanceModifier.fillMaxSize()) {
                            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = state.statusSummary.uppercase(),
                                    style = TextStyle(color = colors.currentAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                    modifier = GlanceModifier.defaultWeight()
                                )
                                state.nextClass?.let { next ->
                                    Text(
                                        text = "Next: ${TimeUtils.formatTo12Hour(next.startTime)}",
                                        style = TextStyle(color = colors.textSecondary, fontSize = 8.sp)
                                    )
                                }
                            }
                            Text(
                                text = state.statusSummary,
                                style = TextStyle(color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            if (state.totalMinutes > 0) {
                                LinearProgressIndicator(
                                    progress = state.progressFraction,
                                    modifier = GlanceModifier.fillMaxWidth().height(3.dp),
                                    color = colors.currentAccent,
                                    backgroundColor = colors.progressTrack
                                )
                                Spacer(modifier = GlanceModifier.height(1.dp))
                            }
                            Text(
                                text = "${state.progressPercentage}% • ${state.timeSummary}",
                                style = TextStyle(color = colors.currentAccent, fontSize = 8.sp, fontWeight = FontWeight.Medium),
                                maxLines = 1
                            )
                        }
                    }
                    TimetableStatus.BEFORE_CLASSES -> {
                        Column(modifier = GlanceModifier.fillMaxSize()) {
                            Text(
                                text = "COLLEGE SCHEDULE",
                                style = TextStyle(color = colors.currentAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Before Classes",
                                style = TextStyle(color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Text(
                                text = state.timeSummary,
                                style = TextStyle(color = colors.textSecondary, fontSize = 9.sp),
                                maxLines = 1
                            )
                        }
                    }
                    TimetableStatus.NO_MORE_CLASSES -> {
                        Column(modifier = GlanceModifier.fillMaxSize()) {
                            Text(
                                text = "CLASSES COMPLETED",
                                style = TextStyle(color = colors.currentAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "All Done For Today",
                                style = TextStyle(color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Text(
                                text = "See you tomorrow!",
                                style = TextStyle(color = colors.textSecondary, fontSize = 9.sp),
                                maxLines = 1
                            )
                        }
                    }
                    TimetableStatus.NO_CLASSES_TODAY -> {
                        Column(modifier = GlanceModifier.fillMaxSize()) {
                            Text(
                                text = "TIMETABLE",
                                style = TextStyle(color = colors.currentAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "No Classes Today",
                                style = TextStyle(color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Text(
                                text = "Enjoy your day off",
                                style = TextStyle(color = colors.textSecondary, fontSize = 9.sp),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(3.dp))

            // Card 2: NEXT CLASS (Clickable to open app)
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(8.dp)
                    .background(colors.nextCardBg)
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                if (state.nextClass != null) {
                    val next = state.nextClass
                    val timeSpan = "${TimeUtils.formatTo12Hour(next.startTime)} – ${TimeUtils.formatTo12Hour(next.endTime)}"
                    val countdown = when {
                        state.status != TimetableStatus.CURRENT_CLASS && state.remainingMinutes > 0 -> {
                            if (state.remainingMinutes <= 1) " • in <1m" else " • in ${state.remainingMinutes}m"
                        }
                        state.status == TimetableStatus.CURRENT_CLASS -> {
                            val mins = calculateMinutesUntil(next.startTime)
                            if (mins != null) {
                                if (mins <= 1) " • in <1m" else " • in ${mins}m"
                            } else {
                                " • next"
                            }
                        }
                        else -> ""
                    }
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "NEXT",
                            style = TextStyle(color = colors.nextAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(
                            text = next.subject,
                            style = TextStyle(color = colors.textPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            modifier = GlanceModifier.defaultWeight(),
                            maxLines = 1
                        )
                        Spacer(modifier = GlanceModifier.width(2.dp))
                        Text(
                            text = "$timeSpan$countdown",
                            style = TextStyle(color = colors.nextAccent, fontSize = 8.sp, fontWeight = FontWeight.Medium),
                            maxLines = 1
                        )
                    }
                } else {
                    val message = when (state.status) {
                        TimetableStatus.CURRENT_CLASS -> "Last class of the day • Done after this"
                        TimetableStatus.NO_MORE_CLASSES -> "No further classes today"
                        TimetableStatus.NO_CLASSES_TODAY -> "No classes scheduled today"
                        else -> "No further classes today"
                    }
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "NEXT",
                            style = TextStyle(color = colors.nextAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(
                            text = message,
                            style = TextStyle(color = colors.textSecondary, fontSize = 9.sp),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------
    // 3. WIDE HORIZONTAL DUAL-CARD LAYOUT (Primary Layout for all wide sizes)
    // -------------------------------------------------------------
    @Composable
    private fun MediumLayout(
        state: ScheduleState,
        colors: WidgetColors,
        isSmartSilentEnabled: Boolean,
        lastUpdatedTime: String
    ) {
        HorizontalDualCardLayout(state, colors, isSmartSilentEnabled, lastUpdatedTime, isTall = false)
    }

    @Composable
    private fun HorizontalDualCardLayout(
        state: ScheduleState,
        colors: WidgetColors,
        isSmartSilentEnabled: Boolean,
        lastUpdatedTime: String,
        isTall: Boolean = false
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 4.dp, vertical = if (isTall) 4.dp else 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Header Row: Brand, Updated time, Silent Toggle, Refresh
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "ClassNow",
                        style = TextStyle(
                            color = colors.primaryCyan,
                            fontSize = if (isTall) 14.sp else 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Updated: $lastUpdatedTime",
                        style = TextStyle(
                            color = colors.textSecondary,
                            fontSize = if (isTall) 9.sp else 8.sp
                        )
                    )
                }

                WidgetSilentToggleButton(
                    isSmartSilentEnabled = isSmartSilentEnabled,
                    colors = colors,
                    isCompact = !isTall,
                    heightDp = if (isTall) 28 else 24
                )

                Spacer(modifier = GlanceModifier.width(if (isTall) 8.dp else 6.dp))

                WidgetRefreshButton(colors = colors, sizeDp = if (isTall) 28 else 24)
            }

            Spacer(modifier = GlanceModifier.height(if (isTall) 8.dp else 4.dp))

            // Content row — two cards side by side (LEFT: CURRENT, RIGHT: NEXT)
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CurrentClassHorizontalCard(
                    state = state,
                    colors = colors,
                    isTall = isTall,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                )

                Spacer(modifier = GlanceModifier.width(if (isTall) 8.dp else 6.dp))

                NextClassHorizontalCard(
                    state = state,
                    colors = colors,
                    isTall = isTall,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                )
            }
        }
    }

    @Composable
    private fun CurrentClassHorizontalCard(
        state: ScheduleState,
        colors: WidgetColors,
        isTall: Boolean = false,
        modifier: GlanceModifier
    ) {
        Box(
            modifier = modifier
                .cornerRadius(10.dp)
                .background(colors.currentCardBg)
                .clickable(actionStartActivity<MainActivity>())
                .padding(horizontal = if (isTall) 9.dp else 7.dp, vertical = if (isTall) 7.dp else 5.dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Top
            ) {
                // Card Top Row: Tag (Coral Pink) + Time Range
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tag = when (state.status) {
                        TimetableStatus.CURRENT_CLASS -> "CURRENT CLASS"
                        TimetableStatus.BREAK -> "BREAK"
                        TimetableStatus.LUNCH -> "LUNCH"
                        TimetableStatus.FREE_PERIOD -> "FREE PERIOD"
                        TimetableStatus.BEFORE_CLASSES -> "COLLEGE SCHEDULE"
                        TimetableStatus.NO_MORE_CLASSES -> "COMPLETED"
                        TimetableStatus.NO_CLASSES_TODAY -> "TIMETABLE"
                    }
                    Text(
                        text = tag,
                        style = TextStyle(
                            color = colors.currentAccent,
                            fontSize = if (isTall) 9.sp else 8.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )

                    val timeRange = when (state.status) {
                        TimetableStatus.CURRENT_CLASS -> {
                            state.currentClass?.let {
                                "${TimeUtils.formatTo12Hour(it.startTime)} - ${TimeUtils.formatTo12Hour(it.endTime)}"
                            } ?: ""
                        }
                        TimetableStatus.BREAK, TimetableStatus.LUNCH, TimetableStatus.FREE_PERIOD -> {
                            if (state.remainingMinutes > 0) "${state.remainingMinutes}m left" else ""
                        }
                        else -> ""
                    }
                    if (timeRange.isNotBlank()) {
                        Text(
                            text = timeRange,
                            style = TextStyle(
                                color = colors.textSecondary,
                                fontSize = if (isTall) 9.sp else 8.sp
                            ),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(if (isTall) 3.dp else 2.dp))

                // Card Main Title (Subject or Status)
                val mainTitle = when (state.status) {
                    TimetableStatus.CURRENT_CLASS -> state.currentClass?.subject ?: "Class"
                    TimetableStatus.BREAK -> "Short Break"
                    TimetableStatus.LUNCH -> "Lunch Break"
                    TimetableStatus.FREE_PERIOD -> "Free Period"
                    TimetableStatus.BEFORE_CLASSES -> "Before Classes"
                    TimetableStatus.NO_MORE_CLASSES -> "Classes Completed"
                    TimetableStatus.NO_CLASSES_TODAY -> "No Classes Today"
                }

                Text(
                    text = mainTitle,
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = if (isTall) 13.sp else 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )

                // Subtitle / Progress / Description
                when (state.status) {
                    TimetableStatus.CURRENT_CLASS -> {
                        Spacer(modifier = GlanceModifier.height(if (isTall) 4.dp else 3.dp))
                        LinearProgressIndicator(
                            progress = state.progressFraction,
                            modifier = GlanceModifier.fillMaxWidth().height(if (isTall) 4.dp else 3.dp),
                            color = colors.currentAccent,
                            backgroundColor = colors.progressTrack
                        )
                        Spacer(modifier = GlanceModifier.height(if (isTall) 3.dp else 2.dp))
                        Text(
                            text = "${state.progressPercentage}% • ${state.timeSummary}",
                            style = TextStyle(
                                color = colors.currentAccent,
                                fontSize = if (isTall) 9.sp else 8.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                    }
                    TimetableStatus.BREAK, TimetableStatus.LUNCH, TimetableStatus.FREE_PERIOD -> {
                        Spacer(modifier = GlanceModifier.height(if (isTall) 4.dp else 3.dp))
                        if (state.totalMinutes > 0) {
                            LinearProgressIndicator(
                                progress = state.progressFraction,
                                modifier = GlanceModifier.fillMaxWidth().height(if (isTall) 4.dp else 3.dp),
                                color = colors.currentAccent,
                                backgroundColor = colors.progressTrack
                            )
                            Spacer(modifier = GlanceModifier.height(if (isTall) 3.dp else 2.dp))
                        }
                        Text(
                            text = if (state.totalMinutes > 0) "${state.progressPercentage}% • ${state.timeSummary}" else state.timeSummary,
                            style = TextStyle(
                                color = colors.currentAccent,
                                fontSize = if (isTall) 9.sp else 8.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                    }
                    TimetableStatus.BEFORE_CLASSES -> {
                        Spacer(modifier = GlanceModifier.height(if (isTall) 4.dp else 3.dp))
                        Text(
                            text = "Starts at ${state.nextClass?.let { TimeUtils.formatTo12Hour(it.startTime) } ?: "first period"}",
                            style = TextStyle(
                                color = colors.textSecondary,
                                fontSize = if (isTall) 9.sp else 8.sp
                            ),
                            maxLines = 1
                        )
                    }
                    TimetableStatus.NO_MORE_CLASSES -> {
                        Spacer(modifier = GlanceModifier.height(if (isTall) 4.dp else 3.dp))
                        Text(
                            text = "All done for today! See you tomorrow.",
                            style = TextStyle(
                                color = colors.textSecondary,
                                fontSize = if (isTall) 9.sp else 8.sp
                            ),
                            maxLines = 2
                        )
                    }
                    TimetableStatus.NO_CLASSES_TODAY -> {
                        Spacer(modifier = GlanceModifier.height(if (isTall) 4.dp else 3.dp))
                        Text(
                            text = "Enjoy your day off • Tap to manage",
                            style = TextStyle(
                                color = colors.textSecondary,
                                fontSize = if (isTall) 9.sp else 8.sp
                            ),
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun NextClassHorizontalCard(
        state: ScheduleState,
        colors: WidgetColors,
        isTall: Boolean = false,
        modifier: GlanceModifier
    ) {
        Box(
            modifier = modifier
                .cornerRadius(10.dp)
                .background(colors.nextCardBg)
                .clickable(actionStartActivity<MainActivity>())
                .padding(horizontal = if (isTall) 9.dp else 7.dp, vertical = if (isTall) 7.dp else 5.dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Top
            ) {
                // Card Top Row: Tag (Neon Aqua) + Countdown / Status
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NEXT CLASS",
                        style = TextStyle(
                            color = colors.nextAccent,
                            fontSize = if (isTall) 9.sp else 8.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )

                    if (state.nextClass != null) {
                        val badge = when {
                            state.status != TimetableStatus.CURRENT_CLASS && state.remainingMinutes > 0 -> {
                                if (state.remainingMinutes <= 1) "in <1m" else "in ${state.remainingMinutes}m"
                            }
                            state.status == TimetableStatus.CURRENT_CLASS -> {
                                val mins = calculateMinutesUntil(state.nextClass.startTime)
                                if (mins != null) {
                                    if (mins <= 1) "in <1m" else "in ${mins}m"
                                } else "next"
                            }
                            else -> ""
                        }
                        if (badge.isNotBlank()) {
                            Text(
                                text = badge,
                                style = TextStyle(
                                    color = colors.nextAccent,
                                    fontSize = if (isTall) 9.sp else 8.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.height(if (isTall) 3.dp else 2.dp))

                if (state.nextClass != null) {
                    val next = state.nextClass
                    // Subject Name (truncated to 1 line gracefully)
                    Text(
                        text = next.subject,
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = if (isTall) 13.sp else 11.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )

                    Spacer(modifier = GlanceModifier.height(if (isTall) 3.dp else 2.dp))

                    // Time Range
                    Text(
                        text = "${TimeUtils.formatTo12Hour(next.startTime)} – ${TimeUtils.formatTo12Hour(next.endTime)}",
                        style = TextStyle(
                            color = colors.textSecondary,
                            fontSize = if (isTall) 9.sp else 8.sp
                        ),
                        maxLines = 1
                    )

                    Spacer(modifier = GlanceModifier.height(if (isTall) 3.dp else 2.dp))

                    // Detail: Starts in X min / Starts after current class
                    val detail = when {
                        state.status == TimetableStatus.CURRENT_CLASS -> {
                            val mins = calculateMinutesUntil(next.startTime)
                            if (mins != null && mins > 0) "Starts in ${mins} min" else "Starts after current class"
                        }
                        state.remainingMinutes > 0 -> "Starts in ${state.remainingMinutes} min"
                        else -> "Upcoming next"
                    }
                    Text(
                        text = detail,
                        style = TextStyle(
                            color = colors.nextAccent,
                            fontSize = if (isTall) 9.sp else 8.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                } else {
                    // Empty state when nextClass == null
                    val emptyTitle = when (state.status) {
                        TimetableStatus.CURRENT_CLASS -> "Final Class"
                        TimetableStatus.NO_MORE_CLASSES -> "All Done"
                        TimetableStatus.NO_CLASSES_TODAY -> "No Classes"
                        else -> "No Classes"
                    }
                    val emptySubtitle = when (state.status) {
                        TimetableStatus.CURRENT_CLASS -> "Done after this class"
                        TimetableStatus.NO_MORE_CLASSES -> "No further classes today"
                        TimetableStatus.NO_CLASSES_TODAY -> "No classes scheduled today"
                        else -> "No further classes today"
                    }

                    Text(
                        text = emptyTitle,
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = if (isTall) 13.sp else 11.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )

                    Spacer(modifier = GlanceModifier.height(if (isTall) 3.dp else 2.dp))

                    Text(
                        text = emptySubtitle,
                        style = TextStyle(
                            color = colors.textSecondary,
                            fontSize = if (isTall) 9.sp else 8.sp
                        ),
                        maxLines = 2
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------
    // 4. LARGE LAYOUT (Delegates to HorizontalDualCardLayout)
    // -------------------------------------------------------------
    @Composable
    private fun LargeLayout(
        state: ScheduleState,
        colors: WidgetColors,
        isSmartSilentEnabled: Boolean,
        lastUpdatedTime: String
    ) {
        HorizontalDualCardLayout(state, colors, isSmartSilentEnabled, lastUpdatedTime, isTall = true)
    }

    // -------------------------------------------------------------
    // 5. EXTRA-LARGE EXPANDED LAYOUT (Delegates to HorizontalDualCardLayout)
    // -------------------------------------------------------------
    @Composable
    private fun ExtraLargeLayout(
        state: ScheduleState,
        colors: WidgetColors,
        isSmartSilentEnabled: Boolean,
        lastUpdatedTime: String
    ) {
        HorizontalDualCardLayout(state, colors, isSmartSilentEnabled, lastUpdatedTime, isTall = true)
    }

    // -------------------------------------------------------------
    // SHARED REUSABLE COMPONENTS
    // -------------------------------------------------------------

    /**
     * Dedicated refresh icon button with rounded corners, vector icon,
     * and an explicit broadcast trigger to ClassNowWidgetReceiver.
     */
    @Composable
    private fun WidgetRefreshButton(colors: WidgetColors, sizeDp: Int = 26) {
        val context = LocalContext.current
        val refreshIntent = Intent(context, ClassNowWidgetReceiver::class.java).apply {
            action = ClassNowWidgetReceiver.ACTION_REFRESH_WIDGET
            `package` = context.packageName
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        val iconSize = ((sizeDp * 0.6).toInt().coerceAtLeast(12)).dp

        Box(
            modifier = GlanceModifier
                .size(sizeDp.dp)
                .cornerRadius((sizeDp / 3).coerceAtLeast(6).dp)
                .background(colors.buttonBackground)
                .clickable(actionSendBroadcast(refreshIntent)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Refresh Timetable",
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(colors.buttonContent)
            )
        }
    }

    /**
     * Dedicated Smart Silent Mode control with rounded corners, vector icon,
     * clear "Smart Silent: ON/OFF" text, and an explicit broadcast trigger.
     */
    @Composable
    private fun WidgetSilentToggleButton(
        isSmartSilentEnabled: Boolean,
        colors: WidgetColors,
        isCompact: Boolean = false,
        heightDp: Int = 26
    ) {
        val context = LocalContext.current
        val toggleIntent = Intent(context, ClassNowWidgetReceiver::class.java).apply {
            action = ClassNowWidgetReceiver.ACTION_TOGGLE_SMART_SILENT
            `package` = context.packageName
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        val hasDnd = SmartSilentManager.hasDndPermission(context)
        val bg = if (isSmartSilentEnabled) {
            if (hasDnd) colors.silentOnBackground else colors.warningBackground
        } else colors.silentOffBackground
        val textCol = if (isSmartSilentEnabled) {
            if (hasDnd) colors.silentOnContent else colors.warningContent
        } else colors.silentOffContent
        val iconRes = if (isSmartSilentEnabled) R.drawable.ic_volume_off else R.drawable.ic_volume_up
        val label = if (isSmartSilentEnabled) {
            if (!hasDnd) {
                if (isCompact) "Silent: NO DND" else "Smart Silent: NO DND"
            } else {
                if (isCompact) "Silent: ON" else "Smart Silent: ON"
            }
        } else {
            if (isCompact) "Silent: OFF" else "Smart Silent: OFF"
        }

        Box(
            modifier = GlanceModifier
                .height(heightDp.dp)
                .cornerRadius((heightDp / 2).dp)
                .background(bg)
                .clickable(actionSendBroadcast(toggleIntent))
                .padding(horizontal = if (isCompact) 8.dp else 10.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = null,
                    modifier = GlanceModifier.size(if (heightDp <= 28) 14.dp else 18.dp),
                    colorFilter = ColorFilter.tint(textCol)
                )
                Spacer(modifier = GlanceModifier.width(if (heightDp <= 28) 4.dp else 6.dp))
                Text(
                    text = label,
                    style = TextStyle(
                        color = textCol,
                        fontSize = if (heightDp <= 28) 10.sp else 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

// -------------------------------------------------------------
// ACTION CALLBACKS (Retained for programmatic or test invocation)
// -------------------------------------------------------------

class RefreshCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetUpdateHelper.triggerUpdate(context)
        ClassNowWidgetReceiver.showToast(context, "Timetable refreshed")
    }
}

class ToggleSilentActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        ClassNowWidgetReceiver.executeMasterToggle(context)
    }
}
