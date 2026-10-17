package org.telegram.ui.Stars;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.Switch;

/** Direct personal privacy control beside history, shared with the account-free preview. */
public final class StarsPrivacyView extends LinearLayout implements NotificationCenter.NotificationCenterDelegate {
    private final int account;
    private final BaseFragment fragment;
    private final Theme.ResourcesProvider resourcesProvider;
    private final TextView title;
    private final TextView subtitle;
    private final Switch toggle;
    private final RLottieImageView star;
    private final FrameLayout lockBadge;
    private final RLottieImageView lock;
    private boolean checked;
    private Bulletin feedback;

    public StarsPrivacyView(Context context, int account, BaseFragment fragment) {
        super(context);
        this.account = account;
        this.fragment = fragment;
        resourcesProvider = fragment.getResourceProvider();
        setTag("stars_history_privacy");
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16));
        setMinimumHeight(AndroidUtilities.dp(112));
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        FrameLayout illustration = new FrameLayout(context);
        illustration.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        star = new RLottieImageView(context);
        star.setAnimation(R.raw.stars_topup, 72, 72);
        star.setProgress(1f);
        illustration.addView(star, LayoutHelper.createFrame(72, 72, Gravity.CENTER));
        lockBadge = new FrameLayout(context);
        lock = new RLottieImageView(context);
        lock.setAnimation(R.raw.passcode_lock_close, 32, 32);
        lock.setProgress(1f);
        lockBadge.addView(lock, LayoutHelper.createFrame(32, 32, Gravity.CENTER));
        illustration.addView(lockBadge, LayoutHelper.createFrame(36, 36, Gravity.BOTTOM | Gravity.RIGHT));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        labels.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setText(LocaleController.getString(R.string.StarsPrivacyMakePrivate));
        title.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        labels.addView(title, LayoutHelper.createLinear(-1, -2));
        subtitle = new TextView(context);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subtitle.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        labels.addView(subtitle, LayoutHelper.createLinear(-1, -2, 0, 5, 0, 0));

        toggle = new Switch(context, resourcesProvider);
        toggle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        toggle.setFocusable(false);
        toggle.setClickable(false);
        if (LocaleController.isRTL) {
            addView(toggle, LayoutHelper.createLinear(37, 40, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));
            addView(labels, LayoutHelper.createLinear(0, -2, 1f));
            addView(illustration, LayoutHelper.createLinear(72, 72, Gravity.CENTER_VERTICAL, 12, 0, 0, 0));
        } else {
            addView(illustration, LayoutHelper.createLinear(72, 72, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
            addView(labels, LayoutHelper.createLinear(0, -2, 1f));
            addView(toggle, LayoutHelper.createLinear(37, 40, Gravity.CENTER_VERTICAL, 14, 0, 0, 0));
        }
        setOnClickListener(v -> {
            boolean enabled = !StarsPrivacy.isEnabled(account);
            fragment.setVisibleDialog(StarsPrivacyConfirmation.request(context, account, enabled, resourcesProvider, () -> {
                // Once confirmed, privacy applies synchronously, before the illustration animates.
                updateState(enabled, true);
                if (feedback != null) feedback.hide(false, 0);
                feedback = BulletinFactory.of(fragment).createSimpleBulletin(R.raw.passcode_lock_close,
                    LocaleController.getString(enabled ? R.string.StarsPrivacyEnabledNotice : R.string.StarsPrivacyDisabledNotice), 4, 5000);
                feedback.show();
            }));
        });
        updateColors();
        updateState(StarsPrivacy.isEnabled(account), false);
    }

    public void updateColors() {
        setBackground(Theme.getSelectorDrawable(true, resourcesProvider));
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        toggle.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked,
            Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        lockBadge.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(36),
            Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
        lock.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(32),
            Theme.getColor(Theme.key_switchTrackChecked, resourcesProvider)));
    }

    private void updateState(boolean enabled, boolean animated) {
        boolean changed = checked != enabled;
        checked = enabled;
        toggle.setChecked(enabled, animated);
        subtitle.setText(LocaleController.getString(enabled ? R.string.StarsPrivacyOn : R.string.StarsPrivacyOff));
        setContentDescription(title.getText() + ". " + subtitle.getText());
        if (!changed && animated) return;
        cancelMotion();
        boolean motion = animated && isShown() && (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled());
        if (motion) {
            // Existing Stars Lottie + passcode latch, with Telegram's standard settling curve.
            // Only the badge leaves on opt-out: the old private receipts stay untouched.
            if (enabled) {
                star.setProgress(0f);
                star.playAnimation();
                lock.setProgress(0f);
                lock.playAnimation();
            }
            lockBadge.animate().alpha(enabled ? 1f : 0f)
                .scaleX(enabled ? 1f : .65f).scaleY(enabled ? 1f : .65f)
                .translationY(enabled ? 0f : AndroidUtilities.dp(5))
                .setDuration(enabled ? 320 : 200).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        } else {
            settleIllustration();
        }
    }

    private void cancelMotion() {
        lockBadge.animate().cancel();
        star.stopAnimation();
        lock.stopAnimation();
        star.setProgress(1f);
        lock.setProgress(1f);
    }

    private void settleIllustration() {
        cancelMotion();
        lockBadge.setAlpha(checked ? 1f : 0f);
        lockBadge.setScaleX(checked ? 1f : .65f);
        lockBadge.setScaleY(checked ? 1f : .65f);
        lockBadge.setTranslationY(checked ? 0f : AndroidUtilities.dp(5));
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(checked);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.starsPrivacyChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        updateColors();
        updateState(StarsPrivacy.isEnabled(account), false);
    }

    @Override protected void onDetachedFromWindow() {
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.starsPrivacyChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        settleIllustration();
        hideFeedback();
        super.onDetachedFromWindow();
    }

    public void hideFeedback() {
        if (feedback != null) {
            feedback.hide(false, 0);
            feedback = null;
        }
    }

    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme) {
            hideFeedback();
            updateColors();
        } else {
            updateState(StarsPrivacy.isEnabled(this.account), true);
        }
    }
}
