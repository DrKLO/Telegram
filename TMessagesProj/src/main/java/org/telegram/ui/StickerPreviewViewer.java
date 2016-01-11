/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.LayoutHelper;

public class StickerPreviewViewer {

    private class FrameLayoutDrawer extends FrameLayout {
        public FrameLayoutDrawer(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            getInstance().onDraw(canvas);
        }
    }

    private ColorDrawable backgroundDrawable = new ColorDrawable(0x71000000);
    private Activity parentActivity;
    private WindowManager.LayoutParams windowLayoutParams;
    private FrameLayout windowView;
    private FrameLayoutDrawer containerView;
    private ImageReceiver centerImage = new ImageReceiver();
    private boolean isVisible = false;
    private float showProgress;
    private long lastUpdateTime;
    private int keyboardHeight = AndroidUtilities.dp(200);

    private TLRPC.Document currentSticker = null;

    private static volatile StickerPreviewViewer Instance = null;
    public static StickerPreviewViewer getInstance() {
        StickerPreviewViewer localInstance = Instance;
        if (localInstance == null) {
            synchronized (PhotoViewer.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new StickerPreviewViewer();
                }
            }
        }
        return localInstance;
    }

    public void setParentActivity(Activity activity) {
        if (parentActivity == activity) {
            return;
        }
        parentActivity = activity;

        windowView = new FrameLayout(activity);
        windowView.setFocusable(true);
        windowView.setFocusableInTouchMode(true);

        containerView = new FrameLayoutDrawer(activity);
        containerView.setFocusable(false);
        windowView.addView(containerView);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) containerView.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        containerView.setLayoutParams(layoutParams);
        containerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_POINTER_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    close();
                }
                return true;
            }
        });

        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.gravity = Gravity.TOP;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        if (Build.VERSION.SDK_INT >= 21) {
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        } else {
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        centerImage.setAspectFit(true);
        centerImage.setInvalidateAll(true);
        centerImage.setParentView(containerView);
    }

    public void setKeyboardHeight(int height) {
        keyboardHeight = height;
    }

    public void open(TLRPC.Document sticker) {
        if (parentActivity == null || sticker == null) {
            return;
        }

        centerImage.setImage(sticker, null, sticker.thumb.location, null, "webp", true);
        currentSticker = sticker;
        containerView.invalidate();

        if (!isVisible) {
            AndroidUtilities.lockOrientation(parentActivity);
            try {
                if (windowView.getParent() != null) {
                    WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                    wm.removeView(windowView);
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
            wm.addView(windowView, windowLayoutParams);
            isVisible = true;
            showProgress = 0.0f;
            lastUpdateTime = System.currentTimeMillis();
        }
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void close() {
        if (parentActivity == null) {
            return;
        }
        showProgress = 1.0f;
        currentSticker = null;
        isVisible = false;
        AndroidUtilities.unlockOrientation(parentActivity);
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                centerImage.setImageBitmap((Bitmap)null);
            }
        });
        try {
            if (windowView.getParent() != null) {
                WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                wm.removeView(windowView);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public void destroy() {
        isVisible = false;
        currentSticker = null;
        if (parentActivity == null || windowView == null) {
            return;
        }
        try {
            if (windowView.getParent() != null) {
                WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                wm.removeViewImmediate(windowView);
            }
            windowView = null;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        Instance = null;
    }

    private void onDraw(Canvas canvas) {
        backgroundDrawable.setAlpha((int) (180 * showProgress));
        backgroundDrawable.setBounds(0, 0, containerView.getWidth(), containerView.getHeight());
        backgroundDrawable.draw(canvas);

        canvas.save();
        int size = (int) (Math.min(containerView.getWidth(), containerView.getHeight()) / 1.8f);
        canvas.translate(containerView.getWidth() / 2, Math.max(size / 2 + AndroidUtilities.statusBarHeight, (containerView.getHeight() - keyboardHeight) / 2));
        Bitmap bitmap = centerImage.getBitmap();
        if (bitmap != null) {
            float scale = 0.8f * showProgress / 0.8f;
            size = (int) (size * scale);
            centerImage.setAlpha(showProgress);
            centerImage.setImageCoords(-size / 2, -size / 2, size, size);
            centerImage.draw(canvas);
        }
        canvas.restore();
        if (showProgress != 1) {
            long newTime = System.currentTimeMillis();
            long dt = newTime - lastUpdateTime;
            lastUpdateTime = newTime;
            showProgress += dt / 150.0f;
            containerView.invalidate();
            if (showProgress > 1.0f) {
                showProgress = 1.0f;
            }
        }
    }
}
