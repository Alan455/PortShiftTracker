package it.alantamanti.portshifttracker.data.repository

import androidx.room.withTransaction
import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.data.local.AppDatabase
import it.alantamanti.portshifttracker.data.local.ShiftAllowanceSelectionEntity
import it.alantamanti.portshifttracker.data.local.ShiftEntity
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

class PortRepository(
    private val db: AppDatabase,
    private val calculator: AllowanceCalculator = AllowanceCalculator()
) {
    private val workerDao = db.workerDao()
    private val shiftDao = db.shiftDao()
    private val ruleDao = db.allowanceRuleDao()
    private val selectionDao = db.shiftAllowanceSelectionDao()

    val workers: Flow<List<WorkerEntity>> = workerDao.observeAll()
    val shifts: Flow<List<ShiftEntity>> = shiftDao.observeAll()
    val rules: Flow<List<AllowanceRuleEntity>> = ruleDao.observeAll()
    val selections: Flow<List<ShiftAllowanceSelectionEntity>> = selectionDao.observeAll()

    val shiftRows: Flow<List<ShiftWithPay>> = combine(workers, shifts, rules, selections) { ws, ss, rs, sels ->
        buildRows(ws, ss, rs, sels)
    }

    fun shiftRowsBetween(startInclusive: Long, endExclusive: Long): Flow<List<ShiftWithPay>> =
        combine(
            workers,
            shiftDao.observeBetween(startInclusive, endExclusive),
            rules,
            selectionDao.observeForShiftRange(startInclusive, endExclusive)
        ) { ws, ss, rs, sels ->
            buildRows(ws, ss, rs, sels)
        }

    private fun buildRows(
        ws: List<WorkerEntity>,
        ss: List<ShiftEntity>,
        rs: List<AllowanceRuleEntity>,
        sels: List<ShiftAllowanceSelectionEntity>
    ): List<ShiftWithPay> {
        val workerMap = ws.associateBy { it.id }
        val rulesById = rs.associateBy { it.id }
        val selectedByShift = sels.groupBy { it.shiftId }.mapValues { (_, rows) -> rows.map { it.ruleId }.toSet() }

        return ss.mapNotNull { shift ->
            val worker = workerMap[shift.workerId] ?: return@mapNotNull null
            val selectedIds = normalizeSelectedRuleIds(
                shift.performanceType,
                selectedByShift[shift.id].orEmpty(),
                rs
            )
            val breakdown = calculator.calculate(
                worker.toDomain(),
                shift.toDomain(),
                rs.map { it.toDomain() },
                selectedIds
            )
            val selectedRules = selectedIds.mapNotNull { rulesById[it] }
                .sortedWith(compareBy<AllowanceRuleEntity> { it.category.ordinal }.thenBy { it.priority }.thenBy { it.name })
            ShiftWithPay(shift, worker, breakdown, selectedRules)
        }
    }

    suspend fun saveWorker(worker: WorkerEntity) = workerDao.upsert(worker)

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
        val seen = mutableSetOf<Triple<Long, LocalDate, it.alantamanti.portshifttracker.domain.PerformanceType>>()
        val result = mutableListOf<Long>()

        shifts.forEach { rawShift ->
            val shift = canonicalShift(rawShift)
            val date = localDateOf(shift)
            val key = Triple(shift.workerId, date, shift.performanceType)
            if (!seen.add(key)) throw DuplicatePerformanceException(date, shift.performanceType)
            ensureNoDuplicate(shift)

            val normalized = normalizeSelectedRuleIds(shift.performanceType, selectedRuleIds, allRules)
            selectionValidationMessage(shift.performanceType, normalized, allRules)?.let {
                throw IllegalArgumentException(it)
            }

            val shiftId = shiftDao.insert(shift)
            if (normalized.isNotEmpty()) {
                selectionDao.insertAll(normalized.map { ShiftAllowanceSelectionEntity(shiftId, it) })
            }
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
        val normalized = normalizeSelectedRuleIds(canonical.performanceType, selectedRuleIds, allRules)
        selectionValidationMessage(shift.performanceType, normalized, allRules)?.let {
            throw IllegalArgumentException(it)
        }

        shiftDao.update(canonical)
        selectionDao.deleteForShift(canonical.id)
        if (normalized.isNotEmpty()) {
            selectionDao.insertAll(normalized.map { ShiftAllowanceSelectionEntity(canonical.id, it) })
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

    suspend fun deleteShift(shift: ShiftEntity) = shiftDao.delete(shift)

    suspend fun restoreDeletedShift(shift: ShiftEntity, selectedRuleIds: Set<Long>) = db.withTransaction {
        val canonical = canonicalShift(shift)
        shiftDao.insert(canonical)
        if (selectedRuleIds.isNotEmpty()) {
            selectionDao.insertAll(selectedRuleIds.map { ShiftAllowanceSelectionEntity(canonical.id, it) })
        }
    }

    suspend fun saveRule(rule: AllowanceRuleEntity) = ruleDao.upsert(rule)
    suspend fun deleteRule(rule: AllowanceRuleEntity) = ruleDao.delete(rule)

    suspend fun exportSnapshot(): DatabaseSnapshot = DatabaseSnapshot(
        workers = workerDao.getAll(),
        shifts = shiftDao.getAll(),
        rules = ruleDao.getAll(),
        selections = selectionDao.getAll()
    )

    suspend fun restoreSnapshot(snapshot: DatabaseSnapshot) = db.withTransaction {
        selectionDao.deleteAll()
        shiftDao.deleteAll()
        ruleDao.deleteAll()
        workerDao.deleteAll()

        workerDao.insertAll(snapshot.workers)
        ruleDao.insertAll(snapshot.rules)
        shiftDao.insertAll(snapshot.shifts)
        if (snapshot.selections.isNotEmpty()) selectionDao.insertAll(snapshot.selections)
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
