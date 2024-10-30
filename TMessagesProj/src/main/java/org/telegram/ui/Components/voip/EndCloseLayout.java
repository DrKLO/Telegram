package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class EndCloseLayout extends FrameLayout {
    private final EndCloseView endCloseView;
    private final TransitionSet transitionSet;
    private boolean isClosedState = false;

    public EndCloseLayout(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);
        endCloseView = new EndCloseView(context);
        this.addView(endCloseView, LayoutHelper.createFrame(52, 52, Gravity.RIGHT));

        transitionSet = new TransitionSet();
        transitionSet.setOrdering(TransitionSet.ORDERING_TOGETHER);
        transitionSet.addTransition(new ChangeBounds() {

            public void captureStartValues(TransitionValues transitionValues) {
                super.captureStartValues(transitionValues);
                if (transitionValues.view instanceof EndCloseView) {
                    int color = ((EndCloseView) transitionValues.view).backColor;
                    int round = ((EndCloseView) transitionValues.view).round;
                    int declineCallAlpha = ((EndCloseView) transitionValues.view).callDeclineAlpha;
                    int closeTextAlpha = ((EndCloseView) transitionValues.view).closeTextAlpha;
                    transitionValues.values.put("back_color_end_close", color);
                    transitionValues.values.put("round_end_close", round);
                    transitionValues.values.put("decline_call_alpha_end_close", declineCallAlpha);
                    transitionValues.values.put("close_text_alpha_end_close", closeTextAlpha);
                }
            }

            public void captureEndValues(TransitionValues transitionValues) {
                super.captureEndValues(transitionValues);
                if (transitionValues.view instanceof EndCloseView) {
                    int color = ((EndCloseView) transitionValues.view).backColor;
                    int round = ((EndCloseView) transitionValues.view).round;
                    int declineCallAlpha = ((EndCloseView) transitionValues.view).callDeclineAlpha;
                    int closeTextAlpha = ((EndCloseView) transitionValues.view).closeTextAlpha;
                    transitionValues.values.put("back_color_end_close", color);
                    transitionValues.values.put("round_end_close", round);
                    transitionValues.values.put("decline_call_alpha_end_close", declineCallAlpha);
                    transitionValues.values.put("close_text_alpha_end_close", closeTextAlpha);
                }
            }

            @Override
            public Animator createAnimator(ViewGroup sceneRoot, TransitionValues startValues, TransitionValues endValues) {
                if (startValues != null && endValues != null && startValues.view instanceof EndCloseView) {
                    AnimatorSet animatorSet = new AnimatorSet();
                    Animator animator = super.createAnimator(sceneRoot, startValues, endValues);
                    if (animator != null) {
                        animatorSet.playTogether(animator);
                    }
                    final Integer startTextColor = (Integer) startValues.values.get("back_color_end_close");
                    final Integer endTextColor = (Integer) endValues.values.get("back_color_end_close");
                    final Integer startRound = (Integer) startValues.values.get("round_end_close");
                    final Integer endRound = (Integer) endValues.values.get("round_end_close");
                    final Integer startDeclineCallAlpha = (Integer) startValues.values.get("decline_call_alpha_end_close");
                    final Integer endDeclineCallAlpha = (Integer) endValues.values.get("decline_call_alpha_end_close");
                    final Integer startCloseTextAlpha = (Integer) startValues.values.get("close_text_alpha_end_close");
                    final Integer endCloseTextAlpha = (Integer) endValues.values.get("close_text_alpha_end_close");

                    ValueAnimator colorAnimator = new ValueAnimator();
                    colorAnimator.setIntValues(startTextColor, endTextColor);
                    colorAnimator.setEvaluator(new ArgbEvaluator());
                    colorAnimator.addUpdateListener(a -> ((EndCloseView) startValues.view).backColor = (int) a.getAnimatedValue());
                    animatorSet.playTogether(colorAnimator);

                    ValueAnimator roundAnimator = ValueAnimator.ofInt(startRound, endRound);
                    roundAnimator.addUpdateListener(animation -> ((EndCloseView) startValues.view).round = (int) animation.getAnimatedValue());
                    animatorSet.playTogether(roundAnimator);

                    ValueAnimator declineCallAlphaAnimator = ValueAnimator.ofInt(startDeclineCallAlpha, endDeclineCallAlpha, endDeclineCallAlpha, endDeclineCallAlpha, endDeclineCallAlpha, endDeclineCallAlpha, endDeclineCallAlpha);
                    declineCallAlphaAnimator.addUpdateListener(animation -> ((EndCloseView) startValues.view).callDeclineAlpha = (int) animation.getAnimatedValue());
                    animatorSet.playTogether(declineCallAlphaAnimator);

                    ValueAnimator closeTextAlphaAnimator = ValueAnimator.ofInt(startCloseTextAlpha, startCloseTextAlpha, (int) (endCloseTextAlpha * 0.25f), (int) (endCloseTextAlpha * 0.5f), (int) (endCloseTextAlpha * 0.75f), endCloseTextAlpha);
                    closeTextAlphaAnimator.addUpdateListener(animation -> ((EndCloseView) startValues.view).closeTextAlpha = (int) animation.getAnimatedValue());
                    animatorSet.playTogether(closeTextAlphaAnimator);

                    animatorSet.addListener(new AnimatorListenerAdapter() {

                        @Override
                        public void onAnimationStart(Animator animation) {
                            super.onAnimationStart(animation);
                            startValues.view.setEnabled(false);
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            startValues.view.setEnabled(true);
                        }
                    });
                    return animatorSet;
                } else {
                    return super.createAnimator(sceneRoot, startValues, endValues);
                }
            }
        });
        transitionSet.setDuration(500);
        transitionSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
    }

    public EndCloseView getEndCloseView() {
        return endCloseView;
    }

    public void switchToClose(OnClickListener onClickListener, boolean animate) {
        if (isClosedState) return;
        isClosedState = true;

        if (animate) {
            TransitionManager.beginDelayedTransition(this, transitionSet);
        }

        endCloseView.closeTextAlpha = 255;
        endCloseView.backColor = 0xFFffffff;
        endCloseView.callDeclineAlpha = 0;
        endCloseView.round = AndroidUtilities.dp(8);
        ViewGroup.LayoutParams lp = endCloseView.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        endCloseView.setLayoutParams(lp);
        AndroidUtilities.runOnUIThread(() -> endCloseView.setOnClickListener(onClickListener), 500);
    }

    static class EndCloseView extends View {
        private Drawable rippleDrawable;
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaintMask = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF backgroundRect = new RectF();
        private final Drawable callDeclineDrawable;
        private final String closeText;
        public int backColor = 0xFFf4606c;
        public int round = AndroidUtilities.dp(26);
        public int callDeclineAlpha = 255;
        public int closeTextAlpha = 0;

        public EndCloseView(@NonNull Context context) {
            super(context);
            callDeclineDrawable = ContextCompat.getDrawable(getContext(), R.drawable.calls_decline).mutate();
            callDeclineDrawable.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
            textPaintMask.setTextSize(AndroidUtilities.dp(18));
            textPaintMask.setTypeface(AndroidUtilities.bold());
            textPaintMask.setTextAlign(Paint.Align.CENTER);
            textPaintMask.setColor(0xff000000);
            textPaintMask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            textPaint.setTextSize(AndroidUtilities.dp(18));
            textPaint.setTypeface(AndroidUtilities.bold());
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.BLACK);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            setClickable(true);
            closeText = LocaleController.getString(R.string.Close);
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            if (rippleDrawable != null) {
                rippleDrawable.setState(getDrawableState());
            }
        }

        @Override
        public boolean verifyDrawable(@NonNull Drawable drawable) {
            return rippleDrawable == drawable || super.verifyDrawable(drawable);
        }

        @Override
        public void jumpDrawablesToCurrentState() {
            super.jumpDrawablesToCurrentState();
            if (rippleDrawable != null) {
                rippleDrawable.jumpToCurrentState();
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (!isEnabled()) {
                return false;
            }
            return super.dispatchTouchEvent(ev);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            backgroundPaint.setColor(backColor);
            backgroundRect.set(0, 0, getWidth(), getHeight());
            canvas.drawRoundRect(backgroundRect, round, round, backgroundPaint);

            callDeclineDrawable.setBounds(
                    (int) (cx - callDeclineDrawable.getIntrinsicWidth() / 2f), (int) (cy - callDeclineDrawable.getIntrinsicHeight() / 2),
                    (int) (cx + callDeclineDrawable.getIntrinsicWidth() / 2), (int) (cy + callDeclineDrawable.getIntrinsicHeight() / 2)
            );
            callDeclineDrawable.setAlpha(callDeclineAlpha);
            callDeclineDrawable.draw(canvas);

            textPaintMask.setAlpha(closeTextAlpha);
            int maxDarkAlpha = (int) (255 * 0.15f);
            textPaint.setAlpha(maxDarkAlpha * (closeTextAlpha / 255));
            canvas.drawText(closeText, cx, cy + AndroidUtilities.dp(6), textPaintMask);
            canvas.drawText(closeText, cx, cy + AndroidUtilities.dp(6), textPaint);

            if (rippleDrawable == null) {
                rippleDrawable = Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector), 8, 8);
                rippleDrawable.setCallback(this);
            }
            rippleDrawable.setBounds(0, 0, getWidth(), getHeight());
            rippleDrawable.draw(canvas);
        }
    }
}


