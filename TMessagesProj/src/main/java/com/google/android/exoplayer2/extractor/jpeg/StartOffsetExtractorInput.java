/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.jpeg;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ForwardingExtractorInput;

/**
 * An extractor input that wraps another extractor input and exposes data starting at a given start
 * byte offset.
 *
 * <p>This is useful for reading data from a container that's concatenated after some prefix data
 * but where the container's extractor doesn't handle a non-zero start offset (for example, because
 * it seeks to absolute positions read from the container data).
 */
/* package */ final class StartOffsetExtractorInput extends ForwardingExtractorInput {

  private final long startOffset;

  /**
   * Creates a new wrapper reading from the given start byte offset.
   *
   * @param input The extractor input to wrap. The reading position must be at or after the start
   *     offset, otherwise data could be read from before the start offset.
   * @param startOffset The offset from which this extractor input provides data, in bytes.
   * @throws IllegalArgumentException Thrown if the start offset is before the current reading
   *     position.
   */
  public StartOffsetExtractorInput(ExtractorInput input, long startOffset) {
    super(input);
    checkArgument(input.getPosition() >= startOffset);
    this.startOffset = startOffset;
  }

  @Override
  public long getPosition() {
    return super.getPosition() - startOffset;
  }

  @Override
  public long getPeekPosition() {
    return super.getPeekPosition() - startOffset;
  }

  @Override
  public long getLength() {
    return super.getLength() - startOffset;
  }

  @Override
  public <E extends Throwable> void setRetryPosition(long position, E e) throws E {
    super.setRetryPosition(position + startOffset, e);
  }
}
