package com.pushgate.app.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pushgate.app.block.AdminReceiver
import com.pushgate.app.block.GuardBypass
import com.pushgate.app.quota.TaperPlan
import com.pushgate.app.ui.AppStatus
import com.pushgate.app.ui.MainViewModel
import com.pushgate.app.ui.common.AppIcon
import com.pushgate.app.ui.common.rememberAdminEnabler
import com.pushgate.app.ui.common.MeterBar
import com.pushgate.app.ui.common.SectionCard
import com.pushgate.app.ui.theme.Amber
import com.pushgate.app.ui.theme.Chalk
import com.pushgate.app.ui.theme.Crimson
import com.pushgate.app.ui.theme.Emerald
import com.pushgate.app.ui.theme.Mist
import com.pushgate.app.util.TimeKeys
import java.time.LocalDate

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val statuses by viewModel.appStatuses.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val protection by viewModel.protection.collectAsStateWithLifecycle()
    val totalReps by viewModel.totalReps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val enableAdmin = rememberAdminEnabler(viewModel)

    val today = LocalDate.now()
    val dayIndex = TaperPlan.dayIndex(settings, today)
    val budgetToday = TaperPlan.minutesToday(settings, today)
    val active = statuses.filter { it.enabled }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // -------------------------------------------------- headline
        item {
            Column {
                Text(
                    "Day ${(dayIndex + 1).coerceAtLeast(1)}",
                    color = Emerald,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$budgetToday",
                        color = Chalk,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        " minutes each",
                        color = Mist,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 9.dp)
                    )
                }
                Text(
                    tomorrowLine(settings, today),
                    color = Mist,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // -------------------------------------------------- the two things that can go wrong
        if (!protection.accessibilityEnabled) {
            item {
                AlertCard(
                    tone = Crimson,
                    title = "Nothing is being blocked",
                    body = "PushGate's accessibility service is off. Your apps are wide open. " +
                        "Android only lets you turn this back on yourself.",
                    cta = "Turn it back on"
                ) {
                    GuardBypass.open(3, "enabling accessibility service")
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        }

        if (!protection.deviceAdminActive) {
            item {
                AlertCard(
                    tone = Amber,
                    title = "This app can still be uninstalled",
                    body = "Long-press the PushGate icon, tap uninstall, and everything is gone in " +
                        "two seconds — no wait, no push-ups. Turning on uninstall protection " +
                        "closes that door.",
                    cta = "Close that door"
                ) { enableAdmin() }
            }
        }

        // -------------------------------------------------- today at a glance
        if (active.isNotEmpty()) {
            item {
                SectionCard {
                    Row(Modifier.fillMaxWidth()) {
                        Stat(
                            TimeKeys.formatDuration(active.sumOf { it.remainingMs }),
                            "left today",
                            Modifier.weight(1f),
                            Emerald
                        )
                        Stat(
                            "${active.count { it.exhausted }}",
                            "already shut",
                            Modifier.weight(1f),
                            if (active.any { it.exhausted }) Crimson else Mist
                        )
                        Stat("$totalReps", "push-ups paid", Modifier.weight(1f), Chalk)
                    }
                }
            }
        }

        if (statuses.isEmpty()) {
            item {
                SectionCard {
                    Text(
                        "No apps blocked yet",
                        color = Chalk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Open the Apps tab and pick the one you reach for without deciding to.",
                        color = Mist,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        items(statuses, key = { it.packageName }) { status -> AppRow(status) }
    }
}

@Composable
private fun AppRow(status: AppStatus) {
    val tone = when {
        !status.enabled -> Mist
        status.exhausted -> Crimson
        status.fractionUsed > 0.75f -> Amber
        else -> Emerald
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(status.packageName, status.label, size = 44)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    status.label,
                    color = Chalk,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (status.exhausted && status.enabled) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Crimson,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        when {
                            !status.enabled -> "Paused"
                            status.exhausted -> "Shut — push-ups only"
                            else -> "${TimeKeys.formatDuration(status.remainingMs)} left"
                        },
                        color = tone,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        MeterBar(fraction = status.fractionUsed, color = tone)

        if (status.opens > 0 || status.earnedMs > 0L) {
            Spacer(Modifier.height(9.dp))
            Text(
                buildString {
                    append("Opened ${status.opens}×")
                    if (status.earnedMs > 0L) {
                        append(" · ${TimeKeys.formatDuration(status.earnedMs)} bought back")
                    }
                },
                color = Mist,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun AlertCard(
    tone: Color,
    title: String,
    body: String,
    cta: String,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(tone.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = tone, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, color = tone, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(body, color = Mist, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Text(cta + "  →", color = tone, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun Stat(value: String, caption: String, modifier: Modifier, color: Color) {
    Column(modifier) {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(2.dp))
        Text(caption, color = Mist, fontSize = 12.sp)
    }
}

private fun tomorrowLine(
    settings: com.pushgate.app.data.prefs.Settings,
    today: LocalDate
): String {
    val todayMinutes = TaperPlan.minutesToday(settings, today)
    val tomorrowMinutes =
        TaperPlan.minutesForDayIndex(settings, TaperPlan.dayIndex(settings, today) + 1)
    return when {
        settings.planStartEpochDay < 0L -> "No plan running yet — set one up in Settings."
        tomorrowMinutes < todayMinutes -> "Tomorrow it drops to $tomorrowMinutes."
        else -> "You are at your steady level. Hold it here."
    }
}
