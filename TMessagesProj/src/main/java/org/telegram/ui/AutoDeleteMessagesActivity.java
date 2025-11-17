package org.telegram.ui;

import android.content.Context;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.StickerImageView;

import java.util.ArrayList;

public class AutoDeleteMessagesActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private final static int ONE_DAY = 60 * 24;
    private final static int ONE_WEEK = 60 * 24 * 7;
    private final static int ONE_MONTH = 60 * 24 * 31;

    RadioCellInternal offCell;
    RadioCellInternal afterOneDay;
    RadioCellInternal afterOneWeek;
    RadioCellInternal afterOneMonth;
    RadioCellInternal customTimeButton;
    LinearLayout checkBoxContainer;

    ArrayList<RadioCellInternal> arrayList = new ArrayList<>();

    public int startFromTtl = 0;

    @Override
    public boolean onFragmentCreate() {
        startFromTtl = getUserConfig().getGlobalTTl();
        if (startFromTtl < 0) {
            startFromTtl = 0;
        }
        getUserConfig().loadGlobalTTl();
        getNotificationCenter().addObserver(this, NotificationCenter.didUpdateGlobalAutoDeleteTimer);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.didUpdateGlobalAutoDeleteTimer);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.AutoDeleteMessages));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        ScrollView scrollView = new ScrollView(getContext());
        LinearLayout mainContainer = new LinearLayout(getContext());
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(mainContainer);
        frameLayout.addView(scrollView);


        FrameLayout stickerHeaderCell = new FrameLayout(context);
        StickerImageView backupImageView = new StickerImageView(context, currentAccount);
        backupImageView.setStickerNum(10);
        stickerHeaderCell.addView(backupImageView, LayoutHelper.createFrame(130, 130, Gravity.CENTER));

        mainContainer.addView(stickerHeaderCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 170));

        checkBoxContainer = new LinearLayout(getContext());
        checkBoxContainer.setOrientation(LinearLayout.VERTICAL);
        checkBoxContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        mainContainer.addView(checkBoxContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        HeaderCell headerCell = new HeaderCell(getContext());
        headerCell.setText(LocaleController.getString(R.string.MessageLifetime));
        checkBoxContainer.addView(headerCell);

        offCell = new RadioCellInternal(getContext());
        offCell.setText(LocaleController.getString(R.string.ShortMessageLifetimeForever), false, true);
        offCell.time = 0;
        checkBoxContainer.addView(offCell);

        afterOneDay = new RadioCellInternal(getContext());
        afterOneDay.setText(LocaleController.getString(R.string.AutoDeleteAfter1Day), false, true);
        afterOneDay.time = ONE_DAY;
        checkBoxContainer.addView(afterOneDay);

        afterOneWeek = new RadioCellInternal(getContext());
        afterOneWeek.setText(LocaleController.getString(R.string.AutoDeleteAfter1Week), false, true);
        afterOneWeek.time = ONE_WEEK;
        checkBoxContainer.addView(afterOneWeek);

        afterOneMonth = new RadioCellInternal(getContext());
        afterOneMonth.setText(LocaleController.getString(R.string.AutoDeleteAfter1Month), false, true);
        afterOneMonth.time = ONE_MONTH;
        checkBoxContainer.addView(afterOneMonth);

        customTimeButton = new RadioCellInternal(getContext());
        customTimeButton.setText(LocaleController.getString(R.string.SetCustomTime), false, false);
        customTimeButton.hideRadioButton();
        checkBoxContainer.addView(customTimeButton);

        arrayList.add(offCell);
        arrayList.add(afterOneDay);
        arrayList.add(afterOneWeek);
        arrayList.add(afterOneMonth);
        arrayList.add(customTimeButton);

        updateItems();

        TextInfoPrivacyCell textInfoPrivacyCell = new TextInfoPrivacyCell(context);
        CharSequence infoText = AndroidUtilities.replaceSingleTag(LocaleController.getString(R.string.GlobalAutoDeleteInfo), new Runnable() {
            @Override
            public void run() {
                UsersSelectActivity usersSelectActivity = new UsersSelectActivity(UsersSelectActivity.TYPE_AUTO_DELETE_EXISTING_CHATS);
                usersSelectActivity.setTtlPeriod(getSelectedTime());
                usersSelectActivity.setDelegate((ids, flags) -> {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!ids.isEmpty()) {
                            for (int i = 0; i < ids.size(); i++) {
                                getMessagesController().setDialogHistoryTTL(ids.get(i), getSelectedTime() * 60);
                            }
                            if (getSelectedTime() > 0) {
                                BulletinFactory.of(AutoDeleteMessagesActivity.this).createSimpleBulletin(R.raw.fire_on, AndroidUtilities.replaceTags(LocaleController.formatString("AutodeleteTimerEnabledForChats", R.string.AutodeleteTimerEnabledForChats,
                                        LocaleController.formatTTLString(getSelectedTime() * 60),
                                        LocaleController.formatPluralString("Chats", ids.size(), ids.size())
                                ))).show();
                            } else {
                                BulletinFactory.of(AutoDeleteMessagesActivity.this).createSimpleBulletin(R.raw.fire_off, LocaleController.formatString("AutodeleteTimerDisabledForChats", R.string.AutodeleteTimerDisabledForChats,
                                        LocaleController.formatPluralString("Chats", ids.size(), ids.size())
                                )).show();
                            }
                        }
                    }, 100);


                });
                presentFragment(usersSelectActivity);
            }
        });
        textInfoPrivacyCell.setText(infoText);
        mainContainer.addView(textInfoPrivacyCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        selectDate(startFromTtl, false);
        return fragmentView;
    }

    private void updateItems() {
        for (int i = 0; i < arrayList.size(); i++) {
            arrayList.get(i).setBackground(Theme.createSelectorWithBackgroundDrawable(Theme.getColor(Theme.key_windowBackgroundWhite), Theme.getColor(Theme.key_listSelector)));
            arrayList.get(i).setOnClickListener(v -> {
                if (v == customTimeButton) {
                    AlertsCreator.createAutoDeleteDatePickerDialog(getContext(), 1, null, new AlertsCreator.ScheduleDatePickerDelegate() {
                        @Override
                        public void didSelectDate(boolean notify, int scheduleDate, int scheduleRepeatPeriod) {
                            AndroidUtilities.runOnUIThread(() -> {
                                selectDate(scheduleDate, true);
                            }, 50);

                        }
                    });
                } else {
                    int time = ((RadioCellInternal)v).time;
                    int selctedTime = getSelectedTime();
                    if (selctedTime == 0 && time > 0) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                        builder.setTitle(LocaleController.getString(R.string.MessageLifetime));
                        builder.setMessage(LocaleController.formatString("AutoDeleteConfirmMessage", R.string.AutoDeleteConfirmMessage, LocaleController.formatTTLString(time * 60)));
                        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> {
                            dialog.dismiss();
                        });
                        builder.setPositiveButton(LocaleController.getString(R.string.Enable), (dialog, which) -> {
                            dialog.dismiss();
                            selectRadioButton(v, true);
                        });
                        builder.show();
                    } else {
                        selectRadioButton(v, true);
                    }
                }
            });
        }
    }

    private int getSelectedTime() {
        for (int i = 0; i < arrayList.size(); i++) {
            if (arrayList.get(i).isChecked()) {
                return arrayList.get(i).time;
            }
        }
        return startFromTtl;
    }

    private void selectDate(int scheduleDate, boolean showBulletin) {
        TransitionSet transition = new TransitionSet();
        ChangeBounds changeBounds = new ChangeBounds();
        changeBounds.setDuration(150);
        Fade in = new Fade(Fade.IN);
        in.setDuration(150);
        transition
                .addTransition(new Fade(Fade.OUT).setDuration(150))
                .addTransition(changeBounds)
                .addTransition(in);
        transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
        transition.setInterpolator(CubicBezierInterpolator.DEFAULT);
        TransitionManager.beginDelayedTransition(checkBoxContainer, transition);

        for (int i = 0; i < arrayList.size(); i++) {
            if (arrayList.get(i).time == scheduleDate) {
                selectRadioButton(arrayList.get(i), showBulletin);
                return;
            }
        }

        for (int i = 0; i < arrayList.size(); i++) {
            if (arrayList.get(i).custom) {
                checkBoxContainer.removeView(arrayList.get(i));
                arrayList.remove(i);
                i--;
            }
        }

        int position = arrayList.size();
        for (int i = 0; i < arrayList.size(); i++) {
            if (scheduleDate < arrayList.get(i).time) {
                position = i + 1;
                break;
            }
        }
        RadioCellInternal customTimeButton = new RadioCellInternal(getContext());
        customTimeButton.custom = true;
        customTimeButton.time = scheduleDate;

        customTimeButton.setText(LocaleController.formatString("AutoDeleteAfterShort", R.string.AutoDeleteAfterShort, LocaleController.formatTTLString(scheduleDate * 60)), false, true);
        arrayList.add(position, customTimeButton);
        checkBoxContainer.addView(customTimeButton, position);
        updateItems();
        selectRadioButton(customTimeButton, showBulletin);
    }

    private void selectRadioButton(View v, boolean showBulletin) {
        for (int i = 0; i < arrayList.size(); i++) {
            if (arrayList.get(i) == v) {
                arrayList.get(i).setChecked(true, fragmentBeginToShow);
            } else {
                arrayList.get(i).setChecked(false, fragmentBeginToShow);
            }
        }
        if (showBulletin) {
            int time = ((RadioCellInternal) v).time;
            if (time > 0) {
                String text = LocaleController.formatString("AutoDeleteGlobalTimerEnabled", R.string.AutoDeleteGlobalTimerEnabled, LocaleController.formatTTLString(time * 60));
                BulletinFactory.of(this).createSimpleBulletin(R.raw.fire_on, AndroidUtilities.replaceTags(text)).show();
            } else {
//                String text = LocaleController.formatString("AutoDeleteGlobalTimerDisabled", R.string.AutoDeleteGlobalTimerDisabled);
//                BulletinFactory.of(this).createSimpleBulletin(R.raw.fire_off, text).show();
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
//        int newTTl = getUserConfig().getGlobalTTl();
//        if (newTTl != startFromTtl) {
//            startFromTtl = newTTl;
//            selectDate(startFromTtl, false);
//        }
    }

    private class RadioCellInternal extends RadioCell {

        boolean custom;
        int time;

        public RadioCellInternal(Context context) {
            super(context);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        for (int i = 0; i < arrayList.size(); i++) {
            if (arrayList.get(i).isChecked()) {
                if (arrayList.get(i).time != startFromTtl) {
                    startFromTtl = arrayList.get(i).time;
                    TLRPC.TL_messages_setDefaultHistoryTTL setDefaultHistoryTTL = new TLRPC.TL_messages_setDefaultHistoryTTL();
                    setDefaultHistoryTTL.period = arrayList.get(i).time * 60;
                    getConnectionsManager().sendRequest(setDefaultHistoryTTL, new RequestDelegate() {
                        @Override
                        public void run(TLObject response, TLRPC.TL_error error) {
                        }
                    });
                    getUserConfig().setGlobalTtl(startFromTtl);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didUpdateGlobalAutoDeleteTimer);
                }
                break;
            }
        }
    }
}
