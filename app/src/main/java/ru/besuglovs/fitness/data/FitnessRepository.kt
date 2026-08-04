package ru.besuglovs.fitness.data

import kotlinx.coroutines.flow.Flow

class FitnessRepository(private val db: AppDatabase) {

    val exerciseDao get() = db.exerciseDao()
    val workoutDao get() = db.workoutDao()
    val statsDao get() = db.statsDao()

    // Exercises
    fun exercises(): Flow<List<Exercise>> = db.exerciseDao().getAll()
    suspend fun getExercise(id: Long): Exercise? = db.exerciseDao().getById(id)
    suspend fun insertExercise(exercise: Exercise): Long = db.exerciseDao().insert(exercise)
    suspend fun updateExercise(exercise: Exercise) = db.exerciseDao().update(exercise)
    suspend fun deleteExercise(exercise: Exercise) = db.exerciseDao().delete(exercise)

    // Workouts
    fun unfinishedWorkout(): Flow<Workout?> = db.workoutDao().getUnfinished()
    fun workouts(): Flow<List<Workout>> = db.workoutDao().getAll()
    fun workoutDetails(id: Long): Flow<WorkoutWithDetails?> = db.workoutDao().getWorkoutDetails(id)
    suspend fun createWorkout(): Long = db.workoutDao().insert(Workout(startTime = System.currentTimeMillis()))
    suspend fun saveWorkoutWithSets(
        workoutId: Long,
        endTime: Long,
        notes: String,
        exercises: List<ExerciseWithSets>
    ) = db.workoutDao().saveWorkoutWithSets(workoutId, endTime, notes, exercises)

    suspend fun discardUnfinished() = db.workoutDao().deleteUnfinished()
    suspend fun deleteWorkout(id: Long) = db.workoutDao().delete(id)

    // Export helpers
    suspend fun allExercisesOnce(): List<Exercise> = db.exerciseDao().getAllOnce()
    suspend fun allWorkoutsOnce(): List<Workout> = db.workoutDao().getAllOnce()
    suspend fun allWorkoutDetailsOnce(): List<WorkoutWithDetails> = db.workoutDao().getAllDetailsOnce()

    // Stats
    fun progression(exerciseId: Long): Flow<List<ProgressPoint>> = db.statsDao().progression(exerciseId)
    fun maxWeight(exerciseId: Long): Flow<Double?> = db.statsDao().maxWeight(exerciseId)
    fun overallVolumeTimeline(): Flow<List<ProgressPoint>> = db.statsDao().overallVolumeTimeline()
    fun workoutCount(): Flow<Int> = db.statsDao().workoutCount()
    fun exerciseCount(): Flow<Int> = db.statsDao().exerciseCount()
    fun summaries(): Flow<List<WorkoutSummary>> = db.statsDao().summaries()
}
