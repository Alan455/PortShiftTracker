package it.alantamanti.portshifttracker.data.local

import androidx.room.TypeConverter
import it.alantamanti.portshifttracker.domain.AllowanceApplicationMode
import it.alantamanti.portshifttracker.domain.AllowanceCalculationType
import it.alantamanti.portshifttracker.domain.AllowanceCategory
import it.alantamanti.portshifttracker.domain.AllowanceAutoTrigger
import it.alantamanti.portshifttracker.domain.BasePayEffect
import it.alantamanti.portshifttracker.domain.BasePayMode
import it.alantamanti.portshifttracker.domain.PerformanceType

class Converters {
    @TypeConverter
    fun allowanceTypeToString(value: AllowanceCalculationType): String = value.name

    @TypeConverter
    fun stringToAllowanceType(value: String): AllowanceCalculationType =
        AllowanceCalculationType.valueOf(value)

    @TypeConverter
    fun categoryToString(value: AllowanceCategory): String = value.name

    @TypeConverter
    fun stringToCategory(value: String): AllowanceCategory = AllowanceCategory.valueOf(value)

    @TypeConverter
    fun applicationModeToString(value: AllowanceApplicationMode): String = value.name

    @TypeConverter
    fun stringToApplicationMode(value: String): AllowanceApplicationMode =
        AllowanceApplicationMode.valueOf(value)

    @TypeConverter
    fun basePayEffectToString(value: BasePayEffect): String = value.name

    @TypeConverter
    fun stringToBasePayEffect(value: String): BasePayEffect = BasePayEffect.valueOf(value)

    @TypeConverter
    fun autoTriggerToString(value: AllowanceAutoTrigger): String = value.name

    @TypeConverter
    fun stringToAutoTrigger(value: String): AllowanceAutoTrigger = AllowanceAutoTrigger.valueOf(value)

    @TypeConverter
    fun basePayModeToString(value: BasePayMode): String = value.name

    @TypeConverter
    fun stringToBasePayMode(value: String): BasePayMode = BasePayMode.valueOf(value)

    @TypeConverter
    fun performanceTypeToString(value: PerformanceType): String = value.name

    @TypeConverter
    fun stringToPerformanceType(value: String): PerformanceType = PerformanceType.valueOf(value)
}
