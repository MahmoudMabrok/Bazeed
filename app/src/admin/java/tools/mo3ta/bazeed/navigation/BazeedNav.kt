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
import tools.mo3ta.bazeed.data.Announcement
import tools.mo3ta.bazeed.data.Repositories
import tools.mo3ta.bazeed.data.auth.AuthUser
import tools.mo3ta.bazeed.data.auth.UserRole
import tools.mo3ta.bazeed.ui.auth.LoginScreen
import tools.mo3ta.bazeed.ui.screens.AdminDashboardScreen
import tools.mo3ta.bazeed.ui.screens.AnnouncementEditorScreen
import tools.mo3ta.bazeed.ui.screens.AnnouncementListScreen
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

private sealed interface AdminRoute {
    data object Dashboard : AdminRoute
    data object CreateUser : AdminRoute
    data object Users : AdminRoute
    data object Announcements : AdminRoute
    data class AnnouncementEditor(val existing: Announcement?) : AdminRoute
}

@Composable
private fun AdminShell(admin: AuthUser, onSignOut: () -> Unit) {
    var route by remember { mutableStateOf<AdminRoute>(AdminRoute.Dashboard) }

    Scaffold(containerColor = Sand) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val r = route) {
                AdminRoute.Dashboard -> AdminDashboardScreen(
                    admin = admin,
                    onCreateUser = { route = AdminRoute.CreateUser },
                    onViewUsers = { route = AdminRoute.Users },
                    onManageAnnouncements = { route = AdminRoute.Announcements },
                    onSignOut = onSignOut,
                )

                AdminRoute.CreateUser -> CreateUserScreen(
                    onBack = { route = AdminRoute.Dashboard },
                )

                AdminRoute.Users -> UserListScreen(
                    onBack = { route = AdminRoute.Dashboard },
                )

                AdminRoute.Announcements -> AnnouncementListScreen(
                    onBack = { route = AdminRoute.Dashboard },
                    onCreate = { route = AdminRoute.AnnouncementEditor(existing = null) },
                    onEdit = { ann -> route = AdminRoute.AnnouncementEditor(existing = ann) },
                )

                is AdminRoute.AnnouncementEditor -> AnnouncementEditorScreen(
                    existing = r.existing,
                    onBack = { route = AdminRoute.Announcements },
                    onSaved = { route = AdminRoute.Announcements },
                )
            }
        }
    }
}
