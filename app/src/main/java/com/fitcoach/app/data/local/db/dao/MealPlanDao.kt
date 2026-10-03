package com.fitcoach.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitcoach.app.data.local.db.entity.MealPlanEntity
import com.fitcoach.app.data.local.db.entity.ShoppingItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MealPlanDao {
    @Query("SELECT * FROM meal_plans WHERE isActive = 1 ORDER BY createdAt DESC LIMIT 1")
    fun observeActive(): Flow<MealPlanEntity?>

    @Query("SELECT * FROM meal_plans WHERE isActive = 1 ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActive(): MealPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: MealPlanEntity)

    @Query("DELETE FROM meal_plans")
    suspend fun deleteAll()

    @Query("DELETE FROM meal_plans WHERE id = :planId")
    suspend fun deleteById(planId: String)

    // ---- список покупок ----

    @Query("SELECT s.* FROM shopping_items s INNER JOIN meal_plans p ON p.id = s.planId WHERE p.isActive = 1 ORDER BY s.sortOrder ASC")
    fun observeActiveShopping(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT s.* FROM shopping_items s INNER JOIN meal_plans p ON p.id = s.planId WHERE p.isActive = 1 ORDER BY s.sortOrder ASC")
    suspend fun getActiveShopping(): List<ShoppingItemEntity>

    @Query("SELECT COUNT(*) FROM shopping_items s INNER JOIN meal_plans p ON p.id = s.planId WHERE p.isActive = 1 AND s.checked = 0")
    fun observeUncheckedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShopping(items: List<ShoppingItemEntity>)

    @Query("UPDATE shopping_items SET checked = :checked WHERE id = :itemId")
    suspend fun setChecked(itemId: String, checked: Boolean)

    @Query("UPDATE shopping_items SET checked = 0 WHERE planId IN (SELECT id FROM meal_plans WHERE isActive = 1)")
    suspend fun uncheckAllActive()

    @Query("DELETE FROM shopping_items WHERE planId = :planId")
    suspend fun deleteShoppingByPlan(planId: String)

    @Query("DELETE FROM shopping_items")
    suspend fun deleteAllShopping()
}
