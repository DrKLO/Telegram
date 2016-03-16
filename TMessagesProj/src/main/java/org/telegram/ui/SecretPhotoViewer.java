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
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.util.ArrayList;

public class SecretPhotoViewer implements NotificationCenter.NotificationCenterDelegate {

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

    private class SecretDeleteTimer extends FrameLayout {
        private String currentInfoString;
        private int infoWidth;
        private TextPaint infoPaint = null;
        private StaticLayout infoLayout = null;
        private Paint deleteProgressPaint;
        private RectF deleteProgressRect = new RectF();
        private Drawable drawable = null;

        public SecretDeleteTimer(Context context) {
            super(context);
            setWillNotDraw(false);

            infoPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            infoPaint.setTextSize(AndroidUtilities.dp(15));
            infoPaint.setColor(0xffffffff);

            deleteProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            deleteProgressPaint.setColor(0xffe6e6e6);

            drawable = getResources().getDrawable(R.drawable.circle1);
        }

        private void updateSecretTimeText() {
            if (currentMessageObject == null) {
                return;
            }
            String str = currentMessageObject.getSecretTimeString();
            if (str == null) {
                return;
            }
            if (currentInfoString == null || !currentInfoString.equals(str)) {
                currentInfoString = str;
                infoWidth = (int)Math.ceil(infoPaint.measureText(currentInfoString));
                CharSequence str2 = TextUtils.ellipsize(currentInfoString, infoPaint, infoWidth, TextUtils.TruncateAt.END);
                infoLayout = new StaticLayout(str2, infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                invalidate();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            deleteProgressRect.set(getMeasuredWidth() - AndroidUtilities.dp(30), AndroidUtilities.dp(2), getMeasuredWidth() - AndroidUtilities.dp(2), AndroidUtilities.dp(30));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentMessageObject == null || currentMessageObject.messageOwner.destroyTime == 0) {
                return;
            }

            if (drawable != null) {
                drawable.setBounds(getMeasuredWidth() - AndroidUtilities.dp(32), 0, getMeasuredWidth(), AndroidUtilities.dp(32));
                drawable.draw(canvas);
            }

            long msTime = System.currentTimeMillis() + ConnectionsManager.getInstance().getTimeDifference() * 1000;
            float progress = Math.max(0, (long)currentMessageObject.messageOwner.destroyTime * 1000 - msTime) / (currentMessageObject.messageOwner.ttl * 1000.0f);
            canvas.drawArc(deleteProgressRect, -90, -360 * progress, true, deleteProgressPaint);
            if (progress != 0) {
                int offset = AndroidUtilities.dp(2);
                invalidate((int)deleteProgressRect.left - offset, (int)deleteProgressRect.top - offset, (int)deleteProgressRect.right + offset * 2, (int)deleteProgressRect.bottom + offset * 2);
            }
            updateSecretTimeText();

            if (infoLayout != null) {
                canvas.save();
                canvas.translate(getMeasuredWidth() - AndroidUtilities.dp(38) - infoWidth, AndroidUtilities.dp(7));
                infoLayout.draw(canvas);
                canvas.restore();
            }
        }
    }

    private Activity parentActivity;
    private WindowManager.LayoutParams windowLayoutParams;
    private FrameLayout windowView;
    private FrameLayoutDrawer containerView;
    private ImageReceiver centerImage = new ImageReceiver();
    private SecretDeleteTimer secretDeleteTimer;
    private boolean isVisible = false;

    private MessageObject currentMessageObject = null;

    private static volatile SecretPhotoViewer Instance = null;
    public static SecretPhotoViewer getInstance() {
        SecretPhotoViewer localInstance = Instance;
        if (localInstance == null) {
            synchronized (PhotoViewer.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new SecretPhotoViewer();
                }
            }
        }
        return localInstance;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.messagesDeleted) {
            if (currentMessageObject == null) {
                return;
            }
            int channelId = (Integer) args[1];
            if (channelId != 0) {
                return;
            }
            ArrayList<Integer> markAsDeletedMessages = (ArrayList<Integer>)args[0];
            if (markAsDeletedMessages.contains(currentMessageObject.getId())) {
                closePhoto();
            }
        } else if (id == NotificationCenter.didCreatedNewDeleteTask) {
            if (currentMessageObject == null || secretDeleteTimer == null) {
                return;
            }
            SparseArray<ArrayList<Integer>> mids = (SparseArray<ArrayList<Integer>>)args[0];
            for(int i = 0; i < mids.size(); i++) {
                int key = mids.keyAt(i);
                ArrayList<Integer> arr = mids.get(key);
                for (Integer mid : arr) {
                    if (currentMessageObject.getId() == mid) {
                        currentMessageObject.messageOwner.destroyTime = key;
                        secretDeleteTimer.invalidate();
                        return;
                    }
                }
            }
        }
    }

    public void setParentActivity(Activity activity) {
        if (parentActivity == activity) {
            return;
        }
        parentActivity = activity;

        windowView = new FrameLayout(activity);
        windowView.setBackgroundColor(0xff000000);
        windowView.setFocusable(true);
        windowView.setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= 23) {
            windowView.setFitsSystemWindows(true); //TODO ?
        }

        containerView = new FrameLayoutDrawer(activity);
        containerView.setFocusable(false);
        windowView.addView(containerView);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)containerView.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        containerView.setLayoutParams(layoutParams);
        containerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_POINTER_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    closePhoto();
                }
                return true;
            }
        });

        secretDeleteTimer = new SecretDeleteTimer(activity);
        containerView.addView(secretDeleteTimer);
        layoutParams = (FrameLayout.LayoutParams)secretDeleteTimer.getLayoutParams();
        layoutParams.gravity = Gravity.TOP | Gravity.RIGHT;
        layoutParams.width = AndroidUtilities.dp(100);
        layoutParams.height = AndroidUtilities.dp(32);
        layoutParams.rightMargin = AndroidUtilities.dp(19);
        layoutParams.topMargin = AndroidUtilities.dp(19);
        secretDeleteTimer.setLayoutParams(layoutParams);

        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.gravity = Gravity.TOP;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        centerImage.setParentView(containerView);
    }

    public void openPhoto(MessageObject messageObject) {
        if (parentActivity == null || messageObject == null || messageObject.messageOwner.media == null || messageObject.messageOwner.media.photo == null) {
            return;
        }

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didCreatedNewDeleteTask);

        TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, AndroidUtilities.getPhotoSize());
        int size = sizeFull.size;
        if (size == 0) {
            size = -1;
        }
        BitmapDrawable drawable = ImageLoader.getInstance().getImageFromMemory(sizeFull.location, null, null);
        if (drawable == null) {
            File file = FileLoader.getPathToAttach(sizeFull);
            Bitmap bitmap = null;
            BitmapFactory.Options options = null;
            if (Build.VERSION.SDK_INT >= 14 && Build.VERSION.SDK_INT < 21) {
                options = new BitmapFactory.Options();
                options.inDither = true;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                options.inPurgeable = true;
                options.inSampleSize = 1;
                options.inMutable = true;
            }
            try {
                bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            } catch (Throwable e) {
                FileLog.e("tmessages", e);
            }
            if (bitmap != null) {
                drawable = new BitmapDrawable(bitmap);
                ImageLoader.getInstance().putImageToCache(drawable, sizeFull.location.volume_id + "_" + sizeFull.location.local_id);
            }
        }
        if (drawable != null) {
            centerImage.setImageBitmap(drawable);
        } else {
            centerImage.setImage(sizeFull.location, null, null, size, null, false);
        }

        currentMessageObject = messageObject;

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
        secretDeleteTimer.invalidate();
        isVisible = true;
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void closePhoto() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didCreatedNewDeleteTask);
        if (parentActivity == null) {
            return;
        }
        currentMessageObject = null;
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

    public void destroyPhotoViewer() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didCreatedNewDeleteTask);
        isVisible = false;
        currentMessageObject = null;
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
        canvas.save();
        canvas.translate(containerView.getWidth() / 2, containerView.getHeight() / 2);
        Bitmap bitmap = centerImage.getBitmap();
        if (bitmap != null) {
            int bitmapWidth = bitmap.getWidth();
            int bitmapHeight = bitmap.getHeight();

            float scaleX = (float) containerView.getWidth() / (float) bitmapWidth;
            float scaleY = (float) containerView.getHeight() / (float) bitmapHeight;
            float scale = scaleX > scaleY ? scaleY : scaleX;
            int width = (int) (bitmapWidth * scale);
            int height = (int) (bitmapHeight * scale);

            centerImage.setImageCoords(-width / 2, -height / 2, width, height);
            centerImage.draw(canvas);
        }
        canvas.restore();
    }
}
