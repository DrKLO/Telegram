package org.telegram.messenger;

import android.text.TextUtils;
import android.util.LongSparseArray;

import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;

public class CacheByChatsController {

    public static int KEEP_MEDIA_DELETE = 4;
    public static int KEEP_MEDIA_FOREVER = 2;
    public static int KEEP_MEDIA_ONE_DAY = 3;
    public static int KEEP_MEDIA_ONE_WEEK = 0;
    public static int KEEP_MEDIA_ONE_MONTH = 1;
    //TEST VALUE
    public static int KEEP_MEDIA_ONE_MINUTE = 5;

    public static final int KEEP_MEDIA_TYPE_USER = 0;
    public static final int KEEP_MEDIA_TYPE_GROUP = 1;
    public static final int KEEP_MEDIA_TYPE_CHANNEL = 2;

    private final int currentAccount;

    int[] keepMediaByTypes = {-1, -1, -1};

    public CacheByChatsController(int currentAccount) {
        this.currentAccount = currentAccount;
        for (int i = 0; i < 3; i++) {
            keepMediaByTypes[i] = SharedConfig.getPreferences().getInt("keep_media_type_" + i, getDefault(i));
        }
    }

    public static int getDefault(int type) {
        if (type == KEEP_MEDIA_TYPE_USER) {
            return KEEP_MEDIA_FOREVER;
        } else if (type == KEEP_MEDIA_TYPE_GROUP) {
            return KEEP_MEDIA_ONE_MONTH;
        } else if (type == KEEP_MEDIA_TYPE_CHANNEL) {
            return KEEP_MEDIA_ONE_MONTH;
        }
        return SharedConfig.keepMedia;
    }

    public static String getKeepMediaString(int keepMedia) {
        if (keepMedia == KEEP_MEDIA_ONE_MINUTE) {
            return LocaleController.formatPluralString("Minutes", 1);
        } else if (keepMedia == KEEP_MEDIA_ONE_DAY) {
            return LocaleController.formatPluralString("Days", 1);
        } else if (keepMedia == KEEP_MEDIA_ONE_WEEK) {
            return LocaleController.formatPluralString("Weeks", 1);
        } else if (keepMedia == KEEP_MEDIA_ONE_MONTH) {
            return LocaleController.formatPluralString("Months", 1);
        }
        return LocaleController.getString("AutoDeleteMediaNever", R.string.AutoDeleteMediaNever);
    }

    public static long getDaysInSeconds(int keepMedia) {
        long seconds;
        if (keepMedia == CacheByChatsController.KEEP_MEDIA_FOREVER) {
            seconds = Long.MAX_VALUE;
        } else if (keepMedia == CacheByChatsController.KEEP_MEDIA_ONE_WEEK) {
            seconds =  60L * 60L * 24L * 7L;
        } else if (keepMedia == CacheByChatsController.KEEP_MEDIA_ONE_MONTH) {
            seconds = 60L * 60L * 24L * 30L;
        } else if (keepMedia == CacheByChatsController.KEEP_MEDIA_ONE_DAY) {
            seconds = 60L * 60L * 24L;
        } else { //one min
            seconds = 60L;
        }
        return seconds;
    }

    public ArrayList<KeepMediaException> getKeepMediaExceptions(int type) {
        ArrayList<KeepMediaException> exceptions = new ArrayList<>();
        HashSet<Long> idsSet = new HashSet<>();
        String exceptionsHash = UserConfig.getInstance(currentAccount).getPreferences().getString("keep_media_exceptions_" + type, "");
        if (TextUtils.isEmpty(exceptionsHash)) {
            return exceptions;
        } else {
            ByteBuffer byteBuffer = ByteBuffer.wrap(Utilities.hexToBytes(exceptionsHash));
            int n = byteBuffer.getInt();
            for (int i = 0; i < n; i++) {
                KeepMediaException exception = new KeepMediaException(byteBuffer.getLong(), byteBuffer.getInt());
                if (!idsSet.contains(exception.dialogId)) {
                    idsSet.add(exception.dialogId);
                    exceptions.add(exception);
                }
            }
            byteBuffer.clear();
        }
        return exceptions;
    }

    public void saveKeepMediaExceptions(int type, ArrayList<KeepMediaException> exceptions) {
        String key = "keep_media_exceptions_" + type;
        if (exceptions.isEmpty()) {
            UserConfig.getInstance(currentAccount).getPreferences().edit().remove(key).apply();
        } else {
            int n = exceptions.size();
            ByteBuffer byteBuffer = ByteBuffer.allocate(4 + (8 + 4) * n);
            byteBuffer.putInt(n);
            for (int i = 0; i < n; i++) {
                byteBuffer.putLong(exceptions.get(i).dialogId);
                byteBuffer.putInt(exceptions.get(i).keepMedia);
            }
            UserConfig.getInstance(currentAccount).getPreferences().edit().putString(key, Utilities.bytesToHex(byteBuffer.array())).apply();
            byteBuffer.clear();
        }
    }

    public int getKeepMedia(int type) {
        if (keepMediaByTypes[type] == -1) {
            return SharedConfig.keepMedia;
        }
        return keepMediaByTypes[type];
    }

    public void setKeepMedia(int type, int keepMedia) {
        keepMediaByTypes[type] = keepMedia;
        SharedConfig.getPreferences().edit().putInt("keep_media_type_" + type, keepMedia).apply();
    }

    public void lookupFiles(ArrayList<? extends KeepMediaFile> keepMediaFiles) {
        LongSparseArray<ArrayList<KeepMediaFile>> filesByDialogId = FileLoader.getInstance(currentAccount).getFileDatabase().lookupFiles(keepMediaFiles);
        LongSparseArray<KeepMediaException> exceptionsByType = getKeepMediaExceptionsByDialogs();
        for (int i = 0; i < filesByDialogId.size(); i++) {
            long dialogId = filesByDialogId.keyAt(i);
            ArrayList<? extends KeepMediaFile> files = filesByDialogId.valueAt(i);
            int type;
            if (dialogId >= 0) {
                type = KEEP_MEDIA_TYPE_USER;
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                if (chat == null) {
                    chat = MessagesStorage.getInstance(currentAccount).getChatSync(-dialogId);
                }
                if (chat == null) {
                    type = -1;
                } else if (ChatObject.isChannel(chat)) {
                    type = KEEP_MEDIA_TYPE_CHANNEL;
                } else {
                    type = KEEP_MEDIA_TYPE_GROUP;
                }
            }
            KeepMediaException exception = exceptionsByType.get(dialogId);
            for (int k = 0; k < files.size(); k++) {
                KeepMediaFile file = files.get(k);
                if (type >= 0) {
                    file.dialogType = type;
                }
                if (exception != null) {
                    file.keepMedia = exception.keepMedia;
                }
            }
        }
    }

    public LongSparseArray<KeepMediaException> getKeepMediaExceptionsByDialogs() {
        LongSparseArray<KeepMediaException> sparseArray = new LongSparseArray<>();
        for (int i = 0; i < 3; i++) {
            ArrayList<KeepMediaException> exceptions = getKeepMediaExceptions(i);
            if (exceptions != null) {
                for (int k = 0; k < exceptions.size(); k++) {
                    sparseArray.put(exceptions.get(k).dialogId, exceptions.get(k));
                }
            }
        }
        return sparseArray;
    }

    public static class KeepMediaException {
        public final long dialogId;
        public int keepMedia;

        public KeepMediaException(long dialogId, int keepMedia) {
            this.dialogId = dialogId;
            this.keepMedia = keepMedia;
        }
    }

    public static class KeepMediaFile {
        final File file;
        int keepMedia = -1;
        int dialogType = KEEP_MEDIA_TYPE_CHANNEL;

        public KeepMediaFile(File file) {
            this.file = file;
        }
    }
}
