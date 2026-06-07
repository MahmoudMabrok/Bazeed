package tools.mo3ta.bazeed.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tools.mo3ta.bazeed.data.Repositories
import tools.mo3ta.bazeed.ui.components.AnnouncementCard
import tools.mo3ta.bazeed.ui.theme.Almarai
import tools.mo3ta.bazeed.ui.theme.Amiri
import tools.mo3ta.bazeed.ui.theme.Ink
import tools.mo3ta.bazeed.ui.theme.InkMute
import tools.mo3ta.bazeed.ui.theme.LineSoft
import tools.mo3ta.bazeed.ui.theme.Mono
import tools.mo3ta.bazeed.ui.theme.Paper
import tools.mo3ta.bazeed.ui.theme.Paper2
import tools.mo3ta.bazeed.ui.theme.Terracotta

@Composable
fun AnnouncementsScreen(
    onAnnouncementTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val announcements by Repositories.content.announcements.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
    ) {
        item { AnnouncementsHeader(count = announcements.size); Spacer(Modifier.height(8.dp)) }
        if (announcements.isEmpty()) {
            item { EmptyState() }
        } else {
            items(announcements, key = { it.id }) { ann ->
                AnnouncementCard(announcement = ann, onClick = { onAnnouncementTap(ann.id) })
            }
        }
    }
}

@Composable
private fun AnnouncementsHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "ANNOUNCEMENTS · $count",
                fontFamily = Mono,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = InkMute,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "إعلانات بازيد",
                fontFamily = Amiri,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Paper2)
                .border(1.dp, LineSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(18.dp),
            )
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Terracotta)
                        .border(2.dp, Paper, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = count.toString(),
                        fontFamily = Almarai,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Paper,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "لا توجد إعلانات حالياً",
            fontFamily = Almarai,
            fontSize = 14.sp,
            color = InkMute,
        )
    }
}
