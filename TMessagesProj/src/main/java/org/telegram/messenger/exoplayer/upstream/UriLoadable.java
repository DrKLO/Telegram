/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.upstream;

import android.net.Uri;
import org.telegram.messenger.exoplayer.ParserException;
import org.telegram.messenger.exoplayer.upstream.Loader.Loadable;
import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link Loadable} for loading an object from a URI.
 *
 * @param <T> The type of the object being loaded.
 */
public final class UriLoadable<T> implements Loadable {

  /**
   * Parses an object from loaded data.
   */
  public interface Parser<T> {

    /**
     * Parses an object from a response.
     *
     * @param connectionUrl The source of the response, after any redirection.
     * @param inputStream An {@link InputStream} from which the response data can be read.
     * @return The parsed object.
     * @throws ParserException If an error occurs parsing the data.
     * @throws IOException If an error occurs reading data from the stream.
     */
    T parse(String connectionUrl, InputStream inputStream) throws ParserException, IOException;

  }

  private final DataSpec dataSpec;
  private final UriDataSource uriDataSource;
  private final Parser<T> parser;

  private volatile T result;
  private volatile boolean isCanceled;

  /**
   * @param url The url from which the object should be loaded.
   * @param uriDataSource A {@link UriDataSource} to use when loading the data.
   * @param parser Parses the object from the response.
   */
  public UriLoadable(String url, UriDataSource uriDataSource, Parser<T> parser) {
    this.uriDataSource = uriDataSource;
    this.parser = parser;
    dataSpec = new DataSpec(Uri.parse(url), DataSpec.FLAG_ALLOW_GZIP);
  }

  /**
   * Returns the loaded object, or null if an object has not been loaded.
   */
  public final T getResult() {
    return result;
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
    DataSourceInputStream inputStream = new DataSourceInputStream(uriDataSource, dataSpec);
    try {
      inputStream.open();
      result = parser.parse(uriDataSource.getUri(), inputStream);
    } finally {
      inputStream.close();
    }
  }

}
