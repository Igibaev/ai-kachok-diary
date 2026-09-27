package com.fitcoach.app.presentation.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.presentation.theme.FitCoachColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** ЗАГЛУШКА: полноценный onboarding реализует агент «Программы/онбординг». */
@HiltViewModel
class OnboardingViewModel @Inject constructor(private val userRepo: UserRepository) : ViewModel() {
    fun complete(onDone: () -> Unit) = viewModelScope.launch {
        val p = userRepo.getProfile() ?: UserProfile()
        userRepo.saveProfile(p.copy(onboardingCompleted = true))
        onDone()
    }
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    Column(
        modifier = Modifier.fillMaxSize().background(FitCoachColors.Background).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Добро пожаловать", color = FitCoachColors.TextPrimary)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { viewModel.complete(onFinished) }) { Text("Начать") }
    }
}
