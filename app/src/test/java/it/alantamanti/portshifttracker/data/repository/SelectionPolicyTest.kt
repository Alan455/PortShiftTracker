package it.alantamanti.portshifttracker.data.repository

import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.domain.AllowanceApplicationMode
import it.alantamanti.portshifttracker.domain.AllowanceCalculationType
import it.alantamanti.portshifttracker.domain.AllowanceCategory
import it.alantamanti.portshifttracker.domain.PerformanceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionPolicyTest {
    private val sera2 = rule(1, "SERA2", AllowanceCategory.TURNO)
    private val tuMezzo = rule(2, "DOP_TU_MEZZO", AllowanceCategory.MEZZO_TURNO)
    private val onMezzo = rule(3, "DOP_ON_MEZZO", AllowanceCategory.MEZZO_TURNO)
    private val mezzaIma = rule(4, "ALT_MEZZA_IMA", AllowanceCategory.ALTRE_VOCI)
    private val ferie = rule(5, "ALT_FERIE", AllowanceCategory.ALTRE_VOCI)
    private val doppioSera = rule(
        6,
        "DOP_SERA",
        AllowanceCategory.DOPPIO,
        mask = PerformanceType.DOPPIO.maskBit or PerformanceType.MEZZO_DOPPIO.maskBit
    )
    private val rules = listOf(sera2, tuMezzo, onMezzo, mezzaIma, ferie, doppioSera)

    @Test
    fun tuMezzo_adds_mezzaIma_automatically() {
        val result = normalizeSelectedRuleIds(
            PerformanceType.TURNO,
            setOf(sera2.id, tuMezzo.id),
            rules
        )
        assertTrue(mezzaIma.id in result)
    }

    @Test
    fun onMezzo_adds_mezzaIma_automatically() {
        val result = normalizeSelectedRuleIds(
            PerformanceType.TURNO,
            setOf(sera2.id, onMezzo.id),
            rules
        )
        assertTrue(mezzaIma.id in result)
    }

    @Test
    fun isolated_mezzaIma_is_removed() {
        val result = normalizeSelectedRuleIds(
            PerformanceType.TURNO,
            setOf(sera2.id, mezzaIma.id),
            rules
        )
        assertEquals(setOf(sera2.id), result)
    }

    @Test
    fun ordinary_turn_requires_turn_kind_but_ferie_does_not() {
        assertTrue(
            selectionValidationMessage(PerformanceType.TURNO, emptySet(), rules)
                ?.contains("Seleziona il turno") == true
        )
        assertNull(selectionValidationMessage(PerformanceType.TURNO, setOf(ferie.id), rules))
    }

    @Test
    fun doppio_requires_its_own_turn_kind() {
        assertTrue(selectionValidationMessage(PerformanceType.DOPPIO, emptySet(), rules) != null)
        assertNull(selectionValidationMessage(PerformanceType.DOPPIO, setOf(doppioSera.id), rules))
    }

    private fun rule(
        id: Long,
        code: String,
        category: AllowanceCategory,
        mask: Int = PerformanceType.TURNO.maskBit
    ) = AllowanceRuleEntity(
        id = id,
        name = code,
        code = code,
        calculationType = AllowanceCalculationType.FIXED_PER_SHIFT,
        value = 0,
        enabled = true,
        category = category,
        applicationMode = AllowanceApplicationMode.MANUAL,
        performanceMask = mask
    )
}
