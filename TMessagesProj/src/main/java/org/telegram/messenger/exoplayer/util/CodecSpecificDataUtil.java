/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.util;

import android.util.Pair;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides static utility methods for manipulating various types of codec specific data.
 */
public final class CodecSpecificDataUtil {

  private static final byte[] NAL_START_CODE = new byte[] {0, 0, 0, 1};

  private static final int AUDIO_SPECIFIC_CONFIG_FREQUENCY_INDEX_ARBITRARY = 0xF;

  private static final int[] AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE = new int[] {
    96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350
  };

  private static final int AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID = -1;
  /**
   * In the channel configurations below, <A> indicates a single channel element; (A, B) indicates a
   * channel pair element; and [A] indicates a low-frequency effects element.
   * The speaker mapping short forms used are:
   * - FC: front center
   * - BC: back center
   * - FL/FR: front left/right
   * - FCL/FCR: front center left/right
   * - FTL/FTR: front top left/right
   * - SL/SR: back surround left/right
   * - BL/BR: back left/right
   * - LFE: low frequency effects
   */
  private static final int[] AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE =
      new int[] {
        0,
        1, /* mono: <FC> */
        2, /* stereo: (FL, FR) */
        3, /* 3.0: <FC>, (FL, FR) */
        4, /* 4.0: <FC>, (FL, FR), <BC> */
        5, /* 5.0 back: <FC>, (FL, FR), (SL, SR) */
        6, /* 5.1 back: <FC>, (FL, FR), (SL, SR), <BC>, [LFE] */
        8, /* 7.1 wide back: <FC>, (FCL, FCR), (FL, FR), (SL, SR), [LFE] */
        AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID,
        AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID,
        AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID,
        7, /* 6.1: <FC>, (FL, FR), (SL, SR), <RC>, [LFE] */
        8, /* 7.1: <FC>, (FL, FR), (SL, SR), (BL, BR), [LFE] */
        AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID,
        8, /* 7.1 top: <FC>, (FL, FR), (SL, SR), [LFE], (FTL, FTR) */
        AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID
      };

  // Advanced Audio Coding Low-Complexity profile.
  private static final int AUDIO_OBJECT_TYPE_AAC_LC = 2;
  // Spectral Band Replication.
  private static final int AUDIO_OBJECT_TYPE_SBR = 5;
  // Error Resilient Bit-Sliced Arithmetic Coding.
  private static final int AUDIO_OBJECT_TYPE_ER_BSAC = 22;
  // Parametric Stereo.
  private static final int AUDIO_OBJECT_TYPE_PS = 29;

  private CodecSpecificDataUtil() {}

  /**
   * Parses an AudioSpecificConfig, as defined in ISO 14496-3 1.6.2.1
   *
   * @param audioSpecificConfig The AudioSpecificConfig to parse.
   * @return A pair consisting of the sample rate in Hz and the channel count.
   */
  public static Pair<Integer, Integer> parseAacAudioSpecificConfig(byte[] audioSpecificConfig) {
    ParsableBitArray bitArray = new ParsableBitArray(audioSpecificConfig);
    int audioObjectType = bitArray.readBits(5);
    int frequencyIndex = bitArray.readBits(4);
    int sampleRate;
    if (frequencyIndex == AUDIO_SPECIFIC_CONFIG_FREQUENCY_INDEX_ARBITRARY) {
      sampleRate = bitArray.readBits(24);
    } else {
      Assertions.checkArgument(frequencyIndex < 13);
      sampleRate = AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE[frequencyIndex];
    }
    int channelConfiguration = bitArray.readBits(4);
    if (audioObjectType == AUDIO_OBJECT_TYPE_SBR || audioObjectType == AUDIO_OBJECT_TYPE_PS) {
      // For an AAC bitstream using spectral band replication (SBR) or parametric stereo (PS) with
      // explicit signaling, we return the extension sampling frequency as the sample rate of the
      // content; this is identical to the sample rate of the decoded output but may differ from
      // the sample rate set above.
      // Use the extensionSamplingFrequencyIndex.
      frequencyIndex = bitArray.readBits(4);
      if (frequencyIndex == AUDIO_SPECIFIC_CONFIG_FREQUENCY_INDEX_ARBITRARY) {
        sampleRate = bitArray.readBits(24);
      } else {
        Assertions.checkArgument(frequencyIndex < 13);
        sampleRate = AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE[frequencyIndex];
      }
      audioObjectType = bitArray.readBits(5);
      if (audioObjectType == AUDIO_OBJECT_TYPE_ER_BSAC) {
        // Use the extensionChannelConfiguration.
        channelConfiguration = bitArray.readBits(4);
      }
    }
    int channelCount = AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE[channelConfiguration];
    Assertions.checkArgument(channelCount != AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID);
    return Pair.create(sampleRate, channelCount);
  }

  /**
   * Builds a simple AudioSpecificConfig, as defined in ISO 14496-3 1.6.2.1
   *
   * @param audioObjectType The audio object type.
   * @param sampleRateIndex The sample rate index.
   * @param channelConfig The channel configuration.
   * @return The AudioSpecificConfig.
   */
  public static byte[] buildAacAudioSpecificConfig(int audioObjectType, int sampleRateIndex,
      int channelConfig) {
    byte[] audioSpecificConfig = new byte[2];
    audioSpecificConfig[0] = (byte) ((audioObjectType << 3) & 0xF8 | (sampleRateIndex >> 1) & 0x07);
    audioSpecificConfig[1] = (byte) ((sampleRateIndex << 7) & 0x80 | (channelConfig << 3) & 0x78);
    return audioSpecificConfig;
  }

  /**
   * Builds a simple HE-AAC LC AudioSpecificConfig, as defined in ISO 14496-3 1.6.2.1
   *
   * @param sampleRate The sample rate in Hz.
   * @param numChannels The number of channels.
   * @return The AudioSpecificConfig.
   */
  public static byte[] buildAacAudioSpecificConfig(int sampleRate, int numChannels) {
    int sampleRateIndex = -1;
    for (int i = 0; i < AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE.length; ++i) {
      if (sampleRate == AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE[i]) {
        sampleRateIndex = i;
      }
    }
    int channelConfig = -1;
    for (int i = 0; i < AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE.length; ++i) {
      if (numChannels == AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE[i]) {
        channelConfig = i;
      }
    }
    // The full specification for AudioSpecificConfig is stated in ISO 14496-3 Section 1.6.2.1
    byte[] csd = new byte[2];
    csd[0] = (byte) ((AUDIO_OBJECT_TYPE_AAC_LC << 3) | (sampleRateIndex >> 1));
    csd[1] = (byte) (((sampleRateIndex & 0x1) << 7) | (channelConfig << 3));
    return csd;
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
   * <p>
   * If the input consists of NAL start code delimited units, then the returned array consists of
   * the split NAL units, each of which is still prefixed with the NAL start code. For any other
   * input, null is returned.
   *
   * @param data An array of data.
   * @return The individual NAL units, or null if the input did not consist of NAL start code
   *     delimited units.
   */
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
    } while (nalUnitIndex != -1);
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
   * @return The index of the first byte of the found start code, or -1.
   */
  private static int findNalStartCode(byte[] data, int index) {
    int endIndex = data.length - NAL_START_CODE.length;
    for (int i = index; i <= endIndex; i++) {
      if (isNalStartCode(data, i)) {
        return i;
      }
    }
    return -1;
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

}
