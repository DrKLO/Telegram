/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.android;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.RemoteInput;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PopupNotificationActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

public class NotificationsController {

    public static final String EXTRA_VOICE_REPLY = "extra_voice_reply";

    private ArrayList<MessageObject> pushMessages = new ArrayList<MessageObject>();
    private HashMap<Integer, MessageObject> pushMessagesDict = new HashMap<Integer, MessageObject>();
    private NotificationManagerCompat notificationManager = null;
    private HashMap<Long, Integer> pushDialogs = new HashMap<Long, Integer>();
    private HashMap<Long, Integer> wearNoticationsIds = new HashMap<Long, Integer>();
    private int wearNotificationId = 10000;
    public ArrayList<MessageObject> popupMessages = new ArrayList<MessageObject>();
    private long openned_dialog_id = 0;
    private int total_unread_count = 0;
    private int personal_count = 0;
    private boolean notifyCheck = false;

    private static volatile NotificationsController Instance = null;
    public static NotificationsController getInstance() {
        NotificationsController localInstance = Instance;
        if (localInstance == null) {
            synchronized (MessagesController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new NotificationsController();
                }
            }
        }
        return localInstance;
    }

    public NotificationsController() {
        notificationManager = NotificationManagerCompat.from(ApplicationLoader.applicationContext);
    }

    public void cleanup() {
        openned_dialog_id = 0;
        total_unread_count = 0;
        personal_count = 0;
        pushMessages.clear();
        pushMessagesDict.clear();
        pushDialogs.clear();
        popupMessages.clear();
        wearNoticationsIds.clear();
        notifyCheck = false;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.commit();
    }

    public void setOpennedDialogId(long dialog_id) {
        openned_dialog_id = dialog_id;
    }

    private String getStringForMessage(MessageObject messageObject, boolean shortMessage) {
        long dialog_id = messageObject.messageOwner.dialog_id;
        int chat_id = messageObject.messageOwner.to_id.chat_id;
        int user_id = messageObject.messageOwner.to_id.user_id;
        if (user_id == 0) {
            user_id = messageObject.messageOwner.from_id;
        } else if (user_id == UserConfig.getClientUserId()) {
            user_id = messageObject.messageOwner.from_id;
        }

        if (dialog_id == 0) {
            if (chat_id != 0) {
                dialog_id = -chat_id;
            } else if (user_id != 0) {
                dialog_id = user_id;
            }
        }

        TLRPC.User user = MessagesController.getInstance().getUser(user_id);
        if (user == null) {
            return null;
        }
        TLRPC.Chat chat = null;
        if (chat_id != 0) {
            chat = MessagesController.getInstance().getChat(chat_id);
            if (chat == null) {
                return null;
            }
        }

        String msg = null;
        if ((int)dialog_id != 0) {
            if (chat_id == 0 && user_id != 0) {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Context.MODE_PRIVATE);
                if (preferences.getBoolean("EnablePreviewAll", true)) {
                    if (messageObject.messageOwner instanceof TLRPC.TL_messageService) {
                        if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserJoined) {
                            msg = LocaleController.formatString("NotificationContactJoined", R.string.NotificationContactJoined, ContactsController.formatName(user.first_name, user.last_name));
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                            msg = LocaleController.formatString("NotificationContactNewPhoto", R.string.NotificationContactNewPhoto, ContactsController.formatName(user.first_name, user.last_name));
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionLoginUnknownLocation) {
                            String date = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.formatterYear.format(((long) messageObject.messageOwner.date) * 1000), LocaleController.formatterDay.format(((long) messageObject.messageOwner.date) * 1000));
                            msg = LocaleController.formatString("NotificationUnrecognizedDevice", R.string.NotificationUnrecognizedDevice, UserConfig.getCurrentUser().first_name, date, messageObject.messageOwner.action.title, messageObject.messageOwner.action.address);
                        }
                    } else {
                        if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty) {
                            if (!shortMessage) {
                                if (messageObject.messageOwner.message != null && messageObject.messageOwner.message.length() != 0) {
                                    msg = LocaleController.formatString("NotificationMessageText", R.string.NotificationMessageText, ContactsController.formatName(user.first_name, user.last_name), messageObject.messageOwner.message);
                                } else {
                                    msg = LocaleController.formatString("NotificationMessageNoText", R.string.NotificationMessageNoText, ContactsController.formatName(user.first_name, user.last_name));
                                }
                            } else {
                                msg = LocaleController.formatString("NotificationMessageNoText", R.string.NotificationMessageNoText, ContactsController.formatName(user.first_name, user.last_name));
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                            msg = LocaleController.formatString("NotificationMessagePhoto", R.string.NotificationMessagePhoto, ContactsController.formatName(user.first_name, user.last_name));
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVideo) {
                            msg = LocaleController.formatString("NotificationMessageVideo", R.string.NotificationMessageVideo, ContactsController.formatName(user.first_name, user.last_name));
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                            msg = LocaleController.formatString("NotificationMessageContact", R.string.NotificationMessageContact, ContactsController.formatName(user.first_name, user.last_name));
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo) {
                            msg = LocaleController.formatString("NotificationMessageMap", R.string.NotificationMessageMap, ContactsController.formatName(user.first_name, user.last_name));
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                            msg = LocaleController.formatString("NotificationMessageDocument", R.string.NotificationMessageDocument, ContactsController.formatName(user.first_name, user.last_name));
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaAudio) {
                            msg = LocaleController.formatString("NotificationMessageAudio", R.string.NotificationMessageAudio, ContactsController.formatName(user.first_name, user.last_name));
                        }
                    }
                } else {
                    msg = LocaleController.formatString("NotificationMessageNoText", R.string.NotificationMessageNoText, ContactsController.formatName(user.first_name, user.last_name));
                }
            } else if (chat_id != 0) {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Context.MODE_PRIVATE);
                if (preferences.getBoolean("EnablePreviewGroup", true)) {
                    if (messageObject.messageOwner instanceof TLRPC.TL_messageService) {
                        if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatAddUser) {
                            if (messageObject.messageOwner.action.user_id == UserConfig.getClientUserId()) {
                                msg = LocaleController.formatString("NotificationInvitedToGroup", R.string.NotificationInvitedToGroup, ContactsController.formatName(user.first_name, user.last_name), chat.title);
                            } else {
                                TLRPC.User u2 = MessagesController.getInstance().getUser(messageObject.messageOwner.action.user_id);
                                if (u2 == null) {
                                    return null;
                                }
                                msg = LocaleController.formatString("NotificationGroupAddMember", R.string.NotificationGroupAddMember, ContactsController.formatName(user.first_name, user.last_name), chat.title, ContactsController.formatName(u2.first_name, u2.last_name));
                            }
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatEditTitle) {
                            msg = LocaleController.formatString("NotificationEditedGroupName", R.string.NotificationEditedGroupName, ContactsController.formatName(user.first_name, user.last_name), messageObject.messageOwner.action.title);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatEditPhoto || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
                            msg = LocaleController.formatString("NotificationEditedGroupPhoto", R.string.NotificationEditedGroupPhoto, ContactsController.formatName(user.first_name, user.last_name), chat.title);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                            if (messageObject.messageOwner.action.user_id == UserConfig.getClientUserId()) {
                                msg = LocaleController.formatString("NotificationGroupKickYou", R.string.NotificationGroupKickYou, ContactsController.formatName(user.first_name, user.last_name), chat.title);
                            } else if (messageObject.messageOwner.action.user_id == user.id) {
                                msg = LocaleController.formatString("NotificationGroupLeftMember", R.string.NotificationGroupLeftMember, ContactsController.formatName(user.first_name, user.last_name), chat.title);
                            } else {
                                TLRPC.User u2 = MessagesController.getInstance().getUser(messageObject.messageOwner.action.user_id);
                                if (u2 == null) {
                                    return null;
                                }
                                msg = LocaleController.formatString("NotificationGroupKickMember", R.string.NotificationGroupKickMember, ContactsController.formatName(user.first_name, user.last_name), chat.title, ContactsController.formatName(u2.first_name, u2.last_name));
                            }
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatCreate) {
                            msg = messageObject.messageText.toString();
                        }
                    } else {
                        if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty) {
                            if (!shortMessage && messageObject.messageOwner.message != null && messageObject.messageOwner.message.length() != 0) {
                                msg = LocaleController.formatString("NotificationMessageGroupText", R.string.NotificationMessageGroupText, ContactsController.formatName(user.first_name, user.last_name), chat.title, messageObject.messageOwner.message);
                            } else {
                                msg = LocaleController.formatString("NotificationMessageGroupNoText", R.string.NotificationMessageGroupNoText, ContactsController.formatName(user.first_name, user.last_name), chat.title);
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                            msg = LocaleController.formatString("NotificationMessageGroupPhoto", R.string.NotificationMessageGroupPhoto, ContactsController.formatName(user.first_name, user.last_name), chat.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVideo) {
                            msg = LocaleController.formatString("NotificationMessageGroupVideo", R.string.NotificationMessageGroupVideo, ContactsController.formatName(user.first_name, user.last_name), chat.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                            msg = LocaleController.formatString("NotificationMessageGroupContact", R.string.NotificationMessageGroupContact, ContactsController.formatName(user.first_name, user.last_name), chat.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo) {
                            msg = LocaleController.formatString("NotificationMessageGroupMap", R.string.NotificationMessageGroupMap, ContactsController.formatName(user.first_name, user.last_name), chat.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                            msg = LocaleController.formatString("NotificationMessageGroupDocument", R.string.NotificationMessageGroupDocument, ContactsController.formatName(user.first_name, user.last_name), chat.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaAudio) {
                            msg = LocaleController.formatString("NotificationMessageGroupAudio", R.string.NotificationMessageGroupAudio, ContactsController.formatName(user.first_name, user.last_name), chat.title);
                        }
                    }
                } else {
                    msg = LocaleController.formatString("NotificationMessageGroupNoText", R.string.NotificationMessageGroupNoText, ContactsController.formatName(user.first_name, user.last_name), chat.title);
                }
            }
        } else {
            msg = LocaleController.getString("YouHaveNewMessage", R.string.YouHaveNewMessage);
        }
        return msg;
    }

    private void scheduleNotificationRepeat() {
        try {
            AlarmManager alarm = (AlarmManager) ApplicationLoader.applicationContext.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pintent = PendingIntent.getService(ApplicationLoader.applicationContext, 0, new Intent(ApplicationLoader.applicationContext, NotificationRepeat.class), 0);
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
            int minutes = preferences.getInt("repeat_messages", 60);
            if (minutes > 0 && personal_count > 0) {
                alarm.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes * 60 * 1000, pintent);
            } else {
                alarm.cancel(pintent);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    protected void repeatNotificationMaybe() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 11 && hour <= 22) {
            notificationManager.cancel(1);
            showOrUpdateNotification(true);
        } else {
            scheduleNotificationRepeat();
        }
    }

    private void showOrUpdateNotification(boolean notifyAboutLast) {
        if (!UserConfig.isClientActivated() || pushMessages.isEmpty()) {
            dismissNotification();
            return;
        }
        try {
            ConnectionsManager.getInstance().resumeNetworkMaybe();

            MessageObject lastMessageObject = pushMessages.get(0);

            long dialog_id = lastMessageObject.getDialogId();
            int mid = lastMessageObject.messageOwner.id;
            int chat_id = lastMessageObject.messageOwner.to_id.chat_id;
            int user_id = lastMessageObject.messageOwner.to_id.user_id;
            if (user_id == 0) {
                user_id = lastMessageObject.messageOwner.from_id;
            } else if (user_id == UserConfig.getClientUserId()) {
                user_id = lastMessageObject.messageOwner.from_id;
            }

            TLRPC.User user = MessagesController.getInstance().getUser(user_id);
            TLRPC.Chat chat = null;
            if (chat_id != 0) {
                chat = MessagesController.getInstance().getChat(chat_id);
            }
            TLRPC.FileLocation photoPath = null;

            boolean notifyDisabled = false;
            int needVibrate = 0;
            String choosenSoundPath = null;
            int ledColor = 0xff00ff00;
            boolean inAppSounds = false;
            boolean inAppVibrate = false;
            boolean inAppPreview = false;
            boolean inAppPriority = false;
            int priority = 0;
            int priority_override = 0;
            int vibrate_override = 0;

            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Context.MODE_PRIVATE);
            int notify_override = preferences.getInt("notify2_" + dialog_id, 0);
            if (!notifyAboutLast || notify_override == 2 || (!preferences.getBoolean("EnableAll", true) || chat_id != 0 && !preferences.getBoolean("EnableGroup", true)) && notify_override == 0) {
                notifyDisabled = true;
            }

            String defaultPath = Settings.System.DEFAULT_NOTIFICATION_URI.getPath();
            if (!notifyDisabled) {
                inAppSounds = preferences.getBoolean("EnableInAppSounds", true);
                inAppVibrate = preferences.getBoolean("EnableInAppVibrate", true);
                inAppPreview = preferences.getBoolean("EnableInAppPreview", true);
                inAppPriority = preferences.getBoolean("EnableInAppPriority", false);
                vibrate_override = preferences.getInt("vibrate_" + dialog_id, 0);
                priority_override = preferences.getInt("priority_" + dialog_id, 3);

                choosenSoundPath = preferences.getString("sound_path_" + dialog_id, null);
                if (chat_id != 0) {
                    if (choosenSoundPath != null && choosenSoundPath.equals(defaultPath)) {
                        choosenSoundPath = null;
                    } else if (choosenSoundPath == null) {
                        choosenSoundPath = preferences.getString("GroupSoundPath", defaultPath);
                    }
                    needVibrate = preferences.getInt("vibrate_group", 0);
                    priority = preferences.getInt("priority_group", 1);
                    ledColor = preferences.getInt("GroupLed", 0xff00ff00);
                } else if (user_id != 0) {
                    if (choosenSoundPath != null && choosenSoundPath.equals(defaultPath)) {
                        choosenSoundPath = null;
                    } else if (choosenSoundPath == null) {
                        choosenSoundPath = preferences.getString("GlobalSoundPath", defaultPath);
                    }
                    needVibrate = preferences.getInt("vibrate_messages", 0);
                    priority = preferences.getInt("priority_group", 1);
                    ledColor = preferences.getInt("MessagesLed", 0xff00ff00);
                }
                if (preferences.contains("color_" + dialog_id)) {
                    ledColor = preferences.getInt("color_" + dialog_id, 0);
                }

                if (priority_override != 3) {
                    priority = priority_override;
                }

                if (needVibrate == 2 && (vibrate_override == 1 || vibrate_override == 3 || vibrate_override == 5) || needVibrate != 2 && vibrate_override == 2 || vibrate_override != 0) {
                    needVibrate = vibrate_override;
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
            }

            Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
            intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
            intent.setFlags(32768);
            if ((int)dialog_id != 0) {
                if (pushDialogs.size() == 1) {
                    if (chat_id != 0) {
                        intent.putExtra("chatId", chat_id);
                    } else if (user_id != 0) {
                        intent.putExtra("userId", user_id);
                    }
                }
                if (pushDialogs.size() == 1) {
                    if (chat != null) {
                        if (chat.photo != null && chat.photo.photo_small != null && chat.photo.photo_small.volume_id != 0 && chat.photo.photo_small.local_id != 0) {
                            photoPath = chat.photo.photo_small;
                        }
                    } else {
                        if (user.photo != null && user.photo.photo_small != null && user.photo.photo_small.volume_id != 0 && user.photo.photo_small.local_id != 0) {
                            photoPath = user.photo.photo_small;
                        }
                    }
                }
            } else {
                if (pushDialogs.size() == 1) {
                    intent.putExtra("encId", (int) (dialog_id >> 32));
                }
            }
            PendingIntent contentIntent = PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent, PendingIntent.FLAG_ONE_SHOT);

            String name = null;
            boolean replace = true;
            if ((int)dialog_id == 0 || pushDialogs.size() > 1) {
                name = LocaleController.getString("AppName", R.string.AppName);
                replace = false;
            } else {
                if (chat != null) {
                    name = chat.title;
                } else {
                    name = ContactsController.formatName(user.first_name, user.last_name);
                }
            }

            String detailText = null;
            if (pushDialogs.size() == 1) {
                detailText = LocaleController.formatPluralString("NewMessages", total_unread_count);
            } else {
                detailText = LocaleController.formatString("NotificationMessagesPeopleDisplayOrder", R.string.NotificationMessagesPeopleDisplayOrder, LocaleController.formatPluralString("NewMessages", total_unread_count), LocaleController.formatPluralString("FromContacts", pushDialogs.size()));
            }

            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(ApplicationLoader.applicationContext)
                    .setContentTitle(name)
                    .setSmallIcon(R.drawable.notification)
                    .setAutoCancel(true)
                    .setNumber(total_unread_count)
                    .setContentIntent(contentIntent)
                    .setGroup("messages")
                    .setGroupSummary(true);

            if (priority == 0) {
                mBuilder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
            } else if (priority == 1) {
                mBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
            } else if (priority == 2) {
                mBuilder.setPriority(NotificationCompat.PRIORITY_MAX);
            }

            mBuilder.setCategory(NotificationCompat.CATEGORY_MESSAGE);
            if (chat == null && user != null && user.phone != null && user.phone.length() > 0) {
                mBuilder.addPerson("tel:+" + user.phone);
            }
            /*Bundle bundle = new Bundle();
            bundle.putString(NotificationCompat.EXTRA_PEOPLE, );
            mBuilder.setExtras()*/

            String lastMessage = null;
            String lastMessageFull = null;
            if (pushMessages.size() == 1) {
                String message = lastMessageFull = getStringForMessage(pushMessages.get(0), false);
                //lastMessage = getStringForMessage(pushMessages.get(0), true);
                lastMessage = lastMessageFull;
                if (message == null) {
                    return;
                }
                if (replace) {
                    if (chat != null) {
                        message = message.replace(" @ " + name, "");
                    } else {
                        message = message.replace(name + ": ", "").replace(name + " ", "");
                    }
                }
                mBuilder.setContentText(message);
                mBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(message));
            } else {
                mBuilder.setContentText(detailText);
                NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
                inboxStyle.setBigContentTitle(name);
                int count = Math.min(10, pushMessages.size());
                for (int i = 0; i < count; i++) {
                    String message = getStringForMessage(pushMessages.get(i), false);
                    if (message == null) {
                        continue;
                    }
                    if (i == 0) {
                        lastMessageFull = message;
                        //lastMessage = getStringForMessage(pushMessages.get(i), true);
                        lastMessage = lastMessageFull;
                    }
                    if (pushDialogs.size() == 1) {
                        if (replace) {
                            if (chat != null) {
                                message = message.replace(" @ " + name, "");
                            } else {
                                message = message.replace(name + ": ", "").replace(name + " ", "");
                            }
                        }
                    }
                    inboxStyle.addLine(message);
                }
                inboxStyle.setSummaryText(detailText);
                mBuilder.setStyle(inboxStyle);
            }

            if (photoPath != null) {
                BitmapDrawable img = ImageLoader.getInstance().getImageFromMemory(photoPath, null, "50_50", null);
                if (img != null) {
                    mBuilder.setLargeIcon(img.getBitmap());
                }
            }

            if (!notifyDisabled) {
                if (ApplicationLoader.mainInterfacePaused || inAppPreview) {
                    if (lastMessage.length() > 100) {
                        lastMessage = lastMessage.substring(0, 100).replace("\n", " ").trim() + "...";
                    }
                    mBuilder.setTicker(lastMessage);
                }
                if (choosenSoundPath != null && !choosenSoundPath.equals("NoSound")) {
                    if (choosenSoundPath.equals(defaultPath)) {
                        /*MediaPlayer mediaPlayer = new MediaPlayer();
                        mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
                        mediaPlayer.setDataSource(ApplicationLoader.applicationContext, Settings.System.DEFAULT_NOTIFICATION_URI);
                        mediaPlayer.prepare();
                        mediaPlayer.start();*/
                        mBuilder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AudioManager.STREAM_NOTIFICATION);
                    } else {
                        mBuilder.setSound(Uri.parse(choosenSoundPath), AudioManager.STREAM_NOTIFICATION);
                    }
                }
                if (ledColor != 0) {
                    mBuilder.setLights(ledColor, 1000, 1000);
                }
                if (needVibrate == 2) {
                    mBuilder.setVibrate(new long[]{0, 0});
                } else if (needVibrate == 1) {
                    mBuilder.setVibrate(new long[]{0, 100, 0, 100});
                } else if (needVibrate == 0 || needVibrate == 4) {
                    mBuilder.setDefaults(NotificationCompat.DEFAULT_VIBRATE);
                } else if (needVibrate == 3) {
                    mBuilder.setVibrate(new long[]{0, 1000});
                }
            } else {
                mBuilder.setVibrate(new long[]{0, 0});
            }

            notificationManager.notify(1, mBuilder.build());
            if (preferences.getBoolean("EnablePebbleNotifications", false)) {
                sendAlertToPebble(lastMessageFull);
            }
            showWearNotifications(notifyAboutLast);
            scheduleNotificationRepeat();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public void showWearNotifications(boolean notifyAboutLast) {
        if (Build.VERSION.SDK_INT < 19) {
            return;
        }
        ArrayList<Long> sortedDialogs = new ArrayList<Long>();
        HashMap<Long, ArrayList<MessageObject>> messagesByDialogs = new HashMap<Long, ArrayList<MessageObject>>();
        for (MessageObject messageObject : pushMessages) {
            long dialog_id = messageObject.getDialogId();
            if ((int)dialog_id == 0) {
                continue;
            }

            ArrayList<MessageObject> arrayList = messagesByDialogs.get(dialog_id);
            if (arrayList == null) {
                arrayList = new ArrayList<MessageObject>();
                messagesByDialogs.put(dialog_id, arrayList);
                sortedDialogs.add(0, dialog_id);
            }
            arrayList.add(messageObject);
        }

        HashMap<Long, Integer> oldIds = new HashMap<Long, Integer>();
        oldIds.putAll(wearNoticationsIds);
        wearNoticationsIds.clear();

        for (long dialog_id : sortedDialogs) {
            ArrayList<MessageObject> messageObjects = messagesByDialogs.get(dialog_id);
            int max_id = messageObjects.get(0).messageOwner.id;
            TLRPC.Chat chat = null;
            TLRPC.User user = null;
            String name = null;
            if (dialog_id > 0) {
                user = MessagesController.getInstance().getUser((int)dialog_id);
                if (user == null) {
                    continue;
                }
            } else {
                chat = MessagesController.getInstance().getChat(-(int)dialog_id);
                if (chat == null) {
                    continue;
                }
            }
            if (chat != null) {
                name = chat.title;
            } else {
                name = ContactsController.formatName(user.first_name, user.last_name);
            }

            Integer notificationId = oldIds.get(dialog_id);
            if (notificationId == null) {
                notificationId = wearNotificationId++;
            } else {
                oldIds.remove(dialog_id);
            }

            Intent replyIntent = new Intent(ApplicationLoader.applicationContext, WearReplyReceiver.class);
            replyIntent.putExtra("dialog_id", dialog_id);
            replyIntent.putExtra("max_id", max_id);
            PendingIntent replyPendingIntent = PendingIntent.getBroadcast(ApplicationLoader.applicationContext, notificationId, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            RemoteInput remoteInput = new RemoteInput.Builder(EXTRA_VOICE_REPLY).setLabel(LocaleController.getString("Reply", R.string.Reply)).build();
            String replyToString;
            if (chat != null) {
                replyToString = LocaleController.formatString("ReplyToGroup", R.string.ReplyToGroup, name);
            } else {
                replyToString = LocaleController.formatString("ReplyToUser", R.string.ReplyToUser, name);
            }
            NotificationCompat.Action action = new NotificationCompat.Action.Builder(R.drawable.ic_reply_icon, replyToString, replyPendingIntent).addRemoteInput(remoteInput).build();

            String text = "";
            for (MessageObject messageObject : messageObjects) {
                String message = getStringForMessage(messageObject, false);
                if (message == null) {
                    continue;
                }
                if (chat != null) {
                    message = message.replace(" @ " + name, "");
                } else {
                    message = message.replace(name + ": ", "").replace(name + " ", "");
                }
                if (text.length() > 0) {
                    text += "\n\n";
                }
                text += message;
            }

            Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
            intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
            intent.setFlags(32768);
            if (chat != null) {
                intent.putExtra("chatId", chat.id);
            } else if (user != null) {
                intent.putExtra("userId", user.id);
            }
            PendingIntent contentIntent = PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent, PendingIntent.FLAG_ONE_SHOT);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext)
                    .setContentTitle(name)
                    .setSmallIcon(R.drawable.notification)
                    .setGroup("messages")
                    .setContentText(text)
                    .setGroupSummary(false)
                    .setContentIntent(contentIntent)
                    .extend(new NotificationCompat.WearableExtender().addAction(action))
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE);

            if (chat == null && user != null && user.phone != null && user.phone.length() > 0) {
                builder.addPerson("tel:+" + user.phone);
            }

            notificationManager.notify(notificationId, builder.build());
            wearNoticationsIds.put(dialog_id, notificationId);
        }

        for (HashMap.Entry<Long, Integer> entry : oldIds.entrySet()) {
            notificationManager.cancel(entry.getValue());
        }
    }

    private void dismissNotification() {
        try {
            notificationManager.cancel(1);
            pushMessages.clear();
            pushMessagesDict.clear();
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.pushMessagesUpdated);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private void sendAlertToPebble(String message) {
        try {
            final Intent i = new Intent("com.getpebble.action.SEND_NOTIFICATION");

            final HashMap<String, String> data = new HashMap<String, String>();
            data.put("title", LocaleController.getString("AppName", R.string.AppName));
            data.put("body", message);
            final JSONObject jsonData = new JSONObject(data);
            final String notificationData = new JSONArray().put(jsonData).toString();

            i.putExtra("messageType", "PEBBLE_ALERT");
            i.putExtra("sender", LocaleController.formatString("AppName", R.string.AppName));
            i.putExtra("notificationData", notificationData);

            ApplicationLoader.applicationContext.sendBroadcast(i);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public void processReadMessages(ArrayList<Integer> readMessages, long dialog_id, int max_date, int max_id, boolean isPopup) {
        int oldCount = popupMessages.size();
        int oldCount2 = pushMessages.size();
        if (readMessages != null) {
            for (Integer id : readMessages) {
                MessageObject messageObject = pushMessagesDict.get(id);
                if (messageObject != null) {
                    if (isPersonalMessage(messageObject)) {
                        personal_count--;
                    }
                    pushMessages.remove(messageObject);
                    popupMessages.remove(messageObject);
                    pushMessagesDict.remove(id);
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
                            if (messageObject.messageOwner.id <= max_id || max_id < 0) {
                                remove = true;
                            }
                        } else {
                            if (messageObject.messageOwner.id == max_id || max_id < 0) {
                                remove = true;
                            }
                        }
                    }
                    if (remove) {
                        if (isPersonalMessage(messageObject)) {
                            personal_count--;
                        }
                        pushMessages.remove(a);
                        popupMessages.remove(messageObject);
                        pushMessagesDict.remove(messageObject.messageOwner.id);
                        a--;
                    }
                }
            }
        }
        if (oldCount != popupMessages.size()) {
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.pushMessagesUpdated);
        }
    }

    public void processNewMessages(ArrayList<MessageObject> messageObjects, boolean isLast) {
        if (messageObjects.isEmpty()) {
            return;
        }
        boolean added = false;

        int oldCount = popupMessages.size();
        HashMap<Long, Boolean> settingsCache = new HashMap<Long, Boolean>();
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Context.MODE_PRIVATE);
        int popup = 0;

        for (MessageObject messageObject : messageObjects) {
            if (pushMessagesDict.containsKey(messageObject.messageOwner.id)) {
                continue;
            }
            long dialog_id = messageObject.getDialogId();
            if (dialog_id == openned_dialog_id && ApplicationLoader.isScreenOn) {
                continue;
            }
            if (isPersonalMessage(messageObject)) {
                personal_count++;
            }
            added = true;

            Boolean value = settingsCache.get(dialog_id);
            boolean isChat = (int)dialog_id < 0;
            popup = (int)dialog_id == 0 ? 0 : preferences.getInt(isChat ? "popupGroup" : "popupAll", 0);
            if (value == null) {
                int notify_override = preferences.getInt("notify2_" + dialog_id, 0);
                value = !(notify_override == 2 || (!preferences.getBoolean("EnableAll", true) || isChat && !preferences.getBoolean("EnableGroup", true)) && notify_override == 0);
                settingsCache.put(dialog_id, value);
            }
            if (value) {
                if (popup != 0) {
                    popupMessages.add(0, messageObject);
                }
                pushMessages.add(0, messageObject);
                pushMessagesDict.put(messageObject.messageOwner.id, messageObject);
            }
        }

        if (added) {
            notifyCheck = isLast;
        }

        if (!popupMessages.isEmpty() && oldCount != popupMessages.size()) {
            if (ApplicationLoader.mainInterfacePaused || !ApplicationLoader.isScreenOn) {
                MessageObject messageObject = messageObjects.get(0);
                if (popup == 3 || popup == 1 && ApplicationLoader.isScreenOn || popup == 2 && !ApplicationLoader.isScreenOn) {
                    Intent popupIntent = new Intent(ApplicationLoader.applicationContext, PopupNotificationActivity.class);
                    popupIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_FROM_BACKGROUND);
                    ApplicationLoader.applicationContext.startActivity(popupIntent);
                }
            }
        }
    }

    public void processDialogsUpdateRead(final HashMap<Long, Integer> dialogsToUpdate) {
        int old_unread_count = total_unread_count;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Context.MODE_PRIVATE);
        for (HashMap.Entry<Long, Integer> entry : dialogsToUpdate.entrySet()) {
            long dialog_id = entry.getKey();

            int notify_override = preferences.getInt("notify2_" + dialog_id, 0);
            boolean canAddValue = !(notify_override == 2 || (!preferences.getBoolean("EnableAll", true) || ((int)dialog_id < 0) && !preferences.getBoolean("EnableGroup", true)) && notify_override == 0);

            Integer currentCount = pushDialogs.get(dialog_id);
            Integer newCount = entry.getValue();
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
                for (int a = 0; a < pushMessages.size(); a++) {
                    MessageObject messageObject = pushMessages.get(a);
                    if (messageObject.getDialogId() == dialog_id) {
                        if (isPersonalMessage(messageObject)) {
                            personal_count--;
                        }
                        pushMessages.remove(a);
                        a--;
                        pushMessagesDict.remove(messageObject.messageOwner.id);
                        popupMessages.remove(messageObject);
                    }
                }
            } else if (canAddValue) {
                total_unread_count += newCount;
                pushDialogs.put(dialog_id, newCount);
            }
        }
        if (old_unread_count != total_unread_count) {
            showOrUpdateNotification(notifyCheck);
        }
        notifyCheck = false;
        if (preferences.getBoolean("badgeNumber", true)) {
            setBadge(ApplicationLoader.applicationContext, total_unread_count);
        }
    }

    public void processLoadedUnreadMessages(HashMap<Long, Integer> dialogs, ArrayList<TLRPC.Message> messages, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats, ArrayList<TLRPC.EncryptedChat> encryptedChats) {
        MessagesController.getInstance().putUsers(users, true);
        MessagesController.getInstance().putChats(chats, true);
        MessagesController.getInstance().putEncryptedChats(encryptedChats, true);

        pushDialogs.clear();
        pushMessages.clear();
        pushMessagesDict.clear();
        total_unread_count = 0;
        personal_count = 0;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Context.MODE_PRIVATE);
        HashMap<Long, Boolean> settingsCache = new HashMap<Long, Boolean>();

        for (HashMap.Entry<Long, Integer> entry : dialogs.entrySet()) {
            long dialog_id = entry.getKey();
            Boolean value = settingsCache.get(dialog_id);
            if (value == null) {
                int notify_override = preferences.getInt("notify2_" + dialog_id, 0);
                value = !(notify_override == 2 || (!preferences.getBoolean("EnableAll", true) || ((int) dialog_id < 0) && !preferences.getBoolean("EnableGroup", true)) && notify_override == 0);
                settingsCache.put(dialog_id, value);
            }
            if (!value) {
                continue;
            }
            int count = entry.getValue();
            pushDialogs.put(dialog_id, count);
            total_unread_count += count;
        }
        if (messages != null) {
            for (TLRPC.Message message : messages) {
                if (pushMessagesDict.containsKey(message.id)) {
                    continue;
                }
                MessageObject messageObject = new MessageObject(message, null, 0);
                if (isPersonalMessage(messageObject)) {
                    personal_count++;
                }
                long dialog_id = messageObject.getDialogId();
                Boolean value = settingsCache.get(dialog_id);
                if (value == null) {
                    int notify_override = preferences.getInt("notify2_" + dialog_id, 0);
                    value = !(notify_override == 2 || (!preferences.getBoolean("EnableAll", true) || ((int) dialog_id < 0) && !preferences.getBoolean("EnableGroup", true)) && notify_override == 0);
                    settingsCache.put(dialog_id, value);
                }
                if (!value || dialog_id == openned_dialog_id && ApplicationLoader.isScreenOn) {
                    continue;
                }
                pushMessagesDict.put(messageObject.messageOwner.id, messageObject);
                pushMessages.add(0, messageObject);
            }
        }
        if (total_unread_count == 0) {
            popupMessages.clear();
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.pushMessagesUpdated);
        }
        showOrUpdateNotification(SystemClock.uptimeMillis() / 1000 < 60);

        if (preferences.getBoolean("badgeNumber", true)) {
            setBadge(ApplicationLoader.applicationContext, total_unread_count);
        }
    }

    public void setBadgeEnabled(boolean enabled) {
        setBadge(ApplicationLoader.applicationContext, enabled ? total_unread_count : 0);
    }

    private void setBadge(Context context, int count) {
        try {
            String launcherClassName = getLauncherClassName(context);
            if (launcherClassName == null) {
                return;
            }
            Intent intent = new Intent("android.intent.action.BADGE_COUNT_UPDATE");
            intent.putExtra("badge_count", count);
            intent.putExtra("badge_count_package_name", context.getPackageName());
            intent.putExtra("badge_count_class_name", launcherClassName);
            context.sendBroadcast(intent);
        } catch (Throwable e) {
            FileLog.e("tmessages", e);
        }
    }

    public static String getLauncherClassName(Context context) {
        try {
            PackageManager pm = context.getPackageManager();

            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);

            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
            for (ResolveInfo resolveInfo : resolveInfos) {
                String pkgName = resolveInfo.activityInfo.applicationInfo.packageName;
                if (pkgName.equalsIgnoreCase(context.getPackageName())) {
                    return resolveInfo.activityInfo.name;
                }
            }
        } catch (Throwable e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }

    private boolean isPersonalMessage(MessageObject messageObject) {
        return messageObject.messageOwner.to_id != null && messageObject.messageOwner.to_id.chat_id == 0
                && (messageObject.messageOwner.action == null || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionEmpty);
    }
}
