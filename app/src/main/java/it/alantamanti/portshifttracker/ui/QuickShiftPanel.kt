package it.alantamanti.portshifttracker.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val quickDateFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)

@Composable
internal fun EntryModeSelector(
    quickMode: Boolean,
    quickEnabled: Boolean,
    onQuick: () -> Unit,
    onDetailed: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Modalità inserimento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = quickMode && quickEnabled,
                    onClick = onQuick,
                    enabled = quickEnabled,
                    label = { Text("5 turni", textAlign = TextAlign.Center) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = !quickMode || !quickEnabled,
                    onClick = onDetailed,
                    label = { Text("Dettagliato", textAlign = TextAlign.Center) },
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                if (quickEnabled)
                    "5 turni sceglie automaticamente l'indennità di turno in base alla data; Dettagliato mantiene la selezione completa."
                else
                    "La modalità 5 turni è disponibile per il Turno normale. Doppio e Mezzo Doppio restano in modalità dettagliata.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun QuickShiftPanel(
    date: LocalDate,
    rules: List<AllowanceRuleEntity>,
    selectedKind: QuickShiftKind?,
    onKindSelected: (QuickShiftKind) -> Unit
) {
    val dayClass = portDayClass(date)
    val dayLabel = date.format(quickDateFormatter).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALIAN) else it.toString() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Turno rapido", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(dayLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        dayClass.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            val kinds = QuickShiftKind.entries
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                kinds.take(3).forEach { kind ->
                    QuickShiftTile(
                        kind = kind,
                        date = date,
                        rules = rules,
                        selected = selectedKind == kind,
                        modifier = Modifier.weight(1f),
                        onClick = { onKindSelected(kind) }
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                kinds.drop(3).forEach { kind ->
                    QuickShiftTile(
                        kind = kind,
                        date = date,
                        rules = rules,
                        selected = selectedKind == kind,
                        modifier = Modifier.weight(1f),
                        onClick = { onKindSelected(kind) }
                    )
                }
                Spacer(Modifier.weight(1f))
            }

            selectedKind?.let { kind ->
                val resolution = resolveQuickShift(kind, date)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                ) {
                    Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Selezionato: ${resolution.compactCode}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            resolution.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickShiftTile(
    kind: QuickShiftKind,
    date: LocalDate,
    rules: List<AllowanceRuleEntity>,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val resolution = resolveQuickShift(kind, date)
    val rule = rules.firstOrNull { it.code == resolution.ruleCode && it.enabled }
    val shape = RoundedCornerShape(14.dp)

    Surface(
        modifier = modifier
            .heightIn(min = 88.dp)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .clickable(enabled = rule != null, onClick = onClick),
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(kind.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(3.dp))
            Text(
                resolution.compactCode,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                rule?.let { moneyQuick(it.value) } ?: "non disponibile",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun moneyQuick(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.ITALY).format(cents / 100.0)
