package it.alantamanti.portshifttracker.data.repository

import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.domain.AllowanceCalculationType
import it.alantamanti.portshifttracker.domain.PerformanceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandaloneCongedoTest {
    private val congedo = AllowanceRuleEntity(
        id = 100,
        code = "AVV_CONGEDO",
        name = "Congedo",
        calculationType = AllowanceCalculationType.FIXED_PER_SHIFT,
        value = 3000,
        performanceMask = PerformanceType.TURNO.maskBit
    )
    private val turno = congedo.copy(id = 200, code = "MAT", name = "Mattina")
    private val rules = listOf(congedo, turno)

    @Test fun guidedCongedoAloneIsSaveable() {
        val selected = setOf(congedo.id)
        assertTrue(isStandaloneCongedoSelection(PerformanceType.TURNO, selected, rules))
        // The guided and repository paths preserve the singleton before the
        // generic normalization, which is intended for worked-turn choices.
    }

    @Test fun mixedMissingDisabledAndDoubleAreNotExemptFromValidation() {
        assertFalse(isStandaloneCongedoSelection(PerformanceType.TURNO, emptySet(), rules))
        assertFalse(isStandaloneCongedoSelection(PerformanceType.TURNO, setOf(congedo.id, turno.id), rules))
        assertFalse(isStandaloneCongedoSelection(PerformanceType.TURNO, setOf(99), rules))
        assertFalse(isStandaloneCongedoSelection(PerformanceType.DOPPIO, setOf(congedo.id), rules))
        assertFalse(isStandaloneCongedoSelection(PerformanceType.TURNO, setOf(congedo.id), listOf(congedo.copy(enabled = false))))
    }
}
