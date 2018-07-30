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
package org.telegram.messenger.exoplayer2.source.hls.offline;

import android.net.Uri;
import android.support.annotation.Nullable;
import org.telegram.messenger.exoplayer2.offline.DownloadAction;
import org.telegram.messenger.exoplayer2.offline.DownloaderConstructorHelper;
import org.telegram.messenger.exoplayer2.offline.SegmentDownloadAction;
import org.telegram.messenger.exoplayer2.source.hls.playlist.RenditionKey;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/** An action to download or remove downloaded HLS streams. */
public final class HlsDownloadAction extends SegmentDownloadAction<RenditionKey> {

  private static final String TYPE = "hls";
  private static final int VERSION = 0;

  public static final Deserializer DESERIALIZER =
      new SegmentDownloadActionDeserializer<RenditionKey>(TYPE, VERSION) {

        @Override
        protected RenditionKey readKey(DataInputStream input) throws IOException {
          int renditionGroup = input.readInt();
          int trackIndex = input.readInt();
          return new RenditionKey(renditionGroup, trackIndex);
        }

        @Override
        protected DownloadAction createDownloadAction(
            Uri uri, boolean isRemoveAction, byte[] data, List<RenditionKey> keys) {
          return new HlsDownloadAction(uri, isRemoveAction, data, keys);
        }
      };

  /**
   * @param uri The HLS playlist URI.
   * @param isRemoveAction Whether the data will be removed. If {@code false} it will be downloaded.
   * @param data Optional custom data for this action.
   * @param keys Keys of renditions to be downloaded. If empty, all renditions are downloaded. If
   *     {@code removeAction} is true, {@code keys} must empty.
   */
  public HlsDownloadAction(
      Uri uri, boolean isRemoveAction, @Nullable byte[] data, List<RenditionKey> keys) {
    super(TYPE, VERSION, uri, isRemoveAction, data, keys);
  }

  @Override
  protected HlsDownloader createDownloader(DownloaderConstructorHelper constructorHelper) {
    return new HlsDownloader(uri, keys, constructorHelper);
  }

  @Override
  protected void writeKey(DataOutputStream output, RenditionKey key) throws IOException {
    output.writeInt(key.type);
    output.writeInt(key.trackIndex);
  }

}
