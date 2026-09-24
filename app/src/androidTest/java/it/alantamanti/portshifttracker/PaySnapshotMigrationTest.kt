package it.alantamanti.portshifttracker

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uses the checked-in v7 schema and the generated v8 schema to verify that
 * installing an update does not drop saved performances or worker rates.
 */
@RunWith(AndroidJUnit4::class)
class PaySnapshotMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), it.alantamanti.portshifttracker.data.local.AppDatabase::class.java
    )

    @Test fun migratingExistingV7PreservesRowsAndCreatesEmptySnapshotTable() {
        val dbName = "shift-pay-migration-test"
        val before = helper.createDatabase(dbName, 7)
        before.execSQL(
            "INSERT INTO workers (id,name,hourlyRateCents,basePayMode,baseShiftCents,doubleBaseCents,irpefBasisPoints,senioritySteps) " +
                "VALUES (1,'Test',0,'FIXED_PER_SHIFT',6780,8840,3000,3)"
        )
        before.execSQL(
            "INSERT INTO shifts (id,workerId,startEpochMillis,endEpochMillis,zoneId,role,notes,serviceEpochDay,performanceType) " +
                "VALUES (9,1,1000000,4600000,'Europe/Rome','Operatore','storico',0,'TURNO')"
        )
        before.close()

        val after = helper.runMigrationsAndValidate(
            dbName, 8, true, PortShiftApplication.MIGRATION_7_8
        )
        try {
            after.query("SELECT id, baseShiftCents FROM workers WHERE id = 1").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
                assertEquals(6780L, cursor.getLong(1))
            }
            after.query("SELECT id, notes FROM shifts WHERE id = 9").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(9L, cursor.getLong(0))
                assertEquals("storico", cursor.getString(1))
            }
            after.query("SELECT count(*) FROM shift_pay_snapshots").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0)) // populated once after seeding/correction
            }
        } finally {
            after.close()
        }
    }
}
