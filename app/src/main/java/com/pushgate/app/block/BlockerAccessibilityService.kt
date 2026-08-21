package com.pushgate.app.block

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.pushgate.app.data.db.EventLog
import com.pushgate.app.data.prefs.Settings
import com.pushgate.app.data.repo.BlockDecision
import com.pushgate.app.data.repo.BlockRepository
import com.pushgate.app.util.TimeKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The engine room.
 *
 * Responsibilities, in priority order:
 *  1. Know which app is in front, to the nearest window transition.
 *  2. Count that app's time against today's budget and interrupt the moment it runs out.
 *  3. While Strict Mode is armed, stop the user from walking into Settings and switching all of
 *     this off on an impulse — which is exactly when they would want to.
 *
 * Deliberately does not read screen *content* for anything except the Strict Mode guard, and even
 * there it only looks for its own name.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var repo: BlockRepository

    /** The blocked app currently being counted down, if any. */
    private var trackedPkg: String? = null
    private var trackedChargesQuota = true
    private var remainingMs = 0L
    private var lastTickUptime = 0L
    private var unflushedMs = 0L
    private var warned = false

    /** Last real app we saw in front, tracked or not. Used to detect a failed block-screen launch. */
    @Volatile private var lastSeenPkg: String? = null

    private var settingsSnapshot: Settings = Settings()
    private var imePackage: String? = null
    private var lastGuardActionAt = 0L
    private var lastBlockLaunchAt = 0L

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            if (trackedPkg != null) main.postDelayed(this, TICK_MS)
        }
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onServiceConnected() {
        super.onServiceConnected()
        repo = BlockRepository.get(this)
        isConnected = true

        Notifications.ensureChannels(this)
        Notifications.clearServiceDown(this)

        imePackage = runCatching {
            AndroidSettings.Secure.getString(contentResolver, AndroidSettings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')
        }.getOrNull()

        scope.launch {
            repo.settingsStore.flow.collect { settingsSnapshot = it }
        }
        scope.launch {
            repo.logEvent(EventLog.SERVICE_UP)
            repo.prune()
        }
        WatchdogScheduler.schedule(this)
        updateIdleNotification()
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::repo.isInitialized) return

        val pkg = event.packageName?.toString() ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (isSensitiveSurface(pkg)) {
                    guardProtectedSettings(pkg)
                    return
                }
                if (shouldIgnore(pkg)) return
                lastSeenPkg = pkg
                onForegroundPackage(pkg)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Settings screens redraw without a fresh window event when you drill in, so the
                // guard has to watch content changes too. Everything else is ignored here.
                if (isSensitiveSurface(pkg)) guardProtectedSettings(pkg)
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        handleShutdown("unbind")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handleShutdown("destroy")
        scope.cancel()
        super.onDestroy()
    }

    private fun handleShutdown(reason: String) {
        if (!isConnected) return
        isConnected = false
        stopTracking(flush = true)
        val strict = settingsSnapshot.strictMode
        val appContext = applicationContext
        if (::repo.isInitialized) {
            CoroutineScope(Dispatchers.IO).launch {
                repo.logEvent(EventLog.SERVICE_DOWN, detail = reason)
            }
        }
        if (strict && !cooldownMatured(settingsSnapshot)) {
            Notifications.warnServiceDown(appContext)
        }
        // The high-priority alert above already says this; do not also raise a foreground
        // service from a service that is being torn down.
        BlockerForegroundService.stop(appContext)
    }

    // ------------------------------------------------------------------ foreground handling

    private fun shouldIgnore(pkg: String): Boolean =
        pkg == packageName ||
            pkg == "com.android.systemui" ||
            pkg == imePackage ||
            pkg.endsWith(".inputmethod") ||
            pkg == "android"

    private fun onForegroundPackage(pkg: String) {
        if (pkg == trackedPkg) return

        stopTracking(flush = true)

        scope.launch {
            val decision = repo.decide(pkg)
            withContext(Dispatchers.Main) { applyDecision(pkg, decision) }
        }
    }

    private fun applyDecision(pkg: String, decision: BlockDecision) {
        when (decision) {
            is BlockDecision.NotTracked -> updateIdleNotification()

            is BlockDecision.AllowedByQuota -> {
                startTracking(pkg, decision.remainingMs, chargesQuota = true)
                scope.launch { repo.recordOpen(pkg) }
            }

            is BlockDecision.AllowedByGrant -> {
                startTracking(pkg, decision.remainingMs, chargesQuota = false)
            }

            is BlockDecision.Blocked -> {
                scope.launch {
                    repo.logEvent(EventLog.BLOCKED, pkg, "budget spent")
                }
                showBlockScreen(decision)
            }
        }
    }

    private fun startTracking(pkg: String, remaining: Long, chargesQuota: Boolean) {
        trackedPkg = pkg
        trackedChargesQuota = chargesQuota
        remainingMs = remaining
        lastTickUptime = SystemClock.elapsedRealtime()
        unflushedMs = 0L
        warned = false

        main.removeCallbacks(ticker)
        main.postDelayed(ticker, TICK_MS)
        updateCountdownNotification()
    }

    private fun stopTracking(flush: Boolean) {
        val pkg = trackedPkg ?: return
        main.removeCallbacks(ticker)

        if (flush) {
            val now = SystemClock.elapsedRealtime()
            val delta = (now - lastTickUptime).coerceAtLeast(0L)
            unflushedMs += delta
            flushUsage(pkg, unflushedMs, trackedChargesQuota)
        }

        trackedPkg = null
        unflushedMs = 0L
        warned = false
    }

    private fun tick() {
        val pkg = trackedPkg ?: return
        val now = SystemClock.elapsedRealtime()
        val delta = (now - lastTickUptime).coerceAtLeast(0L)
        lastTickUptime = now

        remainingMs -= delta
        unflushedMs += delta

        if (unflushedMs >= FLUSH_EVERY_MS) {
            flushUsage(pkg, unflushedMs, trackedChargesQuota)
            unflushedMs = 0L
        }

        val warnAt = settingsSnapshot.quotaWarningSeconds * 1000L
        if (!warned && warnAt > 0 && remainingMs in 1..warnAt) {
            warned = true
            Toast.makeText(
                this,
                "${TimeKeys.formatClock(remainingMs)} left here",
                Toast.LENGTH_SHORT
            ).show()
        }

        if (remainingMs <= 0L) {
            flushUsage(pkg, unflushedMs, trackedChargesQuota)
            unflushedMs = 0L
            main.removeCallbacks(ticker)
            trackedPkg = null

            scope.launch {
                repo.logEvent(EventLog.QUOTA_EXHAUSTED, pkg)
                val decision = repo.decide(pkg)
                withContext(Dispatchers.Main) {
                    if (decision is BlockDecision.Blocked) {
                        showBlockScreen(decision)
                    } else {
                        applyDecision(pkg, decision)
                    }
                }
            }
        } else {
            updateCountdownNotification()
        }
    }

    private fun flushUsage(pkg: String, ms: Long, chargesQuota: Boolean) {
        if (ms <= 0L) return
        scope.launch {
            if (chargesQuota) repo.recordUsage(pkg, ms) else repo.recordEarnedUsage(pkg, ms)
        }
    }

    // ------------------------------------------------------------------ interception

    private fun showBlockScreen(decision: BlockDecision.Blocked) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockLaunchAt < BLOCK_DEBOUNCE_MS) return
        lastBlockLaunchAt = now

        val intent = BlockScreenActivity.blockIntent(this, decision)
        runCatching { startActivity(intent) }
            .onFailure { Log.w(TAG, "Block screen launch failed", it) }

        // Some OEM skins swallow a background activity start while their own app is resuming.
        // If the blocked app is still in front a moment later, fall back to HOME and retry.
        main.postDelayed({
            if (lastSeenPkg == decision.packageName) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                main.postDelayed({
                    runCatching { startActivity(BlockScreenActivity.blockIntent(this, decision)) }
                }, 150L)
            }
        }, 700L)
    }

    // ------------------------------------------------------------------ strict mode guard

    private fun isSensitiveSurface(pkg: String): Boolean =
        pkg in SETTINGS_PACKAGES || pkg in INSTALLER_PACKAGES ||
            pkg.endsWith(".settings") || pkg.endsWith(".packageinstaller")

    /**
     * While Strict Mode is armed and no cooldown has matured, block the three doors out:
     * the accessibility toggle, the device-admin list, and this app's own App Info page.
     *
     * The check is narrow on purpose: it only fires when PushGate's own name is on screen, so
     * the rest of Settings stays usable.
     */
    private fun guardProtectedSettings(pkg: String) {
        val settings = settingsSnapshot
        if (!settings.strictMode) return
        if (cooldownMatured(settings)) return
        // PushGate sends the user into Settings for its own setup; do not fight its own flow.
        if (GuardBypass.isOpen) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastGuardActionAt < GUARD_DEBOUNCE_MS) return

        val root = rootInActiveWindow ?: return
        val hit = try {
            containsOwnIdentity(root)
        } catch (t: Throwable) {
            false
        }
        if (!hit) return

        lastGuardActionAt = now
        val isInstaller = pkg in INSTALLER_PACKAGES || pkg.endsWith(".packageinstaller")

        performGlobalAction(GLOBAL_ACTION_HOME)
        main.postDelayed({
            runCatching {
                startActivity(BlockScreenActivity.tamperIntent(this, settings.cooldownMinutes))
            }
        }, 120L)

        scope.launch {
            repo.logEvent(
                if (isInstaller) EventLog.TAMPER_UNINSTALL else EventLog.TAMPER_SETTINGS,
                pkg
            )
        }
    }

    private fun containsOwnIdentity(root: AccessibilityNodeInfo): Boolean {
        for (needle in IDENTITY_STRINGS) {
            val matches = root.findAccessibilityNodeInfosByText(needle)
            if (!matches.isNullOrEmpty()) return true
        }
        return false
    }

    private fun cooldownMatured(settings: Settings): Boolean {
        val ends = settings.cooldownEndsAt
        return ends > 0L && System.currentTimeMillis() >= ends
    }

    // ------------------------------------------------------------------ notification

    /**
      * The permanent notification was the single most-complained-about thing in testing, and it
      * bought very little: the accessibility service is a bound system service and survives on its
      * own. The foreground service now only runs while a blocked app is actually being counted
      * down, where the notification is genuinely useful because it shows the remaining time.
      */
    private fun updateIdleNotification() {
        if (settingsSnapshot.alwaysShowGuardNotification) {
            BlockerForegroundService.updateStatus(
                this,
                "PushGate is on guard",
                "Watching your blocked apps."
            )
        } else {
            BlockerForegroundService.stop(this)
        }
    }

    private fun updateCountdownNotification() {
        val pkg = trackedPkg ?: return
        val label = AppLabels.labelFor(this, pkg)
        val source = if (trackedChargesQuota) "quota" else "earned time"
        BlockerForegroundService.updateStatus(
            this,
            "$label — ${TimeKeys.formatClock(remainingMs)} left",
            "Running on your $source. PushGate will step in when it hits zero."
        )
    }

    companion object {
        private const val TAG = "PushGate/A11y"
        private const val TICK_MS = 1_000L
        private const val FLUSH_EVERY_MS = 5_000L
        private const val GUARD_DEBOUNCE_MS = 2_500L
        private const val BLOCK_DEBOUNCE_MS = 900L

        @Volatile
        var isConnected: Boolean = false
            private set

        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.samsung.android.settings",
            "com.miui.securitycenter",
            "com.oneplus.settings",
            "com.oppo.settings",
            "com.coloros.safecenter",
            "com.huawei.systemmanager"
        )

        private val INSTALLER_PACKAGES = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.samsung.android.packageinstaller"
        )

        /** Strings that only appear on a screen that is about to switch PushGate off. */
        private val IDENTITY_STRINGS = listOf(
            "PushGate",
            "PushGate app blocking",
            "PushGate protection"
        )

        /**
         * Whether the OS currently has our service enabled. Read from Secure settings rather than
         * trusting [isConnected], because a killed process reports a stale flag.
         */
        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${BlockerAccessibilityService::class.java.name}"
            val enabled = runCatching {
                AndroidSettings.Secure.getString(
                    context.contentResolver,
                    AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            }.getOrNull() ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
