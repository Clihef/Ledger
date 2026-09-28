package com.ledger.app

import java.math.BigDecimal
import java.time.LocalDate

data class ParsedTransaction(
    val date: LocalDate,
    val name: String,
    val amountCents: Long,
    val suggestedCategory: String,
    val type: EntryType = EntryType.EXPENSE,
    val confidence: Float = 0.8f,
    val originalText: String = "",
)

enum class EntryType { EXPENSE, INCOME }

interface ExpenseParser {
    fun parse(text: String, referenceDate: LocalDate): List<ParsedTransaction>
}

object CategoryRuleEngine {
    val categories = listOf("餐饮", "交通", "购物", "娱乐", "生活用品", "学习", "医疗", "住房", "旅行", "人情/社交", "数码", "运动", "其他")
    private val rules = linkedMapOf(
        "餐饮" to listOf("早餐", "午饭", "午餐", "晚饭", "晚餐", "夜宵", "咖啡", "奶茶", "食堂", "包子", "豆浆", "面包", "聚餐", "饭团", "便利蜂", "便利店", "零食", "餐厅", "外卖", "库迪"),
        "交通" to listOf("地铁", "公交", "打车", "滴滴", "出租车", "车票", "高铁"),
        "娱乐" to listOf("ktv", "电影", "台球", "颐和园", "游戏", "门票"),
        "运动" to listOf("健身", "篮球", "羽毛球", "游泳"),
        "购物" to listOf("购物", "衣服", "鞋", "超市"),
        "生活用品" to listOf("棉签", "纸巾", "洗发", "牙膏"),
        "学习" to listOf("书", "课程", "文具"),
        "医疗" to listOf("药", "医院", "诊所"),
        "住房" to listOf("房租", "水电", "物业"),
        "旅行" to listOf("旅游", "酒店", "住宿", "机票"),
        "人情/社交" to listOf("红包", "礼物", "请客"),
        "数码" to listOf("电脑", "手机", "耳机"),
    )

    fun infer(name: String, remembered: Map<String, String> = emptyMap()): String {
        val lower = name.lowercase()
        remembered.entries.filter { lower.contains(it.key.lowercase()) }
            .maxByOrNull { it.key.length }?.let { return it.value }
        return rules.entries.firstOrNull { (_, words) -> words.any(lower::contains) }?.key ?: "其他"
    }
}

class TextExpenseParser(private val remembered: Map<String, String> = emptyMap()) : ExpenseParser {
    private val dateOnly = Regex("^(?:(\\d{4})[.年/-])?(\\d{1,2})[.月/-](\\d{1,2})日?$")
    private val datePrefix = Regex("^(今天|昨天|前天|(?:(?:\\d{4})[.年/-])?\\d{1,2}[.月/-]\\d{1,2}日?)\\s*")
    private val entry = Regex("^(.+?)\\s*([0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:元|块)?$")
    private val inlineBoundary = Regex("(?<=\\d)\\s+(?=[^\\d\\s])")

    override fun parse(text: String, referenceDate: LocalDate): List<ParsedTransaction> {
        val output = mutableListOf<ParsedTransaction>()
        var activeDate: LocalDate? = referenceDate
        for (line in text.replace('，', '\n').replace('；', '\n').lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split(inlineBoundary)
            for (rawPart in parts) {
                var part = rawPart.trim()
                val prefix = datePrefix.find(part)
                if (prefix != null) {
                    activeDate = resolveDate(prefix.groupValues[1], referenceDate)
                    part = part.removeRange(prefix.range).trim()
                }
                if (part.isBlank() || activeDate == null) continue
                // An invalid date header must never become a transaction.
                if (dateOnly.matches(part)) continue
                val match = entry.matchEntire(part) ?: continue
                val name = match.groupValues[1].trim().trimEnd('：', ':')
                val cents = try { BigDecimal(match.groupValues[2]).movePointRight(2).longValueExact() } catch (_: Exception) { continue }
                if (name.isBlank() || cents <= 0) continue
                val income = name.startsWith("收入") || name.startsWith("工资") || name.startsWith("奖金") || name.startsWith("退款")
                val category = if (income) "其他" else CategoryRuleEngine.infer(name, remembered)
                output += ParsedTransaction(activeDate, name, cents, category, if (income) EntryType.INCOME else EntryType.EXPENSE, if (category == "其他") 0.5f else 0.8f, rawPart)
            }
        }
        return output
    }

    private fun resolveDate(value: String, reference: LocalDate): LocalDate? = when (value) {
        "今天" -> reference
        "昨天" -> reference.minusDays(1)
        "前天" -> reference.minusDays(2)
        else -> {
            val m = dateOnly.matchEntire(value) ?: return null
            val month = m.groupValues[2].toInt()
            val day = m.groupValues[3].toInt()
            val year = m.groupValues[1].takeIf(String::isNotEmpty)?.toInt() ?: reference.year
            runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }
    }
}

fun parseCents(input: String): Long? = runCatching {
    BigDecimal(input.trim()).movePointRight(2).longValueExact().takeIf { it > 0 }
}.getOrNull()

fun money(cents: Long): String = "¥" + BigDecimal.valueOf(cents, 2).toPlainString()
