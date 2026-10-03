package com.fitcoach.app.presentation.navigation

sealed class Screen(val route: String) {
    // Вкладки
    object Dashboard : Screen("dashboard")
    object WorkoutHistory : Screen("workout_history")
    object Nutrition : Screen("nutrition")
    object Progress : Screen("progress")
    object Club : Screen("club")

    // Полноэкранные
    object Onboarding : Screen("onboarding")
    object WorkoutActive : Screen("workout_active/{workoutId}") {
        fun createRoute(workoutId: String) = "workout_active/$workoutId"
    }
    object WorkoutDetail : Screen("workout_detail/{workoutId}") {
        fun createRoute(workoutId: String) = "workout_detail/$workoutId"
    }
    object AddFood : Screen("add_food/{mealType}") {
        fun createRoute(mealType: String) = "add_food/$mealType"
    }
    object Water : Screen("water")
    object Chat : Screen("chat")
    object Settings : Screen("settings")
    object QrPass : Screen("qr_pass")
    object Programs : Screen("programs")

    // AI-повар
    object MealPlan : Screen("meal_plan")
    object ShoppingList : Screen("shopping_list")
    object FoodPhoto : Screen("food_photo")

    companion object {
        /** Маршруты, на которые можно перейти из уведомления (extra `nav_route`); остальное игнорируется. */
        val deepLinkable: Set<String> = setOf(ShoppingList.route, MealPlan.route, Nutrition.route, Water.route, Chat.route)
    }
}
