package org.telegram.messenger;

import static org.telegram.messenger.NotificationsController.TYPE_PRIVATE;

import android.content.SharedPreferences;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.NotificationsSoundActivity;

public class NotificationsSettingsFacade {

    public final static String PROPERTY_NOTIFY = "notify2_";
    public final static String PROPERTY_CUSTOM = "custom_";
    public final static String PROPERTY_NOTIFY_UNTIL = "notifyuntil_";
    public final static String PROPERTY_CONTENT_PREVIEW = "content_preview_";
    public final static String PROPERTY_SILENT  = "silent_";
    public final static String PROPERTY_STORIES_NOTIFY = "stories_";

    private final int currentAccount;

    public NotificationsSettingsFacade(int currentAccount) {
        this.currentAccount = currentAccount;
    }


    public boolean isDefault(long dialogId, int topicId) {
        String key = NotificationsController.getSharedPrefKey(dialogId, topicId);
        return false;
    }

    public void clearPreference(long dialogId, int topicId) {
        String key = NotificationsController.getSharedPrefKey(dialogId, topicId);
        getPreferences().edit()
                .remove(PROPERTY_NOTIFY + key)
                .remove(PROPERTY_CUSTOM + key)
                .remove(PROPERTY_NOTIFY_UNTIL + key)
                .remove(PROPERTY_CONTENT_PREVIEW + key)
                .remove(PROPERTY_SILENT + key)
                .remove(PROPERTY_STORIES_NOTIFY + key)
                .apply();

    }


    public int getProperty(String property, long dialogId, int topicId, int defaultValue) {
        String key = NotificationsController.getSharedPrefKey(dialogId, topicId);
        if (getPreferences().contains(property + key)) {
            return getPreferences().getInt(property + key, defaultValue);
        }
        key = NotificationsController.getSharedPrefKey(dialogId, 0);
        return getPreferences().getInt(property + key, defaultValue);
    }

    public long getProperty(String property, long dialogId, int topicId, long defaultValue) {
        String key = NotificationsController.getSharedPrefKey(dialogId, topicId);
        if (getPreferences().contains(property + key)) {
            return getPreferences().getLong(property + key, defaultValue);
        }
        key = NotificationsController.getSharedPrefKey(dialogId, 0);
        return getPreferences().getLong(property + key, defaultValue);
    }

    public boolean getProperty(String property, long dialogId, int topicId, boolean defaultValue) {
        String key = NotificationsController.getSharedPrefKey(dialogId, topicId);
        if (getPreferences().contains(property + key)) {
            return getPreferences().getBoolean(property + key, defaultValue);
        }
        key = NotificationsController.getSharedPrefKey(dialogId, 0);
        return getPreferences().getBoolean(property + key, defaultValue);
    }

    public String getPropertyString(String property, long dialogId, int topicId, String defaultValue) {
        String key = NotificationsController.getSharedPrefKey(dialogId, topicId);
        if (getPreferences().contains(property + key)) {
            return getPreferences().getString(property + key, defaultValue);
        }
        key = NotificationsController.getSharedPrefKey(dialogId, 0);
        return getPreferences().getString(property + key, defaultValue);
    }


    public void removeProperty(String property, long dialogId, int topicId) {
        String key = NotificationsController.getSharedPrefKey(dialogId, topicId);
        getPreferences().edit().remove(property + key).apply();
    }

    private SharedPreferences getPreferences() {
        return MessagesController.getNotificationsSettings(currentAccount);
    }

    public void applyDialogNotificationsSettings(long dialogId, int topicId, TLRPC.PeerNotifySettings notify_settings) {
        if (notify_settings == null) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            String key = NotificationsController.getSharedPrefKey(dialogId, topicId);
            MessagesController messagesController = MessagesController.getInstance(currentAccount);
            ConnectionsManager connectionsManager = ConnectionsManager.getInstance(currentAccount);
            MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
            NotificationsController notificationsController = NotificationsController.getInstance(currentAccount);

            int currentValue = getPreferences().getInt(PROPERTY_NOTIFY + key, -1);
            int currentValue2 = getPreferences().getInt(PROPERTY_NOTIFY_UNTIL + key, 0);
            SharedPreferences.Editor editor = getPreferences().edit();
            boolean updated = false;
            if ((notify_settings.flags & 2) != 0) {
                editor.putBoolean(PROPERTY_SILENT + key, notify_settings.silent);
            } else {
                editor.remove(PROPERTY_SILENT + key);
            }
            if ((notify_settings.flags & 64) != 0) {
                editor.putBoolean(PROPERTY_STORIES_NOTIFY + key, !notify_settings.stories_muted);
            } else {
                editor.remove(PROPERTY_STORIES_NOTIFY + key);
            }

            TLRPC.Dialog dialog = null;
            if (topicId == 0) {
                dialog = messagesController.dialogs_dict.get(dialogId);
            }
            if (dialog != null) {
                dialog.notify_settings = notify_settings;
            }

            if ((notify_settings.flags & 4) != 0) {
                if (notify_settings.mute_until > connectionsManager.getCurrentTime()) {
                    int until = 0;
                    if (notify_settings.mute_until > connectionsManager.getCurrentTime() + 60 * 60 * 24 * 365) {
                        if (currentValue != 2) {
                            updated = true;
                            editor.putInt(PROPERTY_NOTIFY + key, 2);
                            if (dialog != null) {
                                dialog.notify_settings.mute_until = Integer.MAX_VALUE;
                            }
                        }
                    } else {
                        if (currentValue != 3 || currentValue2 != notify_settings.mute_until) {
                            updated = true;
                            editor.putInt(PROPERTY_NOTIFY + key, 3);
                            editor.putInt(PROPERTY_NOTIFY_UNTIL + key, notify_settings.mute_until);
                            if (dialog != null) {
                                dialog.notify_settings.mute_until = until;
                            }
                        }
                        until = notify_settings.mute_until;
                    }
                    if (topicId == 0) {
                        messagesStorage.setDialogFlags(dialogId, ((long) until << 32) | 1);
                        notificationsController.removeNotificationsForDialog(dialogId);
                    }
                } else {
                    if (currentValue != 0 && currentValue != 1) {
                        updated = true;
                        if (dialog != null) {
                            dialog.notify_settings.mute_until = 0;
                        }
                        editor.putInt(PROPERTY_NOTIFY + key, 0);
                    }
                    if (topicId == 0) {
                        messagesStorage.setDialogFlags(dialogId, 0);
                    }
                }
            } else {
                if (currentValue != -1) {
                    updated = true;
                    if (dialog != null) {
                        dialog.notify_settings.mute_until = 0;
                    }
                    editor.remove(PROPERTY_NOTIFY + key);
                }
                if (topicId == 0) {
                    messagesStorage.setDialogFlags(dialogId, 0);
                }
            }
            applySoundSettings(notify_settings.android_sound, editor, dialogId, topicId, 0, false);
            editor.apply();
            if (updated) {
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.notificationsSettingsUpdated);
                });
            }
        });
    }

    public void applySoundSettings(TLRPC.NotificationSound settings, SharedPreferences.Editor editor, long dialogId, int topicId, int globalType, boolean serverUpdate) {
        if (settings == null) {
            return;
        }
        String soundPref;
        String soundPathPref;
        String soundDocPref;
        if (dialogId != 0) {
            String key = NotificationsController.getSharedPrefKey(dialogId, topicId);
            soundPref = "sound_" + key;
            soundPathPref = "sound_path_" + key;
            soundDocPref = "sound_document_id_" + key;
        } else {
            if (globalType == NotificationsController.TYPE_GROUP) {
                soundPref = "GroupSound";
                soundDocPref = "GroupSoundDocId";
                soundPathPref = "GroupSoundPath";
            } else if (globalType == NotificationsController.TYPE_STORIES) {
                soundPref = "StoriesSound";
                soundDocPref = "StoriesSoundDocId";
                soundPathPref = "StoriesSoundPath";
            } else if (globalType == TYPE_PRIVATE) {
                soundPref = "GlobalSound";
                soundDocPref = "GlobalSoundDocId";
                soundPathPref = "GlobalSoundPath";
            } else {
                soundPref = "ChannelSound";
                soundDocPref = "ChannelSoundDocId";
                soundPathPref = "ChannelSoundPath";
            }
        }

        if (settings instanceof TLRPC.TL_notificationSoundLocal) {
            TLRPC.TL_notificationSoundLocal localSound = (TLRPC.TL_notificationSoundLocal) settings;
            if ("Default".equalsIgnoreCase(localSound.data)) {
                settings = new TLRPC.TL_notificationSoundDefault();
            } else if ("NoSound".equalsIgnoreCase(localSound.data)) {
                settings = new TLRPC.TL_notificationSoundNone();
            } else {
                String path = NotificationsSoundActivity.findRingtonePathByName(localSound.title);
                if (path == null) {
//                    settings = new TLRPC.TL_notificationSoundDefault();
                    return;
                } else {
                    localSound.data = path;
                }
            }
        }

        if (settings instanceof TLRPC.TL_notificationSoundDefault) {
            editor.putString(soundPref, "Default");
            editor.putString(soundPathPref, "Default");
            editor.remove(soundDocPref);
        } else if (settings instanceof TLRPC.TL_notificationSoundNone) {
            editor.putString(soundPref, "NoSound");
            editor.putString(soundPathPref, "NoSound");
            editor.remove(soundDocPref);
        } else if (settings instanceof TLRPC.TL_notificationSoundLocal) {
            TLRPC.TL_notificationSoundLocal localSound = (TLRPC.TL_notificationSoundLocal) settings;
            editor.putString(soundPref, localSound.title);
            editor.putString(soundPathPref, localSound.data);
            editor.remove(soundDocPref);
        } else if (settings instanceof TLRPC.TL_notificationSoundRingtone) {
            TLRPC.TL_notificationSoundRingtone soundRingtone = (TLRPC.TL_notificationSoundRingtone) settings;
            editor.putLong(soundDocPref, soundRingtone.id);
            MediaDataController.getInstance(currentAccount).checkRingtones(true);
            if (serverUpdate && dialogId != 0) {
                editor.putBoolean("custom_" + dialogId, true);
            }
            MediaDataController.getInstance(currentAccount).ringtoneDataStore.getDocument(soundRingtone.id);
        }
    }

    public void setSettingsForDialog(TLRPC.Dialog dialog, TLRPC.PeerNotifySettings notify_settings) {
        SharedPreferences.Editor editor = getPreferences().edit();
        long dialogId = MessageObject.getPeerId(dialog.peer);

        if ((dialog.notify_settings.flags & 2) != 0) {
            editor.putBoolean(PROPERTY_SILENT + dialogId, dialog.notify_settings.silent);
        } else {
            editor.remove(PROPERTY_SILENT + dialogId);
        }
        ConnectionsManager connectionsManager = ConnectionsManager.getInstance(currentAccount);
        if ((dialog.notify_settings.flags & 4) != 0) {
            if (dialog.notify_settings.mute_until > connectionsManager.getCurrentTime()) {
                if (dialog.notify_settings.mute_until > connectionsManager.getCurrentTime() + 60 * 60 * 24 * 365) {
                    editor.putInt(PROPERTY_NOTIFY + dialogId, 2);
                    dialog.notify_settings.mute_until = Integer.MAX_VALUE;
                } else {
                    editor.putInt(PROPERTY_NOTIFY + dialogId, 3);
                    editor.putInt(PROPERTY_NOTIFY_UNTIL + dialogId, dialog.notify_settings.mute_until);
                }
            } else {
                editor.putInt(PROPERTY_NOTIFY + dialogId, 0);
            }
        } else {
            editor.remove(PROPERTY_NOTIFY + dialogId);
        }

        editor.apply();
    }
}
