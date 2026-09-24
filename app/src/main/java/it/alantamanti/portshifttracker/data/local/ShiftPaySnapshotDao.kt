package it.alantamanti.portshifttracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftPaySnapshotDao {
    @Query("SELECT * FROM shift_pay_snapshots")
    fun observeAll(): Flow<List<ShiftPaySnapshotEntity>>

    @Query(
        "SELECT snap.* FROM shift_pay_snapshots snap INNER JOIN shifts s ON s.id = snap.shiftId " +
            "WHERE s.startEpochMillis >= :startInclusive AND s.startEpochMillis < :endExclusive"
    )
    fun observeForShiftRange(startInclusive: Long, endExclusive: Long): Flow<List<ShiftPaySnapshotEntity>>

    @Query("SELECT * FROM shift_pay_snapshots ORDER BY shiftId")
    suspend fun getAll(): List<ShiftPaySnapshotEntity>

    @Query("SELECT * FROM shift_pay_snapshots WHERE shiftId = :shiftId LIMIT 1")
    suspend fun findByShiftId(shiftId: Long): ShiftPaySnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: ShiftPaySnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snapshots: List<ShiftPaySnapshotEntity>)

    @Query("DELETE FROM shift_pay_snapshots")
    suspend fun deleteAll()
}
