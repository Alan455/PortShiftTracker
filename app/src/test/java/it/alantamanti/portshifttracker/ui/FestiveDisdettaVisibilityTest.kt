package it.alantamanti.portshifttracker.ui

import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.domain.AllowanceCalculationType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FestiveDisdettaVisibilityTest {
    private fun rule(code: String, name: String) = AllowanceRuleEntity(
        id = 10, code = code, name = name,
        calculationType = AllowanceCalculationType.FIXED_PER_SHIFT, value = 1500
    )

    @Test fun ferialeSeptember17DoesNotOfferFestiveDisdetta() {
        val date = LocalDate.of(2026, 9, 17)
        val festiva = rule("AVV_DIS_CASA_FEST", "Disdetta casa festiva")
        assertTrue(portDayClass(date) == PortDayClass.FERIALE)
        assertTrue(isFestiveDisdettaRule(festiva))
        assertFalse(!isFestiveDisdettaRule(festiva) || portDayClass(date) == PortDayClass.FESTIVO)
    }

    @Test fun festiveAndLegacyNamesAreRecognizedWithoutHidingOrdinaryDisdetta() {
        assertTrue(isFestiveDisdettaRule(rule("AVV_DIS_CASA_FEST", "Vecchia etichetta")))
        assertTrue(isFestiveDisdettaRule(rule("CUSTOM_FEST", " Disdetta casa festiva ")))
        assertFalse(isFestiveDisdettaRule(rule("AVV_DIS_CASA", "Disdetta casa")))
        assertTrue(portDayClass(LocalDate.of(2026, 9, 20)) == PortDayClass.FESTIVO)
    }
}
