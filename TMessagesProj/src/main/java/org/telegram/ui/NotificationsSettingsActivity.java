/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextColorCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Map;

public class NotificationsSettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public static class NotificationException {
        public int muteUntil;
        public boolean hasCustom;
        public int notify;
        public long did;
    }

    private RecyclerListView listView;
    private boolean reseting = false;
    private ListAdapter adapter;
    private ArrayList<NotificationException> exceptionUsers = null;
    private ArrayList<NotificationException> exceptionChats = null;

    private int notificationsServiceRow;
    private int notificationsServiceConnectionRow;
    private int messageSectionRow;
    private int messageAlertRow;
    private int messagePreviewRow;
    private int messageVibrateRow;
    private int messageSoundRow;
    private int messageLedRow;
    private int messagePopupNotificationRow;
    private int messagePriorityRow;
    private int messageExceptionsRow;
    private int groupSectionRow2;
    private int groupSectionRow;
    private int groupAlertRow;
    private int groupPreviewRow;
    private int groupVibrateRow;
    private int groupSoundRow;
    private int groupLedRow;
    private int groupPopupNotificationRow;
    private int groupPriorityRow;
    private int groupExceptionsRow;
    private int inappSectionRow2;
    private int inappSectionRow;
    private int inappSoundRow;
    private int inappVibrateRow;
    private int inappPreviewRow;
    private int inchatSoundRow;
    private int inappPriorityRow;
    private int callsSectionRow2;
    private int callsSectionRow;
    private int callsVibrateRow;
    private int callsRingtoneRow;
    private int eventsSectionRow2;
    private int eventsSectionRow;
    private int contactJoinedRow;
    private int pinnedMessageRow;
    private int otherSectionRow2;
    private int otherSectionRow;
    private int badgeNumberRow;
    private int androidAutoAlertRow;
    private int repeatRow;
    private int resetSectionRow2;
    private int resetSectionRow;
    private int resetNotificationsRow;
    private int rowCount = 0;

    @Override
    public boolean onFragmentCreate() {
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            ArrayList<NotificationException> usersResult = new ArrayList<>();
            ArrayList<NotificationException> chatsResult = new ArrayList<>();
            LongSparseArray<NotificationException> waitingForLoadExceptions = new LongSparseArray<>();

            ArrayList<Integer> usersToLoad = new ArrayList<>();
            ArrayList<Integer> chatsToLoad = new ArrayList<>();
            ArrayList<Integer> encryptedChatsToLoad = new ArrayList<>();

            ArrayList<TLRPC.User> users = new ArrayList<>();
            ArrayList<TLRPC.Chat> chats = new ArrayList<>();
            ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
            int selfId = UserConfig.getInstance(currentAccount).clientUserId;

            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
            Map<String, ?> values = preferences.getAll();
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("notify2_")) {
                    key = key.replace("notify2_", "");

                    long did = Utilities.parseLong(key);
                    if (did != 0 && did != selfId) {
                        NotificationException exception = new NotificationException();
                        exception.did = did;
                        exception.hasCustom = preferences.getBoolean("custom_" + did, false);
                        exception.notify = (Integer) entry.getValue();
                        if (exception.notify != 0) {
                            Integer time = (Integer) values.get("notifyuntil_" + key);
                            if (time != null) {
                                exception.muteUntil = time;
                            }
                        }

                        int lower_id = (int) did;
                        int high_id = (int) (did << 32);
                        if (lower_id != 0) {
                            if (lower_id > 0) {
                                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(lower_id);
                                if (user == null) {
                                    usersToLoad.add(lower_id);
                                    waitingForLoadExceptions.put(did, exception);
                                }
                                usersResult.add(exception);
                            } else {
                                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lower_id);
                                if (chat == null) {
                                    chatsToLoad.add(-lower_id);
                                    waitingForLoadExceptions.put(did, exception);
                                } else if (chat.left || chat.kicked || chat.migrated_to != null) {
                                    continue;
                                }
                                chatsResult.add(exception);
                            }
                        } else if (high_id != 0) {
                            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(high_id);
                            if (encryptedChat == null) {
                                encryptedChatsToLoad.add(high_id);
                                waitingForLoadExceptions.put(did, exception);
                            } else {
                                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
                                if (user == null) {
                                    usersToLoad.add(encryptedChat.user_id);
                                    waitingForLoadExceptions.put(encryptedChat.user_id, exception);
                                }
                            }
                            usersResult.add(exception);
                        }
                    }
                }
            }
            if (waitingForLoadExceptions.size() != 0) {
                try {
                    if (!encryptedChatsToLoad.isEmpty()) {
                        MessagesStorage.getInstance(currentAccount).getEncryptedChatsInternal(TextUtils.join(",", encryptedChatsToLoad), encryptedChats, usersToLoad);
                    }
                    if (!usersToLoad.isEmpty()) {
                        MessagesStorage.getInstance(currentAccount).getUsersInternal(TextUtils.join(",", usersToLoad), users);
                    }
                    if (!chatsToLoad.isEmpty()) {
                        MessagesStorage.getInstance(currentAccount).getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                for (int a = 0, size = chats.size(); a < size; a++) {
                    TLRPC.Chat chat = chats.get(a);
                    if (chat.left || chat.kicked || chat.migrated_to != null) {
                        continue;
                    }
                    waitingForLoadExceptions.remove(-chat.id);
                }
                for (int a = 0, size = users.size(); a < size; a++) {
                    TLRPC.User user = users.get(a);
                    waitingForLoadExceptions.remove(user.id);
                }
                for (int a = 0, size = encryptedChats.size(); a < size; a++) {
                    TLRPC.EncryptedChat encryptedChat = encryptedChats.get(a);
                    waitingForLoadExceptions.remove(((long) encryptedChat.id) << 32);
                }
                for (int a = 0, size = waitingForLoadExceptions.size(); a < size; a++) {
                    long did = waitingForLoadExceptions.keyAt(a);
                    if ((int) did < 0) {
                        chatsResult.remove(waitingForLoadExceptions.valueAt(a));
                    } else {
                        usersResult.remove(waitingForLoadExceptions.valueAt(a));
                    }
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                MessagesController.getInstance(currentAccount).putUsers(users, true);
                MessagesController.getInstance(currentAccount).putChats(chats, true);
                MessagesController.getInstance(currentAccount).putEncryptedChats(encryptedChats, true);
                exceptionUsers = usersResult;
                exceptionChats = chatsResult;
                adapter.notifyItemChanged(messageExceptionsRow);
                adapter.notifyItemChanged(groupExceptionsRow);
            });
        });


        messageSectionRow = rowCount++;
        messageAlertRow = rowCount++;
        messagePreviewRow = rowCount++;
        messageLedRow = rowCount++;
        messageVibrateRow = rowCount++;
        messagePopupNotificationRow = rowCount++;
        messageSoundRow = rowCount++;
        if (Build.VERSION.SDK_INT >= 21) {
            messagePriorityRow = rowCount++;
        } else {
            messagePriorityRow = -1;
        }
        messageExceptionsRow = rowCount++;
        groupSectionRow2 = rowCount++;
        groupSectionRow = rowCount++;
        groupAlertRow = rowCount++;
        groupPreviewRow = rowCount++;
        groupLedRow = rowCount++;
        groupVibrateRow = rowCount++;
        groupPopupNotificationRow = rowCount++;
        groupSoundRow = rowCount++;
        if (Build.VERSION.SDK_INT >= 21) {
            groupPriorityRow = rowCount++;
        } else {
            groupPriorityRow = -1;
        }
        groupExceptionsRow = rowCount++;
        inappSectionRow2 = rowCount++;
        inappSectionRow = rowCount++;
        inappSoundRow = rowCount++;
        inappVibrateRow = rowCount++;
        inappPreviewRow = rowCount++;
        inchatSoundRow = rowCount++;
        if (Build.VERSION.SDK_INT >= 21) {
            inappPriorityRow = rowCount++;
        } else {
            inappPriorityRow = -1;
        }
        callsSectionRow2 = rowCount++;
        callsSectionRow = rowCount++;
        callsVibrateRow = rowCount++;
        callsRingtoneRow = rowCount++;
        eventsSectionRow2 = rowCount++;
        eventsSectionRow = rowCount++;
        contactJoinedRow = rowCount++;
        pinnedMessageRow = rowCount++;
        otherSectionRow2 = rowCount++;
        otherSectionRow = rowCount++;
        notificationsServiceRow = rowCount++;
        notificationsServiceConnectionRow = rowCount++;
        badgeNumberRow = rowCount++;
        androidAutoAlertRow = -1;
        repeatRow = rowCount++;
        resetSectionRow2 = rowCount++;
        resetSectionRow = rowCount++;
        resetNotificationsRow = rowCount++;

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.notificationsSettingsUpdated);

        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.notificationsSettingsUpdated);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("NotificationsAndSounds", R.string.NotificationsAndSounds));
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

        listView = new RecyclerListView(context);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        listView.setVerticalScrollBarEnabled(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position) -> {
            boolean enabled = false;
            if (getParentActivity() == null) {
                return;
            }
            if (position == messageAlertRow || position == groupAlertRow) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                if (position == messageAlertRow) {
                    enabled = preferences.getBoolean("EnableAll", true);
                    editor.putBoolean("EnableAll", !enabled);
                } else if (position == groupAlertRow) {
                    enabled = preferences.getBoolean("EnableGroup", true);
                    editor.putBoolean("EnableGroup", !enabled);
                }
                editor.commit();
                updateServerNotificationsSettings(position == groupAlertRow);
            } else if (position == messagePreviewRow || position == groupPreviewRow) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                if (position == messagePreviewRow) {
                    enabled = preferences.getBoolean("EnablePreviewAll", true);
                    editor.putBoolean("EnablePreviewAll", !enabled);
                } else if (position == groupPreviewRow) {
                    enabled = preferences.getBoolean("EnablePreviewGroup", true);
                    editor.putBoolean("EnablePreviewGroup", !enabled);
                }
                editor.commit();
                updateServerNotificationsSettings(position == groupPreviewRow);
            } else if (position == messageSoundRow || position == groupSoundRow || position == callsRingtoneRow) {
                try {
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    Intent tmpIntent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                    tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, position == callsRingtoneRow ? RingtoneManager.TYPE_RINGTONE : RingtoneManager.TYPE_NOTIFICATION);
                    tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                    tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(position == callsRingtoneRow ? RingtoneManager.TYPE_RINGTONE : RingtoneManager.TYPE_NOTIFICATION));
                    Uri currentSound = null;

                    String defaultPath = null;
                    Uri defaultUri = position == callsRingtoneRow ? Settings.System.DEFAULT_RINGTONE_URI : Settings.System.DEFAULT_NOTIFICATION_URI;
                    if (defaultUri != null) {
                        defaultPath = defaultUri.getPath();
                    }

                    if (position == messageSoundRow) {
                        String path = preferences.getString("GlobalSoundPath", defaultPath);
                        if (path != null && !path.equals("NoSound")) {
                            if (path.equals(defaultPath)) {
                                currentSound = defaultUri;
                            } else {
                                currentSound = Uri.parse(path);
                            }
                        }
                    } else if (position == groupSoundRow) {
                        String path = preferences.getString("GroupSoundPath", defaultPath);
                        if (path != null && !path.equals("NoSound")) {
                            if (path.equals(defaultPath)) {
                                currentSound = defaultUri;
                            } else {
                                currentSound = Uri.parse(path);
                            }
                        }
                    } else if (position == callsRingtoneRow) {
                        String path = preferences.getString("CallsRingtonfePath", defaultPath);
                        if (path != null && !path.equals("NoSound")) {
                            if (path.equals(defaultPath)) {
                                currentSound = defaultUri;
                            } else {
                                currentSound = Uri.parse(path);
                            }
                        }
                    }
                    tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentSound);
                    startActivityForResult(tmpIntent, position);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else if (position == resetNotificationsRow) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setMessage(LocaleController.getString("ResetNotificationsAlert", R.string.ResetNotificationsAlert));
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setPositiveButton(LocaleController.getString("Reset", R.string.Reset), (dialogInterface, i) -> {
                    if (reseting) {
                        return;
                    }
                    reseting = true;
                    TLRPC.TL_account_resetNotifySettings req = new TLRPC.TL_account_resetNotifySettings();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        MessagesController.getInstance(currentAccount).enableJoined = true;
                        reseting = false;
                        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.clear();
                        editor.commit();
                        exceptionChats.clear();
                        exceptionUsers.clear();
                        adapter.notifyDataSetChanged();
                        if (getParentActivity() != null) {
                            Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("ResetNotificationsText", R.string.ResetNotificationsText), Toast.LENGTH_SHORT);
                            toast.show();
                        }
                    }));
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            } else if (position == inappSoundRow) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                enabled = preferences.getBoolean("EnableInAppSounds", true);
                editor.putBoolean("EnableInAppSounds", !enabled);
                editor.commit();
            } else if (position == inappVibrateRow) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                enabled = preferences.getBoolean("EnableInAppVibrate", true);
                editor.putBoolean("EnableInAppVibrate", !enabled);
                editor.commit();
            } else if (position == inappPreviewRow) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                enabled = preferences.getBoolean("EnableInAppPreview", true);
                editor.putBoolean("EnableInAppPreview", !enabled);
                editor.commit();
            } else if (position == inchatSoundRow) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                enabled = preferences.getBoolean("EnableInChatSound", true);
                editor.putBoolean("EnableInChatSound", !enabled);
                editor.commit();
                NotificationsController.getInstance(currentAccount).setInChatSoundEnabled(!enabled);
            } else if (position == inappPriorityRow) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                enabled = preferences.getBoolean("EnableInAppPriority", false);
                editor.putBoolean("EnableInAppPriority", !enabled);
                editor.commit();
            } else if (position == contactJoinedRow) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                enabled = preferences.getBoolean("EnableContactJoined", true);
                MessagesController.getInstance(currentAccount).enableJoined = !enabled;
                editor.putBoolean("EnableContactJoined", !enabled);
                editor.commit();
            } else if (position == pinnedMessageRow) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                enabled = preferences.getBoolean("PinnedMessages", true);
                editor.putBoolean("PinnedMessages", !enabled);
                editor.commit();
            } else if (position == androidAutoAlertRow) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                enabled = preferences.getBoolean("EnableAutoNotifications", false);
                editor.putBoolean("EnableAutoNotifications", !enabled);
                editor.commit();
            } else if (position == badgeNumberRow) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                enabled = NotificationsController.getInstance(currentAccount).showBadgeNumber;
                NotificationsController.getInstance(currentAccount).showBadgeNumber = !enabled;
                editor.putBoolean("badgeNumber", NotificationsController.getInstance(currentAccount).showBadgeNumber);
                editor.commit();
                NotificationsController.getInstance(currentAccount).setBadgeEnabled(!enabled);
            } else if (position == notificationsServiceConnectionRow) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                enabled = preferences.getBoolean("pushConnection", true);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("pushConnection", !enabled);
                editor.commit();
                if (!enabled) {
                    ConnectionsManager.getInstance(currentAccount).setPushConnectionEnabled(true);
                } else {
                    ConnectionsManager.getInstance(currentAccount).setPushConnectionEnabled(false);
                }
            } else if (position == notificationsServiceRow) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                enabled = preferences.getBoolean("pushService", true);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("pushService", !enabled);
                editor.commit();
                if (!enabled) {
                    ApplicationLoader.startPushService();
                } else {
                    ApplicationLoader.stopPushService();
                }
            } else if (position == messageLedRow || position == groupLedRow) {
                if (getParentActivity() == null) {
                    return;
                }
                showDialog(AlertsCreator.createColorSelectDialog(getParentActivity(), 0, position == groupLedRow, position == messageLedRow, () -> adapter.notifyItemChanged(position)));
            } else if (position == messagePopupNotificationRow || position == groupPopupNotificationRow) {
                if (getParentActivity() == null) {
                    return;
                }
                showDialog(AlertsCreator.createPopupSelectDialog(getParentActivity(), NotificationsSettingsActivity.this, position == groupPopupNotificationRow, position == messagePopupNotificationRow, () -> adapter.notifyItemChanged(position)));
            } else if (position == messageVibrateRow || position == groupVibrateRow || position == callsVibrateRow) {
                if (getParentActivity() == null) {
                    return;
                }
                String key = null;
                if (position == messageVibrateRow) {
                    key = "vibrate_messages";
                } else if (position == groupVibrateRow) {
                    key = "vibrate_group";
                } else if (position == callsVibrateRow) {
                    key = "vibrate_calls";
                }
                showDialog(AlertsCreator.createVibrationSelectDialog(getParentActivity(), NotificationsSettingsActivity.this, 0, key, () -> adapter.notifyItemChanged(position)));
            } else if (position == messagePriorityRow || position == groupPriorityRow) {
                showDialog(AlertsCreator.createPrioritySelectDialog(getParentActivity(), NotificationsSettingsActivity.this, 0, position == groupPriorityRow, position == messagePriorityRow, () -> adapter.notifyItemChanged(position)));
            } else if (position == messageExceptionsRow || position == groupExceptionsRow) {
                presentFragment(new NotificationsExceptionsActivity(position == messageExceptionsRow ? exceptionUsers : exceptionChats));
            } else if (position == repeatRow) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("RepeatNotifications", R.string.RepeatNotifications));
                builder.setItems(new CharSequence[]{
                        LocaleController.getString("RepeatDisabled", R.string.RepeatDisabled),
                        LocaleController.formatPluralString("Minutes", 5),
                        LocaleController.formatPluralString("Minutes", 10),
                        LocaleController.formatPluralString("Minutes", 30),
                        LocaleController.formatPluralString("Hours", 1),
                        LocaleController.formatPluralString("Hours", 2),
                        LocaleController.formatPluralString("Hours", 4)
                }, (dialog, which) -> {
                    int minutes = 0;
                    if (which == 1) {
                        minutes = 5;
                    } else if (which == 2) {
                        minutes = 10;
                    } else if (which == 3) {
                        minutes = 30;
                    } else if (which == 4) {
                        minutes = 60;
                    } else if (which == 5) {
                        minutes = 60 * 2;
                    } else if (which == 6) {
                        minutes = 60 * 4;
                    }
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    preferences.edit().putInt("repeat_messages", minutes).commit();
                    adapter.notifyItemChanged(position);
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            }
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(!enabled);
            }
        });

        return fragmentView;
    }

    public void updateServerNotificationsSettings(boolean group) {
        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
        TLRPC.TL_account_updateNotifySettings req = new TLRPC.TL_account_updateNotifySettings();
        req.settings = new TLRPC.TL_inputPeerNotifySettings();
        req.settings.flags = 5;
        if (!group) {
            req.peer = new TLRPC.TL_inputNotifyUsers();
            req.settings.mute_until = preferences.getBoolean("EnableAll", true) ? 0 : Integer.MAX_VALUE;
            req.settings.show_previews = preferences.getBoolean("EnablePreviewAll", true);
        } else {
            req.peer = new TLRPC.TL_inputNotifyChats();
            req.settings.mute_until = preferences.getBoolean("EnableGroup", true) ? 0 : Integer.MAX_VALUE;
            req.settings.show_previews = preferences.getBoolean("EnablePreviewGroup", true);
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

        });
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            Uri ringtone = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            String name = null;
            if (ringtone != null) {
                Ringtone rng = RingtoneManager.getRingtone(getParentActivity(), ringtone);
                if (rng != null) {
                    if (requestCode == callsRingtoneRow) {
                        if (ringtone.equals(Settings.System.DEFAULT_RINGTONE_URI)) {
                            name = LocaleController.getString("DefaultRingtone", R.string.DefaultRingtone);
                        } else {
                            name = rng.getTitle(getParentActivity());
                        }
                    } else {
                        if (ringtone.equals(Settings.System.DEFAULT_NOTIFICATION_URI)) {
                            name = LocaleController.getString("SoundDefault", R.string.SoundDefault);
                        } else {
                            name = rng.getTitle(getParentActivity());
                        }
                    }
                    rng.stop();
                }
            }

            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
            SharedPreferences.Editor editor = preferences.edit();

            if (requestCode == messageSoundRow) {
                if (name != null && ringtone != null) {
                    editor.putString("GlobalSound", name);
                    editor.putString("GlobalSoundPath", ringtone.toString());
                } else {
                    editor.putString("GlobalSound", "NoSound");
                    editor.putString("GlobalSoundPath", "NoSound");
                }
            } else if (requestCode == groupSoundRow) {
                if (name != null && ringtone != null) {
                    editor.putString("GroupSound", name);
                    editor.putString("GroupSoundPath", ringtone.toString());
                } else {
                    editor.putString("GroupSound", "NoSound");
                    editor.putString("GroupSoundPath", "NoSound");
                }
            } else if (requestCode == callsRingtoneRow) {
                if (name != null && ringtone != null) {
                    editor.putString("CallsRingtone", name);
                    editor.putString("CallsRingtonePath", ringtone.toString());
                } else {
                    editor.putString("CallsRingtone", "NoSound");
                    editor.putString("CallsRingtonePath", "NoSound");
                }
            }
            editor.commit();
            if (requestCode == messageSoundRow || requestCode == groupSoundRow) {
                updateServerNotificationsSettings(requestCode == groupSoundRow);
            }
            adapter.notifyItemChanged(requestCode);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.notificationsSettingsUpdated) {
            adapter.notifyDataSetChanged();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return !(position == messageSectionRow || position == groupSectionRow || position == inappSectionRow ||
                    position == eventsSectionRow || position == otherSectionRow || position == resetSectionRow ||
                    position == eventsSectionRow2 || position == groupSectionRow2 ||
                    position == inappSectionRow2 || position == otherSectionRow2 || position == resetSectionRow2 ||
                    position == callsSectionRow2 || position == callsSectionRow);
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new TextDetailSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new TextColorCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 5:
                default:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == messageSectionRow) {
                        headerCell.setText(LocaleController.getString("MessageNotifications", R.string.MessageNotifications));
                    } else if (position == groupSectionRow) {
                        headerCell.setText(LocaleController.getString("GroupNotifications", R.string.GroupNotifications));
                    } else if (position == inappSectionRow) {
                        headerCell.setText(LocaleController.getString("InAppNotifications", R.string.InAppNotifications));
                    } else if (position == eventsSectionRow) {
                        headerCell.setText(LocaleController.getString("Events", R.string.Events));
                    } else if (position == otherSectionRow) {
                        headerCell.setText(LocaleController.getString("NotificationsOther", R.string.NotificationsOther));
                    } else if (position == resetSectionRow) {
                        headerCell.setText(LocaleController.getString("Reset", R.string.Reset));
                    } else if (position == callsSectionRow) {
                        headerCell.setText(LocaleController.getString("VoipNotificationSettings", R.string.VoipNotificationSettings));
                    }
                    break;
                }
                case 1: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    if (position == messageAlertRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("Alert", R.string.Alert), preferences.getBoolean("EnableAll", true), true);
                    } else if (position == groupAlertRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("Alert", R.string.Alert), preferences.getBoolean("EnableGroup", true), true);
                    } else if (position == messagePreviewRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("MessagePreview", R.string.MessagePreview), preferences.getBoolean("EnablePreviewAll", true), true);
                    } else if (position == groupPreviewRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("MessagePreview", R.string.MessagePreview), preferences.getBoolean("EnablePreviewGroup", true), true);
                    } else if (position == inappSoundRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("InAppSounds", R.string.InAppSounds), preferences.getBoolean("EnableInAppSounds", true), true);
                    } else if (position == inappVibrateRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("InAppVibrate", R.string.InAppVibrate), preferences.getBoolean("EnableInAppVibrate", true), true);
                    } else if (position == inappPreviewRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("InAppPreview", R.string.InAppPreview), preferences.getBoolean("EnableInAppPreview", true), true);
                    } else if (position == inappPriorityRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("NotificationsImportance", R.string.NotificationsImportance), preferences.getBoolean("EnableInAppPriority", false), false);
                    } else if (position == contactJoinedRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("ContactJoined", R.string.ContactJoined), preferences.getBoolean("EnableContactJoined", true), true);
                    } else if (position == pinnedMessageRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("PinnedMessages", R.string.PinnedMessages), preferences.getBoolean("PinnedMessages", true), false);
                    } else if (position == androidAutoAlertRow) {
                        checkCell.setTextAndCheck("Android Auto", preferences.getBoolean("EnableAutoNotifications", false), true);
                    } else if (position == notificationsServiceRow) {
                        checkCell.setTextAndValueAndCheck(LocaleController.getString("NotificationsService", R.string.NotificationsService), LocaleController.getString("NotificationsServiceInfo", R.string.NotificationsServiceInfo), preferences.getBoolean("pushService", true), true, true);
                    } else if (position == notificationsServiceConnectionRow) {
                        checkCell.setTextAndValueAndCheck(LocaleController.getString("NotificationsServiceConnection", R.string.NotificationsServiceConnection), LocaleController.getString("NotificationsServiceConnectionInfo", R.string.NotificationsServiceConnectionInfo), preferences.getBoolean("pushConnection", true), true, true);
                    } else if (position == badgeNumberRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("BadgeNumber", R.string.BadgeNumber), NotificationsController.getInstance(currentAccount).showBadgeNumber, true);
                    } else if (position == inchatSoundRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("InChatSound", R.string.InChatSound), preferences.getBoolean("EnableInChatSound", true), true);
                    } else if (position == callsVibrateRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("Vibrate", R.string.Vibrate), preferences.getBoolean("EnableCallVibrate", true), true);
                    }
                    break;
                }
                case 2: {
                    TextDetailSettingsCell settingsCell = (TextDetailSettingsCell) holder.itemView;
                    settingsCell.setMultilineDetail(true);
                    if (position == resetNotificationsRow) {
                        settingsCell.setTextAndValue(LocaleController.getString("ResetAllNotifications", R.string.ResetAllNotifications), LocaleController.getString("UndoAllCustom", R.string.UndoAllCustom), false);
                    } else if (position == messageExceptionsRow || position == groupExceptionsRow) {
                        ArrayList<NotificationException> arrayList;
                        if (position == groupExceptionsRow) {
                            arrayList = exceptionChats;
                        } else {
                            arrayList = exceptionUsers;
                        }
                        StringBuilder builder = new StringBuilder();
                        int length = 0;
                        for (int a = 0, size = arrayList.size(); a < size; a++) {
                            NotificationException exception = arrayList.get(a);
                            int lower_id = (int) exception.did;
                            int high_id = (int) (exception.did >> 32);

                            String name = null;
                            if (lower_id != 0) {
                                if (lower_id > 0) {
                                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(lower_id);
                                    if (user != null) {
                                        name = ContactsController.formatName(user.first_name, user.last_name);
                                    }
                                } else {
                                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lower_id);
                                    if (chat != null) {
                                        name = chat.title;
                                    }
                                }
                            } else {
                                TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(high_id);
                                if (encryptedChat != null) {
                                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
                                    if (user != null) {
                                        name = ContactsController.formatName(user.first_name, user.last_name);
                                    }
                                }
                            }
                            if (name != null) {
                                if (name.length() > 20) {
                                    int pos = 19;
                                    while (pos >= 0 && name.charAt(pos) == ' ') {
                                        pos--;
                                    }
                                    name = name.substring(0, pos + 1) + "..";
                                }
                                length += settingsCell.getValueTextView().getPaint().measureText(name);
                                if (builder.length() > 0) {
                                    builder.append(", ");
                                }
                                builder.append(name);
                                if (length > AndroidUtilities.displaySize.x * 2.0f) {
                                    if (a != size - 1) {
                                        builder.append(", ...");
                                    }
                                    break;
                                }
                            }
                        }
                        settingsCell.setTextAndValue(LocaleController.getString("NotificationsExceptions", R.string.NotificationsExceptions), builder.toString(), false);
                    }
                    break;
                }
                case 3: {
                    TextColorCell textColorCell = (TextColorCell) holder.itemView;
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    int color;
                    if (position == messageLedRow) {
                        color = preferences.getInt("MessagesLed", 0xff0000ff);
                    } else {
                        color = preferences.getInt("GroupLed", 0xff0000ff);
                    }
                    for (int a = 0; a < 9; a++) {
                        if (TextColorCell.colorsToSave[a] == color) {
                            color = TextColorCell.colors[a];
                            break;
                        }
                    }
                    textColorCell.setTextAndColor(LocaleController.getString("LedColor", R.string.LedColor), color, true);
                    break;
                }
                case 5:
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    if (position == messageSoundRow || position == groupSoundRow || position == callsRingtoneRow) {
                        String value = null;
                        if (position == messageSoundRow) {
                            value = preferences.getString("GlobalSound", LocaleController.getString("SoundDefault", R.string.SoundDefault));
                        } else if (position == groupSoundRow) {
                            value = preferences.getString("GroupSound", LocaleController.getString("SoundDefault", R.string.SoundDefault));
                        } else if (position == callsRingtoneRow) {
                            value = preferences.getString("CallsRingtone", LocaleController.getString("DefaultRingtone", R.string.DefaultRingtone));
                        }
                        if (value.equals("NoSound")) {
                            value = LocaleController.getString("NoSound", R.string.NoSound);
                        }
                        if (position == callsRingtoneRow) {
                            textCell.setTextAndValue(LocaleController.getString("VoipSettingsRingtone", R.string.VoipSettingsRingtone), value, true);
                        } else {
                            textCell.setTextAndValue(LocaleController.getString("Sound", R.string.Sound), value, true);
                        }
                    } else if (position == messageVibrateRow || position == groupVibrateRow || position == callsVibrateRow) {
                        int value = 0;
                        if (position == messageVibrateRow) {
                            value = preferences.getInt("vibrate_messages", 0);
                        } else if (position == groupVibrateRow) {
                            value = preferences.getInt("vibrate_group", 0);
                        } else if (position == callsVibrateRow) {
                            value = preferences.getInt("vibrate_calls", 0);
                        }
                        if (value == 0) {
                            textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("VibrationDefault", R.string.VibrationDefault), true);
                        } else if (value == 1) {
                            textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("Short", R.string.Short), true);
                        } else if (value == 2) {
                            textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("VibrationDisabled", R.string.VibrationDisabled), true);
                        } else if (value == 3) {
                            textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("Long", R.string.Long), true);
                        } else if (value == 4) {
                            textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("OnlyIfSilent", R.string.OnlyIfSilent), true);
                        }
                    } else if (position == repeatRow) {
                        int minutes = preferences.getInt("repeat_messages", 60);
                        String value;
                        if (minutes == 0) {
                            value = LocaleController.getString("RepeatNotificationsNever", R.string.RepeatNotificationsNever);
                        } else if (minutes < 60) {
                            value = LocaleController.formatPluralString("Minutes", minutes);
                        } else {
                            value = LocaleController.formatPluralString("Hours", minutes / 60);
                        }
                        textCell.setTextAndValue(LocaleController.getString("RepeatNotifications", R.string.RepeatNotifications), value, false);
                    } else if (position == messagePriorityRow || position == groupPriorityRow) {
                        int value = 0;
                        if (position == messagePriorityRow) {
                            value = preferences.getInt("priority_messages", 1);
                        } else if (position == groupPriorityRow) {
                            value = preferences.getInt("priority_group", 1);
                        }
                        if (value == 0) {
                            textCell.setTextAndValue(LocaleController.getString("NotificationsImportance", R.string.NotificationsImportance), LocaleController.getString("NotificationsPriorityHigh", R.string.NotificationsPriorityHigh), true);
                        } else if (value == 1 || value == 2) {
                            textCell.setTextAndValue(LocaleController.getString("NotificationsImportance", R.string.NotificationsImportance), LocaleController.getString("NotificationsPriorityUrgent", R.string.NotificationsPriorityUrgent), true);
                        } else if (value == 4) {
                            textCell.setTextAndValue(LocaleController.getString("NotificationsImportance", R.string.NotificationsImportance), LocaleController.getString("NotificationsPriorityLow", R.string.NotificationsPriorityLow), true);
                        } else if (value == 5) {
                            textCell.setTextAndValue(LocaleController.getString("NotificationsImportance", R.string.NotificationsImportance), LocaleController.getString("NotificationsPriorityMedium", R.string.NotificationsPriorityMedium), true);
                        }
                    } else if (position == messageExceptionsRow || position == groupExceptionsRow) {
                        String text;
                        if (position == messageExceptionsRow && exceptionUsers == null || position == groupExceptionsRow && exceptionChats == null) {
                            text = LocaleController.getString("Loading", R.string.Loading);
                        } else if (position == messageExceptionsRow) {
                            if (exceptionUsers.isEmpty()) {
                                text = LocaleController.getString("EmptyExceptions", R.string.EmptyExceptions);
                            } else {
                                text = String.format("%d", exceptionUsers.size());
                            }
                        } else {
                            if (exceptionChats.isEmpty()) {
                                text = LocaleController.getString("EmptyExceptions", R.string.EmptyExceptions);
                            } else {
                                text = String.format("%d", exceptionChats.size());
                            }
                        }
                        textCell.setTextAndValue(LocaleController.getString("NotificationsExceptions", R.string.NotificationsExceptions), text, false);
                    } else if (position == messagePopupNotificationRow || position == groupPopupNotificationRow) {
                        int option = 0;
                        if (position == messagePopupNotificationRow) {
                            option = preferences.getInt("popupAll", 0);
                        } else if (position == groupPopupNotificationRow) {
                            option = preferences.getInt("popupGroup", 0);
                        }
                        String value;
                        if (option == 0) {
                            value = LocaleController.getString("NoPopup", R.string.NoPopup);
                        } else if (option == 1) {
                            value = LocaleController.getString("OnlyWhenScreenOn", R.string.OnlyWhenScreenOn);
                        } else if (option == 2) {
                            value = LocaleController.getString("OnlyWhenScreenOff", R.string.OnlyWhenScreenOff);
                        } else {
                            value = LocaleController.getString("AlwaysShowPopup", R.string.AlwaysShowPopup);
                        }
                        textCell.setTextAndValue(LocaleController.getString("PopupNotification", R.string.PopupNotification), value, true);
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == messageSectionRow || position == groupSectionRow || position == inappSectionRow ||
                    position == eventsSectionRow || position == otherSectionRow || position == resetSectionRow ||
                    position == callsSectionRow) {
                return 0;
            } else if (position == messageAlertRow || position == messagePreviewRow || position == groupAlertRow ||
                    position == groupPreviewRow || position == inappSoundRow || position == inappVibrateRow ||
                    position == inappPreviewRow || position == contactJoinedRow || position == pinnedMessageRow ||
                    position == notificationsServiceRow || position == badgeNumberRow || position == inappPriorityRow ||
                    position == inchatSoundRow || position == androidAutoAlertRow || position == notificationsServiceConnectionRow) {
                return 1;
            } else if (position == messageLedRow || position == groupLedRow) {
                return 3;
            } else if (position == eventsSectionRow2 || position == groupSectionRow2 ||
                    position == inappSectionRow2 || position == otherSectionRow2 || position == resetSectionRow2 ||
                    position == callsSectionRow2) {
                return 4;
            } else if (position == resetNotificationsRow || position == messageExceptionsRow && exceptionUsers != null && !exceptionUsers.isEmpty() || position == groupExceptionsRow && exceptionChats != null && !exceptionChats.isEmpty()) {
                return 2;
            } else {
                return 5;
            }
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, TextCheckCell.class, TextDetailSettingsCell.class, TextColorCell.class, TextSettingsCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),

                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchThumb),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchThumbChecked),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked),

                new ThemeDescription(listView, 0, new Class[]{TextColorCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),

                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
        };
    }
}
