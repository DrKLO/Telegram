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
package com.google.android.exoplayer2.source.hls;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import java.io.IOException;

/** Extracts samples and track {@link Format Formats} from {@link HlsMediaChunk HlsMediaChunks}. */
public interface HlsMediaChunkExtractor {

  /**
   * Initializes the extractor with an {@link ExtractorOutput}. Called at most once.
   *
   * @param extractorOutput An {@link ExtractorOutput} to receive extracted data.
   */
  void init(ExtractorOutput extractorOutput);

  /**
   * Extracts data read from a provided {@link ExtractorInput}. Must not be called before {@link
   * #init(ExtractorOutput)}.
   *
   * <p>A single call to this method will block until some progress has been made, but will not
   * block for longer than this. Hence each call will consume only a small amount of input data.
   *
   * <p>When this method throws an {@link IOException}, extraction may continue by providing an
   * {@link ExtractorInput} with an unchanged {@link ExtractorInput#getPosition() read position} to
   * a subsequent call to this method.
   *
   * @param extractorInput The input to read from.
   * @return Whether there is any data left to extract. Returns false if the end of input has been
   *     reached.
   * @throws IOException If an error occurred reading from or parsing the input.
   */
  boolean read(ExtractorInput extractorInput) throws IOException;

  /** Returns whether this is a packed audio extractor, as defined in RFC 8216, Section 3.4. */
  boolean isPackedAudioExtractor();

  /** Returns whether this instance can be used for extracting multiple continuous segments. */
  boolean isReusable();

  /**
   * Returns a new instance for extracting the same type of media as this one. Can only be called on
   * instances that are not {@link #isReusable() reusable}.
   */
  HlsMediaChunkExtractor recreate();

  /**
   * Resets the sample parsing state.
   *
   * <p>Resetting the parsing state allows support for Fragmented MP4 EXT-X-I-FRAME-STREAM-INF
   * segments. EXT-X-I-FRAME-STREAM-INF segments are truncated to include only a leading key frame.
   * After parsing said keyframe, an extractor may reach an unexpected end of file. By resetting its
   * state, we can continue feeding samples from the following segments to the extractor. See <a
   * href="https://github.com/google/ExoPlayer/issues/7512">#7512</a> for context.
   */
  void onTruncatedSegmentParsed();
}
