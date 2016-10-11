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

import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.SampleHolder;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import java.io.EOFException;
import java.io.IOException;

/**
 * Receives track level data extracted by an {@link Extractor}.
 */
public interface TrackOutput {

  /**
   * Invoked when the {@link MediaFormat} of the track has been extracted from the stream.
   *
   * @param format The extracted {@link MediaFormat}.
   */
  void format(MediaFormat format);

  /**
   * Invoked to write sample data to the output.
   *
   * @param input An {@link ExtractorInput} from which to read the sample data.
   * @param length The maximum length to read from the input.
   * @param allowEndOfInput True if encountering the end of the input having read no data is
   *     allowed, and should result in {@link C#RESULT_END_OF_INPUT} being returned. False if it
   *     should be considered an error, causing an {@link EOFException} to be thrown.
   * @return The number of bytes appended.
   * @throws IOException If an error occurred reading from the input.
   * @throws InterruptedException If the thread was interrupted.
   */
  int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException;

  /**
   * Invoked to write sample data to the output.
   *
   * @param data A {@link ParsableByteArray} from which to read the sample data.
   * @param length The number of bytes to read.
   */
  void sampleData(ParsableByteArray data, int length);

  /**
   * Invoked when metadata associated with a sample has been extracted from the stream.
   * <p>
   * The corresponding sample data will have already been passed to the output via calls to
   * {@link #sampleData(ExtractorInput, int, boolean)} or
   * {@link #sampleData(ParsableByteArray, int)}.
   *
   * @param timeUs The media timestamp associated with the sample, in microseconds.
   * @param flags Flags associated with the sample. See {@link SampleHolder#flags}.
   * @param size The size of the sample data, in bytes.
   * @param offset The number of bytes that have been passed to
   *     {@link #sampleData(ExtractorInput, int, boolean)} or
   *     {@link #sampleData(ParsableByteArray, int)} since the last byte belonging to the sample
   *     whose metadata is being passed.
   * @param encryptionKey The encryption key associated with the sample. May be null.
   */
  void sampleMetadata(long timeUs, int flags, int size, int offset, byte[] encryptionKey);

}
