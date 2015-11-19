package com.finger2view.messenger.support.util;

import android.graphics.Bitmap;

import java.util.Random;

/**
 * Created by paulorodenas on 11/19/15.
 */
public class BitmapShuffler {
    private Bitmap bitmap;

    public BitmapShuffler(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public Bitmap shuffle() {
        if(!bitmap.isRecycled()){
            Bitmap mutableBitmap = bitmap.copy(bitmap.getConfig(), true);

            int[] pixels = new int[mutableBitmap.getWidth() * mutableBitmap.getHeight()];
            mutableBitmap.getPixels(pixels, 0, mutableBitmap.getWidth(), 0, 0, mutableBitmap.getWidth(), mutableBitmap.getHeight());
            shuffleArray(pixels);
            mutableBitmap.setPixels(pixels, 0, mutableBitmap.getWidth(), 0, 0, mutableBitmap.getWidth(), mutableBitmap.getHeight());
            return mutableBitmap;
        }
        return bitmap;
    }

    private void shuffleArray(int[] ar) {
        Random rnd = new Random();
        for (int i = ar.length - 1; i > 0; i--) {
            int index = rnd.nextInt(i + 1);
            // Simple swap
            int a = ar[index];
            ar[index] = ar[i];
            ar[i] = a;
        }
    }
}
