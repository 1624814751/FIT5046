package com.example.movesmart.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Room
import android.content.Context

@Database(entities = [WorkoutEntity::class], version = 1, exportSchema = true)
abstract class MoveSmartDatabase : RoomDatabase() {
    abstract fun workouts(): WorkoutDao

    companion object {
        @Volatile private var instance: MoveSmartDatabase? = null

        fun get(context: Context): MoveSmartDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, MoveSmartDatabase::class.java, "movesmart-workouts.db")
                .build().also { instance = it }
        }
    }
}
