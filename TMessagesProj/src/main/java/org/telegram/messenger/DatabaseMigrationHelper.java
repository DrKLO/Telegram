package org.telegram.messenger;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

public class DatabaseMigrationHelper {

    public static int migrate(MessagesStorage messagesStorage, int version) throws Exception {
        SQLiteDatabase database = messagesStorage.getDatabase();
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

            messagesStorage.fixNotificationSettings();
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
            messagesStorage.fixNotificationSettings();
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
            messagesStorage.executeNoException("ALTER TABLE messages ADD COLUMN forwards INTEGER default 0");
            database.executeFast("PRAGMA user_version = 69").stepThis().dispose();
            version = 69;
        }
        if (version == 69) {
            messagesStorage.executeNoException("ALTER TABLE messages ADD COLUMN replies_data BLOB default NULL");
            messagesStorage.executeNoException("ALTER TABLE messages ADD COLUMN thread_reply_id INTEGER default 0");
            database.executeFast("PRAGMA user_version = 70").stepThis().dispose();
            version = 70;
        }
        if (version == 70) {
            database.executeFast("CREATE TABLE IF NOT EXISTS chat_pinned_v2(uid INTEGER, mid INTEGER, data BLOB, PRIMARY KEY (uid, mid));").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 71").stepThis().dispose();
            version = 71;
        }
        if (version == 71) {
            messagesStorage.executeNoException("ALTER TABLE sharing_locations ADD COLUMN proximity INTEGER default 0");
            database.executeFast("PRAGMA user_version = 72").stepThis().dispose();
            version = 72;
        }
        if (version == 72) {
            database.executeFast("CREATE TABLE IF NOT EXISTS chat_pinned_count(uid INTEGER PRIMARY KEY, count INTEGER, end INTEGER);").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 73").stepThis().dispose();
            version = 73;
        }
        if (version == 73) {
            messagesStorage.executeNoException("ALTER TABLE chat_settings_v2 ADD COLUMN inviter INTEGER default 0");
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
            messagesStorage.executeNoException("ALTER TABLE chat_settings_v2 ADD COLUMN links INTEGER default 0");
            database.executeFast("PRAGMA user_version = 76").stepThis().dispose();
            version = 76;
        }
        if (version == 76) {
            messagesStorage.executeNoException("ALTER TABLE enc_tasks_v2 ADD COLUMN media INTEGER default -1");
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
                            message.readAttachPath(data, messagesStorage.getUserConfig().clientUserId);
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
            messagesStorage.executeNoException("ALTER TABLE messages_v2 ADD COLUMN reply_to_message_id INTEGER default 0");
            messagesStorage.executeNoException("ALTER TABLE scheduled_messages_v2 ADD COLUMN reply_to_message_id INTEGER default 0");

            database.executeFast("CREATE INDEX IF NOT EXISTS reply_to_idx_messages_v2 ON messages_v2(mid, reply_to_message_id);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS reply_to_idx_scheduled_messages_v2 ON scheduled_messages_v2(mid, reply_to_message_id);").stepThis().dispose();

            messagesStorage.executeNoException("UPDATE messages_v2 SET replydata = NULL");
            messagesStorage.executeNoException("UPDATE scheduled_messages_v2 SET replydata = NULL");
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

        if (version == 92) {
            database.executeFast("CREATE TABLE IF NOT EXISTS attach_menu_bots(data BLOB, hash INTEGER, date INTEGER);").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 93").stepThis().dispose();
            version = 95;
        }

        if (version == 95 || version == 93) {
            messagesStorage.executeNoException("ALTER TABLE messages_v2 ADD COLUMN custom_params BLOB default NULL");
            database.executeFast("PRAGMA user_version = 96").stepThis().dispose();
            version = 96;
        }

        // skip 94, 95. private beta db rollback
        if (version == 96) {
            database.executeFast("CREATE TABLE IF NOT EXISTS premium_promo(data BLOB, date INTEGER);").stepThis().dispose();
            database.executeFast("UPDATE stickers_v2 SET date = 0");
            database.executeFast("PRAGMA user_version = 97").stepThis().dispose();
            version = 97;
        }

        if (version == 97) {
            database.executeFast("DROP TABLE IF EXISTS stickers_featured;").stepThis().dispose();
            database.executeFast("CREATE TABLE stickers_featured(id INTEGER PRIMARY KEY, data BLOB, unread BLOB, date INTEGER, hash INTEGER, premium INTEGER);").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 98").stepThis().dispose();
            version = 98;
        }

        if (version == 98) {
            database.executeFast("CREATE TABLE animated_emoji(document_id INTEGER PRIMARY KEY, data BLOB);").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 99").stepThis().dispose();
            version = 99;
        }

        if (version == 99) {
            database.executeFast("ALTER TABLE stickers_featured ADD COLUMN emoji INTEGER default 0").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 100").stepThis().dispose();
            version = 100;
        }

        if (version == 100) {
            database.executeFast("CREATE TABLE emoji_statuses(data BLOB, type INTEGER);").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 101").stepThis().dispose();
            version = 101;
        }

        if (version == 101) {
            database.executeFast("ALTER TABLE messages_v2 ADD COLUMN group_id INTEGER default NULL").stepThis().dispose();
            database.executeFast("ALTER TABLE dialogs ADD COLUMN last_mid_group INTEGER default NULL").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_groupid_messages_v2 ON messages_v2(uid, mid, group_id);").stepThis().dispose();

            database.executeFast("PRAGMA user_version = 102").stepThis().dispose();
            version = 102;
        }

        if (version == 102) {
            database.executeFast("CREATE TABLE messages_holes_topics(uid INTEGER, topic_id INTEGER, start INTEGER, end INTEGER, PRIMARY KEY(uid, topic_id, start));").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS uid_end_messages_holes ON messages_holes_topics(uid, topic_id, end);").stepThis().dispose();

            database.executeFast("CREATE TABLE messages_topics(mid INTEGER, uid INTEGER, topic_id INTEGER, read_state INTEGER, send_state INTEGER, date INTEGER, data BLOB, out INTEGER, ttl INTEGER, media INTEGER, replydata BLOB, imp INTEGER, mention INTEGER, forwards INTEGER, replies_data BLOB, thread_reply_id INTEGER, is_channel INTEGER, reply_to_message_id INTEGER, custom_params BLOB, PRIMARY KEY(mid, topic_id, uid))").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_read_out_idx_messages_topics ON messages_topics(uid, mid, read_state, out);").stepThis().dispose();//move to topic id
            database.executeFast("CREATE INDEX IF NOT EXISTS uid_date_mid_idx_messages_topics ON messages_topics(uid, date, mid);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS mid_out_idx_messages_topics ON messages_topics(mid, out);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS task_idx_messages_topics ON messages_topics(uid, out, read_state, ttl, date, send_state);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS send_state_idx_messages_topics ON messages_topics(mid, send_state, date);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS uid_mention_idx_messages_topics ON messages_topics(uid, mention, read_state);").stepThis().dispose();//move to uid, topic_id, mentiin_read_state
            database.executeFast("CREATE INDEX IF NOT EXISTS is_channel_idx_messages_topics ON messages_topics(mid, is_channel);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS reply_to_idx_messages_topics ON messages_topics(mid, reply_to_message_id);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS mid_uid_messages_topics ON messages_topics(mid, uid);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS mid_uid_topic_id_messages_topics ON messages_topics(mid, topic_id, uid);").stepThis().dispose();

            database.executeFast("CREATE TABLE media_topics(mid INTEGER, uid INTEGER, topic_id INTEGER, date INTEGER, type INTEGER, data BLOB, PRIMARY KEY(mid, uid, topic_id, type))").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_type_date_idx_media_topics ON media_topics(uid, topic_id, mid, type, date);").stepThis().dispose();

            database.executeFast("CREATE TABLE media_holes_topics(uid INTEGER, topic_id INTEGER, type INTEGER, start INTEGER, end INTEGER, PRIMARY KEY(uid, topic_id, type, start));").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS uid_end_media_holes_topics ON media_holes_topics(uid, topic_id, type, end);").stepThis().dispose();

            database.executeFast("CREATE TABLE topics(did INTEGER, topic_id INTEGER, data BLOB, top_message INTEGER, topic_message BLOB, unread_count INTEGER, max_read_id INTEGER, unread_mentions INTEGER, unread_reactions INTEGER, PRIMARY KEY(did, topic_id));").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS did_top_message_topics ON topics(did, top_message);").stepThis().dispose();

            database.executeFast("PRAGMA user_version = 103").stepThis().dispose();
            version = 103;
        }

        if (version == 103) {
            database.executeFast("CREATE TABLE IF NOT EXISTS media_counts_topics(uid INTEGER, topic_id INTEGER, type INTEGER, count INTEGER, old INTEGER, PRIMARY KEY(uid, topic_id, type))").stepThis().dispose();
            database.executeFast("CREATE TABLE IF NOT EXISTS reaction_mentions_topics(message_id INTEGER, state INTEGER, dialog_id INTEGER, topic_id INTEGER, PRIMARY KEY(message_id, dialog_id, topic_id))").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS reaction_mentions_topics_did ON reaction_mentions_topics(dialog_id, topic_id);").stepThis().dispose();

            database.executeFast("PRAGMA user_version = 104").stepThis().dispose();
            version = 104;
        }

        if (version == 104) {
            database.executeFast("ALTER TABLE topics ADD COLUMN read_outbox INTEGER default 0").stepThis().dispose();

            database.executeFast("PRAGMA user_version = 105").stepThis().dispose();
            version = 105;
        }

        if (version == 105) {
            database.executeFast("ALTER TABLE topics ADD COLUMN pinned INTEGER default 0").stepThis().dispose();

            database.executeFast("PRAGMA user_version = 106").stepThis().dispose();
            version = 106;
        }

        if (version == 106) {
            database.executeFast("DROP INDEX IF EXISTS uid_mid_read_out_idx_messages_topics").stepThis().dispose();
            database.executeFast("DROP INDEX IF EXISTS uid_mention_idx_messages_topics").stepThis().dispose();

            database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_read_out_idx_messages_topics ON messages_topics(uid, topic_id, mid, read_state, out);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS uid_mention_idx_messages_topics ON messages_topics(uid, topic_id, mention, read_state);").stepThis().dispose();

            database.executeFast("CREATE INDEX IF NOT EXISTS uid_topic_id_messages_topics ON messages_topics(uid, topic_id);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS uid_topic_id_date_mid_messages_topics ON messages_topics(uid, topic_id, date, mid);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS uid_topic_id_mid_messages_topics ON messages_topics(uid, topic_id, mid);").stepThis().dispose();

            database.executeFast("CREATE INDEX IF NOT EXISTS did_topics ON topics(did);").stepThis().dispose();

            database.executeFast("PRAGMA user_version = 107").stepThis().dispose();
            version = 107;
        }

        if (version == 107) {
            database.executeFast("ALTER TABLE topics ADD COLUMN total_messages_count INTEGER default 0").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 108").stepThis().dispose();
            version = 108;
        }

        if (version == 108) {
            database.executeFast("ALTER TABLE topics ADD COLUMN hidden INTEGER default 0").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 109").stepThis().dispose();
            version = 109;
        }

        if (version == 109) {
            database.executeFast("ALTER TABLE dialogs ADD COLUMN ttl_period INTEGER default 0").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 110").stepThis().dispose();
            version = 110;
        }

        if (version == 110) {
            database.executeFast("CREATE TABLE stickersets(id INTEGER PRIMATE KEY, data BLOB, hash INTEGER);").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 111").stepThis().dispose();
            version = 111;
        }

        if (version == 111) {
            database.executeFast("CREATE TABLE emoji_groups(type INTEGER PRIMARY KEY, data BLOB)").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 112").stepThis().dispose();
            version = 112;
        }

        if (version == 112) {
            database.executeFast("CREATE TABLE app_config(data BLOB)").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 113").stepThis().dispose();
            version = 113;
        }

        if (version == 113) {
            //fix issue when database file was deleted
            //just reload dialogs
            messagesStorage.reset();
            database.executeFast("PRAGMA user_version = 114").stepThis().dispose();
            version = 114;
        }
        if (version == 114) {
            database.executeFast("CREATE TABLE bot_keyboard_topics(uid INTEGER, tid INTEGER, mid INTEGER, info BLOB, PRIMARY KEY(uid, tid))").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS bot_keyboard_topics_idx_mid_v2 ON bot_keyboard_topics(mid, uid, tid);").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 115").stepThis().dispose();
            version = 115;
        }
        if (version == 115) {
            database.executeFast("CREATE INDEX IF NOT EXISTS idx_to_reply_messages_v2 ON messages_v2(reply_to_message_id, mid);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS idx_to_reply_scheduled_messages_v2 ON scheduled_messages_v2(reply_to_message_id, mid);").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS idx_to_reply_messages_topics ON messages_topics(reply_to_message_id, mid);").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 117").stepThis().dispose();
            version = 117;
        }

        if (version == 116 || version == 117 || version == 118) {
            database.executeFast("DROP TABLE IF EXISTS stories").stepThis().dispose();
            database.executeFast("DROP TABLE IF EXISTS stories_counter").stepThis().dispose();

            database.executeFast("CREATE TABLE stories (dialog_id INTEGER, story_id INTEGER, data BLOB, local_path TEXT, local_thumb_path TEXT, PRIMARY KEY (dialog_id, story_id));").stepThis().dispose();
            database.executeFast("CREATE TABLE stories_counter (dialog_id INTEGER PRIMARY KEY, count INTEGER, max_read INTEGER);").stepThis().dispose();
            database.executeFast("PRAGMA user_version = 119").stepThis().dispose();
            messagesStorage.getMessagesController().getStoriesController().cleanup();
            version = 119;
        }

        if (version == 119) {
            database.executeFast("ALTER TABLE messages_v2 ADD COLUMN reply_to_story_id INTEGER default 0").stepThis().dispose();
            database.executeFast("ALTER TABLE messages_topics ADD COLUMN reply_to_story_id INTEGER default 0").stepThis().dispose();

            database.executeFast("PRAGMA user_version = 120").stepThis().dispose();
            version = 120;
        }

        if (version == 120) {
            database.executeFast("CREATE TABLE profile_stories (dialog_id INTEGER, story_id INTEGER, data BLOB, PRIMARY KEY(dialog_id, story_id));").stepThis().dispose();
            database.executeFast("CREATE TABLE archived_stories (story_id INTEGER PRIMARY KEY, data BLOB);").stepThis().dispose();

            database.executeFast("PRAGMA user_version = 121").stepThis().dispose();
            version = 121;
        }

        if (version == 121) {
            database.executeFast("CREATE TABLE story_drafts (id INTEGER PRIMARY KEY, date INTEGER, data BLOB);").stepThis().dispose();

            database.executeFast("PRAGMA user_version = 122").stepThis().dispose();
            version = 122;
        }

        if (version == 122) {
            database.executeFast("ALTER TABLE chat_settings_v2 ADD COLUMN participants_count INTEGER default 0").stepThis().dispose();

            database.executeFast("PRAGMA user_version = 123").stepThis().dispose();
            version = 123;
        }

        if (version == 123) {
            database.executeFast("CREATE TABLE story_pushes (uid INTEGER PRIMARY KEY, minId INTEGER, maxId INTEGER, date INTEGER, localName TEXT);").stepThis().dispose();

            database.executeFast("PRAGMA user_version = 124").stepThis().dispose();
            version = 124;
        }

        if (version == 124) {
            database.executeFast("DROP TABLE IF EXISTS story_pushes;").stepThis().dispose();
            database.executeFast("CREATE TABLE story_pushes (uid INTEGER, sid INTEGER, date INTEGER, localName TEXT, PRIMARY KEY(uid, sid));").stepThis().dispose();

            database.executeFast("PRAGMA user_version = 125").stepThis().dispose();
            version = 125;
        }

        if (version == 125) {
            database.executeFast("ALTER TABLE story_pushes ADD COLUMN flags INTEGER default 0").stepThis().dispose();

            database.executeFast("PRAGMA user_version = 126").stepThis().dispose();
            version = 126;
        }

        if (version == 126) {
            database.executeFast("ALTER TABLE story_pushes ADD COLUMN expire_date INTEGER default 0").stepThis().dispose();

            database.executeFast("PRAGMA user_version = 127").stepThis().dispose();
            version = 127;
        }

        return version;
    }

    public static boolean recoverDatabase(File oldDatabaseFile, File oldDatabaseWall, File oldDatabaseShm, int currentAccount) {
        File filesDir = ApplicationLoader.getFilesDirFixed();
        filesDir = new File(filesDir, "recover_database_" + currentAccount + "/");
        filesDir.mkdirs();

        File cacheFile = new File(filesDir, "cache4.db");
        File walCacheFile = new File(filesDir, "cache4.db-wal");
        File shmCacheFile = new File(filesDir, "cache4.db-shm");
        try {
            cacheFile.delete();
            walCacheFile.delete();
            shmCacheFile.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }

        SQLiteDatabase newDatabase = null;
        long time = 0;
        ArrayList<Long> encryptedDialogs = new ArrayList<>();
        ArrayList<Long> dialogs = new ArrayList<>();
        boolean recovered = true;
        FileLog.d("start recover database");

        try {
           time = System.currentTimeMillis();

            newDatabase = new SQLiteDatabase(cacheFile.getPath());
            newDatabase.executeFast("PRAGMA secure_delete = ON").stepThis().dispose();
            newDatabase.executeFast("PRAGMA temp_store = MEMORY").stepThis().dispose();
            newDatabase.executeFast("PRAGMA journal_mode = WAL").stepThis().dispose();
            newDatabase.executeFast("PRAGMA journal_size_limit = 10485760").stepThis().dispose();

            MessagesStorage.createTables(newDatabase);
            newDatabase.executeFast("ATTACH DATABASE \"" + oldDatabaseFile.getAbsolutePath() + "\" AS old;").stepThis().dispose();

            int version = newDatabase.executeInt("PRAGMA old.user_version");
            if (version != MessagesStorage.LAST_DB_VERSION) {
                FileLog.e("can't restore database from version " + version);
                return false;
            }
            HashSet<String> excludeTables = new HashSet<>();
            excludeTables.add("messages_v2");
            excludeTables.add("messages_holes");
            excludeTables.add("scheduled_messages_v2");
            excludeTables.add("media_holes_v2");
            excludeTables.add("media_v4");
            excludeTables.add("messages_holes_topics");
            excludeTables.add("messages_topics");
            excludeTables.add("media_topics");
            excludeTables.add("media_holes_topics");
            excludeTables.add("topics");
            excludeTables.add("media_counts_v2");
            excludeTables.add("media_counts_topics");
            excludeTables.add("dialogs");
            excludeTables.add("dialog_filter");
            excludeTables.add("dialog_filter_ep");
            excludeTables.add("dialog_filter_pin_v2");

            //restore whole tables
            for (int i = 0; i < MessagesStorage.DATABASE_TABLES.length; i++) {
                String tableName = MessagesStorage.DATABASE_TABLES[i];
                if (excludeTables.contains(tableName)) {
                    continue;
                }
                newDatabase.executeFast(String.format(Locale.US, "INSERT OR IGNORE INTO %s SELECT * FROM old.%s;", tableName, tableName)).stepThis().dispose();
            }

            SQLiteCursor cursor = newDatabase.queryFinalized("SELECT did FROM old.dialogs");

            while (cursor.next()) {
                long did = cursor.longValue(0);
                if (DialogObject.isEncryptedDialog(did)) {
                    encryptedDialogs.add(did);
                } else {
                    dialogs.add(did);
                }
            }
            cursor.dispose();

            //restore only secret chats
            for (int i = 0; i < encryptedDialogs.size(); i++) {
                long dialogId = encryptedDialogs.get(i);
                newDatabase.executeFast(String.format(Locale.US, "INSERT OR IGNORE INTO messages_v2 SELECT * FROM old.messages_v2 WHERE uid = %d;", dialogId)).stepThis().dispose();
                newDatabase.executeFast(String.format(Locale.US, "INSERT OR IGNORE INTO messages_holes SELECT * FROM old.messages_holes WHERE uid = %d;", dialogId)).stepThis().dispose();
                newDatabase.executeFast(String.format(Locale.US, "INSERT OR IGNORE INTO media_holes_v2 SELECT * FROM old.media_holes_v2 WHERE uid = %d;", dialogId)).stepThis().dispose();
                newDatabase.executeFast(String.format(Locale.US, "INSERT OR IGNORE INTO media_v4 SELECT * FROM old.media_v4 WHERE uid = %d;", dialogId)).stepThis().dispose();
            }

            SQLitePreparedStatement state5 = newDatabase.executeFast("REPLACE INTO messages_holes VALUES(?, ?, ?)");
            SQLitePreparedStatement state6 = newDatabase.executeFast("REPLACE INTO media_holes_v2 VALUES(?, ?, ?, ?)");

            for (int a = 0; a < dialogs.size(); a++) {
                Long did = dialogs.get(a);

                cursor = newDatabase.queryFinalized("SELECT last_mid_i, last_mid FROM old.dialogs WHERE did = " + did);

                if (cursor.next()) {
                    long last_mid_i = cursor.longValue(0);
                    long last_mid = cursor.longValue(1);
                    newDatabase.executeFast("INSERT OR IGNORE INTO messages_v2 SELECT * FROM old.messages_v2 WHERE uid = " + did + " AND mid IN (" + last_mid_i + "," + last_mid + ")").stepThis().dispose();

                    MessagesStorage.createFirstHoles(did, state5, state6, (int) last_mid, 0);

                }
                cursor.dispose();
                cursor = null;
            }

            state5.dispose();
            state6.dispose();

            newDatabase.executeFast("DETACH DATABASE old;").stepThis().dispose();
            newDatabase.close();
        } catch (Exception e) {
            FileLog.e(e);
            recovered = false;
        }
        if (!recovered) {
            return false;
        }
        try {
            oldDatabaseFile.delete();
            oldDatabaseWall.delete();
            oldDatabaseShm.delete();

            AndroidUtilities.copyFile(cacheFile, oldDatabaseFile);
            AndroidUtilities.copyFile(walCacheFile, oldDatabaseWall);
            AndroidUtilities.copyFile(shmCacheFile, oldDatabaseShm);

            cacheFile.delete();
            walCacheFile.delete();
            shmCacheFile.delete();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        FileLog.d("database recovered time " + (System.currentTimeMillis() - time));
        return true;
    }
}
