/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import com.google.android.exoplayer2.source.TrackGroupArray;
import java.util.List;

/** A {@link DownloadHelper} for progressive streams. */
public final class ProgressiveDownloadHelper extends DownloadHelper {

  private final Uri uri;
  private final @Nullable String customCacheKey;

  public ProgressiveDownloadHelper(Uri uri) {
    this(uri, null);
  }

  public ProgressiveDownloadHelper(Uri uri, @Nullable String customCacheKey) {
    this.uri = uri;
    this.customCacheKey = customCacheKey;
  }

  @Override
  protected void prepareInternal() {
    // Do nothing.
  }

  @Override
  public int getPeriodCount() {
    return 1;
  }

  @Override
  public TrackGroupArray getTrackGroups(int periodIndex) {
    return TrackGroupArray.EMPTY;
  }

  @Override
  public ProgressiveDownloadAction getDownloadAction(
      @Nullable byte[] data, List<TrackKey> trackKeys) {
    return ProgressiveDownloadAction.createDownloadAction(uri, data, customCacheKey);
  }

  @Override
  public ProgressiveDownloadAction getRemoveAction(@Nullable byte[] data) {
    return ProgressiveDownloadAction.createRemoveAction(uri, data, customCacheKey);
  }
}
