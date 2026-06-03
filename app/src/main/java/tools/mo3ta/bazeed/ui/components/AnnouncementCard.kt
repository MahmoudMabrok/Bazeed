package tools.mo3ta.bazeed.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tools.mo3ta.bazeed.data.Announcement
import tools.mo3ta.bazeed.data.AnnouncementCategory
import tools.mo3ta.bazeed.ui.theme.Almarai
import tools.mo3ta.bazeed.ui.theme.Amiri
import tools.mo3ta.bazeed.ui.theme.Green
import tools.mo3ta.bazeed.ui.theme.GreenSoft
import tools.mo3ta.bazeed.ui.theme.Ink
import tools.mo3ta.bazeed.ui.theme.InkMute
import tools.mo3ta.bazeed.ui.theme.LineSoft
import tools.mo3ta.bazeed.ui.theme.Mint
import tools.mo3ta.bazeed.ui.theme.Mono
import tools.mo3ta.bazeed.ui.theme.Paper
import tools.mo3ta.bazeed.ui.theme.Paper2
import tools.mo3ta.bazeed.ui.theme.Saffron
import tools.mo3ta.bazeed.ui.theme.SaffronLight
import tools.mo3ta.bazeed.ui.theme.Terracotta

@Composable
fun AnnouncementCard(
    announcement: Announcement,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val (icon, gradient, onAccent) = categoryStyle(announcement.category)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Paper2)
            .border(1.dp, LineSoft, RoundedCornerShape(20.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(brush = Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onAccent,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = announcement.category.labelAr,
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    color = Green,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.6.sp
                )
                Box(
                    Modifier
                        .size(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(InkMute)
                )
                Icon(
                    imageVector = Icons.Outlined.AccessTime,
                    contentDescription = null,
                    tint = InkMute,
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = announcement.timeAgoAr,
                    fontFamily = Almarai,
                    fontSize = 10.sp,
                    color = InkMute
                )
            }
            Text(
                text = announcement.title,
                fontFamily = Amiri,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                lineHeight = 20.sp
            )
            Text(
                text = announcement.snippet,
                fontFamily = Almarai,
                fontSize = 11.sp,
                color = InkMute,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun FeaturedAnnouncementCard(
    announcement: Announcement,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Terracotta)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Paper.copy(alpha = 0.18f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "FEATURED · هذا الأسبوع",
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    color = Paper,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp
                )
            }
            Text(
                text = announcement.title,
                fontFamily = Amiri,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Paper,
                lineHeight = 24.sp
            )
            if (announcement.highlight != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = announcement.highlight,
                        fontFamily = Mono,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaffronLight
                    )
                    Text(
                        text = " off",
                        fontFamily = Mono,
                        fontSize = 14.sp,
                        color = SaffronLight.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
            Text(
                text = announcement.snippet,
                fontFamily = Almarai,
                fontSize = 12.sp,
                color = Paper.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Paper)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "اعرف التفاصيل",
                    fontFamily = Amiri,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Terracotta
                )
            }
        }
    }
}

private fun categoryStyle(category: AnnouncementCategory): Triple<ImageVector, List<Color>, Color> {
    return when (category) {
        AnnouncementCategory.Health ->
            Triple(Icons.Outlined.Spa, listOf(Mint, GreenSoft), Green)
        AnnouncementCategory.Alert ->
            Triple(Icons.Outlined.Campaign, listOf(SaffronLight, Saffron), Ink)
        AnnouncementCategory.Tip ->
            Triple(Icons.Outlined.MedicalServices, listOf(Mint, GreenSoft), Green)
        AnnouncementCategory.Offer ->
            Triple(Icons.Outlined.LocalOffer, listOf(SaffronLight, Saffron), Ink)
    }
}

@Composable
fun Divider3dp() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LineSoft)
    )
}
