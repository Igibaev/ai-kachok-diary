package com.fitcoach.app.data.local.db.dao

import androidx.room.*
import com.fitcoach.app.data.local.db.entity.NutritionEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NutritionDao {
    @Query("SELECT * FROM nutrition_entries WHERE date >= :startOfDay AND date < :endOfDay ORDER BY createdAt ASC")
    fun getEntriesForDate(startOfDay: Long, endOfDay: Long): Flow<List<NutritionEntryEntity>>

    @Query("SELECT * FROM nutrition_entries WHERE date >= :startOfDay AND date < :endOfDay ORDER BY createdAt ASC")
    suspend fun getEntriesForDateSync(startOfDay: Long, endOfDay: Long): List<NutritionEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: NutritionEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: NutritionEntryEntity)

    @Query("SELECT DISTINCT name FROM nutrition_entries ORDER BY name")
    suspend fun getAllFoodNames(): List<String>

    @Query("SELECT * FROM nutrition_entries WHERE name LIKE '%' || :query || '%' LIMIT 50")
    suspend fun searchEntries(query: String): List<NutritionEntryEntity>

    @Query("SELECT * FROM nutrition_entries WHERE mealType = 'TEMPLATE' ORDER BY name")
    suspend fun getTemplates(): List<NutritionEntryEntity>
}
