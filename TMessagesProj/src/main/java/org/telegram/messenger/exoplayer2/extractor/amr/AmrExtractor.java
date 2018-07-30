/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.telegram.messenger.exoplayer2.extractor.amr;

import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.ParserException;
import org.telegram.messenger.exoplayer2.extractor.Extractor;
import org.telegram.messenger.exoplayer2.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer2.extractor.ExtractorOutput;
import org.telegram.messenger.exoplayer2.extractor.ExtractorsFactory;
import org.telegram.messenger.exoplayer2.extractor.PositionHolder;
import org.telegram.messenger.exoplayer2.extractor.SeekMap;
import org.telegram.messenger.exoplayer2.extractor.TrackOutput;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

/**
 * Extracts data from the AMR containers format (either AMR or AMR-WB). This follows RFC-4867,
 * section 5.
 *
 * <p>This extractor only supports single-channel AMR container formats.
 */
public final class AmrExtractor implements Extractor {

  /** Factory for {@link AmrExtractor} instances. */
  public static final ExtractorsFactory FACTORY =
      new ExtractorsFactory() {

        @Override
        public Extractor[] createExtractors() {
          return new Extractor[] {new AmrExtractor()};
        }
      };

  /**
   * The frame size in bytes, including header (1 byte), for each of the 16 frame types for AMR
   * narrow band.
   */
  private static final int[] frameSizeBytesByTypeNb = {
    13,
    14,
    16,
    18,
    20,
    21,
    27,
    32,
    6, // AMR SID
    7, // GSM-EFR SID
    6, // TDMA-EFR SID
    6, // PDC-EFR SID
    1, // Future use
    1, // Future use
    1, // Future use
    1 // No data
  };

  /**
   * The frame size in bytes, including header (1 byte), for each of the 16 frame types for AMR wide
   * band.
   */
  private static final int[] frameSizeBytesByTypeWb = {
    18,
    24,
    33,
    37,
    41,
    47,
    51,
    59,
    61,
    6, // AMR-WB SID
    1, // Future use
    1, // Future use
    1, // Future use
    1, // Future use
    1, // speech lost
    1 // No data
  };

  private static final byte[] amrSignatureNb = Util.getUtf8Bytes("#!AMR\n");
  private static final byte[] amrSignatureWb = Util.getUtf8Bytes("#!AMR-WB\n");

  /** Theoretical maximum frame size for a AMR frame. */
  private static final int MAX_FRAME_SIZE_BYTES = frameSizeBytesByTypeWb[8];

  private static final int SAMPLE_RATE_WB = 16_000;
  private static final int SAMPLE_RATE_NB = 8_000;
  private static final int SAMPLE_TIME_PER_FRAME_US = 20_000;

  private final byte[] scratch;

  private boolean isWideBand;
  private long currentSampleTimeUs;
  private int currentSampleTotalBytes;
  private int currentSampleBytesRemaining;

  private TrackOutput trackOutput;
  private boolean hasOutputFormat;

  public AmrExtractor() {
    scratch = new byte[1];
  }

  // Extractor implementation.

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    return readAmrHeader(input);
  }

  @Override
  public void init(ExtractorOutput output) {
    output.seekMap(new SeekMap.Unseekable(C.TIME_UNSET));
    trackOutput = output.track(/* id= */ 0, C.TRACK_TYPE_AUDIO);
    output.endTracks();
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    if (input.getPosition() == 0) {
      if (!readAmrHeader(input)) {
        throw new ParserException("Could not find AMR header.");
      }
    }
    maybeOutputFormat();
    return readSample(input);
  }

  @Override
  public void seek(long position, long timeUs) {
    currentSampleTimeUs = 0;
    currentSampleTotalBytes = 0;
    currentSampleBytesRemaining = 0;
  }

  @Override
  public void release() {
    // Do nothing
  }

  /* package */ static int frameSizeBytesByTypeNb(int frameType) {
    return frameSizeBytesByTypeNb[frameType];
  }

  /* package */ static int frameSizeBytesByTypeWb(int frameType) {
    return frameSizeBytesByTypeWb[frameType];
  }

  /* package */ static byte[] amrSignatureNb() {
    return Arrays.copyOf(amrSignatureNb, amrSignatureNb.length);
  }

  /* package */ static byte[] amrSignatureWb() {
    return Arrays.copyOf(amrSignatureWb, amrSignatureWb.length);
  }

  // Internal methods.

  /**
   * Peeks the AMR header from the beginning of the input, and consumes it if it exists.
   *
   * @param input The {@link ExtractorInput} from which data should be peeked/read.
   * @return Whether the AMR header has been read.
   */
  private boolean readAmrHeader(ExtractorInput input) throws IOException, InterruptedException {
    if (peekAmrSignature(input, amrSignatureNb)) {
      isWideBand = false;
      input.skipFully(amrSignatureNb.length);
      return true;
    } else if (peekAmrSignature(input, amrSignatureWb)) {
      isWideBand = true;
      input.skipFully(amrSignatureWb.length);
      return true;
    }
    return false;
  }

  /** Peeks from the beginning of the input to see if the given AMR signature exists. */
  private boolean peekAmrSignature(ExtractorInput input, byte[] amrSignature)
      throws IOException, InterruptedException {
    input.resetPeekPosition();
    byte[] header = new byte[amrSignature.length];
    input.peekFully(header, 0, amrSignature.length);
    return Arrays.equals(header, amrSignature);
  }

  private void maybeOutputFormat() {
    if (!hasOutputFormat) {
      hasOutputFormat = true;
      String mimeType = isWideBand ? MimeTypes.AUDIO_AMR_WB : MimeTypes.AUDIO_AMR_NB;
      int sampleRate = isWideBand ? SAMPLE_RATE_WB : SAMPLE_RATE_NB;
      trackOutput.format(
          Format.createAudioSampleFormat(
              /* id= */ null,
              mimeType,
              /* codecs= */ null,
              /* bitrate= */ Format.NO_VALUE,
              MAX_FRAME_SIZE_BYTES,
              /* channelCount= */ 1,
              sampleRate,
              /* pcmEncoding= */ Format.NO_VALUE,
              /* initializationData= */ null,
              /* drmInitData= */ null,
              /* selectionFlags= */ 0,
              /* language= */ null));
    }
  }

  private int readSample(ExtractorInput extractorInput) throws IOException, InterruptedException {
    if (currentSampleBytesRemaining == 0) {
      try {
        currentSampleTotalBytes = readNextSampleSize(extractorInput);
      } catch (EOFException e) {
        return RESULT_END_OF_INPUT;
      }
      currentSampleBytesRemaining = currentSampleTotalBytes;
    }

    int bytesAppended =
        trackOutput.sampleData(
            extractorInput, currentSampleBytesRemaining, /* allowEndOfInput= */ true);
    if (bytesAppended == C.RESULT_END_OF_INPUT) {
      return RESULT_END_OF_INPUT;
    }
    currentSampleBytesRemaining -= bytesAppended;
    if (currentSampleBytesRemaining > 0) {
      return RESULT_CONTINUE;
    }

    trackOutput.sampleMetadata(
        currentSampleTimeUs,
        C.BUFFER_FLAG_KEY_FRAME,
        currentSampleTotalBytes,
        /* offset= */ 0,
        /* encryptionData= */ null);
    currentSampleTimeUs += SAMPLE_TIME_PER_FRAME_US;
    return RESULT_CONTINUE;
  }

  private int readNextSampleSize(ExtractorInput extractorInput)
      throws IOException, InterruptedException {
    extractorInput.resetPeekPosition();
    extractorInput.peekFully(scratch, /* offset= */ 0, /* length= */ 1);

    byte frameHeader = scratch[0];
    if ((frameHeader & 0x83) > 0) {
      // The padding bits are at bit-1 positions in the following pattern: 1000 0011
      // Padding bits must be 0.
      throw new ParserException("Invalid padding bits for frame header " + frameHeader);
    }

    int frameType = (frameHeader >> 3) & 0x0f;
    return getFrameSizeInBytes(frameType);
  }

  private int getFrameSizeInBytes(int frameType) throws ParserException {
    if (!isValidFrameType(frameType)) {
      throw new ParserException(
          "Illegal AMR " + (isWideBand ? "WB" : "NB") + " frame type " + frameType);
    }

    return isWideBand ? frameSizeBytesByTypeWb[frameType] : frameSizeBytesByTypeNb[frameType];
  }

  private boolean isValidFrameType(int frameType) {
    return frameType >= 0
        && frameType <= 15
        && (isWideBandValidFrameType(frameType) || isNarrowBandValidFrameType(frameType));
  }

  private boolean isWideBandValidFrameType(int frameType) {
    // For wide band, type 10-13 are for future use.
    return isWideBand && (frameType < 10 || frameType > 13);
  }

  private boolean isNarrowBandValidFrameType(int frameType) {
    // For narrow band, type 12-14 are for future use.
    return !isWideBand && (frameType < 12 || frameType > 14);
  }
}
