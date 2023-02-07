package org.telegram.ui.Components;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.StateSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class CanvasButton {

    Path drawingPath = new Path();
    ArrayList<RectF> drawingRects = new ArrayList<>();
    int usingRectCount;
    boolean buttonPressed;
    RippleDrawable selectorDrawable;
    private final static int[] pressedState = new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed};

    private final View parent;
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Runnable delegate;
    private boolean pathCreated;
    Runnable longPressRunnable;
    Runnable longPressRunnableInner = new Runnable() {
        @Override
        public void run() {
            checkTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0));
            parent.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (longPressRunnable != null) {
                longPressRunnable.run();
            }
        }
    };
    private boolean longPressEnabled;
    CornerPathEffect pathEffect;
    boolean rounded;
    float roundRadius = AndroidUtilities.dp(12);
    Paint maskPaint;

    public CanvasButton(View parent) {
        this.parent = parent;
        paint.setPathEffect(pathEffect = new CornerPathEffect(roundRadius));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            maskPaint.setFilterBitmap(true);
            maskPaint.setPathEffect(new CornerPathEffect(AndroidUtilities.dp(12)));
            maskPaint.setColor(0xffffffff);

            final Paint maskPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
            maskPaint2.setFilterBitmap(true);
            maskPaint2.setColor(0xffffffff);
            Drawable maskDrawable = new Drawable() {

                @Override
                public void draw(Canvas canvas) {
                    if (usingRectCount > 1) {
                        drawInternal(canvas, maskPaint);
                    } else {
                        drawInternal(canvas, maskPaint2);
                    }
                }

                @Override
                public void setAlpha(int alpha) {

                }

                @Override
                public void setColorFilter(ColorFilter colorFilter) {

                }

                @Override
                public int getOpacity() {
                    return PixelFormat.TRANSPARENT;
                }
            };
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{Theme.getColor(Theme.key_listSelector) & 0x19ffffff}
            );
            selectorDrawable = new RippleDrawable(colorStateList, null, maskDrawable);
        }
    }


    public void draw(Canvas canvas) {
        drawInternal(canvas, paint);
        if (selectorDrawable != null) {
            selectorDrawable.draw(canvas);
        }
    }

    private void drawInternal(Canvas canvas, Paint paint) {
        if (usingRectCount > 1) {
            if (!pathCreated) {
                drawingPath.rewind();
                int left = 0, top = 0, right = 0, bottom = 0;
                for (int i = 0; i < usingRectCount; i++) {
                    if (i + 1 < usingRectCount) {
                        float rightCurrent = drawingRects.get(i).right;
                        float rightNext = drawingRects.get(i + 1).right;
                        if (Math.abs(rightCurrent - rightNext) < AndroidUtilities.dp(4)) {
                            drawingRects.get(i + 1).right = drawingRects.get(i).right = Math.max(rightCurrent, rightNext);
                        }
                    }
                    if (i == 0 || drawingRects.get(i).bottom > bottom) {
                        bottom = (int) drawingRects.get(i).bottom;
                    }
                    if (i == 0 || drawingRects.get(i).right > right) {
                        right = (int) drawingRects.get(i).right;
                    }
                    if (i == 0 || drawingRects.get(i).left < left) {
                        left = (int) drawingRects.get(i).left;
                    }
                    if (i == 0 || drawingRects.get(i).top < top) {
                        top = (int) drawingRects.get(i).top;
                    }
                    drawingPath.addRect(drawingRects.get(i), Path.Direction.CCW);
                    if (selectorDrawable != null) {
                        selectorDrawable.setBounds(left, top, right, bottom);
                    }
                }
                pathCreated = true;
            }
            paint.setPathEffect(pathEffect);
            canvas.drawPath(drawingPath, paint);
        } else if (usingRectCount == 1) {
            if (selectorDrawable != null) {
                selectorDrawable.setBounds((int) drawingRects.get(0).left, (int) drawingRects.get(0).top, (int) drawingRects.get(0).right, (int) drawingRects.get(0).bottom);
            }
            if (rounded) {
                paint.setPathEffect(null);
                float rad = Math.min(drawingRects.get(0).width(), drawingRects.get(0).height()) / 2f;
                canvas.drawRoundRect(drawingRects.get(0), rad, rad, paint);
            } else {
                paint.setPathEffect(pathEffect);
                canvas.drawRoundRect(drawingRects.get(0), 0, 0, paint);
            }

        }
    }

    public boolean checkTouchEvent(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (contains(x, y)) {
                buttonPressed = true;
                if (Build.VERSION.SDK_INT >= 21 && selectorDrawable != null) {
                    selectorDrawable.setHotspot(x, y);
                    selectorDrawable.setState(pressedState);
                }
                AndroidUtilities.cancelRunOnUIThread(longPressRunnableInner);
                if (longPressEnabled) {
                    AndroidUtilities.runOnUIThread(longPressRunnableInner, ViewConfiguration.getLongPressTimeout());
                }
                parent.invalidate();
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (buttonPressed) {
                if (event.getAction() == MotionEvent.ACTION_UP && delegate != null) {
                    delegate.run();
                }
                parent.playSoundEffect(SoundEffectConstants.CLICK);
                if (Build.VERSION.SDK_INT >= 21 && selectorDrawable != null) {
                    selectorDrawable.setState(StateSet.NOTHING);
                }
                buttonPressed = false;
                parent.invalidate();
            }
            AndroidUtilities.cancelRunOnUIThread(longPressRunnableInner);
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (buttonPressed && Build.VERSION.SDK_INT >= 21 && selectorDrawable != null) {
                selectorDrawable.setHotspot(x, y);
            }
        }
        return buttonPressed;
    }

    private boolean contains(int x, int y) {
        for (int i = 0; i < usingRectCount; i++) {
            if (drawingRects.get(i).contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    public void setColor(int color) {
        setColor(color, color);
    }

    public void setColor(int color, int selectorColor) {
        paint.setColor(color);
        if (selectorDrawable != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Theme.setSelectorDrawableColor(selectorDrawable, selectorColor, true);
        }
    }

    public void setDelegate(Runnable delegate) {
        this.delegate = delegate;
    }

    public void rewind() {
        pathCreated = false;
        usingRectCount = 0;
    }

    public void addRect(RectF rectF) {
        usingRectCount++;
        if (usingRectCount > drawingRects.size()) {
            drawingRects.add(new RectF());
        }
        RectF rect = drawingRects.get(usingRectCount - 1);
        rect.set(rectF);
    }

    public void setRect(RectF rectF) {
        rewind();
        addRect(rectF);
    }

    public void setLongPress(Runnable runnable) {
        longPressEnabled = true;
        longPressRunnable = runnable;
    }

    public void setRounded(boolean rounded) {
        this.rounded = rounded;
    }

    public void setRoundRadius(int radius) {
        roundRadius = radius;
        pathEffect = new CornerPathEffect(radius);
        maskPaint.setPathEffect(new CornerPathEffect(radius));
    }

    public void cancelRipple() {
        if (Build.VERSION.SDK_INT >= 21 && selectorDrawable != null) {
            selectorDrawable.setState(StateSet.NOTHING);
            selectorDrawable.jumpToCurrentState();
        }

    }

    public void setRect(int x, int y, int x1, int y1) {
        AndroidUtilities.rectTmp.set(x, y, x1, y1);
        setRect(AndroidUtilities.rectTmp);
    }
}
