package com.fitcoach.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import com.fitcoach.app.ai.chef.MealPlanDto
import com.fitcoach.app.ai.chef.PrefsDto
import com.fitcoach.app.ai.chef.toDomain
import com.fitcoach.app.ai.chef.toDto
import com.fitcoach.app.data.local.db.AppDatabase
import com.fitcoach.app.data.local.db.dao.MealPlanDao
import com.fitcoach.app.data.local.db.entity.MealPlanEntity
import com.fitcoach.app.data.local.db.entity.ShoppingItemEntity
import com.fitcoach.app.domain.model.Budget
import com.fitcoach.app.domain.model.Cuisine
import com.fitcoach.app.domain.model.IngredientCategory
import com.fitcoach.app.domain.model.MealPlan
import com.fitcoach.app.domain.model.MealPlanPrefs
import com.fitcoach.app.domain.model.MealPlanRequest
import com.fitcoach.app.domain.model.PlanGoals
import com.fitcoach.app.domain.model.SavedMealPlan
import com.fitcoach.app.domain.model.ShoppingItem
import com.fitcoach.app.domain.model.ShoppingReminder
import com.fitcoach.app.domain.model.ShoppingUnit
import com.fitcoach.app.domain.repository.MealPlanRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealPlanRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val dao: MealPlanDao,
    private val json: Json,
    @ApplicationContext private val context: Context
) : MealPlanRepository {

    private val prefs: SharedPreferences by lazy { context.getSharedPreferences("fitcoach_prefs", Context.MODE_PRIVATE) }
    private val reminderFlow by lazy { MutableStateFlow(readReminder()) }

    override fun observeActivePlan(): Flow<SavedMealPlan?> = dao.observeActive().map { it?.toDomain() }

    override suspend fun getActivePlan(): SavedMealPlan? = dao.getActive()?.toDomain()

    override suspend fun savePlan(plan: MealPlan, request: MealPlanRequest, isDemo: Boolean): SavedMealPlan {
        val id = UUID.randomUUID().toString()
        val entity = MealPlanEntity(
            id = id,
            createdAt = System.currentTimeMillis(),
            days = request.days,
            mealsPerDay = request.mealsPerDay,
            prefsJson = json.encodeToString(PrefsDto.serializer(), request.prefs.toDto()),
            planJson = json.encodeToString(MealPlanDto.serializer(), plan.toDto()),
            goalCalories = request.goals.calories,
            goalProteinG = request.goals.proteinG,
            goalCarbsG = request.goals.carbsG,
            goalFatG = request.goals.fatG,
            isActive = true,
            isDemo = isDemo
        )
        var order = 0
        val items = plan.shopping.flatMap { group ->
            group.items.map { line ->
                ShoppingItemEntity(
                    id = UUID.randomUUID().toString(), planId = id, category = group.category.wire,
                    name = line.name, quantity = line.quantity, unit = line.unit.wire, checked = false, sortOrder = order++
                )
            }
        }
        db.withTransaction {
            // Один активный план: старые планы и их списки удаляем, а не копим.
            dao.deleteAllShopping()
            dao.deleteAll()
            dao.insert(entity)
            dao.insertShopping(items)
        }
        return entity.toDomain()
    }

    override suspend fun deleteActivePlan() {
        val active = dao.getActive() ?: return
        db.withTransaction {
            dao.deleteShoppingByPlan(active.id)
            dao.deleteById(active.id)
        }
    }

    override fun observeShoppingItems(): Flow<List<ShoppingItem>> = dao.observeActiveShopping().map { it.map { e -> e.toDomain() } }

    override suspend fun getShoppingItems(): List<ShoppingItem> = dao.getActiveShopping().map { it.toDomain() }

    override fun observeUncheckedCount(): Flow<Int> = dao.observeUncheckedCount()

    override suspend fun setChecked(itemId: String, checked: Boolean) = dao.setChecked(itemId, checked)

    override suspend fun uncheckAll() = dao.uncheckAllActive()

    override suspend fun clearAll() {
        db.withTransaction {
            dao.deleteAllShopping()
            dao.deleteAll()
        }
    }

    // ---- напоминание (prefs) ----

    override fun observeReminder(): Flow<ShoppingReminder> = reminderFlow.asStateFlow()

    override suspend fun getReminder(): ShoppingReminder = readReminder()

    override suspend fun saveReminder(reminder: ShoppingReminder) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, reminder.enabled)
            .putInt(KEY_DAY, reminder.dayOfWeek.coerceIn(1, 7))
            .putInt(KEY_HOUR, reminder.hour.coerceIn(0, 23))
            .putInt(KEY_MINUTE, reminder.minute.coerceIn(0, 59))
            .apply()
        reminderFlow.value = readReminder()
    }

    private fun readReminder() = ShoppingReminder(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        dayOfWeek = prefs.getInt(KEY_DAY, 6),
        hour = prefs.getInt(KEY_HOUR, 18),
        minute = prefs.getInt(KEY_MINUTE, 0)
    )

    private fun MealPlanEntity.toDomain(): SavedMealPlan = SavedMealPlan(
        id = id,
        createdAt = createdAt,
        days = days,
        mealsPerDay = mealsPerDay,
        prefs = runCatching { json.decodeFromString(PrefsDto.serializer(), prefsJson).toDomain() }.getOrDefault(MealPlanPrefs()),
        plan = runCatching { json.decodeFromString(MealPlanDto.serializer(), planJson).toDomain() }.getOrDefault(MealPlan(emptyList(), emptyList())),
        goals = PlanGoals(goalCalories, goalProteinG, goalCarbsG, goalFatG),
        isActive = isActive,
        isDemo = isDemo
    )

    private companion object {
        const val KEY_ENABLED = "shopping_reminder_enabled"
        const val KEY_DAY = "shopping_reminder_day"
        const val KEY_HOUR = "shopping_reminder_hour"
        const val KEY_MINUTE = "shopping_reminder_minute"
    }
}

private fun ShoppingItemEntity.toDomain() = ShoppingItem(
    id = id, planId = planId, category = IngredientCategory.fromWire(category), name = name,
    quantity = quantity, unit = ShoppingUnit.fromWire(unit), checked = checked, sortOrder = sortOrder
)

fun MealPlanPrefs.toDto() = PrefsDto(cuisine.wire, exclusions, halal, budget.wire, batchCooking)

fun PrefsDto.toDomain() = MealPlanPrefs(
    cuisine = Cuisine.entries.firstOrNull { it.wire == cuisine } ?: Cuisine.ANY,
    exclusions = exclusions,
    halal = halal,
    budget = Budget.entries.firstOrNull { it.wire == budget } ?: Budget.ANY,
    batchCooking = batchCooking
)
