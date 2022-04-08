/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.appwidget.AppWidgetManager;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Pair;
import android.util.SparseArray;
import android.util.SparseIntArray;

import androidx.annotation.UiThread;
import androidx.collection.LongSparseArray;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.DialogsSearchAdapter;
import org.telegram.ui.EditWidgetActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class MessagesStorage extends BaseController {

    private DispatchQueue storageQueue = new DispatchQueue("storageQueue");
    private SQLiteDatabase database;
    private File cacheFile;
    private File walCacheFile;
    private File shmCacheFile;
    private AtomicLong lastTaskId = new AtomicLong(System.currentTimeMillis());
    private SparseArray<ArrayList<Runnable>> tasks = new SparseArray<>();

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

    private ArrayList<MessagesController.DialogFilter> dialogFilters = new ArrayList<>();
    private SparseArray<MessagesController.DialogFilter> dialogFiltersMap = new SparseArray<>();
    private LongSparseArray<Boolean> unknownDialogsIds = new LongSparseArray<>();
    private int mainUnreadCount;
    private int archiveUnreadCount;
    private volatile int pendingMainUnreadCount;
    private volatile int pendingArchiveUnreadCount;

    private CountDownLatch openSync = new CountDownLatch(1);

    private static volatile MessagesStorage[] Instance = new MessagesStorage[UserConfig.MAX_ACCOUNT_COUNT];
    private final static int LAST_DB_VERSION = 92;
    private boolean databaseMigrationInProgress;
    public boolean showClearDatabaseAlert;


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

    public int getMainUnreadCount() {
        return mainUnreadCount;
    }

    public int getArchiveUnreadCount() {
        return archiveUnreadCount;
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

    @UiThread
    public void bindTaskToGuid(Runnable task, int guid) {
        ArrayList<Runnable> arrayList = tasks.get(guid);
        if (arrayList == null) {
            arrayList = new ArrayList<>();
            tasks.put(guid, arrayList);
        }
        arrayList.add(task);
    }

    @UiThread
    public void cancelTasksForGuid(int guid) {
        ArrayList<Runnable> arrayList = tasks.get(guid);
        if (arrayList == null) {
            return;
        }
        for (int a = 0, N = arrayList.size(); a < N; a++) {
            storageQueue.cancelRunnable(arrayList.get(a));
        }
        tasks.remove(guid);
    }

    @UiThread
    public void completeTaskForGuid(Runnable runnable, int guid) {
        ArrayList<Runnable> arrayList = tasks.get(guid);
        if (arrayList == null) {
            return;
        }
        arrayList.remove(runnable);
        if (arrayList.isEmpty()) {
            tasks.remove(guid);
        }
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

        if (!cacheFile.exists()) {
            createTable = true;
        }
        try {
            database = new SQLiteDatabase(cacheFile.getPath());
            database.executeFast("PRAGMA secure_delete = ON").stepThis().dispose();
            database.executeFast("PRAGMA temp_store = MEMORY").stepThis().dispose();
            database.executeFast("PRAGMA journal_mode = WAL").stepThis().dispose();
            database.executeFast("PRAGMA journal_size_limit = 10485760").stepThis().dispose();

            if (createTable) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("create new database");
                }
                database.executeFast("CREATE TABLE messages_holes(uid INTEGER, start INTEGER, end INTEGER, PRIMARY KEY(uid, start));").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS uid_end_messages_holes ON messages_holes(uid, end);").stepThis().dispose();

                database.executeFast("CREATE TABLE media_holes_v2(uid INTEGER, type INTEGER, start INTEGER, end INTEGER, PRIMARY KEY(uid, type, start));").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS uid_end_media_holes_v2 ON media_holes_v2(uid, type, end);").stepThis().dispose();

                database.executeFast("CREATE TABLE scheduled_messages_v2(mid INTEGER, uid INTEGER, send_state INTEGER, date INTEGER, data BLOB, ttl INTEGER, replydata BLOB, reply_to_message_id INTEGER, PRIMARY KEY(mid, uid))").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS send_state_idx_scheduled_messages_v2 ON scheduled_messages_v2(mid, send_state, date);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS uid_date_idx_scheduled_messages_v2 ON scheduled_messages_v2(uid, date);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS reply_to_idx_scheduled_messages_v2 ON scheduled_messages_v2(mid, reply_to_message_id);").stepThis().dispose();

                database.executeFast("CREATE TABLE messages_v2(mid INTEGER, uid INTEGER, read_state INTEGER, send_state INTEGER, date INTEGER, data BLOB, out INTEGER, ttl INTEGER, media INTEGER, replydata BLOB, imp INTEGER, mention INTEGER, forwards INTEGER, replies_data BLOB, thread_reply_id INTEGER, is_channel INTEGER, reply_to_message_id INTEGER, PRIMARY KEY(mid, uid))").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_read_out_idx_messages_v2 ON messages_v2(uid, mid, read_state, out);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS uid_date_mid_idx_messages_v2 ON messages_v2(uid, date, mid);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS mid_out_idx_messages_v2 ON messages_v2(mid, out);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS task_idx_messages_v2 ON messages_v2(uid, out, read_state, ttl, date, send_state);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS send_state_idx_messages_v2 ON messages_v2(mid, send_state, date);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS uid_mention_idx_messages_v2 ON messages_v2(uid, mention, read_state);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS is_channel_idx_messages_v2 ON messages_v2(mid, is_channel);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS reply_to_idx_messages_v2 ON messages_v2(mid, reply_to_message_id);").stepThis().dispose();

                database.executeFast("CREATE TABLE download_queue(uid INTEGER, type INTEGER, date INTEGER, data BLOB, parent TEXT, PRIMARY KEY (uid, type));").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS type_date_idx_download_queue ON download_queue(type, date);").stepThis().dispose();

                database.executeFast("CREATE TABLE user_contacts_v7(key TEXT PRIMARY KEY, uid INTEGER, fname TEXT, sname TEXT, imported INTEGER)").stepThis().dispose();
                database.executeFast("CREATE TABLE user_phones_v7(key TEXT, phone TEXT, sphone TEXT, deleted INTEGER, PRIMARY KEY (key, phone))").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS sphone_deleted_idx_user_phones ON user_phones_v7(sphone, deleted);").stepThis().dispose();

                database.executeFast("CREATE TABLE dialogs(did INTEGER PRIMARY KEY, date INTEGER, unread_count INTEGER, last_mid INTEGER, inbox_max INTEGER, outbox_max INTEGER, last_mid_i INTEGER, unread_count_i INTEGER, pts INTEGER, date_i INTEGER, pinned INTEGER, flags INTEGER, folder_id INTEGER, data BLOB, unread_reactions INTEGER)").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS date_idx_dialogs ON dialogs(date);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS last_mid_idx_dialogs ON dialogs(last_mid);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS unread_count_idx_dialogs ON dialogs(unread_count);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS last_mid_i_idx_dialogs ON dialogs(last_mid_i);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS unread_count_i_idx_dialogs ON dialogs(unread_count_i);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS folder_id_idx_dialogs ON dialogs(folder_id);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS flags_idx_dialogs ON dialogs(flags);").stepThis().dispose();

                database.executeFast("CREATE TABLE dialog_filter(id INTEGER PRIMARY KEY, ord INTEGER, unread_count INTEGER, flags INTEGER, title TEXT)").stepThis().dispose();
                database.executeFast("CREATE TABLE dialog_filter_ep(id INTEGER, peer INTEGER, PRIMARY KEY (id, peer))").stepThis().dispose();
                database.executeFast("CREATE TABLE dialog_filter_pin_v2(id INTEGER, peer INTEGER, pin INTEGER, PRIMARY KEY (id, peer))").stepThis().dispose();

                database.executeFast("CREATE TABLE randoms_v2(random_id INTEGER, mid INTEGER, uid INTEGER, PRIMARY KEY (random_id, mid, uid))").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS mid_idx_randoms_v2 ON randoms_v2(mid, uid);").stepThis().dispose();

                database.executeFast("CREATE TABLE enc_tasks_v4(mid INTEGER, uid INTEGER, date INTEGER, media INTEGER, PRIMARY KEY(mid, uid, media))").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS date_idx_enc_tasks_v4 ON enc_tasks_v4(date);").stepThis().dispose();

                database.executeFast("CREATE TABLE messages_seq(mid INTEGER PRIMARY KEY, seq_in INTEGER, seq_out INTEGER);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS seq_idx_messages_seq ON messages_seq(seq_in, seq_out);").stepThis().dispose();

                database.executeFast("CREATE TABLE params(id INTEGER PRIMARY KEY, seq INTEGER, pts INTEGER, date INTEGER, qts INTEGER, lsv INTEGER, sg INTEGER, pbytes BLOB)").stepThis().dispose();
                database.executeFast("INSERT INTO params VALUES(1, 0, 0, 0, 0, 0, 0, NULL)").stepThis().dispose();

                database.executeFast("CREATE TABLE media_v4(mid INTEGER, uid INTEGER, date INTEGER, type INTEGER, data BLOB, PRIMARY KEY(mid, uid, type))").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_type_date_idx_media_v4 ON media_v4(uid, mid, type, date);").stepThis().dispose();

                database.executeFast("CREATE TABLE bot_keyboard(uid INTEGER PRIMARY KEY, mid INTEGER, info BLOB)").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS bot_keyboard_idx_mid_v2 ON bot_keyboard(mid, uid);").stepThis().dispose();

                database.executeFast("CREATE TABLE chat_settings_v2(uid INTEGER PRIMARY KEY, info BLOB, pinned INTEGER, online INTEGER, inviter INTEGER, links INTEGER)").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS chat_settings_pinned_idx ON chat_settings_v2(uid, pinned) WHERE pinned != 0;").stepThis().dispose();

                database.executeFast("CREATE TABLE user_settings(uid INTEGER PRIMARY KEY, info BLOB, pinned INTEGER)").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS user_settings_pinned_idx ON user_settings(uid, pinned) WHERE pinned != 0;").stepThis().dispose();

                database.executeFast("CREATE TABLE chat_pinned_v2(uid INTEGER, mid INTEGER, data BLOB, PRIMARY KEY (uid, mid));").stepThis().dispose();
                database.executeFast("CREATE TABLE chat_pinned_count(uid INTEGER PRIMARY KEY, count INTEGER, end INTEGER);").stepThis().dispose();

                database.executeFast("CREATE TABLE chat_hints(did INTEGER, type INTEGER, rating REAL, date INTEGER, PRIMARY KEY(did, type))").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS chat_hints_rating_idx ON chat_hints(rating);").stepThis().dispose();

                database.executeFast("CREATE TABLE botcache(id TEXT PRIMARY KEY, date INTEGER, data BLOB)").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS botcache_date_idx ON botcache(date);").stepThis().dispose();

                database.executeFast("CREATE TABLE users_data(uid INTEGER PRIMARY KEY, about TEXT)").stepThis().dispose();
                database.executeFast("CREATE TABLE users(uid INTEGER PRIMARY KEY, name TEXT, status INTEGER, data BLOB)").stepThis().dispose();
                database.executeFast("CREATE TABLE chats(uid INTEGER PRIMARY KEY, name TEXT, data BLOB)").stepThis().dispose();
                database.executeFast("CREATE TABLE enc_chats(uid INTEGER PRIMARY KEY, user INTEGER, name TEXT, data BLOB, g BLOB, authkey BLOB, ttl INTEGER, layer INTEGER, seq_in INTEGER, seq_out INTEGER, use_count INTEGER, exchange_id INTEGER, key_date INTEGER, fprint INTEGER, fauthkey BLOB, khash BLOB, in_seq_no INTEGER, admin_id INTEGER, mtproto_seq INTEGER)").stepThis().dispose();
                database.executeFast("CREATE TABLE channel_users_v2(did INTEGER, uid INTEGER, date INTEGER, data BLOB, PRIMARY KEY(did, uid))").stepThis().dispose();
                database.executeFast("CREATE TABLE channel_admins_v3(did INTEGER, uid INTEGER, data BLOB, PRIMARY KEY(did, uid))").stepThis().dispose();
                database.executeFast("CREATE TABLE contacts(uid INTEGER PRIMARY KEY, mutual INTEGER)").stepThis().dispose();
                database.executeFast("CREATE TABLE user_photos(uid INTEGER, id INTEGER, data BLOB, PRIMARY KEY (uid, id))").stepThis().dispose();
                database.executeFast("CREATE TABLE dialog_settings(did INTEGER PRIMARY KEY, flags INTEGER);").stepThis().dispose();
                database.executeFast("CREATE TABLE web_recent_v3(id TEXT, type INTEGER, image_url TEXT, thumb_url TEXT, local_url TEXT, width INTEGER, height INTEGER, size INTEGER, date INTEGER, document BLOB, PRIMARY KEY (id, type));").stepThis().dispose();
                database.executeFast("CREATE TABLE stickers_v2(id INTEGER PRIMARY KEY, data BLOB, date INTEGER, hash INTEGER);").stepThis().dispose();
                database.executeFast("CREATE TABLE stickers_featured(id INTEGER PRIMARY KEY, data BLOB, unread BLOB, date INTEGER, hash INTEGER);").stepThis().dispose();
                database.executeFast("CREATE TABLE stickers_dice(emoji TEXT PRIMARY KEY, data BLOB, date INTEGER);").stepThis().dispose();
                database.executeFast("CREATE TABLE hashtag_recent_v2(id TEXT PRIMARY KEY, date INTEGER);").stepThis().dispose();
                database.executeFast("CREATE TABLE webpage_pending_v2(id INTEGER, mid INTEGER, uid INTEGER, PRIMARY KEY (id, mid, uid));").stepThis().dispose();
                database.executeFast("CREATE TABLE sent_files_v2(uid TEXT, type INTEGER, data BLOB, parent TEXT, PRIMARY KEY (uid, type))").stepThis().dispose();
                database.executeFast("CREATE TABLE search_recent(did INTEGER PRIMARY KEY, date INTEGER);").stepThis().dispose();
                database.executeFast("CREATE TABLE media_counts_v2(uid INTEGER, type INTEGER, count INTEGER, old INTEGER, PRIMARY KEY(uid, type))").stepThis().dispose();
                database.executeFast("CREATE TABLE keyvalue(id TEXT PRIMARY KEY, value TEXT)").stepThis().dispose();
                database.executeFast("CREATE TABLE bot_info_v2(uid INTEGER, dialogId INTEGER, info BLOB, PRIMARY KEY(uid, dialogId))").stepThis().dispose();
                database.executeFast("CREATE TABLE pending_tasks(id INTEGER PRIMARY KEY, data BLOB);").stepThis().dispose();
                database.executeFast("CREATE TABLE requested_holes(uid INTEGER, seq_out_start INTEGER, seq_out_end INTEGER, PRIMARY KEY (uid, seq_out_start, seq_out_end));").stepThis().dispose();
                database.executeFast("CREATE TABLE sharing_locations(uid INTEGER PRIMARY KEY, mid INTEGER, date INTEGER, period INTEGER, message BLOB, proximity INTEGER);").stepThis().dispose();

                database.executeFast("CREATE TABLE shortcut_widget(id INTEGER, did INTEGER, ord INTEGER, PRIMARY KEY (id, did));").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS shortcut_widget_did ON shortcut_widget(did);").stepThis().dispose();

                database.executeFast("CREATE TABLE emoji_keywords_v2(lang TEXT, keyword TEXT, emoji TEXT, PRIMARY KEY(lang, keyword, emoji));").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS emoji_keywords_v2_keyword ON emoji_keywords_v2(keyword);").stepThis().dispose();
                database.executeFast("CREATE TABLE emoji_keywords_info_v2(lang TEXT PRIMARY KEY, alias TEXT, version INTEGER, date INTEGER);").stepThis().dispose();

                database.executeFast("CREATE TABLE wallpapers2(uid INTEGER PRIMARY KEY, data BLOB, num INTEGER)").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS wallpapers_num ON wallpapers2(num);").stepThis().dispose();

                database.executeFast("CREATE TABLE unread_push_messages(uid INTEGER, mid INTEGER, random INTEGER, date INTEGER, data BLOB, fm TEXT, name TEXT, uname TEXT, flags INTEGER, PRIMARY KEY(uid, mid))").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS unread_push_messages_idx_date ON unread_push_messages(date);").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS unread_push_messages_idx_random ON unread_push_messages(random);").stepThis().dispose();

                database.executeFast("CREATE TABLE polls_v2(mid INTEGER, uid INTEGER, id INTEGER, PRIMARY KEY (mid, uid));").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS polls_id_v2 ON polls_v2(id);").stepThis().dispose();

                database.executeFast("CREATE TABLE reactions(data BLOB, hash INTEGER, date INTEGER);").stepThis().dispose();
                database.executeFast("CREATE TABLE reaction_mentions(message_id INTEGER, state INTEGER, dialog_id INTEGER, PRIMARY KEY(message_id, dialog_id))").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS reaction_mentions_did ON reaction_mentions(dialog_id);").stepThis().dispose();

                database.executeFast("CREATE TABLE downloading_documents(data BLOB, hash INTEGER, id INTEGER, state INTEGER, date INTEGER, PRIMARY KEY(hash, id));").stepThis().dispose();
                //version
                database.executeFast("PRAGMA user_version = " + LAST_DB_VERSION).stepThis().dispose();
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
                    if (e.getMessage() != null && e.getMessage().contains("malformed")) {
                        throw new RuntimeException("malformed");
                    }
                    FileLog.e(e);
                    try {
                        database.executeFast("CREATE TABLE IF NOT EXISTS params(id INTEGER PRIMARY KEY, seq INTEGER, pts INTEGER, date INTEGER, qts INTEGER, lsv INTEGER, sg INTEGER, pbytes BLOB)").stepThis().dispose();
                        database.executeFast("INSERT INTO params VALUES(1, 0, 0, 0, 0, 0, 0, NULL)").stepThis().dispose();
                    } catch (Exception e2) {
                        FileLog.e(e2);
                    }
                }
                if (version < LAST_DB_VERSION) {
                    try {
                        updateDbToLastVersion(version);
                    } catch (Exception e) {
                        if (BuildVars.DEBUG_PRIVATE_VERSION) {
                            throw e;
                        }
                        FileLog.e(e);
                        throw new RuntimeException("malformed");
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            if (BuildVars.DEBUG_PRIVATE_VERSION) {
                throw new RuntimeException(e);
            }
            if (openTries < 3 && e.getMessage() != null && e.getMessage().contains("malformed")) {
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

        AndroidUtilities.runOnUIThread(() -> {
            if (databaseMigrationInProgress) {
                databaseMigrationInProgress = false;
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.onDatabaseMigration, false);
            }
        });
        loadDialogFilters();
        loadUnreadMessages();
        loadPendingTasks();
        try {
            openSync.countDown();
        } catch (Throwable ignore) {

        }

        AndroidUtilities.runOnUIThread(() -> {
            //TODO add progress view and uncomment
            showClearDatabaseAlert = false;//getDatabaseSize() > 150 * 1024 * 1024;
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.onDatabaseOpened);
        });
    }

    public boolean isDatabaseMigrationInProgress() {
        return databaseMigrationInProgress;
    }

    private void updateDbToLastVersion(int currentVersion) throws Exception {
        AndroidUtilities.runOnUIThread(() -> {
            databaseMigrationInProgress = true;
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.onDatabaseMigration, true);
        });

        int version = currentVersion;
        FileLog.d("MessagesStorage start db migration from " + version + " to " + LAST_DB_VERSION);
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
            database.executeFast("PRAGMA user_version = 18").stepThis().dispose();
            version = 18;
        }
        if (version == 18) {
            database.executeFast("DROP TABLE IF EXISTS stickers;").stepThis().dispose();
            database.executeFast("CREATE TABLE IF NOT EXISTS stickers_v2(id INTEGER PRIMARY KEY, data BLOB, date INTEGER, hash INTEGER);").stepThis().dispose();
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
                long chatId = cursor.intValue(0);
                NativeByteBuffer data = cursor.byteBufferValue(1);
                if (data != null) {
                    TLRPC.ChatParticipants participants = TLRPC.ChatParticipants.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();
                    if (participants != null) {
                        TLRPC.TL_chatFull chatFull = new TLRPC.TL_chatFull();
                        chatFull.id = chatId;
                        chatFull.chat_photo = new TLRPC.TL_photoEmpty();
                        chatFull.notify_settings = new TLRPC.TL_peerNotifySettingsEmpty_layer77();
                        chatFull.exported_invite = null;
                        chatFull.participants = participants;
                        NativeByteBuffer data2 = new NativeByteBuffer(chatFull.getObjectSize());
                        chatFull.serializeToStream(data2);
                        state.requery();
                        state.bindLong(1, chatId);
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
            database.executeFast("CREATE TABLE IF NOT EXISTS stickers_featured(id INTEGER PRIMARY KEY, data BLOB, unread BLOB, date INTEGER, hash INTEGER);").stepThis().dispose();
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
            database.executeFast("CREATE TABLE IF NOT EXISTS polls_v2(mid INTEGER, uid INTEGER, id INTEGER, PRIMARY KEY (mid, uid));").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS polls_id ON polls_v2(id);").stepThis().dispose();
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
            database.executeFast("DELETE FROM download_queue WHERE 1").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 64").stepThis().dispose();
            version = 64;
        }
        if (version == 64) {
            database.executeFast("CREATE TABLE IF NOT EXISTS dialog_filter(id INTEGER PRIMARY KEY, ord INTEGER, unread_count INTEGER, flags INTEGER, title TEXT)").stepThis().dispose();
            database.executeFast("CREATE TABLE IF NOT EXISTS dialog_filter_ep(id INTEGER, peer INTEGER, PRIMARY KEY (id, peer))").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 65").stepThis().dispose();
            version = 65;
        }
        if (version == 65) {
            database.executeFast("CREATE INDEX IF NOT EXISTS flags_idx_dialogs ON dialogs(flags);").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 66").stepThis().dispose();
            version = 66;
        }
        if (version == 66) {
            database.executeFast("CREATE TABLE dialog_filter_pin_v2(id INTEGER, peer INTEGER, pin INTEGER, PRIMARY KEY (id, peer))").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 67").stepThis().dispose();
            version = 67;
        }
        if (version == 67) {
            database.executeFast("CREATE TABLE IF NOT EXISTS stickers_dice(emoji TEXT PRIMARY KEY, data BLOB, date INTEGER);").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 68").stepThis().dispose();
            version = 68;
        }
        if (version == 68) {
            executeNoException("ALTER TABLE messages ADD COLUMN forwards INTEGER default 0");
            database.executeFast("PRAGMA user_version = 69").stepThis().dispose();
            version = 69;
        }
        if (version == 69) {
            executeNoException("ALTER TABLE messages ADD COLUMN replies_data BLOB default NULL");
            executeNoException("ALTER TABLE messages ADD COLUMN thread_reply_id INTEGER default 0");
            database.executeFast("PRAGMA user_version = 70").stepThis().dispose();
            version = 70;
        }
        if (version == 70) {
            database.executeFast("CREATE TABLE IF NOT EXISTS chat_pinned_v2(uid INTEGER, mid INTEGER, data BLOB, PRIMARY KEY (uid, mid));").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 71").stepThis().dispose();
            version = 71;
        }
        if (version == 71) {
            executeNoException("ALTER TABLE sharing_locations ADD COLUMN proximity INTEGER default 0");
            database.executeFast("PRAGMA user_version = 72").stepThis().dispose();
            version = 72;
        }
        if (version == 72) {
            database.executeFast("CREATE TABLE IF NOT EXISTS chat_pinned_count(uid INTEGER PRIMARY KEY, count INTEGER, end INTEGER);").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 73").stepThis().dispose();
            version = 73;
        }
        if (version == 73) {
            executeNoException("ALTER TABLE chat_settings_v2 ADD COLUMN inviter INTEGER default 0");
            database.executeFast("PRAGMA user_version = 74").stepThis().dispose();
            version = 74;
        }
        if (version == 74) {
            database.executeFast("CREATE TABLE IF NOT EXISTS shortcut_widget(id INTEGER, did INTEGER, ord INTEGER, PRIMARY KEY (id, did));").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS shortcut_widget_did ON shortcut_widget(did);").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 75").stepThis().dispose();
            version = 75;
        }
        if (version == 75) {
            executeNoException("ALTER TABLE chat_settings_v2 ADD COLUMN links INTEGER default 0");
            database.executeFast("PRAGMA user_version = 76").stepThis().dispose();
            version = 76;
        }
        if (version == 76) {
            executeNoException("ALTER TABLE enc_tasks_v2 ADD COLUMN media INTEGER default -1");
            database.executeFast("PRAGMA user_version = 77").stepThis().dispose();
            version = 77;
        }
        if (version == 77) {
            database.executeFast("DROP TABLE IF EXISTS channel_admins_v2;").stepThis().dispose();
            database.executeFast("CREATE TABLE IF NOT EXISTS channel_admins_v3(did INTEGER, uid INTEGER, data BLOB, PRIMARY KEY(did, uid))").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 78").stepThis().dispose();
            version = 78;
        }
        if (version == 78) {
            database.executeFast("DROP TABLE IF EXISTS bot_info;").stepThis().dispose();
            database.executeFast("CREATE TABLE IF NOT EXISTS bot_info_v2(uid INTEGER, dialogId INTEGER, info BLOB, PRIMARY KEY(uid, dialogId))").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 79").stepThis().dispose();
            version = 79;
        }
        if (version == 79) {
            database.executeFast("CREATE TABLE IF NOT EXISTS enc_tasks_v3(mid INTEGER, date INTEGER, media INTEGER, PRIMARY KEY(mid, media))").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS date_idx_enc_tasks_v3 ON enc_tasks_v3(date);").stepThis().dispose();

            database.beginTransaction();
            SQLiteCursor cursor = database.queryFinalized("SELECT mid, date, media FROM enc_tasks_v2 WHERE 1");
            SQLitePreparedStatement state = database.executeFast("REPLACE INTO enc_tasks_v3 VALUES(?, ?, ?)");
            if (cursor.next()) {
                long mid = cursor.longValue(0);
                int date = cursor.intValue(1);
                int media = cursor.intValue(2);

                state.requery();
                state.bindLong(1, mid);
                state.bindInteger(2, date);
                state.bindInteger(3, media);
                state.step();
            }
            state.dispose();
            cursor.dispose();
            database.commitTransaction();

            database.executeFast("DROP INDEX IF EXISTS date_idx_enc_tasks_v2;").stepThis().dispose();
            database.executeFast("DROP TABLE IF EXISTS enc_tasks_v2;").stepThis().dispose();

            database.executeFast("PRAGMA user_version = 80").stepThis().dispose();
            version = 80;
        }
        if (version == 80) {
            database.executeFast("CREATE TABLE IF NOT EXISTS scheduled_messages_v2(mid INTEGER, uid INTEGER, send_state INTEGER, date INTEGER, data BLOB, ttl INTEGER, replydata BLOB, PRIMARY KEY(mid, uid))").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS send_state_idx_scheduled_messages_v2 ON scheduled_messages_v2(mid, send_state, date);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS uid_date_idx_scheduled_messages_v2 ON scheduled_messages_v2(uid, date);").stepThis().dispose();

            database.executeFast("CREATE INDEX IF NOT EXISTS bot_keyboard_idx_mid_v2 ON bot_keyboard(mid, uid);").stepThis().dispose();
            database.executeFast("DROP INDEX IF EXISTS bot_keyboard_idx_mid;").stepThis().dispose();

            database.beginTransaction();
            SQLiteCursor cursor;
            try {
                cursor = database.queryFinalized("SELECT mid, uid, send_state, date, data, ttl, replydata FROM scheduled_messages_v2 WHERE 1");
            } catch (Exception e) {
                cursor = null;
                FileLog.e(e);
            }
            if (cursor != null) {
                SQLitePreparedStatement statement = database.executeFast("REPLACE INTO scheduled_messages_v2 VALUES(?, ?, ?, ?, ?, ?, ?)");
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(4);
                    if (data == null) {
                        continue;
                    }
                    int mid = cursor.intValue(0);
                    long uid = cursor.longValue(1);
                    int sendState = cursor.intValue(2);
                    int date = cursor.intValue(3);
                    int ttl = cursor.intValue(5);
                    NativeByteBuffer replydata = cursor.byteBufferValue(6);

                    statement.requery();
                    statement.bindInteger(1, mid);
                    statement.bindLong(2, uid);
                    statement.bindInteger(3, sendState);
                    statement.bindByteBuffer(4, data);
                    statement.bindInteger(5, date);
                    statement.bindInteger(6, ttl);
                    if (replydata != null) {
                        statement.bindByteBuffer(7, replydata);
                    } else {
                        statement.bindNull(7);
                    }
                    statement.step();
                    if (replydata != null) {
                        replydata.reuse();
                    }
                    data.reuse();
                }
                cursor.dispose();
                statement.dispose();
            }

            database.executeFast("DROP INDEX IF EXISTS send_state_idx_scheduled_messages;").stepThis().dispose();
            database.executeFast("DROP INDEX IF EXISTS uid_date_idx_scheduled_messages;").stepThis().dispose();
            database.executeFast("DROP TABLE IF EXISTS scheduled_messages;").stepThis().dispose();

            database.commitTransaction();
            database.executeFast("PRAGMA user_version = 81").stepThis().dispose();
            version = 81;
        }
        if (version == 81) {
            database.executeFast("CREATE TABLE IF NOT EXISTS media_v3(mid INTEGER, uid INTEGER, date INTEGER, type INTEGER, data BLOB, PRIMARY KEY(mid, uid))").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_type_date_idx_media_v3 ON media_v3(uid, mid, type, date);").stepThis().dispose();

            database.beginTransaction();
            SQLiteCursor cursor;
            try {
                cursor = database.queryFinalized("SELECT mid, uid, date, type, data FROM media_v2 WHERE 1");
            } catch (Exception e) {
                cursor = null;
                FileLog.e(e);
            }
            if (cursor != null) {
                SQLitePreparedStatement statement = database.executeFast("REPLACE INTO media_v3 VALUES(?, ?, ?, ?, ?)");
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(4);
                    if (data == null) {
                        continue;
                    }
                    int mid = cursor.intValue(0);
                    long uid = cursor.longValue(1);
                    int lowerId = (int) uid;
                    if (lowerId == 0) {
                        int highId = (int) (uid >> 32);
                        uid = DialogObject.makeEncryptedDialogId(highId);
                    }
                    int date = cursor.intValue(2);
                    int type = cursor.intValue(3);

                    statement.requery();
                    statement.bindInteger(1, mid);
                    statement.bindLong(2, uid);
                    statement.bindInteger(3, date);
                    statement.bindInteger(4, type);
                    statement.bindByteBuffer(5, data);
                    statement.step();
                    data.reuse();
                }
                cursor.dispose();
                statement.dispose();
            }

            database.executeFast("DROP INDEX IF EXISTS uid_mid_type_date_idx_media;").stepThis().dispose();
            database.executeFast("DROP TABLE IF EXISTS media_v2;").stepThis().dispose();
            database.commitTransaction();

            database.executeFast("PRAGMA user_version = 82").stepThis().dispose();
            version = 82;
        }
        if (version == 82) {
            database.executeFast("CREATE TABLE IF NOT EXISTS randoms_v2(random_id INTEGER, mid INTEGER, uid INTEGER, PRIMARY KEY (random_id, mid, uid))").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS mid_idx_randoms_v2 ON randoms_v2(mid, uid);").stepThis().dispose();

            database.executeFast("CREATE TABLE IF NOT EXISTS enc_tasks_v4(mid INTEGER, uid INTEGER, date INTEGER, media INTEGER, PRIMARY KEY(mid, uid, media))").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS date_idx_enc_tasks_v4 ON enc_tasks_v4(date);").stepThis().dispose();

            database.executeFast("CREATE TABLE IF NOT EXISTS polls_v2(mid INTEGER, uid INTEGER, id INTEGER, PRIMARY KEY (mid, uid));").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS polls_id_v2 ON polls_v2(id);").stepThis().dispose();

            database.executeFast("CREATE TABLE IF NOT EXISTS webpage_pending_v2(id INTEGER, mid INTEGER, uid INTEGER, PRIMARY KEY (id, mid, uid));").stepThis().dispose();

            database.beginTransaction();

            SQLiteCursor cursor;
            try {
                cursor = database.queryFinalized("SELECT r.random_id, r.mid, m.uid FROM randoms as r INNER JOIN messages as m ON r.mid = m.mid WHERE 1");
            } catch (Exception e) {
                cursor = null;
                FileLog.e(e);
            }
            if (cursor != null) {
                SQLitePreparedStatement statement = database.executeFast("REPLACE INTO randoms_v2 VALUES(?, ?, ?)");
                while (cursor.next()) {
                    long randomId = cursor.longValue(0);
                    int mid = cursor.intValue(1);
                    long uid = cursor.longValue(2);
                    int lowerId = (int) uid;
                    if (lowerId == 0) {
                        int highId = (int) (uid >> 32);
                        uid = DialogObject.makeEncryptedDialogId(highId);
                    }

                    statement.requery();
                    statement.bindLong(1, randomId);
                    statement.bindInteger(2, mid);
                    statement.bindLong(3, uid);
                    statement.step();
                }
                cursor.dispose();
                statement.dispose();
            }

            try {
                cursor = database.queryFinalized("SELECT p.mid, m.uid, p.id FROM polls as p INNER JOIN messages as m ON p.mid = m.mid WHERE 1");
            } catch (Exception e) {
                cursor = null;
                FileLog.e(e);
            }
            if (cursor != null) {
                SQLitePreparedStatement statement = database.executeFast("REPLACE INTO polls_v2 VALUES(?, ?, ?)");
                while (cursor.next()) {
                    int mid = cursor.intValue(0);
                    long uid = cursor.longValue(1);
                    long id = cursor.longValue(2);
                    int lowerId = (int) uid;
                    if (lowerId == 0) {
                        int highId = (int) (uid >> 32);
                        uid = DialogObject.makeEncryptedDialogId(highId);
                    }

                    statement.requery();
                    statement.bindInteger(1, mid);
                    statement.bindLong(2, uid);
                    statement.bindLong(3, id);
                    statement.step();
                }
                cursor.dispose();
                statement.dispose();
            }

            try {
                cursor = database.queryFinalized("SELECT wp.id, wp.mid, m.uid FROM webpage_pending as wp INNER JOIN messages as m ON wp.mid = m.mid WHERE 1");
            } catch (Exception e) {
                cursor = null;
                FileLog.e(e);
            }
            if (cursor != null) {
                SQLitePreparedStatement statement = database.executeFast("REPLACE INTO webpage_pending_v2 VALUES(?, ?, ?)");
                while (cursor.next()) {
                    long id = cursor.longValue(0);
                    int mid = cursor.intValue(1);
                    long uid = cursor.longValue(2);
                    int lowerId = (int) uid;
                    if (lowerId == 0) {
                        int highId = (int) (uid >> 32);
                        uid = DialogObject.makeEncryptedDialogId(highId);
                    }

                    statement.requery();
                    statement.bindLong(1, id);
                    statement.bindInteger(2, mid);
                    statement.bindLong(3, uid);
                    statement.step();
                }
                cursor.dispose();
                statement.dispose();
            }

            try {
                cursor = database.queryFinalized("SELECT et.mid, m.uid, et.date, et.media FROM enc_tasks_v3 as et INNER JOIN messages as m ON et.mid = m.mid WHERE 1");
            } catch (Exception e) {
                cursor = null;
                FileLog.e(e);
            }
            if (cursor != null) {
                SQLitePreparedStatement statement = database.executeFast("REPLACE INTO enc_tasks_v4 VALUES(?, ?, ?, ?)");
                while (cursor.next()) {
                    int mid = cursor.intValue(0);
                    long uid = cursor.longValue(1);
                    int date = cursor.intValue(2);
                    int media = cursor.intValue(3);

                    int lowerId = (int) uid;
                    if (lowerId == 0) {
                        int highId = (int) (uid >> 32);
                        uid = DialogObject.makeEncryptedDialogId(highId);
                    }

                    statement.requery();
                    statement.bindInteger(1, mid);
                    statement.bindLong(2, uid);
                    statement.bindInteger(3, date);
                    statement.bindInteger(4, media);
                    statement.step();
                }
                cursor.dispose();
                statement.dispose();
            }

            database.executeFast("DROP INDEX IF EXISTS mid_idx_randoms;").stepThis().dispose();
            database.executeFast("DROP TABLE IF EXISTS randoms;").stepThis().dispose();

            database.executeFast("DROP INDEX IF EXISTS date_idx_enc_tasks_v3;").stepThis().dispose();
            database.executeFast("DROP TABLE IF EXISTS enc_tasks_v3;").stepThis().dispose();

            database.executeFast("DROP INDEX IF EXISTS polls_id;").stepThis().dispose();
            database.executeFast("DROP TABLE IF EXISTS polls;").stepThis().dispose();

            database.executeFast("DROP TABLE IF EXISTS webpage_pending;").stepThis().dispose();
            database.commitTransaction();

            database.executeFast("PRAGMA user_version = 83").stepThis().dispose();
            version = 83;
        }
        if (version == 83) {
            database.executeFast("CREATE TABLE IF NOT EXISTS messages_v2(mid INTEGER, uid INTEGER, read_state INTEGER, send_state INTEGER, date INTEGER, data BLOB, out INTEGER, ttl INTEGER, media INTEGER, replydata BLOB, imp INTEGER, mention INTEGER, forwards INTEGER, replies_data BLOB, thread_reply_id INTEGER, is_channel INTEGER, PRIMARY KEY(mid, uid))").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_read_out_idx_messages_v2 ON messages_v2(uid, mid, read_state, out);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS uid_date_mid_idx_messages_v2 ON messages_v2(uid, date, mid);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS mid_out_idx_messages_v2 ON messages_v2(mid, out);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS task_idx_messages_v2 ON messages_v2(uid, out, read_state, ttl, date, send_state);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS send_state_idx_messages_v2 ON messages_v2(mid, send_state, date);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS uid_mention_idx_messages_v2 ON messages_v2(uid, mention, read_state);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS is_channel_idx_messages_v2 ON messages_v2(mid, is_channel);").stepThis().dispose();

            database.beginTransaction();

            SQLiteCursor cursor;

            try {
                cursor = database.queryFinalized("SELECT mid, uid, read_state, send_state, date, data, out, ttl, media, replydata, imp, mention, forwards, replies_data, thread_reply_id FROM messages WHERE 1");
            } catch (Exception e) {
                cursor = null;
                FileLog.e(e);
            }
            if (cursor != null) {
                SQLitePreparedStatement statement = database.executeFast("REPLACE INTO messages_v2 VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                int num = 0;
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(5);
                    if (data == null) {
                        continue;
                    }
                    num++;
                    long mid = cursor.intValue(0);
                    long uid = cursor.longValue(1);
                    int lowerId = (int) uid;
                    if (lowerId == 0) {
                        int highId = (int) (uid >> 32);
                        uid = DialogObject.makeEncryptedDialogId(highId);
                    }
                    int readState = cursor.intValue(2);
                    int sendState = cursor.intValue(3);
                    int date = cursor.intValue(4);
                    int out = cursor.intValue(6);
                    int ttl = cursor.intValue(7);
                    int media = cursor.intValue(8);
                    NativeByteBuffer replydata = cursor.byteBufferValue(9);
                    int imp = cursor.intValue(10);
                    int mention = cursor.intValue(11);
                    int forwards = cursor.intValue(12);
                    NativeByteBuffer repliesdata = cursor.byteBufferValue(13);
                    int thread_reply_id = cursor.intValue(14);
                    int channelId = (int) (uid >> 32);
                    if (ttl < 0) {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        if (message != null) {
                            message.readAttachPath(data, getUserConfig().clientUserId);
                            if (message.params == null) {
                                message.params = new HashMap<>();
                                message.params.put("fwd_peer", "" + ttl);
                            }
                            data.reuse();
                            data = new NativeByteBuffer(message.getObjectSize());
                            message.serializeToStream(data);
                        }
                        ttl = 0;
                    }

                    statement.requery();
                    statement.bindInteger(1, (int) mid);
                    statement.bindLong(2, uid);
                    statement.bindInteger(3, readState);
                    statement.bindInteger(4, sendState);
                    statement.bindInteger(5, date);
                    statement.bindByteBuffer(6, data);
                    statement.bindInteger(7, out);
                    statement.bindInteger(8, ttl);
                    statement.bindInteger(9, media);
                    if (replydata != null) {
                        statement.bindByteBuffer(10, replydata);
                    } else {
                        statement.bindNull(10);
                    }
                    statement.bindInteger(11, imp);
                    statement.bindInteger(12, mention);
                    statement.bindInteger(13, forwards);
                    if (repliesdata != null) {
                        statement.bindByteBuffer(14, repliesdata);
                    } else {
                        statement.bindNull(14);
                    }
                    statement.bindInteger(15, thread_reply_id);
                    statement.bindInteger(16, channelId > 0 ? 1 : 0);
                    statement.step();
                    if (replydata != null) {
                        replydata.reuse();
                    }
                    if (repliesdata != null) {
                        repliesdata.reuse();
                    }
                    data.reuse();
                }
                cursor.dispose();
                statement.dispose();
            }

            ArrayList<Integer> secretChatsToUpdate = null;
            ArrayList<Integer> foldersToUpdate = null;
            cursor = database.queryFinalized("SELECT did, last_mid, last_mid_i FROM dialogs WHERE 1");
            SQLitePreparedStatement statement4 = database.executeFast("UPDATE dialogs SET last_mid = ?, last_mid_i = ? WHERE did = ?");
            while (cursor.next()) {
                long did = cursor.longValue(0);
                int lowerId = (int) did;
                int highId = (int) (did >> 32);
                if (lowerId == 0) {
                    if (secretChatsToUpdate == null) {
                        secretChatsToUpdate = new ArrayList<>();
                    }
                    secretChatsToUpdate.add(highId);
                } else if (highId == 2) {
                    if (foldersToUpdate == null) {
                        foldersToUpdate = new ArrayList<>();
                    }
                    foldersToUpdate.add(lowerId);
                }

                statement4.requery();
                statement4.bindInteger(1, cursor.intValue(1));
                statement4.bindInteger(2, cursor.intValue(2));
                statement4.bindLong(3, did);
                statement4.step();
            }
            statement4.dispose();
            cursor.dispose();

            cursor = database.queryFinalized("SELECT uid, mid FROM unread_push_messages WHERE 1");
            statement4 = database.executeFast("UPDATE unread_push_messages SET mid = ? WHERE uid = ? AND mid = ?");
            while (cursor.next()) {
                long did = cursor.longValue(0);
                int mid = cursor.intValue(1);
                statement4.requery();
                statement4.bindInteger(1, mid);
                statement4.bindLong(2, did);
                statement4.bindInteger(3, mid);
                statement4.step();
            }
            statement4.dispose();
            cursor.dispose();

            if (secretChatsToUpdate != null) {
                SQLitePreparedStatement statement = database.executeFast("UPDATE dialogs SET did = ? WHERE did = ?");
                SQLitePreparedStatement statement2 = database.executeFast("UPDATE dialog_filter_pin_v2 SET peer = ? WHERE peer = ?");
                SQLitePreparedStatement statement3 = database.executeFast("UPDATE dialog_filter_ep SET peer = ? WHERE peer = ?");
                for (int a = 0, N = secretChatsToUpdate.size(); a < N; a++) {
                    int sid = secretChatsToUpdate.get(a);

                    long newId = DialogObject.makeEncryptedDialogId(sid);
                    long oldId = ((long) sid) << 32;
                    statement.requery();
                    statement.bindLong(1, newId);
                    statement.bindLong(2, oldId);
                    statement.step();

                    statement2.requery();
                    statement2.bindLong(1, newId);
                    statement2.bindLong(2, oldId);
                    statement2.step();

                    statement3.requery();
                    statement3.bindLong(1, newId);
                    statement3.bindLong(2, oldId);
                    statement3.step();
                }
                statement.dispose();
                statement2.dispose();
                statement3.dispose();
            }
            if (foldersToUpdate != null) {
                SQLitePreparedStatement statement = database.executeFast("UPDATE dialogs SET did = ? WHERE did = ?");
                for (int a = 0, N = foldersToUpdate.size(); a < N; a++) {
                    int fid = foldersToUpdate.get(a);

                    long newId = DialogObject.makeFolderDialogId(fid);
                    long oldId = (((long) 2) << 32) | fid;
                    statement.requery();
                    statement.bindLong(1, newId);
                    statement.bindLong(2, oldId);
                    statement.step();
                }
                statement.dispose();
            }

            database.executeFast("DROP INDEX IF EXISTS uid_mid_read_out_idx_messages;").stepThis().dispose();
            database.executeFast("DROP INDEX IF EXISTS uid_date_mid_idx_messages;").stepThis().dispose();
            database.executeFast("DROP INDEX IF EXISTS mid_out_idx_messages;").stepThis().dispose();
            database.executeFast("DROP INDEX IF EXISTS task_idx_messages;").stepThis().dispose();
            database.executeFast("DROP INDEX IF EXISTS send_state_idx_messages2;").stepThis().dispose();
            database.executeFast("DROP INDEX IF EXISTS uid_mention_idx_messages;").stepThis().dispose();
            database.executeFast("DROP TABLE IF EXISTS messages;").stepThis().dispose();
            database.commitTransaction();

            database.executeFast("PRAGMA user_version = 84").stepThis().dispose();
            version = 84;
        }
        if (version == 84) {
            database.executeFast("CREATE TABLE IF NOT EXISTS media_v4(mid INTEGER, uid INTEGER, date INTEGER, type INTEGER, data BLOB, PRIMARY KEY(mid, uid, type))").stepThis().dispose();
            database.beginTransaction();
            SQLiteCursor cursor;
            try {
                cursor = database.queryFinalized("SELECT mid, uid, date, type, data FROM media_v3 WHERE 1");
            } catch (Exception e) {
                cursor = null;
                FileLog.e(e);
            }
            if (cursor != null) {
                SQLitePreparedStatement statement = database.executeFast("REPLACE INTO media_v4 VALUES(?, ?, ?, ?, ?)");
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(4);
                    if (data == null) {
                        continue;
                    }
                    int mid = cursor.intValue(0);
                    long uid = cursor.longValue(1);
                    int lowerId = (int) uid;
                    if (lowerId == 0) {
                        int highId = (int) (uid >> 32);
                        uid = DialogObject.makeEncryptedDialogId(highId);
                    }
                    int date = cursor.intValue(2);
                    int type = cursor.intValue(3);

                    statement.requery();
                    statement.bindInteger(1, mid);
                    statement.bindLong(2, uid);
                    statement.bindInteger(3, date);
                    statement.bindInteger(4, type);
                    statement.bindByteBuffer(5, data);
                    statement.step();
                    data.reuse();
                }
                cursor.dispose();
                statement.dispose();
            }
            database.commitTransaction();

            database.executeFast("DROP TABLE IF EXISTS media_v3;").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 85").stepThis().dispose();
            version = 85;
        }
        if (version == 85) {
            executeNoException("ALTER TABLE messages_v2 ADD COLUMN reply_to_message_id INTEGER default 0");
            executeNoException("ALTER TABLE scheduled_messages_v2 ADD COLUMN reply_to_message_id INTEGER default 0");

            database.executeFast("CREATE INDEX IF NOT EXISTS reply_to_idx_messages_v2 ON messages_v2(mid, reply_to_message_id);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS reply_to_idx_scheduled_messages_v2 ON scheduled_messages_v2(mid, reply_to_message_id);").stepThis().dispose();

            executeNoException("UPDATE messages_v2 SET replydata = NULL");
            executeNoException("UPDATE scheduled_messages_v2 SET replydata = NULL");
            database.executeFast("PRAGMA user_version = 86").stepThis().dispose();
            version = 86;
        }

        if (version == 86) {
            database.executeFast("CREATE TABLE IF NOT EXISTS reactions(data BLOB, hash INTEGER, date INTEGER);").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 87").stepThis().dispose();
            version = 87;
        }

        if (version == 87) {
            database.executeFast("ALTER TABLE dialogs ADD COLUMN unread_reactions INTEGER default 0").stepThis().dispose();
            database.executeFast("CREATE TABLE reaction_mentions(message_id INTEGER PRIMARY KEY, state INTEGER);").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 88").stepThis().dispose();
            version = 88;
        }

        if (version == 88 || version == 89) {
            database.executeFast("DROP TABLE IF EXISTS reaction_mentions;").stepThis().dispose();
            database.executeFast("CREATE TABLE IF NOT EXISTS reaction_mentions(message_id INTEGER, state INTEGER, dialog_id INTEGER, PRIMARY KEY(dialog_id, message_id));").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS reaction_mentions_did ON reaction_mentions(dialog_id);").stepThis().dispose();

            database.executeFast("DROP INDEX IF EXISTS uid_mid_type_date_idx_media_v3").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_type_date_idx_media_v4 ON media_v4(uid, mid, type, date);").stepThis().dispose();

            database.executeFast("PRAGMA user_version = 90").stepThis().dispose();

            version = 90;
        }

        if (version == 90 || version == 91) {
            database.executeFast("DROP TABLE IF EXISTS downloading_documents;").stepThis().dispose();
            database.executeFast("CREATE TABLE downloading_documents(data BLOB, hash INTEGER, id INTEGER, state INTEGER, date INTEGER, PRIMARY KEY(hash, id));").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 92").stepThis().dispose();
            version = 92;
        }

        FileLog.d("MessagesStorage db migration finished");
        AndroidUtilities.runOnUIThread(() -> {
            databaseMigrationInProgress = false;
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.onDatabaseMigration, false);
        });
    }

    private void executeNoException(String query) {
        try {
            database.executeFast(query).stepThis().dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void cleanupInternal(boolean deleteFiles) {
        lastDateValue = 0;
        lastSeqValue = 0;
        lastPtsValue = 0;
        lastQtsValue = 0;
        lastSecretVersion = 0;
        mainUnreadCount = 0;
        archiveUnreadCount = 0;
        pendingMainUnreadCount = 0;
        pendingArchiveUnreadCount = 0;
        dialogFilters.clear();
        dialogFiltersMap.clear();
        unknownDialogsIds.clear();

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

    public void cleanup(boolean isLogin) {
        storageQueue.postRunnable(() -> {
            cleanupInternal(true);
            openDatabase(1);
            if (isLogin) {
                Utilities.stageQueue.postRunnable(() -> getMessagesController().getDifference());
            }
        });
    }

    public void saveSecretParams(int lsv, int sg, byte[] pbytes) {
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

    public long createPendingTask(NativeByteBuffer data) {
        if (data == null) {
            return 0;
        }
        long id = lastTaskId.getAndAdd(1);
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

    public void removePendingTask(long id) {
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
                    long taskId = cursor.longValue(0);
                    NativeByteBuffer data = cursor.byteBufferValue(1);
                    if (data != null) {
                        int type = data.readInt32(false);
                        switch (type) {
                            case 0: {
                                TLRPC.Chat chat = TLRPC.Chat.TLdeserialize(data, data.readInt32(false), false);
                                if (chat != null) {
                                    Utilities.stageQueue.postRunnable(() -> getMessagesController().loadUnknownChannel(chat, taskId));
                                }
                                break;
                            }
                            case 1: {
                                long channelId = data.readInt32(false);
                                int newDialogType = data.readInt32(false);
                                Utilities.stageQueue.postRunnable(() -> getMessagesController().getChannelDifference(channelId, newDialogType, taskId, null));
                                break;
                            }
                            case 2:
                            case 5:
                            case 8:
                            case 10:
                            case 14: {
                                TLRPC.Dialog dialog = new TLRPC.TL_dialog();
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
                                TLRPC.InputPeer peer = TLRPC.InputPeer.TLdeserialize(data, data.readInt32(false), false);
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
                                long did = data.readInt64(false);
                                boolean pin = data.readBool(false);
                                TLRPC.InputPeer peer = TLRPC.InputPeer.TLdeserialize(data, data.readInt32(false), false);
                                AndroidUtilities.runOnUIThread(() -> getMessagesController().pinDialog(did, pin, peer, taskId));
                                break;
                            }
                            case 6: {
                                long channelId = data.readInt32(false);
                                int newDialogType = data.readInt32(false);
                                TLRPC.InputChannel inputChannel = TLRPC.InputChannel.TLdeserialize(data, data.readInt32(false), false);
                                Utilities.stageQueue.postRunnable(() -> getMessagesController().getChannelDifference(channelId, newDialogType, taskId, inputChannel));
                                break;
                            }
                            case 25: {
                                long channelId = data.readInt64(false);
                                int newDialogType = data.readInt32(false);
                                TLRPC.InputChannel inputChannel = TLRPC.InputChannel.TLdeserialize(data, data.readInt32(false), false);
                                Utilities.stageQueue.postRunnable(() -> getMessagesController().getChannelDifference(channelId, newDialogType, taskId, inputChannel));
                                break;
                            }
                            case 7: {
                                long channelId = data.readInt32(false);
                                int constructor = data.readInt32(false);
                                TLObject request = TLRPC.TL_messages_deleteMessages.TLdeserialize(data, constructor, false);
                                if (request == null) {
                                    request = TLRPC.TL_channels_deleteMessages.TLdeserialize(data, constructor, false);
                                }
                                if (request == null) {
                                    removePendingTask(taskId);
                                } else {
                                    TLObject finalRequest = request;
                                    AndroidUtilities.runOnUIThread(() -> getMessagesController().deleteMessages(null, null, null, -channelId, true, false, false, taskId, finalRequest));
                                }
                                break;
                            }
                            case 24: {
                                long dialogId = data.readInt64(false);
                                int constructor = data.readInt32(false);
                                TLObject request = TLRPC.TL_messages_deleteMessages.TLdeserialize(data, constructor, false);
                                if (request == null) {
                                    request = TLRPC.TL_channels_deleteMessages.TLdeserialize(data, constructor, false);
                                }
                                if (request == null) {
                                    removePendingTask(taskId);
                                } else {
                                    TLObject finalRequest = request;
                                    AndroidUtilities.runOnUIThread(() -> getMessagesController().deleteMessages(null, null, null, dialogId, true, false, false, taskId, finalRequest));
                                }
                                break;
                            }
                            case 9: {
                                long did = data.readInt64(false);
                                TLRPC.InputPeer peer = TLRPC.InputPeer.TLdeserialize(data, data.readInt32(false), false);
                                AndroidUtilities.runOnUIThread(() -> getMessagesController().markDialogAsUnread(did, peer, taskId));
                                break;
                            }
                            case 11: {
                                TLRPC.InputChannel inputChannel;
                                int mid = data.readInt32(false);
                                long channelId = data.readInt32(false);
                                int ttl = data.readInt32(false);
                                if (channelId != 0) {
                                    inputChannel = TLRPC.InputChannel.TLdeserialize(data, data.readInt32(false), false);
                                } else {
                                    inputChannel = null;
                                }
                                AndroidUtilities.runOnUIThread(() -> getMessagesController().markMessageAsRead2(-channelId, mid, inputChannel, ttl, taskId));
                                break;
                            }
                            case 23: {
                                TLRPC.InputChannel inputChannel;
                                long dialogId = data.readInt64(false);
                                int mid = data.readInt32(false);
                                int ttl = data.readInt32(false);
                                if (!DialogObject.isEncryptedDialog(dialogId) && DialogObject.isChatDialog(dialogId) && data.hasRemaining()) {
                                    inputChannel = TLRPC.InputChannel.TLdeserialize(data, data.readInt32(false), false);
                                } else {
                                    inputChannel = null;
                                }
                                AndroidUtilities.runOnUIThread(() -> getMessagesController().markMessageAsRead2(dialogId, mid, inputChannel, ttl, taskId));
                                break;
                            }
                            case 12:
                            case 19:
                            case 20:
                                removePendingTask(taskId);
                                break;
                            case 21: {
                                Theme.OverrideWallpaperInfo info = new Theme.OverrideWallpaperInfo();
                                long id = data.readInt64(false);
                                info.isBlurred = data.readBool(false);
                                info.isMotion = data.readBool(false);
                                info.color = data.readInt32(false);
                                info.gradientColor1 = data.readInt32(false);
                                info.rotation = data.readInt32(false);
                                info.intensity = (float) data.readDouble(false);
                                boolean install = data.readBool(false);
                                info.slug = data.readString(false);
                                info.originalFileName = data.readString(false);
                                AndroidUtilities.runOnUIThread(() -> getMessagesController().saveWallpaperToServer(null, info, install, taskId));
                                break;
                            }
                            case 13: {
                                long did = data.readInt64(false);
                                boolean first = data.readBool(false);
                                int onlyHistory = data.readInt32(false);
                                int maxIdDelete = data.readInt32(false);
                                boolean revoke = data.readBool(false);
                                TLRPC.InputPeer inputPeer = TLRPC.InputPeer.TLdeserialize(data, data.readInt32(false), false);
                                AndroidUtilities.runOnUIThread(() -> getMessagesController().deleteDialog(did, first ? 1 : 0, onlyHistory, maxIdDelete, revoke, inputPeer, taskId));
                                break;
                            }
                            case 15: {
                                TLRPC.InputPeer inputPeer = TLRPC.InputPeer.TLdeserialize(data, data.readInt32(false), false);
                                Utilities.stageQueue.postRunnable(() -> getMessagesController().loadUnknownDialog(inputPeer, taskId));
                                break;
                            }
                            case 16: {
                                int folderId = data.readInt32(false);
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
                                int folderId = data.readInt32(false);
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
                                long dialogId = data.readInt64(false);
                                data.readInt32(false);
                                int constructor = data.readInt32(false);
                                TLObject request = TLRPC.TL_messages_deleteScheduledMessages.TLdeserialize(data, constructor, false);
                                if (request == null) {
                                    removePendingTask(taskId);
                                } else {
                                    AndroidUtilities.runOnUIThread(() -> getMessagesController().deleteMessages(null, null, null, dialogId, true, true, false, taskId, request));
                                }
                                break;
                            }
                            case 22: {
                                TLRPC.InputPeer inputPeer = TLRPC.InputPeer.TLdeserialize(data, data.readInt32(false), false);
                                AndroidUtilities.runOnUIThread(() -> getMessagesController().reloadMentionsCountForChannel(inputPeer, taskId));
                                break;
                            }
                            case 100: {
                                final int chatId = data.readInt32(false);
                                final boolean revoke = data.readBool(false);
                                AndroidUtilities.runOnUIThread(() -> getSecretChatHelper().declineSecretChat(chatId, revoke, taskId));
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

    public void saveChannelPts(long channelId, int pts) {
        storageQueue.postRunnable(() -> {
            try {
                SQLitePreparedStatement state = database.executeFast("UPDATE dialogs SET pts = ? WHERE did = ?");
                state.bindInteger(1, pts);
                state.bindLong(2, -channelId);
                state.step();
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void saveDiffParamsInternal(int seq, int pts, int date, int qts) {
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

    public void saveDiffParams(int seq, int pts, int date, int qts) {
        storageQueue.postRunnable(() -> saveDiffParamsInternal(seq, pts, date, qts));
    }

    public void updateMutedDialogsFiltersCounters() {
        storageQueue.postRunnable(() -> resetAllUnreadCounters(true));
    }

    public void setDialogFlags(long did, long flags) {
        storageQueue.postRunnable(() -> {
            try {
                int oldFlags = 0;
                SQLiteCursor cursor = database.queryFinalized("SELECT flags FROM dialog_settings WHERE did = " + did);
                if (cursor.next()) {
                    oldFlags = cursor.intValue(0);
                }
                cursor.dispose();
                if (flags == oldFlags) {
                    return;
                }
                database.executeFast(String.format(Locale.US, "REPLACE INTO dialog_settings VALUES(%d, %d)", did, flags)).stepThis().dispose();
                resetAllUnreadCounters(true);
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
                state.bindInteger(2, message.getId());
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

    public void clearLocalDatabase() {
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            try {
                SQLiteDatabase database = MessagesStorage.getInstance(currentAccount).getDatabase();
                ArrayList<Long> dialogsToCleanup = new ArrayList<>();

                database.executeFast("DELETE FROM reaction_mentions").stepThis().dispose();
                database.executeFast("DELETE FROM downloading_documents").stepThis().dispose();

                SQLiteCursor cursor = database.queryFinalized("SELECT did FROM dialogs WHERE 1");
                StringBuilder ids = new StringBuilder();
                while (cursor.next()) {
                    long did = cursor.longValue(0);
                    if (!DialogObject.isEncryptedDialog(did)) {
                        dialogsToCleanup.add(did);
                    }
                }
                cursor.dispose();

                SQLitePreparedStatement state5 = database.executeFast("REPLACE INTO messages_holes VALUES(?, ?, ?)");
                SQLitePreparedStatement state6 = database.executeFast("REPLACE INTO media_holes_v2 VALUES(?, ?, ?, ?)");

                database.beginTransaction();
                for (int a = 0; a < dialogsToCleanup.size(); a++) {
                    Long did = dialogsToCleanup.get(a);
                    int messagesCount = 0;
                    cursor = database.queryFinalized("SELECT COUNT(mid) FROM messages_v2 WHERE uid = " + did);
                    if (cursor.next()) {
                        messagesCount = cursor.intValue(0);
                    }
                    cursor.dispose();
                    if (messagesCount <= 2) {
                        continue;
                    }

                    cursor = database.queryFinalized("SELECT last_mid_i, last_mid FROM dialogs WHERE did = " + did);
                    int messageId = -1;
                    if (cursor.next()) {
                        long last_mid_i = cursor.longValue(0);
                        long last_mid = cursor.longValue(1);
                        SQLiteCursor cursor2 = database.queryFinalized("SELECT data FROM messages_v2 WHERE uid = " + did + " AND mid IN (" + last_mid_i + "," + last_mid + ")");
                        try {
                            while (cursor2.next()) {
                                NativeByteBuffer data = cursor2.byteBufferValue(0);
                                if (data != null) {
                                    TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                    if (message != null) {
                                        messageId = message.id;
                                        message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
                                    }
                                    data.reuse();
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        cursor2.dispose();

                        database.executeFast("DELETE FROM messages_v2 WHERE uid = " + did + " AND mid != " + last_mid_i + " AND mid != " + last_mid).stepThis().dispose();
                        database.executeFast("DELETE FROM messages_holes WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM bot_keyboard WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM media_counts_v2 WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM media_v4 WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM media_holes_v2 WHERE uid = " + did).stepThis().dispose();
                        MediaDataController.getInstance(currentAccount).clearBotKeyboard(did, null);
                        if (messageId != -1) {
                            MessagesStorage.createFirstHoles(did, state5, state6, messageId);
                        }
                    }
                    cursor.dispose();
                }

                state5.dispose();
                state6.dispose();
                database.commitTransaction();
                database.executeFast("PRAGMA journal_size_limit = 0").stepThis().dispose();
                database.executeFast("VACUUM").stepThis().dispose();
                database.executeFast("PRAGMA journal_size_limit = -1").stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didClearDatabase);
                });
            }
        });
    }



    private static class ReadDialog {
        public int lastMid;
        public int date;
        public int unreadCount;
    }

    public void readAllDialogs(int folderId) {
        storageQueue.postRunnable(() -> {
            try {
                ArrayList<Long> usersToLoad = new ArrayList<>();
                ArrayList<Long> chatsToLoad = new ArrayList<>();
                ArrayList<Integer> encryptedChatIds = new ArrayList<>();

                LongSparseArray<ReadDialog> dialogs = new LongSparseArray<>();
                SQLiteCursor cursor;
                if (folderId >= 0) {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT did, last_mid, unread_count, date FROM dialogs WHERE unread_count > 0 AND folder_id = %1$d", folderId));
                } else {
                    cursor = database.queryFinalized("SELECT did, last_mid, unread_count, date FROM dialogs WHERE unread_count > 0");
                }
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
                    if (!DialogObject.isEncryptedDialog(did)) {
                        if (DialogObject.isChatDialog(did)) {
                            if (!chatsToLoad.contains(-did)) {
                                chatsToLoad.add(-did);
                            }
                        } else {
                            if (!usersToLoad.contains(did)) {
                                usersToLoad.add(did);
                            }
                        }
                    } else {
                        int encryptedChatId = DialogObject.getEncryptedChatId(did);
                        if (!encryptedChatIds.contains(encryptedChatId)) {
                            encryptedChatIds.add(encryptedChatId);
                        }
                    }
                }
                cursor.dispose();

                ArrayList<TLRPC.User> users = new ArrayList<>();
                ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
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
                        getMessagesController().markDialogAsRead(did, dialog.lastMid, dialog.lastMid, dialog.date, false, 0, dialog.unreadCount, true, 0);
                    }
                });
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private TLRPC.messages_Dialogs loadDialogsByIds(String ids, ArrayList<Long> usersToLoad, ArrayList<Long> chatsToLoad, ArrayList<Integer> encryptedToLoad) throws Exception {
        TLRPC.messages_Dialogs dialogs = new TLRPC.TL_messages_dialogs();
        LongSparseArray<TLRPC.Message> replyMessageOwners = new LongSparseArray<>();

        SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT d.did, d.last_mid, d.unread_count, d.date, m.data, m.read_state, m.mid, m.send_state, s.flags, m.date, d.pts, d.inbox_max, d.outbox_max, m.replydata, d.pinned, d.unread_count_i, d.flags, d.folder_id, d.data, d.unread_reactions FROM dialogs as d LEFT JOIN messages_v2 as m ON d.last_mid = m.mid AND d.did = m.uid LEFT JOIN dialog_settings as s ON d.did = s.did WHERE d.did IN (%s) ORDER BY d.pinned DESC, d.date DESC", ids));
        while (cursor.next()) {
            long dialogId = cursor.longValue(0);
            TLRPC.Dialog dialog = new TLRPC.TL_dialog();
            dialog.id = dialogId;
            dialog.top_message = cursor.intValue(1);
            dialog.unread_count = cursor.intValue(2);
            dialog.last_message_date = cursor.intValue(3);
            dialog.pts = cursor.intValue(10);
            dialog.flags = dialog.pts == 0 || DialogObject.isUserDialog(dialog.id) ? 0 : 1;
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
            dialog.unread_reactions_count = cursor.intValue(19);
            dialogs.dialogs.add(dialog);

            NativeByteBuffer data = cursor.byteBufferValue(4);
            if (data != null) {
                TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                if (message != null) {
                    message.readAttachPath(data, getUserConfig().clientUserId);
                    data.reuse();
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
                        if (message.reply_to != null && message.reply_to.reply_to_msg_id != 0 && (
                                message.action instanceof TLRPC.TL_messageActionPinMessage ||
                                        message.action instanceof TLRPC.TL_messageActionPaymentSent ||
                                        message.action instanceof TLRPC.TL_messageActionGameScore)) {
                            if (!cursor.isNull(13)) {
                                NativeByteBuffer data2 = cursor.byteBufferValue(13);
                                if (data2 != null) {
                                    message.replyMessage = TLRPC.Message.TLdeserialize(data2, data2.readInt32(false), false);
                                    message.replyMessage.readAttachPath(data2, getUserConfig().clientUserId);
                                    data2.reuse();
                                    if (message.replyMessage != null) {
                                        addUsersAndChatsFromMessage(message.replyMessage, usersToLoad, chatsToLoad);
                                    }
                                }
                            }
                            if (message.replyMessage == null) {
                                replyMessageOwners.put(dialog.id, message);
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else {
                    data.reuse();
                }
            }
            if (DialogObject.isEncryptedDialog(dialogId)) {
                int encryptedChatId = DialogObject.getEncryptedChatId(dialogId);
                if (!encryptedToLoad.contains(encryptedChatId)) {
                    encryptedToLoad.add(encryptedChatId);
                }
            } else if (DialogObject.isUserDialog(dialogId)) {
                if (!usersToLoad.contains(dialogId)) {
                    usersToLoad.add(dialogId);
                }
            } else {
                if (!chatsToLoad.contains(-dialogId)) {
                    chatsToLoad.add(-dialogId);
                }
            }
        }
        cursor.dispose();

        if (!replyMessageOwners.isEmpty()) {
            for (int a = 0, N = replyMessageOwners.size(); a < N; a++) {
                long dialogId = replyMessageOwners.keyAt(a);
                TLRPC.Message ownerMessage = replyMessageOwners.valueAt(a);
                SQLiteCursor replyCursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid, date, uid FROM messages_v2 WHERE mid = %d and uid = %d", ownerMessage.id, dialogId));
                while (replyCursor.next()) {
                    NativeByteBuffer data = replyCursor.byteBufferValue(0);
                    if (data != null) {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        message.readAttachPath(data, getUserConfig().clientUserId);
                        data.reuse();
                        message.id = replyCursor.intValue(1);
                        message.date = replyCursor.intValue(2);
                        message.dialog_id = replyCursor.longValue(3);

                        addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);

                        ownerMessage.replyMessage = message;
                        message.dialog_id = ownerMessage.dialog_id;
                    }
                }
                replyCursor.dispose();
            }
        }
        return dialogs;
    }

    private void loadDialogFilters() {
        storageQueue.postRunnable(() -> {
            try {
                ArrayList<Long> usersToLoad = new ArrayList<>();
                ArrayList<Long> chatsToLoad = new ArrayList<>();
                ArrayList<Integer> encryptedToLoad = new ArrayList<>();
                ArrayList<Long> dialogsToLoad = new ArrayList<>();
                SparseArray<MessagesController.DialogFilter> filtersById = new SparseArray<>();

                usersToLoad.add(getUserConfig().getClientUserId());

                SQLiteCursor filtersCursor = database.queryFinalized("SELECT id, ord, unread_count, flags, title FROM dialog_filter WHERE 1");

                boolean updateCounters = false;
                while (filtersCursor.next()) {
                    MessagesController.DialogFilter filter = new MessagesController.DialogFilter();
                    filter.id = filtersCursor.intValue(0);
                    filter.order = filtersCursor.intValue(1);
                    filter.pendingUnreadCount = filter.unreadCount = -1;//filtersCursor.intValue(2);
                    filter.flags = filtersCursor.intValue(3);
                    filter.name = filtersCursor.stringValue(4);
                    dialogFilters.add(filter);
                    dialogFiltersMap.put(filter.id, filter);
                    filtersById.put(filter.id, filter);
                    if (filter.pendingUnreadCount < 0) {
                        updateCounters = true;
                    }

                    for (int a = 0; a < 2; a++) {
                        SQLiteCursor cursor2;
                        if (a == 0) {
                            cursor2 = database.queryFinalized("SELECT peer, pin FROM dialog_filter_pin_v2 WHERE id = " + filter.id);
                        } else {
                            cursor2 = database.queryFinalized("SELECT peer FROM dialog_filter_ep WHERE id = " + filter.id);
                        }
                        while (cursor2.next()) {
                            long did = cursor2.longValue(0);
                            if (a == 0) {
                                if (!DialogObject.isEncryptedDialog(did)) {
                                    filter.alwaysShow.add(did);
                                }
                                int pin = cursor2.intValue(1);
                                if (pin != Integer.MIN_VALUE) {
                                    filter.pinnedDialogs.put(did, pin);
                                    if (!dialogsToLoad.contains(did)) {
                                        dialogsToLoad.add(did);
                                    }
                                }
                            } else {
                                if (!DialogObject.isEncryptedDialog(did)) {
                                    filter.neverShow.add(did);
                                }
                            }
                            if (DialogObject.isChatDialog(did)) {
                                if (!chatsToLoad.contains(-did)) {
                                    chatsToLoad.add(-did);
                                }
                            } else if (DialogObject.isUserDialog(did)) {
                                if (!usersToLoad.contains(did)) {
                                    usersToLoad.add(did);
                                }
                            } else {
                                int encryptedChatId = DialogObject.getEncryptedChatId(did);
                                if (!encryptedToLoad.contains(encryptedChatId)) {
                                    encryptedToLoad.add(encryptedChatId);
                                }
                            }
                        }
                        cursor2.dispose();
                    }
                }
                filtersCursor.dispose();

                Collections.sort(dialogFilters, (o1, o2) -> {
                    if (o1.order > o2.order) {
                        return 1;
                    } else if (o1.order < o2.order) {
                        return -1;
                    }
                    return 0;
                });

                if (updateCounters) {
                    calcUnreadCounters(true);
                }

                TLRPC.messages_Dialogs dialogs;
                if (!dialogsToLoad.isEmpty()) {
                    dialogs = loadDialogsByIds(TextUtils.join(",", dialogsToLoad), usersToLoad, chatsToLoad, encryptedToLoad);
                } else {
                    dialogs = new TLRPC.TL_messages_dialogs();
                }

                ArrayList<TLRPC.User> users = new ArrayList<>();
                ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();

                if (!encryptedToLoad.isEmpty()) {
                    getEncryptedChatsInternal(TextUtils.join(",", encryptedToLoad), encryptedChats, usersToLoad);
                }
                if (!usersToLoad.isEmpty()) {
                    getUsersInternal(TextUtils.join(",", usersToLoad), users);
                }
                if (!chatsToLoad.isEmpty()) {
                    getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                }

                getMessagesController().processLoadedDialogFilters(new ArrayList<>(dialogFilters), dialogs, null, users, chats, encryptedChats, 0);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private int[][] contacts = new int[][]{new int[2], new int[2]};
    private int[][] nonContacts = new int[][]{new int[2], new int[2]};
    private int[][] bots = new int[][]{new int[2], new int[2]};
    private int[][] channels = new int[][]{new int[2], new int[2]};
    private int[][] groups = new int[][]{new int[2], new int[2]};
    private int[] mentionChannels = new int[2];
    private int[] mentionGroups = new int[2];
    private LongSparseArray<Integer> dialogsWithMentions = new LongSparseArray<>();
    private LongSparseArray<Integer> dialogsWithUnread = new LongSparseArray<>();

    private void calcUnreadCounters(boolean apply) {
        try {
            for (int a = 0; a < 2; a++) {
                for (int b = 0; b < 2; b++) {
                    contacts[a][b] = nonContacts[a][b] = bots[a][b] = channels[a][b] = groups[a][b] = 0;
                }
            }
            dialogsWithMentions.clear();
            dialogsWithUnread.clear();

            ArrayList<TLRPC.User> users = new ArrayList<>();
            ArrayList<TLRPC.User> encUsers = new ArrayList<>();
            ArrayList<TLRPC.Chat> chats = new ArrayList<>();
            ArrayList<Long> usersToLoad = new ArrayList<>();
            ArrayList<Long> chatsToLoad = new ArrayList<>();
            ArrayList<Integer> encryptedToLoad = new ArrayList<>();
            LongSparseIntArray dialogsByFolders = new LongSparseIntArray();
            SQLiteCursor cursor = database.queryFinalized("SELECT did, folder_id, unread_count, unread_count_i FROM dialogs WHERE unread_count > 0 OR flags > 0 UNION ALL " +
                    "SELECT did, folder_id, unread_count, unread_count_i FROM dialogs WHERE unread_count_i > 0");
            while (cursor.next()) {
                int folderId = cursor.intValue(1);
                long did = cursor.longValue(0);
                int unread = cursor.intValue(2);
                int mentions = cursor.intValue(3);
                if (unread > 0) {
                    dialogsWithUnread.put(did, unread);
                }
                if (mentions > 0) {
                    dialogsWithMentions.put(did, mentions);
                }
                /*if (BuildVars.DEBUG_VERSION) {
                    FileLog.d("unread chat " + did + " counters = " + unread + " and " + mentions);
                }*/
                dialogsByFolders.put(did, folderId);
                if (DialogObject.isEncryptedDialog(did)) {
                    int encryptedChatId = DialogObject.getEncryptedChatId(did);
                    if (!encryptedToLoad.contains(encryptedChatId)) {
                        encryptedToLoad.add(encryptedChatId);
                    }
                } else if (DialogObject.isUserDialog(did)) {
                    if (!usersToLoad.contains(did)) {
                        usersToLoad.add(did);
                    }
                } else {
                    if (!chatsToLoad.contains(-did)) {
                        chatsToLoad.add(-did);
                    }
                }
            }
            cursor.dispose();
            LongSparseArray<TLRPC.User> usersDict = new LongSparseArray<>();
            LongSparseArray<TLRPC.Chat> chatsDict = new LongSparseArray<>();
            LongSparseArray<TLRPC.User> encUsersDict = new LongSparseArray<>();
            LongSparseIntArray encryptedChatsByUsersCount = new LongSparseIntArray();
            LongSparseArray<Boolean> mutedDialogs = new LongSparseArray<>();
            LongSparseArray<Boolean> archivedDialogs = new LongSparseArray<>();
            if (!usersToLoad.isEmpty()) {
                getUsersInternal(TextUtils.join(",", usersToLoad), users);
                for (int a = 0, N = users.size(); a < N; a++) {
                    TLRPC.User user = users.get(a);
                    boolean muted = getMessagesController().isDialogMuted(user.id);
                    int idx1 = dialogsByFolders.get(user.id);
                    int idx2 = muted ? 1 : 0;
                    if (muted) {
                        mutedDialogs.put(user.id, true);
                    }
                    if (idx1 == 1) {
                        archivedDialogs.put(user.id, true);
                    }
                    if (user.bot) {
                        bots[idx1][idx2]++;
                    } else if (user.self || user.contact) {
                        contacts[idx1][idx2]++;
                    } else {
                        nonContacts[idx1][idx2]++;
                    }
                    usersDict.put(user.id, user);
                }
            }
            if (!encryptedToLoad.isEmpty()) {
                ArrayList<Long> encUsersToLoad = new ArrayList<>();
                ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
                getEncryptedChatsInternal(TextUtils.join(",", encryptedToLoad), encryptedChats, encUsersToLoad);
                if (!encUsersToLoad.isEmpty()) {
                    getUsersInternal(TextUtils.join(",", encUsersToLoad), encUsers);
                    for (int a = 0, N = encUsers.size(); a < N; a++) {
                        TLRPC.User user = encUsers.get(a);
                        encUsersDict.put(user.id, user);
                    }
                    for (int a = 0, N = encryptedChats.size(); a < N; a++) {
                        TLRPC.EncryptedChat encryptedChat = encryptedChats.get(a);
                        TLRPC.User user = encUsersDict.get(encryptedChat.user_id);
                        if (user == null) {
                            continue;
                        }
                        long did = DialogObject.makeEncryptedDialogId(encryptedChat.id);
                        boolean muted = getMessagesController().isDialogMuted(did);
                        int idx1 = dialogsByFolders.get(did);
                        int idx2 = muted ? 1 : 0;
                        if (muted) {
                            mutedDialogs.put(user.id, true);
                        }
                        if (idx1 == 1) {
                            archivedDialogs.put(user.id, true);
                        }
                        if (user.self || user.contact) {
                            contacts[idx1][idx2]++;
                        } else {
                            nonContacts[idx1][idx2]++;
                        }
                        int count = encryptedChatsByUsersCount.get(user.id, 0);
                        encryptedChatsByUsersCount.put(user.id, count + 1);
                    }
                }
            }
            if (!chatsToLoad.isEmpty()) {
                getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                for (int a = 0, N = chats.size(); a < N; a++) {
                    TLRPC.Chat chat = chats.get(a);
                    if (chat.migrated_to instanceof TLRPC.TL_inputChannel || ChatObject.isNotInChat(chat)) {
                        dialogsWithUnread.remove(-chat.id);
                        dialogsWithMentions.remove(-chat.id);
                        continue;
                    }
                    boolean muted = getMessagesController().isDialogMuted(-chat.id, chat);
                    int idx1 = dialogsByFolders.get(-chat.id);
                    int idx2 = muted && dialogsWithMentions.indexOfKey(-chat.id) < 0 ? 1 : 0;
                    if (muted) {
                        mutedDialogs.put(-chat.id, true);
                    }
                    if (idx1 == 1) {
                        archivedDialogs.put(-chat.id, true);
                    }
                    if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        channels[idx1][idx2]++;
                    } else {
                        groups[idx1][idx2]++;
                    }
                    chatsDict.put(chat.id, chat);
                }
            }
            /*if (BuildVars.DEBUG_VERSION) {
                for (int b = 0; b < 2; b++) {
                    FileLog.d("contacts = " + contacts[b][0] + ", " + contacts[b][1]);
                    FileLog.d("nonContacts = " + nonContacts[b][0] + ", " + nonContacts[b][1]);
                    FileLog.d("groups = " + groups[b][0] + ", " + groups[b][1]);
                    FileLog.d("channels = " + channels[b][0] + ", " + channels[b][1]);
                    FileLog.d("bots = " + bots[b][0] + ", " + bots[b][1]);
                }
            }*/
            for (int a = 0, N = dialogFilters.size(); a < N + 2; a++) {
                MessagesController.DialogFilter filter;
                int flags;
                if (a < N) {
                    filter = dialogFilters.get(a);
                    if (filter.pendingUnreadCount >= 0) {
                        continue;
                    }
                    flags = filter.flags;
                } else {
                    filter = null;
                    flags = MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS;
                    if (a == N) {
                        if (!getNotificationsController().showBadgeMuted) {
                            flags |= MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED;
                        }
                        flags |= MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED;
                    } else {
                        flags |= MessagesController.DIALOG_FILTER_FLAG_ONLY_ARCHIVED;
                    }
                }
                int unreadCount = 0;
                if ((flags & MessagesController.DIALOG_FILTER_FLAG_CONTACTS) != 0) {
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_ONLY_ARCHIVED) == 0) {
                        unreadCount += contacts[0][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount += contacts[0][1];
                        }
                    }
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) == 0) {
                        unreadCount += contacts[1][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount += contacts[1][1];
                        }
                    }
                }
                if ((flags & MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS) != 0) {
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_ONLY_ARCHIVED) == 0) {
                        unreadCount += nonContacts[0][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount += nonContacts[0][1];
                        }
                    }
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) == 0) {
                        unreadCount += nonContacts[1][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount += nonContacts[1][1];
                        }
                    }
                }
                if ((flags & MessagesController.DIALOG_FILTER_FLAG_GROUPS) != 0) {
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_ONLY_ARCHIVED) == 0) {
                        unreadCount += groups[0][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount += groups[0][1];
                        }
                    }
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) == 0) {
                        unreadCount += groups[1][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount += groups[1][1];
                        }
                    }
                }
                if ((flags & MessagesController.DIALOG_FILTER_FLAG_CHANNELS) != 0) {
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_ONLY_ARCHIVED) == 0) {
                        unreadCount += channels[0][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount += channels[0][1];
                        }
                    }
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) == 0) {
                        unreadCount += channels[1][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount += channels[1][1];
                        }
                    }
                }
                if ((flags & MessagesController.DIALOG_FILTER_FLAG_BOTS) != 0) {
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_ONLY_ARCHIVED) == 0) {
                        unreadCount += bots[0][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount += bots[0][1];
                        }
                    }
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) == 0) {
                        unreadCount += bots[1][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount += bots[1][1];
                        }
                    }
                }
                if (filter != null) {
                    for (int b = 0, N2 = filter.alwaysShow.size(); b < N2; b++) {
                        long did = filter.alwaysShow.get(b);
                        if (DialogObject.isUserDialog(did)) {
                            for (int i = 0; i < 2; i++) {
                                LongSparseArray<TLRPC.User> dict = i == 0 ? usersDict : encUsersDict;
                                TLRPC.User user = dict.get(did);
                                if (user != null) {
                                    int count;
                                    if (i == 0) {
                                        count = 1;
                                    } else {
                                        count = encryptedChatsByUsersCount.get(did, 0);
                                        if (count == 0) {
                                            continue;
                                        }
                                    }
                                    int flag;
                                    if (user.bot) {
                                        flag = MessagesController.DIALOG_FILTER_FLAG_BOTS;
                                    } else if (user.self || user.contact) {
                                        flag = MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
                                    } else {
                                        flag = MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
                                    }
                                    if ((flags & flag) == 0) {
                                        unreadCount += count;
                                    } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) != 0 && mutedDialogs.indexOfKey(user.id) >= 0) {
                                        unreadCount += count;
                                    } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) != 0 && archivedDialogs.indexOfKey(user.id) >= 0) {
                                        unreadCount += count;
                                    }
                                }
                            }
                        } else {
                            TLRPC.Chat chat = chatsDict.get(-did);
                            if (chat != null) {
                                int flag;
                                if (ChatObject.isChannel(chat) && !chat.megagroup) {
                                    flag = MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
                                } else {
                                    flag = MessagesController.DIALOG_FILTER_FLAG_GROUPS;
                                }
                                if ((flags & flag) == 0) {
                                    unreadCount++;
                                } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) != 0 && mutedDialogs.indexOfKey(-chat.id) >= 0 && dialogsWithMentions.indexOfKey(-chat.id) < 0) {
                                    unreadCount++;
                                } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) != 0 && archivedDialogs.indexOfKey(-chat.id) >= 0) {
                                    unreadCount++;
                                }
                            }
                        }
                    }
                    for (int b = 0, N2 = filter.neverShow.size(); b < N2; b++) {
                        long did = filter.neverShow.get(b);
                        if (DialogObject.isUserDialog(did)) {
                            for (int i = 0; i < 2; i++) {
                                LongSparseArray<TLRPC.User> dict = i == 0 ? usersDict : encUsersDict;
                                TLRPC.User user = dict.get(did);
                                if (user != null) {
                                    int count;
                                    if (i == 0) {
                                        count = 1;
                                    } else {
                                        count = encryptedChatsByUsersCount.get(did, 0);
                                        if (count == 0) {
                                            continue;
                                        }
                                    }
                                    int flag;
                                    if (user.bot) {
                                        flag = MessagesController.DIALOG_FILTER_FLAG_BOTS;
                                    } else if (user.self || user.contact) {
                                        flag = MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
                                    } else {
                                        flag = MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
                                    }
                                    if ((flags & flag) != 0) {
                                        if (((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) == 0 || archivedDialogs.indexOfKey(user.id) < 0) &&
                                                ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0 || mutedDialogs.indexOfKey(user.id) < 0)) {
                                            unreadCount -= count;
                                        }
                                    }
                                }
                            }
                        } else {
                            TLRPC.Chat chat = chatsDict.get(-did);
                            if (chat != null) {
                                int flag;
                                if (ChatObject.isChannel(chat) && !chat.megagroup) {
                                    flag = MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
                                } else {
                                    flag = MessagesController.DIALOG_FILTER_FLAG_GROUPS;
                                }
                                if ((flags & flag) != 0) {
                                    if (((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) == 0 || archivedDialogs.indexOfKey(-chat.id) < 0) &&
                                            ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0 || mutedDialogs.indexOfKey(-chat.id) < 0 || dialogsWithMentions.indexOfKey(-chat.id) >= 0)) {
                                        unreadCount--;
                                    }
                                }
                            }
                        }
                    }
                    filter.pendingUnreadCount = unreadCount;
                    /*if (BuildVars.DEBUG_VERSION) {
                        FileLog.d("filter " + filter.name + " flags = " + filter.flags + " unread count = " + filter.pendingUnreadCount);
                    }*/
                    if (apply) {
                        filter.unreadCount = unreadCount;
                    }
                } else if (a == N) {
                    pendingMainUnreadCount = unreadCount;
                    if (apply) {
                        mainUnreadCount = unreadCount;
                    }
                } else if (a == N + 1) {
                    pendingArchiveUnreadCount = unreadCount;
                    if (apply) {
                        archiveUnreadCount = unreadCount;
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void saveDialogFilterInternal(MessagesController.DialogFilter filter, boolean atBegin, boolean peers) {
        try {
            if (!dialogFilters.contains(filter)) {
                if (atBegin) {
                    dialogFilters.add(0, filter);
                } else {
                    dialogFilters.add(filter);
                }
                dialogFiltersMap.put(filter.id, filter);
            }

            SQLitePreparedStatement state = database.executeFast("REPLACE INTO dialog_filter VALUES(?, ?, ?, ?, ?)");
            state.bindInteger(1, filter.id);
            state.bindInteger(2, filter.order);
            state.bindInteger(3, filter.unreadCount);
            state.bindInteger(4, filter.flags);
            state.bindString(5, filter.name);
            state.step();
            state.dispose();
            if (peers) {
                database.executeFast("DELETE FROM dialog_filter_ep WHERE id = " + filter.id).stepThis().dispose();
                database.executeFast("DELETE FROM dialog_filter_pin_v2 WHERE id = " + filter.id).stepThis().dispose();
                database.beginTransaction();
                state = database.executeFast("REPLACE INTO dialog_filter_pin_v2 VALUES(?, ?, ?)");
                for (int a = 0, N = filter.alwaysShow.size(); a < N; a++) {
                    long key = filter.alwaysShow.get(a);
                    state.requery();
                    state.bindInteger(1, filter.id);
                    state.bindLong(2, key);
                    state.bindInteger(3, filter.pinnedDialogs.get(key, Integer.MIN_VALUE));
                    state.step();
                }
                for (int a = 0, N = filter.pinnedDialogs.size(); a < N; a++) {
                    long key = filter.pinnedDialogs.keyAt(a);
                    if (!DialogObject.isEncryptedDialog(key)) {
                        continue;
                    }
                    state.requery();
                    state.bindInteger(1, filter.id);
                    state.bindLong(2, key);
                    state.bindInteger(3, filter.pinnedDialogs.valueAt(a));
                    state.step();
                }
                state.dispose();

                state = database.executeFast("REPLACE INTO dialog_filter_ep VALUES(?, ?)");
                for (int a = 0, N = filter.neverShow.size(); a < N; a++) {
                    state.requery();
                    state.bindInteger(1, filter.id);
                    state.bindLong(2, filter.neverShow.get(a));
                    state.step();
                }
                state.dispose();
                database.commitTransaction();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void checkLoadedRemoteFilters(TLRPC.Vector vector) {
        storageQueue.postRunnable(() -> {
            try {
                SparseArray<MessagesController.DialogFilter> filtersToDelete = new SparseArray<>();
                for (int a = 0, N = dialogFilters.size(); a < N; a++) {
                    MessagesController.DialogFilter filter = dialogFilters.get(a);
                    filtersToDelete.put(filter.id, filter);
                }
                ArrayList<Integer> filtersOrder = new ArrayList<>();
                ArrayList<Long> usersToLoad = new ArrayList<>();
                HashMap<Long, TLRPC.InputPeer> usersToLoadMap = new HashMap<>();
                ArrayList<Long> chatsToLoad = new ArrayList<>();
                HashMap<Long, TLRPC.InputPeer> chatsToLoadMap = new HashMap<>();
                ArrayList<Long> dialogsToLoad = new ArrayList<>();
                HashMap<Long, TLRPC.InputPeer> dialogsToLoadMap = new HashMap<>();
                ArrayList<MessagesController.DialogFilter> filtersToSave = new ArrayList<>();
                HashMap<Integer, HashSet<Long>> filterUserRemovals = new HashMap<>();
                HashMap<Integer, HashSet<Long>> filterDialogRemovals = new HashMap<>();
                HashSet<Integer> filtersUnreadCounterReset = new HashSet<>();
                for (int a = 0, N = vector.objects.size(); a < N; a++) {
                    TLRPC.TL_dialogFilter newFilter = (TLRPC.TL_dialogFilter) vector.objects.get(a);
                    filtersOrder.add(newFilter.id);
                    int newFlags = 0;
                    if (newFilter.contacts) {
                        newFlags |= MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
                    }
                    if (newFilter.non_contacts) {
                        newFlags |= MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
                    }
                    if (newFilter.groups) {
                        newFlags |= MessagesController.DIALOG_FILTER_FLAG_GROUPS;
                    }
                    if (newFilter.broadcasts) {
                        newFlags |= MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
                    }
                    if (newFilter.bots) {
                        newFlags |= MessagesController.DIALOG_FILTER_FLAG_BOTS;
                    }
                    if (newFilter.exclude_muted) {
                        newFlags |= MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED;
                    }
                    if (newFilter.exclude_read) {
                        newFlags |= MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ;
                    }
                    if (newFilter.exclude_archived) {
                        newFlags |= MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED;
                    }

                    MessagesController.DialogFilter filter = dialogFiltersMap.get(newFilter.id);
                    if (filter != null) {
                        filtersToDelete.remove(newFilter.id);
                        boolean changed = false;
                        boolean unreadChanged = false;
                        if (!TextUtils.equals(filter.name, newFilter.title)) {
                            changed = true;
                            filter.name = newFilter.title;
                        }
                        if (filter.flags != newFlags) {
                            filter.flags = newFlags;
                            changed = true;
                            unreadChanged = true;
                        }

                        HashSet<Long> existingIds = new HashSet<>(filter.alwaysShow);
                        existingIds.addAll(filter.neverShow);
                        HashSet<Long> existingDialogsIds = new HashSet<>();

                        LinkedHashMap<Integer, Long> secretChatsMap = null;
                        if (filter.pinnedDialogs.size() != 0) {
                            ArrayList<Long> pinArray = new ArrayList<>();
                            boolean hasSecret = false;
                            for (int c = 0, N2 = filter.pinnedDialogs.size(); c < N2; c++) {
                                long did = filter.pinnedDialogs.keyAt(c);
                                if (DialogObject.isEncryptedDialog(did)) {
                                    hasSecret = true;
                                }
                                pinArray.add(did);
                            }
                            if (hasSecret) {
                                secretChatsMap = new LinkedHashMap<>();
                                LongSparseIntArray pinnedDialogs = filter.pinnedDialogs;
                                Collections.sort(pinArray, (o1, o2) -> {
                                    int idx1 = pinnedDialogs.get(o1);
                                    int idx2 = pinnedDialogs.get(o2);
                                    if (idx1 > idx2) {
                                        return 1;
                                    } else if (idx1 < idx2) {
                                        return -1;
                                    }
                                    return 0;
                                });
                                for (int c = 0, N2 = pinArray.size(); c < N2; c++) {
                                    long did = pinArray.get(c);
                                    if (!DialogObject.isEncryptedDialog(did)) {
                                        continue;
                                    }
                                    secretChatsMap.put(c, did);
                                }
                            }
                        }

                        for (int c = 0, N2 = filter.pinnedDialogs.size(); c < N2; c++) {
                            long did = filter.pinnedDialogs.keyAt(c);
                            if (DialogObject.isEncryptedDialog(did)) {
                                continue;
                            }
                            existingDialogsIds.add(did);
                            existingIds.remove(did);
                        }
                        for (int c = 0; c < 2; c++) {
                            ArrayList<TLRPC.InputPeer> fromArray = c == 0 ? newFilter.include_peers : newFilter.exclude_peers;
                            ArrayList<Long> toArray = c == 0 ? filter.alwaysShow : filter.neverShow;

                            if (c == 0) {
                                filter.pinnedDialogs.clear();
                                for (int b = 0, N2 = newFilter.pinned_peers.size(); b < N2; b++) {
                                    TLRPC.InputPeer peer = newFilter.pinned_peers.get(b);
                                    Long id;
                                    if (peer.user_id != 0) {
                                        id = peer.user_id;
                                    } else {
                                        id = -(peer.chat_id != 0 ? peer.chat_id : peer.channel_id);
                                    }
                                    if (!filter.alwaysShow.contains(id)) {
                                        filter.alwaysShow.add(id);
                                    }
                                    int index = filter.pinnedDialogs.size();
                                    if (secretChatsMap != null) {
                                        Long did;
                                        while ((did = secretChatsMap.remove(index)) != null) {
                                            filter.pinnedDialogs.put(did, index);
                                            index++;
                                        }
                                    }
                                    filter.pinnedDialogs.put(id, index);
                                    existingIds.remove(id);
                                    if (!existingDialogsIds.remove(id)) {
                                        changed = true;
                                        if (!dialogsToLoadMap.containsKey(id)) {
                                            dialogsToLoad.add(id);
                                            dialogsToLoadMap.put(id, peer);
                                        }
                                    }
                                }
                                if (secretChatsMap != null) {
                                    for (LinkedHashMap.Entry<Integer, Long> entry : secretChatsMap.entrySet()) {
                                        filter.pinnedDialogs.put(entry.getValue(), filter.pinnedDialogs.size());
                                    }
                                }
                            }
                            for (int b = 0, N2 = fromArray.size(); b < N2; b++) {
                                TLRPC.InputPeer peer = fromArray.get(b);
                                if (peer.user_id != 0) {
                                    Long uid = peer.user_id;
                                    if (!existingIds.remove(uid)) {
                                        changed = true;
                                        if (!toArray.contains(uid)) {
                                            toArray.add(uid);
                                        }
                                        if (!usersToLoadMap.containsKey(uid)) {
                                            usersToLoad.add(uid);
                                            usersToLoadMap.put(uid, peer);
                                            unreadChanged = true;
                                        }
                                    }
                                } else {
                                    Long chatId = peer.chat_id != 0 ? peer.chat_id : peer.channel_id;
                                    Long dialogId = -chatId;
                                    if (!existingIds.remove(dialogId)) {
                                        changed = true;
                                        if (!toArray.contains(dialogId)) {
                                            toArray.add(dialogId);
                                        }
                                        if (!chatsToLoadMap.containsKey(chatId)) {
                                            chatsToLoad.add(chatId);
                                            chatsToLoadMap.put(chatId, peer);
                                            unreadChanged = true;
                                        }
                                    }
                                }
                            }
                        }
                        if (!existingIds.isEmpty()) {
                            filterUserRemovals.put(filter.id, existingIds);
                            unreadChanged = true;
                            changed = true;
                        }
                        if (!existingDialogsIds.isEmpty()) {
                            filterDialogRemovals.put(filter.id, existingDialogsIds);
                            changed = true;
                        }
                        if (changed) {
                            filtersToSave.add(filter);
                        }
                        if (unreadChanged) {
                            filtersUnreadCounterReset.add(filter.id);
                        }
                    } else {
                        filter = new MessagesController.DialogFilter();
                        filter.id = newFilter.id;
                        filter.flags = newFlags;
                        filter.name = newFilter.title;
                        filter.pendingUnreadCount = -1;
                        for (int c = 0; c < 2; c++) {
                            if (c == 0) {
                                for (int b = 0, N2 = newFilter.pinned_peers.size(); b < N2; b++) {
                                    TLRPC.InputPeer peer = newFilter.pinned_peers.get(b);
                                    Long id;
                                    if (peer.user_id != 0) {
                                        id = peer.user_id;
                                    } else {
                                        id = -(peer.chat_id != 0 ? peer.chat_id : peer.channel_id);
                                    }
                                    if (!filter.alwaysShow.contains(id)) {
                                        filter.alwaysShow.add(id);
                                    }
                                    filter.pinnedDialogs.put(id, filter.pinnedDialogs.size() + 1);
                                    if (!dialogsToLoadMap.containsKey(id)) {
                                        dialogsToLoad.add(id);
                                        dialogsToLoadMap.put(id, peer);
                                    }
                                }
                            }
                            ArrayList<TLRPC.InputPeer> fromArray = c == 0 ? newFilter.include_peers : newFilter.exclude_peers;
                            ArrayList<Long> toArray = c == 0 ? filter.alwaysShow : filter.neverShow;
                            for (int b = 0, N2 = fromArray.size(); b < N2; b++) {
                                TLRPC.InputPeer peer = fromArray.get(b);
                                if (peer.user_id != 0) {
                                    Long uid = peer.user_id;
                                    if (!toArray.contains(uid)) {
                                        toArray.add(uid);
                                    }
                                    if (!usersToLoadMap.containsKey(uid)) {
                                        usersToLoad.add(uid);
                                        usersToLoadMap.put(uid, peer);
                                    }
                                } else {
                                    Long chatId = peer.chat_id != 0 ? peer.chat_id : peer.channel_id;
                                    Long dialogId = -chatId;
                                    if (!toArray.contains(dialogId)) {
                                        toArray.add(dialogId);
                                    }
                                    if (!chatsToLoadMap.containsKey(chatId)) {
                                        chatsToLoad.add(chatId);
                                        chatsToLoadMap.put(chatId, peer);
                                    }
                                }
                            }
                        }
                        filtersToSave.add(filter);
                    }
                }

                TLRPC.messages_Dialogs dialogs;
                if (!dialogsToLoad.isEmpty()) {
                    dialogs = loadDialogsByIds(TextUtils.join(",", dialogsToLoad), usersToLoad, chatsToLoad, new ArrayList<>());
                    for (int a = 0, N = dialogs.dialogs.size(); a < N; a++) {
                        TLRPC.Dialog dialog = dialogs.dialogs.get(a);
                        dialogsToLoadMap.remove(dialog.id);
                    }
                } else {
                    dialogs = new TLRPC.TL_messages_dialogs();
                }
                ArrayList<TLRPC.User> users = new ArrayList<>();
                if (!usersToLoad.isEmpty()) {
                    getUsersInternal(TextUtils.join(",", usersToLoad), users);
                    for (int a = 0, N = users.size(); a < N; a++) {
                        TLRPC.User user = users.get(a);
                        usersToLoadMap.remove(user.id);
                    }
                }
                ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                if (!chatsToLoad.isEmpty()) {
                    getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                    for (int a = 0, N = chats.size(); a < N; a++) {
                        TLRPC.Chat chat = chats.get(a);
                        chatsToLoadMap.remove(chat.id);
                    }
                }

                if (usersToLoadMap.isEmpty() && chatsToLoadMap.isEmpty() && dialogsToLoadMap.isEmpty()) {
                    processLoadedFilterPeersInternal(dialogs, null, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset);
                } else {
                    getMessagesController().loadFilterPeers(dialogsToLoadMap, usersToLoadMap, chatsToLoadMap, dialogs, new TLRPC.TL_messages_dialogs(), users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void processLoadedFilterPeersInternal(TLRPC.messages_Dialogs pinnedDialogs, TLRPC.messages_Dialogs pinnedRemoteDialogs, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats, ArrayList<MessagesController.DialogFilter> filtersToSave, SparseArray<MessagesController.DialogFilter> filtersToDelete, ArrayList<Integer> filtersOrder, HashMap<Integer, HashSet<Long>> filterDialogRemovals, HashMap<Integer, HashSet<Long>> filterUserRemovals, HashSet<Integer> filtersUnreadCounterReset) {
        boolean anythingChanged = false;
        putUsersAndChats(users, chats, true, false);
        for (int a = 0, N = filtersToDelete.size(); a < N; a++) {
            deleteDialogFilterInternal(filtersToDelete.valueAt(a));
            anythingChanged = true;
        }
        for (Integer id : filtersUnreadCounterReset) {
            MessagesController.DialogFilter filter = dialogFiltersMap.get(id);
            if (filter == null) {
                continue;
            }
            filter.pendingUnreadCount = -1;
        }
        for (HashMap.Entry<Integer, HashSet<Long>> entry : filterUserRemovals.entrySet()) {
            MessagesController.DialogFilter filter = dialogFiltersMap.get(entry.getKey());
            if (filter == null) {
                continue;
            }
            HashSet<Long> set = entry.getValue();
            filter.alwaysShow.removeAll(set);
            filter.neverShow.removeAll(set);
            anythingChanged = true;
        }
        for (HashMap.Entry<Integer, HashSet<Long>> entry : filterDialogRemovals.entrySet()) {
            MessagesController.DialogFilter filter = dialogFiltersMap.get(entry.getKey());
            if (filter == null) {
                continue;
            }
            HashSet<Long> set = entry.getValue();
            for (Long id : set) {
                filter.pinnedDialogs.delete(id);
            }
            anythingChanged = true;
        }
        for (int a = 0, N = filtersToSave.size(); a < N; a++) {
            saveDialogFilterInternal(filtersToSave.get(a), false, true);
            anythingChanged = true;
        }
        boolean orderChanged = false;
        for (int a = 0, N = dialogFilters.size(); a < N; a++) {
            MessagesController.DialogFilter filter = dialogFilters.get(a);
            int order = filtersOrder.indexOf(filter.id);
            if (filter.order != order) {
                filter.order = order;
                anythingChanged = true;
                orderChanged = true;
            }
        }
        if (orderChanged) {
            Collections.sort(dialogFilters, (o1, o2) -> {
                if (o1.order > o2.order) {
                    return 1;
                } else if (o1.order < o2.order) {
                    return -1;
                }
                return 0;
            });
            saveDialogFiltersOrderInternal();
        }
        int remote = anythingChanged ? 1 : 2;
        calcUnreadCounters(true);
        getMessagesController().processLoadedDialogFilters(new ArrayList<>(dialogFilters), pinnedDialogs, pinnedRemoteDialogs, users, chats, null, remote);
    }

    protected void processLoadedFilterPeers(TLRPC.messages_Dialogs pinnedDialogs, TLRPC.messages_Dialogs pinnedRemoteDialogs, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats, ArrayList<MessagesController.DialogFilter> filtersToSave, SparseArray<MessagesController.DialogFilter> filtersToDelete, ArrayList<Integer> filtersOrder, HashMap<Integer, HashSet<Long>> filterDialogRemovals, HashMap<Integer, HashSet<Long>> filterUserRemovals, HashSet<Integer> filtersUnreadCounterReset) {
        storageQueue.postRunnable(() -> processLoadedFilterPeersInternal(pinnedDialogs, pinnedRemoteDialogs, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset));
    }

    private void deleteDialogFilterInternal(MessagesController.DialogFilter filter) {
        try {
            dialogFilters.remove(filter);
            dialogFiltersMap.remove(filter.id);
            database.executeFast("DELETE FROM dialog_filter WHERE id = " + filter.id).stepThis().dispose();
            database.executeFast("DELETE FROM dialog_filter_ep WHERE id = " + filter.id).stepThis().dispose();
            database.executeFast("DELETE FROM dialog_filter_pin_v2 WHERE id = " + filter.id).stepThis().dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void deleteDialogFilter(MessagesController.DialogFilter filter) {
        storageQueue.postRunnable(() -> deleteDialogFilterInternal(filter));
    }

    public void saveDialogFilter(MessagesController.DialogFilter filter, boolean atBegin, boolean peers) {
        storageQueue.postRunnable(() -> {
            saveDialogFilterInternal(filter, atBegin, peers);
            calcUnreadCounters(false);
            AndroidUtilities.runOnUIThread(() -> {
                ArrayList<MessagesController.DialogFilter> filters = getMessagesController().dialogFilters;
                for (int a = 0, N = filters.size(); a < N; a++) {
                    filters.get(a).unreadCount = filters.get(a).pendingUnreadCount;
                }
                mainUnreadCount = pendingMainUnreadCount;
                archiveUnreadCount = pendingArchiveUnreadCount;
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_READ_DIALOG_MESSAGE);
            });
        });
    }

    public void saveDialogFiltersOrderInternal() {
        try {
            SQLitePreparedStatement state = database.executeFast("UPDATE dialog_filter SET ord = ?, flags = ? WHERE id = ?");
            for (int a = 0, N = dialogFilters.size(); a < N; a++) {
                MessagesController.DialogFilter filter = dialogFilters.get(a);
                state.requery();
                state.bindInteger(1, filter.order);
                state.bindInteger(2, filter.flags);
                state.bindInteger(3, filter.id);
                state.step();
            }
            state.dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void saveDialogFiltersOrder() {
        storageQueue.postRunnable(this::saveDialogFiltersOrderInternal);
    }

    protected static void addReplyMessages(TLRPC.Message message, LongSparseArray<SparseArray<ArrayList<TLRPC.Message>>> replyMessageOwners, LongSparseArray<ArrayList<Integer>> dialogReplyMessagesIds) {
        int messageId = message.reply_to.reply_to_msg_id;
        long dialogId = MessageObject.getReplyToDialogId(message);
        SparseArray<ArrayList<TLRPC.Message>> sparseArray = replyMessageOwners.get(dialogId);
        ArrayList<Integer> ids = dialogReplyMessagesIds.get(dialogId);
        if (sparseArray == null) {
            sparseArray = new SparseArray<>();
            replyMessageOwners.put(dialogId, sparseArray);
        }
        if (ids == null) {
            ids = new ArrayList<>();
            dialogReplyMessagesIds.put(dialogId, ids);
        }
        ArrayList<TLRPC.Message> arrayList = sparseArray.get(message.reply_to.reply_to_msg_id);
        if (arrayList == null) {
            arrayList = new ArrayList<>();
            sparseArray.put(message.reply_to.reply_to_msg_id, arrayList);
            if (!ids.contains(message.reply_to.reply_to_msg_id)) {
                ids.add(message.reply_to.reply_to_msg_id);
            }
        }
        arrayList.add(message);
    }

    protected void loadReplyMessages(LongSparseArray<SparseArray<ArrayList<TLRPC.Message>>> replyMessageOwners, LongSparseArray<ArrayList<Integer>> dialogReplyMessagesIds, ArrayList<Long> usersToLoad, ArrayList<Long> chatsToLoad) throws SQLiteException {
        if (replyMessageOwners.isEmpty()) {
            return;
        }

        for (int b = 0, N2 = replyMessageOwners.size(); b < N2; b++) {
            long dialogId = replyMessageOwners.keyAt(b);
            SparseArray<ArrayList<TLRPC.Message>> owners = replyMessageOwners.valueAt(b);
            ArrayList<Integer> ids = dialogReplyMessagesIds.get(dialogId);
            if (ids == null) {
                continue;
            }
            SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid, date, uid FROM messages_v2 WHERE mid IN(%s) AND uid = %d", TextUtils.join(",", ids), dialogId));
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

                    ArrayList<TLRPC.Message> arrayList = owners.get(message.id);
                    if (arrayList != null) {
                        for (int a = 0, N = arrayList.size(); a < N; a++) {
                            TLRPC.Message m = arrayList.get(a);
                            m.replyMessage = message;
                            MessageObject.getDialogId(message);
                        }
                    }
                }
            }
            cursor.dispose();
        }
    }

    public void loadUnreadMessages() {
        storageQueue.postRunnable(() -> {
            try {
                ArrayList<Long> usersToLoad = new ArrayList<>();
                ArrayList<Long> chatsToLoad = new ArrayList<>();
                ArrayList<Integer> encryptedChatIds = new ArrayList<>();

                LongSparseArray<Integer> pushDialogs = new LongSparseArray<>();
                SQLiteCursor cursor = database.queryFinalized("SELECT d.did, d.unread_count, s.flags FROM dialogs as d LEFT JOIN dialog_settings as s ON d.did = s.did WHERE d.unread_count > 0");
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
                        if (DialogObject.isEncryptedDialog(did)) {
                            int encryptedChatId = DialogObject.getEncryptedChatId(did);
                            if (!encryptedChatIds.contains(encryptedChatId)) {
                                encryptedChatIds.add(encryptedChatId);
                            }
                        } else if (DialogObject.isUserDialog(did)) {
                            if (!usersToLoad.contains(did)) {
                                usersToLoad.add(did);
                            }
                        } else {
                            if (!chatsToLoad.contains(-did)) {
                                chatsToLoad.add(-did);
                            }
                        }
                    }
                }
                cursor.dispose();

                LongSparseArray<SparseArray<ArrayList<TLRPC.Message>>> replyMessageOwners = new LongSparseArray<>();
                LongSparseArray<ArrayList<Integer>> dialogReplyMessagesIds = new LongSparseArray<>();
                ArrayList<TLRPC.Message> messages = new ArrayList<>();
                ArrayList<MessageObject> pushMessages = new ArrayList<>();
                ArrayList<TLRPC.User> users = new ArrayList<>();
                ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
                int maxDate = 0;
                if (ids.length() > 0) {
                    cursor = database.queryFinalized("SELECT read_state, data, send_state, mid, date, uid, replydata FROM messages_v2 WHERE uid IN (" + ids.toString() + ") AND out = 0 AND read_state IN(0,2) ORDER BY date DESC LIMIT 50");
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

                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);
                            message.send_state = cursor.intValue(2);
                            if (message.peer_id.channel_id == 0 && !MessageObject.isUnread(message) && !DialogObject.isEncryptedDialog(message.dialog_id) || message.id > 0) {
                                message.send_state = 0;
                            }
                            if (DialogObject.isEncryptedDialog(message.dialog_id) && !cursor.isNull(5)) {
                                message.random_id = cursor.longValue(5);
                            }

                            try {
                                if (message.reply_to != null && message.reply_to.reply_to_msg_id != 0 && (
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
                                                addUsersAndChatsFromMessage(message.replyMessage, usersToLoad, chatsToLoad);
                                            }
                                        }
                                    }
                                    if (message.replyMessage == null) {
                                        addReplyMessages(message, replyMessageOwners, dialogReplyMessagesIds);
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
                            if (MessageObject.getFromChatId(message) == 0) {
                                if (DialogObject.isUserDialog(message.dialog_id)) {
                                    message.from_id = new TLRPC.TL_peerUser();
                                    message.from_id.user_id = message.dialog_id;
                                }
                            }
                            if (DialogObject.isUserDialog(message.dialog_id)) {
                                if (!usersToLoad.contains(message.dialog_id)) {
                                    usersToLoad.add(message.dialog_id);
                                }
                            } else if (DialogObject.isChatDialog(message.dialog_id)) {
                                if (!chatsToLoad.contains(-message.dialog_id)) {
                                    chatsToLoad.add(-message.dialog_id);
                                }
                            }

                            pushMessages.add(new MessageObject(currentAccount, message, messageText, name, userName, (flags & 1) != 0, (flags & 2) != 0, (message.flags & 0x80000000) != 0, false));
                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);
                        }
                    }
                    cursor.dispose();

                    loadReplyMessages(replyMessageOwners, dialogReplyMessagesIds, usersToLoad, chatsToLoad);

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
                            if (chat != null && (ChatObject.isNotInChat(chat) || chat.min || chat.migrated_to != null)) {
                                long did = -chat.id;
                                database.executeFast("UPDATE dialogs SET unread_count = 0 WHERE did = " + did).stepThis().dispose();
                                database.executeFast(String.format(Locale.US, "UPDATE messages_v2 SET read_state = 3 WHERE uid = %d AND mid > 0 AND read_state IN(0,2) AND out = 0", did)).stepThis().dispose();
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

    public void putWallpapers(ArrayList<TLRPC.WallPaper> wallPapers, int action) {
        storageQueue.postRunnable(() -> {
            try {
                if (action == 1) {
                    database.executeFast("DELETE FROM wallpapers2 WHERE num >= -1").stepThis().dispose();
                }
                database.beginTransaction();
                SQLitePreparedStatement state;
                if (action != 0) {
                    state = database.executeFast("REPLACE INTO wallpapers2 VALUES(?, ?, ?)");
                } else {
                    state = database.executeFast("UPDATE wallpapers2 SET data = ? WHERE uid = ?");
                }
                for (int a = 0, N = wallPapers.size(); a < N; a++) {
                    TLRPC.WallPaper wallPaper = (TLRPC.WallPaper) wallPapers.get(a);
                    state.requery();
                    NativeByteBuffer data = new NativeByteBuffer(wallPaper.getObjectSize());
                    wallPaper.serializeToStream(data);
                    if (action != 0) {
                        state.bindLong(1, wallPaper.id);
                        state.bindByteBuffer(2, data);
                        if (action < 0) {
                            state.bindInteger(3, action);
                        } else {
                            state.bindInteger(3, action == 2 ? -1 : a);
                        }
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

    public void deleteWallpaper(long id) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM wallpapers2 WHERE uid = " + id).stepThis().dispose();
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
                ArrayList<TLRPC.WallPaper> wallPapers = new ArrayList<>();
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        TLRPC.WallPaper wallPaper = TLRPC.WallPaper.TLdeserialize(data, data.readInt32(false), false);
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

    public void addRecentLocalFile(String imageUrl, String localUrl, TLRPC.Document document) {
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

    public void deleteUserChatHistory(long dialogId, long fromId) {
        storageQueue.postRunnable(() -> {
            try {
                ArrayList<Integer> mids = new ArrayList<>();
                SQLiteCursor cursor = database.queryFinalized("SELECT data FROM messages_v2 WHERE uid = " + dialogId);
                ArrayList<File> filesToDelete = new ArrayList<>();
                ArrayList<String> namesToDelete = new ArrayList<>();
                ArrayList<Pair<Long, Integer>> idsToDelete = new ArrayList<>();
                try {
                    while (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            if (message != null) {
                                message.readAttachPath(data, getUserConfig().clientUserId);
                                if (UserObject.isReplyUser(dialogId) && MessageObject.getPeerId(message.fwd_from.from_id) == fromId || MessageObject.getFromChatId(message) == fromId && message.id != 1) {
                                    mids.add(message.id);
                                    addFilesToDelete(message, filesToDelete, idsToDelete, namesToDelete, false);
                                }
                            }
                            data.reuse();
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                cursor.dispose();
                deleteFromDownloadQueue(idsToDelete, true);
                AndroidUtilities.runOnUIThread(() -> {
                    getFileLoader().cancelLoadFiles(namesToDelete);
                    getMessagesController().markDialogMessageAsDeleted(dialogId, mids);
                });
                markMessagesAsDeletedInternal(dialogId, mids, false, false);
                updateDialogsWithDeletedMessagesInternal(dialogId, DialogObject.isChatDialog(dialogId) ? -dialogId : 0, mids, null);
                getFileLoader().deleteFiles(filesToDelete, 0);
                if (!mids.isEmpty()) {
                    AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.messagesDeleted, mids, DialogObject.isChatDialog(dialogId) ? -dialogId : 0, false));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private boolean addFilesToDelete(TLRPC.Message message, ArrayList<File> filesToDelete, ArrayList<Pair<Long, Integer>> ids, ArrayList<String> namesToDelete, boolean forceCache) {
        if (message == null) {
            return false;
        }
        int type = 0;
        long id = 0;
        TLRPC.Document document = MessageObject.getDocument(message);
        TLRPC.Photo photo = MessageObject.getPhoto(message);
        if (MessageObject.isVoiceMessage(message)) {
            if (document == null) {
                return false;
            }
            id = document.id;
            type = DownloadController.AUTODOWNLOAD_TYPE_AUDIO;
        } else if (MessageObject.isStickerMessage(message) || MessageObject.isAnimatedStickerMessage(message)) {
            if (document == null) {
                return false;
            }
            id = document.id;
            type = DownloadController.AUTODOWNLOAD_TYPE_PHOTO;
        } else if (MessageObject.isVideoMessage(message) || MessageObject.isRoundVideoMessage(message) || MessageObject.isGifMessage(message)) {
            if (document == null) {
                return false;
            }
            id = document.id;
            type = DownloadController.AUTODOWNLOAD_TYPE_VIDEO;
        } else if (document != null) {
            id = document.id;
            type = DownloadController.AUTODOWNLOAD_TYPE_DOCUMENT;
        } else if (photo != null) {
            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
            if (photoSize != null) {
                id = photo.id;
                type = DownloadController.AUTODOWNLOAD_TYPE_PHOTO;
            }
        }
        if (id != 0) {
            ids.add(new Pair<>(id, type));
        }
        if (photo != null) {
            for (int a = 0, N = photo.sizes.size(); a < N; a++) {
                TLRPC.PhotoSize photoSize = photo.sizes.get(a);
                String name = FileLoader.getAttachFileName(photoSize);
                if (!TextUtils.isEmpty(name)) {
                    namesToDelete.add(name);
                }
                File file = FileLoader.getPathToAttach(photoSize, forceCache);
                if (file.toString().length() > 0) {
                    filesToDelete.add(file);
                }
            }
            return true;
        } else if (document != null) {
            String name = FileLoader.getAttachFileName(document);
            if (!TextUtils.isEmpty(name)) {
                namesToDelete.add(name);
            }
            File file = FileLoader.getPathToAttach(document, forceCache);
            if (file.toString().length() > 0) {
                filesToDelete.add(file);
            }
            for (int a = 0, N = document.thumbs.size(); a < N; a++) {
                TLRPC.PhotoSize photoSize = document.thumbs.get(a);
                file = FileLoader.getPathToAttach(photoSize);
                if (file.toString().length() > 0) {
                    filesToDelete.add(file);
                }
            }
            return true;
        }
        return false;
    }

    public void deleteDialog(long did, int messagesOnly) {
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
                if (DialogObject.isEncryptedDialog(did) || messagesOnly == 2) {
                    SQLiteCursor cursor = database.queryFinalized("SELECT data FROM messages_v2 WHERE uid = " + did);
                    ArrayList<File> filesToDelete = new ArrayList<>();
                    ArrayList<String> namesToDelete = new ArrayList<>();
                    ArrayList<Pair<Long, Integer>> idsToDelete = new ArrayList<>();
                    try {
                        while (cursor.next()) {
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                message.readAttachPath(data, getUserConfig().clientUserId);
                                data.reuse();
                                addFilesToDelete(message, filesToDelete, idsToDelete, namesToDelete, false);
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    cursor.dispose();
                    deleteFromDownloadQueue(idsToDelete, true);
                    AndroidUtilities.runOnUIThread(() -> getFileLoader().cancelLoadFiles(namesToDelete));
                    getFileLoader().deleteFiles(filesToDelete, messagesOnly);
                }

                if (messagesOnly == 0 || messagesOnly == 3) {
                    database.executeFast("DELETE FROM dialogs WHERE did = " + did).stepThis().dispose();
                    database.executeFast("DELETE FROM chat_pinned_v2 WHERE uid = " + did).stepThis().dispose();
                    database.executeFast("DELETE FROM chat_pinned_count WHERE uid = " + did).stepThis().dispose();
                    database.executeFast("DELETE FROM channel_users_v2 WHERE did = " + did).stepThis().dispose();
                    database.executeFast("DELETE FROM search_recent WHERE did = " + did).stepThis().dispose();
                    if (!DialogObject.isEncryptedDialog(did)) {
                        if (DialogObject.isChatDialog(did)) {
                            database.executeFast("DELETE FROM chat_settings_v2 WHERE uid = " + (-did)).stepThis().dispose();
                        }
                    } else {
                        database.executeFast("DELETE FROM enc_chats WHERE uid = " + DialogObject.getEncryptedChatId(did)).stepThis().dispose();
                    }
                } else if (messagesOnly == 2) {
                    SQLiteCursor cursor = database.queryFinalized("SELECT last_mid_i, last_mid FROM dialogs WHERE did = " + did);
                    int messageId = -1;
                    if (cursor.next()) {
                        long last_mid_i = cursor.longValue(0);
                        long last_mid = cursor.longValue(1);
                        SQLiteCursor cursor2 = database.queryFinalized("SELECT data FROM messages_v2 WHERE uid = " + did + " AND mid IN (" + last_mid_i + "," + last_mid + ")");
                        try {
                            while (cursor2.next()) {
                                NativeByteBuffer data = cursor2.byteBufferValue(0);
                                if (data != null) {
                                    TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                    if (message != null) {
                                        message.readAttachPath(data, getUserConfig().clientUserId);
                                    }
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

                        database.executeFast("DELETE FROM messages_v2 WHERE uid = " + did + " AND mid != " + last_mid_i + " AND mid != " + last_mid).stepThis().dispose();
                        database.executeFast("DELETE FROM messages_holes WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM bot_keyboard WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM media_counts_v2 WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM media_v4 WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM media_holes_v2 WHERE uid = " + did).stepThis().dispose();
                        getMediaDataController().clearBotKeyboard(did, null);

                        SQLitePreparedStatement state5 = database.executeFast("REPLACE INTO messages_holes VALUES(?, ?, ?)");
                        SQLitePreparedStatement state6 = database.executeFast("REPLACE INTO media_holes_v2 VALUES(?, ?, ?, ?)");
                        if (messageId != -1) {
                            createFirstHoles(did, state5, state6, messageId);
                        }
                        state5.dispose();
                        state6.dispose();
                        updateWidgets(did);
                    }
                    cursor.dispose();
                    return;
                }

                database.executeFast("UPDATE dialogs SET unread_count = 0, unread_count_i = 0 WHERE did = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM messages_v2 WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM bot_keyboard WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM media_counts_v2 WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM media_v4 WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM messages_holes WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM media_holes_v2 WHERE uid = " + did).stepThis().dispose();
                getMediaDataController().clearBotKeyboard(did, null);
                AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.needReloadRecentDialogsSearch));
                resetAllUnreadCounters(false);
                updateWidgets(did);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void onDeleteQueryComplete(long did) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM media_counts_v2 WHERE uid = " + did).stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void getDialogPhotos(long did, int count, int maxId, int classGuid) {
        storageQueue.postRunnable(() -> {
            try {
                SQLiteCursor cursor;

                if (maxId != 0) {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM user_photos WHERE uid = %d AND id < %d ORDER BY rowid ASC LIMIT %d", did, maxId, count));
                } else {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM user_photos WHERE uid = %d ORDER BY rowid ASC LIMIT %d", did, count));
                }

                TLRPC.photos_Photos res = new TLRPC.TL_photos_photos();
                ArrayList<TLRPC.Message> messages = new ArrayList<>();

                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        TLRPC.Photo photo = TLRPC.Photo.TLdeserialize(data, data.readInt32(false), false);
                        if (data.remaining() > 0) {
                            messages.add(TLRPC.Message.TLdeserialize(data, data.readInt32(false), false));
                        } else {
                            messages.add(null);
                        }
                        data.reuse();
                        res.photos.add(photo);
                    }
                }
                cursor.dispose();

                Utilities.stageQueue.postRunnable(() -> getMessagesController().processLoadedUserPhotos(res, messages, did, count, maxId, true, classGuid));
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void clearUserPhotos(long dialogId) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM user_photos WHERE uid = " + dialogId).stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void clearUserPhoto(long dialogId, long pid) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM user_photos WHERE uid = " + dialogId + " AND id = " + pid).stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void resetDialogs(TLRPC.messages_Dialogs dialogsRes, int messagesCount, int seq, int newPts, int date, int qts, LongSparseArray<TLRPC.Dialog> new_dialogs_dict, LongSparseArray<MessageObject> new_dialogMessage, TLRPC.Message lastMessage, int dialogsCount) {
        storageQueue.postRunnable(() -> {
            try {
                int maxPinnedNum = 0;

                ArrayList<Long> dids = new ArrayList<>();

                int totalPinnedCount = dialogsRes.dialogs.size() - dialogsCount;
                LongSparseIntArray oldPinnedDialogNums = new LongSparseIntArray();
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
                    if (!DialogObject.isEncryptedDialog(did)) {
                        dids.add(did);
                        if (pinnedNum > 0) {
                            maxPinnedNum = Math.max(pinnedNum, maxPinnedNum);
                            oldPinnedDialogNums.put(did, pinnedNum);
                            oldPinnedOrder.add(did);
                        }
                    }
                }
                Collections.sort(oldPinnedOrder, (o1, o2) -> {
                    int val1 = oldPinnedDialogNums.get(o1);
                    int val2 = oldPinnedDialogNums.get(o2);
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
                database.executeFast("DELETE FROM chat_pinned_count WHERE uid IN " + ids).stepThis().dispose();
                database.executeFast("DELETE FROM chat_pinned_v2 WHERE uid IN " + ids).stepThis().dispose();
                database.executeFast("DELETE FROM dialogs WHERE did IN " + ids).stepThis().dispose();
                database.executeFast("DELETE FROM messages_v2 WHERE uid IN " + ids).stepThis().dispose();
                database.executeFast("DELETE FROM polls_v2 WHERE 1").stepThis().dispose();
                database.executeFast("DELETE FROM bot_keyboard WHERE uid IN " + ids).stepThis().dispose();
                database.executeFast("DELETE FROM media_v4 WHERE uid IN " + ids).stepThis().dispose();
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
                            int oldNum = oldPinnedDialogNums.get(dialog.id, -1);
                            if (oldNum != -1) {
                                dialog.pinnedNum = oldNum;
                            }
                        } else {
                            long oldDid = oldPinnedOrder.get(newIdx);
                            int oldNum = oldPinnedDialogNums.get(oldDid, -1);
                            if (oldNum != -1) {
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
                int dialogsLoadOffsetId;
                int dialogsLoadOffsetDate;
                long dialogsLoadOffsetChannelId = 0;
                long dialogsLoadOffsetChatId = 0;
                long dialogsLoadOffsetUserId = 0;
                long dialogsLoadOffsetAccess = 0;

                totalDialogsLoadCount += dialogsRes.dialogs.size();
                dialogsLoadOffsetId = lastMessage.id;
                dialogsLoadOffsetDate = lastMessage.date;
                if (lastMessage.peer_id.channel_id != 0) {
                    dialogsLoadOffsetChannelId = lastMessage.peer_id.channel_id;
                    dialogsLoadOffsetChatId = 0;
                    dialogsLoadOffsetUserId = 0;
                    for (int a = 0; a < dialogsRes.chats.size(); a++) {
                        TLRPC.Chat chat = dialogsRes.chats.get(a);
                        if (chat.id == dialogsLoadOffsetChannelId) {
                            dialogsLoadOffsetAccess = chat.access_hash;
                            break;
                        }
                    }
                } else if (lastMessage.peer_id.chat_id != 0) {
                    dialogsLoadOffsetChatId = lastMessage.peer_id.chat_id;
                    dialogsLoadOffsetChannelId = 0;
                    dialogsLoadOffsetUserId = 0;
                    for (int a = 0; a < dialogsRes.chats.size(); a++) {
                        TLRPC.Chat chat = dialogsRes.chats.get(a);
                        if (chat.id == dialogsLoadOffsetChatId) {
                            dialogsLoadOffsetAccess = chat.access_hash;
                            break;
                        }
                    }
                } else if (lastMessage.peer_id.user_id != 0) {
                    dialogsLoadOffsetUserId = lastMessage.peer_id.user_id;
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
                getUserConfig().draftsLoaded = false;
                getUserConfig().saveConfig(false);
                getMessagesController().completeDialogsReset(dialogsRes, messagesCount, seq, newPts, date, qts, new_dialogs_dict, new_dialogMessage, lastMessage);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void putDialogPhotos(long did, TLRPC.photos_Photos photos, ArrayList<TLRPC.Message> messages) {
        if (photos == null) {
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
                    int size = photo.getObjectSize();
                    if (messages != null) {
                        size += messages.get(a).getObjectSize();
                    }
                    NativeByteBuffer data = new NativeByteBuffer(size);
                    photo.serializeToStream(data);
                    if (messages != null) {
                        messages.get(a).serializeToStream(data);
                    }
                    state.bindLong(1, did);
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

    public void emptyMessagesMedia(long dialogId, ArrayList<Integer> mids) {
        storageQueue.postRunnable(() -> {
            try {
                ArrayList<File> filesToDelete = new ArrayList<>();
                ArrayList<String> namesToDelete = new ArrayList<>();
                ArrayList<Pair<Long, Integer>> idsToDelete = new ArrayList<>();
                ArrayList<TLRPC.Message> messages = new ArrayList<>();
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid, date, uid FROM messages_v2 WHERE mid IN (%s) AND uid = %d", TextUtils.join(",", mids), dialogId));
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        message.readAttachPath(data, getUserConfig().clientUserId);
                        data.reuse();
                        if (message.media != null) {
                            if (!addFilesToDelete(message, filesToDelete, idsToDelete, namesToDelete, true)) {
                                continue;
                            } else {
                                if (message.media.document != null) {
                                    message.media.document = new TLRPC.TL_documentEmpty();
                                } else if (message.media.photo != null) {
                                    message.media.photo = new TLRPC.TL_photoEmpty();
                                }
                            }
                            message.media.flags = message.media.flags & ~1;
                            message.id = cursor.intValue(1);
                            message.date = cursor.intValue(2);
                            message.dialog_id = cursor.longValue(3);
                            messages.add(message);
                        }
                    }
                }
                cursor.dispose();
                deleteFromDownloadQueue(idsToDelete, true);
                if (!messages.isEmpty()) {
                    SQLitePreparedStatement state = database.executeFast("REPLACE INTO messages_v2 VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, 0)");
                    for (int a = 0; a < messages.size(); a++) {
                        TLRPC.Message message = messages.get(a);

                        NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                        message.serializeToStream(data);

                        state.requery();
                        state.bindInteger(1, message.id);
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
                        int flags = 0;
                        if (message.stickerVerified == 0) {
                            flags |= 1;
                        } else if (message.stickerVerified == 2) {
                            flags |= 2;
                        }
                        state.bindInteger(10, flags);
                        state.bindInteger(11, message.mentioned ? 1 : 0);
                        state.bindInteger(12, message.forwards);
                        NativeByteBuffer repliesData = null;
                        if (message.replies != null) {
                            repliesData = new NativeByteBuffer(message.replies.getObjectSize());
                            message.replies.serializeToStream(repliesData);
                            state.bindByteBuffer(13, repliesData);
                        } else {
                            state.bindNull(13);
                        }
                        if (message.reply_to != null) {
                            state.bindInteger(14, message.reply_to.reply_to_top_id != 0 ? message.reply_to.reply_to_top_id : message.reply_to.reply_to_msg_id);
                        } else {
                            state.bindInteger(14, 0);
                        }
                        state.bindLong(15, MessageObject.getChannelId(message));
                        state.step();

                        data.reuse();
                        if (repliesData != null) {
                            repliesData.reuse();
                        }
                    }
                    state.dispose();
                    AndroidUtilities.runOnUIThread(() -> {
                        for (int a = 0; a < messages.size(); a++) {
                            getNotificationCenter().postNotificationName(NotificationCenter.updateMessageMedia, messages.get(a));
                        }
                    });
                }
                AndroidUtilities.runOnUIThread(() -> getFileLoader().cancelLoadFiles(namesToDelete));
                getFileLoader().deleteFiles(filesToDelete, 0);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void updateMessagePollResults(long pollId, TLRPC.Poll poll, TLRPC.PollResults results) {
        storageQueue.postRunnable(() -> {
            try {
                LongSparseArray<ArrayList<Integer>> dialogs = null;
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, mid FROM polls_v2 WHERE id = %d", pollId));
                while (cursor.next()) {
                    long dialogId = cursor.longValue(0);
                    if (dialogs == null) {
                        dialogs = new LongSparseArray<>();
                    }
                    ArrayList<Integer> mids = dialogs.get(dialogId);
                    if (mids == null) {
                        mids = new ArrayList<>();
                        dialogs.put(dialogId, mids);
                    }
                    mids.add(cursor.intValue(1));
                }
                cursor.dispose();
                if (dialogs != null) {
                    database.beginTransaction();
                    SQLitePreparedStatement state = database.executeFast("UPDATE messages_v2 SET data = ? WHERE mid = ? AND uid = ?");
                    for (int b = 0, N2 = dialogs.size(); b < N2; b++) {
                        long dialogId = dialogs.keyAt(b);
                        ArrayList<Integer> mids = dialogs.valueAt(b);
                        for (int a = 0, N = mids.size(); a < N; a++) {
                            Integer mid = mids.get(a);
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM messages_v2 WHERE mid = %d AND uid = %d", mid, dialogId));
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

                                        data = new NativeByteBuffer(message.getObjectSize());
                                        message.serializeToStream(data);
                                        state.requery();
                                        state.bindByteBuffer(1, data);
                                        state.bindInteger(2, mid);
                                        state.bindLong(3, dialogId);
                                        state.step();
                                        data.reuse();
                                    }
                                }
                            } else {
                                database.executeFast(String.format(Locale.US, "DELETE FROM polls_v2 WHERE mid = %d AND uid = %d", mid, dialogId)).stepThis().dispose();
                            }
                            cursor.dispose();
                        }
                    }
                    state.dispose();
                    database.commitTransaction();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void updateMessageReactions(long dialogId, int msgId, TLRPC.TL_messageReactions reactions) {
        storageQueue.postRunnable(() -> {
            try {
                database.beginTransaction();
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM messages_v2 WHERE mid = %d AND uid = %d", msgId, dialogId));
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        if (message != null) {
                            message.readAttachPath(data, getUserConfig().clientUserId);
                            data.reuse();
                            MessageObject.updateReactions(message, reactions);
                            SQLitePreparedStatement state = database.executeFast("UPDATE messages_v2 SET data = ? WHERE mid = ? AND uid = ?");
                            NativeByteBuffer data2 = new NativeByteBuffer(message.getObjectSize());
                            message.serializeToStream(data2);
                            state.requery();
                            state.bindByteBuffer(1, data2);
                            state.bindInteger(2, msgId);
                            state.bindLong(3, dialogId);
                            state.step();
                            data2.reuse();
                            state.dispose();
                        } else {
                            data.reuse();
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

    public void getNewTask(LongSparseArray<ArrayList<Integer>> oldTask, LongSparseArray<ArrayList<Integer>> oldTaskMedia) {
        storageQueue.postRunnable(() -> {
            try {
                if (oldTask != null) {
                    for (int a = 0, N = oldTask.size(); a < N; a++) {
                        database.executeFast(String.format(Locale.US, "DELETE FROM enc_tasks_v4 WHERE mid IN(%s) AND uid = %d AND media = 0", TextUtils.join(",", oldTask.valueAt(a)), oldTask.keyAt(a))).stepThis().dispose();
                    }
                }
                if (oldTaskMedia != null) {
                    for (int a = 0, N = oldTaskMedia.size(); a < N; a++) {
                        database.executeFast(String.format(Locale.US, "DELETE FROM enc_tasks_v4 WHERE mid IN(%s) AND uid = %d AND media = 1", TextUtils.join(",", oldTaskMedia.valueAt(a)), oldTaskMedia.keyAt(a))).stepThis().dispose();
                    }
                }
                int date = 0;
                LongSparseArray<ArrayList<Integer>> newTask = null;
                LongSparseArray<ArrayList<Integer>> newTaskMedia = null;
                SQLiteCursor cursor = database.queryFinalized("SELECT mid, date, media, uid FROM enc_tasks_v4 WHERE date = (SELECT min(date) FROM enc_tasks_v4)");
                while (cursor.next()) {
                    int mid = cursor.intValue(0);
                    date = cursor.intValue(1);
                    int isMedia = cursor.intValue(2);
                    long uid = cursor.longValue(3);
                    boolean media;
                    if (isMedia == -1) {
                        media = mid > 0;
                    } else {
                        media = isMedia != 0;
                    }
                    LongSparseArray<ArrayList<Integer>> task;
                    if (media) {
                        if (newTaskMedia == null) {
                            newTaskMedia = new LongSparseArray<>();
                        }
                        task = newTaskMedia;
                    } else {
                        if (newTask == null) {
                            newTask = new LongSparseArray<>();
                        }
                        task = newTask;
                    }
                    ArrayList<Integer> arr = task.get(uid);
                    if (arr == null) {
                        arr = new ArrayList<>();
                        task.put(uid, arr);
                    }
                    arr.add(mid);
                }
                cursor.dispose();
                getMessagesController().processLoadedDeleteTask(date, newTask, newTaskMedia);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void markMentionMessageAsRead(long dialogId, int messageId, long did) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast(String.format(Locale.US, "UPDATE messages_v2 SET read_state = read_state | 2 WHERE mid = %d AND uid = %d", messageId, dialogId)).stepThis().dispose();

                SQLiteCursor cursor = database.queryFinalized("SELECT unread_count_i FROM dialogs WHERE did = " + did);
                int old_mentions_count = 0;
                if (cursor.next()) {
                    old_mentions_count = Math.max(0, cursor.intValue(0) - 1);
                }
                cursor.dispose();
                database.executeFast(String.format(Locale.US, "UPDATE dialogs SET unread_count_i = %d WHERE did = %d", old_mentions_count, did)).stepThis().dispose();
                LongSparseIntArray sparseArray = new LongSparseIntArray(1);
                sparseArray.put(did, old_mentions_count);
                if (old_mentions_count == 0) {
                    updateFiltersReadCounter(null, sparseArray, true);
                }
                getMessagesController().processDialogsUpdateRead(null, sparseArray);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void markMessageAsMention(long dialogId, int mid) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast(String.format(Locale.US, "UPDATE messages_v2 SET mention = 1, read_state = read_state & ~2 WHERE mid = %d AND uid = %d", mid, dialogId)).stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void resetMentionsCount(long did, int count) {
        storageQueue.postRunnable(() -> {
            try {
                int prevUnreadCount = 0;
                SQLiteCursor cursor = database.queryFinalized("SELECT unread_count_i FROM dialogs WHERE did = " + did);
                if (cursor.next()) {
                    prevUnreadCount = cursor.intValue(0);
                }
                cursor.dispose();
                if (prevUnreadCount != 0 || count != 0) {
                    if (count == 0) {
                        database.executeFast(String.format(Locale.US, "UPDATE messages_v2 SET read_state = read_state | 2 WHERE uid = %d AND mention = 1 AND read_state IN(0, 1)", did)).stepThis().dispose();
                    }
                    database.executeFast(String.format(Locale.US, "UPDATE dialogs SET unread_count_i = %d WHERE did = %d", count, did)).stepThis().dispose();
                    LongSparseIntArray sparseArray = new LongSparseIntArray(1);
                    sparseArray.put(did, count);
                    getMessagesController().processDialogsUpdateRead(null, sparseArray);
                    if (count == 0) {
                        updateFiltersReadCounter(null, sparseArray, true);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void createTaskForMid(long dialogId, int messageId, int time, int readTime, int ttl, boolean inner) {
        storageQueue.postRunnable(() -> {
            try {
                int minDate = Math.max(time, readTime) + ttl;
                SparseArray<ArrayList<Integer>> messages = new SparseArray<>();
                ArrayList<Integer> midsArray = new ArrayList<>();

                midsArray.add(messageId);
                messages.put(minDate, midsArray);

                AndroidUtilities.runOnUIThread(() -> {
                    if (!inner) {
                        markMessagesContentAsRead(dialogId, midsArray, 0);
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.messagesReadContent, dialogId, midsArray);
                });

                SQLitePreparedStatement state = database.executeFast("REPLACE INTO enc_tasks_v4 VALUES(?, ?, ?, ?)");
                for (int a = 0; a < messages.size(); a++) {
                    int key = messages.keyAt(a);
                    ArrayList<Integer> arr = messages.get(key);
                    for (int b = 0; b < arr.size(); b++) {
                        state.requery();
                        state.bindInteger(1, arr.get(b));
                        state.bindLong(2, dialogId);
                        state.bindInteger(3, key);
                        state.bindInteger(4, 1);
                        state.step();
                    }
                }
                state.dispose();
                database.executeFast(String.format(Locale.US, "UPDATE messages_v2 SET ttl = 0 WHERE mid = %d AND uid = %d", messageId, dialogId)).stepThis().dispose();
                getMessagesController().didAddedNewTask(minDate, dialogId, messages);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void createTaskForSecretChat(int chatId, int time, int readTime, int isOut, ArrayList<Long> random_ids) {
        storageQueue.postRunnable(() -> {
            try {
                long dialogId = DialogObject.makeEncryptedDialogId(chatId);
                int minDate = Integer.MAX_VALUE;
                SparseArray<ArrayList<Integer>> messages = new SparseArray<>();
                ArrayList<Integer> midsArray = new ArrayList<>();
                StringBuilder mids = new StringBuilder();
                SQLiteCursor cursor;
                if (random_ids == null) {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid, ttl FROM messages_v2 WHERE uid = %d AND out = %d AND read_state > 0 AND ttl > 0 AND date <= %d AND send_state = 0 AND media != 1", dialogId, isOut, time));
                } else {
                    String ids = TextUtils.join(",", random_ids);
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.mid, m.ttl FROM messages_v2 as m INNER JOIN randoms_v2 as r ON m.mid = r.mid AND m.uid = r.uid WHERE r.random_id IN (%s)", ids));
                }
                while (cursor.next()) {
                    int ttl = cursor.intValue(1);
                    int mid = cursor.intValue(0);
                    if (random_ids != null) {
                        midsArray.add(mid);
                    }
                    if (ttl <= 0) {
                        continue;
                    }
                    int date = Math.max(time, readTime) + ttl;
                    minDate = Math.min(minDate, date);
                    ArrayList<Integer> arr = messages.get(date);
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
                        markMessagesContentAsRead(dialogId, midsArray, 0);
                        getNotificationCenter().postNotificationName(NotificationCenter.messagesReadContent, dialogId, midsArray);
                    });
                }

                if (messages.size() != 0) {
                    database.beginTransaction();
                    SQLitePreparedStatement state = database.executeFast("REPLACE INTO enc_tasks_v4 VALUES(?, ?, ?, ?)");
                    for (int a = 0; a < messages.size(); a++) {
                        int key = messages.keyAt(a);
                        ArrayList<Integer> arr = messages.get(key);
                        for (int b = 0; b < arr.size(); b++) {
                            state.requery();
                            state.bindInteger(1, arr.get(b));
                            state.bindLong(2, dialogId);
                            state.bindInteger(3, key);
                            state.bindInteger(4, 0);
                            state.step();
                        }
                    }
                    state.dispose();
                    database.commitTransaction();
                    database.executeFast(String.format(Locale.US, "UPDATE messages_v2 SET ttl = 0 WHERE mid IN(%s) AND uid = %d", mids.toString(), dialogId)).stepThis().dispose();
                    getMessagesController().didAddedNewTask(minDate, dialogId, messages);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void updateFiltersReadCounter(LongSparseIntArray dialogsToUpdate, LongSparseIntArray dialogsToUpdateMentions, boolean read) throws Exception {
        if ((dialogsToUpdate == null || dialogsToUpdate.size() == 0) && (dialogsToUpdateMentions == null || dialogsToUpdateMentions.size() == 0)) {
            return;
        }
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                contacts[a][b] = nonContacts[a][b] = bots[a][b] = channels[a][b] = groups[a][b] = 0;
            }
            mentionChannels[a] = mentionGroups[a] = 0;
        }

        ArrayList<TLRPC.User> users = new ArrayList<>();
        ArrayList<TLRPC.User> encUsers = new ArrayList<>();
        ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        ArrayList<Long> usersToLoad = new ArrayList<>();
        ArrayList<Long> chatsToLoad = new ArrayList<>();
        ArrayList<Integer> encryptedToLoad = new ArrayList<>();
        LongSparseArray<Integer> dialogsByFolders = new LongSparseArray<>();
        LongSparseArray<Integer> newUnreadDialogs = new LongSparseArray<>();

        for (int b = 0; b < 2; b++) {
            LongSparseIntArray array = b == 0 ? dialogsToUpdate : dialogsToUpdateMentions;
            if (array == null) {
                continue;
            }
            for (int a = 0; a < array.size(); a++) {
                Integer count = array.valueAt(a);
                if (read && count != 0 || !read && count == 0) {
                    continue;
                }
                long did = array.keyAt(a);
                if (read) {
                    if (b == 0) {
                        dialogsWithUnread.remove(did);
                        /*if (BuildVars.DEBUG_VERSION) {
                            FileLog.d("read remove = " + did);
                        }*/
                    } else {
                        dialogsWithMentions.remove(did);
                        /*if (BuildVars.DEBUG_VERSION) {
                            FileLog.d("mention remove = " + did);
                        }*/
                    }
                } else {
                    if (dialogsWithMentions.indexOfKey(did) < 0 && dialogsWithUnread.indexOfKey(did) < 0) {
                        newUnreadDialogs.put(did, count);
                    }
                    if (b == 0) {
                        dialogsWithUnread.put(did, count);
                        /*if (BuildVars.DEBUG_VERSION) {
                            FileLog.d("read add = " + did);
                        }*/
                    } else {
                        dialogsWithMentions.put(did, count);
                        /*if (BuildVars.DEBUG_VERSION) {
                            FileLog.d("mention add = " + did);
                        }*/
                    }
                }

                if (dialogsByFolders.indexOfKey(did) < 0) {
                    SQLiteCursor cursor = database.queryFinalized("SELECT folder_id FROM dialogs WHERE did = " + did);
                    int folderId = 0;
                    if (cursor.next()) {
                        folderId = cursor.intValue(0);
                    }
                    cursor.dispose();
                    dialogsByFolders.put(did, folderId);
                }

                if (DialogObject.isEncryptedDialog(did)) {
                    int encryptedChatId = DialogObject.getEncryptedChatId(did);
                    if (!encryptedToLoad.contains(encryptedChatId)) {
                        encryptedToLoad.add(encryptedChatId);
                    }
                } else if (DialogObject.isUserDialog(did)) {
                    if (!usersToLoad.contains(did)) {
                        usersToLoad.add(did);
                    }
                } else {
                    if (!chatsToLoad.contains(-did)) {
                        chatsToLoad.add(-did);
                    }
                }
            }
        }
        LongSparseArray<TLRPC.User> usersDict = new LongSparseArray<>();
        LongSparseArray<TLRPC.Chat> chatsDict = new LongSparseArray<>();
        LongSparseArray<TLRPC.User> encUsersDict = new LongSparseArray<>();
        LongSparseArray<Integer> encryptedChatsByUsersCount = new LongSparseArray<>();
        LongSparseArray<Boolean> mutedDialogs = new LongSparseArray<>();
        LongSparseArray<Boolean> archivedDialogs = new LongSparseArray<>();
        if (!usersToLoad.isEmpty()) {
            getUsersInternal(TextUtils.join(",", usersToLoad), users);
            for (int a = 0, N = users.size(); a < N; a++) {
                TLRPC.User user = users.get(a);
                boolean muted = getMessagesController().isDialogMuted(user.id);
                int idx1 = dialogsByFolders.get(user.id);
                int idx2 = muted ? 1 : 0;
                if (muted) {
                    mutedDialogs.put(user.id, true);
                }
                if (idx1 == 1) {
                    archivedDialogs.put(user.id, true);
                }
                if (user.bot) {
                    bots[idx1][idx2]++;
                } else if (user.self || user.contact) {
                    contacts[idx1][idx2]++;
                } else {
                    nonContacts[idx1][idx2]++;
                }
                usersDict.put(user.id, user);
            }
        }
        if (!encryptedToLoad.isEmpty()) {
            ArrayList<Long> encUsersToLoad = new ArrayList<>();
            ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
            getEncryptedChatsInternal(TextUtils.join(",", encryptedToLoad), encryptedChats, encUsersToLoad);
            if (!encUsersToLoad.isEmpty()) {
                getUsersInternal(TextUtils.join(",", encUsersToLoad), encUsers);
                for (int a = 0, N = encUsers.size(); a < N; a++) {
                    TLRPC.User user = encUsers.get(a);
                    encUsersDict.put(user.id, user);
                }
                for (int a = 0, N = encryptedChats.size(); a < N; a++) {
                    TLRPC.EncryptedChat encryptedChat = encryptedChats.get(a);
                    TLRPC.User user = encUsersDict.get(encryptedChat.user_id);
                    if (user == null) {
                        continue;
                    }
                    long did = DialogObject.makeEncryptedDialogId(encryptedChat.id);
                    boolean muted = getMessagesController().isDialogMuted(did);
                    int idx1 = dialogsByFolders.get(did);
                    int idx2 = muted ? 1 : 0;
                    if (muted) {
                        mutedDialogs.put(user.id, true);
                    }
                    if (idx1 == 1) {
                        archivedDialogs.put(user.id, true);
                    }
                    if (user.self || user.contact) {
                        contacts[idx1][idx2]++;
                    } else {
                        nonContacts[idx1][idx2]++;
                    }
                    int count = encryptedChatsByUsersCount.get(user.id, 0);
                    encryptedChatsByUsersCount.put(user.id, count + 1);
                }
            }
        }
        if (!chatsToLoad.isEmpty()) {
            getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
            for (int a = 0, N = chats.size(); a < N; a++) {
                TLRPC.Chat chat = chats.get(a);
                if (chat.migrated_to instanceof TLRPC.TL_inputChannel || ChatObject.isNotInChat(chat)) {
                    continue;
                }
                boolean muted = getMessagesController().isDialogMuted(-chat.id, chat);
                boolean hasUnread = dialogsWithUnread.indexOfKey(-chat.id) >= 0;
                boolean hasMention = dialogsWithMentions.indexOfKey(-chat.id) >= 0;
                int idx1 = dialogsByFolders.get(-chat.id);
                int idx2 = muted ? 1 : 0;
                if (muted) {
                    mutedDialogs.put(-chat.id, true);
                }
                if (idx1 == 1) {
                    archivedDialogs.put(-chat.id, true);
                }
                if (muted && dialogsToUpdateMentions != null && dialogsToUpdateMentions.indexOfKey(-chat.id) >= 0) {
                    if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        mentionChannels[idx1]++;
                    } else {
                        mentionGroups[idx1]++;
                    }
                }
                if (read && !hasUnread && !hasMention || !read && newUnreadDialogs.indexOfKey(-chat.id) >= 0) {
                    if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        channels[idx1][idx2]++;
                    } else {
                        groups[idx1][idx2]++;
                    }
                }
                chatsDict.put(chat.id, chat);
            }
        }
        /*if (BuildVars.DEBUG_VERSION) {
            for (int b = 0; b < 2; b++) {
                FileLog.d("read = " + read + " contacts = " + contacts[b][0] + ", " + contacts[b][1]);
                FileLog.d("read = " + read + " nonContacts = " + nonContacts[b][0] + ", " + nonContacts[b][1]);
                FileLog.d("read = " + read + " groups = " + groups[b][0] + ", " + groups[b][1]);
                FileLog.d("read = " + read + " channels = " + channels[b][0] + ", " + channels[b][1]);
                FileLog.d("read = " + read + " bots = " + bots[b][0] + ", " + bots[b][1]);
            }
        }*/

        for (int a = 0, N = dialogFilters.size(); a < N + 2; a++) {
            int unreadCount;
            MessagesController.DialogFilter filter;
            int flags;
            if (a < N) {
                filter = dialogFilters.get(a);
                if (filter.pendingUnreadCount < 0) {
                    continue;
                }
                unreadCount = filter.pendingUnreadCount;
                flags = filter.flags;
            } else {
                filter = null;
                flags = MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS;
                if (a == N) {
                    unreadCount = pendingMainUnreadCount;
                    flags |= MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED;
                    if (!getNotificationsController().showBadgeMuted) {
                        flags |= MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED;
                    }
                } else {
                    unreadCount = pendingArchiveUnreadCount;
                    flags |= MessagesController.DIALOG_FILTER_FLAG_ONLY_ARCHIVED;
                }
            }
            if (read) {
                if ((flags & MessagesController.DIALOG_FILTER_FLAG_CONTACTS) != 0) {
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_ONLY_ARCHIVED) == 0) {
                        unreadCount -= contacts[0][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount -= contacts[0][1];
                        }
                    }
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) == 0) {
                        unreadCount -= contacts[1][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount -= contacts[1][1];
                        }
                    }
                }
                if ((flags & MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS) != 0) {
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_ONLY_ARCHIVED) == 0) {
                        unreadCount -= nonContacts[0][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount -= nonContacts[0][1];
                        }
                    }
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) == 0) {
                        unreadCount -= nonContacts[1][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount -= nonContacts[1][1];
                        }
                    }
                }
                if ((flags & MessagesController.DIALOG_FILTER_FLAG_GROUPS) != 0) {
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_ONLY_ARCHIVED) == 0) {
                        unreadCount -= groups[0][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount -= groups[0][1];
                        } else {
                            unreadCount -= mentionGroups[0];
                        }
                    }
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) == 0) {
                        unreadCount -= groups[1][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount -= groups[1][1];
                        } else {
                            unreadCount -= mentionGroups[1];
                        }
                    }
                }
                if ((flags & MessagesController.DIALOG_FILTER_FLAG_CHANNELS) != 0) {
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_ONLY_ARCHIVED) == 0) {
                        unreadCount -= channels[0][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount -= channels[0][1];
                        } else {
                            unreadCount -= mentionChannels[0];
                        }
                    }
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) == 0) {
                        unreadCount -= channels[1][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount -= channels[1][1];
                        } else {
                            unreadCount -= mentionChannels[1];
                        }
                    }
                }
                if ((flags & MessagesController.DIALOG_FILTER_FLAG_BOTS) != 0) {
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_ONLY_ARCHIVED) == 0) {
                        unreadCount -= bots[0][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount -= bots[0][1];
                        }
                    }
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) == 0) {
                        unreadCount -= bots[1][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount -= bots[1][1];
                        }
                    }
                }
                if (filter != null) {
                    for (int b = 0, N2 = filter.alwaysShow.size(); b < N2; b++) {
                        long did = filter.alwaysShow.get(b);
                        if (DialogObject.isUserDialog(did)) {
                            for (int i = 0; i < 2; i++) {
                                LongSparseArray<TLRPC.User> dict = i == 0 ? usersDict : encUsersDict;
                                TLRPC.User user = dict.get(did);
                                if (user != null) {
                                    int count;
                                    if (i == 0) {
                                        count = 1;
                                    } else {
                                        count = encryptedChatsByUsersCount.get(did, 0);
                                        if (count == 0) {
                                            continue;
                                        }
                                    }
                                    int flag;
                                    if (user.bot) {
                                        flag = MessagesController.DIALOG_FILTER_FLAG_BOTS;
                                    } else if (user.self || user.contact) {
                                        flag = MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
                                    } else {
                                        flag = MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
                                    }
                                    if ((flags & flag) == 0) {
                                        unreadCount -= count;
                                    } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) != 0 && mutedDialogs.indexOfKey(user.id) >= 0) {
                                        unreadCount -= count;
                                    } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) != 0 && archivedDialogs.indexOfKey(user.id) >= 0) {
                                        unreadCount -= count;
                                    }
                                }
                            }
                        } else {
                            TLRPC.Chat chat = chatsDict.get(-did);
                            if (chat != null) {
                                int flag;
                                if (ChatObject.isChannel(chat) && !chat.megagroup) {
                                    flag = MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
                                } else {
                                    flag = MessagesController.DIALOG_FILTER_FLAG_GROUPS;
                                }
                                if ((flags & flag) == 0) {
                                    unreadCount--;
                                } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) != 0 && mutedDialogs.indexOfKey(-chat.id) >= 0 && dialogsWithMentions.indexOfKey(-chat.id) < 0) {
                                    unreadCount--;
                                } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) != 0 && archivedDialogs.indexOfKey(-chat.id) >= 0) {
                                    unreadCount--;
                                }
                            }
                        }
                    }
                    for (int b = 0, N2 = filter.neverShow.size(); b < N2; b++) {
                        long did = filter.neverShow.get(b);
                        if (dialogsToUpdateMentions != null && dialogsToUpdateMentions.indexOfKey(did) >= 0 && mutedDialogs.indexOfKey(did) < 0) {
                            continue;
                        }
                        if (DialogObject.isUserDialog(did)) {
                            for (int i = 0; i < 2; i++) {
                                LongSparseArray<TLRPC.User> dict = i == 0 ? usersDict : encUsersDict;
                                TLRPC.User user = dict.get(did);
                                if (user != null) {
                                    int count;
                                    if (i == 0) {
                                        count = 1;
                                    } else {
                                        count = encryptedChatsByUsersCount.get(did, 0);
                                        if (count == 0) {
                                            continue;
                                        }
                                    }
                                    int flag;
                                    if (user.bot) {
                                        flag = MessagesController.DIALOG_FILTER_FLAG_BOTS;
                                    } else if (user.self || user.contact) {
                                        flag = MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
                                    } else {
                                        flag = MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
                                    }
                                    if ((flags & flag) != 0) {
                                        if (((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) == 0 || archivedDialogs.indexOfKey(user.id) < 0) &&
                                                ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0 || mutedDialogs.indexOfKey(user.id) < 0)) {
                                            unreadCount += count;
                                        }
                                    }
                                }
                            }
                        } else {
                            TLRPC.Chat chat = chatsDict.get(-did);
                            if (chat != null) {
                                int flag;
                                if (ChatObject.isChannel(chat) && !chat.megagroup) {
                                    flag = MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
                                } else {
                                    flag = MessagesController.DIALOG_FILTER_FLAG_GROUPS;
                                }
                                if ((flags & flag) != 0) {
                                    if (((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) == 0 || archivedDialogs.indexOfKey(-chat.id) < 0) &&
                                            ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0 || mutedDialogs.indexOfKey(-chat.id) < 0 || dialogsWithMentions.indexOfKey(-chat.id) >= 0)) {
                                        unreadCount++;
                                    }
                                }
                            }
                        }
                    }
                }
                if (unreadCount < 0) {
                    unreadCount = 0;
                }
            } else {
                if ((flags & MessagesController.DIALOG_FILTER_FLAG_CONTACTS) != 0) {
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_ONLY_ARCHIVED) == 0) {
                        unreadCount += contacts[0][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount += contacts[0][1];
                        }
                    }
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) == 0) {
                        unreadCount += contacts[1][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount += contacts[1][1];
                        }
                    }
                }
                if ((flags & MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS) != 0) {
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_ONLY_ARCHIVED) == 0) {
                        unreadCount += nonContacts[0][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount += nonContacts[0][1];
                        }
                    }
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) == 0) {
                        unreadCount += nonContacts[1][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount += nonContacts[1][1];
                        }
                    }
                }
                if ((flags & MessagesController.DIALOG_FILTER_FLAG_GROUPS) != 0) {
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_ONLY_ARCHIVED) == 0) {
                        unreadCount += groups[0][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount += groups[0][1];
                        } else {
                            unreadCount += mentionGroups[0];
                        }
                    }
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) == 0) {
                        unreadCount += groups[1][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount += groups[1][1];
                        } else {
                            unreadCount += mentionGroups[1];
                        }
                    }
                }
                if ((flags & MessagesController.DIALOG_FILTER_FLAG_CHANNELS) != 0) {
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_ONLY_ARCHIVED) == 0) {
                        unreadCount += channels[0][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount += channels[0][1];
                        } else {
                            unreadCount += mentionChannels[0];
                        }
                    }
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) == 0) {
                        unreadCount += channels[1][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount += channels[1][1];
                        } else {
                            unreadCount += mentionChannels[1];
                        }
                    }
                }
                if ((flags & MessagesController.DIALOG_FILTER_FLAG_BOTS) != 0) {
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_ONLY_ARCHIVED) == 0) {
                        unreadCount += bots[0][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount += bots[0][1];
                        }
                    }
                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) == 0) {
                        unreadCount += bots[1][0];
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) == 0) {
                            unreadCount += bots[1][1];
                        }
                    }
                }
                if (filter != null) {
                    if (!filter.alwaysShow.isEmpty()) {
                        if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) != 0) {
                            for (int b = 0, N2 = dialogsToUpdateMentions.size(); b < N2; b++) {
                                long did = dialogsToUpdateMentions.keyAt(b);
                                TLRPC.Chat chat = chatsDict.get(-did);
                                if (ChatObject.isChannel(chat) && !chat.megagroup) {
                                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_CHANNELS) == 0) {
                                        continue;
                                    }
                                } else {
                                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_GROUPS) == 0) {
                                        continue;
                                    }
                                }
                                if (mutedDialogs.indexOfKey(did) >= 0 && filter.alwaysShow.contains(did)) {
                                    unreadCount--;
                                }
                            }
                        }
                        for (int b = 0, N2 = filter.alwaysShow.size(); b < N2; b++) {
                            long did = filter.alwaysShow.get(b);
                            if (newUnreadDialogs.indexOfKey(did) < 0) {
                                continue;
                            }
                            if (DialogObject.isUserDialog(did)) {
                                TLRPC.User user = usersDict.get(did);
                                if (user != null) {
                                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) != 0 && mutedDialogs.indexOfKey(user.id) >= 0) {
                                        unreadCount++;
                                    } else {
                                        if (user.bot) {
                                            if ((flags & MessagesController.DIALOG_FILTER_FLAG_BOTS) == 0) {
                                                unreadCount++;
                                            }
                                        } else if (user.self || user.contact) {
                                            if ((flags & MessagesController.DIALOG_FILTER_FLAG_CONTACTS) == 0) {
                                                unreadCount++;
                                            }
                                        } else {
                                            if ((flags & MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS) == 0) {
                                                unreadCount++;
                                            }
                                        }
                                    }
                                }
                                user = encUsersDict.get(did);
                                if (user != null) {
                                    int count = encryptedChatsByUsersCount.get(did, 0);
                                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) != 0 && mutedDialogs.indexOfKey(user.id) >= 0) {
                                        unreadCount += count;
                                    } else {
                                        if (user.bot) {
                                            if ((flags & MessagesController.DIALOG_FILTER_FLAG_BOTS) == 0) {
                                                unreadCount += count;
                                            }
                                        } else if (user.self || user.contact) {
                                            if ((flags & MessagesController.DIALOG_FILTER_FLAG_CONTACTS) == 0) {
                                                unreadCount += count;
                                            }
                                        } else {
                                            if ((flags & MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS) == 0) {
                                                unreadCount += count;
                                            }
                                        }
                                    }
                                }
                            } else {
                                TLRPC.Chat chat = chatsDict.get(-did);
                                if (chat != null) {
                                    if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) != 0 && mutedDialogs.indexOfKey(-chat.id) >= 0) {
                                        unreadCount++;
                                    } else {
                                        if (ChatObject.isChannel(chat) && !chat.megagroup) {
                                            if ((flags & MessagesController.DIALOG_FILTER_FLAG_CHANNELS) == 0) {
                                                unreadCount++;
                                            }
                                        } else {
                                            if ((flags & MessagesController.DIALOG_FILTER_FLAG_GROUPS) == 0) {
                                                unreadCount++;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    for (int b = 0, N2 = filter.neverShow.size(); b < N2; b++) {
                        long did = filter.neverShow.get(b);
                        if (DialogObject.isUserDialog(did)) {
                            TLRPC.User user = usersDict.get(did);
                            if (user != null) {
                                unreadCount--;
                            }
                            user = encUsersDict.get(did);
                            if (user != null) {
                                unreadCount -= encryptedChatsByUsersCount.get(did, 0);
                            }
                        } else {
                            TLRPC.Chat chat = chatsDict.get(-did);
                            if (chat != null) {
                                unreadCount--;
                            }
                        }
                    }
                }
            }
            if (filter != null) {
                filter.pendingUnreadCount = unreadCount;
                /*if (BuildVars.DEBUG_VERSION) {
                    FileLog.d("filter " + filter.name + " flags = " + flags + " read = " + read + " unread count = " + filter.pendingUnreadCount);
                }*/
            } else if (a == N) {
                pendingMainUnreadCount = unreadCount;
            } else if (a == N + 1) {
                pendingArchiveUnreadCount = unreadCount;
            }
        }
        AndroidUtilities.runOnUIThread(() -> {
            ArrayList<MessagesController.DialogFilter> filters = getMessagesController().dialogFilters;
            for (int a = 0, N = filters.size(); a < N; a++) {
                filters.get(a).unreadCount = filters.get(a).pendingUnreadCount;
            }
            mainUnreadCount = pendingMainUnreadCount;
            archiveUnreadCount = pendingArchiveUnreadCount;
        });
    }

    private void updateDialogsWithReadMessagesInternal(ArrayList<Integer> messages, LongSparseIntArray inbox, LongSparseIntArray outbox, LongSparseArray<ArrayList<Integer>> mentions) {
        try {
            LongSparseIntArray dialogsToUpdate = new LongSparseIntArray();
            LongSparseIntArray dialogsToUpdateMentions = new LongSparseIntArray();
            ArrayList<Long> channelMentionsToReload = new ArrayList<>();

            if (!isEmpty(messages)) {
                String ids = TextUtils.join(",", messages);
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, read_state, out FROM messages_v2 WHERE mid IN(%s) AND is_channel = 0", ids));
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
                    int currentCount = dialogsToUpdate.get(uid);
                    if (currentCount == 0) {
                        dialogsToUpdate.put(uid, 1);
                    } else {
                        dialogsToUpdate.put(uid, currentCount + 1);
                    }
                }
                cursor.dispose();
            } else {
                if (!isEmpty(inbox)) {
                    for (int b = 0; b < inbox.size(); b++) {
                        long key = inbox.keyAt(b);
                        int messageId = inbox.get(key);
                        SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM messages_v2 WHERE uid = %d AND mid > %d AND read_state IN(0,2) AND out = 0", key, messageId));
                        if (cursor.next()) {
                            dialogsToUpdate.put(key, cursor.intValue(0));
                        }
                        cursor.dispose();

                        SQLitePreparedStatement state = database.executeFast("UPDATE dialogs SET inbox_max = max((SELECT inbox_max FROM dialogs WHERE did = ?), ?) WHERE did = ?");
                        state.requery();
                        state.bindLong(1, key);
                        state.bindInteger(2, messageId);
                        state.bindLong(3, key);
                        state.step();
                        state.dispose();
                    }
                }
                if (!isEmpty(mentions)) {
                    for (int b = 0, N = mentions.size(); b < N; b++) {
                        ArrayList<Integer> arrayList = mentions.valueAt(b);
                        ArrayList<Integer> notFoundMentions = new ArrayList<>(arrayList);
                        String ids = TextUtils.join(",", arrayList);
                        long channelId = 0;
                        SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, read_state, out, mention, mid, is_channel FROM messages_v2 WHERE mid IN(%s)", ids));
                        while (cursor.next()) {
                            long did = cursor.longValue(0);
                            notFoundMentions.remove((Integer) cursor.intValue(4));
                            if (cursor.intValue(1) < 2 && cursor.intValue(2) == 0 && cursor.intValue(3) == 1) {
                                int unread_count = dialogsToUpdateMentions.get(did, -1);
                                if (unread_count < 0) {
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
                            channelId = cursor.longValue(5);
                        }
                        cursor.dispose();
                        if (!notFoundMentions.isEmpty() && channelId != 0) {
                            if (!channelMentionsToReload.contains(channelId)) {
                                channelMentionsToReload.add(channelId);
                            }
                        }
                    }
                }
                if (!isEmpty(outbox)) {
                    for (int b = 0; b < outbox.size(); b++) {
                        long key = outbox.keyAt(b);
                        int messageId = outbox.get(key);
                        SQLitePreparedStatement state = database.executeFast("UPDATE dialogs SET outbox_max = max((SELECT outbox_max FROM dialogs WHERE did = ?), ?) WHERE did = ?");
                        state.requery();
                        state.bindLong(1, key);
                        state.bindInteger(2, messageId);
                        state.bindLong(3, key);
                        state.step();
                        state.dispose();
                    }
                }
            }

            if (dialogsToUpdate.size() > 0 || dialogsToUpdateMentions.size() > 0) {
                database.beginTransaction();
                if (dialogsToUpdate.size() > 0) {
                    ArrayList<Long> dids = new ArrayList<>();
                    SQLitePreparedStatement state = database.executeFast("UPDATE dialogs SET unread_count = ? WHERE did = ?");
                    for (int a = 0; a < dialogsToUpdate.size(); a++) {
                        long did = dialogsToUpdate.keyAt(a);
                        int prevUnreadCount = 0;
                        int newCount = dialogsToUpdate.valueAt(a);
                        SQLiteCursor cursor = database.queryFinalized("SELECT unread_count FROM dialogs WHERE did = " + did);
                        if (cursor.next()) {
                            prevUnreadCount = cursor.intValue(0);
                        }
                        cursor.dispose();
                        if (prevUnreadCount == newCount) {
                            dialogsToUpdate.removeAt(a);
                            a--;
                            continue;
                        }

                        state.requery();
                        state.bindInteger(1, newCount);
                        state.bindLong(2, did);
                        state.step();
                        dids.add(did);
                    }
                    state.dispose();
                    updateWidgets(dids);
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
            updateFiltersReadCounter(dialogsToUpdate, dialogsToUpdateMentions, true);

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

    private static boolean isEmpty(LongSparseIntArray array) {
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

    public void updateDialogsWithReadMessages(LongSparseIntArray inbox, LongSparseIntArray outbox, LongSparseArray<ArrayList<Integer>> mentions, boolean useQueue) {
        if (isEmpty(inbox) && isEmpty(outbox) && isEmpty(mentions)) {
            return;
        }
        if (useQueue) {
            storageQueue.postRunnable(() -> updateDialogsWithReadMessagesInternal(null, inbox, outbox, mentions));
        } else {
            updateDialogsWithReadMessagesInternal(null, inbox, outbox, mentions);
        }
    }

    public void updateChatParticipants(TLRPC.ChatParticipants participants) {
        if (participants == null) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                SQLiteCursor cursor = database.queryFinalized("SELECT info, pinned, online, inviter FROM chat_settings_v2 WHERE uid = " + participants.chat_id);
                TLRPC.ChatFull info = null;
                ArrayList<TLRPC.User> loadedUsers = new ArrayList<>();
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        info = TLRPC.ChatFull.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                        info.pinned_msg_id = cursor.intValue(1);
                        info.online_count = cursor.intValue(2);
                        info.inviterId = cursor.longValue(3);
                    }
                }
                cursor.dispose();
                if (info instanceof TLRPC.TL_chatFull) {
                    info.participants = participants;
                    TLRPC.ChatFull finalInfo = info;
                    AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, finalInfo, 0, false, false));

                    SQLitePreparedStatement state = database.executeFast("REPLACE INTO chat_settings_v2 VALUES(?, ?, ?, ?, ?, ?)");
                    NativeByteBuffer data = new NativeByteBuffer(info.getObjectSize());
                    info.serializeToStream(data);
                    state.bindLong(1, info.id);
                    state.bindByteBuffer(2, data);
                    state.bindInteger(3, info.pinned_msg_id);
                    state.bindInteger(4, info.online_count);
                    state.bindLong(5, info.inviterId);
                    state.bindInteger(6, info.invitesCount);
                    state.step();
                    state.dispose();
                    data.reuse();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void loadChannelAdmins(long chatId) {
        storageQueue.postRunnable(() -> {
            try {
                SQLiteCursor cursor = database.queryFinalized("SELECT uid, data FROM channel_admins_v3 WHERE did = " + chatId);
                LongSparseArray<TLRPC.ChannelParticipant> ids = new LongSparseArray<>();
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(1);
                    if (data != null) {
                        TLRPC.ChannelParticipant participant = TLRPC.ChannelParticipant.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                        if (participant != null) {
                            ids.put(cursor.longValue(0), participant);
                        }
                    }
                }
                cursor.dispose();
                getMessagesController().processLoadedChannelAdmins(ids, chatId, true);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void putChannelAdmins(long chatId, LongSparseArray<TLRPC.ChannelParticipant> ids) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM channel_admins_v3 WHERE did = " + chatId).stepThis().dispose();
                database.beginTransaction();
                SQLitePreparedStatement state = database.executeFast("REPLACE INTO channel_admins_v3 VALUES(?, ?, ?)");
                int date = (int) (System.currentTimeMillis() / 1000);
                NativeByteBuffer data;
                for (int a = 0; a < ids.size(); a++) {
                    state.requery();
                    state.bindLong(1, chatId);
                    state.bindLong(2, ids.keyAt(a));
                    TLRPC.ChannelParticipant participant = ids.valueAt(a);
                    data = new NativeByteBuffer(participant.getObjectSize());
                    participant.serializeToStream(data);
                    state.bindByteBuffer(3, data);
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

    public void updateChannelUsers(long channelId, ArrayList<TLRPC.ChannelParticipant> participants) {
        storageQueue.postRunnable(() -> {
            try {
                long did = -channelId;
                database.executeFast("DELETE FROM channel_users_v2 WHERE did = " + did).stepThis().dispose();
                database.beginTransaction();
                SQLitePreparedStatement state = database.executeFast("REPLACE INTO channel_users_v2 VALUES(?, ?, ?, ?)");
                NativeByteBuffer data;
                int date = (int) (System.currentTimeMillis() / 1000);
                for (int a = 0; a < participants.size(); a++) {
                    TLRPC.ChannelParticipant participant = participants.get(a);
                    state.requery();
                    state.bindLong(1, did);
                    state.bindLong(2, MessageObject.getPeerId(participant.peer));
                    state.bindInteger(3, date);
                    data = new NativeByteBuffer(participant.getObjectSize());
                    participant.serializeToStream(data);
                    state.bindByteBuffer(4, data);
                    state.step();
                    data.reuse();
                    date--;
                }
                state.dispose();
                database.commitTransaction();
                loadChatInfo(channelId, true, null, false, true);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void saveBotCache(String key, TLObject result) {
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

    public void getBotCache(String key, RequestDelegate requestDelegate) {
        if (key == null || requestDelegate == null) {
            return;
        }
        int currentDate = getConnectionsManager().getCurrentTime();
        storageQueue.postRunnable(() -> {
            TLObject result = null;
            try {
                database.executeFast("DELETE FROM botcache WHERE date < " + currentDate).stepThis().dispose();
                SQLiteCursor cursor = database.queryFinalized("SELECT data FROM botcache WHERE id = ?", key);
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

    public void loadUserInfo(TLRPC.User user, boolean force, int classGuid, int fromMessageId) {
        if (user == null) {
            return;
        }
        storageQueue.postRunnable(() -> {
            HashMap<Integer, MessageObject> pinnedMessagesMap = new HashMap<>();
            ArrayList<Integer> pinnedMessages = new ArrayList<>();
            int totalPinnedCount = 0;
            boolean pinnedEndReached = false;

            TLRPC.UserFull info = null;
            try {
                SQLiteCursor cursor = database.queryFinalized("SELECT info, pinned FROM user_settings WHERE uid = " + user.id);
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        info = TLRPC.UserFull.TLdeserialize(data, data.readInt32(false), false);
                        info.pinned_msg_id = cursor.intValue(1);
                        data.reuse();
                    }
                }
                cursor.dispose();

                cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT mid FROM chat_pinned_v2 WHERE uid = %d ORDER BY mid DESC", user.id));
                while (cursor.next()) {
                    int id = cursor.intValue(0);
                    pinnedMessages.add(id);
                    pinnedMessagesMap.put(id, null);
                }
                cursor.dispose();

                cursor = database.queryFinalized("SELECT count, end FROM chat_pinned_count WHERE uid = " + user.id);
                if (cursor.next()) {
                    totalPinnedCount = cursor.intValue(0);
                    pinnedEndReached = cursor.intValue(1) != 0;
                }
                cursor.dispose();

                if (info != null && info.pinned_msg_id != 0) {
                    if (pinnedMessages.isEmpty() || info.pinned_msg_id > pinnedMessages.get(0)) {
                        pinnedMessages.clear();
                        pinnedMessages.add(info.pinned_msg_id);
                        pinnedMessagesMap.put(info.pinned_msg_id, null);
                    }
                }
                if (!pinnedMessages.isEmpty()) {
                    ArrayList<MessageObject> messageObjects = getMediaDataController().loadPinnedMessages(user.id, 0, pinnedMessages, false);
                    if (messageObjects != null) {
                        for (int a = 0, N = messageObjects.size(); a < N; a++) {
                            MessageObject messageObject = messageObjects.get(a);
                            pinnedMessagesMap.put(messageObject.getId(), messageObject);
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                getMessagesController().processUserInfo(user, info, true, force, classGuid, pinnedMessages, pinnedMessagesMap, totalPinnedCount, pinnedEndReached);
            }
        });
    }

    public void updateUserInfo(TLRPC.UserFull info, boolean ifExist) {
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
                state.bindLong(1, info.user.id);
                state.bindByteBuffer(2, data);
                state.bindInteger(3, info.pinned_msg_id);
                state.step();
                state.dispose();
                data.reuse();
                if ((info.flags & 2048) != 0) {
                    state = database.executeFast("UPDATE dialogs SET folder_id = ? WHERE did = ?");
                    state.bindInteger(1, info.folder_id);
                    state.bindLong(2, info.user.id);
                    state.step();
                    state.dispose();
                    unknownDialogsIds.remove(info.user.id);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void saveChatInviter(long chatId, long inviterId) {
        storageQueue.postRunnable(() -> {
            try {
                SQLitePreparedStatement state = database.executeFast("UPDATE chat_settings_v2 SET inviter = ? WHERE uid = ?");
                state.requery();
                state.bindLong(1, inviterId);
                state.bindLong(2, chatId);
                state.step();
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void saveChatLinksCount(long chatId, int linksCount) {
        storageQueue.postRunnable(() -> {
            try {
                SQLitePreparedStatement state = database.executeFast("UPDATE chat_settings_v2 SET links = ? WHERE uid = ?");
                state.requery();
                state.bindInteger(1, linksCount);
                state.bindLong(2, chatId);
                state.step();
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void updateChatInfo(TLRPC.ChatFull info, boolean ifExist) {
        storageQueue.postRunnable(() -> {
            try {
                int currentOnline = -1;
                int inviter = 0;
                int links = 0;
                SQLiteCursor cursor = database.queryFinalized("SELECT online, inviter, links FROM chat_settings_v2 WHERE uid = " + info.id);
                if (cursor.next()) {
                    currentOnline = cursor.intValue(0);
                    info.inviterId = cursor.longValue(1);
                    links = cursor.intValue(2);
                }
                cursor.dispose();
                if (ifExist && currentOnline == -1) {
                    return;
                }

                if (currentOnline >= 0 && (info.flags & 8192) == 0) {
                    info.online_count = currentOnline;
                }

                if (links >= 0) {
                    info.invitesCount = links;
                }

                SQLitePreparedStatement state = database.executeFast("REPLACE INTO chat_settings_v2 VALUES(?, ?, ?, ?, ?, ?)");
                NativeByteBuffer data = new NativeByteBuffer(info.getObjectSize());
                info.serializeToStream(data);
                state.bindLong(1, info.id);
                state.bindByteBuffer(2, data);
                state.bindInteger(3, info.pinned_msg_id);
                state.bindInteger(4, info.online_count);
                state.bindLong(5, info.inviterId);
                state.bindInteger(6, info.invitesCount);
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
                if ((info.flags & 2048) != 0) {
                    state = database.executeFast("UPDATE dialogs SET folder_id = ? WHERE did = ?");
                    state.bindInteger(1, info.folder_id);
                    state.bindLong(2, -info.id);
                    state.step();
                    state.dispose();
                    unknownDialogsIds.remove(-info.id);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void updateChatOnlineCount(long channelId, int onlineCount) {
        storageQueue.postRunnable(() -> {
            try {
                SQLitePreparedStatement state = database.executeFast("UPDATE chat_settings_v2 SET online = ? WHERE uid = ?");
                state.requery();
                state.bindInteger(1, onlineCount);
                state.bindLong(2, channelId);
                state.step();
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void updatePinnedMessages(long dialogId, ArrayList<Integer> ids, boolean pin, int totalCount, int maxId, boolean end, HashMap<Integer, MessageObject> messages) {
        storageQueue.postRunnable(() -> {
            try {
                if (pin) {
                    database.beginTransaction();
                    int alreadyAdded = 0;
                    boolean endReached;
                    if (messages != null) {
                        if (maxId == 0) {
                            database.executeFast("DELETE FROM chat_pinned_v2 WHERE uid = " + dialogId).stepThis().dispose();
                        }
                    } else {
                        SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM chat_pinned_v2 WHERE uid = %d AND mid IN (%s)", dialogId, TextUtils.join(",", ids)));
                        alreadyAdded = cursor.next() ? cursor.intValue(0) : 0;
                        cursor.dispose();
                    }
                    SQLitePreparedStatement state = database.executeFast("REPLACE INTO chat_pinned_v2 VALUES(?, ?, ?)");
                    for (int a = 0, N = ids.size(); a < N; a++) {
                        Integer id = ids.get(a);
                        state.requery();
                        state.bindLong(1, dialogId);
                        state.bindInteger(2, id);
                        MessageObject message = null;
                        if (messages != null) {
                            message = messages.get(id);
                        }
                        NativeByteBuffer data = null;
                        if (message != null) {
                            data = new NativeByteBuffer(message.messageOwner.getObjectSize());
                            message.messageOwner.serializeToStream(data);
                            state.bindByteBuffer(3, data);
                        } else {
                            state.bindNull(3);
                        }
                        state.step();
                        if (data != null) {
                            data.reuse();
                        }
                    }
                    state.dispose();
                    database.commitTransaction();

                    SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM chat_pinned_v2 WHERE uid = %d", dialogId));
                    int newCount1 = cursor.next() ? cursor.intValue(0) : 0;
                    cursor.dispose();

                    int newCount;
                    if (messages != null) {
                        newCount = Math.max(totalCount, newCount1);
                        endReached = end;
                    } else {
                        SQLiteCursor cursor2 = database.queryFinalized(String.format(Locale.US, "SELECT count, end FROM chat_pinned_count WHERE uid = %d", dialogId));
                        int newCount2;
                        if (cursor2.next()) {
                            newCount2 = cursor2.intValue(0);
                            endReached = cursor2.intValue(1) != 0;
                        } else {
                            newCount2 = 0;
                            endReached = false;
                        }
                        cursor2.dispose();
                        newCount = Math.max(newCount2 + (ids.size() - alreadyAdded), newCount1);
                    }

                    state = database.executeFast("REPLACE INTO chat_pinned_count VALUES(?, ?, ?)");
                    state.requery();
                    state.bindLong(1, dialogId);
                    state.bindInteger(2, newCount);
                    state.bindInteger(3, endReached ? 1 : 0);
                    state.step();
                    state.dispose();

                    AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.didLoadPinnedMessages, dialogId, ids, true, null, messages, maxId, newCount, endReached));
                } else {
                    int newCount;
                    boolean endReached;
                    if (ids == null) {
                        database.executeFast("DELETE FROM chat_pinned_v2 WHERE uid = " + dialogId).stepThis().dispose();
                        if (DialogObject.isChatDialog(dialogId)) {
                            database.executeFast(String.format(Locale.US, "UPDATE chat_settings_v2 SET pinned = 0 WHERE uid = %d", -dialogId)).stepThis().dispose();
                        } else {
                            database.executeFast(String.format(Locale.US, "UPDATE user_settings SET pinned = 0 WHERE uid = %d", dialogId)).stepThis().dispose();
                        }
                        newCount = 0;
                        endReached = true;
                    } else {
                        String idsStr = TextUtils.join(",", ids);
                        if (DialogObject.isChatDialog(dialogId)) {
                            database.executeFast(String.format(Locale.US, "UPDATE chat_settings_v2 SET pinned = 0 WHERE uid = %d AND pinned IN (%s)", -dialogId, idsStr)).stepThis().dispose();
                        } else {
                            database.executeFast(String.format(Locale.US, "UPDATE user_settings SET pinned = 0 WHERE uid = %d AND pinned IN (%s)", dialogId, idsStr)).stepThis().dispose();
                        }

                        database.executeFast(String.format(Locale.US, "DELETE FROM chat_pinned_v2 WHERE uid = %d AND mid IN(%s)", dialogId, idsStr)).stepThis().dispose();

                        SQLiteCursor cursor = database.queryFinalized("SELECT changes()");
                        int updatedCount = cursor.next() ? cursor.intValue(0) : 0;
                        cursor.dispose();

                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM chat_pinned_v2 WHERE uid = %d", dialogId));
                        int newCount1 = cursor.next() ? cursor.intValue(0) : 0;
                        cursor.dispose();

                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT count, end FROM chat_pinned_count WHERE uid = %d", dialogId));
                        int newCount2;
                        if (cursor.next()) {
                            newCount2 = Math.max(0, cursor.intValue(0) - updatedCount);
                            endReached = cursor.intValue(1) != 0;
                        } else {
                            newCount2 = 0;
                            endReached = false;
                        }
                        cursor.dispose();
                        newCount = Math.max(newCount1, newCount2);
                    }

                    SQLitePreparedStatement state = database.executeFast("REPLACE INTO chat_pinned_count VALUES(?, ?, ?)");
                    state.requery();
                    state.bindLong(1, dialogId);
                    state.bindInteger(2, newCount);
                    state.bindInteger(3, endReached ? 1 : 0);
                    state.step();
                    state.dispose();

                    AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.didLoadPinnedMessages, dialogId, ids, false, null, messages, maxId, newCount, endReached));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void updateChatInfo(long chatId, long userId, int what, long invited_id, int version) {
        storageQueue.postRunnable(() -> {
            try {
                SQLiteCursor cursor = database.queryFinalized("SELECT info, pinned, online, inviter FROM chat_settings_v2 WHERE uid = " + chatId);
                TLRPC.ChatFull info = null;
                ArrayList<TLRPC.User> loadedUsers = new ArrayList<>();
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        info = TLRPC.ChatFull.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                        info.pinned_msg_id = cursor.intValue(1);
                        info.online_count = cursor.intValue(2);
                        info.inviterId = cursor.longValue(3);
                    }
                }
                cursor.dispose();
                if (info instanceof TLRPC.TL_chatFull) {
                    if (what == 1) {
                        for (int a = 0; a < info.participants.participants.size(); a++) {
                            TLRPC.ChatParticipant participant = info.participants.participants.get(a);
                            if (participant.user_id == userId) {
                                info.participants.participants.remove(a);
                                break;
                            }
                        }
                    } else if (what == 0) {
                        for (TLRPC.ChatParticipant part : info.participants.participants) {
                            if (part.user_id == userId) {
                                return;
                            }
                        }
                        TLRPC.TL_chatParticipant participant = new TLRPC.TL_chatParticipant();
                        participant.user_id = userId;
                        participant.inviter_id = invited_id;
                        participant.date = getConnectionsManager().getCurrentTime();
                        info.participants.participants.add(participant);
                    } else if (what == 2) {
                        for (int a = 0; a < info.participants.participants.size(); a++) {
                            TLRPC.ChatParticipant participant = info.participants.participants.get(a);
                            if (participant.user_id == userId) {
                                TLRPC.ChatParticipant newParticipant;
                                if (invited_id == 1) {
                                    newParticipant = new TLRPC.TL_chatParticipantAdmin();
                                } else {
                                    newParticipant = new TLRPC.TL_chatParticipant();
                                }
                                newParticipant.user_id = participant.user_id;
                                newParticipant.date = participant.date;
                                newParticipant.inviter_id = participant.inviter_id;
                                info.participants.participants.set(a, newParticipant);
                                break;
                            }
                        }
                    }
                    info.participants.version = version;

                    TLRPC.ChatFull finalInfo = info;
                    AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, finalInfo, 0, false, false));

                    SQLitePreparedStatement state = database.executeFast("REPLACE INTO chat_settings_v2 VALUES(?, ?, ?, ?, ?, ?)");
                    NativeByteBuffer data = new NativeByteBuffer(info.getObjectSize());
                    info.serializeToStream(data);
                    state.bindLong(1, chatId);
                    state.bindByteBuffer(2, data);
                    state.bindInteger(3, info.pinned_msg_id);
                    state.bindInteger(4, info.online_count);
                    state.bindLong(5, info.inviterId);
                    state.bindInteger(6, info.invitesCount);
                    state.step();
                    state.dispose();
                    data.reuse();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public boolean isMigratedChat(long chatId) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        boolean[] result = new boolean[1];
        storageQueue.postRunnable(() -> {
            try {
                SQLiteCursor cursor = database.queryFinalized("SELECT info FROM chat_settings_v2 WHERE uid = " + chatId);
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
                countDownLatch.countDown();
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

    public boolean hasInviteMeMessage(long chatId) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        boolean[] result = new boolean[1];
        storageQueue.postRunnable(() -> {
            try {
                long selfId = getUserConfig().getClientUserId();
                SQLiteCursor cursor = database.queryFinalized("SELECT data FROM messages_v2 WHERE uid = " + -chatId + " AND out = 0 ORDER BY mid DESC LIMIT 100");
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                        if (message.action instanceof TLRPC.TL_messageActionChatAddUser && message.action.users.contains(selfId)) {
                            result[0] = true;
                            break;
                        }
                    }
                }
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

    private TLRPC.ChatFull loadChatInfoInternal(long chatId, boolean isChannel, boolean force, boolean byChannelUsers, int fromMessageId) {
        TLRPC.ChatFull info = null;
        ArrayList<TLRPC.User> loadedUsers = new ArrayList<>();

        HashMap<Integer, MessageObject> pinnedMessagesMap = new HashMap<>();
        ArrayList<Integer> pinnedMessages = new ArrayList<>();
        int totalPinnedCount = 0;
        boolean pinnedEndReached = false;

        try {
            SQLiteCursor cursor = database.queryFinalized("SELECT info, pinned, online, inviter, links FROM chat_settings_v2 WHERE uid = " + chatId);
            if (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null) {
                    info = TLRPC.ChatFull.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();
                    info.pinned_msg_id = cursor.intValue(1);
                    info.online_count = cursor.intValue(2);
                    info.inviterId = cursor.longValue(3);
                    info.invitesCount = cursor.intValue(4);
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
                cursor = database.queryFinalized("SELECT us.data, us.status, cu.data, cu.date FROM channel_users_v2 as cu LEFT JOIN users as us ON us.uid = cu.uid WHERE cu.did = " + (-chatId) + " ORDER BY cu.date DESC");
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
                        if (participant != null && participant.user_id == getUserConfig().clientUserId) {
                            user = getUserConfig().getCurrentUser();
                        }
                        if (user != null && participant != null) {
                            if (user.status != null) {
                                user.status.expires = cursor.intValue(1);
                            }
                            loadedUsers.add(user);
                            participant.date = cursor.intValue(3);
                            TLRPC.TL_chatChannelParticipant chatChannelParticipant = new TLRPC.TL_chatChannelParticipant();
                            chatChannelParticipant.user_id = MessageObject.getPeerId(participant.peer);
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
            if (info != null && info.inviterId != 0) {
                getUsersInternal("" + info.inviterId, loadedUsers);
            }

            cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT mid FROM chat_pinned_v2 WHERE uid = %d ORDER BY mid DESC", -chatId));
            while (cursor.next()) {
                int id = cursor.intValue(0);
                pinnedMessages.add(id);
                pinnedMessagesMap.put(id, null);
            }
            cursor.dispose();

            cursor = database.queryFinalized("SELECT count, end FROM chat_pinned_count WHERE uid = " + (-chatId));
            if (cursor.next()) {
                totalPinnedCount = cursor.intValue(0);
                pinnedEndReached = cursor.intValue(1) != 0;
            }
            cursor.dispose();

            if (info != null && info.pinned_msg_id != 0) {
                if (pinnedMessages.isEmpty() || info.pinned_msg_id > pinnedMessages.get(0)) {
                    pinnedMessages.clear();
                    pinnedMessages.add(info.pinned_msg_id);
                    pinnedMessagesMap.put(info.pinned_msg_id, null);
                }
            }
            if (!pinnedMessages.isEmpty()) {
                ArrayList<MessageObject> messageObjects = getMediaDataController().loadPinnedMessages(-chatId, isChannel ? chatId : 0, pinnedMessages, false);
                if (messageObjects != null) {
                    for (int a = 0, N = messageObjects.size(); a < N; a++) {
                        MessageObject messageObject = messageObjects.get(a);
                        pinnedMessagesMap.put(messageObject.getId(), messageObject);
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            getMessagesController().processChatInfo(chatId, info, loadedUsers, true, force, byChannelUsers, pinnedMessages, pinnedMessagesMap, totalPinnedCount, pinnedEndReached);
        }
        return info;
    }

    public TLRPC.ChatFull loadChatInfo(long chatId, boolean isChannel, CountDownLatch countDownLatch, boolean force, boolean byChannelUsers) {
        return loadChatInfo(chatId, isChannel, countDownLatch, force, byChannelUsers, 0);
    }

    public TLRPC.ChatFull loadChatInfo(long chatId, boolean isChannel, CountDownLatch countDownLatch, boolean force, boolean byChannelUsers, int fromMessageId) {
        TLRPC.ChatFull[] result = new TLRPC.ChatFull[1];
        storageQueue.postRunnable(() -> {
            result[0] = loadChatInfoInternal(chatId, isChannel, force, byChannelUsers, fromMessageId);
            if (countDownLatch != null) {
                countDownLatch.countDown();
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

    public void processPendingRead(long dialogId, int maxPositiveId, int maxNegativeId, int scheduledCount) {
        int maxDate = lastSavedDate;
        storageQueue.postRunnable(() -> {
            try {
                int currentMaxId = 0;
                int unreadCount = 0;
                long last_mid = 0;
                int prevUnreadCount = 0;
                SQLiteCursor cursor = database.queryFinalized("SELECT unread_count, inbox_max, last_mid FROM dialogs WHERE did = " + dialogId);
                if (cursor.next()) {
                    prevUnreadCount = unreadCount = cursor.intValue(0);
                    currentMaxId = cursor.intValue(1);
                    last_mid = cursor.longValue(2);
                }
                cursor.dispose();

                database.beginTransaction();
                SQLitePreparedStatement state;

                if (!DialogObject.isEncryptedDialog(dialogId)) {
                    currentMaxId = Math.max(currentMaxId, maxPositiveId);

                    state = database.executeFast("UPDATE messages_v2 SET read_state = read_state | 1 WHERE uid = ? AND mid <= ? AND read_state IN(0,2) AND out = 0");
                    state.requery();
                    state.bindLong(1, dialogId);
                    state.bindInteger(2, currentMaxId);
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
                    state.bindLong(1, dialogId);
                    state.bindInteger(2, currentMaxId);
                    state.step();
                    state.dispose();

                    state = database.executeFast("DELETE FROM unread_push_messages WHERE uid = ? AND date <= ?");
                    state.requery();
                    state.bindLong(1, dialogId);
                    state.bindInteger(2, maxDate);
                    state.step();
                    state.dispose();
                } else {
                    currentMaxId = maxNegativeId;

                    state = database.executeFast("UPDATE messages_v2 SET read_state = read_state | 1 WHERE uid = ? AND mid >= ? AND read_state IN(0,2) AND out = 0");
                    state.requery();
                    state.bindLong(1, dialogId);
                    state.bindInteger(2, currentMaxId);
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
                state.bindInteger(2, currentMaxId);
                state.bindLong(3, dialogId);
                state.step();
                state.dispose();

                database.commitTransaction();

                if (prevUnreadCount != 0 && unreadCount == 0) {
                    LongSparseIntArray dialogsToUpdate = new LongSparseIntArray();
                    dialogsToUpdate.put(dialogId, unreadCount);
                    updateFiltersReadCounter(dialogsToUpdate, null, true);
                }
                updateWidgets(dialogId);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void putContacts(ArrayList<TLRPC.TL_contact> contacts, boolean deleteAll) {
        if (contacts.isEmpty() && !deleteAll) {
            return;
        }
        ArrayList<TLRPC.TL_contact> contactsCopy = new ArrayList<>(contacts);
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
                    state.bindLong(1, contact.user_id);
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

    public void deleteContacts(ArrayList<Long> uids) {
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

    public void applyPhoneBookUpdates(String adds, String deletes) {
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

    public void putCachedPhoneBook(HashMap<String, ContactsController.Contact> contactHashMap, boolean migrate, boolean delete) {
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

    public void getCachedPhoneBook(boolean byError) {
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
                    long userId = cursor.intValue(0);
                    TLRPC.TL_contact contact = new TLRPC.TL_contact();
                    contact.user_id = userId;
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

    public void getUnsentMessages(int count) {
        storageQueue.postRunnable(() -> {
            try {
                SparseArray<TLRPC.Message> messageHashMap = new SparseArray<>();
                ArrayList<TLRPC.Message> messages = new ArrayList<>();
                ArrayList<TLRPC.Message> scheduledMessages = new ArrayList<>();
                ArrayList<TLRPC.User> users = new ArrayList<>();
                ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();

                ArrayList<Long> usersToLoad = new ArrayList<>();
                ArrayList<Long> chatsToLoad = new ArrayList<>();
                ArrayList<Integer> encryptedChatIds = new ArrayList<>();

                SQLiteCursor cursor = database.queryFinalized("SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.uid, s.seq_in, s.seq_out, m.ttl FROM messages_v2 as m LEFT JOIN randoms_v2 as r ON r.mid = m.mid AND r.uid = m.uid LEFT JOIN messages_seq as s ON m.mid = s.mid WHERE (m.mid < 0 AND m.send_state = 1) OR (m.mid > 0 AND m.send_state = 3) ORDER BY m.mid DESC LIMIT " + count);
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

                            if (DialogObject.isEncryptedDialog(message.dialog_id)) {
                                int encryptedChatId = DialogObject.getEncryptedChatId(message.dialog_id);
                                if (!encryptedChatIds.contains(encryptedChatId)) {
                                    encryptedChatIds.add(encryptedChatId);
                                }
                            } else if (DialogObject.isUserDialog(message.dialog_id)) {
                                if (!usersToLoad.contains(message.dialog_id)) {
                                    usersToLoad.add(message.dialog_id);
                                }
                            } else {
                                if (!chatsToLoad.contains(-message.dialog_id)) {
                                    chatsToLoad.add(-message.dialog_id);
                                }
                            }

                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);

                            if (message.send_state != 3 && (message.peer_id.channel_id == 0 && !MessageObject.isUnread(message) && !DialogObject.isEncryptedDialog(message.dialog_id) || message.id > 0)) {
                                message.send_state = 0;
                            }
                        }
                    }
                }
                cursor.dispose();

                cursor = database.queryFinalized("SELECT m.data, m.send_state, m.mid, m.date, r.random_id, m.uid, m.ttl FROM scheduled_messages_v2 as m LEFT JOIN randoms_v2 as r ON r.mid = m.mid AND r.uid = m.uid WHERE (m.mid < 0 AND m.send_state = 1) OR (m.mid > 0 AND m.send_state = 3) ORDER BY date ASC");
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

                            if (DialogObject.isEncryptedDialog(message.dialog_id)) {
                                int encryptedChatId = DialogObject.getEncryptedChatId(message.dialog_id);
                                if (!encryptedChatIds.contains(encryptedChatId)) {
                                    encryptedChatIds.add(encryptedChatId);
                                }
                            } else if (DialogObject.isUserDialog(message.dialog_id)) {
                                if (!usersToLoad.contains(message.dialog_id)) {
                                    usersToLoad.add(message.dialog_id);
                                }
                            } else {
                                if (!chatsToLoad.contains(-message.dialog_id)) {
                                    chatsToLoad.add(-message.dialog_id);
                                }
                            }

                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);

                            if (message.send_state != 3 && (message.peer_id.channel_id == 0 && !MessageObject.isUnread(message) && !DialogObject.isEncryptedDialog(message.dialog_id) || message.id > 0)) {
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
                        Long cid = chatsToLoad.get(a);
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

    public boolean checkMessageByRandomId(long random_id) {
        boolean[] result = new boolean[1];
        CountDownLatch countDownLatch = new CountDownLatch(1);
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT random_id FROM randoms_v2 WHERE random_id = %d", random_id));
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

    public boolean checkMessageId(long dialogId, int mid) {
        boolean[] result = new boolean[1];
        CountDownLatch countDownLatch = new CountDownLatch(1);
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid FROM messages_v2 WHERE uid = %d AND mid = %d", dialogId, mid));
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

    public void getUnreadMention(long dialog_id, IntCallback callback) {
        storageQueue.postRunnable(() -> {
            try {
                int result;
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT MIN(mid) FROM messages_v2 WHERE uid = %d AND mention = 1 AND read_state IN(0, 1)", dialog_id));
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

    public void getMessagesCount(long dialog_id, IntCallback callback) {
        storageQueue.postRunnable(() -> {
            try {
                int result;
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM messages_v2 WHERE uid = %d", dialog_id));
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

    public Runnable getMessagesInternal(long dialogId, long mergeDialogId, int count, int max_id, int offset_date, int minDate, int classGuid, int load_type, boolean scheduled, int replyMessageId, int loadIndex, boolean processMessages) {
        TLRPC.TL_messages_messages res = new TLRPC.TL_messages_messages();
        long currentUserId = getUserConfig().clientUserId;
        int count_unread = 0;
        int mentions_unread = 0;
        int count_query = count;
        int offset_query = 0;
        int min_unread_id = 0;
        int last_message_id = 0;
        boolean queryFromServer = false;
        int max_unread_date = 0;
        int messageMaxId = max_id;
        int max_id_query = max_id;
        boolean unreadCountIsLocal = false;
        int max_id_override = max_id;
        boolean isEnd = false;
        int num = dialogId == 777000 ? 10 : 1;
        int messagesCount = 0;
        long startLoadTime = SystemClock.elapsedRealtime();
        try {
            ArrayList<Long> usersToLoad = new ArrayList<>();
            ArrayList<Long> chatsToLoad = new ArrayList<>();
            LongSparseArray<SparseArray<ArrayList<TLRPC.Message>>> replyMessageOwners = new LongSparseArray<>();
            LongSparseArray<ArrayList<Integer>> dialogReplyMessagesIds = new LongSparseArray<>();
            LongSparseArray<ArrayList<TLRPC.Message>> replyMessageRandomOwners = new LongSparseArray<>();
            ArrayList<Long> replyMessageRandomIds = new ArrayList<>();
            SQLiteCursor cursor;
            String messageSelect = "SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention, m.imp, m.forwards, m.replies_data FROM messages_v2 as m LEFT JOIN randoms_v2 as r ON r.mid = m.mid AND r.uid = m.uid";

            if (scheduled) {
                isEnd = true;
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.ttl FROM scheduled_messages_v2 as m LEFT JOIN randoms_v2 as r ON r.mid = m.mid AND r.uid = m.uid WHERE m.uid = %d ORDER BY m.date DESC", dialogId));
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        message.send_state = cursor.intValue(1);
                        message.id = cursor.intValue(2);
                        if (message.id > 0 && message.send_state != 0 && message.send_state != 3) {
                            message.send_state = 0;
                        }
                        if (dialogId == currentUserId) {
                            message.out = true;
                            message.unread = false;
                        } else {
                            message.unread = true;
                        }
                        message.readAttachPath(data, currentUserId);
                        data.reuse();
                        message.date = cursor.intValue(3);
                        message.dialog_id = dialogId;
                        if (message.ttl == 0) {
                            message.ttl = cursor.intValue(6);
                        }
                        res.messages.add(message);

                        addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);

                        if (message.reply_to != null && (message.reply_to.reply_to_msg_id != 0 || message.reply_to.reply_to_random_id != 0)) {
                            if (!cursor.isNull(5)) {
                                data = cursor.byteBufferValue(5);
                                if (data != null) {
                                    message.replyMessage = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                    message.replyMessage.readAttachPath(data, currentUserId);
                                    data.reuse();
                                    if (message.replyMessage != null) {
                                        addUsersAndChatsFromMessage(message.replyMessage, usersToLoad, chatsToLoad);
                                    }
                                }
                            }
                            if (message.replyMessage == null) {
                                if (message.reply_to.reply_to_msg_id != 0) {
                                    addReplyMessages(message, replyMessageOwners, dialogReplyMessagesIds);
                                } else {
                                    ArrayList<TLRPC.Message> messages = replyMessageRandomOwners.get(message.reply_to.reply_to_random_id);
                                    if (messages == null) {
                                        messages = new ArrayList<>();
                                        replyMessageRandomOwners.put(message.reply_to.reply_to_random_id, messages);
                                    }
                                    if (!replyMessageRandomIds.contains(message.reply_to.reply_to_random_id)) {
                                        replyMessageRandomIds.add(message.reply_to.reply_to_random_id);
                                    }
                                    messages.add(message);
                                }
                            }
                        }
                    }
                }
                cursor.dispose();
            } else {
                if (!DialogObject.isEncryptedDialog(dialogId)) {
                    if (load_type == 3 && minDate == 0) {
                        cursor = database.queryFinalized("SELECT inbox_max, unread_count, date, unread_count_i FROM dialogs WHERE did = " + dialogId);
                        if (cursor.next()) {
                            min_unread_id = Math.max(1, cursor.intValue(0)) + 1;
                            count_unread = cursor.intValue(1);
                            max_unread_date = cursor.intValue(2);
                            mentions_unread = cursor.intValue(3);
                        }
                        cursor.dispose();
                    } else if (load_type != 1 && load_type != 3 && load_type != 4 && minDate == 0) {
                        if (load_type == 2) {
                            cursor = database.queryFinalized("SELECT inbox_max, unread_count, date, unread_count_i FROM dialogs WHERE did = " + dialogId);
                            if (cursor.next()) {
                                messageMaxId = max_id_query = min_unread_id = Math.max(1, cursor.intValue(0));
                                count_unread = cursor.intValue(1);
                                max_unread_date = cursor.intValue(2);
                                mentions_unread = cursor.intValue(3);
                                queryFromServer = true;
                                if (dialogId == currentUserId) {
                                    count_unread = 0;
                                }
                            }
                            cursor.dispose();
                            if (!queryFromServer) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid), max(date) FROM messages_v2 WHERE uid = %d AND out = 0 AND read_state IN(0,2) AND mid > 0", dialogId));
                                if (cursor.next()) {
                                    min_unread_id = cursor.intValue(0);
                                    max_unread_date = cursor.intValue(1);
                                }
                                cursor.dispose();
                                if (min_unread_id != 0) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(*) FROM messages_v2 WHERE uid = %d AND mid >= %d AND out = 0 AND read_state IN(0,2)", dialogId, min_unread_id));
                                    if (cursor.next()) {
                                        count_unread = cursor.intValue(0);
                                    }
                                    cursor.dispose();
                                }
                            } else if (max_id_query == 0) {
                                int existingUnreadCount = 0;
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(*) FROM messages_v2 WHERE uid = %d AND mid > 0 AND out = 0 AND read_state IN(0,2)", dialogId));
                                if (cursor.next()) {
                                    existingUnreadCount = cursor.intValue(0);
                                }
                                cursor.dispose();
                                if (existingUnreadCount == count_unread) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages_v2 WHERE uid = %d AND out = 0 AND read_state IN(0,2) AND mid > 0", dialogId));
                                    if (cursor.next()) {
                                        messageMaxId = max_id_query = min_unread_id = cursor.intValue(0);
                                    }
                                    cursor.dispose();
                                }
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT start, end FROM messages_holes WHERE uid = %d AND start < %d AND end > %d", dialogId, max_id_query, max_id_query));
                                boolean containMessage = !cursor.next();
                                cursor.dispose();

                                if (containMessage) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages_v2 WHERE uid = %d AND out = 0 AND read_state IN(0,2) AND mid > %d", dialogId, max_id_query));
                                    if (cursor.next()) {
                                        messageMaxId = max_id_query = cursor.intValue(0);
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

                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM messages_holes WHERE uid = %d AND start IN (0, 1)", dialogId));
                    if (cursor.next()) {
                        isEnd = cursor.intValue(0) == 1;
                    } else {
                        cursor.dispose();
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages_v2 WHERE uid = %d AND mid > 0", dialogId));
                        if (cursor.next()) {
                            int mid = cursor.intValue(0);
                            if (mid != 0) {
                                SQLitePreparedStatement state = database.executeFast("REPLACE INTO messages_holes VALUES(?, ?, ?)");
                                state.requery();
                                state.bindLong(1, dialogId);
                                state.bindInteger(2, 0);
                                state.bindInteger(3, mid);
                                state.step();
                                state.dispose();
                            }
                        }
                    }
                    cursor.dispose();

                    if (load_type == 3 || load_type == 4 || queryFromServer && load_type == 2) {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(mid) FROM messages_v2 WHERE uid = %d AND mid > 0", dialogId));
                        if (cursor.next()) {
                            last_message_id = cursor.intValue(0);
                        }
                        cursor.dispose();

                        if (load_type == 4 && offset_date != 0) {
                            int startMid;
                            int endMid;

                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(mid) FROM messages_v2 WHERE uid = %d AND date <= %d AND mid > 0", dialogId, offset_date));
                            if (cursor.next()) {
                                startMid = cursor.intValue(0);
                            } else {
                                startMid = -1;
                            }
                            cursor.dispose();
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages_v2 WHERE uid = %d AND date >= %d AND mid > 0", dialogId, offset_date));
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
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM messages_holes WHERE uid = %d AND start <= %d AND end > %d", dialogId, startMid, startMid));
                                    if (cursor.next()) {
                                        startMid = -1;
                                    }
                                    cursor.dispose();
                                    if (startMid != -1) {
                                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM messages_holes WHERE uid = %d AND start <= %d AND end > %d", dialogId, endMid, endMid));
                                        if (cursor.next()) {
                                            endMid = -1;
                                        }
                                        cursor.dispose();
                                        if (endMid != -1) {
                                            max_id_override = endMid;
                                            messageMaxId = max_id_query = endMid;
                                        }
                                    }
                                }
                            }
                        }

                        boolean containMessage = max_id_query != 0;
                        if (containMessage) {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM messages_holes WHERE uid = %d AND start < %d AND end > %d", dialogId, max_id_query, max_id_query));
                            if (cursor.next()) {
                                containMessage = false;
                            }
                            cursor.dispose();
                        }

                        if (containMessage) {
                            int holeMessageMaxId = 0;
                            int holeMessageMinId = 1;
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM messages_holes WHERE uid = %d AND start >= %d ORDER BY start ASC LIMIT 1", dialogId, max_id_query));
                            if (cursor.next()) {
                                holeMessageMaxId = cursor.intValue(0);
                            }
                            cursor.dispose();
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT end FROM messages_holes WHERE uid = %d AND end <= %d ORDER BY end DESC LIMIT 1", dialogId, max_id_query));
                            if (cursor.next()) {
                                holeMessageMinId = cursor.intValue(0);
                            }
                            cursor.dispose();
                            if (holeMessageMaxId != 0 || holeMessageMinId != 1) {
                                if (holeMessageMaxId == 0) {
                                    holeMessageMaxId = 1000000000;
                                }
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT * FROM (" + messageSelect + " WHERE m.uid = %d AND m.mid <= %d AND (m.mid >= %d OR m.mid < 0) ORDER BY m.date DESC, m.mid DESC LIMIT %d) UNION " +
                                        "SELECT * FROM (" + messageSelect + " WHERE m.uid = %d AND m.mid > %d AND (m.mid <= %d OR m.mid < 0) ORDER BY m.date ASC, m.mid ASC LIMIT %d)", dialogId, messageMaxId, holeMessageMinId, count_query / 2, dialogId, messageMaxId, holeMessageMaxId, count_query / 2));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT * FROM (" + messageSelect + " WHERE m.uid = %d AND m.mid <= %d ORDER BY m.date DESC, m.mid DESC LIMIT %d) UNION " +
                                        "SELECT * FROM (" + messageSelect + " WHERE m.uid = %d AND m.mid > %d ORDER BY m.date ASC, m.mid ASC LIMIT %d)", dialogId, messageMaxId, count_query / 2, dialogId, messageMaxId, count_query / 2));
                            }
                        } else {
                            if (load_type == 2) {
                                int existingUnreadCount = 0;
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(*) FROM messages_v2 WHERE uid = %d AND mid != 0 AND out = 0 AND read_state IN(0,2)", dialogId));
                                if (cursor.next()) {
                                    existingUnreadCount = cursor.intValue(0);
                                }
                                cursor.dispose();
                                if (existingUnreadCount == count_unread) {
                                    unreadCountIsLocal = true;
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT * FROM (" + messageSelect + " WHERE m.uid = %d AND m.mid <= %d ORDER BY m.date DESC, m.mid DESC LIMIT %d) UNION " +
                                            "SELECT * FROM (" + messageSelect + " WHERE m.uid = %d AND m.mid > %d ORDER BY m.date ASC, m.mid ASC LIMIT %d)", dialogId, messageMaxId, count_query / 2, dialogId, messageMaxId, count_query / 2));
                                } else {
                                    cursor = null;
                                }
                            } else {
                                cursor = null;
                            }
                        }
                    } else if (load_type == 1) {
                        int holeMessageId = 0;
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT start, end FROM messages_holes WHERE uid = %d AND (start >= %d AND start != 1 AND end != 1 OR start < %d AND end > %d) ORDER BY start ASC LIMIT 1", dialogId, max_id, max_id, max_id));
                        if (cursor.next()) {
                            holeMessageId = cursor.intValue(0);
                        }
                        cursor.dispose();
                        if (holeMessageId != 0) {
                            cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND m.date >= %d AND m.mid > %d AND m.mid <= %d ORDER BY m.date ASC, m.mid ASC LIMIT %d", dialogId, minDate, messageMaxId, holeMessageId, count_query));
                        } else {
                            cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND m.date >= %d AND m.mid > %d ORDER BY m.date ASC, m.mid ASC LIMIT %d", dialogId, minDate, messageMaxId, count_query));
                        }
                    } else if (minDate != 0) {
                        if (messageMaxId != 0) {
                            int holeMessageId = 0;
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT end FROM messages_holes WHERE uid = %d AND end <= %d ORDER BY end DESC LIMIT 1", dialogId, max_id));
                            if (cursor.next()) {
                                holeMessageId = cursor.intValue(0);
                            }
                            cursor.dispose();
                            if (holeMessageId != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND m.date <= %d AND m.mid < %d AND (m.mid >= %d OR m.mid < 0) ORDER BY m.date DESC, m.mid DESC LIMIT %d", dialogId, minDate, messageMaxId, holeMessageId, count_query));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND m.date <= %d AND m.mid < %d ORDER BY m.date DESC, m.mid DESC LIMIT %d", dialogId, minDate, messageMaxId, count_query));
                            }
                        } else {
                            cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND m.date <= %d ORDER BY m.date DESC, m.mid DESC LIMIT %d,%d", dialogId, minDate, offset_query, count_query));
                        }
                    } else {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(mid) FROM messages_v2 WHERE uid = %d AND mid > 0", dialogId));
                        if (cursor.next()) {
                            last_message_id = cursor.intValue(0);
                        }
                        cursor.dispose();

                        int holeMessageId = 0;
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(end) FROM messages_holes WHERE uid = %d", dialogId));
                        if (cursor.next()) {
                            holeMessageId = cursor.intValue(0);
                        }
                        cursor.dispose();
                        if (holeMessageId != 0) {
                            cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND (m.mid >= %d OR m.mid < 0) ORDER BY m.date DESC, m.mid DESC LIMIT %d,%d", dialogId, holeMessageId, offset_query, count_query));
                        } else {
                            cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d ORDER BY m.date DESC, m.mid DESC LIMIT %d,%d", dialogId, offset_query, count_query));
                        }
                    }
                } else {
                    isEnd = true;

                    if (load_type == 3 && minDate == 0) {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages_v2 WHERE uid = %d AND mid < 0", dialogId));
                        if (cursor.next()) {
                            min_unread_id = cursor.intValue(0);
                        }
                        cursor.dispose();

                        int min_unread_id2 = 0;
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(mid), max(date) FROM messages_v2 WHERE uid = %d AND out = 0 AND read_state IN(0,2) AND mid < 0", dialogId));
                        if (cursor.next()) {
                            min_unread_id2 = cursor.intValue(0);
                            max_unread_date = cursor.intValue(1);
                        }
                        cursor.dispose();
                        if (min_unread_id2 != 0) {
                            min_unread_id = min_unread_id2;
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(*) FROM messages_v2 WHERE uid = %d AND mid <= %d AND out = 0 AND read_state IN(0,2)", dialogId, min_unread_id2));
                            if (cursor.next()) {
                                count_unread = cursor.intValue(0);
                            }
                            cursor.dispose();
                        }
                    }

                    if (load_type == 3 || load_type == 4) {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages_v2 WHERE uid = %d AND mid < 0", dialogId));
                        if (cursor.next()) {
                            last_message_id = cursor.intValue(0);
                        }
                        cursor.dispose();

                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT * FROM (" + messageSelect + " WHERE m.uid = %d AND m.mid <= %d ORDER BY m.mid DESC LIMIT %d) UNION " +
                                "SELECT * FROM (" + messageSelect + " WHERE m.uid = %d AND m.mid > %d ORDER BY m.mid ASC LIMIT %d)", dialogId, messageMaxId, count_query / 2, dialogId, messageMaxId, count_query / 2));
                    } else if (load_type == 1) {
                        cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND m.mid < %d ORDER BY m.mid DESC LIMIT %d", dialogId, max_id, count_query));
                    } else if (minDate != 0) {
                        if (max_id != 0) {
                            cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND m.mid > %d ORDER BY m.mid ASC LIMIT %d", dialogId, max_id, count_query));
                        } else {
                            cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND m.date <= %d ORDER BY m.mid ASC LIMIT %d,%d", dialogId, minDate, offset_query, count_query));
                        }
                    } else {
                        if (load_type == 2) {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages_v2 WHERE uid = %d AND mid < 0", dialogId));
                            if (cursor.next()) {
                                last_message_id = cursor.intValue(0);
                            }
                            cursor.dispose();

                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(mid), max(date) FROM messages_v2 WHERE uid = %d AND out = 0 AND read_state IN(0,2) AND mid < 0", dialogId));
                            if (cursor.next()) {
                                min_unread_id = cursor.intValue(0);
                                max_unread_date = cursor.intValue(1);
                            }
                            cursor.dispose();
                            if (min_unread_id != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(*) FROM messages_v2 WHERE uid = %d AND mid <= %d AND out = 0 AND read_state IN(0,2)", dialogId, min_unread_id));
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
                        cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d ORDER BY m.mid ASC LIMIT %d,%d", dialogId, offset_query, count_query));
                    }
                }
                int minId = Integer.MAX_VALUE;
                int maxId = Integer.MIN_VALUE;
                ArrayList<Long> messageIdsToFix = null;
                if (cursor != null) {
                    while (cursor.next()) {
                        messagesCount++;
                        if (!processMessages) {
                            continue;
                        }
                        NativeByteBuffer data = cursor.byteBufferValue(1);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.send_state = cursor.intValue(2);
                            long fullMid = cursor.longValue(3);
                            message.id = (int) fullMid;
                            if ((fullMid & 0xffffffff00000000L) == 0xffffffff00000000L && message.id > 0) {
                                if (messageIdsToFix == null) {
                                    messageIdsToFix = new ArrayList<>();
                                }
                                messageIdsToFix.add(fullMid);
                            }
                            if (message.id > 0 && message.send_state != 0 && message.send_state != 3) {
                                message.send_state = 0;
                            }
                            if (dialogId == currentUserId) {
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
                            message.dialog_id = dialogId;
                            if ((message.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                                message.views = cursor.intValue(7);
                                message.forwards = cursor.intValue(11);
                            }
                            NativeByteBuffer repliesData = cursor.byteBufferValue(12);
                            if (repliesData != null) {
                                TLRPC.MessageReplies replies = TLRPC.MessageReplies.TLdeserialize(repliesData, repliesData.readInt32(false), false);
                                if (replies != null) {
                                    message.replies = replies;
                                }
                                repliesData.reuse();
                            }
                            if (!DialogObject.isEncryptedDialog(dialogId) && message.ttl == 0) {
                                message.ttl = cursor.intValue(8);
                            }
                            if (cursor.intValue(9) != 0) {
                                message.mentioned = true;
                            }
                            int flags = cursor.intValue(10);
                            if ((flags & 1) != 0) {
                                message.stickerVerified = 0;
                            } else if ((flags & 2) != 0) {
                                message.stickerVerified = 2;
                            }
                            res.messages.add(message);

                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);

                            if (message.reply_to != null && (message.reply_to.reply_to_msg_id != 0 || message.reply_to.reply_to_random_id != 0)) {
                                if (!cursor.isNull(6)) {
                                    data = cursor.byteBufferValue(6);
                                    if (data != null) {
                                        message.replyMessage = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                        message.replyMessage.readAttachPath(data, currentUserId);
                                        data.reuse();
                                        if (message.replyMessage != null) {
                                            addUsersAndChatsFromMessage(message.replyMessage, usersToLoad, chatsToLoad);
                                        }
                                    }
                                }
                                if (message.replyMessage == null) {
                                    if (message.reply_to.reply_to_msg_id != 0) {
                                        addReplyMessages(message, replyMessageOwners, dialogReplyMessagesIds);
                                    } else {
                                        ArrayList<TLRPC.Message> messages = replyMessageRandomOwners.get(message.reply_to.reply_to_random_id);
                                        if (messages == null) {
                                            messages = new ArrayList<>();
                                            replyMessageRandomOwners.put(message.reply_to.reply_to_random_id, messages);
                                        }
                                        if (!replyMessageRandomIds.contains(message.reply_to.reply_to_random_id)) {
                                            replyMessageRandomIds.add(message.reply_to.reply_to_random_id);
                                        }
                                        messages.add(message);
                                    }
                                }
                            }
                            if (DialogObject.isEncryptedDialog(dialogId) && !cursor.isNull(5)) {
                                message.random_id = cursor.longValue(5);
                            }
                            if (MessageObject.isSecretMedia(message)) {
                                try {
                                    SQLiteCursor cursor2 = database.queryFinalized(String.format(Locale.US, "SELECT date FROM enc_tasks_v4 WHERE mid = %d AND uid = %d AND media = 1", message.id, MessageObject.getDialogId(message)));
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

                if (!DialogObject.isEncryptedDialog(dialogId)) {
                    if ((load_type == 3 || load_type == 4 || load_type == 2 && queryFromServer && !unreadCountIsLocal) && !res.messages.isEmpty()) {
                        if (!(minId <= max_id_query && maxId >= max_id_query)) {
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
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM messages_v2 WHERE uid = %d AND mention = 1 AND read_state IN(0, 1)", dialogId));
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
            if (!replyMessageRandomOwners.isEmpty()) {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.data, m.mid, m.date, r.random_id FROM randoms_v2 as r INNER JOIN messages_v2 as m ON r.mid = m.mid AND r.uid = m.uid WHERE r.random_id IN(%s)", TextUtils.join(",", replyMessageRandomIds)));
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        message.readAttachPath(data, currentUserId);
                        data.reuse();
                        message.id = cursor.intValue(1);
                        message.date = cursor.intValue(2);
                        message.dialog_id = dialogId;

                        addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);

                        long value = cursor.longValue(3);
                        ArrayList<TLRPC.Message> arrayList = replyMessageRandomOwners.get(value);
                        replyMessageRandomOwners.remove(value);
                        if (arrayList != null) {
                            for (int a = 0; a < arrayList.size(); a++) {
                                TLRPC.Message object = arrayList.get(a);
                                object.replyMessage = message;
                                if (object.reply_to != null) {
                                    object.reply_to.reply_to_msg_id = message.id;
                                }
                            }
                        }
                    }
                }

                cursor.dispose();
                for (int b = 0, N = replyMessageRandomOwners.size(); b < N; b++) {
                    ArrayList<TLRPC.Message> arrayList = replyMessageRandomOwners.valueAt(b);
                    for (int a = 0, N2 = arrayList.size(); a < N2; a++) {
                        TLRPC.Message message = arrayList.get(a);
                        if (message.reply_to != null) {
                            message.reply_to.reply_to_random_id = 0;
                        }
                    }
                }
            } else {
                loadReplyMessages(replyMessageOwners, dialogReplyMessagesIds, usersToLoad, chatsToLoad);
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
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("messages load time = " + (SystemClock.elapsedRealtime() - startLoadTime) + " for dialog = " + dialogId);
        }
        int countQueryFinal = count_query;
        int maxIdOverrideFinal = max_id_override;
        int minUnreadIdFinal = min_unread_id;
        int lastMessageIdFinal = last_message_id;
        boolean isEndFinal = isEnd;
        boolean queryFromServerFinal = queryFromServer;
        int mentionsUnreadFinal = mentions_unread;
        int countUnreadFinal = count_unread;
        int maxUnreadDateFinal = max_unread_date;
        /*if (!scheduled && mergeDialogId != 0 && res.messages.size() < count && isEnd && load_type == 2) { TODO fix if end not reached
            Runnable runnable = getMessagesInternal(mergeDialogId, 0, count, Integer.MAX_VALUE, 0, 0, classGuid, 0, false, false, loadIndex);
            return () -> {
                getMessagesController().processLoadedMessages(res, dialogId, mergeDialogId, countQueryFinal, maxIdOverrideFinal, offset_date, true, classGuid, minUnreadIdFinal, lastMessageIdFinal, countUnreadFinal, maxUnreadDateFinal, load_type, isChannel, isEndFinal, scheduled, -loadIndex, queryFromServerFinal, mentionsUnreadFinal);
                runnable.run();
            };
        } else {*/
        int finalMessagesCount = scheduled ? res.messages.size() : messagesCount;
        return () -> getMessagesController().processLoadedMessages(res, finalMessagesCount, dialogId, mergeDialogId, countQueryFinal, maxIdOverrideFinal, offset_date, true, classGuid, minUnreadIdFinal, lastMessageIdFinal, countUnreadFinal, maxUnreadDateFinal, load_type, isEndFinal, scheduled ? 1 : 0, replyMessageId, loadIndex, queryFromServerFinal, mentionsUnreadFinal, processMessages);
        //}
    }

    public void getMessages(long dialogId, long mergeDialogId, boolean loadInfo, int count, int max_id, int offset_date, int minDate, int classGuid, int load_type, boolean scheduled, int replyMessageId, int loadIndex, boolean processMessages) {
        storageQueue.postRunnable(() -> {
            /*if (loadInfo) {
                if (lowerId < 0) {
                    TLRPC.ChatFull info = loadChatInfoInternal(-lowerId, true, false, 0);
                    if (info != null) {
                        mergeDialogIdFinal = -info.migrated_from_chat_id;
                    }
                }
            }*/
            Utilities.stageQueue.postRunnable(getMessagesInternal(dialogId, mergeDialogId, count, max_id, offset_date, minDate, classGuid, load_type, scheduled, replyMessageId, loadIndex, processMessages));
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

    public Object[] getSentFile(String path, int type) {
        if (path == null || path.toLowerCase().endsWith("attheme")) {
            return null;
        }
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Object[] result = new Object[2];
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

    private void updateWidgets(long did) {
        ArrayList<Long> dids = new ArrayList<>();
        dids.add(did);
        updateWidgets(dids);
    }

    private void updateWidgets(ArrayList<Long> dids) {
        if (dids.isEmpty()) {
            return;
        }
        try {
            AppWidgetManager appWidgetManager = null;
            String ids = TextUtils.join(",", dids);
            SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT DISTINCT id FROM shortcut_widget WHERE did IN(%s,-1)", TextUtils.join(",", dids)));
            while (cursor.next()) {
                if (appWidgetManager == null) {
                    appWidgetManager = AppWidgetManager.getInstance(ApplicationLoader.applicationContext);
                }
                appWidgetManager.notifyAppWidgetViewDataChanged(cursor.intValue(0), R.id.list_view);
            }
            cursor.dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void putWidgetDialogs(int widgetId, ArrayList<Long> dids) {
        storageQueue.postRunnable(() -> {
            try {
                database.beginTransaction();
                database.executeFast("DELETE FROM shortcut_widget WHERE id = " + widgetId).stepThis().dispose();
                SQLitePreparedStatement state = database.executeFast("REPLACE INTO shortcut_widget VALUES(?, ?, ?)");
                if (dids.isEmpty()) {
                    state.requery();
                    state.bindInteger(1, widgetId);
                    state.bindLong(2, -1);
                    state.bindInteger(3, 0);
                    state.step();
                } else {
                    for (int a = 0, N = dids.size(); a < N; a++) {
                        long did = dids.get(a);
                        state.requery();
                        state.bindInteger(1, widgetId);
                        state.bindLong(2, did);
                        state.bindInteger(3, a);
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

    public void clearWidgetDialogs(int widgetId) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM shortcut_widget WHERE id = " + widgetId).stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void getWidgetDialogIds(int widgetId, int type, ArrayList<Long> dids, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats, boolean edit) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        storageQueue.postRunnable(() -> {
            try {
                ArrayList<Long> usersToLoad = new ArrayList<>();
                ArrayList<Long> chatsToLoad = new ArrayList<>();
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT did FROM shortcut_widget WHERE id = %d ORDER BY ord ASC", widgetId));
                while (cursor.next()) {
                    long id = cursor.longValue(0);
                    if (id == -1) {
                        continue;
                    }
                    dids.add(id);
                    if (users != null && chats != null) {
                        if (DialogObject.isUserDialog(id)) {
                            usersToLoad.add(id);
                        } else {
                            chatsToLoad.add(-id);
                        }
                    }
                }
                cursor.dispose();
                if (!edit && dids.isEmpty()) {
                    if (type == EditWidgetActivity.TYPE_CHATS) {
                        cursor = database.queryFinalized("SELECT did FROM dialogs WHERE folder_id = 0 ORDER BY pinned DESC, date DESC LIMIT 0,10");
                        while (cursor.next()) {
                            long dialogId = cursor.longValue(0);
                            if (DialogObject.isFolderDialogId(dialogId)) {
                                continue;
                            }
                            dids.add(dialogId);
                            if (users != null && chats != null) {
                                if (DialogObject.isUserDialog(dialogId)) {
                                    usersToLoad.add(dialogId);
                                } else {
                                    chatsToLoad.add(-dialogId);
                                }
                            }
                        }
                        cursor.dispose();
                    } else {
                        cursor = getMessagesStorage().getDatabase().queryFinalized("SELECT did FROM chat_hints WHERE type = 0 ORDER BY rating DESC LIMIT 4");
                        while (cursor.next()) {
                            long dialogId = cursor.longValue(0);
                            dids.add(dialogId);
                            if (users != null && chats != null) {
                                if (DialogObject.isUserDialog(dialogId)) {
                                    usersToLoad.add(dialogId);
                                } else {
                                    chatsToLoad.add(-dialogId);
                                }
                            }
                        }
                        cursor.dispose();
                    }
                }
                if (users != null && chats != null) {
                    if (!chatsToLoad.isEmpty()) {
                        getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                    }
                    if (!usersToLoad.isEmpty()) {
                        getUsersInternal(TextUtils.join(",", usersToLoad), users);
                    }
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
    }

    public void getWidgetDialogs(int widgetId, int type, ArrayList<Long> dids, LongSparseArray<TLRPC.Dialog> dialogs, LongSparseArray<TLRPC.Message> messages, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        storageQueue.postRunnable(() -> {
            try {
                boolean add = false;
                ArrayList<Long> usersToLoad = new ArrayList<>();
                ArrayList<Long> chatsToLoad = new ArrayList<>();
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT did FROM shortcut_widget WHERE id = %d ORDER BY ord ASC", widgetId));
                while (cursor.next()) {
                    long id = cursor.longValue(0);
                    if (id == -1) {
                        continue;
                    }
                    dids.add(id);
                    if (DialogObject.isUserDialog(id)) {
                        usersToLoad.add(id);
                    } else {
                        chatsToLoad.add(-id);
                    }
                }
                cursor.dispose();
                if (dids.isEmpty() && type == EditWidgetActivity.TYPE_CONTACTS) {
                    cursor = getMessagesStorage().getDatabase().queryFinalized("SELECT did FROM chat_hints WHERE type = 0 ORDER BY rating DESC LIMIT 4");
                    while (cursor.next()) {
                        long dialogId = cursor.longValue(0);
                        dids.add(dialogId);
                        if (DialogObject.isUserDialog(dialogId)) {
                            usersToLoad.add(dialogId);
                        } else {
                            chatsToLoad.add(-dialogId);
                        }
                    }
                    cursor.dispose();
                }
                if (dids.isEmpty()) {
                    add = true;
                    cursor = database.queryFinalized("SELECT d.did, d.last_mid, d.unread_count, d.date, m.data, m.read_state, m.mid, m.send_state, m.date FROM dialogs as d LEFT JOIN messages_v2 as m ON d.last_mid = m.mid AND d.did = m.uid WHERE d.folder_id = 0 ORDER BY d.pinned DESC, d.date DESC LIMIT 0,10");
                } else {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT d.did, d.last_mid, d.unread_count, d.date, m.data, m.read_state, m.mid, m.send_state, m.date FROM dialogs as d LEFT JOIN messages_v2 as m ON d.last_mid = m.mid AND d.did = m.uid WHERE d.did IN(%s)", TextUtils.join(",", dids)));
                }
                while (cursor.next()) {
                    long dialogId = cursor.longValue(0);
                    if (DialogObject.isFolderDialogId(dialogId)) {
                        continue;
                    }
                    if (add) {
                        dids.add(dialogId);
                    }
                    TLRPC.Dialog dialog = new TLRPC.TL_dialog();
                    dialog.id = dialogId;
                    dialog.top_message = cursor.intValue(1);
                    dialog.unread_count = cursor.intValue(2);
                    dialog.last_message_date = cursor.intValue(3);

                    dialogs.put(dialog.id, dialog);

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
                        messages.put(dialog.id, message);
                        addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);
                    }
                }
                cursor.dispose();
                if (!add) {
                    if (dids.size() > dialogs.size()) {
                        for (int a = 0, N = dids.size(); a < N; a++) {
                            long did = dids.get(a);
                            if (dialogs.get(dids.get(a)) == null) {
                                TLRPC.TL_dialog dialog = new TLRPC.TL_dialog();
                                dialog.id = did;
                                dialogs.put(dialog.id, dialog);
                                if (DialogObject.isChatDialog(did)) {
                                    if (chatsToLoad.contains(-did)) {
                                        chatsToLoad.add(-did);
                                    }
                                } else {
                                    if (usersToLoad.contains(did)) {
                                        usersToLoad.add(did);
                                    }
                                }
                            }
                        }
                    }
                }
                if (!chatsToLoad.isEmpty()) {
                    getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                }
                if (!usersToLoad.isEmpty()) {
                    getUsersInternal(TextUtils.join(",", usersToLoad), users);
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
    }

    public void putSentFile(String path, TLObject file, int type, String parent) {
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

    public void updateEncryptedChatSeq(TLRPC.EncryptedChat chat, boolean cleanup) {
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
                if (cleanup && chat.in_seq_no != 0) {
                    long did = DialogObject.getEncryptedChatId(chat.id);
                    database.executeFast(String.format(Locale.US, "DELETE FROM messages_v2 WHERE mid IN (SELECT m.mid FROM messages_v2 as m LEFT JOIN messages_seq as s ON m.mid = s.mid WHERE m.uid = %d AND m.date = 0 AND m.mid < 0 AND s.seq_out <= %d) AND uid = %d", did, chat.in_seq_no, did)).stepThis().dispose();
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

    public void updateEncryptedChatTTL(TLRPC.EncryptedChat chat) {
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

    public void updateEncryptedChatLayer(TLRPC.EncryptedChat chat) {
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

    public void updateEncryptedChat(TLRPC.EncryptedChat chat) {
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
                state.bindLong(15, chat.admin_id);
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

    public void isDialogHasTopMessage(long did, Runnable onDontExist) {
        storageQueue.postRunnable(() -> {
            boolean exists = false;
            try {
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT last_mid FROM dialogs WHERE did = %d", did));
                if (cursor.next()) {
                    exists = cursor.intValue(0) != 0;
                }
                cursor.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (!exists) {
                AndroidUtilities.runOnUIThread(onDontExist);
            }
        });
    }

    public boolean hasAuthMessage(int date) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        boolean[] result = new boolean[1];
        storageQueue.postRunnable(() -> {
            try {
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid FROM messages_v2 WHERE uid = 777000 AND date = %d AND mid < 0 LIMIT 1", date));
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

    public void getEncryptedChat(long chatId, CountDownLatch countDownLatch, ArrayList<TLObject> result) {
        if (countDownLatch == null || result == null) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                ArrayList<Long> usersToLoad = new ArrayList<>();
                ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
                getEncryptedChatsInternal("" + chatId, encryptedChats, usersToLoad);
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

    public void putEncryptedChat(TLRPC.EncryptedChat chat, TLRPC.User user, TLRPC.Dialog dialog) {
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
                state.bindLong(2, user.id);
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
                state.bindLong(18, chat.admin_id);
                state.bindInteger(19, chat.mtproto_seq);

                state.step();
                state.dispose();
                data.reuse();
                data2.reuse();
                data3.reuse();
                data4.reuse();
                data5.reuse();
                if (dialog != null) {
                    state = database.executeFast("REPLACE INTO dialogs VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
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
                    state.bindInteger(15, dialog.unread_reactions_count);
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
                                if (user.apply_min_photo) {
                                    if (user.photo != null) {
                                        oldUser.photo = user.photo;
                                        oldUser.flags |= 32;
                                    } else {
                                        oldUser.photo = null;
                                        oldUser.flags = oldUser.flags & ~32;
                                    }
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
            state.bindLong(1, user.id);
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

    public void updateChatDefaultBannedRights(long chatId, TLRPC.TL_chatBannedRights rights, int version) {
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
                state.bindLong(2, chat.id);
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
                                oldChat.call_not_empty = chat.call_not_empty;
                                oldChat.call_active = chat.call_active;
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
            chat.flags |= 131072;
            NativeByteBuffer data = new NativeByteBuffer(chat.getObjectSize());
            chat.serializeToStream(data);
            state.bindLong(1, chat.id);
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

    public void getEncryptedChatsInternal(String chatsToLoad, ArrayList<TLRPC.EncryptedChat> result, ArrayList<Long> usersToLoad) throws Exception {
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
                        chat.user_id = cursor.longValue(1);
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
                        long admin_id = cursor.longValue(15);
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

    private void putUsersAndChatsInternal(ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats, boolean withTransaction) {
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

    public void putUsersAndChats(ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats, boolean withTransaction, boolean useQueue) {
        if (users != null && users.isEmpty() && chats != null && chats.isEmpty()) {
            return;
        }
        if (useQueue) {
            storageQueue.postRunnable(() -> putUsersAndChatsInternal(users, chats, withTransaction));
        } else {
            putUsersAndChatsInternal(users, chats, withTransaction);
        }
    }

    public void removeFromDownloadQueue(long id, int type, boolean move) {
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

    private void deleteFromDownloadQueue(ArrayList<Pair<Long, Integer>> ids, boolean transaction) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        try {
            if (transaction) {
                database.beginTransaction();
            }
            SQLitePreparedStatement state = database.executeFast("DELETE FROM download_queue WHERE uid = ? AND type = ?");
            for (int a = 0, N = ids.size(); a < N; a++) {
                Pair<Long, Integer> pair = ids.get(a);
                state.requery();
                state.bindLong(1, pair.first);
                state.bindInteger(2, pair.second);
                state.step();
            }
            state.dispose();
            if (transaction) {
                database.commitTransaction();
            }
            AndroidUtilities.runOnUIThread(() -> getDownloadController().cancelDownloading(ids));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void clearDownloadQueue(int type) {
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

    public void getDownloadQueue(int type) {
        storageQueue.postRunnable(() -> {
            try {
                ArrayList<DownloadObject> objects = new ArrayList<>();
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
                            downloadObject.secret = MessageObject.isVideoDocument(messageMedia.document) && messageMedia.ttl_seconds > 0 && messageMedia.ttl_seconds <= 60;
                        } else if (messageMedia.photo != null) {
                            downloadObject.object = messageMedia.photo;
                            downloadObject.secret = messageMedia.ttl_seconds > 0 && messageMedia.ttl_seconds <= 60;
                        }
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
            if (message.media instanceof TLRPC.TL_messageMediaPhoto || MessageObject.isGifMessage(message) ||
                    MessageObject.isVoiceMessage(message) ||
                    MessageObject.isVideoMessage(message) ||
                    MessageObject.isRoundVideoMessage(message)) {
                if (message.ttl > 0 && message.ttl <= 60) {
                    return 1;
                } else {
                    return 0;
                }
            }
        } else if (message instanceof TLRPC.TL_message && (message.media instanceof TLRPC.TL_messageMediaPhoto || message.media instanceof TLRPC.TL_messageMediaDocument) && message.media.ttl_seconds != 0) {
            return 1;
        } else if (message.media instanceof TLRPC.TL_messageMediaPhoto || MessageObject.isVideoMessage(message)) {
            return 0;
        }
        return -1;
    }

    public void putWebPages(LongSparseArray<TLRPC.WebPage> webPages) {
        if (isEmpty(webPages)) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                ArrayList<TLRPC.Message> messages = new ArrayList<>();
                for (int a = 0, N = webPages.size(); a < N; a++) {
                    SQLiteCursor cursor = database.queryFinalized("SELECT mid, uid FROM webpage_pending_v2 WHERE id = " + webPages.keyAt(a));
                    LongSparseArray<ArrayList<Integer>> dialogs = new LongSparseArray<>();
                    while (cursor.next()) {
                        long dialogId = cursor.longValue(1);
                        ArrayList<Integer> mids = dialogs.get(dialogId);
                        if (mids == null) {
                            mids = new ArrayList<>();
                            dialogs.put(dialogId, mids);
                        }
                        mids.add(cursor.intValue(0));
                    }
                    cursor.dispose();

                    if (dialogs.isEmpty()) {
                        continue;
                    }
                    for (int b = 0, N2 = dialogs.size(); b < N2; b++) {
                        long dialogId = dialogs.keyAt(b);
                        ArrayList<Integer> mids = dialogs.valueAt(b);
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid, data FROM messages_v2 WHERE mid IN (%s) AND uid = %d", TextUtils.join(",", mids), dialogId));
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
                }

                if (messages.isEmpty()) {
                    return;
                }

                database.beginTransaction();

                SQLitePreparedStatement state = database.executeFast("UPDATE messages_v2 SET data = ? WHERE mid = ? AND uid = ?");
                SQLitePreparedStatement state2 = database.executeFast("UPDATE media_v4 SET data = ? WHERE mid = ? AND uid = ?");
                for (int a = 0; a < messages.size(); a++) {
                    TLRPC.Message message = messages.get(a);
                    NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                    message.serializeToStream(data);

                    state.requery();
                    state.bindByteBuffer(1, data);
                    state.bindInteger(2, message.id);
                    state.bindLong(3, MessageObject.getDialogId(message));
                    state.step();

                    state2.requery();
                    state2.bindByteBuffer(1, data);
                    state2.bindInteger(2, message.id);
                    state2.bindLong(3, MessageObject.getDialogId(message));
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

    public void overwriteChannel(long channelId, TLRPC.TL_updates_channelDifferenceTooLong difference, int newDialogType) {
        storageQueue.postRunnable(() -> {
            try {
                boolean checkInvite = false;
                long did = -channelId;
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

                database.executeFast("DELETE FROM chat_pinned_count WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM chat_pinned_v2 WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM messages_v2 WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM bot_keyboard WHERE uid = " + did).stepThis().dispose();
                database.executeFast("UPDATE media_counts_v2 SET old = 1 WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM media_v4 WHERE uid = " + did).stepThis().dispose();
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

                updateDialogsWithDeletedMessages(-channelId, channelId, new ArrayList<>(), null, false);
                AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.removeAllMessagesFromDialog, did, true, difference));
                if (checkInvite) {
                    if (newDialogType == 1) {
                        getMessagesController().checkChatInviter(channelId, true);
                    } else {
                        getMessagesController().generateJoinMessage(channelId, false);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void putChannelViews(LongSparseArray<SparseIntArray> channelViews, LongSparseArray<SparseIntArray> channelForwards, LongSparseArray<SparseArray<TLRPC.MessageReplies>> channelReplies, boolean addReply) {
        if (isEmpty(channelViews) && isEmpty(channelForwards) && isEmpty(channelReplies)) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                database.beginTransaction();
                if (!isEmpty(channelViews)) {
                    SQLitePreparedStatement state = database.executeFast("UPDATE messages_v2 SET media = max((SELECT media FROM messages_v2 WHERE mid = ? AND uid = ?), ?) WHERE mid = ? AND uid = ?");
                    for (int a = 0; a < channelViews.size(); a++) {
                        long peer = channelViews.keyAt(a);
                        SparseIntArray messages = channelViews.valueAt(a);
                        for (int b = 0, N = messages.size(); b < N; b++) {
                            int views = messages.valueAt(b);
                            int messageId = messages.keyAt(b);
                            state.requery();
                            state.bindInteger(1, messageId);
                            state.bindLong(2, peer);
                            state.bindInteger(3, views);
                            state.bindInteger(4, messageId);
                            state.bindLong(5, peer);
                            state.step();
                        }
                    }
                    state.dispose();
                }

                if (!isEmpty(channelForwards)) {
                    SQLitePreparedStatement state = database.executeFast("UPDATE messages_v2 SET forwards = max((SELECT forwards FROM messages_v2 WHERE mid = ? AND uid = ?), ?) WHERE mid = ? AND uid = ?");
                    for (int a = 0; a < channelForwards.size(); a++) {
                        long peer = channelForwards.keyAt(a);
                        SparseIntArray messages = channelForwards.valueAt(a);
                        for (int b = 0, N = messages.size(); b < N; b++) {
                            int forwards = messages.valueAt(b);
                            int messageId = messages.keyAt(b);
                            state.requery();
                            state.bindInteger(1, messageId);
                            state.bindLong(2, peer);
                            state.bindInteger(3, forwards);
                            state.bindInteger(4, messageId);
                            state.bindLong(5, peer);
                            state.step();
                        }
                    }
                    state.dispose();
                }

                if (!isEmpty(channelReplies)) {
                    SQLitePreparedStatement state = database.executeFast("UPDATE messages_v2 SET replies_data = ? WHERE mid = ? AND uid = ?");
                    for (int a = 0; a < channelReplies.size(); a++) {
                        long peer = channelReplies.keyAt(a);
                        SparseArray<TLRPC.MessageReplies> messages = channelReplies.valueAt(a);
                        for (int b = 0, N3 = messages.size(); b < N3; b++) {
                            int messageId = messages.keyAt(b);
                            boolean messageExists;
                            TLRPC.MessageReplies currentReplies = null;
                            SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT replies_data FROM messages_v2 WHERE mid = %d AND uid = %d", messageId, peer));
                            if (messageExists = cursor.next()) {
                                NativeByteBuffer data = cursor.byteBufferValue(0);
                                if (data != null) {
                                    currentReplies = TLRPC.MessageReplies.TLdeserialize(data, data.readInt32(false), false);
                                    data.reuse();
                                }
                            }
                            cursor.dispose();
                            if (!messageExists) {
                                continue;
                            }
                            TLRPC.MessageReplies replies = messages.get(messages.keyAt(b));
                            if (!addReply && currentReplies != null && currentReplies.replies_pts != 0 && replies.replies_pts <= currentReplies.replies_pts && replies.read_max_id <= currentReplies.read_max_id && replies.max_id <= currentReplies.max_id) {
                                continue;
                            }
                            if (addReply) {
                                if (currentReplies == null) {
                                    currentReplies = new TLRPC.TL_messageReplies();
                                    currentReplies.flags |= 2;
                                }
                                currentReplies.replies += replies.replies;
                                for (int c = 0, N = replies.recent_repliers.size(); c < N; c++) {
                                    long id = MessageObject.getPeerId(replies.recent_repliers.get(c));
                                    for (int d = 0, N2 = currentReplies.recent_repliers.size(); d < N2; d++) {
                                        long id2 = MessageObject.getPeerId(currentReplies.recent_repliers.get(d));
                                        if (id == id2) {
                                            currentReplies.recent_repliers.remove(d);
                                            d--;
                                            N2--;
                                        }
                                    }
                                }
                                currentReplies.recent_repliers.addAll(0, replies.recent_repliers);
                                while (currentReplies.recent_repliers.size() > 3) {
                                    currentReplies.recent_repliers.remove(0);
                                }
                                replies = currentReplies;
                            }
                            if (currentReplies != null && currentReplies.read_max_id > replies.read_max_id) {
                                replies.read_max_id = currentReplies.read_max_id;
                            }
                            state.requery();
                            NativeByteBuffer data = new NativeByteBuffer(replies.getObjectSize());
                            replies.serializeToStream(data);
                            state.bindByteBuffer(1, data);
                            state.bindInteger(2, messageId);
                            state.bindLong(3, peer);
                            state.step();
                            data.reuse();
                        }
                    }
                    state.dispose();
                }
                database.commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void updateRepliesMaxReadIdInternal(long chatId, int mid, int readMaxId) {
        try {
            long dialogId = -chatId;
            SQLitePreparedStatement state = database.executeFast("UPDATE messages_v2 SET replies_data = ? WHERE mid = ? AND uid = ?");
            TLRPC.MessageReplies currentReplies = null;
            SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT replies_data FROM messages_v2 WHERE mid = %d AND uid = %d", mid, dialogId));
            if (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null) {
                    currentReplies = TLRPC.MessageReplies.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();
                }
            }
            cursor.dispose();
            if (currentReplies != null) {
                currentReplies.read_max_id = readMaxId;
                state.requery();
                NativeByteBuffer data = new NativeByteBuffer(currentReplies.getObjectSize());
                currentReplies.serializeToStream(data);
                state.bindByteBuffer(1, data);
                state.bindInteger(2, mid);
                state.bindLong(3, dialogId);
                state.step();
                data.reuse();
            }
            state.dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void updateRepliesMaxReadId(long chatId, int mid, int readMaxId, boolean useQueue) {
        if (useQueue) {
            storageQueue.postRunnable(() -> updateRepliesMaxReadIdInternal(chatId, mid, readMaxId));
        } else {
            updateRepliesMaxReadIdInternal(chatId, mid, readMaxId);
        }
    }

    public void updateRepliesCount(long chatId, int mid, ArrayList<TLRPC.Peer> repliers, int maxId, int count) {
        storageQueue.postRunnable(() -> {
            try {
                SQLitePreparedStatement state = database.executeFast("UPDATE messages_v2 SET replies_data = ? WHERE mid = ? AND uid = ?");
                TLRPC.MessageReplies currentReplies = null;
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.ENGLISH, "SELECT replies_data FROM messages_v2 WHERE mid = %d AND uid = %d", mid, -chatId));
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        currentReplies = TLRPC.MessageReplies.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                    }
                }
                cursor.dispose();
                if (currentReplies != null) {
                    currentReplies.replies += count;
                    if (currentReplies.replies < 0) {
                        currentReplies.replies = 0;
                    }
                    if (repliers != null) {
                        currentReplies.recent_repliers = repliers;
                        currentReplies.flags |= 2;
                    }
                    if (maxId != 0) {
                        currentReplies.max_id = maxId;
                    }
                    state.requery();
                    NativeByteBuffer data = new NativeByteBuffer(currentReplies.getObjectSize());
                    currentReplies.serializeToStream(data);
                    state.bindByteBuffer(1, data);
                    state.bindInteger(2, mid);
                    state.bindLong(3, -chatId);
                    state.step();
                    data.reuse();
                }
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private boolean isValidKeyboardToSave(TLRPC.Message message) {
        return message.reply_markup != null && !(message.reply_markup instanceof TLRPC.TL_replyInlineMarkup) && (!message.reply_markup.selective || message.mentioned);
    }

    public void updateMessageVerifyFlags(ArrayList<TLRPC.Message> messages) {
        Utilities.stageQueue.postRunnable(() -> {
            try {
                database.beginTransaction();
                SQLitePreparedStatement state = database.executeFast("UPDATE messages_v2 SET imp = ? WHERE mid = ? AND uid = ?");
                for (int a = 0, N = messages.size(); a < N; a++) {
                    TLRPC.Message message = messages.get(a);
                    state.requery();
                    int flags = 0;
                    if (message.stickerVerified == 0) {
                        flags |= 1;
                    } else if (message.stickerVerified == 2) {
                        flags |= 2;
                    }
                    state.bindInteger(1, flags);
                    state.bindInteger(2, message.id);
                    state.bindLong(3, MessageObject.getDialogId(message));
                    state.step();
                }
                state.dispose();
                database.commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });

    }

    private void putMessagesInternal(ArrayList<TLRPC.Message> messages, boolean withTransaction, boolean doNotUpdateDialogDate, int downloadMask, boolean ifNoLastMessage, boolean scheduled) {
        try {
            if (scheduled) {
                if (withTransaction) {
                    database.beginTransaction();
                }

                SQLitePreparedStatement state_messages = database.executeFast("REPLACE INTO scheduled_messages_v2 VALUES(?, ?, ?, ?, ?, ?, NULL, 0)");
                SQLitePreparedStatement state_randoms = database.executeFast("REPLACE INTO randoms_v2 VALUES(?, ?, ?)");
                ArrayList<Long> dialogsToUpdate = new ArrayList<>();

                for (int a = 0; a < messages.size(); a++) {
                    TLRPC.Message message = messages.get(a);
                    if (message instanceof TLRPC.TL_messageEmpty) {
                        continue;
                    }
                    fixUnsupportedMedia(message);

                    state_messages.requery();
                    int messageId = message.id;
                    if (message.local_id != 0) {
                        messageId = message.local_id;
                    }

                    NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                    message.serializeToStream(data);

                    long did = MessageObject.getDialogId(message);
                    state_messages.bindInteger(1, messageId);
                    state_messages.bindLong(2, did);
                    state_messages.bindInteger(3, message.send_state);
                    state_messages.bindInteger(4, message.date);
                    state_messages.bindByteBuffer(5, data);
                    state_messages.bindInteger(6, message.ttl);
                    state_messages.step();

                    if (message.random_id != 0) {
                        state_randoms.requery();
                        state_randoms.bindLong(1, message.random_id);
                        state_randoms.bindInteger(2, messageId);
                        state_randoms.bindLong(3, message.dialog_id);
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
                LongSparseIntArray messagesCounts = new LongSparseIntArray();
                LongSparseIntArray newMessagesCounts = new LongSparseIntArray();
                LongSparseIntArray newMentionsCounts = new LongSparseIntArray();
                LongSparseIntArray mentionCounts = new LongSparseIntArray();
                SparseArray<LongSparseIntArray> mediaCounts = null;
                LongSparseArray<TLRPC.Message> botKeyboards = new LongSparseArray<>();

                LongSparseArray<ArrayList<Integer>> dialogMessagesMediaIdsMap = null;
                LongSparseArray<SparseIntArray> dialogsMediaTypesChange = null;
                LongSparseArray<StringBuilder> mediaIdsMap = null;
                LongSparseArray<SparseIntArray> dialogMediaTypes = null;
                LongSparseArray<StringBuilder> messageIdsMap = new LongSparseArray<>();
                LongSparseIntArray dialogsReadMax = new LongSparseIntArray();
                LongSparseArray<ArrayList<Integer>> dialogMessagesIdsMap = new LongSparseArray<>();
                LongSparseArray<ArrayList<Integer>> dialogMentionsIdsMap = new LongSparseArray<>();

                SQLitePreparedStatement state_messages = database.executeFast("REPLACE INTO messages_v2 VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, 0)");
                SQLitePreparedStatement state_media = null;
                SQLitePreparedStatement state_randoms = database.executeFast("REPLACE INTO randoms_v2 VALUES(?, ?, ?)");
                SQLitePreparedStatement state_download = database.executeFast("REPLACE INTO download_queue VALUES(?, ?, ?, ?, ?)");
                SQLitePreparedStatement state_webpage = database.executeFast("REPLACE INTO webpage_pending_v2 VALUES(?, ?, ?)");
                SQLitePreparedStatement state_polls = null;
                SQLitePreparedStatement state_tasks = null;
                int minDeleteTime = Integer.MAX_VALUE;

                for (int a = 0; a < messages.size(); a++) {
                    TLRPC.Message message = messages.get(a);

                    int messageId = message.id;
                    MessageObject.getDialogId(message);
                    if (message.mentioned && message.media_unread) {
                        ArrayList<Integer> ids = dialogMentionsIdsMap.get(message.dialog_id);
                        if (ids == null) {
                            ids = new ArrayList<>();
                            dialogMentionsIdsMap.put(message.dialog_id, ids);
                        }
                        ids.add(messageId);
                    }

                    if (!(message.action instanceof TLRPC.TL_messageActionHistoryClear) && (!MessageObject.isOut(message) || message.from_scheduled) && (message.id > 0 || MessageObject.isUnread(message))) {
                        int currentMaxId = dialogsReadMax.get(message.dialog_id, -1);
                        if (currentMaxId == -1) {
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
                            StringBuilder messageIds = messageIdsMap.get(message.dialog_id);
                            if (messageIds == null) {
                                messageIds = new StringBuilder();
                                messageIdsMap.put(message.dialog_id, messageIds);
                            }
                            if (messageIds.length() > 0) {
                                messageIds.append(",");
                            }
                            messageIds.append(messageId);

                            ArrayList<Integer> ids = dialogMessagesIdsMap.get(message.dialog_id);
                            if (ids == null) {
                                ids = new ArrayList<>();
                                dialogMessagesIdsMap.put(message.dialog_id, ids);
                            }
                            ids.add(messageId);
                        }
                    }
                    if (MediaDataController.canAddMessageToMedia(message)) {
                        if (mediaIdsMap == null) {
                            mediaIdsMap = new LongSparseArray<>();
                            dialogMessagesMediaIdsMap = new LongSparseArray<>();
                            dialogMediaTypes = new LongSparseArray<>();
                        }
                        StringBuilder messageMediaIds = mediaIdsMap.get(message.dialog_id);
                        if (messageMediaIds == null) {
                            messageMediaIds = new StringBuilder();
                            mediaIdsMap.put(message.dialog_id, messageMediaIds);
                        }
                        if (messageMediaIds.length() > 0) {
                            messageMediaIds.append(",");
                        }
                        messageMediaIds.append(messageId);

                        ArrayList<Integer> ids = dialogMessagesMediaIdsMap.get(message.dialog_id);
                        if (ids == null) {
                            ids = new ArrayList<>();
                            dialogMessagesMediaIdsMap.put(message.dialog_id, ids);
                        }
                        ids.add(messageId);

                        SparseIntArray mediaTypes = dialogMediaTypes.get(message.dialog_id);
                        if (mediaTypes == null) {
                            mediaTypes = new SparseIntArray();
                            dialogMediaTypes.put(message.dialog_id, mediaTypes);
                        }
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

                if (mediaIdsMap != null) {
                    for (int b = 0, N2 = mediaIdsMap.size(); b < N2; b++) {
                        long dialogId = mediaIdsMap.keyAt(b);
                        StringBuilder messageMediaIds = mediaIdsMap.valueAt(b);
                        SparseIntArray mediaTypes = dialogMediaTypes.get(dialogId);
                        ArrayList<Integer> messagesMediaIdsMap = dialogMessagesMediaIdsMap.get(dialogId);
                        SparseIntArray mediaTypesChange = null;
                        SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid, type FROM media_v4 WHERE mid IN(%s) AND uid = %d", messageMediaIds.toString(), dialogId));
                        while (cursor.next()) {
                            int mid = cursor.intValue(0);
                            int type = cursor.intValue(1);
                            if (type == mediaTypes.get(mid)) {
                                messagesMediaIdsMap.remove((Integer) mid);
                            } else {
                                if (mediaTypesChange == null) {
                                    if (dialogsMediaTypesChange == null) {
                                        dialogsMediaTypesChange = new LongSparseArray<>();
                                    }
                                    mediaTypesChange = dialogsMediaTypesChange.get(dialogId);
                                    if (mediaTypesChange == null) {
                                        mediaTypesChange = new SparseIntArray();
                                        dialogsMediaTypesChange.put(dialogId, mediaTypesChange);
                                    }
                                }
                                mediaTypesChange.put(mid, type);
                            }
                        }
                        cursor.dispose();
                        if (mediaCounts == null) {
                            mediaCounts = new SparseArray<>();
                        }

                        for (int a = 0, N = messagesMediaIdsMap.size(); a < N; a++) {
                            int key = messagesMediaIdsMap.get(a);
                            int type = mediaTypes.get(key);
                            LongSparseIntArray counts = mediaCounts.get(type);
                            int count;
                            if (counts == null) {
                                counts = new LongSparseIntArray();
                                mediaCounts.put(type, counts);
                                count = 0;
                            } else {
                                count = counts.get(dialogId, Integer.MIN_VALUE);
                            }
                            if (count == Integer.MIN_VALUE) {
                                count = 0;
                            }
                            count++;
                            counts.put(dialogId, count);
                            if (mediaTypesChange != null) {
                                int previousType = mediaTypesChange.get(key, -1);
                                if (previousType >= 0) {
                                    counts = mediaCounts.get(previousType);
                                    if (counts == null) {
                                        counts = new LongSparseIntArray();
                                        count = 0;
                                        mediaCounts.put(previousType, counts);
                                    } else {
                                        count = counts.get(dialogId, Integer.MIN_VALUE);
                                    }
                                    if (count == Integer.MIN_VALUE) {
                                        count = 0;
                                    }
                                    count--;
                                    counts.put(dialogId, count);
                                }
                            }
                        }
                    }
                }

                if (!messageIdsMap.isEmpty()) {
                    for (int b = 0, N2 = messageIdsMap.size(); b < N2; b++) {
                        long dialogId = messageIdsMap.keyAt(b);
                        StringBuilder messageIds = messageIdsMap.valueAt(b);
                        ArrayList<Integer> messagesIdsMap = dialogMessagesIdsMap.get(dialogId);
                        ArrayList<Integer> mentionsIdsMap = dialogMentionsIdsMap.get(dialogId);
                        SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid FROM messages_v2 WHERE mid IN(%s) AND uid = %d", messageIds.toString(), dialogId));
                        while (cursor.next()) {
                            Integer mid = cursor.intValue(0);
                            if (messagesIdsMap != null) {
                                messagesIdsMap.remove(mid);
                            }
                            if (mentionsIdsMap != null) {
                                mentionsIdsMap.remove(mid);
                            }
                        }
                        cursor.dispose();

                        if (messagesCounts != null) {
                            int count = messagesCounts.get(dialogId, -1);
                            if (count < 0) {
                                count = 0;
                            }
                            count += messagesIdsMap.size();
                            messagesCounts.put(dialogId, count);
                        }

                        if (mentionsIdsMap != null) {
                            int count = mentionCounts.get(dialogId, -1);
                            if (count < 0) {
                                count = 0;
                            }
                            count += mentionsIdsMap.size();
                            mentionCounts.put(dialogId, count);
                        }
                    }
                }

                int downloadMediaMask = 0;
                for (int a = 0; a < messages.size(); a++) {
                    TLRPC.Message message = messages.get(a);
                    fixUnsupportedMedia(message);

                    state_messages.requery();
                    int messageId = message.id;
                    if (message.local_id != 0) {
                        messageId = message.local_id;
                    }

                    NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                    message.serializeToStream(data);

                    boolean updateDialog = true;
                    if (message.action instanceof TLRPC.TL_messageEncryptedAction && !(message.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL || message.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages)) {
                        updateDialog = false;
                    } else if (message.out) {
                        SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid FROM messages_v2 WHERE mid = %d AND uid = %d", messageId, message.dialog_id));
                        if (cursor.next()) {
                            updateDialog = false;
                        }
                        cursor.dispose();
                    }

                    if (updateDialog) {
                        TLRPC.Message lastMessage = messagesMap.get(message.dialog_id);
                        if (lastMessage == null || message.date > lastMessage.date || lastMessage.id > 0 && message.id > lastMessage.id || lastMessage.id < 0 && message.id < lastMessage.id) {
                            messagesMap.put(message.dialog_id, message);
                        }
                    }

                    state_messages.bindInteger(1, messageId);
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
                    int flags = 0;
                    if (message.stickerVerified == 0) {
                        flags |= 1;
                    } else if (message.stickerVerified == 2) {
                        flags |= 2;
                    }
                    state_messages.bindInteger(10, flags);
                    state_messages.bindInteger(11, message.mentioned ? 1 : 0);
                    state_messages.bindInteger(12, message.forwards);
                    NativeByteBuffer repliesData = null;
                    if (message.replies != null) {
                        repliesData = new NativeByteBuffer(message.replies.getObjectSize());
                        message.replies.serializeToStream(repliesData);
                        state_messages.bindByteBuffer(13, repliesData);
                    } else {
                        state_messages.bindNull(13);
                    }
                    if (message.reply_to != null) {
                        state_messages.bindInteger(14, message.reply_to.reply_to_top_id != 0 ? message.reply_to.reply_to_top_id : message.reply_to.reply_to_msg_id);
                    } else {
                        state_messages.bindInteger(14, 0);
                    }
                    state_messages.bindLong(15, MessageObject.getChannelId(message));
                    state_messages.step();

                    if (message.random_id != 0) {
                        state_randoms.requery();
                        state_randoms.bindLong(1, message.random_id);
                        state_randoms.bindInteger(2, messageId);
                        state_randoms.bindLong(3, message.dialog_id);
                        state_randoms.step();
                    }

                    if (MediaDataController.canAddMessageToMedia(message)) {
                        if (state_media == null) {
                            state_media = database.executeFast("REPLACE INTO media_v4 VALUES(?, ?, ?, ?, ?)");
                        }
                        state_media.requery();
                        state_media.bindInteger(1, messageId);
                        state_media.bindLong(2, message.dialog_id);
                        state_media.bindInteger(3, message.date);
                        state_media.bindInteger(4, MediaDataController.getMediaType(message));
                        state_media.bindByteBuffer(5, data);
                        state_media.step();
                    }

                    if (message.ttl_period != 0 && message.id > 0) {
                        if (state_tasks == null) {
                            state_tasks = database.executeFast("REPLACE INTO enc_tasks_v4 VALUES(?, ?, ?, ?)");
                        }
                        state_tasks.requery();
                        state_tasks.bindInteger(1, messageId);
                        state_tasks.bindLong(2, message.dialog_id);
                        state_tasks.bindInteger(3, message.date + message.ttl_period);
                        state_tasks.bindInteger(4, 0);
                        state_tasks.step();
                        minDeleteTime = Math.min(minDeleteTime, message.date + message.ttl_period);
                    }

                    if (message.media instanceof TLRPC.TL_messageMediaPoll) {
                        if (state_polls == null) {
                            state_polls = database.executeFast("REPLACE INTO polls_v2 VALUES(?, ?, ?)");
                        }
                        TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) message.media;
                        state_polls.requery();
                        state_polls.bindInteger(1, messageId);
                        state_polls.bindLong(2, message.dialog_id);
                        state_polls.bindLong(3, mediaPoll.poll.id);
                        state_polls.step();
                    } else if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
                        state_webpage.requery();
                        state_webpage.bindLong(1, message.media.webpage.id);
                        state_webpage.bindInteger(2, messageId);
                        state_webpage.bindLong(3, message.dialog_id);
                        state_webpage.step();
                    }

                    if (repliesData != null) {
                        repliesData.reuse();
                    }
                    data.reuse();

                    if (downloadMask != 0 && (message.peer_id.channel_id == 0 || message.post) && message.date >= getConnectionsManager().getCurrentTime() - 60 * 60 && getDownloadController().canDownloadMedia(message) == 1) {
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
                                state_download.bindString(5, "sent_" + (message.peer_id != null ? message.peer_id.channel_id : 0) + "_" + message.id);
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
                if (state_tasks != null) {
                    state_tasks.dispose();
                    getMessagesController().didAddedNewTask(minDeleteTime, 0, null);
                }
                if (state_polls != null) {
                    state_polls.dispose();
                }
                state_randoms.dispose();
                state_download.dispose();
                state_webpage.dispose();

                SQLitePreparedStatement state_dialogs_replace = database.executeFast("REPLACE INTO dialogs VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                SQLitePreparedStatement state_dialogs_update = database.executeFast("UPDATE dialogs SET date = ?, unread_count = ?, last_mid = ?, unread_count_i = ? WHERE did = ?");

                ArrayList<Long> dids = new ArrayList<>();
                for (int a = 0; a < messagesMap.size(); a++) {
                    long key = messagesMap.keyAt(a);
                    if (key == 0) {
                        continue;
                    }
                    TLRPC.Message message = messagesMap.valueAt(a);

                    long channelId = MessageObject.getChannelId(message);

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
                        getMessagesController().checkChatInviter(channelId, true);
                    }
                    cursor.dispose();

                    int mentions_count = mentionCounts.get(key, -1);
                    int unread_count = messagesCounts.get(key, -1);
                    if (unread_count == -1) {
                        unread_count = 0;
                    } else {
                        messagesCounts.put(key, unread_count + old_unread_count);
                    }
                    if (mentions_count == -1) {
                        mentions_count = 0;
                    } else {
                        mentionCounts.put(key, mentions_count + old_mentions_count);
                    }
                    int messageId = message != null ? message.id : last_mid;
                    if (message != null) {
                        if (message.local_id != 0) {
                            messageId = message.local_id;
                        }
                    }
                    if (old_unread_count == 0 && unread_count != 0) {
                        newMessagesCounts.put(key, unread_count);
                    }
                    if (old_mentions_count == 0 && mentions_count != 0) {
                        newMentionsCounts.put(key, mentions_count);
                    }

                    dids.add(key);
                    if (exists) {
                        state_dialogs_update.requery();
                        state_dialogs_update.bindInteger(1, message != null && (!doNotUpdateDialogDate || dialog_date == 0) ? message.date : dialog_date);
                        state_dialogs_update.bindInteger(2, old_unread_count + unread_count);
                        state_dialogs_update.bindInteger(3, messageId);
                        state_dialogs_update.bindInteger(4, old_mentions_count + mentions_count);
                        state_dialogs_update.bindLong(5, key);
                        state_dialogs_update.step();
                    } else {
                        state_dialogs_replace.requery();
                        state_dialogs_replace.bindLong(1, key);
                        state_dialogs_replace.bindInteger(2, message != null && (!doNotUpdateDialogDate || dialog_date == 0) ? message.date : dialog_date);
                        state_dialogs_replace.bindInteger(3, old_unread_count + unread_count);
                        state_dialogs_replace.bindInteger(4, messageId);
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
                        state_dialogs_replace.bindInteger(15, 0);
                        state_dialogs_replace.step();
                        unknownDialogsIds.put(key, true);
                    }
                }
                state_dialogs_update.dispose();
                state_dialogs_replace.dispose();

                if (mediaCounts != null) {
                    state_randoms = database.executeFast("REPLACE INTO media_counts_v2 VALUES(?, ?, ?, ?)");
                    for (int a = 0, N = mediaCounts.size(); a < N; a++) {
                        int type = mediaCounts.keyAt(a);
                        LongSparseIntArray value = mediaCounts.valueAt(a);
                        for (int b = 0, N2 = value.size(); b < N2; b++) {
                            long uid = value.keyAt(b);
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
                updateFiltersReadCounter(newMessagesCounts, newMentionsCounts, false);
                getMessagesController().processDialogsUpdateRead(messagesCounts, mentionCounts);

                if (downloadMediaMask != 0) {
                    int downloadMediaMaskFinal = downloadMediaMask;
                    AndroidUtilities.runOnUIThread(() -> getDownloadController().newDownloadObjectsAvailable(downloadMediaMaskFinal));
                }
                updateWidgets(dids);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void putMessages(ArrayList<TLRPC.Message> messages, boolean withTransaction, boolean useQueue, boolean doNotUpdateDialogDate, int downloadMask, boolean scheduled) {
        putMessages(messages, withTransaction, useQueue, doNotUpdateDialogDate, downloadMask, false, scheduled);
    }

    public void putMessages(ArrayList<TLRPC.Message> messages, boolean withTransaction, boolean useQueue, boolean doNotUpdateDialogDate, int downloadMask, boolean ifNoLastMessage, boolean scheduled) {
        if (messages.size() == 0) {
            return;
        }
        if (useQueue) {
            storageQueue.postRunnable(() -> putMessagesInternal(messages, withTransaction, doNotUpdateDialogDate, downloadMask, ifNoLastMessage, scheduled));
        } else {
            putMessagesInternal(messages, withTransaction, doNotUpdateDialogDate, downloadMask, ifNoLastMessage, scheduled);
        }
    }

    public void markMessageAsSendError(TLRPC.Message message, boolean scheduled) {
        storageQueue.postRunnable(() -> {
            try {
                long messageId = message.id;
                if (scheduled) {
                    database.executeFast(String.format(Locale.US, "UPDATE scheduled_messages_v2 SET send_state = 2 WHERE mid = %d AND uid = %d", messageId, MessageObject.getDialogId(message))).stepThis().dispose();
                } else {
                    database.executeFast(String.format(Locale.US, "UPDATE messages_v2 SET send_state = 2 WHERE mid = %d AND uid = %d", messageId, MessageObject.getDialogId(message))).stepThis().dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void setMessageSeq(int mid, int seq_in, int seq_out) {
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

    private long[] updateMessageStateAndIdInternal(long randomId, long dialogId, Integer _oldId, int newId, int date, int scheduled) {
        SQLiteCursor cursor = null;
        int oldMessageId;

        if (_oldId == null) {
            try {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid, uid FROM randoms_v2 WHERE random_id = %d LIMIT 1", randomId));
                if (cursor.next()) {
                    _oldId = cursor.intValue(0);
                    dialogId = cursor.longValue(1);
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
        if (_oldId < 0 && scheduled == 1) {
            SQLitePreparedStatement state = null;
            try {
                state = database.executeFast("UPDATE randoms_v2 SET mid = ? WHERE random_id = ? AND mid = ?");
                state.bindInteger(1, newId);
                state.bindLong(2, randomId);
                state.bindInteger(3, oldMessageId);
                state.step();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
        } else if (_oldId > 0) {
            TLRPC.TL_updateDeleteScheduledMessages update = new TLRPC.TL_updateDeleteScheduledMessages();
            update.messages.add(oldMessageId);
            if (DialogObject.isChatDialog(dialogId)) {
                update.peer = new TLRPC.TL_peerChannel();
                update.peer.channel_id = -dialogId;
            } else {
                update.peer = new TLRPC.TL_peerUser();
                update.peer.user_id = dialogId;
            }
            TLRPC.TL_updates updates = new TLRPC.TL_updates();
            updates.updates.add(update);
            Utilities.stageQueue.postRunnable(() -> getMessagesController().processUpdates(updates, false));
            try {
                database.executeFast(String.format(Locale.US, "DELETE FROM randoms_v2 WHERE random_id = %d AND mid = %d AND uid = %d", randomId, _oldId, dialogId)).stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
            return null;
        }

        long did = 0;
        if (scheduled == -1 || scheduled == 0) {
            try {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid FROM messages_v2 WHERE mid = %d LIMIT 1", oldMessageId));
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
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid FROM scheduled_messages_v2 WHERE mid = %d LIMIT 1", oldMessageId));
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
        SQLitePreparedStatement state = null;
        if (oldMessageId == newId && date != 0) {
            try {
                if (scheduled == 0) {
                    state = database.executeFast("UPDATE messages_v2 SET send_state = 0, date = ? WHERE mid = ? AND uid = ?");
                } else {
                    state = database.executeFast("UPDATE scheduled_messages_v2 SET send_state = 0, date = ? WHERE mid = ? AND uid = ?");
                }
                state.bindInteger(1, date);
                state.bindInteger(2, newId);
                state.bindLong(3, did);
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
            if (scheduled == 0) {
                try {
                    state = database.executeFast("UPDATE messages_v2 SET mid = ?, send_state = 0 WHERE mid = ? AND uid = ?");
                    state.bindInteger(1, newId);
                    state.bindInteger(2, oldMessageId);
                    state.bindLong(3, did);
                    state.step();
                } catch (Exception e) {
                    try {
                        database.executeFast(String.format(Locale.US, "DELETE FROM messages_v2 WHERE mid = %d AND uid = %d", oldMessageId, did)).stepThis().dispose();
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
                    state = database.executeFast("UPDATE media_v4 SET mid = ? WHERE mid = ? AND uid = ?");
                    state.bindInteger(1, newId);
                    state.bindInteger(2, oldMessageId);
                    state.bindLong(3, did);
                    state.step();
                } catch (Exception e) {
                    try {
                        database.executeFast(String.format(Locale.US, "DELETE FROM media_v4 WHERE mid = %d AND uid = %d", oldMessageId, did)).stepThis().dispose();
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
                    state.bindInteger(1, newId);
                    state.bindInteger(2, oldMessageId);
                    state.step();
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (state != null) {
                        state.dispose();
                    }
                }
            } else {
                try {
                    state = database.executeFast("UPDATE scheduled_messages_v2 SET mid = ?, send_state = 0 WHERE mid = ? AND uid = ?");
                    state.bindInteger(1, newId);
                    state.bindInteger(2, oldMessageId);
                    state.bindLong(3, did);
                    state.step();
                } catch (Exception e) {
                    try {
                        database.executeFast(String.format(Locale.US, "DELETE FROM scheduled_messages_v2 WHERE mid = %d AND uid = %d", oldMessageId, did)).stepThis().dispose();
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

    public long[] updateMessageStateAndId(long random_id, long dialogId, Integer _oldId, int newId, int date, boolean useQueue, int scheduled) {
        if (useQueue) {
            storageQueue.postRunnable(() -> updateMessageStateAndIdInternal(random_id, dialogId, _oldId, newId, date, scheduled));
        } else {
            return updateMessageStateAndIdInternal(random_id, dialogId, _oldId, newId, date, scheduled);
        }
        return null;
    }

    private void updateUsersInternal(ArrayList<TLRPC.User> users, boolean onlyStatus, boolean withTransaction) {
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
                    state.bindLong(2, user.id);
                    state.step();
                }
                state.dispose();
                if (withTransaction) {
                    database.commitTransaction();
                }
            } else {
                StringBuilder ids = new StringBuilder();
                LongSparseArray<TLRPC.User> usersDict = new LongSparseArray<>();
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

    public void updateUsers(ArrayList<TLRPC.User> users, boolean onlyStatus, boolean withTransaction, boolean useQueue) {
        if (users == null || users.isEmpty()) {
            return;
        }
        if (useQueue) {
            storageQueue.postRunnable(() -> updateUsersInternal(users, onlyStatus, withTransaction));
        } else {
            updateUsersInternal(users, onlyStatus, withTransaction);
        }
    }

    private void markMessagesAsReadInternal(LongSparseIntArray inbox, LongSparseIntArray outbox, SparseIntArray encryptedMessages) {
        try {
            if (!isEmpty(inbox)) {
                SQLitePreparedStatement state = database.executeFast("DELETE FROM unread_push_messages WHERE uid = ? AND mid <= ?");
                for (int b = 0; b < inbox.size(); b++) {
                    long key = inbox.keyAt(b);
                    int messageId = inbox.get(key);
                    database.executeFast(String.format(Locale.US, "UPDATE messages_v2 SET read_state = read_state | 1 WHERE uid = %d AND mid > 0 AND mid <= %d AND read_state IN(0,2) AND out = 0", key, messageId)).stepThis().dispose();

                    state.requery();
                    state.bindLong(1, key);
                    state.bindInteger(2, messageId);
                    state.step();
                }
                state.dispose();
            }
            if (!isEmpty(outbox)) {
                for (int b = 0; b < outbox.size(); b++) {
                    long key = outbox.keyAt(b);
                    int messageId = outbox.get(key);
                    database.executeFast(String.format(Locale.US, "UPDATE messages_v2 SET read_state = read_state | 1 WHERE uid = %d AND mid > 0 AND mid <= %d AND read_state IN(0,2) AND out = 1", key, messageId)).stepThis().dispose();
                }
            }
            if (encryptedMessages != null && !isEmpty(encryptedMessages)) {
                for (int a = 0; a < encryptedMessages.size(); a++) {
                    long dialogId = DialogObject.makeEncryptedDialogId(encryptedMessages.keyAt(a));
                    int max_date = encryptedMessages.valueAt(a);
                    SQLitePreparedStatement state = database.executeFast("UPDATE messages_v2 SET read_state = read_state | 1 WHERE uid = ? AND date <= ? AND read_state IN(0,2) AND out = 1");
                    state.requery();
                    state.bindLong(1, dialogId);
                    state.bindInteger(2, max_date);
                    state.step();
                    state.dispose();
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void markMessagesContentAsReadInternal(long dialogId, ArrayList<Integer> mids, int date) {
        try {
            String midsStr = TextUtils.join(",", mids);
            database.executeFast(String.format(Locale.US, "UPDATE messages_v2 SET read_state = read_state | 2 WHERE mid IN (%s) AND uid = %d", midsStr, dialogId)).stepThis().dispose();
            if (date != 0) {
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid, ttl FROM messages_v2 WHERE mid IN (%s) AND uid = %d AND ttl > 0", midsStr, dialogId));
                ArrayList<Integer> arrayList = null;
                while (cursor.next()) {
                    if (arrayList == null) {
                        arrayList = new ArrayList<>();
                    }
                    arrayList.add(cursor.intValue(0));
                }
                if (arrayList != null) {
                    emptyMessagesMedia(dialogId, arrayList);
                }
                cursor.dispose();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void markMessagesContentAsRead(long dialogId, ArrayList<Integer> mids, int date) {
        if (isEmpty(mids)) {
            return;
        }
        storageQueue.postRunnable(() -> {
            if (dialogId == 0) {
                try {
                    LongSparseArray<ArrayList<Integer>> sparseArray = new LongSparseArray<>();
                    SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, mid FROM messages_v2 WHERE mid IN (%s) AND is_channel = 0", TextUtils.join(",", mids)));
                    while (cursor.next()) {
                        long did = cursor.longValue(0);
                        ArrayList<Integer> arrayList = sparseArray.get(did);
                        if (arrayList == null) {
                            arrayList = new ArrayList<>();
                            sparseArray.put(did, arrayList);
                        }
                        arrayList.add(cursor.intValue(1));
                    }
                    cursor.dispose();
                    for (int a = 0, N = sparseArray.size(); a < N; a++) {
                        markMessagesContentAsReadInternal(sparseArray.keyAt(a), sparseArray.valueAt(a), date);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else {
                markMessagesContentAsReadInternal(dialogId, mids, date);
            }
        });
    }

    public void markMessagesAsRead(LongSparseIntArray inbox, LongSparseIntArray outbox, SparseIntArray encryptedMessages, boolean useQueue) {
        if (useQueue) {
            storageQueue.postRunnable(() -> markMessagesAsReadInternal(inbox, outbox, encryptedMessages));
        } else {
            markMessagesAsReadInternal(inbox, outbox, encryptedMessages);
        }
    }

    public void markMessagesAsDeletedByRandoms(ArrayList<Long> messages) {
        if (messages.isEmpty()) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                String ids = TextUtils.join(",", messages);
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid, uid FROM randoms_v2 WHERE random_id IN(%s)", ids));
                LongSparseArray<ArrayList<Integer>> dialogs = new LongSparseArray<>();
                while (cursor.next()) {
                    long dialogId = cursor.longValue(1);
                    ArrayList<Integer> mids = dialogs.get(dialogId);
                    if (mids == null) {
                        mids = new ArrayList<>();
                        dialogs.put(dialogId, mids);
                    }
                    mids.add(cursor.intValue(0));
                }
                cursor.dispose();
                if (!dialogs.isEmpty()) {
                    for (int a = 0, N = dialogs.size(); a < N; a++) {
                        long dialogId = dialogs.keyAt(a);
                        ArrayList<Integer> mids = dialogs.valueAt(a);
                        AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.messagesDeleted, mids, 0L, false));
                        updateDialogsWithReadMessagesInternal(mids, null, null, null);
                        markMessagesAsDeletedInternal(dialogId, mids, true, false);
                        updateDialogsWithDeletedMessagesInternal(dialogId, 0, mids, null);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    protected void deletePushMessages(long dialogId, ArrayList<Integer> messages) {
        try {
            database.executeFast(String.format(Locale.US, "DELETE FROM unread_push_messages WHERE uid = %d AND mid IN(%s)", dialogId, TextUtils.join(",", messages))).stepThis().dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void broadcastScheduledMessagesChange(Long did) {
        try {
            SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM scheduled_messages_v2 WHERE uid = %d", did));
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

    private ArrayList<Long> markMessagesAsDeletedInternal(long dialogId, ArrayList<Integer> messages, boolean deleteFiles, boolean scheduled) {
        try {
            ArrayList<Long> dialogsIds = new ArrayList<>();
            if (scheduled) {
                String ids = TextUtils.join(",", messages);

                ArrayList<Long> dialogsToUpdate = new ArrayList<>();

                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid FROM scheduled_messages_v2 WHERE mid IN(%s) AND uid = %d", ids, dialogId));
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

                database.executeFast(String.format(Locale.US, "DELETE FROM scheduled_messages_v2 WHERE mid IN(%s) AND uid = %d", ids, dialogId)).stepThis().dispose();
                for (int a = 0, N = dialogsToUpdate.size(); a < N; a++) {
                    broadcastScheduledMessagesChange(dialogsToUpdate.get(a));
                }
            } else {
                ArrayList<Integer> temp = new ArrayList<>(messages);
                LongSparseArray<Integer[]> dialogsToUpdate = new LongSparseArray<>();
                LongSparseArray<ArrayList<Integer>> messagesByDialogs = new LongSparseArray<>();
                String ids = TextUtils.join(",", messages);
                ArrayList<File> filesToDelete = new ArrayList<>();
                ArrayList<String> namesToDelete = new ArrayList<>();
                ArrayList<Pair<Long, Integer>> idsToDelete = new ArrayList<>();
                long currentUser = getUserConfig().getClientUserId();
                SQLiteCursor cursor;
                if (dialogId != 0) {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, data, read_state, out, mention, mid FROM messages_v2 WHERE mid IN(%s) AND uid = %d", ids, dialogId));
                } else {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, data, read_state, out, mention, mid FROM messages_v2 WHERE mid IN(%s) AND is_channel = 0", ids));
                }

                try {
                    while (cursor.next()) {
                        long did = cursor.longValue(0);
                        int mid = cursor.intValue(5);
                        temp.remove((Integer) mid);
                        ArrayList<Integer> mids = messagesByDialogs.get(did);
                        if (mids == null) {
                            mids = new ArrayList<>();
                            messagesByDialogs.put(did, mids);
                        }
                        mids.add(mid);
                        if (did != currentUser) {
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
                        }
                        if (!DialogObject.isEncryptedDialog(did) && !deleteFiles) {
                            continue;
                        }
                        NativeByteBuffer data = cursor.byteBufferValue(1);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.readAttachPath(data, getUserConfig().clientUserId);
                            data.reuse();
                            addFilesToDelete(message, filesToDelete, idsToDelete, namesToDelete, false);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                cursor.dispose();

                getMessagesStorage().getDatabase().beginTransaction();
                SQLitePreparedStatement state;
                for (int i = 0; i < 2; i++) {
                    if (i == 0) {
                        if (dialogId != 0) {
                            state = getMessagesStorage().getDatabase().executeFast("UPDATE messages_v2 SET replydata = ? WHERE reply_to_message_id IN(?) AND uid = ?");
                        } else {
                            state = getMessagesStorage().getDatabase().executeFast("UPDATE messages_v2 SET replydata = ? WHERE reply_to_message_id IN(?) AND is_channel = 0");
                        }
                    } else {
                        if (dialogId != 0) {
                            state = getMessagesStorage().getDatabase().executeFast("UPDATE scheduled_messages_v2 SET replydata = ? WHERE reply_to_message_id IN(?) AND uid = ?");
                        } else {
                            state = getMessagesStorage().getDatabase().executeFast("UPDATE scheduled_messages_v2 SET replydata = ? WHERE reply_to_message_id IN(?)");
                        }
                    }
                    TLRPC.TL_messageEmpty emptyMessage = new TLRPC.TL_messageEmpty();
                    NativeByteBuffer data = new NativeByteBuffer(emptyMessage.getObjectSize());
                    emptyMessage.serializeToStream(data);

                    state.requery();
                    state.bindByteBuffer(1, data);
                    state.bindString(2, ids);
                    if (dialogId != 0) {
                        state.bindLong(3, dialogId);
                    }
                    state.step();
                    state.dispose();
                    getMessagesStorage().getDatabase().commitTransaction();
                }

                deleteFromDownloadQueue(idsToDelete, true);
                AndroidUtilities.runOnUIThread(() -> getFileLoader().cancelLoadFiles(namesToDelete));
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
                    state = database.executeFast("UPDATE dialogs SET unread_count = ?, unread_count_i = ? WHERE did = ?");
                    state.requery();
                    state.bindInteger(1, Math.max(0, old_unread_count - counts[0]));
                    state.bindInteger(2, Math.max(0, old_mentions_count - counts[1]));
                    state.bindLong(3, did);
                    state.step();
                    state.dispose();
                }

                for (int a = 0, N = messagesByDialogs.size(); a < N; a++) {
                    long did = messagesByDialogs.keyAt(a);
                    ArrayList<Integer> mids = messagesByDialogs.valueAt(a);
                    String idsStr = TextUtils.join(",", mids);
                    if (!DialogObject.isEncryptedDialog(did)) {
                        if (DialogObject.isChatDialog(did)) {
                            database.executeFast(String.format(Locale.US, "UPDATE chat_settings_v2 SET pinned = 0 WHERE uid = %d AND pinned IN (%s)", -did, idsStr)).stepThis().dispose();
                        } else {
                            database.executeFast(String.format(Locale.US, "UPDATE user_settings SET pinned = 0 WHERE uid = %d AND pinned IN (%s)", did, idsStr)).stepThis().dispose();
                        }
                    }
                    database.executeFast(String.format(Locale.US, "DELETE FROM chat_pinned_v2 WHERE uid = %d AND mid IN(%s)", did, idsStr)).stepThis().dispose();
                    int updatedCount = 0;
                    cursor = database.queryFinalized("SELECT changes()");
                    if (cursor.next()) {
                        updatedCount = cursor.intValue(0);
                    }
                    cursor.dispose();
                    if (updatedCount > 0) {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT count FROM chat_pinned_count WHERE uid = %d", did));
                        if (cursor.next()) {
                            int count = cursor.intValue(0);
                            state = database.executeFast("UPDATE chat_pinned_count SET count = ? WHERE uid = ?");
                            state.requery();
                            state.bindInteger(1, Math.max(0, count - updatedCount));
                            state.bindLong(2, did);
                            state.step();
                            state.dispose();
                        }
                        cursor.dispose();
                    }
                    database.executeFast(String.format(Locale.US, "DELETE FROM messages_v2 WHERE mid IN(%s) AND uid = %d", ids, did)).stepThis().dispose();
                    database.executeFast(String.format(Locale.US, "DELETE FROM polls_v2 WHERE mid IN(%s) AND uid = %d", ids, did)).stepThis().dispose();
                    database.executeFast(String.format(Locale.US, "DELETE FROM bot_keyboard WHERE mid IN(%s) AND uid = %d", ids, did)).stepThis().dispose();
                    if (temp.isEmpty()) {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, type FROM media_v4 WHERE mid IN(%s) AND uid = %d", ids, did));
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
                            state = database.executeFast("REPLACE INTO media_counts_v2 VALUES(?, ?, ?, ?)");
                            for (int c = 0, N3 = mediaCounts.size(); c < N3; c++) {
                                int type = mediaCounts.keyAt(c);
                                LongSparseArray<Integer> value = mediaCounts.valueAt(c);
                                for (int b = 0, N2 = value.size(); b < N2; b++) {
                                    long uid = value.keyAt(b);
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
                    }
                    long time = System.currentTimeMillis();
                    database.executeFast(String.format(Locale.US, "DELETE FROM media_v4 WHERE mid IN(%s) AND uid = %d", ids, did)).stepThis().dispose();
                }
                database.executeFast(String.format(Locale.US, "DELETE FROM messages_seq WHERE mid IN(%s)", ids)).stepThis().dispose();
                if (!temp.isEmpty()) {
                    if (dialogId == 0) {
                        database.executeFast("UPDATE media_counts_v2 SET old = 1 WHERE 1").stepThis().dispose();
                    } else {
                        database.executeFast(String.format(Locale.US, "UPDATE media_counts_v2 SET old = 1 WHERE uid = %d", dialogId)).stepThis().dispose();
                    }
                }
                getMediaDataController().clearBotKeyboard(0, messages);

                if (dialogsToUpdate.size() != 0) {
                    resetAllUnreadCounters(false);
                }
                updateWidgets(dialogsIds);
            }
            return dialogsIds;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private void updateDialogsWithDeletedMessagesInternal(long originalDialogId, long channelId, ArrayList<Integer> messages, ArrayList<Long> additionalDialogsToUpdate) {
        try {
            ArrayList<Long> dialogsToUpdate = new ArrayList<>();
            if (!messages.isEmpty()) {
                SQLitePreparedStatement state;
                if (channelId != 0) {
                    dialogsToUpdate.add(-channelId);
                    state = database.executeFast("UPDATE dialogs SET last_mid = (SELECT mid FROM messages_v2 WHERE uid = ? AND date = (SELECT MAX(date) FROM messages_v2 WHERE uid = ?)) WHERE did = ?");
                } else {
                    if (originalDialogId == 0) {
                        String ids = TextUtils.join(",", messages);
                        SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT did FROM dialogs WHERE last_mid IN(%s) AND flags = 0", ids));
                        while (cursor.next()) {
                            dialogsToUpdate.add(cursor.longValue(0));
                        }
                        cursor.dispose();
                    } else {
                        dialogsToUpdate.add(originalDialogId);
                    }
                    state = database.executeFast("UPDATE dialogs SET last_mid = (SELECT mid FROM messages_v2 WHERE uid = ? AND date = (SELECT MAX(date) FROM messages_v2 WHERE uid = ? AND date != 0)) WHERE did = ?");
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
                dialogsToUpdate.add(-channelId);
            }
            if (additionalDialogsToUpdate != null) {
                for (int a = 0; a < additionalDialogsToUpdate.size(); a++) {
                    Long did = additionalDialogsToUpdate.get(a);
                    if (!dialogsToUpdate.contains(did)) {
                        dialogsToUpdate.add(did);
                    }
                }
            }
            String ids = TextUtils.join(",", dialogsToUpdate);

            TLRPC.messages_Dialogs dialogs = new TLRPC.TL_messages_dialogs();
            ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
            ArrayList<Long> usersToLoad = new ArrayList<>();
            ArrayList<Long> chatsToLoad = new ArrayList<>();
            ArrayList<Integer> encryptedToLoad = new ArrayList<>();
            SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT d.did, d.last_mid, d.unread_count, d.date, m.data, m.read_state, m.mid, m.send_state, m.date, d.pts, d.inbox_max, d.outbox_max, d.pinned, d.unread_count_i, d.flags, d.folder_id, d.data, d.unread_reactions FROM dialogs as d LEFT JOIN messages_v2 as m ON d.last_mid = m.mid AND d.did = m.uid WHERE d.did IN(%s)", ids));
            while (cursor.next()) {
                long dialogId = cursor.longValue(0);
                TLRPC.Dialog dialog;
                if (DialogObject.isFolderDialogId(dialogId)) {
                    TLRPC.TL_dialogFolder dialogFolder = new TLRPC.TL_dialogFolder();
                    if (!cursor.isNull(16)) {
                        NativeByteBuffer data = cursor.byteBufferValue(16);
                        if (data != null) {
                            dialogFolder.folder = TLRPC.TL_folder.TLdeserialize(data, data.readInt32(false), false);
                            data.reuse();
                        } else {
                            dialogFolder.folder = new TLRPC.TL_folder();
                            dialogFolder.folder.id = cursor.intValue(15);
                        }
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
                dialog.unread_reactions_count = cursor.intValue(17);

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
                if (DialogObject.isEncryptedDialog(dialogId)) {
                    int encryptedChatId = DialogObject.getEncryptedChatId(dialogId);
                    if (!encryptedToLoad.contains(encryptedChatId)) {
                        encryptedToLoad.add(encryptedChatId);
                    }
                } else if (DialogObject.isUserDialog(dialogId)) {
                    if (!usersToLoad.contains(dialogId)) {
                        usersToLoad.add(dialogId);
                    }
                } else {
                    if (!chatsToLoad.contains(-dialogId)) {
                        chatsToLoad.add(-dialogId);
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
                getMessagesController().processDialogsUpdate(dialogs, encryptedChats, true);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void updateDialogsWithDeletedMessages(long dialogId, long channelId, ArrayList<Integer> messages, ArrayList<Long> additionalDialogsToUpdate, boolean useQueue) {
        if (useQueue) {
            storageQueue.postRunnable(() -> updateDialogsWithDeletedMessagesInternal(dialogId, channelId, messages, additionalDialogsToUpdate));
        } else {
            updateDialogsWithDeletedMessagesInternal(dialogId, channelId, messages, additionalDialogsToUpdate);
        }
    }

    public ArrayList<Long> markMessagesAsDeleted(long dialogId, ArrayList<Integer> messages, boolean useQueue, boolean deleteFiles, boolean scheduled) {
        if (messages.isEmpty()) {
            return null;
        }
        if (useQueue) {
            storageQueue.postRunnable(() -> markMessagesAsDeletedInternal(dialogId, messages, deleteFiles, scheduled));
        } else {
            return markMessagesAsDeletedInternal(dialogId, messages, deleteFiles, scheduled);
        }
        return null;
    }

    private ArrayList<Long> markMessagesAsDeletedInternal(long channelId, int mid, boolean deleteFiles) {
        try {
            String ids;
            ArrayList<Long> dialogsIds = new ArrayList<>();
            LongSparseArray<Integer[]> dialogsToUpdate = new LongSparseArray<>();

            ArrayList<File> filesToDelete = new ArrayList<>();
            ArrayList<String> namesToDelete = new ArrayList<>();
            ArrayList<Pair<Long, Integer>> idsToDelete = new ArrayList<>();
            long currentUser = getUserConfig().getClientUserId();

            SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, data, read_state, out, mention FROM messages_v2 WHERE uid = %d AND mid <= %d", -channelId, mid));

            try {
                while (cursor.next()) {
                    long did = cursor.longValue(0);
                    if (did != currentUser) {
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
                    }
                    if (!DialogObject.isEncryptedDialog(did) && !deleteFiles) {
                        continue;
                    }
                    NativeByteBuffer data = cursor.byteBufferValue(1);
                    if (data != null) {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        message.readAttachPath(data, getUserConfig().clientUserId);
                        data.reuse();
                        addFilesToDelete(message, filesToDelete, idsToDelete, namesToDelete, false);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            cursor.dispose();

            deleteFromDownloadQueue(idsToDelete, true);
            AndroidUtilities.runOnUIThread(() -> getFileLoader().cancelLoadFiles(namesToDelete));
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


            database.executeFast(String.format(Locale.US, "UPDATE chat_settings_v2 SET pinned = 0 WHERE uid = %d AND pinned <= %d", channelId, mid)).stepThis().dispose();
            database.executeFast(String.format(Locale.US, "DELETE FROM chat_pinned_v2 WHERE uid = %d AND mid <= %d", channelId, mid)).stepThis().dispose();
            int updatedCount = 0;
            cursor = database.queryFinalized("SELECT changes()");
            if (cursor.next()) {
                updatedCount = cursor.intValue(0);
            }
            cursor.dispose();
            if (updatedCount > 0) {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT count FROM chat_pinned_count WHERE uid = %d", -channelId));
                if (cursor.next()) {
                    int count = cursor.intValue(0);
                    SQLitePreparedStatement state = database.executeFast("UPDATE chat_pinned_count SET count = ? WHERE uid = ?");
                    state.requery();
                    state.bindInteger(1, Math.max(0, count - updatedCount));
                    state.bindLong(2, -channelId);
                    state.step();
                    state.dispose();
                }
                cursor.dispose();
            }

            database.executeFast(String.format(Locale.US, "DELETE FROM messages_v2 WHERE uid = %d AND mid <= %d", -channelId, mid)).stepThis().dispose();
            database.executeFast(String.format(Locale.US, "DELETE FROM media_v4 WHERE uid = %d AND mid <= %d", -channelId, mid)).stepThis().dispose();
            database.executeFast(String.format(Locale.US, "UPDATE media_counts_v2 SET old = 1 WHERE uid = %d", -channelId)).stepThis().dispose();
            updateWidgets(dialogsIds);
            return dialogsIds;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public ArrayList<Long> markMessagesAsDeleted(long channelId, int mid, boolean useQueue, boolean deleteFiles) {
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
                message.media.bytes = Utilities.intToBytes(TLRPC.LAYER);
            }
        } else if (message.media instanceof TLRPC.TL_messageMediaUnsupported) {
            message.media = new TLRPC.TL_messageMediaUnsupported_old();
            message.media.bytes = Utilities.intToBytes(TLRPC.LAYER);
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

    private static class Hole {

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
                                FileLog.e(e, false);
                            }
                        }
                    } else if (minId <= hole.start + 1) {
                        if (hole.start != maxId) {
                            try {
                                database.executeFast(String.format(Locale.US, "UPDATE media_holes_v2 SET start = %d WHERE uid = %d AND type = %d AND start = %d AND end = %d", maxId, did, hole.type, hole.start, hole.end)).stepThis().dispose();
                            } catch (Exception e) {
                                FileLog.e(e, false);
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
                                FileLog.e(e, false);
                            }
                        }
                    } else if (minId <= hole.start + 1) {
                        if (hole.start != maxId) {
                            try {
                                database.executeFast(String.format(Locale.US, "UPDATE " + table + " SET start = %d WHERE uid = %d AND start = %d AND end = %d", maxId, did, hole.start, hole.end)).stepThis().dispose();
                            } catch (Exception e) {
                                FileLog.e(e, false);
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

    public void replaceMessageIfExists(TLRPC.Message message, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats, boolean broadcast) {
        if (message == null) {
            return;
        }
        storageQueue.postRunnable(() -> {
            try {
                SQLiteCursor cursor = null;
                int readState = 0;
                try {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, read_state FROM messages_v2 WHERE mid = %d AND uid = %d LIMIT 1", message.id, MessageObject.getDialogId(message)));
                    if (!cursor.next()) {
                        return;
                    }
                    readState = cursor.intValue(1);
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }

                database.beginTransaction();

                SQLitePreparedStatement state = database.executeFast("REPLACE INTO messages_v2 VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, 0)");
                SQLitePreparedStatement state2 = database.executeFast("REPLACE INTO media_v4 VALUES(?, ?, ?, ?, ?)");
                if (message.dialog_id == 0) {
                    MessageObject.getDialogId(message);
                }

                fixUnsupportedMedia(message);
                state.requery();
                NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                message.serializeToStream(data);
                state.bindInteger(1, message.id);
                state.bindLong(2, message.dialog_id);
                state.bindInteger(3, readState);
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
                int flags = 0;
                if (message.stickerVerified == 0) {
                    flags |= 1;
                } else if (message.stickerVerified == 2) {
                    flags |= 2;
                }
                state.bindInteger(10, flags);
                state.bindInteger(11, message.mentioned ? 1 : 0);
                state.bindInteger(12, message.forwards);
                NativeByteBuffer repliesData = null;
                if (message.replies != null) {
                    repliesData = new NativeByteBuffer(message.replies.getObjectSize());
                    message.replies.serializeToStream(repliesData);
                    state.bindByteBuffer(13, repliesData);
                } else {
                    state.bindNull(13);
                }
                if (message.reply_to != null) {
                    state.bindInteger(14, message.reply_to.reply_to_top_id != 0 ? message.reply_to.reply_to_top_id : message.reply_to.reply_to_msg_id);
                } else {
                    state.bindInteger(14, 0);
                }
                state.bindLong(15, MessageObject.getChannelId(message));
                state.step();

                if (MediaDataController.canAddMessageToMedia(message)) {
                    state2.requery();
                    state2.bindInteger(1, message.id);
                    state2.bindLong(2, message.dialog_id);
                    state2.bindInteger(3, message.date);
                    state2.bindInteger(4, MediaDataController.getMediaType(message));
                    state2.bindByteBuffer(5, data);
                    state2.step();
                }
                if (repliesData != null) {
                    repliesData.reuse();
                }
                data.reuse();

                state.dispose();
                state2.dispose();

                database.commitTransaction();
                if (broadcast) {
                    HashMap<Long, TLRPC.User> userHashMap = new HashMap<>();
                    HashMap<Long, TLRPC.Chat> chatHashMap = new HashMap<>();
                    for (int a = 0; a < users.size(); a++) {
                        TLRPC.User user = users.get(a);
                        userHashMap.put(user.id, user);
                    }
                    for (int a = 0; a < chats.size(); a++) {
                        TLRPC.Chat chat = chats.get(a);
                        chatHashMap.put(chat.id, chat);
                    }
                    MessageObject messageObject = new MessageObject(currentAccount, message, userHashMap, chatHashMap, true, true);
                    ArrayList<MessageObject> arrayList = new ArrayList<>();
                    arrayList.add(messageObject);
                    AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.replaceMessagesObjects, messageObject.getDialogId(), arrayList));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void putMessages(TLRPC.messages_Messages messages, long dialogId, int load_type, int max_id, boolean createDialog, boolean scheduled) {
        storageQueue.postRunnable(() -> {
            try {
                if (scheduled) {
                    database.executeFast(String.format(Locale.US, "DELETE FROM scheduled_messages_v2 WHERE uid = %d AND mid > 0", dialogId)).stepThis().dispose();
                    SQLitePreparedStatement state_messages = database.executeFast("REPLACE INTO scheduled_messages_v2 VALUES(?, ?, ?, ?, ?, ?, NULL, 0)");
                    int count = messages.messages.size();
                    for (int a = 0; a < count; a++) {
                        TLRPC.Message message = messages.messages.get(a);
                        if (message instanceof TLRPC.TL_messageEmpty) {
                            continue;
                        }

                        fixUnsupportedMedia(message);
                        state_messages.requery();
                        NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                        message.serializeToStream(data);
                        state_messages.bindInteger(1, message.id);
                        state_messages.bindLong(2, dialogId);
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
                    broadcastScheduledMessagesChange(dialogId);
                } else {
                    int mentionCountUpdate = Integer.MAX_VALUE;
                    if (messages.messages.isEmpty()) {
                        if (load_type == 0) {
                            doneHolesInTable("messages_holes", dialogId, max_id);
                            doneHolesInMedia(dialogId, max_id, -1);
                        }
                        return;
                    }
                    database.beginTransaction();

                    if (load_type == 0) {
                        int minId = messages.messages.get(messages.messages.size() - 1).id;
                        closeHolesInTable("messages_holes", dialogId, minId, max_id);
                        closeHolesInMedia(dialogId, minId, max_id, -1);
                    } else if (load_type == 1) {
                        int maxId = messages.messages.get(0).id;
                        closeHolesInTable("messages_holes", dialogId, max_id, maxId);
                        closeHolesInMedia(dialogId, max_id, maxId, -1);
                    } else if (load_type == 3 || load_type == 2 || load_type == 4) {
                        int maxId = max_id == 0 && load_type != 4 ? Integer.MAX_VALUE : messages.messages.get(0).id;
                        int minId = messages.messages.get(messages.messages.size() - 1).id;
                        closeHolesInTable("messages_holes", dialogId, minId, maxId);
                        closeHolesInMedia(dialogId, minId, maxId, -1);
                    }
                    int count = messages.messages.size();

                    //load_type == 0 ? backward loading
                    //load_type == 1 ? forward loading
                    //load_type == 2 ? load from first unread
                    //load_type == 3 ? load around message
                    //load_type == 4 ? load around date
                    ArrayList<File> filesToDelete = new ArrayList<>();
                    ArrayList<String> namesToDelete = new ArrayList<>();
                    ArrayList<Pair<Long, Integer>> idsToDelete = new ArrayList<>();

                    SQLitePreparedStatement state_messages = database.executeFast("REPLACE INTO messages_v2 VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, 0)");
                    SQLitePreparedStatement state_media = database.executeFast("REPLACE INTO media_v4 VALUES(?, ?, ?, ?, ?)");
                    SQLitePreparedStatement state_polls = null;
                    SQLitePreparedStatement state_webpage = null;
                    SQLitePreparedStatement state_tasks = null;
                    int minDeleteTime = Integer.MAX_VALUE;
                    TLRPC.Message botKeyboard = null;
                    long channelId = 0;
                    for (int a = 0; a < count; a++) {
                        TLRPC.Message message = messages.messages.get(a);

                        if (channelId == 0) {
                            channelId = message.peer_id.channel_id;
                        }

                        if (load_type == -2) {
                            SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid, data, ttl, mention, read_state, send_state FROM messages_v2 WHERE mid = %d AND uid = %d", message.id, MessageObject.getDialogId(message)));
                            boolean exist;
                            if (exist = cursor.next()) {
                                NativeByteBuffer data = cursor.byteBufferValue(1);
                                if (data != null) {
                                    TLRPC.Message oldMessage = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                    oldMessage.readAttachPath(data, getUserConfig().clientUserId);
                                    data.reuse();
                                    int send_state = cursor.intValue(5);
                                    if (send_state != 3) {
                                        if (MessageObject.getFileName(oldMessage).equals(MessageObject.getFileName(message))) {
                                            message.attachPath = oldMessage.attachPath;
                                        }
                                        message.ttl = cursor.intValue(2);
                                    }
                                    boolean sameMedia = false;
                                    if (oldMessage.media instanceof TLRPC.TL_messageMediaPhoto && message.media instanceof TLRPC.TL_messageMediaPhoto && oldMessage.media.photo != null && message.media.photo != null) {
                                        sameMedia = oldMessage.media.photo.id == message.media.photo.id;
                                    } else if (oldMessage.media instanceof TLRPC.TL_messageMediaDocument && message.media instanceof TLRPC.TL_messageMediaDocument && oldMessage.media.document != null && message.media.document != null) {
                                        sameMedia = oldMessage.media.document.id == message.media.document.id;
                                    }
                                    if (!sameMedia) {
                                        addFilesToDelete(oldMessage, filesToDelete, idsToDelete, namesToDelete, false);
                                    }
                                }
                                boolean oldMention = cursor.intValue(3) != 0;
                                int readState = cursor.intValue(4);
                                if (oldMention != message.mentioned) {
                                    if (mentionCountUpdate == Integer.MAX_VALUE) {
                                        SQLiteCursor cursor2 = database.queryFinalized("SELECT unread_count_i FROM dialogs WHERE did = " + dialogId);
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
                            SQLiteCursor cursor = database.queryFinalized("SELECT pinned, unread_count_i, flags FROM dialogs WHERE did = " + dialogId);
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
                                state3.bindInteger(2, message.id);
                                state3.bindInteger(3, message.id);
                                state3.bindInteger(4, message.id);
                                state3.bindInteger(5, messages.pts);
                                state3.bindInteger(6, message.date);
                                state3.bindLong(7, dialogId);
                            } else {
                                state3 = database.executeFast("REPLACE INTO dialogs VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                                state3.bindLong(1, dialogId);
                                state3.bindInteger(2, message.date);
                                state3.bindInteger(3, 0);
                                state3.bindInteger(4, message.id);
                                state3.bindInteger(5, message.id);
                                state3.bindInteger(6, 0);
                                state3.bindInteger(7, message.id);
                                state3.bindInteger(8, mentions);
                                state3.bindInteger(9, messages.pts);
                                state3.bindInteger(10, message.date);
                                state3.bindInteger(11, pinned);
                                state3.bindInteger(12, flags);
                                state3.bindInteger(13, -1);
                                state3.bindNull(14);
                                state3.bindInteger(15, 0);
                                unknownDialogsIds.put(dialogId, true);
                            }
                            state3.step();
                            state3.dispose();
                        }

                        fixUnsupportedMedia(message);
                        state_messages.requery();
                        NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                        message.serializeToStream(data);
                        state_messages.bindInteger(1, message.id);
                        state_messages.bindLong(2, dialogId);
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
                        int flags = 0;
                        if (message.stickerVerified == 0) {
                            flags |= 1;
                        } else if (message.stickerVerified == 2) {
                            flags |= 2;
                        }
                        state_messages.bindInteger(10, flags);
                        state_messages.bindInteger(11, message.mentioned ? 1 : 0);
                        state_messages.bindInteger(12, message.forwards);
                        NativeByteBuffer repliesData = null;
                        if (message.replies != null) {
                            repliesData = new NativeByteBuffer(message.replies.getObjectSize());
                            message.replies.serializeToStream(repliesData);
                            state_messages.bindByteBuffer(13, repliesData);
                        } else {
                            state_messages.bindNull(13);
                        }
                        if (message.reply_to != null) {
                            state_messages.bindInteger(14, message.reply_to.reply_to_top_id != 0 ? message.reply_to.reply_to_top_id : message.reply_to.reply_to_msg_id);
                        } else {
                            state_messages.bindInteger(14, 0);
                        }
                        state_messages.bindLong(15, MessageObject.getChannelId(message));
                        state_messages.step();

                        if (MediaDataController.canAddMessageToMedia(message)) {
                            state_media.requery();
                            state_media.bindInteger(1, message.id);
                            state_media.bindLong(2, dialogId);
                            state_media.bindInteger(3, message.date);
                            state_media.bindInteger(4, MediaDataController.getMediaType(message));
                            state_media.bindByteBuffer(5, data);
                            state_media.step();
                        } else if (message instanceof TLRPC.TL_messageService && message.action instanceof TLRPC.TL_messageActionHistoryClear) {
                            try {
                                database.executeFast(String.format(Locale.US, "DELETE FROM media_v4 WHERE mid = %d AND uid = %d", message.id, dialogId)).stepThis().dispose();
                                database.executeFast("DELETE FROM media_counts_v2 WHERE uid = " + dialogId).stepThis().dispose();
                            } catch (Exception e2) {
                                FileLog.e(e2);
                            }
                        }
                        if (repliesData != null) {
                            repliesData.reuse();
                        }
                        data.reuse();

                        if (message.ttl_period != 0 && message.id > 0) {
                            if (state_tasks == null) {
                                state_tasks = database.executeFast("REPLACE INTO enc_tasks_v4 VALUES(?, ?, ?, ?)");
                            }
                            state_tasks.requery();
                            state_tasks.bindInteger(1, message.id);
                            state_tasks.bindLong(2, message.dialog_id);
                            state_tasks.bindInteger(3, message.date + message.ttl_period);
                            state_tasks.bindInteger(4, 0);
                            state_tasks.step();
                            minDeleteTime = Math.min(minDeleteTime, message.date + message.ttl_period);
                        }

                        if (message.media instanceof TLRPC.TL_messageMediaPoll) {
                            if (state_polls == null) {
                                state_polls = database.executeFast("REPLACE INTO polls_v2 VALUES(?, ?, ?)");
                            }
                            TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) message.media;
                            state_polls.requery();
                            state_polls.bindInteger(1, message.id);
                            state_polls.bindLong(2, message.dialog_id);
                            state_polls.bindLong(3, mediaPoll.poll.id);
                            state_polls.step();
                        } else if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
                            if (state_webpage == null) {
                                state_webpage = database.executeFast("REPLACE INTO webpage_pending_v2 VALUES(?, ?, ?)");
                            }
                            state_webpage.requery();
                            state_webpage.bindLong(1, message.media.webpage.id);
                            state_webpage.bindInteger(2, message.id);
                            state_webpage.bindLong(3, message.dialog_id);
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
                    if (state_tasks != null) {
                        state_tasks.dispose();
                        getMessagesController().didAddedNewTask(minDeleteTime, 0, null);
                    }
                    if (state_polls != null) {
                        state_polls.dispose();
                    }
                    if (botKeyboard != null) {
                        getMediaDataController().putBotKeyboard(dialogId, botKeyboard);
                    }
                    deleteFromDownloadQueue(idsToDelete, false);
                    AndroidUtilities.runOnUIThread(() -> getFileLoader().cancelLoadFiles(namesToDelete));
                    getFileLoader().deleteFiles(filesToDelete, 0);
                    putUsersInternal(messages.users);
                    putChatsInternal(messages.chats);

                    if (mentionCountUpdate != Integer.MAX_VALUE) {
                        database.executeFast(String.format(Locale.US, "UPDATE dialogs SET unread_count_i = %d WHERE did = %d", mentionCountUpdate, dialogId)).stepThis().dispose();
                        LongSparseIntArray sparseArray = new LongSparseIntArray(1);
                        sparseArray.put(dialogId, mentionCountUpdate);
                        getMessagesController().processDialogsUpdateRead(null, sparseArray);
                    }

                    database.commitTransaction();

                    if (createDialog) {
                        updateDialogsWithDeletedMessages(dialogId, channelId, new ArrayList<>(), null, false);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public static void addUsersAndChatsFromMessage(TLRPC.Message message, ArrayList<Long> usersToLoad, ArrayList<Long> chatsToLoad) {
        long fromId = MessageObject.getFromChatId(message);
        if (DialogObject.isUserDialog(fromId)) {
            if (!usersToLoad.contains(fromId)) {
                usersToLoad.add(fromId);
            }
        } else if (DialogObject.isChatDialog(fromId)) {
            if (!chatsToLoad.contains(-fromId)) {
                chatsToLoad.add(-fromId);
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
            if (message.action instanceof TLRPC.TL_messageActionGeoProximityReached) {
                TLRPC.TL_messageActionGeoProximityReached action = (TLRPC.TL_messageActionGeoProximityReached) message.action;
                long id = MessageObject.getPeerId(action.from_id);
                if (DialogObject.isUserDialog(id)) {
                    if (!usersToLoad.contains(id)) {
                        usersToLoad.add(id);
                    }
                } else if (!chatsToLoad.contains(-id)) {
                    chatsToLoad.add(-id);
                }
                id = MessageObject.getPeerId(action.to_id);
                if (id > 0) {
                    if (!usersToLoad.contains(id)) {
                        usersToLoad.add(id);
                    }
                } else if (!chatsToLoad.contains(-id)) {
                    chatsToLoad.add(-id);
                }
            }
            if (!message.action.users.isEmpty()) {
                for (int a = 0; a < message.action.users.size(); a++) {
                    Long uid = message.action.users.get(a);
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
            if (message.media instanceof TLRPC.TL_messageMediaPoll) {
                TLRPC.TL_messageMediaPoll messageMediaPoll = (TLRPC.TL_messageMediaPoll) message.media;
                if (!messageMediaPoll.results.recent_voters.isEmpty()) {
                    usersToLoad.addAll(messageMediaPoll.results.recent_voters);
                }
            }
        }
        if (message.replies != null) {
            for (int a = 0, N = message.replies.recent_repliers.size(); a < N; a++) {
                long id = MessageObject.getPeerId(message.replies.recent_repliers.get(a));
                if (DialogObject.isUserDialog(id)) {
                    if (!usersToLoad.contains(id)) {
                        usersToLoad.add(id);
                    }
                } else if (DialogObject.isChatDialog(id)) {
                    if (!chatsToLoad.contains(-id)) {
                        chatsToLoad.add(-id);
                    }
                }
            }
        }
        if (message.reply_to != null && message.reply_to.reply_to_peer_id != null) {
            long id = MessageObject.getPeerId(message.reply_to.reply_to_peer_id);
            if (DialogObject.isUserDialog(id)) {
                if (!usersToLoad.contains(id)) {
                    usersToLoad.add(id);
                }
            } else if (DialogObject.isChatDialog(id)) {
                if (!chatsToLoad.contains(-id)) {
                    chatsToLoad.add(-id);
                }
            }
        }
        if (message.fwd_from != null) {
            if (message.fwd_from.from_id instanceof TLRPC.TL_peerUser) {
                if (!usersToLoad.contains(message.fwd_from.from_id.user_id)) {
                    usersToLoad.add(message.fwd_from.from_id.user_id);
                }
            } else if (message.fwd_from.from_id instanceof TLRPC.TL_peerChannel) {
                if (!chatsToLoad.contains(message.fwd_from.from_id.channel_id)) {
                    chatsToLoad.add(message.fwd_from.from_id.channel_id);
                }
            } else if (message.fwd_from.from_id instanceof TLRPC.TL_peerChat) {
                if (!chatsToLoad.contains(message.fwd_from.from_id.chat_id)) {
                    chatsToLoad.add(message.fwd_from.from_id.chat_id);
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
        if (message.params != null) {
            String peerIdStr = message.params.get("fwd_peer");
            if (peerIdStr != null) {
                long peerId = Utilities.parseLong(peerIdStr);
                if (peerId < 0) {
                    if (!chatsToLoad.contains(-peerId)) {
                        chatsToLoad.add(-peerId);
                    }
                }
            }
        }
    }

    public void getDialogs(int folderId, int offset, int count, boolean loadDraftsPeersAndFolders) {
        long[] draftsDialogIds;
        if (loadDraftsPeersAndFolders) {
            LongSparseArray<SparseArray<TLRPC.DraftMessage>> drafts = getMediaDataController().getDrafts();
            int draftsCount = drafts.size();
            if (draftsCount > 0) {
                draftsDialogIds = new long[draftsCount];
                for (int i = 0; i < draftsCount; i++) {
                    SparseArray<TLRPC.DraftMessage> threads = drafts.valueAt(i);
                    if (threads.get(0) == null) {
                        continue;
                    }
                    draftsDialogIds[i] = drafts.keyAt(i);
                }
            } else {
                draftsDialogIds = null;
            }
        } else {
            draftsDialogIds = null;
        }
        storageQueue.postRunnable(() -> {
            TLRPC.messages_Dialogs dialogs = new TLRPC.TL_messages_dialogs();
            ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
            try {
                ArrayList<Long> usersToLoad = new ArrayList<>();
                usersToLoad.add(getUserConfig().getClientUserId());
                ArrayList<Long> chatsToLoad = new ArrayList<>();
                ArrayList<Integer> encryptedToLoad = new ArrayList<>();
                ArrayList<Long> loadedDialogs = new ArrayList<>();
                LongSparseArray<SparseArray<ArrayList<TLRPC.Message>>> replyMessageOwners = new LongSparseArray<>();
                LongSparseArray<ArrayList<Integer>> dialogReplyMessagesIds = new LongSparseArray<>();
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
                        cnt = 100;
                    }

                    SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT d.did, d.last_mid, d.unread_count, d.date, m.data, m.read_state, m.mid, m.send_state, s.flags, m.date, d.pts, d.inbox_max, d.outbox_max, m.replydata, d.pinned, d.unread_count_i, d.flags, d.folder_id, d.data, d.unread_reactions FROM dialogs as d LEFT JOIN messages_v2 as m ON d.last_mid = m.mid AND d.did = m.uid LEFT JOIN dialog_settings as s ON d.did = s.did WHERE d.folder_id = %d ORDER BY d.pinned DESC, d.date DESC LIMIT %d,%d", fid, off, cnt));
                    while (cursor.next()) {
                        long dialogId = cursor.longValue(0);
                        TLRPC.Dialog dialog;
                        if (DialogObject.isFolderDialogId(dialogId)) {
                            TLRPC.TL_dialogFolder dialogFolder = new TLRPC.TL_dialogFolder();
                            if (!cursor.isNull(18)) {
                                NativeByteBuffer data = cursor.byteBufferValue(18);
                                if (data != null) {
                                    dialogFolder.folder = TLRPC.TL_folder.TLdeserialize(data, data.readInt32(false), false);
                                    data.reuse();
                                } else {
                                    dialogFolder.folder = new TLRPC.TL_folder();
                                    dialogFolder.folder.id = DialogObject.getFolderId(dialogId);
                                }
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
                        dialog.flags = dialog.pts == 0 || DialogObject.isUserDialog(dialog.id) ? 0 : 1;
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
                        dialog.unread_reactions_count = cursor.intValue(19);
                        dialogs.dialogs.add(dialog);

                        if (draftsDialogIds != null) {
                            loadedDialogs.add(dialogId);
                        }

                        NativeByteBuffer data = cursor.byteBufferValue(4);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            if (message != null) {
                                message.readAttachPath(data, getUserConfig().clientUserId);
                                data.reuse();
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
                                    if (message.reply_to != null && message.reply_to.reply_to_msg_id != 0 && (
                                            message.action instanceof TLRPC.TL_messageActionPinMessage ||
                                                    message.action instanceof TLRPC.TL_messageActionPaymentSent ||
                                                    message.action instanceof TLRPC.TL_messageActionGameScore)) {
                                        if (!cursor.isNull(13)) {
                                            NativeByteBuffer data2 = cursor.byteBufferValue(13);
                                            if (data2 != null) {
                                                message.replyMessage = TLRPC.Message.TLdeserialize(data2, data2.readInt32(false), false);
                                                message.replyMessage.readAttachPath(data2, getUserConfig().clientUserId);
                                                data2.reuse();
                                                if (message.replyMessage != null) {
                                                    addUsersAndChatsFromMessage(message.replyMessage, usersToLoad, chatsToLoad);
                                                }
                                            }
                                        }
                                        if (message.replyMessage == null) {
                                            addReplyMessages(message, replyMessageOwners, dialogReplyMessagesIds);
                                        }
                                    }
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            } else {
                                data.reuse();
                            }
                        }

                        if (DialogObject.isEncryptedDialog(dialogId)) {
                            int encryptedChatId = DialogObject.getEncryptedChatId(dialogId);
                            if (!encryptedToLoad.contains(encryptedChatId)) {
                                encryptedToLoad.add(encryptedChatId);
                            }
                        } else if (DialogObject.isUserDialog(dialogId)) {
                            if (!usersToLoad.contains(dialogId)) {
                                usersToLoad.add(dialogId);
                            }
                        } else if (DialogObject.isChatDialog(dialogId)) {
                            if (!chatsToLoad.contains(-dialogId)) {
                                chatsToLoad.add(-dialogId);
                            }
                        }
                    }
                    cursor.dispose();
                }

                loadReplyMessages(replyMessageOwners, dialogReplyMessagesIds, usersToLoad, chatsToLoad);

                if (draftsDialogIds != null) {
                    ArrayList<Long> unloadedDialogs = new ArrayList<>();

                    for (int i = 0; i < draftsDialogIds.length; i++) {
                        long dialogId = draftsDialogIds[i];
                        if (DialogObject.isEncryptedDialog(dialogId)) {
                            continue;
                        }
                        if (dialogId > 0) {
                            if (!usersToLoad.contains(dialogId)) {
                                usersToLoad.add(dialogId);
                            }
                        } else {
                            if (!chatsToLoad.contains(-dialogId)) {
                                chatsToLoad.add(-dialogId);
                            }
                        }
                        if (!loadedDialogs.contains(draftsDialogIds[i])) {
                            unloadedDialogs.add(draftsDialogIds[i]);
                        }
                    }

                    LongSparseArray<Integer> folderIds;

                    if (!unloadedDialogs.isEmpty()) {
                        folderIds = new LongSparseArray<>(unloadedDialogs.size());
                        SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT did, folder_id FROM dialogs WHERE did IN(%s)", TextUtils.join(",", unloadedDialogs)));
                        while (cursor.next()) {
                            folderIds.put(cursor.longValue(0), cursor.intValue(1));
                        }
                        cursor.dispose();
                    } else {
                        folderIds = null;
                    }

                    AndroidUtilities.runOnUIThread(() -> {
                        MediaDataController mediaDataController = getMediaDataController();
                        mediaDataController.clearDraftsFolderIds();
                        if (folderIds != null) {
                            for (int i = 0, size = folderIds.size(); i < size; i++) {
                                mediaDataController.setDraftFolderId(folderIds.keyAt(i), folderIds.valueAt(i));
                            }
                        }
                    });
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

    private void putDialogsInternal(TLRPC.messages_Dialogs dialogs, int check) {
        try {
            database.beginTransaction();
            LongSparseArray<TLRPC.Message> new_dialogMessage = new LongSparseArray<>(dialogs.messages.size());
            for (int a = 0; a < dialogs.messages.size(); a++) {
                TLRPC.Message message = dialogs.messages.get(a);
                new_dialogMessage.put(MessageObject.getDialogId(message), message);
            }

            if (!dialogs.dialogs.isEmpty()) {
                SQLitePreparedStatement state_messages = database.executeFast("REPLACE INTO messages_v2 VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, 0)");
                SQLitePreparedStatement state_dialogs = database.executeFast("REPLACE INTO dialogs VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                SQLitePreparedStatement state_media = database.executeFast("REPLACE INTO media_v4 VALUES(?, ?, ?, ?, ?)");
                SQLitePreparedStatement state_settings = database.executeFast("REPLACE INTO dialog_settings VALUES(?, ?)");
                SQLitePreparedStatement state_holes = database.executeFast("REPLACE INTO messages_holes VALUES(?, ?, ?)");
                SQLitePreparedStatement state_media_holes = database.executeFast("REPLACE INTO media_holes_v2 VALUES(?, ?, ?, ?)");
                SQLitePreparedStatement state_polls = null;
                SQLitePreparedStatement state_tasks = null;
                int minDeleteTime = Integer.MAX_VALUE;

                for (int a = 0; a < dialogs.dialogs.size(); a++) {
                    TLRPC.Dialog dialog = dialogs.dialogs.get(a);
                    boolean exists = false;
                    DialogObject.initDialog(dialog);
                    unknownDialogsIds.remove(dialog.id);

                    if (check == 1) {
                        SQLiteCursor cursor = database.queryFinalized("SELECT did FROM dialogs WHERE did = " + dialog.id);
                        exists = cursor.next();
                        cursor.dispose();
                        if (exists) {
                            continue;
                        }
                    } else if (check == 2) {
                        SQLiteCursor cursor = database.queryFinalized("SELECT pinned FROM dialogs WHERE did = " + dialog.id);
                        if (cursor.next()) {
                            exists = true;
                            if (dialog.pinned) {
                                dialog.pinnedNum = cursor.intValue(0);
                            }
                        }
                        cursor.dispose();
                    } else if (check == 3) {
                        int mid = 0;
                        SQLiteCursor cursor = database.queryFinalized("SELECT last_mid FROM dialogs WHERE did = " + dialog.id);
                        if (cursor.next()) {
                            mid = cursor.intValue(0);
                        }
                        cursor.dispose();
                        if (mid < 0) {
                            continue;
                        }
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

                        state_messages.requery();
                        state_messages.bindInteger(1, message.id);
                        state_messages.bindLong(2, dialog.id);
                        state_messages.bindInteger(3, MessageObject.getUnreadFlags(message));
                        state_messages.bindInteger(4, message.send_state);
                        state_messages.bindInteger(5, message.date);
                        state_messages.bindByteBuffer(6, data);
                        state_messages.bindInteger(7, (MessageObject.isOut(message) || message.from_scheduled ? 1 : 0));
                        state_messages.bindInteger(8, 0);
                        state_messages.bindInteger(9, (message.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0 ? message.views : 0);
                        int flags = 0;
                        if (message.stickerVerified == 0) {
                            flags |= 1;
                        } else if (message.stickerVerified == 2) {
                            flags |= 2;
                        }
                        state_messages.bindInteger(10, flags);
                        state_messages.bindInteger(11, message.mentioned ? 1 : 0);
                        state_messages.bindInteger(12, message.forwards);
                        NativeByteBuffer repliesData = null;
                        if (message.replies != null) {
                            repliesData = new NativeByteBuffer(message.replies.getObjectSize());
                            message.replies.serializeToStream(repliesData);
                            state_messages.bindByteBuffer(13, repliesData);
                        } else {
                            state_messages.bindNull(13);
                        }
                        if (message.reply_to != null) {
                            state_messages.bindInteger(14, message.reply_to.reply_to_top_id != 0 ? message.reply_to.reply_to_top_id : message.reply_to.reply_to_msg_id);
                        } else {
                            state_messages.bindInteger(14, 0);
                        }
                        state_messages.bindLong(15, MessageObject.getChannelId(message));
                        state_messages.step();

                        if (MediaDataController.canAddMessageToMedia(message)) {
                            state_media.requery();
                            state_media.bindInteger(1, message.id);
                            state_media.bindLong(2, dialog.id);
                            state_media.bindInteger(3, message.date);
                            state_media.bindInteger(4, MediaDataController.getMediaType(message));
                            state_media.bindByteBuffer(5, data);
                            state_media.step();
                        }
                        if (repliesData != null) {
                            repliesData.reuse();
                        }
                        data.reuse();

                        if (message.ttl_period != 0 && message.id > 0) {
                            if (state_tasks == null) {
                                state_tasks = database.executeFast("REPLACE INTO enc_tasks_v4 VALUES(?, ?, ?, ?)");
                            }
                            state_tasks.requery();
                            state_tasks.bindInteger(1, message.id);
                            state_tasks.bindLong(2, message.dialog_id);
                            state_tasks.bindInteger(3, message.date + message.ttl_period);
                            state_tasks.bindInteger(4, 0);
                            state_tasks.step();
                            minDeleteTime = Math.min(minDeleteTime, message.date + message.ttl_period);
                        }

                        if (message.media instanceof TLRPC.TL_messageMediaPoll) {
                            if (state_polls == null) {
                                state_polls = database.executeFast("REPLACE INTO polls_v2 VALUES(?, ?, ?)");
                            }
                            TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) message.media;
                            state_polls.requery();
                            state_polls.bindInteger(1, message.id);
                            state_polls.bindLong(2, message.dialog_id);
                            state_polls.bindLong(3, mediaPoll.poll.id);
                            state_polls.step();
                        }

                        if (exists) {
                            closeHolesInTable("messages_holes", dialog.id, message.id, message.id);
                            closeHolesInMedia(dialog.id, message.id, message.id, -1);
                        } else {
                            createFirstHoles(dialog.id, state_holes, state_media_holes, message.id);
                        }
                    }

                    state_dialogs.requery();
                    state_dialogs.bindLong(1, dialog.id);
                    state_dialogs.bindInteger(2, messageDate);
                    state_dialogs.bindInteger(3, dialog.unread_count);
                    state_dialogs.bindInteger(4, dialog.top_message);
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
                    state_dialogs.bindInteger(15, dialog.unread_reactions_count);
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
                if (state_tasks != null) {
                    state_tasks.dispose();
                    getMessagesController().didAddedNewTask(minDeleteTime, 0, null);
                }
                if (state_polls != null) {
                    state_polls.dispose();
                }
            }

            putUsersInternal(dialogs.users);
            putChatsInternal(dialogs.chats);

            database.commitTransaction();
            resetAllUnreadCounters(false);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void getDialogFolderId(long dialogId, IntCallback callback) {
        storageQueue.postRunnable(() -> {
            try {
                int folderId;
                if (unknownDialogsIds.get(dialogId) != null) {
                    folderId = -1;
                } else {
                    SQLiteCursor cursor = database.queryFinalized("SELECT folder_id FROM dialogs WHERE did = ?", dialogId);
                    if (cursor.next()) {
                        folderId = cursor.intValue(0);
                    } else {
                        folderId = -1;
                    }
                    cursor.dispose();
                }
                AndroidUtilities.runOnUIThread(() -> callback.run(folderId));
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void setDialogsFolderId(ArrayList<TLRPC.TL_folderPeer> peers, ArrayList<TLRPC.TL_inputFolderPeer> inputPeers, long dialogId, int folderId) {
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
                        unknownDialogsIds.remove(did);
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
                        unknownDialogsIds.remove(did);
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
                resetAllUnreadCounters(false);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void checkIfFolderEmptyInternal(int folderId) {
        try {
            SQLiteCursor cursor = database.queryFinalized("SELECT did FROM dialogs WHERE folder_id = ?", folderId);
            boolean isEmpty = true;
            while (cursor.next()) {
                long did = cursor.longValue(0);
                if (DialogObject.isUserDialog(did) || DialogObject.isEncryptedDialog(did)) {
                    isEmpty = false;
                    break;
                } else {
                    TLRPC.Chat chat = getChat(-did);
                    if (!ChatObject.isNotInChat(chat) && chat.migrated_to == null) {
                        isEmpty = false;
                        break;
                    }
                }
            }
            cursor.dispose();
            if (isEmpty) {
                AndroidUtilities.runOnUIThread(() -> getMessagesController().onFolderEmpty(folderId));
                database.executeFast("DELETE FROM dialogs WHERE did = " + DialogObject.makeFolderDialogId(folderId)).stepThis().dispose();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void checkIfFolderEmpty(int folderId) {
        storageQueue.postRunnable(() -> checkIfFolderEmptyInternal(folderId));
    }

    public void unpinAllDialogsExceptNew(ArrayList<Long> dids, int folderId) {
        storageQueue.postRunnable(() -> {
            try {
                ArrayList<Long> unpinnedDialogs = new ArrayList<>();
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT did, folder_id FROM dialogs WHERE pinned > 0 AND did NOT IN (%s)", TextUtils.join(",", dids)));
                while (cursor.next()) {
                    long did = cursor.longValue(0);
                    int fid = cursor.intValue(1);
                    if (fid == folderId && !DialogObject.isEncryptedDialog(did) && !DialogObject.isFolderDialogId(did)) {
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

    public void setDialogUnread(long did, boolean unread) {
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
                resetAllUnreadCounters(false);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void resetAllUnreadCounters(boolean muted) {
        for (int a = 0, N = dialogFilters.size(); a < N; a++) {
            MessagesController.DialogFilter filter = dialogFilters.get(a);
            if (muted) {
                if ((filter.flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) != 0) {
                    filter.pendingUnreadCount = -1;
                }
            } else {
                filter.pendingUnreadCount = -1;
            }
        }
        calcUnreadCounters(false);
        AndroidUtilities.runOnUIThread(() -> {
            ArrayList<MessagesController.DialogFilter> filters = getMessagesController().dialogFilters;
            for (int a = 0, N = filters.size(); a < N; a++) {
                filters.get(a).unreadCount = filters.get(a).pendingUnreadCount;
            }
            mainUnreadCount = pendingMainUnreadCount;
            archiveUnreadCount = pendingArchiveUnreadCount;
            getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_READ_DIALOG_MESSAGE);
        });
    }

    public void setDialogPinned(long did, int pinned) {
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

    public void setDialogsPinned(ArrayList<Long> dids, ArrayList<Integer> pinned) {
        storageQueue.postRunnable(() -> {
            try {
                SQLitePreparedStatement state = database.executeFast("UPDATE dialogs SET pinned = ? WHERE did = ?");
                for (int a = 0, N = dids.size(); a < N; a++) {
                    state.requery();
                    state.bindInteger(1, pinned.get(a));
                    state.bindLong(2, dids.get(a));
                    state.step();
                }
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void putDialogs(TLRPC.messages_Dialogs dialogs, int check) {
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

    public void getDialogMaxMessageId(long dialog_id, IntCallback callback) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            int[] max = new int[1];
            try {
                cursor = database.queryFinalized("SELECT MAX(mid) FROM messages_v2 WHERE uid = " + dialog_id);
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
            AndroidUtilities.runOnUIThread(() -> callback.run(max[0]));
        });
    }

    public int getDialogReadMax(boolean outbox, long dialog_id) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Integer[] max = new Integer[]{0};
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

    public int getChannelPtsSync(long channelId) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Integer[] pts = new Integer[]{0};
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
                countDownLatch.countDown();
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

    public TLRPC.User getUserSync(long userId) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        TLRPC.User[] user = new TLRPC.User[1];
        storageQueue.postRunnable(() -> {
            user[0] = getUser(userId);
            countDownLatch.countDown();
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return user[0];
    }

    public TLRPC.Chat getChatSync(long chatId) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        TLRPC.Chat[] chat = new TLRPC.Chat[1];
        storageQueue.postRunnable(() -> {
            chat[0] = getChat(chatId);
            countDownLatch.countDown();
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return chat[0];
    }

    public TLRPC.User getUser(long userId) {
        TLRPC.User user = null;
        try {
            ArrayList<TLRPC.User> users = new ArrayList<>();
            getUsersInternal("" + userId, users);
            if (!users.isEmpty()) {
                user = users.get(0);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return user;
    }

    public ArrayList<TLRPC.User> getUsers(ArrayList<Long> uids) {
        ArrayList<TLRPC.User> users = new ArrayList<>();
        try {
            getUsersInternal(TextUtils.join(",", uids), users);
        } catch (Exception e) {
            users.clear();
            FileLog.e(e);
        }
        return users;
    }

    public TLRPC.Chat getChat(long chatId) {
        TLRPC.Chat chat = null;
        try {
            ArrayList<TLRPC.Chat> chats = new ArrayList<>();
            getChatsInternal("" + chatId, chats);
            if (!chats.isEmpty()) {
                chat = chats.get(0);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return chat;
    }

    public TLRPC.EncryptedChat getEncryptedChat(long chatId) {
        TLRPC.EncryptedChat chat = null;
        try {
            ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
            getEncryptedChatsInternal("" + chatId, encryptedChats, null);
            if (!encryptedChats.isEmpty()) {
                chat = encryptedChats.get(0);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return chat;
    }


    public void localSearch(int dialogsType, String query, ArrayList<Object> resultArray, ArrayList<CharSequence> resultArrayNames, ArrayList<TLRPC.User> encUsers, int folderId) {
        long selfUserId = UserConfig.getInstance(currentAccount).getClientUserId();
        try {
            String search1 = query.trim().toLowerCase();
            if (TextUtils.isEmpty(search1)) {
                return;
            }
            String savedMessages = LocaleController.getString("SavedMessages", R.string.SavedMessages).toLowerCase();
            String savedMessages2 = "saved messages";
            String search2 = LocaleController.getInstance().getTranslitString(search1);
            if (search1.equals(search2) || search2.length() == 0) {
                search2 = null;
            }
            String[] search = new String[1 + (search2 != null ? 1 : 0)];
            search[0] = search1;
            if (search2 != null) {
                search[1] = search2;
            }

            ArrayList<Long> usersToLoad = new ArrayList<>();
            ArrayList<Long> chatsToLoad = new ArrayList<>();
            ArrayList<Integer> encryptedToLoad = new ArrayList<>();
            int resultCount = 0;

            LongSparseArray<DialogsSearchAdapter.DialogSearchResult> dialogsResult = new LongSparseArray<>();
            SQLiteCursor cursor;
            if (folderId >= 0) {
                cursor = getDatabase().queryFinalized("SELECT did, date FROM dialogs WHERE folder_id = ? ORDER BY date DESC LIMIT 600", folderId);
            } else {
                cursor = getDatabase().queryFinalized("SELECT did, date FROM dialogs ORDER BY date DESC LIMIT 600");
            }
            while (cursor.next()) {
                long id = cursor.longValue(0);
                DialogsSearchAdapter.DialogSearchResult dialogSearchResult = new DialogsSearchAdapter.DialogSearchResult();
                dialogSearchResult.date = cursor.intValue(1);
                dialogsResult.put(id, dialogSearchResult);

                if (!DialogObject.isEncryptedDialog(id)) {
                    if (DialogObject.isUserDialog(id)) {
                        if (dialogsType == 4 && id == selfUserId) {
                            continue;
                        }
                        if (dialogsType != 2 && !usersToLoad.contains(id)) {
                            usersToLoad.add(id);
                        }
                    } else {
                        if (dialogsType == 4) {
                            continue;
                        }
                        if (!chatsToLoad.contains(-id)) {
                            chatsToLoad.add(-id);
                        }
                    }
                } else if (dialogsType == 0 || dialogsType == 3) {
                    int encryptedChatId = DialogObject.getEncryptedChatId(id);
                    if (!encryptedToLoad.contains(encryptedChatId)) {
                        encryptedToLoad.add(encryptedChatId);
                    }
                }
            }
            cursor.dispose();

            if (dialogsType != 4 && (savedMessages).startsWith(search1) || savedMessages2.startsWith(search1)) {
                TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
                DialogsSearchAdapter.DialogSearchResult dialogSearchResult = new DialogsSearchAdapter.DialogSearchResult();
                dialogSearchResult.date = Integer.MAX_VALUE;
                dialogSearchResult.name = savedMessages;
                dialogSearchResult.object = user;
                dialogsResult.put(user.id, dialogSearchResult);
                resultCount++;
            }

            if (!usersToLoad.isEmpty()) {
                cursor = getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, status, name FROM users WHERE uid IN(%s)", TextUtils.join(",", usersToLoad)));
                while (cursor.next()) {
                    String name = cursor.stringValue(2);
                    String tName = LocaleController.getInstance().getTranslitString(name);
                    if (name.equals(tName)) {
                        tName = null;
                    }
                    String username = null;
                    int usernamePos = name.lastIndexOf(";;;");
                    if (usernamePos != -1) {
                        username = name.substring(usernamePos + 3);
                    }
                    int found = 0;
                    for (String q : search) {
                        if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                            found = 1;
                        } else if (username != null && username.startsWith(q)) {
                            found = 2;
                        }
                        if (found != 0) {
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                TLRPC.User user = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                                data.reuse();
                                DialogsSearchAdapter.DialogSearchResult dialogSearchResult = dialogsResult.get(user.id);
                                if (user.status != null) {
                                    user.status.expires = cursor.intValue(1);
                                }
                                if (found == 1) {
                                    dialogSearchResult.name = AndroidUtilities.generateSearchName(user.first_name, user.last_name, q);
                                } else {
                                    dialogSearchResult.name = AndroidUtilities.generateSearchName("@" + user.username, null, "@" + q);
                                }
                                dialogSearchResult.object = user;
                                resultCount++;
                            }
                            break;
                        }
                    }
                }
                cursor.dispose();
            }

            if (!chatsToLoad.isEmpty()) {
                cursor = getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, name FROM chats WHERE uid IN(%s)", TextUtils.join(",", chatsToLoad)));
                while (cursor.next()) {
                    String name = cursor.stringValue(1);
                    String tName = LocaleController.getInstance().getTranslitString(name);
                    if (name.equals(tName)) {
                        tName = null;
                    }
                    for (String q : search) {
                        if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                TLRPC.Chat chat = TLRPC.Chat.TLdeserialize(data, data.readInt32(false), false);
                                data.reuse();
                                if (!(chat == null || chat.deactivated || ChatObject.isChannel(chat) && ChatObject.isNotInChat(chat))) {
                                    long dialog_id = -chat.id;
                                    DialogsSearchAdapter.DialogSearchResult dialogSearchResult = dialogsResult.get(dialog_id);
                                    dialogSearchResult.name = AndroidUtilities.generateSearchName(chat.title, null, q);
                                    dialogSearchResult.object = chat;
                                    resultCount++;
                                }
                            }
                            break;
                        }
                    }
                }
                cursor.dispose();
            }

            if (!encryptedToLoad.isEmpty()) {
                cursor = getDatabase().queryFinalized(String.format(Locale.US, "SELECT q.data, u.name, q.user, q.g, q.authkey, q.ttl, u.data, u.status, q.layer, q.seq_in, q.seq_out, q.use_count, q.exchange_id, q.key_date, q.fprint, q.fauthkey, q.khash, q.in_seq_no, q.admin_id, q.mtproto_seq FROM enc_chats as q INNER JOIN users as u ON q.user = u.uid WHERE q.uid IN(%s)", TextUtils.join(",", encryptedToLoad)));
                while (cursor.next()) {
                    String name = cursor.stringValue(1);
                    String tName = LocaleController.getInstance().getTranslitString(name);
                    if (name.equals(tName)) {
                        tName = null;
                    }

                    String username = null;
                    int usernamePos = name.lastIndexOf(";;;");
                    if (usernamePos != -1) {
                        username = name.substring(usernamePos + 2);
                    }
                    int found = 0;
                    for (int a = 0; a < search.length; a++) {
                        String q = search[a];
                        if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                            found = 1;
                        } else if (username != null && username.startsWith(q)) {
                            found = 2;
                        }

                        if (found != 0) {
                            TLRPC.EncryptedChat chat = null;
                            TLRPC.User user = null;
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                chat = TLRPC.EncryptedChat.TLdeserialize(data, data.readInt32(false), false);
                                data.reuse();
                            }
                            data = cursor.byteBufferValue(6);
                            if (data != null) {
                                user = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                                data.reuse();
                            }
                            if (chat != null && user != null) {
                                DialogsSearchAdapter.DialogSearchResult dialogSearchResult = dialogsResult.get(DialogObject.makeEncryptedDialogId(chat.id));
                                chat.user_id = cursor.longValue(2);
                                chat.a_or_b = cursor.byteArrayValue(3);
                                chat.auth_key = cursor.byteArrayValue(4);
                                chat.ttl = cursor.intValue(5);
                                chat.layer = cursor.intValue(8);
                                chat.seq_in = cursor.intValue(9);
                                chat.seq_out = cursor.intValue(10);
                                int use_count = cursor.intValue(11);
                                chat.key_use_count_in = (short) (use_count >> 16);
                                chat.key_use_count_out = (short) (use_count);
                                chat.exchange_id = cursor.longValue(12);
                                chat.key_create_date = cursor.intValue(13);
                                chat.future_key_fingerprint = cursor.longValue(14);
                                chat.future_auth_key = cursor.byteArrayValue(15);
                                chat.key_hash = cursor.byteArrayValue(16);
                                chat.in_seq_no = cursor.intValue(17);
                                long admin_id = cursor.longValue(18);
                                if (admin_id != 0) {
                                    chat.admin_id = admin_id;
                                }
                                chat.mtproto_seq = cursor.intValue(19);

                                if (user.status != null) {
                                    user.status.expires = cursor.intValue(7);
                                }
                                if (found == 1) {
                                    dialogSearchResult.name = new SpannableStringBuilder(ContactsController.formatName(user.first_name, user.last_name));
                                    ((SpannableStringBuilder) dialogSearchResult.name).setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_chats_secretName)), 0, dialogSearchResult.name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                } else {
                                    dialogSearchResult.name = AndroidUtilities.generateSearchName("@" + user.username, null, "@" + q);
                                }
                                dialogSearchResult.object = chat;
                                encUsers.add(user);
                                resultCount++;
                            }
                            break;
                        }
                    }
                }
                cursor.dispose();
            }

            ArrayList<DialogsSearchAdapter.DialogSearchResult> searchResults = new ArrayList<>(resultCount);
            for (int a = 0; a < dialogsResult.size(); a++) {
                DialogsSearchAdapter.DialogSearchResult dialogSearchResult = dialogsResult.valueAt(a);
                if (dialogSearchResult.object != null && dialogSearchResult.name != null) {
                    searchResults.add(dialogSearchResult);
                }
            }

            Collections.sort(searchResults, (lhs, rhs) -> {
                if (lhs.date < rhs.date) {
                    return 1;
                } else if (lhs.date > rhs.date) {
                    return -1;
                }
                return 0;
            });

            for (int a = 0; a < searchResults.size(); a++) {
                DialogsSearchAdapter.DialogSearchResult dialogSearchResult = searchResults.get(a);
                resultArray.add(dialogSearchResult.object);
                resultArrayNames.add(dialogSearchResult.name);
            }

            if (dialogsType != 2) {
                cursor = getDatabase().queryFinalized("SELECT u.data, u.status, u.name, u.uid FROM users as u INNER JOIN contacts as c ON u.uid = c.uid");
                while (cursor.next()) {
                    long uid = cursor.longValue(3);
                    if (dialogsResult.indexOfKey(uid) >= 0) {
                        continue;
                    }
                    String name = cursor.stringValue(2);
                    String tName = LocaleController.getInstance().getTranslitString(name);
                    if (name.equals(tName)) {
                        tName = null;
                    }
                    String username = null;
                    int usernamePos = name.lastIndexOf(";;;");
                    if (usernamePos != -1) {
                        username = name.substring(usernamePos + 3);
                    }
                    int found = 0;
                    for (String q : search) {
                        if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                            found = 1;
                        } else if (username != null && username.startsWith(q)) {
                            found = 2;
                        }
                        if (found != 0) {
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                TLRPC.User user = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                                data.reuse();
                                if (user.status != null) {
                                    user.status.expires = cursor.intValue(1);
                                }
                                if (found == 1) {
                                    resultArrayNames.add(AndroidUtilities.generateSearchName(user.first_name, user.last_name, q));
                                } else {
                                    resultArrayNames.add(AndroidUtilities.generateSearchName("@" + user.username, null, "@" + q));
                                }
                                resultArray.add(user);
                            }
                            break;
                        }
                    }
                }
                cursor.dispose();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public ArrayList<Integer> getCachedMessagesInRange(long dialogId, int minDate, int maxDate) {
        ArrayList<Integer> messageIds = new ArrayList<>();
        try {
            SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid FROM messages_v2 WHERE uid = %d AND date >= %d AND date <= %d", dialogId, minDate, maxDate));
            try {
                while (cursor.next()) {
                    int mid = cursor.intValue(0);
                    messageIds.add(mid);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            cursor.dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return messageIds;
    }

    public void updateUnreadReactionsCount(long dialogId, int count) {
        storageQueue.postRunnable(() -> {
            try {
                SQLitePreparedStatement state = database.executeFast("UPDATE dialogs SET unread_reactions = ? WHERE did = ?");
                state.bindInteger(1, Math.max(count, 0));
                state.bindLong(2, dialogId);
                state.step();
                state.dispose();

                if (count == 0) {
                    state = database.executeFast("UPDATE reaction_mentions SET state = 0 WHERE dialog_id ?");
                    state.bindLong(1, dialogId);
                    state.step();
                    state.dispose();
                }
            } catch (SQLiteException e) {
                e.printStackTrace();
            }
        });
    }

    public void markMessageReactionsAsRead(long dialogId, int messageId, boolean usequeue) {
        if (usequeue) {
            getStorageQueue().postRunnable(() -> {
                markMessageReactionsAsReadInternal(dialogId, messageId);
            });
        } else {
            markMessageReactionsAsReadInternal(dialogId, messageId);
        }
    }

    public void markMessageReactionsAsReadInternal(long dialogId, int messageId) {
        try {
            SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("UPDATE reaction_mentions SET state = 0 WHERE message_id = ? AND dialog_id = ?");
            state.bindInteger(1, messageId);
            state.bindLong(2, dialogId);
            state.step();
            state.dispose();

            SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM messages_v2 WHERE uid = %d AND mid = %d", dialogId, messageId));
            TLRPC.Message message = null;
            if (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null) {
                    message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    message.readAttachPath(data, getUserConfig().clientUserId);
                    data.reuse();
                    if (message.reactions != null && message.reactions.recent_reactions != null) {
                        for (int i = 0; i < message.reactions.recent_reactions.size(); i++) {
                            message.reactions.recent_reactions.get(i).unread = false;
                        }
                    }
                }
            }
            cursor.dispose();
            if (message != null) {
                state = getMessagesStorage().getDatabase().executeFast(String.format(Locale.US, "UPDATE messages_v2 SET data = ? WHERE uid = %d AND mid = %d", dialogId, messageId));
                try {
                    NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                    message.serializeToStream(data);
                    state.bindByteBuffer(1, data);
                    state.step();
                    state.dispose();
                    data.reuse();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } catch (SQLiteException e) {
            FileLog.e(e);
        }

    }

    public void updateDialogUnreadReactions(long dialogId, int newUnreadCount, boolean increment) {
        storageQueue.postRunnable(() -> {
            try {
                int oldUnreadRactions = 0;
                if (increment) {
                    SQLiteCursor cursor = database.queryFinalized("SELECT unread_reactions FROM dialogs WHERE did = " + dialogId);
                    if (cursor.next()) {
                        oldUnreadRactions = Math.max(0, cursor.intValue(0));
                    }
                    cursor.dispose();
                }
                oldUnreadRactions += newUnreadCount;
                SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("UPDATE dialogs SET unread_reactions = ? WHERE did = ?");
                state.bindInteger(1, oldUnreadRactions);
                state.bindLong(2, dialogId);
                state.step();
                state.dispose();
            } catch (SQLiteException e) {
                e.printStackTrace();
            }
        });
    }

    public interface IntCallback {
        void run(int param);
    }

    public interface LongCallback {
        void run(long param);
    }

    public interface StringCallback {
        void run(String param);
    }

    public interface BooleanCallback {
        void run(boolean param);
    }
}
