package com.fitcoach.app.data.repository

import com.fitcoach.app.data.local.db.dao.WaterDao
import com.fitcoach.app.data.local.db.entity.WaterEntryEntity
import com.fitcoach.app.domain.model.WaterEntry
import com.fitcoach.app.domain.repository.WaterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

class WaterRepositoryImpl @Inject constructor(
    private val dao: WaterDao
) : WaterRepository {

    override fun getEntriesForDate(dateMillis: Long): Flow<List<WaterEntry>> {
        val (start, end) = dayBounds(dateMillis)
        return dao.getEntriesForDate(start, end).map { it.map { e -> e.toDomain() } }
    }

    override fun getTotalForDate(dateMillis: Long): Flow<Int> {
        val (start, end) = dayBounds(dateMillis)
        return dao.getTotalForDate(start, end)
    }

    override suspend fun addWater(amountMl: Int) {
        val now = System.currentTimeMillis()
        dao.insertEntry(WaterEntryEntity(
            id = UUID.randomUUID().toString(),
            date = todayStart(),
            amountMl = amountMl,
            createdAt = now
        ))
    }

    override suspend fun deleteEntry(entry: WaterEntry) = dao.deleteEntry(entry.toEntity())

    override suspend fun getTotalForDateSync(dateMillis: Long): Int {
        val (start, end) = dayBounds(dateMillis)
        return dao.getTotalForDateSync(start, end)
    }

    private fun dayBounds(millis: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return start to cal.timeInMillis
    }

    private fun todayStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

private fun WaterEntryEntity.toDomain() = WaterEntry(id, date, amountMl, createdAt)
private fun WaterEntry.toEntity() = WaterEntryEntity(id, date, amountMl, createdAt)
