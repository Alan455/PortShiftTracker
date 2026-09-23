package it.alantamanti.portshifttracker.domain

enum class BasePayMode {
    FIXED_PER_SHIFT,
    HOURLY
}

enum class PerformanceType(val maskBit: Int) {
    TURNO(1),
    DOPPIO(2),
    /** Mezzo Doppio: 50% della base Doppio; solo Pom/Sera/Sera2/Notte e relative varianti al 50%. */
    MEZZO_DOPPIO(4)
}

data class Worker(
    val id: Long,
    val name: String,
    val hourlyRateCents: Long,
    val basePayMode: BasePayMode = BasePayMode.FIXED_PER_SHIFT,
    val baseShiftCents: Long = 6780,
    val doubleBaseCents: Long = 8840,
    val irpefBasisPoints: Long = 3000,
    val senioritySteps: Int = 3
)

data class Shift(
    val id: Long,
    val workerId: Long,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val zoneId: String = "Europe/Rome",
    val role: String = "",
    val notes: String = "",
    /** Ogni record rappresenta una singola prestazione: turno ordinario oppure doppio. */
    val performanceType: PerformanceType = PerformanceType.TURNO
)

enum class AllowanceCalculationType {
    FIXED_PER_SHIFT,
    PER_HOUR,
    PERCENT_BASE
}

enum class AllowanceCategory {
    TURNO,
    MEZZO_TURNO,
    AVVIAMENTO,
    DISAGIO,
    AREA,
    DOPPIO,
    ALTRE_VOCI,
    ALTRO
}

enum class BasePayEffect {
    ADDITIVE,
    REPLACE_BASE
}

enum class AllowanceAutoTrigger {
    NONE,
    WHEN_TURNO_SELECTED
}

enum class AllowanceApplicationMode {
    /** La voce viene scelta esplicitamente quando si registra la prestazione. */
    MANUAL,
    /** La voce viene applicata automaticamente se i filtri della regola coincidono. */
    AUTO
}

data class AllowanceRule(
    val id: Long,
    val name: String,
    val code: String,
    val calculationType: AllowanceCalculationType,
    /**
     * FIXED_PER_SHIFT/PER_HOUR: centesimi.
     * PERCENT_BASE: basis points (2500 = 25%).
     */
    val value: Long,
    val enabled: Boolean = true,
    /** bit 0 lunedì ... bit 6 domenica. 127 = tutti i giorni. */
    val weekdayMask: Int = 127,
    /** Finestra locale opzionale, in minuti da mezzanotte. */
    val windowStartMinute: Int? = null,
    val windowEndMinute: Int? = null,
    val minimumShiftMinutes: Int = 0,
    /** Filtro case-insensitive; null/vuoto = ogni mansione. */
    val roleFilter: String? = null,
    val priority: Int = 100,
    val effectiveFromEpochDay: Long? = null,
    val effectiveToEpochDay: Long? = null,
    val category: AllowanceCategory = AllowanceCategory.ALTRO,
    val applicationMode: AllowanceApplicationMode = AllowanceApplicationMode.AUTO,
    /** Regole con lo stesso gruppo sono mutuamente esclusive nella UI della prestazione. */
    val exclusiveGroup: String? = null,
    val basePayEffect: BasePayEffect = BasePayEffect.ADDITIVE,
    val autoTrigger: AllowanceAutoTrigger = AllowanceAutoTrigger.NONE,
    /** 10000 = 100%; 5000 = dimezza le sole indennità di categoria TURNO. */
    val turnAllowanceMultiplierBasisPoints: Int = 10000,
    /** Tag semantici CSV usati per relazioni tra voci (es. MEZZO_TURNO). */
    val tagsCsv: String? = null,
    /** Tag di cui è consigliata la presenza insieme a questa voce. Non è un vincolo rigido. */
    val recommendedWithAnyTagCsv: String? = null,
    /**
     * Maschera delle prestazioni su cui la voce è applicabile.
     * bit 0 = Turno, bit 1 = Doppio, bit 2 = Mezzo Doppio. Le combinazioni sono additive.
     */
    val performanceMask: Int = PerformanceType.entries.fold(0) { acc, type -> acc or type.maskBit }
)

data class AllowanceLine(
    val ruleId: Long,
    val name: String,
    val eligibleMinutes: Long,
    val amountCents: Long
)

data class PayBreakdown(
    val totalMinutes: Long,
    val basePayCents: Long,
    val allowanceLines: List<AllowanceLine>
) {
    val allowancesCents: Long get() = allowanceLines.sumOf { it.amountCents }
    val totalPayCents: Long get() = basePayCents + allowancesCents
}
