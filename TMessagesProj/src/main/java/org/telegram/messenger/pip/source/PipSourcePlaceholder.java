package org.telegram.messenger.pip.source;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class PipSourcePlaceholder {
    private final @NonNull View placeholderActivityView;
    private final @Nullable View placeholderSourceView;

    private Bitmap placeholder;
    private Drawable placeholderSourceDrawable;
    private Drawable placeholderActivityDrawable;

    public PipSourcePlaceholder(@NonNull View placeholderActivityView, @Nullable View placeholderSourceView) {
        this.placeholderActivityView = placeholderActivityView;
        this.placeholderSourceView = placeholderSourceView;
    }

    public void setPlaceholder(Bitmap bitmap) {
        if (placeholder == bitmap) {
            return;
        }

        clear();

        placeholder = bitmap;
        placeholderActivityDrawable = new PlaceholderDrawable(placeholder);
        placeholderActivityView.setBackground(placeholderActivityDrawable);
        if (placeholderSourceView != null) {
            placeholderSourceDrawable = new PlaceholderDrawable(placeholder);
            placeholderSourceView.setBackground(placeholderSourceDrawable);
        }
    }

    public void stopPlaceholderForActivity() {
        if (placeholderActivityDrawable != null) {
            placeholderActivityView.setBackground(null);
            placeholderActivityDrawable = null;
        }
        maybeClearPlaceholder();
    }

    public void stopPlaceholderForSource() {
        if (placeholderSourceDrawable != null) {
            placeholderSourceDrawable = null;
            if (placeholderSourceView != null) {
                placeholderSourceView.setBackground(null);
            }
        }
        maybeClearPlaceholder();
    }

    public void clear() {
        stopPlaceholderForActivity();
        stopPlaceholderForSource();
    }

    private void maybeClearPlaceholder() {
        if (placeholderSourceDrawable == null && placeholderActivityDrawable == null) {
            if (placeholder != null) {
                placeholder.recycle();
                placeholder = null;
            }
        }
    }


    private static class PlaceholderDrawable extends Drawable {
        private final Bitmap bitmap;
        private final Rect rect = new Rect();

        private PlaceholderDrawable(Bitmap bitmap) {
            this.bitmap = bitmap;
        }


        @Override
        public void draw(@NonNull Canvas canvas) {
            if (bitmap.isRecycled()) {
                return;
            }

            canvas.drawBitmap(bitmap, null, rect, null);
        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {

        }

        @Override
        public void setBounds(int left, int top, int right, int bottom) {
            super.setBounds(left, top, right, bottom);
            if (bitmap.isRecycled()) {
                return;
            }

            int destWidth = right - left;
            int destHeight = bottom - top;

            int bitmapWidth = bitmap.getWidth();
            int bitmapHeight = bitmap.getHeight();

            float scale = Math.min(
                (float) destWidth / bitmapWidth,
                (float) destHeight / bitmapHeight
            );

            int scaledWidth = Math.round(bitmapWidth * scale);
            int scaledHeight = Math.round(bitmapHeight * scale);

            int dx = (destWidth - scaledWidth) / 2;
            int dy = (destHeight - scaledHeight) / 2;

            rect.set(
                left + dx,
                top + dy,
                left + dx + scaledWidth,
                top + dy + scaledHeight
            );
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
