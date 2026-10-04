/**
 * Общие помощники прокси: JSON-ответы, локализация ошибок, лимиты в KV, маппинг ошибок провайдера.
 * Используются /v1/chat, /v1/meal-plan и /v1/food-photo. Про конкретную нейросеть здесь ничего не известно —
 * только `ProviderError` из providers/.
 */
import { ProviderError, ProviderNotConfiguredError, resolveProvider } from "./providers/index.ts";
import type { AiProvider, Feature, ProviderEnv, ProviderResult } from "./providers/index.ts";

/** Переменные провайдера (ANTHROPIC_API_KEY, AI_*, CHEF_*, MODEL) описаны в providers/index.ts. */
export interface Env extends ProviderEnv {
  /** Токен, который зашит в приложение (brands/<client>.properties → aiProxyToken). */
  APP_TOKEN: string;
  /** Лимит запросов чата на одно устройство в сутки. */
  DAILY_LIMIT_PER_DEVICE?: string;
  /** Общий лимит запросов (чат + план + фото) на всех в сутки (защита бюджета клуба). */
  DAILY_LIMIT_GLOBAL?: string;
  /** KV для счётчиков лимитов. ОБЯЗАТЕЛЕН: без привязки прокси отвечает 503 (fail-closed),
   *  иначе извлекаемый из APK токен давал бы неограниченный расход ключа клуба. */
  RATE_LIMIT_KV?: KVNamespace;
  /** Лимит запросов с одного IP в сутки (защита от смены X-Device-Id клиентом). */
  DAILY_LIMIT_PER_IP?: string;
  /** Планов питания на устройство в сутки (AI-повар). */
  DAILY_LIMIT_PLANS_PER_DEVICE?: string;
  /** Разборов фото еды на устройство в сутки (AI-повар). */
  DAILY_LIMIT_PHOTOS_PER_DEVICE?: string;
}

export type Locale = "ru" | "kk";

/** Идентификатор устройства — UUID/hex/base64url без экзотики; иначе ключ KV может превысить 512 байт. */
export const DEVICE_ID_RE = /^[A-Za-z0-9_-]{1,64}$/;

export const json = (body: unknown, status = 200, extra: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", ...extra },
  });

export const t = (locale: string | undefined, ru: string, kk: string) => (locale === "kk" ? kk : ru);

export const normalizeLocale = (value: unknown): Locale => (value === "kk" ? "kk" : "ru");

export function todayKey(): string {
  return new Date().toISOString().slice(0, 10);
}

export async function bumpCounter(kv: KVNamespace, key: string, limit: number): Promise<boolean> {
  const current = Number((await kv.get(key)) ?? "0");
  if (current >= limit) return false;
  // TTL 2 суток — счётчик сам исчезнет.
  await kv.put(key, String(current + 1), { expirationTtl: 60 * 60 * 48 });
  return true;
}

export function parsePositiveInt(value: string | undefined, fallback: number): number {
  const n = Number(value);
  return Number.isFinite(n) && n > 0 ? Math.floor(n) : fallback;
}

/**
 * Общие проверки перед любым AI-запросом: токен приложения, наличие KV, content-type, размер тела,
 * формат X-Device-Id. Возвращает Response с ошибкой или null, если всё в порядке.
 */
export function guardRequest(request: Request, env: Env, maxBodyBytes: number): Response | null {
  if (!env.APP_TOKEN || request.headers.get("x-app-token") !== env.APP_TOKEN) {
    return json({ error: "unauthorized" }, 401);
  }
  // Fail-closed: без KV лимиты не работают, а токен приложения извлекаем из APK —
  // лучше отказать, чем сжечь бюджет клуба.
  if (!env.RATE_LIMIT_KV) {
    return json({ error: "rate_limit_not_configured" }, 503);
  }
  const contentType = request.headers.get("content-type") ?? "";
  if (!contentType.toLowerCase().startsWith("application/json")) {
    return json({ error: "unsupported_media_type" }, 415);
  }
  const contentLength = Number(request.headers.get("content-length") ?? "0");
  if (!Number.isFinite(contentLength) || contentLength <= 0 || contentLength > maxBodyBytes) {
    return json({ error: "payload_too_large" }, 413);
  }
  const headerDeviceId = request.headers.get("x-device-id");
  if (headerDeviceId !== null && !DEVICE_ID_RE.test(headerDeviceId)) {
    return json({ error: "bad_device_id" }, 400);
  }
  return null;
}

/**
 * Идентификатор устройства: заголовок X-Device-Id (уже проверен в guardRequest) → deviceId из тела → "anonymous".
 * Некорректный deviceId в теле игнорируется (как в исходном /v1/chat), а не отклоняется.
 */
export function resolveDeviceId(request: Request, bodyDeviceId: string | undefined): string | null {
  const fromBody = bodyDeviceId !== undefined && DEVICE_ID_RE.test(bodyDeviceId) ? bodyDeviceId : undefined;
  const deviceId = request.headers.get("x-device-id") ?? fromBody ?? "anonymous";
  return DEVICE_ID_RE.test(deviceId) ? deviceId : null;
}

export interface LimitSpec {
  /** Префикс ключа KV на устройство: rl (чат), rlp (план), rlf (фото). */
  prefix: "rl" | "rlp" | "rlf";
  perDevice: number;
  /** Текст 429 при исчерпании лимита устройства. */
  deviceText: { ru: string; kk: string };
  /** Текст 429 при исчерпании общего лимита клуба (по умолчанию — текст AI-тренера). */
  globalText?: { ru: string; kk: string };
}

/**
 * Проверяет и увеличивает счётчики: на устройство (свой префикс), на IP и общий — общий и IP-счётчики
 * одни на все эндпоинты. Возвращает Response 429/503 или null.
 */
export async function applyLimits(
  request: Request,
  env: Env,
  kv: KVNamespace,
  deviceId: string,
  locale: Locale,
  spec: LimitSpec,
): Promise<Response | null> {
  const global = parsePositiveInt(env.DAILY_LIMIT_GLOBAL, 5000);
  const chatPerDevice = parsePositiveInt(env.DAILY_LIMIT_PER_DEVICE, 40);
  const day = todayKey();
  let okDevice: boolean;
  try {
    okDevice = await bumpCounter(kv, `${spec.prefix}:${day}:${deviceId}`, spec.perDevice);
    // Лимит по IP: deviceId контролируется клиентом и его можно менять на каждый запрос.
    const ip = request.headers.get("cf-connecting-ip");
    if (ip && okDevice) {
      const perIp = parsePositiveInt(env.DAILY_LIMIT_PER_IP, chatPerDevice * 5);
      okDevice = await bumpCounter(kv, `rl:${day}:ip:${ip}`, perIp);
    }
  } catch {
    return json({ error: "rate_limit_unavailable" }, 503);
  }
  if (!okDevice) {
    return json({ error: "device_limit", text: t(locale, spec.deviceText.ru, spec.deviceText.kk) }, 429);
  }
  let okGlobal: boolean;
  try {
    okGlobal = await bumpCounter(kv, `rl:${day}:__global__`, global);
  } catch {
    return json({ error: "rate_limit_unavailable" }, 503);
  }
  if (!okGlobal) {
    return json(
      {
        error: "global_limit",
        text: t(
          locale,
          spec.globalText?.ru ?? "AI-тренер сейчас отдыхает. Попробуй позже.",
          spec.globalText?.kk ?? "AI-жаттықтырушы қазір демалып жатыр. Кейінірек көріңіз.",
        ),
      },
      429,
    );
  }
  return null;
}

/**
 * Провайдер для фичи или готовый ответ 503 provider_not_configured (нет ключа / некорректные AI_* или CHEF_*).
 * Проверяется до списания лимитов, чтобы ошибка конфигурации не ела дневные квоты пользователей.
 */
export function providerOrResponse(env: Env, feature: Feature, locale: Locale): AiProvider | Response {
  try {
    return resolveProvider(env, feature);
  } catch (error) {
    const detail = error instanceof ProviderNotConfiguredError ? error.message : "provider";
    console.error(`provider_not_configured (${feature}): ${detail}`);
    return json(
      {
        error: "provider_not_configured",
        text: t(locale, "AI временно не настроен. Обратись к администратору клуба.", "AI әлі бапталмаған. Клуб әкімшісіне хабарласыңыз."),
      },
      503,
    );
  }
}

/**
 * Единый маппинг `ProviderError` в ответы `{error, text}` — формат общий для всех эндпоинтов.
 * `badOutputText` — текст для 502 bad_model_output (ответ модели обрезан / не прошёл схему).
 * `refused` сюда обычно не доходит — эндпоинты обрабатывают отказ сами (чат → 200 с текстом, повар → 422).
 */
export function mapUpstreamError(
  error: unknown,
  locale: Locale,
  subject: { ru: string; kk: string },
  badOutputText?: { ru: string; kk: string },
): Response {
  if (error instanceof ProviderError) {
    switch (error.kind) {
      case "auth":
        return json({ error: "upstream_auth", text: t(locale, `${subject.ru} временно недоступен (ключ).`, `${subject.kk} уақытша қолжетімсіз (кілт).`) }, 502);
      case "rate_limit":
        return json({ error: "upstream_rate_limit", text: t(locale, "Слишком много запросов. Попробуй через минуту.", "Сұраныс тым көп. Бір минуттан кейін көріңіз.") }, 429);
      case "connection":
        return json({ error: "upstream_connection", text: t(locale, "Нет связи с AI. Проверь интернет.", "AI-мен байланыс жоқ. Интернетті тексеріңіз.") }, 504);
      case "timeout":
        return json({ error: "upstream_timeout", text: t(locale, "AI не ответил вовремя. Попробуй ещё раз.", "AI уақытында жауап бермеді. Қайта көріңіз.") }, 504);
      case "refused":
        return json({ error: "refused", text: t(locale, `${subject.ru} не может ответить на этот запрос.`, `${subject.kk} бұл сұранысқа жауап бере алмайды.`) }, 422);
      case "bad_output":
        return json(
          {
            error: "bad_model_output",
            text: t(
              locale,
              badOutputText?.ru ?? `${subject.ru} вернул неполный ответ. Попробуй ещё раз.`,
              badOutputText?.kk ?? `${subject.kk} толық емес жауап қайтарды. Қайта көріңіз.`,
            ),
          },
          502,
        );
      case "upstream":
        return json({ error: "upstream_error", status: error.status, text: t(locale, `${subject.ru} временно недоступен.`, `${subject.kk} уақытша қолжетімсіз.`) }, 502);
    }
  }
  console.error("internal error", error instanceof Error ? error.stack ?? error.message : error);
  return json({ error: "internal", text: t(locale, "Внутренняя ошибка.", "Ішкі қате.") }, 500);
}

/** usage провайдера → формат ответа приложения `{ input_tokens, output_tokens }`. */
export function usageOf(result: ProviderResult<unknown>): { input_tokens: number; output_tokens: number } | undefined {
  return result.usage ? { input_tokens: result.usage.inputTokens, output_tokens: result.usage.outputTokens } : undefined;
}
