package org.telegram.ui.Components.blur3.utils;

import android.graphics.Bitmap;

import java.lang.ref.WeakReference;

public class BitmapMemoizedMetadata<T> {
    public interface Provider<T> {
        T get(Bitmap bitmap);
    }

    private final BitmapChangeTracker lastBitmap = new BitmapChangeTracker();
    private final Provider<T> provider;
    private T memoized;

    public BitmapMemoizedMetadata(Provider<T> provider) {
        this.provider = provider;
    }

    public T get(Bitmap bitmap) {
        if (lastBitmap.isInvalidated(bitmap)) {
            memoized = provider.get(bitmap);
            lastBitmap.set(bitmap);
        }

        return memoized;
    }
}
