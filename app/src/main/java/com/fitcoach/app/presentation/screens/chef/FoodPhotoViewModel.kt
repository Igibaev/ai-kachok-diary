package com.fitcoach.app.presentation.screens.chef

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.ai.AiErrors
import com.fitcoach.app.ai.AiMode
import com.fitcoach.app.ai.chef.AiChefSelector
import com.fitcoach.app.ai.chef.ChefErrors
import com.fitcoach.app.ai.chef.ImageDownscaler
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.domain.model.FoodAnalysis
import com.fitcoach.app.domain.model.FoodItem
import com.fitcoach.app.domain.model.MealType
import com.fitcoach.app.domain.model.NutritionEntry
import com.fitcoach.app.domain.repository.NutritionRepository
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.domain.usecase.chef.FoodMath
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

data class FoodPhotoUiState(
    val imageUri: Uri? = null,
    /** Уменьшенный JPEG — превью после анализа (файл снимка к этому моменту уже удалён). */
    val imageBytes: ByteArray? = null,
    val hint: String = "",
    val analyzing: Boolean = false,
    val analysis: FoodAnalysis? = null,
    /** Позиции с правками граммов (КБЖУ пересчитаны). */
    val items: List<FoodItem> = emptyList(),
    val error: String? = null,
    val mode: AiMode = AiMode.DEMO,
    val showPrivacyHint: Boolean = false,
    val selectedMeal: MealType = defaultMeal(),
    /** Одноразовое событие: сколько позиций добавлено в дневник. */
    val addedCount: Int? = null
) {
    val hasImage: Boolean get() = imageUri != null || imageBytes != null
    val totalCalories: Int get() = items.sumOf { it.calories }
    val totalProtein: Float get() = items.sumOf { it.proteinG.toDouble() }.toFloat()
    val totalCarbs: Float get() = items.sumOf { it.carbsG.toDouble() }.toFloat()
    val totalFat: Float get() = items.sumOf { it.fatG.toDouble() }.toFloat()

    companion object {
        fun defaultMeal(hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): MealType = when {
            hour < 11 -> MealType.BREAKFAST
            hour < 15 -> MealType.LUNCH
            hour < 18 -> MealType.SNACK
            else -> MealType.DINNER
        }
    }
}

@HiltViewModel
class FoodPhotoViewModel @Inject constructor(
    private val selector: AiChefSelector,
    private val nutritionRepo: NutritionRepository,
    private val userRepo: UserRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(FoodPhotoUiState())
    val state = _state.asStateFlow()

    /** Файл текущего снимка камеры (в cacheDir/photos); удаляется после анализа или при смене фото. */
    private var cameraFile: File? = null

    init {
        viewModelScope.launch {
            _state.update {
                it.copy(mode = selector.currentMode(), showPrivacyHint = !userRepo.getFlag(FLAG_PRIVACY_HINT_SHOWN))
            }
        }
    }

    fun dismissPrivacyHint() {
        _state.update { it.copy(showPrivacyHint = false) }
        viewModelScope.launch { userRepo.setFlag(FLAG_PRIVACY_HINT_SHOWN, true) }
    }

    /** Uri для TakePicture: файл в cacheDir/photos через FileProvider (разрешение CAMERA не требуется). */
    fun newCameraUri(): Uri {
        deleteCameraFile()
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "food_${System.currentTimeMillis()}.jpg")
        cameraFile = file
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun onCameraResult(success: Boolean, uri: Uri) {
        // После смерти процесса cameraFile потерян — восстанавливаем по Uri, чтобы файл всё равно удалился после анализа.
        if (cameraFile == null) cameraFile = uri.lastPathSegment?.takeIf { it.startsWith("food_") }?.let { File(File(context.cacheDir, "photos"), it) }
        if (success) setImage(uri) else deleteCameraFile()
    }

    fun setImage(uri: Uri?) {
        if (uri == null) return
        val current = cameraFile
        if (current != null && uri.lastPathSegment != current.name) deleteCameraFile()
        _state.update { it.copy(imageUri = uri, imageBytes = null, analysis = null, items = emptyList(), error = null) }
    }

    fun setHint(value: String) = _state.update { it.copy(hint = value.take(120)) }

    /** Анализ по Uri снимка или (повтор после ошибки, когда файл уже удалён) по уменьшенным байтам в памяти. */
    fun analyze() {
        val uri = _state.value.imageUri
        val cached = _state.value.imageBytes
        if (uri == null && cached == null) return
        if (_state.value.analyzing) return
        _state.update { it.copy(analyzing = true, error = null) }
        viewModelScope.launch {
            var locale = BrandConfig.defaultLanguage
            try {
                locale = userRepo.getProfile()?.language?.ifBlank { null } ?: BrandConfig.defaultLanguage
                val bytes = if (uri != null) ImageDownscaler.downscale(context, uri) else cached
                if (bytes == null) {
                    _state.update { it.copy(analyzing = false, error = ChefErrors.imageFailed(locale)) }
                    return@launch
                }
                val result = selector.current().analyzeFoodPhoto(bytes, ImageDownscaler.MEDIA_TYPE, _state.value.hint, locale)
                deleteCameraFile()
                result.fold(
                    onSuccess = { analysis ->
                        _state.update {
                            it.copy(analyzing = false, analysis = analysis, items = analysis.items, imageBytes = bytes, imageUri = null, mode = selector.currentMode())
                        }
                    },
                    onFailure = { e -> _state.update { it.copy(analyzing = false, imageBytes = bytes, imageUri = null, error = e.message ?: AiErrors.emptyAnswer(locale)) } }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(analyzing = false, error = AiErrors.serverUnavailable(locale)) }
            }
        }
    }

    fun updateGrams(index: Int, grams: Float) {
        _state.update { s ->
            val original = s.analysis?.items?.getOrNull(index) ?: return@update s
            val items = s.items.toMutableList()
            if (index in items.indices) items[index] = FoodMath.rescale(original, grams)
            s.copy(items = items)
        }
    }

    fun selectMeal(mealType: MealType) = _state.update { it.copy(selectedMeal = mealType) }

    /** По одной записи дневника на позицию, source = "photo". */
    fun addToDiary() {
        val s = _state.value
        val items = s.items.filter { it.grams > 0f && it.calories >= 0 }
        if (items.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            items.forEach { item ->
                nutritionRepo.addEntry(
                    NutritionEntry(
                        id = UUID.randomUUID().toString(), date = now, mealType = s.selectedMeal, name = item.name,
                        calories = item.calories, proteinG = item.proteinG, carbsG = item.carbsG, fatG = item.fatG,
                        grams = item.grams, source = NutritionEntry.SOURCE_PHOTO
                    )
                )
            }
            _state.update { it.copy(addedCount = items.size) }
        }
    }

    fun consumeAdded() = _state.update { it.copy(addedCount = null) }

    fun clearError() = _state.update { it.copy(error = null) }

    /** «Другое фото»: сброс результата, превью и подсказки. */
    fun reset() {
        deleteCameraFile()
        _state.update { FoodPhotoUiState(mode = it.mode, selectedMeal = it.selectedMeal) }
    }

    private fun deleteCameraFile() {
        cameraFile?.let { runCatching { it.delete() } }
        cameraFile = null
    }

    override fun onCleared() {
        deleteCameraFile()
        super.onCleared()
    }

    companion object {
        const val FLAG_PRIVACY_HINT_SHOWN = "food_photo_privacy_hint_shown"
    }
}
