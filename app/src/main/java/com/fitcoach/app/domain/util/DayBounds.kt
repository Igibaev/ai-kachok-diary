package com.fitcoach.app.domain.util

import java.util.Calendar

/** Границы календарного дня [start, end) в миллисекундах для локальной таймзоны. */
object DayBounds {
    fun of(millis: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return start to cal.timeInMillis
    }

    fun startOfDay(millis: Long = System.currentTimeMillis()): Long = of(millis).first
}
