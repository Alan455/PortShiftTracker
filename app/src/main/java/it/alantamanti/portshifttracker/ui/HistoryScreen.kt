package it.alantamanti.portshifttracker.ui

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.alantamanti.portshifttracker.data.repository.PortRepository
import it.alantamanti.portshifttracker.data.repository.ShiftWithPay
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
    var typeFilter by remember { mutableStateOf<HistoryCategory?>(null) }

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
            val typeOk = typeFilter == null || historyCategory(row) == typeFilter
            val textOk = needle.isBlank() || buildString {
                append(row.shift.role)
                append(' ')
                append(row.shift.notes)
                append(' ')
                append(performanceLabel(row.shift.performanceType))
                append(' ')
                append(historyCategory(row).label)
                append(' ')
                row.selectedRules.forEach {
                    append(historicalAllowanceName(row, it))
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
                        HistoryCategory.entries.forEach { category ->
                            FilterChip(
                                selected = typeFilter == category,
                                onClick = { typeFilter = category },
                                label = { Text(category.label) }
                            )
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedContent(
                            targetState = filtered.size,
                            transitionSpec = {
                                fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                            },
                            label = "Risultati storico"
                        ) { count ->
                            Text("$count risultati", style = MaterialTheme.typography.labelMedium)
                        }
                        AnimatedContent(
                            targetState = filtered.sumOf { it.pay.totalPayCents },
                            transitionSpec = {
                                fadeIn(tween(190)) togetherWith fadeOut(tween(120))
                            },
                            label = "Totale storico filtrato"
                        ) { totalCents ->
                            Text(
                                money(totalCents),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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
            // "Tutti" mostra cinque gruppi separati; il filtro mostra un solo gruppo.
            // Ogni prestazione appartiene a una sola categoria e viene renderizzata una volta.
            val groups = if (typeFilter == null) HistoryCategory.entries.toList()
                else listOfNotNull(typeFilter)
            groups.forEach { category ->
                val categoryRows = filtered.filter { historyCategory(it) == category }
                if (categoryRows.isNotEmpty()) {
                    item(key = "heading_${category.name}") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .animateItem(
                                    fadeInSpec = tween(180),
                                    placementSpec = tween(220),
                                    fadeOutSpec = tween(120)
                                )
                                .padding(top = 8.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${category.label} (${categoryRows.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                money(categoryRows.sumOf { it.pay.totalPayCents }),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    items(categoryRows, key = { it.shift.id }) { row ->
                        HistoryEntryCard(
                            row = row,
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(180),
                                placementSpec = tween(220),
                                fadeOutSpec = tween(120)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(
    row: ShiftWithPay,
    modifier: Modifier = Modifier
) {
    var showDetails by remember(row.shift.id) { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
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
            Text(displayShiftLabel(row), style = MaterialTheme.typography.bodyMedium)
            val extras = displayShiftExtras(row).take(5).joinToString(" · ")
            if (extras.isNotBlank()) {
                Text(
                    extras,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                modifier = Modifier.align(Alignment.End),
                onClick = { showDetails = !showDetails }
            ) {
                Text(if (showDetails) "Nascondi dettaglio" else "Vedi dettaglio")
            }
            AnimatedVisibility(
                visible = showDetails,
                enter = fadeIn(tween(180)) + expandVertically(tween(240)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(180))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    HorizontalDivider()
                    HistoryPayLine("Base", row.pay.basePayCents)
                    row.pay.allowanceLines.forEach { line ->
                        HistoryPayLine(line.name, line.amountCents)
                    }
                    HorizontalDivider()
                    HistoryPayLine("Totale", row.pay.totalPayCents)
                }
            }
        }
    }
}

@Composable
private fun HistoryPayLine(label: String, cents: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(money(cents), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
