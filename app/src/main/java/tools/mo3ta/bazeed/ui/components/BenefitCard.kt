package tools.mo3ta.bazeed.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tools.mo3ta.bazeed.data.Benefit
import tools.mo3ta.bazeed.data.BenefitAccent
import tools.mo3ta.bazeed.ui.theme.Almarai
import tools.mo3ta.bazeed.ui.theme.Amiri
import tools.mo3ta.bazeed.ui.theme.Green
import tools.mo3ta.bazeed.ui.theme.Ink
import tools.mo3ta.bazeed.ui.theme.InkMute
import tools.mo3ta.bazeed.ui.theme.LineSoft
import tools.mo3ta.bazeed.ui.theme.Paper
import tools.mo3ta.bazeed.ui.theme.Paper2
import tools.mo3ta.bazeed.ui.theme.Saffron
import tools.mo3ta.bazeed.ui.theme.Terracotta

@Composable
fun BenefitCard(
    benefit: Benefit,
    modifier: Modifier = Modifier
) {
    val icon = iconForTitle(benefit.titleAr)
    val (bg, fg) = colorForAccent(benefit.accent)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Paper2)
            .border(1.dp, LineSoft, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = benefit.titleAr,
            fontFamily = Amiri,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            lineHeight = 16.sp
        )
        Text(
            text = benefit.detailAr,
            fontFamily = Almarai,
            fontSize = 11.sp,
            color = InkMute,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(2.dp))
    }
}

private fun iconForTitle(title: String): ImageVector {
    return when {
        title.contains("توصيل") -> Icons.Outlined.LocalShipping
        title.contains("مراجعة") -> Icons.Outlined.AccessTime
        title.contains("تجديد") -> Icons.Outlined.Autorenew
        else -> Icons.Outlined.Notifications
    }
}

private fun colorForAccent(accent: BenefitAccent): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    return when (accent) {
        BenefitAccent.Green -> Green to Paper
        BenefitAccent.Terracotta -> Terracotta to Paper
        BenefitAccent.Saffron -> Saffron to Ink
        BenefitAccent.Ink -> Ink to Paper
    }
}
