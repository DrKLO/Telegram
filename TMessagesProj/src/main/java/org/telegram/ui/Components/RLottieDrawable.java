/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RLottieDrawable extends BitmapDrawable implements Animatable {

    private static native long create(String src, int w, int h, int[] params, boolean precache, int[] colorReplacement, boolean limitFps);
    private static native long createWithJson(String json, String name, int[] params, int[] colorReplacement);
    private static native void destroy(long ptr);
    private static native void setLayerColor(long ptr, String layer, int color);
    private static native void replaceColors(long ptr, int[] colorReplacement);
    private static native int getFrame(long ptr, int frame, Bitmap bitmap, int w, int h, int stride);
    private static native void createCache(long ptr, int w, int h);

    private int width;
    private int height;
    private final int[] metaData = new int[3];
    private int timeBetweenFrames;
    private int[] newReplaceColors;
    private int[] pendingReplaceColors;
    private HashMap<String, Integer> newColorUpdates = new HashMap<>();
    private volatile HashMap<String, Integer> pendingColorUpdates = new HashMap<>();
    private HashMap<Integer, Integer> vibrationPattern;

    private View currentParentView;

    private int autoRepeat = 1;
    private int autoRepeatPlayCount;

    private long lastFrameTime;
    private volatile boolean nextFrameIsLast;

    private Runnable cacheGenerateTask;
    private Runnable loadFrameTask;
    private volatile Bitmap renderingBitmap;
    private volatile Bitmap nextRenderingBitmap;
    private volatile Bitmap backgroundBitmap;

    private boolean destroyWhenDone;
    private boolean decodeSingleFrame;
    private boolean singleFrameDecoded;
    private boolean forceFrameRedraw;
    private boolean applyingLayerColors;
    private int currentFrame;
    private boolean shouldLimitFps;

    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private boolean applyTransformation;
    private final Rect dstRect = new Rect();
    private static final Handler uiHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isRunning;
    private volatile boolean isRecycled;
    private volatile long nativePtr;

    private static byte[] readBuffer = new byte[64 * 1024];
    private static byte[] buffer = new byte[4096];

    private ArrayList<WeakReference<View>> parentViews = new ArrayList<>();
    private static ExecutorService loadFrameRunnableQueue = Executors.newCachedThreadPool();
    private static ThreadPoolExecutor lottieCacheGenerateQueue;

    private Runnable uiRunnableNoFrame = new Runnable() {
        @Override
        public void run() {
            loadFrameTask = null;
            decodeFrameFinishedInternal();
        }
    };

    private Runnable uiRunnableCacheFinished = new Runnable() {
        @Override
        public void run() {
            cacheGenerateTask = null;
            decodeFrameFinishedInternal();
        }
    };

    private Runnable uiRunnable = new Runnable() {
        @Override
        public void run() {
            singleFrameDecoded = true;
            invalidateInternal();
            decodeFrameFinishedInternal();
        }
    };

    private Runnable uiRunnableLastFrame = new Runnable() {
        @Override
        public void run() {
            singleFrameDecoded = true;
            isRunning = false;
            invalidateInternal();
            decodeFrameFinishedInternal();
        }
    };

    private Runnable uiRunnableGenerateCache = new Runnable() {
        @Override
        public void run() {
            if (!isRecycled && !destroyWhenDone && nativePtr != 0) {
                lottieCacheGenerateQueue.execute(cacheGenerateTask = () -> {
                    if (cacheGenerateTask == null) {
                        return;
                    }
                    createCache(nativePtr, width, height);
                    uiHandler.post(uiRunnableCacheFinished);
                });
            }
            decodeFrameFinishedInternal();
        }
    };

    private void checkRunningTasks() {
        if (cacheGenerateTask != null) {
            if (lottieCacheGenerateQueue.remove(cacheGenerateTask)) {
                cacheGenerateTask = null;
            }
        }
        if (!hasParentView() && nextRenderingBitmap != null && loadFrameTask != null) {
            loadFrameTask = null;
            nextRenderingBitmap = null;
        }
    }

    private void decodeFrameFinishedInternal() {
        if (destroyWhenDone) {
            checkRunningTasks();
            if (loadFrameTask == null && cacheGenerateTask == null && nativePtr != 0) {
                destroy(nativePtr);
                nativePtr = 0;
            }
        }
        if (nativePtr == 0) {
            recycleResources();
            return;
        }
        if (!hasParentView()) {
            stop();
        }
        scheduleNextGetFrame();
    }

    private void recycleResources() {
        if (renderingBitmap != null) {
            renderingBitmap.recycle();
            renderingBitmap = null;
        }
        if (backgroundBitmap != null) {
            backgroundBitmap.recycle();
            backgroundBitmap = null;
        }
    }

    private Runnable loadFrameRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRecycled) {
                return;
            }
            if (nativePtr == 0) {
                uiHandler.post(uiRunnableNoFrame);
                return;
            }
            if (backgroundBitmap == null) {
                try {
                    backgroundBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            if (backgroundBitmap != null) {
                try {
                    if (!pendingColorUpdates.isEmpty()) {
                        for (HashMap.Entry<String, Integer> entry : pendingColorUpdates.entrySet()) {
                            setLayerColor(nativePtr, entry.getKey(), entry.getValue());
                        }
                        pendingColorUpdates.clear();
                    }
                } catch (Exception ignore) {

                }
                if (pendingReplaceColors != null) {
                    replaceColors(nativePtr, pendingReplaceColors);
                    pendingReplaceColors = null;
                }
                try {
                    int result = getFrame(nativePtr, currentFrame, backgroundBitmap, width, height, backgroundBitmap.getRowBytes());
                    if (result == -1) {
                        uiHandler.post(uiRunnableNoFrame);
                        return;
                    }
                    if (metaData[2] != 0) {
                        uiHandler.post(uiRunnableGenerateCache);
                        metaData[2] = 0;
                    }
                    nextRenderingBitmap = backgroundBitmap;
                    int framesPerUpdates = shouldLimitFps ? 2 : 1;
                    if (currentFrame + framesPerUpdates < metaData[0]) {
                        if (autoRepeat == 3) {
                            nextFrameIsLast = true;
                            autoRepeatPlayCount++;
                        } else {
                            currentFrame += framesPerUpdates;
                            nextFrameIsLast = false;
                        }
                    } else if (autoRepeat == 1) {
                        currentFrame = 0;
                        nextFrameIsLast = false;
                    } else if (autoRepeat == 2) {
                        currentFrame = 0;
                        nextFrameIsLast = true;
                        autoRepeatPlayCount++;
                    } else {
                        nextFrameIsLast = true;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            uiHandler.post(uiRunnable);
        }
    };

    public RLottieDrawable(File file, int w, int h, boolean precache, boolean limitFps) {
        this(file, w, h, precache, limitFps, null);
    }

    public RLottieDrawable(File file, int w, int h, boolean precache, boolean limitFps, int[] colorReplacement) {
        width = w;
        height = h;
        shouldLimitFps = limitFps;
        getPaint().setFlags(Paint.FILTER_BITMAP_FLAG);

        nativePtr = create(file.getAbsolutePath(), w, h, metaData, precache, colorReplacement, shouldLimitFps);
        if (precache && lottieCacheGenerateQueue == null) {
            lottieCacheGenerateQueue = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        }
        if (nativePtr == 0) {
            file.delete();
        }
        if (shouldLimitFps && metaData[1] < 60) {
            shouldLimitFps = false;
        }
        timeBetweenFrames = Math.max(shouldLimitFps ? 33 : 16, (int) (1000.0f / metaData[1]));
    }

    public RLottieDrawable(int rawRes, String name, int w, int h) {
        this(rawRes, name, w, h, true, null);
    }

    public RLottieDrawable(int rawRes, String name, int w, int h, boolean startDecode, int[] colorReplacement) {
        try {
            InputStream inputStream = ApplicationLoader.applicationContext.getResources().openRawResource(rawRes);
            int readLen;
            int totalRead = 0;
            while ((readLen = inputStream.read(buffer, 0, buffer.length)) > 0) {
                if (readBuffer.length < totalRead + readLen) {
                    byte[] newBuffer = new byte[readBuffer.length * 2];
                    System.arraycopy(readBuffer, 0, newBuffer, 0, totalRead);
                    readBuffer = newBuffer;
                }
                System.arraycopy(buffer, 0, readBuffer, totalRead, readLen);
                totalRead += readLen;
            }
            String jsonString = new String(readBuffer, 0, totalRead);
            inputStream.close();

            width = w;
            height = h;
            getPaint().setFlags(Paint.FILTER_BITMAP_FLAG);
            nativePtr = createWithJson(jsonString, name, metaData, colorReplacement);
            timeBetweenFrames = Math.max(16, (int) (1000.0f / metaData[1]));
            autoRepeat = 0;
            if (startDecode) {
                setAllowDecodeSingleFrame(true);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public void addParentView(View view) {
        if (view == null) {
            return;
        }
        for (int a = 0, N = parentViews.size(); a < N; a++) {
            if (parentViews.get(a).get() == view) {
                return;
            } else if (parentViews.get(a).get() == null) {
                parentViews.remove(a);
                N--;
                a--;
            }
        }
        parentViews.add(0, new WeakReference<>(view));
    }

    public void removeParentView(View view) {
        if (view == null) {
            return;
        }
        for (int a = 0, N = parentViews.size(); a < N; a++) {
            View v = parentViews.get(a).get();
            if (v == view || v == null) {
                parentViews.remove(a);
                N--;
                a--;
            }
        }
    }

    private boolean hasParentView() {
        if (getCallback() != null) {
            return true;
        }
        for (int a = 0, N = parentViews.size(); a < N; a++) {
            View view = parentViews.get(a).get();
            if (view != null) {
                return true;
            } else {
                parentViews.remove(a);
                N--;
                a--;
            }
        }
        return false;
    }

    private void invalidateInternal() {
        for (int a = 0, N = parentViews.size(); a < N; a++) {
            View view = parentViews.get(a).get();
            if (view != null) {
                view.invalidate();
            } else {
                parentViews.remove(a);
                N--;
                a--;
            }
        }
        if (getCallback() != null) {
            invalidateSelf();
        }
    }

    public void setAllowDecodeSingleFrame(boolean value) {
        decodeSingleFrame = value;
        if (decodeSingleFrame) {
            scheduleNextGetFrame();
        }
    }

    public void recycle() {
        isRunning = false;
        isRecycled = true;
        checkRunningTasks();
        if (loadFrameTask == null && cacheGenerateTask == null) {
            if (nativePtr != 0) {
                destroy(nativePtr);
                nativePtr = 0;
            }
            recycleResources();
        } else {
            destroyWhenDone = true;
        }
    }

    public void setAutoRepeat(int value) {
        if (autoRepeat == 2 && value == 3 && currentFrame != 0) {
            return;
        }
        autoRepeat = value;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            recycle();
        } finally {
            super.finalize();
        }
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public void start() {
        if (isRunning || autoRepeat >= 2 && autoRepeatPlayCount != 0) {
            return;
        }
        isRunning = true;
        scheduleNextGetFrame();
        invalidateInternal();
    }

    public boolean restart() {
        if (autoRepeat < 2 || autoRepeatPlayCount == 0) {
            return false;
        }
        autoRepeatPlayCount = 0;
        autoRepeat = 2;
        start();
        return true;
    }

    public void setVibrationPattern(HashMap<Integer, Integer> pattern) {
        vibrationPattern = pattern;
    }

    public void beginApplyLayerColors() {
        applyingLayerColors = true;
    }

    public void commitApplyLayerColors() {
        if (!applyingLayerColors) {
            return;
        }
        applyingLayerColors = false;
        if (!isRunning && decodeSingleFrame) {
            if (currentFrame <= 2) {
                currentFrame = 0;
            }
            nextFrameIsLast = false;
            singleFrameDecoded = false;
            if (!scheduleNextGetFrame()) {
                forceFrameRedraw = true;
            }
        }
        invalidateInternal();
    }

    public void replaceColors(int[] colors) {
        newReplaceColors = colors;
        requestRedrawColors();
    }

    public void setLayerColor(String layerName, int color) {
        newColorUpdates.put(layerName, color);
        requestRedrawColors();
    }

    private void requestRedrawColors() {
        if (!applyingLayerColors && !isRunning && decodeSingleFrame) {
            if (currentFrame <= 2) {
                currentFrame = 0;
            }
            nextFrameIsLast = false;
            singleFrameDecoded = false;
            if (!scheduleNextGetFrame()) {
                forceFrameRedraw = true;
            }
        }
        invalidateInternal();
    }

    private boolean scheduleNextGetFrame() {
        if (loadFrameTask != null || nextRenderingBitmap != null || nativePtr == 0 || destroyWhenDone || !isRunning && (!decodeSingleFrame || decodeSingleFrame && singleFrameDecoded)) {
            return false;
        }
        if (!newColorUpdates.isEmpty()) {
            pendingColorUpdates.putAll(newColorUpdates);
            newColorUpdates.clear();
        }
        if (newReplaceColors != null) {
            pendingReplaceColors = newReplaceColors;
            newReplaceColors = null;
        }
        loadFrameRunnableQueue.execute(loadFrameTask = loadFrameRunnable);
        return true;
    }

    @Override
    public void stop() {
        isRunning = false;
    }

    public void setProgress(float progress) {
        if (progress < 0.0f) {
            progress = 0.0f;
        } else if (progress > 1.0f) {
            progress = 1.0f;
        }
        currentFrame = (int) (metaData[0] * progress);
        nextFrameIsLast = false;
        singleFrameDecoded = false;
        if (!scheduleNextGetFrame()) {
            forceFrameRedraw = true;
        }
        invalidateSelf();
    }

    public void setCurrentParentView(View view) {
        currentParentView = view;
    }

    private boolean isCurrentParentViewMaster() {
        if (getCallback() != null) {
            return true;
        }
        for (int a = 0, N = parentViews.size(); a < N; a++) {
            if (parentViews.get(a).get() == null) {
                parentViews.remove(a);
                N--;
                a--;
                continue;
            }
            return parentViews.get(a).get() == currentParentView;
        }
        return true;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public int getIntrinsicHeight() {
        return height;
    }

    @Override
    public int getIntrinsicWidth() {
        return width;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        applyTransformation = true;
    }

    @Override
    public void draw(Canvas canvas) {
        if (nativePtr == 0 || destroyWhenDone) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long timeDiff = Math.abs(now - lastFrameTime);
        int timeCheck;
        if (AndroidUtilities.screenRefreshRate <= 60) {
            timeCheck = timeBetweenFrames - 6;
        } else {
            timeCheck = timeBetweenFrames;
        }
        if (isRunning) {
            if (renderingBitmap == null && nextRenderingBitmap == null) {
                scheduleNextGetFrame();
            } else if (nextRenderingBitmap != null && (renderingBitmap == null || timeDiff >= timeCheck) && isCurrentParentViewMaster()) {
                if (vibrationPattern != null && currentParentView != null) {
                    Integer force = vibrationPattern.get(currentFrame - 1);
                    if (force != null) {
                        currentParentView.performHapticFeedback(force == 1 ? HapticFeedbackConstants.LONG_PRESS : HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    }
                }
                backgroundBitmap = renderingBitmap;
                renderingBitmap = nextRenderingBitmap;
                if (nextFrameIsLast) {
                    stop();
                }
                loadFrameTask = null;
                singleFrameDecoded = true;
                nextRenderingBitmap = null;
                if (AndroidUtilities.screenRefreshRate <= 60) {
                    lastFrameTime = now;
                } else {
                    lastFrameTime = now - Math.min(16, timeDiff - timeCheck);
                }
                scheduleNextGetFrame();
            }
        } else if ((forceFrameRedraw || decodeSingleFrame && timeDiff >= timeCheck) && nextRenderingBitmap != null) {
            backgroundBitmap = renderingBitmap;
            renderingBitmap = nextRenderingBitmap;
            loadFrameTask = null;
            singleFrameDecoded = true;
            nextRenderingBitmap = null;
            if (AndroidUtilities.screenRefreshRate <= 60) {
                lastFrameTime = now;
            } else {
                lastFrameTime = now - Math.min(16, timeDiff - timeCheck);
            }
            if (forceFrameRedraw) {
                singleFrameDecoded = false;
                forceFrameRedraw = false;
            }
            scheduleNextGetFrame();
        }

        if (renderingBitmap != null) {
            if (applyTransformation) {
                dstRect.set(getBounds());
                scaleX = (float) dstRect.width() / width;
                scaleY = (float) dstRect.height() / height;
                applyTransformation = false;
            }
            canvas.save();
            canvas.translate(dstRect.left, dstRect.top);
            canvas.scale(scaleX, scaleY);
            canvas.drawBitmap(renderingBitmap, 0, 0, getPaint());
            if (isRunning) {
                invalidateInternal();
            }
            canvas.restore();
        }
    }

    @Override
    public int getMinimumHeight() {
        return height;
    }

    @Override
    public int getMinimumWidth() {
        return width;
    }

    public Bitmap getRenderingBitmap() {
        return renderingBitmap;
    }

    public Bitmap getNextRenderingBitmap() {
        return nextRenderingBitmap;
    }

    public Bitmap getBackgroundBitmap() {
        return backgroundBitmap;
    }

    public Bitmap getAnimatedBitmap() {
        if (renderingBitmap != null) {
            return renderingBitmap;
        } else if (nextRenderingBitmap != null) {
            return nextRenderingBitmap;
        }
        return null;
    }

    public boolean hasBitmap() {
        return nativePtr != 0 && (renderingBitmap != null || nextRenderingBitmap != null);
    }
}
