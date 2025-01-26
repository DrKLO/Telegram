package org.telegram.messenger;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

public class UnconfirmedAuthController {

    private final int currentAccount;

    public UnconfirmedAuthController(int currentAccount) {
        this.currentAccount = currentAccount;
        readCache();
    }

    public final ArrayList<UnconfirmedAuth> auths = new ArrayList<>();

    private boolean fetchedCache, fetchingCache;
    private boolean saveAfterFetch;

    public void readCache() {
        if (fetchedCache || fetchingCache) {
            return;
        }
        fetchingCache = true;
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            final HashSet<Long> hashes = new HashSet<>();
            final ArrayList<UnconfirmedAuth> result = new ArrayList<>();
            SQLiteDatabase database = MessagesStorage.getInstance(currentAccount).getDatabase();
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM unconfirmed_auth"));
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        try {
                            UnconfirmedAuth auth = new UnconfirmedAuth(data);
                            result.add(auth);
                            hashes.add(auth.hash);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                    cursor = null;
                }
            }

            AndroidUtilities.runOnUIThread(() -> {
                boolean wasEmpty = auths.isEmpty();
                for (int i = 0; i < auths.size(); ++i) {
                    UnconfirmedAuth existingAuth = auths.get(i);
                    if (existingAuth == null || existingAuth.expired() || hashes.contains(existingAuth.hash)) {
                        auths.remove(i);
                        i--;
                    }
                }
                auths.addAll(result);
                boolean isEmpty = auths.isEmpty();

                fetchedCache = true;
                fetchingCache = false;

                if (wasEmpty != isEmpty) {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.unconfirmedAuthUpdate);
                }
                scheduleAuthExpireCheck();

                if (saveAfterFetch) {
                    saveAfterFetch = false;
                    saveCache();
                }
            });
        });
    }

    private void scheduleAuthExpireCheck() {
        AndroidUtilities.cancelRunOnUIThread(checkExpiration);
        if (auths.isEmpty()) {
            return;
        }

        long minTime = Long.MAX_VALUE;
        for (UnconfirmedAuth auth : auths) {
            minTime = Math.min(minTime, auth.expiresAfter());
        }
        if (minTime == Long.MAX_VALUE) {
            return;
        }
        AndroidUtilities.runOnUIThread(checkExpiration, Math.max(0, minTime * 1000L));
    }

    private final Runnable checkExpiration = () -> {
        for (int i = 0; i < auths.size(); ++i) {
            UnconfirmedAuth auth = auths.get(i);
            if (auth.expired()) {
                auths.remove(i);
                i--;
            }
        }
        saveCache();
    };

    private boolean debug = false;
    public void putDebug() {
        debug = true;
        TLRPC.TL_updateNewAuthorization update = new TLRPC.TL_updateNewAuthorization();
        update.unconfirmed = true;
        update.device = "device";
        update.location = "location";
        update.hash = 123;
        processUpdate(update);
    }

    public void processUpdate(TLRPC.TL_updateNewAuthorization update) {
        for (int i = 0; i < auths.size(); ++i) {
            UnconfirmedAuth auth = auths.get(i);
            if (auth != null && auth.hash == update.hash) {
                auths.remove(i);
                i--;
            }
        }
        if (update.unconfirmed) {
            auths.add(new UnconfirmedAuth(update));
        }
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.unconfirmedAuthUpdate);
        scheduleAuthExpireCheck();
        saveCache();
    }

    private boolean savingCache;
    public void saveCache() {
        if (savingCache) {
            return;
        }
        if (fetchingCache) {
            saveAfterFetch = true;
            return;
        }
        savingCache = true;
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            SQLiteDatabase database = MessagesStorage.getInstance(currentAccount).getDatabase();
            SQLitePreparedStatement state = null;
            try {
                database.executeFast("DELETE FROM unconfirmed_auth WHERE 1").stepThis().dispose();
                state = database.executeFast("REPLACE INTO unconfirmed_auth VALUES(?)");
                for (UnconfirmedAuth auth : auths) {
                    state.requery();
                    NativeByteBuffer buffer = new NativeByteBuffer(auth.getObjectSize());
                    auth.serializeToStream(buffer);
                    state.bindByteBuffer(1, buffer);
                    state.step();
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (state != null) {
                    state.dispose();
                    state = null;
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                savingCache = false;
            });
        });
    }

    public void cleanup() {
        auths.clear();
        saveCache();
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.unconfirmedAuthUpdate);
        scheduleAuthExpireCheck();
    }

    private void updateList(boolean confirm, ArrayList<UnconfirmedAuth> _list, Utilities.Callback<ArrayList<UnconfirmedAuth>> whenDone) {
        final ArrayList<UnconfirmedAuth> list = new ArrayList<>(_list);
        final boolean[] results = new boolean[list.size()];
        final Utilities.Callback<Runnable>[] callbacks = new Utilities.Callback[list.size()];
        for (int i = 0; i < list.size(); ++i) {
            final int a = i;
            final UnconfirmedAuth auth = list.get(a);
            callbacks[a] = finish -> {
                Utilities.Callback<Boolean> next = success -> { results[a] = success; finish.run(); };
                if (confirm) {
                    auth.confirm(next);
                } else {
                    auth.deny(next);
                }
            };
        }
        Utilities.raceCallbacks(
            () -> {
                final HashSet<Long> hashes = new HashSet<>();
                final ArrayList<UnconfirmedAuth> success = new ArrayList<>();
                for (int i = 0; i < results.length; ++i) {
                    if (results[i]) {
                        UnconfirmedAuth auth = list.get(i);
                        success.add(auth);
                        hashes.add(auth.hash);
                    }
                }
                if (!confirm) {
                    for (int i = 0; i < auths.size(); ++i) {
                        if (hashes.contains(auths.get(i).hash)) {
                            auths.remove(i);
                            i--;
                        }
                    }
                    if (!hashes.isEmpty()) {
                        saveCache();
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.unconfirmedAuthUpdate);
                        scheduleAuthExpireCheck();
                    }
                }
                whenDone.run(success);
            },
            callbacks
        );
        if (confirm) {
            final HashSet<Long> hashes = new HashSet<>();
            for (int i = 0; i < list.size(); ++i) {
                hashes.add(list.get(i).hash);
            }
            for (int i = 0; i < auths.size(); ++i) {
                if (hashes.contains(auths.get(i).hash)) {
                    auths.remove(i);
                    i--;
                }
            }
            if (!hashes.isEmpty()) {
                saveCache();
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.unconfirmedAuthUpdate);
                scheduleAuthExpireCheck();
            }
        }
    }

    public void confirm(ArrayList<UnconfirmedAuth> list, Utilities.Callback<ArrayList<UnconfirmedAuth>> whenDone) {
        updateList(true, list, whenDone);
    }

    public void deny(ArrayList<UnconfirmedAuth> list, Utilities.Callback<ArrayList<UnconfirmedAuth>> whenDone) {
        updateList(false, list, whenDone);
    }

    public class UnconfirmedAuth extends TLObject {

        public long hash;
        public int date;
        public String device;
        public String location;


        public UnconfirmedAuth(AbstractSerializedData stream) {
            int magic = stream.readInt32(true);
            if (magic != 0x7ab6618c) {
                throw new RuntimeException("UnconfirmedAuth can't parse magic " + Integer.toHexString(magic));
            }
            hash = stream.readInt64(true);
            date = stream.readInt32(true);
            device = stream.readString(true);
            location = stream.readString(true);
        }

        public UnconfirmedAuth(TLRPC.TL_updateNewAuthorization update) {
            hash = update.hash;
            date = update.date;
            device = update.device;
            location = update.location;
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(0x7ab6618c);
            stream.writeInt64(hash);
            stream.writeInt32(date);
            stream.writeString(device);
            stream.writeString(location);
        }

        public long expiresAfter() {
            return ConnectionsManager.getInstance(currentAccount).getCurrentTime() + MessagesController.getInstance(currentAccount).authorizationAutoconfirmPeriod - date;
        }

        public boolean expired() {
            return expiresAfter() <= 0;
        }

        public void confirm(Utilities.Callback<Boolean> whenDone) {
            TL_account.changeAuthorizationSettings req = new TL_account.changeAuthorizationSettings();
            req.hash = hash;
            req.confirmed = true;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                AndroidUtilities.runOnUIThread(() -> {
                    if (whenDone != null) {
                        whenDone.run(res instanceof TLRPC.TL_boolTrue && err == null || debug);
                        debug = false;
                    }
                });
            });
        }

        public void deny(Utilities.Callback<Boolean> whenDone) {
            TL_account.resetAuthorization req = new TL_account.resetAuthorization();
            req.hash = hash;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                AndroidUtilities.runOnUIThread(() -> {
                    if (whenDone != null) {
                        whenDone.run(res instanceof TLRPC.TL_boolTrue && err == null || debug);
                        debug = false;
                    }
                });
            });
        }
    }
}
