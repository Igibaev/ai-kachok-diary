# AI-прокси для FitCoach AI (Cloudflare Worker)

Ключ Anthropic хранится **у клуба**, а не в приложении. Приложение обращается к этому прокси
с токеном приложения; прокси проверяет токен, лимиты и вызывает Claude.

```
Телефон клиента ──(X-App-Token, история чата)──▶ Worker ──(ANTHROPIC_API_KEY)──▶ Anthropic API
```

Бесплатного тарифа Cloudflare Workers (100 000 запросов/день) хватает клубу любого размера.
Стоимость самих AI-запросов: при `claude-opus-5` и эффорте `low` типичный вопрос
«что поесть после тренировки» ≈ 1 500 входных + 250 выходных токенов ≈ $0.014.
Клуб на 1 000 активных участников при 5 вопросах в неделю на человека ≈ $280/мес. Дневные
лимиты на устройство и общий (см. ниже) не дают выйти за бюджет. Модель можно переключить
на `claude-sonnet-5` (в ~2.5 раза дешевле) одной переменной.

## Деплой за 10 минут

1. Установите Node.js 20+ и зайдите в аккаунт Cloudflare (бесплатный).
2. В этой папке:
   ```bash
   npm install
   npx wrangler login
   npx wrangler kv namespace create RATE_LIMIT_KV      # скопируйте id в wrangler.toml (раскомментируйте блок)
   npx wrangler secret put ANTHROPIC_API_KEY            # ключ из console.anthropic.com
   npx wrangler secret put APP_TOKEN                    # длинная случайная строка, например: openssl rand -hex 24
   npx wrangler deploy
   ```
3. Скопируйте URL вида `https://fitcoach-ai-proxy.<account>.workers.dev` и токен в
   `brands/<клиент>.properties`:
   ```properties
   aiProxyUrl=https://fitcoach-ai-proxy.<account>.workers.dev
   aiProxyToken=<APP_TOKEN>
   ```
4. Пересоберите приложение. Всё.

Проверка: `curl https://<url>/health` → `{"ok":true,"model":"claude-opus-5"}`.

## Переменные (`wrangler.toml` → `[vars]`)

| Переменная | По умолчанию | Смысл |
|---|---|---|
| `MODEL` | `claude-opus-5` | Модель Claude. Дешевле: `claude-sonnet-5`. |
| `DAILY_LIMIT_PER_DEVICE` | `40` | Вопросов в сутки с одного устройства. |
| `DAILY_LIMIT_GLOBAL` | `5000` | Вопросов в сутки на весь клуб (страховка бюджета). |

Секреты: `ANTHROPIC_API_KEY`, `APP_TOKEN` — только через `wrangler secret put`.

## Контракт API

`POST /v1/chat`, заголовки `X-App-Token`, `X-Device-Id` (необязательно), тело:

```json
{
  "system": "…системный промпт из приложения…",
  "messages": [{ "role": "user", "content": "Что поесть после тренировки?" }],
  "locale": "ru"
}
```

Ответ `200`: `{ "text": "…", "model": "claude-opus-5", "usage": { "input_tokens": 1500, "output_tokens": 240 } }`.
Ошибки: `401` неверный токен, `429` лимит (в `text` — готовая фраза для показа пользователю), `502/504` проблема выше по цепочке.

## Безопасность

- Ключ Anthropic никогда не покидает Cloudflare.
- Токен приложения можно перевыпустить (`wrangler secret put APP_TOKEN` + пересборка).
- История чата хранится только на телефоне; прокси ничего не логирует.
- При отказе классификаторов безопасности Anthropic запрос автоматически переигрывается на
  рекомендованной модели (`fallbacks: "default"`), пользователь получает ответ, а не ошибку.

## Локальный запуск

```bash
cp .dev.vars.example .dev.vars   # заполните ключи
npm run dev                       # http://localhost:8787
```

В приложении для локальной отладки укажите `aiProxyUrl=http://10.0.2.2:8787` (эмулятор).
