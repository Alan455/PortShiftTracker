package it.alantamanti.portshifttracker.data.repository

import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.data.local.ShiftAllowanceSelectionEntity
import it.alantamanti.portshifttracker.data.local.ShiftEntity
import it.alantamanti.portshifttracker.data.local.WorkerEntity
import it.alantamanti.portshifttracker.domain.AllowanceApplicationMode
import it.alantamanti.portshifttracker.domain.AllowanceAutoTrigger
import it.alantamanti.portshifttracker.domain.AllowanceCalculationType
import it.alantamanti.portshifttracker.domain.AllowanceCategory
import it.alantamanti.portshifttracker.domain.BasePayEffect
import it.alantamanti.portshifttracker.domain.BasePayMode
import it.alantamanti.portshifttracker.domain.PerformanceType
import org.json.JSONArray
import org.json.JSONObject

data class DatabaseSnapshot(
    val workers: List<WorkerEntity>,
    val shifts: List<ShiftEntity>,
    val rules: List<AllowanceRuleEntity>,
    val selections: List<ShiftAllowanceSelectionEntity>
)

data class DecodedAppBackup(
    val database: DatabaseSnapshot,
    val featureStoreJson: String
)

object BackupCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(snapshot: DatabaseSnapshot, featureStoreJson: String): String {
        val root = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("workers", JSONArray().apply { snapshot.workers.forEach { put(workerToJson(it)) } })
            .put("shifts", JSONArray().apply { snapshot.shifts.forEach { put(shiftToJson(it)) } })
            .put("rules", JSONArray().apply { snapshot.rules.forEach { put(ruleToJson(it)) } })
            .put("selections", JSONArray().apply { snapshot.selections.forEach { put(selectionToJson(it)) } })
            .put("features", JSONObject(featureStoreJson))
        return root.toString(2)
    }

    fun decode(json: String): DecodedAppBackup {
        val root = JSONObject(json)
        require(root.optInt("schemaVersion", 0) in 1..SCHEMA_VERSION) { "Versione backup non supportata" }
        val workers = root.getJSONArray("workers").objects(::workerFromJson)
        val shifts = root.getJSONArray("shifts").objects(::shiftFromJson)
        val rules = root.getJSONArray("rules").objects(::ruleFromJson)
        val selections = root.getJSONArray("selections").objects(::selectionFromJson)
        val features = root.optJSONObject("features")?.toString() ?: "{}"
        return DecodedAppBackup(DatabaseSnapshot(workers, shifts, rules, selections), features)
    }

    private fun workerToJson(v: WorkerEntity) = JSONObject()
        .put("id", v.id)
        .put("name", v.name)
        .put("hourlyRateCents", v.hourlyRateCents)
        .put("basePayMode", v.basePayMode.name)
        .put("baseShiftCents", v.baseShiftCents)
        .put("doubleBaseCents", v.doubleBaseCents)
        .put("irpefBasisPoints", v.irpefBasisPoints)
        .put("senioritySteps", v.senioritySteps)

    private fun workerFromJson(o: JSONObject) = WorkerEntity(
        id = o.getLong("id"),
        name = o.getString("name"),
        hourlyRateCents = o.getLong("hourlyRateCents"),
        basePayMode = BasePayMode.valueOf(o.getString("basePayMode")),
        baseShiftCents = o.getLong("baseShiftCents"),
        doubleBaseCents = o.optLong("doubleBaseCents", 8840),
        irpefBasisPoints = o.getLong("irpefBasisPoints"),
        senioritySteps = o.getInt("senioritySteps")
    )

    private fun shiftToJson(v: ShiftEntity) = JSONObject()
        .put("id", v.id)
        .put("workerId", v.workerId)
        .put("startEpochMillis", v.startEpochMillis)
        .put("endEpochMillis", v.endEpochMillis)
        .put("zoneId", v.zoneId)
        .put("role", v.role)
        .put("notes", v.notes)
        .put("performanceType", v.performanceType.name)

    private fun shiftFromJson(o: JSONObject) = ShiftEntity(
        id = o.getLong("id"),
        workerId = o.getLong("workerId"),
        startEpochMillis = o.getLong("startEpochMillis"),
        endEpochMillis = o.getLong("endEpochMillis"),
        zoneId = o.optString("zoneId", "Europe/Rome"),
        role = o.optString("role", ""),
        notes = o.optString("notes", ""),
        performanceType = PerformanceType.valueOf(o.optString("performanceType", PerformanceType.TURNO.name))
    )

    private fun ruleToJson(v: AllowanceRuleEntity) = JSONObject()
        .put("id", v.id)
        .put("name", v.name)
        .put("code", v.code)
        .put("calculationType", v.calculationType.name)
        .put("value", v.value)
        .put("enabled", v.enabled)
        .put("weekdayMask", v.weekdayMask)
        .putNullable("windowStartMinute", v.windowStartMinute)
        .putNullable("windowEndMinute", v.windowEndMinute)
        .put("minimumShiftMinutes", v.minimumShiftMinutes)
        .putNullable("roleFilter", v.roleFilter)
        .put("priority", v.priority)
        .putNullable("effectiveFromEpochDay", v.effectiveFromEpochDay)
        .putNullable("effectiveToEpochDay", v.effectiveToEpochDay)
        .put("category", v.category.name)
        .put("applicationMode", v.applicationMode.name)
        .putNullable("exclusiveGroup", v.exclusiveGroup)
        .put("basePayEffect", v.basePayEffect.name)
        .put("autoTrigger", v.autoTrigger.name)
        .put("turnAllowanceMultiplierBasisPoints", v.turnAllowanceMultiplierBasisPoints)
        .putNullable("tagsCsv", v.tagsCsv)
        .putNullable("recommendedWithAnyTagCsv", v.recommendedWithAnyTagCsv)
        .put("performanceMask", v.performanceMask)

    private fun ruleFromJson(o: JSONObject) = AllowanceRuleEntity(
        id = o.getLong("id"),
        name = o.getString("name"),
        code = o.getString("code"),
        calculationType = AllowanceCalculationType.valueOf(o.getString("calculationType")),
        value = o.getLong("value"),
        enabled = o.optBoolean("enabled", true),
        weekdayMask = o.optInt("weekdayMask", 127),
        windowStartMinute = o.optNullableInt("windowStartMinute"),
        windowEndMinute = o.optNullableInt("windowEndMinute"),
        minimumShiftMinutes = o.optInt("minimumShiftMinutes", 0),
        roleFilter = o.optNullableString("roleFilter"),
        priority = o.optInt("priority", 100),
        effectiveFromEpochDay = o.optNullableLong("effectiveFromEpochDay"),
        effectiveToEpochDay = o.optNullableLong("effectiveToEpochDay"),
        category = AllowanceCategory.valueOf(o.optString("category", AllowanceCategory.ALTRO.name)),
        applicationMode = AllowanceApplicationMode.valueOf(o.optString("applicationMode", AllowanceApplicationMode.AUTO.name)),
        exclusiveGroup = o.optNullableString("exclusiveGroup"),
        basePayEffect = BasePayEffect.valueOf(o.optString("basePayEffect", BasePayEffect.ADDITIVE.name)),
        autoTrigger = AllowanceAutoTrigger.valueOf(o.optString("autoTrigger", AllowanceAutoTrigger.NONE.name)),
        turnAllowanceMultiplierBasisPoints = o.optInt("turnAllowanceMultiplierBasisPoints", 10000),
        tagsCsv = o.optNullableString("tagsCsv"),
        recommendedWithAnyTagCsv = o.optNullableString("recommendedWithAnyTagCsv"),
        performanceMask = o.optInt("performanceMask", PerformanceType.entries.fold(0) { acc, type -> acc or type.maskBit })
    )

    private fun selectionToJson(v: ShiftAllowanceSelectionEntity) = JSONObject()
        .put("shiftId", v.shiftId)
        .put("ruleId", v.ruleId)

    private fun selectionFromJson(o: JSONObject) = ShiftAllowanceSelectionEntity(
        shiftId = o.getLong("shiftId"),
        ruleId = o.getLong("ruleId")
    )

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = apply {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optNullableString(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)
    private fun JSONObject.optNullableLong(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
    private fun JSONObject.optNullableInt(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)

    private inline fun <T> JSONArray.objects(block: (JSONObject) -> T): List<T> = buildList {
        for (i in 0 until length()) add(block(getJSONObject(i)))
    }
}
