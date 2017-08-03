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
package org.telegram.messenger.exoplayer2.upstream;

import android.net.Uri;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.ParserException;
import org.telegram.messenger.exoplayer2.upstream.Loader.Loadable;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link Loadable} for objects that can be parsed from binary data using a {@link Parser}.
 *
 * @param <T> The type of the object being loaded.
 */
public final class ParsingLoadable<T> implements Loadable {

  /**
   * Parses an object from loaded data.
   */
  public interface Parser<T> {

    /**
     * Parses an object from a response.
     *
     * @param uri The source {@link Uri} of the response, after any redirection.
     * @param inputStream An {@link InputStream} from which the response data can be read.
     * @return The parsed object.
     * @throws ParserException If an error occurs parsing the data.
     * @throws IOException If an error occurs reading data from the stream.
     */
    T parse(Uri uri, InputStream inputStream) throws IOException;

  }

  /**
   * The {@link DataSpec} that defines the data to be loaded.
   */
  public final DataSpec dataSpec;
  /**
   * The type of the data. One of the {@code DATA_TYPE_*} constants defined in {@link C}. For
   * reporting only.
   */
  public final int type;

  private final DataSource dataSource;
  private final Parser<? extends T> parser;

  private volatile T result;
  private volatile boolean isCanceled;
  private volatile long bytesLoaded;

  /**
   * @param dataSource A {@link DataSource} to use when loading the data.
   * @param uri The {@link Uri} from which the object should be loaded.
   * @param type See {@link #type}.
   * @param parser Parses the object from the response.
   */
  public ParsingLoadable(DataSource dataSource, Uri uri, int type, Parser<? extends T> parser) {
    this.dataSource = dataSource;
    this.dataSpec = new DataSpec(uri, DataSpec.FLAG_ALLOW_GZIP);
    this.type = type;
    this.parser = parser;
  }

  /**
   * Returns the loaded object, or null if an object has not been loaded.
   */
  public final T getResult() {
    return result;
  }

  /**
   * Returns the number of bytes loaded. In the case that the network response was compressed, the
   * value returned is the size of the data <em>after</em> decompression.
   *
   * @return The number of bytes loaded.
   */
  public long bytesLoaded() {
    return bytesLoaded;
  }

  @Override
  public final void cancelLoad() {
    // We don't actually cancel anything, but we need to record the cancellation so that
    // isLoadCanceled can return the correct value.
    isCanceled = true;
  }

  @Override
  public final boolean isLoadCanceled() {
    return isCanceled;
  }

  @Override
  public final void load() throws IOException, InterruptedException {
    DataSourceInputStream inputStream = new DataSourceInputStream(dataSource, dataSpec);
    try {
      inputStream.open();
      result = parser.parse(dataSource.getUri(), inputStream);
    } finally {
      bytesLoaded = inputStream.bytesRead();
      Util.closeQuietly(inputStream);
    }
  }

}
