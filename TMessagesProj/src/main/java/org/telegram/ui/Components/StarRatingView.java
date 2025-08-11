package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stars.StarsReactionsSheet;
import org.telegram.ui.Stories.recorder.HintView2;

public class StarRatingView extends View {
    public static final float MARGIN = 4;
    public static final float WIDTH = 145;
    public static final float HEIGHT = 19.333f;
    public static final float TEXT_SIZE = 11f;
    public static final float PADDING = 1.667f;
    public static final float PADDING_TEXT = 7f;

    private final StarsReactionsSheet.Particles particles = new StarsReactionsSheet.Particles(StarsReactionsSheet.Particles.TYPE_RADIAL, 30);
    private final Colors colors = new Colors();
    private final Paint fillingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path outerPath = new Path();
    private final Path interPath = new Path();
    private final Path interPathOut = new Path();

    private final AnimatedFloat isExpandedAnimator = new AnimatedFloat(this::onUpdateExpandedFactor, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat isVisibleAnimator = new AnimatedFloat(this::onUpdateVisibilityFactor, 380, CubicBezierInterpolator.EASE_OUT_QUINT);

    private boolean isExpanded;
    private boolean isVisible;
    private boolean needShowHintOnNextClick;

    private Delegate delegate;

    private Text titleText;
    private Text levelText;
    private Text targetLevelText;

    private float progress;
    private long stars;
    private int level;
    private long target;

    public StarRatingView(Context context) {
        super(context);
        isExpandedAnimator.set(isExpanded = false);
        isVisibleAnimator.set(isVisible = false);

        checkVisibility();
        buildText();
        setOnClickListener(this::onClick);
    }

    public interface Delegate {
        void onUpdateState(float expanded, float visibility);
        void showStarsHint(String text);
        void showLearnMore();
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    private void setExpanded(boolean isExpanded) {
        if (this.isExpanded == isExpanded) {
            return;
        }

        this.isExpanded = isExpanded;
        invalidate();
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    private void onClick(View v) {
        if (isVisibleAnimator.isInProgress() || isExpandedAnimator.isInProgress()) {
            return;
        }

        if (!isExpanded()) {
            setExpanded(true);
            needShowHintOnNextClick = true;
            if (delegate != null) {
                delegate.showLearnMore();
            }
        } else {
            if (needShowHintOnNextClick && delegate != null) {
                delegate.showStarsHint(getHintText());
            } else {
                setExpanded(false);
                if (collectibleHint != null && collectibleHint.shown()) {
                    collectibleHint.hide();
                }
            }
            needShowHintOnNextClick = false;
        }
    }

    public void set(TL_stars.Tl_starsRating starsRating) {
        isVisibleInternal = starsRating != null;
        checkVisibility();
        if (starsRating == null) {
            return;
        }

        level = starsRating.level;
        stars = starsRating.stars;
        target = starsRating.next_level_stars;

        if (starsRating.next_level_stars != 0) {
            progress = ((float) (starsRating.stars - starsRating.current_level_stars)) /
                (starsRating.next_level_stars - starsRating.current_level_stars);
        } else {
            progress = 1f;
        }

        buildText();
        invalidate();
    }

    private void buildText() {
        titleText = new Text(getString(R.string.ProfileLevelLevel), TEXT_SIZE, AndroidUtilities.bold());
        levelText = new Text(Integer.toString(level), TEXT_SIZE, AndroidUtilities.bold());
        if (target > 0) {
            targetLevelText = new Text(Integer.toString(level + 1), TEXT_SIZE, AndroidUtilities.bold());
        } else {
            targetLevelText = null;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        particles.setBounds(0, 0, dp(WIDTH) * 5 / 4, dp(HEIGHT) * 5 / 4);
        particles.setSpeed(0.3f);
        particles.setLifetime(2.5f);
    }

    private void build(float expanded) {
        float width = lerp(dp(HEIGHT), dp(WIDTH), expanded);
        tmpRectF.set(0, 0, width, dp(HEIGHT));

        outerPath.rewind();
        outerPath.addRoundRect(tmpRectF, tmpRectF.height() / 2, tmpRectF.height() / 2, Path.Direction.CW);
        outerPath.close();

        interPathOut.rewind();
        interPathOut.addRoundRect(tmpRectF, tmpRectF.height() / 2, tmpRectF.height() / 2, Path.Direction.CW);

        tmpRectF.inset(dp(PADDING), dp(PADDING));
        tmpRectF.right = lerp(
            tmpRectF.left + dp(HEIGHT) - dp(PADDING) * 2,
            tmpRectF.left + getInnerRectWidth(),
            expanded
        );

        float ins = Math.max(tmpRectF.height() - tmpRectF.width(), 0) / 2;
        tmpRectF.inset(0, ins);

        interPath.rewind();
        interPath.addRoundRect(tmpRectF, tmpRectF.height() / 2, tmpRectF.height() / 2, Path.Direction.CW);
        interPath.close();

        interPathOut.addRoundRect(tmpRectF, tmpRectF.height() / 2, tmpRectF.height() / 2, Path.Direction.CCW);
        interPathOut.close();
    }

    private boolean isVisibleInternal;
    private boolean isVisibleExternal;

    public void setVisibility(boolean isVisible) {
        isVisibleExternal = isVisible;
        checkVisibility();
    }

    private void checkVisibility() {
        isVisible = isVisibleExternal && isVisibleInternal;
        isVisibleAnimator.set(isVisible);

        if (!isVisible && isExpanded) {
            isExpandedAnimator.set(isExpanded = false);
        }

        setEnabled(isVisible);
        setClickable(isVisible);
        invalidate();
    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        float width = lerp(dp(HEIGHT), dp(WIDTH), isExpandedAnimator.get());
        tmpRectF.set(0, 0, width, dp(HEIGHT));
        tmpRectF.right += dp(MARGIN * 2);
        tmpRectF.bottom += dp(MARGIN * 2);

        return tmpRectF.contains(x, y) && super.dispatchTouchEvent(event);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        final float visibility = isVisibleAnimator.set(isVisible);
        final float expanded = isExpandedAnimator.set(isExpanded);

        canvas.save();
        canvas.translate((getMeasuredWidth() - dp(WIDTH)) / 2f, (getMeasuredHeight() - dp(HEIGHT)) / 2f);
        canvas.scale(visibility, visibility, 0, dp(HEIGHT) / 2f);

        build(expanded);

        fillingPaint.setColor(colors.backgroundColor);
        canvas.drawPath(outerPath, fillingPaint);

        fillingPaint.setColor(colors.fillingColor);

        if (expanded > 0 && progress < 1) {
            canvas.save();
            canvas.clipPath(outerPath);
            canvas.clipRect(
                (int) (dp(PADDING) + getInnerRectWidth() - dp(HEIGHT - PADDING) / 2f),
                0, dp(WIDTH), dp(HEIGHT)
            );

            canvas.scale(0.8f, 0.8f);

            particles.process();
            particles.draw(canvas, ColorUtils.setAlphaComponent(colors.fillingColor, (int)(255 * expanded)));
            invalidate();
            canvas.restore();
        }

        canvas.drawPath(interPath, fillingPaint);

        canvas.save();
        canvas.clipPath(interPath);
        drawTexts(canvas, expanded, colors.fillingTextColor, 1f);
        canvas.restore();

        canvas.save();
        canvas.clipPath(interPathOut);
        drawTexts(canvas, expanded, colors.backgroundTextColor, 1f);
        canvas.restore();

        /*if (true) {
            progress += 0.005;
            progress = progress % 1;
            invalidate();
        }*/

        canvas.restore();
    }

    private void drawTexts(Canvas canvas, float expanded, int color, float alpha) {
        if (alpha == 0) {
            return;
        }

        float xe1 = dp(PADDING + PADDING_TEXT);
        float xe2 = xe1 + (titleText.getWidth() + dp(3));

        float xne2 = (dp(HEIGHT) - levelText.getWidth()) / 2;
        float xne1 = xne2 - (titleText.getWidth() + dp(3));

        final float x1 = lerp(xne1, xe1, expanded);
        final float x2 = lerp(xne2, xe2, expanded);
        final float cy = dp(HEIGHT) / 2f;

        if (expanded > 0) {
            titleText.draw(canvas, x1, cy, color, alpha * expanded);
        }

        levelText.draw(canvas, x2, cy, color, alpha);

        if (targetLevelText != null && expanded > 0) {
            float width = lerp(dp(HEIGHT), dp(WIDTH), expanded);
            targetLevelText.draw(canvas, width - dp(PADDING_TEXT) - targetLevelText.getWidth(), cy, color, alpha * expanded);
        }
    }

    /* */

    private HintView2 collectibleHint;

    public HintView2 getHintView() {
        if (collectibleHint == null) {
            collectibleHint = new HintView2(getContext(), HintView2.DIRECTION_BOTTOM);
            // collectibleHintBackgroundColor = Theme.blendOver(status.center_color | 0xFF000000, Theme.multAlpha(status.pattern_color | 0xFF000000, .5f));
            collectibleHint.setPadding(dp(4), 0, dp(4), dp(2));
            collectibleHint.setFlicker(.66f, Theme.multAlpha(Color.WHITE /*status.text_color*/ | 0xFF000000, 0.5f));

            collectibleHint.setTextSize(9.33f);
            collectibleHint.setTextTypeface(AndroidUtilities.bold());
            collectibleHint.setDuration(-1);
            collectibleHint.setInnerPadding(4.66f + 1, 2.66f, 4.66f + 1, 2.66f);
            collectibleHint.setArrowSize(4, 2.66f);
            collectibleHint.setRoundingWithCornerEffect(false);
            collectibleHint.setRounding(16);
        }

        collectibleHint.setBgColor(colors.backgroundColor);
        collectibleHint.setTextColor(colors.backgroundTextColor);

        return collectibleHint;
    }

    public void hideHintView() {
        if (collectibleHint != null && collectibleHint.shown()) {
            collectibleHint.hide();
        }
    }

    public int getBgColor() {
        return colors.backgroundColor;
    }

    private String getHintText() {
        if (target == 0 && stars == 0) {
            return "0 / 0";
        }

        StringBuilder sb = new StringBuilder(15);
        sb.append(stars);
        if (target > 0) {
            sb.append(" / ");
            sb.append(target);
        }
        return sb.toString();
    }

    private float getInnerRectWidth() {
        float innerHeight = dp(HEIGHT - PADDING * 2);

        float minX = Math.max(innerHeight, getLeftTextWidth());
        float maxX = dp(WIDTH - PADDING * 2) - getRightTextWidth();

        return lerp(minX, maxX, progress);
    }

    public int getJointTranslateX() {
        return (int) (dp(PADDING) + getInnerRectWidth());
    }

    private static final RectF tmpRectF = new RectF();
    private static final float[] tmpRadii = new float[8];

    private float getLeftTextWidth() {
        return 0; // titleText.getWidth() + dp(3) + levelText.getWidth() + dp(PADDING_TEXT) * 2;
    }

    private float getRightTextWidth() {
        return 0; // targetLevelText != null ? targetLevelText.getWidth() + dp(PADDING_TEXT * 2) : 0;
    }

    private void onUpdateExpandedFactor() {
        invalidate();
        AndroidUtilities.runOnUIThread(() -> {
            if (delegate != null) {
                delegate.onUpdateState(getExpandedFactor(), getVisibilityFactor());
            }
        });
    }

    private void onUpdateVisibilityFactor() {
        invalidate();
        AndroidUtilities.runOnUIThread(() -> {
            if (delegate != null) {
                delegate.onUpdateState(getExpandedFactor(), getVisibilityFactor());
            }
        });
    }

    public float getVisibilityFactor() {
        return isVisibleAnimator.get();
    }

    public float getExpandedFactor() {
        return isExpandedAnimator.get();
    }


    private float lastTranslationX;
    private float lastTranslationY;

    @Override
    public void setTranslationX(float translationX) {
        if (Math.abs(lastTranslationX - translationX) > 0.0001f) {
            hideHintView();
        }
        super.setTranslationX(lastTranslationX = translationX);
    }

    @Override
    public void setTranslationY(float translationY) {
        if (Math.abs(lastTranslationY - translationY) > 0.0001f) {
            hideHintView();
        }
        super.setTranslationY(lastTranslationY = translationY);
    }


    /**/

    public void updateColors(MessagesController.PeerColor peerColor) {
        colors.update(peerColor);
        invalidate();
    }

    public void setParentExpanded(float parentExpanded) {
        colors.setParentExpanded(parentExpanded);
        invalidate();
    }

    private static class Colors {
        public MessagesController.PeerColor peerColor;

        public int backgroundColor = 0xFF000000;
        public int fillingColor = 0xFFFFFFFF;
        public int backgroundTextColor = 0xFFFFFFFF;
        public int fillingTextColor = 0xFF000000;
        private float parentExpanded;

        public void update(MessagesController.PeerColor peerColor) {
            this.peerColor = peerColor;
            if (peerColor == null) {
                reset();
                return;
            }

            int color1 = peerColor.getBgColor1(Theme.isCurrentThemeDark());
            int color2 = peerColor.getBgColor2(Theme.isCurrentThemeDark());

            int textColor = peerColor.textColor;
            if (Color.alpha(textColor) != 255) {
                textColor = AndroidUtilities.computePerceivedBrightness(backgroundColor) > .721f ? Color.BLACK : Color.WHITE;
            }

            backgroundColor = fillingTextColor = getTabsViewBackgroundColor(null, color2, color1, parentExpanded);
            backgroundTextColor = fillingColor = ColorUtils.blendARGB(textColor, Theme.getColor(Theme.key_actionBarDefaultTitle), parentExpanded);
            fillingTextColor |= 0xFF000000;
        }

        public void reset() {
            int color1 = Theme.getColor(Theme.key_actionBarDefault);
            int color2 = Theme.getColor(Theme.key_actionBarDefault);

            backgroundColor = fillingTextColor = getTabsViewBackgroundColor(null, color2, color1, parentExpanded);//Theme.blendOver(Theme.getColor(Theme.key_avatar_backgroundActionBarBlue), Theme.multAlpha(Color.BLACK, 0.1f));
            backgroundTextColor = fillingColor = Theme.getColor(Theme.key_actionBarDefaultTitle); //0xFFFFFFFF;
            fillingTextColor |= 0xFF000000;
        }

        public void setParentExpanded(float parentExpanded) {
            this.parentExpanded = parentExpanded;
            update(peerColor);
        }
    }

    public static int getTabsViewBackgroundColor(Theme.ResourcesProvider resourcesProvider, int color1, int color2, float parentExpanded) {
        return (ColorUtils.blendARGB(
                0xB0000000
            /*AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_actionBarDefault, resourcesProvider)) > .721f ?
                Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider) :
                Theme.adaptHSV(Theme.getColor(Theme.key_actionBarDefault, resourcesProvider), +.08f, -.08f)*/,
            AndroidUtilities.computePerceivedBrightness(ColorUtils.blendARGB(color1, color2, .75f)) > .721f ?
                Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon, resourcesProvider) :
                Theme.adaptHSV(ColorUtils.blendARGB(color1, color2, .75f), +.08f, -.08f),
            1f - parentExpanded
        ));
    }
}
