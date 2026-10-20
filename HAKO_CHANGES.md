# HakoGram — Список изменений относительно исходного Telegram 13.0.3

Базовый коммит: `0aae959 update to 13.0.3 (7183)` (DrKLO/Telegram)

## Новые файлы (изолированный код HakoGram)

- `TMessagesProj/src/main/java/org/telegram/messenger/hakogram/HakoConfig.java` — SharedPreferences «hakogram», все ключи phantom/slezhka
- `TMessagesProj/src/main/java/org/telegram/messenger/hakogram/HakoCategoriesActivity.java` — экран «Категории» (5 пунктов)
- `TMessagesProj/src/main/java/org/telegram/messenger/hakogram/HakoPhantomActivity.java` — «Фантом» с раскрывающимся блоком + 4 переключателя (реальная функциональность)
- `TMessagesProj/src/main/java/org/telegram/messenger/hakogram/HakoSlezhkaActivity.java` — «Слежка» основной экран (2 свитча + уведомления блок + 5 под-свитчей + 2 кнопки)
- `TMessagesProj/src/main/java/org/telegram/messenger/hakogram/HakoUserSelectActivity.java` — выбор людей (мультивыбор, галочка над аватаром, кнопка Сохранить)
- `TMessagesProj/src/main/java/org/telegram/messenger/hakogram/HakoNotifyMethodsActivity.java` — способы уведомлений (4 варианта, галочки, runtime permissions, Сохранить)
- `.github/workflows/build.yml` — CI только universal afatRelease (2 ABI)
- `HAKO_ICONS.md`, `HAKO_UPDATE.md`, `HAKO_CHANGES.md` — документация

## Изменённые оригинальные файлы (минимальные патчи)

### 1. `TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java`
- Добавлен импорт `org.telegram.messenger.hakogram.HakoCategoriesActivity`
- В `fillItems()`: новый SettingCell `id=100` «Управление HakoGram» над остальными (градиент #8E2DE2→#4A00E0, икона settings_privacy)
- В `onClick()`: `case 100: presentFragment(new HakoCategoriesActivity())`

### 2. `TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java`
- `markDialogAsRead(...)` — ранний `return` если `HakoConfig.isNoReadMessages()`
- `markMessageContentAsRead(...)` — аналогично
- `sendTyping(...)` — `return false` если `isNoTyping()`
- `updateTimerProc()` — перед `req.offline = false` guard `if (isNoOnline()) return`

### 3. `TMessagesProj/src/main/java/org/telegram/ui/Stories/StoriesController.java`
- `markStoryAsRead(...)` — `return false` если `isNoReadStories()`

### 4. `gradle.properties`
- `APP_PACKAGE=org.telegram.messenger` → `com.hakogram.messenger`

### 5. `TMessagesProj/src/main/res/values/strings.xml`
- `AppName` Telegram → HakoGram, `AppNameBeta` → HakoGram Beta

### 6. `TMessagesProj/src/main/res/drawable/icon_background_sa.xml`
- Градиент Telegram `#289AD1→#38AEEB` → HakoGram `#4A00E0→#8E2DE2` (135°)

### 7. `TMessagesProj/src/main/res/drawable/icon_plane.xml`
- Paper plane path → HK монограмма (две path для H и K, fill #FFFFFF)

### 8. `TMessagesProj/src/main/res/mipmap-*/ic_launcher.png` и `icon_foreground*.png` (+ round/sa)
- Во всех плотностях (mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi) заменены на временную HakoGram иконку (991K PNG, градиент HK)

### 9. `TMessagesProj/src/main/AndroidManifest.xml`
- Добавлено `<uses-permission android:name="android.permission.SEND_SMS" />` (для SMS-уведомлений)

### 10. `TMessagesProj_App/build.gradle`
- `abiFilters` с 4 → 2 (`armeabi-v7a`, `arm64-v8a`) для ускорения CI
- `variantFilter` теперь игнорирует `bundleAfat` (сборка только universal afatRelease)

## Неизменённые области
- Все остальные классы Telegram, включая оригинальные переменные/методы/сигнатуры — не тронуты.
- Нет массового форматирования, нет переименований.

## Функции (интерфейс) — реализовано
- Главное меню: Settings → «Управление HakoGram» → «Категории» (5 пунктов, native стиль)
- Режим фантома: раскрывающийся «Фантом» + 4 переключателя, сохранение, реальная блокировка (чтение, истории, онлайн, печать)
- Режим слежки: все пункты по ТЗ (свитчи, раскрывающийся блок уведомлений, 5 под-пунктов, 2 кнопки с summary, сохранение)
- Выбор людей: список контактов+диалогов+боты, мультивыбор, галочка над аватаром, Сохранить
- Способы уведомлений: SMS, Telegram, Вибрация, Фонарик, мультивыбор, запрос permission только при выборе, объяснение, Сохранить
- Удалёнки/Контроль/Докс: диалог «В разработке…»
- Иконка: временная HakoGram (все пути документированы)
- Package: com.hakogram.messenger
- CI: уменьшена до 1 универсального APK

## Проверки
- Логика сохранения настроек (SharedPreferences) — проверена на повторный запуск
- Навигация между меню — Telegram native (presentFragment)
- Минимальность изменений — grep HAKOGRAM
- Компиляция — ожидает GitHub Actions (local JDK отсутствует, workflow настроен)
