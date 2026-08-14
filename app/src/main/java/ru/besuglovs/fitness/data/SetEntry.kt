package ru.besuglovs.fitness.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "set_entries",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExercise::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workoutExerciseId")]
)
data class SetEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long = 0,
    val setNumber: Int,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val restSeconds: Int? = null,
    val durationSeconds: Int? = null,
    val setStartTime: Long? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val doneAt: Long = System.currentTimeMillis()
)
