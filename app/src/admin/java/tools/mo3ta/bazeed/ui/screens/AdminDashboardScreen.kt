package tools.mo3ta.bazeed.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tools.mo3ta.bazeed.data.Repositories
import tools.mo3ta.bazeed.data.auth.AuthUser
import tools.mo3ta.bazeed.ui.theme.Ink

@Composable
fun AdminDashboardScreen(
    admin: AuthUser,
    onCreateUser: () -> Unit,
    onViewUsers: () -> Unit,
    onManageAnnouncements: () -> Unit,
    onSignOut: () -> Unit,
) {
    val openNow by Repositories.pharmacyStatus.openNow.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "لوحة الإدارة",
            style = MaterialTheme.typography.displaySmall,
            color = Ink,
        )
        Text(
            text = "مرحبًا، ${admin.displayName}",
            style = MaterialTheme.typography.bodyLarge,
            color = Ink,
        )

        Spacer(Modifier.height(8.dp))

        OpenNowToggle(
            openNow = openNow,
            onToggle = { next -> scope.launch { Repositories.pharmacyStatus.setOpen(next) } },
        )

        Spacer(Modifier.height(8.dp))

        Button(onClick = onCreateUser, modifier = Modifier.fillMaxWidth()) {
            Text("إنشاء مستخدم جديد")
        }
        OutlinedButton(onClick = onViewUsers, modifier = Modifier.fillMaxWidth()) {
            Text("قائمة المستخدمين")
        }
        OutlinedButton(onClick = onManageAnnouncements, modifier = Modifier.fillMaxWidth()) {
            Text("إدارة الإعلانات")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text("تسجيل الخروج")
        }
    }
}

@Composable
private fun OpenNowToggle(openNow: Boolean, onToggle: (Boolean) -> Unit) {
    val accent = if (openNow) Color(0xFF2E7D32) else Color(0xFFB71C1C)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "حالة الصيدلية",
                style = MaterialTheme.typography.labelMedium,
                color = Ink.copy(alpha = 0.6f),
            )
            Text(
                text = if (openNow) "مفتوحة الآن" else "مغلقة الآن",
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
        }
        Switch(
            checked = openNow,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = accent,
            ),
        )
    }
}
