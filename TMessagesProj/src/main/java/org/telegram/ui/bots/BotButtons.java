package org.telegram.ui.bots;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedColor;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.voip.CellFlickerDrawable;

public class BotButtons extends FrameLayout {

    public static class ButtonState {
        public boolean visible;
        public boolean active;
        public boolean progressVisible;
        public boolean shineEffect;
        public String text;
        public int color;
        public int textColor;

        public String position;

        public static ButtonState of(boolean visible, boolean active, boolean progress, boolean shine, String text, int color, int textColor) {
            return of(visible, active, progress, shine, text, color, textColor, null);
        }

        public static ButtonState of(boolean visible, boolean active, boolean progress, boolean shine, String text, int color, int textColor, String position) {
            ButtonState state = new ButtonState();
            state.visible = visible;
            state.active = active;
            state.progressVisible = progress;
            state.shineEffect = shine;
            state.text = text;
            state.color = color;
            state.textColor = textColor;
            state.position = position;
            return state;
        }
    }

    public static class ButtonsState {
        public ButtonState main = new ButtonState();
        public ButtonState secondary = new ButtonState();
        public int backgroundColor;
    }

    private class Button {
        public final RectF bounds = new RectF();

        public final AnimatedFloat alpha = new AnimatedFloat(BotButtons.this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        public final AnimatedFloat x = new AnimatedFloat(BotButtons.this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        public final AnimatedFloat y = new AnimatedFloat(BotButtons.this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        public final AnimatedFloat w = new AnimatedFloat(BotButtons.this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

        public final AnimatedColor backgroundColor = new AnimatedColor(BotButtons.this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        public final AnimatedColor textColor = new AnimatedColor(BotButtons.this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

        public final AnimatedFloat progressAlpha = new AnimatedFloat(BotButtons.this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        public final AnimatedFloat flickerAlpha = new AnimatedFloat(BotButtons.this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

        public final ButtonBounce bounce = new ButtonBounce(BotButtons.this);
        public final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public final AnimatedTextView.AnimatedTextDrawable textDrawable = new AnimatedTextView.AnimatedTextDrawable(true, true, true);

        public int rippleColor;
        public final Drawable ripple = Theme.createRadSelectorDrawable(0, 9, 9);
        public final CircularProgressDrawable progress = new CircularProgressDrawable();

        public final CellFlickerDrawable flicker = new CellFlickerDrawable();

        {
            textDrawable.setGravity(Gravity.CENTER);
            textDrawable.setTextSize(dp(14));
            textDrawable.setTypeface(AndroidUtilities.bold());
            textDrawable.setOverrideFullWidth(AndroidUtilities.displaySize.x * 4);
            textDrawable.setEllipsizeByGradient(true);

            progress.setCallback(BotButtons.this);
            ripple.setCallback(BotButtons.this);

            flicker.frameInside = true;
            flicker.repeatProgress = 2f;
        }
    }

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint separatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public final AnimatedFloat height = new AnimatedFloat(BotButtons.this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    public final AnimatedColor background = new AnimatedColor(BotButtons.this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

    public ButtonsState state = new ButtonsState();
    public final Button[] buttons = new Button[2];

    public BotButtons(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        setWillNotDraw(false);

        separatorPaint.setColor(Theme.multAlpha(Color.BLACK, .1f));
        backgroundPaint.setColor(state.backgroundColor = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));

        buttons[0] = new Button();
        buttons[1] = new Button();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        final float height = this.height.set(getTotalHeight());

        final float t = getHeight() - height;
        canvas.drawRect(0, t - 1, getWidth(), t, separatorPaint);
        backgroundPaint.setColor(background.set(state.backgroundColor));
        canvas.drawRect(0, t, getWidth(), getHeight(), backgroundPaint);

        final String secondary_pos = state.secondary.position;
        boolean r = buttons[1].alpha.get() < buttons[0].alpha.get();
        for (int i = (r ? 1 : 0); r ? i >= 0 : i <= 1; i += (r ? -1 : +1)) {
            final Button button = buttons[i];
            final ButtonState state = i == 0 ? this.state.main : this.state.secondary;

            final float alpha = button.alpha.set(state.visible);
            final float bx = !state.visible ? button.x.get() : button.x.set(
                this.state.secondary.visible && this.state.main.visible ? (
                    "left".equalsIgnoreCase(secondary_pos) ? (i == 0 ? 1 : 0) :
                    "right".equalsIgnoreCase(secondary_pos) ? (i == 0 ? 0 : 1) :
                    0
                ) : 0
            );
            final float by = !state.visible ? button.y.get() : button.y.set(
                this.state.secondary.visible && this.state.main.visible ? (
                    "top".equalsIgnoreCase(secondary_pos) ? (i == 0 ? 1 : 0) :
                    "bottom".equalsIgnoreCase(secondary_pos) ? (i == 0 ? 0 : 1) :
                    0
                ) : 0
            );
            final float bw = !state.visible ? button.w.get() : button.w.set(
                this.state.secondary.visible && this.state.main.visible && (
                    "left".equalsIgnoreCase(secondary_pos) ||
                    "right".equalsIgnoreCase(secondary_pos)
                ) ? 0 : 1
            );

            final float w = lerp((getWidth() - dp(16 + 10)) / 2f, getWidth() - dp(16), bw);
            final float h = dp(44);
            final float cx = lerp(dp(8), dp(8 + 10) + (getWidth() - dp(16 + 10)) / 2f, bx) + w / 2f;
            final float cy = lerp(dp(7), dp(7 + 44 + 7), by) + h / 2f;
            button.bounds.set(cx - w / 2f, t + cy - h / 2f, cx + w / 2f, t + cy + h / 2f);

            final float progress = button.progressAlpha.set(state.progressVisible);
            final float flicker = button.flickerAlpha.set(state.shineEffect);

            canvas.save();
            final float s = button.bounce.getScale(0.02f) * lerp(.7f, 1f, alpha);
            canvas.scale(s, s, cx, t + cy);

            button.backgroundPaint.setColor(Theme.multAlpha(button.backgroundColor.set(state.color), alpha));
            canvas.drawRoundRect(button.bounds, dp(9), dp(9), button.backgroundPaint);

            if (progress < 1) {
                canvas.save();
                final float s2 = lerp(.75f, 1f, 1f - progress);
                canvas.scale(s2, s2, cx, t + cy);
                canvas.translate(0, dp(-10) * progress);
                button.textDrawable.setTextColor(Theme.multAlpha(button.textColor.set(state.textColor), alpha * (1f - progress)));
                button.textDrawable.setBounds(button.bounds);
                button.textDrawable.draw(canvas);
                canvas.restore();
            }
            if (progress > 0) {
                canvas.save();
                final float s2 = lerp(.75f, 1f, progress);
                canvas.scale(s2, s2, cx, t + cy);
                canvas.translate(0, dp(10) * (1f - progress));
                button.progress.setColor(Theme.multAlpha(button.textColor.set(state.textColor), alpha * progress));
                button.progress.setBounds((int) button.bounds.left, (int) button.bounds.top, (int) button.bounds.right, (int) button.bounds.bottom);
                button.progress.draw(canvas);
                canvas.restore();
            }
            if (flicker > 0) {
                button.flicker.setColors(Theme.multAlpha(button.textColor.set(state.textColor), alpha * flicker));
                button.flicker.draw(canvas, button.bounds, dp(8), this);
            }

            if (button.rippleColor != Theme.multAlpha(state.textColor, .15f)) {
                Theme.setSelectorDrawableColor(button.ripple, button.rippleColor = Theme.multAlpha(state.textColor, .15f), true);
            }
            button.ripple.setBounds((int) button.bounds.left, (int) button.bounds.top, (int) button.bounds.right, (int) button.bounds.bottom);
            button.ripple.draw(canvas);

            canvas.restore();
        }
    }

    public void setMainState(ButtonState newState, boolean animated) {
        final int wasHeight = getTotalHeight();
        this.state.main = newState;
        buttons[0].textDrawable.cancelAnimation();
        buttons[0].textDrawable.setText(newState.text, animated);
        invalidate();
        if (wasHeight != getTotalHeight() && whenResized != null) {
            if (wasHeight < getTotalHeight()) {
                AndroidUtilities.runOnUIThread(whenResized, 200);
            } else {
                whenResized.run();
            }
        }
    }

    public void setSecondaryState(ButtonState newState, boolean animated) {
        final int wasHeight = getTotalHeight();
        this.state.secondary = newState;
        buttons[1].textDrawable.cancelAnimation();
        buttons[1].textDrawable.setText(newState.text, animated);
        invalidate();
        if (wasHeight != getTotalHeight() && whenResized != null) {
            if (wasHeight < getTotalHeight()) {
                AndroidUtilities.runOnUIThread(whenResized, 200);
            } else {
                whenResized.run();
            }
        }
    }

    public void setState(ButtonsState newState, boolean animated) {
        final int wasHeight = getTotalHeight();
        this.state = newState;
        buttons[0].textDrawable.cancelAnimation();
        buttons[0].textDrawable.setText(newState.main.text, animated);
        buttons[1].textDrawable.cancelAnimation();
        buttons[1].textDrawable.setText(newState.secondary.text, animated);
        invalidate();
        if (wasHeight != getTotalHeight() && whenResized != null) {
            if (wasHeight < getTotalHeight()) {
                AndroidUtilities.runOnUIThread(whenResized, 200);
            } else {
                whenResized.run();
            }
        }
        setBackgroundColor(newState.backgroundColor, animated);
    }

    public void setBackgroundColor(int color, boolean animated) {
        backgroundPaint.setColor(state.backgroundColor = color);
        if (!animated) {
            background.set(color, true);
        }
    }

    public int getTotalHeight() {
        int rowsCount = 0;
        if (state.main.visible || state.secondary.visible) {
            rowsCount++;
        }
        if (state.main.visible && state.secondary.visible && ("top".equalsIgnoreCase(state.secondary.position) || "bottom".equalsIgnoreCase(state.secondary.position))) {
            rowsCount++;
        }
        if (rowsCount == 0) {
            return 0;
        } else if (rowsCount == 1) {
            return dp(7 + 44 + 7);
        } else {
            return dp(7 + 44 + 7 + 44 + 7);
        }
    }

    public float getAnimatedTotalHeight() {
        return this.height.get();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dp(7 + 44 + 7 + 44 + 7) + 1, MeasureSpec.EXACTLY)
        );
    }

    private Button getHitButton(float x, float y) {
        for (int i = 0; i < buttons.length; ++i) {
            final ButtonState state = i == 0 ? this.state.main : this.state.secondary;
            if (buttons[i].bounds.contains(x, y) && state.visible && state.active) {
                return buttons[i];
            }
        }
        return null;
    }

    private Button pressedButton;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            pressedButton = getHitButton(event.getX(), event.getY());
            if (pressedButton != null) {
                pressedButton.bounce.setPressed(true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    pressedButton.ripple.setHotspot(event.getX(), event.getY());
                }
                pressedButton.ripple.setState(new int[] { android.R.attr.state_pressed, android.R.attr.state_enabled });
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (pressedButton != null) {
                if (event.getAction() == MotionEvent.ACTION_UP && getHitButton(event.getX(), event.getY()) == pressedButton) {
                    if (whenClicked != null) {
                        whenClicked.run(pressedButton == buttons[0]);
                    }
                }
                pressedButton.bounce.setPressed(false);
                pressedButton.ripple.setState(new int[0]);
                pressedButton = null;
            }
        }
        return pressedButton != null;
    }

    private Utilities.Callback<Boolean> whenClicked;
    public void setOnButtonClickListener(Utilities.Callback<Boolean> whenClicked) {
        this.whenClicked = whenClicked;
    }

    private Runnable whenResized;
    public void setOnResizeListener(Runnable whenResized) {
        this.whenResized = whenResized;
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return (
            buttons[0].ripple == who || buttons[0].progress == who ||
            buttons[1].ripple == who || buttons[1].progress == who ||
            super.verifyDrawable(who)
        );
    }
}
