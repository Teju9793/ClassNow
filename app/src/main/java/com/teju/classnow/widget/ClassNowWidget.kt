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
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle as DateTimeTextStyle
import java.util.Locale

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

class ClassNowWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val dao = AppDatabase.getDatabase(appContext).timetableDao()
        val settings = SettingsRepository(appContext)
        val currentDay = LocalDate.now().dayOfWeek.getDisplayName(DateTimeTextStyle.FULL, Locale.US)

        // Snapshot initial values for instant cold-start rendering without flicker
        val initialSilent = settings.isSmartSilentModeEnabled.first()
        val initialOverride = settings.manualOverridePeriod.first()
        val initialTheme = settings.themeMode.first()
        val initialEntries = dao.getEntriesForDayList(currentDay)

        provideContent {
            val isSmartSilentEnabled by settings.isSmartSilentModeEnabled.collectAsState(initial = initialSilent)
            val manualOverridePeriod by settings.manualOverridePeriod.collectAsState(initial = initialOverride)
            val themeMode by settings.themeMode.collectAsState(initial = initialTheme)
            val entries by dao.getEntriesForDay(currentDay).collectAsState(initial = initialEntries)

            val scheduleState = remember(entries, isSmartSilentEnabled, manualOverridePeriod) {
                TimetableEngine.getScheduleState(
                    currentTime = LocalTime.now(),
                    entries = entries,
                    smartSilentMasterEnabled = isSmartSilentEnabled,
                    manualOverridePeriodKey = manualOverridePeriod
                )
            }

            val colors = remember(themeMode) {
                WidgetTheme.getColors(appContext, themeMode)
            }

            val lastUpdatedTime = remember(scheduleState) {
                TimeUtils.formatLocalTimeTo12Hour(LocalTime.now())
            }

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
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .background(colors.background)
                .padding(8.dp)
        ) {
            when {
                height < 95.dp -> {
                    CompactLayout(state, colors, isSmartSilentEnabled, width, lastUpdatedTime)
                }
                width < 220.dp -> {
                    SmallLayout(state, colors, isSmartSilentEnabled, lastUpdatedTime)
                }
                height < 170.dp -> {
                    MediumLayout(state, colors, isSmartSilentEnabled, lastUpdatedTime)
                }
                height < 270.dp -> {
                    LargeLayout(state, colors, isSmartSilentEnabled, lastUpdatedTime)
                }
                else -> {
                    ExtraLargeLayout(state, colors, isSmartSilentEnabled, lastUpdatedTime)
                }
            }
        }
    }

    // -------------------------------------------------------------
    // 1. COMPACT LAYOUT (2x1, 4x1 — height < 95.dp)
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

                Text(
                    text = title,
                    style = TextStyle(color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )

                if (state.status == TimetableStatus.CURRENT_CLASS) {
                    Text(
                        text = "${state.progressPercentage}% • ${state.timeSummary} • $lastUpdatedTime",
                        style = TextStyle(color = colors.primaryCyan, fontSize = 10.sp),
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = "${state.timeSummary} • $lastUpdatedTime",
                        style = TextStyle(color = colors.textSecondary, fontSize = 10.sp),
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
                    isCompact = true
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
            }

            WidgetRefreshButton(colors = colors, sizeDp = 48)
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
            // Header Row: Brand and Refresh
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "ClassNow",
                        style = TextStyle(color = colors.primaryCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Upd: $lastUpdatedTime",
                        style = TextStyle(color = colors.textSecondary, fontSize = 9.sp)
                    )
                }

                WidgetRefreshButton(colors = colors, sizeDp = 48)
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Main Content Area: clickable to open app
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .clickable(actionStartActivity<MainActivity>())
            ) {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    when (state.status) {
                        TimetableStatus.CURRENT_CLASS -> {
                            val current = state.currentClass
                            Text(
                                text = "ACTIVE CLASS",
                                style = TextStyle(color = colors.cyanAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = current?.subject ?: "Class",
                                style = TextStyle(color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            Text(
                                text = "${TimeUtils.formatTo12Hour(current?.startTime)} - ${TimeUtils.formatTo12Hour(current?.endTime)}",
                                style = TextStyle(color = colors.textSecondary, fontSize = 10.sp)
                            )
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = state.progressFraction,
                                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                                color = colors.primaryCyan,
                                backgroundColor = colors.progressTrack
                            )
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Text(
                                text = "${state.progressPercentage}% • ${state.timeSummary}",
                                style = TextStyle(color = colors.primaryCyan, fontSize = 9.sp)
                            )
                        }
                        TimetableStatus.BREAK,
                        TimetableStatus.LUNCH,
                        TimetableStatus.FREE_PERIOD -> {
                            Text(
                                text = state.statusSummary.uppercase(),
                                style = TextStyle(color = colors.cyanAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = state.nextClass?.let { "Next: ${it.subject}" } ?: "Break in Session",
                                style = TextStyle(color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            Text(
                                text = state.timeSummary,
                                style = TextStyle(color = colors.textSecondary, fontSize = 10.sp)
                            )
                        }
                        TimetableStatus.BEFORE_CLASSES -> {
                            Text(
                                text = "BEFORE COLLEGE",
                                style = TextStyle(color = colors.cyanAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = state.nextClass?.let { "First: ${it.subject}" } ?: "Classes Today",
                                style = TextStyle(color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            Text(
                                text = state.timeSummary,
                                style = TextStyle(color = colors.textSecondary, fontSize = 10.sp)
                            )
                        }
                        TimetableStatus.NO_MORE_CLASSES -> {
                            Text(
                                text = "DONE FOR TODAY",
                                style = TextStyle(color = colors.cyanAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Classes Finished",
                                style = TextStyle(color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Relax & Recharge",
                                style = TextStyle(color = colors.textSecondary, fontSize = 10.sp)
                            )
                        }
                        TimetableStatus.NO_CLASSES_TODAY -> {
                            Text(
                                text = "NO CLASSES",
                                style = TextStyle(color = colors.cyanAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Day Off",
                                style = TextStyle(color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Nothing scheduled",
                                style = TextStyle(color = colors.textSecondary, fontSize = 10.sp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Dedicated Smart Silent Toggle at bottom of small widget
            WidgetSilentToggleButton(
                isSmartSilentEnabled = isSmartSilentEnabled,
                colors = colors,
                isCompact = true
            )
        }
    }

    // -------------------------------------------------------------
    // 3. MEDIUM HORIZONTAL LAYOUT (4x2 — height < 170.dp)
    // -------------------------------------------------------------
    @Composable
    private fun MediumLayout(
        state: ScheduleState,
        colors: WidgetColors,
        isSmartSilentEnabled: Boolean,
        lastUpdatedTime: String
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(4.dp),
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
                        style = TextStyle(color = colors.primaryCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Updated: $lastUpdatedTime",
                        style = TextStyle(color = colors.textSecondary, fontSize = 9.sp)
                    )
                }

                WidgetSilentToggleButton(
                    isSmartSilentEnabled = isSmartSilentEnabled,
                    colors = colors,
                    isCompact = true
                )

                Spacer(modifier = GlanceModifier.width(6.dp))

                WidgetRefreshButton(colors = colors, sizeDp = 40)
            }

            Spacer(modifier = GlanceModifier.height(5.dp))

            // Main Period Card (clicking opens app) - What's happening NOW
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .cornerRadius(10.dp)
                    .background(colors.cardBackground)
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                when (state.status) {
                    TimetableStatus.CURRENT_CLASS -> {
                        val current = state.currentClass
                        Column(modifier = GlanceModifier.fillMaxSize()) {
                            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "CURRENT CLASS",
                                    style = TextStyle(color = colors.cyanAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                    modifier = GlanceModifier.defaultWeight()
                                )
                                Text(
                                    text = "${TimeUtils.formatTo12Hour(current?.startTime)} – ${TimeUtils.formatTo12Hour(current?.endTime)}",
                                    style = TextStyle(color = colors.textSecondary, fontSize = 9.sp)
                                )
                            }
                            Text(
                                text = current?.subject ?: "Class",
                                style = TextStyle(color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            Spacer(modifier = GlanceModifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = state.progressFraction,
                                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                                color = colors.primaryCyan,
                                backgroundColor = colors.progressTrack
                            )
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Text(
                                text = "${state.progressPercentage}% • ${state.timeSummary}",
                                style = TextStyle(color = colors.primaryCyan, fontSize = 9.sp, fontWeight = FontWeight.Medium),
                                maxLines = 1
                            )
                        }
                    }
                    TimetableStatus.BREAK,
                    TimetableStatus.LUNCH,
                    TimetableStatus.FREE_PERIOD -> {
                        Column(modifier = GlanceModifier.fillMaxSize()) {
                            Text(
                                text = state.statusSummary.uppercase(),
                                style = TextStyle(color = colors.cyanAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Break in Session",
                                style = TextStyle(color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            Text(
                                text = state.timeSummary,
                                style = TextStyle(color = colors.textSecondary, fontSize = 10.sp),
                                maxLines = 1
                            )
                        }
                    }
                    TimetableStatus.BEFORE_CLASSES -> {
                        Column(modifier = GlanceModifier.fillMaxSize()) {
                            Text(
                                text = "COLLEGE SCHEDULE",
                                style = TextStyle(color = colors.cyanAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Before College Hours",
                                style = TextStyle(color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            Text(
                                text = state.timeSummary,
                                style = TextStyle(color = colors.textSecondary, fontSize = 10.sp),
                                maxLines = 1
                            )
                        }
                    }
                    TimetableStatus.NO_MORE_CLASSES -> {
                        Column(modifier = GlanceModifier.fillMaxSize()) {
                            Text(
                                text = "CLASSES COMPLETED",
                                style = TextStyle(color = colors.cyanAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "All Done For Today",
                                style = TextStyle(color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            Text(
                                text = "Have a great evening!",
                                style = TextStyle(color = colors.textSecondary, fontSize = 10.sp),
                                maxLines = 1
                            )
                        }
                    }
                    TimetableStatus.NO_CLASSES_TODAY -> {
                        Column(modifier = GlanceModifier.fillMaxSize()) {
                            Text(
                                text = "TIMETABLE",
                                style = TextStyle(color = colors.cyanAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "No Classes Today",
                                style = TextStyle(color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            Text(
                                text = "Enjoy your day off",
                                style = TextStyle(color = colors.textSecondary, fontSize = 10.sp),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Dedicated Next Class Section (NEXT)
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(8.dp)
                    .background(colors.cardBackground)
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                if (state.nextClass != null) {
                    val next = state.nextClass
                    val countdown = if (state.status != TimetableStatus.CURRENT_CLASS && state.remainingMinutes > 0) " • in ${state.remainingMinutes} min" else ""
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "NEXT",
                            style = TextStyle(color = colors.cyanAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Text(
                            text = next.subject,
                            style = TextStyle(color = colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            modifier = GlanceModifier.defaultWeight(),
                            maxLines = 1
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(
                            text = "${TimeUtils.formatTo12Hour(next.startTime)}$countdown",
                            style = TextStyle(color = colors.primaryCyan, fontSize = 10.sp, fontWeight = FontWeight.Medium),
                            maxLines = 1
                        )
                    }
                } else {
                    val message = when (state.status) {
                        TimetableStatus.CURRENT_CLASS -> "Last class of the day"
                        TimetableStatus.NO_MORE_CLASSES -> "No more classes scheduled today"
                        TimetableStatus.NO_CLASSES_TODAY -> "Tap anywhere to add classes"
                        else -> "No upcoming classes"
                    }
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "STATUS",
                            style = TextStyle(color = colors.cyanAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Text(
                            text = message,
                            style = TextStyle(color = colors.textSecondary, fontSize = 10.sp),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------
    // 4. LARGE LAYOUT (4x3, 4x4 — height < 270.dp)
    // -------------------------------------------------------------
    @Composable
    private fun LargeLayout(
        state: ScheduleState,
        colors: WidgetColors,
        isSmartSilentEnabled: Boolean,
        lastUpdatedTime: String
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(4.dp),
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
                        style = TextStyle(color = colors.primaryCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Updated $lastUpdatedTime",
                        style = TextStyle(color = colors.textSecondary, fontSize = 9.sp)
                    )
                }

                WidgetSilentToggleButton(
                    isSmartSilentEnabled = isSmartSilentEnabled,
                    colors = colors,
                    isCompact = false
                )

                Spacer(modifier = GlanceModifier.width(8.dp))

                WidgetRefreshButton(colors = colors, sizeDp = 40)
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Main Period Card (clicking opens app)
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(12.dp)
                    .background(colors.cardBackground)
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(10.dp)
            ) {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    when (state.status) {
                        TimetableStatus.CURRENT_CLASS -> {
                            val current = state.currentClass
                            Text(
                                text = "CURRENT CLASS",
                                style = TextStyle(color = colors.cyanAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = current?.subject ?: "Class",
                                style = TextStyle(color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            Text(
                                text = "${TimeUtils.formatTo12Hour(current?.startTime)} — ${TimeUtils.formatTo12Hour(current?.endTime)}${current?.faculty?.let { " • $it" } ?: ""}",
                                style = TextStyle(color = colors.textSecondary, fontSize = 11.sp)
                            )

                            Spacer(modifier = GlanceModifier.height(6.dp))

                            LinearProgressIndicator(
                                progress = state.progressFraction,
                                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                                color = colors.primaryCyan,
                                backgroundColor = colors.progressTrack
                            )

                            Spacer(modifier = GlanceModifier.height(4.dp))

                            Row(modifier = GlanceModifier.fillMaxWidth()) {
                                Text(
                                    text = "${state.progressPercentage}% completed",
                                    style = TextStyle(color = colors.primaryCyan, fontSize = 10.sp, fontWeight = FontWeight.Medium),
                                    modifier = GlanceModifier.defaultWeight()
                                )
                                Text(
                                    text = state.timeSummary,
                                    style = TextStyle(color = colors.textSecondary, fontSize = 10.sp)
                                )
                            }
                        }
                        TimetableStatus.BREAK,
                        TimetableStatus.LUNCH,
                        TimetableStatus.FREE_PERIOD -> {
                            Text(
                                text = state.statusSummary.uppercase(),
                                style = TextStyle(color = colors.cyanAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Break in Session",
                                style = TextStyle(color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = state.timeSummary,
                                style = TextStyle(color = colors.textSecondary, fontSize = 11.sp)
                            )
                        }
                        TimetableStatus.BEFORE_CLASSES -> {
                            Text(
                                text = "COLLEGE TIMETABLE",
                                style = TextStyle(color = colors.cyanAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Classes Haven't Started",
                                style = TextStyle(color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = state.timeSummary,
                                style = TextStyle(color = colors.textSecondary, fontSize = 11.sp)
                            )
                        }
                        TimetableStatus.NO_MORE_CLASSES -> {
                            Text(
                                text = "CLASSES COMPLETED",
                                style = TextStyle(color = colors.cyanAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "All Done For Today",
                                style = TextStyle(color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Have a great evening!",
                                style = TextStyle(color = colors.textSecondary, fontSize = 11.sp)
                            )
                        }
                        TimetableStatus.NO_CLASSES_TODAY -> {
                            Text(
                                text = "TIMETABLE",
                                style = TextStyle(color = colors.cyanAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "No Classes Scheduled Today",
                                style = TextStyle(color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Tap anywhere to add classes.",
                                style = TextStyle(color = colors.textSecondary, fontSize = 11.sp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Next Class Preview Card
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(10.dp)
                    .background(colors.cardBackground)
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(8.dp)
            ) {
                if (state.nextClass != null) {
                    val next = state.nextClass
                    Column {
                        Text(
                            text = "UPCOMING",
                            style = TextStyle(color = colors.cyanAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        )
                        val countdown = if (state.status != TimetableStatus.CURRENT_CLASS && state.remainingMinutes > 0) " • in ${state.remainingMinutes} min" else ""
                        Text(
                            text = "${next.subject} (${TimeUtils.formatTo12Hour(next.startTime)})$countdown",
                            style = TextStyle(color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            maxLines = 1
                        )
                    }
                } else {
                    val note = when (state.status) {
                        TimetableStatus.CURRENT_CLASS -> "Last class of the day — no further classes after this."
                        TimetableStatus.NO_MORE_CLASSES -> "All classes completed for today — relax & recharge!"
                        TimetableStatus.NO_CLASSES_TODAY -> "No classes scheduled for today. Tap to add classes."
                        else -> "No further classes scheduled today."
                    }
                    Column {
                        Text(
                            text = "UPCOMING",
                            style = TextStyle(color = colors.cyanAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = note,
                            style = TextStyle(color = colors.textSecondary, fontSize = 11.sp),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------
    // 5. EXTRA-LARGE EXPANDED LAYOUT (4x4+, EXPANDED — height >= 270.dp)
    // -------------------------------------------------------------
    @Composable
    private fun ExtraLargeLayout(
        state: ScheduleState,
        colors: WidgetColors,
        isSmartSilentEnabled: Boolean,
        lastUpdatedTime: String
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(4.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Header Row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "ClassNow Dashboard",
                        style = TextStyle(color = colors.primaryCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Updated $lastUpdatedTime",
                        style = TextStyle(color = colors.textSecondary, fontSize = 10.sp)
                    )
                }

                WidgetSilentToggleButton(
                    isSmartSilentEnabled = isSmartSilentEnabled,
                    colors = colors,
                    isCompact = false
                )

                Spacer(modifier = GlanceModifier.width(8.dp))

                WidgetRefreshButton(colors = colors, sizeDp = 40)
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // Main Active Period Card
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(12.dp)
                    .background(colors.cardBackground)
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(12.dp)
            ) {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    when (state.status) {
                        TimetableStatus.CURRENT_CLASS -> {
                            val current = state.currentClass
                            Text(
                                text = "CURRENT CLASS",
                                style = TextStyle(color = colors.cyanAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = current?.subject ?: "Class",
                                style = TextStyle(color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            Text(
                                text = "${TimeUtils.formatTo12Hour(current?.startTime)} — ${TimeUtils.formatTo12Hour(current?.endTime)}${current?.faculty?.let { " • $it" } ?: ""}",
                                style = TextStyle(color = colors.textSecondary, fontSize = 12.sp)
                            )

                            Spacer(modifier = GlanceModifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = state.progressFraction,
                                modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                                color = colors.primaryCyan,
                                backgroundColor = colors.progressTrack
                            )

                            Spacer(modifier = GlanceModifier.height(6.dp))

                            Row(modifier = GlanceModifier.fillMaxWidth()) {
                                Text(
                                    text = "${state.progressPercentage}% completed",
                                    style = TextStyle(color = colors.primaryCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                    modifier = GlanceModifier.defaultWeight()
                                )
                                Text(
                                    text = state.timeSummary,
                                    style = TextStyle(color = colors.textSecondary, fontSize = 11.sp)
                                )
                            }
                        }
                        TimetableStatus.BREAK,
                        TimetableStatus.LUNCH,
                        TimetableStatus.FREE_PERIOD -> {
                            Text(
                                text = state.statusSummary.uppercase(),
                                style = TextStyle(color = colors.cyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Break in Session",
                                style = TextStyle(color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = state.timeSummary,
                                style = TextStyle(color = colors.textSecondary, fontSize = 12.sp)
                            )
                        }
                        TimetableStatus.BEFORE_CLASSES -> {
                            Text(
                                text = "COLLEGE SCHEDULE",
                                style = TextStyle(color = colors.cyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = state.nextClass?.let { "First Class: ${it.subject}" } ?: "Classes Haven't Started",
                                style = TextStyle(color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = state.timeSummary,
                                style = TextStyle(color = colors.textSecondary, fontSize = 12.sp)
                            )
                        }
                        TimetableStatus.NO_MORE_CLASSES -> {
                            Text(
                                text = "CLASSES COMPLETED",
                                style = TextStyle(color = colors.cyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "All Done For Today",
                                style = TextStyle(color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Have a great evening!",
                                style = TextStyle(color = colors.textSecondary, fontSize = 12.sp)
                            )
                        }
                        TimetableStatus.NO_CLASSES_TODAY -> {
                            Text(
                                text = "TIMETABLE",
                                style = TextStyle(color = colors.cyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "No Classes Scheduled Today",
                                style = TextStyle(color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Tap here to manage schedule",
                                style = TextStyle(color = colors.textSecondary, fontSize = 12.sp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // Upcoming Schedule Section
            Text(
                text = "UPCOMING TODAY",
                style = TextStyle(color = colors.cyanAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = GlanceModifier.height(4.dp))

            if (state.upcomingClasses.isNotEmpty()) {
                val previewList = state.upcomingClasses.take(3)
                for (upcoming in previewList) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .cornerRadius(8.dp)
                            .background(colors.cardBackground)
                            .clickable(actionStartActivity<MainActivity>())
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = upcoming.subject,
                                style = TextStyle(color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                modifier = GlanceModifier.defaultWeight(),
                                maxLines = 1
                            )
                            Text(
                                text = "${TimeUtils.formatTo12Hour(upcoming.startTime)} - ${TimeUtils.formatTo12Hour(upcoming.endTime)}",
                                style = TextStyle(color = colors.primaryCyan, fontSize = 11.sp)
                            )
                        }
                    }
                    Spacer(modifier = GlanceModifier.height(4.dp))
                }
            } else {
                Text(
                    text = "No further classes scheduled for today.",
                    style = TextStyle(color = colors.textSecondary, fontSize = 11.sp)
                )
            }
        }
    }

    // -------------------------------------------------------------
    // SHARED REUSABLE COMPONENTS
    // -------------------------------------------------------------

    /**
     * Dedicated refresh icon button with rounded corners, vector icon,
     * and an explicit broadcast trigger to ClassNowWidgetReceiver.
     */
    @Composable
    private fun WidgetRefreshButton(colors: WidgetColors, sizeDp: Int = 48) {
        val context = LocalContext.current
        val refreshIntent = Intent(context, ClassNowWidgetReceiver::class.java).apply {
            action = ClassNowWidgetReceiver.ACTION_REFRESH_WIDGET
            `package` = context.packageName
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }

        Box(
            modifier = GlanceModifier
                .size(sizeDp.coerceAtLeast(48).dp)
                .cornerRadius(12.dp)
                .background(colors.buttonBackground)
                .clickable(actionSendBroadcast(refreshIntent)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Refresh Timetable",
                modifier = GlanceModifier.size(20.dp),
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
        isCompact: Boolean = false
    ) {
        val context = LocalContext.current
        val toggleIntent = Intent(context, ClassNowWidgetReceiver::class.java).apply {
            action = ClassNowWidgetReceiver.ACTION_TOGGLE_SMART_SILENT
            `package` = context.packageName
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        val bg = if (isSmartSilentEnabled) colors.silentOnBackground else colors.silentOffBackground
        val textCol = if (isSmartSilentEnabled) colors.silentOnContent else colors.silentOffContent
        val iconRes = if (isSmartSilentEnabled) R.drawable.ic_volume_off else R.drawable.ic_volume_up
        val label = if (isSmartSilentEnabled) {
            if (isCompact) "Silent: ON" else "Smart Silent: ON"
        } else {
            if (isCompact) "Silent: OFF" else "Smart Silent: OFF"
        }

        Box(
            modifier = GlanceModifier
                .height(48.dp)
                .cornerRadius(12.dp)
                .background(bg)
                .clickable(actionSendBroadcast(toggleIntent))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = null,
                    modifier = GlanceModifier.size(18.dp),
                    colorFilter = ColorFilter.tint(textCol)
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = label,
                    style = TextStyle(
                        color = textCol,
                        fontSize = 11.sp,
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
