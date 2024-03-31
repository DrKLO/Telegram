/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.content.SharedPreferences;
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
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.checkerframework.checker.units.qual.A;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;

public class Emoji {

    private static HashMap<CharSequence, DrawableInfo> rects = new HashMap<>();
    public static int drawImgSize;
    public static int bigImgSize;
    private static boolean inited = false;
    public static Paint placeholderPaint;
    private static int[] emojiCounts = new int[]{
        EmojiData.data[0].length,
        EmojiData.data[1].length,
        EmojiData.data[2].length,
        EmojiData.data[3].length,
        EmojiData.data[4].length,
        EmojiData.data[5].length,
        EmojiData.data[6].length,
        EmojiData.data[7].length
    };
    private static Bitmap[][] emojiBmp = new Bitmap[8][];
    private static boolean[][] loadingEmoji = new boolean[8][];

    public static HashMap<String, Integer> emojiUseHistory = new HashMap<>();
    public static ArrayList<String> recentEmoji = new ArrayList<>();
    public static HashMap<String, String> emojiColor = new HashMap<>();
    private static boolean recentEmojiLoaded;
    public static Runnable invalidateUiRunnable = () -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.emojiLoaded);
    public static float emojiDrawingYOffset;
    public static boolean emojiDrawingUseAlpha = true;

    private static String[] DEFAULT_RECENT = new String[]{
        "\uD83D\uDE02", "\uD83D\uDE18", "\u2764", "\uD83D\uDE0D", "\uD83D\uDE0A", "\uD83D\uDE01",
        "\uD83D\uDC4D", "\u263A", "\uD83D\uDE14", "\uD83D\uDE04", "\uD83D\uDE2D", "\uD83D\uDC8B",
        "\uD83D\uDE12", "\uD83D\uDE33", "\uD83D\uDE1C", "\uD83D\uDE48", "\uD83D\uDE09", "\uD83D\uDE03",
        "\uD83D\uDE22", "\uD83D\uDE1D", "\uD83D\uDE31", "\uD83D\uDE21", "\uD83D\uDE0F", "\uD83D\uDE1E",
        "\uD83D\uDE05", "\uD83D\uDE1A", "\uD83D\uDE4A", "\uD83D\uDE0C", "\uD83D\uDE00", "\uD83D\uDE0B",
        "\uD83D\uDE06", "\uD83D\uDC4C", "\uD83D\uDE10", "\uD83D\uDE15"
    };

    private final static int MAX_RECENT_EMOJI_COUNT = 48;

    static {
        drawImgSize = AndroidUtilities.dp(20);
        bigImgSize = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 40 : 34);
        for (int a = 0; a < emojiBmp.length; a++) {
            emojiBmp[a] = new Bitmap[emojiCounts[a]];
            loadingEmoji[a] = new boolean[emojiCounts[a]];
        }

        for (int j = 0; j < EmojiData.data.length; j++) {
            int position;
            for (int i = 0; i < EmojiData.data[j].length; i++) {
                rects.put(EmojiData.data[j][i], new DrawableInfo((byte) j, (short) i, i));
            }
        }
        placeholderPaint = new Paint();
        placeholderPaint.setColor(0x00000000);
    }

    public static void preloadEmoji(CharSequence code) {
        final DrawableInfo info = getDrawableInfo(code);
        if (info != null) {
            loadEmoji(info.page, info.page2);
        }
    }

    private static void loadEmoji(final byte page, final short page2) {
        if (emojiBmp[page][page2] == null) {
            if (loadingEmoji[page][page2]) {
                return;
            }
            loadingEmoji[page][page2] = true;
            Utilities.globalQueue.postRunnable(() -> {
                final Bitmap bitmap = loadBitmap("emoji/" + String.format(Locale.US, "%d_%d.png", page, page2));
                if (bitmap != null) {
                    emojiBmp[page][page2] = bitmap;
                    AndroidUtilities.cancelRunOnUIThread(invalidateUiRunnable);
                    AndroidUtilities.runOnUIThread(invalidateUiRunnable);
                }
                loadingEmoji[page][page2] = false;
            });
        }
    }

    public static Bitmap loadBitmap(String path) {
        try {
            int imageResize;
            if (AndroidUtilities.density <= 1.0f) {
                imageResize = 2;
            } else {
                imageResize = 1;
            }

            Bitmap bitmap = null;
            try {
                InputStream is = ApplicationLoader.applicationContext.getAssets().open(path);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = false;
                opts.inSampleSize = imageResize;
                bitmap = BitmapFactory.decodeStream(is, null, opts);
                is.close();
            } catch (Throwable e) {
                FileLog.e(e);
            }
            return bitmap;
        } catch (Throwable x) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("Error loading emoji", x);
            }
        }
        return null;
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
        int length = emoji.length();
        for (int a = 0; a < length; a++) {
            ch = emoji.charAt(a);
            if (ch >= 0xD83C && ch <= 0xD83E) {
                if (ch == 0xD83C && a < length - 1) {
                    ch = emoji.charAt(a + 1);
                    if (ch == 0xDE2F || ch == 0xDC04 || ch == 0xDE1A || ch == 0xDD7F) {
                        emoji = emoji.substring(0, a + 2) + "\uFE0F" + emoji.substring(a + 2);
                        length++;
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
                    length++;
                    a++;
                }
            }
        }
        return emoji;
    }

    public static EmojiDrawable getEmojiDrawable(CharSequence code) {
        DrawableInfo info = getDrawableInfo(code);
        if (info == null) {
            if (code != null) {
                CompoundEmoji.CompoundEmojiDrawable compoundEmojiDrawable = CompoundEmoji.getCompoundEmojiDrawable(code.toString());
                if (compoundEmojiDrawable != null) {
                    compoundEmojiDrawable.setBounds(0, 0, drawImgSize, drawImgSize);
                    return compoundEmojiDrawable;
                }
            }
            return null;
        }
        EmojiDrawable ed = new SimpleEmojiDrawable(info, endsWithRightArrow(code));
        ed.setBounds(0, 0, drawImgSize, drawImgSize);
        return ed;
    }

    public static boolean endsWithRightArrow(CharSequence code) {
        return code != null && code.length() > 2 &&
            code.charAt(code.length() - 2) == '‍' &&
            code.charAt(code.length() - 1) == '➡';
    }

    private static DrawableInfo getDrawableInfo(CharSequence code) {
        if (endsWithRightArrow(code)) {
            code = code.subSequence(0, code.length() - 2);
        }
        DrawableInfo info = rects.get(code);
        if (info == null) {
            CharSequence newCode = EmojiData.emojiAliasMap.get(code);
            if (newCode != null) {
                info = Emoji.rects.get(newCode);
            }
        }
        return info;
    }

    public static boolean isValidEmoji(CharSequence code) {
        if (TextUtils.isEmpty(code)) {
            return false;
        }
        DrawableInfo info = rects.get(code);
        if (info == null) {
            CharSequence newCode = EmojiData.emojiAliasMap.get(code);
            if (newCode != null) {
                info = Emoji.rects.get(newCode);
            }
        }
        return info != null;
    }

    public static Drawable getEmojiBigDrawable(String code) {
        EmojiDrawable ed = null;
        CompoundEmoji.CompoundEmojiDrawable compoundEmojiDrawable = CompoundEmoji.getCompoundEmojiDrawable(code);
        if (compoundEmojiDrawable != null) {
            compoundEmojiDrawable.setBounds(0, 0, drawImgSize, drawImgSize);
            ed = compoundEmojiDrawable;
        }
        if (ed == null) {
            ed = getEmojiDrawable(code);
        }
        if (ed == null) {
            CharSequence newCode = EmojiData.emojiAliasMap.get(code);
            if (newCode != null) {
                ed = Emoji.getEmojiDrawable(newCode);
            }
        }
        if (ed == null) {
            return null;
        }
        ed.setBounds(0, 0, bigImgSize, bigImgSize);
        ed.fullSize = true;
        return ed;
    }

    public static abstract class EmojiDrawable extends Drawable {
        boolean fullSize = false;
        int placeholderColor = 0x10000000;

        public boolean isLoaded() {
            return false;
        }
        public void preload() {}
    }

    public static class SimpleEmojiDrawable extends EmojiDrawable {
        private DrawableInfo info;
        private static Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private static Rect rect = new Rect();
        private boolean invert;

        public SimpleEmojiDrawable(DrawableInfo i, boolean invert) {
            info = i;
            this.invert = invert;
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
            if (!isLoaded()) {
                loadEmoji(info.page, info.page2);
                placeholderPaint.setColor(placeholderColor);
                Rect bounds = getBounds();
                canvas.drawCircle(bounds.centerX(), bounds.centerY(), bounds.width() * .4f, placeholderPaint);
                return;
            }

            Rect b;
            if (fullSize) {
                b = getDrawRect();
            } else {
                b = getBounds();
            }

            if (!canvas.quickReject(b.left, b.top, b.right, b.bottom, Canvas.EdgeType.AA)) {
                if (invert) {
                    canvas.save();
                    canvas.scale(-1, 1, b.centerX(), b.centerY());
                }
                canvas.drawBitmap(emojiBmp[info.page][info.page2], null, b, paint);
                if (invert) {
                    canvas.restore();
                }
            }
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {

        }

        @Override
        public boolean isLoaded() {
            return emojiBmp[info.page][info.page2] != null;
        }

        @Override
        public void preload() {
            if (!isLoaded()) {
                loadEmoji(info.page, info.page2);
            }
        }
    }

    private static class DrawableInfo {
        public byte page;
        public short page2;
        public int emojiIndex;

        public DrawableInfo(byte p, short p2, int index) {
            page = p;
            page2 = p2;
            emojiIndex = index;
        }
    }

    public static class EmojiSpanRange {
        public EmojiSpanRange(int start, int end, CharSequence code) {
            this.start = start;
            this.end = end;
            this.code = code;
        }
        public int start;
        public int end;
        public CharSequence code;
    }

    public static boolean fullyConsistsOfEmojis(CharSequence cs) {
        int[] emojiOnly = new int[1];
        parseEmojis(cs, emojiOnly);
        return emojiOnly[0] > 0;
    }

    public static ArrayList<EmojiSpanRange> parseEmojis(CharSequence cs) {
        return parseEmojis(cs, null);
    }

    public static ArrayList<EmojiSpanRange> parseEmojis(CharSequence cs, int[] emojiOnly) {
        ArrayList<EmojiSpanRange> emojis = new ArrayList<>();
        if (cs == null || cs.length() <= 0) {
            return emojis;
        }
        long buf = 0;
        char c;
        int startIndex = -1;
        int startLength = 0;
        int previousGoodIndex = 0;
        StringBuilder emojiCode = new StringBuilder(16);
        final int length = cs.length();
        boolean doneEmoji = false;
        boolean notOnlyEmoji;
        boolean resetStartIndex = false;

        try {
            for (int i = 0; i < length; i++) {
                c = cs.charAt(i);
                notOnlyEmoji = false;
                if (c >= 0xD83C && c <= 0xD83E || (buf != 0 && (buf & 0xFFFFFFFF00000000L) == 0 && (buf & 0xFFFF) == 0xD83C && (c >= 0xDDE6 && c <= 0xDDFF))) {
                    if (startIndex == -1) {
                        startIndex = i;
                    } else if (resetStartIndex) {
                        startIndex = i;
                        startLength = 0;
                        resetStartIndex = false;
                    }
                    emojiCode.append(c);
                    startLength++;
                    buf <<= 16;
                    buf |= c;
                } else if (emojiCode.length() > 0 && (c == 0x2640 || c == 0x2642 || c == 0x2695) || buf > 0 && (c & 0xF000) == 0xD000) {
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
                            resetStartIndex = false;
                        }
                    }
                } else if ((c == 0x00A9 || c == 0x00AE || c >= 0x203C && c <= 0x3299) && EmojiData.dataCharsMap.containsKey(c)) {
                    if (startIndex == -1) {
                        startIndex = i;
                    } else if (resetStartIndex) {
                        startIndex = i;
                        startLength = 0;
                        resetStartIndex = false;
                    }
                    startLength++;
                    emojiCode.append(c);
                    doneEmoji = true;
                } else if (startIndex != -1) {
                    emojiCode.setLength(0);
                    startIndex = -1;
                    startLength = 0;
                    doneEmoji = false;
                    resetStartIndex = false;
                } else if (c != 0xfe0f && c != '\n' && c != ' ' && c != '\t') {
                    notOnlyEmoji = true;
                }
                if (doneEmoji && i + 2 < length) {
                    char next = cs.charAt(i + 1);
                    if (next == 0xD83C) {
                        next = cs.charAt(i + 2);
                        if (next >= 0xDFFB && next <= 0xDFFF) {
                            emojiCode.append(cs.subSequence(i + 1, i + 3));
                            startLength += 2;
                            i += 2;
                        }
                    } else if (emojiCode.length() >= 2 && emojiCode.charAt(0) == 0xD83C && emojiCode.charAt(1) == 0xDFF4 && next == 0xDB40) {
                        i++;
                        while (true) {
                            if (i < cs.length()) {
                                emojiCode.append(cs.charAt(i));
                            }
                            if (i + 1 < cs.length()) {
                                emojiCode.append(cs.charAt(i + 1));
                            }
                            startLength += 2;
                            i += 2;
                            if (i >= cs.length() || cs.charAt(i) != 0xDB40) {
                                i--;
                                break;
                            }
                        }
                    }
                }
                previousGoodIndex = i;
                char prevCh = c;
                for (int a = 0; a < 3; a++) {
                    if (i + 1 < length) {
                        c = cs.charAt(i + 1);
                        if (a == 1) {
                            if (c == 0x200D && emojiCode.length() > 0) {
                                notOnlyEmoji = false;
                                emojiCode.append(c);
                                i++;
                                startLength++;
                                doneEmoji = false;
                            }
                        } else if (prevCh == '*' || prevCh == '#' || prevCh >= '0' && prevCh <= '9') {
                            if (c >= 0xFE00 && c <= 0xFE0F) {
                                startIndex = previousGoodIndex;
                                resetStartIndex = true;
                                i++;
                                startLength++;
                                if (!doneEmoji) {
                                    doneEmoji = i + 1 >= length;
                                }
                            }
                        } else if (startIndex != -1) {
                            if (c >= 0xFE00 && c <= 0xFE0F) {
                                i++;
                                startLength++;
                                if (!doneEmoji) {
                                    doneEmoji = i + 1 >= length;
                                }
                            }
                        }
                    }
                }
                if (notOnlyEmoji && emojiOnly != null) {
                    emojiOnly[0] = 0;
                    emojiOnly = null;
                }
                if (doneEmoji && i + 2 < length && cs.charAt(i + 1) == 0xD83C) {
                    char next = cs.charAt(i + 2);
                    if (next >= 0xDFFB && next <= 0xDFFF) {
                        emojiCode.append(cs.subSequence(i + 1, i + 3));
                        startLength += 2;
                        i += 2;
                    }
                }
                if (doneEmoji) {
                    if (emojiOnly != null) {
                        emojiOnly[0]++;
                    }
                    if (startIndex >= 0 && startIndex + startLength <= length) {
                        emojis.add(new EmojiSpanRange(startIndex, startIndex + startLength, emojiCode.subSequence(0, emojiCode.length())));
                    }
                    startLength = 0;
                    startIndex = -1;
                    emojiCode.setLength(0);
                    doneEmoji = false;
                    resetStartIndex = false;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (emojiOnly != null && emojiCode.length() != 0) {
            emojiOnly[0] = 0;
        }
        return emojis;
    }

    public static CharSequence replaceEmoji(CharSequence cs, Paint.FontMetricsInt fontMetrics, boolean createNew) {
        return replaceEmoji(cs, fontMetrics, createNew, null);
    }

    public static CharSequence replaceEmoji(CharSequence cs, Paint.FontMetricsInt fontMetrics, int size, boolean createNew) {
        return replaceEmoji(cs, fontMetrics, createNew, null);
    }

    public static CharSequence replaceEmoji(CharSequence cs, Paint.FontMetricsInt fontMetrics, boolean createNew, int[] emojiOnly) {
        return replaceEmoji(cs, fontMetrics, createNew, emojiOnly, DynamicDrawableSpan.ALIGN_BOTTOM);
    }

    public static CharSequence replaceEmoji(CharSequence cs, Paint.FontMetricsInt fontMetrics, boolean createNew, int[] emojiOnly, int alignment) {
        if (SharedConfig.useSystemEmoji || cs == null || cs.length() == 0) {
            return cs;
        }
        Spannable s;
        if (!createNew && cs instanceof Spannable) {
            s = (Spannable) cs;
        } else {
            s = Spannable.Factory.getInstance().newSpannable(cs.toString());
        }
        ArrayList<EmojiSpanRange> emojis = parseEmojis(s, emojiOnly);
        if (emojis.isEmpty()) {
            return cs;
        }

        AnimatedEmojiSpan[] animatedEmojiSpans = s.getSpans(0, s.length(), AnimatedEmojiSpan.class);
        EmojiSpan span;
        Drawable drawable;
        int limitCount = SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH ? 100 : 50;
        for (int i = 0; i < emojis.size(); ++i) {
            try {
                EmojiSpanRange emojiRange = emojis.get(i);
                if (animatedEmojiSpans != null) {
                    boolean hasAnimated = false;
                    for (int j = 0; j < animatedEmojiSpans.length; ++j) {
                        AnimatedEmojiSpan animatedSpan = animatedEmojiSpans[j];
                        if (animatedSpan != null && s.getSpanStart(animatedSpan) == emojiRange.start && s.getSpanEnd(animatedSpan) == emojiRange.end) {
                            hasAnimated = true;
                            break;
                        }
                    }
                    if (hasAnimated) {
                        continue;
                    }
                }
                drawable = Emoji.getEmojiDrawable(emojiRange.code);
                if (drawable != null) {
                    span = new EmojiSpan(drawable, alignment, fontMetrics);
                    span.emoji = emojiRange.code == null ? null : emojiRange.code.toString();
                    s.setSpan(span, emojiRange.start, emojiRange.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if ((Build.VERSION.SDK_INT < 23 || Build.VERSION.SDK_INT >= 29)/* && !BuildVars.DEBUG_PRIVATE_VERSION*/ && (i + 1) >= limitCount) {
                break;
            }
        }
        return s;
    }

    public static CharSequence replaceWithRestrictedEmoji(CharSequence cs, TextView textView, Runnable update) {
        return replaceWithRestrictedEmoji(cs, textView.getPaint().getFontMetricsInt(), update);
    }

    public static CharSequence replaceWithRestrictedEmoji(CharSequence cs, Paint.FontMetricsInt fontMetrics, Runnable update) {
        if (SharedConfig.useSystemEmoji || cs == null || cs.length() == 0) {
            return cs;
        }

        final int currentAccount = UserConfig.selectedAccount;
        TLRPC.InputStickerSet inputStickerSet = new TLRPC.TL_inputStickerSetShortName();
        inputStickerSet.short_name = "RestrictedEmoji";
        TLRPC.TL_messages_stickerSet set = MediaDataController.getInstance(currentAccount).getStickerSet(inputStickerSet, 0, false, true, update == null ? null : s -> update.run());

        Spannable s;
        if (cs instanceof Spannable) {
            s = (Spannable) cs;
        } else {
            s = Spannable.Factory.getInstance().newSpannable(cs.toString());
        }
        ArrayList<EmojiSpanRange> emojis = parseEmojis(s, null);
        if (emojis.isEmpty()) {
            return cs;
        }

        AnimatedEmojiSpan[] animatedEmojiSpans = s.getSpans(0, s.length(), AnimatedEmojiSpan.class);
        EmojiSpan span;
        Drawable drawable;
        int limitCount = SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH ? 100 : 50;
        for (int i = 0; i < emojis.size(); ++i) {
            try {
                EmojiSpanRange emojiRange = emojis.get(i);
                if (animatedEmojiSpans != null) {
                    boolean hasAnimated = false;
                    for (int j = 0; j < animatedEmojiSpans.length; ++j) {
                        AnimatedEmojiSpan animatedSpan = animatedEmojiSpans[j];
                        if (animatedSpan != null && s.getSpanStart(animatedSpan) == emojiRange.start && s.getSpanEnd(animatedSpan) == emojiRange.end) {
                            hasAnimated = true;
                            break;
                        }
                    }
                    if (hasAnimated) {
                        continue;
                    }
                }
                TLRPC.Document document = null;
                if (set != null) {
                    for (TLRPC.Document d : set.documents) {
                        if (MessageObject.findAnimatedEmojiEmoticon(d, null).contains(emojiRange.code)) {
                            document = d;
                            break;
                        }
                    }
                }
                AnimatedEmojiSpan animatedSpan;
                if (document != null) {
                    animatedSpan = new AnimatedEmojiSpan(document, fontMetrics);
                } else {
                    animatedSpan = new AnimatedEmojiSpan(0, fontMetrics);
                }
                animatedSpan.emoji = (emojiRange.code).toString();
                animatedSpan.cacheType = AnimatedEmojiDrawable.CACHE_TYPE_STANDARD_EMOJI;
                s.setSpan(animatedSpan, emojiRange.start, emojiRange.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception e) {
                FileLog.e(e);
            }
            if ((Build.VERSION.SDK_INT < 23 || Build.VERSION.SDK_INT >= 29)/* && !BuildVars.DEBUG_PRIVATE_VERSION*/ && (i + 1) >= limitCount) {
                break;
            }
        }
        return s;
    }

    public static class EmojiSpan extends ImageSpan {
        public Paint.FontMetricsInt fontMetrics;
        public float scale = 1f;
        public int size = AndroidUtilities.dp(20);
        public String emoji;

        public EmojiSpan(Drawable d, int verticalAlignment, Paint.FontMetricsInt original) {
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

        public void replaceFontMetrics(Paint.FontMetricsInt newMetrics) {
            fontMetrics = newMetrics;
            if (fontMetrics != null) {
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

            int scaledSize = (int) (scale * size);
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
                    getDrawable().setBounds(0, 0, scaledSize, scaledSize);
                }
                return scaledSize;
            }
        }

        public boolean drawn;
        public float lastDrawX, lastDrawY;

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            lastDrawX = x + scale * size / 2f;
            lastDrawY = top + (bottom - top) / 2f;
            drawn = true;

            boolean restoreAlpha = false;
            if (paint.getAlpha() != 255 && emojiDrawingUseAlpha) {
                restoreAlpha = true;
                getDrawable().setAlpha(paint.getAlpha());
            }
            boolean needRestore = false;
            float ty = emojiDrawingYOffset - (size - scale * size) / 2;
            if (ty != 0) {
                needRestore = true;
                canvas.save();
                canvas.translate(0, ty);
            }
            super.draw(canvas, text, start, end, x, top, y, bottom, paint);
            if (needRestore) {
                canvas.restore();
            }
            if (restoreAlpha) {
                getDrawable().setAlpha(255);
            }
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            if (getDrawable() instanceof EmojiDrawable) {
                ((EmojiDrawable) getDrawable()).placeholderColor = 0x10ffffff & ds.getColor();
            }
            super.updateDrawState(ds);
        }
    }

    public static void addRecentEmoji(String code) {
        Integer count = emojiUseHistory.get(code);
        if (count == null) {
            count = 0;
        }
        if (count == 0 && emojiUseHistory.size() >= MAX_RECENT_EMOJI_COUNT) {
            String emoji = recentEmoji.get(recentEmoji.size() - 1);
            emojiUseHistory.remove(emoji);
            recentEmoji.set(recentEmoji.size() - 1, code);
        }
        emojiUseHistory.put(code, ++count);
    }

    public static void removeRecentEmoji(String code) {
        emojiUseHistory.remove(code);
        recentEmoji.remove(code);
        if (emojiUseHistory.isEmpty() || recentEmoji.isEmpty()) {
            addRecentEmoji(DEFAULT_RECENT[0]);
        }
    }

    public static void sortEmoji() {
        recentEmoji.clear();
        for (HashMap.Entry<String, Integer> entry : emojiUseHistory.entrySet()) {
            recentEmoji.add(entry.getKey());
        }
        Collections.sort(recentEmoji, (lhs, rhs) -> {
            Integer count1 = emojiUseHistory.get(lhs);
            Integer count2 = emojiUseHistory.get(rhs);
            if (count1 == null) {
                count1 = 0;
            }
            if (count2 == null) {
                count2 = 0;
            }
            if (count1 > count2) {
                return -1;
            } else if (count1 < count2) {
                return 1;
            }
            return 0;
        });
        while (recentEmoji.size() > MAX_RECENT_EMOJI_COUNT) {
            recentEmoji.remove(recentEmoji.size() - 1);
        }
    }

    public static void saveRecentEmoji() {
        SharedPreferences preferences = MessagesController.getGlobalEmojiSettings();
        StringBuilder stringBuilder = new StringBuilder();
        for (HashMap.Entry<String, Integer> entry : emojiUseHistory.entrySet()) {
            if (stringBuilder.length() != 0) {
                stringBuilder.append(",");
            }
            stringBuilder.append(entry.getKey());
            stringBuilder.append("=");
            stringBuilder.append(entry.getValue());
        }
        preferences.edit().putString("emojis2", stringBuilder.toString()).commit();
    }

    public static void clearRecentEmoji() {
        SharedPreferences preferences = MessagesController.getGlobalEmojiSettings();
        preferences.edit().putBoolean("filled_default", true).commit();
        emojiUseHistory.clear();
        recentEmoji.clear();
        saveRecentEmoji();
    }

    public static void loadRecentEmoji() {
        if (recentEmojiLoaded) {
            return;
        }
        recentEmojiLoaded = true;
        SharedPreferences preferences = MessagesController.getGlobalEmojiSettings();

        String str;
        try {
            emojiUseHistory.clear();
            if (preferences.contains("emojis")) {
                str = preferences.getString("emojis", "");
                if (str != null && str.length() > 0) {
                    String[] args = str.split(",");
                    for (String arg : args) {
                        String[] args2 = arg.split("=");
                        long value = Utilities.parseLong(args2[0]);
                        StringBuilder string = new StringBuilder();
                        for (int a = 0; a < 4; a++) {
                            char ch = (char) value;
                            string.insert(0, ch);
                            value >>= 16;
                            if (value == 0) {
                                break;
                            }
                        }
                        if (string.length() > 0) {
                            emojiUseHistory.put(string.toString(), Utilities.parseInt(args2[1]));
                        }
                    }
                }
                preferences.edit().remove("emojis").commit();
                saveRecentEmoji();
            } else {
                str = preferences.getString("emojis2", "");
                if (str != null && str.length() > 0) {
                    String[] args = str.split(",");
                    for (String arg : args) {
                        String[] args2 = arg.split("=");
                        emojiUseHistory.put(args2[0], Utilities.parseInt(args2[1]));
                    }
                }
            }
            if (emojiUseHistory.isEmpty()) {
                if (!preferences.getBoolean("filled_default", false)) {
                    for (int i = 0; i < DEFAULT_RECENT.length; i++) {
                        emojiUseHistory.put(DEFAULT_RECENT[i], DEFAULT_RECENT.length - i);
                    }
                    preferences.edit().putBoolean("filled_default", true).commit();
                    saveRecentEmoji();
                }
            }
            sortEmoji();
        } catch (Exception e) {
            FileLog.e(e);
        }

        try {
            str = preferences.getString("color", "");
            if (str != null && str.length() > 0) {
                String[] args = str.split(",");
                for (int a = 0; a < args.length; a++) {
                    String arg = args[a];
                    String[] args2 = arg.split("=");
                    emojiColor.put(args2[0], args2[1]);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void saveEmojiColors() {
        SharedPreferences preferences = MessagesController.getGlobalEmojiSettings();
        StringBuilder stringBuilder = new StringBuilder();
        for (HashMap.Entry<String, String> entry : emojiColor.entrySet()) {
            if (stringBuilder.length() != 0) {
                stringBuilder.append(",");
            }
            stringBuilder.append(entry.getKey());
            stringBuilder.append("=");
            stringBuilder.append(entry.getValue());
        }
        preferences.edit().putString("color", stringBuilder.toString()).commit();
    }
}
