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
package com.google.android.exoplayer2.extractor.flv;

import static java.lang.Math.max;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.IndexSeekMap;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** Extracts data from the FLV container format. */
public final class FlvExtractor implements Extractor {

  /** Factory for {@link FlvExtractor} instances. */
  public static final ExtractorsFactory FACTORY = () -> new Extractor[] {new FlvExtractor()};

  /** Extractor states. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    STATE_READING_FLV_HEADER,
    STATE_SKIPPING_TO_TAG_HEADER,
    STATE_READING_TAG_HEADER,
    STATE_READING_TAG_DATA
  })
  private @interface States {}

  private static final int STATE_READING_FLV_HEADER = 1;
  private static final int STATE_SKIPPING_TO_TAG_HEADER = 2;
  private static final int STATE_READING_TAG_HEADER = 3;
  private static final int STATE_READING_TAG_DATA = 4;

  // Header sizes.
  private static final int FLV_HEADER_SIZE = 9;
  private static final int FLV_TAG_HEADER_SIZE = 11;

  // Tag types.
  private static final int TAG_TYPE_AUDIO = 8;
  private static final int TAG_TYPE_VIDEO = 9;
  private static final int TAG_TYPE_SCRIPT_DATA = 18;

  // FLV container identifier.
  private static final int FLV_TAG = 0x00464c56;

  private final ParsableByteArray scratch;
  private final ParsableByteArray headerBuffer;
  private final ParsableByteArray tagHeaderBuffer;
  private final ParsableByteArray tagData;
  private final ScriptTagPayloadReader metadataReader;

  private @MonotonicNonNull ExtractorOutput extractorOutput;
  private @States int state;
  private boolean outputFirstSample;
  private long mediaTagTimestampOffsetUs;
  private int bytesToNextTagHeader;
  private int tagType;
  private int tagDataSize;
  private long tagTimestampUs;
  private boolean outputSeekMap;
  private @MonotonicNonNull AudioTagPayloadReader audioReader;
  private @MonotonicNonNull VideoTagPayloadReader videoReader;

  public FlvExtractor() {
    scratch = new ParsableByteArray(4);
    headerBuffer = new ParsableByteArray(FLV_HEADER_SIZE);
    tagHeaderBuffer = new ParsableByteArray(FLV_TAG_HEADER_SIZE);
    tagData = new ParsableByteArray();
    metadataReader = new ScriptTagPayloadReader();
    state = STATE_READING_FLV_HEADER;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    // Check if file starts with "FLV" tag
    input.peekFully(scratch.getData(), 0, 3);
    scratch.setPosition(0);
    if (scratch.readUnsignedInt24() != FLV_TAG) {
      return false;
    }

    // Checking reserved flags are set to 0
    input.peekFully(scratch.getData(), 0, 2);
    scratch.setPosition(0);
    if ((scratch.readUnsignedShort() & 0xFA) != 0) {
      return false;
    }

    // Read data offset
    input.peekFully(scratch.getData(), 0, 4);
    scratch.setPosition(0);
    int dataOffset = scratch.readInt();

    input.resetPeekPosition();
    input.advancePeekPosition(dataOffset);

    // Checking first "previous tag size" is set to 0
    input.peekFully(scratch.getData(), 0, 4);
    scratch.setPosition(0);

    return scratch.readInt() == 0;
  }

  @Override
  public void init(ExtractorOutput output) {
    this.extractorOutput = output;
  }

  @Override
  public void seek(long position, long timeUs) {
    if (position == 0) {
      state = STATE_READING_FLV_HEADER;
      outputFirstSample = false;
    } else {
      state = STATE_READING_TAG_HEADER;
    }
    bytesToNextTagHeader = 0;
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    Assertions.checkStateNotNull(extractorOutput); // Asserts that init has been called.
    while (true) {
      switch (state) {
        case STATE_READING_FLV_HEADER:
          if (!readFlvHeader(input)) {
            return RESULT_END_OF_INPUT;
          }
          break;
        case STATE_SKIPPING_TO_TAG_HEADER:
          skipToTagHeader(input);
          break;
        case STATE_READING_TAG_HEADER:
          if (!readTagHeader(input)) {
            return RESULT_END_OF_INPUT;
          }
          break;
        case STATE_READING_TAG_DATA:
          if (readTagData(input)) {
            return RESULT_CONTINUE;
          }
          break;
        default:
          // Never happens.
          throw new IllegalStateException();
      }
    }
  }

  /**
   * Reads an FLV container header from the provided {@link ExtractorInput}.
   *
   * @param input The {@link ExtractorInput} from which to read.
   * @return True if header was read successfully. False if the end of stream was reached.
   * @throws IOException If an error occurred reading or parsing data from the source.
   */
  @RequiresNonNull("extractorOutput")
  private boolean readFlvHeader(ExtractorInput input) throws IOException {
    if (!input.readFully(headerBuffer.getData(), 0, FLV_HEADER_SIZE, true)) {
      // We've reached the end of the stream.
      return false;
    }

    headerBuffer.setPosition(0);
    headerBuffer.skipBytes(4);
    int flags = headerBuffer.readUnsignedByte();
    boolean hasAudio = (flags & 0x04) != 0;
    boolean hasVideo = (flags & 0x01) != 0;
    if (hasAudio && audioReader == null) {
      audioReader =
          new AudioTagPayloadReader(extractorOutput.track(TAG_TYPE_AUDIO, C.TRACK_TYPE_AUDIO));
    }
    if (hasVideo && videoReader == null) {
      videoReader =
          new VideoTagPayloadReader(extractorOutput.track(TAG_TYPE_VIDEO, C.TRACK_TYPE_VIDEO));
    }
    extractorOutput.endTracks();

    // We need to skip any additional content in the FLV header, plus the 4 byte previous tag size.
    bytesToNextTagHeader = headerBuffer.readInt() - FLV_HEADER_SIZE + 4;
    state = STATE_SKIPPING_TO_TAG_HEADER;
    return true;
  }

  /**
   * Skips over data to reach the next tag header.
   *
   * @param input The {@link ExtractorInput} from which to read.
   * @throws IOException If an error occurred skipping data from the source.
   */
  private void skipToTagHeader(ExtractorInput input) throws IOException {
    input.skipFully(bytesToNextTagHeader);
    bytesToNextTagHeader = 0;
    state = STATE_READING_TAG_HEADER;
  }

  /**
   * Reads a tag header from the provided {@link ExtractorInput}.
   *
   * @param input The {@link ExtractorInput} from which to read.
   * @return True if tag header was read successfully. Otherwise, false.
   * @throws IOException If an error occurred reading or parsing data from the source.
   */
  private boolean readTagHeader(ExtractorInput input) throws IOException {
    if (!input.readFully(tagHeaderBuffer.getData(), 0, FLV_TAG_HEADER_SIZE, true)) {
      // We've reached the end of the stream.
      return false;
    }

    tagHeaderBuffer.setPosition(0);
    tagType = tagHeaderBuffer.readUnsignedByte();
    tagDataSize = tagHeaderBuffer.readUnsignedInt24();
    tagTimestampUs = tagHeaderBuffer.readUnsignedInt24();
    tagTimestampUs = ((tagHeaderBuffer.readUnsignedByte() << 24) | tagTimestampUs) * 1000L;
    tagHeaderBuffer.skipBytes(3); // streamId
    state = STATE_READING_TAG_DATA;
    return true;
  }

  /**
   * Reads the body of a tag from the provided {@link ExtractorInput}.
   *
   * @param input The {@link ExtractorInput} from which to read.
   * @return True if the data was consumed by a reader. False if it was skipped.
   * @throws IOException If an error occurred reading or parsing data from the source.
   */
  @RequiresNonNull("extractorOutput")
  private boolean readTagData(ExtractorInput input) throws IOException {
    boolean wasConsumed = true;
    boolean wasSampleOutput = false;
    long timestampUs = getCurrentTimestampUs();
    if (tagType == TAG_TYPE_AUDIO && audioReader != null) {
      ensureReadyForMediaOutput();
      wasSampleOutput = audioReader.consume(prepareTagData(input), timestampUs);
    } else if (tagType == TAG_TYPE_VIDEO && videoReader != null) {
      ensureReadyForMediaOutput();
      wasSampleOutput = videoReader.consume(prepareTagData(input), timestampUs);
    } else if (tagType == TAG_TYPE_SCRIPT_DATA && !outputSeekMap) {
      wasSampleOutput = metadataReader.consume(prepareTagData(input), timestampUs);
      long durationUs = metadataReader.getDurationUs();
      if (durationUs != C.TIME_UNSET) {
        extractorOutput.seekMap(
            new IndexSeekMap(
                metadataReader.getKeyFrameTagPositions(),
                metadataReader.getKeyFrameTimesUs(),
                durationUs));
        outputSeekMap = true;
      }
    } else {
      input.skipFully(tagDataSize);
      wasConsumed = false;
    }
    if (!outputFirstSample && wasSampleOutput) {
      outputFirstSample = true;
      mediaTagTimestampOffsetUs =
          metadataReader.getDurationUs() == C.TIME_UNSET ? -tagTimestampUs : 0;
    }
    bytesToNextTagHeader = 4; // There's a 4 byte previous tag size before the next header.
    state = STATE_SKIPPING_TO_TAG_HEADER;
    return wasConsumed;
  }

  private ParsableByteArray prepareTagData(ExtractorInput input) throws IOException {
    if (tagDataSize > tagData.capacity()) {
      tagData.reset(new byte[max(tagData.capacity() * 2, tagDataSize)], 0);
    } else {
      tagData.setPosition(0);
    }
    tagData.setLimit(tagDataSize);
    input.readFully(tagData.getData(), 0, tagDataSize);
    return tagData;
  }

  @RequiresNonNull("extractorOutput")
  private void ensureReadyForMediaOutput() {
    if (!outputSeekMap) {
      extractorOutput.seekMap(new SeekMap.Unseekable(C.TIME_UNSET));
      outputSeekMap = true;
    }
  }

  private long getCurrentTimestampUs() {
    return outputFirstSample
        ? (mediaTagTimestampOffsetUs + tagTimestampUs)
        : (metadataReader.getDurationUs() == C.TIME_UNSET ? 0 : tagTimestampUs);
  }
}
