package it.alantamanti.portshifttracker.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class AppFeatureStoreTest {
    @Test
    fun month_lock_is_persisted_in_datastore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AppFeatureStore(context)
        store.importJson("""{"schemaVersion":1}""")

        val month = YearMonth.of(2026, 9)
        store.savePayslip(PayslipComparison(month = month.toString(), locked = true))
        assertTrue(store.payslip(month)?.locked == true)

        store.savePayslip(store.payslip(month)!!.copy(locked = false))
        assertFalse(store.payslip(month)?.locked == true)
    }
}
