package org.telegram.ui.Components.Premium;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.GenericProvider;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CheckBoxBase;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.PremiumPreviewFragment;

public class PremiumTierCell extends ViewGroup {
    private CheckBox2 checkBox;
    private TextView titleView;
    private TextView pricePerMonthView;
    private TextView pricePerYearStrikeView;
    private TextView pricePerYearView;

    private int leftPaddingToTextDp = 12;
    private int leftPaddingToCheckboxDp = 8;

    protected PremiumPreviewFragment.SubscriptionTier tier;
    protected TextView discountView;

    private String colorKey1 = Theme.key_windowBackgroundWhite;
    private String colorKey2 = Theme.key_windowBackgroundGray;
    private int gradientWidth;
    private LinearGradient gradient;
    private Paint paint = new Paint();
    private PremiumTierCell globalGradientView;
    private int color0;
    private int color1;
    private Matrix matrix = new Matrix();
    private long lastUpdateTime;
    private int totalTranslation;
    private float parentXOffset;
    private int parentWidth, parentHeight;

    private boolean isDrawingGradient;

    private boolean hasDivider;

    public PremiumTierCell(@NonNull Context context) {
        super(context);

        checkBox = new CheckBox2(context, 24);
        checkBox.setDrawBackgroundAsArc(10);
        checkBox.setColor(Theme.key_radioBackground, Theme.key_radioBackground, Theme.key_checkboxCheck);
        addView(checkBox);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        titleView.setSingleLine();
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 0, 8, 0, 0));

        discountView = new TextView(context);
        discountView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        discountView.setTextColor(Color.WHITE);
        discountView.setPadding(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(3), 0);
        discountView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        addView(discountView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM, 0, 0, 0, 8));

        pricePerYearStrikeView = new TextView(context);
        pricePerYearStrikeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        pricePerYearStrikeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        pricePerYearStrikeView.getPaint().setStrikeThruText(true);
        pricePerYearStrikeView.setSingleLine();
        addView(pricePerYearStrikeView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM, 0, 0, 0, 8));

        pricePerYearView = new TextView(context);
        pricePerYearView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        pricePerYearView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        pricePerYearView.setSingleLine();
        addView(pricePerYearView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM, 0, 0, 0, 8));

        pricePerMonthView = new TextView(context);
        pricePerMonthView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        pricePerMonthView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        pricePerMonthView.setSingleLine();
        addView(pricePerMonthView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END));

        setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8));
        setClipToPadding(false);
        setWillNotDraw(false);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        titleView.setAlpha(enabled ? 1 : 0.6f);
        pricePerMonthView.setAlpha(enabled ? 1 : 0.6f);
        checkBox.setAlpha(enabled ? 1 : 0.6f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (hasDivider) {
            if (LocaleController.isRTL) {
                canvas.drawLine(0, getHeight() - 1, titleView.getRight(), getHeight() - 1, Theme.dividerPaint);
            } else {
                canvas.drawLine(titleView.getLeft(), getHeight() - 1, getWidth(), getHeight() - 1, Theme.dividerPaint);
            }
        }
    }

    public void setParentXOffset(float parentXOffset) {
        this.parentXOffset = parentXOffset;
    }

    public void setGlobalGradientView(PremiumTierCell globalGradientView) {
        this.globalGradientView = globalGradientView;
    }

    public void setProgressDelegate(CheckBoxBase.ProgressDelegate delegate) {
        checkBox.setProgressDelegate(delegate);
    }

    public void setCirclePaintProvider(GenericProvider<Void, Paint> circlePaintProvider) {
        checkBox.setCirclePaintProvider(circlePaintProvider);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        AndroidUtilities.rectTmp2.set(AndroidUtilities.dp(leftPaddingToCheckboxDp) + getPaddingLeft(), (int)((getMeasuredHeight() - checkBox.getMeasuredHeight()) / 2f), 0, 0);
        checkRtlAndLayout(checkBox);

        int y = (int) ((getMeasuredHeight() - pricePerMonthView.getMeasuredHeight()) / 2f);
        if (AndroidUtilities.dp(leftPaddingToCheckboxDp + leftPaddingToTextDp + 24) + checkBox.getMeasuredWidth() + (pricePerYearStrikeView.getVisibility() == VISIBLE ? pricePerYearStrikeView.getMeasuredWidth() : 0) + pricePerYearView.getMeasuredWidth() + getPaddingLeft() > getMeasuredWidth() - pricePerMonthView.getMeasuredWidth() && discountView.getVisibility() == VISIBLE) {
            y = getPaddingTop() + AndroidUtilities.dp(2);
        }
        AndroidUtilities.rectTmp2.set(getMeasuredWidth() - pricePerMonthView.getMeasuredWidth() - AndroidUtilities.dp(16) - getPaddingRight(), y, 0, 0);
        checkRtlAndLayout(pricePerMonthView);

        AndroidUtilities.rectTmp2.set(AndroidUtilities.dp(leftPaddingToCheckboxDp + leftPaddingToTextDp) + checkBox.getMeasuredWidth() + getPaddingLeft(), pricePerYearView.getVisibility() == GONE ? (int) ((getMeasuredHeight() - titleView.getMeasuredHeight()) / 2f) : getPaddingTop(), 0, 0);
        checkRtlAndLayout(titleView);

        if (discountView.getVisibility() == VISIBLE) {
            AndroidUtilities.rectTmp2.set(AndroidUtilities.dp(leftPaddingToCheckboxDp + leftPaddingToTextDp + 6) + checkBox.getMeasuredWidth() + getPaddingLeft() + titleView.getMeasuredWidth(), getPaddingTop() + AndroidUtilities.dp(2), 0, 0);
            checkRtlAndLayout(discountView);
        }

        AndroidUtilities.rectTmp2.set(AndroidUtilities.dp(leftPaddingToCheckboxDp + leftPaddingToTextDp) + checkBox.getMeasuredWidth() + getPaddingLeft(), getMeasuredHeight() - pricePerYearStrikeView.getMeasuredHeight() - getPaddingBottom(), 0, 0);
        checkRtlAndLayout(pricePerYearStrikeView);

        AndroidUtilities.rectTmp2.set(AndroidUtilities.dp(leftPaddingToCheckboxDp + leftPaddingToTextDp) + checkBox.getMeasuredWidth() + (pricePerYearStrikeView.getVisibility() == VISIBLE ? pricePerYearStrikeView.getMeasuredWidth() + AndroidUtilities.dp(6) : 0) + getPaddingLeft(), getMeasuredHeight() - pricePerYearView.getMeasuredHeight() - getPaddingBottom(), 0, 0);
        checkRtlAndLayout(pricePerYearView);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (isDrawingGradient) {
            Paint paint = this.paint;
            if (globalGradientView != null) {
                paint = globalGradientView.paint;
            }

            drawChild(canvas, checkBox, getDrawingTime());

            updateColors();
            updateGradient();

            AndroidUtilities.rectTmp.set(pricePerMonthView.getLeft(), pricePerMonthView.getTop() + AndroidUtilities.dp(4), pricePerMonthView.getRight(), pricePerMonthView.getBottom() - AndroidUtilities.dp(4));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(8), AndroidUtilities.dp(8), paint);

            AndroidUtilities.rectTmp.set(pricePerYearStrikeView.getLeft(), pricePerYearStrikeView.getTop() + AndroidUtilities.dp(3), pricePerYearStrikeView.getRight(), pricePerYearStrikeView.getBottom() - AndroidUtilities.dp(3));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(8), AndroidUtilities.dp(8), paint);

            AndroidUtilities.rectTmp.set(titleView.getLeft(), titleView.getTop() + AndroidUtilities.dp(4), titleView.getRight(), titleView.getBottom() - AndroidUtilities.dp(4));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(8), AndroidUtilities.dp(8), paint);

            invalidate();
        } else {
            super.dispatchDraw(canvas);
        }
    }

    private void checkRtlAndLayout(View v) {
        Rect rect = AndroidUtilities.rectTmp2;
        rect.right = rect.left + v.getMeasuredWidth();
        rect.bottom = rect.top + v.getMeasuredHeight();
        if (LocaleController.isRTL) {
            int right = rect.right;
            rect.right = getWidth() - rect.left;
            rect.left = getWidth() - right;
        }
        v.layout(AndroidUtilities.rectTmp2.left, AndroidUtilities.rectTmp2.top, AndroidUtilities.rectTmp2.right, AndroidUtilities.rectTmp2.bottom);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec), height = AndroidUtilities.dp(58);
        int checkboxSpec = MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(28), MeasureSpec.EXACTLY);
        checkBox.measure(checkboxSpec, checkboxSpec);

        pricePerMonthView.measure(MeasureSpec.makeMeasureSpec(width - checkBox.getMeasuredWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        titleView.measure(MeasureSpec.makeMeasureSpec(width - checkBox.getMeasuredWidth() - pricePerMonthView.getMeasuredWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        if (discountView.getVisibility() == VISIBLE) {
            discountView.measure(MeasureSpec.makeMeasureSpec(width - checkBox.getMeasuredWidth() - pricePerMonthView.getMeasuredWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        } else {
            discountView.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY));
        }
        pricePerYearStrikeView.measure(MeasureSpec.makeMeasureSpec(width - checkBox.getMeasuredWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        pricePerYearView.measure(MeasureSpec.makeMeasureSpec(width - checkBox.getMeasuredWidth() - (pricePerYearStrikeView.getVisibility() == VISIBLE ? pricePerYearStrikeView.getMeasuredWidth() : 0) - AndroidUtilities.dp(6), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));

        if (pricePerYearView.getVisibility() != VISIBLE) {
            height -= AndroidUtilities.dp(8);
        }

        setMeasuredDimension(width, height);
    }

    public PremiumPreviewFragment.SubscriptionTier getTier() {
        return tier;
    }

    public void setChecked(boolean checked, boolean animate) {
        checkBox.setChecked(checked, animate);
    }

    @SuppressLint("SetTextI18n")
    public void bind(PremiumPreviewFragment.SubscriptionTier tier, boolean hasDivider) {
        this.tier = tier;
        this.hasDivider = hasDivider;

        switch (tier.getMonths()) {
            default:
                titleView.setText(LocaleController.formatPluralString("Months", tier.getMonths()));
                break;
            case 12:
                titleView.setText(LocaleController.getString(R.string.PremiumTierAnnual));
                break;
            case 6:
                titleView.setText(LocaleController.getString(R.string.PremiumTierSemiannual));
                break;
            case 1:
                titleView.setText(LocaleController.getString(R.string.PremiumTierMonthly));
                break;
        }

        isDrawingGradient = !BuildVars.useInvoiceBilling() && (!BillingController.getInstance().isReady() || tier.getOfferDetails() == null);
        if (!isDrawingGradient) {
            if (tier.getDiscount() <= 0) {
                discountView.setVisibility(GONE);
                pricePerYearStrikeView.setVisibility(GONE);
                pricePerYearView.setVisibility(GONE);
            } else {
                discountView.setText(LocaleController.formatString(R.string.GiftPremiumOptionDiscount, tier.getDiscount()));
                discountView.setVisibility(VISIBLE);
                pricePerYearStrikeView.setVisibility(VISIBLE);
                pricePerYearView.setVisibility(VISIBLE);
            }
            pricePerYearStrikeView.setText(tier.getFormattedPricePerYearRegular());
            pricePerYearView.setText(LocaleController.formatString(R.string.PricePerYear, tier.getFormattedPricePerYear()));
            pricePerMonthView.setText(LocaleController.formatString(R.string.PricePerMonthMe, tier.getFormattedPricePerMonth()));

            if (tier.subscriptionOption.current) {
                pricePerYearView.setVisibility(VISIBLE);
                pricePerYearView.setText(LocaleController.getString(R.string.YourCurrentPlan));
            }
        } else {
            discountView.setText(LocaleController.formatString(R.string.GiftPremiumOptionDiscount, 10));
            discountView.setVisibility(VISIBLE);
            pricePerYearStrikeView.setVisibility(VISIBLE);
            pricePerYearView.setVisibility(VISIBLE);
            pricePerYearStrikeView.setText("USD00.00");
            pricePerYearView.setText(LocaleController.formatString(R.string.PricePerYear, 1000));
            pricePerMonthView.setText(LocaleController.formatString(R.string.PricePerMonthMe, 100));
        }

        requestLayout();
    }

    public void updateGradient() {
        if (globalGradientView != null) {
            globalGradientView.updateGradient();
            return;
        }
        long newUpdateTime = SystemClock.elapsedRealtime();
        long dt = Math.abs(lastUpdateTime - newUpdateTime);
        if (dt > 17) {
            dt = 16;
        }
        if (dt < 4) {
            dt = 0;
        }
        int width = parentWidth;
        if (width == 0) {
            width = getMeasuredWidth();
        }
        lastUpdateTime = newUpdateTime;
        totalTranslation += dt * width / 400.0f;
        if (totalTranslation >= width * 4) {
            totalTranslation = -gradientWidth * 2;
        }
        matrix.setTranslate(totalTranslation + parentXOffset, 0);
        if (gradient != null) {
            gradient.setLocalMatrix(matrix);
        }
    }

    public void setParentSize(int parentWidth, int parentHeight, float parentXOffset) {
        this.parentWidth = parentWidth;
        this.parentHeight = parentHeight;
        this.parentXOffset = parentXOffset;
    }

    public void updateColors() {
        if (globalGradientView != null) {
            globalGradientView.updateColors();
            return;
        }
        int color0 = Theme.getColor(colorKey1);
        int color1 = Theme.getColor(colorKey2);
        if (this.color1 != color1 || this.color0 != color0) {
            this.color0 = color0;
            this.color1 = color1;
            gradient = new LinearGradient(0, 0, gradientWidth = AndroidUtilities.dp(200), 0, new int[]{color1, color0, color0, color1}, new float[]{0.0f, 0.4f, 0.6f, 1f}, Shader.TileMode.CLAMP);
            paint.setShader(gradient);
        }
    }
}
