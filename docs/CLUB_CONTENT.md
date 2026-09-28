# Контент клуба: акции, расписание, тренеры, цены

Вкладка «Клуб», баннер акций на главной, хэштеги и промокод на карточках для Stories
читаются из одного JSON-файла. Владелец клуба редактирует его **без релиза приложения**.

## Откуда приложение берёт данные

1. `app/src/main/assets/club/club.json` — версия, зашитая в сборку (fallback, работает офлайн).
   Для клиента кладите свой файл в `app/src/<brand>/assets/club/club.json`.
2. Если в `brands/<brand>.properties` задан `clubDataUrl`, приложение при запуске и раз в 6 часов
   (`ClubNewsWorker`) скачивает JSON по этому адресу, кэширует его в `filesDir/club_cache.json`
   и показывает локальное уведомление «Новая акция: …», когда появляется акция с новым `id`.

Порядок приоритета: сервер → кэш → assets.

## Схема JSON

```json
{
  "version": 1,
  "trainers": [
    { "name": "Данияр Ахметов", "role": "Персональный тренер", "photoUrl": null,
      "instagram": "daniyar.strong", "whatsapp": "77001234567",
      "specialties": ["Набор массы", "Новички"] }
  ],
  "services": [
    { "title": "Абонемент на 1 месяц", "price": 25000, "unit": "мес", "description": "Зал + группы" }
  ],
  "schedule": [
    { "day": 1, "time": "19:00", "title": "Функциональный тренинг", "trainer": "Нурлан", "durationMin": 55 }
  ],
  "promos": [
    { "id": "autumn_2026", "title": "3 месяца по цене 2", "text": "До конца ноября",
      "validUntil": "2026-11-30", "ctaText": "Узнать условия", "ctaUrl": "https://wa.me/77001234567" }
  ],
  "hashtags": ["#алматыфитнес"],
  "referralCode": "FRIEND10",
  "referralText": "Покажи код на ресепшене — скидка 10% тебе и другу."
}
```

| Поле | Обязательное | Комментарий |
|---|---|---|
| `version` | нет | Номер схемы, сейчас `1`. |
| `trainers[].name` | да | Имя и фамилия. |
| `trainers[].role` | нет | Специализация одной строкой. |
| `trainers[].whatsapp` | нет | Только цифры с кодом страны: `77001234567`. Кнопка «Записаться» пишет этому тренеру; если пусто — на общий WhatsApp клуба. |
| `trainers[].instagram` | нет | Ник без `@`. |
| `trainers[].specialties` | нет | Список чипов. |
| `services[].price` | да | Число в тенге, без пробелов. `0` = «Бесплатно». |
| `services[].unit` | нет | «мес», «занятие», «визит» — отображается как «25 000 ₸ / мес». |
| `schedule[].day` | да | 1 = понедельник … 7 = воскресенье. |
| `schedule[].time` | да | `HH:mm`, по этому полю сортируется. |
| `promos[].id` | да | Уникальный и **постоянный**: по нему определяется «новая акция» для push. Не переиспользуйте старые id. |
| `promos[].validUntil` | нет | `ГГГГ-ММ-ДД`; после этой даты акция скрывается. Пусто = бессрочно. |
| `promos[].ctaUrl` | нет | Ссылка кнопки (WhatsApp, Instagram, сайт). Без ссылки кнопка открывает WhatsApp клуба. |
| `hashtags` | нет | Печатаются на карточке для Stories. |
| `referralCode` / `referralText` | нет | Промокод «Приведи друга» на карточках и на экране «Клуб». |

Неизвестные поля игнорируются — можно добавлять свои колонки в таблицу.

## Google Sheets → JSON за 10 минут

Владельцу удобнее править таблицу, чем JSON. Схема: одна Google-таблица, 5 листов.

- **trainers**: `name | role | instagram | whatsapp | specialties` (специальности через `;`)
- **services**: `title | price | unit | description`
- **schedule**: `day | time | title | trainer | durationMin`
- **promos**: `id | title | text | validUntil | ctaText | ctaUrl`
- **settings**: `key | value` со строками `referralCode`, `referralText`, `hashtags` (через пробел)

Первая строка каждого листа — заголовки ровно с такими именами.

### Apps Script

В таблице: **Расширения → Apps Script**, вставьте код, затем **Развернуть → Новое развёртывание →
Веб-приложение**, «Кто имеет доступ: Все». Полученный URL вида
`https://script.google.com/macros/s/…/exec` впишите в `clubDataUrl` файла бренда.

```javascript
function doGet() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const rows = (name) => {
    const sh = ss.getSheetByName(name);
    if (!sh) return [];
    const [head, ...body] = sh.getDataRange().getValues();
    return body.filter(r => r.some(c => c !== '')).map(r =>
      Object.fromEntries(head.map((h, i) => [String(h).trim(), r[i]])));
  };
  const settings = Object.fromEntries(rows('settings').map(r => [r.key, String(r.value)]));
  const str = (v) => (v === undefined || v === null) ? '' : String(v).trim();
  const dateStr = (v) => v instanceof Date
    ? Utilities.formatDate(v, ss.getSpreadsheetTimeZone(), 'yyyy-MM-dd') : str(v);
  const timeStr = (v) => v instanceof Date
    ? Utilities.formatDate(v, ss.getSpreadsheetTimeZone(), 'HH:mm') : str(v);

  const json = {
    version: 1,
    trainers: rows('trainers').map(r => ({
      name: str(r.name), role: str(r.role),
      instagram: str(r.instagram) || null,
      whatsapp: str(r.whatsapp).replace(/\D/g, '') || null,
      specialties: str(r.specialties).split(';').map(s => s.trim()).filter(Boolean)
    })),
    services: rows('services').map(r => ({
      title: str(r.title), price: Number(r.price) || 0, unit: str(r.unit),
      description: str(r.description) || null
    })),
    schedule: rows('schedule').map(r => ({
      day: Number(r.day), time: timeStr(r.time), title: str(r.title),
      trainer: str(r.trainer) || null, durationMin: Number(r.durationMin) || 60
    })),
    promos: rows('promos').map(r => ({
      id: str(r.id), title: str(r.title), text: str(r.text),
      validUntil: dateStr(r.validUntil) || null,
      ctaText: str(r.ctaText) || null, ctaUrl: str(r.ctaUrl) || null
    })),
    hashtags: str(settings.hashtags).split(/\s+/).filter(Boolean),
    referralCode: str(settings.referralCode),
    referralText: str(settings.referralText)
  };
  return ContentService.createTextOutput(JSON.stringify(json))
    .setMimeType(ContentService.MimeType.JSON);
}
```

Проверка: откройте URL в браузере — должен вернуться JSON. Приложение подхватит изменения при
следующем запуске или в течение 6 часов.

## Альтернатива без Apps Script

Положите `club.json` на любой хостинг со статическими файлами (GitHub Pages, Cloudflare Pages, сайт
клуба) и укажите прямую ссылку в `clubDataUrl`. Файл должен отдаваться с `Content-Type: application/json`
и без авторизации.

## Как проверить push о новой акции

1. Добавьте акцию с новым `id` в таблицу.
2. В приложении: Настройки → перезапустите приложение или дождитесь `ClubNewsWorker` (до 6 часов).
3. Придёт уведомление «Новая акция: …» в канале «Новости клуба». При первом запуске уведомления
   не отправляются — все текущие акции считаются уже известными.
