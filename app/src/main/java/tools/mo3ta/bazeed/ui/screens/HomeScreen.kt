package tools.mo3ta.bazeed.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tools.mo3ta.bazeed.data.SampleData
import tools.mo3ta.bazeed.ui.components.AnnouncementCard
import tools.mo3ta.bazeed.ui.components.BrandHeader
import tools.mo3ta.bazeed.ui.components.LocationPill
import tools.mo3ta.bazeed.ui.components.MonthlyServicePromoCard
import tools.mo3ta.bazeed.ui.components.SectionHeader

@Composable
fun HomeScreen(
    onMonthlyServiceTap: () -> Unit,
    onAnnouncementTap: (String) -> Unit,
    onSeeAllAnnouncements: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(scrollState)
            .padding(bottom = 96.dp)
    ) {
        BrandHeader(
            titleAr = "أهلًا بك",
            captionAr = "صيدلية بازيد · البحيرة"
        )
        Spacer(Modifier.height(4.dp))
        LocationPill(
            text = "دمنهور — شارع الجيش · مفتوحة الآن",
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(12.dp))

        MonthlyServicePromoCard(onTap = onMonthlyServiceTap)

        SectionHeader(
            titleAr = "إعلانات بازيد",
            actionAr = "عرض الكل"
        )
        SampleData.announcements.take(3).forEach { ann ->
            AnnouncementCard(
                announcement = ann,
                onClick = { onAnnouncementTap(ann.id) }
            )
        }
    }
}
