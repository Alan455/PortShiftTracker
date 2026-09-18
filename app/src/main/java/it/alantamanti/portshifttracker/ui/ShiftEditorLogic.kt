package it.alantamanti.portshifttracker.ui

import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.data.repository.ShiftWithPay
import it.alantamanti.portshifttracker.domain.AllowanceCategory
import it.alantamanti.portshifttracker.domain.PayBreakdown
import it.alantamanti.portshifttracker.domain.PerformanceType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Punteggio adattivo delle voci: gli utilizzi recenti, nella stessa prestazione
 * e con la stessa mansione pesano di più dei vecchi utilizzi generici.
 */
internal fun ruleUsageScores(
    rows: List<ShiftWithPay>,
    currentDate: LocalDate,
    performanceType: PerformanceType,
    role: String
): Map<Long, Int> {
    val normalizedRole = role.trim().lowercase(Locale.ITALIAN)
    val scores = mutableMapOf<Long, Int>()

    rows.forEach { row ->
        val date = Instant.ofEpochMilli(row.shift.startEpochMillis)
            .atZone(ZoneId.of(row.shift.zoneId))
            .toLocalDate()
        val age = ChronoUnit.DAYS.between(date, currentDate).let { kotlin.math.abs(it) }
        val recency = when {
            age <= 14 -> 60
            age <= 60 -> 35
            age <= 180 -> 15
            age <= 365 -> 6
            else -> 1
        }
        val performanceBonus = if (row.shift.performanceType == performanceType) 80 else 10
        val rowRole = row.shift.role.trim().lowercase(Locale.ITALIAN)
        val roleBonus = if (
            normalizedRole.isNotBlank() &&
            rowRole.isNotBlank() &&
            rowRole == normalizedRole
        ) 50 else 0

        row.selectedRules.forEach { rule ->
            scores[rule.id] = (scores[rule.id] ?: 0) + recency + performanceBonus + roleBonus
        }
    }
    return scores
}

internal fun selectionSummary(
    selectedIds: Set<Long>,
    rules: List<AllowanceRuleEntity>,
    performanceType: PerformanceType
): String {
    val selected = rules.filter { it.id in selectedIds }

    val absence = selected.firstOrNull {
        it.code == "ALT_FERIE" || it.code == "ALT_MALATTIA" || it.code == "ALT_IMA"
    }
    if (absence != null) {
        val extras = selected.count {
            it.id != absence.id &&
                it.category != AllowanceCategory.TURNO &&
                it.category != AllowanceCategory.DOPPIO
        }
        return if (extras > 0) "${absence.name} · $extras extra" else absence.name
    }

    val primary = when (performanceType) {
        PerformanceType.TURNO -> selected.firstOrNull { it.category == AllowanceCategory.TURNO }
        PerformanceType.DOPPIO, PerformanceType.MEZZO_DOPPIO ->
            selected.firstOrNull { it.category == AllowanceCategory.DOPPIO }
    }?.name

    val half = selected.firstOrNull { it.category == AllowanceCategory.MEZZO_TURNO }?.name
    val extras = selected.count {
        it.category != AllowanceCategory.TURNO &&
            it.category != AllowanceCategory.DOPPIO &&
            it.category != AllowanceCategory.MEZZO_TURNO
    }

    val parts = buildList {
        primary?.let(::add)
        half?.let(::add)
        if (extras > 0) add("$extras indennità")
    }
    return parts.joinToString(" · ").ifBlank { "Nessuna selezione" }
}

internal fun payFormula(pay: PayBreakdown): String {
    val nonZero = pay.allowanceLines.filter { it.amountCents != 0L }
    val shown = nonZero.take(4)
    val terms = buildList {
        add("Base ${moneyCompact(pay.basePayCents)}")
        shown.forEach { add("${it.name} ${signedMoney(it.amountCents)}") }
        if (nonZero.size > shown.size) add("+ altre")
    }
    return terms.joinToString(" + ").replace("+ -", "- ") +
        " = ${moneyCompact(pay.totalPayCents)}"
}

private fun signedMoney(cents: Long): String =
    if (cents >= 0) "+${moneyCompact(cents)}" else "-${moneyCompact(-cents)}"

private fun moneyCompact(cents: Long): String = "€ %.2f".format(Locale.ITALY, cents / 100.0)
