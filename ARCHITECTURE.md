# Архитектура

Один Android-модуль `app`, слои по пакетам. Никакого собственного бэкенда, кроме AI-прокси (`proxy/`).

```
com.fitcoach.app
├── brand/            BrandConfig — все настройки клиента из BuildConfig (brands/*.properties → flavor)
├── domain/
│   ├── model/        UserProfile (пол, цель, уровень, ограничения, программа, цели КБЖУ/вода, язык), Workout, ExerciseSet, Nutrition* (NutritionEntry.source: manual|photo|plan), Water*, ChatMessage, MealPlan, ShoppingItem, FoodAnalysis
│   ├── program/      ProgramCatalog (3 программы × 12 недель × 3 фазы), nextWorkout (гибкий график), StreakCalculator, Achievements
│   ├── usecase/      GoalCalculator (Mifflin-St Jeor), ai/BuildSystemPromptUseCase
│   ├── repository/   интерфейсы репозиториев (…, MealPlanRepository: активный план, список покупок, отметки «куплено»)
│   └── service/      ShareService (карточки Stories), DemoDataSeeder (демо-данные)
├── data/
│   ├── local/db/     Room v4: workouts, exercise_sets, nutrition_entries (+TEMPLATE-продукты, колонка source), water_entries, chat_messages, user_profile, body_measurements, meal_plans, shopping_items
│   ├── repository/   реализации (маппинг entity ↔ domain)
│   ├── club/         ClubRepository: assets/club/club.json → кэш → удалённый JSON (clubDataUrl), ClubNewsWorker (локальный push о новой акции)
│   └── remote/       Retrofit-клиент Anthropic (только debug-режим «свой ключ»)
├── ai/               AiClient: ProxyAiClient (Cloudflare Worker) · DirectAnthropicClient (debug) · DemoAiClient (офлайн RU/KK) · AiClientSelector
│   └── chef/         AiChefClient: ProxyAiChefClient (/v1/meal-plan, /v1/food-photo) · DemoAiChefClient (офлайн план RU/KK + фиксированный разбор фото) · AiChefSelector (только Proxy/Demo) · ChefDtos
├── share/            ShareServiceImpl — Canvas 1080×1920, FileProvider, Instagram Stories intent
├── demo/             DemoDataSeederImpl — 3 недели истории для презентаций
├── workers/          WaterReminderWorker, WorkoutReminderWorker, ShoppingReminderWorker (+ ShoppingReminderScheduler.nextTrigger), Notifications (каналы, иконка бренда, extra `nav_route` → экран)
├── di/               Hilt-модули (AppModule: Room/OkHttp/Retrofit; RepositoryModule; ServiceModule; AiModule; ClubModule)
└── presentation/
    ├── navigation/   Screen (маршруты), AppNavHost
    ├── theme/        FitCoachColors (брендовые цвета из BrandConfig + семантические), Typography, Theme
    ├── components/   FitCard, LabeledProgressBar, CircularProgress, SetButton…
    └── screens/      onboarding, dashboard, workout/{active,history,detail}, programs, nutrition (+ карточка «AI-повар»), meal_plan, shopping_list, food_photo, water, progress, chat, club, settings
```

## Ключевые решения

- **White-label через product flavors.** `app/build.gradle.kts` читает `brands/*.properties` и создаёт flavor на каждый файл: `applicationId`, имя, цвета и контакты попадают в `BuildConfig`/`resValue`. Сборка падает, если контраст акцента к фону < 3.0. Ресурсы клиента (`app/src/<brand>/res`, `assets`) переопределяют стандартные.
- **Локальные данные.** Всё хранится в Room на устройстве; аккаунтов нет. Это упрощает закон о персональных данных РК и убирает стоимость бэкенда. Синхронизация — опцион фазы 2.
- **AI без ключа в приложении.** Приложение → Worker (токен приложения, лимит на устройство/день, общий лимит) → провайдер модели по выбору клуба (Anthropic по умолчанию или любой OpenAI-совместимый API). Системный промпт строится на устройстве и не содержит имени/телефона. Отказ модели (`ProviderError` вида `refused`: у Anthropic — `stop_reason: refusal`, у OpenAI-совместимых — `finish_reason: content_filter`) заменяется безопасной фразой; у Anthropic при отказе классификатора включён server-side fallback.
- **Провайдер-независимый AI-слой (с 1.1.1, `proxy/src/providers/`).** Эндпоинты работают только с интерфейсом `AiProvider` (`generateText` для чата, `generateJson<T>` с zod-схемой для повара); реализации — `anthropic.ts` (`@anthropic-ai/sdk`) и `openaiCompatible.ts` (`fetch` на `${baseUrl}/chat/completions`, без SDK: OpenAI, Google Gemini, DeepSeek, Mistral, Groq, OpenRouter, xAI, локальные Ollama/vLLM/LM Studio). `resolveProvider(env, "chat" | "chef")` выбирает реализацию по переменным (раздел «AI-прокси: слой провайдеров»); повар может работать на другой модели, чем чат. Ошибки провайдеров приводятся к единому `ProviderError` (`auth`, `rate_limit`, `refused`, `bad_output`, `timeout`, `connection`, `upstream`) → один `mapUpstreamError` → `{error, text}` на языке пользователя. Приложение о провайдере не знает: контракт `/v1/*` не меняется, ключи — только на прокси. Требование к модели повара — понимает изображения и отдаёт JSON; точность оценки по фото зависит от модели.
- **AI-повар — структурированный вывод, не свободный текст.** `/v1/meal-plan` и `/v1/food-photo` на прокси вызывают `provider.generateJson` с zod-схемами (`MealPlan`, `FoodAnalysis`): у Anthropic это `messages.parse` + structured outputs, у OpenAI-совместимых — `response_format: json_schema` (JSON Schema из zod через `z.toJSONSchema`) с откатом на `json_object` + описание схемы в системном промпте, проверка `safeParse` и один повтор при невалидном ответе; поэтому приложение получает валидный JSON, а не парсит текст. Список покупок прокси пересчитывает сам: объединяет одинаковые позиции без учёта регистра, граммы > 1 000 → кг. Отказ модели (`ProviderError` вида `refused`) → `422 refused` с текстом для пользователя; «не еда» → `isFood=false` и объяснение в `note`. Отдельные дневные лимиты на устройство: планы (KV `rlp:`, по умолчанию 3) и фото (`rlf:`, 20), тело запроса ≤ 2 МБ. Фото уменьшается на устройстве (1024 px по длинной стороне, JPEG q80, ≤ 1,5 МБ, EXIF-поворот), файл удаляется после анализа, прокси изображение не хранит. В приложении AI-повар работает только через прокси или демо (`AiChefSelector`), режима прямого ключа нет; план и список хранятся в Room (`meal_plans`, `shopping_items`), записи дневника из плана/фото помечены `source`.
- **Напоминание о покупках.** `ShoppingReminderWorker` — OneTime-задача WorkManager с точной задержкой до ближайшего выбранного дня недели и времени (`nextTrigger` — чистая функция с тестами границ); по срабатыванию перепланируется на следующую неделю, молчит при пустом списке, тап по уведомлению открывает `shopping_list` через extra `nav_route`.
- **Гибкий график программ.** Следующая тренировка определяется числом выполненных тренировок в программе, а не днём недели: начать можно в любой день, демо не показывает «день отдыха».
- **Ограничения по здоровью.** `Restriction` влияет на подбор программы, автозамену упражнений (`avoidFor` → альтернатива), вопрос о дискомфорте после тренировки (только при «спина») и системный промпт AI.
- **Контент клуба без разработчика.** `club.json` (тренеры, услуги, расписание, акции, хэштеги, промокод) грузится из assets, затем из `clubDataUrl` (Google Sheets → Apps Script → JSON), кэшируется; новая акция → локальное уведомление.
- **Демо-надёжность.** `DemoAiClient` (офлайн-сценарии RU/KK), `DemoAiChefClient` (готовый 3-дневный план с казахской кухней и фиксированный разбор фото с пометкой «демо-оценка») и `DemoDataSeeder` (3 недели истории + демо-план и список покупок с отмеченными позициями) — чтобы презентация никогда не выглядела пустой или сломанной.

## AI-прокси: слой провайдеров

```
Приложение ──X-App-Token, X-Device-Id──▶ Worker (лимиты KV, валидация, zod) ──▶ AiProvider — провайдер по выбору
                                                                                 ├─ anthropic.ts         AI_PROVIDER=anthropic (по умолчанию) · @anthropic-ai/sdk · ANTHROPIC_API_KEY
                                                                                 └─ openaiCompatible.ts  AI_PROVIDER=openai · fetch ${AI_BASE_URL}/chat/completions · AI_API_KEY
                                                                                    OpenAI · Google Gemini · DeepSeek · OpenRouter · Mistral/Groq/xAI · Ollama/vLLM
```

```
proxy/src/
├── index.ts             маршрутизация, /v1/chat, лимиты, mapUpstreamError, /health
├── mealPlan.ts          /v1/meal-plan (generateJson → MealPlan, пересчёт списка покупок)
├── foodPhoto.ts         /v1/food-photo (generateJson с изображением → FoodAnalysis)
├── schemas.ts · shopping.ts · common.ts
└── providers/
    ├── types.ts             AiProvider, GenerateTextInput, GenerateJsonInput, ProviderResult, ProviderError
    ├── anthropic.ts         @anthropic-ai/sdk: beta.messages.create (чат), messages.parse + zodOutputFormat (JSON)
    ├── openaiCompatible.ts  fetch /chat/completions, json_schema → json_object, image_url data-URL, AbortController
    └── index.ts             resolveProvider(env, "chat" | "chef") — переменные ниже
```

| Переменная | Назначение | По умолчанию |
|---|---|---|
| `AI_PROVIDER` | `anthropic` или `openai` (любой OpenAI-совместимый API) | `anthropic` |
| `AI_MODEL` | Имя модели у провайдера (старая `MODEL` продолжает работать) | `claude-opus-5` для anthropic; для openai — модель провайдера (например, `gpt-4o`) |
| `AI_BASE_URL` | Базовый URL для `openai` (Gemini — `https://generativelanguage.googleapis.com/v1beta/openai`, локальный сервер — `http://…/v1`) | `https://api.openai.com/v1` |
| `AI_API_KEY` (секрет) | Ключ для `openai`; для `anthropic` — по-прежнему `ANTHROPIC_API_KEY` | — |
| `AI_EXTRA_HEADERS` | JSON с дополнительными заголовками (OpenRouter: `HTTP-Referer`, `X-Title`) | — |
| `CHEF_PROVIDER`, `CHEF_MODEL`, `CHEF_BASE_URL`, `CHEF_API_KEY`, `CHEF_EXTRA_HEADERS` | Переопределения для повара (`/v1/meal-plan`, `/v1/food-photo`); не заданы → наследуют `AI_*` | — |

Нет ключа для выбранного провайдера → `503 {error: "provider_not_configured"}`. Примеры конфигураций по провайдерам — `proxy/README.md`.

## AI-прокси: эндпоинты

| Эндпоинт | Вход | Выход | Лимит в день на устройство |
|---|---|---|---|
| `POST /v1/chat` | `system`, `messages`, `locale` | `{text, model, usage}` | `DAILY_LIMIT_PER_DEVICE` (40), KV `rl:` |
| `POST /v1/meal-plan` | `locale`, `days` 3/5/7, `mealsPerDay` 3–5, `goals` (ккал/БЖУ), `profile` (пол, возраст, вес, цель, ограничения), `prefs` (кухня, исключения, халяль, бюджет, `batchCooking`) | `{plan: MealPlan, model, usage}` — структурированный вывод (zod), `max_tokens` 16 000, effort low (Anthropic), таймаут 180 с; `shopping` пересчитан на сервере | `DAILY_LIMIT_PLANS_PER_DEVICE` (3), KV `rlp:` |
| `POST /v1/food-photo` | `locale`, `imageBase64` (JPEG/PNG/WebP, ≤ 1,5 МБ после декодирования), `mediaType`, `hint?` | `{analysis: FoodAnalysis, model, usage}`, `max_tokens` 4 096; `isFood=false`, если на фото не еда | `DAILY_LIMIT_PHOTOS_PER_DEVICE` (20), KV `rlf:`; тело ≤ 2 МБ (413) |

Общие для всех эндпоинтов: `X-App-Token`, `X-Device-Id`, общий дневной счётчик клуба `DAILY_LIMIT_GLOBAL`, fail-closed без KV, ошибки `{error, text}` с текстом на `locale`. `/health` → `{ok, features: ["chat","meal-plan","food-photo"], providers: {chat: {id, model}, chef: {id, model}}}` — без ключей и baseUrl. Детали и стоимость — `proxy/README.md`.

## Точки расширения (фаза 2)

| Опцион | Где подключать |
|---|---|
| Аккаунты/синхронизация | `data/repository/*` → удалённый источник; `UserRepository.getDeviceId()` уже даёт стабильный id |
| Интеграция с 1С:Фитнес клуб (абонемент, посещения, запись) | новый `data/crm/` + секции в `ClubScreen`; `memberId` → номер карты клиента |
| Серверные push (FCM) | `workers/Notifications` уже даёт каналы и иконку; добавить `FirebaseMessagingService` |
| Панель владельца | анонимная телеметрия из `ProxyAiClient`/Worker (KV) → простая веб-страница |
| iOS | домен и программы — чистый Kotlin, кандидаты на Kotlin Multiplatform |

## Тесты

JVM unit-тесты (`app/src/test`): GoalCalculator, ProgramCatalog (циклы/недели), StreakCalculator, Achievements, санитайзер истории чата, маршрутизация DemoAiClient, парсинг `club.json`, детектор новой акции, формат QR; AI-повар (`…/chef/`): парсинг `MealPlan`/`FoodAnalysis` с неизвестными полями, пересчёт граммов → КБЖУ, валидный план `DemoAiChefClient` на ru и kk, границы `ShoppingReminderScheduler.nextTrigger`. Запуск: `./gradlew :app:testDemoDebugUnitTest`. Прокси: `proxy/test/` (node:test, `npm test`, без сети) — серверная агрегация списка покупок; выбор провайдера по переменным и совместимость `MODEL` (`providers.test.ts`); OpenAI-совместимый клиент на моке `fetch` (`openaiCompatible.test.ts`: путь `json_schema`, откат на `json_object` со снятием markdown-ограждения, повтор и `bad_output` при невалидном JSON, маппинг 401/429/`content_filter`/`length`, data-URL изображения, доп. заголовки); `/health` показывает провайдеров без секретов. CI: `.github/workflows/android.yml` (тесты + сборка всех брендов + артефакты APK).
