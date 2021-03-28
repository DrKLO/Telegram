package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.DynamicLayout;
import android.text.Layout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;


import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class AnimationPropertiesCell extends View {

    private static final float lineHeight = AndroidUtilities.dp(2);
    private static final float lineLeftRightSpace = AndroidUtilities.dp(27);
    private static final float textLeftRightSpace = AndroidUtilities.dp(21);
    private static final float textTopBottomSpace = AndroidUtilities.dp(2);
    private static final float boundRectRadius = AndroidUtilities.dp(3);
    private static final float boundRectBackRadius = AndroidUtilities.dp(5);
    private static final float topBottomLinesSpace = AndroidUtilities.dp(150);

    private final Paint lineProgressPaint = new Paint();
    private final Paint lineBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    private final Bitmap boundBitmap = createBoundBitmap();
    private final ProgressSelectorDrawable topProgressDrawable = new ProgressSelectorDrawable(getContext());
    private final ProgressSelectorDrawable bottomProgressDrawable = new ProgressSelectorDrawable(getContext());
    private final ProgressSelectorDrawable leftProgressDrawable = new ProgressSelectorDrawable(getContext());
    private final ProgressSelectorDrawable rightProgressDrawable = new ProgressSelectorDrawable(getContext());
    private final Layout topProgressTextLayout;
    private final Layout bottomProgressTextLayout;

    // source fields
    private float leftProgress = 0f;
    private float rightProgress = 1f;
    private float topProgress = 1f;
    private float bottomProgress = 1f;
    private int maxValue;

    private final StringBuilder topProgressText = new StringBuilder("100%");
    private final StringBuilder bottomProgressText = new StringBuilder("100%");
    @Nullable
    private ProgressSelectorDrawable draggingDrawable;

    public AnimationPropertiesCell(Context context) {
        super(context);
        lineProgressPaint.setColor(Theme.getColor(Theme.key_player_progress));
        lineBackgroundPaint.setColor(Theme.getColor(Theme.key_player_progressBackground));

        leftProgressDrawable.setBounds(0, 0, AndroidUtilities.dp(19), AndroidUtilities.dp(33));
        rightProgressDrawable.setBounds(leftProgressDrawable.getBounds());

        TextPaint topBottomProgressTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        topBottomProgressTextPaint.setColor(Theme.getColor(Theme.key_player_progress));
        topBottomProgressTextPaint.setTextSize(AndroidUtilities.dp(13));
        int progressTextWidth = (int) topBottomProgressTextPaint.measureText("100%");
        topProgressTextLayout = createDynamicLayout(topProgressText, topBottomProgressTextPaint, progressTextWidth);
        bottomProgressTextLayout = createDynamicLayout(bottomProgressText, topBottomProgressTextPaint, progressTextWidth);

        setMaxValue(2000);
        setLeftProgress(0.15f);
        setRightProgress(0.75f);
        setTopProgress(0.66f);
        setBottomProgress(0.66f);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(224));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean isHandled = false;

        final float x = event.getX();
        final float y = event.getY();
        final int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                if (topProgressDrawable.contains(x, y)) {
                    draggingDrawable = topProgressDrawable;
                    isHandled = true;
                } else if (bottomProgressDrawable.contains(x, y)) {
                    draggingDrawable = bottomProgressDrawable;
                    isHandled = true;
                } else if (leftProgressDrawable.contains(x, y)) {
                    draggingDrawable = leftProgressDrawable;
                    isHandled = true;
                } else if (rightProgressDrawable.contains(x, y)) {
                    draggingDrawable = rightProgressDrawable;
                    isHandled = true;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (draggingDrawable != null) {
                    float lineMaxWidth = getWidth() - lineLeftRightSpace * 2;

                    float lineProgress = (x - lineLeftRightSpace) / lineMaxWidth;
                    lineProgress = MathUtils.clamp(lineProgress, 0f, 1f);

                    float topBottomProgress = (x - lineLeftRightSpace - leftProgress * lineMaxWidth) / (lineMaxWidth - (1f - (rightProgress - leftProgress)) * lineMaxWidth);;
                    topBottomProgress = MathUtils.clamp(topBottomProgress, 0f, 1f);

                    if (draggingDrawable == topProgressDrawable) {
                        setTopProgress(1f - topBottomProgress);
                    } else if (draggingDrawable == bottomProgressDrawable) {
                        setBottomProgress(topBottomProgress);
                    } else if (draggingDrawable == leftProgressDrawable) {
                        setLeftProgress(lineProgress);
                    } else if (draggingDrawable == rightProgressDrawable) {
                        setRightProgress(lineProgress);
                    }
                    isHandled = true;
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                draggingDrawable = null;
                isHandled = true;
                break;
            }
        }

        if (isHandled) {
            invalidate();
        }

        return isHandled;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float lineMaxWidth = getWidth() - lineLeftRightSpace * 2;
        float topBottomProgressMaxWidth = lineMaxWidth * (rightProgress - leftProgress);
        float topBottomProgressLeft = lineLeftRightSpace + lineMaxWidth * leftProgress;
        float topBottomProgressRight = getWidth() - lineLeftRightSpace - lineMaxWidth * (1f - rightProgress);

        float top = 0f;


        // top progress text
        float xTopProgress = topBottomProgressLeft + topBottomProgressMaxWidth * (1f - topProgress);
        canvas.save();
        float xTranslate = MathUtils.clamp(
                xTopProgress - topProgressTextLayout.getWidth() * 0.5f,
                textLeftRightSpace,
                getWidth() - textLeftRightSpace - topProgressTextLayout.getWidth()
        );
        canvas.translate(xTranslate, 0f);
        topProgressTextLayout.draw(canvas);
        canvas.restore();

        // top progress line
        top += topProgressTextLayout.getHeight() + topProgressDrawable.getBounds().height() * 0.5f;
        float topLineVerticalCenter = top + lineHeight * 0.5f;
        canvas.drawRect(lineLeftRightSpace, top, lineLeftRightSpace + lineMaxWidth, top + lineHeight, lineBackgroundPaint);
        canvas.drawRect(xTopProgress, top, topBottomProgressLeft + topBottomProgressMaxWidth, top + lineHeight, lineProgressPaint);

        // bottom progress line
        top += topBottomLinesSpace;
        float bottomLineVerticalCenter = top + lineHeight * 0.5f;
        float xBottomProgress = topBottomProgressLeft + topBottomProgressMaxWidth * bottomProgress;
        canvas.drawRect(lineLeftRightSpace, top, lineLeftRightSpace + lineMaxWidth, top + lineHeight, lineBackgroundPaint);
        canvas.drawRect(topBottomProgressLeft, top, xBottomProgress, top + lineHeight, lineProgressPaint);

        // bottom progress text
        top += bottomProgressDrawable.getBounds().height() * 0.5f;
        canvas.save();
        xTranslate = MathUtils.clamp(
                xBottomProgress - bottomProgressTextLayout.getWidth() * 0.5f,
                textLeftRightSpace,
                getWidth() - textLeftRightSpace - bottomProgressTextLayout.getWidth()
        );
        canvas.translate(xTranslate, top);
        bottomProgressTextLayout.draw(canvas);
        canvas.restore();

        // bound lines
        canvas.drawBitmap(boundBitmap, topBottomProgressLeft - boundRectBackRadius, topLineVerticalCenter - boundRectBackRadius, bitmapPaint);
        canvas.drawBitmap(boundBitmap, topBottomProgressRight - boundRectBackRadius, topLineVerticalCenter - boundRectBackRadius, bitmapPaint);
        leftProgressDrawable.left = topBottomProgressLeft - leftProgressDrawable.getBounds().width() * 0.5f;
        leftProgressDrawable.top = topLineVerticalCenter + (bottomLineVerticalCenter - topLineVerticalCenter) * 0.5f - leftProgressDrawable.getBounds().height() * 0.5f;
        rightProgressDrawable.left = leftProgressDrawable.left + topBottomProgressMaxWidth;
        rightProgressDrawable.top = leftProgressDrawable.top;
        canvas.save();
        canvas.translate(leftProgressDrawable.left, leftProgressDrawable.top);
        leftProgressDrawable.draw(canvas);
        canvas.translate(topBottomProgressMaxWidth, 0f);
        rightProgressDrawable.draw(canvas);
        canvas.restore();

        // top bottom progress buttons
        topProgressDrawable.left = xTopProgress - topProgressDrawable.getBounds().width() * 0.5f;
        topProgressDrawable.top = topLineVerticalCenter - topProgressDrawable.getBounds().height() * 0.5f;
        bottomProgressDrawable.left = xBottomProgress - bottomProgressDrawable.getBounds().width() * 0.5f;
        bottomProgressDrawable.top = bottomLineVerticalCenter - bottomProgressDrawable.getBounds().height() * 0.5f;
        canvas.save();
        canvas.translate(topProgressDrawable.left, topProgressDrawable.top);
        topProgressDrawable.draw(canvas);
        canvas.translate(bottomProgressDrawable.left - topProgressDrawable.left, bottomProgressDrawable.top - topProgressDrawable.top);
        bottomProgressDrawable.draw(canvas);
        canvas.restore();
    }

    public void setTopProgress(float progress) {
        topProgress = progress;
        int value = Math.round(100 * progress);
        setPercentValue(topProgressText, value);
        invalidate();
    }

    public void setBottomProgress(float progress) {
        bottomProgress = progress;
        int value = Math.round(100 * progress);
        setPercentValue(bottomProgressText, value);
        invalidate();
    }

    public void setLeftProgress(float progress) {
        leftProgress = progress;
        invalidate();
    }

    public void setRightProgress(float progress) {
        rightProgress = progress;
        invalidate();
    }

    public void setMaxValue(int duration) {
        maxValue = duration;
        invalidate();
    }

    private Layout createDynamicLayout(CharSequence source, TextPaint paint, int width) {
        return new DynamicLayout(source, paint, width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
    }

    private void setPercentValue(StringBuilder builder, int value) {
        builder.replace(0, builder.length(), value + "%");
        if (builder.length() < 4) {
            final int srcLength = builder.length();
            for (int i = 0; i < 4 - srcLength; ++i) {
                builder.insert(0, ' ');
            }
        }
    }

    private Bitmap createBoundBitmap() {
        final Paint boundCircleBackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boundCircleBackPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        final Paint boundCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boundCirclePaint.setColor(0xFFFFCD00);

        final float dotWidth = AndroidUtilities.dp(2f);
        final float dotHeight = AndroidUtilities.dp(7f / 3f);
        final float dotRadius = dotWidth * 0.5f;
        final RectF dotRect = new RectF(
                boundRectBackRadius - dotWidth * 0.5f,
                boundRectBackRadius - dotHeight * 0.5f,
                boundRectBackRadius + dotWidth * 0.5f,
                boundRectBackRadius + dotHeight * 0.5f
        );

        Bitmap bitmap = Bitmap.createBitmap(AndroidUtilities.dp(10), (int)(topBottomLinesSpace + boundRectBackRadius * 2), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawCircle(boundRectBackRadius, boundRectBackRadius, boundRectBackRadius, boundCircleBackPaint);
        canvas.drawCircle(boundRectBackRadius, boundRectBackRadius, boundRectRadius, boundCirclePaint);
        canvas.drawCircle(boundRectBackRadius, bitmap.getHeight() - boundRectBackRadius, boundRectBackRadius, boundCircleBackPaint);
        canvas.drawCircle(boundRectBackRadius, bitmap.getHeight() - boundRectBackRadius, boundRectRadius, boundCirclePaint);
        float yOffset = topBottomLinesSpace / 19f;
        while (dotRect.top < topBottomLinesSpace) {
            canvas.drawRoundRect(dotRect, dotRadius, dotRadius, boundCirclePaint);
            dotRect.offset(0f, yOffset);
        }

        return bitmap;
    }


    private static class ProgressSelectorDrawable extends Drawable {

        private static final float shadowSize = AndroidUtilities.dp(3);

        private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final RectF rect = new RectF();
        private final int touchSlop;

        @Nullable
        private Bitmap bitmap;

        public float left;
        public float top;

        public ProgressSelectorDrawable(Context context) {
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            setBounds(0, 0, getIntrinsicWidth(), getIntrinsicHeight());
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (bitmap == null) {
                bitmap = createBitmap();
            }
            canvas.drawBitmap(bitmap, 0f, 0f, null);
        }

        @Override
        public void setBounds(int left, int top, int right, int bottom) {
            super.setBounds(left, top, right, bottom);
            rect.set(left + shadowSize, top + shadowSize, right - shadowSize, bottom - shadowSize);
            if (bitmap != null) {
                bitmap.recycle();
                bitmap = null;
            }
            bitmap = createBitmap();
        }

        @Override
        public void setAlpha(int alpha) {
            bitmapPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            bitmapPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return AndroidUtilities.dp(23);
        }

        @Override
        public int getIntrinsicHeight() {
            return getIntrinsicWidth();
        }

        public boolean contains(float x, float y) {
            return left - touchSlop <= x && x <= left + touchSlop + getBounds().width()
                    && top - touchSlop <= y && y <= top + touchSlop + getBounds().height();
        }

        private Bitmap createBitmap() {
            Bitmap bitmap = Bitmap.createBitmap(getBounds().width(), getBounds().height(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.WHITE);
            paint.setShadowLayer(shadowSize, 0f, 0f, Theme.getColor(Theme.key_player_progressBackground2));

            if (getBounds().width() == getBounds().height()) {
                float center = getBounds().width() * 0.5f;
                canvas.drawCircle(center, center, center - shadowSize, paint);
            } else {
                float radius = Math.min(getBounds().width(), getBounds().height()) * 0.5f;
                canvas.drawRoundRect(rect, radius, radius, paint);
            }
            return bitmap;
        }
    }
}
