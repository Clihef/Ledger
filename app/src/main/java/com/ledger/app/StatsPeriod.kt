package com.ledger.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class StatsPeriod(val label: String) { WEEK("周"), MONTH("月"), YEAR("年"), CUSTOM("自定义") }

data class DateRange(val start: LocalDate, val end: LocalDate) {
    init { require(!end.isBefore(start)) }
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
    fun daysThrough(today: LocalDate): Long = (ChronoUnit.DAYS.between(start, minOf(end, today)) + 1).coerceAtLeast(1)
}

fun periodRange(period: StatsPeriod, date: LocalDate): DateRange = when (period) {
    StatsPeriod.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { DateRange(it, it.plusDays(6)) }
    StatsPeriod.MONTH -> date.withDayOfMonth(1).let { DateRange(it, it.plusMonths(1).minusDays(1)) }
    StatsPeriod.YEAR -> date.withDayOfYear(1).let { DateRange(it, it.plusYears(1).minusDays(1)) }
    StatsPeriod.CUSTOM -> DateRange(date, date)
}

fun shiftPeriod(period: StatsPeriod, anchor: LocalDate, offset: Long): LocalDate = when (period) {
    StatsPeriod.WEEK -> anchor.plusWeeks(offset)
    StatsPeriod.MONTH -> anchor.plusMonths(offset)
    StatsPeriod.YEAR -> anchor.plusYears(offset)
    StatsPeriod.CUSTOM -> anchor
}

fun periodLabel(period: StatsPeriod, range: DateRange): String = when (period) {
    StatsPeriod.WEEK -> "${range.start.monthValue}月${range.start.dayOfMonth}日—${range.end.monthValue}月${range.end.dayOfMonth}日"
    StatsPeriod.MONTH -> "${range.start.year}年${range.start.monthValue}月"
    StatsPeriod.YEAR -> "${range.start.year}年"
    StatsPeriod.CUSTOM -> "${range.start}—${range.end}"
}

fun periodChoices(period: StatsPeriod, today: LocalDate, earliest: LocalDate?): List<DateRange> {
    if (period == StatsPeriod.CUSTOM) return emptyList()
    val current = periodRange(period, today).start
    val floor = earliest?.let { minOf(current, periodRange(period, it).start) } ?: shiftPeriod(period, current, when (period) {
        StatsPeriod.WEEK -> -11
        StatsPeriod.MONTH -> -11
        StatsPeriod.YEAR -> -4
        StatsPeriod.CUSTOM -> 0
    })
    return generateSequence(current) { shiftPeriod(period, it, -1) }
        .takeWhile { !it.isBefore(floor) }
        .map { periodRange(period, it) }
        .toList()
}
