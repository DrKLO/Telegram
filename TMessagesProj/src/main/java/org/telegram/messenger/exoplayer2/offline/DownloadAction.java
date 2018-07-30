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
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/** Contains the necessary parameters for a download or remove action. */
public abstract class DownloadAction {

  /** Used to deserialize {@link DownloadAction}s. */
  public abstract static class Deserializer {

    public final String type;
    public final int version;

    public Deserializer(String type, int version) {
      this.type = type;
      this.version = version;
    }

    /**
     * Deserializes an action from the {@code input}.
     *
     * @param version The version of the serialized action.
     * @param input The stream from which to read the action.
     * @see DownloadAction#writeToStream(DataOutputStream)
     */
    public abstract DownloadAction readFromStream(int version, DataInputStream input)
        throws IOException;
  }

  /**
   * Deserializes one action that was serialized with {@link #serializeToStream(DownloadAction,
   * OutputStream)} from the {@code input}, using the {@link Deserializer}s that supports the
   * action's type.
   *
   * <p>The caller is responsible for closing the given {@link InputStream}.
   *
   * @param deserializers {@link Deserializer}s for supported actions.
   * @param input The stream from which to read the action.
   * @return The deserialized action.
   * @throws IOException If there is an IO error reading from {@code input}, or if the action type
   *     isn't supported by any of the {@code deserializers}.
   */
  public static DownloadAction deserializeFromStream(
      Deserializer[] deserializers, InputStream input) throws IOException {
    // Don't close the stream as it closes the underlying stream too.
    DataInputStream dataInputStream = new DataInputStream(input);
    String type = dataInputStream.readUTF();
    int version = dataInputStream.readInt();
    for (Deserializer deserializer : deserializers) {
      if (type.equals(deserializer.type) && deserializer.version >= version) {
        return deserializer.readFromStream(version, dataInputStream);
      }
    }
    throw new DownloadException("No deserializer found for:" + type + ", " + version);
  }

  /** Serializes {@code action} type and data into the {@code output}. */
  public static void serializeToStream(DownloadAction action, OutputStream output)
      throws IOException {
    // Don't close the stream as it closes the underlying stream too.
    DataOutputStream dataOutputStream = new DataOutputStream(output);
    dataOutputStream.writeUTF(action.type);
    dataOutputStream.writeInt(action.version);
    action.writeToStream(dataOutputStream);
    dataOutputStream.flush();
  }

  /** The type of the action. */
  public final String type;
  /** The action version. */
  public final int version;
  /** The uri being downloaded or removed. */
  public final Uri uri;
  /** Whether this is a remove action. If false, this is a download action. */
  public final boolean isRemoveAction;
  /** Custom data for this action. May be empty. */
  public final byte[] data;

  /**
   * @param type The type of the action.
   * @param version The action version.
   * @param uri The uri being downloaded or removed.
   * @param isRemoveAction Whether this is a remove action. If false, this is a download action.
   * @param data Optional custom data for this action.
   */
  protected DownloadAction(
      String type, int version, Uri uri, boolean isRemoveAction, @Nullable byte[] data) {
    this.type = type;
    this.version = version;
    this.uri = uri;
    this.isRemoveAction = isRemoveAction;
    this.data = data != null ? data : new byte[0];
  }

  /** Serializes itself into a byte array. */
  public final byte[] toByteArray() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      serializeToStream(this, output);
    } catch (IOException e) {
      // ByteArrayOutputStream shouldn't throw IOException.
      throw new IllegalStateException();
    }
    return output.toByteArray();
  }

  /** Returns whether this is an action for the same media as the {@code other}. */
  public boolean isSameMedia(DownloadAction other) {
    return uri.equals(other.uri);
  }

  /** Serializes itself into the {@code output}. */
  protected abstract void writeToStream(DataOutputStream output) throws IOException;

  /** Creates a {@link Downloader} with the given parameters. */
  protected abstract Downloader createDownloader(
      DownloaderConstructorHelper downloaderConstructorHelper);

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DownloadAction that = (DownloadAction) o;
    return type.equals(that.type)
        && version == that.version
        && uri.equals(that.uri)
        && isRemoveAction == that.isRemoveAction
        && Arrays.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    int result = uri.hashCode();
    result = 31 * result + (isRemoveAction ? 1 : 0);
    result = 31 * result + Arrays.hashCode(data);
    return result;
  }

}
