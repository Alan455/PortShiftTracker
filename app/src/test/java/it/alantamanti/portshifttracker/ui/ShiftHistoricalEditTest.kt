package it.alantamanti.portshifttracker.ui

import it.alantamanti.portshifttracker.data.local.ShiftEntity
import it.alantamanti.portshifttracker.domain.PerformanceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftHistoricalEditTest {
    private val original = ShiftEntity(
        id = 9,
        workerId = 1,
        startEpochMillis = 1790058600123L, // sub-minute precision must remain untouched
        endEpochMillis = 1790080200456L,
        role = "Operatore",
        notes = "Nota originale",
        performanceType = PerformanceType.TURNO
    )

    private fun unchanged(
        originalShift: ShiftEntity? = original,
        copy: Boolean = false,
        start: String = "22/09/2026 08:00",
        end: String = "22/09/2026 14:00",
        role: String = "Operatore",
        performanceType: PerformanceType = PerformanceType.TURNO,
        selected: Set<Long> = setOf(10L, 11L)
    ) = isNotesOnlyEdit(
        original = originalShift,
        isCopy = copy,
        originalStartText = "22/09/2026 08:00",
        originalEndText = "22/09/2026 14:00",
        startText = start,
        endText = end,
        role = role,
        performanceType = performanceType,
        initialNormalizedIds = setOf(10L, 11L),
        selectedNormalizedIds = selected
    )

    @Test fun noteOnlyEditPreservesOriginalEconomicInputs() {
        assertTrue(unchanged())
        // The UI compares unchanged display text, not re-parsed minute-precision epoch millis.
        assertTrue(unchanged(selected = setOf(11L, 10L)))
    }

    @Test fun economicChangesAndCopiesMustUseNormalSave() {
        assertFalse(unchanged(originalShift = null))
        assertFalse(unchanged(copy = true))
        assertFalse(unchanged(start = "22/09/2026 08:01"))
        assertFalse(unchanged(end = "22/09/2026 14:01"))
        assertFalse(unchanged(role = "Caposquadra"))
        assertFalse(unchanged(performanceType = PerformanceType.DOPPIO))
        assertFalse(unchanged(selected = setOf(10L)))
    }
}
