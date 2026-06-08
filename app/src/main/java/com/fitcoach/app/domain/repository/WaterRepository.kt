package com.fitcoach.app.domain.repository

import com.fitcoach.app.domain.model.WaterEntry
import kotlinx.coroutines.flow.Flow

interface WaterRepository {
    fun getEntriesForDate(dateMillis: Long): Flow<List<WaterEntry>>
    fun getTotalForDate(dateMillis: Long): Flow<Int>
    suspend fun addWater(amountMl: Int)
    suspend fun deleteEntry(entry: WaterEntry)
    suspend fun getTotalForDateSync(dateMillis: Long): Int
}
