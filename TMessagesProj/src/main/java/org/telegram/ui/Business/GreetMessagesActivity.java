package org.telegram.ui.Business;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CrossfadeDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class GreetMessagesActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int done_button = 1;
    private CrossfadeDrawable doneButtonDrawable;
    private ActionBarMenuItem doneButton;

    private UniversalRecyclerView listView;
    private BusinessRecipientsHelper recipientsHelper;

    private final int[] daysOfInactivity = new int[] { 7, 14, 21, 28 };
    private final String[] daysOfInactivityTexts;

    public GreetMessagesActivity() {
        daysOfInactivityTexts = new String[daysOfInactivity.length];
        for (int i = 0; i < daysOfInactivity.length; ++i) {
            daysOfInactivityTexts[i] = LocaleController.formatPluralString("DaysSchedule", daysOfInactivity[i]);
        }
    }

    public static void preloadSticker(int currentAccount) {

    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.BusinessGreet));
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
        recipientsHelper.doNotExcludeNewChats();
        recipientsHelper.setValue(currentValue == null ? null : currentValue.recipients);

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        setValue();

        return fragmentView = contentView;
    }

    private boolean valueSet;
    private void setValue() {
        if (valueSet) return;

        final long selfId = getUserConfig().getClientUserId();
        TLRPC.UserFull userFull = getMessagesController().getUserFull(selfId);
        if (userFull == null) {
            getMessagesController().loadUserInfo(getUserConfig().getCurrentUser(), true, getClassGuid());
            return;
        }

        currentValue = userFull.business_greeting_message;

        enabled = currentValue != null;
        inactivityDays = currentValue != null ? currentValue.no_activity_days : 7;
        exclude = currentValue != null ? currentValue.recipients.exclude_selected : true;
        if (recipientsHelper != null) {
            recipientsHelper.setValue(currentValue == null ? null : currentValue.recipients);
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
            if (currentValue.no_activity_days != inactivityDays) {
                return true;
            }
            if (currentValue.recipients.exclude_selected != exclude) {
                return true;
            }
            if (recipientsHelper != null && recipientsHelper.hasChanges()) {
                return true;
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

        QuickRepliesController.QuickReply reply = QuickRepliesController.getInstance(currentAccount).findReply(QuickRepliesController.GREETING);
        if (enabled && reply == null) {
            BotWebViewVibrationEffect.APP_ERROR.vibrate();
            AndroidUtilities.shakeViewSpring(listView.findViewByItemId(BUTTON_CREATE), shiftDp = -shiftDp);
            return;
        }

        if (enabled && !recipientsHelper.validate(listView)) {
            return;
        }

        doneButtonDrawable.animateToProgress(1f);
        TLRPC.UserFull userFull = getMessagesController().getUserFull(getUserConfig().getClientUserId());
        TLRPC.TL_account_updateBusinessGreetingMessage req = new TLRPC.TL_account_updateBusinessGreetingMessage();
        if (enabled) {
            req.message = new TLRPC.TL_inputBusinessGreetingMessage();
            req.message.shortcut_id = reply.id;
            req.message.recipients = recipientsHelper.getInputValue();
            req.message.no_activity_days = inactivityDays;
            req.flags |= 1;

            if (userFull != null) {
                userFull.flags2 |= 4;
                userFull.business_greeting_message = new TLRPC.TL_businessGreetingMessage();
                userFull.business_greeting_message.shortcut_id = reply.id;
                userFull.business_greeting_message.recipients = recipientsHelper.getValue();
                userFull.business_greeting_message.no_activity_days = inactivityDays;
            }
        } else {
            if (userFull != null) {
                userFull.flags2 &=~ 4;
                userFull.business_greeting_message = null;
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
            builder.setMessage(LocaleController.getString(R.string.BusinessGreetUnsavedChanges));
            builder.setPositiveButton(LocaleController.getString(R.string.ApplyTheme), (dialogInterface, i) -> processDone());
            builder.setNegativeButton(LocaleController.getString(R.string.PassportDiscard), (dialog, which) -> finishFragment());
            showDialog(builder.create());
            return false;
        }
        return super.onBackPressed();
    }


    public TLRPC.TL_businessGreetingMessage currentValue;

    public boolean enabled;
    public boolean exclude;
    public int inactivityDays = 7;

    private final static int BUTTON_ENABLE = 1;
    private final static int BUTTON_CREATE = 2;
    private static final int RADIO_PRIVATE_CHATS = 3;
    private static final int RADIO_ALL_CHATS = 4;

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asTopView(getString(R.string.BusinessGreetInfo), "RestrictedEmoji", "👋"));
        items.add(UItem.asCheck(BUTTON_ENABLE, getString(R.string.BusinessGreetSend)).setChecked(enabled));
        items.add(UItem.asShadow(null));
        if (enabled) {
            QuickRepliesController.QuickReply reply = QuickRepliesController.getInstance(currentAccount).findReply(QuickRepliesController.GREETING);
            if (reply != null) {
                items.add(UItem.asLargeQuickReply(reply));
            } else {
                items.add(UItem.asButton(BUTTON_CREATE, R.drawable.msg2_chats_add, getString(R.string.BusinessGreetCreate)).accent());
            }
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader(getString(R.string.BusinessRecipients)));
            items.add(UItem.asRadio(RADIO_PRIVATE_CHATS, getString(R.string.BusinessChatsAllPrivateExcept)).setChecked(exclude));
            items.add(UItem.asRadio(RADIO_ALL_CHATS, getString(R.string.BusinessChatsOnlySelected)).setChecked(!exclude));
            items.add(UItem.asShadow(null));
            recipientsHelper.fillItems(items);
            items.add(UItem.asShadow(getString(R.string.BusinessGreetRecipientsInfo)));
            items.add(UItem.asHeader(getString(R.string.BusinessGreetPeriod)));
            int daysIndex = -1;
            for (int i = 0; i < daysOfInactivity.length; ++i) {
                if (daysOfInactivity[i] == inactivityDays) {
                    daysIndex = i;
                    break;
                }
            }
            items.add(UItem.asSlideView(daysOfInactivityTexts, daysIndex, this::chooseInactivity));
            items.add(UItem.asShadow(getString(R.string.BusinessGreetPeriodInfo)));
        }
    }

    private void chooseInactivity(int index) {
        inactivityDays = daysOfInactivity[index];
        checkDone(true);
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (recipientsHelper.onClick(item)) {
            return;
        }
        if (item.id == BUTTON_CREATE || item.viewType == UniversalAdapter.VIEW_TYPE_LARGE_QUICK_REPLY) {
            Bundle args = new Bundle();
            args.putLong("user_id", getUserConfig().getClientUserId());
            args.putInt("chatMode", ChatActivity.MODE_QUICK_REPLIES);
            args.putString("quick_reply", QuickRepliesController.GREETING);
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
