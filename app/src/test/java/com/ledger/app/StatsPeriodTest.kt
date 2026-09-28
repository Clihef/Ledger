package com.ledger.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StatsPeriodTest {
    @Test fun weeksCrossMonthsAndYears() {
        val week = periodRange(StatsPeriod.WEEK, LocalDate.of(2027, 1, 1))
        assertEquals(LocalDate.of(2026, 12, 28), week.start)
        assertEquals(LocalDate.of(2027, 1, 3), week.end)
        assertEquals("12月28日—1月3日", periodLabel(StatsPeriod.WEEK, week))
        assertEquals(LocalDate.of(2026, 12, 21), periodRange(StatsPeriod.WEEK, shiftPeriod(StatsPeriod.WEEK, week.start, -1)).start)
    }

    @Test fun historyChoicesAndElapsedDays() {
        val today = LocalDate.of(2026, 9, 28)
        val choices = periodChoices(StatsPeriod.WEEK, today, today.minusWeeks(2))
        assertEquals(3, choices.size)
        assertEquals(today.minusWeeks(2), choices.last().start)
        assertEquals(1L, choices.first().daysThrough(today))
        assertEquals(7L, choices.last().daysThrough(today))
        assertEquals(LocalDate.of(2026, 8, 1), periodRange(StatsPeriod.MONTH, LocalDate.of(2026, 8, 31)).start)
    }

    @Test fun monthAndYearChoicesReachOlderRecords() {
        val today = LocalDate.of(2026, 9, 28)
        assertEquals(LocalDate.of(2025, 12, 1), periodChoices(StatsPeriod.MONTH, today, LocalDate.of(2025, 12, 31)).last().start)
        assertEquals(LocalDate.of(2024, 1, 1), periodChoices(StatsPeriod.YEAR, today, LocalDate.of(2024, 6, 2)).last().start)
        assertEquals(DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)), periodRange(StatsPeriod.YEAR, LocalDate.of(2024, 2, 29)))
    }
}
