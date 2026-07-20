package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EllipsizeSpanAnimator;
import org.telegram.ui.Components.LayoutHelper;

import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;
import me.vkryl.core.lambda.Destroyable;

@SuppressLint("ViewConstructor")
public class ActionBarAnimatedSubtitleOverlayContainer extends FrameLayout implements ReplaceAnimator.Callback {
    private final Theme.ResourcesProvider resourcesProvider;
    private final EllipsizeSpanAnimator ellipsizeSpanAnimator;

    private final ReplaceAnimator<SimpleTextViewReplaceable> titleOverlayAnimator =
            new ReplaceAnimator<>(this, CubicBezierInterpolator.EASE_OUT_QUINT, 350);

    public ActionBarAnimatedSubtitleOverlayContainer(@NonNull Context context, Theme.ResourcesProvider resourcesProvider, EllipsizeSpanAnimator ellipsizeSpanAnimator) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.ellipsizeSpanAnimator = ellipsizeSpanAnimator;
    }

    public void setText(CharSequence text, boolean animated) {
        if (TextUtils.isEmpty(text)) {
            titleOverlayAnimator.clear(animated);
            return;
        }

        boolean ellipsize = false;
        int index = TextUtils.indexOf(text, "...");
        if (index >= 0) {
            SpannableString spannableString = SpannableString.valueOf(text);
            ellipsizeSpanAnimator.wrap(spannableString, index);
            text = spannableString;
            ellipsize = true;
        }

        final SimpleTextViewReplaceable overlayTextView = new SimpleTextViewReplaceable(getContext());
        overlayTextView.setTextColor(Theme.getColor(Theme.key_telegram_color_dialogsLogo, resourcesProvider));
        overlayTextView.setLinkTextColor(Theme.getColor(Theme.key_telegram_color_dialogsLogo, resourcesProvider));
        overlayTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        overlayTextView.setAlpha(0);
        overlayTextView.setText(text);
        if (ellipsize) {
            ellipsizeSpanAnimator.addView(overlayTextView);
        }

        addView(overlayTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        titleOverlayAnimator.replace(overlayTextView, animated);
    }

    private void checkUi_titleOverlayTextAnimation() {
        for (ListAnimator.Entry<SimpleTextViewReplaceable> entry : titleOverlayAnimator) {
            final float visibility = entry.getVisibility();
            final float scale = lerp(0.85f, 1f, visibility);
            entry.item.setAlpha(visibility);
            entry.item.setScaleX(scale);
            entry.item.setScaleY(scale);
            entry.item.setTranslationY(lerp(dp(entry.isAffectingList() ? 9 : -9), 0, visibility));
        }
    }

    public void updateColors() {
        for (ListAnimator.Entry<SimpleTextViewReplaceable> entry : titleOverlayAnimator) {
            entry.item.setTextColor(Theme.getColor(Theme.key_telegram_color_dialogsLogo, resourcesProvider));
            entry.item.setLinkTextColor(Theme.getColor(Theme.key_telegram_color_dialogsLogo, resourcesProvider));
        }
    }

    @Override
    public void onItemChanged(ReplaceAnimator<?> animator) {
        checkUi_titleOverlayTextAnimation();
    }

    public float getTotalVisibility() {
        return titleOverlayAnimator.getMetadata().getTotalVisibility();
    }

    private class SimpleTextViewReplaceable extends TextView implements Destroyable {
        public SimpleTextViewReplaceable(Context context) {
            super(context);
        }

        @Override
        public void performDestroy() {
            AndroidUtilities.removeFromParent(this);
            ellipsizeSpanAnimator.removeView(this);
        }
    }
}
