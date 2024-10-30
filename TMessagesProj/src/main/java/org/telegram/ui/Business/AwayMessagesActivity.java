package org.telegram.ui.Business;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CrossfadeDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class AwayMessagesActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int done_button = 1;
    private CrossfadeDrawable doneButtonDrawable;
    private ActionBarMenuItem doneButton;

    private UniversalRecyclerView listView;
    private BusinessRecipientsHelper recipientsHelper;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.BusinessAway));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (onBackPressed()) {
                        finishFragment();
                    }
                } else if (id == done_button) {
                    processDone();
                }
            }
        });

        Drawable checkmark = context.getResources().getDrawable(R.drawable.ic_ab_done).mutate();
        checkmark.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.MULTIPLY));
        doneButtonDrawable = new CrossfadeDrawable(checkmark, new CircularProgressDrawable(Theme.getColor(Theme.key_actionBarDefaultIcon)));
        doneButton = actionBar.createMenu().addItemWithWidth(done_button, doneButtonDrawable, AndroidUtilities.dp(56), LocaleController.getString(R.string.Done));
        checkDone(false);

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        recipientsHelper = new BusinessRecipientsHelper(this, () -> {
            listView.adapter.update(true);
            checkDone(true);
        });
        recipientsHelper.setExclude(exclude);
        if (recipientsHelper != null) {
            recipientsHelper.setValue(currentValue == null ? null : currentValue.recipients);
        }

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        setValue();

        return fragmentView = contentView;
    }

    private boolean hasHours;
    private boolean valueSet;
    private void setValue() {
        if (valueSet) return;

        final long selfId = getUserConfig().getClientUserId();
        TLRPC.UserFull userFull = getMessagesController().getUserFull(selfId);
        if (userFull == null) {
            getMessagesController().loadUserInfo(getUserConfig().getCurrentUser(), true, getClassGuid());
            return;
        }

        currentValue = userFull.business_away_message;
        hasHours = userFull.business_work_hours != null;

        enabled = currentValue != null;
        exclude = currentValue != null ? currentValue.recipients.exclude_selected : true;
        offline_only = currentValue != null ? currentValue.offline_only : true;
        if (recipientsHelper != null) {
            recipientsHelper.setValue(currentValue == null ? null : currentValue.recipients);
        }
        if (currentValue != null && currentValue.schedule instanceof TLRPC.TL_businessAwayMessageScheduleCustom) {
            schedule = currentValueScheduleType = SCHEDULE_CUSTOM;
            scheduleCustomStart = currentScheduleCustomStart = ((TLRPC.TL_businessAwayMessageScheduleCustom) currentValue.schedule).start_date;
            scheduleCustomEnd =currentScheduleCustomEnd = ((TLRPC.TL_businessAwayMessageScheduleCustom) currentValue.schedule).end_date;
        } else {
            scheduleCustomStart = getConnectionsManager().getCurrentTime();
            scheduleCustomEnd = getConnectionsManager().getCurrentTime() + 60 * 60 * 24;
            if (currentValue != null && currentValue.schedule instanceof TLRPC.TL_businessAwayMessageScheduleAlways) {
                schedule = currentValueScheduleType = SCHEDULE_ALWAYS;
            } else if (currentValue != null && currentValue.schedule instanceof TLRPC.TL_businessAwayMessageScheduleOutsideWorkHours) {
                schedule = currentValueScheduleType = SCHEDULE_OUTSIDE_HOURS;
            } else {
                schedule = currentValueScheduleType = SCHEDULE_ALWAYS;
            }
        }

        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
        checkDone(true);
        valueSet = true;
    }

    public boolean hasChanges() {
        if (!valueSet) return false;
        if (enabled != (currentValue != null)) return true;
        if (enabled && currentValue != null) {
            if (currentValue.recipients.exclude_selected != exclude) {
                return true;
            }
            if (recipientsHelper != null && recipientsHelper.hasChanges()) {
                return true;
            }
            if (currentValueScheduleType != schedule) {
                return true;
            }
            if (currentValue.offline_only != offline_only) {
                return true;
            }
            if (schedule == SCHEDULE_CUSTOM) {
                if (currentScheduleCustomStart != scheduleCustomStart || currentScheduleCustomEnd != scheduleCustomEnd) {
                    return true;
                }
            }
        }
        return false;
    }

    private void checkDone(boolean animated) {
        if (doneButton == null) return;
        final boolean hasChanges = hasChanges();
        doneButton.setEnabled(hasChanges);
        if (animated) {
            doneButton.animate().alpha(hasChanges ? 1.0f : 0.0f).scaleX(hasChanges ? 1.0f : 0.0f).scaleY(hasChanges ? 1.0f : 0.0f).setDuration(180).start();
        } else {
            doneButton.setAlpha(hasChanges ? 1.0f : 0.0f);
            doneButton.setScaleX(hasChanges ? 1.0f : 0.0f);
            doneButton.setScaleY(hasChanges ? 1.0f : 0.0f);
        }
    }

    private int shiftDp = -4;
    private void processDone() {
        if (doneButtonDrawable.getProgress() > 0f) return;

        if (!hasChanges()) {
            finishFragment();
            return;
        }

        QuickRepliesController.QuickReply reply = QuickRepliesController.getInstance(currentAccount).findReply(QuickRepliesController.AWAY);
        if (enabled && reply == null) {
            BotWebViewVibrationEffect.APP_ERROR.vibrate();
            AndroidUtilities.shakeViewSpring(listView.findViewByItemId(BUTTON_CREATE), shiftDp = -shiftDp);
            listView.smoothScrollToPosition(listView.findPositionByItemId(BUTTON_CREATE));
            return;
        }

        if (enabled && !recipientsHelper.validate(listView)) {
            return;
        }

        doneButtonDrawable.animateToProgress(1f);
        TLRPC.UserFull userFull = getMessagesController().getUserFull(getUserConfig().getClientUserId());
        TLRPC.TL_account_updateBusinessAwayMessage req = new TLRPC.TL_account_updateBusinessAwayMessage();
        if (enabled) {
            req.message = new TLRPC.TL_inputBusinessAwayMessage();
            req.message.offline_only = offline_only;
            req.message.shortcut_id = reply.id;
            req.message.recipients = recipientsHelper.getInputValue();
            if (schedule == SCHEDULE_ALWAYS) {
                req.message.schedule = new TLRPC.TL_businessAwayMessageScheduleAlways();
            } else if (schedule == SCHEDULE_OUTSIDE_HOURS) {
                req.message.schedule = new TLRPC.TL_businessAwayMessageScheduleOutsideWorkHours();
            } else if (schedule == SCHEDULE_CUSTOM) {
                TLRPC.TL_businessAwayMessageScheduleCustom custom = new TLRPC.TL_businessAwayMessageScheduleCustom();
                custom.start_date = scheduleCustomStart;
                custom.end_date = scheduleCustomEnd;
                req.message.schedule = custom;
            }
            req.flags |= 1;

            if (userFull != null) {
                userFull.flags2 |= 8;
                userFull.business_away_message = new TLRPC.TL_businessAwayMessage();
                userFull.business_away_message.offline_only = offline_only;
                userFull.business_away_message.shortcut_id = reply.id;
                userFull.business_away_message.recipients = recipientsHelper.getValue();
                userFull.business_away_message.schedule = req.message.schedule;
            }
        } else {
            if (userFull != null) {
                userFull.flags2 &=~ 8;
                userFull.business_away_message = null;
            }
        }

        getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (err != null) {
                doneButtonDrawable.animateToProgress(0f);
                BulletinFactory.showError(err);
            } else if (res instanceof TLRPC.TL_boolFalse) {
                doneButtonDrawable.animateToProgress(0f);
                BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.UnknownError)).show();
            } else {
                finishFragment();
            }
        }));
        getMessagesStorage().updateUserInfo(userFull, false);
    }

    @Override
    public boolean onBackPressed() {
        if (hasChanges()) {
            if (!enabled) {
                processDone();
                return false;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString(R.string.UnsavedChanges));
            builder.setMessage(LocaleController.getString(R.string.BusinessAwayUnsavedChanges));
            builder.setPositiveButton(LocaleController.getString(R.string.ApplyTheme), (dialogInterface, i) -> processDone());
            builder.setNegativeButton(LocaleController.getString(R.string.PassportDiscard), (dialog, which) -> finishFragment());
            showDialog(builder.create());
            return false;
        }
        return super.onBackPressed();
    }

    public TLRPC.TL_businessAwayMessage currentValue;
    public int currentValueScheduleType;

    public boolean enabled;
    public boolean exclude;
    public boolean offline_only;
    public int schedule;

    private int currentScheduleCustomStart, currentScheduleCustomEnd;
    public int scheduleCustomStart, scheduleCustomEnd;

    private final static int BUTTON_ENABLE = 1;
    private final static int BUTTON_CREATE = 2;
    private final static int RADIO_SCHEDULE_ALWAYS = 3;
    private final static int RADIO_SCHEDULE_OUTSIDE_HOURS = 4;
    private final static int RADIO_SCHEDULE_CUSTOM = 5;
    private static final int RADIO_PRIVATE_CHATS = 6;
    private static final int RADIO_ALL_CHATS = 7;
    private static final int BUTTON_SCHEDULE_CUSTOM_START = 8;
    private static final int BUTTON_SCHEDULE_CUSTOM_END = 9;
    private static final int BUTTON_ONLY_OFFLINE = 10;

    private static final int SCHEDULE_ALWAYS = 0;
    private static final int SCHEDULE_OUTSIDE_HOURS = 1;
    private static final int SCHEDULE_CUSTOM = 2;

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asTopView(getString(R.string.BusinessAwayInfo), "RestrictedEmoji", "💤"));
        items.add(UItem.asCheck(BUTTON_ENABLE, getString(R.string.BusinessAwaySend)).setChecked(enabled));
        items.add(UItem.asShadow(null));
        if (enabled) {
            QuickRepliesController.QuickReply reply = QuickRepliesController.getInstance(currentAccount).findReply(QuickRepliesController.AWAY);
            if (reply != null) {
                items.add(UItem.asLargeQuickReply(reply));
            } else {
                items.add(UItem.asButton(BUTTON_CREATE, R.drawable.msg2_chats_add, getString(R.string.BusinessAwayCreate)).accent());
            }
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader(getString(R.string.BusinessAwaySchedule)));
            items.add(UItem.asRadio(RADIO_SCHEDULE_ALWAYS, getString(R.string.BusinessAwayScheduleAlways)).setChecked(schedule == SCHEDULE_ALWAYS));
            if (hasHours) {
                items.add(UItem.asRadio(RADIO_SCHEDULE_OUTSIDE_HOURS, getString(R.string.BusinessAwayScheduleOutsideHours)).setChecked(schedule == SCHEDULE_OUTSIDE_HOURS));
            }
            items.add(UItem.asRadio(RADIO_SCHEDULE_CUSTOM, getString(R.string.BusinessAwayScheduleCustom)).setChecked(schedule == SCHEDULE_CUSTOM));
            if (schedule == SCHEDULE_CUSTOM) {
                items.add(UItem.asShadow(null));
                items.add(UItem.asHeader(getString(R.string.BusinessAwaySchedule)));
                items.add(UItem.asButton(BUTTON_SCHEDULE_CUSTOM_START, getString(R.string.BusinessAwayScheduleCustomStart), LocaleController.formatShortDateTime(scheduleCustomStart)));
                items.add(UItem.asButton(BUTTON_SCHEDULE_CUSTOM_END, getString(R.string.BusinessAwayScheduleCustomEnd), LocaleController.formatShortDateTime(scheduleCustomEnd)));
            }
            items.add(UItem.asShadow(null));
            items.add(UItem.asCheck(BUTTON_ONLY_OFFLINE, LocaleController.getString(R.string.BusinessAwayOnlyOffline)).setChecked(offline_only));
            items.add(UItem.asShadow(LocaleController.getString(R.string.BusinessAwayOnlyOfflineInfo)));
            items.add(UItem.asHeader(getString(R.string.BusinessRecipients)));
            items.add(UItem.asRadio(RADIO_PRIVATE_CHATS, getString(R.string.BusinessChatsAllPrivateExcept)).setChecked(exclude));
            items.add(UItem.asRadio(RADIO_ALL_CHATS, getString(R.string.BusinessChatsOnlySelected)).setChecked(!exclude));
            items.add(UItem.asShadow(null));
            recipientsHelper.fillItems(items);
            items.add(UItem.asShadow(null));
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (recipientsHelper.onClick(item)) {
            return;
        }
        if (item.id == BUTTON_CREATE || item.viewType == UniversalAdapter.VIEW_TYPE_LARGE_QUICK_REPLY) {
            Bundle args = new Bundle();
            args.putLong("user_id", getUserConfig().getClientUserId());
            args.putInt("chatMode", ChatActivity.MODE_QUICK_REPLIES);
            args.putString("quick_reply", QuickRepliesController.AWAY);
            presentFragment(new ChatActivity(args));
        } else if (item.id == BUTTON_ENABLE) {
            enabled = !enabled;
            listView.adapter.update(true);
            checkDone(true);
        } else if (item.id == RADIO_PRIVATE_CHATS) {
            recipientsHelper.setExclude(exclude = true);
            listView.adapter.update(true);
            checkDone(true);
        } else if (item.id == RADIO_ALL_CHATS) {
            recipientsHelper.setExclude(exclude = false);
            listView.adapter.update(true);
            checkDone(true);
        } else if (item.id == RADIO_SCHEDULE_ALWAYS) {
            schedule = SCHEDULE_ALWAYS;
            listView.adapter.update(true);
            checkDone(true);
        } else if (item.id == RADIO_SCHEDULE_OUTSIDE_HOURS) {
            schedule = SCHEDULE_OUTSIDE_HOURS;
            listView.adapter.update(true);
            checkDone(true);
        } else if (item.id == RADIO_SCHEDULE_CUSTOM) {
            schedule = SCHEDULE_CUSTOM;
            listView.adapter.update(true);
            checkDone(true);
        } else if (item.id == BUTTON_SCHEDULE_CUSTOM_START) {
            AlertsCreator.createDatePickerDialog(getContext(), getString(R.string.BusinessAwayScheduleCustomStartTitle), getString(R.string.BusinessAwayScheduleCustomSetButton), scheduleCustomStart, (notify, date) -> {
                ((TextCell) view).setValue(LocaleController.formatShortDateTime(scheduleCustomStart = date), true);
                checkDone(true);
            });
        } else if (item.id == BUTTON_SCHEDULE_CUSTOM_END) {
            AlertsCreator.createDatePickerDialog(getContext(), getString(R.string.BusinessAwayScheduleCustomEndTitle), getString(R.string.BusinessAwayScheduleCustomSetButton), scheduleCustomEnd, (notify, date) -> {
                ((TextCell) view).setValue(LocaleController.formatShortDateTime(scheduleCustomEnd = date), true);
                checkDone(true);
            });
        } else if (item.id == BUTTON_ONLY_OFFLINE) {
            offline_only = !offline_only;
            ((TextCheckCell) view).setChecked(offline_only);
            checkDone(true);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.quickRepliesUpdated) {
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            checkDone(true);
        } else if (id == NotificationCenter.userInfoDidLoad) {
            setValue();
        }
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.quickRepliesUpdated);
        getNotificationCenter().addObserver(this, NotificationCenter.userInfoDidLoad);
        QuickRepliesController.getInstance(currentAccount).load();
        setValue();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.quickRepliesUpdated);
        getNotificationCenter().removeObserver(this, NotificationCenter.userInfoDidLoad);
        super.onFragmentDestroy();
    }

}
