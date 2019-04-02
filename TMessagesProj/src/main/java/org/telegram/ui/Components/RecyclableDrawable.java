package org.telegram.ui.Components;

import android.graphics.drawable.Drawable;

public abstract class RecyclableDrawable extends Drawable {
    public abstract void recycle();
}
