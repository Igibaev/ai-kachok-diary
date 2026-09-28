package com.fitcoach.app.presentation.screens.water

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.data.local.db.dao.WaterDao
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.model.WaterEntry
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.domain.repository.WaterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.fitcoach.app.domain.util.DayBounds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Итог одного дня для мини-баров: дата и выпито мл. */
data class DayTotal(val date: LocalDate, val totalMl: Int)

data class WaterUiState(
    val entries: List<WaterEntry> = emptyList(),
    val totalMl: Int = 0,
    val profile: UserProfile = UserProfile(),
    /** 7 дней по возрастанию даты, последний — сегодня. */
    val week: List<DayTotal> = emptyList()
)

@HiltViewModel
class WaterViewModel @Inject constructor(
    private val waterRepo: WaterRepository,
    private val waterDao: WaterDao,
    userRepo: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WaterUiState())
    val state = _state.asStateFlow()

    /** Начало текущего дня; переоценивается раз в минуту, чтобы экран, оставленный через полночь, не показывал вчера. */
    private val today = flow {
        while (true) {
            emit(DayBounds.startOfDay())
            delay(60_000)
        }
    }.distinctUntilChanged()

    init {
        viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            today.flatMapLatest { day ->
                combine(
                    waterRepo.getEntriesForDate(day),
                    waterRepo.getTotalForDate(day),
                    userRepo.observeProfile()
                ) { entries, total, profile -> Triple(entries, total, profile ?: UserProfile()) }
            }.collect { (entries, total, profile) ->
                _state.value = WaterUiState(entries, total, profile, loadWeek())
            }
        }
    }

    private suspend fun loadWeek(): List<DayTotal> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from = today.minusDays(6)
        val since = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val byDay = waterDao.getEntriesSince(since)
            .groupBy { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> list.sumOf { it.amountMl } }
        return (0..6).map { i -> val d = from.plusDays(i.toLong()); DayTotal(d, byDay[d] ?: 0) }
    }

    fun addWater(ml: Int) = viewModelScope.launch { if (ml in 1..5000) waterRepo.addWater(ml) }
    fun deleteEntry(entry: WaterEntry) = viewModelScope.launch { waterRepo.deleteEntry(entry) }
}
