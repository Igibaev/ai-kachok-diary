/**
 * Unit-тесты серверной агрегации списка покупок (node:test, без сети и без Anthropic).
 * Запуск: npm test (Node 22.18+ выполняет .ts напрямую через type stripping).
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import { aggregateIngredients, buildShoppingList, countItems, normalizeName, toQuantity } from "../src/shopping.ts";

test("toQuantity: до 1000 г — граммы целыми, свыше — кг с шагом 0,1", () => {
  assert.deepEqual(toQuantity(150), { quantity: 150, unit: "g" });
  assert.deepEqual(toQuantity(149.6), { quantity: 150, unit: "g" });
  assert.deepEqual(toQuantity(1000), { quantity: 1000, unit: "g" });
  assert.deepEqual(toQuantity(1001), { quantity: 1, unit: "kg" });
  assert.deepEqual(toQuantity(1250), { quantity: 1.3, unit: "kg" });
  assert.deepEqual(toQuantity(2340), { quantity: 2.3, unit: "kg" });
});

test("normalizeName: регистр, пробелы и хвостовая пунктуация не различают позиции", () => {
  assert.equal(normalizeName("Куриная  грудка "), "куриная грудка");
  assert.equal(normalizeName("куриная грудка."), "куриная грудка");
  assert.equal(normalizeName("ГРЕЧКА"), "гречка");
});

test("дубликаты объединяются без учёта регистра, граммы суммируются", () => {
  const groups = aggregateIngredients([
    { name: "Куриная грудка", grams: 150, category: "meat_fish" },
    { name: "куриная грудка", grams: 200, category: "meat_fish" },
    { name: "КУРИНАЯ  ГРУДКА", grams: 700, category: "meat_fish" },
  ]);
  assert.equal(groups.length, 1);
  assert.equal(groups[0].category, "meat_fish");
  assert.deepEqual(groups[0].items, [{ name: "Куриная грудка", quantity: 1.1, unit: "kg" }]);
});

test("группировка по отделам в фиксированном порядке, пустые отделы опускаются", () => {
  const groups = aggregateIngredients([
    { name: "яблоко", grams: 180, category: "produce" },
    { name: "рис", grams: 80, category: "grains" },
    { name: "айран", grams: 250, category: "dairy" },
    { name: "соль", grams: 3, category: "other" },
  ]);
  assert.deepEqual(
    groups.map((g) => g.category),
    ["dairy", "grains", "produce", "other"],
  );
  assert.deepEqual(groups[2].items, [{ name: "Яблоко", quantity: 180, unit: "g" }]);
});

test("внутри отдела позиции отсортированы по убыванию массы", () => {
  const [group] = aggregateIngredients([
    { name: "лук", grams: 60, category: "produce" },
    { name: "картофель", grams: 400, category: "produce" },
    { name: "морковь", grams: 120, category: "produce" },
  ]);
  assert.deepEqual(
    group.items.map((i) => i.name),
    ["Картофель", "Морковь", "Лук"],
  );
});

test("некорректные записи (нет имени, 0 или NaN граммов) пропускаются; неизвестная категория → other", () => {
  const groups = aggregateIngredients([
    { name: "", grams: 100, category: "produce" },
    { name: "вода", grams: 0, category: "other" },
    { name: "масло", grams: Number.NaN, category: "other" },
    { name: "мёд", grams: 20, category: "sweets" as unknown as string },
  ]);
  assert.deepEqual(groups, [{ category: "other", items: [{ name: "Мёд", quantity: 20, unit: "g" }] }]);
});

test("buildShoppingList: ингредиенты суммируются по всем дням и приёмам", () => {
  const days = [
    {
      meals: [
        { ingredients: [{ name: "Гречка", grams: 80, category: "grains" as const }, { name: "Говядина", grams: 150, category: "meat_fish" as const }] },
        { ingredients: [{ name: "гречка", grams: 80, category: "grains" as const }] },
      ],
    },
    {
      meals: [
        { ingredients: [{ name: "Гречка", grams: 90, category: "grains" as const }, { name: "Говядина", grams: 900, category: "meat_fish" as const }] },
      ],
    },
  ];
  const groups = buildShoppingList(days as never);
  assert.deepEqual(groups, [
    { category: "meat_fish", items: [{ name: "Говядина", quantity: 1.1, unit: "kg" }] },
    { category: "grains", items: [{ name: "Гречка", quantity: 250, unit: "g" }] },
  ]);
  assert.equal(countItems(groups), 2);
});

test("пустой план → пустой список", () => {
  assert.deepEqual(buildShoppingList([]), []);
  assert.deepEqual(buildShoppingList([{ meals: [] }] as never), []);
});
