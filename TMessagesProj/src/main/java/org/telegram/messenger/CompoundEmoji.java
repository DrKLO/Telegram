package org.telegram.messenger;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.util.Pair;
import android.util.SparseArray;
import android.view.View;

import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class CompoundEmoji {

    public static List<String> skinTones = Arrays.asList("🏻", "🏼", "🏽", "🏾", "🏿");

    public static int getSkinTone(String string) {
        if (string == null) {
            return -1;
        }
        return skinTones.indexOf(string.substring(string.length() - 2));
    }

    public static CompoundEmojiDrawable getCompoundEmojiDrawable(String string) {
        return getCompoundEmojiDrawable(string, null, null);
    }

    public static CompoundEmojiDrawable getCompoundEmojiDrawable(String string, Integer overrideSkinTone1, Integer overrideSkinTone2) {
        if (string == null) {
            return null;
        }

        Pair<Integer, Integer> handshakeTones = isHandshake(string);
        if (handshakeTones != null) {
            return new CompoundEmojiDrawable(
                new DrawableInfo(0, overrideSkinTone1 != null ? overrideSkinTone1 : handshakeTones.first, 0),
                new DrawableInfo(0, overrideSkinTone2 != null ? overrideSkinTone2 : handshakeTones.second, 1)
            );
        }

        return null;
    }

    public static Pair<Integer, Integer> isHandshake(String code) {

        int skinTone1 = -1, skinTone2 = -1;

        // simple handshake 🤝
        if (
            code.startsWith("\uD83E\uDD1D") && // 🤝
            (code.length() == 2 || code.length() == 4 && (skinTone1 = getSkinTone(code)) >= 0)
        ) {
            return new Pair(skinTone1, skinTone1);
        }

        // compound handshake 🫱 + ZWJ + 🫲
        String[] parts = code.split("\u200D"); // ZWJ
        if (
            parts.length == 2 &&
            parts[0].startsWith("\uD83E\uDEF1") && // 🫱
            parts[1].startsWith("\uD83E\uDEF2") && // 🫲
            (parts[0].length() == 2 || parts[0].length() == 4 && (skinTone1 = getSkinTone(parts[0])) >= 0) &&
            (parts[1].length() == 2 || parts[1].length() == 4 && (skinTone2 = getSkinTone(parts[1])) >= 0)
        ) {
            return new Pair(skinTone1, skinTone2);
        }

        return null;
    }

    public static String applyColor(String code, String color) {
        if (isHandshake(code) == null) {
            return code;
        }

        if (color == null) {
            return "\uD83E\uDD1D";
        } else if (color.contains("\u200D")) {
            String[] parts = color.split("\u200D");
            return "\uD83E\uDEF1" + (parts.length >= 1 ? parts[0] : "") + "\u200D" + "\uD83E\uDEF2" + (parts.length >= 2 ? parts[1] : "");
        } else {
            return "\uD83E\uDD1D" + color;
        }
    }

    public static boolean isCompound(String string) {
        return getCompoundEmojiDrawable(string) != null;
    }

    private static class DrawableInfo {

        private static final SparseArray<Bitmap> bitmaps = new SparseArray<>();
        private static final ArrayList<Integer> loading = new ArrayList<>();

        int emoji; // HANDSHAKE = 0
        int skin; // YELLOW = -1, TONES = 0...4
        int place; // LEFT = 0, RIGHT = 1
        int hash;

        boolean placeholder;

        public DrawableInfo(int emoji, int skin, int place) {
            if (skin == -2) {
                skin = -1;
                placeholder = true;
            }
            this.hash = Objects.hash(this.emoji = emoji, this.skin = skin, this.place = place);
        }

        public DrawableInfo updateSkin(int newSkin) {
            if (skin == newSkin) {
                return this;
            }
            return new DrawableInfo(emoji, newSkin, place);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        public boolean isLoaded() {
            return bitmaps.indexOfKey(hash) >= 0;
        }

        public void load() {
            if (isLoaded() || loading.contains(hash)) {
                return;
            }
            loading.add(hash);
            Utilities.globalQueue.postRunnable(() -> {
                final Bitmap bitmap = Emoji.loadBitmap("emoji/compound/" + emoji + "_" + skin + "_" + place + ".png");
                if (bitmap != null) {
                    bitmaps.put(hash, bitmap);
                    AndroidUtilities.cancelRunOnUIThread(Emoji.invalidateUiRunnable);
                    AndroidUtilities.runOnUIThread(Emoji.invalidateUiRunnable);
                }
                loading.remove((Integer) hash);
            });
        }

        public Bitmap getBitmap() {
            return bitmaps.get(hash);
        }
    }

    private static Paint placeholderPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    public static void setPlaceholderColor(int color) {
        placeholderPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
    }

    public static class CompoundEmojiDrawable extends Emoji.EmojiDrawable {
        private DrawableInfo left, right;
        private static Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private static Rect rect = new Rect();

        private View parent;
        private AnimatedFloat leftUpdateT, rightUpdateT;
        private DrawableInfo newLeft, newRight;

        public CompoundEmojiDrawable(DrawableInfo left, DrawableInfo right) {
            this.left = left;
            this.right = right;
        }

        public Rect getDrawRect() {
            Rect original = getBounds();
            int cX = original.centerX(), cY = original.centerY();
            rect.left = cX - (fullSize ? Emoji.bigImgSize : Emoji.drawImgSize) / 2;
            rect.right = cX + (fullSize ? Emoji.bigImgSize : Emoji.drawImgSize) / 2;
            rect.top = cY - (fullSize ? Emoji.bigImgSize : Emoji.drawImgSize) / 2;
            rect.bottom = cY + (fullSize ? Emoji.bigImgSize : Emoji.drawImgSize) / 2;
            return rect;
        }

        @Override
        public void draw(Canvas canvas) {
            if (!isLoaded()) {
                preload();
                Emoji.placeholderPaint.setColor(placeholderColor);
                Rect bounds = getBounds();
                canvas.drawCircle(bounds.centerX(), bounds.centerY(), bounds.width() * .4f, Emoji.placeholderPaint);
                return;
            }

            Rect b;
            if (fullSize) {
                b = getDrawRect();
            } else {
                b = getBounds();
            }

            if (!canvas.quickReject(b.left, b.top, b.right, b.bottom, Canvas.EdgeType.AA)) {
                if (newLeft != null) {
                    if (leftUpdateT == null) {
                        leftUpdateT = new AnimatedFloat(0, this::invalidate, 0, 320, CubicBezierInterpolator.EASE_OUT);
                    }
                    float t = leftUpdateT.set(1);

                    drawDrawableInfo(canvas, newLeft, b, Math.min(1, 1.5f * t));
                    drawDrawableInfo(canvas, left, b, 1f - t);

                    if (t >= 1) {
                        left = newLeft;
                        newLeft = null;
                    }
                } else {
                    drawDrawableInfo(canvas, left, b, 1);
                }

                if (newRight != null) {
                    if (rightUpdateT == null) {
                        rightUpdateT = new AnimatedFloat(0, this::invalidate, 0, 320, CubicBezierInterpolator.EASE_OUT);
                    }
                    float t = rightUpdateT.set(1);

                    drawDrawableInfo(canvas, newRight, b, Math.min(1, 1.5f * t));
                    drawDrawableInfo(canvas, right, b, 1f - t);

                    if (t >= 1) {
                        right = newRight;
                        newRight = null;
                    }
                } else {
                    drawDrawableInfo(canvas, right, b, 1);
                }
            }
        }

        private void invalidate() {
            if (parent != null) {
                parent.invalidate();
            }
            invalidateSelf();
        }

        public void update(int skinTone1, int skinTone2) {
            if (left.skin != skinTone1) {
                if (newLeft != null) {
                    left = newLeft;
                }
                newLeft = left.updateSkin(skinTone1);
                if (leftUpdateT != null) {
                    leftUpdateT.set(0, true);
                }
            }
            if (right.skin != skinTone2) {
                if (newRight != null) {
                    right = newRight;
                }
                newRight = right.updateSkin(skinTone2);
                if (rightUpdateT != null) {
                    rightUpdateT.set(0, true);
                }
            }
            invalidate();
        }

        private void drawDrawableInfo(Canvas canvas, DrawableInfo info, Rect bounds, float alpha) {
            final Bitmap leftBitmap = info.getBitmap();
            if (leftBitmap != null) {
                Paint currentPaint = info.placeholder ? placeholderPaint : paint;
                int wasAlpha = 0xFF;
                if (alpha < 1) {
                    wasAlpha = currentPaint.getAlpha();
                    currentPaint.setAlpha((int) (wasAlpha * alpha));
                }
                canvas.drawBitmap(leftBitmap, null, bounds, currentPaint);
                if (alpha < 1) {
                    currentPaint.setAlpha(wasAlpha);
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
        public void setColorFilter(ColorFilter cf) {}

        @Override
        public boolean isLoaded() {
            return left.isLoaded() && right.isLoaded();
        }

        @Override
        public void preload() {
            if (!isLoaded()) {
                left.load();
                right.load();
            }
        }
    }
}
