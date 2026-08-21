package com.pushgate.app

import android.app.Application
import com.pushgate.app.block.BlockerAccessibilityService
import com.pushgate.app.block.BlockerForegroundService
import com.pushgate.app.block.Notifications
import com.pushgate.app.block.WatchdogScheduler
import com.pushgate.app.data.repo.BlockRepository
import com.pushgate.app.quota.DailyRollover
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PushGateApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val repo = BlockRepository.get(this@PushGateApp)
            val settings = repo.settingsStore.current()
            if (!settings.onboardingComplete) return@launch

            // Anything that survived a reboot or an update gets re-armed here, so protection is
            // never one crash away from silently ending.
            BlockerForegroundService.start(this@PushGateApp)
            WatchdogScheduler.schedule(this@PushGateApp)
            DailyRollover.schedule(this@PushGateApp)

            if (!BlockerAccessibilityService.isEnabled(this@PushGateApp) && settings.strictMode) {
                Notifications.warnServiceDown(this@PushGateApp)
            }
        }
    }
}
