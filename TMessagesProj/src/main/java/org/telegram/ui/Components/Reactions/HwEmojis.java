package org.telegram.ui.Components.Reactions;

import static org.telegram.messenger.SharedConfig.PERFORMANCE_CLASS_HIGH;
import static org.telegram.messenger.SharedConfig.getDevicePerformanceClass;

import android.view.View;

import org.telegram.messenger.ImageLoader;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class HwEmojis {
    private static final Set<View> hwViews = new HashSet<>();
    private static volatile boolean hwEnabled = false;
    private static Runnable task;
    private static boolean firstOpen = true;
    private static boolean isPreparing = false;
    private static boolean isCascade = false;
    private static boolean isBeforePreparing = false;
    private static Boolean isWeakDevice;

    public static void prepare(Runnable runnable, boolean cascade) {
        isCascade = cascade;
        isPreparing = true;
        isBeforePreparing = false;
        if (firstOpen) {
            firstOpen = false;
        }
        task = runnable;
    }

    public static void beforePreparing() {
        ImageLoader.getInstance().getCacheOutQueue().pause();
        isBeforePreparing = true;
    }

    public static boolean isCascade() {
        return isCascade;
    }

    public static boolean isPreparing() {
        return isPreparing;
    }

    public static boolean isFirstOpen() {
        return firstOpen;
    }

    public static boolean isHwEnabled() {
        return hwEnabled;
    }

    public static boolean isHwEnabledOrPreparing() {
        return hwEnabled || isPreparing || isBeforePreparing;
    }

    public static void exec() {
        if (task != null) {
            task.run();
            task = null;
        }
    }

    public static boolean grab(View view) {
        if (hwEnabled) {
            hwViews.add(view);
        }
        return hwEnabled;
    }

    public static boolean grabIfWeakDevice(View... views) {
        if (isWeakDevice == null) {
            isWeakDevice = getDevicePerformanceClass() != PERFORMANCE_CLASS_HIGH;
        }

        if (!isWeakDevice) {
            return false;
        }

        if (hwEnabled) {
            hwViews.addAll(Arrays.asList(views));
        }
        return hwEnabled;
    }

    public static void enableHw() {
        ImageLoader.getInstance().getCacheOutQueue().pause();
        hwEnabled = true;
        isPreparing = false;
        isBeforePreparing = false;
    }

    public static void disableHw() {
        ImageLoader.getInstance().getCacheOutQueue().resume();
        hwEnabled = false;
        isPreparing = false;
        isBeforePreparing = false;
        task = null;
        for (View view : hwViews) {
            view.invalidate();
        }
        hwViews.clear();
    }
}
