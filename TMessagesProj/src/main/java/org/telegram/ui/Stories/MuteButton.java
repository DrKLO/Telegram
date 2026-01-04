package org.telegram.ui.Stories;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.blur3.StrokeDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;

public class MuteButton extends FrameLayout {

    private final FrameLayout layout;
    private final View filledBackgroundView;
    private final ImageView image;
    private final StrokeDrawable background;
    private final View loadingView;

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

        filledBackgroundView = new View(context);
        filledBackgroundView.setBackground(Theme.createCircleDrawable(dp(40), 0xFF31AA28));
        layout.addView(filledBackgroundView, LayoutHelper.createFrame(38, 38, Gravity.CENTER));
        filledBackgroundView.setAlpha(0);
        filledBackgroundView.setScaleX(0);
        filledBackgroundView.setScaleY(0);

        loadingView = new View(context) {
            private final CircularProgressDrawable progressDrawable = new CircularProgressDrawable(dp(36), dp(2), 0xFF31AA28);
            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                final int p = dp(1);
                progressDrawable.setBounds(p, p, getWidth()-p-p, getHeight()-p-p);
                progressDrawable.draw(canvas);
                invalidate();
            }
        };
        addView(loadingView, LayoutHelper.createFrame(42, 42, Gravity.CENTER));

        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER);
        image.setScaleX(0.75f);
        image.setScaleY(0.75f);
        image.setColorFilter(new PorterDuffColorFilter(0xFFD2D3D4, PorterDuff.Mode.SRC_IN));
        layout.addView(image, LayoutHelper.createFrame(40, 40, Gravity.CENTER));

        setMuted(false, false);
    }

    private boolean connected;
    private ValueAnimator loadingViewAnimator;
    public void setConnected(boolean connected, boolean animated) {
        if (this.connected == connected && animated) return;
        this.connected = connected;
        if (loadingViewAnimator != null) {
            loadingViewAnimator.cancel();
            loadingViewAnimator = null;
        }
        if (!animated) {
            loadingView.setAlpha(connected ? 0f : 1f);
            loadingView.setVisibility(connected ? View.GONE : View.VISIBLE);
        } else {
            loadingView.setVisibility(View.VISIBLE);
            loadingViewAnimator = ValueAnimator.ofFloat(loadingView.getAlpha(), connected ? 0f : 1f);
            loadingViewAnimator.addUpdateListener(a -> {
                loadingView.setAlpha((float) a.getAnimatedValue());
            });
            loadingViewAnimator.setDuration(320);
            loadingViewAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            loadingViewAnimator.start();
        }
        updateFill(muted || !connected, animated);
    }

    private boolean muted;
    public void setMuted(boolean muted, boolean animated) {
        this.muted = muted;
        if (!animated) {
            AndroidUtilities.updateImageViewImageAnimated(image, muted ? R.drawable.msg_voice_muted : R.drawable.msg_voice_unmuted);
        } else {
            image.setImageResource(muted ? R.drawable.msg_voice_muted : R.drawable.msg_voice_unmuted);
        }
        updateFill(muted || !connected, animated);
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
//            background.setBackgroundColor(ColorUtils.blendARGB(0xFF31AA28, 0xFF20242A, mutedT));
            filledBackgroundView.setAlpha(1.0f - mutedT);
            filledBackgroundView.setScaleX(1.0f - mutedT);
            filledBackgroundView.setScaleY(1.0f - mutedT);
            image.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(0xFFFFFFFF, 0xFFD2D3D4, mutedT), PorterDuff.Mode.SRC_IN));
            layout.invalidate();
        } else {
            animator = ValueAnimator.ofFloat(mutedT, muted ? 1.0f : 0.0f);
            animator.addUpdateListener(a -> {
                mutedT = (float) a.getAnimatedValue();
//                background.setBackgroundColor(ColorUtils.blendARGB(0xFF31AA28, 0xFF20242A, mutedT));
                filledBackgroundView.setAlpha(1.0f - mutedT);
                filledBackgroundView.setScaleX(1.0f - mutedT);
                filledBackgroundView.setScaleY(1.0f - mutedT);
                image.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(0xFFFFFFFF, 0xFFD2D3D4, mutedT), PorterDuff.Mode.SRC_IN));
                layout.invalidate();
            });
            animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            animator.setDuration(420);
            animator.start();
        }
    }


}
