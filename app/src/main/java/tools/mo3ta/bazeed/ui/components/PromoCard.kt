package tools.mo3ta.bazeed.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tools.mo3ta.bazeed.ui.theme.Almarai
import tools.mo3ta.bazeed.ui.theme.Amiri
import tools.mo3ta.bazeed.ui.theme.Green
import tools.mo3ta.bazeed.ui.theme.Ink
import tools.mo3ta.bazeed.ui.theme.Paper
import tools.mo3ta.bazeed.ui.theme.Saffron
import tools.mo3ta.bazeed.ui.theme.SaffronLight

@Composable
fun MonthlyServicePromoCard(
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Saffron, SaffronLight)
                )
            )
            .clickable { onTap() }
            .drawBehind {
                drawCircle(
                    color = Paper.copy(alpha = 0.18f),
                    radius = 90.dp.toPx(),
                    center = Offset(size.width + 24.dp.toPx(), -20.dp.toPx())
                )
            }
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Label chip
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Green.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "MONTHLY SERVICE · خدمة",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Green,
                    letterSpacing = 2.sp
                )
            }
            Text(
                text = "العلاج الشهري",
                fontFamily = Amiri,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Text(
                text = "خدمة شهرية لمرضى الأمراض المزمنة — اعرف المميزات وطريقة الاشتراك.",
                fontFamily = Almarai,
                fontSize = 13.sp,
                color = Ink.copy(alpha = 0.72f),
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "تفاصيل الخدمة",
                        fontFamily = Amiri,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaffronLight
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = SaffronLight,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = "للاشتراك زُر الصيدلية",
                    fontFamily = Almarai,
                    fontSize = 10.sp,
                    color = Ink.copy(alpha = 0.65f),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Helper sized spacer in case other components need it
@Composable
fun ChipDot() {
    Box(
        Modifier
            .width(6.dp)
            .height(6.dp)
            .clip(CircleShape)
            .background(Green)
    )
}
