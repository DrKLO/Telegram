/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.readRes;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.text.TextUtils;
import android.util.JsonReader;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.AnyThread;
import androidx.annotation.RawRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.DispatchQueuePoolBackground;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.ResLottieMeta;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.BitmapsCache;
import org.telegram.messenger.utils.Choreographer60FpsContent;
import org.telegram.ui.BubbleActivity;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class RLottieDrawable extends BitmapDrawable implements Animatable, BitmapsCache.Cacheable {

    public boolean skipFrameUpdate;

    protected final int width;
    protected final int height;
    private boolean pendingNativeInit;
    protected final int[] metaData = new int[3];
    protected int customEndFrame = -1;
    protected boolean playInDirectionOfCustomEndFrame;
    private int[] newReplaceColors;
    private int[] pendingReplaceColors;
    private final HashMap<String, Integer> newColorUpdates = new HashMap<>();
    private final HashMap<String, Integer> pendingColorUpdates = new HashMap<>();
    private final HashMap<String, Integer> layerColors = new HashMap<>();
    protected HashMap<Integer, Integer> vibrationPattern;
    protected boolean resetVibrationAfterRestart = false;
    private boolean allowVibration = true;
    private float speedMultiply = 1f;
    private final boolean isSingleChannel;

    protected WeakReference<Runnable> onFinishCallback;
    private int finishFrame;

    private final ArrayList<ImageReceiver> parentViews = new ArrayList<>();

    protected int isDice;

    protected int autoRepeat = 1;
    protected int autoRepeatCount = -1;
    protected int autoRepeatPlayCount;
    protected long autoRepeatTimeout;

    protected volatile boolean nextFrameIsLast;

    private Runnable cacheGenerateTask;
    protected Runnable loadFrameTask;
    protected volatile Bitmap renderingBitmap;
    protected volatile Bitmap nextRenderingBitmap;
    protected volatile Bitmap backgroundBitmap;

    protected boolean waitingForNextTask;

    private CountDownLatch frameWaitSync;

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
    private final RectF[] dstRectBackground = new RectF[DrawingInBackgroundThreadDrawable.THREAD_COUNT];
    private final Paint[] backgroundPaint = new Paint[DrawingInBackgroundThreadDrawable.THREAD_COUNT];
    protected volatile boolean isRunning;
    protected volatile boolean isRecycled;
    protected volatile RLottieNative nativePtr;
    private boolean fallbackCache;

    private boolean invalidateOnProgressSet;
    private boolean isInvalid;
    private boolean doNotRemoveInvalidOnFrameReady;

    private static final AtomicInteger threadId = new AtomicInteger();
    private static final AtomicInteger threadId2 = new AtomicInteger();
    private static final Executor loadFrameRunnableQueue = Executors.newFixedThreadPool(4, r -> new Thread(r, "Lottie-" + threadId.getAndIncrement()));
    private static final Executor loadFrameRunnableQueueLimitFps = Executors.newFixedThreadPool(2, r -> new Thread(r, "LottieLow-" + threadId2.getAndIncrement()));

    public static DispatchQueue lottieCacheGenerateQueue;

    private File file;
    private boolean precache;

    private Runnable onAnimationEndListener;

    private View masterParent;
    private NativePtrArgs args;

    private final Runnable uiRunnableNoFrame = this::uiRunnableNoFrameImpl;

    private void uiRunnableNoFrameImpl() {
        loadFrameTask = null;
        decodeFrameFinishedInternal();
    }



    private final Runnable uiRunnable = this::uiRunnableImpl;

    @UiThread
    private void uiRunnableImpl() {
        singleFrameDecoded = true;
        // Static frame: Choreographer won't tick when !isRunning, invalidate manually.
        if (!isRunning && decodeSingleFrame || renderingBitmap == null && nextRenderingBitmap != null) {
            invalidateInternal();
        }
        decodeFrameFinishedInternal();
    }



    boolean generatingCache;

    private final Runnable uiRunnableGenerateCache = this::uiRunnableGenerateCacheImpl;

    private void uiRunnableGenerateCacheImpl() {
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
                } catch (Throwable ignoreThrowable) {

                }
                AndroidUtilities.runOnUIThread(uiRunnableCacheFinished);
            });
        }
    }

    private final Runnable uiRunnableCacheFinished = this::uiRunnableCacheFinishedImpl;

    @UiThread
    private void uiRunnableCacheFinishedImpl() {
        if (cacheGenerateTask != null) {
            BitmapsCache.decrementTaskCounter();
            cacheGenerateTask = null;
        }
        generatingCache = false;
        decodeFrameFinishedInternal();
        if (whenCacheDone != null) {
            whenCacheDone.run();
            whenCacheDone = null;
        }
    }

    public Runnable whenCacheDone;

    BitmapsCache bitmapsCache;
    int generateCacheFramePointer;

    public static void createCacheGenQueue() {
        lottieCacheGenerateQueue = new DispatchQueue("cache generator queue");
    }

    protected final void checkRunningTasks() {
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
            if (loadFrameTask == null && cacheGenerateTask == null && nativePtr != null) {
                recycleNativePtr(true);
            }
        }
        if ((nativePtr == null || fallbackCache) && bitmapsCache == null) {
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

    protected void recycleNativePtr(boolean uiThread) {
        RLottieNative nativePtrFinal = nativePtr;
        nativePtr = null;
        if (nativePtrFinal != null) {
            final Runnable recycleImpl = nativePtrFinal::recycle;
            if (uiThread) {
                DispatchQueuePoolBackground.execute(recycleImpl);
            } else {
                Utilities.globalQueue.postRunnable(recycleImpl);
            }
        }
    }

    protected final void recycleResources() {
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

    public final void setOnFinishCallback(Runnable callback, int frame) {
        if (callback != null) {
            onFinishCallback = new WeakReference<>(callback);
            finishFrame = frame;
        } else if (onFinishCallback != null) {
            onFinishCallback = null;
        }
    }

    private boolean genCacheSend;
    private boolean allowDrawFramesWhileCacheGenerating;

    protected final Runnable loadFrameRunnable = this::loadFrameRunnableInternal;

    protected static final int LOAD_FRAME_RESULT_OK = 1;
    protected static final int LOAD_FRAME_RESULT_ERROR = 2;
    protected static final int LOAD_FRAME_RESULT_RECYCLED = 3;


    private int retryDelay;

    @WorkerThread
    private void loadFrameRunnableInternal() {
        final int result = loadFrameRunnableImpl();

        if (result == LOAD_FRAME_RESULT_OK) {
            retryDelay = 0;
            AndroidUtilities.runOnUIThread(uiRunnable);
        } else if (result == LOAD_FRAME_RESULT_ERROR) {
            AndroidUtilities.runOnUIThread(uiRunnableNoFrame, retryDelay);
            retryDelay = Math.min(Math.max(retryDelay, 2) * 3 / 2, 2000);
        } else if (result == LOAD_FRAME_RESULT_RECYCLED) {
            AndroidUtilities.runOnUIThread(uiRunnableNoFrame);
        }

        if (frameWaitSync != null) {
            frameWaitSync.countDown();
        }
    }

    @WorkerThread
    protected int loadFrameRunnableImpl() {
        if (isRecycled) {
            return LOAD_FRAME_RESULT_RECYCLED;
        }
        if (!canLoadFrames()) {
            return LOAD_FRAME_RESULT_ERROR;
        }

        if (nativePtr == null && pendingNativeInit) {
            final String jsonString = AndroidUtilities.readRes(args.resId);
            if (TextUtils.isEmpty(jsonString)) {
                return LOAD_FRAME_RESULT_ERROR;
            }

            nativePtr = RLottieNative.createFromRawJson(jsonString, args.name, metaData, args.colorReplacement, layerColors);
            pendingNativeInit = false;
        }

        boolean needClearBitmap = true;
        if (backgroundBitmap == null) {
            try {
                final Bitmap.Config config = isSingleChannel ? Bitmap.Config.ALPHA_8 : Bitmap.Config.ARGB_8888;
                backgroundBitmap = Bitmap.createBitmap(width, height, config);
                needClearBitmap = false;
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        if (backgroundBitmap != null) {
            applyPendingColorsUpdates();
            try {
                final RLottieNative ptrToUse = nativePtr;
                int result = 0;
                int framesPerUpdates = shouldLimitFps ? 2 : 1;
                if (precache && bitmapsCache != null) {
                    try {
                        result = bitmapsCache.getFrame(currentFrame / framesPerUpdates, backgroundBitmap);
                        if (!bitmapsCache.needGenCache() && allowDrawFramesWhileCacheGenerating && nativePtr != null) {
                            nativePtr.recycle();
                            nativePtr = null;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else {
                    result = ptrToUse.getFrame(currentFrame, backgroundBitmap, needClearBitmap);
                }
                if (bitmapsCache != null && bitmapsCache.needGenCache()) {
                    if (!genCacheSend) {
                        genCacheSend = true;
                        AndroidUtilities.runOnUIThread(uiRunnableGenerateCache);
                    }
                    if (allowDrawFramesWhileCacheGenerating) {
                        if (nativePtr == null) {
                            nativePtr = RLottieNative.createFromFile(args.file.toString(), args.json, width, height, null, false, args.colorReplacement, false, args.fitzModifier, layerColors);
                        }
                        result = nativePtr != null ? nativePtr.getFrame(currentFrame, backgroundBitmap, needClearBitmap) : -1;
                    } else {
                        result = -1;
                    }
                }
                if (result < 0) {
                    return LOAD_FRAME_RESULT_ERROR;
                }

                nextRenderingBitmap = backgroundBitmap;

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
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return LOAD_FRAME_RESULT_OK;
    }

    @WorkerThread
    private void applyPendingColorsUpdates() {
        final RLottieNative old = nativePtr;
        if (old == null) {
            return;
        }
        try {
            if (!pendingColorUpdates.isEmpty() || pendingReplaceColors != null) {
                layerColors.putAll(pendingColorUpdates);
                if (pendingReplaceColors != null) {
                    args.colorReplacement = pendingReplaceColors.clone();
                }
                RLottieNative replacement;
                if (args.file != null) {
                    replacement = RLottieNative.createFromFile(args.file.getAbsolutePath(), args.json,
                            width, height, metaData, false, args.colorReplacement, shouldLimitFps,
                            args.fitzModifier, layerColors);
                } else if (args.resId != 0 && args.json == null) {
                    final String jsonString = AndroidUtilities.readRes(args.resId);
                    if (TextUtils.isEmpty(jsonString)) {
                        return;
                    }
                    args.json = jsonString;
                    replacement = RLottieNative.createFromRawJson(jsonString, args.name, metaData,
                            args.colorReplacement, layerColors);
                } else {
                    replacement = RLottieNative.createFromRawJson(args.json, args.name, metaData,
                            args.colorReplacement, layerColors);
                }
                if (replacement != null) {
                    nativePtr = replacement;
                    old.recycle();
                    pendingColorUpdates.clear();
                    pendingReplaceColors = null;
                }
            }
        } catch (Exception ignore) {

        }
    }

    public RLottieDrawable(File file, String json, int w, int h, BitmapsCache.CacheOptions options, boolean limitFps, int[] colorReplacement, int fitzModifier, boolean isSingleChannel) {
        width = w;
        height = h;
        shouldLimitFps = limitFps;
        this.isSingleChannel = isSingleChannel;
        this.precache = options != null;
        this.fallbackCache = json == null && options != null && options.fallback;
        this.createdForFirstFrame = options != null && options.firstFrame;
        args = new NativePtrArgs();
        args.file = file.getAbsoluteFile();
        args.json = json;
        args.colorReplacement = colorReplacement == null ? null : colorReplacement.clone();
        args.fitzModifier = fitzModifier;
        getPaint().setFlags(Paint.FILTER_BITMAP_FLAG);
        if (json == null) {
            this.file = file;
        }
        if (precache && lottieCacheGenerateQueue == null) {
            createCacheGenQueue();
        }
        if (precache) {
            if (createdForFirstFrame) {
                return;
            }
            parseLottieMetadata(file, json, metaData);
            if (shouldLimitFps && metaData[1] < 60) {
                shouldLimitFps = false;
            }
            bitmapsCache = new BitmapsCache(file, this, options, w, h, !limitFps, fitzModifier);
        } else {
            nativePtr = RLottieNative.createFromFile(file.getAbsolutePath(), json, w, h, metaData, precache, args.colorReplacement, shouldLimitFps, fitzModifier, layerColors);
            if (nativePtr == null) {
                FileLog.d("RLottieDrawable nativePtr == 0 " + file.getAbsolutePath() + " remove file");
                file.delete();
            }
            if (shouldLimitFps && metaData[1] < 60) {
                shouldLimitFps = false;
            }
        }
    }

    private void parseLottieMetadata(File file, String json, int[] metaData) {
        try {
            double fr = 30.0;
            double ip = 0;
            double op = 0;
            try (JsonReader reader = new JsonReader(new FileReader(file.getAbsoluteFile()))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    switch (name) {
                        case "ip": {
                            ip = reader.nextDouble();
                            break;
                        }
                        case "op": {
                            op = reader.nextDouble();
                            break;
                        }
                        case "fr": {
                            fr = reader.nextDouble();
                            break;
                        }
                        default: {
                            reader.skipValue();
                            break;
                        }
                    }
                }
                reader.endObject();
            }
            metaData[0] = (int) (op - ip);
            metaData[1] = (int) fr;
        } catch (Exception e) {
            // ignore app center, try handle by old method
            FileLog.e(e, false);

            final RLottieNative lottieNative = RLottieNative.createFromFile(file.getAbsolutePath(), json, width, height, metaData, false, args.colorReplacement, shouldLimitFps, args.fitzModifier, layerColors);
            if (lottieNative != null) {
                lottieNative.recycle();
            }
        }
    }

    protected RLottieDrawable(int w, int h) {
        width = w;
        height = h;
        isSingleChannel = false;
    }

    private void checkDispatchOnAnimationEnd() {
        if (onAnimationEndListener != null) {
            onAnimationEndListener.run();
            onAnimationEndListener = null;
        }
    }

    public final void setOnAnimationEndListener(Runnable onAnimationEndListener) {
        this.onAnimationEndListener = onAnimationEndListener;
    }

    public RLottieDrawable(@RawRes int rawRes, String name, int w, int h) {
        this(rawRes, name, w, h, true, null);
    }

    public RLottieDrawable(@RawRes int rawRes, String name, int w, int h, boolean startDecode, int[] colorReplacement) {
        width = w;
        height = h;
        autoRepeat = 0;
        getPaint().setFlags(Paint.FILTER_BITMAP_FLAG);
        args = new NativePtrArgs();
        args.name = name;
        args.colorReplacement = colorReplacement == null ? null : colorReplacement.clone();

        final long found = ResLottieMeta.find(rawRes);
        if (found != ResLottieMeta.NOT_FOUND) {
            pendingNativeInit = true;
            args.resId = rawRes;

            isSingleChannel = ResLottieMeta.isMonoColorOf(found);
            metaData[0] = ResLottieMeta.frameCountOf(found);
            metaData[1] = ResLottieMeta.fpsOf(found);
        } else {
            isSingleChannel = false;

            if (BuildConfig.DEBUG_PRIVATE_VERSION) {
                throw new IllegalArgumentException("rawRes not found");
            }

            String jsonString = readRes(rawRes);
            if (TextUtils.isEmpty(jsonString)) {
                args = null;
                return;
            }
            args.json = jsonString;
            nativePtr = RLottieNative.createFromRawJson(jsonString, name, metaData, args.colorReplacement, layerColors);
        }

        if (isSingleChannel) {
            setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        }

        if (startDecode) {
            setAllowDecodeSingleFrame(true);
        }
    }

    public final void multiplySpeed(float multiplier) {
        speedMultiply *= multiplier;
    }

    public final int getCurrentFrame() {
        return currentFrame;
    }

    public final float getProgress() {
        return (float) currentFrame / metaData[0];
    }

    public final int getCustomEndFrame() {
        return customEndFrame;
    }

    public final long getDuration() {
        return (long) (metaData[0] / (float) metaData[1] * 1000);
    }

    public final void setPlayInDirectionOfCustomEndFrame(boolean value) {
        playInDirectionOfCustomEndFrame = value;
    }

    public final boolean setCustomEndFrame(int frame) {
        if (customEndFrame == frame || frame > metaData[0]) {
            return false;
        }
        customEndFrame = frame;
        return true;
    }

    public final int getFramesCount() {
        return metaData[0];
    }

    public final void addParentView(ImageReceiver parent) {
        if (parent == null) {
            return;
        }
        parentViews.add(parent);
    }

    public final void removeParentView(ImageReceiver parent) {
        if (parent == null) {
            return;
        }
        parentViews.remove(parent);
        checkCacheCancel();
    }

    public final void checkCacheCancel() {
        if (bitmapsCache == null || lottieCacheGenerateQueue == null || cacheGenerateTask == null) {
            return;
        }
        final boolean mustCancel = parentViews.isEmpty() && getCallback() == null
            && (masterParent == null || !masterParent.isAttachedToWindow());

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

    protected final boolean hasParentView() {
        return !parentViews.isEmpty() || masterParent != null || getCallback() != null;
    }

    @UiThread
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

    public final void setAllowDecodeSingleFrame(boolean value) {
        decodeSingleFrame = value;
        if (decodeSingleFrame) {
            scheduleNextGetFrame();
        }
    }

    public void recycle(boolean uiThread) {
        isRunning = false;
        isRecycled = true;
        checkChoreographer();
        checkRunningTasks();
        if (loadFrameTask == null && cacheGenerateTask == null && !generatingCache) {
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

    public final void setAutoRepeat(int value) {
        if (autoRepeat == 2 && value == 3 && currentFrame != 0) {
            return;
        }
        autoRepeat = value;
    }

    public final void setAutoRepeatCount(int count) {
        autoRepeatCount = count;
    }

    public final void setAutoRepeatTimeout(long timeout) {
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
    public final int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public final void start() {
        if (isRunning || autoRepeat >= 2 && autoRepeatPlayCount != 0 || customEndFrame == currentFrame) {
            return;
        }
        isRunning = true;
        isPaused = false;
        if (invalidateOnProgressSet) {
            isInvalid = true;
            if (loadFrameTask != null) {
                doNotRemoveInvalidOnFrameReady = true;
            }
        }
        scheduleNextGetFrame();
        invalidateInternal();
        checkChoreographer();
    }

    public final boolean restart() {
        return restart(false);
    }

    public final boolean restart(boolean force) {
        if (!force && (autoRepeat < 2 || autoRepeatPlayCount == 0) && autoRepeatCount < 0) {
            return false;
        }
        autoRepeatPlayCount = 0;
        autoRepeat = 2;
        start();
        return true;
    }

    public final void setVibrationPattern(HashMap<Integer, Integer> pattern) {
        vibrationPattern = pattern;
    }

    public final boolean hasVibrationPattern() {
        return vibrationPattern != null;
    }

    public final void beginApplyLayerColors() {
        applyingLayerColors = true;
    }

    public final void commitApplyLayerColors() {
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

    public final void replaceColors(int[] colors) {
        newReplaceColors = colors;
        requestRedrawColors();
    }

    public final void setLayerColor(String layerName, int color) {
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

    protected final boolean scheduleNextGetFrame() {
        final boolean ignoreScheduleNext = loadFrameTask != null
            || nextRenderingBitmap != null
            || !canLoadFrames()
            || ignoreScheduleNextGetFrame()
            || destroyWhenDone
            || isRecycled
            || !isRunning && (!decodeSingleFrame || singleFrameDecoded)
            || generatingCache && !allowDrawFramesWhileCacheGenerating;

        if (ignoreScheduleNext) {
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

        final Executor executor = shouldLimitFps ?
            loadFrameRunnableQueueLimitFps : loadFrameRunnableQueue;

        executor.execute(loadFrameTask);
        return true;
    }

    protected boolean ignoreScheduleNextGetFrame() {
        return renderingBitmap != null && getFramesCount() == 1;
    }

    public boolean isHeavyDrawable() {
        return true;
    }

    @Override
    public final void stop() {
        isRunning = false;
        checkChoreographer();
    }

    public final void setCurrentFrame(int frame) {
        setCurrentFrame(frame, true);
    }

    public final void setCurrentFrame(int frame, boolean async) {
        setCurrentFrame(frame, async, false);
    }

    public final void setCurrentFrame(int frame, boolean async, boolean resetFrame) {
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
            checkChoreographer();
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

    public final void setProgressMs(long ms) {
        if (metaData[0] == 0 || metaData[1] == 0) {
            return;
        }
        final float timeBetweenFramesMs = 1000f / metaData[1];
        final int frameNum = Math.round((Math.max(0, ms) / timeBetweenFramesMs)) % metaData[0];
        setCurrentFrame(frameNum, true, true);
    }

    public final void setProgress(float progress) {
        setProgress(progress, true);
    }

    public final void setProgress(float progress, boolean async) {
        if (progress < 0.0f) {
            progress = 0.0f;
        } else if (progress > 1.0f) {
            progress = 1.0f;
        }
        setCurrentFrame((int) (metaData[0] * progress), async);
    }

    @Override
    public final boolean isRunning() {
        return isRunning;
    }

    @Override
    public final int getIntrinsicHeight() {
        return height;
    }

    @Override
    public final int getIntrinsicWidth() {
        return width;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        applyTransformation = true;
    }


    @UiThread
    private void swapBuffers() {
        renderingBitmap = nextRenderingBitmap;
        nextRenderingBitmap = null;
        swapBuffersAllowedByChoreographer = false;
    }


    @UiThread
    private void setCurrentFrame(long now, boolean force) {
        backgroundBitmap = renderingBitmap;
        swapBuffers();
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
    public final void draw(Canvas canvas) {
        drawInternal(canvas, null, false, 0, 0);
    }

    public final void drawInBackground(Canvas canvas, float x, float y, float w, float h, int alpha, ColorFilter colorFilter, int threadIndex) {
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

    public final void draw(Canvas canvas, Paint paint) {
        drawInternal(canvas, paint, false, 0, 0);
    }

    public final void drawInternal(Canvas canvas, Paint overridePaint, boolean drawInBackground, long time, int threadIndex) {
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

        if (isInvalid || renderingBitmap == null) {
            return;
        }

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

        //if (isRunning && !drawInBackground) {
        //    invalidateInternal();
        //}
    }

    @UiThread
    public void updateCurrentFrame(long time, boolean updateInBackground) {
        checkChoreographerAfterDrawCall();
        updateCurrentFrameInternal(time, updateInBackground);
    }

    @UiThread
    private void updateCurrentFrameInternal(long time, boolean updateInBackground) {
        final long now = time == 0 ? System.currentTimeMillis() : time;
        //final boolean canSwapBuffers = timeDiff >= timeCheck;

        final boolean canSwapBuffers = swapBuffersAllowedByChoreographer
            || !isRunning && decodeSingleFrame;

        if (isRunning) {
            if (renderingBitmap == null && nextRenderingBitmap == null) {
                scheduleNextGetFrame();
            } else if (nextRenderingBitmap != null && (renderingBitmap == null || (canSwapBuffers && !skipFrameUpdate))) {
                performVibration();
                setCurrentFrame(now, false);
            }
        } else if ((forceFrameRedraw || decodeSingleFrame && canSwapBuffers) && nextRenderingBitmap != null) {
            setCurrentFrame(now, true);
        }
    }

    @UiThread
    private void performVibration() {
        if (vibrationPattern != null && allowVibration) {
            Integer force = vibrationPattern.get(currentFrame - 1);
            if (force != null) {
                try {
                    Activity activity = LaunchActivity.instance;
                    if (activity == null) activity = BubbleActivity.instance;
                    activity.getWindow().getDecorView().performHapticFeedback(force == 1 ? HapticFeedbackConstants.LONG_PRESS : HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                } catch (Exception ignored) {}
            }
        }
    }

    public final void setAllowVibration(boolean allow) {
        allowVibration = allow;
    }

    public final void resetVibrationAfterRestart(boolean value) {
        resetVibrationAfterRestart = value;
    }

    @Override
    public final int getMinimumHeight() {
        return height;
    }

    @Override
    public final int getMinimumWidth() {
        return width;
    }

    public final Bitmap getAnimatedBitmap() {
        if (renderingBitmap != null) {
            return renderingBitmap;
        } else if (nextRenderingBitmap != null) {
            return nextRenderingBitmap;
        }
        return null;
    }

    public final boolean hasBitmap() {
        return !isRecycled && (renderingBitmap != null || nextRenderingBitmap != null) && !isInvalid;
    }

    public final void setInvalidateOnProgressSet(boolean value) {
        invalidateOnProgressSet = value;
    }

    public final boolean isGeneratingCache() {
        return cacheGenerateTask != null;
    }

    public final boolean isLastFrame() {
        return currentFrame == getFramesCount() - 1;
    }

    private RLottieNative generateCacheNative;

    @Override
    @AnyThread
    public final void prepareForGenerateCache() {
        generateCacheNative = RLottieNative.createFromFile(args.file != null ? args.file.toString() : null, args.json, width, height, createdForFirstFrame ? metaData : null, false, args.colorReplacement, false, args.fitzModifier, layerColors);
        generateCacheFramePointer = 0;
        if (generateCacheNative == null && file != null) {
            file.delete();
        }
    }

    public final void setGeneratingFrame(int i) {
        generateCacheFramePointer = i;
    }

    @Override
    @AnyThread
    public final int getNextFrame(Bitmap bitmap) {
        if (generateCacheNative == null) {
            return -1;
        }
        if (generateCacheFramePointer >= generateCacheNative.getFrameCount()) {
            return 0;
        }
        int framesPerUpdates = shouldLimitFps ? 2 : 1;
        int result = generateCacheNative.getFrame(generateCacheFramePointer, bitmap, true);
        if (result == -5) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return getNextFrame(bitmap);
        }
        generateCacheFramePointer += framesPerUpdates;
        return 1;
    }

    @Override
    @AnyThread
    public final void releaseForGenerateCache() {
        if (generateCacheNative != null) {
            generateCacheNative.recycle();
            generateCacheNative = null;
        }
    }

    public final void setMasterParent(View parent) {
        masterParent = parent;
    }

    private boolean canLoadFrames() {
        if (precache) {
            return bitmapsCache != null || fallbackCache;
        } else {
            return nativePtr != null || pendingNativeInit;
        }
    }

    private static class NativePtrArgs {
        public int[] colorReplacement;
        public int fitzModifier;
        public @RawRes int resId;
        File file;
        String json;
        String name;
    }

    public final void setAllowDrawFramesWhileCacheGenerating(boolean allow) {
        allowDrawFramesWhileCacheGenerating = allow;
    }


    public int estimateSizeInCache() {
        final int intrinsicSize = getIntrinsicWidth() * getIntrinsicHeight();
        if (isSingleChannel) {
            // 2 Alpha8 frame buffers
            return intrinsicSize * 2;
        } else {
            // 2 RGBA8888 bitmaps
            return intrinsicSize * 4 * 2;
        }
    }






    private static final int PAUSE_AFTER_TICKS = 10;
    private int ticksWithoutDraw;
    private volatile boolean isPaused;

    @UiThread
    private void checkChoreographerAfterFrameCall() {
        ticksWithoutDraw++;
        if (ticksWithoutDraw > PAUSE_AFTER_TICKS) {
            isPaused = true;
        }
        checkChoreographerInternal();
    }

    @UiThread
    private void checkChoreographerAfterDrawCall() {
        ticksWithoutDraw = 0;
        if (isPaused) {
            isPaused = false;
            checkChoreographer();
        }
    }

    private final Choreographer60FpsContent.FrameCallback mUiThreadChoreographerCallback = this::onChoreographerFrame;
    private boolean swapBuffersAllowedByChoreographer;

    @UiThread
    private void onChoreographerFrame(long frameTimeNanos) {
        checkChoreographerAfterFrameCall();
        if (isChoreographerRegistered) {
            swapBuffersAllowedByChoreographer = true;
            invalidateInternal();
        }
    }

    @AnyThread
    protected final void checkChoreographer() {
        AndroidUtilities.executeOnUIThread(this::checkChoreographerInternal);
    }

    private static int activeChoreographersCount;
    private boolean isChoreographerRegistered;

    @UiThread
    private void checkChoreographerInternal() {
        if (isRunning && !isPaused) {
            if (!isChoreographerRegistered) {
                final int fps = Math.round(metaData[1] / (shouldLimitFps ? 2f : 1f) * speedMultiply);
                if (fps <= 0 || metaData[0] == 1 && isDice == 0) {
                    return;
                }
                activeChoreographersCount++;
                isChoreographerRegistered = true;
                ticksWithoutDraw = 0;
                Choreographer60FpsContent.getInstance().addFrameCallback(mUiThreadChoreographerCallback, fps);
                // Log.i("CHOREOGRAPHER_DEBUG", "+ LottieDrawable " + activeChoreographersCount + " fps: " + fps);
                invalidateInternal();
            }
        } else {
            if (isChoreographerRegistered) {
                activeChoreographersCount--;
                isChoreographerRegistered = false;
                ticksWithoutDraw = 0;
                Choreographer60FpsContent.getInstance().removeFrameCallback(mUiThreadChoreographerCallback);
                // Log.i("CHOREOGRAPHER_DEBUG", "- LottieDrawable " + activeChoreographersCount);
            }
        }
    }
}
