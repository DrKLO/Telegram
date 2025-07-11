package me.telegraphy.android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DatabaseManager extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "telegraphy.db";
    private static final int DATABASE_VERSION = 1;

    private static volatile DatabaseManager Instance = null;

    // Table Names
    private static final String TABLE_USERS = "users";
    private static final String TABLE_CHATS = "chats";
    private static final String TABLE_MESSAGES = "messages";
    private static final String TABLE_DIALOGS = "dialogs";
    private static final String TABLE_CONTACTS = "contacts";
    private static final String TABLE_STICKER_SETS = "sticker_sets";
    private static final String TABLE_STICKERS = "stickers";
    private static final String TABLE_REACTIONS = "reactions";
    private static final String TABLE_MESSAGE_REACTIONS = "message_reactions";
    private static final String TABLE_DRAFTS = "drafts";
    private static final String TABLE_CHAT_USERS = "chat_users"; // Junction table for many-to-many between chats and users
    private static final String TABLE_MEDIA = "media";
    private static final String TABLE_WEBPAGE_INFO = "webpage_info";
    private static final String TABLE_DIALOG_FILTERS = "dialog_filters";
    private static final String TABLE_DIALOG_FILTER_PEERS = "dialog_filter_peers"; // For pinned, include, exclude

    // Common column names
    private static final String KEY_ID = "id"; // Common primary key

    // Users Table Columns
    private static final String KEY_USER_ID = "user_id"; // TLRPC.User.id
    private static final String KEY_USER_FIRST_NAME = "first_name";
    private static final String KEY_USER_LAST_NAME = "last_name";
    private static final String KEY_USER_USERNAME = "username";
    private static final String KEY_USER_PHONE = "phone";
    private static final String KEY_USER_PHOTO = "photo"; // Store as serialized TLRPC.UserProfilePhoto or path
    private static final String KEY_USER_STATUS_EXPIRES = "status_expires"; // TLRPC.UserStatus.expires
    private static final String KEY_USER_IS_BOT = "is_bot";
    private static final String KEY_USER_IS_CONTACT = "is_contact";

    // Chats Table Columns
    private static final String KEY_CHAT_ID = "chat_id"; // TLRPC.Chat.id (use negative for chats/channels)
    private static final String KEY_CHAT_TITLE = "title";
    private static final String KEY_CHAT_PHOTO = "photo"; // Store as serialized TLRPC.ChatPhoto or path
    private static final String KEY_CHAT_PARTICIPANTS_COUNT = "participants_count";
    private static final String KEY_CHAT_TYPE = "type"; // e.g., "chat", "group", "channel"
    private static final String KEY_CHAT_ADMIN_RIGHTS = "admin_rights"; // Serialized TLRPC.ChatAdminRights
    private static final String KEY_CHAT_DEFAULT_BANNED_RIGHTS = "default_banned_rights"; // Serialized TLRPC.ChatBannedRights
    private static final String KEY_CHAT_IS_MEGAGROUP = "is_megagroup";
    private static final String KEY_CHAT_IS_FORUM = "is_forum";

    // Messages Table Columns
    private static final String KEY_MESSAGE_ID = "message_id"; // TLRPC.Message.id
    private static final String KEY_MESSAGE_DIALOG_ID = "dialog_id"; // To which dialog this message belongs
    private static final String KEY_MESSAGE_FROM_ID = "from_id"; // User ID of sender
    private static final String KEY_MESSAGE_PEER_ID = "peer_id"; // User or Chat ID where message was sent
    private static final String KEY_MESSAGE_TEXT = "message_text";
    private static final String KEY_MESSAGE_DATE = "date";
    private static final String KEY_MESSAGE_MEDIA_TYPE = "media_type"; // String identifier like "photo", "video"
    private static final String KEY_MESSAGE_MEDIA_DATA = "media_data"; // Serialized TLRPC.MessageMedia
    private static final String KEY_MESSAGE_ENTITIES = "entities"; // Serialized ArrayList<TLRPC.MessageEntity>
    private static final String KEY_MESSAGE_REPLY_TO_MSG_ID = "reply_to_msg_id";
    private static final String KEY_MESSAGE_FWD_FROM_CHAT_ID = "fwd_from_chat_id";
    private static final String KEY_MESSAGE_FWD_FROM_MSG_ID = "fwd_from_msg_id";
    private static final String KEY_MESSAGE_VIEWS = "views";
    private static final String KEY_MESSAGE_OUT = "out"; // 1 if outgoing, 0 if incoming
    private static final String KEY_MESSAGE_SEND_STATE = "send_state"; // e.g., sending, sent, failed
    private static final String KEY_MESSAGE_LOCAL_ID = "local_id"; // for unsent messages

    // Dialogs Table Columns
    private static final String KEY_DIALOG_ID = "dialog_id"; // User or Chat ID
    private static final String KEY_DIALOG_TOP_MESSAGE_ID = "top_message_id";
    private static final String KEY_DIALOG_UNREAD_COUNT = "unread_count";
    private static final String KEY_DIALOG_LAST_MESSAGE_DATE = "last_message_date";
    private static final String KEY_DIALOG_NOTIFY_SETTINGS = "notify_settings"; // Serialized TLRPC.PeerNotifySettings
    private static final String KEY_DIALOG_PINNED = "pinned"; // 1 if pinned, 0 otherwise
    private static final String KEY_DIALOG_FOLDER_ID = "folder_id";
    private static final String KEY_DIALOG_DRAFT_MESSAGE = "draft_message"; // Serialized TLRPC.DraftMessage

    // Media Table Columns (Example - might need more specific tables or JSON for flexibility)
    private static final String KEY_MEDIA_ID = "media_id"; // Unique ID for the media item
    private static final String KEY_MEDIA_REMOTE_ID = "remote_id"; // e.g., photo.id or document.id
    private static final String KEY_MEDIA_ACCESS_HASH = "access_hash";
    private static final String KEY_MEDIA_LOCAL_PATH = "local_path";
    private static final String KEY_MEDIA_SIZE = "size";
    private static final String KEY_MEDIA_MIME_TYPE = "mime_type";
    private static final String KEY_MEDIA_WIDTH = "width";
    private static final String KEY_MEDIA_HEIGHT = "height";
    private static final String KEY_MEDIA_DURATION = "duration";


    public static DatabaseManager getInstance() {
        DatabaseManager localInstance = Instance;
        if (localInstance == null) {
            synchronized (DatabaseManager.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new DatabaseManager(ApplicationLoader.applicationContext);
                }
            }
        }
        return localInstance;
    }

    private DatabaseManager(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create Users Table
        String CREATE_USERS_TABLE = "CREATE TABLE " + TABLE_USERS + "("
                + KEY_USER_ID + " INTEGER PRIMARY KEY,"
                + KEY_USER_FIRST_NAME + " TEXT,"
                + KEY_USER_LAST_NAME + " TEXT,"
                + KEY_USER_USERNAME + " TEXT UNIQUE,"
                + KEY_USER_PHONE + " TEXT,"
                + KEY_USER_PHOTO + " TEXT,"
                + KEY_USER_STATUS_EXPIRES + " INTEGER,"
                + KEY_USER_IS_BOT + " INTEGER DEFAULT 0,"
                + KEY_USER_IS_CONTACT + " INTEGER DEFAULT 0"
                + ")";
        db.execSQL(CREATE_USERS_TABLE);

        // Create Chats Table
        String CREATE_CHATS_TABLE = "CREATE TABLE " + TABLE_CHATS + "("
                + KEY_CHAT_ID + " INTEGER PRIMARY KEY," // Negative for chats/channels
                + KEY_CHAT_TITLE + " TEXT,"
                + KEY_CHAT_PHOTO + " TEXT,"
                + KEY_CHAT_PARTICIPANTS_COUNT + " INTEGER,"
                + KEY_CHAT_TYPE + " TEXT,"
                + KEY_CHAT_ADMIN_RIGHTS + " TEXT,"
                + KEY_CHAT_DEFAULT_BANNED_RIGHTS + " TEXT,"
                + KEY_CHAT_IS_MEGAGROUP + " INTEGER DEFAULT 0,"
                + KEY_CHAT_IS_FORUM + " INTEGER DEFAULT 0"
                + ")";
        db.execSQL(CREATE_CHATS_TABLE);

        // Create Chat Users Junction Table
        String CREATE_CHAT_USERS_TABLE = "CREATE TABLE " + TABLE_CHAT_USERS + "("
                + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KEY_CHAT_ID + " INTEGER,"
                + KEY_USER_ID + " INTEGER,"
                + "FOREIGN KEY(" + KEY_CHAT_ID + ") REFERENCES " + TABLE_CHATS + "(" + KEY_CHAT_ID + "),"
                + "FOREIGN KEY(" + KEY_USER_ID + ") REFERENCES " + TABLE_USERS + "(" + KEY_USER_ID + "),"
                + "UNIQUE (" + KEY_CHAT_ID + ", " + KEY_USER_ID + ")"
                + ")";
        db.execSQL(CREATE_CHAT_USERS_TABLE);
        db.execSQL("CREATE INDEX idx_chat_users_chat_id ON " + TABLE_CHAT_USERS + "(" + KEY_CHAT_ID + ");");
        db.execSQL("CREATE INDEX idx_chat_users_user_id ON " + TABLE_CHAT_USERS + "(" + KEY_USER_ID + ");");


        // Create Messages Table
        String CREATE_MESSAGES_TABLE = "CREATE TABLE " + TABLE_MESSAGES + "("
                + KEY_MESSAGE_ID + " INTEGER PRIMARY KEY,"
                + KEY_MESSAGE_DIALOG_ID + " INTEGER NOT NULL,"
                + KEY_MESSAGE_FROM_ID + " INTEGER,"
                + KEY_MESSAGE_PEER_ID + " INTEGER,"
                + KEY_MESSAGE_TEXT + " TEXT,"
                + KEY_MESSAGE_DATE + " INTEGER,"
                + KEY_MESSAGE_MEDIA_TYPE + " TEXT,"
                + KEY_MESSAGE_MEDIA_DATA + " TEXT," // Consider BLOB for direct serialization
                + KEY_MESSAGE_ENTITIES + " TEXT,"    // Consider BLOB
                + KEY_MESSAGE_REPLY_TO_MSG_ID + " INTEGER,"
                + KEY_MESSAGE_FWD_FROM_CHAT_ID + " INTEGER,"
                + KEY_MESSAGE_FWD_FROM_MSG_ID + " INTEGER,"
                + KEY_MESSAGE_VIEWS + " INTEGER,"
                + KEY_MESSAGE_OUT + " INTEGER,"
                + KEY_MESSAGE_SEND_STATE + " INTEGER,"
                + KEY_MESSAGE_LOCAL_ID + " INTEGER UNIQUE"
                // Foreign keys can be added here if strict enforcement is needed at DB level
                // e.g., FOREIGN KEY(from_id) REFERENCES users(user_id)
                + ")";
        db.execSQL(CREATE_MESSAGES_TABLE);
        db.execSQL("CREATE INDEX idx_messages_dialog_id_date ON " + TABLE_MESSAGES + "(" + KEY_MESSAGE_DIALOG_ID + ", " + KEY_MESSAGE_DATE + ");");
        db.execSQL("CREATE INDEX idx_messages_local_id ON " + TABLE_MESSAGES + "(" + KEY_MESSAGE_LOCAL_ID + ");");


        // Create Dialogs Table
        String CREATE_DIALOGS_TABLE = "CREATE TABLE " + TABLE_DIALOGS + "("
                + KEY_DIALOG_ID + " INTEGER PRIMARY KEY,"
                + KEY_DIALOG_TOP_MESSAGE_ID + " INTEGER,"
                + KEY_DIALOG_UNREAD_COUNT + " INTEGER,"
                + KEY_DIALOG_LAST_MESSAGE_DATE + " INTEGER,"
                + KEY_DIALOG_NOTIFY_SETTINGS + " TEXT,"
                + KEY_DIALOG_PINNED + " INTEGER DEFAULT 0,"
                + KEY_DIALOG_FOLDER_ID + " INTEGER DEFAULT 0,"
                + KEY_DIALOG_DRAFT_MESSAGE + " TEXT"
                + ")";
        db.execSQL(CREATE_DIALOGS_TABLE);
        db.execSQL("CREATE INDEX idx_dialogs_last_message_date ON " + TABLE_DIALOGS + "(" + KEY_DIALOG_LAST_MESSAGE_DATE + ");");
        db.execSQL("CREATE INDEX idx_dialogs_folder_id ON " + TABLE_DIALOGS + "(" + KEY_DIALOG_FOLDER_ID + ");");


        // Create Contacts Table
        String CREATE_CONTACTS_TABLE = "CREATE TABLE " + TABLE_CONTACTS + "("
                + KEY_USER_ID + " INTEGER PRIMARY KEY,"
                + KEY_USER_FIRST_NAME + " TEXT,"
                + KEY_USER_LAST_NAME + " TEXT"
                + ")";
        db.execSQL(CREATE_CONTACTS_TABLE);

        // Create Sticker Sets Table
        String CREATE_STICKER_SETS_TABLE = "CREATE TABLE " + TABLE_STICKER_SETS + "("
                + KEY_ID + " INTEGER PRIMARY KEY," // set_id
                + "title TEXT,"
                + "short_name TEXT UNIQUE,"
                + "count INTEGER,"
                + "hash INTEGER,"
                + "official INTEGER DEFAULT 0,"
                + "masks INTEGER DEFAULT 0,"
                + "emojis INTEGER DEFAULT 0"
                + ")";
        db.execSQL(CREATE_STICKER_SETS_TABLE);

        // Create Stickers Table
        String CREATE_STICKERS_TABLE = "CREATE TABLE " + TABLE_STICKERS + "("
                + KEY_ID + " INTEGER PRIMARY KEY," // document_id
                + "set_id INTEGER,"
                + "emoji TEXT,"
                + "FOREIGN KEY(set_id) REFERENCES " + TABLE_STICKER_SETS + "(" + KEY_ID + ")"
                + ")";
        db.execSQL(CREATE_STICKERS_TABLE);

        // Create Reactions Table (Available reactions)
        String CREATE_REACTIONS_TABLE = "CREATE TABLE " + TABLE_REACTIONS + "("
                + KEY_ID + " TEXT PRIMARY KEY," // reaction string like "👍"
                + "static_icon_path TEXT,"
                + "appear_animation_path TEXT,"
                + "select_animation_path TEXT"
                + ")";
        db.execSQL(CREATE_REACTIONS_TABLE);

        // Create Message Reactions Table
        String CREATE_MESSAGE_REACTIONS_TABLE = "CREATE TABLE " + TABLE_MESSAGE_REACTIONS + "("
                + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KEY_MESSAGE_ID + " INTEGER,"
                + KEY_USER_ID + " INTEGER,"
                + "reaction_id TEXT,"
                + "date INTEGER,"
                + "FOREIGN KEY(" + KEY_MESSAGE_ID + ") REFERENCES " + TABLE_MESSAGES + "(" + KEY_MESSAGE_ID + "),"
                + "FOREIGN KEY(" + KEY_USER_ID + ") REFERENCES " + TABLE_USERS + "(" + KEY_USER_ID + "),"
                + "FOREIGN KEY(reaction_id) REFERENCES " + TABLE_REACTIONS + "(" + KEY_ID + ")"
                + ")";
        db.execSQL(CREATE_MESSAGE_REACTIONS_TABLE);

        // Create Drafts Table
        String CREATE_DRAFTS_TABLE = "CREATE TABLE " + TABLE_DRAFTS + "("
                + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," // Or use dialog_id + thread_id as composite PK
                + KEY_MESSAGE_DIALOG_ID + " INTEGER NOT NULL,"
                + "thread_id INTEGER DEFAULT 0,"
                + KEY_MESSAGE_TEXT + " TEXT,"
                + KEY_MESSAGE_ENTITIES + " TEXT,"
                + KEY_MESSAGE_REPLY_TO_MSG_ID + " INTEGER,"
                + KEY_MESSAGE_DATE + " INTEGER,"
                + "no_webpage INTEGER DEFAULT 0,"
                + "UNIQUE (" + KEY_MESSAGE_DIALOG_ID + ", thread_id)"
                + ")";
        db.execSQL(CREATE_DRAFTS_TABLE);

        // Create Media Table
        String CREATE_MEDIA_TABLE = "CREATE TABLE " + TABLE_MEDIA + "("
                + KEY_MEDIA_ID + " INTEGER PRIMARY KEY,"
                + KEY_MEDIA_REMOTE_ID + " INTEGER,"
                + "media_type INTEGER," // 0: photo, 1: video, 2: audio, 3: document
                + KEY_MEDIA_ACCESS_HASH + " INTEGER,"
                + KEY_MEDIA_LOCAL_PATH + " TEXT,"
                + KEY_MEDIA_SIZE + " INTEGER,"
                + KEY_MEDIA_MIME_TYPE + " TEXT,"
                + KEY_MEDIA_WIDTH + " INTEGER,"
                + KEY_MEDIA_HEIGHT + " INTEGER,"
                + KEY_MEDIA_DURATION + " INTEGER"
                + ")";
        db.execSQL(CREATE_MEDIA_TABLE);

        // Create Webpage Info Table
        String CREATE_WEBPAGE_INFO_TABLE = "CREATE TABLE " + TABLE_WEBPAGE_INFO + "("
                + KEY_ID + " INTEGER PRIMARY KEY," // webpage_id from TLRPC
                + "url TEXT UNIQUE,"
                + "display_url TEXT,"
                + "type TEXT,"
                + "site_name TEXT,"
                + "title TEXT,"
                + "description TEXT,"
                + "photo_id INTEGER," // FK to a photo in media table or store photo data directly
                + "embed_url TEXT,"
                + "embed_type TEXT,"
                + "embed_width INTEGER,"
                + "embed_height INTEGER,"
                + "duration INTEGER,"
                + "author TEXT"
                + ")";
        db.execSQL(CREATE_WEBPAGE_INFO_TABLE);

        // Create Dialog Filters Table
        String CREATE_DIALOG_FILTERS_TABLE = "CREATE TABLE " + TABLE_DIALOG_FILTERS + "("
                + KEY_ID + " INTEGER PRIMARY KEY," // filter_id
                + "title TEXT,"
                + "flags INTEGER,"
                + "color INTEGER,"
                + "emoticon TEXT"
                + ")";
        db.execSQL(CREATE_DIALOG_FILTERS_TABLE);

        // Create Dialog Filter Peers Table
        String CREATE_DIALOG_FILTER_PEERS_TABLE = "CREATE TABLE " + TABLE_DIALOG_FILTER_PEERS + "("
                + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "filter_id INTEGER,"
                + "peer_id INTEGER," // dialog_id
                + "peer_type INTEGER," // 0: pinned, 1: include, 2: exclude
                + "FOREIGN KEY(filter_id) REFERENCES " + TABLE_DIALOG_FILTERS + "(" + KEY_ID + ")"
                + ")";
        db.execSQL(CREATE_DIALOG_FILTER_PEERS_TABLE);
        db.execSQL("CREATE INDEX idx_dialog_filter_peers_filter_id ON " + TABLE_DIALOG_FILTER_PEERS + "(filter_id);");

        FileLog.d("DatabaseManager: onCreate finished, tables created.");
    }


    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        FileLog.d(String.format(Locale.US, "DatabaseManager: Upgrading database from version %d to %d.", oldVersion, newVersion));
        // Basic upgrade strategy: drop old tables and recreate.
        // For a real app, you'd implement specific migration steps for each version.
        // Example:
        // if (oldVersion < 2) {
        //     db.execSQL("ALTER TABLE users ADD COLUMN new_column TEXT;");
        // }
        // if (oldVersion < 3) {
        //     // another migration
        // }
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHATS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DIALOGS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONTACTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_STICKER_SETS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_STICKERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_REACTIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGE_REACTIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DRAFTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHAT_USERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEDIA);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_WEBPAGE_INFO);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DIALOG_FILTERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DIALOG_FILTER_PEERS);
        onCreate(db);
        FileLog.d("DatabaseManager: onUpgrade finished.");
    }

    // Example CRUD operations (to be expanded)

    public long addUser(TLRPC.User user) {
        if (user == null) return -1;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_USER_ID, user.id);
        values.put(KEY_USER_FIRST_NAME, user.first_name);
        values.put(KEY_USER_LAST_NAME, user.last_name);
        values.put(KEY_USER_USERNAME, user.username);
        values.put(KEY_USER_PHONE, user.phone);
        // TODO: Serialize user.photo and user.status
        // values.put(KEY_USER_PHOTO, serialize(user.photo));
        values.put(KEY_USER_STATUS_EXPIRES, user.status != null ? user.status.expires : 0);
        values.put(KEY_USER_IS_BOT, user.bot ? 1 : 0);
        values.put(KEY_USER_IS_CONTACT, user.contact ? 1 : 0);

        long id = db.insertWithOnConflict(TABLE_USERS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        // db.close(); // Don't close if you use a singleton helper instance
        return id;
    }

    public TLRPC.User getUser(long userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, null, KEY_USER_ID + "=?",
                new String[]{String.valueOf(userId)}, null, null, null, null);
        TLRPC.User user = null;
        if (cursor != null && cursor.moveToFirst()) {
            user = new TLRPC.TL_user(); // Or specific type if stored
            user.id = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_USER_ID));
            user.first_name = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_FIRST_NAME));
            user.last_name = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_LAST_NAME));
            user.username = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_USERNAME));
            user.phone = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_PHONE));
            // TODO: Deserialize photo and status
            user.bot = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_USER_IS_BOT)) == 1;
            user.contact = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_USER_IS_CONTACT)) == 1;
            cursor.close();
        }
        return user;
    }

    public long addChat(TLRPC.Chat chat) {
        if (chat == null) return -1;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_CHAT_ID, chat.id); // Store as positive, convert to negative in logic if needed
        values.put(KEY_CHAT_TITLE, chat.title);
        // TODO: Serialize chat.photo, admin_rights, default_banned_rights
        values.put(KEY_CHAT_PARTICIPANTS_COUNT, chat.participants_count);
        String type = "chat";
        if (chat instanceof TLRPC.TL_channel) {
            type = ((TLRPC.TL_channel) chat).megagroup ? "megagroup" : "channel";
        } else if (chat instanceof TLRPC.TL_chatForbidden || chat instanceof TLRPC.TL_channelForbidden) {
            type = "forbidden";
        }
        values.put(KEY_CHAT_TYPE, type);
        values.put(KEY_CHAT_IS_MEGAGROUP, (chat instanceof TLRPC.TL_channel && ((TLRPC.TL_channel) chat).megagroup) ? 1: 0);
        values.put(KEY_CHAT_IS_FORUM, (chat.flags2 & TLRPC.CHAT_FLAG2_IS_FORUM) != 0 ? 1: 0);


        long id = db.insertWithOnConflict(TABLE_CHATS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        return id;
    }

    public long addMessage(TLRPC.Message message) {
        if (message == null) return -1;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_MESSAGE_ID, message.id);
        values.put(KEY_MESSAGE_DIALOG_ID, message.dialog_id);
        values.put(KEY_MESSAGE_FROM_ID, MessageObject.getFromChatId(message));
        values.put(KEY_MESSAGE_PEER_ID, MessageObject.getPeerId(message.peer_id));
        values.put(KEY_MESSAGE_TEXT, message.message);
        values.put(KEY_MESSAGE_DATE, message.date);
        // TODO: Serialize media, entities, reply_to, fwd_from
        if (message.media != null) {
            // values.put(KEY_MESSAGE_MEDIA_DATA, serialize(message.media));
            // Determine media type string
        }
        values.put(KEY_MESSAGE_VIEWS, message.views);
        values.put(KEY_MESSAGE_OUT, message.out ? 1 : 0);
        // values.put(KEY_MESSAGE_SEND_STATE, ...); // Determine based on message status
        values.put(KEY_MESSAGE_LOCAL_ID, message.local_id != 0 ? message.local_id : null);


        long id = db.insertWithOnConflict(TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        return id;
    }

    public List<TLRPC.Message> getMessagesForDialog(long dialogId, int limit, int offset) {
        List<TLRPC.Message> messages = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_MESSAGES, null, KEY_MESSAGE_DIALOG_ID + "=?",
                new String[]{String.valueOf(dialogId)}, null, null, KEY_MESSAGE_DATE + " DESC",
                offset + "," + limit);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                TLRPC.Message message = new TLRPC.TL_message(); // Or specific type if stored
                message.id = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_MESSAGE_ID));
                message.dialog_id = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_MESSAGE_DIALOG_ID));
                // ... populate other fields
                // TODO: Deserialize media, entities etc.
                messages.add(message);
            }
            cursor.close();
        }
        return messages;
    }

    // TODO: Implement methods for all CRUD operations for all tables
    // - insert, update, delete, query specific items, query all items
    // - Handle serialization/deserialization for complex objects (like TLRPC.Photo, TLRPC.MessageMedia)
    //   You might store them as JSON strings or BLOBs.

    // Helper for serialization (example - use a proper library like Gson or Jackson if allowed, or manual for TLRPC)
    // private String serialize(TLObject object) { /* ... */ return null; }
    // private <T extends TLObject> T deserialize(String data, Class<T> clazz) { /* ... */ return null; }
}
