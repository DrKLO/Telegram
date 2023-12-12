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

import static java.lang.Math.min;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.upstream.HttpDataSource.CleartextNotPermittedException;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer2.upstream.Loader.UnexpectedLoaderException;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Default implementation of {@link LoadErrorHandlingPolicy}. */
public class DefaultLoadErrorHandlingPolicy implements LoadErrorHandlingPolicy {

  /** The default minimum number of times to retry loading data prior to propagating the error. */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;
  /**
   * The default minimum number of times to retry loading prior to failing for progressive live
   * streams.
   */
  public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT_PROGRESSIVE_LIVE = 6;
  /** The default duration for which a track is excluded in milliseconds. */
  public static final long DEFAULT_TRACK_EXCLUSION_MS = 60_000;
  /**
   * @deprecated Use {@link #DEFAULT_TRACK_EXCLUSION_MS} instead.
   */
  @Deprecated public static final long DEFAULT_TRACK_BLACKLIST_MS = DEFAULT_TRACK_EXCLUSION_MS;
  /** The default duration for which a location is excluded in milliseconds. */
  public static final long DEFAULT_LOCATION_EXCLUSION_MS = 5 * 60_000;

  private static final int DEFAULT_BEHAVIOR_MIN_LOADABLE_RETRY_COUNT = -1;

  private final int minimumLoadableRetryCount;

  /**
   * Creates an instance with default behavior.
   *
   * <p>{@link #getMinimumLoadableRetryCount} will return {@link
   * #DEFAULT_MIN_LOADABLE_RETRY_COUNT_PROGRESSIVE_LIVE} for {@code dataType} {@link
   * C#DATA_TYPE_MEDIA_PROGRESSIVE_LIVE}. For other {@code dataType} values, it will return {@link
   * #DEFAULT_MIN_LOADABLE_RETRY_COUNT}.
   */
  public DefaultLoadErrorHandlingPolicy() {
    this(DEFAULT_BEHAVIOR_MIN_LOADABLE_RETRY_COUNT);
  }

  /**
   * Creates an instance with the given value for {@link #getMinimumLoadableRetryCount(int)}.
   *
   * @param minimumLoadableRetryCount See {@link #getMinimumLoadableRetryCount}.
   */
  public DefaultLoadErrorHandlingPolicy(int minimumLoadableRetryCount) {
    this.minimumLoadableRetryCount = minimumLoadableRetryCount;
  }

  /**
   * Returns whether a loader should fall back to using another resource on encountering an error,
   * and if so the duration for which the failing resource should be excluded.
   *
   * <ul>
   *   <li>This policy will only specify a fallback if {@link #isEligibleForFallback} returns {@code
   *       true} for the error.
   *   <li>This policy will always specify a location fallback rather than a track fallback if both
   *       {@link FallbackOptions#isFallbackAvailable(int) are available}.
   *   <li>When a fallback is specified, the duration for which the failing resource will be
   *       excluded is {@link #DEFAULT_LOCATION_EXCLUSION_MS} or {@link
   *       #DEFAULT_TRACK_EXCLUSION_MS}, depending on the fallback type.
   * </ul>
   */
  @Override
  @Nullable
  public FallbackSelection getFallbackSelectionFor(
      FallbackOptions fallbackOptions, LoadErrorInfo loadErrorInfo) {
    if (!isEligibleForFallback(loadErrorInfo.exception)) {
      return null;
    }
    // Prefer location fallbacks to track fallbacks, when both are available.
    if (fallbackOptions.isFallbackAvailable(FALLBACK_TYPE_LOCATION)) {
      return new FallbackSelection(FALLBACK_TYPE_LOCATION, DEFAULT_LOCATION_EXCLUSION_MS);
    } else if (fallbackOptions.isFallbackAvailable(FALLBACK_TYPE_TRACK)) {
      return new FallbackSelection(FALLBACK_TYPE_TRACK, DEFAULT_TRACK_EXCLUSION_MS);
    }
    return null;
  }

  /**
   * Retries for any exception that is not a subclass of {@link ParserException}, {@link
   * FileNotFoundException}, {@link CleartextNotPermittedException} or {@link
   * UnexpectedLoaderException}, and for which {@link
   * DataSourceException#isCausedByPositionOutOfRange} returns {@code false}. The retry delay is
   * calculated as {@code Math.min((errorCount - 1) * 1000, 5000)}.
   */
  @Override
  public long getRetryDelayMsFor(LoadErrorInfo loadErrorInfo) {
    IOException exception = loadErrorInfo.exception;
    return exception instanceof ParserException
            || exception instanceof FileNotFoundException
            || exception instanceof CleartextNotPermittedException
            || exception instanceof UnexpectedLoaderException
            || DataSourceException.isCausedByPositionOutOfRange(exception)
        ? C.TIME_UNSET
        : min((loadErrorInfo.errorCount - 1) * 1000, 5000);
  }

  /**
   * See {@link #DefaultLoadErrorHandlingPolicy()} and {@link #DefaultLoadErrorHandlingPolicy(int)}
   * for documentation about the behavior of this method.
   */
  @Override
  public int getMinimumLoadableRetryCount(int dataType) {
    if (minimumLoadableRetryCount == DEFAULT_BEHAVIOR_MIN_LOADABLE_RETRY_COUNT) {
      return dataType == C.DATA_TYPE_MEDIA_PROGRESSIVE_LIVE
          ? DEFAULT_MIN_LOADABLE_RETRY_COUNT_PROGRESSIVE_LIVE
          : DEFAULT_MIN_LOADABLE_RETRY_COUNT;
    } else {
      return minimumLoadableRetryCount;
    }
  }

  /** Returns whether an error should trigger a fallback if possible. */
  protected boolean isEligibleForFallback(IOException exception) {
    if (!(exception instanceof InvalidResponseCodeException)) {
      return false;
    }
    InvalidResponseCodeException invalidResponseCodeException =
        (InvalidResponseCodeException) exception;
    return invalidResponseCodeException.responseCode == 403 // HTTP 403 Forbidden.
        || invalidResponseCodeException.responseCode == 404 // HTTP 404 Not Found.
        || invalidResponseCodeException.responseCode == 410 // HTTP 410 Gone.
        || invalidResponseCodeException.responseCode == 416 // HTTP 416 Range Not Satisfiable.
        || invalidResponseCodeException.responseCode == 500 // HTTP 500 Internal Server Error.
        || invalidResponseCodeException.responseCode == 503; // HTTP 503 Service Unavailable.
  }
}
