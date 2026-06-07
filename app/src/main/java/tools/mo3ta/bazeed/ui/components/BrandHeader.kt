package tools.mo3ta.bazeed.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tools.mo3ta.bazeed.ui.theme.Amiri
import tools.mo3ta.bazeed.ui.theme.Green
import tools.mo3ta.bazeed.ui.theme.InkMute
import tools.mo3ta.bazeed.ui.theme.LineSoft
import tools.mo3ta.bazeed.ui.theme.Paper
import tools.mo3ta.bazeed.ui.theme.Paper2
import tools.mo3ta.bazeed.ui.theme.Terracotta

@Composable
fun BrandHeader(
    titleAr: String,
    captionAr: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = captionAr,
                fontSize = 11.sp,
                color = InkMute,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = titleAr,
                fontFamily = Amiri,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        BrandMark()
    }
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Paper2)
            .border(1.dp, LineSoft, CircleShape)
            .drawBehind {
                val dotRadius = 3.dp.toPx()
                val cx = size.width - dotRadius - 4.dp.toPx()
                val cy = dotRadius + 4.dp.toPx()
                drawCircle(
                    color = Terracotta,
                    radius = dotRadius,
                    center = androidx.compose.ui.geometry.Offset(cx, cy)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "ب",
            fontFamily = Amiri,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Green
        )
    }
}

@Composable
fun LocationPill(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Green.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Green)
        )
        Text(
            text = text,
            color = Green,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun SectionHeader(
    titleAr: String,
    actionAr: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = titleAr,
            fontFamily = Amiri,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (actionAr != null) {
            Text(
                text = actionAr,
                color = Terracotta,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clickable(enabled = onAction != null) { onAction?.invoke() }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
}
