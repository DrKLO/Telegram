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
import org.telegram.ui.Components.ColorSpanUnderline;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ProgressButton;

public class FeaturedStickerSetInfoCell extends FrameLayout {

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

    public FeaturedStickerSetInfoCell(Context context, int left) {
        this(context, left, false);
    }

    public FeaturedStickerSetInfoCell(Context context, int left, boolean supportRtl) {
        super(context);

        FrameLayout.LayoutParams lp;

        nameTextView = new TextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_chat_emojiPanelTrendingTitle));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setSingleLine(true);
        if (supportRtl) {
            lp = LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START, left, 8, 40, 0);
        } else {
            lp = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, left, 8, 40, 0);
        }
        addView(nameTextView, lp);

        infoTextView = new TextView(context);
        infoTextView.setTextColor(Theme.getColor(Theme.key_chat_emojiPanelTrendingDescription));
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        infoTextView.setEllipsize(TextUtils.TruncateAt.END);
        infoTextView.setSingleLine(true);
        if (supportRtl) {
            lp = LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START, left, 30, 100, 0);
        } else {
            lp = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, left, 30, 100, 0);
        }
        addView(infoTextView, lp);

        addButton = new ProgressButton(context);
        addButton.setProgressColor(Theme.getColor(Theme.key_featuredStickers_buttonProgress));
        addButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        addButton.setBackgroundRoundRect(Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed));
        addButton.setText(LocaleController.getString("Add", R.string.Add));
        if (supportRtl) {
            lp = LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.END, 0, 16, 14, 0);
        } else {
            lp = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.RIGHT, 0, 16, 14, 0);
        }
        addView(addButton, lp);

        delButton = new TextView(context);
        delButton.setGravity(Gravity.CENTER);
        delButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_removeButtonText));
        delButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        delButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        delButton.setText(LocaleController.getString("StickersRemove", R.string.StickersRemove));
        if (supportRtl) {
            lp = LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.END, 0, 16, 14, 0);
        } else {
            lp = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.RIGHT, 0, 16, 14, 0);
        }
        addView(delButton, lp);

        setWillNotDraw(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(60), MeasureSpec.EXACTLY));

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

    public void setAddOnClickListener(OnClickListener onClickListener) {
        hasOnClick = true;
        addButton.setOnClickListener(onClickListener);
        delButton.setOnClickListener(onClickListener);
    }

    public void setAddDrawProgress(boolean drawProgress, boolean animated) {
        addButton.setDrawProgress(drawProgress, animated);
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
        if (searchLength != 0) {
            SpannableStringBuilder builder = new SpannableStringBuilder(stickerSet.set.title);
            try {
                builder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)), index, index + searchLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception ignore) {

            }
            nameTextView.setText(builder);
        } else {
            nameTextView.setText(stickerSet.set.title);
        }
        infoTextView.setText(LocaleController.formatPluralString("Stickers", stickerSet.set.count));
        isUnread = unread;
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

        set = stickerSet;
    }

    public void setUrl(CharSequence text, int searchLength) {
        if (text != null) {
            SpannableStringBuilder builder = new SpannableStringBuilder(text);
            try {
                builder.setSpan(new ColorSpanUnderline(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)), 0, searchLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ColorSpanUnderline(Theme.getColor(Theme.key_chat_emojiPanelTrendingDescription)), searchLength, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
        if (isUnread) {
            paint.setColor(Theme.getColor(Theme.key_featuredStickers_unread));
            canvas.drawCircle(nameTextView.getRight() + AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(4), paint);
        }
        if (needDivider) {
            canvas.drawLine(0, 0, getWidth(), 0, Theme.dividerPaint);
        }
    }
}
