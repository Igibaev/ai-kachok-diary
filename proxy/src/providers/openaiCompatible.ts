/**
 * AiProvider для любого OpenAI-совместимого API (`POST {baseUrl}/chat/completions`) — без SDK, через `fetch`.
 *
 * Покрывает OpenAI (GPT-4o/4.1/5…), Google Gemini (`https://generativelanguage.googleapis.com/v1beta/openai`),
 * DeepSeek, Mistral, Groq, OpenRouter, xAI, Together, Yandex/Sber через совместимый шлюз,
 * локальные Ollama / vLLM / LM Studio.
 *
 * generateJson:
 *  1) `response_format: { type: "json_schema", json_schema: { name, schema, strict: true } }` — JSON Schema строится
 *     из zod (`z.toJSONSchema`, zod v4, без новых зависимостей);
 *  2) если провайдер отвечает 400 с упоминанием `response_format`/`json_schema` — повтор с `{ type: "json_object" }`
 *     и описанием схемы в системном промпте (генерируется из той же JSON Schema);
 *  3) ответ очищается от ```json-ограждений, парсится и валидируется `schema.safeParse`; при ошибке — один повтор
 *     с текстом ошибки валидации, затем `bad_output`.
 * Изображение передаётся как `image_url` с data-URL (`data:<mediaType>;base64,…`, detail: high).
 */
import { z } from "zod";
import { ProviderError } from "./types.ts";
import type { AiProvider, GenerateJsonInput, GenerateTextInput, ProviderResult } from "./types.ts";

export const OPENAI_DEFAULT_BASE_URL = "https://api.openai.com/v1";
/** Модель по умолчанию для семейства openai — ПРИМЕР; подставьте модель своего провайдера через AI_MODEL. */
export const OPENAI_DEFAULT_MODEL = "gpt-4o";

export interface OpenAiCompatibleConfig {
  apiKey: string;
  model: string;
  /** База API без завершающего слэша, например `https://api.openai.com/v1`. */
  baseUrl: string;
  /** Доп. заголовки (OpenRouter: HTTP-Referer / X-Title; корпоративные шлюзы: свои). */
  extraHeaders?: Record<string, string>;
}

type OaiContentPart = { type: "text"; text: string } | { type: "image_url"; image_url: { url: string; detail: "high" } };
type OaiMessage = { role: "system" | "user" | "assistant"; content: string | OaiContentPart[] };

type JsonMode = { type: "json_schema"; name: string; schema: Record<string, unknown> } | { type: "json_object" };

interface OaiChoiceMessage {
  content?: string | Array<{ type?: string; text?: string }> | null;
  refusal?: string | null;
}

interface OaiResponse {
  model?: string;
  choices?: Array<{ finish_reason?: string | null; message?: OaiChoiceMessage }>;
  usage?: { prompt_tokens?: number; completion_tokens?: number };
}

/** JSON Schema для strict-режима OpenAI: без `$schema`, все объекты с `additionalProperties:false` и полным `required`
 *  (zod v4 генерирует именно так, если в схеме нет optional-полей). */
export function toStrictJsonSchema(schema: z.ZodType): Record<string, unknown> {
  const { $schema: _ignored, ...rest } = z.toJSONSchema(schema, { target: "draft-7" }) as Record<string, unknown>;
  return rest;
}

/** Описание схемы для режима json_object — генерируется автоматически, не руками. */
export function schemaInstruction(jsonSchema: Record<string, unknown>): string {
  return [
    "Ответ — строго один JSON-объект по следующей JSON Schema, без пояснений, без markdown и без текста вокруг.",
    "Все поля обязательны, значения-перечисления — строго из списка enum.",
    `JSON Schema: ${JSON.stringify(jsonSchema)}`,
  ].join("\n");
}

/** Снимает ```json … ``` ограждения и текст вокруг первого/последнего { }. */
export function extractJson(raw: string): string {
  let text = raw.trim();
  const fence = text.match(/^```[a-zA-Z]*\s*([\s\S]*?)\s*```$/);
  if (fence) text = fence[1].trim();
  if (!text.startsWith("{")) {
    const start = text.indexOf("{");
    const end = text.lastIndexOf("}");
    if (start >= 0 && end > start) text = text.slice(start, end + 1);
  }
  return text;
}

function contentText(message: OaiChoiceMessage | undefined): string {
  const content = message?.content;
  if (typeof content === "string") return content;
  if (Array.isArray(content)) return content.map((p) => (typeof p?.text === "string" ? p.text : "")).join("");
  return "";
}

function dataUrl(image: NonNullable<GenerateJsonInput<unknown>["image"]>): string {
  return `data:${image.mediaType};base64,${image.base64}`;
}

function mentionsResponseFormat(bodyText: string): boolean {
  const lower = bodyText.toLowerCase();
  return lower.includes("response_format") || lower.includes("json_schema");
}

export function createOpenAiCompatibleProvider(config: OpenAiCompatibleConfig): AiProvider {
  const baseUrl = config.baseUrl.replace(/\/+$/, "");
  const endpoint = `${baseUrl}/chat/completions`;

  /** Один HTTP-вызов. Возвращает ответ целиком; HTTP-ошибки (кроме 400, которое решает вызывающий) → ProviderError. */
  async function call(body: Record<string, unknown>, timeoutMs: number): Promise<{ status: number; text: string; data: OaiResponse | null }> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    let response: Response;
    try {
      // globalThis.fetch берётся в момент вызова — тесты подменяют его.
      response = await globalThis.fetch(endpoint, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          authorization: `Bearer ${config.apiKey}`,
          ...(config.extraHeaders ?? {}),
        },
        body: JSON.stringify(body),
        signal: controller.signal,
      });
    } catch (error) {
      if (controller.signal.aborted || (error instanceof Error && error.name === "AbortError")) {
        throw new ProviderError("timeout", `timeout after ${timeoutMs} ms`);
      }
      throw new ProviderError("connection", error instanceof Error ? error.message : String(error));
    } finally {
      clearTimeout(timer);
    }

    const text = await response.text().catch(() => "");
    if (response.status === 401 || response.status === 403) throw new ProviderError("auth", text.slice(0, 500), response.status);
    if (response.status === 429) throw new ProviderError("rate_limit", text.slice(0, 500), response.status);
    if (response.status === 408 || response.status === 504) throw new ProviderError("timeout", text.slice(0, 500), response.status);
    if (response.status >= 500) throw new ProviderError("upstream", text.slice(0, 500), response.status);
    if (response.status === 400) return { status: 400, text, data: null };
    if (!response.ok) throw new ProviderError("upstream", text.slice(0, 500), response.status);

    let data: OaiResponse;
    try {
      data = JSON.parse(text) as OaiResponse;
    } catch {
      throw new ProviderError("bad_output", "non-JSON response body from provider", response.status);
    }
    return { status: response.status, text, data };
  }

  /** Извлекает текст первого choice; finish_reason length → bad_output, content_filter/refusal → refused. */
  function readChoice(data: OaiResponse): { text: string; model: string; usage?: ProviderResult<unknown>["usage"] } {
    const choice = data.choices?.[0];
    if (!choice) throw new ProviderError("bad_output", "no choices in response");
    const finish = choice.finish_reason ?? undefined;
    if (finish === "content_filter" || (typeof choice.message?.refusal === "string" && choice.message.refusal)) {
      throw new ProviderError("refused", choice.message?.refusal || "content_filter");
    }
    if (finish === "length") throw new ProviderError("bad_output", "response truncated (finish_reason=length)");
    const usage =
      data.usage && typeof data.usage.prompt_tokens === "number" && typeof data.usage.completion_tokens === "number"
        ? { inputTokens: data.usage.prompt_tokens, outputTokens: data.usage.completion_tokens }
        : undefined;
    return { text: contentText(choice.message), model: data.model || config.model, usage };
  }

  return {
    id: "openai",
    model: config.model,

    async generateText(input: GenerateTextInput): Promise<ProviderResult<string>> {
      const messages: OaiMessage[] = [{ role: "system", content: input.system }, ...input.messages];
      const result = await call({ model: config.model, max_tokens: input.maxTokens, messages }, input.timeoutMs);
      if (result.status === 400) throw new ProviderError("upstream", result.text.slice(0, 500), 400);
      const choice = readChoice(result.data!);
      return { value: choice.text.trim(), model: choice.model, usage: choice.usage };
    },

    async generateJson<T>(input: GenerateJsonInput<T>): Promise<ProviderResult<T>> {
      const jsonSchema = toStrictJsonSchema(input.schema);
      const userContent: OaiContentPart[] = [];
      if (input.image) userContent.push({ type: "image_url", image_url: { url: dataUrl(input.image), detail: "high" } });
      userContent.push({ type: "text", text: input.userText });

      let mode: JsonMode = { type: "json_schema", name: input.schemaName, schema: jsonSchema };

      const buildBody = (history: OaiMessage[]): Record<string, unknown> => {
        const system = mode.type === "json_schema" ? input.system : `${input.system}\n\n${schemaInstruction(jsonSchema)}`;
        const response_format =
          mode.type === "json_schema"
            ? { type: "json_schema", json_schema: { name: mode.name, schema: mode.schema, strict: true } }
            : { type: "json_object" };
        return {
          model: config.model,
          max_tokens: input.maxTokens,
          messages: [{ role: "system", content: system }, { role: "user", content: userContent }, ...history],
          response_format,
        };
      };

      /** Вызов с автоматическим откатом json_schema → json_object при 400 про response_format. */
      const request = async (history: OaiMessage[]): Promise<OaiResponse> => {
        let result = await call(buildBody(history), input.timeoutMs);
        if (result.status === 400 && mode.type === "json_schema" && mentionsResponseFormat(result.text)) {
          mode = { type: "json_object" };
          result = await call(buildBody(history), input.timeoutMs);
        }
        if (result.status === 400) throw new ProviderError("upstream", result.text.slice(0, 500), 400);
        return result.data!;
      };

      const history: OaiMessage[] = [];
      let lastModel = config.model;
      let lastUsage: ProviderResult<unknown>["usage"];
      let lastError = "";
      for (let attempt = 0; attempt < 2; attempt++) {
        const choice = readChoice(await request(history));
        lastModel = choice.model;
        lastUsage = choice.usage;
        let parsed: unknown;
        try {
          parsed = JSON.parse(extractJson(choice.text));
        } catch (error) {
          lastError = `не валидный JSON: ${error instanceof Error ? error.message : String(error)}`;
          parsed = undefined;
        }
        if (parsed !== undefined) {
          const checked = input.schema.safeParse(parsed);
          if (checked.success) return { value: checked.data, model: lastModel, usage: lastUsage };
          lastError = `не по схеме: ${checked.error.issues
            .slice(0, 5)
            .map((i) => `${i.path.join(".") || "<root>"}: ${i.message}`)
            .join("; ")}`;
        }
        history.length = 0;
        history.push(
          { role: "assistant", content: choice.text },
          { role: "user", content: `Предыдущий ответ не прошёл проверку (${lastError}). Верни исправленный ответ — только JSON по схеме, без пояснений.` },
        );
      }
      throw new ProviderError("bad_output", `model output failed validation: ${lastError}`);
    },
  };
}
