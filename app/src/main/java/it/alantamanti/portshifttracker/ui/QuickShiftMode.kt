package it.alantamanti.portshifttracker.ui

import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.domain.PerformanceType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay

/** Five common port shifts used by the quick-entry UI. */
internal enum class QuickShiftKind(val label: String) {
    MATTINA("Mattina"),
    POMERIGGIO("Pomeriggio"),
    SERA("Sera"),
    SERA2("Sera2"),
    NOTTE("Notte"),
    GIORNALIERO("Giornaliero")
}

internal enum class PortDayClass(val label: String) {
    FERIALE("Feriale"),
    SABATO("Sabato"),
    FESTIVO("Festivo"),
    SEMIFESTIVO("Semifestivo")
}

internal data class QuickShiftResolution(
    val kind: QuickShiftKind,
    val dayClass: PortDayClass,
    val ruleCode: String,
    val compactCode: String,
    val explanation: String
)

/**
 * Calendar used by the quick-entry mode for Ravenna port work.
 * An optional override comes from the editable "Calendario speciale" and always wins.
 */
internal fun resolveQuickShift(
    kind: QuickShiftKind,
    date: LocalDate,
    overrideClass: PortDayClass? = null
): QuickShiftResolution {
    val baseClass = overrideClass ?: portDayClass(date)
    val semiFestiveAfternoon = overrideClass == null && isPortSemiFestive(date) && kind in setOf(
        QuickShiftKind.POMERIGGIO,
        QuickShiftKind.SERA,
        QuickShiftKind.SERA2
    )
    val effectiveClass = if (semiFestiveAfternoon && baseClass != PortDayClass.FESTIVO) {
        PortDayClass.SEMIFESTIVO
    } else {
        baseClass
    }
    val festiveForAllowance = effectiveClass == PortDayClass.FESTIVO || effectiveClass == PortDayClass.SEMIFESTIVO

    val (ruleCode, compactCode) = when (kind) {
        QuickShiftKind.MATTINA -> if (festiveForAllowance) "MATF" to "MatF" else "MAT" to "Mat"
        QuickShiftKind.POMERIGGIO -> when {
            festiveForAllowance -> "POMF" to "PomF"
            effectiveClass == PortDayClass.SABATO -> "POMS" to "PomS"
            else -> "POM" to "Pom"
        }
        QuickShiftKind.SERA -> when {
            festiveForAllowance -> "SERAF" to "SeraF"
            effectiveClass == PortDayClass.SABATO -> "SERAS" to "SeraS"
            else -> "SERA" to "Sera"
        }
        QuickShiftKind.SERA2 -> when {
            festiveForAllowance -> "SERAF2" to "Sera2F"
            effectiveClass == PortDayClass.SABATO -> "SERAS2" to "SeraS2"
            else -> "SERA2" to "Sera2"
        }
        QuickShiftKind.NOTTE -> if (festiveForAllowance) "NOTTEF" to "NotteF" else "NOTTE" to "Notte"
        QuickShiftKind.GIORNALIERO -> "G" to "G"
    }

    val prefix = if (overrideClass != null) "Calendario speciale: " else ""
    val explanation = prefix + when (effectiveClass) {
        PortDayClass.FERIALE -> "giorno feriale, viene selezionata automaticamente $compactCode."
        PortDayClass.SABATO -> "sabato, viene selezionata automaticamente $compactCode."
        PortDayClass.FESTIVO -> "giorno festivo, viene selezionata automaticamente $compactCode."
        PortDayClass.SEMIFESTIVO -> "semifestivo, viene applicata la variante festiva $compactCode."
    }
    return QuickShiftResolution(kind, effectiveClass, ruleCode, compactCode, explanation)
}

internal fun applyQuickTurnSelection(
    currentIds: Set<Long>,
    rules: List<AllowanceRuleEntity>,
    kind: QuickShiftKind,
    date: LocalDate,
    overrideClass: PortDayClass? = null
): Set<Long> {
    val resolution = resolveQuickShift(kind, date, overrideClass)
    val target = rules.firstOrNull {
        it.enabled && it.code == resolution.ruleCode && (it.performanceMask and PerformanceType.TURNO.maskBit) != 0
    } ?: return currentIds

    val turnGroupIds = rules.asSequence()
        .filter { it.exclusiveGroup == "TIPO_TURNO" && (it.performanceMask and PerformanceType.TURNO.maskBit) != 0 }
        .map { it.id }
        .toSet()

    return currentIds.filterNotTo(mutableSetOf()) { it in turnGroupIds }.also { it += target.id }
}

internal fun inferQuickShiftKind(selectedIds: Set<Long>, rules: List<AllowanceRuleEntity>): QuickShiftKind? {
    val code = rules.firstOrNull { it.id in selectedIds && it.exclusiveGroup == "TIPO_TURNO" }?.code ?: return null
    return when (code) {
        "MAT", "MATF" -> QuickShiftKind.MATTINA
        "POM", "POMS", "POMF" -> QuickShiftKind.POMERIGGIO
        "SERA", "SERAS", "SERAF" -> QuickShiftKind.SERA
        "SERA2", "SERAS2", "SERAF2" -> QuickShiftKind.SERA2
        "NOTTE", "NOTTEF" -> QuickShiftKind.NOTTE
        "G" -> QuickShiftKind.GIORNALIERO
        else -> null
    }
}

internal fun portDayClass(date: LocalDate): PortDayClass {
    if (isPortHoliday(date)) return PortDayClass.FESTIVO
    if (date.dayOfWeek == DayOfWeek.SATURDAY) return PortDayClass.SABATO
    return PortDayClass.FERIALE
}

private fun isPortHoliday(date: LocalDate): Boolean {
    if (date.dayOfWeek == DayOfWeek.SUNDAY) return true

    val fixed = setOf(
        MonthDay.of(1, 1),
        MonthDay.of(1, 6),
        MonthDay.of(4, 25),
        MonthDay.of(5, 1),
        MonthDay.of(6, 2),
        MonthDay.of(7, 23),
        MonthDay.of(8, 15),
        MonthDay.of(11, 1),
        MonthDay.of(11, 4),
        MonthDay.of(12, 8),
        MonthDay.of(12, 25),
        MonthDay.of(12, 26)
    )
    if (MonthDay.from(date) in fixed) return true
    if (date.year >= 2026 && MonthDay.from(date) == MonthDay.of(10, 4)) return true

    val easter = easterSunday(date.year)
    return date == easter.plusDays(1)
}

private fun isPortSemiFestive(date: LocalDate): Boolean {
    if (MonthDay.from(date) == MonthDay.of(12, 24)) return true
    return date == easterSunday(date.year).minusDays(1)
}

/** Gregorian Easter (Meeus/Jones/Butcher). */
private fun easterSunday(year: Int): LocalDate {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31
    val day = ((h + l - 7 * m + 114) % 31) + 1
    return LocalDate.of(year, month, day)
}
