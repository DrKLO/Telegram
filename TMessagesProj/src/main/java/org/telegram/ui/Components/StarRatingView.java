package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.Theme;

public class StarRatingView extends View {
    private final BadgeLevelDrawable drawable;
    private final Colors colors = new Colors();

    private final AnimatedFloat isVisibleAnimator = new AnimatedFloat(this::onUpdateVisibilityFactor, 380, CubicBezierInterpolator.EASE_OUT_QUINT);
    private boolean isVisible;

    private Delegate delegate;

    public StarRatingView(Context context) {
        super(context);

        drawable = new BadgeLevelDrawable(context);
        drawable.setCallback(this);
        isVisibleAnimator.set(isVisible = false);

        checkVisibility();
    }

    public void setResourcesProvider(Theme.ResourcesProvider resourcesProvider) {
        this.colors.resourcesProvider = resourcesProvider;
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || who == drawable;
    }

    public interface Delegate {
        void onUpdateState(float visibility);
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void set(TL_stars.Tl_starsRating starsRating) {
        isVisibleInternal = starsRating != null;
        checkVisibility();
        if (starsRating == null) {
            return;
        }

        drawable.setBadgeLevel(starsRating.level, true);
        invalidate();
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

        setEnabled(isVisible);
        setClickable(isVisible);
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        final float visibility = isVisibleAnimator.set(isVisible);
        final int x = (getMeasuredWidth() - dp(24)) / 2;
        final int y = (getMeasuredHeight() - dp(24)) / 2;

        canvas.save();
        canvas.translate(x, y);
        canvas.scale(visibility, visibility, 0, dp(12));

        drawable.setBounds(0, 0, dp(24), dp(24));
        drawable.setOuterColor(colors.backgroundColor);
        drawable.setInnerColor(colors.fillingColor);
        drawable.setTextColor(colors.fillingTextColor);
        drawable.draw(canvas);

        canvas.restore();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        drawable.debugUpdateStart();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        drawable.debugUpdateStop();
    }

    private void onUpdateVisibilityFactor() {
        invalidate();
        AndroidUtilities.runOnUIThread(() -> {
            if (delegate != null) {
                delegate.onUpdateState(getVisibilityFactor());
            }
        });
    }

    public float getVisibilityFactor() {
        return isVisibleAnimator.get();
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
        private Theme.ResourcesProvider resourcesProvider;

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
            int textColor = AndroidUtilities.computePerceivedBrightness(backgroundColor) > .721f ? Color.BLACK : Color.WHITE;

            backgroundColor = fillingTextColor = getTabsViewBackgroundColor(resourcesProvider, color2, color1, parentExpanded);
            backgroundTextColor = fillingColor = ColorUtils.blendARGB(textColor, Theme.getColor(Theme.key_actionBarDefaultTitle, resourcesProvider), parentExpanded);
            fillingTextColor |= 0xFF000000;
        }

        public void reset() {
            int color1 = Theme.getColor(Theme.key_actionBarDefault, resourcesProvider);
            int color2 = Theme.getColor(Theme.key_actionBarDefault, resourcesProvider);

            backgroundColor = fillingTextColor = getTabsViewBackgroundColor(resourcesProvider, color2, color1, parentExpanded);
            backgroundTextColor = fillingColor = Theme.getColor(Theme.key_actionBarDefaultTitle, resourcesProvider);
            fillingTextColor |= 0xFF000000;
        }

        public void setParentExpanded(float parentExpanded) {
            this.parentExpanded = parentExpanded;
            update(peerColor);
        }
    }

    public static int getTabsViewBackgroundColor(Theme.ResourcesProvider resourcesProvider, int color1, int color2, float parentExpanded) {
        return (ColorUtils.blendARGB(0x24000000,
            AndroidUtilities.computePerceivedBrightness(ColorUtils.blendARGB(color1, color2, .75f)) > .721f ?
                Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon, resourcesProvider) :
                Theme.adaptHSV(ColorUtils.blendARGB(color1, color2, .75f), +.08f, -.08f),
            1f - parentExpanded
        ));
    }
}
