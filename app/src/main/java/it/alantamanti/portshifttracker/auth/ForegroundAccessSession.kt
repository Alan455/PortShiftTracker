package it.alantamanti.portshifttracker.auth

/**
 * A server response is usable only for the latest verification started while
 * the app is in the foreground. Pausing, resuming, signing out, or starting a
 * newer verification invalidates the result of every older request.
 */
internal class ForegroundAccessSession {
    private var generation = 0L
    private var foreground = false

    fun resume() {
        foreground = true
        generation++
    }

    fun pause() {
        foreground = false
        generation++
    }

    fun beginVerification(): Long? = if (foreground) ++generation else null

    fun isCurrent(requestVersion: Long): Boolean =
        foreground && requestVersion == generation

    fun invalidate() {
        generation++
    }
}
