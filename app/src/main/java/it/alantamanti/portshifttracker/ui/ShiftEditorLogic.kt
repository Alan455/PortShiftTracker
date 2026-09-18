package it.alantamanti.portshifttracker.ui

import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.data.local.ShiftEntity
import it.alantamanti.portshifttracker.data.repository.ShiftWithPay
import it.alantamanti.portshifttracker.data.repository.normalizeSelectedRuleIds
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


internal fun recentRoles(
    rows: List<ShiftWithPay>,
    currentDate: LocalDate,
    limit: Int = 5
): List<String> {
    val scores = mutableMapOf<String, Int>()
    val display = mutableMapOf<String, String>()
    rows.forEach { row ->
        val value = row.shift.role.trim()
        if (value.isBlank()) return@forEach
        val key = value.lowercase(Locale.ITALIAN)
        val date = Instant.ofEpochMilli(row.shift.startEpochMillis)
            .atZone(ZoneId.of(row.shift.zoneId))
            .toLocalDate()
        val age = kotlin.math.abs(ChronoUnit.DAYS.between(date, currentDate))
        val score = when {
            age <= 14 -> 50
            age <= 60 -> 25
            age <= 180 -> 10
            else -> 2
        }
        scores[key] = (scores[key] ?: 0) + score
        display[key] = value
    }
    return scores.entries
        .sortedByDescending { it.value }
        .take(limit)
        .mapNotNull { display[it.key] }
}

internal fun lastRepeatCandidate(
    rows: List<ShiftWithPay>,
    currentDate: LocalDate
): ShiftWithPay? = rows
    .filter { row ->
        val date = Instant.ofEpochMilli(row.shift.startEpochMillis)
            .atZone(ZoneId.of(row.shift.zoneId))
            .toLocalDate()
        !date.isAfter(currentDate)
    }
    .maxByOrNull { it.shift.startEpochMillis }

internal fun repeatSelectionForDate(
    source: ShiftWithPay,
    rules: List<AllowanceRuleEntity>,
    targetDate: LocalDate,
    overrideClass: PortDayClass?
): Pair<Set<Long>, QuickShiftKind?> {
    val type = source.shift.performanceType
    val sourceIds = source.selectedRules.map { it.id }.toSet()
    val kind = if (type == PerformanceType.MEZZO_DOPPIO) {
        inferQuickShiftKind(sourceIds, rules, PerformanceType.DOPPIO)
    } else {
        inferQuickShiftKind(sourceIds, rules, type)
    }

    if (kind == null) {
        return normalizeSelectedRuleIds(type, sourceIds, rules) to null
    }

    val primaryCategory = if (type == PerformanceType.TURNO) {
        AllowanceCategory.TURNO
    } else {
        AllowanceCategory.DOPPIO
    }
    val preserved = sourceIds.filterTo(mutableSetOf()) { id ->
        rules.firstOrNull { it.id == id }?.category != primaryCategory
    }

    val reapplied = if (type == PerformanceType.MEZZO_DOPPIO) {
        val resolution = resolveQuickShift(
            kind = kind,
            date = targetDate,
            overrideClass = overrideClass,
            performanceType = PerformanceType.DOPPIO
        )
        val target = rules.firstOrNull {
            it.enabled &&
                it.code == resolution.ruleCode &&
                (it.performanceMask and PerformanceType.MEZZO_DOPPIO.maskBit) != 0
        }
        if (target == null) preserved else preserved + target.id
    } else {
        applyQuickTurnSelection(
            currentIds = preserved,
            rules = rules,
            kind = kind,
            date = targetDate,
            overrideClass = overrideClass,
            performanceType = type
        )
    }
    return normalizeSelectedRuleIds(type, reapplied, rules) to kind
}


internal fun copyShiftToDate(shift: ShiftEntity, targetDate: LocalDate): ShiftEntity {
    val zone = ZoneId.of(shift.zoneId)
    val originalStart = Instant.ofEpochMilli(shift.startEpochMillis).atZone(zone)
    val durationMillis = shift.endEpochMillis - shift.startEpochMillis
    val newStart = targetDate.atTime(originalStart.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
    return shift.copy(
        id = 0,
        startEpochMillis = newStart,
        endEpochMillis = newStart + durationMillis,
        serviceEpochDay = targetDate.toEpochDay()
    )
}
