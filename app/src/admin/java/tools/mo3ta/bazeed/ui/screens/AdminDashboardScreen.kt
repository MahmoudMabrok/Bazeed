package tools.mo3ta.bazeed.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tools.mo3ta.bazeed.data.auth.AuthUser
import tools.mo3ta.bazeed.ui.theme.Ink

@Composable
fun AdminDashboardScreen(
    admin: AuthUser,
    onCreateUser: () -> Unit,
    onViewUsers: () -> Unit,
    onSignOut: () -> Unit,
) {
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

        Spacer(Modifier.height(16.dp))

        Button(onClick = onCreateUser, modifier = Modifier.fillMaxWidth()) {
            Text("إنشاء مستخدم جديد")
        }
        OutlinedButton(onClick = onViewUsers, modifier = Modifier.fillMaxWidth()) {
            Text("قائمة المستخدمين")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text("تسجيل الخروج")
        }
    }
}
