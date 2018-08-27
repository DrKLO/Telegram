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
import com.google.android.exoplayer2.upstream.cache.CacheUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** An action to download or remove downloaded progressive streams. */
public final class ProgressiveDownloadAction extends DownloadAction {

  private static final String TYPE = "progressive";
  private static final int VERSION = 0;

  public static final Deserializer DESERIALIZER =
      new Deserializer(TYPE, VERSION) {
        @Override
        public ProgressiveDownloadAction readFromStream(int version, DataInputStream input)
            throws IOException {
          Uri uri = Uri.parse(input.readUTF());
          boolean isRemoveAction = input.readBoolean();
          int dataLength = input.readInt();
          byte[] data = new byte[dataLength];
          input.readFully(data);
          String customCacheKey = input.readBoolean() ? input.readUTF() : null;
          return new ProgressiveDownloadAction(uri, isRemoveAction, data, customCacheKey);
        }
      };

  private final @Nullable String customCacheKey;

  /**
   * Creates a progressive stream download action.
   *
   * @param uri Uri of the data to be downloaded.
   * @param data Optional custom data for this action.
   * @param customCacheKey A custom key that uniquely identifies the original stream. If not null it
   *     is used for cache indexing.
   */
  public static ProgressiveDownloadAction createDownloadAction(
      Uri uri, @Nullable byte[] data, @Nullable String customCacheKey) {
    return new ProgressiveDownloadAction(uri, /* isRemoveAction= */ false, data, customCacheKey);
  }

  /**
   * Creates a progressive stream remove action.
   *
   * @param uri Uri of the data to be removed.
   * @param data Optional custom data for this action.
   * @param customCacheKey A custom key that uniquely identifies the original stream. If not null it
   *     is used for cache indexing.
   */
  public static ProgressiveDownloadAction createRemoveAction(
      Uri uri, @Nullable byte[] data, @Nullable String customCacheKey) {
    return new ProgressiveDownloadAction(uri, /* isRemoveAction= */ true, data, customCacheKey);
  }

  /**
   * @param uri Uri of the data to be downloaded.
   * @param isRemoveAction Whether this is a remove action. If false, this is a download action.
   * @param data Optional custom data for this action.
   * @param customCacheKey A custom key that uniquely identifies the original stream. If not null it
   *     is used for cache indexing.
   * @deprecated Use {@link #createDownloadAction(Uri, byte[], String)} or {@link
   *     #createRemoveAction(Uri, byte[], String)}.
   */
  @Deprecated
  public ProgressiveDownloadAction(
      Uri uri, boolean isRemoveAction, @Nullable byte[] data, @Nullable String customCacheKey) {
    super(TYPE, VERSION, uri, isRemoveAction, data);
    this.customCacheKey = customCacheKey;
  }

  @Override
  public ProgressiveDownloader createDownloader(DownloaderConstructorHelper constructorHelper) {
    return new ProgressiveDownloader(uri, customCacheKey, constructorHelper);
  }

  @Override
  protected void writeToStream(DataOutputStream output) throws IOException {
    output.writeUTF(uri.toString());
    output.writeBoolean(isRemoveAction);
    output.writeInt(data.length);
    output.write(data);
    boolean customCacheKeySet = customCacheKey != null;
    output.writeBoolean(customCacheKeySet);
    if (customCacheKeySet) {
      output.writeUTF(customCacheKey);
    }
  }

  @Override
  public boolean isSameMedia(DownloadAction other) {
    return ((other instanceof ProgressiveDownloadAction)
        && getCacheKey().equals(((ProgressiveDownloadAction) other).getCacheKey()));
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!super.equals(o)) {
      return false;
    }
    ProgressiveDownloadAction that = (ProgressiveDownloadAction) o;
    return Util.areEqual(customCacheKey, that.customCacheKey);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (customCacheKey != null ? customCacheKey.hashCode() : 0);
    return result;
  }

  private String getCacheKey() {
    return customCacheKey != null ? customCacheKey : CacheUtil.generateKey(uri);
  }

}
