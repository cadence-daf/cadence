package com.cadence.daf

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WidgetsTest {
    @Test
    fun dialFillsWithTheGoal() {
        assertEquals(0, litSteps(0, 10))
        assertEquals(1, litSteps(1, 10))
        assertEquals(4, litSteps(6, 10))
        assertEquals(6, litSteps(9, 10))
        assertEquals(8, litSteps(10, 10))
        assertEquals(8, litSteps(30, 10))
    }

    @Test
    fun dialStaysDimWhenTheGoalIsOff() {
        assertEquals(0, litSteps(30, 0))
    }

    @Test
    fun widgetStyleValuesAreClamped() {
        assertEquals(5, clampWidgetAccent(5))
        assertEquals(0, clampWidgetAccent(6))
        assertEquals(0, clampWidgetAccent(-1))
        assertEquals(100, clampWidgetOpacity(150))
        assertEquals(0, clampWidgetOpacity(-5))
    }

    @Test
    fun weekIsSevenDaysEndingToday() {
        val today = LocalDate.of(2026, 3, 2)
        val week = lastWeek(mapOf(today to 5, today.minusDays(6) to 3, today.minusDays(7) to 9), today)
        assertEquals(listOf(3, 0, 0, 0, 0, 0, 5), week.map { it.minutes })
        assertEquals(LocalDate.of(2026, 2, 24), week.first().date)
    }
}
