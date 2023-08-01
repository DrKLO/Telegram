package org.telegram.ui.Stories.recorder;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.core.graphics.ColorUtils;
import androidx.palette.graphics.Palette;
import androidx.palette.graphics.Target;

import org.telegram.messenger.Utilities;

public class DominantColors {

    public static void getColors(boolean smart, Bitmap bitmap, boolean themeIsDark, Utilities.Callback<int[]> callback) {
        if (callback == null) {
            return;
        }
        if (bitmap == null) {
            callback.run(new int[] {0, 0});
            return;
        }

        smart = false;

        if (smart) {
            final Integer[] colors = new Integer[2];
            new Palette.Builder(bitmap).setRegion(0, 0, bitmap.getWidth(), (int) (bitmap.getHeight() * .5f)).generate(palette -> {
                colors[0] = adapt(getColorFromPalette(palette), themeIsDark);
                if (colors[1] != null) {
                    callback.run(new int[]{colors[0], colors[1]});
                }
            });
            new Palette.Builder(bitmap).setRegion(0, (int) (bitmap.getHeight() * (1f - .5f)), bitmap.getWidth(), bitmap.getHeight()).generate(palette -> {
                colors[1] = adapt(getColorFromPalette(palette), themeIsDark);
                if (colors[0] != null) {
                    callback.run(new int[]{colors[0], colors[1]});
                }
            });
        } else {
            callback.run(getColorsSync(smart, bitmap, themeIsDark));
        }
    }

    private static float[] tempHsv;
    private static int adapt(int color, boolean themeIsDark) {
        if (tempHsv == null) {
            tempHsv = new float[3];
        }
        Color.colorToHSV(color, tempHsv);
        tempHsv[2] = Utilities.clamp(tempHsv[2] + (themeIsDark ? -.05f : +.07f), .85f, .15f);
        if (tempHsv[1] > 0.1f && tempHsv[1] <= 0.95f) {
            if (tempHsv[1] <= 0.5f) {
                tempHsv[1] = Utilities.clamp(tempHsv[1] + 0.2f, 1f, 0f);
            } else if (tempHsv[1] > 0.8f) {
                tempHsv[1] = Utilities.clamp(tempHsv[1] - 0.4f, 1f, 0f);
            }
        }
        return Color.HSVToColor(tempHsv);
    }

    public static int[] getColorsSync(boolean smart, Bitmap bitmap, boolean themeIsDark) {
        smart = false;
        if (smart) {
            Palette top = new Palette.Builder(bitmap).setRegion(0, 0, bitmap.getWidth(), (int) (bitmap.getHeight() * .5f)).generate();
            Palette bottom = new Palette.Builder(bitmap).setRegion(0, (int) (bitmap.getHeight() * (1f - .5f)), bitmap.getWidth(), bitmap.getHeight()).generate();
            return new int[]{adapt(getColorFromPalette(top), themeIsDark), adapt(getColorFromPalette(bottom), themeIsDark)};
        } else {
            return new int[]{
                adapt(bitmap.getPixel(bitmap.getWidth() / 2, (int) (bitmap.getHeight() * .1f)), themeIsDark),
                adapt(bitmap.getPixel(bitmap.getWidth() / 2, (int) (bitmap.getHeight() * .9f)), themeIsDark)
            };
        }
    }

    private static int getColorFromPalette(Palette palette) {
        if (palette == null)
            return 0x00000000;
        int color;
//        color = palette.getColorForTarget(new Target.Builder().setTargetSaturation(.7f).setMaximumSaturation(.9f).build(), 0);
//        if (Color.alpha(color) > 200)
//            return color;
        color = palette.getMutedColor(0);
        if (Color.alpha(color) > 200)
            return color;
        color = palette.getVibrantColor(0);
        if (Color.alpha(color) > 200)
            return color;
        color = palette.getColorForTarget(new Target.Builder().setMaximumLightness(.8f).setMinimumLightness(.1f).setSaturationWeight(.4f).build(), 0);
        if (Color.alpha(color) > 200)
            return color;
        color = palette.getDarkVibrantColor(0);
        if (Color.alpha(color) > 200)
            return color;
        color = palette.getDarkMutedColor(0);
        if (Color.alpha(color) > 200)
            return color;
        return 0;
    }
}
