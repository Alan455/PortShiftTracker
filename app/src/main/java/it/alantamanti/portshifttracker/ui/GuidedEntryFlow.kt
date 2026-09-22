package it.alantamanti.portshifttracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.domain.PerformanceType
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The first decision is made from the selected date, not from a manually
 * selected performance type. New entries use this flow; edit/copy retain
 * their original data and editor so older records are not silently changed.
 */
internal enum class GuidedEntryChoice(
    val label: String,
    val performanceType: PerformanceType = PerformanceType.TURNO,
    val initialRuleCode: String? = null
) {
    WORK_TURNO("Turno"),
    WORK_GIORNALIERO("Giornaliero", initialRuleCode = "G"),
    ABS_FERIE("Ferie", initialRuleCode = "ALT_FERIE"),
    ABS_MALATTIA("Malattia", initialRuleCode = "ALT_MALATTIA"),
    ABS_CONGEDO("Congedo", initialRuleCode = "AVV_CONGEDO"),
    ABS_IMA("IMA", initialRuleCode = "ALT_IMA"),
    ABS_INAIL("INAIL", initialRuleCode = "AVV_INAIL"),
    ABS_DONAZIONE("Donazione sangue", initialRuleCode = "AVV_DS"),
    DOUBLE_TURNO("Doppio turno", PerformanceType.DOPPIO),
    HALF_DOUBLE("Mezzo Doppio", PerformanceType.MEZZO_DOPPIO),
    HALF_GIORNALIERO("Mezzo Giornaliero", PerformanceType.DOPPIO, "DOP_G");

    val isAbsence: Boolean get() = name.startsWith("ABS_")
    val isSecond: Boolean get() = this == DOUBLE_TURNO || this == HALF_DOUBLE || this == HALF_GIORNALIERO
}

internal fun guidedInitialRuleIds(choice: GuidedEntryChoice, rules: List<AllowanceRuleEntity>): Set<Long> =
    choice.initialRuleCode?.let { code ->
        rules.firstOrNull {
            it.code == code && it.enabled && (it.performanceMask and choice.performanceType.maskBit) != 0
        }?.let { setOf(it.id) }
    } ?: emptySet()

/** Reused for the Home prompt and pure state tests. */
internal enum class GuidedDayState { FIRST, SECOND, BLOCKED }

internal fun guidedDayState(
    hasWorkedFirst: Boolean,
    hasAbsence: Boolean,
    hasSecond: Boolean
): GuidedDayState = when {
    hasAbsence || hasSecond -> GuidedDayState.BLOCKED
    hasWorkedFirst -> GuidedDayState.SECOND
    else -> GuidedDayState.FIRST
}

private enum class GuidedStep { WORK_OR_ABSENCE, WORK_TYPE, ABSENCE_TYPE, SECOND_TYPE }

@Composable
internal fun GuidedEntryPicker(
    date: LocalDate,
    dayState: GuidedDayState,
    rules: List<AllowanceRuleEntity>,
    onChoose: (GuidedEntryChoice) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember(date, dayState) {
        mutableStateOf(if (dayState == GuidedDayState.SECOND) GuidedStep.SECOND_TYPE else GuidedStep.WORK_OR_ABSENCE)
    }

    val heading = when {
        dayState == GuidedDayState.BLOCKED -> "Giornata già registrata"
        step == GuidedStep.WORK_OR_ABSENCE -> "Cosa vuoi inserire?"
        step == GuidedStep.WORK_TYPE -> "Che tipo di lavoro?"
        step == GuidedStep.ABSENCE_TYPE -> "Quale assenza?"
        else -> "Quale secondo turno?"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                when {
                    dayState == GuidedDayState.BLOCKED -> {
                        Text("Per questa giornata è già presente un'assenza o una seconda prestazione. Puoi modificare quella esistente dalla scheda giorno.")
                    }
                    step == GuidedStep.WORK_OR_ABSENCE -> {
                        EntryOption("💼", "Turno / Lavoro", "Turno oppure Giornaliero") { step = GuidedStep.WORK_TYPE }
                        EntryOption("🛏", "Assenza", "Ferie, Malattia, Congedo, IMA") { step = GuidedStep.ABSENCE_TYPE }
                    }
                    step == GuidedStep.WORK_TYPE -> {
                        EntryOption("🗓", "Turno", "Mattina, Pomeriggio, Sera, S2, Notte") { onChoose(GuidedEntryChoice.WORK_TURNO) }
                        EntryOption("💼", "Giornaliero", "Base €90 · Polivalenza automatica") { onChoose(GuidedEntryChoice.WORK_GIORNALIERO) }
                    }
                    step == GuidedStep.ABSENCE_TYPE -> {
                        val available = listOf(
                            GuidedEntryChoice.ABS_FERIE to "🌴",
                            GuidedEntryChoice.ABS_MALATTIA to "✚",
                            GuidedEntryChoice.ABS_CONGEDO to "◉",
                            GuidedEntryChoice.ABS_IMA to "📄",
                            GuidedEntryChoice.ABS_INAIL to "🛡",
                            GuidedEntryChoice.ABS_DONAZIONE to "♥"
                        ).filter { (choice, _) -> guidedInitialRuleIds(choice, rules).isNotEmpty() }
                        available.forEach { (choice, glyph) ->
                            EntryOption(glyph, choice.label) { onChoose(choice) }
                        }
                        if (available.isEmpty()) Text("Non ci sono assenze abilitate nel catalogo indennità.")
                    }
                    step == GuidedStep.SECOND_TYPE -> {
                        EntryOption("2×", "Doppio turno", "Indennità di turno intera") { onChoose(GuidedEntryChoice.DOUBLE_TURNO) }
                        EntryOption("½", "Mezzo Doppio", "Base dimezzata e indennità di turno ridotte") { onChoose(GuidedEntryChoice.HALF_DOUBLE) }
                        if (guidedInitialRuleIds(GuidedEntryChoice.HALF_GIORNALIERO, rules).isNotEmpty()) {
                            EntryOption("G", "Mezzo Giornaliero", "Base €45 · Nessuna Mezza IMA") { onChoose(GuidedEntryChoice.HALF_GIORNALIERO) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        },
        dismissButton = {
            if (step != GuidedStep.WORK_OR_ABSENCE && step != GuidedStep.SECOND_TYPE && dayState == GuidedDayState.FIRST) {
                TextButton(onClick = { step = GuidedStep.WORK_OR_ABSENCE }) { Text("Indietro") }
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
private fun EntryOption(icon: String, label: String, detail: String = "", onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(icon, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold)
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** One compact choice row, shown only for the work branches that need a shift. */
@Composable
internal fun GuidedShiftPicker(
    date: LocalDate,
    performanceType: PerformanceType,
    specialDay: PortDayClass?,
    rules: List<AllowanceRuleEntity>,
    selected: QuickShiftKind?,
    onSelect: (QuickShiftKind) -> Unit
) {
    val kinds = quickShiftKindsFor(performanceType).filter { it != QuickShiftKind.GIORNALIERO }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Seleziona il turno", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                kinds.forEach { kind ->
                    val resolution = resolveQuickShift(kind, date, specialDay, performanceType)
                    val available = rules.any {
                        it.enabled && it.code == resolution.ruleCode &&
                            (it.performanceMask and performanceType.maskBit) != 0
                    }
                    FilterChip(
                        selected = selected == kind,
                        onClick = { onSelect(kind) },
                        enabled = available,
                        label = {
                            Text(when (kind) {
                                QuickShiftKind.MATTINA -> "M"
                                QuickShiftKind.POMERIGGIO -> "P"
                                QuickShiftKind.SERA -> "S"
                                QuickShiftKind.SERA2 -> "S2"
                                QuickShiftKind.NOTTE -> "N"
                                QuickShiftKind.GIORNALIERO -> "G"
                            })
                        },
                        modifier = Modifier.widthIn(min = 48.dp)
                    )
                }
            }
            if (selected != null) {
                Text(
                    resolveQuickShift(selected, date, specialDay, performanceType).explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Internal nominal intervals used when the user picks a shift without the old
 * time form. They preserve date attribution for overnight work and hour-based
 * allowances. The date in the calendar is the start date of the shift.
 */
internal fun guidedShiftInterval(date: LocalDate, kind: QuickShiftKind): Pair<LocalDateTime, LocalDateTime> = when (kind) {
    QuickShiftKind.MATTINA -> date.atTime(6, 30) to date.atTime(13, 0)
    QuickShiftKind.POMERIGGIO -> date.atTime(13, 0) to date.atTime(19, 30)
    QuickShiftKind.SERA -> date.atTime(19, 30) to date.plusDays(1).atTime(1, 0)
    QuickShiftKind.SERA2 -> date.atTime(19, 30) to date.plusDays(1).atTime(2, 0)
    QuickShiftKind.NOTTE -> date.atTime(1, 0) to date.atTime(6, 30)
    QuickShiftKind.GIORNALIERO -> date.atTime(8, 0) to date.atTime(14, 0)
}
