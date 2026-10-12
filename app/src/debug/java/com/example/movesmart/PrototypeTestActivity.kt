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
import java.util.UUID

/** Debug-only instrumentation host; its test database survives Activity recreation. */
class PrototypeTestActivity : ComponentActivity() {
    private lateinit var store: WorkoutTestStore
    val workoutRepository: RoomWorkoutRepository get() = store.repository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = ViewModelProvider(this)[WorkoutTestStore::class.java]
        setContent { MoveSmartTheme { MoveSmartApp(workoutRepository = workoutRepository) } }
    }
}

class WorkoutTestStore(application: Application) : AndroidViewModel(application) {
    private val name = "prototype-ui-test-${UUID.randomUUID()}.db"
    private val database = Room.databaseBuilder(application, MoveSmartDatabase::class.java, name).build()
    val repository = RoomWorkoutRepository(database)
    override fun onCleared() {
        database.close()
        getApplication<Application>().deleteDatabase(name)
    }
}
