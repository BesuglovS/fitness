package ru.besuglovs.fitness

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.besuglovs.fitness.ui.screens.CircuitScreen
import ru.besuglovs.fitness.ui.screens.ExerciseLibraryScreen
import ru.besuglovs.fitness.ui.screens.HistoryScreen
import ru.besuglovs.fitness.ui.screens.HomeScreen
import ru.besuglovs.fitness.ui.screens.ProgressScreen
import ru.besuglovs.fitness.ui.screens.SettingsScreen
import ru.besuglovs.fitness.ui.screens.WorkoutDetailScreen
import ru.besuglovs.fitness.ui.screens.WorkoutScreen
import ru.besuglovs.fitness.ui.theme.FitnessTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            FitnessTheme {
                FitnessRoot()
            }
        }
    }
}

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: @Composable () -> androidx.compose.ui.graphics.vector.ImageVector
)

private val mainTabs = listOf(
    BottomTab("home", "Главная") { Icons.Filled.Home },
    BottomTab("history", "История") { Icons.Outlined.History },
    BottomTab("progress", "Прогресс") { Icons.Filled.Insights },
    BottomTab("settings", "Настройки") { Icons.Filled.Settings }
)

@Composable
fun FitnessRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = mainTabs.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    mainTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon(), contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(
                top = 0.dp,
                bottom = innerPadding.calculateBottomPadding()
            )
        ) {
            composable("home") {
                HomeScreen(
                    onStartWorkout = { id, restSeconds -> navController.navigate("workout/$id?restSeconds=$restSeconds") },
                    onStartCircuit = { id, restSeconds -> navController.navigate("circuit/$id?restSeconds=$restSeconds") },
                    onResumeWorkout = { id, isCircuit ->
                        if (isCircuit) navController.navigate("circuit/$id")
                        else navController.navigate("workout/$id")
                    },
                    onOpenLibrary = { navController.navigate("library") },
                    onOpenWorkout = { id -> navController.navigate("detail/$id") }
                )
            }
            composable(
                "workout/{workoutId}?restSeconds={restSeconds}",
                arguments = listOf(
                    navArgument("workoutId") { type = androidx.navigation.NavType.LongType },
                    navArgument("restSeconds") {
                        type = androidx.navigation.NavType.IntType
                        defaultValue = 90
                    }
                )
            ) {
                WorkoutScreen(
                    onFinish = { navController.popBackStack("home", inclusive = false) },
                    onExit = { navController.popBackStack("home", inclusive = false) }
                )
            }
            composable(
                "circuit/{workoutId}?restSeconds={restSeconds}",
                arguments = listOf(
                    navArgument("workoutId") { type = androidx.navigation.NavType.LongType },
                    navArgument("restSeconds") {
                        type = androidx.navigation.NavType.IntType
                        defaultValue = 90
                    }
                )
            ) {
                CircuitScreen(
                    onFinish = { navController.popBackStack("home", inclusive = false) },
                    onExit = { navController.popBackStack("home", inclusive = false) }
                )
            }
            composable("library") {
                ExerciseLibraryScreen(onBack = { navController.popBackStack() })
            }
            composable("history") {
                HistoryScreen(onOpenWorkout = { id -> navController.navigate("detail/$id") })
            }
            composable(
                "detail/{workoutId}",
                arguments = listOf(navArgument("workoutId") { type = androidx.navigation.NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("workoutId") ?: 0L
                WorkoutDetailScreen(workoutId = id, onBack = { navController.popBackStack() })
            }
            composable("progress") {
                ProgressScreen()
            }
            composable("settings") {
                SettingsScreen()
            }
        }
    }
}
