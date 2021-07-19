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
    private RectF rect = new RectF();
    private float[] lines;

    public EditTextOutline(Context context) {
        super(context);

        mStrokeColor = Color.TRANSPARENT;
        setInputType(getInputType() | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        mUpdateCachedBitmap = true;
        textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    protected void onTextChanged(CharSequence text, int start, int before, int after) {
        super.onTextChanged(text, start, before, after);
        mUpdateCachedBitmap = true;
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            mUpdateCachedBitmap = true;
            if (mCache != null) {
                mCache.recycle();
            }
            mCache = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        } else {
            mCache = null;
        }
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
                final String text = getText().toString();

                mCanvas.setBitmap(mCache);
                mCanvas.drawColor(0, PorterDuff.Mode.CLEAR);

                float strokeWidth = mStrokeWidth > 0 ? mStrokeWidth : (float) Math.ceil(getTextSize() / 11.5f);
                textPaint.setStrokeWidth(strokeWidth);
                textPaint.setColor(mStrokeColor);
                textPaint.setTextSize(getTextSize());
                textPaint.setTypeface(getTypeface());
                textPaint.setStyle(Paint.Style.FILL_AND_STROKE);

                StaticLayout sl = new StaticLayout(text, textPaint, w, Layout.Alignment.ALIGN_CENTER, 1, 0, true);

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
            paint.setColor(mFrameColor);
            Layout sl = getLayout();
            if (lines == null || lines.length != sl.getLineCount()) {
                lines = new float[sl.getLineCount()];
            }
            float rad = AndroidUtilities.dp(6);
            float padding = AndroidUtilities.dp(6);
            float inset = AndroidUtilities.dp(26);
            for (int a = 0; a < lines.length; a++) {
                float w = (float) Math.ceil(sl.getLineRight(a) - sl.getLineLeft(a));
                if (w > AndroidUtilities.dp(1)) {
                    lines[a] = w + padding * 2;
                } else {
                    lines[a] = 0;
                }
            }
            boolean hasChanges;
            do {
                hasChanges = false;
                for (int a = 1; a < lines.length; a++) {
                    if (lines[a] == 0) {
                        continue;
                    }
                    float diff = lines[a] - lines[a - 1];
                    if (diff > 0) {
                        if (diff < inset) {
                            lines[a - 1] = lines[a];
                            hasChanges = true;
                        } else if (diff < rad * 4) {
                            lines[a] += Math.ceil(rad * 4 - diff);
                            hasChanges = true;
                        }
                    } else if (diff < 0) {
                        if (-diff < inset) {
                            lines[a] = lines[a - 1];
                            hasChanges = true;
                        } else if (-diff < rad * 4) {
                            lines[a - 1] += Math.ceil(rad * 4 + diff);
                            hasChanges = true;
                        }
                    }
                }
            } while (hasChanges);
            int cx = getMeasuredWidth() / 2;
            float cx1, cx2;
            float top = (getMeasuredHeight() - sl.getHeight()) / 2;
            for (int a = 0; a < lines.length; a++) {
                int h = (sl.getLineBottom(a) - sl.getLineTop(a)) - (a != lines.length - 1 ? AndroidUtilities.dp(1) : 0) + (a != 0 ? AndroidUtilities.dp(1) : 0);
                if (lines[a] <= padding * 2) {
                    top += h;
                    continue;
                }
                boolean topLess = a > 0 && lines[a - 1] > lines[a] && lines[a - 1] > padding * 2;
                boolean bottomLess = a + 1 < lines.length && lines[a + 1] > lines[a] && lines[a + 1] > padding * 2;
                boolean drawTop = a == 0 || lines[a - 1] != lines[a];
                boolean drawBottom = a == lines.length - 1 || lines[a] != lines[a + 1];

                path.reset();

                if (a != 0) {
                    top -= 1;
                    h += 1;
                }
                float bottom = (float) Math.ceil(top + h);

                cx1 = cx - lines[a] / 2 + rad;
                cx2 = cx + lines[a] / 2 - rad;

                path.moveTo(cx1, top);

                if (drawTop) {
                    if (topLess) {
                        path.lineTo(cx2 + rad * 2, top);
                        rect.set(cx2 + rad, top, cx2 + rad * 3, top + rad * 2);
                        path.arcTo(rect, 270, -90, false);
                    } else {
                        path.lineTo(cx2, top);
                        rect.set(cx2 - rad, top, cx2 + rad, top + rad * 2);
                        path.arcTo(rect, 270, 90, false);
                    }
                } else {
                    path.lineTo(cx2 + rad, top);
                }

                path.lineTo(cx2 + rad, bottom - rad);

                if (drawBottom) {
                    if (bottomLess) {
                        rect.set(cx2 + rad, bottom - rad * 2, cx2 + rad * 3, bottom);
                        path.arcTo(rect, 180, -90, false);
                        path.lineTo(cx1 - rad * 2, bottom);
                    } else {
                        rect.set(cx2 - rad, bottom - rad * 2, cx2 + rad, bottom);
                        path.arcTo(rect, 0, 90, false);
                        path.lineTo(cx1, bottom);
                    }

                    if (bottomLess) {
                        rect.set(cx1 - rad * 3, bottom - rad * 2, cx1 - rad, bottom);
                        path.arcTo(rect, 90, -90, false);
                    } else {
                        rect.set(cx1 - rad, bottom - rad * 2, cx1 + rad, bottom);
                        path.arcTo(rect, 90, 90, false);
                    }
                } else {
                    path.lineTo(cx2 + rad, bottom);
                    path.lineTo(cx1 - rad, bottom);
                }

                path.lineTo(cx1 - rad, top - rad);

                if (drawTop) {
                    if (topLess) {
                        rect.set(cx1 - rad * 3, top, cx1 - rad, top + rad * 2);
                        path.arcTo(rect, 0, -90, false);
                    } else {
                        rect.set(cx1 - rad, top, cx1 + rad, top + rad * 2);
                        path.arcTo(rect, 180, 90, false);
                    }
                } else {
                    path.lineTo(cx1 - rad, top);
                }

                path.close();

                canvas.drawPath(path, paint);

                if (a != 0) {
                    top += 1;
                    h -= 1;
                }
                top += h;
            }
        }
        super.onDraw(canvas);
    }
}