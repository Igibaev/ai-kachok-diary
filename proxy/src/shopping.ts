/**
 * Серверная агрегация списка покупок из ингредиентов плана питания.
 * Чистые функции без зависимостей от рантайма — покрыты unit-тестами (test/shopping.test.ts).
 *
 * Правила (контракт AI-повара):
 *  - одинаковые названия объединяются без учёта регистра и лишних пробелов;
 *  - граммы суммируются по всем дням и приёмам;
 *  - сумма > 1000 г → килограммы с округлением до 0,1; иначе граммы, округлённые до целого;
 *  - позиции сгруппированы по отделам магазина в фиксированном порядке
 *    (мясо/рыба, молочное, крупы и бакалея, овощи и фрукты, прочее); пустые отделы опускаются.
 */
import type { IngredientCategory, PlanDay, ShoppingGroup, ShoppingItem } from "./schemas.ts";

export const CATEGORY_ORDER: readonly IngredientCategory[] = ["meat_fish", "dairy", "grains", "produce", "other"];

export interface IngredientLike {
  name: string;
  grams: number;
  category: string;
}

/** Ключ объединения: нижний регистр, схлопнутые пробелы, без завершающей пунктуации. */
export function normalizeName(name: string): string {
  return name
    .toLowerCase()
    .replace(/\s+/g, " ")
    .replace(/[\s.,;:!]+$/g, "")
    .trim();
}

/** Красивое отображаемое имя: первая буква заглавная, пробелы схлопнуты. */
export function displayName(name: string): string {
  const clean = name.replace(/\s+/g, " ").trim();
  if (!clean) return clean;
  return clean.charAt(0).toUpperCase() + clean.slice(1);
}

/** Граммы → { quantity, unit }: > 1000 г → кг с шагом 0,1; иначе целые граммы. */
export function toQuantity(grams: number): Pick<ShoppingItem, "quantity" | "unit"> {
  if (grams > 1000) {
    return { quantity: Math.round(grams / 100) / 10, unit: "kg" };
  }
  return { quantity: Math.round(grams), unit: "g" };
}

function safeGrams(value: unknown): number {
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) && n > 0 ? n : 0;
}

function safeCategory(value: string): IngredientCategory {
  return (CATEGORY_ORDER as readonly string[]).includes(value) ? (value as IngredientCategory) : "other";
}

/** Собирает список покупок из плоского списка ингредиентов. */
export function aggregateIngredients(ingredients: Iterable<IngredientLike>): ShoppingGroup[] {
  const byCategory = new Map<IngredientCategory, Map<string, { name: string; grams: number }>>();
  for (const ing of ingredients) {
    if (!ing || typeof ing.name !== "string") continue;
    const key = normalizeName(ing.name);
    const grams = safeGrams(ing.grams);
    if (!key || grams <= 0) continue;
    const category = safeCategory(ing.category);
    let bucket = byCategory.get(category);
    if (!bucket) {
      bucket = new Map();
      byCategory.set(category, bucket);
    }
    const existing = bucket.get(key);
    if (existing) {
      existing.grams += grams;
    } else {
      bucket.set(key, { name: displayName(ing.name), grams });
    }
  }

  const groups: ShoppingGroup[] = [];
  for (const category of CATEGORY_ORDER) {
    const bucket = byCategory.get(category);
    if (!bucket || bucket.size === 0) continue;
    const items: ShoppingItem[] = [...bucket.values()]
      .sort((a, b) => b.grams - a.grams || a.name.localeCompare(b.name))
      .map((entry) => ({ name: entry.name, ...toQuantity(entry.grams) }));
    groups.push({ category, items });
  }
  return groups;
}

/** Список покупок из дней плана (все приёмы всех дней). */
export function buildShoppingList(days: ReadonlyArray<Pick<PlanDay, "meals">>): ShoppingGroup[] {
  const all: IngredientLike[] = [];
  for (const day of days) {
    for (const meal of day.meals ?? []) {
      for (const ing of meal.ingredients ?? []) all.push(ing);
    }
  }
  return aggregateIngredients(all);
}

/** Количество позиций во всех группах — удобно для бейджа «N позиций». */
export function countItems(groups: ReadonlyArray<ShoppingGroup>): number {
  return groups.reduce((sum, g) => sum + g.items.length, 0);
}
