package org.telegram.ui.Components.conference;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.voip.VoIPToggleButton;

import java.util.LinkedHashMap;
import java.util.Map;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class GroupCallActivityButtonsLayout extends ViewGroup {
    private static final int BUTTON_WIDTH = 50;
    private static final int BUTTON_HEIGHT = 76;

    public GroupCallActivityButtonsLayout(@NonNull Context context) {
        super(context);
    }

    private int lastWidth, lastHeight;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(width, height);

        final int viewWidthSpec = MeasureSpec.makeMeasureSpec(dp(76), MeasureSpec.EXACTLY);
        final int viewHeightSpec = MeasureSpec.makeMeasureSpec(dp(76), MeasureSpec.EXACTLY);
        for (int a = 0, N = getChildCount(); a < N; a++) {
            View view = getChildAt(a);
            view.measure(viewWidthSpec, viewHeightSpec);
        }

        if (lastWidth != width || lastHeight != height) {
            doLayout(false, true);
            lastWidth = width;
            lastHeight = height;
        } else {
            doLayout(true, false);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View view = getChildAt(a);
            view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        }
    }

    private final LinkedHashMap<View, ButtonHolder> holders = new LinkedHashMap<>(16);

    public void addButton(VoIPToggleButton view) {
        addView(view);
        holders.put(view, new ButtonHolder(view, this::invalidate));
    }

    public void setButtonVisibility(VoIPToggleButton view, boolean visible, boolean animated) {
        ButtonHolder h = holders.get(view);
        if (h != null) {
            if (h.isVisible != visible) {
                h.isVisible = visible;
                doLayout(animated, false);
            }
        }
    }

    public void setButtonEnabled(VoIPToggleButton view, boolean enabled, boolean animated) {
        ButtonHolder h = holders.get(view);
        if (h != null) {
            h.enabled.setValue(enabled, animated);
            view.setEnabled(enabled);
        }
    }

    private void doLayout(boolean animated, boolean immediately) {
        final int width = getMeasuredWidth();
        final int height = getMeasuredHeight();
        final int orientation = width > height ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL;

        if (width <= 0 || height <= 0) {
            return;
        }

        int count = 0;
        for (ButtonHolder holder : holders.values()) {
            if (holder.isVisible) {
                count++;
            }
        }
        if (count == 0) {
            count = 1;
        }

        final int viewWidth, viewHeight, paddingStart;
        if (orientation == LinearLayout.HORIZONTAL) {
            final int availWidth = width - dp(BUTTON_WIDTH) * count;
            final int gap = Math.max((int) (availWidth / (count + 0.333f)), 0);

            viewWidth = Math.min(dp(BUTTON_WIDTH) + gap, width / count);
            viewHeight = dp(BUTTON_HEIGHT);
            paddingStart = (width - viewWidth * count) / 2;
        } else {
            final int availHeight = height - dp(BUTTON_WIDTH) * count;
            final int gap = Math.max((int) (availHeight / (count + 0.333f)), 0);

            viewWidth = width;
            viewHeight = Math.min(dp(BUTTON_WIDTH) + gap, height / count);
            paddingStart = (height - viewHeight * count) / 2;
        }


        int index = 0;
        for (Map.Entry<View, ButtonHolder> entry : holders.entrySet()) {
            final ButtonHolder holder = entry.getValue();

            if (holder.isVisible) {
                final int l, t;
                if (orientation == LinearLayout.HORIZONTAL) {
                    l = paddingStart + viewWidth * index + (viewWidth - holder.view.getMeasuredWidth()) / 2;
                    t = getMeasuredHeight() - dp(BUTTON_HEIGHT);
                } else {
                    l = getMeasuredWidth() - viewWidth + (viewWidth - holder.view.getMeasuredWidth()) / 2;;
                    t = paddingStart + viewHeight * index;
                }

                if (!immediately && (animated || holder.xAnimator.isAnimating()) && holder.visibility.getValue()) {
                    holder.xAnimator.animateTo(l);
                } else {
                    holder.xAnimator.forceFactor(l);
                }
                if (!immediately && (animated || holder.yAnimator.isAnimating()) && holder.visibility.getValue()) {
                    holder.yAnimator.animateTo(t);
                } else {
                    holder.yAnimator.forceFactor(t);
                }

                index++;
            }

            holder.visibility.setValue(holder.isVisible, !immediately && (animated || holder.visibility.isAnimating()));
        }

        invalidate();
    }

    public void removeButton(VoIPToggleButton view) {
        removeView(view);
        holders.remove(view);
    }


    private static class ButtonHolder implements FactorAnimator.Target {
        private static final int ANIMATOR_VISIBILITY = 0;
        private static final int ANIMATOR_X = 1;
        private static final int ANIMATOR_Y = 2;
        private static final int ANIMATOR_ENABLED = 3;

        public final FactorAnimator xAnimator = new FactorAnimator(ANIMATOR_X, this, CubicBezierInterpolator.EASE_OUT_QUINT, 350);
        public final FactorAnimator yAnimator = new FactorAnimator(ANIMATOR_Y, this, CubicBezierInterpolator.EASE_OUT_QUINT, 350);
        public final BoolAnimator visibility = new BoolAnimator(ANIMATOR_VISIBILITY, this, CubicBezierInterpolator.EASE_OUT_QUINT, 350, true);
        public final BoolAnimator enabled = new BoolAnimator(ANIMATOR_ENABLED, this, CubicBezierInterpolator.EASE_OUT_QUINT, 350, true);
        public final VoIPToggleButton view;
        private final Runnable invalidateRunnable;

        private boolean isVisible = true;

        private ButtonHolder(VoIPToggleButton view, Runnable invalidateRunnable) {
            this.view = view;
            this.invalidateRunnable = invalidateRunnable;
        }

        public void cancel() {
            xAnimator.cancel();
            yAnimator.cancel();
            visibility.cancel();
            enabled.cancel();
        }

        @Override
        public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
            if (id == ANIMATOR_X) {
                view.setTranslationX(xAnimator.getFactor());
            }
            if (id == ANIMATOR_Y) {
                view.setTranslationY(yAnimator.getFactor());
            }
            if (id == ANIMATOR_VISIBILITY) {
                float alpha = visibility.getFloatValue() * lerp(0.5f, 1f, enabled.getFloatValue());
                view.setAlpha(alpha);
                view.setScaleX(lerp(0.3f, 1f, factor));
                view.setScaleY(lerp(0.3f, 1f, factor));
                view.setVisibility(factor > 0 ? VISIBLE : GONE);
            }
            if (id == ANIMATOR_ENABLED) {
                float alpha = visibility.getFloatValue() * lerp(0.5f, 1f, enabled.getFloatValue());
                view.setAlpha(alpha);
            }

            if (invalidateRunnable != null) {
                invalidateRunnable.run();
            }
        }
    }
}
