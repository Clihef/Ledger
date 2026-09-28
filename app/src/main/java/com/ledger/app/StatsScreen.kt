package com.ledger.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private data class TrendPoint(val label: String, val amount: Long, val range: DateRange)

@Composable
fun StatsScreen(state: LedgerState, onSelection: (String?, Pair<LocalDate, LocalDate>) -> Unit) {
    var period by rememberSaveable { mutableStateOf(StatsPeriod.WEEK) }
    var anchor by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var customStart by rememberSaveable { mutableStateOf(LocalDate.now().minusDays(29).toString()) }
    var customEnd by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var dailyOnly by rememberSaveable { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val customFrom = runCatching { LocalDate.parse(customStart) }.getOrNull()
    val customTo = runCatching { LocalDate.parse(customEnd) }.getOrNull()
    val range = if (period == StatsPeriod.CUSTOM) {
        if (customFrom != null && customTo != null && !customTo.isBefore(customFrom)) DateRange(customFrom, customTo) else null
    } else periodRange(period, LocalDate.parse(anchor))
    val expenses = state.transactions.filter { it.type == EntryType.EXPENSE.name }
    val palette = categoryColors(androidx.compose.foundation.isSystemInDarkTheme())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("消费统计", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("按时间回看每一笔花费", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            StatsPeriod.entries.forEachIndexed { index, value ->
                SegmentedButton(selected = period == value, onClick = {
                    period = value
                    anchor = today.toString()
                    pickerOpen = false
                }, shape = SegmentedButtonDefaults.itemShape(index, StatsPeriod.entries.size)) { Text(value.label) }
            }
        }
        if (period == StatsPeriod.CUSTOM) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(customStart, { customStart = it }, Modifier.weight(1f), label = { Text("开始日期") }, supportingText = { Text("年-月-日") }, isError = customFrom == null, singleLine = true)
                OutlinedTextField(customEnd, { customEnd = it }, Modifier.weight(1f), label = { Text("结束日期") }, supportingText = { Text("年-月-日") }, isError = customTo == null || (customFrom != null && customTo.isBefore(customFrom)), singleLine = true)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { anchor = shiftPeriod(period, LocalDate.parse(anchor), -1).toString() }, modifier = Modifier.size(48.dp)) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
                OutlinedButton(onClick = { pickerOpen = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(periodLabel(period, range!!), maxLines = 1) }
                IconButton(onClick = { anchor = shiftPeriod(period, LocalDate.parse(anchor), 1).toString() }, enabled = range!!.start.isBefore(periodRange(period, today).start), modifier = Modifier.size(48.dp)) { Text("›", style = MaterialTheme.typography.headlineMedium) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !dailyOnly, onClick = { dailyOnly = false }, label = { Text("全部消费") })
            FilterChip(selected = dailyOnly, onClick = { dailyOnly = true }, label = { Text("日常消费") })
        }

        if (range == null) {
            Text("请输入有效日期，结束日期不能早于开始日期。", color = MaterialTheme.colorScheme.error)
        } else {
            val all = expenses.filter { it.date in range }
            val rows = if (dailyOnly) all.filterNot { it.excludeFromDailyStats } else all
            val total = rows.sumOf { it.amountCents }
            val special = all.filter { it.excludeFromDailyStats }.sumOf { it.amountCents }
            val byDay = rows.groupBy { it.date }.mapValues { (_, group) -> group.sumOf { it.amountCents } }
            val topDay = byDay.maxByOrNull { it.value }
            val byCategory = rows.groupBy { row -> state.categories.firstOrNull { it.id == row.categoryId }?.name ?: "其他" }
                .mapValues { (_, group) -> group.sumOf { it.amountCents } }.toList().sortedByDescending { it.second }
            val previous = if (period == StatsPeriod.CUSTOM) {
                val length = ChronoUnit.DAYS.between(range.start, range.end) + 1
                DateRange(range.start.minusDays(length), range.start.minusDays(1))
            } else periodRange(period, shiftPeriod(period, range.start, -1))
            val previousTotal = expenses.filter { it.date in previous && (!dailyOnly || !it.excludeFromDailyStats) }.sumOf { it.amountCents }

            ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${period.label}期支出", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(money(total), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(if (previousTotal == 0L) "上一期暂无支出" else "较上一期${if (total >= previousTotal) "增加" else "减少"} ${money(kotlin.math.abs(total - previousTotal))}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    Text("总支出 ${money(all.sumOf { it.amountCents })}    特殊支出 ${money(special)}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("日均 ${money(total / range.daysThrough(today))}    最高消费日 ${topDay?.let { "${it.key.monthValue}月${it.key.dayOfMonth}日 ${money(it.value)}" } ?: "暂无"}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            if (rows.isEmpty()) {
                ElevatedCard(Modifier.fillMaxWidth()) { Text("这个时间段还没有支出。可切换时间或查看全部消费。", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val trend = trendPoints(range, byDay)
                val monthlyAxis = trend.firstOrNull()?.range?.start != trend.firstOrNull()?.range?.end
                ChartCard(if (monthlyAxis) "每月支出趋势" else "每日支出趋势", "点按柱形查看对应账单") {
                    BarChart(trend, MaterialTheme.colorScheme.primary, onSelect = { onSelection(null, it.range.start to it.range.end) })
                }
                ChartCard("分类消费", "点按分类查看对应账单") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        DonutChart(byCategory.map { it.second }, palette, Modifier.size(136.dp)) { index ->
                            byCategory.getOrNull(index)?.let { onSelection(it.first, range.start to range.end) }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            byCategory.take(3).forEachIndexed { index, (name, amount) ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Surface(Modifier.size(9.dp), shape = CircleShape, color = palette[index % palette.size]) {}
                                    Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Text("${amount * 100 / total}%", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    byCategory.forEachIndexed { index, (name, amount) ->
                        Column(Modifier.fillMaxWidth().clickable { onSelection(name, range.start to range.end) }.padding(vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(Modifier.size(10.dp), shape = CircleShape, color = palette[index % palette.size]) {}
                                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(money(amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                            LinearProgressIndicator(progress = { amount.toFloat() / byCategory.first().second }, modifier = Modifier.fillMaxWidth(), color = palette[index % palette.size], trackColor = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
                if (period != StatsPeriod.CUSTOM) {
                    val comparison = (5 downTo 0).map { offset ->
                        val itemRange = periodRange(period, shiftPeriod(period, range.start, -offset.toLong()))
                        val shortLabel = when (period) {
                            StatsPeriod.WEEK -> "${itemRange.start.monthValue}/${itemRange.start.dayOfMonth}"
                            StatsPeriod.MONTH -> "${itemRange.start.monthValue}月"
                            StatsPeriod.YEAR -> "${itemRange.start.year}"
                            StatsPeriod.CUSTOM -> ""
                        }
                        TrendPoint(shortLabel, expenses.filter { it.date in itemRange && (!dailyOnly || !it.excludeFromDailyStats) }.sumOf { it.amountCents }, itemRange)
                    }
                    ChartCard("相邻${period.label}期对比", "点按柱形查看该期账单") {
                        BarChart(comparison, MaterialTheme.colorScheme.tertiary, onSelect = { onSelection(null, it.range.start to it.range.end) })
                    }
                }
                ChartCard("消费洞察", null) {
                    Text("${periodLabel(period, range)}共支出 ${money(total)}，日均 ${money(total / range.daysThrough(today))}。")
                    val top = byCategory.first()
                    Text("${top.first}占比最高，为 ${top.second * 100 / total}%。")
                    val rides = rows.filter { it.name.contains("打车") || it.name.contains("滴滴") }
                    if (rides.isNotEmpty()) Text("打车 ${rides.size} 次，共 ${money(rides.sumOf { it.amountCents })}。")
                }
            }
        }
    }

    if (pickerOpen && period != StatsPeriod.CUSTOM) {
        val options = periodChoices(period, today, expenses.minOfOrNull { it.date })
        AlertDialog(onDismissRequest = { pickerOpen = false }, title = { Text("选择${period.label}期") }, text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(options, key = { it.start.toEpochDay() }) { option ->
                    ListItem(headlineContent = { Text(periodLabel(period, option)) }, supportingContent = { if (option.start == periodRange(period, today).start) Text("当前${period.label}期") }, modifier = Modifier.fillMaxWidth().clickable { anchor = option.start.toString(); pickerOpen = false })
                }
            }
        }, confirmButton = { TextButton(onClick = { pickerOpen = false }) { Text("关闭") } })
    }
}

private fun trendPoints(range: DateRange, amounts: Map<LocalDate, Long>): List<TrendPoint> {
    val byMonth = ChronoUnit.DAYS.between(range.start, range.end) > 62
    val result = mutableListOf<TrendPoint>()
    var cursor = range.start
    while (!cursor.isAfter(range.end)) {
        val end = if (byMonth) minOf(cursor.withDayOfMonth(1).plusMonths(1).minusDays(1), range.end) else cursor
        val span = DateRange(cursor, end)
        result += TrendPoint(if (byMonth) "${cursor.monthValue}月" else "${cursor.monthValue}/${cursor.dayOfMonth}", amounts.filterKeys { it in span }.values.sum(), span)
        cursor = end.plusDays(1)
    }
    return result
}

@Composable
private fun ChartCard(title: String, hint: String?, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun BarChart(points: List<TrendPoint>, color: Color, onSelect: (TrendPoint) -> Unit) {
    if (points.isEmpty()) return
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(132.dp).semantics { contentDescription = "支出趋势，点按柱形查看对应账单" }.pointerInput(points) {
        detectTapGestures { tap -> onSelect(points[(tap.x / size.width * points.size).toInt().coerceIn(0, points.lastIndex)]) }
    }) {
        val max = points.maxOf { it.amount }.coerceAtLeast(1L).toFloat()
        val slot = size.width / points.size
        val barWidth = (slot * 0.62f).coerceAtLeast(2.dp.toPx())
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        drawLine(grid.copy(alpha = 0.5f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx())
        points.forEachIndexed { index, point ->
            if (point.amount > 0L) {
                val height = (point.amount / max * (size.height - 10.dp.toPx())).coerceAtLeast(3.dp.toPx())
                drawRoundRect(color, topLeft = Offset(index * slot + (slot - barWidth) / 2, size.height - height), size = Size(barWidth, height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            }
        }
    }
    val ticks = if (points.size <= 7) points.indices.toList() else listOf(0, points.lastIndex / 4, points.lastIndex / 2, points.lastIndex * 3 / 4, points.lastIndex).distinct()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ticks.forEach { index -> Text(points[index].label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun DonutChart(values: List<Long>, colors: List<Color>, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "分类消费占比；下方列表提供分类、金额和颜色" }.pointerInput(values) {
            detectTapGestures { tap ->
                val angle = ((Math.toDegrees(kotlin.math.atan2((tap.y - size.height / 2).toDouble(), (tap.x - size.width / 2).toDouble())) + 450) % 360).toFloat()
                val total = values.sum().coerceAtLeast(1L)
                var swept = 0f
                values.forEachIndexed { index, value ->
                    swept += value * 360f / total
                    if (angle <= swept) { onSelect(index); return@detectTapGestures }
                }
            }
        }) {
            val total = values.sum().coerceAtLeast(1L)
            var start = -90f
            values.forEachIndexed { index, value ->
                val sweep = value * 360f / total
                drawArc(colors[index % colors.size], start, sweep, useCenter = false, style = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Butt))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${values.size}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("个分类", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
