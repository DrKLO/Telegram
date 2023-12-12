package org.telegram.ui.Components;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Path;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashSet;

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
    private final View gap;
    private final TextView topicsExceptionsTextView;

    public final static int TYPE_PREVIEW_MENU = 1;

    public int type;

    public ChatNotificationsPopupWrapper(Context context, int currentAccount, PopupSwipeBackLayout swipeBackLayout, boolean createBackground, boolean isProfile, Callback callback, Theme.ResourcesProvider resourcesProvider) {
        this.currentAccount = currentAccount;
        this.callback = callback;
        this.isProfile = isProfile;
        windowLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, createBackground ? R.drawable.popup_fixed_alert : 0, resourcesProvider) {
            Path path = new Path();

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                canvas.save();
                path.rewind();
                AndroidUtilities.rectTmp.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
                path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(6), AndroidUtilities.dp(6), Path.Direction.CW);
                canvas.clipPath(path);
                boolean draw = super.drawChild(canvas, child, drawingTime);
                canvas.restore();
                return draw;
            }
        };
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

        gap = new FrameLayout(context);
        gap.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuSeparator, resourcesProvider));
        windowLayout.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

        topicsExceptionsTextView = new TextView(context);
        topicsExceptionsTextView.setPadding(AndroidUtilities.dp(13), AndroidUtilities.dp(8), AndroidUtilities.dp(13), AndroidUtilities.dp(8));
        topicsExceptionsTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        topicsExceptionsTextView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider));

        gap.setTag(R.id.fit_width_tag, 1);
        topicsExceptionsTextView.setTag(R.id.fit_width_tag, 1);
        windowLayout.addView(topicsExceptionsTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        topicsExceptionsTextView.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector, resourcesProvider), 0,6));
        topicsExceptionsTextView.setOnClickListener(v -> {
            if (callback != null) {
                callback.openExceptions();
            }
            dismiss();
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

    public void update(long dialogId, int topicId, HashSet<Integer> topicExceptions) {
        if (System.currentTimeMillis() - lastDismissTime < 200) {
            //do on popup close
            AndroidUtilities.runOnUIThread(() -> {
                update(dialogId, topicId, topicExceptions);
            });
            return;
        }
        boolean muted = MessagesController.getInstance(currentAccount).isDialogMuted(dialogId, topicId);

        int color;
        if (muted) {
            muteUnmuteButton.setTextAndIcon(LocaleController.getString("UnmuteNotifications", R.string.UnmuteNotifications), R.drawable.msg_unmute);
            color = Theme.getColor(Theme.key_windowBackgroundWhiteGreenText2);
            soundToggle.setVisibility(View.GONE);
        } else {
            muteUnmuteButton.setTextAndIcon(LocaleController.getString("MuteNotifications", R.string.MuteNotifications), R.drawable.msg_mute);
            color = Theme.getColor(Theme.key_text_RedBold);
            soundToggle.setVisibility(View.VISIBLE);
            boolean soundOn = MessagesController.getInstance(currentAccount).isDialogNotificationsSoundEnabled(dialogId, topicId);
            if (soundOn) {
                soundToggle.setTextAndIcon(LocaleController.getString("SoundOff", R.string.SoundOff), R.drawable.msg_tone_off);
            } else {
                soundToggle.setTextAndIcon(LocaleController.getString("SoundOn", R.string.SoundOn), R.drawable.msg_tone_on);
            }
        }

        if (type == TYPE_PREVIEW_MENU) {
            backItem.setVisibility(View.GONE);
        }

        int time1;
        int time2;
        if (muted || type == TYPE_PREVIEW_MENU) {
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
        muteUnmuteButton.setSelectorColor(Theme.multAlpha(color, .1f));

        if (topicExceptions == null || topicExceptions.isEmpty()) {
            gap.setVisibility(View.GONE);
            topicsExceptionsTextView.setVisibility(View.GONE);
        } else {
            gap.setVisibility(View.VISIBLE);
            topicsExceptionsTextView.setVisibility(View.VISIBLE);
            topicsExceptionsTextView.setText(AndroidUtilities.replaceSingleTag(
                    LocaleController.formatPluralString("TopicNotificationsExceptions", topicExceptions.size()),
                    Theme.key_windowBackgroundWhiteBlueText,
                    AndroidUtilities.REPLACING_TAG_TYPE_BOLD,
                    null
            ));
        }
    }

    private String formatMuteForTime(int time) {
        StringBuilder stringBuilder = new StringBuilder();
        int days = time / (60 * 60 * 24);
        time -= days * (60 * 60 * 24);
        int hours = time / (60 * 60);
        time -= hours * (60 * 60);
        int minutes = time / 60;

        if (days != 0) {
            stringBuilder.append(days).append(LocaleController.getString("SecretChatTimerDays", R.string.SecretChatTimerDays));
        }
        if (hours != 0) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(" ");
            }
            stringBuilder.append(hours).append(LocaleController.getString("SecretChatTimerHours", R.string.SecretChatTimerHours));
        }
        if (minutes != 0) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(" ");
            }
            stringBuilder.append(minutes).append(LocaleController.getString("SecretChatTimerMinutes", R.string.SecretChatTimerMinutes));
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
            if (view.getParent() == null) {
                return;
            }
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

        default void openExceptions() {

        }
    }

}
