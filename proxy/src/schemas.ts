/**
 * Zod-схемы структурированных ответов модели (AI-повар). Провайдер-независимы:
 *  - anthropic → `output_config.format = zodOutputFormat(schema)` (providers/anthropic.ts);
 *  - openai-совместимые → JSON Schema через `z.toJSONSchema` + `response_format` + `safeParse` (providers/openaiCompatible.ts).
 * Без ограничений вида .min()/.max() и без optional-полей: это нужно и для подмножества JSON Schema у Anthropic,
 * и для strict-режима OpenAI (все поля в required, additionalProperties:false). Числа проверяются
 * и нормализуются на сервере (см. shopping.ts / mealPlan.ts / foodPhoto.ts).
 */
import { z } from "zod";

export const INGREDIENT_CATEGORIES = ["meat_fish", "dairy", "grains", "produce", "other"] as const;
export type IngredientCategory = (typeof INGREDIENT_CATEGORIES)[number];

export const MEAL_SLOTS = ["breakfast", "lunch", "dinner", "snack"] as const;
export type MealSlot = (typeof MEAL_SLOTS)[number];

export const IngredientSchema = z.object({
  name: z.string(),
  grams: z.number(),
  category: z.enum(INGREDIENT_CATEGORIES),
});

export const MealSchema = z.object({
  slot: z.enum(MEAL_SLOTS),
  title: z.string(),
  timeMinutes: z.number(),
  ingredients: z.array(IngredientSchema),
  calories: z.number(),
  proteinG: z.number(),
  carbsG: z.number(),
  fatG: z.number(),
  steps: z.array(z.string()),
});

export const DaySchema = z.object({
  day: z.number(),
  meals: z.array(MealSchema),
  totalCalories: z.number(),
  totalProteinG: z.number(),
  totalCarbsG: z.number(),
  totalFatG: z.number(),
});

/**
 * Что просим у модели: дни и заметки. Список покупок модель НЕ формирует — сервер
 * детерминированно собирает его из ингредиентов (меньше выходных токенов, нет ошибок суммирования).
 */
export const MealPlanModelSchema = z.object({
  days: z.array(DaySchema),
  notes: z.string(),
});
export type MealPlanModelOutput = z.infer<typeof MealPlanModelSchema>;

export const SHOPPING_UNITS = ["g", "kg", "pcs", "ml", "l"] as const;
export type ShoppingUnit = (typeof SHOPPING_UNITS)[number];

export interface ShoppingItem {
  name: string;
  quantity: number;
  unit: ShoppingUnit;
}

export interface ShoppingGroup {
  /** Ключ отдела магазина: meat_fish | dairy | grains | produce | other (клиент локализует). */
  category: string;
  items: ShoppingItem[];
}

/** Полный ответ /v1/meal-plan — контракт с приложением. */
export type MealPlan = MealPlanModelOutput & { shopping: ShoppingGroup[] };

export type Ingredient = z.infer<typeof IngredientSchema>;
export type Meal = z.infer<typeof MealSchema>;
export type PlanDay = z.infer<typeof DaySchema>;

export const FoodItemSchema = z.object({
  name: z.string(),
  grams: z.number(),
  calories: z.number(),
  proteinG: z.number(),
  carbsG: z.number(),
  fatG: z.number(),
  confidence: z.enum(["low", "medium", "high"]),
});

export const FoodAnalysisSchema = z.object({
  items: z.array(FoodItemSchema),
  totalCalories: z.number(),
  totalProteinG: z.number(),
  totalCarbsG: z.number(),
  totalFatG: z.number(),
  note: z.string(),
  isFood: z.boolean(),
});
export type FoodAnalysis = z.infer<typeof FoodAnalysisSchema>;
