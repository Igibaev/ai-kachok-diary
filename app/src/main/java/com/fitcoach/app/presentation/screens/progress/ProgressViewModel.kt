package com.fitcoach.app.presentation.screens.progress

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.data.club.ClubRepository
import com.fitcoach.app.data.local.db.dao.BodyMeasurementDao
import com.fitcoach.app.data.local.db.entity.BodyMeasurementEntity
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.model.Workout
import com.fitcoach.app.domain.program.Achievements
import com.fitcoach.app.domain.program.ProgramCatalog
import com.fitcoach.app.domain.program.StreakCalculator
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.domain.repository.WorkoutRepository
import com.fitcoach.app.domain.service.ProgressShareData
import com.fitcoach.app.domain.service.ShareService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

data class ProgressUiState(
    val profile: UserProfile = UserProfile(),
    val completedTotal: Int = 0,
    /** Тренировки текущей программы (без «Отметить активность»). */
    val programCompleted: Int = 0,
    val programTotal: Int = 36,
    val programTitle: String = "",
    val weekInProgram: Int = 1,
    val streakWeeks: Int = 0,
    val totalVolumeKg: Int = 0,
    val earnedBadges: List<Achievements.Badge> = emptyList(),
    val measurements: List<BodyMeasurementEntity> = emptyList(),
    /** Замеры за последние 90 дней по возрастанию даты — для графика. */
    val chartPoints: List<BodyMeasurementEntity> = emptyList(),
    val startWeight: Float? = null,
    val currentWeight: Float? = null,
    val isSharing: Boolean = false
) {
    val allBadges: List<Achievements.Badge> get() = Achievements.all
}

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val userRepo: UserRepository,
    private val measurementDao: BodyMeasurementDao,
    private val shareService: ShareService,
    private val clubRepository: ClubRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                workoutRepo.observeCompletedWorkouts(),
                userRepo.observeProfile(),
                measurementDao.getAllMeasurements()
            ) { workouts, profile, measurements -> Triple(workouts, profile ?: UserProfile(), measurements) }
                .collect { (workouts, profile, measurements) ->
                    _state.value = build(workouts, profile, measurements).copy(isSharing = _state.value.isSharing)
                }
        }
    }

    private suspend fun build(workouts: List<Workout>, profile: UserProfile, measurements: List<BodyMeasurementEntity>): ProgressUiState {
        val zone = ZoneId.systemDefault()
        val programWorkouts = workouts.filter { !it.planKey.startsWith("ACTIVITY_") }
        val volume = programWorkouts.sumOf { w ->
            workoutRepo.getSetsForWorkoutSync(w.id).sumOf { s ->
                if (s.isDone) ((s.actualWeight ?: 0f) * (s.actualReps ?: 0)).toDouble() else 0.0
            }
        }.toInt()
        val streak = StreakCalculator.currentStreakWeeks(workouts, profile.daysPerWeek)
        val since = LocalDate.now(zone).minusDays(90).atStartOfDay(zone).toInstant().toEpochMilli()
        val chart = measurements.filter { it.date >= since }.sortedBy { it.date }
        val start = measurements.minByOrNull { it.date }?.weightKg
        val current = measurements.maxByOrNull { it.date }?.weightKg ?: profile.weightKg
        val program = ProgramCatalog.getOrDefault(profile.programKey)
        val programDone = ProgramCatalog.completedInProgram(program, workouts)
        val week = ProgramCatalog.weekFor(programDone, profile.daysPerWeek)

        return ProgressUiState(
            profile = profile,
            completedTotal = workouts.size,
            programCompleted = programDone.coerceAtMost(program.totalWeeks * profile.daysPerWeek.coerceAtLeast(1)),
            programTotal = program.totalWeeks * profile.daysPerWeek.coerceAtLeast(1),
            programTitle = program.title,
            weekInProgram = week,
            streakWeeks = streak,
            totalVolumeKg = volume,
            earnedBadges = Achievements.evaluate(workouts.size, streak, volume),
            measurements = measurements,
            chartPoints = chart,
            startWeight = start,
            currentWeight = current
        )
    }

    fun addMeasurement(weightKg: Float, waistCm: Float?, notes: String) {
        viewModelScope.launch {
            measurementDao.insertMeasurement(
                BodyMeasurementEntity(id = UUID.randomUUID().toString(), date = System.currentTimeMillis(), weightKg = weightKg, waistCm = waistCm, notes = notes)
            )
            userRepo.getProfile()?.let { if (it.onboardingCompleted) userRepo.saveProfile(it.copy(weightKg = weightKg)) }
        }
    }

    fun deleteMeasurement(m: BodyMeasurementEntity) {
        viewModelScope.launch { measurementDao.deleteMeasurement(m) }
    }

    /** Карточка прогресса для Stories; результат — для snackbar. */
    fun share(context: Context, onResult: (Result<Unit>) -> Unit) {
        val s = _state.value
        if (s.isSharing) return
        viewModelScope.launch {
            _state.value = s.copy(isSharing = true)
            val club = clubRepository.current()
            val data = ProgressShareData(
                userName = s.profile.name,
                startWeightKg = s.startWeight ?: s.profile.weightKg,
                currentWeightKg = s.currentWeight ?: s.profile.weightKg,
                workoutsCompleted = s.completedTotal,
                weeksInProgram = s.weekInProgram,
                referralCode = club.referralCode
            )
            val result = shareService.shareProgressCard(context, data)
            _state.value = _state.value.copy(isSharing = false)
            onResult(result)
        }
    }

    companion object {
        const val PROGRAM_WEEKS = 12

        fun programTitle(key: String): String = ProgramCatalog.getOrDefault(key).title
    }
}
