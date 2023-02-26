/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Reads data from URI-identified resources. */
public interface DataSource extends DataReader {

  /** A factory for {@link DataSource} instances. */
  interface Factory {

    /** Creates a {@link DataSource} instance. */
    DataSource createDataSource();
  }

  /**
   * Adds a {@link TransferListener} to listen to data transfers. This method is not thread-safe.
   *
   * @param transferListener A {@link TransferListener}.
   */
  void addTransferListener(TransferListener transferListener);

  /**
   * Opens the source to read the specified data. If an {@link IOException} is thrown, callers must
   * still call {@link #close()} to ensure that any partial effects of the invocation are cleaned
   * up.
   *
   * <p>The following edge case behaviors apply:
   *
   * <ul>
   *   <li>If the {@link DataSpec#position requested position} is within the resource, but the
   *       {@link DataSpec#length requested length} extends beyond the end of the resource, then
   *       {@link #open} will succeed and data from the requested position to the end of the
   *       resource will be made available through {@link #read}.
   *   <li>If the {@link DataSpec#position requested position} is equal to the length of the
   *       resource, then {@link #open} will succeed, and {@link #read} will immediately return
   *       {@link C#RESULT_END_OF_INPUT}.
   *   <li>If the {@link DataSpec#position requested position} is greater than the length of the
   *       resource, then {@link #open} will throw an {@link IOException} for which {@link
   *       DataSourceException#isCausedByPositionOutOfRange} will be {@code true}.
   * </ul>
   *
   * @param dataSpec Defines the data to be read.
   * @throws IOException If an error occurs opening the source. {@link DataSourceException} can be
   *     thrown or used as a cause of the thrown exception to specify the reason of the error.
   * @return The number of bytes that can be read from the opened source. For unbounded requests
   *     (i.e., requests where {@link DataSpec#length} equals {@link C#LENGTH_UNSET}) this value is
   *     the resolved length of the request, or {@link C#LENGTH_UNSET} if the length is still
   *     unresolved. For all other requests, the value returned will be equal to the request's
   *     {@link DataSpec#length}.
   */
  long open(DataSpec dataSpec) throws IOException;

  /**
   * When the source is open, returns the {@link Uri} from which data is being read. The returned
   * {@link Uri} will be identical to the one passed {@link #open(DataSpec)} in the {@link DataSpec}
   * unless redirection has occurred. If redirection has occurred, the {@link Uri} after redirection
   * is returned.
   *
   * @return The {@link Uri} from which data is being read, or null if the source is not open.
   */
  @Nullable
  Uri getUri();

  /**
   * When the source is open, returns the response headers associated with the last {@link #open}
   * call. Otherwise, returns an empty map.
   *
   * <p>Key look-up in the returned map is case-insensitive.
   */
  default Map<String, List<String>> getResponseHeaders() {
    return Collections.emptyMap();
  }

  /**
   * Closes the source. This method must be called even if the corresponding call to {@link
   * #open(DataSpec)} threw an {@link IOException}.
   *
   * @throws IOException If an error occurs closing the source.
   */
  void close() throws IOException;
}
