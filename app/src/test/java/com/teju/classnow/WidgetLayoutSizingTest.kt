package com.teju.classnow

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

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
        height < 95.dp -> WidgetLayoutCategory.COMPACT
        width < 220.dp -> WidgetLayoutCategory.SMALL
        height < 170.dp -> WidgetLayoutCategory.MEDIUM
        height < 270.dp -> WidgetLayoutCategory.LARGE
        else -> WidgetLayoutCategory.EXTRA_LARGE
    }
}

class WidgetLayoutSizingTest {

    @Test
    fun test_CompactLayout_OnShort1RowSizes() {
        assertEquals(WidgetLayoutCategory.COMPACT, resolveLayoutCategory(300.dp, 60.dp))
        assertEquals(WidgetLayoutCategory.COMPACT, resolveLayoutCategory(180.dp, 80.dp))
        assertEquals(WidgetLayoutCategory.COMPACT, resolveLayoutCategory(360.dp, 90.dp))
    }

    @Test
    fun test_SmallLayout_OnSquare2x2Launchers() {
        assertEquals(WidgetLayoutCategory.SMALL, resolveLayoutCategory(160.dp, 140.dp))
        assertEquals(WidgetLayoutCategory.SMALL, resolveLayoutCategory(180.dp, 160.dp))
    }

    @Test
    fun test_MediumHorizontalLayout_On4x2Launchers() {
        assertEquals(WidgetLayoutCategory.MEDIUM, resolveLayoutCategory(260.dp, 130.dp))
        assertEquals(WidgetLayoutCategory.MEDIUM, resolveLayoutCategory(320.dp, 150.dp))
    }

    @Test
    fun test_LargeLayout_On4x3Or4x4Launchers() {
        assertEquals(WidgetLayoutCategory.LARGE, resolveLayoutCategory(280.dp, 210.dp))
        assertEquals(WidgetLayoutCategory.LARGE, resolveLayoutCategory(350.dp, 250.dp))
    }

    @Test
    fun test_ExtraLargeLayout_OnExpandedOrScrollableLaunchers() {
        assertEquals(WidgetLayoutCategory.EXTRA_LARGE, resolveLayoutCategory(350.dp, 350.dp))
        assertEquals(WidgetLayoutCategory.EXTRA_LARGE, resolveLayoutCategory(400.dp, 450.dp))
    }
}
