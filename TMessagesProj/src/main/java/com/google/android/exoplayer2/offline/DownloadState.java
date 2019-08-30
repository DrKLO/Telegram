/*
 * Copyright (C) 2018 The Android Open Source Project
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
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Represents state of a download. */
public final class DownloadState {

  /**
   * Download states. One of {@link #STATE_QUEUED}, {@link #STATE_STOPPED}, {@link
   * #STATE_DOWNLOADING}, {@link #STATE_COMPLETED}, {@link #STATE_FAILED}, {@link #STATE_REMOVING},
   * {@link #STATE_REMOVED} or {@link #STATE_RESTARTING}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    STATE_QUEUED,
    STATE_STOPPED,
    STATE_DOWNLOADING,
    STATE_COMPLETED,
    STATE_FAILED,
    STATE_REMOVING,
    STATE_REMOVED,
    STATE_RESTARTING
  })
  public @interface State {}
  /** The download is waiting to be started. */
  public static final int STATE_QUEUED = 0;
  /** The download is stopped. */
  public static final int STATE_STOPPED = 1;
  /** The download is currently started. */
  public static final int STATE_DOWNLOADING = 2;
  /** The download completed. */
  public static final int STATE_COMPLETED = 3;
  /** The download failed. */
  public static final int STATE_FAILED = 4;
  /** The download is being removed. */
  public static final int STATE_REMOVING = 5;
  /** The download is removed. */
  public static final int STATE_REMOVED = 6;
  /** The download will restart after all downloaded data is removed. */
  public static final int STATE_RESTARTING = 7;

  /** Failure reasons. Either {@link #FAILURE_REASON_NONE} or {@link #FAILURE_REASON_UNKNOWN}. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({FAILURE_REASON_NONE, FAILURE_REASON_UNKNOWN})
  public @interface FailureReason {}
  /** The download isn't failed. */
  public static final int FAILURE_REASON_NONE = 0;
  /** The download is failed because of unknown reason. */
  public static final int FAILURE_REASON_UNKNOWN = 1;

  /**
   * Download stop flags. Possible flag values are {@link #STOP_FLAG_DOWNLOAD_MANAGER_NOT_READY} and
   * {@link #STOP_FLAG_STOPPED}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
      flag = true,
      value = {STOP_FLAG_DOWNLOAD_MANAGER_NOT_READY, STOP_FLAG_STOPPED})
  public @interface StopFlags {}
  /** Download can't be started as the manager isn't ready. */
  public static final int STOP_FLAG_DOWNLOAD_MANAGER_NOT_READY = 1;
  /** All downloads are stopped by the application. */
  public static final int STOP_FLAG_STOPPED = 1 << 1;

  /** Returns the state string for the given state value. */
  public static String getStateString(@State int state) {
    switch (state) {
      case STATE_QUEUED:
        return "QUEUED";
      case STATE_STOPPED:
        return "STOPPED";
      case STATE_DOWNLOADING:
        return "DOWNLOADING";
      case STATE_COMPLETED:
        return "COMPLETED";
      case STATE_FAILED:
        return "FAILED";
      case STATE_REMOVING:
        return "REMOVING";
      case STATE_REMOVED:
        return "REMOVED";
      case STATE_RESTARTING:
        return "RESTARTING";
      default:
        throw new IllegalStateException();
    }
  }

  /** Returns the failure string for the given failure reason value. */
  public static String getFailureString(@FailureReason int failureReason) {
    switch (failureReason) {
      case FAILURE_REASON_NONE:
        return "NO_REASON";
      case FAILURE_REASON_UNKNOWN:
        return "UNKNOWN_REASON";
      default:
        throw new IllegalStateException();
    }
  }

  /** The unique content id. */
  public final String id;
  /** The type of the content. */
  public final String type;
  /** The Uri of the content. */
  public final Uri uri;
  /** A custom key for cache indexing. */
  @Nullable public final String cacheKey;
  /** The state of the download. */
  @State public final int state;
  /** The estimated download percentage, or {@link C#PERCENTAGE_UNSET} if unavailable. */
  public final float downloadPercentage;
  /** The total number of downloaded bytes. */
  public final long downloadedBytes;
  /** The total size of the media, or {@link C#LENGTH_UNSET} if unknown. */
  public final long totalBytes;
  /** The first time when download entry is created. */
  public final long startTimeMs;
  /** The last update time. */
  public final long updateTimeMs;
  /** Keys of streams to be downloaded. If empty, all streams will be downloaded. */
  public final StreamKey[] streamKeys;
  /** Optional custom data. */
  public final byte[] customMetadata;
  /**
   * If {@link #state} is {@link #STATE_FAILED} then this is the cause, otherwise {@link
   * #FAILURE_REASON_NONE}.
   */
  @FailureReason public final int failureReason;
  /** Download stop flags. These flags stop downloading any content. */
  public final int stopFlags;

  /* package */ DownloadState(
      String id,
      String type,
      Uri uri,
      @Nullable String cacheKey,
      @State int state,
      float downloadPercentage,
      long downloadedBytes,
      long totalBytes,
      @FailureReason int failureReason,
      @StopFlags int stopFlags,
      long startTimeMs,
      long updateTimeMs,
      StreamKey[] streamKeys,
      byte[] customMetadata) {
    this.stopFlags = stopFlags;
    Assertions.checkState(
        failureReason == FAILURE_REASON_NONE ? state != STATE_FAILED : state == STATE_FAILED);
    // TODO enable this when we start changing state immediately
    // Assertions.checkState(stopFlags == 0 || (state != STATE_DOWNLOADING && state !=
    // STATE_QUEUED));
    this.id = id;
    this.type = type;
    this.uri = uri;
    this.cacheKey = cacheKey;
    this.streamKeys = streamKeys;
    this.customMetadata = customMetadata;
    this.state = state;
    this.downloadPercentage = downloadPercentage;
    this.downloadedBytes = downloadedBytes;
    this.totalBytes = totalBytes;
    this.failureReason = failureReason;
    this.startTimeMs = startTimeMs;
    this.updateTimeMs = updateTimeMs;
  }
}
