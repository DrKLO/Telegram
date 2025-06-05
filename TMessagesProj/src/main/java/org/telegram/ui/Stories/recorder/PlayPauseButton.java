package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.ui.Components.PlayPauseDrawable;

public class PlayPauseButton extends View {

    private final static int playSizeDp = 10;

    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final PlayPauseDrawable drawable = new PlayPauseDrawable(playSizeDp);

    public PlayPauseButton(Context context) {
        super(context);

        circlePaint.setColor(0xFFFFFFFF);
        circlePaint.setShadowLayer(1, 0, 0, 0x19000000);
        circlePaint.setStyle(Paint.Style.STROKE);

        drawable.setCallback(this);
        drawable.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == drawable || super.verifyDrawable(who);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        circlePaint.setStrokeWidth(dpf2(1.66f));
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, dp(10), circlePaint);

        drawable.setBounds(0, 0, dp(playSizeDp), dp(playSizeDp));
        canvas.save();
        canvas.translate((getWidth() - dp(playSizeDp)) / 2f, (getHeight() - dp(playSizeDp)) / 2f);
        drawable.draw(canvas);
        canvas.restore();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(dp(56), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dp(56), MeasureSpec.EXACTLY)
        );
    }

}
