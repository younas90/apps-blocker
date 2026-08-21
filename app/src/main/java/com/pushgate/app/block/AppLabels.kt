package com.pushgate.app.block

import android.content.Context
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Package name to human label, memoised. The accessibility service asks for this on every
 * notification refresh, and PackageManager lookups are far too slow to do once a second.
 */
object AppLabels {

    private val cache = ConcurrentHashMap<String, String>()

    fun labelFor(context: Context, pkg: String): String = cache.getOrPut(pkg) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() })
    }

    fun iconOrNull(context: Context, pkg: String) = runCatching {
        context.packageManager.getApplicationIcon(pkg)
    }.getOrNull()

    fun isInstalled(context: Context, pkg: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(pkg, 0)
        true
    }.getOrDefault(false)

    fun launchIntentFor(context: Context, pkg: String) = runCatching {
        context.packageManager.getLaunchIntentForPackage(pkg)
    }.getOrNull()

    fun clear() = cache.clear()

    @Suppress("unused")
    fun preload(context: Context, packages: Collection<String>, pm: PackageManager = context.packageManager) {
        packages.forEach { pkg ->
            cache.getOrPut(pkg) {
                runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                    .getOrDefault(pkg)
            }
        }
    }
}
