/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2021.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;

import androidx.core.widget.NestedScrollView;

public class ClearHistoryAlert extends BottomSheet {

    private Drawable shadowDrawable;
    private LinearLayout linearLayout;
    private BottomSheetCell setTimerButton;
    private CheckBoxCell cell;

    private boolean autoDeleteOnly;
    private int scrollOffsetY;

    private int[] location = new int[2];

    private int currentTimer;
    private int newTimer;

    private boolean dismissedDelayed;

    private ClearHistoryAlertDelegate delegate;

    public interface ClearHistoryAlertDelegate {

        default void onClearHistory(boolean revoke) {

        }

        default void onAutoDeleteHistory(int ttl, int action) {

        }
    }

    public static class BottomSheetCell extends FrameLayout {

        private View background;
        private TextView textView;
        private LinearLayout linearLayout;
        private final Theme.ResourcesProvider resourcesProvider;

        public BottomSheetCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            background = new View(context);
            background.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), getThemedColor(Theme.key_featuredStickers_addButton), getThemedColor(Theme.key_featuredStickers_addButtonPressed)));
            addView(background, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 16, 16, 16, 16));

            textView = new TextView(context);
            textView.setLines(1);
            textView.setSingleLine(true);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setGravity(Gravity.CENTER);
            textView.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80), MeasureSpec.EXACTLY));
        }

        public void setText(CharSequence text) {
            textView.setText(text);
        }

        protected int getThemedColor(int key) {
            return Theme.getColor(key, resourcesProvider);
        }
    }

    public ClearHistoryAlert(final Context context, TLRPC.User user, TLRPC.Chat chat, boolean full, Theme.ResourcesProvider resourcesProvider) {
        super(context, false, resourcesProvider);
        autoDeleteOnly = !full;
        setApplyBottomPadding(false);

        int ttl;
        if (user != null) {
            TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(user.id);
            ttl = userFull != null ? userFull.ttl_period : 0;
        } else {
            TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(chat.id);
            ttl = chatFull != null ? chatFull.ttl_period : 0;
        }
        if (ttl == 0) {
            newTimer = currentTimer = 0;
        } else if (ttl == 24 * 60 * 60) {
            newTimer = currentTimer = 1;
        } else if (ttl == 7 * 24 * 60 * 60) {
            newTimer = currentTimer = 2;
        } else {
            newTimer = currentTimer = 3;
        }

        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));

        NestedScrollView scrollView = new NestedScrollView(context) {

            private boolean ignoreLayout;

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY) {
                    dismiss();
                    return true;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                return !isDismissed() && super.onTouchEvent(e);
            }

            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                updateLayout();
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int height = MeasureSpec.getSize(heightMeasureSpec);
                measureChildWithMargins(linearLayout, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int contentHeight = linearLayout.getMeasuredHeight();
                int padding = (height / 5 * 3);
                int visiblePart = height - padding;
                if (autoDeleteOnly || contentHeight - visiblePart < AndroidUtilities.dp(90) || contentHeight < height / 2 + AndroidUtilities.dp(90)) {
                    padding = height - contentHeight;
                } else {
                    int minHeight = contentHeight / 2 + AndroidUtilities.dp(108);
                    if (visiblePart < minHeight) {
                        padding = height - minHeight;
                    }
                }
                if (getPaddingTop() != padding) {
                    ignoreLayout = true;
                    setPadding(0, padding, 0, 0);
                    ignoreLayout = false;
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                updateLayout();
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                int top = (int) (scrollOffsetY - backgroundPaddingTop + getScrollY() - getTranslationY());
                shadowDrawable.setBounds(0, top, getMeasuredWidth(), top + linearLayout.getMeasuredHeight() + backgroundPaddingTop + AndroidUtilities.dp(19));
                shadowDrawable.draw(canvas);
            }

            @Override
            protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                super.onScrollChanged(l, t, oldl, oldt);
                updateLayout();
            }
        };
        scrollView.setFillViewport(true);
        scrollView.setWillNotDraw(false);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        containerView = scrollView;

        linearLayout = new LinearLayout(context) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                updateLayout();
            }
        };
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
        setCustomView(linearLayout);

        long selfUserId = UserConfig.getInstance(currentAccount).getClientUserId();

        boolean canRevokeInbox = user != null && !user.bot && user.id != selfUserId && MessagesController.getInstance(currentAccount).canRevokePmInbox;
        int revokeTimeLimit;
        if (user != null) {
            revokeTimeLimit = MessagesController.getInstance(currentAccount).revokeTimePmLimit;
        } else {
            revokeTimeLimit = MessagesController.getInstance(currentAccount).revokeTimeLimit;
        }
        boolean canDeleteInbox = user != null && canRevokeInbox && revokeTimeLimit == 0x7fffffff;
        final boolean[] deleteForAll = new boolean[]{false};
        boolean deleteChatForAll = false;

        if (!autoDeleteOnly) {
            TextView textView = new TextView(context);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            textView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            textView.setText(LocaleController.getString("ClearHistory", R.string.ClearHistory));
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 23, 20, 23, 0));

            TextView messageTextView = new TextView(getContext());
            messageTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            messageTextView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
            messageTextView.setLinkTextColor(getThemedColor(Theme.key_dialogTextLink));
            messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 23, 16, 23, 5));
            if (user != null) {
                messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureClearHistoryWithUser", R.string.AreYouSureClearHistoryWithUser, UserObject.getUserName(user))));
            } else {
                if (!ChatObject.isChannel(chat) || chat.megagroup && !ChatObject.isPublic(chat)) {
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("AreYouSureClearHistoryWithChat", R.string.AreYouSureClearHistoryWithChat, chat.title)));
                } else if (chat.megagroup) {
                    messageTextView.setText(LocaleController.getString("AreYouSureClearHistoryGroup", R.string.AreYouSureClearHistoryGroup));
                } else {
                    messageTextView.setText(LocaleController.getString("AreYouSureClearHistoryChannel", R.string.AreYouSureClearHistoryChannel));
                }
            }

            if (canDeleteInbox && !UserObject.isDeleted(user)) {
                cell = new CheckBoxCell(context, 1, resourcesProvider);
                cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                cell.setText(LocaleController.formatString("ClearHistoryOptionAlso", R.string.ClearHistoryOptionAlso, UserObject.getFirstName(user)), "", false, false);
                cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(5), 0, LocaleController.isRTL ? AndroidUtilities.dp(5) : AndroidUtilities.dp(16), 0);
                linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
                cell.setOnClickListener(v -> {
                    CheckBoxCell cell1 = (CheckBoxCell) v;
                    deleteForAll[0] = !deleteForAll[0];
                    cell1.setChecked(deleteForAll[0], true);
                });
            }

            BottomSheetCell clearButton = new BottomSheetCell(context, resourcesProvider);
            clearButton.setBackground(null);
            clearButton.setText(LocaleController.getString("AlertClearHistory", R.string.AlertClearHistory));
            clearButton.background.setOnClickListener(v -> {
                if (dismissedDelayed) {
                    return;
                }
                delegate.onClearHistory(cell != null && cell.isChecked());
                dismiss();
            });
            linearLayout.addView(clearButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));

            ShadowSectionCell shadowSectionCell = new ShadowSectionCell(context);
            Drawable drawable = Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
            CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(getThemedColor(Theme.key_windowBackgroundGray)), drawable);
            combinedDrawable.setFullsize(true);
            shadowSectionCell.setBackgroundDrawable(combinedDrawable);
            linearLayout.addView(shadowSectionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            HeaderCell headerCell = new HeaderCell(context, resourcesProvider);
            headerCell.setText(LocaleController.getString("AutoDeleteHeader", R.string.AutoDeleteHeader));
            linearLayout.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1, autoDeleteOnly ? 20 : 0, 1, 0));
        } else {
            RLottieImageView lottieImageView = new RLottieImageView(context);
            lottieImageView.setAutoRepeat(false);
            lottieImageView.setAnimation(R.raw.utyan_private, 120, 120);
            lottieImageView.setPadding(0, AndroidUtilities.dp(20), 0, 0);
            lottieImageView.playAnimation();
            linearLayout.addView(lottieImageView, LayoutHelper.createLinear(160, 160, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 17, 0, 17, 0));

            TextView percentTextView = new TextView(context);
            percentTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            percentTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
            percentTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            percentTextView.setText(LocaleController.getString("AutoDeleteAlertTitle", R.string.AutoDeleteAlertTitle));
            linearLayout.addView(percentTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 17, 18, 17, 0));

            TextView infoTextView = new TextView(context);
            infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            infoTextView.setTextColor(getThemedColor(Theme.key_dialogTextGray3));
            infoTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            if (user != null) {
                infoTextView.setText(LocaleController.formatString("AutoDeleteAlertUserInfo", R.string.AutoDeleteAlertUserInfo, UserObject.getFirstName(user)));
            } else {
                if (ChatObject.isChannel(chat) && !chat.megagroup) {
                    infoTextView.setText(LocaleController.getString("AutoDeleteAlertChannelInfo", R.string.AutoDeleteAlertChannelInfo));
                } else {
                    infoTextView.setText(LocaleController.getString("AutoDeleteAlertGroupInfo", R.string.AutoDeleteAlertGroupInfo));
                }
            }
            linearLayout.addView(infoTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 30, 22, 30, 20));
        }

        SlideChooseView slideChooseView = new SlideChooseView(context, resourcesProvider);
        slideChooseView.setCallback(new SlideChooseView.Callback() {
            @Override
            public void onOptionSelected(int index) {
                newTimer = index;
                updateTimerButton(true);
            }

            @Override
            public void onTouchEnd() {
                scrollView.smoothScrollTo(0, linearLayout.getMeasuredHeight());
            }
        });
        String[] strings = new String[]{
                LocaleController.getString("AutoDeleteNever", R.string.AutoDeleteNever),
                LocaleController.getString("AutoDelete24Hours", R.string.AutoDelete24Hours),
                LocaleController.getString("AutoDelete7Days", R.string.AutoDelete7Days),
                LocaleController.getString("AutoDelete1Month", R.string.AutoDelete1Month)
        };
        slideChooseView.setOptions(currentTimer, strings);
        linearLayout.addView(slideChooseView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        FrameLayout buttonContainer = new FrameLayout(context);
        Drawable drawable = Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);
        CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(getThemedColor(Theme.key_windowBackgroundGray)), drawable);
        combinedDrawable.setFullsize(true);
        buttonContainer.setBackgroundDrawable(combinedDrawable);
        linearLayout.addView(buttonContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context, resourcesProvider);
        infoCell.setText(LocaleController.getString("AutoDeleteInfo", R.string.AutoDeleteInfo));
        buttonContainer.addView(infoCell);

        setTimerButton = new BottomSheetCell(context, resourcesProvider);
        setTimerButton.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
        if (autoDeleteOnly) {
            setTimerButton.setText(LocaleController.getString("AutoDeleteSet", R.string.AutoDeleteSet));
        } else if (full && currentTimer == 0) {
            setTimerButton.setText(LocaleController.getString("EnableAutoDelete", R.string.EnableAutoDelete));
        } else {
            setTimerButton.setText(LocaleController.getString("AutoDeleteConfirm", R.string.AutoDeleteConfirm));
        }
        setTimerButton.background.setOnClickListener(v -> {
            if (dismissedDelayed) {
                return;
            }
            if (newTimer != currentTimer) {
                dismissedDelayed = true;
                int time;
                int action;
                if (newTimer == 3) {
                    time = 31 * 24 * 60 * 60;
                    action = UndoView.ACTION_AUTO_DELETE_ON;
                } else if (newTimer == 2) {
                    time = 7 * 24 * 60 * 60;
                    action = UndoView.ACTION_AUTO_DELETE_ON;
                } else if (newTimer == 1) {
                    time = 24 * 60 * 60;
                    action = UndoView.ACTION_AUTO_DELETE_ON;
                } else {
                    time = 0;
                    action = UndoView.ACTION_AUTO_DELETE_OFF;
                }
                delegate.onAutoDeleteHistory(time, action);
            }
            if (dismissedDelayed) {
                AndroidUtilities.runOnUIThread(this::dismiss, 200);
            } else {
                dismiss();
            }
        });
        buttonContainer.addView(setTimerButton);

        updateTimerButton(false);
    }

    private void updateTimerButton(boolean animated) {
        if (currentTimer == newTimer && !autoDeleteOnly) {
            if (animated) {
                setTimerButton.animate().alpha(0.0f).setDuration(180).start();
            } else {
                setTimerButton.setVisibility(View.INVISIBLE);
                setTimerButton.setAlpha(0.0f);
            }
        } else {
            setTimerButton.setVisibility(View.VISIBLE);
            if (animated) {
                setTimerButton.animate().alpha(1.0f).setDuration(180).start();
            } else {
                setTimerButton.setAlpha(1.0f);
            }
        }
    }

    private void updateLayout() {
        View child = linearLayout.getChildAt(0);
        child.getLocationInWindow(location);
        int top = location[1] - AndroidUtilities.dp(autoDeleteOnly ? 6 : 19);
        int newOffset = Math.max(top, 0);
        if (scrollOffsetY != newOffset) {
            scrollOffsetY = newOffset;
            containerView.invalidate();
        }
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    public void setDelegate(ClearHistoryAlertDelegate clearHistoryAlertDelegate) {
        delegate = clearHistoryAlertDelegate;
    }
}
