package com.mbox.stream.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mbox.stream.AppViewModel
import com.mbox.stream.magnet.MagnetScreen
import com.mbox.stream.ui.DetailScreen
import com.mbox.stream.ui.DiscoverScreen
import com.mbox.stream.ui.HomeScreen
import com.mbox.stream.ui.LibraryScreen
import com.mbox.stream.ui.SettingsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Default.Home),
    Tab("discover", "Discover", Icons.Default.Search),
    Tab("library", "Library", Icons.Default.VideoLibrary),
    Tab("settings", "Settings", Icons.Default.Settings)
)

private fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun MboxRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { nav.goTab(t.route) },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") {
                HomeScreen(
                    vm,
                    onOpen = { nav.navigate("detail/$it?autoplay=false") },
                    onPlay = { nav.navigate("detail/$it?autoplay=true") },
                    onGenre = { g -> vm.setGenre(g); nav.goTab("discover") }
                )
            }
            composable("discover") { DiscoverScreen(vm, onOpen = { nav.navigate("detail/$it?autoplay=false") }) }
            composable("library") {
                LibraryScreen(
                    vm,
                    onOpen = { nav.navigate("detail/$it?autoplay=false") },
                    onMagnet = { nav.navigate("magnet") }
                )
            }
            composable("settings") { SettingsScreen(vm) }
            composable(
                "detail/{id}?autoplay={autoplay}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("autoplay") { type = NavType.BoolType; defaultValue = false }
                )
            ) { entry ->
                DetailScreen(
                    vm,
                    id = entry.arguments?.getString("id") ?: "",
                    autoplay = entry.arguments?.getBoolean("autoplay") ?: false,
                    onBack = { nav.popBackStack() }
                )
            }
            composable("magnet") { MagnetScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
