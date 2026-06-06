package tools.mo3ta.bazeed.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import tools.mo3ta.bazeed.data.Repositories
import tools.mo3ta.bazeed.data.auth.AuthUser
import tools.mo3ta.bazeed.data.auth.UserRole
import tools.mo3ta.bazeed.ui.auth.LoginScreen
import tools.mo3ta.bazeed.ui.screens.AdminDashboardScreen
import tools.mo3ta.bazeed.ui.screens.CreateUserScreen
import tools.mo3ta.bazeed.ui.screens.NotAuthorizedScreen
import tools.mo3ta.bazeed.ui.screens.UserListScreen
import tools.mo3ta.bazeed.ui.theme.Sand

/** Admin flavor entry point: login → role gate → admin shell. */
@Composable
fun BazeedAppRoot() {
    val user by Repositories.auth.currentUser.collectAsState()
    val current = user
    when {
        current == null ->
            LoginScreen(title = "بازيد — الإدارة", subtitle = "دخول المسؤول")

        current.role != UserRole.ADMIN ->
            NotAuthorizedScreen(onSignOut = { Repositories.auth.signOut() })

        else ->
            AdminShell(admin = current, onSignOut = { Repositories.auth.signOut() })
    }
}

private enum class AdminRoute { Dashboard, CreateUser, Users }

@Composable
private fun AdminShell(admin: AuthUser, onSignOut: () -> Unit) {
    var route by remember { mutableStateOf(AdminRoute.Dashboard) }

    Scaffold(containerColor = Sand) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (route) {
                AdminRoute.Dashboard -> AdminDashboardScreen(
                    admin = admin,
                    onCreateUser = { route = AdminRoute.CreateUser },
                    onViewUsers = { route = AdminRoute.Users },
                    onSignOut = onSignOut,
                )

                AdminRoute.CreateUser -> CreateUserScreen(
                    onBack = { route = AdminRoute.Dashboard },
                )

                AdminRoute.Users -> UserListScreen(
                    onBack = { route = AdminRoute.Dashboard },
                )
            }
        }
    }
}
