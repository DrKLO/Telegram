package org.telegram.messenger.pip.utils;

import android.app.PictureInPictureParams;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.util.Rational;

import androidx.annotation.RequiresApi;

public class PipSourceParams {
    private final Rect position = new Rect();
    private final Point ratio = new Point();

    public boolean setRatio(int width, int height) {
        if (ratio.x != width || ratio.y != height) {
            this.ratio.set(width, height);
            return true;
        }

        return false;
    }

    public boolean setRatio(Point ratio) {
        if (!this.ratio.equals(ratio)) {
            this.ratio.set(ratio.x, ratio.y);
            return true;
        }

        return false;
    }

    public boolean setPosition(Rect position) {
        if (!this.position.equals(position)) {
            this.position.set(position);
            return true;
        }

        return false;
    }

    public boolean setPosition(int left, int top, int right, int bottom) {
        if (position.left != left || position.top != top || position.right != right || position.bottom != bottom) {
            position.set(left, top, right, bottom);
            return true;
        }

        return false;
    }

    public boolean set(Rect position, Point ratio) {
        boolean changed = false;

        changed |= setPosition(position);
        changed |= setRatio(ratio);

        return changed;
    }

    public boolean isValid() {
        return !position.isEmpty() && ratio.x > 0 && ratio.y > 0;
    }

    public void getPosition(Rect outRect) {
        outRect.set(position);
    }

    public int getWidth() {
        return position.width();
    }

    public int getHeight() {
        return position.height();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public PictureInPictureParams.Builder build() {
        final PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
        if (ratio.x > 0 && ratio.y > 0) {
            final Rational r;
            final float rat = (float) ratio.x / ratio.y;
            if (rat < 0.45) {
                r = new Rational(45, 100);
            } else if (rat > 2.35) {
                r = new Rational(235, 100);
            } else {
                r = new Rational(ratio.x, ratio.y);
            }
            builder.setAspectRatio(r);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                builder.setExpandedAspectRatio(r);
            }
        } else {
            builder.setAspectRatio(null);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                builder.setExpandedAspectRatio(null);
            }
        }

        if (!position.isEmpty()) {
            builder.setSourceRectHint(position);
        } else {
            builder.setSourceRectHint(null);
        }

        return builder;
    }
}
