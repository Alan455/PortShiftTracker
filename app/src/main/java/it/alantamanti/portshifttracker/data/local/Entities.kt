package it.alantamanti.portshifttracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import it.alantamanti.portshifttracker.domain.AllowanceApplicationMode
import it.alantamanti.portshifttracker.domain.AllowanceCalculationType
import it.alantamanti.portshifttracker.domain.AllowanceCategory
import it.alantamanti.portshifttracker.domain.AllowanceAutoTrigger
import it.alantamanti.portshifttracker.domain.BasePayEffect
import it.alantamanti.portshifttracker.domain.BasePayMode
import it.alantamanti.portshifttracker.domain.PerformanceType

@Entity(tableName = "workers")
data class WorkerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val hourlyRateCents: Long,
    val basePayMode: BasePayMode = BasePayMode.FIXED_PER_SHIFT,
    val baseShiftCents: Long = 6780,
    /** Base autonoma del doppio; modificabile dal profilo. */
    val doubleBaseCents: Long = 8840,
    val irpefBasisPoints: Long = 3000,
    val senioritySteps: Int = 3
)

@Entity(
    tableName = "shifts",
    foreignKeys = [ForeignKey(
        entity = WorkerEntity::class,
        parentColumns = ["id"],
        childColumns = ["workerId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("workerId"),
        Index("startEpochMillis"),
        Index(value = ["workerId", "serviceEpochDay", "performanceType"], unique = true)
    ]
)
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workerId: Long,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val zoneId: String = "Europe/Rome",
    val role: String = "",
    val notes: String = "",
    /** Giorno di servizio locale (epoch day). Null solo per eventuali record legacy non ancora normalizzati. */
    val serviceEpochDay: Long? = null,
    /** Consente più prestazioni nello stesso giorno, es. TURNO + DOPPIO. */
    val performanceType: PerformanceType = PerformanceType.TURNO
)

@Entity(tableName = "allowance_rules", indices = [Index(value = ["code"], unique = true)])
data class AllowanceRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val code: String,
    val calculationType: AllowanceCalculationType,
    val value: Long,
    val enabled: Boolean = true,
    val weekdayMask: Int = 127,
    val windowStartMinute: Int? = null,
    val windowEndMinute: Int? = null,
    val minimumShiftMinutes: Int = 0,
    val roleFilter: String? = null,
    val priority: Int = 100,
    val effectiveFromEpochDay: Long? = null,
    val effectiveToEpochDay: Long? = null,
    val category: AllowanceCategory = AllowanceCategory.ALTRO,
    val applicationMode: AllowanceApplicationMode = AllowanceApplicationMode.AUTO,
    val exclusiveGroup: String? = null,
    val basePayEffect: BasePayEffect = BasePayEffect.ADDITIVE,
    val autoTrigger: AllowanceAutoTrigger = AllowanceAutoTrigger.NONE,
    val turnAllowanceMultiplierBasisPoints: Int = 10000,
    val tagsCsv: String? = null,
    val recommendedWithAnyTagCsv: String? = null,
    /** bit 0 TURNO, bit 1 DOPPIO, bit 2 MEZZO_DOPPIO. */
    val performanceMask: Int = PerformanceType.entries.fold(0) { acc, type -> acc or type.maskBit }
)

@Entity(
    tableName = "shift_allowance_selections",
    primaryKeys = ["shiftId", "ruleId"],
    foreignKeys = [
        ForeignKey(
            entity = ShiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["shiftId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AllowanceRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("shiftId"), Index("ruleId")]
)
data class ShiftAllowanceSelectionEntity(
    val shiftId: Long,
    val ruleId: Long
)
