package tools.mo3ta.bazeed.navigation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import tools.mo3ta.bazeed.data.SampleData
import tools.mo3ta.bazeed.messaging.FcmTopics
import tools.mo3ta.bazeed.ui.components.BazeedBottomNav
import tools.mo3ta.bazeed.ui.screens.AnnouncementsScreen
import tools.mo3ta.bazeed.ui.screens.ContactScreen
import tools.mo3ta.bazeed.ui.screens.HomeScreen
import tools.mo3ta.bazeed.ui.screens.MonthlyServiceScreen
import tools.mo3ta.bazeed.ui.theme.Sand

enum class BazeedDestination(val route: String) {
    Home("home"),
    Announcements("announcements"),
    MonthlyService("monthly"),
    Contact("contact");

    companion object {
        fun fromRoute(route: String?): BazeedDestination = entries.firstOrNull { it.route == route } ?: Home
    }
}

/** Customer flavor entry point: customer shell (no login required). */
@Composable
fun BazeedAppRoot() {
    LaunchedEffect(Unit) { FcmTopics.subscribeAnnouncements() }
    CustomerShell()
}

@Composable
private fun CustomerShell() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = BazeedDestination.fromRoute(backStack?.destination?.route)
    val context = LocalContext.current

    Scaffold(
        containerColor = Sand,
        bottomBar = {
            BazeedBottomNav(
                current = current,
                onSelect = { dest -> navController.navigateTab(dest) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopStart
        ) {
            NavHost(
                navController = navController,
                startDestination = BazeedDestination.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(BazeedDestination.Home.route) {
                    HomeScreen(
                        onMonthlyServiceTap = { navController.navigateTab(BazeedDestination.MonthlyService) },
                        onAnnouncementTap = { navController.navigateTab(BazeedDestination.Announcements) },
                        onSeeAllAnnouncements = { navController.navigateTab(BazeedDestination.Announcements) }
                    )
                }
                composable(BazeedDestination.Announcements.route) {
                    AnnouncementsScreen(onAnnouncementTap = { /* detail later */ })
                }
                composable(BazeedDestination.MonthlyService.route) {
                    MonthlyServiceScreen(
                        onBack = { navController.navigateTab(BazeedDestination.Home) },
                        onDirections = { context.openMaps(SampleData.pharmacy.streetAr) }
                    )
                }
                composable(BazeedDestination.Contact.route) {
                    ContactScreen(
                        onCall = { context.dial(SampleData.pharmacy.phone) },
                        onWhatsapp = { context.openWhatsapp(SampleData.pharmacy.phone) },
                        onDirections = { context.openMaps(SampleData.pharmacy.streetAr) }
                    )
                }
            }
        }
    }
}

private fun NavController.navigateTab(destination: BazeedDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun android.content.Context.dial(phone: String) {
    safeStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.replace(" ", "")}")))
}

private fun android.content.Context.openWhatsapp(phone: String) {
    val number = phone.replace(" ", "").let { if (it.startsWith("+")) it else "+20$it" }
    safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${number.removePrefix("+")}")))
}

private fun android.content.Context.openMaps(query: String) {
    val encoded = Uri.encode(query)
    safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")))
}

private fun android.content.Context.safeStart(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}
