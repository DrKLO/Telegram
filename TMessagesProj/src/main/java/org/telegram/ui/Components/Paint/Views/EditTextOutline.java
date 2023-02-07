package org.telegram.ui.Components.Paint.Views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.text.InputType;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.EditTextBoldCursor;

import java.util.Arrays;

public class EditTextOutline extends EditTextBoldCursor {

    private Canvas mCanvas = new Canvas();
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap mCache;
    private boolean mUpdateCachedBitmap;
    private int mStrokeColor;
    private float mStrokeWidth;
    private int mFrameColor;
    private Path path = new Path();

    private RectF[] lines;
    private boolean isFrameDirty;

    public EditTextOutline(Context context) {
        super(context);

        mStrokeColor = Color.TRANSPARENT;
        setInputType(getInputType() | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        mUpdateCachedBitmap = true;
        isFrameDirty = true;
        textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    protected void onTextChanged(CharSequence text, int start, int before, int after) {
        super.onTextChanged(text, start, before, after);
        mUpdateCachedBitmap = true;
        isFrameDirty = true;
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            mUpdateCachedBitmap = true;
            isFrameDirty = true;
            if (mCache != null) {
                mCache.recycle();
            }
            mCache = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        } else {
            mCache = null;
        }
    }

    @Override
    public void setGravity(int gravity) {
        super.setGravity(gravity);
        mUpdateCachedBitmap = true;
        isFrameDirty = true;
        invalidate();
    }

    public void setStrokeColor(int strokeColor) {
        mStrokeColor = strokeColor;
        mUpdateCachedBitmap = true;
        invalidate();
    }

    public void setFrameColor(int frameColor) {
        if (mFrameColor == 0 && frameColor != 0) {
            setPadding(AndroidUtilities.dp(7 + 12), AndroidUtilities.dp(7), AndroidUtilities.dp(7 + 12), AndroidUtilities.dp(7));
            setCursorColor(0xff000000);
        } else if (mFrameColor != 0 && frameColor == 0) {
            setPadding(AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7));
            setCursorColor(0xffffffff);
        }
        mFrameColor = frameColor;

        if (mFrameColor != 0) {
            float lightness = AndroidUtilities.computePerceivedBrightness(mFrameColor);
            if (lightness == 0) {
                lightness = Color.red(mFrameColor) / 255.0f;
            }
            if (lightness > 0.87) {
                setTextColor(0xff000000);
            } else {
                setTextColor(0xffffffff);
            }
            isFrameDirty = true;
        }
        mUpdateCachedBitmap = true;
        invalidate();
    }

    public void setStrokeWidth(float strokeWidth) {
        mStrokeWidth = strokeWidth;
        mUpdateCachedBitmap = true;
        invalidate();
    }

    @SuppressLint("DrawAllocation")
    protected void onDraw(Canvas canvas) {
        if (mCache != null && mStrokeColor != Color.TRANSPARENT) {
            if (mUpdateCachedBitmap) {
                final int w = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
                final int h = getMeasuredHeight();
                final CharSequence text = getText();

                mCanvas.setBitmap(mCache);
                mCanvas.drawColor(0, PorterDuff.Mode.CLEAR);

                float strokeWidth = mStrokeWidth > 0 ? mStrokeWidth : (float) Math.ceil(getTextSize() / 11.5f);
                textPaint.setStrokeWidth(strokeWidth);
                textPaint.setColor(mStrokeColor);
                textPaint.setTextSize(getTextSize());
                textPaint.setTypeface(getTypeface());
                textPaint.setStyle(Paint.Style.FILL_AND_STROKE);

                Layout.Alignment alignment = Layout.Alignment.ALIGN_NORMAL;
                if (getLayout() != null) {
                    alignment = getLayout().getAlignment();
                }
                StaticLayout sl = new StaticLayout(text, textPaint, w, alignment, 1, 0, true);

                mCanvas.save();
                float ty = (h - getPaddingTop() - getPaddingBottom() - sl.getHeight()) / 2.0f;
                mCanvas.translate(getPaddingLeft(), ty + getPaddingTop());
                sl.draw(mCanvas);
                mCanvas.restore();

                mUpdateCachedBitmap = false;
            }
            canvas.drawBitmap(mCache, 0, 0, textPaint);
        }
        if (mFrameColor != 0) {
            // have you heard about CornerPathEffect?
            paint.setColor(mFrameColor);
            Layout layout = getLayout();
            if (layout == null) {
                super.onDraw(canvas);
                return;
            }
            if (lines == null || lines.length != layout.getLineCount()) {
                lines = new RectF[layout.getLineCount()];
                isFrameDirty = true;
            }
            if (isFrameDirty) {
                isFrameDirty = false;
                for (int i = 0; i < layout.getLineCount(); i++) {
                    if (lines[i] == null) {
                        lines[i] = new RectF();
                    }
                    lines[i].set(layout.getLineLeft(i), layout.getLineTop(i), layout.getLineRight(i), layout.getLineBottom(i));

                    if (lines[i].width() > AndroidUtilities.dp(1)) {
                        int pad = AndroidUtilities.dp(6);
                        lines[i].right += AndroidUtilities.dp(32);
                        lines[i].bottom += pad;
                    } else {
                        lines[i].left = lines[i].right;
                    }
                }
            }
            path.rewind();

            float rad = AndroidUtilities.dp(16);
            float cornersOffset = AndroidUtilities.dp(8);

            for (int i = 0; i < lines.length; i++) {
                if (lines[i].width() == 0) {
                    continue;
                }

                if (i != lines.length - 1 && lines[i].left > lines[i + 1].left) { // Left top arc corner
                    if (lines[i].left - lines[i + 1].left > rad + cornersOffset) {
                        float bottom = lines[i + 1].top;
                        AndroidUtilities.rectTmp.set(lines[i].left - rad * 2, bottom - rad * 2, lines[i].left, bottom);
                        path.moveTo(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.bottom);
                        path.arcTo(AndroidUtilities.rectTmp, 90, -90);
                        path.lineTo(AndroidUtilities.rectTmp.right, AndroidUtilities.rectTmp.bottom);
                        path.lineTo(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.bottom);
                    }
                }

                if (i != lines.length - 1 && lines[i].right < lines[i + 1].right) { // Right top arc corner
                    if (lines[i + 1].right - lines[i].right > rad + cornersOffset) {
                        float bottom = lines[i + 1].top;
                        AndroidUtilities.rectTmp.set(lines[i].right, bottom - rad * 2, lines[i].right + rad * 2, bottom);
                        path.moveTo(AndroidUtilities.rectTmp.right, AndroidUtilities.rectTmp.bottom);
                        path.arcTo(AndroidUtilities.rectTmp, 90, 90);
                        path.lineTo(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.bottom);
                        path.lineTo(AndroidUtilities.rectTmp.right, AndroidUtilities.rectTmp.bottom);
                    }
                }

                if (i != 0 && lines[i].left > lines[i - 1].left) { // Left bottom arc corner
                    if (lines[i].left - lines[i - 1].left > rad + cornersOffset) {
                        float top = lines[i - 1].bottom;
                        AndroidUtilities.rectTmp.set(lines[i].left - rad * 2, top, lines[i].left, top + rad * 2);
                        path.moveTo(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.top);
                        path.arcTo(AndroidUtilities.rectTmp, -90, 90);
                        path.lineTo(AndroidUtilities.rectTmp.right, AndroidUtilities.rectTmp.top);
                        path.lineTo(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.top);
                    } else {
                        lines[i].left = lines[i - 1].left;
                    }
                }
                if (i != 0 && lines[i].right < lines[i - 1].right) { // Right bottom arc corner
                    if (lines[i - 1].right - lines[i].right > rad + cornersOffset) {
                        float top = lines[i - 1].bottom;
                        AndroidUtilities.rectTmp.set(lines[i].right, top, lines[i].right + rad * 2, top + rad * 2);
                        path.moveTo(AndroidUtilities.rectTmp.right, AndroidUtilities.rectTmp.top);
                        path.arcTo(AndroidUtilities.rectTmp, -90, -90);
                        path.lineTo(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.top);
                        path.lineTo(AndroidUtilities.rectTmp.right, AndroidUtilities.rectTmp.top);
                    } else {
                        lines[i].right = lines[i - 1].right;
                    }
                }
            }

            float[] radii = new float[8];
            for (int i = 0; i < lines.length; i++) {
                Arrays.fill(radii, 0);

                if (i == 0 || lines[i].left < lines[i - 1].left || lines[i - 1].width() == 0) {
                    radii[0] = radii[1] = rad; // Top left corner
                }
                if (i == 0 || lines[i].right > lines[i - 1].right || lines[i - 1].width() == 0) {
                    radii[2] = radii[3] = rad; // Top right corner
                }
                if (i == lines.length - 1 || lines[i + 1].left > lines[i].left || lines[i + 1].width() == 0) {
                    radii[6] = radii[7] = rad; // Bottom left corner
                }
                if (i == lines.length - 1 || lines[i + 1].right < lines[i].right || lines[i + 1].width() == 0) {
                    radii[4] = radii[5] = rad; // Bottom right corner
                }

                AndroidUtilities.rectTmp.set(lines[i]);
                path.addRoundRect(AndroidUtilities.rectTmp, radii, Path.Direction.CW);
            }
            path.close();

            canvas.drawPath(path, paint);
        }
        super.onDraw(canvas);
    }
}