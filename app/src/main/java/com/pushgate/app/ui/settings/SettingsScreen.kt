package com.pushgate.app.ui.settings

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.pushgate.app.block.AdminReceiver
import com.pushgate.app.block.GuardBypass
import com.pushgate.app.quota.TaperPlan
import com.pushgate.app.ui.FormPreset
import com.pushgate.app.ui.MainViewModel
import com.pushgate.app.ui.common.KeyValueRow
import com.pushgate.app.ui.common.SectionCard
import com.pushgate.app.ui.common.SectionLabel
import com.pushgate.app.ui.theme.Amber
import com.pushgate.app.ui.theme.Chalk
import com.pushgate.app.ui.theme.Crimson
import com.pushgate.app.ui.theme.Emerald
import com.pushgate.app.ui.theme.Ink
import com.pushgate.app.ui.theme.InkCard
import com.pushgate.app.ui.theme.Mist
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val protection by viewModel.protection.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val cooldownPending = settings.cooldownEndsAt > System.currentTimeMillis()
    val cooldownMatured = settings.cooldownEndsAt in 1..System.currentTimeMillis()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("Settings", color = Chalk, style = MaterialTheme.typography.displaySmall) }

        // ------------------------------------------------------------ protection
        item {
            SectionCard(accent = if (protection.fullyArmed) Emerald else Crimson) {
                SectionLabel(
                    if (protection.fullyArmed) "Armed" else "Not fully armed",
                    if (protection.fullyArmed) Emerald else Crimson
                )
                Spacer(Modifier.height(12.dp))
                KeyValueRow(
                    "Accessibility service",
                    if (protection.accessibilityEnabled) "On" else "Off",
                    if (protection.accessibilityEnabled) Emerald else Crimson
                )
                KeyValueRow(
                    "Background guard",
                    if (protection.foregroundServiceRunning) "Running" else "Stopped",
                    if (protection.foregroundServiceRunning) Emerald else Amber
                )
                KeyValueRow(
                    "Uninstall protection",
                    if (protection.deviceAdminActive) "On" else "Off",
                    if (protection.deviceAdminActive) Emerald else Amber
                )

                Spacer(Modifier.height(14.dp))

                if (!protection.accessibilityEnabled) {
                    Button(
                        onClick = {
                            GuardBypass.open(3, "enabling accessibility service")
                            runCatching {
                                context.startActivity(
                                    Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Ink),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Enable accessibility service", fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(8.dp))
                }

                if (!protection.deviceAdminActive) {
                    OutlinedButton(
                        onClick = {
                            GuardBypass.open(3, "activating uninstall protection")
                            runCatching {
                                context.startActivity(
                                    AdminReceiver.enableIntent(context)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Turn on uninstall protection", color = Emerald) }
                } else if (cooldownMatured) {
                    OutlinedButton(
                        onClick = {
                            viewModel.deactivateDeviceAdmin()
                            viewModel.clearCooldown()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Remove uninstall protection", color = Crimson) }
                }
            }
        }

        // ------------------------------------------------------------ strict mode
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Strict Mode", color = Chalk, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Blocks the Settings screens that would switch PushGate off. " +
                                "You can still get there — it just costs a wait.",
                            color = Mist,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(
                        checked = settings.strictMode,
                        enabled = !settings.strictMode || cooldownMatured,
                        onCheckedChange = { on ->
                            viewModel.setStrictMode(on)
                            if (!on) viewModel.clearCooldown()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Ink,
                            checkedTrackColor = Emerald,
                            uncheckedThumbColor = Mist,
                            uncheckedTrackColor = InkCard
                        )
                    )
                }

                if (settings.strictMode) {
                    Spacer(Modifier.height(14.dp))
                    when {
                        cooldownMatured -> Text(
                            "Cooldown served. You can turn Strict Mode off now.",
                            color = Emerald,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        cooldownPending -> Text(
                            "Cooldown ends in ${
                                ((settings.cooldownEndsAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(1)
                            } minutes.",
                            color = Amber,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        else -> OutlinedButton(
                            onClick = { viewModel.startCooldown() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Start ${settings.cooldownMinutes}-minute cooldown",
                                color = Mist
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    LabelledSlider(
                        label = "Cooldown length",
                        value = settings.cooldownMinutes.toFloat(),
                        range = 5f..180f,
                        steps = 34,
                        display = { "${it.roundToInt()} min" },
                        onChange = { viewModel.setCooldownMinutes(it.roundToInt()) }
                    )
                }
            }
        }

        // ------------------------------------------------------------ the price
        item {
            SectionCard {
                SectionLabel("The price")
                Spacer(Modifier.height(12.dp))

                LabelledSlider(
                    label = "Push-ups per unlock",
                    value = settings.baseReps.toFloat(),
                    range = 1f..40f,
                    steps = 38,
                    display = { "${it.roundToInt()} reps" },
                    onChange = { viewModel.setBaseReps(it.roundToInt()) }
                )

                LabelledSlider(
                    label = "Minutes bought",
                    value = settings.baseUnlockMinutes.toFloat(),
                    range = 1f..15f,
                    steps = 13,
                    display = { "${it.roundToInt()} min" },
                    onChange = { viewModel.setBaseMinutes(it.roundToInt()) }
                )

                LabelledSlider(
                    label = "Price escalation per unlock",
                    value = settings.escalation,
                    range = 0f..1.5f,
                    steps = 14,
                    display = { "+${(it * 100).roundToInt()}%" },
                    onChange = { viewModel.setEscalation(it) }
                )

                LabelledSlider(
                    label = "Unlocks allowed per day",
                    value = settings.maxEarnedUnlocksPerDay.toFloat(),
                    range = 1f..20f,
                    steps = 18,
                    display = { "${it.roundToInt()}" },
                    onChange = { viewModel.setMaxEarnedUnlocks(it.roundToInt()) }
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    priceLadder(settings.baseReps, settings.escalation),
                    color = Mist,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow buying time at all", color = Chalk, fontSize = 15.sp)
                        Text(
                            "Off means the daily budget is the hard ceiling — no push-up escape hatch.",
                            color = Mist,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = settings.allowEarnedUnlocks,
                        onCheckedChange = { viewModel.setAllowEarned(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Ink,
                            checkedTrackColor = Emerald,
                            uncheckedThumbColor = Mist,
                            uncheckedTrackColor = InkCard
                        )
                    )
                }
            }
        }

        // ------------------------------------------------------------ form strictness
        item {
            SectionCard {
                SectionLabel("How strictly reps are judged")
                Spacer(Modifier.height(12.dp))
                FormPreset.entries.forEach { preset ->
                    val active = settings.downAngleThreshold == preset.downAngle &&
                        settings.upAngleThreshold == preset.upAngle
                    OutlinedButton(
                        onClick = { viewModel.setFormStrictness(preset) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (active) Emerald.copy(alpha = 0.12f) else Ink
                        )
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                preset.label,
                                color = if (active) Emerald else Chalk,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(preset.description, color = Mist, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        // ------------------------------------------------------------ taper
        item {
            SectionCard {
                SectionLabel("Taper plan")
                Spacer(Modifier.height(10.dp))
                val today = LocalDate.now()
                Text(
                    "Day ${(TaperPlan.dayIndex(settings, today) + 1).coerceAtLeast(1)} of ${settings.planDays} · " +
                        "${settings.startMinutesPerDay} min down to ${settings.endMinutesPerDay} min",
                    color = Chalk,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(12.dp))
                TaperCurve(settings.startMinutesPerDay, settings.endMinutesPerDay, settings.planDays)
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.startPlan(
                            settings.planDays,
                            settings.startMinutesPerDay,
                            settings.endMinutesPerDay
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Restart the plan from today", color = Amber) }
            }
        }

        // ------------------------------------------------------------ misc
        item {
            SectionCard {
                SectionLabel("Feel")
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Buzz on each counted rep", color = Chalk, modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.vibrateOnRep,
                        onCheckedChange = { viewModel.setVibrate(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Ink,
                            checkedTrackColor = Emerald,
                            uncheckedThumbColor = Mist,
                            uncheckedTrackColor = InkCard
                        )
                    )
                }
                Spacer(Modifier.height(10.dp))
                LabelledSlider(
                    label = "Day rolls over at",
                    value = settings.rolloverHour.toFloat(),
                    range = 0f..12f,
                    steps = 11,
                    display = { "%02d:00".format(it.roundToInt()) },
                    onChange = { viewModel.setRolloverHour(it.roundToInt()) }
                )
            }
        }

        item {
            Text(
                "Everything runs on this phone. No account, no server, no analytics. " +
                    "Camera frames are processed live and never stored.",
                color = Mist,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: (Float) -> String,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Mist, fontSize = 13.sp)
            Text(display(value), color = Emerald, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Emerald,
                activeTrackColor = Emerald,
                inactiveTrackColor = Chalk.copy(alpha = 0.15f)
            )
        )
    }
}

@Composable
private fun TaperCurve(start: Int, end: Int, days: Int) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(70.dp)) {
        val n = days.coerceAtLeast(2)
        val gap = size.width * 0.02f
        val barWidth = (size.width - gap * (n - 1)) / n
        val maxV = start.coerceAtLeast(1).toFloat()

        for (i in 0 until n) {
            val t = i.toFloat() / (n - 1)
            val v = start + (end - start) * t
            val h = (size.height * (v / maxV)).coerceAtLeast(3f)
            drawRoundRect(
                color = Emerald.copy(alpha = 0.35f + 0.45f * (1f - t)),
                topLeft = androidx.compose.ui.geometry.Offset(i * (barWidth + gap), size.height - h),
                size = androidx.compose.ui.geometry.Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 3f)
            )
        }
    }
}

private fun priceLadder(baseReps: Int, escalation: Float): String {
    val ladder = (0 until 4).map { i ->
        (baseReps * (1f + escalation * i)).roundToInt().coerceAtLeast(1)
    }
    return "Today's ladder: ${ladder.joinToString(" → ")} reps, and up from there."
}
