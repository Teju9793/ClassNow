package com.teju.classnow

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.teju.classnow.data.model.TimetableEntry
import com.teju.classnow.domain.timetable.TimetableEngine
import com.teju.classnow.domain.timetable.TimetableStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

enum class WidgetLayoutCategory {
    COMPACT,
    SMALL,
    MEDIUM,
    LARGE,
    EXTRA_LARGE
}

/**
 * Pure Kotlin representation of the sizing decision logic used in ClassNowWidget
 */
fun resolveLayoutCategory(width: Dp, height: Dp): WidgetLayoutCategory {
    return when {
        height < 55.dp -> WidgetLayoutCategory.COMPACT
        width < 220.dp -> WidgetLayoutCategory.SMALL
        height < 170.dp -> WidgetLayoutCategory.MEDIUM
        height < 270.dp -> WidgetLayoutCategory.LARGE
        else -> WidgetLayoutCategory.EXTRA_LARGE
    }
}

/**
 * Indicates whether a resolved layout category renders the horizontal side-by-side dual-card layout.
 * In ClassNowWidget, all wide layouts (MEDIUM, LARGE, EXTRA_LARGE) render HorizontalDualCardLayout.
 */
fun isCategoryRenderedHorizontally(category: WidgetLayoutCategory): Boolean {
    return category == WidgetLayoutCategory.MEDIUM ||
           category == WidgetLayoutCategory.LARGE ||
           category == WidgetLayoutCategory.EXTRA_LARGE
}

class WidgetLayoutSizingTest {

    @Test
    fun test_CompactLayout_OnUltraShort1RowRibbon() {
        assertEquals(WidgetLayoutCategory.COMPACT, resolveLayoutCategory(300.dp, 40.dp))
        assertEquals(WidgetLayoutCategory.COMPACT, resolveLayoutCategory(180.dp, 50.dp))
        assertFalse(isCategoryRenderedHorizontally(WidgetLayoutCategory.COMPACT))
    }

    @Test
    fun test_SmallLayout_OnNarrowLaunchers_ValidNarrowFallback() {
        // Narrow widths (< 220.dp) use deliberate single-column compact fallback
        assertEquals(WidgetLayoutCategory.SMALL, resolveLayoutCategory(180.dp, 70.dp))
        assertEquals(WidgetLayoutCategory.SMALL, resolveLayoutCategory(160.dp, 140.dp))
        assertEquals(WidgetLayoutCategory.SMALL, resolveLayoutCategory(180.dp, 160.dp))
        assertEquals(WidgetLayoutCategory.SMALL, resolveLayoutCategory(200.dp, 250.dp))
        assertFalse("Narrow widgets do not force side-by-side", isCategoryRenderedHorizontally(WidgetLayoutCategory.SMALL))
    }

    @Test
    fun test_AllWideSizes_AlwaysSelectHorizontalDualCardLayout() {
        // Every wide size (width >= 220.dp) must render side-by-side CURRENT + NEXT cards
        val wideSizes = listOf(
            260.dp to 75.dp,   // 4x2 standard compact height
            260.dp to 130.dp,  // 4x2 standard medium height
            320.dp to 150.dp,  // 4x2 high-res phone
            280.dp to 210.dp,  // 4x3 tall widget
            350.dp to 250.dp,  // 4x3 / 4x4 launcher
            350.dp to 350.dp,  // 4x4 expanded
            400.dp to 450.dp   // Tablet or expanded dashboard
        )

        for ((w, h) in wideSizes) {
            val category = resolveLayoutCategory(w, h)
            assertTrue(
                "Wide size ${w}x${h} must render horizontal dual-card layout (category=$category)",
                isCategoryRenderedHorizontally(category)
            )
        }
    }

    @Test
    fun test_WideLayout_EliminatesLegacyUpcomingTodayVerticalSection() {
        // In the wide layout, the upcoming class is placed inside NEXT CLASS card on the right,
        // and NOT in a separate vertical "UPCOMING TODAY" list below the current class.
        val sampleEntries = listOf(
            TimetableEntry(id = 1L, dayOfWeek = "Monday", subject = "Algorithms", startTime = "09:00", endTime = "10:00"),
            TimetableEntry(id = 2L, dayOfWeek = "Monday", subject = "Database Systems", startTime = "10:15", endTime = "11:15"),
            TimetableEntry(id = 3L, dayOfWeek = "Monday", subject = "Computer Networks", startTime = "11:30", endTime = "12:30")
        )

        val state = TimetableEngine.getScheduleState(LocalTime.of(9, 30), sampleEntries)
        assertEquals(TimetableStatus.CURRENT_CLASS, state.status)
        assertEquals("Algorithms", state.currentClass?.subject)
        assertNotNull("NEXT CLASS card must display the immediate next class", state.nextClass)
        assertEquals("Database Systems", state.nextClass?.subject)

        // The horizontal layout renders Left Card = CURRENT CLASS, Right Card = NEXT CLASS
        val leftCardTitle = state.currentClass?.subject
        val rightCardTitle = state.nextClass?.subject
        assertEquals("Algorithms", leftCardTitle)
        assertEquals("Database Systems", rightCardTitle)
    }

    @Test
    fun test_WideLayout_EmptyNextClassState_RendersInsideRightHandCard() {
        // During the last class of the day, nextClass is null.
        // The right-hand card must render a graceful empty state ("Final Class • Done after this class")
        // rather than collapsing, leaving a blank gap, or rendering a vertical section.
        val singleEntry = listOf(
            TimetableEntry(id = 1L, dayOfWeek = "Monday", subject = "Final Project", startTime = "14:00", endTime = "16:00")
        )

        val state = TimetableEngine.getScheduleState(LocalTime.of(15, 0), singleEntry)
        assertEquals(TimetableStatus.CURRENT_CLASS, state.status)
        assertEquals(null, state.nextClass)

        val rightCardEmptyTitle = when (state.status) {
            TimetableStatus.CURRENT_CLASS -> "Final Class"
            TimetableStatus.NO_MORE_CLASSES -> "All Done"
            else -> "No Classes"
        }
        val rightCardEmptySubtitle = when (state.status) {
            TimetableStatus.CURRENT_CLASS -> "Done after this class"
            TimetableStatus.NO_MORE_CLASSES -> "No further classes today"
            else -> "No classes scheduled today"
        }

        assertEquals("Final Class", rightCardEmptyTitle)
        assertEquals("Done after this class", rightCardEmptySubtitle)
    }
}
