package org.telegram.SQLite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.Favourite;

public class DatabaseHandler extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "favourites";
    private static final String TABLE_FAVS = "tbl_favs";

    private static final String KEY_ID = "id";
    private static final String KEY_CHAT_ID = "chat_id";

    public DatabaseHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_FAVS_TABLE = "CREATE TABLE " + TABLE_FAVS + "("
                + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + KEY_CHAT_ID + " INTEGER" + ")";
        db.execSQL(CREATE_FAVS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FAVS);
        onCreate(db);
    }

    public void addFavourite(Favourite favourite) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_CHAT_ID, favourite.getChatID());
        db.insert(TABLE_FAVS, null, values);
        db.close();
    }

    public Favourite getFavouriteByChatId(long chat_id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {

            String [] projection = {
                    KEY_ID,
                    KEY_CHAT_ID
            };

            String whereClause = KEY_CHAT_ID+"=?";
            String [] whereArgs = {String.valueOf(chat_id)};

            cursor = db.query(
                    TABLE_FAVS,
                    projection,
                    whereClause,
                    whereArgs,
                    null,
                    null,
                    null
            );

            if( cursor != null && cursor.moveToFirst() ){
                return new Favourite(cursor.getLong(1));
            }
        } catch (Exception e) {
            if(cursor != null)
                cursor.close();
            FileLog.e("tmessages", e);
            return null;
        } finally {
            if(cursor != null)
                cursor.close();
        }
        return null;
    }

    public void deleteFavourite(Long chat_id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_FAVS, KEY_CHAT_ID + " = ?", new String[] { String.valueOf(chat_id) });
        db.close();
    }

    /*public List<Favourite> getAllFavourites() {
        List<Favourite> favsList = new ArrayList<Favourite>();

        String selectQuery = "SELECT  * FROM " + TABLE_FAVS;

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                Favourite favourite = new Favourite();
                favourite.setID(Integer.parseInt(cursor.getString(0)));
                favourite.setChatID(cursor.getLong(1));

                favsList.add(favourite);
            } while (cursor.moveToNext());
        }

        return favsList;
    }

    public int getFavouritesCount() {
        String countQuery = "SELECT  * FROM " + TABLE_FAVS;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(countQuery, null);
        cursor.close();

        return cursor.getCount();
    }*/

}
