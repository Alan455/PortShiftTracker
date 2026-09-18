package it.alantamanti.portshifttracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.data.local.AppFeatureStore
import it.alantamanti.portshifttracker.data.local.ShiftEntity
import it.alantamanti.portshifttracker.data.local.SpecialDayOverride
import it.alantamanti.portshifttracker.data.local.WorkerEntity
import it.alantamanti.portshifttracker.data.repository.DuplicatePerformanceException
import it.alantamanti.portshifttracker.data.repository.PortRepository
import it.alantamanti.portshifttracker.data.repository.normalizeSelectedRuleIds
import it.alantamanti.portshifttracker.data.repository.selectionValidationMessage
import it.alantamanti.portshifttracker.data.repository.ShiftWithPay
import it.alantamanti.portshifttracker.data.repository.toDomain
import it.alantamanti.portshifttracker.domain.AllowanceApplicationMode
import it.alantamanti.portshifttracker.domain.AllowanceCalculationType
import it.alantamanti.portshifttracker.domain.AllowanceCalculator
import it.alantamanti.portshifttracker.domain.AllowanceCategory
import it.alantamanti.portshifttracker.domain.BasePayMode
import it.alantamanti.portshifttracker.domain.PerformanceType
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

@Composable
internal fun SummaryScreen(repository: PortRepository) {
    val rules by repository.rules.collectAsState(initial = emptyList())
    var month by remember { mutableStateOf(YearMonth.now()) }
    val zone = remember { ZoneId.of("Europe/Rome") }

    val monthStart = remember(month) { month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val monthEnd = remember(month) { month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val monthRowsFlow = remember(monthStart, monthEnd) {
        repository.shiftRowsBetween(monthStart, monthEnd)
    }
    val monthRows by monthRowsFlow.collectAsState(initial = emptyList())

    val trendStart = remember(month) {
        month.minusMonths(5).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val trendRowsFlow = remember(trendStart, monthEnd) {
        repository.shiftRowsBetween(trendStart, monthEnd)
    }
    val trendRows by trendRowsFlow.collectAsState(initial = emptyList())

    val total = monthRows.sumOf { it.pay.totalPayCents }
    val baseTotal = monthRows.sumOf { it.pay.basePayCents }
    val allowanceTotal = monthRows.sumOf { it.pay.allowancesCents }
    val daysWorked = monthRows.map(::rowDate).distinct().size
    val groupedDays = monthRows.groupBy(::rowDate).toList().sortedByDescending { it.first }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Row(
                    Modifier.padding(10.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
                    Text(
                        italianTitle(month.format(monthFormatter)),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = { month = month.plusMonths(1) }) { Text("›") }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Totale mese", style = MaterialTheme.typography.labelLarge)
                    Text(
                        money(total),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Base ${money(baseTotal)}  •  Indennità ${money(allowanceTotal)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Giorni lavorati", daysWorked.toString(), Modifier.weight(1f))
                MetricCard("Prestazioni", monthRows.size.toString(), Modifier.weight(1f))
            }
        }

        item { StatisticsCard(month = month, monthRows = monthRows, trendRows = trendRows) }

        item { SectionHeader("Totali per tipo di prestazione") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PerformanceType.entries.forEach { type ->
                        val matching = monthRows.filter { it.shift.performanceType == type }
                        PerformanceTotalRow(type, matching.size, matching.sumOf { it.pay.totalPayCents })
                    }
                }
            }
        }

        item { PayslipComparisonCard(month = month, rows = monthRows, rules = rules) }
        item { MonthlyExportCard(month = month, rows = monthRows) }

        item { SectionHeader("Giornate del mese") }
        if (groupedDays.isEmpty()) {
            item { EmptySummaryCard() }
        } else {
            items(groupedDays, key = { it.first }) { (date, dayRows) ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(italianTitle(date.format(shortDayFormatter)), fontWeight = FontWeight.SemiBold)
                            Text(money(dayRows.sumOf { it.pay.totalPayCents }), fontWeight = FontWeight.Bold)
                        }
                        dayRows.forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    performanceLabel(row.shift.performanceType) +
                                        mainAllowanceName(row)?.let { " • $it" }.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(money(row.pay.totalPayCents), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

