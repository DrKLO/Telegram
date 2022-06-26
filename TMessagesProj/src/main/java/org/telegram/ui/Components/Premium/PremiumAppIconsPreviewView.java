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
import org.telegram.ui.Cells.AppIconsSelectorCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LauncherIconController;

import java.util.ArrayList;
import java.util.List;

public class PremiumAppIconsPreviewView extends FrameLayout implements PagerHeaderView {
    private List<LauncherIconController.LauncherIcon> icons = new ArrayList<>();
    private AppIconsSelectorCell.AdaptiveIconImageView topIcon, bottomLeftIcon, bottomRightIcon;
    boolean isEmpty;

    public PremiumAppIconsPreviewView(Context context) {
        super(context);

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

        topIcon = newIconView(context, icons.get(0));
        bottomLeftIcon = newIconView(context, icons.get(1));
        bottomRightIcon = newIconView(context, icons.get(2));
    }

    private AppIconsSelectorCell.AdaptiveIconImageView newIconView(Context ctx, LauncherIconController.LauncherIcon icon) {
        AppIconsSelectorCell.AdaptiveIconImageView iconImageView = new AppIconsSelectorCell.AdaptiveIconImageView(ctx) {
            private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            {
                paint.setColor(Color.WHITE);
            }
            
            @Override
            public void draw(Canvas canvas) {
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(AppIconsSelectorCell.ICONS_ROUND_RADIUS), AndroidUtilities.dp(AppIconsSelectorCell.ICONS_ROUND_RADIUS), paint);
                
                super.draw(canvas);
            }
        };
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
        float progress = translationX / getMeasuredWidth();

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
    }
}
