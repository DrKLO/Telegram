package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;


@SuppressWarnings("FieldCanBeLocal")
public class ChecksHintView extends FrameLayout {

    private TextView[] textView = new TextView[2];
    private RLottieImageView[] imageView = new RLottieImageView[2];
    private ImageView arrowImageView;
    private ChatMessageCell messageCell;
    private View currentView;
    private AnimatorSet animatorSet;
    private Runnable hideRunnable;
    private float translationY;

    private long showingDuration = 2000;
    private final Theme.ResourcesProvider resourcesProvider;

    public ChecksHintView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        FrameLayout backgroundView = new FrameLayout(context);
        backgroundView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(6), getThemedColor(Theme.key_chat_gifSaveHintBackground)));
        backgroundView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 6));

        for (int a = 0; a < 2; a++) {
            imageView[a] = new RLottieImageView(context);
            imageView[a].setScaleType(ImageView.ScaleType.CENTER);
            backgroundView.addView(imageView[a], LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.TOP, 0, a == 0 ? 0 : 24, 0, 0));

            textView[a] = new TextView(context);
            textView[a].setTextColor(getThemedColor(Theme.key_chat_gifSaveHintText));
            textView[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView[a].setMaxLines(1);
            textView[a].setSingleLine(true);
            textView[a].setMaxWidth(AndroidUtilities.dp(250));
            textView[a].setGravity(Gravity.LEFT | Gravity.TOP);
            textView[a].setPivotX(0);
            backgroundView.addView(textView[a], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 32, a == 0 ? 2 : 26, 10, 0));

            if (a == 0) {
                imageView[a].setAnimation(R.raw.ticks_single, 24, 24);
                textView[a].setText(LocaleController.getString("HintSent", R.string.HintSent));
            } else {
                imageView[a].setAnimation(R.raw.ticks_double, 24, 24);
                textView[a].setText(LocaleController.getString("HintRead", R.string.HintRead));
            }
            imageView[a].playAnimation();
        }

        arrowImageView = new ImageView(context);
        arrowImageView.setImageResource(R.drawable.tooltip_arrow);
        arrowImageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_gifSaveHintBackground), PorterDuff.Mode.MULTIPLY));
        addView(arrowImageView, LayoutHelper.createFrame(14, 6, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 0));
    }

    public float getBaseTranslationY() {
        return translationY;
    }

    public boolean showForMessageCell(ChatMessageCell cell, boolean animated) {
        if (hideRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hideRunnable);
            hideRunnable = null;
        }
        int[] position = new int[2];
        cell.getLocationInWindow(position);
        int top = position[1];
        View p = (View) getParent();
        p.getLocationInWindow(position);
        top -= position[1];

        View parentView = (View) cell.getParent();

        measure(MeasureSpec.makeMeasureSpec(1000, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(1000, MeasureSpec.AT_MOST));

        if (top <= getMeasuredHeight() + AndroidUtilities.dp(10)) {
            return false;
        }
        top += cell.getChecksY() + AndroidUtilities.dp(6);
        int centerX = cell.getChecksX() + AndroidUtilities.dp(5);

        int parentWidth = parentView.getMeasuredWidth();
        setTranslationY(translationY = top - getMeasuredHeight());
        int iconX = cell.getLeft() + centerX;
        int left = AndroidUtilities.dp(15);
        if (iconX > parentView.getMeasuredWidth() / 2) {
            int offset = parentWidth - getMeasuredWidth() - AndroidUtilities.dp(20);
            setTranslationX(offset);
            left += offset;
        } else {
            setTranslationX(0);
        }
        float arrowX = cell.getLeft() + centerX - left - arrowImageView.getMeasuredWidth() / 2;
        arrowImageView.setTranslationX(arrowX);
        if (iconX > parentView.getMeasuredWidth() / 2) {
            if (arrowX < AndroidUtilities.dp(10)) {
                float diff = arrowX - AndroidUtilities.dp(10);
                setTranslationX(getTranslationX() + diff);
                arrowImageView.setTranslationX(arrowX - diff);
            }
        } else {
            if (arrowX > getMeasuredWidth() - AndroidUtilities.dp(14 + 10)) {
                float diff = arrowX - getMeasuredWidth() + AndroidUtilities.dp(14 + 10);
                setTranslationX(diff);
                arrowImageView.setTranslationX(arrowX - diff);
            } else if (arrowX < AndroidUtilities.dp(10)) {
                float diff = arrowX - AndroidUtilities.dp(10);
                setTranslationX(getTranslationX() + diff);
                arrowImageView.setTranslationX(arrowX - diff);
            }
        }
        setPivotX(arrowX);
        setPivotY(getMeasuredHeight());

        messageCell = cell;
        if (animatorSet != null) {
            animatorSet.cancel();
            animatorSet = null;
        }

        setTag(1);
        setVisibility(VISIBLE);
        if (animated) {
            animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(this, View.ALPHA, 0.0f, 1.0f),
                    ObjectAnimator.ofFloat(this, View.SCALE_X, 0.0f, 1.0f),
                    ObjectAnimator.ofFloat(this, View.SCALE_Y, 0.0f, 1.0f)
            );
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animatorSet = null;
                    AndroidUtilities.runOnUIThread(hideRunnable = () -> hide(), 3000);
                }
            });
            animatorSet.setDuration(180);
            animatorSet.start();

            for (int a = 0; a < 2; a++) {
                final int num = a;
                textView[a].animate().scaleX(1.04f).scaleY(1.04f).setInterpolator(CubicBezierInterpolator.EASE_IN).setStartDelay((a == 0 ? 132 : 500) + 140).setDuration(100).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        textView[num].animate().scaleX(1.0f).scaleY(1.0f).setInterpolator(CubicBezierInterpolator.EASE_OUT).setStartDelay(0).setDuration(100).start();
                    }
                }).start();
            }
        } else {
            setAlpha(1.0f);
        }

        return true;
    }

    public void hide() {
        if (getTag() == null) {
            return;
        }
        setTag(null);
        if (hideRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hideRunnable);
            hideRunnable = null;
        }
        if (animatorSet != null) {
            animatorSet.cancel();
            animatorSet = null;
        }
        animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(this, View.ALPHA, 0.0f),
                ObjectAnimator.ofFloat(this, View.SCALE_X, 0.0f),
                ObjectAnimator.ofFloat(this, View.SCALE_Y, 0.0f)
        );
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setVisibility(View.INVISIBLE);
                currentView = null;
                messageCell = null;
                animatorSet = null;
            }
        });
        animatorSet.setDuration(180);
        animatorSet.start();
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
