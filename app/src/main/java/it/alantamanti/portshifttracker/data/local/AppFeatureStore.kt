package it.alantamanti.portshifttracker.data.local

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
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

private val Context.portShiftFeatureDataStore by preferencesDataStore(
    name = "portshift_features_v2",
    produceMigrations = { context ->
        // Migra automaticamente il vecchio JSON da SharedPreferences al primo avvio.
        listOf(SharedPreferencesMigration(context, "portshift_features"))
    }
)

/**
 * Archivio configurazioni utente basato su Jetpack DataStore.
 *
 * Il formato JSON resta versionato per mantenere backup e migrazione semplici,
 * ma letture/scritture sono ora asincrone, atomiche e osservabili con Flow.
 */
class AppFeatureStore(context: Context) {
    private val dataStore = context.applicationContext.portShiftFeatureDataStore
    private val rootKey = stringPreferencesKey(ROOT_KEY)

    private val rootFlow: Flow<JSONObject> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs -> parseRoot(prefs[rootKey]) }

    val presetsFlow: Flow<List<ShiftPreset>> = rootFlow
        .map(::readPresets)
        .distinctUntilChanged()

    val specialDaysFlow: Flow<List<SpecialDayOverride>> = rootFlow
        .map(::readSpecialDays)
        .distinctUntilChanged()

    val payslipsFlow: Flow<List<PayslipComparison>> = rootFlow
        .map(::readPayslips)
        .distinctUntilChanged()

    suspend fun presets(): List<ShiftPreset> = presetsFlow.first()

    suspend fun upsertPreset(preset: ShiftPreset) {
        mutate { root ->
            val all = readPresets(root).filterNot { it.id == preset.id } + preset
            root.put("presets", JSONArray().apply { all.forEach { put(it.toJson()) } })
        }
    }

    suspend fun deletePreset(id: String) {
        mutate { root ->
            val all = readPresets(root).filterNot { it.id == id }
            root.put("presets", JSONArray().apply { all.forEach { put(it.toJson()) } })
        }
    }

    suspend fun specialDays(): List<SpecialDayOverride> = specialDaysFlow.first()

    suspend fun specialDay(date: LocalDate): SpecialDayOverride? =
        specialDays().firstOrNull { it.epochDay == date.toEpochDay() }

    suspend fun upsertSpecialDay(value: SpecialDayOverride) {
        mutate { root ->
            val all = readSpecialDays(root).filterNot { it.epochDay == value.epochDay } + value
            root.put(
                "specialDays",
                JSONArray().apply { all.sortedBy { it.epochDay }.forEach { put(it.toJson()) } }
            )
        }
    }

    suspend fun deleteSpecialDay(epochDay: Long) {
        mutate { root ->
            val all = readSpecialDays(root).filterNot { it.epochDay == epochDay }
            root.put("specialDays", JSONArray().apply { all.forEach { put(it.toJson()) } })
        }
    }

    suspend fun payslip(month: YearMonth): PayslipComparison? =
        payslipsFlow.first().firstOrNull { it.month == month.toString() }

    suspend fun savePayslip(value: PayslipComparison) {
        mutate { root ->
            val all = readPayslips(root).filterNot { it.month == value.month } + value
            root.put(
                "payslips",
                JSONArray().apply { all.sortedBy { it.month }.forEach { put(it.toJson()) } }
            )
        }
    }

    suspend fun exportJson(): String = rootFlow.first().toString(2)

    suspend fun importJson(json: String) {
        val parsed = JSONObject(json)
        if (!parsed.has("schemaVersion")) parsed.put("schemaVersion", SCHEMA_VERSION)
        require(parsed.optInt("schemaVersion", 0) in 1..SCHEMA_VERSION) {
            "Versione impostazioni non supportata"
        }
        dataStore.edit { prefs -> prefs[rootKey] = parsed.toString() }
    }

    private suspend fun mutate(block: (JSONObject) -> Unit) {
        dataStore.edit { prefs ->
            val root = parseRoot(prefs[rootKey])
            block(root)
            prefs[rootKey] = root.toString()
        }
    }

    private fun parseRoot(json: String?): JSONObject = runCatching {
        JSONObject(json ?: "{}")
    }.getOrElse { JSONObject() }.also {
        if (!it.has("schemaVersion")) it.put("schemaVersion", SCHEMA_VERSION)
    }

    private fun readPresets(root: JSONObject): List<ShiftPreset> =
        root.optJSONArray("presets").orEmpty().mapObjects { obj ->
            ShiftPreset(
                id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = obj.optString("name", "Preset"),
                role = obj.optString("role", ""),
                ruleCodes = obj.optJSONArray("ruleCodes").orEmpty().mapStrings().toSet()
            )
        }

    private fun readSpecialDays(root: JSONObject): List<SpecialDayOverride> =
        root.optJSONArray("specialDays").orEmpty().mapObjects { obj ->
            SpecialDayOverride(
                epochDay = obj.optLong("epochDay"),
                label = obj.optString("label", "Giorno speciale"),
                dayClass = runCatching {
                    DayOverrideClass.valueOf(obj.optString("dayClass"))
                }.getOrDefault(DayOverrideClass.FESTIVO)
            )
        }.sortedBy { it.epochDay }

    private fun readPayslips(root: JSONObject): List<PayslipComparison> =
        root.optJSONArray("payslips").orEmpty().mapObjects { obj ->
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

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

    private fun JSONArray.mapStrings(): List<String> = buildList {
        for (i in 0 until length()) add(optString(i))
    }

    private inline fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> = buildList {
        for (i in 0 until length()) optJSONObject(i)?.let { add(block(it)) }
    }

    companion object {
        private const val ROOT_KEY = "feature_store_json"
        private const val SCHEMA_VERSION = 1
    }
}
