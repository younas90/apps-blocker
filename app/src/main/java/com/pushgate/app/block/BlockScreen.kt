package com.pushgate.app.block

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.pushgate.app.ui.theme.Amber
import com.pushgate.app.ui.theme.Chalk
import com.pushgate.app.ui.theme.Crimson
import com.pushgate.app.ui.theme.Emerald
import com.pushgate.app.ui.theme.Ink
import com.pushgate.app.ui.theme.InkCard
import com.pushgate.app.ui.theme.Mist
import com.pushgate.app.util.TimeKeys

@Composable
fun BlockScreen(
    packageName: String,
    label: String,
    budgetMs: Long,
    usedMs: Long,
    earnedUnlocksToday: Int,
    canEarn: Boolean,
    repsRequired: Int,
    minutesOffered: Int,
    onStartChallenge: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val icon = remember(packageName) { AppLabels.iconOrNull(context, packageName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 26.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(InkCard)
                .border(1.dp, Chalk.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Image(
                    bitmap = remember(icon) { icon.toBitmap(96, 96).asImageBitmap() },
                    contentDescription = null,
                    modifier = Modifier.size(46.dp)
                )
            } else {
                Text(
                    label.take(1).uppercase(),
                    color = Chalk,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        Text(
            text = "$label is closed",
            color = Chalk,
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = buildString {
                append("You have used ")
                append(TimeKeys.formatDuration(usedMs))
                append(" of today's ")
                append(TimeKeys.formatDuration(budgetMs))
                append('.')
            },
            color = Mist,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(26.dp))

        BudgetBar(usedMs = usedMs, budgetMs = budgetMs)

        Spacer(Modifier.height(34.dp))

        if (canEarn) {
            PriceCard(
                reps = repsRequired,
                minutes = minutesOffered,
                earnedToday = earnedUnlocksToday
            )

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = onStartChallenge,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Ink),
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "Do $repsRequired push-ups",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Crimson.copy(alpha = 0.10f))
                    .border(1.dp, Crimson.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "That is the ceiling for today",
                        color = Crimson,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "You have bought $earnedUnlocksToday unlocks already. " +
                            "The gate stays shut until tomorrow — that is the whole point.",
                        color = Mist,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        TextButton(onClick = onDismiss) {
            Text("Not worth it — go home", color = Mist, fontSize = 15.sp)
        }
    }
}

@Composable
private fun BudgetBar(usedMs: Long, budgetMs: Long) {
    val fraction = if (budgetMs <= 0L) 1f else (usedMs.toFloat() / budgetMs).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Chalk.copy(alpha = 0.12f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(10.dp)
                .background(Crimson)
        )
    }
}

@Composable
private fun PriceCard(reps: Int, minutes: Int, earnedToday: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(InkCard)
            .border(1.dp, Emerald.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
            .padding(20.dp)
    ) {
        Column {
            Text("THE PRICE", color = Emerald, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("$reps", color = Chalk, fontSize = 40.sp, fontWeight = FontWeight.Black)
                    Text("push-ups", color = Mist, fontSize = 13.sp)
                }
                Spacer(Modifier.width(20.dp))
                Text("→", color = Mist, fontSize = 26.sp)
                Spacer(Modifier.width(20.dp))
                Column {
                    Text("$minutes", color = Emerald, fontSize = 40.sp, fontWeight = FontWeight.Black)
                    Text("minutes", color = Mist, fontSize = 13.sp)
                }
            }
            if (earnedToday > 0) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Unlock #${earnedToday + 1} today. Each one costs more than the last.",
                    color = Amber,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/** Shown when Strict Mode catches someone heading for the off switch. */
@Composable
fun TamperHost(
    cooldownMinutes: Int,
    onStartCooldown: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 28.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(76.dp)) {
            drawCircle(color = Amber.copy(alpha = 0.14f))
            drawCircle(color = Amber, style = Stroke(width = 3f))
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Strict Mode is on",
            color = Chalk,
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(14.dp))

        Text(
            "You set this up when you were thinking clearly. Right now you are not — that is what " +
                "the urge feels like from the inside.\n\nYou can still switch PushGate off. It just " +
                "takes $cooldownMinutes minutes of waiting first, and by then you will not want to.",
            color = Mist,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Ink),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Fine — leave it on", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = { onStartCooldown(cooldownMinutes) }) {
            Text(
                "Start the $cooldownMinutes-minute cooldown anyway",
                color = Mist,
                fontSize = 14.sp
            )
        }
    }
}
