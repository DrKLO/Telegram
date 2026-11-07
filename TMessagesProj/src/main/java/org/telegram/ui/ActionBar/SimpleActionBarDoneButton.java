package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.utils.settings.SharedSettings;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

/**
 * The confirm button of {@link SimpleActionBar}, placed right of the menu. Its look comes from the
 * experimental settings: {@link Style} picks the color and {@link Shape} picks a check or a text.
 * A glass button appears only while it is active; an accent button stays and dims when inactive.
 */
public class SimpleActionBarDoneButton extends View implements FactorAnimator.Target {
    public enum Style {
        /** White glass, shown only while active. */
        GLASS,
        /** Filled accent color, always shown; an inactive button mixes the accent with the background. */
        ACCENT
    }

    public enum Shape {
        /** A round button with a check, of the back button size. */
        ICON,
        /** A pill with the action text: "Save", "Create", "Apply". */
        TEXT
    }

    private static final int ANIMATOR_ACTIVE = 0;
    private static final int ANIMATOR_LOADING = 1;

    private final SimpleActionBar parentBar;
    private final int id;
    private final Style style;
    private final Shape shape;

    private final Drawable checkDrawable;
    private final CircularProgressDrawable progressDrawable;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private CharSequence text;
    private float textWidth;

    private final BoolAnimator activeAnimator = new BoolAnimator(ANIMATOR_ACTIVE, this, CubicBezierInterpolator.EASE_OUT_QUINT, SimpleActionBar.DURATION, true);
    private final BoolAnimator loadingAnimator = new BoolAnimator(ANIMATOR_LOADING, this, CubicBezierInterpolator.EASE_OUT_QUINT, 250);

    private int accentColor, inactiveColor, contentColor, glassContentColor;

    SimpleActionBarDoneButton(Context context, SimpleActionBar bar, int id, CharSequence text) {
        super(context);
        this.parentBar = bar;
        this.id = id;
        this.style = SharedSettings.headerDoneButtonStyle.get();
        this.shape = SharedSettings.headerDoneButtonShape.get();
        this.text = text;

        checkDrawable = ContextCompat.getDrawable(context, R.drawable.ic_ab_done).mutate();
        progressDrawable = new CircularProgressDrawable(dp(18), dp(2), 0xFFFFFFFF);
        progressDrawable.setCallback(this);
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextSize(dp(15));
        updateColors();
        updateTextWidth();

        setContentDescription(text);
        setOnClickListener(v -> onClick());
    }

    public Style getStyle() {
        return style;
    }

    public Shape getShape() {
        return shape;
    }

    public int getButtonId() {
        return id;
    }

    /* State */

    /**
     * Whether the action can be done now (there are changes to save). A glass button appears and
     * disappears, an accent button changes its color.
     */
    public void setActive(boolean active, boolean animated) {
        if (activeAnimator.getValue() == active && (animated || !activeAnimator.isAnimating())) {
            return;
        }
        activeAnimator.setValue(active, animated && parentBar.isAnimationsAllowed());
        onFactorChanged(ANIMATOR_ACTIVE, activeAnimator.getFloatValue(), 0, null);
        parentBar.requestLayout();
    }

    public boolean isActive() {
        return activeAnimator.getValue();
    }

    /** Replaces the check or the text with a spinner while the action is being done. */
    public void setLoading(boolean loading, boolean animated) {
        loadingAnimator.setValue(loading, animated && parentBar.isAnimationsAllowed());
        invalidate();
    }

    public boolean isLoading() {
        return loadingAnimator.getValue();
    }

    public void setText(CharSequence text) {
        if (TextUtils.equals(this.text, text)) {
            return;
        }
        this.text = text;
        setContentDescription(text);
        updateTextWidth();
        requestLayout();
        parentBar.requestLayout();
    }

    private void onClick() {
        if (loadingAnimator.getValue()) {
            return;
        }
        if (activeAnimator.getValue()) {
            parentBar.onItemClick(id);
        } else if (style == Style.ACCENT) {
            AndroidUtilities.shakeViewSpring(this, 4);
        }
    }

    /** How much the button is shown: an accent button is always there, a glass one only while active. */
    float getVisibleFactor() {
        return style == Style.ACCENT ? 1f : activeAnimator.getFloatValue();
    }

    float getTargetVisibleFactor() {
        return style == Style.ACCENT || activeAnimator.getValue() ? 1f : 0f;
    }

    boolean drawsGlass() {
        return style == Style.GLASS;
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ACTIVE) {
            parentBar.onDoneButtonFactorChanged();
        }
        invalidate();
    }

    /* Colors and size */

    void updateColors() {
        final Theme.ResourcesProvider resourcesProvider = parentBar.resourcesProvider;
        accentColor = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        inactiveColor = ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider), accentColor, 0.5f);
        contentColor = Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider);
        glassContentColor = parentBar.getItemsColor(false);
        final int color = style == Style.ACCENT ? contentColor : glassContentColor;
        checkDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        progressDrawable.setColor(color);
        textPaint.setColor(color);
        setBackground(Theme.createRadSelectorDrawable(style == Style.ACCENT ? Theme.multAlpha(contentColor, 0.2f) : parentBar.getItemsBackgroundColor(false), dp(23), dp(23)));
        invalidate();
    }

    private void updateTextWidth() {
        textWidth = text != null ? textPaint.measureText(text, 0, text.length()) : 0;
    }

    int getButtonWidth() {
        if (shape == Shape.ICON || TextUtils.isEmpty(text)) {
            return dp(46);
        }
        return Math.max(dp(46), (int) Math.ceil(textWidth) + dp(32));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getButtonWidth(), dp(46));
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == progressDrawable || super.verifyDrawable(who);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        final float w = getWidth(), h = getHeight();
        if (style == Style.ACCENT) {
            fillPaint.setColor(ColorUtils.blendARGB(inactiveColor, accentColor, activeAnimator.getFloatValue()));
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, h / 2f, h / 2f, fillPaint);
        }

        final float loading = loadingAnimator.getFloatValue();
        if (loading < 1f) {
            final float scale = lerp(1f, 0.6f, loading);
            canvas.save();
            canvas.scale(scale, scale, w / 2f, h / 2f);
            final int alpha = (int) (255 * (1f - loading));
            if (shape == Shape.ICON || TextUtils.isEmpty(text)) {
                final int iw = checkDrawable.getIntrinsicWidth(), ih = checkDrawable.getIntrinsicHeight();
                checkDrawable.setBounds((int) ((w - iw) / 2f), (int) ((h - ih) / 2f), (int) ((w + iw) / 2f), (int) ((h + ih) / 2f));
                checkDrawable.setAlpha(alpha);
                checkDrawable.draw(canvas);
            } else {
                final int wasAlpha = textPaint.getAlpha();
                textPaint.setAlpha((int) (wasAlpha * (1f - loading)));
                final float baseline = (h - textPaint.descent() - textPaint.ascent()) / 2f;
                canvas.drawText(text, 0, text.length(), (w - textWidth) / 2f, baseline, textPaint);
                textPaint.setAlpha(wasAlpha);
            }
            canvas.restore();
        }
        if (loading > 0f) {
            final float scale = lerp(0.6f, 1f, loading);
            canvas.save();
            canvas.scale(scale, scale, w / 2f, h / 2f);
            progressDrawable.setBounds(0, 0, (int) w, (int) h);
            progressDrawable.setAlpha((int) (255 * loading));
            progressDrawable.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    public void setTranslationX(float translationX) {
        super.setTranslationX(translationX);
        parentBar.invalidate();
    }
}
