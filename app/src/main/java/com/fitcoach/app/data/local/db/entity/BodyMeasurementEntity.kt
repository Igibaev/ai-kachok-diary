package com.fitcoach.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "body_measurements")
data class BodyMeasurementEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: Long,
    val weightKg: Float,
    val waistCm: Float? = null,
    val chestCm: Float? = null,
    val hipsCm: Float? = null,
    val armCm: Float? = null,
    val notes: String = ""
)
