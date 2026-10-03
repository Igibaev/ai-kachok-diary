package com.fitcoach.app.presentation.screens.chef

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitcoach.app.R
import com.fitcoach.app.domain.model.IngredientCategory
import com.fitcoach.app.domain.model.ShoppingItem
import com.fitcoach.app.domain.usecase.chef.FoodMath
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.components.LabeledProgressBar
import com.fitcoach.app.presentation.components.PrimaryButton
import com.fitcoach.app.presentation.theme.FitCoachColors

@Composable
fun ShoppingListScreen(
    onBack: () -> Unit,
    onOpenMealPlan: () -> Unit,
    viewModel: ShoppingListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        containerColor = FitCoachColors.Background,
        topBar = {
            ChefTopBar(title = stringResource(R.string.shopping_title), onBack = onBack) {
                if (state.items.isNotEmpty()) {
                    IconButton(onClick = { viewModel.share(context) }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.shopping_share), tint = FitCoachColors.Accent)
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(FitCoachColors.Background)) {
            when {
                state.loading -> CircularProgressIndicator(color = FitCoachColors.Accent, modifier = Modifier.align(Alignment.Center))
                state.items.isEmpty() -> Column(modifier = Modifier.padding(16.dp)) {
                    ChefEmptyState(
                        emoji = "🛒",
                        title = stringResource(R.string.shopping_empty_title),
                        text = stringResource(R.string.shopping_empty_text),
                        buttonText = stringResource(R.string.shopping_open_plan),
                        onClick = onOpenMealPlan
                    )
                }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        FitCard {
                            LabeledProgressBar(
                                value = state.checkedCount.toFloat(),
                                max = state.total.toFloat(),
                                color = FitCoachColors.Success,
                                label = if (state.checkedCount == state.total) stringResource(R.string.shopping_all_done) else stringResource(R.string.shopping_progress, state.checkedCount, state.total),
                                sub = "${state.checkedCount}/${state.total}"
                            )
                        }
                    }
                    state.groups.forEach { (category, items) ->
                        item(key = category.name) { ShoppingGroupCard(category, items, onToggle = viewModel::toggle) }
                    }
                    item {
                        PrimaryButton(text = "📤 " + stringResource(R.string.shopping_share), onClick = { viewModel.share(context) })
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = viewModel::uncheckAll, modifier = Modifier.fillMaxWidth(), enabled = state.checkedCount > 0) {
                            Text(stringResource(R.string.shopping_uncheck_all), color = if (state.checkedCount > 0) FitCoachColors.TextPrimary else FitCoachColors.TextMuted)
                        }
                    }
                    item { ShoppingReminderCard(reminder = state.reminder, onChange = viewModel::saveReminder) }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ShoppingGroupCard(category: IngredientCategory, items: List<ShoppingItem>, onToggle: (ShoppingItem) -> Unit) {
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ShoppingListViewModel.categoryEmoji(category), fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(ShoppingListViewModel.categoryRes(category)), fontWeight = FontWeight.SemiBold, color = FitCoachColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            Text("${items.count { it.checked }}/${items.size}", fontSize = 12.sp, color = FitCoachColors.TextMuted)
        }
        HorizontalDivider(color = FitCoachColors.Border, modifier = Modifier.padding(vertical = 8.dp))
        items.forEach { item ->
            val color = if (item.checked) FitCoachColors.TextMuted else FitCoachColors.TextPrimary
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle(item) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.checked,
                    onCheckedChange = { onToggle(item) },
                    colors = CheckboxDefaults.colors(checkedColor = FitCoachColors.Success, uncheckedColor = FitCoachColors.TextMuted, checkmarkColor = FitCoachColors.Background)
                )
                Text(
                    item.name, color = color, fontSize = 14.sp, modifier = Modifier.weight(1f),
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null
                )
                Text(
                    FoodMath.formatQuantity(item.quantity, stringResource(ShoppingListViewModel.unitRes(item.unit))),
                    color = color, fontSize = 14.sp
                )
            }
        }
    }
}
