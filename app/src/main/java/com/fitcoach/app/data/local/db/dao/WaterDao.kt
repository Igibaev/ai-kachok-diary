package com.fitcoach.app.data.local.db.dao

import androidx.room.*
import com.fitcoach.app.data.local.db.entity.WaterEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterDao {
    @Query("SELECT * FROM water_entries WHERE date >= :startOfDay AND date < :endOfDay ORDER BY createdAt ASC")
    fun getEntriesForDate(startOfDay: Long, endOfDay: Long): Flow<List<WaterEntryEntity>>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_entries WHERE date >= :startOfDay AND date < :endOfDay")
    fun getTotalForDate(startOfDay: Long, endOfDay: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_entries WHERE date >= :startOfDay AND date < :endOfDay")
    suspend fun getTotalForDateSync(startOfDay: Long, endOfDay: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: WaterEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: WaterEntryEntity)

    @Query("SELECT * FROM water_entries WHERE date >= :startDate ORDER BY date ASC")
    suspend fun getEntriesSince(startDate: Long): List<WaterEntryEntity>
}
