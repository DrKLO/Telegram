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
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.GenericProvider;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CheckBoxBase;
import org.telegram.ui.Components.LayoutHelper;

public class PremiumGiftTierCell extends ViewGroup {
    private CheckBox2 checkBox;
    private TextView titleView;
    private TextView priceTotalView;
    private TextView pricePerMonthView;

    private int leftPaddingToTextDp = 24;

    protected GiftPremiumBottomSheet.GiftTier tier;
    protected TextView discountView;

    private int colorKey1 = Theme.key_windowBackgroundWhite;
    private int colorKey2 = Theme.key_windowBackgroundGray;
    private int gradientWidth;
    private LinearGradient gradient;
    private Paint paint = new Paint();
    private PremiumGiftTierCell globalGradientView;
    private int color0;
    private int color1;
    private Matrix matrix = new Matrix();
    private long lastUpdateTime;
    private int totalTranslation;
    private float parentXOffset;
    private int parentWidth, parentHeight;

    private boolean isDrawingGradient;

    public PremiumGiftTierCell(@NonNull Context context) {
        super(context);

        checkBox = new CheckBox2(context, 24);
        checkBox.setDrawBackgroundAsArc(10);
        checkBox.setColor(Theme.key_radioBackground, Theme.key_radioBackground, Theme.key_checkboxCheck);
        addView(checkBox);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 0, 8, 0, 0));

        discountView = new TextView(context);
        discountView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        discountView.setTextColor(Color.WHITE);
        discountView.setPadding(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(3), 0);
        discountView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        addView(discountView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM, 0, 0, 0, 8));

        pricePerMonthView = new TextView(context);
        pricePerMonthView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        pricePerMonthView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        addView(pricePerMonthView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM, 0, 0, 0, 8));

        priceTotalView = new TextView(context);
        priceTotalView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        priceTotalView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        addView(priceTotalView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END));

        setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        setClipToPadding(false);
        setWillNotDraw(false);
    }

    public void setParentXOffset(float parentXOffset) {
        this.parentXOffset = parentXOffset;
    }

    public void setGlobalGradientView(PremiumGiftTierCell globalGradientView) {
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
        AndroidUtilities.rectTmp2.set(AndroidUtilities.dp(8) + getPaddingLeft(), (int)((getMeasuredHeight() - checkBox.getMeasuredHeight()) / 2f), 0, 0);
        checkRtlAndLayout(checkBox);

        AndroidUtilities.rectTmp2.set(getMeasuredWidth() - priceTotalView.getMeasuredWidth() - AndroidUtilities.dp(16) - getPaddingRight(), (int) ((getMeasuredHeight() - priceTotalView.getMeasuredHeight()) / 2f), 0, 0);
        checkRtlAndLayout(priceTotalView);

        AndroidUtilities.rectTmp2.set(AndroidUtilities.dp(8 + leftPaddingToTextDp) + checkBox.getMeasuredWidth() + getPaddingLeft(), getPaddingTop(), 0, 0);
        checkRtlAndLayout(titleView);

        if (discountView.getVisibility() == VISIBLE) {
            AndroidUtilities.rectTmp2.set(AndroidUtilities.dp(8 + leftPaddingToTextDp) + checkBox.getMeasuredWidth() + getPaddingLeft(), getMeasuredHeight() - discountView.getMeasuredHeight() - getPaddingBottom(), 0, 0);
            checkRtlAndLayout(discountView);
        }

        AndroidUtilities.rectTmp2.set(AndroidUtilities.dp(8 + leftPaddingToTextDp + (discountView.getVisibility() == VISIBLE ? 6 : 0)) + checkBox.getMeasuredWidth() + discountView.getMeasuredWidth() + getPaddingLeft(), getMeasuredHeight() - pricePerMonthView.getMeasuredHeight() - getPaddingBottom(), 0, 0);
        checkRtlAndLayout(pricePerMonthView);
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

            AndroidUtilities.rectTmp.set(priceTotalView.getLeft(), priceTotalView.getTop() + AndroidUtilities.dp(4), priceTotalView.getRight(), priceTotalView.getBottom() - AndroidUtilities.dp(4));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(8), AndroidUtilities.dp(8), paint);

            AndroidUtilities.rectTmp.set(pricePerMonthView.getLeft(), AndroidUtilities.dp(42), pricePerMonthView.getRight(), AndroidUtilities.dp(54));
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
        int width = MeasureSpec.getSize(widthMeasureSpec), height = AndroidUtilities.dp(68);
        int checkboxSpec = MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(28), MeasureSpec.EXACTLY);
        checkBox.measure(checkboxSpec, checkboxSpec);

        priceTotalView.measure(MeasureSpec.makeMeasureSpec(width - checkBox.getMeasuredWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        titleView.measure(MeasureSpec.makeMeasureSpec(width - checkBox.getMeasuredWidth() - priceTotalView.getMeasuredWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        if (discountView.getVisibility() == VISIBLE) {
            discountView.measure(MeasureSpec.makeMeasureSpec(width - checkBox.getMeasuredWidth() - priceTotalView.getMeasuredWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        } else {
            discountView.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY));
        }
        pricePerMonthView.measure(MeasureSpec.makeMeasureSpec(width - checkBox.getMeasuredWidth() - priceTotalView.getMeasuredWidth() - discountView.getMeasuredWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));

        setMeasuredDimension(width, height);
    }

    public void setChecked(boolean checked, boolean animate) {
        checkBox.setChecked(checked, animate);
    }

    @SuppressLint("SetTextI18n")
    public void bind(GiftPremiumBottomSheet.GiftTier tier) {
        this.tier = tier;

        titleView.setText(LocaleController.formatPluralString("Months", tier.getMonths()));

        isDrawingGradient = !BuildVars.useInvoiceBilling();// && (!BillingController.getInstance().isReady() || tier.getGooglePlayProductDetails() == null);
        if (!isDrawingGradient) {
            if (tier.getDiscount() <= 0) {
                discountView.setVisibility(GONE);
            } else {
                discountView.setText(LocaleController.formatString(R.string.GiftPremiumOptionDiscount, tier.getDiscount()));
                discountView.setVisibility(VISIBLE);
            }
            pricePerMonthView.setText(LocaleController.formatString(R.string.PricePerMonth, tier.getFormattedPricePerMonth()));
            priceTotalView.setText(tier.getFormattedPrice());
        } else {
            discountView.setText(LocaleController.formatString(R.string.GiftPremiumOptionDiscount, 10));
            discountView.setVisibility(VISIBLE);
            pricePerMonthView.setText(LocaleController.formatString(R.string.PricePerMonth, 100));
            priceTotalView.setText("USD00,00");
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
