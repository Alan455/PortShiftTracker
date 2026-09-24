package it.alantamanti.portshifttracker.data.repository

import androidx.room.withTransaction
import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.data.local.AppDatabase
import it.alantamanti.portshifttracker.data.local.ShiftAllowanceSelectionEntity
import it.alantamanti.portshifttracker.data.local.ShiftEntity
import it.alantamanti.portshifttracker.data.local.ShiftPaySnapshotEntity
import it.alantamanti.portshifttracker.data.local.WorkerEntity
import it.alantamanti.portshifttracker.domain.AllowanceCalculator
import it.alantamanti.portshifttracker.domain.AllowanceRule
import it.alantamanti.portshifttracker.domain.PayBreakdown
import it.alantamanti.portshifttracker.domain.Shift
import it.alantamanti.portshifttracker.domain.Worker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

class PortRepository(
    private val db: AppDatabase,
    private val calculator: AllowanceCalculator = AllowanceCalculator()
) {
    private val workerDao = db.workerDao()
    private val shiftDao = db.shiftDao()
    private val ruleDao = db.allowanceRuleDao()
    private val selectionDao = db.shiftAllowanceSelectionDao()
    private val paySnapshotDao = db.shiftPaySnapshotDao()
    // A short undo must restore the original frozen amounts, not recalculate.
    private val pendingUndoSnapshots = mutableMapOf<Long, ShiftPaySnapshotEntity>()

    val workers: Flow<List<WorkerEntity>> = workerDao.observeAll()
    val shifts: Flow<List<ShiftEntity>> = shiftDao.observeAll()
    val rules: Flow<List<AllowanceRuleEntity>> = ruleDao.observeAll()
    val selections: Flow<List<ShiftAllowanceSelectionEntity>> = selectionDao.observeAll()

    val shiftRows: Flow<List<ShiftWithPay>> =
        combine(workers, shifts, rules, selections, paySnapshotDao.observeAll()) { ws, ss, rs, sels, snaps ->
            buildRows(ws, ss, rs, sels, snaps)
        }

    fun shiftRowsBetween(startInclusive: Long, endExclusive: Long): Flow<List<ShiftWithPay>> =
        combine(
            workers,
            shiftDao.observeBetween(startInclusive, endExclusive),
            rules,
            selectionDao.observeForShiftRange(startInclusive, endExclusive),
            paySnapshotDao.observeForShiftRange(startInclusive, endExclusive)
        ) { ws, ss, rs, sels, snaps ->
            buildRows(ws, ss, rs, sels, snaps)
        }

    private fun buildRows(
        ws: List<WorkerEntity>,
        ss: List<ShiftEntity>,
        rs: List<AllowanceRuleEntity>,
        sels: List<ShiftAllowanceSelectionEntity>,
        snapshots: List<ShiftPaySnapshotEntity>
    ): List<ShiftWithPay> {
        val workerMap = ws.associateBy { it.id }
        val rulesById = rs.associateBy { it.id }
        val snapshotsByShiftId = snapshots.associateBy { it.shiftId }
        val domainRules = rs.map { it.toDomain() }
        val selectedByShift = sels.groupBy { it.shiftId }.mapValues { (_, rows) -> rows.map { it.ruleId }.toSet() }

        return ss.mapNotNull { shift ->
            val worker = workerMap[shift.workerId] ?: return@mapNotNull null
            val selectedIds = normalizeSelectedRuleIds(
                shift.performanceType,
                selectedByShift[shift.id].orEmpty(),
                rs
            )
            val breakdown = snapshotsByShiftId[shift.id]?.toBreakdown()
                ?: calculator.calculate(worker.toDomain(), shift.toDomain(), domainRules, selectedIds)
            val selectedRules = selectedIds.mapNotNull { rulesById[it] }
                .sortedWith(compareBy<AllowanceRuleEntity> { it.category.ordinal }.thenBy { it.priority }.thenBy { it.name })
            ShiftWithPay(shift, worker, breakdown, selectedRules)
        }
    }

    /**
     * Freeze legacy records once, after known bug fixes, before a user can
     * change a rate. A snapshot is never replaced by an ordinary rate edit.
     */
    suspend fun backfillMissingPaySnapshots() = db.withTransaction {
        backfillMissingPaySnapshotsWithinTransaction()
    }

    private suspend fun backfillMissingPaySnapshotsWithinTransaction() {
        val existing = paySnapshotDao.getAll()
        val rows = buildRows(
            workerDao.getAll(), shiftDao.getAll(), ruleDao.getAll(),
            selectionDao.getAll(), existing
        )
        val savedIds = existing.mapTo(mutableSetOf()) { it.shiftId }
        rows.filter { it.shift.id !in savedIds }.forEach { row ->
            paySnapshotDao.upsert(ShiftPaySnapshotEntity.fromBreakdown(row.shift.id, row.pay))
        }
    }

    suspend fun saveWorker(worker: WorkerEntity) = db.withTransaction {
        backfillMissingPaySnapshotsWithinTransaction()
        workerDao.upsert(worker)
    }

    suspend fun addShiftWithSelections(shift: ShiftEntity, selectedRuleIds: Set<Long>): Long =
        addShiftsWithSelections(listOf(shift), selectedRuleIds).single()

    /**
     * Salva un intero periodo (es. Ferie/Malattia) in una sola transazione:
     * o vengono inserite tutte le giornate, oppure nessuna.
     */
    suspend fun addShiftsWithSelections(
        shifts: List<ShiftEntity>,
        selectedRuleIds: Set<Long>
    ): List<Long> = db.withTransaction {
        require(shifts.isNotEmpty()) { "Nessuna prestazione da salvare" }
        val allRules = ruleDao.getAll()
        val workerById = workerDao.getAll().associateBy { it.id }
        val domainRules = allRules.map { it.toDomain() }
        val seen = mutableSetOf<Triple<Long, LocalDate, it.alantamanti.portshifttracker.domain.PerformanceType>>()
        val result = mutableListOf<Long>()

        shifts.forEach { rawShift ->
            val shift = canonicalShift(rawShift)
            val date = localDateOf(shift)
            val key = Triple(shift.workerId, date, shift.performanceType)
            if (!seen.add(key)) throw DuplicatePerformanceException(date, shift.performanceType)
            ensureNoDuplicate(shift)

            val normalized =
                if (isStandaloneCongedoSelection(shift.performanceType, selectedRuleIds, allRules)) {
                    selectedRuleIds
                } else normalizeSelectedRuleIds(shift.performanceType, selectedRuleIds, allRules)
            if (!isStandaloneCongedoSelection(shift.performanceType, normalized, allRules)) {
                selectionValidationMessage(shift.performanceType, normalized, allRules)?.let {
                    throw IllegalArgumentException(it)
                }
            }

            val shiftId = shiftDao.insert(shift)
            if (normalized.isNotEmpty()) {
                selectionDao.insertAll(normalized.map { ShiftAllowanceSelectionEntity(shiftId, it) })
            }
            val worker = requireNotNull(workerById[shift.workerId]) { "Lavoratore inesistente" }
            val pay = calculator.calculate(
                worker.toDomain(), shift.copy(id = shiftId).toDomain(), domainRules, normalized
            )
            paySnapshotDao.upsert(ShiftPaySnapshotEntity.fromBreakdown(shiftId, pay))
            result += shiftId
        }
        result
    }

    suspend fun updateShiftWithSelections(
        shift: ShiftEntity,
        selectedRuleIds: Set<Long>
    ) = db.withTransaction {
        val allRules = ruleDao.getAll()
        val canonical = canonicalShift(shift)
        ensureNoDuplicate(canonical, excludeId = canonical.id)
        val normalized =
            if (isStandaloneCongedoSelection(canonical.performanceType, selectedRuleIds, allRules)) {
                selectedRuleIds
            } else normalizeSelectedRuleIds(canonical.performanceType, selectedRuleIds, allRules)
        if (!isStandaloneCongedoSelection(shift.performanceType, normalized, allRules)) {
            selectionValidationMessage(shift.performanceType, normalized, allRules)?.let {
                throw IllegalArgumentException(it)
            }
        }

        val previous = shiftDao.getAll().firstOrNull { it.id == canonical.id }
        val previousSelected = selectionDao.getAll()
            .filter { it.shiftId == canonical.id }.mapTo(mutableSetOf()) { it.ruleId }
        val amountsAffected = previous == null ||
            previous.workerId != canonical.workerId ||
            previous.startEpochMillis != canonical.startEpochMillis ||
            previous.endEpochMillis != canonical.endEpochMillis ||
            previous.zoneId != canonical.zoneId ||
            previous.role != canonical.role ||
            previous.performanceType != canonical.performanceType ||
            previousSelected != normalized
        shiftDao.update(canonical)
        selectionDao.deleteForShift(canonical.id)
        if (normalized.isNotEmpty()) {
            selectionDao.insertAll(normalized.map { ShiftAllowanceSelectionEntity(canonical.id, it) })
        }
        // Editing only notes/service-day metadata must not recalculate past pay.
        if (amountsAffected || paySnapshotDao.findByShiftId(canonical.id) == null) {
            val worker = requireNotNull(workerDao.getAll().firstOrNull { it.id == canonical.workerId })
            val pay = calculator.calculate(
                worker.toDomain(), canonical.toDomain(), allRules.map { it.toDomain() }, normalized
            )
            paySnapshotDao.upsert(ShiftPaySnapshotEntity.fromBreakdown(canonical.id, pay))
        }
    }

    private suspend fun ensureNoDuplicate(shift: ShiftEntity, excludeId: Long = 0) {
        val date = localDateOf(shift)
        val zone = ZoneId.of(shift.zoneId)
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val duplicate = shiftDao.findSameTypeInDay(
            workerId = shift.workerId,
            performanceType = shift.performanceType,
            serviceEpochDay = shift.serviceEpochDay ?: date.toEpochDay(),
            startInclusive = start,
            endExclusive = end,
            excludeId = excludeId
        )
        if (duplicate != null) throw DuplicatePerformanceException(date, shift.performanceType)
    }

    private fun localDateOf(shift: ShiftEntity): LocalDate =
        Instant.ofEpochMilli(shift.startEpochMillis).atZone(ZoneId.of(shift.zoneId)).toLocalDate()

    private fun canonicalShift(shift: ShiftEntity): ShiftEntity =
        shift.copy(serviceEpochDay = localDateOf(shift).toEpochDay())

    suspend fun deleteShift(shift: ShiftEntity) = db.withTransaction {
        paySnapshotDao.findByShiftId(shift.id)?.let { pendingUndoSnapshots[shift.id] = it }
        shiftDao.delete(shift)
    }

    suspend fun restoreDeletedShift(shift: ShiftEntity, selectedRuleIds: Set<Long>) = db.withTransaction {
        val canonical = canonicalShift(shift)
        shiftDao.insert(canonical)
        if (selectedRuleIds.isNotEmpty()) {
            selectionDao.insertAll(selectedRuleIds.map { ShiftAllowanceSelectionEntity(canonical.id, it) })
        }
        val previousSnapshot = pendingUndoSnapshots.remove(canonical.id)
        if (previousSnapshot != null) {
            paySnapshotDao.upsert(previousSnapshot)
        } else {
            val rules = ruleDao.getAll()
            val worker = requireNotNull(workerDao.getAll().firstOrNull { it.id == canonical.workerId })
            val pay = calculator.calculate(
                worker.toDomain(), canonical.toDomain(), rules.map { it.toDomain() }, selectedRuleIds
            )
            paySnapshotDao.upsert(ShiftPaySnapshotEntity.fromBreakdown(canonical.id, pay))
        }
    }

    suspend fun saveRule(rule: AllowanceRuleEntity) = db.withTransaction {
        backfillMissingPaySnapshotsWithinTransaction()
        val storedRule = if (rule.code == "DOP_G" || rule.code == "DOP_ON_MEZZO") {
            val giornaliero = ruleDao.getAll().firstOrNull { it.code == "G" }
            if (giornaliero != null) rule.copy(value = (giornaliero.value / 2.0).roundToLong())
            else rule
        } else rule
        ruleDao.upsert(
            if (storedRule.code in setOf("G", "DOP_G", "DOP_ON_MEZZO", "DOP_TU_MEZZO")) {
                storedRule.copy(
                    calculationType = it.alantamanti.portshifttracker.domain.AllowanceCalculationType.FIXED_PER_SHIFT
                )
            } else storedRule
        )
        // DOP_G and ONMezzo are derived from the current Giornaliero base.
        if (rule.code == "G") {
            val half = ((rule.value / 2.0).roundToLong())
            ruleDao.getAll().filter { it.code == "DOP_G" || it.code == "DOP_ON_MEZZO" }
                .forEach { derived -> ruleDao.upsert(derived.copy(value = half)) }
        }
    }
    /**
     * Explicit economic bug correction, not an ordinary tariff edit. It updates
     * only lines for one rule across all previously recorded performances.
     * Callers must supply the verified rule-specific correction formula.
     */
    suspend fun correctHistoricAllowanceLine(
        ruleCode: String,
        correctedAmount: (ShiftEntity, Long) -> Long
    ): Int = db.withTransaction {
        backfillMissingPaySnapshotsWithinTransaction()
        val matchingIds = ruleDao.getAll()
            .filter { it.code == ruleCode }.mapTo(mutableSetOf()) { it.id }
        if (matchingIds.isEmpty()) return@withTransaction 0
        val shiftsById = shiftDao.getAll().associateBy { it.id }
        var corrections = 0
        paySnapshotDao.getAll().forEach { snapshot ->
            val shift = shiftsById[snapshot.shiftId] ?: return@forEach
            val old = snapshot.toBreakdown()
            val revised = old.allowanceLines.map { line ->
                if (line.ruleId in matchingIds) {
                    line.copy(amountCents = correctedAmount(shift, line.amountCents))
                } else line
            }
            if (old.allowanceLines != revised) {
                paySnapshotDao.upsert(
                    ShiftPaySnapshotEntity.fromBreakdown(
                        snapshot.shiftId, old.copy(allowanceLines = revised),
                        snapshot.savedAtEpochMillis
                    )
                )
                corrections++
            }
        }
        corrections
    }

    /**
     * Explicit retroactive fix for a paid absence that replaces an entire
     * performance (e.g. Congedo). Does not touch unrelated shift snapshots.
     */
    suspend fun correctHistoricExclusiveAbsence(ruleCode: String, amountCents: Long): Int =
        db.withTransaction {
            require(amountCents >= 0L)
            backfillMissingPaySnapshotsWithinTransaction()
            val ids = ruleDao.getAll().filter { it.code == ruleCode }
                .mapTo(mutableSetOf()) { it.id }
            if (ids.isEmpty()) return@withTransaction 0
            val affected = selectionDao.getAll().asSequence()
                .filter { it.ruleId in ids }.map { it.shiftId }.toSet()
            var corrections = 0
            paySnapshotDao.getAll().filter { it.shiftId in affected }.forEach { snapshot ->
                if (snapshot.basePayCents != amountCents || snapshot.toBreakdown().allowanceLines.isNotEmpty()) {
                    paySnapshotDao.upsert(
                        snapshot.copy(basePayCents = amountCents, allowanceLinesJson = "[]")
                    )
                    corrections++
                }
            }
            corrections
        }

    suspend fun deleteRule(rule: AllowanceRuleEntity) = db.withTransaction {
        backfillMissingPaySnapshotsWithinTransaction()
        ruleDao.delete(rule)
    }

    suspend fun exportSnapshot(): DatabaseSnapshot = db.withTransaction {
        backfillMissingPaySnapshotsWithinTransaction()
        DatabaseSnapshot(
            workers = workerDao.getAll(),
            shifts = shiftDao.getAll(),
            rules = ruleDao.getAll(),
            selections = selectionDao.getAll(),
            paySnapshots = paySnapshotDao.getAll()
        )
    }

    suspend fun restoreSnapshot(snapshot: DatabaseSnapshot) = db.withTransaction {
        selectionDao.deleteAll()
        paySnapshotDao.deleteAll()
        shiftDao.deleteAll()
        ruleDao.deleteAll()
        workerDao.deleteAll()
        pendingUndoSnapshots.clear()

        workerDao.insertAll(snapshot.workers)
        ruleDao.insertAll(snapshot.rules)
        shiftDao.insertAll(snapshot.shifts)
        if (snapshot.selections.isNotEmpty()) selectionDao.insertAll(snapshot.selections)
        if (snapshot.paySnapshots.isNotEmpty()) paySnapshotDao.insertAll(snapshot.paySnapshots)
        // Backups from schema v1 have no frozen pay: reconstruct them once.
        backfillMissingPaySnapshotsWithinTransaction()
    }
}

data class ShiftWithPay(
    val shift: ShiftEntity,
    val worker: WorkerEntity,
    val pay: PayBreakdown,
    val selectedRules: List<AllowanceRuleEntity>
)

fun WorkerEntity.toDomain() = Worker(
    id = id,
    name = name,
    hourlyRateCents = hourlyRateCents,
    basePayMode = basePayMode,
    baseShiftCents = baseShiftCents,
    doubleBaseCents = doubleBaseCents,
    irpefBasisPoints = irpefBasisPoints,
    senioritySteps = senioritySteps
)

fun ShiftEntity.toDomain() = Shift(id, workerId, startEpochMillis, endEpochMillis, zoneId, role, notes, performanceType)

fun AllowanceRuleEntity.toDomain() = AllowanceRule(
    id = id,
    name = name,
    code = code,
    calculationType = calculationType,
    value = value,
    enabled = enabled,
    weekdayMask = weekdayMask,
    windowStartMinute = windowStartMinute,
    windowEndMinute = windowEndMinute,
    minimumShiftMinutes = minimumShiftMinutes,
    roleFilter = roleFilter,
    priority = priority,
    effectiveFromEpochDay = effectiveFromEpochDay,
    effectiveToEpochDay = effectiveToEpochDay,
    category = category,
    applicationMode = applicationMode,
    exclusiveGroup = exclusiveGroup,
    basePayEffect = basePayEffect,
    autoTrigger = autoTrigger,
    turnAllowanceMultiplierBasisPoints = turnAllowanceMultiplierBasisPoints,
    tagsCsv = tagsCsv,
    recommendedWithAnyTagCsv = recommendedWithAnyTagCsv,
    performanceMask = performanceMask
)


class DuplicatePerformanceException(
    val date: LocalDate,
    val performanceType: it.alantamanti.portshifttracker.domain.PerformanceType
) : IllegalStateException(
    "Esiste già una prestazione ${performanceType.name.replace('_', ' ')} il ${date}."
)
