package com.example.movesmart.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "workouts", indices = [Index(value = ["userId", "dateEpochDay"])])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val title: String,
    val minutes: Int,
    val dateEpochDay: Long,
    val startedAtEpochMillis: Long?,
    val source: String
)
