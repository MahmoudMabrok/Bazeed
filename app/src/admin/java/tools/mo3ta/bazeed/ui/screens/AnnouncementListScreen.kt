package tools.mo3ta.bazeed.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tools.mo3ta.bazeed.data.Announcement
import tools.mo3ta.bazeed.data.Repositories
import tools.mo3ta.bazeed.data.TimeAgo

@Composable
fun AnnouncementListScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Announcement) -> Unit,
) {
    val items by Repositories.content.announcements.collectAsState()
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<Announcement?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Outlined.Add, contentDescription = "إعلان جديد")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("إدارة الإعلانات", style = MaterialTheme.typography.headlineSmall)

            OutlinedButton(onClick = onBack) { Text("رجوع") }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("لا توجد إعلانات. اضغط + لإضافة إعلان جديد.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.id }) { ann ->
                        AdminAnnouncementRow(
                            announcement = ann,
                            onTap = { onEdit(ann) },
                            onDelete = { pendingDelete = ann },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { ann ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف الإعلان؟") },
            text = { Text("سيتم حذف \"${ann.title}\" نهائياً.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch { Repositories.content.delete(ann.id) }
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") }
            },
        )
    }
}

@Composable
private fun AdminAnnouncementRow(
    announcement: Announcement,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onTap)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(announcement.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                "${announcement.type.labelAr} · ${TimeAgo.format(announcement.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "حذف")
        }
    }
}
