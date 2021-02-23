/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.content.FileProvider;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.util.SparseIntArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.support.SparseLongArray;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.BubbleActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PopupNotificationActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class NotificationsController extends BaseController {

    public static final String EXTRA_VOICE_REPLY = "extra_voice_reply";
    public static String OTHER_NOTIFICATIONS_CHANNEL = null;

    private static DispatchQueue notificationsQueue = new DispatchQueue("notificationsQueue");
    private ArrayList<MessageObject> pushMessages = new ArrayList<>();
    private ArrayList<MessageObject> delayedPushMessages = new ArrayList<>();
    private LongSparseArray<MessageObject> pushMessagesDict = new LongSparseArray<>();
    private LongSparseArray<MessageObject> fcmRandomMessagesDict = new LongSparseArray<>();
    private LongSparseArray<Point> smartNotificationsDialogs = new LongSparseArray<>();
    private static NotificationManagerCompat notificationManager = null;
    private static NotificationManager systemNotificationManager = null;
    private LongSparseArray<Integer> pushDialogs = new LongSparseArray<>();
    private LongSparseArray<Integer> wearNotificationsIds = new LongSparseArray<>();
    private LongSparseArray<Integer> lastWearNotifiedMessageId = new LongSparseArray<>();
    private LongSparseArray<Integer> pushDialogsOverrideMention = new LongSparseArray<>();
    public ArrayList<MessageObject> popupMessages = new ArrayList<>();
    public ArrayList<MessageObject> popupReplyMessages = new ArrayList<>();
    private HashSet<Long> openedInBubbleDialogs = new HashSet<>();
    private long opened_dialog_id = 0;
    private int lastButtonId = 5000;
    private int total_unread_count = 0;
    private int personal_count = 0;
    private boolean notifyCheck = false;
    private int lastOnlineFromOtherDevice = 0;
    private boolean inChatSoundEnabled;
    private int lastBadgeCount = -1;
    private String launcherClassName;

    private Boolean groupsCreated;

    public static long globalSecretChatId = -(1L << 32);

    public boolean showBadgeNumber;
    public boolean showBadgeMuted;
    public boolean showBadgeMessages;

    private Runnable notificationDelayRunnable;
    private PowerManager.WakeLock notificationDelayWakelock;

    private long lastSoundPlay;
    private long lastSoundOutPlay;
    private SoundPool soundPool;
    private int soundIn;
    private int soundOut;
    private int soundRecord;
    private boolean soundInLoaded;
    private boolean soundOutLoaded;
    private boolean soundRecordLoaded;
    protected static AudioManager audioManager;
    private AlarmManager alarmManager;

    private int notificationId;
    private String notificationGroup;

    static {
        if (Build.VERSION.SDK_INT >= 26 && ApplicationLoader.applicationContext != null) {
            notificationManager = NotificationManagerCompat.from(ApplicationLoader.applicationContext);
            systemNotificationManager = (NotificationManager) ApplicationLoader.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
            checkOtherNotificationsChannel();
        }
        audioManager = (AudioManager) ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE);
    }
    
    private static volatile NotificationsController[] Instance = new NotificationsController[UserConfig.MAX_ACCOUNT_COUNT];

    public static NotificationsController getInstance(int num) {
        NotificationsController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (NotificationsController.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new NotificationsController(num);
                }
            }
        }
        return localInstance;
    }

    public NotificationsController(int instance) {
        super(instance);
        
        notificationId = currentAccount + 1;
        notificationGroup = "messages" + (currentAccount == 0 ? "" : currentAccount);
        SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
        inChatSoundEnabled = preferences.getBoolean("EnableInChatSound", true);
        showBadgeNumber = preferences.getBoolean("badgeNumber", true);
        showBadgeMuted = preferences.getBoolean("badgeNumberMuted", false);
        showBadgeMessages = preferences.getBoolean("badgeNumberMessages", true);

        notificationManager = NotificationManagerCompat.from(ApplicationLoader.applicationContext);
        systemNotificationManager = (NotificationManager) ApplicationLoader.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);

        try {
            audioManager = (AudioManager) ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE);
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            alarmManager = (AlarmManager) ApplicationLoader.applicationContext.getSystemService(Context.ALARM_SERVICE);
        } catch (Exception e) {
            FileLog.e(e);
        }

        try {
            PowerManager pm = (PowerManager) ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
            notificationDelayWakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "telegram:notification_delay_lock");
            notificationDelayWakelock.setReferenceCounted(false);
        } catch (Exception e) {
            FileLog.e(e);
        }

        notificationDelayRunnable = () -> {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("delay reached");
            }
            if (!delayedPushMessages.isEmpty()) {
                showOrUpdateNotification(true);
                delayedPushMessages.clear();
            }
            try {
                if (notificationDelayWakelock.isHeld()) {
                    notificationDelayWakelock.release();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        };
    }

    public static void checkOtherNotificationsChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        SharedPreferences preferences = null;
        if (OTHER_NOTIFICATIONS_CHANNEL == null) {
            preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
            OTHER_NOTIFICATIONS_CHANNEL = preferences.getString("OtherKey", "Other3");
        }
        NotificationChannel notificationChannel = systemNotificationManager.getNotificationChannel(OTHER_NOTIFICATIONS_CHANNEL);
        if (notificationChannel != null && notificationChannel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
            systemNotificationManager.deleteNotificationChannel(OTHER_NOTIFICATIONS_CHANNEL);
            OTHER_NOTIFICATIONS_CHANNEL = null;
            notificationChannel = null;
        }
        if (OTHER_NOTIFICATIONS_CHANNEL == null) {
            if (preferences == null) {
                preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
            }
            OTHER_NOTIFICATIONS_CHANNEL = "Other" + Utilities.random.nextLong();
            preferences.edit().putString("OtherKey", OTHER_NOTIFICATIONS_CHANNEL).commit();
        }
        if (notificationChannel == null) {
            notificationChannel = new NotificationChannel(OTHER_NOTIFICATIONS_CHANNEL, "Internal notifications", NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.enableLights(false);
            notificationChannel.enableVibration(false);
            notificationChannel.setSound(null, null);
            try {
                systemNotificationManager.createNotificationChannel(notificationChannel);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public void cleanup() {
        popupMessages.clear();
        popupReplyMessages.clear();
        notificationsQueue.postRunnable(() -> {
            opened_dialog_id = 0;
            total_unread_count = 0;
            personal_count = 0;
            pushMessages.clear();
            pushMessagesDict.clear();
            fcmRandomMessagesDict.clear();
            pushDialogs.clear();
            wearNotificationsIds.clear();
            lastWearNotifiedMessageId.clear();
            openedInBubbleDialogs.clear();
            delayedPushMessages.clear();
            notifyCheck = false;
            lastBadgeCount = 0;
            try {
                if (notificationDelayWakelock.isHeld()) {
                    notificationDelayWakelock.release();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            dismissNotification();
            setBadge(getTotalAllUnreadCount());
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
            SharedPreferences.Editor editor = preferences.edit();
            editor.clear();
            editor.commit();

            if (Build.VERSION.SDK_INT >= 26) {
                try {
                    systemNotificationManager.deleteNotificationChannelGroup("channels" + currentAccount);
                    systemNotificationManager.deleteNotificationChannelGroup("groups" + currentAccount);
                    systemNotificationManager.deleteNotificationChannelGroup("private" + currentAccount);
                    systemNotificationManager.deleteNotificationChannelGroup("other" + currentAccount);

                    String keyStart = currentAccount + "channel";
                    List<NotificationChannel> list = systemNotificationManager.getNotificationChannels();
                    int count = list.size();
                    for (int a = 0; a < count; a++) {
                        NotificationChannel channel = list.get(a);
                        String id = channel.getId();
                        if (id.startsWith(keyStart)) {
                            try {
                                systemNotificationManager.deleteNotificationChannel(id);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("delete channel cleanup " + id);
                            }
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        });
    }

    public void setInChatSoundEnabled(boolean value) {
        inChatSoundEnabled = value;
    }

    public void setOpenedDialogId(final long dialog_id) {
        notificationsQueue.postRunnable(() -> opened_dialog_id = dialog_id);
    }

    public void setOpenedInBubble(final long dialog_id, boolean opened) {
        notificationsQueue.postRunnable(() -> {
            if (opened) {
                openedInBubbleDialogs.add(dialog_id);
            } else {
                openedInBubbleDialogs.remove(dialog_id);
            }
        });
    }

    public void setLastOnlineFromOtherDevice(final int time) {
        notificationsQueue.postRunnable(() -> {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("set last online from other device = " + time);
            }
            lastOnlineFromOtherDevice = time;
        });
    }

    public void removeNotificationsForDialog(long did) {
        processReadMessages(null, did, 0, Integer.MAX_VALUE, false);
        LongSparseArray<Integer> dialogsToUpdate = new LongSparseArray<>();
        dialogsToUpdate.put(did, 0);
        processDialogsUpdateRead(dialogsToUpdate);
    }

    public boolean hasMessagesToReply() {
        for (int a = 0; a < pushMessages.size(); a++) {
            MessageObject messageObject = pushMessages.get(a);
            long dialog_id = messageObject.getDialogId();
            if (messageObject.messageOwner.mentioned && messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPinMessage ||
                    (int) dialog_id == 0 || messageObject.messageOwner.peer_id.channel_id != 0 && !messageObject.isSupergroup()) {
                continue;
            }
            return true;
        }
        return false;
    }

    protected void forceShowPopupForReply() {
        notificationsQueue.postRunnable(() -> {
            final ArrayList<MessageObject> popupArray = new ArrayList<>();
            for (int a = 0; a < pushMessages.size(); a++) {
                MessageObject messageObject = pushMessages.get(a);
                long dialog_id = messageObject.getDialogId();
                if (messageObject.messageOwner.mentioned && messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPinMessage ||
                        (int) dialog_id == 0 || messageObject.messageOwner.peer_id.channel_id != 0 && !messageObject.isSupergroup()) {
                    continue;
                }
                popupArray.add(0, messageObject);
            }
            if (!popupArray.isEmpty() && !AndroidUtilities.needShowPasscode() && !SharedConfig.isWaitingForPasscodeEnter) {
                AndroidUtilities.runOnUIThread(() -> {
                    popupReplyMessages = popupArray;
                    Intent popupIntent = new Intent(ApplicationLoader.applicationContext, PopupNotificationActivity.class);
                    popupIntent.putExtra("force", true);
                    popupIntent.putExtra("currentAccount", currentAccount);
                    popupIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_FROM_BACKGROUND);
                    ApplicationLoader.applicationContext.startActivity(popupIntent);
                    Intent it = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
                    ApplicationLoader.applicationContext.sendBroadcast(it);
                });
            }
        });
    }

    public void removeDeletedMessagesFromNotifications(final SparseArray<ArrayList<Integer>> deletedMessages) {
        final ArrayList<MessageObject> popupArrayRemove = new ArrayList<>(0);
        notificationsQueue.postRunnable(() -> {
            int old_unread_count = total_unread_count;
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
            for (int a = 0; a < deletedMessages.size(); a++) {
                int key = deletedMessages.keyAt(a);
                ArrayList<Integer> mids = deletedMessages.get(key);
                for (int b = 0; b < mids.size(); b++) {
                    long mid = mids.get(b);
                    if (key != 0) {
                        mid |= ((long) key) << 32;
                    }
                    MessageObject messageObject = pushMessagesDict.get(mid);
                    if (messageObject != null) {
                        long dialogId = messageObject.getDialogId();
                        Integer currentCount = pushDialogs.get(dialogId);
                        if (currentCount == null) {
                            currentCount = 0;
                        }
                        Integer newCount = currentCount - 1;
                        if (newCount <= 0) {
                            newCount = 0;
                            smartNotificationsDialogs.remove(dialogId);
                        }
                        if (!newCount.equals(currentCount)) {
                            total_unread_count -= currentCount;
                            total_unread_count += newCount;
                            pushDialogs.put(dialogId, newCount);
                        }
                        if (newCount == 0) {
                            pushDialogs.remove(dialogId);
                            pushDialogsOverrideMention.remove(dialogId);
                        }

                        pushMessagesDict.remove(mid);
                        delayedPushMessages.remove(messageObject);
                        pushMessages.remove(messageObject);
                        if (isPersonalMessage(messageObject)) {
                            personal_count--;
                        }
                        popupArrayRemove.add(messageObject);
                    }
                }
            }
            if (!popupArrayRemove.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> {
                    for (int a = 0, size = popupArrayRemove.size(); a < size; a++) {
                        popupMessages.remove(popupArrayRemove.get(a));
                    }
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pushMessagesUpdated);
                });
            }
            if (old_unread_count != total_unread_count) {
                if (!notifyCheck) {
                    delayedPushMessages.clear();
                    showOrUpdateNotification(notifyCheck);
                } else {
                    scheduleNotificationDelay(lastOnlineFromOtherDevice > getConnectionsManager().getCurrentTime());
                }
                final int pushDialogsCount = pushDialogs.size();
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.notificationsCountUpdated, currentAccount);
                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsUnreadCounterChanged, pushDialogsCount);
                });
            }
            notifyCheck = false;
            if (showBadgeNumber) {
                setBadge(getTotalAllUnreadCount());
            }
        });
    }

    public void removeDeletedHisoryFromNotifications(final SparseIntArray deletedMessages) {
        final ArrayList<MessageObject> popupArrayRemove = new ArrayList<>(0);
        notificationsQueue.postRunnable(() -> {
            int old_unread_count = total_unread_count;
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();

            for (int a = 0; a < deletedMessages.size(); a++) {
                int key = deletedMessages.keyAt(a);
                long dialog_id = -key;
                int id = deletedMessages.get(key);
                Integer currentCount = pushDialogs.get(dialog_id);
                if (currentCount == null) {
                    currentCount = 0;
                }
                Integer newCount = currentCount;

                for (int c = 0; c < pushMessages.size(); c++) {
                    MessageObject messageObject = pushMessages.get(c);
                    if (messageObject.getDialogId() == dialog_id && messageObject.getId() <= id) {
                        pushMessagesDict.remove(messageObject.getIdWithChannel());
                        delayedPushMessages.remove(messageObject);
                        pushMessages.remove(messageObject);
                        c--;
                        if (isPersonalMessage(messageObject)) {
                            personal_count--;
                        }
                        popupArrayRemove.add(messageObject);
                        newCount--;
                    }
                }

                if (newCount <= 0) {
                    newCount = 0;
                    smartNotificationsDialogs.remove(dialog_id);
                }
                if (!newCount.equals(currentCount)) {
                    total_unread_count -= currentCount;
                    total_unread_count += newCount;
                    pushDialogs.put(dialog_id, newCount);
                }
                if (newCount == 0) {
                    pushDialogs.remove(dialog_id);
                    pushDialogsOverrideMention.remove(dialog_id);
                }
            }
            if (popupArrayRemove.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> {
                    for (int a = 0, size = popupArrayRemove.size(); a < size; a++) {
                        popupMessages.remove(popupArrayRemove.get(a));
                    }
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pushMessagesUpdated);
                });
            }
            if (old_unread_count != total_unread_count) {
                if (!notifyCheck) {
                    delayedPushMessages.clear();
                    showOrUpdateNotification(notifyCheck);
                } else {
                    scheduleNotificationDelay(lastOnlineFromOtherDevice > getConnectionsManager().getCurrentTime());
                }
                final int pushDialogsCount = pushDialogs.size();
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.notificationsCountUpdated, currentAccount);
                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsUnreadCounterChanged, pushDialogsCount);
                });
            }
            notifyCheck = false;
            if (showBadgeNumber) {
                setBadge(getTotalAllUnreadCount());
            }
        });
    }

    public void processReadMessages(final SparseLongArray inbox, final long dialog_id, final int max_date, final int max_id, final boolean isPopup) {
        final ArrayList<MessageObject> popupArrayRemove = new ArrayList<>(0);
        notificationsQueue.postRunnable(() -> {
            if (inbox != null) {
                for (int b = 0; b < inbox.size(); b++) {
                    int key = inbox.keyAt(b);
                    long messageId = inbox.get(key);
                    for (int a = 0; a < pushMessages.size(); a++) {
                        MessageObject messageObject = pushMessages.get(a);
                        if (!messageObject.messageOwner.from_scheduled && messageObject.getDialogId() == key && messageObject.getId() <= (int) messageId) {
                            if (isPersonalMessage(messageObject)) {
                                personal_count--;
                            }
                            popupArrayRemove.add(messageObject);
                            long mid = messageObject.getId();
                            if (messageObject.messageOwner.peer_id.channel_id != 0) {
                                mid |= ((long) messageObject.messageOwner.peer_id.channel_id) << 32;
                            }
                            pushMessagesDict.remove(mid);
                            delayedPushMessages.remove(messageObject);
                            pushMessages.remove(a);
                            a--;
                        }
                    }
                }
            }
            if (dialog_id != 0 && (max_id != 0 || max_date != 0)) {
                for (int a = 0; a < pushMessages.size(); a++) {
                    MessageObject messageObject = pushMessages.get(a);
                    if (messageObject.getDialogId() == dialog_id) {
                        boolean remove = false;
                        if (max_date != 0) {
                            if (messageObject.messageOwner.date <= max_date) {
                                remove = true;
                            }
                        } else {
                            if (!isPopup) {
                                if (messageObject.getId() <= max_id || max_id < 0) {
                                    remove = true;
                                }
                            } else {
                                if (messageObject.getId() == max_id || max_id < 0) {
                                    remove = true;
                                }
                            }
                        }
                        if (remove) {
                            if (isPersonalMessage(messageObject)) {
                                personal_count--;
                            }
                            pushMessages.remove(a);
                            delayedPushMessages.remove(messageObject);
                            popupArrayRemove.add(messageObject);
                            long mid = messageObject.getId();
                            if (messageObject.messageOwner.peer_id.channel_id != 0) {
                                mid |= ((long) messageObject.messageOwner.peer_id.channel_id) << 32;
                            }
                            pushMessagesDict.remove(mid);
                            a--;
                        }
                    }
                }
            }
            if (!popupArrayRemove.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> {
                    for (int a = 0, size = popupArrayRemove.size(); a < size; a++) {
                        popupMessages.remove(popupArrayRemove.get(a));
                    }
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pushMessagesUpdated);
                });
            }
        });
    }

    private int addToPopupMessages(final ArrayList<MessageObject> popupArrayAdd, MessageObject messageObject, int lower_id, long dialog_id, boolean isChannel, SharedPreferences preferences) {
        int popup = 0;
        if (lower_id != 0) {
            if (preferences.getBoolean("custom_" + dialog_id, false)) {
                popup = preferences.getInt("popup_" + dialog_id, 0);
            }
            if (popup == 0) {
                if (isChannel) {
                    popup = preferences.getInt("popupChannel", 0);
                } else {
                    popup = preferences.getInt((int) dialog_id < 0 ? "popupGroup" : "popupAll", 0);
                }
            } else if (popup == 1) {
                popup = 3;
            } else if (popup == 2) {
                popup = 0;
            }
        }
        if (popup != 0 && messageObject.messageOwner.peer_id.channel_id != 0 && !messageObject.isSupergroup()) {
            popup = 0;
        }
        if (popup != 0) {
            popupArrayAdd.add(0, messageObject);
        }
        return popup;
    }

    public void processEditedMessages(final LongSparseArray<ArrayList<MessageObject>> editedMessages) {
        if (editedMessages.size() == 0) {
            return;
        }
        final ArrayList<MessageObject> popupArrayAdd = new ArrayList<>(0);
        notificationsQueue.postRunnable(() -> {
            boolean updated = false;
            for (int a = 0, N = editedMessages.size(); a < N; a++) {
                long did = editedMessages.keyAt(a);
                if (pushDialogs.indexOfKey(did) < 0) {
                    continue;
                }
                ArrayList<MessageObject> messages = editedMessages.valueAt(a);
                for (int b = 0, N2 = messages.size(); b < N2; b++) {
                    MessageObject messageObject = messages.get(b);
                    long mid = messageObject.getId();
                    if (messageObject.messageOwner.peer_id.channel_id != 0) {
                        mid |= ((long) messageObject.messageOwner.peer_id.channel_id) << 32;
                    }
                    MessageObject oldMessage = pushMessagesDict.get(mid);
                    if (oldMessage != null) {
                        updated = true;
                        pushMessagesDict.put(mid, messageObject);
                        int idx = pushMessages.indexOf(oldMessage);
                        if (idx >= 0) {
                            pushMessages.set(idx, messageObject);
                        }
                        idx = delayedPushMessages.indexOf(oldMessage);
                        if (idx >= 0) {
                            delayedPushMessages.set(idx, messageObject);
                        }
                    }
                }
            }
            if (updated) {
                showOrUpdateNotification(false);
            }
        });
    }

    public void processNewMessages(final ArrayList<MessageObject> messageObjects, final boolean isLast, final boolean isFcm, CountDownLatch countDownLatch) {
        if (messageObjects.isEmpty()) {
            if (countDownLatch != null) {
                countDownLatch.countDown();
            }
            return;
        }
        final ArrayList<MessageObject> popupArrayAdd = new ArrayList<>(0);
        notificationsQueue.postRunnable(() -> {
            boolean added = false;
            boolean edited = false;

            LongSparseArray<Boolean> settingsCache = new LongSparseArray<>();
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
            boolean allowPinned = preferences.getBoolean("PinnedMessages", true);
            int popup = 0;
            boolean hasScheduled = false;

            for (int a = 0; a < messageObjects.size(); a++) {
                MessageObject messageObject = messageObjects.get(a);
                if (messageObject.messageOwner != null && (messageObject.isImportedForward() || messageObject.messageOwner.silent && (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionContactSignUp || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserJoined))) {
                    continue;
                }
                long mid = messageObject.getId();
                long random_id = messageObject.isFcmMessage() ? messageObject.messageOwner.random_id : 0;
                long dialog_id = messageObject.getDialogId();
                int lower_id = (int) dialog_id;
                boolean isChannel;
                if (messageObject.isFcmMessage()) {
                    isChannel = messageObject.localChannel;
                } else if (lower_id < 0) {
                    TLRPC.Chat chat = getMessagesController().getChat(-lower_id);
                    isChannel = ChatObject.isChannel(chat) && !chat.megagroup;
                } else {
                    isChannel = false;
                }
                if (messageObject.messageOwner.peer_id.channel_id != 0) {
                    mid |= ((long) messageObject.messageOwner.peer_id.channel_id) << 32;
                }

                MessageObject oldMessageObject = pushMessagesDict.get(mid);
                if (oldMessageObject == null && messageObject.messageOwner.random_id != 0) {
                    oldMessageObject = fcmRandomMessagesDict.get(messageObject.messageOwner.random_id);
                    if (oldMessageObject != null) {
                        fcmRandomMessagesDict.remove(messageObject.messageOwner.random_id);
                    }
                }
                if (oldMessageObject != null) {
                    if (oldMessageObject.isFcmMessage()) {
                        pushMessagesDict.put(mid, messageObject);
                        int idxOld = pushMessages.indexOf(oldMessageObject);
                        if (idxOld >= 0) {
                            pushMessages.set(idxOld, messageObject);
                            popup = addToPopupMessages(popupArrayAdd, messageObject, lower_id, dialog_id, isChannel, preferences);
                        }
                        if (isFcm && (edited = messageObject.localEdit)) {
                            getMessagesStorage().putPushMessage(messageObject);
                        }
                    }
                    continue;
                }
                if (edited) {
                    continue;
                }
                if (isFcm) {
                    getMessagesStorage().putPushMessage(messageObject);
                }

                long original_dialog_id = dialog_id;
                if (dialog_id == opened_dialog_id && ApplicationLoader.isScreenOn) {
                    if (!isFcm) {
                        playInChatSound();
                    }
                    continue;
                }
                if (messageObject.messageOwner.mentioned) {
                    if (!allowPinned && messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPinMessage) {
                        continue;
                    }
                    dialog_id = messageObject.getFromChatId();
                }
                if (isPersonalMessage(messageObject)) {
                    personal_count++;
                }
                added = true;

                boolean isChat = lower_id < 0;
                int index = settingsCache.indexOfKey(dialog_id);
                boolean value;
                if (index >= 0) {
                    value = settingsCache.valueAt(index);
                } else {
                    int notifyOverride = getNotifyOverride(preferences, dialog_id);
                    if (notifyOverride == -1) {
                        value = isGlobalNotificationsEnabled(dialog_id, isChannel);
                        /*if (BuildVars.DEBUG_PRIVATE_VERSION && BuildVars.LOGS_ENABLED) {
                            FileLog.d("global notify settings for " + dialog_id + " = " + value);
                        }*/
                    } else {
                        value = notifyOverride != 2;
                    }

                    settingsCache.put(dialog_id, value);
                }

                if (value) {
                    if (!isFcm) {
                        popup = addToPopupMessages(popupArrayAdd, messageObject, lower_id, dialog_id, isChannel, preferences);
                    }
                    if (!hasScheduled) {
                        hasScheduled = messageObject.messageOwner.from_scheduled;
                    }
                    delayedPushMessages.add(messageObject);
                    pushMessages.add(0, messageObject);
                    if (mid != 0) {
                        pushMessagesDict.put(mid, messageObject);
                    } else if (random_id != 0) {
                        fcmRandomMessagesDict.put(random_id, messageObject);
                    }
                    if (original_dialog_id != dialog_id) {
                        Integer current = pushDialogsOverrideMention.get(original_dialog_id);
                        pushDialogsOverrideMention.put(original_dialog_id, current == null ? 1 : current + 1);
                    }
                }
            }

            if (added) {
                notifyCheck = isLast;
            }

            if (!popupArrayAdd.isEmpty() && !AndroidUtilities.needShowPasscode() && !SharedConfig.isWaitingForPasscodeEnter) {
                final int popupFinal = popup;
                AndroidUtilities.runOnUIThread(() -> {
                    popupMessages.addAll(0, popupArrayAdd);
                    if (ApplicationLoader.mainInterfacePaused || !ApplicationLoader.isScreenOn) {
                        if (popupFinal == 3 || popupFinal == 1 && ApplicationLoader.isScreenOn || popupFinal == 2 && !ApplicationLoader.isScreenOn) {
                            Intent popupIntent = new Intent(ApplicationLoader.applicationContext, PopupNotificationActivity.class);
                            popupIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_FROM_BACKGROUND);
                            try {
                                ApplicationLoader.applicationContext.startActivity(popupIntent);
                            } catch (Throwable ignore) {

                            }
                        }
                    }
                });
            }
            if (isFcm || hasScheduled) {
                if (edited) {
                    delayedPushMessages.clear();
                    showOrUpdateNotification(notifyCheck);
                } else if (added) {
                    MessageObject messageObject = messageObjects.get(0);
                    long dialog_id = messageObject.getDialogId();
                    Boolean isChannel;
                    if (messageObject.isFcmMessage()) {
                        isChannel = messageObject.localChannel;
                    } else {
                        isChannel = null;
                    }
                    int old_unread_count = total_unread_count;

                    int notifyOverride = getNotifyOverride(preferences, dialog_id);
                    boolean canAddValue;
                    if (notifyOverride == -1) {
                        canAddValue = isGlobalNotificationsEnabled(dialog_id, isChannel);
                        /*if (BuildVars.DEBUG_PRIVATE_VERSION && BuildVars.LOGS_ENABLED) {
                            FileLog.d("global notify settings for " + dialog_id + " = " + canAddValue);
                        }*/
                    } else {
                        canAddValue = notifyOverride != 2;
                    }

                    Integer currentCount = pushDialogs.get(dialog_id);
                    int newCount = currentCount != null ? currentCount + 1 : 1;

                    if (notifyCheck && !canAddValue) {
                        Integer override = pushDialogsOverrideMention.get(dialog_id);
                        if (override != null && override != 0) {
                            canAddValue = true;
                            newCount = override;
                        }
                    }

                    if (canAddValue) {
                        if (currentCount != null) {
                            total_unread_count -= currentCount;
                        }
                        total_unread_count += newCount;
                        pushDialogs.put(dialog_id, newCount);
                    }
                    if (old_unread_count != total_unread_count) {
                        delayedPushMessages.clear();
                        showOrUpdateNotification(notifyCheck);
                        final int pushDialogsCount = pushDialogs.size();
                        AndroidUtilities.runOnUIThread(() -> {
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.notificationsCountUpdated, currentAccount);
                            getNotificationCenter().postNotificationName(NotificationCenter.dialogsUnreadCounterChanged, pushDialogsCount);
                        });
                    }
                    notifyCheck = false;
                    if (showBadgeNumber) {
                        setBadge(getTotalAllUnreadCount());
                    }
                }
            }
            if (countDownLatch != null) {
                countDownLatch.countDown();
            }
        });
    }

    public int getTotalUnreadCount() {
        return total_unread_count;
    }

    public void processDialogsUpdateRead(final LongSparseArray<Integer> dialogsToUpdate) {
        final ArrayList<MessageObject> popupArrayToRemove = new ArrayList<>();
        notificationsQueue.postRunnable(() -> {
            int old_unread_count = total_unread_count;
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
            for (int b = 0; b < dialogsToUpdate.size(); b++) {
                long dialogId = dialogsToUpdate.keyAt(b);
                Integer currentCount = pushDialogs.get(dialogId);
                Integer newCount = dialogsToUpdate.get(dialogId);

                int lowerId = (int) dialogId;
                if (lowerId < 0) {
                    TLRPC.Chat chat = getMessagesController().getChat(-lowerId);
                    if (chat == null || chat.min || ChatObject.isNotInChat(chat)) {
                        newCount = 0;
                    }
                }

                int notifyOverride = getNotifyOverride(preferences, dialogId);
                boolean canAddValue;
                if (notifyOverride == -1) {
                    canAddValue = isGlobalNotificationsEnabled(dialogId);
                } else {
                    canAddValue = notifyOverride != 2;
                }

                if (notifyCheck && !canAddValue) {
                    Integer override = pushDialogsOverrideMention.get(dialogId);
                    if (override != null && override != 0) {
                        canAddValue = true;
                        newCount = override;
                    }
                }

                if (newCount == 0) {
                    smartNotificationsDialogs.remove(dialogId);
                }

                if (newCount < 0) {
                    if (currentCount == null) {
                        continue;
                    }
                    newCount = currentCount + newCount;
                }
                if (canAddValue || newCount == 0) {
                    if (currentCount != null) {
                        total_unread_count -= currentCount;
                    }
                }
                if (newCount == 0) {
                    pushDialogs.remove(dialogId);
                    pushDialogsOverrideMention.remove(dialogId);
                    for (int a = 0; a < pushMessages.size(); a++) {
                        MessageObject messageObject = pushMessages.get(a);
                        if (!messageObject.messageOwner.from_scheduled && messageObject.getDialogId() == dialogId) {
                            if (isPersonalMessage(messageObject)) {
                                personal_count--;
                            }
                            pushMessages.remove(a);
                            a--;
                            delayedPushMessages.remove(messageObject);
                            long mid = messageObject.getId();
                            if (messageObject.messageOwner.peer_id.channel_id != 0) {
                                mid |= ((long) messageObject.messageOwner.peer_id.channel_id) << 32;
                            }
                            pushMessagesDict.remove(mid);
                            popupArrayToRemove.add(messageObject);
                        }
                    }
                } else if (canAddValue) {
                    total_unread_count += newCount;
                    pushDialogs.put(dialogId, newCount);
                }
            }
            if (!popupArrayToRemove.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> {
                    for (int a = 0, size = popupArrayToRemove.size(); a < size; a++) {
                        popupMessages.remove(popupArrayToRemove.get(a));
                    }
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pushMessagesUpdated);
                });
            }
            if (old_unread_count != total_unread_count) {
                if (!notifyCheck) {
                    delayedPushMessages.clear();
                    showOrUpdateNotification(notifyCheck);
                } else {
                    scheduleNotificationDelay(lastOnlineFromOtherDevice > getConnectionsManager().getCurrentTime());
                }
                final int pushDialogsCount = pushDialogs.size();
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.notificationsCountUpdated, currentAccount);
                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsUnreadCounterChanged, pushDialogsCount);
                });
            }
            notifyCheck = false;
            if (showBadgeNumber) {
                setBadge(getTotalAllUnreadCount());
            }
        });
    }

    public void processLoadedUnreadMessages(final LongSparseArray<Integer> dialogs, final ArrayList<TLRPC.Message> messages, ArrayList<MessageObject> push, final ArrayList<TLRPC.User> users, final ArrayList<TLRPC.Chat> chats, final ArrayList<TLRPC.EncryptedChat> encryptedChats) {
        getMessagesController().putUsers(users, true);
        getMessagesController().putChats(chats, true);
        getMessagesController().putEncryptedChats(encryptedChats, true);

        notificationsQueue.postRunnable(() -> {
            pushDialogs.clear();
            pushMessages.clear();
            pushMessagesDict.clear();
            total_unread_count = 0;
            personal_count = 0;
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
            LongSparseArray<Boolean> settingsCache = new LongSparseArray<>();

            if (messages != null) {
                for (int a = 0; a < messages.size(); a++) {
                    TLRPC.Message message = messages.get(a);
                    if (message != null && (message.fwd_from != null && message.fwd_from.imported || message.silent && (message.action instanceof TLRPC.TL_messageActionContactSignUp || message.action instanceof TLRPC.TL_messageActionUserJoined))) {
                        continue;
                    }
                    long mid = message.id;
                    if (message.peer_id.channel_id != 0) {
                        mid |= ((long) message.peer_id.channel_id) << 32;
                    }
                    if (pushMessagesDict.indexOfKey(mid) >= 0) {
                        continue;
                    }
                    MessageObject messageObject = new MessageObject(currentAccount, message, false, false);
                    if (isPersonalMessage(messageObject)) {
                        personal_count++;
                    }
                    long dialog_id = messageObject.getDialogId();
                    long original_dialog_id = dialog_id;
                    if (messageObject.messageOwner.mentioned) {
                        dialog_id = messageObject.getFromChatId();
                    }
                    int index = settingsCache.indexOfKey(dialog_id);
                    boolean value;
                    if (index >= 0) {
                        value = settingsCache.valueAt(index);
                    } else {
                        int notifyOverride = getNotifyOverride(preferences, dialog_id);
                        if (notifyOverride == -1) {
                            value = isGlobalNotificationsEnabled(dialog_id);
                        } else {
                            value = notifyOverride != 2;
                        }
                        settingsCache.put(dialog_id, value);
                    }
                    if (!value || dialog_id == opened_dialog_id && ApplicationLoader.isScreenOn) {
                        continue;
                    }
                    pushMessagesDict.put(mid, messageObject);
                    pushMessages.add(0, messageObject);
                    if (original_dialog_id != dialog_id) {
                        Integer current = pushDialogsOverrideMention.get(original_dialog_id);
                        pushDialogsOverrideMention.put(original_dialog_id, current == null ? 1 : current + 1);
                    }
                }
            }
            for (int a = 0; a < dialogs.size(); a++) {
                long dialog_id = dialogs.keyAt(a);
                int index = settingsCache.indexOfKey(dialog_id);
                boolean value;
                if (index >= 0) {
                    value = settingsCache.valueAt(index);
                } else {
                    int notifyOverride = getNotifyOverride(preferences, dialog_id);
                    if (notifyOverride == -1) {
                        value = isGlobalNotificationsEnabled(dialog_id);
                    } else {
                        value = notifyOverride != 2;
                    }

                    /*if (!value) {
                        Integer override = pushDialogsOverrideMention.get(dialog_id);
                        if (override != null && override != 0) {
                            value = true;
                            newCount = override;
                        }
                    }*/

                    settingsCache.put(dialog_id, value);
                }
                if (!value) {
                    continue;
                }
                int count = dialogs.valueAt(a);
                pushDialogs.put(dialog_id, count);
                total_unread_count += count;
            }

            if (push != null) {
                for (int a = 0; a < push.size(); a++) {
                    MessageObject messageObject = push.get(a);
                    long mid = messageObject.getId();
                    if (messageObject.messageOwner.peer_id.channel_id != 0) {
                        mid |= ((long) messageObject.messageOwner.peer_id.channel_id) << 32;
                    }
                    if (pushMessagesDict.indexOfKey(mid) >= 0) {
                        continue;
                    }
                    if (isPersonalMessage(messageObject)) {
                        personal_count++;
                    }
                    long dialog_id = messageObject.getDialogId();
                    long original_dialog_id = dialog_id;
                    long random_id = messageObject.messageOwner.random_id;
                    if (messageObject.messageOwner.mentioned) {
                        dialog_id = messageObject.getFromChatId();
                    }
                    int index = settingsCache.indexOfKey(dialog_id);
                    boolean value;
                    if (index >= 0) {
                        value = settingsCache.valueAt(index);
                    } else {
                        int notifyOverride = getNotifyOverride(preferences, dialog_id);
                        if (notifyOverride == -1) {
                            value = isGlobalNotificationsEnabled(dialog_id);
                        } else {
                            value = notifyOverride != 2;
                        }
                        settingsCache.put(dialog_id, value);
                    }
                    if (!value || dialog_id == opened_dialog_id && ApplicationLoader.isScreenOn) {
                        continue;
                    }
                    if (mid != 0) {
                        pushMessagesDict.put(mid, messageObject);
                    } else if (random_id != 0) {
                        fcmRandomMessagesDict.put(random_id, messageObject);
                    }
                    pushMessages.add(0, messageObject);
                    if (original_dialog_id != dialog_id) {
                        Integer current = pushDialogsOverrideMention.get(original_dialog_id);
                        pushDialogsOverrideMention.put(original_dialog_id, current == null ? 1 : current + 1);
                    }

                    Integer currentCount = pushDialogs.get(dialog_id);
                    int newCount = currentCount != null ? currentCount + 1 : 1;

                    if (currentCount != null) {
                        total_unread_count -= currentCount;
                    }
                    total_unread_count += newCount;
                    pushDialogs.put(dialog_id, newCount);
                }
            }

            final int pushDialogsCount = pushDialogs.size();
            AndroidUtilities.runOnUIThread(() -> {
                if (total_unread_count == 0) {
                    popupMessages.clear();
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pushMessagesUpdated);
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.notificationsCountUpdated, currentAccount);
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsUnreadCounterChanged, pushDialogsCount);
            });
            showOrUpdateNotification(SystemClock.elapsedRealtime() / 1000 < 60);

            if (showBadgeNumber) {
                setBadge(getTotalAllUnreadCount());
            }
        });
    }

    private int getTotalAllUnreadCount() {
        int count = 0;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                NotificationsController controller = getInstance(a);
                if (controller.showBadgeNumber) {
                    if (controller.showBadgeMessages) {
                        if (controller.showBadgeMuted) {
                            try {
                                for (int i = 0, N = MessagesController.getInstance(a).allDialogs.size(); i < N; i++) {
                                    TLRPC.Dialog dialog = MessagesController.getInstance(a).allDialogs.get(i);
                                    int lowerId = (int) dialog.id;
                                    if (lowerId < 0) {
                                        TLRPC.Chat chat = getMessagesController().getChat(-lowerId);
                                        if (ChatObject.isNotInChat(chat)) {
                                            continue;
                                        }
                                    }
                                    if (dialog.unread_count != 0) {
                                        count += dialog.unread_count;
                                    }
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        } else {
                            count += controller.total_unread_count;
                        }
                    } else {
                        if (controller.showBadgeMuted) {
                            try {
                                for (int i = 0, N = MessagesController.getInstance(a).allDialogs.size(); i < N; i++) {
                                    TLRPC.Dialog dialog = MessagesController.getInstance(a).allDialogs.get(i);
                                    int lowerId = (int) dialog.id;
                                    if (lowerId < 0) {
                                        TLRPC.Chat chat = getMessagesController().getChat(-lowerId);
                                        if (ChatObject.isNotInChat(chat)) {
                                            continue;
                                        }
                                    }
                                    if (dialog.unread_count != 0) {
                                        count++;
                                    }
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        } else {
                            count += controller.pushDialogs.size();
                        }
                    }
                }
            }
        }
        return count;
    }

    public void updateBadge() {
        notificationsQueue.postRunnable(() -> setBadge(getTotalAllUnreadCount()));
    }

    private void setBadge(final int count) {
        if (lastBadgeCount == count) {
            return;
        }
        lastBadgeCount = count;
        NotificationBadge.applyCount(count);
    }

    private String getShortStringForMessage(MessageObject messageObject, String[] userName, boolean[] preview) {
        if (AndroidUtilities.needShowPasscode() || SharedConfig.isWaitingForPasscodeEnter) {
            return LocaleController.getString("NotificationHiddenMessage", R.string.NotificationHiddenMessage);
        }
        long dialogId = messageObject.messageOwner.dialog_id;
        int chat_id = messageObject.messageOwner.peer_id.chat_id != 0 ? messageObject.messageOwner.peer_id.chat_id : messageObject.messageOwner.peer_id.channel_id;
        int fromId = messageObject.messageOwner.peer_id.user_id;
        if (preview != null) {
            preview[0] = true;
        }
        SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
        boolean dialogPreviewEnabled = preferences.getBoolean("content_preview_" + dialogId, true);
        if (messageObject.isFcmMessage()) {
            if (chat_id == 0 && fromId != 0) {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
                    userName[0] = messageObject.localName;
                }
                if (!dialogPreviewEnabled || !preferences.getBoolean("EnablePreviewAll", true)) {
                    if (preview != null) {
                        preview[0] = false;
                    }
                    return LocaleController.getString("Message", R.string.Message);
                }
            } else if (chat_id != 0) {
                if (messageObject.messageOwner.peer_id.channel_id == 0 || messageObject.isSupergroup()) {
                    userName[0] = messageObject.localUserName;
                } else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
                    userName[0] = messageObject.localName;
                }
                if (!dialogPreviewEnabled || !messageObject.localChannel && !preferences.getBoolean("EnablePreviewGroup", true) || messageObject.localChannel && !preferences.getBoolean("EnablePreviewChannel", true)) {
                    if (preview != null) {
                        preview[0] = false;
                    }
                    if (messageObject.messageOwner.peer_id.channel_id != 0 && !messageObject.isSupergroup()) {
                        return LocaleController.formatString("ChannelMessageNoText", R.string.ChannelMessageNoText, messageObject.localName);
                    } else {
                        return LocaleController.formatString("NotificationMessageGroupNoText", R.string.NotificationMessageGroupNoText, messageObject.localUserName, messageObject.localName);
                    }
                }
            }
            return messageObject.messageOwner.message;
        }
        int selfUsedId = getUserConfig().getClientUserId();
        if (fromId == 0) {
            fromId = messageObject.getFromChatId();
            if (fromId == 0) {
                fromId = -chat_id;
            }
        } else if (fromId == selfUsedId) {
            fromId = messageObject.getFromChatId();
        }

        if (dialogId == 0) {
            if (chat_id != 0) {
                dialogId = -chat_id;
            } else if (fromId != 0) {
                dialogId = fromId;
            }
        }

        String name = null;
        if (UserObject.isReplyUser(dialogId) && messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.from_id != null) {
            fromId = MessageObject.getPeerId(messageObject.messageOwner.fwd_from.from_id);
        }
        if (fromId > 0) {
            TLRPC.User user = getMessagesController().getUser(fromId);
            if (user != null) {
                name = UserObject.getUserName(user);
                if (chat_id != 0) {
                    userName[0] = name;
                } else {
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
                        userName[0] = name;
                    } else {
                        userName[0] = null;
                    }
                }
            }
        } else {
            TLRPC.Chat chat = getMessagesController().getChat(-fromId);
            if (chat != null) {
                name = chat.title;
                userName[0] = name;
            }
        }
        if (name != null && fromId > 0 && UserObject.isReplyUser(dialogId) && messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.saved_from_peer != null) {
            int id = MessageObject.getPeerId(messageObject.messageOwner.fwd_from.saved_from_peer);
            if (id < 0) {
                TLRPC.Chat chat = getMessagesController().getChat(-id);
                if (chat != null) {
                    name += " @ " + chat.title;
                    if (userName[0] != null) {
                        userName[0] = name;
                    }
                }
            }
        }

        if (name == null) {
            return null;
        }
        TLRPC.Chat chat = null;
        if (chat_id != 0) {
            chat = getMessagesController().getChat(chat_id);
            if (chat == null) {
                return null;
            } else if (ChatObject.isChannel(chat) && !chat.megagroup) {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
                    userName[0] = null;
                }
            }
        }

        String msg = null;
        if ((int) dialogId == 0) {
            userName[0] = null;
            return LocaleController.getString("NotificationHiddenMessage", R.string.NotificationHiddenMessage);
        } else {
            boolean isChannel = ChatObject.isChannel(chat) && !chat.megagroup;
            if (dialogPreviewEnabled && (chat_id == 0 && fromId != 0 && preferences.getBoolean("EnablePreviewAll", true) || chat_id != 0 && (!isChannel && preferences.getBoolean("EnablePreviewGroup", true) || isChannel && preferences.getBoolean("EnablePreviewChannel", true)))) {
                if (messageObject.messageOwner instanceof TLRPC.TL_messageService) {
                    userName[0] = null;
                    if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGeoProximityReached) {
                        return messageObject.messageText.toString();
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserJoined || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionContactSignUp) {
                        return LocaleController.formatString("NotificationContactJoined", R.string.NotificationContactJoined, name);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                        return LocaleController.formatString("NotificationContactNewPhoto", R.string.NotificationContactNewPhoto, name);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionLoginUnknownLocation) {
                        String date = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.getInstance().formatterYear.format(((long) messageObject.messageOwner.date) * 1000), LocaleController.getInstance().formatterDay.format(((long) messageObject.messageOwner.date) * 1000));
                        return LocaleController.formatString("NotificationUnrecognizedDevice", R.string.NotificationUnrecognizedDevice, getUserConfig().getCurrentUser().first_name, date, messageObject.messageOwner.action.title, messageObject.messageOwner.action.address);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGameScore || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPaymentSent) {
                        return messageObject.messageText.toString();
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPhoneCall) {
                        if (messageObject.messageOwner.action.video) {
                            return LocaleController.getString("CallMessageVideoIncomingMissed", R.string.CallMessageVideoIncomingMissed);
                        } else {
                            return LocaleController.getString("CallMessageIncomingMissed", R.string.CallMessageIncomingMissed);
                        }
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatAddUser) {
                        int singleUserId = messageObject.messageOwner.action.user_id;
                        if (singleUserId == 0 && messageObject.messageOwner.action.users.size() == 1) {
                            singleUserId = messageObject.messageOwner.action.users.get(0);
                        }
                        if (singleUserId != 0) {
                            if (messageObject.messageOwner.peer_id.channel_id != 0 && !chat.megagroup) {
                                return LocaleController.formatString("ChannelAddedByNotification", R.string.ChannelAddedByNotification, name, chat.title);
                            } else {
                                if (singleUserId == selfUsedId) {
                                    return LocaleController.formatString("NotificationInvitedToGroup", R.string.NotificationInvitedToGroup, name, chat.title);
                                } else {
                                    TLRPC.User u2 = getMessagesController().getUser(singleUserId);
                                    if (u2 == null) {
                                        return null;
                                    }
                                    if (fromId == u2.id) {
                                        if (chat.megagroup) {
                                            return LocaleController.formatString("NotificationGroupAddSelfMega", R.string.NotificationGroupAddSelfMega, name, chat.title);
                                        } else {
                                            return LocaleController.formatString("NotificationGroupAddSelf", R.string.NotificationGroupAddSelf, name, chat.title);
                                        }
                                    } else {
                                        return LocaleController.formatString("NotificationGroupAddMember", R.string.NotificationGroupAddMember, name, chat.title, UserObject.getUserName(u2));
                                    }
                                }
                            }
                        } else {
                            StringBuilder names = new StringBuilder();
                            for (int a = 0; a < messageObject.messageOwner.action.users.size(); a++) {
                                TLRPC.User user = getMessagesController().getUser(messageObject.messageOwner.action.users.get(a));
                                if (user != null) {
                                    String name2 = UserObject.getUserName(user);
                                    if (names.length() != 0) {
                                        names.append(", ");
                                    }
                                    names.append(name2);
                                }
                            }
                            return LocaleController.formatString("NotificationGroupAddMember", R.string.NotificationGroupAddMember, name, chat.title, names.toString());
                        }
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGroupCall) {
                        return LocaleController.formatString("NotificationGroupCreatedCall", R.string.NotificationGroupCreatedCall, name, chat.title);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionInviteToGroupCall) {
                        int singleUserId = messageObject.messageOwner.action.user_id;
                        if (singleUserId == 0 && messageObject.messageOwner.action.users.size() == 1) {
                            singleUserId = messageObject.messageOwner.action.users.get(0);
                        }
                        if (singleUserId != 0) {
                            if (singleUserId == selfUsedId) {
                                return LocaleController.formatString("NotificationGroupInvitedYouToCall", R.string.NotificationGroupInvitedYouToCall, name, chat.title);
                            } else {
                                TLRPC.User u2 = getMessagesController().getUser(singleUserId);
                                if (u2 == null) {
                                    return null;
                                }
                                return LocaleController.formatString("NotificationGroupInvitedToCall", R.string.NotificationGroupInvitedToCall, name, chat.title, UserObject.getUserName(u2));
                            }
                        } else {
                            StringBuilder names = new StringBuilder();
                            for (int a = 0; a < messageObject.messageOwner.action.users.size(); a++) {
                                TLRPC.User user = getMessagesController().getUser(messageObject.messageOwner.action.users.get(a));
                                if (user != null) {
                                    String name2 = UserObject.getUserName(user);
                                    if (names.length() != 0) {
                                        names.append(", ");
                                    }
                                    names.append(name2);
                                }
                            }
                            return LocaleController.formatString("NotificationGroupInvitedToCall", R.string.NotificationGroupInvitedToCall, name, chat.title, names.toString());
                        }
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatJoinedByLink) {
                        return LocaleController.formatString("NotificationInvitedToGroupByLink", R.string.NotificationInvitedToGroupByLink, name, chat.title);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatEditTitle) {
                        return LocaleController.formatString("NotificationEditedGroupName", R.string.NotificationEditedGroupName, name, messageObject.messageOwner.action.title);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatEditPhoto || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
                        if (messageObject.messageOwner.peer_id.channel_id != 0 && !chat.megagroup) {
                            if (messageObject.isVideoAvatar()) {
                                return LocaleController.formatString("ChannelVideoEditNotification", R.string.ChannelVideoEditNotification, chat.title);
                            } else {
                                return LocaleController.formatString("ChannelPhotoEditNotification", R.string.ChannelPhotoEditNotification, chat.title);
                            }
                        } else {
                            if (messageObject.isVideoAvatar()) {
                                return LocaleController.formatString("NotificationEditedGroupVideo", R.string.NotificationEditedGroupVideo, name, chat.title);
                            } else {
                                return LocaleController.formatString("NotificationEditedGroupPhoto", R.string.NotificationEditedGroupPhoto, name, chat.title);
                            }
                        }
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                        if (messageObject.messageOwner.action.user_id == selfUsedId) {
                            return LocaleController.formatString("NotificationGroupKickYou", R.string.NotificationGroupKickYou, name, chat.title);
                        } else if (messageObject.messageOwner.action.user_id == fromId) {
                            return LocaleController.formatString("NotificationGroupLeftMember", R.string.NotificationGroupLeftMember, name, chat.title);
                        } else {
                            TLRPC.User u2 = getMessagesController().getUser(messageObject.messageOwner.action.user_id);
                            if (u2 == null) {
                                return null;
                            }
                            return LocaleController.formatString("NotificationGroupKickMember", R.string.NotificationGroupKickMember, name, chat.title, UserObject.getUserName(u2));
                        }
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatCreate) {
                        return messageObject.messageText.toString();
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChannelCreate) {
                        return messageObject.messageText.toString();
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatMigrateTo) {
                        return LocaleController.formatString("ActionMigrateFromGroupNotify", R.string.ActionMigrateFromGroupNotify, chat.title);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChannelMigrateFrom) {
                        return LocaleController.formatString("ActionMigrateFromGroupNotify", R.string.ActionMigrateFromGroupNotify, messageObject.messageOwner.action.title);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionScreenshotTaken) {
                        return messageObject.messageText.toString();
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPinMessage) {
                        if (chat != null && (!ChatObject.isChannel(chat) || chat.megagroup)) {
                            if (messageObject.replyMessageObject == null) {
                                return LocaleController.formatString("NotificationActionPinnedNoText", R.string.NotificationActionPinnedNoText, name, chat.title);
                            } else {
                                MessageObject object = messageObject.replyMessageObject;
                                if (object.isMusic()) {
                                    return LocaleController.formatString("NotificationActionPinnedMusic", R.string.NotificationActionPinnedMusic, name, chat.title);
                                } else if (object.isVideo()) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83D\uDCF9 " + object.messageOwner.message;
                                        return LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, name, message, chat.title);
                                    } else {
                                        return LocaleController.formatString("NotificationActionPinnedVideo", R.string.NotificationActionPinnedVideo, name, chat.title);
                                    }
                                } else if (object.isGif()) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83C\uDFAC " + object.messageOwner.message;
                                        return LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, name, message, chat.title);
                                    } else {
                                        return LocaleController.formatString("NotificationActionPinnedGif", R.string.NotificationActionPinnedGif, name, chat.title);
                                    }
                                } else if (object.isVoice()) {
                                    return LocaleController.formatString("NotificationActionPinnedVoice", R.string.NotificationActionPinnedVoice, name, chat.title);
                                } else if (object.isRoundVideo()) {
                                    return LocaleController.formatString("NotificationActionPinnedRound", R.string.NotificationActionPinnedRound, name, chat.title);
                                } else if (object.isSticker() || object.isAnimatedSticker()) {
                                    String emoji = object.getStickerEmoji();
                                    if (emoji != null) {
                                        return LocaleController.formatString("NotificationActionPinnedStickerEmoji", R.string.NotificationActionPinnedStickerEmoji, name, chat.title, emoji);
                                    } else {
                                        return LocaleController.formatString("NotificationActionPinnedSticker", R.string.NotificationActionPinnedSticker, name, chat.title);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83D\uDCCE " + object.messageOwner.message;
                                        return LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, name, message, chat.title);
                                    } else {
                                        return LocaleController.formatString("NotificationActionPinnedFile", R.string.NotificationActionPinnedFile, name, chat.title);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || object.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                                    return LocaleController.formatString("NotificationActionPinnedGeo", R.string.NotificationActionPinnedGeo, name, chat.title);
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                                    return LocaleController.formatString("NotificationActionPinnedGeoLive", R.string.NotificationActionPinnedGeoLive, name, chat.title);
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                                    TLRPC.TL_messageMediaContact mediaContact = (TLRPC.TL_messageMediaContact) object.messageOwner.media;
                                    return LocaleController.formatString("NotificationActionPinnedContact2", R.string.NotificationActionPinnedContact2, name, chat.title, ContactsController.formatName(mediaContact.first_name, mediaContact.last_name));
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                                    TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) object.messageOwner.media;
                                    if (mediaPoll.poll.quiz) {
                                        return LocaleController.formatString("NotificationActionPinnedQuiz2", R.string.NotificationActionPinnedQuiz2, name, chat.title, mediaPoll.poll.question);
                                    } else {
                                        return LocaleController.formatString("NotificationActionPinnedPoll2", R.string.NotificationActionPinnedPoll2, name, chat.title, mediaPoll.poll.question);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83D\uDDBC " + object.messageOwner.message;
                                        return LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, name, message, chat.title);
                                    } else {
                                        return LocaleController.formatString("NotificationActionPinnedPhoto", R.string.NotificationActionPinnedPhoto, name, chat.title);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                                    return LocaleController.formatString("NotificationActionPinnedGame", R.string.NotificationActionPinnedGame, name, chat.title);
                                } else if (object.messageText != null && object.messageText.length() > 0) {
                                    CharSequence message = object.messageText;
                                    if (message.length() > 20) {
                                        message = message.subSequence(0, 20) + "...";
                                    }
                                    return LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, name, message, chat.title);
                                } else {
                                    return LocaleController.formatString("NotificationActionPinnedNoText", R.string.NotificationActionPinnedNoText, name, chat.title);
                                }
                            }
                        } else if (chat != null) {
                            if (messageObject.replyMessageObject == null) {
                                return LocaleController.formatString("NotificationActionPinnedNoTextChannel", R.string.NotificationActionPinnedNoTextChannel, chat.title);
                            } else {
                                MessageObject object = messageObject.replyMessageObject;
                                if (object.isMusic()) {
                                    return LocaleController.formatString("NotificationActionPinnedMusicChannel", R.string.NotificationActionPinnedMusicChannel, chat.title);
                                } else if (object.isVideo()) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83D\uDCF9 " + object.messageOwner.message;
                                        return LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, chat.title, message);
                                    } else {
                                        return LocaleController.formatString("NotificationActionPinnedVideoChannel", R.string.NotificationActionPinnedVideoChannel, chat.title);
                                    }
                                } else if (object.isGif()) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83C\uDFAC " + object.messageOwner.message;
                                        return LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, chat.title, message);
                                    } else {
                                        return LocaleController.formatString("NotificationActionPinnedGifChannel", R.string.NotificationActionPinnedGifChannel, chat.title);
                                    }
                                } else if (object.isVoice()) {
                                    return LocaleController.formatString("NotificationActionPinnedVoiceChannel", R.string.NotificationActionPinnedVoiceChannel, chat.title);
                                } else if (object.isRoundVideo()) {
                                    return LocaleController.formatString("NotificationActionPinnedRoundChannel", R.string.NotificationActionPinnedRoundChannel, chat.title);
                                } else if (object.isSticker() || object.isAnimatedSticker()) {
                                    String emoji = object.getStickerEmoji();
                                    if (emoji != null) {
                                        return LocaleController.formatString("NotificationActionPinnedStickerEmojiChannel", R.string.NotificationActionPinnedStickerEmojiChannel, chat.title, emoji);
                                    } else {
                                        return LocaleController.formatString("NotificationActionPinnedStickerChannel", R.string.NotificationActionPinnedStickerChannel, chat.title);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83D\uDCCE " + object.messageOwner.message;
                                        return LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, chat.title, message);
                                    } else {
                                        return LocaleController.formatString("NotificationActionPinnedFileChannel", R.string.NotificationActionPinnedFileChannel, chat.title);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || object.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                                    return LocaleController.formatString("NotificationActionPinnedGeoChannel", R.string.NotificationActionPinnedGeoChannel, chat.title);
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                                    return LocaleController.formatString("NotificationActionPinnedGeoLiveChannel", R.string.NotificationActionPinnedGeoLiveChannel, chat.title);
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                                    TLRPC.TL_messageMediaContact mediaContact = (TLRPC.TL_messageMediaContact) object.messageOwner.media;
                                    return LocaleController.formatString("NotificationActionPinnedContactChannel2", R.string.NotificationActionPinnedContactChannel2, chat.title, ContactsController.formatName(mediaContact.first_name, mediaContact.last_name));
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                                    TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) object.messageOwner.media;
                                    if (mediaPoll.poll.quiz) {
                                        return LocaleController.formatString("NotificationActionPinnedQuizChannel2", R.string.NotificationActionPinnedQuizChannel2, chat.title, mediaPoll.poll.question);
                                    } else {
                                        return LocaleController.formatString("NotificationActionPinnedPollChannel2", R.string.NotificationActionPinnedPollChannel2, chat.title, mediaPoll.poll.question);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83D\uDDBC " + object.messageOwner.message;
                                        return LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, chat.title, message);
                                    } else {
                                        return LocaleController.formatString("NotificationActionPinnedPhotoChannel", R.string.NotificationActionPinnedPhotoChannel, chat.title);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                                    return LocaleController.formatString("NotificationActionPinnedGameChannel", R.string.NotificationActionPinnedGameChannel, chat.title);
                                } else if (object.messageText != null && object.messageText.length() > 0) {
                                    CharSequence message = object.messageText;
                                    if (message.length() > 20) {
                                        message = message.subSequence(0, 20) + "...";
                                    }
                                    return LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, chat.title, message);
                                } else {
                                    return LocaleController.formatString("NotificationActionPinnedNoTextChannel", R.string.NotificationActionPinnedNoTextChannel, chat.title);
                                }
                            }
                        } else {
                            if (messageObject.replyMessageObject == null) {
                                return LocaleController.formatString("NotificationActionPinnedNoTextUser", R.string.NotificationActionPinnedNoTextUser, name);
                            } else {
                                MessageObject object = messageObject.replyMessageObject;
                                if (object.isMusic()) {
                                    return LocaleController.formatString("NotificationActionPinnedMusicUser", R.string.NotificationActionPinnedMusicUser, name);
                                } else if (object.isVideo()) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83D\uDCF9 " + object.messageOwner.message;
                                        return LocaleController.formatString("NotificationActionPinnedTextUser", R.string.NotificationActionPinnedTextUser, name, message);
                                    } else {
                                        return LocaleController.formatString("NotificationActionPinnedVideoUser", R.string.NotificationActionPinnedVideoUser, name);
                                    }
                                } else if (object.isGif()) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83C\uDFAC " + object.messageOwner.message;
                                        return LocaleController.formatString("NotificationActionPinnedTextUser", R.string.NotificationActionPinnedTextUser, name, message);
                                    } else {
                                        return LocaleController.formatString("NotificationActionPinnedGifUser", R.string.NotificationActionPinnedGifUser, name);
                                    }
                                } else if (object.isVoice()) {
                                    return LocaleController.formatString("NotificationActionPinnedVoiceUser", R.string.NotificationActionPinnedVoiceUser, name);
                                } else if (object.isRoundVideo()) {
                                    return LocaleController.formatString("NotificationActionPinnedRoundUser", R.string.NotificationActionPinnedRoundUser, name);
                                } else if (object.isSticker() || object.isAnimatedSticker()) {
                                    String emoji = object.getStickerEmoji();
                                    if (emoji != null) {
                                        return LocaleController.formatString("NotificationActionPinnedStickerEmojiUser", R.string.NotificationActionPinnedStickerEmojiUser, name, emoji);
                                    } else {
                                        return LocaleController.formatString("NotificationActionPinnedStickerUser", R.string.NotificationActionPinnedStickerUser, name);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83D\uDCCE " + object.messageOwner.message;
                                        return LocaleController.formatString("NotificationActionPinnedTextUser", R.string.NotificationActionPinnedTextUser, name, message);
                                    } else {
                                        return LocaleController.formatString("NotificationActionPinnedFileUser", R.string.NotificationActionPinnedFileUser, name);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || object.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                                    return LocaleController.formatString("NotificationActionPinnedGeoUser", R.string.NotificationActionPinnedGeoUser, name);
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                                    return LocaleController.formatString("NotificationActionPinnedGeoLiveUser", R.string.NotificationActionPinnedGeoLiveUser, name);
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                                    TLRPC.TL_messageMediaContact mediaContact = (TLRPC.TL_messageMediaContact) object.messageOwner.media;
                                    return LocaleController.formatString("NotificationActionPinnedContactUser", R.string.NotificationActionPinnedContactUser, name, ContactsController.formatName(mediaContact.first_name, mediaContact.last_name));
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                                    TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) object.messageOwner.media;
                                    if (mediaPoll.poll.quiz) {
                                        return LocaleController.formatString("NotificationActionPinnedQuizUser", R.string.NotificationActionPinnedQuizUser, name, mediaPoll.poll.question);
                                    } else {
                                        return LocaleController.formatString("NotificationActionPinnedPollUser", R.string.NotificationActionPinnedPollUser, name, mediaPoll.poll.question);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83D\uDDBC " + object.messageOwner.message;
                                        return LocaleController.formatString("NotificationActionPinnedTextUser", R.string.NotificationActionPinnedTextUser, name, message);
                                    } else {
                                        return LocaleController.formatString("NotificationActionPinnedPhotoUser", R.string.NotificationActionPinnedPhotoUser, name);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                                    return LocaleController.formatString("NotificationActionPinnedGameUser", R.string.NotificationActionPinnedGameUser, name);
                                } else if (object.messageText != null && object.messageText.length() > 0) {
                                    CharSequence message = object.messageText;
                                    if (message.length() > 20) {
                                        message = message.subSequence(0, 20) + "...";
                                    }
                                    return LocaleController.formatString("NotificationActionPinnedTextUser", R.string.NotificationActionPinnedTextUser, name, message);
                                } else {
                                    return LocaleController.formatString("NotificationActionPinnedNoTextUser", R.string.NotificationActionPinnedNoTextUser, name);
                                }
                            }
                        }
                    }
                } else {
                    if (messageObject.isMediaEmpty()) {
                        if (!TextUtils.isEmpty(messageObject.messageOwner.message)) {
                            return messageObject.messageOwner.message;
                        } else {
                            return LocaleController.getString("Message", R.string.Message);
                        }
                    } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                        if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                            return "\uD83D\uDDBC " + messageObject.messageOwner.message;
                        } else if (messageObject.messageOwner.media.ttl_seconds != 0) {
                            return LocaleController.getString("AttachDestructingPhoto", R.string.AttachDestructingPhoto);
                        } else {
                            return LocaleController.getString("AttachPhoto", R.string.AttachPhoto);
                        }
                    } else if (messageObject.isVideo()) {
                        if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                            return "\uD83D\uDCF9 " + messageObject.messageOwner.message;
                        } else if (messageObject.messageOwner.media.ttl_seconds != 0) {
                            return LocaleController.getString("AttachDestructingVideo", R.string.AttachDestructingVideo);
                        } else {
                            return LocaleController.getString("AttachVideo", R.string.AttachVideo);
                        }
                    } else if (messageObject.isGame()) {
                        return LocaleController.getString("AttachGame", R.string.AttachGame);
                    } else if (messageObject.isVoice()) {
                        return LocaleController.getString("AttachAudio", R.string.AttachAudio);
                    } else if (messageObject.isRoundVideo()) {
                        return LocaleController.getString("AttachRound", R.string.AttachRound);
                    } else if (messageObject.isMusic()) {
                        return LocaleController.getString("AttachMusic", R.string.AttachMusic);
                    } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                        return LocaleController.getString("AttachContact", R.string.AttachContact);
                    } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                        if (((TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media).poll.quiz) {
                            return LocaleController.getString("QuizPoll", R.string.QuizPoll);
                        } else {
                            return LocaleController.getString("Poll", R.string.Poll);
                        }
                    } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                        return LocaleController.getString("AttachLocation", R.string.AttachLocation);
                    } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                        return LocaleController.getString("AttachLiveLocation", R.string.AttachLiveLocation);
                    } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                        if (messageObject.isSticker() || messageObject.isAnimatedSticker()) {
                            String emoji = messageObject.getStickerEmoji();
                            if (emoji != null) {
                                return emoji + " " + LocaleController.getString("AttachSticker", R.string.AttachSticker);
                            } else {
                                return LocaleController.getString("AttachSticker", R.string.AttachSticker);
                            }
                        } else if (messageObject.isGif()) {
                            if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                return "\uD83C\uDFAC " + messageObject.messageOwner.message;
                            } else {
                                return LocaleController.getString("AttachGif", R.string.AttachGif);
                            }
                        } else {
                            if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                return "\uD83D\uDCCE " + messageObject.messageOwner.message;
                            } else {
                                return LocaleController.getString("AttachDocument", R.string.AttachDocument);
                            }
                        }
                    } else if (!TextUtils.isEmpty(messageObject.messageText)) {
                        return messageObject.messageText.toString();
                    } else {
                        return LocaleController.getString("Message", R.string.Message);
                    }
                }
            } else {
                if (preview != null) {
                    preview[0] = false;
                }
                return LocaleController.getString("Message", R.string.Message);
            }
        }
        return null;
    }

    private String getStringForMessage(MessageObject messageObject, boolean shortMessage, boolean[] text, boolean[] preview) {
        if (AndroidUtilities.needShowPasscode() || SharedConfig.isWaitingForPasscodeEnter) {
            return LocaleController.getString("YouHaveNewMessage", R.string.YouHaveNewMessage);
        }
        long dialog_id = messageObject.messageOwner.dialog_id;
        int chat_id = messageObject.messageOwner.peer_id.chat_id != 0 ? messageObject.messageOwner.peer_id.chat_id : messageObject.messageOwner.peer_id.channel_id;
        int from_id = messageObject.messageOwner.peer_id.user_id;
        if (preview != null) {
            preview[0] = true;
        }
        SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
        boolean dialogPreviewEnabled = preferences.getBoolean("content_preview_" + dialog_id, true);
        if (messageObject.isFcmMessage()) {
            if (chat_id == 0 && from_id != 0) {
                if (!dialogPreviewEnabled || !preferences.getBoolean("EnablePreviewAll", true)) {
                    if (preview != null) {
                        preview[0] = false;
                    }
                    return LocaleController.formatString("NotificationMessageNoText", R.string.NotificationMessageNoText, messageObject.localName);
                }
            } else if (chat_id != 0) {
                if (!dialogPreviewEnabled || !messageObject.localChannel && !preferences.getBoolean("EnablePreviewGroup", true) || messageObject.localChannel && !preferences.getBoolean("EnablePreviewChannel", true)) {
                    if (preview != null) {
                        preview[0] = false;
                    }
                    if (messageObject.messageOwner.peer_id.channel_id != 0 && !messageObject.isSupergroup()) {
                        return LocaleController.formatString("ChannelMessageNoText", R.string.ChannelMessageNoText, messageObject.localName);
                    } else {
                        return LocaleController.formatString("NotificationMessageGroupNoText", R.string.NotificationMessageGroupNoText, messageObject.localUserName, messageObject.localName);
                    }
                }
            }
            text[0] = true;
            return (String) messageObject.messageText;
        }
        int selfUsedId = getUserConfig().getClientUserId();
        if (from_id == 0) {
            from_id = messageObject.getFromChatId();
            if (from_id == 0) {
                from_id = -chat_id;
            }
        } else if (from_id == selfUsedId) {
            from_id = messageObject.getFromChatId();
        }

        if (dialog_id == 0) {
            if (chat_id != 0) {
                dialog_id = -chat_id;
            } else if (from_id != 0) {
                dialog_id = from_id;
            }
        }

        String name = null;
        if (from_id > 0) {
            if (messageObject.messageOwner.from_scheduled) {
                if (dialog_id == selfUsedId) {
                    name = LocaleController.getString("MessageScheduledReminderNotification", R.string.MessageScheduledReminderNotification);
                } else {
                    name = LocaleController.getString("NotificationMessageScheduledName", R.string.NotificationMessageScheduledName);
                }
            } else {
                TLRPC.User user = getMessagesController().getUser(from_id);
                if (user != null) {
                    name = UserObject.getUserName(user);
                }
            }
        } else {
            TLRPC.Chat chat = getMessagesController().getChat(-from_id);
            if (chat != null) {
                name = chat.title;
            }
        }

        if (name == null) {
            return null;
        }
        TLRPC.Chat chat = null;
        if (chat_id != 0) {
            chat = getMessagesController().getChat(chat_id);
            if (chat == null) {
                return null;
            }
        }

        String msg = null;
        if ((int) dialog_id == 0) {
            msg = LocaleController.getString("YouHaveNewMessage", R.string.YouHaveNewMessage);
        } else {
            if (chat_id == 0 && from_id != 0) {
                if (dialogPreviewEnabled && preferences.getBoolean("EnablePreviewAll", true)) {
                    if (messageObject.messageOwner instanceof TLRPC.TL_messageService) {
                        if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGeoProximityReached) {
                            msg = messageObject.messageText.toString();
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserJoined || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionContactSignUp) {
                            msg = LocaleController.formatString("NotificationContactJoined", R.string.NotificationContactJoined, name);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                            msg = LocaleController.formatString("NotificationContactNewPhoto", R.string.NotificationContactNewPhoto, name);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionLoginUnknownLocation) {
                            String date = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.getInstance().formatterYear.format(((long) messageObject.messageOwner.date) * 1000), LocaleController.getInstance().formatterDay.format(((long) messageObject.messageOwner.date) * 1000));
                            msg = LocaleController.formatString("NotificationUnrecognizedDevice", R.string.NotificationUnrecognizedDevice, getUserConfig().getCurrentUser().first_name, date, messageObject.messageOwner.action.title, messageObject.messageOwner.action.address);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGameScore || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPaymentSent) {
                            msg = messageObject.messageText.toString();
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPhoneCall) {
                            if (messageObject.messageOwner.action.video) {
                                msg = LocaleController.getString("CallMessageVideoIncomingMissed", R.string.CallMessageVideoIncomingMissed);
                            } else {
                                msg = LocaleController.getString("CallMessageIncomingMissed", R.string.CallMessageIncomingMissed);
                            }
                        }
                    } else {
                        if (messageObject.isMediaEmpty()) {
                            if (!shortMessage) {
                                if (!TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                    msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, messageObject.messageOwner.message);
                                    text[0] = true;
                                } else {
                                    msg = LocaleController.formatString("NotificationMessageNoText", R.string.NotificationMessageNoText, name);
                                }
                            } else {
                                msg = LocaleController.formatString("NotificationMessageNoText", R.string.NotificationMessageNoText, name);
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                            if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, "\uD83D\uDDBC " + messageObject.messageOwner.message);
                                text[0] = true;
                            } else {
                                if (messageObject.messageOwner.media.ttl_seconds != 0) {
                                    msg = LocaleController.formatString("NotificationMessageSDPhoto", R.string.NotificationMessageSDPhoto, name);
                                } else {
                                    msg = LocaleController.formatString("NotificationMessagePhoto", R.string.NotificationMessagePhoto, name);
                                }
                            }
                        } else if (messageObject.isVideo()) {
                            if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, "\uD83D\uDCF9 " + messageObject.messageOwner.message);
                                text[0] = true;
                            } else {
                                if (messageObject.messageOwner.media.ttl_seconds != 0) {
                                    msg = LocaleController.formatString("NotificationMessageSDVideo", R.string.NotificationMessageSDVideo, name);
                                } else {
                                    msg = LocaleController.formatString("NotificationMessageVideo", R.string.NotificationMessageVideo, name);
                                }
                            }
                        } else if (messageObject.isGame()) {
                            msg = LocaleController.formatString("NotificationMessageGame", R.string.NotificationMessageGame, name, messageObject.messageOwner.media.game.title);
                        } else if (messageObject.isVoice()) {
                            msg = LocaleController.formatString("NotificationMessageAudio", R.string.NotificationMessageAudio, name);
                        } else if (messageObject.isRoundVideo()) {
                            msg = LocaleController.formatString("NotificationMessageRound", R.string.NotificationMessageRound, name);
                        } else if (messageObject.isMusic()) {
                            msg = LocaleController.formatString("NotificationMessageMusic", R.string.NotificationMessageMusic, name);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                            TLRPC.TL_messageMediaContact mediaContact = (TLRPC.TL_messageMediaContact) messageObject.messageOwner.media;
                            msg = LocaleController.formatString("NotificationMessageContact2", R.string.NotificationMessageContact2, name, ContactsController.formatName(mediaContact.first_name, mediaContact.last_name));
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                            TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media;
                            if (mediaPoll.poll.quiz) {
                                msg = LocaleController.formatString("NotificationMessageQuiz2", R.string.NotificationMessageQuiz2, name, mediaPoll.poll.question);
                            } else {
                                msg = LocaleController.formatString("NotificationMessagePoll2", R.string.NotificationMessagePoll2, name, mediaPoll.poll.question);
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                            msg = LocaleController.formatString("NotificationMessageMap", R.string.NotificationMessageMap, name);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                            msg = LocaleController.formatString("NotificationMessageLiveLocation", R.string.NotificationMessageLiveLocation, name);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                            if (messageObject.isSticker() || messageObject.isAnimatedSticker()) {
                                String emoji = messageObject.getStickerEmoji();
                                if (emoji != null) {
                                    msg = LocaleController.formatString("NotificationMessageStickerEmoji", R.string.NotificationMessageStickerEmoji, name, emoji);
                                } else {
                                    msg = LocaleController.formatString("NotificationMessageSticker", R.string.NotificationMessageSticker, name);
                                }
                            } else if (messageObject.isGif()) {
                                if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                    msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, "\uD83C\uDFAC " + messageObject.messageOwner.message);
                                    text[0] = true;
                                } else {
                                    msg = LocaleController.formatString("NotificationMessageGif", R.string.NotificationMessageGif, name);
                                }
                            } else {
                                if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                    msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, "\uD83D\uDCCE " + messageObject.messageOwner.message);
                                    text[0] = true;
                                } else {
                                    msg = LocaleController.formatString("NotificationMessageDocument", R.string.NotificationMessageDocument, name);
                                }
                            }
                        } else {
                            if (!shortMessage && !TextUtils.isEmpty(messageObject.messageText)) {
                                msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, messageObject.messageText);
                                text[0] = true;
                            } else {
                                msg = LocaleController.formatString("NotificationMessageNoText", R.string.NotificationMessageNoText, name);
                            }
                        }
                    }
                } else {
                    if (preview != null) {
                        preview[0] = false;
                    }
                    msg = LocaleController.formatString("NotificationMessageNoText", R.string.NotificationMessageNoText, name);
                }
            } else if (chat_id != 0) {
                boolean isChannel = ChatObject.isChannel(chat) && !chat.megagroup;
                if (dialogPreviewEnabled && (!isChannel && preferences.getBoolean("EnablePreviewGroup", true) || isChannel && preferences.getBoolean("EnablePreviewChannel", true))) {
                    if (messageObject.messageOwner instanceof TLRPC.TL_messageService) {
                        if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatAddUser) {
                            int singleUserId = messageObject.messageOwner.action.user_id;
                            if (singleUserId == 0 && messageObject.messageOwner.action.users.size() == 1) {
                                singleUserId = messageObject.messageOwner.action.users.get(0);
                            }
                            if (singleUserId != 0) {
                                if (messageObject.messageOwner.peer_id.channel_id != 0 && !chat.megagroup) {
                                    msg = LocaleController.formatString("ChannelAddedByNotification", R.string.ChannelAddedByNotification, name, chat.title);
                                } else {
                                    if (singleUserId == selfUsedId) {
                                        msg = LocaleController.formatString("NotificationInvitedToGroup", R.string.NotificationInvitedToGroup, name, chat.title);
                                    } else {
                                        TLRPC.User u2 = getMessagesController().getUser(singleUserId);
                                        if (u2 == null) {
                                            return null;
                                        }
                                        if (from_id == u2.id) {
                                            if (chat.megagroup) {
                                                msg = LocaleController.formatString("NotificationGroupAddSelfMega", R.string.NotificationGroupAddSelfMega, name, chat.title);
                                            } else {
                                                msg = LocaleController.formatString("NotificationGroupAddSelf", R.string.NotificationGroupAddSelf, name, chat.title);
                                            }
                                        } else {
                                            msg = LocaleController.formatString("NotificationGroupAddMember", R.string.NotificationGroupAddMember, name, chat.title, UserObject.getUserName(u2));
                                        }
                                    }
                                }
                            } else {
                                StringBuilder names = new StringBuilder();
                                for (int a = 0; a < messageObject.messageOwner.action.users.size(); a++) {
                                    TLRPC.User user = getMessagesController().getUser(messageObject.messageOwner.action.users.get(a));
                                    if (user != null) {
                                        String name2 = UserObject.getUserName(user);
                                        if (names.length() != 0) {
                                            names.append(", ");
                                        }
                                        names.append(name2);
                                    }
                                }
                                msg = LocaleController.formatString("NotificationGroupAddMember", R.string.NotificationGroupAddMember, name, chat.title, names.toString());
                            }
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGroupCall) {
                            msg = LocaleController.formatString("NotificationGroupCreatedCall", R.string.NotificationGroupCreatedCall, name, chat.title);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionInviteToGroupCall) {
                            int singleUserId = messageObject.messageOwner.action.user_id;
                            if (singleUserId == 0 && messageObject.messageOwner.action.users.size() == 1) {
                                singleUserId = messageObject.messageOwner.action.users.get(0);
                            }
                            if (singleUserId != 0) {
                                if (singleUserId == selfUsedId) {
                                    msg = LocaleController.formatString("NotificationGroupInvitedYouToCall", R.string.NotificationGroupInvitedYouToCall, name, chat.title);
                                } else {
                                    TLRPC.User u2 = getMessagesController().getUser(singleUserId);
                                    if (u2 == null) {
                                        return null;
                                    }
                                    msg = LocaleController.formatString("NotificationGroupInvitedToCall", R.string.NotificationGroupInvitedToCall, name, chat.title, UserObject.getUserName(u2));
                                }
                            } else {
                                StringBuilder names = new StringBuilder();
                                for (int a = 0; a < messageObject.messageOwner.action.users.size(); a++) {
                                    TLRPC.User user = getMessagesController().getUser(messageObject.messageOwner.action.users.get(a));
                                    if (user != null) {
                                        String name2 = UserObject.getUserName(user);
                                        if (names.length() != 0) {
                                            names.append(", ");
                                        }
                                        names.append(name2);
                                    }
                                }
                                msg = LocaleController.formatString("NotificationGroupInvitedToCall", R.string.NotificationGroupInvitedToCall, name, chat.title, names.toString());
                            }
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatJoinedByLink) {
                            msg = LocaleController.formatString("NotificationInvitedToGroupByLink", R.string.NotificationInvitedToGroupByLink, name, chat.title);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatEditTitle) {
                            msg = LocaleController.formatString("NotificationEditedGroupName", R.string.NotificationEditedGroupName, name, messageObject.messageOwner.action.title);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatEditPhoto || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
                            if (messageObject.messageOwner.peer_id.channel_id != 0 && !chat.megagroup) {
                                if (messageObject.isVideoAvatar()) {
                                    msg = LocaleController.formatString("ChannelVideoEditNotification", R.string.ChannelVideoEditNotification, chat.title);
                                } else {
                                    msg = LocaleController.formatString("ChannelPhotoEditNotification", R.string.ChannelPhotoEditNotification, chat.title);
                                }
                            } else {
                                if (messageObject.isVideoAvatar()) {
                                    msg = LocaleController.formatString("NotificationEditedGroupVideo", R.string.NotificationEditedGroupVideo, name, chat.title);
                                } else {
                                    msg = LocaleController.formatString("NotificationEditedGroupPhoto", R.string.NotificationEditedGroupPhoto, name, chat.title);
                                }
                            }
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                            if (messageObject.messageOwner.action.user_id == selfUsedId) {
                                msg = LocaleController.formatString("NotificationGroupKickYou", R.string.NotificationGroupKickYou, name, chat.title);
                            } else if (messageObject.messageOwner.action.user_id == from_id) {
                                msg = LocaleController.formatString("NotificationGroupLeftMember", R.string.NotificationGroupLeftMember, name, chat.title);
                            } else {
                                TLRPC.User u2 = getMessagesController().getUser(messageObject.messageOwner.action.user_id);
                                if (u2 == null) {
                                    return null;
                                }
                                msg = LocaleController.formatString("NotificationGroupKickMember", R.string.NotificationGroupKickMember, name, chat.title, UserObject.getUserName(u2));
                            }
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatCreate) {
                            msg = messageObject.messageText.toString();
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChannelCreate) {
                            msg = messageObject.messageText.toString();
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatMigrateTo) {
                            msg = LocaleController.formatString("ActionMigrateFromGroupNotify", R.string.ActionMigrateFromGroupNotify, chat.title);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChannelMigrateFrom) {
                            msg = LocaleController.formatString("ActionMigrateFromGroupNotify", R.string.ActionMigrateFromGroupNotify, messageObject.messageOwner.action.title);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionScreenshotTaken) {
                            msg = messageObject.messageText.toString();
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPinMessage) {
                            if (!ChatObject.isChannel(chat) || chat.megagroup) {
                                if (messageObject.replyMessageObject == null) {
                                    msg = LocaleController.formatString("NotificationActionPinnedNoText", R.string.NotificationActionPinnedNoText, name, chat.title);
                                } else {
                                    MessageObject object = messageObject.replyMessageObject;
                                    if (object.isMusic()) {
                                        msg = LocaleController.formatString("NotificationActionPinnedMusic", R.string.NotificationActionPinnedMusic, name, chat.title);
                                    } else if (object.isVideo()) {
                                        if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                            String message = "\uD83D\uDCF9 " + object.messageOwner.message;
                                            msg = LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, name, message, chat.title);
                                        } else {
                                            msg = LocaleController.formatString("NotificationActionPinnedVideo", R.string.NotificationActionPinnedVideo, name, chat.title);
                                        }
                                    } else if (object.isGif()) {
                                        if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                            String message = "\uD83C\uDFAC " + object.messageOwner.message;
                                            msg = LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, name, message, chat.title);
                                        } else {
                                            msg = LocaleController.formatString("NotificationActionPinnedGif", R.string.NotificationActionPinnedGif, name, chat.title);
                                        }
                                    } else if (object.isVoice()) {
                                        msg = LocaleController.formatString("NotificationActionPinnedVoice", R.string.NotificationActionPinnedVoice, name, chat.title);
                                    } else if (object.isRoundVideo()) {
                                        msg = LocaleController.formatString("NotificationActionPinnedRound", R.string.NotificationActionPinnedRound, name, chat.title);
                                    } else if (object.isSticker() || object.isAnimatedSticker()) {
                                        String emoji = object.getStickerEmoji();
                                        if (emoji != null) {
                                            msg = LocaleController.formatString("NotificationActionPinnedStickerEmoji", R.string.NotificationActionPinnedStickerEmoji, name, chat.title, emoji);
                                        } else {
                                            msg = LocaleController.formatString("NotificationActionPinnedSticker", R.string.NotificationActionPinnedSticker, name, chat.title);
                                        }
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                                        if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                            String message = "\uD83D\uDCCE " + object.messageOwner.message;
                                            msg = LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, name, message, chat.title);
                                        } else {
                                            msg = LocaleController.formatString("NotificationActionPinnedFile", R.string.NotificationActionPinnedFile, name, chat.title);
                                        }
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || object.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                                        msg = LocaleController.formatString("NotificationActionPinnedGeo", R.string.NotificationActionPinnedGeo, name, chat.title);
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                                        msg = LocaleController.formatString("NotificationActionPinnedGeoLive", R.string.NotificationActionPinnedGeoLive, name, chat.title);
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                                        TLRPC.TL_messageMediaContact mediaContact = (TLRPC.TL_messageMediaContact) messageObject.messageOwner.media;
                                        msg = LocaleController.formatString("NotificationActionPinnedContact2", R.string.NotificationActionPinnedContact2, name, chat.title, ContactsController.formatName(mediaContact.first_name, mediaContact.last_name));
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                                        TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) object.messageOwner.media;
                                        if (mediaPoll.poll.quiz) {
                                            msg = LocaleController.formatString("NotificationActionPinnedQuiz2", R.string.NotificationActionPinnedQuiz2, name, chat.title, mediaPoll.poll.question);
                                        } else {
                                            msg = LocaleController.formatString("NotificationActionPinnedPoll2", R.string.NotificationActionPinnedPoll2, name, chat.title, mediaPoll.poll.question);
                                        }
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                                        if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                            String message = "\uD83D\uDDBC " + object.messageOwner.message;
                                            msg = LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, name, message, chat.title);
                                        } else {
                                            msg = LocaleController.formatString("NotificationActionPinnedPhoto", R.string.NotificationActionPinnedPhoto, name, chat.title);
                                        }
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                                        msg = LocaleController.formatString("NotificationActionPinnedGame", R.string.NotificationActionPinnedGame, name, chat.title);
                                    } else if (object.messageText != null && object.messageText.length() > 0) {
                                        CharSequence message = object.messageText;
                                        if (message.length() > 20) {
                                            message = message.subSequence(0, 20) + "...";
                                        }
                                        msg = LocaleController.formatString("NotificationActionPinnedText", R.string.NotificationActionPinnedText, name, message, chat.title);
                                    } else {
                                        msg = LocaleController.formatString("NotificationActionPinnedNoText", R.string.NotificationActionPinnedNoText, name, chat.title);
                                    }
                                }
                            } else {
                                if (messageObject.replyMessageObject == null) {
                                    msg = LocaleController.formatString("NotificationActionPinnedNoTextChannel", R.string.NotificationActionPinnedNoTextChannel, chat.title);
                                } else {
                                    MessageObject object = messageObject.replyMessageObject;
                                    if (object.isMusic()) {
                                        msg = LocaleController.formatString("NotificationActionPinnedMusicChannel", R.string.NotificationActionPinnedMusicChannel, chat.title);
                                    } else if (object.isVideo()) {
                                        if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                            String message = "\uD83D\uDCF9 " + object.messageOwner.message;
                                            msg = LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, chat.title, message);
                                        } else {
                                            msg = LocaleController.formatString("NotificationActionPinnedVideoChannel", R.string.NotificationActionPinnedVideoChannel, chat.title);
                                        }
                                    } else if (object.isGif()) {
                                        if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                            String message = "\uD83C\uDFAC " + object.messageOwner.message;
                                            msg = LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, chat.title, message);
                                        } else {
                                            msg = LocaleController.formatString("NotificationActionPinnedGifChannel", R.string.NotificationActionPinnedGifChannel, chat.title);
                                        }
                                    } else if (object.isVoice()) {
                                        msg = LocaleController.formatString("NotificationActionPinnedVoiceChannel", R.string.NotificationActionPinnedVoiceChannel, chat.title);
                                    } else if (object.isRoundVideo()) {
                                        msg = LocaleController.formatString("NotificationActionPinnedRoundChannel", R.string.NotificationActionPinnedRoundChannel, chat.title);
                                    } else if (object.isSticker() || object.isAnimatedSticker()) {
                                        String emoji = object.getStickerEmoji();
                                        if (emoji != null) {
                                            msg = LocaleController.formatString("NotificationActionPinnedStickerEmojiChannel", R.string.NotificationActionPinnedStickerEmojiChannel, chat.title, emoji);
                                        } else {
                                            msg = LocaleController.formatString("NotificationActionPinnedStickerChannel", R.string.NotificationActionPinnedStickerChannel, chat.title);
                                        }
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                                        if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                            String message = "\uD83D\uDCCE " + object.messageOwner.message;
                                            msg = LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, chat.title, message);
                                        } else {
                                            msg = LocaleController.formatString("NotificationActionPinnedFileChannel", R.string.NotificationActionPinnedFileChannel, chat.title);
                                        }
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || object.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                                        msg = LocaleController.formatString("NotificationActionPinnedGeoChannel", R.string.NotificationActionPinnedGeoChannel, chat.title);
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                                        msg = LocaleController.formatString("NotificationActionPinnedGeoLiveChannel", R.string.NotificationActionPinnedGeoLiveChannel, chat.title);
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                                        TLRPC.TL_messageMediaContact mediaContact = (TLRPC.TL_messageMediaContact) messageObject.messageOwner.media;
                                        msg = LocaleController.formatString("NotificationActionPinnedContactChannel2", R.string.NotificationActionPinnedContactChannel2, chat.title, ContactsController.formatName(mediaContact.first_name, mediaContact.last_name));
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                                        TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) object.messageOwner.media;
                                        if (mediaPoll.poll.quiz) {
                                            msg = LocaleController.formatString("NotificationActionPinnedQuizChannel2", R.string.NotificationActionPinnedQuizChannel2, chat.title, mediaPoll.poll.question);
                                        } else {
                                            msg = LocaleController.formatString("NotificationActionPinnedPollChannel2", R.string.NotificationActionPinnedPollChannel2, chat.title, mediaPoll.poll.question);
                                        }
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                                        if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                            String message = "\uD83D\uDDBC " + object.messageOwner.message;
                                            msg = LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, chat.title, message);
                                        } else {
                                            msg = LocaleController.formatString("NotificationActionPinnedPhotoChannel", R.string.NotificationActionPinnedPhotoChannel, chat.title);
                                        }
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                                        msg = LocaleController.formatString("NotificationActionPinnedGameChannel", R.string.NotificationActionPinnedGameChannel, chat.title);
                                    } else if (object.messageText != null && object.messageText.length() > 0) {
                                        CharSequence message = object.messageText;
                                        if (message.length() > 20) {
                                            message = message.subSequence(0, 20) + "...";
                                        }
                                        msg = LocaleController.formatString("NotificationActionPinnedTextChannel", R.string.NotificationActionPinnedTextChannel, chat.title, message);
                                    } else {
                                        msg = LocaleController.formatString("NotificationActionPinnedNoTextChannel", R.string.NotificationActionPinnedNoTextChannel, chat.title);
                                    }
                                }
                            }
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGameScore) {
                            msg = messageObject.messageText.toString();
                        }
                    } else if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        if (messageObject.isMediaEmpty()) {
                            if (!shortMessage && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, messageObject.messageOwner.message);
                                text[0] = true;
                            } else {
                                msg = LocaleController.formatString("ChannelMessageNoText", R.string.ChannelMessageNoText, name);
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                            if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, "\uD83D\uDDBC " + messageObject.messageOwner.message);
                                text[0] = true;
                            } else {
                                msg = LocaleController.formatString("ChannelMessagePhoto", R.string.ChannelMessagePhoto, name);
                            }
                        } else if (messageObject.isVideo()) {
                            if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, "\uD83D\uDCF9 " + messageObject.messageOwner.message);
                                text[0] = true;
                            } else {
                                msg = LocaleController.formatString("ChannelMessageVideo", R.string.ChannelMessageVideo, name);
                            }
                        } else if (messageObject.isVoice()) {
                            msg = LocaleController.formatString("ChannelMessageAudio", R.string.ChannelMessageAudio, name);
                        } else if (messageObject.isRoundVideo()) {
                            msg = LocaleController.formatString("ChannelMessageRound", R.string.ChannelMessageRound, name);
                        } else if (messageObject.isMusic()) {
                            msg = LocaleController.formatString("ChannelMessageMusic", R.string.ChannelMessageMusic, name);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                            TLRPC.TL_messageMediaContact mediaContact = (TLRPC.TL_messageMediaContact) messageObject.messageOwner.media;
                            msg = LocaleController.formatString("ChannelMessageContact2", R.string.ChannelMessageContact2, name, ContactsController.formatName(mediaContact.first_name, mediaContact.last_name));
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                            TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media;
                            if (mediaPoll.poll.quiz) {
                                msg = LocaleController.formatString("ChannelMessageQuiz2", R.string.ChannelMessageQuiz2, name, mediaPoll.poll.question);
                            } else {
                                msg = LocaleController.formatString("ChannelMessagePoll2", R.string.ChannelMessagePoll2, name, mediaPoll.poll.question);
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                            msg = LocaleController.formatString("ChannelMessageMap", R.string.ChannelMessageMap, name);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                            msg = LocaleController.formatString("ChannelMessageLiveLocation", R.string.ChannelMessageLiveLocation, name);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                            if (messageObject.isSticker() || messageObject.isAnimatedSticker()) {
                                String emoji = messageObject.getStickerEmoji();
                                if (emoji != null) {
                                    msg = LocaleController.formatString("ChannelMessageStickerEmoji", R.string.ChannelMessageStickerEmoji, name, emoji);
                                } else {
                                    msg = LocaleController.formatString("ChannelMessageSticker", R.string.ChannelMessageSticker, name);
                                }
                            } else if (messageObject.isGif()) {
                                if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                    msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, "\uD83C\uDFAC " + messageObject.messageOwner.message);
                                    text[0] = true;
                                } else {
                                    msg = LocaleController.formatString("ChannelMessageGIF", R.string.ChannelMessageGIF, name);
                                }
                            } else {
                                if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                    msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, "\uD83D\uDCCE " + messageObject.messageOwner.message);
                                    text[0] = true;
                                } else {
                                    msg = LocaleController.formatString("ChannelMessageDocument", R.string.ChannelMessageDocument, name);
                                }
                            }
                        } else {
                            if (!shortMessage && !TextUtils.isEmpty(messageObject.messageText)) {
                                msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, name, messageObject.messageText);
                                text[0] = true;
                            } else {
                                msg = LocaleController.formatString("ChannelMessageNoText", R.string.ChannelMessageNoText, name);
                            }
                        }
                    } else {
                        if (messageObject.isMediaEmpty()) {
                            if (!shortMessage && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                msg = LocaleController.formatString("NotificationMessageGroupText", R.string.NotificationMessageGroupText, name, chat.title, messageObject.messageOwner.message);
                            } else {
                                msg = LocaleController.formatString("NotificationMessageGroupNoText", R.string.NotificationMessageGroupNoText, name, chat.title);
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                            if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                msg = LocaleController.formatString("NotificationMessageGroupText", R.string.NotificationMessageGroupText, name, chat.title, "\uD83D\uDDBC " + messageObject.messageOwner.message);
                            } else {
                                msg = LocaleController.formatString("NotificationMessageGroupPhoto", R.string.NotificationMessageGroupPhoto, name, chat.title);
                            }
                        } else if (messageObject.isVideo()) {
                            if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                msg = LocaleController.formatString("NotificationMessageGroupText", R.string.NotificationMessageGroupText, name, chat.title, "\uD83D\uDCF9 " + messageObject.messageOwner.message);
                            } else {
                                msg = LocaleController.formatString(" ", R.string.NotificationMessageGroupVideo, name, chat.title);
                            }
                        } else if (messageObject.isVoice()) {
                            msg = LocaleController.formatString("NotificationMessageGroupAudio", R.string.NotificationMessageGroupAudio, name, chat.title);
                        } else if (messageObject.isRoundVideo()) {
                            msg = LocaleController.formatString("NotificationMessageGroupRound", R.string.NotificationMessageGroupRound, name, chat.title);
                        } else if (messageObject.isMusic()) {
                            msg = LocaleController.formatString("NotificationMessageGroupMusic", R.string.NotificationMessageGroupMusic, name, chat.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                            TLRPC.TL_messageMediaContact mediaContact = (TLRPC.TL_messageMediaContact) messageObject.messageOwner.media;
                            msg = LocaleController.formatString("NotificationMessageGroupContact2", R.string.NotificationMessageGroupContact2, name, chat.title, ContactsController.formatName(mediaContact.first_name, mediaContact.last_name));
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                            TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media;
                            if (mediaPoll.poll.quiz) {
                                msg = LocaleController.formatString("NotificationMessageGroupQuiz2", R.string.NotificationMessageGroupQuiz2, name, chat.title, mediaPoll.poll.question);
                            } else {
                                msg = LocaleController.formatString("NotificationMessageGroupPoll2", R.string.NotificationMessageGroupPoll2, name, chat.title, mediaPoll.poll.question);
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                            msg = LocaleController.formatString("NotificationMessageGroupGame", R.string.NotificationMessageGroupGame, name, chat.title, messageObject.messageOwner.media.game.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                            msg = LocaleController.formatString("NotificationMessageGroupMap", R.string.NotificationMessageGroupMap, name, chat.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                            msg = LocaleController.formatString("NotificationMessageGroupLiveLocation", R.string.NotificationMessageGroupLiveLocation, name, chat.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                            if (messageObject.isSticker() || messageObject.isAnimatedSticker()) {
                                String emoji = messageObject.getStickerEmoji();
                                if (emoji != null) {
                                    msg = LocaleController.formatString("NotificationMessageGroupStickerEmoji", R.string.NotificationMessageGroupStickerEmoji, name, chat.title, emoji);
                                } else {
                                    msg = LocaleController.formatString("NotificationMessageGroupSticker", R.string.NotificationMessageGroupSticker, name, chat.title);
                                }
                            } else if (messageObject.isGif()) {
                                if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                    msg = LocaleController.formatString("NotificationMessageGroupText", R.string.NotificationMessageGroupText, name, chat.title, "\uD83C\uDFAC " + messageObject.messageOwner.message);
                                } else {
                                    msg = LocaleController.formatString("NotificationMessageGroupGif", R.string.NotificationMessageGroupGif, name, chat.title);
                                }
                            } else {
                                if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                    msg = LocaleController.formatString("NotificationMessageGroupText", R.string.NotificationMessageGroupText, name, chat.title, "\uD83D\uDCCE " + messageObject.messageOwner.message);
                                } else {
                                    msg = LocaleController.formatString("NotificationMessageGroupDocument", R.string.NotificationMessageGroupDocument, name, chat.title);
                                }
                            }
                        } else {
                            if (!shortMessage && !TextUtils.isEmpty(messageObject.messageText)) {
                                msg = LocaleController.formatString("NotificationMessageGroupText", R.string.NotificationMessageGroupText, name, chat.title, messageObject.messageText);
                            } else {
                                msg = LocaleController.formatString("NotificationMessageGroupNoText", R.string.NotificationMessageGroupNoText, name, chat.title);
                            }
                        }
                    }
                } else {
                    if (preview != null) {
                        preview[0] = false;
                    }
                    if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        msg = LocaleController.formatString("ChannelMessageNoText", R.string.ChannelMessageNoText, name);
                    } else {
                        msg = LocaleController.formatString("NotificationMessageGroupNoText", R.string.NotificationMessageGroupNoText, name, chat.title);
                    }
                }
            }
        }
        return msg;
    }

    private void scheduleNotificationRepeat() {
        try {
            Intent intent = new Intent(ApplicationLoader.applicationContext, NotificationRepeat.class);
            intent.putExtra("currentAccount", currentAccount);
            PendingIntent pintent = PendingIntent.getService(ApplicationLoader.applicationContext, 0, intent, 0);
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
            int minutes = preferences.getInt("repeat_messages", 60);
            if (minutes > 0 && personal_count > 0) {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + minutes * 60 * 1000, pintent);
            } else {
                alarmManager.cancel(pintent);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private boolean isPersonalMessage(MessageObject messageObject) {
        return messageObject.messageOwner.peer_id != null && messageObject.messageOwner.peer_id.chat_id == 0 && messageObject.messageOwner.peer_id.channel_id == 0
                && (messageObject.messageOwner.action == null || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionEmpty);
    }

    private int getNotifyOverride(SharedPreferences preferences, long dialog_id) {
        int notifyOverride = preferences.getInt("notify2_" + dialog_id, -1);
        if (notifyOverride == 3) {
            int muteUntil = preferences.getInt("notifyuntil_" + dialog_id, 0);
            if (muteUntil >= getConnectionsManager().getCurrentTime()) {
                notifyOverride = 2;
            }
        }
        /*if (BuildVars.LOGS_ENABLED && BuildVars.DEBUG_VERSION) {
            FileLog.d("notify override for " + dialog_id + " = " + notifyOverride);
        }*/
        return notifyOverride;
    }

    public void showNotifications() {
        notificationsQueue.postRunnable(() -> showOrUpdateNotification(false));
    }

    public void hideNotifications() {
        notificationsQueue.postRunnable(() -> {
            notificationManager.cancel(notificationId);
            lastWearNotifiedMessageId.clear();
            for (int a = 0; a < wearNotificationsIds.size(); a++) {
                notificationManager.cancel(wearNotificationsIds.valueAt(a));
            }
            wearNotificationsIds.clear();
        });
    }

    private void dismissNotification() {
        try {
            notificationManager.cancel(notificationId);
            pushMessages.clear();
            pushMessagesDict.clear();
            lastWearNotifiedMessageId.clear();
            for (int a = 0; a < wearNotificationsIds.size(); a++) {
                long did = wearNotificationsIds.keyAt(a);
                if (openedInBubbleDialogs.contains(did)) {
                    continue;
                }
                notificationManager.cancel(wearNotificationsIds.valueAt(a));
            }
            wearNotificationsIds.clear();
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pushMessagesUpdated));
            if (WearDataLayerListenerService.isWatchConnected()) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("id", getUserConfig().getClientUserId());
                    o.put("cancel_all", true);
                    WearDataLayerListenerService.sendMessageToWatch("/notify", o.toString().getBytes(), "remote_notifications");
                } catch (JSONException ignore) {
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void playInChatSound() {
        if (!inChatSoundEnabled || MediaController.getInstance().isRecordingAudio()) {
            return;
        }
        try {
            if (audioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT) {
                return;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        try {
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
            int notifyOverride = getNotifyOverride(preferences, opened_dialog_id);
            if (notifyOverride == 2) {
                return;
            }
            notificationsQueue.postRunnable(() -> {
                if (Math.abs(SystemClock.elapsedRealtime() - lastSoundPlay) <= 500) {
                    return;
                }
                try {
                    if (soundPool == null) {
                        soundPool = new SoundPool(3, AudioManager.STREAM_SYSTEM, 0);
                        soundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
                            if (status == 0) {
                                try {
                                    soundPool.play(sampleId, 1.0f, 1.0f, 1, 0, 1.0f);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            }
                        });
                    }
                    if (soundIn == 0 && !soundInLoaded) {
                        soundInLoaded = true;
                        soundIn = soundPool.load(ApplicationLoader.applicationContext, R.raw.sound_in, 1);
                    }
                    if (soundIn != 0) {
                        try {
                            soundPool.play(soundIn, 1.0f, 1.0f, 1, 0, 1.0f);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void scheduleNotificationDelay(boolean onlineReason) {
        try {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("delay notification start, onlineReason = " + onlineReason);
            }
            notificationDelayWakelock.acquire(10000);
            notificationsQueue.cancelRunnable(notificationDelayRunnable);
            notificationsQueue.postRunnable(notificationDelayRunnable, (onlineReason ? 3 * 1000 : 1000));
        } catch (Exception e) {
            FileLog.e(e);
            showOrUpdateNotification(notifyCheck);
        }
    }

    protected void repeatNotificationMaybe() {
        notificationsQueue.postRunnable(() -> {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (hour >= 11 && hour <= 22) {
                notificationManager.cancel(notificationId);
                showOrUpdateNotification(true);
            } else {
                scheduleNotificationRepeat();
            }
        });
    }

    private boolean isEmptyVibration(long[] pattern) {
        if (pattern == null || pattern.length == 0) {
            return false;
        }
        for (int a = 0; a < pattern.length; a++) {
            if (pattern[a] != 0) {
                return false;
            }
        }
        return true;
    }

    public void deleteNotificationChannel(long dialogId) {
        deleteNotificationChannel(dialogId, -1);
    }

    private void deleteNotificationChannelInternal(long dialogId, int what) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        try {
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
            SharedPreferences.Editor editor = preferences.edit();
            if (what == 0 || what == -1) {
                String key = "org.telegram.key" + dialogId;
                String channelId = preferences.getString(key, null);
                if (channelId != null) {
                    editor.remove(key).remove(key + "_s");
                    try {
                        systemNotificationManager.deleteNotificationChannel(channelId);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("delete channel internal " + channelId);
                    }
                }
            }
            if (what == 1 || what == -1) {
                String key = "org.telegram.keyia" + dialogId;
                String channelId = preferences.getString(key, null);
                if (channelId != null) {
                    editor.remove(key).remove(key + "_s");
                    try {
                        systemNotificationManager.deleteNotificationChannel(channelId);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("delete channel internal " + channelId);
                    }
                }
            }
            editor.commit();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void deleteNotificationChannel(long dialogId, int what) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        notificationsQueue.postRunnable(() -> deleteNotificationChannelInternal(dialogId, what));
    }

    public void deleteNotificationChannelGlobal(int type) {
        deleteNotificationChannelGlobal(type, -1);
    }

    public void deleteNotificationChannelGlobalInternal(int type, int what) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        try {
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
            SharedPreferences.Editor editor = preferences.edit();
            if (what == 0 || what == -1) {
                String key;
                if (type == TYPE_CHANNEL) {
                    key = "channels";
                } else if (type == TYPE_GROUP) {
                    key = "groups";
                } else {
                    key = "private";
                }
                String channelId = preferences.getString(key, null);
                if (channelId != null) {
                    editor.remove(key).remove(key + "_s");
                    try {
                        systemNotificationManager.deleteNotificationChannel(channelId);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("delete channel global internal " + channelId);
                    }
                }
            }

            if (what == 1 || what == -1) {
                String key;
                if (type == TYPE_CHANNEL) {
                    key = "channels_ia";
                } else if (type == TYPE_GROUP) {
                    key = "groups_ia";
                } else {
                    key = "private_ia";
                }
                String channelId = preferences.getString(key, null);
                if (channelId != null) {
                    editor.remove(key).remove(key + "_s");
                    try {
                        systemNotificationManager.deleteNotificationChannel(channelId);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("delete channel global internal " + channelId);
                    }
                }
            }
            String overwriteKey;
            if (type == TYPE_CHANNEL) {
                overwriteKey = "overwrite_channel";
            } else if (type == TYPE_GROUP) {
                overwriteKey = "overwrite_group";
            } else {
                overwriteKey = "overwrite_private";
            }
            editor.remove(overwriteKey);
            editor.commit();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void deleteNotificationChannelGlobal(int type, int what) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        notificationsQueue.postRunnable(() -> deleteNotificationChannelGlobalInternal(type, what));
    }

    public void deleteAllNotificationChannels() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        notificationsQueue.postRunnable(() -> {
            try {
                SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
                Map<String, ?> values = preferences.getAll();
                SharedPreferences.Editor editor = preferences.edit();
                for (Map.Entry<String, ?> entry : values.entrySet()) {
                    String key = entry.getKey();
                    if (key.startsWith("org.telegram.key")) {
                        if (!key.endsWith("_s")) {
                            String id = (String) entry.getValue();
                            systemNotificationManager.deleteNotificationChannel(id);
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("delete all channel " + id);
                            }
                        }
                        editor.remove(key);
                    }
                }
                editor.commit();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private boolean unsupportedNotificationShortcut() {
        return Build.VERSION.SDK_INT < 29 || !SharedConfig.chatBubbles;
    }

    @SuppressLint("RestrictedApi")
    private String createNotificationShortcut(NotificationCompat.Builder builder, int did, String name, TLRPC.User user, TLRPC.Chat chat, Person person) {
        if (unsupportedNotificationShortcut() || ChatObject.isChannel(chat) && !chat.megagroup) {
            return null;
        }
        try {
            String id = "ndid_" + did;
            ShortcutInfoCompat.Builder shortcutBuilder = new ShortcutInfoCompat.Builder(ApplicationLoader.applicationContext, id)
                    .setShortLabel(chat != null ? name : UserObject.getFirstName(user))
                    .setLongLabel(name)
                    .setIntent(new Intent(Intent.ACTION_DEFAULT))
                    .setLongLived(true);

            Bitmap avatar = null;
            if (person != null) {
                shortcutBuilder.setPerson(person);
                shortcutBuilder.setIcon(person.getIcon());
                if (person.getIcon() != null) {
                    avatar = person.getIcon().getBitmap();
                }
            }
            ArrayList<ShortcutInfoCompat> arrayList = new ArrayList<>(1);
            arrayList.add(shortcutBuilder.build());
            ShortcutManagerCompat.addDynamicShortcuts(ApplicationLoader.applicationContext, arrayList);
            builder.setShortcutId(id);
            NotificationCompat.BubbleMetadata.Builder bubbleBuilder = new NotificationCompat.BubbleMetadata.Builder();
            Intent intent = new Intent(ApplicationLoader.applicationContext, BubbleActivity.class);
            intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
            if (did > 0) {
                intent.putExtra("userId", did);
            } else {
                intent.putExtra("chatId", -did);
            }
            intent.putExtra("currentAccount", currentAccount);
            bubbleBuilder.setIntent(PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT));
            bubbleBuilder.setSuppressNotification(opened_dialog_id == did);
            bubbleBuilder.setAutoExpandBubble(false);
            bubbleBuilder.setDesiredHeight(AndroidUtilities.dp(640));
            if (avatar != null) {
                bubbleBuilder.setIcon(IconCompat.createWithAdaptiveBitmap(avatar));
            } else {
                if (user != null) {
                    bubbleBuilder.setIcon(IconCompat.createWithResource(ApplicationLoader.applicationContext, user.bot ? R.drawable.book_bot : R.drawable.book_user));
                } else {
                    bubbleBuilder.setIcon(IconCompat.createWithResource(ApplicationLoader.applicationContext, R.drawable.book_group));
                }
            }
            builder.setBubbleMetadata(bubbleBuilder.build());
            return id;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    @TargetApi(26)
    protected void ensureGroupsCreated() {
        SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
        if (groupsCreated == null) {
            groupsCreated = preferences.getBoolean("groupsCreated4", false);
        }
        if (!groupsCreated) {
            try {
                String keyStart = currentAccount + "channel";
                List<NotificationChannel> list = systemNotificationManager.getNotificationChannels();
                int count = list.size();
                SharedPreferences.Editor editor = null;
                for (int a = 0; a < count; a++) {
                    NotificationChannel channel = list.get(a);
                    String id = channel.getId();
                    if (id.startsWith(keyStart)) {
                        int importance = channel.getImportance();
                        if (importance != NotificationManager.IMPORTANCE_HIGH && importance != NotificationManager.IMPORTANCE_MAX) { //TODO remove after some time, 7.3.0 bug fix
                            if (id.contains("_ia_")) {
                                //do nothing
                            } else if (id.contains("_channels_")) {
                                if (editor == null) {
                                    editor = getAccountInstance().getNotificationsSettings().edit();
                                }
                                editor.remove("priority_channel").remove("vibrate_channel").remove("ChannelSoundPath").remove("ChannelSound");
                            } else if (id.contains("_groups_")) {
                                if (editor == null) {
                                    editor = getAccountInstance().getNotificationsSettings().edit();
                                }
                                editor.remove("priority_group").remove("vibrate_group").remove("GroupSoundPath").remove("GroupSound");
                            } else if (id.contains("_private_")) {
                                if (editor == null) {
                                    editor = getAccountInstance().getNotificationsSettings().edit();
                                }
                                editor.remove("priority_messages");
                                editor.remove("priority_group").remove("vibrate_messages").remove("GlobalSoundPath").remove("GlobalSound");
                            } else {
                                long dialogId = Utilities.parseLong(id.substring(9, id.indexOf('_', 9)));
                                if (dialogId != 0) {
                                    if (editor == null) {
                                        editor = getAccountInstance().getNotificationsSettings().edit();
                                    }
                                    editor.remove("priority_" + dialogId).remove("vibrate_" + dialogId).remove("sound_path_" + dialogId).remove("sound_" + dialogId);
                                }
                            }
                        }
                        systemNotificationManager.deleteNotificationChannel(id);
                    }
                }
                if (editor != null) {
                    editor.commit();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            TLRPC.User user = getMessagesController().getUser(getUserConfig().getClientUserId());
            if (user == null) {
                getUserConfig().getCurrentUser();
            }
            String userName;
            if (user != null) {
                userName = " (" + ContactsController.formatName(user.first_name, user.last_name) + ")";
            } else {
                userName = "";
            }

            systemNotificationManager.createNotificationChannelGroups(Arrays.asList(
                    new NotificationChannelGroup("channels" + currentAccount, LocaleController.getString("NotificationsChannels", R.string.NotificationsChannels) + userName),
                    new NotificationChannelGroup("groups" + currentAccount, LocaleController.getString("NotificationsGroups", R.string.NotificationsGroups) + userName),
                    new NotificationChannelGroup("private" + currentAccount, LocaleController.getString("NotificationsPrivateChats", R.string.NotificationsPrivateChats) + userName),
                    new NotificationChannelGroup("other" + currentAccount, LocaleController.getString("NotificationsOther", R.string.NotificationsOther) + userName)
            ));

            preferences.edit().putBoolean("groupsCreated4", true).commit();
            groupsCreated = true;
        }
    }

    @TargetApi(26)
    private String validateChannelId(long dialogId, String name, long[] vibrationPattern, int ledColor, Uri sound, int importance, boolean isDefault, boolean isInApp, boolean isSilent, int type) {
        ensureGroupsCreated();

        SharedPreferences preferences = getAccountInstance().getNotificationsSettings();

        String key;
        String groupId;
        String overwriteKey;
        if (isSilent) {
            groupId = "other" + currentAccount;
            overwriteKey = null;
        } else {
            if (type == TYPE_CHANNEL) {
                groupId = "channels" + currentAccount;
                overwriteKey = "overwrite_channel";
            } else if (type == TYPE_GROUP) {
                groupId = "groups" + currentAccount;
                overwriteKey = "overwrite_group";
            } else {
                groupId = "private" + currentAccount;
                overwriteKey = "overwrite_private";
            }
        }

        boolean secretChat = !isDefault && (int) dialogId == 0;
        boolean shouldOverwrite = !isInApp && overwriteKey != null && preferences.getBoolean(overwriteKey, false);

        if (isSilent) {
            name = LocaleController.getString("NotificationsSilent", R.string.NotificationsSilent);
            key = "silent";
        } else if (isDefault) {
            name = isInApp ? LocaleController.getString("NotificationsInAppDefault", R.string.NotificationsInAppDefault) : LocaleController.getString("NotificationsDefault", R.string.NotificationsDefault);
            if (type == TYPE_CHANNEL) {
                key = isInApp ? "channels_ia" : "channels";
            } else if (type == TYPE_GROUP) {
                key = isInApp ? "groups_ia" : "groups";
            } else {
                key = isInApp ? "private_ia" : "private";
            }
        } else {
            if (isInApp) {
                name = LocaleController.formatString("NotificationsChatInApp", R.string.NotificationsChatInApp, name);
            }
            key = (isInApp ? "org.telegram.keyia" : "org.telegram.key") + dialogId;
        }
        String channelId = preferences.getString(key, null);
        String settings = preferences.getString(key + "_s", null);
        boolean edited = false;
        StringBuilder newSettings = new StringBuilder();
        String newSettingsHash = null;

        if (channelId != null) {
            NotificationChannel existingChannel = systemNotificationManager.getNotificationChannel(channelId);
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("current channel for " + channelId + " = " + existingChannel);
            }
            if (existingChannel != null) {
                if (!isSilent && !shouldOverwrite) {
                    int channelImportance = existingChannel.getImportance();
                    Uri channelSound = existingChannel.getSound();
                    long[] channelVibrationPattern = existingChannel.getVibrationPattern();
                    boolean vibrate = existingChannel.shouldVibrate();
                    if (!vibrate && channelVibrationPattern == null) {
                        channelVibrationPattern = new long[]{0, 0};
                    }
                    int channelLedColor = existingChannel.getLightColor();
                    if (channelVibrationPattern != null) {
                        for (int a = 0; a < channelVibrationPattern.length; a++) {
                            newSettings.append(channelVibrationPattern[a]);
                        }
                    }
                    newSettings.append(channelLedColor);
                    if (channelSound != null) {
                        newSettings.append(channelSound.toString());
                    }
                    newSettings.append(channelImportance);
                    if (!isDefault && secretChat) {
                        newSettings.append("secret");
                    }
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("current channel settings for " + channelId + " = " + newSettings + " old = " + settings);
                    }
                    newSettingsHash = Utilities.MD5(newSettings.toString());
                    newSettings.setLength(0);
                    if (!newSettingsHash.equals(settings)) {
                        SharedPreferences.Editor editor = null;
                        if (channelImportance == NotificationManager.IMPORTANCE_NONE) {
                            editor = preferences.edit();
                            if (isDefault) {
                                if (!isInApp) {
                                    editor.putInt(getGlobalNotificationsKey(type), Integer.MAX_VALUE);
                                    updateServerNotificationsSettings(type);
                                }
                            } else {
                                editor.putInt("notify2_" + dialogId, 2);
                                updateServerNotificationsSettings(dialogId, true);
                            }
                            edited = true;
                        } else if (channelImportance != importance) {
                            if (!isInApp) {
                                editor = preferences.edit();
                                int priority;
                                if (channelImportance == NotificationManager.IMPORTANCE_HIGH || channelImportance == NotificationManager.IMPORTANCE_MAX) {
                                    priority = 1;
                                } else if (channelImportance == NotificationManager.IMPORTANCE_MIN) {
                                    priority = 4;
                                } else if (channelImportance == NotificationManager.IMPORTANCE_LOW) {
                                    priority = 5;
                                } else {
                                    priority = 0;
                                }
                                if (isDefault) {
                                    editor.putInt(getGlobalNotificationsKey(type), 0).commit();
                                    if (type == TYPE_CHANNEL) {
                                        editor.putInt("priority_channel", priority);
                                    } else if (type == TYPE_GROUP) {
                                        editor.putInt("priority_group", priority);
                                    } else {
                                        editor.putInt("priority_messages", priority);
                                    }
                                } else {
                                    editor.putInt("notify2_" + dialogId, 0);
                                    editor.remove("notifyuntil_" + dialogId);
                                    editor.putInt("priority_" + dialogId, priority);
                                }
                            }
                            edited = true;
                        }
                        if (channelSound == null && sound != null || channelSound != null && (sound == null || !TextUtils.equals(channelSound.toString(), sound.toString()))) {
                            if (!isInApp) {
                                if (editor == null) {
                                    editor = preferences.edit();
                                }
                                String newSound;
                                if (channelSound == null) {
                                    newSound = "NoSound";
                                    if (isDefault) {
                                        if (type == TYPE_CHANNEL) {
                                            editor.putString("ChannelSound", "NoSound");
                                        } else if (type == TYPE_GROUP) {
                                            editor.putString("GroupSound", "NoSound");
                                        } else {
                                            editor.putString("GlobalSound", "NoSound");
                                        }
                                    } else {
                                        editor.putString("sound_" + dialogId, "NoSound");
                                    }
                                } else {
                                    newSound = channelSound.toString();
                                    Ringtone rng = RingtoneManager.getRingtone(ApplicationLoader.applicationContext, channelSound);
                                    String ringtoneName = null;
                                    if (rng != null) {
                                        if (channelSound.equals(Settings.System.DEFAULT_RINGTONE_URI)) {
                                            ringtoneName = LocaleController.getString("DefaultRingtone", R.string.DefaultRingtone);
                                        } else {
                                            ringtoneName = rng.getTitle(ApplicationLoader.applicationContext);
                                        }
                                        rng.stop();
                                    }
                                    if (ringtoneName != null) {
                                        if (isDefault) {
                                            if (type == TYPE_CHANNEL) {
                                                editor.putString("ChannelSound", ringtoneName);
                                            } else if (type == TYPE_GROUP) {
                                                editor.putString("GroupSound", ringtoneName);
                                            } else {
                                                editor.putString("GlobalSound", ringtoneName);
                                            }
                                        } else {
                                            editor.putString("sound_" + dialogId, ringtoneName);
                                        }
                                    }
                                }
                                if (isDefault) {
                                    if (type == TYPE_CHANNEL) {
                                        editor.putString("ChannelSoundPath", newSound);
                                    } else if (type == TYPE_GROUP) {
                                        editor.putString("GroupSoundPath", newSound);
                                    } else {
                                        editor.putString("GlobalSoundPath", newSound);
                                    }
                                } else {
                                    editor.putString("sound_path_" + dialogId, newSound);
                                }
                            }
                            sound = channelSound;
                            edited = true;
                        }
                        boolean hasVibration = !isEmptyVibration(vibrationPattern);
                        if (hasVibration != vibrate) {
                            if (!isInApp) {
                                if (editor == null) {
                                    editor = preferences.edit();
                                }
                                if (isDefault) {
                                    if (type == TYPE_CHANNEL) {
                                        editor.putInt("vibrate_channel", vibrate ? 0 : 2);
                                    } else if (type == TYPE_GROUP) {
                                        editor.putInt("vibrate_group", vibrate ? 0 : 2);
                                    } else {
                                        editor.putInt("vibrate_messages", vibrate ? 0 : 2);
                                    }
                                } else {
                                    editor.putInt("vibrate_" + dialogId, vibrate ? 0 : 2);
                                }
                            }
                            vibrationPattern = channelVibrationPattern;
                            edited = true;
                        }
                        if (channelLedColor != ledColor) {
                            if (!isInApp) {
                                if (editor == null) {
                                    editor = preferences.edit();
                                }
                                if (isDefault) {
                                    if (type == TYPE_CHANNEL) {
                                        editor.putInt("ChannelLed", channelLedColor);
                                    } else if (type == TYPE_GROUP) {
                                        editor.putInt("GroupLed", channelLedColor);
                                    } else {
                                        editor.putInt("MessagesLed", channelLedColor);
                                    }
                                } else {
                                    editor.putInt("color_" + dialogId, channelLedColor);
                                }
                            }
                            ledColor = channelLedColor;
                            edited = true;
                        }
                        if (editor != null) {
                            editor.commit();
                        }
                    }
                }
            } else {
                channelId = null;
                settings = null;
            }
        }

        if (edited && newSettingsHash != null) {
            preferences.edit().putString(key, channelId).putString(key + "_s", newSettingsHash).commit();
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("change edited channel " + channelId);
            }
        } else if (shouldOverwrite || newSettingsHash == null || !isInApp || !isDefault) {
            for (int a = 0; a < vibrationPattern.length; a++) {
                newSettings.append(vibrationPattern[a]);
            }
            newSettings.append(ledColor);
            if (sound != null) {
                newSettings.append(sound.toString());
            }
            newSettings.append(importance);
            if (!isDefault && secretChat) {
                newSettings.append("secret");
            }
            newSettingsHash = Utilities.MD5(newSettings.toString());

            if (!isSilent && channelId != null && (shouldOverwrite || !settings.equals(newSettingsHash))) {
                try {
                    systemNotificationManager.deleteNotificationChannel(channelId);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("delete channel by settings change " + channelId);
                }
                channelId = null;
            }
        }
        if (channelId == null) {
            if (isDefault) {
                channelId = currentAccount + "channel_" + key + "_" + Utilities.random.nextLong();
            } else {
                channelId = currentAccount + "channel_" + dialogId + "_" + Utilities.random.nextLong();
            }
            NotificationChannel notificationChannel = new NotificationChannel(channelId, secretChat ? LocaleController.getString("SecretChatName", R.string.SecretChatName) : name, importance);
            notificationChannel.setGroup(groupId);
            if (ledColor != 0) {
                notificationChannel.enableLights(true);
                notificationChannel.setLightColor(ledColor);
            } else {
                notificationChannel.enableLights(false);
            }
            if (!isEmptyVibration(vibrationPattern)) {
                notificationChannel.enableVibration(true);
                if (vibrationPattern.length > 0) {
                    notificationChannel.setVibrationPattern(vibrationPattern);
                }
            } else {
                notificationChannel.enableVibration(false);
            }
            AudioAttributes.Builder builder = new AudioAttributes.Builder();
            builder.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION);
            builder.setUsage(AudioAttributes.USAGE_NOTIFICATION);
            if (sound != null) {
                notificationChannel.setSound(sound, builder.build());
            } else {
                notificationChannel.setSound(null, builder.build());
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("create new channel " + channelId);
            }
            systemNotificationManager.createNotificationChannel(notificationChannel);
            preferences.edit().putString(key, channelId).putString(key + "_s", newSettingsHash).commit();
        }
        return channelId;
    }

    private void showOrUpdateNotification(boolean notifyAboutLast) {
        if (!getUserConfig().isClientActivated() || pushMessages.isEmpty() || !SharedConfig.showNotificationsForAllAccounts && currentAccount != UserConfig.selectedAccount) {
            dismissNotification();
            return;
        }
        try {
            getConnectionsManager().resumeNetworkMaybe();

            MessageObject lastMessageObject = pushMessages.get(0);
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
            int dismissDate = preferences.getInt("dismissDate", 0);
            if (lastMessageObject.messageOwner.date <= dismissDate) {
                dismissNotification();
                return;
            }

            long dialog_id = lastMessageObject.getDialogId();
            boolean isChannel = false;
            long override_dialog_id = dialog_id;
            if (lastMessageObject.messageOwner.mentioned) {
                override_dialog_id = lastMessageObject.getFromChatId();
            }
            int mid = lastMessageObject.getId();
            int chat_id = lastMessageObject.messageOwner.peer_id.chat_id != 0 ? lastMessageObject.messageOwner.peer_id.chat_id : lastMessageObject.messageOwner.peer_id.channel_id;
            int user_id = lastMessageObject.messageOwner.peer_id.user_id;
            if (lastMessageObject.isFromUser() && (user_id == 0 || user_id == getUserConfig().getClientUserId())) {
                user_id = lastMessageObject.messageOwner.from_id.user_id;
            }

            TLRPC.User user = getMessagesController().getUser(user_id);
            TLRPC.Chat chat = null;
            if (chat_id != 0) {
                chat = getMessagesController().getChat(chat_id);
                if (chat == null && lastMessageObject.isFcmMessage()) {
                    isChannel = lastMessageObject.localChannel;
                } else {
                    isChannel = ChatObject.isChannel(chat) && !chat.megagroup;
                }
            }
            TLRPC.FileLocation photoPath = null;

            boolean notifyDisabled = false;
            int vibrate = 0;
            String soundPath = null;
            int ledColor = 0xff0000ff;
            int importance = 0;

            int notifyOverride = getNotifyOverride(preferences, override_dialog_id);
            boolean value;
            if (notifyOverride == -1) {
                value = isGlobalNotificationsEnabled(dialog_id, isChannel);
            } else {
                value = notifyOverride != 2;
            }

            String name;
            String chatName;
            boolean replace = true;
            if (((chat_id != 0 && chat == null) || user == null) && lastMessageObject.isFcmMessage()) {
                chatName = lastMessageObject.localName;
            } else if (chat != null) {
                chatName = chat.title;
            } else {
                chatName = UserObject.getUserName(user);
            }
            boolean passcode = AndroidUtilities.needShowPasscode() || SharedConfig.isWaitingForPasscodeEnter;
            if ((int) dialog_id == 0 || pushDialogs.size() > 1 || passcode) {
                if (passcode) {
                    if (chat_id != 0) {
                        name = LocaleController.getString("NotificationHiddenChatName", R.string.NotificationHiddenChatName);
                    } else {
                        name = LocaleController.getString("NotificationHiddenName", R.string.NotificationHiddenName);
                    }
                } else {
                    name = LocaleController.getString("AppName", R.string.AppName);
                }
                replace = false;
            } else {
                name = chatName;
            }

            String detailText;
            if (UserConfig.getActivatedAccountsCount() > 1) {
                if (pushDialogs.size() == 1) {
                    detailText = UserObject.getFirstName(getUserConfig().getCurrentUser());
                } else {
                    detailText = UserObject.getFirstName(getUserConfig().getCurrentUser()) + "・";
                }
            } else {
                detailText = "";
            }
            if (pushDialogs.size() != 1 || Build.VERSION.SDK_INT < 23) {
                if (pushDialogs.size() == 1) {
                    detailText += LocaleController.formatPluralString("NewMessages", total_unread_count);
                } else {
                    detailText += LocaleController.formatString("NotificationMessagesPeopleDisplayOrder", R.string.NotificationMessagesPeopleDisplayOrder, LocaleController.formatPluralString("NewMessages", total_unread_count), LocaleController.formatPluralString("FromChats", pushDialogs.size()));
                }
            }

            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(ApplicationLoader.applicationContext);

            int silent = 2;
            String lastMessage = null;
            boolean hasNewMessages = false;
            if (pushMessages.size() == 1) {
                MessageObject messageObject = pushMessages.get(0);
                boolean[] text = new boolean[1];
                String message = lastMessage = getStringForMessage(messageObject, false, text, null);
                silent = messageObject.messageOwner.silent ? 1 : 0;
                if (message == null) {
                    return;
                }
                if (replace) {
                    if (chat != null) {
                        message = message.replace(" @ " + name, "");
                    } else {
                        if (text[0]) {
                            message = message.replace(name + ": ", "");
                        } else {
                            message = message.replace(name + " ", "");
                        }
                    }
                }
                mBuilder.setContentText(message);
                mBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(message));
            } else {
                mBuilder.setContentText(detailText);
                NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
                inboxStyle.setBigContentTitle(name);
                int count = Math.min(10, pushMessages.size());
                boolean[] text = new boolean[1];
                for (int i = 0; i < count; i++) {
                    MessageObject messageObject = pushMessages.get(i);
                    String message = getStringForMessage(messageObject, false, text, null);
                    if (message == null || messageObject.messageOwner.date <= dismissDate) {
                        continue;
                    }
                    if (silent == 2) {
                        lastMessage = message;
                        silent = messageObject.messageOwner.silent ? 1 : 0;
                    }
                    if (pushDialogs.size() == 1) {
                        if (replace) {
                            if (chat != null) {
                                message = message.replace(" @ " + name, "");
                            } else {
                                if (text[0]) {
                                    message = message.replace(name + ": ", "");
                                } else {
                                    message = message.replace(name + " ", "");
                                }
                            }
                        }
                    }
                    inboxStyle.addLine(message);
                }
                inboxStyle.setSummaryText(detailText);
                mBuilder.setStyle(inboxStyle);
            }

            if (!notifyAboutLast || !value || MediaController.getInstance().isRecordingAudio() || silent == 1) {
                notifyDisabled = true;
            }

            if (!notifyDisabled && dialog_id == override_dialog_id && chat != null) {
                int notifyMaxCount;
                int notifyDelay;
                if (preferences.getBoolean("custom_" + dialog_id, false)) {
                    notifyMaxCount = preferences.getInt("smart_max_count_" + dialog_id, 2);
                    notifyDelay = preferences.getInt("smart_delay_" + dialog_id, 3 * 60);
                } else {
                    notifyMaxCount = 2;
                    notifyDelay = 3 * 60;
                }
                if (notifyMaxCount != 0) {
                    Point dialogInfo = smartNotificationsDialogs.get(dialog_id);
                    if (dialogInfo == null) {
                        dialogInfo = new Point(1, (int) (SystemClock.elapsedRealtime() / 1000));
                        smartNotificationsDialogs.put(dialog_id, dialogInfo);
                    } else {
                        int lastTime = dialogInfo.y;
                        if (lastTime + notifyDelay < SystemClock.elapsedRealtime() / 1000) {
                            dialogInfo.set(1, (int) (SystemClock.elapsedRealtime() / 1000));
                        } else {
                            int count = dialogInfo.x;
                            if (count < notifyMaxCount) {
                                dialogInfo.set(count + 1, (int) (SystemClock.elapsedRealtime() / 1000));
                            } else {
                                notifyDisabled = true;
                            }
                        }
                    }
                }
            }

            String defaultPath = Settings.System.DEFAULT_NOTIFICATION_URI.getPath();

            boolean isDefault = true;
            boolean isInApp = !ApplicationLoader.mainInterfacePaused;
            int chatType = TYPE_PRIVATE;

            String customSoundPath;
            int customVibrate;
            int customImportance;
            Integer customLedColor;
            if (preferences.getBoolean("custom_" + dialog_id, false)) {
                customVibrate = preferences.getInt("vibrate_" + dialog_id, 0);
                customImportance = preferences.getInt("priority_" + dialog_id, 3);
                customSoundPath = preferences.getString("sound_path_" + dialog_id, null);
                if (preferences.contains("color_" + dialog_id)) {
                    customLedColor = preferences.getInt("color_" + dialog_id, 0);
                } else {
                    customLedColor = null;
                }
            } else {
                customVibrate = 0;
                customImportance = 3;
                customSoundPath = null;
                customLedColor = null;
            }
            boolean vibrateOnlyIfSilent = false;

            if (chat_id != 0) {
                if (isChannel) {
                    soundPath = preferences.getString("ChannelSoundPath", defaultPath);
                    vibrate = preferences.getInt("vibrate_channel", 0);
                    importance = preferences.getInt("priority_channel", 1);
                    ledColor = preferences.getInt("ChannelLed", 0xff0000ff);
                    chatType = TYPE_CHANNEL;
                } else {
                    soundPath = preferences.getString("GroupSoundPath", defaultPath);
                    vibrate = preferences.getInt("vibrate_group", 0);
                    importance = preferences.getInt("priority_group", 1);
                    ledColor = preferences.getInt("GroupLed", 0xff0000ff);
                    chatType = TYPE_GROUP;
                }
            } else if (user_id != 0) {
                soundPath = preferences.getString("GlobalSoundPath", defaultPath);
                vibrate = preferences.getInt("vibrate_messages", 0);
                importance = preferences.getInt("priority_messages", 1);
                ledColor = preferences.getInt("MessagesLed", 0xff0000ff);
                chatType = TYPE_PRIVATE;
            }
            if (vibrate == 4) {
                vibrateOnlyIfSilent = true;
                vibrate = 0;
            }
            if (customSoundPath != null && !TextUtils.equals(soundPath, customSoundPath)) {
                soundPath = customSoundPath;
                isDefault = false;
            }
            if (customImportance != 3 && importance != customImportance) {
                importance = customImportance;
                isDefault = false;
            }
            if (customLedColor != null && customLedColor != ledColor) {
                ledColor = customLedColor;
                isDefault = false;
            }
            if (customVibrate != 0 && customVibrate != 4 && customVibrate != vibrate) {
                vibrate = customVibrate;
                isDefault = false;
            }
            if (isInApp) {
                if (!preferences.getBoolean("EnableInAppSounds", true)) {
                    soundPath = null;
                }
                if (!preferences.getBoolean("EnableInAppVibrate", true)) {
                    vibrate = 2;
                }
                if (!preferences.getBoolean("EnableInAppPriority", false)) {
                    importance = 0;
                } else if (importance == 2) {
                    importance = 1;
                }
            }
            if (vibrateOnlyIfSilent && vibrate != 2) {
                try {
                    int mode = audioManager.getRingerMode();
                    if (mode != AudioManager.RINGER_MODE_SILENT && mode != AudioManager.RINGER_MODE_VIBRATE) {
                        vibrate = 2;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            if (notifyDisabled) {
                vibrate = 0;
                importance = 0;
                ledColor = 0;
                soundPath = null;
            }

            Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
            intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            //intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            if ((int) dialog_id != 0) {
                if (pushDialogs.size() == 1) {
                    if (chat_id != 0) {
                        intent.putExtra("chatId", chat_id);
                    } else if (user_id != 0) {
                        intent.putExtra("userId", user_id);
                    }
                }
                if (AndroidUtilities.needShowPasscode() || SharedConfig.isWaitingForPasscodeEnter) {
                    photoPath = null;
                } else {
                    if (pushDialogs.size() == 1 && Build.VERSION.SDK_INT < 28) {
                        if (chat != null) {
                            if (chat.photo != null && chat.photo.photo_small != null && chat.photo.photo_small.volume_id != 0 && chat.photo.photo_small.local_id != 0) {
                                photoPath = chat.photo.photo_small;
                            }
                        } else if (user != null) {
                            if (user.photo != null && user.photo.photo_small != null && user.photo.photo_small.volume_id != 0 && user.photo.photo_small.local_id != 0) {
                                photoPath = user.photo.photo_small;
                            }
                        }
                    }
                }
            } else {
                if (pushDialogs.size() == 1 && dialog_id != globalSecretChatId) {
                    intent.putExtra("encId", (int) (dialog_id >> 32));
                }
            }
            intent.putExtra("currentAccount", currentAccount);
            PendingIntent contentIntent = PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent, PendingIntent.FLAG_ONE_SHOT);

            mBuilder.setContentTitle(name)
                    .setSmallIcon(R.drawable.notification)
                    .setAutoCancel(true)
                    .setNumber(total_unread_count)
                    .setContentIntent(contentIntent)
                    .setGroup(notificationGroup)
                    .setGroupSummary(true)
                    .setShowWhen(true)
                    .setWhen(((long) lastMessageObject.messageOwner.date) * 1000)
                    .setColor(0xff11acfa);

            long[] vibrationPattern = null;
            Uri sound = null;

            mBuilder.setCategory(NotificationCompat.CATEGORY_MESSAGE);
            if (chat == null && user != null && user.phone != null && user.phone.length() > 0) {
                mBuilder.addPerson("tel:+" + user.phone);
            }

            Intent dismissIntent = new Intent(ApplicationLoader.applicationContext, NotificationDismissReceiver.class);
            dismissIntent.putExtra("messageDate", lastMessageObject.messageOwner.date);
            dismissIntent.putExtra("currentAccount", currentAccount);
            mBuilder.setDeleteIntent(PendingIntent.getBroadcast(ApplicationLoader.applicationContext, 1, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT));

            if (photoPath != null) {
                BitmapDrawable img = ImageLoader.getInstance().getImageFromMemory(photoPath, null, "50_50");
                if (img != null) {
                    mBuilder.setLargeIcon(img.getBitmap());
                } else {
                    try {
                        File file = FileLoader.getPathToAttach(photoPath, true);
                        if (file.exists()) {
                            float scaleFactor = 160.0f / AndroidUtilities.dp(50);
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inSampleSize = scaleFactor < 1 ? 1 : (int) scaleFactor;
                            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                            if (bitmap != null) {
                                mBuilder.setLargeIcon(bitmap);
                            }
                        }
                    } catch (Throwable ignore) {

                    }
                }
            }

            int configImportance = 0;
            if (!notifyAboutLast || silent == 1) {
                mBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
                if (Build.VERSION.SDK_INT >= 26) {
                    configImportance = NotificationManager.IMPORTANCE_LOW;
                }
            } else {
                if (importance == 0) {
                    mBuilder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
                    if (Build.VERSION.SDK_INT >= 26) {
                        configImportance = NotificationManager.IMPORTANCE_DEFAULT;
                    }
                } else if (importance == 1 || importance == 2) {
                    mBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
                    if (Build.VERSION.SDK_INT >= 26) {
                        configImportance = NotificationManager.IMPORTANCE_HIGH;
                    }
                } else if (importance == 4) {
                    mBuilder.setPriority(NotificationCompat.PRIORITY_MIN);
                    if (Build.VERSION.SDK_INT >= 26) {
                        configImportance = NotificationManager.IMPORTANCE_MIN;
                    }
                } else if (importance == 5) {
                    mBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
                    if (Build.VERSION.SDK_INT >= 26) {
                        configImportance = NotificationManager.IMPORTANCE_LOW;
                    }
                }
            }

            if (silent != 1 && !notifyDisabled) {
                if (!isInApp || preferences.getBoolean("EnableInAppPreview", true)) {
                    if (lastMessage.length() > 100) {
                        lastMessage = lastMessage.substring(0, 100).replace('\n', ' ').trim() + "...";
                    }
                    mBuilder.setTicker(lastMessage);
                }
                if (soundPath != null && !soundPath.equals("NoSound")) {
                    if (Build.VERSION.SDK_INT >= 26) {
                        if (soundPath.equals(defaultPath)) {
                            sound = Settings.System.DEFAULT_NOTIFICATION_URI;
                        } else {
                            sound = Uri.parse(soundPath);
                        }
                    } else {
                        if (soundPath.equals(defaultPath)) {
                            mBuilder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AudioManager.STREAM_NOTIFICATION);
                        } else {
                            if (Build.VERSION.SDK_INT >= 24 && soundPath.startsWith("file://") && !AndroidUtilities.isInternalUri(Uri.parse(soundPath))) {
                                try {
                                    Uri uri = FileProvider.getUriForFile(ApplicationLoader.applicationContext, BuildConfig.APPLICATION_ID + ".provider", new File(soundPath.replace("file://", "")));
                                    ApplicationLoader.applicationContext.grantUriPermission("com.android.systemui", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    mBuilder.setSound(uri, AudioManager.STREAM_NOTIFICATION);
                                } catch (Exception e) {
                                    mBuilder.setSound(Uri.parse(soundPath), AudioManager.STREAM_NOTIFICATION);
                                }
                            } else {
                                mBuilder.setSound(Uri.parse(soundPath), AudioManager.STREAM_NOTIFICATION);
                            }
                        }
                    }
                }
                if (ledColor != 0) {
                    mBuilder.setLights(ledColor, 1000, 1000);
                }
                if (vibrate == 2) {
                    mBuilder.setVibrate(vibrationPattern = new long[]{0, 0});
                } else if (vibrate == 1) {
                    mBuilder.setVibrate(vibrationPattern = new long[]{0, 100, 0, 100});
                } else if (vibrate == 0 || vibrate == 4) {
                    mBuilder.setDefaults(NotificationCompat.DEFAULT_VIBRATE);
                    vibrationPattern = new long[]{};
                } else if (vibrate == 3) {
                    mBuilder.setVibrate(vibrationPattern = new long[]{0, 1000});
                }
            } else {
                mBuilder.setVibrate(vibrationPattern = new long[]{0, 0});
            }

            boolean hasCallback = false;
            if (!AndroidUtilities.needShowPasscode() && !SharedConfig.isWaitingForPasscodeEnter && lastMessageObject.getDialogId() == 777000) {
                if (lastMessageObject.messageOwner.reply_markup != null) {
                    ArrayList<TLRPC.TL_keyboardButtonRow> rows = lastMessageObject.messageOwner.reply_markup.rows;
                    for (int a = 0, size = rows.size(); a < size; a++) {
                        TLRPC.TL_keyboardButtonRow row = rows.get(a);
                        for (int b = 0, size2 = row.buttons.size(); b < size2; b++) {
                            TLRPC.KeyboardButton button = row.buttons.get(b);
                            if (button instanceof TLRPC.TL_keyboardButtonCallback) {
                                Intent callbackIntent = new Intent(ApplicationLoader.applicationContext, NotificationCallbackReceiver.class);
                                callbackIntent.putExtra("currentAccount", currentAccount);
                                callbackIntent.putExtra("did", dialog_id);
                                if (button.data != null) {
                                    callbackIntent.putExtra("data", button.data);
                                }
                                callbackIntent.putExtra("mid", lastMessageObject.getId());
                                mBuilder.addAction(0, button.text, PendingIntent.getBroadcast(ApplicationLoader.applicationContext, lastButtonId++, callbackIntent, PendingIntent.FLAG_UPDATE_CURRENT));
                                hasCallback = true;
                            }
                        }
                    }
                }
            }

            if (!hasCallback && Build.VERSION.SDK_INT < 24 && SharedConfig.passcodeHash.length() == 0 && hasMessagesToReply()) {
                Intent replyIntent = new Intent(ApplicationLoader.applicationContext, PopupReplyReceiver.class);
                replyIntent.putExtra("currentAccount", currentAccount);
                if (Build.VERSION.SDK_INT <= 19) {
                    mBuilder.addAction(R.drawable.ic_ab_reply2, LocaleController.getString("Reply", R.string.Reply), PendingIntent.getBroadcast(ApplicationLoader.applicationContext, 2, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT));
                } else {
                    mBuilder.addAction(R.drawable.ic_ab_reply, LocaleController.getString("Reply", R.string.Reply), PendingIntent.getBroadcast(ApplicationLoader.applicationContext, 2, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT));
                }
            }
            showExtraNotifications(mBuilder, detailText, dialog_id, chatName, vibrationPattern, ledColor, sound, configImportance, isDefault, isInApp, notifyDisabled, chatType);
            scheduleNotificationRepeat();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @SuppressLint("NewApi")
    private void setNotificationChannel(Notification mainNotification, NotificationCompat.Builder builder, boolean useSummaryNotification) {
        if (useSummaryNotification) {
            builder.setChannelId(OTHER_NOTIFICATIONS_CHANNEL);
        } else {
            builder.setChannelId(mainNotification.getChannelId());
        }
    }

    private void resetNotificationSound(NotificationCompat.Builder notificationBuilder, long dialogId, String chatName, long[] vibrationPattern, int ledColor, Uri sound, int importance, boolean isDefault, boolean isInApp, boolean isSilent, int chatType) {
        Uri defaultSound = Settings.System.DEFAULT_RINGTONE_URI;
        if (defaultSound != null && sound != null && !TextUtils.equals(defaultSound.toString(), sound.toString())) {
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
            SharedPreferences.Editor editor = preferences.edit();

            String newSound = defaultSound.toString();
            String ringtoneName = LocaleController.getString("DefaultRingtone", R.string.DefaultRingtone);
            if (isDefault) {
                if (chatType == TYPE_CHANNEL) {
                    editor.putString("ChannelSound", ringtoneName);
                } else if (chatType == TYPE_GROUP) {
                    editor.putString("GroupSound", ringtoneName);
                } else {
                    editor.putString("GlobalSound", ringtoneName);
                }
                if (chatType == TYPE_CHANNEL) {
                    editor.putString("ChannelSoundPath", newSound);
                } else if (chatType == TYPE_GROUP) {
                    editor.putString("GroupSoundPath", newSound);
                } else {
                    editor.putString("GlobalSoundPath", newSound);
                }
                getNotificationsController().deleteNotificationChannelGlobalInternal(chatType, -1);
            } else {
                editor.putString("sound_" + dialogId, ringtoneName);
                editor.putString("sound_path_" + dialogId, newSound);
                deleteNotificationChannelInternal(dialogId, -1);
            }
            editor.commit();
            sound = Settings.System.DEFAULT_RINGTONE_URI;
            notificationBuilder.setChannelId(validateChannelId(dialogId, chatName, vibrationPattern, ledColor, sound, importance, isDefault, isInApp, isSilent, chatType));
            notificationManager.notify(notificationId, notificationBuilder.build());
        }
    }

    @SuppressLint("InlinedApi")
    private void showExtraNotifications(NotificationCompat.Builder notificationBuilder, String summary, long dialogId, String chatName, long[] vibrationPattern, int ledColor, Uri sound, int importance, boolean isDefault, boolean isInApp, boolean isSilent, int chatType) {
        if (Build.VERSION.SDK_INT >= 26) {
            notificationBuilder.setChannelId(validateChannelId(dialogId, chatName, vibrationPattern, ledColor, sound, importance, isDefault, isInApp, isSilent, chatType));
        }
        Notification mainNotification = notificationBuilder.build();
        if (Build.VERSION.SDK_INT < 18) {
            notificationManager.notify(notificationId, mainNotification);
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("show summary notification by SDK check");
            }
            return;
        }

        SharedPreferences preferences = getAccountInstance().getNotificationsSettings();

        ArrayList<Long> sortedDialogs = new ArrayList<>();
        LongSparseArray<ArrayList<MessageObject>> messagesByDialogs = new LongSparseArray<>();
        for (int a = 0; a < pushMessages.size(); a++) {
            MessageObject messageObject = pushMessages.get(a);
            long dialog_id = messageObject.getDialogId();
            int dismissDate = preferences.getInt("dismissDate" + dialog_id, 0);
            if (messageObject.messageOwner.date <= dismissDate) {
                continue;
            }

            ArrayList<MessageObject> arrayList = messagesByDialogs.get(dialog_id);
            if (arrayList == null) {
                arrayList = new ArrayList<>();
                messagesByDialogs.put(dialog_id, arrayList);
                sortedDialogs.add(dialog_id);
            }
            arrayList.add(messageObject);
        }

        LongSparseArray<Integer> oldIdsWear = wearNotificationsIds.clone();
        wearNotificationsIds.clear();

        class NotificationHolder {
            int id;
            int lowerId;
            String name;
            TLRPC.User user;
            TLRPC.Chat chat;
            NotificationCompat.Builder notification;

            NotificationHolder(int i, int li, String n, TLRPC.User u, TLRPC.Chat c, NotificationCompat.Builder builder) {
                id = i;
                name = n;
                user = u;
                chat = c;
                notification = builder;
                lowerId = li;
            }

            void call() {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.w("show dialog notification with id " + id);
                }
                try {
                    notificationManager.notify(id, notification.build());
                } catch (SecurityException e) {
                    FileLog.e(e);
                    resetNotificationSound(notification, dialogId, chatName, vibrationPattern, ledColor, sound, importance, isDefault, isInApp, isSilent, chatType);
                }
            }
        }

        ArrayList<NotificationHolder> holders = new ArrayList<>();
        JSONArray serializedNotifications = null;
        if (WearDataLayerListenerService.isWatchConnected()) {
            serializedNotifications = new JSONArray();
        }

        boolean useSummaryNotification = Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1 || sortedDialogs.size() > 1;
        if (useSummaryNotification && Build.VERSION.SDK_INT >= 26) {
            checkOtherNotificationsChannel();
        }

        int selfUserId = getUserConfig().getClientUserId();
        boolean waitingForPasscode = AndroidUtilities.needShowPasscode() || SharedConfig.isWaitingForPasscodeEnter;

        int maxCount = 7;
        LongSparseArray<Person> personCache = new LongSparseArray<>();
        for (int b = 0, size = sortedDialogs.size(); b < size; b++) {
            if (holders.size() >= maxCount) {
                break;
            }
            long dialog_id = sortedDialogs.get(b);
            ArrayList<MessageObject> messageObjects = messagesByDialogs.get(dialog_id);
            int max_id = messageObjects.get(0).getId();
            int lowerId = (int) dialog_id;
            int highId = (int) (dialog_id >> 32);

            Integer internalId = oldIdsWear.get(dialog_id);
            if (internalId == null) {
                if (lowerId != 0) {
                    internalId = lowerId;
                } else {
                    internalId = highId;
                }
            } else {
                oldIdsWear.remove(dialog_id);
            }

            JSONObject serializedChat = null;
            if (serializedNotifications != null) {
                serializedChat = new JSONObject();
            }

            /*if (lastWearNotifiedMessageId.get(dialog_id, 0) == max_id) {
                continue;
            }
            lastWearNotifiedMessageId.put(dialog_id, max_id);*/
            MessageObject lastMessageObject = messageObjects.get(0);
            int max_date = lastMessageObject.messageOwner.date;
            TLRPC.Chat chat = null;
            TLRPC.User user = null;
            boolean isChannel = false;
            boolean isSupergroup = false;
            String name;
            TLRPC.FileLocation photoPath = null;
            Bitmap avatarBitmap = null;
            File avatalFile = null;
            boolean canReply;

            if (lowerId != 0) {
                canReply = lowerId != 777000;
                if (lowerId > 0) {
                    user = getMessagesController().getUser(lowerId);
                    if (user == null) {
                        if (lastMessageObject.isFcmMessage()) {
                            name = lastMessageObject.localName;
                        } else {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.w("not found user to show dialog notification " + lowerId);
                            }
                            continue;
                        }
                    } else {
                        name = UserObject.getUserName(user);
                        if (user.photo != null && user.photo.photo_small != null && user.photo.photo_small.volume_id != 0 && user.photo.photo_small.local_id != 0) {
                            photoPath = user.photo.photo_small;
                        }
                    }
                    if (UserObject.isReplyUser(lowerId)) {
                        name = LocaleController.getString("RepliesTitle", R.string.RepliesTitle);
                    } else if (lowerId == selfUserId) {
                        name = LocaleController.getString("MessageScheduledReminderNotification", R.string.MessageScheduledReminderNotification);
                    }
                } else {
                    chat = getMessagesController().getChat(-lowerId);
                    if (chat == null) {
                        if (lastMessageObject.isFcmMessage()) {
                            isSupergroup = lastMessageObject.isSupergroup();
                            name = lastMessageObject.localName;
                            isChannel = lastMessageObject.localChannel;
                        } else {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.w("not found chat to show dialog notification " + lowerId);
                            }
                            continue;
                        }
                    } else {
                        isSupergroup = chat.megagroup;
                        isChannel = ChatObject.isChannel(chat) && !chat.megagroup;
                        name = chat.title;
                        if (chat.photo != null && chat.photo.photo_small != null && chat.photo.photo_small.volume_id != 0 && chat.photo.photo_small.local_id != 0) {
                            photoPath = chat.photo.photo_small;
                        }
                    }
                }
            } else {
                canReply = false;
                if (dialog_id != globalSecretChatId) {
                    TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(highId);
                    if (encryptedChat == null) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.w("not found secret chat to show dialog notification " + highId);
                        }
                        continue;
                    }
                    user = getMessagesController().getUser(encryptedChat.user_id);
                    if (user == null) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.w("not found secret chat user to show dialog notification " + encryptedChat.user_id);
                        }
                        continue;
                    }
                }
                name = LocaleController.getString("SecretChatName", R.string.SecretChatName);
                photoPath = null;
                serializedChat = null;
            }

            if (waitingForPasscode) {
                if (lowerId < 0) {
                    name = LocaleController.getString("NotificationHiddenChatName", R.string.NotificationHiddenChatName);
                } else {
                    name = LocaleController.getString("NotificationHiddenName", R.string.NotificationHiddenName);
                }
                photoPath = null;
                canReply = false;
            }

            if (photoPath != null) {
                avatalFile = FileLoader.getPathToAttach(photoPath, true);
                if (Build.VERSION.SDK_INT < 28) {
                    BitmapDrawable img = ImageLoader.getInstance().getImageFromMemory(photoPath, null, "50_50");
                    if (img != null) {
                        avatarBitmap = img.getBitmap();
                    } else {
                        try {
                            if (avatalFile.exists()) {
                                float scaleFactor = 160.0f / AndroidUtilities.dp(50);
                                BitmapFactory.Options options = new BitmapFactory.Options();
                                options.inSampleSize = scaleFactor < 1 ? 1 : (int) scaleFactor;
                                avatarBitmap = BitmapFactory.decodeFile(avatalFile.getAbsolutePath(), options);
                            }
                        } catch (Throwable ignore) {

                        }
                    }
                }
            }

            NotificationCompat.Action wearReplyAction = null;

            if ((!isChannel || isSupergroup) && canReply && !SharedConfig.isWaitingForPasscodeEnter && selfUserId != lowerId && !UserObject.isReplyUser(lowerId)) {
                Intent replyIntent = new Intent(ApplicationLoader.applicationContext, WearReplyReceiver.class);
                replyIntent.putExtra("dialog_id", dialog_id);
                replyIntent.putExtra("max_id", max_id);
                replyIntent.putExtra("currentAccount", currentAccount);
                PendingIntent replyPendingIntent = PendingIntent.getBroadcast(ApplicationLoader.applicationContext, internalId, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                RemoteInput remoteInputWear = new RemoteInput.Builder(EXTRA_VOICE_REPLY).setLabel(LocaleController.getString("Reply", R.string.Reply)).build();
                String replyToString;
                if (lowerId < 0) {
                    replyToString = LocaleController.formatString("ReplyToGroup", R.string.ReplyToGroup, name);
                } else {
                    replyToString = LocaleController.formatString("ReplyToUser", R.string.ReplyToUser, name);
                }
                wearReplyAction = new NotificationCompat.Action.Builder(R.drawable.ic_reply_icon, replyToString, replyPendingIntent)
                        .setAllowGeneratedReplies(true)
                        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                        .addRemoteInput(remoteInputWear)
                        .setShowsUserInterface(false)
                        .build();
            }

            Integer count = pushDialogs.get(dialog_id);
            if (count == null) {
                count = 0;
            }
            int n = Math.max(count, messageObjects.size());
            String conversationName;
            if (n <= 1 || Build.VERSION.SDK_INT >= 28) {
                conversationName = name;
            } else {
                conversationName = String.format("%1$s (%2$d)", name, n);
            }

            Person selfPerson = personCache.get(selfUserId);
            if (Build.VERSION.SDK_INT >= 28 && selfPerson == null) {
                TLRPC.User sender = getMessagesController().getUser(selfUserId);
                if (sender == null) {
                    sender = getUserConfig().getCurrentUser();
                }
                try {
                    if (sender != null && sender.photo != null && sender.photo.photo_small != null && sender.photo.photo_small.volume_id != 0 && sender.photo.photo_small.local_id != 0) {
                        Person.Builder personBuilder = new Person.Builder().setName(LocaleController.getString("FromYou", R.string.FromYou));
                        File avatar = FileLoader.getPathToAttach(sender.photo.photo_small, true);
                        loadRoundAvatar(avatar, personBuilder);
                        selfPerson = personBuilder.build();
                        personCache.put(selfUserId, selfPerson);
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }

            NotificationCompat.MessagingStyle messagingStyle;
            if (selfPerson != null) {
                messagingStyle = new NotificationCompat.MessagingStyle(selfPerson);
            } else {
                messagingStyle = new NotificationCompat.MessagingStyle("");
            }
            if (Build.VERSION.SDK_INT < 28 || lowerId < 0 && !isChannel || UserObject.isReplyUser(lowerId)) {
                messagingStyle.setConversationTitle(conversationName);
            }
            messagingStyle.setGroupConversation(Build.VERSION.SDK_INT < 28 || !isChannel && lowerId < 0 || UserObject.isReplyUser(lowerId));

            StringBuilder text = new StringBuilder();
            String[] senderName = new String[1];
            boolean[] preview = new boolean[1];
            ArrayList<TLRPC.TL_keyboardButtonRow> rows = null;
            int rowsMid = 0;
            JSONArray serializedMsgs = null;
            if (serializedChat != null) {
                serializedMsgs = new JSONArray();
            }
            for (int a = messageObjects.size() - 1; a >= 0; a--) {
                MessageObject messageObject = messageObjects.get(a);
                String message = getShortStringForMessage(messageObject, senderName, preview);
                if (dialog_id == selfUserId) {
                    senderName[0] = name;
                } else if (lowerId < 0 && messageObject.messageOwner.from_scheduled) {
                    senderName[0] = LocaleController.getString("NotificationMessageScheduledName", R.string.NotificationMessageScheduledName);
                }
                if (message == null) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.w("message text is null for " + messageObject.getId() + " did = " + messageObject.getDialogId());
                    }
                    continue;
                }
                if (text.length() > 0) {
                    text.append("\n\n");
                }
                if (dialog_id != selfUserId && messageObject.messageOwner.from_scheduled && lowerId > 0) {
                    message = String.format("%1$s: %2$s", LocaleController.getString("NotificationMessageScheduledName", R.string.NotificationMessageScheduledName), message);
                    text.append(message);
                } else {
                    if (senderName[0] != null) {
                        text.append(String.format("%1$s: %2$s", senderName[0], message));
                    } else {
                        text.append(message);
                    }
                }

                long uid;
                if (lowerId > 0) {
                    uid = lowerId;
                } else if (isChannel) {
                    uid = -lowerId;
                } else if (lowerId < 0) {
                    uid = messageObject.getSenderId();
                } else {
                    uid = dialog_id;
                }
                Person person = personCache.get(uid);
                String personName = "";
                if (senderName[0] == null) {
                    if (waitingForPasscode) {
                        if (lowerId < 0) {
                            if (isChannel) {
                                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
                                    personName = LocaleController.getString("NotificationHiddenChatName", R.string.NotificationHiddenChatName);
                                }
                            } else {
                                personName = LocaleController.getString("NotificationHiddenChatUserName", R.string.NotificationHiddenChatUserName);
                            }
                        } else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
                            personName = LocaleController.getString("NotificationHiddenName", R.string.NotificationHiddenName);
                        }
                    }
                } else {
                    personName = senderName[0];
                }
                if (person == null || !TextUtils.equals(person.getName(), personName)) {
                    Person.Builder personBuilder = new Person.Builder().setName(personName);
                    if (preview[0] && lowerId != 0 && Build.VERSION.SDK_INT >= 28) {
                        File avatar = null;
                        if (lowerId > 0 || isChannel) {
                            avatar = avatalFile;
                        } else {
                            int fromId = messageObject.getSenderId();
                            TLRPC.User sender = getMessagesController().getUser(fromId);
                            if (sender == null) {
                                sender = getMessagesStorage().getUserSync(fromId);
                                if (sender != null) {
                                    getMessagesController().putUser(sender, true);
                                }
                            }
                            if (sender != null && sender.photo != null && sender.photo.photo_small != null && sender.photo.photo_small.volume_id != 0 && sender.photo.photo_small.local_id != 0) {
                                avatar = FileLoader.getPathToAttach(sender.photo.photo_small, true);
                            }
                        }
                        loadRoundAvatar(avatar, personBuilder);
                    }
                    person = personBuilder.build();
                    personCache.put(uid, person);
                }

                if (lowerId != 0) {
                    boolean setPhoto = false;
                    if (preview[0] && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !((ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)).isLowRamDevice()) {
                        if (!waitingForPasscode && !messageObject.isSecretMedia() && (messageObject.type == 1 || messageObject.isSticker())) {
                            File attach = FileLoader.getPathToMessage(messageObject.messageOwner);
                            NotificationCompat.MessagingStyle.Message msg = new NotificationCompat.MessagingStyle.Message(message, ((long) messageObject.messageOwner.date) * 1000L, person);
                            String mimeType = messageObject.isSticker() ? "image/webp" : "image/jpeg";
                            Uri uri;
                            if (attach.exists()) {
                                try {
                                    uri = FileProvider.getUriForFile(ApplicationLoader.applicationContext, BuildConfig.APPLICATION_ID + ".provider", attach);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                    uri = null;
                                }
                            } else if (getFileLoader().isLoadingFile(attach.getName())) {
                                Uri.Builder _uri = new Uri.Builder()
                                        .scheme("content")
                                        .authority(NotificationImageProvider.AUTHORITY)
                                        .appendPath("msg_media_raw")
                                        .appendPath(currentAccount + "")
                                        .appendPath(attach.getName())
                                        .appendQueryParameter("final_path", attach.getAbsolutePath());
                                uri = _uri.build();
                            } else {
                                uri = null;
                            }
                            if (uri != null) {
                                msg.setData(mimeType, uri);
                                messagingStyle.addMessage(msg);
                                final Uri uriFinal = uri;
                                ApplicationLoader.applicationContext.grantUriPermission("com.android.systemui", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                AndroidUtilities.runOnUIThread(() -> ApplicationLoader.applicationContext.revokeUriPermission(uriFinal, Intent.FLAG_GRANT_READ_URI_PERMISSION), 20000);

                                if (!TextUtils.isEmpty(messageObject.caption)) {
                                    messagingStyle.addMessage(messageObject.caption, ((long) messageObject.messageOwner.date) * 1000, person);
                                }
                                setPhoto = true;
                            }
                        }
                    }
                    if (!setPhoto) {
                        messagingStyle.addMessage(message, ((long) messageObject.messageOwner.date) * 1000, person);
                    }
                    if (preview[0] && !waitingForPasscode && messageObject.isVoice()) {
                        List<NotificationCompat.MessagingStyle.Message> messages = messagingStyle.getMessages();
                        if (!messages.isEmpty()) {
                            File f = FileLoader.getPathToMessage(messageObject.messageOwner);
                            Uri uri;
                            if (Build.VERSION.SDK_INT >= 24) {
                                try {
                                    uri = FileProvider.getUriForFile(ApplicationLoader.applicationContext, BuildConfig.APPLICATION_ID + ".provider", f);
                                } catch (Exception ignore) {
                                    uri = null;
                                }
                            } else {
                                uri = Uri.fromFile(f);
                            }
                            if (uri != null) {
                                NotificationCompat.MessagingStyle.Message addedMessage = messages.get(messages.size() - 1);
                                addedMessage.setData("audio/ogg", uri);
                            }
                        }
                    }
                } else {
                    messagingStyle.addMessage(message, ((long) messageObject.messageOwner.date) * 1000, person);
                }

                if (serializedMsgs != null) {
                    try {
                        JSONObject jmsg = new JSONObject();
                        jmsg.put("text", message);
                        jmsg.put("date", messageObject.messageOwner.date);
                        if (messageObject.isFromUser() && lowerId < 0) {
                            TLRPC.User sender = getMessagesController().getUser(messageObject.getSenderId());
                            if (sender != null) {
                                jmsg.put("fname", sender.first_name);
                                jmsg.put("lname", sender.last_name);
                            }
                        }
                        serializedMsgs.put(jmsg);
                    } catch (JSONException ignore) {
                    }
                }

                if (dialog_id == 777000 && messageObject.messageOwner.reply_markup != null) {
                    rows = messageObject.messageOwner.reply_markup.rows;
                    rowsMid = messageObject.getId();
                }
            }

            Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
            intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            if (lowerId != 0) {
                if (lowerId > 0) {
                    intent.putExtra("userId", lowerId);
                } else {
                    intent.putExtra("chatId", -lowerId);
                }
            } else {
                intent.putExtra("encId", highId);
            }
            intent.putExtra("currentAccount", currentAccount);
            PendingIntent contentIntent = PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent, PendingIntent.FLAG_ONE_SHOT);

            NotificationCompat.WearableExtender wearableExtender = new NotificationCompat.WearableExtender();
            if (wearReplyAction != null) {
                wearableExtender.addAction(wearReplyAction);
            }
            Intent msgHeardIntent = new Intent(ApplicationLoader.applicationContext, AutoMessageHeardReceiver.class);
            msgHeardIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            msgHeardIntent.setAction("org.telegram.messenger.ACTION_MESSAGE_HEARD");
            msgHeardIntent.putExtra("dialog_id", dialog_id);
            msgHeardIntent.putExtra("max_id", max_id);
            msgHeardIntent.putExtra("currentAccount", currentAccount);
            PendingIntent readPendingIntent = PendingIntent.getBroadcast(ApplicationLoader.applicationContext, internalId, msgHeardIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationCompat.Action readAction = new NotificationCompat.Action.Builder(R.drawable.menu_read, LocaleController.getString("MarkAsRead", R.string.MarkAsRead), readPendingIntent)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                    .setShowsUserInterface(false)
                    .build();

            String dismissalID;
            if (lowerId != 0) {
                if (lowerId > 0) {
                    dismissalID = "tguser" + lowerId + "_" + max_id;
                } else {
                    dismissalID = "tgchat" + (-lowerId) + "_" + max_id;
                }
            } else if (dialog_id != globalSecretChatId) {
                dismissalID = "tgenc" + highId + "_" + max_id;
            } else {
                dismissalID = null;
            }

            if (dismissalID != null) {
                wearableExtender.setDismissalId(dismissalID);
                NotificationCompat.WearableExtender summaryExtender = new NotificationCompat.WearableExtender();
                summaryExtender.setDismissalId("summary_" + dismissalID);
                notificationBuilder.extend(summaryExtender);
            }
            wearableExtender.setBridgeTag("tgaccount" + selfUserId);

            long date = ((long) messageObjects.get(0).messageOwner.date) * 1000;

            NotificationCompat.Builder builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext)
                    .setContentTitle(name)
                    .setSmallIcon(R.drawable.notification)
                    .setContentText(text.toString())
                    .setAutoCancel(true)
                    .setNumber(messageObjects.size())
                    .setColor(0xff11acfa)
                    .setGroupSummary(false)
                    .setWhen(date)
                    .setShowWhen(true)
                    .setStyle(messagingStyle)
                    .setContentIntent(contentIntent)
                    .extend(wearableExtender)
                    .setSortKey(String.valueOf(Long.MAX_VALUE - date))
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE);

            Intent dismissIntent = new Intent(ApplicationLoader.applicationContext, NotificationDismissReceiver.class);
            dismissIntent.putExtra("messageDate", max_date);
            dismissIntent.putExtra("dialogId", dialog_id);
            dismissIntent.putExtra("currentAccount", currentAccount);
            builder.setDeleteIntent(PendingIntent.getBroadcast(ApplicationLoader.applicationContext, internalId, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT));

            if (useSummaryNotification) {
                builder.setGroup(notificationGroup);
                builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);
            }

            if (wearReplyAction != null) {
                builder.addAction(wearReplyAction);
            }
            if (!waitingForPasscode) {
                builder.addAction(readAction);
            }
            if (sortedDialogs.size() == 1 && !TextUtils.isEmpty(summary)) {
                builder.setSubText(summary);
            }
            if (lowerId == 0) {
                builder.setLocalOnly(true);
            }
            if (avatarBitmap != null) {
                builder.setLargeIcon(avatarBitmap);
            }

            if (!AndroidUtilities.needShowPasscode(false) && !SharedConfig.isWaitingForPasscodeEnter) {
                if (rows != null) {
                    for (int r = 0, rc = rows.size(); r < rc; r++) {
                        TLRPC.TL_keyboardButtonRow row = rows.get(r);
                        for (int c = 0, cc = row.buttons.size(); c < cc; c++) {
                            TLRPC.KeyboardButton button = row.buttons.get(c);
                            if (button instanceof TLRPC.TL_keyboardButtonCallback) {
                                Intent callbackIntent = new Intent(ApplicationLoader.applicationContext, NotificationCallbackReceiver.class);
                                callbackIntent.putExtra("currentAccount", currentAccount);
                                callbackIntent.putExtra("did", dialog_id);
                                if (button.data != null) {
                                    callbackIntent.putExtra("data", button.data);
                                }
                                callbackIntent.putExtra("mid", rowsMid);
                                builder.addAction(0, button.text, PendingIntent.getBroadcast(ApplicationLoader.applicationContext, lastButtonId++, callbackIntent, PendingIntent.FLAG_UPDATE_CURRENT));
                            }
                        }
                    }
                }
            }

            if (chat == null && user != null && user.phone != null && user.phone.length() > 0) {
                builder.addPerson("tel:+" + user.phone);
            }

            if (Build.VERSION.SDK_INT >= 26) {
                setNotificationChannel(mainNotification, builder, useSummaryNotification);
            }
            holders.add(new NotificationHolder(internalId, lowerId, name, user, chat, builder));
            wearNotificationsIds.put(dialog_id, internalId);

            if (lowerId != 0) {
                try {
                    if (serializedChat != null) {
                        serializedChat.put("reply", canReply);
                        serializedChat.put("name", name);
                        serializedChat.put("max_id", max_id);
                        serializedChat.put("max_date", max_date);
                        serializedChat.put("id", Math.abs(lowerId));
                        if (photoPath != null) {
                            serializedChat.put("photo", photoPath.dc_id + "_" + photoPath.volume_id + "_" + photoPath.secret);
                        }
                        if (serializedMsgs != null) {
                            serializedChat.put("msgs", serializedMsgs);
                        }
                        if (lowerId > 0) {
                            serializedChat.put("type", "user");
                        } else {
                            if (isChannel || isSupergroup) {
                                serializedChat.put("type", "channel");
                            } else {
                                serializedChat.put("type", "group");
                            }
                        }
                        serializedNotifications.put(serializedChat);
                    }
                } catch (JSONException ignore) {
                }
            }
        }

        if (useSummaryNotification) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("show summary with id " + notificationId);
            }
            try {
                notificationManager.notify(notificationId, mainNotification);
            } catch (SecurityException e) {
                FileLog.e(e);
                resetNotificationSound(notificationBuilder, dialogId, chatName, vibrationPattern, ledColor, sound, importance, isDefault, isInApp, isSilent, chatType);
            }
        } else {
            if (openedInBubbleDialogs.isEmpty()) {
                notificationManager.cancel(notificationId);
            }
        }

        for (int a = 0; a < oldIdsWear.size(); a++) {
            long did = oldIdsWear.keyAt(a);
            if (openedInBubbleDialogs.contains(did)) {
                continue;
            }
            Integer id = oldIdsWear.valueAt(a);
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("cancel notification id " + id);
            }
            notificationManager.cancel(id);
        }

        ArrayList<String> ids = new ArrayList<>(holders.size());
        for (int a = 0, size = holders.size(); a < size; a++) {
            NotificationHolder holder = holders.get(a);
            ids.clear();
            if (Build.VERSION.SDK_INT >= 29 && holder.lowerId != 0) {
                String shortcutId = createNotificationShortcut(holder.notification, holder.lowerId, holder.name, holder.user, holder.chat, personCache.get(holder.lowerId));
                if (shortcutId != null) {
                    ids.add(shortcutId);
                }
            }
            holder.call();
            if (!unsupportedNotificationShortcut() && !ids.isEmpty()) {
                ShortcutManagerCompat.removeDynamicShortcuts(ApplicationLoader.applicationContext, ids);
            }
        }

        if (serializedNotifications != null) {
            try {
                JSONObject s = new JSONObject();
                s.put("id", selfUserId);
                s.put("n", serializedNotifications);
                WearDataLayerListenerService.sendMessageToWatch("/notify", s.toString().getBytes(), "remote_notifications");
            } catch (Exception ignore) {
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.P)
    private void loadRoundAvatar(File avatar, Person.Builder personBuilder) {
        if (avatar != null) {
            try {
                Bitmap bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(avatar), (decoder, info, src) -> decoder.setPostProcessor((canvas) -> {
                    Path path = new Path();
                    path.setFillType(Path.FillType.INVERSE_EVEN_ODD);
                    int width = canvas.getWidth();
                    int height = canvas.getHeight();
                    path.addRoundRect(0, 0, width, height, width / 2, width / 2, Path.Direction.CW);
                    Paint paint = new Paint();
                    paint.setAntiAlias(true);
                    paint.setColor(Color.TRANSPARENT);
                    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
                    canvas.drawPath(path, paint);
                    return PixelFormat.TRANSLUCENT;
                }));
                IconCompat icon = IconCompat.createWithBitmap(bitmap);
                personBuilder.setIcon(icon);
            } catch (Throwable ignore) {

            }
        }
    }

    public void playOutChatSound() {
        if (!inChatSoundEnabled || MediaController.getInstance().isRecordingAudio()) {
            return;
        }
        try {
            if (audioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT) {
                return;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        notificationsQueue.postRunnable(() -> {
            try {
                if (Math.abs(SystemClock.elapsedRealtime() - lastSoundOutPlay) <= 100) {
                    return;
                }
                lastSoundOutPlay = SystemClock.elapsedRealtime();
                if (soundPool == null) {
                    soundPool = new SoundPool(3, AudioManager.STREAM_SYSTEM, 0);
                    soundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
                        if (status == 0) {
                            try {
                                soundPool.play(sampleId, 1.0f, 1.0f, 1, 0, 1.0f);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    });
                }
                if (soundOut == 0 && !soundOutLoaded) {
                    soundOutLoaded = true;
                    soundOut = soundPool.load(ApplicationLoader.applicationContext, R.raw.sound_out, 1);
                }
                if (soundOut != 0) {
                    try {
                        soundPool.play(soundOut, 1.0f, 1.0f, 1, 0, 1.0f);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public static final int SETTING_MUTE_HOUR = 0;
    public static final int SETTING_MUTE_8_HOURS = 1;
    public static final int SETTING_MUTE_2_DAYS = 2;
    public static final int SETTING_MUTE_FOREVER = 3;
    public static final int SETTING_MUTE_UNMUTE = 4;

    public void clearDialogNotificationsSettings(long did) {
        SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove("notify2_" + did).remove("custom_" + did);
        getMessagesStorage().setDialogFlags(did, 0);
        TLRPC.Dialog dialog = getMessagesController().dialogs_dict.get(did);
        if (dialog != null) {
            dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
        }
        editor.commit();
        getNotificationsController().updateServerNotificationsSettings(did, true);
    }

    public void setDialogNotificationsSettings(long dialog_id, int setting) {
        SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
        SharedPreferences.Editor editor = preferences.edit();
        TLRPC.Dialog dialog = MessagesController.getInstance(UserConfig.selectedAccount).dialogs_dict.get(dialog_id);
        if (setting == SETTING_MUTE_UNMUTE) {
            boolean defaultEnabled = isGlobalNotificationsEnabled(dialog_id);
            if (defaultEnabled) {
                editor.remove("notify2_" + dialog_id);
            } else {
                editor.putInt("notify2_" + dialog_id, 0);
            }
            getMessagesStorage().setDialogFlags(dialog_id, 0);
            if (dialog != null) {
                dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
            }
        } else {
            int untilTime = ConnectionsManager.getInstance(UserConfig.selectedAccount).getCurrentTime();
            if (setting == SETTING_MUTE_HOUR) {
                untilTime += 60 * 60;
            } else if (setting == SETTING_MUTE_8_HOURS) {
                untilTime += 60 * 60 * 8;
            } else if (setting == SETTING_MUTE_2_DAYS) {
                untilTime += 60 * 60 * 48;
            } else if (setting == SETTING_MUTE_FOREVER) {
                untilTime = Integer.MAX_VALUE;
            }
            long flags;
            if (setting == SETTING_MUTE_FOREVER) {
                editor.putInt("notify2_" + dialog_id, 2);
                flags = 1;
            } else {
                editor.putInt("notify2_" + dialog_id, 3);
                editor.putInt("notifyuntil_" + dialog_id, untilTime);
                flags = ((long) untilTime << 32) | 1;
            }
            NotificationsController.getInstance(UserConfig.selectedAccount).removeNotificationsForDialog(dialog_id);
            MessagesStorage.getInstance(UserConfig.selectedAccount).setDialogFlags(dialog_id, flags);
            if (dialog != null) {
                dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                dialog.notify_settings.mute_until = untilTime;
            }
        }
        editor.commit();
        updateServerNotificationsSettings(dialog_id);
    }

    public void updateServerNotificationsSettings(long dialog_id) {
        updateServerNotificationsSettings(dialog_id, true);
    }

    public void updateServerNotificationsSettings(long dialog_id, boolean post) {
        if (post) {
            getNotificationCenter().postNotificationName(NotificationCenter.notificationsSettingsUpdated);
        }
        if ((int) dialog_id == 0) {
            return;
        }
        SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
        TLRPC.TL_account_updateNotifySettings req = new TLRPC.TL_account_updateNotifySettings();
        req.settings = new TLRPC.TL_inputPeerNotifySettings();

        req.settings.flags |= 1;
        req.settings.show_previews = preferences.getBoolean("content_preview_" + dialog_id, true);

        req.settings.flags |= 2;
        req.settings.silent = preferences.getBoolean("silent_" + dialog_id, false);

        int mute_type = preferences.getInt("notify2_" + dialog_id, -1);
        if (mute_type != -1) {
            req.settings.flags |= 4;
            if (mute_type == 3) {
                req.settings.mute_until = preferences.getInt("notifyuntil_" + dialog_id, 0);
            } else {
                req.settings.mute_until = mute_type != 2 ? 0 : Integer.MAX_VALUE;
            }
        }

        req.peer = new TLRPC.TL_inputNotifyPeer();
        ((TLRPC.TL_inputNotifyPeer) req.peer).peer = getMessagesController().getInputPeer((int) dialog_id);
        getConnectionsManager().sendRequest(req, (response, error) -> {

        });
    }

    public final static int TYPE_GROUP = 0;
    public final static int TYPE_PRIVATE = 1;
    public final static int TYPE_CHANNEL = 2;

    public void updateServerNotificationsSettings(int type) {
        SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
        TLRPC.TL_account_updateNotifySettings req = new TLRPC.TL_account_updateNotifySettings();
        req.settings = new TLRPC.TL_inputPeerNotifySettings();
        req.settings.flags = 5;
        if (type == TYPE_GROUP) {
            req.peer = new TLRPC.TL_inputNotifyChats();
            req.settings.mute_until = preferences.getInt("EnableGroup2", 0);
            req.settings.show_previews = preferences.getBoolean("EnablePreviewGroup", true);
        } else if (type == TYPE_PRIVATE) {
            req.peer = new TLRPC.TL_inputNotifyUsers();
            req.settings.mute_until = preferences.getInt("EnableAll2", 0);
            req.settings.show_previews = preferences.getBoolean("EnablePreviewAll", true);
        } else {
            req.peer = new TLRPC.TL_inputNotifyBroadcasts();
            req.settings.mute_until = preferences.getInt("EnableChannel2", 0);
            req.settings.show_previews = preferences.getBoolean("EnablePreviewChannel", true);
        }
        getConnectionsManager().sendRequest(req, (response, error) -> {

        });
    }

    public boolean isGlobalNotificationsEnabled(long did) {
        return isGlobalNotificationsEnabled(did, null);
    }

    public boolean isGlobalNotificationsEnabled(long did, Boolean forceChannel) {
        int type;
        int lower_id = (int) did;
        if (lower_id < 0) {
            if (forceChannel != null) {
                if (forceChannel) {
                    type = TYPE_CHANNEL;
                } else {
                    type = TYPE_GROUP;
                }
            } else {
                TLRPC.Chat chat = getMessagesController().getChat(-lower_id);
                if (ChatObject.isChannel(chat) && !chat.megagroup) {
                    type = TYPE_CHANNEL;
                } else {
                    type = TYPE_GROUP;
                }
            }
        } else {
            type = TYPE_PRIVATE;
        }
        return isGlobalNotificationsEnabled(type);
    }

    public boolean isGlobalNotificationsEnabled(int type) {
        return getAccountInstance().getNotificationsSettings().getInt(getGlobalNotificationsKey(type), 0) < getConnectionsManager().getCurrentTime();
    }

    public void setGlobalNotificationsEnabled(int type, int time) {
        getAccountInstance().getNotificationsSettings().edit().putInt(getGlobalNotificationsKey(type), time).commit();
        updateServerNotificationsSettings(type);
        getMessagesStorage().updateMutedDialogsFiltersCounters();
        deleteNotificationChannelGlobal(type);
    }

    public static String getGlobalNotificationsKey(int type) {
        if (type == TYPE_GROUP) {
            return "EnableGroup2";
        } else if (type == TYPE_PRIVATE) {
            return "EnableAll2";
        } else {
            return "EnableChannel2";
        }
    }
}
