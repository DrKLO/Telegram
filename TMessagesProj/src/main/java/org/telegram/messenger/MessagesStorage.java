/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.util.SparseIntArray;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.support.SparseLongArray;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class MessagesStorage extends BaseController {

    public interface IntCallback {
        void run(int param);
    }

    public interface BooleanCallback {
        void run(boolean param);
    }

    private DispatchQueue storageQueue = new DispatchQueue("storageQueue");
    private SQLiteDatabase database;
    private File cacheFile;
    private File walCacheFile;
    private File shmCacheFile;
    private AtomicLong lastTaskId = new AtomicLong(System.currentTimeMillis());

    private int lastDateValue = 0;
    private int lastPtsValue = 0;
    private int lastQtsValue = 0;
    private int lastSeqValue = 0;
    private int lastSecretVersion = 0;
    private byte[] secretPBytes = null;
    private int secretG = 0;

    private int lastSavedSeq = 0;
    private int lastSavedPts = 0;
    private int lastSavedDate = 0;
    private int lastSavedQts = 0;

    private CountDownLatch openSync = new CountDownLatch(1);

    private static volatile MessagesStorage[] Instance = new MessagesStorage[UserConfig.MAX_ACCOUNT_COUNT];
    private final static int LAST_DB_VERSION = 63;

    public static MessagesStorage getInstance(int num) {
        MessagesStorage localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (MessagesStorage.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new MessagesStorage(num);
                }
            }
        }
        return localInstance;
    }

    private void ensureOpened() {
        try {
            openSync.await();
        } catch (Throwable ignore) {

        }
    }

    public int getLastDateValue() {
        ensureOpened();
        return lastDateValue;
    }

    public void setLastDateValue(int value) {
        ensureOpened();
        lastDateValue = value;
    }

    public int getLastPtsValue() {
        ensureOpened();
        return lastPtsValue;
    }

    public void setLastPtsValue(int value) {
        ensureOpened();
        lastPtsValue = value;
    }

    public int getLastQtsValue() {
        ensureOpened();
        return lastQtsValue;
    }

    public void setLastQtsValue(int value) {
        ensureOpened();
        lastQtsValue = value;
    }

    public int getLastSeqValue() {
        ensureOpened();
        return lastSeqValue;
    }

    public void setLastSeqValue(int value) {
        ensureOpened();
        lastSeqValue = value;
    }

    public int getLastSecretVersion() {
        ensureOpened();
        return lastSecretVersion;
    }

    public void setLastSecretVersion(int value) {
        ensureOpened();
        lastSecretVersion = value;
    }

    public byte[] getSecretPBytes() {
        ensureOpened();
        return secretPBytes;
    }

    public void setSecretPBytes(byte[] value) {
        ensureOpened();
        secretPBytes = value;
    }

    public int getSecretG() {
        ensureOpened();
        return secretG;
    }

    public void setSecretG(int value) {
        ensureOpened();
        secretG = value;
    }

    public MessagesStorage(int instance) {
        super(instance);
        //storageQueue.setPriority(Thread.MAX_PRIORITY);
        storageQueue.postRunnable(() -> openDatabase(1));
    }

    public SQLiteDatabase getDatabase() {
        return database;
    }

    public DispatchQueue getStorageQueue() {
        return storageQueue;
    }

    public long getDatabaseSize() {
        long size = 0;
        if (cacheFile != null) {
            size += cacheFile.length();
        }
        if (shmCacheFile != null) {
            size += shmCacheFile.length();
        }
        /*if (walCacheFile != null) {
            size += walCacheFile.length();
        }*/
        return size;
    }

    public void openDatabase(int openTries) {
        File filesDir = ApplicationLoader.getFilesDirFixed();
        if (currentAccount != 0) {
            filesDir = new File(filesDir, "account" + currentAccount + "/");
            filesDir.mkdirs();
        }
        cacheFile = new File(filesDir, "cache4.db");
        walCacheFile = new File(filesDir, "cache4.db-wal");
        shmCacheFile = new File(filesDir, "cache4.db-shm");

        boolean createTable = false;
        //cacheFile.delete();
        if (!cacheFile.exists()) {
            createTable = true;
        }
        try {
            database = new SQLiteDatabase(cacheFile.getPath());
            database.executeFast("PRAGMA secure_delete = ON").stepThis().dispose();
            database.executeFast("PRAGMA temp_store = 1").stepThis().dispose();
            database.executeFast("PRAGMA journal_mode = WAL").stepThis().dispose();

            if (createTable) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("create new database");
                }
                database.executeFast("CREATE TABLE messages_holes(uid INTEGER, start INTEGER, end INTEGER, PRIMARY KEY(uid, start));").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS uid_end_messages_holes ON messages_holes(uid, end);").stepThis().dispose();

                database.executeFast("CREATE TABLE media_holes_v2(uid INTEGER, type INTEGER, start INTEGER, end INTEGER, PRIMARY KEY(uid, type, start));").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS uid_end_media_holes_v2 ON media_holes_v2(uid, type, end);").stepThis().dispose();

                database.executeFast("CREATE TABLE scheduled_messages(mid INTEGER PRIMARY KEY, uid INTEGER, send_state INTEGER, date INTEGER, data BLOB, ttl INTEGER, replydata BLOB)").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS send_state_idx_scheduled_messages ON scheduled_messages(mid, send_state, date);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS uid_date_idx_scheduled_messages ON scheduled_messages(uid, date);").stepThis().dispose();

                database.executeFast("CREATE TABLE messages(mid INTEGER PRIMARY KEY, uid INTEGER, read_state INTEGER, send_state INTEGER, date INTEGER, data BLOB, out INTEGER, ttl INTEGER, media INTEGER, replydata BLOB, imp INTEGER, mention INTEGER)").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_idx_messages ON messages(uid, mid);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS uid_date_mid_idx_messages ON messages(uid, date, mid);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS mid_out_idx_messages ON messages(mid, out);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS task_idx_messages ON messages(uid, out, read_state, ttl, date, send_state);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS send_state_idx_messages2 ON messages(mid, send_state, date);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS uid_mention_idx_messages ON messages(uid, mention, read_state);").stepThis().dispose();

                database.executeFast("CREATE TABLE download_queue(uid INTEGER, type INTEGER, date INTEGER, data BLOB, parent TEXT, PRIMARY KEY (uid, type));").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS type_date_idx_download_queue ON download_queue(type, date);").stepThis().dispose();

                database.executeFast("CREATE TABLE user_contacts_v7(key TEXT PRIMARY KEY, uid INTEGER, fname TEXT, sname TEXT, imported INTEGER)").stepThis().dispose();
                database.executeFast("CREATE TABLE user_phones_v7(key TEXT, phone TEXT, sphone TEXT, deleted INTEGER, PRIMARY KEY (key, phone))").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS sphone_deleted_idx_user_phones ON user_phones_v7(sphone, deleted);").stepThis().dispose();

                database.executeFast("CREATE TABLE dialogs(did INTEGER PRIMARY KEY, date INTEGER, unread_count INTEGER, last_mid INTEGER, inbox_max INTEGER, outbox_max INTEGER, last_mid_i INTEGER, unread_count_i INTEGER, pts INTEGER, date_i INTEGER, pinned INTEGER, flags INTEGER, folder_id INTEGER, data BLOB)").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS date_idx_dialogs ON dialogs(date);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS last_mid_idx_dialogs ON dialogs(last_mid);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS unread_count_idx_dialogs ON dialogs(unread_count);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS last_mid_i_idx_dialogs ON dialogs(last_mid_i);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS unread_count_i_idx_dialogs ON dialogs(unread_count_i);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS folder_id_idx_dialogs ON dialogs(folder_id);").stepThis().dispose();

                database.executeFast("CREATE TABLE randoms(random_id INTEGER, mid INTEGER, PRIMARY KEY (random_id, mid))").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS mid_idx_randoms ON randoms(mid);").stepThis().dispose();

                database.executeFast("CREATE TABLE enc_tasks_v2(mid INTEGER PRIMARY KEY, date INTEGER)").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS date_idx_enc_tasks_v2 ON enc_tasks_v2(date);").stepThis().dispose();

                database.executeFast("CREATE TABLE messages_seq(mid INTEGER PRIMARY KEY, seq_in INTEGER, seq_out INTEGER);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS seq_idx_messages_seq ON messages_seq(seq_in, seq_out);").stepThis().dispose();

                database.executeFast("CREATE TABLE params(id INTEGER PRIMARY KEY, seq INTEGER, pts INTEGER, date INTEGER, qts INTEGER, lsv INTEGER, sg INTEGER, pbytes BLOB)").stepThis().dispose();
                database.executeFast("INSERT INTO params VALUES(1, 0, 0, 0, 0, 0, 0, NULL)").stepThis().dispose();

                database.executeFast("CREATE TABLE media_v2(mid INTEGER PRIMARY KEY, uid INTEGER, date INTEGER, type INTEGER, data BLOB)").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_type_date_idx_media ON media_v2(uid, mid, type, date);").stepThis().dispose();

                database.executeFast("CREATE TABLE bot_keyboard(uid INTEGER PRIMARY KEY, mid INTEGER, info BLOB)").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS bot_keyboard_idx_mid ON bot_keyboard(mid);").stepThis().dispose();

                database.executeFast("CREATE TABLE chat_settings_v2(uid INTEGER PRIMARY KEY, info BLOB, pinned INTEGER, online INTEGER)").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS chat_settings_pinned_idx ON chat_settings_v2(uid, pinned) WHERE pinned != 0;").stepThis().dispose();

                database.executeFast("CREATE TABLE user_settings(uid INTEGER PRIMARY KEY, info BLOB, pinned INTEGER)").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS user_settings_pinned_idx ON user_settings(uid, pinned) WHERE pinned != 0;").stepThis().dispose();

                database.executeFast("CREATE TABLE chat_pinned(uid INTEGER PRIMARY KEY, pinned INTEGER, data BLOB)").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS chat_pinned_mid_idx ON chat_pinned(uid, pinned) WHERE pinned != 0;").stepThis().dispose();

                database.executeFast("CREATE TABLE chat_hints(did INTEGER, type INTEGER, rating REAL, date INTEGER, PRIMARY KEY(did, type))").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS chat_hints_rating_idx ON chat_hints(rating);").stepThis().dispose();

                database.executeFast("CREATE TABLE botcache(id TEXT PRIMARY KEY, date INTEGER, data BLOB)").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS botcache_date_idx ON botcache(date);").stepThis().dispose();

                database.executeFast("CREATE TABLE users_data(uid INTEGER PRIMARY KEY, about TEXT)").stepThis().dispose();
                database.executeFast("CREATE TABLE users(uid INTEGER PRIMARY KEY, name TEXT, status INTEGER, data BLOB)").stepThis().dispose();
                database.executeFast("CREATE TABLE chats(uid INTEGER PRIMARY KEY, name TEXT, data BLOB)").stepThis().dispose();
                database.executeFast("CREATE TABLE enc_chats(uid INTEGER PRIMARY KEY, user INTEGER, name TEXT, data BLOB, g BLOB, authkey BLOB, ttl INTEGER, layer INTEGER, seq_in INTEGER, seq_out INTEGER, use_count INTEGER, exchange_id INTEGER, key_date INTEGER, fprint INTEGER, fauthkey BLOB, khash BLOB, in_seq_no INTEGER, admin_id INTEGER, mtproto_seq INTEGER)").stepThis().dispose();
                database.executeFast("CREATE TABLE channel_users_v2(did INTEGER, uid INTEGER, date INTEGER, data BLOB, PRIMARY KEY(did, uid))").stepThis().dispose();
                database.executeFast("CREATE TABLE channel_admins_v2(did INTEGER, uid INTEGER, rank TEXT, PRIMARY KEY(did, uid))").stepThis().dispose();
                database.executeFast("CREATE TABLE contacts(uid INTEGER PRIMARY KEY, mutual INTEGER)").stepThis().dispose();
                database.executeFast("CREATE TABLE user_photos(uid INTEGER, id INTEGER, data BLOB, PRIMARY KEY (uid, id))").stepThis().dispose();
                database.executeFast("CREATE TABLE dialog_settings(did INTEGER PRIMARY KEY, flags INTEGER);").stepThis().dispose();
                database.executeFast("CREATE TABLE web_recent_v3(id TEXT, type INTEGER, image_url TEXT, thumb_url TEXT, local_url TEXT, width INTEGER, height INTEGER, size INTEGER, date INTEGER, document BLOB, PRIMARY KEY (id, type));").stepThis().dispose();
                database.executeFast("CREATE TABLE stickers_v2(id INTEGER PRIMARY KEY, data BLOB, date INTEGER, hash TEXT);").stepThis().dispose();
                database.executeFast("CREATE TABLE stickers_featured(id INTEGER PRIMARY KEY, data BLOB, unread BLOB, date INTEGER, hash TEXT);").stepThis().dispose();
                database.executeFast("CREATE TABLE hashtag_recent_v2(id TEXT PRIMARY KEY, date INTEGER);").stepThis().dispose();
                database.executeFast("CREATE TABLE webpage_pending(id INTEGER, mid INTEGER, PRIMARY KEY (id, mid));").stepThis().dispose();
                database.executeFast("CREATE TABLE sent_files_v2(uid TEXT, type INTEGER, data BLOB, parent TEXT, PRIMARY KEY (uid, type))").stepThis().dispose();
                database.executeFast("CREATE TABLE search_recent(did INTEGER PRIMARY KEY, date INTEGER);").stepThis().dispose();
                database.executeFast("CREATE TABLE media_counts_v2(uid INTEGER, type INTEGER, count INTEGER, old INTEGER, PRIMARY KEY(uid, type))").stepThis().dispose();
                database.executeFast("CREATE TABLE keyvalue(id TEXT PRIMARY KEY, value TEXT)").stepThis().dispose();
                database.executeFast("CREATE TABLE bot_info(uid INTEGER PRIMARY KEY, info BLOB)").stepThis().dispose();
                database.executeFast("CREATE TABLE pending_tasks(id INTEGER PRIMARY KEY, data BLOB);").stepThis().dispose();
                database.executeFast("CREATE TABLE requested_holes(uid INTEGER, seq_out_start INTEGER, seq_out_end INTEGER, PRIMARY KEY (uid, seq_out_start, seq_out_end));").stepThis().dispose();
                database.executeFast("CREATE TABLE sharing_locations(uid INTEGER PRIMARY KEY, mid INTEGER, date INTEGER, period INTEGER, message BLOB);").stepThis().dispose();

                database.executeFast("CREATE TABLE emoji_keywords_v2(lang TEXT, keyword TEXT, emoji TEXT, PRIMARY KEY(lang, keyword, emoji));").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS emoji_keywords_v2_keyword ON emoji_keywords_v2(keyword);").stepThis().dispose();
                database.executeFast("CREATE TABLE emoji_keywords_info_v2(lang TEXT PRIMARY KEY, alias TEXT, version INTEGER, date INTEGER);").stepThis().dispose();

                database.executeFast("CREATE TABLE wallpapers2(uid INTEGER PRIMARY KEY, data BLOB, num INTEGER)").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS wallpapers_num ON wallpapers2(num);").stepThis().dispose();

                database.executeFast("CREATE TABLE unread_push_messages(uid INTEGER, mid INTEGER, random INTEGER, date INTEGER, data BLOB, fm TEXT, name TEXT, uname TEXT, flags INTEGER, PRIMARY KEY(uid, mid))").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS unread_push_messages_idx_date ON unread_push_messages(date);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS unread_push_messages_idx_random ON unread_push_messages(random);").stepThis().dispose();

                database.executeFast("CREATE TABLE polls(mid INTEGER PRIMARY KEY, id INTEGER);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS polls_id ON polls(id);").stepThis().dispose();

                //version
                database.executeFast("PRAGMA user_version = " + LAST_DB_VERSION).stepThis().dispose();

                //database.executeFast("CREATE TABLE secret_holes(uid INTEGER, seq_in INTEGER, seq_out INTEGER, data BLOB, PRIMARY KEY (uid, seq_in, seq_out));").stepThis().dispose();
                //database.executeFast("CREATE TABLE attach_data(uid INTEGER, id INTEGER, data BLOB, PRIMARY KEY (uid, id))").stepThis().dispose();
            } else {
                int version = database.executeInt("PRAGMA user_version");
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("current db version = " + version);
                }
                if (version == 0) {
                    throw new Exception("malformed");
                }
                try {
                    SQLiteCursor cursor = database.queryFinalized("SELECT seq, pts, date, qts, lsv, sg, pbytes FROM params WHERE id = 1");
                    if (cursor.next()) {
                        lastSeqValue = cursor.intValue(0);
                        lastPtsValue = cursor.intValue(1);
                        lastDateValue = cursor.intValue(2);
                        lastQtsValue = cursor.intValue(3);
                        lastSecretVersion = cursor.intValue(4);
                        secretG = cursor.intValue(5);
                        if (cursor.isNull(6)) {
                            secretPBytes = null;
                        } else {
                            secretPBytes = cursor.byteArrayValue(6);
                            if (secretPBytes != null && secretPBytes.length == 1) {
                                secretPBytes = null;
                            }
                        }
                    }
                    cursor.dispose();
                } catch (Exception e) {
                    FileLog.e(e);
                    try {
                        database.executeFast("CREATE TABLE IF NOT EXISTS params(id INTEGER PRIMARY KEY, seq INTEGER, pts INTEGER, date INTEGER, qts INTEGER, lsv INTEGER, sg INTEGER, pbytes BLOB)").stepThis().dispose();
                        database.executeFast("INSERT INTO params VALUES(1, 0, 0, 0, 0, 0, 0, NULL)").stepThis().dispose();
                    } catch (Exception e2) {
                        FileLog.e(e2);
                    }
                }
                if (version < LAST_DB_VERSION) {
                    updateDbToLastVersion(version);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);

            if (openTries < 3 && e.getMessage().contains("malformed")) {
                if (openTries == 2) {
                    cleanupInternal(true);
                    for (int a = 0; a < 2; a++) {
                        getUserConfig().setDialogsLoadOffset(a, 0, 0, 0, 0, 0, 0);
                        getUserConfig().setTotalDialogsCount(a, 0);
                    }
                    getUserConfig().saveConfig(false);
                } else {
                    cleanupInternal(false);
                }
                openDatabase(openTries == 1 ? 2 : 3);
            }
        }
        loadUnreadMessages();
        loadPendingTasks();
        try {
            openSync.countDown();
        } catch (Throwable ignore) {

        }
    }

    private void updateDbToLastVersion(final int currentVersion) {
        storageQueue.postRunnable(() -> {
            try {
                int version = currentVersion;
                if (version < 4) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS user_photos(uid INTEGER, id INTEGER, data BLOB, PRIMARY KEY (uid, id))").stepThis().dispose();

                    database.executeFast("DROP INDEX IF EXISTS read_state_out_idx_messages;").stepThis().dispose();
                    database.executeFast("DROP INDEX IF EXISTS ttl_idx_messages;").stepThis().dispose();
                    database.executeFast("DROP INDEX IF EXISTS date_idx_messages;").stepThis().dispose();

                    database.executeFast("CREATE INDEX IF NOT EXISTS mid_out_idx_messages ON messages(mid, out);").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS task_idx_messages ON messages(uid, out, read_state, ttl, date, send_state);").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS uid_date_mid_idx_messages ON messages(uid, date, mid);").stepThis().dispose();

                    database.executeFast("CREATE TABLE IF NOT EXISTS user_contacts_v6(uid INTEGER PRIMARY KEY, fname TEXT, sname TEXT)").stepThis().dispose();
                    database.executeFast("CREATE TABLE IF NOT EXISTS user_phones_v6(uid INTEGER, phone TEXT, sphone TEXT, deleted INTEGER, PRIMARY KEY (uid, phone))").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS sphone_deleted_idx_user_phones ON user_phones_v6(sphone, deleted);").stepThis().dispose();

                    database.executeFast("CREATE INDEX IF NOT EXISTS mid_idx_randoms ON randoms(mid);").stepThis().dispose();

                    database.executeFast("CREATE TABLE IF NOT EXISTS sent_files_v2(uid TEXT, type INTEGER, data BLOB, PRIMARY KEY (uid, type))").stepThis().dispose();

                    database.executeFast("CREATE TABLE IF NOT EXISTS download_queue(uid INTEGER, type INTEGER, date INTEGER, data BLOB, PRIMARY KEY (uid, type));").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS type_date_idx_download_queue ON download_queue(type, date);").stepThis().dispose();

                    database.executeFast("CREATE TABLE IF NOT EXISTS dialog_settings(did INTEGER PRIMARY KEY, flags INTEGER);").stepThis().dispose();

                    database.executeFast("CREATE INDEX IF NOT EXISTS unread_count_idx_dialogs ON dialogs(unread_count);").stepThis().dispose();

                    database.executeFast("UPDATE messages SET send_state = 2 WHERE mid < 0 AND send_state = 1").stepThis().dispose();

                    fixNotificationSettings();
                    database.executeFast("PRAGMA user_version = 4").stepThis().dispose();
                    version = 4;
                }
                if (version == 4) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS enc_tasks_v2(mid INTEGER PRIMARY KEY, date INTEGER)").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS date_idx_enc_tasks_v2 ON enc_tasks_v2(date);").stepThis().dispose();
                    database.beginTransaction();
                    SQLiteCursor cursor = database.queryFinalized("SELECT date, data FROM enc_tasks WHERE 1");
                    SQLitePreparedStatement state = database.executeFast("REPLACE INTO enc_tasks_v2 VALUES(?, ?)");
                    if (cursor.next()) {
                        int date = cursor.intValue(0);
                        NativeByteBuffer data = cursor.byteBufferValue(1);
                        if (data != null) {
                            int length = data.limit();
                            for (int a = 0; a < length / 4; a++) {
                                state.requery();
                                state.bindInteger(1, data.readInt32(false));
                                state.bindInteger(2, date);
                                state.step();
                            }
                            data.reuse();
                        }
                    }
                    state.dispose();
                    cursor.dispose();
                    database.commitTransaction();

                    database.executeFast("DROP INDEX IF EXISTS date_idx_enc_tasks;").stepThis().dispose();
                    database.executeFast("DROP TABLE IF EXISTS enc_tasks;").stepThis().dispose();

                    database.executeFast("ALTER TABLE messages ADD COLUMN media INTEGER default 0").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 6").stepThis().dispose();
                    version = 6;
                }
                if (version == 6) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS messages_seq(mid INTEGER PRIMARY KEY, seq_in INTEGER, seq_out INTEGER);").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS seq_idx_messages_seq ON messages_seq(seq_in, seq_out);").stepThis().dispose();
                    database.executeFast("ALTER TABLE enc_chats ADD COLUMN layer INTEGER default 0").stepThis().dispose();
                    database.executeFast("ALTER TABLE enc_chats ADD COLUMN seq_in INTEGER default 0").stepThis().dispose();
                    database.executeFast("ALTER TABLE enc_chats ADD COLUMN seq_out INTEGER default 0").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 7").stepThis().dispose();
                    version = 7;
                }
                /*if (version == 7 && version < 8) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS secret_holes(uid INTEGER, seq_in INTEGER, seq_out INTEGER, data BLOB, PRIMARY KEY (uid, seq_in, seq_out));").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 8").stepThis().dispose();
                    version = 8;
                }*/
                if (version == 7 || version == 8 || version == 9) {
                    database.executeFast("ALTER TABLE enc_chats ADD COLUMN use_count INTEGER default 0").stepThis().dispose();
                    database.executeFast("ALTER TABLE enc_chats ADD COLUMN exchange_id INTEGER default 0").stepThis().dispose();
                    database.executeFast("ALTER TABLE enc_chats ADD COLUMN key_date INTEGER default 0").stepThis().dispose();
                    database.executeFast("ALTER TABLE enc_chats ADD COLUMN fprint INTEGER default 0").stepThis().dispose();
                    database.executeFast("ALTER TABLE enc_chats ADD COLUMN fauthkey BLOB default NULL").stepThis().dispose();
                    database.executeFast("ALTER TABLE enc_chats ADD COLUMN khash BLOB default NULL").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 10").stepThis().dispose();
                    version = 10;
                }
                if (version == 10) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS web_recent_v3(id TEXT, type INTEGER, image_url TEXT, thumb_url TEXT, local_url TEXT, width INTEGER, height INTEGER, size INTEGER, date INTEGER, PRIMARY KEY (id, type));").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 11").stepThis().dispose();
                    version = 11;
                }
                if (version == 11 || version == 12) {
                    database.executeFast("DROP INDEX IF EXISTS uid_mid_idx_media;").stepThis().dispose();
                    database.executeFast("DROP INDEX IF EXISTS mid_idx_media;").stepThis().dispose();
                    database.executeFast("DROP INDEX IF EXISTS uid_date_mid_idx_media;").stepThis().dispose();
                    database.executeFast("DROP TABLE IF EXISTS media;").stepThis().dispose();
                    database.executeFast("DROP TABLE IF EXISTS media_counts;").stepThis().dispose();

                    database.executeFast("CREATE TABLE IF NOT EXISTS media_v2(mid INTEGER PRIMARY KEY, uid INTEGER, date INTEGER, type INTEGER, data BLOB)").stepThis().dispose();
                    database.executeFast("CREATE TABLE IF NOT EXISTS media_counts_v2(uid INTEGER, type INTEGER, count INTEGER, PRIMARY KEY(uid, type))").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_type_date_idx_media ON media_v2(uid, mid, type, date);").stepThis().dispose();

                    database.executeFast("CREATE TABLE IF NOT EXISTS keyvalue(id TEXT PRIMARY KEY, value TEXT)").stepThis().dispose();

                    database.executeFast("PRAGMA user_version = 13").stepThis().dispose();
                    version = 13;
                }
                if (version == 13) {
                    database.executeFast("ALTER TABLE messages ADD COLUMN replydata BLOB default NULL").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 14").stepThis().dispose();
                    version = 14;
                }
                if (version == 14) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS hashtag_recent_v2(id TEXT PRIMARY KEY, date INTEGER);").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 15").stepThis().dispose();
                    version = 15;
                }
                if (version == 15) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS webpage_pending(id INTEGER, mid INTEGER, PRIMARY KEY (id, mid));").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 16").stepThis().dispose();
                    version = 16;
                }
                if (version == 16) {
                    database.executeFast("ALTER TABLE dialogs ADD COLUMN inbox_max INTEGER default 0").stepThis().dispose();
                    database.executeFast("ALTER TABLE dialogs ADD COLUMN outbox_max INTEGER default 0").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 17").stepThis().dispose();
                    version = 17;
                }
                if (version == 17) {
                    database.executeFast("CREATE TABLE bot_info(uid INTEGER PRIMARY KEY, info BLOB)").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 18").stepThis().dispose();
                    version = 18;
                }
                if (version == 18) {
                    database.executeFast("DROP TABLE IF EXISTS stickers;").stepThis().dispose();
                    database.executeFast("CREATE TABLE IF NOT EXISTS stickers_v2(id INTEGER PRIMARY KEY, data BLOB, date INTEGER, hash TEXT);").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 19").stepThis().dispose();
                    version = 19;
                }
                if (version == 19) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS bot_keyboard(uid INTEGER PRIMARY KEY, mid INTEGER, info BLOB)").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS bot_keyboard_idx_mid ON bot_keyboard(mid);").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 20").stepThis().dispose();
                    version = 20;
                }
                if (version == 20) {
                    database.executeFast("CREATE TABLE search_recent(did INTEGER PRIMARY KEY, date INTEGER);").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 21").stepThis().dispose();
                    version = 21;
                }
                if (version == 21) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS chat_settings_v2(uid INTEGER PRIMARY KEY, info BLOB)").stepThis().dispose();

                    SQLiteCursor cursor = database.queryFinalized("SELECT uid, participants FROM chat_settings WHERE uid < 0");
                    SQLitePreparedStatement state = database.executeFast("REPLACE INTO chat_settings_v2 VALUES(?, ?)");
                    while (cursor.next()) {
                        int chat_id = cursor.intValue(0);
                        NativeByteBuffer data = cursor.byteBufferValue(1);
                        if (data != null) {
                            TLRPC.ChatParticipants participants = TLRPC.ChatParticipants.TLdeserialize(data, data.readInt32(false), false);
                            data.reuse();
                            if (participants != null) {
                                TLRPC.TL_chatFull chatFull = new TLRPC.TL_chatFull();
                                chatFull.id = chat_id;
                                chatFull.chat_photo = new TLRPC.TL_photoEmpty();
                                chatFull.notify_settings = new TLRPC.TL_peerNotifySettingsEmpty_layer77();
                                chatFull.exported_invite = new TLRPC.TL_chatInviteEmpty();
                                chatFull.participants = participants;
                                NativeByteBuffer data2 = new NativeByteBuffer(chatFull.getObjectSize());
                                chatFull.serializeToStream(data2);
                                state.requery();
                                state.bindInteger(1, chat_id);
                                state.bindByteBuffer(2, data2);
                                state.step();
                                data2.reuse();
                            }
                        }
                    }
                    state.dispose();
                    cursor.dispose();

                    database.executeFast("DROP TABLE IF EXISTS chat_settings;").stepThis().dispose();
                    database.executeFast("ALTER TABLE dialogs ADD COLUMN last_mid_i INTEGER default 0").stepThis().dispose();
                    database.executeFast("ALTER TABLE dialogs ADD COLUMN unread_count_i INTEGER default 0").stepThis().dispose();
                    database.executeFast("ALTER TABLE dialogs ADD COLUMN pts INTEGER default 0").stepThis().dispose();
                    database.executeFast("ALTER TABLE dialogs ADD COLUMN date_i INTEGER default 0").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS last_mid_i_idx_dialogs ON dialogs(last_mid_i);").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS unread_count_i_idx_dialogs ON dialogs(unread_count_i);").stepThis().dispose();
                    database.executeFast("ALTER TABLE messages ADD COLUMN imp INTEGER default 0").stepThis().dispose();
                    database.executeFast("CREATE TABLE IF NOT EXISTS messages_holes(uid INTEGER, start INTEGER, end INTEGER, PRIMARY KEY(uid, start));").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS uid_end_messages_holes ON messages_holes(uid, end);").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 22").stepThis().dispose();
                    version = 22;
                }
                if (version == 22) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS media_holes_v2(uid INTEGER, type INTEGER, start INTEGER, end INTEGER, PRIMARY KEY(uid, type, start));").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS uid_end_media_holes_v2 ON media_holes_v2(uid, type, end);").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 23").stepThis().dispose();
                    version = 23;
                }
                if (version == 23 || version == 24) {
                    database.executeFast("DELETE FROM media_holes_v2 WHERE uid != 0 AND type >= 0 AND start IN (0, 1)").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 25").stepThis().dispose();
                    version = 25;
                }
                if (version == 25 || version == 26) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS channel_users_v2(did INTEGER, uid INTEGER, date INTEGER, data BLOB, PRIMARY KEY(did, uid))").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 27").stepThis().dispose();
                    version = 27;
                }
                if (version == 27) {
                    database.executeFast("ALTER TABLE web_recent_v3 ADD COLUMN document BLOB default NULL").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 28").stepThis().dispose();
                    version = 28;
                }
                if (version == 28 || version == 29) {
                    database.executeFast("DELETE FROM sent_files_v2 WHERE 1").stepThis().dispose();
                    database.executeFast("DELETE FROM download_queue WHERE 1").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 30").stepThis().dispose();
                    version = 30;
                }
                if (version == 30) {
                    database.executeFast("ALTER TABLE chat_settings_v2 ADD COLUMN pinned INTEGER default 0").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS chat_settings_pinned_idx ON chat_settings_v2(uid, pinned) WHERE pinned != 0;").stepThis().dispose();
                    database.executeFast("CREATE TABLE IF NOT EXISTS chat_pinned(uid INTEGER PRIMARY KEY, pinned INTEGER, data BLOB)").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS chat_pinned_mid_idx ON chat_pinned(uid, pinned) WHERE pinned != 0;").stepThis().dispose();
                    database.executeFast("CREATE TABLE IF NOT EXISTS users_data(uid INTEGER PRIMARY KEY, about TEXT)").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 31").stepThis().dispose();
                    version = 31;
                }
                if (version == 31) {
                    database.executeFast("DROP TABLE IF EXISTS bot_recent;").stepThis().dispose();
                    database.executeFast("CREATE TABLE IF NOT EXISTS chat_hints(did INTEGER, type INTEGER, rating REAL, date INTEGER, PRIMARY KEY(did, type))").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS chat_hints_rating_idx ON chat_hints(rating);").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 32").stepThis().dispose();
                    version = 32;
                }
                if (version == 32) {
                    database.executeFast("DROP INDEX IF EXISTS uid_mid_idx_imp_messages;").stepThis().dispose();
                    database.executeFast("DROP INDEX IF EXISTS uid_date_mid_imp_idx_messages;").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 33").stepThis().dispose();
                    version = 33;
                }
                if (version == 33) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS pending_tasks(id INTEGER PRIMARY KEY, data BLOB);").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 34").stepThis().dispose();
                    version = 34;
                }
                if (version == 34) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS stickers_featured(id INTEGER PRIMARY KEY, data BLOB, unread BLOB, date INTEGER, hash TEXT);").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 35").stepThis().dispose();
                    version = 35;
                }
                if (version == 35) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS requested_holes(uid INTEGER, seq_out_start INTEGER, seq_out_end INTEGER, PRIMARY KEY (uid, seq_out_start, seq_out_end));").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 36").stepThis().dispose();
                    version = 36;
                }
                if (version == 36) {
                    database.executeFast("ALTER TABLE enc_chats ADD COLUMN in_seq_no INTEGER default 0").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 37").stepThis().dispose();
                    version = 37;
                }
                if (version == 37) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS botcache(id TEXT PRIMARY KEY, date INTEGER, data BLOB)").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS botcache_date_idx ON botcache(date);").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 38").stepThis().dispose();
                    version = 38;
                }
                if (version == 38) {
                    database.executeFast("ALTER TABLE dialogs ADD COLUMN pinned INTEGER default 0").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 39").stepThis().dispose();
                    version = 39;
                }
                if (version == 39) {
                    database.executeFast("ALTER TABLE enc_chats ADD COLUMN admin_id INTEGER default 0").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 40").stepThis().dispose();
                    version = 40;
                }
                if (version == 40) {
                    fixNotificationSettings();
                    database.executeFast("PRAGMA user_version = 41").stepThis().dispose();
                    version = 41;
                }
                if (version == 41) {
                    database.executeFast("ALTER TABLE messages ADD COLUMN mention INTEGER default 0").stepThis().dispose();
                    database.executeFast("ALTER TABLE user_contacts_v6 ADD COLUMN imported INTEGER default 0").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS uid_mention_idx_messages ON messages(uid, mention, read_state);").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 42").stepThis().dispose();
                    version = 42;
                }
                if (version == 42) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS sharing_locations(uid INTEGER PRIMARY KEY, mid INTEGER, date INTEGER, period INTEGER, message BLOB);").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 43").stepThis().dispose();
                    version = 43;
                }
                if (version == 43) {
                    database.executeFast("PRAGMA user_version = 44").stepThis().dispose();
                    version = 44;
                }
                if (version == 44) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS user_contacts_v7(key TEXT PRIMARY KEY, uid INTEGER, fname TEXT, sname TEXT, imported INTEGER)").stepThis().dispose();
                    database.executeFast("CREATE TABLE IF NOT EXISTS user_phones_v7(key TEXT, phone TEXT, sphone TEXT, deleted INTEGER, PRIMARY KEY (key, phone))").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS sphone_deleted_idx_user_phones ON user_phones_v7(sphone, deleted);").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 45").stepThis().dispose();
                    version = 45;
                }
                if (version == 45) {
                    database.executeFast("ALTER TABLE enc_chats ADD COLUMN mtproto_seq INTEGER default 0").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 46").stepThis().dispose();
                    version = 46;
                }
                if (version == 46) {
                    database.executeFast("DELETE FROM botcache WHERE 1").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 47").stepThis().dispose();
                    version = 47;
                }
                if (version == 47) {
                    database.executeFast("ALTER TABLE dialogs ADD COLUMN flags INTEGER default 0").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 48").stepThis().dispose();
                    version = 48;
                }
                if (version == 48) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS unread_push_messages(uid INTEGER, mid INTEGER, random INTEGER, date INTEGER, data BLOB, fm TEXT, name TEXT, uname TEXT, flags INTEGER, PRIMARY KEY(uid, mid))").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS unread_push_messages_idx_date ON unread_push_messages(date);").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS unread_push_messages_idx_random ON unread_push_messages(random);").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 49").stepThis().dispose();
                    version = 49;
                }
                if (version == 49) {
                    database.executeFast("DELETE FROM chat_pinned WHERE uid = 1").stepThis().dispose();
                    database.executeFast("CREATE TABLE IF NOT EXISTS user_settings(uid INTEGER PRIMARY KEY, info BLOB, pinned INTEGER)").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS user_settings_pinned_idx ON user_settings(uid, pinned) WHERE pinned != 0;").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 50").stepThis().dispose();
                    version = 50;
                }
                if (version == 50) {
                    database.executeFast("DELETE FROM sent_files_v2 WHERE 1").stepThis().dispose();
                    database.executeFast("ALTER TABLE sent_files_v2 ADD COLUMN parent TEXT").stepThis().dispose();
                    database.executeFast("DELETE FROM download_queue WHERE 1").stepThis().dispose();
                    database.executeFast("ALTER TABLE download_queue ADD COLUMN parent TEXT").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 51").stepThis().dispose();
                    version = 51;
                }
                if (version == 51) {
                    database.executeFast("ALTER TABLE media_counts_v2 ADD COLUMN old INTEGER").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 52").stepThis().dispose();
                    version = 52;
                }
                if (version == 52) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS polls(mid INTEGER PRIMARY KEY, id INTEGER);").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS polls_id ON polls(id);").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 53").stepThis().dispose();
                    version = 53;
                }
                if (version == 53) {
                    database.executeFast("ALTER TABLE chat_settings_v2 ADD COLUMN online INTEGER default 0").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 54").stepThis().dispose();
                    version = 54;
                }
                if (version == 54) {
                    database.executeFast("DROP TABLE IF EXISTS wallpapers;").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 55").stepThis().dispose();
                    version = 55;
                }
                if (version == 55) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS wallpapers2(uid INTEGER PRIMARY KEY, data BLOB, num INTEGER)").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS wallpapers_num ON wallpapers2(num);").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 56").stepThis().dispose();
                    version = 56;
                }
                if (version == 56 || version == 57) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS emoji_keywords_v2(lang TEXT, keyword TEXT, emoji TEXT, PRIMARY KEY(lang, keyword, emoji));").stepThis().dispose();
                    database.executeFast("CREATE TABLE IF NOT EXISTS emoji_keywords_info_v2(lang TEXT PRIMARY KEY, alias TEXT, version INTEGER);").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 58").stepThis().dispose();
                    version = 58;
                }
                if (version == 58) {
                    database.executeFast("CREATE INDEX IF NOT EXISTS emoji_keywords_v2_keyword ON emoji_keywords_v2(keyword);").stepThis().dispose();
                    database.executeFast("ALTER TABLE emoji_keywords_info_v2 ADD COLUMN date INTEGER default 0").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 59").stepThis().dispose();
                    version = 59;
                }
                if (version == 59) {
                    database.executeFast("ALTER TABLE dialogs ADD COLUMN folder_id INTEGER default 0").stepThis().dispose();
                    database.executeFast("ALTER TABLE dialogs ADD COLUMN data BLOB default NULL").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS folder_id_idx_dialogs ON dialogs(folder_id);").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 60").stepThis().dispose();
                    version = 60;
                }
                if (version == 60) {
                    database.executeFast("DROP TABLE IF EXISTS channel_admins;").stepThis().dispose();
                    database.executeFast("DROP TABLE IF EXISTS blocked_users;").stepThis().dispose();
                    database.executeFast("CREATE TABLE IF NOT EXISTS channel_admins_v2(did INTEGER, uid INTEGER, rank TEXT, PRIMARY KEY(did, uid))").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 61").stepThis().dispose();
                    version = 61;
                }
                if (version == 61) {
                    database.executeFast("DROP INDEX IF EXISTS send_state_idx_messages;").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS send_state_idx_messages2 ON messages(mid, send_state, date);").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 62").stepThis().dispose();
                    version = 62;
                }
                if (version == 62) {
                    database.executeFast("CREATE TABLE IF NOT EXISTS scheduled_messages(mid INTEGER PRIMARY KEY, uid INTEGER, send_state INTEGER, date INTEGER, data BLOB, ttl INTEGER, replydata BLOB)").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS send_state_idx_scheduled_messages ON scheduled_messages(mid, send_state, date);").stepThis().dispose();
                    database.executeFast("CREATE INDEX IF NOT EXISTS uid_date_idx_scheduled_messages ON scheduled_messages(uid, date);").stepThis().dispose();
                    database.executeFast("PRAGMA user_version = 63").stepThis().dispose();
                    version = 63;
                }
                if (version == 63) {

                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void cleanupInternal(boolean deleteFiles) {
        lastDateValue = 0;
        lastSeqValue = 0;
        lastPtsValue = 0;
        lastQtsValue = 0;
        lastSecretVersion = 0;

        lastSavedSeq = 0;
        lastSavedPts = 0;
        lastSavedDate = 0;
        lastSavedQts = 0;

        secretPBytes = null;
        secretG = 0;
        if (database != null) {
            database.close();
            database = null;
        }
        if (deleteFiles) {
            if (cacheFile != null) {
                cacheFile.delete();
                cacheFile = null;
            }
            if (walCacheFile != null) {
                walCacheFile.delete();
                walCacheFile = null;
            }
            if (shmCacheFile != null) {
                shmCacheFile.delete();
                shmCacheFile = null;
            }
        }
    }

    public void cleanup(final boolean isLogin) {
        if (!isLogin) {
            storageQueue.cleanupQueue();
        }
        storageQueue.postRunnable(() -> {
            cleanupInternal(true);
            openDatabase(1);
            if (isLogin) {
                Utilities.stageQueue.postRunnable(() -> getMessagesController().getDifference());
            }
        });
    }

    public void saveSecretParams(final int lsv, final int sg, final byte[] pbytes) {
        storageQueue.postRunnable(() -> {
            try {
                SQLitePreparedStatement state = database.executeFast("UPDATE params SET lsv = ?, sg = ?, pbytes = ? WHERE id = 1");
                state.bindInteger(1, lsv);
                state.bindInteger(2, sg);
                NativeByteBuffer data = new NativeByteBuffer(pbytes != null ? pbytes.length : 1);
                if (pbytes != null) {
                    data.writeBytes(pbytes);
                }
                state.bindByteBuffer(3, data);
                state.step();
                state.dispose();
                data.reuse();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void fixNotificationSettings() {
        storageQueue.postRunnable(() -> {
            try {
                LongSparseArray<Long> ids = new LongSparseArray<>();
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                Map<String, ?> values = preferences.getAll();
                for (Map.Entry<String, ?> entry : values.entrySet()) {
                    String key = entry.getKey();
                    if (key.startsWith("notify2_")) {
                        Integer value = (Integer) entry.getValue();
                        if (value == 2 || value == 3) {
                            key = key.replace("notify2_", "");
                            long flags;
                            if (value == 2) {
                                flags = 1;
                            } else {
                                Integer time = (Integer) values.get("notifyuntil_" + key);
                                if (time != null) {
                                    flags = ((long) time << 32) | 1;
                                } else {
                                    flags = 1;
                                }
                            }
                            try {
                                ids.put(Long.parseLong(key), flags);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                try {
                    database.beginTransaction();
                    SQLitePreparedStatement state = database.executeFast("REPLACE INTO dialog_settings VALUES(?, ?)");
                    for (int a = 0; a < ids.size(); a++) {
                        state.requery();
                        state.bindLong(1, ids.keyAt(a));
                        state.bindLong(2, ids.valueAt(a));
                        state.step();
                    }
                    state.dispose();
                    database.commitTransaction();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    public long createPendingTask(final NativeByteBuffer data) {
        if (data == null) {
            return 0;
        }
        final long id = lastTaskId.getAndAdd(1);
        storageQueue.postRunnable(() -> {
            try {
                SQLitePreparedStatement state = database.executeFast("REPLACE INTO pending_tasks VALUES(?, ?)");
                state.bindLong(1, id);
                state.bindByteBuffer(2, data);
                state.step();
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                data.reuse();
            }
        });
        return id;
    }

    public void removePendingTask(final long id) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM pending_tasks WHERE id = " + id).stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void loadPendingTasks() {
        storageQueue.postRunnable(() -> {
            try {
                SQLiteCursor cursor = database.queryFinalized("SELECT id, data FROM pending_tasks WHERE 1");
                while (cursor.next()) {
                    final long taskId = cursor.longValue(0);
                    NativeByteBuffer data = cursor.byteBufferValue(1);
                    if (data != null) {
                        int type = data.readInt32(false);
                        switch (type) {
                            case 0: {
                                final TLRPC.Chat chat = TLRPC.Chat.TLdeserialize(data, data.readInt32(false), false);
                                if (chat != null) {
                                    Utilities.stageQueue.postRunnable(() -> getMessagesController().loadUnknownChannel(chat, taskId));
                                }
                                break;
                            }
                            case 1: {
                                final int channelId = data.readInt32(false);
                                final int newDialogType = data.readInt32(false);
                                Utilities.stageQueue.postRunnable(() -> getMessagesController().getChannelDifference(channelId, newDialogType, taskId, null));
                                break;
                            }
                            case 2:
                            case 5:
                            case 8:
                            case 10:
                            case 14: {
                                final TLRPC.Dialog dialog = new TLRPC.TL_dialog();
                                dialog.id = data.readInt64(false);
                                dialog.top_message = data.readInt32(false);
                                dialog.read_inbox_max_id = data.readInt32(false);
                                dialog.read_outbox_max_id = data.readInt32(false);
                                dialog.unread_count = data.readInt32(false);
                                dialog.last_message_date = data.readInt32(false);
                                dialog.pts = data.readInt32(false);
                                dialog.flags = data.readInt32(false);
                                if (type >= 5) {
                                    dialog.pinned = data.readBool(false);
                                    dialog.pinnedNum = data.readInt32(false);
                                }
                                if (type >= 8) {
                                    dialog.unread_mentions_count = data.readInt32(false);
                                }
                                if (type >= 10) {
                                    dialog.unread_mark = data.readBool(false);
                                }
                                if (type >= 14) {
                                    dialog.folder_id = data.readInt32(false);
                                }
                                final TLRPC.InputPeer peer = TLRPC.InputPeer.TLdeserialize(data, data.readInt32(false), false);
                                AndroidUtilities.runOnUIThread(() -> getMessagesController().checkLastDialogMessage(dialog, peer, taskId));
                                break;
                            }
                            case 3: {
                                long random_id = data.readInt64(false);
                                TLRPC.InputPeer peer = TLRPC.InputPeer.TLdeserialize(data, data.readInt32(false), false);
                                TLRPC.TL_inputMediaGame game = (TLRPC.TL_inputMediaGame) TLRPC.InputMedia.TLdeserialize(data, data.readInt32(false), false);
                                getSendMessagesHelper().sendGame(peer, game, random_id, taskId);
                                break;
                            }
                            case 4: {
                                final long did = data.readInt64(false);
                                final boolean pin = data.readBool(false);
                                final TLRPC.InputPeer peer = TLRPC.InputPeer.TLdeserialize(data, data.readInt32(false), false);
                                AndroidUtilities.runOnUIThread(() -> getMessagesController().pinDialog(did, pin, peer, taskId));
                                break;
                            }
                            case 6: {
                                final int channelId = data.readInt32(false);
                                final int newDialogType = data.readInt32(false);
                                final TLRPC.InputChannel inputChannel = TLRPC.InputChannel.TLdeserialize(data, data.readInt32(false), false);
                                Utilities.stageQueue.postRunnable(() -> getMessagesController().getChannelDifference(channelId, newDialogType, taskId, inputChannel));
                                break;
                            }
                            case 7: {
                                final int channelId = data.readInt32(false);
                                int constructor = data.readInt32(false);
                                TLObject request = TLRPC.TL_messages_deleteMessages.TLdeserialize(data, constructor, false);
                                if (request == null) {
                                    request = TLRPC.TL_channels_deleteMessages.TLdeserialize(data, constructor, false);
                                }
                                if (request == null) {
                                    removePendingTask(taskId);
                                } else {
                                    final TLObject finalRequest = request;
                                    AndroidUtilities.runOnUIThread(() -> getMessagesController().deleteMessages(null, null, null, 0, channelId, true, false, taskId, finalRequest));
                                }
                                break;
                            }
                            case 9: {
                                final long did = data.readInt64(false);
                                final TLRPC.InputPeer peer = TLRPC.InputPeer.TLdeserialize(data, data.readInt32(false), false);
                                AndroidUtilities.runOnUIThread(() -> getMessagesController().markDialogAsUnread(did, peer, taskId));
                                break;
                            }
                            case 11: {
                                TLRPC.InputChannel inputChannel;
                                final int mid = data.readInt32(false);
                                final int channelId = data.readInt32(false);
                                final int ttl = data.readInt32(false);
                                if (channelId != 0) {
                                    inputChannel = TLRPC.InputChannel.TLdeserialize(data, data.readInt32(false), false);
                                } else {
                                    inputChannel = null;
                                }
                                AndroidUtilities.runOnUIThread(() -> getMessagesController().markMessageAsRead(mid, channelId, inputChannel, ttl, taskId));
                                break;
                            }
                            case 12:
                            case 19: {
                                long wallPaperId = data.readInt64(false);
                                long accessHash = data.readInt64(false);
                                boolean isBlurred = data.readBool(false);
                                boolean isMotion = data.readBool(false);
                                int backgroundColor = data.readInt32(false);
                                float intesity = (float) data.readDouble(false);
                                boolean install = data.readBool(false);
                                final String slug;
                                if (type == 19) {
                                    slug = data.readString(false);
                                } else {
                                    slug = null;
                                }
                                AndroidUtilities.runOnUIThread(() -> getMessagesController().saveWallpaperToServer(null, wallPaperId, slug, accessHash, isBlurred, isMotion, backgroundColor, intesity, install, taskId));
                                break;
                            }
                            case 13: {
                                final long did = data.readInt64(false);
                                final boolean first = data.readBool(false);
                                final int onlyHistory = data.readInt32(false);
                                final int maxIdDelete = data.readInt32(false);
                                final boolean revoke = data.readBool(false);
                                TLRPC.InputPeer inputPeer = TLRPC.InputPeer.TLdeserialize(data, data.readInt32(false), false);
                                AndroidUtilities.runOnUIThread(() -> getMessagesController().deleteDialog(did, first, onlyHistory, maxIdDelete, revoke, inputPeer, taskId));
                                break;
                            }
                            case 15: {
                                TLRPC.InputPeer inputPeer = TLRPC.InputPeer.TLdeserialize(data, data.readInt32(false), false);
                                Utilities.stageQueue.postRunnable(() -> getMessagesController().loadUnknownDialog(inputPeer, taskId));
                                break;
                            }
                            case 16: {
                                final int folderId = data.readInt32(false);
                                int count = data.readInt32(false);
                                ArrayList<TLRPC.InputDialogPeer> peers = new ArrayList<>();
                                for (int a = 0; a < count; a++) {
                                    TLRPC.InputDialogPeer inputPeer = TLRPC.InputDialogPeer.TLdeserialize(data, data.readInt32(false), false);
                                    peers.add(inputPeer);
                                }
                                AndroidUtilities.runOnUIThread(() -> getMessagesController().reorderPinnedDialogs(folderId, peers, taskId));
                                break;
                            }
                            case 17: {
                                final int folderId = data.readInt32(false);
                                int count = data.readInt32(false);
                                ArrayList<TLRPC.TL_inputFolderPeer> peers = new ArrayList<>();
                                for (int a = 0; a < count; a++) {
                                    TLRPC.TL_inputFolderPeer inputPeer = TLRPC.TL_inputFolderPeer.TLdeserialize(data, data.readInt32(false), false);
                                    peers.add(inputPeer);
                                }
                                AndroidUtilities.runOnUIThread(() -> getMessagesController().addDialogToFolder(null, folderId, -1, peers, taskId));
                                break;
                            }
                            case 18: {
                                final long dialogId = data.readInt64(false);
                                final int channelId = data.readInt32(false);
                                int constructor = data.readInt32(false);
                                TLObject request = TLRPC.TL_messages_deleteScheduledMessages.TLdeserialize(data, constructor, false);
                                if (request == null) {
                                    removePendingTask(taskId);
                                } else {
                                    final TLObject finalRequest = request;
                                    AndroidUtilities.runOnUIThread(() -> MessagesController.getInstance(currentAccount).deleteMessages(null, null, null, dialogId, channelId, true, true, taskId, finalRequest));
                                }
                                break;
                            }
                        }
                        data.reuse();
                    }
                }
                cursor.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void saveChannelPts(final int channelId, final int pts) {
        storageQueue.postRunnable(() -> {
            try {
                SQLitePreparedStatement state = database.executeFast("UPDATE dialogs SET pts = ? WHERE did = ?");
                state.bindInteger(1, pts);
                state.bindInteger(2, -channelId);
                state.step();
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void saveDiffParamsInternal(final int seq, final int pts, final int date, final int qts) {
        try {
            if (lastSavedSeq == seq && lastSavedPts == pts && lastSavedDate == date && lastQtsValue == qts) {
                return;
            }
            SQLitePreparedStatement state = database.executeFast("UPDATE params SET seq = ?, pts = ?, date = ?, qts = ? WHERE id = 1");
            state.bindInteger(1, seq);
            state.bindInteger(2, pts);
            state.bindInteger(3, date);
            state.bindInteger(4, qts);
            state.step();
            state.dispose();
            lastSavedSeq = seq;
            lastSavedPts = pts;
            lastSavedDate = date;
            lastSavedQts = qts;
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void saveDiffParams(final int seq, final int pts, final int date, final int qts) {
        storageQueue.postRunnable(() -> saveDiffParamsInternal(seq, pts, date, qts));
    }

    public void setDialogFlags(final long did, final long flags) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast(String.format(Locale.US, "REPLACE INTO dialog_settings VALUES(%d, %d)", did, flags)).stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void putPushMessage(MessageObject message) {
        storageQueue.postRunnable(() -> {
            try {
                NativeByteBuffer data = new NativeByteBuffer(message.messageOwner.getObjectSize());
                message.messageOwner.serializeToStream(data);

                long messageId = message.getId();
                if (message.messageOwner.to_id.channel_id != 0) {
                    messageId |= ((long) message.messageOwner.to_id.channel_id) << 32;
                }

                int flags = 0;
                if (message.localType == 2) {
                    flags |= 1;
                }
                if (message.localChannel) {
                    flags |= 2;
                }

                SQLitePreparedStatement state = database.executeFast("REPLACE INTO unread_push_messages VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)");
                state.requery();
                state.bindLong(1, message.getDialogId());
                state.bindLong(2, messageId);
                state.bindLong(3, message.messageOwner.random_id);
                state.bindInteger(4, message.messageOwner.date);
                state.bindByteBuffer(5, data);
                if (message.messageText == null) {
                    state.bindNull(6);
                } else {
                    state.bindString(6, message.messageText.toString());
                }
                if (message.localName == null) {
                    state.bindNull(7);
                } else {
                    state.bindString(7, message.localName);
                }
                if (message.localUserName == null) {
                    state.bindNull(8);
                } else {
                    state.bindString(8, message.localUserName);
                }
                state.bindInteger(9, flags);
                state.step();

                data.reuse();
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private class ReadDialog {
        public int lastMid;
        public int date;
        public int unreadCount;
    }

    public void readAllDialogs() {
        storageQueue.postRunnable(() -> {
            try {
                ArrayList<Integer> usersToLoad = new ArrayList<>();
                ArrayList<Integer> chatsToLoad = new ArrayList<>();
                ArrayList<Integer> encryptedChatIds = new ArrayList<>();

                final LongSparseArray<ReadDialog> dialogs = new LongSparseArray<>();
                SQLiteCursor cursor = database.queryFinalized("SELECT did, last_mid, unread_count, date FROM dialogs WHERE unread_count != 0");
                while (cursor.next()) {
                    long did = cursor.longValue(0);
                    if (DialogObject.isFolderDialogId(did)) {
                        continue;
                    }
                    ReadDialog dialog = new ReadDialog();
                    dialog.lastMid = cursor.intValue(1);
                    dialog.unreadCount = cursor.intValue(2);
                    dialog.date = cursor.intValue(3);

                    dialogs.put(did, dialog);
                    int lower_id = (int) did;
                    int high_id = (int) (did >> 32);
                    if (lower_id != 0) {
                        if (lower_id < 0) {
                            if (!chatsToLoad.contains(-lower_id)) {
                                chatsToLoad.add(-lower_id);
                            }
                        } else {
                            if (!usersToLoad.contains(lower_id)) {
                                usersToLoad.add(lower_id);
                            }
                        }
                    } else {
                        if (!encryptedChatIds.contains(high_id)) {
                            encryptedChatIds.add(high_id);
                        }
                    }
                }
                cursor.dispose();

                final ArrayList<TLRPC.User> users = new ArrayList<>();
                final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                final ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
                if (!encryptedChatIds.isEmpty()) {
                    getEncryptedChatsInternal(TextUtils.join(",", encryptedChatIds), encryptedChats, usersToLoad);
                }
                if (!usersToLoad.isEmpty()) {
                    getUsersInternal(TextUtils.join(",", usersToLoad), users);
                }
                if (!chatsToLoad.isEmpty()) {
                    getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    getMessagesController().putUsers(users, true);
                    getMessagesController().putChats(chats, true);
                    getMessagesController().putEncryptedChats(encryptedChats, true);
                    for (int a = 0; a < dialogs.size(); a++) {
                        long did = dialogs.keyAt(a);
                        ReadDialog dialog = dialogs.valueAt(a);
                        getMessagesController().markDialogAsRead(did, dialog.lastMid, dialog.lastMid, dialog.date, false, dialog.unreadCount, true, 0);
                    }
                });
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }


    public void loadUnreadMessages() {
        storageQueue.postRunnable(() -> {
            try {
                ArrayList<Integer> usersToLoad = new ArrayList<>();
                ArrayList<Integer> chatsToLoad = new ArrayList<>();
                ArrayList<Integer> encryptedChatIds = new ArrayList<>();

                final LongSparseArray<Integer> pushDialogs = new LongSparseArray<>();
                SQLiteCursor cursor = database.queryFinalized("SELECT d.did, d.unread_count, s.flags FROM dialogs as d LEFT JOIN dialog_settings as s ON d.did = s.did WHERE d.unread_count != 0");
                StringBuilder ids = new StringBuilder();
                int currentTime = getConnectionsManager().getCurrentTime();
                while (cursor.next()) {
                    long flags = cursor.longValue(2);
                    boolean muted = (flags & 1) != 0;
                    int mutedUntil = (int) (flags >> 32);
                    if (cursor.isNull(2) || !muted || mutedUntil != 0 && mutedUntil < currentTime) {
                        long did = cursor.longValue(0);
                        if (DialogObject.isFolderDialogId(did)) {
                            continue;
                        }
                        int count = cursor.intValue(1);
                        pushDialogs.put(did, count);
                        if (ids.length() != 0) {
                            ids.append(",");
                        }
                        ids.append(did);
                        int lower_id = (int) did;
                        int high_id = (int) (did >> 32);
                        if (lower_id != 0) {
                            if (lower_id < 0) {
                                if (!chatsToLoad.contains(-lower_id)) {
                                    chatsToLoad.add(-lower_id);
                                }
                            } else {
                                if (!usersToLoad.contains(lower_id)) {
                                    usersToLoad.add(lower_id);
                                }
                            }
                        } else {
                            if (!encryptedChatIds.contains(high_id)) {
                                encryptedChatIds.add(high_id);
                            }
                        }
                    }
                }
                cursor.dispose();

                ArrayList<Long> replyMessages = new ArrayList<>();
                SparseArray<ArrayList<TLRPC.Message>> replyMessageOwners = new SparseArray<>();
                final ArrayList<TLRPC.Message> messages = new ArrayList<>();
                final ArrayList<MessageObject> pushMessages = new ArrayList<>();
                final ArrayList<TLRPC.User> users = new ArrayList<>();
                final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                final ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
                int maxDate = 0;
                if (ids.length() > 0) {
                    cursor = database.queryFinalized("SELECT read_state, data, send_state, mid, date, uid, replydata FROM messages WHERE uid IN (" + ids.toString() + ") AND out = 0 AND read_state IN(0,2) ORDER BY date DESC LIMIT 50");
                    while (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(1);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.readAttachPath(data, getUserConfig().clientUserId);
                            data.reuse();
                            MessageObject.setUnreadFlags(message, cursor.intValue(0));
                            message.id = cursor.intValue(3);
                            message.date = cursor.intValue(4);
                            message.dialog_id = cursor.longValue(5);
                            messages.add(message);
                            maxDate = Math.max(maxDate, message.date);

                            int lower_id = (int) message.dialog_id;
                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);
                            message.send_state = cursor.intValue(2);
                            if (message.to_id.channel_id == 0 && !MessageObject.isUnread(message) && lower_id != 0 || message.id > 0) {
                                message.send_state = 0;
                            }
                            if (lower_id == 0 && !cursor.isNull(5)) {
                                message.random_id = cursor.longValue(5);
                            }

                            try {
                                if (message.reply_to_msg_id != 0 && (
                                        message.action instanceof TLRPC.TL_messageActionPinMessage ||
                                                message.action instanceof TLRPC.TL_messageActionPaymentSent ||
                                                message.action instanceof TLRPC.TL_messageActionGameScore)) {
                                    if (!cursor.isNull(6)) {
                                        data = cursor.byteBufferValue(6);
                                        if (data != null) {
                                            message.replyMessage = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                            message.replyMessage.readAttachPath(data, getUserConfig().clientUserId);
                                            data.reuse();
                                            if (message.replyMessage != null) {
                                                if (MessageObject.isMegagroup(message)) {
                                                    message.replyMessage.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                                }
                                                addUsersAndChatsFromMessage(message.replyMessage, usersToLoad, chatsToLoad);
                                            }
                                        }
                                    }
                                    if (message.replyMessage == null) {
                                        long messageId = message.reply_to_msg_id;
                                        if (message.to_id.channel_id != 0) {
                                            messageId |= ((long) message.to_id.channel_id) << 32;
                                        }
                                        if (!replyMessages.contains(messageId)) {
                                            replyMessages.add(messageId);
                                        }
                                        ArrayList<TLRPC.Message> arrayList = replyMessageOwners.get(message.reply_to_msg_id);
                                        if (arrayList == null) {
                                            arrayList = new ArrayList<>();
                                            replyMessageOwners.put(message.reply_to_msg_id, arrayList);
                                        }
                                        arrayList.add(message);
                                    }
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    }
                    cursor.dispose();

                    database.executeFast("DELETE FROM unread_push_messages WHERE date <= " + maxDate).stepThis().dispose();
                    cursor = database.queryFinalized("SELECT data, mid, date, uid, random, fm, name, uname, flags FROM unread_push_messages WHERE 1 ORDER BY date DESC LIMIT 50");
                    while (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            data.reuse();
                            message.id = cursor.intValue(1);
                            message.date = cursor.intValue(2);
                            message.dialog_id = cursor.longValue(3);
                            message.random_id = cursor.longValue(4);
                            String messageText = cursor.isNull(5) ? null : cursor.stringValue(5);
                            String name = cursor.isNull(6) ? null : cursor.stringValue(6);
                            String userName = cursor.isNull(7) ? null : cursor.stringValue(7);
                            int flags = cursor.intValue(8);
                            if (message.from_id == 0) {
                                int lowerId = (int) message.dialog_id;
                                if (lowerId > 0) {
                                    message.from_id = lowerId;
                                }
                            }
                            int lower_id = (int) message.dialog_id;
                            if (lower_id > 0) {
                                if (!usersToLoad.contains(lower_id)) {
                                    usersToLoad.add(lower_id);
                                }
                            } else if (lower_id < 0) {
                                if (!chatsToLoad.contains(-lower_id)) {
                                    chatsToLoad.add(-lower_id);
                                }
                            }

                            pushMessages.add(new MessageObject(currentAccount, message, messageText, name, userName, (flags & 1) != 0, (flags & 2) != 0, false));
                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);
                        }
                    }
                    cursor.dispose();

                    if (!replyMessages.isEmpty()) {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid, date, uid FROM messages WHERE mid IN(%s)", TextUtils.join(",", replyMessages)));
                        while (cursor.next()) {
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                message.readAttachPath(data, getUserConfig().clientUserId);
                                data.reuse();
                                message.id = cursor.intValue(1);
                                message.date = cursor.intValue(2);
                                message.dialog_id = cursor.longValue(3);

                                addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);

                                ArrayList<TLRPC.Message> arrayList = replyMessageOwners.get(message.id);
                                if (arrayList != null) {
                                    for (int a = 0; a < arrayList.size(); a++) {
                                        TLRPC.Message m = arrayList.get(a);
                                        m.replyMessage = message;
                                        if (MessageObject.isMegagroup(m)) {
                                            m.replyMessage.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                        }
                                    }
                                }
                            }
                        }
                        cursor.dispose();
                    }

                    if (!encryptedChatIds.isEmpty()) {
                        getEncryptedChatsInternal(TextUtils.join(",", encryptedChatIds), encryptedChats, usersToLoad);
                    }

                    if (!usersToLoad.isEmpty()) {
                        getUsersInternal(TextUtils.join(",", usersToLoad), users);
                    }

                    if (!chatsToLoad.isEmpty()) {
                        getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                        for (int a = 0; a < chats.size(); a++) {
                            TLRPC.Chat chat = chats.get(a);
                            if (chat != null && (ChatObject.isNotInChat(chat) || chat.migrated_to != null)) {
                                long did = -chat.id;
                                database.executeFast("UPDATE dialogs SET unread_count = 0 WHERE did = " + did).stepThis().dispose();
                                database.executeFast(String.format(Locale.US, "UPDATE messages SET read_state = 3 WHERE uid = %d AND mid > 0 AND read_state IN(0,2) AND out = 0", did)).stepThis().dispose();
                                chats.remove(a);
                                a--;
                                pushDialogs.remove(did);
                                for (int b = 0; b < messages.size(); b++) {
                                    TLRPC.Message message = messages.get(b);
                                    if (message.dialog_id == did) {
                                        messages.remove(b);
                                        b--;
                                    }
                                }
                            }
                        }
                    }
                }
                Collections.reverse(messages);
                AndroidUtilities.runOnUIThread(() -> getNotificationsController().processLoadedUnreadMessages(pushDialogs, messages, pushMessages, users, chats, encryptedChats));
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void putWallpapers(final ArrayList<TLRPC.WallPaper> wallPapers, int action) {
        storageQueue.postRunnable(() -> {
            try {
                if (action == 1) {
                    database.executeFast("DELETE FROM wallpapers2 WHERE 1").stepThis().dispose();
                }
                database.beginTransaction();
                SQLitePreparedStatement state;
                if (action != 0) {
                    state = database.executeFast("REPLACE INTO wallpapers2 VALUES(?, ?, ?)");
                } else {
                    state = database.executeFast("UPDATE wallpapers2 SET data = ? WHERE uid = ?");
                }
                for (int a = 0, N = wallPapers.size(); a < N; a++) {
                    TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) wallPapers.get(a);
                    state.requery();
                    NativeByteBuffer data = new NativeByteBuffer(wallPaper.getObjectSize());
                    wallPaper.serializeToStream(data);
                    if (action != 0) {
                        state.bindLong(1, wallPaper.id);
                        state.bindByteBuffer(2, data);
                        state.bindInteger(3, action == 2 ? -1 : a);
                    } else {
                        state.bindByteBuffer(1, data);
                        state.bindLong(2, wallPaper.id);
                    }
                    state.step();
                    data.reuse();
                }
                state.dispose();
                database.commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void getWallpapers() {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized("SELECT data FROM wallpapers2 WHERE 1 ORDER BY num ASC");
                final ArrayList<TLRPC.TL_wallPaper> wallPapers = new ArrayList<>();
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) TLRPC.WallPaper.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                        if (wallPaper != null) {
                            wallPapers.add(wallPaper);
                        }
                    }
                }
                AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.wallpapersDidLoad, wallPapers));
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public void loadWebRecent(final int type) {
        storageQueue.postRunnable(() -> {
            try {
                final ArrayList<MediaController.SearchImage> arrayList = new ArrayList<>();
                /*SQLiteCursor cursor = database.queryFinalized("SELECT id, image_url, thumb_url, local_url, width, height, size, date, document FROM web_recent_v3 WHERE type = " + type + " ORDER BY date DESC");
                while (cursor.next()) {
                    MediaController.SearchImage searchImage = new MediaController.SearchImage();
                    searchImage.id = cursor.stringValue(0);
                    searchImage.imageUrl = cursor.stringValue(1);
                    searchImage.thumbUrl = cursor.stringValue(2);
                    searchImage.localUrl = cursor.stringValue(3);
                    searchImage.width = cursor.intValue(4);
                    searchImage.height = cursor.intValue(5);
                    searchImage.size = cursor.intValue(6);
                    searchImage.date = cursor.intValue(7);
                    if (!cursor.isNull(8)) {
                        NativeByteBuffer data = cursor.byteBufferValue(8);
                        if (data != null) {
                            int constructor = data.readInt32(false);
                            searchImage.document = TLRPC.Document.TLdeserialize(data, constructor, false);
                            if (searchImage.document == null) {
                                searchImage.photo = TLRPC.Photo.TLdeserialize(data, constructor, false);
                                if (searchImage.photo != null) {
                                    TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(searchImage.photo.sizes, AndroidUtilities.getPhotoSize());
                                    TLRPC.PhotoSize size2 = FileLoader.getClosestPhotoSizeWithSize(searchImage.photo.sizes, 80);
                                    searchImage.photoSize = size;
                                    searchImage.thumbPhotoSize = size2;
                                }
                            }
                            data.reuse();
                        }
                    }
                    searchImage.type = type;
                    arrayList.add(searchImage);
                }
                cursor.dispose();*/
                AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.recentImagesDidLoad, type, arrayList));
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    public void addRecentLocalFile(final String imageUrl, final String localUrl, final TLRPC.Document document) {
        if (imageUrl == null || imageUrl.length() == 0 || ((localUrl == null || localUrl.length() == 0) && document == null)) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                if (document != null) {
                    SQLitePreparedStatement state = database.executeFast("UPDATE web_recent_v3 SET document = ? WHERE image_url = ?");
                    state.requery();
                    NativeByteBuffer data = new NativeByteBuffer(document.getObjectSize());
                    document.serializeToStream(data);
                    state.bindByteBuffer(1, data);
                    state.bindString(2, imageUrl);
                    state.step();
                    state.dispose();
                    data.reuse();
                } else {
                    SQLitePreparedStatement state = database.executeFast("UPDATE web_recent_v3 SET local_url = ? WHERE image_url = ?");
                    state.requery();
                    state.bindString(1, localUrl);
                    state.bindString(2, imageUrl);
                    state.step();
                    state.dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void clearWebRecent(final int type) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM web_recent_v3 WHERE type = " + type).stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void putWebRecent(final ArrayList<MediaController.SearchImage> arrayList) {
        if (arrayList.isEmpty() || !arrayList.isEmpty()) { //disable web recent
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                database.beginTransaction();
                SQLitePreparedStatement state = database.executeFast("REPLACE INTO web_recent_v3 VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                for (int a = 0; a < arrayList.size(); a++) {
                    if (a == 200) {
                        break;
                    }
                    MediaController.SearchImage searchImage = arrayList.get(a);
                    state.requery();
                    state.bindString(1, searchImage.id);
                    state.bindInteger(2, searchImage.type);
                    state.bindString(3, searchImage.imageUrl != null ? searchImage.imageUrl : "");
                    state.bindString(4, searchImage.thumbUrl != null ? searchImage.thumbUrl : "");
                    state.bindString(5, "");
                    state.bindInteger(6, searchImage.width);
                    state.bindInteger(7, searchImage.height);
                    state.bindInteger(8, searchImage.size);
                    state.bindInteger(9, searchImage.date);
                    NativeByteBuffer data = null;
                    if (searchImage.photo != null) {
                        data = new NativeByteBuffer(searchImage.photo.getObjectSize());
                        searchImage.photo.serializeToStream(data);
                        state.bindByteBuffer(10, data);
                    } else if (searchImage.document != null) {
                        data = new NativeByteBuffer(searchImage.document.getObjectSize());
                        searchImage.document.serializeToStream(data);
                        state.bindByteBuffer(10, data);
                    } else {
                        state.bindNull(10);
                    }
                    state.step();
                    if (data != null) {
                        data.reuse();
                    }
                }
                state.dispose();
                database.commitTransaction();
                if (arrayList.size() >= 200) {
                    database.beginTransaction();
                    for (int a = 200; a < arrayList.size(); a++) {
                        database.executeFast("DELETE FROM web_recent_v3 WHERE id = '" + arrayList.get(a).id + "'").stepThis().dispose();
                    }
                    database.commitTransaction();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void deleteUserChannelHistory(final int channelId, final int uid) {
        storageQueue.postRunnable(() -> {
            try {
                long did = -channelId;
                final ArrayList<Integer> mids = new ArrayList<>();
                SQLiteCursor cursor = database.queryFinalized("SELECT data FROM messages WHERE uid = " + did);
                ArrayList<File> filesToDelete = new ArrayList<>();
                try {
                    while (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.readAttachPath(data, getUserConfig().clientUserId);
                            data.reuse();
                            if (message != null && message.from_id == uid && message.id != 1) {
                                mids.add(message.id);
                                addFilesToDelete(message, filesToDelete, false);
                            }
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                cursor.dispose();
                AndroidUtilities.runOnUIThread(() -> getMessagesController().markChannelDialogMessageAsDeleted(mids, channelId));
                markMessagesAsDeletedInternal(mids, channelId, false, false);
                updateDialogsWithDeletedMessagesInternal(mids, null, channelId);
                getFileLoader().deleteFiles(filesToDelete, 0);
                if (!mids.isEmpty()) {
                    AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.messagesDeleted, mids, channelId, false));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private boolean addFilesToDelete(TLRPC.Message message, ArrayList<File> filesToDelete, boolean forceCache) {
        if (message == null) {
            return false;
        }
        if (message.media instanceof TLRPC.TL_messageMediaPhoto && message.media.photo != null) {
            for (int a = 0, N = message.media.photo.sizes.size(); a < N; a++) {
                TLRPC.PhotoSize photoSize = message.media.photo.sizes.get(a);
                File file = FileLoader.getPathToAttach(photoSize);
                if (file != null && file.toString().length() > 0) {
                    filesToDelete.add(file);
                }
            }
            return true;
        } else if (message.media instanceof TLRPC.TL_messageMediaDocument && message.media.document != null) {
            File file = FileLoader.getPathToAttach(message.media.document, forceCache);
            if (file != null && file.toString().length() > 0) {
                filesToDelete.add(file);
            }
            for (int a = 0, N = message.media.document.thumbs.size(); a < N; a++) {
                TLRPC.PhotoSize photoSize = message.media.document.thumbs.get(a);
                file = FileLoader.getPathToAttach(photoSize);
                if (file != null && file.toString().length() > 0) {
                    filesToDelete.add(file);
                }
            }
            return true;
        }
        return false;
    }

    public void deleteDialog(final long did, final int messagesOnly) {
        storageQueue.postRunnable(() -> {
            try {
                if (messagesOnly == 3) {
                    int lastMid = -1;
                    SQLiteCursor cursor = database.queryFinalized("SELECT last_mid FROM dialogs WHERE did = " + did);
                    if (cursor.next()) {
                        lastMid = cursor.intValue(0);
                    }
                    cursor.dispose();
                    if (lastMid != 0) {
                        return;
                    }
                }
                if ((int) did == 0 || messagesOnly == 2) {
                    SQLiteCursor cursor = database.queryFinalized("SELECT data FROM messages WHERE uid = " + did);
                    ArrayList<File> filesToDelete = new ArrayList<>();
                    try {
                        while (cursor.next()) {
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                message.readAttachPath(data, getUserConfig().clientUserId);
                                data.reuse();
                                addFilesToDelete(message, filesToDelete, false);
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    cursor.dispose();
                    getFileLoader().deleteFiles(filesToDelete, messagesOnly);
                }

                if (messagesOnly == 0 || messagesOnly == 3) {
                    database.executeFast("DELETE FROM dialogs WHERE did = " + did).stepThis().dispose();
                    database.executeFast("DELETE FROM chat_settings_v2 WHERE uid = " + did).stepThis().dispose();
                    database.executeFast("DELETE FROM chat_pinned WHERE uid = " + did).stepThis().dispose();
                    database.executeFast("DELETE FROM channel_users_v2 WHERE did = " + did).stepThis().dispose();
                    database.executeFast("DELETE FROM search_recent WHERE did = " + did).stepThis().dispose();
                    int lower_id = (int) did;
                    int high_id = (int) (did >> 32);
                    if (lower_id != 0) {
                        if (lower_id < 0) {
                            //database.executeFast("DELETE FROM chats WHERE uid = " + (-lower_id)).stepThis().dispose();
                        }
                    } else {
                        database.executeFast("DELETE FROM enc_chats WHERE uid = " + high_id).stepThis().dispose();
                        //database.executeFast("DELETE FROM secret_holes WHERE uid = " + high_id).stepThis().dispose();
                    }
                } else if (messagesOnly == 2) {
                    SQLiteCursor cursor = database.queryFinalized("SELECT last_mid_i, last_mid FROM dialogs WHERE did = " + did);
                    int messageId = -1;
                    if (cursor.next()) {
                        long last_mid_i = cursor.longValue(0);
                        long last_mid = cursor.longValue(1);
                        SQLiteCursor cursor2 = database.queryFinalized("SELECT data FROM messages WHERE uid = " + did + " AND mid IN (" + last_mid_i + "," + last_mid + ")");
                        try {
                            while (cursor2.next()) {
                                NativeByteBuffer data = cursor2.byteBufferValue(0);
                                if (data != null) {
                                    TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                    message.readAttachPath(data, getUserConfig().clientUserId);
                                    data.reuse();
                                    if (message != null) {
                                        messageId = message.id;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        cursor2.dispose();

                        database.executeFast("DELETE FROM messages WHERE uid = " + did + " AND mid != " + last_mid_i + " AND mid != " + last_mid).stepThis().dispose();
                        database.executeFast("DELETE FROM messages_holes WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM bot_keyboard WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM media_counts_v2 WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM media_v2 WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM media_holes_v2 WHERE uid = " + did).stepThis().dispose();
                        getMediaDataController().clearBotKeyboard(did, null);

                        SQLitePreparedStatement state5 = database.executeFast("REPLACE INTO messages_holes VALUES(?, ?, ?)");
                        SQLitePreparedStatement state6 = database.executeFast("REPLACE INTO media_holes_v2 VALUES(?, ?, ?, ?)");
                        if (messageId != -1) {
                            createFirstHoles(did, state5, state6, messageId);
                        }
                        state5.dispose();
                        state6.dispose();
                    }
                    cursor.dispose();
                    return;
                }

                database.executeFast("UPDATE dialogs SET unread_count = 0, unread_count_i = 0 WHERE did = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM messages WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM bot_keyboard WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM media_counts_v2 WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM media_v2 WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM messages_holes WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM media_holes_v2 WHERE uid = " + did).stepThis().dispose();
                getMediaDataController().clearBotKeyboard(did, null);
                AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.needReloadRecentDialogsSearch));
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void onDeleteQueryComplete(final long did) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM media_counts_v2 WHERE uid = " + did).stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void getDialogPhotos(final int did, final int count, final long max_id, final int classGuid) {
        storageQueue.postRunnable(() -> {
            try {
                SQLiteCursor cursor;

                if (max_id != 0) {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM user_photos WHERE uid = %d AND id < %d ORDER BY rowid ASC LIMIT %d", did, max_id, count));
                } else {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM user_photos WHERE uid = %d ORDER BY rowid ASC LIMIT %d", did, count));
                }

                final TLRPC.photos_Photos res = new TLRPC.TL_photos_photos();

                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        TLRPC.Photo photo = TLRPC.Photo.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                        res.photos.add(photo);
                    }
                }
                cursor.dispose();

                Utilities.stageQueue.postRunnable(() -> getMessagesController().processLoadedUserPhotos(res, did, count, max_id, true, classGuid));
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void clearUserPhotos(final int uid) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM user_photos WHERE uid = " + uid).stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void clearUserPhoto(final int uid, final long pid) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM user_photos WHERE uid = " + uid + " AND id = " + pid).stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void resetDialogs(final TLRPC.messages_Dialogs dialogsRes, final int messagesCount, final int seq, final int newPts, final int date, final int qts, final LongSparseArray<TLRPC.Dialog> new_dialogs_dict, final LongSparseArray<MessageObject> new_dialogMessage, final TLRPC.Message lastMessage, final int dialogsCount) {
        storageQueue.postRunnable(() -> {
            try {
                int maxPinnedNum = 0;

                ArrayList<Integer> dids = new ArrayList<>();

                int totalPinnedCount = dialogsRes.dialogs.size() - dialogsCount;
                final LongSparseArray<Integer> oldPinnedDialogNums = new LongSparseArray<>();
                ArrayList<Long> oldPinnedOrder = new ArrayList<>();
                ArrayList<Long> orderArrayList = new ArrayList<>();

                for (int a = dialogsCount; a < dialogsRes.dialogs.size(); a++) {
                    TLRPC.Dialog dialog = dialogsRes.dialogs.get(a);
                    orderArrayList.add(dialog.id);
                }

                SQLiteCursor cursor = database.queryFinalized("SELECT did, pinned FROM dialogs WHERE 1");
                while (cursor.next()) {
                    long did = cursor.longValue(0);
                    int pinnedNum = cursor.intValue(1);
                    int lower_id = (int) did;
                    if (lower_id != 0) {
                        dids.add(lower_id);
                        if (pinnedNum > 0) {
                            maxPinnedNum = Math.max(pinnedNum, maxPinnedNum);
                            oldPinnedDialogNums.put(did, pinnedNum);
                            oldPinnedOrder.add(did);
                        }
                    }
                }
                Collections.sort(oldPinnedOrder, (o1, o2) -> {
                    Integer val1 = oldPinnedDialogNums.get(o1);
                    Integer val2 = oldPinnedDialogNums.get(o2);
                    if (val1 < val2) {
                        return 1;
                    } else if (val1 > val2) {
                        return -1;
                    }
                    return 0;
                });
                while (oldPinnedOrder.size() < totalPinnedCount) {
                    oldPinnedOrder.add(0, 0L);
                }
                cursor.dispose();
                String ids = "(" + TextUtils.join(",", dids) + ")";

                database.beginTransaction();
                database.executeFast("DELETE FROM dialogs WHERE did IN " + ids).stepThis().dispose();
                database.executeFast("DELETE FROM messages WHERE uid IN " + ids).stepThis().dispose();
                database.executeFast("DELETE FROM polls WHERE 1").stepThis().dispose();
                database.executeFast("DELETE FROM bot_keyboard WHERE uid IN " + ids).stepThis().dispose();
                database.executeFast("DELETE FROM media_v2 WHERE uid IN " + ids).stepThis().dispose();
                database.executeFast("DELETE FROM messages_holes WHERE uid IN " + ids).stepThis().dispose();
                database.executeFast("DELETE FROM media_holes_v2 WHERE uid IN " + ids).stepThis().dispose();
                database.commitTransaction();

                for (int a = 0; a < totalPinnedCount; a++) {
                    TLRPC.Dialog dialog = dialogsRes.dialogs.get(dialogsCount + a);
                    if (dialog instanceof TLRPC.TL_dialog && !dialog.pinned) {
                        continue;
                    }
                    int oldIdx = oldPinnedOrder.indexOf(dialog.id);
                    int newIdx = orderArrayList.indexOf(dialog.id);
                    if (oldIdx != -1 && newIdx != -1) {
                        if (oldIdx == newIdx) {
                            Integer oldNum = oldPinnedDialogNums.get(dialog.id);
                            if (oldNum != null) {
                                dialog.pinnedNum = oldNum;
                            }
                        } else {
                            long oldDid = oldPinnedOrder.get(newIdx);
                            Integer oldNum = oldPinnedDialogNums.get(oldDid);
                            if (oldNum != null) {
                                dialog.pinnedNum = oldNum;
                            }
                        }
                    }
                    if (dialog.pinnedNum == 0) {
                        dialog.pinnedNum = (totalPinnedCount - a) + maxPinnedNum;
                    }
                }

                putDialogsInternal(dialogsRes, 0);
                saveDiffParamsInternal(seq, newPts, date, qts);

                int totalDialogsLoadCount = getUserConfig().getTotalDialogsCount(0);
                int[] dialogsLoadOffset = getUserConfig().getDialogLoadOffsets(0);
                int dialogsLoadOffsetId;
                int dialogsLoadOffsetDate;
                int dialogsLoadOffsetChannelId = 0;
                int dialogsLoadOffsetChatId = 0;
                int dialogsLoadOffsetUserId = 0;
                long dialogsLoadOffsetAccess = 0;

                totalDialogsLoadCount += dialogsRes.dialogs.size();
                dialogsLoadOffsetId = lastMessage.id;
                dialogsLoadOffsetDate = lastMessage.date;
                if (lastMessage.to_id.channel_id != 0) {
                    dialogsLoadOffsetChannelId = lastMessage.to_id.channel_id;
                    dialogsLoadOffsetChatId = 0;
                    dialogsLoadOffsetUserId = 0;
                    for (int a = 0; a < dialogsRes.chats.size(); a++) {
                        TLRPC.Chat chat = dialogsRes.chats.get(a);
                        if (chat.id == dialogsLoadOffsetChannelId) {
                            dialogsLoadOffsetAccess = chat.access_hash;
                            break;
                        }
                    }
                } else if (lastMessage.to_id.chat_id != 0) {
                    dialogsLoadOffsetChatId = lastMessage.to_id.chat_id;
                    dialogsLoadOffsetChannelId = 0;
                    dialogsLoadOffsetUserId = 0;
                    for (int a = 0; a < dialogsRes.chats.size(); a++) {
                        TLRPC.Chat chat = dialogsRes.chats.get(a);
                        if (chat.id == dialogsLoadOffsetChatId) {
                            dialogsLoadOffsetAccess = chat.access_hash;
                            break;
                        }
                    }
                } else if (lastMessage.to_id.user_id != 0) {
                    dialogsLoadOffsetUserId = lastMessage.to_id.user_id;
                    dialogsLoadOffsetChatId = 0;
                    dialogsLoadOffsetChannelId = 0;
                    for (int a = 0; a < dialogsRes.users.size(); a++) {
                        TLRPC.User user = dialogsRes.users.get(a);
                        if (user.id == dialogsLoadOffsetUserId) {
                            dialogsLoadOffsetAccess = user.access_hash;
                            break;
                        }
                    }
                }
                for (int a = 0; a < 2; a++) {
                    getUserConfig().setDialogsLoadOffset(a,
                            dialogsLoadOffsetId,
                            dialogsLoadOffsetDate,
                            dialogsLoadOffsetUserId,
                            dialogsLoadOffsetChatId,
                            dialogsLoadOffsetChannelId,
                            dialogsLoadOffsetAccess);
                    getUserConfig().setTotalDialogsCount(a, totalDialogsLoadCount);
                }
                getUserConfig().saveConfig(false);
                getMessagesController().completeDialogsReset(dialogsRes, messagesCount, seq, newPts, date, qts, new_dialogs_dict, new_dialogMessage, lastMessage);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void putDialogPhotos(final int did, final TLRPC.photos_Photos photos) {
        if (photos == null || photos.photos.isEmpty()) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM user_photos WHERE uid = " + did).stepThis().dispose();
                SQLitePreparedStatement state = database.executeFast("REPLACE INTO user_photos VALUES(?, ?, ?)");
                for (int a = 0, N = photos.photos.size(); a < N; a++) {
                    TLRPC.Photo photo = photos.photos.get(a);
                    if (photo instanceof TLRPC.TL_photoEmpty) {
                        continue;
                    }
                    state.requery();
                    NativeByteBuffer data = new NativeByteBuffer(photo.getObjectSize());
                    photo.serializeToStream(data);
                    state.bindInteger(1, did);
                    state.bindLong(2, photo.id);
                    state.bindByteBuffer(3, data);
                    state.step();
                    data.reuse();
                }
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void emptyMessagesMedia(final ArrayList<Integer> mids) {
        storageQueue.postRunnable(() -> {
            try {
                ArrayList<File> filesToDelete = new ArrayList<>();
                final ArrayList<TLRPC.Message> messages = new ArrayList<>();
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid, date, uid FROM messages WHERE mid IN (%s)", TextUtils.join(",", mids)));
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        message.readAttachPath(data, getUserConfig().clientUserId);
                        data.reuse();
                        if (message.media != null) {
                            if (!addFilesToDelete(message, filesToDelete, true)) {
                                continue;
                            } else {
                                if (message.media.document != null) {
                                    message.media.document = new TLRPC.TL_documentEmpty();
                                } else if (message.media.photo != null) {
                                    message.media.photo = new TLRPC.TL_photoEmpty();
                                }
                            }
                            message.media.flags = message.media.flags &~ 1;
                            message.id = cursor.intValue(1);
                            message.date = cursor.intValue(2);
                            message.dialog_id = cursor.longValue(3);
                            messages.add(message);
                        }
                    }
                }
                cursor.dispose();
                if (!messages.isEmpty()) {
                    SQLitePreparedStatement state = database.executeFast("REPLACE INTO messages VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)");
                    for (int a = 0; a < messages.size(); a++) {
                        TLRPC.Message message = messages.get(a);

                        NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                        message.serializeToStream(data);

                        state.requery();
                        state.bindLong(1, message.id);
                        state.bindLong(2, message.dialog_id);
                        state.bindInteger(3, MessageObject.getUnreadFlags(message));
                        state.bindInteger(4, message.send_state);
                        state.bindInteger(5, message.date);
                        state.bindByteBuffer(6, data);
                        state.bindInteger(7, (MessageObject.isOut(message) || message.from_scheduled ? 1 : 0));
                        state.bindInteger(8, message.ttl);
                        if ((message.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                            state.bindInteger(9, message.views);
                        } else {
                            state.bindInteger(9, getMessageMediaType(message));
                        }
                        state.bindInteger(10, 0);
                        state.bindInteger(11, message.mentioned ? 1 : 0);
                        state.step();

                        data.reuse();
                    }
                    state.dispose();
                    AndroidUtilities.runOnUIThread(() -> {
                        for (int a = 0; a < messages.size(); a++) {
                            getNotificationCenter().postNotificationName(NotificationCenter.updateMessageMedia, messages.get(a));
                        }
                    });
                }
                getFileLoader().deleteFiles(filesToDelete, 0);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void updateMessagePollResults(long pollId, TLRPC.TL_poll poll, TLRPC.TL_pollResults results) {
        storageQueue.postRunnable(() -> {
            try {
                ArrayList<Long> mids = null;
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid FROM polls WHERE id = %d", pollId));
                while (cursor.next()) {
                    if (mids == null) {
                        mids = new ArrayList<>();
                    }
                    mids.add(cursor.longValue(0));
                }
                cursor.dispose();
                if (mids != null) {
                    database.beginTransaction();
                    for (int a = 0, N = mids.size(); a < N; a++) {
                        Long mid = mids.get(a);
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM messages WHERE mid = %d", mid));
                        if (cursor.next()) {
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                message.readAttachPath(data, getUserConfig().clientUserId);
                                data.reuse();
                                if (message.media instanceof TLRPC.TL_messageMediaPoll) {
                                    TLRPC.TL_messageMediaPoll media = (TLRPC.TL_messageMediaPoll) message.media;
                                    if (poll != null) {
                                        media.poll = poll;
                                    }
                                    if (results != null) {
                                        MessageObject.updatePollResults(media, results);
                                    }

                                    SQLitePreparedStatement state = database.executeFast("UPDATE messages SET data = ? WHERE mid = ?");
                                    data = new NativeByteBuffer(message.getObjectSize());
                                    message.serializeToStream(data);
                                    state.requery();
                                    state.bindByteBuffer(1, data);
                                    state.bindLong(2, mid);
                                    state.step();
                                    data.reuse();
                                    state.dispose();
                                }
                            }
                        } else {
                            database.executeFast(String.format(Locale.US, "DELETE FROM polls WHERE mid = %d", mid)).stepThis().dispose();
                        }
                        cursor.dispose();
                    }
                    database.commitTransaction();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void updateMessageReactions(long dialogId, int msgId, int channelId, TLRPC.TL_messageReactions reactions) {
        storageQueue.postRunnable(() -> {
            try {
                database.beginTransaction();
                long mid = msgId;
                if (channelId != 0) {
                    mid |= ((long) channelId) << 32;
                }
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM messages WHERE mid = %d", mid));
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        message.readAttachPath(data, getUserConfig().clientUserId);
                        data.reuse();
                        if (message != null) {
                            MessageObject.updateReactions(message, reactions);
                            SQLitePreparedStatement state = database.executeFast("UPDATE messages SET data = ? WHERE mid = ?");
                            data = new NativeByteBuffer(message.getObjectSize());
                            message.serializeToStream(data);
                            state.requery();
                            state.bindByteBuffer(1, data);
                            state.bindLong(2, mid);
                            state.step();
                            data.reuse();
                            state.dispose();
                        }
                    }
                }
                cursor.dispose();
                database.commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void getNewTask(final ArrayList<Integer> oldTask, final int channelId) {
        storageQueue.postRunnable(() -> {
            try {
                if (oldTask != null) {
                    String ids = TextUtils.join(",", oldTask);
                    database.executeFast(String.format(Locale.US, "DELETE FROM enc_tasks_v2 WHERE mid IN(%s)", ids)).stepThis().dispose();
                }
                int date = 0;
                int channelId1 = -1;
                ArrayList<Integer> arr = null;
                SQLiteCursor cursor = database.queryFinalized("SELECT mid, date FROM enc_tasks_v2 WHERE date = (SELECT min(date) FROM enc_tasks_v2)");
                while (cursor.next()) {
                    long mid = cursor.longValue(0);
                    if (channelId1 == -1) {
                        channelId1 = (int) (mid >> 32);
                        if (channelId1 < 0) {
                            channelId1 = 0;
                        }
                    }
                    date = cursor.intValue(1);
                    if (arr == null) {
                        arr = new ArrayList<>();
                    }
                    arr.add((int) mid);
                }
                cursor.dispose();
                getMessagesController().processLoadedDeleteTask(date, arr, channelId1);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void markMentionMessageAsRead(final int messageId, final int channelId, final long did) {
        storageQueue.postRunnable(() -> {
            try {
                long mid = messageId;
                if (channelId != 0) {
                    mid |= ((long) channelId) << 32;
                }

                database.executeFast(String.format(Locale.US, "UPDATE messages SET read_state = read_state | 2 WHERE mid = %d", mid)).stepThis().dispose();

                SQLiteCursor cursor = database.queryFinalized("SELECT unread_count_i FROM dialogs WHERE did = " + did);
                int old_mentions_count = 0;
                if (cursor.next()) {
                    old_mentions_count = Math.max(0, cursor.intValue(0) - 1);
                }
                cursor.dispose();
                database.executeFast(String.format(Locale.US, "UPDATE dialogs SET unread_count_i = %d WHERE did = %d", old_mentions_count, did)).stepThis().dispose();
                LongSparseArray<Integer> sparseArray = new LongSparseArray<>(1);
                sparseArray.put(did, old_mentions_count);
                getMessagesController().processDialogsUpdateRead(null, sparseArray);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void markMessageAsMention(final long mid) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast(String.format(Locale.US, "UPDATE messages SET mention = 1, read_state = read_state & ~2 WHERE mid = %d", mid)).stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void resetMentionsCount(final long did, final int count) {
        storageQueue.postRunnable(() -> {
            try {
                if (count == 0) {
                    database.executeFast(String.format(Locale.US, "UPDATE messages SET read_state = read_state | 2 WHERE uid = %d AND mention = 1 AND read_state IN(0, 1)", did)).stepThis().dispose();
                }
                database.executeFast(String.format(Locale.US, "UPDATE dialogs SET unread_count_i = %d WHERE did = %d", count, did)).stepThis().dispose();
                LongSparseArray<Integer> sparseArray = new LongSparseArray<>(1);
                sparseArray.put(did, count);
                getMessagesController().processDialogsUpdateRead(null, sparseArray);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void createTaskForMid(final int messageId, final int channelId, final int time, final int readTime, final int ttl, final boolean inner) {
        storageQueue.postRunnable(() -> {
            try {
                int minDate = (time > readTime ? time : readTime) + ttl;
                SparseArray<ArrayList<Long>> messages = new SparseArray<>();
                final ArrayList<Long> midsArray = new ArrayList<>();

                long mid = messageId;
                if (channelId != 0) {
                    mid |= ((long) channelId) << 32;
                }
                midsArray.add(mid);
                messages.put(minDate, midsArray);

                AndroidUtilities.runOnUIThread(() -> {
                    if (!inner) {
                        markMessagesContentAsRead(midsArray, 0);
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.messagesReadContent, midsArray);
                });

                SQLitePreparedStatement state = database.executeFast("REPLACE INTO enc_tasks_v2 VALUES(?, ?)");
                for (int a = 0; a < messages.size(); a++) {
                    int key = messages.keyAt(a);
                    ArrayList<Long> arr = messages.get(key);
                    for (int b = 0; b < arr.size(); b++) {
                        state.requery();
                        state.bindLong(1, arr.get(b));
                        state.bindInteger(2, key);
                        state.step();
                    }
                }
                state.dispose();
                database.executeFast(String.format(Locale.US, "UPDATE messages SET ttl = 0 WHERE mid = %d", mid)).stepThis().dispose();
                getMessagesController().didAddedNewTask(minDate, messages);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void createTaskForSecretChat(final int chatId, final int time, final int readTime, final int isOut, final ArrayList<Long> random_ids) {
        storageQueue.postRunnable(() -> {
            try {
                int minDate = Integer.MAX_VALUE;
                SparseArray<ArrayList<Long>> messages = new SparseArray<>();
                final ArrayList<Long> midsArray = new ArrayList<>();
                StringBuilder mids = new StringBuilder();
                SQLiteCursor cursor;
                if (random_ids == null) {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid, ttl FROM messages WHERE uid = %d AND out = %d AND read_state != 0 AND ttl > 0 AND date <= %d AND send_state = 0 AND media != 1", ((long) chatId) << 32, isOut, time));
                } else {
                    String ids = TextUtils.join(",", random_ids);
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.mid, m.ttl FROM messages as m INNER JOIN randoms as r ON m.mid = r.mid WHERE r.random_id IN (%s)", ids));
                }
                while (cursor.next()) {
                    int ttl = cursor.intValue(1);
                    long mid = cursor.intValue(0);
                    if (random_ids != null) {
                        midsArray.add(mid);
                    }
                    if (ttl <= 0) {
                        continue;
                    }
                    int date = (time > readTime ? time : readTime) + ttl;
                    minDate = Math.min(minDate, date);
                    ArrayList<Long> arr = messages.get(date);
                    if (arr == null) {
                        arr = new ArrayList<>();
                        messages.put(date, arr);
                    }
                    if (mids.length() != 0) {
                        mids.append(",");
                    }
                    mids.append(mid);
                    arr.add(mid);
                }
                cursor.dispose();

                if (random_ids != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        markMessagesContentAsRead(midsArray, 0);
                        getNotificationCenter().postNotificationName(NotificationCenter.messagesReadContent, midsArray);
                    });
                }

                if (messages.size() != 0) {
                    database.beginTransaction();
                    SQLitePreparedStatement state = database.executeFast("REPLACE INTO enc_tasks_v2 VALUES(?, ?)");
                    for (int a = 0; a < messages.size(); a++) {
                        int key = messages.keyAt(a);
                        ArrayList<Long> arr = messages.get(key);
                        for (int b = 0; b < arr.size(); b++) {
                            state.requery();
                            state.bindLong(1, arr.get(b));
                            state.bindInteger(2, key);
                            state.step();
                        }
                    }
                    state.dispose();
                    database.commitTransaction();
                    database.executeFast(String.format(Locale.US, "UPDATE messages SET ttl = 0 WHERE mid IN(%s)", mids.toString())).stepThis().dispose();
                    getMessagesController().didAddedNewTask(minDate, messages);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void updateDialogsWithReadMessagesInternal(final ArrayList<Integer> messages, final SparseLongArray inbox, SparseLongArray outbox, final ArrayList<Long> mentions) {
        try {
            LongSparseArray<Integer> dialogsToUpdate = new LongSparseArray<>();
            LongSparseArray<Integer> dialogsToUpdateMentions = new LongSparseArray<>();
            ArrayList<Integer> channelMentionsToReload = new ArrayList<>();

            if (!isEmpty(messages)) {
                String ids = TextUtils.join(",", messages);
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, read_state, out FROM messages WHERE mid IN(%s)", ids));
                while (cursor.next()) {
                    int out = cursor.intValue(2);
                    if (out != 0) {
                        continue;
                    }
                    int read_state = cursor.intValue(1);
                    if (read_state != 0) {
                        continue;
                    }
                    long uid = cursor.longValue(0);
                    Integer currentCount = dialogsToUpdate.get(uid);
                    if (currentCount == null) {
                        dialogsToUpdate.put(uid, 1);
                    } else {
                        dialogsToUpdate.put(uid, currentCount + 1);
                    }
                }
                cursor.dispose();
            } else {
                if (!isEmpty(inbox)) {
                    for (int b = 0; b < inbox.size(); b++) {
                        int key = inbox.keyAt(b);
                        long messageId = inbox.get(key);
                        SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM messages WHERE uid = %d AND mid > %d AND read_state IN(0,2) AND out = 0", key, messageId));
                        if (cursor.next()) {
                            dialogsToUpdate.put((long) key, cursor.intValue(0));
                        }
                        cursor.dispose();

                        SQLitePreparedStatement state = database.executeFast("UPDATE dialogs SET inbox_max = max((SELECT inbox_max FROM dialogs WHERE did = ?), ?) WHERE did = ?");
                        state.requery();
                        state.bindLong(1, key);
                        state.bindInteger(2, (int) messageId);
                        state.bindLong(3, key);
                        state.step();
                        state.dispose();
                    }
                }
                if (!isEmpty(mentions)) {
                    ArrayList<Long> notFoundMentions = new ArrayList<>(mentions);
                    String ids = TextUtils.join(",", mentions);
                    SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, read_state, out, mention, mid FROM messages WHERE mid IN(%s)", ids));
                    while (cursor.next()) {
                        long did = cursor.longValue(0);
                        notFoundMentions.remove(cursor.longValue(4));
                        if (cursor.intValue(1) < 2 && cursor.intValue(2) == 0 && cursor.intValue(3) == 1) {
                            Integer unread_count = dialogsToUpdateMentions.get(did);
                            if (unread_count == null) {
                                SQLiteCursor cursor2 = database.queryFinalized("SELECT unread_count_i FROM dialogs WHERE did = " + did);
                                int old_mentions_count = 0;
                                if (cursor2.next()) {
                                    old_mentions_count = cursor2.intValue(0);
                                }
                                cursor2.dispose();
                                dialogsToUpdateMentions.put(did, Math.max(0, old_mentions_count - 1));
                            } else {
                                dialogsToUpdateMentions.put(did, Math.max(0, unread_count - 1));
                            }
                        }
                    }
                    cursor.dispose();
                    for (int a = 0; a < notFoundMentions.size(); a++) {
                        int channelId = (int) (notFoundMentions.get(a) >> 32);
                        if (channelId > 0 && !channelMentionsToReload.contains(channelId)) {
                            channelMentionsToReload.add(channelId);
                        }
                    }
                }
                if (!isEmpty(outbox)) {
                    for (int b = 0; b < outbox.size(); b++) {
                        int key = outbox.keyAt(b);
                        long messageId = outbox.get(key);
                        SQLitePreparedStatement state = database.executeFast("UPDATE dialogs SET outbox_max = max((SELECT outbox_max FROM dialogs WHERE did = ?), ?) WHERE did = ?");
                        state.requery();
                        state.bindLong(1, key);
                        state.bindInteger(2, (int) messageId);
                        state.bindLong(3, key);
                        state.step();
                        state.dispose();
                    }
                }
            }

            if (dialogsToUpdate.size() > 0 || dialogsToUpdateMentions.size() > 0) {
                database.beginTransaction();
                if (dialogsToUpdate.size() > 0) {
                    SQLitePreparedStatement state = database.executeFast("UPDATE dialogs SET unread_count = ? WHERE did = ?");
                    for (int a = 0; a < dialogsToUpdate.size(); a++) {
                        state.requery();
                        state.bindInteger(1, dialogsToUpdate.valueAt(a));
                        state.bindLong(2, dialogsToUpdate.keyAt(a));
                        state.step();
                    }
                    state.dispose();
                }
                if (dialogsToUpdateMentions.size() > 0) {
                    SQLitePreparedStatement state = database.executeFast("UPDATE dialogs SET unread_count_i = ? WHERE did = ?");
                    for (int a = 0; a < dialogsToUpdateMentions.size(); a++) {
                        state.requery();
                        state.bindInteger(1, dialogsToUpdateMentions.valueAt(a));
                        state.bindLong(2, dialogsToUpdateMentions.keyAt(a));
                        state.step();
                    }
                    state.dispose();
                }
                database.commitTransaction();
            }

            getMessagesController().processDialogsUpdateRead(dialogsToUpdate, dialogsToUpdateMentions);
            if (!channelMentionsToReload.isEmpty()) {
                getMessagesController().reloadMentionsCountForChannels(channelMentionsToReload);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static boolean isEmpty(SparseArray<?> array) {
        return array == null || array.size() == 0;
    }

    private static boolean isEmpty(SparseLongArray array) {
        return array == null || array.size() == 0;
    }

    private static boolean isEmpty(List<?> array) {
        return array == null || array.isEmpty();
    }

    private static boolean isEmpty(SparseIntArray array) {
        return array == null || array.size() == 0;
    }

    private static boolean isEmpty(LongSparseArray<?> array) {
        return array == null || array.size() == 0;
    }

    public void updateDialogsWithReadMessages(final SparseLongArray inbox, final SparseLongArray outbox, final ArrayList<Long> mentions, boolean useQueue) {
        if (isEmpty(inbox) && isEmpty(mentions)) {
            return;
        }
        if (useQueue) {
            storageQueue.postRunnable(() -> updateDialogsWithReadMessagesInternal(null, inbox, outbox, mentions));
        } else {
            updateDialogsWithReadMessagesInternal(null, inbox, outbox, mentions);
        }
    }

    public void updateChatParticipants(final TLRPC.ChatParticipants participants) {
        if (participants == null) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                SQLiteCursor cursor = database.queryFinalized("SELECT info, pinned, online FROM chat_settings_v2 WHERE uid = " + participants.chat_id);
                TLRPC.ChatFull info = null;
                ArrayList<TLRPC.User> loadedUsers = new ArrayList<>();
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        info = TLRPC.ChatFull.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                        info.pinned_msg_id = cursor.intValue(1);
                        info.online_count = cursor.intValue(2);
                    }
                }
                cursor.dispose();
                if (info instanceof TLRPC.TL_chatFull) {
                    info.participants = participants;
                    final TLRPC.ChatFull finalInfo = info;
                    AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, finalInfo, 0, false, null));

                    SQLitePreparedStatement state = database.executeFast("REPLACE INTO chat_settings_v2 VALUES(?, ?, ?, ?)");
                    NativeByteBuffer data = new NativeByteBuffer(info.getObjectSize());
                    info.serializeToStream(data);
                    state.bindInteger(1, info.id);
                    state.bindByteBuffer(2, data);
                    state.bindInteger(3, info.pinned_msg_id);
                    state.bindInteger(4, info.online_count);
                    state.step();
                    state.dispose();
                    data.reuse();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void loadChannelAdmins(final int chatId) {
        storageQueue.postRunnable(() -> {
            try {
                SQLiteCursor cursor = database.queryFinalized("SELECT uid, rank FROM channel_admins_v2 WHERE did = " + chatId);
                SparseArray<String> ids = new SparseArray<>();
                while (cursor.next()) {
                    ids.put(cursor.intValue(0), cursor.stringValue(1));
                }
                cursor.dispose();
                getMessagesController().processLoadedChannelAdmins(ids, chatId, true);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void putChannelAdmins(final int chatId, final SparseArray<String> ids) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM channel_admins_v2 WHERE did = " + chatId).stepThis().dispose();
                database.beginTransaction();
                SQLitePreparedStatement state = database.executeFast("REPLACE INTO channel_admins_v2 VALUES(?, ?, ?)");
                int date = (int) (System.currentTimeMillis() / 1000);
                for (int a = 0; a < ids.size(); a++) {
                    state.requery();
                    state.bindInteger(1, chatId);
                    state.bindInteger(2, ids.keyAt(a));
                    state.bindString(3, ids.valueAt(a));
                    state.step();
                }
                state.dispose();
                database.commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void updateChannelUsers(final int channel_id, final ArrayList<TLRPC.ChannelParticipant> participants) {
        storageQueue.postRunnable(() -> {
            try {
                long did = -channel_id;
                database.executeFast("DELETE FROM channel_users_v2 WHERE did = " + did).stepThis().dispose();
                database.beginTransaction();
                SQLitePreparedStatement state = database.executeFast("REPLACE INTO channel_users_v2 VALUES(?, ?, ?, ?)");
                NativeByteBuffer data;
                int date = (int) (System.currentTimeMillis() / 1000);
                for (int a = 0; a < participants.size(); a++) {
                    TLRPC.ChannelParticipant participant = participants.get(a);
                    state.requery();
                    state.bindLong(1, did);
                    state.bindInteger(2, participant.user_id);
                    state.bindInteger(3, date);
                    data = new NativeByteBuffer(participant.getObjectSize());
                    participant.serializeToStream(data);
                    state.bindByteBuffer(4, data);
                    data.reuse();
                    state.step();
                    date--;
                }
                state.dispose();
                database.commitTransaction();
                loadChatInfo(channel_id, null, false, true);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void saveBotCache(final String key, final TLObject result) {
        if (result == null || TextUtils.isEmpty(key)) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                int currentDate = getConnectionsManager().getCurrentTime();
                if (result instanceof TLRPC.TL_messages_botCallbackAnswer) {
                    currentDate += ((TLRPC.TL_messages_botCallbackAnswer) result).cache_time;
                } else if (result instanceof TLRPC.TL_messages_botResults) {
                    currentDate += ((TLRPC.TL_messages_botResults) result).cache_time;
                }
                SQLitePreparedStatement state = database.executeFast("REPLACE INTO botcache VALUES(?, ?, ?)");
                NativeByteBuffer data = new NativeByteBuffer(result.getObjectSize());
                result.serializeToStream(data);
                state.bindString(1, key);
                state.bindInteger(2, currentDate);
                state.bindByteBuffer(3, data);
                state.step();
                state.dispose();
                data.reuse();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void getBotCache(final String key, final RequestDelegate requestDelegate) {
        if (key == null || requestDelegate == null) {
            return;
        }
        final int currentDate = getConnectionsManager().getCurrentTime();
        storageQueue.postRunnable(() -> {
            TLObject result = null;
            try {
                database.executeFast("DELETE FROM botcache WHERE date < " + currentDate).stepThis().dispose();
                SQLiteCursor cursor = database.queryFinalized( "SELECT data FROM botcache WHERE id = ?", key);
                if (cursor.next()) {
                    try {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            int constructor = data.readInt32(false);
                            if (constructor == TLRPC.TL_messages_botCallbackAnswer.constructor) {
                                result = TLRPC.TL_messages_botCallbackAnswer.TLdeserialize(data, constructor, false);
                            } else {
                                result = TLRPC.messages_BotResults.TLdeserialize(data, constructor, false);
                            }
                            data.reuse();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                cursor.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                requestDelegate.run(result, null);
            }
        });
    }

    public void loadUserInfo(TLRPC.User user, final boolean force, int classGuid) {
        if (user == null) {
            return;
        }
        storageQueue.postRunnable(() -> {
            MessageObject pinnedMessageObject = null;
            TLRPC.UserFull info = null;
            try {
                SQLiteCursor cursor = database.queryFinalized("SELECT info, pinned FROM user_settings WHERE uid = " + user.id);
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        info = TLRPC.UserFull.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                        info.pinned_msg_id = cursor.intValue(1);
                    }
                }
                cursor.dispose();
                if (info != null && info.pinned_msg_id != 0) {
                    pinnedMessageObject = getMediaDataController().loadPinnedMessage(user.id, 0, info.pinned_msg_id, false);
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                getMessagesController().processUserInfo(user, info, true, force, pinnedMessageObject, classGuid);
            }
        });
    }

    public void updateUserInfo(final TLRPC.UserFull info, final boolean ifExist) {
        storageQueue.postRunnable(() -> {
            try {
                if (ifExist) {
                    SQLiteCursor cursor = database.queryFinalized("SELECT uid FROM user_settings WHERE uid = " + info.user.id);
                    boolean exist = cursor.next();
                    cursor.dispose();
                    if (!exist) {
                        return;
                    }
                }
                SQLitePreparedStatement state = database.executeFast("REPLACE INTO user_settings VALUES(?, ?, ?)");
                NativeByteBuffer data = new NativeByteBuffer(info.getObjectSize());
                info.serializeToStream(data);
                state.bindInteger(1, info.user.id);
                state.bindByteBuffer(2, data);
                state.bindInteger(3, info.pinned_msg_id);
                state.step();
                state.dispose();
                data.reuse();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void updateChatInfo(final TLRPC.ChatFull info, final boolean ifExist) {
        storageQueue.postRunnable(() -> {
            try {
                int currentOnline = -1;
                SQLiteCursor cursor = database.queryFinalized("SELECT online FROM chat_settings_v2 WHERE uid = " + info.id);
                if (cursor.next()) {
                    currentOnline = cursor.intValue(0);
                }
                cursor.dispose();
                if (ifExist && currentOnline == -1) {
                    return;
                }

                if (currentOnline >= 0 && (info.flags & 8192) == 0) {
                    info.online_count = currentOnline;
                }

                SQLitePreparedStatement state = database.executeFast("REPLACE INTO chat_settings_v2 VALUES(?, ?, ?, ?)");
                NativeByteBuffer data = new NativeByteBuffer(info.getObjectSize());
                info.serializeToStream(data);
                state.bindInteger(1, info.id);
                state.bindByteBuffer(2, data);
                state.bindInteger(3, info.pinned_msg_id);
                state.bindInteger(4, info.online_count);
                state.step();
                state.dispose();
                data.reuse();

                if (info instanceof TLRPC.TL_channelFull) {
                    cursor = database.queryFinalized("SELECT inbox_max, outbox_max FROM dialogs WHERE did = " + (-info.id));
                    if (cursor.next()) {
                        int inbox_max = cursor.intValue(0);
                        if (inbox_max < info.read_inbox_max_id) {
                            int outbox_max = cursor.intValue(1);

                            state = database.executeFast("UPDATE dialogs SET unread_count = ?, inbox_max = ?, outbox_max = ? WHERE did = ?");
                            state.bindInteger(1, info.unread_count);
                            state.bindInteger(2, info.read_inbox_max_id);
                            state.bindInteger(3, Math.max(outbox_max, info.read_outbox_max_id));
                            state.bindLong(4, -info.id);
                            state.step();
                            state.dispose();
                        }
                    }
                    cursor.dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void updateUserPinnedMessage(final int userId, final int messageId) {
        storageQueue.postRunnable(() -> {
            try {
                SQLiteCursor cursor = database.queryFinalized("SELECT info, pinned FROM user_settings WHERE uid = " + userId);
                TLRPC.UserFull info = null;
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        info = TLRPC.UserFull.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                        info.pinned_msg_id = cursor.intValue(1);
                    }
                }
                cursor.dispose();
                if (info instanceof TLRPC.UserFull) {
                    info.pinned_msg_id = messageId;
                    info.flags |= 64;

                    final TLRPC.UserFull finalInfo = info;
                    AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.userInfoDidLoad, userId, finalInfo, null));

                    SQLitePreparedStatement state = database.executeFast("REPLACE INTO user_settings VALUES(?, ?, ?)");
                    NativeByteBuffer data = new NativeByteBuffer(info.getObjectSize());
                    info.serializeToStream(data);
                    state.bindInteger(1, userId);
                    state.bindByteBuffer(2, data);
                    state.bindInteger(3, info.pinned_msg_id);
                    state.step();
                    state.dispose();
                    data.reuse();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void updateChatOnlineCount(final int channelId, final int onlineCount) {
        storageQueue.postRunnable(() -> {
            try {
                SQLitePreparedStatement state = database.executeFast("UPDATE chat_settings_v2 SET online = ? WHERE uid = ?");
                state.requery();
                state.bindInteger(1, onlineCount);
                state.bindInteger(2, channelId);
                state.step();
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void updateChatPinnedMessage(final int channelId, final int messageId) {
        storageQueue.postRunnable(() -> {
            try {
                SQLiteCursor cursor = database.queryFinalized("SELECT info, pinned, online FROM chat_settings_v2 WHERE uid = " + channelId);
                TLRPC.ChatFull info = null;
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        info = TLRPC.ChatFull.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                        info.pinned_msg_id = cursor.intValue(1);
                        info.online_count = cursor.intValue(2);
                    }
                }
                cursor.dispose();
                if (info != null) {
                    if (info instanceof TLRPC.TL_channelFull) {
                        info.pinned_msg_id = messageId;
                        info.flags |= 32;
                    } else if (info instanceof TLRPC.TL_chatFull) {
                        info.pinned_msg_id = messageId;
                        info.flags |= 64;
                    }

                    final TLRPC.ChatFull finalInfo = info;
                    AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, finalInfo, 0, false, null));

                    SQLitePreparedStatement state = database.executeFast("REPLACE INTO chat_settings_v2 VALUES(?, ?, ?, ?)");
                    NativeByteBuffer data = new NativeByteBuffer(info.getObjectSize());
                    info.serializeToStream(data);
                    state.bindInteger(1, channelId);
                    state.bindByteBuffer(2, data);
                    state.bindInteger(3, info.pinned_msg_id);
                    state.bindInteger(4, info.online_count);
                    state.step();
                    state.dispose();
                    data.reuse();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void updateChatInfo(final int chat_id, final int user_id, final int what, final int invited_id, final int version) {
        storageQueue.postRunnable(() -> {
            try {
                SQLiteCursor cursor = database.queryFinalized("SELECT info, pinned, online FROM chat_settings_v2 WHERE uid = " + chat_id);
                TLRPC.ChatFull info = null;
                ArrayList<TLRPC.User> loadedUsers = new ArrayList<>();
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        info = TLRPC.ChatFull.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                        info.pinned_msg_id = cursor.intValue(1);
                        info.online_count = cursor.intValue(2);
                    }
                }
                cursor.dispose();
                if (info instanceof TLRPC.TL_chatFull) {
                    if (what == 1) {
                        for (int a = 0; a < info.participants.participants.size(); a++) {
                            TLRPC.ChatParticipant participant = info.participants.participants.get(a);
                            if (participant.user_id == user_id) {
                                info.participants.participants.remove(a);
                                break;
                            }
                        }
                    } else if (what == 0) {
                        for (TLRPC.ChatParticipant part : info.participants.participants) {
                            if (part.user_id == user_id) {
                                return;
                            }
                        }
                        TLRPC.TL_chatParticipant participant = new TLRPC.TL_chatParticipant();
                        participant.user_id = user_id;
                        participant.inviter_id = invited_id;
                        participant.date = getConnectionsManager().getCurrentTime();
                        info.participants.participants.add(participant);
                    } else if (what == 2) {
                        for (int a = 0; a < info.participants.participants.size(); a++) {
                            TLRPC.ChatParticipant participant = info.participants.participants.get(a);
                            if (participant.user_id == user_id) {
                                TLRPC.ChatParticipant newParticipant;
                                if (invited_id == 1) {
                                    newParticipant = new TLRPC.TL_chatParticipantAdmin();
                                    newParticipant.user_id = participant.user_id;
                                    newParticipant.date = participant.date;
                                    newParticipant.inviter_id = participant.inviter_id;
                                } else {
                                    newParticipant = new TLRPC.TL_chatParticipant();
                                    newParticipant.user_id = participant.user_id;
                                    newParticipant.date = participant.date;
                                    newParticipant.inviter_id = participant.inviter_id;
                                }
                                info.participants.participants.set(a, newParticipant);
                                break;
                            }
                        }
                    }
                    info.participants.version = version;

                    final TLRPC.ChatFull finalInfo = info;
                    AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, finalInfo, 0, false, null));

                    SQLitePreparedStatement state = database.executeFast("REPLACE INTO chat_settings_v2 VALUES(?, ?, ?, ?)");
                    NativeByteBuffer data = new NativeByteBuffer(info.getObjectSize());
                    info.serializeToStream(data);
                    state.bindInteger(1, chat_id);
                    state.bindByteBuffer(2, data);
                    state.bindInteger(3, info.pinned_msg_id);
                    state.bindInteger(4, info.online_count);
                    state.step();
                    state.dispose();
                    data.reuse();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public boolean isMigratedChat(final int chat_id) {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final boolean[] result = new boolean[1];
        storageQueue.postRunnable(() -> {
            try {
                SQLiteCursor cursor = database.queryFinalized("SELECT info FROM chat_settings_v2 WHERE uid = " + chat_id);
                TLRPC.ChatFull info = null;
                ArrayList<TLRPC.User> loadedUsers = new ArrayList<>();
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        info = TLRPC.ChatFull.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                    }
                }
                cursor.dispose();
                result[0] = info instanceof TLRPC.TL_channelFull && info.migrated_from_chat_id != 0;
                if (countDownLatch != null) {
                    countDownLatch.countDown();
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (countDownLatch != null) {
                    countDownLatch.countDown();
                }
            }
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result[0];
    }

    public TLRPC.ChatFull loadChatInfo(final int chat_id, final CountDownLatch countDownLatch, final boolean force, final boolean byChannelUsers) {
        TLRPC.ChatFull[] result = new TLRPC.ChatFull[1];
        storageQueue.postRunnable(() -> {
            MessageObject pinnedMessageObject = null;
            TLRPC.ChatFull info = null;
            ArrayList<TLRPC.User> loadedUsers = new ArrayList<>();
            try {
                SQLiteCursor cursor = database.queryFinalized("SELECT info, pinned, online FROM chat_settings_v2 WHERE uid = " + chat_id);
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        info = TLRPC.ChatFull.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                        info.pinned_msg_id = cursor.intValue(1);
                        info.online_count = cursor.intValue(2);
                    }
                }
                cursor.dispose();

                if (info instanceof TLRPC.TL_chatFull) {
                    StringBuilder usersToLoad = new StringBuilder();
                    for (int a = 0; a < info.participants.participants.size(); a++) {
                        TLRPC.ChatParticipant c = info.participants.participants.get(a);
                        if (usersToLoad.length() != 0) {
                            usersToLoad.append(",");
                        }
                        usersToLoad.append(c.user_id);
                    }
                    if (usersToLoad.length() != 0) {
                        getUsersInternal(usersToLoad.toString(), loadedUsers);
                    }
                } else if (info instanceof TLRPC.TL_channelFull) {
                    cursor = database.queryFinalized("SELECT us.data, us.status, cu.data, cu.date FROM channel_users_v2 as cu LEFT JOIN users as us ON us.uid = cu.uid WHERE cu.did = " + (-chat_id) + " ORDER BY cu.date DESC");
                    info.participants = new TLRPC.TL_chatParticipants();
                    while (cursor.next()) {
                        try {
                            TLRPC.User user = null;
                            TLRPC.ChannelParticipant participant = null;
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                user = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                                data.reuse();
                            }
                            data = cursor.byteBufferValue(2);
                            if (data != null) {
                                participant = TLRPC.ChannelParticipant.TLdeserialize(data, data.readInt32(false), false);
                                data.reuse();
                            }
                            if (user != null && participant != null) {
                                if (user.status != null) {
                                    user.status.expires = cursor.intValue(1);
                                }
                                loadedUsers.add(user);
                                participant.date = cursor.intValue(3);
                                TLRPC.TL_chatChannelParticipant chatChannelParticipant = new TLRPC.TL_chatChannelParticipant();
                                chatChannelParticipant.user_id = participant.user_id;
                                chatChannelParticipant.date = participant.date;
                                chatChannelParticipant.inviter_id = participant.inviter_id;
                                chatChannelParticipant.channelParticipant = participant;
                                info.participants.participants.add(chatChannelParticipant);
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    cursor.dispose();
                    StringBuilder usersToLoad = new StringBuilder();
                    for (int a = 0; a < info.bot_info.size(); a++) {
                        TLRPC.BotInfo botInfo = info.bot_info.get(a);
                        if (usersToLoad.length() != 0) {
                            usersToLoad.append(",");
                        }
                        usersToLoad.append(botInfo.user_id);
                    }
                    if (usersToLoad.length() != 0) {
                        getUsersInternal(usersToLoad.toString(), loadedUsers);
                    }
                }
                if (info != null && info.pinned_msg_id != 0) {
                    pinnedMessageObject = getMediaDataController().loadPinnedMessage(-chat_id, info instanceof TLRPC.TL_channelFull ? chat_id : 0, info.pinned_msg_id, false);
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                result[0] = info;
                getMessagesController().processChatInfo(chat_id, info, loadedUsers, true, force, byChannelUsers, pinnedMessageObject);
                if (countDownLatch != null) {
                    countDownLatch.countDown();
                }
            }
        });
        if (countDownLatch != null) {
            try {
                countDownLatch.await();
            } catch (Throwable ignore) {

            }
        }
        return result[0];
    }

    public void processPendingRead(final long dialog_id, final long maxPositiveId, final long maxNegativeId, final boolean isChannel, final int scheduledCount) {
        final int maxDate = lastSavedDate;
        storageQueue.postRunnable(() -> {
            try {
                long currentMaxId = 0;
                int unreadCount = 0;
                long last_mid = 0;
                SQLiteCursor cursor = database.queryFinalized("SELECT unread_count, inbox_max, last_mid FROM dialogs WHERE did = " + dialog_id);
                if (cursor.next()) {
                    unreadCount = cursor.intValue(0);
                    currentMaxId = cursor.intValue(1);
                    last_mid = cursor.longValue(2);
                }
                cursor.dispose();

                database.beginTransaction();
                SQLitePreparedStatement state;

                int lower_id = (int) dialog_id;

                if (lower_id != 0) {
                    currentMaxId = Math.max(currentMaxId, (int) maxPositiveId);
                    if (isChannel) {
                        currentMaxId |= ((long) -lower_id) << 32;
                    }

                    state = database.executeFast("UPDATE messages SET read_state = read_state | 1 WHERE uid = ? AND mid <= ? AND read_state IN(0,2) AND out = 0");
                    state.requery();
                    state.bindLong(1, dialog_id);
                    state.bindLong(2, currentMaxId);
                    state.step();
                    state.dispose();

                    if (currentMaxId >= last_mid) {
                        unreadCount = 0;
                    } else {
                        int updatedCount = 0;
                        cursor = database.queryFinalized("SELECT changes()");
                        if (cursor.next()) {
                            updatedCount = cursor.intValue(0) + scheduledCount;
                        }
                        cursor.dispose();
                        unreadCount = Math.max(0, unreadCount - updatedCount);
                    }

                    state = database.executeFast("DELETE FROM unread_push_messages WHERE uid = ? AND mid <= ?");
                    state.requery();
                    state.bindLong(1, dialog_id);
                    state.bindLong(2, currentMaxId);
                    state.step();
                    state.dispose();

                    state = database.executeFast("DELETE FROM unread_push_messages WHERE uid = ? AND date <= ?");
                    state.requery();
                    state.bindLong(1, dialog_id);
                    state.bindLong(2, maxDate);
                    state.step();
                    state.dispose();
                } else {
                    currentMaxId = (int) maxNegativeId;

                    state = database.executeFast("UPDATE messages SET read_state = read_state | 1 WHERE uid = ? AND mid >= ? AND read_state IN(0,2) AND out = 0");
                    state.requery();
                    state.bindLong(1, dialog_id);
                    state.bindLong(2, currentMaxId);
                    state.step();
                    state.dispose();

                    if (currentMaxId <= last_mid) {
                        unreadCount = 0;
                    } else {
                        int updatedCount = 0;
                        cursor = database.queryFinalized("SELECT changes()");
                        if (cursor.next()) {
                            updatedCount = cursor.intValue(0) + scheduledCount;
                        }
                        cursor.dispose();
                        unreadCount = Math.max(0, unreadCount - updatedCount);
                    }
                }

                state = database.executeFast("UPDATE dialogs SET unread_count = ?, inbox_max = ? WHERE did = ?");
                state.requery();
                state.bindInteger(1, unreadCount);
                state.bindInteger(2, (int) currentMaxId);
                state.bindLong(3, dialog_id);
                state.step();
                state.dispose();

                database.commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void putContacts(ArrayList<TLRPC.TL_contact> contacts, final boolean deleteAll) {
        if (contacts.isEmpty() && !deleteAll) {
            return;
        }
        final ArrayList<TLRPC.TL_contact> contactsCopy = new ArrayList<>(contacts);
        storageQueue.postRunnable(() -> {
            try {
                if (deleteAll) {
                    database.executeFast("DELETE FROM contacts WHERE 1").stepThis().dispose();
                }
                database.beginTransaction();
                SQLitePreparedStatement state = database.executeFast("REPLACE INTO contacts VALUES(?, ?)");
                for (int a = 0; a < contactsCopy.size(); a++) {
                    TLRPC.TL_contact contact = contactsCopy.get(a);
                    state.requery();
                    state.bindInteger(1, contact.user_id);
                    state.bindInteger(2, contact.mutual ? 1 : 0);
                    state.step();
                }
                state.dispose();
                database.commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void deleteContacts(final ArrayList<Integer> uids) {
        if (uids == null || uids.isEmpty()) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                String ids = TextUtils.join(",", uids);
                database.executeFast("DELETE FROM contacts WHERE uid IN(" + ids + ")").stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void applyPhoneBookUpdates(final String adds, final String deletes) {
        if (TextUtils.isEmpty(adds)) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                if (adds.length() != 0) {
                    database.executeFast(String.format(Locale.US, "UPDATE user_phones_v7 SET deleted = 0 WHERE sphone IN(%s)", adds)).stepThis().dispose();
                }
                if (deletes.length() != 0) {
                    database.executeFast(String.format(Locale.US, "UPDATE user_phones_v7 SET deleted = 1 WHERE sphone IN(%s)", deletes)).stepThis().dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void putCachedPhoneBook(final HashMap<String, ContactsController.Contact> contactHashMap, final boolean migrate, final boolean delete) {
        if (contactHashMap == null || contactHashMap.isEmpty() && !migrate && !delete) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d(currentAccount + " save contacts to db " + contactHashMap.size());
                }
                database.executeFast("DELETE FROM user_contacts_v7 WHERE 1").stepThis().dispose();
                database.executeFast("DELETE FROM user_phones_v7 WHERE 1").stepThis().dispose();

                database.beginTransaction();
                SQLitePreparedStatement state = database.executeFast("REPLACE INTO user_contacts_v7 VALUES(?, ?, ?, ?, ?)");
                SQLitePreparedStatement state2 = database.executeFast("REPLACE INTO user_phones_v7 VALUES(?, ?, ?, ?)");
                for (HashMap.Entry<String, ContactsController.Contact> entry : contactHashMap.entrySet()) {
                    ContactsController.Contact contact = entry.getValue();
                    if (contact.phones.isEmpty() || contact.shortPhones.isEmpty()) {
                        continue;
                    }
                    state.requery();
                    state.bindString(1, contact.key);
                    state.bindInteger(2, contact.contact_id);
                    state.bindString(3, contact.first_name);
                    state.bindString(4, contact.last_name);
                    state.bindInteger(5, contact.imported);
                    state.step();
                    for (int a = 0; a < contact.phones.size(); a++) {
                        state2.requery();
                        state2.bindString(1, contact.key);
                        state2.bindString(2, contact.phones.get(a));
                        state2.bindString(3, contact.shortPhones.get(a));
                        state2.bindInteger(4, contact.phoneDeleted.get(a));
                        state2.step();
                    }
                }
                state.dispose();
                state2.dispose();
                database.commitTransaction();
                if (migrate) {
                    database.executeFast("DROP TABLE IF EXISTS user_contacts_v6;").stepThis().dispose();
                    database.executeFast("DROP TABLE IF EXISTS user_phones_v6;").stepThis().dispose();
                    getCachedPhoneBook(false);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void getCachedPhoneBook(final boolean byError) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized("SELECT name FROM sqlite_master WHERE type='table' AND name='user_contacts_v6'");
                boolean migrate = cursor.next();
                cursor.dispose();
                cursor = null;
                if (migrate) {
                    int count = 16;
                    cursor = database.queryFinalized("SELECT COUNT(uid) FROM user_contacts_v6 WHERE 1");
                    if (cursor.next()) {
                        count = Math.min(5000, cursor.intValue(0));
                    }
                    cursor.dispose();

                    SparseArray<ContactsController.Contact> contactHashMap = new SparseArray<>(count);
                    cursor = database.queryFinalized("SELECT us.uid, us.fname, us.sname, up.phone, up.sphone, up.deleted, us.imported FROM user_contacts_v6 as us LEFT JOIN user_phones_v6 as up ON us.uid = up.uid WHERE 1");
                    while (cursor.next()) {
                        int uid = cursor.intValue(0);
                        ContactsController.Contact contact = contactHashMap.get(uid);
                        if (contact == null) {
                            contact = new ContactsController.Contact();
                            contact.first_name = cursor.stringValue(1);
                            contact.last_name = cursor.stringValue(2);
                            contact.imported = cursor.intValue(6);
                            if (contact.first_name == null) {
                                contact.first_name = "";
                            }
                            if (contact.last_name == null) {
                                contact.last_name = "";
                            }
                            contact.contact_id = uid;
                            contactHashMap.put(uid, contact);
                        }
                        String phone = cursor.stringValue(3);
                        if (phone == null) {
                            continue;
                        }
                        contact.phones.add(phone);
                        String sphone = cursor.stringValue(4);
                        if (sphone == null) {
                            continue;
                        }
                        if (sphone.length() == 8 && phone.length() != 8) {
                            sphone = PhoneFormat.stripExceptNumbers(phone);
                        }
                        contact.shortPhones.add(sphone);
                        contact.phoneDeleted.add(cursor.intValue(5));
                        contact.phoneTypes.add("");
                        if (contactHashMap.size() == 5000) {
                            break;
                        }
                    }
                    cursor.dispose();
                    cursor = null;
                    getContactsController().migratePhoneBookToV7(contactHashMap);
                    return;
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }

            int count = 16;
            int currentContactsCount = 0;
            int start = 0;
            try {
                cursor = database.queryFinalized("SELECT COUNT(key) FROM user_contacts_v7 WHERE 1");
                if (cursor.next()) {
                    currentContactsCount = cursor.intValue(0);
                    count = Math.min(5000, currentContactsCount);
                    if (currentContactsCount > 5000) {
                        start = currentContactsCount - 5000;
                    }
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d(currentAccount + " current cached contacts count = " + currentContactsCount);
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }

            HashMap<String, ContactsController.Contact> contactHashMap = new HashMap<>(count);
            try {
                if (start != 0) {
                    cursor = database.queryFinalized("SELECT us.key, us.uid, us.fname, us.sname, up.phone, up.sphone, up.deleted, us.imported FROM user_contacts_v7 as us LEFT JOIN user_phones_v7 as up ON us.key = up.key WHERE 1 LIMIT " + 0 + "," + currentContactsCount);
                } else {
                    cursor = database.queryFinalized("SELECT us.key, us.uid, us.fname, us.sname, up.phone, up.sphone, up.deleted, us.imported FROM user_contacts_v7 as us LEFT JOIN user_phones_v7 as up ON us.key = up.key WHERE 1");
                }
                while (cursor.next()) {
                    String key = cursor.stringValue(0);
                    ContactsController.Contact contact = contactHashMap.get(key);
                    if (contact == null) {
                        contact = new ContactsController.Contact();
                        contact.contact_id = cursor.intValue(1);
                        contact.first_name = cursor.stringValue(2);
                        contact.last_name = cursor.stringValue(3);
                        contact.imported = cursor.intValue(7);
                        if (contact.first_name == null) {
                            contact.first_name = "";
                        }
                        if (contact.last_name == null) {
                            contact.last_name = "";
                        }
                        contactHashMap.put(key, contact);
                    }
                    String phone = cursor.stringValue(4);
                    if (phone == null) {
                        continue;
                    }
                    contact.phones.add(phone);
                    String sphone = cursor.stringValue(5);
                    if (sphone == null) {
                        continue;
                    }
                    if (sphone.length() == 8 && phone.length() != 8) {
                        sphone = PhoneFormat.stripExceptNumbers(phone);
                    }
                    contact.shortPhones.add(sphone);
                    contact.phoneDeleted.add(cursor.intValue(6));
                    contact.phoneTypes.add("");
                    if (contactHashMap.size() == 5000) {
                        break;
                    }
                }
                cursor.dispose();
                cursor = null;
            } catch (Exception e) {
                contactHashMap.clear();
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            getContactsController().performSyncPhoneBook(contactHashMap, true, true, false, false, !byError, false);
        });
    }

    public void getContacts() {
        storageQueue.postRunnable(() -> {
            ArrayList<TLRPC.TL_contact> contacts = new ArrayList<>();
            ArrayList<TLRPC.User> users = new ArrayList<>();
            try {
                SQLiteCursor cursor = database.queryFinalized("SELECT * FROM contacts WHERE 1");
                StringBuilder uids = new StringBuilder();
                while (cursor.next()) {
                    int user_id = cursor.intValue(0);
                    TLRPC.TL_contact contact = new TLRPC.TL_contact();
                    contact.user_id = user_id;
                    contact.mutual = cursor.intValue(1) == 1;
                    if (uids.length() != 0) {
                        uids.append(",");
                    }
                    contacts.add(contact);
                    uids.append(contact.user_id);
                }
                cursor.dispose();

                if (uids.length() != 0) {
                    getUsersInternal(uids.toString(), users);
                }
            } catch (Exception e) {
                contacts.clear();
                users.clear();
                FileLog.e(e);
            }
            getContactsController().processLoadedContacts(contacts, users, 1);
        });
    }

    public void getUnsentMessages(final int count) {
        storageQueue.postRunnable(() -> {
            try {
                SparseArray<TLRPC.Message> messageHashMap = new SparseArray<>();
                ArrayList<TLRPC.Message> messages = new ArrayList<>();
                ArrayList<TLRPC.Message> scheduledMessages = new ArrayList<>();
                ArrayList<TLRPC.User> users = new ArrayList<>();
                ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();

                ArrayList<Integer> usersToLoad = new ArrayList<>();
                ArrayList<Integer> chatsToLoad = new ArrayList<>();
                ArrayList<Integer> encryptedChatIds = new ArrayList<>();

                SQLiteCursor cursor = database.queryFinalized("SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.uid, s.seq_in, s.seq_out, m.ttl FROM messages as m LEFT JOIN randoms as r ON r.mid = m.mid LEFT JOIN messages_seq as s ON m.mid = s.mid WHERE (m.mid < 0 AND m.send_state = 1) OR (m.mid > 0 AND m.send_state = 3) ORDER BY m.mid DESC LIMIT " + count);
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(1);
                    if (data != null) {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        message.send_state = cursor.intValue(2);
                        message.readAttachPath(data, getUserConfig().clientUserId);
                        data.reuse();
                        if (messageHashMap.indexOfKey(message.id) < 0) {
                            MessageObject.setUnreadFlags(message, cursor.intValue(0));
                            message.id = cursor.intValue(3);
                            message.date = cursor.intValue(4);
                            if (!cursor.isNull(5)) {
                                message.random_id = cursor.longValue(5);
                            }
                            message.dialog_id = cursor.longValue(6);
                            message.seq_in = cursor.intValue(7);
                            message.seq_out = cursor.intValue(8);
                            message.ttl = cursor.intValue(9);
                            messages.add(message);
                            messageHashMap.put(message.id, message);

                            int lower_id = (int) message.dialog_id;
                            int high_id = (int) (message.dialog_id >> 32);

                            if (lower_id != 0) {
                                if (lower_id < 0) {
                                    if (!chatsToLoad.contains(-lower_id)) {
                                        chatsToLoad.add(-lower_id);
                                    }
                                } else {
                                    if (!usersToLoad.contains(lower_id)) {
                                        usersToLoad.add(lower_id);
                                    }
                                }
                            } else {
                                if (!encryptedChatIds.contains(high_id)) {
                                    encryptedChatIds.add(high_id);
                                }
                            }

                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);

                            if (message.send_state != 3 && (message.to_id.channel_id == 0 && !MessageObject.isUnread(message) && lower_id != 0 || message.id > 0)) {
                                message.send_state = 0;
                            }
                        }
                    }
                }
                cursor.dispose();

                cursor = database.queryFinalized("SELECT m.data, m.send_state, m.mid, m.date, r.random_id, m.uid, m.ttl FROM scheduled_messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE (m.mid < 0 AND m.send_state = 1) OR (m.mid > 0 AND m.send_state = 3) ORDER BY date ASC");
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        message.send_state = cursor.intValue(1);
                        message.readAttachPath(data, getUserConfig().clientUserId);
                        data.reuse();
                        if (messageHashMap.indexOfKey(message.id) < 0) {
                            message.id = cursor.intValue(2);
                            message.date = cursor.intValue(3);
                            if (!cursor.isNull(4)) {
                                message.random_id = cursor.longValue(4);
                            }
                            message.dialog_id = cursor.longValue(5);
                            message.ttl = cursor.intValue(6);
                            scheduledMessages.add(message);
                            messageHashMap.put(message.id, message);

                            int lower_id = (int) message.dialog_id;
                            int high_id = (int) (message.dialog_id >> 32);

                            if (lower_id != 0) {
                                if (lower_id < 0) {
                                    if (!chatsToLoad.contains(-lower_id)) {
                                        chatsToLoad.add(-lower_id);
                                    }
                                } else {
                                    if (!usersToLoad.contains(lower_id)) {
                                        usersToLoad.add(lower_id);
                                    }
                                }
                            } else {
                                if (!encryptedChatIds.contains(high_id)) {
                                    encryptedChatIds.add(high_id);
                                }
                            }

                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);

                            if (message.send_state != 3 && (message.to_id.channel_id == 0 && !MessageObject.isUnread(message) && lower_id != 0 || message.id > 0)) {
                                message.send_state = 0;
                            }
                        }
                    }
                }
                cursor.dispose();

                if (!encryptedChatIds.isEmpty()) {
                    getEncryptedChatsInternal(TextUtils.join(",", encryptedChatIds), encryptedChats, usersToLoad);
                }

                if (!usersToLoad.isEmpty()) {
                    getUsersInternal(TextUtils.join(",", usersToLoad), users);
                }

                if (!chatsToLoad.isEmpty()) {
                    StringBuilder stringToLoad = new StringBuilder();
                    for (int a = 0; a < chatsToLoad.size(); a++) {
                        Integer cid = chatsToLoad.get(a);
                        if (stringToLoad.length() != 0) {
                            stringToLoad.append(",");
                        }
                        stringToLoad.append(cid);
                    }
                    getChatsInternal(stringToLoad.toString(), chats);
                }

                getSendMessagesHelper().processUnsentMessages(messages, scheduledMessages, users, chats, encryptedChats);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public boolean checkMessageByRandomId(final long random_id) {
        final boolean[] result = new boolean[1];
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT random_id FROM randoms WHERE random_id = %d", random_id));
                if (cursor.next()) {
                    result[0] = true;
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            countDownLatch.countDown();
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result[0];
    }

    public boolean checkMessageId(final long dialog_id, final int mid) {
        final boolean[] result = new boolean[1];
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid FROM messages WHERE uid = %d AND mid = %d", dialog_id, mid));
                if (cursor.next()) {
                    result[0] = true;
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            countDownLatch.countDown();
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result[0];
    }

    public void getUnreadMention(final long dialog_id, final IntCallback callback) {
        storageQueue.postRunnable(() -> {
            try {
                final int result;
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT MIN(mid) FROM messages WHERE uid = %d AND mention = 1 AND read_state IN(0, 1)", dialog_id));
                if (cursor.next()) {
                    result = cursor.intValue(0);
                } else {
                    result = 0;
                }
                cursor.dispose();
                AndroidUtilities.runOnUIThread(() -> callback.run(result));
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void getMessagesCount(final long dialog_id, final IntCallback callback) {
        storageQueue.postRunnable(() -> {
            try {
                final int result;
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM messages WHERE uid = %d", dialog_id));
                if (cursor.next()) {
                    result = cursor.intValue(0);
                } else {
                    result = 0;
                }
                cursor.dispose();
                AndroidUtilities.runOnUIThread(() -> callback.run(result));
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void getMessages(final long dialog_id, final int count, final int max_id, final int offset_date, final int minDate, final int classGuid, final int load_type, final boolean isChannel, final boolean scheduled, final int loadIndex) {
        storageQueue.postRunnable(() -> {
            TLRPC.TL_messages_messages res = new TLRPC.TL_messages_messages();
            int currentUserId = getUserConfig().clientUserId;
            int count_unread = 0;
            int mentions_unread = 0;
            int count_query = count;
            int offset_query = 0;
            int min_unread_id = 0;
            int last_message_id = 0;
            boolean queryFromServer = false;
            int max_unread_date = 0;
            long messageMaxId = max_id;
            int max_id_query = max_id;
            boolean unreadCountIsLocal = false;
            int max_id_override = max_id;
            int channelId = 0;
            if (isChannel) {
                channelId = -(int) dialog_id;
            }
            if (messageMaxId != 0 && channelId != 0) {
                messageMaxId |= ((long) channelId) << 32;
            }
            boolean isEnd = false;
            int num = dialog_id == 777000 ? 10 : 1;
            try {
                ArrayList<Integer> usersToLoad = new ArrayList<>();
                ArrayList<Integer> chatsToLoad = new ArrayList<>();
                ArrayList<Long> replyMessages = new ArrayList<>();
                SparseArray<ArrayList<TLRPC.Message>> replyMessageOwners = new SparseArray<>();
                LongSparseArray<ArrayList<TLRPC.Message>> replyMessageRandomOwners = new LongSparseArray<>();
                SQLiteCursor cursor;
                if (scheduled) {
                    isEnd = true;
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.ttl FROM scheduled_messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d ORDER BY m.date DESC", dialog_id));
                    while (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.send_state = cursor.intValue(1);
                            message.id = cursor.intValue(2);
                            if (message.id > 0 && message.send_state != 0 && message.send_state != 3) {
                                message.send_state = 0;
                            }
                            if (dialog_id == currentUserId) {
                                message.out = true;
                                message.unread = false;
                            } else {
                                message.unread = true;
                            }
                            message.readAttachPath(data, currentUserId);
                            data.reuse();
                            message.date = cursor.intValue(3);
                            message.dialog_id = dialog_id;
                            if (message.ttl == 0) {
                                message.ttl = cursor.intValue(6);
                            }
                            res.messages.add(message);

                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);

                            if (message.reply_to_msg_id != 0 || message.reply_to_random_id != 0) {
                                if (!cursor.isNull(5)) {
                                    data = cursor.byteBufferValue(5);
                                    if (data != null) {
                                        message.replyMessage = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                        message.replyMessage.readAttachPath(data, currentUserId);
                                        data.reuse();
                                        if (message.replyMessage != null) {
                                            if (MessageObject.isMegagroup(message)) {
                                                message.replyMessage.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                            }
                                            addUsersAndChatsFromMessage(message.replyMessage, usersToLoad, chatsToLoad);
                                        }
                                    }
                                }
                                if (message.replyMessage == null) {
                                    if (message.reply_to_msg_id != 0) {
                                        long messageId = message.reply_to_msg_id;
                                        if (message.to_id.channel_id != 0) {
                                            messageId |= ((long) message.to_id.channel_id) << 32;
                                        }
                                        if (!replyMessages.contains(messageId)) {
                                            replyMessages.add(messageId);
                                        }
                                        ArrayList<TLRPC.Message> messages = replyMessageOwners.get(message.reply_to_msg_id);
                                        if (messages == null) {
                                            messages = new ArrayList<>();
                                            replyMessageOwners.put(message.reply_to_msg_id, messages);
                                        }
                                        messages.add(message);
                                    } else {
                                        if (!replyMessages.contains(message.reply_to_random_id)) {
                                            replyMessages.add(message.reply_to_random_id);
                                        }
                                        ArrayList<TLRPC.Message> messages = replyMessageRandomOwners.get(message.reply_to_random_id);
                                        if (messages == null) {
                                            messages = new ArrayList<>();
                                            replyMessageRandomOwners.put(message.reply_to_random_id, messages);
                                        }
                                        messages.add(message);
                                    }
                                }
                            }
                        }
                    }
                    cursor.dispose();
                } else {
                    int lower_id = (int) dialog_id;
                    if (lower_id != 0) {
                        if (load_type == 3 && minDate == 0) {
                            cursor = database.queryFinalized("SELECT inbox_max, unread_count, date, unread_count_i FROM dialogs WHERE did = " + dialog_id);
                            if (cursor.next()) {
                                min_unread_id = cursor.intValue(0) + 1;
                                count_unread = cursor.intValue(1);
                                max_unread_date = cursor.intValue(2);
                                mentions_unread = cursor.intValue(3);
                            }
                            cursor.dispose();
                        } else if (load_type != 1 && load_type != 3 && load_type != 4 && minDate == 0) {
                            if (load_type == 2) {
                                cursor = database.queryFinalized("SELECT inbox_max, unread_count, date, unread_count_i FROM dialogs WHERE did = " + dialog_id);
                                if (cursor.next()) {
                                    messageMaxId = max_id_query = min_unread_id = cursor.intValue(0);
                                    count_unread = cursor.intValue(1);
                                    max_unread_date = cursor.intValue(2);
                                    mentions_unread = cursor.intValue(3);
                                    queryFromServer = true;
                                    if (messageMaxId != 0 && channelId != 0) {
                                        messageMaxId |= ((long) channelId) << 32;
                                    }
                                }
                                cursor.dispose();
                                if (!queryFromServer) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid), max(date) FROM messages WHERE uid = %d AND out = 0 AND read_state IN(0,2) AND mid > 0", dialog_id));
                                    if (cursor.next()) {
                                        min_unread_id = cursor.intValue(0);
                                        max_unread_date = cursor.intValue(1);
                                    }
                                    cursor.dispose();
                                    if (min_unread_id != 0) {
                                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(*) FROM messages WHERE uid = %d AND mid >= %d AND out = 0 AND read_state IN(0,2)", dialog_id, min_unread_id));
                                        if (cursor.next()) {
                                            count_unread = cursor.intValue(0);
                                        }
                                        cursor.dispose();
                                    }
                                } else if (max_id_query == 0) {
                                    int existingUnreadCount = 0;
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(*) FROM messages WHERE uid = %d AND mid > 0 AND out = 0 AND read_state IN(0,2)", dialog_id));
                                    if (cursor.next()) {
                                        existingUnreadCount = cursor.intValue(0);
                                    }
                                    cursor.dispose();
                                    if (existingUnreadCount == count_unread) {
                                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages WHERE uid = %d AND out = 0 AND read_state IN(0,2) AND mid > 0", dialog_id));
                                        if (cursor.next()) {
                                            messageMaxId = max_id_query = min_unread_id = cursor.intValue(0);
                                            if (messageMaxId != 0 && channelId != 0) {
                                                messageMaxId |= ((long) channelId) << 32;
                                            }
                                        }
                                        cursor.dispose();
                                    }
                                } else {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT start, end FROM messages_holes WHERE uid = %d AND start < %d AND end > %d", dialog_id, max_id_query, max_id_query));
                                    boolean containMessage = !cursor.next();
                                    cursor.dispose();

                                    if (containMessage) {
                                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages WHERE uid = %d AND out = 0 AND read_state IN(0,2) AND mid > %d", dialog_id, max_id_query));
                                        if (cursor.next()) {
                                            messageMaxId = max_id_query = cursor.intValue(0);
                                            if (messageMaxId != 0 && channelId != 0) {
                                                messageMaxId |= ((long) channelId) << 32;
                                            }
                                        }
                                        cursor.dispose();
                                    }
                                }
                            }

                            if (count_query > count_unread || count_unread < num) {
                                count_query = Math.max(count_query, count_unread + 10);
                                if (count_unread < num) {
                                    count_unread = 0;
                                    min_unread_id = 0;
                                    messageMaxId = 0;
                                    last_message_id = 0;
                                    queryFromServer = false;
                                }
                            } else {
                                offset_query = count_unread - count_query;
                                count_query += 10;
                            }
                        }

                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM messages_holes WHERE uid = %d AND start IN (0, 1)", dialog_id));
                        if (cursor.next()) {
                            isEnd = cursor.intValue(0) == 1;
                            cursor.dispose();
                        } else {
                            cursor.dispose();
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages WHERE uid = %d AND mid > 0", dialog_id));
                            if (cursor.next()) {
                                int mid = cursor.intValue(0);
                                if (mid != 0) {
                                    SQLitePreparedStatement state = database.executeFast("REPLACE INTO messages_holes VALUES(?, ?, ?)");
                                    state.requery();
                                    state.bindLong(1, dialog_id);
                                    state.bindInteger(2, 0);
                                    state.bindInteger(3, mid);
                                    state.step();
                                    state.dispose();
                                }
                            }
                            cursor.dispose();
                        }

                        if (load_type == 3 || load_type == 4 || queryFromServer && load_type == 2) {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(mid) FROM messages WHERE uid = %d AND mid > 0", dialog_id));
                            if (cursor.next()) {
                                last_message_id = cursor.intValue(0);
                            }
                            cursor.dispose();

                            if (load_type == 4 && offset_date != 0) {
                                int startMid;
                                int endMid;

                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(mid) FROM messages WHERE uid = %d AND date <= %d AND mid > 0", dialog_id, offset_date));
                                if (cursor.next()) {
                                    startMid = cursor.intValue(0);
                                } else {
                                    startMid = -1;
                                }
                                cursor.dispose();
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages WHERE uid = %d AND date >= %d AND mid > 0", dialog_id, offset_date));
                                if (cursor.next()) {
                                    endMid = cursor.intValue(0);
                                } else {
                                    endMid = -1;
                                }
                                cursor.dispose();
                                if (startMid != -1 && endMid != -1) {
                                    if (startMid == endMid) {
                                        max_id_query = startMid;
                                    } else {
                                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM messages_holes WHERE uid = %d AND start <= %d AND end > %d", dialog_id, startMid, startMid));
                                        if (cursor.next()) {
                                            startMid = -1;
                                        }
                                        cursor.dispose();
                                        if (startMid != -1) {
                                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM messages_holes WHERE uid = %d AND start <= %d AND end > %d", dialog_id, endMid, endMid));
                                            if (cursor.next()) {
                                                endMid = -1;
                                            }
                                            cursor.dispose();
                                            if (endMid != -1) {
                                                max_id_override = endMid;
                                                messageMaxId = max_id_query = endMid;
                                                if (messageMaxId != 0 && channelId != 0) {
                                                    messageMaxId |= ((long) channelId) << 32;
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            boolean containMessage = max_id_query != 0;
                            if (containMessage) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM messages_holes WHERE uid = %d AND start < %d AND end > %d", dialog_id, max_id_query, max_id_query));
                                if (cursor.next()) {
                                    containMessage = false;
                                }
                                cursor.dispose();
                            }

                            if (containMessage) {
                                long holeMessageMaxId = 0;
                                long holeMessageMinId = 1;
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM messages_holes WHERE uid = %d AND start >= %d ORDER BY start ASC LIMIT 1", dialog_id, max_id_query));
                                if (cursor.next()) {
                                    holeMessageMaxId = cursor.intValue(0);
                                    if (channelId != 0) {
                                        holeMessageMaxId |= ((long) channelId) << 32;
                                    }
                                }
                                cursor.dispose();
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT end FROM messages_holes WHERE uid = %d AND end <= %d ORDER BY end DESC LIMIT 1", dialog_id, max_id_query));
                                if (cursor.next()) {
                                    holeMessageMinId = cursor.intValue(0);
                                    if (channelId != 0) {
                                        holeMessageMinId |= ((long) channelId) << 32;
                                    }
                                }
                            /*if (holeMessageMaxId == holeMessageMinId) {
                                holeMessageMaxId = 0;
                                holeMessageMinId = 1;
                            }*/
                                cursor.dispose();
                                if (holeMessageMaxId != 0 || holeMessageMinId != 1) {
                                    if (holeMessageMaxId == 0) {
                                        holeMessageMaxId = 1000000000;
                                        if (channelId != 0) {
                                            holeMessageMaxId |= ((long) channelId) << 32;
                                        }
                                    }
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT * FROM (SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention FROM messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND m.mid <= %d AND (m.mid >= %d OR m.mid < 0) ORDER BY m.date DESC, m.mid DESC LIMIT %d) UNION " +
                                            "SELECT * FROM (SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention FROM messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND m.mid > %d AND (m.mid <= %d OR m.mid < 0) ORDER BY m.date ASC, m.mid ASC LIMIT %d)", dialog_id, messageMaxId, holeMessageMinId, count_query / 2, dialog_id, messageMaxId, holeMessageMaxId, count_query / 2));
                                } else {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT * FROM (SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention FROM messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND m.mid <= %d ORDER BY m.date DESC, m.mid DESC LIMIT %d) UNION " +
                                            "SELECT * FROM (SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention FROM messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND m.mid > %d ORDER BY m.date ASC, m.mid ASC LIMIT %d)", dialog_id, messageMaxId, count_query / 2, dialog_id, messageMaxId, count_query / 2));
                                }
                            } else {
                                if (load_type == 2) {
                                    int existingUnreadCount = 0;
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(*) FROM messages WHERE uid = %d AND mid != 0 AND out = 0 AND read_state IN(0,2)", dialog_id));
                                    if (cursor.next()) {
                                        existingUnreadCount = cursor.intValue(0);
                                    }
                                    cursor.dispose();
                                    if (existingUnreadCount == count_unread) {
                                        unreadCountIsLocal = true;
                                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT * FROM (SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention FROM messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND m.mid <= %d ORDER BY m.date DESC, m.mid DESC LIMIT %d) UNION " +
                                                "SELECT * FROM (SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention FROM messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND m.mid > %d ORDER BY m.date ASC, m.mid ASC LIMIT %d)", dialog_id, messageMaxId, count_query / 2, dialog_id, messageMaxId, count_query / 2));
                                    } else {
                                        cursor = null;
                                    }
                                } else {
                                    cursor = null;
                                }
                            }
                        } else if (load_type == 1) {
                            long holeMessageId = 0;
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT start, end FROM messages_holes WHERE uid = %d AND start >= %d AND start != 1 AND end != 1 ORDER BY start ASC LIMIT 1", dialog_id, max_id));
                            if (cursor.next()) {
                                holeMessageId = cursor.intValue(0);
                                if (channelId != 0) {
                                    holeMessageId |= ((long) channelId) << 32;
                                }
                            }
                            cursor.dispose();
                            if (holeMessageId != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention FROM messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND m.date >= %d AND m.mid > %d AND m.mid <= %d ORDER BY m.date ASC, m.mid ASC LIMIT %d", dialog_id, minDate, messageMaxId, holeMessageId, count_query));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention FROM messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND m.date >= %d AND m.mid > %d ORDER BY m.date ASC, m.mid ASC LIMIT %d", dialog_id, minDate, messageMaxId, count_query));
                            }
                        } else if (minDate != 0) {
                            if (messageMaxId != 0) {
                                long holeMessageId = 0;
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT end FROM messages_holes WHERE uid = %d AND end <= %d ORDER BY end DESC LIMIT 1", dialog_id, max_id));
                                if (cursor.next()) {
                                    holeMessageId = cursor.intValue(0);
                                    if (channelId != 0) {
                                        holeMessageId |= ((long) channelId) << 32;
                                    }
                                }
                                cursor.dispose();
                                if (holeMessageId != 0) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention FROM messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND m.date <= %d AND m.mid < %d AND (m.mid >= %d OR m.mid < 0) ORDER BY m.date DESC, m.mid DESC LIMIT %d", dialog_id, minDate, messageMaxId, holeMessageId, count_query));
                                } else {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention FROM messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND m.date <= %d AND m.mid < %d ORDER BY m.date DESC, m.mid DESC LIMIT %d", dialog_id, minDate, messageMaxId, count_query));
                                }
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention FROM messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND m.date <= %d ORDER BY m.date DESC, m.mid DESC LIMIT %d,%d", dialog_id, minDate, offset_query, count_query));
                            }
                        } else {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(mid) FROM messages WHERE uid = %d AND mid > 0", dialog_id));
                            if (cursor.next()) {
                                last_message_id = cursor.intValue(0);
                            }
                            cursor.dispose();

                            long holeMessageId = 0;
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(end) FROM messages_holes WHERE uid = %d", dialog_id));
                            if (cursor.next()) {
                                holeMessageId = cursor.intValue(0);
                                if (channelId != 0) {
                                    holeMessageId |= ((long) channelId) << 32;
                                }
                            }
                            cursor.dispose();
                            if (holeMessageId != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention FROM messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND (m.mid >= %d OR m.mid < 0) ORDER BY m.date DESC, m.mid DESC LIMIT %d,%d", dialog_id, holeMessageId, offset_query, count_query));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention FROM messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d ORDER BY m.date DESC, m.mid DESC LIMIT %d,%d", dialog_id, offset_query, count_query));
                            }
                        }
                    } else {
                        isEnd = true;

                        if (load_type == 3 && minDate == 0) {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages WHERE uid = %d AND mid < 0", dialog_id));
                            if (cursor.next()) {
                                min_unread_id = cursor.intValue(0);
                            }
                            cursor.dispose();

                            int min_unread_id2 = 0;
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(mid), max(date) FROM messages WHERE uid = %d AND out = 0 AND read_state IN(0,2) AND mid < 0", dialog_id));
                            if (cursor.next()) {
                                min_unread_id2 = cursor.intValue(0);
                                max_unread_date = cursor.intValue(1);
                            }
                            cursor.dispose();
                            if (min_unread_id2 != 0) {
                                min_unread_id = min_unread_id2;
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(*) FROM messages WHERE uid = %d AND mid <= %d AND out = 0 AND read_state IN(0,2)", dialog_id, min_unread_id2));
                                if (cursor.next()) {
                                    count_unread = cursor.intValue(0);
                                }
                                cursor.dispose();
                            }
                        }

                        if (load_type == 3 || load_type == 4) {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages WHERE uid = %d AND mid < 0", dialog_id));
                            if (cursor.next()) {
                                last_message_id = cursor.intValue(0);
                            }
                            cursor.dispose();

                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT * FROM (SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention FROM messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND m.mid <= %d ORDER BY m.mid DESC LIMIT %d) UNION " +
                                    "SELECT * FROM (SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention FROM messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND m.mid > %d ORDER BY m.mid ASC LIMIT %d)", dialog_id, messageMaxId, count_query / 2, dialog_id, messageMaxId, count_query / 2));
                        } else if (load_type == 1) {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention FROM messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND m.mid < %d ORDER BY m.mid DESC LIMIT %d", dialog_id, max_id, count_query));
                        } else if (minDate != 0) {
                            if (max_id != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention FROM messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND m.mid > %d ORDER BY m.mid ASC LIMIT %d", dialog_id, max_id, count_query));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention FROM messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND m.date <= %d ORDER BY m.mid ASC LIMIT %d,%d", dialog_id, minDate, offset_query, count_query));
                            }
                        } else {
                            if (load_type == 2) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages WHERE uid = %d AND mid < 0", dialog_id));
                                if (cursor.next()) {
                                    last_message_id = cursor.intValue(0);
                                }
                                cursor.dispose();

                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(mid), max(date) FROM messages WHERE uid = %d AND out = 0 AND read_state IN(0,2) AND mid < 0", dialog_id));
                                if (cursor.next()) {
                                    min_unread_id = cursor.intValue(0);
                                    max_unread_date = cursor.intValue(1);
                                }
                                cursor.dispose();
                                if (min_unread_id != 0) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(*) FROM messages WHERE uid = %d AND mid <= %d AND out = 0 AND read_state IN(0,2)", dialog_id, min_unread_id));
                                    if (cursor.next()) {
                                        count_unread = cursor.intValue(0);
                                    }
                                    cursor.dispose();
                                }
                            }

                            if (count_query > count_unread || count_unread < num) {
                                count_query = Math.max(count_query, count_unread + 10);
                                if (count_unread < num) {
                                    count_unread = 0;
                                    min_unread_id = 0;
                                    last_message_id = 0;
                                }
                            } else {
                                offset_query = count_unread - count_query;
                                count_query += 10;
                            }
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention FROM messages as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d ORDER BY m.mid ASC LIMIT %d,%d", dialog_id, offset_query, count_query));
                        }
                    }
                    int minId = Integer.MAX_VALUE;
                    int maxId = Integer.MIN_VALUE;
                    if (cursor != null) {
                        while (cursor.next()) {
                            NativeByteBuffer data = cursor.byteBufferValue(1);
                            if (data != null) {
                                TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                message.send_state = cursor.intValue(2);
                                message.id = cursor.intValue(3);
                                if (message.id > 0 && message.send_state != 0 && message.send_state != 3) {
                                    message.send_state = 0;
                                }
                                if (dialog_id == currentUserId) {
                                    message.out = true;
                                }
                                message.readAttachPath(data, currentUserId);
                                data.reuse();
                                MessageObject.setUnreadFlags(message, cursor.intValue(0));
                                if (message.id > 0) {
                                    minId = Math.min(message.id, minId);
                                    maxId = Math.max(message.id, maxId);
                                }
                                message.date = cursor.intValue(4);
                                message.dialog_id = dialog_id;
                                if ((message.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                                    message.views = cursor.intValue(7);
                                }
                                if (lower_id != 0 && message.ttl == 0) {
                                    message.ttl = cursor.intValue(8);
                                }
                                if (cursor.intValue(9) != 0) {
                                    message.mentioned = true;
                                }
                                res.messages.add(message);

                                addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);

                                if (message.reply_to_msg_id != 0 || message.reply_to_random_id != 0) {
                                    if (!cursor.isNull(6)) {
                                        data = cursor.byteBufferValue(6);
                                        if (data != null) {
                                            message.replyMessage = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                            message.replyMessage.readAttachPath(data, currentUserId);
                                            data.reuse();
                                            if (message.replyMessage != null) {
                                                if (MessageObject.isMegagroup(message)) {
                                                    message.replyMessage.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                                }
                                                addUsersAndChatsFromMessage(message.replyMessage, usersToLoad, chatsToLoad);
                                            }
                                        }
                                    }
                                    if (message.replyMessage == null) {
                                        if (message.reply_to_msg_id != 0) {
                                            long messageId = message.reply_to_msg_id;
                                            if (message.to_id.channel_id != 0) {
                                                messageId |= ((long) message.to_id.channel_id) << 32;
                                            }
                                            if (!replyMessages.contains(messageId)) {
                                                replyMessages.add(messageId);
                                            }
                                            ArrayList<TLRPC.Message> messages = replyMessageOwners.get(message.reply_to_msg_id);
                                            if (messages == null) {
                                                messages = new ArrayList<>();
                                                replyMessageOwners.put(message.reply_to_msg_id, messages);
                                            }
                                            messages.add(message);
                                        } else {
                                            if (!replyMessages.contains(message.reply_to_random_id)) {
                                                replyMessages.add(message.reply_to_random_id);
                                            }
                                            ArrayList<TLRPC.Message> messages = replyMessageRandomOwners.get(message.reply_to_random_id);
                                            if (messages == null) {
                                                messages = new ArrayList<>();
                                                replyMessageRandomOwners.put(message.reply_to_random_id, messages);
                                            }
                                            messages.add(message);
                                        }
                                    }
                                }
                                if (lower_id == 0 && !cursor.isNull(5)) {
                                    message.random_id = cursor.longValue(5);
                                }
                                if (MessageObject.isSecretMedia(message)) {
                                    try {
                                        SQLiteCursor cursor2 = database.queryFinalized(String.format(Locale.US, "SELECT date FROM enc_tasks_v2 WHERE mid = %d", message.id));
                                        if (cursor2.next()) {
                                            message.destroyTime = cursor2.intValue(0);
                                        }
                                        cursor2.dispose();
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                }
                            }
                        }
                        cursor.dispose();
                    }

                    Collections.sort(res.messages, (lhs, rhs) -> {
                        if (lhs.id > 0 && rhs.id > 0) {
                            if (lhs.id > rhs.id) {
                                return -1;
                            } else if (lhs.id < rhs.id) {
                                return 1;
                            }
                        } else if (lhs.id < 0 && rhs.id < 0) {
                            if (lhs.id < rhs.id) {
                                return -1;
                            } else if (lhs.id > rhs.id) {
                                return 1;
                            }
                        } else {
                            if (lhs.date > rhs.date) {
                                return -1;
                            } else if (lhs.date < rhs.date) {
                                return 1;
                            }
                        }
                        return 0;
                    });

                    if (lower_id != 0) {
                        if ((load_type == 3 || load_type == 4 || load_type == 2 && queryFromServer && !unreadCountIsLocal) && !res.messages.isEmpty()) {
                            if (!(minId <= max_id_query && maxId >= max_id_query)) {
                                replyMessages.clear();
                                usersToLoad.clear();
                                chatsToLoad.clear();
                                res.messages.clear();
                            }
                        }
                        if ((load_type == 4 || load_type == 3) && res.messages.size() == 1) {
                            res.messages.clear();
                        }
                    }
                    if (mentions_unread != 0) {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM messages WHERE uid = %d AND mention = 1 AND read_state IN(0, 1)", dialog_id));
                        if (cursor.next()) {
                            if (mentions_unread != cursor.intValue(0)) {
                                mentions_unread *= -1;
                            }
                        } else {
                            mentions_unread *= -1;
                        }
                        cursor.dispose();
                    }
                }
                if (!replyMessages.isEmpty()) {
                    if (replyMessageOwners.size() > 0) {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid, date FROM messages WHERE mid IN(%s)", TextUtils.join(",", replyMessages)));
                    } else {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.data, m.mid, m.date, r.random_id FROM randoms as r INNER JOIN messages as m ON r.mid = m.mid WHERE r.random_id IN(%s)", TextUtils.join(",", replyMessages)));
                    }
                    while (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.readAttachPath(data, currentUserId);
                            data.reuse();
                            message.id = cursor.intValue(1);
                            message.date = cursor.intValue(2);
                            message.dialog_id = dialog_id;

                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);

                            if (replyMessageOwners.size() > 0) {
                                ArrayList<TLRPC.Message> arrayList = replyMessageOwners.get(message.id);
                                if (arrayList != null) {
                                    for (int a = 0; a < arrayList.size(); a++) {
                                        TLRPC.Message object = arrayList.get(a);
                                        object.replyMessage = message;
                                        if (MessageObject.isMegagroup(object)) {
                                            object.replyMessage.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                        }
                                    }
                                }
                            } else {
                                long value = cursor.longValue(3);
                                ArrayList<TLRPC.Message> arrayList = replyMessageRandomOwners.get(value);
                                replyMessageRandomOwners.remove(value);
                                if (arrayList != null) {
                                    for (int a = 0; a < arrayList.size(); a++) {
                                        TLRPC.Message object = arrayList.get(a);
                                        object.replyMessage = message;
                                        object.reply_to_msg_id = message.id;
                                        if (MessageObject.isMegagroup(object)) {
                                            object.replyMessage.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    cursor.dispose();
                    if (replyMessageRandomOwners.size() > 0) {
                        for (int b = 0; b < replyMessageRandomOwners.size(); b++) {
                            ArrayList<TLRPC.Message> arrayList = replyMessageRandomOwners.valueAt(b);
                            for (int a = 0; a < arrayList.size(); a++) {
                                arrayList.get(a).reply_to_random_id = 0;
                            }
                        }
                    }
                }
                if (!usersToLoad.isEmpty()) {
                    getUsersInternal(TextUtils.join(",", usersToLoad), res.users);
                }
                if (!chatsToLoad.isEmpty()) {
                    getChatsInternal(TextUtils.join(",", chatsToLoad), res.chats);
                }
            } catch (Exception e) {
                res.messages.clear();
                res.chats.clear();
                res.users.clear();
                FileLog.e(e);
            } finally {
                getMessagesController().processLoadedMessages(res, dialog_id, count_query, max_id_override, offset_date, true, classGuid, min_unread_id, last_message_id, count_unread, max_unread_date, load_type, isChannel, isEnd, scheduled, loadIndex, queryFromServer, mentions_unread);
            }
        });
    }

    public void clearSentMedia() {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM sent_files_v2 WHERE 1").stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public Object[] getSentFile(final String path, final int type) {
        if (path == null || path.toLowerCase().endsWith("attheme")) {
            return null;
        }
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final Object[] result = new Object[2];
        storageQueue.postRunnable(() -> {
            try {
                String id = Utilities.MD5(path);
                if (id != null) {
                    SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, parent FROM sent_files_v2 WHERE uid = '%s' AND type = %d", id, type));
                    if (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            TLObject file = TLRPC.MessageMedia.TLdeserialize(data, data.readInt32(false), false);
                            data.reuse();
                            if (file instanceof TLRPC.TL_messageMediaDocument) {
                                result[0] = ((TLRPC.TL_messageMediaDocument) file).document;
                            } else if (file instanceof TLRPC.TL_messageMediaPhoto) {
                                result[0] = ((TLRPC.TL_messageMediaPhoto) file).photo;
                            }
                            if (result[0] != null) {
                                result[1] = cursor.stringValue(1);
                            }
                        }
                    }
                    cursor.dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result[0] != null ? result : null;
    }

    public void putSentFile(final String path, final TLObject file, final int type, String parent) {
        if (path == null || file == null || parent == null) {
            return;
        }
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                String id = Utilities.MD5(path);
                if (id != null) {
                    TLRPC.MessageMedia messageMedia = null;
                    if (file instanceof TLRPC.Photo) {
                        messageMedia = new TLRPC.TL_messageMediaPhoto();
                        messageMedia.photo = (TLRPC.Photo) file;
                        messageMedia.flags |= 1;
                    } else if (file instanceof TLRPC.Document) {
                        messageMedia = new TLRPC.TL_messageMediaDocument();
                        messageMedia.document = (TLRPC.Document) file;
                        messageMedia.flags |= 1;
                    }
                    if (messageMedia == null) {
                        return;
                    }
                    state = database.executeFast("REPLACE INTO sent_files_v2 VALUES(?, ?, ?, ?)");
                    state.requery();
                    NativeByteBuffer data = new NativeByteBuffer(messageMedia.getObjectSize());
                    messageMedia.serializeToStream(data);
                    state.bindString(1, id);
                    state.bindInteger(2, type);
                    state.bindByteBuffer(3, data);
                    state.bindString(4, parent);
                    state.step();
                    data.reuse();
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void updateEncryptedChatSeq(final TLRPC.EncryptedChat chat, final boolean cleanup) {
        if (chat == null) {
            return;
        }
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                state = database.executeFast("UPDATE enc_chats SET seq_in = ?, seq_out = ?, use_count = ?, in_seq_no = ?, mtproto_seq = ? WHERE uid = ?");
                state.bindInteger(1, chat.seq_in);
                state.bindInteger(2, chat.seq_out);
                state.bindInteger(3, (int) chat.key_use_count_in << 16 | chat.key_use_count_out);
                state.bindInteger(4, chat.in_seq_no);
                state.bindInteger(5, chat.mtproto_seq);
                state.bindInteger(6, chat.id);
                state.step();
                if (cleanup) {
                    long did = ((long) chat.id) << 32;
                    database.executeFast(String.format(Locale.US, "DELETE FROM messages WHERE mid IN (SELECT m.mid FROM messages as m LEFT JOIN messages_seq as s ON m.mid = s.mid WHERE m.uid = %d AND m.date = 0 AND m.mid < 0 AND s.seq_out <= %d)", did, chat.in_seq_no)).stepThis().dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void updateEncryptedChatTTL(final TLRPC.EncryptedChat chat) {
        if (chat == null) {
            return;
        }
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                state = database.executeFast("UPDATE enc_chats SET ttl = ? WHERE uid = ?");
                state.bindInteger(1, chat.ttl);
                state.bindInteger(2, chat.id);
                state.step();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void updateEncryptedChatLayer(final TLRPC.EncryptedChat chat) {
        if (chat == null) {
            return;
        }
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                state = database.executeFast("UPDATE enc_chats SET layer = ? WHERE uid = ?");
                state.bindInteger(1, chat.layer);
                state.bindInteger(2, chat.id);
                state.step();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void updateEncryptedChat(final TLRPC.EncryptedChat chat) {
        if (chat == null) {
            return;
        }
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                if ((chat.key_hash == null || chat.key_hash.length < 16) && chat.auth_key != null) {
                    chat.key_hash = AndroidUtilities.calcAuthKeyHash(chat.auth_key);
                }

                state = database.executeFast("UPDATE enc_chats SET data = ?, g = ?, authkey = ?, ttl = ?, layer = ?, seq_in = ?, seq_out = ?, use_count = ?, exchange_id = ?, key_date = ?, fprint = ?, fauthkey = ?, khash = ?, in_seq_no = ?, admin_id = ?, mtproto_seq = ? WHERE uid = ?");
                NativeByteBuffer data = new NativeByteBuffer(chat.getObjectSize());
                NativeByteBuffer data2 = new NativeByteBuffer(chat.a_or_b != null ? chat.a_or_b.length : 1);
                NativeByteBuffer data3 = new NativeByteBuffer(chat.auth_key != null ? chat.auth_key.length : 1);
                NativeByteBuffer data4 = new NativeByteBuffer(chat.future_auth_key != null ? chat.future_auth_key.length : 1);
                NativeByteBuffer data5 = new NativeByteBuffer(chat.key_hash != null ? chat.key_hash.length : 1);
                chat.serializeToStream(data);
                state.bindByteBuffer(1, data);
                if (chat.a_or_b != null) {
                    data2.writeBytes(chat.a_or_b);
                }
                if (chat.auth_key != null) {
                    data3.writeBytes(chat.auth_key);
                }
                if (chat.future_auth_key != null) {
                    data4.writeBytes(chat.future_auth_key);
                }
                if (chat.key_hash != null) {
                    data5.writeBytes(chat.key_hash);
                }
                state.bindByteBuffer(2, data2);
                state.bindByteBuffer(3, data3);
                state.bindInteger(4, chat.ttl);
                state.bindInteger(5, chat.layer);
                state.bindInteger(6, chat.seq_in);
                state.bindInteger(7, chat.seq_out);
                state.bindInteger(8, (int) chat.key_use_count_in << 16 | chat.key_use_count_out);
                state.bindLong(9, chat.exchange_id);
                state.bindInteger(10, chat.key_create_date);
                state.bindLong(11, chat.future_key_fingerprint);
                state.bindByteBuffer(12, data4);
                state.bindByteBuffer(13, data5);
                state.bindInteger(14, chat.in_seq_no);
                state.bindInteger(15, chat.admin_id);
                state.bindInteger(16, chat.mtproto_seq);
                state.bindInteger(17, chat.id);

                state.step();
                data.reuse();
                data2.reuse();
                data3.reuse();
                data4.reuse();
                data5.reuse();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public boolean isDialogHasMessages(final long did) {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final boolean[] result = new boolean[1];
        storageQueue.postRunnable(() -> {
            try {
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid FROM messages WHERE uid = %d LIMIT 1", did));
                result[0] = cursor.next();
                cursor.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result[0];
    }

    public boolean hasAuthMessage(final int date) {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final boolean[] result = new boolean[1];
        storageQueue.postRunnable(() -> {
            try {
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid FROM messages WHERE uid = 777000 AND date = %d AND mid < 0 LIMIT 1", date));
                result[0] = cursor.next();
                cursor.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result[0];
    }

    public void getEncryptedChat(final int chat_id, final CountDownLatch countDownLatch, final ArrayList<TLObject> result) {
        if (countDownLatch == null || result == null) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                ArrayList<Integer> usersToLoad = new ArrayList<>();
                ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
                getEncryptedChatsInternal("" + chat_id, encryptedChats, usersToLoad);
                if (!encryptedChats.isEmpty() && !usersToLoad.isEmpty()) {
                    ArrayList<TLRPC.User> users = new ArrayList<>();
                    getUsersInternal(TextUtils.join(",", usersToLoad), users);
                    if (!users.isEmpty()) {
                        result.add(encryptedChats.get(0));
                        result.add(users.get(0));
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                countDownLatch.countDown();
            }
        });
    }

    public void putEncryptedChat(final TLRPC.EncryptedChat chat, final TLRPC.User user, final TLRPC.Dialog dialog) {
        if (chat == null) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                if ((chat.key_hash == null || chat.key_hash.length < 16) && chat.auth_key != null) {
                    chat.key_hash = AndroidUtilities.calcAuthKeyHash(chat.auth_key);
                }
                SQLitePreparedStatement state = database.executeFast("REPLACE INTO enc_chats VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                NativeByteBuffer data = new NativeByteBuffer(chat.getObjectSize());
                NativeByteBuffer data2 = new NativeByteBuffer(chat.a_or_b != null ? chat.a_or_b.length : 1);
                NativeByteBuffer data3 = new NativeByteBuffer(chat.auth_key != null ? chat.auth_key.length : 1);
                NativeByteBuffer data4 = new NativeByteBuffer(chat.future_auth_key != null ? chat.future_auth_key.length : 1);
                NativeByteBuffer data5 = new NativeByteBuffer(chat.key_hash != null ? chat.key_hash.length : 1);

                chat.serializeToStream(data);
                state.bindInteger(1, chat.id);
                state.bindInteger(2, user.id);
                state.bindString(3, formatUserSearchName(user));
                state.bindByteBuffer(4, data);
                if (chat.a_or_b != null) {
                    data2.writeBytes(chat.a_or_b);
                }
                if (chat.auth_key != null) {
                    data3.writeBytes(chat.auth_key);
                }
                if (chat.future_auth_key != null) {
                    data4.writeBytes(chat.future_auth_key);
                }
                if (chat.key_hash != null) {
                    data5.writeBytes(chat.key_hash);
                }
                state.bindByteBuffer(5, data2);
                state.bindByteBuffer(6, data3);
                state.bindInteger(7, chat.ttl);
                state.bindInteger(8, chat.layer);
                state.bindInteger(9, chat.seq_in);
                state.bindInteger(10, chat.seq_out);
                state.bindInteger(11, (int) chat.key_use_count_in << 16 | chat.key_use_count_out);
                state.bindLong(12, chat.exchange_id);
                state.bindInteger(13, chat.key_create_date);
                state.bindLong(14, chat.future_key_fingerprint);
                state.bindByteBuffer(15, data4);
                state.bindByteBuffer(16, data5);
                state.bindInteger(17, chat.in_seq_no);
                state.bindInteger(18, chat.admin_id);
                state.bindInteger(19, chat.mtproto_seq);

                state.step();
                state.dispose();
                data.reuse();
                data2.reuse();
                data3.reuse();
                data4.reuse();
                data5.reuse();
                if (dialog != null) {
                    state = database.executeFast("REPLACE INTO dialogs VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                    state.bindLong(1, dialog.id);
                    state.bindInteger(2, dialog.last_message_date);
                    state.bindInteger(3, dialog.unread_count);
                    state.bindInteger(4, dialog.top_message);
                    state.bindInteger(5, dialog.read_inbox_max_id);
                    state.bindInteger(6, dialog.read_outbox_max_id);
                    state.bindInteger(7, 0);
                    state.bindInteger(8, dialog.unread_mentions_count);
                    state.bindInteger(9, dialog.pts);
                    state.bindInteger(10, 0);
                    state.bindInteger(11, dialog.pinnedNum);
                    state.bindInteger(12, dialog.flags);
                    state.bindInteger(13, dialog.folder_id);
                    state.bindNull(14);
                    state.step();
                    state.dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private String formatUserSearchName(TLRPC.User user) {
        StringBuilder str = new StringBuilder();
        if (user.first_name != null && user.first_name.length() > 0) {
            str.append(user.first_name);
        }
        if (user.last_name != null && user.last_name.length() > 0) {
            if (str.length() > 0) {
                str.append(" ");
            }
            str.append(user.last_name);
        }
        str.append(";;;");
        if (user.username != null && user.username.length() > 0) {
            str.append(user.username);
        }
        return str.toString().toLowerCase();
    }

    private void putUsersInternal(ArrayList<TLRPC.User> users) throws Exception {
        if (users == null || users.isEmpty()) {
            return;
        }
        SQLitePreparedStatement state = database.executeFast("REPLACE INTO users VALUES(?, ?, ?, ?)");
        for (int a = 0; a < users.size(); a++) {
            TLRPC.User user = users.get(a);
            if (user.min) {
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM users WHERE uid = %d", user.id));
                if (cursor.next()) {
                    try {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            TLRPC.User oldUser = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                            data.reuse();
                            if (oldUser != null) {
                                if (user.username != null) {
                                    oldUser.username = user.username;
                                    oldUser.flags |= 8;
                                } else {
                                    oldUser.username = null;
                                    oldUser.flags = oldUser.flags & ~8;
                                }
                                if (user.photo != null) {
                                    oldUser.photo = user.photo;
                                    oldUser.flags |= 32;
                                } else {
                                    oldUser.photo = null;
                                    oldUser.flags = oldUser.flags & ~32;
                                }
                                user = oldUser;
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                cursor.dispose();
            }
            state.requery();
            NativeByteBuffer data = new NativeByteBuffer(user.getObjectSize());
            user.serializeToStream(data);
            state.bindInteger(1, user.id);
            state.bindString(2, formatUserSearchName(user));
            if (user.status != null) {
                if (user.status instanceof TLRPC.TL_userStatusRecently) {
                    user.status.expires = -100;
                } else if (user.status instanceof TLRPC.TL_userStatusLastWeek) {
                    user.status.expires = -101;
                } else if (user.status instanceof TLRPC.TL_userStatusLastMonth) {
                    user.status.expires = -102;
                }
                state.bindInteger(3, user.status.expires);
            } else {
                state.bindInteger(3, 0);
            }
            state.bindByteBuffer(4, data);
            state.step();
            data.reuse();
        }
        state.dispose();
    }

    public void updateChatDefaultBannedRights(int chatId, TLRPC.TL_chatBannedRights rights, int version) {
        if (rights == null || chatId == 0) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                TLRPC.Chat chat = null;
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM chats WHERE uid = %d", chatId));
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        chat = TLRPC.Chat.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                    }
                }
                cursor.dispose();
                if (chat == null || chat.default_banned_rights != null && version < chat.version) {
                    return;
                }
                chat.default_banned_rights = rights;
                chat.flags |= 262144;
                chat.version = version;

                SQLitePreparedStatement state = database.executeFast("UPDATE chats SET data = ? WHERE uid = ?");
                NativeByteBuffer data = new NativeByteBuffer(chat.getObjectSize());
                chat.serializeToStream(data);
                state.bindByteBuffer(1, data);
                state.bindInteger(2, chat.id);
                state.step();
                data.reuse();
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void putChatsInternal(ArrayList<TLRPC.Chat> chats) throws Exception {
        if (chats == null || chats.isEmpty()) {
            return;
        }
        SQLitePreparedStatement state = database.executeFast("REPLACE INTO chats VALUES(?, ?, ?)");
        for (int a = 0; a < chats.size(); a++) {
            TLRPC.Chat chat = chats.get(a);
            if (chat.min) {
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM chats WHERE uid = %d", chat.id));
                if (cursor.next()) {
                    try {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            TLRPC.Chat oldChat = TLRPC.Chat.TLdeserialize(data, data.readInt32(false), false);
                            data.reuse();
                            if (oldChat != null) {
                                oldChat.title = chat.title;
                                oldChat.photo = chat.photo;
                                oldChat.broadcast = chat.broadcast;
                                oldChat.verified = chat.verified;
                                oldChat.megagroup = chat.megagroup;
                                if (chat.default_banned_rights != null) {
                                    oldChat.default_banned_rights = chat.default_banned_rights;
                                    oldChat.flags |= 262144;
                                }
                                if (chat.admin_rights != null) {
                                    oldChat.admin_rights = chat.admin_rights;
                                    oldChat.flags |= 16384;
                                }
                                if (chat.banned_rights != null) {
                                    oldChat.banned_rights = chat.banned_rights;
                                    oldChat.flags |= 32768;
                                }
                                if (chat.username != null) {
                                    oldChat.username = chat.username;
                                    oldChat.flags |= 64;
                                } else {
                                    oldChat.username = null;
                                    oldChat.flags = oldChat.flags & ~64;
                                }
                                chat = oldChat;
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                cursor.dispose();
            }
            state.requery();
            NativeByteBuffer data = new NativeByteBuffer(chat.getObjectSize());
            chat.serializeToStream(data);
            state.bindInteger(1, chat.id);
            if (chat.title != null) {
                String name = chat.title.toLowerCase();
                state.bindString(2, name);
            } else {
                state.bindString(2, "");
            }
            state.bindByteBuffer(3, data);
            state.step();
            data.reuse();
        }
        state.dispose();
    }

    public void getUsersInternal(String usersToLoad, ArrayList<TLRPC.User> result) throws Exception {
        if (usersToLoad == null || usersToLoad.length() == 0 || result == null) {
            return;
        }
        SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, status FROM users WHERE uid IN(%s)", usersToLoad));
        while (cursor.next()) {
            try {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null) {
                    TLRPC.User user = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();
                    if (user != null) {
                        if (user.status != null) {
                            user.status.expires = cursor.intValue(1);
                        }
                        result.add(user);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        cursor.dispose();
    }

    public void getChatsInternal(String chatsToLoad, ArrayList<TLRPC.Chat> result) throws Exception {
        if (chatsToLoad == null || chatsToLoad.length() == 0 || result == null) {
            return;
        }
        SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM chats WHERE uid IN(%s)", chatsToLoad));
        while (cursor.next()) {
            try {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null) {
                    TLRPC.Chat chat = TLRPC.Chat.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();
                    if (chat != null) {
                        result.add(chat);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        cursor.dispose();
    }

    public void getEncryptedChatsInternal(String chatsToLoad, ArrayList<TLRPC.EncryptedChat> result, ArrayList<Integer> usersToLoad) throws Exception {
        if (chatsToLoad == null || chatsToLoad.length() == 0 || result == null) {
            return;
        }
        SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, user, g, authkey, ttl, layer, seq_in, seq_out, use_count, exchange_id, key_date, fprint, fauthkey, khash, in_seq_no, admin_id, mtproto_seq FROM enc_chats WHERE uid IN(%s)", chatsToLoad));
        while (cursor.next()) {
            try {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null) {
                    TLRPC.EncryptedChat chat = TLRPC.EncryptedChat.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();
                    if (chat != null) {
                        chat.user_id = cursor.intValue(1);
                        if (usersToLoad != null && !usersToLoad.contains(chat.user_id)) {
                            usersToLoad.add(chat.user_id);
                        }
                        chat.a_or_b = cursor.byteArrayValue(2);
                        chat.auth_key = cursor.byteArrayValue(3);
                        chat.ttl = cursor.intValue(4);
                        chat.layer = cursor.intValue(5);
                        chat.seq_in = cursor.intValue(6);
                        chat.seq_out = cursor.intValue(7);
                        int use_count = cursor.intValue(8);
                        chat.key_use_count_in = (short) (use_count >> 16);
                        chat.key_use_count_out = (short) (use_count);
                        chat.exchange_id = cursor.longValue(9);
                        chat.key_create_date = cursor.intValue(10);
                        chat.future_key_fingerprint = cursor.longValue(11);
                        chat.future_auth_key = cursor.byteArrayValue(12);
                        chat.key_hash = cursor.byteArrayValue(13);
                        chat.in_seq_no = cursor.intValue(14);
                        int admin_id = cursor.intValue(15);
                        if (admin_id != 0) {
                            chat.admin_id = admin_id;
                        }
                        chat.mtproto_seq = cursor.intValue(16);
                        result.add(chat);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        cursor.dispose();
    }

    private void putUsersAndChatsInternal(final ArrayList<TLRPC.User> users, final ArrayList<TLRPC.Chat> chats, final boolean withTransaction) {
        try {
            if (withTransaction) {
                database.beginTransaction();
            }
            putUsersInternal(users);
            putChatsInternal(chats);
            if (withTransaction) {
                database.commitTransaction();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void putUsersAndChats(final ArrayList<TLRPC.User> users, final ArrayList<TLRPC.Chat> chats, final boolean withTransaction, boolean useQueue) {
        if (users != null && users.isEmpty() && chats != null && chats.isEmpty()) {
            return;
        }
        if (useQueue) {
            storageQueue.postRunnable(() -> putUsersAndChatsInternal(users, chats, withTransaction));
        } else {
            putUsersAndChatsInternal(users, chats, withTransaction);
        }
    }

    public void removeFromDownloadQueue(final long id, final int type, final boolean move) {
        storageQueue.postRunnable(() -> {
            try {
                if (move) {
                    int minDate = -1;
                    SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(date) FROM download_queue WHERE type = %d", type));
                    if (cursor.next()) {
                        minDate = cursor.intValue(0);
                    }
                    cursor.dispose();
                    if (minDate != -1) {
                        database.executeFast(String.format(Locale.US, "UPDATE download_queue SET date = %d WHERE uid = %d AND type = %d", minDate - 1, id, type)).stepThis().dispose();
                    }
                } else {
                    database.executeFast(String.format(Locale.US, "DELETE FROM download_queue WHERE uid = %d AND type = %d", id, type)).stepThis().dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void clearDownloadQueue(final int type) {
        storageQueue.postRunnable(() -> {
            try {
                if (type == 0) {
                    database.executeFast("DELETE FROM download_queue WHERE 1").stepThis().dispose();
                } else {
                    database.executeFast(String.format(Locale.US, "DELETE FROM download_queue WHERE type = %d", type)).stepThis().dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void getDownloadQueue(final int type) {
        storageQueue.postRunnable(() -> {
            try {
                final ArrayList<DownloadObject> objects = new ArrayList<>();
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, type, data, parent FROM download_queue WHERE type = %d ORDER BY date DESC LIMIT 3", type));
                while (cursor.next()) {
                    DownloadObject downloadObject = new DownloadObject();
                    downloadObject.type = cursor.intValue(1);
                    downloadObject.id = cursor.longValue(0);
                    downloadObject.parent = cursor.stringValue(3);
                    NativeByteBuffer data = cursor.byteBufferValue(2);
                    if (data != null) {
                        TLRPC.MessageMedia messageMedia = TLRPC.MessageMedia.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                        if (messageMedia.document != null) {
                            downloadObject.object = messageMedia.document;
                        } else if (messageMedia.photo != null) {
                            downloadObject.object = messageMedia.photo;
                        }
                        downloadObject.secret = messageMedia.ttl_seconds > 0 && messageMedia.ttl_seconds <= 60;
                        downloadObject.forceCache = (messageMedia.flags & 0x80000000) != 0;
                    }
                    objects.add(downloadObject);
                }
                cursor.dispose();

                AndroidUtilities.runOnUIThread(() -> getDownloadController().processDownloadObjects(type, objects));
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private int getMessageMediaType(TLRPC.Message message) {
        if (message instanceof TLRPC.TL_message_secret) {
            if ((message.media instanceof TLRPC.TL_messageMediaPhoto || MessageObject.isGifMessage(message)) && message.ttl > 0 && message.ttl <= 60 ||
                            MessageObject.isVoiceMessage(message) ||
                            MessageObject.isVideoMessage(message) ||
                            MessageObject.isRoundVideoMessage(message)) {
                return 1;
            } else if (message.media instanceof TLRPC.TL_messageMediaPhoto || MessageObject.isVideoMessage(message)) {
                return 0;
            }
        } else if (message instanceof TLRPC.TL_message && (message.media instanceof TLRPC.TL_messageMediaPhoto || message.media instanceof TLRPC.TL_messageMediaDocument) && message.media.ttl_seconds != 0) {
            return 1;
        } else if (message.media instanceof TLRPC.TL_messageMediaPhoto || MessageObject.isVideoMessage(message)) {
            return 0;
        }
        return -1;
    }

    public void putWebPages(final LongSparseArray<TLRPC.WebPage> webPages) {
        if (isEmpty(webPages)) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                final ArrayList<TLRPC.Message> messages = new ArrayList<>();
                for (int a = 0; a < webPages.size(); a++) {
                    SQLiteCursor cursor = database.queryFinalized("SELECT mid FROM webpage_pending WHERE id = " + webPages.keyAt(a));
                    ArrayList<Long> mids = new ArrayList<>();
                    while (cursor.next()) {
                        mids.add(cursor.longValue(0));
                    }
                    cursor.dispose();

                    if (mids.isEmpty()) {
                        continue;
                    }
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid, data FROM messages WHERE mid IN (%s)", TextUtils.join(",", mids)));
                    while (cursor.next()) {
                        int mid = cursor.intValue(0);
                        NativeByteBuffer data = cursor.byteBufferValue(1);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.readAttachPath(data, getUserConfig().clientUserId);
                            data.reuse();
                            if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
                                message.id = mid;
                                message.media.webpage = webPages.valueAt(a);
                                messages.add(message);
                            }
                        }
                    }
                    cursor.dispose();
                }

                //database.executeFast(String.format(Locale.US, "DELETE FROM webpage_pending WHERE id IN (%s)", ids)).stepThis().dispose();

                if (messages.isEmpty()) {
                    return;
                }

                database.beginTransaction();

                SQLitePreparedStatement state = database.executeFast("UPDATE messages SET data = ? WHERE mid = ?");
                SQLitePreparedStatement state2 = database.executeFast("UPDATE media_v2 SET data = ? WHERE mid = ?");
                for (int a = 0; a < messages.size(); a++) {
                    TLRPC.Message message = messages.get(a);
                    NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                    message.serializeToStream(data);

                    long messageId = message.id;
                    if (message.to_id.channel_id != 0) {
                        messageId |= ((long) message.to_id.channel_id) << 32;
                    }

                    state.requery();
                    state.bindByteBuffer(1, data);
                    state.bindLong(2, messageId);
                    state.step();

                    state2.requery();
                    state2.bindByteBuffer(1, data);
                    state2.bindLong(2, messageId);
                    state2.step();

                    data.reuse();
                }
                state.dispose();
                state2.dispose();

                database.commitTransaction();

                AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.didReceivedWebpages, messages));
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void overwriteChannel(final int channel_id, final TLRPC.TL_updates_channelDifferenceTooLong difference, final int newDialogType) {
        storageQueue.postRunnable(() -> {
            try {
                boolean checkInvite = false;
                final long did = -channel_id;
                int pinned = 0;

                SQLiteCursor cursor = database.queryFinalized("SELECT pinned FROM dialogs WHERE did = " + did);
                if (!cursor.next()) {
                    if (newDialogType != 0) {
                        checkInvite = true;
                    }
                } else {
                    pinned = cursor.intValue(0);
                }
                cursor.dispose();


                database.executeFast("DELETE FROM messages WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM bot_keyboard WHERE uid = " + did).stepThis().dispose();
                database.executeFast("UPDATE media_counts_v2 SET old = 1 WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM media_v2 WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM messages_holes WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM media_holes_v2 WHERE uid = " + did).stepThis().dispose();
                getMediaDataController().clearBotKeyboard(did, null);

                TLRPC.TL_messages_dialogs dialogs = new TLRPC.TL_messages_dialogs();
                dialogs.chats.addAll(difference.chats);
                dialogs.users.addAll(difference.users);
                dialogs.messages.addAll(difference.messages);
                TLRPC.Dialog dialog = difference.dialog;
                dialog.id = did;
                dialog.flags = 1;
                dialog.notify_settings = null;
                dialog.pinned = pinned != 0;
                dialog.pinnedNum = pinned;
                dialogs.dialogs.add(dialog);
                putDialogsInternal(dialogs, 0);

                updateDialogsWithDeletedMessages(new ArrayList<>(), null, false, channel_id);
                AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.removeAllMessagesFromDialog, did, true));
                if (checkInvite) {
                    if (newDialogType == 1) {
                        getMessagesController().checkChannelInviter(channel_id);
                    } else {
                        getMessagesController().generateJoinMessage(channel_id, false);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void putChannelViews(final SparseArray<SparseIntArray> channelViews, final boolean isChannel) {
        if (isEmpty(channelViews)) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                database.beginTransaction();
                SQLitePreparedStatement state = database.executeFast("UPDATE messages SET media = max((SELECT media FROM messages WHERE mid = ?), ?) WHERE mid = ?");
                for (int a = 0; a < channelViews.size(); a++) {
                    int peer = channelViews.keyAt(a);
                    SparseIntArray messages = channelViews.get(peer);
                    for (int b = 0; b < messages.size(); b++) {
                        int views = messages.get(messages.keyAt(b));
                        long messageId = messages.keyAt(b);
                        if (isChannel) {
                            messageId |= ((long) -peer) << 32;
                        }
                        state.requery();
                        state.bindLong(1, messageId);
                        state.bindInteger(2, views);
                        state.bindLong(3, messageId);
                        state.step();
                    }
                }
                state.dispose();
                database.commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private boolean isValidKeyboardToSave(TLRPC.Message message) {
        return message.reply_markup != null && !(message.reply_markup instanceof TLRPC.TL_replyInlineMarkup) && (!message.reply_markup.selective || message.mentioned);
    }

    private void putMessagesInternal(final ArrayList<TLRPC.Message> messages, final boolean withTransaction, final boolean doNotUpdateDialogDate, final int downloadMask, boolean ifNoLastMessage, boolean scheduled) {
        try {
            if (scheduled) {
                if (withTransaction) {
                    database.beginTransaction();
                }

                SQLitePreparedStatement state_messages = database.executeFast("REPLACE INTO scheduled_messages VALUES(?, ?, ?, ?, ?, ?, NULL)");
                SQLitePreparedStatement state_randoms = database.executeFast("REPLACE INTO randoms VALUES(?, ?)");
                ArrayList<Long> dialogsToUpdate = new ArrayList<>();

                for (int a = 0; a < messages.size(); a++) {
                    TLRPC.Message message = messages.get(a);
                    fixUnsupportedMedia(message);

                    state_messages.requery();
                    long messageId = message.id;
                    if (message.local_id != 0) {
                        messageId = message.local_id;
                    }
                    if (message.to_id.channel_id != 0) {
                        messageId |= ((long) message.to_id.channel_id) << 32;
                    }

                    NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                    message.serializeToStream(data);

                    long did = MessageObject.getDialogId(message);
                    state_messages.bindLong(1, messageId);
                    state_messages.bindLong(2, did);
                    state_messages.bindInteger(3, message.send_state);
                    state_messages.bindInteger(4, message.date);
                    state_messages.bindByteBuffer(5, data);
                    state_messages.bindInteger(6, message.ttl);
                    state_messages.step();

                    if (message.random_id != 0) {
                        state_randoms.requery();
                        state_randoms.bindLong(1, message.random_id);
                        state_randoms.bindLong(2, messageId);
                        state_randoms.step();
                    }

                    data.reuse();

                    if (!dialogsToUpdate.contains(did)) {
                        dialogsToUpdate.add(did);
                    }
                }
                state_messages.dispose();
                state_randoms.dispose();

                if (withTransaction) {
                    database.commitTransaction();
                }
                for (int a = 0, N = dialogsToUpdate.size(); a < N; a++) {
                    broadcastScheduledMessagesChange(dialogsToUpdate.get(a));
                }
            } else {
                if (ifNoLastMessage) {
                    TLRPC.Message lastMessage = messages.get(0);
                    if (lastMessage.dialog_id == 0) {
                        MessageObject.getDialogId(lastMessage);
                    }
                    int lastMid = -1;
                    SQLiteCursor cursor = database.queryFinalized("SELECT last_mid FROM dialogs WHERE did = " + lastMessage.dialog_id);
                    if (cursor.next()) {
                        lastMid = cursor.intValue(0);
                    }
                    cursor.dispose();
                    if (lastMid != 0) {
                        return;
                    }
                }
                if (withTransaction) {
                    database.beginTransaction();
                }
                LongSparseArray<TLRPC.Message> messagesMap = new LongSparseArray<>();
                LongSparseArray<Integer> messagesCounts = new LongSparseArray<>();
                LongSparseArray<Integer> mentionCounts = new LongSparseArray<>();
                SparseArray<LongSparseArray<Integer>> mediaCounts = null;
                LongSparseArray<TLRPC.Message> botKeyboards = new LongSparseArray<>();

                LongSparseArray<Long> messagesMediaIdsMap = null;
                LongSparseArray<Integer> mediaTypesChange = null;
                StringBuilder messageMediaIds = null;
                LongSparseArray<Integer> mediaTypes = null;
                StringBuilder messageIds = new StringBuilder();
                LongSparseArray<Integer> dialogsReadMax = new LongSparseArray<>();
                LongSparseArray<Long> messagesIdsMap = new LongSparseArray<>();
                LongSparseArray<Long> mentionsIdsMap = new LongSparseArray<>();

                SQLitePreparedStatement state_messages = database.executeFast("REPLACE INTO messages VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)");
                SQLitePreparedStatement state_media = null;
                SQLitePreparedStatement state_randoms = database.executeFast("REPLACE INTO randoms VALUES(?, ?)");
                SQLitePreparedStatement state_download = database.executeFast("REPLACE INTO download_queue VALUES(?, ?, ?, ?, ?)");
                SQLitePreparedStatement state_webpage = database.executeFast("REPLACE INTO webpage_pending VALUES(?, ?)");
                SQLitePreparedStatement state_polls = null;

                for (int a = 0; a < messages.size(); a++) {
                    TLRPC.Message message = messages.get(a);

                    long messageId = message.id;
                    if (message.dialog_id == 0) {
                        MessageObject.getDialogId(message);
                    }
                    if (message.to_id.channel_id != 0) {
                        messageId |= ((long) message.to_id.channel_id) << 32;
                    }
                    if (message.mentioned && message.media_unread) {
                        mentionsIdsMap.put(messageId, message.dialog_id);
                    }

                    if (!(message.action instanceof TLRPC.TL_messageActionHistoryClear) && (!MessageObject.isOut(message) || message.from_scheduled) && (message.id > 0 || MessageObject.isUnread(message))) {
                        Integer currentMaxId = dialogsReadMax.get(message.dialog_id);
                        if (currentMaxId == null) {
                            SQLiteCursor cursor = database.queryFinalized("SELECT inbox_max FROM dialogs WHERE did = " + message.dialog_id);
                            if (cursor.next()) {
                                currentMaxId = cursor.intValue(0);
                            } else {
                                currentMaxId = 0;
                            }
                            cursor.dispose();
                            dialogsReadMax.put(message.dialog_id, currentMaxId);
                        }
                        if (message.id < 0 || currentMaxId < message.id) {
                            if (messageIds.length() > 0) {
                                messageIds.append(",");
                            }
                            messageIds.append(messageId);
                            messagesIdsMap.put(messageId, message.dialog_id);
                        }
                    }
                    if (MediaDataController.canAddMessageToMedia(message)) {
                        if (messageMediaIds == null) {
                            messageMediaIds = new StringBuilder();
                            messagesMediaIdsMap = new LongSparseArray<>();
                            mediaTypes = new LongSparseArray<>();
                        }
                        if (messageMediaIds.length() > 0) {
                            messageMediaIds.append(",");
                        }
                        messageMediaIds.append(messageId);
                        messagesMediaIdsMap.put(messageId, message.dialog_id);
                        mediaTypes.put(messageId, MediaDataController.getMediaType(message));
                    }
                    if (isValidKeyboardToSave(message)) {
                        TLRPC.Message oldMessage = botKeyboards.get(message.dialog_id);
                        if (oldMessage == null || oldMessage.id < message.id) {
                            botKeyboards.put(message.dialog_id, message);
                        }
                    }
                }

                for (int a = 0; a < botKeyboards.size(); a++) {
                    getMediaDataController().putBotKeyboard(botKeyboards.keyAt(a), botKeyboards.valueAt(a));
                }

                if (messageMediaIds != null) {
                    SQLiteCursor cursor = database.queryFinalized("SELECT mid, type FROM media_v2 WHERE mid IN(" + messageMediaIds.toString() + ")");
                    while (cursor.next()) {
                        long mid = cursor.longValue(0);
                        int type = cursor.intValue(1);
                        if (type == mediaTypes.get(mid)) {
                            messagesMediaIdsMap.remove(mid);
                        } else {
                            if (mediaTypesChange == null) {
                                mediaTypesChange = new LongSparseArray<>();
                            }
                            mediaTypesChange.put(mid, type);
                        }
                    }
                    cursor.dispose();
                    mediaCounts = new SparseArray<>();
                    for (int a = 0; a < messagesMediaIdsMap.size(); a++) {
                        long key = messagesMediaIdsMap.keyAt(a);
                        long value = messagesMediaIdsMap.valueAt(a);
                        Integer type = mediaTypes.get(key);
                        LongSparseArray<Integer> counts = mediaCounts.get(type);
                        Integer count;
                        if (counts == null) {
                            counts = new LongSparseArray<>();
                            count = 0;
                            mediaCounts.put(type, counts);
                        } else {
                            count = counts.get(value);
                        }
                        if (count == null) {
                            count = 0;
                        }
                        count++;
                        counts.put(value, count);
                        if (mediaTypesChange != null) {
                            int previousType = mediaTypesChange.get(key, -1);
                            if (previousType >= 0) {
                                counts = mediaCounts.get(previousType);
                                if (counts == null) {
                                    counts = new LongSparseArray<>();
                                    count = 0;
                                    mediaCounts.put(previousType, counts);
                                } else {
                                    count = counts.get(value);
                                }
                                if (count == null) {
                                    count = 0;
                                }
                                count--;
                                counts.put(value, count);
                            }
                        }
                    }
                }

                if (messageIds.length() > 0) {
                    SQLiteCursor cursor = database.queryFinalized("SELECT mid FROM messages WHERE mid IN(" + messageIds.toString() + ")");
                    while (cursor.next()) {
                        long mid = cursor.longValue(0);
                        messagesIdsMap.remove(mid);
                        mentionsIdsMap.remove(mid);
                    }
                    cursor.dispose();
                    for (int a = 0; a < messagesIdsMap.size(); a++) {
                        long dialog_id = messagesIdsMap.valueAt(a);
                        Integer count = messagesCounts.get(dialog_id);
                        if (count == null) {
                            count = 0;
                        }
                        count++;
                        messagesCounts.put(dialog_id, count);
                    }
                    for (int a = 0; a < mentionsIdsMap.size(); a++) {
                        long dialog_id = mentionsIdsMap.valueAt(a);
                        Integer count = mentionCounts.get(dialog_id);
                        if (count == null) {
                            count = 0;
                        }
                        count++;
                        mentionCounts.put(dialog_id, count);
                    }
                }

                int downloadMediaMask = 0;
                for (int a = 0; a < messages.size(); a++) {
                    TLRPC.Message message = messages.get(a);
                    fixUnsupportedMedia(message);

                    state_messages.requery();
                    long messageId = message.id;
                    if (message.local_id != 0) {
                        messageId = message.local_id;
                    }
                    if (message.to_id.channel_id != 0) {
                        messageId |= ((long) message.to_id.channel_id) << 32;
                    }

                    NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                    message.serializeToStream(data);

                    boolean updateDialog = true;
                    if (message.action instanceof TLRPC.TL_messageEncryptedAction && !(message.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL || message.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages)) {
                        updateDialog = false;
                    }

                    if (updateDialog) {
                        TLRPC.Message lastMessage = messagesMap.get(message.dialog_id);
                        if (lastMessage == null || message.date > lastMessage.date || lastMessage.id > 0 && message.id > lastMessage.id || lastMessage.id < 0 && message.id < lastMessage.id) {
                            messagesMap.put(message.dialog_id, message);
                        }
                    }

                    state_messages.bindLong(1, messageId);
                    state_messages.bindLong(2, message.dialog_id);
                    state_messages.bindInteger(3, MessageObject.getUnreadFlags(message));
                    state_messages.bindInteger(4, message.send_state);
                    state_messages.bindInteger(5, message.date);
                    state_messages.bindByteBuffer(6, data);
                    state_messages.bindInteger(7, (MessageObject.isOut(message) || message.from_scheduled ? 1 : 0));
                    state_messages.bindInteger(8, message.ttl);
                    if ((message.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                        state_messages.bindInteger(9, message.views);
                    } else {
                        state_messages.bindInteger(9, getMessageMediaType(message));
                    }
                    state_messages.bindInteger(10, 0);
                    state_messages.bindInteger(11, message.mentioned ? 1 : 0);
                    state_messages.step();

                    if (message.random_id != 0) {
                        state_randoms.requery();
                        state_randoms.bindLong(1, message.random_id);
                        state_randoms.bindLong(2, messageId);
                        state_randoms.step();
                    }

                    if (MediaDataController.canAddMessageToMedia(message)) {
                        if (state_media == null) {
                            state_media = database.executeFast("REPLACE INTO media_v2 VALUES(?, ?, ?, ?, ?)");
                        }
                        state_media.requery();
                        state_media.bindLong(1, messageId);
                        state_media.bindLong(2, message.dialog_id);
                        state_media.bindInteger(3, message.date);
                        state_media.bindInteger(4, MediaDataController.getMediaType(message));
                        state_media.bindByteBuffer(5, data);
                        state_media.step();
                    }

                    if (message.media instanceof TLRPC.TL_messageMediaPoll) {
                        if (state_polls == null) {
                            state_polls = database.executeFast("REPLACE INTO polls VALUES(?, ?)");
                        }
                        TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) message.media;
                        state_polls.requery();
                        state_polls.bindLong(1, messageId);
                        state_polls.bindLong(2, mediaPoll.poll.id);
                        state_polls.step();
                    } else if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
                        state_webpage.requery();
                        state_webpage.bindLong(1, message.media.webpage.id);
                        state_webpage.bindLong(2, messageId);
                        state_webpage.step();
                    }

                    data.reuse();

                    if (downloadMask != 0 && (message.to_id.channel_id == 0 || message.post) && message.date >= getConnectionsManager().getCurrentTime() - 60 * 60 && getDownloadController().canDownloadMedia(message) == 1) {
                        if (message.media instanceof TLRPC.TL_messageMediaPhoto || message.media instanceof TLRPC.TL_messageMediaDocument || message.media instanceof TLRPC.TL_messageMediaWebPage) {
                            int type = 0;
                            long id = 0;
                            TLRPC.MessageMedia object = null;
                            TLRPC.Document document = MessageObject.getDocument(message);
                            TLRPC.Photo photo = MessageObject.getPhoto(message);
                            if (MessageObject.isVoiceMessage(message)) {
                                id = document.id;
                                type = DownloadController.AUTODOWNLOAD_TYPE_AUDIO;
                                object = new TLRPC.TL_messageMediaDocument();
                                object.document = document;
                                object.flags |= 1;
                            } else if (MessageObject.isStickerMessage(message) || MessageObject.isAnimatedStickerMessage(message)) {
                                id = document.id;
                                type = DownloadController.AUTODOWNLOAD_TYPE_PHOTO;
                                object = new TLRPC.TL_messageMediaDocument();
                                object.document = document;
                                object.flags |= 1;
                            } else if (MessageObject.isVideoMessage(message) || MessageObject.isRoundVideoMessage(message) || MessageObject.isGifMessage(message)) {
                                id = document.id;
                                type = DownloadController.AUTODOWNLOAD_TYPE_VIDEO;
                                object = new TLRPC.TL_messageMediaDocument();
                                object.document = document;
                                object.flags |= 1;
                            } else if (document != null) {
                                id = document.id;
                                type = DownloadController.AUTODOWNLOAD_TYPE_DOCUMENT;
                                object = new TLRPC.TL_messageMediaDocument();
                                object.document = document;
                                object.flags |= 1;
                            } else if (photo != null) {
                                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
                                if (photoSize != null) {
                                    id = photo.id;
                                    type = DownloadController.AUTODOWNLOAD_TYPE_PHOTO;
                                    object = new TLRPC.TL_messageMediaPhoto();
                                    object.photo = photo;
                                    object.flags |= 1;
                                    if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
                                        object.flags |= 0x80000000;
                                    }
                                }
                            }
                            if (object != null) {
                                if (message.media.ttl_seconds != 0) {
                                    object.ttl_seconds = message.media.ttl_seconds;
                                    object.flags |= 4;
                                }
                                downloadMediaMask |= type;
                                state_download.requery();
                                data = new NativeByteBuffer(object.getObjectSize());
                                object.serializeToStream(data);
                                state_download.bindLong(1, id);
                                state_download.bindInteger(2, type);
                                state_download.bindInteger(3, message.date);
                                state_download.bindByteBuffer(4, data);
                                state_download.bindString(5, "sent_" + (message.to_id != null ? message.to_id.channel_id : 0) + "_" + message.id);
                                state_download.step();
                                data.reuse();
                            }
                        }
                    }
                }
                state_messages.dispose();
                if (state_media != null) {
                    state_media.dispose();
                }
                if (state_polls != null) {
                    state_polls.dispose();
                }
                state_randoms.dispose();
                state_download.dispose();
                state_webpage.dispose();

                SQLitePreparedStatement state_dialogs_replace = database.executeFast("REPLACE INTO dialogs VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                SQLitePreparedStatement state_dialogs_update = database.executeFast("UPDATE dialogs SET date = ?, unread_count = ?, last_mid = ?, unread_count_i = ? WHERE did = ?");

                for (int a = 0; a < messagesMap.size(); a++) {
                    long key = messagesMap.keyAt(a);
                    if (key == 0) {
                        continue;
                    }
                    TLRPC.Message message = messagesMap.valueAt(a);

                    int channelId = 0;
                    if (message != null) {
                        channelId = message.to_id.channel_id;
                    }

                    SQLiteCursor cursor = database.queryFinalized("SELECT date, unread_count, last_mid, unread_count_i FROM dialogs WHERE did = " + key);
                    int dialog_date = 0;
                    int last_mid = 0;
                    int old_unread_count = 0;
                    int old_mentions_count = 0;
                    boolean exists;
                    if (exists = cursor.next()) {
                        dialog_date = cursor.intValue(0);
                        old_unread_count = Math.max(0, cursor.intValue(1));
                        last_mid = cursor.intValue(2);
                        old_mentions_count = Math.max(0, cursor.intValue(3));
                    } else if (channelId != 0) {
                        getMessagesController().checkChannelInviter(channelId);
                    }
                    cursor.dispose();

                    Integer mentions_count = mentionCounts.get(key);
                    Integer unread_count = messagesCounts.get(key);
                    if (unread_count == null) {
                        unread_count = 0;
                    } else {
                        messagesCounts.put(key, unread_count + old_unread_count);
                    }
                    if (mentions_count == null) {
                        mentions_count = 0;
                    } else {
                        mentionCounts.put(key, mentions_count + old_mentions_count);
                    }
                    long messageId = message != null ? message.id : last_mid;
                    if (message != null) {
                        if (message.local_id != 0) {
                            messageId = message.local_id;
                        }
                    }

                    if (channelId != 0) {
                        messageId |= ((long) channelId) << 32;
                    }

                    if (exists) {
                        state_dialogs_update.requery();
                        state_dialogs_update.bindInteger(1, message != null && (!doNotUpdateDialogDate || dialog_date == 0) ? message.date : dialog_date);
                        state_dialogs_update.bindInteger(2, old_unread_count + unread_count);
                        state_dialogs_update.bindLong(3, messageId);
                        state_dialogs_update.bindInteger(4, old_mentions_count + mentions_count);
                        state_dialogs_update.bindLong(5, key);
                        state_dialogs_update.step();
                    } else {
                        state_dialogs_replace.requery();
                        state_dialogs_replace.bindLong(1, key);
                        state_dialogs_replace.bindInteger(2, message != null && (!doNotUpdateDialogDate || dialog_date == 0) ? message.date : dialog_date);
                        state_dialogs_replace.bindInteger(3, old_unread_count + unread_count);
                        state_dialogs_replace.bindLong(4, messageId);
                        state_dialogs_replace.bindInteger(5, 0);
                        state_dialogs_replace.bindInteger(6, 0);
                        state_dialogs_replace.bindLong(7, 0);
                        state_dialogs_replace.bindInteger(8, old_mentions_count + mentions_count);
                        state_dialogs_replace.bindInteger(9, channelId != 0 ? 1 : 0);
                        state_dialogs_replace.bindInteger(10, 0);
                        state_dialogs_replace.bindInteger(11, 0);
                        state_dialogs_replace.bindInteger(12, 0);
                        state_dialogs_replace.bindInteger(13, 0);
                        state_dialogs_replace.bindNull(14);
                        state_dialogs_replace.step();
                    }
                }
                state_dialogs_update.dispose();
                state_dialogs_replace.dispose();

                if (mediaCounts != null) {
                    state_randoms = database.executeFast("REPLACE INTO media_counts_v2 VALUES(?, ?, ?, ?)");
                    for (int a = 0; a < mediaCounts.size(); a++) {
                        int type = mediaCounts.keyAt(a);
                        LongSparseArray<Integer> value = mediaCounts.valueAt(a);
                        for (int b = 0; b < value.size(); b++) {
                            long uid = value.keyAt(b);
                            int lower_part = (int) uid;
                            int count = -1;
                            int old = 0;
                            SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT count, old FROM media_counts_v2 WHERE uid = %d AND type = %d LIMIT 1", uid, type));
                            if (cursor.next()) {
                                count = cursor.intValue(0);
                                old = cursor.intValue(1);
                            }
                            cursor.dispose();
                            if (count != -1) {
                                state_randoms.requery();
                                count += value.valueAt(b);
                                state_randoms.bindLong(1, uid);
                                state_randoms.bindInteger(2, type);
                                state_randoms.bindInteger(3, Math.max(0, count));
                                state_randoms.bindInteger(4, old);
                                state_randoms.step();
                            }
                        }
                    }
                    state_randoms.dispose();
                }
                if (withTransaction) {
                    database.commitTransaction();
                }
                getMessagesController().processDialogsUpdateRead(messagesCounts, mentionCounts);

                if (downloadMediaMask != 0) {
                    final int downloadMediaMaskFinal = downloadMediaMask;
                    AndroidUtilities.runOnUIThread(() -> getDownloadController().newDownloadObjectsAvailable(downloadMediaMaskFinal));
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void putMessages(final ArrayList<TLRPC.Message> messages, final boolean withTransaction, boolean useQueue, final boolean doNotUpdateDialogDate, final int downloadMask, boolean scheduled) {
        putMessages(messages, withTransaction, useQueue, doNotUpdateDialogDate, downloadMask, false, scheduled);
    }

    public void putMessages(final ArrayList<TLRPC.Message> messages, final boolean withTransaction, boolean useQueue, final boolean doNotUpdateDialogDate, final int downloadMask, final boolean ifNoLastMessage, boolean scheduled) {
        if (messages.size() == 0) {
            return;
        }
        if (useQueue) {
            storageQueue.postRunnable(() -> putMessagesInternal(messages, withTransaction, doNotUpdateDialogDate, downloadMask, ifNoLastMessage, scheduled));
        } else {
            putMessagesInternal(messages, withTransaction, doNotUpdateDialogDate, downloadMask, ifNoLastMessage, scheduled);
        }
    }

    public void markMessageAsSendError(final TLRPC.Message message, boolean scheduled) {
        storageQueue.postRunnable(() -> {
            try {
                long messageId = message.id;
                if (message.to_id.channel_id != 0) {
                    messageId |= ((long) message.to_id.channel_id) << 32;
                }
                if (scheduled) {
                    database.executeFast("UPDATE scheduled_messages SET send_state = 2 WHERE mid = " + messageId).stepThis().dispose();
                } else {
                    database.executeFast("UPDATE messages SET send_state = 2 WHERE mid = " + messageId).stepThis().dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void setMessageSeq(final int mid, final int seq_in, final int seq_out) {
        storageQueue.postRunnable(() -> {
            try {
                SQLitePreparedStatement state = database.executeFast("REPLACE INTO messages_seq VALUES(?, ?, ?)");
                state.requery();
                state.bindInteger(1, mid);
                state.bindInteger(2, seq_in);
                state.bindInteger(3, seq_out);
                state.step();
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private long[] updateMessageStateAndIdInternal(long random_id, Integer _oldId, int newId, int date, int channelId, int scheduled) {
        SQLiteCursor cursor = null;
        long oldMessageId;
        long newMessageId = newId;

        if (_oldId == null) {
            try {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid FROM randoms WHERE random_id = %d LIMIT 1", random_id));
                if (cursor.next()) {
                    _oldId = cursor.intValue(0);
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            if (_oldId == null) {
                return null;
            }
        }
        oldMessageId = _oldId;
        if (channelId != 0) {
            oldMessageId |= ((long) channelId) << 32;
            newMessageId |= ((long) channelId) << 32;
        }

        long did = 0;
        if (scheduled == -1 || scheduled == 0) {
            try {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid FROM messages WHERE mid = %d LIMIT 1", oldMessageId));
                if (cursor.next()) {
                    did = cursor.longValue(0);
                    scheduled = 0;
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        }

        if (scheduled == -1 || scheduled == 1) {
            try {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid FROM scheduled_messages WHERE mid = %d LIMIT 1", oldMessageId));
                if (cursor.next()) {
                    did = cursor.longValue(0);
                    scheduled = 1;
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        }

        if (did == 0) {
            return null;
        }
        if (oldMessageId == newMessageId && date != 0) {
            SQLitePreparedStatement state = null;
            try {
                if (scheduled == 0) {
                    state = database.executeFast("UPDATE messages SET send_state = 0, date = ? WHERE mid = ?");
                } else {
                    state = database.executeFast("UPDATE scheduled_messages SET send_state = 0, date = ? WHERE mid = ?");
                }
                state.bindInteger(1, date);
                state.bindLong(2, newMessageId);
                state.step();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }

            return new long[]{did, newId};
        } else {
            SQLitePreparedStatement state = null;

            if (scheduled == 0) {
                try {
                    state = database.executeFast("UPDATE messages SET mid = ?, send_state = 0 WHERE mid = ?");
                    state.bindLong(1, newMessageId);
                    state.bindLong(2, oldMessageId);
                    state.step();
                } catch (Exception e) {
                    try {
                        database.executeFast(String.format(Locale.US, "DELETE FROM messages WHERE mid = %d", oldMessageId)).stepThis().dispose();
                        database.executeFast(String.format(Locale.US, "DELETE FROM messages_seq WHERE mid = %d", oldMessageId)).stepThis().dispose();
                    } catch (Exception e2) {
                        FileLog.e(e2);
                    }
                } finally {
                    if (state != null) {
                        state.dispose();
                        state = null;
                    }
                }

                try {
                    state = database.executeFast("UPDATE media_v2 SET mid = ? WHERE mid = ?");
                    state.bindLong(1, newMessageId);
                    state.bindLong(2, oldMessageId);
                    state.step();
                } catch (Exception e) {
                    try {
                        database.executeFast(String.format(Locale.US, "DELETE FROM media_v2 WHERE mid = %d", oldMessageId)).stepThis().dispose();
                    } catch (Exception e2) {
                        FileLog.e(e2);
                    }
                } finally {
                    if (state != null) {
                        state.dispose();
                        state = null;
                    }
                }

                try {
                    state = database.executeFast("UPDATE dialogs SET last_mid = ? WHERE last_mid = ?");
                    state.bindLong(1, newMessageId);
                    state.bindLong(2, oldMessageId);
                    state.step();
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (state != null) {
                        state.dispose();
                    }
                }
            } else if (scheduled == 1) {
                try {
                    state = database.executeFast("UPDATE scheduled_messages SET mid = ?, send_state = 0 WHERE mid = ?");
                    state.bindLong(1, newMessageId);
                    state.bindLong(2, oldMessageId);
                    state.step();
                } catch (Exception e) {
                    try {
                        database.executeFast(String.format(Locale.US, "DELETE FROM scheduled_messages WHERE mid = %d", oldMessageId)).stepThis().dispose();
                    } catch (Exception e2) {
                        FileLog.e(e2);
                    }
                } finally {
                    if (state != null) {
                        state.dispose();
                    }
                }
            }

            return new long[]{did, _oldId};
        }
    }

    public long[] updateMessageStateAndId(final long random_id, final Integer _oldId, final int newId, final int date, boolean useQueue, final int channelId, int scheduled) {
        if (useQueue) {
            storageQueue.postRunnable(() -> updateMessageStateAndIdInternal(random_id, _oldId, newId, date, channelId, scheduled));
        } else {
            return updateMessageStateAndIdInternal(random_id, _oldId, newId, date, channelId, scheduled);
        }
        return null;
    }

    private void updateUsersInternal(final ArrayList<TLRPC.User> users, final boolean onlyStatus, final boolean withTransaction) {
        try {
            if (onlyStatus) {
                if (withTransaction) {
                    database.beginTransaction();
                }
                SQLitePreparedStatement state = database.executeFast("UPDATE users SET status = ? WHERE uid = ?");
                for (int a = 0, N = users.size(); a < N; a++) {
                    TLRPC.User user = users.get(a);
                    state.requery();
                    if (user.status != null) {
                        state.bindInteger(1, user.status.expires);
                    } else {
                        state.bindInteger(1, 0);
                    }
                    state.bindInteger(2, user.id);
                    state.step();
                }
                state.dispose();
                if (withTransaction) {
                    database.commitTransaction();
                }
            } else {
                StringBuilder ids = new StringBuilder();
                SparseArray<TLRPC.User> usersDict = new SparseArray<>();
                for (int a = 0, N = users.size(); a < N; a++) {
                    TLRPC.User user = users.get(a);
                    if (ids.length() != 0) {
                        ids.append(",");
                    }
                    ids.append(user.id);
                    usersDict.put(user.id, user);
                }
                ArrayList<TLRPC.User> loadedUsers = new ArrayList<>();
                getUsersInternal(ids.toString(), loadedUsers);
                for (int a = 0, N = loadedUsers.size(); a < N; a++) {
                    TLRPC.User user = loadedUsers.get(a);
                    TLRPC.User updateUser = usersDict.get(user.id);
                    if (updateUser != null) {
                        if (updateUser.first_name != null && updateUser.last_name != null) {
                            if (!UserObject.isContact(user)) {
                                user.first_name = updateUser.first_name;
                                user.last_name = updateUser.last_name;
                            }
                            user.username = updateUser.username;
                        } else if (updateUser.photo != null) {
                            user.photo = updateUser.photo;
                        } else if (updateUser.phone != null) {
                            user.phone = updateUser.phone;
                        }
                    }
                }

                if (!loadedUsers.isEmpty()) {
                    if (withTransaction) {
                        database.beginTransaction();
                    }
                    putUsersInternal(loadedUsers);
                    if (withTransaction) {
                        database.commitTransaction();
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void updateUsers(final ArrayList<TLRPC.User> users, final boolean onlyStatus, final boolean withTransaction, boolean useQueue) {
        if (users == null || users.isEmpty()) {
            return;
        }
        if (useQueue) {
            storageQueue.postRunnable(() -> updateUsersInternal(users, onlyStatus, withTransaction));
        } else {
            updateUsersInternal(users, onlyStatus, withTransaction);
        }
    }

    private void markMessagesAsReadInternal(SparseLongArray inbox, SparseLongArray outbox, SparseIntArray encryptedMessages) {
        try {
            if (!isEmpty(inbox)) {
                SQLitePreparedStatement state = database.executeFast("DELETE FROM unread_push_messages WHERE uid = ? AND mid <= ?");
                for (int b = 0; b < inbox.size(); b++) {
                    int key = inbox.keyAt(b);
                    long messageId = inbox.get(key);
                    database.executeFast(String.format(Locale.US, "UPDATE messages SET read_state = read_state | 1 WHERE uid = %d AND mid > 0 AND mid <= %d AND read_state IN(0,2) AND out = 0", key, messageId)).stepThis().dispose();

                    state.requery();
                    state.bindLong(1, key);
                    state.bindLong(2, messageId);
                    state.step();
                }
                state.dispose();
            }
            if (!isEmpty(outbox)) {
                for (int b = 0; b < outbox.size(); b++) {
                    int key = outbox.keyAt(b);
                    long messageId = outbox.get(key);
                    database.executeFast(String.format(Locale.US, "UPDATE messages SET read_state = read_state | 1 WHERE uid = %d AND mid > 0 AND mid <= %d AND read_state IN(0,2) AND out = 1", key, messageId)).stepThis().dispose();
                }
            }
            if (encryptedMessages != null && !isEmpty(encryptedMessages)) {
                for (int a = 0; a < encryptedMessages.size(); a++) {
                    long dialog_id = ((long) encryptedMessages.keyAt(a)) << 32;
                    int max_date = encryptedMessages.valueAt(a);
                    SQLitePreparedStatement state = database.executeFast("UPDATE messages SET read_state = read_state | 1 WHERE uid = ? AND date <= ? AND read_state IN(0,2) AND out = 1");
                    state.requery();
                    state.bindLong(1, dialog_id);
                    state.bindInteger(2, max_date);
                    state.step();
                    state.dispose();
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void markMessagesContentAsRead(final ArrayList<Long> mids, final int date) {
        if (isEmpty(mids)) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                String midsStr = TextUtils.join(",", mids);
                database.executeFast(String.format(Locale.US, "UPDATE messages SET read_state = read_state | 2 WHERE mid IN (%s)", midsStr)).stepThis().dispose();
                if (date != 0) {
                    SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid, ttl FROM messages WHERE mid IN (%s) AND ttl > 0", midsStr));
                    ArrayList<Integer> arrayList = null;
                    while (cursor.next()) {
                        if (arrayList == null) {
                            arrayList = new ArrayList<>();
                        }
                        arrayList.add(cursor.intValue(0));
                    }
                    if (arrayList != null) {
                        emptyMessagesMedia(arrayList);
                    }
                    cursor.dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void markMessagesAsRead(final SparseLongArray inbox, final SparseLongArray outbox, final SparseIntArray encryptedMessages, boolean useQueue) {
        if (useQueue) {
            storageQueue.postRunnable(() -> markMessagesAsReadInternal(inbox, outbox, encryptedMessages));
        } else {
            markMessagesAsReadInternal(inbox, outbox, encryptedMessages);
        }
    }

    public void markMessagesAsDeletedByRandoms(final ArrayList<Long> messages) {
        if (messages.isEmpty()) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                String ids = TextUtils.join(",", messages);
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid FROM randoms WHERE random_id IN(%s)", ids));
                final ArrayList<Integer> mids = new ArrayList<>();
                while (cursor.next()) {
                    mids.add(cursor.intValue(0));
                }
                cursor.dispose();
                if (!mids.isEmpty()) {
                    AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.messagesDeleted, mids, 0, false));
                    updateDialogsWithReadMessagesInternal(mids, null, null, null);
                    markMessagesAsDeletedInternal(mids, 0, true, false);
                    updateDialogsWithDeletedMessagesInternal(mids, null, 0);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    protected void deletePushMessages(long dialogId, final ArrayList<Integer> messages) {
        try {
            database.executeFast(String.format(Locale.US, "DELETE FROM unread_push_messages WHERE uid = %d AND mid IN(%s)", dialogId, TextUtils.join(",", messages))).stepThis().dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void broadcastScheduledMessagesChange(Long did) {
        try {
            SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM scheduled_messages WHERE uid = %d", did));
            int count;
            if (cursor.next()) {
                count = cursor.intValue(0);
            } else {
                count = 0;
            }
            cursor.dispose();
            AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.scheduledMessagesUpdated, did, count));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private ArrayList<Long> markMessagesAsDeletedInternal(final ArrayList<Integer> messages, int channelId, boolean deleteFiles, boolean scheduled) {
        try {
            ArrayList<Long> dialogsIds = new ArrayList<>();
            if (scheduled) {
                String ids;
                if (channelId != 0) {
                    StringBuilder builder = new StringBuilder(messages.size());
                    for (int a = 0; a < messages.size(); a++) {
                        long messageId = messages.get(a);
                        messageId |= ((long) channelId) << 32;
                        if (builder.length() > 0) {
                            builder.append(',');
                        }
                        builder.append(messageId);
                    }
                    ids = builder.toString();
                } else {
                    ids = TextUtils.join(",", messages);
                }

                ArrayList<Long> dialogsToUpdate = new ArrayList<>();

                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid FROM scheduled_messages WHERE mid IN(%s)", ids));
                try {
                    while (cursor.next()) {
                        long did = cursor.longValue(0);
                        if (!dialogsToUpdate.contains(did)) {
                            dialogsToUpdate.add(did);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                cursor.dispose();

                database.executeFast(String.format(Locale.US, "DELETE FROM scheduled_messages WHERE mid IN(%s)", ids)).stepThis().dispose();
                for (int a = 0, N = dialogsToUpdate.size(); a < N; a++) {
                    broadcastScheduledMessagesChange(dialogsToUpdate.get(a));
                }
            } else {
                String ids;
                final ArrayList<Integer> temp = new ArrayList<>(messages);
                LongSparseArray<Integer[]> dialogsToUpdate = new LongSparseArray<>();
                if (channelId != 0) {
                    StringBuilder builder = new StringBuilder(messages.size());
                    for (int a = 0; a < messages.size(); a++) {
                        long messageId = messages.get(a);
                        messageId |= ((long) channelId) << 32;
                        if (builder.length() > 0) {
                            builder.append(',');
                        }
                        builder.append(messageId);
                    }
                    ids = builder.toString();
                } else {
                    ids = TextUtils.join(",", messages);
                }
                ArrayList<File> filesToDelete = new ArrayList<>();
                int currentUser = getUserConfig().getClientUserId();
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, data, read_state, out, mention, mid FROM messages WHERE mid IN(%s)", ids));

                try {
                    while (cursor.next()) {
                        long did = cursor.longValue(0);
                        int mid = cursor.intValue(5);
                        temp.remove((Integer) mid);
                        if (did == currentUser) {
                            continue;
                        }
                        int read_state = cursor.intValue(2);
                        if (cursor.intValue(3) == 0) {
                            Integer[] unread_count = dialogsToUpdate.get(did);
                            if (unread_count == null) {
                                unread_count = new Integer[]{0, 0};
                                dialogsToUpdate.put(did, unread_count);
                            }
                            if (read_state < 2) {
                                unread_count[1]++;
                            }
                            if (read_state == 0 || read_state == 2) {
                                unread_count[0]++;
                            }
                        }
                        if ((int) did != 0 && !deleteFiles) {
                            continue;
                        }
                        NativeByteBuffer data = cursor.byteBufferValue(1);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.readAttachPath(data, getUserConfig().clientUserId);
                            data.reuse();
                            addFilesToDelete(message, filesToDelete, false);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                cursor.dispose();

                getFileLoader().deleteFiles(filesToDelete, 0);

                for (int a = 0; a < dialogsToUpdate.size(); a++) {
                    long did = dialogsToUpdate.keyAt(a);
                    Integer[] counts = dialogsToUpdate.valueAt(a);

                    cursor = database.queryFinalized("SELECT unread_count, unread_count_i FROM dialogs WHERE did = " + did);
                    int old_unread_count = 0;
                    int old_mentions_count = 0;
                    if (cursor.next()) {
                        old_unread_count = cursor.intValue(0);
                        old_mentions_count = cursor.intValue(1);
                    }
                    cursor.dispose();

                    dialogsIds.add(did);
                    SQLitePreparedStatement state = database.executeFast("UPDATE dialogs SET unread_count = ?, unread_count_i = ? WHERE did = ?");
                    state.requery();
                    state.bindInteger(1, Math.max(0, old_unread_count - counts[0]));
                    state.bindInteger(2, Math.max(0, old_mentions_count - counts[1]));
                    state.bindLong(3, did);
                    state.step();
                    state.dispose();
                }

                database.executeFast(String.format(Locale.US, "DELETE FROM messages WHERE mid IN(%s)", ids)).stepThis().dispose();
                database.executeFast(String.format(Locale.US, "DELETE FROM polls WHERE mid IN(%s)", ids)).stepThis().dispose();
                database.executeFast(String.format(Locale.US, "DELETE FROM bot_keyboard WHERE mid IN(%s)", ids)).stepThis().dispose();
                database.executeFast(String.format(Locale.US, "DELETE FROM messages_seq WHERE mid IN(%s)", ids)).stepThis().dispose();
                if (temp.isEmpty()) {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, type FROM media_v2 WHERE mid IN(%s)", ids));
                    SparseArray<LongSparseArray<Integer>> mediaCounts = null;
                    while (cursor.next()) {
                        long uid = cursor.longValue(0);
                        int type = cursor.intValue(1);
                        if (mediaCounts == null) {
                            mediaCounts = new SparseArray<>();
                        }
                        LongSparseArray<Integer> counts = mediaCounts.get(type);
                        Integer count;
                        if (counts == null) {
                            counts = new LongSparseArray<>();
                            count = 0;
                            mediaCounts.put(type, counts);
                        } else {
                            count = counts.get(uid);
                        }
                        if (count == null) {
                            count = 0;
                        }
                        count++;
                        counts.put(uid, count);
                    }
                    cursor.dispose();
                    if (mediaCounts != null) {
                        SQLitePreparedStatement state = database.executeFast("REPLACE INTO media_counts_v2 VALUES(?, ?, ?, ?)");
                        for (int a = 0; a < mediaCounts.size(); a++) {
                            int type = mediaCounts.keyAt(a);
                            LongSparseArray<Integer> value = mediaCounts.valueAt(a);
                            for (int b = 0; b < value.size(); b++) {
                                long uid = value.keyAt(b);
                                int lower_part = (int) uid;
                                int count = -1;
                                int old = 0;
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT count, old FROM media_counts_v2 WHERE uid = %d AND type = %d LIMIT 1", uid, type));
                                if (cursor.next()) {
                                    count = cursor.intValue(0);
                                    old = cursor.intValue(1);
                                }
                                cursor.dispose();
                                if (count != -1) {
                                    state.requery();
                                    count = Math.max(0, count - value.valueAt(b));
                                    state.bindLong(1, uid);
                                    state.bindInteger(2, type);
                                    state.bindInteger(3, count);
                                    state.bindInteger(4, old);
                                    state.step();
                                }
                            }
                        }
                        state.dispose();
                    }
                } else {
                    if (channelId == 0) {
                        database.executeFast("UPDATE media_counts_v2 SET old = 1 WHERE 1").stepThis().dispose();
                    } else {
                        database.executeFast(String.format(Locale.US, "UPDATE media_counts_v2 SET old = 1 WHERE uid = %d", -channelId)).stepThis().dispose();
                    }
                }
                database.executeFast(String.format(Locale.US, "DELETE FROM media_v2 WHERE mid IN(%s)", ids)).stepThis().dispose();
                getMediaDataController().clearBotKeyboard(0, messages);
            }
            return dialogsIds;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private void updateDialogsWithDeletedMessagesInternal(final ArrayList<Integer> messages, ArrayList<Long> additionalDialogsToUpdate, int channelId) {
        try {
            String ids;
            ArrayList<Long> dialogsToUpdate = new ArrayList<>();
            if (!messages.isEmpty()) {
                SQLitePreparedStatement state;
                if (channelId != 0) {
                    dialogsToUpdate.add((long) -channelId);
                    state = database.executeFast("UPDATE dialogs SET last_mid = (SELECT mid FROM messages WHERE uid = ? AND date = (SELECT MAX(date) FROM messages WHERE uid = ?)) WHERE did = ?");
                } else {
                    ids = TextUtils.join(",", messages);
                    SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT did FROM dialogs WHERE last_mid IN(%s)", ids));
                    while (cursor.next()) {
                        dialogsToUpdate.add(cursor.longValue(0));
                    }
                    cursor.dispose();
                    state = database.executeFast("UPDATE dialogs SET last_mid = (SELECT mid FROM messages WHERE uid = ? AND date = (SELECT MAX(date) FROM messages WHERE uid = ? AND date != 0)) WHERE did = ?");
                }
                database.beginTransaction();
                for (int a = 0; a < dialogsToUpdate.size(); a++) {
                    long did = dialogsToUpdate.get(a);
                    state.requery();
                    state.bindLong(1, did);
                    state.bindLong(2, did);
                    state.bindLong(3, did);
                    state.step();
                }
                state.dispose();
                database.commitTransaction();
            } else {
                dialogsToUpdate.add((long) -channelId);
            }
            if (additionalDialogsToUpdate != null) {
                for (int a = 0; a < additionalDialogsToUpdate.size(); a++) {
                    Long did = additionalDialogsToUpdate.get(a);
                    if (!dialogsToUpdate.contains(did)) {
                        dialogsToUpdate.add(did);
                    }
                }
            }
            ids = TextUtils.join(",", dialogsToUpdate);

            TLRPC.messages_Dialogs dialogs = new TLRPC.TL_messages_dialogs();
            ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
            ArrayList<Integer> usersToLoad = new ArrayList<>();
            ArrayList<Integer> chatsToLoad = new ArrayList<>();
            ArrayList<Integer> encryptedToLoad = new ArrayList<>();
            SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT d.did, d.last_mid, d.unread_count, d.date, m.data, m.read_state, m.mid, m.send_state, m.date, d.pts, d.inbox_max, d.outbox_max, d.pinned, d.unread_count_i, d.flags, d.folder_id, d.data FROM dialogs as d LEFT JOIN messages as m ON d.last_mid = m.mid WHERE d.did IN(%s)", ids));
            while (cursor.next()) {
                long dialogId = cursor.longValue(0);
                TLRPC.Dialog dialog;
                if (DialogObject.isFolderDialogId(dialogId)) {
                    TLRPC.TL_dialogFolder dialogFolder = new TLRPC.TL_dialogFolder();
                    if (!cursor.isNull(16)) {
                        NativeByteBuffer data = cursor.byteBufferValue(16);
                        if (data != null) {
                            dialogFolder.folder = TLRPC.TL_folder.TLdeserialize(data, data.readInt32(false), false);
                        } else {
                            dialogFolder.folder = new TLRPC.TL_folder();
                            dialogFolder.folder.id = cursor.intValue(15);
                        }
                        data.reuse();
                    }
                    dialog = dialogFolder;
                } else {
                    dialog = new TLRPC.TL_dialog();
                }
                dialog.id = dialogId;
                dialog.top_message = cursor.intValue(1);
                dialog.read_inbox_max_id = cursor.intValue(10);
                dialog.read_outbox_max_id = cursor.intValue(11);
                dialog.unread_count = cursor.intValue(2);
                dialog.unread_mentions_count = cursor.intValue(13);
                dialog.last_message_date = cursor.intValue(3);
                dialog.pts = cursor.intValue(9);
                dialog.flags = channelId == 0 ? 0 : 1;
                dialog.pinnedNum = cursor.intValue(12);
                dialog.pinned = dialog.pinnedNum != 0;
                int dialog_flags = cursor.intValue(14);
                dialog.unread_mark = (dialog_flags & 1) != 0;
                dialog.folder_id = cursor.intValue(15);

                dialogs.dialogs.add(dialog);

                NativeByteBuffer data = cursor.byteBufferValue(4);
                if (data != null) {
                    TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    message.readAttachPath(data, getUserConfig().clientUserId);
                    data.reuse();
                    MessageObject.setUnreadFlags(message, cursor.intValue(5));
                    message.id = cursor.intValue(6);
                    message.send_state = cursor.intValue(7);
                    int date = cursor.intValue(8);
                    if (date != 0) {
                        dialog.last_message_date = date;
                    }
                    message.dialog_id = dialog.id;
                    dialogs.messages.add(message);

                    addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);
                }

                int lower_id = (int) dialog.id;
                int high_id = (int) (dialog.id >> 32);
                if (lower_id != 0) {
                    if (lower_id > 0) {
                        if (!usersToLoad.contains(lower_id)) {
                            usersToLoad.add(lower_id);
                        }
                    } else {
                        if (!chatsToLoad.contains(-lower_id)) {
                            chatsToLoad.add(-lower_id);
                        }
                    }
                } else {
                    if (!encryptedToLoad.contains(high_id)) {
                        encryptedToLoad.add(high_id);
                    }
                }
            }
            cursor.dispose();

            if (!encryptedToLoad.isEmpty()) {
                getEncryptedChatsInternal(TextUtils.join(",", encryptedToLoad), encryptedChats, usersToLoad);
            }

            if (!chatsToLoad.isEmpty()) {
                getChatsInternal(TextUtils.join(",", chatsToLoad), dialogs.chats);
            }

            if (!usersToLoad.isEmpty()) {
                getUsersInternal(TextUtils.join(",", usersToLoad), dialogs.users);
            }

            if (!dialogs.dialogs.isEmpty() || !encryptedChats.isEmpty()) {
                getMessagesController().processDialogsUpdate(dialogs, encryptedChats);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void updateDialogsWithDeletedMessages(final ArrayList<Integer> messages, final ArrayList<Long> additionalDialogsToUpdate, boolean useQueue, final int channelId) {
        if (messages.isEmpty() && channelId == 0) {
            return;
        }
        if (useQueue) {
            storageQueue.postRunnable(() -> updateDialogsWithDeletedMessagesInternal(messages, additionalDialogsToUpdate, channelId));
        } else {
            updateDialogsWithDeletedMessagesInternal(messages, additionalDialogsToUpdate, channelId);
        }
    }

    public ArrayList<Long> markMessagesAsDeleted(final ArrayList<Integer> messages, boolean useQueue, final int channelId, boolean deleteFiles, boolean scheduled) {
        if (messages.isEmpty()) {
            return null;
        }
        if (useQueue) {
            storageQueue.postRunnable(() -> markMessagesAsDeletedInternal(messages, channelId, deleteFiles, scheduled));
        } else {
            return markMessagesAsDeletedInternal(messages, channelId, deleteFiles, scheduled);
        }
        return null;
    }

    private ArrayList<Long> markMessagesAsDeletedInternal(final int channelId, final int mid, boolean deleteFiles) {
        try {
            String ids;
            ArrayList<Long> dialogsIds = new ArrayList<>();
            LongSparseArray<Integer[]> dialogsToUpdate = new LongSparseArray<>();
            long maxMessageId = mid;
            maxMessageId |= ((long) channelId) << 32;

            ArrayList<File> filesToDelete = new ArrayList<>();
            int currentUser = getUserConfig().getClientUserId();

            SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, data, read_state, out, mention FROM messages WHERE uid = %d AND mid <= %d", -channelId, maxMessageId));

            try {
                while (cursor.next()) {
                    long did = cursor.longValue(0);
                    if (did == currentUser) {
                        continue;
                    }
                    int read_state = cursor.intValue(2);
                    if (cursor.intValue(3) == 0) {
                        Integer[] unread_count = dialogsToUpdate.get(did);
                        if (unread_count == null) {
                            unread_count = new Integer[]{0, 0};
                            dialogsToUpdate.put(did, unread_count);
                        }
                        if (read_state < 2) {
                            unread_count[1]++;
                        }
                        if (read_state == 0 || read_state == 2) {
                            unread_count[0]++;
                        }
                    }
                    if ((int) did != 0 && !deleteFiles) {
                        continue;
                    }
                    NativeByteBuffer data = cursor.byteBufferValue(1);
                    if (data != null) {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        message.readAttachPath(data, getUserConfig().clientUserId);
                        data.reuse();
                        addFilesToDelete(message, filesToDelete, false);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            cursor.dispose();

            getFileLoader().deleteFiles(filesToDelete, 0);

            for (int a = 0; a < dialogsToUpdate.size(); a++) {
                long did = dialogsToUpdate.keyAt(a);
                Integer[] counts = dialogsToUpdate.valueAt(a);

                cursor = database.queryFinalized("SELECT unread_count, unread_count_i FROM dialogs WHERE did = " + did);
                int old_unread_count = 0;
                int old_mentions_count = 0;
                if (cursor.next()) {
                    old_unread_count = cursor.intValue(0);
                    old_mentions_count = cursor.intValue(1);
                }
                cursor.dispose();

                dialogsIds.add(did);
                SQLitePreparedStatement state = database.executeFast("UPDATE dialogs SET unread_count = ?, unread_count_i = ? WHERE did = ?");
                state.requery();
                state.bindInteger(1, Math.max(0, old_unread_count - counts[0]));
                state.bindInteger(2, Math.max(0, old_mentions_count - counts[1]));
                state.bindLong(3, did);
                state.step();
                state.dispose();
            }

            database.executeFast(String.format(Locale.US, "DELETE FROM messages WHERE uid = %d AND mid <= %d", -channelId, maxMessageId)).stepThis().dispose();
            database.executeFast(String.format(Locale.US, "DELETE FROM media_v2 WHERE uid = %d AND mid <= %d", -channelId, maxMessageId)).stepThis().dispose();
            database.executeFast(String.format(Locale.US, "UPDATE media_counts_v2 SET old = 1 WHERE uid = %d", -channelId)).stepThis().dispose();
            return dialogsIds;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public ArrayList<Long> markMessagesAsDeleted(final int channelId, final int mid, boolean useQueue, boolean deleteFiles) {
        if (useQueue) {
            storageQueue.postRunnable(() -> markMessagesAsDeletedInternal(channelId, mid, deleteFiles));
        } else {
            return markMessagesAsDeletedInternal(channelId, mid, deleteFiles);
        }
        return null;
    }

    private void fixUnsupportedMedia(TLRPC.Message message) {
        if (message == null) {
            return;
        }
        boolean ok = false;
        if (message.media instanceof TLRPC.TL_messageMediaUnsupported_old) {
            if (message.media.bytes.length == 0) {
                message.media.bytes = new byte[1];
                message.media.bytes[0] = TLRPC.LAYER;
            }
        } else if (message.media instanceof TLRPC.TL_messageMediaUnsupported) {
            message.media = new TLRPC.TL_messageMediaUnsupported_old();
            message.media.bytes = new byte[1];
            message.media.bytes[0] = TLRPC.LAYER;
            message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;
        }
    }

    private void doneHolesInTable(String table, long did, int max_id) throws Exception {
        if (max_id == 0) {
            database.executeFast(String.format(Locale.US, "DELETE FROM " + table + " WHERE uid = %d", did)).stepThis().dispose();
        } else {
            database.executeFast(String.format(Locale.US, "DELETE FROM " + table + " WHERE uid = %d AND start = 0", did)).stepThis().dispose();
        }
        SQLitePreparedStatement state = database.executeFast("REPLACE INTO " + table + " VALUES(?, ?, ?)");
        state.requery();
        state.bindLong(1, did);
        state.bindInteger(2, 1);
        state.bindInteger(3, 1);
        state.step();
        state.dispose();
    }

    public void doneHolesInMedia(long did, int max_id, int type) throws Exception {
        if (type == -1) {
            if (max_id == 0) {
                database.executeFast(String.format(Locale.US, "DELETE FROM media_holes_v2 WHERE uid = %d", did)).stepThis().dispose();
            } else {
                database.executeFast(String.format(Locale.US, "DELETE FROM media_holes_v2 WHERE uid = %d AND start = 0", did)).stepThis().dispose();
            }
            SQLitePreparedStatement state = database.executeFast("REPLACE INTO media_holes_v2 VALUES(?, ?, ?, ?)");
            for (int a = 0; a < MediaDataController.MEDIA_TYPES_COUNT; a++) {
                state.requery();
                state.bindLong(1, did);
                state.bindInteger(2, a);
                state.bindInteger(3, 1);
                state.bindInteger(4, 1);
                state.step();
            }
            state.dispose();
        } else {
            if (max_id == 0) {
                database.executeFast(String.format(Locale.US, "DELETE FROM media_holes_v2 WHERE uid = %d AND type = %d", did, type)).stepThis().dispose();
            } else {
                database.executeFast(String.format(Locale.US, "DELETE FROM media_holes_v2 WHERE uid = %d AND type = %d AND start = 0", did, type)).stepThis().dispose();
            }
            SQLitePreparedStatement state = database.executeFast("REPLACE INTO media_holes_v2 VALUES(?, ?, ?, ?)");
            state.requery();
            state.bindLong(1, did);
            state.bindInteger(2, type);
            state.bindInteger(3, 1);
            state.bindInteger(4, 1);
            state.step();
            state.dispose();
        }
    }

    private class Hole {

        public Hole(int s, int e) {
            start = s;
            end = e;
        }

        public Hole(int t, int s, int e) {
            type = t;
            start = s;
            end = e;
        }

        public int start;
        public int end;
        public int type;
    }

    public void closeHolesInMedia(long did, int minId, int maxId, int type) {
        try {
            boolean ok = false;
            SQLiteCursor cursor;
            if (type < 0) {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT type, start, end FROM media_holes_v2 WHERE uid = %d AND type >= 0 AND ((end >= %d AND end <= %d) OR (start >= %d AND start <= %d) OR (start >= %d AND end <= %d) OR (start <= %d AND end >= %d))", did, minId, maxId, minId, maxId, minId, maxId, minId, maxId));
            } else {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT type, start, end FROM media_holes_v2 WHERE uid = %d AND type = %d AND ((end >= %d AND end <= %d) OR (start >= %d AND start <= %d) OR (start >= %d AND end <= %d) OR (start <= %d AND end >= %d))", did, type, minId, maxId, minId, maxId, minId, maxId, minId, maxId));
            }
            ArrayList<Hole> holes = null;
            while (cursor.next()) {
                if (holes == null) {
                    holes = new ArrayList<>();
                }
                int holeType = cursor.intValue(0);
                int start = cursor.intValue(1);
                int end = cursor.intValue(2);
                if (start == end && start == 1) {
                    continue;
                }
                holes.add(new Hole(holeType, start, end));
            }
            cursor.dispose();
            if (holes != null) {
                for (int a = 0; a < holes.size(); a++) {
                    Hole hole = holes.get(a);
                    if (maxId >= hole.end - 1 && minId <= hole.start + 1) {
                        database.executeFast(String.format(Locale.US, "DELETE FROM media_holes_v2 WHERE uid = %d AND type = %d AND start = %d AND end = %d", did, hole.type, hole.start, hole.end)).stepThis().dispose();
                    } else if (maxId >= hole.end - 1) {
                        if (hole.end != minId) {
                            try {
                                database.executeFast(String.format(Locale.US, "UPDATE media_holes_v2 SET end = %d WHERE uid = %d AND type = %d AND start = %d AND end = %d", minId, did, hole.type, hole.start, hole.end)).stepThis().dispose();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    } else if (minId <= hole.start + 1) {
                        if (hole.start != maxId) {
                            try {
                                database.executeFast(String.format(Locale.US, "UPDATE media_holes_v2 SET start = %d WHERE uid = %d AND type = %d AND start = %d AND end = %d", maxId, did, hole.type, hole.start, hole.end)).stepThis().dispose();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    } else {
                        database.executeFast(String.format(Locale.US, "DELETE FROM media_holes_v2 WHERE uid = %d AND type = %d AND start = %d AND end = %d", did, hole.type, hole.start, hole.end)).stepThis().dispose();
                        SQLitePreparedStatement state = database.executeFast("REPLACE INTO media_holes_v2 VALUES(?, ?, ?, ?)");
                        state.requery();
                        state.bindLong(1, did);
                        state.bindInteger(2, hole.type);
                        state.bindInteger(3, hole.start);
                        state.bindInteger(4, minId);
                        state.step();
                        state.requery();
                        state.bindLong(1, did);
                        state.bindInteger(2, hole.type);
                        state.bindInteger(3, maxId);
                        state.bindInteger(4, hole.end);
                        state.step();
                        state.dispose();
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void closeHolesInTable(String table, long did, int minId, int maxId) {
        try {
            boolean ok = false;
            SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT start, end FROM " + table + " WHERE uid = %d AND ((end >= %d AND end <= %d) OR (start >= %d AND start <= %d) OR (start >= %d AND end <= %d) OR (start <= %d AND end >= %d))", did, minId, maxId, minId, maxId, minId, maxId, minId, maxId));
            ArrayList<Hole> holes = null;
            while (cursor.next()) {
                if (holes == null) {
                    holes = new ArrayList<>();
                }
                int start = cursor.intValue(0);
                int end = cursor.intValue(1);
                if (start == end && start == 1) {
                    continue;
                }
                holes.add(new Hole(start, end));
            }
            cursor.dispose();
            if (holes != null) {
                for (int a = 0; a < holes.size(); a++) {
                    Hole hole = holes.get(a);
                    if (maxId >= hole.end - 1 && minId <= hole.start + 1) {
                        database.executeFast(String.format(Locale.US, "DELETE FROM " + table + " WHERE uid = %d AND start = %d AND end = %d", did, hole.start, hole.end)).stepThis().dispose();
                    } else if (maxId >= hole.end - 1) {
                        if (hole.end != minId) {
                            try {
                                database.executeFast(String.format(Locale.US, "UPDATE " + table + " SET end = %d WHERE uid = %d AND start = %d AND end = %d", minId, did, hole.start, hole.end)).stepThis().dispose();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    } else if (minId <= hole.start + 1) {
                        if (hole.start != maxId) {
                            try {
                                database.executeFast(String.format(Locale.US, "UPDATE " + table + " SET start = %d WHERE uid = %d AND start = %d AND end = %d", maxId, did, hole.start, hole.end)).stepThis().dispose();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    } else {
                        database.executeFast(String.format(Locale.US, "DELETE FROM " + table + " WHERE uid = %d AND start = %d AND end = %d", did, hole.start, hole.end)).stepThis().dispose();
                        SQLitePreparedStatement state = database.executeFast("REPLACE INTO " + table + " VALUES(?, ?, ?)");
                        state.requery();
                        state.bindLong(1, did);
                        state.bindInteger(2, hole.start);
                        state.bindInteger(3, minId);
                        state.step();
                        state.requery();
                        state.bindLong(1, did);
                        state.bindInteger(2, maxId);
                        state.bindInteger(3, hole.end);
                        state.step();
                        state.dispose();
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void replaceMessageIfExists(final TLRPC.Message message, int currentAccount, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats, boolean broadcast) {
        if (message == null) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                long messageId = message.id;
                int channelId = 0;
                if (channelId == 0) {
                    channelId = message.to_id.channel_id;
                }
                if (message.to_id.channel_id != 0) {
                    messageId |= ((long) channelId) << 32;
                }

                SQLiteCursor cursor = null;
                try {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid FROM messages WHERE mid = %d LIMIT 1", messageId));
                    if (!cursor.next()) {
                        return;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }

                database.beginTransaction();

                SQLitePreparedStatement state = database.executeFast("REPLACE INTO messages VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)");
                SQLitePreparedStatement state2 = database.executeFast("REPLACE INTO media_v2 VALUES(?, ?, ?, ?, ?)");

                if (message.dialog_id == 0) {
                    MessageObject.getDialogId(message);
                }

                fixUnsupportedMedia(message);
                state.requery();
                NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                message.serializeToStream(data);
                state.bindLong(1, messageId);
                state.bindLong(2, message.dialog_id);
                state.bindInteger(3, MessageObject.getUnreadFlags(message));
                state.bindInteger(4, message.send_state);
                state.bindInteger(5, message.date);
                state.bindByteBuffer(6, data);
                state.bindInteger(7, (MessageObject.isOut(message) || message.from_scheduled ? 1 : 0));
                state.bindInteger(8, message.ttl);
                if ((message.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                    state.bindInteger(9, message.views);
                } else {
                    state.bindInteger(9, getMessageMediaType(message));
                }
                state.bindInteger(10, 0);
                state.bindInteger(11, message.mentioned ? 1 : 0);
                state.step();

                if (MediaDataController.canAddMessageToMedia(message)) {
                    state2.requery();
                    state2.bindLong(1, messageId);
                    state2.bindLong(2, message.dialog_id);
                    state2.bindInteger(3, message.date);
                    state2.bindInteger(4, MediaDataController.getMediaType(message));
                    state2.bindByteBuffer(5, data);
                    state2.step();
                }
                data.reuse();

                state.dispose();
                state2.dispose();

                database.commitTransaction();
                if (broadcast) {
                    HashMap<Integer, TLRPC.User> userHashMap = new HashMap<>();
                    HashMap<Integer, TLRPC.Chat> chatHashMap = new HashMap<>();
                    for (int a = 0; a < users.size(); a++) {
                        TLRPC.User user = users.get(a);
                        userHashMap.put(user.id, user);
                    }
                    for (int a = 0; a < chats.size(); a++) {
                        TLRPC.Chat chat = chats.get(a);
                        chatHashMap.put(chat.id, chat);
                    }
                    MessageObject messageObject = new MessageObject(currentAccount, message, userHashMap, chatHashMap, true);
                    ArrayList<MessageObject> arrayList = new ArrayList<>();
                    arrayList.add(messageObject);
                    AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.replaceMessagesObjects, messageObject.getDialogId(), arrayList));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void putMessages(final TLRPC.messages_Messages messages, final long dialog_id, final int load_type, final int max_id, final boolean createDialog, final boolean scheduled) {
        storageQueue.postRunnable(() -> {
            try {
                if (scheduled) {
                    database.executeFast(String.format(Locale.US, "DELETE FROM scheduled_messages WHERE uid = %d AND mid > 0", dialog_id)).stepThis().dispose();
                    SQLitePreparedStatement state_messages = database.executeFast("REPLACE INTO scheduled_messages VALUES(?, ?, ?, ?, ?, ?, NULL)");
                    int channelId = 0;
                    int count = messages.messages.size();
                    for (int a = 0; a < count; a++) {
                        TLRPC.Message message = messages.messages.get(a);

                        long messageId = message.id;
                        if (channelId == 0) {
                            channelId = message.to_id.channel_id;
                        }
                        if (message.to_id.channel_id != 0) {
                            messageId |= ((long) channelId) << 32;
                        }

                        fixUnsupportedMedia(message);
                        state_messages.requery();
                        NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                        message.serializeToStream(data);
                        state_messages.bindLong(1, messageId);
                        state_messages.bindLong(2, dialog_id);
                        state_messages.bindInteger(3, message.send_state);
                        state_messages.bindInteger(4, message.date);
                        state_messages.bindByteBuffer(5, data);
                        state_messages.bindInteger(6, message.ttl);
                        state_messages.step();

                        data.reuse();
                    }
                    state_messages.dispose();

                    putUsersInternal(messages.users);
                    putChatsInternal(messages.chats);

                    database.commitTransaction();
                    broadcastScheduledMessagesChange(dialog_id);
                } else {
                    int mentionCountUpdate = Integer.MAX_VALUE;
                    if (messages.messages.isEmpty()) {
                        if (load_type == 0) {
                            doneHolesInTable("messages_holes", dialog_id, max_id);
                            doneHolesInMedia(dialog_id, max_id, -1);
                        }
                        return;
                    }
                    database.beginTransaction();

                    if (load_type == 0) {
                        int minId = messages.messages.get(messages.messages.size() - 1).id;
                        closeHolesInTable("messages_holes", dialog_id, minId, max_id);
                        closeHolesInMedia(dialog_id, minId, max_id, -1);
                    } else if (load_type == 1) {
                        int maxId = messages.messages.get(0).id;
                        closeHolesInTable("messages_holes", dialog_id, max_id, maxId);
                        closeHolesInMedia(dialog_id, max_id, maxId, -1);
                    } else if (load_type == 3 || load_type == 2 || load_type == 4) {
                        int maxId = max_id == 0 && load_type != 4 ? Integer.MAX_VALUE : messages.messages.get(0).id;
                        int minId = messages.messages.get(messages.messages.size() - 1).id;
                        closeHolesInTable("messages_holes", dialog_id, minId, maxId);
                        closeHolesInMedia(dialog_id, minId, maxId, -1);
                    }
                    int count = messages.messages.size();

                    //load_type == 0 ? backward loading
                    //load_type == 1 ? forward loading
                    //load_type == 2 ? load from first unread
                    //load_type == 3 ? load around message
                    //load_type == 4 ? load around date
                    ArrayList<File> filesToDelete = new ArrayList<>();

                    SQLitePreparedStatement state_messages = database.executeFast("REPLACE INTO messages VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)");
                    SQLitePreparedStatement state_media = database.executeFast("REPLACE INTO media_v2 VALUES(?, ?, ?, ?, ?)");
                    SQLitePreparedStatement state_polls = null;
                    SQLitePreparedStatement state_webpage = null;
                    TLRPC.Message botKeyboard = null;
                    int channelId = 0;
                    for (int a = 0; a < count; a++) {
                        TLRPC.Message message = messages.messages.get(a);

                        long messageId = message.id;
                        if (channelId == 0) {
                            channelId = message.to_id.channel_id;
                        }
                        if (message.to_id.channel_id != 0) {
                            messageId |= ((long) channelId) << 32;
                        }

                        if (load_type == -2) {
                            SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid, data, ttl, mention, read_state, send_state FROM messages WHERE mid = %d", messageId));
                            boolean exist;
                            if (exist = cursor.next()) {
                                NativeByteBuffer data = cursor.byteBufferValue(1);
                                if (data != null) {
                                    TLRPC.Message oldMessage = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                    oldMessage.readAttachPath(data, getUserConfig().clientUserId);
                                    data.reuse();
                                    int send_state = cursor.intValue(5);
                                    if (oldMessage != null && send_state != 3) {
                                        message.attachPath = oldMessage.attachPath;
                                        message.ttl = cursor.intValue(2);
                                    }
                                    if (!message.out) {
                                        boolean sameMedia = false; //TODO check
                                        if (oldMessage.media instanceof TLRPC.TL_messageMediaPhoto && message.media instanceof TLRPC.TL_messageMediaPhoto && oldMessage.media.photo != null && message.media.photo != null) {
                                            sameMedia = oldMessage.media.photo.id == message.media.photo.id;
                                        } else if (oldMessage.media instanceof TLRPC.TL_messageMediaDocument && message.media instanceof TLRPC.TL_messageMediaDocument && oldMessage.media.document != null && message.media.document != null) {
                                            sameMedia = oldMessage.media.document.id == message.media.document.id;
                                        }
                                        if (!sameMedia) {
                                            addFilesToDelete(oldMessage, filesToDelete, false);
                                        }
                                    }
                                }
                                boolean oldMention = cursor.intValue(3) != 0;
                                int readState = cursor.intValue(4);
                                if (oldMention != message.mentioned) {
                                    if (mentionCountUpdate == Integer.MAX_VALUE) {
                                        SQLiteCursor cursor2 = database.queryFinalized("SELECT unread_count_i FROM dialogs WHERE did = " + dialog_id);
                                        if (cursor2.next()) {
                                            mentionCountUpdate = cursor2.intValue(0);
                                        }
                                        cursor2.dispose();
                                    }
                                    if (oldMention) {
                                        if (readState <= 1) {
                                            mentionCountUpdate--;
                                        }
                                    } else {
                                        if (message.media_unread) {
                                            mentionCountUpdate++;
                                        }
                                    }
                                }
                            }
                            cursor.dispose();
                            if (!exist) {
                                continue;
                            }
                        }

                        if (a == 0 && createDialog) {
                            int pinned = 0;
                            int mentions = 0;
                            int flags = 0;
                            SQLiteCursor cursor = database.queryFinalized("SELECT pinned, unread_count_i, flags FROM dialogs WHERE did = " + dialog_id);
                            boolean exist;
                            if (exist = cursor.next()) {
                                pinned = cursor.intValue(0);
                                mentions = cursor.intValue(1);
                                flags = cursor.intValue(2);
                            }
                            cursor.dispose();

                            SQLitePreparedStatement state3;
                            if (exist) {
                                state3 = database.executeFast("UPDATE dialogs SET date = ?, last_mid = ?, inbox_max = ?, last_mid_i = ?, pts = ?, date_i = ? WHERE did = ?");
                                state3.bindInteger(1, message.date);
                                state3.bindLong(2, messageId);
                                state3.bindInteger(3, message.id);
                                state3.bindLong(4, messageId);
                                state3.bindInteger(5, messages.pts);
                                state3.bindInteger(6, message.date);
                                state3.bindLong(7, dialog_id);
                            } else {
                                state3 = database.executeFast("REPLACE INTO dialogs VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                                state3.bindLong(1, dialog_id);
                                state3.bindInteger(2, message.date);
                                state3.bindInteger(3, 0);
                                state3.bindLong(4, messageId);
                                state3.bindInteger(5, message.id);
                                state3.bindInteger(6, 0);
                                state3.bindLong(7, messageId);
                                state3.bindInteger(8, mentions);
                                state3.bindInteger(9, messages.pts);
                                state3.bindInteger(10, message.date);
                                state3.bindInteger(11, pinned);
                                state3.bindInteger(12, flags);
                                state3.bindInteger(13, 0);
                                state3.bindNull(14);
                            }
                            state3.step();
                            state3.dispose();
                        }

                        fixUnsupportedMedia(message);
                        state_messages.requery();
                        NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                        message.serializeToStream(data);
                        state_messages.bindLong(1, messageId);
                        state_messages.bindLong(2, dialog_id);
                        state_messages.bindInteger(3, MessageObject.getUnreadFlags(message));
                        state_messages.bindInteger(4, message.send_state);
                        state_messages.bindInteger(5, message.date);
                        state_messages.bindByteBuffer(6, data);
                        state_messages.bindInteger(7, (MessageObject.isOut(message) || message.from_scheduled ? 1 : 0));
                        state_messages.bindInteger(8, message.ttl);
                        if ((message.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                            state_messages.bindInteger(9, message.views);
                        } else {
                            state_messages.bindInteger(9, getMessageMediaType(message));
                        }
                        state_messages.bindInteger(10, 0);
                        state_messages.bindInteger(11, message.mentioned ? 1 : 0);
                        state_messages.step();

                        if (MediaDataController.canAddMessageToMedia(message)) {
                            state_media.requery();
                            state_media.bindLong(1, messageId);
                            state_media.bindLong(2, dialog_id);
                            state_media.bindInteger(3, message.date);
                            state_media.bindInteger(4, MediaDataController.getMediaType(message));
                            state_media.bindByteBuffer(5, data);
                            state_media.step();
                        }
                        data.reuse();

                        if (message.media instanceof TLRPC.TL_messageMediaPoll) {
                            if (state_polls == null) {
                                state_polls = database.executeFast("REPLACE INTO polls VALUES(?, ?)");
                            }
                            TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) message.media;
                            state_polls.requery();
                            state_polls.bindLong(1, messageId);
                            state_polls.bindLong(2, mediaPoll.poll.id);
                            state_polls.step();
                        } else if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
                            if (state_webpage == null) {
                                state_webpage = database.executeFast("REPLACE INTO webpage_pending VALUES(?, ?)");
                            }
                            state_webpage.requery();
                            state_webpage.bindLong(1, message.media.webpage.id);
                            state_webpage.bindLong(2, messageId);
                            state_webpage.step();
                        }

                        if (load_type == 0 && isValidKeyboardToSave(message)) {
                            if (botKeyboard == null || botKeyboard.id < message.id) {
                                botKeyboard = message;
                            }
                        }
                    }
                    state_messages.dispose();
                    state_media.dispose();
                    if (state_webpage != null) {
                        state_webpage.dispose();
                    }
                    if (state_polls != null) {
                        state_polls.dispose();
                    }
                    if (botKeyboard != null) {
                        getMediaDataController().putBotKeyboard(dialog_id, botKeyboard);
                    }
                    getFileLoader().deleteFiles(filesToDelete, 0);
                    putUsersInternal(messages.users);
                    putChatsInternal(messages.chats);

                    if (mentionCountUpdate != Integer.MAX_VALUE) {
                        database.executeFast(String.format(Locale.US, "UPDATE dialogs SET unread_count_i = %d WHERE did = %d", mentionCountUpdate, dialog_id)).stepThis().dispose();
                        LongSparseArray<Integer> sparseArray = new LongSparseArray<>(1);
                        sparseArray.put(dialog_id, mentionCountUpdate);
                        getMessagesController().processDialogsUpdateRead(null, sparseArray);
                    }

                    database.commitTransaction();

                    if (createDialog) {
                        updateDialogsWithDeletedMessages(new ArrayList<>(), null, false, channelId);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public static void addUsersAndChatsFromMessage(TLRPC.Message message, ArrayList<Integer> usersToLoad, ArrayList<Integer> chatsToLoad) {
        if (message.from_id != 0) {
            if (message.from_id > 0) {
                if (!usersToLoad.contains(message.from_id)) {
                    usersToLoad.add(message.from_id);
                }
            } else {
                if (!chatsToLoad.contains(-message.from_id)) {
                    chatsToLoad.add(-message.from_id);
                }
            }
        }
        if (message.via_bot_id != 0 && !usersToLoad.contains(message.via_bot_id)) {
            usersToLoad.add(message.via_bot_id);
        }
        if (message.action != null) {
            if (message.action.user_id != 0 && !usersToLoad.contains(message.action.user_id)) {
                usersToLoad.add(message.action.user_id);
            }
            if (message.action.channel_id != 0 && !chatsToLoad.contains(message.action.channel_id)) {
                chatsToLoad.add(message.action.channel_id);
            }
            if (message.action.chat_id != 0 && !chatsToLoad.contains(message.action.chat_id)) {
                chatsToLoad.add(message.action.chat_id);
            }
            if (!message.action.users.isEmpty()) {
                for (int a = 0; a < message.action.users.size(); a++) {
                    Integer uid = message.action.users.get(a);
                    if (!usersToLoad.contains(uid)) {
                        usersToLoad.add(uid);
                    }
                }
            }
        }
        if (!message.entities.isEmpty()) {
            for (int a = 0; a < message.entities.size(); a++) {
                TLRPC.MessageEntity entity = message.entities.get(a);
                if (entity instanceof TLRPC.TL_messageEntityMentionName) {
                    usersToLoad.add(((TLRPC.TL_messageEntityMentionName) entity).user_id);
                } else if (entity instanceof TLRPC.TL_inputMessageEntityMentionName) {
                    usersToLoad.add(((TLRPC.TL_inputMessageEntityMentionName) entity).user_id.user_id);
                }
            }
        }
        if (message.media != null) {
            if (message.media.user_id != 0 && !usersToLoad.contains(message.media.user_id)) {
                usersToLoad.add(message.media.user_id);
            }
        }
        if (message.fwd_from != null) {
            if (message.fwd_from.from_id != 0) {
                if (!usersToLoad.contains(message.fwd_from.from_id)) {
                    usersToLoad.add(message.fwd_from.from_id);
                }
            }
            if (message.fwd_from.channel_id != 0) {
                if (!chatsToLoad.contains(message.fwd_from.channel_id)) {
                    chatsToLoad.add(message.fwd_from.channel_id);
                }
            }
            if (message.fwd_from.saved_from_peer != null) {
                if (message.fwd_from.saved_from_peer.user_id != 0) {
                    if (!chatsToLoad.contains(message.fwd_from.saved_from_peer.user_id)) {
                        usersToLoad.add(message.fwd_from.saved_from_peer.user_id);
                    }
                } else if (message.fwd_from.saved_from_peer.channel_id != 0) {
                    if (!chatsToLoad.contains(message.fwd_from.saved_from_peer.channel_id)) {
                        chatsToLoad.add(message.fwd_from.saved_from_peer.channel_id);
                    }
                } else if (message.fwd_from.saved_from_peer.chat_id != 0) {
                    if (!chatsToLoad.contains(message.fwd_from.saved_from_peer.chat_id)) {
                        chatsToLoad.add(message.fwd_from.saved_from_peer.chat_id);
                    }
                }
            }
        }
        if (message.ttl < 0) {
            if (!chatsToLoad.contains(-message.ttl)) {
                chatsToLoad.add(-message.ttl);
            }
        }
    }

    public void getDialogs(final int folderId, final int offset, final int count) {
        storageQueue.postRunnable(() -> {
            TLRPC.messages_Dialogs dialogs = new TLRPC.TL_messages_dialogs();
            ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
            try {
                ArrayList<Integer> usersToLoad = new ArrayList<>();
                usersToLoad.add(getUserConfig().getClientUserId());
                ArrayList<Integer> chatsToLoad = new ArrayList<>();
                ArrayList<Integer> encryptedToLoad = new ArrayList<>();
                ArrayList<Long> replyMessages = new ArrayList<>();
                LongSparseArray<TLRPC.Message> replyMessageOwners = new LongSparseArray<>();
                ArrayList<Integer> foldersToLoad = new ArrayList<>(2);
                foldersToLoad.add(folderId);
                for (int a = 0; a < foldersToLoad.size(); a++) {
                    int fid = foldersToLoad.get(a);
                    int off;
                    int cnt;
                    if (a == 0) {
                        off = offset;
                        cnt = count;
                    } else {
                        off = 0;
                        cnt = 50;
                    }

                    SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT d.did, d.last_mid, d.unread_count, d.date, m.data, m.read_state, m.mid, m.send_state, s.flags, m.date, d.pts, d.inbox_max, d.outbox_max, m.replydata, d.pinned, d.unread_count_i, d.flags, d.folder_id, d.data FROM dialogs as d LEFT JOIN messages as m ON d.last_mid = m.mid LEFT JOIN dialog_settings as s ON d.did = s.did WHERE d.folder_id = %d ORDER BY d.pinned DESC, d.date DESC LIMIT %d,%d", fid, off, cnt));
                    while (cursor.next()) {
                        long dialogId = cursor.longValue(0);
                        TLRPC.Dialog dialog;
                        if (DialogObject.isFolderDialogId(dialogId)) {
                            TLRPC.TL_dialogFolder dialogFolder = new TLRPC.TL_dialogFolder();
                            if (!cursor.isNull(18)) {
                                NativeByteBuffer data = cursor.byteBufferValue(18);
                                if (data != null) {
                                    dialogFolder.folder = TLRPC.TL_folder.TLdeserialize(data, data.readInt32(false), false);
                                } else {
                                    dialogFolder.folder = new TLRPC.TL_folder();
                                    dialogFolder.folder.id = (int) dialogId;
                                }
                                data.reuse();
                            }
                            dialog = dialogFolder;
                            if (a == 0) {
                                foldersToLoad.add(dialogFolder.folder.id);
                            }
                        } else {
                            dialog = new TLRPC.TL_dialog();
                        }
                        dialog.id = dialogId;
                        dialog.top_message = cursor.intValue(1);
                        dialog.unread_count = cursor.intValue(2);
                        dialog.last_message_date = cursor.intValue(3);
                        dialog.pts = cursor.intValue(10);
                        dialog.flags = dialog.pts == 0 || (int) dialog.id > 0 ? 0 : 1;
                        dialog.read_inbox_max_id = cursor.intValue(11);
                        dialog.read_outbox_max_id = cursor.intValue(12);
                        dialog.pinnedNum = cursor.intValue(14);
                        dialog.pinned = dialog.pinnedNum != 0;
                        dialog.unread_mentions_count = cursor.intValue(15);
                        int dialog_flags = cursor.intValue(16);
                        dialog.unread_mark = (dialog_flags & 1) != 0;
                        long flags = cursor.longValue(8);
                        int low_flags = (int) flags;
                        dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                        if ((low_flags & 1) != 0) {
                            dialog.notify_settings.mute_until = (int) (flags >> 32);
                            if (dialog.notify_settings.mute_until == 0) {
                                dialog.notify_settings.mute_until = Integer.MAX_VALUE;
                            }
                        }
                        dialog.folder_id = cursor.intValue(17);
                        dialogs.dialogs.add(dialog);

                        NativeByteBuffer data = cursor.byteBufferValue(4);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.readAttachPath(data, getUserConfig().clientUserId);
                            data.reuse();
                            if (message != null) {
                                MessageObject.setUnreadFlags(message, cursor.intValue(5));
                                message.id = cursor.intValue(6);
                                int date = cursor.intValue(9);
                                if (date != 0) {
                                    dialog.last_message_date = date;
                                }
                                message.send_state = cursor.intValue(7);
                                message.dialog_id = dialog.id;
                                dialogs.messages.add(message);

                                addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);

                                try {
                                    if (message.reply_to_msg_id != 0 && (
                                            message.action instanceof TLRPC.TL_messageActionPinMessage ||
                                                    message.action instanceof TLRPC.TL_messageActionPaymentSent ||
                                                    message.action instanceof TLRPC.TL_messageActionGameScore)) {
                                        if (!cursor.isNull(13)) {
                                            data = cursor.byteBufferValue(13);
                                            if (data != null) {
                                                message.replyMessage = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                                message.replyMessage.readAttachPath(data, getUserConfig().clientUserId);
                                                data.reuse();
                                                if (message.replyMessage != null) {
                                                    if (MessageObject.isMegagroup(message)) {
                                                        message.replyMessage.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                                    }
                                                    addUsersAndChatsFromMessage(message.replyMessage, usersToLoad, chatsToLoad);
                                                }
                                            }
                                        }
                                        if (message.replyMessage == null) {
                                            long messageId = message.reply_to_msg_id;
                                            if (message.to_id.channel_id != 0) {
                                                messageId |= ((long) message.to_id.channel_id) << 32;
                                            }
                                            if (!replyMessages.contains(messageId)) {
                                                replyMessages.add(messageId);
                                            }
                                            replyMessageOwners.put(dialog.id, message);
                                        }
                                    }
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            }
                        }

                        int lower_id = (int) dialog.id;
                        int high_id = (int) (dialog.id >> 32);
                        if (lower_id != 0) {
                            if (lower_id > 0) {
                                if (!usersToLoad.contains(lower_id)) {
                                    usersToLoad.add(lower_id);
                                }
                            } else {
                                if (!chatsToLoad.contains(-lower_id)) {
                                    chatsToLoad.add(-lower_id);
                                }
                            }
                        } else {
                            if (!encryptedToLoad.contains(high_id)) {
                                encryptedToLoad.add(high_id);
                            }
                        }
                    }
                    cursor.dispose();
                }

                if (!replyMessages.isEmpty()) {
                    SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid, date, uid FROM messages WHERE mid IN(%s)", TextUtils.join(",", replyMessages)));
                    while (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.readAttachPath(data, getUserConfig().clientUserId);
                            data.reuse();
                            message.id = cursor.intValue(1);
                            message.date = cursor.intValue(2);
                            message.dialog_id = cursor.longValue(3);

                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);

                            TLRPC.Message owner = replyMessageOwners.get(message.dialog_id);
                            if (owner != null) {
                                owner.replyMessage = message;
                                message.dialog_id = owner.dialog_id;
                                if (MessageObject.isMegagroup(owner)) {
                                    owner.replyMessage.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                }
                            }
                        }
                    }
                    cursor.dispose();
                }

                if (!encryptedToLoad.isEmpty()) {
                    getEncryptedChatsInternal(TextUtils.join(",", encryptedToLoad), encryptedChats, usersToLoad);
                }
                if (!chatsToLoad.isEmpty()) {
                    getChatsInternal(TextUtils.join(",", chatsToLoad), dialogs.chats);
                }
                if (!usersToLoad.isEmpty()) {
                    getUsersInternal(TextUtils.join(",", usersToLoad), dialogs.users);
                }
                getMessagesController().processLoadedDialogs(dialogs, encryptedChats, folderId, offset, count, 1, false, false, true);
            } catch (Exception e) {
                dialogs.dialogs.clear();
                dialogs.users.clear();
                dialogs.chats.clear();
                encryptedChats.clear();
                FileLog.e(e);
                getMessagesController().processLoadedDialogs(dialogs, encryptedChats, folderId, 0, 100, 1, true, false, true);
            }
        });
    }

    public static void createFirstHoles(long did, SQLitePreparedStatement state5, SQLitePreparedStatement state6, int messageId) throws Exception {
        state5.requery();
        state5.bindLong(1, did);
        state5.bindInteger(2, messageId == 1 ? 1 : 0);
        state5.bindInteger(3, messageId);
        state5.step();

        for (int b = 0; b < MediaDataController.MEDIA_TYPES_COUNT; b++) {
            state6.requery();
            state6.bindLong(1, did);
            state6.bindInteger(2, b);
            state6.bindInteger(3, messageId == 1 ? 1 : 0);
            state6.bindInteger(4, messageId);
            state6.step();
        }
    }

    private void putDialogsInternal(final TLRPC.messages_Dialogs dialogs, int check) {
        try {
            database.beginTransaction();
            final LongSparseArray<TLRPC.Message> new_dialogMessage = new LongSparseArray<>(dialogs.messages.size());
            for (int a = 0; a < dialogs.messages.size(); a++) {
                TLRPC.Message message = dialogs.messages.get(a);
                new_dialogMessage.put(MessageObject.getDialogId(message), message);
            }

            if (!dialogs.dialogs.isEmpty()) {
                SQLitePreparedStatement state_messages = database.executeFast("REPLACE INTO messages VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)");
                SQLitePreparedStatement state_dialogs = database.executeFast("REPLACE INTO dialogs VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                SQLitePreparedStatement state_media = database.executeFast("REPLACE INTO media_v2 VALUES(?, ?, ?, ?, ?)");
                SQLitePreparedStatement state_settings = database.executeFast("REPLACE INTO dialog_settings VALUES(?, ?)");
                SQLitePreparedStatement state_holes = database.executeFast("REPLACE INTO messages_holes VALUES(?, ?, ?)");
                SQLitePreparedStatement state_media_holes = database.executeFast("REPLACE INTO media_holes_v2 VALUES(?, ?, ?, ?)");
                SQLitePreparedStatement state_polls = null;

                for (int a = 0; a < dialogs.dialogs.size(); a++) {
                    TLRPC.Dialog dialog = dialogs.dialogs.get(a);

                    DialogObject.initDialog(dialog);
                    if (check == 1) {
                        SQLiteCursor cursor = database.queryFinalized("SELECT did FROM dialogs WHERE did = " + dialog.id);
                        boolean exists = cursor.next();
                        cursor.dispose();
                        if (exists) {
                            continue;
                        }
                    } else if (dialog.pinned && check == 2) {
                        SQLiteCursor cursor = database.queryFinalized("SELECT pinned FROM dialogs WHERE did = " + dialog.id);
                        if (cursor.next()) {
                            dialog.pinnedNum = cursor.intValue(0);
                        }
                        cursor.dispose();
                    }
                    int messageDate = 0;

                    TLRPC.Message message = new_dialogMessage.get(dialog.id);
                    if (message != null) {
                        messageDate = Math.max(message.date, messageDate);

                        if (isValidKeyboardToSave(message)) {
                            getMediaDataController().putBotKeyboard(dialog.id, message);
                        }

                        fixUnsupportedMedia(message);
                        NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                        message.serializeToStream(data);

                        long messageId = message.id;
                        if (message.to_id.channel_id != 0) {
                            messageId |= ((long) message.to_id.channel_id) << 32;
                        }

                        state_messages.requery();
                        state_messages.bindLong(1, messageId);
                        state_messages.bindLong(2, dialog.id);
                        state_messages.bindInteger(3, MessageObject.getUnreadFlags(message));
                        state_messages.bindInteger(4, message.send_state);
                        state_messages.bindInteger(5, message.date);
                        state_messages.bindByteBuffer(6, data);
                        state_messages.bindInteger(7, (MessageObject.isOut(message) || message.from_scheduled ? 1 : 0));
                        state_messages.bindInteger(8, 0);
                        state_messages.bindInteger(9, (message.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0 ? message.views : 0);
                        state_messages.bindInteger(10, 0);
                        state_messages.bindInteger(11, message.mentioned ? 1 : 0);
                        state_messages.step();

                        if (MediaDataController.canAddMessageToMedia(message)) {
                            state_media.requery();
                            state_media.bindLong(1, messageId);
                            state_media.bindLong(2, dialog.id);
                            state_media.bindInteger(3, message.date);
                            state_media.bindInteger(4, MediaDataController.getMediaType(message));
                            state_media.bindByteBuffer(5, data);
                            state_media.step();
                        }
                        data.reuse();

                        if (message.media instanceof TLRPC.TL_messageMediaPoll) {
                            if (state_polls == null) {
                                state_polls = database.executeFast("REPLACE INTO polls VALUES(?, ?)");
                            }
                            TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) message.media;
                            state_polls.requery();
                            state_polls.bindLong(1, messageId);
                            state_polls.bindLong(2, mediaPoll.poll.id);
                            state_polls.step();
                        }

                        createFirstHoles(dialog.id, state_holes, state_media_holes, message.id);
                    }

                    long topMessage = dialog.top_message;
                    if (dialog.peer != null && dialog.peer.channel_id != 0) {
                        topMessage |= ((long) dialog.peer.channel_id) << 32;
                    }

                    state_dialogs.requery();
                    state_dialogs.bindLong(1, dialog.id);
                    state_dialogs.bindInteger(2, messageDate);
                    state_dialogs.bindInteger(3, dialog.unread_count);
                    state_dialogs.bindLong(4, topMessage);
                    state_dialogs.bindInteger(5, dialog.read_inbox_max_id);
                    state_dialogs.bindInteger(6, dialog.read_outbox_max_id);
                    state_dialogs.bindLong(7, 0);
                    state_dialogs.bindInteger(8, dialog.unread_mentions_count);
                    state_dialogs.bindInteger(9, dialog.pts);
                    state_dialogs.bindInteger(10, 0);
                    state_dialogs.bindInteger(11, dialog.pinnedNum);
                    int flags = 0;
                    if (dialog.unread_mark) {
                        flags |= 1;
                    }
                    state_dialogs.bindInteger(12, flags);
                    state_dialogs.bindInteger(13, dialog.folder_id);
                    NativeByteBuffer data;
                    if (dialog instanceof TLRPC.TL_dialogFolder) {
                        TLRPC.TL_dialogFolder dialogFolder = (TLRPC.TL_dialogFolder) dialog;
                        data = new NativeByteBuffer(dialogFolder.folder.getObjectSize());
                        dialogFolder.folder.serializeToStream(data);
                        state_dialogs.bindByteBuffer(14, data);
                    } else {
                        data = null;
                        state_dialogs.bindNull(14);
                    }
                    state_dialogs.step();
                    if (data != null) {
                        data.reuse();
                    }

                    if (dialog.notify_settings != null) {
                        state_settings.requery();
                        state_settings.bindLong(1, dialog.id);
                        state_settings.bindInteger(2, dialog.notify_settings.mute_until != 0 ? 1 : 0);
                        state_settings.step();
                    }
                }
                state_messages.dispose();
                state_dialogs.dispose();
                state_media.dispose();
                state_settings.dispose();
                state_holes.dispose();
                state_media_holes.dispose();
                if (state_polls != null) {
                    state_polls.dispose();
                }
            }

            putUsersInternal(dialogs.users);
            putChatsInternal(dialogs.chats);

            database.commitTransaction();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void getDialogFolderId(long dialogId, IntCallback callback) {
        storageQueue.postRunnable(() -> {
            try {
                int folderId;
                SQLiteCursor cursor = database.queryFinalized("SELECT folder_id FROM dialogs WHERE did = ?", dialogId);
                if (cursor.next()) {
                    folderId = cursor.intValue(0);
                } else {
                    folderId = -1;
                }
                cursor.dispose();
                AndroidUtilities.runOnUIThread(() -> callback.run(folderId));
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void setDialogsFolderId(final ArrayList<TLRPC.TL_folderPeer> peers, ArrayList<TLRPC.TL_inputFolderPeer> inputPeers, long dialogId, int folderId) {
        if (peers == null && inputPeers == null && dialogId == 0) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                database.beginTransaction();
                SQLitePreparedStatement state = database.executeFast("UPDATE dialogs SET folder_id = ?, pinned = ? WHERE did = ?");
                if (peers != null) {
                    for (int a = 0, N = peers.size(); a < N; a++) {
                        TLRPC.TL_folderPeer folderPeer = peers.get(a);
                        long did = DialogObject.getPeerDialogId(folderPeer.peer);
                        state.requery();
                        state.bindInteger(1, folderPeer.folder_id);
                        state.bindInteger(2, 0);
                        state.bindLong(3, did);
                        state.step();
                    }
                } else if (inputPeers != null) {
                    for (int a = 0, N = inputPeers.size(); a < N; a++) {
                        TLRPC.TL_inputFolderPeer folderPeer = inputPeers.get(a);
                        long did = DialogObject.getPeerDialogId(folderPeer.peer);
                        state.requery();
                        state.bindInteger(1, folderPeer.folder_id);
                        state.bindInteger(2, 0);
                        state.bindLong(3, did);
                        state.step();
                    }
                } else {
                    state.requery();
                    state.bindInteger(1, folderId);
                    state.bindInteger(2, 0);
                    state.bindLong(3, dialogId);
                    state.step();
                }
                state.dispose();
                database.commitTransaction();
                checkIfFolderEmptyInternal(1);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void checkIfFolderEmptyInternal(int folderId) {
        try {
            SQLiteCursor cursor = database.queryFinalized("SELECT did FROM dialogs WHERE folder_id = ?", folderId);
            if (!cursor.next()) {
                AndroidUtilities.runOnUIThread(() -> getMessagesController().onFolderEmpty(folderId));
                database.executeFast("DELETE FROM dialogs WHERE did = " + DialogObject.makeFolderDialogId(folderId)).stepThis().dispose();
            }
            cursor.dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void checkIfFolderEmpty(int folderId) {
        storageQueue.postRunnable(() -> checkIfFolderEmptyInternal(folderId));
    }

    public void unpinAllDialogsExceptNew(final ArrayList<Long> dids, int folderId) {
        storageQueue.postRunnable(() -> {
            try {
                ArrayList<Long> unpinnedDialogs = new ArrayList<>();
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT did, folder_id FROM dialogs WHERE pinned != 0 AND did NOT IN (%s)", TextUtils.join(",", dids)));
                while (cursor.next()) {
                    long did = cursor.longValue(0);
                    int fid = cursor.intValue(1);
                    if (fid == folderId && (int) did != 0 && !DialogObject.isFolderDialogId(did)) {
                        unpinnedDialogs.add(cursor.longValue(0));
                    }
                }
                cursor.dispose();
                if (!unpinnedDialogs.isEmpty()) {
                    SQLitePreparedStatement state = database.executeFast("UPDATE dialogs SET pinned = ? WHERE did = ?");
                    for (int a = 0; a < unpinnedDialogs.size(); a++) {
                        long did = unpinnedDialogs.get(a);
                        state.requery();
                        state.bindInteger(1, 0);
                        state.bindLong(2, did);
                        state.step();
                    }
                    state.dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void setDialogUnread(final long did, final boolean unread) {
        storageQueue.postRunnable(() -> {
            try {
                int flags = 0;
                SQLiteCursor cursor = null;
                try {
                    cursor = database.queryFinalized("SELECT flags FROM dialogs WHERE did = " + did);
                    if (cursor.next()) {
                        flags = cursor.intValue(0);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }

                if (unread) {
                    flags |= 1;
                } else {
                    flags &= ~1;
                }

                SQLitePreparedStatement state = database.executeFast("UPDATE dialogs SET flags = ? WHERE did = ?");
                state.bindInteger(1, flags);
                state.bindLong(2, did);
                state.step();
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void setDialogPinned(final long did, final int pinned) {
        storageQueue.postRunnable(() -> {
            try {
                SQLitePreparedStatement state = database.executeFast("UPDATE dialogs SET pinned = ? WHERE did = ?");
                state.bindInteger(1, pinned);
                state.bindLong(2, did);
                state.step();
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void putDialogs(final TLRPC.messages_Dialogs dialogs, final int check) {
        if (dialogs.dialogs.isEmpty()) {
            return;
        }
        storageQueue.postRunnable(() -> {
            putDialogsInternal(dialogs, check);
            try {
                loadUnreadMessages();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public int getDialogReadMax(final boolean outbox, final long dialog_id) {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final Integer[] max = new Integer[]{0};
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                if (outbox) {
                    cursor = database.queryFinalized("SELECT outbox_max FROM dialogs WHERE did = " + dialog_id);
                } else {
                    cursor = database.queryFinalized("SELECT inbox_max FROM dialogs WHERE did = " + dialog_id);
                }
                if (cursor.next()) {
                    max[0] = cursor.intValue(0);
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            countDownLatch.countDown();
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return max[0];
    }

    public int getChannelPtsSync(final int channelId) {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final Integer[] pts = new Integer[]{0};
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized("SELECT pts FROM dialogs WHERE did = " + (-channelId));
                if (cursor.next()) {
                    pts[0] = cursor.intValue(0);
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            try {
                if (countDownLatch != null) {
                    countDownLatch.countDown();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return pts[0];
    }

    public TLRPC.User getUserSync(final int user_id) {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final TLRPC.User[] user = new TLRPC.User[1];
        storageQueue.postRunnable(() -> {
            user[0] = getUser(user_id);
            countDownLatch.countDown();
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return user[0];
    }

    public TLRPC.Chat getChatSync(final int chat_id) {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final TLRPC.Chat[] chat = new TLRPC.Chat[1];
        storageQueue.postRunnable(() -> {
            chat[0] = getChat(chat_id);
            countDownLatch.countDown();
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return chat[0];
    }

    public TLRPC.User getUser(final int user_id) {
        TLRPC.User user = null;
        try {
            ArrayList<TLRPC.User> users = new ArrayList<>();
            getUsersInternal("" + user_id, users);
            if (!users.isEmpty()) {
                user = users.get(0);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return user;
    }

    public ArrayList<TLRPC.User> getUsers(final ArrayList<Integer> uids) {
        ArrayList<TLRPC.User> users = new ArrayList<>();
        try {
            getUsersInternal(TextUtils.join(",", uids), users);
        } catch (Exception e) {
            users.clear();
            FileLog.e(e);
        }
        return users;
    }

    public TLRPC.Chat getChat(final int chat_id) {
        TLRPC.Chat chat = null;
        try {
            ArrayList<TLRPC.Chat> chats = new ArrayList<>();
            getChatsInternal("" + chat_id, chats);
            if (!chats.isEmpty()) {
                chat = chats.get(0);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return chat;
    }

    public TLRPC.EncryptedChat getEncryptedChat(final int chat_id) {
        TLRPC.EncryptedChat chat = null;
        try {
            ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
            getEncryptedChatsInternal("" + chat_id, encryptedChats, null);
            if (!encryptedChats.isEmpty()) {
                chat = encryptedChats.get(0);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return chat;
    }
}
