package com.pushgate.app.block

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.pushgate.app.R

/**
 * Registering as a device admin is what makes PushGate uninstall-proof: Android refuses to
 * uninstall an active admin, and the only way out is Settings, which the Strict Mode guard covers.
 *
 * PushGate asks for the narrowest policy set that still earns the uninstall protection. It does
 * not wipe, lock, or read anything.
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        BlockerForegroundService.start(context.applicationContext)
        WatchdogScheduler.schedule(context.applicationContext)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Turning this off makes PushGate uninstallable again. If you are three seconds into an " +
            "urge, that is exactly what it will feel like a good idea to do."

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Notifications.ensureChannels(context)
    }

    companion object {

        fun component(context: Context) = ComponentName(context.applicationContext, AdminReceiver::class.java)

        fun dpm(context: Context): DevicePolicyManager =
            context.getSystemService(DevicePolicyManager::class.java)

        fun isActive(context: Context): Boolean =
            runCatching { dpm(context).isAdminActive(component(context)) }.getOrDefault(false)

        /** Intent that opens the system's "activate device admin?" confirmation. */
        fun enableIntent(context: Context): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    context.getString(R.string.device_admin_description)
                )

        /** Programmatic removal, used once a disable cooldown has been served. */
        fun deactivate(context: Context) {
            runCatching { dpm(context).removeActiveAdmin(component(context)) }
        }
    }
}
