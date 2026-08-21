package com.pushgate.app.ui

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pushgate.app.block.AdminReceiver
import com.pushgate.app.block.BlockerAccessibilityService
import com.pushgate.app.block.BlockerForegroundService
import com.pushgate.app.block.ProtectionGate
import com.pushgate.app.block.WatchdogScheduler
import com.pushgate.app.data.db.BlockedApp
import com.pushgate.app.data.db.DailyUsage
import com.pushgate.app.data.db.EventLog
import com.pushgate.app.data.db.RepSession
import com.pushgate.app.data.prefs.Settings
import com.pushgate.app.data.repo.BlockRepository
import com.pushgate.app.quota.DailyRollover
import com.pushgate.app.quota.TaperPlan
import com.pushgate.app.util.TimeKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** One installed, launchable app as shown in the picker. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean
)

/** Everything the home screen needs about one blocked app, today. */
data class AppStatus(
    val packageName: String,
    val label: String,
    val enabled: Boolean,
    val budgetMs: Long,
    val usedMs: Long,
    val earnedMs: Long,
    val opens: Int
) {
    val remainingMs: Long get() = (budgetMs - usedMs).coerceAtLeast(0L)
    val fractionUsed: Float get() = if (budgetMs <= 0L) 1f else (usedMs.toFloat() / budgetMs).coerceIn(0f, 1f)
    val exhausted: Boolean get() = remainingMs <= 0L
}

data class ProtectionState(
    val accessibilityEnabled: Boolean = false,
    val foregroundServiceRunning: Boolean = false,
    val deviceAdminActive: Boolean = false,
    val notificationsAllowed: Boolean = true
) {
    val fullyArmed: Boolean get() = accessibilityEnabled && foregroundServiceRunning
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BlockRepository.get(app)

    val settings: StateFlow<Settings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    private val _protection = MutableStateFlow(ProtectionState())
    val protection: StateFlow<ProtectionState> = _protection.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _loadingApps = MutableStateFlow(false)
    val loadingApps: StateFlow<Boolean> = _loadingApps.asStateFlow()

    val blockedApps: StateFlow<List<BlockedApp>> = repo.blockedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val todayUsage: StateFlow<List<DailyUsage>> = repo.settings
        .flatMapLatest { s -> repo.observeUsage(TimeKeys.todayKey(s.rolloverHour)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The home screen's main list: every blocked app with today's numbers attached. */
    val appStatuses: StateFlow<List<AppStatus>> =
        combine(blockedApps, todayUsage, settings) { apps, usage, s ->
            val today = TimeKeys.dateFor(System.currentTimeMillis(), s.rolloverHour)
            apps.map { app ->
                val row = usage.firstOrNull { it.packageName == app.packageName }
                AppStatus(
                    packageName = app.packageName,
                    label = app.label,
                    enabled = app.enabled,
                    budgetMs = TaperPlan.budgetMillisFor(s, today, app.customBudgetMinutes),
                    usedMs = row?.usedMs ?: 0L,
                    earnedMs = row?.earnedMs ?: 0L,
                    opens = row?.opens ?: 0
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentSessions: StateFlow<List<RepSession>> = repo.observeRecentSessions(40)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalReps: StateFlow<Int> = repo.observeTotalReps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val recentEvents: StateFlow<List<EventLog>> = repo.observeEvents(60)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Last 14 days of usage, for the stats chart. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val recentUsage: StateFlow<List<DailyUsage>> = repo.settings
        .flatMapLatest { s ->
            val from = TimeKeys.dateFor(System.currentTimeMillis(), s.rolloverHour).minusDays(13)
            repo.observeUsageSince(from.toString())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val planPreview: StateFlow<List<TaperPlan.Day>> = settings
        .map { s -> TaperPlan.preview(s, LocalDate.now(), extraDays = 1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refreshProtection()
    }

    // ---------------------------------------------------------------- protection

    fun refreshProtection() {
        val ctx = getApplication<Application>()
        _protection.value = ProtectionState(
            accessibilityEnabled = BlockerAccessibilityService.isEnabled(ctx),
            foregroundServiceRunning = BlockerForegroundService.isRunning,
            deviceAdminActive = AdminReceiver.isActive(ctx),
            notificationsAllowed = true
        )
        viewModelScope.launch {
            repo.settingsStore.setDeviceAdminActive(AdminReceiver.isActive(ctx))
        }
    }

    fun armProtection() {
        val ctx = getApplication<Application>()
        BlockerForegroundService.start(ctx)
        WatchdogScheduler.schedule(ctx)
        DailyRollover.schedule(ctx)
        refreshProtection()
    }

    // ---------------------------------------------------------------- app list

    fun loadInstalledApps() {
        if (_loadingApps.value) return
        viewModelScope.launch {
            _loadingApps.value = true
            _installedApps.value = withContext(Dispatchers.IO) { queryLaunchableApps() }
            _loadingApps.value = false
        }
    }

    private fun queryLaunchableApps(): List<InstalledApp> {
        val ctx = getApplication<Application>()
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolved = runCatching {
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())

        return resolved
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == ctx.packageName) return@mapNotNull null
                val appInfo = info.activityInfo.applicationInfo
                InstalledApp(
                    packageName = pkg,
                    label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg),
                    isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    // ---------------------------------------------------------------- mutations

    fun setBlockedApps(selected: Set<InstalledApp>) {
        viewModelScope.launch {
            repo.addBlockedApps(
                selected.map { BlockedApp(packageName = it.packageName, label = it.label) }
            )
        }
    }

    /**
     * Turning an app back ON is strengthening, so it is free. Pausing it is weakening, so it is
     * gated. Returns false when the gate refused, and the caller shows the wait dialog.
     */
    fun toggleBlockedApp(pkg: String, enabled: Boolean): Boolean {
        if (!enabled && !gateAllows()) return false
        viewModelScope.launch {
            repo.setBlockedAppEnabled(pkg, enabled)
            if (!enabled) consumeGate()
        }
        return true
    }

    fun removeBlockedApp(pkg: String): Boolean {
        if (!gateAllows()) return false
        viewModelScope.launch {
            repo.removeBlockedApp(pkg)
            consumeGate()
        }
        return true
    }

    /**
     * Raising a budget (or clearing an override back to a larger plan default) is weakening.
     * Lowering it is not, so tightening the screws never needs permission.
     */
    fun setCustomBudget(pkg: String, minutes: Int?): Boolean {
        val current = blockedApps.value.firstOrNull { it.packageName == pkg }
        val today = LocalDate.now()
        val planMinutes = TaperPlan.minutesToday(settings.value, today)
        val before = current?.customBudgetMinutes ?: planMinutes
        val after = minutes ?: planMinutes

        if (after > before && !gateAllows()) return false
        viewModelScope.launch {
            repo.setCustomBudget(pkg, minutes)
            if (after > before) consumeGate()
        }
        return true
    }

    fun gateAllows(): Boolean = ProtectionGate.isAllowed(settings.value)

    fun gateVerdict(): ProtectionGate.Verdict = ProtectionGate.check(settings.value)

    /** A served wait buys exactly one change, then has to be served again. */
    private suspend fun consumeGate() {
        if (settings.value.strictMode) repo.settingsStore.setCooldownEndsAt(0L)
    }

    fun startPlan(days: Int, startMinutes: Int, endMinutes: Int) {
        viewModelScope.launch {
            repo.settingsStore.startPlan(LocalDate.now().toEpochDay(), days, startMinutes, endMinutes)
            repo.logEvent(EventLog.PLAN_STARTED, detail = "$days days, $startMinutes -> $endMinutes min")
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            repo.settingsStore.completeOnboarding()
            armProtection()
        }
    }

    fun setStrictMode(on: Boolean): Boolean {
        if (!on && !gateAllows()) return false
        viewModelScope.launch {
            repo.settingsStore.setStrictMode(on)
            repo.settingsStore.setCooldownEndsAt(0L)
        }
        return true
    }
    fun setBaseReps(v: Int) = viewModelScope.launch { repo.settingsStore.setBaseReps(v) }
    fun setBaseMinutes(v: Int) = viewModelScope.launch { repo.settingsStore.setBaseUnlockMinutes(v) }
    fun setEscalation(v: Float) = viewModelScope.launch { repo.settingsStore.setEscalation(v) }
    fun setMaxEarnedUnlocks(v: Int) = viewModelScope.launch { repo.settingsStore.setMaxEarnedUnlocks(v) }
    fun setAllowEarned(v: Boolean) = viewModelScope.launch { repo.settingsStore.setAllowEarnedUnlocks(v) }
    fun setCooldownMinutes(v: Int) = viewModelScope.launch { repo.settingsStore.setCooldownMinutes(v) }
    fun setVibrate(v: Boolean) = viewModelScope.launch { repo.settingsStore.setVibrateOnRep(v) }
    fun setRolloverHour(v: Int) = viewModelScope.launch { repo.settingsStore.setRolloverHour(v) }

    fun setFormStrictness(preset: FormPreset) = viewModelScope.launch {
        repo.settingsStore.setFormStrictness(
            preset.downAngle,
            preset.upAngle,
            preset.maxBend,
            preset.minRepMs
        )
    }

    /** Clears a matured cooldown once the user has actually changed something. */
    fun clearCooldown() = viewModelScope.launch { repo.settingsStore.setCooldownEndsAt(0L) }

    fun startCooldown() = viewModelScope.launch {
        val s = repo.settingsStore.current()
        repo.settingsStore.setCooldownEndsAt(System.currentTimeMillis() + s.cooldownMinutes * 60_000L)
        repo.logEvent(EventLog.COOLDOWN_STARTED, detail = "${s.cooldownMinutes} min")
    }

    fun deactivateDeviceAdmin() {
        AdminReceiver.deactivate(getApplication())
        refreshProtection()
    }
}

enum class FormPreset(
    val label: String,
    val description: String,
    val downAngle: Float,
    val upAngle: Float,
    val maxBend: Float,
    val minRepMs: Int
) {
    FORGIVING(
        "Forgiving",
        "Counts most honest attempts. Good if you are starting from zero.",
        downAngle = 110f, upAngle = 145f, maxBend = 35f, minRepMs = 450
    ),
    STANDARD(
        "Standard",
        "Real range of motion, straight body, no bouncing.",
        downAngle = 95f, upAngle = 155f, maxBend = 25f, minRepMs = 600
    ),
    STRICT(
        "Strict",
        "Chest to the floor, full lockout, plank held throughout.",
        downAngle = 80f, upAngle = 165f, maxBend = 15f, minRepMs = 800
    )
}
