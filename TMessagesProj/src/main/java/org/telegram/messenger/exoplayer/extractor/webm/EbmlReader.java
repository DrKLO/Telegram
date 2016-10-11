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
package org.telegram.messenger.exoplayer.extractor.webm;

import org.telegram.messenger.exoplayer.ParserException;
import org.telegram.messenger.exoplayer.extractor.ExtractorInput;
import java.io.IOException;

/**
 * Event-driven EBML reader that delivers events to an {@link EbmlReaderOutput}.
 * <p>
 * EBML can be summarized as a binary XML format somewhat similar to Protocol Buffers. It was
 * originally designed for the Matroska container format. More information about EBML and
 * Matroska is available <a href="http://www.matroska.org/technical/specs/index.html">here</a>.
 */
/* package */ interface EbmlReader {

  /**
   * Type for unknown elements.
   */
  public static final int TYPE_UNKNOWN = 0;
  /**
   * Type for elements that contain child elements.
   */
  public static final int TYPE_MASTER = 1;
  /**
   * Type for integer value elements of up to 8 bytes.
   */
  public static final int TYPE_UNSIGNED_INT = 2;
  /**
   * Type for string elements.
   */
  public static final int TYPE_STRING = 3;
  /**
   * Type for binary elements.
   */
  public static final int TYPE_BINARY = 4;
  /**
   * Type for IEEE floating point value elements of either 4 or 8 bytes.
   */
  public static final int TYPE_FLOAT = 5;

  /**
   * Initializes the extractor with an {@link EbmlReaderOutput}.
   *
   * @param output An {@link EbmlReaderOutput} to receive events.
   */
  public void init(EbmlReaderOutput output);

  /**
   * Resets the state of the reader.
   * <p>
   * Subsequent calls to {@link #read(ExtractorInput)} will start reading a new EBML structure
   * from scratch.
   */
  public void reset();

  /**
   * Reads from an {@link ExtractorInput}, invoking an event callback if possible.
   *
   * @param input The {@link ExtractorInput} from which data should be read.
   * @return True if data can continue to be read. False if the end of the input was encountered.
   * @throws ParserException If parsing fails.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread is interrupted.
   */
  public boolean read(ExtractorInput input) throws ParserException, IOException,
      InterruptedException;

}
