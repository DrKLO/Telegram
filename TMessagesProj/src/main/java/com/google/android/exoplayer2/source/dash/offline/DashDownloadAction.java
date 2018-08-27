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
package com.google.android.exoplayer2.source.dash.offline;

import android.net.Uri;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.offline.DownloadAction;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.offline.SegmentDownloadAction;
import com.google.android.exoplayer2.offline.StreamKey;
import java.util.Collections;
import java.util.List;

/** An action to download or remove downloaded DASH streams. */
public final class DashDownloadAction extends SegmentDownloadAction {

  private static final String TYPE = "dash";
  private static final int VERSION = 0;

  public static final Deserializer DESERIALIZER =
      new SegmentDownloadActionDeserializer(TYPE, VERSION) {
        @Override
        protected DownloadAction createDownloadAction(
            Uri uri, boolean isRemoveAction, byte[] data, List<StreamKey> keys) {
          return new DashDownloadAction(uri, isRemoveAction, data, keys);
        }
      };

  /**
   * Creates a DASH download action.
   *
   * @param uri The URI of the media to be downloaded.
   * @param data Optional custom data for this action. If {@code null} an empty array will be used.
   * @param keys Keys of tracks to be downloaded. If empty, all tracks will be downloaded.
   */
  public static DashDownloadAction createDownloadAction(
      Uri uri, @Nullable byte[] data, List<StreamKey> keys) {
    return new DashDownloadAction(uri, /* isRemoveAction= */ false, data, keys);
  }

  /**
   * Creates a DASH remove action.
   *
   * @param uri The URI of the media to be removed.
   * @param data Optional custom data for this action. If {@code null} an empty array will be used.
   */
  public static DashDownloadAction createRemoveAction(Uri uri, @Nullable byte[] data) {
    return new DashDownloadAction(uri, /* isRemoveAction= */ true, data, Collections.emptyList());
  }

  /**
   * @param uri The DASH manifest URI.
   * @param isRemoveAction Whether the data will be removed. If {@code false} it will be downloaded.
   * @param data Optional custom data for this action.
   * @param keys Keys of representations to be downloaded. If empty, all representations are
   *     downloaded. If {@code removeAction} is true, {@code keys} must be empty.
   * @deprecated Use {@link #createDownloadAction(Uri, byte[], List)} or {@link
   *     #createRemoveAction(Uri, byte[])}.
   */
  @Deprecated
  public DashDownloadAction(
      Uri uri, boolean isRemoveAction, @Nullable byte[] data, List<StreamKey> keys) {
    super(TYPE, VERSION, uri, isRemoveAction, data, keys);
  }

  @Override
  public DashDownloader createDownloader(DownloaderConstructorHelper constructorHelper) {
    return new DashDownloader(uri, keys, constructorHelper);
  }

}
