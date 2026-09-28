package com.ledger.app

import org.json.JSONArray
import org.json.JSONObject

object BackupCodec {
    fun json(data: BackupData): String = JSONObject().apply {
        put("formatVersion", 1)
        put("categories", JSONArray().apply { data.categories.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } })
        put("transactions", JSONArray().apply { data.transactions.forEach { row -> put(JSONObject().apply {
            put("id", row.id); put("date", row.date.toString()); put("name", row.name); put("amountCents", row.amountCents)
            put("categoryId", row.categoryId); put("type", row.type); put("note", row.note)
            put("excludeFromDailyStats", row.excludeFromDailyStats); put("createdAt", row.createdAt); put("updatedAt", row.updatedAt)
        }) } })
        put("rules", JSONArray().apply { data.rules.forEach { put(JSONObject().put("id", it.id).put("keyword", it.keyword).put("categoryName", it.categoryName)) } })
        put("budgets", JSONArray().apply { data.budgets.forEach { put(JSONObject().put("period", it.period).put("amountCents", it.amountCents)) } })
    }.toString(2)

    fun read(text: String): BackupData {
        val root = JSONObject(text)
        require(root.getInt("formatVersion") == 1) { "不支持此备份版本" }
        val categories = root.getJSONArray("categories").objects().map { CategoryEntity(it.getLong("id"), it.getString("name")) }
        val transactions = root.getJSONArray("transactions").objects().map {
            TransactionEntity(it.getLong("id"), java.time.LocalDate.parse(it.getString("date")).toEpochDay(), it.getString("name"),
                it.getLong("amountCents"), it.getLong("categoryId"), it.getString("type"), it.optString("note"),
                it.getBoolean("excludeFromDailyStats"), it.getLong("createdAt"), it.getLong("updatedAt"))
        }
        val rules = root.getJSONArray("rules").objects().map { CategoryRuleEntity(it.getLong("id"), it.getString("keyword"), it.getString("categoryName")) }
        val budgets = root.getJSONArray("budgets").objects().map { BudgetEntity(it.getString("period"), it.getLong("amountCents")) }
        val ids = categories.map { it.id }.toSet()
        require(categories.isNotEmpty() && categories.size == ids.size && categories.all { it.id > 0 && it.name.isNotBlank() } && transactions.all { it.id > 0 && it.amountCents > 0 && it.name.isNotBlank() && it.categoryId in ids && it.type in listOf("EXPENSE", "INCOME") }) { "备份数据无效" }
        require(transactions.map { it.id }.distinct().size == transactions.size) { "备份账单 ID 重复" }
        return BackupData(transactions, categories, rules, budgets)
    }

    fun csv(data: BackupData): String {
        val names = data.categories.associate { it.id to it.name }
        fun quote(value: Any?): String = "\"" + value.toString().replace("\"", "\"\"") + "\""
        return buildString {
            appendLine("id,date,name,amount,category,type,note,excludeFromDailyStats,createdAt,updatedAt")
            data.transactions.forEach {
                appendLine(listOf(it.id, it.date, it.name, java.math.BigDecimal.valueOf(it.amountCents, 2).toPlainString(),
                    names[it.categoryId] ?: "其他", it.type, it.note, it.excludeFromDailyStats, it.createdAt, it.updatedAt).joinToString(",", transform = ::quote))
            }
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
