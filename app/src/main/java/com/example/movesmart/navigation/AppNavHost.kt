package com.example.movesmart.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.movesmart.AppPrimary
import com.example.movesmart.AppSurface
import com.example.movesmart.ButtonShape
import com.example.movesmart.HistoryScreen
import com.example.movesmart.LiveActivityScreen
import com.example.movesmart.TextMuted
import com.example.movesmart.ui.auth.LoginScreen
import com.example.movesmart.ui.auth.SignUpScreen
import com.example.movesmart.ui.preferences.PreferencesScreen
import com.example.movesmart.ui.profile.ProfileScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Shared UI state only: no account or persistent storage is involved.
    var savedIntensity by rememberSaveable { mutableStateOf("Medium") }
    var savedActivities by rememberSaveable {
        mutableStateOf(arrayListOf("Walking", "Swimming", "Badminton"))
    }
    var savedGoal by rememberSaveable { mutableStateOf("Stay Active") }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.LOGIN,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onLogin = {
                        // TODO: Navigate to the team's Home screen once it is integrated.
                        navController.navigate(Routes.LIVE_ACTIVITY) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onSignUp = {
                        navController.navigate(Routes.SIGN_UP) { launchSingleTop = true }
                    }
                )
            }
            composable(Routes.SIGN_UP) {
                SignUpScreen(
                    onCreateAccount = { navController.popBackStack(Routes.LOGIN, false) },
                    onLogin = { navController.popBackStack(Routes.LOGIN, false) }
                )
            }
            composable(Routes.LIVE_ACTIVITY) { LiveActivityScreen() }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    intensity = savedIntensity,
                    activities = savedActivities,
                    goal = savedGoal,
                    onBack = { navController.popBackStack() },
                    onEditPreferences = {
                        navController.navigate(Routes.PREFERENCES) { launchSingleTop = true }
                    },
                    onLogout = {
                        savedIntensity = "Medium"
                        savedActivities = arrayListOf("Walking", "Swimming", "Badminton")
                        savedGoal = "Stay Active"
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Routes.PREFERENCES) {
                PreferencesScreen(
                    savedIntensity = savedIntensity,
                    savedActivities = savedActivities,
                    savedGoal = savedGoal,
                    onBack = { navController.popBackStack(Routes.PROFILE, false) },
                    onSave = { intensity, activities, goal ->
                        savedIntensity = intensity
                        savedActivities = ArrayList(activities)
                        savedGoal = goal
                        navController.popBackStack(Routes.PROFILE, false)
                    }
                )
            }
        }

        // Replace the old screenshot switch without resizing or editing teammate screens.
        if (currentRoute == Routes.LIVE_ACTIVITY || currentRoute == Routes.HISTORY) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding().padding(16.dp),
                shape = ButtonShape,
                color = AppSurface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Temp nav", color = TextMuted, fontSize = 11.sp)
                    TextButton(
                        onClick = {
                            val destination = if (currentRoute == Routes.LIVE_ACTIVITY) {
                                Routes.HISTORY
                            } else {
                                Routes.LIVE_ACTIVITY
                            }
                            navController.navigate(destination) {
                                popUpTo(Routes.LIVE_ACTIVITY)
                                launchSingleTop = true
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = AppPrimary)
                    ) {
                        Text(if (currentRoute == Routes.LIVE_ACTIVITY) "History" else "Live Activity")
                    }
                    TextButton(
                        onClick = {
                            navController.navigate(Routes.PROFILE) { launchSingleTop = true }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = AppPrimary)
                    ) {
                        Text("Profile")
                    }
                }
            }
        }
    }
}
