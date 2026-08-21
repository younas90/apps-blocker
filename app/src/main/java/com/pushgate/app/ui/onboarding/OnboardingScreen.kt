package com.pushgate.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pushgate.app.block.AdminReceiver
import com.pushgate.app.block.GuardBypass
import com.pushgate.app.ui.FormPreset
import com.pushgate.app.ui.InstalledApp
import com.pushgate.app.ui.MainViewModel
import com.pushgate.app.ui.common.AppIcon
import com.pushgate.app.ui.common.SectionCard
import com.pushgate.app.ui.common.SectionLabel
import com.pushgate.app.ui.theme.Amber
import com.pushgate.app.ui.theme.Chalk
import com.pushgate.app.ui.theme.Crimson
import com.pushgate.app.ui.theme.Emerald
import com.pushgate.app.ui.theme.Ink
import com.pushgate.app.ui.theme.InkCard
import com.pushgate.app.ui.theme.Mist
import kotlin.math.roundToInt

private enum class Step { WELCOME, PICK_APPS, PLAN, PRICE, PERMISSIONS, DONE }

@Composable
fun OnboardingScreen(viewModel: MainViewModel) {
    var step by remember { mutableStateOf(Step.WELCOME) }
    val selected = remember { mutableStateOf(setOf<InstalledApp>()) }

    var planDays by remember { mutableIntStateOf(7) }
    var startMinutes by remember { mutableIntStateOf(60) }
    var endMinutes by remember { mutableIntStateOf(10) }
    var reps by remember { mutableIntStateOf(5) }
    var minutes by remember { mutableIntStateOf(2) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        when (step) {
            Step.WELCOME -> Welcome(onNext = { step = Step.PICK_APPS })

            Step.PICK_APPS -> PickApps(
                viewModel = viewModel,
                selected = selected.value,
                onToggle = { app ->
                    selected.value = if (app in selected.value) selected.value - app else selected.value + app
                },
                onNext = {
                    viewModel.setBlockedApps(selected.value)
                    step = Step.PLAN
                }
            )

            Step.PLAN -> PlanStep(
                days = planDays,
                startMinutes = startMinutes,
                endMinutes = endMinutes,
                onDays = { planDays = it },
                onStart = { startMinutes = it },
                onEnd = { endMinutes = it },
                onNext = {
                    viewModel.startPlan(planDays, startMinutes, endMinutes.coerceAtMost(startMinutes))
                    step = Step.PRICE
                }
            )

            Step.PRICE -> PriceStep(
                reps = reps,
                minutes = minutes,
                onReps = { reps = it },
                onMinutes = { minutes = it },
                onPreset = { viewModel.setFormStrictness(it) },
                onNext = {
                    viewModel.setBaseReps(reps)
                    viewModel.setBaseMinutes(minutes)
                    step = Step.PERMISSIONS
                }
            )

            Step.PERMISSIONS -> PermissionsStep(
                viewModel = viewModel,
                onNext = { step = Step.DONE }
            )

            Step.DONE -> DoneStep(
                onFinish = { viewModel.completeOnboarding() }
            )
        }
    }
}

@Composable
private fun StepScaffold(
    title: String,
    subtitle: String,
    ctaLabel: String,
    ctaEnabled: Boolean = true,
    onCta: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(28.dp))
        Text(title, color = Chalk, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = Mist, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(22.dp))

        Box(Modifier.weight(1f)) { content() }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onCta,
            enabled = ctaEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Emerald,
                contentColor = Ink,
                disabledContainerColor = InkCard,
                disabledContentColor = Mist
            ),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text(ctaLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp) }

        footer?.invoke()
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    StepScaffold(
        title = "PushGate",
        subtitle = "Your apps stay shut. If you want in early, you pay in push-ups.",
        ctaLabel = "Set it up",
        onCta = onNext
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Bullet(
                "A shrinking daily budget",
                "Start where you actually are today, then step down a little every day for a week. " +
                    "Cold turkey fails; a taper does not."
            )
            Bullet(
                "A real price, not a tap-through",
                "Out of budget means the camera comes on and counts real reps with real range of motion."
            )
            Bullet(
                "Hard to weasel out of",
                "Uninstall protection, a guard on the Settings screens that would switch it off, and " +
                    "a wait before anything can be undone — including deleting an app from the list."
            )
            Bullet(
                "Nothing leaves your phone",
                "No account, no server, no analytics. The camera never records."
            )
        }
    }
}

@Composable
private fun Bullet(title: String, body: String) {
    Row {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Emerald)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Chalk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(body, color = Mist, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PickApps(
    viewModel: MainViewModel,
    selected: Set<InstalledApp>,
    onToggle: (InstalledApp) -> Unit,
    onNext: () -> Unit
) {
    val installed by viewModel.installedApps.collectAsStateWithLifecycle()
    val loading by viewModel.loadingApps.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadInstalledApps() }

    val visible = remember(installed, query) {
        installed.filter { !it.isSystem }
            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
    }

    StepScaffold(
        title = "What eats your day?",
        subtitle = "Pick the apps you open without deciding to. You can change this later.",
        ctaLabel = if (selected.isEmpty()) "Pick at least one" else "Block ${selected.size} app${if (selected.size == 1) "" else "s"}",
        ctaEnabled = selected.isNotEmpty(),
        onCta = onNext
    ) {
        Column {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Chalk,
                    unfocusedTextColor = Chalk,
                    focusedContainerColor = InkCard,
                    unfocusedContainerColor = InkCard,
                    focusedLabelColor = Emerald,
                    unfocusedLabelColor = Mist,
                    cursorColor = Emerald,
                    focusedIndicatorColor = Emerald,
                    unfocusedIndicatorColor = Mist.copy(alpha = 0.35f)
                )
            )
            Spacer(Modifier.height(10.dp))

            if (loading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Emerald)
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(visible, key = { it.packageName }) { app ->
                    val isSelected = app in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Emerald.copy(alpha = 0.13f) else InkCard)
                            .clickable { onToggle(app) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(app.packageName, app.label, size = 34)
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, color = Chalk, modifier = Modifier.weight(1f))
                        if (isSelected) {
                            Icon(Icons.Default.Check, null, tint = Emerald, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanStep(
    days: Int,
    startMinutes: Int,
    endMinutes: Int,
    onDays: (Int) -> Unit,
    onStart: (Int) -> Unit,
    onEnd: (Int) -> Unit,
    onNext: () -> Unit
) {
    StepScaffold(
        title = "The taper",
        subtitle = "Be honest about day one. A budget you blow through by lunch teaches you nothing.",
        ctaLabel = "Lock in the plan",
        onCta = onNext
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OnboardSlider("Day 1 budget", startMinutes.toFloat(), 5f..240f, 46, { "${it.roundToInt()} min" }) {
                onStart(it.roundToInt())
            }
            OnboardSlider("Final budget", endMinutes.toFloat(), 0f..120f, 23, { "${it.roundToInt()} min" }) {
                onEnd(it.roundToInt())
            }
            OnboardSlider("Days to get there", days.toFloat(), 3f..30f, 26, { "${it.roundToInt()} days" }) {
                onDays(it.roundToInt())
            }

            Spacer(Modifier.height(14.dp))
            SectionCard {
                SectionLabel("Your curve")
                Spacer(Modifier.height(10.dp))
                val ladder = (0 until days.coerceAtMost(10)).map { i ->
                    val t = i.toFloat() / (days - 1).coerceAtLeast(1)
                    (startMinutes + (endMinutes - startMinutes) * t).roundToInt()
                }
                Text(
                    ladder.joinToString(" → ") { "$it" } + if (days > 10) " → …" else "",
                    color = Emerald,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Minutes per app, per day. After day $days you hold at $endMinutes.",
                    color = Mist,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun PriceStep(
    reps: Int,
    minutes: Int,
    onReps: (Int) -> Unit,
    onMinutes: (Int) -> Unit,
    onPreset: (FormPreset) -> Unit,
    onNext: () -> Unit
) {
    var chosen by remember { mutableStateOf(FormPreset.STANDARD) }

    StepScaffold(
        title = "The price",
        subtitle = "What it costs to buy your way past a closed gate.",
        ctaLabel = "Set the price",
        onCta = {
            onPreset(chosen)
            onNext()
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OnboardSlider("Push-ups", reps.toFloat(), 1f..40f, 38, { "${it.roundToInt()}" }) {
                onReps(it.roundToInt())
            }
            OnboardSlider("Minutes bought", minutes.toFloat(), 1f..15f, 13, { "${it.roundToInt()} min" }) {
                onMinutes(it.roundToInt())
            }

            Spacer(Modifier.height(8.dp))
            SectionCard(accent = Emerald) {
                Text(
                    "$reps push-ups → $minutes minute${if (minutes == 1) "" else "s"}",
                    color = Chalk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Every unlock after the first costs 50% more than the last, so the loophole " +
                        "closes itself as the day goes on.",
                    color = Mist,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionLabel("How strictly reps are judged")
            Spacer(Modifier.height(8.dp))
            FormPreset.entries.forEach { preset ->
                val active = preset == chosen
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Emerald.copy(alpha = 0.13f) else InkCard)
                        .clickable { chosen = preset }
                        .padding(14.dp)
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
}

@Composable
private fun PermissionsStep(viewModel: MainViewModel, onNext: () -> Unit) {
    val context = LocalContext.current
    val protection by viewModel.protection.collectAsStateWithLifecycle()

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshProtection() }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshProtection() }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    StepScaffold(
        title = "Three switches",
        subtitle = "Android will not let an app grant these to itself. That is the point — and it is " +
            "also why nobody can flip them behind your back.",
        ctaLabel = when {
            protection.accessibilityEnabled && protection.deviceAdminActive -> "Continue"
            protection.accessibilityEnabled -> "Continue without uninstall protection"
            else -> "Skip for now"
        },
        onCta = onNext,
        footer = {
            val warning = when {
                !protection.accessibilityEnabled ->
                    "Without the accessibility service nothing gets blocked at all."
                !protection.deviceAdminActive ->
                    "Without uninstall protection, one long-press deletes the whole thing."
                else -> null
            }
            if (warning != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    warning,
                    color = Crimson,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PermissionCard(
                title = "Accessibility service",
                body = "Lets PushGate see which app just came to the front. It is the only way Android " +
                    "allows an app to notice and interrupt another app.",
                done = protection.accessibilityEnabled,
                required = true,
                cta = "Open accessibility settings"
            ) {
                GuardBypass.open(5, "onboarding: accessibility")
                runCatching {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }

            PermissionCard(
                title = "Camera",
                body = "Only used while you are paying push-ups. Frames are processed on-device and " +
                    "never saved or transmitted.",
                done = false,
                required = true,
                cta = "Allow camera"
            ) { cameraLauncher.launch(Manifest.permission.CAMERA) }

            PermissionCard(
                title = "Uninstall protection",
                body = "Without this, long-pressing the PushGate icon and tapping uninstall wipes " +
                    "everything in two seconds — no wait, no push-ups. Turning it on is what makes " +
                    "the rest of this app mean anything.\n\n" +
                    "It registers PushGate as a device admin and nothing else: no wipe, no lock, " +
                    "no reading your screen.",
                done = protection.deviceAdminActive,
                required = true,
                cta = "Turn on uninstall protection"
            ) {
                GuardBypass.open(5, "onboarding: device admin")
                runCatching {
                    context.startActivity(
                        AdminReceiver.enableIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    done: Boolean,
    required: Boolean,
    cta: String,
    onClick: () -> Unit
) {
    SectionCard(accent = if (done) Emerald else if (required) Amber else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Chalk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            if (done) {
                Icon(Icons.Default.Check, null, tint = Emerald, modifier = Modifier.size(20.dp))
            } else {
                Text(if (required) "Required" else "Optional", color = Mist, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(body, color = Mist, style = MaterialTheme.typography.bodyMedium)
        if (!done) {
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onClick) { Text(cta, color = Emerald, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun DoneStep(onFinish: () -> Unit) {
    StepScaffold(
        title = "The gate is up",
        subtitle = "From here on, your blocked apps run on a clock.",
        ctaLabel = "Start",
        onCta = onFinish
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Bullet(
                "When the budget runs out",
                "The app closes and you get a choice: walk away, or do the reps."
            )
            Bullet(
                "Set the phone on the floor",
                "Side on, a metre or so away, so your whole body is in frame. " +
                    "The skeleton turns green when it can see you properly."
            )
            Bullet(
                "One rep in each set is a held rep",
                "You will not know which one until it happens. Hold the bottom until it clears."
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "You will want to turn this off around day three. That is exactly when it is working.",
                color = Amber,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun OnboardSlider(
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
            Text(display(value), color = Emerald, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
