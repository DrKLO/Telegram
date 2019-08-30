/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class StickerCell extends FrameLayout {

    private BackupImageView imageView;
    private TLRPC.Document sticker;
    private Object parentObject;
    private long lastUpdateTime;
    private boolean scaled;
    private float scale;
    private long time = 0;
    private boolean clearsInputField;
    private static AccelerateInterpolator interpolator = new AccelerateInterpolator(0.5f);

    public StickerCell(Context context) {
        super(context);

        imageView = new BackupImageView(context);
        imageView.setAspectFit(true);
        imageView.setLayerNum(1);
        addView(imageView, LayoutHelper.createFrame(66, 66, Gravity.CENTER_HORIZONTAL, 0, 5, 0, 0));
        setFocusable(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(76) + getPaddingLeft() + getPaddingRight(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(78), MeasureSpec.EXACTLY));
    }

    @Override
    public void setPressed(boolean pressed) {
        if (imageView.getImageReceiver().getPressed() != pressed) {
            imageView.getImageReceiver().setPressed(pressed ? 1 : 0);
            imageView.invalidate();
        }
        super.setPressed(pressed);
    }

    public void setClearsInputField(boolean value) {
        clearsInputField = value;
    }

    public boolean isClearsInputField() {
        return clearsInputField;
    }

    public void setSticker(TLRPC.Document document, Object parent, int side) {
        parentObject = parent;
        if (document != null) {
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
            if (MessageObject.canAutoplayAnimatedSticker(document)) {
                if (thumb != null) {
                    imageView.setImage(ImageLocation.getForDocument(document), "80_80", ImageLocation.getForDocument(thumb, document), null, 0, parentObject);
                } else {
                    imageView.setImage(ImageLocation.getForDocument(document), "80_80", null, null, parentObject);
                }
            } else {
                imageView.setImage(ImageLocation.getForDocument(thumb, document), null, "webp", null, parentObject);
            }
        }
        sticker = document;
        if (side == -1) {
            setBackgroundResource(R.drawable.stickers_back_left);
            setPadding(AndroidUtilities.dp(7), 0, 0, 0);
        } else if (side == 0) {
            setBackgroundResource(R.drawable.stickers_back_center);
            setPadding(0, 0, 0, 0);
        } else if (side == 1) {
            setBackgroundResource(R.drawable.stickers_back_right);
            setPadding(0, 0, AndroidUtilities.dp(7), 0);
        } else if (side == 2) {
            setBackgroundResource(R.drawable.stickers_back_all);
            setPadding(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(3), 0);
        }
        Drawable background = getBackground();
        if (background != null) {
            background.setAlpha(230);
            background.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_stickersHintPanel), PorterDuff.Mode.MULTIPLY));
        }
    }

    public TLRPC.Document getSticker() {
        return sticker;
    }

    public Object getParentObject() {
        return parentObject;
    }

    public void setScaled(boolean value) {
        scaled = value;
        lastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    public boolean showingBitmap() {
        return imageView.getImageReceiver().getBitmap() != null;
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (child == imageView && (scaled && scale != 0.8f || !scaled && scale != 1.0f)) {
            long newTime = System.currentTimeMillis();
            long dt = (newTime - lastUpdateTime);
            lastUpdateTime = newTime;
            if (scaled && scale != 0.8f) {
                scale -= dt / 400.0f;
                if (scale < 0.8f) {
                    scale = 0.8f;
                }
            } else {
                scale += dt / 400.0f;
                if (scale > 1.0f) {
                    scale = 1.0f;
                }
            }
            imageView.setScaleX(scale);
            imageView.setScaleY(scale);
            imageView.invalidate();
            invalidate();
        }
        return result;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);
        if (sticker == null)
            return;
        String emoji = null;
        for (int a = 0; a < sticker.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = sticker.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                emoji = attribute.alt != null && attribute.alt.length() > 0 ? attribute.alt : null;
            }
        }
        if (emoji != null)
            info.setText(emoji + " " + LocaleController.getString("AttachSticker", R.string.AttachSticker));
        else
            info.setText(LocaleController.getString("AttachSticker", R.string.AttachSticker));
        info.setEnabled(true);
    }
}
