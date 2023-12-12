/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.upstream;

import android.net.Uri;
import androidx.annotation.Nullable;
import java.io.IOException;

/** A DataSource which provides no data. {@link #open(DataSpec)} throws {@link IOException}. */
public final class PlaceholderDataSource implements DataSource {

  public static final PlaceholderDataSource INSTANCE = new PlaceholderDataSource();

  /** A factory that produces {@link PlaceholderDataSource}. */
  public static final Factory FACTORY = PlaceholderDataSource::new;

  private PlaceholderDataSource() {}

  @Override
  public void addTransferListener(TransferListener transferListener) {
    // Do nothing.
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    throw new IOException("PlaceholderDataSource cannot be opened");
  }

  @Override
  public int read(byte[] buffer, int offset, int length) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public Uri getUri() {
    return null;
  }

  @Override
  public void close() {
    // do nothing.
  }
}
