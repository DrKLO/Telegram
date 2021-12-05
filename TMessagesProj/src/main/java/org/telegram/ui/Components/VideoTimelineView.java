/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.os.AsyncTask;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class VideoTimelineView extends View {

    private long videoLength;
    private float progressLeft;
    private float progressRight = 1;
    private Paint paint;
    private Paint paint2;
    private Paint backgroundGrayPaint;
    private boolean pressedLeft;
    private boolean pressedRight;
    private float pressDx;
    private MediaMetadataRetriever mediaMetadataRetriever;
    private VideoTimelineViewDelegate delegate;
    private ArrayList<Bitmap> frames = new ArrayList<>();
    private AsyncTask<Integer, Integer, Bitmap> currentTask;
    private static final Object sync = new Object();
    private long frameTimeOffset;
    private int frameWidth;
    private int frameHeight;
    private int framesToLoad;
    private float maxProgressDiff = 1.0f;
    private float minProgressDiff = 0.0f;
    private boolean isRoundFrames;
    private Rect rect1;
    private Rect rect2;

    private int roundCornersSize;
    private Bitmap roundCornerBitmap;

    private ArrayList<Bitmap> keyframes = new ArrayList<>();
    private boolean framesLoaded;
    private TimeHintView timeHintView;

    Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint thumbRipplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public void setKeyframes(ArrayList<Bitmap> keyframes) {
        this.keyframes.clear();
        this.keyframes.addAll(keyframes);
    }

    public interface VideoTimelineViewDelegate {
        void onLeftProgressChanged(float progress);
        void onRightProgressChanged(float progress);
        void didStartDragging();
        void didStopDragging();
    }

    public VideoTimelineView(Context context) {
        super(context);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xffffffff);

        paint2 = new Paint();
        paint2.setColor(0x7f000000);

        backgroundGrayPaint = new Paint();

        thumbPaint.setColor(Color.WHITE);
        thumbPaint.setStrokeWidth(AndroidUtilities.dpf2(2f));
        thumbPaint.setStyle(Paint.Style.STROKE);
        thumbPaint.setStrokeCap(Paint.Cap.ROUND);

        updateColors();
    }

    public void updateColors() {
        backgroundGrayPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        thumbRipplePaint.setColor(Theme.getColor(Theme.key_chat_recordedVoiceHighlight));
        roundCornersSize = 0;
        if (timeHintView != null) {
            timeHintView.updateColors();
        }
    }

    public float getLeftProgress() {
        return progressLeft;
    }

    public float getRightProgress() {
        return progressRight;
    }

    public void setMinProgressDiff(float value) {
        minProgressDiff = value;
    }

    public void setMaxProgressDiff(float value) {
        maxProgressDiff = value;
        if (progressRight - progressLeft > maxProgressDiff) {
            progressRight = progressLeft + maxProgressDiff;
            invalidate();
        }
    }

    public void setRoundFrames(boolean value) {
        isRoundFrames = value;
        if (isRoundFrames) {
            rect1 = new Rect(AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(14 + 28), AndroidUtilities.dp(14 + 28));
            rect2 = new Rect();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }
        float x = event.getX();
        float y = event.getY();

        int width = getMeasuredWidth() - AndroidUtilities.dp(24);
        int startX = (int) (width * progressLeft) + AndroidUtilities.dp(12);
        int endX = (int) (width * progressRight) + AndroidUtilities.dp(12);

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
            if (mediaMetadataRetriever == null) {
                return false;
            }
            int additionWidth = AndroidUtilities.dp(24);
            if (startX - additionWidth <= x && x <= startX + additionWidth && y >= 0 && y <= getMeasuredHeight()) {
                if (delegate != null) {
                    delegate.didStartDragging();
                }
                pressedLeft = true;
                pressDx = (int) (x - startX);
                timeHintView.setTime((int) (videoLength / 1000f * progressLeft));
                timeHintView.setCx(startX + getLeft() + AndroidUtilities.dp(4));
                timeHintView.show(true);
                invalidate();
                return true;
            } else if (endX - additionWidth <= x && x <= endX + additionWidth && y >= 0 && y <= getMeasuredHeight()) {
                if (delegate != null) {
                    delegate.didStartDragging();
                }
                pressedRight = true;
                pressDx = (int) (x - endX);
                timeHintView.setTime((int) (videoLength / 1000f * progressRight));
                timeHintView.setCx(endX + getLeft() - AndroidUtilities.dp(4));
                timeHintView.show(true);
                invalidate();
                return true;
            } else {
                timeHintView.show(false);
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (pressedLeft) {
                if (delegate != null) {
                    delegate.didStopDragging();
                }
                pressedLeft = false;
                invalidate();
                timeHintView.show(false);
                return true;
            } else if (pressedRight) {
                if (delegate != null) {
                    delegate.didStopDragging();
                }
                pressedRight = false;
                invalidate();
                timeHintView.show(false);
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (pressedLeft) {
                startX = (int) (x - pressDx);
                if (startX < AndroidUtilities.dp(16)) {
                    startX = AndroidUtilities.dp(16);
                } else if (startX > endX) {
                    startX = endX;
                }
                progressLeft = (float) (startX - AndroidUtilities.dp(16)) / (float) width;
                if (progressRight - progressLeft > maxProgressDiff) {
                    progressRight = progressLeft + maxProgressDiff;
                } else if (minProgressDiff != 0 && progressRight - progressLeft < minProgressDiff) {
                    progressLeft = progressRight - minProgressDiff;
                    if (progressLeft < 0) {
                        progressLeft = 0;
                    }
                }
                timeHintView.setCx((width * progressLeft) + AndroidUtilities.dpf2(12) + getLeft() - AndroidUtilities.dp(4));
                timeHintView.setTime((int) (videoLength / 1000f * progressLeft));
                timeHintView.show(true);
                if (delegate != null) {
                    delegate.onLeftProgressChanged(progressLeft);
                }
                invalidate();
                return true;
            } else if (pressedRight) {
                endX = (int) (x - pressDx);
                if (endX < startX) {
                    endX = startX;
                } else if (endX > width + AndroidUtilities.dp(16)) {
                    endX = width + AndroidUtilities.dp(16);
                }
                progressRight = (float) (endX - AndroidUtilities.dp(16)) / (float) width;
                if (progressRight - progressLeft > maxProgressDiff) {
                    progressLeft = progressRight - maxProgressDiff;
                } else if (minProgressDiff != 0 && progressRight - progressLeft < minProgressDiff) {
                    progressRight = progressLeft + minProgressDiff;
                    if (progressRight > 1.0f) {
                        progressRight = 1.0f;
                    }
                }

                timeHintView.setCx((width * progressRight) + AndroidUtilities.dpf2(12) + getLeft() + AndroidUtilities.dp(4));
                timeHintView.show(true);
                timeHintView.setTime((int) (videoLength / 1000f * progressRight));
                if (delegate != null) {
                    delegate.onRightProgressChanged(progressRight);
                }
                invalidate();
                return true;
            }
        }
        return false;
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    public void setVideoPath(String path) {
        destroy();
        mediaMetadataRetriever = new MediaMetadataRetriever();
        progressLeft = 0.0f;
        progressRight = 1.0f;
        try {
            mediaMetadataRetriever.setDataSource(path);
            String duration = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            videoLength = Long.parseLong(duration);
        } catch (Exception e) {
            FileLog.e(e);
        }
        invalidate();
    }

    public void setDelegate(VideoTimelineViewDelegate videoTimelineViewDelegate) {
        delegate = videoTimelineViewDelegate;
    }

    private void reloadFrames(int frameNum) {
        if (mediaMetadataRetriever == null) {
            return;
        }
        if (frameNum == 0) {
            if (isRoundFrames) {
                frameHeight = frameWidth = AndroidUtilities.dp(56);
                framesToLoad = Math.max(1, (int) Math.ceil((getMeasuredWidth() - AndroidUtilities.dp(16)) / (frameHeight / 2.0f)));
            } else {
                frameHeight = AndroidUtilities.dp(40);
                framesToLoad = Math.max(1, (getMeasuredWidth() - AndroidUtilities.dp(16)) / frameHeight);
                frameWidth = (int) Math.ceil((float) (getMeasuredWidth() - AndroidUtilities.dp(16)) / (float) framesToLoad);
            }
            frameTimeOffset = videoLength / framesToLoad;

            if (!keyframes.isEmpty()) {
                int keyFramesCount = keyframes.size();
                float step = keyFramesCount / (float) framesToLoad;
                float currentP = 0f;
                for (int i = 0; i < framesToLoad; i++) {
                    frames.add(keyframes.get((int) currentP));
                    currentP += step;
                }
                return;
            }
        }
        framesLoaded = false;
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
                        Rect destRect = new Rect((frameWidth - w) / 2, (frameHeight - h) / 2, w, h);
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
                    frames.add(bitmap);
                    invalidate();
                    if (frameNum < framesToLoad) {
                        reloadFrames(frameNum + 1);
                    } else {
                        framesLoaded = true;
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
        if (!keyframes.isEmpty()) {
            for (int a = 0; a < keyframes.size(); a++) {
                Bitmap bitmap = keyframes.get(a);
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }
        } else {
            for (int a = 0; a < frames.size(); a++) {
                Bitmap bitmap = frames.get(a);
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }
        }
        keyframes.clear();
        frames.clear();

        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
        }
    }

    public void clearFrames() {
        if (keyframes.isEmpty()) {
            for (int a = 0; a < frames.size(); a++) {
                Bitmap bitmap = frames.get(a);
                if (bitmap != null) {
                    bitmap.recycle();
                }
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
    protected void onDraw(Canvas canvas) {
        int width = getMeasuredWidth() - AndroidUtilities.dp(24);
        int startX = (int) (width * progressLeft) + AndroidUtilities.dp(12);
        int endX = (int) (width * progressRight) + AndroidUtilities.dp(12);
        int topOffset = (getMeasuredHeight() - AndroidUtilities.dp(32)) >> 1;


        if (frames.isEmpty() && currentTask == null) {
            reloadFrames(0);
        }
        if (!frames.isEmpty()) {
            if (!framesLoaded) {
                canvas.drawRect(0, topOffset, getMeasuredWidth(), getMeasuredHeight() - topOffset, backgroundGrayPaint);
            }
            int offset = 0;
            for (int a = 0; a < frames.size(); a++) {
                Bitmap bitmap = frames.get(a);
                if (bitmap != null) {
                    int x = offset * (isRoundFrames ? frameWidth / 2 : frameWidth);
                    if (isRoundFrames) {
                        rect2.set(x, topOffset, x + AndroidUtilities.dp(28), topOffset + AndroidUtilities.dp(32));
                        canvas.drawBitmap(bitmap, rect1, rect2, null);
                    } else {
                        canvas.drawBitmap(bitmap, x, topOffset, null);
                    }
                }
                offset++;
            }
        } else {
            return;
        }

        canvas.drawRect(0, topOffset, startX, getMeasuredHeight() - topOffset, paint2);
        canvas.drawRect(endX, topOffset, getMeasuredWidth(), getMeasuredHeight() - topOffset, paint2);

        canvas.drawLine(startX - AndroidUtilities.dp(4), topOffset + AndroidUtilities.dp(10), startX - AndroidUtilities.dp(4), getMeasuredHeight() - AndroidUtilities.dp(10) - topOffset, thumbPaint);
        canvas.drawLine(endX + AndroidUtilities.dp(4), topOffset + AndroidUtilities.dp(10), endX + AndroidUtilities.dp(4), getMeasuredHeight() - AndroidUtilities.dp(10) - topOffset, thumbPaint);

        drawCorners(canvas, getMeasuredHeight() - topOffset * 2, getMeasuredWidth(), 0, topOffset);
    }

    private void drawCorners(Canvas canvas, int height, int width, int left, int top) {
        if (AndroidUtilities.dp(6) != roundCornersSize) {
            roundCornersSize = AndroidUtilities.dp(6);
            roundCornerBitmap = Bitmap.createBitmap(AndroidUtilities.dp(6), AndroidUtilities.dp(6), Bitmap.Config.ARGB_8888);
            Canvas bitmapCanvas = new Canvas(roundCornerBitmap);

            Paint xRefP = new Paint(Paint.ANTI_ALIAS_FLAG);
            xRefP.setColor(0);
            xRefP.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

            bitmapCanvas.drawColor(Theme.getColor(Theme.key_chat_messagePanelBackground));
            bitmapCanvas.drawCircle(roundCornersSize, roundCornersSize, roundCornersSize, xRefP);
        }

        int sizeHalf = roundCornersSize >> 1;
        canvas.save();
        canvas.drawBitmap(roundCornerBitmap, left, top, null);
        canvas.rotate(-90,left + sizeHalf,top + height - sizeHalf);
        canvas.drawBitmap(roundCornerBitmap, left, top + height - roundCornersSize, null);
        canvas.restore();
        canvas.save();
        canvas.rotate(180,left + width - sizeHalf,top + height - sizeHalf);
        canvas.drawBitmap(roundCornerBitmap, left + width - roundCornersSize, top + height - roundCornersSize, null);
        canvas.restore();
        canvas.save();
        canvas.rotate(90, left + width - sizeHalf, top + sizeHalf);
        canvas.drawBitmap(roundCornerBitmap, left + width - roundCornersSize, top, null);
        canvas.restore();
    }

    public void setTimeHintView(TimeHintView timeHintView) {
        this.timeHintView = timeHintView;
    }

    public static class TimeHintView extends View {

        private Drawable tooltipBackground;
        private Drawable tooltipBackgroundArrow;
        private StaticLayout tooltipLayout;
        private TextPaint tooltipPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private long lastTime = -1;

        private float tooltipAlpha;
        private boolean showTooltip;
        private long showTooltipStartTime;
        private float cx;
        private float scale;
        private boolean show;

        public TimeHintView(Context context) {
            super(context);
            tooltipPaint.setTextSize(AndroidUtilities.dp(14));
            tooltipBackgroundArrow = ContextCompat.getDrawable(context, R.drawable.tooltip_arrow);
            tooltipBackground = Theme.createRoundRectDrawable(AndroidUtilities.dp(5), Theme.getColor(Theme.key_chat_gifSaveHintBackground));
            updateColors();
            setTime(0);
        }

        public void setTime(int timeInSeconds) {
            if (timeInSeconds != lastTime) {
                lastTime = timeInSeconds;
                String s = AndroidUtilities.formatShortDuration(timeInSeconds);
                tooltipLayout = new StaticLayout(s, tooltipPaint, (int) tooltipPaint.measureText(s), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                    tooltipLayout.getHeight() + AndroidUtilities.dp(4) + tooltipBackgroundArrow.getIntrinsicHeight(),
                    MeasureSpec.EXACTLY
            ));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (tooltipLayout == null) {
                return;
            }
            if (show) {
                if (scale != 1f) {
                    scale += 0.12f;
                    if (scale > 1f) scale = 1f;
                    invalidate();
                }
            } else {
                if (scale != 0f) {
                    scale -= 0.12f;
                    if (scale < 0f) scale = 0f;
                    invalidate();
                }
                if (scale == 0f) {
                    return;
                }
            }
            int alpha = (int) (255 * (scale > 0.5f ? 1f : scale / 0.5f));

            canvas.save();
            canvas.scale(scale, scale, cx, getMeasuredHeight());
            canvas.translate(cx - tooltipLayout.getWidth() / 2f, 0);

            tooltipBackground.setBounds(
                    -AndroidUtilities.dp(8), 0,
                    tooltipLayout.getWidth() + AndroidUtilities.dp(8), (int) (tooltipLayout.getHeight() + AndroidUtilities.dpf2(4))
            );
            tooltipBackgroundArrow.setBounds(
                    tooltipLayout.getWidth() / 2 - tooltipBackgroundArrow.getIntrinsicWidth() / 2, (int) (tooltipLayout.getHeight() + AndroidUtilities.dpf2(4)),
                    tooltipLayout.getWidth() / 2 + tooltipBackgroundArrow.getIntrinsicWidth() / 2, (int) (tooltipLayout.getHeight() + AndroidUtilities.dpf2(4)) + tooltipBackgroundArrow.getIntrinsicHeight()
            );
            tooltipBackgroundArrow.setAlpha(alpha);
            tooltipBackground.setAlpha(alpha);
            tooltipPaint.setAlpha(alpha);

            tooltipBackgroundArrow.draw(canvas);
            tooltipBackground.draw(canvas);
            canvas.translate(0, AndroidUtilities.dpf2(1));
            tooltipLayout.draw(canvas);
            canvas.restore();
        }

        public void updateColors() {
            tooltipPaint.setColor(Theme.getColor(Theme.key_chat_gifSaveHintText));
            tooltipBackground = Theme.createRoundRectDrawable(AndroidUtilities.dp(5), Theme.getColor(Theme.key_chat_gifSaveHintBackground));
            tooltipBackgroundArrow.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_gifSaveHintBackground), PorterDuff.Mode.MULTIPLY));
        }

        public void setCx(float v) {
            cx = v;
            invalidate();
        }

        public void show(boolean s) {
            show = s;
            invalidate();
        }
    }
}
