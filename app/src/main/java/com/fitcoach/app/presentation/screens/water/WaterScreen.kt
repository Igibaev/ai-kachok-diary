package com.fitcoach.app.presentation.screens.water

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.model.WaterEntry
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.domain.repository.WaterRepository
import com.fitcoach.app.presentation.components.CircularProgress
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.theme.FitCoachColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class WaterUiState(
    val entries: List<WaterEntry> = emptyList(),
    val totalMl: Int = 0,
    val profile: UserProfile = UserProfile()
)

@HiltViewModel
class WaterViewModel @Inject constructor(
    private val waterRepo: WaterRepository,
    private val userRepo: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WaterUiState())
    val state = _state.asStateFlow()

    init {
        val today = System.currentTimeMillis()
        viewModelScope.launch {
            combine(
                waterRepo.getEntriesForDate(today),
                waterRepo.getTotalForDate(today),
                userRepo.observeProfile()
            ) { entries, total, profile ->
                WaterUiState(entries, total, profile ?: UserProfile())
            }.collect { _state.value = it }
        }
    }

    fun addWater(ml: Int) = viewModelScope.launch { waterRepo.addWater(ml) }
    fun deleteEntry(entry: WaterEntry) = viewModelScope.launch { waterRepo.deleteEntry(entry) }
}

@Composable
fun WaterScreen(viewModel: WaterViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var customInput by remember { mutableStateOf("") }
    val df = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FitCoachColors.Background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Водный баланс", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FitCoachColors.TextPrimary)

        // Big circular progress
        CircularProgress(
            value = state.totalMl.toFloat(),
            max = state.profile.waterGoalMl.toFloat(),
            color = FitCoachColors.PhaseBlue,
            label = "мл",
            size = 180.dp,
            strokeWidth = 16.dp
        )

        Text(
            text = "${state.totalMl} мл из ${state.profile.waterGoalMl} мл",
            fontSize = 16.sp,
            color = FitCoachColors.TextSecondary
        )

        // Quick add buttons
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(150, 250, 500).forEach { amount ->
                OutlinedButton(
                    onClick = { viewModel.addWater(amount) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FitCoachColors.PhaseBlue),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FitCoachColors.PhaseBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("+${amount}мл", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Custom input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = customInput,
                onValueChange = { customInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Другое количество, мл", color = FitCoachColors.TextMuted) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = FitCoachColors.TextPrimary,
                    unfocusedTextColor = FitCoachColors.TextPrimary,
                    focusedBorderColor = FitCoachColors.PhaseBlue,
                    unfocusedBorderColor = FitCoachColors.Border
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Button(
                onClick = {
                    val ml = customInput.toIntOrNull() ?: return@Button
                    viewModel.addWater(ml)
                    customInput = ""
                },
                colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.PhaseBlue),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Добавить", color = FitCoachColors.Background) }
        }

        // History
        if (state.entries.isNotEmpty()) {
            Text("История за сегодня", style = MaterialTheme.typography.labelMedium, color = FitCoachColors.TextMuted, modifier = Modifier.fillMaxWidth())
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(state.entries.reversed()) { entry ->
                    FitCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("💧", fontSize = 20.sp)
                                Column {
                                    Text("${entry.amountMl} мл", fontWeight = FontWeight.Bold, color = FitCoachColors.PhaseBlue)
                                    Text(df.format(Date(entry.createdAt)), fontSize = 11.sp, color = FitCoachColors.TextMuted)
                                }
                            }
                            IconButton(onClick = { viewModel.deleteEntry(entry) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, null, tint = FitCoachColors.TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
