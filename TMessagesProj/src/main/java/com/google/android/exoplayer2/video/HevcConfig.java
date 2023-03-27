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
package com.google.android.exoplayer2.video;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.util.CodecSpecificDataUtil;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.Collections;
import java.util.List;

/** HEVC configuration data. */
public final class HevcConfig {

  /**
   * Parses HEVC configuration data.
   *
   * @param data A {@link ParsableByteArray}, whose position is set to the start of the HEVC
   *     configuration data to parse.
   * @return A parsed representation of the HEVC configuration data.
   * @throws ParserException If an error occurred parsing the data.
   */
  public static HevcConfig parse(ParsableByteArray data) throws ParserException {
    try {
      data.skipBytes(21); // Skip to the NAL unit length size field.
      int lengthSizeMinusOne = data.readUnsignedByte() & 0x03;

      // Calculate the combined size of all VPS/SPS/PPS bitstreams.
      int numberOfArrays = data.readUnsignedByte();
      int csdLength = 0;
      int csdStartPosition = data.getPosition();
      for (int i = 0; i < numberOfArrays; i++) {
        data.skipBytes(1); // completeness (1), reserved (1), nal_unit_type (6)
        int numberOfNalUnits = data.readUnsignedShort();
        for (int j = 0; j < numberOfNalUnits; j++) {
          int nalUnitLength = data.readUnsignedShort();
          csdLength += 4 + nalUnitLength; // Start code and NAL unit.
          data.skipBytes(nalUnitLength);
        }
      }

      // Concatenate the codec-specific data into a single buffer.
      data.setPosition(csdStartPosition);
      byte[] buffer = new byte[csdLength];
      int bufferPosition = 0;
      int width = Format.NO_VALUE;
      int height = Format.NO_VALUE;
      float pixelWidthHeightRatio = 1;
      @Nullable String codecs = null;
      for (int i = 0; i < numberOfArrays; i++) {
        int nalUnitType =
            data.readUnsignedByte() & 0x3F; // completeness (1), reserved (1), nal_unit_type (6)
        int numberOfNalUnits = data.readUnsignedShort();
        for (int j = 0; j < numberOfNalUnits; j++) {
          int nalUnitLength = data.readUnsignedShort();
          System.arraycopy(
              NalUnitUtil.NAL_START_CODE,
              0,
              buffer,
              bufferPosition,
              NalUnitUtil.NAL_START_CODE.length);
          bufferPosition += NalUnitUtil.NAL_START_CODE.length;
          System.arraycopy(
              data.getData(), data.getPosition(), buffer, bufferPosition, nalUnitLength);
          if (nalUnitType == SPS_NAL_UNIT_TYPE && j == 0) {
            NalUnitUtil.H265SpsData spsData =
                NalUnitUtil.parseH265SpsNalUnit(
                    buffer, bufferPosition, bufferPosition + nalUnitLength);
            width = spsData.width;
            height = spsData.height;
            pixelWidthHeightRatio = spsData.pixelWidthHeightRatio;
            codecs =
                CodecSpecificDataUtil.buildHevcCodecString(
                    spsData.generalProfileSpace,
                    spsData.generalTierFlag,
                    spsData.generalProfileIdc,
                    spsData.generalProfileCompatibilityFlags,
                    spsData.constraintBytes,
                    spsData.generalLevelIdc);
          }
          bufferPosition += nalUnitLength;
          data.skipBytes(nalUnitLength);
        }
      }

      List<byte[]> initializationData =
          csdLength == 0 ? Collections.emptyList() : Collections.singletonList(buffer);
      return new HevcConfig(
          initializationData, lengthSizeMinusOne + 1, width, height, pixelWidthHeightRatio, codecs);
    } catch (ArrayIndexOutOfBoundsException e) {
      throw ParserException.createForMalformedContainer("Error parsing HEVC config", e);
    }
  }

  private static final int SPS_NAL_UNIT_TYPE = 33;

  /**
   * List of buffers containing the codec-specific data to be provided to the decoder.
   *
   * <p>See {@link Format#initializationData}.
   */
  public final List<byte[]> initializationData;

  /** The length of the NAL unit length field in the bitstream's container, in bytes. */
  public final int nalUnitLengthFieldLength;

  /** The width of each decoded frame, or {@link Format#NO_VALUE} if unknown. */
  public final int width;

  /** The height of each decoded frame, or {@link Format#NO_VALUE} if unknown. */
  public final int height;

  /** The pixel width to height ratio. */
  public final float pixelWidthHeightRatio;

  /**
   * An RFC 6381 codecs string representing the video format, or {@code null} if not known.
   *
   * <p>See {@link Format#codecs}.
   */
  @Nullable public final String codecs;

  private HevcConfig(
      List<byte[]> initializationData,
      int nalUnitLengthFieldLength,
      int width,
      int height,
      float pixelWidthHeightRatio,
      @Nullable String codecs) {
    this.initializationData = initializationData;
    this.nalUnitLengthFieldLength = nalUnitLengthFieldLength;
    this.width = width;
    this.height = height;
    this.pixelWidthHeightRatio = pixelWidthHeightRatio;
    this.codecs = codecs;
  }
}
