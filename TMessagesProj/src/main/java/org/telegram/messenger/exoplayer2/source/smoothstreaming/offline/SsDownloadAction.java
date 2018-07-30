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
package org.telegram.messenger.exoplayer2.source.smoothstreaming.offline;

import android.net.Uri;
import android.support.annotation.Nullable;
import org.telegram.messenger.exoplayer2.offline.DownloadAction;
import org.telegram.messenger.exoplayer2.offline.DownloaderConstructorHelper;
import org.telegram.messenger.exoplayer2.offline.SegmentDownloadAction;
import org.telegram.messenger.exoplayer2.source.smoothstreaming.manifest.StreamKey;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/** An action to download or remove downloaded SmoothStreaming streams. */
public final class SsDownloadAction extends SegmentDownloadAction<StreamKey> {

  private static final String TYPE = "ss";
  private static final int VERSION = 0;

  public static final Deserializer DESERIALIZER =
      new SegmentDownloadActionDeserializer<StreamKey>(TYPE, VERSION) {

        @Override
        protected StreamKey readKey(DataInputStream input) throws IOException {
          return new StreamKey(input.readInt(), input.readInt());
        }

        @Override
        protected DownloadAction createDownloadAction(
            Uri uri, boolean isRemoveAction, byte[] data, List<StreamKey> keys) {
          return new SsDownloadAction(uri, isRemoveAction, data, keys);
        }
      };

  /**
   * @param uri The SmoothStreaming manifest URI.
   * @param isRemoveAction Whether the data will be removed. If {@code false} it will be downloaded.
   * @param data Optional custom data for this action.
   * @param keys Keys of streams to be downloaded. If empty, all streams are downloaded. If {@code
   *     removeAction} is true, {@code keys} must be empty.
   */
  public SsDownloadAction(
      Uri uri, boolean isRemoveAction, @Nullable byte[] data, List<StreamKey> keys) {
    super(TYPE, VERSION, uri, isRemoveAction, data, keys);
  }

  @Override
  protected SsDownloader createDownloader(DownloaderConstructorHelper constructorHelper) {
    return new SsDownloader(uri, keys, constructorHelper);
  }

  @Override
  protected void writeKey(DataOutputStream output, StreamKey key) throws IOException {
    output.writeInt(key.streamElementIndex);
    output.writeInt(key.trackIndex);
  }

}
