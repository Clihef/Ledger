package com.ledger.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Composable
fun MeScreen(state: LedgerState, vm: LedgerViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var clearConfirm by remember { mutableStateOf(false) }
    var budgetPeriod by remember { mutableStateOf<String?>(null) }
    var budgetInput by remember { mutableStateOf("") }
    val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                requireNotNull(context.contentResolver.openOutputStream(uri)) { "无法打开目标文件" }.bufferedWriter(Charsets.UTF_8).use { it.write(vm.exportJson()) }
                vm.message.value = "导出完成"
            } catch (e: Exception) { vm.message.value = "导出失败：${e.localizedMessage}" }
        }
    }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            try {
                requireNotNull(context.contentResolver.openOutputStream(uri)) { "无法打开目标文件" }.bufferedWriter(Charsets.UTF_8).use { it.write(vm.exportCsv()) }
                vm.message.value = "导出完成"
            } catch (e: Exception) { vm.message.value = "导出失败：${e.localizedMessage}" }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try { pendingRestore = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } }
            catch (e: Exception) { vm.message.value = "读取失败：${e.localizedMessage}" }
        }
    }
    val today = LocalDate.now()
    val expenses = state.transactions.filter { it.type == "EXPENSE" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("我的", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("管理预算与本机数据", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("预算", style = MaterialTheme.typography.titleLarge)
        listOf("日" to today, "周" to today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), "月" to today.withDayOfMonth(1)).forEach { (period, start) ->
            val budget = state.budgets.firstOrNull { it.period == period }?.amountCents ?: 0
            val spent = expenses.filter { it.dateEpochDay >= start.toEpochDay() }.sumOf { it.amountCents }
            ElevatedCard(onClick = { budgetPeriod = period; budgetInput = if (budget > 0) "%.2f".format(java.util.Locale.US, budget / 100.0) else "" }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("每${period}预算", style = MaterialTheme.typography.titleMedium)
                    Text(if (budget == 0L) "点击设置" else "${money(spent)} / ${money(budget)}    剩余 ${money((budget - spent).coerceAtLeast(0))}")
                    if (budget > 0) {
                        LinearProgressIndicator(progress = { (spent.toFloat() / budget).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        if (spent >= budget * 0.8) Text(if (spent >= budget) "已超出预算" else "即将达到预算", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        HorizontalDivider()
        Text("数据管理", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { createCsv.launch("简记账单.csv") }, modifier = Modifier.fillMaxWidth()) { Text("导出 CSV") }
        OutlinedButton(onClick = { createJson.launch("简记备份.json") }, modifier = Modifier.fillMaxWidth()) { Text("导出 JSON 备份") }
        OutlinedButton(onClick = { open.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("从 JSON 恢复") }
        TextButton(onClick = { clearConfirm = true }) { Text("清空全部数据", color = MaterialTheme.colorScheme.error) }
        Text("数据保存在本机。请定期将 JSON 备份保存到安全位置。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    budgetPeriod?.let { period -> AlertDialog(onDismissRequest = { budgetPeriod = null }, title = { Text("设置每${period}预算") }, text = { OutlinedTextField(budgetInput, { budgetInput = it }, label = { Text("金额") }, isError = budgetInput.isNotEmpty() && parseCents(budgetInput) == null, singleLine = true) }, confirmButton = { TextButton(enabled = parseCents(budgetInput) != null, onClick = { vm.setBudget(period, parseCents(budgetInput)!!); budgetPeriod = null }) { Text("保存") } }, dismissButton = { TextButton(onClick = { budgetPeriod = null }) { Text("取消") } }) }
    pendingRestore?.let { text -> AlertDialog(onDismissRequest = { pendingRestore = null }, title = { Text("覆盖现有数据？") }, text = { Text("恢复备份会替换当前所有账单、分类规则和预算。此操作无法撤销。") }, confirmButton = { TextButton(onClick = { pendingRestore = null; scope.launch { try { vm.restore(text) } catch (e: Exception) { vm.message.value = "恢复失败：${e.localizedMessage}" } } }) { Text("确认恢复") } }, dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("取消") } }) }
    if (clearConfirm) AlertDialog(onDismissRequest = { clearConfirm = false }, title = { Text("清空全部数据？") }, text = { Text("所有账单、分类规则和预算都将删除，无法撤销。建议先导出 JSON 备份。") }, confirmButton = { TextButton(onClick = { vm.clear(); clearConfirm = false }) { Text("确认清空") } }, dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("取消") } })
}
