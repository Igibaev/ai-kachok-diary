package com.fitcoach.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "water_entries")
data class WaterEntryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: Long,
    val amountMl: Int,
    val createdAt: Long = System.currentTimeMillis()
)
