package it.alantamanti.portshifttracker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GuidedEntryFlowTest {
    @Test fun empty_day_starts_with_work_or_absence() {
        assertEquals(GuidedDayState.FIRST, guidedDayState(false, false, false))
    }

    @Test fun worked_first_turn_allows_only_a_second() {
        assertEquals(GuidedDayState.SECOND, guidedDayState(true, false, false))
    }

    @Test fun an_absence_or_existing_second_blocks_additional_work() {
        assertEquals(GuidedDayState.BLOCKED, guidedDayState(false, true, false))
        assertEquals(GuidedDayState.BLOCKED, guidedDayState(true, false, true))
    }

    @Test fun only_first_giornaliero_has_onmezzo_option() {
        assertTrue(GuidedEntryChoice.WORK_GIORNALIERO.name.startsWith("WORK_"))
        assertFalse(GuidedEntryChoice.HALF_GIORNALIERO.isAbsence)
        assertTrue(GuidedEntryChoice.HALF_GIORNALIERO.isSecond)
        assertTrue(GuidedEntryChoice.HALF_DOUBLE.isSecond)
        assertFalse(GuidedEntryChoice.WORK_TURNO.isSecond)
    }

    @Test fun evening_and_s2_end_on_the_next_calendar_day() {
        val friday = LocalDate.of(2026, 9, 18)
        val sera = guidedShiftInterval(friday, QuickShiftKind.SERA)
        val s2 = guidedShiftInterval(friday, QuickShiftKind.SERA2)
        assertEquals(friday, sera.first.toLocalDate())
        assertEquals(friday.plusDays(1), sera.second.toLocalDate())
        assertEquals(1, sera.second.hour)
        assertEquals(friday.plusDays(1), s2.second.toLocalDate())
        assertEquals(2, s2.second.hour)
    }
}
