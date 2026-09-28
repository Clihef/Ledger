package com.ledger.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmScreen(vm: LedgerViewModel, onBack: () -> Unit) {
    val rows by vm.draft.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("确认解析结果", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("检查每笔账单，修改后再一次记入。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(rows) { index, row ->
                var dateOpen by remember { mutableStateOf(false) }
                val datePicker = rememberDatePickerState(initialSelectedDateMillis = row.date.toEpochDay() * 86_400_000L)
                var amountText by remember(index, row.originalText) { mutableStateOf(java.math.BigDecimal.valueOf(row.amountCents, 2).toPlainString()) }
                ElevatedCard { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { dateOpen = true }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(row.date.toString()) }
                        OutlinedTextField(amountText, { amountText = it; vm.updateDraft(index, row.copy(amountCents = parseCents(it) ?: 0)) }, Modifier.weight(1f), label = { Text("金额") }, isError = parseCents(amountText) == null, singleLine = true)
                    }
                    OutlinedTextField(row.name, { vm.updateDraft(index, row.copy(name = it)) }, Modifier.fillMaxWidth(), label = { Text("项目") }, singleLine = true)
                    var expanded by remember { mutableStateOf(false) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(row.suggestedCategory) }
                            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                                CategoryRuleEngine.categories.forEach { category -> DropdownMenuItem(text = { Text(category) }, onClick = { vm.updateDraft(index, row.copy(suggestedCategory = category)); expanded = false }) }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            var typeOpen by remember { mutableStateOf(false) }
                            OutlinedButton(onClick = { typeOpen = true }, modifier = Modifier.fillMaxWidth()) { Text(if (row.type == EntryType.EXPENSE) "支出" else "收入") }
                            DropdownMenu(typeOpen, onDismissRequest = { typeOpen = false }) {
                                EntryType.entries.forEach { type -> DropdownMenuItem(text = { Text(if (type == EntryType.EXPENSE) "支出" else "收入") }, onClick = { vm.updateDraft(index, row.copy(type = type)); typeOpen = false }) }
                            }
                        }
                        TextButton(onClick = { vm.deleteDraft(index) }) { Text("删除") }
                    }
                } }
                if (dateOpen) DatePickerDialog(onDismissRequest = { dateOpen = false }, confirmButton = { TextButton(onClick = {
                    datePicker.selectedDateMillis?.let { vm.updateDraft(index, row.copy(date = LocalDate.ofEpochDay(it / 86_400_000L))) }
                    dateOpen = false
                }) { Text("确定") } }, dismissButton = { TextButton(onClick = { dateOpen = false }) { Text("取消") } }) { DatePicker(datePicker) }
            }
        }
        OutlinedButton(onClick = vm::addDraft, modifier = Modifier.fillMaxWidth()) { Text("新增一笔") }
        Button(onClick = { vm.saveDraft(onBack) }, modifier = Modifier.fillMaxWidth(), enabled = rows.isNotEmpty()) { Text("全部记入 · ${rows.size} 笔") }
    }
}
