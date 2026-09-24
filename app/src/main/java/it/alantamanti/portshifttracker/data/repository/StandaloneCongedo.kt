package it.alantamanti.portshifttracker.data.repository

import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.domain.PerformanceType

/**
 * Congedo replaces a worked shift: it is a complete first performance by itself.
 * Do not relax the ordinary selection validation for any other combination.
 */
internal fun isStandaloneCongedoSelection(
    performanceType: PerformanceType,
    selectedIds: Set<Long>,
    rules: List<AllowanceRuleEntity>
): Boolean {
    if (performanceType != PerformanceType.TURNO || selectedIds.size != 1) return false
    val selected = rules.firstOrNull { it.id == selectedIds.single() } ?: return false
    return selected.code == "AVV_CONGEDO" &&
        selected.enabled &&
        (selected.performanceMask and performanceType.maskBit) != 0
}
