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
import android.util.Log;
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
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.EditWidgetActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class MessagesStorage extends BaseController {

    private DispatchQueue storageQueue;
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
    private static final Object[] lockObjects = new Object[UserConfig.MAX_ACCOUNT_COUNT];
    static {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            lockObjects[i] = new Object();
        }
    }

    public final static int LAST_DB_VERSION = 115;
    private boolean databaseMigrationInProgress;
    public boolean showClearDatabaseAlert;
    private LongSparseIntArray dialogIsForum = new LongSparseIntArray();

    public static MessagesStorage getInstance(int num) {
        MessagesStorage localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (lockObjects[num]) {
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
        storageQueue = new DispatchQueue("storageQueue_" + instance);
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
        if (!NativeLoader.loaded()) {
            int tryCount = 0;
            while (!NativeLoader.loaded()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                tryCount++;
                if (tryCount > 5) {
                    break;
                }
            }
        }
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
                createTables(database);
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
                    if (e.getMessage() != null && e.getMessage().contains("malformed")) {
                        throw new RuntimeException("malformed");
                    }
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
            if (openTries < 3 && e.getMessage() != null && e.getMessage().contains("malformed")) {
                if (openTries == 2) {
                    cleanupInternal(true);
                    clearLoadingDialogsOffsets();
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

    private void clearLoadingDialogsOffsets() {
        for (int a = 0; a < 2; a++) {
            getUserConfig().setDialogsLoadOffset(a, 0, 0, 0, 0, 0, 0);
            getUserConfig().setTotalDialogsCount(a, 0);
        }
        getUserConfig().saveConfig(false);
    }

    private boolean recoverDatabase() {
        database.close();
        boolean restored = DatabaseMigrationHelper.recoverDatabase(cacheFile, walCacheFile, shmCacheFile, currentAccount);
        if (restored) {
            try {
                database = new SQLiteDatabase(cacheFile.getPath());
                database.executeFast("PRAGMA secure_delete = ON").stepThis().dispose();
                database.executeFast("PRAGMA temp_store = MEMORY").stepThis().dispose();
                database.executeFast("PRAGMA journal_mode = WAL").stepThis().dispose();
                database.executeFast("PRAGMA journal_size_limit = 10485760").stepThis().dispose();
            } catch (SQLiteException e) {
                FileLog.e(new Exception(e));
                restored = false;
            }
        }
        if (!restored) {
            openDatabase(1);
        }
        reset();
        return restored;
    }

    public final static String[] DATABASE_TABLES = new String[] {
            "messages_holes",
            "media_holes_v2",
            "scheduled_messages_v2",
            "messages_v2",
            "download_queue",
            "user_contacts_v7",
            "user_phones_v7",
            "dialogs",
            "dialog_filter",
            "dialog_filter_ep",
            "dialog_filter_pin_v2",
            "randoms_v2",
            "enc_tasks_v4",
            "messages_seq",
            "params",
            "media_v4",
            "bot_keyboard",
            "bot_keyboard_topics",
            "chat_settings_v2",
            "user_settings",
            "chat_pinned_v2",
            "chat_pinned_count",
            "chat_hints",
            "botcache",
            "users_data",
            "users",
            "chats",
            "enc_chats",
            "channel_users_v2",
            "channel_admins_v3",
            "contacts",
            "user_photos",
            "dialog_settings",
            "web_recent_v3",
            "stickers_v2",
            "stickers_featured",
            "stickers_dice",
            "stickersets",
            "hashtag_recent_v2",
            "webpage_pending_v2",
            "sent_files_v2",
            "search_recent",
            "media_counts_v2",
            "keyvalue",
            "bot_info_v2",
            "pending_tasks",
            "requested_holes",
            "sharing_locations",
            "shortcut_widget",
            "emoji_keywords_v2",
            "emoji_keywords_info_v2",
            "wallpapers2",
            "unread_push_messages",
            "polls_v2",
            "reactions",
            "reaction_mentions",
            "downloading_documents",
            "animated_emoji",
            "attach_menu_bots",
            "premium_promo",
            "emoji_statuses",
            "messages_holes_topics",
            "messages_topics",
            "media_topics",
            "media_holes_topics",
            "topics",
            "media_counts_topics",
            "reaction_mentions_topics",
            "emoji_groups"
    };

    public static void createTables(SQLiteDatabase database) throws SQLiteException {
        database.executeFast("CREATE TABLE messages_holes(uid INTEGER, start INTEGER, end INTEGER, PRIMARY KEY(uid, start));").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_end_messages_holes ON messages_holes(uid, end);").stepThis().dispose();

        database.executeFast("CREATE TABLE media_holes_v2(uid INTEGER, type INTEGER, start INTEGER, end INTEGER, PRIMARY KEY(uid, type, start));").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_end_media_holes_v2 ON media_holes_v2(uid, type, end);").stepThis().dispose();

        database.executeFast("CREATE TABLE scheduled_messages_v2(mid INTEGER, uid INTEGER, send_state INTEGER, date INTEGER, data BLOB, ttl INTEGER, replydata BLOB, reply_to_message_id INTEGER, PRIMARY KEY(mid, uid))").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS send_state_idx_scheduled_messages_v2 ON scheduled_messages_v2(mid, send_state, date);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_date_idx_scheduled_messages_v2 ON scheduled_messages_v2(uid, date);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS reply_to_idx_scheduled_messages_v2 ON scheduled_messages_v2(mid, reply_to_message_id);").stepThis().dispose();

        database.executeFast("CREATE TABLE messages_v2(mid INTEGER, uid INTEGER, read_state INTEGER, send_state INTEGER, date INTEGER, data BLOB, out INTEGER, ttl INTEGER, media INTEGER, replydata BLOB, imp INTEGER, mention INTEGER, forwards INTEGER, replies_data BLOB, thread_reply_id INTEGER, is_channel INTEGER, reply_to_message_id INTEGER, custom_params BLOB, group_id INTEGER, PRIMARY KEY(mid, uid))").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_read_out_idx_messages_v2 ON messages_v2(uid, mid, read_state, out);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_date_mid_idx_messages_v2 ON messages_v2(uid, date, mid);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS mid_out_idx_messages_v2 ON messages_v2(mid, out);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS task_idx_messages_v2 ON messages_v2(uid, out, read_state, ttl, date, send_state);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS send_state_idx_messages_v2 ON messages_v2(mid, send_state, date);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_mention_idx_messages_v2 ON messages_v2(uid, mention, read_state);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS is_channel_idx_messages_v2 ON messages_v2(mid, is_channel);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS reply_to_idx_messages_v2 ON messages_v2(mid, reply_to_message_id);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_groupid_messages_v2 ON messages_v2(uid, mid, group_id);").stepThis().dispose();

        database.executeFast("CREATE TABLE download_queue(uid INTEGER, type INTEGER, date INTEGER, data BLOB, parent TEXT, PRIMARY KEY (uid, type));").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS type_date_idx_download_queue ON download_queue(type, date);").stepThis().dispose();

        database.executeFast("CREATE TABLE user_contacts_v7(key TEXT PRIMARY KEY, uid INTEGER, fname TEXT, sname TEXT, imported INTEGER)").stepThis().dispose();
        database.executeFast("CREATE TABLE user_phones_v7(key TEXT, phone TEXT, sphone TEXT, deleted INTEGER, PRIMARY KEY (key, phone))").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS sphone_deleted_idx_user_phones ON user_phones_v7(sphone, deleted);").stepThis().dispose();

        database.executeFast("CREATE TABLE dialogs(did INTEGER PRIMARY KEY, date INTEGER, unread_count INTEGER, last_mid INTEGER, inbox_max INTEGER, outbox_max INTEGER, last_mid_i INTEGER, unread_count_i INTEGER, pts INTEGER, date_i INTEGER, pinned INTEGER, flags INTEGER, folder_id INTEGER, data BLOB, unread_reactions INTEGER, last_mid_group INTEGER, ttl_period INTEGER)").stepThis().dispose();
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

        database.executeFast("CREATE TABLE bot_keyboard_topics(uid INTEGER, tid INTEGER, mid INTEGER, info BLOB, PRIMARY KEY(uid, tid))").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS bot_keyboard_topics_idx_mid_v2 ON bot_keyboard_topics(mid, uid, tid);").stepThis().dispose();

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
        database.executeFast("CREATE TABLE stickers_featured(id INTEGER PRIMARY KEY, data BLOB, unread BLOB, date INTEGER, hash INTEGER, premium INTEGER, emoji INTEGER);").stepThis().dispose();
        database.executeFast("CREATE TABLE stickers_dice(emoji TEXT PRIMARY KEY, data BLOB, date INTEGER);").stepThis().dispose();
        database.executeFast("CREATE TABLE stickersets(id INTEGER PRIMATE KEY, data BLOB, hash INTEGER);").stepThis().dispose();
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
        database.executeFast("CREATE TABLE animated_emoji(document_id INTEGER PRIMARY KEY, data BLOB);").stepThis().dispose();

        database.executeFast("CREATE TABLE attach_menu_bots(data BLOB, hash INTEGER, date INTEGER);").stepThis().dispose();

        database.executeFast("CREATE TABLE premium_promo(data BLOB, date INTEGER);").stepThis().dispose();
        database.executeFast("CREATE TABLE emoji_statuses(data BLOB, type INTEGER);").stepThis().dispose();

        database.executeFast("CREATE TABLE messages_holes_topics(uid INTEGER, topic_id INTEGER, start INTEGER, end INTEGER, PRIMARY KEY(uid, topic_id, start));").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_end_messages_holes ON messages_holes_topics(uid, topic_id, end);").stepThis().dispose();

        database.executeFast("CREATE TABLE messages_topics(mid INTEGER, uid INTEGER, topic_id INTEGER, read_state INTEGER, send_state INTEGER, date INTEGER, data BLOB, out INTEGER, ttl INTEGER, media INTEGER, replydata BLOB, imp INTEGER, mention INTEGER, forwards INTEGER, replies_data BLOB, thread_reply_id INTEGER, is_channel INTEGER, reply_to_message_id INTEGER, custom_params BLOB, PRIMARY KEY(mid, topic_id, uid))").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_date_mid_idx_messages_topics ON messages_topics(uid, date, mid);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS mid_out_idx_messages_topics ON messages_topics(mid, out);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS task_idx_messages_topics ON messages_topics(uid, out, read_state, ttl, date, send_state);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS send_state_idx_messages_topics ON messages_topics(mid, send_state, date);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS is_channel_idx_messages_topics ON messages_topics(mid, is_channel);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS reply_to_idx_messages_topics ON messages_topics(mid, reply_to_message_id);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS mid_uid_messages_topics ON messages_topics(mid, uid);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_read_out_idx_messages_topics ON messages_topics(uid, topic_id, mid, read_state, out);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_mention_idx_messages_topics ON messages_topics(uid, topic_id, mention, read_state);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_topic_id_messages_topics ON messages_topics(uid, topic_id);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_topic_id_date_mid_messages_topics ON messages_topics(uid, topic_id, date, mid);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_topic_id_mid_messages_topics ON messages_topics(uid, topic_id, mid);").stepThis().dispose();


        database.executeFast("CREATE TABLE media_topics(mid INTEGER, uid INTEGER, topic_id INTEGER, date INTEGER, type INTEGER, data BLOB, PRIMARY KEY(mid, uid, topic_id, type))").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_type_date_idx_media_topics ON media_topics(uid, topic_id, mid, type, date);").stepThis().dispose();

        database.executeFast("CREATE TABLE media_holes_topics(uid INTEGER, topic_id INTEGER, type INTEGER, start INTEGER, end INTEGER, PRIMARY KEY(uid, topic_id, type, start));").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_end_media_holes_topics ON media_holes_topics(uid, topic_id, type, end);").stepThis().dispose();

        database.executeFast("CREATE TABLE topics(did INTEGER, topic_id INTEGER, data BLOB, top_message INTEGER, topic_message BLOB, unread_count INTEGER, max_read_id INTEGER, unread_mentions INTEGER, unread_reactions INTEGER, read_outbox INTEGER, pinned INTEGER, total_messages_count INTEGER, hidden INTEGER, PRIMARY KEY(did, topic_id));").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS did_top_message_topics ON topics(did, top_message);").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS did_topics ON topics(did);").stepThis().dispose();

        database.executeFast("CREATE TABLE media_counts_topics(uid INTEGER, topic_id INTEGER, type INTEGER, count INTEGER, old INTEGER, PRIMARY KEY(uid, topic_id, type))").stepThis().dispose();

        database.executeFast("CREATE TABLE reaction_mentions_topics(message_id INTEGER, state INTEGER, dialog_id INTEGER, topic_id INTEGER, PRIMARY KEY(message_id, dialog_id, topic_id))").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS reaction_mentions_topics_did ON reaction_mentions_topics(dialog_id, topic_id);").stepThis().dispose();

        database.executeFast("CREATE TABLE emoji_groups(type INTEGER PRIMARY KEY, data BLOB)").stepThis().dispose();
        database.executeFast("CREATE TABLE app_config(data BLOB)").stepThis().dispose();

        database.executeFast("PRAGMA user_version = " + MessagesStorage.LAST_DB_VERSION).stepThis().dispose();

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
        version = DatabaseMigrationHelper.migrate(MessagesStorage.this, version);

        FileLog.d("MessagesStorage db migration finished to varsion " + version);
        AndroidUtilities.runOnUIThread(() -> {
            databaseMigrationInProgress = false;
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.onDatabaseMigration, false);
        });
    }

    void executeNoException(String query) {
        try {
            database.executeFast(query).stepThis().dispose();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void cleanupInternal(boolean deleteFiles) {
        if (deleteFiles) {
            reset();
        } else {
            clearDatabaseValues();
        }
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

    public void clearDatabaseValues() {
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
                checkSQLException(e);
            }
        });
    }

    boolean tryRecover;

    public void checkSQLException(Throwable e) {
        checkSQLException(e, true);
    }

    private void checkSQLException(Throwable e, boolean logToAppCenter) {
        if (e instanceof SQLiteException && e.getMessage() != null && e.getMessage().contains("is malformed") && !tryRecover) {
            tryRecover = true;
            if (recoverDatabase()) {
                tryRecover = false;
                clearLoadingDialogsOffsets();
                AndroidUtilities.runOnUIThread(() -> {
                   getNotificationCenter().postNotificationName(NotificationCenter.onDatabaseReset);
                });
                FileLog.e(new Exception("database restored!!"));
            } else {
                FileLog.e(new Exception(e), logToAppCenter);
            }
        } else {
            FileLog.e(e, logToAppCenter);
        }
    }

    public void fixNotificationSettings() {
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
                    checkSQLException(e);
                }
            } catch (Throwable e) {
                checkSQLException(e);
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
                checkSQLException(e);
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
                checkSQLException(e);
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
                checkSQLException(e);
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
                checkSQLException(e);
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
            checkSQLException(e);
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
                checkSQLException(e);
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
                checkSQLException(e);
            }
        });
    }

    public void clearLocalDatabase() {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            SQLitePreparedStatement state5 = null;
            SQLitePreparedStatement state6 = null;
            try {
                ArrayList<Long> dialogsToCleanup = new ArrayList<>();

                database.executeFast("DELETE FROM reaction_mentions").stepThis().dispose();
                database.executeFast("DELETE FROM reaction_mentions_topics").stepThis().dispose();
                database.executeFast("DELETE FROM downloading_documents").stepThis().dispose();
                database.executeFast("DELETE FROM attach_menu_bots").stepThis().dispose();
                database.executeFast("DELETE FROM animated_emoji").stepThis().dispose();
                database.executeFast("DELETE FROM stickers_v2").stepThis().dispose();
                database.executeFast("DELETE FROM stickersets").stepThis().dispose();
                database.executeFast("DELETE FROM messages_holes_topics").stepThis().dispose();
                database.executeFast("DELETE FROM messages_topics").stepThis().dispose();
                database.executeFast("DELETE FROM topics").stepThis().dispose();
                database.executeFast("DELETE FROM media_holes_topics").stepThis().dispose();
                database.executeFast("DELETE FROM media_topics").stepThis().dispose();
                database.executeFast("DELETE FROM media_counts_topics").stepThis().dispose();
                database.executeFast("DELETE FROM chat_pinned_v2").stepThis().dispose();
                database.executeFast("DELETE FROM chat_pinned_count").stepThis().dispose();

                cursor = database.queryFinalized("SELECT did FROM dialogs WHERE 1");
                while (cursor.next()) {
                    long did = cursor.longValue(0);
                    if (!DialogObject.isEncryptedDialog(did)) {
                        dialogsToCleanup.add(did);
                    }
                }
                cursor.dispose();
                cursor = null;

                state5 = database.executeFast("REPLACE INTO messages_holes VALUES(?, ?, ?)");
                state6 = database.executeFast("REPLACE INTO media_holes_v2 VALUES(?, ?, ?, ?)");

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
                            checkSQLException(e);
                        }
                        cursor2.dispose();

                        database.executeFast("DELETE FROM messages_v2 WHERE uid = " + did + " AND mid != " + last_mid_i + " AND mid != " + last_mid).stepThis().dispose();
                        database.executeFast("DELETE FROM messages_holes WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM bot_keyboard WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM bot_keyboard_topics WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM media_counts_v2 WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM media_v4 WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM media_holes_v2 WHERE uid = " + did).stepThis().dispose();
                        MediaDataController.getInstance(currentAccount).clearBotKeyboard(did);
                        if (messageId != -1) {
                            MessagesStorage.createFirstHoles(did, state5, state6, messageId, 0);
                        }
                    }
                    cursor.dispose();
                    cursor = null;
                }

                state5.dispose();
                state6.dispose();
                state5 = null;
                state6 = null;
                database.commitTransaction();
                database.executeFast("PRAGMA journal_size_limit = 0").stepThis().dispose();
                database.executeFast("VACUUM").stepThis().dispose();
                database.executeFast("PRAGMA journal_size_limit = -1").stepThis().dispose();

                getMessagesController().getTopicsController().databaseCleared();
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (database != null) {
                    database.commitTransaction();
                }
                if (state5 != null) {
                    state5.dispose();
                }
                if (state6 != null) {
                    state6.dispose();
                }
                if (cursor != null) {
                    cursor.dispose();
                }
                reset();
            }
        });
    }

    public void saveTopics(long dialogId, List<TLRPC.TL_forumTopic> topics, boolean replace, boolean useQueue) {
        if (useQueue) {
            storageQueue.postRunnable(() -> {
                saveTopicsInternal(dialogId, topics, replace, true);
            });
        } else {
            saveTopicsInternal(dialogId, topics, replace, false);
        }
    }

    private void saveTopicsInternal(long dialogId, List<TLRPC.TL_forumTopic> topics, boolean replace, boolean inTransaction) {
        SQLitePreparedStatement state = null;
        try {
            HashSet<Integer> existingTopics = new HashSet<>();
            HashMap<Integer, Integer> pinnedValues = new HashMap<>();
            for (int i = 0; i < topics.size(); i++) {
                TLRPC.TL_forumTopic topic = topics.get(i);
                SQLiteCursor cursor = database.queryFinalized("SELECT did, pinned FROM topics WHERE did = " + dialogId + " AND topic_id = " + topic.id);
                boolean exist = cursor.next();
                if (exist) {
                    pinnedValues.put(i, cursor.intValue(2));
                }
                cursor.dispose();
                cursor = null;
                if (exist) {
                    existingTopics.add(i);
                }
            }
            if (replace) {
                database.executeFast("DELETE FROM topics WHERE did = " + dialogId).stepThis().dispose();
            }
            state = database.executeFast("REPLACE INTO topics VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            if (inTransaction) {
                database.beginTransaction();
            }

            for (int i = 0; i < topics.size(); i++) {
                TLRPC.TL_forumTopic topic = topics.get(i);
                boolean exist = existingTopics.contains(i);

                state.requery();
                state.bindLong(1, dialogId);
                state.bindInteger(2, topic.id);
                NativeByteBuffer data = new NativeByteBuffer(topic.getObjectSize());
                topic.serializeToStream(data);

                state.bindByteBuffer(3, data);
                state.bindInteger(4, topic.top_message);

                NativeByteBuffer messageData = new NativeByteBuffer(topic.topicStartMessage.getObjectSize());
                topic.topicStartMessage.serializeToStream(messageData);
                state.bindByteBuffer(5, messageData);
                state.bindInteger(6, topic.unread_count);
                state.bindInteger(7, topic.read_inbox_max_id);
                state.bindInteger(8, topic.unread_mentions_count);
                state.bindInteger(9, topic.unread_reactions_count);
                state.bindInteger(10, topic.read_outbox_max_id);
                if (topic.isShort && pinnedValues.containsKey(i)) {
                    state.bindInteger(11, pinnedValues.get(i));
                } else {
                    state.bindInteger(11, topic.pinned ? 1 + topic.pinnedOrder : 0);
                }
                state.bindInteger(12, topic.totalMessagesCount);
                state.bindInteger(13, topic.hidden ? 1 : 0);

                state.step();
                messageData.reuse();
                data.reuse();

                if (exist) {
                    closeHolesInTable("messages_holes_topics", dialogId, topic.top_message, topic.top_message, topic.id);
                    closeHolesInMedia(dialogId, topic.top_message, topic.top_message, -1, 0);
                } else {
                    database.executeFast(String.format(Locale.ENGLISH, "DELETE FROM messages_holes_topics WHERE uid = %d AND topic_id = %d", dialogId, topic.id)).stepThis().dispose();
                    database.executeFast(String.format(Locale.ENGLISH, "DELETE FROM media_holes_topics WHERE uid = %d AND topic_id = %d", dialogId, topic.id)).stepThis().dispose();
                    database.executeFast(String.format(Locale.ENGLISH, "DELETE FROM messages_topics WHERE uid = %d AND topic_id = %d", dialogId, topic.id)).stepThis().dispose();
                    database.executeFast(String.format(Locale.ENGLISH, "DELETE FROM media_topics WHERE uid = %d AND topic_id = %d", dialogId, topic.id)).stepThis().dispose();

                    SQLitePreparedStatement state_holes = database.executeFast("REPLACE INTO messages_holes_topics VALUES(?, ?, ?, ?)");
                    SQLitePreparedStatement state_media_holes = database.executeFast("REPLACE INTO media_holes_topics VALUES(?, ?, ?, ?, ?)");
                    createFirstHoles(dialogId, state_holes, state_media_holes, topic.top_message, topic.id);
                    state_holes.dispose();
                    state_holes.dispose();
                }
            }
            resetAllUnreadCounters(false);

        } catch (Exception e) {
            checkSQLException(e);

        } finally {
            if (state != null) {
                state.dispose();
            }
            database.commitTransaction();
        }
    }

    public void updateTopicData(long dialogId, TLRPC.TL_forumTopic fromTopic, int flags) {
        if (fromTopic == null) {
            return;
        }
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            SQLiteCursor cursor = null;
            try {
                if ((flags & TopicsController.TOPIC_FLAG_TOTAL_MESSAGES_COUNT) != 0) {
                    state = database.executeFast("UPDATE topics SET total_messages_count = ? WHERE did = ? AND topic_id = ?");
                    state.requery();
                    state.bindInteger(1, fromTopic.totalMessagesCount);
                    state.bindLong(2, dialogId);
                    state.bindInteger(3, fromTopic.id);
                    state.step();
                    state.dispose();
                    if (flags == TopicsController.TOPIC_FLAG_TOTAL_MESSAGES_COUNT) {
                        return;
                    }
                }
                TLRPC.TL_forumTopic topicToUpdate = null;
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM topics WHERE did = %d AND topic_id = %d", dialogId, fromTopic.id));
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        topicToUpdate = TLRPC.TL_forumTopic.TLdeserialize(data, data.readInt32(true), true);
                        data.reuse();
                    }
                }
                cursor.dispose();
                cursor = null;

                if (topicToUpdate != null) {
                    if ((flags & TopicsController.TOPIC_FLAG_TITLE) != 0) {
                        topicToUpdate.title = fromTopic.title;
                    }
                    if ((flags & TopicsController.TOPIC_FLAG_ICON) != 0) {
                        topicToUpdate.icon_emoji_id = fromTopic.icon_emoji_id;
                        topicToUpdate.flags |= 1;
                    }
                    if ((flags & TopicsController.TOPIC_FLAG_PIN) != 0) {
                        topicToUpdate.pinned = fromTopic.pinned;
                        topicToUpdate.pinnedOrder = fromTopic.pinnedOrder;
                    }
                    int pinnedOrder = topicToUpdate.pinned ? 1 + topicToUpdate.pinnedOrder : 0;
                    if ((flags & TopicsController.TOPIC_FLAG_CLOSE) != 0) {
                        topicToUpdate.closed = fromTopic.closed;
                    }
                    if ((flags & TopicsController.TOPIC_FLAG_HIDE) != 0) {
                        topicToUpdate.hidden = fromTopic.hidden;
                    }
                    state = database.executeFast("UPDATE topics SET data = ?, pinned = ?, hidden = ? WHERE did = ? AND topic_id = ?");
                    database.beginTransaction();
                    NativeByteBuffer data = new NativeByteBuffer(topicToUpdate.getObjectSize());
                    topicToUpdate.serializeToStream(data);
                    state.bindByteBuffer(1, data);
                    state.bindInteger(2, pinnedOrder);
                    state.bindInteger(3, topicToUpdate.hidden ? 1 : 0);
                    state.bindLong(4, dialogId);
                    state.bindInteger(5, topicToUpdate.id);
                    state.step();
                    data.reuse();
                }
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
                if (cursor != null) {
                    cursor.dispose();
                }
                database.commitTransaction();
            }
        });
    }

    public void loadTopics(long dialogId, Consumer<ArrayList<TLRPC.TL_forumTopic>> callback) {
        storageQueue.postRunnable(() -> {
            ArrayList<TLRPC.TL_forumTopic> topics = null;
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT top_message, data, topic_message, unread_count, max_read_id, unread_mentions, unread_reactions, read_outbox, pinned, total_messages_count FROM topics WHERE did = %d ORDER BY pinned ASC", dialogId));

                SparseArray<ArrayList<TLRPC.TL_forumTopic>> topicsByTopMessageId = null;
                HashSet<Integer> topMessageIds = null;
                while (cursor.next()) {
                    if (topics == null) {
                        topics = new ArrayList<>();
                        topicsByTopMessageId = new SparseArray<>();
                        topMessageIds = new HashSet<>();
                    }
                    int topMessageId = cursor.intValue(0);
                    NativeByteBuffer data = cursor.byteBufferValue(1);
                    if (data != null) {
                        TLRPC.TL_forumTopic topic = TLRPC.TL_forumTopic.TLdeserialize(data, data.readInt32(false), false);
                        if (topic != null) {
                            topic.top_message = topMessageId;
                            ArrayList<TLRPC.TL_forumTopic> topicsListByTopMessageId = topicsByTopMessageId.get(topMessageId);
                            if (topicsListByTopMessageId == null) {
                                topicsListByTopMessageId = new ArrayList<>();
                                topicsByTopMessageId.put(topMessageId, topicsListByTopMessageId);
                            }
                            topicsListByTopMessageId.add(topic);
                            topMessageIds.add(topMessageId);
                            topics.add(topic);

                            NativeByteBuffer data2 = cursor.byteBufferValue(2);
                            //if (data2 != null) {
                                topic.topicStartMessage = TLRPC.Message.TLdeserialize(data2, data2.readInt32(false), false);
                                if (data2 != null) {
                                    data2.reuse();
                                }
                           // }
                            topic.unread_count = cursor.intValue(3);
                            topic.read_inbox_max_id = cursor.intValue(4);
                            topic.unread_mentions_count = cursor.intValue(5);
                            topic.unread_reactions_count = cursor.intValue(6);
                            topic.read_outbox_max_id = cursor.intValue(7);
                            topic.pinnedOrder = cursor.intValue(8) - 1;
                            topic.pinned = topic.pinnedOrder >= 0;
                            topic.totalMessagesCount = cursor.intValue(9);
                        }

                        data.reuse();
                    }
                }
                ArrayList<Long> usersToLoad = new ArrayList<>();
                ArrayList<Long> chatsToLoad = new ArrayList<>();
                LongSparseArray<SparseArray<ArrayList<TLRPC.Message>>> replyMessageOwners = new LongSparseArray<>();
                LongSparseArray<ArrayList<Integer>> dialogReplyMessagesIds = new LongSparseArray<>();


                if (topics != null && !topics.isEmpty()) {
                    SQLiteCursor cursor2 = database.queryFinalized("SELECT mid, data, replydata FROM messages_v2 WHERE uid = " + dialogId + " AND mid IN (" + TextUtils.join(",", topMessageIds) + ")");
                    while (cursor2.next()) {
                        int messageId = cursor2.intValue(0);
                        NativeByteBuffer data = cursor2.byteBufferValue(1);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            if (message != null) {
                                message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
                            }
                            data.reuse();

                            topMessageIds.remove(messageId);
                            ArrayList<TLRPC.TL_forumTopic> topicsList = topicsByTopMessageId.get(messageId);
                            if (topicsList != null) {
                                for (int i = 0; i < topicsList.size(); i++) {
                                    topicsList.get(i).topMessage = message;
                                }
                            }

                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);

                            try {
                                if (message != null && message.reply_to != null && message.reply_to.reply_to_msg_id != 0 && (
                                    message.action instanceof TLRPC.TL_messageActionPinMessage ||
                                    message.action instanceof TLRPC.TL_messageActionPaymentSent ||
                                    message.action instanceof TLRPC.TL_messageActionGameScore
                                )) {
                                    if (!cursor2.isNull(2)) {
                                        NativeByteBuffer data2 = cursor2.byteBufferValue(2);
                                        if (data2 != null) {
                                            message.replyMessage = TLRPC.Message.TLdeserialize(data2, data2.readInt32(false), false);
                                            message.replyMessage.readAttachPath(data2, getUserConfig().clientUserId);
                                            data2.reuse();
                                            if (message.replyMessage != null) {
                                                addUsersAndChatsFromMessage(message.replyMessage, usersToLoad, chatsToLoad, null);
                                            }
                                        }
                                    }
                                    if (message.replyMessage == null) {
                                        addReplyMessages(message, replyMessageOwners, dialogReplyMessagesIds);
                                    }
                                }
                            } catch (Exception e) {
                                checkSQLException(e);
                            }
                        }
                    }

                    cursor2.dispose();
                    if (!topMessageIds.isEmpty()) {
                        cursor2 = database.queryFinalized("SELECT mid, data FROM messages_topics WHERE uid = " + dialogId + " AND mid IN (" + TextUtils.join(",", topMessageIds) + ")");
                        try {
                            while (cursor2.next()) {
                                int messageId = cursor2.intValue(0);
                                NativeByteBuffer data = cursor2.byteBufferValue(1);
                                if (data != null) {
                                    TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                    if (message != null) {
                                        message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
                                    }
                                    data.reuse();

                                    topMessageIds.remove(messageId);
                                    addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);

                                    ArrayList<TLRPC.TL_forumTopic> topicsList = topicsByTopMessageId.get(messageId);
                                    if (topicsList != null) {
                                        for (int i = 0; i < topicsList.size(); i++) {
                                            topicsList.get(i).topMessage = message;
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            checkSQLException(e);
                        }
                    }

                    loadReplyMessages(replyMessageOwners, dialogReplyMessagesIds, usersToLoad, chatsToLoad, false);

                    ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                    ArrayList<TLRPC.User> users = new ArrayList<>();
                    if (!chatsToLoad.isEmpty()) {
                        getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                    }
                    if (!usersToLoad.isEmpty()) {
                        getUsersInternal(TextUtils.join(",", usersToLoad), users);
                    }

                    AndroidUtilities.runOnUIThread(() -> {
                        if (!users.isEmpty()) {
                            getMessagesController().putUsers(users, true);
                        }
                        if (!chats.isEmpty()) {
                            getMessagesController().putChats(chats, true);
                        }
                    });

                    loadGroupedMessagesForTopics(dialogId, topics);
                }

            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            callback.accept(topics);
        });
    }

    public void loadGroupedMessagesForTopicUpdates(ArrayList<TopicsController.TopicUpdate> topics) {
        if (topics == null) {
            return;
        }
        try {
            LongSparseArray<ArrayList<TopicsController.TopicUpdate>> topicsByGroupedId = new LongSparseArray<>();

            for (int i = 0; i < topics.size(); i++) {
                if (topics.get(i).reloadTopic || topics.get(i).onlyCounters || topics.get(i).topMessage == null) {
                    continue;
                }
                long groupId = topics.get(i).topMessage.grouped_id;
                if (groupId != 0) {
                    ArrayList<TopicsController.TopicUpdate> array = topicsByGroupedId.get(groupId);
                    if (array == null) {
                        array = new ArrayList<>();
                        topicsByGroupedId.put(groupId, array);
                    }
                    array.add(topics.get(i));
                }
            }
            for (int i = 0; i < topicsByGroupedId.size(); i++) {
                long groupId = topicsByGroupedId.keyAt(i);
                ArrayList<TopicsController.TopicUpdate> topicsToUpdate = topicsByGroupedId.valueAt(i);
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM messages_v2 WHERE uid = %s AND group_id = %s ORDER BY date DESC", topicsToUpdate.get(0).dialogId, groupId));

                ArrayList<MessageObject> messageObjects = null;
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    if (message != null) {
                        message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
                    }
                    if (messageObjects == null) {
                        messageObjects = new ArrayList<>();
                    }
                    messageObjects.add(new MessageObject(currentAccount, message, false, false));
                }
                cursor.dispose();
                for (int k = 0; k < topicsToUpdate.size(); k++) {
                    topicsToUpdate.get(k).groupedMessages = messageObjects;
                }

            }
        } catch (Throwable e) {
            checkSQLException(e);
        }
    }

    public void loadGroupedMessagesForTopics(long dialogId, ArrayList<TLRPC.TL_forumTopic> topics) {
        if (topics == null) {
            return;
        }
        try {

            LongSparseArray<ArrayList<TLRPC.TL_forumTopic>> topicsByGroupedId = new LongSparseArray<>();

            for (int i = 0; i < topics.size(); i++) {
                if (topics.get(i).topMessage == null) {
                    continue;
                }
                long groupId = topics.get(i).topMessage.grouped_id;
                if (groupId != 0) {
                    ArrayList<TLRPC.TL_forumTopic> array = topicsByGroupedId.get(groupId);
                    if (array == null) {
                        array = new ArrayList<>();
                        topicsByGroupedId.put(groupId, array);
                    }
                    array.add(topics.get(i));
                }
            }
            for (int i = 0; i < topicsByGroupedId.size(); i++) {
                long groupId = topicsByGroupedId.keyAt(i);
                ArrayList<TLRPC.TL_forumTopic> topicsToUpdate = topicsByGroupedId.valueAt(i);
                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM messages_v2 WHERE uid = %s AND group_id = %s ORDER BY date DESC", dialogId, groupId));

                ArrayList<MessageObject> messageObjects = null;
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    if (message != null) {
                        message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
                    }
                    if (messageObjects == null) {
                        messageObjects = new ArrayList<>();
                    }
                    messageObjects.add(new MessageObject(currentAccount, message, false, false));
                }
                cursor.dispose();
                for (int k = 0; k < topicsToUpdate.size(); k++) {
                    topicsToUpdate.get(k).groupedMessages = messageObjects;
                }

            }
        } catch (Throwable e) {
            checkSQLException(e);
        }

    }

    public void removeTopic(long dialogId, int topicId) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast(String.format(Locale.US, "DELETE FROM topics WHERE did = %d AND topic_id = %d", dialogId, topicId)).stepThis().dispose();
                database.executeFast(String.format(Locale.US, "DELETE FROM messages_topics WHERE uid = %d AND topic_id = %d", dialogId, topicId)).stepThis().dispose();
            } catch (SQLiteException e) {
                e.printStackTrace();
            }
        });
    }

    public void updateTopicsWithReadMessages(HashMap<TopicKey, Integer> topicsReadOutbox) {
        storageQueue.postRunnable(() -> {
            for (TopicKey topicKey : topicsReadOutbox.keySet()) {
                int value = topicsReadOutbox.get(topicKey);
                try {
                    database.executeFast(String.format(Locale.US, "UPDATE topics SET read_outbox = max((SELECT read_outbox FROM topics WHERE did = %d AND topic_id = %d), %d) WHERE did = %d AND topic_id = %d", topicKey.dialogId, topicKey.topicId, value, topicKey.dialogId, topicKey.topicId)).stepThis().dispose();
                } catch (SQLiteException e) {
                   checkSQLException(e);
                }
            }
        });
    }

    public void setDialogTtl(long did, int ttl) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast(String.format(Locale.US, "UPDATE dialogs SET ttl_period = %d WHERE did = %d", ttl, did)).stepThis().dispose();
            } catch (SQLiteException e) {
                checkSQLException(e);
            }
        });
    }

    public ArrayList<File> getDatabaseFiles() {
        ArrayList<File> files = new ArrayList<>();
        files.add(cacheFile);
        files.add(walCacheFile);
        files.add(shmCacheFile);
        return files;
    }

    public void reset() {
        clearDatabaseValues();

        AndroidUtilities.runOnUIThread(() -> {
            for (int a = 0; a < 2; a++) {
                getUserConfig().setDialogsLoadOffset(a, 0, 0, 0, 0, 0, 0);
                getUserConfig().setTotalDialogsCount(a, 0);
            }
            getUserConfig().clearFilters();
            getUserConfig().clearPinnedDialogsLoaded();

            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didClearDatabase);
            getMediaDataController().loadAttachMenuBots(false, true);
            getNotificationCenter().postNotificationName(NotificationCenter.onDatabaseReset);
        });
    }

    private static class ReadDialog {
        public int lastMid;
        public int date;
        public int unreadCount;
    }

    public void readAllDialogs(int folderId) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                ArrayList<Long> usersToLoad = new ArrayList<>();
                ArrayList<Long> chatsToLoad = new ArrayList<>();
                ArrayList<Integer> encryptedChatIds = new ArrayList<>();

                LongSparseArray<ReadDialog> dialogs = new LongSparseArray<>();
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
                cursor = null;

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
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    private TLRPC.messages_Dialogs loadDialogsByIds(String ids, ArrayList<Long> usersToLoad, ArrayList<Long> chatsToLoad, ArrayList<Integer> encryptedToLoad) throws Exception {
        TLRPC.messages_Dialogs dialogs = new TLRPC.TL_messages_dialogs();
        LongSparseArray<TLRPC.Message> replyMessageOwners = new LongSparseArray<>();
        LongSparseArray<Long> groupsToLoad = new LongSparseArray<>();
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized(String.format(Locale.US, "SELECT d.did, d.last_mid, d.unread_count, d.date, m.data, m.read_state, m.mid, m.send_state, s.flags, m.date, d.pts, d.inbox_max, d.outbox_max, m.replydata, d.pinned, d.unread_count_i, d.flags, d.folder_id, d.data, d.unread_reactions, d.last_mid_group, d.ttl_period FROM dialogs as d LEFT JOIN messages_v2 as m ON d.last_mid = m.mid AND d.did = m.uid LEFT JOIN dialog_settings as s ON d.did = s.did WHERE d.did IN (%s) ORDER BY d.pinned DESC, d.date DESC", ids));
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
                long groupMessagesId = cursor.longValue(20);
                if (groupMessagesId != 0) {
                    groupsToLoad.append(dialogId, groupMessagesId);
                }
                dialog.ttl_period = cursor.intValue(21);
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

                        addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);

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
                                            addUsersAndChatsFromMessage(message.replyMessage, usersToLoad, chatsToLoad, null);
                                        }
                                    }
                                }
                                if (message.replyMessage == null) {
                                    replyMessageOwners.put(dialog.id, message);
                                }
                            }
                        } catch (Exception e) {
                            checkSQLException(e);
                        }
                    } else {
                        data.reuse();
                    }
                }
                if (!DialogObject.isEncryptedDialog(dialogId)) {
                    if (dialog.read_inbox_max_id > dialog.top_message) {
                        dialog.read_inbox_max_id = 0;
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
            cursor = null;

            if (!groupsToLoad.isEmpty()) {
                StringBuilder whereClause = new StringBuilder();
                for (int i = 0; i < groupsToLoad.size(); ++i) {
                    whereClause.append("uid = ").append(groupsToLoad.keyAt(i)).append(" AND group_id = ").append(groupsToLoad.valueAt(i));
                    if (i + 1 < groupsToLoad.size()) {
                        whereClause.append(" OR ");
                    }
                }
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, data, read_state, mid, send_state, date, replydata FROM messages_v2 WHERE %s ORDER BY date DESC", whereClause));
                int count = 0;
                while (cursor.next()) {
                    count++;
                    long dialogId = cursor.longValue(0);
                    TLRPC.Dialog dialog = null;
                    for (int i = 0; i < dialogs.dialogs.size(); ++i) {
                        TLRPC.Dialog d = dialogs.dialogs.get(i);
                        if (d != null && d.id == dialogId) {
                            dialog = d;
                            break;
                        }
                    }
                    if (dialog == null) {
                        continue;
                    }
                    NativeByteBuffer data = cursor.byteBufferValue(1);
                    if (data != null) {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        if (message != null) {
                            message.readAttachPath(data, getUserConfig().clientUserId);
                            data.reuse();
                            MessageObject.setUnreadFlags(message, cursor.intValue(2));
                            message.id = cursor.intValue(3);
                            int date = cursor.intValue(5);
                            if (date != 0) {
                                dialog.last_message_date = date;
                            }
                            message.send_state = cursor.intValue(4);
                            message.dialog_id = dialog.id;
                            dialogs.messages.add(message);

                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);

                            try {
                                if (message.reply_to != null && message.reply_to.reply_to_msg_id != 0 && (
                                        message.action instanceof TLRPC.TL_messageActionPinMessage ||
                                                message.action instanceof TLRPC.TL_messageActionPaymentSent ||
                                                message.action instanceof TLRPC.TL_messageActionGameScore)) {
                                    if (!cursor.isNull(6)) {
                                        NativeByteBuffer data2 = cursor.byteBufferValue(6);
                                        if (data2 != null) {
                                            message.replyMessage = TLRPC.Message.TLdeserialize(data2, data2.readInt32(false), false);
                                            message.replyMessage.readAttachPath(data2, getUserConfig().clientUserId);
                                            data2.reuse();
                                            if (message.replyMessage != null) {
                                                addUsersAndChatsFromMessage(message.replyMessage, usersToLoad, chatsToLoad, null);
                                            }
                                        }
                                    }
                                    if (message.replyMessage == null) {
                                        replyMessageOwners.put(dialog.id, message);
                                    }
                                }
                            } catch (Exception e) {
                                checkSQLException(e);
                            }
                        } else {
                            data.reuse();
                        }
                    }
                }
                cursor.dispose();
            }

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

                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);

                            ownerMessage.replyMessage = message;
                            message.dialog_id = ownerMessage.dialog_id;
                        }
                    }
                    replyCursor.dispose();
                }
            }
        } catch (Exception e) {
            throw e;
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
        return dialogs;
    }

    private void loadDialogFilters() {
        storageQueue.postRunnable(() -> {
            SQLiteCursor filtersCursor = null;
            SQLitePreparedStatement state = null;
            try {
                ArrayList<Long> usersToLoad = new ArrayList<>();
                ArrayList<Long> chatsToLoad = new ArrayList<>();
                ArrayList<Integer> encryptedToLoad = new ArrayList<>();
                ArrayList<Long> dialogsToLoad = new ArrayList<>();
                SparseArray<MessagesController.DialogFilter> filtersById = new SparseArray<>();

                usersToLoad.add(getUserConfig().getClientUserId());

                filtersCursor = database.queryFinalized("SELECT id, ord, unread_count, flags, title FROM dialog_filter WHERE 1");

                boolean updateCounters = false;
                boolean hasDefaultFilter = false;
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
                    if (filter.id == 0) {
                        hasDefaultFilter = true;
                    }
                }
                filtersCursor.dispose();
                filtersCursor = null;

                if (!hasDefaultFilter) {
                    MessagesController.DialogFilter filter = new MessagesController.DialogFilter();
                    filter.id = 0;
                    filter.order = 0;
                    filter.name = "ALL_CHATS";
                    for (int i = 0; i < dialogFilters.size(); i++) {
                        dialogFilters.get(i).order++;
                    }
                    dialogFilters.add(filter);
                    dialogFiltersMap.put(filter.id, filter);
                    filtersById.put(filter.id, filter);

                    state = database.executeFast("REPLACE INTO dialog_filter VALUES(?, ?, ?, ?, ?)");
                    state.bindInteger(1, filter.id);
                    state.bindInteger(2, filter.order);
                    state.bindInteger(3, filter.unreadCount);
                    state.bindInteger(4, filter.flags);
                    state.bindString(5, filter.name);
                    state.stepThis().dispose();
                    state = null;
                }

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
                checkSQLException(e);
            } finally {
                if (filtersCursor != null) {
                    filtersCursor.dispose();
                }
                if (state != null) {
                    state.dispose();
                }
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
        SQLiteCursor cursor = null;
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

            LongSparseIntArray forumUnreadCount = new LongSparseIntArray();
            cursor = database.queryFinalized("SELECT DISTINCT did FROM topics WHERE unread_count > 0 OR unread_mentions > 0");
            while (cursor.next()) {
                long dialogId = cursor.longValue(0);
                if (isForum(dialogId)) {
                    forumUnreadCount.put(dialogId, 1);
                }
            }
            cursor = database.queryFinalized("SELECT did, folder_id, unread_count, unread_count_i FROM dialogs WHERE unread_count > 0 OR flags > 0 UNION ALL " +
                    "SELECT did, folder_id, unread_count, unread_count_i FROM dialogs WHERE unread_count_i > 0");
            while (cursor.next()) {
                int folderId = cursor.intValue(1);
                long did = cursor.longValue(0);
                int unread;
                int mentions = 0;
                if (isForum(did)) {
                    unread = forumUnreadCount.get(did, 0);
                    if (unread == 0) {
                        continue;
                    }
                } else {
                    unread = cursor.intValue(2);
                    mentions = cursor.intValue(3);
                }
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
            cursor = null;
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
                    boolean muted = getMessagesController().isDialogMuted(user.id, 0);
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
                        boolean muted = getMessagesController().isDialogMuted(did, 0);
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
                    boolean muted = getMessagesController().isDialogMuted(-chat.id, 0, chat);
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
            checkSQLException(e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    private void saveDialogFilterInternal(MessagesController.DialogFilter filter, boolean atBegin, boolean peers) {
        SQLitePreparedStatement state = null;
        try {
            if (!dialogFilters.contains(filter)) {
                if (atBegin) {
                    dialogFilters.add(0, filter);
                } else {
                    dialogFilters.add(filter);
                }
                dialogFiltersMap.put(filter.id, filter);
            }

            state = database.executeFast("REPLACE INTO dialog_filter VALUES(?, ?, ?, ?, ?)");
            state.bindInteger(1, filter.id);
            state.bindInteger(2, filter.order);
            state.bindInteger(3, filter.unreadCount);
            state.bindInteger(4, filter.flags);
            state.bindString(5, filter.id == 0 ? "ALL_CHATS" : filter.name);
            state.step();
            state.dispose();
            state = null;
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
                state = null;

                state = database.executeFast("REPLACE INTO dialog_filter_ep VALUES(?, ?)");
                for (int a = 0, N = filter.neverShow.size(); a < N; a++) {
                    state.requery();
                    state.bindInteger(1, filter.id);
                    state.bindLong(2, filter.neverShow.get(a));
                    state.step();
                }
                state.dispose();
                state = null;
                database.commitTransaction();
            }
        } catch (Exception e) {
            checkSQLException(e);
        } finally {
            if (database != null) {
                database.commitTransaction();
            }
            if (state != null) {
                state.dispose();
            }
        }
    }

    private ArrayList<Long> toPeerIds(ArrayList<TLRPC.InputPeer> inputPeers) {
        ArrayList<Long> array = new ArrayList<Long>();
        if (inputPeers == null) {
            return array;
        }
        final int count = inputPeers.size();
        for (int i = 0; i < count; ++i) {
            TLRPC.InputPeer peer = inputPeers.get(i);
            if (peer == null) {
                continue;
            }
            long id;
            if (peer.user_id != 0) {
                id = peer.user_id;
            } else {
                id = -(peer.chat_id != 0 ? peer.chat_id : peer.channel_id);
            }
            array.add(id);
        }
        return array;
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
                HashMap<Integer, HashSet<Long>> filterDialogRemovals = new HashMap<>();
                HashSet<Integer> filtersUnreadCounterReset = new HashSet<>();
                for (int a = 0, N = vector.objects.size(); a < N; a++) {
                    TLRPC.DialogFilter newFilter = (TLRPC.DialogFilter) vector.objects.get(a);
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

                        filter.pinnedDialogs.clear();
                        for (int b = 0, N2 = newFilter.pinned_peers.size(); b < N2; b++) {
                            TLRPC.InputPeer peer = newFilter.pinned_peers.get(b);
                            Long id;
                            if (peer.user_id != 0) {
                                id = peer.user_id;
                            } else {
                                id = -(peer.chat_id != 0 ? peer.chat_id : peer.channel_id);
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

                        for (int c = 0; c < 2; c++) {
                            ArrayList<Long> fromArray = toPeerIds(c == 0 ? newFilter.include_peers : newFilter.exclude_peers);
                            ArrayList<Long> toArray = c == 0 ? filter.alwaysShow : filter.neverShow;

                            if (c == 0) {
                                // put pinned_peers into include_peers (alwaysShow)
                                ArrayList<Long> pinnedArray = toPeerIds(newFilter.pinned_peers);
                                for (int i = 0; i < pinnedArray.size(); ++i) {
                                    fromArray.remove(pinnedArray.get(i));
                                }
                                fromArray.addAll(0, pinnedArray);
                            }

                            final int fromArrayCount = fromArray.size();
                            boolean isDifferent = fromArray.size() != toArray.size();
                            if (!isDifferent) {
                                for (int i = 0; i < fromArrayCount; ++i) {
                                    if (!toArray.contains(fromArray.get(i))) {
                                        isDifferent = true;
                                        break;
                                    }
                                }
                            }

                            if (isDifferent) {
                                unreadChanged = true;
                                changed = true;
                                if (c == 0) {
                                    filter.alwaysShow = fromArray;
                                } else {
                                    filter.neverShow = fromArray;
                                }
                            }
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
                    processLoadedFilterPeersInternal(dialogs, null, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filtersUnreadCounterReset);
                } else {
                    getMessagesController().loadFilterPeers(dialogsToLoadMap, usersToLoadMap, chatsToLoadMap, dialogs, new TLRPC.TL_messages_dialogs(), users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filtersUnreadCounterReset);
                }
            } catch (Exception e) {
                checkSQLException(e);
            }
        });
    }

    private void processLoadedFilterPeersInternal(TLRPC.messages_Dialogs pinnedDialogs, TLRPC.messages_Dialogs pinnedRemoteDialogs, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats, ArrayList<MessagesController.DialogFilter> filtersToSave, SparseArray<MessagesController.DialogFilter> filtersToDelete, ArrayList<Integer> filtersOrder, HashMap<Integer, HashSet<Long>> filterDialogRemovals, HashSet<Integer> filtersUnreadCounterReset) {
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

    protected void processLoadedFilterPeers(TLRPC.messages_Dialogs pinnedDialogs, TLRPC.messages_Dialogs pinnedRemoteDialogs, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats, ArrayList<MessagesController.DialogFilter> filtersToSave, SparseArray<MessagesController.DialogFilter> filtersToDelete, ArrayList<Integer> filtersOrder, HashMap<Integer, HashSet<Long>> filterDialogRemovals, HashSet<Integer> filtersUnreadCounterReset) {
        storageQueue.postRunnable(() -> processLoadedFilterPeersInternal(pinnedDialogs, pinnedRemoteDialogs, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filtersUnreadCounterReset));
    }

    private void deleteDialogFilterInternal(MessagesController.DialogFilter filter) {
        try {
            dialogFilters.remove(filter);
            dialogFiltersMap.remove(filter.id);
            database.executeFast("DELETE FROM dialog_filter WHERE id = " + filter.id).stepThis().dispose();
            database.executeFast("DELETE FROM dialog_filter_ep WHERE id = " + filter.id).stepThis().dispose();
            database.executeFast("DELETE FROM dialog_filter_pin_v2 WHERE id = " + filter.id).stepThis().dispose();
        } catch (Exception e) {
            checkSQLException(e);
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
        SQLitePreparedStatement state = null;
        try {
            state = database.executeFast("UPDATE dialog_filter SET ord = ?, flags = ? WHERE id = ?");
            for (int a = 0, N = dialogFilters.size(); a < N; a++) {
                MessagesController.DialogFilter filter = dialogFilters.get(a);
                state.requery();
                state.bindInteger(1, filter.order);
                state.bindInteger(2, filter.flags);
                state.bindInteger(3, filter.id);
                state.step();
            }
            state.dispose();
            state = null;
        } catch (Exception e) {
            checkSQLException(e);
        } finally {
            if (state != null) {
                state.dispose();
            }
        }
    }

    public void saveDialogFiltersOrder() {
        ArrayList<MessagesController.DialogFilter> filtersFinal = new ArrayList<>(getMessagesController().dialogFilters);
        storageQueue.postRunnable(() -> {
            dialogFilters.clear();
            dialogFiltersMap.clear();
            dialogFilters.addAll(filtersFinal);
            for (int i = 0; i < filtersFinal.size(); i++) {
                filtersFinal.get(i).order = i;
                dialogFiltersMap.put(filtersFinal.get(i).id, filtersFinal.get(i));
            }
            saveDialogFiltersOrderInternal();
        });
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

    protected void loadReplyMessages(LongSparseArray<SparseArray<ArrayList<TLRPC.Message>>> replyMessageOwners, LongSparseArray<ArrayList<Integer>> dialogReplyMessagesIds, ArrayList<Long> usersToLoad, ArrayList<Long> chatsToLoad, boolean scheduled) throws SQLiteException {
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
            SQLiteCursor cursor = null;
            try {
                for (int i = 0; i < 2; i++) {
                    if (i == 1 && !scheduled) {
                        continue;
                    }
                    boolean findInScheduled = i == 1;
                    if (findInScheduled) {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid, date, uid FROM scheduled_messages_v2 WHERE mid IN(%s) AND uid = %d", TextUtils.join(",", ids), dialogId));
                    } else {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid, date, uid FROM messages_v2 WHERE mid IN(%s) AND uid = %d", TextUtils.join(",", ids), dialogId));
                    }
                    while (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.readAttachPath(data, getUserConfig().clientUserId);
                            data.reuse();
                            message.id = cursor.intValue(1);
                            message.date = cursor.intValue(2);
                            message.dialog_id = cursor.longValue(3);

                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);

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
            } catch (Exception e) {
                throw e;
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        }
    }

    public void loadUnreadMessages() {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                ArrayList<Long> usersToLoad = new ArrayList<>();
                ArrayList<Long> chatsToLoad = new ArrayList<>();
                ArrayList<Integer> encryptedChatIds = new ArrayList<>();

                LongSparseArray<Integer> pushDialogs = new LongSparseArray<>();
                cursor = database.queryFinalized("SELECT d.did, d.unread_count, s.flags FROM dialogs as d LEFT JOIN dialog_settings as s ON d.did = s.did WHERE d.unread_count > 0");
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
                cursor = null;

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

                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);
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
                                                addUsersAndChatsFromMessage(message.replyMessage, usersToLoad, chatsToLoad, null);
                                            }
                                        }
                                    }
                                    if (message.replyMessage == null) {
                                        addReplyMessages(message, replyMessageOwners, dialogReplyMessagesIds);
                                    }
                                }
                            } catch (Exception e) {
                                checkSQLException(e);
                            }
                        }
                    }
                    cursor.dispose();
                    cursor = null;

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
                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);
                        }
                    }
                    cursor.dispose();
                    cursor = null;

                    loadReplyMessages(replyMessageOwners, dialogReplyMessagesIds, usersToLoad, chatsToLoad, false);

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
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public void putWallpapers(ArrayList<TLRPC.WallPaper> wallPapers, int action) {
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                if (action == 1) {
                    database.executeFast("DELETE FROM wallpapers2 WHERE num >= -1").stepThis().dispose();
                }
                database.beginTransaction();

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
                state = null;
                database.commitTransaction();
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (database != null) {
                    database.commitTransaction();
                }
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void deleteWallpaper(long id) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM wallpapers2 WHERE uid = " + id).stepThis().dispose();
            } catch (Exception e) {
                checkSQLException(e);
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
                checkSQLException(e);
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
            SQLitePreparedStatement state = null;
            try {
                if (document != null) {
                    state = database.executeFast("UPDATE web_recent_v3 SET document = ? WHERE image_url = ?");
                    state.requery();
                    NativeByteBuffer data = new NativeByteBuffer(document.getObjectSize());
                    document.serializeToStream(data);
                    state.bindByteBuffer(1, data);
                    state.bindString(2, imageUrl);
                    state.step();
                    state.dispose();
                    data.reuse();
                } else {
                    state = database.executeFast("UPDATE web_recent_v3 SET local_url = ? WHERE image_url = ?");
                    state.requery();
                    state.bindString(1, localUrl);
                    state.bindString(2, imageUrl);
                    state.step();
                    state.dispose();
                }
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void deleteUserChatHistory(long dialogId, long fromId) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                ArrayList<Integer> mids = new ArrayList<>();
                cursor = database.queryFinalized("SELECT data FROM messages_v2 WHERE uid = " + dialogId);
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
                    checkSQLException(e);
                }
                cursor.dispose();
                cursor = null;
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
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
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
            if (getMediaDataController().ringtoneDataStore.contains(document.id)) {
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
            if (getMediaDataController().ringtoneDataStore.contains(document.id)) {
                return false;
            }
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
                File file = getFileLoader().getPathToAttach(photoSize, forceCache);
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
            File file = getFileLoader().getPathToAttach(document, forceCache);
            if (file.toString().length() > 0) {
                filesToDelete.add(file);
            }
            for (int a = 0, N = document.thumbs.size(); a < N; a++) {
                TLRPC.PhotoSize photoSize = document.thumbs.get(a);
                file = getFileLoader().getPathToAttach(photoSize);
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
            SQLiteCursor cursor = null;
            SQLiteCursor cursor2 = null;
            SQLitePreparedStatement state5 = null;
            SQLitePreparedStatement state6 = null;
            try {
                if (messagesOnly == 3) {
                    int lastMid = -1;
                    cursor = database.queryFinalized("SELECT last_mid FROM dialogs WHERE did = " + did);
                    if (cursor.next()) {
                        lastMid = cursor.intValue(0);
                    }
                    cursor.dispose();
                    cursor = null;
                    if (lastMid != 0) {
                        return;
                    }
                }
                if (DialogObject.isEncryptedDialog(did) || messagesOnly == 2) {
                    cursor = database.queryFinalized("SELECT data FROM messages_v2 WHERE uid = " + did);
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
                        checkSQLException(e);
                    }
                    cursor.dispose();
                    cursor = null;
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
                    cursor = database.queryFinalized("SELECT last_mid_i, last_mid FROM dialogs WHERE did = " + did);
                    int messageId = -1;
                    if (cursor.next()) {
                        long last_mid_i = cursor.longValue(0);
                        long last_mid = cursor.longValue(1);
                        cursor2 = database.queryFinalized("SELECT data FROM messages_v2 WHERE uid = " + did + " AND mid IN (" + last_mid_i + "," + last_mid + ")");
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
                            checkSQLException(e);
                        }
                        cursor2.dispose();
                        cursor2 = null;

                        database.executeFast("DELETE FROM messages_v2 WHERE uid = " + did + " AND mid != " + last_mid_i + " AND mid != " + last_mid).stepThis().dispose();
                        database.executeFast("DELETE FROM messages_holes WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM bot_keyboard WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM bot_keyboard_topics WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM media_counts_v2 WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM media_v4 WHERE uid = " + did).stepThis().dispose();
                        database.executeFast("DELETE FROM media_holes_v2 WHERE uid = " + did).stepThis().dispose();
                        getMediaDataController().clearBotKeyboard(did);

                        state5 = database.executeFast("REPLACE INTO messages_holes VALUES(?, ?, ?)");
                        state6 = database.executeFast("REPLACE INTO media_holes_v2 VALUES(?, ?, ?, ?)");
                        if (messageId != -1) {
                            createFirstHoles(did, state5, state6, messageId, 0);
                        }
                        state5.dispose();
                        state5 = null;
                        state6.dispose();
                        state6 = null;
                        updateWidgets(did);
                    }
                    cursor.dispose();
                    cursor = null;
                    return;
                }

                database.executeFast("UPDATE dialogs SET unread_count = 0, unread_count_i = 0 WHERE did = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM messages_v2 WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM bot_keyboard WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM bot_keyboard_topics WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM media_counts_v2 WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM media_v4 WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM messages_holes WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM media_holes_v2 WHERE uid = " + did).stepThis().dispose();
                getMediaDataController().clearBotKeyboard(did);
                AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.needReloadRecentDialogsSearch));
                resetAllUnreadCounters(false);
                updateWidgets(did);
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
                if (cursor2 != null) {
                    cursor2.dispose();
                }
                if (state5 != null) {
                    state5.dispose();
                }
                if (state6 != null) {
                    state6.dispose();
                }
            }
        });
    }

    public void onDeleteQueryComplete(long did) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM media_counts_v2 WHERE uid = " + did).stepThis().dispose();
            } catch (Exception e) {
                checkSQLException(e);
            }
        });
    }

    public void getDialogPhotos(long did, int count, int maxId, int classGuid) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {

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
                cursor = null;

                Utilities.stageQueue.postRunnable(() -> getMessagesController().processLoadedUserPhotos(res, messages, did, count, maxId, true, classGuid));
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public void clearUserPhotos(long dialogId) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM user_photos WHERE uid = " + dialogId).stepThis().dispose();
            } catch (Exception e) {
                checkSQLException(e);
            }
        });
    }

    public void clearUserPhoto(long dialogId, long pid) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM user_photos WHERE uid = " + dialogId + " AND id = " + pid).stepThis().dispose();
            } catch (Exception e) {
                checkSQLException(e);
            }
        });
    }

    public void resetDialogs(TLRPC.messages_Dialogs dialogsRes, int messagesCount, int seq, int newPts, int date, int qts, LongSparseArray<TLRPC.Dialog> new_dialogs_dict, LongSparseArray<ArrayList<MessageObject>> new_dialogMessage, TLRPC.Message lastMessage, int dialogsCount) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
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

                cursor = database.queryFinalized("SELECT did, pinned FROM dialogs WHERE 1");
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
                cursor = null;
                String ids = "(" + TextUtils.join(",", dids) + ")";

                database.beginTransaction();
                database.executeFast("DELETE FROM chat_pinned_count WHERE uid IN " + ids).stepThis().dispose();
                database.executeFast("DELETE FROM chat_pinned_v2 WHERE uid IN " + ids).stepThis().dispose();
                database.executeFast("DELETE FROM dialogs WHERE did IN " + ids).stepThis().dispose();
                database.executeFast("DELETE FROM messages_v2 WHERE uid IN " + ids).stepThis().dispose();
                database.executeFast("DELETE FROM polls_v2 WHERE 1").stepThis().dispose();
                database.executeFast("DELETE FROM bot_keyboard WHERE uid IN " + ids).stepThis().dispose();
                database.executeFast("DELETE FROM bot_keyboard_topics WHERE uid IN " + ids).stepThis().dispose();
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
                checkSQLException(e);
            } finally {
                if (database != null) {
                    database.commitTransaction();
                }
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public void putDialogPhotos(long did, TLRPC.photos_Photos photos, ArrayList<TLRPC.Message> messages) {
        if (photos == null) {
            return;
        }
        storageQueue.postRunnable(() -> {
            putDialogPhotosInternal(did, photos, messages);
        });
    }

    private void putDialogPhotosInternal(long did, TLRPC.photos_Photos photos, ArrayList<TLRPC.Message> messages) {
        SQLitePreparedStatement state = null;
        try {
            database.executeFast("DELETE FROM user_photos WHERE uid = " + did).stepThis().dispose();
            state = database.executeFast("REPLACE INTO user_photos VALUES(?, ?, ?)");
            for (int a = 0, N = photos.photos.size(); a < N; a++) {
                TLRPC.Photo photo = photos.photos.get(a);
                if (photo instanceof TLRPC.TL_photoEmpty || photo == null) {
                    continue;
                }
                if (photo.file_reference == null) {
                    photo.file_reference = new byte[0];
                }
                state.requery();
                int size = photo.getObjectSize();
                if (messages != null && messages.get(a) != null) {
                    size += messages.get(a).getObjectSize();
                }
                NativeByteBuffer data = new NativeByteBuffer(size);
                photo.serializeToStream(data);
                if (messages != null && messages.get(a) != null) {
                    messages.get(a).serializeToStream(data);
                }
                state.bindLong(1, did);
                state.bindLong(2, photo.id);
                state.bindByteBuffer(3, data);
                state.step();
                data.reuse();
            }
            state.dispose();
            state = null;
        } catch (Exception e) {
            checkSQLException(e);
        } finally {
            if (state != null) {
                state.dispose();
            }
        }
    }

    public void addDialogPhoto(long did, TLRPC.Photo photoToAdd) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM user_photos WHERE uid = %d ORDER BY rowid ASC", did));

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
                        messages.add(null);
                    }
                }
                cursor.dispose();
                cursor = null;
                res.photos.add(0, photoToAdd);
                putDialogPhotosInternal(did, res, messages);
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public void emptyMessagesMedia(long dialogId, ArrayList<Integer> mids) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            SQLitePreparedStatement state = null;
            try {
                ArrayList<File> filesToDelete = new ArrayList<>();
                ArrayList<String> namesToDelete = new ArrayList<>();
                ArrayList<Pair<Long, Integer>> idsToDelete = new ArrayList<>();
                ArrayList<TLRPC.Message> messages = new ArrayList<>();
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid, date, uid, custom_params FROM messages_v2 WHERE mid IN (%s) AND uid = %d", TextUtils.join(",", mids), dialogId));
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
                            NativeByteBuffer customParams = cursor.byteBufferValue(4);
                            if (customParams != null) {
                                MessageCustomParamsHelper.readLocalParams(message, customParams);
                                customParams.reuse();
                            }
                            messages.add(message);
                        }
                    }
                }
                cursor.dispose();
                cursor = null;
                deleteFromDownloadQueue(idsToDelete, true);
                if (!messages.isEmpty()) {
                    state = database.executeFast("REPLACE INTO messages_v2 VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, 0, ?, ?)");
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
                        NativeByteBuffer customParams = MessageCustomParamsHelper.writeLocalParams(message);
                        if (customParams != null) {
                            state.bindByteBuffer(16, customParams);
                        } else {
                            state.bindNull(16);
                        }
                        if ((message.flags & 131072) != 0) {
                            state.bindLong(17, message.grouped_id);
                        } else {
                            state.bindNull(17);
                        }
                        state.step();
                        data.reuse();
                        if (repliesData != null) {
                            repliesData.reuse();
                        }
                        if (customParams != null) {
                            customParams.reuse();
                        }
                    }
                    state.dispose();
                    state = null;
                    AndroidUtilities.runOnUIThread(() -> {
                        for (int a = 0; a < messages.size(); a++) {
                            getNotificationCenter().postNotificationName(NotificationCenter.updateMessageMedia, messages.get(a));
                        }
                    });
                }
                AndroidUtilities.runOnUIThread(() -> getFileLoader().cancelLoadFiles(namesToDelete));
                getFileLoader().deleteFiles(filesToDelete, 0);
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void updateMessagePollResults(long pollId, TLRPC.Poll poll, TLRPC.PollResults results) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                LongSparseArray<ArrayList<Integer>> dialogs = null;
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, mid FROM polls_v2 WHERE id = %d", pollId));
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
                cursor = null;
                if (dialogs != null) {
                    database.beginTransaction();
                    SQLitePreparedStatement state = database.executeFast("UPDATE messages_v2 SET data = ? WHERE mid = ? AND uid = ?");
                    SQLitePreparedStatement state_topics = database.executeFast("UPDATE messages_topics SET data = ? WHERE mid = ? AND uid = ?");
                    for (int b = 0, N2 = dialogs.size(); b < N2; b++) {
                        long dialogId = dialogs.keyAt(b);
                        ArrayList<Integer> mids = dialogs.valueAt(b);
                        for (int a = 0, N = mids.size(); a < N; a++) {
                            Integer mid = mids.get(a);
                            boolean foundMessage = false;
                            for (int k = 0; k < 2; k++) {
                                boolean isTopic = k == 1;
                                if (isTopic) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM messages_topics WHERE mid = %d AND uid = %d", mid, dialogId));
                                } else {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM messages_v2 WHERE mid = %d AND uid = %d", mid, dialogId));
                                }
                                SQLitePreparedStatement currentState = isTopic ? state_topics : state;
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
                                            currentState.requery();
                                            currentState.bindByteBuffer(1, data);
                                            currentState.bindInteger(2, mid);
                                            currentState.bindLong(3, dialogId);
                                            currentState.step();
                                            data.reuse();
                                        }
                                    }
                                    foundMessage = true;
                                }
                                cursor.dispose();
                            }
                            if (!foundMessage) {
                                database.executeFast(String.format(Locale.US, "DELETE FROM polls_v2 WHERE mid = %d AND uid = %d", mid, dialogId)).stepThis().dispose();
                            }
                        }
                    }
                    state.dispose();
                    database.commitTransaction();
                }
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public void updateMessageReactions(long dialogId, int msgId, TLRPC.TL_messageReactions reactions) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                database.beginTransaction();
                for (int i = 0; i < 2; i++) {
                    if (i == 0) {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM messages_v2 WHERE mid = %d AND uid = %d", msgId, dialogId));
                    } else {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM messages_topics WHERE mid = %d AND uid = %d", msgId, dialogId));
                    }
                    if (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            if (message != null) {
                                message.readAttachPath(data, getUserConfig().clientUserId);
                                data.reuse();
                                MessageObject.updateReactions(message, reactions);
                                SQLitePreparedStatement state;
                                if (i == 0) {
                                    state = database.executeFast("UPDATE messages_v2 SET data = ? WHERE mid = ? AND uid = ?");
                                } else {
                                    state = database.executeFast("UPDATE messages_topics SET data = ? WHERE mid = ? AND uid = ?");
                                }
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
                    cursor = null;
                }
                database.commitTransaction();
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (database != null) {
                    database.commitTransaction();
                }
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public void updateMessageVoiceTranscriptionOpen(long dialogId, int msgId, TLRPC.Message saveFromMessage) {
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                database.beginTransaction();
                TLRPC.Message message = getMessageWithCustomParamsOnlyInternal(msgId, dialogId);
                message.voiceTranscriptionOpen = saveFromMessage.voiceTranscriptionOpen;
                message.voiceTranscriptionRated = saveFromMessage.voiceTranscriptionRated;
                message.voiceTranscriptionFinal = saveFromMessage.voiceTranscriptionFinal;
                message.voiceTranscriptionForce = saveFromMessage.voiceTranscriptionForce;
                message.voiceTranscriptionId = saveFromMessage.voiceTranscriptionId;

                for (int i = 0; i < 2; i++) {
                    if (i == 0) {
                        state = database.executeFast("UPDATE messages_v2 SET custom_params = ? WHERE mid = ? AND uid = ?");
                    } else {
                        state = database.executeFast("UPDATE messages_topics SET custom_params = ? WHERE mid = ? AND uid = ?");
                    }
                    state.requery();
                    NativeByteBuffer nativeByteBuffer = MessageCustomParamsHelper.writeLocalParams(message);
                    if (nativeByteBuffer != null) {
                        state.bindByteBuffer(1, nativeByteBuffer);
                    } else {
                        state.bindNull(1);
                    }
                    state.bindInteger(2, msgId);
                    state.bindLong(3, dialogId);
                    state.step();
                    state.dispose();
                    state = null;
                    if (nativeByteBuffer != null) {
                        nativeByteBuffer.reuse();
                    }
                }
                database.commitTransaction();
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (database != null) {
                    database.commitTransaction();
                }
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void updateMessageVoiceTranscription(long dialogId, int messageId, String text, long transcriptionId, boolean isFinal) {
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                database.beginTransaction();
                TLRPC.Message message = getMessageWithCustomParamsOnlyInternal(messageId, dialogId);
                message.voiceTranscriptionFinal = isFinal;
                message.voiceTranscriptionId = transcriptionId;
                message.voiceTranscription = text;

                state = database.executeFast("UPDATE messages_v2 SET custom_params = ? WHERE mid = ? AND uid = ?");
                state.requery();
                NativeByteBuffer nativeByteBuffer = MessageCustomParamsHelper.writeLocalParams(message);
                if (nativeByteBuffer != null) {
                    state.bindByteBuffer(1, nativeByteBuffer);
                } else {
                    state.bindNull(1);
                }
                state.bindInteger(2, messageId);
                state.bindLong(3, dialogId);
                state.step();
                state.dispose();
                state = null;
                database.commitTransaction();
                if (nativeByteBuffer != null) {
                    nativeByteBuffer.reuse();
                }
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (database != null) {
                    database.commitTransaction();
                }
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void updateMessageVoiceTranscription(long dialogId, int messageId, String text, TLRPC.Message saveFromMessage) {
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                database.beginTransaction();
                TLRPC.Message message = getMessageWithCustomParamsOnlyInternal(messageId, dialogId);
                message.voiceTranscriptionOpen = saveFromMessage.voiceTranscriptionOpen;
                message.voiceTranscriptionRated = saveFromMessage.voiceTranscriptionRated;
                message.voiceTranscriptionFinal = saveFromMessage.voiceTranscriptionFinal;
                message.voiceTranscriptionForce = saveFromMessage.voiceTranscriptionForce;
                message.voiceTranscriptionId = saveFromMessage.voiceTranscriptionId;
                message.voiceTranscription = text;

                for (int i = 0; i < 2; i++) {
                    if (i == 0) {
                        state = database.executeFast("UPDATE messages_v2 SET custom_params = ? WHERE mid = ? AND uid = ?");
                    } else {
                        state = database.executeFast("UPDATE messages_topics SET custom_params = ? WHERE mid = ? AND uid = ?");
                    }
                    state.requery();
                    NativeByteBuffer nativeByteBuffer = MessageCustomParamsHelper.writeLocalParams(message);
                    if (nativeByteBuffer != null) {
                        state.bindByteBuffer(1, nativeByteBuffer);
                    } else {
                        state.bindNull(1);
                    }
                    state.bindInteger(2, messageId);
                    state.bindLong(3, dialogId);
                    state.step();
                    state.dispose();
                    state = null;
                    database.commitTransaction();
                    if (nativeByteBuffer != null) {
                        nativeByteBuffer.reuse();
                    }
                }
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (database != null) {
                    database.commitTransaction();
                }
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void updateMessageCustomParams(long dialogId, TLRPC.Message saveFromMessage) {
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                database.beginTransaction();
                TLRPC.Message message = getMessageWithCustomParamsOnlyInternal(saveFromMessage.id, dialogId);
                MessageCustomParamsHelper.copyParams(saveFromMessage, message);

                for (int i = 0; i < 2; i++) {
                    if (i == 0) {
                        state = database.executeFast("UPDATE messages_v2 SET custom_params = ? WHERE mid = ? AND uid = ?");
                    } else {
                        state = database.executeFast("UPDATE messages_topics SET custom_params = ? WHERE mid = ? AND uid = ?");
                    }
                    state.requery();
                    NativeByteBuffer nativeByteBuffer = MessageCustomParamsHelper.writeLocalParams(message);
                    if (nativeByteBuffer != null) {
                        state.bindByteBuffer(1, nativeByteBuffer);
                    } else {
                        state.bindNull(1);
                    }
                    state.bindInteger(2, saveFromMessage.id);
                    state.bindLong(3, dialogId);
                    state.step();
                    state.dispose();
                    state = null;
                    if (nativeByteBuffer != null) {
                        nativeByteBuffer.reuse();
                    }
                }
                database.commitTransaction();
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (database != null) {
                    database.commitTransaction();
                }
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public TLRPC.Message getMessageWithCustomParamsOnlyInternal(int messageId, long dialogId) {
        TLRPC.Message message = new TLRPC.TL_message();
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized("SELECT custom_params FROM messages_v2 WHERE mid = " + messageId + " AND uid = " + dialogId);
            boolean read = false;
            if (cursor.next()) {
                MessageCustomParamsHelper.readLocalParams(message, cursor.byteBufferValue(0));
                read = true;
            }
            cursor.dispose();
            cursor = null;
            if (!read) {
                cursor = database.queryFinalized("SELECT custom_params FROM messages_topics WHERE mid = " + messageId + " AND uid = " + dialogId);
                if (cursor.next()) {
                    MessageCustomParamsHelper.readLocalParams(message, cursor.byteBufferValue(0));
                    read = true;
                }
                cursor.dispose();
                cursor = null;
            }
        } catch (SQLiteException e) {
            checkSQLException(e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
        return message;
    }

    public void getNewTask(LongSparseArray<ArrayList<Integer>> oldTask, LongSparseArray<ArrayList<Integer>> oldTaskMedia) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
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
                cursor = database.queryFinalized("SELECT mid, date, media, uid FROM enc_tasks_v4 WHERE date = (SELECT min(date) FROM enc_tasks_v4)");
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
                cursor = null;
                getMessagesController().processLoadedDeleteTask(date, newTask, newTaskMedia);
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public void markMentionMessageAsRead(long dialogId, int messageId, long did) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                database.executeFast(String.format(Locale.US, "UPDATE messages_v2 SET read_state = read_state | 2 WHERE mid = %d AND uid = %d", messageId, dialogId)).stepThis().dispose();
                cursor = database.queryFinalized("SELECT unread_count_i FROM dialogs WHERE did = " + did);
                int old_mentions_count = 0;
                if (cursor.next()) {
                    old_mentions_count = Math.max(0, cursor.intValue(0) - 1);
                }
                cursor.dispose();
                cursor = null;
                database.executeFast(String.format(Locale.US, "UPDATE dialogs SET unread_count_i = %d WHERE did = %d", old_mentions_count, did)).stepThis().dispose();
                LongSparseIntArray sparseArray = new LongSparseIntArray(1);
                sparseArray.put(did, old_mentions_count);
                if (old_mentions_count == 0) {
                    updateFiltersReadCounter(null, sparseArray, true);
                }
                getMessagesController().processDialogsUpdateRead(null, sparseArray);

                database.executeFast(String.format(Locale.US, "UPDATE messages_topics SET read_state = read_state | 2 WHERE mid = %d AND uid = %d", messageId, dialogId)).stepThis().dispose();
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM messages_topics WHERE mid = %d AND uid = %d", messageId, dialogId));
                int topicId = 0;
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                        topicId = MessageObject.getTopicId(message, isForum(dialogId));
                    }
                }
                cursor.dispose();
                cursor = null;

                if (topicId != 0) {
                    int topicMentionsCount = 0;
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT unread_mentions FROM topics WHERE did = %d AND topic_id = %d", did, topicId));
                    if (cursor.next()) {
                        topicMentionsCount = Math.max(0, cursor.intValue(0) - 1);
                    }
                    cursor.dispose();
                    cursor = null;

                    database.executeFast(String.format(Locale.US, "UPDATE topics SET unread_mentions = %d WHERE did = %d AND topic_id = %d",topicMentionsCount, dialogId, topicId)).stepThis().dispose();

                    getMessagesController().getTopicsController().updateMentionsUnread(dialogId, topicId, topicMentionsCount);
                }

            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public void markMessageAsMention(long dialogId, int mid) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast(String.format(Locale.US, "UPDATE messages_v2 SET mention = 1, read_state = read_state & ~2 WHERE mid = %d AND uid = %d", mid, dialogId)).stepThis().dispose();
            } catch (Exception e) {
                checkSQLException(e);
            }
        });
    }

    public void resetMentionsCount(long did, int topicId, int count) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                if (topicId == 0) {
                    int prevUnreadCount = 0;
                    cursor = database.queryFinalized("SELECT unread_count_i FROM dialogs WHERE did = " + did);
                    if (cursor.next()) {
                        prevUnreadCount = cursor.intValue(0);
                    }
                    cursor.dispose();
                    cursor = null;
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
                } else {
                    database.executeFast(String.format(Locale.US, "UPDATE topics SET unread_mentions = %d WHERE did = %d AND topic_id = %d", count, did, topicId)).stepThis().dispose();
                    TopicsController.TopicUpdate topicUpdate = new TopicsController.TopicUpdate();
                    topicUpdate.dialogId = did;
                    topicUpdate.topicId = topicId;
                    topicUpdate.onlyCounters = true;
                    topicUpdate.unreadMentions = count;
                    topicUpdate.unreadCount = -1;
                    getMessagesController().getTopicsController().processUpdate(Collections.singletonList(topicUpdate));
                }
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public void createTaskForMid(long dialogId, int messageId, int time, int readTime, int ttl, boolean inner) {
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
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

                state = database.executeFast("REPLACE INTO enc_tasks_v4 VALUES(?, ?, ?, ?)");
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
                state = null;
                database.executeFast(String.format(Locale.US, "UPDATE messages_v2 SET ttl = 0 WHERE mid = %d AND uid = %d", messageId, dialogId)).stepThis().dispose();
                getMessagesController().didAddedNewTask(minDate, dialogId, messages);
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void createTaskForSecretChat(int chatId, int time, int readTime, int isOut, ArrayList<Long> random_ids) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            SQLitePreparedStatement state = null;
            try {
                long dialogId = DialogObject.makeEncryptedDialogId(chatId);
                int minDate = Integer.MAX_VALUE;
                SparseArray<ArrayList<Integer>> messages = new SparseArray<>();
                ArrayList<Integer> midsArray = new ArrayList<>();
                StringBuilder mids = new StringBuilder();
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
                cursor = null;

                if (random_ids != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        markMessagesContentAsRead(dialogId, midsArray, 0);
                        getNotificationCenter().postNotificationName(NotificationCenter.messagesReadContent, dialogId, midsArray);
                    });
                }

                if (messages.size() != 0) {
                    database.beginTransaction();
                    state = database.executeFast("REPLACE INTO enc_tasks_v4 VALUES(?, ?, ?, ?)");
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
                    state = null;
                    database.commitTransaction();
                    database.executeFast(String.format(Locale.US, "UPDATE messages_v2 SET ttl = 0 WHERE mid IN(%s) AND uid = %d", mids.toString(), dialogId)).stepThis().dispose();
                    getMessagesController().didAddedNewTask(minDate, dialogId, messages);
                }
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (database != null) {
                    database.commitTransaction();
                }
                if (state != null) {
                    state.dispose();
                }
                if (cursor != null) {
                    cursor.dispose();
                }
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
                boolean muted = getMessagesController().isDialogMuted(user.id, 0);
                Integer folderId = dialogsByFolders.get(user.id);
                int idx1 = folderId == null || folderId < 0 || folderId > 1 ? 0 : folderId;
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
                    boolean muted = getMessagesController().isDialogMuted(did, 0);
                    Integer folderId = dialogsByFolders.get(did);
                    int idx1 = folderId == null || folderId < 0 || folderId > 1 ? 0 : folderId;
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
                boolean muted = getMessagesController().isDialogMuted(-chat.id, 0, chat);
                boolean hasUnread = dialogsWithUnread.indexOfKey(-chat.id) >= 0;
                boolean hasMention = dialogsWithMentions.indexOfKey(-chat.id) >= 0;
                Integer folderId = dialogsByFolders.get(-chat.id);
                int idx1 = folderId == null || folderId < 0 || folderId > 1 ? 0 : folderId;
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

    private void updateDialogsWithReadMessagesInternal(ArrayList<Integer> messages, LongSparseIntArray inbox, LongSparseIntArray outbox, LongSparseArray<ArrayList<Integer>> mentions, LongSparseIntArray stillUnreadMessagesCount) {
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
                        int stillUnread = stillUnreadMessagesCount == null ? -2 : stillUnreadMessagesCount.get(key, -2);

                        if (stillUnread >= 0) {
                            dialogsToUpdate.put(key, stillUnread);
                            if (BuildVars.DEBUG_VERSION) {
                                FileLog.d(key + " update unread messages count by still unread " + stillUnread);
                            }
                        } else {
                            boolean canCountByMessageId = true;

                            if (stillUnreadMessagesCount != null && stillUnread != -2) {
                                SQLiteCursor checkHolesCursor = database.queryFinalized(String.format(Locale.US, "SELECT start, end FROM messages_holes WHERE uid = %d AND end > %d", key, messageId));
                                while (checkHolesCursor.next()) {
                                    canCountByMessageId = false;
                                }
                                checkHolesCursor.dispose();
                            }

                            if (canCountByMessageId) {
                                SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM messages_v2 WHERE uid = %d AND mid > %d AND read_state IN(0,2) AND out = 0", key, messageId));
                                if (cursor.next()) {
                                    int unread = cursor.intValue(0);
                                    dialogsToUpdate.put(key, unread);
                                    if (BuildVars.DEBUG_VERSION) {
                                        FileLog.d(key + " update unread messages count " + unread);
                                    }
                                } else {
                                    if (BuildVars.DEBUG_VERSION) {
                                        FileLog.d(key + " can't update unread messages count cursor trouble");
                                    }
                                }
                                cursor.dispose();
                            } else {
                                if (BuildVars.DEBUG_VERSION) {
                                    FileLog.d(key + " can't update unread messages count");
                                }
                            }
                        }

                        FileLog.d(key + " set inbox max " + messageId);
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
                        if (isForum(did)) {
                            dialogsToUpdate.removeAt(a);
                            a--;
                            continue;
                        }
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
                        long did = dialogsToUpdateMentions.keyAt(a);
                        if (isForum(did)) {
                            dialogsToUpdateMentions.removeAt(a);
                            a--;
                            continue;
                        }
                        state.requery();
                        state.bindInteger(1, dialogsToUpdateMentions.valueAt(a));
                        state.bindLong(2, did);
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
            checkSQLException(e);
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

    public void updateDialogsWithReadMessages(LongSparseIntArray inbox, LongSparseIntArray outbox, LongSparseArray<ArrayList<Integer>> mentions, LongSparseIntArray stillUnread, boolean useQueue) {
        if (isEmpty(inbox) && isEmpty(outbox) && isEmpty(mentions) && isEmpty(stillUnread)) {
            return;
        }
        if (useQueue) {
            storageQueue.postRunnable(() -> updateDialogsWithReadMessagesInternal(null, inbox, outbox, mentions, stillUnread));
        } else {
            updateDialogsWithReadMessagesInternal(null, inbox, outbox, mentions, stillUnread);
        }
    }

    public void updateChatParticipants(TLRPC.ChatParticipants participants) {
        if (participants == null) {
            return;
        }
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized("SELECT info, pinned, online, inviter FROM chat_settings_v2 WHERE uid = " + participants.chat_id);
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
                cursor = null;
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
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public void loadChannelAdmins(long chatId) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized("SELECT uid, data FROM channel_admins_v3 WHERE did = " + chatId);
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
                cursor = null;
                getMessagesController().processLoadedChannelAdmins(ids, chatId, true);
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public void putChannelAdmins(long chatId, LongSparseArray<TLRPC.ChannelParticipant> ids) {
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                database.executeFast("DELETE FROM channel_admins_v3 WHERE did = " + chatId).stepThis().dispose();
                database.beginTransaction();
                state = database.executeFast("REPLACE INTO channel_admins_v3 VALUES(?, ?, ?)");
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
                state = null;
                database.commitTransaction();
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (database != null) {
                    database.commitTransaction();
                }
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void updateChannelUsers(long channelId, ArrayList<TLRPC.ChannelParticipant> participants) {
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                long did = -channelId;
                database.executeFast("DELETE FROM channel_users_v2 WHERE did = " + did).stepThis().dispose();
                database.beginTransaction();
                state = database.executeFast("REPLACE INTO channel_users_v2 VALUES(?, ?, ?, ?)");
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
                state = null;
                database.commitTransaction();
                loadChatInfo(channelId, true, null, false, true);
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (database != null) {
                    database.commitTransaction();
                }
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void saveBotCache(String key, TLObject result) {
        if (result == null || TextUtils.isEmpty(key)) {
            return;
        }
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                int currentDate = getConnectionsManager().getCurrentTime();
                if (result instanceof TLRPC.TL_messages_botCallbackAnswer) {
                    currentDate += ((TLRPC.TL_messages_botCallbackAnswer) result).cache_time;
                } else if (result instanceof TLRPC.TL_messages_botResults) {
                    currentDate += ((TLRPC.TL_messages_botResults) result).cache_time;
                }
                state = database.executeFast("REPLACE INTO botcache VALUES(?, ?, ?)");
                NativeByteBuffer data = new NativeByteBuffer(result.getObjectSize());
                result.serializeToStream(data);
                state.bindString(1, key);
                state.bindInteger(2, currentDate);
                state.bindByteBuffer(3, data);
                state.step();
                state.dispose();
                state = null;
                data.reuse();
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (state !=  null) {
                    state.dispose();
                }
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
            SQLiteCursor cursor = null;
            try {
                database.executeFast("DELETE FROM botcache WHERE date < " + currentDate).stepThis().dispose();
                cursor = database.queryFinalized("SELECT data FROM botcache WHERE id = ?", key);
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
                        checkSQLException(e);
                    }
                }
                cursor.dispose();
                cursor = null;
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                requestDelegate.run(result, null);
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public ArrayList<TLRPC.UserFull> loadUserInfos(HashSet<Long> uids) {
        ArrayList<TLRPC.UserFull> arrayList = new ArrayList<>();
        try {
            String ids = TextUtils.join(",", uids);
            SQLiteCursor cursor = database.queryFinalized("SELECT info, pinned FROM user_settings WHERE uid IN(" + ids + ")");
            while (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null) {
                    TLRPC.UserFull info = TLRPC.UserFull.TLdeserialize(data, data.readInt32(false), false);
                    info.pinned_msg_id = cursor.intValue(1);
                    arrayList.add(info);
                    data.reuse();

                }
            }
            cursor.dispose();
            cursor = null;
        } catch (Exception e) {
            checkSQLException(e);
        }
        return arrayList;
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
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized("SELECT info, pinned FROM user_settings WHERE uid = " + user.id);
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        info = TLRPC.UserFull.TLdeserialize(data, data.readInt32(false), false);
                        info.pinned_msg_id = cursor.intValue(1);
                        data.reuse();
                    }
                }
                cursor.dispose();
                cursor = null;

                cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT mid FROM chat_pinned_v2 WHERE uid = %d ORDER BY mid DESC", user.id));
                while (cursor.next()) {
                    int id = cursor.intValue(0);
                    pinnedMessages.add(id);
                    pinnedMessagesMap.put(id, null);
                }
                cursor.dispose();
                cursor = null;

                cursor = database.queryFinalized("SELECT count, end FROM chat_pinned_count WHERE uid = " + user.id);
                if (cursor.next()) {
                    totalPinnedCount = cursor.intValue(0);
                    pinnedEndReached = cursor.intValue(1) != 0;
                }
                cursor.dispose();
                cursor = null;

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
                checkSQLException(e);
            } finally {
                getMessagesController().processUserInfo(user, info, true, force, classGuid, pinnedMessages, pinnedMessagesMap, totalPinnedCount, pinnedEndReached);
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public void updateUserInfo(TLRPC.UserFull info, boolean ifExist) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            SQLitePreparedStatement state = null;
            try {
                if (ifExist) {
                    cursor = database.queryFinalized("SELECT uid FROM user_settings WHERE uid = " + info.user.id);
                    boolean exist = cursor.next();
                    cursor.dispose();
                    cursor = null;
                    if (!exist) {
                        return;
                    }
                }
                state = database.executeFast("REPLACE INTO user_settings VALUES(?, ?, ?)");
                NativeByteBuffer data = new NativeByteBuffer(info.getObjectSize());
                info.serializeToStream(data);
                state.bindLong(1, info.user.id);
                state.bindByteBuffer(2, data);
                state.bindInteger(3, info.pinned_msg_id);
                state.step();
                state.dispose();
                state = null;
                data.reuse();
                if ((info.flags & 2048) != 0) {
                    state = database.executeFast("UPDATE dialogs SET folder_id = ? WHERE did = ?");
                    state.bindInteger(1, info.folder_id);
                    state.bindLong(2, info.user.id);
                    state.step();
                    state.dispose();
                    state = null;
                    unknownDialogsIds.remove(info.user.id);
                }
                if ((info.flags & 16384) != 0) {
                    state = database.executeFast("UPDATE dialogs SET ttl_period = ? WHERE did = ?");
                    state.bindInteger(1, info.ttl_period);
                    state.bindLong(2, info.user.id);
                    state.step();
                    state.dispose();
                    state = null;
                }
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public void saveChatInviter(long chatId, long inviterId) {
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                state = database.executeFast("UPDATE chat_settings_v2 SET inviter = ? WHERE uid = ?");
                state.requery();
                state.bindLong(1, inviterId);
                state.bindLong(2, chatId);
                state.step();
                state.dispose();
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void saveChatLinksCount(long chatId, int linksCount) {
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                state = database.executeFast("UPDATE chat_settings_v2 SET links = ? WHERE uid = ?");
                state.requery();
                state.bindInteger(1, linksCount);
                state.bindLong(2, chatId);
                state.step();
                state.dispose();
                state = null;
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void updateChatInfo(TLRPC.ChatFull info, boolean ifExist) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            SQLitePreparedStatement state = null;
            try {
                int currentOnline = -1;
                int inviter = 0;
                int links = 0;
                cursor = database.queryFinalized("SELECT online, inviter, links FROM chat_settings_v2 WHERE uid = " + info.id);
                if (cursor.next()) {
                    currentOnline = cursor.intValue(0);
                    info.inviterId = cursor.longValue(1);
                    links = cursor.intValue(2);
                }
                cursor.dispose();
                cursor = null;
                if (ifExist && currentOnline == -1) {
                    return;
                }

                if (currentOnline >= 0 && (info.flags & 8192) == 0) {
                    info.online_count = currentOnline;
                }

                if (links >= 0) {
                    info.invitesCount = links;
                }

                state = database.executeFast("REPLACE INTO chat_settings_v2 VALUES(?, ?, ?, ?, ?, ?)");
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
                state = null;
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
                    cursor = null;
                }
                if ((info.flags & 2048) != 0) {
                    state = database.executeFast("UPDATE dialogs SET folder_id = ? WHERE did = ?");
                    state.bindInteger(1, info.folder_id);
                    state.bindLong(2, -info.id);
                    state.step();
                    state.dispose();
                    state = null;
                    unknownDialogsIds.remove(-info.id);
                }

                state = database.executeFast("UPDATE dialogs SET ttl_period = ? WHERE did = ?");
                state.bindInteger(1, info.ttl_period);
                state.bindLong(2, -info.id);
                state.step();
                state.dispose();
                state = null;
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void updateChatOnlineCount(long channelId, int onlineCount) {
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                state = database.executeFast("UPDATE chat_settings_v2 SET online = ? WHERE uid = ?");
                state.requery();
                state.bindInteger(1, onlineCount);
                state.bindLong(2, channelId);
                state.step();
                state.dispose();
                state = null;
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void updatePinnedMessages(long dialogId, ArrayList<Integer> ids, boolean pin, int totalCount, int maxId, boolean end, HashMap<Integer, MessageObject> messages) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            SQLiteCursor cursor2 = null;
            SQLitePreparedStatement state = null;
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
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM chat_pinned_v2 WHERE uid = %d AND mid IN (%s)", dialogId, TextUtils.join(",", ids)));
                        alreadyAdded = cursor.next() ? cursor.intValue(0) : 0;
                        cursor.dispose();
                        cursor = null;
                    }
                    state = database.executeFast("REPLACE INTO chat_pinned_v2 VALUES(?, ?, ?)");
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
                    state = null;
                    database.commitTransaction();

                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM chat_pinned_v2 WHERE uid = %d", dialogId));
                    int newCount1 = cursor.next() ? cursor.intValue(0) : 0;
                    cursor.dispose();
                    cursor = null;

                    int newCount;
                    if (messages != null) {
                        newCount = Math.max(totalCount, newCount1);
                        endReached = end;
                    } else {
                        cursor2 = database.queryFinalized(String.format(Locale.US, "SELECT count, end FROM chat_pinned_count WHERE uid = %d", dialogId));
                        int newCount2;
                        if (cursor2.next()) {
                            newCount2 = cursor2.intValue(0);
                            endReached = cursor2.intValue(1) != 0;
                        } else {
                            newCount2 = 0;
                            endReached = false;
                        }
                        cursor2.dispose();
                        cursor2 = null;
                        newCount = Math.max(newCount2 + (ids.size() - alreadyAdded), newCount1);
                    }

                    state = database.executeFast("REPLACE INTO chat_pinned_count VALUES(?, ?, ?)");
                    state.requery();
                    state.bindLong(1, dialogId);
                    state.bindInteger(2, newCount);
                    state.bindInteger(3, endReached ? 1 : 0);
                    state.step();
                    state.dispose();
                    state = null;

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

                        cursor = database.queryFinalized("SELECT changes()");
                        int updatedCount = cursor.next() ? cursor.intValue(0) : 0;
                        cursor.dispose();
                        cursor = null;

                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM chat_pinned_v2 WHERE uid = %d", dialogId));
                        int newCount1 = cursor.next() ? cursor.intValue(0) : 0;
                        cursor.dispose();
                        cursor = null;

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
                        cursor = null;
                        newCount = Math.max(newCount1, newCount2);
                    }

                    state = database.executeFast("REPLACE INTO chat_pinned_count VALUES(?, ?, ?)");
                    state.requery();
                    state.bindLong(1, dialogId);
                    state.bindInteger(2, newCount);
                    state.bindInteger(3, endReached ? 1 : 0);
                    state.step();
                    state.dispose();
                    state = null;

                    AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.didLoadPinnedMessages, dialogId, ids, false, null, messages, maxId, newCount, endReached));
                }
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (database != null) {
                    database.commitTransaction();
                }
                if (cursor != null) {
                    cursor.dispose();
                }
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void updateChatInfo(long chatId, long userId, int what, long invited_id, int version) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized("SELECT info, pinned, online, inviter FROM chat_settings_v2 WHERE uid = " + chatId);
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
                cursor = null;
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
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public boolean isMigratedChat(long chatId) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        boolean[] result = new boolean[1];
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized("SELECT info FROM chat_settings_v2 WHERE uid = " + chatId);
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
                cursor = null;
                result[0] = info instanceof TLRPC.TL_channelFull && info.migrated_from_chat_id != 0;
                countDownLatch.countDown();
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
                countDownLatch.countDown();
            }
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            checkSQLException(e);
        }
        return result[0];
    }

    public TLRPC.Message getMessage(long dialogId, long msgId) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicReference<TLRPC.Message> ref = new AtomicReference<>();
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized("SELECT data FROM messages_v2 WHERE uid = " + dialogId + " AND mid = " + msgId + " LIMIT 1");
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                        ref.set(message);
                    }
                }
                cursor.dispose();
                cursor = null;
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
                countDownLatch.countDown();
            }
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            checkSQLException(e);
        }
        return ref.get();
    }

    public boolean hasInviteMeMessage(long chatId) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        boolean[] result = new boolean[1];
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                long selfId = getUserConfig().getClientUserId();
                cursor = database.queryFinalized("SELECT data FROM messages_v2 WHERE uid = " + -chatId + " AND out = 0 ORDER BY mid DESC LIMIT 100");
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
                cursor = null;
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
                countDownLatch.countDown();
            }
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            checkSQLException(e);
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

        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized("SELECT info, pinned, online, inviter, links FROM chat_settings_v2 WHERE uid = " + chatId);
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
            cursor = null;

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
                        checkSQLException(e);
                    }
                }
                cursor.dispose();
                cursor = null;
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
            cursor = null;
            cursor = database.queryFinalized("SELECT count, end FROM chat_pinned_count WHERE uid = " + (-chatId));
            if (cursor.next()) {
                totalPinnedCount = cursor.intValue(0);
                pinnedEndReached = cursor.intValue(1) != 0;
            }
            cursor.dispose();
            cursor = null;

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
            checkSQLException(e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
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
            SQLiteCursor cursor = null;
            SQLitePreparedStatement state = null;
            try {
                int currentMaxId = 0;
                int unreadCount = 0;
                long last_mid = 0;
                int prevUnreadCount = 0;
                cursor = database.queryFinalized("SELECT unread_count, inbox_max, last_mid FROM dialogs WHERE did = " + dialogId);
                if (cursor.next()) {
                    prevUnreadCount = unreadCount = cursor.intValue(0);
                    currentMaxId = cursor.intValue(1);
                    last_mid = cursor.longValue(2);
                }
                cursor.dispose();
                cursor = null;

                database.beginTransaction();

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
                    state = null;
                } else {
                    currentMaxId = maxNegativeId;

                    state = database.executeFast("UPDATE messages_v2 SET read_state = read_state | 1 WHERE uid = ? AND mid >= ? AND read_state IN(0,2) AND out = 0");
                    state.requery();
                    state.bindLong(1, dialogId);
                    state.bindInteger(2, currentMaxId);
                    state.step();
                    state.dispose();
                    state = null;

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
                state = null;

                database.commitTransaction();

                //TODO topics maybe read all topics when all messages read
                if (prevUnreadCount != 0 && unreadCount == 0 && !isForum(dialogId)) {
                    LongSparseIntArray dialogsToUpdate = new LongSparseIntArray();
                    dialogsToUpdate.put(dialogId, unreadCount);
                    updateFiltersReadCounter(dialogsToUpdate, null, true);
                }
                updateWidgets(dialogId);
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
                if (cursor != null) {
                    cursor.dispose();
                }
                if (database != null) {
                    database.commitTransaction();
                }
            }
        });
    }

    public void putContacts(ArrayList<TLRPC.TL_contact> contacts, boolean deleteAll) {
        if (contacts.isEmpty() && !deleteAll) {
            return;
        }
        ArrayList<TLRPC.TL_contact> contactsCopy = new ArrayList<>(contacts);
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                if (deleteAll) {
                    database.executeFast("DELETE FROM contacts WHERE 1").stepThis().dispose();
                }
                database.beginTransaction();
                state = database.executeFast("REPLACE INTO contacts VALUES(?, ?)");
                for (int a = 0; a < contactsCopy.size(); a++) {
                    TLRPC.TL_contact contact = contactsCopy.get(a);
                    state.requery();
                    state.bindLong(1, contact.user_id);
                    state.bindInteger(2, contact.mutual ? 1 : 0);
                    state.step();
                }
                state.dispose();
                state = null;
                database.commitTransaction();
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
                if (database != null) {
                    database.commitTransaction();
                }
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
                checkSQLException(e);
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
                checkSQLException(e);
            }
        });
    }

    public void putCachedPhoneBook(HashMap<String, ContactsController.Contact> contactHashMap, boolean migrate, boolean delete) {
        if (contactHashMap == null || contactHashMap.isEmpty() && !migrate && !delete) {
            return;
        }
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            SQLitePreparedStatement state2 = null;
            try {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d(currentAccount + " save contacts to db " + contactHashMap.size());
                }
                database.executeFast("DELETE FROM user_contacts_v7 WHERE 1").stepThis().dispose();
                database.executeFast("DELETE FROM user_phones_v7 WHERE 1").stepThis().dispose();

                database.beginTransaction();
                state = database.executeFast("REPLACE INTO user_contacts_v7 VALUES(?, ?, ?, ?, ?)");
                state2 = database.executeFast("REPLACE INTO user_phones_v7 VALUES(?, ?, ?, ?)");
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
                state = null;
                state2.dispose();
                state2 = null;
                database.commitTransaction();
                if (migrate) {
                    database.executeFast("DROP TABLE IF EXISTS user_contacts_v6;").stepThis().dispose();
                    database.executeFast("DROP TABLE IF EXISTS user_phones_v6;").stepThis().dispose();
                    getCachedPhoneBook(false);
                }
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
                if (state2 != null) {
                    state2.dispose();
                }
                if (database != null) {
                    database.commitTransaction();
                }
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
                checkSQLException(e);
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
                checkSQLException(e);
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
                checkSQLException(e);
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
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized("SELECT * FROM contacts WHERE 1");
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
                cursor = null;

                if (uids.length() != 0) {
                    getUsersInternal(uids.toString(), users);
                }
            } catch (Exception e) {
                contacts.clear();
                users.clear();
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            getContactsController().processLoadedContacts(contacts, users, 1);
        });
    }

    public void getUnsentMessages(int count) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
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

                cursor = database.queryFinalized("SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.uid, s.seq_in, s.seq_out, m.ttl FROM messages_v2 as m LEFT JOIN randoms_v2 as r ON r.mid = m.mid AND r.uid = m.uid LEFT JOIN messages_seq as s ON m.mid = s.mid WHERE (m.mid < 0 AND m.send_state = 1) OR (m.mid > 0 AND m.send_state = 3) ORDER BY m.mid DESC LIMIT " + count);
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

                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);

                            if (message.send_state != 3 && (message.peer_id.channel_id == 0 && !MessageObject.isUnread(message) && !DialogObject.isEncryptedDialog(message.dialog_id) || message.id > 0)) {
                                message.send_state = 0;
                            }
                        }
                    }
                }
                cursor.dispose();
                cursor = null;

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

                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);

                            if (message.send_state != 3 && (message.peer_id.channel_id == 0 && !MessageObject.isUnread(message) && !DialogObject.isEncryptedDialog(message.dialog_id) || message.id > 0)) {
                                message.send_state = 0;
                            }
                        }
                    }
                }
                cursor.dispose();
                cursor = null;

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
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
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
                checkSQLException(e);
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
            checkSQLException(e);
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
                checkSQLException(e);
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
            checkSQLException(e);
        }
        return result[0];
    }

    public void getUnreadMention(long dialog_id, int topicId, IntCallback callback) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                int result;
                if (topicId != 0) {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT MIN(mid) FROM messages_topics WHERE uid = %d AND topic_id = %d AND mention = 1 AND read_state IN(0, 1)", dialog_id, topicId));
                } else {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT MIN(mid) FROM messages_v2 WHERE uid = %d AND mention = 1 AND read_state IN(0, 1)", dialog_id));
                }
                if (cursor.next()) {
                    result = cursor.intValue(0);
                } else {
                    result = 0;
                }
                cursor.dispose();
                AndroidUtilities.runOnUIThread(() -> callback.run(result));
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public void getMessagesCount(long dialog_id, IntCallback callback) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                int result;
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM messages_v2 WHERE uid = %d", dialog_id));
                if (cursor.next()) {
                    result = cursor.intValue(0);
                } else {
                    result = 0;
                }
                cursor.dispose();
                AndroidUtilities.runOnUIThread(() -> callback.run(result));
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                cursor.dispose();
            }
        });
    }

    public Runnable getMessagesInternal(long dialogId, long mergeDialogId, int count, int max_id, int offset_date, int minDate, int classGuid, int load_type, boolean scheduled, int threadMessageId, int loadIndex, boolean processMessages, boolean isTopic) {
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
        int serviceUnreadCount = 0;
        long startLoadTime = SystemClock.elapsedRealtime();
        SQLiteCursor cursor = null;
        try {
            ArrayList<Long> usersToLoad = new ArrayList<>();
            ArrayList<Long> chatsToLoad = new ArrayList<>();
            ArrayList<Long> animatedEmojiToLoad = new ArrayList<>();
            LongSparseArray<SparseArray<ArrayList<TLRPC.Message>>> replyMessageOwners = new LongSparseArray<>();
            LongSparseArray<ArrayList<Integer>> dialogReplyMessagesIds = new LongSparseArray<>();
            LongSparseArray<ArrayList<TLRPC.Message>> replyMessageRandomOwners = new LongSparseArray<>();
            ArrayList<Long> replyMessageRandomIds = new ArrayList<>();
            String messageSelect;
            if (threadMessageId != 0) {
                messageSelect = "SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention, m.imp, m.forwards, m.replies_data, m.custom_params FROM messages_topics as m LEFT JOIN randoms_v2 as r ON r.mid = m.mid AND r.uid = m.uid";
            } else {
                messageSelect = "SELECT m.read_state, m.data, m.send_state, m.mid, m.date, r.random_id, m.replydata, m.media, m.ttl, m.mention, m.imp, m.forwards, m.replies_data, m.custom_params FROM messages_v2 as m LEFT JOIN randoms_v2 as r ON r.mid = m.mid AND r.uid = m.uid";
            }
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

                        addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, animatedEmojiToLoad);

                        if (message.reply_to != null && (message.reply_to.reply_to_msg_id != 0 || message.reply_to.reply_to_random_id != 0)) {
                            if (!cursor.isNull(5)) {
                                data = cursor.byteBufferValue(5);
                                if (data != null) {
                                    message.replyMessage = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                    message.replyMessage.readAttachPath(data, currentUserId);
                                    data.reuse();
                                    if (message.replyMessage != null) {
                                        addUsersAndChatsFromMessage(message.replyMessage, usersToLoad, chatsToLoad, animatedEmojiToLoad);
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
                cursor = null;
            } else {
                if (!DialogObject.isEncryptedDialog(dialogId)) {
                    if (load_type == 3 && minDate == 0) {
                        if (threadMessageId == 0) {
                            cursor = database.queryFinalized("SELECT inbox_max, unread_count, date, unread_count_i FROM dialogs WHERE did = " + dialogId);
                            if (cursor.next()) {
                                min_unread_id = Math.max(1, cursor.intValue(0)) + 1;
                                count_unread = cursor.intValue(1);
                                max_unread_date = cursor.intValue(2);
                                mentions_unread = cursor.intValue(3);
                            }
                            cursor.dispose();
                            cursor = null;
                        } else {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT unread_count, unread_mentions FROM topics WHERE did = %d AND topic_id = %d", dialogId, threadMessageId));
                            if (cursor.next()) {
                                count_unread = cursor.intValue(0);
                                mentions_unread = cursor.intValue(1);
                            }
                            cursor.dispose();
                            cursor = null;
                        }
                    } else if (load_type != 1 && load_type != 3 && load_type != 4 && minDate == 0) {
                        if (load_type == 2) {
                            if (threadMessageId == 0) {
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
                                cursor = null;
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT max_read_id, unread_count, unread_mentions FROM topics WHERE did = %d AND topic_id = %d", dialogId, threadMessageId));
                                if (cursor.next()) {
                                    messageMaxId = max_id_query = min_unread_id = Math.max(1, cursor.intValue(0));
                                    count_unread = cursor.intValue(1);
                                    mentions_unread = cursor.intValue(2);
                                }
                                cursor.dispose();
                                cursor = null;
                                queryFromServer = true;
                            }
                            if (!queryFromServer) {
                                if (threadMessageId != 0) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid), max(date) FROM messages_topics WHERE uid = %d AND topic_id = %d AND out = 0 AND read_state IN(0,2) AND mid > 0", dialogId, threadMessageId));
                                } else {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid), max(date) FROM messages_v2 WHERE uid = %d AND out = 0 AND read_state IN(0,2) AND mid > 0", dialogId));
                                }
                                if (cursor.next()) {
                                    min_unread_id = cursor.intValue(0);
                                    max_unread_date = cursor.intValue(1);
                                }
                                cursor.dispose();
                                cursor = null;
                                if (min_unread_id != 0) {
                                    if (threadMessageId != 0) {
                                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(*) FROM messages_topics WHERE uid = %d AND topic_id = %d AND mid >= %d AND out = 0 AND read_state IN(0,2)", dialogId, threadMessageId, min_unread_id));
                                    } else {
                                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(*) FROM messages_v2 WHERE uid = %d AND mid >= %d AND out = 0 AND read_state IN(0,2)", dialogId, min_unread_id));
                                    }
                                    if (cursor.next()) {
                                        count_unread = cursor.intValue(0);
                                    }
                                    cursor.dispose();
                                    cursor = null;
                                }
                            } else if (max_id_query == 0) {
                                int existingUnreadCount = 0;
                                if (threadMessageId != 0) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(*) FROM messages_topics WHERE uid = %d AND topic_id = %d AND mid > 0 AND out = 0 AND read_state IN(0,2)", dialogId, threadMessageId));
                                } else {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(*) FROM messages_v2 WHERE uid = %d AND mid > 0 AND out = 0 AND read_state IN(0,2)", dialogId));
                                }
                                if (cursor.next()) {
                                    existingUnreadCount = cursor.intValue(0);
                                }
                                cursor.dispose();
                                cursor = null;
                                if (existingUnreadCount == count_unread) {
                                    if (threadMessageId != 0) {
                                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages_topics WHERE uid = %d AND topic_id = %d AND out = 0 AND read_state IN(0,2) AND mid > 0", dialogId, threadMessageId));
                                    } else {
                                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages_v2 WHERE uid = %d AND out = 0 AND read_state IN(0,2) AND mid > 0", dialogId));
                                    }
                                    if (cursor.next()) {
                                        messageMaxId = max_id_query = min_unread_id = cursor.intValue(0);
                                    }
                                    cursor.dispose();
                                    cursor = null;
                                }
                            } else {
                                if (threadMessageId != 0) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT start, end FROM messages_holes_topics WHERE uid = %d AND topic_id = %d AND start < %d AND end > %d", dialogId, threadMessageId, max_id_query, max_id_query));
                                } else {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT start, end FROM messages_holes WHERE uid = %d AND start < %d AND end > %d", dialogId, max_id_query, max_id_query));
                                }
                                boolean containMessage = !cursor.next();
                                cursor.dispose();
                                cursor = null;

                                if (containMessage) {
                                    if (threadMessageId != 0) {
                                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages_topics WHERE uid = %d AND topic_id = %d AND out = 0 AND read_state IN(0,2) AND mid > %d", dialogId, threadMessageId, max_id_query));
                                    } else {
                                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages_v2 WHERE uid = %d AND out = 0 AND read_state IN(0,2) AND mid > %d", dialogId, max_id_query));
                                    }
                                    if (cursor.next()) {
                                        messageMaxId = max_id_query = cursor.intValue(0);
                                    }
                                    cursor.dispose();
                                    cursor = null;
                                }
                            }
                        }

                        if (count_query > count_unread || count_unread < num) {
                            count_query = Math.max(count_query, count_unread + 10);
                            if (count_unread < num) {
                                serviceUnreadCount = count_unread;
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

                    if (threadMessageId != 0) {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM messages_holes_topics WHERE uid = %d AND topic_id = %d AND start IN (0, 1)", dialogId, threadMessageId));
                    } else {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM messages_holes WHERE uid = %d AND start IN (0, 1)", dialogId));
                    }

                    if (cursor.next()) {
                        isEnd = cursor.intValue(0) == 1;
                    } else {
                        cursor.dispose();
                        cursor = null;
                        if (threadMessageId != 0) {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages_topics WHERE uid = %d AND topic_id = %d AND mid > 0", dialogId, threadMessageId));
                        } else {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages_v2 WHERE uid = %d AND mid > 0", dialogId));
                        }
                        if (cursor.next()) {
                            int mid = cursor.intValue(0);
                            if (mid != 0) {
                                SQLitePreparedStatement state;
                                if (threadMessageId != 0) {
                                    state = database.executeFast("REPLACE INTO messages_holes_topics VALUES(?, ?, ?, ?)");
                                } else {
                                    state = database.executeFast("REPLACE INTO messages_holes VALUES(?, ?, ?)");
                                }
                                int pointer = 1;
                                state.requery();
                                state.bindLong(pointer++, dialogId);
                                if (threadMessageId != 0) {
                                    state.bindInteger(pointer++, threadMessageId);
                                }
                                state.bindInteger(pointer++, 0);
                                state.bindInteger(pointer++, mid);
                                state.step();
                                state.dispose();
                            }
                        }
                    }
                    cursor.dispose();
                    cursor = null;

                    if (load_type == 3 || load_type == 4 || queryFromServer && load_type == 2) {
                        if (threadMessageId != 0) {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(mid) FROM messages_topics WHERE uid = %d AND topic_id = %d AND mid > 0", dialogId, threadMessageId));
                        } else {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(mid) FROM messages_v2 WHERE uid = %d AND mid > 0", dialogId));
                        }
                        if (cursor.next()) {
                            last_message_id = cursor.intValue(0);
                        }
                        cursor.dispose();
                        cursor = null;

                        if (load_type == 4 && offset_date != 0) {
                            int startMid;
                            int endMid;

                            if (threadMessageId != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(mid) FROM messages_topics WHERE uid = %d AND topic_id = %d AND date <= %d AND mid > 0", dialogId, threadMessageId, offset_date));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(mid) FROM messages_v2 WHERE uid = %d AND date <= %d AND mid > 0", dialogId, offset_date));
                            }
                            if (cursor.next()) {
                                startMid = cursor.intValue(0);
                            } else {
                                startMid = -1;
                            }
                            cursor.dispose();
                            cursor = null;
                            if (threadMessageId != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages_topics WHERE uid = %d AND topic_id = %d AND date >= %d AND mid > 0", dialogId, threadMessageId, offset_date));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages_v2 WHERE uid = %d AND date >= %d AND mid > 0", dialogId, offset_date));
                            }
                            if (cursor.next()) {
                                endMid = cursor.intValue(0);
                            } else {
                                endMid = -1;
                            }
                            cursor.dispose();
                            cursor = null;
                            if (startMid != -1 && endMid != -1) {
                                if (startMid == endMid) {
                                    max_id_query = startMid;
                                } else {
                                    if (threadMessageId != 0) {
                                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM messages_holes_topics WHERE uid = %d AND topic_id = %d AND start <= %d AND end > %d", dialogId, threadMessageId, startMid, startMid));
                                    } else {
                                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM messages_holes WHERE uid = %d AND start <= %d AND end > %d", dialogId, startMid, startMid));
                                    }
                                    if (cursor.next()) {
                                        startMid = -1;
                                    }
                                    cursor.dispose();
                                    cursor = null;
                                    if (startMid != -1) {
                                        if (threadMessageId != 0) {
                                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM messages_holes_topics WHERE uid = %d AND topic_id = %d AND start <= %d AND end > %d", dialogId, threadMessageId, endMid, endMid));
                                        } else {
                                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM messages_holes WHERE uid = %d AND start <= %d AND end > %d", dialogId, endMid, endMid));
                                        }
                                        if (cursor.next()) {
                                            endMid = -1;
                                        }
                                        cursor.dispose();
                                        cursor = null;
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
                            if (threadMessageId != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM messages_holes_topics WHERE uid = %d AND topic_id = %d AND start < %d AND end > %d", dialogId, threadMessageId, max_id_query, max_id_query));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM messages_holes WHERE uid = %d AND start < %d AND end > %d", dialogId, max_id_query, max_id_query));
                            }
                            if (cursor.next()) {
                                containMessage = false;
                            }

                            cursor.dispose();
                            cursor = null;
                        }

                        if (containMessage) {
                            int holeMessageMaxId = 0;
                            int holeMessageMinId = 1;
                            if (threadMessageId != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM messages_holes_topics WHERE uid = %d AND topic_id = %d AND start >= %d ORDER BY start ASC LIMIT 1", dialogId, threadMessageId, max_id_query));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM messages_holes WHERE uid = %d AND start >= %d ORDER BY start ASC LIMIT 1", dialogId, max_id_query));
                            }
                            if (cursor.next()) {
                                holeMessageMaxId = cursor.intValue(0);
                            }
                            cursor.dispose();
                            cursor = null;
                            if (threadMessageId != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT end FROM messages_holes_topics WHERE uid = %d AND topic_id = %d AND end <= %d ORDER BY end DESC LIMIT 1", dialogId, threadMessageId, max_id_query));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT end FROM messages_holes WHERE uid = %d AND end <= %d ORDER BY end DESC LIMIT 1", dialogId, max_id_query));
                            }
                            if (cursor.next()) {
                                holeMessageMinId = cursor.intValue(0);
                            }
                            cursor.dispose();
                            cursor = null;
                            if (holeMessageMaxId != 0 || holeMessageMinId != 1) {
                                if (holeMessageMaxId == 0) {
                                    holeMessageMaxId = 1000000000;
                                }
                                if (threadMessageId != 0) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT * FROM (" + messageSelect + " WHERE m.uid = %d AND m.topic_id = %d AND m.mid <= %d AND (m.mid >= %d OR m.mid < 0) ORDER BY m.date DESC, m.mid DESC LIMIT %d) UNION " +
                                            "SELECT * FROM (" + messageSelect + " WHERE m.uid = %d AND m.topic_id = %d AND m.mid > %d AND (m.mid <= %d OR m.mid < 0) ORDER BY m.date ASC, m.mid ASC LIMIT %d)", dialogId, threadMessageId, messageMaxId, holeMessageMinId, count_query / 2, dialogId, threadMessageId, messageMaxId, holeMessageMaxId, count_query / 2));
                                } else {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT * FROM (" + messageSelect + " WHERE m.uid = %d AND m.mid <= %d AND (m.mid >= %d OR m.mid < 0) ORDER BY m.date DESC, m.mid DESC LIMIT %d) UNION " +
                                            "SELECT * FROM (" + messageSelect + " WHERE m.uid = %d AND m.mid > %d AND (m.mid <= %d OR m.mid < 0) ORDER BY m.date ASC, m.mid ASC LIMIT %d)", dialogId, messageMaxId, holeMessageMinId, count_query / 2, dialogId, messageMaxId, holeMessageMaxId, count_query / 2));
                                }
                            } else {
                                if (threadMessageId != 0) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT * FROM (" + messageSelect + " WHERE m.uid = %d AND m.topic_id = %d AND m.mid <= %d ORDER BY m.date DESC, m.mid DESC LIMIT %d) UNION " +
                                            "SELECT * FROM (" + messageSelect + " WHERE m.uid = %d AND m.topic_id = %d AND m.mid > %d ORDER BY m.date ASC, m.mid ASC LIMIT %d)", dialogId, threadMessageId, messageMaxId, count_query / 2, dialogId, threadMessageId, messageMaxId, count_query / 2));
                                } else {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT * FROM (" + messageSelect + " WHERE m.uid = %d AND m.mid <= %d ORDER BY m.date DESC, m.mid DESC LIMIT %d) UNION " +
                                            "SELECT * FROM (" + messageSelect + " WHERE m.uid = %d AND m.mid > %d ORDER BY m.date ASC, m.mid ASC LIMIT %d)", dialogId, messageMaxId, count_query / 2, dialogId, messageMaxId, count_query / 2));
                                }
                            }
                        } else {
                            if (load_type == 2) {
                                int existingUnreadCount = 0;
                                if (threadMessageId != 0) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(*) FROM messages_topics WHERE uid = %d AND topic_id = %d AND mid != 0 AND out = 0 AND read_state IN(0,2)", dialogId, threadMessageId));
                                } else {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(*) FROM messages_v2 WHERE uid = %d AND mid != 0 AND out = 0 AND read_state IN(0,2)", dialogId));
                                }
                                if (cursor.next()) {
                                    existingUnreadCount = cursor.intValue(0);
                                }
                                cursor.dispose();
                                cursor = null;
                                if (existingUnreadCount == count_unread) {
                                    unreadCountIsLocal = true;
                                    if (threadMessageId != 0) {
                                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT * FROM (" + messageSelect + " WHERE m.uid = %d AND m.topic_id = %d AND m.mid <= %d ORDER BY m.date DESC, m.mid DESC LIMIT %d) UNION " +
                                                "SELECT * FROM (" + messageSelect + " WHERE m.uid = %d AND m.topic_id = %d AND m.mid > %d ORDER BY m.date ASC, m.mid ASC LIMIT %d)", dialogId, threadMessageId, messageMaxId, count_query / 2, dialogId, threadMessageId, messageMaxId, count_query / 2));
                                    } else {
                                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT * FROM (" + messageSelect + " WHERE m.uid = %d AND m.mid <= %d ORDER BY m.date DESC, m.mid DESC LIMIT %d) UNION " +
                                                "SELECT * FROM (" + messageSelect + " WHERE m.uid = %d AND m.mid > %d ORDER BY m.date ASC, m.mid ASC LIMIT %d)", dialogId, messageMaxId, count_query / 2, dialogId, messageMaxId, count_query / 2));
                                    }
                                }
                            }
                        }
                    } else if (load_type == 1) {
                        int holeMessageId = 0;
                        if (threadMessageId != 0) {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT start, end FROM messages_holes_topics WHERE uid = %d AND topic_id = %d AND (start >= %d AND start != 1 AND end != 1 OR start < %d AND end > %d) ORDER BY start ASC LIMIT 1", dialogId, threadMessageId, max_id, max_id, max_id));
                        } else {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT start, end FROM messages_holes WHERE uid = %d AND (start >= %d AND start != 1 AND end != 1 OR start < %d AND end > %d) ORDER BY start ASC LIMIT 1", dialogId, max_id, max_id, max_id));
                        }
                        if (cursor.next()) {
                            holeMessageId = cursor.intValue(0);
                        }
                        cursor.dispose();
                        cursor = null;
                        if (threadMessageId != 0) {
                            if (holeMessageId != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND m.topic_id = %d AND m.date >= %d AND m.mid > %d AND m.mid <= %d ORDER BY m.date ASC, m.mid ASC LIMIT %d", dialogId, threadMessageId, minDate, messageMaxId, holeMessageId, count_query));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND m.topic_id = %d AND m.date >= %d AND m.mid > %d ORDER BY m.date ASC, m.mid ASC LIMIT %d", dialogId, threadMessageId, minDate, messageMaxId, count_query));
                            }
                        } else {
                            if (holeMessageId != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND m.date >= %d AND m.mid > %d AND m.mid <= %d ORDER BY m.date ASC, m.mid ASC LIMIT %d", dialogId, minDate, messageMaxId, holeMessageId, count_query));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND m.date >= %d AND m.mid > %d ORDER BY m.date ASC, m.mid ASC LIMIT %d", dialogId, minDate, messageMaxId, count_query));
                            }
                        }
                    } else if (minDate != 0) {
                        if (messageMaxId != 0) {
                            int holeMessageId = 0;
                            if (threadMessageId != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT end FROM messages_holes_topics WHERE uid = %d AND topic_id = %d AND end <= %d ORDER BY end DESC LIMIT 1", dialogId, threadMessageId, max_id));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT end FROM messages_holes WHERE uid = %d AND end <= %d ORDER BY end DESC LIMIT 1", dialogId, max_id));
                            }

                            if (cursor.next()) {
                                holeMessageId = cursor.intValue(0);
                            }
                            cursor.dispose();
                            cursor = null;
                            if (threadMessageId != 0) {
                                if (holeMessageId != 0) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND m.topic_id = %d AND m.date <= %d AND m.mid < %d AND (m.mid >= %d OR m.mid < 0) ORDER BY m.date DESC, m.mid DESC LIMIT %d", dialogId, threadMessageId, minDate, messageMaxId, holeMessageId, count_query));
                                } else {
                                    cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND m.topic_id = %d AND m.date <= %d AND m.mid < %d ORDER BY m.date DESC, m.mid DESC LIMIT %d", dialogId, threadMessageId, minDate, messageMaxId, count_query));
                                }
                            } else {
                                if (holeMessageId != 0) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND m.date <= %d AND m.mid < %d AND (m.mid >= %d OR m.mid < 0) ORDER BY m.date DESC, m.mid DESC LIMIT %d", dialogId, minDate, messageMaxId, holeMessageId, count_query));
                                } else {
                                    cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND m.date <= %d AND m.mid < %d ORDER BY m.date DESC, m.mid DESC LIMIT %d", dialogId, minDate, messageMaxId, count_query));
                                }
                            }
                        } else {
                            if (threadMessageId != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND m.topic_id = %d AND m.date <= %d ORDER BY m.date DESC, m.mid DESC LIMIT %d,%d", dialogId, threadMessageId, minDate, offset_query, count_query));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND m.date <= %d ORDER BY m.date DESC, m.mid DESC LIMIT %d,%d", dialogId, minDate, offset_query, count_query));
                            }
                        }
                    } else {
                        if (threadMessageId != 0) {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(mid) FROM messages_topics WHERE uid = %d AND topic_id = %d AND mid > 0", dialogId, threadMessageId));
                        } else {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(mid) FROM messages_v2 WHERE uid = %d AND mid > 0", dialogId));
                        }
                        if (cursor.next()) {
                            last_message_id = cursor.intValue(0);
                        }
                        cursor.dispose();
                        cursor = null;

                        int holeMessageId = 0;
                        if (threadMessageId != 0) {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(end) FROM messages_holes_topics WHERE uid = %d AND topic_id = %d", dialogId, threadMessageId));
                        } else {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(end) FROM messages_holes WHERE uid = %d", dialogId));
                        }
                        if (cursor.next()) {
                            holeMessageId = cursor.intValue(0);
                        }
                        cursor.dispose();
                        cursor = null;
                        if (threadMessageId != 0) {
                            if (holeMessageId != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND m.topic_id = %d AND (m.mid >= %d OR m.mid < 0) ORDER BY m.date DESC, m.mid DESC LIMIT %d,%d", dialogId, threadMessageId, holeMessageId, offset_query, count_query));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND m.topic_id = %d ORDER BY m.date DESC, m.mid DESC LIMIT %d,%d", dialogId, threadMessageId, offset_query, count_query));
                            }
                        } else {
                            if (holeMessageId != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d AND (m.mid >= %d OR m.mid < 0) ORDER BY m.date DESC, m.mid DESC LIMIT %d,%d", dialogId, holeMessageId, offset_query, count_query));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "" + messageSelect + " WHERE m.uid = %d ORDER BY m.date DESC, m.mid DESC LIMIT %d,%d", dialogId, offset_query, count_query));
                            }
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
                        cursor = null;

                        int min_unread_id2 = 0;
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(mid), max(date) FROM messages_v2 WHERE uid = %d AND out = 0 AND read_state IN(0,2) AND mid < 0", dialogId));
                        if (cursor.next()) {
                            min_unread_id2 = cursor.intValue(0);
                            max_unread_date = cursor.intValue(1);
                        }
                        cursor.dispose();
                        cursor = null;
                        if (min_unread_id2 != 0) {
                            min_unread_id = min_unread_id2;
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(*) FROM messages_v2 WHERE uid = %d AND mid <= %d AND out = 0 AND read_state IN(0,2)", dialogId, min_unread_id2));
                            if (cursor.next()) {
                                count_unread = cursor.intValue(0);
                            }
                            cursor.dispose();
                            cursor = null;
                        }
                    }

                    if (load_type == 3 || load_type == 4) {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM messages_v2 WHERE uid = %d AND mid < 0", dialogId));
                        if (cursor.next()) {
                            last_message_id = cursor.intValue(0);
                        }
                        cursor.dispose();
                        cursor = null;

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
                            cursor = null;

                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(mid), max(date) FROM messages_v2 WHERE uid = %d AND out = 0 AND read_state IN(0,2) AND mid < 0", dialogId));
                            if (cursor.next()) {
                                min_unread_id = cursor.intValue(0);
                                max_unread_date = cursor.intValue(1);
                            }
                            cursor.dispose();
                            cursor = null;
                            if (min_unread_id != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(*) FROM messages_v2 WHERE uid = %d AND mid <= %d AND out = 0 AND read_state IN(0,2)", dialogId, min_unread_id));
                                if (cursor.next()) {
                                    count_unread = cursor.intValue(0);
                                }
                                cursor.dispose();
                                cursor = null;
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
                            NativeByteBuffer customParams = cursor.byteBufferValue(13);
                            if (customParams != null) {
                                MessageCustomParamsHelper.readLocalParams(message, customParams);
                                customParams.reuse();
                            }
                            res.messages.add(message);

                            addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, animatedEmojiToLoad);

                            if (message.reply_to != null && (message.reply_to.reply_to_msg_id != 0 || message.reply_to.reply_to_random_id != 0)) {
                                if (!cursor.isNull(6)) {
                                    data = cursor.byteBufferValue(6);
                                    if (data != null) {
                                        message.replyMessage = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                        message.replyMessage.readAttachPath(data, currentUserId);
                                        data.reuse();
                                        if (message.replyMessage != null) {
                                            addUsersAndChatsFromMessage(message.replyMessage, usersToLoad, chatsToLoad, animatedEmojiToLoad);
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
                                    checkSQLException(e);
                                }
                            }
                        }
                    }
                    cursor.dispose();
                    cursor = null;
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
                    if (threadMessageId == 0) {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM messages_v2 WHERE uid = %d AND mention = 1 AND read_state IN(0, 1)", dialogId));
                        if (cursor.next()) {
                            if (mentions_unread != cursor.intValue(0)) {
                                mentions_unread *= -1;
                            }
                        } else {
                            mentions_unread *= -1;
                        }
                        cursor.dispose();
                        cursor = null;
                    } else {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM messages_topics WHERE uid = %d AND topic_id = %d AND mention = 1 AND read_state IN(0, 1)", dialogId, threadMessageId));
                        if (cursor.next()) {
                            if (mentions_unread != cursor.intValue(0)) {
                                mentions_unread *= -1;
                            }
                        } else {
                            mentions_unread *= -1;
                        }
                        cursor.dispose();
                        cursor = null;
                    }
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

                        addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, animatedEmojiToLoad);

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
                cursor = null;
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
                loadReplyMessages(replyMessageOwners, dialogReplyMessagesIds, usersToLoad, chatsToLoad, scheduled);
            }
            if (!usersToLoad.isEmpty()) {
                getUsersInternal(TextUtils.join(",", usersToLoad), res.users);
            }
            if (!chatsToLoad.isEmpty()) {
                getChatsInternal(TextUtils.join(",", chatsToLoad), res.chats);
            }
            if (!animatedEmojiToLoad.isEmpty()) {
                res.animatedEmoji = new ArrayList<>();
                getAnimatedEmoji(TextUtils.join(",", animatedEmojiToLoad), res.animatedEmoji);
            }
        } catch (Exception e) {
            res.messages.clear();
            res.chats.clear();
            res.users.clear();
            res.animatedEmoji = null;
            checkSQLException(e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("messages load time = " + (SystemClock.elapsedRealtime() - startLoadTime) + " for dialog = " + dialogId);
        }
        if (dialogId == 777000 && serviceUnreadCount != 0) {
            count_unread = serviceUnreadCount;
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
        return () -> getMessagesController().processLoadedMessages(res, finalMessagesCount, dialogId, mergeDialogId, countQueryFinal, maxIdOverrideFinal, offset_date, true, classGuid, minUnreadIdFinal, lastMessageIdFinal, countUnreadFinal, maxUnreadDateFinal, load_type, isEndFinal, scheduled ? 1 : 0, threadMessageId, loadIndex, queryFromServerFinal, mentionsUnreadFinal, processMessages, isTopic);
        //}
    }

    private void getAnimatedEmoji(String join, ArrayList<TLRPC.Document> documents) {
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM animated_emoji WHERE document_id IN (%s)", join));
            while (cursor.next()) {
                NativeByteBuffer byteBuffer = cursor.byteBufferValue(0);
                try {
                    TLRPC.Document document = TLRPC.Document.TLdeserialize(byteBuffer, byteBuffer.readInt32(true), true);
                    if (document != null && document.id != 0) {
                        documents.add(document);
                    }
                } catch (Exception e) {
                    checkSQLException(e);
                }
                if (byteBuffer != null) {
                    byteBuffer.reuse();
                }
            }
        } catch (SQLiteException e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    public void getMessages(long dialogId, long mergeDialogId, boolean loadInfo, int count, int max_id, int offset_date, int minDate, int classGuid, int load_type, boolean scheduled, int replyMessageId, int loadIndex, boolean processMessages, boolean isTopic) {
        storageQueue.postRunnable(() -> {
            Runnable processMessagesRunnable = getMessagesInternal(dialogId, mergeDialogId, count, max_id, offset_date, minDate, classGuid, load_type, scheduled, replyMessageId, loadIndex, processMessages, isTopic);
            Utilities.stageQueue.postRunnable(() -> {
                processMessagesRunnable.run();
            });
        });
    }

    public void clearSentMedia() {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM sent_files_v2 WHERE 1").stepThis().dispose();
            } catch (Exception e) {
                checkSQLException(e);
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
                checkSQLException(e);
            } finally {
                countDownLatch.countDown();
            }
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            checkSQLException(e);
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
            checkSQLException(e);
        }
    }

    public void putWidgetDialogs(int widgetId, ArrayList<TopicKey> dids) {
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
                        long did = dids.get(a).dialogId;
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
                checkSQLException(e);
            }
        });
    }

    public void clearWidgetDialogs(int widgetId) {
        storageQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM shortcut_widget WHERE id = " + widgetId).stepThis().dispose();
            } catch (Exception e) {
                checkSQLException(e);
            }
        });
    }

    public void getWidgetDialogIds(int widgetId, int type, ArrayList<Long> dids, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats, boolean edit) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                ArrayList<Long> usersToLoad = new ArrayList<>();
                ArrayList<Long> chatsToLoad = new ArrayList<>();
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT did FROM shortcut_widget WHERE id = %d ORDER BY ord ASC", widgetId));
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
                cursor = null;
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
                        cursor = null;
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
                        cursor = null;
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
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
                countDownLatch.countDown();
            }
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            checkSQLException(e);
        }
    }

    public void getWidgetDialogs(int widgetId, int type, ArrayList<Long> dids, LongSparseArray<TLRPC.Dialog> dialogs, LongSparseArray<TLRPC.Message> messages, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                boolean add = false;
                ArrayList<Long> usersToLoad = new ArrayList<>();
                ArrayList<Long> chatsToLoad = new ArrayList<>();
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT did FROM shortcut_widget WHERE id = %d ORDER BY ord ASC", widgetId));
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
                cursor = null;
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
                    cursor = null;
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
                        addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);
                    }
                }
                cursor.dispose();
                cursor = null;
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
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
                countDownLatch.countDown();
            }
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            checkSQLException(e);
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
                checkSQLException(e);
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
                checkSQLException(e);
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
                checkSQLException(e);
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
                checkSQLException(e);
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
                checkSQLException(e);
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
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT last_mid FROM dialogs WHERE did = %d", did));
                if (cursor.next()) {
                    exists = cursor.intValue(0) != 0;
                }

            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
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
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid FROM messages_v2 WHERE uid = 777000 AND date = %d AND mid < 0 LIMIT 1", date));
                result[0] = cursor.next();
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
                countDownLatch.countDown();
            }
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            checkSQLException(e);
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
                checkSQLException(e);
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
            SQLitePreparedStatement state = null;
            try {
                if ((chat.key_hash == null || chat.key_hash.length < 16) && chat.auth_key != null) {
                    chat.key_hash = AndroidUtilities.calcAuthKeyHash(chat.auth_key);
                }
                state = database.executeFast("REPLACE INTO enc_chats VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
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
                state = null;
                data.reuse();
                data2.reuse();
                data3.reuse();
                data4.reuse();
                data5.reuse();
                if (dialog != null) {
                    state = database.executeFast("REPLACE INTO dialogs VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
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
                    state.bindInteger(16, 0);
                    state.bindInteger(17, dialog.ttl_period);
                    state.step();
                    state.dispose();
                    state = null;
                }
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
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
        } else if (user.usernames != null && user.usernames.size() > 0) {
            for (int i = 0; i < user.usernames.size(); ++i) {
                TLRPC.TL_username u = user.usernames.get(i);
                if (u != null && u.active) {
                    str.append(u.username).append(";;");
                }
            }
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
            if (user != null && user.min) {
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
                        checkSQLException(e);
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
            SQLiteCursor cursor = null;
            SQLitePreparedStatement state = null;
            try {
                TLRPC.Chat chat = null;
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM chats WHERE uid = %d", chatId));
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        chat = TLRPC.Chat.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                    }
                }
                cursor.dispose();
                cursor = null;
                if (chat == null || chat.default_banned_rights != null && version < chat.version) {
                    return;
                }
                chat.default_banned_rights = rights;
                chat.flags |= 262144;
                chat.version = version;

                state = database.executeFast("UPDATE chats SET data = ? WHERE uid = ?");
                NativeByteBuffer data = new NativeByteBuffer(chat.getObjectSize());
                chat.serializeToStream(data);
                state.bindByteBuffer(1, data);
                state.bindLong(2, chat.id);
                state.step();
                data.reuse();
                state.dispose();
                state = null;
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
                if (state != null) {
                    state.dispose();
                }
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
            dialogIsForum.put(-chat.id, chat.forum ? 1 : 0);
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
                checkSQLException(e);
            }
        }
        cursor.dispose();
    }

    public void getChatsInternal(String chatsToLoad, ArrayList<TLRPC.Chat> result) throws Exception {
        getChatsInternal(chatsToLoad, result, true);
    }

    public void getChatsInternal(String chatsToLoad, ArrayList<TLRPC.Chat> result, boolean parseFullData) throws Exception {
        if (chatsToLoad == null || chatsToLoad.length() == 0 || result == null) {
            return;
        }
        SQLiteCursor cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM chats WHERE uid IN(%s)", chatsToLoad));
        while (cursor.next()) {
            try {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null) {
                    TLRPC.Chat chat = TLRPC.Chat.TLdeserialize(data, data.readInt32(false), false, parseFullData);
                    data.reuse();
                    if (chat != null) {
                        result.add(chat);
                    }
                }
            } catch (Exception e) {
                checkSQLException(e);
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
                checkSQLException(e);
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
        } catch (Exception e) {
            checkSQLException(e);
        } finally {
            if (database != null) {
                database.commitTransaction();
            }
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
            SQLiteCursor cursor = null;
            try {
                if (move) {
                    int minDate = -1;
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(date) FROM download_queue WHERE type = %d", type));
                    if (cursor.next()) {
                        minDate = cursor.intValue(0);
                    }
                    cursor.dispose();
                    cursor = null;
                    if (minDate != -1) {
                        database.executeFast(String.format(Locale.US, "UPDATE download_queue SET date = %d WHERE uid = %d AND type = %d", minDate - 1, id, type)).stepThis().dispose();
                    }
                } else {
                    database.executeFast(String.format(Locale.US, "DELETE FROM download_queue WHERE uid = %d AND type = %d", id, type)).stepThis().dispose();
                }
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    private void deleteFromDownloadQueue(ArrayList<Pair<Long, Integer>> ids, boolean transaction) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        SQLitePreparedStatement state = null;
        try {
            if (transaction) {
                database.beginTransaction();
            }
            state = database.executeFast("DELETE FROM download_queue WHERE uid = ? AND type = ?");
            for (int a = 0, N = ids.size(); a < N; a++) {
                Pair<Long, Integer> pair = ids.get(a);
                state.requery();
                state.bindLong(1, pair.first);
                state.bindInteger(2, pair.second);
                state.step();
            }
            state.dispose();
            state = null;
            if (transaction) {
                database.commitTransaction();
            }
            AndroidUtilities.runOnUIThread(() -> getDownloadController().cancelDownloading(ids));
        } catch (Exception e) {
            checkSQLException(e);
        } finally {
            if (state != null) {
                state.dispose();
            }
            if (transaction) {
                if (database != null) {
                    database.commitTransaction();
                }
            }
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
                checkSQLException(e);
            }
        });
    }

    public void getDownloadQueue(int type) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                ArrayList<DownloadObject> objects = new ArrayList<>();
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, type, data, parent FROM download_queue WHERE type = %d ORDER BY date DESC LIMIT 3", type));
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
                cursor = null;

                AndroidUtilities.runOnUIThread(() -> getDownloadController().processDownloadObjects(type, objects));
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
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
            SQLiteCursor cursor = null;
            SQLitePreparedStatement state = null;
            SQLitePreparedStatement state2 = null;
            try {
                ArrayList<TLRPC.Message> messages = new ArrayList<>();
                for (int a = 0, N = webPages.size(); a < N; a++) {
                    cursor = database.queryFinalized("SELECT mid, uid FROM webpage_pending_v2 WHERE id = " + webPages.keyAt(a));
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
                    cursor = null;

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
                        cursor = null;
                    }
                }

                if (messages.isEmpty()) {
                    return;
                }

                database.beginTransaction();

                state = database.executeFast("UPDATE messages_v2 SET data = ? WHERE mid = ? AND uid = ?");
                state2 = database.executeFast("UPDATE media_v4 SET data = ? WHERE mid = ? AND uid = ?");
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
                state = null;
                state2.dispose();
                state2 = null;

                database.commitTransaction();

                AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.didReceivedWebpages, messages));
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
                if (state != null) {
                    state.dispose();
                }
                if (state2 != null) {
                    state2.dispose();
                }
                if (database != null) {
                    database.commitTransaction();
                }
            }
        });
    }

    public void overwriteChannel(long channelId, TLRPC.TL_updates_channelDifferenceTooLong difference, int newDialogType) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                boolean checkInvite = false;
                long did = -channelId;
                int pinned = 0;

                cursor = database.queryFinalized("SELECT pinned FROM dialogs WHERE did = " + did);
                if (!cursor.next()) {
                    if (newDialogType != 0) {
                        checkInvite = true;
                    }
                } else {
                    pinned = cursor.intValue(0);
                }
                cursor.dispose();
                cursor = null;

                database.executeFast("DELETE FROM chat_pinned_count WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM chat_pinned_v2 WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM messages_v2 WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM bot_keyboard WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM bot_keyboard_topics WHERE uid = " + did).stepThis().dispose();
                database.executeFast("UPDATE media_counts_v2 SET old = 1 WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM media_v4 WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM messages_holes WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM media_holes_v2 WHERE uid = " + did).stepThis().dispose();

                database.executeFast("DELETE FROM topics WHERE did = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM media_topics WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM media_holes_topics WHERE uid = " + did).stepThis().dispose();
                database.executeFast("UPDATE media_counts_topics SET old = 1 WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM messages_topics WHERE uid = " + did).stepThis().dispose();
                database.executeFast("DELETE FROM messages_holes_topics WHERE uid = " + did).stepThis().dispose();

                getMediaDataController().clearBotKeyboard(did);

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
                if (newDialogType != 1) {
                    getMessagesController().getTopicsController().reloadTopics(channelId);
                }
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public void putChannelViews(LongSparseArray<SparseIntArray> channelViews, LongSparseArray<SparseIntArray> channelForwards, LongSparseArray<SparseArray<TLRPC.MessageReplies>> channelReplies, boolean addReply) {
        if (isEmpty(channelViews) && isEmpty(channelForwards) && isEmpty(channelReplies)) {
            return;
        }
        storageQueue.postRunnable(() -> {
            boolean inTransaction = false;
            SQLitePreparedStatement state = null;
            try {
                database.beginTransaction();
                inTransaction = true;
                if (!isEmpty(channelViews)) {
                    state = database.executeFast("UPDATE messages_v2 SET media = max((SELECT media FROM messages_v2 WHERE mid = ? AND uid = ?), ?) WHERE mid = ? AND uid = ?");
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
                    state = null;
                }

                if (!isEmpty(channelForwards)) {
                    state = database.executeFast("UPDATE messages_v2 SET forwards = max((SELECT forwards FROM messages_v2 WHERE mid = ? AND uid = ?), ?) WHERE mid = ? AND uid = ?");
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
                    state = null;
                }

                if (!isEmpty(channelReplies)) {
                    state = database.executeFast("UPDATE messages_v2 SET replies_data = ? WHERE mid = ? AND uid = ?");
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
                    state = null;
                }
                database.commitTransaction();
                inTransaction = false;
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (inTransaction) {
                    if (database != null) {
                        database.commitTransaction();
                    }
                }
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    private void updateRepliesMaxReadIdInternal(long chatId, int mid, int readMaxId, int unreadCount) {
        SQLitePreparedStatement state = null;
        SQLiteCursor cursor = null;
        try {
            long dialogId = -chatId;
            if (!isForum(-chatId)) {
                state = database.executeFast("UPDATE messages_v2 SET replies_data = ? WHERE mid = ? AND uid = ?");
                TLRPC.MessageReplies currentReplies = null;
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT replies_data FROM messages_v2 WHERE mid = %d AND uid = %d", mid, dialogId));
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        currentReplies = TLRPC.MessageReplies.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                    }
                }
                cursor.dispose();

                cursor = null;
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
                state = null;
            }


            cursor = database.queryFinalized(String.format(Locale.US, "SELECT max_read_id FROM topics WHERE did = %d AND topic_id = %d", -chatId, mid));
            boolean updateTopic = false;
            if (cursor.next()) {
                int currentMaxId = cursor.intValue(0);
                if (readMaxId >= currentMaxId) {
                    updateTopic = true;
                }
            }
            cursor.dispose();
            cursor = null;

            database.executeFast(String.format(Locale.US, "UPDATE messages_topics SET read_state = read_state | 1 WHERE uid = %d AND topic_id = %d AND mid <= %d AND read_state IN(0, 2) AND out = 0", -chatId, mid, readMaxId)).stepThis().dispose();
            //mark mentions as read
            database.executeFast(String.format(Locale.US, "UPDATE messages_topics SET read_state = read_state | 2 WHERE uid = %d AND topic_id = %d AND mid <= %d AND read_state IN(0, 1) AND out = 0", -chatId, mid, readMaxId)).stepThis().dispose();

            int unreadMentionsCount = -1;
            if (unreadCount < 0) {
                unreadCount = 0;
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT count(mid) FROM messages_topics WHERE uid = %d AND topic_id = %d AND mid > %d AND read_state IN(0, 2) AND out = 0", -chatId, mid, readMaxId));
                if (cursor.next()) {
                    unreadCount = cursor.intValue(0);
                }
                cursor.dispose();
                cursor = null;
                if (unreadCount == 0) {
                    unreadMentionsCount = 0;
                } else {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT count(mid) FROM messages_topics WHERE uid = %d AND topic_id = %d AND mid > %d AND read_state IN(0, 1) AND out = 0", -chatId, mid, readMaxId));
                    if (cursor.next()) {
                        unreadMentionsCount = cursor.intValue(0);
                    }
                    cursor.dispose();
                    cursor = null;

                    int currentUnreadMentions = 0;
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT unread_mentions FROM topics WHERE did = %d AND topic_id = %d", -chatId, mid));
                    if (cursor.next()) {
                        currentUnreadMentions = cursor.intValue(0);
                    }
                    cursor.dispose();
                    cursor = null;
                    if (unreadMentionsCount > currentUnreadMentions) {
                        unreadMentionsCount = currentUnreadMentions;
                    }
                }
            } else if (unreadCount == 0) {
                unreadMentionsCount = 0;
            }


            if (updateTopic) {
                if (unreadMentionsCount >= 0) {
                    if (BuildVars.DEBUG_PRIVATE_VERSION && unreadMentionsCount > 0) {
                        FileLog.d("(updateRepliesMaxReadIdInternal) new unread mentions " + unreadMentionsCount + " for dialog_id=" + -chatId + " topic_id=" + mid);
                    }
                    database.executeFast(String.format(Locale.ENGLISH, "UPDATE topics SET max_read_id = %d, unread_count = %d, unread_mentions = %d WHERE did = %d AND topic_id = %d", readMaxId, unreadCount, unreadMentionsCount, -chatId, mid)).stepThis().dispose();
                } else {
                    database.executeFast(String.format(Locale.ENGLISH, "UPDATE topics SET max_read_id = %d, unread_count = %d WHERE did = %d AND topic_id = %d", readMaxId, unreadCount, -chatId, mid)).stepThis().dispose();
                }

                int finalUnreadCount = unreadCount;
                int finalUnreadMentionsCount = unreadMentionsCount;
                AndroidUtilities.runOnUIThread(() -> {
                    getMessagesController().getTopicsController().updateMaxReadId(chatId, mid, readMaxId, finalUnreadCount, finalUnreadMentionsCount);
                });

                resetForumBadgeIfNeed(-chatId);
            }
        } catch (Exception e) {
            checkSQLException(e);
        } finally {
            if (state != null) {
                state.dispose();
            }
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    //if all topics read mark all dialog as read
    private void resetForumBadgeIfNeed(long dialogId) {
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized(String.format(Locale.ENGLISH, "SELECT topic_id FROM topics WHERE did = %d AND unread_count > 0", dialogId));

            LongSparseIntArray dialogsToUpdate = null;
            if (!cursor.next()) {
                dialogsToUpdate = new LongSparseIntArray();
                dialogsToUpdate.put(dialogId, 0);
            }
            cursor.dispose();
            cursor = null;

            if (dialogsToUpdate != null) {
                database.executeFast(String.format(Locale.ENGLISH, "UPDATE dialogs SET unread_count = 0, unread_count_i = 0 WHERE did = %d", dialogId)).stepThis().dispose();
            }
            updateFiltersReadCounter(dialogsToUpdate, null, true);
            getMessagesController().processDialogsUpdateRead(dialogsToUpdate, null);
        } catch (Throwable e) {
            checkSQLException(e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    public void updateRepliesMaxReadId(long chatId, int mid, int readMaxId, int unreadCount, boolean useQueue) {
        if (useQueue) {
            storageQueue.postRunnable(() -> updateRepliesMaxReadIdInternal(chatId, mid, readMaxId, unreadCount));
        } else {
            updateRepliesMaxReadIdInternal(chatId, mid, readMaxId, unreadCount);
        }
    }

    public void updateRepliesCount(long chatId, int mid, ArrayList<TLRPC.Peer> repliers, int maxId, int count) {
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            SQLiteCursor cursor = null;
            try {
                state = database.executeFast("UPDATE messages_v2 SET replies_data = ? WHERE mid = ? AND uid = ?");
                TLRPC.MessageReplies currentReplies = null;
                cursor = database.queryFinalized(String.format(Locale.ENGLISH, "SELECT replies_data FROM messages_v2 WHERE mid = %d AND uid = %d", mid, -chatId));
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        currentReplies = TLRPC.MessageReplies.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                    }
                }
                cursor.dispose();
                cursor = null;
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
                state = null;
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    private boolean isValidKeyboardToSave(TLRPC.Message message) {
        return message.reply_markup != null && !(message.reply_markup instanceof TLRPC.TL_replyInlineMarkup) && (!message.reply_markup.selective || message.mentioned);
    }

    public void updateMessageVerifyFlags(ArrayList<TLRPC.Message> messages) {
        Utilities.stageQueue.postRunnable(() -> {
            boolean databaseInTransaction = false;
            SQLitePreparedStatement state = null;
            try {
                database.beginTransaction();
                databaseInTransaction = true;
                state = database.executeFast("UPDATE messages_v2 SET imp = ? WHERE mid = ? AND uid = ?");
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
                state = null;
                database.commitTransaction();
                databaseInTransaction = false;
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (databaseInTransaction) {
                    if (database != null) {
                        database.commitTransaction();
                    }
                }
                if (state != null) {
                    state.dispose();
                }
            }
        });

    }

    private void putMessagesInternal(ArrayList<TLRPC.Message> messages, boolean withTransaction, boolean doNotUpdateDialogDate, int downloadMask, boolean ifNoLastMessage, boolean scheduled, int threadMessageId) {
        boolean databaseInTransaction = false;
        SQLitePreparedStatement state_messages = null;
        SQLitePreparedStatement state_messages_topic = null;
        SQLitePreparedStatement state_randoms = null;
        SQLitePreparedStatement state_download = null;
        SQLitePreparedStatement state_webpage = null;
        SQLitePreparedStatement state_media = null;
        SQLitePreparedStatement state_polls = null;
        SQLitePreparedStatement state_tasks = null;
        SQLitePreparedStatement state_dialogs_replace = null;
        SQLitePreparedStatement state_dialogs_update = null;
        SQLitePreparedStatement state_topics_update = null;
        SQLitePreparedStatement state_media_topics = null;
        SQLiteCursor cursor = null;
        try {
            if (scheduled) {
                if (withTransaction) {
                    database.beginTransaction();
                    databaseInTransaction = true;
                }

                state_messages = database.executeFast("REPLACE INTO scheduled_messages_v2 VALUES(?, ?, ?, ?, ?, ?, NULL, 0)");
                state_randoms = database.executeFast("REPLACE INTO randoms_v2 VALUES(?, ?, ?)");
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
                state_messages = null;
                state_randoms.dispose();
                state_randoms = null;

                if (withTransaction) {
                    database.commitTransaction();
                    databaseInTransaction = false;
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
                    cursor = database.queryFinalized("SELECT last_mid FROM dialogs WHERE did = " + lastMessage.dialog_id);
                    if (cursor.next()) {
                        lastMid = cursor.intValue(0);
                    }
                    cursor.dispose();
                    cursor = null;
                    if (lastMid != 0) {
                        return;
                    }
                }
                if (withTransaction) {
                    database.beginTransaction();
                    databaseInTransaction = false;
                }
                LongSparseArray<TLRPC.Message> messagesMap = new LongSparseArray<>();
                LongSparseIntArray messagesCounts = new LongSparseIntArray();
                LongSparseIntArray newMessagesCounts = new LongSparseIntArray();
                LongSparseIntArray newMentionsCounts = new LongSparseIntArray();
                LongSparseIntArray mentionCounts = new LongSparseIntArray();
                SparseArray<LongSparseIntArray> mediaCounts = null;
                HashMap<TopicKey, TLRPC.Message> botKeyboards = new HashMap<>();

                LongSparseArray<ArrayList<Integer>> dialogMessagesMediaIdsMap = null;
                LongSparseArray<SparseIntArray> dialogsMediaTypesChange = null;
                LongSparseArray<StringBuilder> mediaIdsMap = null;
                LongSparseArray<SparseIntArray> dialogMediaTypes = null;
                LongSparseArray<StringBuilder> messageIdsMap = new LongSparseArray<>();
                LongSparseIntArray dialogsReadMax = new LongSparseIntArray();
                LongSparseArray<ArrayList<Integer>> dialogMessagesIdsMap = new LongSparseArray<>();
                LongSparseArray<ArrayList<Integer>> dialogMentionsIdsMap = new LongSparseArray<>();

                HashMap<TopicKey, Integer> topicsReadMax = new HashMap<>();
                HashMap<TopicKey, Integer> topicsNewUnreadMessages = new HashMap<>();
                HashMap<TopicKey, Integer> topicsNewMessages = new HashMap<>();
                HashMap<TopicKey, TLRPC.Message> topicMessagesMap = new HashMap<>();
                HashMap<TopicKey, ArrayList<Integer>> topicMentionsIdsMap = new HashMap<>();
                HashMap<TopicKey, Integer> topicsMentions = new HashMap<>();
                SparseArray<HashMap<TopicKey, Integer>> mediaCountsTopics = new SparseArray<>();
                HashMap<TopicKey,StringBuilder> mediaIdsMapTopics =  new HashMap<>();
                HashMap<TopicKey, ArrayList<Integer>> messagesMediaIdsMapTopics = new HashMap<>();
                ArrayList<TLRPC.Message> createNewTopics = null;

                state_messages = database.executeFast("REPLACE INTO messages_v2 VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, 0, ?, ?)");
                state_messages_topic = database.executeFast("REPLACE INTO messages_topics VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, 0, ?)");
                state_media = null;
                state_randoms = database.executeFast("REPLACE INTO randoms_v2 VALUES(?, ?, ?)");
                state_download = database.executeFast("REPLACE INTO download_queue VALUES(?, ?, ?, ?, ?)");
                state_webpage = database.executeFast("REPLACE INTO webpage_pending_v2 VALUES(?, ?, ?)");
                state_polls = null;
                state_tasks = null;
                int minDeleteTime = Integer.MAX_VALUE;

                for (int a = 0; a < messages.size(); a++) {
                    TLRPC.Message message = messages.get(a);

                    int messageId = message.id;
                    MessageObject.getDialogId(message);
                    int topicId = MessageObject.getTopicId(message, isForum(message.dialog_id));

                    if (message.mentioned && message.media_unread) {
                        ArrayList<Integer> ids = dialogMentionsIdsMap.get(message.dialog_id);
                        if (ids == null) {
                            ids = new ArrayList<>();
                            dialogMentionsIdsMap.put(message.dialog_id, ids);
                        }
                        ids.add(messageId);
                        if (topicId != 0) {
                            FileLog.d("add message with message to " + message.dialog_id + " " + topicId);
                            TopicKey topicKey = TopicKey.of(message.dialog_id, topicId);
                            ArrayList<Integer> ids2 = topicMentionsIdsMap.get(topicKey);
                            if (ids2 == null) {
                                ids2 = new ArrayList<>();
                                topicMentionsIdsMap.put(topicKey, ids2);
                            }
                            ids2.add(messageId);
                        }
                    }

                    if (!(message.action instanceof TLRPC.TL_messageActionHistoryClear) && (!MessageObject.isOut(message) || message.from_scheduled || topicId != 0) && (message.id > 0 || MessageObject.isUnread(message)) && !(isForum(message.dialog_id) && topicId == 0)) {
                        int currentMaxId = dialogsReadMax.get(message.dialog_id, -1);
                        if (currentMaxId == -1) {
                            cursor = database.queryFinalized("SELECT inbox_max FROM dialogs WHERE did = " + message.dialog_id);
                            if (cursor.next()) {
                                currentMaxId = cursor.intValue(0);
                            } else {
                                currentMaxId = 0;
                            }
                            cursor.dispose();
                            cursor = null;
                            dialogsReadMax.put(message.dialog_id, currentMaxId);
                        }
                        FileLog.d("update messageRead currentMaxId = " + currentMaxId + " dialogId = " + message.dialog_id);
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
                        if (topicId != 0) {
                            TopicKey topicKey = TopicKey.of(message.dialog_id, topicId);
                            Integer value = topicsReadMax.get(topicKey);
                            int currentTopicMaxId = value == null ? -1 : value;
                            if (currentTopicMaxId == -1) {
                                cursor = database.queryFinalized("SELECT top_message FROM topics WHERE did = " + message.dialog_id + " AND topic_id = " + topicId);
                                if (cursor.next()) {
                                    currentTopicMaxId = cursor.intValue(0);
                                } else {
                                    currentTopicMaxId = 0;
                                }
                                cursor.dispose();
                                cursor = null;
                                topicsReadMax.put(topicKey, currentTopicMaxId);
                            }

                            if (currentTopicMaxId < message.id && message.unread && !message.out) {
                                Integer newUnread = topicsNewUnreadMessages.get(topicKey);
                                if (newUnread == null) {
                                    newUnread = 0;
                                }
                                newUnread++;
                                topicsNewUnreadMessages.put(topicKey, newUnread);
                            }
                            if (currentTopicMaxId < message.id) {
                                Integer newMessages = topicsNewMessages.get(topicKey);
                                if (newMessages == null) {
                                    newMessages = 0;
                                }
                                newMessages++;
                                topicsNewMessages.put(topicKey, newMessages);
                            }
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
                        if (topicId != 0) {
                            TopicKey topicKey = TopicKey.of(message.dialog_id, topicId);
                            StringBuilder messageMediaIdsTopics = mediaIdsMapTopics.get(topicKey);
                            if (messageMediaIdsTopics == null) {
                                messageMediaIdsTopics = new StringBuilder();
                                mediaIdsMapTopics.put(topicKey, messageMediaIdsTopics);
                            }
                            if (messageMediaIdsTopics.length() > 0) {
                                messageMediaIdsTopics.append(",");
                            }
                            messageMediaIdsTopics.append(messageId);

                            ids = messagesMediaIdsMapTopics.get(topicKey);
                            if (ids == null) {
                                ids = new ArrayList<>();
                                messagesMediaIdsMapTopics.put(topicKey, ids);
                            }
                            ids.add(messageId);
                        }
                    }
                    if (isValidKeyboardToSave(message)) {
                        TopicKey topicKey = TopicKey.of(message.dialog_id, topicId);
                        TLRPC.Message oldMessage = botKeyboards.get(topicKey);
                        if (oldMessage == null || oldMessage.id < message.id) {
                            botKeyboards.put(topicKey, message);
                        }
                    }
                }

                if (botKeyboards != null && !botKeyboards.isEmpty()) {
                    Iterator<TopicKey> iterator = botKeyboards.keySet().iterator();
                    while (iterator.hasNext()) {
                        TopicKey topicKey = iterator.next();
                        getMediaDataController().putBotKeyboard(topicKey, botKeyboards.get(topicKey));
                    }
                }

                if (mediaIdsMap != null) {
                    for (int b = 0, N2 = mediaIdsMap.size(); b < N2; b++) {
                        long dialogId = mediaIdsMap.keyAt(b);
                        StringBuilder messageMediaIds = mediaIdsMap.valueAt(b);
                        SparseIntArray mediaTypes = dialogMediaTypes.get(dialogId);
                        ArrayList<Integer> messagesMediaIds = dialogMessagesMediaIdsMap.get(dialogId);
                        SparseIntArray mediaTypesChange = null;
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid, type FROM media_v4 WHERE mid IN(%s) AND uid = %d", messageMediaIds.toString(), dialogId));
                        while (cursor.next()) {
                            int mid = cursor.intValue(0);
                            int type = cursor.intValue(1);
                            if (type == mediaTypes.get(mid)) {
                                messagesMediaIds.remove((Integer) mid);
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
                        cursor = null;
                        if (mediaCounts == null) {
                            mediaCounts = new SparseArray<>();
                        }

                        for (int a = 0, N = messagesMediaIds.size(); a < N; a++) {
                            int messageId = messagesMediaIds.get(a);
                            int type = mediaTypes.get(messageId);
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
                                int previousType = mediaTypesChange.get(messageId, -1);
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
                if (mediaIdsMapTopics != null) {
                    Iterator<TopicKey> iterator = mediaIdsMapTopics.keySet().iterator();

                    while (iterator.hasNext()) {
                        TopicKey topicKey = iterator.next();
                        ArrayList<Integer> messagesIds = messagesMediaIdsMapTopics.get(topicKey);
                        StringBuilder messageIds = mediaIdsMapTopics.get(topicKey);
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid, type FROM media_topics WHERE mid IN(%s) AND uid = %d AND topic_id = %d", messageIds.toString(), topicKey.dialogId, topicKey.topicId));

                        SparseIntArray mediaTypesChange = null;

                        while (cursor.next()) {
                            SparseIntArray mediaTypes = dialogMediaTypes.get(topicKey.dialogId);

                            int mid = cursor.intValue(0);
                            int type = cursor.intValue(1);
                            if (type == mediaTypes.get(mid)) {
                                messagesIds.remove((Integer) mid);
                            } else {
                                if (mediaTypesChange == null) {
                                    if (dialogsMediaTypesChange == null) {
                                        dialogsMediaTypesChange = new LongSparseArray<>();
                                    }
                                    mediaTypesChange = dialogsMediaTypesChange.get(topicKey.dialogId);
                                    if (mediaTypesChange == null) {
                                        mediaTypesChange = new SparseIntArray();
                                        dialogsMediaTypesChange.put(topicKey.dialogId, mediaTypesChange);
                                    }
                                }
                                mediaTypesChange.put(mid, type);
                            }
                        }

                        SparseIntArray mediaTypes = dialogMediaTypes.get(topicKey.dialogId);

                        if (mediaCountsTopics == null) {
                            mediaCountsTopics = new SparseArray<>();
                        }

                        for (int a = 0, N = messagesIds.size(); a < N; a++) {
                            int messageId = messagesIds.get(a);
                            int type = mediaTypes.get(messageId);
                            HashMap<TopicKey, Integer> counts = mediaCountsTopics.get(type);
                            int count;
                            if (counts == null) {
                                counts = new HashMap<>();
                                mediaCountsTopics.put(type, counts);
                                count = 0;
                            } else {
                                Integer v = counts.get(topicKey);
                                if (v == null) {
                                    count = 0;
                                } else {
                                    count = v;
                                }
                            }

                            count++;
                            counts.put(topicKey, count);
                            if (mediaTypesChange != null) {
                                int previousType = mediaTypesChange.get(messageId, -1);
                                if (previousType >= 0) {
                                    counts = mediaCountsTopics.get(previousType);
                                    if (counts == null) {
                                        counts = new HashMap<>();
                                        count = 0;
                                        mediaCountsTopics.put(previousType, counts);
                                    } else {
                                        Integer v = counts.get(topicKey);
                                        if (v == null) {
                                            count = Integer.MIN_VALUE;
                                        } else {
                                            count = v;
                                        }
                                    }
                                    if (count == Integer.MIN_VALUE) {
                                        count = 0;
                                    }
                                    count--;
                                    counts.put(topicKey, count);
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
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid FROM messages_v2 WHERE mid IN(%s) AND uid = %d", messageIds.toString(), dialogId));
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
                        cursor = null;

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

                if (!topicMentionsIdsMap.isEmpty()) {
                    Iterator<TopicKey> iterator = topicMentionsIdsMap.keySet().iterator();
                    while (iterator.hasNext()) {
                        TopicKey topicKey = iterator.next();
                        ArrayList<Integer> messageIds = topicMentionsIdsMap.get(topicKey);
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid FROM messages_topics WHERE mid IN(%s) AND uid = %d AND topic_id = %d", TextUtils.join(",", messageIds), topicKey.dialogId, topicKey.topicId));
                        while (cursor.next()) {
                            Integer mid = cursor.intValue(0);
                            messageIds.remove(mid);
                        }
                        cursor.dispose();
                        cursor = null;

                        FileLog.d("new unread mentions " + topicKey.dialogId + " " + topicKey.topicId + " " + messageIds.size());
                        topicsMentions.put(topicKey, messageIds.size());
                    }
                }

                if (dialogsMediaTypesChange != null) {
                    for (int i = 0; i < dialogsMediaTypesChange.size(); i++) {
                        long dialogId = dialogsMediaTypesChange.keyAt(i);
                        SparseIntArray messageIds = dialogsMediaTypesChange.valueAt(i);
                        StringBuilder messagesString = new StringBuilder();
                        for (int k = 0; k < dialogsMediaTypesChange.size(); k++) {
                            int mid = messageIds.keyAt(k);
                            if (messagesString.length() != 0) {
                                messagesString.append(", ");
                            }
                            messagesString.append(mid);
                        }
                        database.executeFast(String.format(Locale.US, "DELETE FROM media_v4 WHERE mid IN(%s) AND uid = %d", messagesString.toString(), dialogId)).stepThis().dispose();
                        database.executeFast(String.format(Locale.US, "DELETE FROM media_topics WHERE mid IN(%s) AND uid = %d", messagesString.toString(), dialogId)).stepThis().dispose();
                    }
                }

                int downloadMediaMask = 0;
                for (int a = 0; a < messages.size(); a++) {
                    TLRPC.Message message = messages.get(a);
                    if (message == null) {
                        continue;
                    }
                    fixUnsupportedMedia(message);
                    int topicId = MessageObject.getTopicId(message, isForum(message.dialog_id));

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
//                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid, group_id FROM messages_v2 WHERE mid = %d AND uid = %d", messageId, message.dialog_id));
//                        if (cursor.next()) {
//                            updateDialog = false;
//                        }
//                        cursor.dispose();
//                        cursor = null;
                    }

                    if (message.action instanceof TLRPC.TL_messageActionTopicCreate && !MessageObject.isOut(message)) {
                        if (createNewTopics == null) {
                            createNewTopics = new ArrayList<>();
                        }
                        createNewTopics.add(message);
                    }
                    if (message.action instanceof TLRPC.TL_messageActionTopicEdit) {
                        if (createNewTopics == null) {
                            createNewTopics = new ArrayList<>();
                        }
                        createNewTopics.add(message);
                    }

                    if (updateDialog) {
                        TLRPC.Message lastMessage = messagesMap.get(message.dialog_id);
                        if (lastMessage == null || message.date > lastMessage.date || lastMessage.id > 0 && message.id > lastMessage.id || lastMessage.id < 0 && message.id < lastMessage.id) {
                            messagesMap.put(message.dialog_id, message);
                        }
                        if (topicId != 0) {
                            TopicKey topicKey = TopicKey.of(message.dialog_id, topicId);
                            lastMessage = topicMessagesMap.get(topicKey);
                            if (lastMessage == null || message.date > lastMessage.date || lastMessage.id > 0 && message.id > lastMessage.id || lastMessage.id < 0 && message.id < lastMessage.id) {
                                topicMessagesMap.put(topicKey, message);
                            }
                        }
                    }


                    for (int i = 0; i < 2; i++) {
                        boolean isTopic = i == 1;
                        if (threadMessageId != 0 && !isTopic) {
                            continue;
                        }
                        if (isTopic && topicId == 0) {
                            continue;
                        }
                        int pointer = 1;
                        SQLitePreparedStatement statement = isTopic ? state_messages_topic : state_messages;

                        statement.requery();
                        statement.bindInteger(pointer++, messageId);
                        statement.bindLong(pointer++, message.dialog_id);
                        if (isTopic) {
                            statement.bindLong(pointer++, topicId);
                        }
                        statement.bindInteger(pointer++, MessageObject.getUnreadFlags(message));
                        statement.bindInteger(pointer++, message.send_state);
                        statement.bindInteger(pointer++, message.date);
                        statement.bindByteBuffer(pointer++, data);
                        statement.bindInteger(pointer++, (MessageObject.isOut(message) || message.from_scheduled ? 1 : 0));
                        statement.bindInteger(pointer++, message.ttl);
                        if ((message.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                            statement.bindInteger(pointer++, message.views);
                        } else {
                            statement.bindInteger(pointer++, getMessageMediaType(message));
                        }
                        int flags = 0;
                        if (message.stickerVerified == 0) {
                            flags |= 1;
                        } else if (message.stickerVerified == 2) {
                            flags |= 2;
                        }
                        statement.bindInteger(pointer++, flags);
                        statement.bindInteger(pointer++, message.mentioned ? 1 : 0);
                        statement.bindInteger(pointer++, message.forwards);
                        NativeByteBuffer repliesData = null;
                        if (message.replies != null) {
                            repliesData = new NativeByteBuffer(message.replies.getObjectSize());
                            message.replies.serializeToStream(repliesData);
                            statement.bindByteBuffer(pointer++, repliesData);
                        } else {
                            statement.bindNull(pointer++);
                        }
                        if (message.reply_to != null) {
                            statement.bindInteger(pointer++, message.reply_to.reply_to_top_id != 0 ? message.reply_to.reply_to_top_id : message.reply_to.reply_to_msg_id);
                        } else {
                            statement.bindInteger(pointer++, 0);
                        }
                        statement.bindLong(pointer++, MessageObject.getChannelId(message));
                        NativeByteBuffer customParams = MessageCustomParamsHelper.writeLocalParams(message);
                        if (customParams != null) {
                            statement.bindByteBuffer(pointer++, customParams);
                        } else {
                            statement.bindNull(pointer++);
                        }
                        if (!isTopic) {
                            if ((message.flags & 131072) != 0) {
                                statement.bindLong(pointer++, message.grouped_id);
                            } else {
                                statement.bindNull(pointer++);
                            }
                        }
                        statement.step();

                        if (repliesData != null) {
                            repliesData.reuse();
                        }
                        if (customParams != null) {
                            customParams.reuse();
                        }
                    }

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
                        state_media = null;

                        if (topicId != 0) {
                            if (state_media_topics == null) {
                                state_media_topics = database.executeFast("REPLACE INTO media_topics VALUES(?, ?, ?, ?, ?, ?)");
                            }
                            state_media_topics.requery();
                            state_media_topics.bindInteger(1, messageId);
                            state_media_topics.bindLong(2, message.dialog_id);
                            state_media_topics.bindInteger(3, topicId);
                            state_media_topics.bindInteger(4, message.date);
                            state_media_topics.bindInteger(5, MediaDataController.getMediaType(message));
                            state_media_topics.bindByteBuffer(6, data);
                            state_media_topics.step();
                        }
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
                                MessageObject messageObject = new MessageObject(currentAccount, message, false, false);
                                downloadMediaMask |= type;
                                state_download.requery();
                                data = new NativeByteBuffer(object.getObjectSize());
                                object.serializeToStream(data);
                                state_download.bindLong(1, id);
                                state_download.bindInteger(2, type);
                                state_download.bindInteger(3, message.date);
                                state_download.bindByteBuffer(4, data);
                                state_download.bindString(5, "sent_" + (message.peer_id != null ? message.peer_id.channel_id : 0) + "_" + message.id + "_" + DialogObject.getPeerDialogId(message.peer_id) + "_" + messageObject.type + "_" + messageObject.getSize());
                                state_download.step();
                                data.reuse();
                            }
                        }
                    }
                }
                state_messages.dispose();
                state_messages_topic.dispose();
                if (state_media != null) {
                    state_media.dispose();
                    state_media = null;
                }
                if (state_tasks != null) {
                    state_tasks.dispose();
                    getMessagesController().didAddedNewTask(minDeleteTime, 0, null);
                    state_tasks = null;
                }
                if (state_polls != null) {
                    state_polls.dispose();
                    state_polls = null;
                }
                state_randoms.dispose();
                state_randoms = null;
                state_download.dispose();
                state_download = null;
                state_webpage.dispose();
                state_webpage = null;

                if (createNewTopics != null) {
                    for (int i = 0; i < createNewTopics.size(); i++) {
                        TLRPC.Message message = createNewTopics.get(i);
                        createOrEditTopic(message.dialog_id, message);
                    }
                }

                state_dialogs_replace = database.executeFast("REPLACE INTO dialogs VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                state_dialogs_update = database.executeFast("UPDATE dialogs SET date = ?, unread_count = ?, last_mid = ?, last_mid_group = ?, unread_count_i = ? WHERE did = ?");
                state_topics_update = database.executeFast("UPDATE topics SET unread_count = ?, top_message = ?, unread_mentions = ?, total_messages_count = ? WHERE did = ? AND topic_id = ?");

                ArrayList<Long> dids = new ArrayList<>();
                for (int a = 0; a < messagesMap.size(); a++) {
                    long key = messagesMap.keyAt(a);
                    if (key == 0) {
                        continue;
                    }
                    TLRPC.Message message = messagesMap.valueAt(a);

                    long channelId = MessageObject.getChannelId(message);

                    cursor = database.queryFinalized("SELECT date, unread_count, last_mid, unread_count_i FROM dialogs WHERE did = " + key);
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
                    cursor = null;

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
                    if (!isForum(key)) {
                        if (old_unread_count == 0 && unread_count != 0) {
                            newMessagesCounts.put(key, unread_count);
                        }
                        if (old_mentions_count == 0 && mentions_count != 0){
                            newMentionsCounts.put(key, mentions_count);
                        }
                    }

                    dids.add(key);
                    if (exists) {
                        state_dialogs_update.requery();
                        state_dialogs_update.bindInteger(1, message != null && (!doNotUpdateDialogDate || dialog_date == 0) ? message.date : dialog_date);
                        state_dialogs_update.bindInteger(2, old_unread_count + unread_count);
                        state_dialogs_update.bindInteger(3, messageId);
                        if (message != null && (message.flags & 131072) != 0) {
                            state_dialogs_update.bindLong(4, message.grouped_id);
                        } else {
                            state_dialogs_update.bindNull(4);
                        }
                        state_dialogs_update.bindInteger(5, old_mentions_count + mentions_count);
                        state_dialogs_update.bindLong(6, key);
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
                        if (message != null && (message.flags & 131072) != 0) {
                            state_dialogs_replace.bindLong(16, message.grouped_id);
                        } else {
                            state_dialogs_replace.bindNull(16);
                        }
                        state_dialogs_replace.bindInteger(17, 0);
                        state_dialogs_replace.step();
                        unknownDialogsIds.put(key, true);
                    }
                }
                state_dialogs_update.dispose();
                state_dialogs_update = null;
                state_dialogs_replace.dispose();
                state_dialogs_replace = null;


                ArrayList<TopicsController.TopicUpdate> topicUpdatesInUi = new ArrayList<>();
                Iterator<TopicKey> iterator = topicMessagesMap.keySet().iterator();
                while (iterator.hasNext()) {
                    TopicKey topicKey = iterator.next();
                    if (topicKey.dialogId == 0 || topicKey.topicId == 0) {
                        continue;
                    }

                    TLRPC.Message message = topicMessagesMap.get(topicKey);

                    cursor = database.queryFinalized("SELECT unread_count, top_message, unread_mentions, total_messages_count FROM topics WHERE did = " + topicKey.dialogId + " AND topic_id = " + topicKey.topicId);
                    int oldUnreadCount = 0;
                    int oldMentions = 0;
                    int oldTotalCount = 0;
                    int topMessage = 0;
                    int newMentions = 0;
                    int newUnreadMessages = 0;
                    int newMessages = 0;

                    boolean exist = false;
                    if (cursor.next()) {
                        exist = true;
                        oldUnreadCount = cursor.intValue(0);
                        topMessage = cursor.intValue(1);
                        oldMentions = cursor.intValue(2);
                        oldTotalCount = cursor.intValue(3);
                    }
                    cursor.dispose();
                    cursor = null;
                    if (!exist) {
                        if (topicUpdatesInUi == null) {
                            topicUpdatesInUi = new ArrayList<>();
                        }
                        TopicsController.TopicUpdate topicUpdate = new TopicsController.TopicUpdate();
                        topicUpdate.dialogId = topicKey.dialogId;
                        topicUpdate.topicId = topicKey.topicId;
                        topicUpdate.reloadTopic = true;
                        topicUpdatesInUi.add(topicUpdate);
                        FileLog.d("unknown topic need reload " + topicKey.dialogId + " " + topicKey.topicId);
                        continue;
                    }
                    Integer newMessagesInteger = topicsNewUnreadMessages.get(topicKey);
                    Integer newMentionsInteger = topicsMentions.get(topicKey);
                    Integer newMessagesTotalInteger = topicsNewMessages.get(topicKey);

                    int messageId = message != null ? message.id : topMessage;
                    if (message != null) {
                        if (message.local_id != 0) {
                            messageId = message.local_id;
                        }
                    }
                    if (newMessagesInteger != null) {
                        newUnreadMessages = newMessagesInteger;
                    }
                    if (newMentionsInteger != null) {
                        newMentions = newMentionsInteger;
                    }
                    if (newMessagesTotalInteger != null) {
                        newMessages = newMessagesTotalInteger;
                    }
                    int newUnreadCount = oldUnreadCount + newUnreadMessages;
                    int newUnreadMentions = oldMentions + newMentions;
                    int newTotalMessagesCount = oldTotalCount == 0 ? 0 : oldTotalCount + newMessages;

                    if (BuildVars.DEBUG_PRIVATE_VERSION && newUnreadMentions > 0) {
                        FileLog.d("(putMessagesInternal)" + " new unread mentions " + newUnreadMentions + " for dialog_id=" + topicKey.dialogId + " topic_id=" + topicKey.topicId);
                    }
                    state_topics_update.requery();
                    state_topics_update.bindInteger(1, newUnreadCount);
                    state_topics_update.bindInteger(2,  messageId);
                    state_topics_update.bindInteger(3,  newUnreadMentions);
                    state_topics_update.bindInteger(4,  newTotalMessagesCount);
                    state_topics_update.bindLong(5, topicKey.dialogId);
                    state_topics_update.bindInteger(6, topicKey.topicId);
                    state_topics_update.step();

                    if (isForum(topicKey.dialogId)) {
                        if (oldUnreadCount == 0 && newUnreadCount != 0) {
                            newMessagesCounts.put(topicKey.dialogId, 1);
                        }
                        if (oldMentions == 0 && newUnreadMentions != 0){
                            newMentionsCounts.put(topicKey.dialogId, newUnreadMentions);
                        }
                    }

                    FileLog.d("update topic " + topicKey.dialogId + " " + topicKey.topicId + " " + (oldUnreadCount + newUnreadMessages) + " " + (oldMentions + newMentions));
                    if (message != null) {
                        if (topicUpdatesInUi == null) {
                            topicUpdatesInUi = new ArrayList<>();
                        }
                        TopicsController.TopicUpdate topicUpdate = new TopicsController.TopicUpdate();
                        topicUpdate.dialogId = topicKey.dialogId;
                        topicUpdate.topicId = topicKey.topicId;
                        topicUpdate.topMessage = message;
                        topicUpdate.unreadMentions = oldMentions + newMentions;
                        topicUpdate.topMessageId = messageId;
                        topicUpdate.unreadCount = oldUnreadCount + newUnreadMessages;
                        topicUpdate.totalMessagesCount = newTotalMessagesCount;
                        topicUpdatesInUi.add(topicUpdate);
                    }
                }

                state_topics_update.dispose();
                state_topics_update = null;

                if (mediaCounts != null) {
                    state_randoms = database.executeFast("REPLACE INTO media_counts_v2 VALUES(?, ?, ?, ?)");
                    for (int a = 0, N = mediaCounts.size(); a < N; a++) {
                        int type = mediaCounts.keyAt(a);
                        LongSparseIntArray value = mediaCounts.valueAt(a);
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
                            cursor = null;
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
                    state_randoms = null;
                }

                if (mediaCountsTopics != null) {
                    state_randoms = database.executeFast("REPLACE INTO media_counts_topics VALUES(?, ?, ?, ?, ?)");
                    for (int a = 0, N = mediaCountsTopics.size(); a < N; a++) {
                        int type = mediaCountsTopics.keyAt(a);
                        HashMap<TopicKey, Integer> topicCountsMap = mediaCountsTopics.valueAt(a);
                        iterator = topicCountsMap.keySet().iterator();
                        while (iterator.hasNext()) {
                            TopicKey topicKey = iterator.next();
                            int count = -1;
                            int old = 0;
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT count, old FROM media_counts_topics WHERE uid = %d AND topic_id = %d AND type = %d LIMIT 1", topicKey.dialogId, topicKey.topicId, type));
                            if (cursor.next()) {
                                count = cursor.intValue(0);
                                old = cursor.intValue(1);
                            }
                            cursor.dispose();
                            cursor = null;
                            if (count != -1) {
                                state_randoms.requery();
                                count += topicCountsMap.get(topicKey);
                                state_randoms.bindLong(1, topicKey.dialogId);
                                state_randoms.bindInteger(2, topicKey.topicId);
                                state_randoms.bindInteger(3, type);
                                state_randoms.bindInteger(4, Math.max(0, count));
                                state_randoms.bindInteger(5, old);
                                state_randoms.step();
                            }

                            if (BuildVars.DEBUG_PRIVATE_VERSION) {
                                FileLog.d("update" + topicKey.dialogId + topicKey.topicId + " " + type + " " + count);
                            }

                        }
                    }
                    state_randoms.dispose();
                    state_randoms = null;
                }

                if (withTransaction) {
                    database.commitTransaction();
                    databaseInTransaction = false;
                }
                updateFiltersReadCounter(newMessagesCounts, newMentionsCounts, false);
                loadGroupedMessagesForTopicUpdates(topicUpdatesInUi);
                getMessagesController().processDialogsUpdateRead(messagesCounts, mentionCounts);
                getMessagesController().getTopicsController().processUpdate(topicUpdatesInUi);

                if (downloadMediaMask != 0) {
                    int downloadMediaMaskFinal = downloadMediaMask;
                    AndroidUtilities.runOnUIThread(() -> getDownloadController().newDownloadObjectsAvailable(downloadMediaMaskFinal));
                }
                updateWidgets(dids);
            }
        } catch (Exception e) {
            checkSQLException(e);
        } finally {
            if (databaseInTransaction) {
                if (database != null) {
                    database.commitTransaction();
                }
            }
            if (state_messages != null) {
                state_messages.dispose();
            }
            if (state_randoms != null) {
                state_randoms.dispose();
            }
            if (state_download != null) {
                state_download.dispose();
            }
            if (state_webpage != null) {
                state_webpage.dispose();
            }
            if (state_media != null) {
                state_media.dispose();
            }
            if (state_polls != null) {
                state_polls.dispose();
            }
            if (state_tasks != null) {
                state_tasks.dispose();
            }
            if (state_dialogs_replace != null) {
                state_dialogs_replace.dispose();
            }
            if (state_dialogs_update != null) {
                state_dialogs_update.dispose();
            }
            if (state_topics_update != null) {
                state_topics_update.dispose();
            }
            if (cursor != null) {
                cursor = null;
            }
        }
    }

    private void createOrEditTopic(long dialogId, TLRPC.Message message) {
        TLRPC.TL_forumTopic forumTopic = new TLRPC.TL_forumTopic();

        forumTopic.topicStartMessage = message;
        forumTopic.top_message = message.id;
        forumTopic.topMessage = message;
        forumTopic.from_id = getMessagesController().getPeer(getUserConfig().clientUserId);
        forumTopic.notify_settings = new TLRPC.TL_peerNotifySettings();
        forumTopic.unread_count = 0;

        if (message.action instanceof TLRPC.TL_messageActionTopicCreate) {
            TLRPC.TL_messageActionTopicCreate action = (TLRPC.TL_messageActionTopicCreate) message.action;
            forumTopic.id = message.id;
            forumTopic.icon_emoji_id = action.icon_emoji_id;
            forumTopic.title = action.title;
            forumTopic.icon_color = action.icon_color;
            if (forumTopic.icon_emoji_id != 0) {
                forumTopic.flags |= 1;
            }
            ArrayList<TLRPC.TL_forumTopic> topics = new ArrayList<>();
            topics.add(forumTopic);
            saveTopics(dialogId, topics, false, false);
            AndroidUtilities.runOnUIThread(() -> {
                getMessagesController().getTopicsController().onTopicCreated(dialogId, forumTopic, false);
            });
        } else if (message.action instanceof TLRPC.TL_messageActionTopicEdit) {
            TLRPC.TL_messageActionTopicEdit action = (TLRPC.TL_messageActionTopicEdit) message.action;
            forumTopic.id = MessageObject.getTopicId(message, true);
            forumTopic.icon_emoji_id = action.icon_emoji_id;
            forumTopic.title = action.title;
            forumTopic.closed = action.closed;
            forumTopic.hidden = action.hidden;
            int flags = 0;
            if ((action.flags & 1) != 0) {
                flags += TopicsController.TOPIC_FLAG_TITLE;
            }
            if ((action.flags & 2) != 0) {
                flags += TopicsController.TOPIC_FLAG_ICON;
            }
            if ((action.flags & 4) != 0) {
                flags += TopicsController.TOPIC_FLAG_CLOSE;
            }
            if ((action.flags & 8) != 0) {
                flags += TopicsController.TOPIC_FLAG_HIDE;
            }
            updateTopicData(dialogId, forumTopic, flags);
            int finalFlags = flags;
            AndroidUtilities.runOnUIThread(() -> {
                getMessagesController().getTopicsController().updateTopicInUi(dialogId, forumTopic, finalFlags);
            });
        }
    }

    public void putMessages(ArrayList<TLRPC.Message> messages, boolean withTransaction, boolean useQueue, boolean doNotUpdateDialogDate, int downloadMask, boolean scheduled, int threadMessageId) {
        putMessages(messages, withTransaction, useQueue, doNotUpdateDialogDate, downloadMask, false, scheduled, threadMessageId);
    }

    public void putMessages(ArrayList<TLRPC.Message> messages, boolean withTransaction, boolean useQueue, boolean doNotUpdateDialogDate, int downloadMask, boolean ifNoLastMessage, boolean scheduled, int threadMessageId) {
        if (messages.size() == 0) {
            return;
        }
        if (useQueue) {
            storageQueue.postRunnable(() -> putMessagesInternal(messages, withTransaction, doNotUpdateDialogDate, downloadMask, ifNoLastMessage, scheduled, threadMessageId));
        } else {
            putMessagesInternal(messages, withTransaction, doNotUpdateDialogDate, downloadMask, ifNoLastMessage, scheduled, threadMessageId);
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
                    database.executeFast(String.format(Locale.US, "UPDATE messages_topics SET send_state = 2 WHERE mid = %d AND uid = %d", messageId, MessageObject.getDialogId(message))).stepThis().dispose();
                }
            } catch (Exception e) {
                checkSQLException(e);
            }
        });
    }

    public void setMessageSeq(int mid, int seq_in, int seq_out) {
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                state = database.executeFast("REPLACE INTO messages_seq VALUES(?, ?, ?)");
                state.requery();
                state.bindInteger(1, mid);
                state.bindInteger(2, seq_in);
                state.bindInteger(3, seq_out);
                state.step();
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
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
                checkSQLException(e);
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
                checkSQLException(e);
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
                checkSQLException(e);
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
                checkSQLException(e);
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
                checkSQLException(e);
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
        SQLitePreparedStatement state2 = null;
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

                if (scheduled == 0) {
                    state2 = database.executeFast("UPDATE messages_topics SET send_state = 0, date = ? WHERE mid = ? AND uid = ?");
                    state2.bindInteger(1, date);
                    state2.bindInteger(2, newId);
                    state2.bindLong(3, did);
                    state2.step();
                }
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
                if (state2 != null) {
                    state2.dispose();
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

                    state2 = database.executeFast("UPDATE messages_topics SET mid = ?, send_state = 0 WHERE mid = ? AND uid = ?");
                    state2.bindInteger(1, newId);
                    state2.bindInteger(2, oldMessageId);
                    state2.bindLong(3, did);
                    state2.step();
                } catch (Exception e) {
                    try {
                        database.executeFast(String.format(Locale.US, "DELETE FROM messages_v2 WHERE mid = %d AND uid = %d", oldMessageId, did)).stepThis().dispose();
                        database.executeFast(String.format(Locale.US, "DELETE FROM messages_seq WHERE mid = %d", oldMessageId)).stepThis().dispose();
                        database.executeFast(String.format(Locale.US, "DELETE FROM messages_topics WHERE mid = %d AND uid = %d", oldMessageId, did)).stepThis().dispose();
                    } catch (Exception e2) {
                        checkSQLException(e2);
                    }
                } finally {
                    if (state != null) {
                        state.dispose();
                        state = null;
                    }
                    if (state2 != null) {
                        state2.dispose();
                        state2 = null;
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
                        checkSQLException(e2);
                    }
                } finally {
                    if (state != null) {
                        state.dispose();
                        state = null;
                    }
                }

                try {
                    state = database.executeFast("UPDATE media_topics SET mid = ? WHERE mid = ? AND uid = ?");
                    state.bindInteger(1, newId);
                    state.bindInteger(2, oldMessageId);
                    state.bindLong(3, did);
                    state.step();
                } catch (Exception e) {
                    try {
                        database.executeFast(String.format(Locale.US, "DELETE FROM media_topics WHERE mid = %d AND uid = %d", oldMessageId, did)).stepThis().dispose();
                    } catch (Exception e2) {
                        checkSQLException(e2);
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
                    checkSQLException(e);
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
                        checkSQLException(e2);
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
        SQLitePreparedStatement state = null;
        try {
            if (onlyStatus) {
                if (withTransaction) {
                    database.beginTransaction();
                }
                state = database.executeFast("UPDATE users SET status = ? WHERE uid = ?");
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
                state = null;
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
            checkSQLException(e);
        } finally {
            if (database != null) {
                database.commitTransaction();
            }
            if (state != null) {
                state.dispose();
            }
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
        SQLitePreparedStatement state = null;
        try {
            if (!isEmpty(inbox)) {
                state = database.executeFast("DELETE FROM unread_push_messages WHERE uid = ? AND mid <= ?");
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
                state = null;
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
                    state = database.executeFast("UPDATE messages_v2 SET read_state = read_state | 1 WHERE uid = ? AND date <= ? AND read_state IN(0,2) AND out = 1");
                    state.requery();
                    state.bindLong(1, dialogId);
                    state.bindInteger(2, max_date);
                    state.step();
                    state.dispose();
                    state = null;
                }
            }
        } catch (Exception e) {
            checkSQLException(e);
        } finally {
            if (state != null) {
                state.dispose();
            }
        }
    }

    private void markMessagesContentAsReadInternal(long dialogId, ArrayList<Integer> mids, int date) {
        SQLiteCursor cursor = null;
        try {
            String midsStr = TextUtils.join(",", mids);
            database.executeFast(String.format(Locale.US, "UPDATE messages_v2 SET read_state = read_state | 2 WHERE mid IN (%s) AND uid = %d", midsStr, dialogId)).stepThis().dispose();
            if (date != 0) {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid, ttl FROM messages_v2 WHERE mid IN (%s) AND uid = %d AND ttl > 0", midsStr, dialogId));
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
                cursor = null;
            }
        } catch (Exception e) {
            checkSQLException(e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    public void markMessagesContentAsRead(long dialogId, ArrayList<Integer> mids, int date) {
        if (isEmpty(mids)) {
            return;
        }

        storageQueue.postRunnable(() -> {
            if (dialogId == 0) {
                SQLiteCursor cursor = null;
                try {
                    LongSparseArray<ArrayList<Integer>> sparseArray = new LongSparseArray<>();
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, mid FROM messages_v2 WHERE mid IN (%s) AND is_channel = 0", TextUtils.join(",", mids)));
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
                    cursor = null;
                    for (int a = 0, N = sparseArray.size(); a < N; a++) {
                        markMessagesContentAsReadInternal(sparseArray.keyAt(a), sparseArray.valueAt(a), date);
                    }
                } catch (Exception e) {
                    checkSQLException(e);
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
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
            SQLiteCursor cursor = null;
            try {
                String ids = TextUtils.join(",", messages);
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid, uid FROM randoms_v2 WHERE random_id IN(%s)", ids));
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
                cursor = null;
                if (!dialogs.isEmpty()) {
                    for (int a = 0, N = dialogs.size(); a < N; a++) {
                        long dialogId = dialogs.keyAt(a);
                        ArrayList<Integer> mids = dialogs.valueAt(a);
                        AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.messagesDeleted, mids, 0L, false));
                        updateDialogsWithReadMessagesInternal(mids, null, null, null, null);
                        markMessagesAsDeletedInternal(dialogId, mids, true, false);
                        updateDialogsWithDeletedMessagesInternal(dialogId, 0, mids, null);
                    }
                }
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    protected void deletePushMessages(long dialogId, ArrayList<Integer> messages) {
        try {
            database.executeFast(String.format(Locale.US, "DELETE FROM unread_push_messages WHERE uid = %d AND mid IN(%s)", dialogId, TextUtils.join(",", messages))).stepThis().dispose();
        } catch (Exception e) {
            checkSQLException(e);
        }
    }

    private void broadcastScheduledMessagesChange(Long did) {
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM scheduled_messages_v2 WHERE uid = %d", did));
            int count;
            if (cursor.next()) {
                count = cursor.intValue(0);
            } else {
                count = 0;
            }
            cursor.dispose();
            cursor = null;
            AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.scheduledMessagesUpdated, did, count));
        } catch (Exception e) {
            checkSQLException(e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    private ArrayList<Long> markMessagesAsDeletedInternal(long dialogId, ArrayList<Integer> messages, boolean deleteFiles, boolean scheduled) {
        SQLiteCursor cursor = null;
        SQLitePreparedStatement state = null;
        try {
            ArrayList<Long> dialogsIds = new ArrayList<>();
            if (scheduled) {
                String ids = TextUtils.join(",", messages);

                ArrayList<Long> dialogsToUpdate = new ArrayList<>();

                cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid FROM scheduled_messages_v2 WHERE mid IN(%s) AND uid = %d", ids, dialogId));
                try {
                    while (cursor.next()) {
                        long did = cursor.longValue(0);
                        if (!dialogsToUpdate.contains(did)) {
                            dialogsToUpdate.add(did);
                        }
                    }
                } catch (Exception e) {
                    checkSQLException(e);
                }
                cursor.dispose();
                cursor = null;

                database.executeFast(String.format(Locale.US, "DELETE FROM scheduled_messages_v2 WHERE mid IN(%s) AND uid = %d", ids, dialogId)).stepThis().dispose();
                for (int a = 0, N = dialogsToUpdate.size(); a < N; a++) {
                    broadcastScheduledMessagesChange(dialogsToUpdate.get(a));
                }
            } else {
                ArrayList<Integer> unknownMessages = new ArrayList<>(messages);
                ArrayList<Integer> unknownMessagesInTopics = new ArrayList<>(messages);
                LongSparseArray<Integer[]> dialogsToUpdate = new LongSparseArray<>();
                HashMap<TopicKey, int[]> topicsMessagesToUpdate = new HashMap<>();
                LongSparseArray<ArrayList<Integer>> messagesByDialogs = new LongSparseArray<>();
                String ids = TextUtils.join(",", messages);
                ArrayList<File> filesToDelete = new ArrayList<>();
                ArrayList<String> namesToDelete = new ArrayList<>();
                ArrayList<Pair<Long, Integer>> idsToDelete = new ArrayList<>();
                ArrayList<TopicsController.TopicUpdate> topicUpdatesInUi = null;

                long currentUser = getUserConfig().getClientUserId();
                if (dialogId != 0) {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, data, read_state, out, mention, mid FROM messages_v2 WHERE mid IN(%s) AND uid = %d", ids, dialogId));
                } else {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, data, read_state, out, mention, mid FROM messages_v2 WHERE mid IN(%s) AND is_channel = 0", ids));
                }

                try {
                    while (cursor.next()) {
                        long did = cursor.longValue(0);
                        int mid = cursor.intValue(5);
                        unknownMessages.remove((Integer) mid);
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
                    checkSQLException(e);
                }
                cursor.dispose();
                cursor = null;

                ArrayList<TopicKey> topicsToDelete = null;
                if (dialogId < 0) {
                   cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, data, read_state, out, mention, mid FROM messages_topics WHERE mid IN(%s) AND uid = %d", ids, dialogId));

                    try {
                        while (cursor.next()) {
                            long did = cursor.longValue(0);
                            int mid = cursor.intValue(5);
                            int topicId = 0;
                            unknownMessagesInTopics.remove((Integer) mid);

                            NativeByteBuffer data = cursor.byteBufferValue(1);
                            if (data != null) {
                                TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                message.readAttachPath(data, getUserConfig().clientUserId);
                                data.reuse();
                                addFilesToDelete(message, filesToDelete, idsToDelete, namesToDelete, false);
                                if (message.action instanceof TLRPC.TL_messageActionTopicCreate) {
                                    if (topicsToDelete == null) {
                                        topicsToDelete = new ArrayList<>();
                                    }
                                    topicsToDelete.add(TopicKey.of(did, message.id));
                                }
                                topicId = MessageObject.getTopicId(message, isForum(did));
                            }
                            if (topicId != 0) {
                                TopicKey topicKey = TopicKey.of(dialogId, topicId);

                                int read_state = cursor.intValue(2);
                                int[] count = topicsMessagesToUpdate.get(topicKey);
                                if (count == null) {
                                    count = new int[3];
                                    topicsMessagesToUpdate.put(topicKey, count);
                                }
                                count[2]++;
                                if (cursor.intValue(3) == 0) {
                                    if (read_state < 2) {
                                        count[1]++;
                                    }
                                    if (read_state == 0 || read_state == 2) {
                                        count[0]++;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        checkSQLException(e);
                    }
                    cursor.dispose();
                    cursor = null;
                }

                database.beginTransaction();
                for (int i = 0; i < 3; i++) {
                    if (i == 0) {
                        if (dialogId != 0) {
                            state = getMessagesStorage().getDatabase().executeFast("UPDATE messages_v2 SET replydata = ? WHERE reply_to_message_id IN(?) AND uid = ?");
                        } else {
                            state = getMessagesStorage().getDatabase().executeFast("UPDATE messages_v2 SET replydata = ? WHERE reply_to_message_id IN(?) AND is_channel = 0");
                        }
                    } else if (i == 1) {
                        if (dialogId != 0) {
                            state = getMessagesStorage().getDatabase().executeFast("UPDATE scheduled_messages_v2 SET replydata = ? WHERE reply_to_message_id IN(?) AND uid = ?");
                        } else {
                            state = getMessagesStorage().getDatabase().executeFast("UPDATE scheduled_messages_v2 SET replydata = ? WHERE reply_to_message_id IN(?)");
                        }
                    } else {
                        if (dialogId == 0) {
                            continue;
                        }
                        state = getMessagesStorage().getDatabase().executeFast("UPDATE messages_topics SET replydata = ? WHERE reply_to_message_id IN(?) AND uid = ?");
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
                    state = null;
                    database.commitTransaction();
                    data.reuse();
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
                    cursor = null;

                    dialogsIds.add(did);
                    state = database.executeFast("UPDATE dialogs SET unread_count = ?, unread_count_i = ? WHERE did = ?");
                    state.requery();
                    state.bindInteger(1, Math.max(0, old_unread_count - counts[0]));
                    state.bindInteger(2, Math.max(0, old_mentions_count - counts[1]));
                    state.bindLong(3, did);
                    state.step();
                    state.dispose();
                    state = null;
                }

                if (!topicsMessagesToUpdate.isEmpty()) {
                    HashSet<Long> dialogsToCheck = null;
                    for (TopicKey topicKey : topicsMessagesToUpdate.keySet()) {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT unread_count, unread_mentions, total_messages_count FROM topics WHERE did = %d AND topic_id = %d", topicKey.dialogId, topicKey.topicId));
                        int old_unread_count = 0;
                        int old_mentions_count = 0;
                        int old_total_count = 0;
                        if (cursor.next()) {
                            old_unread_count = cursor.intValue(0);
                            old_mentions_count = cursor.intValue(1);
                            old_total_count = cursor.intValue(2);
                        }
                        cursor.dispose();
                        cursor = null;

                        int[] counts = topicsMessagesToUpdate.get(topicKey);
                        int newUnreadCount = Math.max(0, old_unread_count - counts[0]);
                        int newUnreadMentionsCount = Math.max(0, old_mentions_count - counts[1]);
                        int newTotalCount = Math.max(0, old_total_count - counts[2]);
                        if (BuildVars.DEBUG_PRIVATE_VERSION && newUnreadMentionsCount > 0) {
                            FileLog.d("(markMessagesAsDeletedInternal) new unread mentions " + newUnreadMentionsCount + " for dialog_id=" + topicKey.dialogId + " topic_id=" + topicKey.topicId);
                        }
                        state = database.executeFast("UPDATE topics SET unread_count = ?, unread_mentions = ?, total_messages_count = ? WHERE did = ? AND topic_id = ?");
                        state.requery();
                        state.bindInteger(1, newUnreadCount);
                        state.bindInteger(2, newUnreadMentionsCount);
                        state.bindLong(3, newTotalCount);
                        state.bindLong(4, topicKey.dialogId);
                        state.bindLong(5, topicKey.topicId);

                        state.step();
                        state.dispose();
                        state = null;

                        if (newUnreadCount == 0) {
                            if (dialogsToCheck == null) {
                                dialogsToCheck = new HashSet<>();
                            }
                            dialogsToCheck.add(topicKey.dialogId);
                        }

                        TopicsController.TopicUpdate topicUpdate = new TopicsController.TopicUpdate();
                        topicUpdate.dialogId = topicKey.dialogId;
                        topicUpdate.topicId = topicKey.topicId;
                        topicUpdate.unreadCount = newUnreadCount;
                        topicUpdate.totalMessagesCount = newTotalCount;
                        topicUpdate.onlyCounters = true;
                        if (topicUpdatesInUi == null) {
                            topicUpdatesInUi = new ArrayList<>();
                        }
                        topicUpdatesInUi.add(topicUpdate);
                    }
                    if (dialogsToCheck != null) {
                        for (Long dialogToResert : dialogsToCheck) {
                            resetForumBadgeIfNeed(dialogToResert);
                        }
                    }
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
                    cursor = null;
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
                        cursor = null;
                    }
                    database.executeFast(String.format(Locale.US, "DELETE FROM messages_v2 WHERE mid IN(%s) AND uid = %d", ids, did)).stepThis().dispose();
                    database.executeFast(String.format(Locale.US, "DELETE FROM messages_topics WHERE mid IN(%s) AND uid = %d", ids, did)).stepThis().dispose();
                    database.executeFast(String.format(Locale.US, "DELETE FROM polls_v2 WHERE mid IN(%s) AND uid = %d", ids, did)).stepThis().dispose();
                    database.executeFast(String.format(Locale.US, "DELETE FROM bot_keyboard WHERE mid IN(%s) AND uid = %d", ids, did)).stepThis().dispose();
                    database.executeFast(String.format(Locale.US, "DELETE FROM bot_keyboard_topics WHERE mid IN(%s) AND uid = %d", ids, did)).stepThis().dispose();
                    if (unknownMessages.isEmpty()) {
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
                        cursor = null;
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
                            state = null;
                        }
                    }
                    if (unknownMessagesInTopics.isEmpty()) {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, topic_id, type FROM media_topics WHERE mid IN(%s) AND uid = %d", ids, did));
                        SparseArray<HashMap<TopicKey, Integer>> mediaCounts = null;
                        while (cursor.next()) {
                            long uid = cursor.longValue(0);
                            int topicId = cursor.intValue(1);
                            int type = cursor.intValue(2);
                            TopicKey topicKey = TopicKey.of(uid, topicId);
                            if (mediaCounts == null) {
                                mediaCounts = new SparseArray<>();
                            }
                            HashMap<TopicKey, Integer> counts = mediaCounts.get(type);
                            Integer count;
                            if (counts == null) {
                                counts = new HashMap<>();
                                count = 0;
                                mediaCounts.put(type, counts);
                            } else {
                                count = counts.get(topicKey);
                            }
                            if (count == null) {
                                count = 0;
                            }
                            count++;
                            counts.put(topicKey, count);
                        }
                        cursor.dispose();
                        cursor = null;
                        if (mediaCounts != null) {
                            state = database.executeFast("REPLACE INTO media_counts_topics VALUES(?, ?, ?, ?, ?)");
                            for (int c = 0, N3 = mediaCounts.size(); c < N3; c++) {
                                int type = mediaCounts.keyAt(c);
                                HashMap<TopicKey, Integer> value = mediaCounts.valueAt(c);
                                Iterator<TopicKey> iterator = value.keySet().iterator();
                                while (iterator.hasNext()) {
                                    TopicKey topicKey = iterator.next();
                                    int count = -1;
                                    int old = 0;
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT count, old FROM media_counts_topics WHERE uid = %d AND topic_id = %d AND type = %d LIMIT 1", topicKey.dialogId, topicKey.topicId, type));
                                    if (cursor.next()) {
                                        count = cursor.intValue(0);
                                        old = cursor.intValue(1);
                                    }
                                    cursor.dispose();
                                    if (count != -1) {
                                        state.requery();
                                        count = Math.max(0, count - value.get(topicKey));
                                        state.bindLong(1, topicKey.dialogId);
                                        state.bindLong(2, topicKey.topicId);
                                        state.bindInteger(3, type);
                                        state.bindInteger(4, count);
                                        state.bindInteger(5, old);
                                        state.step();
                                    }
                                }
                            }
                            state.dispose();
                            state = null;
                        }
                    }
                    database.executeFast(String.format(Locale.US, "DELETE FROM media_v4 WHERE mid IN(%s) AND uid = %d", ids, did)).stepThis().dispose();
                    database.executeFast(String.format(Locale.US, "DELETE FROM media_topics WHERE mid IN(%s) AND uid = %d", ids, did)).stepThis().dispose();
                }
                database.executeFast(String.format(Locale.US, "DELETE FROM messages_seq WHERE mid IN(%s)", ids)).stepThis().dispose();
                if (!unknownMessages.isEmpty()) {
                    if (dialogId == 0) {
                        database.executeFast("UPDATE media_counts_v2 SET old = 1 WHERE 1").stepThis().dispose();
                    } else {
                        database.executeFast(String.format(Locale.US, "UPDATE media_counts_v2 SET old = 1 WHERE uid = %d", dialogId)).stepThis().dispose();
                    }
                }
                if (!unknownMessagesInTopics.isEmpty()) {
                    if (dialogId == 0) {
                        database.executeFast("UPDATE media_counts_topics SET old = 1 WHERE 1").stepThis().dispose();
                    } else {
                        database.executeFast(String.format(Locale.US, "UPDATE media_counts_topics SET old = 1 WHERE uid = %d", dialogId)).stepThis().dispose();
                    }
                }
                getMediaDataController().clearBotKeyboard(null, messages);

                if (dialogsToUpdate.size() != 0) {
                    resetAllUnreadCounters(false);
                }
                updateWidgets(dialogsIds);

                if (topicsToDelete != null) {
                    for (int i = 0; i < topicsToDelete.size(); i++) {
                        TopicKey topicKey = topicsToDelete.get(i);
                        database.executeFast(String.format(Locale.US, "DELETE FROM topics WHERE did = %d AND topic_id = %d", topicKey.dialogId, topicKey.topicId)).stepThis().dispose();
                    }
                    getMessagesController().getTopicsController().onTopicsDeletedServerSide(topicsToDelete);
                }
                if (topicUpdatesInUi != null) {
                    getMessagesController().getTopicsController().processUpdate(topicUpdatesInUi);
                }
            }
            return dialogsIds;
        } catch (Exception e) {
            checkSQLException(e);
        } finally {
            if (database != null) {
                database.commitTransaction();
            }
            if (cursor != null) {
                cursor.dispose();
            }
            if (state != null) {
                state.dispose();
            }
        }
        return null;
    }

    private void updateDialogsWithDeletedMessagesInternal(long originalDialogId, long channelId, ArrayList<Integer> messages, ArrayList<Long> additionalDialogsToUpdate) {
        SQLitePreparedStatement state = null;
        SQLiteCursor cursor = null;
        try {
            ArrayList<Long> dialogsToUpdate = new ArrayList<>();
            if (!messages.isEmpty()) {
                if (channelId != 0) {
                    dialogsToUpdate.add(-channelId);
                    state = database.executeFast("UPDATE dialogs SET (last_mid, last_mid_group) = (SELECT mid, group_id FROM messages_v2 WHERE uid = ? AND date = (SELECT MAX(date) FROM messages_v2 WHERE uid = ?)) WHERE did = ?");
                } else {
                    if (originalDialogId == 0) {
                        String ids = TextUtils.join(",", messages);
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT did FROM dialogs WHERE last_mid IN(%s) AND flags = 0", ids));
                        while (cursor.next()) {
                            dialogsToUpdate.add(cursor.longValue(0));
                        }
                        cursor.dispose();
                        cursor = null;
                    } else {
                        dialogsToUpdate.add(originalDialogId);
                    }
                    state = database.executeFast("UPDATE dialogs SET (last_mid, last_mid_group) = (SELECT mid, group_id FROM messages_v2 WHERE uid = ? AND date = (SELECT MAX(date) FROM messages_v2 WHERE uid = ? AND date != 0)) WHERE did = ?");
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
                state = null;
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
            LongSparseArray<Long> groupsToLoad = new LongSparseArray<>();

            cursor = database.queryFinalized(String.format(Locale.US, "SELECT d.did, d.last_mid, d.unread_count, d.date, m.data, m.read_state, m.mid, m.send_state, m.date, d.pts, d.inbox_max, d.outbox_max, d.pinned, d.unread_count_i, d.flags, d.folder_id, d.data, d.unread_reactions, d.last_mid_group, d.ttl_period FROM dialogs as d LEFT JOIN messages_v2 as m ON d.last_mid = m.mid AND d.did = m.uid AND d.last_mid_group IS NULL WHERE d.did IN(%s)", ids));
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
                long groupMessagesId = cursor.longValue(18);
                if (groupMessagesId != 0) {
                    groupsToLoad.put(dialogId, groupMessagesId);
                }
                dialog.ttl_period = cursor.intValue(19);

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

                    addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);
                }
                if (!DialogObject.isEncryptedDialog(dialogId)) {
                    if (dialog.read_inbox_max_id > dialog.top_message) {
                        dialog.read_inbox_max_id = 0;
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
            cursor = null;

            if (!groupsToLoad.isEmpty()) {
                StringBuilder whereClause = new StringBuilder();
                for (int i = 0; i < groupsToLoad.size(); ++i) {
                    whereClause.append("uid = ").append(groupsToLoad.keyAt(i)).append(" AND group_id = ").append(groupsToLoad.valueAt(i));
                    if (i + 1 < groupsToLoad.size()) {
                        whereClause.append(" OR ");
                    }
                }
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, data, read_state, mid, send_state, date, group_id FROM messages_v2 WHERE %s", whereClause));
                while (cursor.next()) {
                    long dialogId = cursor.longValue(0);
                    TLRPC.Dialog dialog = null;
                    for (int i = 0; i < dialogs.dialogs.size(); ++i) {
                        TLRPC.Dialog d = dialogs.dialogs.get(i);
                        if (d != null && d.id == dialogId) {
                            dialog = d;
                            break;
                        }
                    }
                    if (dialog == null) {
                        continue;
                    }
                    NativeByteBuffer data = cursor.byteBufferValue(1);
                    if (data != null) {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        message.readAttachPath(data, getUserConfig().clientUserId);
                        data.reuse();
                        MessageObject.setUnreadFlags(message, cursor.intValue(2));
                        message.id = cursor.intValue(3);
                        message.send_state = cursor.intValue(4);
                        int date = cursor.intValue(5);
                        if (date != 0) {
                            dialog.last_message_date = date;
                        }
                        message.dialog_id = dialog.id;
                        dialogs.messages.add(message);

                        addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);
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

            getMessagesController().getTopicsController().updateTopicsWithDeletedMessages(originalDialogId, messages);

            if (!dialogs.dialogs.isEmpty() || !encryptedChats.isEmpty()) {
                getMessagesController().processDialogsUpdate(dialogs, encryptedChats, true);
            }
        } catch (Exception e) {
            checkSQLException(e);
        } finally {
            if (database != null) {
                database.commitTransaction();
            }
            if (cursor != null) {
                cursor.dispose();
            }
            if (state != null) {
                state.dispose();
            }
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
        SQLiteCursor cursor = null;
        SQLitePreparedStatement state = null;
        try {
            ArrayList<Long> dialogsIds = new ArrayList<>();
            LongSparseArray<Integer[]> dialogsToUpdate = new LongSparseArray<>();

            ArrayList<File> filesToDelete = new ArrayList<>();
            ArrayList<String> namesToDelete = new ArrayList<>();
            ArrayList<Pair<Long, Integer>> idsToDelete = new ArrayList<>();
            long currentUser = getUserConfig().getClientUserId();

            cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, data, read_state, out, mention FROM messages_v2 WHERE uid = %d AND mid <= %d", -channelId, mid));

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
                checkSQLException(e);
            }
            cursor.dispose();
            cursor = null;

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
                cursor = null;

                dialogsIds.add(did);
                state = database.executeFast("UPDATE dialogs SET unread_count = ?, unread_count_i = ? WHERE did = ?");
                state.requery();
                state.bindInteger(1, Math.max(0, old_unread_count - counts[0]));
                state.bindInteger(2, Math.max(0, old_mentions_count - counts[1]));
                state.bindLong(3, did);
                state.step();
                state.dispose();
                state = null;
            }


            database.executeFast(String.format(Locale.US, "UPDATE chat_settings_v2 SET pinned = 0 WHERE uid = %d AND pinned <= %d", channelId, mid)).stepThis().dispose();
            database.executeFast(String.format(Locale.US, "DELETE FROM chat_pinned_v2 WHERE uid = %d AND mid <= %d", channelId, mid)).stepThis().dispose();
            int updatedCount = 0;
            cursor = database.queryFinalized("SELECT changes()");
            if (cursor.next()) {
                updatedCount = cursor.intValue(0);
            }
            cursor.dispose();
            cursor = null;
            if (updatedCount > 0) {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT count FROM chat_pinned_count WHERE uid = %d", -channelId));
                if (cursor.next()) {
                    int count = cursor.intValue(0);
                    state = database.executeFast("UPDATE chat_pinned_count SET count = ? WHERE uid = ?");
                    state.requery();
                    state.bindInteger(1, Math.max(0, count - updatedCount));
                    state.bindLong(2, -channelId);
                    state.step();
                    state.dispose();
                    state = null;
                }
                cursor.dispose();
                cursor = null;
            }

            database.executeFast(String.format(Locale.US, "DELETE FROM messages_v2 WHERE uid = %d AND mid <= %d", -channelId, mid)).stepThis().dispose();
            database.executeFast(String.format(Locale.US, "DELETE FROM messages_topics WHERE uid = %d AND mid <= %d", -channelId, mid)).stepThis().dispose();
            database.executeFast(String.format(Locale.US, "DELETE FROM media_v4 WHERE uid = %d AND mid <= %d", -channelId, mid)).stepThis().dispose();
            database.executeFast(String.format(Locale.US, "UPDATE media_counts_v2 SET old = 1 WHERE uid = %d", -channelId)).stepThis().dispose();
            database.executeFast(String.format(Locale.US, "UPDATE media_counts_topics SET old = 1 WHERE uid = %d", -channelId)).stepThis().dispose();
            updateWidgets(dialogsIds);
            return dialogsIds;
        } catch (Exception e) {
            checkSQLException(e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
            if (state != null) {
                state.dispose();
            }
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

    private void doneHolesInTable(String table, long did, int max_id, int thread_message_id) throws Exception {
        if (thread_message_id != 0) {
            if (max_id == 0) {
                database.executeFast(String.format(Locale.US, "DELETE FROM " + table + " WHERE uid = %d AND topic_id = %d", did, thread_message_id)).stepThis().dispose();
            } else {
                database.executeFast(String.format(Locale.US, "DELETE FROM " + table + " WHERE uid = %d AND topic_id = %d AND start = 0", did, thread_message_id)).stepThis().dispose();
            }
        } else {
            if (max_id == 0) {
                database.executeFast(String.format(Locale.US, "DELETE FROM " + table + " WHERE uid = %d", did)).stepThis().dispose();
            } else {
                database.executeFast(String.format(Locale.US, "DELETE FROM " + table + " WHERE uid = %d AND start = 0", did)).stepThis().dispose();
            }
        }
        SQLitePreparedStatement state = null;
        try {
            if (thread_message_id != 0) {
                state = database.executeFast("REPLACE INTO " + table + " VALUES(?, ?, ?, ?)");
            } else {
                state = database.executeFast("REPLACE INTO " + table + " VALUES(?, ?, ?)");
            }
            state.requery();
            int pointer = 1;
            state.bindLong(pointer++, did);
            if (thread_message_id != 0) {
                state.bindInteger(pointer++, thread_message_id);
            }
            state.bindInteger(pointer++, 1);
            state.bindInteger(pointer++, 1);
            state.step();
        } catch (Exception e) {
            throw e;
        } finally {
            if (state != null) {
                state.dispose();
            }
        }
    }

    public void doneHolesInMedia(long did, int max_id, int type, int thread_message_id) throws Exception {
        if (type == -1) {
            if (thread_message_id != 0) {
                if (max_id == 0) {
                    database.executeFast(String.format(Locale.US, "DELETE FROM media_holes_topics WHERE uid = %d AND topic_id = %d", did, thread_message_id)).stepThis().dispose();
                } else {
                    database.executeFast(String.format(Locale.US, "DELETE FROM media_holes_topics WHERE uid = %d AND topic_id = %d AND start = 0", did, thread_message_id)).stepThis().dispose();
                }
            } else {
                if (max_id == 0) {
                    database.executeFast(String.format(Locale.US, "DELETE FROM media_holes_v2 WHERE uid = %d", did)).stepThis().dispose();
                } else {
                    database.executeFast(String.format(Locale.US, "DELETE FROM media_holes_v2 WHERE uid = %d AND start = 0", did)).stepThis().dispose();
                }
            }
            SQLitePreparedStatement state = null;
            try {
                if (thread_message_id != 0) {
                    state = database.executeFast("REPLACE INTO media_holes_topics VALUES(?, ?, ?, ?, ?)");
                } else {
                    state = database.executeFast("REPLACE INTO media_holes_v2 VALUES(?, ?, ?, ?)");
                }
                for (int a = 0; a < MediaDataController.MEDIA_TYPES_COUNT; a++) {
                    state.requery();
                    int pointer = 1;
                    state.bindLong(pointer++, did);
                    if (thread_message_id != 0) {
                        state.bindInteger(pointer++, thread_message_id);
                    }
                    state.bindInteger(pointer++, a);
                    state.bindInteger(pointer++, 1);
                    state.bindInteger(pointer++, 1);
                    state.step();
                }
            } catch (Exception e) {
                throw e;
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }

        } else {
            if (thread_message_id != 0) {
                if (max_id == 0) {
                    database.executeFast(String.format(Locale.US, "DELETE FROM media_holes_topics WHERE uid = %d AND topic_id = %d AND type = %d", did, thread_message_id, type)).stepThis().dispose();
                } else {
                    database.executeFast(String.format(Locale.US, "DELETE FROM media_holes_topics WHERE uid = %d AND topic_id = %d AND type = %d AND start = 0", did, thread_message_id, type)).stepThis().dispose();
                }
            } else {
                if (max_id == 0) {
                    database.executeFast(String.format(Locale.US, "DELETE FROM media_holes_v2 WHERE uid = %d AND type = %d", did, type)).stepThis().dispose();
                } else {
                    database.executeFast(String.format(Locale.US, "DELETE FROM media_holes_v2 WHERE uid = %d AND type = %d AND start = 0", did, type)).stepThis().dispose();
                }
            }
            SQLitePreparedStatement state = null;
            try {
                if (thread_message_id != 0) {
                    state = database.executeFast("REPLACE INTO media_holes_topics VALUES(?, ?, ?, ?, ?)");
                } else {
                    state = database.executeFast("REPLACE INTO media_holes_v2 VALUES(?, ?, ?, ?)");
                }
                state.requery();
                int pointer = 1;
                state.bindLong(pointer++, did);
                if (thread_message_id != 0) {
                    state.bindInteger(pointer++, thread_message_id);
                }
                state.bindInteger(pointer++, type);
                state.bindInteger(pointer++, 1);
                state.bindInteger(pointer++, 1);
                state.step();
                state.dispose();
            } catch (Exception e) {
                throw e;
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
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

    public void closeHolesInMedia(long did, int minId, int maxId, int type, int topicId) {
        SQLiteCursor cursor = null;
        SQLitePreparedStatement state = null;
        try {
            boolean ok = false;
            if (topicId != 0) {
                if (type < 0) {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT type, start, end FROM media_holes_topics WHERE uid = %d AND topic_id = %d AND type >= 0 AND ((end >= %d AND end <= %d) OR (start >= %d AND start <= %d) OR (start >= %d AND end <= %d) OR (start <= %d AND end >= %d))", did, topicId, minId, maxId, minId, maxId, minId, maxId, minId, maxId));
                } else {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT type, start, end FROM media_holes_topics WHERE uid = %d AND topic_id = %d AND type = %d AND ((end >= %d AND end <= %d) OR (start >= %d AND start <= %d) OR (start >= %d AND end <= %d) OR (start <= %d AND end >= %d))", did, topicId, type, minId, maxId, minId, maxId, minId, maxId, minId, maxId));
                }
            } else {
                if (type < 0) {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT type, start, end FROM media_holes_v2 WHERE uid = %d AND type >= 0 AND ((end >= %d AND end <= %d) OR (start >= %d AND start <= %d) OR (start >= %d AND end <= %d) OR (start <= %d AND end >= %d))", did, minId, maxId, minId, maxId, minId, maxId, minId, maxId));
                } else {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT type, start, end FROM media_holes_v2 WHERE uid = %d AND type = %d AND ((end >= %d AND end <= %d) OR (start >= %d AND start <= %d) OR (start >= %d AND end <= %d) OR (start <= %d AND end >= %d))", did, type, minId, maxId, minId, maxId, minId, maxId, minId, maxId));
                }
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
            cursor = null;
            if (holes != null) {
                for (int a = 0; a < holes.size(); a++) {
                    Hole hole = holes.get(a);
                    if (maxId >= hole.end - 1 && minId <= hole.start + 1) {
                        if (topicId != 0) {
                            database.executeFast(String.format(Locale.US, "DELETE FROM media_holes_topics WHERE uid = %d AND topic_id = %d AND type = %d AND start = %d AND end = %d", did, topicId, hole.type, hole.start, hole.end)).stepThis().dispose();
                        } else {
                            database.executeFast(String.format(Locale.US, "DELETE FROM media_holes_v2 WHERE uid = %d AND type = %d AND start = %d AND end = %d", did, hole.type, hole.start, hole.end)).stepThis().dispose();
                        }
                    } else if (maxId >= hole.end - 1) {
                        if (hole.end != minId) {
                            try {
                                if (topicId != 0) {
                                    database.executeFast(String.format(Locale.US, "UPDATE media_holes_topics SET end = %d WHERE uid = %d AND topic_id = %d AND type = %d AND start = %d AND end = %d", minId, did, topicId, hole.type, hole.start, hole.end)).stepThis().dispose();
                                } else {
                                    database.executeFast(String.format(Locale.US, "UPDATE media_holes_v2 SET end = %d WHERE uid = %d AND type = %d AND start = %d AND end = %d", minId, did, hole.type, hole.start, hole.end)).stepThis().dispose();
                                }
                            } catch (Exception e) {
                                checkSQLException(e, false);
                            }
                        }
                    } else if (minId <= hole.start + 1) {
                        if (hole.start != maxId) {
                            try {
                                if (topicId != 0) {
                                    database.executeFast(String.format(Locale.US, "UPDATE media_holes_topics SET start = %d WHERE uid = %d AND topic_id = %d AND type = %d AND start = %d AND end = %d", maxId, did, topicId, hole.type, hole.start, hole.end)).stepThis().dispose();
                                } else {
                                    database.executeFast(String.format(Locale.US, "UPDATE media_holes_v2 SET start = %d WHERE uid = %d AND type = %d AND start = %d AND end = %d", maxId, did, hole.type, hole.start, hole.end)).stepThis().dispose();
                                }
                            } catch (Exception e) {
                                checkSQLException(e, false);
                            }
                        }
                    } else {
                        if (topicId != 0) {
                            database.executeFast(String.format(Locale.US, "DELETE FROM media_holes_topics WHERE uid = %d AND topic_id = %d AND type = %d AND start = %d AND end = %d", did, topicId, hole.type, hole.start, hole.end)).stepThis().dispose();
                            state = database.executeFast("REPLACE INTO media_holes_topics VALUES(?, ?, ?, ?, ?)");
                        } else {
                            database.executeFast(String.format(Locale.US, "DELETE FROM media_holes_v2 WHERE uid = %d AND type = %d AND start = %d AND end = %d", did, hole.type, hole.start, hole.end)).stepThis().dispose();
                            state = database.executeFast("REPLACE INTO media_holes_v2 VALUES(?, ?, ?, ?)");
                        }
                        state.requery();
                        int pointer = 1;
                        state.bindLong(pointer++, did);
                        if (topicId != 0) {
                            state.bindInteger(pointer++, topicId);
                        }
                        state.bindInteger(pointer++, hole.type);
                        state.bindInteger(pointer++, hole.start);
                        state.bindInteger( pointer++, minId);
                        state.step();
                        state.requery();
                        state.bindLong(1, did);
                        state.bindInteger(2, hole.type);
                        state.bindInteger(3, maxId);
                        state.bindInteger(4, hole.end);
                        state.step();
                        state.dispose();
                        state = null;
                    }
                }
            }
        } catch (Exception e) {
            checkSQLException(e);
        } finally {
            if (state != null) {
                state.dispose();
            }
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    private void closeHolesInTable(String table, long did, int minId, int maxId, int thread_message_id) {
        SQLiteCursor cursor = null;
        SQLitePreparedStatement state = null;
        try {
            boolean ok = false;
            if (thread_message_id != 0) {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT start, end FROM " + table + " WHERE uid = %d AND topic_id = %d AND ((end >= %d AND end <= %d) OR (start >= %d AND start <= %d) OR (start >= %d AND end <= %d) OR (start <= %d AND end >= %d))", did, thread_message_id, minId, maxId, minId, maxId, minId, maxId, minId, maxId));
            } else {
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT start, end FROM " + table + " WHERE uid = %d AND ((end >= %d AND end <= %d) OR (start >= %d AND start <= %d) OR (start >= %d AND end <= %d) OR (start <= %d AND end >= %d))", did, minId, maxId, minId, maxId, minId, maxId, minId, maxId));
            }
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
            cursor = null;
            if (holes != null) {
                for (int a = 0; a < holes.size(); a++) {
                    Hole hole = holes.get(a);
                    if (maxId >= hole.end - 1 && minId <= hole.start + 1) {
                        if (thread_message_id != 0) {
                            database.executeFast(String.format(Locale.US, "DELETE FROM " + table + " WHERE uid = %d AND topic_id = %d AND start = %d AND end = %d", did, thread_message_id, hole.start, hole.end)).stepThis().dispose();
                        } else {
                            database.executeFast(String.format(Locale.US, "DELETE FROM " + table + " WHERE uid = %d AND start = %d AND end = %d", did, hole.start, hole.end)).stepThis().dispose();
                        }
                    } else if (maxId >= hole.end - 1) {
                        if (hole.end != minId) {
                            try {
                                if (thread_message_id != 0) {
                                    database.executeFast(String.format(Locale.US, "UPDATE " + table + " SET end = %d WHERE uid = %d AND topic_id = %d AND start = %d AND end = %d", minId, did, thread_message_id, hole.start, hole.end)).stepThis().dispose();
                                } else {
                                    database.executeFast(String.format(Locale.US, "UPDATE " + table + " SET end = %d WHERE uid = %d AND start = %d AND end = %d", minId, did, hole.start, hole.end)).stepThis().dispose();
                                }
                            } catch (Exception e) {
                                checkSQLException(e, false);
                            }
                        }
                    } else if (minId <= hole.start + 1) {
                        if (hole.start != maxId) {
                            try {
                                if (thread_message_id != 0) {
                                    database.executeFast(String.format(Locale.US, "UPDATE " + table + " SET start = %d WHERE uid = %d AND topic_id = %d AND start = %d AND end = %d", maxId, did, thread_message_id, hole.start, hole.end)).stepThis().dispose();
                                } else {
                                    database.executeFast(String.format(Locale.US, "UPDATE " + table + " SET start = %d WHERE uid = %d AND start = %d AND end = %d", maxId, did, hole.start, hole.end)).stepThis().dispose();
                                }
                            } catch (Exception e) {
                                checkSQLException(e, false);
                            }
                        }
                    } else {
                        if (thread_message_id != 0) {
                            database.executeFast(String.format(Locale.US, "DELETE FROM " + table + " WHERE uid = %d AND topic_id = %d AND start = %d AND end = %d", did, thread_message_id, hole.start, hole.end)).stepThis().dispose();
                            state = database.executeFast("REPLACE INTO " + table + " VALUES(?, ?, ?, ?)");
                        } else {
                            database.executeFast(String.format(Locale.US, "DELETE FROM " + table + " WHERE uid = %d AND start = %d AND end = %d", did, hole.start, hole.end)).stepThis().dispose();
                            state = database.executeFast("REPLACE INTO " + table + " VALUES(?, ?, ?)");
                        }
                        int pointer = 1;
                        state.requery();
                        state.bindLong(pointer++, did);
                        if (thread_message_id != 0) {
                            state.bindInteger(pointer++, thread_message_id);
                        }
                        state.bindInteger(pointer++, hole.start);
                        state.bindInteger(pointer++, minId);
                        state.step();
                        state.requery();
                        pointer = 1;
                        state.bindLong(pointer++, did);
                        if (thread_message_id != 0) {
                            state.bindInteger(pointer++, thread_message_id);
                        }
                        state.bindInteger(pointer++, maxId);
                        state.bindInteger(pointer++, hole.end);
                        state.step();
                        state.dispose();
                        state = null;
                    }
                }
            }
        } catch (Exception e) {
            checkSQLException(e);
        } finally {
            if (state != null) {
                state.dispose();
            }
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    public void replaceMessageIfExists(TLRPC.Message message, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats, boolean broadcast) {
        if (message == null) {
            return;
        }
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            SQLitePreparedStatement state2 = null;
            try {
                SQLiteCursor cursor = null;
                int readState = 0;
                NativeByteBuffer customParams = null;
                try {
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, read_state, custom_params FROM messages_v2 WHERE mid = %d AND uid = %d LIMIT 1", message.id, MessageObject.getDialogId(message)));
                    if (!cursor.next()) {
                        cursor.dispose();
                        return;
                    }
                    readState = cursor.intValue(1);
                    customParams = cursor.byteBufferValue(2);
                } catch (Exception e) {
                    checkSQLException(e);
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }

                database.beginTransaction();

                if (message.dialog_id == 0) {
                    MessageObject.getDialogId(message);
                }

                fixUnsupportedMedia(message);
                NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                message.serializeToStream(data);

                for (int i = 0; i < 2; i++) {
                    boolean isTopic = i == 1;
                    int topicId = MessageObject.getTopicId(message, isForum(message.dialog_id));
                    if (isTopic && topicId == 0) {
                        continue;
                    }
                    if (isTopic) {
                        state = database.executeFast("REPLACE INTO messages_topics VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, 0, ?)");
                    } else {
                        state = database.executeFast("REPLACE INTO messages_v2 VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, 0, ?, ?)");
                    }
                    state.requery();

                    int pointer = 1;
                    state.bindInteger(pointer++, message.id);
                    state.bindLong(pointer++, message.dialog_id);
                    if (isTopic) {
                        state.bindInteger(pointer++, topicId);
                    }
                    state.bindInteger(pointer++, readState);
                    state.bindInteger(pointer++, message.send_state);
                    state.bindInteger(pointer++, message.date);
                    state.bindByteBuffer(pointer++, data);
                    state.bindInteger(pointer++, (MessageObject.isOut(message) || message.from_scheduled ? 1 : 0));
                    state.bindInteger(pointer++, message.ttl);
                    if ((message.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                        state.bindInteger(pointer++, message.views);
                    } else {
                        state.bindInteger(pointer++, getMessageMediaType(message));
                    }
                    int flags = 0;
                    if (message.stickerVerified == 0) {
                        flags |= 1;
                    } else if (message.stickerVerified == 2) {
                        flags |= 2;
                    }
                    state.bindInteger(pointer++, flags);
                    state.bindInteger(pointer++, message.mentioned ? 1 : 0);
                    state.bindInteger(pointer++, message.forwards);
                    NativeByteBuffer repliesData = null;
                    if (message.replies != null) {
                        repliesData = new NativeByteBuffer(message.replies.getObjectSize());
                        message.replies.serializeToStream(repliesData);
                        state.bindByteBuffer(pointer++, repliesData);
                    } else {
                        state.bindNull(pointer++);
                    }
                    if (message.reply_to != null) {
                        state.bindInteger(pointer++, message.reply_to.reply_to_top_id != 0 ? message.reply_to.reply_to_top_id : message.reply_to.reply_to_msg_id);
                    } else {
                        state.bindInteger(pointer++, 0);
                    }
                    state.bindLong(pointer++, MessageObject.getChannelId(message));
                    if (customParams != null) {
                        state.bindByteBuffer(pointer++, customParams);
                    } else {
                        state.bindNull(pointer++);
                    }
                    if (!isTopic) {
                        if ((message.flags & 131072) != 0) {
                            state.bindLong(pointer++, message.grouped_id);
                        } else {
                            state.bindNull(pointer++);
                        }
                    }
                    state.step();
                    state.dispose();
                    state = null;
                    if (repliesData != null) {
                        repliesData.reuse();
                    }
                }

                if (MediaDataController.canAddMessageToMedia(message)) {
                    for (int i = 0; i < 2; i++) {
                        boolean isTopic = i == 1;
                        int topicId = MessageObject.getTopicId(message, isForum(message.dialog_id));
                        if (isTopic && topicId == 0) {
                            continue;
                        }
                        if (i == 0) {
                            state2 = database.executeFast("REPLACE INTO media_v4 VALUES(?, ?, ?, ?, ?)");
                        } else {
                            state2 = database.executeFast("REPLACE INTO media_topics VALUES(?, ?, ?, ?, ?, ?)");
                        }
                        int pointer = 1;
                        state2.requery();
                        state2.bindInteger(pointer++, message.id);
                        state2.bindLong(pointer++, message.dialog_id);
                        if (i != 0) {
                            state2.bindLong(pointer++, topicId);
                        }
                        state2.bindInteger(pointer++, message.date);
                        state2.bindInteger(pointer++, MediaDataController.getMediaType(message));
                        state2.bindByteBuffer(pointer++, data);
                        state2.step();
                        state2.dispose();
                        state2 = null;
                    }
                }

                if (customParams != null) {
                    customParams.reuse();
                }
                data.reuse();

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
                checkSQLException(e);
            } finally {
                if (database != null) {
                    database.commitTransaction();
                }
                if (state != null) {
                    state.dispose();
                }
                if (state2 != null) {
                    state2.dispose();
                }
            }
        });
    }

    // put messages in data base while load history
    public void putMessages(TLRPC.messages_Messages messages, long dialogId, int load_type, int max_id, boolean createDialog, boolean scheduled, int threadMessageId) {
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state_messages = null;
            SQLitePreparedStatement state_messages_topics = null;
            SQLitePreparedStatement state_media = null;
            SQLitePreparedStatement state_media_topics = null;
            SQLitePreparedStatement state_polls = null;
            SQLitePreparedStatement state_webpage = null;
            SQLitePreparedStatement state_tasks = null;
            SQLitePreparedStatement state3 = null;
            SQLiteCursor cursor = null;
            try {
                if (scheduled) {
                    database.executeFast(String.format(Locale.US, "DELETE FROM scheduled_messages_v2 WHERE uid = %d AND mid > 0", dialogId)).stepThis().dispose();
                    state_messages = database.executeFast("REPLACE INTO scheduled_messages_v2 VALUES(?, ?, ?, ?, ?, ?, NULL, 0)");
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
                    state_messages = null;

                    putUsersInternal(messages.users);
                    putChatsInternal(messages.chats);

                    database.commitTransaction();
                    broadcastScheduledMessagesChange(dialogId);
                } else {
                    int mentionCountUpdate = Integer.MAX_VALUE;
                    boolean isTopic = threadMessageId != 0;
                    String holesTableName = isTopic ? "messages_holes_topics" : "messages_holes";
                    if (messages.messages.isEmpty()) {
                        if (load_type == 0) {
                            doneHolesInTable(holesTableName, dialogId, max_id, threadMessageId);
                            doneHolesInMedia(dialogId, max_id, -1, threadMessageId);
                        }
                        return;
                    }
                    database.beginTransaction();

                    if (load_type == 0) {
                        int minId = messages.messages.get(messages.messages.size() - 1).id;
                        closeHolesInTable(holesTableName, dialogId, minId, max_id, threadMessageId);
                        closeHolesInMedia(dialogId, minId, max_id, -1, threadMessageId);
                    } else if (load_type == 1) {
                        int maxId = messages.messages.get(0).id;
                        closeHolesInTable(holesTableName, dialogId, max_id, maxId, threadMessageId);
                        closeHolesInMedia(dialogId, max_id, maxId, -1, threadMessageId);
                    } else if (load_type == 3 || load_type == 2 || load_type == 4) {
                        int maxId = max_id == 0 && load_type != 4 ? Integer.MAX_VALUE : messages.messages.get(0).id;
                        int minId = messages.messages.get(messages.messages.size() - 1).id;
                        closeHolesInTable(holesTableName, dialogId, minId, maxId, threadMessageId);
                        closeHolesInMedia(dialogId, minId, maxId, -1, threadMessageId);
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
                    Integer lastMessageId = null;
                    Long lastMessageGroupId = null;

                    state_messages_topics = database.executeFast("REPLACE INTO messages_topics VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, 0, ?)");
                    state_messages = database.executeFast("REPLACE INTO messages_v2 VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, 0, ?, ?)");
                    state_media = database.executeFast("REPLACE INTO media_v4 VALUES(?, ?, ?, ?, ?)");
                    state_media_topics = database.executeFast("REPLACE INTO media_topics VALUES(?, ?, ?, ?, ?, ?)");
                    state_polls = null;
                    state_webpage = null;
                    state_tasks = null;
                    int minDeleteTime = Integer.MAX_VALUE;
                    HashMap<TopicKey, TLRPC.Message> botKeyboards = null;
                    long channelId = 0;
                    for (int a = 0; a < count; a++) {
                        TLRPC.Message message = messages.messages.get(a);
                        if (lastMessageId == null && message != null || lastMessageId != null && lastMessageId < message.id) {
                            lastMessageId = message.id;
                            lastMessageGroupId = (message.flags & 131072) != 0 ? message.grouped_id : null;
                        }

                        if (channelId == 0) {
                            channelId = message.peer_id.channel_id;
                        }

                        if (load_type == -2) {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid, data, ttl, mention, read_state, send_state, custom_params FROM messages_v2 WHERE mid = %d AND uid = %d", message.id, MessageObject.getDialogId(message)));
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
                                    NativeByteBuffer customParams = cursor.byteBufferValue(6);
                                    MessageCustomParamsHelper.readLocalParams(message, customParams);
                                    if (customParams != null) {
                                        customParams.reuse();
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
                            cursor = null;
                            if (!exist) {
                                continue;
                            }
                        }

                        if (a == 0 && createDialog) {
                            int pinned = 0;
                            int mentions = 0;
                            int flags = 0;
                            cursor = database.queryFinalized("SELECT pinned, unread_count_i, flags FROM dialogs WHERE did = " + dialogId);
                            boolean exist;
                            if (exist = cursor.next()) {
                                pinned = cursor.intValue(0);
                                mentions = cursor.intValue(1);
                                flags = cursor.intValue(2);
                            }
                            cursor.dispose();
                            cursor = null;

                            if (exist) {
                                state3 = database.executeFast("UPDATE dialogs SET date = ?, last_mid = ?, last_mid_group = ?, inbox_max = ?, last_mid_i = ?, pts = ?, date_i = ? WHERE did = ?");
                                state3.bindInteger(1, message.date);
                                state3.bindInteger(2, message.id);
                                if (message != null && (message.flags & 131072) != 0) {
                                    state3.bindLong(3, message.grouped_id);
                                } else {
                                    state3.bindNull(3);
                                }
                                state3.bindInteger(4, message.id);
                                state3.bindInteger(5, message.id);
                                state3.bindInteger(6, messages.pts);
                                state3.bindInteger(7, message.date);
                                state3.bindLong(8, dialogId);
                            } else {
                                state3 = database.executeFast("REPLACE INTO dialogs VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
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
                                if (message != null && (message.flags & 131072) != 0) {
                                    state3.bindLong(16, message.grouped_id);
                                } else {
                                    state3.bindNull(16);
                                }
                                state3.bindInteger(17, 0);
                                unknownDialogsIds.put(dialogId, true);
                            }
                            state3.step();
                            state3.dispose();
                            state3 = null;
                        }

                        fixUnsupportedMedia(message);
                        NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                        message.serializeToStream(data);

                        for (int i = 0; i < 2; i++) {
                            boolean isTopicMessage = i == 1;
                            int topicId = threadMessageId;
                            if (isTopicMessage && topicId == 0) {
                                topicId = MessageObject.getTopicId(message, isForum(message.dialog_id));
                            }
                            if (isTopicMessage && topicId == 0) {
                                continue;
                            }

                            SQLitePreparedStatement currentState = isTopicMessage ? state_messages_topics : state_messages;
                            currentState.requery();

                            int pointer = 1;
                            currentState.bindInteger(pointer++, message.id);
                            currentState.bindLong(pointer++, dialogId);
                            if (isTopicMessage) {
                                currentState.bindInteger(pointer++, topicId);
                            }
                            currentState.bindInteger(pointer++, MessageObject.getUnreadFlags(message));
                            currentState.bindInteger(pointer++, message.send_state);
                            currentState.bindInteger(pointer++, message.date);
                            currentState.bindByteBuffer(pointer++, data);
                            currentState.bindInteger(pointer++, (MessageObject.isOut(message) || message.from_scheduled ? 1 : 0));
                            currentState.bindInteger(pointer++, message.ttl);
                            if ((message.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                                currentState.bindInteger(pointer++, message.views);
                            } else {
                                currentState.bindInteger(pointer++, getMessageMediaType(message));
                            }
                            int flags = 0;
                            if (message.stickerVerified == 0) {
                                flags |= 1;
                            } else if (message.stickerVerified == 2) {
                                flags |= 2;
                            }
                            currentState.bindInteger(pointer++, flags);
                            currentState.bindInteger(pointer++, message.mentioned ? 1 : 0);
                            currentState.bindInteger(pointer++, message.forwards);
                            NativeByteBuffer repliesData = null;
                            if (message.replies != null) {
                                repliesData = new NativeByteBuffer(message.replies.getObjectSize());
                                message.replies.serializeToStream(repliesData);
                                currentState.bindByteBuffer(pointer++, repliesData);
                            } else {
                                currentState.bindNull(pointer++);
                            }
                            if (message.reply_to != null) {
                                currentState.bindInteger(pointer++, message.reply_to.reply_to_top_id != 0 ? message.reply_to.reply_to_top_id : message.reply_to.reply_to_msg_id);
                            } else {
                                currentState.bindInteger(pointer++, 0);
                            }
                            currentState.bindLong(pointer++, MessageObject.getChannelId(message));
                            NativeByteBuffer customParams = MessageCustomParamsHelper.writeLocalParams(message);
                            if (customParams == null) {
                                currentState.bindNull(pointer++);
                            } else {
                                currentState.bindByteBuffer(pointer++, customParams);
                            }
                            if (!isTopicMessage) {
                                if ((message.flags & 131072) != 0) {
                                    currentState.bindLong(pointer++, message.grouped_id);
                                } else {
                                    currentState.bindNull(pointer++);
                                }
                            }
                            currentState.step();

                            if (repliesData != null) {
                                repliesData.reuse();
                            }
                            if (customParams != null) {
                                customParams.reuse();
                            }
                        }

                        if (threadMessageId == 0 || load_type == -2) {
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
                                    checkSQLException(e2);
                                }
                            }
                        }
                        int topicId = MessageObject.getTopicId(message, isForum(message.dialog_id));
                        if (threadMessageId != 0 || (load_type == -2 && topicId != 0)) {
                            if (MediaDataController.canAddMessageToMedia(message)) {
                                state_media_topics.requery();
                                state_media_topics.bindInteger(1, message.id);
                                state_media_topics.bindLong(2, dialogId);
                                state_media_topics.bindLong(3, threadMessageId != 0 ? threadMessageId : topicId);
                                state_media_topics.bindInteger(4, message.date);
                                state_media_topics.bindInteger(5, MediaDataController.getMediaType(message));
                                state_media_topics.bindByteBuffer(6, data);
                                state_media_topics.step();
                            }
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
                            TopicKey topicKey = TopicKey.of(dialogId, MessageObject.getTopicId(message, isForum(dialogId)));
                            TLRPC.Message currentBotKeyboard = botKeyboards == null ? null : botKeyboards.get(topicKey);
                            if (currentBotKeyboard == null || currentBotKeyboard.id < message.id) {
                                if (botKeyboards == null) {
                                    botKeyboards = new HashMap<>();
                                }
                                botKeyboards.put(topicKey, message);
                            }
                        }
                    }
                    state_messages.dispose();
                    state_messages = null;
                    state_messages_topics.dispose();
                    state_messages_topics = null;
                    state_media.dispose();
                    state_media = null;
                    if (state_webpage != null) {
                        state_webpage.dispose();
                        state_webpage = null;
                    }
                    if (state_tasks != null) {
                        state_tasks.dispose();
                        getMessagesController().didAddedNewTask(minDeleteTime, 0, null);
                        state_tasks = null;
                    }
                    if (state_polls != null) {
                        state_polls.dispose();
                        state_polls = null;
                    }
                    if (botKeyboards != null) {
                        Iterator<TopicKey> iterator = botKeyboards.keySet().iterator();
                        while (iterator.hasNext()) {
                            TopicKey topicKey = iterator.next();
                            getMediaDataController().putBotKeyboard(topicKey, botKeyboards.get(topicKey));
                        }
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

                    boolean updateDialogs = false;
                    if (lastMessageId != null) {
                        database.executeFast(String.format(Locale.US, "UPDATE dialogs SET last_mid_group = %s WHERE did = %d AND last_mid <= %d", lastMessageGroupId == null ? "NULL" : lastMessageGroupId + "", dialogId, lastMessageId)).stepThis().dispose();
                        updateDialogs = true;
                    }

                    database.commitTransaction();

                    if (createDialog || updateDialogs) {
                        updateDialogsWithDeletedMessages(dialogId, channelId, new ArrayList<>(), null, false);
                    }
                }
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (database != null) {
                    database.commitTransaction();
                }
                if (state_messages_topics != null) {
                    state_messages_topics.dispose();
                }
                if (state_messages != null) {
                    state_messages.dispose();
                }
                if (state_media != null) {
                    state_media.dispose();
                }
                if (state_polls != null) {
                    state_polls.dispose();
                }
                if (state_webpage != null) {
                    state_webpage.dispose();
                }
                if (state_tasks != null) {
                    state_tasks.dispose();
                }
                if (state3 != null) {
                    state3.dispose();
                }
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public static void addUsersAndChatsFromMessage(TLRPC.Message message, ArrayList<Long> usersToLoad, ArrayList<Long> chatsToLoad, ArrayList<Long> emojiToLoad) {
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
                } else if (emojiToLoad != null && entity instanceof TLRPC.TL_messageEntityCustomEmoji) {
                    emojiToLoad.add(((TLRPC.TL_messageEntityCustomEmoji) entity).document_id);
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
            SQLiteCursor cursor = null;
            try {
                ArrayList<Long> usersToLoad = new ArrayList<>();
                HashSet<Long> dialogUsers = new HashSet<>();
                usersToLoad.add(getUserConfig().getClientUserId());
                ArrayList<Long> chatsToLoad = new ArrayList<>();
                ArrayList<Integer> encryptedToLoad = new ArrayList<>();
                ArrayList<Long> loadedDialogs = new ArrayList<>();
                ArrayList<Long> emojiToLoad = new ArrayList<>();
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

                    ArrayList<Pair<Long, Long>> dialogsToLoadGroupMessages = new ArrayList<>();
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT d.did, d.last_mid, d.unread_count, d.date, m.data, m.read_state, m.mid, m.send_state, s.flags, m.date, d.pts, d.inbox_max, d.outbox_max, m.replydata, d.pinned, d.unread_count_i, d.flags, d.folder_id, d.data, d.unread_reactions, d.last_mid_group, d.ttl_period FROM dialogs as d LEFT JOIN messages_v2 as m ON d.last_mid = m.mid AND d.did = m.uid AND d.last_mid_group IS NULL LEFT JOIN dialog_settings as s ON d.did = s.did WHERE d.folder_id = %d ORDER BY d.pinned DESC, d.date DESC LIMIT %d,%d", fid, off, cnt));
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
                        long groupMessagesId = cursor.longValue(20);
                        if (groupMessagesId != 0) {
                            dialogsToLoadGroupMessages.add(new Pair<>(dialogId, groupMessagesId));
                        }
                        dialog.ttl_period = cursor.intValue(21);
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

                                addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, emojiToLoad);

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
                                                    addUsersAndChatsFromMessage(message.replyMessage, usersToLoad, chatsToLoad, emojiToLoad);
                                                }
                                            }
                                        }
                                        if (message.replyMessage == null) {
                                            addReplyMessages(message, replyMessageOwners, dialogReplyMessagesIds);
                                        }
                                    }
                                } catch (Exception e) {
                                    checkSQLException(e);
                                }
                            } else {
                                data.reuse();
                            }
                        }

                        if (!DialogObject.isEncryptedDialog(dialogId)) {
                            if (dialog.read_inbox_max_id > dialog.top_message) {
                                dialog.read_inbox_max_id = 0;
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
                            dialogUsers.add(dialogId);
                        } else if (DialogObject.isChatDialog(dialogId)) {
                            if (!chatsToLoad.contains(-dialogId)) {
                                chatsToLoad.add(-dialogId);
                            }
                        }
                    }
                    cursor.dispose();
                    cursor = null;

                    if (!dialogsToLoadGroupMessages.isEmpty()) {
                        StringBuilder whereClause = new StringBuilder();
                        for (int i = 0; i < dialogsToLoadGroupMessages.size(); ++i) {
                            Pair<Long, Long> pair = dialogsToLoadGroupMessages.get(i);
                            whereClause.append("uid = ").append(pair.first).append(" AND group_id = ").append(pair.second);
                            if (i + 1 < dialogsToLoadGroupMessages.size()) {
                                whereClause.append(" OR ");
                            }
                        }
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT uid, data, read_state, mid, send_state, date, replydata, group_id FROM messages_v2 WHERE %s ORDER BY date DESC", whereClause));
                        int COUNT = 0;
                        while (cursor.next()) {
                            COUNT++;
                            long did = cursor.longValue(0);
                            NativeByteBuffer data = cursor.byteBufferValue(1);
                            TLRPC.Dialog dialog = null;
                            for (int i = 0; i < dialogs.dialogs.size(); ++i) {
                                TLRPC.Dialog d = dialogs.dialogs.get(i);
                                if (d != null && d.id == did) {
                                    dialog = d;
                                    break;
                                }
                            }
                            if (dialog == null) {
                                continue;
                            }
                            if (data != null) {
                                TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                if (message != null) {
                                    message.readAttachPath(data, getUserConfig().clientUserId);
                                    data.reuse();
                                    MessageObject.setUnreadFlags(message, cursor.intValue(2));
                                    message.id = cursor.intValue(3);
                                    int date = cursor.intValue(5);
                                    if (date != 0) {
                                        dialog.last_message_date = date;
                                    }
                                    message.send_state = cursor.intValue(4);
                                    message.dialog_id = did;
                                    dialogs.messages.add(message);

                                    addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);

                                    try {
                                        if (message.reply_to != null && message.reply_to.reply_to_msg_id != 0 && (
                                                message.action instanceof TLRPC.TL_messageActionPinMessage ||
                                                        message.action instanceof TLRPC.TL_messageActionPaymentSent ||
                                                        message.action instanceof TLRPC.TL_messageActionGameScore)) {
                                            if (!cursor.isNull(7)) {
                                                NativeByteBuffer data2 = cursor.byteBufferValue(7);
                                                if (data2 != null) {
                                                    message.replyMessage = TLRPC.Message.TLdeserialize(data2, data2.readInt32(false), false);
                                                    message.replyMessage.readAttachPath(data2, getUserConfig().clientUserId);
                                                    data2.reuse();
                                                    if (message.replyMessage != null) {
                                                        addUsersAndChatsFromMessage(message.replyMessage, usersToLoad, chatsToLoad, null);
                                                    }
                                                }
                                            }
                                            if (message.replyMessage == null) {
                                                addReplyMessages(message, replyMessageOwners, dialogReplyMessagesIds);
                                            }
                                        }
                                    } catch (Exception e) {
                                        checkSQLException(e);
                                    }
                                } else {
                                    data.reuse();
                                }
                            }
                        }
                        cursor.dispose();
                    }
                }

                loadReplyMessages(replyMessageOwners, dialogReplyMessagesIds, usersToLoad, chatsToLoad, false);

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
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT did, folder_id FROM dialogs WHERE did IN(%s)", TextUtils.join(",", unloadedDialogs)));
                        while (cursor.next()) {
                            folderIds.put(cursor.longValue(0), cursor.intValue(1));
                        }
                        cursor.dispose();
                        cursor = null;
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
                ArrayList<TLRPC.UserFull> fullUsers = null;
                if (!dialogUsers.isEmpty()) {
                    HashSet<Long> fullUsersToLoad = new HashSet<>();
                    for (Long did : dialogUsers) {
                        for (int i = 0; i < dialogs.users.size(); i++) {
                            if (dialogs.users.get(i).id == did && dialogs.users.get(i).premium) {
                                fullUsersToLoad.add(did);
                            }
                        }
                    }
                    if (!fullUsersToLoad.isEmpty()) {
                        fullUsers = loadUserInfos(fullUsersToLoad);
                    }
                }
                getMessagesController().processLoadedDialogs(dialogs, encryptedChats, fullUsers, folderId, offset, count, 1, false, false, true);
            } catch (Exception e) {
                dialogs.dialogs.clear();
                dialogs.users.clear();
                dialogs.chats.clear();
                encryptedChats.clear();
                checkSQLException(e);
                getMessagesController().processLoadedDialogs(dialogs, encryptedChats, null, folderId, 0, 100, 1, true, false, true);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public static void createFirstHoles(long did, SQLitePreparedStatement state5, SQLitePreparedStatement state6, int messageId, int topicId) throws Exception {
        state5.requery();
        int pointer = 1;
        state5.bindLong(pointer++, did);
        if (topicId != 0) {
            state5.bindInteger(pointer++, topicId);
        }
        state5.bindInteger(pointer++, messageId == 1 ? 1 : 0);
        state5.bindInteger(pointer++, messageId);
        state5.step();

        for (int b = 0; b < MediaDataController.MEDIA_TYPES_COUNT; b++) {
            state6.requery();
            pointer = 1;
            state6.bindLong(pointer++, did);
            if (topicId != 0) {
                state6.bindInteger(pointer++, topicId);
            }
            state6.bindInteger(pointer++, b);
            state6.bindInteger(pointer++, messageId == 1 ? 1 : 0);
            state6.bindInteger(pointer++, messageId);
            state6.step();
        }
    }

    public void updateDialogData(TLRPC.Dialog dialog) {
        if (dialog == null) {
            return;
        }
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            SQLitePreparedStatement state = null;
            try {
                cursor = database.queryFinalized("SELECT data FROM dialogs WHERE did = " + dialog.id);
                if (!cursor.next()) {
                    return;
                }

                state = database.executeFast("UPDATE dialogs SET data = ? WHERE did = ?");
                NativeByteBuffer data = new NativeByteBuffer(dialog.getObjectSize());
                dialog.serializeToStream(data);
                state.bindByteBuffer(1, data);
                state.bindLong(2, dialog.id);
                state.step();
                state.dispose();
                state = null;
                data.reuse();
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    private void putDialogsInternal(TLRPC.messages_Dialogs dialogs, int check) {
        SQLitePreparedStatement state_messages = null;
        SQLitePreparedStatement state_dialogs = null;
        SQLitePreparedStatement state_media = null;
        SQLitePreparedStatement state_settings = null;
        SQLitePreparedStatement state_holes = null;
        SQLitePreparedStatement state_media_holes = null;
        SQLitePreparedStatement state_polls = null;
        SQLitePreparedStatement state_tasks = null;
        SQLiteCursor cursor = null;
        try {
            database.beginTransaction();
            LongSparseArray<TLRPC.Message> new_dialogMessage = new LongSparseArray<>(dialogs.messages.size());
            for (int a = 0; a < dialogs.messages.size(); a++) {
                TLRPC.Message message = dialogs.messages.get(a);
                long did = MessageObject.getDialogId(message);
                if (!new_dialogMessage.containsKey(did) || new_dialogMessage.get(did) != null && new_dialogMessage.get(did).date < message.date) {
                    new_dialogMessage.put(did, message);
                }
            }

            if (!dialogs.dialogs.isEmpty()) {
                state_messages = database.executeFast("REPLACE INTO messages_v2 VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, 0, NULL, ?)");
                state_dialogs = database.executeFast("REPLACE INTO dialogs VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                state_media = database.executeFast("REPLACE INTO media_v4 VALUES(?, ?, ?, ?, ?)");
                state_settings = database.executeFast("REPLACE INTO dialog_settings VALUES(?, ?)");
                state_holes = database.executeFast("REPLACE INTO messages_holes VALUES(?, ?, ?)");
                state_media_holes = database.executeFast("REPLACE INTO media_holes_v2 VALUES(?, ?, ?, ?)");
                state_polls = null;
                state_tasks = null;
                int minDeleteTime = Integer.MAX_VALUE;

                for (int a = 0; a < dialogs.dialogs.size(); a++) {
                    TLRPC.Dialog dialog = dialogs.dialogs.get(a);
                    boolean exists = false;
                    DialogObject.initDialog(dialog);
                    unknownDialogsIds.remove(dialog.id);

                    if (check == 1) {
                        cursor = database.queryFinalized("SELECT did FROM dialogs WHERE did = " + dialog.id);
                        exists = cursor.next();
                        cursor.dispose();
                        cursor = null;
                        if (exists) {
                            continue;
                        }
                    } else if (check == 2) {
                        cursor = database.queryFinalized("SELECT pinned FROM dialogs WHERE did = " + dialog.id);
                        if (cursor.next()) {
                            exists = true;
                            if (dialog.pinned) {
                                dialog.pinnedNum = cursor.intValue(0);
                            }
                        }
                        cursor.dispose();
                        cursor = null;
                    } else if (check == 3) {
                        int mid = 0;
                        cursor = database.queryFinalized("SELECT last_mid FROM dialogs WHERE did = " + dialog.id);
                        if (cursor.next()) {
                            mid = cursor.intValue(0);
                        }
                        cursor.dispose();
                        cursor = null;
                        if (mid < 0) {
                            continue;
                        }
                    }
                    int messageDate = 0;

                    TLRPC.Message message = new_dialogMessage.get(dialog.id);
                    if (message != null) {
                        messageDate = Math.max(message.date, messageDate);

                        if (isValidKeyboardToSave(message)) {
                            TopicKey topicKey = TopicKey.of(dialog.id, MessageObject.getTopicId(message, isForum(dialog.id)));
                            getMediaDataController().putBotKeyboard(topicKey, message);
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
                        if ((message.flags & 131072) != 0) {
                            state_messages.bindLong(16, message.grouped_id);
                        } else {
                            state_messages.bindNull(16);
                        }
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
                            closeHolesInTable("messages_holes", dialog.id, message.id, message.id, 0);
                            closeHolesInMedia(dialog.id, message.id, message.id, -1, 0);
                        } else {
                            createFirstHoles(dialog.id, state_holes, state_media_holes, message.id, 0);
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
                    if (message != null && (message.flags & 131072) != 0) {
                        state_dialogs.bindLong(16, message.grouped_id);
                    } else {
                        state_dialogs.bindNull(16);
                    }
                    state_dialogs.bindInteger(17, dialog.ttl_period);
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
                state_messages = null;
                state_dialogs.dispose();
                state_dialogs = null;
                state_media.dispose();
                state_media = null;
                state_settings.dispose();
                state_settings = null;
                state_holes.dispose();
                state_holes = null;
                state_media_holes.dispose();
                state_media_holes = null;
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
            checkSQLException(e);
        } finally {
            if (database != null) {
                database.commitTransaction();
            }
            if (cursor != null) {
                cursor.dispose();
            }
            if (state_messages != null) {
                state_messages.dispose();
            }
            if (state_dialogs != null) {
                state_dialogs.dispose();
            }
            if (state_media != null) {
                state_media.dispose();
            }
            if (state_settings != null) {
                state_settings.dispose();
            }
            if (state_holes != null) {
                state_holes.dispose();
            }
            if (state_holes != null) {
                state_holes.dispose();
            }
            if (state_media_holes != null) {
                state_media_holes.dispose();
            }
            if (state_polls != null) {
                state_polls.dispose();
            }
            if (state_tasks != null) {
                state_tasks.dispose();
            }
        }
    }

    public void getDialogFolderId(long dialogId, IntCallback callback) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                int folderId;
                if (unknownDialogsIds.get(dialogId) != null) {
                    folderId = -1;
                } else {
                    cursor = database.queryFinalized("SELECT folder_id FROM dialogs WHERE did = ?", dialogId);
                    if (cursor.next()) {
                        folderId = cursor.intValue(0);
                    } else {
                        folderId = -1;
                    }
                    cursor.dispose();
                }
                AndroidUtilities.runOnUIThread(() -> callback.run(folderId));
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    public void setDialogsFolderId(ArrayList<TLRPC.TL_folderPeer> peers, ArrayList<TLRPC.TL_inputFolderPeer> inputPeers, long dialogId, int folderId) {
        if (peers == null && inputPeers == null && dialogId == 0) {
            return;
        }
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                database.beginTransaction();
                state = database.executeFast("UPDATE dialogs SET folder_id = ?, pinned = ? WHERE did = ?");
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
                state = null;
                database.commitTransaction();
                checkIfFolderEmptyInternal(1);
                resetAllUnreadCounters(false);
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (database != null) {
                    database.commitTransaction();
                }
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    private void checkIfFolderEmptyInternal(int folderId) {
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized("SELECT did FROM dialogs WHERE folder_id = ?", folderId);
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
            checkSQLException(e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    public void checkIfFolderEmpty(int folderId) {
        storageQueue.postRunnable(() -> checkIfFolderEmptyInternal(folderId));
    }

    public void unpinAllDialogsExceptNew(ArrayList<Long> dids, int folderId) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            SQLitePreparedStatement state = null;
            try {
                ArrayList<Long> unpinnedDialogs = new ArrayList<>();
                cursor = database.queryFinalized(String.format(Locale.US, "SELECT did, folder_id FROM dialogs WHERE pinned > 0 AND did NOT IN (%s)", TextUtils.join(",", dids)));
                while (cursor.next()) {
                    long did = cursor.longValue(0);
                    int fid = cursor.intValue(1);
                    if (fid == folderId && !DialogObject.isEncryptedDialog(did) && !DialogObject.isFolderDialogId(did)) {
                        unpinnedDialogs.add(cursor.longValue(0));
                    }
                }
                cursor.dispose();
                cursor = null;
                if (!unpinnedDialogs.isEmpty()) {
                    state = database.executeFast("UPDATE dialogs SET pinned = ? WHERE did = ?");
                    for (int a = 0; a < unpinnedDialogs.size(); a++) {
                        long did = unpinnedDialogs.get(a);
                        state.requery();
                        state.bindInteger(1, 0);
                        state.bindLong(2, did);
                        state.step();
                    }
                    state.dispose();
                    state = null;
                }
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void setDialogUnread(long did, boolean unread) {
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                int flags = 0;
                SQLiteCursor cursor = null;
                try {
                    cursor = database.queryFinalized("SELECT flags FROM dialogs WHERE did = " + did);
                    if (cursor.next()) {
                        flags = cursor.intValue(0);
                    }
                } catch (Exception e) {
                    checkSQLException(e);
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

                state = database.executeFast("UPDATE dialogs SET flags = ? WHERE did = ?");
                state.bindInteger(1, flags);
                state.bindLong(2, did);
                state.step();
                state.dispose();
                resetAllUnreadCounters(false);
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void resetAllUnreadCounters(boolean muted) {
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
            SQLitePreparedStatement state = null;
            try {
                state = database.executeFast("UPDATE dialogs SET pinned = ? WHERE did = ?");
                state.bindInteger(1, pinned);
                state.bindLong(2, did);
                state.step();
                state.dispose();
                state = null;
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void setDialogsPinned(ArrayList<Long> dids, ArrayList<Integer> pinned) {
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                state = database.executeFast("UPDATE dialogs SET pinned = ? WHERE did = ?");
                for (int a = 0, N = dids.size(); a < N; a++) {
                    state.requery();
                    state.bindInteger(1, pinned.get(a));
                    state.bindLong(2, dids.get(a));
                    state.step();
                }
                state.dispose();
                state = null;
            } catch (Exception e) {
                checkSQLException(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
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
                checkSQLException(e);
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
                checkSQLException(e);
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
                    if (cursor.next()) {
                        max[0] = cursor.intValue(0);
                    }
                } else {
                    cursor = database.queryFinalized("SELECT last_mid, inbox_max FROM dialogs WHERE did = " + dialog_id);
                    if (cursor.next()) {
                        int lastMid = cursor.intValue(0);
                        int inboxMax = cursor.intValue(1);
                        if (inboxMax > lastMid) {
                            max[0] = 0;
                        } else {
                            max[0] = inboxMax;
                        }
                    }
                }
            } catch (Exception e) {
                checkSQLException(e);
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
            checkSQLException(e);
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
                checkSQLException(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            try {
                countDownLatch.countDown();
            } catch (Exception e) {
                checkSQLException(e);
            }
        });
        try {
            countDownLatch.await();
        } catch (Exception e) {
            checkSQLException(e);
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
            checkSQLException(e);
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
            checkSQLException(e);
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
            checkSQLException(e);
        }
        return user;
    }

    public ArrayList<TLRPC.User> getUsers(ArrayList<Long> uids) {
        ArrayList<TLRPC.User> users = new ArrayList<>();
        try {
            getUsersInternal(TextUtils.join(",", uids), users);
        } catch (Exception e) {
            users.clear();
            checkSQLException(e);
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
            checkSQLException(e);
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
            checkSQLException(e);
        }
        return chat;
    }


    public void localSearch(int dialogsType, String query, ArrayList<Object> resultArray, ArrayList<CharSequence> resultArrayNames, ArrayList<TLRPC.User> encUsers, ArrayList<Long> onlyDialogIds, int folderId) {
        long selfUserId = UserConfig.getInstance(currentAccount).getClientUserId();
        SQLiteCursor cursor = null;
        try {
            String search1 = query.trim().toLowerCase();
            if (TextUtils.isEmpty(search1)) {
                return;
            }
            String savedMessages = LocaleController.getString("SavedMessages", R.string.SavedMessages).toLowerCase();
            String savedMessages2 = "saved messages";
            String replies = LocaleController.getString("RepliesTitle", R.string.RepliesTitle).toLowerCase();
            String replies2 = "replies";
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

                if (dialogsType == DialogsActivity.DIALOGS_TYPE_BOT_REQUEST_PEER && (onlyDialogIds == null || !onlyDialogIds.contains(id))) {
                    continue;
                }

                if (!DialogObject.isEncryptedDialog(id)) {
                    if (DialogObject.isUserDialog(id)) {
                        if (dialogsType == DialogsActivity.DIALOGS_TYPE_USERS_ONLY && id == selfUserId) {
                            continue;
                        }
                        if (dialogsType == DialogsActivity.DIALOGS_TYPE_GROUPS_ONLY || dialogsType == DialogsActivity.DIALOGS_TYPE_CHANNELS_ONLY) {
                            continue;
                        }
                        if (dialogsType != DialogsActivity.DIALOGS_TYPE_ADD_USERS_TO && !usersToLoad.contains(id)) {
                            usersToLoad.add(id);
                        }
                    } else {
                        if (dialogsType == DialogsActivity.DIALOGS_TYPE_USERS_ONLY) {
                            continue;
                        }
                        if (!chatsToLoad.contains(-id)) {
                            chatsToLoad.add(-id);
                        }
                    }
                } else if (dialogsType == DialogsActivity.DIALOGS_TYPE_DEFAULT || dialogsType == DialogsActivity.DIALOGS_TYPE_FORWARD) {
                    int encryptedChatId = DialogObject.getEncryptedChatId(id);
                    if (!encryptedToLoad.contains(encryptedChatId)) {
                        encryptedToLoad.add(encryptedChatId);
                    }
                }
            }
            cursor.dispose();
            cursor = null;

            if (dialogsType != 4 && (savedMessages).startsWith(search1) || savedMessages2.startsWith(search1)) {
                TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
                DialogsSearchAdapter.DialogSearchResult dialogSearchResult = new DialogsSearchAdapter.DialogSearchResult();
                dialogSearchResult.date = Integer.MAX_VALUE;
                dialogSearchResult.name = savedMessages;
                dialogSearchResult.object = user;
                dialogsResult.put(user.id, dialogSearchResult);
                resultCount++;
            }

            if (dialogsType != 4 && (replies).startsWith(search1) || replies2.startsWith(search1)) {
                TLRPC.User user = getMessagesController().getUser(708513L);
                if (user == null) {
                    user = getMessagesController().getUser(1271266957L);
                }
                if (user != null) {
                    DialogsSearchAdapter.DialogSearchResult dialogSearchResult = new DialogsSearchAdapter.DialogSearchResult();
                    dialogSearchResult.date = Integer.MAX_VALUE;
                    dialogSearchResult.name = LocaleController.getString("RepliesTitle", R.string.RepliesTitle);
                    dialogSearchResult.object = user;
                    dialogsResult.put(user.id, dialogSearchResult);
                    resultCount++;
                }
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
                                if (dialogsType == DialogsActivity.DIALOGS_TYPE_BOT_REQUEST_PEER && (onlyDialogIds == null || !onlyDialogIds.contains(user.id))) {
                                    continue;
                                }
                                DialogsSearchAdapter.DialogSearchResult dialogSearchResult = dialogsResult.get(user.id);
                                if (user.status != null) {
                                    user.status.expires = cursor.intValue(1);
                                }
                                if (found == 1) {
                                    dialogSearchResult.name = AndroidUtilities.generateSearchName(user.first_name, user.last_name, q);
                                } else {
                                    dialogSearchResult.name = AndroidUtilities.generateSearchName("@" + UserObject.getPublicUsername(user), null, "@" + q);
                                }
                                dialogSearchResult.object = user;
                                resultCount++;
                            }
                            break;
                        }
                    }
                }
                cursor.dispose();
                cursor = null;
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
                                if (dialogsType == DialogsActivity.DIALOGS_TYPE_BOT_REQUEST_PEER && (onlyDialogIds == null || !onlyDialogIds.contains(-chat.id))) {
                                    continue;
                                }
                                if (dialogsType == DialogsActivity.DIALOGS_TYPE_GROUPS_ONLY && ChatObject.isChannelAndNotMegaGroup(chat)) {
                                    continue;
                                }
                                if (dialogsType == DialogsActivity.DIALOGS_TYPE_CHANNELS_ONLY && !ChatObject.isChannelAndNotMegaGroup(chat)) {
                                    continue;
                                }
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
                cursor = null;
            }

            if (!encryptedToLoad.isEmpty() && dialogsType != DialogsActivity.DIALOGS_TYPE_BOT_REQUEST_PEER) {
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
                                    dialogSearchResult.name = AndroidUtilities.generateSearchName("@" + UserObject.getPublicUsername(user), null, "@" + q);
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
                cursor = null;
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

            if (dialogsType != DialogsActivity.DIALOGS_TYPE_ADD_USERS_TO && dialogsType != DialogsActivity.DIALOGS_TYPE_BOT_REQUEST_PEER && dialogsType != DialogsActivity.DIALOGS_TYPE_GROUPS_ONLY && dialogsType != DialogsActivity.DIALOGS_TYPE_CHANNELS_ONLY) {
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
                                    resultArrayNames.add(AndroidUtilities.generateSearchName("@" + UserObject.getPublicUsername(user), null, "@" + q));
                                }
                                resultArray.add(user);
                            }
                            break;
                        }
                    }
                }
                cursor.dispose();
                cursor = null;
            }
        } catch (Exception e) {
            checkSQLException(e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
    }

    public ArrayList<Integer> getCachedMessagesInRange(long dialogId, int minDate, int maxDate) {
        ArrayList<Integer> messageIds = new ArrayList<>();
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized(String.format(Locale.US, "SELECT mid FROM messages_v2 WHERE uid = %d AND date >= %d AND date <= %d", dialogId, minDate, maxDate));
            try {
                while (cursor.next()) {
                    int mid = cursor.intValue(0);
                    messageIds.add(mid);
                }
            } catch (Exception e) {
                checkSQLException(e);
            }
            cursor.dispose();
        } catch (Exception e) {
            checkSQLException(e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
        return messageIds;
    }

    public void updateUnreadReactionsCount(long dialogId, int topicId, int count) {
        updateUnreadReactionsCount(dialogId, topicId, count, false);
    }

    public void updateUnreadReactionsCount(long dialogId, int topicId, int count, boolean increment) {
        storageQueue.postRunnable(() -> {
            SQLitePreparedStatement state = null;
            if (topicId != 0) {
                try {
                    int currentReactions = 0;
                    if (increment) {
                        SQLiteCursor cursor = database.queryFinalized(String.format("SELECT unread_reactions FROM topics WHERE did = %d AND topic_id = %d", dialogId, topicId));
                        if (cursor.next()) {
                            currentReactions = cursor.intValue(0);
                        }
                        cursor.dispose();
                    }
                    state = database.executeFast("UPDATE topics SET unread_reactions = ? WHERE did = ? AND topic_id = ?");
                    state.bindInteger(1, Math.max(currentReactions + count, 0));
                    state.bindLong(2, dialogId);
                    state.bindInteger(3, topicId);
                    state.step();
                    state.dispose();
                    state = null;

                    if (count == 0) {
                        state = database.executeFast("UPDATE reaction_mentions_topics SET state = 0 WHERE dialog_id = ? AND topic_id = ? ");
                        state.bindLong(1, dialogId);
                        state.bindInteger(2, topicId);
                        state.step();
                        state.dispose();
                        state = null;
                    }
                } catch (SQLiteException e) {
                    e.printStackTrace();
                } finally {
                    if (state != null) {
                        state.dispose();
                    }
                }
            } else {
                try {
                    state = database.executeFast("UPDATE dialogs SET unread_reactions = ? WHERE did = ?");
                    state.bindInteger(1, Math.max(count, 0));
                    state.bindLong(2, dialogId);
                    state.step();
                    state.dispose();
                    state = null;

                    if (count == 0) {
                        state = database.executeFast("UPDATE reaction_mentions SET state = 0 WHERE dialog_id = ?");
                        state.bindLong(1, dialogId);
                        state.step();
                        state.dispose();
                        state = null;
                    }
                } catch (SQLiteException e) {
                    e.printStackTrace();
                } finally {
                    if (state != null) {
                        state.dispose();
                    }
                }
            }
        });
    }

    public void markMessageReactionsAsRead(long dialogId, int topicId, int messageId, boolean usequeue) {
        if (usequeue) {
            getStorageQueue().postRunnable(() -> {
                markMessageReactionsAsReadInternal(dialogId, topicId, messageId);
            });
        } else {
            markMessageReactionsAsReadInternal(dialogId, topicId, messageId);
        }
    }

    public void markMessageReactionsAsReadInternal(long dialogId, int topicId, int messageId) {
        SQLitePreparedStatement state = null;
        SQLiteCursor cursor = null;
        try {
            for (int k = 0; k < 2; k++) {
                boolean isTopic = k == 1;
                if (isTopic && topicId == 0) {
                    continue;
                }
                if (!isTopic) {
                    state = getMessagesStorage().getDatabase().executeFast("UPDATE reaction_mentions SET state = 0 WHERE message_id = ? AND dialog_id = ?");
                    state.bindInteger(1, messageId);
                    state.bindLong(2, dialogId);
                    state.step();
                    state.dispose();
                    state = null;
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM messages_v2 WHERE uid = %d AND mid = %d", dialogId, messageId));
                } else {
                    state = getMessagesStorage().getDatabase().executeFast("UPDATE reaction_mentions_topics SET state = 0 WHERE message_id = ? AND dialog_id = ? AND topic_id = ? ");
                    state.bindInteger(1, messageId);
                    state.bindLong(2, dialogId);
                    state.bindInteger(3, topicId);
                    state.step();
                    state.dispose();
                    state = null;
                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data FROM messages_topics WHERE uid = %d AND mid = %d", dialogId, messageId));
                }
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
                cursor = null;

                if (message != null) {
                    if (!isTopic) {
                        state = getMessagesStorage().getDatabase().executeFast(String.format(Locale.US, "UPDATE messages_v2 SET data = ? WHERE uid = %d AND mid = %d", dialogId, messageId));
                    } else {
                        state = getMessagesStorage().getDatabase().executeFast(String.format(Locale.US, "UPDATE messages_topics SET data = ? WHERE uid = %d AND mid = %d", dialogId, messageId));
                    }
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
            }
        } catch (SQLiteException e) {
            checkSQLException(e);
        } finally {
            if (state != null) {
                state.dispose();
            }
            if (cursor != null) {
                cursor.dispose();
            }
        }

    }

    public void updateDialogUnreadReactions(long dialogId, int topicId, int newUnreadCount, boolean increment) {
        storageQueue.postRunnable(() -> {
            SQLiteCursor cursor = null;
            SQLitePreparedStatement state = null;
            try {
                int oldUnreadRactions = 0;
                if (increment) {
                    cursor = database.queryFinalized("SELECT unread_reactions FROM dialogs WHERE did = " + dialogId);
                    if (cursor.next()) {
                        oldUnreadRactions = Math.max(0, cursor.intValue(0));
                    }
                    cursor.dispose();
                    cursor = null;
                }
                oldUnreadRactions += newUnreadCount;
                state = getMessagesStorage().getDatabase().executeFast("UPDATE dialogs SET unread_reactions = ? WHERE did = ?");
                state.bindInteger(1, oldUnreadRactions);
                state.bindLong(2, dialogId);
                state.step();
                state.dispose();
                state = null;

                if (topicId != 0) {
                    oldUnreadRactions = 0;
                    if (increment) {
                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT unread_reactions FROM topics WHERE did = %d AND topic_id = %d", dialogId, topicId));
                        if (cursor.next()) {
                            oldUnreadRactions = Math.max(0, cursor.intValue(0));
                        }
                        cursor.dispose();
                        cursor = null;
                    }

                    oldUnreadRactions += newUnreadCount;
                    state = getMessagesStorage().getDatabase().executeFast("UPDATE topics SET unread_reactions = ? WHERE did = ? AND topic_id = ?");
                    state.bindInteger(1, oldUnreadRactions);
                    state.bindLong(2, dialogId);
                    state.bindInteger(3, topicId);
                    state.step();
                    state.dispose();
                    state = null;
                }
            } catch (SQLiteException e) {
                e.printStackTrace();
            } finally {
                if (state != null) {
                    state.dispose();
                }
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    private boolean isForum(long dialogId) {
        int v = dialogIsForum.get(dialogId, -1);
        if (v == -1) {
            TLRPC.Chat chat = getChat(-dialogId);
            v = chat != null && chat.forum ? 1 : 0;
            dialogIsForum.put(dialogId, v);
        }
        return v == 1;
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

    public static class TopicKey {
        public long dialogId;
        public int topicId;

        public static TopicKey of(long dialogId, int topicId) {
            TopicKey topicKey = new TopicKey();
            topicKey.dialogId = dialogId;
            topicKey.topicId = topicId;
            return topicKey;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TopicKey topicKey = (TopicKey) o;
            return dialogId == topicKey.dialogId && topicId == topicKey.topicId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(dialogId, topicId);
        }

        @Override
        public String toString() {
            return "TopicKey{" +
                    "dialogId=" + dialogId +
                    ", topicId=" + topicId +
                    '}';
        }
    }
}
