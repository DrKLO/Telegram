package org.telegram.ui.Components.voip;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

@SuppressLint("ViewConstructor")
public class RateCallLayout extends FrameLayout {

    private final RateCallContainer rateCallContainer;
    private final FrameLayout starsContainer;
    private final StarContainer[] startsViews = new StarContainer[5];
    private final VoIPBackgroundProvider backgroundProvider;
    private OnRateSelected onRateSelected;

    public interface OnRateSelected {
        void onRateSelected(int count);
    }

    public RateCallLayout(@NonNull Context context, VoIPBackgroundProvider backgroundProvider) {
        super(context);
        this.backgroundProvider = backgroundProvider;
        setWillNotDraw(false);
        rateCallContainer = new RateCallContainer(context, backgroundProvider);
        starsContainer = new FrameLayout(context);
        rateCallContainer.setVisibility(GONE);
        starsContainer.setVisibility(GONE);

        int starMargin = 4;
        for (int i = 0; i < 5; i++) {
            startsViews[i] = new StarContainer(context);
            startsViews[i].setAllStarsProvider(() -> startsViews);
            startsViews[i].setOnSelectedStar((x, y, starsCount) -> {
                if (starsCount >= 4) {
                    final RLottieImageView img = new RLottieImageView(context);
                    final int rateAnimationSize = 133;
                    final int rateAnimationSizeDp = AndroidUtilities.dp(rateAnimationSize);
                    img.setAnimation(R.raw.rate, rateAnimationSize, rateAnimationSize);
                    int[] location = new int[2];
                    getLocationOnScreen(location);
                    int viewX = location[0];
                    int viewY = location[1];
                    addView(img, LayoutHelper.createFrame(rateAnimationSize, rateAnimationSize));
                    img.setTranslationX((x - viewX) - (rateAnimationSizeDp / 2f));
                    img.setTranslationY((y - viewY) - (rateAnimationSizeDp / 2f));

                    img.setOnAnimationEndListener(() -> AndroidUtilities.runOnUIThread(() -> removeView(img)));
                    img.playAnimation();
                }
                if (onRateSelected != null) onRateSelected.onRateSelected(starsCount);
            }, i);
            starsContainer.addView(startsViews[i], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, i * (StarContainer.starSize + starMargin), 0, 0, 0));
        }

        addView(rateCallContainer, LayoutHelper.createFrame(300, 152, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));
        addView(starsContainer, LayoutHelper.createFrame((StarContainer.starSize * 5) + (starMargin * 4), 100, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 90, 0, 0));
    }

    public void show(OnRateSelected onRateSelected) {
        this.onRateSelected = onRateSelected;

        rateCallContainer.setVisibility(VISIBLE);
        starsContainer.setVisibility(VISIBLE);

        AnimatorSet backSet = new AnimatorSet();
        backSet.playTogether(
                ObjectAnimator.ofFloat(rateCallContainer, View.ALPHA, 0, 1f),
                ObjectAnimator.ofFloat(rateCallContainer, View.SCALE_X, 0.7f, 1f),
                ObjectAnimator.ofFloat(rateCallContainer, View.SCALE_Y, 0.7f, 1f),
                ObjectAnimator.ofFloat(rateCallContainer, View.TRANSLATION_Y, AndroidUtilities.dp(24), 0)
        );
        backSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
        backSet.setDuration(250);

        for (int i = 0; i < startsViews.length; i++) {
            AnimatorSet starSet = new AnimatorSet();
            startsViews[i].setAlpha(0f);
            starSet.playTogether(
                    ObjectAnimator.ofFloat(startsViews[i], View.ALPHA, 0, 1f),
                    ObjectAnimator.ofFloat(startsViews[i], View.SCALE_X, 0.3f, 1f),
                    ObjectAnimator.ofFloat(startsViews[i], View.SCALE_Y, 0.3f, 1f),
                    ObjectAnimator.ofFloat(startsViews[i], View.TRANSLATION_Y, AndroidUtilities.dp(30), 0)
            );
            starSet.setDuration(250);
            starSet.setStartDelay(i * 16L);
            starSet.start();
        }
        backSet.start();
    }

    public static class StarContainer extends FrameLayout {

        private static final int starSize = 37;

        interface OnSelectedStar {
            void onSelected(float x, float y, int starsCount);
        }

        interface AllStarsProvider {
            StarContainer[] getAllStartsViews();
        }

        public RLottieImageView defaultStar;
        public RLottieImageView selectedStar;
        private final Drawable rippleDrawable;
        private OnSelectedStar onSelectedStar;
        private AllStarsProvider allStarsProvider;
        private int pos = 0;

        public void setOnSelectedStar(OnSelectedStar onSelectedStar, int pos) {
            this.onSelectedStar = onSelectedStar;
            this.pos = pos;
        }

        public void setAllStarsProvider(AllStarsProvider allStarsProvider) {
            this.allStarsProvider = allStarsProvider;
        }

        public StarContainer(@NonNull Context context) {
            super(context);
            setWillNotDraw(false);
            defaultStar = new RLottieImageView(context);
            selectedStar = new RLottieImageView(context);

            defaultStar.setAnimation(R.raw.star_stroke, starSize, starSize);
            selectedStar.setAnimation(R.raw.star_fill, starSize, starSize);
            selectedStar.setAlpha(0f);
            addView(defaultStar, LayoutHelper.createFrame(starSize, starSize));
            addView(selectedStar, LayoutHelper.createFrame(starSize, starSize));
            rippleDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(starSize), 0, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.3f)));
            rippleDrawable.setCallback(this);
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            rippleDrawable.setBounds(0, 0, getWidth(), getHeight());
            rippleDrawable.draw(canvas);
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            if (rippleDrawable != null) rippleDrawable.setState(getDrawableState());
        }

        @Override
        public boolean verifyDrawable(@NonNull Drawable drawable) {
            return rippleDrawable == drawable || super.verifyDrawable(drawable);
        }

        @Override
        public void jumpDrawablesToCurrentState() {
            super.jumpDrawablesToCurrentState();
            if (rippleDrawable != null) rippleDrawable.jumpToCurrentState();
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (allStarsProvider != null) {
                        RateCallLayout.StarContainer[] starsViews = allStarsProvider.getAllStartsViews();
                        for (int i = 0; i <= pos; i++) {
                            RLottieImageView defaultStar = starsViews[i].defaultStar;
                            RLottieImageView selectedStar = starsViews[i].selectedStar;
                            defaultStar.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(250).start();
                            selectedStar.animate().alpha(1f).scaleX(0.8f).scaleY(0.8f).setDuration(250).start();
                        }

                        for (int i = pos + 1; i < starsViews.length; i++) {
                            RLottieImageView defaultStar = starsViews[i].defaultStar;
                            RLottieImageView selectedStar = starsViews[i].selectedStar;
                            defaultStar.animate().alpha(1f).scaleX(1.0f).scaleY(1.0f).setDuration(250).start();
                            selectedStar.animate().alpha(0f).scaleX(1.0f).scaleY(1.0f).setDuration(250).start();
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (allStarsProvider != null) {
                        RateCallLayout.StarContainer[] starsViews = allStarsProvider.getAllStartsViews();
                        for (int i = 0; i <= pos; i++) {
                            RLottieImageView defaultStar = starsViews[i].defaultStar;
                            RLottieImageView selectedStar = starsViews[i].selectedStar;
                            defaultStar.animate().scaleX(1.0f).scaleY(1.0f).setDuration(250).start();
                            selectedStar.animate().scaleX(1.0f).scaleY(1.0f).setDuration(250).start();
                        }
                    }
                    if (onSelectedStar != null) {
                        int[] location = new int[2];
                        getLocationOnScreen(location);
                        int viewX = location[0];
                        int viewY = location[1];
                        onSelectedStar.onSelected(viewX + (getWidth() / 2f), viewY + (getHeight() / 2f), this.pos + 1);
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    if (allStarsProvider != null) {
                        RateCallLayout.StarContainer[] starsViews = allStarsProvider.getAllStartsViews();
                        for (StarContainer starsView : starsViews) {
                            RLottieImageView defaultStar = starsView.defaultStar;
                            RLottieImageView selectedStar = starsView.selectedStar;
                            defaultStar.animate().alpha(1f).scaleX(1.0f).scaleY(1.0f).setDuration(250).start();
                            selectedStar.animate().alpha(0f).scaleX(1.0f).scaleY(1.0f).setDuration(250).start();
                        }
                    }
                    break;
            }
            return super.dispatchTouchEvent(event);
        }
    }

    public static class RateCallContainer extends FrameLayout {

        private final TextView titleTextView;
        private final TextView messageTextView;
        private final VoIPBackgroundProvider backgroundProvider;
        private final RectF bgRect = new RectF();

        public RateCallContainer(@NonNull Context context, VoIPBackgroundProvider backgroundProvider) {
            super(context);
            this.backgroundProvider = backgroundProvider;
            backgroundProvider.attach(this);
            setWillNotDraw(false);
            titleTextView = new TextView(context);
            titleTextView.setTextColor(Color.WHITE);
            titleTextView.setText(LocaleController.getString("VoipRateCallTitle", R.string.VoipRateCallTitle));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            titleTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            messageTextView = new TextView(context);
            messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            messageTextView.setTextColor(Color.WHITE);
            messageTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            messageTextView.setText(LocaleController.getString("VoipRateCallDescription", R.string.VoipRateCallDescription));

            addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 0, 24, 0, 0));
            addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 0, 50, 0, 0));
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            bgRect.set(0, 0, getWidth(), getHeight());
            backgroundProvider.setDarkTranslation(getX() + ((View) getParent()).getX(), getY() + ((View) getParent()).getY());
            canvas.drawRoundRect(bgRect, dp(28), dp(28), backgroundProvider.getDarkPaint());
            super.dispatchDraw(canvas);
        }
    }
}
