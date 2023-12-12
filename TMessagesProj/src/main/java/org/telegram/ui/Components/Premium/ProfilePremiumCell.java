package org.telegram.ui.Components.Premium;

import android.content.Context;
import android.graphics.Canvas;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;

public class ProfilePremiumCell extends TextCell {

    StarParticlesView.Drawable drawable = new StarParticlesView.Drawable(6);

    public ProfilePremiumCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        drawable.size1 = 6;
        drawable.size2 = 6;
        drawable.size3 = 6;
        drawable.useGradient = true;
        drawable.speedScale = 3f;
        drawable.minLifeTime = 600;
        drawable.randLifeTime = 500;
        drawable.startFromCenter = true;
        drawable.type = StarParticlesView.Drawable.TYPE_SETTINGS;

        drawable.init();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        float cx = imageView.getX() + imageView.getWidth() / 2f;
        float cy = imageView.getPaddingTop() + imageView.getY() + imageView.getHeight() / 2f - AndroidUtilities.dp(3);
        drawable.rect.set(
                cx - AndroidUtilities.dp(4), cy - AndroidUtilities.dp(4),
                cx + AndroidUtilities.dp(4), cy + AndroidUtilities.dp(4)
        );
        if (changed) {
            drawable.resetPositions();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        drawable.onDraw(canvas);
        invalidate();
        super.dispatchDraw(canvas);
    }
}
