package it.alantamanti.portshifttracker.ui

import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.data.repository.ShiftWithPay
import it.alantamanti.portshifttracker.domain.AllowanceCategory

/**
 * Real month breakdown used by the summary sheet. Amounts are always derived
 * from the existing pay calculation, never from the illustrative design mockups.
 */
internal data class SummaryCategoryComponent(
    val label: String,
    val cents: Long
)

internal data class SummaryCategoryDetail(
    val id: String,
    val label: String,
    val cents: Long,
    val components: List<SummaryCategoryComponent>
)

private val summaryAbsenceNames = linkedMapOf(
    "ALT_MALATTIA" to "Malattia",
    "ALT_FERIE" to "Ferie",
    "AVV_CONGEDO" to "Congedo",
    "ALT_IMA" to "IMA",
    "AVV_DS" to "Donazione sangue",
    "AVV_INAIL" to "INAIL"
)

private fun sortedComponents(components: List<SummaryCategoryComponent>) =
    components.sortedWith(compareByDescending<SummaryCategoryComponent> { it.cents }.thenBy { it.label })

internal fun summaryCategoryDetails(
    rows: List<ShiftWithPay>,
    rules: List<AllowanceRuleEntity>,
    totals: MonthlyCategoryTotals = monthlyCategoryTotals(rows, rules)
): List<SummaryCategoryDetail> {
    val rulesById = rules.associateBy { it.id }

    // A paid absence must not be counted under "Base": show it as its own
    // category. Congedo/Donazione/INAIL may also have an additive allowance
    // line carrying the absence itself; move that line from "Altre voci"
    // into the same absence category so the monthly total is not duplicated.
    val absenceCodeByRow = rows.associateWith { row ->
        summaryAbsenceNames.keys.firstOrNull { code ->
            row.selectedRules.any { it.code == code }
        }
    }
    val absenceRuleIds = rules.filter { it.code in summaryAbsenceNames.keys }
        .associate { it.id to it.code }
    val absorbedAbsenceRuleIds = rows.asSequence()
        .flatMap { row ->
            val absenceCode = absenceCodeByRow[row]
            row.pay.allowanceLines.asSequence()
                .filter { line -> absenceRuleIds[line.ruleId] == absenceCode }
                .map { it.ruleId }
        }
        .toSet()

    val baseComponents = sortedComponents(
        rows.filter { absenceCodeByRow[it] == null }
            .groupBy { row ->
                if (row.selectedRules.any { it.code == "G" || it.code == "DOP_G" }) {
                    "Giornalieri"
                } else {
                    "Turni"
                }
            }
            .map { (label, entries) ->
                SummaryCategoryComponent(label, entries.sumOf { it.pay.basePayCents })
            }
            .filter { it.cents != 0L }
    )

    val absenceDetails = summaryAbsenceNames.mapNotNull { (code, label) ->
        val matching = rows.filter { absenceCodeByRow[it] == code }
        if (matching.isEmpty()) return@mapNotNull null
        val baseAmount = matching.sumOf { it.pay.basePayCents }
        val absenceAllowance = matching.sumOf { row ->
            row.pay.allowanceLines
                .filter { line -> absenceRuleIds[line.ruleId] == code }
                .sumOf { it.amountCents }
        }
        val amount = baseAmount + absenceAllowance
        SummaryCategoryDetail(
            id = "absence:$code",
            label = label,
            cents = amount,
            components = sortedComponents(
                listOf(
                    SummaryCategoryComponent("Base", baseAmount),
                    SummaryCategoryComponent("Indennità $label", absenceAllowance)
                ).filter { it.cents != 0L }
            )
        )
    }

    fun componentsFor(category: AllowanceCategory): List<SummaryCategoryComponent> =
        rows.asSequence()
            .flatMap { it.pay.allowanceLines.asSequence() }
            .filter { rulesById[it.ruleId]?.category == category ||
                (category == AllowanceCategory.TURNO &&
                    rulesById[it.ruleId]?.category == AllowanceCategory.MEZZO_TURNO)
            }
            .groupBy { line -> line.ruleId to (rulesById[line.ruleId]?.name ?: line.name) }
            .map { (identity, lines) ->
                Triple(
                    identity.second,
                    lines.sumOf { it.amountCents },
                    rulesById[identity.first]?.priority ?: Int.MAX_VALUE
                )
            }
            .filter { it.second != 0L }
            .sortedWith(compareByDescending<Triple<String, Long, Int>> { it.second }.thenBy { it.first })
            .map { SummaryCategoryComponent(it.first, it.second) }

    val turno = componentsFor(AllowanceCategory.TURNO)
    val avviamento = componentsFor(AllowanceCategory.AVVIAMENTO)
    val disagio = componentsFor(AllowanceCategory.DISAGIO)
    val area = componentsFor(AllowanceCategory.AREA)
    val doppio = componentsFor(AllowanceCategory.DOPPIO)

    val primary = listOf(
        SummaryCategoryDetail("base", "Base", baseComponents.sumOf { it.cents }, baseComponents),
        SummaryCategoryDetail("turno", "Turno", totals.turno, turno),
        SummaryCategoryDetail("avviamento", "Avviamento", totals.avviamento, avviamento),
        SummaryCategoryDetail("disagio", "Disagi", totals.disagio, disagio),
        SummaryCategoryDetail("area", "Area", totals.area, area),
        SummaryCategoryDetail("doppio", "Doppio", totals.doppio, doppio)
    )

    // Each "Altre voci" rule is now a full-size row in the ONE category card,
    // not a nested row under an "Altre voci" heading.
    val others = rows.asSequence()
        .flatMap { it.pay.allowanceLines.asSequence() }
        .filter { line ->
            line.ruleId !in absorbedAbsenceRuleIds &&
                when (rulesById[line.ruleId]?.category) {
                AllowanceCategory.ALTRE_VOCI, AllowanceCategory.ALTRO, null -> true
                else -> false
            }
        }
        .groupBy { it.ruleId to (rulesById[it.ruleId]?.name ?: it.name) }
        .map { (identity, lines) ->
            Triple(
                identity,
                lines.sumOf { it.amountCents },
                rulesById[identity.first]?.priority ?: Int.MAX_VALUE
            )
        }
        .filter { it.second != 0L }
        .sortedWith(compareByDescending<Triple<Pair<Long, String>, Long, Int>> { it.second }
            .thenBy { it.first.second })
        .map { (identity, amount, _) ->
            SummaryCategoryDetail(
                id = "other:${identity.first}:${identity.second}",
                label = identity.second,
                cents = amount,
                components = listOf(SummaryCategoryComponent(identity.second, amount))
            )
        }

    return (primary + absenceDetails + others)
        .sortedWith(compareByDescending<SummaryCategoryDetail> { it.cents }.thenBy { it.label })
}
