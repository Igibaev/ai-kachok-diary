# AI-прокси для FitCoach AI (Cloudflare Worker)

Ключ Anthropic хранится **у клуба**, а не в приложении. Приложение обращается к этому прокси
с токеном приложения; прокси проверяет токен, лимиты и вызывает Claude.

```
Телефон клиента ──(X-App-Token, история чата / цели питания / фото еды)──▶ Worker ──(ANTHROPIC_API_KEY)──▶ Anthropic API
```

Три функции за одним прокси: **AI-тренер** (`/v1/chat`), **AI-повар — план питания + список покупок**
(`/v1/meal-plan`) и **AI-повар — фото еды → КБЖУ** (`/v1/food-photo`, Claude vision).

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
   npx wrangler kv namespace create RATE_LIMIT_KV      # ОБЯЗАТЕЛЬНО: вставьте id в wrangler.toml (без KV прокси отвечает 503)
   npx wrangler secret put ANTHROPIC_API_KEY            # ключ из console.anthropic.com
   npx wrangler secret put APP_TOKEN                    # длинная случайная строка, например: openssl rand -hex 24
   npx wrangler deploy
   ```
3. Скопируйте URL вида `https://fitcoach-ai-proxy.<account>.workers.dev` в
   `brands/<клиент>.properties`, а токен — в переменную окружения сборки (НЕ в git):
   ```properties
   aiProxyUrl=https://fitcoach-ai-proxy.<account>.workers.dev
   # aiProxyToken оставьте пустым — см. ниже
   ```
   ```bash
   export AI_PROXY_TOKEN_<КЛИЕНТ_В_ВЕРХНЕМ_РЕГИСТРЕ>=<APP_TOKEN>   # например AI_PROXY_TOKEN_DEMO
   ```
   Альтернатива — gitignored-файл `brands/<клиент>.secrets.properties` с строкой `aiProxyToken=<APP_TOKEN>`.
   Значение из окружения/secrets-файла имеет приоритет над `brands/<клиент>.properties`.
4. Пересоберите приложение. Всё.

Проверка: `curl https://<url>/health` → `{"ok":true,"model":"claude-opus-5","features":["chat","meal-plan","food-photo"]}`.

## Переменные (`wrangler.toml` → `[vars]`)

| Переменная | По умолчанию | Смысл |
|---|---|---|
| `MODEL` | `claude-opus-5` | Модель Claude. Дешевле: `claude-sonnet-5`. |
| `DAILY_LIMIT_PER_DEVICE` | `40` | Вопросов в сутки с одного устройства. |
| `DAILY_LIMIT_GLOBAL` | `5000` | Запросов в сутки на весь клуб — общий счётчик для чата, планов и фото (страховка бюджета). |
| `DAILY_LIMIT_PER_IP` | `200` | Запросов в сутки с одного IP (все эндпоинты), защита от смены `X-Device-Id`. |
| `DAILY_LIMIT_PLANS_PER_DEVICE` | `3` | Планов питания в сутки с одного устройства (ключ KV `rlp:<день>:<deviceId>`). |
| `DAILY_LIMIT_PHOTOS_PER_DEVICE` | `20` | Разборов фото еды в сутки с одного устройства (ключ KV `rlf:<день>:<deviceId>`). |

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
Ошибки (общие для всех эндпоинтов): `401` неверный токен, `400` некорректный `X-Device-Id` (разрешено `[A-Za-z0-9_-]{1,64}`) или тело,
`413/415` тело больше лимита (чат 64 КБ, план 32 КБ, фото 2 МБ) или не JSON,
`429` лимит (в `text` — готовая фраза для показа пользователю на языке `locale`), `503 rate_limit_not_configured` — не привязан KV,
`502/504` проблема выше по цепочке, `422 refused` — модель отказалась (в `text` — объяснение для пользователя).
Все ошибки — JSON `{ "error": "<код>", "text": "<фраза для пользователя>" }` (`text` может отсутствовать у технических кодов 401/404/413/415).

### AI-повар: `POST /v1/meal-plan`

План питания на 3/5/7 дней по целям профиля. Заголовки те же (`X-App-Token`, `X-Device-Id`). Тело:

```json
{
  "locale": "ru",
  "days": 7,
  "mealsPerDay": 4,
  "goals": { "calories": 1850, "proteinG": 140, "carbsG": 190, "fatG": 60 },
  "profile": { "sex": "male", "age": 31, "weightKg": 82, "goal": "похудение", "restrictions": ["спина"] },
  "prefs": { "cuisine": "kazakh", "exclusions": ["свинина", "орехи"], "halal": true, "budget": "mid", "batchCooking": false }
}
```

`cuisine`: `kazakh | home | any`; `budget`: `low | mid | any`. Ответ `200`:

```json
{
  "plan": {
    "days": [{ "day": 1, "meals": [{ "slot": "breakfast", "title": "…", "timeMinutes": 15,
               "ingredients": [{ "name": "овсянка", "grams": 60, "category": "grains" }],
               "calories": 420, "proteinG": 25, "carbsG": 55, "fatG": 10, "steps": ["…", "…"] }],
               "totalCalories": 1840, "totalProteinG": 141, "totalCarbsG": 188, "totalFatG": 59 }],
    "shopping": [{ "category": "meat_fish", "items": [{ "name": "Куриная грудка", "quantity": 1.2, "unit": "kg" }] }],
    "notes": "…"
  },
  "model": "claude-opus-5",
  "usage": { "input_tokens": 2500, "output_tokens": 7000 }
}
```

- `slot`: `breakfast | lunch | dinner | snack`; `category` ингредиента и отдела списка покупок:
  `meat_fish | dairy | grains | produce | other` (приложение локализует названия отделов).
- Ответ модели — структурированный (`messages.parse` + zod-схема), поэтому JSON всегда валиден.
  **Список покупок собирает сервер** из ингредиентов всех дней: одинаковые названия объединяются без учёта
  регистра, граммы суммируются, > 1000 г → `kg` с шагом 0,1, иначе целые `g`; суточные итоги пересчитываются
  из приёмов. Это покрыто unit-тестами (`npm test`).
- Таймаут вызова Claude 180 с (7 дней × 5 приёмов — долгий ответ); клиенту стоит ждать до 200 с.
- Специфичные ошибки: `400 goals_required` (нет целей КБЖУ), `502 bad_model_output` (ответ обрезан по `max_tokens` или не прошёл
  схему — предложите меньше дней/приёмов).

### AI-повар: `POST /v1/food-photo`

Фото еды → распознанные блюда с граммами и КБЖУ. Тело (приложение само уменьшает фото до 1024 px JPEG q80):

```json
{ "locale": "ru", "imageBase64": "<base64>", "mediaType": "image/jpeg", "hint": "бешбармак, порция средняя" }
```

`mediaType`: `image/jpeg | image/png | image/webp`; изображение ≤ 1,5 МБ после декодирования (`413 image_too_large`),
`hint` ≤ 200 символов. Ответ `200`:

```json
{
  "analysis": {
    "items": [{ "name": "Бешбармак (мясо и тесто)", "grams": 350, "calories": 620, "proteinG": 38, "carbsG": 55, "fatG": 26, "confidence": "medium" }],
    "totalCalories": 620, "totalProteinG": 38, "totalCarbsG": 55, "totalFatG": 26,
    "note": "Если с казы — добавь ~150 ккал.",
    "isFood": true
  },
  "model": "claude-opus-5",
  "usage": { "input_tokens": 1600, "output_tokens": 400 }
}
```

Если на фото не еда — `isFood: false`, `items: []`, `note` объясняет, что сфотографировать. Итоги пересчитываются
сервером из позиций. Фото не сохраняется и не логируется: оно уходит в Anthropic в теле запроса и больше нигде не живёт.
Ошибки: `400 image_required | image_not_base64 | unsupported_image_type`, `413 image_too_large`, `422 refused`,
`502 bad_model_output` (ответ модели неполный или не по схеме — предложите повторить).

### Стоимость AI-повара (ориентир)

| Запрос | Токены | `claude-opus-5` | `claude-sonnet-5` |
|---|---|---|---|
| План на 7 дней | ≈ 2 500 вх + 7 000 вых | ≈ $0.19 | ≈ $0.075 |
| Фото еды | ≈ 1 600 вх + 400 вых | ≈ $0.018 | ≈ $0.007 |

При лимитах по умолчанию (3 плана + 20 фото в сутки на устройство) максимум на одного активного участника ≈ $0.93/день
на Opus 5; реальный расход в разы меньше (план составляют раз в неделю). Общий `DAILY_LIMIT_GLOBAL` считает чат,
планы и фото вместе — это потолок бюджета клуба в запросах.

## Безопасность

- Ключ Anthropic никогда не покидает Cloudflare.
- Токен приложения — это клиентский секрет ограниченной силы: он извлекается из APK, поэтому бюджет
  защищают лимиты (на устройство, на IP, общий), а не токен. Лимиты обязательны (fail-closed без KV).
- Ротация токена: `wrangler secret put APP_TOKEN`, новый токен в `AI_PROXY_TOKEN_<КЛИЕНТ>`, пересборка и
  выкладка обновления; старые сборки получат `401` и перейдут в демо-режим.
- Мониторинг расходов: Cloudflare Dashboard → Workers → Metrics (число запросов) и console.anthropic.com → Usage;
  при аномалии уменьшите `DAILY_LIMIT_GLOBAL` и ротируйте токен.
- История чата хранится только на телефоне; прокси ничего не логирует. Фото еды проходит через прокси в Anthropic
  и не сохраняется ни в Cloudflare, ни в KV (в KV — только счётчики лимитов).
- При отказе классификаторов безопасности Anthropic запрос автоматически переигрывается на
  рекомендованной модели (`fallbacks: "default"`), пользователь получает ответ, а не ошибку.

## Проверка кода

```bash
npm run typecheck   # tsc --noEmit
npm test            # node:test — агрегация списка покупок, валидация запросов, нормализация ответов (без сети)
```

Тесты выполняются Node 22.18+ напрямую из `.ts` (type stripping), сетевых вызовов к Anthropic нет.

## Локальный запуск

```bash
cp .dev.vars.example .dev.vars   # заполните ключи
npm run dev                       # http://localhost:8787
```

В приложении для локальной отладки укажите `aiProxyUrl=http://10.0.2.2:8787` (эмулятор) — cleartext
разрешён только в debug-сборках для `10.0.2.2`/`localhost` (`res/xml/network_security_config*.xml`); release-сборка
требует `https://` для `aiProxyUrl`, `clubDataUrl` и `newsUrl`.
