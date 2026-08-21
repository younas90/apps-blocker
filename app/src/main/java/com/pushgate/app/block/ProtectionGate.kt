package com.pushgate.app.block

import com.pushgate.app.data.prefs.Settings

/**
 * One rule for every action that makes PushGate weaker.
 *
 * Guarding the Settings screens while leaving a delete button on the app list was a hole big
 * enough to walk through: the urge that would have sent someone into Settings just sent them two
 * taps into PushGate itself instead. Removing an app, pausing it, raising its budget and switching
 * Strict Mode off are all the same act, so they all cost the same thing — the wait.
 *
 * Actions that make PushGate *stronger* (adding apps, lowering a budget, tightening form) are
 * never gated. There is no reason to make the right thing hard.
 */
object ProtectionGate {

    sealed interface Verdict {
        /** Nothing stands in the way — Strict Mode is off, or the wait has been served. */
        data object Allowed : Verdict

        /** A wait is already running. */
        data class Waiting(val remainingMs: Long) : Verdict

        /** Strict Mode is armed and no wait has been started yet. */
        data class NeedsWait(val minutes: Int) : Verdict
    }

    fun check(settings: Settings, now: Long = System.currentTimeMillis()): Verdict = when {
        !settings.strictMode -> Verdict.Allowed
        settings.cooldownEndsAt > 0L && now >= settings.cooldownEndsAt -> Verdict.Allowed
        settings.cooldownEndsAt > now -> Verdict.Waiting(settings.cooldownEndsAt - now)
        else -> Verdict.NeedsWait(settings.cooldownMinutes)
    }

    fun isAllowed(settings: Settings, now: Long = System.currentTimeMillis()): Boolean =
        check(settings, now) is Verdict.Allowed

    /** Human-readable reason, used verbatim in the dialog so the wording is consistent everywhere. */
    fun explain(verdict: Verdict, action: String): String = when (verdict) {
        is Verdict.Allowed ->
            "The wait is served. You can $action now."
        is Verdict.Waiting ->
            "You already started the wait. $action becomes possible when it finishes."
        is Verdict.NeedsWait ->
            "Strict Mode is on, so $action takes a ${verdict.minutes}-minute wait first.\n\n" +
                "You are not locked out. You are just not allowed to do it in the ten seconds " +
                "where it feels urgent."
    }
}
