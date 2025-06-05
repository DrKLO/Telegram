package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.SparseIntArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Bitmaps;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;

public class ThemePreviewDrawable extends BitmapDrawable {

    private DocumentObject.ThemeDocument themeDocument;

    public ThemePreviewDrawable(File pattern, DocumentObject.ThemeDocument document) {
        super(createPreview(pattern, document));
        themeDocument = document;
    }
    
    public DocumentObject.ThemeDocument getThemeDocument() {
        return themeDocument;
    }

    private static Bitmap createPreview(File pattern, DocumentObject.ThemeDocument themeDocument) {
        RectF rect = new RectF();
        Paint paint = new Paint();
        
        Bitmap bitmap = Bitmaps.createBitmap(560, 678, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        SparseIntArray baseColors = Theme.getThemeFileValues(null, themeDocument.baseTheme.assetName, null);
        SparseIntArray colors = baseColors.clone();
        themeDocument.accent.fillAccentColors(baseColors, colors);

        int actionBarColor = Theme.getPreviewColor(colors, Theme.key_actionBarDefault);
        int actionBarIconColor = Theme.getPreviewColor(colors, Theme.key_actionBarDefaultIcon);
        int messageFieldColor = Theme.getPreviewColor(colors, Theme.key_chat_messagePanelBackground);
        int messageFieldIconColor = Theme.getPreviewColor(colors, Theme.key_chat_messagePanelIcons);
        int messageInColor = Theme.getPreviewColor(colors, Theme.key_chat_inBubble);

        int messageOutColor = Theme.getPreviewColor(colors, Theme.key_chat_outBubble);
        Integer backgroundColor = colors.get(Theme.key_chat_wallpaper);
        Integer gradientToColor1 = colors.get(Theme.key_chat_wallpaper_gradient_to1);
        Integer gradientToColor2 = colors.get(Theme.key_chat_wallpaper_gradient_to2);
        Integer gradientToColor3 = colors.get(Theme.key_chat_wallpaper_gradient_to3);

        Integer gradientRotation = colors.get(Theme.key_chat_wallpaper_gradient_rotation);
        if (gradientRotation == null) {
            gradientRotation = 45;
        }

        Drawable backDrawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.preview_back).mutate();
        Theme.setDrawableColor(backDrawable, actionBarIconColor);
        Drawable otherDrawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.preview_dots).mutate();
        Theme.setDrawableColor(otherDrawable, actionBarIconColor);
        Drawable emojiDrawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.preview_smile).mutate();
        Theme.setDrawableColor(emojiDrawable, messageFieldIconColor);
        Drawable micDrawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.preview_mic).mutate();
        Theme.setDrawableColor(micDrawable, messageFieldIconColor);

        Theme.MessageDrawable[] messageDrawable = new Theme.MessageDrawable[2];
        for (int a = 0; a < 2; a++) {
            messageDrawable[a] = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_PREVIEW, a == 1, false) {
                @Override
                protected int getColor(int key) {
                    Integer color = colors.get(key);
                    if (color == null) {
                        return Theme.getColor(key);
                    }
                    return color;
                }

                @Override
                protected int getCurrentColor(int key) {
                    return colors.get(key);
                }
            };
            Theme.setDrawableColor(messageDrawable[a], a == 1 ? messageOutColor : messageInColor);
        }

        boolean hasBackground = false;
        if (backgroundColor != null) {
            Drawable wallpaperDrawable = null;
            MotionBackgroundDrawable motionBackgroundDrawable = null;
            int patternColor;
            if (gradientToColor1 == null) {
                wallpaperDrawable = new ColorDrawable(backgroundColor);
                patternColor = AndroidUtilities.getPatternColor(backgroundColor);
            } else {
                if (gradientToColor2 != 0) {
                    motionBackgroundDrawable = new MotionBackgroundDrawable(backgroundColor, gradientToColor1, gradientToColor2, gradientToColor3, true);
                } else {
                    final int[] gradientColors = {backgroundColor, gradientToColor1};
                    wallpaperDrawable = BackgroundGradientDrawable.createDitheredGradientBitmapDrawable(gradientRotation, gradientColors, bitmap.getWidth(), bitmap.getHeight() - 120);
                }
                patternColor = AndroidUtilities.getPatternColor(AndroidUtilities.getAverageColor(backgroundColor, gradientToColor1));
            }
            if (wallpaperDrawable != null) {
                wallpaperDrawable.setBounds(0, 120, bitmap.getWidth(), bitmap.getHeight() - 120);
                wallpaperDrawable.draw(canvas);
            }

            Bitmap patternBitmap = null;
            if (pattern != null) {
                int W = 560, H = 678;
                if ("application/x-tgwallpattern".equals(themeDocument.mime_type)) {
                    patternBitmap = SvgHelper.getBitmap(pattern, W, H, false);
                } else {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 1;
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(pattern.getAbsolutePath(), opts);
                    float photoW = opts.outWidth;
                    float photoH = opts.outHeight;
                    float scaleFactor;
                    int w_filter = W;
                    int h_filter = H;
                    if (w_filter >= h_filter && photoW > photoH) {
                        scaleFactor = Math.max(photoW / w_filter, photoH / h_filter);
                    } else {
                        scaleFactor = Math.min(photoW / w_filter, photoH / h_filter);
                    }
                    if (scaleFactor < 1.2f) {
                        scaleFactor = 1;
                    }
                    opts.inJustDecodeBounds = false;
                    if (scaleFactor > 1.0f && (photoW > w_filter || photoH > h_filter)) {
                        int sample = 1;
                        do {
                            sample *= 2;
                        } while (sample * 2 < scaleFactor);
                        opts.inSampleSize = sample;
                    } else {
                        opts.inSampleSize = (int) scaleFactor;
                    }
                    patternBitmap = BitmapFactory.decodeFile(pattern.getAbsolutePath(), opts);
                }
                if (patternBitmap != null) {
                    if (motionBackgroundDrawable != null) {
                        motionBackgroundDrawable.setPatternBitmap((int) (themeDocument.accent.patternIntensity * 100), patternBitmap);
                        motionBackgroundDrawable.setBounds(0, 120, bitmap.getWidth(), bitmap.getHeight() - 120);
                        motionBackgroundDrawable.draw(canvas);
                    } else {
                        Paint backgroundPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
                        if (themeDocument.accent.patternIntensity >= 0) {
                            backgroundPaint.setColorFilter(new PorterDuffColorFilter(patternColor, PorterDuff.Mode.SRC_IN));
                        }
                        backgroundPaint.setAlpha(255);
                        float scale = Math.max((float) W / patternBitmap.getWidth(), (float) H / patternBitmap.getHeight());
                        int w = (int) (patternBitmap.getWidth() * scale);
                        int h = (int) (patternBitmap.getHeight() * scale);
                        int x = (W - w) / 2;
                        int y = (H - h) / 2;
                        canvas.save();
                        canvas.translate(x, y);
                        canvas.scale(scale, scale);
                        canvas.drawBitmap(patternBitmap, 0, 0, backgroundPaint);
                        canvas.restore();
                    }
                }
            }

            if (patternBitmap == null && motionBackgroundDrawable != null) {
                motionBackgroundDrawable.setBounds(0, 120, bitmap.getWidth(), bitmap.getHeight() - 120);
                motionBackgroundDrawable.draw(canvas);
            }

            hasBackground = true;
        }
        if (!hasBackground) {
            Drawable catsDrawable = Theme.createDefaultWallpaper(bitmap.getWidth(), bitmap.getHeight() - 120);
            catsDrawable.setBounds(0, 120, bitmap.getWidth(), bitmap.getHeight() - 120);
            catsDrawable.draw(canvas);
        }

        paint.setColor(actionBarColor);
        canvas.drawRect(0, 0, bitmap.getWidth(), 120, paint);

        if (backDrawable != null) {
            int x = 13;
            int y = (120 - backDrawable.getIntrinsicHeight()) / 2;
            backDrawable.setBounds(x, y, x + backDrawable.getIntrinsicWidth(), y + backDrawable.getIntrinsicHeight());
            backDrawable.draw(canvas);
        }
        if (otherDrawable != null) {
            int x = bitmap.getWidth() - otherDrawable.getIntrinsicWidth() - 10;
            int y = (120 - otherDrawable.getIntrinsicHeight()) / 2;
            otherDrawable.setBounds(x, y, x + otherDrawable.getIntrinsicWidth(), y + otherDrawable.getIntrinsicHeight());
            otherDrawable.draw(canvas);
        }

        messageDrawable[1].setBounds(161, 216, bitmap.getWidth() - 20, 216 + 92);
        messageDrawable[1].setTop(0, 560, 522, false, false);
        messageDrawable[1].draw(canvas);

        messageDrawable[1].setBounds(161, 430, bitmap.getWidth() - 20, 430 + 92);
        messageDrawable[1].setTop(430, 560, 522, false, false);
        messageDrawable[1].draw(canvas);

        messageDrawable[0].setBounds(20, 323, 399, 323 + 92);
        messageDrawable[0].setTop(323, 560, 522, false, false);
        messageDrawable[0].draw(canvas);

        paint.setColor(messageFieldColor);
        canvas.drawRect(0, bitmap.getHeight() - 120, bitmap.getWidth(), bitmap.getHeight(), paint);
        if (emojiDrawable != null) {
            int x = 22;
            int y = bitmap.getHeight() - 120 + (120 - emojiDrawable.getIntrinsicHeight()) / 2;
            emojiDrawable.setBounds(x, y, x + emojiDrawable.getIntrinsicWidth(), y + emojiDrawable.getIntrinsicHeight());
            emojiDrawable.draw(canvas);
        }
        if (micDrawable != null) {
            int x = bitmap.getWidth() - micDrawable.getIntrinsicWidth() - 22;
            int y = bitmap.getHeight() - 120 + (120 - micDrawable.getIntrinsicHeight()) / 2;
            micDrawable.setBounds(x, y, x + micDrawable.getIntrinsicWidth(), y + micDrawable.getIntrinsicHeight());
            micDrawable.draw(canvas);
        }
        return bitmap;
    }
}
