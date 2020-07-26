/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.util.StateSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class SeekBarView extends FrameLayout {

    private final SeekBarAccessibilityDelegate seekBarAccessibilityDelegate;

    private Paint innerPaint1;
    private Paint outerPaint1;
    private int thumbSize;
    private int selectorWidth;
    private int thumbX;
    private int thumbDX;
    private float progressToSet;
    private boolean pressed;
    private SeekBarViewDelegate delegate;
    private boolean reportChanges;
    private float bufferedProgress;
    private Drawable hoverDrawable;
    private long lastUpdateTime;
    private float currentRadius;
    private int[] pressedState = new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed};

    public interface SeekBarViewDelegate {
        void onSeekBarDrag(boolean stop, float progress);
        void onSeekBarPressed(boolean pressed);
        default CharSequence getContentDescription() {
            return null;
        }
        default int getStepsCount() {
            return 0;
        }
    }

    public SeekBarView(Context context) {
        this(context, false);
    }

    public SeekBarView(Context context, boolean inPercents) {
        super(context);
        setWillNotDraw(false);
        innerPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);

        outerPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerPaint1.setColor(Theme.getColor(Theme.key_player_progress));

        selectorWidth = AndroidUtilities.dp(32);
        thumbSize = AndroidUtilities.dp(24);
        currentRadius = AndroidUtilities.dp(6);

        if (Build.VERSION.SDK_INT >= 21) {
            int color = Theme.getColor(Theme.key_player_progress);
            hoverDrawable = Theme.createSelectorDrawable(Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)), 1, AndroidUtilities.dp(16));
            hoverDrawable.setCallback(this);
            hoverDrawable.setVisible(true, false);
        }

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setAccessibilityDelegate(seekBarAccessibilityDelegate = new FloatSeekBarAccessibilityDelegate(inPercents) {
            @Override
            public float getProgress() {
                return SeekBarView.this.getProgress();
            }

            @Override
            public void setProgress(float progress) {
                pressed = true;
                SeekBarView.this.setProgress(progress);
                if (delegate != null) {
                    delegate.onSeekBarDrag(true, progress);
                }
                pressed = false;
            }

            @Override
            protected float getDelta() {
                final int stepsCount = delegate.getStepsCount();
                if (stepsCount > 0) {
                    return 1f / stepsCount;
                } else {
                    return super.getDelta();
                }
            }

            @Override
            public CharSequence getContentDescription(View host) {
                return delegate != null ? delegate.getContentDescription() : null;
            }
        });
    }

    public void setColors(int inner, int outer) {
        innerPaint1.setColor(inner);
        outerPaint1.setColor(outer);
        if (hoverDrawable != null) {
            Theme.setDrawableColor(hoverDrawable, Color.argb(40, Color.red(outer), Color.green(outer), Color.blue(outer)));
        }
    }

    public void setInnerColor(int color) {
        innerPaint1.setColor(color);
    }

    public void setOuterColor(int color) {
        outerPaint1.setColor(color);
        if (hoverDrawable != null) {
            Theme.setDrawableColor(hoverDrawable, Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)));
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return onTouch(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return onTouch(event);
    }

    public void setReportChanges(boolean value) {
        reportChanges = value;
    }

    public void setDelegate(SeekBarViewDelegate seekBarViewDelegate) {
        delegate = seekBarViewDelegate;
    }

    boolean captured;
    float sx, sy;
    boolean onTouch(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            sx = ev.getX();
            sy = ev.getY();
            return true;
        } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            captured = false;
            if (ev.getAction() == MotionEvent.ACTION_UP) {
                final ViewConfiguration vc = ViewConfiguration.get(getContext());
                if (Math.abs(ev.getY() - sy) < vc.getScaledTouchSlop()) {
                    int additionWidth = (getMeasuredHeight() - thumbSize) / 2;
                    if (!(thumbX - additionWidth <= ev.getX() && ev.getX() <= thumbX + thumbSize + additionWidth)) {
                        thumbX = (int) ev.getX() - thumbSize / 2;
                        if (thumbX < 0) {
                            thumbX = 0;
                        } else if (thumbX > getMeasuredWidth() - selectorWidth) {
                            thumbX = getMeasuredWidth() - selectorWidth;
                        }
                    }
                    thumbDX = (int) (ev.getX() - thumbX);
                    pressed = true;
                }
            }
            if (pressed) {
                if (ev.getAction() == MotionEvent.ACTION_UP) {
                    delegate.onSeekBarDrag(true, (float) thumbX / (float) (getMeasuredWidth() - selectorWidth));
                }
                if (Build.VERSION.SDK_INT >= 21 && hoverDrawable != null) {
                    hoverDrawable.setState(StateSet.NOTHING);
                }
                delegate.onSeekBarPressed(false);
                pressed = false;
                invalidate();
                return true;
            }
        } else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
            if (!captured) {
                final ViewConfiguration vc = ViewConfiguration.get(getContext());
                if (Math.abs(ev.getY() - sy) > vc.getScaledTouchSlop()) {
                    return false;
                }
                if (Math.abs(ev.getX() - sx) > vc.getScaledTouchSlop()) {
                    captured = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    int additionWidth = (getMeasuredHeight() - thumbSize) / 2;
                    if (ev.getY() >= 0 && ev.getY() <= getMeasuredHeight()) {
                        if (!(thumbX - additionWidth <= ev.getX() && ev.getX() <= thumbX + thumbSize + additionWidth)) {
                            thumbX = (int) ev.getX() - thumbSize / 2;
                            if (thumbX < 0) {
                                thumbX = 0;
                            } else if (thumbX > getMeasuredWidth() - selectorWidth) {
                                thumbX = getMeasuredWidth() - selectorWidth;
                            }
                        }
                        thumbDX = (int) (ev.getX() - thumbX);
                        pressed = true;
                        delegate.onSeekBarPressed(true);
                        if (Build.VERSION.SDK_INT >= 21 && hoverDrawable != null) {
                            hoverDrawable.setState(pressedState);
                            hoverDrawable.setHotspot(ev.getX(), ev.getY());
                        }
                        invalidate();
                        return true;
                    }
                }
            } else {
                if (pressed) {
                    thumbX = (int) (ev.getX() - thumbDX);
                    if (thumbX < 0) {
                        thumbX = 0;
                    } else if (thumbX > getMeasuredWidth() - selectorWidth) {
                        thumbX = getMeasuredWidth() - selectorWidth;
                    }
                    if (reportChanges) {
                        delegate.onSeekBarDrag(false, (float) thumbX / (float) (getMeasuredWidth() - selectorWidth));
                    }
                    if (Build.VERSION.SDK_INT >= 21 && hoverDrawable != null) {
                        hoverDrawable.setHotspot(ev.getX(), ev.getY());
                    }
                    invalidate();
                    return true;
                }
            }
        }
        return false;
    }

    public float getProgress() {
        if (getMeasuredWidth() == 0) {
            return progressToSet;
        }
        return thumbX / (float) (getMeasuredWidth() - selectorWidth);
    }

    public void setProgress(float progress) {
        if (getMeasuredWidth() == 0) {
            progressToSet = progress;
            return;
        }
        progressToSet = -1;
        int newThumbX = (int) Math.ceil((getMeasuredWidth() - selectorWidth) * progress);
        if (thumbX != newThumbX) {
            thumbX = newThumbX;
            if (thumbX < 0) {
                thumbX = 0;
            } else if (thumbX > getMeasuredWidth() - selectorWidth) {
                thumbX = getMeasuredWidth() - selectorWidth;
            }
            invalidate();
        }
    }

    public void setBufferedProgress(float progress) {
        bufferedProgress = progress;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (progressToSet >= 0 && getMeasuredWidth() > 0) {
            setProgress(progressToSet);
            progressToSet = -1;
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || who == hoverDrawable;
    }

    public boolean isDragging() {
        return pressed;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int y = (getMeasuredHeight() - thumbSize) / 2;
        innerPaint1.setColor(Theme.getColor(Theme.key_player_progressBackground));
        canvas.drawRect(selectorWidth / 2, getMeasuredHeight() / 2 - AndroidUtilities.dp(1), getMeasuredWidth() - selectorWidth / 2, getMeasuredHeight() / 2 + AndroidUtilities.dp(1), innerPaint1);
        if (bufferedProgress > 0) {
            innerPaint1.setColor(Theme.getColor(Theme.key_player_progressCachedBackground));
            canvas.drawRect(selectorWidth / 2, getMeasuredHeight() / 2 - AndroidUtilities.dp(1), selectorWidth / 2 + bufferedProgress * (getMeasuredWidth() - selectorWidth), getMeasuredHeight() / 2 + AndroidUtilities.dp(1), innerPaint1);
        }
        canvas.drawRect(selectorWidth / 2, getMeasuredHeight() / 2 - AndroidUtilities.dp(1), selectorWidth / 2 + thumbX, getMeasuredHeight() / 2 + AndroidUtilities.dp(1), outerPaint1);
        if (hoverDrawable != null) {
            int dx = thumbX + selectorWidth / 2 - AndroidUtilities.dp(16);
            int dy = y + thumbSize / 2 - AndroidUtilities.dp(16);
            hoverDrawable.setBounds(dx, dy, dx + AndroidUtilities.dp(32), dy + AndroidUtilities.dp(32));
            hoverDrawable.draw(canvas);
        }
        int newRad = AndroidUtilities.dp(pressed ? 8 : 6);
        if (currentRadius != newRad) {
            long newUpdateTime = SystemClock.elapsedRealtime();
            long dt = newUpdateTime - lastUpdateTime;
            if (dt > 18) {
                dt = 16;
            }
            if (currentRadius < newRad) {
                currentRadius += AndroidUtilities.dp(1) * (dt / 60.0f);
                if (currentRadius > newRad) {
                    currentRadius = newRad;
                }
            } else {
                currentRadius -= AndroidUtilities.dp(1) * (dt / 60.0f);
                if (currentRadius < newRad) {
                    currentRadius = newRad;
                }
            }
            invalidate();
        }
        canvas.drawCircle(thumbX + selectorWidth / 2, y + thumbSize / 2, currentRadius, outerPaint1);
    }

    public SeekBarAccessibilityDelegate getSeekBarAccessibilityDelegate() {
        return seekBarAccessibilityDelegate;
    }
}
