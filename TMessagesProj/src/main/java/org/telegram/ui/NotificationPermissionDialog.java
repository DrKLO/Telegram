package org.telegram.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

public class NotificationPermissionDialog extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    private CounterView counterView;
    private RLottieImageView rLottieImageView;
    private Utilities.Callback<Boolean> whenGranted;

    public NotificationPermissionDialog(Context context, boolean settings, Utilities.Callback<Boolean> whenGranted) {
        super(context, false);
        this.whenGranted = whenGranted;

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        FrameLayout block = new FrameLayout(context);
        rLottieImageView = new RLottieImageView(context);
        rLottieImageView.setScaleType(ImageView.ScaleType.CENTER);
        rLottieImageView.setAnimation(R.raw.silent_unmute, 46, 46);
        rLottieImageView.playAnimation();
        rLottieImageView.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(72), Theme.getColor(Theme.key_featuredStickers_addButton)));
        block.addView(rLottieImageView, LayoutHelper.createFrame(72, 72, Gravity.CENTER));
        block.addView(counterView = new CounterView(context), LayoutHelper.createFrame(64, 32, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 29, 16, 0, 0));
        counterView.setCount(0);
        block.setOnClickListener(e -> {
            if (!rLottieImageView.isPlaying()) {
                rLottieImageView.setProgress(0);
                rLottieImageView.playAnimation();
            }
        });
        linearLayout.addView(block, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 110));

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setTypeface(AndroidUtilities.bold());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setText(LocaleController.getString(R.string.NotificationsPermissionAlertTitle));
        textView.setPadding(AndroidUtilities.dp(30), 0, AndroidUtilities.dp(30), 0);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setText(LocaleController.getString(R.string.NotificationsPermissionAlertSubtitle));
        textView.setPadding(AndroidUtilities.dp(30), AndroidUtilities.dp(10), AndroidUtilities.dp(30), AndroidUtilities.dp(21));
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        linearLayout.addView(new SectionView(context, R.drawable.msg_message_s,     LocaleController.getString(R.string.NotificationsPermissionAlert1)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        linearLayout.addView(new SectionView(context, R.drawable.msg_members_list2, LocaleController.getString(R.string.NotificationsPermissionAlert2)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        linearLayout.addView(new SectionView(context, R.drawable.msg_customize_s,   LocaleController.getString(R.string.NotificationsPermissionAlert3)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        setCustomView(linearLayout);
        fixNavigationBar(getThemedColor(Theme.key_dialogBackground));

        textView = new TextView(context);
        textView.setText(LocaleController.getString(settings ? R.string.NotificationsPermissionSettings : R.string.NotificationsPermissionContinue));
        textView.setGravity(Gravity.CENTER);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        textView.setBackground(Theme.AdaptiveRipple.filledRect(Theme.getColor(Theme.key_featuredStickers_addButton), 8));
        textView.setOnClickListener(e -> {
            if (this.whenGranted != null) {
                this.whenGranted.run(true);
                this.whenGranted = null;
            }
            dismiss();
        });
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 14, 14, 14, 10));

        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; ++a) {
            try {
                NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.updateInterfaces);
            } catch (Exception ignore) {}
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            int flags = (int) args[0];
            if ((flags & MessagesController.UPDATE_MASK_READ_DIALOG_MESSAGE) >= 0) {
                updateCounter();
            }
        }
    }

    public void updateCounter() {
        int counter = 0;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; ++a) {
            MessagesStorage messagesStorage = MessagesStorage.getInstance(a);
            if (messagesStorage != null) {
                counter += messagesStorage.getMainUnreadCount();
            }
        }
        if (counterView.setCount(counter)) {
            if (!rLottieImageView.isPlaying()) {
                rLottieImageView.setProgress(0);
                rLottieImageView.playAnimation();
            }
        }
    }

    private long showTime;

    @Override
    public void show() {
        super.show();
        showTime = System.currentTimeMillis();
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (whenGranted != null) {
            whenGranted.run(false);
            whenGranted = null;
            askLater();
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; ++a) {
            try {
                NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.updateInterfaces);
            } catch (Exception ignore) {}
        }
    }

    private static class CounterView extends View {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG), strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final AnimatedFloat alpha = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

        AnimatedTextView.AnimatedTextDrawable textDrawable = new AnimatedTextView.AnimatedTextDrawable(false, true, true);

        public CounterView(Context context) {
            super(context);

            fillPaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            strokePaint.setColor(Theme.getColor(Theme.key_dialogBackground));
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(AndroidUtilities.dp(4));

            textDrawable.setCallback(this);
            textDrawable.setAnimationProperties(.35f, 0, 200, CubicBezierInterpolator.EASE_OUT_QUINT);
            textDrawable.getPaint().setStyle(Paint.Style.FILL_AND_STROKE);
            textDrawable.getPaint().setStrokeWidth(AndroidUtilities.dp(.24f));
            textDrawable.getPaint().setStrokeJoin(Paint.Join.ROUND);
            textDrawable.setTextSize(AndroidUtilities.dp(13.3f));
            textDrawable.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            textDrawable.setOverrideFullWidth(AndroidUtilities.dp(64));
            textDrawable.setGravity(Gravity.CENTER_HORIZONTAL);
        }

        private int lastCount;
        public boolean setCount(int count) {
            if (lastCount != count) {
                boolean more = lastCount < count;
                lastCount = count;
                textDrawable.setText(lastCount > 0 ? "" + lastCount : "", true);
                if (more) {
                    animateBounce();
                }
                return more;
            }
            return false;
        }

        private float countScale = 1;
        private ValueAnimator countAnimator;
        private void animateBounce() {
            if (countAnimator != null) {
                countAnimator.cancel();
                countAnimator = null;
            }

            countAnimator = ValueAnimator.ofFloat(0, 1);
            countAnimator.addUpdateListener(anm -> {
                countScale = Math.max(1, (float) anm.getAnimatedValue());
                invalidate();
            });
            countAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    countScale = 1;
                    invalidate();
                }
            });
            countAnimator.setInterpolator(new OvershootInterpolator(2.0f));
            countAnimator.setDuration(200);
            countAnimator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float alpha = this.alpha.set(lastCount > 0 ? 1 : 0);

            canvas.save();
            canvas.scale(countScale * alpha, countScale * alpha, getWidth() / 2f, getHeight() / 2f);

            float w = textDrawable.getCurrentWidth() + AndroidUtilities.dpf2(12.66f), h = AndroidUtilities.dpf2(20.3f);
            AndroidUtilities.rectTmp.set((getWidth() - w) / 2f, (getHeight() - h) / 2f, (getWidth() + w) / 2f, (getHeight() + h) / 2f);

            strokePaint.setAlpha((int) (0xFF * alpha));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(30), AndroidUtilities.dp(30), strokePaint);
            fillPaint.setAlpha((int) (0xFF * alpha));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(30), AndroidUtilities.dp(30), fillPaint);

            canvas.save();
            canvas.translate(0, -AndroidUtilities.dp(1));
            textDrawable.setBounds(0, 0, getWidth(), getHeight());
            textDrawable.draw(canvas);
            canvas.restore();

            canvas.restore();
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return who == textDrawable || super.verifyDrawable(who);
        }
    }


    private static class SectionView extends FrameLayout {
        public SectionView(Context context, int resId, CharSequence text) {
            super(context);

            setPadding(0, AndroidUtilities.dp(7), 0, AndroidUtilities.dp(7));

            ImageView imageView = new ImageView(context);
            imageView.setImageResource(resId);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));
            addView(imageView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 22, 0, LocaleController.isRTL ? 22 : 0, 0));

            TextView textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            textView.setText(text);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, LocaleController.isRTL ? 0 : 61, 0, LocaleController.isRTL ? 61 : 0, 0));
        }
    }

    public static boolean shouldAsk(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        long askAfter = MessagesController.getGlobalMainSettings().getLong("askNotificationsAfter", -1);
        return askAfter != -2 && (askAfter < 0 || System.currentTimeMillis() >= askAfter);
    }

    public static void askLater() {
        long askAfter;
        final long day = 1000L * 60L * 60L * 24L;
        long nextDuration = MessagesController.getGlobalMainSettings().getLong("askNotificationsDuration", day);
        askAfter = System.currentTimeMillis() + nextDuration;
        if (nextDuration < day * 3) {
            nextDuration = day * 3;
        } else if (nextDuration < day * 7) {
            nextDuration = day * 7;
        } else {
            nextDuration = day * 30;
        }
        MessagesController.getGlobalMainSettings().edit().putLong("askNotificationsAfter", askAfter).putLong("askNotificationsDuration", nextDuration).apply();
    }
}
