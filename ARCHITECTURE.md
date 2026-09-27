# Архитектура

Один Android-модуль `app`, слои по пакетам. Никакого собственного бэкенда, кроме AI-прокси (`proxy/`).

```
com.fitcoach.app
├── brand/            BrandConfig — все настройки клиента из BuildConfig (brands/*.properties → flavor)
├── domain/
│   ├── model/        UserProfile (пол, цель, уровень, ограничения, программа, цели КБЖУ/вода, язык), Workout, ExerciseSet, Nutrition*, Water*, ChatMessage
│   ├── program/      ProgramCatalog (3 программы × 12 недель × 3 фазы), nextWorkout (гибкий график), StreakCalculator, Achievements
│   ├── usecase/      GoalCalculator (Mifflin-St Jeor), ai/BuildSystemPromptUseCase
│   ├── repository/   интерфейсы репозиториев
│   └── service/      ShareService (карточки Stories), DemoDataSeeder (демо-данные)
├── data/
│   ├── local/db/     Room v2: workouts, exercise_sets, nutrition_entries (+TEMPLATE-продукты), water_entries, chat_messages, user_profile, body_measurements
│   ├── repository/   реализации (маппинг entity ↔ domain)
│   ├── club/         ClubRepository: assets/club/club.json → кэш → удалённый JSON (clubDataUrl), ClubNewsWorker (локальный push о новой акции)
│   └── remote/       Retrofit-клиент Anthropic (только debug-режим «свой ключ»)
├── ai/               AiClient: ProxyAiClient (Cloudflare Worker) · DirectAnthropicClient (debug) · DemoAiClient (офлайн RU/KK) · AiClientSelector
├── share/            ShareServiceImpl — Canvas 1080×1920, FileProvider, Instagram Stories intent
├── demo/             DemoDataSeederImpl — 3 недели истории для презентаций
├── workers/          WaterReminderWorker, WorkoutReminderWorker, Notifications (каналы, иконка бренда)
├── di/               Hilt-модули (AppModule: Room/OkHttp/Retrofit; RepositoryModule; ServiceModule; AiModule; ClubModule)
└── presentation/
    ├── navigation/   Screen (маршруты), AppNavHost
    ├── theme/        FitCoachColors (брендовые цвета из BrandConfig + семантические), Typography, Theme
    ├── components/   FitCard, LabeledProgressBar, CircularProgress, SetButton…
    └── screens/      onboarding, dashboard, workout/{active,history,detail}, programs, nutrition, water, progress, chat, club, settings
```

## Ключевые решения

- **White-label через product flavors.** `app/build.gradle.kts` читает `brands/*.properties` и создаёт flavor на каждый файл: `applicationId`, имя, цвета и контакты попадают в `BuildConfig`/`resValue`. Сборка падает, если контраст акцента к фону < 3.0. Ресурсы клиента (`app/src/<brand>/res`, `assets`) переопределяют стандартные.
- **Локальные данные.** Всё хранится в Room на устройстве; аккаунтов нет. Это упрощает закон о персональных данных РК и убирает стоимость бэкенда. Синхронизация — опцион фазы 2.
- **AI без ключа в приложении.** Приложение → Worker (токен приложения, лимит на устройство/день, общий лимит) → Anthropic. Системный промпт строится на устройстве и не содержит имени/телефона. Ответ с `stop_reason: refusal` заменяется безопасной фразой; при отказе классификатора включён server-side fallback.
- **Гибкий график программ.** Следующая тренировка определяется числом выполненных тренировок в программе, а не днём недели: начать можно в любой день, демо не показывает «день отдыха».
- **Ограничения по здоровью.** `Restriction` влияет на подбор программы, автозамену упражнений (`avoidFor` → альтернатива), вопрос о дискомфорте после тренировки (только при «спина») и системный промпт AI.
- **Контент клуба без разработчика.** `club.json` (тренеры, услуги, расписание, акции, хэштеги, промокод) грузится из assets, затем из `clubDataUrl` (Google Sheets → Apps Script → JSON), кэшируется; новая акция → локальное уведомление.
- **Демо-надёжность.** `DemoAiClient` (офлайн-сценарии RU/KK) и `DemoDataSeeder` (3 недели истории) — чтобы презентация никогда не выглядела пустой или сломанной.

## Точки расширения (фаза 2)

| Опцион | Где подключать |
|---|---|
| Аккаунты/синхронизация | `data/repository/*` → удалённый источник; `UserRepository.getDeviceId()` уже даёт стабильный id |
| Интеграция с 1С:Фитнес клуб (абонемент, посещения, запись) | новый `data/crm/` + секции в `ClubScreen`; `memberId` → номер карты клиента |
| Серверные push (FCM) | `workers/Notifications` уже даёт каналы и иконку; добавить `FirebaseMessagingService` |
| Панель владельца | анонимная телеметрия из `ProxyAiClient`/Worker (KV) → простая веб-страница |
| iOS | домен и программы — чистый Kotlin, кандидаты на Kotlin Multiplatform |

## Тесты

JVM unit-тесты (`app/src/test`): GoalCalculator, ProgramCatalog (циклы/недели), StreakCalculator, Achievements, санитайзер истории чата, маршрутизация DemoAiClient, парсинг `club.json`, детектор новой акции, формат QR. Запуск: `./gradlew :app:testDemoDebugUnitTest`. CI: `.github/workflows/android.yml` (тесты + сборка всех брендов + артефакты APK).
