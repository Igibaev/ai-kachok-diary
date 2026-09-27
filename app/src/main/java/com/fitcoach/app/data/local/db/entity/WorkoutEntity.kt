package com.fitcoach.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "workouts", indices = [Index("date")])
data class WorkoutEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: Long,
    val programKey: String = "BEGINNER_3",
    val planKey: String,
    val phaseName: String,
    val weekNumber: Int,
    val isCompleted: Boolean = false,
    val durationMinutes: Int? = null,
    val painLevel: Int = 0,
    val rpe: Int = 0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
