# HakoGram — Временная иконка и пути замены

Иконка приложения заменена на временную заглушку HakoGram (градиент #4A00E0 → #8E2DE2 + HK).

Все основные файлы иконки указаны ниже. Для установки своей готовой иконки замените содержимое этих файлов на свои PNG (рекомендуется 1024×1024) и/или векторные drawable.

## Adaptive icon (основной)

- `TMessagesProj/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` — адаптивная иконка (фон + форсграунд + монохром)
  ```xml
  <adaptive-icon>
      <background android:drawable="@drawable/icon_background" />
      <foreground android:drawable="@mipmap/icon_foreground" />
      <monochrome android:drawable="@drawable/icon_plane" />
  </adaptive-icon>
  ```
- `TMessagesProj/src/main/res/drawable/icon_background.xml` → использует `icon_background_sa` (градиент)
- `TMessagesProj/src/main/res/drawable/icon_background_sa.xml` — градиент фона (текущий HakoGram: #4A00E0 → #8E2DE2)
- `TMessagesProj/src/main/res/mipmap-*/icon_foreground.png` (mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi) — форсграунд (HK монограмма)
- `TMessagesProj/src/main/res/drawable/icon_plane.xml` — монохромная иконка (HK вектор) для Android 13+
- Связанные варианты:
  - `mipmap-*/icon_foreground_round.png`
  - `mipmap-*/icon_foreground_sa.png`

## Legacy launcher (fallback)

- `TMessagesProj/src/main/res/mipmap-*/ic_launcher.png` — legacy иконка (копируется во все плотности)
- `TMessagesProj/src/main/res/mipmap-*/ic_launcher_round.png` — круглая версия
- `TMessagesProj/src/main/res/drawable-*/ic_launcher_dr.webp` — иконка для уведомлений/контактов (mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi)

## Манифесты

Ссылаются на `@mipmap/ic_launcher` / `@mipmap/ic_launcher_round`:

- `TMessagesProj/src/main/AndroidManifest.xml` — `<application android:icon="@mipmap/ic_launcher" ...>`
- `TMessagesProj/config/release/AndroidManifest.xml` — `android:icon="@mipmap/ic_launcher"`
- `TMessagesProj/config/debug/AndroidManifest.xml`
- `TMessagesProj/config/release/AndroidManifest_SDK23.xml`
- `TMessagesProj/config/release/AndroidManifest_standalone.xml` — использует `@mipmap/ic_launcher_sa` (отдельный набор, при необходимости скопируйте туда же HakoGram иконку)

## Как заменить на свою иконку

1. Подготовьте:
   - PNG 1024×1024 (прозрачный фон для адаптивного foreground, или полный квадрат для legacy)
   - SVG/vector для `icon_plane.xml` (монохром, один цвет #FFFFFF)
2. Скопируйте ваш PNG во все `mipmap-*/ic_launcher.png` и `ic_launcher_round.png` и `icon_foreground*.png`
   ```bash
   cp my_icon.png TMessagesProj/src/main/res/mipmap-hdpi/ic_launcher.png
   cp my_icon.png TMessagesProj/src/main/res/mipmap-mdpi/ic_launcher.png
   # ... xhdpi, xxhdpi, xxxhdpi, аналогично для _round и icon_foreground*
   ```
3. Если используете адаптивный фон-градиент — отредактируйте `drawable/icon_background_sa.xml` (startColor/endColor) или замените на `@drawable/your_background`.
4. Для уведомлений (опционально) замените `drawable-*/ic_launcher_dr.webp` (конвертируйте PNG в WEBP).
5. Пересоберите `assembleAfatRelease`.

## Примечание
Текущие файлы — заглушка. Оригинальная иконка Telegram удалена из использования как основная (но резервные копии PNG остаются в истории git).
