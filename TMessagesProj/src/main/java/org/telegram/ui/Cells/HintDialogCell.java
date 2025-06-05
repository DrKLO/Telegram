/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CounterView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumGradient;

public class HintDialogCell extends FrameLayout {

    private BackupImageView imageView;
    private TextView nameTextView;
    private AvatarDrawable avatarDrawable = new AvatarDrawable();
    private RectF rect = new RectF();
    private Theme.ResourcesProvider resourcesProvider;

    private int lastUnreadCount;
    private TLRPC.User currentUser;

    private long dialogId;
    private int currentAccount = UserConfig.selectedAccount;
    float showOnlineProgress;
    boolean wasDraw;

    CounterView counterView;
    CheckBox2 checkBox;
    private final boolean drawCheckbox;

    private boolean showPremiumBlocked;
    private final AnimatedFloat premiumBlockedT = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private boolean premiumBlocked;
    private final AnimatedFloat starsBlockedT = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private long starsPriceBlocked;

    public boolean isBlocked() {
        return premiumBlocked;
    }

    public HintDialogCell(Context context, boolean drawCheckbox, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.drawCheckbox = drawCheckbox;

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(27));
        addView(imageView, LayoutHelper.createFrame(54, 54, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 7, 0, 0));

        nameTextView = new TextView(context) {
            @Override
            public void setText(CharSequence text, BufferType type) {
                text = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), false);
                super.setText(text, type);
            }
        };
        NotificationCenter.listenEmojiLoading(nameTextView);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        nameTextView.setMaxLines(1);
        nameTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        nameTextView.setLines(1);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 6, 64, 6, 0));

        counterView = new CounterView(context, resourcesProvider);
        addView(counterView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 28, Gravity.TOP,0 ,4,0,0));
        counterView.setColors(Theme.key_chats_unreadCounterText, Theme.key_chats_unreadCounter);
        counterView.setGravity(Gravity.RIGHT);

        if (drawCheckbox) {
            checkBox = new CheckBox2(context, 21, resourcesProvider);
            checkBox.setColor(Theme.key_dialogRoundCheckBox, Theme.key_dialogBackground, Theme.key_dialogRoundCheckBoxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(4);
            checkBox.setProgressDelegate(progress -> {
                float scale = 1.0f - (1.0f - 0.857f) * checkBox.getProgress();
                imageView.setScaleX(scale);
                imageView.setScaleY(scale);
                invalidate();
            });
            addView(checkBox, LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 19, 42, 0, 0));
            checkBox.setChecked(false, false);
            setWillNotDraw(false);
        }
    }

    public void showPremiumBlocked() {
        if (showPremiumBlocked) return;
        showPremiumBlocked = true;
        NotificationCenter.getInstance(currentAccount).listen(this, NotificationCenter.userIsPremiumBlockedUpadted, args -> {
            updatePremiumBlocked(true);
        });
    }

    private void updatePremiumBlocked(boolean animated) {
        final TL_account.RequirementToContact r = showPremiumBlocked && currentUser != null ? MessagesController.getInstance(currentAccount).isUserContactBlocked(currentUser.id) : null;
        if (premiumBlocked != DialogObject.isPremiumBlocked(r) || starsPriceBlocked != DialogObject.getMessagesStarsPrice(r)) {
            premiumBlocked = DialogObject.isPremiumBlocked(r);
            starsPriceBlocked = DialogObject.getMessagesStarsPrice(r);
            if (!animated) {
                premiumBlockedT.set(premiumBlocked, true);
                starsBlockedT.set(starsPriceBlocked > 0, true);
            }
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(86), MeasureSpec.EXACTLY));
        counterView.counterDrawable.horizontalPadding = AndroidUtilities.dp(13);
    }

    public void update(int mask) {
        if ((mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
            if (currentUser != null) {
                currentUser = MessagesController.getInstance(currentAccount).getUser(currentUser.id);
                imageView.invalidate();
                invalidate();
            }
        }
        if (mask != 0 && (mask & MessagesController.UPDATE_MASK_READ_DIALOG_MESSAGE) == 0 && (mask & MessagesController.UPDATE_MASK_NEW_MESSAGE) == 0) {
            return;
        }
        TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(dialogId);
        if (dialog != null && dialog.unread_count != 0) {
            if (lastUnreadCount != dialog.unread_count) {
                lastUnreadCount = dialog.unread_count;
                counterView.setCount(lastUnreadCount, wasDraw);
            }
        } else {
            lastUnreadCount = 0;
            counterView.setCount(0, wasDraw);
        }
    }

    public void update() {
        if (DialogObject.isUserDialog(dialogId)) {
            currentUser = MessagesController.getInstance(currentAccount).getUser(dialogId);
            avatarDrawable.setInfo(currentAccount, currentUser);
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            avatarDrawable.setInfo(currentAccount, chat);
            currentUser = null;
        }
        updatePremiumBlocked(true);
    }

    public void setColors(int textColorKey, int backgroundColorKey) {
        nameTextView.setTextColor(Theme.getColor(textColorKey, resourcesProvider));
        this.backgroundColorKey = backgroundColorKey;
        checkBox.setColor(Theme.key_dialogRoundCheckBox, backgroundColorKey, Theme.key_dialogRoundCheckBoxCheck);
    }

    public void setDialog(long uid, boolean counter, CharSequence name) {
        if (dialogId != uid) {
            wasDraw = false;
            invalidate();
        }
        dialogId = uid;
        if (DialogObject.isUserDialog(uid)) {
            currentUser = MessagesController.getInstance(currentAccount).getUser(uid);
            if (name != null) {
                nameTextView.setText(name);
            } else if (currentUser != null) {
                nameTextView.setText(UserObject.getFirstName(currentUser));
            } else {
                nameTextView.setText("");
            }
            avatarDrawable.setInfo(currentAccount, currentUser);
            imageView.setForUserOrChat(currentUser, avatarDrawable);
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-uid);
            if (name != null) {
                nameTextView.setText(name);
            } else if (chat != null) {
                nameTextView.setText(chat.title);
            } else {
                nameTextView.setText("");
            }
            avatarDrawable.setInfo(currentAccount, chat);
            currentUser = null;
            imageView.setForUserOrChat(chat, avatarDrawable);
        }
        updatePremiumBlocked(false);
        if (counter) {
            update(0);
        }
    }

    private int backgroundColorKey = Theme.key_windowBackgroundWhite;

    private PremiumGradient.PremiumGradientTools premiumGradient;
    private Drawable lockDrawable;

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (child == imageView) {
            boolean showOnline = !premiumBlocked && currentUser != null && !currentUser.bot && (currentUser.status != null && currentUser.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime() || MessagesController.getInstance(currentAccount).onlinePrivacy.containsKey(currentUser.id));
            if (!wasDraw) {
                showOnlineProgress = showOnline ? 1f : 0f;
            }
            if (showOnline && showOnlineProgress != 1f) {
                showOnlineProgress += 16f / 150;
                if (showOnlineProgress > 1) {
                    showOnlineProgress = 1f;
                }
                invalidate();
            } else if (!showOnline && showOnlineProgress != 0) {
                showOnlineProgress -= 16f / 150;
                if (showOnlineProgress < 0) {
                    showOnlineProgress = 0;
                }
                invalidate();
            }

            final float lockT = premiumBlockedT.set(premiumBlocked);
            if (lockT > 0) {
                float top = child.getY() + child.getHeight() / 2f + dp(18);
                float left = child.getX() + child.getWidth() / 2f + dp(18);

                canvas.save();
                Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(backgroundColorKey, resourcesProvider));
                canvas.drawCircle(left, top, dp(10 + 1.33f) * lockT, Theme.dialogs_onlineCirclePaint);
                if (premiumGradient == null) {
                    premiumGradient = new PremiumGradient.PremiumGradientTools(Theme.key_premiumGradient1, Theme.key_premiumGradient2, -1, -1, -1, resourcesProvider);
                }
                premiumGradient.gradientMatrix((int) (left - dp(10)), (int) (top - dp(10)), (int) (left + dp(10)), (int) (top + dp(10)), 0, 0);
                canvas.drawCircle(left, top, dp(10) * lockT, premiumGradient.paint);
                if (lockDrawable == null) {
                    lockDrawable = getContext().getResources().getDrawable(R.drawable.msg_mini_lock2).mutate();
                    lockDrawable.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
                }
                lockDrawable.setBounds(
                        (int) (left - lockDrawable.getIntrinsicWidth() / 2f * .875f * lockT),
                        (int) (top  - lockDrawable.getIntrinsicHeight() / 2f * .875f * lockT),
                        (int) (left + lockDrawable.getIntrinsicWidth() / 2f * .875f * lockT),
                        (int) (top  + lockDrawable.getIntrinsicHeight() / 2f * .875f * lockT)
                );
                lockDrawable.setAlpha((int) (0xFF * lockT));
                lockDrawable.draw(canvas);
                canvas.restore();
            } else if (showOnlineProgress != 0) {
                int top = AndroidUtilities.dp(53);
                int left = AndroidUtilities.dp(59);
                canvas.save();
                canvas.scale(showOnlineProgress, showOnlineProgress, left, top);
                Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(backgroundColorKey));
                canvas.drawCircle(left, top, AndroidUtilities.dp(7), Theme.dialogs_onlineCirclePaint);
                Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_chats_onlineCircle));
                canvas.drawCircle(left, top, AndroidUtilities.dp(5), Theme.dialogs_onlineCirclePaint);
                canvas.restore();
            }
            wasDraw = true;
        }
        return result;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (drawCheckbox) {
            int cx = imageView.getLeft() + imageView.getMeasuredWidth() / 2;
            int cy = imageView.getTop() + imageView.getMeasuredHeight() / 2;
            Theme.checkboxSquare_checkPaint.setColor(Theme.getColor(Theme.key_dialogRoundCheckBox));
            Theme.checkboxSquare_checkPaint.setAlpha((int) (checkBox.getProgress() * 255));
            canvas.drawCircle(cx, cy, AndroidUtilities.dp(28), Theme.checkboxSquare_checkPaint);
        }
    }

    public void setChecked(boolean checked, boolean animated) {
        if (drawCheckbox) {
            checkBox.setChecked(checked, animated);
        }
    }

    public long getDialogId() {
        return dialogId;
    }
}
