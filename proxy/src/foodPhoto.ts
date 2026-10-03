/**
 * POST /v1/food-photo — фото еды → распознанные блюда с граммами и КБЖУ (Claude vision).
 * Изображение приходит base64 (JPEG/PNG/WebP, ≤ 1,5 МБ после декодирования), ответ структурированный
 * (`client.messages.parse` + zod). Фото не сохраняется и не логируется.
 */
import Anthropic from "@anthropic-ai/sdk";
import { zodOutputFormat } from "@anthropic-ai/sdk/helpers/zod";
import { applyLimits, DEFAULT_MODEL, guardRequest, json, mapUpstreamError, normalizeLocale, parsePositiveInt, resolveDeviceId, t, usageOf } from "./common.ts";
import type { Env, Locale } from "./common.ts";
import { FoodAnalysisSchema } from "./schemas.ts";
import type { FoodAnalysis } from "./schemas.ts";

/** Тело запроса ≤ 2 МБ (base64 картинки + JSON). */
const MAX_BODY_BYTES = 2 * 1024 * 1024;
/** Размер изображения после декодирования base64. */
const MAX_IMAGE_BYTES = Math.floor(1.5 * 1024 * 1024);
const MAX_HINT_CHARS = 200;
const MEDIA_TYPES = ["image/jpeg", "image/png", "image/webp"] as const;
type MediaType = (typeof MEDIA_TYPES)[number];
const BASE64_RE = /^[A-Za-z0-9+/]+={0,2}$/;

const SUBJECT = { ru: "AI-повар", kk: "AI-аспаз" };
const GLOBAL_LIMIT_TEXT = { ru: "AI-повар сейчас отдыхает. Попробуй позже.", kk: "AI-аспаз қазір демалып жатыр. Кейінірек көріңіз." };
/** 502 bad_model_output: ответ модели неполный или не прошёл схему. */
const BAD_OUTPUT_TEXT = { ru: "Не удалось разобрать фото. Попробуй ещё раз.", kk: "Суретті талдау мүмкін болмады. Қайта көріңіз." };

export interface FoodPhotoRequest {
  locale: Locale;
  imageBase64: string;
  mediaType: MediaType;
  hint?: string;
  deviceId?: string;
}

/** Длина данных после декодирования base64 (без фактического декодирования). */
export function decodedBase64Length(b64: string): number {
  const padding = b64.endsWith("==") ? 2 : b64.endsWith("=") ? 1 : 0;
  return Math.floor((b64.length * 3) / 4) - padding;
}

/** Валидация тела: коды ошибок → 400, кроме image_too_large → 413. */
export function sanitizeFoodPhotoRequest(raw: unknown): FoodPhotoRequest | string {
  if (!raw || typeof raw !== "object") return "bad_body";
  const r = raw as Record<string, unknown>;
  if (typeof r.imageBase64 !== "string" || r.imageBase64.length === 0) return "image_required";
  // Допускаем data-URI префикс, переводы строк и отсутствие паддинга (Base64.NO_PADDING на Android) —
  // паддинг достраиваем сами; остаток 1 символ в base64 невозможен.
  let data = r.imageBase64.replace(/^data:[^;]+;base64,/, "").replace(/\s+/g, "");
  if (data.length % 4 === 1) return "image_not_base64";
  if (data.length % 4 !== 0 && !data.endsWith("=")) data += "=".repeat(4 - (data.length % 4));
  if (!BASE64_RE.test(data) || data.length % 4 !== 0) return "image_not_base64";
  if (decodedBase64Length(data) > MAX_IMAGE_BYTES) return "image_too_large";
  if (!(MEDIA_TYPES as readonly unknown[]).includes(r.mediaType)) return "unsupported_image_type";
  const hint = typeof r.hint === "string" ? r.hint.replace(/\s+/g, " ").trim().slice(0, MAX_HINT_CHARS) : "";
  return {
    locale: normalizeLocale(r.locale),
    imageBase64: data,
    mediaType: r.mediaType as MediaType,
    hint: hint || undefined,
    deviceId: typeof r.deviceId === "string" ? r.deviceId : undefined,
  };
}

export function buildPhotoSystemPrompt(locale: Locale): string {
  const lang = locale === "kk" ? "казахском (настоящий литературный казахский)" : "русском";
  return [
    "Ты — диетолог фитнес-клуба в Казахстане. По фотографии еды оцениваешь блюда, их массу и КБЖУ.",
    "Оценивай порции по визуальным ориентирам: стандартная тарелка ~26 см, пиала ~12 см, столовая ложка ~15 г, чайная ~5 г, ладонь/кулак, стакан 250 мл. Давай реалистичные граммы готового блюда — не занижай: обычная порция гарнира 150–250 г, мяса 120–200 г, супа 300–400 г.",
    "Знай казахскую и среднеазиатскую кухню: бешбармак (мясо + тесто жайма + сорпа), лагман, плов, манты, самса, куырдак, сорпа, баурсак (один ≈ 25–30 г, ~110 ккал), курт (один ≈ 10–15 г), иримшик, казы, шелпек, айран, кумыс, шубат, лепёшка/тандырный хлеб. Учитывай скрытые жиры и масло.",
    "Каждое отдельное блюдо/компонент — своя позиция items (например, «бешбармак (мясо и тесто)», «сорпа», «лепёшка»). Напитки тоже учитывай, воду — нет. Для каждой позиции — граммы, ккал, белки, углеводы, жиры (на указанную массу) и confidence: high — блюдо и порция очевидны; medium — есть сомнения в составе или массе; low — плохо видно.",
    "Если на фото не еда (или еды не видно) — isFood=false, items пустой, totals = 0, в note объясни, что распознать не удалось и что сфотографировать.",
    "Если есть подсказка пользователя — доверяй ей в части названия блюда и размера порции, но КБЖУ считай сам.",
    "note — 1–2 предложения: что видно, что уточнить (например, «если бешбармак с казы — добавь ~150 ккал»). Без медицинских выводов.",
    `Весь текст (name, note) — на ${lang}. Значения confidence — строго латиницей как в схеме.`,
  ].join("\n");
}

export function buildPhotoUserText(req: FoodPhotoRequest): string {
  const base = req.locale === "kk" ? "Суреттегі тағамды анықта, грамын және КБЖУ-ды бағала." : "Распознай еду на фото, оцени граммы и КБЖУ.";
  return req.hint ? `${base}\n${req.locale === "kk" ? "Пайдаланушы кеңесі" : "Подсказка пользователя"}: ${req.hint}` : base;
}

const round1 = (n: number) => Math.round(n * 10) / 10;
const nonNeg = (n: unknown) => (typeof n === "number" && Number.isFinite(n) && n >= 0 ? n : 0);

/** Нормализация: не еда → пустые позиции; отрицательные/NaN → 0; итоги пересчитываются из позиций. */
export function finalizeAnalysis(output: FoodAnalysis): FoodAnalysis {
  const items = output.isFood
    ? output.items
        .filter((i) => typeof i.name === "string" && i.name.trim())
        .map((i) => ({
          name: i.name.trim(),
          grams: Math.round(nonNeg(i.grams)),
          calories: Math.round(nonNeg(i.calories)),
          proteinG: round1(nonNeg(i.proteinG)),
          carbsG: round1(nonNeg(i.carbsG)),
          fatG: round1(nonNeg(i.fatG)),
          confidence: i.confidence,
        }))
    : [];
  return {
    items,
    totalCalories: items.reduce((s, i) => s + i.calories, 0),
    totalProteinG: round1(items.reduce((s, i) => s + i.proteinG, 0)),
    totalCarbsG: round1(items.reduce((s, i) => s + i.carbsG, 0)),
    totalFatG: round1(items.reduce((s, i) => s + i.fatG, 0)),
    note: output.note.trim(),
    isFood: output.isFood && items.length > 0,
  };
}

export async function handleFoodPhoto(request: Request, env: Env): Promise<Response> {
  const guard = guardRequest(request, env, MAX_BODY_BYTES);
  if (guard) return guard;
  const kv = env.RATE_LIMIT_KV!;

  let parsed: FoodPhotoRequest | string;
  try {
    parsed = sanitizeFoodPhotoRequest(await request.json());
  } catch {
    parsed = "bad_json";
  }
  if (parsed === "image_too_large") return json({ error: parsed }, 413);
  if (typeof parsed === "string") return json({ error: parsed }, 400);
  const body = parsed;
  const locale = body.locale;

  const deviceId = resolveDeviceId(request, body.deviceId);
  if (!deviceId) return json({ error: "bad_device_id" }, 400);

  const limited = await applyLimits(request, env, kv, deviceId, locale, {
    prefix: "rlf",
    perDevice: parsePositiveInt(env.DAILY_LIMIT_PHOTOS_PER_DEVICE, 20),
    deviceText: {
      ru: "Лимит разборов фото на сегодня исчерпан. Продолжим завтра 📷",
      kk: "Бүгінгі фото талдау лимиті аяқталды. Ертең жалғастырамыз 📷",
    },
    globalText: GLOBAL_LIMIT_TEXT,
  });
  if (limited) return limited;

  const client = new Anthropic({ apiKey: env.ANTHROPIC_API_KEY, maxRetries: 1, timeout: 90_000 });
  const model = env.MODEL ?? DEFAULT_MODEL;

  try {
    const response = await client.messages.parse({
      model,
      max_tokens: 4096,
      system: buildPhotoSystemPrompt(locale),
      messages: [
        {
          role: "user",
          content: [
            { type: "image", source: { type: "base64", media_type: body.mediaType, data: body.imageBase64 } },
            { type: "text", text: buildPhotoUserText(body) },
          ],
        },
      ],
      output_config: { effort: "low", format: zodOutputFormat(FoodAnalysisSchema) },
    });

    if (response.stop_reason === "refusal") {
      return json(
        {
          error: "refused",
          text: t(locale, "AI-повар не может разобрать это фото. Сфотографируй только еду на тарелке.", "AI-аспаз бұл суретті талдай алмайды. Тек тәрелкедегі тағамды түсіріңіз."),
        },
        422,
      );
    }
    if (response.stop_reason === "max_tokens" || !response.parsed_output) {
      return json({ error: "bad_model_output", text: t(locale, BAD_OUTPUT_TEXT.ru, BAD_OUTPUT_TEXT.kk) }, 502);
    }

    return json({ analysis: finalizeAnalysis(response.parsed_output), model: response.model, usage: usageOf(response) });
  } catch (error) {
    // Обрезанный/невалидный JSON SDK бросает как AnthropicError ещё в parse → тоже bad_model_output.
    return mapUpstreamError(error, locale, SUBJECT, BAD_OUTPUT_TEXT);
  }
}
