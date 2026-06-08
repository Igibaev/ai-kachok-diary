package com.fitcoach.app.presentation.screens.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.data.local.db.dao.BodyMeasurementDao
import com.fitcoach.app.data.local.db.entity.BodyMeasurementEntity
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.domain.repository.WorkoutRepository
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.components.LabeledProgressBar
import com.fitcoach.app.presentation.theme.FitCoachColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ProgressUiState(
    val completedWorkouts: Int = 0,
    val profile: UserProfile = UserProfile(),
    val measurements: List<BodyMeasurementEntity> = emptyList()
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val userRepo: UserRepository,
    private val measurementDao: BodyMeasurementDao
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                workoutRepo.getCompletedWorkoutCount(),
                userRepo.observeProfile(),
                measurementDao.getAllMeasurements()
            ) { count, profile, measurements ->
                ProgressUiState(count, profile ?: UserProfile(), measurements)
            }.collect { _state.value = it }
        }
    }

    fun addMeasurement(weightKg: Float, waistCm: Float?, notes: String) {
        viewModelScope.launch {
            measurementDao.insertMeasurement(
                BodyMeasurementEntity(
                    id = UUID.randomUUID().toString(),
                    date = System.currentTimeMillis(),
                    weightKg = weightKg,
                    waistCm = waistCm,
                    notes = notes
                )
            )
        }
    }
}

@Composable
fun ProgressScreen(viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var showAddMeasurement by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FitCoachColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Прогресс", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FitCoachColors.TextPrimary)
                FloatingActionButton(
                    onClick = { showAddMeasurement = true },
                    containerColor = FitCoachColors.Accent,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = FitCoachColors.Background)
                }
            }

            // Program progress
            FitCard {
                Text("ПРОГРЕСС ПРОГРАММЫ", style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
                Spacer(Modifier.height(12.dp))
                LabeledProgressBar(
                    value = state.completedWorkouts.toFloat(),
                    max = 36f,
                    color = FitCoachColors.Accent,
                    label = "Тренировок",
                    sub = "${state.completedWorkouts}/36"
                )
                Spacer(Modifier.height(8.dp))

                // Phase progress
                val phase1Done = minOf(state.completedWorkouts, 12)
                val phase2Done = minOf((state.completedWorkouts - 12).coerceAtLeast(0), 16)
                val phase3Done = minOf((state.completedWorkouts - 28).coerceAtLeast(0), 16)

                Spacer(Modifier.height(8.dp))
                LabeledProgressBar(phase1Done.toFloat(), 12f, FitCoachColors.PhaseBlue, "Фаза I", "$phase1Done/12")
                Spacer(Modifier.height(8.dp))
                LabeledProgressBar(phase2Done.toFloat(), 16f, FitCoachColors.PhaseOrange, "Фаза II", "$phase2Done/16")
                Spacer(Modifier.height(8.dp))
                LabeledProgressBar(phase3Done.toFloat(), 16f, FitCoachColors.PhaseAccent, "Фаза III", "$phase3Done/16")
            }

            // Weight measurements
            if (state.measurements.isNotEmpty()) {
                FitCard {
                    Text("ЗАМЕРЫ ВЕСА", style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
                    Spacer(Modifier.height(12.dp))
                    val latest = state.measurements.first()
                    val oldest = state.measurements.last()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("Текущий", "${latest.weightKg} кг", FitCoachColors.Accent)
                        if (state.measurements.size > 1) {
                            val diff = latest.weightKg - oldest.weightKg
                            StatItem("Изменение", "${if (diff > 0) "+" else ""}${"%.1f".format(diff)} кг",
                                if (diff <= 0) FitCoachColors.Success else FitCoachColors.Error)
                        }
                        state.profile.targetWeightKg?.let {
                            StatItem("Цель", "$it кг", FitCoachColors.PhaseBlue)
                        }
                    }
                }

                FitCard {
                    Text("ИСТОРИЯ ЗАМЕРОВ", style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
                    Spacer(Modifier.height(8.dp))
                    state.measurements.take(10).forEach { m ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(m.date)), fontSize = 13.sp, color = FitCoachColors.TextSecondary)
                            Text("${m.weightKg} кг", fontSize = 13.sp, color = FitCoachColors.TextPrimary, fontWeight = FontWeight.Medium)
                            m.waistCm?.let { Text("талия: $it см", fontSize = 11.sp, color = FitCoachColors.TextMuted) }
                        }
                    }
                }
            } else {
                FitCard {
                    Text("Нет замеров", color = FitCoachColors.TextMuted, modifier = Modifier.fillMaxWidth())
                    Text("Нажми + чтобы добавить первый замер", fontSize = 12.sp, color = FitCoachColors.TextMuted)
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    if (showAddMeasurement) {
        AddMeasurementDialog(
            onDismiss = { showAddMeasurement = false },
            onAdd = { weight, waist, notes ->
                viewModel.addMeasurement(weight, waist, notes)
                showAddMeasurement = false
            }
        )
    }
}

@Composable
private fun StatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 11.sp, color = FitCoachColors.TextMuted)
    }
}

@Composable
private fun AddMeasurementDialog(onDismiss: () -> Unit, onAdd: (Float, Float?, String) -> Unit) {
    var weight by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FitCoachColors.Card,
        title = { Text("Новый замер", color = FitCoachColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weight, onValueChange = { weight = it },
                    label = { Text("Вес (кг)", color = FitCoachColors.TextMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FitCoachColors.TextPrimary, unfocusedTextColor = FitCoachColors.TextPrimary,
                        focusedBorderColor = FitCoachColors.Accent, unfocusedBorderColor = FitCoachColors.Border
                    )
                )
                OutlinedTextField(
                    value = waist, onValueChange = { waist = it },
                    label = { Text("Талия (см, опционально)", color = FitCoachColors.TextMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FitCoachColors.TextPrimary, unfocusedTextColor = FitCoachColors.TextPrimary,
                        focusedBorderColor = FitCoachColors.Accent, unfocusedBorderColor = FitCoachColors.Border
                    )
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Заметки", color = FitCoachColors.TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FitCoachColors.TextPrimary, unfocusedTextColor = FitCoachColors.TextPrimary,
                        focusedBorderColor = FitCoachColors.Accent, unfocusedBorderColor = FitCoachColors.Border
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(weight.toFloatOrNull() ?: return@Button, waist.toFloatOrNull(), notes) },
                enabled = weight.toFloatOrNull() != null,
                colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Accent)
            ) { Text("Сохранить", color = FitCoachColors.Background) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = FitCoachColors.TextMuted) } }
    )
}
