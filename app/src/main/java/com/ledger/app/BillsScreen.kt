package com.ledger.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BillsScreen(state: LedgerState, vm: LedgerViewModel) {
    var period by remember { mutableStateOf("全部") }
    var query by remember { mutableStateOf("") }
    val selectedCategory by vm.billCategory.collectAsStateWithLifecycle()
    val selectedRange by vm.billRange.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TransactionEntity?>(null) }
    var deleting by remember { mutableStateOf<TransactionEntity?>(null) }
    val today = LocalDate.now()
    val start = when (period) {
        "日" -> today
        "周" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        "月" -> today.withDayOfMonth(1)
        "年" -> today.withDayOfYear(1)
        else -> LocalDate.MIN
    }.toEpochDay()
    val rows = state.transactions.filter { row ->
        (selectedRange?.let { row.dateEpochDay in it.first.toEpochDay()..it.second.toEpochDay() } ?: (row.dateEpochDay >= start)) && row.name.contains(query, ignoreCase = true) &&
            (selectedCategory == null || state.categories.firstOrNull { it.id == row.categoryId }?.name == selectedCategory)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("账单", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("按时间、分类或项目找到一笔记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("全部", "日", "周", "月", "年").forEachIndexed { index, option ->
                SegmentedButton(selected = period == option, onClick = { period = option }, shape = SegmentedButtonDefaults.itemShape(index, 5)) { Text(option) }
            }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("搜索项目") }, singleLine = true)
        selectedRange?.let { range -> InputChip(selected = true, onClick = { vm.billRange.value = null }, label = { Text("${range.first} ~ ${range.second}  ×") }) }
        Box {
            var open by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { open = true }) { Text("分类：${selectedCategory ?: "全部"}") }
            DropdownMenu(open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("全部") }, onClick = { vm.billCategory.value = null; open = false })
                state.categories.forEach { category -> DropdownMenuItem(text = { Text(category.name) }, onClick = { vm.billCategory.value = category.name; open = false }) }
            }
        }
        if (rows.isEmpty()) Box(Modifier.weight(1f)) { Text("这个范围内还没有账单。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.groupBy { it.date }.forEach { (date, dayRows) ->
                item { Text("${date.monthValue}月${date.dayOfMonth}日   ${money(dayRows.filter { it.type == "EXPENSE" }.sumOf { it.amountCents })}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                items(dayRows, key = { it.id }) { row ->
                    val category = state.categories.firstOrNull { it.id == row.categoryId }?.name ?: "其他"
                    Surface(Modifier.fillMaxWidth().combinedClickable(onClick = { editing = row }, onLongClick = { deleting = row }), shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
                        ListItem(headlineContent = { Text(row.name) }, supportingContent = { Text("$category${if (row.excludeFromDailyStats) " · 特殊支出" else ""}${if (row.note.isNotBlank()) " · ${row.note}" else ""}") }, trailingContent = { Text("${if (row.type == "INCOME") "+" else "-"}${money(row.amountCents)}") })
                    }
                }
            }
        }
    }
    editing?.let { row -> EditTransactionDialog(row, state.categories, onDismiss = { editing = null }, onSave = { changed, categoryName -> vm.save(changed, if (categoryName != state.categories.firstOrNull { it.id == row.categoryId }?.name) categoryName else null); editing = null }, onDelete = { editing = null; deleting = row }) }
    deleting?.let { row -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除账单？") }, text = { Text("${row.name} ${money(row.amountCents)} 删除后无法撤销。") }, confirmButton = { TextButton(onClick = { vm.delete(row.id); deleting = null }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
}

@Composable
private fun EditTransactionDialog(row: TransactionEntity, categories: List<CategoryEntity>, onDismiss: () -> Unit, onSave: (TransactionEntity, String) -> Unit, onDelete: () -> Unit) {
    var date by remember(row.id) { mutableStateOf(row.date.toString()) }
    var name by remember(row.id) { mutableStateOf(row.name) }
    var amount by remember(row.id) { mutableStateOf("%.2f".format(java.util.Locale.US, row.amountCents / 100.0)) }
    var note by remember(row.id) { mutableStateOf(row.note) }
    var category by remember(row.id) { mutableLongStateOf(row.categoryId) }
    var type by remember(row.id) { mutableStateOf(row.type) }
    var excluded by remember(row.id) { mutableStateOf(row.excludeFromDailyStats) }
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
    val cents = parseCents(amount)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("编辑账单") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(date, { date = it }, label = { Text("日期 YYYY-MM-DD") }, isError = parsedDate == null, singleLine = true)
            OutlinedTextField(name, { name = it }, label = { Text("项目") }, singleLine = true)
            OutlinedTextField(amount, { amount = it }, label = { Text("金额") }, isError = cents == null, singleLine = true)
            OutlinedTextField(note, { note = it }, label = { Text("备注") }, singleLine = true)
            Box {
                var expanded by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { expanded = true }) { Text(categories.firstOrNull { it.id == category }?.name ?: "分类") }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) { categories.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { category = c.id; expanded = false }) } }
            }
            Row { FilterChip(type == "EXPENSE", onClick = { type = "EXPENSE" }, label = { Text("支出") }); Spacer(Modifier.width(8.dp)); FilterChip(type == "INCOME", onClick = { type = "INCOME" }, label = { Text("收入") }) }
            Row { Checkbox(excluded, { excluded = it }); Text("排除出日常统计", modifier = Modifier.padding(top = 12.dp)) }
            TextButton(onClick = onDelete) { Text("删除这笔") }
        }
    }, confirmButton = {
        TextButton(enabled = parsedDate != null && cents != null && name.isNotBlank() && category > 0, onClick = {
            onSave(row.copy(dateEpochDay = parsedDate!!.toEpochDay(), name = name.trim(), amountCents = cents!!, categoryId = category, type = type, note = note, excludeFromDailyStats = excluded), categories.first { it.id == category }.name)
        }) { Text("保存") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
