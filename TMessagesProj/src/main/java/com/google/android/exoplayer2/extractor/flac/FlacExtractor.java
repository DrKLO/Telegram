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
package com.google.android.exoplayer2.extractor.flac;

import static com.google.android.exoplayer2.util.Util.castNonNull;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.FlacFrameReader;
import com.google.android.exoplayer2.extractor.FlacFrameReader.SampleNumberHolder;
import com.google.android.exoplayer2.extractor.FlacMetadataReader;
import com.google.android.exoplayer2.extractor.FlacSeekTableSeekMap;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.FlacConstants;
import com.google.android.exoplayer2.util.FlacStreamMetadata;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Extracts data from FLAC container format.
 *
 * <p>The format specification can be found at https://xiph.org/flac/format.html.
 */
public final class FlacExtractor implements Extractor {

  /** Factory for {@link FlacExtractor} instances. */
  public static final ExtractorsFactory FACTORY = () -> new Extractor[] {new FlacExtractor()};

  /**
   * Flags controlling the behavior of the extractor. Possible flag value is {@link
   * #FLAG_DISABLE_ID3_METADATA}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
      flag = true,
      value = {FLAG_DISABLE_ID3_METADATA})
  public @interface Flags {}

  /**
   * Flag to disable parsing of ID3 metadata. Can be set to save memory if ID3 metadata is not
   * required.
   */
  public static final int FLAG_DISABLE_ID3_METADATA = 1;

  /** Parser state. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    STATE_READ_ID3_METADATA,
    STATE_GET_STREAM_MARKER_AND_INFO_BLOCK_BYTES,
    STATE_READ_STREAM_MARKER,
    STATE_READ_METADATA_BLOCKS,
    STATE_GET_FRAME_START_MARKER,
    STATE_READ_FRAMES
  })
  private @interface State {}

  private static final int STATE_READ_ID3_METADATA = 0;
  private static final int STATE_GET_STREAM_MARKER_AND_INFO_BLOCK_BYTES = 1;
  private static final int STATE_READ_STREAM_MARKER = 2;
  private static final int STATE_READ_METADATA_BLOCKS = 3;
  private static final int STATE_GET_FRAME_START_MARKER = 4;
  private static final int STATE_READ_FRAMES = 5;

  /** Arbitrary buffer length of 32KB, which is ~170ms of 16-bit stereo PCM audio at 48KHz. */
  private static final int BUFFER_LENGTH = 32 * 1024;

  /** Value of an unknown sample number. */
  private static final int SAMPLE_NUMBER_UNKNOWN = -1;

  private final byte[] streamMarkerAndInfoBlock;
  private final ParsableByteArray buffer;
  private final boolean id3MetadataDisabled;

  private final SampleNumberHolder sampleNumberHolder;

  @MonotonicNonNull private ExtractorOutput extractorOutput;
  @MonotonicNonNull private TrackOutput trackOutput;

  private @State int state;
  @Nullable private Metadata id3Metadata;
  @MonotonicNonNull private FlacStreamMetadata flacStreamMetadata;
  private int minFrameSize;
  private int frameStartMarker;
  @MonotonicNonNull private FlacBinarySearchSeeker binarySearchSeeker;
  private int currentFrameBytesWritten;
  private long currentFrameFirstSampleNumber;

  /** Constructs an instance with {@code flags = 0}. */
  public FlacExtractor() {
    this(/* flags= */ 0);
  }

  /**
   * Constructs an instance.
   *
   * @param flags Flags that control the extractor's behavior. Possible flags are described by
   *     {@link Flags}.
   */
  public FlacExtractor(int flags) {
    streamMarkerAndInfoBlock =
        new byte[FlacConstants.STREAM_MARKER_SIZE + FlacConstants.STREAM_INFO_BLOCK_SIZE];
    buffer = new ParsableByteArray(new byte[BUFFER_LENGTH], /* limit= */ 0);
    id3MetadataDisabled = (flags & FLAG_DISABLE_ID3_METADATA) != 0;
    sampleNumberHolder = new SampleNumberHolder();
    state = STATE_READ_ID3_METADATA;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    FlacMetadataReader.peekId3Metadata(input, /* parseData= */ false);
    return FlacMetadataReader.checkAndPeekStreamMarker(input);
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
    trackOutput = output.track(/* id= */ 0, C.TRACK_TYPE_AUDIO);
    output.endTracks();
  }

  @Override
  public @ReadResult int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    switch (state) {
      case STATE_READ_ID3_METADATA:
        readId3Metadata(input);
        return Extractor.RESULT_CONTINUE;
      case STATE_GET_STREAM_MARKER_AND_INFO_BLOCK_BYTES:
        getStreamMarkerAndInfoBlockBytes(input);
        return Extractor.RESULT_CONTINUE;
      case STATE_READ_STREAM_MARKER:
        readStreamMarker(input);
        return Extractor.RESULT_CONTINUE;
      case STATE_READ_METADATA_BLOCKS:
        readMetadataBlocks(input);
        return Extractor.RESULT_CONTINUE;
      case STATE_GET_FRAME_START_MARKER:
        getFrameStartMarker(input);
        return Extractor.RESULT_CONTINUE;
      case STATE_READ_FRAMES:
        return readFrames(input, seekPosition);
      default:
        throw new IllegalStateException();
    }
  }

  @Override
  public void seek(long position, long timeUs) {
    if (position == 0) {
      state = STATE_READ_ID3_METADATA;
    } else if (binarySearchSeeker != null) {
      binarySearchSeeker.setSeekTargetUs(timeUs);
    }
    currentFrameFirstSampleNumber = timeUs == 0 ? 0 : SAMPLE_NUMBER_UNKNOWN;
    currentFrameBytesWritten = 0;
    buffer.reset();
  }

  @Override
  public void release() {
    // Do nothing.
  }

  // Private methods.

  private void readId3Metadata(ExtractorInput input) throws IOException, InterruptedException {
    id3Metadata = FlacMetadataReader.readId3Metadata(input, /* parseData= */ !id3MetadataDisabled);
    state = STATE_GET_STREAM_MARKER_AND_INFO_BLOCK_BYTES;
  }

  private void getStreamMarkerAndInfoBlockBytes(ExtractorInput input)
      throws IOException, InterruptedException {
    input.peekFully(streamMarkerAndInfoBlock, 0, streamMarkerAndInfoBlock.length);
    input.resetPeekPosition();
    state = STATE_READ_STREAM_MARKER;
  }

  private void readStreamMarker(ExtractorInput input) throws IOException, InterruptedException {
    FlacMetadataReader.readStreamMarker(input);
    state = STATE_READ_METADATA_BLOCKS;
  }

  private void readMetadataBlocks(ExtractorInput input) throws IOException, InterruptedException {
    boolean isLastMetadataBlock = false;
    FlacMetadataReader.FlacStreamMetadataHolder metadataHolder =
        new FlacMetadataReader.FlacStreamMetadataHolder(flacStreamMetadata);
    while (!isLastMetadataBlock) {
      isLastMetadataBlock = FlacMetadataReader.readMetadataBlock(input, metadataHolder);
      // Save the current metadata in case an exception occurs.
      flacStreamMetadata = castNonNull(metadataHolder.flacStreamMetadata);
    }

    Assertions.checkNotNull(flacStreamMetadata);
    minFrameSize = Math.max(flacStreamMetadata.minFrameSize, FlacConstants.MIN_FRAME_HEADER_SIZE);
    castNonNull(trackOutput)
        .format(flacStreamMetadata.getFormat(streamMarkerAndInfoBlock, id3Metadata));

    state = STATE_GET_FRAME_START_MARKER;
  }

  private void getFrameStartMarker(ExtractorInput input) throws IOException, InterruptedException {
    frameStartMarker = FlacMetadataReader.getFrameStartMarker(input);
    castNonNull(extractorOutput)
        .seekMap(
            getSeekMap(
                /* firstFramePosition= */ input.getPosition(),
                /* streamLength= */ input.getLength()));

    state = STATE_READ_FRAMES;
  }

  private @ReadResult int readFrames(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    Assertions.checkNotNull(trackOutput);
    Assertions.checkNotNull(flacStreamMetadata);

    // Handle pending binary search seek if necessary.
    if (binarySearchSeeker != null && binarySearchSeeker.isSeeking()) {
      return binarySearchSeeker.handlePendingSeek(input, seekPosition);
    }

    // Set current frame first sample number if it became unknown after seeking.
    if (currentFrameFirstSampleNumber == SAMPLE_NUMBER_UNKNOWN) {
      currentFrameFirstSampleNumber =
          FlacFrameReader.getFirstSampleNumber(input, flacStreamMetadata);
      return Extractor.RESULT_CONTINUE;
    }

    // Copy more bytes into the buffer.
    int currentLimit = buffer.limit();
    boolean foundEndOfInput = false;
    if (currentLimit < BUFFER_LENGTH) {
      int bytesRead =
          input.read(
              buffer.data, /* offset= */ currentLimit, /* length= */ BUFFER_LENGTH - currentLimit);
      foundEndOfInput = bytesRead == C.RESULT_END_OF_INPUT;
      if (!foundEndOfInput) {
        buffer.setLimit(currentLimit + bytesRead);
      } else if (buffer.bytesLeft() == 0) {
        outputSampleMetadata();
        return Extractor.RESULT_END_OF_INPUT;
      }
    }

    // Search for a frame.
    int positionBeforeFindingAFrame = buffer.getPosition();

    // Skip frame search on the bytes within the minimum frame size.
    if (currentFrameBytesWritten < minFrameSize) {
      buffer.skipBytes(Math.min(minFrameSize - currentFrameBytesWritten, buffer.bytesLeft()));
    }

    long nextFrameFirstSampleNumber = findFrame(buffer, foundEndOfInput);
    int numberOfFrameBytes = buffer.getPosition() - positionBeforeFindingAFrame;
    buffer.setPosition(positionBeforeFindingAFrame);
    trackOutput.sampleData(buffer, numberOfFrameBytes);
    currentFrameBytesWritten += numberOfFrameBytes;

    // Frame found.
    if (nextFrameFirstSampleNumber != SAMPLE_NUMBER_UNKNOWN) {
      outputSampleMetadata();
      currentFrameBytesWritten = 0;
      currentFrameFirstSampleNumber = nextFrameFirstSampleNumber;
    }

    if (buffer.bytesLeft() < FlacConstants.MAX_FRAME_HEADER_SIZE) {
      // The next frame header may not fit in the rest of the buffer, so put the trailing bytes at
      // the start of the buffer, and reset the position and limit.
      System.arraycopy(
          buffer.data, buffer.getPosition(), buffer.data, /* destPos= */ 0, buffer.bytesLeft());
      buffer.reset(buffer.bytesLeft());
    }

    return Extractor.RESULT_CONTINUE;
  }

  private SeekMap getSeekMap(long firstFramePosition, long streamLength) {
    Assertions.checkNotNull(flacStreamMetadata);
    if (flacStreamMetadata.seekTable != null) {
      return new FlacSeekTableSeekMap(flacStreamMetadata, firstFramePosition);
    } else if (streamLength != C.LENGTH_UNSET && flacStreamMetadata.totalSamples > 0) {
      binarySearchSeeker =
          new FlacBinarySearchSeeker(
              flacStreamMetadata, frameStartMarker, firstFramePosition, streamLength);
      return binarySearchSeeker.getSeekMap();
    } else {
      return new SeekMap.Unseekable(flacStreamMetadata.getDurationUs());
    }
  }

  /**
   * Searches for the start of a frame in {@code data}.
   *
   * <ul>
   *   <li>If the search is successful, the position is set to the start of the found frame.
   *   <li>Otherwise, the position is set to the first unsearched byte.
   * </ul>
   *
   * @param data The array to be searched.
   * @param foundEndOfInput If the end of input was met when filling in the {@code data}.
   * @return The number of the first sample in the frame found, or {@code SAMPLE_NUMBER_UNKNOWN} if
   *     the search was not successful.
   */
  private long findFrame(ParsableByteArray data, boolean foundEndOfInput) {
    Assertions.checkNotNull(flacStreamMetadata);

    int frameOffset = data.getPosition();
    while (frameOffset <= data.limit() - FlacConstants.MAX_FRAME_HEADER_SIZE) {
      data.setPosition(frameOffset);
      if (FlacFrameReader.checkAndReadFrameHeader(
          data, flacStreamMetadata, frameStartMarker, sampleNumberHolder)) {
        data.setPosition(frameOffset);
        return sampleNumberHolder.sampleNumber;
      }
      frameOffset++;
    }

    if (foundEndOfInput) {
      // Verify whether there is a frame of size < MAX_FRAME_HEADER_SIZE at the end of the stream by
      // checking at every position at a distance between MAX_FRAME_HEADER_SIZE and minFrameSize
      // from the buffer limit if it corresponds to a valid frame header.
      // At every offset, the different possibilities are:
      // 1. The current offset indicates the start of a valid frame header. In this case, consider
      //    that a frame has been found and stop searching.
      // 2. A frame starting at the current offset would be invalid. In this case, keep looking for
      //    a valid frame header.
      // 3. The current offset could be the start of a valid frame header, but there is not enough
      //    bytes remaining to complete the header. As the end of the file has been reached, this
      //    means that the current offset does not correspond to a new frame and that the last bytes
      //    of the last frame happen to be a valid partial frame header. This case can occur in two
      //    ways:
      //    3.1. An attempt to read past the buffer is made when reading the potential frame header.
      //    3.2. Reading the potential frame header does not exceed the buffer size, but exceeds the
      //         buffer limit.
      // Note that the third case is very unlikely. It never happens if the end of the input has not
      // been reached as it is always made sure that the buffer has at least MAX_FRAME_HEADER_SIZE
      // bytes available when reading a potential frame header.
      while (frameOffset <= data.limit() - minFrameSize) {
        data.setPosition(frameOffset);
        boolean frameFound;
        try {
          frameFound =
              FlacFrameReader.checkAndReadFrameHeader(
                  data, flacStreamMetadata, frameStartMarker, sampleNumberHolder);
        } catch (IndexOutOfBoundsException e) {
          // Case 3.1.
          frameFound = false;
        }
        if (data.getPosition() > data.limit()) {
          // TODO: Remove (and update above comments) once [Internal ref: b/147657250] is fixed.
          // Case 3.2.
          frameFound = false;
        }
        if (frameFound) {
          // Case 1.
          data.setPosition(frameOffset);
          return sampleNumberHolder.sampleNumber;
        }
        frameOffset++;
      }
      // The end of the frame is the end of the file.
      data.setPosition(data.limit());
    } else {
      data.setPosition(frameOffset);
    }

    return SAMPLE_NUMBER_UNKNOWN;
  }

  private void outputSampleMetadata() {
    long timeUs =
        currentFrameFirstSampleNumber
            * C.MICROS_PER_SECOND
            / castNonNull(flacStreamMetadata).sampleRate;
    castNonNull(trackOutput)
        .sampleMetadata(
            timeUs,
            C.BUFFER_FLAG_KEY_FRAME,
            currentFrameBytesWritten,
            /* offset= */ 0,
            /* encryptionData= */ null);
  }
}
