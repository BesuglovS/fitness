package ru.besuglovs.fitness.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkoutDao {

    @Insert
    abstract suspend fun insert(workout: Workout): Long

    @Insert
    abstract suspend fun insertWorkoutExercise(we: WorkoutExercise): Long

    @Insert
    abstract suspend fun insertSets(sets: List<SetEntry>)

    @Query("UPDATE workouts SET endTime = :endTime, notes = :notes, isCircuit = :isCircuit, sessionJson = NULL, pausedAt = NULL WHERE id = :id")
    abstract suspend fun finishWorkout(id: Long, endTime: Long, notes: String, isCircuit: Boolean)

    @Query("UPDATE workouts SET sessionJson = :sessionJson, pausedAt = :pausedAt, isCircuit = :isCircuit WHERE id = :id")
    abstract suspend fun saveSession(id: Long, sessionJson: String, pausedAt: Long, isCircuit: Boolean)

    @Query("SELECT * FROM workouts WHERE endTime IS NULL ORDER BY id DESC LIMIT 1")
    abstract fun getUnfinished(): Flow<Workout?>

    @Query("SELECT * FROM workouts ORDER BY startTime DESC")
    abstract fun getAll(): Flow<List<Workout>>

    @Query("SELECT * FROM workouts ORDER BY startTime DESC")
    abstract suspend fun getAllOnce(): List<Workout>

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY startTime DESC")
    abstract suspend fun getAllDetailsOnce(): List<WorkoutWithDetails>

    @Query("SELECT * FROM workouts WHERE id = :id")
    abstract fun getById(id: Long): Flow<Workout?>

    @Query("SELECT * FROM workouts WHERE id = :id")
    abstract suspend fun getByIdOnce(id: Long): Workout?

    @Query("DELETE FROM workouts WHERE endTime IS NULL")
    abstract suspend fun deleteUnfinished()

    @Query("DELETE FROM workouts WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    abstract fun getWorkoutDetails(id: Long): Flow<WorkoutWithDetails?>

    @Transaction
    open suspend fun saveWorkoutWithSets(
        workoutId: Long,
        endTime: Long,
        notes: String,
        exercises: List<ExerciseWithSets>,
        isCircuit: Boolean = false
    ) {
        finishWorkout(workoutId, endTime, notes, isCircuit)
        var order = 0
        for (we in exercises) {
            val weId = insertWorkoutExercise(
                WorkoutExercise(
                    workoutId = workoutId,
                    exerciseId = we.exerciseId,
                    orderIndex = order++
                )
            )
            val sets = we.sets.mapIndexed { i, s -> s.copy(workoutExerciseId = weId, setNumber = i + 1) }
            if (sets.isNotEmpty()) insertSets(sets)
        }
    }
}
