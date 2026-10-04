# White-label: новый клиент за 15 минут

Каждый клуб — это отдельная сборка приложения со своим именем, иконкой, цветами, контактами и AI-прокси.
Все настройки клиента живут в **одном файле** `brands/<клиент>.properties`. Код не меняется.

## Шаг 1. Создайте файл бренда

```bash
cp brands/_template.properties.example brands/ironclub.properties
```

Имя файла (`ironclub`) — это имя flavor и папки ресурсов. Только латиница и цифры, начинается с буквы.

Заполните поля (все — обычный текст, кодировка UTF-8):

| Ключ | Что это | Пример |
|---|---|---|
| `appName` | Имя на иконке и в системе | `Iron Club` |
| `applicationId` | Уникальный id в Google Play (менять после публикации нельзя) | `kz.ironclub.app` |
| `versionName` | Версия для магазина | `1.0.0` |
| `brandName` | Короткое имя внутри приложения | `Iron Club` |
| `aiCoachName` | Как представляется AI-тренер | `Арман` |
| `defaultLanguage` | `ru` или `kk` | `ru` |
| `clubName`, `clubCity`, `clubAddress`, `clubHours` | Вкладка «Клуб», карточки шаринга, промпт AI | |
| `clubPhone` | Кнопка «Позвонить» | `+7 727 123 45 67` |
| `clubWhatsapp` | Кнопка WhatsApp, только цифры с кодом страны | `77011234567` |
| `clubInstagram` | Без `@` | `ironclub.almaty` |
| `clubMapUrl` | Ссылка 2GIS / Google Maps | `https://2gis.kz/almaty/firm/…` |
| `clubWebsite` | Сайт (необязательно) | |
| `clubDataUrl` | JSON с тренерами/расписанием/акциями (см. `docs/CLUB_CONTENT.md`). Пусто → берётся из `assets/club/club.json` | |
| `newsUrl` | Зарезервировано | |
| `accentColor`, `accentOnColor`, `backgroundColor`, `surfaceColor`, `cardColor` | Цвета `#RRGGBB`. Тема тёмная; акцент должен быть светлым — сборка проверяет контраст (≥ 3.0) | `#FF5A1F` |
| `aiProxyUrl` | Адрес Cloudflare Worker из папки `proxy/` (только `https://`) | |
| `aiProxyToken` | Токен Worker — НЕ в git: переменная `AI_PROXY_TOKEN_<NAME>` или `brands/<name>.secrets.properties` | |
| `privacyPolicyUrl` | Публичная ссылка на политику конфиденциальности (ссылка в онбординге и настройках; обязательна для Play) | |
| `aiModel` | Модель только для режима разработчика (debug-сборка, свой ключ Anthropic; по умолчанию `claude-opus-5`). Нейросеть для клиентов клуба выбирается на прокси — шаг 4 | |

## Шаг 2. Логотип и иконка (необязательно, но именно это даёт «вау»)

Создайте папку ресурсов клиента и положите туда файлы — они **переопределят** стандартные:

```
app/src/ironclub/res/drawable/brand_logo.xml            # или drawable-nodpi/brand_logo.png (квадрат, прозрачный фон, ≥512px)
app/src/ironclub/res/drawable/ic_launcher_foreground.xml  # передний план иконки (108×108dp, безопасная зона — центральные 66dp)
```

Фон иконки и сплэша берётся из `backgroundColor`, цвет стандартного логотипа — из `accentColor`. Если у клуба есть PNG-логотип, достаточно положить его как `drawable-nodpi/brand_logo.png` и `drawable-nodpi/ic_launcher_foreground.png` (108×108 dp → 432×432 px для xxhdpi; подойдёт 512×512 с отступами).

## Шаг 3. Контент вкладки «Клуб»

Скопируйте `app/src/main/assets/club/club.json` в `app/src/ironclub/assets/club/club.json` и заполните тренеров, расписание групповых, услуги, акции. Позже клуб сможет править это сам через Google Sheets — см. `docs/CLUB_CONTENT.md`.

## Шаг 4. AI-прокси и выбор нейросети

Один Worker на клиента (ключ AI-провайдера — у клуба или у вас, лимиты — в переменных). Инструкция: `proxy/README.md`. Полученные URL и токен → `aiProxyUrl` / `aiProxyToken`.
Тот же Worker обслуживает AI-повара (`/v1/meal-plan`, `/v1/food-photo`): лимиты планов и фото в день на устройство — `DAILY_LIMIT_PLANS_PER_DEVICE` (3) и `DAILY_LIMIT_PHOTOS_PER_DEVICE` (20) в `wrangler.toml`; для пакета «Старт» (без AI-повара) — 0.

**Нейросеть выбирается одной переменной** в `wrangler.toml` `[vars]` — приложение об этом не знает и не пересобирается:

| Хотим | `[vars]` | Секрет (`wrangler secret put …`) |
|---|---|---|
| Claude (по умолчанию) | `AI_PROVIDER = "anthropic"`, `AI_MODEL = "claude-sonnet-5"` (или `claude-opus-5`) | `ANTHROPIC_API_KEY` |
| OpenAI | `AI_PROVIDER = "openai"`, `AI_MODEL = "<модель OpenAI>"` | `AI_API_KEY` |
| Google Gemini | `AI_PROVIDER = "openai"`, `AI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai"`, `AI_MODEL = "<модель Gemini>"` | `AI_API_KEY` |
| DeepSeek, OpenRouter, Mistral, Groq, локальная Ollama/vLLM | `AI_PROVIDER = "openai"`, `AI_BASE_URL = "<OpenAI-совместимый URL>/v1"`, `AI_MODEL = "<модель>"` | `AI_API_KEY` |

Повар может работать на другой модели, чем чат: задайте `CHEF_PROVIDER` / `CHEF_MODEL` / `CHEF_BASE_URL` и секрет `CHEF_API_KEY` (не заданы — наследуют `AI_*`). Например, чат на Claude, фото еды — на Gemini. Модель для повара должна понимать изображения и отдавать JSON; точность оценки по фото зависит от модели — проверьте на 5–10 своих снимках перед релизом. Имена моделей и цены берите из документации провайдера; примеры конфигураций по каждому провайдеру — `proxy/README.md`. Проверка: `curl …/health` → `providers.chat` и `providers.chef` показывают выбранные `id` / `model` (без ключей).

## Шаг 5. Сборка

```bash
./gradlew :app:assembleIronclubDebug      # для проверки
./gradlew :app:assembleIronclubRelease    # релиз (подпись — см. docs/RELEASE.md)
```

APK: `app/build/outputs/apk/ironclub/…`. Все бренды сразу: `./gradlew assembleDebug`.

## Чек-лист «брендинг под клиента за 1 час перед демо»

1. Instagram клуба → скачать логотип (аватар), посмотреть фирменный цвет → `accentColor`.
2. 2GIS → адрес, часы, телефон, ссылка → `clubAddress`, `clubHours`, `clubPhone`, `clubMapUrl`.
3. WhatsApp клуба (обычно в шапке Instagram) → `clubWhatsapp`.
4. 3–4 тренера с фото из Instagram, 5–6 услуг с ценами с сайта/сторис, 2 текущие акции → `club.json`.
5. `./gradlew :app:assemble<Brand>Debug` → установить на телефон для демо (`adb install`).
6. В настройках приложения: «Загрузить 3 недели» (Настройки → Демо-данные) → приложение выглядит «живым», включая демо-план питания и список покупок AI-повара.
7. Если интернета на встрече может не быть — включить «Демо-режим AI (офлайн)»: тренер отвечает по сценариям, AI-повар показывает готовый план и разбор фото с пометкой «демо-оценка».

## Что можно, а что нельзя менять после публикации

- `applicationId` — **нельзя** (это идентификатор приложения в Google Play).
- Всё остальное (цвета, контакты, логотип, прокси) — можно в любой момент, это новая версия (`versionName`, `versionCode` — см. `docs/RELEASE.md`).
- Нейросеть (провайдер и модель) — меняется на прокси (`AI_*` / `CHEF_*`) в любой момент, без новой версии приложения.
