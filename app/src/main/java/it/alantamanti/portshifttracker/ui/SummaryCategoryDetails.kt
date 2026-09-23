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

private val summaryAbsenceCodes = setOf(
    "ALT_FERIE", "ALT_MALATTIA", "ALT_IMA", "AVV_DS", "AVV_INAIL", "AVV_CONGEDO"
)

internal fun summaryCategoryDetails(
    rows: List<ShiftWithPay>,
    rules: List<AllowanceRuleEntity>,
    totals: MonthlyCategoryTotals = monthlyCategoryTotals(rows, rules)
): List<SummaryCategoryDetail> {
    val rulesById = rules.associateBy { it.id }

    // DOP_G and first-turn G/ONMezzo both contribute to Giornalieri.
    // Preserve any paid absence base as its own component so the sheet's
    // components always reconcile with the actual monthly Base amount.
    val baseByKind = rows.groupBy { row ->
        when {
            row.selectedRules.any { it.code in summaryAbsenceCodes } -> "Assenze"
            row.selectedRules.any { it.code == "G" || it.code == "DOP_G" } -> "Giornalieri"
            else -> "Turni"
        }
    }
    val baseComponents = listOf("Turni", "Giornalieri", "Assenze")
        .mapNotNull { kind ->
            baseByKind[kind]?.sumOf { it.pay.basePayCents }
                ?.let { amount -> SummaryCategoryComponent(kind, amount) }
        }
        .filter { it.cents != 0L }

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
            .sortedWith(compareBy<Triple<String, Long, Int>> { it.third }.thenBy { it.first })
            .map { SummaryCategoryComponent(it.first, it.second) }

    val turno = componentsFor(AllowanceCategory.TURNO)
    val avviamento = componentsFor(AllowanceCategory.AVVIAMENTO)
    val disagio = componentsFor(AllowanceCategory.DISAGIO)
    val area = componentsFor(AllowanceCategory.AREA)
    val doppio = componentsFor(AllowanceCategory.DOPPIO)

    val primary = listOf(
        SummaryCategoryDetail("base", "Base", totals.base, baseComponents),
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
        .sortedWith(compareBy<Triple<Pair<Long, String>, Long, Int>> { it.third }
            .thenBy { it.first.second })
        .map { (identity, amount, _) ->
            SummaryCategoryDetail(
                id = "other:${identity.first}:${identity.second}",
                label = identity.second,
                cents = amount,
                components = listOf(SummaryCategoryComponent(identity.second, amount))
            )
        }

    return primary + others
}
