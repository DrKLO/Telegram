# HakoGram — Руководство по обновлению на новые версии Telegram

Цель: максимально упростить перенос изменений Telegram (upstream DrKLO/Telegram) в форк HakoGram.

## 1. Архитектура изменений (для минимизации конфликтов)

- **Новый код HakoGram изолирован:**
  - `TMessagesProj/src/main/java/org/telegram/messenger/hakogram/HakoConfig.java` — центральное хранилище настроек (SharedPreferences "hakogram")
  - `HakoCategoriesActivity.java` — экран «Категории»
  - `HakoPhantomActivity.java` — «Режим фантома» (4 переключателя)
  - `HakoSlezhkaActivity.java` — «Режим слежки» (основной)
  - `HakoUserSelectActivity.java` — выбор людей (мультивыбор с галочкой над аватаром)
  - `HakoNotifyMethodsActivity.java` — способы уведомлений (SMS/Telegram/Вибрация/Фонарик)
- **Минимальные изменения оригинальных файлов (только guards):**
  - `TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java` — 3 вставки guards (markDialogAsRead, markMessageContentAsRead, sendTyping, updateTimerProc для онлайн-статуса)
  - `TMessagesProj/src/main/java/org/telegram/ui/Stories/StoriesController.java` — 1 guard (markStoryAsRead)
  - `TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java` — добавлена кнопка «Управление HakoGram» (id 100) + импорт
- **Ресурсы/конфиги:**
  - `gradle.properties` — `APP_PACKAGE=com.hakogram.messenger`
  - `TMessagesProj/src/main/res/values/strings.xml` — AppName → HakoGram
  - `TMessagesProj/src/main/res/drawable/icon_background_sa.xml` — градиент HakoGram
  - `TMessagesProj/src/main/res/drawable/icon_plane.xml` — HK монограмма
  - `TMessagesProj/src/main/res/mipmap-*/ic_launcher.png` (×5) + `icon_foreground*` + `_round` — временная иконка
  - `TMessagesProj/src/main/AndroidManifest.xml` — добавлено `SEND_SMS`
  - `TMessagesProj_App/build.gradle` — уменьшены abiFilters до `arm64-v8a`, `armeabi-v7a` и отключён `bundleAfat` для быстрой CI
  - `.github/workflows/build.yml` — новая CI (только `assembleAfatRelease`)

Все HakoGram правки помечены комментарием `// HAKOGRAM:` для быстрого поиска `grep -rn HAKOGRAM`.

## 2. Рекомендуемый процесс обновления

### Подготовка remotes
```bash
git remote -v
# origin → https://github.com/mritch1313/HakoGram.git (ваш форк)
# добавьте upstream, если ещё нет:
git remote add upstream https://github.com/DrKLO/Telegram.git
git fetch upstream
git fetch origin
```

### Создание ветки для обновления
```bash
git checkout master
git pull origin master
git checkout -b update/telegram-13.x
```

### Слияние upstream
```bash
git merge upstream/master
# или конкретный тег: git merge upstream/11.0.0 ... 
# Разрешите конфликты:
# - приоритет: сохранить HAKOGRAM-блоки
# - внимательно проверить MessagesController, SettingsActivity, StoriesController
```

### Проверка конфликтов
```bash
grep -rn "HAKOGRAM" TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java
grep -rn "HAKOGRAM" TMessagesProj/src/main/java/org/telegram/ui/Stories/StoriesController.java
grep -rn "HAKOGRAM" TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java
```

Если Telegram переименовал методы (например, markDialogAsRead → markDialogRead), перенесите guards в новые сигнатуры.

### Тестирование
- Соберите локально: `./gradlew :TMessagesProj_App:assembleAfatRelease`
- Проверьте GitHub Actions (вкладка Actions → Build HakoGram)

### Завершение
```bash
git push origin update/telegram-13.x
# PR → master, merge → tag
```

## 3. Что проверять при каждой новой версии Telegram

1. Созданы ли новые методы чтения (`mark*AsRead`, `readStories`, `sendTyping`, `updateStatus`) — добавьте туда guards.
2. Изменилась ли логика `updateTimerProc` (онлайн статус) — убедитесь что блок `req.offline = false` всё ещё под guard.
3. Поменялся ли `SettingsActivity` (новые SettingCell ids) — id 100 для HakoGram свободен? Если конфликт, смените на 101+.
4. Добавлены ли новые abiFilters/productFlavors — верните HakoGram ограничение до 2 ABI.
5. Обновились ли иконки Telegram — не перезаписывайте HakoGram иконку случайно; проверьте `mipmap/ic_launcher*`.

## 4. История изменений оригинальных файлов

| Файл | Причина | Объём |
|------|---------|-------|
| `MessagesController.java` | Phantom guards (не читать сообщения, не показывать онлайн/печатает) | ~3 строки ×3 + коммент |
| `StoriesController.java` | Phantom guard (не читать истории) | 4 строки |
| `SettingsActivity.java` | Кнопка Управления HakoGram + onClick id 100 | +6 строк |
| `gradle.properties` | applicationId | 1 строка |
| `strings.xml` | AppName | 2 строки |
| `icon_background_sa.xml` | Временный дизайн | 4 строки |
| `icon_plane.xml` | HK monogram | весь файл |
| `AndroidManifest.xml` | SEND_SMS permission | 2 строки |
| `TMessagesProj_App/build.gradle` | Сократить ABI/флаворы | 6 строк |
| `.github/workflows/build.yml` | Новый CI | новый файл |

При обновлении достаточно проверить эти файлы; весь остальной код HakoGram в отдельном пакете `org.telegram.messenger.hakogram` не требует правок.

## 5. Откат и безопасность

- Не используйте `git push --force` к master без необходимости; делайте PR.
- Сохраняйте `release.keystore` (dummy) — перед публикацией замените на свой.
- Не удаляйте remote origin/upstream.

