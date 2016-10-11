/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.extractor;

import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import java.io.IOException;

/**
 * A dummy {@link TrackOutput} implementation.
 */
public class DummyTrackOutput implements TrackOutput {
  @Override
  public void format(MediaFormat format) {
    // Do nothing.
  }

  @Override
  public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    return input.skip(length);
  }

  @Override
  public void sampleData(ParsableByteArray data, int length) {
    data.skipBytes(length);
  }

  @Override
  public void sampleMetadata(long timeUs, int flags, int size, int offset, byte[] encryptionKey) {
    // Do nothing.
  }
}
