package com.pushgate.app.ui.settings

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pushgate.app.block.AdminReceiver
import com.pushgate.app.block.GuardBypass
import com.pushgate.app.block.ProtectionGate
import com.pushgate.app.quota.TaperPlan
import com.pushgate.app.ui.FormPreset
import com.pushgate.app.ui.MainViewModel
import com.pushgate.app.ui.common.ProtectedActionDialog
import com.pushgate.app.ui.common.rememberAdminEnabler
import com.pushgate.app.ui.common.SectionCard
import com.pushgate.app.ui.theme.Amber
import com.pushgate.app.ui.theme.Chalk
import com.pushgate.app.ui.theme.Crimson
import com.pushgate.app.ui.theme.Emerald
import com.pushgate.app.ui.theme.Ink
import com.pushgate.app.ui.theme.InkCard
import com.pushgate.app.ui.theme.Mist
import com.pushgate.app.util.TimeKeys
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val protection by viewModel.protection.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val enableAdmin = rememberAdminEnabler(viewModel)

    var showAdvanced by remember { mutableStateOf(false) }
    var blockedAction by remember { mutableStateOf<Pair<String, ProtectionGate.Verdict>?>(null) }

    blockedAction?.let { (action, verdict) ->
        ProtectedActionDialog(
            action = action,
            verdict = verdict,
            onStartWait = { viewModel.startCooldown(); blockedAction = null },
            onDismiss = { blockedAction = null }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("Settings", color = Chalk, style = MaterialTheme.typography.displaySmall) }

        // ---------------------------------------------------------------- protection
        item {
            val armed = protection.accessibilityEnabled
            SectionCard(accent = if (armed) Emerald else Crimson) {
                Text(
                    if (armed) "PushGate is watching" else "PushGate is switched off",
                    color = if (armed) Emerald else Crimson,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (armed) {
                        "Your blocked apps are being held to their daily minutes."
                    } else {
                        "Nothing is being blocked right now."
                    },
                    color = Mist,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(14.dp))

                if (!armed) {
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
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Switch it back on", fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(10.dp))
                }

                UninstallProtectionRow(
                    active = protection.deviceAdminActive,
                    canRemove = ProtectionGate.isAllowed(settings),
                    onEnable = enableAdmin,
                    onRemove = {
                        if (ProtectionGate.isAllowed(settings)) {
                            viewModel.deactivateDeviceAdmin()
                            viewModel.clearCooldown()
                        } else {
                            blockedAction =
                                "turning off uninstall protection" to viewModel.gateVerdict()
                        }
                    }
                )
            }
        }

        // ---------------------------------------------------------------- strict mode
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Make it hard to quit",
                            color = Chalk,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Removing an app, pausing one, or switching PushGate off all take a " +
                                "${settings.cooldownMinutes}-minute wait first.",
                            color = Mist,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(
                        checked = settings.strictMode,
                        onCheckedChange = { on ->
                            if (!viewModel.setStrictMode(on)) {
                                blockedAction =
                                    "switching this off" to viewModel.gateVerdict()
                            }
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
                    Spacer(Modifier.height(12.dp))
                    when (val v = ProtectionGate.check(settings)) {
                        is ProtectionGate.Verdict.Allowed -> Banner(
                            "The wait is served — your next change goes through.",
                            Emerald
                        )
                        is ProtectionGate.Verdict.Waiting -> Banner(
                            "Waiting: ${TimeKeys.formatDuration(v.remainingMs)} to go.",
                            Amber
                        )
                        is ProtectionGate.Verdict.NeedsWait -> Banner(
                            "Everything is locked in. Changes need a ${v.minutes}-minute wait.",
                            Mist
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------------- the price
        item {
            SectionCard {
                Text("The price of more time", color = Chalk, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${settings.baseReps}", color = Chalk, fontSize = 38.sp, fontWeight = FontWeight.Black)
                        Text("push-ups", color = Mist, fontSize = 13.sp)
                    }
                    Text("buys", color = Mist, fontSize = 14.sp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(
                            "${settings.baseUnlockMinutes}",
                            color = Emerald,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text("minutes", color = Mist, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(14.dp))
                PlainSlider("Push-ups", settings.baseReps.toFloat(), 1f..40f, 38, { "${it.roundToInt()}" }) {
                    viewModel.setBaseReps(it.roundToInt())
                }
                PlainSlider("Minutes", settings.baseUnlockMinutes.toFloat(), 1f..15f, 13, { "${it.roundToInt()}" }) {
                    viewModel.setBaseMinutes(it.roundToInt())
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Each unlock today costs more than the last: " +
                        priceLadder(settings.baseReps, settings.escalation),
                    color = Mist,
                    fontSize = 12.sp
                )
            }
        }

        // ---------------------------------------------------------------- difficulty
        item {
            SectionCard {
                Text("How strict is a rep?", color = Chalk, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.height(12.dp))
                FormPreset.entries.forEach { preset ->
                    val active = settings.downAngleThreshold == preset.downAngle &&
                        settings.upAngleThreshold == preset.upAngle
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) Emerald.copy(alpha = 0.13f) else Ink)
                            .clickable { viewModel.setFormStrictness(preset) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                preset.label,
                                color = if (active) Emerald else Chalk,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(preset.description, color = Mist, fontSize = 12.sp)
                        }
                        if (active) Icon(Icons.Default.Check, null, tint = Emerald)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        // ---------------------------------------------------------------- plan
        item {
            SectionCard {
                Text("Your plan", color = Chalk, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.height(8.dp))
                val today = LocalDate.now()
                Text(
                    "Day ${(TaperPlan.dayIndex(settings, today) + 1).coerceAtLeast(1)} of ${settings.planDays} — " +
                        "from ${settings.startMinutesPerDay} minutes down to ${settings.endMinutesPerDay}.",
                    color = Mist,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                TaperCurve(settings.startMinutesPerDay, settings.endMinutesPerDay, settings.planDays)
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = {
                        viewModel.startPlan(
                            settings.planDays,
                            settings.startMinutesPerDay,
                            settings.endMinutesPerDay
                        )
                    }
                ) { Text("Start the plan again from today", color = Amber) }
            }
        }

        // ---------------------------------------------------------------- advanced
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (showAdvanced) "Hide the fiddly bits" else "Show the fiddly bits",
                    color = Mist,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ExpandMore, null, tint = Mist)
            }
        }

        item {
            AnimatedVisibility(visible = showAdvanced) {
                SectionCard {
                    PlainSlider(
                        "Length of the wait",
                        settings.cooldownMinutes.toFloat(), 5f..180f, 34,
                        { "${it.roundToInt()} min" }
                    ) { viewModel.setCooldownMinutes(it.roundToInt()) }

                    PlainSlider(
                        "Extra cost per unlock",
                        settings.escalation, 0f..1.5f, 14,
                        { "+${(it * 100).roundToInt()}%" }
                    ) { viewModel.setEscalation(it) }

                    PlainSlider(
                        "Most unlocks per day",
                        settings.maxEarnedUnlocksPerDay.toFloat(), 1f..20f, 18,
                        { "${it.roundToInt()}" }
                    ) { viewModel.setMaxEarnedUnlocks(it.roundToInt()) }

                    PlainSlider(
                        "New day starts at",
                        settings.rolloverHour.toFloat(), 0f..12f, 11,
                        { "%02d:00".format(it.roundToInt()) }
                    ) { viewModel.setRolloverHour(it.roundToInt()) }

                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Allow buying time at all", color = Chalk, fontSize = 15.sp)
                            Text(
                                "Off means the daily minutes are a hard ceiling — no push-up way out.",
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

                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Always show the status notification", color = Chalk, fontSize = 15.sp)
                            Text(
                                "Off by default. It normally appears only while you are inside a " +
                                    "blocked app, showing the countdown.",
                                color = Mist,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = settings.alwaysShowGuardNotification,
                            onCheckedChange = { viewModel.setAlwaysShowGuard(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Ink,
                                checkedTrackColor = Emerald,
                                uncheckedThumbColor = Mist,
                                uncheckedTrackColor = InkCard
                            )
                        )
                    }

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
                }
            }
        }

        item {
            Text(
                "Everything stays on this phone. No account, no server, and the app has no " +
                    "internet permission at all — it cannot send anything anywhere.",
                color = Mist,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun UninstallProtectionRow(
    active: Boolean,
    canRemove: Boolean,
    onEnable: () -> Unit,
    onRemove: () -> Unit
) {
    if (active) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, null, tint = Emerald, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Cannot be uninstalled", color = Emerald, modifier = Modifier.weight(1f), fontSize = 14.sp)
            TextButton(onClick = onRemove) {
                Text(if (canRemove) "Remove" else "Locked", color = Mist, fontSize = 13.sp)
            }
        }
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Crimson.copy(alpha = 0.12f))
                .clickable(onClick = onEnable)
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = Crimson, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Anyone can uninstall this app", color = Crimson, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Long-press the icon, hit uninstall, and the whole thing is gone in two " +
                            "seconds. Tap here to stop that.",
                        color = Mist,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun Banner(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(12.dp)
    ) {
        Text(text, color = color, fontSize = 13.sp)
    }
}

@Composable
private fun PlainSlider(
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
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(64.dp)) {
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

private fun priceLadder(baseReps: Int, escalation: Float): String =
    (0 until 4).joinToString(" → ") { i ->
        (baseReps * (1f + escalation * i)).roundToInt().coerceAtLeast(1).toString()
    } + " reps"
