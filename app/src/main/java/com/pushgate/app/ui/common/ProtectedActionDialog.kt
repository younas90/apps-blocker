package com.pushgate.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pushgate.app.block.ProtectionGate
import com.pushgate.app.ui.theme.Amber
import com.pushgate.app.ui.theme.Chalk
import com.pushgate.app.ui.theme.Emerald
import com.pushgate.app.ui.theme.InkRaised
import com.pushgate.app.ui.theme.Mist
import com.pushgate.app.util.TimeKeys

/**
 * The single dialog shown whenever something would make PushGate weaker.
 *
 * Same wording, same options, same wait, whether the user is deleting an app, pausing one, raising
 * a budget or switching Strict Mode off — so there is never a softer-looking door to try next.
 */
@Composable
fun ProtectedActionDialog(
    /** Verb phrase completing "…takes a wait", e.g. "removing TikTok". */
    action: String,
    verdict: ProtectionGate.Verdict,
    onStartWait: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InkRaised,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                when (verdict) {
                    is ProtectionGate.Verdict.Waiting -> "The wait has started"
                    else -> "Not right now"
                },
                color = Chalk,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    ProtectionGate.explain(verdict, action),
                    color = Mist,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (verdict is ProtectionGate.Verdict.Waiting) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "${TimeKeys.formatDuration(verdict.remainingMs)} to go",
                        color = Amber,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            when (verdict) {
                is ProtectionGate.Verdict.NeedsWait -> TextButton(onClick = onStartWait) {
                    Text("Start the wait", color = Amber, fontWeight = FontWeight.SemiBold)
                }
                else -> TextButton(onClick = onDismiss) {
                    Text("OK", color = Emerald, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            if (verdict is ProtectionGate.Verdict.NeedsWait) {
                TextButton(onClick = onDismiss) {
                    Text("Leave it alone", color = Emerald, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    )
}
