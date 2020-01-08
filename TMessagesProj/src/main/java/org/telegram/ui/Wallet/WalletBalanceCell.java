/*
 * This is the source code of Wallet for Android v. 1.0.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov, 2019.
 */

package org.telegram.ui.Wallet;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TonController;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.TypefaceSpan;

@SuppressWarnings("FieldCanBeLocal")
public class WalletBalanceCell extends FrameLayout {

    private SimpleTextView valueTextView;
    private TextView yourBalanceTextView;
    private FrameLayout receiveButton;
    private FrameLayout sendButton;
    private SimpleTextView receiveTextView;
    private SimpleTextView sendTextView;
    private Drawable sendDrawable;
    private Drawable receiveDrawable;
    private Typeface defaultTypeFace;
    private RLottieDrawable gemDrawable;

    public WalletBalanceCell(Context context) {
        super(context);

        valueTextView = new SimpleTextView(context);
        valueTextView.setTextColor(Theme.getColor(Theme.key_wallet_whiteText));
        valueTextView.setTextSize(41);
        valueTextView.setDrawablePadding(AndroidUtilities.dp(7));
        valueTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        valueTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 35, 0, 0));

        gemDrawable = new RLottieDrawable(R.raw.wallet_gem, "" + R.raw.wallet_gem, AndroidUtilities.dp(42), AndroidUtilities.dp(42), false, null);
        gemDrawable.setAutoRepeat(1);
        gemDrawable.setAllowDecodeSingleFrame(true);
        gemDrawable.addParentView(valueTextView);
        valueTextView.setRightDrawable(gemDrawable);
        gemDrawable.start();

        yourBalanceTextView = new TextView(context);
        yourBalanceTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        yourBalanceTextView.setTextColor(Theme.getColor(Theme.key_wallet_whiteText));
        defaultTypeFace = yourBalanceTextView.getTypeface();
        yourBalanceTextView.setText(LocaleController.getString("WalletYourBalance", R.string.WalletYourBalance));
        addView(yourBalanceTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 90, 0, 0));

        receiveDrawable = context.getResources().getDrawable(R.drawable.wallet_receive).mutate();
        receiveDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_wallet_buttonText), PorterDuff.Mode.MULTIPLY));
        sendDrawable = context.getResources().getDrawable(R.drawable.wallet_send).mutate();
        sendDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_wallet_buttonText), PorterDuff.Mode.MULTIPLY));

        for (int a = 0; a < 2; a++) {
            FrameLayout frameLayout = new FrameLayout(context);
            frameLayout.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_wallet_buttonBackground), Theme.getColor(Theme.key_wallet_buttonPressedBackground)));
            addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 42, Gravity.LEFT | Gravity.TOP, a == 0 ? 32 : 16, 168, 0, 0));
            frameLayout.setOnClickListener(v -> {
                if (v == receiveButton) {
                    onReceivePressed();
                } else {
                    onSendPressed();
                }
            });

            SimpleTextView buttonTextView = new SimpleTextView(context);
            buttonTextView.setTextColor(Theme.getColor(Theme.key_wallet_buttonText));
            buttonTextView.setTextSize(14);
            buttonTextView.setDrawablePadding(AndroidUtilities.dp(6));
            buttonTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            if (a == 0) {
                buttonTextView.setText(LocaleController.getString("WalletReceive", R.string.WalletReceive));
                buttonTextView.setLeftDrawable(receiveDrawable);
                receiveTextView = buttonTextView;
                receiveButton = frameLayout;
            } else {
                buttonTextView.setText(LocaleController.getString("WalletSend", R.string.WalletSend));
                buttonTextView.setLeftDrawable(sendDrawable);
                sendTextView = buttonTextView;
                sendButton = frameLayout;
            }
            frameLayout.addView(buttonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int buttonWidth;
        if (sendButton.getVisibility() == VISIBLE) {
            buttonWidth = (width - AndroidUtilities.dp(80)) / 2;
        } else {
            buttonWidth = width - AndroidUtilities.dp(64);
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) receiveButton.getLayoutParams();
        layoutParams.width = buttonWidth;

        layoutParams = (FrameLayout.LayoutParams) sendButton.getLayoutParams();
        layoutParams.width = buttonWidth;
        layoutParams.leftMargin = AndroidUtilities.dp(16 + 32) + buttonWidth;

        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(236 + 6), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        gemDrawable.stop();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        gemDrawable.start();
    }

    public void setBalance(long value) {
        if (value >= 0) {
            SpannableStringBuilder stringBuilder = new SpannableStringBuilder(TonController.formatCurrency(value));
            int index = TextUtils.indexOf(stringBuilder, '.');
            if (index >= 0) {
                stringBuilder.setSpan(new TypefaceSpan(defaultTypeFace, AndroidUtilities.dp(27)), index + 1, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            valueTextView.setText(stringBuilder);
            valueTextView.setTranslationX(0);
            yourBalanceTextView.setVisibility(VISIBLE);
        } else {
            valueTextView.setText("");
            valueTextView.setTranslationX(-AndroidUtilities.dp(4));
            yourBalanceTextView.setVisibility(GONE);
        }
        int visibility = value <= 0 ? GONE : VISIBLE;
        if (sendButton.getVisibility() != visibility) {
            sendButton.setVisibility(visibility);
        }
    }

    protected void onReceivePressed() {

    }

    protected void onSendPressed() {

    }
}
