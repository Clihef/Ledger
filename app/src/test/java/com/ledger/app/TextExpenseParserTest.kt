package com.ledger.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TextExpenseParserTest {
    private val day = LocalDate.of(2026, 9, 20)
    private val parser = TextExpenseParser()

    @Test fun individualAmountsAndCategories() {
        val rows = parser.parse("早餐3\n午饭11.5\n咖啡6\n打车22.13", day)
        assertEquals(listOf(300L, 1150L, 600L, 2213L), rows.map { it.amountCents })
        assertEquals(listOf("餐饮", "餐饮", "餐饮", "交通"), rows.map { it.suggestedCategory })
    }

    @Test fun datesAndInlineEntries() {
        val rows = parser.parse("9.20 晚饭83\n昨天晚饭20\n今天早餐5 午饭18 晚饭25", day)
        assertEquals(5, rows.size)
        assertEquals(listOf(day, day.minusDays(1), day, day, day), rows.map { it.date })
        assertEquals(listOf(8300L, 2000L, 500L, 1800L, 2500L), rows.map { it.amountCents })
    }

    @Test fun fullMultiDayBill() {
        val text = """9.15
            早餐包子豆浆6
            午饭健康餐17
            下午奶油面包6
            下午ktv18
            库迪咖啡6
            晚饭健康餐18
            夜宵便利蜂饭团和包子8.26

            9.16
            早餐食堂3
            午饭健康餐18
            晚饭健康餐18

            9.17
            早餐食堂3
            棉签3
            午饭食堂11.5
            玩到食堂13""".trimIndent()
        val rows = parser.parse(text, day)
        assertEquals(14, rows.size)
        assertEquals(listOf(7, 3, 4), rows.groupingBy { it.date.dayOfMonth }.eachCount().values.toList())
        assertEquals(826L, rows[6].amountCents)
        assertEquals("娱乐", rows[3].suggestedCategory)
        assertEquals("生活用品", rows[11].suggestedCategory)
    }

    @Test fun explicitYearAndInvalidInput() {
        assertEquals(LocalDate.of(2025, 9, 20), parser.parse("2025.9.20\n午饭16", day).single().date)
        assertEquals(day.minusDays(2), parser.parse("前天\n地铁8", day).single().date)
        assertTrue(parser.parse("9.31\n没有金额", day).isEmpty())
        assertTrue(parser.parse("9.31\n午饭16", day).isEmpty())
        assertNull(parseCents("1.234"))
        assertEquals("交通", CategoryRuleEngine.infer("库迪", mapOf("库迪" to "交通")))
    }

    @Test fun chineseDateAndIncome() {
        val rows = parser.parse("9月20日\n地铁8\n2026.9.21 工资5000", day)
        assertEquals(listOf(day, day.plusDays(1)), rows.map { it.date })
        assertEquals(listOf(800L, 500000L), rows.map { it.amountCents })
        assertEquals(EntryType.INCOME, rows.last().type)
    }
}
