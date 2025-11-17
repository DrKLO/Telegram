package org.telegram.ui.Stories;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.blur3.StrokeDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;

public class MuteButton extends FrameLayout {

    private final FrameLayout layout;
    private final ImageView image;
    private final StrokeDrawable background;

    public MuteButton(Context context, BlurredBackgroundColorProvider colorProvider) {
        super(context);
        ScaleStateListAnimator.apply(this);

        layout = new FrameLayout(context);
        background = new StrokeDrawable();
        background.setColorProvider(colorProvider);
        background.setBackgroundColor(0xFF20242A);
        background.setPadding(dp(1));
        layout.setBackground(background);
        addView(layout, LayoutHelper.createFrame(40, 40, Gravity.CENTER));

        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER);
        image.setScaleX(0.75f);
        image.setScaleY(0.75f);
        image.setColorFilter(new PorterDuffColorFilter(0xFFD2D3D4, PorterDuff.Mode.SRC_IN));
        layout.addView(image, LayoutHelper.createFrame(40, 40, Gravity.CENTER));

        setMuted(false, false);
    }

    public void setMuted(boolean muted, boolean animated) {
        if (!animated) {
            AndroidUtilities.updateImageViewImageAnimated(image, muted ? R.drawable.msg_voice_muted : R.drawable.msg_voice_unmuted);
        } else {
            image.setImageResource(muted ? R.drawable.msg_voice_muted : R.drawable.msg_voice_unmuted);
        }
        updateFill(muted, animated);
    }

    private float mutedT;
    private ValueAnimator animator;
    private void updateFill(boolean muted, boolean animated) {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (!animated) {
            mutedT = muted ? 1.0f : 0.0f;
            background.setBackgroundColor(ColorUtils.blendARGB(0xFF31AA28, 0xFF20242A, mutedT));
            image.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(0xFFFFFFFF, 0xFFD2D3D4, mutedT), PorterDuff.Mode.SRC_IN));
            layout.invalidate();
        } else {
            animator = ValueAnimator.ofFloat(mutedT, muted ? 1.0f : 0.0f);
            animator.addUpdateListener(a -> {
                mutedT = (float) a.getAnimatedValue();
                background.setBackgroundColor(ColorUtils.blendARGB(0xFF31AA28, 0xFF20242A, mutedT));
                image.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(0xFFFFFFFF, 0xFFD2D3D4, mutedT), PorterDuff.Mode.SRC_IN));
                layout.invalidate();
            });
            animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            animator.setDuration(200);
            animator.start();
        }
    }


}
