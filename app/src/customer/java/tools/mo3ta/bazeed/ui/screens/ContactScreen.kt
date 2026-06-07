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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Facebook
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tools.mo3ta.bazeed.data.Repositories
import tools.mo3ta.bazeed.data.SampleData
import tools.mo3ta.bazeed.ui.theme.Almarai
import tools.mo3ta.bazeed.ui.theme.Amiri
import tools.mo3ta.bazeed.ui.theme.Green
import tools.mo3ta.bazeed.ui.theme.Ink
import tools.mo3ta.bazeed.ui.theme.InkMute
import tools.mo3ta.bazeed.ui.theme.LineSoft
import tools.mo3ta.bazeed.ui.theme.Mono
import tools.mo3ta.bazeed.ui.theme.Paper
import tools.mo3ta.bazeed.ui.theme.Paper2
import tools.mo3ta.bazeed.ui.theme.SaffronLight
import tools.mo3ta.bazeed.ui.theme.Terracotta

@Composable
fun ContactScreen(
    onCall: () -> Unit,
    onWhatsapp: () -> Unit,
    onDirections: () -> Unit,
    onFacebook: () -> Unit,
    modifier: Modifier = Modifier
) {
    val info = SampleData.pharmacy
    val openNow by Repositories.pharmacyStatus.openNow.collectAsState()
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(scrollState)
            .padding(bottom = 96.dp)
    ) {
        Header()
        HeroCard(openNow = openNow)
        Spacer(Modifier.height(8.dp))
        ContactRow(
            icon = Icons.Outlined.Phone,
            labelAr = "اتصال هاتفي",
            valueEn = info.phone,
            valueAr = "اتصل بالصيدلي الآن",
            accent = Green,
            onClick = onCall
        )
        ContactRow(
            icon = Icons.Outlined.Sms,
            labelAr = "واتساب",
            valueEn = info.phone,
            valueAr = "أرسل رسالة فورية",
            accent = Terracotta,
            onClick = onWhatsapp
        )
        ContactRow(
            icon = Icons.Outlined.LocationOn,
            labelAr = "العنوان",
            valueEn = info.streetAr,
            valueAr = "احصل على الاتجاهات",
            accent = Ink,
            onClick = onDirections
        )
        ContactRow(
            icon = Icons.Outlined.AccessTime,
            labelAr = "ساعات العمل",
            valueEn = info.hoursAr,
            valueAr = "السبت — الخميس",
            accent = SaffronLight,
            onClick = {}
        )
        ContactRow(
            icon = Icons.Outlined.Facebook,
            labelAr = "فيسبوك",
            valueEn = "Bazeed Pharmacy",
            valueAr = "تابعنا على فيسبوك",
            accent = Terracotta,
            onClick = onFacebook
        )
    }
}

@Composable
private fun Header() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = "CONTACT · تواصل",
            fontFamily = Mono,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = InkMute,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "تواصل مع الصيدلية",
            fontFamily = Amiri,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun HeroCard(openNow: Boolean) {
    val badgeColor = if (openNow) Green else Terracotta
    val badgeText = if (openNow) "مفتوحة الآن" else "مغلقة الآن"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Paper2)
            .border(1.dp, LineSoft, RoundedCornerShape(22.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = SampleData.pharmacy.nameAr,
                fontFamily = Amiri,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Text(
                text = SampleData.pharmacy.cityAr,
                fontFamily = Almarai,
                fontSize = 13.sp,
                color = InkMute
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor.copy(alpha = 0.10f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(badgeColor)
                )
                Text(
                    text = badgeText,
                    fontFamily = Almarai,
                    fontSize = 11.sp,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ContactRow(
    icon: ImageVector,
    labelAr: String,
    valueEn: String,
    valueAr: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Paper)
            .border(1.dp, LineSoft, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (accent == SaffronLight) Ink else Paper,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = labelAr,
                fontFamily = Mono,
                fontSize = 9.sp,
                color = InkMute,
                letterSpacing = 1.6.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = valueEn,
                fontFamily = Amiri,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Text(
                text = valueAr,
                fontFamily = Almarai,
                fontSize = 11.sp,
                color = InkMute
            )
        }
    }
}
