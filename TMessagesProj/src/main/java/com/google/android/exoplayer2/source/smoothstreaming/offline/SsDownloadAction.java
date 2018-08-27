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
package com.google.android.exoplayer2.source.smoothstreaming.offline;

import android.net.Uri;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.offline.DownloadAction;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.offline.SegmentDownloadAction;
import com.google.android.exoplayer2.offline.StreamKey;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** An action to download or remove downloaded SmoothStreaming streams. */
public final class SsDownloadAction extends SegmentDownloadAction {

  private static final String TYPE = "ss";
  private static final int VERSION = 1;

  public static final Deserializer DESERIALIZER =
      new SegmentDownloadActionDeserializer(TYPE, VERSION) {

        @Override
        protected StreamKey readKey(int version, DataInputStream input) throws IOException {
          if (version > 0) {
            return super.readKey(version, input);
          }
          int groupIndex = input.readInt();
          int trackIndex = input.readInt();
          return new StreamKey(groupIndex, trackIndex);
        }

        @Override
        protected DownloadAction createDownloadAction(
            Uri uri, boolean isRemoveAction, byte[] data, List<StreamKey> keys) {
          return new SsDownloadAction(uri, isRemoveAction, data, keys);
        }
      };

  /**
   * Creates a SmoothStreaming download action.
   *
   * @param uri The URI of the media to be downloaded.
   * @param data Optional custom data for this action. If {@code null} an empty array will be used.
   * @param keys Keys of tracks to be downloaded. If empty, all tracks will be downloaded.
   */
  public static SsDownloadAction createDownloadAction(
      Uri uri, @Nullable byte[] data, List<StreamKey> keys) {
    return new SsDownloadAction(uri, /* isRemoveAction= */ false, data, keys);
  }

  /**
   * Creates a SmoothStreaming remove action.
   *
   * @param uri The URI of the media to be removed.
   * @param data Optional custom data for this action. If {@code null} an empty array will be used.
   */
  public static SsDownloadAction createRemoveAction(Uri uri, @Nullable byte[] data) {
    return new SsDownloadAction(uri, /* isRemoveAction= */ true, data, Collections.emptyList());
  }

  /**
   * @param uri The SmoothStreaming manifest URI.
   * @param isRemoveAction Whether the data will be removed. If {@code false} it will be downloaded.
   * @param data Optional custom data for this action.
   * @param keys Keys of streams to be downloaded. If empty, all streams are downloaded. If {@code
   *     removeAction} is true, {@code keys} must be empty.
   * @deprecated Use {@link #createDownloadAction(Uri, byte[], List)} or {@link
   *     #createRemoveAction(Uri, byte[])}.
   */
  @Deprecated
  public SsDownloadAction(
      Uri uri, boolean isRemoveAction, @Nullable byte[] data, List<StreamKey> keys) {
    super(TYPE, VERSION, uri, isRemoveAction, data, keys);
  }

  @Override
  public SsDownloader createDownloader(DownloaderConstructorHelper constructorHelper) {
    return new SsDownloader(uri, keys, constructorHelper);
  }

}
