package it.alantamanti.portshifttracker.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class QuickShiftModeTest {

    @Test
    fun pomeriggio_changes_with_weekday_saturday_and_holiday() {
        assertEquals("POM", resolveQuickShift(QuickShiftKind.POMERIGGIO, LocalDate.of(2026, 9, 18)).ruleCode)
        assertEquals("POMS", resolveQuickShift(QuickShiftKind.POMERIGGIO, LocalDate.of(2026, 9, 19)).ruleCode)
        assertEquals("POMF", resolveQuickShift(QuickShiftKind.POMERIGGIO, LocalDate.of(2026, 9, 20)).ruleCode)
    }

    @Test
    fun sera2_uses_requested_compact_labels() {
        assertEquals("Sera2", resolveQuickShift(QuickShiftKind.SERA2, LocalDate.of(2026, 9, 18)).compactCode)
        assertEquals("SeraS2", resolveQuickShift(QuickShiftKind.SERA2, LocalDate.of(2026, 9, 19)).compactCode)
        val festive = resolveQuickShift(QuickShiftKind.SERA2, LocalDate.of(2026, 9, 20))
        assertEquals("SERAF2", festive.ruleCode)
        assertEquals("Sera2F", festive.compactCode)
    }

    @Test
    fun ravenna_patron_and_port_november_fourth_are_festive() {
        assertEquals(PortDayClass.FESTIVO, portDayClass(LocalDate.of(2026, 7, 23)))
        assertEquals(PortDayClass.FESTIVO, portDayClass(LocalDate.of(2026, 11, 4)))
    }

    @Test
    fun saint_francis_is_festive_from_2026() {
        assertEquals(PortDayClass.FESTIVO, portDayClass(LocalDate.of(2027, 10, 4)))
    }

    @Test
    fun christmas_eve_afternoon_is_port_semi_festive_but_morning_is_not() {
        val pom = resolveQuickShift(QuickShiftKind.POMERIGGIO, LocalDate.of(2026, 12, 24))
        val mat = resolveQuickShift(QuickShiftKind.MATTINA, LocalDate.of(2026, 12, 24))
        assertEquals(PortDayClass.SEMIFESTIVO, pom.dayClass)
        assertEquals("POMF", pom.ruleCode)
        assertEquals("MAT", mat.ruleCode)
    }
}
