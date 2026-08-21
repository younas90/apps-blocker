package com.pushgate.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pushgate_settings")

/**
 * Everything the user can tune. Held in DataStore rather than Room because the accessibility
 * service reads it on every foreground transition and needs a cheap, always-warm snapshot.
 */
data class Settings(
    val onboardingComplete: Boolean = false,
    /** Epoch day the taper plan began. -1 means no plan running. */
    val planStartEpochDay: Long = -1L,
    val planDays: Int = 7,
    val startMinutesPerDay: Int = 60,
    val endMinutesPerDay: Int = 10,
    val rolloverHour: Int = 4,

    val strictMode: Boolean = true,
    /** Minutes the user must wait after requesting to disable protection. */
    val cooldownMinutes: Int = 30,
    /** Timestamp at which a pending disable-request matures. 0 means none pending. */
    val cooldownEndsAt: Long = 0L,

    val baseReps: Int = 5,
    val baseUnlockMinutes: Int = 2,
    /** Each extra earned unlock in the same day costs this much more. 0.5 adds 50% per unlock. */
    val escalation: Float = 0.5f,
    val maxEarnedUnlocksPerDay: Int = 8,

    /** Elbow angle below which a rep counts as down. Lower is stricter. */
    val downAngleThreshold: Float = 95f,
    /** Elbow angle above which a rep counts as up. */
    val upAngleThreshold: Float = 155f,
    /** Max allowed hip sag or pike deviation from a straight body line, in degrees. */
    val maxBodyBend: Float = 25f,
    /** Reps faster than this are rejected as bouncing. */
    val minRepMillis: Int = 600,

    val allowEarnedUnlocks: Boolean = true,
    val quotaWarningSeconds: Int = 30,
    val vibrateOnRep: Boolean = true,
    val deviceAdminActive: Boolean = false,
    val cameraFacingFront: Boolean = true
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val planStartEpochDay = longPreferencesKey("plan_start_epoch_day")
        val planDays = intPreferencesKey("plan_days")
        val startMinutes = intPreferencesKey("start_minutes")
        val endMinutes = intPreferencesKey("end_minutes")
        val rolloverHour = intPreferencesKey("rollover_hour")
        val strictMode = booleanPreferencesKey("strict_mode")
        val cooldownMinutes = intPreferencesKey("cooldown_minutes")
        val cooldownEndsAt = longPreferencesKey("cooldown_ends_at")
        val baseReps = intPreferencesKey("base_reps")
        val baseUnlockMinutes = intPreferencesKey("base_unlock_minutes")
        val escalation = floatPreferencesKey("escalation")
        val maxEarnedUnlocks = intPreferencesKey("max_earned_unlocks")
        val downAngle = floatPreferencesKey("down_angle")
        val upAngle = floatPreferencesKey("up_angle")
        val maxBodyBend = floatPreferencesKey("max_body_bend")
        val minRepMillis = intPreferencesKey("min_rep_millis")
        val allowEarned = booleanPreferencesKey("allow_earned")
        val warningSeconds = intPreferencesKey("warning_seconds")
        val vibrateOnRep = booleanPreferencesKey("vibrate_on_rep")
        val deviceAdminActive = booleanPreferencesKey("device_admin_active")
        val cameraFront = booleanPreferencesKey("camera_front")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            onboardingComplete = p[Keys.onboardingComplete] ?: d.onboardingComplete,
            planStartEpochDay = p[Keys.planStartEpochDay] ?: d.planStartEpochDay,
            planDays = p[Keys.planDays] ?: d.planDays,
            startMinutesPerDay = p[Keys.startMinutes] ?: d.startMinutesPerDay,
            endMinutesPerDay = p[Keys.endMinutes] ?: d.endMinutesPerDay,
            rolloverHour = p[Keys.rolloverHour] ?: d.rolloverHour,
            strictMode = p[Keys.strictMode] ?: d.strictMode,
            cooldownMinutes = p[Keys.cooldownMinutes] ?: d.cooldownMinutes,
            cooldownEndsAt = p[Keys.cooldownEndsAt] ?: d.cooldownEndsAt,
            baseReps = p[Keys.baseReps] ?: d.baseReps,
            baseUnlockMinutes = p[Keys.baseUnlockMinutes] ?: d.baseUnlockMinutes,
            escalation = p[Keys.escalation] ?: d.escalation,
            maxEarnedUnlocksPerDay = p[Keys.maxEarnedUnlocks] ?: d.maxEarnedUnlocksPerDay,
            downAngleThreshold = p[Keys.downAngle] ?: d.downAngleThreshold,
            upAngleThreshold = p[Keys.upAngle] ?: d.upAngleThreshold,
            maxBodyBend = p[Keys.maxBodyBend] ?: d.maxBodyBend,
            minRepMillis = p[Keys.minRepMillis] ?: d.minRepMillis,
            allowEarnedUnlocks = p[Keys.allowEarned] ?: d.allowEarnedUnlocks,
            quotaWarningSeconds = p[Keys.warningSeconds] ?: d.quotaWarningSeconds,
            vibrateOnRep = p[Keys.vibrateOnRep] ?: d.vibrateOnRep,
            deviceAdminActive = p[Keys.deviceAdminActive] ?: d.deviceAdminActive,
            cameraFacingFront = p[Keys.cameraFront] ?: d.cameraFacingFront
        )
    }

    suspend fun current(): Settings = flow.first()

    suspend fun completeOnboarding() = edit { it[Keys.onboardingComplete] = true }

    suspend fun startPlan(startEpochDay: Long, days: Int, startMinutes: Int, endMinutes: Int) = edit {
        it[Keys.planStartEpochDay] = startEpochDay
        it[Keys.planDays] = days
        it[Keys.startMinutes] = startMinutes
        it[Keys.endMinutes] = endMinutes
    }

    suspend fun setStrictMode(on: Boolean) = edit { it[Keys.strictMode] = on }
    suspend fun setCooldownEndsAt(at: Long) = edit { it[Keys.cooldownEndsAt] = at }
    suspend fun setCooldownMinutes(v: Int) = edit { it[Keys.cooldownMinutes] = v }
    suspend fun setBaseReps(v: Int) = edit { it[Keys.baseReps] = v }
    suspend fun setBaseUnlockMinutes(v: Int) = edit { it[Keys.baseUnlockMinutes] = v }
    suspend fun setEscalation(v: Float) = edit { it[Keys.escalation] = v }
    suspend fun setMaxEarnedUnlocks(v: Int) = edit { it[Keys.maxEarnedUnlocks] = v }
    suspend fun setAllowEarnedUnlocks(v: Boolean) = edit { it[Keys.allowEarned] = v }
    suspend fun setDeviceAdminActive(v: Boolean) = edit { it[Keys.deviceAdminActive] = v }
    suspend fun setCameraFront(v: Boolean) = edit { it[Keys.cameraFront] = v }
    suspend fun setVibrateOnRep(v: Boolean) = edit { it[Keys.vibrateOnRep] = v }
    suspend fun setRolloverHour(v: Int) = edit { it[Keys.rolloverHour] = v }
    suspend fun setWarningSeconds(v: Int) = edit { it[Keys.warningSeconds] = v }

    suspend fun setFormStrictness(downAngle: Float, upAngle: Float, maxBend: Float, minRepMs: Int) = edit {
        it[Keys.downAngle] = downAngle
        it[Keys.upAngle] = upAngle
        it[Keys.maxBodyBend] = maxBend
        it[Keys.minRepMillis] = minRepMs
    }

    private suspend fun edit(block: suspend (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
