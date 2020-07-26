/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.wav;

import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.audio.WavUtil;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Extracts data from WAV byte streams.
 */
public final class WavExtractor implements Extractor {

  /**
   * When outputting PCM data to a {@link TrackOutput}, we can choose how many frames are grouped
   * into each sample, and hence each sample's duration. This is the target number of samples to
   * output for each second of media, meaning that each sample will have a duration of ~100ms.
   */
  private static final int TARGET_SAMPLES_PER_SECOND = 10;

  /** Factory for {@link WavExtractor} instances. */
  public static final ExtractorsFactory FACTORY = () -> new Extractor[] {new WavExtractor()};

  @MonotonicNonNull private ExtractorOutput extractorOutput;
  @MonotonicNonNull private TrackOutput trackOutput;
  @MonotonicNonNull private OutputWriter outputWriter;
  private int dataStartPosition;
  private long dataEndPosition;

  public WavExtractor() {
    dataStartPosition = C.POSITION_UNSET;
    dataEndPosition = C.POSITION_UNSET;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    return WavHeaderReader.peek(input) != null;
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
    trackOutput = output.track(0, C.TRACK_TYPE_AUDIO);
    output.endTracks();
  }

  @Override
  public void seek(long position, long timeUs) {
    if (outputWriter != null) {
      outputWriter.reset(timeUs);
    }
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    assertInitialized();
    if (outputWriter == null) {
      WavHeader header = WavHeaderReader.peek(input);
      if (header == null) {
        // Should only happen if the media wasn't sniffed.
        throw new ParserException("Unsupported or unrecognized wav header.");
      }

      if (header.formatType == WavUtil.TYPE_IMA_ADPCM) {
        outputWriter = new ImaAdPcmOutputWriter(extractorOutput, trackOutput, header);
      } else if (header.formatType == WavUtil.TYPE_ALAW) {
        outputWriter =
            new PassthroughOutputWriter(
                extractorOutput,
                trackOutput,
                header,
                MimeTypes.AUDIO_ALAW,
                /* pcmEncoding= */ Format.NO_VALUE);
      } else if (header.formatType == WavUtil.TYPE_MLAW) {
        outputWriter =
            new PassthroughOutputWriter(
                extractorOutput,
                trackOutput,
                header,
                MimeTypes.AUDIO_MLAW,
                /* pcmEncoding= */ Format.NO_VALUE);
      } else {
        @C.PcmEncoding
        int pcmEncoding = WavUtil.getPcmEncodingForType(header.formatType, header.bitsPerSample);
        if (pcmEncoding == C.ENCODING_INVALID) {
          throw new ParserException("Unsupported WAV format type: " + header.formatType);
        }
        outputWriter =
            new PassthroughOutputWriter(
                extractorOutput, trackOutput, header, MimeTypes.AUDIO_RAW, pcmEncoding);
      }
    }

    if (dataStartPosition == C.POSITION_UNSET) {
      Pair<Long, Long> dataBounds = WavHeaderReader.skipToData(input);
      dataStartPosition = dataBounds.first.intValue();
      dataEndPosition = dataBounds.second;
      outputWriter.init(dataStartPosition, dataEndPosition);
    } else if (input.getPosition() == 0) {
      input.skipFully(dataStartPosition);
    }

    Assertions.checkState(dataEndPosition != C.POSITION_UNSET);
    long bytesLeft = dataEndPosition - input.getPosition();
    return outputWriter.sampleData(input, bytesLeft) ? RESULT_END_OF_INPUT : RESULT_CONTINUE;
  }

  @EnsuresNonNull({"extractorOutput", "trackOutput"})
  private void assertInitialized() {
    Assertions.checkStateNotNull(trackOutput);
    Util.castNonNull(extractorOutput);
  }

  /** Writes to the extractor's output. */
  private interface OutputWriter {

    /**
     * Resets the writer.
     *
     * @param timeUs The new start position in microseconds.
     */
    void reset(long timeUs);

    /**
     * Initializes the writer.
     *
     * <p>Must be called once, before any calls to {@link #sampleData(ExtractorInput, long)}.
     *
     * @param dataStartPosition The byte position (inclusive) in the stream at which data starts.
     * @param dataEndPosition The end position (exclusive) in the stream at which data ends.
     * @throws ParserException If an error occurs initializing the writer.
     */
    void init(int dataStartPosition, long dataEndPosition) throws ParserException;

    /**
     * Consumes sample data from {@code input}, writing corresponding samples to the extractor's
     * output.
     *
     * <p>Must not be called until after {@link #init(int, long)} has been called.
     *
     * @param input The input from which to read.
     * @param bytesLeft The number of sample data bytes left to be read from the input.
     * @return Whether the end of the sample data has been reached.
     * @throws IOException If an error occurs reading from the input.
     * @throws InterruptedException If the thread has been interrupted.
     */
    boolean sampleData(ExtractorInput input, long bytesLeft)
        throws IOException, InterruptedException;
  }

  private static final class PassthroughOutputWriter implements OutputWriter {

    private final ExtractorOutput extractorOutput;
    private final TrackOutput trackOutput;
    private final WavHeader header;
    private final Format format;
    /** The target size of each output sample, in bytes. */
    private final int targetSampleSizeBytes;

    /** The time at which the writer was last {@link #reset}. */
    private long startTimeUs;
    /**
     * The number of bytes that have been written to {@link #trackOutput} but have yet to be
     * included as part of a sample (i.e. the corresponding call to {@link
     * TrackOutput#sampleMetadata} has yet to be made).
     */
    private int pendingOutputBytes;
    /**
     * The total number of frames in samples that have been written to the trackOutput since the
     * last call to {@link #reset}.
     */
    private long outputFrameCount;

    public PassthroughOutputWriter(
        ExtractorOutput extractorOutput,
        TrackOutput trackOutput,
        WavHeader header,
        String mimeType,
        @C.PcmEncoding int pcmEncoding)
        throws ParserException {
      this.extractorOutput = extractorOutput;
      this.trackOutput = trackOutput;
      this.header = header;

      int bytesPerFrame = header.numChannels * header.bitsPerSample / 8;
      // Validate the header. Blocks are expected to correspond to single frames.
      if (header.blockSize != bytesPerFrame) {
        throw new ParserException(
            "Expected block size: " + bytesPerFrame + "; got: " + header.blockSize);
      }

      targetSampleSizeBytes =
          Math.max(bytesPerFrame, header.frameRateHz * bytesPerFrame / TARGET_SAMPLES_PER_SECOND);
      format =
          Format.createAudioSampleFormat(
              /* id= */ null,
              mimeType,
              /* codecs= */ null,
              /* bitrate= */ header.frameRateHz * bytesPerFrame * 8,
              /* maxInputSize= */ targetSampleSizeBytes,
              header.numChannels,
              header.frameRateHz,
              pcmEncoding,
              /* initializationData= */ null,
              /* drmInitData= */ null,
              /* selectionFlags= */ 0,
              /* language= */ null);
    }

    @Override
    public void reset(long timeUs) {
      startTimeUs = timeUs;
      pendingOutputBytes = 0;
      outputFrameCount = 0;
    }

    @Override
    public void init(int dataStartPosition, long dataEndPosition) {
      extractorOutput.seekMap(
          new WavSeekMap(header, /* framesPerBlock= */ 1, dataStartPosition, dataEndPosition));
      trackOutput.format(format);
    }

    @Override
    public boolean sampleData(ExtractorInput input, long bytesLeft)
        throws IOException, InterruptedException {
      // Write sample data until we've reached the target sample size, or the end of the data.
      while (bytesLeft > 0 && pendingOutputBytes < targetSampleSizeBytes) {
        int bytesToRead = (int) Math.min(targetSampleSizeBytes - pendingOutputBytes, bytesLeft);
        int bytesAppended = trackOutput.sampleData(input, bytesToRead, true);
        if (bytesAppended == RESULT_END_OF_INPUT) {
          bytesLeft = 0;
        } else {
          pendingOutputBytes += bytesAppended;
          bytesLeft -= bytesAppended;
        }
      }

      // Write the corresponding sample metadata. Samples must be a whole number of frames. It's
      // possible that the number of pending output bytes is not a whole number of frames if the
      // stream ended unexpectedly.
      int bytesPerFrame = header.blockSize;
      int pendingFrames = pendingOutputBytes / bytesPerFrame;
      if (pendingFrames > 0) {
        long timeUs =
            startTimeUs
                + Util.scaleLargeTimestamp(
                    outputFrameCount, C.MICROS_PER_SECOND, header.frameRateHz);
        int size = pendingFrames * bytesPerFrame;
        int offset = pendingOutputBytes - size;
        trackOutput.sampleMetadata(
            timeUs, C.BUFFER_FLAG_KEY_FRAME, size, offset, /* encryptionData= */ null);
        outputFrameCount += pendingFrames;
        pendingOutputBytes = offset;
      }

      return bytesLeft <= 0;
    }
  }

  private static final class ImaAdPcmOutputWriter implements OutputWriter {

    private static final int[] INDEX_TABLE = {
      -1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8
    };

    private static final int[] STEP_TABLE = {
      7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66,
      73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408,
      449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
      2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630,
      9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794,
      32767
    };

    private final ExtractorOutput extractorOutput;
    private final TrackOutput trackOutput;
    private final WavHeader header;

    /** Number of frames per block of the input (yet to be decoded) data. */
    private final int framesPerBlock;
    /** Target for the input (yet to be decoded) data. */
    private final byte[] inputData;
    /** Target for decoded (yet to be output) data. */
    private final ParsableByteArray decodedData;
    /** The target size of each output sample, in frames. */
    private final int targetSampleSizeFrames;
    /** The output format. */
    private final Format format;

    /** The number of pending bytes in {@link #inputData}. */
    private int pendingInputBytes;
    /** The time at which the writer was last {@link #reset}. */
    private long startTimeUs;
    /**
     * The number of bytes that have been written to {@link #trackOutput} but have yet to be
     * included as part of a sample (i.e. the corresponding call to {@link
     * TrackOutput#sampleMetadata} has yet to be made).
     */
    private int pendingOutputBytes;
    /**
     * The total number of frames in samples that have been written to the trackOutput since the
     * last call to {@link #reset}.
     */
    private long outputFrameCount;

    public ImaAdPcmOutputWriter(
        ExtractorOutput extractorOutput, TrackOutput trackOutput, WavHeader header)
        throws ParserException {
      this.extractorOutput = extractorOutput;
      this.trackOutput = trackOutput;
      this.header = header;
      targetSampleSizeFrames = Math.max(1, header.frameRateHz / TARGET_SAMPLES_PER_SECOND);

      ParsableByteArray scratch = new ParsableByteArray(header.extraData);
      scratch.readLittleEndianUnsignedShort();
      framesPerBlock = scratch.readLittleEndianUnsignedShort();

      int numChannels = header.numChannels;
      // Validate the header. This calculation is defined in "Microsoft Multimedia Standards Update
      // - New Multimedia Types and Data Techniques" (1994). See the "IMA ADPCM Wave Type" and "DVI
      // ADPCM Wave Type" sections, and the calculation of wSamplesPerBlock in the latter.
      int expectedFramesPerBlock =
          (((header.blockSize - (4 * numChannels)) * 8) / (header.bitsPerSample * numChannels)) + 1;
      if (framesPerBlock != expectedFramesPerBlock) {
        throw new ParserException(
            "Expected frames per block: " + expectedFramesPerBlock + "; got: " + framesPerBlock);
      }

      // Calculate the number of blocks we'll need to decode to obtain an output sample of the
      // target sample size, and allocate suitably sized buffers for input and decoded data.
      int maxBlocksToDecode = Util.ceilDivide(targetSampleSizeFrames, framesPerBlock);
      inputData = new byte[maxBlocksToDecode * header.blockSize];
      decodedData =
          new ParsableByteArray(
              maxBlocksToDecode * numOutputFramesToBytes(framesPerBlock, numChannels));

      // Create the format. We calculate the bitrate of the data before decoding, since this is the
      // bitrate of the stream itself.
      int bitrate = header.frameRateHz * header.blockSize * 8 / framesPerBlock;
      format =
          Format.createAudioSampleFormat(
              /* id= */ null,
              MimeTypes.AUDIO_RAW,
              /* codecs= */ null,
              bitrate,
              /* maxInputSize= */ numOutputFramesToBytes(targetSampleSizeFrames, numChannels),
              header.numChannels,
              header.frameRateHz,
              C.ENCODING_PCM_16BIT,
              /* initializationData= */ null,
              /* drmInitData= */ null,
              /* selectionFlags= */ 0,
              /* language= */ null);
    }

    @Override
    public void reset(long timeUs) {
      pendingInputBytes = 0;
      startTimeUs = timeUs;
      pendingOutputBytes = 0;
      outputFrameCount = 0;
    }

    @Override
    public void init(int dataStartPosition, long dataEndPosition) {
      extractorOutput.seekMap(
          new WavSeekMap(header, framesPerBlock, dataStartPosition, dataEndPosition));
      trackOutput.format(format);
    }

    @Override
    public boolean sampleData(ExtractorInput input, long bytesLeft)
        throws IOException, InterruptedException {
      // Calculate the number of additional frames that we need on the output side to complete a
      // sample of the target size.
      int targetFramesRemaining =
          targetSampleSizeFrames - numOutputBytesToFrames(pendingOutputBytes);
      // Calculate the whole number of blocks that we need to decode to obtain this many frames.
      int blocksToDecode = Util.ceilDivide(targetFramesRemaining, framesPerBlock);
      int targetReadBytes = blocksToDecode * header.blockSize;

      // Read input data until we've reached the target number of blocks, or the end of the data.
      boolean endOfSampleData = bytesLeft == 0;
      while (!endOfSampleData && pendingInputBytes < targetReadBytes) {
        int bytesToRead = (int) Math.min(targetReadBytes - pendingInputBytes, bytesLeft);
        int bytesAppended = input.read(inputData, pendingInputBytes, bytesToRead);
        if (bytesAppended == RESULT_END_OF_INPUT) {
          endOfSampleData = true;
        } else {
          pendingInputBytes += bytesAppended;
        }
      }

      int pendingBlockCount = pendingInputBytes / header.blockSize;
      if (pendingBlockCount > 0) {
        // We have at least one whole block to decode.
        decode(inputData, pendingBlockCount, decodedData);
        pendingInputBytes -= pendingBlockCount * header.blockSize;

        // Write all of the decoded data to the track output.
        int decodedDataSize = decodedData.limit();
        trackOutput.sampleData(decodedData, decodedDataSize);
        pendingOutputBytes += decodedDataSize;

        // Output the next sample at the target size.
        int pendingOutputFrames = numOutputBytesToFrames(pendingOutputBytes);
        if (pendingOutputFrames >= targetSampleSizeFrames) {
          writeSampleMetadata(targetSampleSizeFrames);
        }
      }

      // If we've reached the end of the data, we might need to output a final partial sample.
      if (endOfSampleData) {
        int pendingOutputFrames = numOutputBytesToFrames(pendingOutputBytes);
        if (pendingOutputFrames > 0) {
          writeSampleMetadata(pendingOutputFrames);
        }
      }

      return endOfSampleData;
    }

    private void writeSampleMetadata(int sampleFrames) {
      long timeUs =
          startTimeUs
              + Util.scaleLargeTimestamp(outputFrameCount, C.MICROS_PER_SECOND, header.frameRateHz);
      int size = numOutputFramesToBytes(sampleFrames);
      int offset = pendingOutputBytes - size;
      trackOutput.sampleMetadata(
          timeUs, C.BUFFER_FLAG_KEY_FRAME, size, offset, /* encryptionData= */ null);
      outputFrameCount += sampleFrames;
      pendingOutputBytes -= size;
    }

    /**
     * Decodes IMA ADPCM data to 16 bit PCM.
     *
     * @param input The input data to decode.
     * @param blockCount The number of blocks to decode.
     * @param output The output into which the decoded data will be written.
     */
    private void decode(byte[] input, int blockCount, ParsableByteArray output) {
      for (int blockIndex = 0; blockIndex < blockCount; blockIndex++) {
        for (int channelIndex = 0; channelIndex < header.numChannels; channelIndex++) {
          decodeBlockForChannel(input, blockIndex, channelIndex, output.data);
        }
      }
      int decodedDataSize = numOutputFramesToBytes(framesPerBlock * blockCount);
      output.reset(decodedDataSize);
    }

    private void decodeBlockForChannel(
        byte[] input, int blockIndex, int channelIndex, byte[] output) {
      int blockSize = header.blockSize;
      int numChannels = header.numChannels;

      // The input data consists for a four byte header [Ci] for each of the N channels, followed
      // by interleaved data segments [Ci-DATAj], each of which are four bytes long.
      //
      // [C1][C2]...[CN] [C1-Data0][C2-Data0]...[CN-Data0] [C1-Data1][C2-Data1]...[CN-Data1] etc
      //
      // Compute the start indices for the [Ci] and [Ci-Data0] for the current channel, as well as
      // the number of data bytes for the channel in the block.
      int blockStartIndex = blockIndex * blockSize;
      int headerStartIndex = blockStartIndex + channelIndex * 4;
      int dataStartIndex = headerStartIndex + numChannels * 4;
      int dataSizeBytes = blockSize / numChannels - 4;

      // Decode initialization. Casting to a short is necessary for the most significant bit to be
      // treated as -2^15 rather than 2^15.
      int predictedSample =
          (short) (((input[headerStartIndex + 1] & 0xFF) << 8) | (input[headerStartIndex] & 0xFF));
      int stepIndex = Math.min(input[headerStartIndex + 2] & 0xFF, 88);
      int step = STEP_TABLE[stepIndex];

      // Output the initial 16 bit PCM sample from the header.
      int outputIndex = (blockIndex * framesPerBlock * numChannels + channelIndex) * 2;
      output[outputIndex] = (byte) (predictedSample & 0xFF);
      output[outputIndex + 1] = (byte) (predictedSample >> 8);

      // We examine each data byte twice during decode.
      for (int i = 0; i < dataSizeBytes * 2; i++) {
        int dataSegmentIndex = i / 8;
        int dataSegmentOffset = (i / 2) % 4;
        int dataIndex = dataStartIndex + (dataSegmentIndex * numChannels * 4) + dataSegmentOffset;

        int originalSample = input[dataIndex] & 0xFF;
        if (i % 2 == 0) {
          originalSample &= 0x0F; // Bottom four bits.
        } else {
          originalSample >>= 4; // Top four bits.
        }

        int delta = originalSample & 0x07;
        int difference = ((2 * delta + 1) * step) >> 3;

        if ((originalSample & 0x08) != 0) {
          difference = -difference;
        }

        predictedSample += difference;
        predictedSample = Util.constrainValue(predictedSample, /* min= */ -32768, /* max= */ 32767);

        // Output the next 16 bit PCM sample to the correct position in the output.
        outputIndex += 2 * numChannels;
        output[outputIndex] = (byte) (predictedSample & 0xFF);
        output[outputIndex + 1] = (byte) (predictedSample >> 8);

        stepIndex += INDEX_TABLE[originalSample];
        stepIndex = Util.constrainValue(stepIndex, /* min= */ 0, /* max= */ STEP_TABLE.length - 1);
        step = STEP_TABLE[stepIndex];
      }
    }

    private int numOutputBytesToFrames(int bytes) {
      return bytes / (2 * header.numChannels);
    }

    private int numOutputFramesToBytes(int frames) {
      return numOutputFramesToBytes(frames, header.numChannels);
    }

    private static int numOutputFramesToBytes(int frames, int numChannels) {
      return frames * 2 * numChannels;
    }
  }
}
