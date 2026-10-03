package com.fitcoach.app.domain.repository

import com.fitcoach.app.domain.model.MealPlan
import com.fitcoach.app.domain.model.MealPlanRequest
import com.fitcoach.app.domain.model.SavedMealPlan
import com.fitcoach.app.domain.model.ShoppingItem
import com.fitcoach.app.domain.model.ShoppingReminder
import kotlinx.coroutines.flow.Flow

/** План питания AI-повара (один активный), собранный из него список покупок и настройки напоминания о покупках. */
interface MealPlanRepository {
    fun observeActivePlan(): Flow<SavedMealPlan?>
    suspend fun getActivePlan(): SavedMealPlan?

    /** Сохраняет план как активный (предыдущий удаляется вместе со списком) и собирает новый список покупок. */
    suspend fun savePlan(plan: MealPlan, request: MealPlanRequest, isDemo: Boolean): SavedMealPlan

    suspend fun deleteActivePlan()

    fun observeShoppingItems(): Flow<List<ShoppingItem>>
    suspend fun getShoppingItems(): List<ShoppingItem>
    fun observeUncheckedCount(): Flow<Int>
    suspend fun setChecked(itemId: String, checked: Boolean)
    suspend fun uncheckAll()

    /** Удаляет все планы и списки (очистка демо-данных). */
    suspend fun clearAll()

    fun observeReminder(): Flow<ShoppingReminder>
    suspend fun getReminder(): ShoppingReminder
    suspend fun saveReminder(reminder: ShoppingReminder)
}
