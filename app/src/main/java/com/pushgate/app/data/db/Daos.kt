package com.pushgate.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {

    @Query("SELECT * FROM blocked_apps ORDER BY label COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<BlockedApp>>

    @Query("SELECT * FROM blocked_apps WHERE enabled = 1")
    suspend fun activeList(): List<BlockedApp>

    @Query("SELECT packageName FROM blocked_apps WHERE enabled = 1")
    suspend fun activePackages(): List<String>

    @Query("SELECT * FROM blocked_apps WHERE packageName = :pkg LIMIT 1")
    suspend fun find(pkg: String): BlockedApp?

    @Upsert
    suspend fun upsert(app: BlockedApp)

    @Upsert
    suspend fun upsertAll(apps: List<BlockedApp>)

    @Query("DELETE FROM blocked_apps WHERE packageName = :pkg")
    suspend fun remove(pkg: String)

    @Query("UPDATE blocked_apps SET enabled = :enabled WHERE packageName = :pkg")
    suspend fun setEnabled(pkg: String, enabled: Boolean)
}

@Dao
interface UsageDao {

    @Query("SELECT * FROM daily_usage WHERE dateKey = :dateKey AND packageName = :pkg LIMIT 1")
    suspend fun get(dateKey: String, pkg: String): DailyUsage?

    @Query("SELECT * FROM daily_usage WHERE dateKey = :dateKey")
    fun observeDay(dateKey: String): Flow<List<DailyUsage>>

    @Query("SELECT * FROM daily_usage WHERE dateKey >= :fromKey ORDER BY dateKey ASC")
    fun observeSince(fromKey: String): Flow<List<DailyUsage>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(row: DailyUsage)

    @Query("UPDATE daily_usage SET usedMs = usedMs + :deltaMs WHERE dateKey = :dateKey AND packageName = :pkg")
    suspend fun addUsage(dateKey: String, pkg: String, deltaMs: Long)

    @Query("UPDATE daily_usage SET earnedMs = earnedMs + :deltaMs WHERE dateKey = :dateKey AND packageName = :pkg")
    suspend fun addEarnedUsage(dateKey: String, pkg: String, deltaMs: Long)

    @Query("UPDATE daily_usage SET opens = opens + 1 WHERE dateKey = :dateKey AND packageName = :pkg")
    suspend fun addOpen(dateKey: String, pkg: String)

    @Query("SELECT COALESCE(SUM(usedMs), 0) FROM daily_usage WHERE dateKey = :dateKey")
    suspend fun totalForDay(dateKey: String): Long

    @Query("DELETE FROM daily_usage WHERE dateKey < :beforeKey")
    suspend fun pruneBefore(beforeKey: String)
}

@Dao
interface GrantDao {

    @Query("SELECT * FROM unlock_grants WHERE packageName = :pkg AND expiresAt > :now ORDER BY expiresAt DESC LIMIT 1")
    suspend fun activeFor(pkg: String, now: Long): UnlockGrant?

    @Query("SELECT * FROM unlock_grants WHERE expiresAt > :now")
    fun observeActive(now: Long): Flow<List<UnlockGrant>>

    @Insert
    suspend fun insert(grant: UnlockGrant): Long

    @Query("DELETE FROM unlock_grants WHERE packageName = :pkg")
    suspend fun revoke(pkg: String)

    @Query("DELETE FROM unlock_grants")
    suspend fun revokeAll()

    @Query("UPDATE unlock_grants SET expiresAt = :now WHERE packageName = :pkg AND expiresAt > :now")
    suspend fun expireNow(pkg: String, now: Long)

    @Query("DELETE FROM unlock_grants WHERE expiresAt < :cutoff")
    suspend fun pruneBefore(cutoff: Long)

    @Query("SELECT COUNT(*) FROM unlock_grants WHERE packageName = :pkg AND source = 'EARNED' AND grantedAt >= :since")
    suspend fun earnedCountSince(pkg: String, since: Long): Int
}

@Dao
interface RepSessionDao {

    @Insert
    suspend fun insert(session: RepSession): Long

    @Query("SELECT * FROM rep_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RepSession>>

    @Query("SELECT COALESCE(SUM(repsCompleted), 0) FROM rep_sessions WHERE startedAt >= :since AND completed = 1")
    fun observeRepsSince(since: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(repsCompleted), 0) FROM rep_sessions WHERE completed = 1")
    fun observeTotalReps(): Flow<Int>
}

@Dao
interface EventDao {

    @Insert
    suspend fun insert(event: EventLog)

    @Query("SELECT * FROM event_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<EventLog>>

    @Query("SELECT COUNT(*) FROM event_log WHERE type = :type AND timestamp >= :since")
    fun observeCountSince(type: String, since: Long): Flow<Int>

    @Query("DELETE FROM event_log WHERE timestamp < :cutoff")
    suspend fun pruneBefore(cutoff: Long)
}
