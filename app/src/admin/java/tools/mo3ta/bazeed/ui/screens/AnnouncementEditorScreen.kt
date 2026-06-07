package tools.mo3ta.bazeed.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tools.mo3ta.bazeed.data.Announcement
import tools.mo3ta.bazeed.data.AnnouncementType
import tools.mo3ta.bazeed.data.Repositories
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

private const val DEFAULT_EXPIRATION_DAYS = 7L
private const val MS_PER_DAY = 24L * 60 * 60 * 1000

/**
 * Create-or-edit form. If [existing] is null, this is create mode; otherwise edit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementEditorScreen(
    existing: Announcement?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isEdit = existing != null

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: AnnouncementType.Health) }
    var expirationMs by remember {
        mutableStateOf(existing?.expirationDate ?: (System.currentTimeMillis() + DEFAULT_EXPIRATION_DAYS * MS_PER_DAY))
    }

    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (isEdit) "تعديل إعلان" else "إعلان جديد",
            style = MaterialTheme.typography.headlineSmall,
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("العنوان") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("الوصف") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )

        ExposedDropdownMenuBox(
            expanded = typeMenuExpanded,
            onExpandedChange = { typeMenuExpanded = !typeMenuExpanded },
        ) {
            OutlinedTextField(
                readOnly = true,
                value = type.labelAr,
                onValueChange = {},
                label = { Text("النوع") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = typeMenuExpanded,
                onDismissRequest = { typeMenuExpanded = false },
            ) {
                AnnouncementType.entries.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t.labelAr) },
                        onClick = { type = t; typeMenuExpanded = false },
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { datePickerOpen = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("تاريخ الانتهاء: ${formatDate(expirationMs)}")
        }

        if (datePickerOpen) {
            val state = rememberDatePickerState(initialSelectedDateMillis = expirationMs)
            DatePickerDialog(
                onDismissRequest = { datePickerOpen = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { expirationMs = it }
                        datePickerOpen = false
                    }) { Text("تأكيد") }
                },
                dismissButton = {
                    TextButton(onClick = { datePickerOpen = false }) { Text("إلغاء") }
                },
            ) { DatePicker(state = state) }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Spacer(Modifier.height(8.dp))

        Button(
            enabled = !saving,
            onClick = {
                val v = validate(title, description, expirationMs)
                if (v != null) { error = v; return@Button }
                error = null
                saving = true
                scope.launch {
                    val ann = Announcement(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        title = title.trim(),
                        description = description.trim(),
                        type = type,
                        expirationDate = expirationMs,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    )
                    val result = if (isEdit) {
                        Repositories.content.update(ann.id, ann)
                    } else {
                        Repositories.content.create(ann).map { }
                    }
                    saving = false
                    result.fold(
                        onSuccess = { onSaved() },
                        onFailure = { error = it.message ?: "تعذّر الحفظ" },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (saving) "جاري الحفظ..." else "حفظ") }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("إلغاء")
        }
    }
}

private fun validate(title: String, description: String, expirationMs: Long): String? {
    if (title.isBlank()) return "العنوان مطلوب"
    if (description.isBlank()) return "الوصف مطلوب"
    if (expirationMs <= System.currentTimeMillis()) return "تاريخ الانتهاء يجب أن يكون في المستقبل"
    return null
}

private fun formatDate(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(ms))
