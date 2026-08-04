package ru.besuglovs.fitness.data

import androidx.room.Embedded
import androidx.room.Relation

data class ExerciseWithSets(
    val exerciseId: Long,
    val sets: List<SetEntry>
)

data class WorkoutWithDetails(
    @Embedded val workout: Workout,
    @Relation(
        parentColumn = "id",
        entityColumn = "workoutId",
        entity = WorkoutExercise::class
    )
    val exercises: List<WorkoutExerciseWithExercise>
)

data class WorkoutExerciseWithExercise(
    @Embedded val workoutExercise: WorkoutExercise,
    @Relation(
        parentColumn = "exerciseId",
        entityColumn = "id",
        entity = Exercise::class
    )
    val exercise: List<Exercise>,
    @Relation(
        parentColumn = "id",
        entityColumn = "workoutExerciseId"
    )
    val sets: List<SetEntry>
) {
    val exerciseName: String get() = exercise.firstOrNull()?.name ?: "?"
    val muscleGroup: String get() = exercise.firstOrNull()?.muscleGroup ?: ""
}

data class ProgressPoint(
    val workoutStart: Long,
    val maxWeight: Double?,
    val maxReps: Int?,
    val volume: Double?
)

data class WorkoutSummary(
    val id: Long,
    val startTime: Long,
    val endTime: Long?,
    val exerciseCount: Int,
    val setCount: Int,
    val volume: Double?
)
