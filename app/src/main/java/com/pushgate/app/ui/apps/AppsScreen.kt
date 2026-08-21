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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pushgate.app.ui.InstalledApp
import com.pushgate.app.ui.MainViewModel
import com.pushgate.app.ui.common.AppIcon
import com.pushgate.app.ui.common.SectionCard
import com.pushgate.app.ui.common.SectionLabel
import com.pushgate.app.ui.theme.Chalk
import com.pushgate.app.ui.theme.Crimson
import com.pushgate.app.ui.theme.Emerald
import com.pushgate.app.ui.theme.Ink
import com.pushgate.app.ui.theme.InkCard
import com.pushgate.app.ui.theme.Mist

@Composable
fun AppsScreen(viewModel: MainViewModel) {
    var picking by remember { mutableStateOf(false) }

    if (picking) {
        AppPicker(
            viewModel = viewModel,
            onDone = { picking = false }
        )
    } else {
        BlockedAppList(viewModel = viewModel, onAdd = { picking = true })
    }
}

@Composable
private fun BlockedAppList(viewModel: MainViewModel, onAdd: () -> Unit) {
    val blocked by viewModel.blockedApps.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    "Blocked apps",
                    color = Chalk,
                    style = MaterialTheme.typography.displaySmall
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Each app gets its own daily budget. Pausing one keeps it on the list but " +
                        "stops the gate — useful for a day off, not for an urge.",
                    color = Mist,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Ink),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Add apps", fontWeight = FontWeight.Bold) }
        }

        if (blocked.isEmpty()) {
            item {
                SectionCard {
                    Text(
                        "No apps yet. Start with the one you reach for without deciding to.",
                        color = Mist,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        items(blocked, key = { it.packageName }) { app ->
            var customText by remember(app.packageName) {
                mutableStateOf(app.customBudgetMinutes?.toString().orEmpty())
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.packageName, app.label)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            app.label,
                            color = Chalk,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            app.packageName,
                            color = Mist,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = app.enabled,
                        onCheckedChange = { viewModel.toggleBlockedApp(app.packageName, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Ink,
                            checkedTrackColor = Emerald,
                            uncheckedThumbColor = Mist,
                            uncheckedTrackColor = InkCard
                        )
                    )
                    IconButton(onClick = { viewModel.removeBlockedApp(app.packageName) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Crimson)
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { new ->
                            customText = new.filter { it.isDigit() }.take(4)
                            viewModel.setCustomBudget(
                                app.packageName,
                                customText.toIntOrNull()
                            )
                        },
                        label = { Text("Custom budget (min)", fontSize = 12.sp) },
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
                    if (customText.isNotEmpty()) {
                        TextButton(onClick = {
                            customText = ""
                            viewModel.setCustomBudget(app.packageName, null)
                        }) { Text("Use plan", color = Mist, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPicker(viewModel: MainViewModel, onDone: () -> Unit) {
    val installed by viewModel.installedApps.collectAsStateWithLifecycle()
    val loading by viewModel.loadingApps.collectAsStateWithLifecycle()
    val alreadyBlocked by viewModel.blockedApps.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    val selected = remember { mutableStateOf(setOf<InstalledApp>()) }

    LaunchedEffect(Unit) { viewModel.loadInstalledApps() }

    val blockedPackages = remember(alreadyBlocked) { alreadyBlocked.map { it.packageName }.toSet() }

    val visible = remember(installed, query, showSystem, blockedPackages) {
        installed.asSequence()
            .filter { showSystem || !it.isSystem }
            .filter { it.packageName !in blockedPackages }
            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            .toList()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(18.dp))
        Text("Pick apps", color = Chalk, style = MaterialTheme.typography.displaySmall)
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

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Show system apps", color = Mist, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = showSystem,
                onCheckedChange = { showSystem = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Ink,
                    checkedTrackColor = Emerald,
                    uncheckedThumbColor = Mist,
                    uncheckedTrackColor = InkCard
                )
            )
        }

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
                        .background(if (isSelected) Emerald.copy(alpha = 0.12f) else InkCard)
                        .clickable {
                            selected.value = if (isSelected) {
                                selected.value - app
                            } else {
                                selected.value + app
                            }
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(app.packageName, app.label, size = 34)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, color = Chalk, style = MaterialTheme.typography.bodyLarge)
                        if (app.isSystem) {
                            Text("System", color = Mist, fontSize = 11.sp)
                        }
                    }
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
                    if (selected.value.isEmpty()) "Select apps" else "Block ${selected.value.size}",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
@Suppress("unused")
private fun PickerHeader() {
    SectionLabel("Choose carefully")
}
