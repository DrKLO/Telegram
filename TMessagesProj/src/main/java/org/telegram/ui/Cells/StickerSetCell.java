/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
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
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;

import java.util.ArrayList;

public class StickerSetCell extends FrameLayout {

    private final int option;

    private TextView textView;
    private TextView valueTextView;
    private BackupImageView imageView;
    private RadialProgressView progressView;
    private CheckBox2 checkBox;
    private boolean needDivider;
    private ImageView optionsButton;
    private ImageView reorderButton;
    private TLRPC.TL_messages_stickerSet stickersSet;
    private Rect rect = new Rect();

    public StickerSetCell(Context context, int option) {
        super(context);
        this.option = option;

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity(LayoutHelper.getAbsoluteGravityStart());
        addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START, 71, 9, 46, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setGravity(LayoutHelper.getAbsoluteGravityStart());
        addView(valueTextView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START, 71, 32, 46, 0));

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

                reorderButton = new ImageView(context);
                reorderButton.setAlpha(0f);
                reorderButton.setVisibility(GONE);
                reorderButton.setScaleType(ImageView.ScaleType.CENTER);
                reorderButton.setImageResource(R.drawable.list_reorder);
                reorderButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_stickers_menu), PorterDuff.Mode.MULTIPLY));
                addView(reorderButton, LayoutHelper.createFrameRelatively(58, 58, Gravity.END));

                checkBox = new CheckBox2(context, 21);
                checkBox.setColor(null, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
                checkBox.setDrawUnchecked(false);
                checkBox.setDrawBackgroundAsArc(3);
                addView(checkBox, LayoutHelper.createFrameRelatively(24, 24, Gravity.START, 34, 30, 0, 0));
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

    public void setNeedDivider(boolean needDivider) {
        this.needDivider = needDivider;
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
            TLObject object = FileLoader.getClosestPhotoSizeWithSize(set.set.thumbs, 90);
            if (object == null) {
                object = sticker;
            }
            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(set.set.thumbs, Theme.key_windowBackgroundGray, 1.0f);
            ImageLocation imageLocation;

            if (object instanceof TLRPC.Document) {
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90);
                imageLocation = ImageLocation.getForDocument(thumb, sticker);
            } else {
                TLRPC.PhotoSize thumb = (TLRPC.PhotoSize) object;
                imageLocation = ImageLocation.getForSticker(thumb, sticker, set.set.thumb_version);
            }

            if (object instanceof TLRPC.Document && MessageObject.isAnimatedStickerDocument(sticker, true)) {
                if (svgThumb != null) {
                    imageView.setImage(ImageLocation.getForDocument(sticker), "50_50", svgThumb, 0, set);
                } else {
                    imageView.setImage(ImageLocation.getForDocument(sticker), "50_50", imageLocation, null, 0, set);
                }
            } else if (imageLocation != null && imageLocation.imageType == FileLoader.IMAGE_TYPE_LOTTIE) {
                imageView.setImage(imageLocation, "50_50", "tgs", svgThumb, set);
            } else {
                imageView.setImage(imageLocation, "50_50", "webp", svgThumb, set);
            }
        } else {
            valueTextView.setText(LocaleController.formatPluralString("Stickers", 0));
            imageView.setImageDrawable(null);
        }
    }

    public void setChecked(boolean checked) {
        setChecked(checked, true);
    }

    public void setChecked(boolean checked, boolean animated) {
        switch (option) {
            case 1:
                checkBox.setChecked(checked, animated);
                break;
            case 3:
                optionsButton.setVisibility(checked ? VISIBLE : INVISIBLE);
                break;
        }
    }

    public void setReorderable(boolean reorderable) {
        setReorderable(reorderable, true);
    }

    public void setReorderable(boolean reorderable, boolean animated) {
        if (option == 1) {

            final float[] alphaValues = {reorderable ? 1f : 0f, reorderable ? 0f : 1f};
            final float[] scaleValues = {reorderable ? 1f : .66f, reorderable ? .66f : 1f};

            if (animated) {
                reorderButton.setVisibility(VISIBLE);
                reorderButton.animate()
                        .alpha(alphaValues[0])
                        .scaleX(scaleValues[0])
                        .scaleY(scaleValues[0])
                        .setDuration(200)
                        .setInterpolator(Easings.easeOutSine)
                        .withEndAction(() -> {
                            if (!reorderable) {
                                reorderButton.setVisibility(GONE);
                            }
                        }).start();

                optionsButton.setVisibility(VISIBLE);
                optionsButton.animate()
                        .alpha(alphaValues[1])
                        .scaleX(scaleValues[1])
                        .scaleY(scaleValues[1])
                        .setDuration(200)
                        .setInterpolator(Easings.easeOutSine)
                        .withEndAction(() -> {
                            if (reorderable) {
                                optionsButton.setVisibility(GONE);
                            }
                        }).start();
            } else {
                reorderButton.setVisibility(reorderable ? VISIBLE : GONE);
                reorderButton.setAlpha(alphaValues[0]);
                reorderButton.setScaleX(scaleValues[0]);
                reorderButton.setScaleY(scaleValues[0]);

                optionsButton.setVisibility(reorderable ? GONE : VISIBLE);
                optionsButton.setAlpha(alphaValues[1]);
                optionsButton.setScaleX(scaleValues[1]);
                optionsButton.setScaleY(scaleValues[1]);
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    public void setOnReorderButtonTouchListener(OnTouchListener listener) {
        reorderButton.setOnTouchListener(listener);
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
