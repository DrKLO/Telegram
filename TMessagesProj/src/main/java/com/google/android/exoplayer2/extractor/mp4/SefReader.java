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
package com.google.android.exoplayer2.extractor.mp4;

import static com.google.android.exoplayer2.extractor.Extractor.RESULT_SEEK;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.mp4.SlowMotionData;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.common.base.Splitter;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads Samsung Extension Format (SEF) metadata.
 *
 * <p>To be used in conjunction with {@link Mp4Extractor}.
 */
/* package */ final class SefReader {

  /** Reader states. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    STATE_SHOULD_CHECK_FOR_SEF,
    STATE_CHECKING_FOR_SEF,
    STATE_READING_SDRS,
    STATE_READING_SEF_DATA
  })
  private @interface State {}

  private static final int STATE_SHOULD_CHECK_FOR_SEF = 0;
  private static final int STATE_CHECKING_FOR_SEF = 1;
  private static final int STATE_READING_SDRS = 2;
  private static final int STATE_READING_SEF_DATA = 3;

  /** Supported data types. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    TYPE_SLOW_MOTION_DATA,
    TYPE_SUPER_SLOW_MOTION_DATA,
    TYPE_SUPER_SLOW_MOTION_BGM,
    TYPE_SUPER_SLOW_MOTION_EDIT_DATA,
    TYPE_SUPER_SLOW_DEFLICKERING_ON
  })
  private @interface DataType {}

  private static final int TYPE_SLOW_MOTION_DATA = 0x0890; // 2192
  private static final int TYPE_SUPER_SLOW_MOTION_DATA = 0x0b00; // 2816
  private static final int TYPE_SUPER_SLOW_MOTION_BGM = 0x0b01; // 2817
  private static final int TYPE_SUPER_SLOW_MOTION_EDIT_DATA = 0x0b03; // 2819
  private static final int TYPE_SUPER_SLOW_DEFLICKERING_ON = 0x0b04; // 2820

  private static final String TAG = "SefReader";

  /**
   * Hex representation of `SEFT` (in ASCII).
   *
   * <p>This is the last 4 bytes of a file that has Samsung Extension Format (SEF) data.
   */
  private static final int SAMSUNG_TAIL_SIGNATURE = 0x53454654;
  /** Start signature (4 bytes), SEF version (4 bytes), SDR count (4 bytes). */
  private static final int TAIL_HEADER_LENGTH = 12;
  /** Tail offset (4 bytes), tail signature (4 bytes). */
  private static final int TAIL_FOOTER_LENGTH = 8;

  private static final int LENGTH_OF_ONE_SDR = 12;
  private static final Splitter COLON_SPLITTER = Splitter.on(':');
  private static final Splitter ASTERISK_SPLITTER = Splitter.on('*');

  private final List<DataReference> dataReferences;
  private @State int readerState;
  private int tailLength;

  public SefReader() {
    dataReferences = new ArrayList<>();
    readerState = STATE_SHOULD_CHECK_FOR_SEF;
  }

  public void reset() {
    dataReferences.clear();
    readerState = STATE_SHOULD_CHECK_FOR_SEF;
  }

  public @Extractor.ReadResult int read(
      ExtractorInput input,
      PositionHolder seekPosition,
      List<Metadata.Entry> slowMotionMetadataEntries)
      throws IOException {
    switch (readerState) {
      case STATE_SHOULD_CHECK_FOR_SEF:
        long inputLength = input.getLength();
        seekPosition.position =
            inputLength == C.LENGTH_UNSET || inputLength < TAIL_FOOTER_LENGTH
                ? 0
                : inputLength - TAIL_FOOTER_LENGTH;
        readerState = STATE_CHECKING_FOR_SEF;
        break;
      case STATE_CHECKING_FOR_SEF:
        checkForSefData(input, seekPosition);
        break;
      case STATE_READING_SDRS:
        readSdrs(input, seekPosition);
        break;
      case STATE_READING_SEF_DATA:
        readSefData(input, slowMotionMetadataEntries);
        seekPosition.position = 0;
        break;
      default:
        throw new IllegalStateException();
    }
    return RESULT_SEEK;
  }

  private void checkForSefData(ExtractorInput input, PositionHolder seekPosition)
      throws IOException {
    ParsableByteArray scratch = new ParsableByteArray(/* limit= */ TAIL_FOOTER_LENGTH);
    input.readFully(scratch.getData(), /* offset= */ 0, /* length= */ TAIL_FOOTER_LENGTH);
    tailLength = scratch.readLittleEndianInt() + TAIL_FOOTER_LENGTH;
    if (scratch.readInt() != SAMSUNG_TAIL_SIGNATURE) {
      seekPosition.position = 0;
      return;
    }

    // input.getPosition is at the very end of the tail, so jump forward by tailLength, but
    // account for the tail header, which needs to be ignored.
    seekPosition.position = input.getPosition() - (tailLength - TAIL_HEADER_LENGTH);
    readerState = STATE_READING_SDRS;
  }

  private void readSdrs(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    long streamLength = input.getLength();
    int sdrsLength = tailLength - TAIL_HEADER_LENGTH - TAIL_FOOTER_LENGTH;
    ParsableByteArray scratch = new ParsableByteArray(/* limit= */ sdrsLength);
    input.readFully(scratch.getData(), /* offset= */ 0, /* length= */ sdrsLength);

    for (int i = 0; i < sdrsLength / LENGTH_OF_ONE_SDR; i++) {
      scratch.skipBytes(2); // SDR data sub info flag and reserved bits (2).
      @DataType int dataType = scratch.readLittleEndianShort();
      switch (dataType) {
        case TYPE_SLOW_MOTION_DATA:
        case TYPE_SUPER_SLOW_MOTION_DATA:
        case TYPE_SUPER_SLOW_MOTION_BGM:
        case TYPE_SUPER_SLOW_MOTION_EDIT_DATA:
        case TYPE_SUPER_SLOW_DEFLICKERING_ON:
          // The read int is the distance from the tail info to the start of the metadata.
          // Calculated as an offset from the start by working backwards.
          long startOffset = streamLength - tailLength - scratch.readLittleEndianInt();
          int size = scratch.readLittleEndianInt();
          dataReferences.add(new DataReference(dataType, startOffset, size));
          break;
        default:
          scratch.skipBytes(8); // startPosition (4), size (4).
      }
    }

    if (dataReferences.isEmpty()) {
      seekPosition.position = 0;
      return;
    }

    readerState = STATE_READING_SEF_DATA;
    seekPosition.position = dataReferences.get(0).startOffset;
  }

  private void readSefData(ExtractorInput input, List<Metadata.Entry> slowMotionMetadataEntries)
      throws IOException {
    long dataStartOffset = input.getPosition();
    int totalDataLength = (int) (input.getLength() - input.getPosition() - tailLength);
    ParsableByteArray data = new ParsableByteArray(/* limit= */ totalDataLength);
    input.readFully(data.getData(), 0, totalDataLength);

    for (int i = 0; i < dataReferences.size(); i++) {
      DataReference dataReference = dataReferences.get(i);
      int intendedPosition = (int) (dataReference.startOffset - dataStartOffset);
      data.setPosition(intendedPosition);

      // The data type is derived from the name because the SEF format has inconsistent data type
      // values.
      data.skipBytes(4); // data type (2), data sub info (2).
      int nameLength = data.readLittleEndianInt();
      String name = data.readString(nameLength);
      @DataType int dataType = nameToDataType(name);

      int remainingDataLength = dataReference.size - (8 + nameLength);
      switch (dataType) {
        case TYPE_SLOW_MOTION_DATA:
          slowMotionMetadataEntries.add(readSlowMotionData(data, remainingDataLength));
          break;
        case TYPE_SUPER_SLOW_MOTION_DATA:
        case TYPE_SUPER_SLOW_MOTION_BGM:
        case TYPE_SUPER_SLOW_MOTION_EDIT_DATA:
        case TYPE_SUPER_SLOW_DEFLICKERING_ON:
          break;
        default:
          throw new IllegalStateException();
      }
    }
  }

  private static SlowMotionData readSlowMotionData(ParsableByteArray data, int dataLength)
      throws ParserException {
    List<SlowMotionData.Segment> segments = new ArrayList<>();
    String dataString = data.readString(dataLength);
    List<String> segmentStrings = ASTERISK_SPLITTER.splitToList(dataString);
    for (int i = 0; i < segmentStrings.size(); i++) {
      List<String> values = COLON_SPLITTER.splitToList(segmentStrings.get(i));
      if (values.size() != 3) {
        throw ParserException.createForMalformedContainer(/* message= */ null, /* cause= */ null);
      }
      try {
        long startTimeMs = Long.parseLong(values.get(0));
        long endTimeMs = Long.parseLong(values.get(1));
        int speedMode = Integer.parseInt(values.get(2));
        int speedDivisor = 1 << (speedMode - 1);
        segments.add(new SlowMotionData.Segment(startTimeMs, endTimeMs, speedDivisor));
      } catch (NumberFormatException e) {
        throw ParserException.createForMalformedContainer(/* message= */ null, /* cause= */ e);
      }
    }
    return new SlowMotionData(segments);
  }

  private static @DataType int nameToDataType(String name) throws ParserException {
    switch (name) {
      case "SlowMotion_Data":
        return TYPE_SLOW_MOTION_DATA;
      case "Super_SlowMotion_Data":
        return TYPE_SUPER_SLOW_MOTION_DATA;
      case "Super_SlowMotion_BGM":
        return TYPE_SUPER_SLOW_MOTION_BGM;
      case "Super_SlowMotion_Edit_Data":
        return TYPE_SUPER_SLOW_MOTION_EDIT_DATA;
      case "Super_SlowMotion_Deflickering_On":
        return TYPE_SUPER_SLOW_DEFLICKERING_ON;
      default:
        throw ParserException.createForMalformedContainer("Invalid SEF name", /* cause= */ null);
    }
  }

  private static final class DataReference {
    public final @DataType int dataType;
    public final long startOffset;
    public final int size;

    public DataReference(@DataType int dataType, long startOffset, int size) {
      this.dataType = dataType;
      this.startOffset = startOffset;
      this.size = size;
    }
  }
}
