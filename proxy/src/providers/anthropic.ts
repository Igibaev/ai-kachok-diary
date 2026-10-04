/**
 * AiProvider поверх официального `@anthropic-ai/sdk` (Claude).
 *
 * Формы вызовов сохранены как в исходных эндпоинтах:
 *  - generateText — `client.beta.messages.create` с prompt-cache на системном промпте и
 *    server-side fallback (при отказе классификатора безопасности запрос уходит на рекомендованную модель);
 *  - generateJson — `client.messages.parse` + `output_config.format = zodOutputFormat(schema)`,
 *    изображение — блок `image` с base64.
 * Единственное место в прокси, где импортируется `Anthropic`.
 */
import Anthropic from "@anthropic-ai/sdk";
import { zodOutputFormat } from "@anthropic-ai/sdk/helpers/zod";
import { ProviderError } from "./types.ts";
import type { AiProvider, GenerateJsonInput, GenerateTextInput, ProviderResult } from "./types.ts";

/** Модель по умолчанию для семейства anthropic (пример — можно сменить переменной AI_MODEL). */
export const ANTHROPIC_DEFAULT_MODEL = "claude-opus-5";

export interface AnthropicProviderConfig {
  apiKey: string;
  model: string;
}

/** Ошибки SDK → ProviderError. Экспортируется для unit-тестов (без сети). */
export function toProviderError(error: unknown): ProviderError {
  if (error instanceof ProviderError) return error;
  if (error instanceof Anthropic.AuthenticationError || error instanceof Anthropic.PermissionDeniedError) {
    return new ProviderError("auth", error.message, error.status);
  }
  if (error instanceof Anthropic.RateLimitError) return new ProviderError("rate_limit", error.message, error.status);
  if (error instanceof Anthropic.APIConnectionTimeoutError) return new ProviderError("timeout", error.message);
  if (error instanceof Anthropic.APIConnectionError) return new ProviderError("connection", error.message);
  if (error instanceof Anthropic.APIError) return new ProviderError("upstream", error.message, error.status);
  if (error instanceof Anthropic.AnthropicError) {
    // Не ошибка API: SDK не смог разобрать структурированный ответ («Failed to parse structured output»).
    return new ProviderError("bad_output", error.message);
  }
  return new ProviderError("upstream", error instanceof Error ? error.message : String(error));
}

const usageOf = (response: { usage: { input_tokens: number; output_tokens: number } }) => ({
  inputTokens: response.usage.input_tokens,
  outputTokens: response.usage.output_tokens,
});

export function createAnthropicProvider(config: AnthropicProviderConfig): AiProvider {
  const client = (maxRetries: number | undefined, timeout: number) =>
    new Anthropic({ apiKey: config.apiKey, maxRetries: maxRetries ?? 2, timeout });

  return {
    id: "anthropic",
    model: config.model,

    async generateText(input: GenerateTextInput): Promise<ProviderResult<string>> {
      try {
        const response = await client(input.maxRetries, input.timeoutMs).beta.messages.create({
          model: config.model,
          max_tokens: input.maxTokens,
          system: [{ type: "text", text: input.system, cache_control: { type: "ephemeral" } }],
          messages: input.messages,
          output_config: { effort: "low" },
          // При отказе классификатора безопасности запрос автоматически уходит на рекомендованную модель.
          betas: ["server-side-fallback-2026-07-01"],
          fallbacks: "default",
        });
        if (response.stop_reason === "refusal") throw new ProviderError("refused", "model refused");
        const text = response.content
          .filter((b): b is Anthropic.Beta.BetaTextBlock => b.type === "text")
          .map((b) => b.text)
          .join("\n")
          .trim();
        return { value: text, model: response.model, usage: usageOf(response) };
      } catch (error) {
        throw toProviderError(error);
      }
    },

    async generateJson<T>(input: GenerateJsonInput<T>): Promise<ProviderResult<T>> {
      const content: Anthropic.ContentBlockParam[] = [];
      if (input.image) {
        content.push({ type: "image", source: { type: "base64", media_type: input.image.mediaType, data: input.image.base64 } });
      }
      content.push({ type: "text", text: input.userText });
      try {
        const response = await client(input.maxRetries, input.timeoutMs).messages.parse({
          model: config.model,
          max_tokens: input.maxTokens,
          system: input.system,
          messages: [{ role: "user", content }],
          output_config: { effort: "low", format: zodOutputFormat(input.schema) },
        });
        if (response.stop_reason === "refusal") throw new ProviderError("refused", "model refused");
        if (response.stop_reason === "max_tokens" || response.parsed_output === null || response.parsed_output === undefined) {
          throw new ProviderError("bad_output", "structured output truncated or missing");
        }
        return { value: response.parsed_output as T, model: response.model, usage: usageOf(response) };
      } catch (error) {
        throw toProviderError(error);
      }
    },
  };
}
