package com.fitcoach.app.data.repository

import com.fitcoach.app.data.local.db.dao.NutritionDao
import com.fitcoach.app.data.local.db.entity.NutritionEntryEntity
import com.fitcoach.app.domain.model.MealType
import com.fitcoach.app.domain.model.NutritionEntry
import com.fitcoach.app.domain.model.NutritionSummary
import com.fitcoach.app.domain.repository.NutritionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject

class NutritionRepositoryImpl @Inject constructor(
    private val dao: NutritionDao
) : NutritionRepository {

    override fun getEntriesForDate(dateMillis: Long): Flow<List<NutritionEntry>> {
        val (start, end) = dayBounds(dateMillis)
        return dao.getEntriesForDate(start, end).map { it.map { e -> e.toDomain() } }
    }

    override fun getNutritionSummaryForDate(dateMillis: Long): Flow<NutritionSummary> {
        val (start, end) = dayBounds(dateMillis)
        return dao.getEntriesForDate(start, end).map { entities ->
            val entries = entities.filter { it.mealType != "TEMPLATE" }.map { it.toDomain() }
            NutritionSummary(
                calories = entries.sumOf { it.calories },
                proteinG = entries.sumOf { it.proteinG.toDouble() }.toFloat(),
                carbsG = entries.sumOf { it.carbsG.toDouble() }.toFloat(),
                fatG = entries.sumOf { it.fatG.toDouble() }.toFloat(),
                entriesByMeal = entries.groupBy { it.mealType }
            )
        }
    }

    override suspend fun addEntry(entry: NutritionEntry) = dao.insertEntry(entry.toEntity())

    override suspend fun deleteEntry(entry: NutritionEntry) = dao.deleteEntry(entry.toEntity())

    override suspend fun getTemplates(): List<NutritionEntry> {
        return dao.getTemplates().map { it.toDomain() }
    }

    override suspend fun searchFoods(query: String): List<NutritionEntry> =
        dao.searchEntries(query).map { it.toDomain() }

    private fun dayBounds(millis: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return start to cal.timeInMillis
    }
}

private fun NutritionEntryEntity.toDomain() = NutritionEntry(
    id = id, date = date,
    mealType = try { MealType.valueOf(mealType) } catch (e: Exception) { MealType.SNACK },
    name = name, calories = calories, proteinG = proteinG, carbsG = carbsG, fatG = fatG, grams = grams
)

private fun NutritionEntry.toEntity() = NutritionEntryEntity(
    id = id, date = date, mealType = mealType.name, name = name,
    calories = calories, proteinG = proteinG, carbsG = carbsG, fatG = fatG, grams = grams
)
