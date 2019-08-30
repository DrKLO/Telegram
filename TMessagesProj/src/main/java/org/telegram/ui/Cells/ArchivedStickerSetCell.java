/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

public class ArchivedStickerSetCell extends FrameLayout {

    private TextView textView;
    private TextView valueTextView;
    private BackupImageView imageView;
    private boolean needDivider;
    private Switch checkBox;
    private TLRPC.StickerSetCovered stickersSet;
    private Rect rect = new Rect();
    private Switch.OnCheckedChangeListener onCheckedChangeListener;

    public ArchivedStickerSetCell(Context context, boolean needCheckBox) {
        super(context);

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 71, 10, needCheckBox ? 71 : 21, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 71, 35, needCheckBox ? 71 : 21, 0));

        imageView = new BackupImageView(context);
        imageView.setAspectFit(true);
        imageView.setLayerNum(1);
        addView(imageView, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 12, 8, LocaleController.isRTL ? 12 : 0, 0));

        if (needCheckBox) {
            checkBox = new Switch(context);
            checkBox.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
            addView(checkBox, LayoutHelper.createFrame(37, 40, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 16, 0, 16, 0));
        }
    }

    public TextView getTextView() {
        return textView;
    }

    public TextView getValueTextView() {
        return valueTextView;
    }

    public Switch getCheckBox() {
        return checkBox;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public void setStickersSet(TLRPC.StickerSetCovered set, boolean divider) {
        needDivider = divider;
        stickersSet = set;
        setWillNotDraw(!needDivider);

        textView.setText(stickersSet.set.title);

        valueTextView.setText(LocaleController.formatPluralString("Stickers", set.set.count));

        TLRPC.Document sticker;
        if (set.cover != null) {
            sticker = set.cover;
        } else if (!set.covers.isEmpty()) {
            sticker = set.covers.get(0);
        } else {
            sticker = null;
        }
        if (sticker != null) {
            TLObject object;
            if (set.set.thumb instanceof TLRPC.TL_photoSize) {
                object = set.set.thumb;
            } else {
                object = sticker;
            }
            ImageLocation imageLocation;

            if (object instanceof TLRPC.Document) {
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90);
                imageLocation = ImageLocation.getForDocument(thumb, sticker);
            } else {
                TLRPC.PhotoSize thumb = (TLRPC.PhotoSize) object;
                imageLocation = ImageLocation.getForSticker(thumb, sticker);
            }

            if (object instanceof TLRPC.Document && MessageObject.isAnimatedStickerDocument(sticker)) {
                imageView.setImage(ImageLocation.getForDocument(sticker), "50_50", imageLocation, null, 0, set);
            } else if (imageLocation != null && imageLocation.lottieAnimation) {
                imageView.setImage(imageLocation, "50_50", "tgs", null, set);
            } else {
                imageView.setImage(imageLocation, "50_50", "webp", null, set);
            }
        } else {
            imageView.setImage(null, null, "webp", null, set);
        }
    }

    public void setOnCheckClick(Switch.OnCheckedChangeListener listener) {
        checkBox.setOnCheckedChangeListener(onCheckedChangeListener = listener);
        checkBox.setOnClickListener(v -> checkBox.setChecked(!checkBox.isChecked(), true));
    }

    public void setChecked(boolean checked) {
        checkBox.setOnCheckedChangeListener(null);
        checkBox.setChecked(checked, true);
        checkBox.setOnCheckedChangeListener(onCheckedChangeListener);
    }

    public boolean isChecked() {
        return checkBox != null && checkBox.isChecked();
    }

    public TLRPC.StickerSetCovered getStickersSet() {
        return stickersSet;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (checkBox != null) {
            checkBox.getHitRect(rect);
            if (rect.contains((int) event.getX(), (int) event.getY())) {
                event.offsetLocation(-checkBox.getX(), -checkBox.getY());
                return checkBox.onTouchEvent(event);
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(0, getHeight() - 1, getWidth() - getPaddingRight(), getHeight() - 1, Theme.dividerPaint);
        }
    }
}
