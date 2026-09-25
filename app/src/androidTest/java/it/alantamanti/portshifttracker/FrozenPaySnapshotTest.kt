package it.alantamanti.portshifttracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.alantamanti.portshifttracker.data.local.AppDatabase
import it.alantamanti.portshifttracker.data.local.DefaultCatalog
import it.alantamanti.portshifttracker.data.local.ShiftEntity
import it.alantamanti.portshifttracker.data.local.WorkerEntity
import it.alantamanti.portshifttracker.data.repository.BackupCodec
import it.alantamanti.portshifttracker.data.repository.PortRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class FrozenPaySnapshotTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: PortRepository

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).build()
        repository = PortRepository(db)
        db.workerDao().upsert(WorkerEntity(id = 1, name = "Test", hourlyRateCents = 0))
        DefaultCatalog.rules().forEach { db.allowanceRuleDao().insertIfMissing(it) }
    }

    @After fun tearDown() {
        db.close()
    }

    @Test fun rateEditDoesNotAlterPreviouslyRecordedAllowances() = runBlocking {
        val rules = db.allowanceRuleDao().getAll()
        val mattina = rules.single { it.code == "MAT" }
        val q2 = rules.single { it.code == "AREA_Q2" }
        val date = LocalDate.of(2026, 9, 17)
        val start = date.atTime(6, 30).atZone(ZoneId.of("Europe/Rome")).toInstant().toEpochMilli()
        val shift = ShiftEntity(workerId = 1, startEpochMillis = start, endEpochMillis = start + 6L * 3600_000L)
        repository.addShiftWithSelections(shift, setOf(mattina.id, q2.id))

        val before = repository.shiftRows.first().single().pay
        assertEquals(932L, before.allowanceLines.single { it.ruleId == q2.id }.amountCents)
        repository.saveRule(q2.copy(value = 1200))
        val historic = repository.shiftRows.first().single().pay
        assertEquals(before, historic)
        // Editing notes is not an economic event.
        val savedShift = db.shiftDao().getAll().single()
        repository.updateShiftWithSelections(
            savedShift.copy(notes = "Solo nota"),
            setOf(mattina.id, q2.id)
        )
        assertEquals(before, repository.shiftRows.first().single().pay)

        val backup = repository.exportSnapshot()
        assertEquals(1, backup.paySnapshots.size)
        val saved = BackupCodec.encode(backup, "{}")
        val restored = BackupCodec.decode(saved)
        assertEquals(1, restored.database.paySnapshots.size)
        assertEquals(before, restored.database.paySnapshots.single().toBreakdown())

        db.shiftPaySnapshotDao().deleteAll()
        repository.restoreSnapshot(restored.database)
        assertEquals(before, repository.shiftRows.first().single().pay)
    }

    @Test fun renamingAllowancePreservesOldNameAndAmountIncludingBackupRestore() = runBlocking {
        val rules = db.allowanceRuleDao().getAll()
        val mattina = rules.single { it.code == "MAT" }
        val q2 = rules.single { it.code == "AREA_Q2" }
        val date = LocalDate.of(2026, 9, 17)
        val start = date.atTime(6, 30).atZone(ZoneId.of("Europe/Rome")).toInstant().toEpochMilli()
        val previousId = repository.addShiftWithSelections(
            ShiftEntity(workerId = 1, startEpochMillis = start, endEpochMillis = start + 6L * 3600_000L),
            setOf(mattina.id, q2.id)
        )
        val before = repository.shiftRows.first().single().pay
        val oldLine = before.allowanceLines.single { it.ruleId == q2.id }

        repository.saveRule(q2.copy(name = "Q2 rinominata", value = 1_200L))
        val oldRow = repository.shiftRows.first().single()
        assertEquals(before, oldRow.pay)
        assertEquals("Q2", oldRow.pay.allowanceLines.single { it.ruleId == q2.id }.name)
        assertEquals(932L, oldLine.amountCents)
        assertEquals("Q2 rinominata", oldRow.selectedRules.single { it.id == q2.id }.name)

        val laterId = repository.addShiftWithSelections(
            ShiftEntity(workerId = 1, startEpochMillis = start + 24L * 3600_000L,
                endEpochMillis = start + 30L * 3600_000L),
            setOf(mattina.id, q2.id)
        )
        val rows = repository.shiftRows.first().associateBy { it.shift.id }
        val newLine = rows.getValue(laterId).pay.allowanceLines.single { it.ruleId == q2.id }
        assertEquals("Q2 rinominata", newLine.name)
        assertEquals(1_200L, newLine.amountCents)
        assertEquals(before, rows.getValue(previousId).pay)

        repository.restoreSnapshot(BackupCodec.decode(BackupCodec.encode(repository.exportSnapshot(), "{}")).database)
        val restored = repository.shiftRows.first().associateBy { it.shift.id }
        assertEquals(before, restored.getValue(previousId).pay)
        assertEquals("Q2 rinominata",
            restored.getValue(laterId).pay.allowanceLines.single { it.ruleId == q2.id }.name)
    }

    @Test fun notesOnlyUpdatePreservesStoredTimeSelectionsAndHistoricalPay() = runBlocking {
        val rules = db.allowanceRuleDao().getAll()
        val mattina = rules.single { it.code == "MAT" }
        val q2 = rules.single { it.code == "AREA_Q2" }
        val start = LocalDate.of(2026, 9, 17).atTime(6, 30)
            .atZone(ZoneId.of("Europe/Rome")).toInstant().toEpochMilli() + 12_345L
        val id = repository.addShiftWithSelections(
            ShiftEntity(workerId = 1, startEpochMillis = start, endEpochMillis = start + 6L * 3600_000L),
            setOf(mattina.id, q2.id)
        )
        val originalShift = db.shiftDao().getAll().single()
        val originalSelections = db.shiftAllowanceSelectionDao().getAll()
        val originalSnapshot = db.shiftPaySnapshotDao().findByShiftId(id)!!
        repository.saveRule(q2.copy(value = 1200L))
        repository.updateShiftNotes(id, "Nota aggiornata dopo la modifica di Q2")
        assertEquals(
            originalShift.copy(notes = "Nota aggiornata dopo la modifica di Q2"),
            db.shiftDao().getAll().single()
        )
        assertEquals(originalSelections, db.shiftAllowanceSelectionDao().getAll())
        assertEquals(originalSnapshot, db.shiftPaySnapshotDao().findByShiftId(id))
        assertEquals(originalSnapshot.toBreakdown(), repository.shiftRows.first().single().pay)
    }

    @Test fun legacyShiftIsFrozenBeforeAnOrdinaryTariffEdit() = runBlocking {
        val rules = db.allowanceRuleDao().getAll()
        val mattina = rules.single { it.code == "MAT" }
        val q2 = rules.single { it.code == "AREA_Q2" }
        val start = LocalDate.of(2026, 9, 17).atTime(6, 30)
            .atZone(ZoneId.of("Europe/Rome")).toInstant().toEpochMilli()
        val oldId = db.shiftDao().insert(
            ShiftEntity(workerId = 1, startEpochMillis = start, endEpochMillis = start + 6L * 3600_000L)
        )
        db.shiftAllowanceSelectionDao().insertAll(listOf(
            it.alantamanti.portshifttracker.data.local.ShiftAllowanceSelectionEntity(oldId, mattina.id),
            it.alantamanti.portshifttracker.data.local.ShiftAllowanceSelectionEntity(oldId, q2.id)
        ))
        assertTrue(db.shiftPaySnapshotDao().getAll().isEmpty())
        val original = repository.shiftRows.first().single().pay
        repository.saveRule(q2.copy(value = 1200))
        assertEquals(1, db.shiftPaySnapshotDao().getAll().size)
        assertEquals(original, repository.shiftRows.first().single().pay)
    }

    @Test fun targetedRetroactiveCorrectionUpdatesOnlyRequestedAllowance() = runBlocking {
        val rules = db.allowanceRuleDao().getAll()
        val mattina = rules.single { it.code == "MAT" }
        val q2 = rules.single { it.code == "AREA_Q2" }
        val start = LocalDate.of(2026, 9, 17).atTime(6, 30)
            .atZone(ZoneId.of("Europe/Rome")).toInstant().toEpochMilli()
        repository.addShiftWithSelections(
            ShiftEntity(workerId = 1, startEpochMillis = start, endEpochMillis = start + 6L * 3600_000L),
            setOf(mattina.id, q2.id)
        )
        val before = repository.shiftRows.first().single().pay
        assertEquals(1, repository.correctHistoricAllowanceLine("AREA_Q2") { _, previous -> previous + 100 })
        val after = repository.shiftRows.first().single().pay
        assertEquals(before.basePayCents, after.basePayCents)
        assertEquals(before.allowanceLines.filter { it.ruleId != q2.id },
            after.allowanceLines.filter { it.ruleId != q2.id })
        assertEquals(before.totalPayCents + 100L, after.totalPayCents)
        assertEquals(0, repository.correctHistoricAllowanceLine("AREA_Q2") { _, previous -> previous })
    }

    @Test fun v1BackupWithoutSnapshotsCanStillBeRead() = runBlocking {
        val backup = repository.exportSnapshot()
        val root = JSONObject(BackupCodec.encode(backup, "{}"))
        root.put("schemaVersion", 1)
        root.remove("paySnapshots")
        val parsed = BackupCodec.decode(root.toString())
        assertTrue(parsed.database.paySnapshots.isEmpty())
    }

    @Test fun changingGiornalieroUpdatesDerivedTariffsWithoutRewritingHistory() = runBlocking {
        val rules = db.allowanceRuleDao().getAll()
        val giornaliero = rules.single { it.code == "G" }
        repository.saveRule(giornaliero.copy(value = 10_000L))
        val updated = db.allowanceRuleDao().getAll().associateBy { it.code }
        assertEquals(5_000L, updated.getValue("DOP_G").value)
        assertEquals(5_000L, updated.getValue("DOP_ON_MEZZO").value)
    }
}
