package org.telegram.tgnet.test

import org.junit.Test
import org.telegram.SQLite.SQLiteCursor
import org.telegram.SQLite.SQLiteDatabase
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.DatabaseMigrationHelper
import org.telegram.messenger.MessagesStorage
import java.io.File

class TestDatabaseMigration {
    @Test
    public fun test() {
        val dbCreationPath = File(ApplicationLoader.getFilesDirFixed(), "test_creation.db");
        dbCreationPath.mkdirs()
        dbCreationPath.delete()
        val dbCreation = SQLiteDatabase(dbCreationPath.path);MessagesStorage.createTables(dbCreation)

        val dbCreationPath2 = File(ApplicationLoader.getFilesDirFixed(), "test_creation_120.db");
        dbCreationPath2.mkdirs()
        dbCreationPath2.delete()
        val dbCreation2 = SQLiteDatabase(dbCreationPath2.path);
        createTablesV120(dbCreation2)
        DatabaseMigrationHelper.migrate(null, dbCreation2, 120);

        assert(assertSameSchema(dbCreation, dbCreation2))
    }


    fun assertSameSchema(db1: SQLiteDatabase, db2: SQLiteDatabase): Boolean {
        val schema1 = readSchema(db1)
        val schema2 = readSchema(db2)

        compareMaps(
            path = "tables",
            left = schema1.tables,
            right = schema2.tables,
            valueComparator = ::compareTable
        )

        compareMaps(
            path = "views",
            left = schema1.views,
            right = schema2.views,
            valueComparator = ::compareView
        )

        compareMaps(
            path = "triggers",
            left = schema1.triggers,
            right = schema2.triggers,
            valueComparator = ::compareTrigger
        )

        return true
    }

    private fun readSchema(db: SQLiteDatabase): DbSchema {
        val tables = linkedMapOf<String, TableSchema>()
        val views = linkedMapOf<String, ViewSchema>()
        val triggers = linkedMapOf<String, TriggerSchema>()

        query(
            db,
            """
            SELECT type, name, tbl_name, sql
            FROM sqlite_master
            WHERE name NOT LIKE 'sqlite_%'
            ORDER BY type, name
            """.trimIndent()
        ) { cursor ->
            val type = cursor.stringValue(0)
            val name = cursor.stringValue(1)
            val tableName = cursor.stringValue(2)
            val sql = cursorNullableString(3, cursor)

            when (type) {
                "table" -> {
                    tables[name] = TableSchema(
                        name = name,
                        sql = sql,
                        columns = readColumns(db, name),
                        indexes = readIndexes(db, name)
                    )
                }

                "view" -> {
                    views[name] = ViewSchema(
                        name = name,
                        sql = sql
                    )
                }

                "trigger" -> {
                    triggers[name] = TriggerSchema(
                        name = name,
                        tableName = tableName,
                        sql = sql
                    )
                }
            }
        }

        return DbSchema(
            tables = tables,
            views = views,
            triggers = triggers
        )
    }

    private fun readColumns(db: SQLiteDatabase, tableName: String): List<ColumnSchema> {
        val result = ArrayList<ColumnSchema>()

        query(db, "PRAGMA table_info(${escapeSqlName(tableName)})") { cursor ->
            result.add(
                ColumnSchema(
                    cid = cursor.intValue(0),
                    name = cursor.stringValue(1),
                    type = cursorNullableString(2, cursor),
                    notNull = cursor.intValue(3) != 0,
                    defaultValue = cursorNullableString(4, cursor),
                    pk = cursor.intValue(5)
                )
            )
        }

        return result
    }

    private fun readIndexes(db: SQLiteDatabase, tableName: String): LinkedHashMap<String, IndexSchema> {
        val result = linkedMapOf<String, IndexSchema>()

        query(db, "PRAGMA index_list(${escapeSqlName(tableName)})") { cursor ->
            val indexName = cursor.stringValue(1)
            val unique = cursor.intValue(2) != 0
            val origin = cursorNullableString(3, cursor)
            val partial = if (cursor.isNull(4)) false else cursor.intValue(4) != 0

            result[indexName] = IndexSchema(
                name = indexName,
                unique = unique,
                origin = origin,
                partial = partial,
                sql = readIndexSql(db, indexName),
                columns = readIndexColumns(db, indexName)
            )
        }

        return result
    }

    private fun readIndexSql(db: SQLiteDatabase, indexName: String): String? {
        var sql: String? = null

        query(
            db,
            """
            SELECT sql
            FROM sqlite_master
            WHERE type = 'index' AND name = ${sqlStringLiteral(indexName)}
            """.trimIndent()
        ) { cursor ->
            sql = cursorNullableString(0, cursor)
        }

        return sql
    }

    private fun readIndexColumns(db: SQLiteDatabase, indexName: String): List<IndexColumnSchema> {
        val result = ArrayList<IndexColumnSchema>()

        query(db, "PRAGMA index_info(${escapeSqlName(indexName)})") { cursor ->
            result.add(
                IndexColumnSchema(
                    seqno = cursor.intValue(0),
                    cid = cursor.intValue(1),
                    name = cursorNullableString(2, cursor)
                )
            )
        }

        return result
    }

    private fun compareTable(path: String, left: TableSchema, right: TableSchema) {
        // requireEquals("$path.sql", left.sql, right.sql)

        compareLists(
            path = "$path.columns",
            left = left.columns,
            right = right.columns,
            itemComparator = ::compareColumn
        )

        compareMaps(
            path = "$path.indexes",
            left = left.indexes,
            right = right.indexes,
            valueComparator = ::compareIndex
        )
    }

    private fun compareColumn(path: String, left: ColumnSchema, right: ColumnSchema) {
        requireEquals("$path.cid", left.cid, right.cid)
        requireEquals("$path.name", left.name, right.name)
        requireEquals("$path.type", left.type, right.type)
        requireEquals("$path.notNull", left.notNull, right.notNull)
        // requireEquals("$path.defaultValue", left.defaultValue, right.defaultValue)
        requireEquals("$path.pk", left.pk, right.pk)
    }

    private fun compareIndex(path: String, left: IndexSchema, right: IndexSchema) {
        requireEquals("$path.unique", left.unique, right.unique)
        requireEquals("$path.origin", left.origin, right.origin)
        requireEquals("$path.partial", left.partial, right.partial)
        requireEquals("$path.sql", left.sql, right.sql)

        compareLists(
            path = "$path.columns",
            left = left.columns,
            right = right.columns,
            itemComparator = ::compareIndexColumn
        )
    }

    private fun compareIndexColumn(path: String, left: IndexColumnSchema, right: IndexColumnSchema) {
        requireEquals("$path.seqno", left.seqno, right.seqno)
        requireEquals("$path.cid", left.cid, right.cid)
        requireEquals("$path.name", left.name, right.name)
    }

    private fun compareView(path: String, left: ViewSchema, right: ViewSchema) {
        requireEquals("$path.sql", left.sql, right.sql)
    }

    private fun compareTrigger(path: String, left: TriggerSchema, right: TriggerSchema) {
        requireEquals("$path.tableName", left.tableName, right.tableName)
        requireEquals("$path.sql", left.sql, right.sql)
    }

    private fun <T> compareLists(
        path: String,
        left: List<T>,
        right: List<T>,
        itemComparator: (path: String, left: T, right: T) -> Unit
    ) {
        if (left.size != right.size) {
            fail("$path size differs: ${left.size} != ${right.size}")
        }

        for (i in left.indices) {
            itemComparator("$path[$i]", left[i], right[i])
        }
    }

    private fun <T> compareMaps(
        path: String,
        left: Map<String, T>,
        right: Map<String, T>,
        valueComparator: (path: String, left: T, right: T) -> Unit
    ) {
        val leftKeys = left.keys
        val rightKeys = right.keys

        for (key in leftKeys) {
            if (!right.containsKey(key)) {
                fail("$path[$key] exists only in first DB")
            }
        }

        for (key in rightKeys) {
            if (!left.containsKey(key)) {
                fail("$path[$key] exists only in second DB")
            }
        }

        for (key in leftKeys) {
            valueComparator("$path[$key]", left.getValue(key), right.getValue(key))
        }
    }

    private fun requireEquals(path: String, left: Any?, right: Any?) {
        if (left != right) {
            fail("$path differs: first=$left, second=$right")
        }
    }

    private fun fail(message: String): Nothing {
        throw IllegalStateException("SQLite schema mismatch: $message")
    }

    private inline fun query(
        db: SQLiteDatabase,
        sql: String,
        block: (SQLiteCursor) -> Unit
    ) {
        var cursor: SQLiteCursor? = null
        try {
            cursor = db.queryFinalized(sql)
            while (cursor.next()) {
                block(cursor)
            }
        } finally {
            cursor?.dispose()
        }
    }

    private fun cursorNullableString(index: Int, cursor: SQLiteCursor): String? {
        return if (cursor.isNull(index)) null else cursor.stringValue(index)
    }

    private fun escapeSqlName(name: String): String {
        return "\"" + name.replace("\"", "\"\"") + "\""
    }

    private fun sqlStringLiteral(value: String): String {
        return "'" + value.replace("'", "''") + "'"
    }

    private data class DbSchema(
        val tables: LinkedHashMap<String, TableSchema>,
        val views: LinkedHashMap<String, ViewSchema>,
        val triggers: LinkedHashMap<String, TriggerSchema>
    )

    private data class TableSchema(
        val name: String,
        val sql: String?,
        val columns: List<ColumnSchema>,
        val indexes: LinkedHashMap<String, IndexSchema>
    )

    private data class ColumnSchema(
        val cid: Int,
        val name: String,
        val type: String?,
        val notNull: Boolean,
        val defaultValue: String?,
        val pk: Int
    )

    private data class IndexSchema(
        val name: String,
        val unique: Boolean,
        val origin: String?,
        val partial: Boolean,
        val sql: String?,
        val columns: List<IndexColumnSchema>
    )

    private data class IndexColumnSchema(
        val seqno: Int,
        val cid: Int,
        val name: String?
    )

    private data class ViewSchema(
        val name: String,
        val sql: String?
    )

    private data class TriggerSchema(
        val name: String,
        val tableName: String,
        val sql: String?
    )

    fun createTablesV120(database: SQLiteDatabase) {
        database.executeFast("CREATE TABLE messages_holes(uid INTEGER, start INTEGER, end INTEGER, PRIMARY KEY(uid, start));")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_end_messages_holes ON messages_holes(uid, end);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE media_holes_v2(uid INTEGER, type INTEGER, start INTEGER, end INTEGER, PRIMARY KEY(uid, type, start));")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_end_media_holes_v2 ON media_holes_v2(uid, type, end);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE scheduled_messages_v2(mid INTEGER, uid INTEGER, send_state INTEGER, date INTEGER, data BLOB, ttl INTEGER, replydata BLOB, reply_to_message_id INTEGER, PRIMARY KEY(mid, uid))")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS send_state_idx_scheduled_messages_v2 ON scheduled_messages_v2(mid, send_state, date);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_date_idx_scheduled_messages_v2 ON scheduled_messages_v2(uid, date);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS reply_to_idx_scheduled_messages_v2 ON scheduled_messages_v2(mid, reply_to_message_id);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS idx_to_reply_scheduled_messages_v2 ON scheduled_messages_v2(reply_to_message_id, mid);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE messages_v2(mid INTEGER, uid INTEGER, read_state INTEGER, send_state INTEGER, date INTEGER, data BLOB, out INTEGER, ttl INTEGER, media INTEGER, replydata BLOB, imp INTEGER, mention INTEGER, forwards INTEGER, replies_data BLOB, thread_reply_id INTEGER, is_channel INTEGER, reply_to_message_id INTEGER, custom_params BLOB, group_id INTEGER, reply_to_story_id INTEGER, PRIMARY KEY(mid, uid))")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_read_out_idx_messages_v2 ON messages_v2(uid, mid, read_state, out);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_date_mid_idx_messages_v2 ON messages_v2(uid, date, mid);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS mid_out_idx_messages_v2 ON messages_v2(mid, out);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS task_idx_messages_v2 ON messages_v2(uid, out, read_state, ttl, date, send_state);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS send_state_idx_messages_v2 ON messages_v2(mid, send_state, date);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_mention_idx_messages_v2 ON messages_v2(uid, mention, read_state);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS is_channel_idx_messages_v2 ON messages_v2(mid, is_channel);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS reply_to_idx_messages_v2 ON messages_v2(mid, reply_to_message_id);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS idx_to_reply_messages_v2 ON messages_v2(reply_to_message_id, mid);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_groupid_messages_v2 ON messages_v2(uid, mid, group_id);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE download_queue(uid INTEGER, type INTEGER, date INTEGER, data BLOB, parent TEXT, PRIMARY KEY (uid, type));")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS type_date_idx_download_queue ON download_queue(type, date);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE user_contacts_v7(key TEXT PRIMARY KEY, uid INTEGER, fname TEXT, sname TEXT, imported INTEGER)")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE user_phones_v7(key TEXT, phone TEXT, sphone TEXT, deleted INTEGER, PRIMARY KEY (key, phone))")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS sphone_deleted_idx_user_phones ON user_phones_v7(sphone, deleted);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE dialogs(did INTEGER PRIMARY KEY, date INTEGER, unread_count INTEGER, last_mid INTEGER, inbox_max INTEGER, outbox_max INTEGER, last_mid_i INTEGER, unread_count_i INTEGER, pts INTEGER, date_i INTEGER, pinned INTEGER, flags INTEGER, folder_id INTEGER, data BLOB, unread_reactions INTEGER, last_mid_group INTEGER, ttl_period INTEGER)")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS date_idx_dialogs ON dialogs(date);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS last_mid_idx_dialogs ON dialogs(last_mid);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS unread_count_idx_dialogs ON dialogs(unread_count);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS last_mid_i_idx_dialogs ON dialogs(last_mid_i);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS unread_count_i_idx_dialogs ON dialogs(unread_count_i);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS folder_id_idx_dialogs ON dialogs(folder_id);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS flags_idx_dialogs ON dialogs(flags);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE dialog_filter(id INTEGER PRIMARY KEY, ord INTEGER, unread_count INTEGER, flags INTEGER, title TEXT)")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE dialog_filter_ep(id INTEGER, peer INTEGER, PRIMARY KEY (id, peer))")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE dialog_filter_pin_v2(id INTEGER, peer INTEGER, pin INTEGER, PRIMARY KEY (id, peer))")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE randoms_v2(random_id INTEGER, mid INTEGER, uid INTEGER, PRIMARY KEY (random_id, mid, uid))")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS mid_idx_randoms_v2 ON randoms_v2(mid, uid);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE enc_tasks_v4(mid INTEGER, uid INTEGER, date INTEGER, media INTEGER, PRIMARY KEY(mid, uid, media))")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS date_idx_enc_tasks_v4 ON enc_tasks_v4(date);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE messages_seq(mid INTEGER PRIMARY KEY, seq_in INTEGER, seq_out INTEGER);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS seq_idx_messages_seq ON messages_seq(seq_in, seq_out);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE params(id INTEGER PRIMARY KEY, seq INTEGER, pts INTEGER, date INTEGER, qts INTEGER, lsv INTEGER, sg INTEGER, pbytes BLOB)")
            .stepThis().dispose()
        database.executeFast("INSERT INTO params VALUES(1, 0, 0, 0, 0, 0, 0, NULL)").stepThis()
            .dispose()

        database.executeFast("CREATE TABLE media_v4(mid INTEGER, uid INTEGER, date INTEGER, type INTEGER, data BLOB, PRIMARY KEY(mid, uid, type))")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_type_date_idx_media_v4 ON media_v4(uid, mid, type, date);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE bot_keyboard(uid INTEGER PRIMARY KEY, mid INTEGER, info BLOB)")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS bot_keyboard_idx_mid_v2 ON bot_keyboard(mid, uid);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE bot_keyboard_topics(uid INTEGER, tid INTEGER, mid INTEGER, info BLOB, PRIMARY KEY(uid, tid))")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS bot_keyboard_topics_idx_mid_v2 ON bot_keyboard_topics(mid, uid, tid);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE chat_settings_v2(uid INTEGER PRIMARY KEY, info BLOB, pinned INTEGER, online INTEGER, inviter INTEGER, links INTEGER)")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS chat_settings_pinned_idx ON chat_settings_v2(uid, pinned) WHERE pinned != 0;")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE user_settings(uid INTEGER PRIMARY KEY, info BLOB, pinned INTEGER)")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS user_settings_pinned_idx ON user_settings(uid, pinned) WHERE pinned != 0;")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE chat_pinned_v2(uid INTEGER, mid INTEGER, data BLOB, PRIMARY KEY (uid, mid));")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE chat_pinned_count(uid INTEGER PRIMARY KEY, count INTEGER, end INTEGER);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE chat_hints(did INTEGER, type INTEGER, rating REAL, date INTEGER, PRIMARY KEY(did, type))")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS chat_hints_rating_idx ON chat_hints(rating);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE botcache(id TEXT PRIMARY KEY, date INTEGER, data BLOB)")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS botcache_date_idx ON botcache(date);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE users_data(uid INTEGER PRIMARY KEY, about TEXT)")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE users(uid INTEGER PRIMARY KEY, name TEXT, status INTEGER, data BLOB)")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE chats(uid INTEGER PRIMARY KEY, name TEXT, data BLOB)")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE enc_chats(uid INTEGER PRIMARY KEY, user INTEGER, name TEXT, data BLOB, g BLOB, authkey BLOB, ttl INTEGER, layer INTEGER, seq_in INTEGER, seq_out INTEGER, use_count INTEGER, exchange_id INTEGER, key_date INTEGER, fprint INTEGER, fauthkey BLOB, khash BLOB, in_seq_no INTEGER, admin_id INTEGER, mtproto_seq INTEGER)")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE channel_users_v2(did INTEGER, uid INTEGER, date INTEGER, data BLOB, PRIMARY KEY(did, uid))")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE channel_admins_v3(did INTEGER, uid INTEGER, data BLOB, PRIMARY KEY(did, uid))")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE contacts(uid INTEGER PRIMARY KEY, mutual INTEGER)")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE user_photos(uid INTEGER, id INTEGER, data BLOB, PRIMARY KEY (uid, id))")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE dialog_settings(did INTEGER PRIMARY KEY, flags INTEGER);")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE web_recent_v3(id TEXT, type INTEGER, image_url TEXT, thumb_url TEXT, local_url TEXT, width INTEGER, height INTEGER, size INTEGER, date INTEGER, document BLOB, PRIMARY KEY (id, type));")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE stickers_v2(id INTEGER PRIMARY KEY, data BLOB, date INTEGER, hash INTEGER);")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE stickers_featured(id INTEGER PRIMARY KEY, data BLOB, unread BLOB, date INTEGER, hash INTEGER, premium INTEGER, emoji INTEGER);")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE stickers_dice(emoji TEXT PRIMARY KEY, data BLOB, date INTEGER);")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE hashtag_recent_v2(id TEXT PRIMARY KEY, date INTEGER);")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE webpage_pending_v2(id INTEGER, mid INTEGER, uid INTEGER, PRIMARY KEY (id, mid, uid));")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE sent_files_v2(uid TEXT, type INTEGER, data BLOB, parent TEXT, PRIMARY KEY (uid, type))")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE search_recent(did INTEGER PRIMARY KEY, date INTEGER);")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE media_counts_v2(uid INTEGER, type INTEGER, count INTEGER, old INTEGER, PRIMARY KEY(uid, type))")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE keyvalue(id TEXT PRIMARY KEY, value TEXT)").stepThis()
            .dispose()
        database.executeFast("CREATE TABLE bot_info_v2(uid INTEGER, dialogId INTEGER, info BLOB, PRIMARY KEY(uid, dialogId))")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE pending_tasks(id INTEGER PRIMARY KEY, data BLOB);")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE requested_holes(uid INTEGER, seq_out_start INTEGER, seq_out_end INTEGER, PRIMARY KEY (uid, seq_out_start, seq_out_end));")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE sharing_locations(uid INTEGER PRIMARY KEY, mid INTEGER, date INTEGER, period INTEGER, message BLOB, proximity INTEGER);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE stickersets(id INTEGER PRIMATE KEY, data BLOB, hash INTEGER);")
            .stepThis().dispose()

        database.executeFast("CREATE INDEX IF NOT EXISTS stickers_featured_emoji_index ON stickers_featured(emoji);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE shortcut_widget(id INTEGER, did INTEGER, ord INTEGER, PRIMARY KEY (id, did));")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS shortcut_widget_did ON shortcut_widget(did);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE emoji_keywords_v2(lang TEXT, keyword TEXT, emoji TEXT, PRIMARY KEY(lang, keyword, emoji));")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS emoji_keywords_v2_keyword ON emoji_keywords_v2(keyword);")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE emoji_keywords_info_v2(lang TEXT PRIMARY KEY, alias TEXT, version INTEGER, date INTEGER);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE wallpapers2(uid INTEGER PRIMARY KEY, data BLOB, num INTEGER)")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS wallpapers_num ON wallpapers2(num);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE unread_push_messages(uid INTEGER, mid INTEGER, random INTEGER, date INTEGER, data BLOB, fm TEXT, name TEXT, uname TEXT, flags INTEGER, PRIMARY KEY(uid, mid))")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS unread_push_messages_idx_date ON unread_push_messages(date);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS unread_push_messages_idx_random ON unread_push_messages(random);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE polls_v2(mid INTEGER, uid INTEGER, id INTEGER, PRIMARY KEY (mid, uid));")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS polls_id_v2 ON polls_v2(id);").stepThis()
            .dispose()

        database.executeFast("CREATE TABLE reactions(data BLOB, hash INTEGER, date INTEGER);")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE reaction_mentions(message_id INTEGER, state INTEGER, dialog_id INTEGER, PRIMARY KEY(message_id, dialog_id))")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS reaction_mentions_did ON reaction_mentions(dialog_id);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE downloading_documents(data BLOB, hash INTEGER, id INTEGER, state INTEGER, date INTEGER, PRIMARY KEY(hash, id));")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE animated_emoji(document_id INTEGER PRIMARY KEY, data BLOB);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE attach_menu_bots(data BLOB, hash INTEGER, date INTEGER);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE premium_promo(data BLOB, date INTEGER);").stepThis()
            .dispose()
        database.executeFast("CREATE TABLE emoji_statuses(data BLOB, type INTEGER);").stepThis()
            .dispose()

        database.executeFast("CREATE TABLE messages_holes_topics(uid INTEGER, topic_id INTEGER, start INTEGER, end INTEGER, PRIMARY KEY(uid, topic_id, start));")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_end_messages_holes ON messages_holes_topics(uid, topic_id, end);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE messages_topics(mid INTEGER, uid INTEGER, topic_id INTEGER, read_state INTEGER, send_state INTEGER, date INTEGER, data BLOB, out INTEGER, ttl INTEGER, media INTEGER, replydata BLOB, imp INTEGER, mention INTEGER, forwards INTEGER, replies_data BLOB, thread_reply_id INTEGER, is_channel INTEGER, reply_to_message_id INTEGER, custom_params BLOB, reply_to_story_id INTEGER, PRIMARY KEY(mid, topic_id, uid))")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_date_mid_idx_messages_topics ON messages_topics(uid, date, mid);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS mid_out_idx_messages_topics ON messages_topics(mid, out);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS task_idx_messages_topics ON messages_topics(uid, out, read_state, ttl, date, send_state);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS send_state_idx_messages_topics ON messages_topics(mid, send_state, date);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS is_channel_idx_messages_topics ON messages_topics(mid, is_channel);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS reply_to_idx_messages_topics ON messages_topics(mid, reply_to_message_id);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS idx_to_reply_messages_topics ON messages_topics(reply_to_message_id, mid);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS mid_uid_messages_topics ON messages_topics(mid, uid);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_read_out_idx_messages_topics ON messages_topics(uid, topic_id, mid, read_state, out);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_mention_idx_messages_topics ON messages_topics(uid, topic_id, mention, read_state);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_topic_id_messages_topics ON messages_topics(uid, topic_id);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_topic_id_date_mid_messages_topics ON messages_topics(uid, topic_id, date, mid);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_topic_id_mid_messages_topics ON messages_topics(uid, topic_id, mid);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE media_topics(mid INTEGER, uid INTEGER, topic_id INTEGER, date INTEGER, type INTEGER, data BLOB, PRIMARY KEY(mid, uid, topic_id, type))")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_mid_type_date_idx_media_topics ON media_topics(uid, topic_id, mid, type, date);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE media_holes_topics(uid INTEGER, topic_id INTEGER, type INTEGER, start INTEGER, end INTEGER, PRIMARY KEY(uid, topic_id, type, start));")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS uid_end_media_holes_topics ON media_holes_topics(uid, topic_id, type, end);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE topics(did INTEGER, topic_id INTEGER, data BLOB, top_message INTEGER, topic_message BLOB, unread_count INTEGER, max_read_id INTEGER, unread_mentions INTEGER, unread_reactions INTEGER, read_outbox INTEGER, pinned INTEGER, total_messages_count INTEGER, hidden INTEGER, PRIMARY KEY(did, topic_id));")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS did_top_message_topics ON topics(did, top_message);")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS did_topics ON topics(did);").stepThis()
            .dispose()

        database.executeFast("CREATE TABLE media_counts_topics(uid INTEGER, topic_id INTEGER, type INTEGER, count INTEGER, old INTEGER, PRIMARY KEY(uid, topic_id, type))")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE reaction_mentions_topics(message_id INTEGER, state INTEGER, dialog_id INTEGER, topic_id INTEGER, PRIMARY KEY(message_id, dialog_id, topic_id))")
            .stepThis().dispose()
        database.executeFast("CREATE INDEX IF NOT EXISTS reaction_mentions_topics_did ON reaction_mentions_topics(dialog_id, topic_id);")
            .stepThis().dispose()

        database.executeFast("CREATE TABLE emoji_groups(type INTEGER PRIMARY KEY, data BLOB)")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE app_config(data BLOB)").stepThis().dispose()

        database.executeFast("CREATE TABLE stories (dialog_id INTEGER, story_id INTEGER, data BLOB, local_path TEXT, local_thumb_path TEXT, PRIMARY KEY (dialog_id, story_id));")
            .stepThis().dispose()
        database.executeFast("CREATE TABLE stories_counter (dialog_id INTEGER PRIMARY KEY, count INTEGER, max_read INTEGER);")
            .stepThis().dispose()

        database.executeFast("PRAGMA user_version = 120").stepThis().dispose()
    }
}