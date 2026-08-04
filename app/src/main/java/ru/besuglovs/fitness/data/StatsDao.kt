package ru.besuglovs.fitness.data

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {

    @Query(
        """
        SELECT w.startTime AS workoutStart,
               MAX(s.weightKg) AS maxWeight,
               MAX(s.reps) AS maxReps,
               SUM(s.weightKg * s.reps) AS volume
        FROM workouts w
        JOIN workout_exercises we ON we.workoutId = w.id
        JOIN set_entries s ON s.workoutExerciseId = we.id
        WHERE we.exerciseId = :exerciseId AND w.endTime IS NOT NULL AND s.weightKg IS NOT NULL AND s.reps IS NOT NULL
        GROUP BY w.id
        ORDER BY w.startTime
        """
    )
    fun progression(exerciseId: Long): Flow<List<ProgressPoint>>

    @Query(
        """
        SELECT MAX(s.weightKg) FROM set_entries s
        JOIN workout_exercises we ON we.id = s.workoutExerciseId
        JOIN workouts w ON w.id = we.workoutId
        WHERE we.exerciseId = :exerciseId AND w.endTime IS NOT NULL
        """
    )
    fun maxWeight(exerciseId: Long): Flow<Double?>

    @Query(
        """
        SELECT w.startTime AS workoutStart, MAX(s.weightKg) AS maxWeight, MAX(s.reps) AS maxReps, SUM(s.weightKg * s.reps) AS volume
        FROM workouts w
        JOIN workout_exercises we ON we.workoutId = w.id
        JOIN set_entries s ON s.workoutExerciseId = we.id
        WHERE w.endTime IS NOT NULL AND s.weightKg IS NOT NULL AND s.reps IS NOT NULL
        GROUP BY w.id
        ORDER BY w.startTime
        """
    )
    fun overallVolumeTimeline(): Flow<List<ProgressPoint>>

    @Query(
        """
        SELECT COUNT(DISTINCT w.id) FROM workouts w WHERE w.endTime IS NOT NULL
        """
    )
    fun workoutCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM exercises")
    fun exerciseCount(): Flow<Int>

    @Query(
        """
        SELECT w.id, w.startTime, w.endTime,
               COUNT(DISTINCT we.id) AS exerciseCount,
               COUNT(s.id) AS setCount,
               SUM(s.weightKg * s.reps) AS volume
        FROM workouts w
        LEFT JOIN workout_exercises we ON we.workoutId = w.id
        LEFT JOIN set_entries s ON s.workoutExerciseId = we.id
        WHERE w.endTime IS NOT NULL
        GROUP BY w.id
        ORDER BY w.startTime DESC
        """
    )
    fun summaries(): Flow<List<WorkoutSummary>>
}
