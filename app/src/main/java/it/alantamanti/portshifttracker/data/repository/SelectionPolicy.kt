package it.alantamanti.portshifttracker.data.repository

import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.domain.AllowanceCategory
import it.alantamanti.portshifttracker.domain.PerformanceType

private val halfTurnCodes = setOf("DOP_TU_MEZZO", "DOP_ON_MEZZO")
private val absenceCodes = setOf(
    "ALT_FERIE", "ALT_MALATTIA", "ALT_IMA",
    "AVV_CONGEDO", "AVV_INAIL", "AVV_DS"
)

/**
 * Applica le relazioni obbligatorie tra voci prima di calcolo e salvataggio.
 * TUMezzo/ONmezzo implicano sempre Mezza IMA; Mezza IMA isolata viene rimossa.
 */
fun normalizeSelectedRuleIds(
    performanceType: PerformanceType,
    selectedIds: Set<Long>,
    rules: List<AllowanceRuleEntity>
): Set<Long> {
    val compatible = selectedIds.filterTo(mutableSetOf()) { id ->
        rules.firstOrNull { it.id == id }?.let { rule ->
            rule.enabled && (rule.performanceMask and performanceType.maskBit) != 0 &&
                (performanceType == PerformanceType.TURNO ||
                    rule.code !in setOf("ALT_MEZZA_IMA", "DOP_TU_MEZZO", "DOP_ON_MEZZO"))
        } == true
    }

    if (performanceType != PerformanceType.TURNO) return compatible

    val halfTurnSelected = rules.any { it.id in compatible && it.code in halfTurnCodes }
    val mezzaImaId = rules.firstOrNull {
        it.code == "ALT_MEZZA_IMA" &&
            it.enabled &&
            (it.performanceMask and PerformanceType.TURNO.maskBit) != 0
    }?.id

    return when {
        mezzaImaId == null -> compatible
        halfTurnSelected -> compatible + mezzaImaId
        else -> compatible - mezzaImaId
    }
}

/**
 * Restituisce il motivo per cui una prestazione non è ancora salvabile.
 * Le assenze (Ferie, Malattia, IMA, Congedo, INAIL e Donazione) non richiedono un turno M/P/S/S2/N.
 */
fun selectionValidationMessage(
    performanceType: PerformanceType,
    selectedIds: Set<Long>,
    rules: List<AllowanceRuleEntity>
): String? {
    val selected = rules.filter { it.id in selectedIds }
    if (performanceType == PerformanceType.TURNO && selected.any { it.code in absenceCodes }) {
        return null
    }

    val hasPrimary = when (performanceType) {
        PerformanceType.TURNO -> selected.any { it.category == AllowanceCategory.TURNO }
        PerformanceType.DOPPIO, PerformanceType.MEZZO_DOPPIO ->
            selected.any { it.category == AllowanceCategory.DOPPIO }
    }

    return if (hasPrimary) null else when (performanceType) {
        PerformanceType.TURNO -> "Seleziona il turno (Mattina, Pomeriggio, Sera, Sera2, Notte o Giornaliero)."
        PerformanceType.DOPPIO -> "Seleziona il turno del Doppio (Pom, Sera o Sera2)."
        PerformanceType.MEZZO_DOPPIO -> "Seleziona l'indennità di turno del Mezzo Doppio."
    }
}

fun isAbsenceSelection(selectedIds: Set<Long>, rules: List<AllowanceRuleEntity>): Boolean =
    rules.any { it.id in selectedIds && it.code in absenceCodes }
