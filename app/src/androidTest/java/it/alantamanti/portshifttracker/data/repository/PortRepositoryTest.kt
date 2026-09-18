package it.alantamanti.portshifttracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import it.alantamanti.portshifttracker.data.local.AllowanceRuleEntity
import it.alantamanti.portshifttracker.data.local.AppDatabase
import it.alantamanti.portshifttracker.data.local.ShiftEntity
import it.alantamanti.portshifttracker.data.local.WorkerEntity
import it.alantamanti.portshifttracker.domain.AllowanceApplicationMode
import it.alantamanti.portshifttracker.domain.AllowanceCalculationType
import it.alantamanti.portshifttracker.domain.AllowanceCategory
import it.alantamanti.portshifttracker.domain.PerformanceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PortRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: PortRepository

    private val turnoRule = AllowanceRuleEntity(
        id = 1,
        name = "Sera",
        code = "SERA",
        calculationType = AllowanceCalculationType.FIXED_PER_SHIFT,
        value = 426,
        category = AllowanceCategory.TURNO,
        applicationMode = AllowanceApplicationMode.MANUAL,
        exclusiveGroup = "TIPO_TURNO",
        performanceMask = PerformanceType.TURNO.maskBit
    )

    private val doppioRule = AllowanceRuleEntity(
        id = 2,
        name = "Sera doppio",
        code = "DOP_SERA",
        calculationType = AllowanceCalculationType.FIXED_PER_SHIFT,
        value = 426,
        category = AllowanceCategory.DOPPIO,
        applicationMode = AllowanceApplicationMode.MANUAL,
        exclusiveGroup = "DOPPIO",
        performanceMask = PerformanceType.DOPPIO.maskBit
    )

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PortRepository(db)
        db.workerDao().insertAll(
            listOf(WorkerEntity(id = 1, name = "Test", hourlyRateCents = 0))
        )
        db.allowanceRuleDao().insertAll(listOf(turnoRule, doppioRule))
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun same_performance_same_day_is_rejected() = runBlocking {
        val date = LocalDate.of(2026, 9, 18)
        repository.addShiftWithSelections(shift(date, PerformanceType.TURNO), setOf(1))

        assertThrows(DuplicatePerformanceException::class.java) {
            runBlocking {
                repository.addShiftWithSelections(shift(date, PerformanceType.TURNO), setOf(1))
            }
        }
    }

    @Test
    fun turno_and_doppio_same_day_are_allowed() = runBlocking {
        val date = LocalDate.of(2026, 9, 18)
        repository.addShiftWithSelections(shift(date, PerformanceType.TURNO), setOf(1))
        repository.addShiftWithSelections(shift(date, PerformanceType.DOPPIO), setOf(2))

        assertEquals(2, db.shiftDao().getAll().size)
    }

    @Test
    fun range_save_rolls_back_completely_when_one_day_conflicts() = runBlocking {
        val first = LocalDate.of(2026, 9, 18)
        val second = first.plusDays(1)

        repository.addShiftWithSelections(shift(second, PerformanceType.TURNO), setOf(1))

        assertThrows(DuplicatePerformanceException::class.java) {
            runBlocking {
                repository.addShiftsWithSelections(
                    listOf(
                        shift(first, PerformanceType.TURNO),
                        shift(second, PerformanceType.TURNO)
                    ),
                    setOf(1)
                )
            }
        }

        // Rimane soltanto il record preesistente: il primo giorno del range è rollbackato.
        assertEquals(1, db.shiftDao().getAll().size)
    }

    private fun shift(date: LocalDate, type: PerformanceType): ShiftEntity {
        val zone = ZoneId.of("Europe/Rome")
        val start = date.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        val end = date.atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        return ShiftEntity(
            workerId = 1,
            startEpochMillis = start,
            endEpochMillis = end,
            zoneId = zone.id,
            performanceType = type
        )
    }
}
