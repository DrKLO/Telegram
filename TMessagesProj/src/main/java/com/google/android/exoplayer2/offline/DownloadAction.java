/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Contains the necessary parameters for a download or remove action. */
public final class DownloadAction {

  /** Type for progressive downloads. */
  public static final String TYPE_PROGRESSIVE = "progressive";
  /** Type for DASH downloads. */
  public static final String TYPE_DASH = "dash";
  /** Type for HLS downloads. */
  public static final String TYPE_HLS = "hls";
  /** Type for SmoothStreaming downloads. */
  public static final String TYPE_SS = "ss";

  private static final int VERSION = 2;

  /**
   * Deserializes an action from the {@code data}.
   *
   * @param data The action data to deserialize.
   * @return The deserialized action.
   * @throws IOException If the data could not be deserialized.
   */
  public static DownloadAction fromByteArray(byte[] data) throws IOException {
    ByteArrayInputStream input = new ByteArrayInputStream(data);
    return deserializeFromStream(input);
  }

  /**
   * Deserializes one action that was serialized with {@link #serializeToStream(OutputStream)} from
   * the {@code input}.
   *
   * <p>The caller is responsible for closing the given {@link InputStream}.
   *
   * @param input The stream from which to read.
   * @return The deserialized action.
   * @throws IOException If there is an IO error reading from {@code input}, or if the data could
   *     not be deserialized.
   */
  public static DownloadAction deserializeFromStream(InputStream input) throws IOException {
    return readFromStream(new DataInputStream(input));
  }

  /**
   * Creates a DASH download action.
   *
   * @param type The type of the action.
   * @param uri The URI of the media to be downloaded.
   * @param keys Keys of streams to be downloaded. If empty, all streams will be downloaded.
   * @param customCacheKey A custom key for cache indexing, or null.
   * @param data Optional custom data for this action. If {@code null} an empty array will be used.
   */
  public static DownloadAction createDownloadAction(
      String type,
      Uri uri,
      List<StreamKey> keys,
      @Nullable String customCacheKey,
      @Nullable byte[] data) {
    return new DownloadAction(type, uri, /* isRemoveAction= */ false, keys, customCacheKey, data);
  }

  /**
   * Creates a DASH remove action.
   *
   * @param type The type of the action.
   * @param uri The URI of the media to be removed.
   * @param customCacheKey A custom key for cache indexing, or null.
   */
  public static DownloadAction createRemoveAction(
      String type, Uri uri, @Nullable String customCacheKey) {
    return new DownloadAction(
        type,
        uri,
        /* isRemoveAction= */ true,
        Collections.emptyList(),
        customCacheKey,
        /* data= */ null);
  }

  /** The unique content id. */
  public final String id;
  /** The type of the action. */
  public final String type;
  /** The uri being downloaded or removed. */
  public final Uri uri;
  /** Whether this is a remove action. If false, this is a download action. */
  public final boolean isRemoveAction;
  /**
   * Keys of streams to be downloaded. If empty, all streams will be downloaded. Empty if this
   * action is a remove action.
   */
  public final List<StreamKey> keys;
  /** A custom key for cache indexing, or null. */
  @Nullable public final String customCacheKey;
  /** Custom data for this action. May be empty. */
  public final byte[] data;

  /**
   * @param type The type of the action.
   * @param uri The uri being downloaded or removed.
   * @param isRemoveAction Whether this is a remove action. If false, this is a download action.
   * @param keys Keys of streams to be downloaded. If empty, all streams will be downloaded. Empty
   *     if this action is a remove action.
   * @param customCacheKey A custom key for cache indexing, or null.
   * @param data Custom data for this action. Null if this action is a remove action.
   */
  private DownloadAction(
      String type,
      Uri uri,
      boolean isRemoveAction,
      List<StreamKey> keys,
      @Nullable String customCacheKey,
      @Nullable byte[] data) {
    this.id = customCacheKey != null ? customCacheKey : uri.toString();
    this.type = type;
    this.uri = uri;
    this.isRemoveAction = isRemoveAction;
    this.customCacheKey = customCacheKey;
    if (isRemoveAction) {
      Assertions.checkArgument(keys.isEmpty());
      Assertions.checkArgument(data == null);
      this.keys = Collections.emptyList();
      this.data = Util.EMPTY_BYTE_ARRAY;
    } else {
      ArrayList<StreamKey> mutableKeys = new ArrayList<>(keys);
      Collections.sort(mutableKeys);
      this.keys = Collections.unmodifiableList(mutableKeys);
      this.data = data != null ? Arrays.copyOf(data, data.length) : Util.EMPTY_BYTE_ARRAY;
    }
  }

  /** Serializes itself into a byte array. */
  public byte[] toByteArray() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      serializeToStream(output);
    } catch (IOException e) {
      // ByteArrayOutputStream shouldn't throw IOException.
      throw new IllegalStateException();
    }
    return output.toByteArray();
  }

  /** Returns whether this is an action for the same media as the {@code other}. */
  public boolean isSameMedia(DownloadAction other) {
    return id.equals(other.id);
  }

  /** Returns keys of streams to be downloaded. */
  public List<StreamKey> getKeys() {
    return keys;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (!(o instanceof DownloadAction)) {
      return false;
    }
    DownloadAction that = (DownloadAction) o;
    return id.equals(that.id)
        && type.equals(that.type)
        && uri.equals(that.uri)
        && isRemoveAction == that.isRemoveAction
        && keys.equals(that.keys)
        && Util.areEqual(customCacheKey, that.customCacheKey)
        && Arrays.equals(data, that.data);
  }

  @Override
  public final int hashCode() {
    int result = type.hashCode();
    result = 31 * result + id.hashCode();
    result = 31 * result + uri.hashCode();
    result = 31 * result + (isRemoveAction ? 1 : 0);
    result = 31 * result + keys.hashCode();
    result = 31 * result + (customCacheKey != null ? customCacheKey.hashCode() : 0);
    result = 31 * result + Arrays.hashCode(data);
    return result;
  }

  // Serialization.

  /**
   * Serializes this action into an {@link OutputStream}.
   *
   * @param output The stream to write to.
   */
  public final void serializeToStream(OutputStream output) throws IOException {
    // Don't close the stream as it closes the underlying stream too.
    DataOutputStream dataOutputStream = new DataOutputStream(output);
    dataOutputStream.writeUTF(type);
    dataOutputStream.writeInt(VERSION);
    dataOutputStream.writeUTF(uri.toString());
    dataOutputStream.writeBoolean(isRemoveAction);
    dataOutputStream.writeInt(data.length);
    dataOutputStream.write(data);
    dataOutputStream.writeInt(keys.size());
    for (int i = 0; i < keys.size(); i++) {
      StreamKey key = keys.get(i);
      dataOutputStream.writeInt(key.periodIndex);
      dataOutputStream.writeInt(key.groupIndex);
      dataOutputStream.writeInt(key.trackIndex);
    }
    dataOutputStream.writeBoolean(customCacheKey != null);
    if (customCacheKey != null) {
      dataOutputStream.writeUTF(customCacheKey);
    }
    dataOutputStream.flush();
  }

  private static DownloadAction readFromStream(DataInputStream input) throws IOException {
    String type = input.readUTF();
    int version = input.readInt();

    Uri uri = Uri.parse(input.readUTF());
    boolean isRemoveAction = input.readBoolean();

    int dataLength = input.readInt();
    byte[] data;
    if (dataLength != 0) {
      data = new byte[dataLength];
      input.readFully(data);
      if (isRemoveAction) {
        // Remove actions are no longer permitted to have data.
        data = null;
      }
    } else {
      data = null;
    }

    // Serialized version 0 progressive actions did not contain keys.
    boolean isLegacyProgressive = version == 0 && TYPE_PROGRESSIVE.equals(type);
    List<StreamKey> keys = new ArrayList<>();
    if (!isLegacyProgressive) {
      int keyCount = input.readInt();
      for (int i = 0; i < keyCount; i++) {
        keys.add(readKey(type, version, input));
      }
    }

    // Serialized version 0 and 1 DASH/HLS/SS actions did not contain a custom cache key.
    boolean isLegacySegmented =
        version < 2 && (TYPE_DASH.equals(type) || TYPE_HLS.equals(type) || TYPE_SS.equals(type));
    String customCacheKey = null;
    if (!isLegacySegmented) {
      customCacheKey = input.readBoolean() ? input.readUTF() : null;
    }

    return new DownloadAction(type, uri, isRemoveAction, keys, customCacheKey, data);
  }

  private static StreamKey readKey(String type, int version, DataInputStream input)
      throws IOException {
    int periodIndex;
    int groupIndex;
    int trackIndex;

    // Serialized version 0 HLS/SS actions did not contain a period index.
    if ((TYPE_HLS.equals(type) || TYPE_SS.equals(type)) && version == 0) {
      periodIndex = 0;
      groupIndex = input.readInt();
      trackIndex = input.readInt();
    } else {
      periodIndex = input.readInt();
      groupIndex = input.readInt();
      trackIndex = input.readInt();
    }
    return new StreamKey(periodIndex, groupIndex, trackIndex);
  }
}
