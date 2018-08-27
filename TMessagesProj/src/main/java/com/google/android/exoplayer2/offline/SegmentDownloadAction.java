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
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.util.Assertions;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** {@link DownloadAction} for {@link SegmentDownloader}s. */
public abstract class SegmentDownloadAction extends DownloadAction {

  /** Base class for {@link SegmentDownloadAction} {@link Deserializer}s. */
  protected abstract static class SegmentDownloadActionDeserializer extends Deserializer {

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
      List<StreamKey> keys = new ArrayList<>();
      for (int i = 0; i < keyCount; i++) {
        keys.add(readKey(version, input));
      }
      return createDownloadAction(uri, isRemoveAction, data, keys);
    }

    /** Deserializes a key from the {@code input}. */
    protected StreamKey readKey(int version, DataInputStream input) throws IOException {
      int periodIndex = input.readInt();
      int groupIndex = input.readInt();
      int trackIndex = input.readInt();
      return new StreamKey(periodIndex, groupIndex, trackIndex);
    }

    /** Returns a {@link DownloadAction}. */
    protected abstract DownloadAction createDownloadAction(
        Uri manifestUri, boolean isRemoveAction, byte[] data, List<StreamKey> keys);
  }

  public final List<StreamKey> keys;

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
      List<StreamKey> keys) {
    super(type, version, uri, isRemoveAction, data);
    if (isRemoveAction) {
      Assertions.checkArgument(keys.isEmpty());
      this.keys = Collections.emptyList();
    } else {
      ArrayList<StreamKey> mutableKeys = new ArrayList<>(keys);
      Collections.sort(mutableKeys);
      this.keys = Collections.unmodifiableList(mutableKeys);
    }
  }

  @Override
  public List<StreamKey> getKeys() {
    return keys;
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

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!super.equals(o)) {
      return false;
    }
    SegmentDownloadAction that = (SegmentDownloadAction) o;
    return keys.equals(that.keys);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + keys.hashCode();
    return result;
  }

  /** Serializes the {@code key} into the {@code output}. */
  private void writeKey(DataOutputStream output, StreamKey key) throws IOException {
    output.writeInt(key.periodIndex);
    output.writeInt(key.groupIndex);
    output.writeInt(key.trackIndex);
  }
}
