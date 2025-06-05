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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.database.DatabaseIOException;
import com.google.android.exoplayer2.database.DatabaseProvider;
import com.google.android.exoplayer2.database.VersionTable;
import com.google.android.exoplayer2.offline.Download.FailureReason;
import com.google.android.exoplayer2.offline.Download.State;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.List;

/** A {@link DownloadIndex} that uses SQLite to persist {@link Download Downloads}. */
public final class DefaultDownloadIndex implements WritableDownloadIndex {

  private static final String TABLE_PREFIX = DatabaseProvider.TABLE_PREFIX + "Downloads";

  @VisibleForTesting /* package */ static final int TABLE_VERSION = 3;

  private static final String COLUMN_ID = "id";
  private static final String COLUMN_MIME_TYPE = "mime_type";
  private static final String COLUMN_URI = "uri";
  private static final String COLUMN_STREAM_KEYS = "stream_keys";
  private static final String COLUMN_CUSTOM_CACHE_KEY = "custom_cache_key";
  private static final String COLUMN_DATA = "data";
  private static final String COLUMN_STATE = "state";
  private static final String COLUMN_START_TIME_MS = "start_time_ms";
  private static final String COLUMN_UPDATE_TIME_MS = "update_time_ms";
  private static final String COLUMN_CONTENT_LENGTH = "content_length";
  private static final String COLUMN_STOP_REASON = "stop_reason";
  private static final String COLUMN_FAILURE_REASON = "failure_reason";
  private static final String COLUMN_PERCENT_DOWNLOADED = "percent_downloaded";
  private static final String COLUMN_BYTES_DOWNLOADED = "bytes_downloaded";
  private static final String COLUMN_KEY_SET_ID = "key_set_id";

  private static final int COLUMN_INDEX_ID = 0;
  private static final int COLUMN_INDEX_MIME_TYPE = 1;
  private static final int COLUMN_INDEX_URI = 2;
  private static final int COLUMN_INDEX_STREAM_KEYS = 3;
  private static final int COLUMN_INDEX_CUSTOM_CACHE_KEY = 4;
  private static final int COLUMN_INDEX_DATA = 5;
  private static final int COLUMN_INDEX_STATE = 6;
  private static final int COLUMN_INDEX_START_TIME_MS = 7;
  private static final int COLUMN_INDEX_UPDATE_TIME_MS = 8;
  private static final int COLUMN_INDEX_CONTENT_LENGTH = 9;
  private static final int COLUMN_INDEX_STOP_REASON = 10;
  private static final int COLUMN_INDEX_FAILURE_REASON = 11;
  private static final int COLUMN_INDEX_PERCENT_DOWNLOADED = 12;
  private static final int COLUMN_INDEX_BYTES_DOWNLOADED = 13;
  private static final int COLUMN_INDEX_KEY_SET_ID = 14;

  private static final String WHERE_ID_EQUALS = COLUMN_ID + " = ?";
  private static final String WHERE_STATE_IS_DOWNLOADING =
      COLUMN_STATE + " = " + Download.STATE_DOWNLOADING;
  private static final String WHERE_STATE_IS_TERMINAL =
      getStateQuery(Download.STATE_COMPLETED, Download.STATE_FAILED);

  private static final String[] COLUMNS =
      new String[] {
        COLUMN_ID,
        COLUMN_MIME_TYPE,
        COLUMN_URI,
        COLUMN_STREAM_KEYS,
        COLUMN_CUSTOM_CACHE_KEY,
        COLUMN_DATA,
        COLUMN_STATE,
        COLUMN_START_TIME_MS,
        COLUMN_UPDATE_TIME_MS,
        COLUMN_CONTENT_LENGTH,
        COLUMN_STOP_REASON,
        COLUMN_FAILURE_REASON,
        COLUMN_PERCENT_DOWNLOADED,
        COLUMN_BYTES_DOWNLOADED,
        COLUMN_KEY_SET_ID
      };

  private static final String TABLE_SCHEMA =
      "("
          + COLUMN_ID
          + " TEXT PRIMARY KEY NOT NULL,"
          + COLUMN_MIME_TYPE
          + " TEXT,"
          + COLUMN_URI
          + " TEXT NOT NULL,"
          + COLUMN_STREAM_KEYS
          + " TEXT NOT NULL,"
          + COLUMN_CUSTOM_CACHE_KEY
          + " TEXT,"
          + COLUMN_DATA
          + " BLOB NOT NULL,"
          + COLUMN_STATE
          + " INTEGER NOT NULL,"
          + COLUMN_START_TIME_MS
          + " INTEGER NOT NULL,"
          + COLUMN_UPDATE_TIME_MS
          + " INTEGER NOT NULL,"
          + COLUMN_CONTENT_LENGTH
          + " INTEGER NOT NULL,"
          + COLUMN_STOP_REASON
          + " INTEGER NOT NULL,"
          + COLUMN_FAILURE_REASON
          + " INTEGER NOT NULL,"
          + COLUMN_PERCENT_DOWNLOADED
          + " REAL NOT NULL,"
          + COLUMN_BYTES_DOWNLOADED
          + " INTEGER NOT NULL,"
          + COLUMN_KEY_SET_ID
          + " BLOB NOT NULL)";

  private static final String TRUE = "1";

  private final String name;
  private final String tableName;
  private final DatabaseProvider databaseProvider;
  private final Object initializationLock;

  @GuardedBy("initializationLock")
  private boolean initialized;

  /**
   * Creates an instance that stores the {@link Download Downloads} in an SQLite database provided
   * by a {@link DatabaseProvider}.
   *
   * <p>Equivalent to calling {@link #DefaultDownloadIndex(DatabaseProvider, String)} with {@code
   * name=""}.
   *
   * <p>Applications that only have one download index may use this constructor. Applications that
   * have multiple download indices should call {@link #DefaultDownloadIndex(DatabaseProvider,
   * String)} to specify a unique name for each index.
   *
   * @param databaseProvider Provides the SQLite database in which downloads are persisted.
   */
  public DefaultDownloadIndex(DatabaseProvider databaseProvider) {
    this(databaseProvider, "");
  }

  /**
   * Creates an instance that stores the {@link Download Downloads} in an SQLite database provided
   * by a {@link DatabaseProvider}.
   *
   * @param databaseProvider Provides the SQLite database in which downloads are persisted.
   * @param name The name of the index. This name is incorporated into the names of the SQLite
   *     tables in which downloads are persisted.
   */
  public DefaultDownloadIndex(DatabaseProvider databaseProvider, String name) {
    this.name = name;
    this.databaseProvider = databaseProvider;
    tableName = TABLE_PREFIX + name;
    initializationLock = new Object();
  }

  @Override
  @Nullable
  public Download getDownload(String id) throws DatabaseIOException {
    ensureInitialized();
    try (Cursor cursor = getCursor(WHERE_ID_EQUALS, new String[] {id})) {
      if (cursor.getCount() == 0) {
        return null;
      }
      cursor.moveToNext();
      return getDownloadForCurrentRow(cursor);
    } catch (SQLiteException e) {
      throw new DatabaseIOException(e);
    }
  }

  @Override
  public DownloadCursor getDownloads(@Download.State int... states) throws DatabaseIOException {
    ensureInitialized();
    Cursor cursor = getCursor(getStateQuery(states), /* selectionArgs= */ null);
    return new DownloadCursorImpl(cursor);
  }

  @Override
  public void putDownload(Download download) throws DatabaseIOException {
    ensureInitialized();
    try {
      SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
      putDownloadInternal(download, writableDatabase);
    } catch (SQLiteException e) {
      throw new DatabaseIOException(e);
    }
  }

  @Override
  public void removeDownload(String id) throws DatabaseIOException {
    ensureInitialized();
    try {
      databaseProvider.getWritableDatabase().delete(tableName, WHERE_ID_EQUALS, new String[] {id});
    } catch (SQLiteException e) {
      throw new DatabaseIOException(e);
    }
  }

  @Override
  public void setDownloadingStatesToQueued() throws DatabaseIOException {
    ensureInitialized();
    try {
      ContentValues values = new ContentValues();
      values.put(COLUMN_STATE, Download.STATE_QUEUED);
      SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
      writableDatabase.update(tableName, values, WHERE_STATE_IS_DOWNLOADING, /* whereArgs= */ null);
    } catch (SQLException e) {
      throw new DatabaseIOException(e);
    }
  }

  @Override
  public void setStatesToRemoving() throws DatabaseIOException {
    ensureInitialized();
    try {
      ContentValues values = new ContentValues();
      values.put(COLUMN_STATE, Download.STATE_REMOVING);
      // Only downloads in STATE_FAILED are allowed a failure reason, so we need to clear it here in
      // case we're moving downloads from STATE_FAILED to STATE_REMOVING.
      values.put(COLUMN_FAILURE_REASON, Download.FAILURE_REASON_NONE);
      SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
      writableDatabase.update(tableName, values, /* whereClause= */ null, /* whereArgs= */ null);
    } catch (SQLException e) {
      throw new DatabaseIOException(e);
    }
  }

  @Override
  public void setStopReason(int stopReason) throws DatabaseIOException {
    ensureInitialized();
    try {
      ContentValues values = new ContentValues();
      values.put(COLUMN_STOP_REASON, stopReason);
      SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
      writableDatabase.update(tableName, values, WHERE_STATE_IS_TERMINAL, /* whereArgs= */ null);
    } catch (SQLException e) {
      throw new DatabaseIOException(e);
    }
  }

  @Override
  public void setStopReason(String id, int stopReason) throws DatabaseIOException {
    ensureInitialized();
    try {
      ContentValues values = new ContentValues();
      values.put(COLUMN_STOP_REASON, stopReason);
      SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
      writableDatabase.update(
          tableName,
          values,
          WHERE_STATE_IS_TERMINAL + " AND " + WHERE_ID_EQUALS,
          new String[] {id});
    } catch (SQLException e) {
      throw new DatabaseIOException(e);
    }
  }

  private void ensureInitialized() throws DatabaseIOException {
    synchronized (initializationLock) {
      if (initialized) {
        return;
      }
      try {
        SQLiteDatabase readableDatabase = databaseProvider.getReadableDatabase();
        int version = VersionTable.getVersion(readableDatabase, VersionTable.FEATURE_OFFLINE, name);
        if (version != TABLE_VERSION) {
          SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
          writableDatabase.beginTransactionNonExclusive();
          try {
            VersionTable.setVersion(
                writableDatabase, VersionTable.FEATURE_OFFLINE, name, TABLE_VERSION);
            List<Download> upgradedDownloads =
                version == 2 ? loadDownloadsFromVersion2(writableDatabase) : new ArrayList<>();
            writableDatabase.execSQL("DROP TABLE IF EXISTS " + tableName);
            writableDatabase.execSQL("CREATE TABLE " + tableName + " " + TABLE_SCHEMA);
            for (Download download : upgradedDownloads) {
              putDownloadInternal(download, writableDatabase);
            }
            writableDatabase.setTransactionSuccessful();
          } finally {
            writableDatabase.endTransaction();
          }
        }
        initialized = true;
      } catch (SQLException e) {
        throw new DatabaseIOException(e);
      }
    }
  }

  private void putDownloadInternal(Download download, SQLiteDatabase database) {
    byte[] keySetId =
        download.request.keySetId == null ? Util.EMPTY_BYTE_ARRAY : download.request.keySetId;
    ContentValues values = new ContentValues();
    values.put(COLUMN_ID, download.request.id);
    values.put(COLUMN_MIME_TYPE, download.request.mimeType);
    values.put(COLUMN_URI, download.request.uri.toString());
    values.put(COLUMN_STREAM_KEYS, encodeStreamKeys(download.request.streamKeys));
    values.put(COLUMN_CUSTOM_CACHE_KEY, download.request.customCacheKey);
    values.put(COLUMN_DATA, download.request.data);
    values.put(COLUMN_STATE, download.state);
    values.put(COLUMN_START_TIME_MS, download.startTimeMs);
    values.put(COLUMN_UPDATE_TIME_MS, download.updateTimeMs);
    values.put(COLUMN_CONTENT_LENGTH, download.contentLength);
    values.put(COLUMN_STOP_REASON, download.stopReason);
    values.put(COLUMN_FAILURE_REASON, download.failureReason);
    values.put(COLUMN_PERCENT_DOWNLOADED, download.getPercentDownloaded());
    values.put(COLUMN_BYTES_DOWNLOADED, download.getBytesDownloaded());
    values.put(COLUMN_KEY_SET_ID, keySetId);
    database.replaceOrThrow(tableName, /* nullColumnHack= */ null, values);
  }

  private List<Download> loadDownloadsFromVersion2(SQLiteDatabase database) {
    List<Download> downloads = new ArrayList<>();
    if (!Util.tableExists(database, tableName)) {
      return downloads;
    }

    String[] columnsV2 =
        new String[] {
          "id",
          "title",
          "uri",
          "stream_keys",
          "custom_cache_key",
          "data",
          "state",
          "start_time_ms",
          "update_time_ms",
          "content_length",
          "stop_reason",
          "failure_reason",
          "percent_downloaded",
          "bytes_downloaded"
        };
    try (Cursor cursor =
        database.query(
            tableName,
            columnsV2,
            /* selection= */ null,
            /* selectionArgs= */ null,
            /* groupBy= */ null,
            /* having= */ null,
            /* orderBy= */ null); ) {
      while (cursor.moveToNext()) {
        downloads.add(getDownloadForCurrentRowV2(cursor));
      }
      return downloads;
    }
  }

  /** Infers the MIME type from a v2 table row. */
  private static String inferMimeType(@Nullable String downloadType) {
    if ("dash".equals(downloadType)) {
      return MimeTypes.APPLICATION_MPD;
    } else if ("hls".equals(downloadType)) {
      return MimeTypes.APPLICATION_M3U8;
    } else if ("ss".equals(downloadType)) {
      return MimeTypes.APPLICATION_SS;
    } else {
      return MimeTypes.VIDEO_UNKNOWN;
    }
  }

  private Cursor getCursor(String selection, @Nullable String[] selectionArgs)
      throws DatabaseIOException {
    try {
      String sortOrder = COLUMN_START_TIME_MS + " ASC";
      return databaseProvider
          .getReadableDatabase()
          .query(
              tableName,
              COLUMNS,
              selection,
              selectionArgs,
              /* groupBy= */ null,
              /* having= */ null,
              sortOrder);
    } catch (SQLiteException e) {
      throw new DatabaseIOException(e);
    }
  }

  @VisibleForTesting
  /* package */ static String encodeStreamKeys(List<StreamKey> streamKeys) {
    StringBuilder stringBuilder = new StringBuilder();
    for (int i = 0; i < streamKeys.size(); i++) {
      StreamKey streamKey = streamKeys.get(i);
      stringBuilder
          .append(streamKey.periodIndex)
          .append('.')
          .append(streamKey.groupIndex)
          .append('.')
          .append(streamKey.streamIndex)
          .append(',');
    }
    if (stringBuilder.length() > 0) {
      stringBuilder.setLength(stringBuilder.length() - 1);
    }
    return stringBuilder.toString();
  }

  private static String getStateQuery(@Download.State int... states) {
    if (states.length == 0) {
      return TRUE;
    }
    StringBuilder selectionBuilder = new StringBuilder();
    selectionBuilder.append(COLUMN_STATE).append(" IN (");
    for (int i = 0; i < states.length; i++) {
      if (i > 0) {
        selectionBuilder.append(',');
      }
      selectionBuilder.append(states[i]);
    }
    selectionBuilder.append(')');
    return selectionBuilder.toString();
  }

  private static Download getDownloadForCurrentRow(Cursor cursor) {
    byte[] keySetId = cursor.getBlob(COLUMN_INDEX_KEY_SET_ID);
    DownloadRequest request =
        new DownloadRequest.Builder(
                /* id= */ checkNotNull(cursor.getString(COLUMN_INDEX_ID)),
                /* uri= */ Uri.parse(checkNotNull(cursor.getString(COLUMN_INDEX_URI))))
            .setMimeType(cursor.getString(COLUMN_INDEX_MIME_TYPE))
            .setStreamKeys(decodeStreamKeys(cursor.getString(COLUMN_INDEX_STREAM_KEYS)))
            .setKeySetId(keySetId.length > 0 ? keySetId : null)
            .setCustomCacheKey(cursor.getString(COLUMN_INDEX_CUSTOM_CACHE_KEY))
            .setData(cursor.getBlob(COLUMN_INDEX_DATA))
            .build();
    DownloadProgress downloadProgress = new DownloadProgress();
    downloadProgress.bytesDownloaded = cursor.getLong(COLUMN_INDEX_BYTES_DOWNLOADED);
    downloadProgress.percentDownloaded = cursor.getFloat(COLUMN_INDEX_PERCENT_DOWNLOADED);
    @State int state = cursor.getInt(COLUMN_INDEX_STATE);
    // It's possible the database contains failure reasons for non-failed downloads, which is
    // invalid. Clear them here. See https://github.com/google/ExoPlayer/issues/6785.
    @FailureReason
    int failureReason =
        state == Download.STATE_FAILED
            ? cursor.getInt(COLUMN_INDEX_FAILURE_REASON)
            : Download.FAILURE_REASON_NONE;
    return new Download(
        request,
        state,
        /* startTimeMs= */ cursor.getLong(COLUMN_INDEX_START_TIME_MS),
        /* updateTimeMs= */ cursor.getLong(COLUMN_INDEX_UPDATE_TIME_MS),
        /* contentLength= */ cursor.getLong(COLUMN_INDEX_CONTENT_LENGTH),
        /* stopReason= */ cursor.getInt(COLUMN_INDEX_STOP_REASON),
        failureReason,
        downloadProgress);
  }

  /** Read a {@link Download} from a table row of version 2. */
  private static Download getDownloadForCurrentRowV2(Cursor cursor) {
    /*
     * Version 2 schema
     * Index  Column              Type
     * 0      id                  string
     * 1      type                string
     * 2      uri                 string
     * 3      stream_keys         string
     * 4      custom_cache_key    string
     * 5      data                blob
     * 6      state               integer
     * 7      start_time_ms       integer
     * 8      update_time_ms      integer
     * 9      content_length      integer
     * 10     stop_reason         integer
     * 11     failure_reason      integer
     * 12     percent_downloaded  real
     * 13     bytes_downloaded    integer
     */
    DownloadRequest request =
        new DownloadRequest.Builder(
                /* id= */ checkNotNull(cursor.getString(0)),
                /* uri= */ Uri.parse(checkNotNull(cursor.getString(2))))
            .setMimeType(inferMimeType(cursor.getString(1)))
            .setStreamKeys(decodeStreamKeys(cursor.getString(3)))
            .setCustomCacheKey(cursor.getString(4))
            .setData(cursor.getBlob(5))
            .build();
    DownloadProgress downloadProgress = new DownloadProgress();
    downloadProgress.bytesDownloaded = cursor.getLong(13);
    downloadProgress.percentDownloaded = cursor.getFloat(12);
    @State int state = cursor.getInt(6);
    // It's possible the database contains failure reasons for non-failed downloads, which is
    // invalid. Clear them here. See https://github.com/google/ExoPlayer/issues/6785.
    @FailureReason
    int failureReason =
        state == Download.STATE_FAILED ? cursor.getInt(11) : Download.FAILURE_REASON_NONE;
    return new Download(
        request,
        state,
        /* startTimeMs= */ cursor.getLong(7),
        /* updateTimeMs= */ cursor.getLong(8),
        /* contentLength= */ cursor.getLong(9),
        /* stopReason= */ cursor.getInt(10),
        failureReason,
        downloadProgress);
  }

  private static List<StreamKey> decodeStreamKeys(@Nullable String encodedStreamKeys) {
    ArrayList<StreamKey> streamKeys = new ArrayList<>();
    if (TextUtils.isEmpty(encodedStreamKeys)) {
      return streamKeys;
    }
    String[] streamKeysStrings = Util.split(encodedStreamKeys, ",");
    for (String streamKeysString : streamKeysStrings) {
      String[] indices = Util.split(streamKeysString, "\\.");
      Assertions.checkState(indices.length == 3);
      streamKeys.add(
          new StreamKey(
              Integer.parseInt(indices[0]),
              Integer.parseInt(indices[1]),
              Integer.parseInt(indices[2])));
    }
    return streamKeys;
  }

  private static final class DownloadCursorImpl implements DownloadCursor {

    private final Cursor cursor;

    private DownloadCursorImpl(Cursor cursor) {
      this.cursor = cursor;
    }

    @Override
    public Download getDownload() {
      return getDownloadForCurrentRow(cursor);
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
}
