package tools.mo3ta.bazeed.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tools.mo3ta.bazeed.data.Repositories
import tools.mo3ta.bazeed.data.auth.UserRole
import tools.mo3ta.bazeed.ui.theme.Green
import tools.mo3ta.bazeed.ui.theme.Ink
import tools.mo3ta.bazeed.ui.theme.Terracotta

@Composable
fun CreateUserScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun resetMessages() { error = null; success = null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("إنشاء مستخدم", style = MaterialTheme.typography.displaySmall, color = Ink)

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it; resetMessages() },
            label = { Text("الاسم") },
            singleLine = true,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; resetMessages() },
            label = { Text("البريد الإلكتروني") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; resetMessages() },
            label = { Text("كلمة مرور مؤقتة") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = isAdmin, onCheckedChange = { isAdmin = it; resetMessages() }, enabled = !loading)
            Spacer(Modifier.height(0.dp))
            Text("  منح صلاحية مدير", style = MaterialTheme.typography.bodyMedium, color = Ink)
        }

        error?.let {
            Text(it, color = Terracotta, style = MaterialTheme.typography.bodySmall)
        }
        success?.let {
            Text(it, color = Green, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                loading = true
                resetMessages()
                scope.launch {
                    val result = Repositories.users.createUser(
                        email = email,
                        password = password,
                        displayName = displayName,
                        role = if (isAdmin) UserRole.ADMIN else UserRole.USER,
                    )
                    loading = false
                    result.fold(
                        onSuccess = { u ->
                            success = "تم إنشاء حساب ${u.email}. كلمة المرور المؤقتة: $password"
                            displayName = ""; email = ""; password = ""; isAdmin = false
                        },
                        onFailure = { error = it.message ?: "تعذّر إنشاء الحساب" },
                    )
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (loading) "جارٍ الإنشاء…" else "إنشاء")
        }

        OutlinedButton(onClick = onBack, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            Text("رجوع")
        }
    }
}
