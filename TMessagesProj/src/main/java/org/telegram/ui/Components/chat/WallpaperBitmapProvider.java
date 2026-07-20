package org.telegram.ui.Components.chat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.Utilities;
import org.telegram.ui.ChatBackgroundDrawable;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSource;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceBitmap;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceWrapped;
import org.telegram.ui.Components.blur3.utils.BitmapMemoizedMetadata;

public class WallpaperBitmapProvider {

    private final BlurredBackgroundSourceColor sourceColor = new BlurredBackgroundSourceColor();
    private final BlurredBackgroundSourceBitmap sourceBitmap = new BlurredBackgroundSourceBitmap();

    private static final Rect tmpRect = new Rect();

    public BlurredBackgroundSource updateSourceFromBackgroundViewDrawable(
        Drawable drawable
    ) {
        if (drawable instanceof ColorDrawable) {
            final int color = ((ColorDrawable) drawable).getColor();
            sourceColor.setColor(color);
            return sourceColor;
        }

        if (drawable instanceof MotionBackgroundDrawable) {
            final MotionBackgroundDrawable motionDrawable = (MotionBackgroundDrawable) drawable;
            if (motionDrawable.getIntensity() < 0) {
                sourceColor.setColor(Color.BLACK);
                return sourceColor;
            }
            sourceBitmap.setBitmap(motionDrawable.getBitmap());
            return sourceBitmap;
        }

        if (drawable instanceof BitmapDrawable) {
            final Bitmap bitmap = blurredFromBitmap.get(((BitmapDrawable) drawable).getBitmap());
            sourceBitmap.setBitmap(bitmap);
            return sourceBitmap;
        }

        if (drawable instanceof ChatBackgroundDrawable) {
            ChatBackgroundDrawable chatDrawable = (ChatBackgroundDrawable) drawable;
            return updateSourceFromBackgroundViewDrawable(chatDrawable.getDrawable(false));
        }

        if (drawable != null) {
            Canvas canvas = sourceBitmap.beginRecording(120, 160);
            tmpRect.set(drawable.getBounds());
            drawable.setBounds(0, 0, 120, 160);
            drawable.draw(canvas);
            drawable.setBounds(tmpRect);
            sourceBitmap.endRecording();
            sourceBitmap.setBitmap(blurredFromBitmap.get(sourceBitmap.getBitmap()));
        }

        return sourceBitmap;
    }

    public int getNavigationBarColor(BlurredBackgroundSource source) {
        if (source instanceof BlurredBackgroundSourceColor) {
            return ((BlurredBackgroundSourceColor) source).getColor();
        }

        if (source instanceof BlurredBackgroundSourceBitmap) {
            final Bitmap bitmap = ((BlurredBackgroundSourceBitmap) source).getBitmap();
            return navbarColorFromBitmap.get(bitmap);
        }

        if (source instanceof BlurredBackgroundSourceWrapped) {
            return getNavigationBarColor(((BlurredBackgroundSourceWrapped) source).getSource());
        }

        return 0;
    }

    public int getStatusBarColor(BlurredBackgroundSource source) {
        if (source instanceof BlurredBackgroundSourceColor) {
            return ((BlurredBackgroundSourceColor) source).getColor();
        }

        if (source instanceof BlurredBackgroundSourceBitmap) {
            final Bitmap bitmap = ((BlurredBackgroundSourceBitmap) source).getBitmap();
            return statusBarColorFromBitmap.get(bitmap);
        }

        if (source instanceof BlurredBackgroundSourceWrapped) {
            return getStatusBarColor(((BlurredBackgroundSourceWrapped) source).getSource());
        }

        return 0;
    }

    private final BitmapMemoizedMetadata<Bitmap> blurredFromBitmap = new BitmapMemoizedMetadata<>(WallpaperBitmapProvider::blurBitmap);
    private final BitmapMemoizedMetadata<Integer> navbarColorFromBitmap = new BitmapMemoizedMetadata<>(WallpaperBitmapProvider::averageBottomColor);
    private final BitmapMemoizedMetadata<Integer> statusBarColorFromBitmap = new BitmapMemoizedMetadata<>(WallpaperBitmapProvider::averageTopColor);


    private static Bitmap blurBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }

        final float scale = Math.max(bitmap.getWidth() / 90f, bitmap.getHeight() / 120f);
        final Bitmap result = Utilities.stackBlurBitmapWithScaleFactor(bitmap, scale);
        result.setHasAlpha(false);
        return result;
    }



    private static int averageTopColor(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return 0;
        }

        final int height = bitmap.getHeight();
        final int width = bitmap.getWidth();
        final int bottom = height / 10;
        return Utilities.averageBitmapColor(bitmap, 0, 0, width, bottom);
    }

    private static int averageBottomColor(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return 0;
        }

        final int height = bitmap.getHeight();
        final int width = bitmap.getWidth();
        final int top = height * 9 / 10;
        return Utilities.averageBitmapColor(bitmap, 0, top, width, height);
    }

}
