package org.telegram.ui.Components;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

public class ChatNotificationsPopupWrapper {

    View backItem;
    ActionBarMenuSubItem soundToggle;
    ActionBarMenuSubItem muteUnmuteButton;
    ActionBarMenuSubItem muteForLastSelected;
    ActionBarMenuSubItem muteForLastSelected2;
    public ActionBarPopupWindow.ActionBarPopupWindowLayout windowLayout;
    int currentAccount;
    ActionBarPopupWindow popupWindow;
    Callback callback;
    long lastDismissTime;

    private final static String LAST_SELECTED_TIME_KEY_1 = "last_selected_mute_until_time";
    private final static String LAST_SELECTED_TIME_KEY_2 = "last_selected_mute_until_time2";
    private final boolean isProfile;
    private int muteForLastSelected2Time;
    private int muteForLastSelected1Time;

    public ChatNotificationsPopupWrapper(Context context, int currentAccount, PopupSwipeBackLayout swipeBackLayout, boolean createBackground, boolean isProfile, Callback callback, Theme.ResourcesProvider resourcesProvider) {
        this.currentAccount = currentAccount;
        this.callback = callback;
        this.isProfile = isProfile;
        windowLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, createBackground ? R.drawable.popup_fixed_alert : 0, resourcesProvider);
        windowLayout.setFitItems(true);

        if (swipeBackLayout != null) {
            backItem = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_arrow_back, LocaleController.getString("Back", R.string.Back), false, resourcesProvider);
            backItem.setOnClickListener(view -> {
                swipeBackLayout.closeForeground();
            });
        }


        soundToggle = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_tone_on, LocaleController.getString("SoundOn", R.string.SoundOn), false, resourcesProvider);
        soundToggle.setOnClickListener(view -> {
            dismiss();
            callback.toggleSound();
        });

        muteForLastSelected = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_mute_1h, LocaleController.getString("MuteFor1h", R.string.MuteFor1h), false, resourcesProvider);
        muteForLastSelected.setOnClickListener(view -> {
            dismiss();
            callback.muteFor(muteForLastSelected1Time);
        });

        muteForLastSelected2 = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_mute_1h, LocaleController.getString("MuteFor1h", R.string.MuteFor1h), false, resourcesProvider);
        muteForLastSelected2.setOnClickListener(view -> {
            dismiss();
            callback.muteFor(muteForLastSelected2Time);
        });

        ActionBarMenuSubItem item = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_mute_period, LocaleController.getString("MuteForPopup", R.string.MuteForPopup), false, resourcesProvider);
        item.setOnClickListener(view -> {
            dismiss();
            AlertsCreator.createMuteForPickerDialog(context, resourcesProvider, (notify, inSecond) -> {
                AndroidUtilities.runOnUIThread(() -> {
                    if (inSecond != 0) {
                        SharedPreferences sharedPreferences = MessagesController.getNotificationsSettings(currentAccount);
                        int time1 = sharedPreferences.getInt(LAST_SELECTED_TIME_KEY_1, 0);
                        int time2;
                        time2 = time1;
                        time1 = inSecond;
                        sharedPreferences.edit()
                                .putInt(LAST_SELECTED_TIME_KEY_1, time1)
                                .putInt(LAST_SELECTED_TIME_KEY_2, time2)
                                .apply();
                    }
                    callback.muteFor(inSecond);
                }, 16);
            });
        });

        item = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_customize, LocaleController.getString("NotificationsCustomize", R.string.NotificationsCustomize), false, resourcesProvider);
        item.setOnClickListener(view -> {
            dismiss();
            callback.showCustomize();
        });


        muteUnmuteButton = ActionBarMenuItem.addItem(windowLayout, 0, "", false, resourcesProvider);
        muteUnmuteButton.setOnClickListener(view -> {
            dismiss();
            AndroidUtilities.runOnUIThread(() -> {
                callback.toggleMute();
            });

        });
    }

    private void dismiss() {
        if (popupWindow != null) {
            popupWindow.dismiss();
            popupWindow.dismiss();
        }
        callback.dismiss();
        lastDismissTime = System.currentTimeMillis();
    }

    public void update(long dialogId) {
        if (System.currentTimeMillis() - lastDismissTime < 200) {
            AndroidUtilities.runOnUIThread(() -> {
                update(dialogId);
            });
            return;
        }
        boolean muted = MessagesController.getInstance(currentAccount).isDialogMuted(dialogId);

        int color;
        if (muted) {
            muteUnmuteButton.setTextAndIcon(LocaleController.getString("UnmuteNotifications", R.string.UnmuteNotifications), R.drawable.msg_unmute);
            color = Theme.getColor(Theme.key_wallet_greenText);
            soundToggle.setVisibility(View.GONE);
        } else {
            muteUnmuteButton.setTextAndIcon(LocaleController.getString("MuteNotifications", R.string.MuteNotifications), R.drawable.msg_mute);
            color = Theme.getColor(Theme.key_dialogTextRed);
            soundToggle.setVisibility(View.VISIBLE);
            boolean soundOn = MessagesController.getInstance(currentAccount).isDialogNotificationsSoundEnabled(dialogId);
            if (soundOn) {
                soundToggle.setTextAndIcon(LocaleController.getString("SoundOff", R.string.SoundOff), R.drawable.msg_tone_off);
            } else {
                soundToggle.setTextAndIcon(LocaleController.getString("SoundOn", R.string.SoundOn), R.drawable.msg_tone_on);
            }
        }

        int time1;
        int time2;
        if (muted) {
            time1 = 0;
            time2 = 0;
        } else {
            SharedPreferences sharedPreferences = MessagesController.getNotificationsSettings(currentAccount);
            time1 = sharedPreferences.getInt(LAST_SELECTED_TIME_KEY_1, 0);
            time2 = sharedPreferences.getInt(LAST_SELECTED_TIME_KEY_2, 0);
        }
        if (time1 != 0) {
            muteForLastSelected1Time = time1;
            muteForLastSelected.setVisibility(View.VISIBLE);
            muteForLastSelected.getImageView().setImageDrawable(TimerDrawable.getTtlIcon(time1));
            muteForLastSelected.setText(formatMuteForTime(time1));
        } else {
            muteForLastSelected.setVisibility(View.GONE);
        }

        if (time2 != 0) {
            muteForLastSelected2Time = time2;
            muteForLastSelected2.setVisibility(View.VISIBLE);
            muteForLastSelected2.getImageView().setImageDrawable(TimerDrawable.getTtlIcon(time2));
            muteForLastSelected2.setText(formatMuteForTime(time2));
        } else {
            muteForLastSelected2.setVisibility(View.GONE);
        }


        muteUnmuteButton.setColors(color, color);

    }

    private String formatMuteForTime(int time) {
        StringBuilder stringBuilder = new StringBuilder();
        int days = time / (60 * 60 * 24);
        time -= days * (60 * 60 * 24);
        int hours = time / (60 * 60);

        if (days != 0) {
            stringBuilder.append(days).append(LocaleController.getString("SecretChatTimerDays", R.string.SecretChatTimerDays));
        }
        if (hours != 0) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(" ");
            }
            stringBuilder.append(hours).append(LocaleController.getString("SecretChatTimerHours", R.string.SecretChatTimerHours));
        }
        return LocaleController.formatString("MuteForButton", R.string.MuteForButton, stringBuilder.toString());
    }

    public void showAsOptions(BaseFragment parentFragment, View anchorView, float touchedX, float touchedY) {
        if (parentFragment == null || parentFragment.getFragmentView() == null) {
            return;
        }
        popupWindow = new ActionBarPopupWindow(windowLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        popupWindow.setPauseNotifications(true);
        popupWindow.setDismissAnimationDuration(220);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setClippingEnabled(true);
        popupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        popupWindow.setFocusable(true);
        windowLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.getContentView().setFocusableInTouchMode(true);

        float x = touchedX, y = touchedY;
        View view = anchorView;
        while (view != parentFragment.getFragmentView()) {
            x += view.getX();
            y += view.getY();
            view = (View) view.getParent();
        }
        x -= windowLayout.getMeasuredWidth() / 2f;
        y -= windowLayout.getMeasuredHeight() / 2f;
        popupWindow.showAtLocation(parentFragment.getFragmentView(), 0, (int) x, (int) y);
        popupWindow.dimBehind();
        //  parentFragment.dimBehindView(true);
    }

    public interface Callback {
        default void dismiss() {}

        void toggleSound();

        void muteFor(int timeInSecond);

        void showCustomize();

        void toggleMute();
    }

}
