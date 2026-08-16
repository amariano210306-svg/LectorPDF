package com.example.lectorpdf.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navOptions
import com.example.lectorpdf.ui.screens.BookDetailsScreen
import com.example.lectorpdf.ui.screens.FilesScreen
import com.example.lectorpdf.ui.screens.HomeScreen
import com.example.lectorpdf.ui.screens.LibraryScreen
import com.example.lectorpdf.ui.screens.SettingsScreen
import com.example.lectorpdf.ui.screens.StatsScreen
import com.example.lectorpdf.ui.viewmodel.AppViewModelProvider
import com.example.lectorpdf.ui.viewmodel.BookDetailsViewModel
import com.example.lectorpdf.ui.viewmodel.FilesViewModel
import com.example.lectorpdf.ui.viewmodel.HomeViewModel
import com.example.lectorpdf.ui.viewmodel.LibraryViewModel
import com.example.lectorpdf.ui.viewmodel.SettingsViewModel
import com.example.lectorpdf.ui.viewmodel.StatsViewModel

private enum class TopDestination(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Inicio", Icons.Filled.Home),
    LIBRARY("library", "Biblioteca", Icons.Filled.AutoStories),
    FILES("files", "Archivos", Icons.Filled.Folder),
    STATS("stats", "Estadísticas", Icons.Filled.BarChart),
    SETTINGS("settings", "Ajustes", Icons.Filled.Settings),
}

@Composable
fun LectorApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = TopDestination.entries.any { it.route == currentRoute }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopDestination.HOME.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopDestination.HOME.route) {
                val vm: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
                HomeScreen(
                    vm,
                    onBookClick = { navController.navigate("book/$it") },
                    onOpenLibrary = { navController.navigate(TopDestination.LIBRARY.route) { launchSingleTop = true } },
                    onImport = { navController.navigate(TopDestination.FILES.route) { launchSingleTop = true } },
                )
            }
            composable(TopDestination.LIBRARY.route) {
                val vm: LibraryViewModel = viewModel(factory = AppViewModelProvider.Factory)
                LibraryScreen(
                    vm,
                    onBookClick = { navController.navigate("book/$it") },
                    onImport = { navController.navigate(TopDestination.FILES.route) { launchSingleTop = true } },
                )
            }
            composable(TopDestination.FILES.route) {
                val vm: FilesViewModel = viewModel(factory = AppViewModelProvider.Factory)
                FilesScreen(vm)
            }
            composable(TopDestination.STATS.route) {
                val vm: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory)
                StatsScreen(vm)
            }
            composable(TopDestination.SETTINGS.route) {
                val vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
                SettingsScreen(vm)
            }
            composable(
                route = "book/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
            ) {
                val vm: BookDetailsViewModel = viewModel(factory = AppViewModelProvider.Factory)
                BookDetailsScreen(vm, navController::popBackStack)
            }
        }
    }
}
