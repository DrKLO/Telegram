/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import android.net.Uri;
import android.os.SystemClock;
import com.google.android.exoplayer2.upstream.DataSpec;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** {@link MediaSource} load event information. */
public final class LoadEventInfo {

  /** Used for the generation of unique ids. */
  private static final AtomicLong idSource = new AtomicLong();

  /** Returns an non-negative identifier which is unique to the JVM instance. */
  public static long getNewId() {
    return idSource.getAndIncrement();
  }

  /** Identifies the load task to which this event corresponds. */
  public final long loadTaskId;
  /** Defines the requested data. */
  public final DataSpec dataSpec;
  /**
   * The {@link Uri} from which data is being read. The uri will be identical to the one in {@link
   * #dataSpec}.uri unless redirection has occurred. If redirection has occurred, this is the uri
   * after redirection.
   */
  public final Uri uri;
  /** The response headers associated with the load, or an empty map if unavailable. */
  public final Map<String, List<String>> responseHeaders;
  /** The value of {@link SystemClock#elapsedRealtime} at the time of the load event. */
  public final long elapsedRealtimeMs;
  /** The duration of the load up to the event time. */
  public final long loadDurationMs;
  /** The number of bytes that were loaded up to the event time. */
  public final long bytesLoaded;

  /**
   * Equivalent to {@link #LoadEventInfo(long, DataSpec, Uri, Map, long, long, long)
   * LoadEventInfo(loadTaskId, dataSpec, dataSpec.uri, Collections.emptyMap(), elapsedRealtimeMs, 0,
   * 0)}.
   */
  public LoadEventInfo(long loadTaskId, DataSpec dataSpec, long elapsedRealtimeMs) {
    this(
        loadTaskId,
        dataSpec,
        dataSpec.uri,
        Collections.emptyMap(),
        elapsedRealtimeMs,
        /* loadDurationMs= */ 0,
        /* bytesLoaded= */ 0);
  }

  /**
   * Creates load event info.
   *
   * @param loadTaskId See {@link #loadTaskId}.
   * @param dataSpec See {@link #dataSpec}.
   * @param uri See {@link #uri}.
   * @param responseHeaders See {@link #responseHeaders}.
   * @param elapsedRealtimeMs See {@link #elapsedRealtimeMs}.
   * @param loadDurationMs See {@link #loadDurationMs}.
   * @param bytesLoaded See {@link #bytesLoaded}.
   */
  public LoadEventInfo(
      long loadTaskId,
      DataSpec dataSpec,
      Uri uri,
      Map<String, List<String>> responseHeaders,
      long elapsedRealtimeMs,
      long loadDurationMs,
      long bytesLoaded) {
    this.loadTaskId = loadTaskId;
    this.dataSpec = dataSpec;
    this.uri = uri;
    this.responseHeaders = responseHeaders;
    this.elapsedRealtimeMs = elapsedRealtimeMs;
    this.loadDurationMs = loadDurationMs;
    this.bytesLoaded = bytesLoaded;
  }
}
