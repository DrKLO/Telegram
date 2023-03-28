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

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.util.Pair;
import androidx.annotation.IntDef;
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
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** Extracts data from WAV byte streams. */
public final class WavExtractor implements Extractor {

  private static final String TAG = "WavExtractor";

  /**
   * When outputting PCM data to a {@link TrackOutput}, we can choose how many frames are grouped
   * into each sample, and hence each sample's duration. This is the target number of samples to
   * output for each second of media, meaning that each sample will have a duration of ~100ms.
   */
  private static final int TARGET_SAMPLES_PER_SECOND = 10;

  /** Factory for {@link WavExtractor} instances. */
  public static final ExtractorsFactory FACTORY = () -> new Extractor[] {new WavExtractor()};

  /** Parser state. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    STATE_READING_FILE_TYPE,
    STATE_READING_RF64_SAMPLE_DATA_SIZE,
    STATE_READING_FORMAT,
    STATE_SKIPPING_TO_SAMPLE_DATA,
    STATE_READING_SAMPLE_DATA
  })
  private @interface State {}

  private static final int STATE_READING_FILE_TYPE = 0;
  private static final int STATE_READING_RF64_SAMPLE_DATA_SIZE = 1;
  private static final int STATE_READING_FORMAT = 2;
  private static final int STATE_SKIPPING_TO_SAMPLE_DATA = 3;
  private static final int STATE_READING_SAMPLE_DATA = 4;

  private @MonotonicNonNull ExtractorOutput extractorOutput;
  private @MonotonicNonNull TrackOutput trackOutput;
  private @State int state;
  private long rf64SampleDataSize;
  private @MonotonicNonNull OutputWriter outputWriter;
  private int dataStartPosition;
  private long dataEndPosition;

  public WavExtractor() {
    state = STATE_READING_FILE_TYPE;
    rf64SampleDataSize = C.LENGTH_UNSET;
    dataStartPosition = C.POSITION_UNSET;
    dataEndPosition = C.POSITION_UNSET;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    return WavHeaderReader.checkFileType(input);
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
    trackOutput = output.track(0, C.TRACK_TYPE_AUDIO);
    output.endTracks();
  }

  @Override
  public void seek(long position, long timeUs) {
    state = position == 0 ? STATE_READING_FILE_TYPE : STATE_READING_SAMPLE_DATA;
    if (outputWriter != null) {
      outputWriter.reset(timeUs);
    }
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public @ReadResult int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException {
    assertInitialized();
    switch (state) {
      case STATE_READING_FILE_TYPE:
        readFileType(input);
        return Extractor.RESULT_CONTINUE;
      case STATE_READING_RF64_SAMPLE_DATA_SIZE:
        readRf64SampleDataSize(input);
        return Extractor.RESULT_CONTINUE;
      case STATE_READING_FORMAT:
        readFormat(input);
        return Extractor.RESULT_CONTINUE;
      case STATE_SKIPPING_TO_SAMPLE_DATA:
        skipToSampleData(input);
        return Extractor.RESULT_CONTINUE;
      case STATE_READING_SAMPLE_DATA:
        return readSampleData(input);
      default:
        throw new IllegalStateException();
    }
  }

  @EnsuresNonNull({"extractorOutput", "trackOutput"})
  private void assertInitialized() {
    Assertions.checkStateNotNull(trackOutput);
    Util.castNonNull(extractorOutput);
  }

  private void readFileType(ExtractorInput input) throws IOException {
    Assertions.checkState(input.getPosition() == 0);
    if (dataStartPosition != C.POSITION_UNSET) {
      input.skipFully(dataStartPosition);
      state = STATE_READING_SAMPLE_DATA;
      return;
    }
    if (!WavHeaderReader.checkFileType(input)) {
      // Should only happen if the media wasn't sniffed.
      throw ParserException.createForMalformedContainer(
          "Unsupported or unrecognized wav file type.", /* cause= */ null);
    }
    input.skipFully((int) (input.getPeekPosition() - input.getPosition()));
    state = STATE_READING_RF64_SAMPLE_DATA_SIZE;
  }

  private void readRf64SampleDataSize(ExtractorInput input) throws IOException {
    rf64SampleDataSize = WavHeaderReader.readRf64SampleDataSize(input);
    state = STATE_READING_FORMAT;
  }

  @RequiresNonNull({"extractorOutput", "trackOutput"})
  private void readFormat(ExtractorInput input) throws IOException {
    WavFormat wavFormat = WavHeaderReader.readFormat(input);
    if (wavFormat.formatType == WavUtil.TYPE_IMA_ADPCM) {
      outputWriter = new ImaAdPcmOutputWriter(extractorOutput, trackOutput, wavFormat);
    } else if (wavFormat.formatType == WavUtil.TYPE_ALAW) {
      outputWriter =
          new PassthroughOutputWriter(
              extractorOutput,
              trackOutput,
              wavFormat,
              MimeTypes.AUDIO_ALAW,
              /* pcmEncoding= */ Format.NO_VALUE);
    } else if (wavFormat.formatType == WavUtil.TYPE_MLAW) {
      outputWriter =
          new PassthroughOutputWriter(
              extractorOutput,
              trackOutput,
              wavFormat,
              MimeTypes.AUDIO_MLAW,
              /* pcmEncoding= */ Format.NO_VALUE);
    } else {
      @C.PcmEncoding
      int pcmEncoding =
          WavUtil.getPcmEncodingForType(wavFormat.formatType, wavFormat.bitsPerSample);
      if (pcmEncoding == C.ENCODING_INVALID) {
        throw ParserException.createForUnsupportedContainerFeature(
            "Unsupported WAV format type: " + wavFormat.formatType);
      }
      outputWriter =
          new PassthroughOutputWriter(
              extractorOutput, trackOutput, wavFormat, MimeTypes.AUDIO_RAW, pcmEncoding);
    }
    state = STATE_SKIPPING_TO_SAMPLE_DATA;
  }

  private void skipToSampleData(ExtractorInput input) throws IOException {
    Pair<Long, Long> dataBounds = WavHeaderReader.skipToSampleData(input);
    dataStartPosition = dataBounds.first.intValue();
    long dataSize = dataBounds.second;
    if (rf64SampleDataSize != C.LENGTH_UNSET && dataSize == 0xFFFFFFFFL) {
      // Following EBU - Tech 3306-2007, the data size indicated in the ds64 chunk should only be
      // used if the size of the data chunk is unset.
      dataSize = rf64SampleDataSize;
    }
    dataEndPosition = dataStartPosition + dataSize;
    long inputLength = input.getLength();
    if (inputLength != C.LENGTH_UNSET && dataEndPosition > inputLength) {
      Log.w(TAG, "Data exceeds input length: " + dataEndPosition + ", " + inputLength);
      dataEndPosition = inputLength;
    }
    Assertions.checkNotNull(outputWriter).init(dataStartPosition, dataEndPosition);
    state = STATE_READING_SAMPLE_DATA;
  }

  private @ReadResult int readSampleData(ExtractorInput input) throws IOException {
    Assertions.checkState(dataEndPosition != C.POSITION_UNSET);
    long bytesLeft = dataEndPosition - input.getPosition();
    return Assertions.checkNotNull(outputWriter).sampleData(input, bytesLeft)
        ? RESULT_END_OF_INPUT
        : RESULT_CONTINUE;
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
     */
    boolean sampleData(ExtractorInput input, long bytesLeft) throws IOException;
  }

  private static final class PassthroughOutputWriter implements OutputWriter {

    private final ExtractorOutput extractorOutput;
    private final TrackOutput trackOutput;
    private final WavFormat wavFormat;
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
        WavFormat wavFormat,
        String mimeType,
        @C.PcmEncoding int pcmEncoding)
        throws ParserException {
      this.extractorOutput = extractorOutput;
      this.trackOutput = trackOutput;
      this.wavFormat = wavFormat;

      int bytesPerFrame = wavFormat.numChannels * wavFormat.bitsPerSample / 8;
      // Validate the WAV format. Blocks are expected to correspond to single frames.
      if (wavFormat.blockSize != bytesPerFrame) {
        throw ParserException.createForMalformedContainer(
            "Expected block size: " + bytesPerFrame + "; got: " + wavFormat.blockSize,
            /* cause= */ null);
      }

      int constantBitrate = wavFormat.frameRateHz * bytesPerFrame * 8;
      targetSampleSizeBytes =
          max(bytesPerFrame, wavFormat.frameRateHz * bytesPerFrame / TARGET_SAMPLES_PER_SECOND);
      format =
          new Format.Builder()
              .setSampleMimeType(mimeType)
              .setAverageBitrate(constantBitrate)
              .setPeakBitrate(constantBitrate)
              .setMaxInputSize(targetSampleSizeBytes)
              .setChannelCount(wavFormat.numChannels)
              .setSampleRate(wavFormat.frameRateHz)
              .setPcmEncoding(pcmEncoding)
              .build();
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
          new WavSeekMap(wavFormat, /* framesPerBlock= */ 1, dataStartPosition, dataEndPosition));
      trackOutput.format(format);
    }

    @Override
    public boolean sampleData(ExtractorInput input, long bytesLeft) throws IOException {
      // Write sample data until we've reached the target sample size, or the end of the data.
      while (bytesLeft > 0 && pendingOutputBytes < targetSampleSizeBytes) {
        int bytesToRead = (int) min(targetSampleSizeBytes - pendingOutputBytes, bytesLeft);
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
      int bytesPerFrame = wavFormat.blockSize;
      int pendingFrames = pendingOutputBytes / bytesPerFrame;
      if (pendingFrames > 0) {
        long timeUs =
            startTimeUs
                + Util.scaleLargeTimestamp(
                    outputFrameCount, C.MICROS_PER_SECOND, wavFormat.frameRateHz);
        int size = pendingFrames * bytesPerFrame;
        int offset = pendingOutputBytes - size;
        trackOutput.sampleMetadata(
            timeUs, C.BUFFER_FLAG_KEY_FRAME, size, offset, /* cryptoData= */ null);
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
    private final WavFormat wavFormat;

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
        ExtractorOutput extractorOutput, TrackOutput trackOutput, WavFormat wavFormat)
        throws ParserException {
      this.extractorOutput = extractorOutput;
      this.trackOutput = trackOutput;
      this.wavFormat = wavFormat;
      targetSampleSizeFrames = max(1, wavFormat.frameRateHz / TARGET_SAMPLES_PER_SECOND);

      ParsableByteArray scratch = new ParsableByteArray(wavFormat.extraData);
      scratch.readLittleEndianUnsignedShort();
      framesPerBlock = scratch.readLittleEndianUnsignedShort();

      int numChannels = wavFormat.numChannels;
      // Validate the WAV format. This calculation is defined in "Microsoft Multimedia Standards
      // Update
      // - New Multimedia Types and Data Techniques" (1994). See the "IMA ADPCM Wave Type" and "DVI
      // ADPCM Wave Type" sections, and the calculation of wSamplesPerBlock in the latter.
      int expectedFramesPerBlock =
          (((wavFormat.blockSize - (4 * numChannels)) * 8)
                  / (wavFormat.bitsPerSample * numChannels))
              + 1;
      if (framesPerBlock != expectedFramesPerBlock) {
        throw ParserException.createForMalformedContainer(
            "Expected frames per block: " + expectedFramesPerBlock + "; got: " + framesPerBlock,
            /* cause= */ null);
      }

      // Calculate the number of blocks we'll need to decode to obtain an output sample of the
      // target sample size, and allocate suitably sized buffers for input and decoded data.
      int maxBlocksToDecode = Util.ceilDivide(targetSampleSizeFrames, framesPerBlock);
      inputData = new byte[maxBlocksToDecode * wavFormat.blockSize];
      decodedData =
          new ParsableByteArray(
              maxBlocksToDecode * numOutputFramesToBytes(framesPerBlock, numChannels));

      // Create the format. We calculate the bitrate of the data before decoding, since this is the
      // bitrate of the stream itself.
      int constantBitrate = wavFormat.frameRateHz * wavFormat.blockSize * 8 / framesPerBlock;
      format =
          new Format.Builder()
              .setSampleMimeType(MimeTypes.AUDIO_RAW)
              .setAverageBitrate(constantBitrate)
              .setPeakBitrate(constantBitrate)
              .setMaxInputSize(numOutputFramesToBytes(targetSampleSizeFrames, numChannels))
              .setChannelCount(wavFormat.numChannels)
              .setSampleRate(wavFormat.frameRateHz)
              .setPcmEncoding(C.ENCODING_PCM_16BIT)
              .build();
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
          new WavSeekMap(wavFormat, framesPerBlock, dataStartPosition, dataEndPosition));
      trackOutput.format(format);
    }

    @Override
    public boolean sampleData(ExtractorInput input, long bytesLeft) throws IOException {
      // Calculate the number of additional frames that we need on the output side to complete a
      // sample of the target size.
      int targetFramesRemaining =
          targetSampleSizeFrames - numOutputBytesToFrames(pendingOutputBytes);
      // Calculate the whole number of blocks that we need to decode to obtain this many frames.
      int blocksToDecode = Util.ceilDivide(targetFramesRemaining, framesPerBlock);
      int targetReadBytes = blocksToDecode * wavFormat.blockSize;

      // Read input data until we've reached the target number of blocks, or the end of the data.
      boolean endOfSampleData = bytesLeft == 0;
      while (!endOfSampleData && pendingInputBytes < targetReadBytes) {
        int bytesToRead = (int) min(targetReadBytes - pendingInputBytes, bytesLeft);
        int bytesAppended = input.read(inputData, pendingInputBytes, bytesToRead);
        if (bytesAppended == RESULT_END_OF_INPUT) {
          endOfSampleData = true;
        } else {
          pendingInputBytes += bytesAppended;
        }
      }

      int pendingBlockCount = pendingInputBytes / wavFormat.blockSize;
      if (pendingBlockCount > 0) {
        // We have at least one whole block to decode.
        decode(inputData, pendingBlockCount, decodedData);
        pendingInputBytes -= pendingBlockCount * wavFormat.blockSize;

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
              + Util.scaleLargeTimestamp(
                  outputFrameCount, C.MICROS_PER_SECOND, wavFormat.frameRateHz);
      int size = numOutputFramesToBytes(sampleFrames);
      int offset = pendingOutputBytes - size;
      trackOutput.sampleMetadata(
          timeUs, C.BUFFER_FLAG_KEY_FRAME, size, offset, /* cryptoData= */ null);
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
        for (int channelIndex = 0; channelIndex < wavFormat.numChannels; channelIndex++) {
          decodeBlockForChannel(input, blockIndex, channelIndex, output.getData());
        }
      }
      int decodedDataSize = numOutputFramesToBytes(framesPerBlock * blockCount);
      output.setPosition(0);
      output.setLimit(decodedDataSize);
    }

    private void decodeBlockForChannel(
        byte[] input, int blockIndex, int channelIndex, byte[] output) {
      int blockSize = wavFormat.blockSize;
      int numChannels = wavFormat.numChannels;

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
      int stepIndex = min(input[headerStartIndex + 2] & 0xFF, 88);
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
      return bytes / (2 * wavFormat.numChannels);
    }

    private int numOutputFramesToBytes(int frames) {
      return numOutputFramesToBytes(frames, wavFormat.numChannels);
    }

    private static int numOutputFramesToBytes(int frames, int numChannels) {
      return frames * 2 * numChannels;
    }
  }
}
