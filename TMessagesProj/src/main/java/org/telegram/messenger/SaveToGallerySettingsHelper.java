package org.telegram.messenger;

import static org.telegram.messenger.SharedConfig.SAVE_TO_GALLERY_FLAG_CHANNELS;
import static org.telegram.messenger.SharedConfig.SAVE_TO_GALLERY_FLAG_GROUP;
import static org.telegram.messenger.SharedConfig.SAVE_TO_GALLERY_FLAG_PEER;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.LongSparseArray;

public class SaveToGallerySettingsHelper {

    //shared settings
    public static SharedSettings user;
    public static SharedSettings groups;
    public static SharedSettings channels;

    public static String USERS_PREF_NAME = "users_save_gallery_exceptions";
    public static String CHANNELS_PREF_NAME = "channels_save_gallery_exceptions";
    public static String GROUPS_PREF_NAME = "groups_save_gallery_exceptions";

    public static final long DEFAULT_VIDEO_LIMIT = 100 * 1024 * 1024;//100 MB
    public static final long MAX_VIDEO_LIMIT = 4L * 1000 * 1024 * 1024;//100 MB

    public static void load(SharedPreferences preferences) {
        boolean saveToGalleryLegacy = preferences.getBoolean("save_gallery", false);
        int saveToGalleryFlags;
        if (saveToGalleryLegacy && BuildVars.NO_SCOPED_STORAGE) {
            saveToGalleryFlags = SAVE_TO_GALLERY_FLAG_PEER + SAVE_TO_GALLERY_FLAG_CHANNELS + SAVE_TO_GALLERY_FLAG_GROUP;
        } else {
            saveToGalleryFlags = preferences.getInt("save_gallery_flags", -1);
        }
        //migration
        if (saveToGalleryFlags != -1) {
            preferences.edit().remove("save_gallery").remove("save_gallery_flags").apply();
            user = new SharedSettings();
            user.savePhoto = user.saveVideo = (saveToGalleryFlags & SAVE_TO_GALLERY_FLAG_PEER) != 0;
            user.limitVideo = DEFAULT_VIDEO_LIMIT;
            user.save("user", preferences);

            groups = new SharedSettings();
            groups.savePhoto = user.saveVideo = (saveToGalleryFlags & SAVE_TO_GALLERY_FLAG_GROUP) != 0;
            groups.limitVideo = DEFAULT_VIDEO_LIMIT;
            groups.save("groups", preferences);

            channels = new SharedSettings();
            channels.savePhoto = channels.saveVideo = (saveToGalleryFlags & SAVE_TO_GALLERY_FLAG_CHANNELS) != 0;
            channels.limitVideo = DEFAULT_VIDEO_LIMIT;
            channels.save("channels", preferences);

        } else {
            user = SharedSettings.read("user", preferences);
            groups = SharedSettings.read("groups", preferences);
            channels = SharedSettings.read("channels", preferences);
        }
        user.type = SAVE_TO_GALLERY_FLAG_PEER;
        groups.type = SAVE_TO_GALLERY_FLAG_GROUP;
        channels.type = SAVE_TO_GALLERY_FLAG_CHANNELS;
    }

    public static boolean needSave(int flag, FilePathDatabase.FileMeta metaData, MessageObject messageObject, int currentAccount) {
        SharedSettings settings;
        if (flag == SharedConfig.SAVE_TO_GALLERY_FLAG_PEER) {
            settings = user;
        } else if (flag == SharedConfig.SAVE_TO_GALLERY_FLAG_CHANNELS) {
            settings = channels;
        } else if (flag == SharedConfig.SAVE_TO_GALLERY_FLAG_GROUP) {
            settings = groups;
        } else {
            return false;
        }
        return settings.needSave(metaData, messageObject, currentAccount);
    }

    public static LongSparseArray<DialogException> loadExceptions(SharedPreferences sharedPreferences) {
        LongSparseArray<DialogException> exceptions = new LongSparseArray<>();
        int count = sharedPreferences.getInt("count", 0);
        for (int i = 0; i < count; i++) {
            DialogException dialogException = new DialogException();
            dialogException.dialogId = sharedPreferences.getLong(i + "_dialog_id", 0);
            dialogException.savePhoto = sharedPreferences.getBoolean(i + "_photo", false);
            dialogException.saveVideo = sharedPreferences.getBoolean(i + "_video", false);
            dialogException.limitVideo = sharedPreferences.getLong(i + "_limitVideo", DEFAULT_VIDEO_LIMIT);
            if (dialogException.dialogId != 0) {
                exceptions.put(dialogException.dialogId, dialogException);
            }
        }
        return exceptions;
    }

    public static void saveExceptions(SharedPreferences sharedPreferences, LongSparseArray<DialogException> exceptions) {
        sharedPreferences.edit().clear().apply();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("count", exceptions.size());
        for (int i = 0; i < exceptions.size(); i++) {
            DialogException dialogException = exceptions.valueAt(i);
            editor.putLong(i + "_dialog_id", dialogException.dialogId);
            editor.putBoolean(i + "_photo", dialogException.savePhoto);
            editor.putBoolean(i + "_video", dialogException.saveVideo);
            editor.putLong(i + "_limitVideo", dialogException.limitVideo);
        }
        editor.apply();
    }

    public static Settings getSettings(int type) {
        if (type == SAVE_TO_GALLERY_FLAG_PEER) {
            return user;
        } else if (type == SAVE_TO_GALLERY_FLAG_GROUP) {
            return groups;
        } else if (type == SAVE_TO_GALLERY_FLAG_CHANNELS) {
            return channels;
        }
        return null;
    }

    public static void saveSettings(int type) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        if (type == SAVE_TO_GALLERY_FLAG_PEER) {
            user.save("user", preferences);
        } else if (type == SAVE_TO_GALLERY_FLAG_GROUP) {
            groups.save("groups", preferences);
        } else if (type == SAVE_TO_GALLERY_FLAG_CHANNELS) {
            channels.save("channels", preferences);
        }
    }

    public static abstract class Settings {
        public boolean savePhoto;
        public boolean saveVideo;
        public long limitVideo = 100 * 1024 * 1024; ///100 MB

        public boolean enabled() {
            return savePhoto || saveVideo;
        }

        public abstract CharSequence createDescription(int currentAccount);

        public void toggle() {
            if (enabled()) {
                saveVideo = false;
                savePhoto = false;
            } else {
                savePhoto = true;
                saveVideo = true;
            }
        }
    }

    public static class SharedSettings extends Settings {
        private int type;

        private void save(String prefix, SharedPreferences sharedPreferences) {
            sharedPreferences.edit()
                    .putBoolean(prefix + "_save_gallery_photo", savePhoto)
                    .putBoolean(prefix + "_save_gallery_video", saveVideo)
                    .putLong(prefix + "_save_gallery_limitVideo", limitVideo)
                    .apply();

        }

        private static SharedSettings read(String prefix, SharedPreferences preferences) {
            SharedSettings settings = new SharedSettings();
            settings.savePhoto = preferences.getBoolean(prefix + "_save_gallery_photo", false);
            settings.saveVideo = preferences.getBoolean(prefix + "_save_gallery_video", false);
            settings.limitVideo = preferences.getLong(prefix + "_save_gallery_limitVideo", DEFAULT_VIDEO_LIMIT);
            return settings;
        }

        private boolean needSave(FilePathDatabase.FileMeta meta, MessageObject messageObject, int currentAccount) {
            LongSparseArray<DialogException> exceptions = UserConfig.getInstance(currentAccount).getSaveGalleryExceptions(type);
            DialogException exception = exceptions.get(meta.dialogId);
            if (messageObject != null && (messageObject.isOutOwner() || messageObject.isSecretMedia())) {
                return false;
            }
            boolean isVideo = (messageObject != null && messageObject.isVideo()) || meta.messageType == MessageObject.TYPE_VIDEO;
            long size = messageObject != null ? messageObject.getSize() : meta.messageSize;
            boolean needSaveVideo = saveVideo;
            boolean needSavePhoto = savePhoto;
            long saveVideoLimit = limitVideo;
            if (exception != null) {
                needSaveVideo = exception.saveVideo;
                needSavePhoto = exception.savePhoto;
                saveVideoLimit = exception.limitVideo;
            }
            if (isVideo) {
                if (needSaveVideo && (saveVideoLimit == -1 || size < saveVideoLimit)) {
                    return true;
                }
            } else {
                if (needSavePhoto) {
                    return true;
                }
            }
            return false;
        }

        public CharSequence createDescription(int currentAccount) {
            StringBuilder builder = new StringBuilder();
            if (enabled()) {
                if (savePhoto) {
                    builder.append(LocaleController.getString("SaveToGalleryPhotos", R.string.SaveToGalleryPhotos));
                }
                if (saveVideo) {
                    if (builder.length() != 0) {
                        builder.append(", ");
                    }
                    builder.append(LocaleController.getString("SaveToGalleryVideos", R.string.SaveToGalleryVideos));
                    if (limitVideo > 0 && limitVideo < 4L * 1000 * 1024 * 1024) {
                        builder.append(" (").append(AndroidUtilities.formatFileSize(limitVideo, true)).append(")");
                    }
                }
            } else {
                builder.append(LocaleController.getString("SaveToGalleryOff", R.string.SaveToGalleryOff));
            }
            LongSparseArray<DialogException> exceptions = UserConfig.getInstance(currentAccount).getSaveGalleryExceptions(type);
            if (exceptions.size() != 0) {
                if (builder.length() != 0) {
                    builder.append(", ");
                }
                builder.append(LocaleController.formatPluralString("Exception", exceptions.size(), exceptions.size()));
            }
            return builder;
        }

        @Override
        public void toggle() {
            super.toggle();
            saveSettings(type);
        }
    }

    public static class DialogException extends Settings {
        public long dialogId;

        public CharSequence createDescription(int currentAccount) {
            StringBuilder builder = new StringBuilder();
            if (enabled()) {
                if (savePhoto) {
                    builder.append(LocaleController.getString("SaveToGalleryPhotos", R.string.SaveToGalleryPhotos));
                }
                if (saveVideo) {
                    if (builder.length() != 0) {
                        builder.append(", ");
                    }

                    if (limitVideo > 0 && limitVideo < 4L * 1000 * 1024 * 1024) {
                        builder.append(LocaleController.formatString("SaveToGalleryVideosUpTo", R.string.SaveToGalleryVideosUpTo, AndroidUtilities.formatFileSize(limitVideo, true)));
                    } else {
                        builder.append(LocaleController.formatString("SaveToGalleryVideos", R.string.SaveToGalleryVideos));
                    }
                }
            } else {
                builder.append(LocaleController.getString("SaveToGalleryOff", R.string.SaveToGalleryOff));
            }
            return builder;
        }
    }
}
