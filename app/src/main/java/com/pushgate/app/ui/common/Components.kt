package com.pushgate.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.pushgate.app.block.AppLabels
import com.pushgate.app.ui.theme.Chalk
import com.pushgate.app.ui.theme.Emerald
import com.pushgate.app.ui.theme.InkCard
import com.pushgate.app.ui.theme.Mist

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(InkCard)
            .then(
                if (accent != null) {
                    Modifier.border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                } else {
                    Modifier.border(1.dp, Chalk.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
                }
            )
            .padding(18.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun SectionLabel(text: String, color: Color = Emerald) {
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun AppIcon(packageName: String, label: String, size: Int = 40) {
    val context = LocalContext.current
    val drawable = remember(packageName) { AppLabels.iconOrNull(context, packageName) }

    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Chalk.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (drawable != null) {
            Image(
                bitmap = remember(drawable) { drawable.toBitmap(96, 96).asImageBitmap() },
                contentDescription = null,
                modifier = Modifier.size((size * 0.62f).dp)
            )
        } else {
            Text(
                label.take(1).uppercase(),
                color = Chalk,
                fontWeight = FontWeight.Bold,
                fontSize = (size * 0.38f).sp
            )
        }
    }
}

@Composable
fun StatTile(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Emerald
) {
    Column(modifier) {
        Text(value, color = valueColor, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(2.dp))
        Text(caption, color = Mist, fontSize = 12.sp)
    }
}

@Composable
fun MeterBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Int = 8
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape((height / 2).dp))
            .background(Chalk.copy(alpha = 0.10f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height.dp)
                .background(color)
        )
    }
}

@Composable
fun KeyValueRow(key: String, value: String, valueColor: Color = Chalk) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(key, color = Mist, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
