package it.alantamanti.portshifttracker.ui

import it.alantamanti.portshifttracker.data.repository.ShiftWithPay
import it.alantamanti.portshifttracker.domain.PerformanceType

/** Le cinque sezioni dello Storico: ogni prestazione rientra in una sola sezione. */
internal enum class HistoryCategory(val label: String) {
    TURNI("Turni"),
    ASSENZE("Assenze"),
    MEZZI_PRIMI_TURNI("Mezzi primi turni"),
    DOPPI("Doppi"),
    MEZZI_DOPPI("Mezzi doppi")
}

private val absenceCodes = setOf(
    "ALT_FERIE",
    "ALT_MALATTIA",
    "AVV_INAIL",
    "AVV_CONGEDO",
    "AVV_DS",
    "ALT_IMA"
)

private val halfFirstShiftCodes = setOf("DOP_TU_MEZZO", "DOP_ON_MEZZO")

internal fun historyCategory(row: ShiftWithPay): HistoryCategory =
    historyCategory(row.shift.performanceType, row.selectedRules.map { it.code }.toSet())

/**
 * Le assenze e i mezzi primi turni sono registrati internamente come TURNO:
 * la categoria dello Storico deve quindi dipendere anche dalle causali selezionate.
 * DOP_G (Mezzo Giornaliero sul secondo turno) resta fra i Doppi.
 */
internal fun historyCategory(type: PerformanceType, codes: Set<String>): HistoryCategory =
    when (type) {
        PerformanceType.MEZZO_DOPPIO -> HistoryCategory.MEZZI_DOPPI
        PerformanceType.DOPPIO -> HistoryCategory.DOPPI
        PerformanceType.TURNO -> when {
            codes.any { it in absenceCodes } -> HistoryCategory.ASSENZE
            codes.any { it in halfFirstShiftCodes } -> HistoryCategory.MEZZI_PRIMI_TURNI
            else -> HistoryCategory.TURNI
        }
    }
