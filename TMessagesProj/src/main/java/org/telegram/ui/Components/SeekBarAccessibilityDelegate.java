package org.telegram.ui.Components;

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import java.util.HashMap;
import java.util.Map;

public abstract class SeekBarAccessibilityDelegate extends View.AccessibilityDelegate {

    private static final CharSequence SEEK_BAR_CLASS_NAME = SeekBar.class.getName();

    private final Map<View, Runnable> accessibilityEventRunnables = new HashMap<>(4);
    private final View.OnAttachStateChangeListener onAttachStateChangeListener = new View.OnAttachStateChangeListener() {
        @Override
        public void onViewAttachedToWindow(View v) {
        }

        @Override
        public void onViewDetachedFromWindow(View v) {
            v.removeCallbacks(accessibilityEventRunnables.remove(v));
            v.removeOnAttachStateChangeListener(this);
        }
    };

    @Override
    public boolean performAccessibilityAction(@NonNull View host, int action, Bundle args) {
        if (super.performAccessibilityAction(host, action, args)) {
            return true;
        }
        return performAccessibilityActionInternal(host, action, args);
    }

    public boolean performAccessibilityActionInternal(@Nullable View host, int action, Bundle args) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            doScroll(host, action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            if (host != null) {
                postAccessibilityEventRunnable(host);
            }
            return true;
        }
        return false;
    }

    public final boolean performAccessibilityActionInternal(int action, Bundle args) {
        return performAccessibilityActionInternal(null, action, args);
    }

    private void postAccessibilityEventRunnable(@NonNull View host) {
        if (!ViewCompat.isAttachedToWindow(host)) {
            return;
        }
        Runnable runnable = accessibilityEventRunnables.get(host);
        if (runnable == null) {
            accessibilityEventRunnables.put(host, runnable = () -> sendAccessibilityEvent(host, AccessibilityEvent.TYPE_VIEW_SELECTED));
            host.addOnAttachStateChangeListener(onAttachStateChangeListener);
        } else {
            host.removeCallbacks(runnable);
        }
        host.postDelayed(runnable, 400);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull View host, AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(host, info);
        onInitializeAccessibilityNodeInfoInternal(host, info);
    }

    public void onInitializeAccessibilityNodeInfoInternal(@Nullable View host, AccessibilityNodeInfo info) {
        info.setClassName(SEEK_BAR_CLASS_NAME);
        final CharSequence contentDescription = getContentDescription(host);
        if (!TextUtils.isEmpty(contentDescription)) {
            info.setText(contentDescription);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (canScrollBackward(host)) {
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            }
            if (canScrollForward(host)) {
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            }
        }
    }

    public final void onInitializeAccessibilityNodeInfoInternal(AccessibilityNodeInfo info) {
        onInitializeAccessibilityNodeInfoInternal(null, info);
    }

    protected CharSequence getContentDescription(@Nullable View host) {
        return null;
    }

    protected abstract void doScroll(@Nullable View host, boolean backward);

    protected abstract boolean canScrollBackward(@Nullable View host);

    protected abstract boolean canScrollForward(@Nullable View host);
}
