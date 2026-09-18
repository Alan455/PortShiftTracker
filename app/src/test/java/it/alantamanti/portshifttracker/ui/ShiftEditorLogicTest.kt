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
import org.junit.Assert.assertEquals
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


    @Test
    fun copy_shift_moves_to_target_date_and_keeps_duration() {
        val sourceDate = LocalDate.of(2026, 9, 18)
        val targetDate = LocalDate.of(2026, 9, 25)
        val source = row(sourceDate, PerformanceType.TURNO, "Gruista", recentRule).shift

        val copied = copyShiftToDate(source, targetDate)

        assertEquals(0L, copied.id)
        assertEquals(targetDate.toEpochDay(), copied.serviceEpochDay)
        assertEquals(
            source.endEpochMillis - source.startEpochMillis,
            copied.endEpochMillis - copied.startEpochMillis
        )
    }

    @Test
    fun repeat_selection_recalculates_saturday_turn_for_holiday() {
        val poms = turnRule(10, "POMS", "PomS")
        val pomf = turnRule(11, "POMF", "PomF")
        val pom = turnRule(12, "POM", "Pom")
        val area = recentRule
        val saturday = LocalDate.of(2026, 9, 19)
        val sunday = LocalDate.of(2026, 9, 20)
        val source = rowWithRules(
            saturday,
            PerformanceType.TURNO,
            "Gruista",
            listOf(poms, area)
        )

        val (ids, kind) = repeatSelectionForDate(
            source = source,
            rules = listOf(poms, pomf, pom, area),
            targetDate = sunday,
            overrideClass = null
        )

        assertEquals(QuickShiftKind.POMERIGGIO, kind)
        assertTrue(pomf.id in ids)
        assertTrue(area.id in ids)
        assertTrue(poms.id !in ids)
    }

    @Test
    fun recent_roles_prioritize_recent_usage() {
        val today = LocalDate.of(2026, 9, 18)
        val rows = listOf(
            row(today.minusDays(2), PerformanceType.TURNO, "Stiva", recentRule),
            row(today.minusDays(400), PerformanceType.TURNO, "Ralla", oldRule)
        )
        assertEquals("Stiva", recentRoles(rows, today).first())
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


    private fun rowWithRules(
        date: LocalDate,
        type: PerformanceType,
        role: String,
        rules: List<AllowanceRuleEntity>
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
            selectedRules = rules
        )
    }

    private fun turnRule(id: Long, code: String, name: String) = AllowanceRuleEntity(
        id = id,
        name = name,
        code = code,
        calculationType = AllowanceCalculationType.FIXED_PER_SHIFT,
        value = 100,
        category = AllowanceCategory.TURNO,
        applicationMode = AllowanceApplicationMode.MANUAL,
        exclusiveGroup = "TIPO_TURNO",
        performanceMask = PerformanceType.TURNO.maskBit
    )

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
