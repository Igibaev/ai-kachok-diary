/**
 * Тесты валидации запросов и нормализации ответов AI-повара (без сети: Anthropic не вызывается).
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import { zodOutputFormat } from "@anthropic-ai/sdk/helpers/zod";
import { finalizePlan, sanitizeMealPlanRequest } from "../src/mealPlan.ts";
import { decodedBase64Length, finalizeAnalysis, sanitizeFoodPhotoRequest } from "../src/foodPhoto.ts";
import { FoodAnalysisSchema, MealPlanModelSchema } from "../src/schemas.ts";
import { FEATURES } from "../src/index.ts";
import Anthropic from "@anthropic-ai/sdk";
import { mapUpstreamError, resolveDeviceId } from "../src/common.ts";

const goals = { calories: 1850, proteinG: 140, carbsG: 190, fatG: 60 };

test("health features", () => {
  assert.deepEqual([...FEATURES], ["chat", "meal-plan", "food-photo"]);
});

test("meal-plan: невалидные значения заменяются значениями по умолчанию, цели обязательны", () => {
  assert.equal(sanitizeMealPlanRequest(null), "bad_body");
  assert.equal(sanitizeMealPlanRequest({ goals: { calories: "x" } }), "goals_required");
  const req = sanitizeMealPlanRequest({
    locale: "kk",
    days: 4,
    mealsPerDay: 9,
    goals,
    profile: { sex: "male", age: 31, weightKg: 82, restrictions: ["спина", "", 42] },
    prefs: { cuisine: "kazakh", exclusions: ["  свинина ", "орехи"], halal: "yes", budget: "low", batchCooking: true },
    deviceId: "abc-123",
  });
  assert.ok(typeof req !== "string");
  assert.equal(req.locale, "kk");
  assert.equal(req.days, 3);
  assert.equal(req.mealsPerDay, 3);
  assert.deepEqual(req.profile.restrictions, ["спина"]);
  assert.deepEqual(req.prefs.exclusions, ["свинина", "орехи"]);
  assert.equal(req.prefs.halal, false);
  assert.equal(req.prefs.batchCooking, true);
  assert.equal(req.deviceId, "abc-123");
});

test("finalizePlan: итоги дня пересчитываются, список покупок собирается из ингредиентов", () => {
  const plan = finalizePlan({
    notes: "  Пей воду.  ",
    days: [
      {
        day: 7,
        totalCalories: 1,
        totalProteinG: 1,
        totalCarbsG: 1,
        totalFatG: 1,
        meals: [
          {
            slot: "breakfast",
            title: " Овсянка ",
            timeMinutes: 10,
            ingredients: [
              { name: "овсянка", grams: 60, category: "grains" },
              { name: "молоко", grams: 200, category: "dairy" },
              { name: "вода", grams: 0, category: "other" },
            ],
            calories: 300,
            proteinG: 12.26,
            carbsG: 45,
            fatG: 8,
            steps: ["Свари", ""],
          },
          {
            slot: "dinner",
            title: "Куырдак",
            timeMinutes: 30,
            ingredients: [
              { name: "Говядина", grams: 150, category: "meat_fish" },
              { name: "картофель", grams: 200, category: "produce" },
            ],
            calories: 550,
            proteinG: 40,
            carbsG: 35,
            fatG: -3,
            steps: ["Обжарь", "Потуши"],
          },
        ],
      },
      { day: 2, meals: [], totalCalories: 0, totalProteinG: 0, totalCarbsG: 0, totalFatG: 0 },
    ],
  });
  assert.equal(plan.days.length, 1);
  assert.equal(plan.days[0].day, 1);
  assert.equal(plan.days[0].totalCalories, 850);
  assert.equal(plan.days[0].totalProteinG, 52.3);
  assert.equal(plan.days[0].meals[1].fatG, 0);
  assert.equal(plan.days[0].meals[0].title, "Овсянка");
  assert.deepEqual(plan.days[0].meals[0].steps, ["Свари"]);
  assert.equal(plan.days[0].meals[0].ingredients.length, 2);
  assert.equal(plan.notes, "Пей воду.");
  assert.deepEqual(
    plan.shopping.map((g) => g.category),
    ["meat_fish", "dairy", "grains", "produce"],
  );
  assert.deepEqual(plan.shopping[0].items, [{ name: "Говядина", quantity: 150, unit: "g" }]);
});

test("food-photo: валидация base64, типа и размера", () => {
  const ok = sanitizeFoodPhotoRequest({ locale: "ru", imageBase64: "data:image/jpeg;base64,/9j/4AAQ\nSkZJRg==", mediaType: "image/jpeg", hint: "  бешбармак,  порция средняя " });
  assert.ok(typeof ok !== "string");
  assert.equal(ok.imageBase64, "/9j/4AAQSkZJRg==");
  assert.equal(ok.hint, "бешбармак, порция средняя");
  assert.equal(sanitizeFoodPhotoRequest({ imageBase64: "", mediaType: "image/jpeg" }), "image_required");
  assert.equal(sanitizeFoodPhotoRequest({ imageBase64: "not base64!!", mediaType: "image/jpeg" }), "image_not_base64");
  assert.equal(sanitizeFoodPhotoRequest({ imageBase64: "AAAA", mediaType: "image/gif" }), "unsupported_image_type");
  const huge = "A".repeat(Math.ceil((1.5 * 1024 * 1024 + 4) / 3) * 4);
  assert.equal(sanitizeFoodPhotoRequest({ imageBase64: huge, mediaType: "image/png" }), "image_too_large");
});

test("decodedBase64Length учитывает padding", () => {
  assert.equal(decodedBase64Length("QUJD"), 3); // "ABC"
  assert.equal(decodedBase64Length("QUI="), 2); // "AB"
  assert.equal(decodedBase64Length("QQ=="), 1); // "A"
});

test("finalizeAnalysis: не еда → пустые позиции и нули; итоги пересчитываются", () => {
  const notFood = finalizeAnalysis({
    isFood: false,
    items: [{ name: "стол", grams: 1000, calories: 1, proteinG: 1, carbsG: 1, fatG: 1, confidence: "low" }],
    totalCalories: 1,
    totalProteinG: 1,
    totalCarbsG: 1,
    totalFatG: 1,
    note: "На фото нет еды.",
  });
  assert.equal(notFood.isFood, false);
  assert.deepEqual(notFood.items, []);
  assert.equal(notFood.totalCalories, 0);

  const food = finalizeAnalysis({
    isFood: true,
    items: [
      { name: "Куриная грудка", grams: 150, calories: 248, proteinG: 46.5, carbsG: 0, fatG: 5.4, confidence: "high" },
      { name: "Гречка", grams: 180, calories: 198, proteinG: 7.2, carbsG: 38, fatG: 1.8, confidence: "medium" },
    ],
    totalCalories: 0,
    totalProteinG: 0,
    totalCarbsG: 0,
    totalFatG: 0,
    note: "Порция средняя.",
  });
  assert.equal(food.isFood, true);
  assert.equal(food.totalCalories, 446);
  assert.equal(food.totalProteinG, 53.7);
  assert.equal(food.totalFatG, 7.2);
});

test("zodOutputFormat принимает схемы (JSON Schema без неподдерживаемых ограничений)", () => {
  const plan = zodOutputFormat(MealPlanModelSchema);
  const photo = zodOutputFormat(FoodAnalysisSchema);
  assert.equal(plan.type, "json_schema");
  assert.equal(photo.type, "json_schema");
  const planSchema = JSON.stringify(plan.schema);
  assert.ok(planSchema.includes('meat_fish'));
  assert.ok(!planSchema.includes('"minimum"'));
  assert.ok(JSON.stringify(photo.schema).includes('"isFood"'));
});

test("mapUpstreamError: ошибка разбора структурированного ответа (parse) → 502 bad_model_output, не 500", async () => {
  const res = mapUpstreamError(new Anthropic.AnthropicError("Failed to parse structured output"), "ru", { ru: "AI-повар", kk: "AI-аспаз" }, { ru: "План неполный.", kk: "Жоспар толық емес." });
  assert.equal(res.status, 502);
  assert.deepEqual(await res.json(), { error: "bad_model_output", text: "План неполный." });
  const generic = mapUpstreamError(new Error("boom"), "kk", { ru: "AI-повар", kk: "AI-аспаз" });
  assert.equal(generic.status, 500);
  assert.equal(((await generic.json()) as { error: string }).error, "internal");
});

test("resolveDeviceId: заголовок приоритетнее тела, некорректный deviceId тела → anonymous", () => {
  const withHeader = new Request("https://x/v1/chat", { headers: { "x-device-id": "dev-1" } });
  assert.equal(resolveDeviceId(withHeader, "bad id!"), "dev-1");
  const noHeader = new Request("https://x/v1/chat");
  assert.equal(resolveDeviceId(noHeader, "bad id!"), "anonymous");
  assert.equal(resolveDeviceId(noHeader, "abc-123"), "abc-123");
  assert.equal(resolveDeviceId(noHeader, undefined), "anonymous");
});

test("food-photo: base64 без паддинга принимается (паддинг достраивается), остаток 1 — ошибка", () => {
  const ok = sanitizeFoodPhotoRequest({ imageBase64: "QUI", mediaType: "image/jpeg" });
  assert.ok(typeof ok !== "string");
  assert.equal(ok.imageBase64, "QUI=");
  assert.equal(sanitizeFoodPhotoRequest({ imageBase64: "QUIAB", mediaType: "image/jpeg" }), "image_not_base64");
});
