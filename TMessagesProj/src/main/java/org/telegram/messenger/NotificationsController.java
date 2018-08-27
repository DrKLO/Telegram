/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
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
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.Person;
import android.support.v4.app.RemoteInput;
import android.support.v4.content.FileProvider;
import android.support.v4.graphics.drawable.IconCompat;
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
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PopupNotificationActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class NotificationsController {

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
    private long opened_dialog_id = 0;
    private int lastButtonId = 5000;
    private int total_unread_count = 0;
    private int personal_count = 0;
    private boolean notifyCheck = false;
    private int lastOnlineFromOtherDevice = 0;
    private boolean inChatSoundEnabled;
    private int lastBadgeCount = -1;
    private String launcherClassName;

    public static long globalSecretChatId = -(1L << 32);

    private boolean lastNotificationIsNoData;
    public static long lastNoDataNotificationTime;

    public boolean showBadgeNumber;

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

    private int currentAccount;
    private static volatile NotificationsController Instance[] = new NotificationsController[UserConfig.MAX_ACCOUNT_COUNT];

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
        currentAccount = instance;
        notificationId = currentAccount + 1;
        notificationGroup = "messages" + (currentAccount == 0 ? "" : currentAccount);
        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
        inChatSoundEnabled = preferences.getBoolean("EnableInChatSound", true);
        showBadgeNumber = preferences.getBoolean("badgeNumber", true);

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
            notificationDelayWakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lock");
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
            } else if (lastNotificationIsNoData) {
                notificationManager.cancel(notificationId);
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
            notificationChannel = new NotificationChannel(OTHER_NOTIFICATIONS_CHANNEL, "Other", NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.enableLights(false);
            notificationChannel.enableVibration(false);
            notificationChannel.setSound(null, null);
            systemNotificationManager.createNotificationChannel(notificationChannel);
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
            setBadge(getTotalAllUnreadCount());
            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
            SharedPreferences.Editor editor = preferences.edit();
            editor.clear();
            editor.commit();

            if (Build.VERSION.SDK_INT >= 26) {
                try {
                    String keyStart = currentAccount + "channel";
                    List<NotificationChannel> list = systemNotificationManager.getNotificationChannels();
                    int count = list.size();
                    for (int a = 0; a < count; a++) {
                        NotificationChannel channel = list.get(a);
                        String id = channel.getId();
                        if (id.startsWith(keyStart)) {
                            systemNotificationManager.deleteNotificationChannel(id);
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

    public void setLastOnlineFromOtherDevice(final int time) {
        notificationsQueue.postRunnable(() -> {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("set last online from other device = " + time);
            }
            lastOnlineFromOtherDevice = time;
        });
    }

    public void removeNotificationsForDialog(long did) {
        NotificationsController.getInstance(currentAccount).processReadMessages(null, did, 0, Integer.MAX_VALUE, false);
        LongSparseArray<Integer> dialogsToUpdate = new LongSparseArray<>();
        dialogsToUpdate.put(did, 0);
        NotificationsController.getInstance(currentAccount).processDialogsUpdateRead(dialogsToUpdate);
    }

    public boolean hasMessagesToReply() {
        for (int a = 0; a < pushMessages.size(); a++) {
            MessageObject messageObject = pushMessages.get(a);
            long dialog_id = messageObject.getDialogId();
            if (messageObject.messageOwner.mentioned && messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPinMessage ||
                    (int) dialog_id == 0 || messageObject.messageOwner.to_id.channel_id != 0 && !messageObject.isMegagroup()) {
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
                        (int) dialog_id == 0 || messageObject.messageOwner.to_id.channel_id != 0 && !messageObject.isMegagroup()) {
                    continue;
                }
                popupArray.add(0, messageObject);
            }
            if (!popupArray.isEmpty() && !AndroidUtilities.needShowPasscode(false)) {
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
            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
            for (int a = 0; a < deletedMessages.size(); a++) {
                int key = deletedMessages.keyAt(a);
                long dialog_id = -key;
                ArrayList<Integer> mids = deletedMessages.get(key);
                Integer currentCount = pushDialogs.get(dialog_id);
                if (currentCount == null) {
                    currentCount = 0;
                }
                Integer newCount = currentCount;
                for (int b = 0; b < mids.size(); b++) {
                    long mid = mids.get(b);
                    mid |= ((long) key) << 32;
                    MessageObject messageObject = pushMessagesDict.get(mid);
                    if (messageObject != null) {
                        pushMessagesDict.remove(mid);
                        delayedPushMessages.remove(messageObject);
                        pushMessages.remove(messageObject);
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
            if (!popupArrayRemove.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> {
                    for (int a = 0, size = popupArrayRemove.size(); a < size; a++) {
                        popupMessages.remove(popupArrayRemove.get(a));
                    }
                });
            }
            if (old_unread_count != total_unread_count) {
                if (!notifyCheck) {
                    delayedPushMessages.clear();
                    showOrUpdateNotification(notifyCheck);
                } else {
                    scheduleNotificationDelay(lastOnlineFromOtherDevice > ConnectionsManager.getInstance(currentAccount).getCurrentTime());
                }
                final int pushDialogsCount = pushDialogs.size();
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.notificationsCountUpdated, currentAccount);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsUnreadCounterChanged, pushDialogsCount);
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
            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);

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
                });
            }
            if (old_unread_count != total_unread_count) {
                if (!notifyCheck) {
                    delayedPushMessages.clear();
                    showOrUpdateNotification(notifyCheck);
                } else {
                    scheduleNotificationDelay(lastOnlineFromOtherDevice > ConnectionsManager.getInstance(currentAccount).getCurrentTime());
                }
                final int pushDialogsCount = pushDialogs.size();
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.notificationsCountUpdated, currentAccount);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsUnreadCounterChanged, pushDialogsCount);
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
                        if (messageObject.getDialogId() == key && messageObject.getId() <= (int) messageId) {
                            if (isPersonalMessage(messageObject)) {
                                personal_count--;
                            }
                            popupArrayRemove.add(messageObject);
                            long mid = messageObject.getId();
                            if (messageObject.messageOwner.to_id.channel_id != 0) {
                                mid |= ((long) messageObject.messageOwner.to_id.channel_id) << 32;
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
                            if (messageObject.messageOwner.to_id.channel_id != 0) {
                                mid |= ((long) messageObject.messageOwner.to_id.channel_id) << 32;
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

    public void processNewMessages(final ArrayList<MessageObject> messageObjects, final boolean isLast, final boolean isFcm) {
        if (messageObjects.isEmpty()) {
            return;
        }
        final ArrayList<MessageObject> popupArrayAdd = new ArrayList<>(0);
        notificationsQueue.postRunnable(() -> {
            boolean added = false;

            LongSparseArray<Boolean> settingsCache = new LongSparseArray<>();
            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
            boolean allowPinned = preferences.getBoolean("PinnedMessages", true);
            int popup = 0;

            for (int a = 0; a < messageObjects.size(); a++) {
                MessageObject messageObject = messageObjects.get(a);
                long mid = messageObject.getId();
                long random_id = messageObject.isFcmMessage() ? messageObject.messageOwner.random_id : 0;
                if (messageObject.messageOwner.to_id.channel_id != 0) {
                    mid |= ((long) messageObject.messageOwner.to_id.channel_id) << 32;
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
                        }
                    }
                    continue;
                }
                long dialog_id = messageObject.getDialogId();
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
                    dialog_id = messageObject.messageOwner.from_id;
                }
                if (isPersonalMessage(messageObject)) {
                    personal_count++;
                }
                added = true;

                int lower_id = (int) dialog_id;
                boolean isChat = lower_id < 0;
                int index = settingsCache.indexOfKey(dialog_id);
                boolean value;
                if (index >= 0) {
                    value = settingsCache.valueAt(index);
                } else {
                    int notifyOverride = getNotifyOverride(preferences, dialog_id);
                    if (notifyOverride == -1) {
                        value = (int) dialog_id < 0 ? preferences.getBoolean("EnableGroup", true) : preferences.getBoolean("EnableAll", true);
                    } else {
                        value = notifyOverride != 2;
                    }

                    settingsCache.put(dialog_id, value);
                }
                if (lower_id != 0) {
                    if (preferences.getBoolean("custom_" + dialog_id, false)) {
                        popup = preferences.getInt("popup_" + dialog_id, 0);
                    } else {
                        popup = 0;
                    }
                    if (popup == 0) {
                        popup = preferences.getInt((int) dialog_id < 0 ? "popupGroup" : "popupAll", 0);
                    } else if (popup == 1) {
                        popup = 3;
                    } else if (popup == 2) {
                        popup = 0;
                    }
                }
                if (popup != 0 && messageObject.messageOwner.to_id.channel_id != 0 && !messageObject.isMegagroup()) {
                    popup = 0;
                }
                if (value) {
                    if (popup != 0) {
                        popupArrayAdd.add(0, messageObject);
                    }
                    delayedPushMessages.add(messageObject);
                    pushMessages.add(0, messageObject);
                    if (mid != 0) {
                        pushMessagesDict.put(mid, messageObject);
                    } else if (random_id != 0) {
                        fcmRandomMessagesDict.put(random_id, messageObject);
                    }
                    if (original_dialog_id != dialog_id) {
                        pushDialogsOverrideMention.put(original_dialog_id, 1);
                    }
                }
            }

            if (added) {
                notifyCheck = isLast;
            }

            if (!popupArrayAdd.isEmpty() && !AndroidUtilities.needShowPasscode(false)) {
                final int popupFinal = popup;
                AndroidUtilities.runOnUIThread(() -> {
                    popupMessages.addAll(0, popupArrayAdd);
                    if (ApplicationLoader.mainInterfacePaused || !ApplicationLoader.isScreenOn && !SharedConfig.isWaitingForPasscodeEnter) {
                        if (popupFinal == 3 || popupFinal == 1 && ApplicationLoader.isScreenOn || popupFinal == 2 && !ApplicationLoader.isScreenOn) {
                            Intent popupIntent = new Intent(ApplicationLoader.applicationContext, PopupNotificationActivity.class);
                            popupIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_FROM_BACKGROUND);
                            ApplicationLoader.applicationContext.startActivity(popupIntent);
                        }
                    }
                });
            }
            if (added && isFcm) {
                long dialog_id = messageObjects.get(0).getDialogId();
                int old_unread_count = total_unread_count;

                int notifyOverride = getNotifyOverride(preferences, dialog_id);
                if (notifyCheck) {
                    Integer override = pushDialogsOverrideMention.get(dialog_id);
                    if (override != null && override == 1) {
                        pushDialogsOverrideMention.put(dialog_id, 0);
                        notifyOverride = 1;
                    }
                }
                boolean canAddValue;
                if (notifyOverride == -1) {
                    canAddValue = (int) dialog_id < 0 ? preferences.getBoolean("EnableGroup", true) : preferences.getBoolean("EnableAll", true);
                } else {
                    canAddValue = notifyOverride != 2;
                }

                Integer currentCount = pushDialogs.get(dialog_id);
                Integer newCount = currentCount != null ? currentCount + 1 : 1;

                if (canAddValue) {
                    if (currentCount != null) {
                        total_unread_count -= currentCount;
                    }
                    total_unread_count += newCount;
                    pushDialogs.put(dialog_id, newCount);
                }
                if (old_unread_count != total_unread_count) {
                    if (!notifyCheck) {
                        delayedPushMessages.clear();
                        showOrUpdateNotification(notifyCheck);
                    } else {
                        scheduleNotificationDelay(lastOnlineFromOtherDevice > ConnectionsManager.getInstance(currentAccount).getCurrentTime());
                    }
                    final int pushDialogsCount = pushDialogs.size();
                    AndroidUtilities.runOnUIThread(() -> {
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.notificationsCountUpdated, currentAccount);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsUnreadCounterChanged, pushDialogsCount);
                    });
                }
                notifyCheck = false;
                if (showBadgeNumber) {
                    setBadge(getTotalAllUnreadCount());
                }
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
            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
            for (int b = 0; b < dialogsToUpdate.size(); b++) {
                long dialog_id = dialogsToUpdate.keyAt(b);

                int notifyOverride = getNotifyOverride(preferences, dialog_id);
                if (notifyCheck) {
                    Integer override = pushDialogsOverrideMention.get(dialog_id);
                    if (override != null && override == 1) {
                        pushDialogsOverrideMention.put(dialog_id, 0);
                        notifyOverride = 1;
                    }
                }
                boolean canAddValue;
                if (notifyOverride == -1) {
                    canAddValue = (int) dialog_id < 0 ? preferences.getBoolean("EnableGroup", true) : preferences.getBoolean("EnableAll", true);
                } else {
                    canAddValue = notifyOverride != 2;
                }

                Integer currentCount = pushDialogs.get(dialog_id);
                Integer newCount = dialogsToUpdate.get(dialog_id);
                if (newCount == 0) {
                    smartNotificationsDialogs.remove(dialog_id);
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
                    pushDialogs.remove(dialog_id);
                    pushDialogsOverrideMention.remove(dialog_id);
                    for (int a = 0; a < pushMessages.size(); a++) {
                        MessageObject messageObject = pushMessages.get(a);
                        if (messageObject.getDialogId() == dialog_id) {
                            if (isPersonalMessage(messageObject)) {
                                personal_count--;
                            }
                            pushMessages.remove(a);
                            a--;
                            delayedPushMessages.remove(messageObject);
                            long mid = messageObject.getId();
                            if (messageObject.messageOwner.to_id.channel_id != 0) {
                                mid |= ((long) messageObject.messageOwner.to_id.channel_id) << 32;
                            }
                            pushMessagesDict.remove(mid);
                            popupArrayToRemove.add(messageObject);
                        }
                    }
                } else if (canAddValue) {
                    total_unread_count += newCount;
                    pushDialogs.put(dialog_id, newCount);
                }
            }
            if (!popupArrayToRemove.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> {
                    for (int a = 0, size = popupArrayToRemove.size(); a < size; a++) {
                        popupMessages.remove(popupArrayToRemove.get(a));
                    }
                });
            }
            if (old_unread_count != total_unread_count) {
                if (!notifyCheck) {
                    delayedPushMessages.clear();
                    showOrUpdateNotification(notifyCheck);
                } else {
                    scheduleNotificationDelay(lastOnlineFromOtherDevice > ConnectionsManager.getInstance(currentAccount).getCurrentTime());
                }
                final int pushDialogsCount = pushDialogs.size();
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.notificationsCountUpdated, currentAccount);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsUnreadCounterChanged, pushDialogsCount);
                });
            }
            notifyCheck = false;
            if (showBadgeNumber) {
                setBadge(getTotalAllUnreadCount());
            }
        });
    }

    public void processLoadedUnreadMessages(final LongSparseArray<Integer> dialogs, final ArrayList<TLRPC.Message> messages, final ArrayList<TLRPC.User> users, final ArrayList<TLRPC.Chat> chats, final ArrayList<TLRPC.EncryptedChat> encryptedChats) {
        MessagesController.getInstance(currentAccount).putUsers(users, true);
        MessagesController.getInstance(currentAccount).putChats(chats, true);
        MessagesController.getInstance(currentAccount).putEncryptedChats(encryptedChats, true);

        notificationsQueue.postRunnable(() -> {
            pushDialogs.clear();
            pushMessages.clear();
            pushMessagesDict.clear();
            total_unread_count = 0;
            personal_count = 0;
            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
            LongSparseArray<Boolean> settingsCache = new LongSparseArray<>();

            if (messages != null) {
                for (int a = 0; a < messages.size(); a++) {
                    TLRPC.Message message = messages.get(a);
                    long mid = message.id;
                    if (message.to_id.channel_id != 0) {
                        mid |= ((long) message.to_id.channel_id) << 32;
                    }
                    if (pushMessagesDict.indexOfKey(mid) >= 0) {
                        continue;
                    }
                    MessageObject messageObject = new MessageObject(currentAccount, message, false);
                    if (isPersonalMessage(messageObject)) {
                        personal_count++;
                    }
                    long dialog_id = messageObject.getDialogId();
                    long original_dialog_id = dialog_id;
                    if (messageObject.messageOwner.mentioned) {
                        dialog_id = messageObject.messageOwner.from_id;
                    }
                    int index = settingsCache.indexOfKey(dialog_id);
                    boolean value;
                    if (index >= 0) {
                        value = settingsCache.valueAt(index);
                    } else {
                        int notifyOverride = getNotifyOverride(preferences, dialog_id);
                        if (notifyOverride == -1) {
                            value = (int) dialog_id < 0 ? preferences.getBoolean("EnableGroup", true) : preferences.getBoolean("EnableAll", true);
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
                        pushDialogsOverrideMention.put(original_dialog_id, 1);
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
                    Integer override = pushDialogsOverrideMention.get(dialog_id);
                    if (override != null && override == 1) {
                        pushDialogsOverrideMention.put(dialog_id, 0);
                        notifyOverride = 1;
                    }
                    if (notifyOverride == -1) {
                        value = (int) dialog_id < 0 ? preferences.getBoolean("EnableGroup", true) : preferences.getBoolean("EnableAll", true);
                    } else {
                        value = notifyOverride != 2;
                    }
                    settingsCache.put(dialog_id, value);
                }
                if (!value) {
                    continue;
                }
                int count = dialogs.valueAt(a);
                pushDialogs.put(dialog_id, count);
                total_unread_count += count;
            }
            final int pushDialogsCount = pushDialogs.size();
            AndroidUtilities.runOnUIThread(() -> {
                if (total_unread_count == 0) {
                    popupMessages.clear();
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pushMessagesUpdated);
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.notificationsCountUpdated, currentAccount);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsUnreadCounterChanged, pushDialogsCount);
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
                    count += controller.total_unread_count;
                }
            }
        }
        return count;
    }

    public void setBadgeEnabled(boolean enabled) {
        showBadgeNumber = enabled;
        notificationsQueue.postRunnable(() -> setBadge(getTotalAllUnreadCount()));
    }

    private void setBadge(final int count) {
        if (lastBadgeCount == count) {
            return;
        }
        lastBadgeCount = count;
        NotificationBadge.applyCount(count);
    }

    private String getShortStringForMessage(MessageObject messageObject, String[] userName) {
        if (AndroidUtilities.needShowPasscode(false) || SharedConfig.isWaitingForPasscodeEnter) {
            return LocaleController.getString("YouHaveNewMessage", R.string.YouHaveNewMessage);
        }
        long dialog_id = messageObject.messageOwner.dialog_id;
        int chat_id = messageObject.messageOwner.to_id.chat_id != 0 ? messageObject.messageOwner.to_id.chat_id : messageObject.messageOwner.to_id.channel_id;
        int from_id = messageObject.messageOwner.to_id.user_id;
        if (messageObject.isFcmMessage()) {
            if (chat_id == 0 && from_id != 0) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                if (!preferences.getBoolean("EnablePreviewAll", true)) {
                    return LocaleController.formatString("NotificationMessageNoText", R.string.NotificationMessageNoText, messageObject.localName);
                }
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
                    userName[0] = messageObject.localName;
                }
            } else if (chat_id != 0) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                if (!preferences.getBoolean("EnablePreviewGroup", true)) {
                    if (!messageObject.isMegagroup() && messageObject.messageOwner.to_id.channel_id != 0) {
                        return LocaleController.formatString("ChannelMessageNoText", R.string.ChannelMessageNoText, messageObject.localName);
                    } else {
                        return LocaleController.formatString("NotificationMessageGroupNoText", R.string.NotificationMessageGroupNoText, messageObject.localUserName, messageObject.localName);
                    }
                }
                if (messageObject.messageOwner.to_id.channel_id == 0 || messageObject.isMegagroup()) {
                    userName[0] = messageObject.localUserName;
                } else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
                    userName[0] = messageObject.localName;
                }
            }
            return messageObject.messageOwner.message;
        }
        if (from_id == 0) {
            if (messageObject.isFromUser() || messageObject.getId() < 0) {
                from_id = messageObject.messageOwner.from_id;
            } else {
                from_id = -chat_id;
            }
        } else if (from_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
            from_id = messageObject.messageOwner.from_id;
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
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(from_id);
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
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-from_id);
            if (chat != null) {
                name = chat.title;
                userName[0] = name;
            }
        }

        if (name == null) {
            return null;
        }
        TLRPC.Chat chat = null;
        if (chat_id != 0) {
            chat = MessagesController.getInstance(currentAccount).getChat(chat_id);
            if (chat == null) {
                return null;
            } else if (ChatObject.isChannel(chat) && !chat.megagroup) {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
                    userName[0] = null;
                }
            }
        }

        String msg = null;
        if ((int) dialog_id == 0) {
            return LocaleController.getString("YouHaveNewMessage", R.string.YouHaveNewMessage);
        } else {
            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
            if (chat_id == 0 && from_id != 0 && preferences.getBoolean("EnablePreviewAll", true) || chat_id != 0 && preferences.getBoolean("EnablePreviewGroup", true)) {
                if (messageObject.messageOwner instanceof TLRPC.TL_messageService) {
                    userName[0] = null;
                    if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserJoined) {
                        return LocaleController.formatString("NotificationContactJoined", R.string.NotificationContactJoined, name);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                        return LocaleController.formatString("NotificationContactNewPhoto", R.string.NotificationContactNewPhoto, name);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionLoginUnknownLocation) {
                        String date = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.getInstance().formatterYear.format(((long) messageObject.messageOwner.date) * 1000), LocaleController.getInstance().formatterDay.format(((long) messageObject.messageOwner.date) * 1000));
                        return LocaleController.formatString("NotificationUnrecognizedDevice", R.string.NotificationUnrecognizedDevice, UserConfig.getInstance(currentAccount).getCurrentUser().first_name, date, messageObject.messageOwner.action.title, messageObject.messageOwner.action.address);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGameScore || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPaymentSent) {
                        return messageObject.messageText.toString();
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPhoneCall) {
                        TLRPC.PhoneCallDiscardReason reason = messageObject.messageOwner.action.reason;
                        if (!messageObject.isOut() && (reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed)) {
                            return LocaleController.getString("CallMessageIncomingMissed", R.string.CallMessageIncomingMissed);
                        }
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatAddUser) {
                        int singleUserId = messageObject.messageOwner.action.user_id;
                        if (singleUserId == 0 && messageObject.messageOwner.action.users.size() == 1) {
                            singleUserId = messageObject.messageOwner.action.users.get(0);
                        }
                        if (singleUserId != 0) {
                            if (messageObject.messageOwner.to_id.channel_id != 0 && !chat.megagroup) {
                                return LocaleController.formatString("ChannelAddedByNotification", R.string.ChannelAddedByNotification, name, chat.title);
                            } else {
                                if (singleUserId == UserConfig.getInstance(currentAccount).getClientUserId()) {
                                    return LocaleController.formatString("NotificationInvitedToGroup", R.string.NotificationInvitedToGroup, name, chat.title);
                                } else {
                                    TLRPC.User u2 = MessagesController.getInstance(currentAccount).getUser(singleUserId);
                                    if (u2 == null) {
                                        return null;
                                    }
                                    if (from_id == u2.id) {
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
                            StringBuilder names = new StringBuilder("");
                            for (int a = 0; a < messageObject.messageOwner.action.users.size(); a++) {
                                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(messageObject.messageOwner.action.users.get(a));
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
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatJoinedByLink) {
                        return LocaleController.formatString("NotificationInvitedToGroupByLink", R.string.NotificationInvitedToGroupByLink, name, chat.title);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatEditTitle) {
                        return LocaleController.formatString("NotificationEditedGroupName", R.string.NotificationEditedGroupName, name, messageObject.messageOwner.action.title);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatEditPhoto || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
                        if (messageObject.messageOwner.to_id.channel_id != 0 && !chat.megagroup) {
                            return LocaleController.formatString("ChannelPhotoEditNotification", R.string.ChannelPhotoEditNotification, chat.title);
                        } else {
                            return LocaleController.formatString("NotificationEditedGroupPhoto", R.string.NotificationEditedGroupPhoto, name, chat.title);
                        }
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                        if (messageObject.messageOwner.action.user_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                            return LocaleController.formatString("NotificationGroupKickYou", R.string.NotificationGroupKickYou, name, chat.title);
                        } else if (messageObject.messageOwner.action.user_id == from_id) {
                            return LocaleController.formatString("NotificationGroupLeftMember", R.string.NotificationGroupLeftMember, name, chat.title);
                        } else {
                            TLRPC.User u2 = MessagesController.getInstance(currentAccount).getUser(messageObject.messageOwner.action.user_id);
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
                        if (chat != null && chat.megagroup) {
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
                                } else if (object.isSticker()) {
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
                                    return LocaleController.formatString("NotificationActionPinnedContact", R.string.NotificationActionPinnedContact, name, chat.title);
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
                        } else {
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
                                } else if (object.isSticker()) {
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
                                    return LocaleController.formatString("NotificationActionPinnedContactChannel", R.string.NotificationActionPinnedContactChannel, chat.title);
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
                        }
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGameScore) {
                        return messageObject.messageText.toString();
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
                    } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                        return LocaleController.getString("AttachLocation", R.string.AttachLocation);
                    } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                        return LocaleController.getString("AttachLiveLocation", R.string.AttachLiveLocation);
                    } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                        if (messageObject.isSticker()) {
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
                                return  "\uD83D\uDCCE " + messageObject.messageOwner.message;
                            } else {
                                return LocaleController.getString("AttachDocument", R.string.AttachDocument);
                            }
                        }
                    }
                }
            } else {
                return LocaleController.getString("Message", R.string.Message);
            }
        }
        return null;
    }

    private String getStringForMessage(MessageObject messageObject, boolean shortMessage, boolean text[]) {
        if (AndroidUtilities.needShowPasscode(false) || SharedConfig.isWaitingForPasscodeEnter) {
            return LocaleController.getString("YouHaveNewMessage", R.string.YouHaveNewMessage);
        }
        long dialog_id = messageObject.messageOwner.dialog_id;
        int chat_id = messageObject.messageOwner.to_id.chat_id != 0 ? messageObject.messageOwner.to_id.chat_id : messageObject.messageOwner.to_id.channel_id;
        int from_id = messageObject.messageOwner.to_id.user_id;
        if (messageObject.isFcmMessage()) {
            if (chat_id == 0 && from_id != 0) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                if (!preferences.getBoolean("EnablePreviewAll", true)) {
                    return LocaleController.formatString("NotificationMessageNoText", R.string.NotificationMessageNoText, messageObject.localName);
                }
            } else if (chat_id != 0) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                if (!preferences.getBoolean("EnablePreviewGroup", true)) {
                    if (!messageObject.isMegagroup() && messageObject.messageOwner.to_id.channel_id != 0) {
                        return LocaleController.formatString("ChannelMessageNoText", R.string.ChannelMessageNoText, messageObject.localName);
                    } else {
                        return LocaleController.formatString("NotificationMessageGroupNoText", R.string.NotificationMessageGroupNoText, messageObject.localUserName, messageObject.localName);
                    }
                }
            }
            text[0] = true;
            return (String) messageObject.messageText;
        }
        if (from_id == 0) {
            if (messageObject.isFromUser() || messageObject.getId() < 0) {
                from_id = messageObject.messageOwner.from_id;
            } else {
                from_id = -chat_id;
            }
        } else if (from_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
            from_id = messageObject.messageOwner.from_id;
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
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(from_id);
            if (user != null) {
                name = UserObject.getUserName(user);
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-from_id);
            if (chat != null) {
                name = chat.title;
            }
        }

        if (name == null) {
            return null;
        }
        TLRPC.Chat chat = null;
        if (chat_id != 0) {
            chat = MessagesController.getInstance(currentAccount).getChat(chat_id);
            if (chat == null) {
                return null;
            }
        }

        String msg = null;
        if ((int) dialog_id == 0) {
            msg = LocaleController.getString("YouHaveNewMessage", R.string.YouHaveNewMessage);
        } else {
            if (chat_id == 0 && from_id != 0) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                if (preferences.getBoolean("EnablePreviewAll", true)) {
                    if (messageObject.messageOwner instanceof TLRPC.TL_messageService) {
                        if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserJoined) {
                            msg = LocaleController.formatString("NotificationContactJoined", R.string.NotificationContactJoined, name);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                            msg = LocaleController.formatString("NotificationContactNewPhoto", R.string.NotificationContactNewPhoto, name);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionLoginUnknownLocation) {
                            String date = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.getInstance().formatterYear.format(((long) messageObject.messageOwner.date) * 1000), LocaleController.getInstance().formatterDay.format(((long) messageObject.messageOwner.date) * 1000));
                            msg = LocaleController.formatString("NotificationUnrecognizedDevice", R.string.NotificationUnrecognizedDevice, UserConfig.getInstance(currentAccount).getCurrentUser().first_name, date, messageObject.messageOwner.action.title, messageObject.messageOwner.action.address);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGameScore || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPaymentSent) {
                            msg = messageObject.messageText.toString();
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPhoneCall) {
                            TLRPC.PhoneCallDiscardReason reason = messageObject.messageOwner.action.reason;
                            if (!messageObject.isOut() && (reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed)) {
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
                            msg = LocaleController.formatString("NotificationMessageContact", R.string.NotificationMessageContact, name);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                            msg = LocaleController.formatString("NotificationMessageMap", R.string.NotificationMessageMap, name);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                            msg = LocaleController.formatString("NotificationMessageLiveLocation", R.string.NotificationMessageLiveLocation, name);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                            if (messageObject.isSticker()) {
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
                        }
                    }
                } else {
                    msg = LocaleController.formatString("NotificationMessageNoText", R.string.NotificationMessageNoText, name);
                }
            } else if (chat_id != 0) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                if (preferences.getBoolean("EnablePreviewGroup", true)) {
                    if (messageObject.messageOwner instanceof TLRPC.TL_messageService) {
                        if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatAddUser) {
                            int singleUserId = messageObject.messageOwner.action.user_id;
                            if (singleUserId == 0 && messageObject.messageOwner.action.users.size() == 1) {
                                singleUserId = messageObject.messageOwner.action.users.get(0);
                            }
                            if (singleUserId != 0) {
                                if (messageObject.messageOwner.to_id.channel_id != 0 && !chat.megagroup) {
                                    msg = LocaleController.formatString("ChannelAddedByNotification", R.string.ChannelAddedByNotification, name, chat.title);
                                } else {
                                    if (singleUserId == UserConfig.getInstance(currentAccount).getClientUserId()) {
                                        msg = LocaleController.formatString("NotificationInvitedToGroup", R.string.NotificationInvitedToGroup, name, chat.title);
                                    } else {
                                        TLRPC.User u2 = MessagesController.getInstance(currentAccount).getUser(singleUserId);
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
                                StringBuilder names = new StringBuilder("");
                                for (int a = 0; a < messageObject.messageOwner.action.users.size(); a++) {
                                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(messageObject.messageOwner.action.users.get(a));
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
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatJoinedByLink) {
                            msg = LocaleController.formatString("NotificationInvitedToGroupByLink", R.string.NotificationInvitedToGroupByLink, name, chat.title);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatEditTitle) {
                            msg = LocaleController.formatString("NotificationEditedGroupName", R.string.NotificationEditedGroupName, name, messageObject.messageOwner.action.title);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatEditPhoto || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
                            if (messageObject.messageOwner.to_id.channel_id != 0 && !chat.megagroup) {
                                msg = LocaleController.formatString("ChannelPhotoEditNotification", R.string.ChannelPhotoEditNotification, chat.title);
                            } else {
                                msg = LocaleController.formatString("NotificationEditedGroupPhoto", R.string.NotificationEditedGroupPhoto, name, chat.title);
                            }
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                            if (messageObject.messageOwner.action.user_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                                msg = LocaleController.formatString("NotificationGroupKickYou", R.string.NotificationGroupKickYou, name, chat.title);
                            } else if (messageObject.messageOwner.action.user_id == from_id) {
                                msg = LocaleController.formatString("NotificationGroupLeftMember", R.string.NotificationGroupLeftMember, name, chat.title);
                            } else {
                                TLRPC.User u2 = MessagesController.getInstance(currentAccount).getUser(messageObject.messageOwner.action.user_id);
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
                            if (chat != null && chat.megagroup) {
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
                                    } else if (object.isSticker()) {
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
                                        msg = LocaleController.formatString("NotificationActionPinnedContact", R.string.NotificationActionPinnedContact, name, chat.title);
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
                                    } else if (object.isSticker()) {
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
                                        msg = LocaleController.formatString("NotificationActionPinnedContactChannel", R.string.NotificationActionPinnedContactChannel, chat.title);
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
                            if (!shortMessage && messageObject.messageOwner.message != null && messageObject.messageOwner.message.length() != 0) {
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
                            msg = LocaleController.formatString("ChannelMessageContact", R.string.ChannelMessageContact, name);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                            msg = LocaleController.formatString("ChannelMessageMap", R.string.ChannelMessageMap, name);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                            msg = LocaleController.formatString("ChannelMessageLiveLocation", R.string.ChannelMessageLiveLocation, name);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                            if (messageObject.isSticker()) {
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
                        }
                    } else {
                        if (messageObject.isMediaEmpty()) {
                            if (!shortMessage && messageObject.messageOwner.message != null && messageObject.messageOwner.message.length() != 0) {
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
                                msg = LocaleController.formatString("NotificationMessageGroupVideo", R.string.NotificationMessageGroupVideo, name, chat.title);
                            }
                        } else if (messageObject.isVoice()) {
                            msg = LocaleController.formatString("NotificationMessageGroupAudio", R.string.NotificationMessageGroupAudio, name, chat.title);
                        } else if (messageObject.isRoundVideo()) {
                            msg = LocaleController.formatString("NotificationMessageGroupRound", R.string.NotificationMessageGroupRound, name, chat.title);
                        } else if (messageObject.isMusic()) {
                            msg = LocaleController.formatString("NotificationMessageGroupMusic", R.string.NotificationMessageGroupMusic, name, chat.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                            msg = LocaleController.formatString("NotificationMessageGroupContact", R.string.NotificationMessageGroupContact, name, chat.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                            msg = LocaleController.formatString("NotificationMessageGroupGame", R.string.NotificationMessageGroupGame, name, chat.title, messageObject.messageOwner.media.game.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                            msg = LocaleController.formatString("NotificationMessageGroupMap", R.string.NotificationMessageGroupMap, name, chat.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                            msg = LocaleController.formatString("NotificationMessageGroupLiveLocation", R.string.NotificationMessageGroupLiveLocation, name, chat.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                            if (messageObject.isSticker()) {
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
                        }
                    }
                } else {
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
            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
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
        return messageObject.messageOwner.to_id != null && messageObject.messageOwner.to_id.chat_id == 0 && messageObject.messageOwner.to_id.channel_id == 0
                && (messageObject.messageOwner.action == null || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionEmpty);
    }

    private int getNotifyOverride(SharedPreferences preferences, long dialog_id) {
        int notifyOverride = preferences.getInt("notify2_" + dialog_id, -1);
        if (notifyOverride == 3) {
            int muteUntil = preferences.getInt("notifyuntil_" + dialog_id, 0);
            if (muteUntil >= ConnectionsManager.getInstance(currentAccount).getCurrentTime()) {
                notifyOverride = 2;
            }
        }
        return notifyOverride;
    }

    private void dismissNotification() {
        try {
            lastNotificationIsNoData = false;
            notificationManager.cancel(notificationId);
            pushMessages.clear();
            pushMessagesDict.clear();
            lastWearNotifiedMessageId.clear();
            for (int a = 0; a < wearNotificationsIds.size(); a++) {
                notificationManager.cancel(wearNotificationsIds.valueAt(a));
            }
            wearNotificationsIds.clear();
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pushMessagesUpdated));
            if (WearDataLayerListenerService.isWatchConnected()) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("id", UserConfig.getInstance(currentAccount).getClientUserId());
                    o.put("cancel_all", true);
                    WearDataLayerListenerService.sendMessageToWatch("/notify", o.toString().getBytes("UTF-8"), "remote_notifications");
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
            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
            int notifyOverride = getNotifyOverride(preferences, opened_dialog_id);
            if (notifyOverride == 2) {
                return;
            }
            notificationsQueue.postRunnable(() -> {
                if (Math.abs(System.currentTimeMillis() - lastSoundPlay) <= 500) {
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
            if (pattern[0] != 0) {
                return false;
            }
        }
        return true;
    }

    @TargetApi(26)
    private String validateChannelId(long dialogId, String name, long[] vibrationPattern, int ledColor, Uri sound, int importance, long[] configVibrationPattern, Uri configSound, int configImportance) {
        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
        String key = "org.telegram.key" + dialogId;
        String channelId = preferences.getString(key, null);
        String settings = preferences.getString(key + "_s", null);
        boolean edited = false;
        StringBuilder newSettings = new StringBuilder();
        String newSettingsHash;

        /*NotificationChannel existingChannel = systemNotificationManager.getNotificationChannel(channelId);
        if (existingChannel != null) {
            int channelImportance = existingChannel.getImportance();
            Uri channelSound = existingChannel.getSound();
            long[] channelVibrationPattern = existingChannel.getVibrationPattern();
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
            newSettingsHash = Utilities.MD5(newSettings.toString());
            newSettings.setLength(0);
            if (!settings.equals(newSettingsHash)) {
                SharedPreferences.Editor editor = null;
                if (channelImportance != configImportance) {
                    if (editor == null) {
                        editor = preferences.edit();
                    }
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
                    editor.putInt("priority_" + dialogId, priority);
                    if (configImportance == importance) {
                        importance = channelImportance;
                        edited = true;
                    }
                }
                if (configSound == null || channelSound != null || configSound != null && channelSound == null || !configSound.equals(channelSound)) {
                    if (editor == null) {
                        editor = preferences.edit();
                    }
                    String newSound;
                    if (channelSound == null) {
                        newSound = "NoSound";
                        editor.putString("sound_" + dialogId, "NoSound");
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
                            editor.putString("sound_" + dialogId, ringtoneName);
                        }
                    }
                    editor.putString("sound_path_" + dialogId, newSound);
                    if (configSound == null && sound == null || configSound != null && sound != null || configSound.equals(sound)) {
                        sound = channelSound;
                        edited = true;
                    }
                }
                boolean vibrate = existingChannel.shouldVibrate();
                if (isEmptyVibration(configVibrationPattern) != vibrate) {
                    if (editor == null) {
                        editor = preferences.edit();
                    }
                    editor.putInt("vibrate_" + dialogId, vibrate ? 0 : 2);
                }
                if (editor != null) {
                    editor.putBoolean("custom_" + dialogId, true);
                    editor.commit();
                }
            }
        }*/

        for (int a = 0; a < vibrationPattern.length; a++) {
            newSettings.append(vibrationPattern[a]);
        }
        newSettings.append(ledColor);
        if (sound != null) {
            newSettings.append(sound.toString());
        }
        newSettings.append(importance);

        newSettingsHash = Utilities.MD5(newSettings.toString());
        if (channelId != null && !settings.equals(newSettingsHash)) {
            if (edited) {
                preferences.edit().putString(key, channelId).putString(key + "_s", newSettingsHash).commit();
            } else {
                systemNotificationManager.deleteNotificationChannel(channelId);
                channelId = null;
            }
        }
        if (channelId == null) {
            channelId = currentAccount + "channel" + dialogId + "_" + Utilities.random.nextLong();
            NotificationChannel notificationChannel = new NotificationChannel(channelId, name, importance);
            if (ledColor != 0) {
                notificationChannel.enableLights(true);
                notificationChannel.setLightColor(ledColor);
            }
            if (!isEmptyVibration(vibrationPattern)) {
                notificationChannel.enableVibration(true);
                if (vibrationPattern != null && vibrationPattern.length > 0) {
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
            systemNotificationManager.createNotificationChannel(notificationChannel);
            preferences.edit().putString(key, channelId).putString(key + "_s", newSettingsHash).commit();
        }
        return channelId;
    }

    private void showOrUpdateNotification(boolean notifyAboutLast) {
        if (!UserConfig.getInstance(currentAccount).isClientActivated() || pushMessages.isEmpty()) {
            dismissNotification();
            return;
        }
        try {
            ConnectionsManager.getInstance(currentAccount).resumeNetworkMaybe();

            MessageObject lastMessageObject = pushMessages.get(0);
            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
            int dismissDate = preferences.getInt("dismissDate", 0);
            if (lastMessageObject.messageOwner.date <= dismissDate) {
                dismissNotification();
                return;
            }

            long dialog_id = lastMessageObject.getDialogId();
            long override_dialog_id = dialog_id;
            if (lastMessageObject.messageOwner.mentioned) {
                override_dialog_id = lastMessageObject.messageOwner.from_id;
            }
            int mid = lastMessageObject.getId();
            int chat_id = lastMessageObject.messageOwner.to_id.chat_id != 0 ? lastMessageObject.messageOwner.to_id.chat_id : lastMessageObject.messageOwner.to_id.channel_id;
            int user_id = lastMessageObject.messageOwner.to_id.user_id;
            if (user_id == 0) {
                user_id = lastMessageObject.messageOwner.from_id;
            } else if (user_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                user_id = lastMessageObject.messageOwner.from_id;
            }

            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
            TLRPC.Chat chat = null;
            if (chat_id != 0) {
                chat = MessagesController.getInstance(currentAccount).getChat(chat_id);
            }
            TLRPC.FileLocation photoPath = null;

            boolean notifyDisabled = false;
            int needVibrate = 0;
            String choosenSoundPath;
            int ledColor = 0xff0000ff;
            int priority = 0;

            int notifyOverride = getNotifyOverride(preferences, override_dialog_id);
            boolean value;
            if (notifyOverride == -1) {
                value = (int) dialog_id < 0 ? preferences.getBoolean("EnableGroup", true) : preferences.getBoolean("EnableAll", true);
            } else {
                value = notifyOverride != 2;
            }
            if (!notifyAboutLast || !value) {
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
                        dialogInfo = new Point(1, (int) (System.currentTimeMillis() / 1000));
                        smartNotificationsDialogs.put(dialog_id, dialogInfo);
                    } else {
                        int lastTime = dialogInfo.y;
                        if (lastTime + notifyDelay < System.currentTimeMillis() / 1000) {
                            dialogInfo.set(1, (int) (System.currentTimeMillis() / 1000));
                        } else {
                            int count = dialogInfo.x;
                            if (count < notifyMaxCount) {
                                dialogInfo.set(count + 1, (int) (System.currentTimeMillis() / 1000));
                            } else {
                                notifyDisabled = true;
                            }
                        }
                    }
                }
            }

            String defaultPath = Settings.System.DEFAULT_NOTIFICATION_URI.getPath();

            //
            boolean inAppSounds = preferences.getBoolean("EnableInAppSounds", true);
            boolean inAppVibrate = preferences.getBoolean("EnableInAppVibrate", true);
            boolean inAppPreview = preferences.getBoolean("EnableInAppPreview", true);
            boolean inAppPriority = preferences.getBoolean("EnableInAppPriority", false);
            boolean custom;
            int vibrateOverride;
            int priorityOverride;
            if (custom = preferences.getBoolean("custom_" + dialog_id, false)) {
                vibrateOverride = preferences.getInt("vibrate_" + dialog_id, 0);
                priorityOverride = preferences.getInt("priority_" + dialog_id, 3);
                choosenSoundPath = preferences.getString("sound_path_" + dialog_id, null);
            } else {
                vibrateOverride = 0;
                priorityOverride = 3;
                choosenSoundPath = null;
            }
            boolean vibrateOnlyIfSilent = false;

            if (chat_id != 0) {
                if (choosenSoundPath != null && choosenSoundPath.equals(defaultPath)) {
                    choosenSoundPath = null;
                } else if (choosenSoundPath == null) {
                    choosenSoundPath = preferences.getString("GroupSoundPath", defaultPath);
                }
                needVibrate = preferences.getInt("vibrate_group", 0);
                priority = preferences.getInt("priority_group", 1);
                ledColor = preferences.getInt("GroupLed", 0xff0000ff);
            } else if (user_id != 0) {
                if (choosenSoundPath != null && choosenSoundPath.equals(defaultPath)) {
                    choosenSoundPath = null;
                } else if (choosenSoundPath == null) {
                    choosenSoundPath = preferences.getString("GlobalSoundPath", defaultPath);
                }
                needVibrate = preferences.getInt("vibrate_messages", 0);
                priority = preferences.getInt("priority_messages", 1);
                ledColor = preferences.getInt("MessagesLed", 0xff0000ff);
            }
            if (custom) {
                if (preferences.contains("color_" + dialog_id)) {
                    ledColor = preferences.getInt("color_" + dialog_id, 0);
                }
            }

            if (priorityOverride != 3) {
                priority = priorityOverride;
            }

            if (needVibrate == 4) {
                vibrateOnlyIfSilent = true;
                needVibrate = 0;
            }
            if (needVibrate == 2 && (vibrateOverride == 1 || vibrateOverride == 3) || needVibrate != 2 && vibrateOverride == 2 || vibrateOverride != 0 && vibrateOverride != 4) {
                needVibrate = vibrateOverride;
            }
            if (!ApplicationLoader.mainInterfacePaused) {
                if (!inAppSounds) {
                    choosenSoundPath = null;
                }
                if (!inAppVibrate) {
                    needVibrate = 2;
                }
                if (!inAppPriority) {
                    priority = 0;
                } else if (priority == 2) {
                    priority = 1;
                }
            }
            if (vibrateOnlyIfSilent && needVibrate != 2) {
                try {
                    int mode = audioManager.getRingerMode();
                    if (mode != AudioManager.RINGER_MODE_SILENT && mode != AudioManager.RINGER_MODE_VIBRATE) {
                        needVibrate = 2;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            //
            Uri configSound = null;
            long configVibrationPattern[] = null;
            int configImportance = 0;
            if (Build.VERSION.SDK_INT >= 26) {
                if (needVibrate == 2) {
                    configVibrationPattern = new long[]{0, 0};
                } else if (needVibrate == 1) {
                    configVibrationPattern = new long[]{0, 100, 0, 100};
                } else if (needVibrate == 0 || needVibrate == 4) {
                    configVibrationPattern = new long[]{};
                } else if (needVibrate == 3) {
                    configVibrationPattern = new long[]{0, 1000};
                }
                if (choosenSoundPath != null && !choosenSoundPath.equals("NoSound")) {
                    if (choosenSoundPath.equals(defaultPath)) {
                        configSound = Settings.System.DEFAULT_NOTIFICATION_URI;
                    } else {
                        configSound = Uri.parse(choosenSoundPath);
                    }
                }
                if (priority == 0) {
                    configImportance = NotificationManager.IMPORTANCE_DEFAULT;
                } else if (priority == 1 || priority == 2) {
                    configImportance = NotificationManager.IMPORTANCE_HIGH;
                } else if (priority == 4) {
                    configImportance = NotificationManager.IMPORTANCE_MIN;
                } else if (priority == 5) {
                    configImportance = NotificationManager.IMPORTANCE_LOW;
                }
            }

            if (notifyDisabled) {
                needVibrate = 0;
                priority = 0;
                ledColor = 0;
                choosenSoundPath = null;
            }

            Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
            intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            if ((int) dialog_id != 0) {
                if (pushDialogs.size() == 1) {
                    if (chat_id != 0) {
                        intent.putExtra("chatId", chat_id);
                    } else if (user_id != 0) {
                        intent.putExtra("userId", user_id);
                    }
                }
                if (AndroidUtilities.needShowPasscode(false) || SharedConfig.isWaitingForPasscodeEnter) {
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
            if ((int) dialog_id == 0 || pushDialogs.size() > 1 || AndroidUtilities.needShowPasscode(false) || SharedConfig.isWaitingForPasscodeEnter) {
                name = LocaleController.getString("AppName", R.string.AppName);
                replace = false;
            } else {
                name = chatName;
            }

            String detailText;
            if (UserConfig.getActivatedAccountsCount() > 1) {
                if (pushDialogs.size() == 1) {
                    detailText = UserObject.getFirstName(UserConfig.getInstance(currentAccount).getCurrentUser());
                } else {
                    detailText = UserObject.getFirstName(UserConfig.getInstance(currentAccount).getCurrentUser()) + "・";
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

            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(ApplicationLoader.applicationContext)
                    .setContentTitle(name)
                    .setSmallIcon(R.drawable.notification)
                    .setAutoCancel(true)
                    .setNumber(total_unread_count)
                    .setContentIntent(contentIntent)
                    .setGroup(notificationGroup)
                    .setGroupSummary(true)
                    .setShowWhen(true)
                    .setWhen(((long) lastMessageObject.messageOwner.date) * 1000)
                    .setColor(0xff2ca5e0);

            long vibrationPattern[] = null;
            int importance = 0;
            Uri sound = null;

            mBuilder.setCategory(NotificationCompat.CATEGORY_MESSAGE);
            if (chat == null && user != null && user.phone != null && user.phone.length() > 0) {
                mBuilder.addPerson("tel:+" + user.phone);
            }

            int silent = 2;
            String lastMessage = null;
            boolean hasNewMessages = false;
            if (pushMessages.size() == 1) {
                MessageObject messageObject = pushMessages.get(0);
                boolean text[] = new boolean[1];
                String message = lastMessage = getStringForMessage(messageObject, false, text);
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
                boolean text[] = new boolean[1];
                for (int i = 0; i < count; i++) {
                    MessageObject messageObject = pushMessages.get(i);
                    String message = getStringForMessage(messageObject, false, text);
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
                    } catch (Throwable e) {
                        //ignore
                    }
                }
            }

            if (!notifyAboutLast || silent == 1) {
                mBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
                if (Build.VERSION.SDK_INT >= 26) {
                    importance = NotificationManager.IMPORTANCE_LOW;
                }
            } else {
                if (priority == 0) {
                    mBuilder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
                    if (Build.VERSION.SDK_INT >= 26) {
                        importance = NotificationManager.IMPORTANCE_DEFAULT;
                    }
                } else if (priority == 1 || priority == 2) {
                    mBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
                    if (Build.VERSION.SDK_INT >= 26) {
                        importance = NotificationManager.IMPORTANCE_HIGH;
                    }
                } else if (priority == 4) {
                    mBuilder.setPriority(NotificationCompat.PRIORITY_MIN);
                    if (Build.VERSION.SDK_INT >= 26) {
                        importance = NotificationManager.IMPORTANCE_MIN;
                    }
                } else if (priority == 5) {
                    mBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
                    if (Build.VERSION.SDK_INT >= 26) {
                        importance = NotificationManager.IMPORTANCE_LOW;
                    }
                }
            }

            if (silent != 1 && !notifyDisabled) {
                if (ApplicationLoader.mainInterfacePaused || inAppPreview) {
                    if (lastMessage.length() > 100) {
                        lastMessage = lastMessage.substring(0, 100).replace('\n', ' ').trim() + "...";
                    }
                    mBuilder.setTicker(lastMessage);
                }
                if (!MediaController.getInstance().isRecordingAudio()) {
                    if (choosenSoundPath != null && !choosenSoundPath.equals("NoSound")) {
                        if (Build.VERSION.SDK_INT >= 26) {
                            if (choosenSoundPath.equals(defaultPath)) {
                                sound = Settings.System.DEFAULT_NOTIFICATION_URI;
                            } else {
                                sound = Uri.parse(choosenSoundPath);
                            }
                        } else {
                            if (choosenSoundPath.equals(defaultPath)) {
                                mBuilder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AudioManager.STREAM_NOTIFICATION);
                            } else {
                                mBuilder.setSound(Uri.parse(choosenSoundPath), AudioManager.STREAM_NOTIFICATION);
                            }
                        }
                    }
                }
                if (ledColor != 0) {
                    mBuilder.setLights(ledColor, 1000, 1000);
                }
                if (needVibrate == 2 || MediaController.getInstance().isRecordingAudio()) {
                    mBuilder.setVibrate(vibrationPattern = new long[]{0, 0});
                } else if (needVibrate == 1) {
                    mBuilder.setVibrate(vibrationPattern = new long[]{0, 100, 0, 100});
                } else if (needVibrate == 0 || needVibrate == 4) {
                    mBuilder.setDefaults(NotificationCompat.DEFAULT_VIBRATE);
                    vibrationPattern = new long[]{};
                } else if (needVibrate == 3) {
                    mBuilder.setVibrate(vibrationPattern = new long[]{0, 1000});
                }
            } else {
                mBuilder.setVibrate(vibrationPattern = new long[]{0, 0});
            }

            boolean hasCallback = false;
            if (!AndroidUtilities.needShowPasscode(false) && !SharedConfig.isWaitingForPasscodeEnter && lastMessageObject.getDialogId() == 777000) {
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
            if (Build.VERSION.SDK_INT >= 26) {
                mBuilder.setChannelId(validateChannelId(dialog_id, chatName, vibrationPattern, ledColor, sound, importance, configVibrationPattern, configSound, configImportance));
            }
            showExtraNotifications(mBuilder, notifyAboutLast, detailText);
            lastNotificationIsNoData = false;
            scheduleNotificationRepeat();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @SuppressLint("InlinedApi")
    private void showExtraNotifications(NotificationCompat.Builder notificationBuilder, boolean notifyAboutLast, String summary) {
        Notification mainNotification = notificationBuilder.build();
        if (Build.VERSION.SDK_INT < 18) {
            notificationManager.notify(notificationId, mainNotification);
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("show summary notification by SDK check");
            }
            return;
        }

        ArrayList<Long> sortedDialogs = new ArrayList<>();
        LongSparseArray<ArrayList<MessageObject>> messagesByDialogs = new LongSparseArray<>();
        for (int a = 0; a < pushMessages.size(); a++) {
            MessageObject messageObject = pushMessages.get(a);
            long dialog_id = messageObject.getDialogId();

            ArrayList<MessageObject> arrayList = messagesByDialogs.get(dialog_id);
            if (arrayList == null) {
                arrayList = new ArrayList<>();
                messagesByDialogs.put(dialog_id, arrayList);
                sortedDialogs.add(0, dialog_id);
            }
            arrayList.add(messageObject);
        }

        LongSparseArray<Integer> oldIdsWear = wearNotificationsIds.clone();
        wearNotificationsIds.clear();

        class NotificationHolder {
            int id;
            Notification notification;

            NotificationHolder(int i, Notification n) {
                id = i;
                notification = n;
            }

            void call() {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.w("show dialog notification with id " + id);
                }
                notificationManager.notify(id, notification);
            }
        }

        ArrayList<NotificationHolder> holders = new ArrayList<>();
        JSONArray serializedNotifications = null;
        if (WearDataLayerListenerService.isWatchConnected()) {
            serializedNotifications = new JSONArray();
        }

        boolean useSummaryNotification = Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1 || Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1 && sortedDialogs.size() > 1;
        if (useSummaryNotification && Build.VERSION.SDK_INT >= 26) {
            checkOtherNotificationsChannel();
        }

        for (int b = 0, size = sortedDialogs.size(); b < size; b++) {
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
            LongSparseArray<Person> personCache = new LongSparseArray<>();

            if (lowerId != 0) {
                canReply = lowerId != 777000;
                if (lowerId > 0) {
                    user = MessagesController.getInstance(currentAccount).getUser(lowerId);
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
                } else {
                    chat = MessagesController.getInstance(currentAccount).getChat(-lowerId);
                    if (chat == null) {
                        if (lastMessageObject.isFcmMessage()) {
                            isSupergroup = lastMessageObject.isMegagroup();
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
                    TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(highId);
                    if (encryptedChat == null) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.w("not found secret chat to show dialog notification " + highId);
                        }
                        continue;
                    }
                    user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
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

            if (AndroidUtilities.needShowPasscode(false) || SharedConfig.isWaitingForPasscodeEnter) {
                name = LocaleController.getString("AppName", R.string.AppName);
                photoPath = null;
                canReply = false;
            }

            if (photoPath != null) {
                avatalFile = FileLoader.getPathToAttach(photoPath, true);
                BitmapDrawable img = ImageLoader.getInstance().getImageFromMemory(photoPath, null, "50_50");
                if (img != null) {
                    avatarBitmap = img.getBitmap();
                } else if (Build.VERSION.SDK_INT < 28) {
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

            NotificationCompat.CarExtender.UnreadConversation.Builder unreadConvBuilder = new NotificationCompat.CarExtender.UnreadConversation.Builder(name).setLatestTimestamp((long) max_date * 1000);

            Intent msgHeardIntent = new Intent(ApplicationLoader.applicationContext, AutoMessageHeardReceiver.class);
            msgHeardIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            msgHeardIntent.setAction("org.telegram.messenger.ACTION_MESSAGE_HEARD");
            msgHeardIntent.putExtra("dialog_id", dialog_id);
            msgHeardIntent.putExtra("max_id", max_id);
            msgHeardIntent.putExtra("currentAccount", currentAccount);
            PendingIntent msgHeardPendingIntent = PendingIntent.getBroadcast(ApplicationLoader.applicationContext, internalId, msgHeardIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            unreadConvBuilder.setReadPendingIntent(msgHeardPendingIntent);

            NotificationCompat.Action wearReplyAction = null;

            if ((!isChannel || isSupergroup) && canReply && !SharedConfig.isWaitingForPasscodeEnter) {
                Intent msgReplyIntent = new Intent(ApplicationLoader.applicationContext, AutoMessageReplyReceiver.class);
                msgReplyIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                msgReplyIntent.setAction("org.telegram.messenger.ACTION_MESSAGE_REPLY");
                msgReplyIntent.putExtra("dialog_id", dialog_id);
                msgReplyIntent.putExtra("max_id", max_id);
                msgReplyIntent.putExtra("currentAccount", currentAccount);
                PendingIntent msgReplyPendingIntent = PendingIntent.getBroadcast(ApplicationLoader.applicationContext, internalId, msgReplyIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                RemoteInput remoteInputAuto = new RemoteInput.Builder(EXTRA_VOICE_REPLY).setLabel(LocaleController.getString("Reply", R.string.Reply)).build();
                unreadConvBuilder.setReplyAction(msgReplyPendingIntent, remoteInputAuto);

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
                wearReplyAction = new NotificationCompat.Action.Builder(R.drawable.ic_reply_icon, replyToString, replyPendingIntent).setAllowGeneratedReplies(true).addRemoteInput(remoteInputWear).build();
            }

            Integer count = pushDialogs.get(dialog_id);
            if (count == null) {
                count = 0;
            }
            String conversationName = String.format("%1$s (%2$s)", name, LocaleController.formatPluralString("NewMessages", Math.max(count, messageObjects.size())));

            NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle("");
            messagingStyle.setConversationTitle(conversationName);
            if (!isChannel && lowerId < 0) {
                messagingStyle.setGroupConversation(true);
            }

            StringBuilder text = new StringBuilder();
            String senderName[] = new String[1];
            ArrayList<TLRPC.TL_keyboardButtonRow> rows = null;
            int rowsMid = 0;
            JSONArray serializedMsgs = null;
            if (serializedChat != null) {
                serializedMsgs = new JSONArray();
            }
            for (int a = messageObjects.size() - 1; a >= 0; a--) {
                MessageObject messageObject = messageObjects.get(a);
                String message = getShortStringForMessage(messageObject, senderName);
                if (message == null) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.w("message text is null for " + messageObject.getId() + " did = " + messageObject.getDialogId());
                    }
                    continue;
                }
                if (text.length() > 0) {
                    text.append("\n\n");
                }
                if (senderName[0] != null) {
                    text.append(String.format("%1$s: %2$s", senderName[0], message));
                } else {
                    text.append(message);
                }

                unreadConvBuilder.addMessage(message);

                long uid;
                if (lowerId > 0) {
                    uid = lowerId;
                } else if (isChannel) {
                    uid = -lowerId;
                } else if (lowerId < 0) {
                    uid = messageObject.getFromId();
                } else {
                    uid = dialog_id;
                }
                Person person = personCache.get(uid);
                if (person == null) {
                    Person.Builder personBuilder = new Person.Builder().setName(senderName[0] == null ? "" : senderName[0]);
                    if (Build.VERSION.SDK_INT >= 28) {
                        File avatar = null;
                        if (lowerId > 0 || isChannel) {
                            avatar = avatalFile;
                        } else if (lowerId < 0) {
                            int fromId = messageObject.getFromId();
                            TLRPC.User sender = MessagesController.getInstance(currentAccount).getUser(fromId);
                            if (sender == null) {
                                sender = MessagesStorage.getInstance(currentAccount).getUserSync(fromId);
                                if (sender != null) {
                                    MessagesController.getInstance(currentAccount).putUser(sender, true);
                                }
                            }
                            if (sender != null && sender.photo != null && sender.photo.photo_small != null && sender.photo.photo_small.volume_id != 0 && sender.photo.photo_small.local_id != 0) {
                                avatar = FileLoader.getPathToAttach(sender.photo.photo_small, true);
                            }
                        }
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
                    person = personBuilder.build();
                    personCache.put(uid, person);
                }

                messagingStyle.addMessage(message, ((long) messageObject.messageOwner.date) * 1000, person);
                if (messageObject.isVoice()) {
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

                if (serializedMsgs != null) {
                    try {
                        JSONObject jmsg = new JSONObject();
                        jmsg.put("text", message);
                        jmsg.put("date", messageObject.messageOwner.date);
                        if (messageObject.isFromUser() && lowerId < 0) {
                            TLRPC.User sender = MessagesController.getInstance(currentAccount).getUser(messageObject.getFromId());
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
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
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
            PendingIntent readPendingIntent = PendingIntent.getBroadcast(ApplicationLoader.applicationContext, internalId, msgHeardIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationCompat.Action readAction = new NotificationCompat.Action.Builder(R.drawable.menu_read, LocaleController.getString("MarkAsRead", R.string.MarkAsRead), readPendingIntent).build();

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
            wearableExtender.setBridgeTag("tgaccount" + UserConfig.getInstance(currentAccount).getClientUserId());

            long date = ((long) messageObjects.get(0).messageOwner.date) * 1000;

            NotificationCompat.Builder builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext)
                    .setContentTitle(name)
                    .setSmallIcon(R.drawable.notification)
                    .setContentText(text.toString())
                    .setAutoCancel(true)
                    .setNumber(messageObjects.size())
                    .setColor(0xff2ca5e0)
                    .setGroupSummary(false)
                    .setWhen(date)
                    .setShowWhen(true)
                    .setShortcutId("sdid_" + dialog_id)
                    .setStyle(messagingStyle)
                    .setContentIntent(contentIntent)
                    .extend(wearableExtender)
                    .setSortKey("" + (Long.MAX_VALUE - date))
                    .extend(new NotificationCompat.CarExtender().setUnreadConversation(unreadConvBuilder.build()))
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE);

            if (useSummaryNotification) {
                builder.setGroup(notificationGroup);
                builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);
            }

            if (wearReplyAction != null) {
                builder.addAction(wearReplyAction);
            }
            builder.addAction(readAction);
            if (pushDialogs.size() == 1 && !TextUtils.isEmpty(summary)) {
                builder.setSubText(summary);
            }
            if (lowerId == 0) {
                builder.setLocalOnly(true);
            }
            if (avatarBitmap != null && Build.VERSION.SDK_INT < 28) {
                builder.setLargeIcon(avatarBitmap);
            }

            if (!AndroidUtilities.needShowPasscode(false) && !SharedConfig.isWaitingForPasscodeEnter && rows != null) {
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

            if (chat == null && user != null && user.phone != null && user.phone.length() > 0) {
                builder.addPerson("tel:+" + user.phone);
            }

            if (Build.VERSION.SDK_INT >= 26) {
                if (useSummaryNotification) {
                    builder.setChannelId(OTHER_NOTIFICATIONS_CHANNEL);
                } else {
                    builder.setChannelId(mainNotification.getChannelId());
                }
            }
            holders.add(new NotificationHolder(internalId, builder.build()));
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
                        } else if (lowerId < 0) {
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
            notificationManager.notify(notificationId, mainNotification);
        } else {
            notificationManager.cancel(notificationId);
        }
        for (int a = 0, size = holders.size(); a < size; a++) {
            holders.get(a).call();
        }

        for (int a = 0; a < oldIdsWear.size(); a++) {
            Integer id = oldIdsWear.valueAt(a);
            if (BuildVars.LOGS_ENABLED) {
                FileLog.w("cancel notification id " + id);
            }
            notificationManager.cancel(id);
        }
        if (serializedNotifications != null) {
            try {
                JSONObject s = new JSONObject();
                s.put("id", UserConfig.getInstance(currentAccount).getClientUserId());
                s.put("n", serializedNotifications);
                WearDataLayerListenerService.sendMessageToWatch("/notify", s.toString().getBytes("UTF-8"), "remote_notifications");
            } catch (Exception ignore) {
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
                if (Math.abs(System.currentTimeMillis() - lastSoundOutPlay) <= 100) {
                    return;
                }
                lastSoundOutPlay = System.currentTimeMillis();
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

    public void updateServerNotificationsSettings(long dialog_id) {
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.notificationsSettingsUpdated);
        if ((int) dialog_id == 0) {
            return;
        }
        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
        TLRPC.TL_account_updateNotifySettings req = new TLRPC.TL_account_updateNotifySettings();
        req.settings = new TLRPC.TL_inputPeerNotifySettings();

        req.settings.flags |= 1;
        req.settings.show_previews = preferences.getBoolean("preview_" + dialog_id, true);

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
        ((TLRPC.TL_inputNotifyPeer) req.peer).peer = MessagesController.getInstance(currentAccount).getInputPeer((int) dialog_id);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

        });
    }
}
