package it.alantamanti.portshifttracker.ui

import it.alantamanti.portshifttracker.domain.PerformanceType
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryCategoryTest {
    @Test
    fun ordinaryWorkedShiftAndGiornalieroAreTurni() {
        assertEquals(HistoryCategory.TURNI, historyCategory(PerformanceType.TURNO, setOf("MAT")))
        assertEquals(HistoryCategory.TURNI, historyCategory(PerformanceType.TURNO, setOf("G")))
    }

    @Test
    fun nonWorkedShiftsAreAbsences() {
        listOf("ALT_FERIE", "ALT_MALATTIA", "AVV_INAIL", "AVV_CONGEDO", "AVV_DS", "ALT_IMA")
            .forEach { code ->
                assertEquals(code, HistoryCategory.ASSENZE, historyCategory(PerformanceType.TURNO, setOf(code)))
            }
    }

    @Test
    fun halfFirstShiftIncludesOnMezzoGiornaliero() {
        assertEquals(
            HistoryCategory.MEZZI_PRIMI_TURNI,
            historyCategory(PerformanceType.TURNO, setOf("SERA2", "DOP_TU_MEZZO", "ALT_MEZZA_IMA"))
        )
        assertEquals(
            HistoryCategory.MEZZI_PRIMI_TURNI,
            historyCategory(PerformanceType.TURNO, setOf("G", "DOP_ON_MEZZO", "ALT_MEZZA_IMA"))
        )
    }

    @Test
    fun doublesAndHalfDoublesRemainSeparate() {
        assertEquals(HistoryCategory.DOPPI, historyCategory(PerformanceType.DOPPIO, setOf("DOP_SERA2")))
        assertEquals(HistoryCategory.DOPPI, historyCategory(PerformanceType.DOPPIO, setOf("DOP_G")))
        assertEquals(HistoryCategory.MEZZI_DOPPI, historyCategory(PerformanceType.MEZZO_DOPPIO, setOf("DOP_SERA2")))
    }

    @Test
    fun absenceTakesPrecedenceOverHalfFirstShiftIfBothAreSelected() {
        assertEquals(
            HistoryCategory.ASSENZE,
            historyCategory(PerformanceType.TURNO, setOf("ALT_FERIE", "DOP_ON_MEZZO"))
        )
    }
}
