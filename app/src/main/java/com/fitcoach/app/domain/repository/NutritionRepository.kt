package com.fitcoach.app.domain.repository

import com.fitcoach.app.domain.model.NutritionEntry
import com.fitcoach.app.domain.model.NutritionSummary
import kotlinx.coroutines.flow.Flow

interface NutritionRepository {
    fun getEntriesForDate(dateMillis: Long): Flow<List<NutritionEntry>>
    fun getNutritionSummaryForDate(dateMillis: Long): Flow<NutritionSummary>
    suspend fun addEntry(entry: NutritionEntry)
    suspend fun deleteEntry(entry: NutritionEntry)
    suspend fun getTemplates(): List<NutritionEntry>
    suspend fun searchFoods(query: String): List<NutritionEntry>
}
