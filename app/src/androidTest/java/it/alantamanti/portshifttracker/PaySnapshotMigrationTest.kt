package it.alantamanti.portshifttracker

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.alantamanti.portshifttracker.data.local.AppDatabase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end migration of a real v7 SQLite file to Room v8.
 *
 * Read the checked-in v7 schema with org.json instead of MigrationTestHelper:
 * some room-testing / kotlinx-serialization runtime combinations throw
 * AbstractMethodError while DESERIALIZING FieldBundle, before any migration
 * SQL runs. Using Room.databaseBuilder to open the old file still runs the
 * actual Migration(7, 8) and Room's own post-migration schema validation.
 */
@RunWith(AndroidJUnit4::class)
class PaySnapshotMigrationTest {
    @Test
    fun migratingExistingV7PreservesRowsAndCreatesEmptySnapshotTable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val dbName = "shift-pay-migration-test"
        val schemaPath =
            "it.alantamanti.portshifttracker.data.local.AppDatabase/7.json"
        val schema = JSONObject(
            instrumentation.context.assets.open(schemaPath)
                .bufferedReader().use { it.readText() }
        ).getJSONObject("database")

        // This removes only the dedicated TEST database; it does not touch
        // the user's Shift database or any of its saved performances.
        context.deleteDatabase(dbName)
        val file = context.getDatabasePath(dbName)
        check(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)

        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val tableName = entity.getString("tableName")
                    old.execSQL(
                        entity.getString("createSql")
                            .replace("$" + "{TABLE_NAME}", tableName)
                    )
                    val indices = entity.optJSONArray("indices") ?: continue
                    for (j in 0 until indices.length()) {
                        old.execSQL(
                            indices.getJSONObject(j).getString("createSql")
                                .replace("$" + "{TABLE_NAME}", tableName)
                        )
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
                old.execSQL(
                    "INSERT INTO workers (id,name,hourlyRateCents,basePayMode," +
                        "baseShiftCents,doubleBaseCents,irpefBasisPoints,senioritySteps) " +
                        "VALUES (1,'Test',0,'FIXED_PER_SHIFT',6780,8840,3000,3)"
                )
                old.execSQL(
                    "INSERT INTO shifts (id,workerId,startEpochMillis,endEpochMillis," +
                        "zoneId,role,notes,serviceEpochDay,performanceType) " +
                        "VALUES (9,1,1000000,4600000,'Europe/Rome'," +
                        "'Operatore','storico',0,'TURNO')"
                )
                old.version = 7
            }

            // Opening a real v7 database exercises the app's registered
            // migration and Room's own validation against the current schema.
            val migrated = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .addMigrations(PortShiftApplication.MIGRATION_7_8)
                .allowMainThreadQueries()
                .build()
            try {
                val db = migrated.openHelper.writableDatabase
                db.query("PRAGMA user_version").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(8, cursor.getInt(0))
                }
                db.query("SELECT id, baseShiftCents FROM workers WHERE id = 1").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(1L, cursor.getLong(0))
                    assertEquals(6780L, cursor.getLong(1))
                }
                db.query("SELECT id, notes FROM shifts WHERE id = 9").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(9L, cursor.getLong(0))
                    assertEquals("storico", cursor.getString(1))
                }
                db.query("SELECT count(*) FROM shift_pay_snapshots").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0)) // seeded by application after migration
                }
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(dbName)
        }
    }
}
