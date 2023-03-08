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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.os.Build;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.ForegroundColorSpanThemable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumButtonView;
import org.telegram.ui.Components.RadialProgressView;

import java.util.ArrayList;
import java.util.Locale;

public class StickerSetCell extends FrameLayout {
    private final static String LINK_PREFIX = "t.me/addstickers/";
    private final static String LINK_PREFIX_EMOJI = "t.me/addemoji/";

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

    private boolean emojis;
    private FrameLayout sideButtons;
    private TextView addButtonView;
    private TextView removeButtonView;
    private PremiumButtonView premiumButtonView;

    public StickerSetCell(Context context, int option) {
        this(context, null, option);
    }

    public StickerSetCell(Context context, Theme.ResourcesProvider resourcesProvider, int option) {
        super(context);
        this.option = option;

        imageView = new BackupImageView(context);
        imageView.setAspectFit(true);
        imageView.setLayerNum(1);
        addView(imageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 13, 9, LocaleController.isRTL ? 13 : 0, 0));

        if (option != 0) {
            optionsButton = new ImageView(context);
            optionsButton.setFocusable(false);
            optionsButton.setScaleType(ImageView.ScaleType.CENTER);
            if (option != 3) {
                optionsButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_stickers_menuSelector)));
            }
            if (option == 1) {
                optionsButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_stickers_menu), PorterDuff.Mode.MULTIPLY));
                optionsButton.setImageResource(R.drawable.msg_actions);
                optionsButton.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
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
                optionsButton.setImageResource(R.drawable.floating_check);
                addView(optionsButton, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, (LocaleController.isRTL ? 10 : 0), 9, (LocaleController.isRTL ? 0 : 10), 0));
            }
        }

        sideButtons = new FrameLayout(getContext());

        addButtonView = new TextView(context);
        addButtonView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        addButtonView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        addButtonView.setText(LocaleController.getString("Add", R.string.Add));
        addButtonView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
        addButtonView.setBackground(Theme.AdaptiveRipple.createRect(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourcesProvider), 4));
        addButtonView.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
        addButtonView.setGravity(Gravity.CENTER);
        addButtonView.setOnClickListener(e -> onAddButtonClick());
        sideButtons.addView(addButtonView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 28, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL));

        removeButtonView = new TextView(context);
        removeButtonView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        removeButtonView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        removeButtonView.setText(LocaleController.getString("StickersRemove", R.string.StickersRemove));
        removeButtonView.setTextColor(Theme.getColor(Theme.key_featuredStickers_removeButtonText, resourcesProvider));
        removeButtonView.setBackground(Theme.AdaptiveRipple.createRect(0, Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider) & 0x1affffff, 4));
        removeButtonView.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        removeButtonView.setGravity(Gravity.CENTER);
        removeButtonView.setOnClickListener(e -> onRemoveButtonClick());
        sideButtons.addView(removeButtonView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 32, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 0, -2, 0, 0));

        premiumButtonView = new PremiumButtonView(context, AndroidUtilities.dp(4), false);
        premiumButtonView.setIcon(R.raw.unlock_icon);
        premiumButtonView.setButton(LocaleController.getString("Unlock", R.string.Unlock), e -> onPremiumButtonClick());
        try {
            MarginLayoutParams iconLayout = (MarginLayoutParams) premiumButtonView.getIconView().getLayoutParams();
            iconLayout.leftMargin = AndroidUtilities.dp(1);
            iconLayout.topMargin = AndroidUtilities.dp(1);
            iconLayout.width = iconLayout.height = AndroidUtilities.dp(20);
            MarginLayoutParams layout = (MarginLayoutParams) premiumButtonView.getTextView().getLayoutParams();
            layout.leftMargin = AndroidUtilities.dp(3);
            premiumButtonView.getChildAt(0).setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        } catch (Exception ev) {}
        sideButtons.addView(premiumButtonView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 28, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL));

        sideButtons.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        addView(sideButtons, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 0, 0, 0, 0));
        sideButtons.setOnClickListener(e -> {
            if (premiumButtonView.getVisibility() == View.VISIBLE && premiumButtonView.isEnabled()) {
                premiumButtonView.performClick();
            } else if (addButtonView.getVisibility() == View.VISIBLE && addButtonView.isEnabled()) {
                addButtonView.performClick();
            } else if (removeButtonView.getVisibility() == View.VISIBLE && removeButtonView.isEnabled()) {
                removeButtonView.performClick();
            }
        });

        textView = new TextView(context) {
            @Override
            public void setText(CharSequence text, BufferType type) {
                text = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false);
                super.setText(text, type);
            }
        };
        NotificationCenter.listenEmojiLoading(textView);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity(LayoutHelper.getAbsoluteGravityStart());
        addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START, 71, 9, 70, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setGravity(LayoutHelper.getAbsoluteGravityStart());
        addView(valueTextView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START, 71, 32, 70, 0));

        updateButtonState(BUTTON_STATE_EMPTY, false);
    }

    protected void onAddButtonClick() {

    }

    protected void onRemoveButtonClick() {

    }

    protected void onPremiumButtonClick() {

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
        setStickersSet(set, divider, false);
    }

    public void setSearchQuery(TLRPC.TL_messages_stickerSet tlSet, String query, Theme.ResourcesProvider resourcesProvider) {
        TLRPC.StickerSet set = tlSet.set;
        int titleIndex = set.title.toLowerCase(Locale.ROOT).indexOf(query);
        if (titleIndex != -1) {
            SpannableString spannableString = new SpannableString(set.title);
            spannableString.setSpan(new ForegroundColorSpanThemable(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider), titleIndex, titleIndex + query.length(), 0);
            textView.setText(spannableString);
        }
        int linkIndex = set.short_name.toLowerCase(Locale.ROOT).indexOf(query);
        if (linkIndex != -1) {
            String linkPrefix = LINK_PREFIX;
            if (set.emojis) {
                linkPrefix = LINK_PREFIX_EMOJI;
            }
            linkIndex += linkPrefix.length();
            SpannableString spannableString = new SpannableString(linkPrefix + set.short_name);
            spannableString.setSpan(new ForegroundColorSpanThemable(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider), linkIndex, linkIndex + query.length(), 0);
            valueTextView.setText(spannableString);
        }
    }

    @SuppressLint("SetTextI18n")
    public void setStickersSet(TLRPC.TL_messages_stickerSet set, boolean divider, boolean groupSearch) {
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

        emojis = set.set.emojis;
        sideButtons.setVisibility(emojis ? View.VISIBLE : View.GONE);
        optionsButton.setVisibility(emojis ? View.GONE : View.VISIBLE);
        imageView.setColorFilter(null);

        ArrayList<TLRPC.Document> documents = set.documents;
        if (documents != null && !documents.isEmpty()) {
            valueTextView.setText(LocaleController.formatPluralString(emojis ? "EmojiCount" : "Stickers", documents.size()));

            TLRPC.Document sticker = null;
            for (int i = 0; i < documents.size(); ++i) {
                TLRPC.Document d = documents.get(i);
                if (d != null && d.id == set.set.thumb_document_id) {
                    sticker = d;
                    break;
                }
            }
            if (sticker == null) {
                sticker = documents.get(0);
            }
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

            if (object instanceof TLRPC.Document && MessageObject.isAnimatedStickerDocument(sticker, true) || MessageObject.isVideoSticker(sticker)) {
                if (svgThumb != null) {
                    imageView.setImage(ImageLocation.getForDocument(sticker), "50_50", svgThumb, 0, set);
                } else {
                    imageView.setImage(ImageLocation.getForDocument(sticker), "50_50", imageLocation, null, 0, set);
                }
                if (MessageObject.isTextColorEmoji(sticker)) {
                    imageView.setColorFilter(Theme.chat_animatedEmojiTextColorFilter);
                }
            } else if (imageLocation != null && imageLocation.imageType == FileLoader.IMAGE_TYPE_LOTTIE) {
                imageView.setImage(imageLocation, "50_50", "tgs", svgThumb, set);
            } else {
                imageView.setImage(imageLocation, "50_50", "webp", svgThumb, set);
            }
        } else {
            valueTextView.setText(LocaleController.formatPluralString(set.set.emojis ? "EmojiCount" : "Stickers", 0));
            imageView.setImageDrawable(null);
        }
        if (groupSearch) {
            valueTextView.setText((set.set.emojis ? LINK_PREFIX_EMOJI : LINK_PREFIX) + set.set.short_name);
        }
    }

    public void setChecked(boolean checked) {
        setChecked(checked, true);
    }

    public boolean isChecked() {
        if (option == 1) {
            return checkBox.isChecked();
        }
        if (option == 3) {
            return optionsButton.getVisibility() == VISIBLE;
        }
        if (emojis) {
            return sideButtons.getVisibility() == VISIBLE;
        }
        return false;
    }

    public void setChecked(boolean checked, boolean animated) {
        if (option == 1) {
            checkBox.setChecked(checked, animated);
        } else if (emojis) {
            if (animated) {
                sideButtons.animate().cancel();
                sideButtons.animate().setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!checked) {
                            sideButtons.setVisibility(INVISIBLE);
                        }
                    }

                    @Override
                    public void onAnimationStart(Animator animation) {
                        if (checked) {
                            sideButtons.setVisibility(VISIBLE);
                        }
                    }
                }).alpha(checked ? 1 : 0).scaleX(checked ? 1 : 0.1f).scaleY(checked ? 1 : 0.1f).setDuration(150).start();
            } else {
                sideButtons.setVisibility(checked ? VISIBLE : INVISIBLE);
                if (!checked) {
                    sideButtons.setScaleX(0.1f);
                    sideButtons.setScaleY(0.1f);
                }
            }
        } else if (option == 3) {
            if (animated) {
                optionsButton.animate().cancel();
                optionsButton.animate().setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!checked) {
                            optionsButton.setVisibility(INVISIBLE);
                        }
                    }

                    @Override
                    public void onAnimationStart(Animator animation) {
                        if (checked) {
                            optionsButton.setVisibility(VISIBLE);
                        }
                    }
                }).alpha(checked ? 1 : 0).scaleX(checked ? 1 : 0.1f).scaleY(checked ? 1 : 0.1f).setDuration(150).start();
            } else {
                optionsButton.setVisibility(checked ? VISIBLE : INVISIBLE);
                if (!checked) {
                    optionsButton.setScaleX(0.1f);
                    optionsButton.setScaleY(0.1f);
                }
            }
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

                if (emojis) {
                    sideButtons.setVisibility(VISIBLE);
                    sideButtons.animate()
                        .alpha(alphaValues[1])
                        .scaleX(scaleValues[1])
                        .scaleY(scaleValues[1])
                        .setDuration(200)
                        .setInterpolator(Easings.easeOutSine)
                        .withEndAction(() -> {
                            if (reorderable) {
                                sideButtons.setVisibility(GONE);
                            }
                        }).start();
                } else {
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
                }

            } else {
                reorderButton.setVisibility(reorderable ? VISIBLE : GONE);
                reorderButton.setAlpha(alphaValues[0]);
                reorderButton.setScaleX(scaleValues[0]);
                reorderButton.setScaleY(scaleValues[0]);

                if (emojis) {
                    sideButtons.setVisibility(reorderable ? GONE : VISIBLE);
                    sideButtons.setAlpha(alphaValues[1]);
                    sideButtons.setScaleX(scaleValues[1]);
                    sideButtons.setScaleY(scaleValues[1]);
                } else {
                    optionsButton.setVisibility(reorderable ? GONE : VISIBLE);
                    optionsButton.setAlpha(alphaValues[1]);
                    optionsButton.setScaleX(scaleValues[1]);
                    optionsButton.setScaleY(scaleValues[1]);
                }

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
        if (Build.VERSION.SDK_INT >= 21 && getBackground() != null && emojis && sideButtons != null) {
            sideButtons.getHitRect(rect);
            if (rect.contains((int) event.getX(), (int) event.getY())) {
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(71), getHeight() - 1, getWidth() - getPaddingRight() - (LocaleController.isRTL ? AndroidUtilities.dp(71) : 0), getHeight() - 1, Theme.dividerPaint);
        }
    }

    public void updateRightMargin() {
        sideButtons.measure(MeasureSpec.makeMeasureSpec(999999, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(58), MeasureSpec.EXACTLY));
        final int margin = AndroidUtilities.dp(26) + sideButtons.getMeasuredWidth();
        if (LocaleController.isRTL) {
            ((MarginLayoutParams) textView.getLayoutParams()).leftMargin = margin;
            ((MarginLayoutParams) valueTextView.getLayoutParams()).leftMargin = margin;
        } else {
            ((MarginLayoutParams) textView.getLayoutParams()).rightMargin = margin;
            ((MarginLayoutParams) valueTextView.getLayoutParams()).rightMargin = margin;
        }
    }

    public static final int BUTTON_STATE_EMPTY = 0;
    public static final int BUTTON_STATE_LOCKED = 1;
    public static final int BUTTON_STATE_LOCKED_RESTORE = 2;
    public static final int BUTTON_STATE_ADD = 3;
    public static final int BUTTON_STATE_REMOVE = 4;

    private AnimatorSet stateAnimator;
    public void updateButtonState(int state, boolean animated) {
        if (stateAnimator != null) {
            stateAnimator.cancel();
            stateAnimator = null;
        }
        if (state == BUTTON_STATE_LOCKED) {
            premiumButtonView.setButton(LocaleController.getString("Unlock", R.string.Unlock), e -> onPremiumButtonClick());
        } else if (state == BUTTON_STATE_LOCKED_RESTORE) {
            premiumButtonView.setButton(LocaleController.getString("Restore", R.string.Restore), e -> onPremiumButtonClick());
        }
        premiumButtonView.setEnabled(state == BUTTON_STATE_LOCKED || state == BUTTON_STATE_LOCKED_RESTORE);
        addButtonView.setEnabled(state == BUTTON_STATE_ADD);
        removeButtonView.setEnabled(state == BUTTON_STATE_REMOVE);
        if (animated) {
            stateAnimator = new AnimatorSet();
            stateAnimator.playTogether(
                    ObjectAnimator.ofFloat(premiumButtonView, ALPHA, state == BUTTON_STATE_LOCKED || state == BUTTON_STATE_LOCKED_RESTORE ? 1 : 0),
                    ObjectAnimator.ofFloat(premiumButtonView, SCALE_X, state == BUTTON_STATE_LOCKED || state == BUTTON_STATE_LOCKED_RESTORE ? 1 : .6f),
                    ObjectAnimator.ofFloat(premiumButtonView, SCALE_Y, state == BUTTON_STATE_LOCKED || state == BUTTON_STATE_LOCKED_RESTORE ? 1 : .6f),
                    ObjectAnimator.ofFloat(addButtonView, ALPHA, state == BUTTON_STATE_ADD ? 1 : 0),
                    ObjectAnimator.ofFloat(addButtonView, SCALE_X, state == BUTTON_STATE_ADD ? 1 : .6f),
                    ObjectAnimator.ofFloat(addButtonView, SCALE_Y, state == BUTTON_STATE_ADD ? 1 : .6f),
                    ObjectAnimator.ofFloat(removeButtonView, ALPHA, state == BUTTON_STATE_REMOVE ? 1 : 0),
                    ObjectAnimator.ofFloat(removeButtonView, SCALE_X, state == BUTTON_STATE_REMOVE ? 1 : .6f),
                    ObjectAnimator.ofFloat(removeButtonView, SCALE_Y, state == BUTTON_STATE_REMOVE ? 1 : .6f)
            );
            stateAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    premiumButtonView.setVisibility(View.VISIBLE);
                    addButtonView.setVisibility(View.VISIBLE);
                    removeButtonView.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    premiumButtonView.setVisibility(state == BUTTON_STATE_LOCKED || state == BUTTON_STATE_LOCKED_RESTORE ? View.VISIBLE : View.GONE);
                    addButtonView.setVisibility(state == BUTTON_STATE_ADD ? View.VISIBLE : View.GONE);
                    removeButtonView.setVisibility(state == BUTTON_STATE_REMOVE ? View.VISIBLE : View.GONE);
                    updateRightMargin();
                }
            });
            stateAnimator.setDuration(250);
            stateAnimator.setInterpolator(new OvershootInterpolator(1.02f));
            stateAnimator.start();
        } else {
            premiumButtonView.setAlpha(state == BUTTON_STATE_LOCKED || state == BUTTON_STATE_LOCKED_RESTORE ? 1 : 0);
            premiumButtonView.setScaleX(state == BUTTON_STATE_LOCKED || state == BUTTON_STATE_LOCKED_RESTORE ? 1 : .6f);
            premiumButtonView.setScaleY(state == BUTTON_STATE_LOCKED || state == BUTTON_STATE_LOCKED_RESTORE ? 1 : .6f);
            premiumButtonView.setVisibility(state == BUTTON_STATE_LOCKED || state == BUTTON_STATE_LOCKED_RESTORE ? View.VISIBLE : View.GONE);
            addButtonView.setAlpha(state == BUTTON_STATE_ADD ? 1 : 0);
            addButtonView.setScaleX(state == BUTTON_STATE_ADD ? 1 : .6f);
            addButtonView.setScaleY(state == BUTTON_STATE_ADD ? 1 : .6f);
            addButtonView.setVisibility(state == BUTTON_STATE_ADD ? View.VISIBLE : View.GONE);
            removeButtonView.setAlpha(state == BUTTON_STATE_REMOVE ? 1 : 0);
            removeButtonView.setScaleX(state == BUTTON_STATE_REMOVE ? 1 : .6f);
            removeButtonView.setScaleY(state == BUTTON_STATE_REMOVE ? 1 : .6f);
            removeButtonView.setVisibility(state == BUTTON_STATE_REMOVE ? View.VISIBLE : View.GONE);
            updateRightMargin();
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (checkBox != null && checkBox.isChecked()) {
            info.setCheckable(true);
            info.setChecked(true);
        }
    }
}
