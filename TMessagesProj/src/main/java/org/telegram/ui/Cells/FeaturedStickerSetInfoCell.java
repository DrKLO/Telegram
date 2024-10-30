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
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.ColorSpanUnderline;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ProgressButton;
import org.telegram.ui.Components.RecyclerListView;

import java.util.List;

public class FeaturedStickerSetInfoCell extends FrameLayout {

    private boolean canAddRemove;

    private TextView nameTextView;
    private TextView infoTextView;
    private ProgressButton addButton;
    private TextView delButton;
    private TLRPC.StickerSetCovered set;

    private AnimatorSet animatorSet;

    private boolean needDivider;
    private boolean isInstalled;
    private boolean hasOnClick;
    private boolean isUnread;

    private int currentAccount = UserConfig.selectedAccount;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int stickerSetNameSearchIndex;
    private int stickerSetNameSearchLength;

    private CharSequence url;
    private int urlSearchLength;

    float unreadProgress;
    private final Theme.ResourcesProvider resourcesProvider;

    public FeaturedStickerSetInfoCell(Context context, int left, boolean supportRtl, boolean canAddRemove, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.canAddRemove = canAddRemove;
        this.resourcesProvider = resourcesProvider;

        FrameLayout.LayoutParams lp;

        nameTextView = new TextView(context);
        nameTextView.setTextColor(getThemedColor(Theme.key_chat_emojiPanelTrendingTitle));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        nameTextView.setTypeface(AndroidUtilities.bold());
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setSingleLine(true);
        if (supportRtl) {
            lp = LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START, left, 8, 40, 0);
        } else {
            lp = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, left, 8, 40, 0);
        }
        addView(nameTextView, lp);

        infoTextView = new TextView(context);
        infoTextView.setTextColor(getThemedColor(Theme.key_chat_emojiPanelTrendingDescription));
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        infoTextView.setEllipsize(TextUtils.TruncateAt.END);
        infoTextView.setSingleLine(true);
        if (supportRtl) {
            lp = LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START, left, 30, 100, 0);
        } else {
            lp = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, left, 30, 100, 0);
        }
        addView(infoTextView, lp);

        if (canAddRemove) {
            addButton = new ProgressButton(context);
            addButton.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
            addButton.setText(LocaleController.getString(R.string.Add));
            if (supportRtl) {
                lp = LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.END, 0, 16, 14, 0);
            } else {
                lp = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.RIGHT, 0, 16, 14, 0);
            }
            addView(addButton, lp);

            delButton = new TextView(context);
            delButton.setGravity(Gravity.CENTER);
            delButton.setTextColor(getThemedColor(Theme.key_featuredStickers_removeButtonText));
            delButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            delButton.setTypeface(AndroidUtilities.bold());
            delButton.setText(LocaleController.getString(R.string.StickersRemove));
            if (supportRtl) {
                lp = LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.END, 0, 16, 14, 0);
            } else {
                lp = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.RIGHT, 0, 16, 14, 0);
            }
            addView(delButton, lp);
        }

        setWillNotDraw(false);
        updateColors();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(60), MeasureSpec.EXACTLY));

        if (canAddRemove) {
            int width = addButton.getMeasuredWidth();
            int width2 = delButton.getMeasuredWidth();
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) delButton.getLayoutParams();
            if (width2 < width) {
                layoutParams.rightMargin = AndroidUtilities.dp(14) + (width - width2) / 2;
            } else {
                layoutParams.rightMargin = AndroidUtilities.dp(14);
            }

            measureChildWithMargins(nameTextView, widthMeasureSpec, width, heightMeasureSpec, 0);
        }
    }

    public void setAddOnClickListener(OnClickListener onClickListener) {
        if (canAddRemove) {
            hasOnClick = true;
            addButton.setOnClickListener(onClickListener);
            delButton.setOnClickListener(onClickListener);
        }
    }

    public void setAddDrawProgress(boolean drawProgress, boolean animated) {
        if (canAddRemove) {
            addButton.setDrawProgress(drawProgress, animated);
        }
    }

    public void setStickerSet(TLRPC.StickerSetCovered stickerSet, boolean unread) {
        setStickerSet(stickerSet, unread, false, 0, 0, false);
    }

    public void setStickerSet(TLRPC.StickerSetCovered stickerSet, boolean unread, boolean animated) {
        setStickerSet(stickerSet, unread, animated, 0, 0, false);
    }

    public void setStickerSet(TLRPC.StickerSetCovered stickerSet, boolean unread, boolean animated, int index, int searchLength) {
        setStickerSet(stickerSet, unread, animated, index, searchLength, false);
    }

    public void setStickerSet(TLRPC.StickerSetCovered stickerSet, boolean unread, boolean animated, int index, int searchLength, boolean forceInstalled) {
        if (animatorSet != null) {
            animatorSet.cancel();
            animatorSet = null;
        }
        if (set != stickerSet) {
            unreadProgress = unread ? 1f : 0;
            invalidate();
        }

        set = stickerSet;
        stickerSetNameSearchIndex = index;
        stickerSetNameSearchLength = searchLength;
        if (searchLength != 0) {
            updateStickerSetNameSearchSpan();
        } else {
            nameTextView.setText(stickerSet.set.title);
        }
        if (stickerSet.set.emojis) {
            infoTextView.setText(LocaleController.formatPluralString("EmojiCount", stickerSet.set.count));
        } else {
            infoTextView.setText(LocaleController.formatPluralString("Stickers", stickerSet.set.count));
        }
        isUnread = unread;
        if (canAddRemove) {
            if (hasOnClick) {
                addButton.setVisibility(VISIBLE);
                isInstalled = forceInstalled || MediaDataController.getInstance(currentAccount).isStickerPackInstalled(stickerSet.set.id);
                if (animated) {
                    if (isInstalled) {
                        delButton.setVisibility(VISIBLE);
                    } else {
                        addButton.setVisibility(VISIBLE);
                    }
                    animatorSet = new AnimatorSet();
                    animatorSet.setDuration(250);
                    animatorSet.playTogether(
                            ObjectAnimator.ofFloat(delButton, View.ALPHA, isInstalled ? 1.0f : 0.0f),
                            ObjectAnimator.ofFloat(delButton, View.SCALE_X, isInstalled ? 1.0f : 0.0f),
                            ObjectAnimator.ofFloat(delButton, View.SCALE_Y, isInstalled ? 1.0f : 0.0f),
                            ObjectAnimator.ofFloat(addButton, View.ALPHA, isInstalled ? 0.0f : 1.0f),
                            ObjectAnimator.ofFloat(addButton, View.SCALE_X, isInstalled ? 0.0f : 1.0f),
                            ObjectAnimator.ofFloat(addButton, View.SCALE_Y, isInstalled ? 0.0f : 1.0f));
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (isInstalled) {
                                addButton.setVisibility(INVISIBLE);
                            } else {
                                delButton.setVisibility(INVISIBLE);
                            }
                        }
                    });
                    animatorSet.setInterpolator(new OvershootInterpolator(1.02f));
                    animatorSet.start();
                } else {
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
            } else {
                addButton.setVisibility(GONE);
            }
        }
    }

    private void updateStickerSetNameSearchSpan() {
        if (stickerSetNameSearchLength != 0) {
            SpannableStringBuilder builder = new SpannableStringBuilder(set.set.title);
            try {
                builder.setSpan(new ForegroundColorSpan(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4)), stickerSetNameSearchIndex, stickerSetNameSearchIndex + stickerSetNameSearchLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception ignore) {
            }
            nameTextView.setText(builder);
        }
    }

    public void setUrl(CharSequence text, int searchLength) {
        url = text;
        urlSearchLength = searchLength;
        updateUrlSearchSpan();
    }

    private void updateUrlSearchSpan() {
        if (url != null) {
            SpannableStringBuilder builder = new SpannableStringBuilder(url);
            try {
                builder.setSpan(new ColorSpanUnderline(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4)), 0, urlSearchLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ColorSpanUnderline(getThemedColor(Theme.key_chat_emojiPanelTrendingDescription)), urlSearchLength, url.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception ignore) {
            }
            infoTextView.setText(builder);
        }
    }

    public boolean isInstalled() {
        return isInstalled;
    }

    public TLRPC.StickerSetCovered getStickerSet() {
        return set;
    }

    public boolean isNeedDivider() {
        return needDivider;
    }

    public void setNeedDivider(boolean needDivider) {
        this.needDivider = needDivider;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isUnread || unreadProgress != 0f) {
            if (isUnread && unreadProgress != 1f) {
                unreadProgress += 16f / 100f;
                if (unreadProgress > 1f) {
                    unreadProgress = 1f;
                } else {
                    invalidate();
                }
            } else if (!isUnread && unreadProgress != 0) {
                unreadProgress -= 16f / 100f;
                if (unreadProgress < 0) {
                    unreadProgress = 0;
                } else {
                    invalidate();
                }
            }
            paint.setColor(getThemedColor(Theme.key_featuredStickers_unread));
            canvas.drawCircle(nameTextView.getRight() + AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(4) * unreadProgress, paint);
        }
        if (needDivider) {
            canvas.drawLine(0, 0, getWidth(), 0, Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider));
        }
    }

    public void updateColors() {
        if (canAddRemove) {
            addButton.setProgressColor(getThemedColor(Theme.key_featuredStickers_buttonProgress));
            addButton.setBackgroundRoundRect(getThemedColor(Theme.key_featuredStickers_addButton), getThemedColor(Theme.key_featuredStickers_addButtonPressed));
        }
        updateStickerSetNameSearchSpan();
        updateUrlSearchSpan();
    }

    public static void createThemeDescriptions(List<ThemeDescription> descriptions, RecyclerListView listView, ThemeDescription.ThemeDescriptionDelegate delegate) {
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{FeaturedStickerSetInfoCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_chat_emojiPanelTrendingTitle));
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{FeaturedStickerSetInfoCell.class}, new String[]{"infoTextView"}, null, null, null, Theme.key_chat_emojiPanelTrendingDescription));
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{FeaturedStickerSetInfoCell.class}, new String[]{"addButton"}, null, null, null, Theme.key_featuredStickers_buttonText));
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{FeaturedStickerSetInfoCell.class}, new String[]{"delButton"}, null, null, null, Theme.key_featuredStickers_removeButtonText));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{FeaturedStickerSetInfoCell.class}, null, null, null, Theme.key_featuredStickers_unread));
        descriptions.add(new ThemeDescription(listView, 0, new Class[]{FeaturedStickerSetInfoCell.class}, Theme.dividerPaint, null, null, Theme.key_divider));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_featuredStickers_buttonProgress));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_featuredStickers_addButton));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_featuredStickers_addButtonPressed));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_windowBackgroundWhiteBlueText4));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_chat_emojiPanelTrendingDescription));
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
