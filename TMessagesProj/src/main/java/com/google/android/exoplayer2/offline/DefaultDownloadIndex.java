/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.offline;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A {@link DownloadIndex} which uses SQLite to persist {@link DownloadState}s.
 *
 * <p class="caution">Database access may take a long time, do not call methods of this class from
 * the application main thread.
 */
public final class DefaultDownloadIndex implements DownloadIndex {

  /** Provides {@link SQLiteDatabase} instances. */
  public interface DatabaseProvider {
    /** Closes any open database object. */
    void close();

    /**
     * Creates and/or opens a database that will be used for reading and writing.
     *
     * <p>Once opened successfully, the database is cached, so you can call this method every time
     * you need to write to the database. (Make sure to call {@link #close} when you no longer need
     * the database.) Errors such as bad permissions or a full disk may cause this method to fail,
     * but future attempts may succeed if the problem is fixed.
     *
     * @throws SQLiteException If the database cannot be opened for writing.
     * @return A read/write database object valid until {@link #close} is called.
     */
    SQLiteDatabase getWritableDatabase();

    /**
     * Creates and/or opens a database. This will be the same object returned by {@link
     * #getWritableDatabase} unless some problem, such as a full disk, requires the database to be
     * opened read-only. In that case, a read-only database object will be returned. If the problem
     * is fixed, a future call to {@link #getWritableDatabase} may succeed, in which case the
     * read-only database object will be closed and the read/write object will be returned in the
     * future.
     *
     * <p>Once opened successfully, the database should be cached. When the database is no longer
     * needed, {@link #close} will be called.
     *
     * @throws SQLiteException If the database cannot be opened.
     * @return A database object valid until {@link #getWritableDatabase} or {@link #close} is
     *     called.
     */
    SQLiteDatabase getReadableDatabase();
  }

  private static final String DATABASE_NAME = "exoplayer_internal.db";

  private final DatabaseProvider databaseProvider;
  @Nullable private DownloadStateTable downloadStateTable;

  /**
   * Creates a DefaultDownloadIndex which stores the {@link DownloadState}s on a SQLite database.
   *
   * @param context A Context.
   */
  public DefaultDownloadIndex(Context context) {
    this(new DefaultDatabaseProvider(context));
  }

  /**
   * Creates a DefaultDownloadIndex which stores the {@link DownloadState}s on a SQLite database
   * provided by {@code databaseProvider}.
   *
   * @param databaseProvider A DatabaseProvider which provides the database which will be used to
   *     store DownloadStatus table.
   */
  public DefaultDownloadIndex(DatabaseProvider databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public void release() {
    databaseProvider.close();
  }

  @Override
  @Nullable
  public DownloadState getDownloadState(String id) {
    return getDownloadStateTable().get(id);
  }

  @Override
  public DownloadStateCursor getDownloadStates(@DownloadState.State int... states) {
    return getDownloadStateTable().get(states);
  }

  @Override
  public void putDownloadState(DownloadState downloadState) {
    getDownloadStateTable().replace(downloadState);
  }

  @Override
  public void removeDownloadState(String id) {
    getDownloadStateTable().delete(id);
  }

  private DownloadStateTable getDownloadStateTable() {
    if (downloadStateTable == null) {
      downloadStateTable = new DownloadStateTable(databaseProvider);
    }
    return downloadStateTable;
  }

  @VisibleForTesting
  /* package */ static boolean doesTableExist(DatabaseProvider databaseProvider, String tableName) {
    SQLiteDatabase readableDatabase = databaseProvider.getReadableDatabase();
    long count =
        DatabaseUtils.queryNumEntries(
            readableDatabase, "sqlite_master", "tbl_name = ?", new String[] {tableName});
    return count > 0;
  }

  private static final class DownloadStateCursorImpl implements DownloadStateCursor {

    private final Cursor cursor;

    private DownloadStateCursorImpl(Cursor cursor) {
      this.cursor = cursor;
    }

    @Override
    public DownloadState getDownloadState() {
      return DownloadStateTable.getDownloadState(cursor);
    }

    @Override
    public int getCount() {
      return cursor.getCount();
    }

    @Override
    public int getPosition() {
      return cursor.getPosition();
    }

    @Override
    public boolean moveToPosition(int position) {
      return cursor.moveToPosition(position);
    }

    @Override
    public void close() {
      cursor.close();
    }

    @Override
    public boolean isClosed() {
      return cursor.isClosed();
    }
  }

  @VisibleForTesting
  /* package */ static final class DownloadStateTable {
    @VisibleForTesting /* package */ static final String TABLE_NAME = "ExoPlayerDownloadStates";
    @VisibleForTesting /* package */ static final int TABLE_VERSION = 1;

    private static final String COLUMN_ID = "id";
    private static final String COLUMN_TYPE = "title";
    private static final String COLUMN_URI = "subtitle";
    private static final String COLUMN_CACHE_KEY = "cache_key";
    private static final String COLUMN_STATE = "state";
    private static final String COLUMN_DOWNLOAD_PERCENTAGE = "download_percentage";
    private static final String COLUMN_DOWNLOADED_BYTES = "downloaded_bytes";
    private static final String COLUMN_TOTAL_BYTES = "total_bytes";
    private static final String COLUMN_FAILURE_REASON = "failure_reason";
    private static final String COLUMN_STOP_FLAGS = "stop_flags";
    private static final String COLUMN_START_TIME_MS = "start_time_ms";
    private static final String COLUMN_UPDATE_TIME_MS = "update_time_ms";
    private static final String COLUMN_STREAM_KEYS = "stream_keys";
    private static final String COLUMN_CUSTOM_METADATA = "custom_metadata";

    private static final int COLUMN_INDEX_ID = 0;
    private static final int COLUMN_INDEX_TYPE = 1;
    private static final int COLUMN_INDEX_URI = 2;
    private static final int COLUMN_INDEX_CACHE_KEY = 3;
    private static final int COLUMN_INDEX_STATE = 4;
    private static final int COLUMN_INDEX_DOWNLOAD_PERCENTAGE = 5;
    private static final int COLUMN_INDEX_DOWNLOADED_BYTES = 6;
    private static final int COLUMN_INDEX_TOTAL_BYTES = 7;
    private static final int COLUMN_INDEX_FAILURE_REASON = 8;
    private static final int COLUMN_INDEX_STOP_FLAGS = 9;
    private static final int COLUMN_INDEX_START_TIME_MS = 10;
    private static final int COLUMN_INDEX_UPDATE_TIME_MS = 11;
    private static final int COLUMN_INDEX_STREAM_KEYS = 12;
    private static final int COLUMN_INDEX_CUSTOM_METADATA = 13;

    private static final String COLUMN_SELECTION_ID = COLUMN_ID + " = ?";

    private static final String[] COLUMNS =
        new String[] {
          COLUMN_ID,
          COLUMN_TYPE,
          COLUMN_URI,
          COLUMN_CACHE_KEY,
          COLUMN_STATE,
          COLUMN_DOWNLOAD_PERCENTAGE,
          COLUMN_DOWNLOADED_BYTES,
          COLUMN_TOTAL_BYTES,
          COLUMN_FAILURE_REASON,
          COLUMN_STOP_FLAGS,
          COLUMN_START_TIME_MS,
          COLUMN_UPDATE_TIME_MS,
          COLUMN_STREAM_KEYS,
          COLUMN_CUSTOM_METADATA
        };

    private static final String SQL_DROP_TABLE = "DROP TABLE IF EXISTS " + TABLE_NAME;
    private static final String SQL_CREATE_TABLE =
        "CREATE TABLE IF NOT EXISTS "
            + TABLE_NAME
            + " ("
            + COLUMN_ID
            + " TEXT PRIMARY KEY NOT NULL,"
            + COLUMN_TYPE
            + " TEXT NOT NULL,"
            + COLUMN_URI
            + " TEXT NOT NULL,"
            + COLUMN_CACHE_KEY
            + " TEXT,"
            + COLUMN_STATE
            + " INTEGER NOT NULL,"
            + COLUMN_DOWNLOAD_PERCENTAGE
            + " REAL NOT NULL,"
            + COLUMN_DOWNLOADED_BYTES
            + " INTEGER NOT NULL,"
            + COLUMN_TOTAL_BYTES
            + " INTEGER NOT NULL,"
            + COLUMN_FAILURE_REASON
            + " INTEGER NOT NULL,"
            + COLUMN_STOP_FLAGS
            + " INTEGER NOT NULL,"
            + COLUMN_START_TIME_MS
            + " INTEGER NOT NULL,"
            + COLUMN_UPDATE_TIME_MS
            + " INTEGER NOT NULL,"
            + COLUMN_STREAM_KEYS
            + " TEXT NOT NULL,"
            + COLUMN_CUSTOM_METADATA
            + " BLOB NOT NULL)";

    private final DatabaseProvider databaseProvider;

    public DownloadStateTable(DatabaseProvider databaseProvider) {
      this.databaseProvider = databaseProvider;
      VersionTable versionTable = new VersionTable(databaseProvider);
      int version = versionTable.getVersion(VersionTable.FEATURE_OFFLINE);
      if (!doesTableExist(databaseProvider, TABLE_NAME)
          || version == 0
          || version > TABLE_VERSION) {
        SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
        writableDatabase.beginTransaction();
        try {
          writableDatabase.execSQL(SQL_DROP_TABLE);
          writableDatabase.execSQL(SQL_CREATE_TABLE);
          versionTable.setVersion(VersionTable.FEATURE_OFFLINE, TABLE_VERSION);
          writableDatabase.setTransactionSuccessful();
        } finally {
          writableDatabase.endTransaction();
        }
      } else if (version < TABLE_VERSION) {
        // There is no previous version currently.
        throw new IllegalStateException();
      }
    }

    public void replace(DownloadState downloadState) {
      ContentValues values = new ContentValues();
      values.put(COLUMN_ID, downloadState.id);
      values.put(COLUMN_TYPE, downloadState.type);
      values.put(COLUMN_URI, downloadState.uri.toString());
      values.put(COLUMN_CACHE_KEY, downloadState.cacheKey);
      values.put(COLUMN_STATE, downloadState.state);
      values.put(COLUMN_DOWNLOAD_PERCENTAGE, downloadState.downloadPercentage);
      values.put(COLUMN_DOWNLOADED_BYTES, downloadState.downloadedBytes);
      values.put(COLUMN_TOTAL_BYTES, downloadState.totalBytes);
      values.put(COLUMN_FAILURE_REASON, downloadState.failureReason);
      values.put(COLUMN_STOP_FLAGS, downloadState.stopFlags);
      values.put(COLUMN_START_TIME_MS, downloadState.startTimeMs);
      values.put(COLUMN_UPDATE_TIME_MS, downloadState.updateTimeMs);
      values.put(COLUMN_STREAM_KEYS, encodeStreamKeys(downloadState.streamKeys));
      values.put(COLUMN_CUSTOM_METADATA, downloadState.customMetadata);
      SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
      writableDatabase.replace(TABLE_NAME, /* nullColumnHack= */ null, values);
    }

    @Nullable
    public DownloadState get(String id) {
      String[] selectionArgs = {id};
      try (Cursor cursor = query(COLUMN_SELECTION_ID, selectionArgs)) {
        if (cursor.getCount() == 0) {
          return null;
        }
        cursor.moveToNext();
        DownloadState downloadState = getDownloadState(cursor);
        Assertions.checkState(id.equals(downloadState.id));
        return downloadState;
      }
    }

    public DownloadStateCursor get(@DownloadState.State int... states) {
      String selection = null;
      if (states.length > 0) {
        StringBuilder selectionBuilder = new StringBuilder();
        selectionBuilder.append(COLUMN_STATE).append(" IN (");
        for (int i = 0; i < states.length; i++) {
          if (i > 0) {
            selectionBuilder.append(',');
          }
          selectionBuilder.append(states[i]);
        }
        selectionBuilder.append(')');
        selection = selectionBuilder.toString();
      }
      Cursor cursor = query(selection, /* selectionArgs= */ null);
      return new DownloadStateCursorImpl(cursor);
    }

    public void delete(String id) {
      String[] selectionArgs = {id};
      databaseProvider.getWritableDatabase().delete(TABLE_NAME, COLUMN_SELECTION_ID, selectionArgs);
    }

    private Cursor query(@Nullable String selection, @Nullable String[] selectionArgs) {
      String sortOrder = COLUMN_START_TIME_MS + " ASC";
      return databaseProvider
          .getReadableDatabase()
          .query(
              TABLE_NAME,
              COLUMNS,
              selection,
              selectionArgs,
              /* groupBy= */ null,
              /* having= */ null,
              sortOrder);
    }

    private static DownloadState getDownloadState(Cursor cursor) {
      return new DownloadState(
          cursor.getString(COLUMN_INDEX_ID),
          cursor.getString(COLUMN_INDEX_TYPE),
          Uri.parse(cursor.getString(COLUMN_INDEX_URI)),
          cursor.getString(COLUMN_INDEX_CACHE_KEY),
          cursor.getInt(COLUMN_INDEX_STATE),
          cursor.getFloat(COLUMN_INDEX_DOWNLOAD_PERCENTAGE),
          cursor.getLong(COLUMN_INDEX_DOWNLOADED_BYTES),
          cursor.getLong(COLUMN_INDEX_TOTAL_BYTES),
          cursor.getInt(COLUMN_INDEX_FAILURE_REASON),
          cursor.getInt(COLUMN_INDEX_STOP_FLAGS),
          cursor.getLong(COLUMN_INDEX_START_TIME_MS),
          cursor.getLong(COLUMN_INDEX_UPDATE_TIME_MS),
          decodeStreamKeys(cursor.getString(COLUMN_INDEX_STREAM_KEYS)),
          cursor.getBlob(COLUMN_INDEX_CUSTOM_METADATA));
    }

    private static String encodeStreamKeys(StreamKey[] streamKeys) {
      StringBuilder stringBuilder = new StringBuilder();
      for (StreamKey streamKey : streamKeys) {
        stringBuilder
            .append(streamKey.periodIndex)
            .append('.')
            .append(streamKey.groupIndex)
            .append('.')
            .append(streamKey.trackIndex)
            .append(',');
      }
      if (stringBuilder.length() > 0) {
        stringBuilder.setLength(stringBuilder.length() - 1);
      }
      return stringBuilder.toString();
    }

    private static StreamKey[] decodeStreamKeys(String encodedStreamKeys) {
      if (encodedStreamKeys.isEmpty()) {
        return new StreamKey[0];
      }
      String[] streamKeysStrings = Util.split(encodedStreamKeys, ",");
      int streamKeysCount = streamKeysStrings.length;
      StreamKey[] streamKeys = new StreamKey[streamKeysCount];
      for (int i = 0; i < streamKeysCount; i++) {
        String[] indices = Util.split(streamKeysStrings[i], "\\.");
        Assertions.checkState(indices.length == 3);
        streamKeys[i] =
            new StreamKey(
                Integer.parseInt(indices[0]),
                Integer.parseInt(indices[1]),
                Integer.parseInt(indices[2]));
      }
      return streamKeys;
    }
  }

  @VisibleForTesting
  /* package */ static final class VersionTable {
    private static final String TABLE_NAME = "ExoPlayerVersions";

    private static final String COLUMN_FEATURE = "feature";
    private static final String COLUMN_VERSION = "version";

    private static final String SQL_CREATE_TABLE =
        "CREATE TABLE IF NOT EXISTS "
            + TABLE_NAME
            + " ("
            + COLUMN_FEATURE
            + " INTEGER PRIMARY KEY NOT NULL,"
            + COLUMN_VERSION
            + " INTEGER NOT NULL)";

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({FEATURE_OFFLINE, FEATURE_CACHE})
    private @interface Feature {}

    public static final int FEATURE_OFFLINE = 0;
    public static final int FEATURE_CACHE = 1;

    private final DatabaseProvider databaseProvider;

    public VersionTable(DatabaseProvider databaseProvider) {
      this.databaseProvider = databaseProvider;
      if (!doesTableExist(databaseProvider, TABLE_NAME)) {
        databaseProvider.getWritableDatabase().execSQL(SQL_CREATE_TABLE);
      }
    }

    public void setVersion(@Feature int feature, int version) {
      ContentValues values = new ContentValues();
      values.put(COLUMN_FEATURE, feature);
      values.put(COLUMN_VERSION, version);
      SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
      writableDatabase.replace(TABLE_NAME, /* nullColumnHack= */ null, values);
    }

    public int getVersion(@Feature int feature) {
      String selection = COLUMN_FEATURE + " = ?";
      String[] selectionArgs = {Integer.toString(feature)};
      try (Cursor cursor =
          databaseProvider
              .getReadableDatabase()
              .query(
                  TABLE_NAME,
                  new String[] {COLUMN_VERSION},
                  selection,
                  selectionArgs,
                  /* groupBy= */ null,
                  /* having= */ null,
                  /* orderBy= */ null)) {
        if (cursor.getCount() == 0) {
          return 0;
        }
        cursor.moveToNext();
        return cursor.getInt(/* COLUMN_VERSION index */ 0);
      }
    }
  }

  private static final class DefaultDatabaseProvider extends SQLiteOpenHelper
      implements DatabaseProvider {
    public DefaultDatabaseProvider(Context context) {
      super(context, DATABASE_NAME, /* factory= */ null, /* version= */ 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      // Table creation is done in DownloadStateTable constructor.
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      // Upgrade is handled in DownloadStateTable constructor.
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      // TODO: Wipe the database.
      super.onDowngrade(db, oldVersion, newVersion);
    }

    // DatabaseProvider implementation.

    @Override
    public synchronized void close() {
      super.close();
    }

    @Override
    public SQLiteDatabase getWritableDatabase() {
      return super.getWritableDatabase();
    }

    @Override
    public SQLiteDatabase getReadableDatabase() {
      return super.getReadableDatabase();
    }
  }
}
