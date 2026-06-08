package com.fitcoach.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "nutrition_entries")
data class NutritionEntryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: Long,
    val mealType: String,
    val name: String,
    val calories: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val grams: Float? = null,
    val createdAt: Long = System.currentTimeMillis()
)
