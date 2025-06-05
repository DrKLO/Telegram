package org.telegram.messenger;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;

public class ExtendedBitmapDrawable extends BitmapDrawable {

    private int invert;
    private int orientation;

    public ExtendedBitmapDrawable(Bitmap bitmap, int orient, int invrt) {
        super(bitmap);
        invert = invrt;
        orientation = orient;
    }

    public boolean invertHorizontally() {
        return (invert & 1) != 0;
    }

    public boolean invertVertically() {
        return (invert & 2) != 0;
    }

    public int getInvert() {
        return invert;
    }

    public int getOrientation() {
        return orientation;
    }
}
