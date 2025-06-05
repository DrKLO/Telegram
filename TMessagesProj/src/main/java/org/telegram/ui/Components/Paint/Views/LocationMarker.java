package org.telegram.ui.Components.Paint.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;

public class LocationMarker extends View {

    public static final int VARIANT_LOCATION = 0;
    public static final int VARIANT_WEATHER = 1;

    public final int variant;

    private int maxWidth;
    private String text = "";
    private boolean relayout;

    public final static float SCALE = 1.2f;

    private final RectF padding = new RectF(4, 4.33f, 7.66f, 3);
    private final float iconPadding = 3.25f;
    private final float flagIconPadding = 2.25f;
    private final float iconSize = 21.33f;

    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    public final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable icon;

    private boolean hasFlag;
    private final ImageReceiver flagImageReceiver = new ImageReceiver(this);
    private final ImageReceiver flagAnimatedImageReceiver = new ImageReceiver(this);
    private TLRPC.Document flagDocument;
    private TLRPC.Document flagAnimatedDocument;
    private boolean forceEmoji;

    public final float density;
    private float textScale = 1;
    private StaticLayout layout;
    private float layoutWidth, layoutLeft;

    public final int type;
    public final int padx, pady;

    public LocationMarker(Context context, int variant, float density, int type) {
        super(context);

        this.variant = variant;
        this.density = density;

        flagImageReceiver.setCrossfadeWithOldImage(true);
        flagImageReceiver.setInvalidateAll(true);
        flagAnimatedImageReceiver.setCrossfadeWithOldImage(true);
        flagAnimatedImageReceiver.setInvalidateAll(true);

        padx = (int) (3 * density);
        pady = (int) (1 * density);
        setPadding(padx, pady, padx, pady);

        this.type = type;
        icon = context.getResources().getDrawable(R.drawable.map_pin3).mutate();
        textPaint.setTextSize(24 * density);
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rcondensedbold.ttf"));

        NotificationCenter.listenEmojiLoading(this);
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
        if (drawable instanceof Emoji.SimpleEmojiDrawable) {
            ((Emoji.SimpleEmojiDrawable) drawable).fullSize = false;
        }
        if (drawable == null) {
            return null;
        }
        final Drawable emojiDrawable = drawable;
        return new Drawable() {
            @Override
            public void draw(@NonNull Canvas canvas) {
                canvas.save();
                if (emojiDrawable.getBounds() != null) {
                    canvas.scale(1f / SCALE, 1f / SCALE, emojiDrawable.getBounds().centerX(), emojiDrawable.getBounds().centerY());
                }
                emojiDrawable.draw(canvas);
                canvas.restore();
            }

            @Override
            public void setAlpha(int alpha) {
                emojiDrawable.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(@Nullable ColorFilter colorFilter) {
                emojiDrawable.setColorFilter(colorFilter);
            }

            @Override
            public void setBounds(@NonNull Rect bounds) {
                emojiDrawable.setBounds(bounds);
            }

            @Override
            public void setBounds(int left, int top, int right, int bottom) {
                emojiDrawable.setBounds(left, top, right, bottom);
            }

            @Override
            public int getOpacity() {
                return emojiDrawable.getOpacity();
            }
        };
    }

    public void setCodeEmoji(int currentAccount, String emoji) {
        if (TextUtils.isEmpty(emoji)) {
            hasFlag = false;
            flagDocument = null;
            flagAnimatedDocument = null;
            flagImageReceiver.clearImage();
            flagAnimatedImageReceiver.clearImage();
        } else {
            hasFlag = true;
            flagDocument = null;
            flagAnimatedDocument = null;

            TLRPC.TL_inputStickerSetShortName inputStickerSetShortName2 = new TLRPC.TL_inputStickerSetShortName();
            inputStickerSetShortName2.short_name = "StaticEmoji";
            MediaDataController.getInstance(currentAccount).getStickerSet(inputStickerSetShortName2, 0, false, set2 -> {
                flagDocument = findDocument(set2, emoji);
                flagImageReceiver.setImage(
                    ImageLocation.getForDocument(flagDocument), "80_80",
                    getEmojiThumb(emoji),
                    null, null, 0
                );
                flagAnimatedImageReceiver.setImage(
                    ImageLocation.getForDocument(flagAnimatedDocument), "80_80",
                    ImageLocation.getForDocument(flagDocument), "80_80",
                    null, null,
                    getEmojiThumb(emoji),
                    0, null, null, 0
                );
            });

            TLRPC.TL_inputStickerSetShortName inputStickerSetShortName = new TLRPC.TL_inputStickerSetShortName();
            inputStickerSetShortName.short_name = "RestrictedEmoji";
            TLRPC.TL_messages_stickerSet instantSet = MediaDataController.getInstance(currentAccount).getStickerSet(inputStickerSetShortName, 0, false, set -> {
                flagAnimatedDocument = findDocument(set, emoji);
                if (flagAnimatedDocument == null) {
                    return;
                }
                flagAnimatedImageReceiver.setImage(
                    ImageLocation.getForDocument(flagAnimatedDocument), "80_80",
                    ImageLocation.getForDocument(flagDocument), "80_80",
                    null, null,
                    getEmojiThumb(emoji),
                    0, null, null, 0
                );
            });

            flagImageReceiver.setImage(
                ImageLocation.getForDocument(flagDocument), "80_80",
                getEmojiThumb(emoji),
                null, null, 0
            );

            flagAnimatedImageReceiver.setImage(
                ImageLocation.getForDocument(flagAnimatedDocument), "80_80",
                ImageLocation.getForDocument(flagDocument), "80_80",
                null, null,
                getEmojiThumb(emoji),
                0, null, null, 0
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

            if (containsEmoji(pack.emoticon, emoji) && !pack.documents.isEmpty()) {
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

    private boolean containsEmoji(String emojiString, String emoji) {
        if (emojiString == null || emoji == null) return false;
        ArrayList<Emoji.EmojiSpanRange> emojis = Emoji.parseEmojis(emojiString);
        for (int i = 0; i < emojis.size(); ++i) {
            if (TextUtils.equals(emojis.get(i).code, emoji)) {
                return true;
            }
        }
        return false;
    }

    public TLRPC.Document getCodeEmojiDocument() {
        return isVideo && flagAnimatedDocument != null ? flagAnimatedDocument : flagDocument;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachInternal();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        detachInternal();
    }

    private boolean attachedToWindow;
    public void attachInternal() {
        attachedToWindow = true;
        if (isVideo) {
            flagAnimatedImageReceiver.onAttachedToWindow();
        } else {
            flagImageReceiver.onAttachedToWindow();
        }
    }

    public void detachInternal() {
        attachedToWindow = false;
        flagImageReceiver.onDetachedFromWindow();
        flagAnimatedImageReceiver.onDetachedFromWindow();
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
        if (type == 0) {
            outlinePaint.setColor(0xFF000000);
            textPaint.setColor(0xFFFFFFFF);
            icon.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        } else if (type == 1) {
            outlinePaint.setColor(0x4C000000);
            textPaint.setColor(0xFFFFFFFF);
            icon.setColorFilter(null);
        } else if (type == 2) {
            outlinePaint.setColor(0xFFFFFFFF);
            textPaint.setColor(0xFF000000);
            icon.setColorFilter(null);
        } else {
            outlinePaint.setColor(color);
            final int textColor = AndroidUtilities.computePerceivedBrightness(color) >= .721f ? Color.BLACK : Color.WHITE;
            textPaint.setColor(textColor);
            icon.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
        }
        invalidate();
    }

    private boolean isVideo;
    public void setIsVideo(boolean isVideo) {
        if (this.isVideo != isVideo && attachedToWindow) {
            if (isVideo) {
                flagImageReceiver.onDetachedFromWindow();
                flagAnimatedImageReceiver.onAttachedToWindow();
            } else {
                flagImageReceiver.onAttachedToWindow();
                flagAnimatedImageReceiver.onDetachedFromWindow();
            }
        }
        this.isVideo = isVideo;
        invalidate();
    }

    public int getTypesCount() {
        return 4;
    }

    private float w, h;

    public void setupLayout() {
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
        setMeasuredDimension(getWidthInternal(), getHeightInternal());
    }

    public int getWidthInternal() {
        return padx + (int) Math.round(w) + padx;
    }

    public int getHeightInternal() {
        return pady + (int) Math.round(h) + pady;
    }

    private final RectF bounds = new RectF();
    private final Path path = new Path();

    public float getRadius() {
        return .2f * h;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        drawInternal(canvas);
    }

    private AnimatedFloat animatedVideo = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    
    public void drawInternal(Canvas canvas) {
        setupLayout();
        if (layout == null) {
            return;
        }

        bounds.set(padx, pady, padx + w, pady + h);
        canvas.drawRoundRect(bounds, .2f * h, .2f * h, outlinePaint);

        if (hasFlag) {
            float video = animatedVideo.set(isVideo);
            
            if (video > 0) {
                flagAnimatedImageReceiver.setImageCoords(
                        padx + (padding.left + flagIconPadding) * density, pady + (h - iconSize * density) / 2,
                        iconSize * density, iconSize * density
                );
                canvas.save();
                canvas.scale(SCALE, SCALE, flagAnimatedImageReceiver.getCenterX(), flagAnimatedImageReceiver.getCenterY());
                flagAnimatedImageReceiver.setAlpha(video);
                flagAnimatedImageReceiver.draw(canvas);
                canvas.restore();
            }
            if (video < 1) {
                flagImageReceiver.setImageCoords(
                        padx + (padding.left + flagIconPadding) * density, pady + (h - iconSize * density) / 2,
                        iconSize * density, iconSize * density
                );
                canvas.save();
                canvas.scale(SCALE, SCALE, flagImageReceiver.getCenterX(), flagImageReceiver.getCenterY());
                flagImageReceiver.setAlpha(1f - video);
                flagImageReceiver.draw(canvas);
                canvas.restore();
            }
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
