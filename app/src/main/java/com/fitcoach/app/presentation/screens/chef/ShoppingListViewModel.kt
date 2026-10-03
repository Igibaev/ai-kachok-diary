package com.fitcoach.app.presentation.screens.chef

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.R
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.domain.model.IngredientCategory
import com.fitcoach.app.domain.model.SavedMealPlan
import com.fitcoach.app.domain.model.ShoppingItem
import com.fitcoach.app.domain.model.ShoppingReminder
import com.fitcoach.app.domain.model.ShoppingUnit
import com.fitcoach.app.domain.repository.MealPlanRepository
import com.fitcoach.app.domain.usecase.chef.FoodMath
import com.fitcoach.app.workers.ShoppingReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShoppingUiState(
    val loading: Boolean = true,
    val plan: SavedMealPlan? = null,
    val items: List<ShoppingItem> = emptyList(),
    val reminder: ShoppingReminder = ShoppingReminder()
) {
    val total: Int get() = items.size
    val checkedCount: Int get() = items.count { it.checked }

    /** Группы по отделам в порядке [IngredientCategory]; внутри группы купленные — вниз. */
    val groups: List<Pair<IngredientCategory, List<ShoppingItem>>>
        get() = IngredientCategory.entries.mapNotNull { cat ->
            val list = items.filter { it.category == cat }
            if (list.isEmpty()) null else cat to list.sortedWith(compareBy<ShoppingItem> { it.checked }.thenBy { it.sortOrder })
        }
}

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val mealPlanRepo: MealPlanRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ShoppingUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(mealPlanRepo.observeActivePlan(), mealPlanRepo.observeShoppingItems(), mealPlanRepo.observeReminder()) { plan, items, reminder ->
                ShoppingUiState(loading = false, plan = plan, items = items, reminder = reminder)
            }.collect { _state.value = it }
        }
    }

    fun toggle(item: ShoppingItem) {
        viewModelScope.launch { mealPlanRepo.setChecked(item.id, !item.checked) }
    }

    fun uncheckAll() {
        viewModelScope.launch { mealPlanRepo.uncheckAll() }
    }

    fun saveReminder(reminder: ShoppingReminder) {
        viewModelScope.launch {
            mealPlanRepo.saveReminder(reminder)
            ShoppingReminderScheduler.schedule(context, reminder)
        }
    }

    /** Текст списка для WhatsApp и любого приложения: эмодзи отделов, галочки у купленного, подпись клуба. */
    fun shareText(ctx: Context): String {
        val s = _state.value
        val res = ctx.resources
        val sb = StringBuilder()
        sb.appendLine(res.getString(R.string.shopping_share_header, s.plan?.days ?: 0, BrandConfig.clubName))
        s.groups.forEach { (cat, items) ->
            sb.appendLine()
            sb.appendLine("${categoryEmoji(cat)} ${res.getString(categoryRes(cat))}")
            items.forEach { item ->
                val mark = if (item.checked) "✅" else "▫️"
                sb.appendLine("$mark ${item.name} — ${FoodMath.formatQuantity(item.quantity, res.getString(unitRes(item.unit)))}")
            }
        }
        sb.appendLine()
        sb.append(res.getString(R.string.shopping_share_footer, BrandConfig.appName))
        return sb.toString()
    }

    fun share(ctx: Context) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText(ctx))
        }
        val chooser = Intent.createChooser(send, ctx.getString(R.string.shopping_share_chooser))
        runCatching { ctx.startActivity(chooser) }
    }

    companion object {
        fun categoryRes(cat: IngredientCategory): Int = when (cat) {
            IngredientCategory.MEAT_FISH -> R.string.shopping_category_meat_fish
            IngredientCategory.DAIRY -> R.string.shopping_category_dairy
            IngredientCategory.GRAINS -> R.string.shopping_category_grains
            IngredientCategory.PRODUCE -> R.string.shopping_category_produce
            IngredientCategory.OTHER -> R.string.shopping_category_other
        }

        fun categoryEmoji(cat: IngredientCategory): String = when (cat) {
            IngredientCategory.MEAT_FISH -> "🥩"
            IngredientCategory.DAIRY -> "🥛"
            IngredientCategory.GRAINS -> "🌾"
            IngredientCategory.PRODUCE -> "🥦"
            IngredientCategory.OTHER -> "🧂"
        }

        fun unitRes(unit: ShoppingUnit): Int = when (unit) {
            ShoppingUnit.G -> R.string.unit_g
            ShoppingUnit.KG -> R.string.unit_kg
            ShoppingUnit.PCS -> R.string.unit_pcs
            ShoppingUnit.ML -> R.string.unit_ml
            ShoppingUnit.L -> R.string.unit_l
        }
    }
}
