package com.pushgate.app.data.repo

import android.content.Context
import com.pushgate.app.data.db.AppDatabase
import com.pushgate.app.data.db.BlockedApp
import com.pushgate.app.data.db.DailyUsage
import com.pushgate.app.data.db.EventLog
import com.pushgate.app.data.db.RepSession
import com.pushgate.app.data.db.UnlockGrant
import com.pushgate.app.data.prefs.Settings
import com.pushgate.app.data.prefs.SettingsStore
import com.pushgate.app.quota.TaperPlan
import com.pushgate.app.util.TimeKeys
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * The single source of truth for "may this app run right now, and for how long".
 *
 * Everything the accessibility service needs goes through here so the decision logic lives in one
 * testable place instead of being smeared across the service, the block screen and the UI.
 */
class BlockRepository private constructor(context: Context) {

    private val db = AppDatabase.get(context)
    val settingsStore = SettingsStore(context.applicationContext)

    val blockedApps: Flow<List<BlockedApp>> = db.blockedApps().observeAll()
    val settings: Flow<Settings> = settingsStore.flow

    fun observeUsage(dateKey: String): Flow<List<DailyUsage>> = db.usage().observeDay(dateKey)
    fun observeUsageSince(fromKey: String): Flow<List<DailyUsage>> = db.usage().observeSince(fromKey)
    fun observeRecentSessions(limit: Int = 30): Flow<List<RepSession>> = db.repSessions().observeRecent(limit)
    fun observeTotalReps(): Flow<Int> = db.repSessions().observeTotalReps()
    fun observeRepsSince(since: Long): Flow<Int> = db.repSessions().observeRepsSince(since)
    fun observeEvents(limit: Int = 100): Flow<List<EventLog>> = db.events().observeRecent(limit)
    fun observeEventCountSince(type: String, since: Long): Flow<Int> =
        db.events().observeCountSince(type, since)

    // ---------------------------------------------------------------- blocked app management

    suspend fun activePackages(): Set<String> = db.blockedApps().activePackages().toSet()

    suspend fun addBlockedApps(apps: List<BlockedApp>) = db.blockedApps().upsertAll(apps)

    suspend fun removeBlockedApp(pkg: String) {
        db.blockedApps().remove(pkg)
        db.grants().revoke(pkg)
    }

    suspend fun setBlockedAppEnabled(pkg: String, enabled: Boolean) {
        db.blockedApps().setEnabled(pkg, enabled)
        if (!enabled) db.grants().revoke(pkg)
    }

    suspend fun setCustomBudget(pkg: String, minutes: Int?) {
        val existing = db.blockedApps().find(pkg) ?: return
        db.blockedApps().upsert(existing.copy(customBudgetMinutes = minutes))
    }

    // ---------------------------------------------------------------- decisions

    /**
     * What should happen the instant [pkg] comes to the foreground.
     *
     * Order matters: an explicitly earned grant outranks the daily quota, because the user has
     * already paid for it and would otherwise burn quota they thought they had bought past.
     */
    suspend fun decide(pkg: String, now: Long = System.currentTimeMillis()): BlockDecision {
        val app = db.blockedApps().find(pkg)
        if (app == null || !app.enabled) return BlockDecision.NotTracked

        val settings = settingsStore.current()
        val today = TimeKeys.dateFor(now, settings.rolloverHour)
        val dateKey = today.toString()

        val grant = db.grants().activeFor(pkg, now)
        if (grant != null) {
            return BlockDecision.AllowedByGrant(
                packageName = pkg,
                remainingMs = grant.expiresAt - now,
                source = grant.source
            )
        }

        val budgetMs = TaperPlan.budgetMillisFor(settings, today, app.customBudgetMinutes)
        val usedMs = db.usage().get(dateKey, pkg)?.usedMs ?: 0L
        val remaining = budgetMs - usedMs

        return if (remaining > 0L) {
            BlockDecision.AllowedByQuota(
                packageName = pkg,
                remainingMs = remaining,
                budgetMs = budgetMs,
                usedMs = usedMs
            )
        } else {
            val earnedToday = earnedUnlocksToday(pkg, settings, today)
            BlockDecision.Blocked(
                packageName = pkg,
                label = app.label,
                budgetMs = budgetMs,
                usedMs = usedMs,
                earnedUnlocksToday = earnedToday,
                canEarn = settings.allowEarnedUnlocks && earnedToday < settings.maxEarnedUnlocksPerDay,
                repsRequired = TaperPlan.repsForNextUnlock(settings, earnedToday),
                minutesOffered = TaperPlan.minutesForNextUnlock(settings)
            )
        }
    }

    /** Cheap pre-check used on every window transition before doing any real work. */
    suspend fun isTracked(pkg: String): Boolean = db.blockedApps().find(pkg)?.enabled == true

    private suspend fun earnedUnlocksToday(pkg: String, settings: Settings, today: LocalDate): Int {
        val dayStart = today.atStartOfDay(TimeKeys.zone())
            .plusHours(settings.rolloverHour.toLong())
            .toInstant()
            .toEpochMilli()
        return db.grants().earnedCountSince(pkg, dayStart)
    }

    // ---------------------------------------------------------------- usage accounting

    suspend fun recordUsage(pkg: String, deltaMs: Long, now: Long = System.currentTimeMillis()) {
        if (deltaMs <= 0L) return
        val settings = settingsStore.current()
        val dateKey = TimeKeys.keyFor(now, settings.rolloverHour)
        db.usage().insertIgnore(DailyUsage(dateKey, pkg))
        db.usage().addUsage(dateKey, pkg, deltaMs)
    }

    /** Time spent inside a paid grant: recorded for honest stats, never charged to the budget. */
    suspend fun recordEarnedUsage(pkg: String, deltaMs: Long, now: Long = System.currentTimeMillis()) {
        if (deltaMs <= 0L) return
        val settings = settingsStore.current()
        val dateKey = TimeKeys.keyFor(now, settings.rolloverHour)
        db.usage().insertIgnore(DailyUsage(dateKey, pkg))
        db.usage().addEarnedUsage(dateKey, pkg, deltaMs)
    }

    suspend fun recordOpen(pkg: String, now: Long = System.currentTimeMillis()) {
        val settings = settingsStore.current()
        val dateKey = TimeKeys.keyFor(now, settings.rolloverHour)
        db.usage().insertIgnore(DailyUsage(dateKey, pkg))
        db.usage().addOpen(dateKey, pkg)
    }

    suspend fun usedMsToday(pkg: String, now: Long = System.currentTimeMillis()): Long {
        val settings = settingsStore.current()
        return db.usage().get(TimeKeys.keyFor(now, settings.rolloverHour), pkg)?.usedMs ?: 0L
    }

    // ---------------------------------------------------------------- grants

    suspend fun grantEarnedUnlock(pkg: String, minutes: Int, reps: Int): UnlockGrant {
        val now = System.currentTimeMillis()
        val grant = UnlockGrant(
            packageName = pkg,
            grantedAt = now,
            expiresAt = now + minutes * 60_000L,
            source = UnlockGrant.SOURCE_EARNED,
            repsPaid = reps
        )
        val id = db.grants().insert(grant)
        logEvent(EventLog.EARNED_UNLOCK, pkg, "$reps reps -> $minutes min")
        return grant.copy(id = id)
    }

    suspend fun revokeGrant(pkg: String) = db.grants().expireNow(pkg, System.currentTimeMillis())

    suspend fun revokeAllGrants() = db.grants().revokeAll()

    suspend fun saveRepSession(session: RepSession) {
        db.repSessions().insert(session)
    }

    // ---------------------------------------------------------------- misc

    suspend fun logEvent(type: String, pkg: String? = null, detail: String? = null) {
        db.events().insert(EventLog(type = type, packageName = pkg, detail = detail))
    }

    /** Housekeeping so the DB does not grow without bound on a long-running install. */
    suspend fun prune(now: Long = System.currentTimeMillis()) {
        val settings = settingsStore.current()
        val cutoffDate = TimeKeys.dateFor(now, settings.rolloverHour).minusDays(120)
        db.usage().pruneBefore(cutoffDate.toString())
        db.events().pruneBefore(now - 120L * 24 * 60 * 60 * 1000)
        db.grants().pruneBefore(now - 7L * 24 * 60 * 60 * 1000)
    }

    companion object {
        @Volatile private var instance: BlockRepository? = null

        fun get(context: Context): BlockRepository = instance ?: synchronized(this) {
            instance ?: BlockRepository(context.applicationContext).also { instance = it }
        }
    }
}
