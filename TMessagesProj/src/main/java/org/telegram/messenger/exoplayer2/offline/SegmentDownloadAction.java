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
package org.telegram.messenger.exoplayer2.offline;

import android.net.Uri;
import android.support.annotation.Nullable;
import org.telegram.messenger.exoplayer2.util.Assertions;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link DownloadAction} for {@link SegmentDownloader}s.
 *
 * @param <K> The type of the representation key object.
 */
public abstract class SegmentDownloadAction<K extends Comparable<K>> extends DownloadAction {

  /**
   * Base class for {@link SegmentDownloadAction} {@link Deserializer}s.
   *
   * @param <K> The type of the representation key object.
   */
  protected abstract static class SegmentDownloadActionDeserializer<K> extends Deserializer {

    public SegmentDownloadActionDeserializer(String type, int version) {
      super(type, version);
    }

    @Override
    public final DownloadAction readFromStream(int version, DataInputStream input)
        throws IOException {
      Uri uri = Uri.parse(input.readUTF());
      boolean isRemoveAction = input.readBoolean();
      int dataLength = input.readInt();
      byte[] data = new byte[dataLength];
      input.readFully(data);
      int keyCount = input.readInt();
      List<K> keys = new ArrayList<>();
      for (int i = 0; i < keyCount; i++) {
        keys.add(readKey(input));
      }
      return createDownloadAction(uri, isRemoveAction, data, keys);
    }

    /** Deserializes a key from the {@code input}. */
    protected abstract K readKey(DataInputStream input) throws IOException;

    /** Returns a {@link DownloadAction}. */
    protected abstract DownloadAction createDownloadAction(
        Uri manifestUri, boolean isRemoveAction, byte[] data, List<K> keys);
  }

  public final List<K> keys;

  /**
   * @param type The type of the action.
   * @param version The action version.
   * @param uri The URI of the media being downloaded.
   * @param isRemoveAction Whether the data will be removed. If {@code false} it will be downloaded.
   * @param data Optional custom data for this action. If {@code null} an empty array will be used.
   * @param keys Keys of tracks to be downloaded. If empty, all tracks will be downloaded. If {@code
   *     removeAction} is true, {@code keys} must be empty.
   */
  protected SegmentDownloadAction(
      String type,
      int version,
      Uri uri,
      boolean isRemoveAction,
      @Nullable byte[] data,
      List<K> keys) {
    super(type, version, uri, isRemoveAction, data);
    if (isRemoveAction) {
      Assertions.checkArgument(keys.isEmpty());
      this.keys = Collections.emptyList();
    } else {
      ArrayList<K> mutableKeys = new ArrayList<>(keys);
      Collections.sort(mutableKeys);
      this.keys = Collections.unmodifiableList(mutableKeys);
    }
  }

  @Override
  public final void writeToStream(DataOutputStream output) throws IOException {
    output.writeUTF(uri.toString());
    output.writeBoolean(isRemoveAction);
    output.writeInt(data.length);
    output.write(data);
    output.writeInt(keys.size());
    for (int i = 0; i < keys.size(); i++) {
      writeKey(output, keys.get(i));
    }
  }

  /** Serializes the {@code key} into the {@code output}. */
  protected abstract void writeKey(DataOutputStream output, K key) throws IOException;

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!super.equals(o)) {
      return false;
    }
    SegmentDownloadAction<?> that = (SegmentDownloadAction<?>) o;
    return keys.equals(that.keys);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + keys.hashCode();
    return result;
  }

}
