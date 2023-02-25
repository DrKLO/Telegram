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
package com.google.android.exoplayer2.extractor;

import java.io.IOException;

/** An overridable {@link ExtractorInput} implementation forwarding all methods to another input. */
public class ForwardingExtractorInput implements ExtractorInput {

  private final ExtractorInput input;

  public ForwardingExtractorInput(ExtractorInput input) {
    this.input = input;
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    return input.read(buffer, offset, length);
  }

  @Override
  public boolean readFully(byte[] target, int offset, int length, boolean allowEndOfInput)
      throws IOException {
    return input.readFully(target, offset, length, allowEndOfInput);
  }

  @Override
  public void readFully(byte[] target, int offset, int length) throws IOException {
    input.readFully(target, offset, length);
  }

  @Override
  public int skip(int length) throws IOException {
    return input.skip(length);
  }

  @Override
  public boolean skipFully(int length, boolean allowEndOfInput) throws IOException {
    return input.skipFully(length, allowEndOfInput);
  }

  @Override
  public void skipFully(int length) throws IOException {
    input.skipFully(length);
  }

  @Override
  public int peek(byte[] target, int offset, int length) throws IOException {
    return input.peek(target, offset, length);
  }

  @Override
  public boolean peekFully(byte[] target, int offset, int length, boolean allowEndOfInput)
      throws IOException {
    return input.peekFully(target, offset, length, allowEndOfInput);
  }

  @Override
  public void peekFully(byte[] target, int offset, int length) throws IOException {
    input.peekFully(target, offset, length);
  }

  @Override
  public boolean advancePeekPosition(int length, boolean allowEndOfInput) throws IOException {
    return input.advancePeekPosition(length, allowEndOfInput);
  }

  @Override
  public void advancePeekPosition(int length) throws IOException {
    input.advancePeekPosition(length);
  }

  @Override
  public void resetPeekPosition() {
    input.resetPeekPosition();
  }

  @Override
  public long getPeekPosition() {
    return input.getPeekPosition();
  }

  @Override
  public long getPosition() {
    return input.getPosition();
  }

  @Override
  public long getLength() {
    return input.getLength();
  }

  @Override
  public <E extends Throwable> void setRetryPosition(long position, E e) throws E {
    input.setRetryPosition(position, e);
  }
}
