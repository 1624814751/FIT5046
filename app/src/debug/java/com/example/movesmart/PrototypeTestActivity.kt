package com.example.movesmart

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.example.movesmart.data.local.MoveSmartDatabase
import com.example.movesmart.data.local.RoomWorkoutRepository
import com.example.movesmart.navigation.MoveSmartApp
import com.example.movesmart.ui.theme.MoveSmartTheme
import com.example.movesmart.data.remote.GoogleLoginProvider
import com.example.movesmart.shared.*
import com.example.movesmart.ui.auth.AuthViewModel
import com.example.movesmart.ui.preferences.PreferencesViewModel
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/** Debug-only instrumentation host; its test database survives Activity recreation. */
class PrototypeTestActivity : ComponentActivity() {
    private lateinit var store: WorkoutTestStore
    val testAuth: AuthRepository get() = store.auth
    val workoutRepository: RoomWorkoutRepository get() = store.repository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = ViewModelProvider(this)[WorkoutTestStore::class.java]
        // Offline navigation tests must not inherit a real Firebase login from
        // an earlier manual run. Standard ViewModel keys also survive recreation.
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
                AuthViewModel::class.java -> AuthViewModel(store.auth, object : GoogleLoginProvider {
                    override suspend fun getIdToken(activity: android.app.Activity) =
                        Result.failure<String>(UnsupportedOperationException("Offline test host"))
                    override suspend fun clear() = Result.success(Unit)
                })
                PreferencesViewModel::class.java -> PreferencesViewModel(store.auth, object : PreferencesRepository {
                    override fun observe(userId: String) = flowOf<UserPreferences?>(UserPreferences(userId, "Stay active", Intensity.MEDIUM, 150, emptyList(), Environment.ANY))
                    override suspend fun save(preferences: UserPreferences) = Result.success(Unit)
                })
                else -> error("Unexpected test model")
            } as T
        }
        ViewModelProvider(this, factory)[AuthViewModel::class.java]
        ViewModelProvider(this, factory)[PreferencesViewModel::class.java]
        setContent { MoveSmartTheme { MoveSmartApp(workoutRepository = workoutRepository) } }
    }
}

class WorkoutTestStore(application: Application) : AndroidViewModel(application) {
    val auth = object : AuthRepository {
        override val currentUser = MutableStateFlow<UserAccount?>(null)
        override suspend fun signIn(email: String, password: String): Result<UserAccount> {
            val account = UserAccount("test-authenticated-user", "Test User", email)
            currentUser.value = account
            return Result.success(account)
        }
        override suspend fun signUp(displayName: String, email: String, password: String) = signIn(email, password)
        override suspend fun signInWithGoogle(idToken: String) = signIn("", "")
        override suspend fun signOut(): Result<Unit> { currentUser.value = null; return Result.success(Unit) }
    }
    private val name = "prototype-ui-test-${UUID.randomUUID()}.db"
    private val database = Room.databaseBuilder(application, MoveSmartDatabase::class.java, name).build()
    val repository = RoomWorkoutRepository(database)
    override fun onCleared() {
        database.close()
        getApplication<Application>().deleteDatabase(name)
    }
}
