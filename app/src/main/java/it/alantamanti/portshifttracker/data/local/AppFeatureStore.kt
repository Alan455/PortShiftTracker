package it.alantamanti.portshifttracker.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class DayOverrideClass { FERIALE, SABATO, FESTIVO, SEMIFESTIVO }

data class ShiftPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: String,
    val ruleCodes: Set<String>
)

data class SpecialDayOverride(
    val epochDay: Long,
    val label: String,
    val dayClass: DayOverrideClass
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
}

data class PayslipComparison(
    val month: String,
    val totalCents: Long? = null,
    val baseCents: Long? = null,
    val turnoCents: Long? = null,
    val avviamentoCents: Long? = null,
    val disagioCents: Long? = null,
    val areaCents: Long? = null,
    val doppioCents: Long? = null,
    val altreCents: Long? = null
)

/**
 * Piccolo archivio configurazioni utente. I dati restano separati dalle tariffe economiche
 * e sono inclusi nel backup manuale dell'app. Il formato JSON è versionato e facilmente
 * migrabile se in futuro queste preferenze verranno spostate in Room o nel cloud.
 */
class AppFeatureStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun presets(): List<ShiftPreset> = root().optJSONArray("presets").orEmpty().mapObjects { obj ->
        ShiftPreset(
            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = obj.optString("name", "Preset"),
            role = obj.optString("role", ""),
            ruleCodes = obj.optJSONArray("ruleCodes").orEmpty().mapStrings().toSet()
        )
    }

    @Synchronized
    fun upsertPreset(preset: ShiftPreset) {
        val all = presets().filterNot { it.id == preset.id } + preset
        mutate { root -> root.put("presets", JSONArray().apply { all.forEach { put(it.toJson()) } }) }
    }

    @Synchronized
    fun deletePreset(id: String) {
        val all = presets().filterNot { it.id == id }
        mutate { root -> root.put("presets", JSONArray().apply { all.forEach { put(it.toJson()) } }) }
    }

    @Synchronized
    fun specialDays(): List<SpecialDayOverride> = root().optJSONArray("specialDays").orEmpty().mapObjects { obj ->
        SpecialDayOverride(
            epochDay = obj.optLong("epochDay"),
            label = obj.optString("label", "Giorno speciale"),
            dayClass = runCatching { DayOverrideClass.valueOf(obj.optString("dayClass")) }.getOrDefault(DayOverrideClass.FESTIVO)
        )
    }.sortedBy { it.epochDay }

    fun specialDay(date: LocalDate): SpecialDayOverride? = specialDays().firstOrNull { it.epochDay == date.toEpochDay() }

    @Synchronized
    fun upsertSpecialDay(value: SpecialDayOverride) {
        val all = specialDays().filterNot { it.epochDay == value.epochDay } + value
        mutate { root -> root.put("specialDays", JSONArray().apply { all.sortedBy { it.epochDay }.forEach { put(it.toJson()) } }) }
    }

    @Synchronized
    fun deleteSpecialDay(epochDay: Long) {
        val all = specialDays().filterNot { it.epochDay == epochDay }
        mutate { root -> root.put("specialDays", JSONArray().apply { all.forEach { put(it.toJson()) } }) }
    }

    @Synchronized
    fun payslip(month: YearMonth): PayslipComparison? = payslips().firstOrNull { it.month == month.toString() }

    @Synchronized
    fun savePayslip(value: PayslipComparison) {
        val all = payslips().filterNot { it.month == value.month } + value
        mutate { root -> root.put("payslips", JSONArray().apply { all.sortedBy { it.month }.forEach { put(it.toJson()) } }) }
    }

    @Synchronized
    fun exportJson(): String = root().toString(2)

    @Synchronized
    fun importJson(json: String) {
        val parsed = JSONObject(json)
        if (!parsed.has("schemaVersion")) parsed.put("schemaVersion", SCHEMA_VERSION)
        prefs.edit().putString(ROOT_KEY, parsed.toString()).apply()
    }

    private fun payslips(): List<PayslipComparison> = root().optJSONArray("payslips").orEmpty().mapObjects { obj ->
        PayslipComparison(
            month = obj.optString("month"),
            totalCents = obj.optNullableLong("totalCents"),
            baseCents = obj.optNullableLong("baseCents"),
            turnoCents = obj.optNullableLong("turnoCents"),
            avviamentoCents = obj.optNullableLong("avviamentoCents"),
            disagioCents = obj.optNullableLong("disagioCents"),
            areaCents = obj.optNullableLong("areaCents"),
            doppioCents = obj.optNullableLong("doppioCents"),
            altreCents = obj.optNullableLong("altreCents")
        )
    }

    private fun root(): JSONObject = runCatching {
        JSONObject(prefs.getString(ROOT_KEY, null) ?: "{}")
    }.getOrElse { JSONObject() }.also {
        if (!it.has("schemaVersion")) it.put("schemaVersion", SCHEMA_VERSION)
    }

    private fun mutate(block: (JSONObject) -> Unit) {
        val value = root()
        block(value)
        prefs.edit().putString(ROOT_KEY, value.toString()).apply()
    }

    private fun ShiftPreset.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("role", role)
        .put("ruleCodes", JSONArray().apply { ruleCodes.sorted().forEach { put(it) } })

    private fun SpecialDayOverride.toJson() = JSONObject()
        .put("epochDay", epochDay)
        .put("label", label)
        .put("dayClass", dayClass.name)

    private fun PayslipComparison.toJson() = JSONObject()
        .put("month", month)
        .putNullable("totalCents", totalCents)
        .putNullable("baseCents", baseCents)
        .putNullable("turnoCents", turnoCents)
        .putNullable("avviamentoCents", avviamentoCents)
        .putNullable("disagioCents", disagioCents)
        .putNullable("areaCents", areaCents)
        .putNullable("doppioCents", doppioCents)
        .putNullable("altreCents", altreCents)

    private fun JSONObject.putNullable(key: String, value: Long?): JSONObject = apply {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.optNullableLong(key: String): Long? = if (!has(key) || isNull(key)) null else optLong(key)

    private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

    private fun JSONArray.mapStrings(): List<String> = buildList {
        for (i in 0 until length()) add(optString(i))
    }

    private inline fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> = buildList {
        for (i in 0 until length()) optJSONObject(i)?.let { add(block(it)) }
    }

    companion object {
        private const val PREFS_NAME = "portshift_features"
        private const val ROOT_KEY = "feature_store_json"
        private const val SCHEMA_VERSION = 1
    }
}
