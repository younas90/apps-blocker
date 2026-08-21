package com.pushgate.app.ui.apps

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pushgate.app.block.ProtectionGate
import com.pushgate.app.ui.InstalledApp
import com.pushgate.app.ui.MainViewModel
import com.pushgate.app.ui.common.AppIcon
import com.pushgate.app.ui.common.ProtectedActionDialog
import com.pushgate.app.ui.common.SectionCard
import com.pushgate.app.ui.theme.Amber
import com.pushgate.app.ui.theme.Chalk
import com.pushgate.app.ui.theme.Emerald
import com.pushgate.app.ui.theme.Ink
import com.pushgate.app.ui.theme.InkCard
import com.pushgate.app.ui.theme.Mist

@Composable
fun AppsScreen(viewModel: MainViewModel) {
    var picking by remember { mutableStateOf(false) }

    if (picking) {
        AppPicker(viewModel = viewModel, onDone = { picking = false })
    } else {
        BlockedAppList(viewModel = viewModel, onAdd = { picking = true })
    }
}

@Composable
private fun BlockedAppList(viewModel: MainViewModel, onAdd: () -> Unit) {
    val blocked by viewModel.blockedApps.collectAsStateWithLifecycle()

    // Set when a weakening action is refused, so one dialog serves every case.
    var blockedAction by remember { mutableStateOf<Pair<String, ProtectionGate.Verdict>?>(null) }

    blockedAction?.let { (action, verdict) ->
        ProtectedActionDialog(
            action = action,
            verdict = verdict,
            onStartWait = {
                viewModel.startCooldown()
                blockedAction = null
            },
            onDismiss = { blockedAction = null }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Your apps", color = Chalk, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Each one gets its own daily minutes. Adding an app is instant. " +
                        "Removing one takes a wait — that is the whole point.",
                    color = Mist,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Ink),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add an app", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        if (blocked.isEmpty()) {
            item {
                SectionCard {
                    Text(
                        "Nothing blocked yet.\n\nStart with the one you open without deciding to.",
                        color = Mist,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        items(blocked, key = { it.packageName }) { app ->
            var budgetText by remember(app.packageName, app.customBudgetMinutes) {
                mutableStateOf(app.customBudgetMinutes?.toString().orEmpty())
            }
            var editing by remember(app.packageName) { mutableStateOf(false) }

            SectionCard(accent = if (app.enabled) Emerald else Amber) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.packageName, app.label, size = 44)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            app.label,
                            color = Chalk,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (app.enabled) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Emerald,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    app.customBudgetMinutes?.let { "$it min a day" } ?: "On the plan",
                                    color = Emerald,
                                    fontSize = 13.sp
                                )
                            } else {
                                Text("Paused — not being blocked", color = Amber, fontSize = 13.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Re-enabling is free. Pausing is not.
                    SmallAction(
                        label = if (app.enabled) "Pause" else "Resume",
                        tint = if (app.enabled) Amber else Emerald,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (!viewModel.toggleBlockedApp(app.packageName, !app.enabled)) {
                            blockedAction = "pausing ${app.label}" to viewModel.gateVerdict()
                        }
                    }

                    SmallAction(
                        label = if (editing) "Done" else "Minutes",
                        tint = Mist,
                        modifier = Modifier.weight(1f)
                    ) { editing = !editing }

                    SmallAction(
                        label = "Remove",
                        tint = Mist,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (!viewModel.removeBlockedApp(app.packageName)) {
                            blockedAction = "removing ${app.label}" to viewModel.gateVerdict()
                        }
                    }
                }

                if (editing) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = budgetText,
                            onValueChange = { budgetText = it.filter { c -> c.isDigit() }.take(4) },
                            label = { Text("Minutes a day", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Chalk,
                                unfocusedTextColor = Chalk,
                                focusedContainerColor = Ink,
                                unfocusedContainerColor = Ink,
                                focusedLabelColor = Emerald,
                                unfocusedLabelColor = Mist,
                                cursorColor = Emerald,
                                focusedIndicatorColor = Emerald,
                                unfocusedIndicatorColor = Mist.copy(alpha = 0.4f)
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            val requested = budgetText.toIntOrNull()
                            if (viewModel.setCustomBudget(app.packageName, requested)) {
                                editing = false
                            } else {
                                blockedAction =
                                    "giving ${app.label} more minutes" to viewModel.gateVerdict()
                            }
                        }) { Text("Save", color = Emerald, fontWeight = FontWeight.SemiBold) }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Leave it empty to follow the shrinking plan. Lowering it is instant; " +
                            "raising it takes a wait.",
                        color = Mist,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallAction(
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AppPicker(viewModel: MainViewModel, onDone: () -> Unit) {
    val installed by viewModel.installedApps.collectAsStateWithLifecycle()
    val loading by viewModel.loadingApps.collectAsStateWithLifecycle()
    val alreadyBlocked by viewModel.blockedApps.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateOf(setOf<InstalledApp>()) }

    LaunchedEffect(Unit) { viewModel.loadInstalledApps() }

    val blockedPackages = remember(alreadyBlocked) { alreadyBlocked.map { it.packageName }.toSet() }

    val visible = remember(installed, query, blockedPackages) {
        installed.asSequence()
            .filter { !it.isSystem }
            .filter { it.packageName !in blockedPackages }
            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            .toList()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(18.dp))
        Text("Add apps", color = Chalk, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(10.dp))

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

        Spacer(Modifier.height(12.dp))

        if (loading) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Emerald)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(visible, key = { it.packageName }) { app ->
                val isSelected = app in selected.value
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Emerald.copy(alpha = 0.14f) else InkCard)
                        .clickable {
                            selected.value =
                                if (isSelected) selected.value - app else selected.value + app
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(app.packageName, app.label, size = 36)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        app.label,
                        color = Chalk,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Emerald,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            TextButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                Text("Cancel", color = Mist)
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = {
                    viewModel.setBlockedApps(selected.value)
                    onDone()
                },
                enabled = selected.value.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Emerald,
                    contentColor = Ink,
                    disabledContainerColor = InkCard,
                    disabledContentColor = Mist
                ),
                modifier = Modifier.weight(2f).height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    if (selected.value.isEmpty()) "Pick some apps" else "Block ${selected.value.size}",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
