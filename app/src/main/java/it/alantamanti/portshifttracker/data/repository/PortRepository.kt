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
        val workerMap = ws.associateBy { it.id }
        val rulesById = rs.associateBy { it.id }
        val selectedByShift = sels.groupBy { it.shiftId }.mapValues { (_, rows) -> rows.map { it.ruleId }.toSet() }

        ss.mapNotNull { shift ->
            val worker = workerMap[shift.workerId] ?: return@mapNotNull null
            val selectedIds = selectedByShift[shift.id].orEmpty()
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

    suspend fun addShiftWithSelections(shift: ShiftEntity, selectedRuleIds: Set<Long>): Long = db.withTransaction {
        val shiftId = shiftDao.insert(shift)
        if (selectedRuleIds.isNotEmpty()) {
            selectionDao.insertAll(selectedRuleIds.map { ShiftAllowanceSelectionEntity(shiftId, it) })
        }
        shiftId
    }

    suspend fun updateShiftWithSelections(shift: ShiftEntity, selectedRuleIds: Set<Long>) = db.withTransaction {
        shiftDao.update(shift)
        selectionDao.deleteForShift(shift.id)
        if (selectedRuleIds.isNotEmpty()) {
            selectionDao.insertAll(selectedRuleIds.map { ShiftAllowanceSelectionEntity(shift.id, it) })
        }
    }

    suspend fun deleteShift(shift: ShiftEntity) = shiftDao.delete(shift)
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
