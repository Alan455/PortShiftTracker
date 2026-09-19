package it.alantamanti.portshifttracker.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundAccessSessionTest {
    @Test
    fun noVerificationCanBeginWhileAppIsInBackground() {
        val session = ForegroundAccessSession()
        assertNull(session.beginVerification())
        session.resume()
        val activeRequest = session.beginVerification()
        assertNotNull(activeRequest)
        session.pause()
        assertNull(session.beginVerification())
        assertFalse(session.isCurrent(activeRequest!!))
    }

    @Test
    fun returningToForegroundRequiresNewServerVerification() {
        val session = ForegroundAccessSession()
        session.resume()
        val oldRequest = session.beginVerification()!!
        session.pause()
        session.resume()

        assertFalse(session.isCurrent(oldRequest))
        val newRequest = session.beginVerification()!!
        assertTrue(session.isCurrent(newRequest))
    }

    @Test
    fun olderServerResponseCannotOverrideNewerVerificationOrSignOut() {
        val session = ForegroundAccessSession()
        session.resume()
        val oldRequest = session.beginVerification()!!
        val newRequest = session.beginVerification()!!

        assertFalse(session.isCurrent(oldRequest))
        assertTrue(session.isCurrent(newRequest))
        session.invalidate()
        assertFalse(session.isCurrent(newRequest))
    }
}
