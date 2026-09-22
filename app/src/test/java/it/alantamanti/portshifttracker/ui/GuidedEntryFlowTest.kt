package it.alantamanti.portshifttracker.ui

import it.alantamanti.portshifttracker.domain.PerformanceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedEntryFlowTest {

    @Test
    fun firstWorkAndAbsencesUseTurnoPerformance() {
        listOf(
            GuidedEntryKind.FIRST_TURNO,
            GuidedEntryKind.FIRST_GIORNALIERO,
            GuidedEntryKind.ABS_FERIE,
            GuidedEntryKind.ABS_MALATTIA,
            GuidedEntryKind.ABS_CONGEDO,
            GuidedEntryKind.ABS_IMA
        ).forEach {
            assertEquals(PerformanceType.TURNO, it.initialPerformanceType())
        }
    }

    @Test
    fun secondShiftChoicesMapToCorrectStructuredPerformance() {
        assertEquals(PerformanceType.DOPPIO, GuidedEntryKind.SECOND_DOPPIO.initialPerformanceType())
        assertEquals(PerformanceType.MEZZO_DOPPIO, GuidedEntryKind.SECOND_MEZZO_DOPPIO.initialPerformanceType())
        assertEquals(PerformanceType.DOPPIO, GuidedEntryKind.SECOND_MEZZO_GIORNALIERO.initialPerformanceType())
    }

    @Test
    fun onlyGiornalieroAndAbsencesAndHalfDailyHaveInitialRuleCodes() {
        assertEquals(setOf("G"), GuidedEntryKind.FIRST_GIORNALIERO.initialRuleCodes())
        assertEquals(setOf("ALT_FERIE"), GuidedEntryKind.ABS_FERIE.initialRuleCodes())
        assertEquals(setOf("ALT_MALATTIA"), GuidedEntryKind.ABS_MALATTIA.initialRuleCodes())
        assertEquals(setOf("AVV_CONGEDO"), GuidedEntryKind.ABS_CONGEDO.initialRuleCodes())
        assertEquals(setOf("ALT_IMA"), GuidedEntryKind.ABS_IMA.initialRuleCodes())
        assertEquals(setOf("DOP_G"), GuidedEntryKind.SECOND_MEZZO_GIORNALIERO.initialRuleCodes())
        assertTrue(GuidedEntryKind.FIRST_TURNO.initialRuleCodes().isEmpty())
        assertTrue(GuidedEntryKind.SECOND_DOPPIO.initialRuleCodes().isEmpty())
        assertTrue(GuidedEntryKind.SECOND_MEZZO_DOPPIO.initialRuleCodes().isEmpty())
    }

    @Test
    fun absenceClassificationIsLimitedToAbsenceBranches() {
        assertTrue(GuidedEntryKind.ABS_FERIE.isAbsence())
        assertTrue(GuidedEntryKind.ABS_MALATTIA.isAbsence())
        assertTrue(GuidedEntryKind.ABS_CONGEDO.isAbsence())
        assertTrue(GuidedEntryKind.ABS_IMA.isAbsence())
        assertFalse(GuidedEntryKind.FIRST_TURNO.isAbsence())
        assertFalse(GuidedEntryKind.SECOND_DOPPIO.isAbsence())
    }
}
