package com.example.movesmart.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.movesmart.model.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.movesmart.ui.auth.LoginScreen
import com.example.movesmart.ui.auth.SignUpScreen
import com.example.movesmart.ui.preferences.PreferencesScreen
import com.example.movesmart.ui.profile.ProfileScreen
import com.example.movesmart.ui.context.HomeScreen
import com.example.movesmart.ui.context.ActivityRecommendationScreen
import com.example.movesmart.ui.context.ActivityDetailsScreen
import com.example.movesmart.ui.context.WorkoutViewModel
import com.example.movesmart.ui.screens.*
import kotlinx.coroutines.launch

// Stable routes and callback-based screens are the integration contract for A/B/C/D.
object Routes {
    const val LOGIN = "login"
    const val SIGN_UP = "signup"
    const val HOME = "home"
    const val PREFERENCES = "preferences"
    const val DISCOVER = "discover"
    const val PLAN = "plan"
    const val LIVE = "live"
    const val SEARCH = "search"
    const val HISTORY = "history"
    const val RECORD = "record/{id}"
    const val REMINDERS = "reminders"
    const val STATISTICS = "statistics"
    const val PROFILE = "profile"
    fun record(id: Int) = "record/$id"
}
private data class TopLevel(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(
    TopLevel(Routes.HOME, "Home", Icons.Outlined.Home),
    TopLevel(Routes.HISTORY, "History", Icons.Outlined.History),
    TopLevel(Routes.STATISTICS, "Statistics", Icons.Outlined.BarChart),
    TopLevel(Routes.PROFILE, "Profile", Icons.Outlined.PersonOutline)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveSmartApp() {
    val nav = rememberNavController()
    val context: WorkoutViewModel = viewModel()
    var state by rememberSaveable(stateSaver = PrototypeState.StateSaver) { mutableStateOf(PrototypeState()) }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: Routes.LOGIN
    val auth = route == Routes.LOGIN || route == Routes.SIGN_UP
    val topLevel = tabs.any { it.route == route }
    val selectedTab = when (route) {
        Routes.REMINDERS, Routes.PREFERENCES -> Routes.PROFILE
        Routes.DISCOVER, Routes.PLAN, Routes.LIVE -> Routes.HOME
        Routes.RECORD -> Routes.HISTORY
        Routes.SEARCH -> nav.previousBackStackEntry?.destination?.route?.takeIf { previous -> tabs.any { it.route == previous } } ?: Routes.HOME
        else -> route
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun notify(message: String) { scope.launch { snackbar.showSnackbar(message) } }
    fun navigate(target: String) {
        snackbar.currentSnackbarData?.dismiss()
        nav.navigate(target) { launchSingleTop = true }
    }
    fun selectTab(target: String) {
        snackbar.currentSnackbarData?.dismiss()
        nav.navigate(target) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun startWorkout(title: String, minutes: Int = 30, intensity: String = state.preferences.intensity) {
        state.activeWorkout = title; state.activePlannedMinutes = minutes; state.activeIntensity = intensity
        navigate(Routes.LIVE)
    }
    val title = when (route) {
        Routes.LOGIN -> "MoveSmart"
        Routes.SIGN_UP -> "Sign up"
        Routes.HOME -> "MoveSmart"
        Routes.PREFERENCES -> "Preferences"
        Routes.DISCOVER -> "Find an activity"
        Routes.PLAN -> "Activity details"
        Routes.LIVE -> "Live workout"
        Routes.SEARCH -> "Search"
        Routes.HISTORY -> "History"
        Routes.RECORD -> if (entry?.arguments?.getInt("id") == -1) "Add workout" else "Edit workout"
        Routes.REMINDERS -> "Reminder settings"
        Routes.STATISTICS -> "Statistics"
        else -> "Profile"
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(title) }, navigationIcon = {
                if (!topLevel && route != Routes.LOGIN) IconButton({ nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            }, actions = {
                if (route == Routes.HOME || route == Routes.HISTORY) IconButton({ navigate(Routes.SEARCH) }) { Icon(Icons.Outlined.Search, contentDescription = "Search") }
                if (route == Routes.HOME) IconButton({ navigate(Routes.REMINDERS) }) { Icon(Icons.Outlined.NotificationsNone, contentDescription = "Reminder settings") }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        },
        bottomBar = {
            if (!auth && route != Routes.LIVE && route != Routes.PREFERENCES && route != Routes.RECORD) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(selected = selectedTab == tab.route, onClick = { selectTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) }, label = { Text(tab.label) })
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        NavHost(nav, startDestination = Routes.LOGIN, modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            composable(Routes.LOGIN) {
                fun enterDemo() {
                    nav.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true }; launchSingleTop = true }
                }
                LoginScreen(onLogin = { email -> state.email = email; enterDemo() },
                    onSignUp = { navigate(Routes.SIGN_UP) }, onExploreDemo = { enterDemo() })
            }
            composable(Routes.SIGN_UP) {
                SignUpScreen(onCreateAccount = { name, email ->
                    state.displayName = name; state.email = email
                    nav.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true }; launchSingleTop = true }
                    navigate(Routes.PREFERENCES)
                }, onLogin = { nav.popBackStack() })
            }
            composable(Routes.HOME) {
                HomeScreen(context, state.displayName, state.preferences.goal,
                    { navigate(Routes.DISCOVER) }, { navigate(Routes.PREFERENCES) })
            }
            composable(Routes.PREFERENCES) {
                PreferencesScreen(state.preferences.intensity, state.preferences.activities, state.preferences.goal,
                    state.preferences.weeklyMinutes) { intensity, activities, goal, minutes ->
                    state.preferences = Preferences(goal, intensity, minutes, activities)
                    context.updateIntensity(intensity)
                    nav.popBackStack(); notify("Preferences saved for this demo.")
                }
            }
            composable(Routes.DISCOVER) { ActivityRecommendationScreen(context) { navigate(Routes.PLAN) } }
            composable(Routes.PLAN) {
                ActivityDetailsScreen(context, onBack = { nav.popBackStack() }, onStart = {
                    val recommendation = context.recommendation
                    startWorkout(recommendation.title, recommendation.minutes, context.selectedIntensity)
                })
            }
            composable(Routes.LIVE) { LiveActivityScreen(state.activeWorkout, state.activeIntensity, state.activePlannedMinutes) {
                state.saveRecord(WorkoutRecord(state.nextId(), state.activeWorkout, sampleSessionMinutes(state.activeWorkout, state.activePlannedMinutes), state.today))
                nav.navigate(Routes.HISTORY) { popUpTo(Routes.HOME); launchSingleTop = true }
                notify("Session saved. Your statistics are updated.")
            } }
            composable(Routes.HISTORY) { HistoryScreen(state.records, { navigate(Routes.record(-1)) }, { navigate(Routes.record(it)) },
                { id -> state.records = state.records.filterNot { it.id == id }; notify("Workout deleted.") },
                { navigate(Routes.SEARCH) }, { selectTab(Routes.STATISTICS) }) }
            composable(Routes.SEARCH) { SearchScreen(state.records, { navigate(Routes.record(it)) }, { startWorkout(it) }) }
            composable(Routes.RECORD, arguments = listOf(navArgument("id") { type = NavType.IntType })) { recordEntry ->
                val id = recordEntry.arguments?.getInt("id") ?: -1
                val record = state.records.find { it.id == id }
                if (id != -1 && record == null) {
                    Column { Text("This workout is no longer in your history."); TextButton({ nav.popBackStack() }) { Text("Go back") } }
                } else WorkoutEditorScreen(record, state.today) { name, minutes, date ->
                    state.saveRecord(WorkoutRecord(if (id == -1) state.nextId() else id, name, minutes, date))
                    nav.popBackStack(); notify("Workout saved. Your statistics are updated.")
                }
            }
            composable(Routes.REMINDERS) { ReminderSettingsScreen(state.reminder) {
                state.reminder = it; notify("Reminder settings saved for this demo.")
            } }
            composable(Routes.STATISTICS) { StatisticsScreen(state.records, state.preferences.weeklyMinutes, state.today) { selectTab(Routes.HISTORY) } }
            composable(Routes.PROFILE) { ProfileScreen(
                intensity = state.preferences.intensity, activities = state.preferences.activities, goal = state.preferences.goal,
                displayName = state.displayName, email = state.email, weeklyMinutes = state.preferences.weeklyMinutes,
                reminderEnabled = state.reminder.enabled, onReminders = { navigate(Routes.REMINDERS) },
                onStatistics = { selectTab(Routes.STATISTICS) }, onEditPreferences = { navigate(Routes.PREFERENCES) }) {
                state = PrototypeState()
                context.reset()
                // Clear saved tab stacks as well as the active stack when leaving a session.
                tabs.forEach { nav.clearBackStack(it.route) }
                nav.navigate(Routes.LOGIN) { popUpTo(nav.graph.id) { inclusive = true }; launchSingleTop = true }
            } }
        }
    }
}
