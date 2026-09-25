package org.telegram.utils.camera.roundvideo;

import android.app.Activity;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.telegram.messenger.ApplicationLoader;

final class RoundVideoSwitchTimingStore {

    private static final int SAMPLE_COUNT = 8;
    private static final int MIN_SAMPLE_MS = 200;
    private static final int MAX_SAMPLE_MS = 2_000;
    private static final int DEFAULT_BACK_TO_FRONT_MS = 680;
    private static final int DEFAULT_FRONT_TO_BACK_MS = 630;

    private static final int BACK_TO_FRONT = 0;
    private static final int FRONT_TO_BACK = 1;

    private static final String[] KEYS = {
            "round_video_switch_back_to_front_ms",
            "round_video_switch_front_to_back_ms"
    };
    private static final int[][] samples = new int[2][SAMPLE_COUNT];
    private static final int[] counts = new int[2];
    private static final int[] nextIndices = new int[2];
    private static boolean loaded;

    private RoundVideoSwitchTimingStore() {
    }

    static synchronized int getAverageMs(
            @NonNull RoundVideoSession.CameraFacing from,
            @NonNull RoundVideoSession.CameraFacing to
    ) {
        ensureLoaded();
        int direction = direction(from, to);
        return average(direction);
    }

    static synchronized int recordAndGetAverageMs(
            @NonNull RoundVideoSession.CameraFacing from,
            @NonNull RoundVideoSession.CameraFacing to,
            int durationMs
    ) {
        ensureLoaded();
        int direction = direction(from, to);
        int value = clamp(durationMs, MIN_SAMPLE_MS, MAX_SAMPLE_MS);
        samples[direction][nextIndices[direction]] = value;
        nextIndices[direction] = (nextIndices[direction] + 1) % SAMPLE_COUNT;
        if (counts[direction] < SAMPLE_COUNT) {
            counts[direction]++;
        }
        persist(direction);
        return average(direction);
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        SharedPreferences preferences = preferences();
        for (int direction = 0; direction < KEYS.length; direction++) {
            String stored = preferences.getString(KEYS[direction], null);
            if (stored == null || stored.isEmpty()) {
                continue;
            }
            String[] values = stored.split(",");
            int start = Math.max(0, values.length - SAMPLE_COUNT);
            for (int i = start; i < values.length; i++) {
                try {
                    int parsed = clamp(
                            Integer.parseInt(values[i]),
                            MIN_SAMPLE_MS,
                            MAX_SAMPLE_MS
                    );
                    samples[direction][counts[direction]++] = parsed;
                } catch (NumberFormatException ignore) {
                }
            }
            nextIndices[direction] = counts[direction] % SAMPLE_COUNT;
        }
    }

    private static int average(int direction) {
        int count = counts[direction];
        if (count == 0) {
            return direction == BACK_TO_FRONT
                    ? DEFAULT_BACK_TO_FRONT_MS
                    : DEFAULT_FRONT_TO_BACK_MS;
        }
        int sum = 0;
        for (int i = 0; i < count; i++) {
            sum += samples[direction][i];
        }
        return Math.round(sum / (float) count);
    }

    private static void persist(int direction) {
        int count = counts[direction];
        int first = count == SAMPLE_COUNT ? nextIndices[direction] : 0;
        StringBuilder value = new StringBuilder(count * 5);
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                value.append(',');
            }
            value.append(samples[direction][(first + i) % SAMPLE_COUNT]);
        }
        preferences().edit().putString(KEYS[direction], value.toString()).apply();
    }

    private static int direction(
            @NonNull RoundVideoSession.CameraFacing from,
            @NonNull RoundVideoSession.CameraFacing to
    ) {
        if (from == to) {
            throw new IllegalArgumentException("Camera switch direction must change");
        }
        return from == RoundVideoSession.CameraFacing.BACK ? BACK_TO_FRONT : FRONT_TO_BACK;
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                "mainconfig",
                Activity.MODE_PRIVATE
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
