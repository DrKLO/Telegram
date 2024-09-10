package org.telegram.ui.Stories;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;

import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class StoriesIntro extends FrameLayout {

    private final ArrayList<StoriesIntroItemView> items;
    private ValueAnimator valueAnimator;
    private int prev = -1;
    private int current = 0;
    private final Runnable startItemAnimationRunnable = () -> {
        updateCurrentAnimatedItem();
        startAnimation(true);
    };

    public StoriesIntro(Context context, View parentView) {
        super(context);
        ImageView backgroundImageView = new ImageView(context);
        addView(backgroundImageView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        View scrim = new View(context);
        scrim.setBackgroundColor(0x64000000); // 40%
        addView(scrim, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(0, AndroidUtilities.dp(48), 0, AndroidUtilities.dp(48));
        linearLayout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView header = new TextView(context);
        header.setTextColor(Color.WHITE);
        header.setTypeface(AndroidUtilities.bold());
        header.setText(LocaleController.getString(R.string.StoriesIntroHeader));
        header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        linearLayout.addView(header, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView subHeader = new TextView(context);
        subHeader.setTextColor(0x96FFFFFF); // 60%
        subHeader.setText(LocaleController.getString(R.string.StoriesIntroSubHeader));
        subHeader.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subHeader.setGravity(Gravity.CENTER_HORIZONTAL);
        linearLayout.addView(subHeader, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 68, 8, 68, 36));

        items = new ArrayList<>(4);
        items.add(new StoriesIntroItemView(context, R.raw.stories_intro_go_forward, LocaleController.getString(R.string.StoriesIntroGoForwardHeader), LocaleController.getString(R.string.StoriesIntroGoForwardSubHeader)));
        items.add(new StoriesIntroItemView(context, R.raw.stories_intro_pause, LocaleController.getString(R.string.StoriesIntroPauseAndSeekHeader), LocaleController.getString(R.string.StoriesIntroPauseAndSeekSubHeader)));
        items.add(new StoriesIntroItemView(context, R.raw.stories_intro_go_back, LocaleController.getString(R.string.StoriesIntroGoBackHeader), LocaleController.getString(R.string.StoriesIntroGoBackSubHeader)));
        items.add(new StoriesIntroItemView(context, R.raw.stories_intro_go_to_next, LocaleController.getString(R.string.StoriesIntroGoToNextAuthorHeader), LocaleController.getString(R.string.StoriesIntroGoToNextAuthorSubHeader)));

        // adjust the width for small devices
        int storiesIntroItemViewWidth = parentView.getMeasuredWidth() - AndroidUtilities.dp(100);
        for (StoriesIntroItemView storiesIntroItemView : items) {
            int w = storiesIntroItemView.getRequiredWidth();
            if (w > storiesIntroItemViewWidth) {
                storiesIntroItemViewWidth = w;
            }
        }
        if (storiesIntroItemViewWidth + AndroidUtilities.dp(8) > parentView.getMeasuredWidth()) {
            storiesIntroItemViewWidth = parentView.getMeasuredWidth() - AndroidUtilities.dp(8);
        }
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(storiesIntroItemViewWidth, AndroidUtilities.dp(64));
        layoutParams.setMargins(0, AndroidUtilities.dp(5), 0, AndroidUtilities.dp(5));
        for (StoriesIntroItemView storiesIntroItemView : items) {
            linearLayout.addView(storiesIntroItemView, layoutParams);
        }
        TextView bottomText = new TextView(context);
        bottomText.setTextColor(Color.WHITE);
        bottomText.setTypeface(AndroidUtilities.bold());
        bottomText.setText(LocaleController.getString(R.string.StoriesIntroDismiss));
        bottomText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        linearLayout.addView(bottomText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 73, 0, 0));
        addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        BitmapDrawable bitmap = new BitmapDrawable(getContext().getResources(), AndroidUtilities.makeBlurBitmap(parentView, 12f, 10));
        bitmap.setColorFilter(new PorterDuffColorFilter(0xdd000000, PorterDuff.Mode.DST_OVER));
        backgroundImageView.setImageDrawable(bitmap);

        // adjusts for small devices
        getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    public void onGlobalLayout() {
                        int[] bottomTextLocation = new int[2];
                        bottomText.getLocationOnScreen(bottomTextLocation);
                        if (bottomTextLocation[1] + AndroidUtilities.dp(24) > parentView.getMeasuredHeight()) {
                            bottomText.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 13, 0, 0));
                            subHeader.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 68, 8, 68, 13));
                            requestLayout();
                        }
                        getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                }
        );
    }

    void startAnimation(boolean delay) {
        if (valueAnimator != null) {
            valueAnimator.cancel();
        }
        valueAnimator = ValueAnimator.ofFloat(0f, 1f);
        if (delay) {
            valueAnimator.setStartDelay(50L);
        }
        valueAnimator.setDuration(350L);
        valueAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        valueAnimator.getCurrentPlayTime();
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                items.get(current).startIconAnimation();
            }
        });
        valueAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            items.get(current).setProgress(progress);
            if (prev != -1) {
                items.get(prev).setProgress(1f - progress);
            }
        });
        valueAnimator.start();
        AndroidUtilities.runOnUIThread(startItemAnimationRunnable, items.get(current).getLottieAnimationDuration() + 100);
    }

    public void stopAnimation() {
        AndroidUtilities.cancelRunOnUIThread(startItemAnimationRunnable);
        if (valueAnimator != null) {
            valueAnimator.cancel();
            valueAnimator = null;
        }
        if (prev != -1) {
            items.get(prev).stopAnimation();
        }
        items.get(current).stopAnimation();
        updateCurrentAnimatedItem();
    }

    private void updateCurrentAnimatedItem() {
        current++;
        if (current >= items.size()) {
            current = 0;
        }
        prev++;
        if (prev >= items.size()) {
            prev = 0;
        }
    }

    @SuppressLint("ViewConstructor")
    static class StoriesIntroItemView extends View {

        private final String header;
        private final String subHeader;
        private final RLottieDrawable lottieDrawable;
        private final Paint backgroundPaint;
        private final TextPaint headerTextPaint;
        private final TextPaint subHeaderTextPaint;
        private final RectF rectF;
        private float progress;
        private final Rect textBounds = new Rect();

        public StoriesIntroItemView(Context context, int rawRes, String header, String subHeader) {
            super(context);
            this.header = header;
            this.subHeader = subHeader;
            lottieDrawable = new RLottieDrawable(rawRes, "" + rawRes, AndroidUtilities.dp(36), AndroidUtilities.dp(36), true, null);
            lottieDrawable.setAutoRepeat(1);
            lottieDrawable.setMasterParent(this);

            backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            backgroundPaint.setColor(0x16D8D8D8);

            headerTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            headerTextPaint.setColor(Color.WHITE);
            headerTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics()));
            headerTextPaint.setTypeface(AndroidUtilities.bold());

            subHeaderTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            subHeaderTextPaint.setColor(0x96FFFFFF);

            subHeaderTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, getResources().getDisplayMetrics()));
            rectF = new RectF();
        }

        public int getRequiredWidth() {
            headerTextPaint.getTextBounds(header, 0, header.length(), textBounds);
            int headerWidth = textBounds.width();
            subHeaderTextPaint.getTextBounds(subHeader, 0, subHeader.length(), textBounds);
            int subHeaderWidth = textBounds.width();
            return AndroidUtilities.dp(88) + AndroidUtilities.dp(8) + Math.max(headerWidth, subHeaderWidth);
        }

        public long getLottieAnimationDuration() {
            return lottieDrawable.getDuration() * 2;
        }

        public void startAnimation() {
            lottieDrawable.start();
            progress = 1f;
            invalidate();
        }

        public void stopAnimation() {
            lottieDrawable.setCurrentFrame(0);
            lottieDrawable.stop();
            progress = 0f;
            invalidate();
        }

        public void startIconAnimation() {
            lottieDrawable.setAutoRepeatCount(2);
            lottieDrawable.start();
        }

        public void setProgress(float progress) {
            this.progress = progress;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int iconCx = AndroidUtilities.dp(40);
            int iconCy = getMeasuredHeight() / 2;
            int size = AndroidUtilities.dp(36);
            int left = iconCx - size / 2;
            int top = iconCy - size / 2;
            int right = left + size;
            int bottom = top + size;
            lottieDrawable.setBounds(left, top, right, bottom);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int iconCx = AndroidUtilities.dp(40);
            int iconCy = getMeasuredHeight() / 2;
            int size = (int) (AndroidUtilities.dp(36) + AndroidUtilities.dp(8) * progress);
            int left = iconCx - size / 2;
            int top = iconCy - size / 2;
            int right = left + size;
            int bottom = top + size;
            lottieDrawable.setBounds(left, top, right, bottom);
            lottieDrawable.draw(canvas);
            if (progress > 0f) {
                float backgroundOffset = AndroidUtilities.dpf2(4) * (1f - progress);
                rectF.set(backgroundOffset, backgroundOffset, getMeasuredWidth() - backgroundOffset * 2, getMeasuredHeight() - backgroundOffset * 2);
                backgroundPaint.setAlpha((int) (30 * progress));
                canvas.drawRoundRect(rectF, AndroidUtilities.dpf2(12f), AndroidUtilities.dpf2(12f), backgroundPaint);
                canvas.save();
                canvas.scale(1 + 0.05f * progress, 1 + 0.05f * progress, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);
            }
            canvas.drawText(header, AndroidUtilities.dpf2(80), getMeasuredHeight() / 2f - AndroidUtilities.dpf2(4), headerTextPaint);
            canvas.drawText(subHeader, AndroidUtilities.dpf2(80), getMeasuredHeight() / 2f + AndroidUtilities.dpf2(18), subHeaderTextPaint);
            if (progress > 0) {
                canvas.restore();
            }
        }
    }
}
