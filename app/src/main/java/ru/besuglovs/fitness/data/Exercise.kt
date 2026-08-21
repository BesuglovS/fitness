package ru.besuglovs.fitness.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: String = "",
    val category: String = "Силовая",
    val createdAt: Long = System.currentTimeMillis()
)
