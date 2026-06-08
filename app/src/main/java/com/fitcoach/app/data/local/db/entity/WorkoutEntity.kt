package com.fitcoach.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: Long,
    val planKey: String,
    val phaseName: String,
    val weekNumber: Int,
    val isCompleted: Boolean = false,
    val durationMinutes: Int? = null,
    val backPainLevel: Int = 0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
