package com.pushgate.app

import android.app.Application
import android.util.Log
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
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

/**
 * Implements [CameraXConfig.Provider] deliberately.
 *
 * Left to itself, CameraX finds its default configuration by *reflecting* on
 * `androidx.camera.camera2.Camera2Config`. R8 renames that class in a minified release build, the
 * reflection misses, and `ProcessCameraProvider.getInstance()` fails with nothing but
 * "CameraX failed to initialize" — which is what made the push-up challenge report that the camera
 * was unavailable on a phone whose camera was perfectly fine.
 *
 * Declaring the config here removes the reflection from the path entirely, so release builds
 * behave exactly like debug ones.
 */
class PushGateApp : Application(), CameraXConfig.Provider {

    override fun getCameraXConfig(): CameraXConfig =
        CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
            .setMinimumLoggingLevel(Log.ERROR)
            .build()

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
