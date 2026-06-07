package tools.mo3ta.bazeed.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tools.mo3ta.bazeed.data.AnnouncementType
import tools.mo3ta.bazeed.data.Repositories
import tools.mo3ta.bazeed.data.TimeAgo
import tools.mo3ta.bazeed.ui.theme.Almarai
import tools.mo3ta.bazeed.ui.theme.Amiri
import tools.mo3ta.bazeed.ui.theme.Green
import tools.mo3ta.bazeed.ui.theme.Ink
import tools.mo3ta.bazeed.ui.theme.InkMute
import tools.mo3ta.bazeed.ui.theme.InkSoft
import tools.mo3ta.bazeed.ui.theme.Line
import tools.mo3ta.bazeed.ui.theme.Mono
import tools.mo3ta.bazeed.ui.theme.Paper
import tools.mo3ta.bazeed.ui.theme.Saffron
import tools.mo3ta.bazeed.ui.theme.Sand
import tools.mo3ta.bazeed.ui.theme.SandDeep
import tools.mo3ta.bazeed.ui.theme.Terracotta

/**
 * Announcement detail — "Sand Card" direction.
 * One floating paper card on a warm sand wash; a single saffron top-edge
 * line hints at character without re-stating the type. Content only.
 */
@Composable
fun AnnouncementDetailScreen(
    announcementId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val all by Repositories.content.announcements.collectAsState()
    val announcement = all.firstOrNull { it.id == announcementId }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Sand, SandDeep)))
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp),
        ) {
            TopBar(
                onBack = onBack,
                typeLabelAr = announcement?.type?.labelAr,
            )

            if (announcement == null) {
                MissingState()
                return@Column
            }

            val reveal = remember { MutableTransitionState(false).apply { targetState = true } }

            AnimatedVisibility(
                visibleState = reveal,
                enter = fadeIn(animationSpec = tween(durationMillis = 500, delayMillis = 80)) +
                        slideInVertically(
                            animationSpec = tween(durationMillis = 500, delayMillis = 80),
                            initialOffsetY = { it / 10 },
                        ),
            ) {
                ContentCard(
                    title = announcement.title,
                    description = announcement.description,
                    timeAgo = TimeAgo.format(announcement.createdAt),
                    topEdgeColor = topEdgeColor(announcement.type),
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    onBack: () -> Unit,
    typeLabelAr: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.40f))
                .border(1.dp, Line, CircleShape)
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "رجوع",
                tint = Ink,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = if (typeLabelAr != null) "$typeLabelAr · BAZEED" else "إعلان · BAZEED",
            fontFamily = Mono,
            fontSize = 9.5.sp,
            color = InkMute,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.4.sp,
        )
    }
}

@Composable
private fun ContentCard(
    title: String,
    description: String,
    timeAgo: String,
    topEdgeColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Ink,
                spotColor = Ink,
            )
            .clip(RoundedCornerShape(22.dp))
            .background(Paper),
    ) {
        // top-edge line, fading at the sides — colored to match the announcement type
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, topEdgeColor, Color.Transparent)
                    )
                )
                .align(Alignment.TopCenter)
        )

        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 26.dp)
        ) {
            // small asterism ornament — sets the editorial tone without an icon
            Text(
                text = "⁂",
                fontFamily = Amiri,
                fontStyle = FontStyle.Italic,
                fontSize = 22.sp,
                color = InkMute,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Text(
                text = title,
                fontFamily = Amiri,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Bold,
                fontSize = 25.sp,
                lineHeight = 38.sp,
                color = Ink,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "نُشر $timeAgo",
                fontFamily = Almarai,
                fontSize = 11.sp,
                color = InkMute,
            )

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(2.dp)
                    .background(Terracotta)
            )

            Spacer(Modifier.height(16.dp))

            // Body — split on blank-line separators if admin authored paragraphs;
            // otherwise render as one continuous block.
            val paragraphs = remember(description) {
                description.split(Regex("\\n\\s*\\n"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }
            paragraphs.forEachIndexed { index, paragraph ->
                Text(
                    text = paragraph,
                    fontFamily = Almarai,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.5.sp,
                    lineHeight = 28.sp,
                    color = InkSoft,
                )
                if (index < paragraphs.lastIndex) Spacer(Modifier.height(10.dp))
            }
        }
    }
}

private fun topEdgeColor(type: AnnouncementType): Color = when (type) {
    AnnouncementType.Health -> Green
    AnnouncementType.Tip -> Green
    AnnouncementType.Alert -> Terracotta
    AnnouncementType.Offer -> Saffron
}

@Composable
private fun MissingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(60.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "—",
                fontFamily = Amiri,
                fontSize = 28.sp,
                color = InkMute,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "هذا الإعلان غير متاح",
                fontFamily = Almarai,
                fontSize = 14.sp,
                color = InkMute,
            )
        }
    }
}
