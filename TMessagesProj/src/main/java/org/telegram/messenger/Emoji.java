/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.messenger;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class Emoji {
    private static HashMap<CharSequence, DrawableInfo> rects = new HashMap<>();
    private static int drawImgSize;
    private static int bigImgSize;
    private static boolean inited = false;
    private static Paint placeholderPaint;
    private static final int splitCount = 4;
    private static Bitmap emojiBmp[][] = new Bitmap[5][splitCount];
    private static boolean loadingEmoji[][] = new boolean[5][splitCount];

    private static final int[][] cols = {
            {11, 11, 11, 11},
            {6, 6, 6, 6},
            {9, 9, 9, 9},
            {9, 9, 9, 9},
            {8, 8, 8, 7}
    };

    static {
        int emojiFullSize;
        if (AndroidUtilities.density <= 1.0f) {
            emojiFullSize = 32;
        } else if (AndroidUtilities.density <= 1.5f) {
            emojiFullSize = 48;
        } else if (AndroidUtilities.density <= 2.0f) {
            emojiFullSize = 64;
        } else {
            emojiFullSize = 64;
        }
        drawImgSize = AndroidUtilities.dp(20);
        bigImgSize = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 40 : 32);

        for (int j = 0; j < EmojiData.data.length; j++) {
            int count2 = (int) Math.ceil(EmojiData.data[j].length / (float) splitCount);
            int position;
            for (int i = 0; i < EmojiData.data[j].length; i++) {
                int page = i / count2;
                position = i - page * count2;
                Rect rect = new Rect((position % cols[j][page]) * emojiFullSize, (position / cols[j][page]) * emojiFullSize, (position % cols[j][page] + 1) * emojiFullSize, (position / cols[j][page] + 1) * emojiFullSize);
                rects.put(EmojiData.data[j][i], new DrawableInfo(rect, (byte) j, (byte) page));
            }
        }
        placeholderPaint = new Paint();
        placeholderPaint.setColor(0x00000000);
    }

    private static void loadEmoji(final int page, final int page2) {
        try {
            float scale;
            int imageResize = 1;
            if (AndroidUtilities.density <= 1.0f) {
                scale = 2.0f;
                imageResize = 2;
            } else if (AndroidUtilities.density <= 1.5f) {
                scale = 3.0f;
                imageResize = 2;
            } else if (AndroidUtilities.density <= 2.0f) {
                scale = 2.0f;
            } else {
                scale = 2.0f;
            }

            String imageName;
            File imageFile;

            try {
                for (int a = 4; a < 6; a++) {
                    imageName = String.format(Locale.US, "v%d_emoji%.01fx_%d.jpg", a, scale, page);
                    imageFile = ApplicationLoader.applicationContext.getFileStreamPath(imageName);
                    if (imageFile.exists()) {
                        imageFile.delete();
                    }
                    imageName = String.format(Locale.US, "v%d_emoji%.01fx_a_%d.jpg", a, scale, page);
                    imageFile = ApplicationLoader.applicationContext.getFileStreamPath(imageName);
                    if (imageFile.exists()) {
                        imageFile.delete();
                    }
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }


            imageName = String.format(Locale.US, "v7_emoji%.01fx_%d_%d.jpg", scale, page, page2);
            imageFile = ApplicationLoader.applicationContext.getFileStreamPath(imageName);
            if (!imageFile.exists()) {
                InputStream is = ApplicationLoader.applicationContext.getAssets().open("emoji/" + imageName);
                AndroidUtilities.copyFile(is, imageFile);
                is.close();
            }

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imageFile.getAbsolutePath(), opts);

            int width = opts.outWidth / imageResize;
            int height = opts.outHeight / imageResize;
            int stride = width * 4;

            final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Utilities.loadBitmap(imageFile.getAbsolutePath(), bitmap, imageResize, width, height, stride);

            imageName = String.format(Locale.US, "v7_emoji%.01fx_a_%d_%d.jpg", scale, page, page2);
            imageFile = ApplicationLoader.applicationContext.getFileStreamPath(imageName);
            if (!imageFile.exists()) {
                InputStream is = ApplicationLoader.applicationContext.getAssets().open("emoji/" + imageName);
                AndroidUtilities.copyFile(is, imageFile);
                is.close();
            }

            Utilities.loadBitmap(imageFile.getAbsolutePath(), bitmap, imageResize, width, height, stride);

            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    emojiBmp[page][page2] = bitmap;
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.emojiDidLoaded);
                }
            });
        } catch (Throwable x) {
            FileLog.e("tmessages", "Error loading emoji", x);
        }
    }

    public static void invalidateAll(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                invalidateAll(g.getChildAt(i));
            }
        } else if (view instanceof TextView) {
            view.invalidate();
        }
    }

    public static String fixEmoji(String emoji) {
        char ch;
        int lenght = emoji.length();
        for (int a = 0; a < lenght; a++) {
            ch = emoji.charAt(a);
            if (ch >= 0xD83C && ch <= 0xD83E) {
                if (ch == 0xD83C && a < lenght - 1) {
                    ch = emoji.charAt(a + 1);
                    if (ch == 0xDE2F || ch == 0xDC04 || ch == 0xDE1A || ch == 0xDD7F) {
                        emoji = emoji.substring(0, a + 2) + "\uFE0F" + emoji.substring(a + 2);
                        lenght++;
                        a += 2;
                    } else {
                        a++;
                    }
                } else {
                    a++;
                }
            } else if (ch == 0x20E3) {
                return emoji;
            } else if (ch >= 0x203C && ch <= 0x3299) {
                if (EmojiData.emojiToFE0FMap.containsKey(ch)) {
                    emoji = emoji.substring(0, a + 1) + "\uFE0F" + emoji.substring(a + 1);
                    lenght++;
                    a++;
                }
            }
        }
        return emoji;
    }

    public static EmojiDrawable getEmojiDrawable(CharSequence code) {
        DrawableInfo info = rects.get(code);
        if (info == null) {
            FileLog.e("tmessages", "No drawable for emoji " + code);
            return null;
        }
        EmojiDrawable ed = new EmojiDrawable(info);
        ed.setBounds(0, 0, drawImgSize, drawImgSize);
        return ed;
    }

    public static Drawable getEmojiBigDrawable(String code) {
        EmojiDrawable ed = getEmojiDrawable(code);
        if (ed == null) {
            return null;
        }
        ed.setBounds(0, 0, bigImgSize, bigImgSize);
        ed.fullSize = true;
        return ed;
    }

    public static class EmojiDrawable extends Drawable {
        private DrawableInfo info;
        private boolean fullSize = false;
        private static Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private static Rect rect = new Rect();

        public EmojiDrawable(DrawableInfo i) {
            info = i;
        }

        public DrawableInfo getDrawableInfo() {
            return info;
        }

        public Rect getDrawRect() {
            Rect original = getBounds();
            int cX = original.centerX(), cY = original.centerY();
            rect.left = cX - (fullSize ? bigImgSize : drawImgSize) / 2;
            rect.right = cX + (fullSize ? bigImgSize : drawImgSize) / 2;
            rect.top = cY - (fullSize ? bigImgSize : drawImgSize) / 2;
            rect.bottom = cY + (fullSize ? bigImgSize : drawImgSize) / 2;
            return rect;
        }

        @Override
        public void draw(Canvas canvas) {
            if (emojiBmp[info.page][info.page2] == null) {
                if (loadingEmoji[info.page][info.page2]) {
                    return;
                }
                loadingEmoji[info.page][info.page2] = true;
                Utilities.globalQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        loadEmoji(info.page, info.page2);
                        loadingEmoji[info.page][info.page2] = false;
                    }
                });
                canvas.drawRect(getBounds(), placeholderPaint);
                return;
            }

            Rect b;
            if (fullSize) {
                b = getDrawRect();
            } else {
                b = getBounds();
            }

            //if (!canvas.quickReject(b.left, b.top, b.right, b.bottom, Canvas.EdgeType.AA)) {
                canvas.drawBitmap(emojiBmp[info.page][info.page2], info.rect, b, paint);
            //}
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(ColorFilter cf) {

        }
    }

    private static class DrawableInfo {
        public Rect rect;
        public byte page;
        public byte page2;

        public DrawableInfo(Rect r, byte p, byte p2) {
            rect = r;
            page = p;
            page2 = p2;
        }
    }

    private static boolean inArray(char c, char[] a) {
        for (char cc : a) {
            if (cc == c) {
                return true;
            }
        }
        return false;
    }

    public static CharSequence replaceEmoji(CharSequence cs, Paint.FontMetricsInt fontMetrics, int size, boolean createNew) {
        if (cs == null || cs.length() == 0) {
            return cs;
        }
        //SpannableStringLight.isFieldsAvailable();
        //SpannableStringLight s = new SpannableStringLight(cs.toString());
        Spannable s;
        if (!createNew && cs instanceof Spannable) {
            s = (Spannable) cs;
        } else {
            s = Spannable.Factory.getInstance().newSpannable(cs.toString());
        }
        long buf = 0;
        int emojiCount = 0;
        char c;
        int startIndex = -1;
        int startLength = 0;
        int previousGoodIndex = 0;
        StringBuilder emojiCode = new StringBuilder(16);
        boolean nextIsSkinTone;
        EmojiDrawable drawable;
        EmojiSpan span;
        int length = cs.length();
        boolean doneEmoji = false;
        //s.setSpansCount(emojiCount);

        try {
            for (int i = 0; i < length; i++) {
                c = cs.charAt(i);
                if (c >= 0xD83C && c <= 0xD83E || (buf != 0 && (buf & 0xFFFFFFFF00000000L) == 0 && (buf & 0xFFFF) == 0xD83C && (c >= 0xDDE6 && c <= 0xDDFF))) {
                    if (startIndex == -1) {
                        startIndex = i;
                    }
                    emojiCode.append(c);
                    startLength++;
                    buf <<= 16;
                    buf |= c;
                } else if (buf > 0 && (c & 0xF000) == 0xD000) {
                    emojiCode.append(c);
                    startLength++;
                    buf = 0;
                    doneEmoji = true;
                } else if (c == 0x20E3) {
                    if (i > 0) {
                        char c2 = cs.charAt(previousGoodIndex);
                        if ((c2 >= '0' && c2 <= '9') || c2 == '#' || c2 == '*') {
                            startIndex = previousGoodIndex;
                            startLength = i - previousGoodIndex + 1;
                            emojiCode.append(c2);
                            emojiCode.append(c);
                            doneEmoji = true;
                        }
                    }
                } else if ((c == 0x00A9 || c == 0x00AE || c >= 0x203C && c <= 0x3299) && EmojiData.dataCharsMap.containsKey(c)) {
                    if (startIndex == -1) {
                        startIndex = i;
                    }
                    startLength++;
                    emojiCode.append(c);
                    doneEmoji = true;
                } else if (startIndex != -1) {
                    emojiCode.setLength(0);
                    startIndex = -1;
                    startLength = 0;
                    doneEmoji = false;
                }
                previousGoodIndex = i;
                for (int a = 0; a < 3; a++) {
                    if (i + 1 < length) {
                        c = cs.charAt(i + 1);
                        if (a == 1) {
                            if (c == 0x200D) {
                                emojiCode.append(c);
                                i++;
                                startLength++;
                                doneEmoji = false;
                            }
                        } else {
                            if (c >= 0xFE00 && c <= 0xFE0F) {
                                i++;
                                startLength++;
                            }
                        }
                    }
                }
                if (doneEmoji) {
                    if (i + 2 < length) {
                        if (cs.charAt(i + 1) == 0xD83C && cs.charAt(i + 2) >= 0xDFFB && cs.charAt(i + 2) <= 0xDFFF) {
                            emojiCode.append(cs.subSequence(i + 1, i + 3));
                            startLength += 2;
                            i += 2;
                        }
                    }
                    drawable = Emoji.getEmojiDrawable(emojiCode.subSequence(0, emojiCode.length()));
                    if (drawable != null) {
                        span = new EmojiSpan(drawable, DynamicDrawableSpan.ALIGN_BOTTOM, size, fontMetrics);
                        s.setSpan(span, startIndex, startIndex + startLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        emojiCount++;
                    }
                    startLength = 0;
                    startIndex = -1;
                    emojiCode.setLength(0);
                    doneEmoji = false;
                }
                if (emojiCount >= 50) { //654 new
                    break;
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            return cs;
        }
        return s;
    }

    public static class EmojiSpan extends ImageSpan {
        private Paint.FontMetricsInt fontMetrics = null;
        private int size = AndroidUtilities.dp(20);

        public EmojiSpan(EmojiDrawable d, int verticalAlignment, int s, Paint.FontMetricsInt original) {
            super(d, verticalAlignment);
            fontMetrics = original;
            if (original != null) {
                size = Math.abs(fontMetrics.descent) + Math.abs(fontMetrics.ascent);
                if (size == 0) {
                    size = AndroidUtilities.dp(20);
                }
            }
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (fm == null) {
                fm = new Paint.FontMetricsInt();
            }

            if (fontMetrics == null) {
                int sz = super.getSize(paint, text, start, end, fm);

                int offset = AndroidUtilities.dp(8);
                int w = AndroidUtilities.dp(10);
                fm.top = -w - offset;
                fm.bottom = w - offset;
                fm.ascent = -w - offset;
                fm.leading = 0;
                fm.descent = w - offset;

                return sz;
            } else {
                if (fm != null) {
                    fm.ascent = fontMetrics.ascent;
                    fm.descent = fontMetrics.descent;

                    fm.top = fontMetrics.top;
                    fm.bottom = fontMetrics.bottom;
                }
                if (getDrawable() != null) {
                    getDrawable().setBounds(0, 0, size, size);
                }
                return size;
            }
        }
    }
}
