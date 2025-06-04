package org.telegram.messenger.pip.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import androidx.core.math.MathUtils;

import org.telegram.messenger.ApplicationLoader;

public class PipDuration {
    private final SharedPreferences mPrefs;

    private long estimated;
    private long start;
    private int count;

    public PipDuration(String name) {
        mPrefs = ApplicationLoader.applicationContext.getSharedPreferences("pip_duration_" + name, Context.MODE_PRIVATE);
        estimated = mPrefs.getLong("estimated", 400);
        count = mPrefs.getInt("count", 0);
    }

    public void start() {
        this.start = SystemClock.uptimeMillis();
    }

    public long estimated() {
        return estimated;
    }

    public float progress() {
        if (estimated > 0) {
            return MathUtils.clamp((float) (SystemClock.uptimeMillis() - start) / estimated, 0, 1);
        }

        return 0.5f;
    }

    public boolean isStarted() {
        return start != 0;
    }

    public long end() {
        if (start == 0) {
            return 0;
        }

        final long duration = SystemClock.uptimeMillis() - start;
        final int weight = MathUtils.clamp(count, 0, 9);
        estimated = (estimated * weight / 10) + (duration * (10 - weight) / 10);
        start = 0;
        count++;

        mPrefs.edit()
            .putLong("estimated", estimated)
            .putInt("count", count).apply();

        return duration;
    }
}
