package org.telegram.ui.Components.blur3.utils;

import android.graphics.Bitmap;

import java.lang.ref.WeakReference;

public class BitmapMemoizedMetadata<T> {
    public interface Provider<T> {
        T get(Bitmap bitmap);
        default boolean isValid(T value) {
            return true;
        }
    }

    private final Provider<T> provider;
    private WeakReference<Bitmap> ref;
    private long generationId;
    private T memoized;

    public BitmapMemoizedMetadata(Provider<T> provider) {
        this.provider = provider;
    }

    public T get() {
        return get(ref != null ? ref.get() : null);
    }

    public T get(Bitmap bitmap) {
        final Bitmap oldBitmap = ref != null ? ref.get() : null;
        final long generationIdToSet = bitmap != null && !bitmap.isRecycled() ?
                bitmap.getGenerationId() : 0;

        if (bitmap != null && oldBitmap == bitmap && generationIdToSet == generationId && provider.isValid(memoized)) {
            return memoized;
        }

        ref = new WeakReference<>(bitmap);
        generationId = generationIdToSet;
        memoized = provider.get(bitmap);

        return memoized;
    }
}
