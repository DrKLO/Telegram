package org.telegram.messenger.utils;

import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;

public class ViewOutlineProviderImpl {
    public static final ViewOutlineProvider BOUNDS_OVAL = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setOval(0, 0, view.getWidth(), view.getHeight());
        }
    };

    public static final ViewOutlineProvider BOUNDS_ROUND_RECT = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                Math.min(view.getWidth(), view.getHeight()) / 2f);
        }
    };

    public static ViewOutlineProvider fromDrawable(Drawable drawable) {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                drawable.getOutline(outline);
            }
        };
    }

    public static ViewOutlineProvider boundsWithPaddingRoundRect(int padding, float radius) {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(padding, padding,
                    view.getWidth() - padding,
                    view.getHeight() - padding, radius);
            }
        };
    }

    public static ViewOutlineProvider boundsWithPaddingRoundRect(int padding) {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                final int w = view.getWidth() - padding * 2;
                final int h = view.getHeight() - padding * 2;
                final float r = Math.min(w, h) / 2f;

                outline.setRoundRect(padding, padding,
                    view.getWidth() - padding,
                    view.getHeight() - padding, r);
            }
        };
    }
}
