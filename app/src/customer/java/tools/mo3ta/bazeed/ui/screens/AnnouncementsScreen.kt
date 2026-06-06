package tools.mo3ta.bazeed.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import tools.mo3ta.bazeed.data.SampleData
import tools.mo3ta.bazeed.ui.components.AnnouncementCard
import tools.mo3ta.bazeed.ui.components.FeaturedAnnouncementCard
import tools.mo3ta.bazeed.ui.theme.Almarai
import tools.mo3ta.bazeed.ui.theme.Amiri
import tools.mo3ta.bazeed.ui.theme.Ink
import tools.mo3ta.bazeed.ui.theme.InkMute
import tools.mo3ta.bazeed.ui.theme.LineSoft
import tools.mo3ta.bazeed.ui.theme.Mono
import tools.mo3ta.bazeed.ui.theme.Paper
import tools.mo3ta.bazeed.ui.theme.Paper2
import tools.mo3ta.bazeed.ui.theme.Terracotta

private val categories = listOf("الكل", "عروض", "أدوية", "نصائح")

@Composable
fun AnnouncementsScreen(
    onAnnouncementTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(categories.first()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 8.dp,
            bottom = 96.dp
        )
    ) {
        item {
            AnnouncementsHeader()
            Spacer(Modifier.height(8.dp))
        }
        item {
            SearchBar()
            Spacer(Modifier.height(10.dp))
        }
        item {
            CategoryFilters(
                selected = selectedCategory,
                onSelect = { selectedCategory = it }
            )
            Spacer(Modifier.height(8.dp))
        }
        item {
            FeaturedAnnouncementCard(announcement = SampleData.featuredAnnouncement)
            Spacer(Modifier.height(4.dp))
        }
        items(SampleData.announcements) { ann ->
            AnnouncementCard(
                announcement = ann,
                onClick = { onAnnouncementTap(ann.id) }
            )
        }
    }
}

@Composable
private fun AnnouncementsHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "ANNOUNCEMENTS · 12",
                fontFamily = Mono,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = InkMute,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "إعلانات بازيد",
                fontFamily = Amiri,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Paper2)
                .border(1.dp, LineSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(18.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Terracotta)
                    .border(2.dp, Paper, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "3",
                    fontFamily = Almarai,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Paper
                )
            }
        }
    }
}

@Composable
private fun SearchBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Paper2)
            .border(1.dp, LineSoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = InkMute,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "ابحث في الإعلانات…",
            fontFamily = Almarai,
            fontSize = 12.sp,
            color = InkMute
        )
    }
}

@Composable
private fun CategoryFilters(
    selected: String,
    onSelect: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(categories) { cat ->
            val isActive = cat == selected
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isActive) Ink else Paper.copy(alpha = 0f))
                    .border(1.dp, if (isActive) Ink else LineSoft, CircleShape)
                    .clickable { onSelect(cat) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = cat,
                    fontFamily = Almarai,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Paper else Ink
                )
            }
        }
    }
}
