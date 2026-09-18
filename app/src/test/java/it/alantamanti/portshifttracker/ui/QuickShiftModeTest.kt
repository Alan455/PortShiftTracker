package it.alantamanti.portshifttracker.ui

import it.alantamanti.portshifttracker.domain.PerformanceType
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
    fun giornaliero_is_available_with_the_other_quick_shifts() {
        assertEquals("G", resolveQuickShift(QuickShiftKind.GIORNALIERO, LocalDate.of(2026, 9, 18)).ruleCode)
        assertEquals("G", resolveQuickShift(QuickShiftKind.GIORNALIERO, LocalDate.of(2026, 9, 20)).ruleCode)
    }

    @Test
    fun doppio_shows_only_pom_sera_and_sera2() {
        assertEquals(
            listOf(QuickShiftKind.POMERIGGIO, QuickShiftKind.SERA, QuickShiftKind.SERA2),
            quickShiftKindsFor(PerformanceType.DOPPIO)
        )
    }

    @Test
    fun doppio_pomeriggio_chooses_modifier_from_date() {
        val weekday = resolveQuickShift(
            QuickShiftKind.POMERIGGIO,
            LocalDate.of(2026, 9, 18),
            performanceType = PerformanceType.DOPPIO
        )
        val saturday = resolveQuickShift(
            QuickShiftKind.POMERIGGIO,
            LocalDate.of(2026, 9, 19),
            performanceType = PerformanceType.DOPPIO
        )
        val holiday = resolveQuickShift(
            QuickShiftKind.POMERIGGIO,
            LocalDate.of(2026, 9, 20),
            performanceType = PerformanceType.DOPPIO
        )

        assertEquals("DOP_POM", weekday.ruleCode)
        assertEquals("DOP_POMS", saturday.ruleCode)
        assertEquals("DOP_POMF", holiday.ruleCode)
    }

    @Test
    fun doppio_sera2_chooses_saturday_and_holiday_variants() {
        val saturday = resolveQuickShift(
            QuickShiftKind.SERA2,
            LocalDate.of(2026, 9, 19),
            performanceType = PerformanceType.DOPPIO
        )
        val holiday = resolveQuickShift(
            QuickShiftKind.SERA2,
            LocalDate.of(2026, 9, 20),
            performanceType = PerformanceType.DOPPIO
        )

        assertEquals("DOP_SERAS2", saturday.ruleCode)
        assertEquals("DOP_SERAF2", holiday.ruleCode)
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
