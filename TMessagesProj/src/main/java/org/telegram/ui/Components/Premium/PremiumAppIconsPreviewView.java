package org.telegram.ui.Components.Premium;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.AppIconsSelectorCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LauncherIconController;

import java.util.ArrayList;
import java.util.List;

public class PremiumAppIconsPreviewView extends FrameLayout implements PagerHeaderView {

    private final Theme.ResourcesProvider resourcesProvider;
    private List<LauncherIconController.LauncherIcon> icons = new ArrayList<>();
    private AdaptiveIconImageView topIcon, bottomLeftIcon, bottomRightIcon;
    boolean isEmpty;

    public PremiumAppIconsPreviewView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.resourcesProvider = resourcesProvider;

        for (LauncherIconController.LauncherIcon icon : LauncherIconController.LauncherIcon.values()) {
            if (icon.premium) {
                icons.add(icon);
            }
            if (icons.size() == 3) {
                break;
            }
        }

        if (icons.size() < 3) {
            FileLog.e(new IllegalArgumentException("There should be at least 3 premium icons!"));
            isEmpty = true;
            return;
        }

        topIcon = newIconView(context, 0);
        bottomLeftIcon = newIconView(context, 1);
        bottomRightIcon = newIconView(context, 2);
        setClipChildren(false);
    }

    private AdaptiveIconImageView newIconView(Context ctx, int i) {
        LauncherIconController.LauncherIcon icon = icons.get(i);

        AdaptiveIconImageView iconImageView = new AdaptiveIconImageView(ctx, i);
        iconImageView.setLayoutParams(LayoutHelper.createFrame(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER, 0, 52, 0, 0));
        iconImageView.setForeground(icon.foreground);
        iconImageView.setBackgroundResource(icon.background);
        iconImageView.setPadding(AndroidUtilities.dp(8));
        iconImageView.setBackgroundOuterPadding(AndroidUtilities.dp(32));
        addView(iconImageView);
        return iconImageView;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (isEmpty) {
            return;
        }
        int minSide = Math.min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        int size = AndroidUtilities.dp(76);
        LayoutParams params = (LayoutParams) topIcon.getLayoutParams();
        params.width = params.height = size;
        params.bottomMargin = (int) (size + minSide * 0.1f);

        params = (LayoutParams) bottomLeftIcon.getLayoutParams();
        params.width = params.height = size;
        params.rightMargin = (int) (size * 0.95f);

        params = (LayoutParams) bottomRightIcon.getLayoutParams();
        params.width = params.height = size;
        params.leftMargin = (int) (size * 0.95f);
    }

    @Override
    public void setOffset(float translationX) {
        if (isEmpty) {
            return;
        }
        float progress = Math.abs(translationX / getMeasuredWidth());

        float rightProgress = CubicBezierInterpolator.EASE_IN.getInterpolation(progress);
        bottomRightIcon.setTranslationX(rightProgress * (getRight() - bottomRightIcon.getRight() + bottomRightIcon.getWidth() * 1.5f + AndroidUtilities.dp(32)));
        bottomRightIcon.setTranslationY(rightProgress * AndroidUtilities.dp(16));
        float scale = AndroidUtilities.lerp(1f, 1.5f, rightProgress);
        scale = Utilities.clamp(scale, 1f, 0);
        bottomRightIcon.setScaleX(scale);
        bottomRightIcon.setScaleY(scale);


        topIcon.setTranslationY(progress * (getTop() - topIcon.getTop() - topIcon.getHeight() * 1.8f - AndroidUtilities.dp(32)));
        topIcon.setTranslationX(progress * AndroidUtilities.dp(16));
        scale = AndroidUtilities.lerp(1f, 1.8f, progress);
        scale = Utilities.clamp(scale, 1f, 0);
        topIcon.setScaleX(scale);
        topIcon.setScaleY(scale);


        float leftProgress = CubicBezierInterpolator.EASE_OUT.getInterpolation(progress);
        bottomLeftIcon.setTranslationX(leftProgress * (getLeft() - bottomLeftIcon.getLeft() - bottomLeftIcon.getWidth() * 2.5f + AndroidUtilities.dp(32)));
        bottomLeftIcon.setTranslationY(leftProgress * (getBottom() - bottomLeftIcon.getBottom() + bottomLeftIcon.getHeight() * 2.5f + AndroidUtilities.dp(32)));
        scale = AndroidUtilities.lerp(1f, 2.5f, progress);
        scale = Utilities.clamp(scale, 1f, 0);
        bottomLeftIcon.setScaleX(scale);
        bottomLeftIcon.setScaleY(scale);

        float p = progress < 0.4f ? progress / 0.4f : 1f;
        bottomRightIcon.particlesScale = p;
        topIcon.particlesScale = p;
        bottomLeftIcon.particlesScale = p;
    }

    private class AdaptiveIconImageView extends AppIconsSelectorCell.AdaptiveIconImageView {

        StarParticlesView.Drawable drawable = new StarParticlesView.Drawable(20);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float particlesScale;

        public AdaptiveIconImageView(Context ctx, int i) {
            super(ctx);
            drawable.size1 = 12;
            drawable.size2 = 8;
            drawable.size3 = 6;
            if (i == 1) {
                drawable.type = StarParticlesView.TYPE_APP_ICON_REACT;
            }  if (i == 0) {
                drawable.type = StarParticlesView.TYPE_APP_ICON_STAR_PREMIUM;
            }
            drawable.resourcesProvider = resourcesProvider;
            drawable.colorKey = Theme.key_premiumStartSmallStarsColor2;
            drawable.init();
            paint.setColor(Color.WHITE);
        }

        @Override
        public void draw(Canvas canvas) {
            int outBoundOffset = AndroidUtilities.dp(10);
            drawable.excludeRect.set(AndroidUtilities.dp(5), AndroidUtilities.dp(5), getMeasuredWidth() - AndroidUtilities.dp(5), getMeasuredHeight() - AndroidUtilities.dp(5));
            drawable.rect.set(-outBoundOffset, -outBoundOffset, getWidth() + outBoundOffset, getHeight() + outBoundOffset);
            canvas.save();
            canvas.scale(1f - particlesScale, 1f - particlesScale, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);
            drawable.onDraw(canvas);
            canvas.restore();
            invalidate();
            AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(AppIconsSelectorCell.ICONS_ROUND_RADIUS), AndroidUtilities.dp(AppIconsSelectorCell.ICONS_ROUND_RADIUS), paint);

            super.draw(canvas);
        }
    }
}
