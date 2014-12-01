/*
https://github.com/koral--/android-gif-drawable/
MIT License
Copyright (c)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */

package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.MediaController;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Locale;

public class GifDrawable extends Drawable implements Animatable, MediaController.MediaPlayerControl {

    private static native void renderFrame(int[] pixels, int gifFileInPtr, int[] metaData);
    private static native int openFile(int[] metaData, String filePath);
    private static native void free(int gifFileInPtr);
    private static native void reset(int gifFileInPtr);
    private static native void setSpeedFactor(int gifFileInPtr, float factor);
    private static native String getComment(int gifFileInPtr);
    private static native int getLoopCount(int gifFileInPtr);
    private static native int getDuration(int gifFileInPtr);
    private static native int getCurrentPosition(int gifFileInPtr);
    private static native int seekToTime(int gifFileInPtr, int pos, int[] pixels);
    private static native int seekToFrame(int gifFileInPtr, int frameNr, int[] pixels);
    private static native int saveRemainder(int gifFileInPtr);
    private static native int restoreRemainder(int gifFileInPtr);
    private static native long getAllocationByteCount(int gifFileInPtr);

    private static final Handler UI_HANDLER = new Handler(Looper.getMainLooper());

    private volatile int mGifInfoPtr;
    private volatile boolean mIsRunning = true;

    private final int[] mMetaData = new int[5];//[w, h, imageCount, errorCode, post invalidation time]
    private final long mInputSourceLength;

    private float mSx = 1f;
    private float mSy = 1f;
    private boolean mApplyTransformation;
    private final Rect mDstRect = new Rect();

    public WeakReference<View> parentView = null;

    protected final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    protected final int[] mColors;

    private final Runnable mResetTask = new Runnable() {
        @Override
        public void run() {
            reset(mGifInfoPtr);
        }
    };

    private final Runnable mStartTask = new Runnable() {
        @Override
        public void run() {
            restoreRemainder(mGifInfoPtr);
            if (parentView != null && parentView.get() != null) {
                parentView.get().invalidate();
            }
            mMetaData[4] = 0;
        }
    };

    private final Runnable mSaveRemainderTask = new Runnable() {
        @Override
        public void run() {
            saveRemainder(mGifInfoPtr);
        }
    };

    private final Runnable mInvalidateTask = new Runnable() {
        @Override
        public void run() {
            if (parentView != null && parentView.get() != null) {
                parentView.get().invalidate();
            }
        }
    };

    private static void runOnUiThread(Runnable task) {
        if (Looper.myLooper() == UI_HANDLER.getLooper()) {
            task.run();
        } else {
            UI_HANDLER.post(task);
        }
    }

    public GifDrawable(String filePath) throws Exception {
        mInputSourceLength = new File(filePath).length();
        mGifInfoPtr = openFile(mMetaData, filePath);
        mColors = new int[mMetaData[0] * mMetaData[1]];
    }

    public GifDrawable(File file) throws Exception {
        mInputSourceLength = file.length();
        mGifInfoPtr = openFile(mMetaData, file.getPath());
        mColors = new int[mMetaData[0] * mMetaData[1]];
    }

    public void recycle() {
        mIsRunning = false;
        int tmpPtr = mGifInfoPtr;
        mGifInfoPtr = 0;
        free(tmpPtr);
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
    public int getIntrinsicHeight() {
        return mMetaData[1];
    }

    @Override
    public int getIntrinsicWidth() {
        return mMetaData[0];
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mPaint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public void start() {
        if (mIsRunning) {
            return;
        }
        mIsRunning = true;
        runOnUiThread(mStartTask);
    }

    public void reset() {
        runOnUiThread(mResetTask);
    }

    @Override
    public void stop() {
        mIsRunning = false;
        runOnUiThread(mSaveRemainderTask);
    }

    @Override
    public boolean isRunning() {
        return mIsRunning;
    }

    public String getComment() {
        return getComment(mGifInfoPtr);
    }

    public int getLoopCount() {
        return getLoopCount(mGifInfoPtr);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "Size: %dx%d, %d frames, error: %d", mMetaData[0], mMetaData[1], mMetaData[2], mMetaData[3]);
    }

    public int getNumberOfFrames() {
        return mMetaData[2];
    }

    public int getError() {
        return mMetaData[3];
    }

    public void setSpeed(float factor) {
        if (factor <= 0f) {
            throw new IllegalArgumentException("Speed factor is not positive");
        }
        setSpeedFactor(mGifInfoPtr, factor);
    }

    @Override
    public void pause() {
        stop();
    }

    @Override
    public int getDuration() {
        return getDuration(mGifInfoPtr);
    }

    @Override
    public int getCurrentPosition() {
        return getCurrentPosition(mGifInfoPtr);
    }

    @Override
    public void seekTo(final int position) {
        if (position < 0) {
            throw new IllegalArgumentException("Position is not positive");
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                seekToTime(mGifInfoPtr, position, mColors);
                if (parentView != null && parentView.get() != null) {
                    parentView.get().invalidate();
                }
            }
        });
    }

    public void seekToFrame(final int frameIndex) {
        if (frameIndex < 0) {
            throw new IllegalArgumentException("frameIndex is not positive");
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                seekToFrame(mGifInfoPtr, frameIndex, mColors);
                if (parentView != null && parentView.get() != null) {
                    parentView.get().invalidate();
                }
            }
        });
    }

    @Override
    public boolean isPlaying() {
        return mIsRunning;
    }

    @Override
    public int getBufferPercentage() {
        return 100;
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return false;
    }

    @Override
    public boolean canSeekForward() {
        return getNumberOfFrames() > 1;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    public int getFrameByteCount() {
        return mMetaData[0] * mMetaData[1] * 4;
    }

    public long getAllocationByteCount() {
        return getAllocationByteCount(mGifInfoPtr) + mColors.length * 4L;
    }

    public long getInputSourceByteCount() {
        return mInputSourceLength;
    }

    public void getPixels(int[] pixels) {
        if (pixels.length < mColors.length) {
            throw new ArrayIndexOutOfBoundsException("Pixels array is too small. Required length: " + mColors.length);
        }
        System.arraycopy(mColors, 0, pixels, 0, mColors.length);
    }

    public int getPixel(int x, int y) {
        if (x < 0) {
            throw new IllegalArgumentException("x must be >= 0");
        }
        if (y < 0) {
            throw new IllegalArgumentException("y must be >= 0");
        }
        if (x >= mMetaData[0]) {
            throw new IllegalArgumentException("x must be < GIF width");
        }
        if (y >= mMetaData[1]) {
            throw new IllegalArgumentException("y must be < GIF height");
        }
        return mColors[mMetaData[1] * y + x];
    }

    public Bitmap getBitmap() {
        seekToFrame(mGifInfoPtr, 0, mColors);
        return Bitmap.createBitmap(mColors, 0, mMetaData[0], mMetaData[0], mMetaData[1], Bitmap.Config.ARGB_8888);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        mApplyTransformation = true;
    }

    @Override
    public void draw(Canvas canvas) {
        if (mApplyTransformation) {
            mDstRect.set(getBounds());
            mSx = (float) mDstRect.width() / mMetaData[0];
            mSy = (float) mDstRect.height() / mMetaData[1];
            mApplyTransformation = false;
        }
        if (mPaint.getShader() == null) {
            if (mIsRunning) {
                renderFrame(mColors, mGifInfoPtr, mMetaData);
            } else {
                mMetaData[4] = -1;
            }
            canvas.translate(mDstRect.left, mDstRect.top);
            canvas.scale(mSx, mSy);
            if (mMetaData[0] > 0 && mMetaData[1] > 0) {
                canvas.drawBitmap(mColors, 0, mMetaData[0], 0f, 0f, mMetaData[0], mMetaData[1], true, mPaint);
            }
            if (mMetaData[4] >= 0 && mMetaData[2] > 1) {
                UI_HANDLER.postDelayed(mInvalidateTask, mMetaData[4]);
            }
        } else {
            canvas.drawRect(mDstRect, mPaint);
        }
    }

    public final Paint getPaint() {
        return mPaint;
    }

    @Override
    public int getAlpha() {
        return mPaint.getAlpha();
    }

    @Override
    public void setFilterBitmap(boolean filter) {
        mPaint.setFilterBitmap(filter);
        if (parentView != null && parentView.get() != null) {
            parentView.get().invalidate();
        }
    }

    @Override
    public void setDither(boolean dither) {
        mPaint.setDither(dither);
        if (parentView != null && parentView.get() != null) {
            parentView.get().invalidate();
        }
    }

    @Override
    public int getMinimumHeight() {
        return mMetaData[1];
    }

    @Override
    public int getMinimumWidth() {
        return mMetaData[0];
    }
}
