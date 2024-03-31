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
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumButtonView;
import org.telegram.ui.Components.ProgressButton;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

public class FeaturedStickerSetCell2 extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount = UserConfig.selectedAccount;

    private final TextView textView;
    private final TextView valueTextView;
    private final BackupImageView imageView;
    private final ProgressButton addButton;
    private final TextView delButton;
    private final PremiumButtonView unlockButton;

    private AnimatorSet currentAnimation;
    private TLRPC.StickerSetCovered stickersSet;
    private boolean isInstalled;
    private boolean isLocked;
    private boolean needDivider;
    private final Theme.ResourcesProvider resourcesProvider;

    private boolean bindedObserver;
    private Long waitingForStickerSetId;
    private boolean unread;
    private boolean forceInstalled;

    public FeaturedStickerSetCell2(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, LocaleController.isRTL ? 22 : 71, 10, LocaleController.isRTL ? 71 : 22, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setEllipsize(TextUtils.TruncateAt.END);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, LocaleController.isRTL ? 100 : 71, 35, LocaleController.isRTL ? 71 : 100, 0));

        imageView = new BackupImageView(context);
        imageView.setAspectFit(true);
        imageView.setLayerNum(1);
        addView(imageView, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 12, 8, LocaleController.isRTL ? 12 : 0, 0));

        addButton = new ProgressButton(context);
        addButton.setText(LocaleController.getString("Add", R.string.Add));
        addButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        addView(addButton, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.END, 0, 18, 14, 0));

        delButton = new TextView(context);
        delButton.setGravity(Gravity.CENTER);
        delButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_removeButtonText));
        delButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        delButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        delButton.setText(LocaleController.getString("StickersRemove", R.string.StickersRemove));
        addView(delButton, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.END, 0, 16, 14, 0));

        unlockButton = new PremiumButtonView(context, AndroidUtilities.dp(4), false, resourcesProvider);
        unlockButton.setIcon(R.raw.unlock_icon);
        unlockButton.setButton(LocaleController.getString("Unlock", R.string.Unlock), e -> onPremiumButtonClick());
        unlockButton.setVisibility(View.GONE);
        try {
            MarginLayoutParams iconLayout = (MarginLayoutParams) unlockButton.getIconView().getLayoutParams();
            iconLayout.leftMargin = AndroidUtilities.dp(1);
            iconLayout.topMargin = AndroidUtilities.dp(1);
            iconLayout.width = iconLayout.height = AndroidUtilities.dp(20);
            MarginLayoutParams layout = (MarginLayoutParams) unlockButton.getTextView().getLayoutParams();
            layout.leftMargin = AndroidUtilities.dp(3);
            unlockButton.getChildAt(0).setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        } catch (Exception ev) {}
        addView(unlockButton, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.END, 0, 16, 10, 0));

        updateColors();
    }

    public TextView getTextView() {
        return textView;
    }

    protected void onPremiumButtonClick() {

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));

        int width = addButton.getMeasuredWidth();
        int width2 = delButton.getMeasuredWidth();
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) delButton.getLayoutParams();
        if (width2 < width) {
            layoutParams.rightMargin = AndroidUtilities.dp(14) + (width - width2) / 2;
        } else {
            layoutParams.rightMargin = AndroidUtilities.dp(14);
        }

        measureChildWithMargins(textView, widthMeasureSpec, width, heightMeasureSpec, 0);
    }

    public void setStickersSet(TLRPC.StickerSetCovered set, boolean divider, boolean unread, boolean forceInstalled, boolean animated) {
        if (currentAnimation != null) {
            currentAnimation.cancel();
            currentAnimation = null;
        }

        needDivider = divider;
        stickersSet = set;
        setWillNotDraw(!needDivider);

        textView.setText(stickersSet.set.title);
        if (this.unread = unread) {
            Drawable drawable = new Drawable() {

                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

                @Override
                public void draw(Canvas canvas) {
                    paint.setColor(0xff44a8ea);
                    canvas.drawCircle(AndroidUtilities.dp(4), AndroidUtilities.dp(5), AndroidUtilities.dp(3), paint);
                }

                @Override
                public void setAlpha(int alpha) {

                }

                @Override
                public void setColorFilter(ColorFilter colorFilter) {

                }

                @Override
                public int getOpacity() {
                    return PixelFormat.TRANSPARENT;
                }

                @Override
                public int getIntrinsicWidth() {
                    return AndroidUtilities.dp(12);
                }

                @Override
                public int getIntrinsicHeight() {
                    return AndroidUtilities.dp(8);
                }
            };
            textView.setCompoundDrawablesWithIntrinsicBounds(LocaleController.isRTL ? null : drawable, null, LocaleController.isRTL ? drawable : null, null);
        } else {
            textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }

        valueTextView.setText(LocaleController.formatPluralString(set.set.emojis ? "EmojiCount" : "Stickers", set.set.count));

        TLRPC.Document sticker;
        if (set instanceof TLRPC.TL_stickerSetNoCovered && set.set != null) {
            sticker = null;
            waitingForStickerSetId = set.set.id;
            if (!bindedObserver) {
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.groupStickersDidLoad);
                bindedObserver = true;
            }
            TLRPC.TL_messages_stickerSet fullSet = MediaDataController.getInstance(currentAccount).getStickerSet(MediaDataController.getInputStickerSet(set.set), set.set.hash, false);
            if (fullSet != null && fullSet.documents != null && !fullSet.documents.isEmpty()) {
                sticker = fullSet.documents.get(0);
                for (int i = 0; i < fullSet.documents.size(); ++i) {
                    if (fullSet.documents.get(i).id == set.set.thumb_document_id) {
                        sticker = fullSet.documents.get(i);
                        break;
                    }
                }
            }
        } else if (set.cover != null) {
            sticker = set.cover;
        } else if (!set.covers.isEmpty()) {
            sticker = set.covers.get(0);
            if (set.set != null) {
                for (int i = 0; i < set.covers.size(); ++i) {
                    if (set.covers.get(i).id == set.set.thumb_document_id) {
                        sticker = set.covers.get(i);
                        break;
                    }
                }
            }
        } else if ((set instanceof TLRPC.TL_stickerSetFullCovered && !((TLRPC.TL_stickerSetFullCovered) set).documents.isEmpty())) {
            ArrayList<TLRPC.Document> documents = ((TLRPC.TL_stickerSetFullCovered) set).documents;
            sticker = documents.get(0);
            for (int i = 0; i < documents.size(); ++i) {
                if (documents.get(i).id == set.set.thumb_document_id) {
                    sticker = documents.get(i);
                    break;
                }
            }
        } else {
            sticker = null;
        }
        if (sticker != null) {
            if (MessageObject.canAutoplayAnimatedSticker(sticker)) {
                TLObject object = FileLoader.getClosestPhotoSizeWithSize(set.set.thumbs, 90);
                if (object == null) {
                    object = sticker;
                }
                SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(set.set.thumbs, Theme.key_windowBackgroundGray, 1.0f);
                ImageLocation imageLocation;

                if (object instanceof TLRPC.Document) { // first sticker in set as a thumb
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90);
                    imageLocation = ImageLocation.getForDocument(thumb, sticker);
                } else { // unique thumb
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
                final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90);
                if (thumb != null) {
                    imageView.setImage(ImageLocation.getForDocument(thumb, sticker), "50_50", "webp", null, set);
                } else {
                    imageView.setImage(ImageLocation.getForDocument(sticker), "50_50", "webp", null, set);
                }
            }
        } else {
            imageView.setImage(null, null, "webp", null, set);
        }

        addButton.setVisibility(VISIBLE);
        this.forceInstalled = forceInstalled;
        isInstalled = forceInstalled || MediaDataController.getInstance(currentAccount).isStickerPackInstalled(set.set.id);
        isLocked = !UserConfig.getInstance(currentAccount).isPremium() && MessageObject.isPremiumEmojiPack(set);
        if (animated) {
            if (isLocked) {
                unlockButton.setVisibility(VISIBLE);
                delButton.setVisibility(VISIBLE);
                addButton.setVisibility(VISIBLE);
            } else {
                unlockButton.setVisibility(VISIBLE);
                if (isInstalled) {
                    delButton.setVisibility(VISIBLE);
                } else {
                    addButton.setVisibility(VISIBLE);
                }
            }
            currentAnimation = new AnimatorSet();
            currentAnimation.setDuration(250);
            currentAnimation.playTogether(
                    ObjectAnimator.ofFloat(delButton, View.ALPHA, isInstalled && !isLocked ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(delButton, View.SCALE_X, isInstalled && !isLocked ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(delButton, View.SCALE_Y, isInstalled && !isLocked ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(addButton, View.ALPHA, isInstalled || isLocked ? 0.0f : 1.0f),
                    ObjectAnimator.ofFloat(addButton, View.SCALE_X, isInstalled || isLocked ? 0.0f : 1.0f),
                    ObjectAnimator.ofFloat(unlockButton, View.SCALE_Y, !isLocked ? 0.0f : 1.0f),
                    ObjectAnimator.ofFloat(unlockButton, View.SCALE_X, !isLocked ? 0.0f : 1.0f),
                    ObjectAnimator.ofFloat(unlockButton, View.SCALE_Y, !isLocked ? 0.0f : 1.0f));
            currentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (isLocked) {
                        addButton.setVisibility(INVISIBLE);
                        delButton.setVisibility(INVISIBLE);
                        unlockButton.setVisibility(VISIBLE);
                    } else {
                        if (isInstalled) {
                            addButton.setVisibility(INVISIBLE);
                        } else {
                            delButton.setVisibility(INVISIBLE);
                        }
                        unlockButton.setVisibility(GONE);
                    }
                }
            });
            currentAnimation.setInterpolator(new OvershootInterpolator(1.02f));
            currentAnimation.start();
        } else {
            if (isLocked) {
                unlockButton.setVisibility(VISIBLE);
                unlockButton.setAlpha(1.0f);
                unlockButton.setScaleX(1.0f);
                unlockButton.setScaleY(1.0f);
                addButton.setVisibility(INVISIBLE);
                addButton.setAlpha(0.0f);
                addButton.setScaleX(0.0f);
                addButton.setScaleY(0.0f);
                delButton.setVisibility(INVISIBLE);
                delButton.setAlpha(0.0f);
                delButton.setScaleX(0.0f);
                delButton.setScaleY(0.0f);
            } else {
                unlockButton.setVisibility(GONE);
                unlockButton.setAlpha(0.0f);
                unlockButton.setScaleX(0.0f);
                unlockButton.setScaleY(0.0f);
                if (isInstalled) {
                    delButton.setVisibility(VISIBLE);
                    delButton.setAlpha(1.0f);
                    delButton.setScaleX(1.0f);
                    delButton.setScaleY(1.0f);
                    addButton.setVisibility(INVISIBLE);
                    addButton.setAlpha(0.0f);
                    addButton.setScaleX(0.0f);
                    addButton.setScaleY(0.0f);
                } else {
                    addButton.setVisibility(VISIBLE);
                    addButton.setAlpha(1.0f);
                    addButton.setScaleX(1.0f);
                    addButton.setScaleY(1.0f);
                    delButton.setVisibility(INVISIBLE);
                    delButton.setAlpha(0.0f);
                    delButton.setScaleX(0.0f);
                    delButton.setScaleY(0.0f);
                }
            }
        }
    }

    public TLRPC.StickerSetCovered getStickerSet() {
        return stickersSet;
    }

    public void setAddOnClickListener(OnClickListener onClickListener) {
        addButton.setOnClickListener(onClickListener);
        delButton.setOnClickListener(onClickListener);
    }

    public void setDrawProgress(boolean value, boolean animated) {
        addButton.setDrawProgress(value, animated);
    }

    public boolean isInstalled() {
        return isInstalled;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(71), getHeight() - 1, getWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(71) : 0), getHeight() - 1, Theme.dividerPaint);
        }
    }

    public BackupImageView getImageView() {
        return imageView;
    }

    public void updateColors() {
        addButton.setProgressColor(Theme.getColor(Theme.key_featuredStickers_buttonProgress));
        addButton.setBackgroundRoundRect(Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed));
    }

    public static void createThemeDescriptions(List<ThemeDescription> descriptions, RecyclerListView listView, ThemeDescription.ThemeDescriptionDelegate delegate) {
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{FeaturedStickerSetCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{FeaturedStickerSetCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{FeaturedStickerSetCell.class}, new String[]{"addButton"}, null, null, null, Theme.key_featuredStickers_buttonText));
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{FeaturedStickerSetCell.class}, new String[]{"delButton"}, null, null, null, Theme.key_featuredStickers_removeButtonText));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{FeaturedStickerSetCell.class}, Theme.dividerPaint, null, null, Theme.key_divider));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_featuredStickers_buttonProgress));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_featuredStickers_addButtonPressed));
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (bindedObserver) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.groupStickersDidLoad);
            bindedObserver = false;
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.groupStickersDidLoad) {
            long setId = (long) args[0];
            if (waitingForStickerSetId != null && waitingForStickerSetId == setId) {
                waitingForStickerSetId = null;
                TLRPC.TL_stickerSetNoCovered setNoCovered = new TLRPC.TL_stickerSetNoCovered();
                setNoCovered.set = ((TLRPC.TL_messages_stickerSet) args[1]).set;
                setStickersSet(setNoCovered, needDivider, unread, forceInstalled, true);
            }
        }
    }
}
