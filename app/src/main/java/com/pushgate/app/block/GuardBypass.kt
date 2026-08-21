package com.pushgate.app.block

import android.os.SystemClock

/**
 * A short, deliberate hole in the Strict Mode guard.
 *
 * PushGate itself sometimes has to send the user into Settings — to grant the camera permission,
 * to enable the accessibility service in the first place, or to serve out a disable cooldown.
 * Without this, the guard would slam the door on the app's own setup flow.
 *
 * The window is opened only from PushGate's own UI, is measured in minutes, and is cleared as soon
 * as the user comes back. It cannot be opened from a blocked app or from Settings itself.
 */
object GuardBypass {

    @Volatile private var expiresAtUptime: Long = 0L
    @Volatile private var reason: String = ""

    fun open(minutes: Int, why: String) {
        expiresAtUptime = SystemClock.elapsedRealtime() + minutes * 60_000L
        reason = why
    }

    fun close() {
        expiresAtUptime = 0L
        reason = ""
    }

    val isOpen: Boolean
        get() = expiresAtUptime > SystemClock.elapsedRealtime()

    val currentReason: String get() = if (isOpen) reason else ""
}
