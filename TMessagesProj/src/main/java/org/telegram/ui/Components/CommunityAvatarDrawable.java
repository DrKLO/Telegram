package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.R;
import org.telegram.messenger.utils.DrawableUtils;
import org.telegram.ui.ActionBar.Theme;

public class CommunityAvatarDrawable extends Drawable {
    private final Drawable drawable;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float radius;

    public CommunityAvatarDrawable(Context context, float radius) {
        drawable = context.getResources().getDrawable(R.drawable.msg_filled_menu_groups);
        paint.setColor(Theme.multAlpha(Color.BLACK, 0.1552f));
        this.radius = radius;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.drawRoundRect(getBounds().left, getBounds().top, getBounds().right, getBounds().bottom, radius, radius, paint);
        DrawableUtils.setBounds(drawable, getBounds().exactCenterX(), getBounds().exactCenterY(), dp(36), dp(36), Gravity.CENTER);
        drawable.draw(canvas);
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
