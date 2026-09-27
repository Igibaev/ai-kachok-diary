/**
 * FitCoach AI — AI-прокси для white-label приложения.
 *
 * Зачем: ключ Anthropic хранится у фитнес-клуба (в секретах Cloudflare), а не в приложении.
 * Приложение шлёт историю диалога и системный промпт, прокси проверяет токен приложения,
 * лимиты на устройство и общий дневной лимит, вызывает Claude и возвращает текст ответа.
 *
 * Деплой: см. README.md в этой папке (wrangler deploy, ~10 минут).
 */
import Anthropic from "@anthropic-ai/sdk";

export interface Env {
  ANTHROPIC_API_KEY: string;
  /** Токен, который зашит в приложение (brands/<client>.properties → aiProxyToken). */
  APP_TOKEN: string;
  /** Модель по умолчанию. */
  MODEL?: string;
  /** Лимит запросов на одно устройство в сутки. */
  DAILY_LIMIT_PER_DEVICE?: string;
  /** Общий лимит запросов на всех в сутки (защита бюджета клуба). */
  DAILY_LIMIT_GLOBAL?: string;
  /** KV для счётчиков лимитов (опционально; без KV лимиты не применяются). */
  RATE_LIMIT_KV?: KVNamespace;
}

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

const json = (body: unknown, status = 200, extra: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", ...extra },
  });

const t = (locale: string | undefined, ru: string, kk: string) => (locale === "kk" ? kk : ru);

function todayKey(): string {
  return new Date().toISOString().slice(0, 10);
}

async function bumpCounter(kv: KVNamespace, key: string, limit: number): Promise<boolean> {
  const current = Number((await kv.get(key)) ?? "0");
  if (current >= limit) return false;
  // TTL 2 суток — счётчик сам исчезнет.
  await kv.put(key, String(current + 1), { expirationTtl: 60 * 60 * 48 });
  return true;
}

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
    deviceId: typeof req.deviceId === "string" ? req.deviceId.slice(0, 64) : undefined,
  };
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, model: env.MODEL ?? "claude-opus-5" });
    }

    if (request.method !== "POST" || url.pathname !== "/v1/chat") {
      return json({ error: "not_found" }, 404);
    }

    if (!env.APP_TOKEN || request.headers.get("x-app-token") !== env.APP_TOKEN) {
      return json({ error: "unauthorized" }, 401);
    }

    let parsed: ChatRequest | string;
    try {
      parsed = sanitize((await request.json()) as ChatRequest);
    } catch {
      parsed = "bad_json";
    }
    if (typeof parsed === "string") return json({ error: parsed }, 400);
    const body = parsed;

    const deviceId = request.headers.get("x-device-id") ?? body.deviceId ?? "anonymous";

    if (env.RATE_LIMIT_KV) {
      const perDevice = Number(env.DAILY_LIMIT_PER_DEVICE ?? "40");
      const global = Number(env.DAILY_LIMIT_GLOBAL ?? "5000");
      const day = todayKey();
      const okDevice = await bumpCounter(env.RATE_LIMIT_KV, `rl:${day}:${deviceId}`, perDevice);
      if (!okDevice) {
        return json(
          {
            error: "device_limit",
            text: t(body.locale, "Лимит вопросов на сегодня исчерпан. Продолжим завтра 💪", "Бүгінгі сұрақ лимиті аяқталды. Ертең жалғастырамыз 💪"),
          },
          429,
        );
      }
      const okGlobal = await bumpCounter(env.RATE_LIMIT_KV, `rl:${day}:__global__`, global);
      if (!okGlobal) {
        return json(
          {
            error: "global_limit",
            text: t(body.locale, "AI-тренер сейчас отдыхает. Попробуй позже.", "AI-жаттықтырушы қазір демалып жатыр. Кейінірек көріңіз."),
          },
          429,
        );
      }
    }

    const client = new Anthropic({ apiKey: env.ANTHROPIC_API_KEY, maxRetries: 2, timeout: 60_000 });
    const model = env.MODEL ?? "claude-opus-5";

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
            body.locale,
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
        text: text || t(body.locale, "Нет ответа. Попробуй переформулировать.", "Жауап жоқ. Басқаша сұрап көріңіз."),
        model: response.model,
        usage: { input_tokens: response.usage.input_tokens, output_tokens: response.usage.output_tokens },
      };
      return json(result);
    } catch (error) {
      if (error instanceof Anthropic.AuthenticationError) {
        return json({ error: "upstream_auth", text: t(body.locale, "AI-тренер временно недоступен (ключ).", "AI-жаттықтырушы уақытша қолжетімсіз (кілт).") }, 502);
      }
      if (error instanceof Anthropic.RateLimitError) {
        return json({ error: "upstream_rate_limit", text: t(body.locale, "Слишком много запросов. Попробуй через минуту.", "Сұраныс тым көп. Бір минуттан кейін көріңіз.") }, 429);
      }
      if (error instanceof Anthropic.APIConnectionError) {
        return json({ error: "upstream_connection", text: t(body.locale, "Нет связи с AI. Проверь интернет.", "AI-мен байланыс жоқ. Интернетті тексеріңіз.") }, 504);
      }
      if (error instanceof Anthropic.APIError) {
        return json({ error: "upstream_error", status: error.status, text: t(body.locale, "AI-тренер временно недоступен.", "AI-жаттықтырушы уақытша қолжетімсіз.") }, 502);
      }
      return json({ error: "internal", text: t(body.locale, "Внутренняя ошибка.", "Ішкі қате.") }, 500);
    }
  },
};
