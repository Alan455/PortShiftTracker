package it.alantamanti.portshifttracker.ui

import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.data.local.ShiftEntity
import it.alantamanti.portshifttracker.data.local.WorkerEntity
import it.alantamanti.portshifttracker.data.repository.ShiftWithPay
import it.alantamanti.portshifttracker.domain.AllowanceCategory
import it.alantamanti.portshifttracker.domain.AllowanceCalculationType
import it.alantamanti.portshifttracker.domain.AllowanceLine
import it.alantamanti.portshifttracker.domain.PayBreakdown
import it.alantamanti.portshifttracker.domain.PerformanceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryCategoryDetailsTest {
    private val worker = WorkerEntity(name = "Test", hourlyRateCents = 0)

    private fun rule(
        id: Long,
        code: String,
        name: String,
        category: AllowanceCategory
    ) = AllowanceRuleEntity(
        id = id,
        code = code,
        name = name,
        calculationType = AllowanceCalculationType.FIXED_PER_SHIFT,
        value = 0L,
        category = category
    )

    private fun row(
        base: Long,
        lines: List<AllowanceLine> = emptyList(),
        selected: List<AllowanceRuleEntity> = emptyList(),
        type: PerformanceType = PerformanceType.TURNO
    ) = ShiftWithPay(
        shift = ShiftEntity(
            workerId = 1,
            startEpochMillis = 1_000L,
            endEpochMillis = 4_000_000L,
            performanceType = type
        ),
        worker = worker,
        pay = PayBreakdown(
            totalMinutes = 60,
            basePayCents = base,
            allowanceLines = lines
        ),
        selectedRules = selected
    )

    @Test
    fun baseIsDividedIntoTurnsAndGiornalieriIncludingHalfGiornalieroInDouble() {
        val giornaliero = rule(1, "G", "Giornaliero", AllowanceCategory.TURNO)
        val mezzoGiornaliero = rule(2, "DOP_G", "Mezzo Giornaliero", AllowanceCategory.DOPPIO)
        val rows = listOf(
            row(base = 12_000),
            row(base = 4_500, selected = listOf(giornaliero)),
            row(base = 2_250, selected = listOf(mezzoGiornaliero), type = PerformanceType.DOPPIO)
        )

        val categories = summaryCategoryDetails(rows, listOf(giornaliero, mezzoGiornaliero))
        val base = categories.single { it.id == "base" }
        assertEquals(18_750L, base.cents)
        assertEquals(
            listOf(SummaryCategoryComponent("Turni", 12_000),
                SummaryCategoryComponent("Giornalieri", 6_750)),
            base.components
        )
        assertEquals(base.cents, base.components.sumOf { it.cents })
    }

    @Test
    fun actualAllowanceLinesAggregateByCategoryAndIndividualRule() {
        val sera2 = rule(10, "SERA2", "Sera2", AllowanceCategory.TURNO)
        val notte = rule(11, "NOTTE", "Notte", AllowanceCategory.TURNO)
        val festiva = rule(12, "NOTTEF", "Notte Festiva", AllowanceCategory.TURNO)
        val casa = rule(13, "AVV_DIS_CASA", "Disdetta casa", AllowanceCategory.AVVIAMENTO)
        val q2 = rule(14, "AREA_Q2", "Q2", AllowanceCategory.AREA)
        val h = rule(15, "AREA_H", "H", AllowanceCategory.AREA)
        val polivalenza = rule(16, "ALT_POLIVALENZA", "Polivalenza", AllowanceCategory.ALTRE_VOCI)
        val rules = listOf(sera2, notte, festiva, casa, q2, h, polivalenza)
        val amounts = listOf(
            AllowanceLine(10, "Sera2", 60, 5_000),
            AllowanceLine(11, "Notte", 60, 2_500),
            AllowanceLine(12, "Notte Festiva", 60, 4_000),
            AllowanceLine(13, "Disdetta casa", 60, 4_000),
            AllowanceLine(14, "Q2", 60, 5_000),
            AllowanceLine(15, "H", 60, 400),
            AllowanceLine(16, "Polivalenza", 60, 852)
        )

        val categories = summaryCategoryDetails(listOf(row(14_680, amounts)), rules)
        assertEquals(11_500L, categories.single { it.id == "turno" }.cents)
        assertEquals(11_500L, categories.single { it.id == "turno" }.components.sumOf { it.cents })
        assertEquals(listOf(SummaryCategoryComponent("Disdetta casa", 4_000)),
            categories.single { it.id == "avviamento" }.components)
        assertEquals(
            mapOf("Q2" to 5_000L, "H" to 400L),
            categories.single { it.id == "area" }.components.associate { it.label to it.cents }
        )
        assertEquals(5_400L, categories.single { it.id == "area" }.cents)
        assertEquals(852L, categories.single { it.label == "Polivalenza" }.cents)
        assertFalse(categories.any { it.label == "Altre voci" })
        assertTrue(categories.single { it.id == "disagio" }.components.isEmpty())
    }

    @Test
    fun paidAbsencesAreNotMisrepresentedAsWorkedTurns() {
        val ferie = rule(20, "ALT_FERIE", "Ferie", AllowanceCategory.ALTRE_VOCI)
        val categories = summaryCategoryDetails(
            listOf(row(base = 6_000), row(base = 4_000, selected = listOf(ferie))),
            listOf(ferie)
        )
        val base = categories.single { it.id == "base" }
        assertEquals(10_000L, base.cents)
        assertEquals(
            listOf(SummaryCategoryComponent("Turni", 6_000), SummaryCategoryComponent("Assenze", 4_000)),
            base.components
        )
    }
}
