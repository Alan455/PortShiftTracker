package it.alantamanti.portshifttracker.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import it.alantamanti.portshifttracker.PortShiftApplication
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration67Test {
    @Test
    fun migration_6_7_adds_service_day_and_unique_index() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE shifts (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            workerId INTEGER NOT NULL,
                            startEpochMillis INTEGER NOT NULL,
                            endEpochMillis INTEGER NOT NULL,
                            zoneId TEXT NOT NULL,
                            role TEXT NOT NULL,
                            notes TEXT NOT NULL,
                            performanceType TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        PortShiftApplication.MIGRATION_6_7.migrate(db)

        val columns = mutableSetOf<String>()
        db.query("PRAGMA table_info(shifts)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) columns += cursor.getString(nameIndex)
        }
        assertTrue("serviceEpochDay" in columns)

        val indices = mutableSetOf<String>()
        db.query("PRAGMA index_list(shifts)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) indices += cursor.getString(nameIndex)
        }
        assertTrue("index_shifts_workerId_serviceEpochDay_performanceType" in indices)
        helper.close()
    }
}
