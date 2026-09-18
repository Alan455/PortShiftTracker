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
import it.alantamanti.portshifttracker.domain.PerformanceType
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val quickDateFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)

@Composable
internal fun QuickShiftPanel(
    date: LocalDate,
    rules: List<AllowanceRuleEntity>,
    selectedKind: QuickShiftKind?,
    performanceType: PerformanceType = PerformanceType.TURNO,
    doubleBaseCents: Long? = null,
    overrideClass: PortDayClass? = null,
    onKindSelected: (QuickShiftKind) -> Unit
) {
    val dayClass = overrideClass ?: portDayClass(date)
    val dayLabel = date.format(quickDateFormatter).replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.ITALIAN) else it.toString()
    }
    val kinds = quickShiftKindsFor(performanceType)

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
                    Text(
                        if (performanceType == PerformanceType.DOPPIO) "Doppio: scegli il turno" else "Turno rapido",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(dayLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (performanceType == PerformanceType.DOPPIO && doubleBaseCents != null) {
                        Text(
                            "Base ${moneyQuick(doubleBaseCents)} + modificatore automatico",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (overrideClass != null) {
                        Text(
                            "Calendario speciale",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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

            kinds.chunked(3).forEach { rowKinds ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowKinds.forEach { kind ->
                        QuickShiftTile(
                            kind = kind,
                            date = date,
                            rules = rules,
                            performanceType = performanceType,
                            selected = selectedKind == kind,
                            overrideClass = overrideClass,
                            modifier = Modifier.weight(1f),
                            onClick = { onKindSelected(kind) }
                        )
                    }
                    repeat((3 - rowKinds.size).coerceAtLeast(0)) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            selectedKind?.takeIf { it in kinds }?.let { kind ->
                val resolution = resolveQuickShift(kind, date, overrideClass, performanceType)
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
    performanceType: PerformanceType,
    selected: Boolean,
    overrideClass: PortDayClass?,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val resolution = resolveQuickShift(kind, date, overrideClass, performanceType)
    val rule = rules.firstOrNull {
        it.code == resolution.ruleCode &&
            it.enabled &&
            (it.performanceMask and performanceType.maskBit) != 0
    }
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
                rule?.let {
                    val amount = moneyQuick(it.value)
                    if (performanceType == PerformanceType.DOPPIO) "+$amount" else amount
                } ?: "non disponibile",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun moneyQuick(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale.ITALY).format(cents / 100.0)
