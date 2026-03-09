package com.lumen.core.util

fun formatEpochDate(epochMillis: Long): String {
    val totalDays = epochMillis / 86_400_000L
    var remainingDays = totalDays
    var year = 1970
    while (true) {
        val daysInYear = if (isLeapYear(year)) 366L else 365L
        if (remainingDays < daysInYear) break
        remainingDays -= daysInYear
        year++
    }
    val monthDays = if (isLeapYear(year)) {
        intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    } else {
        intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    }
    var month = 1
    for (m in monthDays) {
        if (remainingDays < m) break
        remainingDays -= m
        month++
    }
    val day = remainingDays.toInt() + 1
    return "${year}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

fun dateToEpochRange(date: String): Pair<Long, Long> {
    val parts = date.split("-")
    if (parts.size != 3) return 0L to 0L
    val year = parts[0].toIntOrNull() ?: return 0L to 0L
    val month = parts[1].toIntOrNull() ?: return 0L to 0L
    val day = parts[2].toIntOrNull() ?: return 0L to 0L

    val startOfDay = dateToEpochMillis(year, month, day)
    val endOfDay = startOfDay + 86_400_000L
    return startOfDay to endOfDay
}

private fun dateToEpochMillis(year: Int, month: Int, day: Int): Long {
    var totalDays = 0L
    for (y in 1970 until year) {
        totalDays += if (isLeapYear(y)) 366 else 365
    }
    val monthDays = if (isLeapYear(year)) {
        intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    } else {
        intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    }
    for (m in 1 until month) {
        totalDays += monthDays[m - 1]
    }
    totalDays += day - 1
    return totalDays * 86_400_000L
}

fun isLeapYear(year: Int): Boolean {
    return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}
