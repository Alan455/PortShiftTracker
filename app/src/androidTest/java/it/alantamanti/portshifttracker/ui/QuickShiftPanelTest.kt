package it.alantamanti.portshifttracker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.domain.AllowanceApplicationMode
import it.alantamanti.portshifttracker.domain.AllowanceCalculationType
import it.alantamanti.portshifttracker.domain.AllowanceCategory
import it.alantamanti.portshifttracker.domain.PerformanceType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class QuickShiftPanelTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun doppio_saturday_shows_only_three_choices_and_automatic_modifiers() {
        compose.setContent {
            MaterialTheme {
                QuickShiftPanel(
                    date = LocalDate.of(2026, 9, 19),
                    rules = doppioRules(),
                    selectedKind = null,
                    performanceType = PerformanceType.DOPPIO,
                    doubleBaseCents = 8840,
                    onKindSelected = {}
                )
            }
        }

        compose.onNodeWithText("Pom").fetchSemanticsNode()
        compose.onNodeWithText("Sera").fetchSemanticsNode()
        compose.onNodeWithText("Sera2").fetchSemanticsNode()
        assertTrue(compose.onAllNodesWithText("Mattina").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Notte").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun old_doppio_g_is_shown_as_historical_not_as_new_choice() {
        val rules = doppioRules() + rule("DOP_G", "G", 5300)
        compose.setContent {
            MaterialTheme {
                QuickShiftPanel(
                    date = LocalDate.of(2026, 9, 18),
                    rules = rules,
                    selectedKind = QuickShiftKind.GIORNALIERO,
                    performanceType = PerformanceType.DOPPIO,
                    doubleBaseCents = 8840,
                    onKindSelected = {}
                )
            }
        }

        compose.onNodeWithText("Voce storica: G").fetchSemanticsNode()
    }

    private fun doppioRules() = listOf(
        rule("DOP_POM", "Pom", 0),
        rule("DOP_POMS", "PomS", 1136),
        rule("DOP_POMF", "PomF", 4906),
        rule("DOP_SERA", "Sera", 426),
        rule("DOP_SERAS", "SeraS", 1562),
        rule("DOP_SERAF", "SeraF", 5219),
        rule("DOP_SERA2", "Sera2", 1326),
        rule("DOP_SERAS2", "SeraS2", 2462),
        rule("DOP_SERAF2", "SeraF2", 6119)
    )

    private fun rule(code: String, name: String, cents: Long) = AllowanceRuleEntity(
        name = name,
        code = code,
        calculationType = AllowanceCalculationType.FIXED_PER_SHIFT,
        value = cents,
        category = AllowanceCategory.DOPPIO,
        applicationMode = AllowanceApplicationMode.MANUAL,
        exclusiveGroup = "DOPPIO",
        performanceMask = PerformanceType.DOPPIO.maskBit
    )
}
