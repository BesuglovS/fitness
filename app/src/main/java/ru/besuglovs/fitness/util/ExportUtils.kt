package ru.besuglovs.fitness.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.besuglovs.fitness.data.FitnessRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportUtils {

    suspend fun export(context: Context, repository: FitnessRepository): String =
        withContext(Dispatchers.IO) {
            try {
                val exercises = repository.allExercisesOnce()
                val workoutDetails = repository.allWorkoutDetailsOnce()
                val heartRateSamples = repository.allHeartRateSamplesOnce()
                    .groupBy { it.workoutId }

                val root = JSONObject()
                root.put("app", "Фитнес-дневник")
                root.put("exportedAt", System.currentTimeMillis())

                val exArray = JSONArray()
                for (e in exercises) {
                    exArray.put(JSONObject().apply {
                        put("id", e.id)
                        put("name", e.name)
                        put("muscleGroup", e.muscleGroup)
                        put("category", e.category)
                    })
                }
                root.put("exercises", exArray)

                val woArray = JSONArray()
                for (wd in workoutDetails) {
                    val wo = wd.workout
                    val wobj = JSONObject().apply {
                        put("id", wo.id)
                        put("startTime", wo.startTime)
                        put("endTime", wo.endTime)
                        put("notes", wo.notes)
                    }
                    val exes = JSONArray()
                    for (we in wd.exercises) {
                        exes.put(JSONObject().apply {
                            put("exerciseId", we.workoutExercise.exerciseId)
                            put("name", we.exerciseName)
                            val setsArr = JSONArray()
                            for (s in we.sets) {
                                setsArr.put(JSONObject().apply {
                                    put("set", s.setNumber)
                                    put("weightKg", s.weightKg)
                                    put("reps", s.reps)
                                    if (s.durationSeconds != null) put("durationSeconds", s.durationSeconds)
                                    put("restSeconds", s.restSeconds)
                                    if (s.setStartTime != null) put("setStartTime", s.setStartTime)
                                    if (s.doneAt > 0L) put("doneAt", s.doneAt)
                                    if (s.avgHeartRate != null) put("avgHeartRate", s.avgHeartRate)
                                    if (s.maxHeartRate != null) put("maxHeartRate", s.maxHeartRate)
                                })
                            }
                            put("sets", setsArr)
                        })
                    }
                    wobj.put("exercises", exes)
                    val hrArr = JSONArray()
                    for (s in heartRateSamples[wo.id].orEmpty()) {
                        hrArr.put(JSONObject().apply {
                            put("timestamp", s.timestamp)
                            put("bpm", s.bpm)
                        })
                    }
                    wobj.put("heartRate", hrArr)
                    woArray.put(wobj)
                }
                root.put("workouts", woArray)

                val filename = "fitness_backup_" +
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) +
                    ".json"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("Не удалось создать файл")
                    resolver.openOutputStream(uri)?.use {
                        it.write(root.toString(2).toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Не удалось записать файл")
                    "Экспортировано: Downloads/$filename"
                } else {
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, filename)
                    file.writeText(root.toString(2))
                    "Экспортировано: ${file.absolutePath}"
                }
            } catch (e: Exception) {
                "Ошибка экспорта: ${e.message}"
            }
        }
}
