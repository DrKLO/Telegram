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
package com.google.android.exoplayer2.util;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import android.util.Pair;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Provides utilities for handling various types of codec-specific data. */
public final class CodecSpecificDataUtil {

  private static final byte[] NAL_START_CODE = new byte[] {0, 0, 0, 1};
  private static final String[] HEVC_GENERAL_PROFILE_SPACE_STRINGS =
      new String[] {"", "A", "B", "C"};

  // MP4V-ES
  private static final int VISUAL_OBJECT_LAYER = 1;
  private static final int VISUAL_OBJECT_LAYER_START = 0x20;
  private static final int EXTENDED_PAR = 0x0F;
  private static final int RECTANGULAR = 0x00;

  /**
   * Parses an ALAC AudioSpecificConfig (i.e. an <a
   * href="https://github.com/macosforge/alac/blob/master/ALACMagicCookieDescription.txt">ALACSpecificConfig</a>).
   *
   * @param audioSpecificConfig A byte array containing the AudioSpecificConfig to parse.
   * @return A pair consisting of the sample rate in Hz and the channel count.
   */
  public static Pair<Integer, Integer> parseAlacAudioSpecificConfig(byte[] audioSpecificConfig) {
    ParsableByteArray byteArray = new ParsableByteArray(audioSpecificConfig);
    byteArray.setPosition(9);
    int channelCount = byteArray.readUnsignedByte();
    byteArray.setPosition(20);
    int sampleRate = byteArray.readUnsignedIntToInt();
    return Pair.create(sampleRate, channelCount);
  }

  /**
   * Returns initialization data for formats with MIME type {@link MimeTypes#APPLICATION_CEA708}.
   *
   * @param isWideAspectRatio Whether the CEA-708 closed caption service is formatted for displays
   *     with 16:9 aspect ratio.
   * @return Initialization data for formats with MIME type {@link MimeTypes#APPLICATION_CEA708}.
   */
  public static List<byte[]> buildCea708InitializationData(boolean isWideAspectRatio) {
    return Collections.singletonList(isWideAspectRatio ? new byte[] {1} : new byte[] {0});
  }

  /**
   * Returns whether the CEA-708 closed caption service with the given initialization data is
   * formatted for displays with 16:9 aspect ratio.
   *
   * @param initializationData The initialization data to parse.
   * @return Whether the CEA-708 closed caption service is formatted for displays with 16:9 aspect
   *     ratio.
   */
  public static boolean parseCea708InitializationData(List<byte[]> initializationData) {
    return initializationData.size() == 1
        && initializationData.get(0).length == 1
        && initializationData.get(0)[0] == 1;
  }

  /**
   * Parses an MPEG-4 Visual configuration information, as defined in ISO/IEC14496-2.
   *
   * @param videoSpecificConfig A byte array containing the MPEG-4 Visual configuration information
   *     to parse.
   * @return A pair of the video's width and height.
   */
  public static Pair<Integer, Integer> getVideoResolutionFromMpeg4VideoConfig(
      byte[] videoSpecificConfig) {
    int offset = 0;
    boolean foundVOL = false;
    ParsableByteArray scratchBytes = new ParsableByteArray(videoSpecificConfig);
    while (offset + 3 < videoSpecificConfig.length) {
      if (scratchBytes.readUnsignedInt24() != VISUAL_OBJECT_LAYER
          || (videoSpecificConfig[offset + 3] & 0xF0) != VISUAL_OBJECT_LAYER_START) {
        scratchBytes.setPosition(scratchBytes.getPosition() - 2);
        offset++;
        continue;
      }
      foundVOL = true;
      break;
    }

    checkArgument(foundVOL, "Invalid input: VOL not found.");

    ParsableBitArray scratchBits = new ParsableBitArray(videoSpecificConfig);
    // Skip the start codecs from the bitstream
    scratchBits.skipBits((offset + 4) * 8);
    scratchBits.skipBits(1); // random_accessible_vol
    scratchBits.skipBits(8); // video_object_type_indication

    if (scratchBits.readBit()) { // object_layer_identifier
      scratchBits.skipBits(4); // video_object_layer_verid
      scratchBits.skipBits(3); // video_object_layer_priority
    }

    int aspectRatioInfo = scratchBits.readBits(4);
    if (aspectRatioInfo == EXTENDED_PAR) {
      scratchBits.skipBits(8); // par_width
      scratchBits.skipBits(8); // par_height
    }

    if (scratchBits.readBit()) { // vol_control_parameters
      scratchBits.skipBits(2); // chroma_format
      scratchBits.skipBits(1); // low_delay
      if (scratchBits.readBit()) { // vbv_parameters
        scratchBits.skipBits(79);
      }
    }

    int videoObjectLayerShape = scratchBits.readBits(2);
    checkArgument(
        videoObjectLayerShape == RECTANGULAR,
        "Only supports rectangular video object layer shape.");

    checkArgument(scratchBits.readBit()); // marker_bit
    int vopTimeIncrementResolution = scratchBits.readBits(16);
    checkArgument(scratchBits.readBit()); // marker_bit

    if (scratchBits.readBit()) { // fixed_vop_rate
      checkArgument(vopTimeIncrementResolution > 0);
      vopTimeIncrementResolution--;
      int numBitsToSkip = 0;
      while (vopTimeIncrementResolution > 0) {
        numBitsToSkip++;
        vopTimeIncrementResolution >>= 1;
      }
      scratchBits.skipBits(numBitsToSkip); // fixed_vop_time_increment
    }

    checkArgument(scratchBits.readBit()); // marker_bit
    int videoObjectLayerWidth = scratchBits.readBits(13);
    checkArgument(scratchBits.readBit()); // marker_bit
    int videoObjectLayerHeight = scratchBits.readBits(13);
    checkArgument(scratchBits.readBit()); // marker_bit

    scratchBits.skipBits(1); // interlaced

    return Pair.create(videoObjectLayerWidth, videoObjectLayerHeight);
  }

  /**
   * Builds an RFC 6381 AVC codec string using the provided parameters.
   *
   * @param profileIdc The encoding profile.
   * @param constraintsFlagsAndReservedZero2Bits The constraint flags followed by the reserved zero
   *     2 bits, all contained in the least significant byte of the integer.
   * @param levelIdc The encoding level.
   * @return An RFC 6381 AVC codec string built using the provided parameters.
   */
  public static String buildAvcCodecString(
      int profileIdc, int constraintsFlagsAndReservedZero2Bits, int levelIdc) {
    return String.format(
        "avc1.%02X%02X%02X", profileIdc, constraintsFlagsAndReservedZero2Bits, levelIdc);
  }

  /** Builds an RFC 6381 HEVC codec string using the provided parameters. */
  public static String buildHevcCodecString(
      int generalProfileSpace,
      boolean generalTierFlag,
      int generalProfileIdc,
      int generalProfileCompatibilityFlags,
      int[] constraintBytes,
      int generalLevelIdc) {
    StringBuilder builder =
        new StringBuilder(
            Util.formatInvariant(
                "hvc1.%s%d.%X.%c%d",
                HEVC_GENERAL_PROFILE_SPACE_STRINGS[generalProfileSpace],
                generalProfileIdc,
                generalProfileCompatibilityFlags,
                generalTierFlag ? 'H' : 'L',
                generalLevelIdc));
    // Omit trailing zero bytes.
    int trailingZeroIndex = constraintBytes.length;
    while (trailingZeroIndex > 0 && constraintBytes[trailingZeroIndex - 1] == 0) {
      trailingZeroIndex--;
    }
    for (int i = 0; i < trailingZeroIndex; i++) {
      builder.append(String.format(".%02X", constraintBytes[i]));
    }
    return builder.toString();
  }

  /**
   * Constructs a NAL unit consisting of the NAL start code followed by the specified data.
   *
   * @param data An array containing the data that should follow the NAL start code.
   * @param offset The start offset into {@code data}.
   * @param length The number of bytes to copy from {@code data}
   * @return The constructed NAL unit.
   */
  public static byte[] buildNalUnit(byte[] data, int offset, int length) {
    byte[] nalUnit = new byte[length + NAL_START_CODE.length];
    System.arraycopy(NAL_START_CODE, 0, nalUnit, 0, NAL_START_CODE.length);
    System.arraycopy(data, offset, nalUnit, NAL_START_CODE.length, length);
    return nalUnit;
  }

  /**
   * Splits an array of NAL units.
   *
   * <p>If the input consists of NAL start code delimited units, then the returned array consists of
   * the split NAL units, each of which is still prefixed with the NAL start code. For any other
   * input, null is returned.
   *
   * @param data An array of data.
   * @return The individual NAL units, or null if the input did not consist of NAL start code
   *     delimited units.
   */
  @Nullable
  public static byte[][] splitNalUnits(byte[] data) {
    if (!isNalStartCode(data, 0)) {
      // data does not consist of NAL start code delimited units.
      return null;
    }
    List<Integer> starts = new ArrayList<>();
    int nalUnitIndex = 0;
    do {
      starts.add(nalUnitIndex);
      nalUnitIndex = findNalStartCode(data, nalUnitIndex + NAL_START_CODE.length);
    } while (nalUnitIndex != C.INDEX_UNSET);
    byte[][] split = new byte[starts.size()][];
    for (int i = 0; i < starts.size(); i++) {
      int startIndex = starts.get(i);
      int endIndex = i < starts.size() - 1 ? starts.get(i + 1) : data.length;
      byte[] nal = new byte[endIndex - startIndex];
      System.arraycopy(data, startIndex, nal, 0, nal.length);
      split[i] = nal;
    }
    return split;
  }

  /**
   * Finds the next occurrence of the NAL start code from a given index.
   *
   * @param data The data in which to search.
   * @param index The first index to test.
   * @return The index of the first byte of the found start code, or {@link C#INDEX_UNSET}.
   */
  private static int findNalStartCode(byte[] data, int index) {
    int endIndex = data.length - NAL_START_CODE.length;
    for (int i = index; i <= endIndex; i++) {
      if (isNalStartCode(data, i)) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  /**
   * Tests whether there exists a NAL start code at a given index.
   *
   * @param data The data.
   * @param index The index to test.
   * @return Whether there exists a start code that begins at {@code index}.
   */
  private static boolean isNalStartCode(byte[] data, int index) {
    if (data.length - index <= NAL_START_CODE.length) {
      return false;
    }
    for (int j = 0; j < NAL_START_CODE.length; j++) {
      if (data[index + j] != NAL_START_CODE[j]) {
        return false;
      }
    }
    return true;
  }

  private CodecSpecificDataUtil() {}
}
