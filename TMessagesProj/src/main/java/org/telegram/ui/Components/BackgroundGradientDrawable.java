package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.List;

public class BackgroundGradientDrawable extends GradientDrawable {

    public static final float DEFAULT_COMPRESS_RATIO = 0.5f;

    public interface Disposable {
        void dispose();
    }

    public interface Listener {
        void onSizeReady(int width, int height);
        void onAllSizesReady();
    }

    public static class ListenerAdapter implements Listener {
        @Override
        public void onSizeReady(int width, int height) {
        }
        @Override
        public void onAllSizesReady() {
        }
    }

    public static class Sizes {

        public enum Orientation {
            PORTRAIT, LANDSCAPE, BOTH
        }

        private final IntSize[] arr;

        private Sizes(int width, int height, int... additionalSizes) {
            arr = new IntSize[1 + additionalSizes.length / 2];
            arr[0] = new IntSize(width, height);
            for (int i = 0; i < additionalSizes.length / 2; i++) {
                arr[i + 1] = new IntSize(additionalSizes[i * 2], additionalSizes[i * 2 + 1]);
            }
        }

        /** @param additionalSizes [width1, height1, width2, height2, ...] */
        public static Sizes of(int width, int height, int... additionalSizes) {
            return new Sizes(width, height, additionalSizes);
        }

        public static Sizes ofDeviceScreen() {
            return ofDeviceScreen(DEFAULT_COMPRESS_RATIO);
        }

        public static Sizes ofDeviceScreen(float compressRatio) {
            return ofDeviceScreen(compressRatio, Orientation.BOTH);
        }

        public static Sizes ofDeviceScreen(Orientation orientation) {
            return ofDeviceScreen(DEFAULT_COMPRESS_RATIO, orientation);
        }

        public static Sizes ofDeviceScreen(float compressRatio, Orientation orientation) {
            final int width = (int) (AndroidUtilities.displaySize.x * compressRatio);
            final int height = (int) (AndroidUtilities.displaySize.y * compressRatio);

            if (width == height) {
                return of(width, height);
            }

            if (orientation == Orientation.BOTH) {
                // displaySize is orientation-dependent, so we will firstly dither a gradient
                // for the current orientation and only then for another one
                return of(width, height, height, width);
            }

            //noinspection SuspiciousNameCombination
            return (orientation == Orientation.PORTRAIT) == (width < height) ? of(width, height) : of(height, width);
        }
    }

    private final int[] colors;
    private final ArrayMap<IntSize, Bitmap> bitmaps = new ArrayMap<>();
    private final ArrayMap<IntSize, Boolean> isForExactBounds = new ArrayMap<>();
    private final ArrayMap<View, Disposable> disposables = new ArrayMap<>();
    private final List<Runnable[]> ditheringRunnables = new ArrayList<>();
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean disposed = false;

    public BackgroundGradientDrawable(Orientation orientation, int[] colors) {
        super(orientation, colors);
        setDither(true);
        this.colors = colors;
        bitmapPaint.setDither(true);
    }

    @Override
    public void draw(Canvas canvas) {
        if (disposed) {
            super.draw(canvas);
            return;
        }

        final Rect bounds = getBounds();
        final Bitmap bitmap = findBestBitmapForSize(bounds.width(), bounds.height());

        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, bounds, bitmapPaint);
        } else {
            super.draw(canvas);
        }
    }

    public Disposable drawExactBoundsSize(Canvas canvas, View ownerView) {
        return drawExactBoundsSize(canvas, ownerView, DEFAULT_COMPRESS_RATIO);
    }

    public Disposable drawExactBoundsSize(Canvas canvas, View ownerView, float compressRatio) {
        if (disposed) {
            super.draw(canvas);
            return null;
        }

        final Rect bounds = getBounds();
        final int width = (int) (bounds.width() * compressRatio);
        final int height = (int) (bounds.height() * compressRatio);

        for (int i = 0, count = bitmaps.size(); i < count; i++) {
            final IntSize size = bitmaps.keyAt(i);
            if (size.width == width && size.height == height) {
                final Bitmap bitmap = bitmaps.valueAt(i);

                if (bitmap != null) { // ready
                    canvas.drawBitmap(bitmap, null, bounds, bitmapPaint);
                } else { // processing
                    super.draw(canvas);
                }

                return disposables.get(ownerView);
            }
        }

        final Disposable oldDisposable = disposables.remove(ownerView);
        if (oldDisposable != null) {
            oldDisposable.dispose();
        }

        final IntSize size = new IntSize(width, height);
        bitmaps.put(size, null);
        isForExactBounds.put(size, true);
        final Disposable delegate = startDitheringInternal(new IntSize[]{size}, new ListenerAdapter() {
            @Override
            public void onAllSizesReady() {
                ownerView.invalidate();
            }
        }, 0);
        final Disposable disposable = disposables.put(ownerView, () -> {
            disposables.remove(ownerView);
            delegate.dispose();
        });
        super.draw(canvas);
        return disposable;
    }

    @Override
    public void setAlpha(int alpha) {
        super.setAlpha(alpha);
        bitmapPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        super.setColorFilter(colorFilter);
        bitmapPaint.setColorFilter(colorFilter);
    }

    @Nullable
    public int[] getColorsList() {
        return colors;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            dispose();
        } finally {
            super.finalize();
        }
    }

    public Disposable startDithering(Sizes sizes, Listener listener) {
        return startDithering(sizes, listener, 0);
    }

    public Disposable startDithering(Sizes sizes, Listener listener, long delay) {
        if (disposed) {
            return null;
        }

        final List<IntSize> sizesList = new ArrayList<>(sizes.arr.length);

        for (int i = 0; i < sizes.arr.length; i++) {
            final IntSize size = sizes.arr[i];
            if (!bitmaps.containsKey(size)) {
                bitmaps.put(size, null);
                sizesList.add(size);
            }
        }

        if (sizesList.isEmpty()) {
            return null;
        }

        return startDitheringInternal(sizesList.toArray(new IntSize[0]), listener, delay);
    }

    private Disposable startDitheringInternal(IntSize[] sizesArr, Listener listener, long delay) {
        if (sizesArr.length == 0) {
            return null;
        }

        final Listener[] listenerReference = new Listener[]{listener};
        final Runnable[] runnables = new Runnable[sizesArr.length];
        ditheringRunnables.add(runnables);

        for (int i = 0; i < sizesArr.length; i++) {
            final IntSize size = sizesArr[i];
            if (size.width == 0 || size.height == 0) {
                continue;
            }
            final int index = i;
            Utilities.globalQueue.postRunnable(runnables[i] = () -> {
                Bitmap gradientBitmap = null;
                try {
                    gradientBitmap = createDitheredGradientBitmap(getOrientation(), colors, size.width, size.height);
                } finally {
                    final Bitmap bitmap = gradientBitmap;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!ditheringRunnables.contains(runnables)) {
                            if (bitmap != null) {
                                bitmap.recycle();
                            }
                            return;
                        }
                        if (bitmap != null) {
                            bitmaps.put(size, bitmap);
                        } else {
                            bitmaps.remove(size);
                            isForExactBounds.remove(size);
                        }
                        runnables[index] = null;
                        boolean hasNotNull = false;
                        if (runnables.length > 1) {
                            for (int j = 0; j < runnables.length; j++) {
                                if (runnables[j] != null) {
                                    hasNotNull = true;
                                    break;
                                }
                            }
                        }
                        if (!hasNotNull) {
                            ditheringRunnables.remove(runnables);
                        }
                        if (listenerReference[0] != null) {
                            listenerReference[0].onSizeReady(size.width, size.height);
                            if (!hasNotNull) {
                                listenerReference[0].onAllSizesReady();
                                listenerReference[0] = null;
                            }
                        }
                    });
                }
            }, delay);
        }

        return () -> {
            listenerReference[0] = null;
            if (ditheringRunnables.contains(runnables)) {
                Utilities.globalQueue.cancelRunnables(runnables);
                ditheringRunnables.remove(runnables);
            }
            for (int i = 0; i < sizesArr.length; i++) {
                final IntSize size = sizesArr[i];
                final Bitmap bitmap = bitmaps.remove(size);
                isForExactBounds.remove(size);
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }
        };
    }

    public void dispose() {
        if (!disposed) {
            for (int i = ditheringRunnables.size() - 1; i >= 0; i--) {
                Utilities.globalQueue.cancelRunnables(ditheringRunnables.remove(i));
            }
            for (int i = bitmaps.size() - 1; i >= 0; i--) {
                final Bitmap bitmap = bitmaps.removeAt(i);
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }
            isForExactBounds.clear();
            disposables.clear();
            disposed = true;
        }
    }

    private Bitmap findBestBitmapForSize(int width, int height) {
        Bitmap bestBitmap = null;
        float minDist = Float.MAX_VALUE;
        for (int i = 0, count = bitmaps.size(); i < count; i++) {
            final IntSize size = bitmaps.keyAt(i);
            final float dist = (float) Math.sqrt(Math.pow(width - size.width, 2) + Math.pow(height - size.height, 2));
            if (dist < minDist) {
                final Bitmap bitmap = bitmaps.valueAt(i);
                if (bitmap != null) {
                    final Boolean forExactBounds = isForExactBounds.get(size);
                    if (forExactBounds == null || !forExactBounds) {
                        bestBitmap = bitmap;
                        minDist = dist;
                    }
                }
            }
        }
        return bestBitmap;
    }

    public static Rect getGradientPoints(Orientation orientation, int width, int height) {
        final Rect outRect = new Rect();
        switch (orientation) {
            case TOP_BOTTOM:
                outRect.left = width / 2;
                outRect.top = 0;
                outRect.right = outRect.left;
                outRect.bottom = height;
                break;
            case TR_BL:
                outRect.left = width;
                outRect.top = 0;
                outRect.right = 0;
                outRect.bottom = height;
                break;
            case RIGHT_LEFT:
                outRect.left = width;
                outRect.top = height / 2;
                outRect.right = 0;
                outRect.bottom = outRect.top;
                break;
            case BR_TL:
                outRect.left = width;
                outRect.top = height;
                outRect.right = 0;
                outRect.bottom = 0;
                break;
            case BOTTOM_TOP:
                outRect.left = width / 2;
                outRect.top = height;
                outRect.right = outRect.left;
                outRect.bottom = 0;
                break;
            case BL_TR:
                outRect.left = 0;
                outRect.top = height;
                outRect.right = width;
                outRect.bottom = 0;
                break;
            case LEFT_RIGHT:
                outRect.left = 0;
                outRect.top = height / 2;
                outRect.right = width;
                outRect.bottom = outRect.top;
                break;
            default: // TL_BR
                outRect.left = 0;
                outRect.top = 0;
                outRect.right = width;
                outRect.bottom = height;
                break;
        }
        return outRect;
    }

    public static Rect getGradientPoints(int gradientAngle, int width, int height) {
        return getGradientPoints(getGradientOrientation(gradientAngle), width, height);
    }

    public static Orientation getGradientOrientation(int gradientAngle) {
        switch (gradientAngle) {
            case 0:
                return Orientation.BOTTOM_TOP;
            case 90:
                return Orientation.LEFT_RIGHT;
            case 135:
                return Orientation.TL_BR;
            case 180:
                return Orientation.TOP_BOTTOM;
            case 225:
                return Orientation.TR_BL;
            case 270:
                return Orientation.RIGHT_LEFT;
            case 315:
                return Orientation.BR_TL;
            default: // 45
                return Orientation.BL_TR;
        }
    }

    public static BitmapDrawable createDitheredGradientBitmapDrawable(int angle, int[] colors, int width, int height) {
        return createDitheredGradientBitmapDrawable(getGradientOrientation(angle), colors, width, height);
    }

    public static BitmapDrawable createDitheredGradientBitmapDrawable(Orientation orientation, int[] colors, int width, int height) {
        return new BitmapDrawable(ApplicationLoader.applicationContext.getResources(), createDitheredGradientBitmap(orientation, colors, width, height));
    }

    private static Bitmap createDitheredGradientBitmap(Orientation orientation, int[] colors, int width, int height) {
        final Rect r = getGradientPoints(orientation, width, height);
        final Bitmap ditheredGradientBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Utilities.drawDitheredGradient(ditheredGradientBitmap, colors, r.left, r.top, r.right, r.bottom);
        return ditheredGradientBitmap;
    }
}
