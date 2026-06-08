package com.fitcoach.app.presentation.screens.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.domain.model.MealType
import com.fitcoach.app.domain.model.NutritionEntry
import com.fitcoach.app.domain.repository.NutritionRepository
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.theme.FitCoachColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddFoodViewModel @Inject constructor(
    private val nutritionRepo: NutritionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val mealType: String = checkNotNull(savedStateHandle["mealType"])
    private val _templates = MutableStateFlow<List<NutritionEntry>>(emptyList())
    val templates = _templates.asStateFlow()

    private val _searchResults = MutableStateFlow<List<NutritionEntry>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    init {
        viewModelScope.launch {
            _templates.value = nutritionRepo.getTemplates()
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            _searchResults.value = if (query.isBlank()) emptyList()
            else nutritionRepo.searchFoods(query)
        }
    }

    fun addFromTemplate(template: NutritionEntry, grams: Float, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val factor = grams / (template.grams ?: 100f)
            val entry = template.copy(
                id = UUID.randomUUID().toString(),
                date = System.currentTimeMillis(),
                mealType = try { MealType.valueOf(mealType) } catch (e: Exception) { MealType.SNACK },
                calories = (template.calories * factor).toInt(),
                proteinG = template.proteinG * factor,
                carbsG = template.carbsG * factor,
                fatG = template.fatG * factor,
                grams = grams
            )
            nutritionRepo.addEntry(entry)
            onSuccess()
        }
    }

    fun addManual(name: String, calories: Int, proteinG: Float, carbsG: Float, fatG: Float, grams: Float, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val entry = NutritionEntry(
                id = UUID.randomUUID().toString(),
                date = System.currentTimeMillis(),
                mealType = try { MealType.valueOf(mealType) } catch (e: Exception) { MealType.SNACK },
                name = name, calories = calories, proteinG = proteinG, carbsG = carbsG, fatG = fatG, grams = grams
            )
            nutritionRepo.addEntry(entry)
            onSuccess()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(
    mealType: String,
    onBack: () -> Unit,
    viewModel: AddFoodViewModel = hiltViewModel()
) {
    val templates by viewModel.templates.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    var query by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf<NutritionEntry?>(null) }
    var gramsInput by remember { mutableStateOf("") }

    val displayList = if (query.isNotBlank()) searchResults else templates

    Scaffold(
        containerColor = FitCoachColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("Добавить еду", color = FitCoachColors.TextPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = FitCoachColors.TextPrimary) } },
                actions = {
                    TextButton(onClick = { showManual = true }) { Text("Вручную", color = FitCoachColors.Accent) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FitCoachColors.Surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.search(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Поиск продукта...", color = FitCoachColors.TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = FitCoachColors.TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = FitCoachColors.TextPrimary,
                    unfocusedTextColor = FitCoachColors.TextPrimary,
                    focusedBorderColor = FitCoachColors.Accent,
                    unfocusedBorderColor = FitCoachColors.Border
                ),
                shape = RoundedCornerShape(12.dp)
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(displayList) { food ->
                    FoodItem(food = food, onClick = { selectedTemplate = food; gramsInput = food.grams?.toString() ?: "100" })
                }
            }
        }
    }

    selectedTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { selectedTemplate = null },
            containerColor = FitCoachColors.Card,
            title = { Text(template.name, color = FitCoachColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${template.calories} ккал на ${template.grams ?: 100}г", color = FitCoachColors.TextSecondary, fontSize = 13.sp)
                    OutlinedTextField(
                        value = gramsInput,
                        onValueChange = { gramsInput = it },
                        label = { Text("Граммы", color = FitCoachColors.TextMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = FitCoachColors.TextPrimary, unfocusedTextColor = FitCoachColors.TextPrimary,
                            focusedBorderColor = FitCoachColors.Accent, unfocusedBorderColor = FitCoachColors.Border
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val grams = gramsInput.toFloatOrNull() ?: return@Button
                        viewModel.addFromTemplate(template, grams) { onBack() }
                        selectedTemplate = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Accent)
                ) { Text("Добавить", color = FitCoachColors.Background) }
            },
            dismissButton = {
                TextButton(onClick = { selectedTemplate = null }) { Text("Отмена", color = FitCoachColors.TextMuted) }
            }
        )
    }

    if (showManual) {
        ManualFoodDialog(
            onDismiss = { showManual = false },
            onAdd = { name, cal, prot, carbs, fat, grams ->
                viewModel.addManual(name, cal, prot, carbs, fat, grams) { onBack() }
                showManual = false
            }
        )
    }
}

@Composable
private fun FoodItem(food: NutritionEntry, onClick: () -> Unit) {
    FitCard(modifier = Modifier.clickable { onClick() }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(food.name, fontWeight = FontWeight.Medium, color = FitCoachColors.TextPrimary, fontSize = 14.sp)
                Text("${food.calories} ккал · Б${food.proteinG.toInt()}/У${food.carbsG.toInt()}/Ж${food.fatG.toInt()}г · ${food.grams?.toInt() ?: 100}г",
                    fontSize = 11.sp, color = FitCoachColors.TextMuted)
            }
            Icon(Icons.Default.Add, null, tint = FitCoachColors.Accent)
        }
    }
}

@Composable
private fun ManualFoodDialog(onDismiss: () -> Unit, onAdd: (String, Int, Float, Float, Float, Float) -> Unit) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var grams by remember { mutableStateOf("100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FitCoachColors.Card,
        title = { Text("Добавить вручную", color = FitCoachColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Название" to name,
                    "Калории" to calories,
                    "Белок (г)" to protein,
                    "Углеводы (г)" to carbs,
                    "Жиры (г)" to fat,
                    "Граммы" to grams
                ).forEachIndexed { idx, (label, value) ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { v ->
                            when (idx) {
                                0 -> name = v; 1 -> calories = v; 2 -> protein = v
                                3 -> carbs = v; 4 -> fat = v; 5 -> grams = v
                            }
                        },
                        label = { Text(label, color = FitCoachColors.TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = if (idx == 0) KeyboardOptions.Default else KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = FitCoachColors.TextPrimary, unfocusedTextColor = FitCoachColors.TextPrimary,
                            focusedBorderColor = FitCoachColors.Accent, unfocusedBorderColor = FitCoachColors.Border
                        ),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(name, calories.toIntOrNull() ?: 0, protein.toFloatOrNull() ?: 0f,
                        carbs.toFloatOrNull() ?: 0f, fat.toFloatOrNull() ?: 0f, grams.toFloatOrNull() ?: 100f)
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Accent)
            ) { Text("Добавить", color = FitCoachColors.Background) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = FitCoachColors.TextMuted) } }
    )
}
