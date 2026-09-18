package it.alantamanti.portshifttracker.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.alantamanti.portshifttracker.data.repository.PortRepository
import it.alantamanti.portshifttracker.domain.PerformanceType
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Composable
internal fun HistoryScreen(repository: PortRepository) {
    val context = LocalContext.current
    val zone = remember { ZoneId.of("Europe/Rome") }
    var startDate by remember { mutableStateOf(LocalDate.now().minusDays(90)) }
    var endDate by remember { mutableStateOf(LocalDate.now()) }
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<PerformanceType?>(null) }

    val startMillis = remember(startDate) {
        startDate.atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val endExclusive = remember(endDate) {
        endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val rowsFlow = remember(startMillis, endExclusive) {
        repository.shiftRowsBetween(startMillis, endExclusive)
    }
    val rows by rowsFlow.collectAsState(initial = emptyList())

    val filtered = remember(rows, query, typeFilter) {
        val needle = query.trim().lowercase(Locale.ITALIAN)
        rows.filter { row ->
            val typeOk = typeFilter == null || row.shift.performanceType == typeFilter
            val textOk = needle.isBlank() || buildString {
                append(row.shift.role)
                append(' ')
                append(row.shift.notes)
                append(' ')
                append(performanceLabel(row.shift.performanceType))
                append(' ')
                row.selectedRules.forEach {
                    append(it.name)
                    append(' ')
                    append(it.code)
                    append(' ')
                }
            }.lowercase(Locale.ITALIAN).contains(needle)
            typeOk && textOk
        }.sortedByDescending { it.shift.startEpochMillis }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Cerca mansione, indennità, note…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        startDate = LocalDate.of(y, m + 1, d).coerceAtMost(endDate)
                                    },
                                    startDate.year,
                                    startDate.monthValue - 1,
                                    startDate.dayOfMonth
                                ).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Dal ${startDate.dayOfMonth}/${startDate.monthValue}") }

                        OutlinedButton(
                            onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        endDate = LocalDate.of(y, m + 1, d).coerceAtLeast(startDate)
                                    },
                                    endDate.year,
                                    endDate.monthValue - 1,
                                    endDate.dayOfMonth
                                ).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Al ${endDate.dayOfMonth}/${endDate.monthValue}") }
                    }

                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = typeFilter == null,
                            onClick = { typeFilter = null },
                            label = { Text("Tutti") }
                        )
                        PerformanceType.entries.forEach { type ->
                            FilterChip(
                                selected = typeFilter == type,
                                onClick = { typeFilter = type },
                                label = { Text(performanceLabel(type)) }
                            )
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${filtered.size} risultati", style = MaterialTheme.typography.labelMedium)
                        Text(
                            money(filtered.sumOf { it.pay.totalPayCents }),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Text(
                    "Nessuna prestazione corrisponde ai filtri.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(filtered, key = { it.shift.id }) { row ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                italianTitle(rowDate(row).format(shortDayFormatter)),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(money(row.pay.totalPayCents), fontWeight = FontWeight.Bold)
                        }
                        Text(
                            performanceLabel(row.shift.performanceType) +
                                mainAllowanceName(row)?.let { " · $it" }.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (row.shift.role.isNotBlank()) {
                            Text(
                                row.shift.role,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val extras = row.selectedRules
                            .filterNot { it.name == mainAllowanceName(row) }
                            .take(5)
                            .joinToString(" · ") { it.name }
                        if (extras.isNotBlank()) {
                            Text(
                                extras,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
