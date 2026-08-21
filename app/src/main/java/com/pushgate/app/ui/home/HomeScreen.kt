package com.pushgate.app.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pushgate.app.block.GuardBypass
import com.pushgate.app.quota.TaperPlan
import com.pushgate.app.ui.AppStatus
import com.pushgate.app.ui.MainViewModel
import com.pushgate.app.ui.common.AppIcon
import com.pushgate.app.ui.common.MeterBar
import com.pushgate.app.ui.common.SectionCard
import com.pushgate.app.ui.common.SectionLabel
import com.pushgate.app.ui.common.StatTile
import com.pushgate.app.ui.theme.Amber
import com.pushgate.app.ui.theme.Chalk
import com.pushgate.app.ui.theme.Crimson
import com.pushgate.app.ui.theme.Emerald
import com.pushgate.app.ui.theme.Ink
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

    val today = LocalDate.now()
    val dayIndex = TaperPlan.dayIndex(settings, today)
    val budgetToday = TaperPlan.minutesToday(settings, today)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp, end = 18.dp, top = 22.dp, bottom = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        item {
            Column {
                Text(
                    "Day ${(dayIndex + 1).coerceAtLeast(1)}",
                    color = Emerald,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    "$budgetToday minutes each",
                    color = Chalk,
                    style = MaterialTheme.typography.displaySmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tomorrowLine(settings, today),
                    color = Mist,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (!protection.accessibilityEnabled) {
            item {
                SectionCard(accent = Crimson) {
                    SectionLabel("Not protecting you", Crimson)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The accessibility service is off, so nothing is being blocked. " +
                            "Android only lets you turn this on yourself.",
                        color = Chalk,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            GuardBypass.open(3, "enabling accessibility service")
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Crimson, contentColor = Ink),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Turn it on", fontWeight = FontWeight.Bold) }
                }
            }
        }

        item {
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatTile(
                        value = "$totalReps",
                        caption = "push-ups paid",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = TimeKeys.formatDuration(statuses.sumOf { it.usedMs }),
                        caption = "used today",
                        modifier = Modifier.weight(1f),
                        valueColor = Amber
                    )
                    StatTile(
                        value = "${statuses.count { it.exhausted && it.enabled }}",
                        caption = "gates shut",
                        modifier = Modifier.weight(1f),
                        valueColor = Crimson
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(2.dp))
            SectionLabel("Your blocked apps")
        }

        if (statuses.isEmpty()) {
            item {
                SectionCard {
                    Text(
                        "Nothing is blocked yet. Head to the Apps tab and pick the ones that eat your day.",
                        color = Mist,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        items(statuses, key = { it.packageName }) { status ->
            AppStatusCard(status)
        }
    }
}

@Composable
private fun AppStatusCard(status: AppStatus) {
    val barColor = when {
        !status.enabled -> Mist
        status.exhausted -> Crimson
        status.fractionUsed > 0.75f -> Amber
        else -> Emerald
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(status.packageName, status.label)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    status.label,
                    color = Chalk,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    when {
                        !status.enabled -> "Paused"
                        status.exhausted -> "Out of budget — push-ups only"
                        else -> "${TimeKeys.formatDuration(status.remainingMs)} left"
                    },
                    color = barColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                "${(status.fractionUsed * 100).toInt()}%",
                color = barColor,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        Spacer(Modifier.height(12.dp))
        MeterBar(fraction = status.fractionUsed, color = barColor)

        if (status.earnedMs > 0L || status.opens > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                buildString {
                    append("${status.opens} open${if (status.opens == 1) "" else "s"}")
                    if (status.earnedMs > 0L) {
                        append(" · ${TimeKeys.formatDuration(status.earnedMs)} earned back")
                    }
                },
                color = Mist,
                fontSize = 12.sp
            )
        }
    }
}

private fun tomorrowLine(
    settings: com.pushgate.app.data.prefs.Settings,
    today: LocalDate
): String {
    val todayMinutes = TaperPlan.minutesToday(settings, today)
    val tomorrowMinutes = TaperPlan.minutesForDayIndex(
        settings,
        TaperPlan.dayIndex(settings, today) + 1
    )
    return when {
        tomorrowMinutes < todayMinutes ->
            "Tomorrow it drops to $tomorrowMinutes minutes."
        settings.planStartEpochDay < 0L ->
            "No taper running yet — start one in Settings."
        else ->
            "You are at your maintenance level. Hold it here."
    }
}
