package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public class FolderDrawable extends Drawable {

    private final Drawable drawable;
    private final Path path;
    private boolean pathInvalidated = true;
    private final Paint strokePaint, fillPaint;

    public FolderDrawable(Context context, int iconResId, int color) {
        drawable = context.getResources().getDrawable(iconResId);

        if (color >= 0) {
            path = new Path();

            strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(Theme.getColor(Theme.key_dialogBackground));
            strokePaint.setPathEffect(new CornerPathEffect(dp(1)));
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);

            fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(Theme.getColor(Theme.keys_avatar_nameInMessage[color % Theme.keys_avatar_nameInMessage.length]));
            fillPaint.setPathEffect(new CornerPathEffect(dp(1)));
        } else {
            path = null;
            strokePaint = null;
            fillPaint = null;
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        drawable.setBounds(getBounds());
        drawable.draw(canvas);
        if (path != null) {
            if (pathInvalidated) {
                path.rewind();
                path.moveTo(x(.4871f), y(.6025f));
                path.lineTo(x(.8974f), y(.6025f));
                path.lineTo(x(1f), y(.7564f));
                path.lineTo(x(.8974f), y(.9102f));
                path.lineTo(x(.4871f), y(.9102f));
                path.close();
                pathInvalidated = false;

                strokePaint.setStrokeWidth(dp(3));
            }
            canvas.drawPath(path, strokePaint);
            canvas.drawPath(path, fillPaint);
        }
    }

    int x(float x) {
        return lerp(getBounds().left, getBounds().right, x);
    }
    int y(float x) {
        return lerp(getBounds().top, getBounds().bottom, x);
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        pathInvalidated = true;
    }

    @Override
    public void setAlpha(int alpha) {
        drawable.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        drawable.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return drawable.getOpacity();
    }

    @Override
    public int getIntrinsicWidth() {
        return drawable.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return drawable.getIntrinsicHeight();
    }
}
