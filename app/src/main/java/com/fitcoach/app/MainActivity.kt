package com.fitcoach.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.presentation.navigation.AppNavHost
import com.fitcoach.app.presentation.navigation.Screen
import com.fitcoach.app.presentation.theme.FitCoachColors
import com.fitcoach.app.presentation.theme.FitCoachTheme
import com.fitcoach.app.workers.Notifications
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* результат не критичен */ }

    /** Маршрут из уведомления (extra `nav_route`); обрабатывается один раз после старта графа навигации. */
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pendingRoute = routeFrom(intent)

        var startDestination: String? by mutableStateOf(null)
        splash.setKeepOnScreenCondition { startDestination == null }

        setContent {
            val vm: MainViewModel = hiltViewModel()
            val start by vm.startDestination.collectAsState()
            startDestination = start
            // Разрешение на уведомления просим не поверх splash/онбординга, а когда пользователь
            // уже на главной: вернувшийся — сразу, новый — после завершения онбординга.
            LaunchedEffect(start) {
                if (start == Screen.Dashboard.route) requestNotificationPermissionIfNeeded()
            }
            FitCoachTheme {
                start?.let {
                    MainAppContent(
                        startDestination = it,
                        onOnboardingFinished = { requestNotificationPermissionIfNeeded() },
                        pendingRoute = pendingRoute,
                        onRouteConsumed = { pendingRoute = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeFrom(intent)?.let { pendingRoute = it }
    }

    private fun routeFrom(intent: Intent?): String? =
        intent?.getStringExtra(Notifications.EXTRA_ROUTE)?.takeIf { it in Screen.deepLinkable }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(userRepository: UserRepository) : ViewModel() {
    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = userRepository.getProfile()
            _startDestination.value =
                if (profile?.onboardingCompleted == true) Screen.Dashboard.route else Screen.Onboarding.route
        }
    }
}

data class BottomNavItem(
    val screen: Screen,
    @StringRes val labelRes: Int,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, R.string.nav_home, Icons.Default.Home),
    BottomNavItem(Screen.WorkoutHistory, R.string.nav_workouts, Icons.Default.FitnessCenter),
    BottomNavItem(Screen.Nutrition, R.string.nav_nutrition, Icons.Default.Restaurant),
    BottomNavItem(Screen.Progress, R.string.nav_progress, Icons.AutoMirrored.Filled.TrendingUp),
    BottomNavItem(Screen.Club, R.string.nav_club, Icons.Default.Storefront)
)

@Composable
private fun MainAppContent(
    startDestination: String,
    onOnboardingFinished: () -> Unit,
    pendingRoute: String? = null,
    onRouteConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Переход из уведомления — только когда пользователь уже на главной (онбординг не прерываем).
    LaunchedEffect(pendingRoute, startDestination) {
        val route = pendingRoute ?: return@LaunchedEffect
        if (startDestination != Screen.Dashboard.route) return@LaunchedEffect
        navController.navigate(route) { launchSingleTop = true }
        onRouteConsumed()
    }

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
    }

    Scaffold(
        containerColor = FitCoachColors.Background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = FitCoachColors.Surface,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                        val label = stringResource(item.labelRes)
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = label) },
                            label = { Text(label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    // Не findStartDestination(): в первой сессии это Onboarding, которого нет в стеке,
                                    // и popUpTo молча игнорировался бы (вкладки копились, saveState не работал).
                                    popUpTo(Screen.Dashboard.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = FitCoachColors.Accent,
                                selectedTextColor = FitCoachColors.Accent,
                                unselectedIconColor = FitCoachColors.TextMuted,
                                unselectedTextColor = FitCoachColors.TextMuted,
                                indicatorColor = FitCoachColors.AccentSoft
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        AppNavHost(
            navController = navController,
            startDestination = startDestination,
            onOnboardingFinished = onOnboardingFinished,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
