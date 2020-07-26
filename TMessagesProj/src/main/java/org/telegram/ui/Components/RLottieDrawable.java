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
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueuePool;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RLottieDrawable extends BitmapDrawable implements Animatable {

    public static native long create(String src, int w, int h, int[] params, boolean precache, int[] colorReplacement, boolean limitFps);
    private static native long createWithJson(String json, String name, int[] params, int[] colorReplacement);
    public static native void destroy(long ptr);
    private static native void setLayerColor(long ptr, String layer, int color);
    private static native void replaceColors(long ptr, int[] colorReplacement);
    public static native int getFrame(long ptr, int frame, Bitmap bitmap, int w, int h, int stride);
    private static native void createCache(long ptr, int w, int h);

    private int width;
    private int height;
    private final int[] metaData = new int[3];
    private int timeBetweenFrames;
    private int customEndFrame = -1;
    private boolean playInDirectionOfCustomEndFrame;
    private int[] newReplaceColors;
    private int[] pendingReplaceColors;
    private HashMap<String, Integer> newColorUpdates = new HashMap<>();
    private volatile HashMap<String, Integer> pendingColorUpdates = new HashMap<>();
    private HashMap<Integer, Integer> vibrationPattern;

    private WeakReference<Runnable> frameReadyCallback;
    private WeakReference<Runnable> onFinishCallback;
    private int finishFrame;

    private View currentParentView;

    private int isDice;
    private int diceSwitchFramesCount = -1;

    private int autoRepeat = 1;
    private int autoRepeatPlayCount;

    private long lastFrameTime;
    private volatile boolean nextFrameIsLast;

    private Runnable cacheGenerateTask;
    private Runnable loadFrameTask;
    private volatile Bitmap renderingBitmap;
    private volatile Bitmap nextRenderingBitmap;
    private volatile Bitmap backgroundBitmap;
    private boolean waitingForNextTask;

    private CountDownLatch frameWaitSync;

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
    private volatile long secondNativePtr;
    private boolean loadingInBackground;
    private boolean secondLoadingInBackground;
    private boolean destroyAfterLoading;
    private int secondFramesCount;
    private volatile boolean setLastFrame;

    private boolean invalidateOnProgressSet;
    private boolean isInvalid;
    private boolean doNotRemoveInvalidOnFrameReady;

    private static ThreadLocal<byte[]> readBufferLocal = new ThreadLocal<>();
    private static ThreadLocal<byte[]> bufferLocal = new ThreadLocal<>();

    private ArrayList<WeakReference<View>> parentViews = new ArrayList<>();
    private static DispatchQueuePool loadFrameRunnableQueue = new DispatchQueuePool(4);
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
                if (secondNativePtr != 0) {
                    destroy(secondNativePtr);
                    secondNativePtr = 0;
                }
            }
        }
        if (nativePtr == 0 && secondNativePtr == 0) {
            recycleResources();
            return;
        }
        waitingForNextTask = true;
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

    public void setOnFinishCallback(Runnable callback, int frame) {
        if (callback != null) {
            onFinishCallback = new WeakReference<>(callback);
            finishFrame = frame;
        } else if (onFinishCallback != null) {
            onFinishCallback = null;
        }
    }

    private Runnable loadFrameRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRecycled) {
                return;
            }
            if (nativePtr == 0 || isDice == 2 && secondNativePtr == 0) {
                if (frameWaitSync != null) {
                    frameWaitSync.countDown();
                }
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
                    long ptrToUse;
                    if (isDice == 1) {
                        ptrToUse = nativePtr;
                    } else if (isDice == 2) {
                        ptrToUse = secondNativePtr;
                        if (setLastFrame) {
                            currentFrame = secondFramesCount - 1;
                        }
                    } else {
                        ptrToUse = nativePtr;
                    }
                    int result = getFrame(ptrToUse, currentFrame, backgroundBitmap, width, height, backgroundBitmap.getRowBytes());
                    if (result == -1) {
                        uiHandler.post(uiRunnableNoFrame);
                        if (frameWaitSync != null) {
                            frameWaitSync.countDown();
                        }
                        return;
                    }
                    if (metaData[2] != 0) {
                        uiHandler.post(uiRunnableGenerateCache);
                        metaData[2] = 0;
                    }
                    nextRenderingBitmap = backgroundBitmap;
                    int framesPerUpdates = shouldLimitFps ? 2 : 1;
                    if (isDice == 1) {
                        if (currentFrame + framesPerUpdates < (diceSwitchFramesCount == -1 ? metaData[0] : diceSwitchFramesCount)) {
                            currentFrame += framesPerUpdates;
                        } else {
                            currentFrame = 0;
                            nextFrameIsLast = false;
                            if (secondNativePtr != 0) {
                                isDice = 2;
                            }
                        }
                    } else if (isDice == 2) {
                        if (currentFrame + framesPerUpdates < secondFramesCount) {
                            currentFrame += framesPerUpdates;
                        } else {
                            nextFrameIsLast = true;
                            autoRepeatPlayCount++;
                        }
                    } else {
                        if (customEndFrame > 0 && playInDirectionOfCustomEndFrame) {
                            if (currentFrame > customEndFrame) {
                                if (currentFrame - framesPerUpdates > customEndFrame) {
                                    currentFrame -= framesPerUpdates;
                                    nextFrameIsLast = false;
                                } else {
                                    nextFrameIsLast = true;
                                }
                            } else {
                                if (currentFrame + framesPerUpdates < customEndFrame) {
                                    currentFrame += framesPerUpdates;
                                    nextFrameIsLast = false;
                                } else {
                                    nextFrameIsLast = true;
                                }
                            }
                        } else {
                            if (currentFrame + framesPerUpdates < (customEndFrame > 0 ? customEndFrame : metaData[0])) {
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
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            uiHandler.post(uiRunnable);
            if (frameWaitSync != null) {
                frameWaitSync.countDown();
            }
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

    public RLottieDrawable(String diceEmoji, int w, int h) {
        width = w;
        height = h;
        isDice = 1;
        String jsonString;
        if ("\uD83C\uDFB2".equals(diceEmoji)) {
            jsonString = readRes(null, R.raw.diceloop);
            diceSwitchFramesCount = 60;
        } else if ("\uD83C\uDFAF".equals(diceEmoji)) {
            jsonString = readRes(null, R.raw.dartloop);
        } else {
            jsonString = null;
        }
        getPaint().setFlags(Paint.FILTER_BITMAP_FLAG);
        if (TextUtils.isEmpty(jsonString)) {
            timeBetweenFrames = 16;
            return;
        }
        nativePtr = createWithJson(jsonString, "dice", metaData, null);
        timeBetweenFrames = Math.max(16, (int) (1000.0f / metaData[1]));
    }

    public boolean isDice() {
        return isDice != 0;
    }

    public boolean setBaseDice(File path) {
        if (nativePtr != 0 || loadingInBackground) {
            return true;
        }
        String jsonString = readRes(path, 0);
        if (TextUtils.isEmpty(jsonString)) {
            return false;
        }
        loadingInBackground = true;
        Utilities.globalQueue.postRunnable(() -> {
            nativePtr = createWithJson(jsonString, "dice", metaData, null);
            AndroidUtilities.runOnUIThread(() -> {
                loadingInBackground = false;
                if (!secondLoadingInBackground && destroyAfterLoading) {
                    recycle();
                    return;
                }
                timeBetweenFrames = Math.max(16, (int) (1000.0f / metaData[1]));
                if (isRunning) {
                    scheduleNextGetFrame();
                    invalidateInternal();
                }
            });
        });

        return true;
    }

    public boolean hasBaseDice() {
        return nativePtr != 0 || loadingInBackground;
    }

    public boolean setDiceNumber(File path, boolean instant) {
        if (secondNativePtr != 0 || secondLoadingInBackground) {
            return true;
        }
        String jsonString = readRes(path, 0);
        if (TextUtils.isEmpty(jsonString)) {
            return false;
        }
        if (instant && nextRenderingBitmap == null && renderingBitmap == null && loadFrameTask == null) {
            isDice = 2;
            setLastFrame = true;
        }
        secondLoadingInBackground = true;
        Utilities.globalQueue.postRunnable(() -> {
            if (destroyAfterLoading) {
                AndroidUtilities.runOnUIThread(() -> {
                    secondLoadingInBackground = false;
                    if (!loadingInBackground && destroyAfterLoading) {
                        recycle();
                    }
                });
                return;
            }
            int[] metaData2 = new int[3];
            secondNativePtr = createWithJson(jsonString, "dice", metaData2, null);
            AndroidUtilities.runOnUIThread(() -> {
                secondLoadingInBackground = false;
                if (!secondLoadingInBackground && destroyAfterLoading) {
                    recycle();
                    return;
                }
                secondFramesCount = metaData2[0];
                timeBetweenFrames = Math.max(16, (int) (1000.0f / metaData2[1]));
                if (isRunning) {
                    scheduleNextGetFrame();
                    invalidateInternal();
                }
            });
        });
        return true;
    }

    public RLottieDrawable(int rawRes, String name, int w, int h, boolean startDecode, int[] colorReplacement) {
        width = w;
        height = h;
        autoRepeat = 0;
        String jsonString = readRes(null, rawRes);
        if (TextUtils.isEmpty(jsonString)) {
            return;
        }
        getPaint().setFlags(Paint.FILTER_BITMAP_FLAG);
        nativePtr = createWithJson(jsonString, name, metaData, colorReplacement);
        timeBetweenFrames = Math.max(16, (int) (1000.0f / metaData[1]));
        if (startDecode) {
            setAllowDecodeSingleFrame(true);
        }
    }

    private String readRes(File path, int rawRes) {
        int totalRead = 0;
        byte[] readBuffer = readBufferLocal.get();
        if (readBuffer == null) {
            readBuffer = new byte[64 * 1024];
            readBufferLocal.set(readBuffer);
        }
        InputStream inputStream = null;
        try {
            if (path != null) {
                inputStream = new FileInputStream(path);
            } else {
                inputStream = ApplicationLoader.applicationContext.getResources().openRawResource(rawRes);
            }
            int readLen;
            byte[] buffer = bufferLocal.get();
            if (buffer == null) {
                buffer = new byte[4096];
                bufferLocal.set(buffer);
            }
            while ((readLen = inputStream.read(buffer, 0, buffer.length)) >= 0) {
                if (readBuffer.length < totalRead + readLen) {
                    byte[] newBuffer = new byte[readBuffer.length * 2];
                    System.arraycopy(readBuffer, 0, newBuffer, 0, totalRead);
                    readBuffer = newBuffer;
                    readBufferLocal.set(readBuffer);
                }
                if (readLen > 0) {
                    System.arraycopy(buffer, 0, readBuffer, totalRead, readLen);
                    totalRead += readLen;
                }
            }
        } catch (Throwable e) {
            return null;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Throwable ignore) {

            }
        }

        return new String(readBuffer, 0, totalRead);
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public long getDuration() {
        return (long) (metaData[0] / (float) metaData[1] * 1000);
    }

    public void setPlayInDirectionOfCustomEndFrame(boolean value) {
        playInDirectionOfCustomEndFrame = value;
    }

    public void setCustomEndFrame(int frame) {
        if (customEndFrame > metaData[0]) {
            return;
        }
        customEndFrame = frame;
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
        if (loadingInBackground || secondLoadingInBackground) {
            destroyAfterLoading = true;
        } else if (loadFrameTask == null && cacheGenerateTask == null) {
            if (nativePtr != 0) {
                destroy(nativePtr);
                nativePtr = 0;
            }
            if (secondNativePtr != 0) {
                destroy(secondNativePtr);
                secondNativePtr = 0;
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
        if (invalidateOnProgressSet) {
            isInvalid = true;
            if (loadFrameTask != null) {
                doNotRemoveInvalidOnFrameReady = true;
            }
        }
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
        if (loadFrameTask != null || nextRenderingBitmap != null || nativePtr == 0 || loadingInBackground || destroyWhenDone || !isRunning && (!decodeSingleFrame || decodeSingleFrame && singleFrameDecoded)) {
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

    public void setCurrentFrame(int frame) {
        setCurrentFrame(frame, true);
    }

    public void setCurrentFrame(int frame, boolean async) {
        setCurrentFrame(frame, true, false);
    }

    public void setCurrentFrame(int frame, boolean async, boolean resetFrame) {
        if (frame < 0 || frame > metaData[0]) {
            return;
        }
        currentFrame = frame;
        nextFrameIsLast = false;
        singleFrameDecoded = false;
        if (invalidateOnProgressSet) {
            isInvalid = true;
            if (loadFrameTask != null) {
                doNotRemoveInvalidOnFrameReady = true;
            }
        }
        if ((!async || resetFrame) && waitingForNextTask && nextRenderingBitmap != null) {
            backgroundBitmap = nextRenderingBitmap;
            nextRenderingBitmap = null;
            loadFrameTask = null;
            waitingForNextTask = false;
        }
        if (!async) {
            if (loadFrameTask == null) {
                frameWaitSync = new CountDownLatch(1);
            }
        }
        if (scheduleNextGetFrame()) {
            if (!async) {
                try {
                    frameWaitSync.await();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                frameWaitSync = null;
            }
        } else {
            forceFrameRedraw = true;
        }
        invalidateSelf();
    }

    public void setProgressMs(long ms) {
        int frameNum = (int) ((Math.max(0, ms) / timeBetweenFrames) % metaData[0]);
        setCurrentFrame(frameNum, true, true);
    }

    public void setProgress(float progress) {
        setProgress(progress, true);
    }

    public void setProgress(float progress, boolean async) {
        if (progress < 0.0f) {
            progress = 0.0f;
        } else if (progress > 1.0f) {
            progress = 1.0f;
        }
        setCurrentFrame((int) (metaData[0] * progress), async);
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

    private void setCurrentFrame(long now, long timeDiff, long timeCheck, boolean force) {
        backgroundBitmap = renderingBitmap;
        renderingBitmap = nextRenderingBitmap;
        nextRenderingBitmap = null;
        if (isDice == 2) {
            if (onFinishCallback != null && currentFrame - 1 >= finishFrame) {
                Runnable runnable = onFinishCallback.get();
                if (runnable != null) {
                    runnable.run();
                }
                onFinishCallback = null;
            }
        }
        if (nextFrameIsLast) {
            stop();
        }
        loadFrameTask = null;
        if (doNotRemoveInvalidOnFrameReady) {
            doNotRemoveInvalidOnFrameReady = false;
        } else if (isInvalid) {
            isInvalid = false;
        }
        singleFrameDecoded = true;
        waitingForNextTask = false;
        if (AndroidUtilities.screenRefreshRate <= 60) {
            lastFrameTime = now;
        } else {
            lastFrameTime = now - Math.min(16, timeDiff - timeCheck);
        }
        if (force && forceFrameRedraw) {
            singleFrameDecoded = false;
            forceFrameRedraw = false;
        }
        if (isDice == 0) {
            if (onFinishCallback != null && currentFrame >= finishFrame) {
                Runnable runnable = onFinishCallback.get();
                if (runnable != null) {
                    runnable.run();
                }
            }
        }
        scheduleNextGetFrame();
    }

    @Override
    public void draw(Canvas canvas) {
        if (nativePtr == 0 || destroyWhenDone) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
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
                setCurrentFrame(now, timeDiff, timeCheck, false);
            }
        } else if ((forceFrameRedraw || decodeSingleFrame && timeDiff >= timeCheck) && nextRenderingBitmap != null) {
            setCurrentFrame(now, timeDiff, timeCheck, true);
        }

        if (!isInvalid && renderingBitmap != null) {
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
        return nativePtr != 0 && (renderingBitmap != null || nextRenderingBitmap != null) && !isInvalid;
    }

    public void setInvalidateOnProgressSet(boolean value) {
        invalidateOnProgressSet = value;
    }
}
