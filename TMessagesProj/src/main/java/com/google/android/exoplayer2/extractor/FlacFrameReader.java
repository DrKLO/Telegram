/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.flac.FlacConstants;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;

/**
 * Reads and peeks FLAC frame elements according to the <a
 * href="https://xiph.org/flac/format.html">FLAC format specification</a>.
 */
public final class FlacFrameReader {

  /** Holds a sample number. */
  public static final class SampleNumberHolder {
    /** The sample number. */
    public long sampleNumber;
  }

  /**
   * Checks whether the given FLAC frame header is valid and, if so, reads it and writes the frame
   * first sample number in {@code sampleNumberHolder}.
   *
   * <p>If the header is valid, the position of {@code data} is moved to the byte following it.
   * Otherwise, there is no guarantee on the position.
   *
   * @param data The array to read the data from, whose position must correspond to the frame
   *     header.
   * @param flacStreamMetadata The stream metadata.
   * @param frameStartMarker The frame start marker of the stream.
   * @param sampleNumberHolder The holder used to contain the sample number.
   * @return Whether the frame header is valid.
   */
  public static boolean checkAndReadFrameHeader(
      ParsableByteArray data,
      FlacStreamMetadata flacStreamMetadata,
      int frameStartMarker,
      SampleNumberHolder sampleNumberHolder) {
    int frameStartPosition = data.getPosition();

    long frameHeaderBytes = data.readUnsignedInt();
    if (frameHeaderBytes >>> 16 != frameStartMarker) {
      return false;
    }

    boolean isBlockSizeVariable = (frameHeaderBytes >>> 16 & 1) == 1;
    int blockSizeKey = (int) (frameHeaderBytes >> 12 & 0xF);
    int sampleRateKey = (int) (frameHeaderBytes >> 8 & 0xF);
    int channelAssignmentKey = (int) (frameHeaderBytes >> 4 & 0xF);
    int bitsPerSampleKey = (int) (frameHeaderBytes >> 1 & 0x7);
    boolean reservedBit = (frameHeaderBytes & 1) == 1;
    return checkChannelAssignment(channelAssignmentKey, flacStreamMetadata)
        && checkBitsPerSample(bitsPerSampleKey, flacStreamMetadata)
        && !reservedBit
        && checkAndReadFirstSampleNumber(
            data, flacStreamMetadata, isBlockSizeVariable, sampleNumberHolder)
        && checkAndReadBlockSizeSamples(data, flacStreamMetadata, blockSizeKey)
        && checkAndReadSampleRate(data, flacStreamMetadata, sampleRateKey)
        && checkAndReadCrc(data, frameStartPosition);
  }

  /**
   * Checks whether the given FLAC frame header is valid and, if so, writes the frame first sample
   * number in {@code sampleNumberHolder}.
   *
   * <p>The {@code input} peek position is left unchanged.
   *
   * @param input The input to get the data from, whose peek position must correspond to the frame
   *     header.
   * @param flacStreamMetadata The stream metadata.
   * @param frameStartMarker The frame start marker of the stream.
   * @param sampleNumberHolder The holder used to contain the sample number.
   * @return Whether the frame header is valid.
   */
  public static boolean checkFrameHeaderFromPeek(
      ExtractorInput input,
      FlacStreamMetadata flacStreamMetadata,
      int frameStartMarker,
      SampleNumberHolder sampleNumberHolder)
      throws IOException {
    long originalPeekPosition = input.getPeekPosition();

    byte[] frameStartBytes = new byte[2];
    input.peekFully(frameStartBytes, 0, 2);
    int frameStart = (frameStartBytes[0] & 0xFF) << 8 | (frameStartBytes[1] & 0xFF);
    if (frameStart != frameStartMarker) {
      input.resetPeekPosition();
      input.advancePeekPosition((int) (originalPeekPosition - input.getPosition()));
      return false;
    }

    ParsableByteArray scratch = new ParsableByteArray(FlacConstants.MAX_FRAME_HEADER_SIZE);
    System.arraycopy(
        frameStartBytes, /* srcPos= */ 0, scratch.getData(), /* destPos= */ 0, /* length= */ 2);

    int totalBytesPeeked =
        ExtractorUtil.peekToLength(
            input, scratch.getData(), 2, FlacConstants.MAX_FRAME_HEADER_SIZE - 2);
    scratch.setLimit(totalBytesPeeked);

    input.resetPeekPosition();
    input.advancePeekPosition((int) (originalPeekPosition - input.getPosition()));

    return checkAndReadFrameHeader(
        scratch, flacStreamMetadata, frameStartMarker, sampleNumberHolder);
  }

  /**
   * Returns the number of the first sample in the given frame.
   *
   * <p>The read position of {@code input} is left unchanged.
   *
   * <p>If no exception is thrown, the peek position is aligned with the read position. Otherwise,
   * there is no guarantee on the peek position.
   *
   * @param input Input stream to get the sample number from (starting from the read position).
   * @return The frame first sample number.
   * @throws ParserException If an error occurs parsing the sample number.
   * @throws IOException If peeking from the input fails.
   */
  public static long getFirstSampleNumber(
      ExtractorInput input, FlacStreamMetadata flacStreamMetadata) throws IOException {
    input.resetPeekPosition();
    input.advancePeekPosition(1);
    byte[] blockingStrategyByte = new byte[1];
    input.peekFully(blockingStrategyByte, 0, 1);
    boolean isBlockSizeVariable = (blockingStrategyByte[0] & 1) == 1;
    input.advancePeekPosition(2);

    int maxUtf8SampleNumberSize = isBlockSizeVariable ? 7 : 6;
    ParsableByteArray scratch = new ParsableByteArray(maxUtf8SampleNumberSize);
    int totalBytesPeeked =
        ExtractorUtil.peekToLength(input, scratch.getData(), 0, maxUtf8SampleNumberSize);
    scratch.setLimit(totalBytesPeeked);
    input.resetPeekPosition();

    SampleNumberHolder sampleNumberHolder = new SampleNumberHolder();
    if (!checkAndReadFirstSampleNumber(
        scratch, flacStreamMetadata, isBlockSizeVariable, sampleNumberHolder)) {
      throw ParserException.createForMalformedContainer(/* message= */ null, /* cause= */ null);
    }

    return sampleNumberHolder.sampleNumber;
  }

  /**
   * Reads the given block size.
   *
   * @param data The array to read the data from, whose position must correspond to the block size
   *     bits.
   * @param blockSizeKey The key in the block size lookup table.
   * @return The block size in samples, or -1 if the {@code blockSizeKey} is invalid.
   */
  public static int readFrameBlockSizeSamplesFromKey(ParsableByteArray data, int blockSizeKey) {
    switch (blockSizeKey) {
      case 1:
        return 192;
      case 2:
      case 3:
      case 4:
      case 5:
        return 576 << (blockSizeKey - 2);
      case 6:
        return data.readUnsignedByte() + 1;
      case 7:
        return data.readUnsignedShort() + 1;
      case 8:
      case 9:
      case 10:
      case 11:
      case 12:
      case 13:
      case 14:
      case 15:
        return 256 << (blockSizeKey - 8);
      default:
        return -1;
    }
  }

  /**
   * Checks whether the given channel assignment is valid.
   *
   * @param channelAssignmentKey The channel assignment lookup key.
   * @param flacStreamMetadata The stream metadata.
   * @return Whether the channel assignment is valid.
   */
  private static boolean checkChannelAssignment(
      int channelAssignmentKey, FlacStreamMetadata flacStreamMetadata) {
    if (channelAssignmentKey <= 7) {
      return channelAssignmentKey == flacStreamMetadata.channels - 1;
    } else if (channelAssignmentKey <= 10) {
      return flacStreamMetadata.channels == 2;
    } else {
      return false;
    }
  }

  /**
   * Checks whether the given number of bits per sample is valid.
   *
   * @param bitsPerSampleKey The bits per sample lookup key.
   * @param flacStreamMetadata The stream metadata.
   * @return Whether the number of bits per sample is valid.
   */
  private static boolean checkBitsPerSample(
      int bitsPerSampleKey, FlacStreamMetadata flacStreamMetadata) {
    if (bitsPerSampleKey == 0) {
      return true;
    }
    return bitsPerSampleKey == flacStreamMetadata.bitsPerSampleLookupKey;
  }

  /**
   * Checks whether the given sample number is valid and, if so, reads it and writes it in {@code
   * sampleNumberHolder}.
   *
   * <p>If the sample number is valid, the position of {@code data} is moved to the byte following
   * it. Otherwise, there is no guarantee on the position.
   *
   * @param data The array to read the data from, whose position must correspond to the sample
   *     number data.
   * @param flacStreamMetadata The stream metadata.
   * @param isBlockSizeVariable Whether the stream blocking strategy is variable block size or fixed
   *     block size.
   * @param sampleNumberHolder The holder used to contain the sample number.
   * @return Whether the sample number is valid.
   */
  private static boolean checkAndReadFirstSampleNumber(
      ParsableByteArray data,
      FlacStreamMetadata flacStreamMetadata,
      boolean isBlockSizeVariable,
      SampleNumberHolder sampleNumberHolder) {
    long utf8Value;
    try {
      utf8Value = data.readUtf8EncodedLong();
    } catch (NumberFormatException e) {
      return false;
    }

    sampleNumberHolder.sampleNumber =
        isBlockSizeVariable ? utf8Value : utf8Value * flacStreamMetadata.maxBlockSizeSamples;
    return true;
  }

  /**
   * Checks whether the given frame block size key and block size bits are valid and, if so, reads
   * the block size bits.
   *
   * <p>If the block size is valid, the position of {@code data} is moved to the byte following the
   * block size bits. Otherwise, there is no guarantee on the position.
   *
   * @param data The array to read the data from, whose position must correspond to the block size
   *     bits.
   * @param flacStreamMetadata The stream metadata.
   * @param blockSizeKey The key in the block size lookup table.
   * @return Whether the block size is valid.
   */
  private static boolean checkAndReadBlockSizeSamples(
      ParsableByteArray data, FlacStreamMetadata flacStreamMetadata, int blockSizeKey) {
    int blockSizeSamples = readFrameBlockSizeSamplesFromKey(data, blockSizeKey);
    return blockSizeSamples != -1 && blockSizeSamples <= flacStreamMetadata.maxBlockSizeSamples;
  }

  /**
   * Checks whether the given sample rate key and sample rate bits are valid and, if so, reads the
   * sample rate bits.
   *
   * <p>If the sample rate is valid, the position of {@code data} is moved to the byte following the
   * sample rate bits. Otherwise, there is no guarantee on the position.
   *
   * @param data The array to read the data from, whose position must indicate the sample rate bits.
   * @param flacStreamMetadata The stream metadata.
   * @param sampleRateKey The key in the sample rate lookup table.
   * @return Whether the sample rate is valid.
   */
  private static boolean checkAndReadSampleRate(
      ParsableByteArray data, FlacStreamMetadata flacStreamMetadata, int sampleRateKey) {
    int expectedSampleRate = flacStreamMetadata.sampleRate;
    if (sampleRateKey == 0) {
      return true;
    } else if (sampleRateKey <= 11) {
      return sampleRateKey == flacStreamMetadata.sampleRateLookupKey;
    } else if (sampleRateKey == 12) {
      return data.readUnsignedByte() * 1000 == expectedSampleRate;
    } else if (sampleRateKey <= 14) {
      int sampleRate = data.readUnsignedShort();
      if (sampleRateKey == 14) {
        sampleRate *= 10;
      }
      return sampleRate == expectedSampleRate;
    } else {
      return false;
    }
  }

  /**
   * Checks whether the given CRC is valid and, if so, reads it.
   *
   * <p>If the CRC is valid, the position of {@code data} is moved to the byte following it.
   * Otherwise, there is no guarantee on the position.
   *
   * <p>The {@code data} array must contain the whole frame header.
   *
   * @param data The array to read the data from, whose position must indicate the CRC.
   * @param frameStartPosition The frame start offset in {@code data}.
   * @return Whether the CRC is valid.
   */
  private static boolean checkAndReadCrc(ParsableByteArray data, int frameStartPosition) {
    int crc = data.readUnsignedByte();
    int frameEndPosition = data.getPosition();
    int expectedCrc =
        Util.crc8(data.getData(), frameStartPosition, frameEndPosition - 1, /* initialValue= */ 0);
    return crc == expectedCrc;
  }

  private FlacFrameReader() {}
}
