package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RLottieDrawable;

public class TrashView extends View {

    private final RLottieDrawable drawable;

    private final AnimatedTextView.AnimatedTextDrawable textDrawable;

    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint greyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final ButtonBounce bounce = new ButtonBounce(this);

    public TrashView(Context context) {
        super(context);

        circlePaint.setColor(0xffffffff);
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(dpf2(2.66f));
        circlePaint.setShadowLayer(dpf2(3f), 0, dp(1.66f), 0x30000000);
        greyPaint.setColor(0x33000000);

        drawable = new RLottieDrawable(R.raw.group_pip_delete_icon, "" + R.raw.group_pip_delete_icon, dp(48), dp(48), true, null);
        drawable.setMasterParent(this);
        drawable.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
        drawable.setPlayInDirectionOfCustomEndFrame(true);
        drawable.setCustomEndFrame(0);
        drawable.setAllowDecodeSingleFrame(true);
        drawable.start();

        textDrawable = new AnimatedTextView.AnimatedTextDrawable(true, true, false);
        textDrawable.setAnimationProperties(.3f, 0, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
        textDrawable.setOverrideFullWidth(AndroidUtilities.displaySize.x);
        textDrawable.setTextSize(dp(14));
        textDrawable.setTextColor(0xffffffff);
        textDrawable.setShadowLayer(dpf2(1.33f), 0, dp(1), 0x40000000);
        textDrawable.setText(LocaleController.getString("TrashHintDrag", R.string.TrashHintDrag));
        textDrawable.setGravity(Gravity.CENTER);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == textDrawable || super.verifyDrawable(who);
    }

    private boolean dragged;
    private final AnimatedFloat draggedT = new AnimatedFloat(this, 0, 240, CubicBezierInterpolator.EASE_OUT_QUINT);

    @Override
    protected void dispatchDraw(Canvas canvas) {
        final float r = dp(30);
        final float cx = getWidth() / 2f, cy = getHeight() / 2f;

        final float R = r + dp(3) * draggedT.set(dragged);
        canvas.drawCircle(cx, cy, R, greyPaint);
        canvas.drawCircle(cx, cy, R, circlePaint);

        final float sz = dp(48);
        drawable.setBounds((int) (cx - sz / 2f), (int) (cy - sz / 2f), (int) (cx + sz / 2f), (int) (cy + sz / 2f));
        drawable.draw(canvas);

        textDrawable.setBounds(0, (int) (cy + r + dp(7)), getWidth(), getHeight());
        textDrawable.draw(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(widthMeasureSpec, dp(120));
    }

    public void onDragInfo(boolean dragged, boolean deleted) {
        bounce.setPressed(dragged);
        textDrawable.setText(dragged || deleted ? LocaleController.getString("TrashHintRelease", R.string.TrashHintRelease) : LocaleController.getString("TrashHintDrag", R.string.TrashHintDrag));
        if (this.dragged = (dragged && !deleted)) {
            if (drawable.getCurrentFrame() > 34) {
                drawable.setCurrentFrame(0, false);
            }
            drawable.setCustomEndFrame(33);
            drawable.start();
        } else {
            drawable.setCustomEndFrame(deleted ? 66 : 0);
            drawable.start();
        }
        invalidate();
    }
}
