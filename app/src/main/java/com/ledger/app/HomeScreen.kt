package com.ledger.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Composable
fun HomeScreen(state: LedgerState, vm: LedgerViewModel, onConfirm: () -> Unit, onBills: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val today = LocalDate.now()
    val month = today.withDayOfMonth(1).toEpochDay()
    val week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
    val expenses = state.transactions.filter { it.type == EntryType.EXPENSE.name }
    val monthly = expenses.filter { it.dateEpochDay >= month }.sumOf { it.amountCents }
    val previous = expenses.filter { it.dateEpochDay >= today.minusMonths(1).withDayOfMonth(1).toEpochDay() && it.dateEpochDay < month }.sumOf { it.amountCents }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize(), tint = androidx.compose.ui.graphics.Color.Unspecified)
            }
            Column {
                Text("简记", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("今天也记得明明白白", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("本月支出", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(money(monthly), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("较上月整月${if (monthly >= previous) "增加" else "减少"} ${money(kotlin.math.abs(monthly - previous))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) { Text("今日", style = MaterialTheme.typography.labelMedium); Text(money(expenses.filter { it.dateEpochDay == today.toEpochDay() }.sumOf { it.amountCents }), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium) }
                Column(Modifier.weight(1f)) { Text("本周", style = MaterialTheme.typography.labelMedium); Text(money(expenses.filter { it.dateEpochDay >= week }.sumOf { it.amountCents }), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium) }
            }
        } }
        Text("快速记账", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp), label = { Text("输入或粘贴账单") }, placeholder = { Text("9.20\n早餐面包6.5\n午饭16\n地铁8") }, supportingText = { Text("日期写一次即可，后续账单自动沿用") }, minLines = 5)
        Button(onClick = { if (vm.parse(input)) onConfirm() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = input.isNotBlank()) { Text("解析账单") }
        Text("最近账单", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (state.transactions.isEmpty()) Text("还没有账单，试着输入一笔吧。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.transactions.take(5).forEach { row -> ListItem(headlineContent = { Text(row.name) }, supportingContent = { Text("${row.date.monthValue}月${row.date.dayOfMonth}日 · ${state.categories.firstOrNull { it.id == row.categoryId }?.name ?: "其他"}") }, trailingContent = { Text(money(row.amountCents)) }) }
        TextButton(onClick = onBills) { Text("查看全部账单") }
    }
}
