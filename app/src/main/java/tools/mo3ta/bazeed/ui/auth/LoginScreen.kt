package tools.mo3ta.bazeed.ui.auth

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tools.mo3ta.bazeed.data.Repositories
import tools.mo3ta.bazeed.data.repo.AuthException
import tools.mo3ta.bazeed.ui.theme.Ink
import tools.mo3ta.bazeed.ui.theme.Sand
import tools.mo3ta.bazeed.ui.theme.Terracotta

private const val TAG = "LoginScreen"

/**
 * Shared login UI. Both flavors render this when no one is signed in; routing
 * after a successful sign-in is decided by each flavor's app root, which reacts
 * to [Repositories.auth] `currentUser` changing.
 */
@Composable
fun LoginScreen(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<AuthException?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = MaterialTheme.typography.displaySmall, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = Ink)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = { Text("البريد الإلكتروني") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("كلمة المرور") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let { authEx ->
            Spacer(Modifier.height(12.dp))
            Text(text = authEx.userMessage, color = Terracotta, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                Log.d(TAG, "signIn attempt: $email")
                loading = true
                error = null
                scope.launch {
                    val result = Repositories.auth.signIn(email, password)
                    loading = false
                    result.fold(
                        onSuccess = { user ->
                            Log.d(TAG, "signed in as ${user.uid}")
                            // currentUser flips → app root re-routes
                        },
                        onFailure = { throwable ->
                            error = when (throwable) {
                                is AuthException -> {
                                    Log.w(TAG, "signIn failed: ${throwable::class.simpleName}")
                                    throwable
                                }
                                else -> {
                                    Log.e(TAG, "unexpected signIn throw", throwable)
                                    AuthException.Unknown(throwable)
                                }
                            }
                        },
                    )
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp), color = Sand, strokeWidth = 2.dp)
            } else {
                Text("دخول")
            }
        }
    }
}
