package com.ledger.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class LedgerState(
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val rules: List<CategoryRuleEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LedgerRepository(LedgerDatabase.get(application))
    val state = combine(repository.transactions, repository.categories, repository.rules, repository.budgets) { rows, categories, rules, budgets ->
        LedgerState(rows, categories, rules, budgets)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerState())
    val draft = MutableStateFlow<List<ParsedTransaction>>(emptyList())
    val message = MutableStateFlow<String?>(null)
    val billCategory = MutableStateFlow<String?>(null)
    val billRange = MutableStateFlow<Pair<LocalDate, LocalDate>?>(null)

    init { viewModelScope.launch { repository.seedCategories() } }

    fun parse(text: String): Boolean {
        val remembered = state.value.rules.associate { it.keyword to it.categoryName }
        val result = TextExpenseParser(remembered).parse(text, LocalDate.now())
        if (result.isEmpty()) { message.value = "未识别到账单，请检查项目和末尾金额"; return false }
        draft.value = result
        return true
    }

    fun updateDraft(index: Int, row: ParsedTransaction) { draft.value = draft.value.toMutableList().also { it[index] = row } }
    fun deleteDraft(index: Int) { draft.value = draft.value.toMutableList().also { it.removeAt(index) } }
    fun addDraft() { draft.value = draft.value + ParsedTransaction(LocalDate.now(), "", 0, "其他") }
    fun saveDraft(onSaved: () -> Unit) = viewModelScope.launch {
        val rows = draft.value
        if (rows.isEmpty() || rows.any { it.name.isBlank() || it.amountCents <= 0 }) { message.value = "请填写每条账单的项目和正确金额"; return@launch }
        try {
            repository.seedCategories()
            repository.saveParsed(rows, repository.categoryIds())
            draft.value = emptyList()
            message.value = "已记入 ${rows.size} 笔账单"
            onSaved()
        } catch (e: Exception) { message.value = "保存失败：${e.localizedMessage}" }
    }

    fun save(row: TransactionEntity, rememberedCategory: String? = null) = viewModelScope.launch {
        try {
            repository.save(row)
            if (rememberedCategory != null && row.name.isNotBlank()) repository.remember(row.name, rememberedCategory)
            message.value = "账单已保存"
        } catch (e: Exception) { message.value = "保存失败：${e.localizedMessage}" }
    }
    fun delete(id: Long) = viewModelScope.launch { repository.delete(id); message.value = "账单已删除" }
    fun setBudget(period: String, cents: Long) = viewModelScope.launch { repository.setBudget(period, cents); message.value = "预算已保存" }
    fun clear() = viewModelScope.launch { repository.clear(); message.value = "数据已清空" }
    suspend fun exportJson() = BackupCodec.json(repository.snapshot())
    suspend fun exportCsv() = BackupCodec.csv(repository.snapshot())
    suspend fun restore(text: String) {
        val parsed = BackupCodec.read(text)
        repository.restore(parsed)
        message.value = "已恢复 ${parsed.transactions.size} 笔账单"
    }
}
