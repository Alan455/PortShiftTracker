package it.alantamanti.portshifttracker.ui

import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.domain.PerformanceType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay

/** Turni usati dall'inserimento rapido. */
internal enum class QuickShiftKind(val label: String) {
    MATTINA("Mattina"),
    POMERIGGIO("Pomeriggio"),
    SERA("Sera"),
    SERA2("Sera2"),
    NOTTE("Notte"),
    GIORNALIERO("Giornaliero")
}

internal val halfDoubleTurnAllowanceCodes = setOf(
    "DOP_POM", "DOP_POMS", "DOP_POMF",
    "DOP_SERA", "DOP_SERAS", "DOP_SERAF",
    "DOP_SERA2", "DOP_SERAS2", "DOP_SERAF2",
    "DOP_NOTTE", "DOP_NOTTEF"
)

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

internal fun quickShiftKindsFor(performanceType: PerformanceType): List<QuickShiftKind> = when (performanceType) {
    PerformanceType.TURNO -> listOf(
        QuickShiftKind.MATTINA,
        QuickShiftKind.POMERIGGIO,
        QuickShiftKind.SERA,
        QuickShiftKind.SERA2,
        QuickShiftKind.NOTTE
    )
    PerformanceType.DOPPIO,
    PerformanceType.MEZZO_DOPPIO -> listOf(
        QuickShiftKind.MATTINA,
        QuickShiftKind.POMERIGGIO,
        QuickShiftKind.SERA,
        QuickShiftKind.SERA2,
        QuickShiftKind.NOTTE
    )
}

/**
 * Risolve automaticamente la variante feriale/sabato/festiva in base alla data.
 * L'override del "Calendario speciale" ha sempre la precedenza.
 */
internal fun resolveQuickShift(
    kind: QuickShiftKind,
    date: LocalDate,
    overrideClass: PortDayClass? = null,
    performanceType: PerformanceType = PerformanceType.TURNO
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
    val festiveForAllowance =
        effectiveClass == PortDayClass.FESTIVO || effectiveClass == PortDayClass.SEMIFESTIVO

    val (ruleCode, compactCode) = when (performanceType) {
        PerformanceType.TURNO -> when (kind) {
            QuickShiftKind.MATTINA ->
                if (festiveForAllowance) "MATF" to "MatF" else "MAT" to "Mat"

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

            QuickShiftKind.NOTTE ->
                if (festiveForAllowance) "NOTTEF" to "NotteF" else "NOTTE" to "Notte"

            QuickShiftKind.GIORNALIERO -> "G" to "G"
        }

        PerformanceType.DOPPIO,
        PerformanceType.MEZZO_DOPPIO -> when (kind) {
            QuickShiftKind.MATTINA ->
                if (festiveForAllowance) "DOP_MATF" to "MatF" else "DOP_MAT" to "Mat"

            QuickShiftKind.POMERIGGIO -> when {
                festiveForAllowance -> "DOP_POMF" to "PomF"
                effectiveClass == PortDayClass.SABATO -> "DOP_POMS" to "PomS"
                else -> "DOP_POM" to "Pom"
            }

            QuickShiftKind.SERA -> when {
                festiveForAllowance -> "DOP_SERAF" to "SeraF"
                effectiveClass == PortDayClass.SABATO -> "DOP_SERAS" to "SeraS"
                else -> "DOP_SERA" to "Sera"
            }

            QuickShiftKind.SERA2 -> when {
                festiveForAllowance -> "DOP_SERAF2" to "Sera2F"
                effectiveClass == PortDayClass.SABATO -> "DOP_SERAS2" to "SeraS2"
                else -> "DOP_SERA2" to "Sera2"
            }

            QuickShiftKind.NOTTE ->
                if (festiveForAllowance) "DOP_NOTTEF" to "NotteF" else "DOP_NOTTE" to "Notte"

            QuickShiftKind.GIORNALIERO ->
                if (performanceType == PerformanceType.DOPPIO) "DOP_G" to "½G"
                else error("Mezzo Giornaliero non appartiene al Mezzo Doppio")
        }
    }

    val prefix = if (overrideClass != null) "Calendario speciale: " else ""
    val explanation = if (kind == QuickShiftKind.GIORNALIERO) {
        if (performanceType == PerformanceType.DOPPIO) {
            "Mezzo Giornaliero: base fissa €45, senza Mezza IMA e senza Polivalenza."
        } else {
            "Giornaliero: base fissa €90; con ONMezzo la base diventa €45."
        }
    } else prefix + when (effectiveClass) {
        PortDayClass.FERIALE -> "giorno feriale, viene selezionato automaticamente $compactCode."
        PortDayClass.SABATO -> "sabato, viene selezionata automaticamente la variante $compactCode."
        PortDayClass.FESTIVO -> "giorno festivo, viene selezionata automaticamente la variante $compactCode."
        PortDayClass.SEMIFESTIVO -> "semifestivo, viene applicata automaticamente la variante festiva $compactCode."
    }
    return QuickShiftResolution(kind, effectiveClass, ruleCode, compactCode, explanation)
}

internal fun applyQuickTurnSelection(
    currentIds: Set<Long>,
    rules: List<AllowanceRuleEntity>,
    kind: QuickShiftKind,
    date: LocalDate,
    overrideClass: PortDayClass? = null,
    performanceType: PerformanceType = PerformanceType.TURNO
): Set<Long> {
    val resolution = resolveQuickShift(kind, date, overrideClass, performanceType)
    val target = rules.firstOrNull {
        it.enabled &&
            it.code == resolution.ruleCode &&
            (it.performanceMask and performanceType.maskBit) != 0
    } ?: return currentIds

    val groupName = when (performanceType) {
        PerformanceType.TURNO -> "TIPO_TURNO"
        PerformanceType.DOPPIO,
        PerformanceType.MEZZO_DOPPIO -> "DOPPIO"
    }
    val groupIds = rules.asSequence()
        .filter { it.exclusiveGroup == groupName && (it.performanceMask and performanceType.maskBit) != 0 }
        .map { it.id }
        .toSet()

    return currentIds.filterNotTo(mutableSetOf()) { it in groupIds }.also { it += target.id }
}

internal fun inferQuickShiftKind(
    selectedIds: Set<Long>,
    rules: List<AllowanceRuleEntity>,
    performanceType: PerformanceType = PerformanceType.TURNO
): QuickShiftKind? {
    val groupName = when (performanceType) {
        PerformanceType.TURNO -> "TIPO_TURNO"
        PerformanceType.DOPPIO,
        PerformanceType.MEZZO_DOPPIO -> "DOPPIO"
    }
    val code = rules.firstOrNull {
        it.id in selectedIds &&
            it.exclusiveGroup == groupName &&
            (it.performanceMask and performanceType.maskBit) != 0
    }?.code ?: return null

    return when (code) {
        "MAT", "MATF", "DOP_MAT", "DOP_MATF" -> QuickShiftKind.MATTINA
        "POM", "POMS", "POMF", "DOP_POM", "DOP_POMS", "DOP_POMF" -> QuickShiftKind.POMERIGGIO
        "SERA", "SERAS", "SERAF", "DOP_SERA", "DOP_SERAS", "DOP_SERAF" -> QuickShiftKind.SERA
        "SERA2", "SERAS2", "SERAF2", "DOP_SERA2", "DOP_SERAS2", "DOP_SERAF2" -> QuickShiftKind.SERA2
        "NOTTE", "NOTTEF", "DOP_NOTTE", "DOP_NOTTEF" -> QuickShiftKind.NOTTE
        "G", "DOP_G" -> QuickShiftKind.GIORNALIERO
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
