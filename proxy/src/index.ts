/**
 * FitCoach AI — AI-прокси для white-label приложения.
 *
 * Зачем: ключ Anthropic хранится у фитнес-клуба (в секретах Cloudflare), а не в приложении.
 * Приложение шлёт запрос с токеном приложения, прокси проверяет токен, лимиты на устройство
 * и общий дневной лимит, вызывает Claude и возвращает результат.
 *
 * Эндпоинты:
 *   GET  /health          — состояние, модель, список фич
 *   POST /v1/chat         — AI-тренер (текстовый диалог)
 *   POST /v1/meal-plan    — AI-повар: план питания на 3/5/7 дней + список покупок (mealPlan.ts)
 *   POST /v1/food-photo   — AI-повар: фото еды → блюда, граммы, КБЖУ (foodPhoto.ts)
 *
 * Деплой: см. README.md в этой папке (wrangler deploy, ~10 минут).
 */
import Anthropic from "@anthropic-ai/sdk";
import { applyLimits, DEFAULT_MODEL, guardRequest, json, mapUpstreamError, parsePositiveInt, resolveDeviceId, t, usageOf } from "./common.ts";
import type { Env } from "./common.ts";
import { handleFoodPhoto } from "./foodPhoto.ts";
import { handleMealPlan } from "./mealPlan.ts";

export type { Env } from "./common.ts";

export const FEATURES = ["chat", "meal-plan", "food-photo"] as const;

interface ChatMessage {
  role: "user" | "assistant";
  content: string;
}

interface ChatRequest {
  system: string;
  messages: ChatMessage[];
  /** ru | kk — язык интерфейса пользователя, влияет на язык ответа при ошибках. */
  locale?: string;
  /** Необязательный идентификатор устройства для лимитов. */
  deviceId?: string;
}

interface ChatResponse {
  text: string;
  model: string;
  usage?: { input_tokens: number; output_tokens: number };
}

const MAX_MESSAGES = 30;
const MAX_MESSAGE_CHARS = 4000;
const MAX_SYSTEM_CHARS = 12000;
/** Тело запроса не больше 64 КБ: 30 сообщений × 4000 символов + промпт с запасом. */
const MAX_BODY_BYTES = 64 * 1024;

function sanitize(req: ChatRequest): ChatRequest | string {
  if (!req || typeof req !== "object") return "bad_body";
  if (typeof req.system !== "string" || req.system.length === 0) return "system_required";
  if (!Array.isArray(req.messages) || req.messages.length === 0) return "messages_required";
  const messages: ChatMessage[] = req.messages
    .filter((m) => m && (m.role === "user" || m.role === "assistant") && typeof m.content === "string")
    .map((m) => ({ role: m.role, content: m.content.slice(0, MAX_MESSAGE_CHARS) }))
    .slice(-MAX_MESSAGES);
  if (messages.length === 0 || messages[messages.length - 1].role !== "user") return "last_message_must_be_user";
  // Первое сообщение должно быть от пользователя.
  while (messages.length && messages[0].role !== "user") messages.shift();
  return {
    system: req.system.slice(0, MAX_SYSTEM_CHARS),
    messages,
    locale: req.locale === "kk" ? "kk" : "ru",
    deviceId: typeof req.deviceId === "string" ? req.deviceId : undefined,
  };
}

async function handleChat(request: Request, env: Env): Promise<Response> {
  const guard = guardRequest(request, env, MAX_BODY_BYTES);
  if (guard) return guard;
  const kv = env.RATE_LIMIT_KV!;

  let parsed: ChatRequest | string;
  try {
    parsed = sanitize((await request.json()) as ChatRequest);
  } catch {
    parsed = "bad_json";
  }
  if (typeof parsed === "string") return json({ error: parsed }, 400);
  const body = parsed;
  const locale = body.locale === "kk" ? "kk" : "ru";

  const deviceId = resolveDeviceId(request, body.deviceId);
  if (!deviceId) return json({ error: "bad_device_id" }, 400);

  const limited = await applyLimits(request, env, kv, deviceId, locale, {
    prefix: "rl",
    perDevice: parsePositiveInt(env.DAILY_LIMIT_PER_DEVICE, 40),
    deviceText: { ru: "Лимит вопросов на сегодня исчерпан. Продолжим завтра 💪", kk: "Бүгінгі сұрақ лимиті аяқталды. Ертең жалғастырамыз 💪" },
  });
  if (limited) return limited;

  const client = new Anthropic({ apiKey: env.ANTHROPIC_API_KEY, maxRetries: 2, timeout: 60_000 });
  const model = env.MODEL ?? DEFAULT_MODEL;

  try {
    const response = await client.beta.messages.create({
      model,
      max_tokens: 2048,
      system: [{ type: "text", text: body.system, cache_control: { type: "ephemeral" } }],
      messages: body.messages,
      output_config: { effort: "low" },
      // При отказе классификатора безопасности запрос автоматически уходит на рекомендованную модель.
      betas: ["server-side-fallback-2026-07-01"],
      fallbacks: "default",
    });

    if (response.stop_reason === "refusal") {
      return json({
        text: t(
          locale,
          "Я не могу ответить на этот вопрос. По медицинским вопросам обратись к врачу, а по тренировкам — к тренеру клуба.",
          "Бұл сұраққа жауап бере алмаймын. Медициналық сұрақтар бойынша дәрігерге, жаттығу бойынша клуб жаттықтырушысына жүгініңіз.",
        ),
        model: response.model,
      } satisfies ChatResponse);
    }

    const text = response.content
      .filter((b): b is Anthropic.Beta.BetaTextBlock => b.type === "text")
      .map((b) => b.text)
      .join("\n")
      .trim();

    const result: ChatResponse = {
      text: text || t(locale, "Нет ответа. Попробуй переформулировать.", "Жауап жоқ. Басқаша сұрап көріңіз."),
      model: response.model,
      usage: usageOf(response),
    };
    return json(result);
  } catch (error) {
    return mapUpstreamError(error, locale, { ru: "AI-тренер", kk: "AI-жаттықтырушы" });
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, model: env.MODEL ?? DEFAULT_MODEL, features: FEATURES });
    }

    if (request.method !== "POST") {
      return json({ error: "not_found" }, 404);
    }
    switch (url.pathname) {
      case "/v1/chat":
        return handleChat(request, env);
      case "/v1/meal-plan":
        return handleMealPlan(request, env);
      case "/v1/food-photo":
        return handleFoodPhoto(request, env);
      default:
        return json({ error: "not_found" }, 404);
    }
  },
};
