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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tools.mo3ta.bazeed.data.SampleData
import tools.mo3ta.bazeed.ui.components.BenefitCard
import tools.mo3ta.bazeed.ui.components.ServiceHero
import tools.mo3ta.bazeed.ui.components.VisitPharmacyCard
import tools.mo3ta.bazeed.ui.theme.Amiri
import tools.mo3ta.bazeed.ui.theme.Ink
import tools.mo3ta.bazeed.ui.theme.InkMute
import tools.mo3ta.bazeed.ui.theme.LineSoft
import tools.mo3ta.bazeed.ui.theme.Mono
import tools.mo3ta.bazeed.ui.theme.Paper2

@Composable
fun MonthlyServiceScreen(
    onBack: () -> Unit,
    onDirections: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            ServiceHeader(onBack = onBack)
            Spacer(Modifier.height(4.dp))
        }
        item { ServiceHero() }
        item {
            BenefitsHeader()
        }
        item { BenefitsGrid() }
        item { Spacer(Modifier.height(4.dp)) }
        item {
            VisitPharmacyCard(
                info = SampleData.pharmacy,
                onDirections = onDirections
            )
        }
    }
}

@Composable
private fun ServiceHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .border(1.dp, LineSoft, CircleShape)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "رجوع",
                tint = Ink,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "MONTHLY SERVICE",
                fontFamily = Mono,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = InkMute,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "العلاج الشهري",
                fontFamily = Amiri,
                fontSize = 18.sp,
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
                imageVector = Icons.Outlined.MoreHoriz,
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun BenefitsHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "المميزات",
            fontFamily = Amiri,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "BENEFITS",
            fontFamily = Mono,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = InkMute,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun BenefitsGrid() {
    val benefits = SampleData.benefits
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 2x2 grid built with two rows
        for (rowIndex in 0 until (benefits.size + 1) / 2) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val leftIndex = rowIndex * 2
                val rightIndex = leftIndex + 1
                BenefitCard(
                    benefit = benefits[leftIndex],
                    modifier = Modifier.weight(1f)
                )
                if (rightIndex < benefits.size) {
                    BenefitCard(
                        benefit = benefits[rightIndex],
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
