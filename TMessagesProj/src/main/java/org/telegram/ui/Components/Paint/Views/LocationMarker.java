package org.telegram.ui.Components.Paint.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Paint.PaintTypeface;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.concurrent.CountDownLatch;

public class LocationMarker extends View {

    private int maxWidth;
    private String text = "";
    private boolean relayout;

    public final static float SCALE = 1.2f;

    private final RectF padding = new RectF(4, 4.33f, 7.66f, 3);
    private final float iconPadding = 3.25f;
    private final float flagIconPadding = 2.25f;
    private final float iconSize = 21.33f;

    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable icon;

    private boolean hasFlag;
    private final ImageReceiver flagImageReceiver = new ImageReceiver(this);
    private TLRPC.Document flagDocument;
    private boolean forceEmoji;

    public final float density;
    private float textScale = 1;
    private StaticLayout layout;
    private float layoutWidth, layoutLeft;

    public final int type;
    public final int padx, pady;

    public LocationMarker(Context context, float density, int type) {
        super(context);
        this.density = density;

        flagImageReceiver.setCrossfadeWithOldImage(true);

        padx = (int) (3 * density);
        pady = (int) (1 * density);

        this.type = type;
        icon = context.getResources().getDrawable(R.drawable.map_pin3).mutate();
        textPaint.setTextSize(24 * density);
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rcondensedbold.ttf"));
    }

    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
        this.relayout = true;
    }

    public void forceEmoji() {
        forceEmoji = true;
        this.relayout = true;
        requestLayout();
    }

    private Drawable getEmojiThumb(String emoji) {
        Drawable drawable = Emoji.getEmojiBigDrawable(emoji);
        if (drawable == null) {
            return null;
        }
        return new Drawable() {
            @Override
            public void draw(@NonNull Canvas canvas) {
                canvas.save();
                if (drawable.getBounds() != null) {
                    canvas.scale(1f / SCALE, 1f / SCALE, drawable.getBounds().centerX(), drawable.getBounds().centerY());
                }
                drawable.draw(canvas);
                canvas.restore();
            }

            @Override
            public void setAlpha(int alpha) {
                drawable.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(@Nullable ColorFilter colorFilter) {
                drawable.setColorFilter(colorFilter);
            }

            @Override
            public void setBounds(@NonNull Rect bounds) {
                drawable.setBounds(bounds);
            }

            @Override
            public void setBounds(int left, int top, int right, int bottom) {
                drawable.setBounds(left, top, right, bottom);
            }

            @Override
            public int getOpacity() {
                return drawable.getOpacity();
            }
        };
    }

    public void setCountryCodeEmoji(int currentAccount, String emoji) {
        if (TextUtils.isEmpty(emoji)) {
            hasFlag = false;
            flagImageReceiver.clearImage();
        } else {
            hasFlag = true;
            flagDocument = null;
//            TLRPC.TL_inputStickerSetShortName inputStickerSetShortName = new TLRPC.TL_inputStickerSetShortName();
//            inputStickerSetShortName.short_name = "RestrictedEmoji";
//            TLRPC.TL_messages_stickerSet instantSet = MediaDataController.getInstance(currentAccount).getStickerSet(inputStickerSetShortName, 0, false, set -> {
//                flagDocument = findDocument(set, emoji);
//                if (flagDocument == null) {
                    TLRPC.TL_inputStickerSetShortName inputStickerSetShortName2 = new TLRPC.TL_inputStickerSetShortName();
                    inputStickerSetShortName2.short_name = "StaticEmoji";
                    MediaDataController.getInstance(currentAccount).getStickerSet(inputStickerSetShortName2, 0, false, set2 -> {
                        flagDocument = findDocument(set2, emoji);
                        flagImageReceiver.setImage(
                            ImageLocation.getForDocument(flagDocument), "80_80",
                            getEmojiThumb(emoji),
                            null, null, 0
                        );
                    });
//                    return;
//                }
//                flagImageReceiver.setImage(
//                    ImageLocation.getForDocument(flagDocument), "80_80",
//                    getEmojiThumb(emoji),
//                    null, null, 0
//                );
//            });
//            flagDocument = findDocument(instantSet, emoji);
            flagImageReceiver.setImage(
                ImageLocation.getForDocument(flagDocument), "80_80",
                getEmojiThumb(emoji),
                null, null, 0
            );
        }
        this.relayout = true;
        requestLayout();
    }

    private TLRPC.Document findDocument(TLRPC.TL_messages_stickerSet set, String emoji) {
        if (set == null || set.packs == null || set.documents == null) {
            return null;
        }
        for (int i = 0; i < set.packs.size(); ++i) {
            TLRPC.TL_stickerPack pack = set.packs.get(i);
            if (pack.emoticon.contains(emoji) && !pack.documents.isEmpty()) {
                long documentId = pack.documents.get(0);
                for (int j = 0; j < set.documents.size(); ++j) {
                    if (set.documents.get(j).id == documentId) {
                        return set.documents.get(j);
                    }
                }
            }
        }
        return null;
    }

    public TLRPC.Document getCountryCodeEmojiDocument() {
        return flagDocument;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        flagImageReceiver.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        flagImageReceiver.onDetachedFromWindow();
    }

    public void setText(String text) {
        this.text = text;
        this.relayout = true;
        requestLayout();
    }

    public String getText() {
        return text;
    }

    public void setType(int type, int color) {
        if (this.type == 1) return;
        if (type == 0) {
            outlinePaint.setColor(color);
            final int textColor = AndroidUtilities.computePerceivedBrightness(color) >= .721f ? Color.BLACK : Color.WHITE;
            textPaint.setColor(textColor);
            icon.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
        } else if (type == 1) {
            outlinePaint.setColor(0xFF000000);
            textPaint.setColor(0xFFFFFFFF);
            icon.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        } else if (type == 2) {
            outlinePaint.setColor(0x4C000000);
            textPaint.setColor(0xFFFFFFFF);
            icon.setColorFilter(null);
        } else {
            outlinePaint.setColor(0xFFFFFFFF);
            textPaint.setColor(0xFF000000);
            icon.setColorFilter(null);
        }
        invalidate();
    }

    private float w, h;

    private void setupLayout() {
        if (!relayout) {
            return;
        }

        float textWidth = textPaint.measureText(text);
        float maxWidth = this.maxWidth - padx - padx - (padding.left + (hasFlag || forceEmoji ? flagIconPadding : 0) + iconSize + iconPadding + padding.right) * density;
        textScale = Math.min(1, maxWidth / textWidth);
        if (textScale < .4f) {
            layout = new StaticLayout(text, textPaint, HintView2.cutInFancyHalf(text, textPaint), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        } else {
            layout = new StaticLayout(text, textPaint, (int) Math.ceil(textWidth), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        }

        layoutWidth = 0;
        layoutLeft = Float.MAX_VALUE;
        for (int i = 0; i < layout.getLineCount(); ++i) {
            layoutWidth = Math.max(layoutWidth, layout.getLineWidth(i));
            layoutLeft = Math.min(layoutLeft, layout.getLineLeft(i));
        }
        if (layout.getLineCount() > 2) {
            textScale = .3f;
        } else {
            textScale = Math.min(1, maxWidth / layoutWidth);
        }

        w = (padding.left + (hasFlag || forceEmoji ? flagIconPadding : 0) + iconSize + iconPadding + padding.right) * density + layoutWidth * textScale;
        h = (padding.top + padding.bottom) * density + Math.max(iconSize * density, layout.getHeight() * textScale);
        
        relayout = false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setupLayout();
        setMeasuredDimension(padx + (int) Math.round(w) + padx, pady + (int) Math.round(h) + pady);
    }

    private final RectF bounds = new RectF();
    private final Path path = new Path();

    public float getRadius() {
        return .2f * h;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        setupLayout();
        if (layout == null) {
            return;
        }

        bounds.set(padx, pady, padx + w, pady + h);
        canvas.drawRoundRect(bounds, .2f * h, .2f * h, outlinePaint);

        if (hasFlag) {
            flagImageReceiver.setImageCoords(
                padx + (padding.left + flagIconPadding) * density, pady + (h - iconSize * density) / 2,
                iconSize * density, iconSize * density
            );
            canvas.save();
            canvas.scale(SCALE, SCALE, flagImageReceiver.getCenterX(), flagImageReceiver.getCenterY());
            flagImageReceiver.draw(canvas);
            canvas.restore();
        } else if (forceEmoji) {

        } else {
            icon.setBounds(
                padx + (int) (padding.left * density),
                pady + (int) ((h - iconSize * density) / 2),
                padx + (int) ((padding.left + iconSize) * density),
                pady + (int) ((h + iconSize * density) / 2)
            );
            icon.draw(canvas);
        }

        canvas.save();
        canvas.translate(padx + (padding.left + (hasFlag || forceEmoji ? flagIconPadding : 0) + iconSize + iconPadding) * density, pady + h / 2f);
        canvas.scale(textScale, textScale);
        canvas.translate(-layoutLeft, -layout.getHeight() / 2f);
        layout.draw(canvas);
        canvas.restore();
    }

    public void getEmojiBounds(RectF b) {
        b.set(
            padx + (padding.left + flagIconPadding) * density, pady + (h - iconSize * density) / 2,
            padx + (padding.left + flagIconPadding + iconSize) * density, pady + (h + iconSize * density) / 2
        );
    }
}
