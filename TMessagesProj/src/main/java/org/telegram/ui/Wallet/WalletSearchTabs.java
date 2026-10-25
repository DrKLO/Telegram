package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Editable;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

/** Compact pager tabs with an expanding collectible search island. */
class WalletSearchTabs extends FrameLayout {
    private final View tabs;
    private final EditTextBoldCursor field;
    private final View button;
    private final Theme.ResourcesProvider resourcesProvider;
    private final Utilities.Callback<Boolean> onOpen;
    private final Utilities.Callback<Float> onProgress;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private ValueAnimator animator;
    private float progress;
    private float buttonVisibility = 1;
    private boolean open;
    private boolean keyboardDismissed;

    WalletSearchTabs(Context context, View tabs, Theme.ResourcesProvider resourcesProvider,
                     Utilities.Callback<Boolean> onOpen, Utilities.Callback<String> onQuery,
                     Utilities.Callback<Float> onProgress) {
        super(context);
        this.tabs = tabs;
        this.resourcesProvider = resourcesProvider;
        this.onOpen = onOpen;
        this.onProgress = onProgress;
        setWillNotDraw(false);
        addView(tabs, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.TOP | Gravity.LEFT));
        field = new EditTextBoldCursor(context);
        field.setSingleLine(true);
        field.setTextSize(15);
        field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider));
        field.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        field.setHint(LocaleController.getString(R.string.WalletSearchGifts));
        field.setBackground(null);
        field.setPadding(0, 0, 0, 0);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.setVisibility(INVISIBLE);
        field.setEnabled(false);
        field.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            AndroidUtilities.hideKeyboard(field);
            return true;
        });
        field.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { onQuery.run(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });
        addView(field, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.TOP, 40, 0, 44, 0));
        button = new View(context);
        button.setContentDescription(LocaleController.getString(R.string.Search));
        button.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 1));
        button.setOnClickListener(v -> setOpen(!open));
        addView(button, LayoutHelper.createFrame(40, 40, Gravity.TOP | Gravity.RIGHT));
    }

    boolean isOpen() { return open; }

    void setButtonVisibility(float value) {
        if (buttonVisibility == value) return;
        buttonVisibility = value;
        button.setVisibility(value > 0 ? VISIBLE : GONE);
        invalidate();
    }

    void dismissKeyboard() {
        if (!open) return;
        keyboardDismissed = true;
        AndroidUtilities.hideKeyboard(field);
        field.clearFocus();
    }

    void setOpen(boolean value) {
        if (open == value) return;
        open = value;
        keyboardDismissed = false;
        if (animator != null) {
            ValueAnimator previous = animator;
            animator = null;
            previous.cancel();
        }
        field.setEnabled(value);
        field.setVisibility(VISIBLE);
        button.setContentDescription(LocaleController.getString(value ? R.string.Close : R.string.Search));
        if (!value) {
            AndroidUtilities.hideKeyboard(field);
            field.clearFocus();
            field.setText("");
        }
        onOpen.run(value);
        animator = ValueAnimator.ofFloat(progress, value ? 1 : 0);
        animator.setDuration(value ? 380 : 300);
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        // The field overlays the fading tab labels during the transition.
        tabs.setVisibility(VISIBLE);
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            onProgress.run(progress);
            tabs.setAlpha(1 - smooth(0.05f, 0.45f, progress));
            tabs.setScaleX(1 - 0.06f * progress);
            field.setAlpha(smooth(0.3f, 0.9f, progress));
            field.setTranslationX((1 - progress) * Math.max(0, getWidth() - dp(80)));
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animator != animation) return;
                animator = null;
                tabs.setVisibility(open ? INVISIBLE : VISIBLE);
                field.setVisibility(open ? VISIBLE : INVISIBLE);
                if (open && !keyboardDismissed) {
                    field.requestFocus();
                    AndroidUtilities.showKeyboard(field);
                }
            }
        });
        animator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        textPaint.setTextSize(dp(14));
        textPaint.setTypeface(AndroidUtilities.bold());
        int width = dp(14 + 48);
        width += (int) Math.ceil(textPaint.measureText(LocaleController.getString(R.string.WalletTransactions)));
        width += (int) Math.ceil(textPaint.measureText(LocaleController.getString(R.string.WalletCollectibles)));
        int full = MeasureSpec.getSize(widthMeasureSpec);
        width = Math.min(width, Math.max(0, full - dp(48)));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) tabs.getLayoutParams();
        params.width = width;
        params.leftMargin = Math.max(0, Math.min((full - width) / 2, full - dp(48) - width));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float visibility = Math.max(buttonVisibility, progress);
        if (visibility <= 0) return;
        if (visibility < 1) {
            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), Math.round(255 * visibility));
            final float scale = 0.8f + 0.2f * visibility, h = dp(40);
            canvas.scale(scale, scale, getWidth() - h / 2, h / 2);
        }
        drawButton(canvas);
        if (visibility < 1) canvas.restore();
    }

    private void drawButton(Canvas canvas) {
        float h = dp(40), cx = getWidth() - h / 2, cy = h / 2;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        rect.set((getWidth() - h) * (1 - progress), 0, getWidth(), h);
        canvas.drawRoundRect(rect, h / 2, h / 2, paint);
        paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.9f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        float m = smooth(0.08f, 0.85f, progress);
        float radius = dp(6) * (1 - m);
        if (radius > 0.3f) canvas.drawCircle(cx - dp(1.5f) * (1 - m), cy - dp(1.5f) * (1 - m), radius, paint);
        float start = dp(2.6f) * (1 - m) - dp(5) * m;
        float end = dp(6.5f) * (1 - m) + dp(5) * m;
        canvas.drawLine(cx + start, cy + start, cx + end, cy + end, paint);
        float cross = dp(5) * m;
        // A zero-length stroke with round caps renders as a dot in the closed icon.
        if (cross > 0.3f) {
            canvas.drawLine(cx + cross, cy - cross, cx - cross, cy + cross, paint);
        }
        paint.setAlpha(Math.round(255 * smooth(0.3f, 0.9f, progress)));
        float x = dp(22) + (1 - progress) * Math.max(0, getWidth() - dp(80));
        canvas.drawCircle(x - dp(1), cy - dp(1), dp(5), paint);
        canvas.drawLine(x + dp(3), cy + dp(3), x + dp(7), cy + dp(7), paint);
        paint.setAlpha(255);
    }

    private static float smooth(float start, float end, float value) {
        float t = Math.max(0, Math.min(1, (value - start) / (end - start)));
        return t * t * (3 - 2 * t);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            ValueAnimator previous = animator;
            animator = null;
            previous.cancel();
        }
        progress = open ? 1 : 0;
        onProgress.run(progress);
        tabs.setAlpha(open ? 0 : 1);
        tabs.setScaleX(open ? 0.94f : 1);
        tabs.setVisibility(open ? INVISIBLE : VISIBLE);
        field.setAlpha(open ? 1 : 0);
        field.setTranslationX(0);
        field.setVisibility(open ? VISIBLE : INVISIBLE);
        super.onDetachedFromWindow();
    }
}
