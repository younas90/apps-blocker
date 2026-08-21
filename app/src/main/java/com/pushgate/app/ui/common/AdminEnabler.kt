package com.pushgate.app.ui.common

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.pushgate.app.block.AdminReceiver
import com.pushgate.app.block.GuardBypass
import com.pushgate.app.ui.MainViewModel

/**
 * One place that knows how to turn uninstall protection on.
 *
 * The first attempt fired the intent with `startActivity` on whatever Context Compose handed
 * over. That is unreliable — it needs an Activity, and when the flag dance failed the tap simply
 * did nothing, which is exactly what testing reported. A result launcher is bound to the hosting
 * Activity, so it always starts, and the result tells us whether it actually took.
 */
@Composable
fun rememberAdminEnabler(viewModel: MainViewModel): () -> Unit {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshProtection()
        GuardBypass.close()
        val active = AdminReceiver.isActive(context)
        Toast.makeText(
            context,
            if (active) {
                "Uninstall protection is on. PushGate cannot be removed now."
            } else {
                "Not enabled — PushGate can still be uninstalled."
            },
            Toast.LENGTH_LONG
        ).show()
    }

    val fallback = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshProtection()
        GuardBypass.close()
    }

    return {
        // Activating device admin means walking through Settings, which the Strict Mode guard
        // would otherwise slam shut. Declare the detour before leaving.
        GuardBypass.open(minutes = 5, why = "activating uninstall protection")
        if (AdminReceiver.canRequestAdmin(context)) {
            runCatching { launcher.launch(AdminReceiver.enableIntent(context)) }
                .onFailure {
                    Toast.makeText(
                        context,
                        "This device would not open the device-admin screen.",
                        Toast.LENGTH_LONG
                    ).show()
                }
        } else {
            Toast.makeText(
                context,
                "Open Security, then Device admin apps, and enable PushGate.",
                Toast.LENGTH_LONG
            ).show()
            runCatching {
                fallback.launch(AdminReceiver.securitySettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
