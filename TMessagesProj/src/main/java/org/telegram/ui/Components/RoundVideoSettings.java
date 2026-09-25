package org.telegram.ui.Components;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.telegram.messenger.MessagesController;
import org.telegram.utils.camera.roundvideo.RoundVideoSession;

/** Stores Telegram-specific preferences for the Camera2 round-video implementation. */
public final class RoundVideoSettings {
    private static final String KEY_ENABLED = "round_video_camera2_enabled";
    private static final String KEY_OUTPUT_RESOLUTION = "round_video_output_resolution";
    private static final String KEY_CAMERA_RESOLUTION = "round_video_camera_resolution";
    private static final String KEY_FRAME_RATE = "round_video_frame_rate";
    private static final String KEY_VIDEO_BITRATE = "round_video_video_bitrate";
    private static final String KEY_COMPOSITION = "round_video_composition";
    private static final String LEGACY_KEY_OUTSIDE_EFFECT = "round_video_outside_effect";
    private static final String LEGACY_KEY_WATERMARK = "round_video_watermark";
    private static final String KEY_LAST_CAMERA = "round_video_last_camera";

    private static final int DEFAULT_VIDEO_BITRATE = 1_000_000;

    private RoundVideoSettings() {
    }

    /** Returns whether newly created round-video views use the Camera2 implementation. */
    public static boolean isEnabled() {
        return preferences().getBoolean(KEY_ENABLED, true);
    }

    /** Selects the round-video implementation used by subsequently created views. */
    public static void setEnabled(boolean enabled) {
        preferences().edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** Returns the configured encoder resolution. */
    public static @NonNull RoundVideoSession.OutputResolution getOutputResolution() {
        return readEnum(
                KEY_OUTPUT_RESOLUTION,
                RoundVideoSession.OutputResolution.P480,
                RoundVideoSession.OutputResolution.class
        );
    }

    /** Stores the encoder resolution. */
    public static void setOutputResolution(@NonNull RoundVideoSession.OutputResolution value) {
        putEnum(KEY_OUTPUT_RESOLUTION, value);
    }

    /** Returns the preferred camera source tier. */
    public static @NonNull RoundVideoSession.CameraResolution getCameraResolution() {
        return readEnum(
                KEY_CAMERA_RESOLUTION,
                RoundVideoSession.CameraResolution.HIGH,
                RoundVideoSession.CameraResolution.class
        );
    }

    /** Stores the preferred camera source tier. */
    public static void setCameraResolution(@NonNull RoundVideoSession.CameraResolution value) {
        putEnum(KEY_CAMERA_RESOLUTION, value);
    }

    /** Returns the requested capture frame-rate policy. */
    public static @NonNull RoundVideoSession.FrameRate getFrameRate() {
        return readEnum(
                KEY_FRAME_RATE,
                RoundVideoSession.FrameRate.FPS_30,
                RoundVideoSession.FrameRate.class
        );
    }

    /** Stores the requested capture frame-rate policy. */
    public static void setFrameRate(@NonNull RoundVideoSession.FrameRate value) {
        putEnum(KEY_FRAME_RATE, value);
    }

    /** Returns the AVC bitrate in bits per second. */
    public static int getVideoBitrate() {
        return Math.max(1, preferences().getInt(KEY_VIDEO_BITRATE, DEFAULT_VIDEO_BITRATE));
    }

    /** Stores the AVC bitrate in bits per second. */
    public static void setVideoBitrate(int value) {
        if (value <= 0) throw new IllegalArgumentException("Invalid video bitrate");
        preferences().edit().putInt(KEY_VIDEO_BITRATE, value).apply();
    }

    /** Returns whether circular styling and Telegram overlays are enabled. */
    public static boolean isCompositionEnabled() {
        SharedPreferences preferences = preferences();
        if (preferences.contains(KEY_COMPOSITION)) {
            return preferences.getBoolean(KEY_COMPOSITION, true);
        }
        return preferences.getBoolean(LEGACY_KEY_OUTSIDE_EFFECT, true)
                && preferences.getBoolean(LEGACY_KEY_WATERMARK, true);
    }

    /** Stores whether circular styling and Telegram overlays are enabled. */
    public static void setCompositionEnabled(boolean enabled) {
        preferences().edit()
                .putBoolean(KEY_COMPOSITION, enabled)
                .remove(LEGACY_KEY_OUTSIDE_EFFECT)
                .remove(LEGACY_KEY_WATERMARK)
                .apply();
    }

    /** Returns the camera used by the last successfully configured recording session. */
    public static @NonNull RoundVideoSession.CameraFacing getLastCameraFacing() {
        return readEnum(
                KEY_LAST_CAMERA,
                RoundVideoSession.CameraFacing.FRONT,
                RoundVideoSession.CameraFacing.class
        );
    }

    /** Stores the camera used by a successfully configured recording session. */
    public static void setLastCameraFacing(@NonNull RoundVideoSession.CameraFacing value) {
        putEnum(KEY_LAST_CAMERA, value);
    }

    private static SharedPreferences preferences() {
        return MessagesController.getGlobalMainSettings();
    }

    private static void putEnum(String key, Enum<?> value) {
        preferences().edit().putString(key, value.name()).apply();
    }

    private static <T extends Enum<T>> T readEnum(
            String key,
            T fallback,
            Class<T> enumClass
    ) {
        String value = preferences().getString(key, fallback.name());
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException | NullPointerException ignore) {
            return fallback;
        }
    }
}
