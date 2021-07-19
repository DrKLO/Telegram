package org.telegram.messenger;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;

public class ExtendedBitmapDrawable extends BitmapDrawable {

    private boolean canInvert;
    private int orientation;

    public ExtendedBitmapDrawable(Bitmap bitmap, boolean invert, int orient) {
        super(bitmap);
        canInvert = invert;
        orientation = orient;
    }

    public boolean isCanInvert() {
        return canInvert;
    }

    public int getOrientation() {
        return orientation;
    }
}
