package ru.besuglovs.fitness.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    fun getAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    suspend fun getAllOnce(): List<Exercise>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): Exercise?

    @Query("SELECT COUNT(*) FROM workout_exercises WHERE exerciseId = :exerciseId")
    suspend fun countUsages(exerciseId: Long): Int

    @Insert
    suspend fun insert(exercise: Exercise): Long

    @Update
    suspend fun update(exercise: Exercise)

    @Delete
    suspend fun delete(exercise: Exercise)
}
