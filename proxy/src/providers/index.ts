/**
 * Выбор нейросети переменными окружения (wrangler.toml [vars] + wrangler secret put).
 *
 * Общие (чат и повар):
 *   AI_PROVIDER      anthropic (по умолчанию) | openai (любой OpenAI-совместимый API)
 *   AI_MODEL         модель; по умолчанию claude-opus-5 (anthropic) / gpt-4o (openai) — примеры
 *   AI_BASE_URL      только для openai; по умолчанию https://api.openai.com/v1
 *   AI_API_KEY       секрет — ключ для openai; для anthropic по-прежнему ANTHROPIC_API_KEY
 *   AI_EXTRA_HEADERS JSON-строка с доп. заголовками (OpenRouter: HTTP-Referer / X-Title), опционально
 * Повар (/v1/meal-plan + /v1/food-photo) — переопределения CHEF_PROVIDER / CHEF_MODEL / CHEF_BASE_URL /
 *   CHEF_API_KEY / CHEF_EXTRA_HEADERS: если заданы, повар идёт через них, иначе наследует общие.
 * Обратная совместимость: старая переменная MODEL работает как AI_MODEL (если AI_MODEL не задана).
 */
import { ANTHROPIC_DEFAULT_MODEL, createAnthropicProvider } from "./anthropic.ts";
import { OPENAI_DEFAULT_BASE_URL, OPENAI_DEFAULT_MODEL, createOpenAiCompatibleProvider } from "./openaiCompatible.ts";
import type { AiProvider, ProviderId } from "./types.ts";

export type Feature = "chat" | "chef";

/** Переменные окружения, которые читает слой провайдеров (часть общего Env прокси). */
export interface ProviderEnv {
  ANTHROPIC_API_KEY?: string;
  /** Устаревшее имя AI_MODEL — продолжает работать. */
  MODEL?: string;
  AI_PROVIDER?: string;
  AI_MODEL?: string;
  AI_BASE_URL?: string;
  AI_API_KEY?: string;
  AI_EXTRA_HEADERS?: string;
  CHEF_PROVIDER?: string;
  CHEF_MODEL?: string;
  CHEF_BASE_URL?: string;
  CHEF_API_KEY?: string;
  CHEF_EXTRA_HEADERS?: string;
}

/** Конфигурация провайдера некорректна или нет ключа → прокси отвечает 503 provider_not_configured. */
export class ProviderNotConfiguredError extends Error {
  readonly code = "provider_not_configured";
  constructor(message: string) {
    super(message);
    this.name = "ProviderNotConfiguredError";
  }
}

export interface ResolvedProviderConfig {
  id: ProviderId;
  model: string;
  /** Только для openai. */
  baseUrl?: string;
  apiKey?: string;
  extraHeaders?: Record<string, string>;
}

const PROVIDER_IDS: readonly ProviderId[] = ["anthropic", "openai"];

const nonEmpty = (value: string | undefined): string | undefined => {
  const v = value?.trim();
  return v ? v : undefined;
};

function parseProviderId(raw: string | undefined, variable: string): ProviderId {
  const value = nonEmpty(raw)?.toLowerCase() ?? "anthropic";
  if ((PROVIDER_IDS as readonly string[]).includes(value)) return value as ProviderId;
  throw new ProviderNotConfiguredError(`${variable}="${raw}" — допустимо: ${PROVIDER_IDS.join(" | ")}`);
}

function parseHeaders(raw: string | undefined, variable: string): Record<string, string> | undefined {
  const text = nonEmpty(raw);
  if (!text) return undefined;
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new ProviderNotConfiguredError(`${variable} — не JSON-объект`);
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new ProviderNotConfiguredError(`${variable} — ожидается JSON-объект {"Header": "value"}`);
  }
  const headers: Record<string, string> = {};
  for (const [k, v] of Object.entries(parsed as Record<string, unknown>)) {
    if (typeof v !== "string") throw new ProviderNotConfiguredError(`${variable} — значение заголовка ${k} должно быть строкой`);
    headers[k] = v;
  }
  return headers;
}

/**
 * Разрешает конфигурацию провайдера для фичи. Для повара переопределения CHEF_* имеют приоритет;
 * если CHEF_PROVIDER задаёт другое семейство API, общие AI_MODEL/AI_BASE_URL/AI_API_KEY НЕ наследуются
 * (они относятся к другому API) — только значения по умолчанию этого семейства.
 */
export function resolveProviderConfig(env: ProviderEnv, feature: Feature): ResolvedProviderConfig {
  const generalId = parseProviderId(env.AI_PROVIDER, "AI_PROVIDER");
  const chefOverride = feature === "chef" ? nonEmpty(env.CHEF_PROVIDER) : undefined;
  const id = chefOverride ? parseProviderId(chefOverride, "CHEF_PROVIDER") : generalId;
  const inheritGeneral = feature === "chat" || id === generalId;

  const generalModel = nonEmpty(env.AI_MODEL) ?? nonEmpty(env.MODEL);
  const own = feature === "chef";
  const model =
    (own ? nonEmpty(env.CHEF_MODEL) : undefined) ??
    (inheritGeneral ? generalModel : undefined) ??
    (id === "anthropic" ? ANTHROPIC_DEFAULT_MODEL : OPENAI_DEFAULT_MODEL);

  if (id === "anthropic") {
    const apiKey = (own ? nonEmpty(env.CHEF_API_KEY) : undefined) ?? nonEmpty(env.ANTHROPIC_API_KEY);
    return { id, model, apiKey };
  }

  const baseUrl =
    (own ? nonEmpty(env.CHEF_BASE_URL) : undefined) ?? (inheritGeneral ? nonEmpty(env.AI_BASE_URL) : undefined) ?? OPENAI_DEFAULT_BASE_URL;
  const apiKey = (own ? nonEmpty(env.CHEF_API_KEY) : undefined) ?? (inheritGeneral ? nonEmpty(env.AI_API_KEY) : undefined);
  const extraHeaders =
    (own ? parseHeaders(env.CHEF_EXTRA_HEADERS, "CHEF_EXTRA_HEADERS") : undefined) ??
    (inheritGeneral ? parseHeaders(env.AI_EXTRA_HEADERS, "AI_EXTRA_HEADERS") : undefined);
  return { id, model, baseUrl, apiKey, extraHeaders };
}

/** Создаёт провайдера для фичи; без ключа бросает ProviderNotConfiguredError (→ 503 provider_not_configured). */
export function resolveProvider(env: ProviderEnv, feature: Feature): AiProvider {
  const cfg = resolveProviderConfig(env, feature);
  if (!cfg.apiKey) {
    const variable = cfg.id === "anthropic" ? "ANTHROPIC_API_KEY" : "AI_API_KEY";
    throw new ProviderNotConfiguredError(
      `нет ключа для провайдера ${cfg.id} (${feature}): задайте секрет ${variable}${feature === "chef" ? " или CHEF_API_KEY" : ""} (wrangler secret put)`,
    );
  }
  if (cfg.id === "anthropic") return createAnthropicProvider({ apiKey: cfg.apiKey, model: cfg.model });
  return createOpenAiCompatibleProvider({ apiKey: cfg.apiKey, model: cfg.model, baseUrl: cfg.baseUrl ?? OPENAI_DEFAULT_BASE_URL, extraHeaders: cfg.extraHeaders });
}

export interface ProviderSummary {
  id: ProviderId | "misconfigured";
  model: string;
  /** false, если нет ключа или конфигурация некорректна — без раскрытия причин наружу. */
  configured: boolean;
}

/** Для /health: id и модель без ключей и baseUrl. Никогда не бросает. */
export function describeProviders(env: ProviderEnv): { chat: ProviderSummary; chef: ProviderSummary } {
  const describe = (feature: Feature): ProviderSummary => {
    try {
      const cfg = resolveProviderConfig(env, feature);
      return { id: cfg.id, model: cfg.model, configured: Boolean(cfg.apiKey) };
    } catch {
      return { id: "misconfigured", model: "", configured: false };
    }
  };
  return { chat: describe("chat"), chef: describe("chef") };
}

export type { AiProvider, ChatMessage, GenerateJsonInput, GenerateTextInput, ImagePart, ProviderResult } from "./types.ts";
export { ProviderError } from "./types.ts";
