package com.pushgate.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pushgate.app.data.db.EventLog
import com.pushgate.app.data.db.RepSession
import com.pushgate.app.ui.MainViewModel
import com.pushgate.app.ui.common.AppIcon
import com.pushgate.app.ui.common.KeyValueRow
import com.pushgate.app.ui.common.SectionCard
import com.pushgate.app.ui.common.SectionLabel
import com.pushgate.app.ui.common.StatTile
import com.pushgate.app.ui.theme.Amber
import com.pushgate.app.ui.theme.Chalk
import com.pushgate.app.ui.theme.Crimson
import com.pushgate.app.ui.theme.Emerald
import com.pushgate.app.ui.theme.Mist
import com.pushgate.app.util.TimeKeys
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StatsScreen(viewModel: MainViewModel) {
    val sessions by viewModel.recentSessions.collectAsStateWithLifecycle()
    val totalReps by viewModel.totalReps.collectAsStateWithLifecycle()
    val usage by viewModel.recentUsage.collectAsStateWithLifecycle()
    val events by viewModel.recentEvents.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val completed = sessions.count { it.completed }
    val abandoned = sessions.count { !it.completed }
    val totalRejected = sessions.sumOf { it.rejectedReps }
    val avgForm = sessions.filter { it.completed }.map { it.avgFormScore }
        .let { if (it.isEmpty()) 0f else it.sum() / it.size }

    val byDay = remember(usage) {
        usage.groupBy { it.dateKey }
            .mapValues { (_, rows) -> rows.sumOf { it.usedMs } }
            .toSortedMap()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Stats", color = Chalk, style = MaterialTheme.typography.displaySmall)
        }

        item {
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatTile("$totalReps", "push-ups total", Modifier.weight(1f))
                    StatTile("$completed", "sets paid", Modifier.weight(1f))
                    StatTile("$abandoned", "walked away", Modifier.weight(1f), valueColor = Amber)
                }
                Spacer(Modifier.height(14.dp))
                KeyValueRow("Reps rejected for bad form", "$totalRejected", Crimson)
                KeyValueRow(
                    "Average form score",
                    if (avgForm <= 0f) "—" else "${avgForm.roundToInt()} / 100",
                    Emerald
                )
                KeyValueRow(
                    "Walking away instead of paying",
                    if (sessions.isEmpty()) "—"
                    else "${(abandoned * 100f / sessions.size).roundToInt()}% of the time",
                    Emerald
                )
            }
        }

        item {
            SectionCard {
                SectionLabel("Last 14 days")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Time spent inside blocked apps against that day's budget.",
                    color = Mist,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                UsageBars(
                    byDay = byDay,
                    budgetMinutes = settings.startMinutesPerDay.coerceAtLeast(1)
                )
            }
        }

        item { SectionLabel("Recent sets") }

        if (sessions.isEmpty()) {
            item {
                SectionCard {
                    Text(
                        "No push-ups yet. That is either great discipline or a brand-new install.",
                        color = Mist,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        items(sessions, key = { it.id }) { session -> SessionRow(session) }

        item {
            Spacer(Modifier.height(4.dp))
            SectionLabel("Activity log")
        }

        items(events.take(25), key = { it.id }) { event -> EventRow(event) }
    }
}

@Composable
private fun UsageBars(byDay: Map<String, Long>, budgetMinutes: Int) {
    val entries = remember(byDay) {
        val today = LocalDate.now()
        (13 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            date to (byDay[date.toString()] ?: 0L)
        }
    }
    val maxMs = maxOf(entries.maxOfOrNull { it.second } ?: 0L, budgetMinutes * 60_000L)

    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val count = entries.size
        val gap = size.width * 0.02f
        val barWidth = (size.width - gap * (count - 1)) / count

        entries.forEachIndexed { index, (_, ms) ->
            val fraction = if (maxMs <= 0L) 0f else (ms.toFloat() / maxMs)
            val barHeight = (size.height * fraction).coerceAtLeast(if (ms > 0) 3f else 2f)
            val x = index * (barWidth + gap)

            drawRoundRect(
                color = Color.White.copy(alpha = 0.06f),
                topLeft = Offset(x, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = CornerRadius(barWidth / 3f)
            )
            drawRoundRect(
                color = when {
                    ms == 0L -> Color.White.copy(alpha = 0.12f)
                    fraction > 0.95f -> Crimson
                    fraction > 0.7f -> Amber
                    else -> Emerald
                },
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 3f)
            )
        }
    }

    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            entries.first().first.format(DateTimeFormatter.ofPattern("d MMM", Locale.US)),
            color = Mist,
            fontSize = 11.sp
        )
        Text("today", color = Mist, fontSize = 11.sp)
    }
}

@Composable
private fun SessionRow(session: RepSession) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(session.packageName, session.packageName, size = 34)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${session.repsCompleted} / ${session.repsRequired} reps",
                    color = if (session.completed) Emerald else Amber,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    buildString {
                        append(relativeTime(session.startedAt))
                        if (session.completed) {
                            append(" · earned ${session.secondsEarned / 60} min")
                        } else {
                            append(" · gave up")
                        }
                        if (session.rejectedReps > 0) append(" · ${session.rejectedReps} rejected")
                    },
                    color = Mist,
                    fontSize = 12.sp
                )
            }
            Text(
                TimeKeys.formatDuration(session.endedAt - session.startedAt),
                color = Mist,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun EventRow(event: EventLog) {
    val (label, color) = when (event.type) {
        EventLog.BLOCKED -> "Blocked" to Crimson
        EventLog.EARNED_UNLOCK -> "Earned unlock" to Emerald
        EventLog.QUOTA_EXHAUSTED -> "Budget spent" to Amber
        EventLog.TAMPER_SETTINGS -> "Tried to change settings" to Crimson
        EventLog.TAMPER_UNINSTALL -> "Tried to uninstall" to Crimson
        EventLog.CHALLENGE_ABANDONED -> "Walked away" to Emerald
        EventLog.SERVICE_DOWN -> "Protection stopped" to Crimson
        EventLog.SERVICE_UP -> "Protection started" to Emerald
        EventLog.COOLDOWN_STARTED -> "Cooldown started" to Amber
        EventLog.PLAN_STARTED -> "Plan started" to Emerald
        else -> event.type to Mist
    }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.width(8.dp).height(8.dp)) { drawCircle(color) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Chalk, fontSize = 13.sp)
            if (!event.detail.isNullOrBlank() || event.packageName != null) {
                Text(
                    listOfNotNull(event.packageName?.substringAfterLast('.'), event.detail)
                        .joinToString(" · "),
                    color = Mist,
                    fontSize = 11.sp
                )
            }
        }
        Text(relativeTime(event.timestamp), color = Mist, fontSize = 11.sp)
    }
}

private fun relativeTime(millis: Long): String {
    val delta = System.currentTimeMillis() - millis
    val minutes = delta / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> "${days / 7}w ago"
    }
}
