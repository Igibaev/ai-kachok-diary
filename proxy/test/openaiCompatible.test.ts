/**
 * OpenAI-совместимый провайдер: все вызовы идут через подменённый globalThis.fetch, сети нет.
 */
import { test, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { z } from "zod";
import { createOpenAiCompatibleProvider, extractJson, toStrictJsonSchema } from "../src/providers/openaiCompatible.ts";
import { ProviderError } from "../src/providers/types.ts";
import { FoodAnalysisSchema, MealPlanModelSchema } from "../src/schemas.ts";

const Schema = z.object({ name: z.string(), grams: z.number(), tag: z.enum(["a", "b"]) });
type Out = z.infer<typeof Schema>;
const VALID: Out = { name: "Плов", grams: 300, tag: "a" };

interface Call {
  url: string;
  headers: Record<string, string>;
  body: Record<string, any>;
}

const calls: Call[] = [];
const realFetch = globalThis.fetch;

/** Подменяет fetch очередью ответов; каждый элемент — статус + тело (объект → JSON) или функция, бросающая ошибку. */
function mockFetch(responses: Array<{ status?: number; body: unknown } | (() => never)>) {
  let i = 0;
  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const headers: Record<string, string> = {};
    new Headers(init?.headers).forEach((v, k) => (headers[k] = v));
    calls.push({ url: String(input), headers, body: JSON.parse(String(init?.body)) });
    const next = responses[Math.min(i++, responses.length - 1)];
    if (typeof next === "function") next();
    const text = typeof next.body === "string" ? next.body : JSON.stringify(next.body);
    return new Response(text, { status: next.status ?? 200, headers: { "content-type": "application/json" } });
  }) as typeof fetch;
}

const completion = (content: string, extra: Record<string, unknown> = {}, finish = "stop") => ({
  model: "test-model-001",
  choices: [{ finish_reason: finish, message: { role: "assistant", content } }],
  usage: { prompt_tokens: 120, completion_tokens: 40 },
  ...extra,
});

const provider = () =>
  createOpenAiCompatibleProvider({ apiKey: "sk-test", model: "gpt-test", baseUrl: "https://llm.example/v1/", extraHeaders: { "HTTP-Referer": "https://fitcoach.kz", "X-Title": "FitCoach" } });

const jsonInput = (image?: { mediaType: "image/jpeg" | "image/png" | "image/webp"; base64: string }) => ({
  system: "Ты диетолог.",
  userText: "Что на фото?",
  image,
  schema: Schema,
  schemaName: "dish",
  maxTokens: 500,
  timeoutMs: 5_000,
});

beforeEach(() => {
  calls.length = 0;
});
afterEach(() => {
  globalThis.fetch = realFetch;
});

test("(1) json_schema: strict response_format, валидный объект, usage и модель из ответа", async () => {
  mockFetch([{ body: completion(JSON.stringify(VALID)) }]);
  const result = await provider().generateJson(jsonInput());
  assert.deepEqual(result.value, VALID);
  assert.equal(result.model, "test-model-001");
  assert.deepEqual(result.usage, { inputTokens: 120, outputTokens: 40 });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, "https://llm.example/v1/chat/completions");
  const rf = calls[0].body.response_format;
  assert.equal(rf.type, "json_schema");
  assert.equal(rf.json_schema.name, "dish");
  assert.equal(rf.json_schema.strict, true);
  assert.equal(rf.json_schema.schema.additionalProperties, false);
  assert.deepEqual(rf.json_schema.schema.required, ["name", "grams", "tag"]);
  assert.equal(rf.json_schema.schema.$schema, undefined);
  assert.equal(calls[0].body.messages[0].role, "system");
  assert.equal(calls[0].body.messages[0].content, "Ты диетолог.");
  assert.equal(calls[0].body.max_tokens, 500);
});

test("(2) 400 на json_schema → fallback json_object со схемой в системном промпте, парсинг ```json-ограждения", async () => {
  mockFetch([
    { status: 400, body: { error: { message: "Invalid parameter: 'response_format' of type 'json_schema' is not supported" } } },
    { body: completion("Вот ответ:\n```json\n" + JSON.stringify(VALID) + "\n```") },
  ]);
  const result = await provider().generateJson(jsonInput());
  assert.deepEqual(result.value, VALID);
  assert.equal(calls.length, 2);
  assert.deepEqual(calls[1].body.response_format, { type: "json_object" });
  const system: string = calls[1].body.messages[0].content;
  assert.ok(system.startsWith("Ты диетолог."));
  assert.ok(system.includes("JSON Schema"));
  assert.ok(system.includes('"enum":["a","b"]'), "описание схемы генерируется из JSON Schema");
});

test("(3) невалидный ответ → один повтор с текстом ошибки → bad_output", async () => {
  mockFetch([{ body: completion('{"name":"Плов","grams":"много","tag":"a"}') }, { body: completion("это не json") }]);
  await assert.rejects(provider().generateJson(jsonInput()), (e: unknown) => e instanceof ProviderError && e.kind === "bad_output");
  assert.equal(calls.length, 2);
  const retryMessages = calls[1].body.messages;
  assert.equal(retryMessages.length, 4);
  assert.equal(retryMessages[2].role, "assistant");
  assert.equal(retryMessages[3].role, "user");
  assert.ok(String(retryMessages[3].content).includes("grams"), "сообщение о валидации называет поле");
});

test("(3b) невалидный первый ответ, валидный повтор → успех", async () => {
  mockFetch([{ body: completion("{broken") }, { body: completion(JSON.stringify(VALID)) }]);
  const result = await provider().generateJson(jsonInput());
  assert.deepEqual(result.value, VALID);
  assert.equal(calls.length, 2);
});

test("(4) HTTP и finish_reason → kind: 401→auth, 403→auth, 429→rate_limit, 5xx→upstream, content_filter→refused", async () => {
  const kindOf = async (responses: Parameters<typeof mockFetch>[0]) => {
    mockFetch(responses);
    try {
      await provider().generateText({ system: "s", messages: [{ role: "user", content: "hi" }], maxTokens: 100, timeoutMs: 5_000 });
      return "ok";
    } catch (e) {
      return e instanceof ProviderError ? `${e.kind}:${e.status ?? ""}` : "other";
    }
  };
  assert.equal(await kindOf([{ status: 401, body: { error: "bad key" } }]), "auth:401");
  assert.equal(await kindOf([{ status: 403, body: { error: "forbidden" } }]), "auth:403");
  assert.equal(await kindOf([{ status: 429, body: { error: "slow down" } }]), "rate_limit:429");
  assert.equal(await kindOf([{ status: 503, body: "overloaded" }]), "upstream:503");
  assert.equal(await kindOf([{ status: 504, body: "gateway timeout" }]), "timeout:504");
  assert.equal(await kindOf([{ status: 400, body: { error: { message: "model not found" } } }]), "upstream:400");
  assert.equal(await kindOf([{ body: completion("", {}, "content_filter") }]), "refused:");
  assert.equal(await kindOf([{ body: { model: "m", choices: [{ finish_reason: "stop", message: { refusal: "I can't help with that" } }] } }]), "refused:");
  assert.equal(await kindOf([{ body: { model: "m", choices: [] } }]), "bad_output:");
  assert.equal(
    await kindOf([
      () => {
        throw new TypeError("fetch failed");
      },
    ]),
    "connection:",
  );
});

test("(4a) finish_reason=length: для JSON → bad_output (обрезанный JSON бесполезен), для текста чата → частичный текст, как у Anthropic", async () => {
  mockFetch([{ body: completion("{\"name\":", {}, "length") }]);
  await assert.rejects(provider().generateJson(jsonInput()), (e: unknown) => e instanceof ProviderError && e.kind === "bad_output");
  mockFetch([{ body: completion("Начало длинного ответа…", {}, "length") }]);
  const result = await provider().generateText({ system: "s", messages: [{ role: "user", content: "hi" }], maxTokens: 10, timeoutMs: 5_000 });
  assert.equal(result.value, "Начало длинного ответа…");
});

test("(4c) 400 «use max_completion_tokens» → один повтор с max_completion_tokens вместо max_tokens (gpt-5 / o-серия)", async () => {
  mockFetch([
    { status: 400, body: { error: { message: "Unsupported parameter: 'max_tokens' is not supported with this model. Use 'max_completion_tokens' instead.", param: "max_tokens" } } },
    { body: completion("ok") },
  ]);
  const result = await provider().generateText({ system: "s", messages: [{ role: "user", content: "hi" }], maxTokens: 77, timeoutMs: 5_000 });
  assert.equal(result.value, "ok");
  assert.equal(calls.length, 2);
  assert.equal(calls[0].body.max_tokens, 77);
  assert.equal(calls[1].body.max_tokens, undefined);
  assert.equal(calls[1].body.max_completion_tokens, 77);
  // Прочие 400 не повторяются.
  mockFetch([{ status: 400, body: { error: { message: "model not found" } } }, { body: completion("never") }]);
  await assert.rejects(provider().generateText({ system: "s", messages: [{ role: "user", content: "hi" }], maxTokens: 1, timeoutMs: 5_000 }));
  assert.equal(calls.length, 3);
});

test("(4d) таймаут покрывает и чтение тела: заголовки пришли, тело зависло → timeout, а не вечное ожидание", async () => {
  globalThis.fetch = (async () => new Response(new ReadableStream<Uint8Array>({ start() {} }), { status: 200 })) as typeof fetch;
  await assert.rejects(
    provider().generateText({ system: "s", messages: [{ role: "user", content: "hi" }], maxTokens: 10, timeoutMs: 30 }),
    (e: unknown) => e instanceof ProviderError && e.kind === "timeout",
  );
});

test("(4b) таймаут через AbortController → timeout", async () => {
  globalThis.fetch = ((_: RequestInfo | URL, init?: RequestInit) =>
    new Promise<Response>((_resolve, reject) => {
      init?.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")));
    })) as typeof fetch;
  await assert.rejects(
    provider().generateText({ system: "s", messages: [{ role: "user", content: "hi" }], maxTokens: 10, timeoutMs: 20 }),
    (e: unknown) => e instanceof ProviderError && e.kind === "timeout",
  );
});

test("(5) изображение → image_url data-URL с media type и detail high, текст после картинки", async () => {
  mockFetch([{ body: completion(JSON.stringify(VALID)) }]);
  await provider().generateJson(jsonInput({ mediaType: "image/webp", base64: "UklGRgAA" }));
  const user = calls[0].body.messages[1];
  assert.equal(user.role, "user");
  assert.deepEqual(user.content[0], { type: "image_url", image_url: { url: "data:image/webp;base64,UklGRgAA", detail: "high" } });
  assert.deepEqual(user.content[1], { type: "text", text: "Что на фото?" });
});

test("(6) extra headers и Authorization прокидываются; generateText собирает system+messages", async () => {
  mockFetch([{ body: completion("  Привет!  ") }]);
  const result = await provider().generateText({
    system: "Ты тренер.",
    messages: [
      { role: "user", content: "Привет" },
      { role: "assistant", content: "Здравствуй" },
      { role: "user", content: "Что поесть?" },
    ],
    maxTokens: 2048,
    timeoutMs: 5_000,
  });
  assert.equal(result.value, "Привет!");
  assert.equal(calls[0].headers["authorization"], "Bearer sk-test");
  assert.equal(calls[0].headers["http-referer"], "https://fitcoach.kz");
  assert.equal(calls[0].headers["x-title"], "FitCoach");
  assert.equal(calls[0].headers["content-type"], "application/json");
  assert.deepEqual(
    calls[0].body.messages.map((m: { role: string }) => m.role),
    ["system", "user", "assistant", "user"],
  );
  assert.equal(calls[0].body.response_format, undefined);
});

test("toStrictJsonSchema: схемы повара совместимы со strict-режимом (все поля required, additionalProperties:false)", () => {
  const check = (schema: Record<string, unknown>) => {
    const walk = (node: unknown) => {
      if (!node || typeof node !== "object") return;
      const n = node as Record<string, unknown>;
      if (n.type === "object") {
        assert.equal(n.additionalProperties, false);
        assert.deepEqual((n.required as string[]).sort(), Object.keys(n.properties as object).sort());
      }
      for (const v of Object.values(n)) walk(v);
    };
    walk(schema);
    assert.equal(schema.$schema, undefined);
  };
  check(toStrictJsonSchema(MealPlanModelSchema));
  check(toStrictJsonSchema(FoodAnalysisSchema));
});

test("extractJson снимает ограждения и текст вокруг объекта", () => {
  assert.equal(extractJson('```json\n{"a":1}\n```'), '{"a":1}');
  assert.equal(extractJson('Ответ: {"a":1} готово'), '{"a":1}');
  assert.equal(extractJson('{"a":1}'), '{"a":1}');
});
