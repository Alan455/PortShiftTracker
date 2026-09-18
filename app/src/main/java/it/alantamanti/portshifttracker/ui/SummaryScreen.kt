package it.alantamanti.portshifttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.data.local.AppFeatureStore
import it.alantamanti.portshifttracker.data.local.PayslipComparison
import it.alantamanti.portshifttracker.data.repository.PortRepository
import it.alantamanti.portshifttracker.data.repository.ShiftWithPay
import it.alantamanti.portshifttracker.domain.AllowanceCategory
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

@Composable
internal fun SummaryScreen(repository: PortRepository) {
    val rules by repository.rules.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val featureStore = remember(context) { AppFeatureStore(context) }
    val payslips by featureStore.payslipsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var month by remember { mutableStateOf(YearMonth.now()) }
    var showDetails by remember { mutableStateOf(false) }
    var showComparison by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }

    val zone = remember { ZoneId.of("Europe/Rome") }
    val monthStart = remember(month) { month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val monthEnd = remember(month) { month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val monthRowsFlow = remember(monthStart, monthEnd) {
        repository.shiftRowsBetween(monthStart, monthEnd)
    }
    val monthRows by monthRowsFlow.collectAsState(initial = emptyList())

    val previousMonth = remember(month) { month.minusMonths(1) }
    val previousStart = remember(previousMonth) {
        previousMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val previousEnd = remember(month) {
        month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val previousRowsFlow = remember(previousStart, previousEnd) {
        repository.shiftRowsBetween(previousStart, previousEnd)
    }
    val previousRows by previousRowsFlow.collectAsState(initial = emptyList())

    val total = monthRows.sumOf { it.pay.totalPayCents }
    val previousTotal = previousRows.sumOf { it.pay.totalPayCents }
    val daysWorked = monthRows.map(::rowDate).distinct().size
    val previousDaysWorked = previousRows.map(::rowDate).distinct().size
    val averagePerDay = if (daysWorked == 0) 0L else total / daysWorked
    val previousAverage = if (previousDaysWorked == 0) 0L else previousTotal / previousDaysWorked
    val groupedDays = monthRows.groupBy(::rowDate).toList().sortedByDescending { it.first }
    val totals = remember(monthRows, rules) { monthlyCategoryTotals(monthRows, rules) }
    val existingPayslip = payslips.firstOrNull { it.month == month.toString() }
    val isLocked = existingPayslip?.locked == true

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 14.dp,
            top = 12.dp,
            end = 14.dp,
            bottom = 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SummaryPeriodHeader(
                month = month,
                onPrevious = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) }
            )
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryKpiCard(
                    title = "Totale mese",
                    value = money(total),
                    delta = summaryDelta(total, previousTotal),
                    modifier = Modifier.weight(1f)
                )
                SummaryKpiCard(
                    title = "Prestazioni",
                    value = monthRows.size.toString(),
                    delta = summaryDelta(monthRows.size.toLong(), previousRows.size.toLong()),
                    modifier = Modifier.weight(1f)
                )
                SummaryKpiCard(
                    title = "Media/giorno",
                    value = money(averagePerDay),
                    delta = summaryDelta(averagePerDay, previousAverage),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            SummaryHeroTotal(
                month = month,
                total = total,
                previousTotal = previousTotal
            )
        }

        item {
            SummaryBreakdownCard(
                totals = totals,
                monthRows = monthRows,
                rules = rules
            )
        }

        item {
            SummaryMonthTrendCard(
                month = month,
                monthRows = monthRows
            )
        }

        item {
            SummaryActionsCard(
                showDetails = showDetails,
                showComparison = showComparison,
                showExport = showExport,
                locked = isLocked,
                onDetails = { showDetails = !showDetails },
                onCompare = { showComparison = !showComparison },
                onExport = { showExport = !showExport },
                onToggleLock = {
                    scope.launch {
                        val current = existingPayslip ?: PayslipComparison(month = month.toString())
                        featureStore.savePayslip(current.copy(locked = !isLocked))
                    }
                }
            )
        }

        if (showComparison) {
            item { PayslipComparisonCard(month = month, rows = monthRows, rules = rules) }
        }

        if (showExport) {
            item { MonthlyExportCard(month = month, rows = monthRows) }
        }

        if (showDetails) {
            item { SectionHeader("Giornate del mese") }
            if (groupedDays.isEmpty()) {
                item { EmptySummaryCard() }
            } else {
                items(groupedDays, key = { it.first }) { (date, dayRows) ->
                    SummaryDayCard(date, dayRows)
                }
            }
        }
    }
}

@Composable
private fun SummaryPeriodHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Riepilogo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        TextButton(onClick = onPrevious) { Text("‹") }
                        Text(
                            italianTitle(month.format(monthFormatter)),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        TextButton(onClick = onNext) { Text("›") }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text("Mese") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = false,
                    onClick = {},
                    enabled = false,
                    label = { Text("Anno") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = false,
                    onClick = {},
                    enabled = false,
                    label = { Text("Custom") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryKpiCard(
    title: String,
    value: String,
    delta: SummaryDelta,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                delta.label,
                style = MaterialTheme.typography.labelSmall,
                color = delta.color,
                textAlign = TextAlign.Center
            )
            Text(
                "vs. mese prec.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SummaryHeroTotal(
    month: YearMonth,
    total: Long,
    previousTotal: Long
) {
    val delta = summaryDelta(total, previousTotal)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Totale guadagno", fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.72f)
                ) {
                    Text(
                        italianTitle(month.format(monthFormatter)),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                money(total),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "${delta.label} rispetto al mese precedente (${money(previousTotal)})",
                style = MaterialTheme.typography.bodySmall,
                color = delta.color
            )
        }
    }
}

@Composable
private fun SummaryBreakdownCard(
    totals: MonthlyCategoryTotals,
    monthRows: List<ShiftWithPay>,
    rules: List<AllowanceRuleEntity>
) {
    val categoryRows = listOf(
        SummaryBreakdownItem("Base", totals.base, Color(0xFF2D7FF9)),
        SummaryBreakdownItem("Turno", totals.turno, Color(0xFF159A80)),
        SummaryBreakdownItem("Avviamento", totals.avviamento, Color(0xFFE17932)),
        SummaryBreakdownItem("Disagi", totals.disagio, Color(0xFFD95C69)),
        SummaryBreakdownItem("Area", totals.area, Color(0xFF7357D9)),
        SummaryBreakdownItem("Doppio", totals.doppio, Color(0xFFF2B01E))
    )

    val rulesById = rules.associateBy { it.id }
    val otherRows = monthRows
        .flatMap { it.pay.allowanceLines }
        .filter { line ->
            when (rulesById[line.ruleId]?.category) {
                AllowanceCategory.ALTRE_VOCI,
                AllowanceCategory.ALTRO,
                null -> true
                else -> false
            }
        }
        .groupBy { it.ruleId to it.name }
        .map { (key, lines) ->
            val rule = rulesById[key.first]
            SummaryOtherBreakdownItem(
                label = rule?.name ?: key.second,
                cents = lines.sumOf { it.amountCents },
                priority = rule?.priority ?: Int.MAX_VALUE
            )
        }
        .filter { it.cents != 0L }
        .sortedWith(
            compareBy<SummaryOtherBreakdownItem> { it.priority }
                .thenBy { it.label }
        )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                "Dettaglio per categoria",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            categoryRows.forEach { item ->
                SummaryBreakdownRow(
                    label = item.label,
                    cents = item.cents,
                    total = totals.total,
                    color = item.color
                )
            }

            if (otherRows.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Altre voci",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        money(otherRows.sumOf { it.cents }),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                otherRows.forEach { item ->
                    SummaryBreakdownRow(
                        label = item.label,
                        cents = item.cents,
                        total = totals.total,
                        color = Color(0xFF607D9B),
                        nested = true
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryBreakdownRow(
    label: String,
    cents: Long,
    total: Long,
    color: Color,
    nested: Boolean = false
) {
    val denominator = total.coerceAtLeast(1L)
    val ratio = (cents.toFloat() / denominator.toFloat()).coerceIn(0f, 1f)

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(
            Modifier
                .size(if (nested) 7.dp else 9.dp)
                .background(
                    if (nested) color.copy(alpha = 0.72f) else color,
                    CircleShape
                )
        )
        Text(
            label,
            modifier = Modifier.width(96.dp),
            style = if (nested) MaterialTheme.typography.labelMedium
            else MaterialTheme.typography.bodySmall,
            color = if (nested) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface
        )
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.weight(1f).height(if (nested) 5.dp else 7.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            money(cents),
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            summaryPercent(cents, total),
            modifier = Modifier.width(38.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class SummaryOtherBreakdownItem(
    val label: String,
    val cents: Long,
    val priority: Int
)

@Composable
private fun SummaryMonthTrendCard(
    month: YearMonth,
    monthRows: List<ShiftWithPay>
) {
    val byDay = monthRows.groupBy(::rowDate)
    val weekCount = ((month.lengthOfMonth() - 1) / 7) + 1
    val weeklyTotals = (1..weekCount).map { week ->
        week to byDay
            .filterKeys { date -> ((date.dayOfMonth - 1) / 7) + 1 == week }
            .values
            .flatten()
            .sumOf { it.pay.totalPayCents }
    }
    val maxWeek = weeklyTotals.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    val bestDay = byDay.maxByOrNull { (_, rows) -> rows.sumOf { it.pay.totalPayCents } }
    val mostUsed = monthRows
        .groupingBy { calendarDisplayLabel(it) }
        .eachCount()
        .maxByOrNull { it.value }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Andamento nel mese",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            weeklyTotals.forEach { (week, value) ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Sett $week",
                        modifier = Modifier.width(44.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                    LinearProgressIndicator(
                        progress = { (value.toFloat() / maxWeek.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f).height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        money(value),
                        modifier = Modifier.width(74.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryInsight(
                    title = "Giorno migliore",
                    value = bestDay?.let { (date, rows) ->
                        "${italianTitle(date.format(shortDayFormatter))}\n${money(rows.sumOf { it.pay.totalPayCents })}"
                    } ?: "—",
                    modifier = Modifier.weight(1f)
                )
                SummaryInsight(
                    title = "Prestazione più usata",
                    value = mostUsed?.let { "${it.key}\n${it.value} volte" } ?: "—",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryInsight(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) {
        Column(
            Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SummaryActionsCard(
    showDetails: Boolean,
    showComparison: Boolean,
    showExport: Boolean,
    locked: Boolean,
    onDetails: () -> Unit,
    onCompare: () -> Unit,
    onExport: () -> Unit,
    onToggleLock: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Azioni rapide",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                SummaryAction(
                    symbol = "≡",
                    label = if (showDetails) "Nascondi" else "Dettaglio",
                    selected = showDetails,
                    modifier = Modifier.weight(1f),
                    onClick = onDetails
                )
                SummaryAction(
                    symbol = "⇄",
                    label = "Confronta",
                    selected = showComparison,
                    modifier = Modifier.weight(1f),
                    onClick = onCompare
                )
                SummaryAction(
                    symbol = "⇩",
                    label = "Esporta",
                    selected = showExport,
                    modifier = Modifier.weight(1f),
                    onClick = onExport
                )
                SummaryAction(
                    symbol = if (locked) "□" else "▣",
                    label = if (locked) "Riapri" else "Chiudi",
                    selected = locked,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleLock
                )
            }
        }
    }
}

@Composable
private fun SummaryAction(
    symbol: String,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = if (selected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.padding(horizontal = 5.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                symbol,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = if (selected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryDayCard(
    date: LocalDate,
    dayRows: List<ShiftWithPay>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    italianTitle(date.format(shortDayFormatter)),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    money(dayRows.sumOf { it.pay.totalPayCents }),
                    fontWeight = FontWeight.Bold
                )
            }
            dayRows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        displayShiftLabel(row),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        money(row.pay.totalPayCents),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private data class SummaryBreakdownItem(
    val label: String,
    val cents: Long,
    val color: Color
)

private data class SummaryDelta(
    val label: String,
    val color: Color
)

private fun summaryDelta(current: Long, previous: Long): SummaryDelta {
    if (previous == 0L) {
        return if (current == 0L) {
            SummaryDelta("—", Color(0xFF607D9B))
        } else {
            SummaryDelta("Nuovo", Color(0xFF159A80))
        }
    }
    val percent = ((current - previous) * 100.0 / previous.toDouble())
    val prefix = if (percent > 0) "+" else ""
    val color = when {
        percent > 0 -> Color(0xFF159A80)
        percent < 0 -> Color(0xFFD95C69)
        else -> Color(0xFF607D9B)
    }
    return SummaryDelta(
        "$prefix${"%.0f".format(Locale.ITALY, percent)}%",
        color
    )
}

private fun summaryPercent(value: Long, total: Long): String {
    if (total <= 0L || value <= 0L) return "0%"
    val percent = value * 100.0 / total.toDouble()
    return if (percent < 1.0) "<1%" else "${"%.0f".format(Locale.ITALY, percent)}%"
}
