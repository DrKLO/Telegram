/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

public class AnimatedFileDrawable extends BitmapDrawable implements Animatable {

    private static native int createDecoder(String src, int[] params);
    private static native void destroyDecoder(int ptr);
    private static native int getVideoFrame(int ptr, Bitmap bitmap, int[] params);

    private long lastFrameTime;
    private int lastTimeStamp;
    private int invalidateAfter = 50;
    private final int[] metaData = new int[4];
    private Runnable loadFrameTask;
    private Bitmap renderingBitmap;
    private Bitmap nextRenderingBitmap;
    private Bitmap backgroundBitmap;
    private boolean destroyWhenDone;
    private boolean decoderCreated;
    private File path;
    private boolean recycleWithSecond;

    private BitmapShader renderingShader;
    private BitmapShader nextRenderingShader;
    private BitmapShader backgroundShader;

    private int roundRadius;
    private RectF roundRect = new RectF();
    private RectF bitmapRect = new RectF();
    private Matrix shaderMatrix = new Matrix();

    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private boolean applyTransformation;
    private final android.graphics.Rect dstRect = new android.graphics.Rect();
    private static final Handler uiHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isRunning;
    private volatile boolean isRecycled;
    private volatile int nativePtr;
    private static ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(2, new ThreadPoolExecutor.DiscardPolicy());

    private View parentView = null;
    private View secondParentView = null;

    protected final Runnable mInvalidateTask = new Runnable() {
        @Override
        public void run() {
            if (secondParentView != null) {
                secondParentView.invalidate();
            } else if (parentView != null) {
                parentView.invalidate();
            }
        }
    };

    private Runnable uiRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyWhenDone && nativePtr != 0) {
                destroyDecoder(nativePtr);
                nativePtr = 0;
            }
            if (nativePtr == 0) {
                if (backgroundBitmap != null) {
                    backgroundBitmap.recycle();
                    backgroundBitmap = null;
                }
                return;
            }
            loadFrameTask = null;
            nextRenderingBitmap = backgroundBitmap;
            nextRenderingShader = backgroundShader;
            if (metaData[3] < lastTimeStamp) {
                lastTimeStamp = 0;
            }
            if (metaData[3] - lastTimeStamp != 0) {
                invalidateAfter = metaData[3] - lastTimeStamp;
            }
            lastTimeStamp = metaData[3];
            if (secondParentView != null) {
                secondParentView.invalidate();
            } else if (parentView != null) {
                parentView.invalidate();
            }
        }
    };

    private Runnable loadFrameRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRecycled) {
                if (!decoderCreated && nativePtr == 0) {
                    nativePtr = createDecoder(path.getAbsolutePath(), metaData);
                    decoderCreated = true;
                }
                try {
                    if (backgroundBitmap == null) {
                        try {
                            backgroundBitmap = Bitmap.createBitmap(metaData[0], metaData[1], Bitmap.Config.ARGB_8888);
                        } catch (Throwable e) {
                            FileLog.e("tmessages", e);
                        }
                        if (backgroundShader == null && backgroundBitmap != null && roundRadius != 0) {
                            backgroundShader = new BitmapShader(backgroundBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                        }
                    }
                    if (backgroundBitmap != null) {
                        getVideoFrame(nativePtr, backgroundBitmap, metaData);
                    }
                } catch (Throwable e) {
                    FileLog.e("tmessages", e);
                }
            }
            AndroidUtilities.runOnUIThread(uiRunnable);
        }
    };

    private final Runnable mStartTask = new Runnable() {
        @Override
        public void run() {
            if (secondParentView != null) {
                secondParentView.invalidate();
            } else if (parentView != null) {
                parentView.invalidate();
            }
        }
    };

    public AnimatedFileDrawable(File file, boolean createDecoder) {
        path = file;
        if (createDecoder) {
            nativePtr = createDecoder(file.getAbsolutePath(), metaData);
            decoderCreated = true;
        }
    }

    protected void postToDecodeQueue(Runnable runnable) {
        executor.execute(runnable);
    }

    public void setParentView(View view) {
        parentView = view;
    }

    public void setSecondParentView(View view) {
        secondParentView = view;
        if (view == null && recycleWithSecond) {
            recycle();
        }
    }

    public void recycle() {
        if (secondParentView != null) {
            recycleWithSecond = true;
            return;
        }
        isRunning = false;
        isRecycled = true;
        if (loadFrameTask == null) {
            if (nativePtr != 0) {
                destroyDecoder(nativePtr);
                nativePtr = 0;
            }
            if (nextRenderingBitmap != null) {
                nextRenderingBitmap.recycle();
                nextRenderingBitmap = null;
            }
        } else {
            destroyWhenDone = true;
        }
        if (renderingBitmap != null) {
            renderingBitmap.recycle();
            renderingBitmap = null;
        }
    }

    protected static void runOnUiThread(Runnable task) {
        if (Looper.myLooper() == uiHandler.getLooper()) {
            task.run();
        } else {
            uiHandler.post(task);
        }
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
        if (isRunning) {
            return;
        }
        isRunning = true;
        if (renderingBitmap == null) {
            scheduleNextGetFrame();
        }
        runOnUiThread(mStartTask);
    }

    private void scheduleNextGetFrame() {
        if (loadFrameTask != null || nativePtr == 0 && decoderCreated || destroyWhenDone) {
            return;
        }
        postToDecodeQueue(loadFrameTask = loadFrameRunnable);
    }

    @Override
    public void stop() {
        isRunning = false;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public int getIntrinsicHeight() {
        return decoderCreated ? (metaData[2] == 90 || metaData[2] == 270 ? metaData[0] : metaData[1]) : AndroidUtilities.dp(100);
    }

    @Override
    public int getIntrinsicWidth() {
        return decoderCreated ? (metaData[2] == 90 || metaData[2] == 270 ? metaData[1] : metaData[0]) : AndroidUtilities.dp(100);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        applyTransformation = true;
    }

    @Override
    public void draw(Canvas canvas) {
        if (nativePtr == 0 && decoderCreated || destroyWhenDone) {
            return;
        }
        if (isRunning) {
            if (renderingBitmap == null && nextRenderingBitmap == null) {
                scheduleNextGetFrame();
            } else if (Math.abs(System.currentTimeMillis() - lastFrameTime) >= invalidateAfter) {
                if (nextRenderingBitmap != null) {
                    scheduleNextGetFrame();
                    renderingBitmap = nextRenderingBitmap;
                    renderingShader = nextRenderingShader;
                    nextRenderingBitmap = null;
                    nextRenderingShader = null;
                    lastFrameTime = System.currentTimeMillis();
                }
            }
        }

        if (renderingBitmap != null) {
            if (applyTransformation) {
                int bitmapW = renderingBitmap.getWidth();
                int bitmapH = renderingBitmap.getHeight();
                if (metaData[2] == 90 || metaData[2] == 270) {
                    int temp = bitmapW;
                    bitmapW = bitmapH;
                    bitmapH = temp;
                }
                dstRect.set(getBounds());
                scaleX = (float) dstRect.width() / bitmapW;
                scaleY = (float) dstRect.height() / bitmapH;
                applyTransformation = false;
            }
            if (roundRadius != 0) {
                float scale = Math.max(scaleX, scaleY);

                if (renderingShader == null) {
                    renderingShader = new BitmapShader(backgroundBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                }
                getPaint().setShader(renderingShader);
                roundRect.set(dstRect);
                shaderMatrix.reset();
                if (Math.abs(scaleX - scaleY) > 0.00001f) {
                    int w;
                    int h;
                    if (metaData[2] == 90 || metaData[2] == 270) {
                        w = (int) Math.floor(dstRect.height() / scale);
                        h = (int) Math.floor(dstRect.width() / scale);
                    } else {
                        w = (int) Math.floor(dstRect.width() / scale);
                        h = (int) Math.floor(dstRect.height() / scale);
                    }
                    bitmapRect.set((renderingBitmap.getWidth() - w) / 2, (renderingBitmap.getHeight() - h) / 2, w, h);
                    AndroidUtilities.setRectToRect(shaderMatrix, bitmapRect, roundRect, metaData[2], Matrix.ScaleToFit.START);
                } else {
                    bitmapRect.set(0, 0, renderingBitmap.getWidth(), renderingBitmap.getHeight());
                    AndroidUtilities.setRectToRect(shaderMatrix, bitmapRect, roundRect, metaData[2], Matrix.ScaleToFit.FILL);
                }
                renderingShader.setLocalMatrix(shaderMatrix);

                canvas.drawRoundRect(roundRect, roundRadius, roundRadius, getPaint());
            } else {
                canvas.translate(dstRect.left, dstRect.top);
                if (metaData[2] == 90) {
                    canvas.rotate(90);
                    canvas.translate(0, -dstRect.width());
                } else if (metaData[2] == 180) {
                    canvas.rotate(180);
                    canvas.translate(-dstRect.width(), -dstRect.height());
                } else if (metaData[2] == 270) {
                    canvas.rotate(270);
                    canvas.translate(-dstRect.height(), 0);
                }
                canvas.scale(scaleX, scaleY);
                canvas.drawBitmap(renderingBitmap, 0, 0, getPaint());
            }
            if (isRunning) {
                uiHandler.postDelayed(mInvalidateTask, invalidateAfter);
            }
        }
    }

    @Override
    public int getMinimumHeight() {
        return decoderCreated ? (metaData[2] == 90 || metaData[2] == 270 ? metaData[0] : metaData[1]) : AndroidUtilities.dp(100);
    }

    @Override
    public int getMinimumWidth() {
        return decoderCreated ? (metaData[2] == 90 || metaData[2] == 270 ? metaData[1] : metaData[0]) : AndroidUtilities.dp(100);
    }

    public Bitmap getAnimatedBitmap() {
        if (renderingBitmap != null) {
            return renderingBitmap;
        } else if (nextRenderingBitmap != null) {
            return nextRenderingBitmap;
        }
        return null;
    }

    public void setRoundRadius(int value) {
        roundRadius = value;
        getPaint().setFlags(Paint.ANTI_ALIAS_FLAG);
    }

    public boolean hasBitmap() {
        return nativePtr != 0 && (renderingBitmap != null || nextRenderingBitmap != null);
    }

    public int getOrientation() {
        return metaData[2];
    }

    public AnimatedFileDrawable makeCopy() {
        AnimatedFileDrawable drawable = new AnimatedFileDrawable(path, false);
        drawable.metaData[0] = metaData[0];
        drawable.metaData[1] = metaData[1];
        return drawable;
    }
}
