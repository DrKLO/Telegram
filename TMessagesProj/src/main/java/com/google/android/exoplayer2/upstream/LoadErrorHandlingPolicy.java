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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.source.chunk.ChunkedTrackBlacklistUtil;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer2.upstream.Loader.Callback;
import com.google.android.exoplayer2.upstream.Loader.Loadable;
import java.io.IOException;

/**
 * Defines how errors encountered by {@link Loader Loaders} are handled.
 *
 * <p>Loader clients may blacklist a resource when a load error occurs. Blacklisting works around
 * load errors by loading an alternative resource. Clients do not try blacklisting when a resource
 * does not have an alternative. When a resource does have valid alternatives, {@link
 * #getBlacklistDurationMsFor(T, long, IOException, int)} defines whether the resource should be
 * blacklisted. Blacklisting will succeed if any of the alternatives is not in the black list.
 *
 * <p>When blacklisting does not take place, {@link #getRetryDelayMsFor(T, long, IOException, int)}
 * defines whether the load is retried. Errors whose load is not retried are propagated. Load errors
 * whose load is retried are propagated according to {@link
 * #getMinimumLoadableRetryCount(Loadable)}.
 *
 * @param <T> The type of the object being loaded.
 */
public interface LoadErrorHandlingPolicy<T extends Loadable> {

  /** The default minimum number of times to retry loading data prior to propagating the error. */
  int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;

  /** Default implementation of {@link LoadErrorHandlingPolicy}. */
  LoadErrorHandlingPolicy<Loadable> DEFAULT =
      new LoadErrorHandlingPolicy<Loadable>() {

        /**
         * Blacklists resources whose load error was an {@link InvalidResponseCodeException} with
         * response code HTTP 404 or 410. The duration of the blacklisting is {@link
         * ChunkedTrackBlacklistUtil#DEFAULT_TRACK_BLACKLIST_MS}.
         */
        @Override
        public long getBlacklistDurationMsFor(
            Loadable loadable, long loadDurationMs, IOException exception, int errorCount) {
          if (exception instanceof InvalidResponseCodeException) {
            int responseCode = ((InvalidResponseCodeException) exception).responseCode;
            return responseCode == 404 // HTTP 404 Not Found.
                    || responseCode == 410 // HTTP 410 Gone.
                ? ChunkedTrackBlacklistUtil.DEFAULT_TRACK_BLACKLIST_MS
                : C.TIME_UNSET;
          }
          return C.TIME_UNSET;
        }

        /**
         * Retries for any exception that is not a subclass of {@link ParserException}. The retry
         * delay is calculated as {@code Math.min((errorCount - 1) * 1000, 5000)}.
         */
        @Override
        public long getRetryDelayMsFor(
            Loadable loadable, long loadDurationMs, IOException exception, int errorCount) {
          return exception instanceof ParserException
              ? C.TIME_UNSET
              : Math.min((errorCount - 1) * 1000, 5000);
        }

        /** Returns {@link #DEFAULT_MIN_LOADABLE_RETRY_COUNT}. */
        @Override
        public int getMinimumLoadableRetryCount(Loadable loadable) {
          return DEFAULT_MIN_LOADABLE_RETRY_COUNT;
        }
      };

  /** Returns {@link #DEFAULT}. */
  static <U extends Loadable> LoadErrorHandlingPolicy<U> getDefault() {
    @SuppressWarnings("unchecked") // Safe contravariant cast.
    LoadErrorHandlingPolicy<U> policy = (LoadErrorHandlingPolicy<U>) DEFAULT;
    return policy;
  }

  /**
   * Returns the number of milliseconds for which a resource associated to a provided load error
   * should be blacklisted, or {@link C#TIME_UNSET} if the resource should not be blacklisted.
   *
   * @param loadable The loadable whose load failed.
   * @param loadDurationMs The duration in milliseconds of the load up to the point at which the
   *     error occurred, including any previous attempts.
   * @param exception The load error.
   * @param errorCount The number of errors this load has encountered, including this one.
   * @return The blacklist duration in milliseconds, or {@link C#TIME_UNSET} if the resource should
   *     not be blacklisted.
   */
  long getBlacklistDurationMsFor(
      T loadable, long loadDurationMs, IOException exception, int errorCount);

  /**
   * Returns the number of milliseconds to wait before attempting the load again, or {@link
   * C#TIME_UNSET} if the error is fatal and should not be retried.
   *
   * <p>{@link Loader} clients may ignore the retry delay returned by this method in order to wait
   * for a specific event before retrying. However, the load is retried if and only if this method
   * does not return {@link C#TIME_UNSET}.
   *
   * @param loadable The loadable whose load failed.
   * @param loadDurationMs The duration in milliseconds of the load up to the point at which the
   *     error occurred, including any previous attempts.
   * @param exception The load error.
   * @param errorCount The number of errors this load has encountered, including this one.
   * @return The number of milliseconds to wait before attempting the load again, or {@link
   *     C#TIME_UNSET} if the error is fatal and should not be retried.
   */
  long getRetryDelayMsFor(T loadable, long loadDurationMs, IOException exception, int errorCount);

  /**
   * Returns the minimum number of times to retry a load in the case of a load error, before
   * propagating the error.
   *
   * @param loadable The loadable to load.
   * @return The minimum number of times to retry a load in the case of a load error, before
   *     propagating the error.
   * @see Loader#startLoading(Loadable, Callback, int)
   */
  int getMinimumLoadableRetryCount(T loadable);
}
