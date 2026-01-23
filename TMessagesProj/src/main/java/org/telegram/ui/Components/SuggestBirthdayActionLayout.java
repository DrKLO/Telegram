package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.LaunchActivity;

public class SuggestBirthdayActionLayout {

    private final int currentAccount;
    private final View view;
    private final Theme.ResourcesProvider resourcesProvider;

//    private final AnimatedEmojiDrawable sticker;
    private final RLottieDrawable sticker;

    private TL_account.TL_birthday birthday;
    private Text text;

    private Text[] titles;
    private Text[] values;

    private boolean hasButton;
    private Text button;
    private final RectF buttonRect = new RectF();
    private final Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ButtonBounce bounce;

    public SuggestBirthdayActionLayout(int currentAccount, ChatActionCell view, Theme.ResourcesProvider resourcesProvider) {
        this.currentAccount = currentAccount;
        this.view = view;
        this.resourcesProvider = resourcesProvider;

        sticker = new RLottieDrawable(R.raw.cake, "cake", dp(66), dp(66), true, null);
        sticker.restart();
//        sticker = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES_LARGE, 5370999492914976897L);
        bounce = new ButtonBounce(view);
    }

    public void set(MessageObject messageObject) {
        final TLRPC.TL_messageActionSuggestBirthday action = (TLRPC.TL_messageActionSuggestBirthday) messageObject.messageOwner.action;

        birthday = action.birthday;
        text = new Text(TextUtils.concat(messageObject.messageText, ":"), 13)
            .multiline(6)
            .align(Layout.Alignment.ALIGN_CENTER)
            .setMaxWidth(width() - dp(32));

        final int count = (action.birthday.flags & 1) != 0 ? 3 : 2;
        titles = new Text[count];
        values = new Text[count];

        titles[0] = new Text(getString(R.string.DateDay), 11);
        values[0] = new Text("" + action.birthday.day, 11, AndroidUtilities.bold());

        titles[1] = new Text(getString(R.string.DateMonth), 11);
        values[1] = new Text("" + getMonthName(action.birthday.month - 1), 11, AndroidUtilities.bold());

        if ((action.birthday.flags & 1) != 0) {
            titles[2] = new Text(getString(R.string.DateYear), 11);
            values[2] = new Text("" + action.birthday.year, 11, AndroidUtilities.bold());
        }

        hasButton = !messageObject.isOutOwner();
        final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        buttonPaint.setColor(isDark ? Theme.multAlpha(Color.WHITE, 0.12f) : Theme.multAlpha(Color.BLACK, 0.12f));
        button = new Text(getString(R.string.SuggestedDateOfBirthView), 14, AndroidUtilities.bold());
    }

    private final String getMonthName(int month) {
        final int[] months = new int[] {
            R.string.January, R.string.February,
            R.string.March, R.string.April, R.string.May,
            R.string.June, R.string.July, R.string.August,
            R.string.September, R.string.October, R.string.November,
            R.string.December
        };
        if (month < 0 || month >= months.length)
            return "" + month;
        return getString(months[month]);
    }

    public void draw(Canvas canvas) {
        final int stickerSize = dp(66);

        final int ox = (view.getWidth() - stickerSize) / 2;
        sticker.setBounds(ox, dp(4 + 9), ox + stickerSize, dp(4 + 9) + stickerSize);
        sticker.draw(canvas);

        text.draw(canvas, (view.getWidth() - text.getWidth()) / 2, dp(4 + 15) + stickerSize, 0xFFFFFFFF, 1.0f);

        int y = (int) (dp(4 + 15) + stickerSize + text.getHeight() + dp(11 + 6));

        int fullWidth = 0;
        for (int i = 0; i < titles.length; ++i) {
            fullWidth += dp(9) + Math.max(titles[i].getWidth(), values[i].getWidth()) + dp(9);
        }

        int x = (view.getWidth() - fullWidth) / 2;
        for (int i = 0; i < titles.length; ++i) {
            final float w = dp(9) + Math.max(titles[i].getWidth(), values[i].getWidth()) + dp(9);
            final float cx = x + w / 2f;
            x += w;

            titles[i].draw(canvas, cx - titles[i].getWidth() / 2f, y, 0xFFFFFFFF, 0.75f);
            values[i].draw(canvas, cx - values[i].getWidth() / 2f, y + dp(16), 0xFFFFFFFF, 1.0f);
        }

        if (hasButton) {
            y += dp(38);
            canvas.save();
            final float buttonWidth = button.getWidth() + dp(13 + 13);
            final float buttonHeight = dp(30);
            buttonRect.set(
                (view.getWidth() - buttonWidth) / 2f,
                y,
                (view.getWidth() + buttonWidth) / 2f,
                y + buttonHeight
            );
            final float s = bounce.getScale(0.1f);
            canvas.scale(s, s, buttonRect.centerX(), buttonRect.centerY());
            canvas.drawRoundRect(buttonRect, buttonHeight / 2f, buttonHeight / 2f, buttonPaint);
            button.draw(canvas, buttonRect.left + dp(13), buttonRect.centerY(), 0xFFFFFFFF, 1.0f);
            canvas.restore();
        }
    }

    public int width() {
        return dp(174);
    }

    public int height() {
        return (
            dp(4 + 9 + 66 + 6 + 11 + 6 + 38) +
            (int) text.getHeight() +
            (hasButton ? dp(40) : 0)
        );
    }

    public void attach() {
        sticker.setMasterParent(view);
    }

    public void detach() {
        sticker.setMasterParent(null);
    }

    public boolean onTouchEvent(MotionEvent e) {
        final boolean hit = buttonRect.contains(e.getX(), e.getY());

        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            bounce.setPressed(hit);
        } else if (e.getAction() == MotionEvent.ACTION_MOVE) {

        } else if (e.getAction() == MotionEvent.ACTION_UP) {
            if (bounce.isPressed()) {
                open();
            }
            bounce.setPressed(false);
        } else if (e.getAction() == MotionEvent.ACTION_CANCEL) {
            bounce.setPressed(false);
        }
        return bounce.isPressed();
    }

    public void open() {
        AlertsCreator.createBirthdayPickerDialog(
            view.getContext(),
            getString(R.string.DateOfBirth),
            getString(R.string.DateOfBirthAddToProfile),
            birthday,
            birthday -> {
                final TL_account.updateBirthday req = new TL_account.updateBirthday();
                req.flags |= 1;
                req.birthday = birthday;
                TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(UserConfig.getInstance(currentAccount).getClientUserId());
                final TL_account.TL_birthday oldBirthday = userFull != null ? userFull.birthday : null;
                if (userFull != null) {
                    userFull.flags2 |= 32;
                    userFull.birthday = birthday;
                    MessagesStorage.getInstance(currentAccount).updateUserInfo(userFull, false);
                }
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                    if (fragment == null) return;
                    if (res instanceof TLRPC.TL_boolTrue) {
                        BulletinFactory.of(fragment)
                            .createSimpleBulletin(R.raw.gift, getString(R.string.PrivacyBirthdaySetDone), getString(R.string.PrivacyBirthdaySetDoneInfo))
                            .setDuration(Bulletin.DURATION_PROLONG)
                            .show();
                    } else {
                        if (userFull != null) {
                            if (oldBirthday == null) {
                                userFull.flags2 &=~ 32;
                            } else {
                                userFull.flags2 |= 32;
                            }
                            userFull.birthday = oldBirthday;
                            MessagesStorage.getInstance(currentAccount).updateUserInfo(userFull, false);
                        }
                        if (err != null && err.text != null && err.text.startsWith("FLOOD_WAIT_")) {
                            new AlertDialog.Builder(view.getContext())
                                .setTitle(getString(R.string.PrivacyBirthdayTooOftenTitle))
                                .setMessage(getString(R.string.PrivacyBirthdayTooOftenMessage))
                                .setPositiveButton(getString(R.string.OK), null)
                                .show();
                        } else {
                            BulletinFactory.of(fragment)
                                .createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.UnknownError))
                                .show();
                        }
                    }
                }), ConnectionsManager.RequestFlagDoNotWaitFloodWait);
                MessagesController.getInstance(currentAccount).invalidateContentSettings();
                MessagesController.getInstance(currentAccount).removeSuggestion(0, "BIRTHDAY_SETUP");
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.newSuggestionsAvailable);
            },
            null,
            true,
            resourcesProvider
        ).show();
    }

}
