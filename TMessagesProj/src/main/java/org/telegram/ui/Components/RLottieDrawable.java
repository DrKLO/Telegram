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
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;

import com.google.gson.Gson;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.DispatchQueuePool;
import org.telegram.messenger.DispatchQueuePoolBackground;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.BitmapsCache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class RLottieDrawable extends BitmapDrawable implements Animatable, BitmapsCache.Cacheable {

    public boolean skipFrameUpdate;

    public static native long create(String src, String json, int w, int h, int[] params, boolean precache, int[] colorReplacement, boolean limitFps, int fitzModifier);

    protected static native long createWithJson(String json, String name, int[] params, int[] colorReplacement);

    public static native void destroy(long ptr);

    private static native void setLayerColor(long ptr, String layer, int color);

    private static native void replaceColors(long ptr, int[] colorReplacement);

    public static native int getFrame(long ptr, int frame, Bitmap bitmap, int w, int h, int stride, boolean clear);

    protected final int width;
    protected final int height;
    protected final int[] metaData = new int[3];
    protected int timeBetweenFrames;
    protected int customEndFrame = -1;
    protected boolean playInDirectionOfCustomEndFrame;
    private int[] newReplaceColors;
    private int[] pendingReplaceColors;
    private HashMap<String, Integer> newColorUpdates = new HashMap<>();
    private volatile HashMap<String, Integer> pendingColorUpdates = new HashMap<>();
    private HashMap<Integer, Integer> vibrationPattern;
    private boolean resetVibrationAfterRestart = false;
    private boolean allowVibration = true;
    public static Gson gson;

    private WeakReference<Runnable> frameReadyCallback;
    protected WeakReference<Runnable> onFinishCallback;
    private int finishFrame;

    private View currentParentView;
    private ArrayList<ImageReceiver> parentViews = new ArrayList<>();

    protected int isDice;
    protected int diceSwitchFramesCount = -1;

    protected int autoRepeat = 1;
    protected int autoRepeatCount = -1;
    protected int autoRepeatPlayCount;
    protected long autoRepeatTimeout;

    private long lastFrameTime;
    protected volatile boolean nextFrameIsLast;

    protected Runnable cacheGenerateTask;
    protected Runnable loadFrameTask;
    protected volatile Bitmap renderingBitmap;
    protected volatile Bitmap nextRenderingBitmap;
    protected volatile Bitmap backgroundBitmap;
    protected boolean waitingForNextTask;

    protected CountDownLatch frameWaitSync;

    protected boolean destroyWhenDone;
    private boolean decodeSingleFrame;
    private boolean singleFrameDecoded;
    private boolean forceFrameRedraw;
    private boolean applyingLayerColors;
    protected int currentFrame;
    private boolean shouldLimitFps;
    private boolean createdForFirstFrame;

    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private boolean applyTransformation;
    private boolean needScale;
    private final RectF dstRect = new RectF();
    private RectF[] dstRectBackground = new RectF[DrawingInBackgroundThreadDrawable.THREAD_COUNT];
    private Paint[] backgroundPaint = new Paint[DrawingInBackgroundThreadDrawable.THREAD_COUNT];
    protected static final Handler uiHandler = new Handler(Looper.getMainLooper());
    protected volatile boolean isRunning;
    protected volatile boolean isRecycled;
    protected volatile long nativePtr;
    protected volatile long secondNativePtr;
    protected boolean loadingInBackground;
    protected boolean secondLoadingInBackground;
    protected boolean destroyAfterLoading;
    protected int secondFramesCount;
    protected volatile boolean setLastFrame;
    private boolean fallbackCache;

    private boolean invalidateOnProgressSet;
    private boolean isInvalid;
    private boolean doNotRemoveInvalidOnFrameReady;

    private static ThreadLocal<byte[]> readBufferLocal = new ThreadLocal<>();
    private static ThreadLocal<byte[]> bufferLocal = new ThreadLocal<>();

    private static final DispatchQueuePool loadFrameRunnableQueue = new DispatchQueuePool(4);
    public static DispatchQueue lottieCacheGenerateQueue;

    File file;
    boolean precache;

    private Runnable onAnimationEndListener;
    private Runnable onFrameReadyRunnable;

    private View masterParent;
    NativePtrArgs args;

    protected Runnable uiRunnableNoFrame = new Runnable() {
        @Override
        public void run() {
            loadFrameTask = null;
            decodeFrameFinishedInternal();
            if (onFrameReadyRunnable != null) {
                onFrameReadyRunnable.run();
            }
        }
    };


    protected Runnable uiRunnable = new Runnable() {
        @Override
        public void run() {
            singleFrameDecoded = true;
            invalidateInternal();
            decodeFrameFinishedInternal();
            if (onFrameReadyRunnable != null) {
                onFrameReadyRunnable.run();
            }
        }
    };

    boolean generatingCache;

    private Runnable uiRunnableGenerateCache = new Runnable() {
        @Override
        public void run() {
            if (!isRecycled && !destroyWhenDone && canLoadFrames() && cacheGenerateTask == null) {
                generatingCache = true;
                if (lottieCacheGenerateQueue == null) {
                    createCacheGenQueue();
                }
                BitmapsCache.incrementTaskCounter();
                lottieCacheGenerateQueue.postRunnable(cacheGenerateTask = () -> {
                    try {
                        BitmapsCache bitmapsCacheFinal = bitmapsCache;
                        if (bitmapsCacheFinal != null) {
                            bitmapsCacheFinal.createCache();
                        }
                    } catch (Throwable throwable) {

                    }
                    uiHandler.post(uiRunnableCacheFinished);
                });
            }
        }
    };

    private Runnable uiRunnableCacheFinished = new Runnable() {
        @Override
        public void run() {
            if (cacheGenerateTask != null) {
                BitmapsCache.decrementTaskCounter();
                cacheGenerateTask = null;
            }
            generatingCache = false;
            decodeFrameFinishedInternal();
        }
    };

    BitmapsCache bitmapsCache;
    int generateCacheFramePointer;

    public static void createCacheGenQueue() {
        lottieCacheGenerateQueue = new DispatchQueue("cache generator queue");
    }

    protected void checkRunningTasks() {
        if (cacheGenerateTask != null) {
            lottieCacheGenerateQueue.cancelRunnable(cacheGenerateTask);
            BitmapsCache.decrementTaskCounter();
            cacheGenerateTask = null;
        }
        if (!hasParentView() && nextRenderingBitmap != null && loadFrameTask != null) {
            loadFrameTask = null;
            nextRenderingBitmap = null;
        }
    }

    protected void decodeFrameFinishedInternal() {
        if (destroyWhenDone) {
            checkRunningTasks();
            if (loadFrameTask == null && cacheGenerateTask == null && nativePtr != 0) {
                recycleNativePtr(true);
            }
        }
        if ((nativePtr == 0 || fallbackCache) && secondNativePtr == 0 && bitmapsCache == null) {
            recycleResources();
            return;
        }
        waitingForNextTask = true;
        if (!hasParentView()) {
            stop();
        }
        if (isRunning) {
            scheduleNextGetFrame();
        }
    }

    private void recycleNativePtr(boolean uiThread) {
        long nativePtrFinal = nativePtr;
        long secondNativePtrFinal = secondNativePtr;

        nativePtr = 0;
        secondNativePtr = 0;
        if (nativePtrFinal != 0 || secondNativePtrFinal != 0) {
            if (uiThread) {
                DispatchQueuePoolBackground.execute(() -> {
                    if (nativePtrFinal != 0) {
                        destroy(nativePtrFinal);
                    }
                    if (secondNativePtrFinal != 0) {
                        destroy(secondNativePtrFinal);
                    }
                });
            } else {
                Utilities.globalQueue.postRunnable(() ->{
                    if (nativePtrFinal != 0) {
                        destroy(nativePtrFinal);
                    }
                    if (secondNativePtrFinal != 0) {
                        destroy(secondNativePtrFinal);
                    }
                });
            }
        }
    }

    protected void recycleResources() {
        ArrayList<Bitmap> bitmapToRecycle = new ArrayList<>();
        bitmapToRecycle.add(renderingBitmap);
        bitmapToRecycle.add(backgroundBitmap);
        bitmapToRecycle.add(nextRenderingBitmap);
        nextRenderingBitmap = null;
        renderingBitmap = null;
        backgroundBitmap = null;
        AndroidUtilities.recycleBitmaps(bitmapToRecycle);

        if (onAnimationEndListener != null) {
            onAnimationEndListener = null;
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

    private boolean genCacheSend;
    protected Runnable loadFrameRunnable = new Runnable() {
        private long lastUpdate = 0;

        @Override
        public void run() {
            if (isRecycled) {
                return;
            }
            if (!canLoadFrames() || isDice == 2 && secondNativePtr == 0) {
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
                if (pendingReplaceColors != null && nativePtr != 0) {
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
                    int result = 0;
                    int framesPerUpdates = shouldLimitFps ? 2 : 1;
                    if (precache && bitmapsCache != null) {
                        try {
                            result = bitmapsCache.getFrame(currentFrame / framesPerUpdates, backgroundBitmap);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    } else {
                        result = getFrame(ptrToUse, currentFrame, backgroundBitmap, width, height, backgroundBitmap.getRowBytes(), true);
                    }
                    if (bitmapsCache != null && bitmapsCache.needGenCache()) {
                        if (!genCacheSend) {
                            genCacheSend = true;
                            uiHandler.post(uiRunnableGenerateCache);
                        }
                        result = -1;
                    }
                    if (result == -1) {
                        uiHandler.post(uiRunnableNoFrame);
                        if (frameWaitSync != null) {
                            frameWaitSync.countDown();
                        }
                        return;
                    }

                    nextRenderingBitmap = backgroundBitmap;

                    if (isDice == 1) {
                        if (currentFrame + framesPerUpdates < (diceSwitchFramesCount == -1 ? metaData[0] : diceSwitchFramesCount)) {
                            currentFrame += framesPerUpdates;
                        } else {
                            currentFrame = 0;
                            nextFrameIsLast = false;
                            if (secondNativePtr != 0) {
                                isDice = 2;
                            }
                            if (resetVibrationAfterRestart) {
                                vibrationPattern = null;
                                resetVibrationAfterRestart = false;
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
                        if (customEndFrame >= 0 && playInDirectionOfCustomEndFrame) {
                            if (currentFrame > customEndFrame) {
                                if (currentFrame - framesPerUpdates >= customEndFrame) {
                                    currentFrame -= framesPerUpdates;
                                    nextFrameIsLast = false;
                                } else {
                                    nextFrameIsLast = true;
                                    checkDispatchOnAnimationEnd();
                                }
                            } else {
                                if (currentFrame + framesPerUpdates < customEndFrame) {
                                    currentFrame += framesPerUpdates;
                                    nextFrameIsLast = false;
                                } else {
                                    nextFrameIsLast = true;
                                    checkDispatchOnAnimationEnd();
                                }
                            }
                        } else {
                            if (currentFrame + framesPerUpdates < (customEndFrame >= 0 ? customEndFrame : metaData[0])) {
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
                                if (resetVibrationAfterRestart) {
                                    vibrationPattern = null;
                                    resetVibrationAfterRestart = false;
                                }
                                if (autoRepeatCount > 0) {
                                    autoRepeatCount--;
                                }
                            } else if (autoRepeat == 2) {
                                currentFrame = 0;
                                nextFrameIsLast = true;
                                autoRepeatPlayCount++;
                                if (resetVibrationAfterRestart) {
                                    vibrationPattern = null;
                                    resetVibrationAfterRestart = false;
                                }
                            } else {
                                nextFrameIsLast = true;
                                checkDispatchOnAnimationEnd();
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

    public RLottieDrawable(File file, int w, int h, BitmapsCache.CacheOptions cacheOptions, boolean limitFps) {
        this(file, w, h, cacheOptions, limitFps, null, 0);
    }

    public RLottieDrawable(File file, int w, int h, BitmapsCache.CacheOptions cacheOptions, boolean limitFps, int[] colorReplacement, int fitzModifier) {
        width = w;
        height = h;
        shouldLimitFps = limitFps;
        this.precache = cacheOptions != null;
        this.fallbackCache = cacheOptions != null && cacheOptions.fallback;
        this.createdForFirstFrame = cacheOptions != null && cacheOptions.firstFrame;
        getPaint().setFlags(Paint.FILTER_BITMAP_FLAG);

        this.file = file;
        if (precache && lottieCacheGenerateQueue == null) {
            createCacheGenQueue();
        }
        if (precache) {
            args = new NativePtrArgs();
            args.file = file.getAbsoluteFile();
            args.json = null;
            args.colorReplacement = colorReplacement;
            args.fitzModifier = fitzModifier;
            if (createdForFirstFrame) {
                return;
            }
            parseLottieMetadata(file, null, metaData);
            if (shouldLimitFps && metaData[1] < 60) {
                shouldLimitFps = false;
            }
            bitmapsCache = new BitmapsCache(file, this, cacheOptions, w, h, !limitFps);
        } else {
            nativePtr = create(file.getAbsolutePath(), null, w, h, metaData, precache, colorReplacement, shouldLimitFps, fitzModifier);
            if (nativePtr == 0) {
                file.delete();
            }
            if (shouldLimitFps && metaData[1] < 60) {
                shouldLimitFps = false;
            }
        }

        timeBetweenFrames = Math.max(shouldLimitFps ? 33 : 16, (int) (1000.0f / metaData[1]));
    }

    public RLottieDrawable(File file, String json, int w, int h, BitmapsCache.CacheOptions options, boolean limitFps, int[] colorReplacement, int fitzModifier) {
        width = w;
        height = h;
        shouldLimitFps = limitFps;
        this.precache = options != null;
        this.createdForFirstFrame = options != null && options.firstFrame;
        getPaint().setFlags(Paint.FILTER_BITMAP_FLAG);
        if (precache && lottieCacheGenerateQueue == null) {
            createCacheGenQueue();
        }
        if (precache) {
            args = new NativePtrArgs();
            args.file = file.getAbsoluteFile();
            args.json = json;
            args.colorReplacement = colorReplacement;
            args.fitzModifier = fitzModifier;
            if (createdForFirstFrame) {
                return;
            }
            parseLottieMetadata(file, json, metaData);
            if (shouldLimitFps && metaData[1] < 60) {
                shouldLimitFps = false;
            }
            bitmapsCache = new BitmapsCache(file, this, options, w, h, !limitFps);
        } else {
            nativePtr = create(file.getAbsolutePath(), json, w, h, metaData, precache, colorReplacement, shouldLimitFps, fitzModifier);
            if (nativePtr == 0) {
                file.delete();
            }
            if (shouldLimitFps && metaData[1] < 60) {
                shouldLimitFps = false;
            }
        }


        timeBetweenFrames = Math.max(shouldLimitFps ? 33 : 16, (int) (1000.0f / metaData[1]));
    }

    private void parseLottieMetadata(File file, String json, int[] metaData) {
        if (gson == null) {
            gson = new Gson();
        }
        try {
            LottieMetadata lottieMetadata;
            if (file != null) {
                FileReader reader = new FileReader(file.getAbsolutePath());
                lottieMetadata = gson.fromJson(reader, LottieMetadata.class);
                try {
                    reader.close();
                } catch (Exception e) {

                }
            } else {
                lottieMetadata = gson.fromJson(json, LottieMetadata.class);
            }
            metaData[0] = (int) (lottieMetadata.op - lottieMetadata.ip);
            metaData[1] = (int) lottieMetadata.fr;
        } catch (Exception e) {
            // ignore app center, try handle by old method
            FileLog.e(e, false);
            long nativePtr = create(file.getAbsolutePath(), json, width, height, metaData, false, args.colorReplacement, shouldLimitFps, args.fitzModifier);
            if (nativePtr != 0) {
                destroy(nativePtr);
            }
        }
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

    private void checkDispatchOnAnimationEnd() {
        if (onAnimationEndListener != null) {
            onAnimationEndListener.run();
            onAnimationEndListener = null;
        }
    }

    public void setOnAnimationEndListener(Runnable onAnimationEndListener) {
        this.onAnimationEndListener = onAnimationEndListener;
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
                    recycle(true);
                    return;
                }
                timeBetweenFrames = Math.max(16, (int) (1000.0f / metaData[1]));
                scheduleNextGetFrame();
                invalidateInternal();
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
                        recycle(true);
                    }
                });
                return;
            }
            int[] metaData2 = new int[3];
            secondNativePtr = createWithJson(jsonString, "dice", metaData2, null);
            AndroidUtilities.runOnUIThread(() -> {
                secondLoadingInBackground = false;
                if (!secondLoadingInBackground && destroyAfterLoading) {
                    recycle(true);
                    return;
                }
                secondFramesCount = metaData2[0];
                timeBetweenFrames = Math.max(16, (int) (1000.0f / metaData2[1]));
                scheduleNextGetFrame();
                invalidateInternal();
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

    public static String readRes(File path, int rawRes) {
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

    public int getCustomEndFrame() {
        return customEndFrame;
    }

    public long getDuration() {
        return (long) (metaData[0] / (float) metaData[1] * 1000);
    }

    public void setPlayInDirectionOfCustomEndFrame(boolean value) {
        playInDirectionOfCustomEndFrame = value;
    }

    public boolean setCustomEndFrame(int frame) {
        if (customEndFrame == frame || frame > metaData[0]) {
            return false;
        }
        customEndFrame = frame;
        return true;
    }

    public int getFramesCount() {
        return metaData[0];
    }

    public void addParentView(ImageReceiver parent) {
        if (parent == null) {
            return;
        }
        parentViews.add(parent);
    }

    public void removeParentView(ImageReceiver parent) {
        if (parent == null) {
            return;
        }
        parentViews.remove(parent);
        checkCacheCancel();
    }

    public void checkCacheCancel() {
        if (bitmapsCache == null || lottieCacheGenerateQueue == null || cacheGenerateTask == null) {
            return;
        }
        boolean mustCancel = parentViews.isEmpty() && getCallback() == null;
        if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            mustCancel = mustCancel && (masterParent == null || !masterParent.isAttachedToWindow());
        } else {
            mustCancel = mustCancel && masterParent == null;
        }
        if (mustCancel) {
            if (cacheGenerateTask != null) {
                lottieCacheGenerateQueue.cancelRunnable(cacheGenerateTask);
                BitmapsCache.decrementTaskCounter();
                cacheGenerateTask = null;
            }
            generatingCache = false;
            genCacheSend = false;
        }
    }

    protected boolean hasParentView() {
        return !parentViews.isEmpty() || masterParent != null || getCallback() != null;
    }

    protected void invalidateInternal() {
        if (isRecycled) {
            return;
        }
        for (int i = 0, N = parentViews.size(); i < N; i++) {
            parentViews.get(i).invalidate();
        }
        if (masterParent != null) {
            masterParent.invalidate();
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

    public void recycle(boolean uiThread) {
        isRunning = false;
        isRecycled = true;
        checkRunningTasks();
        if (loadingInBackground || secondLoadingInBackground) {
            destroyAfterLoading = true;
        } else if (loadFrameTask == null && cacheGenerateTask == null && !generatingCache) {
            recycleNativePtr(uiThread);
            if (bitmapsCache != null) {
                bitmapsCache.recycle();
                bitmapsCache = null;
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

    public void setAutoRepeatCount(int count) {
        autoRepeatCount = count;
    }

    public void setAutoRepeatTimeout(long timeout) {
        autoRepeatTimeout = timeout;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            recycle(false);
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
        if (isRunning || autoRepeat >= 2 && autoRepeatPlayCount != 0 || customEndFrame == currentFrame) {
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
        return restart(false);
    }

    public boolean restart(boolean force) {
        if (!force && (autoRepeat < 2 || autoRepeatPlayCount == 0) && autoRepeatCount < 0) {
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

    public boolean hasVibrationPattern() {
        return vibrationPattern != null;
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

    protected boolean scheduleNextGetFrame() {
        if (loadFrameTask != null || nextRenderingBitmap != null || !canLoadFrames() || loadingInBackground || destroyWhenDone || !isRunning && (!decodeSingleFrame || decodeSingleFrame && singleFrameDecoded)) {
            return false;
        }
        if (generatingCache) {
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
        loadFrameTask = loadFrameRunnable;
        if (shouldLimitFps && Thread.currentThread() == ApplicationLoader.applicationHandler.getLooper().getThread()) {
            DispatchQueuePoolBackground.execute(loadFrameTask, frameWaitSync != null);
        } else {
            loadFrameRunnableQueue.execute(loadFrameTask);
        }
        return true;
    }

    public boolean isHeavyDrawable() {
        return isDice == 0;
    }

    @Override
    public void stop() {
        isRunning = false;
    }

    public void setCurrentFrame(int frame) {
        setCurrentFrame(frame, true);
    }

    public void setCurrentFrame(int frame, boolean async) {
        setCurrentFrame(frame, async, false);
    }

    public void setCurrentFrame(int frame, boolean async, boolean resetFrame) {
        if (frame < 0 || frame > metaData[0] || (currentFrame == frame && !resetFrame)) {
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
        if (resetFrame && !isRunning) {
            isRunning = true;
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

    public boolean isCacheFallbacked() {
        return fallbackCache;
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
        if (nextFrameIsLast || autoRepeatCount == 0 && autoRepeat == 1) {
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
        drawInternal(canvas, null, false, 0, 0);
    }

    public void drawInBackground(Canvas canvas, float x, float y, float w, float h, int alpha, ColorFilter colorFilter, int threadIndex) {
        if (dstRectBackground[threadIndex] == null) {
            dstRectBackground[threadIndex] = new RectF();
            backgroundPaint[threadIndex] = new Paint(Paint.ANTI_ALIAS_FLAG);
            backgroundPaint[threadIndex].setFilterBitmap(true);
        }
        backgroundPaint[threadIndex].setAlpha(alpha);
        backgroundPaint[threadIndex].setColorFilter(colorFilter);
        dstRectBackground[threadIndex].set(x, y, x + w, y + h);
        drawInternal(canvas, null,true, 0, threadIndex);
    }

    public void draw(Canvas canvas, Paint paint) {
        drawInternal(canvas, paint, false, 0, 0);
    }

    public void drawInternal(Canvas canvas, Paint overridePaint, boolean drawInBackground, long time, int threadIndex) {
        if (!canLoadFrames() || destroyWhenDone) {
            return;
        }
        if (!drawInBackground) {
            updateCurrentFrame(time, false);
        }

        RectF rect = drawInBackground ? dstRectBackground[threadIndex] : dstRect;
        Paint paint = overridePaint != null ? overridePaint : (drawInBackground ? backgroundPaint[threadIndex] : getPaint());

        if (paint.getAlpha() == 0) {
            return;
        }

        if (!isInvalid && renderingBitmap != null) {
            float scaleX, scaleY;
            boolean needScale;
            if (!drawInBackground) {
                rect.set(getBounds());
                if (applyTransformation) {
                    this.scaleX = rect.width() / width;
                    this.scaleY = rect.height() / height;
                    applyTransformation = false;
                    this.needScale = !(Math.abs(rect.width() - width) < AndroidUtilities.dp(1) && Math.abs(rect.height() - height) < AndroidUtilities.dp(1));
                }
                scaleX = this.scaleX;
                scaleY = this.scaleY;
                needScale = this.needScale;
            } else {
                scaleX = rect.width() / width;
                scaleY = rect.height() / height;
                needScale = !(Math.abs(rect.width() - width) < AndroidUtilities.dp(1) && Math.abs(rect.height() - height) < AndroidUtilities.dp(1));
            }
            if (!needScale) {
                canvas.drawBitmap(renderingBitmap, rect.left, rect.top, paint);
            } else {
                canvas.save();
                canvas.translate(rect.left, rect.top);
                canvas.scale(scaleX, scaleY);
                canvas.drawBitmap(renderingBitmap, 0, 0, paint);
                canvas.restore();
            }

            if (isRunning && !drawInBackground) {
                invalidateInternal();
            }
        }
    }

    public void updateCurrentFrame(long time, boolean updateInBackground) {
        long now = time == 0 ? System.currentTimeMillis() : time;
        long timeDiff = now - lastFrameTime;
        int timeCheck;
        if (updateInBackground && !shouldLimitFps) {
            timeCheck = timeBetweenFrames - 16;
        } else if (AndroidUtilities.screenRefreshRate <= 60 || (updateInBackground && AndroidUtilities.screenRefreshRate <= 80)) {
            timeCheck = timeBetweenFrames - 6;
        } else {
            timeCheck = timeBetweenFrames;
        }
        if (isRunning) {
            if (renderingBitmap == null && nextRenderingBitmap == null) {
                scheduleNextGetFrame();
            } else if (nextRenderingBitmap != null && (renderingBitmap == null || (timeDiff >= timeCheck && !skipFrameUpdate))) {
                if (vibrationPattern != null && currentParentView != null && allowVibration) {
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
    }

    public void setAllowVibration(boolean allow) {
        allowVibration = allow;
    }

    public void resetVibrationAfterRestart(boolean value) {
        resetVibrationAfterRestart = value;
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
        return !isRecycled && (renderingBitmap != null || nextRenderingBitmap != null) && !isInvalid;
    }

    public void setInvalidateOnProgressSet(boolean value) {
        invalidateOnProgressSet = value;
    }

    public boolean isGeneratingCache() {
        return cacheGenerateTask != null;
    }

    public void setOnFrameReadyRunnable(Runnable onFrameReadyRunnable) {
        this.onFrameReadyRunnable = onFrameReadyRunnable;
    }

    public boolean isLastFrame() {
        return currentFrame == getFramesCount() - 1;
    }

    long generateCacheNativePtr;

    @Override
    public void prepareForGenerateCache() {
        generateCacheNativePtr = create(args.file.toString(), args.json, width, height, createdForFirstFrame ? metaData : new int[3], false, args.colorReplacement, false, args.fitzModifier);
        if (generateCacheNativePtr == 0 && file != null) {
            file.delete();
        }
    }

    public void setGeneratingFrame(int i) {
        generateCacheFramePointer = i;
    }

    @Override
    public int getNextFrame(Bitmap bitmap) {
        if (generateCacheNativePtr == 0) {
            return -1;
        }
        int framesPerUpdates = shouldLimitFps ? 2 : 1;

        int result = getFrame(generateCacheNativePtr, generateCacheFramePointer, bitmap, width, height, bitmap.getRowBytes(), true);
        if (result == -5) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return getNextFrame(bitmap);
        }
        generateCacheFramePointer += framesPerUpdates;
        if (generateCacheFramePointer > metaData[0]) {
            return 0;
        }
        return 1;
    }

    private int rawBackgroundBitmapFrame = -1;
    public void drawFrame(Canvas canvas, int frame) {
        if (rawBackgroundBitmapFrame != frame || backgroundBitmap == null) {
            if (backgroundBitmap == null) {
                backgroundBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            }
            int result = getFrame(nativePtr, rawBackgroundBitmapFrame = frame, backgroundBitmap, width, height, backgroundBitmap.getRowBytes(), true);
        }
        AndroidUtilities.rectTmp2.set(0, 0, width, height);
        canvas.drawBitmap(backgroundBitmap, AndroidUtilities.rectTmp2, getBounds(), getPaint());
    }

    @Override
    public void releaseForGenerateCache() {
        if (generateCacheNativePtr != 0) {
            destroy(generateCacheNativePtr);
            generateCacheNativePtr = 0;
        }
    }

    @Override
    public Bitmap getFirstFrame(Bitmap bitmap) {
        long nativePtr = create(args.file.toString(), args.json, width, height, new int[3], false, args.colorReplacement, false, args.fitzModifier);
        if (nativePtr == 0) {
            return bitmap;
        }
        getFrame(nativePtr, 0, bitmap, width, height, bitmap.getRowBytes(), true);
        destroy(nativePtr);
        return bitmap;
    }

    public void setMasterParent(View parent) {
        masterParent = parent;
    }

    public boolean canLoadFrames() {
        if (precache) {
            return bitmapsCache != null || fallbackCache;
        } else {
            return nativePtr != 0;
        }
    }

    private class NativePtrArgs {
        public int[] colorReplacement;
        public int fitzModifier;
        File file;
        String json;
    }

    public void checkCache(Runnable onReady) {
        if (bitmapsCache == null) {
            AndroidUtilities.runOnUIThread(onReady);
            return;
        }

        generatingCache = true;
        if (lottieCacheGenerateQueue == null) {
            createCacheGenQueue();
        }
        if (cacheGenerateTask == null) {
            BitmapsCache.incrementTaskCounter();
            lottieCacheGenerateQueue.postRunnable(cacheGenerateTask = () -> {
                try {
                    BitmapsCache bitmapsCacheFinal = bitmapsCache;
                    if (bitmapsCacheFinal != null) {
                        bitmapsCacheFinal.createCache();
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    onReady.run();
                    if (cacheGenerateTask != null) {
                        cacheGenerateTask = null;
                        BitmapsCache.decrementTaskCounter();
                    }
                });
            });
        }
    }

    private class LottieMetadata {
        float fr;
        float op;
        float ip;
    }
}
