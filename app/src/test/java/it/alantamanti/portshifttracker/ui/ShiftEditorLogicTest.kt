package it.alantamanti.portshifttracker.ui

import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.data.local.ShiftEntity
import it.alantamanti.portshifttracker.data.local.WorkerEntity
import it.alantamanti.portshifttracker.data.repository.ShiftWithPay
import it.alantamanti.portshifttracker.domain.AllowanceApplicationMode
import it.alantamanti.portshifttracker.domain.AllowanceCalculationType
import it.alantamanti.portshifttracker.domain.AllowanceCategory
import it.alantamanti.portshifttracker.domain.PayBreakdown
import it.alantamanti.portshifttracker.domain.PerformanceType
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ShiftEditorLogicTest {
    private val worker = WorkerEntity(name = "Test", hourlyRateCents = 0)
    private val recentRule = rule(1, "AREA_A5")
    private val oldRule = rule(2, "AREA_G3")

    @Test
    fun recent_same_role_and_performance_scores_higher() {
        val today = LocalDate.of(2026, 9, 18)
        val rows = listOf(
            row(today.minusDays(2), PerformanceType.TURNO, "Gruista", recentRule),
            row(today.minusDays(500), PerformanceType.DOPPIO, "Altro", oldRule)
        )

        val scores = ruleUsageScores(rows, today, PerformanceType.TURNO, "Gruista")
        assertTrue((scores[recentRule.id] ?: 0) > (scores[oldRule.id] ?: 0))
    }

    private fun row(
        date: LocalDate,
        type: PerformanceType,
        role: String,
        rule: AllowanceRuleEntity
    ): ShiftWithPay {
        val start = date.atTime(8, 0).atZone(ZoneId.of("Europe/Rome")).toInstant().toEpochMilli()
        return ShiftWithPay(
            shift = ShiftEntity(
                id = date.toEpochDay(),
                workerId = 1,
                startEpochMillis = start,
                endEpochMillis = start + 6 * 60 * 60 * 1000,
                role = role,
                performanceType = type
            ),
            worker = worker,
            pay = PayBreakdown(totalMinutes = 360, basePayCents = 0, allowanceLines = emptyList()),
            selectedRules = listOf(rule)
        )
    }

    private fun rule(id: Long, code: String) = AllowanceRuleEntity(
        id = id,
        name = code,
        code = code,
        calculationType = AllowanceCalculationType.FIXED_PER_SHIFT,
        value = 100,
        category = AllowanceCategory.AREA,
        applicationMode = AllowanceApplicationMode.MANUAL
    )
}
