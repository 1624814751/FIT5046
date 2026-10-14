package com.example.movesmart

import android.app.Activity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.movesmart.data.remote.GoogleLoginProvider
import com.example.movesmart.shared.AuthRepository
import com.example.movesmart.shared.PreferencesRepository
import com.example.movesmart.shared.UserAccount
import com.example.movesmart.shared.UserPreferences
import com.example.movesmart.ui.auth.AuthViewModel
import com.example.movesmart.ui.preferences.PreferencesViewModel
import com.example.movesmart.ui.profile.ProfileScreen
import com.example.movesmart.ui.theme.MoveSmartTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import com.example.movesmart.shared.Intensity
import com.example.movesmart.shared.Environment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProfileLogoutUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private lateinit var authRepository: ProfileFakeAuthRepository
    private lateinit var googleLoginProvider: ProfileFakeGoogleLoginProvider
    private lateinit var authViewModel: AuthViewModel
    private lateinit var preferencesViewModel: PreferencesViewModel
    private var logoutFinished = 0
    private val demo = mutableStateOf(false)
    private lateinit var preferencesRepository: LoadingPreferencesRepository

    @Before
    fun showProfile() {
        authRepository = ProfileFakeAuthRepository()
        googleLoginProvider = ProfileFakeGoogleLoginProvider()
        preferencesRepository = LoadingPreferencesRepository()
        authViewModel = AuthViewModel(authRepository, googleLoginProvider)
        preferencesViewModel = PreferencesViewModel(
            authRepository,
            preferencesRepository
        )

        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MoveSmartTheme {
                    ProfileScreen(
                        intensity = "Medium",
                        activities = listOf("Walking"),
                        goal = "Stay Active",
                        displayName = "Demo Alex",
                        email = "demo@example.com",
                        weeklyMinutes = 150,
                        reminderEnabled = false,
                        onReminders = {},
                        onStatistics = {},
                        onEditPreferences = {},
                        onLogout = { logoutFinished += 1 },
                        isDemoMode = demo.value,
                        authViewModel = authViewModel,
                        preferencesViewModel = preferencesViewModel
                    )
                }
            }
        }
        compose.waitUntil(5_000) { preferencesViewModel.state.value.isLoading }
    }

    @After fun clearModels() {
        compose.runOnIdle {
            androidx.lifecycle.ViewModelStore().apply {
                put("auth", authViewModel)
                put("preferences", preferencesViewModel)
                clear()
            }
        }
    }

    @Test fun realAccountNeverFallsBackToPrototypeWhileLoadingOrMissing() {
        compose.onNodeWithText("Alex").assertIsDisplayed()
        compose.onNodeWithText("Demo Alex").assertDoesNotExist()
        compose.onNodeWithText("Beginner").assertDoesNotExist()
        compose.onNodeWithText("Stay Active").assertDoesNotExist()
        compose.onNodeWithText("Loading preferences...").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { preferencesRepository.values.tryEmit(null) }
        compose.onNodeWithText("No preferences saved yet.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Weekly goal: 150 minutes").assertDoesNotExist()
    }

    @Test fun oldUidPreferencesAreNotDisplayedAfterAccountChange() {
        compose.runOnIdle {
            preferencesRepository.values.tryEmit(UserPreferences("profile-user", "Personal goal", Intensity.HIGH,
                210, listOf("Yoga"), Environment.INDOOR))
        }
        compose.onNodeWithText("Personal goal").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { authRepository.userFlow.value = UserAccount("b", "Bea", "bea@example.com") }
        compose.onNodeWithText("Bea").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Personal goal").assertDoesNotExist()
        compose.onNodeWithText("Could not load preferences.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Stay Active").assertDoesNotExist()
    }

    @Test fun demoUsesPrototypeValuesAndExistingLogoutCallback() {
        compose.runOnIdle { demo.value = true }
        compose.onNodeWithText("Demo Alex").assertIsDisplayed()
        compose.onNodeWithText("Stay Active").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Weekly goal: 150 minutes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Log out").performScrollTo().performClick()
        compose.onNodeWithText("Leave the demo?").assertIsDisplayed()
        compose.onNode(hasText("Log out") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(5_000) { logoutFinished == 1 }
        assertEquals(0, authRepository.signOutCalls)
    }

    @Test
    fun logoutButtonWorksWhilePreferencesAreLoading() {
        compose.onNodeWithText("Log out").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Log out?").assertIsDisplayed()

        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Log out?").assertDoesNotExist()

        compose.onNodeWithText("Log out").performScrollTo().performClick()
        compose.onNode(hasText("Log out") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(5_000) { logoutFinished == 1 }

        assertEquals(1, authRepository.signOutCalls)
        assertEquals(1, googleLoginProvider.clearCalls)
        assertNull(authViewModel.currentUser.value)
    }
}

private class ProfileFakeAuthRepository : AuthRepository {
    val userFlow = MutableStateFlow<UserAccount?>(
        UserAccount("profile-user", "Alex", "alex@example.com")
    )
    override val currentUser: StateFlow<UserAccount?> = userFlow
    var signOutCalls = 0

    override suspend fun signIn(email: String, password: String): Result<UserAccount> {
        return Result.success(requireNotNull(userFlow.value))
    }

    override suspend fun signUp(
        displayName: String,
        email: String,
        password: String
    ): Result<UserAccount> {
        return Result.failure(UnsupportedOperationException())
    }

    override suspend fun signInWithGoogle(idToken: String): Result<UserAccount> {
        return Result.success(requireNotNull(userFlow.value))
    }

    override suspend fun signOut(): Result<Unit> {
        signOutCalls += 1
        userFlow.value = null
        return Result.success(Unit)
    }
}

private class ProfileFakeGoogleLoginProvider : GoogleLoginProvider {
    var clearCalls = 0

    override suspend fun getIdToken(activity: Activity): Result<String> {
        return Result.success("test-token")
    }

    override suspend fun clear(): Result<Unit> {
        clearCalls += 1
        return Result.success(Unit)
    }
}

private class LoadingPreferencesRepository : PreferencesRepository {
    val values = MutableSharedFlow<UserPreferences?>(replay = 1)
    override fun observe(userId: String): Flow<UserPreferences?> = values

    override suspend fun save(preferences: UserPreferences): Result<Unit> {
        return Result.success(Unit)
    }
}
