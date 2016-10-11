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
import android.os.Build;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
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
            {12, 12, 12, 12},
            {6, 6, 6, 6},
            {9, 9, 9, 9},
            {9, 9, 9, 9},
            {8, 8, 8, 7}
    };

    static {
        int emojiFullSize;
        int add = 2;
        if (AndroidUtilities.density <= 1.0f) {
            emojiFullSize = 32;
            add = 1;
        } else if (AndroidUtilities.density <= 1.5f) {
            emojiFullSize = 64;
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
                int row = position % cols[j][page];
                int col = position / cols[j][page];
                Rect rect = new Rect(row * emojiFullSize + row * add, col * emojiFullSize + col * add, (row + 1) * emojiFullSize + row * add, (col + 1) * emojiFullSize + col * add);
                rects.put(EmojiData.data[j][i], new DrawableInfo(rect, (byte) j, (byte) page, i));
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
                //scale = 3.0f;
                //imageResize = 2;
                scale = 2.0f;
            } else if (AndroidUtilities.density <= 2.0f) {
                scale = 2.0f;
            } else {
                scale = 2.0f;
            }

            /*String q = "";
            for (int a = 0; a < EmojiData.data.length; a++) {
                String arr[] = EmojiData.data[a];
                for (int b = 0; b < arr.length; b++) {
                    String emoji = arr[b];
                    for (int c = 0; c < emoji.length(); c++) {
                        if (emoji.charAt(c) == '\ufe0f') {
                            q += String.format("0x%x, ", (int) emoji.charAt(0));
                            break;
                        }
                    }
                }
            }
            FileLog.e("tmessages", q);*/

            String imageName;
            File imageFile;

            try {
                for (int a = 4; a < 7; a++) {
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
                for (int a = 8; a < 10; a++) {
                    imageName = String.format(Locale.US, "v%d_emoji%.01fx_%d.png", a, scale, page);
                    imageFile = ApplicationLoader.applicationContext.getFileStreamPath(imageName);
                    if (imageFile.exists()) {
                        imageFile.delete();
                    }
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            Bitmap bitmap = null;
            try {
                InputStream is = ApplicationLoader.applicationContext.getAssets().open("emoji/" + String.format(Locale.US, "v10_emoji%.01fx_%d_%d.png", scale, page, page2));
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = false;
                opts.inSampleSize = imageResize;
                bitmap = BitmapFactory.decodeStream(is, null, opts);
                is.close();
            } catch (Throwable e) {
                FileLog.e("tmessages", e);
            }

            final Bitmap finalBitmap = bitmap;
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    emojiBmp[page][page2] = finalBitmap;
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
        private static TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

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
            /*if (MessagesController.getInstance().useSystemEmoji) {
                //textPaint.setTextSize(getBounds().width());
                canvas.drawText(EmojiData.data[info.page][info.emojiIndex], getBounds().left, getBounds().bottom, textPaint);
                return;
            }*/
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
        public int emojiIndex;

        public DrawableInfo(Rect r, byte p, byte p2, int index) {
            rect = r;
            page = p;
            page2 = p2;
            emojiIndex = index;
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
        return replaceEmoji(cs, fontMetrics, size, createNew, null);
    }

    public static CharSequence replaceEmoji(CharSequence cs, Paint.FontMetricsInt fontMetrics, int size, boolean createNew, int[] emojiOnly) {
        if (MessagesController.getInstance().useSystemEmoji || cs == null || cs.length() == 0) {
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
                } else if (emojiCode.length() > 0 && (c == 0x2640 || c == 0x2642)) {
                    emojiCode.append(c);
                    startLength++;
                    buf = 0;
                    doneEmoji = true;
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
                } else if (c != 0xfe0f) {
                    if (emojiOnly != null) {
                        emojiOnly[0] = 0;
                        emojiOnly = null;
                    }
                }
                previousGoodIndex = i;
                for (int a = 0; a < 3; a++) {
                    if (i + 1 < length) {
                        c = cs.charAt(i + 1);
                        if (a == 1) {
                            if (c == 0x200D && emojiCode.length() > 0) {
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
                    if (emojiOnly != null) {
                        emojiOnly[0]++;
                    }
                    if (i + 2 < length) {
                        if (cs.charAt(i + 1) == 0xD83C && cs.charAt(i + 2) >= 0xDFFB && cs.charAt(i + 2) <= 0xDFFF) {
                            emojiCode.append(cs.subSequence(i + 1, i + 3));
                            startLength += 2;
                            i += 2;
                        }
                    }
                    if (i + 2 < length) {
                        if (cs.charAt(i + 1) == 0x200D && (cs.charAt(i + 2) == 0x2640 || cs.charAt(i + 2) == 0x2642)) {
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
                if (Build.VERSION.SDK_INT < 23 && emojiCount >= 50) {
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

        public void replaceFontMetrics(Paint.FontMetricsInt newMetrics, int newSize) {
            fontMetrics = newMetrics;
            size = newSize;
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
