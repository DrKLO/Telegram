package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Property;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

public class ZoomControlView extends View {

    private Drawable minusDrawable;
    private Drawable plusDrawable;
    private Drawable progressDrawable;
    private Drawable filledProgressDrawable;
    private Drawable knobDrawable;
    private Drawable pressedKnobDrawable;

    private int minusCx;
    private int minusCy;
    private int plusCx;
    private int plusCy;

    private int progressStartX;
    private int progressStartY;
    private int progressEndX;
    private int progressEndY;

    private float zoom;

    private boolean pressed;
    private boolean knobPressed;
    private float knobStartX;
    private float knobStartY;

    private float animatingToZoom;
    private AnimatorSet animatorSet;

    private ZoomControlViewDelegate delegate;

    public interface ZoomControlViewDelegate {
        void didSetZoom(float zoom);
    }

    public final Property<ZoomControlView, Float> ZOOM_PROPERTY = new AnimationProperties.FloatProperty<ZoomControlView>("clipProgress") {
        @Override
        public void setValue(ZoomControlView object, float value) {
            zoom = value;
            if (delegate != null) {
                delegate.didSetZoom(zoom);
            }
            invalidate();
        }

        @Override
        public Float get(ZoomControlView object) {
            return zoom;
        }
    };

    public ZoomControlView(Context context) {
        super(context);

        minusDrawable = context.getResources().getDrawable(R.drawable.zoom_minus);
        plusDrawable = context.getResources().getDrawable(R.drawable.zoom_plus);
        progressDrawable = context.getResources().getDrawable(R.drawable.zoom_slide);
        filledProgressDrawable = context.getResources().getDrawable(R.drawable.zoom_slide_a);
        knobDrawable = context.getResources().getDrawable(R.drawable.zoom_round);
        pressedKnobDrawable = context.getResources().getDrawable(R.drawable.zoom_round_b);
    }

    public float getZoom() {
        if (animatorSet != null) {
            return animatingToZoom;
        }
        return zoom;
    }

    public void setZoom(float value, boolean notify) {
        if (value == zoom) {
            return;
        }
        if (value < 0) {
            value = 0;
        } else if (value > 1.0f) {
            value = 1.0f;
        }
        zoom = value;
        if (notify && delegate != null) {
            delegate.didSetZoom(zoom);
        }
        invalidate();
    }

    public void setDelegate(ZoomControlViewDelegate zoomControlViewDelegate) {
        delegate = zoomControlViewDelegate;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        int action = event.getAction();
        boolean handled = false;
        boolean isPortrait = getMeasuredWidth() > getMeasuredHeight();
        int knobX = (int) (progressStartX + (progressEndX - progressStartX) * zoom);
        int knobY = (int) (progressStartY + (progressEndY - progressStartY) * zoom);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            if (x >= knobX - AndroidUtilities.dp(20) && x <= knobX + AndroidUtilities.dp(20) && y >= knobY - AndroidUtilities.dp(25) && y <= knobY + AndroidUtilities.dp(25)) {
                if (action == MotionEvent.ACTION_DOWN) {
                    knobPressed = true;
                    knobStartX = x - knobX;
                    knobStartY = y - knobY;
                    invalidate();
                }
                handled = true;
            } else if (x >= minusCx - AndroidUtilities.dp(16) && x <= minusCx + AndroidUtilities.dp(16) && y >= minusCy - AndroidUtilities.dp(16) && y <= minusCy + AndroidUtilities.dp(16)) {
                if (action == MotionEvent.ACTION_UP && animateToZoom((float) Math.floor(getZoom() / 0.25f) * 0.25f - 0.25f)) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                } else {
                    pressed = true;
                }
                handled = true;
            } else if (x >= plusCx - AndroidUtilities.dp(16) && x <= plusCx + AndroidUtilities.dp(16) && y >= plusCy - AndroidUtilities.dp(16) && y <= plusCy + AndroidUtilities.dp(16)) {
                if (action == MotionEvent.ACTION_UP && animateToZoom((float) Math.floor(getZoom() / 0.25f) * 0.25f + 0.25f)) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                } else {
                    pressed = true;
                }
                handled = true;
            } else if (isPortrait) {
                if (x >= progressStartX && x <= progressEndX) {
                    if (action == MotionEvent.ACTION_DOWN) {
                        knobStartX = x;
                        pressed = true;
                    } else if (Math.abs(knobStartX - x) <= AndroidUtilities.dp(10)) {
                        zoom = (x - progressStartX) / (progressEndX - progressStartX);
                        if (delegate != null) {
                            delegate.didSetZoom(zoom);
                        }
                        invalidate();
                    }
                    handled = true;
                }
            } else {
                if (y >= progressStartY && y <= progressEndY) {
                    if (action == MotionEvent.ACTION_UP) {
                        knobStartY = y;
                        pressed = true;
                    } else if (Math.abs(knobStartY - y) <= AndroidUtilities.dp(10)) {
                        zoom = (y - progressStartY) / (progressEndY - progressStartY);
                        if (delegate != null) {
                            delegate.didSetZoom(zoom);
                        }
                        invalidate();
                    }
                    handled = true;
                }
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (knobPressed) {
                if (isPortrait) {
                    zoom = ((x + knobStartX) - progressStartX) / (progressEndX - progressStartX);
                } else {
                    zoom = ((y + knobStartY) - progressStartY) / (progressEndY - progressStartY);
                }
                if (zoom < 0) {
                    zoom = 0;
                } else if (zoom > 1.0f) {
                    zoom = 1.0f;
                }
                if (delegate != null) {
                    delegate.didSetZoom(zoom);
                }
                invalidate();
            }
        }
        if (action == MotionEvent.ACTION_UP) {
            pressed = false;
            knobPressed = false;
            invalidate();
        }
        return handled || pressed || knobPressed || super.onTouchEvent(event);
    }

    private boolean animateToZoom(float zoom) {
        if (zoom < 0 || zoom > 1.0f) {
            return false;
        }
        if (animatorSet != null) {
            animatorSet.cancel();
        }
        animatingToZoom = zoom;
        animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(this, ZOOM_PROPERTY, zoom));
        animatorSet.setDuration(180);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animatorSet = null;
            }
        });
        animatorSet.start();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int cx = getMeasuredWidth() / 2;
        int cy = getMeasuredHeight() / 2;
        boolean isPortrait = getMeasuredWidth() > getMeasuredHeight();

        if (isPortrait) {
            minusCx = AndroidUtilities.dp(16 + 25);
            minusCy = cy;
            plusCx = getMeasuredWidth() - AndroidUtilities.dp(16 + 25);
            plusCy = cy;

            progressStartX = minusCx + AndroidUtilities.dp(18);
            progressStartY = cy;

            progressEndX = plusCx - AndroidUtilities.dp(18);
            progressEndY = cy;
        } else {
            minusCx = cx;
            minusCy = AndroidUtilities.dp(16 + 25);
            plusCx = cx;
            plusCy = getMeasuredHeight() - AndroidUtilities.dp(16 + 25);

            progressStartX = cx;
            progressStartY = minusCy + AndroidUtilities.dp(18);

            progressEndX = cx;
            progressEndY = plusCy - AndroidUtilities.dp(18);
        }
        minusDrawable.setBounds(minusCx - AndroidUtilities.dp(7), minusCy - AndroidUtilities.dp(7), minusCx + AndroidUtilities.dp(7), minusCy + AndroidUtilities.dp(7));
        minusDrawable.draw(canvas);
        plusDrawable.setBounds(plusCx - AndroidUtilities.dp(7), plusCy - AndroidUtilities.dp(7), plusCx + AndroidUtilities.dp(7), plusCy + AndroidUtilities.dp(7));
        plusDrawable.draw(canvas);

        int totalX = progressEndX - progressStartX;
        int totalY = progressEndY - progressStartY;
        int knobX = (int) (progressStartX + totalX * zoom);
        int knobY = (int) (progressStartY + totalY * zoom);

        if (isPortrait) {
            progressDrawable.setBounds(progressStartX, progressStartY - AndroidUtilities.dp(3), progressEndX, progressStartY + AndroidUtilities.dp(3));
            filledProgressDrawable.setBounds(progressStartX, progressStartY - AndroidUtilities.dp(3), knobX, progressStartY + AndroidUtilities.dp(3));
        } else {
            progressDrawable.setBounds(progressStartY, 0, progressEndY, AndroidUtilities.dp(6));
            filledProgressDrawable.setBounds(progressStartY, 0, knobY, AndroidUtilities.dp(6));
            canvas.save();
            canvas.rotate(90);
            canvas.translate(0, -progressStartX - AndroidUtilities.dp(3));
        }
        progressDrawable.draw(canvas);
        filledProgressDrawable.draw(canvas);
        if (!isPortrait) {
            canvas.restore();
        }

        Drawable drawable = knobPressed ? pressedKnobDrawable : knobDrawable;
        int size = drawable.getIntrinsicWidth();
        drawable.setBounds(knobX - size / 2, knobY - size / 2, knobX + size / 2, knobY + size / 2);
        drawable.draw(canvas);
    }
}
