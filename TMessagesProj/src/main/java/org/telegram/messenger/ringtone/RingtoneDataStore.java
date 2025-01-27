package org.telegram.messenger.ringtone;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public class RingtoneDataStore {

    private final long clientUserId;
    String prefName = null;

    private static volatile long queryHash;
    private static volatile long lastReloadTimeMs;
    private final int currentAccount;
    private int localIds;

    private static final long reloadTimeoutMs = 24 * 60 * 60 * 1000;//1 day

    public final ArrayList<CachedTone> userRingtones = new ArrayList<>();
    private boolean loaded;

    public final static HashSet<String> ringtoneSupportedMimeType = new HashSet<>(Arrays.asList("audio/mpeg3", "audio/mpeg", "audio/ogg", "audio/m4a"));

    public RingtoneDataStore(int currentAccount) {
        this.currentAccount = currentAccount;
        this.clientUserId = UserConfig.getInstance(currentAccount).clientUserId;
        SharedPreferences preferences = getSharedPreferences();
        try {
            queryHash = preferences.getLong("hash", 0);
            lastReloadTimeMs = preferences.getLong("lastReload", 0);
        } catch (Exception e) {
            FileLog.e(e);
        }
        AndroidUtilities.runOnUIThread(() -> {
            loadUserRingtones(false);
        });
    }

    public void loadUserRingtones(boolean force) {
        boolean needReload = force || System.currentTimeMillis() - lastReloadTimeMs > reloadTimeoutMs;
        TL_account.getSavedRingtones req = new TL_account.getSavedRingtones();
        req.hash = queryHash;
        if (needReload) {
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response != null) {
                    if (response instanceof TL_account.TL_savedRingtonesNotModified) {
                        loadFromPrefs(true);
                    } else if (response instanceof TL_account.TL_savedRingtones) {
                        TL_account.TL_savedRingtones res = (TL_account.TL_savedRingtones) response;
                        saveTones(res.ringtones);
                        getSharedPreferences().edit()
                                .putLong("hash", queryHash = res.hash)
                                .putLong("lastReload", lastReloadTimeMs = System.currentTimeMillis())
                                .apply();
                    }
                    checkRingtoneSoundsLoaded();
                }
            }));
        } else {
            if (!loaded) {
                loadFromPrefs(true);
                loaded = true;
            }
            checkRingtoneSoundsLoaded();
        }
    }

    private void loadFromPrefs(boolean notify) {
        SharedPreferences preferences = getSharedPreferences();
        int count = preferences.getInt("count", 0);
        userRingtones.clear();
        for (int i = 0; i < count; ++i) {
            String value = preferences.getString("tone_document" + i, "");
            String localPath = preferences.getString("tone_local_path" + i, "");
            SerializedData serializedData = new SerializedData(Utilities.hexToBytes(value));
            try {
                TLRPC.Document document = TLRPC.Document.TLdeserialize(serializedData, serializedData.readInt32(true), true);
                CachedTone tone = new CachedTone();
                tone.document = document;
                tone.localUri = localPath;
                tone.localId = localIds++;
                userRingtones.add(tone);
            } catch (Throwable e) {
                if (BuildVars.DEBUG_PRIVATE_VERSION) {
                    throw e;
                }
                FileLog.e(e);
            }
        }
        if (notify) {
            AndroidUtilities.runOnUIThread(() -> {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.onUserRingtonesUpdated);
            });
        }
    }

    private void saveTones(ArrayList<TLRPC.Document> ringtones) {
        if (!loaded) {
            loadFromPrefs(false);
            loaded = true;
        }
        HashMap<Long, String> documentIdToLocalFilePath = new HashMap<>();
        for (CachedTone cachedTone : userRingtones) {
            if (cachedTone.localUri != null && cachedTone.document != null) {
                documentIdToLocalFilePath.put(cachedTone.document.id, cachedTone.localUri);
            }
        }
        userRingtones.clear();
        SharedPreferences preferences = getSharedPreferences();
        preferences.edit().clear().apply();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("count", ringtones.size());

        for (int i = 0; i < ringtones.size(); i++) {
            TLRPC.Document document = ringtones.get(i);
            String localPath = documentIdToLocalFilePath.get(document.id);
            SerializedData data = new SerializedData(document.getObjectSize());
            document.serializeToStream(data);
            editor.putString("tone_document" + i, Utilities.bytesToHex(data.toByteArray()));
            if (localPath != null) {
                editor.putString("tone_local_path" + i, localPath);
            }
            CachedTone tone = new CachedTone();
            tone.document = document;
            tone.localUri = localPath;
            tone.localId = localIds++;
            userRingtones.add(tone);
        }
        editor.apply();
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.onUserRingtonesUpdated);
    }

    public void saveTones() {
        SharedPreferences preferences = getSharedPreferences();
        preferences.edit().clear().apply();
        SharedPreferences.Editor editor = preferences.edit();

        int count = 0;
        for (int i = 0; i < userRingtones.size(); i++) {
            if (userRingtones.get(i).uploading) {
                continue;
            }
            count++;
            TLRPC.Document document = userRingtones.get(i).document;
            String localPath = userRingtones.get(i).localUri;
            SerializedData data = new SerializedData(document.getObjectSize());
            document.serializeToStream(data);
            editor.putString("tone_document" + i, Utilities.bytesToHex(data.toByteArray()));
            if (localPath != null) {
                editor.putString("tone_local_path" + i, localPath);
            }
        }

        editor.putInt("count", count);
        editor.apply();
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.onUserRingtonesUpdated);
    }


    private SharedPreferences getSharedPreferences() {
        if (prefName == null) {
            prefName = "ringtones_pref_" + clientUserId;
        }
        return ApplicationLoader.applicationContext.getSharedPreferences(prefName, Context.MODE_PRIVATE);
    }

    public void addUploadingTone(String filePath) {
        CachedTone cachedTone = new CachedTone();
        cachedTone.localUri = filePath;
        cachedTone.localId = localIds++;
        cachedTone.uploading = true;
        userRingtones.add(cachedTone);
    }

    public void onRingtoneUploaded(String filePath, TLRPC.Document document, boolean error) {
        boolean changed = false;
        if (error) {
            for (int i = 0; i < userRingtones.size(); i++) {
                if (userRingtones.get(i).uploading && filePath.equals(userRingtones.get(i).localUri)) {
                    userRingtones.remove(i);
                    changed = true;
                    break;
                }
            }
        } else {
            for (int i = 0; i < userRingtones.size(); i++) {
                if (userRingtones.get(i).uploading && filePath.equals(userRingtones.get(i).localUri)) {
                    userRingtones.get(i).uploading = false;
                    userRingtones.get(i).document = document;
                    changed = true;
                    break;
                }
            }
            if (changed) {
                saveTones();
            }
        }
        if (changed) {
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.onUserRingtonesUpdated);
        }
    }

    public String getSoundPath(long id) {
        if (!loaded) {
            loadFromPrefs(true);
            loaded = true;
        }
        for (int i = 0; i < userRingtones.size(); i++) {
            if (userRingtones.get(i).document != null && userRingtones.get(i).document.id == id) {
                if (!TextUtils.isEmpty(userRingtones.get(i).localUri)) {
                    return userRingtones.get(i).localUri;
                }
                return FileLoader.getInstance(currentAccount).getPathToAttach(userRingtones.get(i).document).toString();
            }
        }
        return "NoSound";
    }

    public void checkRingtoneSoundsLoaded() {
        if (!loaded) {
            loadFromPrefs(true);
            loaded = true;
        }
        final ArrayList<CachedTone> cachedTones = new ArrayList<>(userRingtones);
        Utilities.globalQueue.postRunnable(() -> {
            for (int i = 0; i < cachedTones.size(); i++) {
                CachedTone tone = cachedTones.get(i);
                if (tone == null) {
                    continue;
                }
                if (!TextUtils.isEmpty(tone.localUri)) {
                    File file = new File(tone.localUri);
                    if (file.exists()) {
                        continue;
                    }
                }

                if (tone.document != null) {
                    TLRPC.Document document = tone.document;
                    File file = FileLoader.getInstance(currentAccount).getPathToAttach(document);
                    if (file == null || !file.exists()) {
                        AndroidUtilities.runOnUIThread(() -> {
                            FileLoader.getInstance(currentAccount).loadFile(document, document, FileLoader.PRIORITY_LOW, 0);
                        });
                    }
                }
            }
        });

    }

    public boolean isLoaded() {
        return loaded;
    }

    public void remove(TLRPC.Document document) {
        if (document == null) {
            return;
        }
        if (!loaded) {
            loadFromPrefs(true);
            loaded = true;
        }
        for (int i = 0; i < userRingtones.size(); i++) {
            if (userRingtones.get(i).document != null && userRingtones.get(i).document.id == document.id) {
                userRingtones.remove(i);
                break;
            }
        }
    }

    public boolean contains(long id) {
        return getDocument(id) != null;
    }

    public void addTone(TLRPC.Document document) {
        if (document == null || contains(document.id)) {
            return;
        }
        CachedTone cachedTone = new CachedTone();
        cachedTone.document = document;
        cachedTone.localId = localIds++;
        cachedTone.uploading = false;
        userRingtones.add(cachedTone);
        saveTones();
    }

    public TLRPC.Document getDocument(long id) {
        if (!loaded) {
            loadFromPrefs(true);
            loaded = true;
        }
        try {
            for (int i = 0; i < userRingtones.size(); i++) {
                if (userRingtones.get(i) != null && userRingtones.get(i).document != null && userRingtones.get(i).document.id == id) {
                    return userRingtones.get(i).document;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public class CachedTone {
        public TLRPC.Document document;
        public String localUri;
        public int localId;
        public boolean uploading;
    }
}
