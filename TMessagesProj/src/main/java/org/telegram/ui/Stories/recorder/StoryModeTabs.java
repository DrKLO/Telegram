package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

public class StoryModeTabs extends FrameLayout implements FlashViews.Invertable {

    private final LinearLayout layout;

    private final FrameLayout liveLayout;
    private final TextView live;
    private final FrameLayout photoLayout;
    private final TextView photo;
    private final FrameLayout videoLayout;
    private final TextView video;

    private float invert;

    public StoryModeTabs(Context context) {
        super(context);

        layout = new LinearLayout(context) {
            private final RectF a = new RectF(), b = new RectF(), c = new RectF();
            private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private void setRect(int mode, RectF rect) {
                View view = mode <= -1 ? liveLayout : mode >= 1 ? videoLayout : photoLayout;
                rect.set(view.getLeft(), view.getBottom() - dp(30), view.getRight(), view.getBottom());
            }
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                setRect((int) Math.floor(mode), a);
                setRect((int) Math.ceil(mode), b);
                lerp(a, b, mode - (float) Math.floor(mode), c);
                backgroundPaint.setColor(Theme.multAlpha(ColorUtils.blendARGB(Color.WHITE, Color.BLACK, invert), 0.15f));
                canvas.drawRoundRect(c, c.height() / 2f, c.height() / 2f, backgroundPaint);

                super.dispatchDraw(canvas);
            }
        };
        layout.setOrientation(LinearLayout.HORIZONTAL);

        liveLayout = new FrameLayout(context);
        live = new TextView(context);
        live.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        live.setTypeface(AndroidUtilities.bold());
        live.setTextColor(0xFFFFFFFF);
        live.setText(getString(R.string.StoryLive));
        liveLayout.addView(live, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 16, 0, 16, 7));
        layout.addView(liveLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.FILL_VERTICAL, 0, 0, 6.66f, 0));
        liveLayout.setOnClickListener(v -> switchModeInternal(-1));
        ScaleStateListAnimator.apply(liveLayout);

        photoLayout = new FrameLayout(context);
        photo = new TextView(context);
        photo.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        photo.setTypeface(AndroidUtilities.bold());
        photo.setTextColor(0xFFFFFFFF);
        photo.setText(getString(R.string.StoryPhoto));
        photoLayout.addView(photo, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 16, 0, 16, 7));
        layout.addView(photoLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.FILL_VERTICAL, 0, 0, 6.66f, 0));
        photoLayout.setOnClickListener(v -> switchModeInternal(0));
        ScaleStateListAnimator.apply(photoLayout);

        videoLayout = new FrameLayout(context);
        video = new TextView(context);
        video.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        video.setTypeface(AndroidUtilities.bold());
        video.setTextColor(0xFFFFFFFF);
        video.setText(getString(R.string.StoryVideo));
        videoLayout.addView(video, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 16, 0, 16, 7));
        layout.addView(videoLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.FILL_VERTICAL, 0, 0, 0, 0));
        videoLayout.setOnClickListener(v -> switchModeInternal(1));
        ScaleStateListAnimator.apply(videoLayout);

        addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_HORIZONTAL | Gravity.FILL_VERTICAL));
    }

    private float mode;
    private int toMode;
    private ValueAnimator animator;

    private Utilities.Callback<Integer> onSwitchModeListener;
    private Utilities.Callback<Float> onSwitchingModeListener;
    public void setOnSwitchModeListener(Utilities.Callback<Integer> onSwitchModeListener) {
        this.onSwitchModeListener = onSwitchModeListener;
    }

    public void setOnSwitchingModeListener(Utilities.Callback<Float> onSwitchingModeListener) {
        this.onSwitchingModeListener = onSwitchingModeListener;
    }

    private void switchModeInternal(int targetMode) {
        if (toMode == targetMode) return;
        switchMode(targetMode);
        if (onSwitchModeListener != null) {
            onSwitchModeListener.run(targetMode);
        }
    }

    public void switchMode(int newMode) {
        if (toMode == newMode) return;
        toMode = newMode;
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(mode, newMode);
        animator.addUpdateListener(anm -> {
            mode = (float) anm.getAnimatedValue();
            if (onSwitchingModeListener != null) {
                onSwitchingModeListener.run(Utilities.clamp(mode, 1, -1));
            }
            layout.invalidate();
        });
        animator.setDuration(320);
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animator.start();
    }

    protected boolean allowTouch() {
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!allowTouch()) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void setInvert(float invert) {
        this.invert = invert;
        live.setTextColor(ColorUtils.blendARGB(0xFFFFFFFF, 0xFF000000, invert));
        photo.setTextColor(ColorUtils.blendARGB(0xFFFFFFFF, 0xFF000000, invert));
        video.setTextColor(ColorUtils.blendARGB(0xFFFFFFFF, 0xFF000000, invert));
    }

    @Override
    public void invalidate() {
        super.invalidate();
        layout.invalidate();
    }
}
