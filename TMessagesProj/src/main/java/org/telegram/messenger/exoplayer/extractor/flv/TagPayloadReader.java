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
package org.telegram.messenger.exoplayer.extractor.flv;

import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.ParserException;
import org.telegram.messenger.exoplayer.extractor.TrackOutput;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;

/**
 * Extracts individual samples from FLV tags, preserving original order.
 */
/* package */ abstract class TagPayloadReader {

  /**
   * Thrown when the format is not supported.
   */
  public static final class UnsupportedFormatException extends ParserException {

    public UnsupportedFormatException(String msg) {
      super(msg);
    }

  }

  protected final TrackOutput output;

  private long durationUs;

  /**
   * @param output A {@link TrackOutput} to which samples should be written.
   */
  protected TagPayloadReader(TrackOutput output) {
    this.output = output;
    this.durationUs = C.UNKNOWN_TIME_US;
  }

  /**
   * Sets duration in microseconds.
   *
   * @param durationUs duration in microseconds.
   */
  public final void setDurationUs(long durationUs) {
    this.durationUs = durationUs;
  }

  /**
   * Gets the duration in microseconds.
   *
   * @return The duration in microseconds.
   */
  public final long getDurationUs() {
    return durationUs;
  }

  /**
   * Notifies the reader that a seek has occurred.
   * <p>
   * Following a call to this method, the data passed to the next invocation of
   * {@link #consume(ParsableByteArray, long)} will not be a continuation of the data that
   * was previously passed. Hence the reader should reset any internal state.
   */
  public abstract void seek();

  /**
   * Consumes payload data.
   *
   * @param data The payload data to consume.
   * @param timeUs The timestamp associated with the payload.
   * @throws ParserException If an error occurs parsing the data.
   */
  public final void consume(ParsableByteArray data, long timeUs) throws ParserException {
    if (parseHeader(data)) {
      parsePayload(data, timeUs);
    }
  }

  /**
   * Parses tag header.
   *
   * @param data Buffer where the tag header is stored.
   * @return True if the header was parsed successfully and the payload should be read. False
   *     otherwise.
   * @throws ParserException If an error occurs parsing the header.
   */
  protected abstract boolean parseHeader(ParsableByteArray data) throws ParserException;

  /**
   * Parses tag payload.
   *
   * @param data Buffer where tag payload is stored
   * @param timeUs Time position of the frame
   * @throws ParserException If an error occurs parsing the payload.
   */
  protected abstract void parsePayload(ParsableByteArray data, long timeUs) throws ParserException;

}
