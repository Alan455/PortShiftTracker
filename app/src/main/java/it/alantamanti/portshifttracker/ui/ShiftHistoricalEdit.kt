package it.alantamanti.portshifttracker.ui

import it.alantamanti.portshifttracker.data.local.ShiftEntity
import it.alantamanti.portshifttracker.domain.PerformanceType

/**
 * Compare the original edit form to the current form BEFORE parsing display
 * timestamps. Parsing to minute precision can silently change stored seconds
 * and make a notes-only edit look like an economic edit.
 */
internal fun isNotesOnlyEdit(
    original: ShiftEntity?,
    isCopy: Boolean,
    originalStartText: String,
    originalEndText: String,
    startText: String,
    endText: String,
    role: String,
    performanceType: PerformanceType,
    initialNormalizedIds: Set<Long>,
    selectedNormalizedIds: Set<Long>
): Boolean = original != null && !isCopy &&
    startText == originalStartText &&
    endText == originalEndText &&
    role == original.role &&
    performanceType == original.performanceType &&
    selectedNormalizedIds == initialNormalizedIds
