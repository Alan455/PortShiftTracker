package it.alantamanti.portshifttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AllowanceCalculatorTest {
    private val calculator = AllowanceCalculator()
    private val worker = Worker(
        id = 1,
        name = "Test",
        hourlyRateCents = 0,
        basePayMode = BasePayMode.FIXED_PER_SHIFT,
        baseShiftCents = 6780,
        doubleBaseCents = 8840
    )

    private val turnoNotte = AllowanceRule(
        id = 1,
        name = "Notte",
        code = "NOTTE",
        calculationType = AllowanceCalculationType.FIXED_PER_SHIFT,
        value = 1136,
        category = AllowanceCategory.TURNO,
        applicationMode = AllowanceApplicationMode.MANUAL,
        performanceMask = PerformanceType.TURNO.maskBit
    )
    private val doppioSera = AllowanceRule(
        id = 2,
        name = "SeraS Doppio",
        code = "DOP_SERAS",
        calculationType = AllowanceCalculationType.FIXED_PER_SHIFT,
        value = 1562,
        category = AllowanceCategory.DOPPIO,
        applicationMode = AllowanceApplicationMode.MANUAL,
        performanceMask = PerformanceType.DOPPIO.maskBit or PerformanceType.MEZZO_DOPPIO.maskBit
    )
    private val polivalenza = AllowanceRule(
        id = 3,
        name = "Polivalenza",
        code = "ALT_POLIVALENZA",
        calculationType = AllowanceCalculationType.FIXED_PER_SHIFT,
        value = 852,
        category = AllowanceCategory.ALTRE_VOCI,
        applicationMode = AllowanceApplicationMode.AUTO,
        autoTrigger = AllowanceAutoTrigger.WHEN_TURNO_SELECTED,
        performanceMask = PerformanceType.TURNO.maskBit
    )
    private val area = AllowanceRule(
        id = 4,
        name = "A5",
        code = "AREA_A5",
        calculationType = AllowanceCalculationType.FIXED_PER_SHIFT,
        value = 1330,
        category = AllowanceCategory.AREA,
        applicationMode = AllowanceApplicationMode.MANUAL,
        performanceMask = 7
    )
    private val disagio = AllowanceRule(
        id = 5,
        name = "Tubi",
        code = "DIS_TUBI",
        calculationType = AllowanceCalculationType.FIXED_PER_SHIFT,
        value = 775,
        category = AllowanceCategory.DISAGIO,
        applicationMode = AllowanceApplicationMode.MANUAL,
        performanceMask = 7
    )
    private val mezzaIma = AllowanceRule(
        id = 6,
        name = "Mezza IMA",
        code = "ALT_MEZZA_IMA",
        calculationType = AllowanceCalculationType.FIXED_PER_SHIFT,
        value = 2615,
        category = AllowanceCategory.ALTRE_VOCI,
        applicationMode = AllowanceApplicationMode.MANUAL,
        turnAllowanceMultiplierBasisPoints = 5000,
        performanceMask = PerformanceType.TURNO.maskBit
    )

    private val sera2 = AllowanceRule(
        id = 7,
        name = "Sera2",
        code = "SERA2",
        calculationType = AllowanceCalculationType.FIXED_PER_SHIFT,
        value = 1326,
        category = AllowanceCategory.TURNO,
        applicationMode = AllowanceApplicationMode.MANUAL,
        performanceMask = PerformanceType.TURNO.maskBit
    )
    private val tuMezzo = AllowanceRule(
        id = 8,
        name = "TUMezzo",
        code = "DOP_TU_MEZZO",
        calculationType = AllowanceCalculationType.FIXED_PER_SHIFT,
        value = 3390,
        category = AllowanceCategory.MEZZO_TURNO,
        applicationMode = AllowanceApplicationMode.MANUAL,
        basePayEffect = BasePayEffect.REPLACE_BASE,
        turnAllowanceMultiplierBasisPoints = 5000,
        performanceMask = PerformanceType.TURNO.maskBit
    )

    private val rules = listOf(turnoNotte, doppioSera, polivalenza, area, disagio, mezzaIma)

    @Test
    fun turnoLavoratoRicevePolivalenza() {
        val pay = calculator.calculate(
            worker,
            shift(PerformanceType.TURNO, "2026-09-17T20:00:00", "2026-09-18T02:00:00"),
            rules,
            setOf(1, 4, 5)
        )

        assertEquals(6780, pay.basePayCents)
        assertTrue(pay.allowanceLines.any { it.name == "Polivalenza" && it.amountCents == 852L })
        assertEquals(10873, pay.totalPayCents)
    }

    @Test
    fun tuMezzoSostituisceBaseEDimezzaSoloIndennitaTurno() {
        val pay = calculator.calculate(
            worker,
            shift(PerformanceType.TURNO, "2026-09-17T18:00:00", "2026-09-17T21:00:00"),
            listOf(sera2, tuMezzo, mezzaIma),
            // Mezza IMA non viene selezionata esplicitamente: deve aggiungerla il motore.
            setOf(7, 8)
        )

        assertEquals(3390, pay.basePayCents)
        assertTrue(pay.allowanceLines.any { it.name == "Sera2" && it.amountCents == 663L })
        assertTrue(pay.allowanceLines.any { it.name == "Mezza IMA" && it.amountCents == 2615L })
        assertEquals(6668, pay.totalPayCents)
    }

    @Test
    fun doppioUsaBase8840ConAreaDisagioMaSenzaPolivalenzaOMezzaIma() {
        val pay = calculator.calculate(
            worker,
            shift(PerformanceType.DOPPIO, "2026-09-17T12:00:00", "2026-09-17T18:00:00"),
            rules,
            // Incluse volutamente voci incompatibili: devono essere ignorate.
            setOf(1, 2, 3, 4, 5, 6)
        )

        assertEquals(8840, pay.basePayCents)
        assertTrue(pay.allowanceLines.any { it.name == "SeraS Doppio" })
        assertTrue(pay.allowanceLines.any { it.name == "A5" })
        assertTrue(pay.allowanceLines.any { it.name == "Tubi" })
        assertFalse(pay.allowanceLines.any { it.name == "Polivalenza" })
        assertFalse(pay.allowanceLines.any { it.name == "Mezza IMA" })
        assertFalse(pay.allowanceLines.any { it.name == "Notte" })
        assertEquals(12507, pay.totalPayCents)
    }

    @Test
    fun mezzoDoppioUsaMetaBaseDimezzaSoloIndennitaDoppioEMantieneAreaDisagioInteri() {
        val pay = calculator.calculate(
            worker,
            shift(PerformanceType.MEZZO_DOPPIO, "2026-09-17T18:00:00", "2026-09-17T21:00:00"),
            rules,
            // Includiamo anche Polivalenza e Mezza IMA: devono essere ignorate.
            setOf(2, 3, 4, 5, 6)
        )

        assertEquals(4420, pay.basePayCents)
        assertTrue(pay.allowanceLines.any { it.name == "SeraS Doppio" && it.amountCents == 781L })
        assertTrue(pay.allowanceLines.any { it.name == "A5" && it.amountCents == 1330L })
        assertTrue(pay.allowanceLines.any { it.name == "Tubi" && it.amountCents == 775L })
        assertFalse(pay.allowanceLines.any { it.name == "Polivalenza" })
        assertFalse(pay.allowanceLines.any { it.name == "Mezza IMA" })
        assertEquals(7306, pay.totalPayCents)
    }

    private fun shift(type: PerformanceType, start: String, end: String): Shift = Shift(
        id = 1,
        workerId = 1,
        startEpochMillis = epoch(start),
        endEpochMillis = epoch(end),
        performanceType = type
    )

    private fun epoch(value: String): Long =
        LocalDateTime.parse(value).atZone(ZoneId.of("Europe/Rome")).toInstant().toEpochMilli()
}
