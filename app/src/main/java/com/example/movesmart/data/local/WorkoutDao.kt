package com.example.movesmart.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts WHERE userId = :userId ORDER BY dateEpochDay DESC, id DESC")
    fun observe(userId: String): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE userId = :userId AND id = :id")
    suspend fun get(userId: String, id: Int): WorkoutEntity?

    @Insert
    suspend fun insert(record: WorkoutEntity): Long

    // The owner predicate prevents an existing ID from being reassigned to another user.
    @Query("UPDATE workouts SET title = :title, minutes = :minutes, dateEpochDay = :dateEpochDay, startedAtEpochMillis = :startedAtEpochMillis, source = :source WHERE userId = :userId AND id = :id")
    suspend fun update(userId: String, id: Int, title: String, minutes: Int, dateEpochDay: Long, startedAtEpochMillis: Long?, source: String): Int

    @Query("DELETE FROM workouts WHERE userId = :userId AND id = :id")
    suspend fun delete(userId: String, id: Int): Int
}
