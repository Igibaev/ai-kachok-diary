/**
 * Провайдер-независимый контракт AI-слоя прокси.
 *
 * Эндпоинты (/v1/chat, /v1/meal-plan, /v1/food-photo) работают только с `AiProvider` и не знают,
 * какая нейросеть за ним: Claude через официальный SDK (providers/anthropic.ts) или любая модель
 * с OpenAI-совместимым API — GPT, Gemini, DeepSeek, OpenRouter, локальная Ollama/vLLM
 * (providers/openaiCompatible.ts). Выбор — переменными окружения, см. providers/index.ts.
 */
import type { z } from "zod";

export type ChatMessage = { role: "user" | "assistant"; content: string };

export type ImageMediaType = "image/jpeg" | "image/png" | "image/webp";

export type ImagePart = { mediaType: ImageMediaType; base64: string };

export interface GenerateTextInput {
  system: string;
  messages: ChatMessage[];
  maxTokens: number;
  /** Таймаут одного вызова модели, мс. */
  timeoutMs: number;
  /** Повторы при сетевых/5xx ошибках (поддерживает Anthropic SDK; OpenAI-совместимый слой — без повторов). */
  maxRetries?: number;
}

export interface GenerateJsonInput<T> {
  system: string;
  userText: string;
  image?: ImagePart;
  /** Zod-схема ответа: из неё строится JSON Schema для модели и ею же валидируется результат. */
  schema: z.ZodType<T>;
  /** Имя схемы для провайдеров, которым оно нужно (OpenAI `response_format.json_schema.name`). */
  schemaName: string;
  maxTokens: number;
  timeoutMs: number;
  maxRetries?: number;
}

export interface ProviderResult<T> {
  value: T;
  /** Фактическая модель, которая ответила (может отличаться от запрошенной — fallback у Anthropic). */
  model: string;
  usage?: { inputTokens: number; outputTokens: number };
}

export type ProviderId = "anthropic" | "openai";

export interface AiProvider {
  /** Семейство API: anthropic — Messages API через SDK; openai — любой OpenAI-совместимый `/chat/completions`. */
  readonly id: ProviderId;
  readonly model: string;
  generateText(input: GenerateTextInput): Promise<ProviderResult<string>>;
  generateJson<T>(input: GenerateJsonInput<T>): Promise<ProviderResult<T>>;
}

export type ProviderErrorKind = "auth" | "rate_limit" | "refused" | "bad_output" | "timeout" | "connection" | "upstream";

/**
 * Единая ошибка провайдера. `kind` определяет HTTP-ответ прокси (см. common.ts → mapUpstreamError):
 *  auth → 502 upstream_auth, rate_limit → 429, refused → 422 (эндпоинт решает сам), bad_output → 502 bad_model_output,
 *  timeout → 504, connection → 504, upstream → 502 (с `status` провайдера).
 */
export class ProviderError extends Error {
  readonly kind: ProviderErrorKind;
  /** HTTP-статус провайдера, если был. */
  readonly status?: number;
  // Без parameter properties: Node запускает тесты из .ts в strip-only режиме.
  constructor(kind: ProviderErrorKind, message: string, status?: number) {
    super(message);
    this.name = "ProviderError";
    this.kind = kind;
    this.status = status;
  }
}
