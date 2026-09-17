package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repository.HttpServerRepository
import com.example.ui.navigation.BottomNavItem
import com.example.ui.navigation.Screen
import com.example.ui.screens.admin.AdminScreen
import com.example.ui.screens.admin.AdminViewModel
import com.example.ui.screens.files.FilesScreen
import com.example.ui.screens.files.FilesViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.media.MediaDetailsScreen
import com.example.ui.screens.media.MediaScreen
import com.example.ui.screens.media.MediaViewModel
import com.example.ui.screens.player.VlcMediaPlayerScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel

@Composable
fun DhilipHomeApp(
    navController: NavHostController = rememberNavController()
) {
    // Live HTTP repository connecting to configured DhilipHome server
    val repository = remember { HttpServerRepository }

    val homeViewModel = remember { HomeViewModel(repository) }
    val filesViewModel = remember { FilesViewModel(repository) }
    val mediaViewModel = remember { MediaViewModel(repository) }
    val adminViewModel = remember { AdminViewModel(repository) }
    val settingsViewModel = remember { SettingsViewModel() }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Only show bottom navigation on top-level screens
    val isTopLevelDestination = BottomNavItem.entries.any { it.route == currentRoute }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dhilip_home_scaffold"),
        bottomBar = {
            AnimatedVisibility(
                visible = isTopLevelDestination,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("bottom_navigation_bar")
                ) {
                    BottomNavItem.entries.forEach { item ->
                        val isSelected = currentRoute == item.route

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title
                                )
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_${item.title.lowercase()}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToFiles = {
                        navController.navigate(Screen.Files.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToMedia = {
                        navController.navigate(Screen.Media.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToAdmin = {
                        navController.navigate(Screen.Admin.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onMovieClick = { mediaId ->
                        navController.navigate(Screen.MediaDetails.createRoute(mediaId))
                    }
                )
            }

            composable(Screen.Files.route) {
                FilesScreen(
                    viewModel = filesViewModel,
                    onVideoClick = { url, title ->
                        navController.navigate(Screen.Player.createRoute(url, title))
                    }
                )
            }

            composable(Screen.Media.route) {
                MediaScreen(
                    viewModel = mediaViewModel,
                    onMediaClick = { mediaId ->
                        navController.navigate(Screen.MediaDetails.createRoute(mediaId))
                    }
                )
            }

            composable(
                route = Screen.MediaDetails.route,
                arguments = listOf(navArgument("mediaId") { type = NavType.StringType })
            ) { backStackEntry ->
                val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
                MediaDetailsScreen(
                    mediaId = mediaId,
                    viewModel = mediaViewModel,
                    onBackClick = { navController.popBackStack() },
                    onPlayClick = { url, title ->
                        navController.navigate(Screen.Player.createRoute(url, title))
                    }
                )
            }

            composable(
                route = Screen.Player.route,
                arguments = listOf(
                    navArgument("url") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("title") {
                        type = NavType.StringType
                        defaultValue = "Media Player"
                    }
                )
            ) { backStackEntry ->
                val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
                val encodedTitle = backStackEntry.arguments?.getString("title") ?: "Media Player"
                val url = java.net.URLDecoder.decode(encodedUrl, "UTF-8")
                val title = java.net.URLDecoder.decode(encodedTitle, "UTF-8")

                VlcMediaPlayerScreen(
                    videoUrl = url,
                    videoTitle = title,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Admin.route) {
                AdminScreen(viewModel = adminViewModel)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }
}
