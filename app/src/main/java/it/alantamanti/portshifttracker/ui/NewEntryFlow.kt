package it.alantamanti.portshifttracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.alantamanti.portshifttracker.data.repository.ShiftWithPay
import it.alantamanti.portshifttracker.domain.PerformanceType
import java.time.LocalDate

/**
 * Scelte del nuovo flusso guidato. L'editor riceve già la tipologia decisa,
 * quindi non deve più mostrare tutte le alternative contemporaneamente.
 */
internal enum class GuidedEntryKind {
    FIRST_TURNO,
    FIRST_GIORNALIERO,
    ABS_FERIE,
    ABS_MALATTIA,
    ABS_CONGEDO,
    ABS_IMA,
    SECOND_DOPPIO,
    SECOND_MEZZO_DOPPIO,
    SECOND_MEZZO_GIORNALIERO
}

internal fun GuidedEntryKind.initialPerformanceType(): PerformanceType = when (this) {
    GuidedEntryKind.FIRST_TURNO,
    GuidedEntryKind.FIRST_GIORNALIERO,
    GuidedEntryKind.ABS_FERIE,
    GuidedEntryKind.ABS_MALATTIA,
    GuidedEntryKind.ABS_CONGEDO,
    GuidedEntryKind.ABS_IMA -> PerformanceType.TURNO
    GuidedEntryKind.SECOND_DOPPIO,
    GuidedEntryKind.SECOND_MEZZO_GIORNALIERO -> PerformanceType.DOPPIO
    GuidedEntryKind.SECOND_MEZZO_DOPPIO -> PerformanceType.MEZZO_DOPPIO
}

internal fun GuidedEntryKind.initialRuleCodes(): Set<String> = when (this) {
    GuidedEntryKind.FIRST_GIORNALIERO -> setOf("G")
    GuidedEntryKind.ABS_FERIE -> setOf("ALT_FERIE")
    GuidedEntryKind.ABS_MALATTIA -> setOf("ALT_MALATTIA")
    GuidedEntryKind.ABS_CONGEDO -> setOf("AVV_CONGEDO")
    GuidedEntryKind.ABS_IMA -> setOf("ALT_IMA")
    GuidedEntryKind.SECOND_MEZZO_GIORNALIERO -> setOf("DOP_G")
    else -> emptySet()
}

internal fun GuidedEntryKind.isAbsence(): Boolean = this in setOf(
    GuidedEntryKind.ABS_FERIE,
    GuidedEntryKind.ABS_MALATTIA,
    GuidedEntryKind.ABS_CONGEDO,
    GuidedEntryKind.ABS_IMA
)

private enum class GuidedStage { ROOT, WORK, ABSENCE, SECOND, BLOCKED }

private val absenceCodes = setOf(
    "ALT_FERIE", "ALT_MALATTIA", "ALT_IMA", "AVV_DS", "AVV_INAIL", "AVV_CONGEDO"
)

@Composable
internal fun NewEntryDecisionFlow(
    date: LocalDate,
    dayRows: List<ShiftWithPay>,
    onDismiss: () -> Unit,
    onChosen: (GuidedEntryKind) -> Unit
) {
    val hasAbsence = dayRows.any { row -> row.selectedRules.any { it.code in absenceCodes } }
    val hasFirstWork = dayRows.any { row ->
        row.shift.performanceType == PerformanceType.TURNO &&
            row.selectedRules.none { it.code in absenceCodes }
    }
    val hasSecond = dayRows.any {
        it.shift.performanceType == PerformanceType.DOPPIO ||
            it.shift.performanceType == PerformanceType.MEZZO_DOPPIO
    }

    val initialStage = when {
        hasAbsence || hasSecond -> GuidedStage.BLOCKED
        hasFirstWork -> GuidedStage.SECOND
        else -> GuidedStage.ROOT
    }
    var stage by remember(date, dayRows.map { it.shift.id }) { mutableStateOf(initialStage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Nuova prestazione", fontWeight = FontWeight.Bold)
                Text(
                    italianTitle(date.format(shortDayFormatter)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (stage) {
                    GuidedStage.ROOT -> {
                        Text("Cosa vuoi inserire?", style = MaterialTheme.typography.titleMedium)
                        FlowChoice(
                            icon = "▣",
                            title = "Turno / Lavoro",
                            subtitle = "Turno oppure Giornaliero",
                            tint = MaterialTheme.colorScheme.primaryContainer,
                            onClick = { stage = GuidedStage.WORK }
                        )
                        FlowChoice(
                            icon = "▰",
                            title = "Assenza",
                            subtitle = "Ferie, Malattia, Congedo, IMA",
                            tint = Color(0xFFFFE7EA),
                            onClick = { stage = GuidedStage.ABSENCE }
                        )
                    }

                    GuidedStage.WORK -> {
                        Text("Che tipo di prestazione?", style = MaterialTheme.typography.titleMedium)
                        FlowChoice(
                            icon = "T",
                            title = "Turno",
                            subtitle = "Mattina, Pomeriggio, Sera, Sera2, Notte",
                            tint = MaterialTheme.colorScheme.primaryContainer,
                            onClick = { onChosen(GuidedEntryKind.FIRST_TURNO) }
                        )
                        FlowChoice(
                            icon = "G",
                            title = "Giornaliero",
                            subtitle = "Base € 90,00 · Polivalenza automatica",
                            tint = Color(0xFFE1F6ED),
                            onClick = { onChosen(GuidedEntryKind.FIRST_GIORNALIERO) }
                        )
                    }

                    GuidedStage.ABSENCE -> {
                        Text("Quale assenza?", style = MaterialTheme.typography.titleMedium)
                        FlowChoice("Ff", "Ferie", "Registra ferie", Color(0xFFE8F5E9)) {
                            onChosen(GuidedEntryKind.ABS_FERIE)
                        }
                        FlowChoice("Mm", "Malattia", "Registra malattia", Color(0xFFE3F2FD)) {
                            onChosen(GuidedEntryKind.ABS_MALATTIA)
                        }
                        FlowChoice("PC", "Congedo", "Registra congedo", Color(0xFFEDE7F6)) {
                            onChosen(GuidedEntryKind.ABS_CONGEDO)
                        }
                        FlowChoice("I", "IMA", "Eventuale Disdetta casa / festiva", Color(0xFFFFF3E0)) {
                            onChosen(GuidedEntryKind.ABS_IMA)
                        }
                    }

                    GuidedStage.SECOND -> {
                        Text("Seconda prestazione", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Il primo turno è già presente: puoi inserire soltanto il Doppio.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowChoice("2×", "Doppio completo", "Base intera · indennità turno intere", Color(0xFFE8EAF6)) {
                            onChosen(GuidedEntryKind.SECOND_DOPPIO)
                        }
                        FlowChoice("½×", "Mezzo Doppio", "Base metà Doppio · Pom/Sera/S2/Notte al 50%", Color(0xFFFFF3E0)) {
                            onChosen(GuidedEntryKind.SECOND_MEZZO_DOPPIO)
                        }
                        FlowChoice("½G", "Mezzo Giornaliero", "Base € 45,00 · nessuna Mezza IMA", Color(0xFFE1F6ED)) {
                            onChosen(GuidedEntryKind.SECOND_MEZZO_GIORNALIERO)
                        }
                    }

                    GuidedStage.BLOCKED -> {
                        Text("Nessuna altra prestazione disponibile", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (hasAbsence) {
                                "Per questa giornata è già registrata un'assenza."
                            } else {
                                "Per questa giornata risultano già presenti primo e secondo turno."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (stage == GuidedStage.WORK || stage == GuidedStage.ABSENCE) {
                    TextButton(onClick = { stage = GuidedStage.ROOT }) { Text("Indietro") }
                }
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        }
    )
}

@Composable
private fun FlowChoice(
    icon: String,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = tint
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.72f)
            ) {
                Text(
                    icon,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}
