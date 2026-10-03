package com.fitcoach.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Сохранённый план питания AI-повара; `planJson` — MealPlanDto, `prefsJson` — PrefsDto (см. ai/chef/ChefDtos.kt). */
@Entity(tableName = "meal_plans")
data class MealPlanEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val days: Int,
    val mealsPerDay: Int,
    val prefsJson: String,
    val planJson: String,
    val goalCalories: Int,
    val goalProteinG: Int,
    val goalCarbsG: Int,
    val goalFatG: Int,
    val isActive: Boolean = true,
    val isDemo: Boolean = false
)

/** Позиция списка покупок, собранного из плана. */
@Entity(tableName = "shopping_items", indices = [Index("planId")])
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val category: String,
    val name: String,
    val quantity: Float,
    val unit: String,
    val checked: Boolean = false,
    val sortOrder: Int = 0
)
