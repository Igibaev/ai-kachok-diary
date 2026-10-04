/**
 * resolveProvider: выбор нейросети переменными окружения (без сети — провайдер только создаётся).
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import { describeProviders, ProviderNotConfiguredError, resolveProvider, resolveProviderConfig } from "../src/providers/index.ts";
import type { ProviderEnv } from "../src/providers/index.ts";

test("по умолчанию — anthropic/claude-opus-5 для чата и повара, ключ ANTHROPIC_API_KEY", () => {
  const env: ProviderEnv = { ANTHROPIC_API_KEY: "sk-ant-x" };
  const chat = resolveProvider(env, "chat");
  const chef = resolveProvider(env, "chef");
  assert.equal(chat.id, "anthropic");
  assert.equal(chat.model, "claude-opus-5");
  assert.equal(chef.id, "anthropic");
  assert.equal(chef.model, "claude-opus-5");
});

test("устаревшая MODEL работает как AI_MODEL; AI_MODEL приоритетнее", () => {
  assert.equal(resolveProvider({ ANTHROPIC_API_KEY: "k", MODEL: "claude-sonnet-5" }, "chat").model, "claude-sonnet-5");
  assert.equal(resolveProvider({ ANTHROPIC_API_KEY: "k", MODEL: "claude-sonnet-5", AI_MODEL: "claude-opus-5" }, "chef").model, "claude-opus-5");
});

test("AI_PROVIDER=openai: ключ AI_API_KEY, base URL по умолчанию, модель-пример gpt-4o", () => {
  const cfg = resolveProviderConfig({ AI_PROVIDER: "openai", AI_API_KEY: "sk-openai" }, "chat");
  assert.deepEqual(cfg, { id: "openai", model: "gpt-4o", baseUrl: "https://api.openai.com/v1", apiKey: "sk-openai", extraHeaders: undefined });
  const provider = resolveProvider({ AI_PROVIDER: "OpenAI", AI_API_KEY: "sk-openai", AI_MODEL: "gpt-4.1-mini", AI_BASE_URL: "https://openrouter.ai/api/v1/" }, "chef");
  assert.equal(provider.id, "openai");
  assert.equal(provider.model, "gpt-4.1-mini");
});

test("CHEF_* переопределяет повара, чат остаётся на общих настройках", () => {
  const env: ProviderEnv = {
    ANTHROPIC_API_KEY: "sk-ant",
    AI_MODEL: "claude-sonnet-5",
    CHEF_PROVIDER: "openai",
    CHEF_MODEL: "gemini-2.5-flash",
    CHEF_BASE_URL: "https://generativelanguage.googleapis.com/v1beta/openai",
    CHEF_API_KEY: "gemini-key",
    CHEF_EXTRA_HEADERS: '{"X-Title":"FitCoach"}',
  };
  const chat = resolveProviderConfig(env, "chat");
  assert.equal(chat.id, "anthropic");
  assert.equal(chat.model, "claude-sonnet-5");
  assert.equal(chat.apiKey, "sk-ant");
  const chef = resolveProviderConfig(env, "chef");
  assert.equal(chef.id, "openai");
  assert.equal(chef.model, "gemini-2.5-flash");
  assert.equal(chef.baseUrl, "https://generativelanguage.googleapis.com/v1beta/openai");
  assert.equal(chef.apiKey, "gemini-key");
  assert.deepEqual(chef.extraHeaders, { "X-Title": "FitCoach" });
});

test("повар на другом семействе API не наследует AI_MODEL/AI_BASE_URL/AI_API_KEY общего провайдера", () => {
  const env: ProviderEnv = { AI_PROVIDER: "openai", AI_MODEL: "gpt-4o", AI_BASE_URL: "https://x/v1", AI_API_KEY: "sk-openai", CHEF_PROVIDER: "anthropic", ANTHROPIC_API_KEY: "sk-ant" };
  const chef = resolveProviderConfig(env, "chef");
  assert.equal(chef.id, "anthropic");
  assert.equal(chef.model, "claude-opus-5");
  assert.equal(chef.apiKey, "sk-ant");
  assert.equal(chef.baseUrl, undefined);
});

test("повар на том же семействе наследует общие настройки, переопределяя только заданные CHEF_*", () => {
  const env: ProviderEnv = { AI_PROVIDER: "openai", AI_MODEL: "gpt-4o", AI_BASE_URL: "https://openrouter.ai/api/v1", AI_API_KEY: "sk-or", CHEF_MODEL: "google/gemini-2.5-flash" };
  const chef = resolveProviderConfig(env, "chef");
  assert.equal(chef.model, "google/gemini-2.5-flash");
  assert.equal(chef.baseUrl, "https://openrouter.ai/api/v1");
  assert.equal(chef.apiKey, "sk-or");
});

test("нет ключа выбранного провайдера → ProviderNotConfiguredError", () => {
  assert.throws(() => resolveProvider({}, "chat"), (e: unknown) => e instanceof ProviderNotConfiguredError && e.code === "provider_not_configured" && /ANTHROPIC_API_KEY/.test(e.message));
  assert.throws(() => resolveProvider({ AI_PROVIDER: "openai" }, "chef"), (e: unknown) => e instanceof ProviderNotConfiguredError && /AI_API_KEY/.test(e.message) && /CHEF_API_KEY/.test(e.message));
  // Ключ Anthropic не подходит для openai-провайдера.
  assert.throws(() => resolveProvider({ AI_PROVIDER: "openai", ANTHROPIC_API_KEY: "sk-ant" }, "chat"), ProviderNotConfiguredError);
});

test("неизвестный провайдер или битый JSON заголовков → ProviderNotConfiguredError", () => {
  assert.throws(() => resolveProvider({ AI_PROVIDER: "gemini", AI_API_KEY: "k" }, "chat"), /AI_PROVIDER/);
  assert.throws(() => resolveProvider({ ANTHROPIC_API_KEY: "k", CHEF_PROVIDER: "local" }, "chef"), /CHEF_PROVIDER/);
  assert.throws(() => resolveProvider({ AI_PROVIDER: "openai", AI_API_KEY: "k", AI_EXTRA_HEADERS: "not json" }, "chat"), /AI_EXTRA_HEADERS/);
  assert.throws(() => resolveProvider({ AI_PROVIDER: "openai", AI_API_KEY: "k", AI_EXTRA_HEADERS: '["a"]' }, "chat"), /AI_EXTRA_HEADERS/);
});

test("describeProviders: только id/model/configured, никогда не бросает", () => {
  const ok = describeProviders({ ANTHROPIC_API_KEY: "k", CHEF_PROVIDER: "openai", CHEF_API_KEY: "o", CHEF_BASE_URL: "https://secret.internal/v1" });
  assert.deepEqual(ok, {
    chat: { id: "anthropic", model: "claude-opus-5", configured: true },
    chef: { id: "openai", model: "gpt-4o", configured: true },
  });
  assert.ok(!JSON.stringify(ok).includes("secret.internal"));
  const noKey = describeProviders({});
  assert.equal(noKey.chat.configured, false);
  assert.equal(noKey.chat.id, "anthropic");
  const broken = describeProviders({ AI_PROVIDER: "???" });
  assert.deepEqual(broken.chat, { id: "misconfigured", model: "", configured: false });
});
