package ru.besuglovs.fitness.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HeartRateDao {

    @Insert
    suspend fun insert(sample: HeartRateSample)

    @Insert
    suspend fun insertAll(samples: List<HeartRateSample>)

    @Query("SELECT * FROM heart_rate_samples WHERE workoutId = :workoutId ORDER BY timestamp ASC")
    fun getForWorkout(workoutId: Long): Flow<List<HeartRateSample>>

    @Query("SELECT * FROM heart_rate_samples WHERE workoutId = :workoutId ORDER BY timestamp ASC")
    suspend fun getForWorkoutOnce(workoutId: Long): List<HeartRateSample>

    @Query("SELECT * FROM heart_rate_samples ORDER BY workoutId, timestamp ASC")
    suspend fun getAllOnce(): List<HeartRateSample>
}