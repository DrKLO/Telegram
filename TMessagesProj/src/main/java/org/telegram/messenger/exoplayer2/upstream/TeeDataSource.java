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
import org.telegram.messenger.exoplayer2.util.Assertions;
import java.io.IOException;

/**
 * Tees data into a {@link DataSink} as the data is read.
 */
public final class TeeDataSource implements DataSource {

  private final DataSource upstream;
  private final DataSink dataSink;

  /**
   * @param upstream The upstream {@link DataSource}.
   * @param dataSink The {@link DataSink} into which data is written.
   */
  public TeeDataSource(DataSource upstream, DataSink dataSink) {
    this.upstream = Assertions.checkNotNull(upstream);
    this.dataSink = Assertions.checkNotNull(dataSink);
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    long dataLength = upstream.open(dataSpec);
    if (dataSpec.length == C.LENGTH_UNSET && dataLength != C.LENGTH_UNSET) {
      // Reconstruct dataSpec in order to provide the resolved length to the sink.
      dataSpec = new DataSpec(dataSpec.uri, dataSpec.absoluteStreamPosition, dataSpec.position,
          dataLength, dataSpec.key, dataSpec.flags);
    }
    dataSink.open(dataSpec);
    return dataLength;
  }

  @Override
  public int read(byte[] buffer, int offset, int max) throws IOException {
    int num = upstream.read(buffer, offset, max);
    if (num > 0) {
      // TODO: Consider continuing even if disk writes fail.
      dataSink.write(buffer, offset, num);
    }
    return num;
  }

  @Override
  public Uri getUri() {
    return upstream.getUri();
  }

  @Override
  public void close() throws IOException {
    try {
      upstream.close();
    } finally {
      dataSink.close();
    }
  }

}
