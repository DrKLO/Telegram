package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.LayoutHelper;

/** One text layout and icon slot throughout sending and settling; no end-frame view swap. */
class WalletTransactionAmountView extends FrameLayout {
    final TextView amount;
    private final ImageView flatDiamond;
    private WalletDiamondView diamond;
    private float progress;
    private float press;
    private boolean flying;

    WalletTransactionAmountView(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        amount = new TextView(context);
        amount.setSingleLine(true);
        amount.setIncludeFontPadding(false);
        amount.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        amount.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        amount.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        addView(amount);
        flatDiamond = new ImageView(context);
        flatDiamond.setScaleType(ImageView.ScaleType.FIT_CENTER);
        flatDiamond.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(flatDiamond, LayoutHelper.createFrame(16, 16));
    }

    void setAmount(CharSequence text, Drawable icon) {
        amount.setText(text);
        // TransactionCell also uses this TextView while binding. Its icon is always
        // rendered in the shared slot, never reintroduced as a compound drawable.
        amount.setCompoundDrawablesRelative(null, null, null, null);
        flatDiamond.setImageDrawable(icon);
    }

    void setColor(int color) { amount.setTextColor(color); }

    void setPendingProgress(float value) {
        progress = value;
        if (value > 0 && diamond == null) {
            // Settling removes this view during an animation frame. Texture GL
            // cleanup is asynchronous, so it cannot join a surface thread here.
            diamond = new WalletDiamondView(getContext(), 40, true);
            diamond.setContinuousRotation(300f);
            addView(diamond, LayoutHelper.createFrame(40, 40));
        }
        if (diamond != null && value == 0) {
            diamond.setPaused(true);
            removeView(diamond);
            diamond = null;
        }
        applyMotion();
        requestLayout();
    }

    void setLandingPress(float value) {
        press = value;
        applyMotion();
    }

    private float textScale() { return 1f + (20f / 14f - 1f) * progress; }
    // Reserve the visible gem width; its transparent 40 dp canvas may overflow.
    private float slotWidth() { return dp(16) + (dp(23) - dp(16)) * progress; }
    private float gap() { return dp(2) + (dp(7) - dp(2)) * progress; }

    private void applyMotion() {
        float centerY = dp(19) + (getHeight() * .5f - dp(19)) * progress;
        float centerX = getWidth() - slotWidth() * .5f;
        amount.setPivotX(amount.getWidth());
        amount.setPivotY(amount.getHeight() * .5f);
        amount.setScaleX(textScale());
        amount.setScaleY(textScale());
        amount.setTranslationX(getWidth() - slotWidth() - gap() - amount.getWidth());
        amount.setTranslationY(centerY - amount.getHeight() * .5f);
        flatDiamond.setTranslationX(centerX - dp(8));
        flatDiamond.setTranslationY(centerY - dp(8));
        flatDiamond.setAlpha(1f - progress);
        if (diamond != null) {
            diamond.setTranslationX(centerX - dp(20));
            diamond.setTranslationY(centerY - dp(20));
            diamond.setPivotX(dp(20));
            diamond.setPivotY(dp(20));
            diamond.setScaleX((1f + .12f * press) * (.6f + .4f * progress));
            diamond.setScaleY((1f - .18f * press) * (.6f + .4f * progress));
            diamond.setAlpha(flying ? 0f : progress);
        }
    }

    WalletDiamondView getDiamond() { return diamond; }

    void setFlying(boolean value) {
        flying = value;
        applyMotion();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int available = Math.max(0, MeasureSpec.getSize(widthSpec) - Math.round(slotWidth() + gap()));
        amount.measure(MeasureSpec.makeMeasureSpec((int) (available / textScale()), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(dp(16), MeasureSpec.EXACTLY));
        flatDiamond.measure(MeasureSpec.makeMeasureSpec(dp(16), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(16), MeasureSpec.EXACTLY));
        if (diamond != null) {
            diamond.measure(MeasureSpec.makeMeasureSpec(dp(40), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(40), MeasureSpec.EXACTLY));
        }
        int width = (int) Math.ceil(amount.getMeasuredWidth() * textScale() + gap() + slotWidth());
        setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(dp(40), heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        amount.layout(0, 0, amount.getMeasuredWidth(), amount.getMeasuredHeight());
        flatDiamond.layout(0, 0, dp(16), dp(16));
        if (diamond != null) diamond.layout(0, 0, dp(40), dp(40));
        applyMotion();
    }
}
