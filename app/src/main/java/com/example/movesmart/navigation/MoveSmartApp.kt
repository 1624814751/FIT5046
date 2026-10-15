package com.example.movesmart.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.movesmart.model.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import com.example.movesmart.data.local.MoveSmartDatabase
import com.example.movesmart.data.local.RoomWorkoutRepository
import com.example.movesmart.shared.DataSource
import com.example.movesmart.shared.SensorRepository
import com.example.movesmart.shared.WorkoutRecord as StoredWorkoutRecord
import com.example.movesmart.sensor.AndroidSensorAssetReader
import com.example.movesmart.sensor.CsvSensorRepository
import com.example.movesmart.sensor.LiveWorkoutViewModel
import com.example.movesmart.sensor.SensorDatasets
import com.example.movesmart.workout.WorkoutRecordsViewModel
import com.example.movesmart.workout.WorkoutRecordsState
import com.example.movesmart.ui.components.ScreenColumn
import java.time.LocalDate
import java.time.ZoneId
import com.example.movesmart.ui.auth.AuthViewModel
import com.example.movesmart.ui.auth.LoginScreen
import com.example.movesmart.ui.auth.SignUpScreen
import com.example.movesmart.ui.preferences.PreferencesScreen
import com.example.movesmart.ui.preferences.PreferencesViewModel
import com.example.movesmart.ui.preferences.intensityToText
import com.example.movesmart.ui.planning.*
import com.example.movesmart.ui.weather.WeatherViewModel
import com.example.movesmart.shared.UserAccount
import com.example.movesmart.worker.WeatherWork
import com.example.movesmart.ui.profile.ProfileScreen
import com.example.movesmart.ui.context.HomeScreen
import com.example.movesmart.ui.context.ActivityRecommendationScreen
import com.example.movesmart.ui.context.ActivityDetailsScreen
import com.example.movesmart.ui.context.WorkoutViewModel
import com.example.movesmart.ui.screens.*
import com.example.movesmart.reminder.ReminderRoute
import com.example.movesmart.reminder.ReminderService
import com.example.movesmart.reminder.toForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Stable routes and callback-based screens are the integration contract for A/B/C/D.
object Routes {
    const val LOGIN = "login"
    const val SIGN_UP = "signup"
    const val HOME = "home"
    const val PREFERENCES = "preferences"
    const val DISCOVER = "discover"
    const val PLAN = "plan"
    const val DETAILS = "details/{recommendationId}"
    fun details(id: String) = "details/${android.net.Uri.encode(id)}"
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

@Composable
fun MoveSmartApp(
    openReminderRequest: Int = 0,
    workoutRepository: com.example.movesmart.shared.WorkoutRepository? = null,
    sensorRepository: SensorRepository? = null,
    workoutUserId: String = "local-demo",
    authViewModelOverride: AuthViewModel? = null,
    preferencesViewModelOverride: PreferencesViewModel? = null
) {
    val androidContext = LocalContext.current
    val authViewModel: AuthViewModel = authViewModelOverride ?: viewModel(factory = AuthViewModel.factory(androidContext))
    val preferencesViewModel: PreferencesViewModel = preferencesViewModelOverride ?:
        viewModel(factory = PreferencesViewModel.factory(androidContext))
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    var demo by rememberSaveable { mutableStateOf(false) }
    // A real account always takes precedence over an old demo flag.
    val isDemoMode = currentUser == null && demo
    val sessionUserId = currentUser?.userId ?: if (isDemoMode) workoutUserId else null
    val reminders = remember(androidContext) { ReminderService.get(androidContext) }
    var reminderError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sessionUserId) {
        val result = if (sessionUserId == null) reminders.logout() else reminders.activate(sessionUserId)
        reminderError = result.exceptionOrNull()?.let { "Could not restore reminders. Open Reminder settings to retry." }
        WeatherWork(androidContext).stopWeatherWork()
    }
    LaunchedEffect(currentUser?.userId) { if (currentUser != null) demo = false }
    // Discard navigation stacks and remembered drafts as soon as identity changes.
    key(sessionUserId) {
        SessionApp(openReminderRequest, workoutRepository, sessionUserId ?: "signed-out",
            currentUser, isDemoMode, { demo = it }, authViewModel, preferencesViewModel, reminderError, sensorRepository)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionApp(
    openReminderRequest: Int,
    workoutRepository: com.example.movesmart.shared.WorkoutRepository?,
    workoutUserId: String,
    currentUser: UserAccount?,
    isDemoMode: Boolean,
    changeDemoMode: (Boolean) -> Unit,
    authViewModel: AuthViewModel,
    preferencesViewModel: PreferencesViewModel,
    reminderError: String?,
    sensorRepository: SensorRepository?
) {
    val nav = rememberNavController()
    val context: WorkoutViewModel = viewModel(key = "demo-plan:$workoutUserId")
    val androidContext = LocalContext.current
    val firstRoute = if (currentUser != null || isDemoMode) Routes.HOME else Routes.LOGIN
    var state by rememberSaveable(stateSaver = PrototypeState.StateSaver) { mutableStateOf(PrototypeState()) }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: Routes.LOGIN
    val auth = route == Routes.LOGIN || route == Routes.SIGN_UP
    val topLevel = tabs.any { it.route == route }
    val selectedTab = when (route) {
        Routes.REMINDERS, Routes.PREFERENCES -> Routes.PROFILE
        Routes.DISCOVER, Routes.PLAN, Routes.DETAILS, Routes.LIVE -> Routes.HOME
        Routes.RECORD -> Routes.HISTORY
        Routes.SEARCH -> nav.previousBackStackEntry?.destination?.route?.takeIf { previous -> tabs.any { it.route == previous } } ?: Routes.HOME
        else -> route
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(reminderError) { reminderError?.let { snackbar.showSnackbar(it) } }
    val scope = rememberCoroutineScope()
    val repository = remember(androidContext, workoutRepository) {
        workoutRepository ?: RoomWorkoutRepository(MoveSmartDatabase.get(androidContext))
    }
    val sensors = remember(androidContext, sensorRepository) {
        sensorRepository ?: CsvSensorRepository(AndroidSensorAssetReader(androidContext))
    }
    // Firebase records and explicit demo records keep separate ownership scopes.
    val workouts: WorkoutRecordsViewModel = viewModel(key = "workouts:$workoutUserId",
        factory = WorkoutRecordsViewModel.factory(repository, workoutUserId))
    DisposableEffect(workouts) {
        workouts.retry()
        onDispose { workouts.stopObserving() }
    }
    val recordsState by workouts.state.collectAsStateWithLifecycle()
    val saving by workouts.isSaving.collectAsStateWithLifecycle()
    val records = remember(recordsState.records) {
        recordsState.records.map { WorkoutRecord(it.id, it.title, it.minutes, it.date) }
    }
    val today = LocalDate.now()
    val reminders = remember(androidContext) { ReminderService.get(androidContext) }
    val reminderUserId = workoutUserId
    val reminderFlow = remember(reminders, reminderUserId) { reminders.store.observe(reminderUserId) }
    val savedReminder by reminderFlow.collectAsState(initial = reminders.store.read(reminderUserId))
    var handledReminderRequest by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(savedReminder, state) { state.reminder = savedReminder?.toForm() ?: ReminderSettings(enabled = false) }
    LaunchedEffect(openReminderRequest, auth) {
        if (!auth && openReminderRequest > handledReminderRequest) {
            handledReminderRequest = openReminderRequest
            nav.navigate(Routes.REMINDERS) { launchSingleTop = true }
        }
    }
    val preferencesState by preferencesViewModel.state.collectAsStateWithLifecycle()
    val livePreferences = preferencesState.preferences?.takeIf { it.userId == currentUser?.userId }
    LaunchedEffect(currentUser, livePreferences) {
        currentUser?.let { state.displayName = it.displayName; state.email = it.email }
        livePreferences?.let {
            state.preferences = Preferences(it.goal, intensityToText(it.preferredIntensity), it.weeklyGoalMinutes, it.activityNames)
        }
    }
    val weatherViewModel: WeatherViewModel = viewModel(key = "weather:$workoutUserId",
        factory = WeatherViewModel.factory(androidContext))
    val currentWeather by weatherViewModel.state.collectAsStateWithLifecycle()
    DisposableEffect(weatherViewModel) { onDispose { weatherViewModel.stopObserving() } }
    val planSession: PlanningViewModel = viewModel(key = "planning:$workoutUserId",
        factory = PlanningViewModel.factory(androidContext, workoutUserId))
    DisposableEffect(planSession) {
        onDispose { planSession.cancelPending() }
    }
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
        Routes.PLAN, Routes.DETAILS -> "Activity details"
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
                if (!topLevel && route != Routes.LOGIN) IconButton({ nav.popBackStack() }, enabled = !saving) {
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
                        NavigationBarItem(selected = selectedTab == tab.route, onClick = { selectTab(tab.route) }, enabled = !saving,
                            icon = { Icon(tab.icon, contentDescription = null) }, label = { Text(tab.label) })
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        NavHost(
            nav,
            startDestination = firstRoute,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
        ) {
            composable(Routes.LOGIN) {
                fun enterApp(demoMode: Boolean) {
                    if (!demoMode && authViewModel.currentUser.value == null) return
                    changeDemoMode(demoMode)
                    nav.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true }; launchSingleTop = true }
                }
                LoginScreen(
                    onLogin = { email -> state.email = email; enterApp(demoMode = false) },
                    onSignUp = { navigate(Routes.SIGN_UP) },
                    onExploreDemo = { enterApp(demoMode = true) },
                    authViewModel = authViewModel
                )
            }
            composable(Routes.SIGN_UP) {
                SignUpScreen(onCreateAccount = { name, email ->
                    changeDemoMode(false)
                    state.displayName = name; state.email = email
                    nav.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true }; launchSingleTop = true }
                    navigate(Routes.PREFERENCES)
                }, onLogin = { nav.popBackStack() }, authViewModel = authViewModel)
            }
            composable(Routes.HOME) {
                if (isDemoMode) HomeScreen(context, state.displayName, state.preferences.goal,
                    { navigate(Routes.DISCOVER) }, { navigate(Routes.PREFERENCES) })
                else ConnectedHome(state.displayName, livePreferences, preferencesState.isLoading,
                    preferencesState.errorMessage, currentWeather, { navigate(Routes.DISCOVER) }, { navigate(Routes.PREFERENCES) })
            }
            composable(Routes.PREFERENCES) {
                PreferencesScreen(state.preferences.intensity, state.preferences.activities, state.preferences.goal,
                    state.preferences.weeklyMinutes, preferencesViewModelOverride = preferencesViewModel) { intensity, activities, goal, minutes ->
                    state.preferences = Preferences(goal, intensity, minutes, activities)
                    context.updateIntensity(intensity)
                    nav.popBackStack(); notify(if (isDemoMode) "Preferences saved for this demo." else "Preferences saved.")
                }
            }
            composable(Routes.DISCOVER) {
                if (isDemoMode) ActivityRecommendationScreen(context) { navigate(Routes.PLAN) }
                else PlanningScreen(planSession, weatherViewModel, livePreferences) { id -> navigate(Routes.details(id)) }
            }
            composable(Routes.PLAN) {
                if (isDemoMode) ActivityDetailsScreen(context, onBack = { nav.popBackStack() },
                    onReminder = { navigate(Routes.REMINDERS) }, onStart = {
                        val recommendation = context.recommendation
                        startWorkout(recommendation.title, recommendation.minutes, context.selectedIntensity)
                    })
                else Text("Choose an activity plan first.")
            }
            composable(Routes.DETAILS, arguments = listOf(navArgument("recommendationId") { type = NavType.StringType })) { detailsEntry ->
                RecommendationDetails(planSession, detailsEntry.arguments?.getString("recommendationId").orEmpty(),
                    onBack = { nav.popBackStack() }, onReminder = { navigate(Routes.REMINDERS) },
                    onStart = { recommendation ->
                        startWorkout(recommendation.activityName, recommendation.minutes, intensityToText(recommendation.intensity))
                    })
            }
            composable(Routes.LIVE) {
                val liveWorkout: LiveWorkoutViewModel = viewModel(
                    factory = LiveWorkoutViewModel.factory(
                        sensors,
                        SensorDatasets.UCI_HAPT_SUBSET_ID
                    )
                )
                val liveState by liveWorkout.state.collectAsStateWithLifecycle()
                var saveError by rememberSaveable { mutableStateOf<String?>(null) }
                var finishSubmitted by rememberSaveable { mutableStateOf(false) }
                LiveActivityScreen(
                    activity = state.activeWorkout,
                    plannedMinutes = state.activePlannedMinutes,
                    state = liveState,
                    isSaving = saving || finishSubmitted,
                    saveError = saveError,
                    onTogglePause = liveWorkout::togglePause,
                    onRetryPlayback = liveWorkout::retryPlayback
                ) {
                    if (finishSubmitted || saving) return@LiveActivityScreen
                    finishSubmitted = true
                    saveError = null
                    val summary = liveWorkout.summary()
                    scope.launch {
                        val record = StoredWorkoutRecord(0, workoutUserId, state.activeWorkout,
                            summary.minutes,
                            summary.startedAt.atZone(ZoneId.systemDefault()).toLocalDate(),
                            summary.startedAt,
                            DataSource.DEMO)
                        val result = workouts.save(record)
                        withContext(Dispatchers.Main.immediate) {
                            result.onSuccess {
                                nav.navigate(Routes.HISTORY) { popUpTo(Routes.HOME); launchSingleTop = true }
                                notify("Dataset playback session saved. Your statistics are updated.")
                            }.onFailure {
                                finishSubmitted = false
                                saveError = "Could not save workout. Please try again."
                            }
                        }
                    }
                }
            }
            composable(Routes.HISTORY) {
                WorkoutDataContent(recordsState, workouts::retry) {
                    HistoryScreen(records, { navigate(Routes.record(-1)) }, { navigate(Routes.record(it)) },
                        { id -> scope.launch {
                            workouts.delete(id).onSuccess { notify("Workout deleted.") }
                                .onFailure { notify("Could not delete workout. Please try again.") }
                        } }, { navigate(Routes.SEARCH) }, { selectTab(Routes.STATISTICS) }, saving)
                }
            }
            composable(Routes.SEARCH) {
                WorkoutDataContent(recordsState, workouts::retry) {
                    SearchScreen(records, { navigate(Routes.record(it)) }, { startWorkout(it) })
                }
            }
            composable(Routes.RECORD, arguments = listOf(navArgument("id") { type = NavType.IntType })) { recordEntry ->
                val id = recordEntry.arguments?.getInt("id") ?: -1
                var saveError by rememberSaveable(id) { mutableStateOf<String?>(null) }
                val original = recordsState.records.find { it.id == id }
                val record = records.find { it.id == id }
                WorkoutDataContent(recordsState, workouts::retry) {
                    if (id != -1 && record == null) {
                        ScreenColumn { Text("This workout is no longer in your history."); TextButton({ nav.popBackStack() }) { Text("Go back") } }
                    } else WorkoutEditorScreen(record, today, saving, saveError, onCancel = { nav.popBackStack() }) { name, minutes, date ->
                        saveError = null
                        scope.launch {
                            val saved = StoredWorkoutRecord(if (id == -1) 0 else id, workoutUserId, name, minutes, date,
                                original?.startedAt, original?.source ?: DataSource.LIVE)
                            workouts.save(saved).onSuccess {
                                nav.popBackStack(); notify("Workout saved. Your statistics are updated.")
                            }.onFailure { saveError = "Could not save workout. Please try again." }
                        }
                    }
                }
            }
            composable(Routes.REMINDERS) { ReminderRoute(reminderUserId) }
            composable(Routes.STATISTICS) {
                WorkoutDataContent(recordsState, workouts::retry) {
                    ActivityStatisticsScreen(recordsState.records, workoutUserId,
                        if (isDemoMode) state.preferences.weeklyMinutes else livePreferences?.weeklyGoalMinutes ?: 0, today,
                        isDemo = false, onHistory = { selectTab(Routes.HISTORY) })
                }
            }
            composable(Routes.PROFILE) { ProfileScreen(
                intensity = state.preferences.intensity, activities = state.preferences.activities, goal = state.preferences.goal,
                displayName = state.displayName, email = state.email, weeklyMinutes = state.preferences.weeklyMinutes,
                reminderEnabled = state.reminder.enabled, onReminders = { navigate(Routes.REMINDERS) },
                onStatistics = { selectTab(Routes.STATISTICS) }, onEditPreferences = { navigate(Routes.PREFERENCES) },
                onLogout = {
                    scope.launch {
                        WeatherWork(androidContext).stopWeatherWork()
                        withContext(Dispatchers.Main.immediate) {
                            changeDemoMode(false)
                            state = PrototypeState()
                            context.reset()
                            // Clear saved tab stacks as well as the active stack when leaving a session.
                            tabs.forEach { nav.clearBackStack(it.route) }
                            nav.navigate(Routes.LOGIN) {
                                popUpTo(nav.graph.id) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }, isDemoMode = isDemoMode, authViewModel = authViewModel,
                preferencesViewModel = preferencesViewModel) }
        }
        // Keep this inside Scaffold's content, after NavHost's own handler registration.
        BackHandler(enabled = saving) { /* Wait for the pending write rather than popping twice. */ }
    }
}

@Composable
private fun WorkoutDataContent(state: WorkoutRecordsState, onRetry: () -> Unit, content: @Composable () -> Unit) {
    when {
        state.loading -> ScreenColumn { CircularProgressIndicator(); Text("Loading workouts…") }
        state.error != null -> ScreenColumn {
            Text(state.error, color = MaterialTheme.colorScheme.error)
            Button(onRetry) { Text("Retry") }
        }
        else -> content()
    }
}
