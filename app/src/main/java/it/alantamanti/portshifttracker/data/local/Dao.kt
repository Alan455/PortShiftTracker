package it.alantamanti.portshifttracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkerDao {
    @Query("SELECT * FROM workers ORDER BY name")
    fun observeAll(): Flow<List<WorkerEntity>>

    @Upsert
    suspend fun upsert(worker: WorkerEntity)
}

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts ORDER BY startEpochMillis DESC")
    fun observeAll(): Flow<List<ShiftEntity>>

    @Insert
    suspend fun insert(shift: ShiftEntity): Long

    @Update
    suspend fun update(shift: ShiftEntity)

    @Delete
    suspend fun delete(shift: ShiftEntity)
}

@Dao
interface AllowanceRuleDao {
    @Query("SELECT * FROM allowance_rules ORDER BY category, priority, name")
    fun observeAll(): Flow<List<AllowanceRuleEntity>>

    @Query("SELECT * FROM allowance_rules WHERE enabled = 1 ORDER BY category, priority, name")
    suspend fun getEnabled(): List<AllowanceRuleEntity>

    @Upsert
    suspend fun upsert(rule: AllowanceRuleEntity)

    /** Non sovrascrive mai una voce già modificata dall'utente. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(rule: AllowanceRuleEntity): Long

    @Delete
    suspend fun delete(rule: AllowanceRuleEntity)
}

@Dao
interface ShiftAllowanceSelectionDao {
    @Query("SELECT * FROM shift_allowance_selections")
    fun observeAll(): Flow<List<ShiftAllowanceSelectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ShiftAllowanceSelectionEntity>)

    @Query("DELETE FROM shift_allowance_selections WHERE shiftId = :shiftId")
    suspend fun deleteForShift(shiftId: Long)
}
