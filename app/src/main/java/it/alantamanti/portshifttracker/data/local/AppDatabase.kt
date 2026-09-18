package it.alantamanti.portshifttracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        WorkerEntity::class,
        ShiftEntity::class,
        AllowanceRuleEntity::class,
        ShiftAllowanceSelectionEntity::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workerDao(): WorkerDao
    abstract fun shiftDao(): ShiftDao
    abstract fun allowanceRuleDao(): AllowanceRuleDao
    abstract fun shiftAllowanceSelectionDao(): ShiftAllowanceSelectionDao
}
