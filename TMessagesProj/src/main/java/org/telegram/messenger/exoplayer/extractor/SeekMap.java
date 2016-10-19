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
package org.telegram.messenger.exoplayer.extractor;

/**
 * Maps seek positions (in microseconds) to corresponding positions (byte offsets) in the stream.
 */
public interface SeekMap {

  /**
   * A {@link SeekMap} that does not support seeking.
   */
  public static final SeekMap UNSEEKABLE = new SeekMap() {

    @Override
    public boolean isSeekable() {
      return false;
    }

    @Override
    public long getPosition(long timeUs) {
      return 0;
    }

  };

  /**
   * Whether or not the seeking is supported.
   * <p>
   * If seeking is not supported then the only valid seek position is the start of the file, and so
   * {@link #getPosition(long)} will return 0 for all input values.
   *
   * @return True if seeking is supported. False otherwise.
   */
  boolean isSeekable();

  /**
   * Maps a seek position in microseconds to a corresponding position (byte offset) in the stream
   * from which data can be provided to the extractor.
   *
   * @param timeUs A seek position in microseconds.
   * @return The corresponding position (byte offset) in the stream from which data can be provided
   *     to the extractor, or 0 if {@code #isSeekable()} returns false.
   */
  long getPosition(long timeUs);

}
