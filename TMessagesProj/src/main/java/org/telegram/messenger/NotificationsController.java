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
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Pair;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import androidx.collection.LongSparseArray;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.content.FileProvider;
import androidx.core.content.LocusIdCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.IconCompat;

import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BubbleActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.spoilers.SpoilerEffect;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PopupNotificationActivity;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.StoryEntry;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

public class NotificationsController extends BaseController {

    public static final String EXTRA_VOICE_REPLY = "extra_voice_reply";
    public static String OTHER_NOTIFICATIONS_CHANNEL = null;

    private static final DispatchQueue notificationsQueue = new DispatchQueue("notificationsQueue");
    private final ArrayList<MessageObject> pushMessages = new ArrayList<>();
    private final ArrayList<MessageObject> delayedPushMessages = new ArrayList<>();
    private final LongSparseArray<SparseArray<MessageObject>> pushMessagesDict = new LongSparseArray<>();
    private final LongSparseArray<MessageObject> fcmRandomMessagesDict = new LongSparseArray<>();
    private final LongSparseArray<Point> smartNotificationsDialogs = new LongSparseArray<>();
    private static NotificationManagerCompat notificationManager = null;
    private static NotificationManager systemNotificationManager = null;
    private final LongSparseArray<Integer> pushDialogs = new LongSparseArray<>();
    private final LongSparseArray<Integer> wearNotificationsIds = new LongSparseArray<>();
    private final LongSparseArray<Integer> lastWearNotifiedMessageId = new LongSparseArray<>();
    private final LongSparseArray<Integer> pushDialogsOverrideMention = new LongSparseArray<>();
    public final ArrayList<MessageObject> popupMessages = new ArrayList<>();
    public ArrayList<MessageObject> popupReplyMessages = new ArrayList<>();
    private final HashSet<Long> openedInBubbleDialogs = new HashSet<>();
    private final ArrayList<StoryNotification> storyPushMessages = new ArrayList<>();
    private final LongSparseArray<StoryNotification> storyPushMessagesDict = new LongSparseArray<>();
    private long openedDialogId = 0;
    private long openedTopicId = 0;
    private int lastButtonId = 5000;
    private int total_unread_count = 0;
    private int personalCount = 0;
    private boolean notifyCheck = false;
    private int lastOnlineFromOtherDevice = 0;
    private boolean inChatSoundEnabled;
    private int lastBadgeCount = -1;
    private String launcherClassName;

    public long lastNotificationChannelCreateTime;

    private Boolean groupsCreated;
    private boolean channelGroupsCreated;

    public static long globalSecretChatId = DialogObject.makeEncryptedDialogId(1);

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

    private SpoilerEffect mediaSpoilerEffect = new SpoilerEffect();

    public static final int SETTING_SOUND_ON = 0;
    public static final int SETTING_SOUND_OFF = 1;

    NotificationsSettingsFacade dialogsNotificationsFacade;

    static {
        if (Build.VERSION.SDK_INT >= 26 && ApplicationLoader.applicationContext != null) {
            notificationManager = NotificationManagerCompat.from(ApplicationLoader.applicationContext);
            systemNotificationManager = (NotificationManager) ApplicationLoader.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
            checkOtherNotificationsChannel();
        }
        audioManager = (AudioManager) ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE);
    }

    private static volatile NotificationsController[] Instance = new NotificationsController[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Object[] lockObjects = new Object[UserConfig.MAX_ACCOUNT_COUNT];
    static {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            lockObjects[i] = new Object();
        }
    }

    public static NotificationsController getInstance(int num) {
        NotificationsController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (lockObjects[num]) {
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

        dialogsNotificationsFacade = new NotificationsSettingsFacade(currentAccount);
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
            try {
                systemNotificationManager.deleteNotificationChannel(OTHER_NOTIFICATIONS_CHANNEL);
            } catch (Exception e) {
                FileLog.e(e);
            }
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

    private static final LongSparseArray<String> sharedPrefCachedKeys = new LongSparseArray<>();

    public static String getSharedPrefKey(long dialog_id, long topicId) {
        return getSharedPrefKey(dialog_id, topicId, false);
    }

    public static String getSharedPrefKey(long dialog_id, long topicId, boolean backgroundThread) {
        if (backgroundThread) {
            String key;
            if (topicId != 0) {
                key = String.format(Locale.US, "%d_%d", dialog_id, topicId);
            } else {
                key = String.valueOf(dialog_id);
            }
            return key;
        }
//        if (BuildVars.DEBUG_PRIVATE_VERSION) {
//            if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
//                throw new IllegalStateException("Not on main thread!");
//            }
//        }
        long hash = dialog_id + ((long) topicId << 12);
        int index = sharedPrefCachedKeys.indexOfKey(hash);
        if (index >= 0) {
            return sharedPrefCachedKeys.valueAt(index);
        }
        String key;
        if (topicId != 0) {
            key = String.format(Locale.US, "%d_%d", dialog_id, topicId);
        } else {
            key = String.valueOf(dialog_id);
        }
        sharedPrefCachedKeys.put(hash, key);
        return key;
    }

    public void muteUntil(long did, long topicId, int selectedTimeInSeconds) {
        if (did != 0) {
            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
            SharedPreferences.Editor editor = preferences.edit();
            long flags;
            boolean override = topicId != 0;
            boolean defaultEnabled = NotificationsController.getInstance(currentAccount).isGlobalNotificationsEnabled(did, false, false);

            String sharedPrefKey = NotificationsController.getSharedPrefKey(did, topicId);
            if (selectedTimeInSeconds == Integer.MAX_VALUE) {
                if (!defaultEnabled && !override) {
                    editor.remove("notify2_" + sharedPrefKey);
                    flags = 0;
                } else {
                    editor.putInt("notify2_" + sharedPrefKey, 2);
                    flags = 1;
                }
            } else {
                editor.putInt("notify2_" + sharedPrefKey, 3);
                editor.putInt("notifyuntil_" + sharedPrefKey,  getConnectionsManager().getCurrentTime() + selectedTimeInSeconds);
                flags = ((long) selectedTimeInSeconds << 32) | 1;
            }
            editor.apply();
            if (topicId == 0) {
                NotificationsController.getInstance(currentAccount).removeNotificationsForDialog(did);
                MessagesStorage.getInstance(currentAccount).setDialogFlags(did, flags);
                TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(did);
                if (dialog != null) {
                    dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                    if (selectedTimeInSeconds != Integer.MAX_VALUE || defaultEnabled) {
                        dialog.notify_settings.mute_until = selectedTimeInSeconds;
                    }
                }
            }
            NotificationsController.getInstance(currentAccount).updateServerNotificationsSettings(did, topicId);
        }
    }

    public void cleanup() {
        popupMessages.clear();
        popupReplyMessages.clear();
        channelGroupsCreated = false;
        notificationsQueue.postRunnable(() -> {
            openedDialogId = 0;
            openedTopicId = 0;
            total_unread_count = 0;
            personalCount = 0;
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
                    systemNotificationManager.deleteNotificationChannelGroup("stories" + currentAccount);
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

    public void setOpenedDialogId(long dialog_id, long topicId) {
        notificationsQueue.postRunnable(() -> {
            openedDialogId = dialog_id;
            openedTopicId = topicId;
        });
    }

    public void setOpenedInBubble(long dialogId, boolean opened) {
        notificationsQueue.postRunnable(() -> {
            if (opened) {
                openedInBubbleDialogs.add(dialogId);
            } else {
                openedInBubbleDialogs.remove(dialogId);
            }
        });
    }

    public void setLastOnlineFromOtherDevice(int time) {
        notificationsQueue.postRunnable(() -> {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("set last online from other device = " + time);
            }
            lastOnlineFromOtherDevice = time;
        });
    }

    public void removeNotificationsForDialog(long did) {
        processReadMessages(null, did, 0, Integer.MAX_VALUE, false);
        LongSparseIntArray dialogsToUpdate = new LongSparseIntArray();
        dialogsToUpdate.put(did, 0);
        processDialogsUpdateRead(dialogsToUpdate);
    }

    public boolean hasMessagesToReply() {
        for (int a = 0; a < pushMessages.size(); a++) {
            MessageObject messageObject = pushMessages.get(a);
            long dialog_id = messageObject.getDialogId();
            if (messageObject.isReactionPush ||
                messageObject.messageOwner.mentioned && messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPinMessage ||
                DialogObject.isEncryptedDialog(dialog_id) ||
                messageObject.messageOwner.peer_id.channel_id != 0 && !messageObject.isSupergroup() ||
                dialog_id == UserObject.VERIFY
            ) {
                continue;
            }
            return true;
        }
        return false;
    }

    protected void forceShowPopupForReply() {
        notificationsQueue.postRunnable(() -> {
            ArrayList<MessageObject> popupArray = new ArrayList<>();
            for (int a = 0; a < pushMessages.size(); a++) {
                MessageObject messageObject = pushMessages.get(a);
                long dialog_id = messageObject.getDialogId();
                if (messageObject.messageOwner.mentioned && messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPinMessage ||
                        DialogObject.isEncryptedDialog(dialog_id) || messageObject.messageOwner.peer_id.channel_id != 0 && !messageObject.isSupergroup()) {
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

    public void removeDeletedMessagesFromNotifications(LongSparseArray<ArrayList<Integer>> deletedMessages, boolean isReactions) {
        ArrayList<MessageObject> popupArrayRemove = new ArrayList<>(0);
        notificationsQueue.postRunnable(() -> {
            int old_unread_count = total_unread_count;
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
            for (int a = 0; a < deletedMessages.size(); a++) {
                long key = deletedMessages.keyAt(a);
                SparseArray<MessageObject> sparseArray = pushMessagesDict.get(key);
                if (sparseArray == null) {
                    continue;
                }
                ArrayList<Integer> mids = deletedMessages.get(key);
                for (int b = 0, N = mids.size(); b < N; b++) {
                    int mid = mids.get(b);
                    MessageObject messageObject = sparseArray.get(mid);
                    if (messageObject != null) {
                        if (messageObject.isStoryReactionPush)
                            continue;
                        if (isReactions && !messageObject.isReactionPush) {
                            continue;
                        }
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
                            if (getMessagesController().isForum(dialogId)) {
                                total_unread_count -= currentCount > 0 ? 1 : 0;
                                total_unread_count += newCount > 0 ? 1 : 0;
                            } else {
                                total_unread_count -= currentCount;
                                total_unread_count += newCount;
                            }
                            pushDialogs.put(dialogId, newCount);
                        }
                        if (newCount == 0) {
                            pushDialogs.remove(dialogId);
                            pushDialogsOverrideMention.remove(dialogId);
                        }

                        sparseArray.remove(mid);
                        delayedPushMessages.remove(messageObject);
                        pushMessages.remove(messageObject);
                        if (isPersonalMessage(messageObject)) {
                            personalCount--;
                        }
                        popupArrayRemove.add(messageObject);
                    }
                }
                if (sparseArray.size() == 0) {
                    pushMessagesDict.remove(key);
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
                int pushDialogsCount = pushDialogs.size();
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

    public void removeDeletedHisoryFromNotifications(LongSparseIntArray deletedMessages) {
        ArrayList<MessageObject> popupArrayRemove = new ArrayList<>(0);
        notificationsQueue.postRunnable(() -> {
            int old_unread_count = total_unread_count;
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();

            for (int a = 0; a < deletedMessages.size(); a++) {
                long key = deletedMessages.keyAt(a);
                long dialogId = -key;
                long id = deletedMessages.get(key);
                Integer currentCount = pushDialogs.get(dialogId);
                if (currentCount == null) {
                    currentCount = 0;
                }
                Integer newCount = currentCount;

                for (int c = 0; c < pushMessages.size(); c++) {
                    MessageObject messageObject = pushMessages.get(c);
                    if (messageObject.getDialogId() == dialogId && messageObject.getId() <= id) {
                        SparseArray<MessageObject> sparseArray = pushMessagesDict.get(dialogId);
                        if (sparseArray != null) {
                            sparseArray.remove(messageObject.getId());
                            if (sparseArray.size() == 0) {
                                pushMessagesDict.remove(dialogId);
                            }
                        }
                        delayedPushMessages.remove(messageObject);
                        pushMessages.remove(messageObject);
                        c--;
                        if (isPersonalMessage(messageObject)) {
                            personalCount--;
                        }
                        popupArrayRemove.add(messageObject);
                        newCount--;
                    }
                }

                if (newCount <= 0) {
                    newCount = 0;
                    smartNotificationsDialogs.remove(dialogId);
                }
                if (!newCount.equals(currentCount)) {
                    if (getMessagesController().isForum(dialogId)) {
                        total_unread_count -= currentCount > 0 ? 1 : 0;
                        total_unread_count += newCount > 0 ? 1 : 0;
                    } else {
                        total_unread_count -= currentCount;
                        total_unread_count += newCount;
                    }
                    pushDialogs.put(dialogId, newCount);
                }
                if (newCount == 0) {
                    pushDialogs.remove(dialogId);
                    pushDialogsOverrideMention.remove(dialogId);
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
                int pushDialogsCount = pushDialogs.size();
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

    public void processSeenStoryReactions(long dialogId, int storyId) {
        if (dialogId != getUserConfig().getClientUserId())
            return;
        notificationsQueue.postRunnable(() -> {
            boolean changed = false;
            for (int i = 0; i < pushMessages.size(); ++i) {
                MessageObject msgObject = pushMessages.get(i);
                if (msgObject.isStoryReactionPush && Math.abs(msgObject.getId()) == storyId) {
                    pushMessages.remove(i);
                    SparseArray<MessageObject> msgs = pushMessagesDict.get(msgObject.getDialogId());
                    if (msgs != null) msgs.remove(msgObject.getId());
                    if (msgs != null && msgs.size() <= 0) pushMessagesDict.remove(msgObject.getDialogId());
                    ArrayList<Integer> ids = new ArrayList<>();
                    ids.add(msgObject.getId());
                    getMessagesStorage().deletePushMessages(msgObject.getDialogId(), ids);
                    i--;
                    changed = true;
                }
            }
            if (changed) {
                showOrUpdateNotification(false);
            }
        });
    }

    public void processDeleteStory(long dialogId, int storyId) {
        notificationsQueue.postRunnable(() -> {
            boolean changed = false;
            StoryNotification notification = storyPushMessagesDict.get(dialogId);
            if (notification != null) {
                notification.dateByIds.remove(storyId);
                if (notification.dateByIds.isEmpty()) {
                    storyPushMessagesDict.remove(dialogId);
                    storyPushMessages.remove(notification);
                    changed = true;
                    getMessagesStorage().deleteStoryPushMessage(dialogId);
                } else {
                    getMessagesStorage().putStoryPushMessage(notification);
                }
            }
            if (changed) {
                showOrUpdateNotification(false);
            }
        });
    }

    public void processReadStories(long dialogId, int maxId) {
        notificationsQueue.postRunnable(() -> {
            boolean changed = false;
            StoryNotification notification = storyPushMessagesDict.get(dialogId);
            if (notification != null) {
//                if (notification.maxId <= maxId) {
                    storyPushMessagesDict.remove(dialogId);
                    storyPushMessages.remove(notification);
                    changed = true;
                    getMessagesStorage().deleteStoryPushMessage(dialogId);
//                } else {
//                    StoryNotification newNotification = new StoryNotification(dialogId, notification.localName, Math.max(notification.minId, maxId), Math.max(notification.maxId, maxId), notification.date);
//                    storyPushMessagesDict.put(dialogId, newNotification);
//                    storyPushMessages.remove(notification);
//                    storyPushMessages.add(newNotification);
//                    changed = true;
//                    getMessagesStorage().putStoryPushMessage(newNotification);
//                }
            }
            if (changed) {
                showOrUpdateNotification(false);
                updateStoryPushesRunnable();
            }
        });
    }

    public void processIgnoreStories() {
        notificationsQueue.postRunnable(() -> {
            boolean changed = !storyPushMessages.isEmpty();
            storyPushMessages.clear();
            storyPushMessagesDict.clear();
            getMessagesStorage().deleteAllStoryPushMessages();
            if (changed) {
                showOrUpdateNotification(false);
            }
        });
    }

    public void processIgnoreStoryReactions() {
        notificationsQueue.postRunnable(() -> {
            boolean changed = false;
            for (int i = 0; i < pushMessages.size(); ++i) {
                MessageObject msg = pushMessages.get(i);
                if (msg != null && msg.isStoryReactionPush) {
                    pushMessages.remove(i);
                    i--;
                    SparseArray<MessageObject> arr = pushMessagesDict.get(msg.getDialogId());
                    if (arr != null) arr.remove(msg.getId());
                    if (arr != null && arr.size() <= 0) pushMessagesDict.remove(msg.getDialogId());
                    changed = true;
                }
            }
            getMessagesStorage().deleteAllStoryReactionPushMessages();
            if (changed) {
                showOrUpdateNotification(false);
            }
        });
    }

    public void processIgnoreStories(long dialogId) {
        notificationsQueue.postRunnable(() -> {
            boolean changed = !storyPushMessages.isEmpty();
            storyPushMessages.clear();
            storyPushMessagesDict.clear();
            getMessagesStorage().deleteStoryPushMessage(dialogId);
            if (changed) {
                showOrUpdateNotification(false);
            }
        });
    }

    public void processReadStories() {

    }

    public void processReadMessages(LongSparseIntArray inbox, long dialogId, int maxDate, int maxId, boolean isPopup) {
        ArrayList<MessageObject> popupArrayRemove = new ArrayList<>(0);
        notificationsQueue.postRunnable(() -> {
            if (inbox != null) {
                for (int b = 0; b < inbox.size(); b++) {
                    long key = inbox.keyAt(b);
                    int messageId = inbox.get(key);
                    for (int a = 0; a < pushMessages.size(); a++) {
                        MessageObject messageObject = pushMessages.get(a);
                        if (!messageObject.messageOwner.from_scheduled && messageObject.getDialogId() == key && messageObject.getId() <= messageId && !messageObject.isStoryReactionPush) {
                            if (isPersonalMessage(messageObject)) {
                                personalCount--;
                            }
                            popupArrayRemove.add(messageObject);
                            long did;
                            if (messageObject.isStoryReactionPush) {
                                did = messageObject.getDialogId();
                            } else if (messageObject.messageOwner.peer_id.channel_id != 0) {
                                did = -messageObject.messageOwner.peer_id.channel_id;
                            } else {
                                did = 0;
                            }
                            SparseArray<MessageObject> sparseArray = pushMessagesDict.get(did);
                            if (sparseArray != null) {
                                sparseArray.remove(messageObject.getId());
                                if (sparseArray.size() == 0) {
                                    pushMessagesDict.remove(did);
                                }
                            }
                            delayedPushMessages.remove(messageObject);
                            pushMessages.remove(a);
                            a--;
                        }
                    }
                }
            }
            if (dialogId != 0 && (maxId != 0 || maxDate != 0)) {
                for (int a = 0; a < pushMessages.size(); a++) {
                    MessageObject messageObject = pushMessages.get(a);
                    if (messageObject.getDialogId() == dialogId && !messageObject.isStoryReactionPush) {
                        boolean remove = false;
                        if (maxDate != 0) {
                            if (messageObject.messageOwner.date <= maxDate) {
                                remove = true;
                            }
                        } else {
                            if (!isPopup) {
                                if (messageObject.getId() <= maxId || maxId < 0) {
                                    remove = true;
                                }
                            } else {
                                if (messageObject.getId() == maxId || maxId < 0) {
                                    remove = true;
                                }
                            }
                        }
                        if (remove) {
                            if (isPersonalMessage(messageObject)) {
                                personalCount--;
                            }
                            long did;
                            if (messageObject.isStoryReactionPush) {
                                did = messageObject.getDialogId();
                            } else if (messageObject.messageOwner.peer_id.channel_id != 0) {
                                did = -messageObject.messageOwner.peer_id.channel_id;
                            } else {
                                did = 0;
                            }
                            SparseArray<MessageObject> sparseArray = pushMessagesDict.get(did);
                            if (sparseArray != null) {
                                sparseArray.remove(messageObject.getId());
                                if (sparseArray.size() == 0) {
                                    pushMessagesDict.remove(did);
                                }
                            }
                            pushMessages.remove(a);
                            delayedPushMessages.remove(messageObject);
                            popupArrayRemove.add(messageObject);
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

    private int addToPopupMessages(ArrayList<MessageObject> popupArrayAdd, MessageObject messageObject, long dialogId, boolean isChannel, SharedPreferences preferences) {
        if (messageObject.isStoryReactionPush) return 0;
        int popup = 0;
        if (!DialogObject.isEncryptedDialog(dialogId)) {
            if (preferences.getBoolean("custom_" + dialogId, false)) {
                popup = preferences.getInt("popup_" + dialogId, 0);
            }
            if (popup == 0) {
                if (isChannel) {
                    popup = preferences.getInt("popupChannel", 0);
                } else {
                    popup = preferences.getInt(DialogObject.isChatDialog(dialogId) ? "popupGroup" : "popupAll", 0);
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

    public void processEditedMessages(LongSparseArray<ArrayList<MessageObject>> editedMessages) {
        if (editedMessages.size() == 0) {
            return;
        }
        ArrayList<MessageObject> popupArrayAdd = new ArrayList<>(0);
        notificationsQueue.postRunnable(() -> {
            boolean updated = false;
            for (int a = 0, N = editedMessages.size(); a < N; a++) {
                long dialogId = editedMessages.keyAt(a);
                ArrayList<MessageObject> messages = editedMessages.valueAt(a);
                for (int b = 0, N2 = messages.size(); b < N2; b++) {
                    MessageObject messageObject = messages.get(b);
                    long did;
                    if (messageObject.isStoryReactionPush) {
                        did = messageObject.getDialogId();
                    } else if (messageObject.messageOwner.peer_id.channel_id != 0) {
                        did = -messageObject.messageOwner.peer_id.channel_id;
                    } else {
                        did = 0;
                    }
                    SparseArray<MessageObject> sparseArray = pushMessagesDict.get(did);
                    if (sparseArray == null) {
                        break;
                    }
                    MessageObject oldMessage = sparseArray.get(messageObject.getId());
                    if (oldMessage != null && (oldMessage.isReactionPush || oldMessage.isStoryReactionPush)) {
                        oldMessage = null;
                    }
                    if (oldMessage != null) {
                        updated = true;
                        sparseArray.put(messageObject.getId(), messageObject);
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

    public void processNewMessages(ArrayList<MessageObject> messageObjects, boolean isLast, boolean isFcm, CountDownLatch countDownLatch) {
        FileLog.d("NotificationsController: processNewMessages msgs.size()=" + (messageObjects == null ? "null" : messageObjects.size()) + " isLast=" + isLast + " isFcm=" + isFcm + ")");
        if (messageObjects.isEmpty()) {
            if (countDownLatch != null) {
                countDownLatch.countDown();
            }
            return;
        }

        ArrayList<MessageObject> popupArrayAdd = new ArrayList<>(0);
        notificationsQueue.postRunnable(() -> {
            boolean added = false;
            boolean edited = false;
            boolean storiesUpdated = false;

            LongSparseArray<Boolean> settingsCache = new LongSparseArray<>();
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
            boolean allowPinned = preferences.getBoolean("PinnedMessages", true);
            int popup = 0;
            boolean hasScheduled = false;

            for (int a = 0; a < messageObjects.size(); a++) {
                MessageObject messageObject = messageObjects.get(a);
                if (messageObject.messageOwner != null && (messageObject.isImportedForward() ||
                        messageObject.messageOwner.action instanceof TLRPC.TL_messageActionSetMessagesTTL ||
                        messageObject.messageOwner.silent && (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionContactSignUp || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserJoined)) ||
                        MessageObject.isTopicActionMessage(messageObject)) {
                    FileLog.d("skipped message because 1");
                    continue;
                }
                if (messageObject.isStoryPush) {
                    long date = messageObject.messageOwner == null ? System.currentTimeMillis() : messageObject.messageOwner.date * 1000L;
                    long dialogId = messageObject.getDialogId();
                    int id = messageObject.getId();
                    StoryNotification oldNotification = storyPushMessagesDict.get(dialogId);
                    StoryNotification notification;
                    if (oldNotification != null) {
                        edited = true;
                        oldNotification.dateByIds.put(id, new Pair<>(date, date + 86400000L));
                        if (oldNotification.hidden != messageObject.isStoryPushHidden) {
                            oldNotification.hidden = messageObject.isStoryPushHidden;
                            storiesUpdated = true;
                        }
                        oldNotification.date = oldNotification.getLeastDate();
                        getMessagesStorage().putStoryPushMessage(oldNotification);
                    } else {
                        added = true;
                        storiesUpdated = true;
                        notification = new StoryNotification(dialogId, messageObject.localName, id, date);
                        notification.hidden = messageObject.isStoryPushHidden;
                        storyPushMessages.add(notification);
                        storyPushMessagesDict.put(dialogId, notification);
                        getMessagesStorage().putStoryPushMessage(notification);
                    }

                    Collections.sort(storyPushMessages, Comparator.comparingLong(n -> n.date));
                    continue;
                }
                int mid = messageObject.getId();
                long randomId = messageObject.isFcmMessage() ? messageObject.messageOwner.random_id : 0;
                long dialogId = messageObject.getDialogId();
                boolean isChannel;
                if (messageObject.isFcmMessage()) {
                    isChannel = messageObject.localChannel;
                } else if (DialogObject.isChatDialog(dialogId)) {
                    TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
                    isChannel = ChatObject.isChannel(chat) && !chat.megagroup;
                } else {
                    isChannel = false;
                }
                long did;
                if (messageObject.isStoryReactionPush) {
                    did = messageObject.getDialogId();
                } else if (messageObject.messageOwner.peer_id.channel_id != 0) {
                    did = -messageObject.messageOwner.peer_id.channel_id;
                } else {
                    did = 0;
                }
                SparseArray<MessageObject> sparseArray = pushMessagesDict.get(did);
                MessageObject oldMessageObject = sparseArray != null ? sparseArray.get(mid) : null;
                if (oldMessageObject == null && messageObject.messageOwner.random_id != 0) {
                    oldMessageObject = fcmRandomMessagesDict.get(messageObject.messageOwner.random_id);
                    if (oldMessageObject != null) {
                        fcmRandomMessagesDict.remove(messageObject.messageOwner.random_id);
                    }
                }
                if (oldMessageObject != null) {
                    if (oldMessageObject.isFcmMessage()) {
                        if (sparseArray == null) {
                            sparseArray = new SparseArray<>();
                            pushMessagesDict.put(did, sparseArray);
                        }
                        sparseArray.put(mid, messageObject);
                        int idxOld = pushMessages.indexOf(oldMessageObject);
                        if (idxOld >= 0) {
                            pushMessages.set(idxOld, messageObject);
                            popup = addToPopupMessages(popupArrayAdd, messageObject, dialogId, isChannel, preferences);
                        }
                        if (isFcm && (edited = messageObject.localEdit)) {
                            getMessagesStorage().putPushMessage(messageObject);
                        }
                    }
                    FileLog.d("skipped message because old message with same dialog and message ids exist: did=" + did + ", mid="+mid);
                    continue;
                }
                if (edited) {
                    FileLog.d("skipped message because edited");
                    continue;
                }
                if (isFcm) {
                    getMessagesStorage().putPushMessage(messageObject);
                }

                long originalDialogId = dialogId;
                long topicId = MessageObject.getTopicId(currentAccount, messageObject.messageOwner, getMessagesController().isForum(messageObject));
                if (dialogId == openedDialogId && ApplicationLoader.isScreenOn && !messageObject.isStoryReactionPush) {
                    if (!isFcm) {
                        playInChatSound();
                    }
                    FileLog.d("skipped message because chat is already opened (openedDialogId = " + openedDialogId + ")");
                    continue;
                }
                if (messageObject.messageOwner.mentioned) {
                    if (!allowPinned && messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPinMessage) {
                        FileLog.d("skipped message because message is mention of pinned");
                        continue;
                    }
                    dialogId = messageObject.getFromChatId();
                }
                if (isPersonalMessage(messageObject)) {
                    personalCount++;
                }
                added = true;

                boolean isChat = DialogObject.isChatDialog(dialogId);
                int index = settingsCache.indexOfKey(dialogId);
                boolean value;
                if (index >= 0 && topicId == 0) {
                    value = settingsCache.valueAt(index);
                } else {
                    int notifyOverride = getNotifyOverride(preferences, dialogId, topicId);
                    if (notifyOverride == -1) {
                        value = isGlobalNotificationsEnabled(dialogId, isChannel, messageObject.isReactionPush, messageObject.isStoryReactionPush);
                        FileLog.d("NotificationsController: process new messages, isGlobalNotificationsEnabled("+dialogId+", "+isChannel+", "+messageObject.isReactionPush+", "+messageObject.isStoryReactionPush+") = " + value);
                        /*if (BuildVars.DEBUG_PRIVATE_VERSION && BuildVars.LOGS_ENABLED) {
                            FileLog.d("global notify settings for " + dialog_id + " = " + value);
                        }*/
                    } else {
                        value = notifyOverride != 2;
                    }

                    settingsCache.put(dialogId, value);
                }

                FileLog.d("NotificationsController: process new messages, value is " + value + " ("+dialogId+", "+isChannel+", "+messageObject.isReactionPush+", "+messageObject.isStoryReactionPush+")");
                if (value) {
                    if (!isFcm) {
                        popup = addToPopupMessages(popupArrayAdd, messageObject, dialogId, isChannel, preferences);
                    }
                    if (!hasScheduled) {
                        hasScheduled = messageObject.messageOwner.from_scheduled;
                    }
                    delayedPushMessages.add(messageObject);
                    appendMessage(messageObject);
                    if (mid != 0) {
                        if (sparseArray == null) {
                            sparseArray = new SparseArray<>();
                            pushMessagesDict.put(did, sparseArray);
                        }
                        sparseArray.put(mid, messageObject);
                    } else if (randomId != 0) {
                        fcmRandomMessagesDict.put(randomId, messageObject);
                    }
                    if (originalDialogId != dialogId) {
                        Integer current = pushDialogsOverrideMention.get(originalDialogId);
                        pushDialogsOverrideMention.put(originalDialogId, current == null ? 1 : current + 1);
                    }
                }
                if (messageObject.isReactionPush) {
                    SparseBooleanArray sparseBooleanArray = new SparseBooleanArray();
                    sparseBooleanArray.put(mid, true);
                    getMessagesController().checkUnreadReactions(dialogId, topicId, sparseBooleanArray);
                }
            }

            if (added) {
                notifyCheck = isLast;
            }

            if (!popupArrayAdd.isEmpty() && !AndroidUtilities.needShowPasscode() && !SharedConfig.isWaitingForPasscodeEnter) {
                int popupFinal = popup;
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
                    FileLog.d("NotificationsController processNewMessages: edited branch, showOrUpdateNotification " + notifyCheck);
                    delayedPushMessages.clear();
                    showOrUpdateNotification(notifyCheck);
                } else if (added) {
                    FileLog.d("NotificationsController processNewMessages: added branch");
                    MessageObject messageObject = messageObjects.get(0);
                    long dialog_id = messageObject.getDialogId();
                    long topicId = MessageObject.getTopicId(currentAccount, messageObject.messageOwner, getMessagesController().isForum(dialog_id));
                    Boolean isChannel;
                    if (messageObject.isFcmMessage()) {
                        isChannel = messageObject.localChannel;
                    } else {
                        isChannel = null;
                    }
                    int old_unread_count = total_unread_count;

                    int notifyOverride = getNotifyOverride(preferences, dialog_id, topicId);
                    boolean canAddValue;
                    if (notifyOverride == -1) {
                        canAddValue = isGlobalNotificationsEnabled(dialog_id, isChannel, messageObject.isReactionPush, messageObject.isStoryReactionPush);
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
                    canAddValue = canAddValue && !messageObject.isStoryPush;

                    if (canAddValue) {
                        if (getMessagesController().isForum(dialog_id)) {
                            total_unread_count -= currentCount != null && currentCount > 0 ? 1 : 0;
                            total_unread_count += newCount > 0 ? 1 : 0;
                        } else {
                            if (currentCount != null) {
                                total_unread_count -= currentCount;
                            }
                            total_unread_count += newCount;
                        }
                        pushDialogs.put(dialog_id, newCount);
                    }
                    if (old_unread_count != total_unread_count || storiesUpdated) {
                        delayedPushMessages.clear();
                        FileLog.d("NotificationsController processNewMessages: added branch: " + notifyCheck);
                        showOrUpdateNotification(notifyCheck);
                        int pushDialogsCount = pushDialogs.size();
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
            if (storiesUpdated) {
                updateStoryPushesRunnable();
            }
            if (countDownLatch != null) {
                countDownLatch.countDown();
            }
        });
    }

    private void appendMessage(MessageObject messageObject) {
        for (int i = 0; i < pushMessages.size(); i++) {
            if (
                pushMessages.get(i).getId() == messageObject.getId() &&
                pushMessages.get(i).getDialogId() == messageObject.getDialogId() &&
                pushMessages.get(i).isStoryPush == messageObject.isStoryPush
            ) {
                return;
            }
        }
        pushMessages.add(0, messageObject);
    }

    public int getTotalUnreadCount() {
        return total_unread_count;
    }

    public void processDialogsUpdateRead(LongSparseIntArray dialogsToUpdate) {
        ArrayList<MessageObject> popupArrayToRemove = new ArrayList<>();
        notificationsQueue.postRunnable(() -> {
            int old_unread_count = total_unread_count;
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
            for (int b = 0; b < dialogsToUpdate.size(); b++) {
                long dialogId = dialogsToUpdate.keyAt(b);
                Integer currentCount = pushDialogs.get(dialogId);
                int newCount = dialogsToUpdate.get(dialogId);
                boolean forum = false;
                if (DialogObject.isChatDialog(dialogId)) {
                    TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
                    if (chat == null || chat.min || ChatObject.isNotInChat(chat)) {
                        newCount = 0;
                    }
                    if (chat != null) {
                        forum = chat.forum;
                    }
                }


                boolean canAddValue;
                if (!forum) {
                    int notifyOverride = getNotifyOverride(preferences, dialogId, 0);
                    if (notifyOverride == -1) {
                        canAddValue = isGlobalNotificationsEnabled(dialogId, false, false);
                    } else {
                        canAddValue = notifyOverride != 2;
                    }
                } else {
                    canAddValue = true;
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
                        if (getMessagesController().isForum(dialogId)) {
                            total_unread_count -= currentCount > 0 ? 1 : 0;
                        } else {
                            total_unread_count -= currentCount;
                        }
                    }
                }
                if (newCount == 0) {
                    pushDialogs.remove(dialogId);
                    pushDialogsOverrideMention.remove(dialogId);
                    for (int a = 0; a < pushMessages.size(); a++) {
                        MessageObject messageObject = pushMessages.get(a);
                        if (!messageObject.messageOwner.from_scheduled && messageObject.getDialogId() == dialogId && !messageObject.isStoryReactionPush) {
                            if (isPersonalMessage(messageObject)) {
                                personalCount--;
                            }
                            pushMessages.remove(a);
                            a--;
                            delayedPushMessages.remove(messageObject);
                            long did;
                            if (messageObject.messageOwner.peer_id.channel_id != 0) {
                                did = -messageObject.messageOwner.peer_id.channel_id;
                            } else {
                                did = 0;
                            }
                            SparseArray<MessageObject> sparseArray = pushMessagesDict.get(did);
                            if (sparseArray != null) {
                                sparseArray.remove(messageObject.getId());
                                if (sparseArray.size() == 0) {
                                    pushMessagesDict.remove(did);
                                }
                            }
                            popupArrayToRemove.add(messageObject);
                        }
                    }
                } else if (canAddValue) {
                    if (getMessagesController().isForum(dialogId)) {
                        total_unread_count += newCount > 0 ? 1 : 0;
                    } else {
                        total_unread_count += newCount;
                    }
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
                int pushDialogsCount = pushDialogs.size();
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

    public void processLoadedUnreadMessages(LongSparseArray<Integer> dialogs, ArrayList<TLRPC.Message> messages, ArrayList<MessageObject> push, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats, ArrayList<TLRPC.EncryptedChat> encryptedChats, Collection<StoryNotification> storyPushes) {
        getMessagesController().putUsers(users, true);
        getMessagesController().putChats(chats, true);
        getMessagesController().putEncryptedChats(encryptedChats, true);

        notificationsQueue.postRunnable(() -> {
            pushDialogs.clear();
            pushMessages.clear();
            pushMessagesDict.clear();
            storyPushMessages.clear();
            storyPushMessagesDict.clear();
            total_unread_count = 0;
            personalCount = 0;
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
            LongSparseArray<Boolean> settingsCache = new LongSparseArray<>();

            if (messages != null) {
                for (int a = 0; a < messages.size(); a++) {
                    TLRPC.Message message = messages.get(a);
                    if (message == null) {
                        continue;
                    }
                    if (message.fwd_from != null && message.fwd_from.imported ||
                            message.action instanceof TLRPC.TL_messageActionSetMessagesTTL ||
                            message.silent && (message.action instanceof TLRPC.TL_messageActionContactSignUp || message.action instanceof TLRPC.TL_messageActionUserJoined)) {
                        continue;
                    }
                    long did;
                    if (message.peer_id.channel_id != 0) {
                        did = -message.peer_id.channel_id;
                    } else {
                        did = 0;
                    }
                    SparseArray<MessageObject> sparseArray = pushMessagesDict.get(did);
                    if (sparseArray != null && sparseArray.indexOfKey(message.id) >= 0) {
                        continue;
                    }
                    MessageObject messageObject = new MessageObject(currentAccount, message, false, false);
                    if (isPersonalMessage(messageObject)) {
                        personalCount++;
                    }
                    long dialog_id = messageObject.getDialogId();
                    long original_dialog_id = dialog_id;
                    long topicId = MessageObject.getTopicId(currentAccount, messageObject.messageOwner, getMessagesController().isForum(messageObject));
                    if (messageObject.messageOwner.mentioned) {
                        dialog_id = messageObject.getFromChatId();
                    }
                    int index = settingsCache.indexOfKey(dialog_id);
                    boolean value;
                    if (index >= 0 && topicId == 0) {
                        value = settingsCache.valueAt(index);
                    } else {
                        int notifyOverride = getNotifyOverride(preferences, dialog_id, topicId);
                        if (notifyOverride == -1) {
                            value = isGlobalNotificationsEnabled(dialog_id, messageObject.isReactionPush, messageObject.isStoryReactionPush);
                        } else {
                            value = notifyOverride != 2;
                        }
                        settingsCache.put(dialog_id, value);
                    }
                    if (!value || dialog_id == openedDialogId && ApplicationLoader.isScreenOn) {
                        continue;
                    }
                    if (sparseArray == null) {
                        sparseArray = new SparseArray<>();
                        pushMessagesDict.put(did, sparseArray);
                    }
                    sparseArray.put(message.id, messageObject);
                    appendMessage(messageObject);
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
                    int notifyOverride = getNotifyOverride(preferences, dialog_id, 0);
                    if (notifyOverride == -1) {
                        value = isGlobalNotificationsEnabled(dialog_id, false, false);
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
                if (getMessagesController().isForum(dialog_id)) {
                    total_unread_count += count > 0 ? 1 : 0;
                } else {
                    total_unread_count += count;
                }
            }

            if (push != null) {
                for (int a = 0; a < push.size(); a++) {
                    MessageObject messageObject = push.get(a);
                    int mid = messageObject.getId();
                    if (pushMessagesDict.indexOfKey(mid) >= 0) {
                        continue;
                    }
                    if (isPersonalMessage(messageObject)) {
                        personalCount++;
                    }
                    long dialogId = messageObject.getDialogId();
                    long originalDialogId = dialogId;
                    long topicId = MessageObject.getTopicId(currentAccount, messageObject.messageOwner, getMessagesController().isForum(messageObject));
                    long randomId = messageObject.messageOwner.random_id;
                    if (messageObject.messageOwner.mentioned) {
                        dialogId = messageObject.getFromChatId();
                    }
                    int index = settingsCache.indexOfKey(dialogId);
                    boolean value;
                    if (index >= 0 && topicId == 0) {
                        value = settingsCache.valueAt(index);
                    } else {
                        int notifyOverride = getNotifyOverride(preferences, dialogId, topicId);
                        if (notifyOverride == -1) {
                            value = isGlobalNotificationsEnabled(dialogId, messageObject.isReactionPush, messageObject.isStoryReactionPush);
                        } else {
                            value = notifyOverride != 2;
                        }
                        settingsCache.put(dialogId, value);
                    }
                    if (!value || dialogId == openedDialogId && ApplicationLoader.isScreenOn) {
                        continue;
                    }
                    if (mid != 0) {
                        long did;
                        if (messageObject.isStoryReactionPush) {
                            did = messageObject.getDialogId();
                        } else if (messageObject.messageOwner.peer_id.channel_id != 0) {
                            did = -messageObject.messageOwner.peer_id.channel_id;
                        } else {
                            did = 0;
                        }
                        SparseArray<MessageObject> sparseArray = pushMessagesDict.get(did);
                        if (sparseArray == null) {
                            sparseArray = new SparseArray<>();
                            pushMessagesDict.put(did, sparseArray);
                        }
                        sparseArray.put(mid, messageObject);
                    } else if (randomId != 0) {
                        fcmRandomMessagesDict.put(randomId, messageObject);
                    }
                    appendMessage(messageObject);
                    if (originalDialogId != dialogId) {
                        Integer current = pushDialogsOverrideMention.get(originalDialogId);
                        pushDialogsOverrideMention.put(originalDialogId, current == null ? 1 : current + 1);
                    }

                    Integer currentCount = pushDialogs.get(dialogId);
                    int newCount = currentCount != null ? currentCount + 1 : 1;

                    if (getMessagesController().isForum(dialogId)) {
                        if (currentCount != null) {
                            total_unread_count -= currentCount > 0 ? 1 : 0;
                        }
                        total_unread_count += newCount > 0 ? 1 : 0;
                    } else {
                        if (currentCount != null) {
                            total_unread_count -= currentCount;
                        }
                        total_unread_count += newCount;
                    }
                    pushDialogs.put(dialogId, newCount);
                }
            }

            if (storyPushes != null) {
                for (StoryNotification notification : storyPushes) {
                    long dialogId = notification.dialogId;
                    StoryNotification oldNotification = storyPushMessagesDict.get(dialogId);
                    if (oldNotification != null) {
                        oldNotification.dateByIds.putAll(notification.dateByIds);
                    } else {
                        storyPushMessages.add(notification);
                        storyPushMessagesDict.put(dialogId, notification);
                    }
                }
                Collections.sort(storyPushMessages, Comparator.comparingLong(n -> n.date));
            }

            int pushDialogsCount = pushDialogs.size();
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
            if (!UserConfig.getInstance(a).isClientActivated() || !SharedConfig.showNotificationsForAllAccounts && UserConfig.selectedAccount != a) {
                continue;
            }
            NotificationsController controller = getInstance(a);
            if (controller.showBadgeNumber) {
                if (controller.showBadgeMessages) {
                    if (controller.showBadgeMuted) {
                        try {
                            final ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>(MessagesController.getInstance(a).allDialogs);
                            for (int i = 0, N = dialogs.size(); i < N; i++) {
                                TLRPC.Dialog dialog = dialogs.get(i);
                                if (dialog != null && DialogObject.isChatDialog(dialog.id)) {
                                    TLRPC.Chat chat = getMessagesController().getChat(-dialog.id);
                                    if (ChatObject.isNotInChat(chat)) {
                                        continue;
                                    }
                                }
                                if (dialog != null) {
                                    count += MessagesController.getInstance(a).getDialogUnreadCount(dialog);
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
                                if (DialogObject.isChatDialog(dialog.id)) {
                                    TLRPC.Chat chat = getMessagesController().getChat(-dialog.id);
                                    if (ChatObject.isNotInChat(chat)) {
                                        continue;
                                    }
                                }
                                if (MessagesController.getInstance(a).getDialogUnreadCount(dialog) != 0) {
                                    count++;
                                }
                            }
                        } catch (Exception e) {
                            //ignore, no thread synchronizations for fast
                            FileLog.e(e, false);
                        }
                    } else {
                        count += controller.pushDialogs.size();
                    }
                }
            }
        }
        return count;
    }

    public void updateBadge() {
        notificationsQueue.postRunnable(() -> setBadge(getTotalAllUnreadCount()));
    }

    private void setBadge(int count) {
        if (lastBadgeCount == count) {
            return;
        }
        FileLog.d("setBadge " + count);
        lastBadgeCount = count;
        NotificationBadge.applyCount(count);
    }

    private String getShortStringForMessage(MessageObject messageObject, String[] userName, boolean[] preview) {
        if (AndroidUtilities.needShowPasscode() || SharedConfig.isWaitingForPasscodeEnter) {
            return LocaleController.getString(R.string.NotificationHiddenMessage);
        }
        long dialogId = messageObject.messageOwner.dialog_id;
        long chat_id = messageObject.messageOwner.peer_id.chat_id != 0 ? messageObject.messageOwner.peer_id.chat_id : messageObject.messageOwner.peer_id.channel_id;
        long fromId = messageObject.messageOwner.peer_id.user_id;
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
                    return LocaleController.getString(R.string.Message);
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
                        return LocaleController.formatString(R.string.ChannelMessageNoText, messageObject.localName);
                    } else {
                        return LocaleController.formatString(R.string.NotificationMessageGroupNoText, messageObject.localUserName, messageObject.localName);
                    }
                }
            }
            return replaceSpoilers(messageObject);
        }
        long selfUsedId = getUserConfig().getClientUserId();
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
            long id = MessageObject.getPeerId(messageObject.messageOwner.fwd_from.saved_from_peer);
            if (DialogObject.isChatDialog(id)) {
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
        if (DialogObject.isEncryptedDialog(dialogId)) {
            userName[0] = null;
            return LocaleController.getString(R.string.NotificationHiddenMessage);
        } else {
            boolean isChannel = ChatObject.isChannel(chat) && !chat.megagroup;
            if (dialogPreviewEnabled && (chat_id == 0 && fromId != 0 && preferences.getBoolean("EnablePreviewAll", true) || chat_id != 0 && (!isChannel && preferences.getBoolean("EnablePreviewGroup", true) || isChannel && preferences.getBoolean("EnablePreviewChannel", true)))) {
                if (messageObject.messageOwner instanceof TLRPC.TL_messageService) {
                    userName[0] = null;
                    if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionSetSameChatWallPaper) {
                        return LocaleController.getString(R.string.WallpaperSameNotification);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionSetChatWallPaper) {
                        return LocaleController.getString(R.string.WallpaperNotification);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGeoProximityReached) {
                        return messageObject.messageText.toString();
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserJoined || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionContactSignUp) {
                        return LocaleController.formatString(R.string.NotificationContactJoined, name);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                        return LocaleController.formatString(R.string.NotificationContactNewPhoto, name);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionLoginUnknownLocation) {
                        String date = LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterYear().format(((long) messageObject.messageOwner.date) * 1000), LocaleController.getInstance().getFormatterDay().format(((long) messageObject.messageOwner.date) * 1000));
                        return LocaleController.formatString(R.string.NotificationUnrecognizedDevice, getUserConfig().getCurrentUser().first_name, date, messageObject.messageOwner.action.title, messageObject.messageOwner.action.address);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGameScore || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPaymentSent || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPaymentSentMe) {
                        return messageObject.messageText.toString();
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGift || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGiftPremium) {
                        return messageObject.messageText.toString();
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPhoneCall) {
                        if (messageObject.messageOwner.action.video) {
                            return LocaleController.getString(R.string.CallMessageVideoIncomingMissed);
                        } else {
                            return LocaleController.getString(R.string.CallMessageIncomingMissed);
                        }
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatAddUser) {
                        long singleUserId = messageObject.messageOwner.action.user_id;
                        if (singleUserId == 0 && messageObject.messageOwner.action.users.size() == 1) {
                            singleUserId = messageObject.messageOwner.action.users.get(0);
                        }
                        if (singleUserId != 0) {
                            if (messageObject.messageOwner.peer_id.channel_id != 0 && !chat.megagroup) {
                                return LocaleController.formatString(R.string.ChannelAddedByNotification, name, chat.title);
                            } else {
                                if (singleUserId == selfUsedId) {
                                    return LocaleController.formatString(R.string.NotificationInvitedToGroup, name, chat.title);
                                } else {
                                    TLRPC.User u2 = getMessagesController().getUser(singleUserId);
                                    if (u2 == null) {
                                        return null;
                                    }
                                    if (fromId == u2.id) {
                                        if (chat.megagroup) {
                                            return LocaleController.formatString(R.string.NotificationGroupAddSelfMega, name, chat.title);
                                        } else {
                                            return LocaleController.formatString(R.string.NotificationGroupAddSelf, name, chat.title);
                                        }
                                    } else {
                                        return LocaleController.formatString(R.string.NotificationGroupAddMember, name, chat.title, UserObject.getUserName(u2));
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
                            return LocaleController.formatString(R.string.NotificationGroupAddMember, name, chat.title, names.toString());
                        }
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGroupCall) {
                        if (messageObject.messageOwner.action.duration != 0) {
                            return LocaleController.formatString(R.string.NotificationGroupEndedCall, name, chat.title);
                        } else {
                            return LocaleController.formatString(R.string.NotificationGroupCreatedCall, name, chat.title);
                        }
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGroupCallScheduled) {
                        return messageObject.messageText.toString();
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionInviteToGroupCall) {
                        long singleUserId = messageObject.messageOwner.action.user_id;
                        if (singleUserId == 0 && messageObject.messageOwner.action.users.size() == 1) {
                            singleUserId = messageObject.messageOwner.action.users.get(0);
                        }
                        if (singleUserId != 0) {
                            if (singleUserId == selfUsedId) {
                                return LocaleController.formatString(R.string.NotificationGroupInvitedYouToCall, name, chat.title);
                            } else {
                                TLRPC.User u2 = getMessagesController().getUser(singleUserId);
                                if (u2 == null) {
                                    return null;
                                }
                                return LocaleController.formatString(R.string.NotificationGroupInvitedToCall, name, chat.title, UserObject.getUserName(u2));
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
                            return LocaleController.formatString(R.string.NotificationGroupInvitedToCall, name, chat.title, names.toString());
                        }
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGiftCode) {
                        return LocaleController.getString(R.string.BoostingReceivedGiftNoName);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatJoinedByLink) {
                        return LocaleController.formatString(R.string.NotificationInvitedToGroupByLink, name, chat.title);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatEditTitle) {
                        return LocaleController.formatString(R.string.NotificationEditedGroupName, name, messageObject.messageOwner.action.title);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatEditPhoto || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
                        if (messageObject.messageOwner.peer_id.channel_id != 0 && !chat.megagroup) {
                            if (messageObject.isVideoAvatar()) {
                                return LocaleController.formatString(R.string.ChannelVideoEditNotification, chat.title);
                            } else {
                                return LocaleController.formatString(R.string.ChannelPhotoEditNotification, chat.title);
                            }
                        } else {
                            if (messageObject.isVideoAvatar()) {
                                return LocaleController.formatString(R.string.NotificationEditedGroupVideo, name, chat.title);
                            } else {
                                return LocaleController.formatString(R.string.NotificationEditedGroupPhoto, name, chat.title);
                            }
                        }
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                        if (messageObject.messageOwner.action.user_id == selfUsedId) {
                            return LocaleController.formatString(R.string.NotificationGroupKickYou, name, chat.title);
                        } else if (messageObject.messageOwner.action.user_id == fromId) {
                            return LocaleController.formatString(R.string.NotificationGroupLeftMember, name, chat.title);
                        } else {
                            TLRPC.User u2 = getMessagesController().getUser(messageObject.messageOwner.action.user_id);
                            if (u2 == null) {
                                return null;
                            }
                            return LocaleController.formatString(R.string.NotificationGroupKickMember, name, chat.title, UserObject.getUserName(u2));
                        }
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatCreate) {
                        return messageObject.messageText.toString();
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChannelCreate) {
                        return messageObject.messageText.toString();
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatMigrateTo) {
                        return LocaleController.formatString(R.string.ActionMigrateFromGroupNotify, chat.title);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChannelMigrateFrom) {
                        return LocaleController.formatString(R.string.ActionMigrateFromGroupNotify, messageObject.messageOwner.action.title);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionScreenshotTaken) {
                        return messageObject.messageText.toString();
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGiveawayLaunch) {
                        return messageObject.messageText.toString();
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGiveawayResults) {
                        return messageObject.messageText.toString();
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPinMessage) {
                        if (chat != null && (!ChatObject.isChannel(chat) || chat.megagroup)) {
                            if (messageObject.replyMessageObject == null) {
                                return LocaleController.formatString(R.string.NotificationActionPinnedNoText, name, chat.title);
                            } else {
                                MessageObject object = messageObject.replyMessageObject;
                                if (object.isMusic()) {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedMusic, name, chat.title);
                                } else if (object.isVideo()) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83D\uDCF9 " + object.messageOwner.message;
                                        return LocaleController.formatString(R.string.NotificationActionPinnedText, name, message, chat.title);
                                    } else {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedVideo, name, chat.title);
                                    }
                                } else if (object.isGif()) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83C\uDFAC " + object.messageOwner.message;
                                        return LocaleController.formatString(R.string.NotificationActionPinnedText, name, message, chat.title);
                                    } else {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedGif, name, chat.title);
                                    }
                                } else if (object.isVoice()) {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedVoice, name, chat.title);
                                } else if (object.isRoundVideo()) {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedRound, name, chat.title);
                                } else if (object.isSticker() || object.isAnimatedSticker()) {
                                    String emoji = object.getStickerEmoji();
                                    if (emoji != null) {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedStickerEmoji, name, chat.title, emoji);
                                    } else {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedSticker, name, chat.title);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83D\uDCCE " + object.messageOwner.message;
                                        return LocaleController.formatString(R.string.NotificationActionPinnedText, name, message, chat.title);
                                    } else {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedFile, name, chat.title);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || object.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedGeo, name, chat.title);
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedGeoLive, name, chat.title);
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                                    TLRPC.TL_messageMediaContact mediaContact = (TLRPC.TL_messageMediaContact) object.messageOwner.media;
                                    return LocaleController.formatString(R.string.NotificationActionPinnedContact2, name, chat.title, ContactsController.formatName(mediaContact.first_name, mediaContact.last_name));
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                                    TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) object.messageOwner.media;
                                    if (mediaPoll.poll.quiz) {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedQuiz2, name, chat.title, mediaPoll.poll.question.text);
                                    } else {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedPoll2, name, chat.title, mediaPoll.poll.question.text);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83D\uDDBC " + object.messageOwner.message;
                                        return LocaleController.formatString(R.string.NotificationActionPinnedText, name, message, chat.title);
                                    } else {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedPhoto, name, chat.title);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedGame, name, chat.title);
                                } else if (object.messageText != null && object.messageText.length() > 0) {
                                    CharSequence message = object.messageText;
                                    if (message.length() > 20) {
                                        message = message.subSequence(0, 20) + "...";
                                    }
                                    return LocaleController.formatString(R.string.NotificationActionPinnedText, name, message, chat.title);
                                } else {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedNoText, name, chat.title);
                                }
                            }
                        } else if (chat != null) {
                            if (messageObject.replyMessageObject == null) {
                                return LocaleController.formatString(R.string.NotificationActionPinnedNoTextChannel, chat.title);
                            } else {
                                MessageObject object = messageObject.replyMessageObject;
                                if (object.isMusic()) {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedMusicChannel, chat.title);
                                } else if (object.isVideo()) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83D\uDCF9 " + object.messageOwner.message;
                                        return LocaleController.formatString(R.string.NotificationActionPinnedTextChannel, chat.title, message);
                                    } else {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedVideoChannel, chat.title);
                                    }
                                } else if (object.isGif()) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83C\uDFAC " + object.messageOwner.message;
                                        return LocaleController.formatString(R.string.NotificationActionPinnedTextChannel, chat.title, message);
                                    } else {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedGifChannel, chat.title);
                                    }
                                } else if (object.isVoice()) {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedVoiceChannel, chat.title);
                                } else if (object.isRoundVideo()) {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedRoundChannel, chat.title);
                                } else if (object.isSticker() || object.isAnimatedSticker()) {
                                    String emoji = object.getStickerEmoji();
                                    if (emoji != null) {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedStickerEmojiChannel, chat.title, emoji);
                                    } else {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedStickerChannel, chat.title);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83D\uDCCE " + object.messageOwner.message;
                                        return LocaleController.formatString(R.string.NotificationActionPinnedTextChannel, chat.title, message);
                                    } else {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedFileChannel, chat.title);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || object.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedGeoChannel, chat.title);
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedGeoLiveChannel, chat.title);
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                                    TLRPC.TL_messageMediaContact mediaContact = (TLRPC.TL_messageMediaContact) object.messageOwner.media;
                                    return LocaleController.formatString(R.string.NotificationActionPinnedContactChannel2, chat.title, ContactsController.formatName(mediaContact.first_name, mediaContact.last_name));
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                                    TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) object.messageOwner.media;
                                    if (mediaPoll.poll.quiz) {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedQuizChannel2, chat.title, mediaPoll.poll.question.text);
                                    } else {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedPollChannel2, chat.title, mediaPoll.poll.question.text);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83D\uDDBC " + object.messageOwner.message;
                                        return LocaleController.formatString(R.string.NotificationActionPinnedTextChannel, chat.title, message);
                                    } else {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedPhotoChannel, chat.title);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedGameChannel, chat.title);
                                } else if (object.messageText != null && object.messageText.length() > 0) {
                                    CharSequence message = object.messageText;
                                    if (message.length() > 20) {
                                        message = message.subSequence(0, 20) + "...";
                                    }
                                    return LocaleController.formatString(R.string.NotificationActionPinnedTextChannel, chat.title, message);
                                } else {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedNoTextChannel, chat.title);
                                }
                            }
                        } else {
                            if (messageObject.replyMessageObject == null) {
                                return LocaleController.formatString(R.string.NotificationActionPinnedNoTextUser, name);
                            } else {
                                MessageObject object = messageObject.replyMessageObject;
                                if (object.isMusic()) {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedMusicUser, name);
                                } else if (object.isVideo()) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83D\uDCF9 " + object.messageOwner.message;
                                        return LocaleController.formatString(R.string.NotificationActionPinnedTextUser, name, message);
                                    } else {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedVideoUser, name);
                                    }
                                } else if (object.isGif()) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83C\uDFAC " + object.messageOwner.message;
                                        return LocaleController.formatString(R.string.NotificationActionPinnedTextUser, name, message);
                                    } else {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedGifUser, name);
                                    }
                                } else if (object.isVoice()) {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedVoiceUser, name);
                                } else if (object.isRoundVideo()) {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedRoundUser, name);
                                } else if (object.isSticker() || object.isAnimatedSticker()) {
                                    String emoji = object.getStickerEmoji();
                                    if (emoji != null) {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedStickerEmojiUser, name, emoji);
                                    } else {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedStickerUser, name);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83D\uDCCE " + object.messageOwner.message;
                                        return LocaleController.formatString(R.string.NotificationActionPinnedTextUser, name, message);
                                    } else {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedFileUser, name);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || object.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedGeoUser, name);
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedGeoLiveUser, name);
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                                    TLRPC.TL_messageMediaContact mediaContact = (TLRPC.TL_messageMediaContact) object.messageOwner.media;
                                    return LocaleController.formatString(R.string.NotificationActionPinnedContactUser, name, ContactsController.formatName(mediaContact.first_name, mediaContact.last_name));
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                                    TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) object.messageOwner.media;
                                    if (mediaPoll.poll.quiz) {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedQuizUser, name, mediaPoll.poll.question.text);
                                    } else {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedPollUser, name, mediaPoll.poll.question.text);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                                    if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                        String message = "\uD83D\uDDBC " + object.messageOwner.message;
                                        return LocaleController.formatString(R.string.NotificationActionPinnedTextUser, name, message);
                                    } else {
                                        return LocaleController.formatString(R.string.NotificationActionPinnedPhotoUser, name);
                                    }
                                } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedGameUser, name);
                                } else if (object.messageText != null && object.messageText.length() > 0) {
                                    CharSequence message = object.messageText;
                                    if (message.length() > 20) {
                                        message = message.subSequence(0, 20) + "...";
                                    }
                                    return LocaleController.formatString(R.string.NotificationActionPinnedTextUser, name, message);
                                } else {
                                    return LocaleController.formatString(R.string.NotificationActionPinnedNoTextUser, name);
                                }
                            }
                        }
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionSetChatTheme) {
                        String emoticon = ((TLRPC.TL_messageActionSetChatTheme) messageObject.messageOwner.action).emoticon;
                        if (TextUtils.isEmpty(emoticon)) {
                            msg = dialogId == selfUsedId
                                    ? LocaleController.formatString(R.string.ChatThemeDisabledYou)
                                    : LocaleController.formatString("ChatThemeDisabled", R.string.ChatThemeDisabled, name, emoticon);
                        } else {
                            msg = dialogId == selfUsedId
                                    ? LocaleController.formatString(R.string.ChatThemeChangedYou, emoticon)
                                    : LocaleController.formatString(R.string.ChatThemeChangedTo, name, emoticon);
                        }
                        return msg;
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatJoinedByRequest) {
                        return messageObject.messageText.toString();
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPrizeStars) {
                        final TLRPC.TL_messageActionPrizeStars action = (TLRPC.TL_messageActionPrizeStars) messageObject.messageOwner.action;
                        final long did = DialogObject.getPeerDialogId(action.boost_peer);
                        final String peername;
                        if (did >= 0) {
                            peername = UserObject.getForcedFirstName(getMessagesController().getUser(did));
                        } else {
                            TLRPC.Chat peerchat = getMessagesController().getChat(-did);
                            peername = peerchat == null ? "" : peerchat.title;
                        }
                        return LocaleController.formatPluralStringComma("BoostingReceivedStars", (int) action.stars, peername);
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPaymentRefunded) {
                        return messageObject.messageText.toString();
                    }
                } else {
                    if (messageObject.isMediaEmpty()) {
                        if (!TextUtils.isEmpty(messageObject.messageOwner.message)) {
                            return replaceSpoilers(messageObject);
                        } else {
                            return LocaleController.getString(R.string.Message);
                        }
                    } else if (messageObject.type == MessageObject.TYPE_PAID_MEDIA && MessageObject.getMedia(messageObject) instanceof TLRPC.TL_messageMediaPaidMedia) {
                        TLRPC.TL_messageMediaPaidMedia paidMedia = (TLRPC.TL_messageMediaPaidMedia) MessageObject.getMedia(messageObject);
                        final int count = paidMedia.extended_media.size();
                        boolean video = false;
                        for (int i = 0; i < count; ++i) {
                            TLRPC.MessageExtendedMedia emedia = paidMedia.extended_media.get(i);
                            if (emedia instanceof TLRPC.TL_messageExtendedMedia) {
                                video = ((TLRPC.TL_messageExtendedMedia) emedia).media instanceof TLRPC.TL_messageMediaDocument &&
                                        MessageObject.isVideoDocument(((TLRPC.TL_messageExtendedMedia) emedia).media.document);
                            } else if (emedia instanceof TLRPC.TL_messageExtendedMediaPreview) {
                                video = (((TLRPC.TL_messageExtendedMediaPreview) emedia).flags & 4) != 0;
                            }
                            if (video) break;
                        }
                        return LocaleController.formatString(R.string.AttachPaidMedia, count == 1 ? LocaleController.getString(video ? R.string.AttachVideo : R.string.AttachPhoto) : LocaleController.formatPluralString(video ? "Media" : "Photos", count));
                    } else if (messageObject.isVoiceOnce()) {
                        return LocaleController.getString(R.string.AttachOnceAudio);
                    } else if (messageObject.isRoundOnce()) {
                        return LocaleController.getString(R.string.AttachOnceRound);
                    } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                        if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                            return "\uD83D\uDDBC " + replaceSpoilers(messageObject);
                        } else if (messageObject.messageOwner.media.ttl_seconds != 0) {
                            return LocaleController.getString(R.string.AttachDestructingPhoto);
                        } else {
                            return LocaleController.getString(R.string.AttachPhoto);
                        }
                    } else if (messageObject.isVideo()) {
                        if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                            return "\uD83D\uDCF9 " + replaceSpoilers(messageObject);
                        } else if (messageObject.messageOwner.media.ttl_seconds != 0) {
                            return LocaleController.getString(R.string.AttachDestructingVideo);
                        } else {
                            return LocaleController.getString(R.string.AttachVideo);
                        }
                    } else if (messageObject.isGame()) {
                        return LocaleController.getString(R.string.AttachGame);
                    } else if (messageObject.isVoice()) {
                        return LocaleController.getString(R.string.AttachAudio);
                    } else if (messageObject.isRoundVideo()) {
                        return LocaleController.getString(R.string.AttachRound);
                    } else if (messageObject.isMusic()) {
                        return LocaleController.getString(R.string.AttachMusic);
                    } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                        return LocaleController.getString(R.string.AttachContact);
                    } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                        if (((TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media).poll.quiz) {
                            return LocaleController.getString(R.string.QuizPoll);
                        } else {
                            return LocaleController.getString(R.string.Poll);
                        }
                    } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGiveaway) {
                        return LocaleController.getString(R.string.BoostingGiveaway);
                    } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGiveawayResults) {
                        return LocaleController.getString(R.string.BoostingGiveawayResults);
                    } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                        return LocaleController.getString(R.string.AttachLocation);
                    } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                        return LocaleController.getString(R.string.AttachLiveLocation);
                    } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                        if (messageObject.isSticker() || messageObject.isAnimatedSticker()) {
                            String emoji = messageObject.getStickerEmoji();
                            if (emoji != null) {
                                return emoji + " " + LocaleController.getString(R.string.AttachSticker);
                            } else {
                                return LocaleController.getString(R.string.AttachSticker);
                            }
                        } else if (messageObject.isGif()) {
                            if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                return "\uD83C\uDFAC " + replaceSpoilers(messageObject);
                            } else {
                                return LocaleController.getString(R.string.AttachGif);
                            }
                        } else {
                            if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                return "\uD83D\uDCCE " + replaceSpoilers(messageObject);
                            } else {
                                return LocaleController.getString(R.string.AttachDocument);
                            }
                        }
                    } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaStory) {
                        TLRPC.TL_messageMediaStory storyMedia = (TLRPC.TL_messageMediaStory) messageObject.messageOwner.media;
                        if (storyMedia.via_mention) {
                            return LocaleController.formatString(R.string.StoryNotificationMention, userName[0] == null ? "" : userName[0]);
                        } else {
                            return LocaleController.getString(R.string.Story);
                        }
                    } else if (!TextUtils.isEmpty(messageObject.messageText)) {
                        return replaceSpoilers(messageObject);
                    } else {
                        return LocaleController.getString(R.string.Message);
                    }
                }
            } else {
                if (preview != null) {
                    preview[0] = false;
                }
                return LocaleController.getString(R.string.Message);
            }
        }
        return null;
    }

    char[] spoilerChars = new char[] {
        '⠌', '⡢', '⢑', '⠨', '⠥', '⠮', '⡑'
    };

    private String replaceSpoilers(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return null;
        }
        String text = messageObject.messageOwner.message;
        if (text == null || messageObject.messageOwner.entities == null) {
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder(text);
        if (messageObject != null && messageObject.didSpoilLoginCode()) {
            return stringBuilder.toString();
        }
        for (int i = 0; i < messageObject.messageOwner.entities.size(); i++) {
            if (messageObject.messageOwner.entities.get(i) instanceof TLRPC.TL_messageEntitySpoiler) {
                TLRPC.TL_messageEntitySpoiler spoiler = (TLRPC.TL_messageEntitySpoiler) messageObject.messageOwner.entities.get(i);
                for (int j = 0; j < spoiler.length; j++) {
                    stringBuilder.setCharAt(spoiler.offset + j, spoilerChars[j % spoilerChars.length]);
                }
            }
        }
        return stringBuilder.toString();
    }

    private String getStringForMessage(MessageObject messageObject, boolean shortMessage, boolean[] text, boolean[] preview) {
        if (AndroidUtilities.needShowPasscode() || SharedConfig.isWaitingForPasscodeEnter) {
            return LocaleController.getString(R.string.YouHaveNewMessage);
        }
        if (messageObject.isStoryPush || messageObject.isStoryMentionPush) {
            return "!" + messageObject.messageOwner.message;
        }
        long dialogId = messageObject.messageOwner.dialog_id;
        long chatId = messageObject.messageOwner.peer_id.chat_id != 0 ? messageObject.messageOwner.peer_id.chat_id : messageObject.messageOwner.peer_id.channel_id;
        long fromId = messageObject.messageOwner.peer_id.user_id;
        if (preview != null) {
            preview[0] = true;
        }
        if (messageObject != null && messageObject.getDialogId() == UserObject.VERIFY && messageObject.getForwardedFromId() != null) {
            fromId = messageObject.getForwardedFromId();
            chatId = fromId < 0 ? -fromId : 0;
        }
        SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
        boolean dialogPreviewEnabled = preferences.getBoolean("content_preview_" + dialogId, true);
        if (messageObject.isFcmMessage()) {
            if (chatId == 0 && fromId != 0) {
                if (!dialogPreviewEnabled || !preferences.getBoolean("EnablePreviewAll", true)) {
                    if (preview != null) {
                        preview[0] = false;
                    }
                    return LocaleController.formatString(R.string.NotificationMessageNoText, messageObject.localName);
                }
            } else if (chatId != 0) {
                if (!dialogPreviewEnabled || !messageObject.localChannel && !preferences.getBoolean("EnablePreviewGroup", true) || messageObject.localChannel && !preferences.getBoolean("EnablePreviewChannel", true)) {
                    if (preview != null) {
                        preview[0] = false;
                    }
                    if (messageObject.messageOwner.peer_id.channel_id != 0 && !messageObject.isSupergroup()) {
                        return LocaleController.formatString(R.string.ChannelMessageNoText, messageObject.localName);
                    } else {
                        return LocaleController.formatString(R.string.NotificationMessageGroupNoText, messageObject.localUserName, messageObject.localName);
                    }
                }
            }
            text[0] = true;
            return (String) messageObject.messageText;
        }
        long selfUsedId = getUserConfig().getClientUserId();
        if (fromId == 0) {
            fromId = messageObject.getFromChatId();
            if (fromId == 0) {
                fromId = -chatId;
            }
        } else if (fromId == selfUsedId) {
            fromId = messageObject.getFromChatId();
        }

        if (dialogId == 0) {
            if (chatId != 0) {
                dialogId = -chatId;
            } else if (fromId != 0) {
                dialogId = fromId;
            }
        }

        String name = null;
        if (fromId > 0) {
            if (messageObject.messageOwner.from_scheduled) {
                if (dialogId == selfUsedId) {
                    name = LocaleController.getString(R.string.MessageScheduledReminderNotification);
                } else {
                    name = LocaleController.getString(R.string.NotificationMessageScheduledName);
                }
            } else {
                TLRPC.User user = getMessagesController().getUser(fromId);
                if (user != null) {
                    name = UserObject.getUserName(user);
                }
            }
        } else {
            TLRPC.Chat chat = getMessagesController().getChat(-fromId);
            if (chat != null) {
                name = chat.title;
            }
        }

        if (name == null) {
            return null;
        }
        TLRPC.Chat chat = null;
        if (chatId != 0) {
            chat = getMessagesController().getChat(chatId);
            if (chat == null) {
                return null;
            }
        }

        String msg = null;
        if (DialogObject.isEncryptedDialog(dialogId)) {
            msg = LocaleController.getString(R.string.YouHaveNewMessage);
        } else {
            if (chatId == 0 && fromId != 0) {
                if (dialogPreviewEnabled && preferences.getBoolean("EnablePreviewAll", true)) {
                    if (messageObject.messageOwner instanceof TLRPC.TL_messageService) {
                        if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionSetSameChatWallPaper) {
                            msg = LocaleController.getString(R.string.WallpaperSameNotification);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionSetChatWallPaper) {
                            msg = LocaleController.getString(R.string.WallpaperNotification);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGeoProximityReached) {
                            msg = messageObject.messageText.toString();
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserJoined || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionContactSignUp) {
                            msg = LocaleController.formatString(R.string.NotificationContactJoined, name);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                            msg = LocaleController.formatString(R.string.NotificationContactNewPhoto, name);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionLoginUnknownLocation) {
                            String date = LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterYear().format(((long) messageObject.messageOwner.date) * 1000), LocaleController.getInstance().getFormatterDay().format(((long) messageObject.messageOwner.date) * 1000));
                            msg = LocaleController.formatString(R.string.NotificationUnrecognizedDevice, getUserConfig().getCurrentUser().first_name, date, messageObject.messageOwner.action.title, messageObject.messageOwner.action.address);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGameScore || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPaymentSent || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPaymentSentMe) {
                            msg = messageObject.messageText.toString();
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGift || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGiftPremium) {
                            msg = messageObject.messageText.toString();
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPhoneCall) {
                            if (messageObject.messageOwner.action.video) {
                                msg = LocaleController.getString(R.string.CallMessageVideoIncomingMissed);
                            } else {
                                msg = LocaleController.getString(R.string.CallMessageIncomingMissed);
                            }
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionSetChatTheme) {
                            String emoticon = ((TLRPC.TL_messageActionSetChatTheme) messageObject.messageOwner.action).emoticon;
                            if (TextUtils.isEmpty(emoticon)) {
                                msg = dialogId == selfUsedId
                                        ? LocaleController.formatString(R.string.ChatThemeDisabledYou)
                                        : LocaleController.formatString(R.string.ChatThemeDisabled, name, emoticon);
                            } else {
                                msg = dialogId == selfUsedId
                                        ? LocaleController.formatString(R.string.ChatThemeChangedYou, emoticon)
                                        : LocaleController.formatString(R.string.ChatThemeChangedTo, name, emoticon);
                            }
                            text[0] = true;
                        }
                    } else {
                        if (messageObject.isMediaEmpty()) {
                            if (!shortMessage) {
                                if (!TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                    msg = LocaleController.formatString(R.string.NotificationMessageText, name, messageObject.messageOwner.message);
                                    text[0] = true;
                                } else {
                                    msg = LocaleController.formatString(R.string.NotificationMessageNoText, name);
                                }
                            } else {
                                msg = LocaleController.formatString(R.string.NotificationMessageNoText, name);
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                            if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                msg = LocaleController.formatString(R.string.NotificationMessageText, name, "\uD83D\uDDBC " + messageObject.messageOwner.message);
                                text[0] = true;
                            } else {
                                if (messageObject.messageOwner.media.ttl_seconds != 0) {
                                    msg = LocaleController.formatString(R.string.NotificationMessageSDPhoto, name);
                                } else {
                                    msg = LocaleController.formatString(R.string.NotificationMessagePhoto, name);
                                }
                            }
                        } else if (messageObject.isVideo()) {
                            if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                msg = LocaleController.formatString(R.string.NotificationMessageText, name, "\uD83D\uDCF9 " + messageObject.messageOwner.message);
                                text[0] = true;
                            } else {
                                if (messageObject.messageOwner.media.ttl_seconds != 0) {
                                    msg = LocaleController.formatString(R.string.NotificationMessageSDVideo, name);
                                } else {
                                    msg = LocaleController.formatString(R.string.NotificationMessageVideo, name);
                                }
                            }
                        } else if (messageObject.isGame()) {
                            msg = LocaleController.formatString(R.string.NotificationMessageGame, name, messageObject.messageOwner.media.game.title);
                        } else if (messageObject.isVoice()) {
                            msg = LocaleController.formatString(R.string.NotificationMessageAudio, name);
                        } else if (messageObject.isRoundVideo()) {
                            msg = LocaleController.formatString(R.string.NotificationMessageRound, name);
                        } else if (messageObject.isMusic()) {
                            msg = LocaleController.formatString(R.string.NotificationMessageMusic, name);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                            TLRPC.TL_messageMediaContact mediaContact = (TLRPC.TL_messageMediaContact) messageObject.messageOwner.media;
                            msg = LocaleController.formatString(R.string.NotificationMessageContact2, name, ContactsController.formatName(mediaContact.first_name, mediaContact.last_name));
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGiveaway) {
                            TLRPC.TL_messageMediaGiveaway giveaway = (TLRPC.TL_messageMediaGiveaway) messageObject.messageOwner.media;
                            msg = LocaleController.formatString(R.string.NotificationMessageChannelGiveaway, name, giveaway.quantity, giveaway.months);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGiveawayResults) {
                            msg = LocaleController.formatString(R.string.BoostingGiveawayResults);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                            TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media;
                            if (mediaPoll.poll.quiz) {
                                msg = LocaleController.formatString(R.string.NotificationMessageQuiz2, name, mediaPoll.poll.question.text);
                            } else {
                                msg = LocaleController.formatString(R.string.NotificationMessagePoll2, name, mediaPoll.poll.question.text);
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                            msg = LocaleController.formatString(R.string.NotificationMessageMap, name);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                            msg = LocaleController.formatString(R.string.NotificationMessageLiveLocation, name);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                            if (messageObject.isSticker() || messageObject.isAnimatedSticker()) {
                                String emoji = messageObject.getStickerEmoji();
                                if (emoji != null) {
                                    msg = LocaleController.formatString(R.string.NotificationMessageStickerEmoji, name, emoji);
                                } else {
                                    msg = LocaleController.formatString(R.string.NotificationMessageSticker, name);
                                }
                            } else if (messageObject.isGif()) {
                                if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                    msg = LocaleController.formatString(R.string.NotificationMessageText, name, "\uD83C\uDFAC " + messageObject.messageOwner.message);
                                    text[0] = true;
                                } else {
                                    msg = LocaleController.formatString(R.string.NotificationMessageGif, name);
                                }
                            } else {
                                if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                    msg = LocaleController.formatString(R.string.NotificationMessageText, name, "\uD83D\uDCCE " + messageObject.messageOwner.message);
                                    text[0] = true;
                                } else {
                                    msg = LocaleController.formatString(R.string.NotificationMessageDocument, name);
                                }
                            }
                        } else {
                            if (!shortMessage && !TextUtils.isEmpty(messageObject.messageText)) {
                                msg = LocaleController.formatString(R.string.NotificationMessageText, name, messageObject.messageText);
                                text[0] = true;
                            } else {
                                msg = LocaleController.formatString(R.string.NotificationMessageNoText, name);
                            }
                        }
                    }
                } else {
                    if (preview != null) {
                        preview[0] = false;
                    }
                    msg = LocaleController.formatString(R.string.NotificationMessageNoText, name);
                }
            } else if (chatId != 0) {
                boolean isChannel = ChatObject.isChannel(chat) && !chat.megagroup;
                if (dialogPreviewEnabled && (!isChannel && preferences.getBoolean("EnablePreviewGroup", true) || isChannel && preferences.getBoolean("EnablePreviewChannel", true))) {
                    if (messageObject.messageOwner instanceof TLRPC.TL_messageService) {
                        if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatAddUser) {
                            long singleUserId = messageObject.messageOwner.action.user_id;
                            if (singleUserId == 0 && messageObject.messageOwner.action.users.size() == 1) {
                                singleUserId = messageObject.messageOwner.action.users.get(0);
                            }
                            if (singleUserId != 0) {
                                if (messageObject.messageOwner.peer_id.channel_id != 0 && !chat.megagroup) {
                                    msg = LocaleController.formatString(R.string.ChannelAddedByNotification, name, chat.title);
                                } else {
                                    if (singleUserId == selfUsedId) {
                                        msg = LocaleController.formatString(R.string.NotificationInvitedToGroup, name, chat.title);
                                    } else {
                                        TLRPC.User u2 = getMessagesController().getUser(singleUserId);
                                        if (u2 == null) {
                                            return null;
                                        }
                                        if (fromId == u2.id) {
                                            if (chat.megagroup) {
                                                msg = LocaleController.formatString(R.string.NotificationGroupAddSelfMega, name, chat.title);
                                            } else {
                                                msg = LocaleController.formatString(R.string.NotificationGroupAddSelf, name, chat.title);
                                            }
                                        } else {
                                            msg = LocaleController.formatString(R.string.NotificationGroupAddMember, name, chat.title, UserObject.getUserName(u2));
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
                                msg = LocaleController.formatString(R.string.NotificationGroupAddMember, name, chat.title, names.toString());
                            }
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGroupCall) {
                            if (messageObject.messageOwner.action.duration != 0) {
                                return LocaleController.formatString(R.string.NotificationGroupEndedCall, name, chat.title);
                            } else {
                                return LocaleController.formatString(R.string.NotificationGroupCreatedCall, name, chat.title);
                            }
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGroupCallScheduled) {
                            msg = messageObject.messageText.toString();
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionInviteToGroupCall) {
                            long singleUserId = messageObject.messageOwner.action.user_id;
                            if (singleUserId == 0 && messageObject.messageOwner.action.users.size() == 1) {
                                singleUserId = messageObject.messageOwner.action.users.get(0);
                            }
                            if (singleUserId != 0) {
                                if (singleUserId == selfUsedId) {
                                    msg = LocaleController.formatString(R.string.NotificationGroupInvitedYouToCall, name, chat.title);
                                } else {
                                    TLRPC.User u2 = getMessagesController().getUser(singleUserId);
                                    if (u2 == null) {
                                        return null;
                                    }
                                    msg = LocaleController.formatString(R.string.NotificationGroupInvitedToCall, name, chat.title, UserObject.getUserName(u2));
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
                                msg = LocaleController.formatString(R.string.NotificationGroupInvitedToCall, name, chat.title, names.toString());
                            }
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGiftCode) {
                            TLRPC.TL_messageActionGiftCode giftCode = (TLRPC.TL_messageActionGiftCode) messageObject.messageOwner.action;
                            TLRPC.Chat fromChat = MessagesController.getInstance(currentAccount).getChat(-DialogObject.getPeerDialogId(giftCode.boost_peer));
                            String from = fromChat == null ? null : fromChat.title;
                            if (from == null) {
                                msg = LocaleController.getString(R.string.BoostingReceivedGiftNoName);
                            } else {
                                msg = LocaleController.formatString(R.string.NotificationMessageGiftCode, from, LocaleController.formatPluralString("Months", giftCode.months));
                            }
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatJoinedByLink) {
                            msg = LocaleController.formatString(R.string.NotificationInvitedToGroupByLink, name, chat.title);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatEditTitle) {
                            msg = LocaleController.formatString(R.string.NotificationEditedGroupName, name, messageObject.messageOwner.action.title);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatEditPhoto || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
                            if (messageObject.messageOwner.peer_id.channel_id != 0 && !chat.megagroup) {
                                if (messageObject.isVideoAvatar()) {
                                    msg = LocaleController.formatString(R.string.ChannelVideoEditNotification, chat.title);
                                } else {
                                    msg = LocaleController.formatString(R.string.ChannelPhotoEditNotification, chat.title);
                                }
                            } else {
                                if (messageObject.isVideoAvatar()) {
                                    msg = LocaleController.formatString(R.string.NotificationEditedGroupVideo, name, chat.title);
                                } else {
                                    msg = LocaleController.formatString(R.string.NotificationEditedGroupPhoto, name, chat.title);
                                }
                            }
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                            if (messageObject.messageOwner.action.user_id == selfUsedId) {
                                msg = LocaleController.formatString(R.string.NotificationGroupKickYou, name, chat.title);
                            } else if (messageObject.messageOwner.action.user_id == fromId) {
                                msg = LocaleController.formatString(R.string.NotificationGroupLeftMember, name, chat.title);
                            } else {
                                TLRPC.User u2 = getMessagesController().getUser(messageObject.messageOwner.action.user_id);
                                if (u2 == null) {
                                    return null;
                                }
                                msg = LocaleController.formatString(R.string.NotificationGroupKickMember, name, chat.title, UserObject.getUserName(u2));
                            }
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatCreate) {
                            msg = messageObject.messageText.toString();
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChannelCreate) {
                            msg = messageObject.messageText.toString();
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatMigrateTo) {
                            msg = LocaleController.formatString(R.string.ActionMigrateFromGroupNotify, chat.title);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChannelMigrateFrom) {
                            msg = LocaleController.formatString(R.string.ActionMigrateFromGroupNotify, messageObject.messageOwner.action.title);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionScreenshotTaken) {
                            msg = messageObject.messageText.toString();
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPinMessage) {
                            if (!ChatObject.isChannel(chat) || chat.megagroup) {
                                if (messageObject.replyMessageObject == null) {
                                    msg = LocaleController.formatString(R.string.NotificationActionPinnedNoText, name, chat.title);
                                } else {
                                    MessageObject object = messageObject.replyMessageObject;
                                    if (object.isMusic()) {
                                        msg = LocaleController.formatString(R.string.NotificationActionPinnedMusic, name, chat.title);
                                    } else if (object.isVideo()) {
                                        if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                            String message = "\uD83D\uDCF9 " + object.messageOwner.message;
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedText, name, message, chat.title);
                                        } else {
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedVideo, name, chat.title);
                                        }
                                    } else if (object.isGif()) {
                                        if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                            String message = "\uD83C\uDFAC " + object.messageOwner.message;
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedText, name, message, chat.title);
                                        } else {
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedGif, name, chat.title);
                                        }
                                    } else if (object.isVoice()) {
                                        msg = LocaleController.formatString(R.string.NotificationActionPinnedVoice, name, chat.title);
                                    } else if (object.isRoundVideo()) {
                                        msg = LocaleController.formatString(R.string.NotificationActionPinnedRound, name, chat.title);
                                    } else if (object.isSticker() || object.isAnimatedSticker()) {
                                        String emoji = object.getStickerEmoji();
                                        if (emoji != null) {
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedStickerEmoji, name, chat.title, emoji);
                                        } else {
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedSticker, name, chat.title);
                                        }
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                                        if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                            String message = "\uD83D\uDCCE " + object.messageOwner.message;
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedText, name, message, chat.title);
                                        } else {
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedFile, name, chat.title);
                                        }
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || object.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                                        msg = LocaleController.formatString(R.string.NotificationActionPinnedGeo, name, chat.title);
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                                        msg = LocaleController.formatString(R.string.NotificationActionPinnedGeoLive, name, chat.title);
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                                        TLRPC.TL_messageMediaContact mediaContact = (TLRPC.TL_messageMediaContact) messageObject.messageOwner.media;
                                        msg = LocaleController.formatString(R.string.NotificationActionPinnedContact2, name, chat.title, ContactsController.formatName(mediaContact.first_name, mediaContact.last_name));
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                                        TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) object.messageOwner.media;
                                        if (mediaPoll.poll.quiz) {
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedQuiz2, name, chat.title, mediaPoll.poll.question.text);
                                        } else {
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedPoll2, name, chat.title, mediaPoll.poll.question.text);
                                        }
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                                        if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                            String message = "\uD83D\uDDBC " + object.messageOwner.message;
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedText, name, message, chat.title);
                                        } else {
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedPhoto, name, chat.title);
                                        }
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                                        msg = LocaleController.formatString(R.string.NotificationActionPinnedGame, name, chat.title);
                                    } else if (object.messageText != null && object.messageText.length() > 0) {
                                        CharSequence message = object.messageText;
                                        if (message.length() > 20) {
                                            message = message.subSequence(0, 20) + "...";
                                        }
                                        msg = LocaleController.formatString(R.string.NotificationActionPinnedText, name, message, chat.title);
                                    } else {
                                        msg = LocaleController.formatString(R.string.NotificationActionPinnedNoText, name, chat.title);
                                    }
                                }
                            } else {
                                if (messageObject.replyMessageObject == null) {
                                    msg = LocaleController.formatString(R.string.NotificationActionPinnedNoTextChannel, chat.title);
                                } else {
                                    MessageObject object = messageObject.replyMessageObject;
                                    if (object.isMusic()) {
                                        msg = LocaleController.formatString(R.string.NotificationActionPinnedMusicChannel, chat.title);
                                    } else if (object.isVideo()) {
                                        if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                            String message = "\uD83D\uDCF9 " + object.messageOwner.message;
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedTextChannel, chat.title, message);
                                        } else {
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedVideoChannel, chat.title);
                                        }
                                    } else if (object.isGif()) {
                                        if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                            String message = "\uD83C\uDFAC " + object.messageOwner.message;
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedTextChannel, chat.title, message);
                                        } else {
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedGifChannel, chat.title);
                                        }
                                    } else if (object.isVoice()) {
                                        msg = LocaleController.formatString(R.string.NotificationActionPinnedVoiceChannel, chat.title);
                                    } else if (object.isRoundVideo()) {
                                        msg = LocaleController.formatString(R.string.NotificationActionPinnedRoundChannel, chat.title);
                                    } else if (object.isSticker() || object.isAnimatedSticker()) {
                                        String emoji = object.getStickerEmoji();
                                        if (emoji != null) {
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedStickerEmojiChannel, chat.title, emoji);
                                        } else {
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedStickerChannel, chat.title);
                                        }
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                                        if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                            String message = "\uD83D\uDCCE " + object.messageOwner.message;
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedTextChannel, chat.title, message);
                                        } else {
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedFileChannel, chat.title);
                                        }
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || object.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                                        msg = LocaleController.formatString(R.string.NotificationActionPinnedGeoChannel, chat.title);
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                                        msg = LocaleController.formatString(R.string.NotificationActionPinnedGeoLiveChannel, chat.title);
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                                        TLRPC.TL_messageMediaContact mediaContact = (TLRPC.TL_messageMediaContact) messageObject.messageOwner.media;
                                        msg = LocaleController.formatString(R.string.NotificationActionPinnedContactChannel2, chat.title, ContactsController.formatName(mediaContact.first_name, mediaContact.last_name));
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                                        TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) object.messageOwner.media;
                                        if (mediaPoll.poll.quiz) {
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedQuizChannel2, chat.title, mediaPoll.poll.question.text);
                                        } else {
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedPollChannel2, chat.title, mediaPoll.poll.question.text);
                                        }
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                                        if (Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(object.messageOwner.message)) {
                                            String message = "\uD83D\uDDBC " + object.messageOwner.message;
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedTextChannel, chat.title, message);
                                        } else {
                                            msg = LocaleController.formatString(R.string.NotificationActionPinnedPhotoChannel, chat.title);
                                        }
                                    } else if (object.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                                        msg = LocaleController.formatString(R.string.NotificationActionPinnedGameChannel, chat.title);
                                    } else if (object.messageText != null && object.messageText.length() > 0) {
                                        CharSequence message = object.messageText;
                                        if (message.length() > 20) {
                                            message = message.subSequence(0, 20) + "...";
                                        }
                                        msg = LocaleController.formatString(R.string.NotificationActionPinnedTextChannel, chat.title, message);
                                    } else {
                                        msg = LocaleController.formatString(R.string.NotificationActionPinnedNoTextChannel, chat.title);
                                    }
                                }
                            }
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGameScore) {
                            msg = messageObject.messageText.toString();
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionSetChatTheme) {
                            String emoticon = ((TLRPC.TL_messageActionSetChatTheme) messageObject.messageOwner.action).emoticon;
                            if (TextUtils.isEmpty(emoticon)) {
                                msg = dialogId == selfUsedId
                                        ? LocaleController.formatString(R.string.ChatThemeDisabledYou)
                                        : LocaleController.formatString("ChatThemeDisabled", R.string.ChatThemeDisabled, name, emoticon);
                            } else {
                                msg = dialogId == selfUsedId
                                        ? LocaleController.formatString(R.string.ChatThemeChangedYou, emoticon)
                                        : LocaleController.formatString(R.string.ChatThemeChangedTo, name, emoticon);
                            }
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatJoinedByRequest) {
                            msg = messageObject.messageText.toString();
                        }
                    } else if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        if (messageObject.isMediaEmpty()) {
                            if (!shortMessage && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                msg = LocaleController.formatString(R.string.NotificationMessageText, name, messageObject.messageOwner.message);
                                text[0] = true;
                            } else {
                                msg = LocaleController.formatString(R.string.ChannelMessageNoText, name);
                            }
                        } else if (messageObject.type == MessageObject.TYPE_PAID_MEDIA && MessageObject.getMedia(messageObject) instanceof TLRPC.TL_messageMediaPaidMedia) {
                            TLRPC.TL_messageMediaPaidMedia paidMedia = (TLRPC.TL_messageMediaPaidMedia) MessageObject.getMedia(messageObject);
                            msg = LocaleController.formatPluralString("NotificationChannelMessagePaidMedia", (int) paidMedia.stars_amount, chat.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                            if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                msg = LocaleController.formatString(R.string.NotificationMessageText, name, "\uD83D\uDDBC " + messageObject.messageOwner.message);
                                text[0] = true;
                            } else {
                                msg = LocaleController.formatString(R.string.ChannelMessagePhoto, name);
                            }
                        } else if (messageObject.isVideo()) {
                            if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                msg = LocaleController.formatString(R.string.NotificationMessageText, name, "\uD83D\uDCF9 " + messageObject.messageOwner.message);
                                text[0] = true;
                            } else {
                                msg = LocaleController.formatString(R.string.ChannelMessageVideo, name);
                            }
                        } else if (messageObject.isVoice()) {
                            msg = LocaleController.formatString(R.string.ChannelMessageAudio, name);
                        } else if (messageObject.isRoundVideo()) {
                            msg = LocaleController.formatString(R.string.ChannelMessageRound, name);
                        } else if (messageObject.isMusic()) {
                            msg = LocaleController.formatString(R.string.ChannelMessageMusic, name);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                            TLRPC.TL_messageMediaContact mediaContact = (TLRPC.TL_messageMediaContact) messageObject.messageOwner.media;
                            msg = LocaleController.formatString(R.string.ChannelMessageContact2, name, ContactsController.formatName(mediaContact.first_name, mediaContact.last_name));
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                            TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media;
                            if (mediaPoll.poll.quiz) {
                                msg = LocaleController.formatString(R.string.ChannelMessageQuiz2, name, mediaPoll.poll.question.text);
                            } else {
                                msg = LocaleController.formatString(R.string.ChannelMessagePoll2, name, mediaPoll.poll.question.text);
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGiveaway) {
                            TLRPC.TL_messageMediaGiveaway giveaway = (TLRPC.TL_messageMediaGiveaway) messageObject.messageOwner.media;
                            msg = LocaleController.formatString(R.string.NotificationMessageChannelGiveaway, chat.title, giveaway.quantity, giveaway.months);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                            msg = LocaleController.formatString(R.string.ChannelMessageMap, name);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                            msg = LocaleController.formatString(R.string.ChannelMessageLiveLocation, name);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                            if (messageObject.isSticker() || messageObject.isAnimatedSticker()) {
                                String emoji = messageObject.getStickerEmoji();
                                if (emoji != null) {
                                    msg = LocaleController.formatString(R.string.ChannelMessageStickerEmoji, name, emoji);
                                } else {
                                    msg = LocaleController.formatString(R.string.ChannelMessageSticker, name);
                                }
                            } else if (messageObject.isGif()) {
                                if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                    msg = LocaleController.formatString(R.string.NotificationMessageText, name, "\uD83C\uDFAC " + messageObject.messageOwner.message);
                                    text[0] = true;
                                } else {
                                    msg = LocaleController.formatString(R.string.ChannelMessageGIF, name);
                                }
                            } else {
                                if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                    msg = LocaleController.formatString(R.string.NotificationMessageText, name, "\uD83D\uDCCE " + messageObject.messageOwner.message);
                                    text[0] = true;
                                } else {
                                    msg = LocaleController.formatString(R.string.ChannelMessageDocument, name);
                                }
                            }
                        } else {
                            if (!shortMessage && !TextUtils.isEmpty(messageObject.messageText)) {
                                msg = LocaleController.formatString(R.string.NotificationMessageText, name, messageObject.messageText);
                                text[0] = true;
                            } else {
                                msg = LocaleController.formatString(R.string.ChannelMessageNoText, name);
                            }
                        }
                    } else {
                        if (messageObject.isMediaEmpty()) {
                            if (!shortMessage && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                msg = LocaleController.formatString(R.string.NotificationMessageGroupText, name, chat.title, messageObject.messageOwner.message);
                            } else {
                                msg = LocaleController.formatString(R.string.NotificationMessageGroupNoText, name, chat.title);
                            }
                        } else if (messageObject.type == MessageObject.TYPE_PAID_MEDIA && MessageObject.getMedia(messageObject) instanceof TLRPC.TL_messageMediaPaidMedia) {
                            TLRPC.TL_messageMediaPaidMedia paidMedia = (TLRPC.TL_messageMediaPaidMedia) MessageObject.getMedia(messageObject);
                            msg = LocaleController.formatPluralString("NotificationChatMessagePaidMedia", (int) paidMedia.stars_amount, name, chat.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                            if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                msg = LocaleController.formatString(R.string.NotificationMessageGroupText, name, chat.title, "\uD83D\uDDBC " + messageObject.messageOwner.message);
                            } else {
                                msg = LocaleController.formatString(R.string.NotificationMessageGroupPhoto, name, chat.title);
                            }
                        } else if (messageObject.isVideo()) {
                            if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                msg = LocaleController.formatString(R.string.NotificationMessageGroupText, name, chat.title, "\uD83D\uDCF9 " + messageObject.messageOwner.message);
                            } else {
                                msg = LocaleController.formatString(R.string.NotificationMessageGroupVideo, name, chat.title);
                            }
                        } else if (messageObject.isVoice()) {
                            msg = LocaleController.formatString(R.string.NotificationMessageGroupAudio, name, chat.title);
                        } else if (messageObject.isRoundVideo()) {
                            msg = LocaleController.formatString(R.string.NotificationMessageGroupRound, name, chat.title);
                        } else if (messageObject.isMusic()) {
                            msg = LocaleController.formatString(R.string.NotificationMessageGroupMusic, name, chat.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                            TLRPC.TL_messageMediaContact mediaContact = (TLRPC.TL_messageMediaContact) messageObject.messageOwner.media;
                            msg = LocaleController.formatString(R.string.NotificationMessageGroupContact2, name, chat.title, ContactsController.formatName(mediaContact.first_name, mediaContact.last_name));
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                            TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media;
                            if (mediaPoll.poll.quiz) {
                                msg = LocaleController.formatString(R.string.NotificationMessageGroupQuiz2, name, chat.title, mediaPoll.poll.question.text);
                            } else {
                                msg = LocaleController.formatString(R.string.NotificationMessageGroupPoll2, name, chat.title, mediaPoll.poll.question.text);
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                            msg = LocaleController.formatString(R.string.NotificationMessageGroupGame, name, chat.title, messageObject.messageOwner.media.game.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGiveaway) {
                            TLRPC.TL_messageMediaGiveaway giveaway = (TLRPC.TL_messageMediaGiveaway) messageObject.messageOwner.media;
                            msg = LocaleController.formatString(R.string.NotificationMessageChannelGiveaway, chat.title, giveaway.quantity, giveaway.months);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGiveawayResults) {
                            msg = LocaleController.formatString(R.string.BoostingGiveawayResults);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                            msg = LocaleController.formatString("NotificationMessageGroupMap", R.string.NotificationMessageGroupMap, name, chat.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                            msg = LocaleController.formatString(R.string.NotificationMessageGroupLiveLocation, name, chat.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                            if (messageObject.isSticker() || messageObject.isAnimatedSticker()) {
                                String emoji = messageObject.getStickerEmoji();
                                if (emoji != null) {
                                    msg = LocaleController.formatString(R.string.NotificationMessageGroupStickerEmoji, name, chat.title, emoji);
                                } else {
                                    msg = LocaleController.formatString(R.string.NotificationMessageGroupSticker, name, chat.title);
                                }
                            } else if (messageObject.isGif()) {
                                if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                    msg = LocaleController.formatString(R.string.NotificationMessageGroupText, name, chat.title, "\uD83C\uDFAC " + messageObject.messageOwner.message);
                                } else {
                                    msg = LocaleController.formatString(R.string.NotificationMessageGroupGif, name, chat.title);
                                }
                            } else {
                                if (!shortMessage && Build.VERSION.SDK_INT >= 19 && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
                                    msg = LocaleController.formatString(R.string.NotificationMessageGroupText, name, chat.title, "\uD83D\uDCCE " + messageObject.messageOwner.message);
                                } else {
                                    msg = LocaleController.formatString(R.string.NotificationMessageGroupDocument, name, chat.title);
                                }
                            }
                        } else {
                            if (!shortMessage && !TextUtils.isEmpty(messageObject.messageText)) {
                                msg = LocaleController.formatString(R.string.NotificationMessageGroupText, name, chat.title, messageObject.messageText);
                            } else {
                                msg = LocaleController.formatString(R.string.NotificationMessageGroupNoText, name, chat.title);
                            }
                        }
                    }
                } else {
                    if (preview != null) {
                        preview[0] = false;
                    }
                    if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        msg = LocaleController.formatString(R.string.ChannelMessageNoText, name);
                    } else if (messageObject.type == MessageObject.TYPE_PAID_MEDIA && MessageObject.getMedia(messageObject) instanceof TLRPC.TL_messageMediaPaidMedia) {
                        TLRPC.TL_messageMediaPaidMedia paidMedia = (TLRPC.TL_messageMediaPaidMedia) MessageObject.getMedia(messageObject);
                        msg = LocaleController.formatPluralString("NotificationMessagePaidMedia", (int) paidMedia.stars_amount, name);
                    } else {
                        msg = LocaleController.formatString(R.string.NotificationMessageGroupNoText, name, chat.title);
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
            PendingIntent pintent = PendingIntent.getService(ApplicationLoader.applicationContext, 0, intent, PendingIntent.FLAG_MUTABLE);
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
            int minutes = preferences.getInt("repeat_messages", 60);
            if (minutes > 0 && personalCount > 0) {
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
                && (messageObject.messageOwner.action == null || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionEmpty) || messageObject.isStoryReactionPush;
    }

    private int getNotifyOverride(SharedPreferences preferences, long dialog_id, long topicId) {
        int notifyOverride = dialogsNotificationsFacade.getProperty(NotificationsSettingsFacade.PROPERTY_NOTIFY, dialog_id, topicId, -1);
        if (notifyOverride == 3) {
            int muteUntil = dialogsNotificationsFacade.getProperty(NotificationsSettingsFacade.PROPERTY_NOTIFY_UNTIL, dialog_id, topicId, 0);
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
        FileLog.d("NotificationsController dismissNotification");
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
            int notifyOverride = getNotifyOverride(preferences, openedDialogId, openedTopicId);
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

    public void deleteNotificationChannel(long dialogId, long topicId) {
        deleteNotificationChannel(dialogId, topicId, -1);
    }

    private void deleteNotificationChannelInternal(long dialogId, long topicId, int what) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        try {
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
            SharedPreferences.Editor editor = preferences.edit();
            if (what == 0 || what == -1) {
                String key = "org.telegram.key" + dialogId;
                if (topicId != 0) {
                    key += ".topic" + topicId;
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

    public void deleteNotificationChannel(long dialogId, long topicId, int what) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        notificationsQueue.postRunnable(() -> deleteNotificationChannelInternal(dialogId, topicId, what));
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
                } else if (type == TYPE_STORIES) {
                    key = "stories";
                } else if (type == TYPE_REACTIONS_MESSAGES || type == TYPE_REACTIONS_STORIES) {
                    key = "reactions";
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
                } else if (type == TYPE_STORIES) {
                    key = "stories_ia";
                } else if (type == TYPE_REACTIONS_MESSAGES || type == TYPE_REACTIONS_STORIES) {
                    key = "reactions_ia";
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
            } else if (type == TYPE_STORIES) {
                overwriteKey = "overwrite_stories";
            } else if (type == TYPE_REACTIONS_MESSAGES || type == TYPE_REACTIONS_STORIES) {
                overwriteKey = "overwrite_reactions";
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
    private String createNotificationShortcut(NotificationCompat.Builder builder, long did, String name, TLRPC.User user, TLRPC.Chat chat, Person person, boolean supportsBubble) {
        if (unsupportedNotificationShortcut() || ChatObject.isChannel(chat) && !chat.megagroup) {
            return null;
        }
        try {
            String id = "ndid_" + did;

            Intent shortcutIntent = new Intent(ApplicationLoader.applicationContext, OpenChatReceiver.class);
            shortcutIntent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
            if (did > 0) {
                shortcutIntent.putExtra("userId", did);
            } else {
                shortcutIntent.putExtra("chatId", -did);
            }

            ShortcutInfoCompat.Builder shortcutBuilder = new ShortcutInfoCompat.Builder(ApplicationLoader.applicationContext, id)
                    .setShortLabel(chat != null ? name : UserObject.getFirstName(user))
                    .setLongLabel(name)
                    .setIntent(new Intent(Intent.ACTION_DEFAULT))
                    .setIntent(shortcutIntent)
                    .setLongLived(true)
                    .setLocusId(new LocusIdCompat(id));

            Bitmap avatar = null;
            if (person != null) {
                shortcutBuilder.setPerson(person);
                shortcutBuilder.setIcon(person.getIcon());
                if (person.getIcon() != null) {
                    avatar = person.getIcon().getBitmap();
                }
            }
            ShortcutInfoCompat shortcut = shortcutBuilder.build();
            ShortcutManagerCompat.pushDynamicShortcut(ApplicationLoader.applicationContext, shortcut);
            builder.setShortcutInfo(shortcut);
            Intent intent = new Intent(ApplicationLoader.applicationContext, BubbleActivity.class);
            intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
            if (DialogObject.isUserDialog(did)) {
                intent.putExtra("userId", did);
            } else {
                intent.putExtra("chatId", -did);
            }
            intent.putExtra("currentAccount", currentAccount);

            IconCompat icon;
            if (avatar != null) {
                icon = IconCompat.createWithAdaptiveBitmap(avatar);
            } else if (user != null) {
                icon = IconCompat.createWithResource(ApplicationLoader.applicationContext, user.bot ? R.drawable.book_bot : R.drawable.book_user);
            } else {
                icon = IconCompat.createWithResource(ApplicationLoader.applicationContext, R.drawable.book_group);
            }
            if (supportsBubble) {
                NotificationCompat.BubbleMetadata.Builder bubbleBuilder =
                        new NotificationCompat.BubbleMetadata.Builder(
                                PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT),
                                icon);
                bubbleBuilder.setSuppressNotification(openedDialogId == did);
                bubbleBuilder.setAutoExpandBubble(false);
                bubbleBuilder.setDesiredHeight(AndroidUtilities.dp(640));
                builder.setBubbleMetadata(bubbleBuilder.build());
            } else {
                builder.setBubbleMetadata(null);
            }
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
            groupsCreated = preferences.getBoolean("groupsCreated5", false);
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
                            } else if (id.contains("_reactions_")) {
                                if (editor == null) {
                                    editor = getAccountInstance().getNotificationsSettings().edit();
                                }
                                editor.remove("priority_react").remove("vibrate_react").remove("ReactionSoundPath").remove("ReactionSound");
                            }  else if (id.contains("_groups_")) {
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
            preferences.edit().putBoolean("groupsCreated5", true).commit();
            groupsCreated = true;
        }
        if (!channelGroupsCreated) {
            List<NotificationChannelGroup> list = systemNotificationManager.getNotificationChannelGroups();
            String channelsId = "channels" + currentAccount;
            String groupsId = "groups" + currentAccount;
            String privateId = "private" + currentAccount;
            String storiesId = "stories" + currentAccount;
            String reactionsId = "reactions" + currentAccount;
            String otherId = "other" + currentAccount;
            for (int a = 0, N = list.size(); a < N; a++) {
                String id = list.get(a).getId();
                if (channelsId != null && channelsId.equals(id)) {
                    channelsId = null;
                } else if (groupsId != null && groupsId.equals(id)) {
                    groupsId = null;
                } else if (storiesId != null && storiesId.equals(id)) {
                    storiesId = null;
                } else if (reactionsId != null && reactionsId.equals(id)) {
                    reactionsId = null;
                } else if (privateId != null && privateId.equals(id)) {
                    privateId = null;
                } else if (otherId != null && otherId.equals(id)) {
                    otherId = null;
                }
                if (channelsId == null && storiesId == null && reactionsId == null && groupsId == null && privateId == null && otherId == null) {
                    break;
                }
            }

            if (channelsId != null || groupsId != null || reactionsId != null || storiesId != null || privateId != null || otherId != null) {
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

                ArrayList<NotificationChannelGroup> channelGroups = new ArrayList<>();
                if (channelsId != null) {
                    channelGroups.add(new NotificationChannelGroup(channelsId, LocaleController.getString(R.string.NotificationsChannels) + userName));
                }
                if (groupsId != null) {
                    channelGroups.add(new NotificationChannelGroup(groupsId, LocaleController.getString(R.string.NotificationsGroups) + userName));
                }
                if (storiesId != null) {
                    channelGroups.add(new NotificationChannelGroup(storiesId, LocaleController.getString(R.string.NotificationsStories) + userName));
                }
                if (reactionsId != null) {
                    channelGroups.add(new NotificationChannelGroup(reactionsId, LocaleController.getString(R.string.NotificationsReactions) + userName));
                }
                if (privateId != null) {
                    channelGroups.add(new NotificationChannelGroup(privateId, LocaleController.getString(R.string.NotificationsPrivateChats) + userName));
                }
                if (otherId != null) {
                    channelGroups.add(new NotificationChannelGroup(otherId, LocaleController.getString(R.string.NotificationsOther) + userName));
                }

                systemNotificationManager.createNotificationChannelGroups(channelGroups);
            }

            channelGroupsCreated = true;
        }
    }

    @TargetApi(26)
    private String validateChannelId(long dialogId, long topicId, String name, long[] vibrationPattern, int ledColor, Uri sound, int importance, boolean isDefault, boolean isInApp, boolean isSilent, int type) {
        ensureGroupsCreated();

        SharedPreferences preferences = getAccountInstance().getNotificationsSettings();

        String key;
        String groupId;
        String overwriteKey;
        if (isSilent) {
            groupId = "other" + currentAccount;
            overwriteKey = null;
        } else if (type == TYPE_CHANNEL) {
            groupId = "channels" + currentAccount;
            overwriteKey = "overwrite_channel";
        } else if (type == TYPE_GROUP) {
            groupId = "groups" + currentAccount;
            overwriteKey = "overwrite_group";
        } else if (type == TYPE_STORIES) {
            groupId = "stories" + currentAccount;
            overwriteKey = "overwrite_stories";
        } else if (type == TYPE_REACTIONS_MESSAGES || type == TYPE_REACTIONS_STORIES) {
            groupId = "reactions" + currentAccount;
            overwriteKey = "overwrite_reactions";
        } else {
            groupId = "private" + currentAccount;
            overwriteKey = "overwrite_private";
        }

        boolean secretChat = !isDefault && DialogObject.isEncryptedDialog(dialogId);
        boolean shouldOverwrite = !isInApp && overwriteKey != null && preferences.getBoolean(overwriteKey, false);

        int nosoundPatch = 2; // when changing code here about no-sound issues, make sure to increment this value
        String soundHash = Utilities.MD5(sound == null ? "NoSound" + nosoundPatch : sound.toString());
        if (soundHash != null && soundHash.length() > 5) {
            soundHash = soundHash.substring(0, 5);
        }
        if (isSilent) {
            name = LocaleController.getString(R.string.NotificationsSilent);
            key = "silent";
        } else if (isDefault) {
            name = isInApp ? LocaleController.getString(R.string.NotificationsInAppDefault) : LocaleController.getString(R.string.NotificationsDefault);
            if (type == TYPE_CHANNEL) {
                key = isInApp ? "channels_ia" : "channels";
            } else if (type == TYPE_GROUP) {
                key = isInApp ? "groups_ia" : "groups";
            } else if (type == TYPE_STORIES) {
                key = isInApp ? "stories_ia" : "stories";
            } else if (type == TYPE_REACTIONS_MESSAGES || type == TYPE_REACTIONS_STORIES) {
                key = isInApp ? "reactions_ia" : "reactions";
            } else {
                key = isInApp ? "private_ia" : "private";
            }
        } else {
            if (isInApp) {
                name = LocaleController.formatString(R.string.NotificationsChatInApp, name);
            }
            //TODO notifications
            key = (isInApp ? "org.telegram.keyia" : "org.telegram.key") + dialogId + "_" + topicId;
        }
        key += "_" + soundHash;
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
                    if (isInApp && importance != channelImportance) {
                        shouldOverwrite = true;
                    } else if (!newSettingsHash.equals(settings)) {
                        SharedPreferences.Editor editor = null;
                        if (channelImportance == NotificationManager.IMPORTANCE_NONE) {
                            editor = preferences.edit();
                            if (isDefault) {
                                if (!isInApp) {
                                    if (type == TYPE_STORIES) {
                                        editor.putBoolean("EnableAllStories", false);
                                    } else if (type == TYPE_REACTIONS_MESSAGES) {
                                        editor.putBoolean("EnableReactionsMessages", true);
                                        editor.putBoolean("EnableReactionsStories", true);
                                    } else {
                                        editor.putInt(getGlobalNotificationsKey(type), Integer.MAX_VALUE);
                                    }
                                    updateServerNotificationsSettings(type);
                                }
                            } else {
                                if (type == TYPE_STORIES) {
                                    editor.putBoolean("stories_" + NotificationsController.getSharedPrefKey(dialogId, 0), false);
                                } else {
                                    editor.putInt("notify2_" + NotificationsController.getSharedPrefKey(dialogId, 0), 2);
                                }
                                updateServerNotificationsSettings(dialogId, 0, true);
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
                                    if (type == TYPE_STORIES) {
                                        editor.putBoolean("EnableAllStories", true);
                                    } else if (type == TYPE_REACTIONS_MESSAGES) {
                                        editor.putBoolean("EnableReactionsMessages", true);
                                        editor.putBoolean("EnableReactionsStories", true);
                                    } else {
                                        editor.putInt(getGlobalNotificationsKey(type), 0);
                                    }
                                    if (type == TYPE_CHANNEL) {
                                        editor.putInt("priority_channel", priority);
                                    } else if (type == TYPE_GROUP) {
                                        editor.putInt("priority_group", priority);
                                    } else if (type == TYPE_STORIES) {
                                        editor.putInt("priority_stories", priority);
                                    } else if (type == TYPE_REACTIONS_MESSAGES || type == TYPE_REACTIONS_STORIES) {
                                        editor.putInt("priority_react", priority);
                                    } else {
                                        editor.putInt("priority_messages", priority);
                                    }
                                } else {
                                    if (type == TYPE_STORIES) {
                                        editor.putBoolean("stories_" + dialogId, true);
                                    } else {
                                        editor.putInt("notify2_" + dialogId, 0);
                                        editor.remove("notifyuntil_" + dialogId);
                                        editor.putInt("priority_" + dialogId, priority);
                                    }
                                }
                            }
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
                                    } else if (type == TYPE_STORIES) {
                                        editor.putInt("vibrate_stories", vibrate ? 0 : 2);
                                    } else if (type == TYPE_REACTIONS_MESSAGES || type == TYPE_REACTIONS_STORIES) {
                                        editor.putInt("vibrate_react", vibrate ? 0 : 2);
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
                                    } else if (type == TYPE_STORIES) {
                                        editor.putInt("StoriesLed", channelLedColor);
                                    } else if (type == TYPE_REACTIONS_STORIES || type == TYPE_REACTIONS_MESSAGES) {
                                        editor.putInt("ReactionsLed", channelLedColor);
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
            NotificationChannel notificationChannel = new NotificationChannel(channelId, secretChat ? LocaleController.getString(R.string.SecretChatName) : name, importance);
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
                // todo: deal with vendor messed up crash here later
                notificationChannel.setSound(null, builder.build());
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("create new channel " + channelId);
            }
            lastNotificationChannelCreateTime = SystemClock.elapsedRealtime();
            systemNotificationManager.createNotificationChannel(notificationChannel);
            preferences.edit().putString(key, channelId).putString(key + "_s", newSettingsHash).commit();
        }
        return channelId;
    }

    private void showOrUpdateNotification(boolean notifyAboutLast) {
        if (!getUserConfig().isClientActivated() || pushMessages.isEmpty() && storyPushMessages.isEmpty() || !SharedConfig.showNotificationsForAllAccounts && currentAccount != UserConfig.selectedAccount) {
            dismissNotification();
            return;
        }
        try {
            getConnectionsManager().resumeNetworkMaybe();

            Object lastNotification = null;
            long maxDate = 0;
            for (int i = 0; i < pushMessages.size(); ++i) {
                MessageObject message = pushMessages.get(i);
                if (maxDate < message.messageOwner.date) {
                    lastNotification = message;
                    maxDate = message.messageOwner.date;
                }
            }
            for (int i = 0; i < storyPushMessages.size(); ++i) {
                StoryNotification n = storyPushMessages.get(i);
                if (maxDate < n.date / 1000L) {
                    lastNotification = n;
                    maxDate = n.date / 1000L;
                }
            }
            if (lastNotification == null) {
                return;
            }

            Bitmap largeBitmap = null;
            MessageObject lastMessageObject;
            if (lastNotification instanceof StoryNotification) {
                StoryNotification lastStoryNotification = (StoryNotification) lastNotification;
                TLRPC.TL_message msg = new TLRPC.TL_message();
                msg.date = (int) (System.currentTimeMillis() / 1000L);
                int storiesCount = 0;
                boolean hidden = false;
                for (int i = 0; i < storyPushMessages.size(); ++i) {
                    hidden |= storyPushMessages.get(i).hidden;
                    msg.date = Math.min(msg.date, (int) (storyPushMessages.get(i).date / 1000L));
                    storiesCount += storyPushMessages.get(i).dateByIds.size();
                }
                TLRPC.TL_peerUser peer = new TLRPC.TL_peerUser();
                msg.dialog_id = peer.user_id = lastStoryNotification.dialogId;
                msg.peer_id = peer;
                ArrayList<String> names = new ArrayList<>();
                ArrayList<Object> avatars = new ArrayList<>();
                parseStoryPushes(names, avatars);
                if (SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE) {
                    largeBitmap = loadMultipleAvatars(avatars);
                }
                String name;
                if (hidden || storyPushMessages.size() >= 2 || names.isEmpty()) {
                    name = LocaleController.formatPluralString("Stories", storiesCount);
                } else {
                    name = names.get(0);
                }
                if (hidden) {
                    msg.message = LocaleController.formatPluralString("StoryNotificationHidden", storiesCount);
                } else if (names.isEmpty()) {
                    msg.message = "";
                } else if (names.size() == 1) {
                    if (storiesCount == 1) {
                        msg.message = LocaleController.getString("StoryNotificationSingle");
                    } else {
                        msg.message = LocaleController.formatPluralString("StoryNotification1", storiesCount, names.get(0));
                    }
                } else if (names.size() == 2) {
                    msg.message = LocaleController.formatString(R.string.StoryNotification2, names.get(0), names.get(1));
                } else if (names.size() == 3 && storyPushMessages.size() == 3) {
                    msg.message = LocaleController.formatString(R.string.StoryNotification3, cutLastName(names.get(0)), cutLastName(names.get(1)), cutLastName(names.get(2)));
                } else {
                    msg.message = LocaleController.formatPluralString("StoryNotification4", storyPushMessages.size() - 2, cutLastName(names.get(0)), cutLastName(names.get(1)));
                }
                lastMessageObject = new MessageObject(currentAccount, msg, msg.message, name, name, false, false, false, false);
                lastMessageObject.isStoryPush = true;
            } else {
                lastMessageObject = pushMessages.get(0);
            }
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
            int dismissDate = preferences.getInt("dismissDate", 0);
            if (!lastMessageObject.isStoryPush && lastMessageObject.messageOwner.date <= dismissDate) {
                dismissNotification();
                return;
            }

            long dialog_id = lastMessageObject.getDialogId();
            long topicId = MessageObject.getTopicId(currentAccount, lastMessageObject.messageOwner, getMessagesController().isForum(lastMessageObject));
            boolean story = lastMessageObject.isStoryPush;

            boolean isChannel = false;
            long override_dialog_id = dialog_id;
            if (lastMessageObject.messageOwner.mentioned) {
                override_dialog_id = lastMessageObject.getFromChatId();
            }
            int mid = lastMessageObject.getId();
            long chatId = lastMessageObject.messageOwner.peer_id.chat_id != 0 ? lastMessageObject.messageOwner.peer_id.chat_id : lastMessageObject.messageOwner.peer_id.channel_id;
            long userId = lastMessageObject.messageOwner.peer_id.user_id;
            if (lastMessageObject.isFromUser() && (userId == 0 || userId == getUserConfig().getClientUserId())) {
                userId = lastMessageObject.messageOwner.from_id.user_id;
            }
            if (lastMessageObject != null && lastMessageObject.getDialogId() == UserObject.VERIFY && lastMessageObject.getForwardedFromId() != null) {
                if (lastMessageObject.getForwardedFromId() >= 0) {
                    userId = lastMessageObject.getForwardedFromId();
                    chatId = 0;
                } else {
                    userId = 0;
                    chatId = lastMessageObject.getForwardedFromId();
                }
            }

            TLRPC.User user = getMessagesController().getUser(userId);
            TLRPC.Chat chat = null;
            if (chatId != 0) {
                chat = getMessagesController().getChat(chatId);
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
            boolean isInternalSoundFile = false;
            int ledColor = 0xff0000ff;
            int importance = 0;

            int notifyOverride = getNotifyOverride(preferences, override_dialog_id, topicId);
            boolean value;
            if (notifyOverride == -1) {
                value = isGlobalNotificationsEnabled(dialog_id, isChannel, lastMessageObject.isReactionPush, lastMessageObject.isReactionPush);
            } else {
                value = notifyOverride != 2;
            }

            String name;
            String chatName;
            boolean replace = true;
            if (((chatId != 0 && chat == null) || user == null) && lastMessageObject.isFcmMessage()) {
                chatName = lastMessageObject.localName;
            } else if (chat != null) {
                chatName = chat.title;
            } else {
                chatName = UserObject.getUserName(user);
            }
            boolean passcode = AndroidUtilities.needShowPasscode() || SharedConfig.isWaitingForPasscodeEnter;
            final boolean allowSummary = !"samsung".equalsIgnoreCase(Build.MANUFACTURER);
            if (DialogObject.isEncryptedDialog(dialog_id) || allowSummary && pushDialogs.size() > 1 || passcode) {
                if (passcode) {
                    if (chatId != 0) {
                        name = LocaleController.getString(R.string.NotificationHiddenChatName);
                    } else {
                        name = LocaleController.getString(R.string.NotificationHiddenName);
                    }
                } else {
                    name = LocaleController.getString(R.string.AppName);
                }
                replace = false;
            } else {
                name = chatName;
            }
            if (lastMessageObject != null && (lastMessageObject.isReactionPush || lastMessageObject.isStoryReactionPush) && !preferences.getBoolean("EnableReactionsPreview", true)) {
                name = LocaleController.getString(R.string.NotificationHiddenName);
            }

            String detailText;
            if (allowSummary) {
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
                        detailText += LocaleController.formatString(R.string.NotificationMessagesPeopleDisplayOrder, LocaleController.formatPluralString("NewMessages", total_unread_count), LocaleController.formatPluralString("FromChats", pushDialogs.size()));
                    }
                }
            } else {
                detailText = "";
            }

            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(ApplicationLoader.applicationContext);

            int silent = 2;
            String lastMessage = null;
            boolean hasNewMessages = false;
            if (pushMessages.size() <= 1 || !allowSummary) {
                boolean[] text = new boolean[1];
                String message = lastMessage = getStringForMessage(lastMessageObject, false, text, null);
                silent = isSilentMessage(lastMessageObject) ? 1 : 0;
                if (message == null) {
                    return;
                }
                if (replace) {
                    if (chat != null && allowSummary) {
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
                if (!allowSummary) {
                    detailText = message;
                }
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
                    if (message == null || !messageObject.isStoryPush && messageObject.messageOwner.date <= dismissDate) {
                        continue;
                    }
                    if (silent == 2) {
                        lastMessage = message;
                        silent = isSilentMessage(messageObject) ? 1 : 0;
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

            if (!notifyDisabled && !preferences.getBoolean("sound_enabled_" + getSharedPrefKey(dialog_id, topicId), true)) {
                notifyDisabled = true;
            }

            String defaultPath = Settings.System.DEFAULT_NOTIFICATION_URI.getPath();

            boolean isDefault = true;
            boolean isInApp = !ApplicationLoader.mainInterfacePaused;
            int chatType = TYPE_PRIVATE;

            String customSoundPath;
            boolean customIsInternalSound = false;
            int customVibrate;
            int customImportance;
            Integer customLedColor;
            String key = getSharedPrefKey(dialog_id, topicId);
            if (dialogsNotificationsFacade.getProperty("custom_", dialog_id, topicId, false)) {
                customVibrate = dialogsNotificationsFacade.getProperty("vibrate_", dialog_id, topicId, 0);
                customImportance = dialogsNotificationsFacade.getProperty("priority_", dialog_id, topicId, 3);
                long soundDocumentId = dialogsNotificationsFacade.getProperty("sound_document_id_" , dialog_id, topicId, 0L);
                if (soundDocumentId != 0) {
                    customIsInternalSound = true;
                    customSoundPath = getMediaDataController().ringtoneDataStore.getSoundPath(soundDocumentId);
                } else {
                    customSoundPath = dialogsNotificationsFacade.getPropertyString("sound_path_" , dialog_id, topicId, null);
                }

                int color = dialogsNotificationsFacade.getProperty("color_", dialog_id, topicId, 0);
                if (color != 0) {
                    customLedColor = color;
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

            if (lastMessageObject != null && (lastMessageObject.isReactionPush || lastMessageObject.isStoryReactionPush)) {
                long soundDocumentId = preferences.getLong("ReactionSoundDocId", 0);
                if (soundDocumentId != 0) {
                    isInternalSoundFile = true;
                    soundPath = getMediaDataController().ringtoneDataStore.getSoundPath(soundDocumentId);
                } else {
                    soundPath = preferences.getString("ReactionSoundPath", defaultPath);
                }
                vibrate = preferences.getInt("vibrate_react", 0);
                importance = preferences.getInt("priority_react", 1);
                ledColor = preferences.getInt("ReactionsLed", 0xff0000ff);
                chatType = lastMessageObject.isStoryReactionPush ? TYPE_REACTIONS_STORIES : TYPE_REACTIONS_MESSAGES;
            } else if (chatId != 0) {
                if (isChannel) {
                    long soundDocumentId = preferences.getLong("ChannelSoundDocId", 0);
                    if (soundDocumentId != 0) {
                        isInternalSoundFile = true;
                        soundPath = getMediaDataController().ringtoneDataStore.getSoundPath(soundDocumentId);
                    } else {
                        soundPath = preferences.getString("ChannelSoundPath", defaultPath);
                    }
                    vibrate = preferences.getInt("vibrate_channel", 0);
                    importance = preferences.getInt("priority_channel", 1);
                    ledColor = preferences.getInt("ChannelLed", 0xff0000ff);
                    chatType = TYPE_CHANNEL;
                } else {
                    long soundDocumentId = preferences.getLong("GroupSoundDocId", 0);
                    if (soundDocumentId != 0) {
                        isInternalSoundFile = true;
                        soundPath = getMediaDataController().ringtoneDataStore.getSoundPath(soundDocumentId);
                    } else {
                        soundPath = preferences.getString("GroupSoundPath", defaultPath);
                    }
                    vibrate = preferences.getInt("vibrate_group", 0);
                    importance = preferences.getInt("priority_group", 1);
                    ledColor = preferences.getInt("GroupLed", 0xff0000ff);
                    chatType = TYPE_GROUP;
                }
            } else if (userId != 0) {
                long soundDocumentId = preferences.getLong(story ? "StoriesSoundDocId" : "GlobalSoundDocId", 0);
                if (soundDocumentId != 0) {
                    isInternalSoundFile = true;
                    soundPath = getMediaDataController().ringtoneDataStore.getSoundPath(soundDocumentId);
                } else {
                    soundPath = preferences.getString(story ? "StoriesSoundPath" : "GlobalSoundPath", defaultPath);
                }
                vibrate = preferences.getInt("vibrate_messages", 0);
                importance = preferences.getInt("priority_messages", 1);
                ledColor = preferences.getInt("MessagesLed", 0xff0000ff);
                chatType = story ? TYPE_STORIES : TYPE_PRIVATE;
            }
            if (vibrate == 4) {
                vibrateOnlyIfSilent = true;
                vibrate = 0;
            }
            if (!TextUtils.isEmpty(customSoundPath) && !TextUtils.equals(soundPath, customSoundPath)) {
                isInternalSoundFile = customIsInternalSound;
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
                if (preferences.getBoolean("EnableInAppPopup", true)) {
                    importance = 2;
                } else {
                    importance = 0;
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
            if (lastMessageObject.isStoryReactionPush) {
                intent.putExtra("storyId", Math.abs(lastMessageObject.getId()));
            } else if (lastMessageObject.isStoryPush) {
                long[] peerIds = new long[storyPushMessages.size()];
                for (int i = 0; i < storyPushMessages.size(); ++i) {
                    peerIds[i] = storyPushMessages.get(i).dialogId;
                }
                intent.putExtra("storyDialogIds", peerIds);
            } else if (!DialogObject.isEncryptedDialog(dialog_id)) {
                if (pushDialogs.size() == 1) {
                    if (chatId != 0) {
                        intent.putExtra("chatId", chatId);
                    } else if (userId != 0) {
                        intent.putExtra("userId", userId);
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
                    intent.putExtra("encId", DialogObject.getEncryptedChatId(dialog_id));
                }
            }
            intent.putExtra("currentAccount", currentAccount);
            PendingIntent contentIntent = PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);

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

            try {
                Intent dismissIntent = new Intent(ApplicationLoader.applicationContext, NotificationDismissReceiver.class);
                dismissIntent.putExtra("messageDate", lastMessageObject.messageOwner.date);
                dismissIntent.putExtra("currentAccount", currentAccount);
                if (lastMessageObject.isStoryPush) {
                    dismissIntent.putExtra("story", true);
                }
                if (lastMessageObject.isStoryReactionPush) {
                    dismissIntent.putExtra("storyReaction", true);
                }
                mBuilder.setDeleteIntent(PendingIntent.getBroadcast(ApplicationLoader.applicationContext, 1, dismissIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            } catch (Throwable e) {
                FileLog.e(e);
            }

            if (largeBitmap != null) {
                mBuilder.setLargeIcon(largeBitmap);
            } else if (photoPath != null) {
                BitmapDrawable img = ImageLoader.getInstance().getImageFromMemory(photoPath, null, "50_50");
                if (img != null) {
                    mBuilder.setLargeIcon(img.getBitmap());
                } else {
                    try {
                        File file = getFileLoader().getPathToAttach(photoPath, true);
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
            } else if (importance == 0) {
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

            if (silent != 1 && !notifyDisabled) {
                if (!isInApp || preferences.getBoolean("EnableInAppPreview", true) && lastMessage != null) {
                    if (lastMessage.length() > 100) {
                        lastMessage = lastMessage.substring(0, 100).replace('\n', ' ').trim() + "...";
                    }
                    mBuilder.setTicker(lastMessage);
                }
                if (soundPath != null && !soundPath.equalsIgnoreCase("NoSound")) {
                    if (Build.VERSION.SDK_INT >= 26) {
                        if (soundPath.equalsIgnoreCase("Default") || soundPath.equals(defaultPath)) {
                            sound = Settings.System.DEFAULT_NOTIFICATION_URI;
                        } else {
                            if (isInternalSoundFile) {
                                sound = FileProvider.getUriForFile(ApplicationLoader.applicationContext, ApplicationLoader.getApplicationId() + ".provider", new File(soundPath));
                                ApplicationLoader.applicationContext.grantUriPermission("com.android.systemui", sound, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } else {
                                sound = Uri.parse(soundPath);
                            }
                        }
                    } else {
                        if (soundPath.equals(defaultPath)) {
                            mBuilder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AudioManager.STREAM_NOTIFICATION);
                        } else {
                            if (Build.VERSION.SDK_INT >= 24 && soundPath.startsWith("file://") && !AndroidUtilities.isInternalUri(Uri.parse(soundPath))) {
                                try {
                                    Uri uri = FileProvider.getUriForFile(ApplicationLoader.applicationContext, ApplicationLoader.getApplicationId() + ".provider", new File(soundPath.replace("file://", "")));
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
                                mBuilder.addAction(0, button.text, PendingIntent.getBroadcast(ApplicationLoader.applicationContext, lastButtonId++, callbackIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
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
                    mBuilder.addAction(R.drawable.ic_ab_reply2, LocaleController.getString(R.string.Reply), PendingIntent.getBroadcast(ApplicationLoader.applicationContext, 2, replyIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
                } else {
                    mBuilder.addAction(R.drawable.ic_ab_reply, LocaleController.getString(R.string.Reply), PendingIntent.getBroadcast(ApplicationLoader.applicationContext, 2, replyIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
                }
            }
            showExtraNotifications(mBuilder, detailText, dialog_id, topicId, chatName, vibrationPattern, ledColor, sound, configImportance, isDefault, isInApp, notifyDisabled, chatType);
            scheduleNotificationRepeat();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private boolean isSilentMessage(MessageObject messageObject) {
        return messageObject.messageOwner.silent || messageObject.isReactionPush;
    }

    @SuppressLint("NewApi")
    private void setNotificationChannel(Notification mainNotification, NotificationCompat.Builder builder, boolean useSummaryNotification) {
        if (useSummaryNotification) {
            builder.setChannelId(OTHER_NOTIFICATIONS_CHANNEL);
        } else {
            builder.setChannelId(mainNotification.getChannelId());
        }
    }

    private void resetNotificationSound(NotificationCompat.Builder notificationBuilder, long dialogId, long topicId, String chatName, long[] vibrationPattern, int ledColor, Uri sound, int importance, boolean isDefault, boolean isInApp, boolean isSilent, int chatType) {
        FileLog.d("resetNotificationSound");
        Uri defaultSound = Settings.System.DEFAULT_RINGTONE_URI;
        if (defaultSound != null && sound != null && !TextUtils.equals(defaultSound.toString(), sound.toString())) {
            SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
            SharedPreferences.Editor editor = preferences.edit();

            String newSound = defaultSound.toString();
            String ringtoneName = LocaleController.getString(R.string.DefaultRingtone);
            if (isDefault) {
                if (chatType == TYPE_CHANNEL) {
                    editor.putString("ChannelSound", ringtoneName);
                } else if (chatType == TYPE_GROUP) {
                    editor.putString("GroupSound", ringtoneName);
                } else if (chatType == TYPE_PRIVATE) {
                    editor.putString("GlobalSound", ringtoneName);
                } else if (chatType == TYPE_STORIES) {
                    editor.putString("StoriesSound", ringtoneName);
                } else if (chatType == TYPE_REACTIONS_MESSAGES || chatType == TYPE_REACTIONS_STORIES) {
                    editor.putString("ReactionSound", ringtoneName);
                }
                if (chatType == TYPE_CHANNEL) {
                    editor.putString("ChannelSoundPath", newSound);
                } else if (chatType == TYPE_GROUP) {
                    editor.putString("GroupSoundPath", newSound);
                } else if (chatType == TYPE_PRIVATE) {
                    editor.putString("GlobalSoundPath", newSound);
                } else if (chatType == TYPE_STORIES) {
                    editor.putString("StoriesSoundPath", newSound);
                } else if (chatType == TYPE_REACTIONS_MESSAGES || chatType == TYPE_REACTIONS_STORIES) {
                    editor.putString("ReactionSound", newSound);
                }
                getNotificationsController().deleteNotificationChannelGlobalInternal(chatType, -1);
            } else {
                editor.putString("sound_" + NotificationsController.getSharedPrefKey(dialogId, topicId), ringtoneName);
                editor.putString("sound_path_" + NotificationsController.getSharedPrefKey(dialogId, topicId), newSound);
                deleteNotificationChannelInternal(dialogId, topicId, -1);
            }
            editor.commit();
            sound = Settings.System.DEFAULT_RINGTONE_URI;
            notificationBuilder.setChannelId(validateChannelId(dialogId, topicId, chatName, vibrationPattern, ledColor, sound, importance, isDefault, isInApp, isSilent, chatType));
            notificationManager.notify(notificationId, notificationBuilder.build());
        }
    }

    @SuppressLint("InlinedApi")
    private void showExtraNotifications(NotificationCompat.Builder notificationBuilder, String summary, long lastDialogId, long lastTopicId, String chatName, long[] vibrationPattern, int ledColor, Uri sound, int importance, boolean isDefault, boolean isInApp, boolean isSilent, int chatType) {
        FileLog.d("showExtraNotifications pushMessages.size()=" + pushMessages.size());
        if (Build.VERSION.SDK_INT >= 26) {
            notificationBuilder.setChannelId(validateChannelId(lastDialogId, lastTopicId, chatName, vibrationPattern, ledColor, sound, importance, isDefault, isInApp, isSilent, chatType));
        }
        Notification mainNotification = notificationBuilder.build();
        if (Build.VERSION.SDK_INT <= 19) {
            notificationManager.notify(notificationId, mainNotification);
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("show summary notification by SDK check");
            }
            return;
        }

        SharedPreferences preferences = getAccountInstance().getNotificationsSettings();

        ArrayList<DialogKey> sortedDialogs = new ArrayList<>();
        if (!storyPushMessages.isEmpty()) {
            sortedDialogs.add(new DialogKey(0, 0, true));
        }
        LongSparseArray<ArrayList<MessageObject>> messagesByDialogs = new LongSparseArray<>();
        for (int a = 0; a < pushMessages.size(); a++) {
            MessageObject messageObject = pushMessages.get(a);
            long dialog_id = messageObject.getDialogId();
            long topicId = MessageObject.getTopicId(currentAccount, messageObject.messageOwner, getMessagesController().isForum(messageObject));
            int dismissDate = preferences.getInt("dismissDate" + dialog_id, 0);
            if (!messageObject.isStoryPush && messageObject.messageOwner.date <= dismissDate) {
                FileLog.d("showExtraNotifications: dialog " + dialog_id + " is skipped, message date (" + messageObject.messageOwner.date + " <= " + dismissDate + ")");
                continue;
            }

            ArrayList<MessageObject> arrayList = messagesByDialogs.get(dialog_id);
            if (arrayList == null) {
                arrayList = new ArrayList<>();
                messagesByDialogs.put(dialog_id, arrayList);
                FileLog.d("showExtraNotifications: sortedDialogs += " + dialog_id);
                sortedDialogs.add(new DialogKey(dialog_id, topicId, false));
            }
            arrayList.add(messageObject);
        }

        LongSparseArray<Integer> oldIdsWear = new LongSparseArray<>();
        for (int i = 0; i < wearNotificationsIds.size(); i++) {
            oldIdsWear.put(wearNotificationsIds.keyAt(i), wearNotificationsIds.valueAt(i));
        }
        wearNotificationsIds.clear();

        class NotificationHolder {
            int id;
            long dialogId;
            long topicId;
            boolean story;
            String name;
            TLRPC.User user;
            TLRPC.Chat chat;
            NotificationCompat.Builder notification;

            NotificationHolder(int i, long li, boolean story, long topicId, String n, TLRPC.User u, TLRPC.Chat c, NotificationCompat.Builder builder) {
                id = i;
                name = n;
                user = u;
                chat = c;
                notification = builder;
                dialogId = li;
                this.story = story;
                this.topicId = topicId;
            }

            void call() {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.w("show dialog notification with id " + id + " " + dialogId +  " user=" + user + " chat=" + chat);
                }
                try {
                    notificationManager.notify(id, notification.build());
                } catch (SecurityException e) {
                    FileLog.e(e);
                    resetNotificationSound(notification, dialogId, lastTopicId, chatName, vibrationPattern, ledColor, sound, importance, isDefault, isInApp, isSilent, chatType);
                }
            }
        }

        ArrayList<NotificationHolder> holders = new ArrayList<>();

        boolean useSummaryNotification = Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1 || sortedDialogs.size() > (storyPushMessages.isEmpty() ? 1 : 2);
        if (useSummaryNotification && Build.VERSION.SDK_INT >= 26) {
            checkOtherNotificationsChannel();
        }

        long selfUserId = getUserConfig().getClientUserId();
        boolean waitingForPasscode = AndroidUtilities.needShowPasscode() || SharedConfig.isWaitingForPasscodeEnter;
        boolean passcode = SharedConfig.passcodeHash.length() > 0;
        FileLog.d("showExtraNotifications: passcode="+passcode+" waitingForPasscode=" + waitingForPasscode + " selfUserId=" + selfUserId + " useSummaryNotification=" + useSummaryNotification);

        int maxCount = 7;
        LongSparseArray<Person> personCache = new LongSparseArray<>();
        for (int b = 0, size = sortedDialogs.size(); b < size; b++) {
            if (holders.size() >= maxCount) {
                FileLog.d("showExtraNotifications: break from holders, count over " + maxCount);
                break;
            }
            final DialogKey dialogKey = sortedDialogs.get(b);
            final long dialogId;
            final long topicId;
            int maxId;
            MessageObject lastMessageObject = null;
            final ArrayList<MessageObject> messageObjects;
            if (dialogKey.story) {
                messageObjects = new ArrayList<>();
                if (storyPushMessages.isEmpty()) {
                    FileLog.d("showExtraNotifications: ["+dialogKey.dialogId+"] continue; story but storyPushMessages is empty");
                    continue;
                }
                dialogId = storyPushMessages.get(0).dialogId;
                topicId = 0;
                maxId = 0;
                for (int id : storyPushMessages.get(0).dateByIds.keySet()) {
                    maxId = Math.max(maxId, id);
                }
            } else {
                dialogId = dialogKey.dialogId;
                topicId = dialogKey.topicId;
                messageObjects = messagesByDialogs.get(dialogKey.dialogId);
                maxId = messageObjects.get(0).getId();
                lastMessageObject = messageObjects.get(0);
            }

            Integer internalId = oldIdsWear.get(dialogKey.dialogId);
            if (dialogKey.story) {
                internalId = Integer.MAX_VALUE - 1;
            } else if (internalId == null) {
                internalId = (int) dialogKey.dialogId + (int) (dialogKey.dialogId >> 32);
            } else {
                oldIdsWear.remove(dialogKey.dialogId);
            }

            int maxDate = 0;
            for (int i = 0; i < messageObjects.size(); i++) {
                if (maxDate < messageObjects.get(i).messageOwner.date) {
                    maxDate = messageObjects.get(i).messageOwner.date;
                }
            }
            TLRPC.Chat chat = null;
            TLRPC.User user = null;
            boolean isChannel = false;
            boolean isSupergroup = false;
            String name;
            TLRPC.FileLocation photoPath = null;
            Bitmap avatarBitmap = null;
            File avatarFile = null;
            boolean canReply;

            if (dialogKey.story) {
                canReply = false;
                user = getMessagesController().getUser(dialogId);
                if (storyPushMessages.size() == 1) {
                    if (user != null) {
                        name = UserObject.getFirstName(user);
                    } else {
                        name = storyPushMessages.get(0).localName;
                    }
                } else {
                    name = LocaleController.formatPluralString("Stories", storyPushMessages.size());
                }
                if (user != null && user.photo != null && user.photo.photo_small != null && user.photo.photo_small.volume_id != 0 && user.photo.photo_small.local_id != 0) {
                    photoPath = user.photo.photo_small;
                }
            } else if (!DialogObject.isEncryptedDialog(dialogId)) {
                canReply = (lastMessageObject != null && !lastMessageObject.isReactionPush && !lastMessageObject.isStoryReactionPush) && dialogId != 777000;
                if (DialogObject.isUserDialog(dialogId)) {
                    user = getMessagesController().getUser(dialogId);
                    if (user == null) {
                        if (lastMessageObject.isFcmMessage()) {
                            name = lastMessageObject.localName;
                        } else {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.w("not found user to show dialog notification " + dialogId);
                            }
                            continue;
                        }
                    } else {
                        name = UserObject.getUserName(user);
                        if (user.photo != null && user.photo.photo_small != null && user.photo.photo_small.volume_id != 0 && user.photo.photo_small.local_id != 0) {
                            photoPath = user.photo.photo_small;
                        }
                    }
                    if (dialogId == UserObject.VERIFY) {
                        name = LocaleController.getString(R.string.VerifyCodesNotifications);
                    } else if (UserObject.isReplyUser(dialogId)) {
                        name = LocaleController.getString(R.string.RepliesTitle);
                    } else if (dialogId == selfUserId) {
                        name = LocaleController.getString(R.string.MessageScheduledReminderNotification);
                    }
                } else {
                    chat = getMessagesController().getChat(-dialogId);
                    if (chat == null) {
                        canReply = false;
                        if (lastMessageObject.isFcmMessage()) {
                            isSupergroup = lastMessageObject.isSupergroup();
                            name = lastMessageObject.localName;
                            isChannel = lastMessageObject.localChannel;
                        } else {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.w("not found chat to show dialog notification " + dialogId);
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

                        if (topicId != 0) {
                            TLRPC.TL_forumTopic topic = getMessagesController().getTopicsController().findTopic(chat.id, topicId);
                            if (topic != null) {
                                name = topic.title + " in " + name;
                            }
                        }
                        if (canReply) {
                            canReply = ChatObject.canSendPlain(chat);
                        }
                    }
                }
                if (dialogId == UserObject.VERIFY && lastMessageObject != null && lastMessageObject.getForwardedFromId() != null) {
                    long did = lastMessageObject.getForwardedFromId();
                    if (DialogObject.isUserDialog(did)) {
                        TLRPC.User fwduser = getMessagesController().getUser(did);
                        if (fwduser.photo != null && fwduser.photo.photo_small != null && fwduser.photo.photo_small.volume_id != 0 && fwduser.photo.photo_small.local_id != 0) {
                            photoPath = fwduser.photo.photo_small;
                        }
                    } else {
                        TLRPC.Chat fwdchat = getMessagesController().getChat(-did);
                        if (fwdchat.photo != null && fwdchat.photo.photo_small != null && fwdchat.photo.photo_small.volume_id != 0 && fwdchat.photo.photo_small.local_id != 0) {
                            photoPath = fwdchat.photo.photo_small;
                        }
                    }
                }
                if (dialogId == UserObject.VERIFY) {
                    canReply = false;
                }
            } else {
                canReply = false;
                if (dialogId != globalSecretChatId) {
                    int encryptedChatId = DialogObject.getEncryptedChatId(dialogId);
                    TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(encryptedChatId);
                    if (encryptedChat == null) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.w("not found secret chat to show dialog notification " + encryptedChatId);
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
                name = LocaleController.getString(R.string.SecretChatName);
                photoPath = null;
            }
            if (lastMessageObject != null && lastMessageObject.isStoryReactionPush && !preferences.getBoolean("EnableReactionsPreview", true)) {
                canReply = false;
                name = LocaleController.getString(R.string.NotificationHiddenChatName);
                photoPath = null;
            }

            if (waitingForPasscode) {
                if (DialogObject.isChatDialog(dialogId)) {
                    name = LocaleController.getString(R.string.NotificationHiddenChatName);
                } else {
                    name = LocaleController.getString(R.string.NotificationHiddenName);
                }
                photoPath = null;
                canReply = false;
            }

            if (photoPath != null) {
                avatarFile = getFileLoader().getPathToAttach(photoPath, true);
                if (Build.VERSION.SDK_INT < 28) {
                    BitmapDrawable img = ImageLoader.getInstance().getImageFromMemory(photoPath, null, "50_50");
                    if (img != null) {
                        avatarBitmap = img.getBitmap();
                    } else {
                        try {
                            if (avatarFile.exists()) {
                                float scaleFactor = 160.0f / AndroidUtilities.dp(50);
                                BitmapFactory.Options options = new BitmapFactory.Options();
                                options.inSampleSize = scaleFactor < 1 ? 1 : (int) scaleFactor;
                                avatarBitmap = BitmapFactory.decodeFile(avatarFile.getAbsolutePath(), options);
                            }
                        } catch (Throwable ignore) {

                        }
                    }
                }
            }

            if (chat != null) {
                Person.Builder personBuilder = new Person.Builder().setName(name);
                if (avatarFile != null && avatarFile.exists() && Build.VERSION.SDK_INT >= 28) {
                    loadRoundAvatar(avatarFile, personBuilder);
                }
                personCache.put(-chat.id, personBuilder.build());
            }

            NotificationCompat.Action wearReplyAction = null;

            if ((!isChannel || isSupergroup) && canReply && !SharedConfig.isWaitingForPasscodeEnter && selfUserId != dialogId && !UserObject.isReplyUser(dialogId)) {
                Intent replyIntent = new Intent(ApplicationLoader.applicationContext, WearReplyReceiver.class);
                replyIntent.putExtra("dialog_id", dialogId);
                replyIntent.putExtra("max_id", maxId);
                replyIntent.putExtra("topic_id", topicId);
                replyIntent.putExtra("currentAccount", currentAccount);
                PendingIntent replyPendingIntent = PendingIntent.getBroadcast(ApplicationLoader.applicationContext, internalId, replyIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                RemoteInput remoteInputWear = new RemoteInput.Builder(EXTRA_VOICE_REPLY).setLabel(LocaleController.getString(R.string.Reply)).build();
                String replyToString;
                if (DialogObject.isChatDialog(dialogId)) {
                    replyToString = LocaleController.formatString(R.string.ReplyToGroup, name);
                } else {
                    replyToString = LocaleController.formatString(R.string.ReplyToUser, name);
                }
                wearReplyAction = new NotificationCompat.Action.Builder(R.drawable.ic_reply_icon, replyToString, replyPendingIntent)
                        .setAllowGeneratedReplies(true)
                        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                        .addRemoteInput(remoteInputWear)
                        .setShowsUserInterface(false)
                        .build();
            }

            Integer count = pushDialogs.get(dialogId);
            if (count == null) {
                count = 0;
            }
            int n;
            if (dialogKey.story) {
                n = storyPushMessages.size();
            } else {
                n = Math.max(count, messageObjects.size());
            }
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
                        Person.Builder personBuilder = new Person.Builder().setName(LocaleController.getString(R.string.FromYou));
                        File avatar = getFileLoader().getPathToAttach(sender.photo.photo_small, true);
                        loadRoundAvatar(avatar, personBuilder);
                        selfPerson = personBuilder.build();
                        personCache.put(selfUserId, selfPerson);
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }

            boolean needAddPerson = lastMessageObject == null || !(lastMessageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatJoinedByRequest);
            NotificationCompat.MessagingStyle messagingStyle;
            if (selfPerson != null && needAddPerson) {
                messagingStyle = new NotificationCompat.MessagingStyle(selfPerson);
            } else {
                messagingStyle = new NotificationCompat.MessagingStyle("");
            }
            if (Build.VERSION.SDK_INT < 28 || DialogObject.isChatDialog(dialogId) && !isChannel || UserObject.isReplyUser(dialogId)) {
                messagingStyle.setConversationTitle(conversationName);
            }
            messagingStyle.setGroupConversation(Build.VERSION.SDK_INT < 28 || !isChannel && DialogObject.isChatDialog(dialogId) || UserObject.isReplyUser(dialogId));

            StringBuilder text = new StringBuilder();
            String[] senderName = new String[1];
            boolean[] preview = new boolean[1];
            ArrayList<TLRPC.TL_keyboardButtonRow> rows = null;
            int rowsMid = 0;
            if (dialogKey.story) {
                ArrayList<String> names = new ArrayList<>();
                ArrayList<Object> avatars = new ArrayList<>();
                Pair<Integer, Boolean> pair = parseStoryPushes(names, avatars);
                int storiesCount = pair.first;
                boolean hidden = pair.second;
                if (hidden) {
                    text.append(LocaleController.formatPluralString("StoryNotificationHidden", storiesCount));
                } else if (names.isEmpty()) {
                    FileLog.d("showExtraNotifications: ["+dialogId+"] continue; story but names is empty");
                    continue;
                } else if (names.size() == 1) {
                    if (storiesCount == 1) {
                        text.append(LocaleController.getString("StoryNotificationSingle"));
                    } else {
                        text.append(LocaleController.formatPluralString("StoryNotification1", storiesCount, names.get(0)));
                    }
                } else if (names.size() == 2) {
                    text.append(LocaleController.formatString(R.string.StoryNotification2, names.get(0), names.get(1)));
                } else if (names.size() == 3 && storyPushMessages.size() == 3) {
                    text.append(LocaleController.formatString(R.string.StoryNotification3, cutLastName(names.get(0)), cutLastName(names.get(1)), cutLastName(names.get(2))));
                } else {
                    text.append(LocaleController.formatPluralString("StoryNotification4", storyPushMessages.size() - 2, cutLastName(names.get(0)), cutLastName(names.get(1))));
                }
                long date = Long.MAX_VALUE;
                for (int i = 0; i < storyPushMessages.size(); ++i) {
                    date = Math.min(storyPushMessages.get(i).date, date);
                }
                messagingStyle.setGroupConversation(false);
                final String title = name = names.size() == 1 && !hidden ? names.get(0) : LocaleController.formatPluralString("Stories", storiesCount);
                messagingStyle.addMessage(text, date, new Person.Builder().setName(title).build());
                if (!hidden) {
                    avatarBitmap = loadMultipleAvatars(avatars);
                } else {
                    avatarBitmap = null;
                }
            } else {
                for (int a = messageObjects.size() - 1; a >= 0; a--) {
                    final MessageObject messageObject = messageObjects.get(a);
                    final boolean isForum = getMessagesController().isForum(messageObject);
                    final long messageTopicId = MessageObject.getTopicId(currentAccount, messageObject.messageOwner, isForum);
                    if (topicId != messageTopicId) {
                        FileLog.d("showExtraNotifications: ["+dialogId+"] continue; topic id is not equal: topicId=" + topicId + " messageTopicId=" + messageTopicId + "; selfId=" + getUserConfig().getClientUserId());
                        continue;
                    }
                    String message = getShortStringForMessage(messageObject, senderName, preview);
                    if (dialogId == UserObject.VERIFY && messageObject.getForwardedFromId() != null) {
                        senderName[0] = getMessagesController().getPeerName(messageObject.getForwardedFromId());
                    } else if (dialogId == selfUserId) {
                        senderName[0] = name;
                    } else if (DialogObject.isChatDialog(dialogId) && messageObject.messageOwner.from_scheduled) {
                        senderName[0] = LocaleController.getString(R.string.NotificationMessageScheduledName);
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
                    if (dialogId != selfUserId && messageObject.messageOwner.from_scheduled && DialogObject.isUserDialog(dialogId)) {
                        message = String.format("%1$s: %2$s", LocaleController.getString(R.string.NotificationMessageScheduledName), message);
                        text.append(message);
                    } else {
                        if (senderName[0] != null) {
                            text.append(String.format("%1$s: %2$s", senderName[0], message));
                        } else {
                            text.append(message);
                        }
                    }

                    long uid;
                    if (dialogId == UserObject.VERIFY && messageObject.getForwardedFromId() != null) {
                        uid = messageObject.getForwardedFromId();
                    } else if (DialogObject.isUserDialog(dialogId)) {
                        uid = dialogId;
                    } else if (isChannel) {
                        uid = -dialogId;
                    } else if (DialogObject.isChatDialog(dialogId)) {
                        uid = messageObject.getSenderId();
                    } else {
                        uid = dialogId;
                    }
                    Person person = personCache.get(uid + ((long) topicId << 16));
                    CharSequence personName = "";
                    if (senderName[0] == null) {
                        if (waitingForPasscode) {
                            if (DialogObject.isChatDialog(dialogId)) {
                                if (isChannel) {
                                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
                                        personName = LocaleController.getString(R.string.NotificationHiddenChatName);
                                    }
                                } else {
                                    personName = LocaleController.getString(R.string.NotificationHiddenChatUserName);
                                }
                            } else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
                                personName = LocaleController.getString(R.string.NotificationHiddenName);
                            }
                        }
                    } else {
                        personName = senderName[0];
                    }

                    if (person == null || !TextUtils.equals(person.getName(), personName)) {
                        Person.Builder personBuilder = new Person.Builder().setName(personName);
                        if (preview[0] && !DialogObject.isEncryptedDialog(dialogId) && Build.VERSION.SDK_INT >= 28) {
                            File avatar = null;
                            if (DialogObject.isUserDialog(dialogId) || isChannel) {
                                avatar = avatarFile;
                            } else {
                                long fromId = messageObject.getSenderId();
                                TLRPC.User sender = getMessagesController().getUser(fromId);
                                if (sender == null) {
                                    sender = getMessagesStorage().getUserSync(fromId);
                                    if (sender != null) {
                                        getMessagesController().putUser(sender, true);
                                    }
                                }
                                if (sender != null && sender.photo != null && sender.photo.photo_small != null && sender.photo.photo_small.volume_id != 0 && sender.photo.photo_small.local_id != 0) {
                                    avatar = getFileLoader().getPathToAttach(sender.photo.photo_small, true);
                                }
                            }
                            if (avatar == null && dialogId == UserObject.VERIFY && messageObject.getForwardedFromId() != null) {
                                if (uid >= 0) {
                                    TLRPC.User sender = getMessagesController().getUser(uid);
                                    if (sender != null && sender.photo != null && sender.photo.photo_small != null && sender.photo.photo_small.volume_id != 0 && sender.photo.photo_small.local_id != 0) {
                                        avatar = getFileLoader().getPathToAttach(sender.photo.photo_small, true);
                                    }
                                } else {
                                    TLRPC.Chat sender = getMessagesController().getChat(-uid);
                                    if (sender != null && sender.photo != null && sender.photo.photo_small != null && sender.photo.photo_small.volume_id != 0 && sender.photo.photo_small.local_id != 0) {
                                        avatar = getFileLoader().getPathToAttach(sender.photo.photo_small, true);
                                    }
                                }
                            }
                            loadRoundAvatar(avatar, personBuilder);
                        }
                        person = personBuilder.build();
                        personCache.put(uid, person);
                    }


                    if (!DialogObject.isEncryptedDialog(dialogId)) {
                        boolean setPhoto = false;
                        if (preview[0] && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !((ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)).isLowRamDevice()) {
                            if (!waitingForPasscode && !messageObject.isSecretMedia() && (messageObject.type == MessageObject.TYPE_PHOTO || messageObject.isSticker())) {
                                File attach = getFileLoader().getPathToMessage(messageObject.messageOwner);
                                File blurredAttach;
                                if (attach.exists() && messageObject.hasMediaSpoilers()) {
                                    blurredAttach = new File(attach.getParentFile(), attach.getName() + ".blur.jpg");
                                    if (!blurredAttach.exists()) {
                                        try {
                                            Bitmap bitmap = BitmapFactory.decodeFile(attach.getAbsolutePath());

                                            Bitmap blurBitmap = Utilities.stackBlurBitmapMax(bitmap);
                                            bitmap.recycle();

                                            Bitmap scaledBitmap = Bitmap.createScaledBitmap(blurBitmap, bitmap.getWidth(), bitmap.getHeight(), true);
                                            Utilities.stackBlurBitmap(scaledBitmap, 5);
                                            blurBitmap.recycle();

                                            Canvas canvas = new Canvas(scaledBitmap);
                                            int sColor = Color.WHITE;
                                            mediaSpoilerEffect.setColor(ColorUtils.setAlphaComponent(sColor, (int) (Color.alpha(sColor) * 0.325f)));
                                            mediaSpoilerEffect.setBounds(0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight());
                                            mediaSpoilerEffect.draw(canvas);

                                            FileOutputStream fos = new FileOutputStream(blurredAttach);
                                            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                                            fos.close();

                                            scaledBitmap.recycle();

                                            attach = blurredAttach;
                                        } catch (Exception e) {
                                            FileLog.e(e);
                                        }
                                    }
                                } else {
                                    blurredAttach = null;
                                }
                                NotificationCompat.MessagingStyle.Message msg = new NotificationCompat.MessagingStyle.Message(message, ((long) messageObject.messageOwner.date) * 1000L, person);
                                String mimeType = messageObject.isSticker() ? "image/webp" : "image/jpeg";
                                Uri uri;
                                if (attach.exists()) {
                                    try {
                                        uri = FileProvider.getUriForFile(ApplicationLoader.applicationContext, ApplicationLoader.getApplicationId() + ".provider", attach);
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                        uri = null;
                                    }
                                } else if (getFileLoader().isLoadingFile(attach.getName())) {
                                    Uri.Builder _uri = new Uri.Builder()
                                            .scheme("content")
                                            .authority(NotificationImageProvider.getAuthority())
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
                                    Uri uriFinal = uri;
                                    ApplicationLoader.applicationContext.grantUriPermission("com.android.systemui", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    AndroidUtilities.runOnUIThread(() -> {
                                        try {
                                            ApplicationLoader.applicationContext.revokeUriPermission(uriFinal, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                        } catch (Exception e) {
                                            FileLog.e(e);
                                        }
                                        try {
                                            if (blurredAttach != null) {
                                                blurredAttach.delete();
                                            }
                                        } catch (Exception e) {
                                            FileLog.e(e);
                                        }
                                    }, 20_000);

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
                                File f = getFileLoader().getPathToMessage(messageObject.messageOwner);
                                Uri uri;
                                if (Build.VERSION.SDK_INT >= 24) {
                                    try {
                                        uri = FileProvider.getUriForFile(ApplicationLoader.applicationContext, ApplicationLoader.getApplicationId() + ".provider", f);
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

                    if (dialogId == 777000 && messageObject.messageOwner.reply_markup != null) {
                        rows = messageObject.messageOwner.reply_markup.rows;
                        rowsMid = messageObject.getId();
                    }
                }
            }

            Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
            intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            if (lastMessageObject != null && lastMessageObject.isStoryReactionPush) {
                intent.putExtra("storyId", Math.abs(lastMessageObject.getId()));
            } else if (dialogKey.story) {
                long[] peerIds = new long[storyPushMessages.size()];
                for (int i = 0; i < storyPushMessages.size(); ++i) {
                    peerIds[i] = storyPushMessages.get(i).dialogId;
                }
                intent.putExtra("storyDialogIds", peerIds);
            } else if (DialogObject.isEncryptedDialog(dialogId)) {
                intent.putExtra("encId", DialogObject.getEncryptedChatId(dialogId));
            } else if (DialogObject.isUserDialog(dialogId)) {
                intent.putExtra("userId", dialogId);
            } else {
                intent.putExtra("chatId", -dialogId);
            }
            FileLog.d("show extra notifications chatId " + dialogId + " topicId " + topicId);
            if (topicId != 0) {
                intent.putExtra("topicId", topicId);
            }
            intent.putExtra("currentAccount", currentAccount);
            PendingIntent contentIntent = PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);

            NotificationCompat.WearableExtender wearableExtender = new NotificationCompat.WearableExtender();
            if (wearReplyAction != null) {
                wearableExtender.addAction(wearReplyAction);
            }
            Intent msgHeardIntent = new Intent(ApplicationLoader.applicationContext, AutoMessageHeardReceiver.class);
            msgHeardIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            msgHeardIntent.setAction("org.telegram.messenger.ACTION_MESSAGE_HEARD");
            msgHeardIntent.putExtra("dialog_id", dialogId);
            msgHeardIntent.putExtra("max_id", maxId);
            msgHeardIntent.putExtra("currentAccount", currentAccount);
            PendingIntent readPendingIntent = PendingIntent.getBroadcast(ApplicationLoader.applicationContext, internalId, msgHeardIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationCompat.Action readAction = new NotificationCompat.Action.Builder(R.drawable.msg_markread, LocaleController.getString(R.string.MarkAsRead), readPendingIntent)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                    .setShowsUserInterface(false)
                    .build();

            String dismissalID;
            if (!DialogObject.isEncryptedDialog(dialogId)) {
                if (DialogObject.isUserDialog(dialogId)) {
                    dismissalID = "tguser" + dialogId + "_" + maxId;
                } else {
                    dismissalID = "tgchat" + (-dialogId) + "_" + maxId;
                }
            } else if (dialogId != globalSecretChatId) {
                dismissalID = "tgenc" + DialogObject.getEncryptedChatId(dialogId) + "_" + maxId;
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

            long date;
            if (dialogKey.story) {
                date = Long.MAX_VALUE;
                for (int i = 0; i < storyPushMessages.size(); ++i) {
                    date = Math.min(storyPushMessages.get(i).date, date);
                }
            } else {
                date = ((long) messageObjects.get(0).messageOwner.date) * 1000;
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext)
                    .setContentTitle(name)
                    .setSmallIcon(R.drawable.notification)
                    .setContentText(text.toString())
                    .setAutoCancel(true)
                    .setNumber(dialogKey.story ? storyPushMessages.size() : messageObjects.size())
                    .setColor(0xff11acfa)
                    .setGroupSummary(false)
                    .setWhen(date)
                    .setShowWhen(true)
                    .setStyle(messagingStyle)
                    .setContentIntent(contentIntent)
                    .extend(wearableExtender)
                    .setSortKey(String.valueOf(Long.MAX_VALUE - date))
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE);

            try {
                Intent dismissIntent = new Intent(ApplicationLoader.applicationContext, NotificationDismissReceiver.class);
                dismissIntent.putExtra("messageDate", maxDate);
                dismissIntent.putExtra("dialogId", dialogId);
                dismissIntent.putExtra("currentAccount", currentAccount);
                if (dialogKey.story) {
                    dismissIntent.putExtra("story", true);
                }
                if (lastMessageObject != null && lastMessageObject.isStoryReactionPush) {
                    dismissIntent.putExtra("storyReaction", true);
                }
                builder.setDeleteIntent(PendingIntent.getBroadcast(ApplicationLoader.applicationContext, internalId, dismissIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            } catch (Exception e) {
                FileLog.e(e);
            }

            if (useSummaryNotification) {
                builder.setGroup(notificationGroup);
                builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);
            }

            TLRPC.TL_keyboardButtonCopy copybutton = null;
            if (lastMessageObject != null && lastMessageObject.messageOwner != null && lastMessageObject.messageOwner.reply_markup != null) {
                TLRPC.ReplyMarkup reply_markup = lastMessageObject.messageOwner.reply_markup;
                for (int i = 0; i < reply_markup.rows.size(); ++i) {
                    for (int j = 0; j < reply_markup.rows.get(i).buttons.size(); ++j) {
                        if (reply_markup.rows.get(i).buttons.get(j) instanceof TLRPC.TL_keyboardButtonCopy) {
                            copybutton = (TLRPC.TL_keyboardButtonCopy) reply_markup.rows.get(i).buttons.get(j);
                            break;
                        }
                    }
                    if (copybutton != null) break;
                }
            }
            if (copybutton != null) {
                Intent copyIntent = new Intent(ApplicationLoader.applicationContext, CopyCodeReceiver.class);
                copyIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                copyIntent.setAction("org.telegram.messenger.ACTION_COPY_CODE");
                copyIntent.putExtra("text", copybutton.copy_text);
                PendingIntent copyPendingIntent = PendingIntent.getBroadcast(ApplicationLoader.applicationContext, internalId, copyIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                NotificationCompat.Action copyAction = new NotificationCompat.Action.Builder(R.drawable.msg_copy, copybutton.text, copyPendingIntent)
                        .setShowsUserInterface(false)
                        .build();
                builder.addAction(copyAction);
            }
            if (dialogKey.dialogId != UserObject.VERIFY) {
                if (wearReplyAction != null) {
                    builder.addAction(wearReplyAction);
                }
                if (!waitingForPasscode && !dialogKey.story && (lastMessageObject == null || !lastMessageObject.isStoryReactionPush)) {
                    builder.addAction(readAction);
                }
            }
            if (sortedDialogs.size() == 1 && !TextUtils.isEmpty(summary) && !dialogKey.story) {
                builder.setSubText(summary);
            }
            if (DialogObject.isEncryptedDialog(dialogId)) {
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
                                callbackIntent.putExtra("did", dialogId);
                                if (button.data != null) {
                                    callbackIntent.putExtra("data", button.data);
                                }
                                callbackIntent.putExtra("mid", rowsMid);
                                builder.addAction(0, button.text, PendingIntent.getBroadcast(ApplicationLoader.applicationContext, lastButtonId++, callbackIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
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
            FileLog.d("showExtraNotifications: holders.add " + dialogId);
            holders.add(new NotificationHolder(internalId, dialogId, dialogKey.story, topicId, name, user, chat, builder));
            wearNotificationsIds.put(dialogId, internalId);
        }

        if (useSummaryNotification) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("show summary with id " + notificationId);
            }
            try {
                notificationManager.notify(notificationId, mainNotification);
            } catch (SecurityException e) {
                FileLog.e(e);
                resetNotificationSound(notificationBuilder, lastDialogId, lastTopicId, chatName, vibrationPattern, ledColor, sound, importance, isDefault, isInApp, isSilent, chatType);
            }
        } else {
            if (openedInBubbleDialogs.isEmpty()) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("cancel summary with id " + notificationId);
                }
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
        FileLog.d("showExtraNotifications: holders.size()=" + holders.size());
        for (int a = 0, size = holders.size(); a < size; a++) {
            NotificationHolder holder = holders.get(a);
            ids.clear();
            if (Build.VERSION.SDK_INT >= 29 && !DialogObject.isEncryptedDialog(holder.dialogId)) {
                String shortcutId = createNotificationShortcut(holder.notification, holder.dialogId, holder.name, holder.user, holder.chat, personCache.get(holder.dialogId), !holder.story);
                if (shortcutId != null) {
                    ids.add(shortcutId);
                }
            }
            FileLog.d("showExtraNotifications: holders["+a+"].call()");
            holder.call();
            if (!unsupportedNotificationShortcut() && !ids.isEmpty()) {
                ShortcutManagerCompat.removeDynamicShortcuts(ApplicationLoader.applicationContext, ids);
            }
        }
    }

    private String cutLastName(String name) {
        if (name == null) {
            return null;
        }
        int index;
        if ((index = name.indexOf(' ')) >= 0) {
            return name.substring(0, index) + (name.endsWith("…") ? "…" : "");
        }
        return name;
    }

    private Pair<Integer, Boolean> parseStoryPushes(ArrayList<String> names, ArrayList<Object> avatars) {
        int storiesCount = 0;
        boolean hidden = false;
        final int count = Math.min(3, storyPushMessages.size());
        for (int i = 0; i < count; ++i) {
            StoryNotification notification = storyPushMessages.get(i);
            storiesCount += notification.dateByIds.size();
            hidden |= notification.hidden;
            TLRPC.User user1 = getMessagesController().getUser(notification.dialogId);
            if (user1 == null) {
                user1 = getMessagesStorage().getUserSync(notification.dialogId);
                if (user1 != null) {
                    getMessagesController().putUser(user1, true);
                }
            }
            String username;
            File avatar = null;
            if (user1 != null) {
                username = UserObject.getUserName(user1);
                if (user1 != null && user1.photo != null && user1.photo.photo_small != null && user1.photo.photo_small.volume_id != 0 && user1.photo.photo_small.local_id != 0) {
                    File file = getFileLoader().getPathToAttach(user1.photo.photo_small, true);
                    if (!file.exists()) {
                        file = null;
                        if (user1.photo.photo_big != null) {
                            file = getFileLoader().getPathToAttach(user1.photo.photo_big, true);
                        }
                        if (file != null && !file.exists()) {
                            file = null;
                        }
                    }
                    if (file != null) {
                        avatar = file;
                    }
                }
            } else if (notification.localName != null) {
                username = notification.localName;
            } else {
                continue;
            }
            if (username.length() > 50) {
                username = username.substring(0, 25) + "…";
            }
            names.add(username);
            if (avatar == null && user1 != null) {
                avatars.add(user1);
            } else if (avatar != null) {
                avatars.add(avatar);
            }
        }
        if (hidden) {
            avatars.clear();
        }
        return new Pair<>(storiesCount, hidden);
    }

    public static Person.Builder loadRoundAvatar(File avatar, Person.Builder personBuilder) {
        if (avatar != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                Bitmap bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(avatar), (decoder, info, src) -> {
                    decoder.setPostProcessor((canvas) -> {
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
                    });
                });
                IconCompat icon = IconCompat.createWithBitmap(bitmap);
                personBuilder.setIcon(icon);
            } catch (Throwable ignore) {

            }
        }
        return personBuilder;
    }

    public static Bitmap loadMultipleAvatars(ArrayList<Object> avatars) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || avatars == null || avatars.size() == 0) {
            return null;
        }
        final int sz = AndroidUtilities.dp(64);
        // TODO: cache that bitmap
        final Bitmap finalBitmap = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(finalBitmap);
        final Matrix matrix = new Matrix();
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Rect rect = new Rect();
        TextPaint textPaint = null;
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        final float s = avatars.size() == 1 ? 1f : avatars.size() == 2 ? .65f : .5f;
        for (int i = 0; i < avatars.size(); ++i) {
            try {
                final float x = sz * (1 - s) / avatars.size() * (avatars.size() - 1 - i);
                final float y = sz * (1 - s) / avatars.size() * i;

                canvas.drawCircle(x + sz * s / 2, y + sz * s / 2, sz * s / 2 + AndroidUtilities.dp(2), clearPaint);

                Object obj = avatars.get(i);

                if (obj instanceof File) {
                    final String path = ((File) avatars.get(i)).getAbsolutePath();
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(path, opts);
                    opts.inSampleSize = StoryEntry.calculateInSampleSize(opts, (int) (sz * s), (int) (sz * s));
                    opts.inJustDecodeBounds = false;
                    opts.inDither = true;
                    Bitmap avatarBitmap = BitmapFactory.decodeFile(path, opts);

                    BitmapShader shader = new BitmapShader(avatarBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    matrix.reset();
                    matrix.postScale((sz * s) / avatarBitmap.getWidth(), (sz * s) / avatarBitmap.getHeight());
                    matrix.postTranslate(x, y);
                    shader.setLocalMatrix(matrix);
                    paint.setShader(shader);
                    canvas.drawCircle(x + sz * s / 2, y + sz * s / 2, sz * s / 2, paint);

                    avatarBitmap.recycle();
                } else if (obj instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) obj;
                    int[] colors = new int[] {
                        Theme.getColor(Theme.keys_avatar_background[AvatarDrawable.getColorIndex(user.id)]),
                        Theme.getColor(Theme.keys_avatar_background2[AvatarDrawable.getColorIndex(user.id)])
                    };
                    LinearGradient shader = new LinearGradient(x, y, x, y + sz * s, colors, new float[] {0, 1}, Shader.TileMode.CLAMP);
                    paint.setShader(shader);
                    canvas.drawCircle(x + sz * s / 2, y + sz * s / 2, sz * s / 2, paint);

                    if (textPaint == null) {
                        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                        textPaint.setTypeface(AndroidUtilities.bold());
                        textPaint.setTextSize(sz * .25f);
                        textPaint.setColor(0xFFFFFFFF);
                    }
                    StringBuilder string = new StringBuilder();
                    AvatarDrawable.getAvatarSymbols(user.first_name, user.last_name, null, string);
                    String text = string.toString();

                    textPaint.getTextBounds(text, 0, text.length(), rect);
                    canvas.drawText(text, x + sz * s / 2 - rect.width() / 2f - rect.left, y + sz * s / 2 - rect.height() / 2f - rect.top, textPaint);
                }

            } catch (Throwable ignore) {}
        }
        return finalBitmap;
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
    public static final int SETTING_MUTE_CUSTOM = 5;

    public void clearDialogNotificationsSettings(long did, long topicId) {
        SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
        SharedPreferences.Editor editor = preferences.edit();
        String prefKey =  NotificationsController.getSharedPrefKey(did, topicId);
        editor.remove("notify2_" + prefKey).remove("custom_" + prefKey);
        getMessagesStorage().setDialogFlags(did, 0);
        TLRPC.Dialog dialog = getMessagesController().dialogs_dict.get(did);
        if (dialog != null) {
            dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
        }
        editor.commit();
        getNotificationsController().updateServerNotificationsSettings(did, topicId,true);
    }

    public void setDialogNotificationsSettings(long dialog_id, long topicId, int setting) {
        SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
        SharedPreferences.Editor editor = preferences.edit();
        TLRPC.Dialog dialog = MessagesController.getInstance(UserConfig.selectedAccount).dialogs_dict.get(dialog_id);
        if (setting == SETTING_MUTE_UNMUTE) {
            boolean defaultEnabled = isGlobalNotificationsEnabled(dialog_id, false, false);
            if (defaultEnabled) {
                editor.remove("notify2_" + NotificationsController.getSharedPrefKey(dialog_id, topicId));
            } else {
                editor.putInt("notify2_" + NotificationsController.getSharedPrefKey(dialog_id, topicId), 0);
            }
            //TODO topic
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
                editor.putInt("notify2_" + NotificationsController.getSharedPrefKey(dialog_id, topicId), 2);
                flags = 1;
            } else {
                editor.putInt("notify2_" + NotificationsController.getSharedPrefKey(dialog_id, topicId), 3);
                editor.putInt("notifyuntil_" + NotificationsController.getSharedPrefKey(dialog_id, topicId), untilTime);
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
        updateServerNotificationsSettings(dialog_id, topicId);
    }

    public void updateServerNotificationsSettings(long dialog_id, long topicId) {
        updateServerNotificationsSettings(dialog_id, topicId, true);
    }

    public void updateServerNotificationsSettings(long dialogId, long topicId, boolean post) {
        if (post) {
            getNotificationCenter().postNotificationName(NotificationCenter.notificationsSettingsUpdated);
        }
        if (DialogObject.isEncryptedDialog(dialogId)) {
            return;
        }
        SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
        TLRPC.TL_account_updateNotifySettings req = new TLRPC.TL_account_updateNotifySettings();
        req.settings = new TLRPC.TL_inputPeerNotifySettings();

        final String key = NotificationsController.getSharedPrefKey(dialogId, topicId);

        req.settings.flags |= 1;
        req.settings.show_previews = preferences.getBoolean("content_preview_" + key, true);

        req.settings.flags |= 2;
        req.settings.silent = preferences.getBoolean("silent_" + key, false);

        if (preferences.contains("stories_" + key)) {
            req.settings.flags |= 64;
            req.settings.stories_muted = !preferences.getBoolean("stories_" + key, true);
        }

        int mute_type = preferences.getInt("notify2_" + NotificationsController.getSharedPrefKey(dialogId, topicId), -1);
        if (mute_type != -1) {
            req.settings.flags |= 4;
            if (mute_type == 3) {
                req.settings.mute_until = preferences.getInt("notifyuntil_" + NotificationsController.getSharedPrefKey(dialogId, topicId), 0);
            } else {
                req.settings.mute_until = mute_type != 2 ? 0 : Integer.MAX_VALUE;
            }
        }

        long soundDocumentId = preferences.getLong("sound_document_id_" + NotificationsController.getSharedPrefKey(dialogId, topicId), 0);
        String soundPath =  preferences.getString("sound_path_" + NotificationsController.getSharedPrefKey(dialogId, topicId), null);
        req.settings.flags |= 8;
        if (soundDocumentId != 0) {
            TLRPC.TL_notificationSoundRingtone ringtoneSound = new TLRPC.TL_notificationSoundRingtone();
            ringtoneSound.id = soundDocumentId;
            req.settings.sound = ringtoneSound;
        } else if (soundPath != null) {
            if (soundPath.equalsIgnoreCase("NoSound")) {
                req.settings.sound = new TLRPC.TL_notificationSoundNone();
            } else {
                TLRPC.TL_notificationSoundLocal localSound = new TLRPC.TL_notificationSoundLocal();
                localSound.title = preferences.getString("sound_" + NotificationsController.getSharedPrefKey(dialogId, topicId), null);
                localSound.data = soundPath;
                req.settings.sound = localSound;
            }
        } else {
            req.settings.sound = new TLRPC.TL_notificationSoundDefault();
        }
        if (topicId != 0 && dialogId != getUserConfig().getClientUserId()) {
            TLRPC.TL_inputNotifyForumTopic topicPeer = new TLRPC.TL_inputNotifyForumTopic();
            topicPeer.peer = getMessagesController().getInputPeer(dialogId);
            topicPeer.top_msg_id = (int) topicId;
            req.peer = topicPeer;
        } else {
            req.peer = new TLRPC.TL_inputNotifyPeer();
            ((TLRPC.TL_inputNotifyPeer) req.peer).peer = getMessagesController().getInputPeer(dialogId);
        }

        getConnectionsManager().sendRequest(req, (response, error) -> {
           // FileLog.d("updateServerNotificationsSettings " + dialogId + " " + topicId + " error = " + error);
        });
    }

    public final static int TYPE_GROUP = 0;
    public final static int TYPE_PRIVATE = 1;
    public final static int TYPE_CHANNEL = 2;
    public final static int TYPE_STORIES = 3;
    public final static int TYPE_REACTIONS_MESSAGES = 4;
    public final static int TYPE_REACTIONS_STORIES = 5;

    public void updateServerNotificationsSettings(int type) {
        SharedPreferences preferences = getAccountInstance().getNotificationsSettings();
        if (type == TYPE_REACTIONS_MESSAGES || type == TYPE_REACTIONS_STORIES) {
            TLRPC.TL_account_setReactionsNotifySettings req = new TLRPC.TL_account_setReactionsNotifySettings();
            req.settings = new TLRPC.TL_reactionsNotifySettings();
            if (preferences.getBoolean("EnableReactionsMessages", true)) {
                req.settings.flags |= 1;
                if (preferences.getBoolean("EnableReactionsMessagesContacts", false)) {
                    req.settings.messages_notify_from = new TLRPC.TL_reactionNotificationsFromContacts();
                } else {
                    req.settings.messages_notify_from = new TLRPC.TL_reactionNotificationsFromAll();
                }
            }
            if (preferences.getBoolean("EnableReactionsStories", true)) {
                req.settings.flags |= 2;
                if (preferences.getBoolean("EnableReactionsStoriesContacts", false)) {
                    req.settings.stories_notify_from = new TLRPC.TL_reactionNotificationsFromContacts();
                } else {
                    req.settings.stories_notify_from = new TLRPC.TL_reactionNotificationsFromAll();
                }
            }
            req.settings.show_previews = preferences.getBoolean("EnableReactionsPreview", true);
            req.settings.sound = getInputSound(preferences, "ReactionSound", "ReactionSoundDocId", "ReactionSoundPath");
            getConnectionsManager().sendRequest(req, (response, error) -> { });
            return;
        }

        TLRPC.TL_account_updateNotifySettings req = new TLRPC.TL_account_updateNotifySettings();
        req.settings = new TLRPC.TL_inputPeerNotifySettings();
        req.settings.flags = 5;
        if (type == TYPE_GROUP) {
            req.peer = new TLRPC.TL_inputNotifyChats();
            req.settings.mute_until = preferences.getInt("EnableGroup2", 0);
            req.settings.show_previews = preferences.getBoolean("EnablePreviewGroup", true);

            req.settings.flags |= 8;
            req.settings.sound = getInputSound(preferences, "GroupSound", "GroupSoundDocId", "GroupSoundPath");
        } else if (type == TYPE_PRIVATE || type == TYPE_STORIES) {
            req.peer = new TLRPC.TL_inputNotifyUsers();
            req.settings.mute_until = preferences.getInt("EnableAll2", 0);
            req.settings.show_previews = preferences.getBoolean("EnablePreviewAll", true);

            req.settings.flags |= 128;
            req.settings.stories_hide_sender = preferences.getBoolean("EnableHideStoriesSenders", false);
            if (preferences.contains("EnableAllStories")) {
                req.settings.flags |= 64;
                req.settings.stories_muted = !preferences.getBoolean("EnableAllStories", true);
            }

            req.settings.flags |= 8;
            req.settings.sound = getInputSound(preferences, "GlobalSound", "GlobalSoundDocId", "GlobalSoundPath");

            req.settings.flags |= 256;
            req.settings.stories_sound = getInputSound(preferences, "StoriesSound", "StoriesSoundDocId", "StoriesSoundPath");
        } else {
            req.peer = new TLRPC.TL_inputNotifyBroadcasts();
            req.settings.mute_until = preferences.getInt("EnableChannel2", 0);
            req.settings.show_previews = preferences.getBoolean("EnablePreviewChannel", true);

            req.settings.flags |= 8;
            req.settings.sound = getInputSound(preferences, "ChannelSound", "ChannelSoundDocId", "ChannelSoundPath");
        }

        getConnectionsManager().sendRequest(req, (response, error) -> { });
    }

    private TLRPC.NotificationSound getInputSound(SharedPreferences preferences, String namePref, String docPref, String pathPref) {
        long soundDocumentId = preferences.getLong(docPref, 0);
        String soundPath =  preferences.getString(pathPref, "NoSound");
        if (soundDocumentId != 0) {
            TLRPC.TL_notificationSoundRingtone ringtoneSound = new TLRPC.TL_notificationSoundRingtone();
            ringtoneSound.id = soundDocumentId;
            return ringtoneSound;
        } else if (soundPath != null) {
            if (soundPath.equalsIgnoreCase("NoSound")) {
                return new TLRPC.TL_notificationSoundNone();
            } else {
                TLRPC.TL_notificationSoundLocal localSound = new TLRPC.TL_notificationSoundLocal();
                localSound.title = preferences.getString(namePref, null);
                localSound.data = soundPath;
                return localSound;
            }
        } else {
            return new TLRPC.TL_notificationSoundDefault();
        }
    }

    public boolean isGlobalNotificationsEnabled(long dialogId, boolean isReaction, boolean isStoryReaction) {
        return isGlobalNotificationsEnabled(dialogId, null, isReaction, isStoryReaction);
    }

    public boolean isGlobalNotificationsEnabled(long dialogId, Boolean forceChannel, boolean isReaction, boolean isStoryReaction) {
        int type;
        if (isReaction) {
            type = TYPE_REACTIONS_MESSAGES;
        } else if (isStoryReaction) {
            type = TYPE_REACTIONS_STORIES;
        } else if (DialogObject.isChatDialog(dialogId)) {
            if (forceChannel != null) {
                if (forceChannel) {
                    type = TYPE_CHANNEL;
                } else {
                    type = TYPE_GROUP;
                }
            } else {
                TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
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
        if (type == TYPE_REACTIONS_MESSAGES) {
            return getAccountInstance().getNotificationsSettings().getBoolean("EnableReactionsMessages", true);
        }
        if (type == TYPE_REACTIONS_STORIES) {
            return getAccountInstance().getNotificationsSettings().getBoolean("EnableReactionsStories", true);
        }
        if (type == TYPE_STORIES) {
            return getAccountInstance().getNotificationsSettings().getBoolean("EnableAllStories", true);
        }
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

    public void muteDialog(long dialog_id, long topicId, boolean mute) {
        if (mute) {
            NotificationsController.getInstance(currentAccount).muteUntil(dialog_id, topicId, Integer.MAX_VALUE);
        } else {
            boolean defaultEnabled = NotificationsController.getInstance(currentAccount).isGlobalNotificationsEnabled(dialog_id, false, false);
            boolean override = topicId != 0;
            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
            SharedPreferences.Editor editor = preferences.edit();
            if (defaultEnabled && !override) {
                editor.remove("notify2_" + getSharedPrefKey(dialog_id, topicId));
            } else {
                editor.putInt("notify2_" + getSharedPrefKey(dialog_id, topicId), 0);
            }
            if (topicId == 0) {
                getMessagesStorage().setDialogFlags(dialog_id, 0);
                TLRPC.Dialog dialog = getMessagesController().dialogs_dict.get(dialog_id);
                if (dialog != null) {
                    dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                }
            }
            editor.apply();
            updateServerNotificationsSettings(dialog_id, topicId);
        }
    }

    public NotificationsSettingsFacade getNotificationsSettingsFacade() {
        return dialogsNotificationsFacade;
    }

    public void loadTopicsNotificationsExceptions(long dialogId, Consumer<HashSet<Integer>> consumer) {
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            HashSet<Integer> topics = new HashSet<>();
            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
            Map<String, ?> values = preferences.getAll();
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("notify2_" + dialogId)) {
                    key = key.replace("notify2_" + dialogId, "");

                    int topicId = Utilities.parseInt(key);
                    if (topicId != 0) {
                        if (getMessagesController().isDialogMuted(dialogId, topicId) != getMessagesController().isDialogMuted(dialogId, 0)) {
                            topics.add(topicId);
                        }
                    }
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (consumer != null) {
                    consumer.accept(topics);
                }
            });
        });

    }

    private static class DialogKey {
        final long dialogId;
        final long topicId;
        final boolean story;

        private DialogKey(long dialogId, long topicId, boolean story) {
            this.dialogId = dialogId;
            this.topicId = topicId;
            this.story = story;
        }
    }

    public static class StoryNotification {
        final long dialogId;
        String localName;
        final HashMap<Integer, Pair<Long, Long>> dateByIds = new HashMap<>();
        boolean hidden;

        public long date;

        public StoryNotification(long dialogId, String localName, int id, long date) {
            this(dialogId, localName, id, date, date + 86400000);
        }

        public StoryNotification(long dialogId, String localName, int id, long date, long expire_date) {
            this.dialogId = dialogId;
            this.localName = localName;
            this.dateByIds.put(id, new Pair<>(date, expire_date));
            this.date = date;
        }

        public long getLeastDate() {
            long minDate = -1;
            for (Pair<Long, Long> date : dateByIds.values()) {
                if (minDate == -1 || minDate > date.first) {
                    minDate = date.first;
                }
            }
            return minDate;
        }
    }

    private void checkStoryPushes() {
        boolean changed = false;
        final long now = System.currentTimeMillis();
        for (int i = 0; i < storyPushMessages.size(); ++i) {
            StoryNotification push = storyPushMessages.get(i);
            Iterator<Map.Entry<Integer, Pair<Long, Long>>> it = push.dateByIds.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Pair<Long, Long>> e = it.next();
                long expire_date = e.getValue().second;
                if (now >= expire_date) {
                    it.remove();
                    changed = true;
                }
            }
            if (changed) {
                if (push.dateByIds.isEmpty()) {
                    getMessagesStorage().deleteStoryPushMessage(push.dialogId);
                    storyPushMessages.remove(i);
                    i--;
                } else {
                    getMessagesStorage().putStoryPushMessage(push);
                }
            }
        }
        if (changed) {
            showOrUpdateNotification(false);
        }
        updateStoryPushesRunnable();
    }

    private Runnable checkStoryPushesRunnable = this::checkStoryPushes;

    private void updateStoryPushesRunnable() {
        long minChangeTime = Long.MAX_VALUE;
        for (int i = 0; i < storyPushMessages.size(); ++i) {
            StoryNotification push = storyPushMessages.get(i);
            for (Pair<Long, Long> d : push.dateByIds.values()) {
                minChangeTime = Math.min(minChangeTime, d.second);
            }
        }
        notificationsQueue.cancelRunnable(checkStoryPushesRunnable);
        long delay = minChangeTime - System.currentTimeMillis();
        if (minChangeTime != Long.MAX_VALUE) {
            notificationsQueue.postRunnable(checkStoryPushesRunnable, Math.max(0, delay));
        }
    }
}
