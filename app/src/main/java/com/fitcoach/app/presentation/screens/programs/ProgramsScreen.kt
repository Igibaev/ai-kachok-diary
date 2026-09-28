package com.fitcoach.app.presentation.screens.programs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.R
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.program.Program
import com.fitcoach.app.domain.program.ProgramCatalog
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.l10n.tr
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.components.InfoChip
import com.fitcoach.app.presentation.theme.FitCoachColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProgramsViewModel @Inject constructor(private val userRepo: UserRepository) : ViewModel() {
    val profile = userRepo.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val programs: List<Program> = ProgramCatalog.programs

    fun select(program: Program) = viewModelScope.launch {
        val p = userRepo.getProfile() ?: UserProfile()
        userRepo.saveProfile(
            p.copy(programKey = program.key, programStartDate = System.currentTimeMillis(), daysPerWeek = program.daysPerWeek)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramsScreen(onBack: () -> Unit, viewModel: ProgramsViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsState()
    val currentKey = profile?.let { ProgramCatalog.getOrDefault(it.programKey).key }
    val recommended = profile?.let { ProgramCatalog.recommend(it).key }
    var confirm by remember { mutableStateOf<Program?>(null) }

    Scaffold(
        containerColor = FitCoachColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.programs_title), color = FitCoachColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = FitCoachColors.TextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FitCoachColors.Surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.programs_intro),
                    fontSize = 13.sp, color = FitCoachColors.TextSecondary
                )
            }
            items(viewModel.programs, key = { it.key }) { program ->
                ProgramCard(
                    program = program,
                    isCurrent = program.key == currentKey,
                    isRecommended = program.key == recommended,
                    onSelect = { confirm = program }
                )
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    confirm?.let { program ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = FitCoachColors.Card,
            title = { Text(stringResource(R.string.programs_switch_title, program.title.tr()), color = FitCoachColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.programs_switch_text, program.daysPerWeek),
                    color = FitCoachColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.select(program); confirm = null },
                    colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Accent, contentColor = FitCoachColors.AccentOn)
                ) { Text(stringResource(R.string.programs_select), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.common_cancel), color = FitCoachColors.TextMuted) } }
        )
    }
}

@Composable
private fun ProgramCard(program: Program, isCurrent: Boolean, isRecommended: Boolean, onSelect: () -> Unit) {
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(program.emoji, fontSize = 32.sp)
            Column(Modifier.weight(1f)) {
                Text(program.title.tr(), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = if (isCurrent) FitCoachColors.Accent else FitCoachColors.TextPrimary)
                Text(program.subtitle.tr(), fontSize = 12.sp, color = FitCoachColors.TextSecondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(program.description.tr(), fontSize = 13.sp, color = FitCoachColors.TextSecondary)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("📅", stringResource(R.string.programs_days_per_week, program.daysPerWeek))
            InfoChip("🎯", program.level.title.tr())
            if (isRecommended) InfoChip("⭐", stringResource(R.string.programs_for_you), color = FitCoachColors.Accent)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.programs_phases, program.phases.joinToString(" → ") { it.name.tr() }), fontSize = 12.sp, color = FitCoachColors.TextMuted)
        Spacer(Modifier.height(12.dp))
        if (isCurrent) {
            OutlinedButton(
                onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, FitCoachColors.Accent),
                colors = ButtonDefaults.outlinedButtonColors(disabledContentColor = FitCoachColors.Accent),
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.programs_current)) }
        } else {
            Button(
                onClick = onSelect, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Accent, contentColor = FitCoachColors.AccentOn),
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.programs_select), fontWeight = FontWeight.Bold) }
        }
    }
}
