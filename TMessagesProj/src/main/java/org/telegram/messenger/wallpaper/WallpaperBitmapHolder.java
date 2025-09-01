package org.telegram.messenger.wallpaper;

import android.graphics.Bitmap;

import androidx.annotation.Nullable;

import java.util.List;

public class WallpaperBitmapHolder {
    public static final int MODE_DEFAULT = 0;
    public static final int MODE_PATTERN = 1;

    public final int mode;
    public final Bitmap bitmap;

    public final @Nullable List<WallpaperGiftPatternPosition> giftPatternPositions;
    
    public WallpaperBitmapHolder(Bitmap bitmap, int mode) {
        this(bitmap, mode, null);
    }
    
    public WallpaperBitmapHolder(
        Bitmap bitmap, 
        int mode, 
        @Nullable List<WallpaperGiftPatternPosition> giftPatternPositions
    ) {
        this.giftPatternPositions = giftPatternPositions;
        this.bitmap = bitmap;
        this.mode = mode;
    }
}
