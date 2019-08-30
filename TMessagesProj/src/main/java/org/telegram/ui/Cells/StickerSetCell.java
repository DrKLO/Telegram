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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;

import java.util.ArrayList;

public class StickerSetCell extends FrameLayout {

    private TextView textView;
    private TextView valueTextView;
    private BackupImageView imageView;
    private RadialProgressView progressView;
    private boolean needDivider;
    private ImageView optionsButton;
    private TLRPC.TL_messages_stickerSet stickersSet;
    private Rect rect = new Rect();

    public StickerSetCell(Context context, int option) {
        super(context);

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, LocaleController.isRTL ? 40 : 71, 9, LocaleController.isRTL ? 71 : 40, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, LocaleController.isRTL ? 40 : 71, 32, LocaleController.isRTL ? 71 : 40, 0));

        imageView = new BackupImageView(context);
        imageView.setAspectFit(true);
        imageView.setLayerNum(1);
        addView(imageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 13, 9, LocaleController.isRTL ? 13 : 0, 0));

        if (option == 2) {
            progressView = new RadialProgressView(getContext());
            progressView.setProgressColor(Theme.getColor(Theme.key_dialogProgressCircle));
            progressView.setSize(AndroidUtilities.dp(30));
            addView(progressView, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 12, 5, LocaleController.isRTL ? 12 : 0, 0));
        } else if (option != 0) {
            optionsButton = new ImageView(context);
            optionsButton.setFocusable(false);
            optionsButton.setScaleType(ImageView.ScaleType.CENTER);
            optionsButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_stickers_menuSelector)));
            if (option == 1) {
                optionsButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_stickers_menu), PorterDuff.Mode.MULTIPLY));
                optionsButton.setImageResource(R.drawable.msg_actions);
                addView(optionsButton, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL));
            } else if (option == 3) {
                optionsButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addedIcon), PorterDuff.Mode.MULTIPLY));
                optionsButton.setImageResource(R.drawable.sticker_added);
                addView(optionsButton, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, (LocaleController.isRTL ? 10 : 0), 9, (LocaleController.isRTL ? 0 : 10), 0));
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(58) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public void setText(String title, String subtitle, int icon, boolean divider) {
        needDivider = divider;
        stickersSet = null;
        textView.setText(title);
        valueTextView.setText(subtitle);
        if (TextUtils.isEmpty(subtitle)) {
            textView.setTranslationY(AndroidUtilities.dp(10));
        } else {
            textView.setTranslationY(0);
        }
        if (icon != 0) {
            imageView.setImageResource(icon, Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon));
            imageView.setVisibility(VISIBLE);
            if (progressView != null) {
                progressView.setVisibility(INVISIBLE);
            }
        } else {
            imageView.setVisibility(INVISIBLE);
            if (progressView != null) {
                progressView.setVisibility(VISIBLE);
            }
        }
    }

    public void setStickersSet(TLRPC.TL_messages_stickerSet set, boolean divider) {
        needDivider = divider;
        stickersSet = set;

        imageView.setVisibility(VISIBLE);
        if (progressView != null) {
            progressView.setVisibility(INVISIBLE);
        }

        textView.setTranslationY(0);
        textView.setText(stickersSet.set.title);
        if (stickersSet.set.archived) {
            textView.setAlpha(0.5f);
            valueTextView.setAlpha(0.5f);
            imageView.setAlpha(0.5f);
        } else {
            textView.setAlpha(1.0f);
            valueTextView.setAlpha(1.0f);
            imageView.setAlpha(1.0f);
        }

        ArrayList<TLRPC.Document> documents = set.documents;
        if (documents != null && !documents.isEmpty()) {
            valueTextView.setText(LocaleController.formatPluralString("Stickers", documents.size()));

            TLRPC.Document sticker = documents.get(0);
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
            valueTextView.setText(LocaleController.formatPluralString("Stickers", 0));
        }
    }

    public void setChecked(boolean checked) {
        if (optionsButton == null) {
            return;
        }
        optionsButton.setVisibility(checked ? VISIBLE : INVISIBLE);
    }

    public void setOnOptionsClick(OnClickListener listener) {
        if (optionsButton == null) {
            return;
        }
        optionsButton.setOnClickListener(listener);
    }

    public TLRPC.TL_messages_stickerSet getStickersSet() {
        return stickersSet;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (Build.VERSION.SDK_INT >= 21 && getBackground() != null && optionsButton != null) {
            optionsButton.getHitRect(rect);
            if (rect.contains((int) event.getX(), (int) event.getY())) {
                return true;
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
