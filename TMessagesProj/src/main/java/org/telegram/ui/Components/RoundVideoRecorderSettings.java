package org.telegram.ui.Components;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.InputType;
import android.widget.EditText;

import io.github.webarmour.videomessagerecorder.RecordingMode;
import io.github.webarmour.videomessagerecorder.camera.AePrioritySetting;
import io.github.webarmour.videomessagerecorder.camera.AntibandingSetting;
import io.github.webarmour.videomessagerecorder.camera.AwbSetting;
import io.github.webarmour.videomessagerecorder.camera.ColorCorrectionSetting;
import io.github.webarmour.videomessagerecorder.camera.EdgeSetting;
import io.github.webarmour.videomessagerecorder.camera.FocusSetting;
import io.github.webarmour.videomessagerecorder.camera.NoiseReductionSetting;
import io.github.webarmour.videomessagerecorder.camera.OpticalStabilizationSetting;
import io.github.webarmour.videomessagerecorder.camera.RecordingConfig;
import io.github.webarmour.videomessagerecorder.camera.StabilizationSetting;
import io.github.webarmour.videomessagerecorder.camera.TonemapSetting;
import io.github.webarmour.videomessagerecorder.camera.VideoQuality;

/** Experimental Camera2 based AAR recorder settings, scoped to video notes. */
public final class RoundVideoRecorderSettings {
    private static final String PREFS = "round_video_custom_recorder";
    private static final String RECORDER = "recorder";
    private static final String MODE = "mode";
    private static final String QUALITY = "quality";
    private static final String FPS = "fps";
    private static final String BITRATE = "bitrate";
    private static final String NOISE = "noise";
    private static final String STABILIZATION = "stabilization";
    private static final String EDGE = "edge";
    private static final String HOLD_FPS = "hold_fps";
    private static final String LOW_LIGHT_BOOST = "low_light_boost";
    private static final String AE_PRIORITY = "ae_priority";
    private static final String EXPOSURE_STEPS = "exposure_steps";
    private static final String AWB = "awb";
    private static final String FOCUS = "focus";
    private static final String ANTIBANDING = "antibanding";
    private static final String OPTICAL_STABILIZATION = "optical_stabilization";
    private static final String COLOR_CORRECTION = "color_correction";
    private static final String TONEMAP = "tonemap";
    private static final String ZOOM = "zoom";
    private static final String AUDIO_BITRATE = "audio_bitrate";
    // CUSTOM_RECORDER_CONFIGURABLE_START_DELAY_V1
    private static final String RECORDING_START_DELAY = "recording_start_delay_ms";
    private static final int DEFAULT_RECORDING_START_DELAY_MS = 50;
    private static final int MAX_RECORDING_START_DELAY_MS = 2000;

    private static final String[] QUALITIES = {
            "COMPACT", "TELEGRAM", "IPHONE_LIKE", "HIGH", "TELEGRAM_NOTE_MAX", "HD", "FULL_HD"
    };
    private static final String[] QUALITY_LABELS = {
            "320 × 320 · экономия места", "384 × 384 · как в Telegram",
            "400 × 400 · стандартное", "480 × 480 · улучшенное",
            "640 × 640 · повышенное", "720 × 720 · HD", "1080 × 1080 · максимальное"
    };

    private RoundVideoRecorderSettings() { }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean useCustomRecorder(Context c) {
        return Build.VERSION.SDK_INT >= 28 && prefs(c).getBoolean(RECORDER, false);
    }

    /** Delay from camera-ready AND first preview frame to actual AAR recording start.
     *  0 ms disables the additional timer but retains the ready/first-frame gate.
     *  Applies to the next recording; never affects Telegram's Camera2 path.
     */
    public static long recordingStartDelayMs(Context context) {
        int saved = prefs(context).getInt(RECORDING_START_DELAY, DEFAULT_RECORDING_START_DELAY_MS);
        return Math.max(0, Math.min(MAX_RECORDING_START_DELAY_MS, saved));
    }

    public static void setCustomRecorder(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(RECORDER, enabled).apply();
    }

    public static String summary(Context c) {
        SharedPreferences p = prefs(c);
        return !p.getBoolean(MODE, false) ? "Автоматически" :
                qualityLabel(p.getString(QUALITY, "HIGH")) + ", " + p.getInt(FPS, 30) + " FPS";
    }

    private static String qualityLabel(String value) {
        if ("QHD".equals(value)) return "1080 × 1080 (вместо прежних 1440)";
        for (int i = 0; i < QUALITIES.length; i++) {
            if (QUALITIES[i].equals(value)) return QUALITY_LABELS[i];
        }
        return "480 × 480";
    }

    public static RecordingMode recordingMode(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.getBoolean(MODE, false)) return RecordingMode.Auto.INSTANCE;

        RecordingConfig d = new RecordingConfig();
        String storedQuality = p.getString(QUALITY, "HIGH");
        VideoQuality quality = safeEnum(VideoQuality.class, storedQuality, VideoQuality.HIGH);
        // Telegram MessageObject.isRoundVideoDocument rejects w/h > 1280.
        // Old 1440x1440 selection must not silently send a square regular video.
        if (quality.getOutputSize().getWidth() > 1280 || quality.getOutputSize().getHeight() > 1280) {
            quality = VideoQuality.FULL_HD;
        }
        return new RecordingMode.Custom(new RecordingConfig(
                quality,
                p.getInt(BITRATE, 2_000_000),
                p.getInt(FPS, 30),
                d.getIFrameIntervalSeconds(),
                d.getMaxBFrames(),
                p.getBoolean(HOLD_FPS, d.getHoldFpsInLowLight()),
                p.getBoolean(LOW_LIGHT_BOOST, d.getLowLightBoost()),
                fromPref(p, AE_PRIORITY, AePrioritySetting.class, d.getAePriority()),
                fromPref(p, ANTIBANDING, AntibandingSetting.class, d.getAntibanding()),
                fromPref(p, NOISE, NoiseReductionSetting.class, d.getNoiseReduction()),
                d.getHotPixelCorrection(),
                d.getLensShading(),
                fromPref(p, COLOR_CORRECTION, ColorCorrectionSetting.class, d.getColorCorrection()),
                d.getColorTemperatureKelvin(),
                d.getColorTint(),
                fromPref(p, STABILIZATION, StabilizationSetting.class, d.getStabilization()),
                fromPref(p, OPTICAL_STABILIZATION, OpticalStabilizationSetting.class, d.getOpticalStabilization()),
                fromPref(p, EDGE, EdgeSetting.class, d.getEdgeEnhancement()),
                d.getDistortionCorrection(),
                d.getAberrationCorrection(),
                fromPref(p, TONEMAP, TonemapSetting.class, d.getTonemap()),
                fromPref(p, AWB, AwbSetting.class, d.getAwb()),
                d.getAeLock(),
                d.getAwbLock(),
                p.getInt(EXPOSURE_STEPS, d.getExposureCompensationSteps()),
                d.getExposureMode(),
                d.getManualExposureTimeUs(),
                d.getManualIso(),
                fromPref(p, FOCUS, FocusSetting.class, d.getFocus()),
                d.getManualFocusDistance(),
                Float.intBitsToFloat(p.getInt(ZOOM, Float.floatToIntBits(d.getZoomRatio()))),
                d.getAperture(),
                false, // Never bake a circular mask into the MP4: Telegram clips the preview itself.
                d.getHidePreviewUntilRecording(),
                d.getAudioSampleRate(),
                p.getInt(AUDIO_BITRATE, d.getAudioBitrate())
        ));
    }

    private static <T extends Enum<T>> T safeEnum(Class<T> cls, String name, T fallback) {
        if (name == null) return fallback;
        try { return Enum.valueOf(cls, name); }
        catch (IllegalArgumentException ignored) { return fallback; }
    }

    private static <T extends Enum<T>> T fromPref(SharedPreferences p, String key, Class<T> cls, T fallback) {
        return safeEnum(cls, p.getString(key, fallback.name()), fallback);
    }

    public static void showDialog(Context context) {
        SharedPreferences p = prefs(context);
        boolean manual = p.getBoolean(MODE, false);
        new AlertDialog.Builder(context)
                .setTitle("Настройки записи кружков")
                .setItems(new String[]{
                        "Режим: " + (manual ? "ручные настройки" : "автоматически"),
                        "Качество видео · " + summary(context),
                        "Фронтальная камера: свет и фокус",
                        "Обработка изображения и стабилизация",
                        "Качество звука",
                        "Задержка начала записи · " + recordingStartDelayMs(context) + " мс",
                        "Сбросить параметры CustomRecorder"
                }, (dialog, item) -> {
                    switch (item) {
                        case 0:
                            choose(context, MODE, "Режим записи", new String[]{
                                    "Автоматически · камера выбирает параметры",
                                    "Вручную · использовать настройки ниже"
                            }, new String[]{"false", "true"}, -1); break;
                        case 1: showVideo(context); break;
                        case 2: showFrontCamera(context); break;
                        case 3: showProcessing(context); break;
                        case 4: showAudio(context); break;
                        case 5: showRecordingStartDelay(context); break;
                        case 6:
                            new AlertDialog.Builder(context)
                                    .setTitle("Сброс настроек")
                                    .setMessage("Сбросить только параметры CustomRecorder? Выбор рекордера сохранится.")
                                    .setPositiveButton("Сбросить", (d, which) -> {
                                        boolean recorder = p.getBoolean(RECORDER, false);
                                        p.edit().clear().putBoolean(RECORDER, recorder).apply();
                                        showDialog(context);
                                    })
                                    .setNegativeButton("Отмена", (d, which) -> showDialog(context))
                                    .show();
                            break;
                        default: break;
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private static void showVideo(Context context) {
        SharedPreferences p = prefs(context);
        new AlertDialog.Builder(context).setTitle("Качество видео")
                .setItems(new String[]{
                        "Разрешение · " + qualityLabel(p.getString(QUALITY, "HIGH")),
                        "Частота кадров · " + p.getInt(FPS, 30) + " FPS",
                        "Битрейт · " + (p.getInt(BITRATE, 2_000_000) / 1_000_000f) + " Мбит/с"
                }, (d, i) -> {
                    switch (i) {
                        case 0: choose(context, QUALITY, "Разрешение (не более 1280 для кружка)", QUALITY_LABELS, QUALITIES, 1); break;
                        case 1: choose(context, FPS, "Частота кадров", new String[]{"24 FPS", "30 FPS", "60 FPS"}, new String[]{"24", "30", "60"}, 1); break;
                        case 2: choose(context, BITRATE, "Битрейт видео", new String[]{"0,5 Мбит/с", "1 Мбит/с", "1,5 Мбит/с", "2 Мбит/с", "3 Мбит/с", "4 Мбит/с", "6 Мбит/с", "8 Мбит/с", "10 Мбит/с"}, new String[]{"500000", "1000000", "1500000", "2000000", "3000000", "4000000", "6000000", "8000000", "10000000"}, 1); break;
                    }
                }).setNegativeButton("Назад", (d, w) -> showDialog(context)).show();
    }

    private static void showFrontCamera(Context context) {
        SharedPreferences p = prefs(context);
        RecordingConfig d = new RecordingConfig();
        new AlertDialog.Builder(context).setTitle("Свет и фокус камеры")
                .setItems(new String[]{
                        "Усиление при слабом освещении · " + onOff(p, LOW_LIGHT_BOOST, d.getLowLightBoost()),
                        "Сохранять FPS при слабом свете · " + onOff(p, HOLD_FPS, d.getHoldFpsInLowLight()),
                        "Приоритет экспозиции · " + readableEnum(p.getString(AE_PRIORITY, d.getAePriority().name())),
                        "Поправка экспозиции · " + p.getInt(EXPOSURE_STEPS, d.getExposureCompensationSteps()) + " шагов",
                        "Баланс белого · " + readableEnum(p.getString(AWB, d.getAwb().name())),
                        "Фокусировка · " + readableEnum(p.getString(FOCUS, d.getFocus().name())),
                        "Подавление мерцания · " + readableEnum(p.getString(ANTIBANDING, d.getAntibanding().name())),
                        "Начальное увеличение · " + Float.intBitsToFloat(p.getInt(ZOOM, Float.floatToIntBits(d.getZoomRatio()))) + "×",
                        "О совместимости фронтальной камеры"
                }, (dialog, i) -> {
                    switch (i) {
                        case 0: toggle(context, LOW_LIGHT_BOOST, d.getLowLightBoost(), 2); break;
                        case 1: toggle(context, HOLD_FPS, d.getHoldFpsInLowLight(), 2); break;
                        case 2: enumChoose(context, AE_PRIORITY, "Приоритет экспозиции", AePrioritySetting.values(), 2); break;
                        case 3:
                            choose(
                                    context,
                                    EXPOSURE_STEPS,
                                    "Поправка экспозиции",
                                    new String[]{
                                            "−7", "−6", "−5", "−4",
                                            "−3", "−2", "−1",
                                            "0 · по умолчанию",
                                            "+1", "+2", "+3"
                                    },
                                    new String[]{
                                            "-7", "-6", "-5", "-4",
                                            "-3", "-2", "-1",
                                            "0", "1", "2", "3"
                                    },
                                    2
                            );
                            break;
                        case 4: enumChoose(context, AWB, "Баланс белого", AwbSetting.values(), 2); break;
                        case 5: enumChoose(context, FOCUS, "Фокусировка", FocusSetting.values(), 2); break;
                        case 6: enumChoose(context, ANTIBANDING, "Подавление мерцания", AntibandingSetting.values(), 2); break;
                        case 7: choose(context, ZOOM, "Начальное увеличение",
                                new String[]{
                                        "0,8× · широкий угол",
                                        "1,0× · без увеличения",
                                        "1,2×",
                                        "1,5×",
                                        "2,0×"
                                },
                                new String[]{
                                        "0.8",
                                        "1.0",
                                        "1.2",
                                        "1.5",
                                        "2.0"
                                }, 2); break;
                        case 8: new AlertDialog.Builder(context)
                                .setTitle("Фронтальная камера")
                                .setMessage("Настройки применяются к обеим камерам. Некоторые функции могут быть недоступны на фронтальной камере. Отдельных профилей и настройки зеркальности нет в публичном RecordingConfig AAR 0.0.2.")
                                .setPositiveButton("Понятно", (d2, w) -> showFrontCamera(context))
                                .show(); break;
                        default: break;
                    }
                }).setNegativeButton("Назад", (dialog, which) -> showDialog(context)).show();
    }

    private static void showProcessing(Context context) {
        SharedPreferences p = prefs(context);
        RecordingConfig defaults = new RecordingConfig();
        new AlertDialog.Builder(context).setTitle("Обработка изображения")
                .setItems(new String[]{
                        "Шумоподавление · " + readableEnum(p.getString(NOISE, defaults.getNoiseReduction().name())),
                        "Электронная стабилизация · " + readableEnum(p.getString(STABILIZATION, defaults.getStabilization().name())),
                        "Оптическая стабилизация · " + readableEnum(p.getString(OPTICAL_STABILIZATION, defaults.getOpticalStabilization().name())),
                        "Резкость и контуры · " + readableEnum(p.getString(EDGE, defaults.getEdgeEnhancement().name())),
                        "Коррекция цвета · " + readableEnum(p.getString(COLOR_CORRECTION, defaults.getColorCorrection().name())),
                        "Тональная обработка · " + readableEnum(p.getString(TONEMAP, defaults.getTonemap().name()))
                }, (d, i) -> {
                    switch (i) {
                        case 0: enumChoose(context, NOISE, "Шумоподавление", NoiseReductionSetting.values(), 3); break;
                        case 1: enumChoose(context, STABILIZATION, "Электронная стабилизация", StabilizationSetting.values(), 3); break;
                        case 2: enumChoose(context, OPTICAL_STABILIZATION, "Оптическая стабилизация (на фронталке может отсутствовать)", OpticalStabilizationSetting.values(), 3); break;
                        case 3: enumChoose(context, EDGE, "Резкость", EdgeSetting.values(), 3); break;
                        case 4: enumChoose(context, COLOR_CORRECTION, "Коррекция цвета", ColorCorrectionSetting.values(), 3); break;
                        case 5: enumChoose(context, TONEMAP, "Тональная обработка", TonemapSetting.values(), 3); break;
                    }
                }).setNegativeButton("Назад", (d, w) -> showDialog(context)).show();
    }

    private static void showRecordingStartDelay(Context context) {
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setText(Long.toString(recordingStartDelayMs(context)));
        input.setSelection(input.length());

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Задержка начала записи")
                .setMessage("Время после готовности камеры и первого кадра до начала записи. "
                        + "0 мс — без дополнительной задержки. Допустимо: 0–"
                        + MAX_RECORDING_START_DELAY_MS + " мс. Работает только для CustomRecorder.")
                .setView(input)
                .setPositiveButton("Сохранить", null)
                .setNegativeButton("Назад", (d, which) -> showDialog(context))
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String raw = input.getText().toString().trim();
                    int delay;
                    try {
                        delay = Integer.parseInt(raw);
                    } catch (NumberFormatException ex) {
                        input.setError("Введите целое число от 0 до " + MAX_RECORDING_START_DELAY_MS);
                        return;
                    }
                    if (delay < 0 || delay > MAX_RECORDING_START_DELAY_MS) {
                        input.setError("Допустимо: 0–" + MAX_RECORDING_START_DELAY_MS + " мс");
                        return;
                    }
                    prefs(context).edit().putInt(RECORDING_START_DELAY, delay).apply();
                    dialog.dismiss();
                    showDialog(context);
                }));
        dialog.show();
    }

    private static void showAudio(Context context) {
        SharedPreferences p = prefs(context);
        RecordingConfig defaults = new RecordingConfig();
        choose(context, AUDIO_BITRATE, "Битрейт звука (сейчас " + p.getInt(AUDIO_BITRATE, defaults.getAudioBitrate()) / 1000 + " кбит/с)",
                new String[]{"64 кбит/с", "96 кбит/с", "128 кбит/с", "192 кбит/с"},
                new String[]{"64000", "96000", "128000", "192000"}, 4);
    }

    private static String onOff(SharedPreferences p, String key, boolean def) {
        return p.getBoolean(key, def) ? "вкл." : "выкл.";
    }

    private static void toggle(Context c, String key, boolean def, int section) {
        SharedPreferences p = prefs(c);
        p.edit().putBoolean(key, !p.getBoolean(key, def)).apply();
        reopen(c, section);
    }

    private static String readableEnum(String name) {
        if (name == null) return "по умолчанию";
        switch (name) {
            case "OEM_DEFAULT": return "как настроено в телефоне";
            case "OFF": return "выключено";
            case "ON": return "включено";
            case "AUTO": return "автоматически";
            case "FAST": return "быстрая обработка";
            case "HIGH_QUALITY": return "максимальное качество";
            case "MINIMAL": return "минимально";
            case "CONTINUOUS_VIDEO": return "непрерывный автофокус";
            case "MANUAL": return "вручную";
            case "VIDEO": return "для видеозаписи";
            case "PREVIEW": return "для предпросмотра";
            case "ISO": return "приоритет ISO";
            case "SHUTTER": return "приоритет выдержки";
            case "HZ_50": return "50 Гц";
            case "HZ_60": return "60 Гц";
            case "DAYLIGHT": return "дневной свет";
            case "CLOUDY_DAYLIGHT": return "облачно";
            case "INCANDESCENT": return "лампы накаливания";
            case "FLUORESCENT": return "люминесцентный свет";
            case "WARM_FLUORESCENT": return "тёплый люминесцентный свет";
            case "TWILIGHT": return "сумерки";
            case "SHADE": return "тень";
            case "CCT": return "цветовая температура";
            default: return name.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        }
    }

    private static <T extends Enum<T>> void enumChoose(Context c, String key, String title, T[] choices, int section) {
        String[] raw = new String[choices.length];
        String[] labels = new String[choices.length];
        for (int i = 0; i < choices.length; i++) {
            raw[i] = choices[i].name();
            labels[i] = readableEnum(raw[i]);
        }
        choose(c, key, title, labels, raw, section);
    }

    private static void choose(Context context, String key, String title,
                               String[] labels, String[] values, int section) {
        new AlertDialog.Builder(context).setTitle(title)
                .setItems(labels, (dialog, index) -> {
                    SharedPreferences.Editor editor = prefs(context).edit();
                    String selected = values[index];
                    if (MODE.equals(key)) editor.putBoolean(key, Boolean.parseBoolean(selected));
                    else if (FPS.equals(key) || BITRATE.equals(key) || EXPOSURE_STEPS.equals(key) || AUDIO_BITRATE.equals(key))
                        editor.putInt(key, Integer.parseInt(selected));
                    else if (ZOOM.equals(key)) editor.putInt(key, Float.floatToIntBits(Float.parseFloat(selected)));
                    else editor.putString(key, selected);
                    editor.apply();
                    reopen(context, section);
                })
                .setNegativeButton("Назад", (dialog, which) -> reopen(context, section))
                .show();
    }

    private static void reopen(Context c, int section) {
        switch (section) {
            case 1: showVideo(c); break;
            case 2: showFrontCamera(c); break;
            case 3: showProcessing(c); break;
            case 4: showDialog(c); break;
            default: showDialog(c); break;
        }
    }
}
