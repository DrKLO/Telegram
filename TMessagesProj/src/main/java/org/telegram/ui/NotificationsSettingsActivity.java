/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;

public class NotificationsSettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public static class NotificationException {
        public int muteUntil;
        public boolean hasCustom;
        public int notify;
        public long did;
        public boolean story;
        public boolean auto;
    }

    private RecyclerListView listView;
    private boolean reseting = false;
    private ListAdapter adapter;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayoutManager layoutManager;
    private ArrayList<NotificationException> exceptionUsers = null;
    private ArrayList<NotificationException> exceptionChats = null;
    private ArrayList<NotificationException> exceptionChannels = null;
    private ArrayList<NotificationException> exceptionStories = null;
    private ArrayList<NotificationException> exceptionAutoStories = null;

    private int accountsSectionRow;
    private int accountsAllRow;
    private int accountsInfoRow;

    private int notificationsServiceRow;
    private int notificationsServiceConnectionRow;

    private int notificationsSectionRow;
    private int privateRow;
    private int groupRow;
    private int channelsRow;
    private int storiesRow;
    private int reactionsRow;
    private int notificationsSection2Row;

    private int inappSectionRow;
    private int inappSoundRow;
    private int inappVibrateRow;
    private int inappPreviewRow;
    private int inchatSoundRow;
    private int inappPriorityRow;
    private int callsSection2Row;
    private int callsSectionRow;
    private int callsVibrateRow;
    private int callsRingtoneRow;
    private int eventsSection2Row;
    private int eventsSectionRow;
    private int contactJoinedRow;
    private int pinnedMessageRow;
    private int otherSection2Row;
    private int otherSectionRow;
    private int badgeNumberSection;
    private int badgeNumberShowRow;
    private int badgeNumberMutedRow;
    private int badgeNumberMessagesRow;
    private int badgeNumberSection2Row;
    private int androidAutoAlertRow;
    private int repeatRow;
    private int resetSection2Row;
    private int resetSectionRow;
    private int resetNotificationsRow;
    private int resetNotificationsSectionRow;
    private int rowCount = 0;

    private boolean updateVibrate;
    private boolean updateRingtone;
    private boolean updateRepeatNotifications;

    @Override
    public boolean onFragmentCreate() {
        MessagesController.getInstance(currentAccount).loadSignUpNotificationsSettings();
        loadExceptions();

        if (UserConfig.getActivatedAccountsCount() > 1) {
            accountsSectionRow = rowCount++;
            accountsAllRow = rowCount++;
            accountsInfoRow = rowCount++;
        } else {
            accountsSectionRow = -1;
            accountsAllRow = -1;
            accountsInfoRow = -1;
        }

        notificationsSectionRow = rowCount++;
        privateRow = rowCount++;
        groupRow = rowCount++;
        channelsRow = rowCount++;
        storiesRow = rowCount++;
        reactionsRow = rowCount++;
        notificationsSection2Row = rowCount++;

        callsSectionRow = rowCount++;
        callsVibrateRow = rowCount++;
        callsRingtoneRow = rowCount++;
        eventsSection2Row = rowCount++;

        badgeNumberSection = rowCount++;
        badgeNumberShowRow = rowCount++;
        badgeNumberMutedRow = rowCount++;
        badgeNumberMessagesRow = rowCount++;
        badgeNumberSection2Row = rowCount++;

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
        callsSection2Row = rowCount++;

        eventsSectionRow = rowCount++;
        contactJoinedRow = rowCount++;
        pinnedMessageRow = rowCount++;
        otherSection2Row = rowCount++;

        otherSectionRow = rowCount++;
        notificationsServiceRow = rowCount++;
        notificationsServiceConnectionRow = rowCount++;
        androidAutoAlertRow = -1;
        repeatRow = rowCount++;
        resetSection2Row = rowCount++;
        resetSectionRow = rowCount++;
        resetNotificationsRow = rowCount++;
        resetNotificationsSectionRow = rowCount++;

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.notificationsSettingsUpdated);

        getMessagesController().reloadReactionsNotifySettings();

        return super.onFragmentCreate();
    }

    private void loadExceptions() {
        MediaDataController.getInstance(currentAccount).loadHints(true);
        final ArrayList<TLRPC.TL_topPeer> topPeers = new ArrayList<>(MediaDataController.getInstance(currentAccount).hints);
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            ArrayList<NotificationException> usersResult = new ArrayList<>();
            ArrayList<NotificationException> chatsResult = new ArrayList<>();
            ArrayList<NotificationException> channelsResult = new ArrayList<>();
            ArrayList<NotificationException> storiesResult = new ArrayList<>();
            ArrayList<NotificationException> storiesAutoResult = new ArrayList<>();
            LongSparseArray<NotificationException> waitingForLoadExceptions = new LongSparseArray<>();

            ArrayList<Long> usersToLoad = new ArrayList<>();
            ArrayList<Long> chatsToLoad = new ArrayList<>();
            ArrayList<Integer> encryptedChatsToLoad = new ArrayList<>();

            ArrayList<TLRPC.User> users = new ArrayList<>();
            ArrayList<TLRPC.Chat> chats = new ArrayList<>();
            ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
            long selfId = UserConfig.getInstance(currentAccount).clientUserId;

            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
            Map<String, ?> values = preferences.getAll();
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("notify2_")) {
                    key = key.replace("notify2_", "");
                    if (key.contains("_")) {
                        //it's topic
                        continue;
                    }

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

                        if (DialogObject.isEncryptedDialog(did)) {
                            int encryptedChatId = DialogObject.getEncryptedChatId(did);
                            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(encryptedChatId);
                            if (encryptedChat == null) {
                                encryptedChatsToLoad.add(encryptedChatId);
                                waitingForLoadExceptions.put(did, exception);
                            } else {
                                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
                                if (user == null) {
                                    usersToLoad.add(encryptedChat.user_id);
                                    waitingForLoadExceptions.put(encryptedChat.user_id, exception);
                                } else if (user.deleted) {
                                    continue;
                                }
                            }
                            usersResult.add(exception);
                        } else if (DialogObject.isUserDialog(did)) {
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                            if (user == null) {
                                usersToLoad.add(did);
                                waitingForLoadExceptions.put(did, exception);
                            } else if (user.deleted) {
                                continue;
                            }
                            usersResult.add(exception);
                        } else {
                            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                            if (chat == null) {
                                chatsToLoad.add(-did);
                                waitingForLoadExceptions.put(did, exception);
                                continue;
                            } else if (chat.left || chat.kicked || chat.migrated_to != null) {
                                continue;
                            }
                            if (ChatObject.isChannel(chat) && !chat.megagroup) {
                                channelsResult.add(exception);
                            } else {
                                chatsResult.add(exception);
                            }
                        }
                    }
                }
            }
            final HashSet<Long> customStories = new HashSet<>();
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("stories_")) {
                    key = key.substring(8);
                    try {
                        long did = Utilities.parseLong(key);
                        if (did != 0 && did != selfId) {
                            NotificationsSettingsActivity.NotificationException exception = new NotificationsSettingsActivity.NotificationException();
                            exception.did = did;
                            exception.notify = ((Boolean) entry.getValue()) ? 0 : Integer.MAX_VALUE;
                            exception.story = true;
                            if (DialogObject.isUserDialog(did)) {
                                TLRPC.User user = getMessagesController().getUser(did);
                                if (user == null) {
                                    usersToLoad.add(did);
                                    waitingForLoadExceptions.put(did, exception);
                                } else if (user.deleted) {
                                    continue;
                                }
                                storiesResult.add(exception);
                                customStories.add(did);
                            }
                        }
                    } catch (Exception ignore) {}
                }
            }
            if (topPeers != null) {
                Collections.sort(topPeers, Comparator.comparingDouble(a -> a.rating));
                for (int i = Math.max(0, topPeers.size() - 5); i < topPeers.size(); ++i) {
                    TLRPC.TL_topPeer topPeer = topPeers.get(i);
                    final long did = DialogObject.getPeerDialogId(topPeer.peer);
                    if (!customStories.contains(did)) {
                        NotificationsSettingsActivity.NotificationException exception = new NotificationsSettingsActivity.NotificationException();
                        exception.did = did;
                        exception.notify = 0;
                        exception.auto = true;
                        exception.story = true;
                        if (DialogObject.isUserDialog(did)) {
                            TLRPC.User user = getMessagesController().getUser(did);
                            if (user == null) {
                                usersToLoad.add(did);
                                waitingForLoadExceptions.put(did, exception);
                            } else if (user.deleted) {
                                continue;
                            }
                            storiesAutoResult.add(0, exception);
                            customStories.add(did);
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
                        MessagesStorage.getInstance(currentAccount).getUsersInternal(usersToLoad, users);
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
                    NotificationException exception = waitingForLoadExceptions.get(-chat.id);
                    waitingForLoadExceptions.remove(-chat.id);

                    if (exception != null) {
                        if (ChatObject.isChannel(chat) && !chat.megagroup) {
                            channelsResult.add(exception);
                        } else {
                            chatsResult.add(exception);
                        }
                    }
                }
                for (int a = 0, size = users.size(); a < size; a++) {
                    TLRPC.User user = users.get(a);
                    if (user.deleted) {
                        continue;
                    }
                    waitingForLoadExceptions.remove(user.id);
                }
                for (int a = 0, size = encryptedChats.size(); a < size; a++) {
                    TLRPC.EncryptedChat encryptedChat = encryptedChats.get(a);
                    waitingForLoadExceptions.remove(DialogObject.makeEncryptedDialogId(encryptedChat.id));
                }
                for (int a = 0, size = waitingForLoadExceptions.size(); a < size; a++) {
                    long did = waitingForLoadExceptions.keyAt(a);
                    if (DialogObject.isChatDialog(did)) {
                        chatsResult.remove(waitingForLoadExceptions.valueAt(a));
                        channelsResult.remove(waitingForLoadExceptions.valueAt(a));
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
                exceptionChannels = channelsResult;
                exceptionStories = storiesResult;
                exceptionAutoStories = storiesAutoResult;
                adapter.notifyItemChanged(privateRow);
                adapter.notifyItemChanged(groupRow);
                adapter.notifyItemChanged(channelsRow);
                adapter.notifyItemChanged(storiesRow);
            });
        });

        // stories exceptions
        // adapter.notifyItemChanged(storiesRow);
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
        actionBar.setTitle(getString("NotificationsAndSounds", R.string.NotificationsAndSounds));
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
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        listView.setVerticalScrollBarEnabled(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position, x, y) -> {
            boolean enabled = false;
            if (getParentActivity() == null) {
                return;
            }
            if (position == privateRow || position == groupRow || position == channelsRow || position == storiesRow || position == reactionsRow) {
                int type;
                ArrayList<NotificationException> exceptions;
                ArrayList<NotificationException> autoExceptions = null;
                if (position == privateRow) {
                    type = NotificationsController.TYPE_PRIVATE;
                    exceptions = exceptionUsers;
                    enabled = getNotificationsController().isGlobalNotificationsEnabled(type);
                } else if (position == groupRow) {
                    type = NotificationsController.TYPE_GROUP;
                    exceptions = exceptionChats;
                    enabled = getNotificationsController().isGlobalNotificationsEnabled(type);
                } else if (position == storiesRow) {
                    type = NotificationsController.TYPE_STORIES;
                    exceptions = exceptionStories;
                    autoExceptions = exceptionAutoStories;
                    enabled = getNotificationsSettings().getBoolean("EnableAllStories", false);
                } else if (position == reactionsRow) {
                    type = NotificationsController.TYPE_REACTIONS_MESSAGES;
                    exceptions = null;
                    enabled = getNotificationsSettings().getBoolean("EnableReactionsMessages", true) || getNotificationsSettings().getBoolean("EnableReactionsStories", true);
                } else {
                    type = NotificationsController.TYPE_CHANNEL;
                    exceptions = exceptionChannels;
                    enabled = getNotificationsController().isGlobalNotificationsEnabled(type);
                }
                if (exceptions == null && type != NotificationsController.TYPE_REACTIONS_MESSAGES) {
                    return;
                }

                NotificationsCheckCell checkCell = (NotificationsCheckCell) view;
                if (LocaleController.isRTL && x <= AndroidUtilities.dp(76) || !LocaleController.isRTL && x >= view.getMeasuredWidth() - AndroidUtilities.dp(76)) {
                    final boolean enabledFinal = enabled;
                    showExceptionsAlert(position, () -> {
                        if (type == NotificationsController.TYPE_STORIES) {
                            SharedPreferences.Editor edit = getNotificationsSettings().edit();
                            if (enabledFinal) {
                                edit.remove("EnableAllStories");
                            } else {
                                edit.putBoolean("EnableAllStories", true);
                            }
                            edit.apply();
                            getNotificationsController().updateServerNotificationsSettings(type);
                        } else if (
                            type == NotificationsController.TYPE_REACTIONS_MESSAGES ||
                            type == NotificationsController.TYPE_REACTIONS_STORIES
                        ) {
                            SharedPreferences.Editor edit = getNotificationsSettings().edit();
                            if (enabledFinal) {
                                edit.putBoolean("EnableReactionsMessages", false);
                                edit.putBoolean("EnableReactionsStories", false);
                            } else {
                                edit.putBoolean("EnableReactionsMessages", true);
                                edit.putBoolean("EnableReactionsStories", true);
                            }
                            edit.apply();
                            getNotificationsController().updateServerNotificationsSettings(type);
                            getNotificationsController().deleteNotificationChannelGlobal(type);
                        } else {
                            getNotificationsController().setGlobalNotificationsEnabled(type, !enabledFinal ? 0 : Integer.MAX_VALUE);
                        }
                        checkCell.setChecked(!enabledFinal, 0);
                        adapter.notifyItemChanged(position);
                    });
                } else {
                    presentFragment(new NotificationsCustomSettingsActivity(type, exceptions, autoExceptions));
                }
            } else if (position == callsRingtoneRow) {
                try {
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    Intent tmpIntent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                    tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
                    tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                    tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
                    tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
                    Uri currentSound = null;

                    String defaultPath = null;
                    Uri defaultUri = Settings.System.DEFAULT_RINGTONE_URI;
                    if (defaultUri != null) {
                        defaultPath = defaultUri.getPath();
                    }
                    String path = preferences.getString("CallsRingtonePath", defaultPath);
                    if (path != null && !path.equals("NoSound")) {
                        if (path.equals(defaultPath)) {
                            currentSound = defaultUri;
                        } else {
                            currentSound = Uri.parse(path);
                        }
                    }
                    tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentSound);
                    startActivityForResult(tmpIntent, position);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else if (position == resetNotificationsRow) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(getString("ResetNotificationsAlertTitle", R.string.ResetNotificationsAlertTitle));
                builder.setMessage(getString("ResetNotificationsAlert", R.string.ResetNotificationsAlert));
                builder.setPositiveButton(getString("Reset", R.string.Reset), (dialogInterface, i) -> {
                    if (reseting) {
                        return;
                    }
                    reseting = true;
                    TL_account.resetNotifySettings req = new TL_account.resetNotifySettings();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        getMessagesController().enableJoined = true;
                        reseting = false;
                        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.clear();
                        editor.commit();
                        exceptionChats.clear();
                        exceptionUsers.clear();
                        adapter.notifyDataSetChanged();
                        if (getParentActivity() != null) {
                            Toast toast = Toast.makeText(getParentActivity(), getString("ResetNotificationsText", R.string.ResetNotificationsText), Toast.LENGTH_SHORT);
                            toast.show();
                        }
                        getMessagesStorage().updateMutedDialogsFiltersCounters();
                    }));
                });
                builder.setNegativeButton(getString("Cancel", R.string.Cancel), null);
                AlertDialog alertDialog = builder.create();
                showDialog(alertDialog);
                TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                }
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
                getNotificationsController().setInChatSoundEnabled(!enabled);
            } else if (position == inappPriorityRow) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                enabled = preferences.getBoolean("EnableInAppPopup", true);
                editor.putBoolean("EnableInAppPopup", !enabled);
                editor.commit();
            } else if (position == contactJoinedRow) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                enabled = preferences.getBoolean("EnableContactJoined", true);
                MessagesController.getInstance(currentAccount).enableJoined = !enabled;
                editor.putBoolean("EnableContactJoined", !enabled);
                editor.commit();
                TL_account.setContactSignUpNotification req = new TL_account.setContactSignUpNotification();
                req.silent = enabled;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

                });
            } /*else if (position == storiesRow) {
                SharedPreferences preferences = getNotificationsSettings();
                SharedPreferences.Editor editor = preferences.edit();
                enabled = preferences.getBoolean("EnableAllStories", true);
                editor.putBoolean("EnableAllStories", !enabled);
                editor.commit();
                getNotificationsController().updateServerNotificationsSettings(NotificationsController.TYPE_PRIVATE);
            } */else if (position == pinnedMessageRow) {
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
            } else if (position == badgeNumberShowRow) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                enabled = getNotificationsController().showBadgeNumber;
                getNotificationsController().showBadgeNumber = !enabled;
                editor.putBoolean("badgeNumber", getNotificationsController().showBadgeNumber);
                editor.commit();
                getNotificationsController().updateBadge();
            } else if (position == badgeNumberMutedRow) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                enabled = getNotificationsController().showBadgeMuted;
                getNotificationsController().showBadgeMuted = !enabled;
                editor.putBoolean("badgeNumberMuted", getNotificationsController().showBadgeMuted);
                editor.commit();
                getNotificationsController().updateBadge();
                getMessagesStorage().updateMutedDialogsFiltersCounters();
            } else if (position == badgeNumberMessagesRow) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                enabled = getNotificationsController().showBadgeMessages;
                getNotificationsController().showBadgeMessages = !enabled;
                editor.putBoolean("badgeNumberMessages", getNotificationsController().showBadgeMessages);
                editor.commit();
                getNotificationsController().updateBadge();
            } else if (position == notificationsServiceConnectionRow) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                enabled = preferences.getBoolean("pushConnection", getMessagesController().backgroundConnection);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("pushConnection", !enabled);
                editor.commit();
                if (!enabled) {
                    ConnectionsManager.getInstance(currentAccount).setPushConnectionEnabled(true);
                } else {
                    ConnectionsManager.getInstance(currentAccount).setPushConnectionEnabled(false);
                }
            } else if (position == accountsAllRow) {
                SharedPreferences preferences = MessagesController.getGlobalNotificationsSettings();
                enabled = preferences.getBoolean("AllAccounts", true);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("AllAccounts", !enabled);
                editor.commit();
                SharedConfig.showNotificationsForAllAccounts = !enabled;
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (SharedConfig.showNotificationsForAllAccounts) {
                        NotificationsController.getInstance(a).showNotifications();
                    } else {
                        if (a == currentAccount) {
                            NotificationsController.getInstance(a).showNotifications();
                        } else {
                            NotificationsController.getInstance(a).hideNotifications();
                        }
                    }
                }
            } else if (position == notificationsServiceRow) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                enabled = preferences.getBoolean("pushService", getMessagesController().keepAliveService);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("pushService", !enabled);
                editor.commit();
                ApplicationLoader.startPushService();
            } else if (position == callsVibrateRow) {
                if (getParentActivity() == null) {
                    return;
                }
                String key = null;
                if (position == callsVibrateRow) {
                    key = "vibrate_calls";
                }
                showDialog(AlertsCreator.createVibrationSelectDialog(getParentActivity(), 0, 0, key, () -> {
                    updateVibrate = true;
                    adapter.notifyItemChanged(position);
                }));
            } else if (position == repeatRow) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(getString("RepeatNotifications", R.string.RepeatNotifications));
                builder.setItems(new CharSequence[]{
                        getString("RepeatDisabled", R.string.RepeatDisabled),
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
                    updateRepeatNotifications = true;
                    adapter.notifyItemChanged(position);
                });
                builder.setNegativeButton(getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            }
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(!enabled);
            }
        });

        return fragmentView;
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
                            name = getString("DefaultRingtone", R.string.DefaultRingtone);
                        } else {
                            name = rng.getTitle(getParentActivity());
                        }
                    } else {
                        if (ringtone.equals(Settings.System.DEFAULT_NOTIFICATION_URI)) {
                            name = getString("SoundDefault", R.string.SoundDefault);
                        } else {
                            name = rng.getTitle(getParentActivity());
                        }
                    }
                    rng.stop();
                }
            }

            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
            SharedPreferences.Editor editor = preferences.edit();

            if (requestCode == callsRingtoneRow) {
                if (name != null && ringtone != null) {
                    editor.putString("CallsRingtone", name);
                    editor.putString("CallsRingtonePath", ringtone.toString());
                } else {
                    editor.putString("CallsRingtone", "NoSound");
                    editor.putString("CallsRingtonePath", "NoSound");
                }
                updateRingtone = true;
            }
            editor.commit();
            adapter.notifyItemChanged(requestCode);
        }
    }

    private void showExceptionsAlert(int position, Runnable whenDone) {
        ArrayList<NotificationException> exceptions;
        final ArrayList<NotificationException> autoExceptions;
        String alertText = null;

        if (position == storiesRow) {
            exceptions = exceptionStories;
            autoExceptions = exceptionAutoStories;
            if (exceptions != null && !exceptions.isEmpty()) {
                alertText = LocaleController.formatPluralString("ChatsException", exceptions.size());
            }
        } else if (position == privateRow) {
            exceptions = exceptionUsers;
            autoExceptions = null;
            if (exceptions != null && !exceptions.isEmpty()) {
                alertText = LocaleController.formatPluralString("ChatsException", exceptions.size());
            }
        } else if (position == groupRow) {
            exceptions = exceptionChats;
            autoExceptions = null;
            if (exceptions != null && !exceptions.isEmpty()) {
                alertText = LocaleController.formatPluralString("Groups", exceptions.size());
            }
        } else if (position == reactionsRow) {
            whenDone.run();
            return;
        } else {
            exceptions = exceptionChannels;
            autoExceptions = null;
            if (exceptions != null && !exceptions.isEmpty()) {
                alertText = LocaleController.formatPluralString("Channels", exceptions.size());
            }
        }
        if (alertText == null) {
            whenDone.run();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        if (exceptions.size() == 1) {
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("NotificationsExceptionsSingleAlert", R.string.NotificationsExceptionsSingleAlert, alertText)));
        } else {
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("NotificationsExceptionsAlert", R.string.NotificationsExceptionsAlert, alertText)));
        }
        builder.setTitle(getString("NotificationsExceptions", R.string.NotificationsExceptions));
        builder.setNeutralButton(getString("ViewExceptions", R.string.ViewExceptions), (dialogInterface, i) -> presentFragment(new NotificationsCustomSettingsActivity(-1, exceptions, autoExceptions)));
        builder.setNegativeButton(getString("OK", R.string.OK), (di, i) -> whenDone.run());
        showDialog(builder.create());
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
            return !(position == notificationsSectionRow || position == notificationsSection2Row || position == inappSectionRow ||
                    position == eventsSectionRow || position == otherSectionRow || position == resetSectionRow ||
                    position == badgeNumberSection || position == otherSection2Row || position == resetSection2Row ||
                    position == callsSection2Row || position == callsSectionRow || position == badgeNumberSection2Row ||
                    position == accountsSectionRow || position == accountsInfoRow || position == resetNotificationsSectionRow ||
                    position == eventsSection2Row);
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
                    view = new HeaderCell(mContext, resourceProvider);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextCheckCell(mContext, resourceProvider);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new TextDetailSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new NotificationsCheckCell(mContext, 21, 64, true, resourceProvider);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = new ShadowSectionCell(mContext, resourceProvider);
                    break;
                case 5:
                    view = new TextSettingsCell(mContext, resourceProvider);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 6:
                default:
                    view = new TextInfoPrivacyCell(mContext, resourceProvider);
                    view.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == notificationsSectionRow) {
                        headerCell.setText(getString("NotificationsForChats", R.string.NotificationsForChats));
                    } else if (position == inappSectionRow) {
                        headerCell.setText(getString("InAppNotifications", R.string.InAppNotifications));
                    } else if (position == eventsSectionRow) {
                        headerCell.setText(getString("Events", R.string.Events));
                    } else if (position == otherSectionRow) {
                        headerCell.setText(getString("NotificationsOther", R.string.NotificationsOther));
                    } else if (position == resetSectionRow) {
                        headerCell.setText(getString("Reset", R.string.Reset));
                    } else if (position == callsSectionRow) {
                        headerCell.setText(getString("VoipNotificationSettings", R.string.VoipNotificationSettings));
                    } else if (position == badgeNumberSection) {
                        headerCell.setText(getString("BadgeNumber", R.string.BadgeNumber));
                    } else if (position == accountsSectionRow) {
                        headerCell.setText(getString("ShowNotificationsFor", R.string.ShowNotificationsFor));
                    }
                    break;
                }
                case 1: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    if (position == inappSoundRow) {
                        checkCell.setTextAndCheck(getString(R.string.InAppSounds), preferences.getBoolean("EnableInAppSounds", true), true);
                    } else if (position == inappVibrateRow) {
                        checkCell.setTextAndCheck(getString(R.string.InAppVibrate), preferences.getBoolean("EnableInAppVibrate", true), true);
                    } else if (position == inappPreviewRow) {
                        checkCell.setTextAndCheck(getString(R.string.InAppPreview), preferences.getBoolean("EnableInAppPreview", true), true);
                    } else if (position == inappPriorityRow) {
                        checkCell.setTextAndValueAndCheck(getString(R.string.InAppPopup), getString(R.string.InAppPopupInfo), preferences.getBoolean("EnableInAppPopup", true), true, false);
                    } else if (position == contactJoinedRow) {
                        checkCell.setTextAndCheck(getString("ContactJoined", R.string.ContactJoined), preferences.getBoolean("EnableContactJoined", true), true);
                    } else if (position == pinnedMessageRow) {
                        checkCell.setTextAndCheck(getString("PinnedMessages", R.string.PinnedMessages), preferences.getBoolean("PinnedMessages", true), false);
                    } else if (position == androidAutoAlertRow) {
                        checkCell.setTextAndCheck("Android Auto", preferences.getBoolean("EnableAutoNotifications", false), true);
                    } else if (position == notificationsServiceRow) {
                        checkCell.setTextAndValueAndCheck(getString("NotificationsService", R.string.NotificationsService), getString("NotificationsServiceInfo", R.string.NotificationsServiceInfo), preferences.getBoolean("pushService", getMessagesController().keepAliveService), true, true);
                    } else if (position == notificationsServiceConnectionRow) {
                        checkCell.setTextAndValueAndCheck(getString("NotificationsServiceConnection", R.string.NotificationsServiceConnection), getString("NotificationsServiceConnectionInfo", R.string.NotificationsServiceConnectionInfo), preferences.getBoolean("pushConnection", getMessagesController().backgroundConnection), true, true);
                    } else if (position == badgeNumberShowRow) {
                        checkCell.setTextAndCheck(getString("BadgeNumberShow", R.string.BadgeNumberShow), getNotificationsController().showBadgeNumber, true);
                    } else if (position == badgeNumberMutedRow) {
                        checkCell.setTextAndCheck(getString("BadgeNumberMutedChats", R.string.BadgeNumberMutedChats), getNotificationsController().showBadgeMuted, true);
                    } else if (position == badgeNumberMessagesRow) {
                        checkCell.setTextAndCheck(getString("BadgeNumberUnread", R.string.BadgeNumberUnread), getNotificationsController().showBadgeMessages, false);
                    } else if (position == inchatSoundRow) {
                        checkCell.setTextAndCheck(getString("InChatSound", R.string.InChatSound), preferences.getBoolean("EnableInChatSound", true), true);
                    } else if (position == callsVibrateRow) {
                        checkCell.setTextAndCheck(getString("Vibrate", R.string.Vibrate), preferences.getBoolean("EnableCallVibrate", true), true);
                    } else if (position == accountsAllRow) {
                        checkCell.setTextAndCheck(getString("AllAccounts", R.string.AllAccounts), MessagesController.getGlobalNotificationsSettings().getBoolean("AllAccounts", true), false);
                    }
                    break;
                }
                case 2: {
                    TextDetailSettingsCell settingsCell = (TextDetailSettingsCell) holder.itemView;
                    settingsCell.setMultilineDetail(true);
                    if (position == resetNotificationsRow) {
                        settingsCell.setTextAndValue(getString("ResetAllNotifications", R.string.ResetAllNotifications), getString("UndoAllCustom", R.string.UndoAllCustom), false);
                    }
                    break;
                }
                case 3: {
                    NotificationsCheckCell checkCell = (NotificationsCheckCell) holder.itemView;
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    int currentTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();

                    String text;
                    int offUntil;
                    ArrayList<NotificationException> exceptions;
                    ArrayList<NotificationException> autoExceptions = null;
                    boolean enabled;
                    boolean allAuto = false;
                    int icon = 0;
                    if (position == privateRow) {
                        text = getString(R.string.NotificationsPrivateChats);
                        exceptions = exceptionUsers;
                        offUntil = preferences.getInt("EnableAll2", 0);
                        icon = R.drawable.msg_openprofile;
                    } else if (position == groupRow) {
                        text = getString(R.string.NotificationsGroups);
                        exceptions = exceptionChats;
                        offUntil = preferences.getInt("EnableGroup2", 0);
                        icon = R.drawable.msg_groups;
                    } else if (position == storiesRow) {
                        text = getString(R.string.NotificationStories);
                        exceptions = exceptionStories;
                        autoExceptions = exceptionAutoStories;
                        offUntil = preferences.getBoolean("EnableAllStories", false) ? 0 : Integer.MAX_VALUE;
                        icon = R.drawable.msg_menu_stories;
                    } else if (position == reactionsRow) {
                        text = getString(R.string.NotificationReactions);
                        exceptions = null;
                        autoExceptions = null;
                        offUntil = preferences.getBoolean("EnableReactionsMessages", true) || preferences.getBoolean("EnableReactionsStories", true) ? 0 : Integer.MAX_VALUE;
                        icon = R.drawable.msg_reactions;
                    } else {
                        text = getString(R.string.NotificationsChannels);
                        exceptions = exceptionChannels;
                        offUntil = preferences.getInt("EnableChannel2", 0);
                        icon = R.drawable.msg_channel;
                    }
                    int iconType;
                    if (enabled = offUntil < currentTime) {
                        iconType = 0;
                    } else if (offUntil - 60 * 60 * 24 * 365 >= currentTime) {
                        iconType = 0;
                    } else {
                        iconType = 2;
                    }
                    StringBuilder builder = new StringBuilder();
                    if (position == reactionsRow) {
                        if (offUntil > 0) {
                            enabled = false;
                            builder.append(getString("NotificationsOff", R.string.NotificationsOff));
                        } else {
                            enabled = true;
                            if (preferences.getBoolean("EnableReactionsMessages", true)) {
                                builder.append(getString(R.string.NotificationReactionsMessages));
                            }
                            if (preferences.getBoolean("EnableReactionsStories", true)) {
                                if (builder.length() > 0) {
                                    builder.append(", ");
                                }
                                builder.append(getString(R.string.NotificationReactionsStories));
                            }
                        }
                    } else if (exceptions != null && !exceptions.isEmpty()) {
                        if (enabled = offUntil < currentTime) {
                            builder.append(getString("NotificationsOn", R.string.NotificationsOn));
                        } else if (offUntil - 60 * 60 * 24 * 365 >= currentTime) {
                            builder.append(getString("NotificationsOff", R.string.NotificationsOff));
                        } else {
                            builder.append(LocaleController.formatString("NotificationsOffUntil", R.string.NotificationsOffUntil, LocaleController.stringForMessageListDate(offUntil)));
                        }
                        if (builder.length() != 0) {
                            builder.append(", ");
                        }
                        int exceptionsCount = exceptions.size();
                        if (position == storiesRow && !preferences.contains("EnableAllStories") && autoExceptions != null) {
                            exceptionsCount += autoExceptions.size();
                        }
                        builder.append(LocaleController.formatPluralString("Exception", exceptionsCount));
                    } else if (autoExceptions != null && !autoExceptions.isEmpty()) {
                        if (offUntil > 0) {
                            builder.append(getString("NotificationsOff", R.string.NotificationsOff));
                        } else {
                            builder.append(getString("NotificationsOn", R.string.NotificationsOn));
                        }
                        if (autoExceptions != null && !autoExceptions.isEmpty() && !preferences.contains("EnableAllStories")) {
                            builder.append(", ");
                            builder.append(LocaleController.formatPluralString("AutoException", autoExceptions.size()));
                        }
                    } else {
                        builder.append(getString("TapToChange", R.string.TapToChange));
                    }
                    checkCell.setTextAndValueAndIconAndCheck(text, builder, icon, enabled, iconType, false, position != reactionsRow);
                    break;
                }
                case 4: {
                    if (position == resetNotificationsSectionRow) {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 5: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    if (position == callsRingtoneRow) {
                        String value = preferences.getString("CallsRingtone", getString("DefaultRingtone", R.string.DefaultRingtone));
                        if (value.equals("NoSound")) {
                            value = getString("NoSound", R.string.NoSound);
                        }
                        textCell.setTextAndValue(getString("VoipSettingsRingtone", R.string.VoipSettingsRingtone), value, updateRingtone, false);
                        updateRingtone = false;
                    } else if (position == callsVibrateRow) {
                        int value = preferences.getInt("vibrate_calls", 0);
                        if (value == 0) {
                            textCell.setTextAndValue(getString("Vibrate", R.string.Vibrate), getString("VibrationDefault", R.string.VibrationDefault), updateVibrate, true);
                        } else if (value == 1) {
                            textCell.setTextAndValue(getString("Vibrate", R.string.Vibrate), getString("Short", R.string.Short), updateVibrate, true);
                        } else if (value == 2) {
                            textCell.setTextAndValue(getString("Vibrate", R.string.Vibrate), getString("VibrationDisabled", R.string.VibrationDisabled), updateVibrate, true);
                        } else if (value == 3) {
                            textCell.setTextAndValue(getString("Vibrate", R.string.Vibrate), getString("Long", R.string.Long), updateVibrate, true);
                        } else if (value == 4) {
                            textCell.setTextAndValue(getString("Vibrate", R.string.Vibrate), getString("OnlyIfSilent", R.string.OnlyIfSilent), updateVibrate, true);
                        }
                        updateVibrate = false;
                    } else if (position == repeatRow) {
                        int minutes = preferences.getInt("repeat_messages", 60);
                        String value;
                        if (minutes == 0) {
                            value = getString("RepeatNotificationsNever", R.string.RepeatNotificationsNever);
                        } else if (minutes < 60) {
                            value = LocaleController.formatPluralString("Minutes", minutes);
                        } else {
                            value = LocaleController.formatPluralString("Hours", minutes / 60);
                        }
                        textCell.setTextAndValue(getString("RepeatNotifications", R.string.RepeatNotifications), value, updateRepeatNotifications, false);
                        updateRepeatNotifications = false;
                    }
                    break;
                }
                case 6: {
                    TextInfoPrivacyCell textCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == accountsInfoRow) {
                        textCell.setText(getString("ShowNotificationsForInfo", R.string.ShowNotificationsForInfo));
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == eventsSectionRow || position == otherSectionRow || position == resetSectionRow ||
                    position == callsSectionRow || position == badgeNumberSection || position == inappSectionRow ||
                    position == notificationsSectionRow || position == accountsSectionRow) {
                return 0;
            } else if (position == inappSoundRow || position == inappVibrateRow || position == notificationsServiceConnectionRow ||
                    position == inappPreviewRow || position == contactJoinedRow || position == pinnedMessageRow ||
                    position == notificationsServiceRow || position == badgeNumberMutedRow || position == badgeNumberMessagesRow ||
                    position == badgeNumberShowRow || position == inappPriorityRow || position == inchatSoundRow ||
                    position == androidAutoAlertRow || position == accountsAllRow) {
                return 1;
            } else if (position == resetNotificationsRow) {
                return 2;
            } else if (position == privateRow || position == groupRow || position == channelsRow || position == storiesRow || position == reactionsRow) {
                return 3;
            } else if (position == eventsSection2Row || position == notificationsSection2Row || position == otherSection2Row ||
                    position == resetSection2Row || position == callsSection2Row || position == badgeNumberSection2Row ||
                    position == resetNotificationsSectionRow) {
                return 4;
            } else if (position == accountsInfoRow) {
                return 6;
            } else {
                return 5;
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, TextCheckCell.class, TextDetailSettingsCell.class, TextSettingsCell.class, NotificationsCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LINKCOLOR, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteLinkText));

        return themeDescriptions;
    }
}
