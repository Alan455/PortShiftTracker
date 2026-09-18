package it.alantamanti.portshifttracker.ui

import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.data.local.DayOverrideClass
import it.alantamanti.portshifttracker.data.repository.ShiftWithPay
import it.alantamanti.portshifttracker.domain.AllowanceCategory
import it.alantamanti.portshifttracker.domain.PerformanceType

internal fun DayOverrideClass.toPortDayClass(): PortDayClass = when (this) {
    DayOverrideClass.FERIALE -> PortDayClass.FERIALE
    DayOverrideClass.SABATO -> PortDayClass.SABATO
    DayOverrideClass.FESTIVO -> PortDayClass.FESTIVO
    DayOverrideClass.SEMIFESTIVO -> PortDayClass.SEMIFESTIVO
}

/** Avvisi non bloccanti: aiutano a intercettare combinazioni probabilmente errate. */
internal fun consistencyWarnings(
    performanceType: PerformanceType,
    selectedRules: List<AllowanceRuleEntity>
): List<String> = buildList {
    val codes = selectedRules.map { it.code }.toSet()
    val turnRules = selectedRules.filter {
        it.category == AllowanceCategory.TURNO ||
            it.category == AllowanceCategory.DOPPIO ||
            it.category == AllowanceCategory.MEZZO_TURNO
    }
    val areas = selectedRules.count { it.category == AllowanceCategory.AREA }

    if (turnRules.groupBy { it.exclusiveGroup ?: it.category.name }.values.any { it.size > 1 }) {
        add("Sono presenti più indennità di turno nello stesso gruppo: controlla la selezione.")
    }
    if (areas > 1) add("Sono selezionate più aree per la stessa prestazione.")

    if ("ALT_MEZZA_IMA" in codes && codes.none { it == "DOP_TU_MEZZO" || it == "DOP_ON_MEZZO" }) {
        add("Mezza IMA normalmente va insieme a TUMezzo o ONmezzo.")
    }
    if (performanceType != PerformanceType.TURNO && "ALT_MEZZA_IMA" in codes) {
        add("Mezza IMA è prevista sul turno ordinario, non sul Doppio o Mezzo Doppio.")
    }
    if (performanceType != PerformanceType.TURNO && "ALT_POLIVALENZA" in codes) {
        add("La Polivalenza non spetta sul Doppio o Mezzo Doppio.")
    }

    val replacingBase = codes.intersect(setOf("ALT_FERIE", "ALT_MALATTIA", "ALT_IMA"))
    if (replacingBase.isNotEmpty() && selectedRules.any { it.category == AllowanceCategory.TURNO || it.category == AllowanceCategory.MEZZO_TURNO }) {
        val name = when {
            "ALT_IMA" in replacingBase -> "IMA"
            "ALT_FERIE" in replacingBase -> "Ferie"
            else -> "Malattia"
        }
        add("$name sostituisce la base: controlla che l'indennità di turno selezionata sia realmente dovuta.")
    }
}

internal fun calendarShiftCode(row: ShiftWithPay): String = when (row.shift.performanceType) {
    PerformanceType.DOPPIO -> "2×"
    PerformanceType.MEZZO_DOPPIO -> "½×"
    PerformanceType.TURNO -> {
        val code = row.selectedRules.firstOrNull { it.category == AllowanceCategory.TURNO }?.code
        when (code) {
            "MAT", "MATF" -> "M"
            "POM", "POMS", "POMF" -> "P"
            "SERA", "SERAS", "SERAF" -> "S"
            "SERA2", "SERAS2", "SERAF2" -> "S2"
            "NOTTE", "NOTTEF" -> "N"
            else -> if (row.selectedRules.any { it.category == AllowanceCategory.MEZZO_TURNO }) "½T" else "T"
        }
    }
}

internal data class MonthlyCategoryTotals(
    val base: Long,
    val turno: Long,
    val avviamento: Long,
    val disagio: Long,
    val area: Long,
    val doppio: Long,
    val altre: Long,
    val total: Long
)

internal fun monthlyCategoryTotals(
    rows: List<ShiftWithPay>,
    rules: List<AllowanceRuleEntity>
): MonthlyCategoryTotals {
    val categories = rules.associate { it.id to it.category }
    var turno = 0L
    var avviamento = 0L
    var disagio = 0L
    var area = 0L
    var doppio = 0L
    var altre = 0L

    rows.forEach { row ->
        row.pay.allowanceLines.forEach { line ->
            when (categories[line.ruleId]) {
                AllowanceCategory.TURNO, AllowanceCategory.MEZZO_TURNO -> turno += line.amountCents
                AllowanceCategory.AVVIAMENTO -> avviamento += line.amountCents
                AllowanceCategory.DISAGIO -> disagio += line.amountCents
                AllowanceCategory.AREA -> area += line.amountCents
                AllowanceCategory.DOPPIO -> doppio += line.amountCents
                AllowanceCategory.ALTRE_VOCI, AllowanceCategory.ALTRO, null -> altre += line.amountCents
            }
        }
    }
    return MonthlyCategoryTotals(
        base = rows.sumOf { it.pay.basePayCents },
        turno = turno,
        avviamento = avviamento,
        disagio = disagio,
        area = area,
        doppio = doppio,
        altre = altre,
        total = rows.sumOf { it.pay.totalPayCents }
    )
}
