package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.PowerManager;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ColoredImageSpan;

public class WalletCardComponent extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private static final int CARD_NUMBER_FONT_WEIGHT = 700;
    private static final float CARD_NUMBER_LENGTH_SCALE = 1.18f;
    private static final int QR_BUTTON_RIGHT_PADDING = 45;
    private static Typeface cardNumberTypeface;
    private static Typeface cardHolderTypeface;

    private boolean diamondOnCard;
    private float balanceDrawScale = 1f;
    private final Matrix balanceDiamondTransform = new Matrix();
    private final Matrix inverseDiamondFaceTransform = new Matrix();
    private final Matrix nextDiamondTransform = new Matrix();
    private final RectF diamondDrawBounds = new RectF();
    private boolean hasBalanceDiamondTransform;
    private boolean use2D;
    private final boolean powerSaveMode;
    private float additionalTilt;
    private float gyroscopeTiltScale = 1.0f;
    private final Matrix faceProjectionMatrix = new Matrix();
    private final Matrix targetTransformMatrix = new Matrix();
    private final Matrix inverseTargetTransformMatrix = new Matrix();
    private View cardView;
    private FrameLayout frontFace;
    private Bitmap engravingBitmap;
    private OnClickListener cardClickListener;
    private final TextView cardHolderView;
    private final View balanceAnchor;
    private final LinearLayout balanceLayout;
    private final AnimatedTextView balanceView;
    private final AnimatedTextView usdBalanceView;
    private final FrameLayout.LayoutParams balanceCardLayoutParams;
    private String cardNumber;
    private int cardIconResId;
    private int qrButtonRightPadding;
    private float balanceTransitionProgress;
    private float balanceActionBarWidth;

    private int currentAccount = UserConfig.selectedAccount;
    private GramWallet wallet;

    public WalletCardComponent(Context context) {
        this(context, false, R.drawable.wallet_card_qr, QR_BUTTON_RIGHT_PADDING);
    }

    public WalletCardComponent(Context context, boolean use2D) {
        this(context, use2D, R.drawable.wallet_card_qr, QR_BUTTON_RIGHT_PADDING);
    }

    public WalletCardComponent(Context context, boolean use2D, int cardIconResId) {
        this(context, use2D, cardIconResId, QR_BUTTON_RIGHT_PADDING);
    }

    public WalletCardComponent(
            Context context,
            boolean use2D,
            int cardIconResId,
            int qrButtonRightPadding
    ) {
        super(context);
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        powerSaveMode = powerManager != null && powerManager.isPowerSaveMode();
        this.use2D = use2D || powerSaveMode;
        this.cardIconResId = cardIconResId;
        this.qrButtonRightPadding = qrButtonRightPadding;
        setClipChildren(false);
        setClipToPadding(false);
        setPadding(dp(12), dp(12), dp(12), dp(12));
        createCardImplementation();

        cardHolderView = new TextView(context);
        cardHolderView.setTypeface(getCardHolderTypeface(context));
        cardHolderView.setTextColor(Color.WHITE);
        cardHolderView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.6f);
        cardHolderView.setShadowLayer(dp(1), dp(1), dp(1), 0xFF0285D4);
        cardHolderView.setEllipsize(TextUtils.TruncateAt.END);
        cardHolderView.setMaxLines(1);
        cardHolderView.setSingleLine();
        frontFace.addView(cardHolderView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.BOTTOM,
                24, 16.6f, 64, 16.6f
        ));

        balanceAnchor = new View(context);
        frontFace.addView(balanceAnchor, LayoutHelper.createFrame(
                1, 1, Gravity.LEFT | Gravity.CENTER_VERTICAL, 24, 0, 0, 0
        ));

        balanceLayout = new LinearLayout(context);
        balanceLayout.setOrientation(LinearLayout.VERTICAL);
        balanceLayout.setClipChildren(false);
        balanceLayout.setClipToPadding(false);
        balanceCardLayoutParams = LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.CENTER_VERTICAL,
                24, 0, 24, 0
        );
        frontFace.addView(balanceLayout, balanceCardLayoutParams);

        balanceView = new AnimatedTextView(context, false, true, true, true, true) {
            @Override
            protected void onDraw(Canvas canvas) {
                // Fit the complete line, including its icon and unit, inside the
                // existing card/overlay projection without changing its geometry.
                final float textWidth = getDrawable().getCurrentWidth();
                final float qrLeft = frontFace.getWidth() * (336f - qrButtonRightPadding - 50f) / 336f;
                final float cardWidth = Math.max(0f, qrLeft - balanceCardLayoutParams.leftMargin - dp(8));
                final float cardScale = textWidth > 0 ? Math.min(1f, cardWidth / textWidth) : 1f;
                final float actionBarScale = textWidth > 0 ? Math.min(1f, balanceActionBarWidth / textWidth) : 1f;
                final float scale = cardScale + (actionBarScale - cardScale) * balanceTransitionProgress;
                balanceDrawScale = scale;
                final int save = canvas.save();
                canvas.scale(scale, scale, 0f, getHeight() / 2f);
                super.onDraw(canvas);
                canvas.restoreToCount(save);
            }
        };
        // Lay out the full balance before scaling; the normal view width would ellipsize it.
        balanceView.getDrawable().setOverrideFullWidth(dp(4096));
        balanceView.setAnimationProperties(.35f, 0, 320, 3.5f, CubicBezierInterpolator.EASE_OUT_QUINT);
        balanceView.setScaleProperty(0.6f);
        balanceView.setTypeface(AndroidUtilities.getTypeface("fonts/gram.ttf"));
        balanceView.setTextSize(dp(24));
        balanceView.setTextColor(Color.WHITE);
        balanceView.setIgnoreRTL(true);
        balanceLayout.addView(balanceView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 28));
        WalletAccessibility.readWithoutIcons(balanceView, null);

        usdBalanceView = new AnimatedTextView(context, false, true, true, true, true);
        usdBalanceView.setAnimationProperties(.35f, 0, 320, 3.5f, CubicBezierInterpolator.EASE_OUT_QUINT);
        usdBalanceView.setScaleProperty(0.6f);
        usdBalanceView.setTypeface(AndroidUtilities.getTypeface("fonts/gram.ttf"));
        usdBalanceView.setTextSize(dp(14));
        usdBalanceView.setTextColor(0xFF87EFFF);
        usdBalanceView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        usdBalanceView.setTextDirection(View.TEXT_DIRECTION_LTR);
        usdBalanceView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        usdBalanceView.setIgnoreRTL(true);
        balanceLayout.addView(usdBalanceView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 16, 0, 0, 0, 0));
    }

    /** Opt in only for the main wallet screen; other cards retain the text icon. */
    public void setDiamondOnCard(boolean enabled) {
        diamondOnCard = enabled;
        if (!enabled && cardView instanceof WalletCardView) {
            ((WalletCardView) cardView).setDiamondBounds(0, 0, 0, 0);
        }
        balanceView.invalidate();
    }

    public FrameLayout getFrontFace() {
        return frontFace;
    }

    public View getBalanceAnchor() {
        return balanceAnchor;
    }

    public LinearLayout getBalanceLayout() {
        return balanceLayout;
    }

    public AnimatedTextView getBalanceView() {
        return balanceView;
    }

    public void setBalanceTransition(float progress, float actionBarWidth) {
        if (balanceTransitionProgress == progress && balanceActionBarWidth == actionBarWidth) return;
        balanceTransitionProgress = progress;
        if (progress == 0f) hasBalanceDiamondTransform = false;
        balanceActionBarWidth = actionBarWidth;
        if (cardView instanceof WalletCardView) {
            ((WalletCardView) cardView).setDiamondAlpha(1f - progress);
        }
        balanceView.invalidate();
    }

    /** Maps the moving balance back onto the card face for the diamond crossfade. */
    public void setBalanceDiamondTransform(Matrix faceToOverlay, Matrix balanceToOverlay) {
        if (!faceToOverlay.invert(inverseDiamondFaceTransform)) return;
        nextDiamondTransform.setConcat(inverseDiamondFaceTransform, balanceToOverlay);
        if (!hasBalanceDiamondTransform || !balanceDiamondTransform.equals(nextDiamondTransform)) {
            balanceDiamondTransform.set(nextDiamondTransform);
            hasBalanceDiamondTransform = true;
            balanceView.invalidate();
        }
    }

    public AnimatedTextView getUsdBalanceView() {
        return usdBalanceView;
    }

    public TextView getCardHolderView() {
        return cardHolderView;
    }

    public void setCardHolder(CharSequence holder) {
        cardHolderView.setText(holder);
    }

    public void setCardNumber(String formattedNumber) {
        if (formattedNumber == null || formattedNumber.equals(cardNumber)) {
            return;
        }
        cardNumber = formattedNumber;
        setEngravingBitmap(createCardNumberEngraving(getContext(), formattedNumber));
    }

    /** Maps design-space face coordinates, including its canvas projection, into target. */
    public boolean getFrontFaceTransform(View target, Matrix result) {
        if (!isAttachedToWindow() || !target.isAttachedToWindow()
                || getRootView() != target.getRootView()) {
            return false;
        }
        final View projectedParent = (View) frontFace.getParent();
        if (cardView instanceof WalletCard2DView) {
            ((WalletCard2DView) cardView).getFrontFaceProjection(faceProjectionMatrix);
        } else {
            ((WalletCardView) cardView).getFrontFaceProjection(faceProjectionMatrix);
        }
        transformToRoot(projectedParent, result);
        result.preConcat(faceProjectionMatrix);
        result.preTranslate(frontFace.getLeft() - projectedParent.getScrollX(),
                frontFace.getTop() - projectedParent.getScrollY());
        result.preConcat(frontFace.getMatrix());
        transformToRoot(target, targetTransformMatrix);
        if (!targetTransformMatrix.invert(inverseTargetTransformMatrix)) return false;
        result.postConcat(inverseTargetTransformMatrix);
        return true;
    }

    private static void transformToRoot(View view, Matrix result) {
        if (view.getParent() instanceof View) {
            final View parent = (View) view.getParent();
            transformToRoot(parent, result);
            result.preTranslate(view.getLeft() - parent.getScrollX(), view.getTop() - parent.getScrollY());
            result.preConcat(view.getMatrix());
        } else {
            result.reset();
        }
    }

    public void setOnFrontContentPresented(Runnable callback) {
        if (cardView instanceof WalletCardView) {
            ((WalletCardView) cardView).setOnFrontContentPresented(callback);
        } else if (callback != null) {
            callback.run();
        }
    }

    public void attachBalanceToCard() {
        setBalanceTransition(0f, 0f);
        AndroidUtilities.removeFromParent(balanceLayout);
        balanceLayout.setScaleX(1.0f);
        balanceLayout.setScaleY(1.0f);
        balanceLayout.setRotationX(0.0f);
        balanceLayout.setRotationY(0.0f);
        balanceLayout.setTranslationX(0.0f);
        balanceLayout.setTranslationY(0.0f);
        usdBalanceView.setScaleX(1.0f);
        usdBalanceView.setScaleY(1.0f);
        frontFace.addView(balanceLayout, balanceCardLayoutParams);
    }

    public boolean isUse2D() {
        return use2D;
    }

    public void setUse2D(boolean use2D) {
        use2D |= powerSaveMode;
        if (this.use2D == use2D) {
            return;
        }

        final int childCount = frontFace.getChildCount();
        final View[] children = new View[childCount];
        final ViewGroup.LayoutParams[] layoutParams = new ViewGroup.LayoutParams[childCount];
        for (int i = 0; i < childCount; i++) {
            children[i] = frontFace.getChildAt(i);
            layoutParams[i] = children[i].getLayoutParams();
        }
        frontFace.removeAllViews();
        removeView(cardView);

        this.use2D = use2D;
        createCardImplementation();
        for (int i = 0; i < childCount; i++) {
            frontFace.addView(children[i], layoutParams[i]);
        }
    }

    public void setCardOnClickListener(OnClickListener listener) {
        cardClickListener = listener;
        cardView.setOnClickListener(listener);
    }

    // what a press on the card does, said by a screen reader
    private CharSequence cardClickAccessibilityLabel;

    public void setCardClickAccessibilityLabel(CharSequence label) {
        cardClickAccessibilityLabel = label;
        WalletAccessibility.setClickLabel(cardView, label);
    }

    public void setCardIcon(int drawableResId) {
        cardIconResId = drawableResId;
        if (cardView instanceof WalletCard2DView) {
            ((WalletCard2DView) cardView).setCardIcon(drawableResId);
        } else {
            ((WalletCardView) cardView).setCardIcon(drawableResId);
        }
    }

    public void setEngravingBitmap(Bitmap bitmap) {
        engravingBitmap = bitmap;
        if (cardView instanceof WalletCard2DView) {
            ((WalletCard2DView) cardView).setEngravingBitmap(bitmap);
        } else {
            ((WalletCardView) cardView).setEngravingBitmap(bitmap);
        }
    }

    /** Adds a vertical tilt in degrees to the live sensor and drag rotation. */
    public void setAdditionalTilt(float tilt) {
        additionalTilt = tilt;
        if (cardView instanceof WalletCard2DView) {
            ((WalletCard2DView) cardView).setAdditionalTilt(tilt);
        } else {
            ((WalletCardView) cardView).setAdditionalTilt(tilt);
        }
    }

    /** Scales sensor tilt: 0 disables it, 0.5 applies half, and 1 applies full tilt. */
    public void setUseGyroscope(float t) {
        gyroscopeTiltScale = t;
        if (cardView instanceof WalletCard2DView) {
            ((WalletCard2DView) cardView).setUseGyroscope(t);
        } else {
            ((WalletCardView) cardView).setUseGyroscope(t);
        }
    }

    public float getCardRotationX() {
        if (cardView instanceof WalletCard2DView) {
            return ((WalletCard2DView) cardView).getCardRotationX();
        }
        return ((WalletCardView) cardView).getCardRotationX();
    }

    public float getCardRotationY() {
        if (cardView instanceof WalletCard2DView) {
            return ((WalletCard2DView) cardView).getCardRotationY();
        }
        return ((WalletCardView) cardView).getCardRotationY();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        final View parent = getParent() instanceof View ? (View) getParent() : null;
        if (parent != null) {
            parent.setTranslationZ(dp(16));
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).setClipChildren(false);
                ((ViewGroup) parent).setClipToPadding(false);
            }
        }
        if (wallet != null) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.walletUpdate);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        final View parent = getParent() instanceof View ? (View) getParent() : null;
        if (parent != null) {
            parent.setTranslationZ(0.0f);
        }
        super.onDetachedFromWindow();
        if (wallet != null) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.walletUpdate);
        }
    }

    public void update(GramWallet currentWallet) {
        if (currentWallet == null) return;
        setCardHolder(UserObject.getUserName(
                UserConfig.getInstance(currentWallet.currentAccount).getCurrentUser()
        ).toUpperCase());
        if (currentWallet.getAddress() != null) {
            setCardNumber(formatAddress(currentWallet.getAddress().toUpperCase()));
        }
        if (currentWallet.isReady()) {
            setWalletBalance(currentWallet, currentWallet.getBalance());
        }
    }

    public void listen(int currentAccount, GramWallet wallet) {
        if (isAttachedToWindow() && this.wallet != null && (this.currentAccount != currentAccount || this.wallet != wallet))
            NotificationCenter.getInstance(this.currentAccount).removeObserver(this, NotificationCenter.walletUpdate);
        this.currentAccount = currentAccount;
        this.wallet = wallet;
        if (isAttachedToWindow() && this.wallet != null)
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.walletUpdate);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.walletUpdate) {
            if (args[0] == wallet) {
                update(wallet);
            }
        }
    }

    static SpannableStringBuilder formatBalance(long nanotons) {
        final DecimalFormat format = new DecimalFormat("#,##0.##", new DecimalFormatSymbols(Locale.US));
        format.setRoundingMode(RoundingMode.DOWN);
        final String gramsBalance = format.format(BigDecimal.valueOf(nanotons, 9));
        return GramWallet.formatGramsScaled(gramsBalance, 0.8f);
    }

    ColoredImageSpan createBalanceDiamondSpan() {
        return new ColoredImageSpan(R.drawable.wallet_gram_large) {
            @Override
            public void draw(Canvas canvas, CharSequence text, int start, int end,
                             float x, int top, int y, int bottom, Paint paint) {
                setAlpha(1f);
                if (diamondOnCard && cardView instanceof WalletCardView
                        && frontFace.getWidth() > 0) {
                    WalletCardView view = (WalletCardView) cardView;
                    // Start in the same local coordinates and scale as the text
                    // icon, then follow its interpolated path back in card space.
                    float width = drawable.getIntrinsicWidth() * balanceDrawScale;
                    float height = drawable.getIntrinsicHeight() * balanceDrawScale;
                    float left = x * balanceDrawScale;
                    float topOnBalance = balanceView.getHeight() / 2f - height / 2f;
                    diamondDrawBounds.set(left, topOnBalance, left + width, topOnBalance + height);
                    if (hasBalanceDiamondTransform && balanceTransitionProgress > 0f) {
                        balanceDiamondTransform.mapRect(diamondDrawBounds);
                    } else {
                        diamondDrawBounds.offset(balanceCardLayoutParams.leftMargin,
                                frontFace.getHeight() / 2f - dp(22));
                    }
                    view.setDiamondBounds(diamondDrawBounds.left / frontFace.getWidth(),
                            diamondDrawBounds.top / frontFace.getHeight(),
                            diamondDrawBounds.width() / frontFace.getWidth(),
                            diamondDrawBounds.height() / frontFace.getHeight());
                    if (view.isCardMaterialReady()) {
                        // Follow the whole balance transition in both directions.
                        float t = Math.max(0f, Math.min(1f, balanceTransitionProgress));
                        setAlpha(t);
                        if (t == 0f) return;
                    } else {
                        balanceView.postInvalidateOnAnimation();
                    }
                }
                super.draw(canvas, text, start, end, x, top, y, bottom, paint);
            }
        };
    }

    private void setWalletBalance(GramWallet wallet, long nanotons) {
        final SpannableStringBuilder text = new SpannableStringBuilder();
        final SpannableString icon = new SpannableString("GRAM");
        icon.setSpan(new ColoredImageSpan(R.drawable.wallet_gram_large), 0, icon.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append(icon).append(" ");
        text.append(formatBalance(nanotons));
        text.append(" ");
        final int labelStart = text.length();
        text.append(LocaleController.getString(R.string.GramCurrency));
        text.setSpan(new ForegroundColorSpan(0xFF6DDCFF), labelStart, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        balanceView.setText(text);

        final double grams = (double) nanotons / 1_000_000_000.0;
        final long usdCents = Math.round(grams
                * MessagesController.getInstance(wallet.currentAccount).config.tonUsdRate.get()
                * 100.0);
        usdBalanceView.setText(BillingController.getInstance().formatCurrency(
                usdCents, "USD", 2, usdCents % 100 == 0
        ));
    }

    static String formatAddress(String address) {
        final StringBuilder formatted = new StringBuilder(address.length() + address.length() / 4);
        for (int i = 0; i < address.length(); i++) {
            if (i > 0 && i % 4 == 0) formatted.append(' ');
            formatted.append(address.charAt(i));
        }
        return formatted.toString();
    }

    private void createCardImplementation() {
        if (use2D) {
            final WalletCard2DView view = new WalletCard2DView(
                    getContext(),
                    cardIconResId,
                    qrButtonRightPadding
            );
            cardView = view;
            frontFace = view.getFrontFace();
        } else {
            final WalletCardView view = new WalletCardView(
                    getContext(),
                    cardIconResId,
                    qrButtonRightPadding
            );
            cardView = view;
            frontFace = view.getFrontFace();
        }
        setAdditionalTilt(additionalTilt);
        setUseGyroscope(gyroscopeTiltScale);
        if (cardView instanceof WalletCardView) {
            ((WalletCardView) cardView).setDiamondAlpha(1f - balanceTransitionProgress);
        }
        cardView.setOnClickListener(cardClickListener);
        if (cardClickAccessibilityLabel != null) {
            WalletAccessibility.setClickLabel(cardView, cardClickAccessibilityLabel);
        }
        addView(cardView, 0, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT
        ));
        if (engravingBitmap != null) {
            setEngravingBitmap(engravingBitmap);
        }
    }

    private static Bitmap createCardNumberEngraving(Context context, String formattedAddress) {
        final int width = 1640;
        final int height = 1000;
        final float scale = width / 336.0f;
        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        canvas.scale(scale, scale);
        canvas.translate(313.0f, 102.5f);
        canvas.rotate(90.0f);
        canvas.scale(CARD_NUMBER_LENGTH_SCALE, 1.0f);

        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTypeface(getCardNumberTypeface(context));
        paint.setTextSize(9.7f);
        paint.setTextAlign(Paint.Align.CENTER);
        final Paint.FontMetrics metrics = paint.getFontMetrics();
        final float middleBaselineOffset = -(metrics.ascent + metrics.descent) * 0.5f;

        final String[] lines = formatCardAddress(formattedAddress).split("\\n");
        canvas.drawText(lines[0], 0, -5.6f + middleBaselineOffset, paint);
        if (lines.length > 1) {
            canvas.drawText(lines[1], 0, 5.6f + middleBaselineOffset, paint);
        }
        return bitmap;
    }

    static String formatCardAddress(String formattedAddress) {
        final String[] groups = formattedAddress.split(" ");
        if (groups.length < 2) {
            return formattedAddress;
        }
        final int lineBreak = groups.length / 2;
        final StringBuilder result = new StringBuilder(formattedAddress.length() + 1);
        for (int i = 0; i < groups.length; i++) {
            if (i > 0) {
                result.append(i == lineBreak ? '\n' : ' ');
            }
            result.append(groups[i]);
        }
        return result.toString();
    }

    private static Typeface getCardHolderTypeface(Context context) {
        if (cardHolderTypeface == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                cardHolderTypeface = new Typeface.Builder(
                        context.getAssets(), "fonts/rmono_var.ttf"
                ).setFontVariationSettings("'wght' 500")
                        .setWeight(500)
                        .build();
            } else {
                cardHolderTypeface = AndroidUtilities.getTypeface("fonts/rmono.ttf");
            }
        }
        return cardHolderTypeface;
    }

    private static Typeface getCardNumberTypeface(Context context) {
        if (cardNumberTypeface == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                cardNumberTypeface = new Typeface.Builder(
                        context.getAssets(), "fonts/rmono_var.ttf"
                ).setFontVariationSettings("'wght' " + CARD_NUMBER_FONT_WEIGHT)
                        .setWeight(CARD_NUMBER_FONT_WEIGHT)
                        .build();
            } else {
                cardNumberTypeface = Typeface.create(
                        AndroidUtilities.getTypeface("fonts/rmono_var.ttf"),
                        Typeface.BOLD
                );
            }
        }
        return cardNumberTypeface;
    }
}
