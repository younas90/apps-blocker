package com.pushgate.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** An app the user has chosen to put behind the gate. */
@Entity(tableName = "blocked_apps")
data class BlockedApp(
    @PrimaryKey val packageName: String,
    val label: String,
    val enabled: Boolean = true,
    /** Optional per-app override of the daily budget, in minutes. Null = use the plan default. */
    val customBudgetMinutes: Int? = null,
    val addedAt: Long = System.currentTimeMillis()
)

/** How much of today's budget a given app has consumed. */
@Entity(tableName = "daily_usage", primaryKeys = ["dateKey", "packageName"])
data class DailyUsage(
    val dateKey: String,
    val packageName: String,
    /** Time that counted against the daily budget. */
    val usedMs: Long = 0L,
    /** Time spent inside a grant the user paid push-ups for. Tracked for stats, never charged. */
    val earnedMs: Long = 0L,
    val opens: Int = 0
)

/**
 * A time-boxed permission to use an app. Created either by the daily quota (source=QUOTA)
 * or by paying push-ups (source=EARNED).
 */
@Entity(
    tableName = "unlock_grants",
    indices = [Index("packageName"), Index("expiresAt")]
)
data class UnlockGrant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val grantedAt: Long,
    val expiresAt: Long,
    val source: String,
    val repsPaid: Int = 0
) {
    companion object {
        const val SOURCE_QUOTA = "QUOTA"
        const val SOURCE_EARNED = "EARNED"
        const val SOURCE_MANUAL = "MANUAL"
    }
}

/** One completed (or abandoned) push-up challenge. Drives the stats screen. */
@Entity(tableName = "rep_sessions", indices = [Index("startedAt")])
data class RepSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startedAt: Long,
    val endedAt: Long,
    val repsRequired: Int,
    val repsCompleted: Int,
    val secondsEarned: Int,
    val avgDepthDegrees: Float,
    val avgFormScore: Float,
    val rejectedReps: Int,
    val completed: Boolean
)

/** Append-only log of everything interesting: blocks, unlocks, tamper attempts. */
@Entity(tableName = "event_log", indices = [Index("timestamp"), Index("type")])
data class EventLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String,
    val packageName: String? = null,
    val detail: String? = null
) {
    companion object {
        const val BLOCKED = "BLOCKED"
        const val QUOTA_STARTED = "QUOTA_STARTED"
        const val QUOTA_EXHAUSTED = "QUOTA_EXHAUSTED"
        const val EARNED_UNLOCK = "EARNED_UNLOCK"
        const val CHALLENGE_ABANDONED = "CHALLENGE_ABANDONED"
        const val TAMPER_SETTINGS = "TAMPER_SETTINGS"
        const val TAMPER_UNINSTALL = "TAMPER_UNINSTALL"
        const val SERVICE_DOWN = "SERVICE_DOWN"
        const val SERVICE_UP = "SERVICE_UP"
        const val COOLDOWN_STARTED = "COOLDOWN_STARTED"
        const val PLAN_STARTED = "PLAN_STARTED"
    }
}
