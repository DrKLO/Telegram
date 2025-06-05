/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.Checkable;
import android.widget.FrameLayout;
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
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ProgressButton;
import org.telegram.ui.Components.ViewHelper;

import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class ArchivedStickerSetCell extends FrameLayout implements Checkable {

    private final boolean checkable;

    private final TextView textView;
    private final TextView valueTextView;
    private final BackupImageView imageView;
    private final Button deleteButton;
    private final ProgressButton addButton;

    private boolean needDivider;
    private Button currentButton;
    private AnimatorSet animatorSet;
    private TLRPC.StickerSetCovered stickersSet;
    private OnCheckedChangeListener onCheckedChangeListener;
    private boolean checked;

    public ArchivedStickerSetCell(Context context, boolean checkable) {
        super(context);

        if (this.checkable = checkable) {
            currentButton = addButton = new ProgressButton(context);
            addButton.setText(LocaleController.getString(R.string.Add));
            addButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            addButton.setProgressColor(Theme.getColor(Theme.key_featuredStickers_buttonProgress));
            addButton.setBackgroundRoundRect(Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed));
            addView(addButton, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.END, 0, 18, 14, 0));

            final int minWidth = AndroidUtilities.dp(60);
            deleteButton = new ProgressButton(context);
            deleteButton.setAllCaps(false);
            deleteButton.setMinWidth(minWidth);
            deleteButton.setMinimumWidth(minWidth);
            deleteButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            deleteButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_removeButtonText));
            deleteButton.setText(LocaleController.getString(R.string.StickersRemove));
            deleteButton.setBackground(Theme.getRoundRectSelectorDrawable(Theme.getColor(Theme.key_featuredStickers_removeButtonText)));
            deleteButton.setTypeface(AndroidUtilities.bold());
            ViewHelper.setPadding(deleteButton, 8, 0, 8, 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                deleteButton.setOutlineProvider(null);
            }
            addView(deleteButton, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.END, 0, 18, 14, 0));

            final OnClickListener toggleListener = v -> toggle();
            addButton.setOnClickListener(toggleListener);
            deleteButton.setOnClickListener(toggleListener);

            syncButtons(false);
        } else {
            addButton = null;
            deleteButton = null;
        }

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity(LayoutHelper.getAbsoluteGravityStart());
        addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START, 71, 10, 21, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setGravity(LayoutHelper.getAbsoluteGravityStart());
        addView(valueTextView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START, 71, 35, 21, 0));

        imageView = new BackupImageView(context);
        imageView.setAspectFit(true);
        imageView.setLayerNum(1);
        addView(imageView, LayoutHelper.createFrameRelatively(48, 48, Gravity.START | Gravity.TOP, 12, 8, 0, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
        if (checkable && child == textView) {
            widthUsed += Math.max(addButton.getMeasuredWidth(), deleteButton.getMeasuredWidth());
        }
        super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(0, getHeight() - 1, getWidth() - getPaddingRight(), getHeight() - 1, Theme.dividerPaint);
        }
    }

    public void setDrawProgress(boolean drawProgress, boolean animated) {
        if (addButton != null) {
            addButton.setDrawProgress(drawProgress, animated);
        }
    }

    public void setStickersSet(TLRPC.StickerSetCovered set, boolean divider) {
        needDivider = divider;
        stickersSet = set;
        setWillNotDraw(!needDivider);

        textView.setText(stickersSet.set.title);
        if (set.set.emojis) {
            valueTextView.setText(LocaleController.formatPluralString("EmojiCount", set.set.count));
        } else {
            valueTextView.setText(LocaleController.formatPluralString("Stickers", set.set.count));
        }

        TLRPC.Document sticker = null;
        if (set instanceof TLRPC.TL_stickerSetFullCovered) {
            ArrayList<TLRPC.Document> documents = ((TLRPC.TL_stickerSetFullCovered) set).documents;
            if (documents == null) {
                return;
            }
            long thumb_document_id = set.set.thumb_document_id;
            for (int i = 0; i < documents.size(); ++i) {
                TLRPC.Document d = documents.get(i);
                if (d != null && d.id == thumb_document_id) {
                    sticker = d;
                    break;
                }
            }
            if (sticker == null && !documents.isEmpty()) {
                sticker = documents.get(0);
            }
        } else if (set.cover != null) {
            sticker = set.cover;
        } else if (!set.covers.isEmpty()) {
            sticker = set.covers.get(0);
        }
        if (sticker != null) {
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

            if (object instanceof TLRPC.Document && (MessageObject.isAnimatedStickerDocument(sticker, true) || MessageObject.isVideoSticker(sticker))) {
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
            imageView.setImage(null, null, "webp", null, set);
        }
    }

    public TLRPC.StickerSetCovered getStickersSet() {
        return stickersSet;
    }

    private void syncButtons(boolean animated) {
        if (checkable) {
            if (animatorSet != null) {
                animatorSet.cancel();
            }
            final float deleteButtonValue = checked ? 1f : 0f;
            final float addButtonValue = checked ? 0f : 1f;
            if (animated) {
                currentButton = checked ? deleteButton : addButton;
                addButton.setVisibility(VISIBLE);
                deleteButton.setVisibility(VISIBLE);
                animatorSet = new AnimatorSet();
                animatorSet.setDuration(250);
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(deleteButton, View.ALPHA, deleteButtonValue),
                        ObjectAnimator.ofFloat(deleteButton, View.SCALE_X, deleteButtonValue),
                        ObjectAnimator.ofFloat(deleteButton, View.SCALE_Y, deleteButtonValue),
                        ObjectAnimator.ofFloat(addButton, View.ALPHA, addButtonValue),
                        ObjectAnimator.ofFloat(addButton, View.SCALE_X, addButtonValue),
                        ObjectAnimator.ofFloat(addButton, View.SCALE_Y, addButtonValue));
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (currentButton == addButton) {
                            deleteButton.setVisibility(INVISIBLE);
                        } else {
                            addButton.setVisibility(INVISIBLE);
                        }
                    }
                });
                animatorSet.setInterpolator(new OvershootInterpolator(1.02f));
                animatorSet.start();
            } else {
                deleteButton.setVisibility(checked ? VISIBLE : INVISIBLE);
                deleteButton.setAlpha(deleteButtonValue);
                deleteButton.setScaleX(deleteButtonValue);
                deleteButton.setScaleY(deleteButtonValue);
                addButton.setVisibility(checked ? INVISIBLE : VISIBLE);
                addButton.setAlpha(addButtonValue);
                addButton.setScaleX(addButtonValue);
                addButton.setScaleY(addButtonValue);
            }
        }
    }

    //region Checkable
    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        onCheckedChangeListener = listener;
    }

    @Override
    public void setChecked(boolean checked) {
        setChecked(checked, true);
    }

    public void setChecked(boolean checked, boolean animated) {
        setChecked(checked, animated, true);
    }

    public void setChecked(boolean checked, boolean animated, boolean notify) {
        if (checkable && this.checked != checked) {
            this.checked = checked;

            syncButtons(animated);

            if (notify && onCheckedChangeListener != null) {
                onCheckedChangeListener.onCheckedChanged(this, checked);
            }
        }
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public void toggle() {
        if (checkable) {
            setChecked(!isChecked());
        }
    }

    public interface OnCheckedChangeListener {
        void onCheckedChanged(ArchivedStickerSetCell cell, boolean isChecked);
    }
    //endregion
}
