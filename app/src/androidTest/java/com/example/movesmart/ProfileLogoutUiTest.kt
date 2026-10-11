package com.example.movesmart

import android.app.Activity
import androidx.activity.compose.setContent
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
import kotlinx.coroutines.flow.emptyFlow
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

    @Before
    fun showProfile() {
        authRepository = ProfileFakeAuthRepository()
        googleLoginProvider = ProfileFakeGoogleLoginProvider()
        authViewModel = AuthViewModel(authRepository, googleLoginProvider)
        preferencesViewModel = PreferencesViewModel(
            authRepository,
            LoadingPreferencesRepository()
        )

        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MoveSmartTheme {
                    ProfileScreen(
                        intensity = "Medium",
                        activities = listOf("Walking"),
                        goal = "Stay Active",
                        displayName = "Alex",
                        email = "alex@example.com",
                        weeklyMinutes = 150,
                        reminderEnabled = false,
                        onReminders = {},
                        onStatistics = {},
                        onEditPreferences = {},
                        onLogout = { logoutFinished += 1 },
                        isDemoMode = false,
                        authViewModel = authViewModel,
                        preferencesViewModel = preferencesViewModel
                    )
                }
            }
        }
        compose.waitUntil(5_000) { preferencesViewModel.state.value.isLoading }
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
    private val userFlow = MutableStateFlow<UserAccount?>(
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
    override fun observe(userId: String): Flow<UserPreferences?> = emptyFlow()

    override suspend fun save(preferences: UserPreferences): Result<Unit> {
        return Result.success(Unit)
    }
}
