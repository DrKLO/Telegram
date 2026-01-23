package org.telegram.ui.Components.FloatingDebug;

import android.annotation.SuppressLint;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.LaunchActivity;

public class FloatingDebugController {
    private static FloatingDebugView debugView;

    public static boolean isActive() {
        return SharedConfig.isFloatingDebugActive;
    }

    public static boolean onBackPressed(boolean invoked) {
        return debugView != null && debugView.onBackPressed(invoked);
    }

    public static void onDestroy() {
        if (debugView != null) {
            debugView.saveConfig();
        }
        debugView = null;
    }

    public static void setActive(LaunchActivity activity, boolean active) {
        setActive(activity, active, true);
    }

    @SuppressLint("WrongConstant")
    public static void setActive(LaunchActivity activity, boolean active, boolean saveConfig) {
        if (active == (debugView != null)) {
            return;
        }

        if (active) {
            debugView = new FloatingDebugView(activity);
            activity.getMainContainerFrameLayout().addView(debugView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            debugView.showFab();
        } else {
            debugView.dismiss(() -> {
                activity.getMainContainerFrameLayout().removeView(debugView);
                debugView = null;
            });
        }
        if (saveConfig) {
            SharedConfig.isFloatingDebugActive = active;
            SharedConfig.saveConfig();
        }
    }

    public static class DebugItem {
        final CharSequence title;
        final DebugItemType type;
        Runnable action;
        float from;
        float to;
        AnimationProperties.FloatProperty floatProperty;

        public DebugItem(CharSequence title, Runnable action) {
            this.type = DebugItemType.SIMPLE;
            this.title = title;
            this.action = action;
        }

        public DebugItem(CharSequence title) {
            this.type = DebugItemType.HEADER;
            this.title = title;
        }

        public DebugItem(CharSequence title, float from, float to, AnimationProperties.FloatProperty callback) {
            this.type = DebugItemType.SEEKBAR;
            this.title = title;
            this.from = from;
            this.to = to;
            this.floatProperty = callback;
        }
    }

    public enum DebugItemType {
        SIMPLE,
        HEADER,
        SEEKBAR
    }
}
