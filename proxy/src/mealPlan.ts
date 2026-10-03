/**
 * POST /v1/meal-plan — план питания на N дней по целям профиля (AI-повар).
 * Структурированный ответ: `client.messages.parse` + `output_config.format = zodOutputFormat(...)`.
 * Список покупок собирается на сервере из ингредиентов (shopping.ts), суточные итоги пересчитываются.
 */
import Anthropic from "@anthropic-ai/sdk";
import { zodOutputFormat } from "@anthropic-ai/sdk/helpers/zod";
import { applyLimits, DEFAULT_MODEL, guardRequest, json, mapUpstreamError, normalizeLocale, parsePositiveInt, resolveDeviceId, t, usageOf } from "./common.ts";
import type { Env, Locale } from "./common.ts";
import { MealPlanModelSchema } from "./schemas.ts";
import type { MealPlan, MealPlanModelOutput } from "./schemas.ts";
import { buildShoppingList } from "./shopping.ts";

/** Тело запроса небольшое: цели, профиль, предпочтения. */
const MAX_BODY_BYTES = 32 * 1024;
const MAX_LIST_ITEMS = 20;
const MAX_ITEM_CHARS = 60;
const DAYS = [3, 5, 7] as const;
const MEALS_PER_DAY = [3, 4, 5] as const;
const CUISINES = ["kazakh", "home", "any"] as const;
const BUDGETS = ["low", "mid", "any"] as const;
const SEX = ["male", "female"] as const;

export interface MealPlanRequest {
  locale: Locale;
  days: (typeof DAYS)[number];
  mealsPerDay: (typeof MEALS_PER_DAY)[number];
  goals: { calories: number; proteinG: number; carbsG: number; fatG: number };
  profile: { sex?: "male" | "female"; age?: number; weightKg?: number; goal?: string; restrictions: string[] };
  prefs: { cuisine: (typeof CUISINES)[number]; exclusions: string[]; halal: boolean; budget: (typeof BUDGETS)[number]; batchCooking: boolean };
  deviceId?: string;
}

const cleanList = (value: unknown): string[] =>
  Array.isArray(value)
    ? value
        .filter((v): v is string => typeof v === "string" && v.trim().length > 0)
        .map((v) => v.replace(/\s+/g, " ").trim().slice(0, MAX_ITEM_CHARS))
        .slice(0, MAX_LIST_ITEMS)
    : [];

const num = (value: unknown, min: number, max: number): number | undefined => {
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) && n >= min && n <= max ? n : undefined;
};

const oneOf = <T extends readonly unknown[]>(value: unknown, allowed: T, fallback: T[number]): T[number] =>
  (allowed as readonly unknown[]).includes(value) ? (value as T[number]) : fallback;

/** Валидация и нормализация тела. Возвращает код ошибки (string) или нормализованный запрос. */
export function sanitizeMealPlanRequest(raw: unknown): MealPlanRequest | string {
  if (!raw || typeof raw !== "object") return "bad_body";
  const r = raw as Record<string, unknown>;
  const goals = (r.goals ?? {}) as Record<string, unknown>;
  const calories = num(goals.calories, 800, 6000);
  const proteinG = num(goals.proteinG, 20, 500);
  const carbsG = num(goals.carbsG, 20, 900);
  const fatG = num(goals.fatG, 15, 300);
  if (calories === undefined || proteinG === undefined || carbsG === undefined || fatG === undefined) return "goals_required";
  const profile = (r.profile ?? {}) as Record<string, unknown>;
  const prefs = (r.prefs ?? {}) as Record<string, unknown>;
  const deviceId = typeof r.deviceId === "string" ? r.deviceId : undefined;
  return {
    locale: normalizeLocale(r.locale),
    days: oneOf(r.days, DAYS, 3),
    mealsPerDay: oneOf(r.mealsPerDay, MEALS_PER_DAY, 3),
    goals: { calories: Math.round(calories), proteinG: Math.round(proteinG), carbsG: Math.round(carbsG), fatG: Math.round(fatG) },
    profile: {
      sex: (SEX as readonly unknown[]).includes(profile.sex) ? (profile.sex as "male" | "female") : undefined,
      age: num(profile.age, 10, 100),
      weightKg: num(profile.weightKg, 30, 300),
      goal: typeof profile.goal === "string" ? profile.goal.trim().slice(0, MAX_ITEM_CHARS) : undefined,
      restrictions: cleanList(profile.restrictions),
    },
    prefs: {
      cuisine: oneOf(prefs.cuisine, CUISINES, "any"),
      exclusions: cleanList(prefs.exclusions),
      halal: prefs.halal === true,
      budget: oneOf(prefs.budget, BUDGETS, "any"),
      batchCooking: prefs.batchCooking === true,
    },
    deviceId,
  };
}

export function buildSystemPrompt(req: MealPlanRequest): string {
  const lang = req.locale === "kk" ? "казахском (қазақ тілі — настоящий литературный казахский, не транслит и не калька с русского)" : "русском";
  const cuisine = {
    kazakh:
      "Кухня: казахская. Используй казахские блюда умеренно и здорово: бешбармак — умеренно (порция, больше мяса и овощей, меньше теста), куырдак, сорпа, казы — редко (жирное), баурсаки — ограниченно (1–2 шт. как лакомство), лагман, манты и плов — с контролем масла и теста; курт, иримшик, айран, кумыс/шубат — как перекусы и источники белка.",
    home: "Кухня: домашняя привычная (супы, каши, тушёное мясо и рыба, салаты, запеканки) с лёгким казахским акцентом (айран, курт, иримшик как перекусы).",
    any: "Кухня: любая, но из продуктов, доступных в Казахстане; допускается лёгкий казахский акцент.",
  }[req.prefs.cuisine];
  const budget = {
    low: "Бюджет: эконом. Базовые продукты: курица, яйца, гречка, рис, овсянка, сезонные овощи, бобовые, творог, айран; без дорогой рыбы, орехов и экзотики.",
    mid: "Бюджет: средний. Обычные продукты супермаркета, рыба 1–2 раза за план, орехи/сыры умеренно.",
    any: "Бюджет: без ограничений, но без избыточных деликатесов — приоритет пользы и вкуса.",
  }[req.prefs.budget];
  return [
    "Ты — диетолог фитнес-клуба в Казахстане. Составляешь практичные планы питания для клиентов клуба.",
    "Продукты — только те, что реально купить в Казахстане (Magnum, Small, базар): говядина, баранина, конина, курица, рыба (судак, карп, минтай, горбуша), яйца, молочное (айран, курт, иримшик, творог, кефир, сыр), крупы (гречка, рис, булгур, овсянка, пшено), макароны, бобовые, сезонные овощи и фрукты, зелень, растительное масло, хлеб/лепёшка.",
    cuisine,
    req.prefs.halal ? "Халяль: обязательно. Никакой свинины и продуктов из неё, никакого алкоголя (в т. ч. в соусах), желатин только халяльный." : "Халяль не требуется, но без алкоголя.",
    budget,
    `Суточные КБЖУ каждого дня должны попадать в ±5 % от цели: ${req.goals.calories} ккал, белки ${req.goals.proteinG} г, углеводы ${req.goals.carbsG} г, жиры ${req.goals.fatG} г. Проверь, что сумма приёмов дня даёт итог дня.`,
    `Приёмов пищи в день: ${req.mealsPerDay}. Слоты: 3 → breakfast, lunch, dinner; 4 → + snack; 5 → + второй snack. Порядок: breakfast, snack?, lunch, snack?, dinner.`,
    req.prefs.batchCooking
      ? "Режим «готовлю заранее на 2 дня»: обед и ужин дня N повторяются в день N+1 (те же блюда, ингредиенты указывай в каждом дне заново — это съедаемые порции). В шагах первого дня пометь «приготовить двойную порцию»."
      : "Разнообразие: не повторяй одно и то же основное блюдо чаще чем через 2 дня.",
    "Рецепты простые, ≤ 30 минут активной готовки (кроме сорпы/бешбармака — до 60, но не чаще 1 раза за план). 2–4 коротких шага. Ингредиенты — с граммами сырого продукта и категорией отдела магазина: meat_fish (мясо/рыба/яйца), dairy (молочное), grains (крупы, макароны, хлеб, бакалея, масло, специи), produce (овощи, фрукты, зелень), other (всё остальное).",
    "Названия ингредиентов пиши одинаково во всём плане (например, всегда «куриная грудка», а не «грудка курицы») — из них собирается список покупок. Не включай воду.",
    "Строго соблюдай исключения и ограничения клиента: исключённые продукты не должны появляться ни в одном блюде и ни в одном ингредиенте.",
    `Весь текст (названия блюд, ингредиенты, шаги, заметки) — на ${lang}. Ключи и значения-перечисления (slot, category) — строго латиницей как в схеме.`,
    "В notes — 2–4 коротких предложения: как пить воду, что можно заменить, совет по готовке. Без медицинских диагнозов; при хронических болезнях — рекомендуй обсудить с врачом.",
  ].join("\n");
}

export function buildUserPrompt(req: MealPlanRequest): string {
  const p = req.profile;
  const profileParts = [
    p.sex ? `пол: ${p.sex === "male" ? "мужской" : "женский"}` : null,
    p.age ? `возраст: ${p.age}` : null,
    p.weightKg ? `вес: ${p.weightKg} кг` : null,
    p.goal ? `цель: ${p.goal}` : null,
    p.restrictions.length ? `ограничения по здоровью: ${p.restrictions.join(", ")}` : null,
  ].filter(Boolean);
  const exclusions = req.prefs.exclusions.length ? req.prefs.exclusions.join(", ") : "нет";
  return [
    `Составь план питания на ${req.days} дней (day = 1..${req.days}), ${req.mealsPerDay} приёма(ов) пищи в день.`,
    `Клиент: ${profileParts.length ? profileParts.join("; ") : "данные не указаны"}.`,
    `Не ем / аллергии (исключить полностью): ${exclusions}.`,
    `Цель дня: ${req.goals.calories} ккал, Б ${req.goals.proteinG} г, У ${req.goals.carbsG} г, Ж ${req.goals.fatG} г.`,
    `Язык ответа: ${req.locale === "kk" ? "казахский" : "русский"}.`,
  ].join("\n");
}

const round1 = (n: number) => Math.round(n * 10) / 10;
const nonNeg = (n: unknown) => (typeof n === "number" && Number.isFinite(n) && n >= 0 ? n : 0);

/** Проверка и нормализация ответа модели: отрицательные/NaN числа → 0, итоги дня пересчитываются из приёмов. */
export function finalizePlan(output: MealPlanModelOutput): MealPlan {
  const days = output.days
    .filter((d) => Array.isArray(d.meals) && d.meals.length > 0)
    .map((d, index) => {
      const meals = d.meals.map((m) => ({
        slot: m.slot,
        title: m.title.trim(),
        timeMinutes: Math.round(nonNeg(m.timeMinutes)),
        ingredients: m.ingredients
          .filter((i) => typeof i.name === "string" && i.name.trim() && nonNeg(i.grams) > 0)
          .map((i) => ({ name: i.name.trim(), grams: Math.round(nonNeg(i.grams)), category: i.category })),
        calories: Math.round(nonNeg(m.calories)),
        proteinG: round1(nonNeg(m.proteinG)),
        carbsG: round1(nonNeg(m.carbsG)),
        fatG: round1(nonNeg(m.fatG)),
        steps: m.steps.map((s) => s.trim()).filter(Boolean),
      }));
      return {
        day: index + 1,
        meals,
        totalCalories: meals.reduce((s, m) => s + m.calories, 0),
        totalProteinG: round1(meals.reduce((s, m) => s + m.proteinG, 0)),
        totalCarbsG: round1(meals.reduce((s, m) => s + m.carbsG, 0)),
        totalFatG: round1(meals.reduce((s, m) => s + m.fatG, 0)),
      };
    });
  return { days, shopping: buildShoppingList(days), notes: output.notes.trim() };
}

export async function handleMealPlan(request: Request, env: Env): Promise<Response> {
  const guard = guardRequest(request, env, MAX_BODY_BYTES);
  if (guard) return guard;
  const kv = env.RATE_LIMIT_KV!;

  let parsed: MealPlanRequest | string;
  try {
    parsed = sanitizeMealPlanRequest(await request.json());
  } catch {
    parsed = "bad_json";
  }
  if (typeof parsed === "string") return json({ error: parsed }, 400);
  const body = parsed;
  const locale = body.locale;

  const deviceId = resolveDeviceId(request, body.deviceId);
  if (!deviceId) return json({ error: "bad_device_id" }, 400);

  const limited = await applyLimits(request, env, kv, deviceId, locale, {
    prefix: "rlp",
    perDevice: parsePositiveInt(env.DAILY_LIMIT_PLANS_PER_DEVICE, 3),
    deviceText: {
      ru: "Лимит планов питания на сегодня исчерпан. Новый план можно составить завтра 🍽",
      kk: "Бүгінгі тамақтану жоспары лимиті аяқталды. Жаңа жоспарды ертең құруға болады 🍽",
    },
  });
  if (limited) return limited;

  // Длинный структурированный ответ (7 дней × 5 приёмов) — таймаут SDK 180 с, без повторов (дорого).
  const client = new Anthropic({ apiKey: env.ANTHROPIC_API_KEY, maxRetries: 0, timeout: 180_000 });
  const model = env.MODEL ?? DEFAULT_MODEL;
  const subject = { ru: "AI-повар", kk: "AI-аспаз" };

  try {
    const response = await client.messages.parse({
      model,
      max_tokens: 16000,
      system: buildSystemPrompt(body),
      messages: [{ role: "user", content: buildUserPrompt(body) }],
      output_config: { effort: "low", format: zodOutputFormat(MealPlanModelSchema) },
    });

    if (response.stop_reason === "refusal") {
      return json(
        {
          error: "refused",
          text: t(locale, "AI-повар не смог составить план по этому запросу. Измени предпочтения и попробуй снова.", "AI-аспаз бұл сұраныс бойынша жоспар құра алмады. Таңдауларды өзгертіп, қайта көріңіз."),
        },
        422,
      );
    }
    if (response.stop_reason === "max_tokens" || !response.parsed_output) {
      return json(
        {
          error: "bad_model_output",
          text: t(locale, "План получился неполным. Попробуй меньше дней или приёмов пищи.", "Жоспар толық шықпады. Күн немесе тамақтану санын азайтып көріңіз."),
        },
        502,
      );
    }

    const plan = finalizePlan(response.parsed_output);
    if (plan.days.length === 0) {
      return json({ error: "bad_model_output", text: t(locale, "AI-повар вернул пустой план. Попробуй ещё раз.", "AI-аспаз бос жоспар қайтарды. Қайта көріңіз.") }, 502);
    }
    return json({ plan, model: response.model, usage: usageOf(response) });
  } catch (error) {
    return mapUpstreamError(error, locale, subject);
  }
}
