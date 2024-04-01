package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.REPLACING_TAG_TYPE_LINK;
import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UnconfirmedAuthController;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.SessionsActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

public class UnconfirmedAuthHintCell extends FrameLayout {

    private final LinearLayout linearLayout;
    private final TextView titleTextView;
    private final TextView messageTextView;

    private final LinearLayout buttonsLayout;
    private final TextViewWithLoading yesButton;
    private final TextViewWithLoading noButton;

    public UnconfirmedAuthHintCell(Context context) {
        super(context);

        setClickable(true);

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        titleTextView = new TextView(context);
        titleTextView.setGravity(Gravity.CENTER);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        titleTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        titleTextView.setText(LocaleController.getString(R.string.UnconfirmedAuthTitle));
        linearLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.TOP | Gravity.FILL_HORIZONTAL, 28,  11, 28, 0));

        messageTextView = new TextView(context);
        messageTextView.setGravity(Gravity.CENTER);
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.TOP | Gravity.FILL_HORIZONTAL, 28,  5, 28, 0));

        buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setGravity(Gravity.CENTER);

        buttonsLayout.addView(new Space(context), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 1, Gravity.CENTER, 1));

        yesButton = new TextViewWithLoading(context);
        yesButton.setPadding(dp(10), dp(5), dp(10), dp(7));
        yesButton.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        yesButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.22f);
        yesButton.setText(LocaleController.getString(R.string.UnconfirmedAuthConfirm));
        buttonsLayout.addView(yesButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 30));

        buttonsLayout.addView(new Space(context), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 1, Gravity.CENTER, 1));

        noButton = new TextViewWithLoading(context);
        noButton.setPadding(dp(10), dp(5), dp(10), dp(7));
        noButton.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        noButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.22f);
        noButton.setText(LocaleController.getString(R.string.UnconfirmedAuthDeny));
        buttonsLayout.addView(noButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 30));

        buttonsLayout.addView(new Space(context), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 1, Gravity.CENTER, 1));

        linearLayout.addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 28, 7, 28, 8));

        addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        updateColors();
    }

    public void set(final BaseFragment fragment, int currentAccount) {
        final ArrayList<UnconfirmedAuthController.UnconfirmedAuth> auths = MessagesController.getInstance(currentAccount).getUnconfirmedAuthController().auths;

        titleTextView.setText(LocaleController.getString(R.string.UnconfirmedAuthTitle));
        yesButton.setText(LocaleController.getString(R.string.UnconfirmedAuthConfirm));
        yesButton.setLoading(false, false);
        noButton.setText(LocaleController.getString(R.string.UnconfirmedAuthDeny));
        noButton.setLoading(false, false);

        if (auths != null && auths.size() == 1) {
            String from = "";
            from += auths.get(0).device;
            if (!TextUtils.isEmpty(auths.get(0).location) && !from.isEmpty()) {
                from += ", ";
            }
            from += auths.get(0).location;
            messageTextView.setText(LocaleController.formatString(R.string.UnconfirmedAuthSingle, from));
        } else if (auths != null && auths.size() > 1) {
            String from = auths.get(0).location;
            for (int i = 1; i < auths.size(); ++i) {
                if (!TextUtils.equals(from, auths.get(i).location)) {
                    from = null;
                    break;
                }
            }
            if (from == null) {
                messageTextView.setText(LocaleController.formatPluralString("UnconfirmedAuthMultiple", auths.size()));
            } else {
                messageTextView.setText(LocaleController.formatPluralString("UnconfirmedAuthMultipleFrom", auths.size(), from));
            }
        }

        yesButton.setOnClickListener(v -> {
            SpannableStringBuilder message = AndroidUtilities.replaceSingleTag(LocaleController.getString(R.string.UnconfirmedAuthConfirmedMessage), Theme.key_undo_cancelColor, REPLACING_TAG_TYPE_LINK, () -> {
                Bulletin.hideVisible();
                fragment.presentFragment(new SessionsActivity(0));
            });
            SpannableString arrowStr = new SpannableString(">");
            ColoredImageSpan span = new ColoredImageSpan(R.drawable.attach_arrow_right);
            span.setOverrideColor(Theme.getColor(Theme.key_undo_cancelColor));
            span.setScale(.7f, .7f);
            span.setWidth(dp(12));
            arrowStr.setSpan(span, 0, arrowStr.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            AndroidUtilities.replaceCharSequence(">", message, arrowStr);
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.UnconfirmedAuthConfirmed), message).show();
            MessagesController.getInstance(currentAccount).getUnconfirmedAuthController().confirm(auths, success -> {

            });
            MessagesController.getInstance(currentAccount).getUnconfirmedAuthController().cleanup();
        });
        noButton.setOnClickListener(v -> {
            noButton.setLoading(true);
            MessagesController.getInstance(currentAccount).getUnconfirmedAuthController().deny(auths, success -> {
                if (LaunchActivity.isActive)
                    showLoginPreventedSheet(success);
                noButton.setLoading(false);
                MessagesController.getInstance(currentAccount).getUnconfirmedAuthController().cleanup();
            });
        });
    }

    public void updateColors() {
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        messageTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        yesButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
        yesButton.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteValueText), Theme.isCurrentThemeDark() ? .3f : .15f), Theme.RIPPLE_MASK_ROUNDRECT_6DP, dp(8)));
        noButton.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        noButton.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_text_RedBold), Theme.isCurrentThemeDark() ? .3f : .15f), Theme.RIPPLE_MASK_ROUNDRECT_6DP, dp(8)));
    }

    private static class TextViewWithLoading extends TextView {
        public TextViewWithLoading(Context context) {
            super(context);
        }

        public void setLoading(boolean loading) {
            setLoading(loading, true);
        }

        public void setLoading(boolean loading, boolean animated) {
            this.loading = loading;
            if (!animated) {
                loadingT.set(loading, true);
            }
            super.setPressed(isPressed() || loading);
            invalidate();
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed || loading);
        }

        private boolean loading;
        private final AnimatedFloat loadingT = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        private CircularProgressDrawable progressDrawable;

        @Override
        protected void onDraw(Canvas canvas) {
            float loading = loadingT.set(this.loading);
            if (loading > 0) {
                if (loading < 1) {
                    canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * (1f - loading)), Canvas.ALL_SAVE_FLAG);
                    final float s = 1f - loading * .2f;
                    canvas.scale(s, s, getWidth() / 2f, getHeight() / 2f);
                    canvas.translate(0, dp(-12) * loading);
                    super.onDraw(canvas);
                    canvas.restore();
                }

                if (progressDrawable == null) {
                    progressDrawable = new CircularProgressDrawable(dp(16), dp(2), getCurrentTextColor());
                    progressDrawable.setCallback(this);
                }
                progressDrawable.setColor(getCurrentTextColor());
                progressDrawable.setBounds(
                    getWidth() / 2, getHeight() / 2 + (int) ((1f - loading) * dp(12)),
                    getWidth() / 2, getHeight() / 2 + (int) ((1f - loading) * dp(12))
                );
                progressDrawable.setAlpha((int) (0xFF * loading));
                progressDrawable.draw(canvas);
                invalidate();
            } else {
                super.onDraw(canvas);
            }
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return progressDrawable == who || super.verifyDrawable(who);
        }
    }

    private int height;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) {
            width = AndroidUtilities.displaySize.x;
        }
        linearLayout.measure(
            MeasureSpec.makeMeasureSpec(width - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y, MeasureSpec.AT_MOST)
        );
        this.height = linearLayout.getMeasuredHeight() + getPaddingTop() + getPaddingBottom() + 1;
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    public int height() {
        if (getVisibility() != View.VISIBLE) {
            return 0;
        }
        if (height <= 0) {
            height = dp(72) + 1;
        }
        return height;
    }

    public void showLoginPreventedSheet(ArrayList<UnconfirmedAuthController.UnconfirmedAuth> auths) {
        if (auths == null || auths.size() == 0) {
            BulletinFactory.of(Bulletin.BulletinWindow.make(getContext()), null)
                    .createErrorBulletin(LocaleController.getString(R.string.UnknownError))
                    .show();
            return;
        }

        LinearLayout linearLayout = new LinearLayout(getContext());
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        RLottieImageView imageView = new RLottieImageView(getContext());
        imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        imageView.setAnimation(R.raw.ic_ban, 50, 50);
        imageView.playAnimation();
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setBackground(Theme.createCircleDrawable(dp(80), Theme.getColor(Theme.key_windowBackgroundWhiteValueText)));
        linearLayout.addView(imageView, LayoutHelper.createLinear(80, 80, Gravity.CENTER, 0, 14, 0, 0));

        TextView headerTextView = new TextView(getContext());
        headerTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        headerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        headerTextView.setGravity(Gravity.CENTER);
        headerTextView.setText(LocaleController.formatPluralString("UnconfirmedAuthDeniedTitle", auths.size()));
        headerTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        linearLayout.addView(headerTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 28, 14, 28, 0));

        TextView messageTextView = new TextView(getContext());
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        messageTextView.setGravity(Gravity.CENTER);
        if (auths.size() == 1) {
            messageTextView.setText(LocaleController.formatString(R.string.UnconfirmedAuthDeniedMessageSingle, from(auths.get(0))));
        } else {
            String from = "\n";
            for (int i = 0; i < Math.min(auths.size(), 10); ++i) {
                from += "• " + from(auths.get(i)) + "\n";
            }
            messageTextView.setText(LocaleController.formatString(R.string.UnconfirmedAuthDeniedMessageMultiple, from));
        }
        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 40, 9, 40, 0));

        FrameLayout warningLayout = new FrameLayout(getContext());
        warningLayout.setPadding(dp(10), dp(10), dp(10), dp(10));
        warningLayout.setBackground(Theme.createRoundRectDrawable(dp(8), Theme.multAlpha(Theme.getColor(Theme.key_text_RedBold), Theme.isCurrentThemeDark() ? .2f : .15f)));

        TextView warningTextView = new TextView(getContext());
        warningTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        warningTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        warningTextView.setGravity(Gravity.CENTER);
        warningTextView.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        warningTextView.setText(LocaleController.getString(R.string.UnconfirmedAuthDeniedWarning));
        warningLayout.addView(warningTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        linearLayout.addView(warningLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 19, 14, 0));

        ButtonWithCounterView button = new ButtonWithCounterView(getContext(), null);
        ScaleStateListAnimator.apply(button, 0.02f, 1.5f);
        button.setText(LocaleController.getString(R.string.GotIt), false);
        linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 14, 20, 14, 4));

        final BottomSheet sheet = new BottomSheet.Builder(getContext())
            .setCustomView(linearLayout)
            .show();

        sheet.setCanDismissWithSwipe(false);
        sheet.setCanDismissWithTouchOutside(false);
        button.setTimer(5, () -> {
            sheet.setCanDismissWithSwipe(true);
            sheet.setCanDismissWithTouchOutside(true);
        });
        button.setOnClickListener(v -> {
            if (button.isTimerActive()) {
                AndroidUtilities.shakeViewSpring(button, 3);
                BotWebViewVibrationEffect.APP_ERROR.vibrate();
            } else {
                sheet.dismiss();
            }
        });
    }

    private static String from(UnconfirmedAuthController.UnconfirmedAuth auth) {
        if (auth == null) {
            return "";
        }
        String from = "";
        from += auth.device;
        if (!TextUtils.isEmpty(auth.location) && !from.isEmpty()) {
            from += ", ";
        }
        from += auth.location;
        return from;
    }
}
