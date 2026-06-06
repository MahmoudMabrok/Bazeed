package tools.mo3ta.bazeed.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tools.mo3ta.bazeed.data.Repositories
import tools.mo3ta.bazeed.data.auth.UserRole
import tools.mo3ta.bazeed.ui.theme.Ink
import tools.mo3ta.bazeed.ui.theme.InkMute

@Composable
fun UserListScreen(onBack: () -> Unit) {
    val users by Repositories.users.users.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("المستخدمون (${users.size})", style = MaterialTheme.typography.displaySmall, color = Ink)

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(users, key = { it.uid }) { user ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(user.displayName, style = MaterialTheme.typography.titleMedium, color = Ink)
                        Text(user.email, style = MaterialTheme.typography.bodyMedium, color = InkMute)
                        Text(
                            text = if (user.role == UserRole.ADMIN) "مدير" else "مستخدم",
                            style = MaterialTheme.typography.labelLarge,
                            color = InkMute,
                        )
                    }
                }
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("رجوع")
        }
    }
}
