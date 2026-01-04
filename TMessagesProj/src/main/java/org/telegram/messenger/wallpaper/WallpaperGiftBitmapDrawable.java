package org.telegram.messenger.wallpaper;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;

import java.util.List;

public class WallpaperGiftBitmapDrawable extends BitmapDrawable {
    public final List<WallpaperGiftPatternPosition> patternPositions;

    public WallpaperGiftBitmapDrawable(Bitmap bitmap, List<WallpaperGiftPatternPosition> positions) {
        super(bitmap);
        this.patternPositions = positions;
    }

    public static BitmapDrawable create(Bitmap bitmap, List<WallpaperGiftPatternPosition> positions) {
        if (bitmap == null) {
            return null;
        }

        if (positions == null || positions.isEmpty()) {
            return new BitmapDrawable(bitmap);
        } else {
            return new WallpaperGiftBitmapDrawable(bitmap, positions);
        }
    }
}
