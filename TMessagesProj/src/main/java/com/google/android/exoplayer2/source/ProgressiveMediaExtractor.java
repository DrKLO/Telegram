/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.upstream.DataReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Extracts the contents of a container file from a progressive media stream. */
public interface ProgressiveMediaExtractor {

  /** Creates {@link ProgressiveMediaExtractor} instances. */
  interface Factory {

    /**
     * Returns a new {@link ProgressiveMediaExtractor} instance.
     *
     * @param playerId The {@link PlayerId} of the player this extractor is used for.
     */
    ProgressiveMediaExtractor createProgressiveMediaExtractor(PlayerId playerId);
  }

  /**
   * Initializes the underlying infrastructure for reading from the input.
   *
   * @param dataReader The {@link DataReader} from which data should be read.
   * @param uri The {@link Uri} from which the media is obtained.
   * @param responseHeaders The response headers of the media, or an empty map if there are none.
   * @param position The initial position of the {@code dataReader} in the stream.
   * @param length The length of the stream, or {@link C#LENGTH_UNSET} if length is unknown.
   * @param output The {@link ExtractorOutput} that will be used to initialize the selected
   *     extractor.
   * @throws UnrecognizedInputFormatException Thrown if the input format could not be detected.
   * @throws IOException Thrown if the input could not be read.
   */
  void init(
      DataReader dataReader,
      Uri uri,
      Map<String, List<String>> responseHeaders,
      long position,
      long length,
      ExtractorOutput output)
      throws IOException;

  /** Releases any held resources. */
  void release();

  /**
   * Disables seeking in MP3 streams.
   *
   * <p>MP3 live streams commonly have seekable metadata, despite being unseekable.
   */
  void disableSeekingOnMp3Streams();

  /**
   * Returns the current read position in the input stream, or {@link C#POSITION_UNSET} if no input
   * is available.
   */
  long getCurrentInputPosition();

  /**
   * Notifies the extracting infrastructure that a seek has occurred.
   *
   * @param position The byte offset in the stream from which data will be provided.
   * @param seekTimeUs The seek time in microseconds.
   */
  void seek(long position, long seekTimeUs);

  /**
   * Extracts data starting at the current input stream position.
   *
   * @param positionHolder If {@link Extractor#RESULT_SEEK} is returned, this holder is updated to
   *     hold the position of the required data.
   * @return One of the {@link Extractor}{@code .RESULT_*} values.
   * @throws IOException If an error occurred reading from the input.
   */
  int read(PositionHolder positionHolder) throws IOException;
}
