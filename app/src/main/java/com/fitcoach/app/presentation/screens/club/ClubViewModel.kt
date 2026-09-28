package com.fitcoach.app.presentation.screens.club

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.data.club.ClubContent
import com.fitcoach.app.data.club.ClubRepository
import com.fitcoach.app.data.club.Promo
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class ClubUiState(
    val content: ClubContent = ClubContent.EMPTY,
    val activePromos: List<Promo> = emptyList(),
    val today: Int = LocalDate.now().dayOfWeek.value,
    val userName: String = "",
    val loaded: Boolean = false
)

@HiltViewModel
class ClubViewModel @Inject constructor(
    clubRepository: ClubRepository,
    userRepository: UserRepository
) : ViewModel() {

    val state: StateFlow<ClubUiState> = combine(
        clubRepository.observe(),
        userRepository.observeProfile()
    ) { content, profile ->
        val today = LocalDate.now()
        ClubUiState(
            content = content,
            activePromos = content.activePromos(today),
            today = today.dayOfWeek.value,
            userName = (profile ?: UserProfile()).name,
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClubUiState())
}

/** Только акции — для баннера на главной. */
@HiltViewModel
class PromoBannerViewModel @Inject constructor(
    clubRepository: ClubRepository
) : ViewModel() {
    val promos: StateFlow<List<Promo>> = clubRepository.observe()
        .map { it.activePromos(LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
