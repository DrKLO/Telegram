/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;

public abstract class BaseCell extends ViewGroup implements SizeNotifierFrameLayout.IViewWithInvalidateCallback {

    private final class CheckForTap implements Runnable {
        public void run() {
            if (pendingCheckForLongPress == null) {
                pendingCheckForLongPress = new CheckForLongPress();
            }
            pendingCheckForLongPress.currentPressCount = ++pressCount;
            postDelayed(pendingCheckForLongPress, ViewConfiguration.getLongPressTimeout() - ViewConfiguration.getTapTimeout());
        }
    }

    class CheckForLongPress implements Runnable {
        public int currentPressCount;

        public void run() {
            if (checkingForLongPress && getParent() != null && currentPressCount == pressCount) {
                checkingForLongPress = false;
                if (onLongPress()) {
                    try {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    } catch (Exception ignore) {}
                    MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                    onTouchEvent(event);
                    event.recycle();
                }
            }
        }
    }

    private boolean checkingForLongPress = false;
    private CheckForLongPress pendingCheckForLongPress = null;
    private int pressCount = 0;
    private CheckForTap pendingCheckForTap = null;

    public BaseCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setFocusable(true);
        setHapticFeedbackEnabled(true);
    }

    public static void setDrawableBounds(Drawable drawable, int x, int y) {
        setDrawableBounds(drawable, x, y, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
    }

    public static void setDrawableBounds(Drawable drawable, float x, float y) {
        setDrawableBounds(drawable, (int) x, (int) y, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
    }

    public static float setDrawableBounds(Drawable drawable, float x, float y, float h) {
        float w = drawable.getIntrinsicWidth() * h / drawable.getIntrinsicHeight();
        setDrawableBounds(drawable, (int) x, (int) y, (int) w, (int) h);
        return w;
    }

    public static void setDrawableBounds(Drawable drawable, int x, int y, int w, int h) {
        if (drawable != null) {
            drawable.setBounds(x, y, x + w, y + h);
        }
    }

    public static void setDrawableBounds(Drawable drawable, float x, float y, int w, int h) {
        if (drawable != null) {
            drawable.setBounds((int) x, (int) y, (int) x + w, (int) y + h);
        }
    }

    protected void startCheckLongPress() {
        if (checkingForLongPress) {
            return;
        }
        checkingForLongPress = true;
        if (pendingCheckForTap == null) {
            pendingCheckForTap = new CheckForTap();
        }
        postDelayed(pendingCheckForTap, ViewConfiguration.getTapTimeout());
    }

    protected void cancelCheckLongPress() {
        checkingForLongPress = false;
        if (pendingCheckForLongPress != null) {
            removeCallbacks(pendingCheckForLongPress);
        }
        if (pendingCheckForTap != null) {
            removeCallbacks(pendingCheckForTap);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    protected boolean onLongPress() {
        return true;
    }

    public int getBoundsLeft() {
        return 0;
    }

    public int getBoundsRight() {
        return getWidth();
    }

    protected Runnable invalidateCallback;
    @Override
    public void listenInvalidate(Runnable callback) {
        invalidateCallback = callback;
    }

    public void invalidateLite() {
        super.invalidate();
    }
    @Override
    public void invalidate() {
        if (invalidateCallback != null) {
            invalidateCallback.run();
        }
        super.invalidate();
    }

    private boolean cachingTop, cachingBottom;
    private RenderNode renderNode;
    public void setCaching(boolean top, boolean caching) {
        if (top) {
            this.cachingTop = SharedConfig.useNewBlur && caching;
        } else {
            this.cachingBottom = SharedConfig.useNewBlur && caching;
        }
    }

    private boolean forceNotCacheNextFrame;
    public void forceNotCacheNextFrame() {
        forceNotCacheNextFrame = true;
    }

    protected boolean updatedContent;
    public void drawCached(Canvas canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && renderNode != null && renderNode.hasDisplayList() && canvas.isHardwareAccelerated() && !updatedContent) {
            canvas.drawRenderNode(renderNode);
        } else {
            draw(canvas);
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final boolean cache = (cachingTop || cachingBottom || SharedConfig.useNewBlur) && allowCaching();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cache != (renderNode != null)) {
            if (cache) {
                renderNode = new RenderNode("basecell");
                renderNode.setClipToBounds(false);
                updatedContent = true;
            } else {
                renderNode = null;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && renderNode != null && !forceNotCacheNextFrame && canvas.isHardwareAccelerated()) {
            renderNode.setPosition(0, 0, getWidth(), getHeight());
            RecordingCanvas recordingCanvas = renderNode.beginRecording();
            super.draw(recordingCanvas);
            renderNode.endRecording();
            canvas.drawRenderNode(renderNode);
        } else {
            super.draw(canvas);
        }
        forceNotCacheNextFrame = false;
        updatedContent = false;
    }

    protected boolean allowCaching() {
        return true;
    }

    public static class RippleDrawableSafe extends RippleDrawable {
        public RippleDrawableSafe(@NonNull ColorStateList color, @Nullable Drawable content, @Nullable Drawable mask) {
            super(color, content, mask);
        }

        @Override
        public boolean setState(@NonNull int[] stateSet) {
            if (getCallback() instanceof BaseCell) {
                ((BaseCell) getCallback()).forceNotCacheNextFrame();
            }
            return super.setState(stateSet);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final int save = canvas.save();
            try {
                super.draw(canvas);
            } catch (Exception e) {
                FileLog.e("probably forgot to put setCallback", e);
            } finally {
                canvas.restoreToCount(save);
            }
        }
    }
}
