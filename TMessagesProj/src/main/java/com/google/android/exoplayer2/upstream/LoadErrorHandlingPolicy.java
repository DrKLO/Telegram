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
package com.google.android.exoplayer2.upstream;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.upstream.Loader.Callback;
import com.google.android.exoplayer2.upstream.Loader.Loadable;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A policy that defines how load errors are handled.
 *
 * <p>Some loaders are able to choose between a number of alternate resources. Such loaders will
 * call {@link #getFallbackSelectionFor(FallbackOptions, LoadErrorInfo)} when a load error occurs.
 * The {@link FallbackSelection} returned by the policy defines whether the loader should fall back
 * to using another resource, and if so the duration for which the failing resource should be
 * excluded.
 *
 * <p>When fallback does not take place, a loader will call {@link
 * #getRetryDelayMsFor(LoadErrorInfo)}. The value returned by the policy defines whether the failed
 * load can be retried, and if so the duration to wait before retrying. If the policy indicates that
 * a load error should not be retried, it will be considered fatal by the loader. The loader may
 * also consider load errors that can be retried fatal if at least {@link
 * #getMinimumLoadableRetryCount(int)} retries have been attempted.
 *
 * <p>Methods are invoked on the playback thread.
 */
public interface LoadErrorHandlingPolicy {

  /** Fallback type. One of {@link #FALLBACK_TYPE_LOCATION} or {@link #FALLBACK_TYPE_TRACK}. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({FALLBACK_TYPE_LOCATION, FALLBACK_TYPE_TRACK})
  @interface FallbackType {}

  /**
   * Fallback to the same resource at a different location (i.e., a different URL through which the
   * exact same data can be requested).
   */
  int FALLBACK_TYPE_LOCATION = 1;
  /**
   * Fallback to a different track (i.e., a different representation of the same content; for
   * example the same video encoded at a different bitrate or resolution).
   */
  int FALLBACK_TYPE_TRACK = 2;

  /** Holds information about a load task error. */
  final class LoadErrorInfo {

    /** The {@link LoadEventInfo} associated with the load that encountered an error. */
    public final LoadEventInfo loadEventInfo;
    /** {@link MediaLoadData} associated with the load that encountered an error. */
    public final MediaLoadData mediaLoadData;
    /** The exception associated to the load error. */
    public final IOException exception;
    /** The number of errors this load task has encountered, including this one. */
    public final int errorCount;

    /** Creates an instance with the given values. */
    public LoadErrorInfo(
        LoadEventInfo loadEventInfo,
        MediaLoadData mediaLoadData,
        IOException exception,
        int errorCount) {
      this.loadEventInfo = loadEventInfo;
      this.mediaLoadData = mediaLoadData;
      this.exception = exception;
      this.errorCount = errorCount;
    }
  }

  /** Holds information about the available fallback options. */
  final class FallbackOptions {
    /** The number of available locations. */
    public final int numberOfLocations;
    /** The number of locations that are already excluded. */
    public final int numberOfExcludedLocations;
    /** The number of tracks. */
    public final int numberOfTracks;
    /** The number of tracks that are already excluded. */
    public final int numberOfExcludedTracks;

    /** Creates an instance. */
    public FallbackOptions(
        int numberOfLocations,
        int numberOfExcludedLocations,
        int numberOfTracks,
        int numberOfExcludedTracks) {
      this.numberOfLocations = numberOfLocations;
      this.numberOfExcludedLocations = numberOfExcludedLocations;
      this.numberOfTracks = numberOfTracks;
      this.numberOfExcludedTracks = numberOfExcludedTracks;
    }

    /** Returns whether a fallback is available for the given {@link FallbackType fallback type}. */
    public boolean isFallbackAvailable(@FallbackType int type) {
      return type == FALLBACK_TYPE_LOCATION
          ? numberOfLocations - numberOfExcludedLocations > 1
          : numberOfTracks - numberOfExcludedTracks > 1;
    }
  }

  /** A selected fallback option. */
  final class FallbackSelection {
    /** The type of fallback. */
    public final @FallbackType int type;
    /** The duration for which the failing resource should be excluded, in milliseconds. */
    public final long exclusionDurationMs;

    /**
     * Creates an instance.
     *
     * @param type The type of fallback.
     * @param exclusionDurationMs The duration for which the failing resource should be excluded, in
     *     milliseconds. Must be non-negative.
     */
    public FallbackSelection(@FallbackType int type, long exclusionDurationMs) {
      checkArgument(exclusionDurationMs >= 0);
      this.type = type;
      this.exclusionDurationMs = exclusionDurationMs;
    }
  }

  /**
   * Returns whether a loader should fall back to using another resource on encountering an error,
   * and if so the duration for which the failing resource should be excluded.
   *
   * <p>If the returned {@link FallbackSelection#type fallback type} was not {@link
   * FallbackOptions#isFallbackAvailable(int) advertised as available}, then the loader will not
   * fall back.
   *
   * @param fallbackOptions The available fallback options.
   * @param loadErrorInfo A {@link LoadErrorInfo} holding information about the load error.
   * @return The selected fallback, or {@code null} if the calling loader should not fall back.
   */
  @Nullable
  FallbackSelection getFallbackSelectionFor(
      FallbackOptions fallbackOptions, LoadErrorInfo loadErrorInfo);

  /**
   * Returns whether a loader can retry on encountering an error, and if so the duration to wait
   * before retrying. A return value of {@link C#TIME_UNSET} indicates that the error is fatal and
   * should not be retried.
   *
   * <p>For loads that can be retried, loaders may ignore the retry delay returned by this method in
   * order to wait for a specific event before retrying.
   *
   * @param loadErrorInfo A {@link LoadErrorInfo} holding information about the load error.
   * @return The duration to wait before retrying in milliseconds, or {@link C#TIME_UNSET} if the
   *     error is fatal and should not be retried.
   */
  long getRetryDelayMsFor(LoadErrorInfo loadErrorInfo);

  /**
   * Called once {@code loadTaskId} will not be associated with any more load errors.
   *
   * <p>Implementations should clean up any resources associated with {@code loadTaskId} when this
   * method is called.
   */
  default void onLoadTaskConcluded(long loadTaskId) {}

  /**
   * Returns the minimum number of times to retry a load before a load error that can be retried may
   * be considered fatal.
   *
   * @param dataType One of the {@link C C.DATA_TYPE_*} constants indicating the type of data being
   *     loaded.
   * @return The minimum number of times to retry a load before a load error that can be retried may
   *     be considered fatal.
   * @see Loader#startLoading(Loadable, Callback, int)
   */
  int getMinimumLoadableRetryCount(int dataType);
}
