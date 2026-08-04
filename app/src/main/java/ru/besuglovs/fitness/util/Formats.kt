package ru.besuglovs.fitness.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDateTime(ts: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ts))

fun formatDate(ts: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(ts))

fun formatTime(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

fun formatShortDate(ts: Long): String =
    SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(ts))

fun formatDuration(start: Long, end: Long?): String {
    val ms = (end ?: System.currentTimeMillis()) - start
    val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatTimer(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

fun weightLabel(w: Double?): String =
    if (w == null) "-" else if (w % 1.0 == 0.0) w.toInt().toString() else w.toString()

fun epley1rm(weight: Double, reps: Int): Double =
    if (reps <= 1) weight else weight * (1.0 + reps / 30.0)
