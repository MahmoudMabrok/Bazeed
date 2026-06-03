package tools.mo3ta.bazeed.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tools.mo3ta.bazeed.ui.theme.Almarai
import tools.mo3ta.bazeed.ui.theme.Amiri
import tools.mo3ta.bazeed.ui.theme.Green
import tools.mo3ta.bazeed.ui.theme.Mono
import tools.mo3ta.bazeed.ui.theme.Paper
import tools.mo3ta.bazeed.ui.theme.SaffronLight

@Composable
fun ServiceHero(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Green)
            .drawBehind {
                drawCircle(
                    color = SaffronLight.copy(alpha = 0.22f),
                    radius = 90.dp.toPx(),
                    center = Offset(-30.dp.toPx(), size.height + 30.dp.toPx())
                )
            }
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Paper.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "MONTHLY · خدمة",
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = SaffronLight,
                    letterSpacing = 2.sp
                )
            }
            Text(
                text = "أدويتك المزمنة كل شهر، بأمان.",
                fontFamily = Amiri,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Paper,
                lineHeight = 26.sp
            )
            Text(
                text = "خدمة شهرية لمرضى الضغط، السكر، والقلب — يُراجع الصيدلي روشتتك ويُحضّر لك العلاج.",
                fontFamily = Almarai,
                fontSize = 12.sp,
                color = Paper.copy(alpha = 0.72f),
                lineHeight = 19.sp
            )
        }
    }
}
