package org.telegram.ui.Stories;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.blur3.StrokeDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;

public class CommentButton extends FrameLayout {

    private final FrameLayout layout;
    private final ImageView commentImage;
    private final ImageView arrowImage;

    private final AnimatedTextView.AnimatedTextDrawable countText;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CommentButton(Context context, BlurredBackgroundColorProvider colorProvider) {
        super(context);
        ScaleStateListAnimator.apply(this);

        countText = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
        countText.setTextColor(0xFF697278);
        countText.setTextSize(dp(9));
        countText.setCallback(this);
        countText.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));
        countText.setAllowCancel(true);
        backgroundPaint.setColor(0xFF20242A);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        layout = new FrameLayout(context);
        final StrokeDrawable background = new StrokeDrawable();
        background.setColorProvider(colorProvider);
        background.setBackgroundColor(0xFF20242A);
        background.setPadding(dp(1));
        layout.setBackground(background);
        addView(layout, LayoutHelper.createFrame(40, 40, Gravity.CENTER));

        commentImage = new ImageView(context);
        commentImage.setImageResource(R.drawable.menu_comments);
        commentImage.setColorFilter(new PorterDuffColorFilter(0xFFD2D3D4, PorterDuff.Mode.SRC_IN));
        layout.addView(commentImage, LayoutHelper.createFrame(20, 20, Gravity.CENTER));

        arrowImage = new ImageView(context);
        arrowImage.setImageResource(R.drawable.menu_comments_arrow);
        arrowImage.setColorFilter(new PorterDuffColorFilter(0xFFD2D3D4, PorterDuff.Mode.SRC_IN));
        layout.addView(arrowImage, LayoutHelper.createFrame(20, 20, Gravity.CENTER));
        arrowImage.setPivotX(dp(10.27f));
        arrowImage.setPivotY(dp(9.58f));
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
        super.dispatchDraw(canvas);

        final float s = countScale * countText.isNotEmpty();
        final float w = Math.max(dp(12), dp(6) + countText.getCurrentWidth());

        canvas.save();
        AndroidUtilities.rectTmp.set(getWidth() - w, 0, getWidth(), dp(13));
        canvas.scale(s, s, AndroidUtilities.rectTmp.centerX(), AndroidUtilities.rectTmp.centerY());
        AndroidUtilities.rectTmp.inset(-dp(2), -dp(2));
        canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.rectTmp.height() / 2, AndroidUtilities.rectTmp.height() / 2, clearPaint);

        AndroidUtilities.rectTmp.set(getWidth() - w, 0, getWidth(), dp(13));
        canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.rectTmp.height() / 2, AndroidUtilities.rectTmp.height() / 2, backgroundPaint);
        canvas.translate(AndroidUtilities.rectTmp.left + (w - countText.getCurrentWidth()) / 2.0f, dp(7));
        countText.draw(canvas);
        canvas.restore();

        canvas.restore();
    }

    private boolean collapsed;
    public void setCollapsed(boolean collapsed, boolean animated) {
        if (animated && this.collapsed == collapsed) return;

        this.collapsed = collapsed;
        if (animated) {
            arrowImage
                .animate()
                .rotation(collapsed ? 0 : 180)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(420)
                .start();
        } else {
            arrowImage.setRotation(collapsed ? 0 : 180f);
        }
    }

    private int lastCount;
    public void setCount(int count) {
        countText.setText(count <= 0 ? "" : LocaleController.formatNumber(count, ','));
        if (lastCount != count) {
            animateBounce();
            lastCount = count;
        }
        invalidate();
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == countText || super.verifyDrawable(who);
    }

    private float countScale = 1;
    private ValueAnimator countAnimator;
    private void animateBounce() {
        if (countAnimator != null) {
            countAnimator.cancel();
            countAnimator = null;
        }

        countAnimator = ValueAnimator.ofFloat(0, 1);
        countAnimator.addUpdateListener(anm -> {
            countScale = Math.max(1, (float) anm.getAnimatedValue());
            invalidate();
        });
        countAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                countScale = 1;
                invalidate();
            }
        });
        countAnimator.setInterpolator(new OvershootInterpolator(2.5f));
        countAnimator.setDuration(200);
        countAnimator.start();
    }

}
