/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Xfermode;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import android.view.View;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimatedFileDrawableStream;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.DispatchQueuePoolBackground;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.utils.BitmapsCache;
import org.telegram.messenger.utils.Choreographer60FpsContent;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

public final class AnimatedFileDrawable extends BitmapDrawable implements Animatable, BitmapsCache.Cacheable {

    public boolean skipFrameUpdate;
    public long currentTime;

    // canvas.drawPath lead to glitches
    // clipPath not use antialias
    private static final boolean USE_BITMAP_SHADER = true; // Build.VERSION.SDK_INT < 29;
    private boolean PRERENDER_FRAME;

    private long lastFrameTime;
    private int lastTimeStamp;
    private int invalidateAfter = 50;
    private final int[] metaData = new int[8];
    private Runnable loadFrameTask;
    private boolean isStaticVideoDetected;

    private final ArrayList<AnimatedFileBuffer> unusedBuffers = new ArrayList<>();
    private AnimatedFileBuffer renderingBuffer;
    private AnimatedFileBuffer nextRenderingBuffer;
    private AnimatedFileBuffer nextRenderingBuffer2;
    private AnimatedFileBuffer backgroundBuffer;

    private boolean destroyWhenDone;
    private boolean decoderCreated;
    private boolean decodeSingleFrame;
    private boolean singleFrameDecoded;
    private boolean forceDecodeAfterNextFrame;
    private final File path;
    private final long streamFileSize;
    private final int streamLoadingPriority;
    private final int currentAccount;
    private boolean recycleWithSecond;
    private volatile long pendingSeekTo = -1;
    private volatile long pendingSeekToUI = -1;
    private boolean pendingRemoveLoading;
    private int pendingRemoveLoadingFramesReset;
    private boolean isRestarted;
    private final Object sync = new Object();

    private boolean invalidateParentViewWithSecond;

    private long lastFrameDecodeTime;

    private final RectF actualDrawRect = new RectF();
    private final int[] roundRadius = new int[4];
    private int[] roundRadiusBackup;
    private final Matrix[] shaderMatrix = new Matrix[1 + DrawingInBackgroundThreadDrawable.THREAD_COUNT];
    private final Path[] roundPath = new Path[1 + DrawingInBackgroundThreadDrawable.THREAD_COUNT];
    private static final float[] radii = new float[8];

    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private boolean applyTransformation;
    private final RectF dstRect = new RectF();
    private volatile boolean isRunning;
    private volatile boolean isRecycled;
    private volatile AnimatedFileNative mDecoder;
    private boolean ptrFail;
    private DispatchQueue decodeQueue;
    private float startTime;
    private float endTime;
    private int renderingHeight;
    private int renderingWidth;
    private final boolean loop;
    private final boolean precache;
    private float scaleFactor = 1f;
    public boolean isWebmSticker;
    private final TLRPC.Document document;
    private final RectF[] dstRectBackground = new RectF[DrawingInBackgroundThreadDrawable.THREAD_COUNT];
    private final Paint[] backgroundPaint = new Paint[DrawingInBackgroundThreadDrawable.THREAD_COUNT];

    private View parentView;
    private final ArrayList<View> secondParentViews = new ArrayList<>();

    private final ArrayList<ImageReceiver> parents = new ArrayList<>();

    private AnimatedFileDrawableStream stream;

    private boolean useSharedQueue;
    private boolean invalidatePath = true;
    private boolean limitFps;

    public int repeatCount;
    private final BitmapsCache bitmapsCache;
    BitmapsCache.Metadata cacheMetadata;

    private static final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(8, new ThreadPoolExecutor.DiscardPolicy());

    private final Runnable uiRunnableNoFrame = this::uiRunnableNoFrameImpl;

    @UiThread
    private void uiRunnableNoFrameImpl() {
        chekDestroyDecoder();
        loadFrameTask = null;
        if (pendingSeekToUI >= 0 && pendingSeekTo == -1) {
            pendingSeekToUI = -1;
            invalidateAfter = 0;
        }
        scheduleNextGetFrame();
        invalidateInternal();
    }


    boolean generatingCache;
    Runnable cacheGenRunnable;
    private final Runnable uiRunnableGenerateCache = this::uiRunnableGenerateCacheImpl;

    @UiThread
    private void uiRunnableGenerateCacheImpl() {
        if (!isRecycled && !destroyWhenDone && !generatingCache && cacheGenRunnable == null) {
            startTime = System.currentTimeMillis();
            if (RLottieDrawable.lottieCacheGenerateQueue == null) {
                RLottieDrawable.createCacheGenQueue();
            }
            generatingCache = true;
            loadFrameTask = null;
            BitmapsCache.incrementTaskCounter();
            RLottieDrawable.lottieCacheGenerateQueue.postRunnable(cacheGenRunnable = () -> {
                bitmapsCache.createCache();
                AndroidUtilities.runOnUIThread(() -> {
                    if (cacheGenRunnable != null) {
                        BitmapsCache.decrementTaskCounter();
                        cacheGenRunnable = null;
                    }
                    generatingCache = false;
                    chekDestroyDecoder();
                    scheduleNextGetFrame();
                });
            });
        }
    }

    @UiThread
    private void chekDestroyDecoder() {
        if (!canLoadFrames()) {
            if (renderingBuffer != null) {
                renderingBuffer.recycle();
                renderingBuffer = null;
            }
            if (backgroundBuffer != null) {
                backgroundBuffer.recycle();
                backgroundBuffer = null;
            }
            if (decodeQueue != null) {
                decodeQueue.recycle();
                decodeQueue = null;
            }
            for (int i = 0; i < unusedBuffers.size(); i++) {
                unusedBuffers.get(i).recycle();
            }
            unusedBuffers.clear();
            invalidateInternal();
        }
    }

    @UiThread
    public void invalidateInternal() {
        for (int i = 0; i < parents.size(); i++) {
            parents.get(i).invalidate();
        }
    }

    private final Runnable uiRunnable = this::uiRunnableImpl;

    @UiThread
    private void uiRunnableImpl() {
        chekDestroyDecoder();
        if (stream != null && pendingRemoveLoading) {
            FileLoader.getInstance(currentAccount).removeLoadingVideo(stream.getDocument(), false, false);
        }
        if (pendingRemoveLoadingFramesReset <= 0) {
            pendingRemoveLoading = true;
        } else {
            pendingRemoveLoadingFramesReset--;
        }
        if (!forceDecodeAfterNextFrame) {
            singleFrameDecoded = true;
        } else {
            forceDecodeAfterNextFrame = false;
        }
        loadFrameTask = null;

        if (pendingSeekToUI >= 0) {
            nextRenderingBuffer = backgroundBuffer;
            nextRenderingBuffer2 = null;
        } else if (!PRERENDER_FRAME) {
            nextRenderingBuffer = backgroundBuffer;
        } else {
            if (nextRenderingBuffer == null && nextRenderingBuffer2 == null) {
                nextRenderingBuffer = backgroundBuffer;
            } else if (nextRenderingBuffer == null) {
                // nextRenderingBuffer2 != null
                nextRenderingBuffer = nextRenderingBuffer2;
                nextRenderingBuffer2 = backgroundBuffer;
            } else {
                // nextRenderingBuffer != null || nextRenderingBuffer2 != null
                nextRenderingBuffer2 = backgroundBuffer;
            }
        }
        backgroundBuffer = null;

        if (isRestarted) {
            isRestarted = false;
            repeatCount++;
            checkRepeat();
        }

        if (metaData[3] < lastTimeStamp) {
            lastTimeStamp = startTime > 0 ? (int) (startTime * 1000) : 0;
        }
        if (metaData[3] - lastTimeStamp != 0) {
            invalidateAfter = metaData[3] - lastTimeStamp;
            if (limitFps && invalidateAfter < 32) {
                invalidateAfter = 32;
            }
        }
        if (pendingSeekToUI >= 0 && pendingSeekTo == -1) {
            pendingSeekToUI = -1;
            invalidateAfter = 0;
        }
        lastTimeStamp = metaData[3];
        for (int a = 0, N = secondParentViews.size(); a < N; a++) {
            secondParentViews.get(a).invalidate();
        }
        // Static frame: Choreographer won't tick when !isRunning, invalidate manually.
        if (!isRunning && decodeSingleFrame || renderingBuffer == null && nextRenderingBuffer != null) {
            invalidateInternal();
        }
        scheduleNextGetFrame();
    }



    public void checkRepeat() {
        int count = 0;
        for (int j = 0; j < parents.size(); j++) {
            ImageReceiver parent = parents.get(j);
            if (!parent.isAttachedToWindow()) {
                parents.remove(j);
                j--;
            }
            if (parent.animatedFileDrawableRepeatMaxCount > 0 && repeatCount >= parent.animatedFileDrawableRepeatMaxCount) {
                count++;
            }
        }
        if (parents.size() == count) {
            stop();
        } else {
            start();
        }
    }

    private int decoderTryCount = 0;
    private final int MAX_TRIES = 15;
    private final Runnable loadFrameRunnable = this::loadFrameRunnableImpl;

    @WorkerThread
    private void loadFrameRunnableImpl() {
        if (isRecycled) {
            AndroidUtilities.runOnUIThread(uiRunnable);
            return;
        }

        if (!decoderCreated && mDecoder == null) {
            mDecoder = AnimatedFileNative.createDecoderFrom(path.getAbsolutePath(), metaData, currentAccount, streamFileSize, stream, false);
            ptrFail = mDecoder == null && (!isWebmSticker || decoderTryCount > MAX_TRIES);
            if (mDecoder != null && (metaData[0] > 3840 || metaData[1] > 3840)) {
                mDecoder.recycle();
                mDecoder = null;
            }
            adaptRenderingSize();
            updateScaleFactor();
            decoderCreated = !isWebmSticker || mDecoder != null || (decoderTryCount++) > MAX_TRIES;
            AndroidUtilities.runOnUIThread(AnimatedFileDrawable.this::checkChoreographerInternal);
        }
        try {
            if (bitmapsCache != null) {
                if (backgroundBuffer == null) {
                    if (!unusedBuffers.isEmpty()) {
                        backgroundBuffer = unusedBuffers.remove(0);
                    } else {
                        backgroundBuffer = AnimatedFileBuffer.of(renderingWidth, renderingHeight);
                    }
                }
                if (cacheMetadata == null) {
                    cacheMetadata = new BitmapsCache.Metadata();
                }
                lastFrameDecodeTime = System.currentTimeMillis();
                int lastFrame = cacheMetadata.frame;
                int result = bitmapsCache.getFrame(backgroundBuffer.bitmap, cacheMetadata);
                if (result != -1 && cacheMetadata.frame < lastFrame) {
                    isRestarted = true;
                }
                metaData[3] = backgroundBuffer.time = cacheMetadata.frame * Math.max(16, metaData[4] / Math.max(1, bitmapsCache.getFrameCount()));
                backgroundBuffer.opaque = false; // unknown

                if (bitmapsCache.needGenCache()) {
                    AndroidUtilities.runOnUIThread(uiRunnableGenerateCache);
                }
                if (result == -1) {
                    AndroidUtilities.runOnUIThread(uiRunnableNoFrame);
                } else {
                    AndroidUtilities.runOnUIThread(uiRunnable);
                }
                return;
            }

            if (mDecoder != null || metaData[0] == 0 || metaData[1] == 0) {
                if (backgroundBuffer == null && metaData[0] > 0 && metaData[1] > 0) {
                    try {
                        if (!unusedBuffers.isEmpty()) {
                            backgroundBuffer = unusedBuffers.remove(0);
                        } else {
                            backgroundBuffer = AnimatedFileBuffer.of((int) (metaData[0] * scaleFactor), (int) (metaData[1] * scaleFactor));
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
                boolean seekWas = false;
                if (pendingSeekTo >= 0) {
                    metaData[3] = (int) pendingSeekTo;
                    long seekTo = pendingSeekTo;
                    synchronized (sync) {
                        pendingSeekTo = -1;
                    }
                    seekWas = true;
                    if (stream != null) {
                        stream.reset();
                    }
                    mDecoder.seekToMs(seekTo, true);
                }
                if (backgroundBuffer != null) {
                    lastFrameDecodeTime = System.currentTimeMillis();

                    if (mDecoder.getVideoFrame(backgroundBuffer.bitmap, false, startTime, endTime, loop) == 0) {
                        AndroidUtilities.runOnUIThread(uiRunnableNoFrame);
                        return;
                    }
                    if (!isStaticVideoDetected) {
                        isStaticVideoDetected = mDecoder.isStaticVideoDetected();
                    }
                    if (metaData[3] < lastTimeStamp) {
                        isRestarted = true;
                    }
                    if (seekWas) {
                        lastTimeStamp = metaData[3];
                    }

                    backgroundBuffer.time = metaData[3];
                    backgroundBuffer.opaque = mDecoder.isLastFrameOpaque();
                }
            } else {
                AndroidUtilities.runOnUIThread(uiRunnableNoFrame);
                return;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        AndroidUtilities.runOnUIThread(uiRunnable);
    }




    private void adaptRenderingSize() {
        if (renderingWidth == 0 && renderingHeight == 0) {
            if (metaData[0] > 3000 || metaData[1] > 3000) {
                renderingWidth = metaData[0] / 4;
                renderingHeight = metaData[1] / 4;
            } else if (metaData[0] > 2200 || metaData[1] > 2200) {
                renderingWidth = metaData[0] / 2;
                renderingHeight = metaData[1] / 2;
            }
        }
    }

    private void updateScaleFactor() {
        if (!isWebmSticker && renderingHeight > 0 && renderingWidth > 0 && metaData[0] > 0 && metaData[1] > 0) {
            scaleFactor = Math.max(renderingWidth / (float) metaData[0], renderingHeight / (float) metaData[1]);
            if (scaleFactor <= 0 || scaleFactor > 0.7) {
                scaleFactor = 1;
            }
        } else {
            scaleFactor = 1f;
        }
    }

    private final Runnable mStartTask = this::uiStartTaskImpl;

    @UiThread
    private void uiStartTaskImpl() {
        for (int a = 0, N = secondParentViews.size(); a < N; a++) {
            secondParentViews.get(a).invalidate();
        }
        if ((secondParentViews.isEmpty() || invalidateParentViewWithSecond) && parentView != null) {
            parentView.invalidate();
        }
    }

    public AnimatedFileDrawable(File file, boolean createDecoder, long streamSize, int streamLoadingPriority, TLRPC.Document document, ImageLocation location, Object parentObject, long seekTo, int account, boolean preview, BitmapsCache.CacheOptions cacheOptions) {
        this(file, createDecoder, streamSize, streamLoadingPriority, document, location, parentObject, seekTo, account, preview, 0, 0, cacheOptions);
    }

    public AnimatedFileDrawable(File file, boolean createDecoder, long streamSize, int streamLoadingPriority, TLRPC.Document document, ImageLocation location, Object parentObject, long seekTo, int account, boolean preview, int w, int h, BitmapsCache.CacheOptions cacheOptions) {
        this(file, createDecoder, streamSize, streamLoadingPriority, document, location, parentObject, seekTo, account, preview, w, h, cacheOptions, document != null ? 1 : 0, true);
    }

    public AnimatedFileDrawable(File file, boolean createDecoder, long streamSize, int streamLoadingPriority, TLRPC.Document document, ImageLocation location, Object parentObject, long seekTo, int account, boolean preview, int w, int h, BitmapsCache.CacheOptions cacheOptions, int cacheType, boolean loop) {
        path = file;
        PRERENDER_FRAME = SharedConfig.deviceIsAboveAverage();
        streamFileSize = streamSize;
        this.streamLoadingPriority = streamLoadingPriority;
        currentAccount = account;
        renderingHeight = h;
        renderingWidth = w;
        this.loop = loop;
        this.precache = cacheOptions != null && renderingWidth > 0 && renderingHeight > 0;
        this.document = document;
        getPaint().setFlags(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (streamSize != 0 && (document != null || location != null)) {
            stream = new AnimatedFileDrawableStream(document, location, parentObject, account, preview, streamLoadingPriority, cacheType);
        }
        if (createDecoder && !this.precache) {
            mDecoder = AnimatedFileNative.createDecoderFrom(file.getAbsolutePath(), metaData, currentAccount, streamFileSize, stream, preview);
            ptrFail = mDecoder == null && (!isWebmSticker || decoderTryCount > MAX_TRIES);
            if (mDecoder != null && (metaData[0] > 3840 || metaData[1] > 3840)) {
                mDecoder.recycle();
                mDecoder = null;
            }
            adaptRenderingSize();
            updateScaleFactor();
            decoderCreated = true;
        }
        BitmapsCache bitmapsCache = null;
        if (this.precache) {
            mDecoder = AnimatedFileNative.createDecoderFrom(file.getAbsolutePath(), metaData, currentAccount, streamFileSize, stream, preview);
            ptrFail = mDecoder == null && (!isWebmSticker || decoderTryCount > MAX_TRIES);
            if (mDecoder != null && (metaData[0] > 3840 || metaData[1] > 3840)) {
                mDecoder.recycle();
                mDecoder = null;
            } else {
                bitmapsCache = new BitmapsCache(file, this, cacheOptions, renderingWidth, renderingHeight, !limitFps);
            }
        }
        this.bitmapsCache = bitmapsCache;
        if (seekTo != 0) {
            seekTo(seekTo, false);
        }
    }

    // call after constructor
    public void setIsWebmSticker(boolean b) {
        isWebmSticker = b;
        if (isWebmSticker) {
            PRERENDER_FRAME = false;
            useSharedQueue = true;
        }
    }

    // call after constructor
    public void setLimitFps(boolean limitFps) {
        this.limitFps = limitFps;
        if (limitFps) {
            PRERENDER_FRAME = false;
        }
    }



    @AnyThread
    @Nullable
    public Bitmap getFrameAtTime(long ms) {
        return getFrameAtTime(ms, false);
    }

    @AnyThread
    @Nullable
    public Bitmap getFrameAtTime(long ms, boolean precise) {
        if (!decoderCreated || mDecoder == null) {
            return null;
        }
        if (stream != null) {
            stream.cancel(false);
            stream.reset();
        }
        if (!precise) {
            mDecoder.seekToMs(ms, precise);
        }
        Bitmap backgroundBitmap = Bitmap.createBitmap(metaData[0], metaData[1], Bitmap.Config.ARGB_8888);
        int result;
        if (precise) {
            result = mDecoder.getFrameAtTime(ms, backgroundBitmap);
        } else {
            result = mDecoder.getVideoFrame(backgroundBitmap, true, (float) 0, (float) 0, true);
        }
        if (result != 0) {
            return backgroundBitmap;
        } else {
            backgroundBitmap.recycle();
            return null;
        }
    }

    public void setParentView(View view) {
        if (parentView != null) {
            return;
        }
        parentView = view;
    }

    public void addParent(ImageReceiver imageReceiver) {
        if (imageReceiver != null && !parents.contains(imageReceiver)) {
            parents.add(imageReceiver);
            if (isRunning) {
                scheduleNextGetFrame();
            }
        }
        checkCacheCancel();
    }

    public void removeParent(ImageReceiver imageReceiver) {
        parents.remove(imageReceiver);
        if (parents.isEmpty()) {
            repeatCount = 0;
        }
        checkCacheCancel();
    }

    private Runnable cancelCache;

    public void checkCacheCancel() {
        if (bitmapsCache == null) {
            return;
        }
        boolean mustCancel = parents.isEmpty();
        if (mustCancel && cancelCache == null) {
            AndroidUtilities.runOnUIThread(cancelCache = () -> {
                if (bitmapsCache != null) {
                    bitmapsCache.cancelCreate();
                }
            }, 600);
        } else if (!mustCancel && cancelCache != null) {
            AndroidUtilities.cancelRunOnUIThread(cancelCache);
            cancelCache = null;
        }
    }

    public void setInvalidateParentViewWithSecond(boolean value) {
        invalidateParentViewWithSecond = value;
    }

    public void addSecondParentView(View view) {
        if (view == null || secondParentViews.contains(view)) {
            return;
        }
        secondParentViews.add(view);
    }

    public void removeSecondParentView(View view) {
        secondParentViews.remove(view);
        if (secondParentViews.isEmpty()) {
            if (recycleWithSecond) {
                recycle();
            } else {
                if (roundRadiusBackup != null) {
                    setRoundRadius(roundRadiusBackup);
                }
            }
        }
    }

    public void setAllowDecodeSingleFrame(boolean value) {
        decodeSingleFrame = value;
        if (decodeSingleFrame) {
            scheduleNextGetFrame();
        }
    }

    public void seekTo(long ms, boolean removeLoading) {
        seekTo(ms, removeLoading, false);
    }

    public void seekTo(long ms, boolean removeLoading, boolean force) {
        synchronized (sync) {
            pendingSeekTo = ms;
            pendingSeekToUI = ms;
            scheduledForSeek = false;
            if (mDecoder != null) {
                mDecoder.prepareToSeek();
            }
            if (decoderCreated && stream != null) {
                stream.cancel(removeLoading);
                pendingRemoveLoading = removeLoading;
                pendingRemoveLoadingFramesReset = pendingRemoveLoading ? 0 : 10;
            }
            if (force && decodeSingleFrame) {
                singleFrameDecoded = false;
                if (loadFrameTask == null) {
                    scheduleNextGetFrame(false, true);
                } else {
                    forceDecodeAfterNextFrame = true;
                }
            }
        }
    }

    public void seekToSync(long ms) {
        if (mDecoder == null) return;
        mDecoder.seekToMs(ms, true);
    }

    public void recycle() {
        if (!secondParentViews.isEmpty()) {
            recycleWithSecond = true;
            return;
        }
        isRunning = false;
        isRecycled = true;
        checkChoreographer();
        if (cacheGenRunnable != null) {
            BitmapsCache.decrementTaskCounter();
            RLottieDrawable.lottieCacheGenerateQueue.cancelRunnable(cacheGenRunnable);
            cacheGenRunnable = null;
        }
        if (loadFrameTask == null) {
            if (mDecoder != null) {
                mDecoder.recycle();
                mDecoder = null;
            }

            ArrayList<Bitmap> bitmapToRecycle = new ArrayList<>();
            if (renderingBuffer != null) {
                bitmapToRecycle.add(renderingBuffer.bitmap);
            }
            if (nextRenderingBuffer != null) {
                bitmapToRecycle.add(nextRenderingBuffer.bitmap);
            }
            if (nextRenderingBuffer2 != null) {
                bitmapToRecycle.add(nextRenderingBuffer2.bitmap);
            }
            if (backgroundBuffer != null) {
                bitmapToRecycle.add(backgroundBuffer.bitmap);
            }
            for (AnimatedFileBuffer buffer : unusedBuffers) {
                if (buffer != null) {
                    bitmapToRecycle.add(buffer.bitmap);
                }
            }

          //  unusedBuffers.remove(backgroundBuffer);
            unusedBuffers.clear();
            renderingBuffer = null;
            nextRenderingBuffer = null;
            nextRenderingBuffer2 = null;
            backgroundBuffer = null;

            if (decodeQueue != null) {
                decodeQueue.recycle();
                decodeQueue = null;
            }
            getPaint().setShader(null);
            AndroidUtilities.recycleBitmaps(bitmapToRecycle);
        } else {
            destroyWhenDone = true;
        }
        if (stream != null) {
            stream.cancel(true);
            stream = null;
        }
        invalidateInternal();
    }

    public void resetStream(boolean stop) {
        if (stream != null) {
            stream.cancel(true);
        }
        if (mDecoder != null) {
            if (stop) {
                mDecoder.stopDecoder();
            } else {
                mDecoder.prepareToSeek();
            }
        }
    }

    public void setUseSharedQueue(boolean value) {
        if (isWebmSticker) {
            return;
        }
        useSharedQueue = value;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            secondParentViews.clear();
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
    @AnyThread
    public void start() {
        if (isRunning || parents.isEmpty()) {
            return;
        }
        isRunning = true;
        isPaused = false;
        scheduleNextGetFrame();
        AndroidUtilities.runOnUIThread(mStartTask);
        checkChoreographer();
    }

    public float getCurrentProgress() {
        if (metaData[4] == 0) {
            return 0;
        }
        if (pendingSeekToUI >= 0) {
            return pendingSeekToUI / (float) metaData[4];
        }
        return metaData[3] / (float) metaData[4];
    }

    public int getCurrentProgressMs() {
        if (pendingSeekToUI >= 0) {
            return (int) pendingSeekToUI;
        }
        if (nextRenderingBuffer != null && nextRenderingBuffer.time != 0) {
            return nextRenderingBuffer.time;
        }
        if (renderingBuffer != null) {
            return renderingBuffer.time;
        }
        return 0;
    }

    public int getProgressMs() {
        return metaData[3];
    }

    public int getDurationMs() {
        return metaData[4];
    }

    @AnyThread
    private void scheduleNextGetFrame() {
        scheduleNextGetFrame(true, false);
    }
    private boolean scheduledForSeek;

    @AnyThread  // maybe ui thread only
    private void scheduleNextGetFrame(boolean wait, boolean cancel) {
        final boolean ignoreScheduleNext = loadFrameTask != null && !cancel
            || (!PRERENDER_FRAME || nextRenderingBuffer2 != null && !(!scheduledForSeek && pendingSeekToUI >= 0)) && nextRenderingBuffer != null
            || renderingBuffer != null && isStaticVideoDetected
            || !canLoadFrames()
            || destroyWhenDone
            || !isRunning && (!decodeSingleFrame || singleFrameDecoded)
            || parents.isEmpty()
            || generatingCache;

        if (ignoreScheduleNext) {
            return;
        }
        // Choreographer owns the timing — always start decoding immediately
        // so the next frame is ready before the next tick arrives.
        if (useSharedQueue) {
            if (limitFps) {
                DispatchQueuePoolBackground.execute(loadFrameTask = loadFrameRunnable);
            } else {
                if (cancel && loadFrameTask != null) {
                    executor.remove(loadFrameTask);
                }
                executor.execute(loadFrameTask = loadFrameRunnable);
            }
        } else {
            if (decodeQueue == null) {
                decodeQueue = new DispatchQueue("decodeQueue" + this);
            }
            if (cancel && loadFrameTask != null) {
                decodeQueue.cancelRunnable(loadFrameTask);
            }
            decodeQueue.postRunnable(loadFrameTask = loadFrameRunnable, 0);
        }
        scheduledForSeek = true;
    }

    public boolean isLoadingStream() {
        return stream != null && stream.isWaitingForLoad();
    }

    @Override
    public void stop() {
        isRunning = false;
        checkChoreographer();
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public int getIntrinsicHeight() {
        int height = decoderCreated ? (metaData[2] == 90 || metaData[2] == 270 ? metaData[0] : metaData[1]) : 0;
        if (height == 0) {
            return dp(100);
        } else {
            height = (int) (height * scaleFactor);
        }
        return height;
    }

    @Override
    public int getIntrinsicWidth() {
        int width = decoderCreated ? (metaData[2] == 90 || metaData[2] == 270 ? metaData[1] : metaData[0]) : 0;
        if (width == 0) {
            return dp(100);
        } else {
            width = (int) (width * scaleFactor);
        }
        return width;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        applyTransformation = true;
    }

    @Override
    @AnyThread
    public void draw(Canvas canvas) {
        drawInternal(canvas, false, System.currentTimeMillis(), 0);
    }

    @WorkerThread
    public void drawInBackground(Canvas canvas, float x, float y, float w, float h, int alpha, ColorFilter colorFilter, int threadIndex) {
        if (dstRectBackground[threadIndex] == null) {
            dstRectBackground[threadIndex] = new RectF();
            backgroundPaint[threadIndex] = new Paint();
            backgroundPaint[threadIndex].setFilterBitmap(true);
        }
        backgroundPaint[threadIndex].setAlpha(alpha);
        backgroundPaint[threadIndex].setColorFilter(colorFilter);
        dstRectBackground[threadIndex].set(x, y, x + w, y + h);
        drawInternal(canvas, true, 0, threadIndex);
    }

    private static final Xfermode SRC_XFERMODE = new PorterDuffXfermode(PorterDuff.Mode.SRC);

    @AnyThread
    public void drawInternal(Canvas canvas, boolean drawInBackground, long currentTime, int threadIndex) {
        if (!canLoadFrames() || destroyWhenDone) {
            return;
        }

        if (currentTime == 0) {
            currentTime = System.currentTimeMillis();
        }

        final RectF rect = drawInBackground ? dstRectBackground[threadIndex] : dstRect;
        final Paint paint = drawInBackground ? backgroundPaint[threadIndex] : getPaint();

        if (!drawInBackground) {
            updateCurrentFrame(currentTime, false);
        }

        if (renderingBuffer == null) {
            return;
        }

        final boolean hasRoundRadius = hasRoundRadius();
        if (!drawInBackground) {
            final Xfermode xfermodeToSet = !hasRoundRadius && renderingBuffer.opaque && paint.getAlpha() == 255 ? SRC_XFERMODE : null;
            if (paint.getXfermode() != xfermodeToSet) {
                paint.setXfermode(xfermodeToSet);
            }
        }

        float scaleX = this.scaleX;
        float scaleY = this.scaleY;
        if (drawInBackground) {
            int bitmapW = renderingBuffer.width;
            int bitmapH = renderingBuffer.height;
            if (metaData[2] == 90 || metaData[2] == 270) {
                int temp = bitmapW;
                bitmapW = bitmapH;
                bitmapH = temp;
            }
            scaleX = rect.width() / bitmapW;
            scaleY = rect.height() / bitmapH;
        } else if (applyTransformation) {
            int bitmapW = renderingBuffer.width;
            int bitmapH = renderingBuffer.height;
            if (metaData[2] == 90 || metaData[2] == 270) {
                int temp = bitmapW;
                bitmapW = bitmapH;
                bitmapH = temp;
            }
            rect.set(getBounds());
            this.scaleX = scaleX = rect.width() / bitmapW;
            this.scaleY = scaleY = rect.height() / bitmapH;
            applyTransformation = false;
        }

        if (hasRoundRadius) {
            int index = drawInBackground ? threadIndex + 1 : 0;
            if (USE_BITMAP_SHADER) {
                final Shader shader = renderingBuffer.getShader(index);
                paint.setShader(shader);
                Matrix matrix = shaderMatrix[index];
                if (matrix == null) {
                    matrix = shaderMatrix[index] = new Matrix();
                }
                matrix.reset();
                matrix.setTranslate(rect.left, rect.top);
                if (metaData[2] == 90) {
                    matrix.preRotate(90);
                    matrix.preTranslate(0, -rect.width());
                } else if (metaData[2] == 180) {
                    matrix.preRotate(180);
                    matrix.preTranslate(-rect.width(), -rect.height());
                } else if (metaData[2] == 270) {
                    matrix.preRotate(270);
                    matrix.preTranslate(-rect.height(), 0);
                }
                matrix.preScale(scaleX, scaleY);
                shader.setLocalMatrix(matrix);
            }

            Path path = roundPath[index];
            if (path == null) {
                path = roundPath[index] = new Path();
            }
            if (invalidatePath || drawInBackground) {
                if (!drawInBackground) {
                    invalidatePath = false;
                }
                for (int a = 0; a < roundRadius.length; a++) {
                    radii[a * 2] = roundRadius[a];
                    radii[a * 2 + 1] = roundRadius[a];
                }
                path.rewind();
                path.addRoundRect(drawInBackground ? rect : actualDrawRect, radii, Path.Direction.CW);
            }
            if (USE_BITMAP_SHADER) {
                if (isRoundRadiusSame()) {
                    canvas.drawRoundRect(drawInBackground ? rect : actualDrawRect, roundRadius[0], roundRadius[0], paint);
                } else {
                    canvas.drawPath(path, paint);
                }
            } else {
                canvas.save();
                canvas.clipPath(path);
                drawBitmap(rect, paint, canvas, scaleX, scaleY);
                canvas.restore();
            }
        } else {
            drawBitmap(rect, paint, canvas, scaleX, scaleY);
        }
    }

    @AnyThread
    private void drawBitmap(RectF rect, Paint paint, Canvas canvas, float sx, float sy) {
        canvas.save();
        canvas.translate(rect.left, rect.top);
        if (metaData[2] == 90) {
            canvas.rotate(90);
            canvas.translate(0, -rect.width());
        } else if (metaData[2] == 180) {
            canvas.rotate(180);
            canvas.translate(-rect.width(), -rect.height());
        } else if (metaData[2] == 270) {
            canvas.rotate(270);
            canvas.translate(-rect.height(), 0);
        }
        canvas.scale(sx, sy);
        canvas.drawBitmap(renderingBuffer.bitmap, 0, 0, paint);
        canvas.restore();
    }

    public long getLastFrameTimestamp() {
        return lastTimeStamp;
    }

    @Override
    public int getMinimumHeight() {
        int height = decoderCreated ? (metaData[2] == 90 || metaData[2] == 270 ? metaData[0] : metaData[1]) : 0;
        if (height == 0) {
            return dp(100);
        }
        return height;
    }

    @Override
    public int getMinimumWidth() {
        int width = decoderCreated ? (metaData[2] == 90 || metaData[2] == 270 ? metaData[1] : metaData[0]) : 0;
        if (width == 0) {
            return dp(100);
        }
        return width;
    }

    public Bitmap getBackgroundBitmap() {
        return backgroundBuffer != null ? backgroundBuffer.bitmap : null;
    }

    public Bitmap getAnimatedBitmap() {
        if (renderingBuffer != null) {
            return renderingBuffer.bitmap;
        } else if (nextRenderingBuffer != null) {
            return nextRenderingBuffer.bitmap;
        } else if (nextRenderingBuffer2 != null) {
            return nextRenderingBuffer2.bitmap;
        }
        return null;
    }

    public void replaceAnimatedBitmap(Bitmap b) {
        if (renderingBuffer != null) {
            unusedBuffers.add(renderingBuffer);
        }
        if (nextRenderingBuffer != null) {
            unusedBuffers.add(nextRenderingBuffer);
        }
        if (nextRenderingBuffer2 != null) {
            unusedBuffers.add(nextRenderingBuffer2);
        }
        renderingBuffer = AnimatedFileBuffer.of(b);
        nextRenderingBuffer = null;
        nextRenderingBuffer2 = null;
    }

    public void setActualDrawRect(float x, float y, float width, float height) {
        float bottom = y + height;
        float right = x + width;
        if (actualDrawRect.left != x || actualDrawRect.top != y || actualDrawRect.right != right || actualDrawRect.bottom != bottom) {
            actualDrawRect.set(x, y, right, bottom);
            invalidatePath = true;
        }
    }

    public void setRoundRadius(int[] value) {
        if (!secondParentViews.isEmpty()) {
            if (roundRadiusBackup == null) {
                roundRadiusBackup = new int[4];
            }
            System.arraycopy(roundRadius, 0, roundRadiusBackup, 0, roundRadiusBackup.length);
        }
        for (int i = 0; i < 4; i++) {
            if (!invalidatePath && value[i] != roundRadius[i]) {
                invalidatePath = true;
            }
            roundRadius[i] = value[i];
        }
    }

    private boolean hasRoundRadius() {
        for (int radius : roundRadius) {
            if (radius != 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isRoundRadiusSame() {
        return roundRadius[0] == roundRadius[1]
            && roundRadius[1] == roundRadius[2]
            && roundRadius[2] == roundRadius[3];
    }


    public boolean hasBitmap() {
        return canLoadFrames() && (renderingBuffer != null || nextRenderingBuffer != null);
    }

    public int getOrientation() {
        return metaData[2];
    }

    public AnimatedFileDrawable makeCopy() {
        AnimatedFileDrawable drawable;
        if (stream != null) {
            drawable = new AnimatedFileDrawable(path, false, streamFileSize, streamLoadingPriority, stream.getDocument(), stream.getLocation(), stream.getParentObject(), pendingSeekToUI, currentAccount, stream != null && stream.isPreview(), null);
        } else {
            drawable = new AnimatedFileDrawable(path, false, streamFileSize, streamLoadingPriority, document, null, null, pendingSeekToUI, currentAccount, false, null);
        }
        drawable.metaData[0] = metaData[0];
        drawable.metaData[1] = metaData[1];
        return drawable;
    }

    public void setStartEndTime(long startTime, long endTime) {
        this.startTime = startTime / 1000f;
        this.endTime = endTime / 1000f;
        if (startTime >= 0 && getCurrentProgressMs() < startTime) {
            seekTo(startTime, true);
        }
    }

    public long getStartTime() {
        return (long) (startTime * 1000);
    }

    public boolean isRecycled() {
        return isRecycled || decoderTryCount >= MAX_TRIES;
    }

    public boolean decoderFailed() {
        return decoderCreated && ptrFail;
    }

    @AnyThread
    public Bitmap getNextFrame(boolean loop) {
        if (mDecoder == null) {
            return backgroundBuffer != null ? backgroundBuffer.bitmap : null;
        }
        if (backgroundBuffer == null) {
            if (!unusedBuffers.isEmpty()) {
                backgroundBuffer = unusedBuffers.remove(0);
            } else {
                backgroundBuffer = AnimatedFileBuffer.of((int) (metaData[0] * scaleFactor), (int) (metaData[1] * scaleFactor));
            }
        }
        mDecoder.getVideoFrame(backgroundBuffer.bitmap, false, startTime, endTime, loop);
        return backgroundBuffer.bitmap;
    }

    public void skipNextFrame(boolean loop) {
        if (mDecoder == null) {
            return;
        }
        mDecoder.getVideoFrame(null, false, startTime, endTime, loop);
    }



    public ArrayList<ImageReceiver> getParents() {
        return parents;
    }

    public File getFilePath() {
        return path;
    }

    long cacheGenerateTimestamp;
    Bitmap generatingCacheBitmap;
    AnimatedFileNative cacheGenerateDecoder;
    int tryCount;
    int lastMetadata;

    @Override
    @AnyThread
    public void prepareForGenerateCache() {
        cacheGenerateDecoder = AnimatedFileNative.createDecoderFrom(path.getAbsolutePath(), metaData, currentAccount, streamFileSize, stream, false);
    }

    @Override
    @AnyThread
    public void releaseForGenerateCache() {
        if (cacheGenerateDecoder != null) {
            cacheGenerateDecoder.recycle();
            cacheGenerateDecoder = null;
        }
    }

    @Override
    @AnyThread
    public int getNextFrame(Bitmap bitmap) {
        if (cacheGenerateDecoder == null) {
            return -1;
        }
        Canvas canvas = new Canvas(bitmap);
        if (generatingCacheBitmap == null) {
            generatingCacheBitmap = Bitmap.createBitmap(metaData[0], metaData[1], Bitmap.Config.ARGB_8888);
        }
        cacheGenerateDecoder.getVideoFrame(generatingCacheBitmap, false, startTime, endTime, this.loop);
        if (cacheGenerateTimestamp != 0 && (metaData[3] == 0 || cacheGenerateTimestamp > metaData[3])) {
            return 0;
        }
        if (lastMetadata == metaData[3]) {
            tryCount++;
            if (tryCount > 5) {
                return 0;
            }
        }
        lastMetadata = metaData[3];
        bitmap.eraseColor(Color.TRANSPARENT);
        canvas.save();
        float s = (float) renderingWidth / generatingCacheBitmap.getWidth();
        canvas.scale(s, s);
        canvas.drawBitmap(generatingCacheBitmap, 0, 0, null);
        canvas.restore();
        cacheGenerateTimestamp = metaData[3];
        return 1;
    }

    @AnyThread
    public Bitmap getFirstFrame(Bitmap bitmap) {
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(renderingWidth, renderingHeight, Bitmap.Config.ARGB_8888);
        }
        Canvas canvas = new Canvas(bitmap);

        AnimatedFileNative tempDecoder = AnimatedFileNative.createDecoderFrom(path.getAbsolutePath(), metaData, currentAccount, streamFileSize, stream, false);
        if (tempDecoder == null) {
            return bitmap;
        }
        if (generatingCacheBitmap == null) {
            generatingCacheBitmap = Bitmap.createBitmap(Math.max(1, metaData[0]), Math.max(1, metaData[1]), Bitmap.Config.ARGB_8888);
        }
        tempDecoder.getVideoFrame(generatingCacheBitmap, false, startTime, endTime, true);
        tempDecoder.recycle();
        bitmap.eraseColor(Color.TRANSPARENT);
        canvas.save();
        float s = (float) renderingWidth / generatingCacheBitmap.getWidth();
        canvas.scale(s, s);
        canvas.drawBitmap(generatingCacheBitmap, 0, 0, null);
        canvas.restore();

        return bitmap;
    }

    private boolean canLoadFrames() {
        if (precache) {
            return bitmapsCache != null;
        } else {
            return mDecoder != null || !decoderCreated;
        }
    }

    @UiThread
    public void updateCurrentFrame(long now, boolean b) {
        checkChoreographerAfterDrawCall();
        updateCurrentFrameInternal(now, b);
    }

    @UiThread
    private void updateCurrentFrameInternal(long now, boolean updateInBackground) {
        // final boolean canSwapBuffers = Math.abs(now - lastFrameTime) >= invalidateAfter;
        final boolean canSwapBuffers = swapBuffersAllowedByChoreographer
            || !isRunning && decodeSingleFrame;

        if (isRunning) {
            if (renderingBuffer == null && nextRenderingBuffer == null) {
                scheduleNextGetFrame();
            } else if (nextRenderingBuffer != null && (renderingBuffer == null || (canSwapBuffers && !skipFrameUpdate && pendingSeekToUI < 0))) {
                swapBuffers(now);
                scheduleNextGetFrame();
            }
        } else if (!isRunning && decodeSingleFrame && canSwapBuffers && nextRenderingBuffer != null) {
            swapBuffers(now);
            scheduleNextGetFrame();
        }
    }

    @UiThread
    private void swapBuffers(long now) {
        if (renderingBuffer != null) {
            unusedBuffers.add(renderingBuffer);
        }
        renderingBuffer = nextRenderingBuffer;
        nextRenderingBuffer = nextRenderingBuffer2;
        nextRenderingBuffer2 = null;
        lastFrameTime = now;
        swapBuffersAllowedByChoreographer = false;
    }

    public int getFps() {
        return metaData[5];
    }

    public int estimateSizeInCache() {
        final int intrinsicSize = getIntrinsicWidth() * getIntrinsicHeight();
        final int renderingSize = renderingWidth * renderingHeight;
        return Math.max(intrinsicSize, renderingSize) * 4 * 3;
    }






    // ── Choreographer integration ─────────────────────────────────────────────

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
    private void checkChoreographer() {
        AndroidUtilities.executeOnUIThread(this::checkChoreographerInternal);
    }

    private static int activeChoreographersCount;
    private boolean isChoreographerRegistered;

    @UiThread
    private void checkChoreographerInternal() {
        if (isRunning && !isPaused && !isStaticVideoDetected) {
            if (!isChoreographerRegistered) {
                final int fps = metaData[5];
                if (fps <= 0) {
                    return;
                }
                activeChoreographersCount++;
                isChoreographerRegistered = true;
                ticksWithoutDraw = 0;
                Choreographer60FpsContent.getInstance().addFrameCallback(mUiThreadChoreographerCallback, fps);
                // Log.i("CHOREOGRAPHER_DEBUG", "+ AnimatedFileDrawable " + activeChoreographersCount + " fps: " + fps);
            }
        } else {
            if (isChoreographerRegistered) {
                activeChoreographersCount--;
                isChoreographerRegistered = false;
                ticksWithoutDraw = 0;
                Choreographer60FpsContent.getInstance().removeFrameCallback(mUiThreadChoreographerCallback);
                // Log.i("CHOREOGRAPHER_DEBUG", "- AnimatedFileDrawable " + activeChoreographersCount);
            }
        }
    }
}