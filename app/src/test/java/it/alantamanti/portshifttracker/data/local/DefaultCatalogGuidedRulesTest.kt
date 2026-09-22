package it.alantamanti.portshifttracker.data.local

import it.alantamanti.portshifttracker.domain.PerformanceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCatalogGuidedRulesTest {

    @Test
    fun imaDisdettaChoicesAreMutuallyExclusive() {
        val rules = DefaultCatalog.rules()
        val casa = rules.firstOrNull { it.code == "AVV_DIS_CASA" }
        val festiva = rules.firstOrNull { it.code == "AVV_DIS_CASA_FEST" }

        assertNotNull(casa)
        assertNotNull(festiva)
        assertEquals("IMA_DISDETTA", casa?.exclusiveGroup)
        assertEquals("IMA_DISDETTA", festiva?.exclusiveGroup)
        assertEquals("Disdetta casa", casa?.name)
        assertEquals("Disdetta casa festiva", festiva?.name)
    }

    @Test
    fun fullAndHalfDoubleHaveMattinaAndNotteRules() {
        val rules = DefaultCatalog.rules()
        val expected = listOf("DOP_MAT", "DOP_MATF", "DOP_NOTTE", "DOP_NOTTEF")

        expected.forEach { code ->
            val rule = rules.firstOrNull { it.code == code }
            assertNotNull(code, rule)
            assertTrue((rule!!.performanceMask and PerformanceType.DOPPIO.maskBit) != 0)
            assertTrue((rule.performanceMask and PerformanceType.MEZZO_DOPPIO.maskBit) != 0)
        }
    }
}
