package com.fitcoach.app.presentation.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object WorkoutActive : Screen("workout_active/{workoutId}") {
        fun createRoute(workoutId: String) = "workout_active/$workoutId"
    }
    object WorkoutHistory : Screen("workout_history")
    object WorkoutDetail : Screen("workout_detail/{workoutId}") {
        fun createRoute(workoutId: String) = "workout_detail/$workoutId"
    }
    object Nutrition : Screen("nutrition")
    object AddFood : Screen("add_food/{mealType}") {
        fun createRoute(mealType: String) = "add_food/$mealType"
    }
    object Water : Screen("water")
    object Chat : Screen("chat")
    object Progress : Screen("progress")
    object Settings : Screen("settings")
    object Onboarding : Screen("onboarding")
}
