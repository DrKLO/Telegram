/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class VideoTimelinePlayView extends View {

    private long videoLength;
    private float progressLeft;
    private float progressRight = 1;
    private Paint paint;
    private Paint paint2;
    private Paint paint3;
    private boolean pressedLeft;
    private boolean pressedRight;
    private boolean pressedPlay;
    private float playProgress = 0.5f;
    private float pressDx;
    private MediaMetadataRetriever mediaMetadataRetriever;
    private VideoTimelineViewDelegate delegate;
    private ArrayList<BitmapFrame> frames = new ArrayList<>();
    private AsyncTask<Integer, Integer, Bitmap> currentTask;
    private static final Object sync = new Object();
    private long frameTimeOffset;
    private int frameWidth;
    private int frameHeight;
    private int framesToLoad;
    private float maxProgressDiff = 1.0f;
    private float minProgressDiff = 0.0f;
    private RectF rect3 = new RectF();
    private Drawable drawableLeft;
    private Drawable drawableRight;
    private int lastWidth;
    private int currentMode = MODE_VIDEO;

    Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private ArrayList<android.graphics.Rect> exclusionRects = new ArrayList<>();
    private android.graphics.Rect exclustionRect = new Rect();

    public final static int MODE_VIDEO = 0;
    public final static int MODE_AVATAR = 1;

    public interface VideoTimelineViewDelegate {
        void onLeftProgressChanged(float progress);
        void onRightProgressChanged(float progress);
        void onPlayProgressChanged(float progress);
        void didStartDragging(int type);
        void didStopDragging(int type);
    }

    public static int TYPE_LEFT = 0;
    public static int TYPE_RIGHT = 1;
    public static int TYPE_PROGRESS = 2;

    public VideoTimelinePlayView(Context context) {
        super(context);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xffffffff);
        paint2 = new Paint();
        paint2.setColor(0x4d000000);
        paint3 = new Paint();
        paint3.setColor(0xff000000);
//        drawableLeft = context.getResources().getDrawable(R.drawable.video_cropleft);
//        drawableLeft.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
//        drawableRight = context.getResources().getDrawable(R.drawable.video_cropright);
//        drawableRight.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
        exclusionRects.add(exclustionRect);
    }

    public float getProgress() {
        return playProgress;
    }

    public float getLeftProgress() {
        return progressLeft;
    }

    public float getRightProgress() {
        return progressRight;
    }

    public float getProgressOf(int type) {
        if (type == TYPE_LEFT) {
            return getLeftProgress();
        } else if (type == TYPE_PROGRESS) {
            return getProgress();
        } else if (type == TYPE_RIGHT) {
            return getRightProgress();
        }
        return 0;
    }

    public void setMinProgressDiff(float value) {
        minProgressDiff = value;
    }

    public void setMode(int mode) {
        if (currentMode == mode) {
            return;
        }
        currentMode = mode;
        invalidate();
    }

    public void setMaxProgressDiff(float value) {
        maxProgressDiff = value;
        if (progressRight - progressLeft > maxProgressDiff) {
            progressRight = progressLeft + maxProgressDiff;
            invalidate();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (Build.VERSION.SDK_INT >= 29) {
            exclustionRect.set(left, 0, right, getMeasuredHeight());
            setSystemGestureExclusionRects(exclusionRects);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }
        float x = event.getX();
        float y = event.getY();

        int width = getMeasuredWidth() - dp(32);
        int startX = (int) (width * progressLeft) + dp(16);
        int playX = (int) (width * playProgress) + dp(16);
        int endX = (int) (width * progressRight) + dp(16);

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
            if (mediaMetadataRetriever == null) {
                return false;
            }
            int additionWidth = dp(16);
            int additionWidthPlay = dp(8);
            if (endX != startX && playX - additionWidthPlay <= x && x <= playX + additionWidthPlay && y >= 0 && y <= getMeasuredHeight()) {
                if (delegate != null) {
                    delegate.didStartDragging(TYPE_PROGRESS);
                }
                pressedPlay = true;
                pressDx = (int) (x - playX);
                invalidate();
                return true;
            } else if (startX - additionWidth <= x && x <= Math.min(startX + additionWidth, endX) && y >= 0 && y <= getMeasuredHeight()) {
                if (delegate != null) {
                    delegate.didStartDragging(TYPE_LEFT);
                }
                pressedLeft = true;
                pressDx = (int) (x - startX);
                invalidate();
                return true;
            } else if (endX - additionWidth <= x && x <= endX + additionWidth && y >= 0 && y <= getMeasuredHeight()) {
                if (delegate != null) {
                    delegate.didStartDragging(TYPE_RIGHT);
                }
                pressedRight = true;
                pressDx = (int) (x - endX);
                invalidate();
                return true;
            } else if (startX <= x && x <= endX && y >= 0 && y <= getMeasuredHeight()) {
                if (delegate != null) {
                    delegate.didStartDragging(TYPE_PROGRESS);
                }
                pressedPlay = true;
                playProgress = (x - dp(16)) / width;
                if (delegate != null) {
                    delegate.onPlayProgressChanged(playProgress);
                }
                pressDx = 0;
                invalidate();
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (pressedLeft) {
                if (delegate != null) {
                    delegate.didStopDragging(TYPE_LEFT);
                }
                pressedLeft = false;
                return true;
            } else if (pressedRight) {
                if (delegate != null) {
                    delegate.didStopDragging(TYPE_RIGHT);
                }
                pressedRight = false;
                return true;
            } else if (pressedPlay) {
                if (delegate != null) {
                    delegate.didStopDragging(TYPE_PROGRESS);
                }
                pressedPlay = false;
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (pressedPlay) {
                playX = (int) (x - pressDx);
                playProgress = (float) (playX - dp(16)) / (float) width;
                if (playProgress < progressLeft) {
                    playProgress = progressLeft;
                } else if (playProgress > progressRight) {
                    playProgress = progressRight;
                }
                if (delegate != null) {
                    delegate.onPlayProgressChanged(playProgress);
                }
                invalidate();
                return true;
            } else if (pressedLeft) {
                startX = (int) (x - pressDx);
                if (startX < dp(16)) {
                    startX = dp(16);
                } else if (startX > endX) {
                    startX = endX;
                }
                progressLeft = (float) (startX - dp(16)) / (float) width;
                if (progressRight - progressLeft > maxProgressDiff) {
                    progressRight = progressLeft + maxProgressDiff;
                } else if (minProgressDiff != 0 && progressRight - progressLeft < minProgressDiff) {
                    progressLeft = progressRight - minProgressDiff;
                    if (progressLeft < 0) {
                        progressLeft = 0;
                    }
                }
                if (progressLeft > playProgress) {
                    playProgress = progressLeft;
                } else if (progressRight < playProgress) {
                    playProgress = progressRight;
                }
                if (delegate != null) {
                    delegate.onLeftProgressChanged(progressLeft);
                }
                invalidate();
                return true;
            } else if (pressedRight) {
                endX = (int) (x - pressDx);
                if (endX < startX) {
                    endX = startX;
                } else if (endX > width + dp(16)) {
                    endX = width + dp(16);
                }
                progressRight = (float) (endX - dp(16)) / (float) width;
                if (progressRight - progressLeft > maxProgressDiff) {
                    progressLeft = progressRight - maxProgressDiff;
                } else if (minProgressDiff != 0 && progressRight - progressLeft < minProgressDiff) {
                    progressRight = progressLeft + minProgressDiff;
                    if (progressRight > 1.0f) {
                        progressRight = 1.0f;
                    }
                }
                if (progressLeft > playProgress) {
                    playProgress = progressLeft;
                } else if (progressRight < playProgress) {
                    playProgress = progressRight;
                }
                if (delegate != null) {
                    delegate.onRightProgressChanged(progressRight);
                }
                invalidate();
                return true;
            }
        }
        return true;
    }

    public void setVideoPath(String path, float left, float right) {
        destroy();
        mediaMetadataRetriever = new MediaMetadataRetriever();
        progressLeft = left;
        progressRight = right;
        try {
            mediaMetadataRetriever.setDataSource(path);
            String duration = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            videoLength = Long.parseLong(duration);
        } catch (Exception e) {
            FileLog.e(e);
        }
        invalidate();
    }

    public long getLength() {
        return Math.max(1, videoLength);
    }

    public void setRightProgress(float value) {
        progressRight = value;
        if (delegate != null) {
            delegate.didStartDragging(TYPE_RIGHT);
        }
        if (delegate != null) {
            delegate.onRightProgressChanged(progressRight);
        }
        if (delegate != null) {
            delegate.didStopDragging(TYPE_RIGHT);
        }
        invalidate();
    }

    public void setLeftRightProgress(float left, float right) {
        progressRight = right;
        progressLeft = left;
        invalidate();
    }

    public void setDelegate(VideoTimelineViewDelegate delegate) {
        this.delegate = delegate;
    }

    private void reloadFrames(int frameNum) {
        if (mediaMetadataRetriever == null) {
            return;
        }
        if (frameNum == 0) {
            frameHeight = dp(40);
            framesToLoad = Math.max(1, (getMeasuredWidth() - dp(16)) / frameHeight);
            frameWidth = (int) Math.ceil((float) (getMeasuredWidth() - dp(16)) / (float) framesToLoad);
            frameTimeOffset = videoLength / framesToLoad;
        }
        currentTask = new AsyncTask<Integer, Integer, Bitmap>() {
            private int frameNum = 0;

            @Override
            protected Bitmap doInBackground(Integer... objects) {
                frameNum = objects[0];
                Bitmap bitmap = null;
                if (isCancelled()) {
                    return null;
                }
                try {
                    bitmap = mediaMetadataRetriever.getFrameAtTime(frameTimeOffset * frameNum * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (isCancelled()) {
                        return null;
                    }
                    if (bitmap != null) {
                        Bitmap result = Bitmap.createBitmap(frameWidth, frameHeight, bitmap.getConfig());
                        Canvas canvas = new Canvas(result);
                        float scaleX = (float) frameWidth / (float) bitmap.getWidth();
                        float scaleY = (float) frameHeight / (float) bitmap.getHeight();
                        float scale = Math.max(scaleX, scaleY);
                        int w = (int) (bitmap.getWidth() * scale);
                        int h = (int) (bitmap.getHeight() * scale);
                        Rect srcRect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
                        Rect destRect = new Rect((frameWidth - w) / 2, (frameHeight - h) / 2, (frameWidth + w) / 2, (frameHeight + h) / 2);
                        canvas.drawBitmap(bitmap, srcRect, destRect, null);
                        bitmap.recycle();
                        bitmap = result;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                return bitmap;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (!isCancelled()) {
                    frames.add(new BitmapFrame(bitmap));
                    invalidate();
                    if (frameNum < framesToLoad) {
                        reloadFrames(frameNum + 1);
                    }
                }
            }
        };
        currentTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, frameNum, null, null);
    }

    public void destroy() {
        synchronized (sync) {
            try {
                if (mediaMetadataRetriever != null) {
                    mediaMetadataRetriever.release();
                    mediaMetadataRetriever = null;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        for (int a = 0; a < frames.size(); a++) {
            BitmapFrame bitmap = frames.get(a);
            if (bitmap != null && bitmap.bitmap != null) {
                bitmap.bitmap.recycle();
            }
        }
        frames.clear();
        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
        }
    }

    public boolean isDragging() {
        return pressedPlay;
    }

    public void setProgress(float value) {
        playProgress = value;
        invalidate();
    }

    public void clearFrames() {
        for (int a = 0; a < frames.size(); a++) {
            BitmapFrame frame = frames.get(a);
            if (frame != null && frame.bitmap != null) {
                frame.bitmap.recycle();
            }
        }
        frames.clear();
        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        if (lastWidth != widthSize) {
            clearFrames();
            lastWidth = widthSize;
        }
    }

    private Path clipPath = new Path();

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getMeasuredWidth() - dp(32);
        int startX = (int) (width * progressLeft) + dp(16);
        int endX = (int) (width * progressRight) + dp(16);

        int top = dp(2 + 4);
        int end = dp(48);

        canvas.save();
        canvas.clipRect(dp(16), dp(4), width + dp(20), dp(48));
        if (frames.isEmpty() && currentTask == null) {
            canvas.drawRect(dp(16), top, dp(16) + width + dp(4), dp(46), paint2);
            reloadFrames(0);
        } else {
            canvas.save();
            clipPath.rewind();
            AndroidUtilities.rectTmp.set(dp(16), dp(6), width + dp(20), dp(46));
            clipPath.addRoundRect(AndroidUtilities.rectTmp, dp(6), dp(6), Path.Direction.CW);
            canvas.clipPath(clipPath);
            canvas.drawRect(startX, top, endX, dp(46), paint2);
            int offset = 0;
            for (int a = 0; a < frames.size(); a++) {
                BitmapFrame bitmap = frames.get(a);
                if (bitmap.bitmap != null) {
                    int x = dp(16) + offset * frameWidth;
                    int y = dp(2 + 4);
                    if (bitmap.alpha != 1f) {
                        bitmap.alpha += 16f / 350f;
                        if (bitmap.alpha > 1f) {
                            bitmap.alpha = 1f;
                        } else {
                            invalidate();
                        }
                        bitmapPaint.setAlpha((int) (255 * CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(bitmap.alpha)));
                        canvas.drawBitmap(bitmap.bitmap, x, y, bitmapPaint);
                    } else {
                        canvas.drawBitmap(bitmap.bitmap, x, y, null);
                    }
                }
                offset++;
            }
            canvas.drawRect(dp(16), top, startX, dp(46), paint2);
            canvas.drawRect(endX + dp(4), top, dp(16) + width + dp(4), dp(46), paint2);
            canvas.restore();
        }

        canvas.drawRect(startX, dp(4), startX + dp(2), end, paint);
        canvas.drawRect(endX + dp(2), dp(4), endX + dp(4), end, paint);
        canvas.drawRect(startX + dp(2), dp(4), endX + dp(4), top, paint);
        canvas.drawRect(startX + dp(2), end - dp(2), endX + dp(4), end, paint);
        canvas.restore();

        rect3.set(startX - dp(8), dp(4), startX + dp(2), end);
        canvas.drawRoundRect(rect3, dp(3), dp(3), paint);
        rect3.set(startX - dpf2(2), dp(21.17f), startX - dpf2(2 + 2), dp(30.83f));
        canvas.drawRoundRect(rect3, dp(3), dp(3), paint3);

        rect3.set(endX + dp(2), dp(4), endX + dp(12), end);
        canvas.drawRoundRect(rect3, dp(3), dp(3), paint);
        rect3.set(endX + dpf2(6), dp(21.17f), endX + dpf2(6 + 2), dp(30.83f));
        canvas.drawRoundRect(rect3, dp(3), dp(3), paint3);

        float cx = dp(18) + width * playProgress;
        rect3.set(cx - dp(2), dp(2), cx + dp(2), dp(50));
        canvas.drawRoundRect(rect3, dp(1), dp(1), paint2);
//        canvas.drawCircle(cx, dp(52), dp(3.5f), paint2);

        rect3.set(cx - dpf2(1.5f), dp(2), cx + dpf2(1.5f), dp(50));
        canvas.drawRoundRect(rect3, dp(1), dp(1), paint);
//        canvas.drawCircle(cx, dp(52), dp(3), paint);
    }

    private static class BitmapFrame {
        Bitmap bitmap;
        float alpha;

        public BitmapFrame(Bitmap bitmap) {
            this.bitmap = bitmap;
        }
    }
}
