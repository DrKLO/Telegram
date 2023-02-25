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
package com.google.android.exoplayer2.audio;

import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableBitArray;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Utility methods for handling AAC audio streams. */
public final class AacUtil {

  private static final String TAG = "AacUtil";

  /** Holds sample format information for AAC audio. */
  public static final class Config {

    /** The sample rate in Hertz. */
    public final int sampleRateHz;
    /** The number of channels. */
    public final int channelCount;
    /** The RFC 6381 codecs string. */
    public final String codecs;

    private Config(int sampleRateHz, int channelCount, String codecs) {
      this.sampleRateHz = sampleRateHz;
      this.channelCount = channelCount;
      this.codecs = codecs;
    }
  }

  // Audio sample count constants assume the frameLengthFlag in the access unit is 0.
  /**
   * Number of raw audio samples that are produced per channel when decoding an AAC LC access unit.
   */
  public static final int AAC_LC_AUDIO_SAMPLE_COUNT = 1024;
  /**
   * Number of raw audio samples that are produced per channel when decoding an AAC XHE access unit.
   */
  public static final int AAC_XHE_AUDIO_SAMPLE_COUNT = AAC_LC_AUDIO_SAMPLE_COUNT;
  /**
   * Number of raw audio samples that are produced per channel when decoding an AAC HE access unit.
   */
  public static final int AAC_HE_AUDIO_SAMPLE_COUNT = 2048;
  /**
   * Number of raw audio samples that are produced per channel when decoding an AAC LD access unit.
   */
  public static final int AAC_LD_AUDIO_SAMPLE_COUNT = 512;

  // Maximum bitrates for AAC profiles from the Fraunhofer FDK AAC encoder documentation:
  // https://cs.android.com/android/platform/superproject/+/android-9.0.0_r8:external/aac/libAACenc/include/aacenc_lib.h;l=718
  /** Maximum rate for an AAC LC audio stream, in bytes per second. */
  public static final int AAC_LC_MAX_RATE_BYTES_PER_SECOND = 800 * 1000 / 8;
  /** Maximum rate for an AAC HE V1 audio stream, in bytes per second. */
  public static final int AAC_HE_V1_MAX_RATE_BYTES_PER_SECOND = 128 * 1000 / 8;
  /** Maximum rate for an AAC HE V2 audio stream, in bytes per second. */
  public static final int AAC_HE_V2_MAX_RATE_BYTES_PER_SECOND = 56 * 1000 / 8;
  /**
   * Maximum rate for an AAC XHE audio stream, in bytes per second.
   *
   * <p>Fraunhofer documentation says "500 kbit/s and above" for stereo, so we use a rate generously
   * above the 500 kbit/s level.
   */
  public static final int AAC_XHE_MAX_RATE_BYTES_PER_SECOND = 2048 * 1000 / 8;
  /**
   * Maximum rate for an AAC ELD audio stream, in bytes per second.
   *
   * <p>Fraunhofer documentation shows AAC-ELD as useful for up to ~ 64 kbit/s so we use this value.
   */
  public static final int AAC_ELD_MAX_RATE_BYTES_PER_SECOND = 64 * 1000 / 8;

  private static final int AUDIO_SPECIFIC_CONFIG_FREQUENCY_INDEX_ARBITRARY = 0xF;
  private static final int[] AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE =
      new int[] {
        96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350
      };
  private static final int AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID = -1;
  /**
   * In the channel configurations below, &lt;A&gt; indicates a single channel element; (A, B)
   * indicates a channel pair element; and [A] indicates a low-frequency effects element. The
   * speaker mapping short forms used are:
   *
   * <ul>
   *   <li>FC: front center
   *   <li>BC: back center
   *   <li>FL/FR: front left/right
   *   <li>FCL/FCR: front center left/right
   *   <li>FTL/FTR: front top left/right
   *   <li>SL/SR: back surround left/right
   *   <li>BL/BR: back left/right
   *   <li>LFE: low frequency effects
   * </ul>
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

  /**
   * Prefix for the RFC 6381 codecs string for AAC formats. To form a full codecs string, suffix the
   * decimal AudioObjectType.
   */
  private static final String CODECS_STRING_PREFIX = "mp4a.40.";

  // Advanced Audio Coding Low-Complexity profile.
  public static final int AUDIO_OBJECT_TYPE_AAC_LC = 2;
  // Spectral Band Replication.
  public static final int AUDIO_OBJECT_TYPE_AAC_SBR = 5;
  // Error Resilient Bit-Sliced Arithmetic Coding.
  public static final int AUDIO_OBJECT_TYPE_AAC_ER_BSAC = 22;
  // Enhanced low delay.
  public static final int AUDIO_OBJECT_TYPE_AAC_ELD = 23;
  // Parametric Stereo.
  public static final int AUDIO_OBJECT_TYPE_AAC_PS = 29;
  // Escape code for extended audio object types.
  private static final int AUDIO_OBJECT_TYPE_ESCAPE = 31;
  // Extended high efficiency.
  public static final int AUDIO_OBJECT_TYPE_AAC_XHE = 42;

  /**
   * Valid AAC Audio object types. One of {@link #AUDIO_OBJECT_TYPE_AAC_LC}, {@link
   * #AUDIO_OBJECT_TYPE_AAC_SBR}, {@link #AUDIO_OBJECT_TYPE_AAC_ER_BSAC}, {@link
   * #AUDIO_OBJECT_TYPE_AAC_ELD}, {@link #AUDIO_OBJECT_TYPE_AAC_PS} or {@link
   * #AUDIO_OBJECT_TYPE_AAC_XHE}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    AUDIO_OBJECT_TYPE_AAC_LC,
    AUDIO_OBJECT_TYPE_AAC_SBR,
    AUDIO_OBJECT_TYPE_AAC_ER_BSAC,
    AUDIO_OBJECT_TYPE_AAC_ELD,
    AUDIO_OBJECT_TYPE_AAC_PS,
    AUDIO_OBJECT_TYPE_AAC_XHE
  })
  public @interface AacAudioObjectType {}

  /**
   * Parses an AAC AudioSpecificConfig, as defined in ISO 14496-3 1.6.2.1
   *
   * @param audioSpecificConfig A byte array containing the AudioSpecificConfig to parse.
   * @return The parsed configuration.
   * @throws ParserException If the AudioSpecificConfig cannot be parsed because it is invalid or
   *     unsupported.
   */
  public static Config parseAudioSpecificConfig(byte[] audioSpecificConfig) throws ParserException {
    return parseAudioSpecificConfig(
        new ParsableBitArray(audioSpecificConfig), /* forceReadToEnd= */ false);
  }

  /**
   * Parses an AAC AudioSpecificConfig, as defined in ISO 14496-3 1.6.2.1
   *
   * @param bitArray A {@link ParsableBitArray} containing the AudioSpecificConfig to parse. The
   *     position is advanced to the end of the AudioSpecificConfig.
   * @param forceReadToEnd Whether the entire AudioSpecificConfig should be read. Required for
   *     knowing the length of the configuration payload.
   * @return The parsed configuration.
   * @throws ParserException If the AudioSpecificConfig cannot be parsed because it is invalid or
   *     unsupported.
   */
  public static Config parseAudioSpecificConfig(ParsableBitArray bitArray, boolean forceReadToEnd)
      throws ParserException {
    int audioObjectType = getAudioObjectType(bitArray);
    int sampleRateHz = getSamplingFrequency(bitArray);
    int channelConfiguration = bitArray.readBits(4);
    String codecs = CODECS_STRING_PREFIX + audioObjectType;
    if (audioObjectType == AUDIO_OBJECT_TYPE_AAC_SBR
        || audioObjectType == AUDIO_OBJECT_TYPE_AAC_PS) {
      // For an AAC bitstream using spectral band replication (SBR) or parametric stereo (PS) with
      // explicit signaling, we return the extension sampling frequency as the sample rate of the
      // content; this is identical to the sample rate of the decoded output but may differ from
      // the sample rate set above.
      // Use the extensionSamplingFrequencyIndex.
      sampleRateHz = getSamplingFrequency(bitArray);
      audioObjectType = getAudioObjectType(bitArray);
      if (audioObjectType == AUDIO_OBJECT_TYPE_AAC_ER_BSAC) {
        // Use the extensionChannelConfiguration.
        channelConfiguration = bitArray.readBits(4);
      }
    }

    if (forceReadToEnd) {
      switch (audioObjectType) {
        case 1:
        case 2:
        case 3:
        case 4:
        case 6:
        case 7:
        case 17:
        case 19:
        case 20:
        case 21:
        case 22:
        case 23:
          parseGaSpecificConfig(bitArray, audioObjectType, channelConfiguration);
          break;
        default:
          throw ParserException.createForUnsupportedContainerFeature(
              "Unsupported audio object type: " + audioObjectType);
      }
      switch (audioObjectType) {
        case 17:
        case 19:
        case 20:
        case 21:
        case 22:
        case 23:
          int epConfig = bitArray.readBits(2);
          if (epConfig == 2 || epConfig == 3) {
            throw ParserException.createForUnsupportedContainerFeature(
                "Unsupported epConfig: " + epConfig);
          }
          break;
        default:
          break;
      }
    }
    // For supported containers, bits_to_decode() is always 0.
    int channelCount = AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE[channelConfiguration];
    if (channelCount == AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID) {
      throw ParserException.createForMalformedContainer(/* message= */ null, /* cause= */ null);
    }
    return new Config(sampleRateHz, channelCount, codecs);
  }

  /**
   * Builds a simple AAC LC AudioSpecificConfig, as defined in ISO 14496-3 1.6.2.1
   *
   * @param sampleRate The sample rate in Hz.
   * @param channelCount The channel count.
   * @return The AudioSpecificConfig.
   */
  public static byte[] buildAacLcAudioSpecificConfig(int sampleRate, int channelCount) {
    int sampleRateIndex = C.INDEX_UNSET;
    for (int i = 0; i < AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE.length; ++i) {
      if (sampleRate == AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE[i]) {
        sampleRateIndex = i;
      }
    }
    int channelConfig = C.INDEX_UNSET;
    for (int i = 0; i < AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE.length; ++i) {
      if (channelCount == AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE[i]) {
        channelConfig = i;
      }
    }
    if (sampleRate == C.INDEX_UNSET || channelConfig == C.INDEX_UNSET) {
      throw new IllegalArgumentException(
          "Invalid sample rate or number of channels: " + sampleRate + ", " + channelCount);
    }
    return buildAudioSpecificConfig(AUDIO_OBJECT_TYPE_AAC_LC, sampleRateIndex, channelConfig);
  }

  /**
   * Builds a simple AudioSpecificConfig, as defined in ISO 14496-3 1.6.2.1
   *
   * @param audioObjectType The audio object type.
   * @param sampleRateIndex The sample rate index.
   * @param channelConfig The channel configuration.
   * @return The AudioSpecificConfig.
   */
  public static byte[] buildAudioSpecificConfig(
      int audioObjectType, int sampleRateIndex, int channelConfig) {
    byte[] specificConfig = new byte[2];
    specificConfig[0] = (byte) (((audioObjectType << 3) & 0xF8) | ((sampleRateIndex >> 1) & 0x07));
    specificConfig[1] = (byte) (((sampleRateIndex << 7) & 0x80) | ((channelConfig << 3) & 0x78));
    return specificConfig;
  }

  /**
   * Returns the AAC audio object type as specified in 14496-3 (2005) Table 1.14.
   *
   * @param bitArray The bit array containing the audio specific configuration.
   * @return The audio object type.
   */
  private static int getAudioObjectType(ParsableBitArray bitArray) {
    int audioObjectType = bitArray.readBits(5);
    if (audioObjectType == AUDIO_OBJECT_TYPE_ESCAPE) {
      audioObjectType = 32 + bitArray.readBits(6);
    }
    return audioObjectType;
  }

  /**
   * Returns the AAC sampling frequency (or extension sampling frequency) as specified in 14496-3
   * (2005) Table 1.13.
   *
   * @param bitArray The bit array containing the audio specific configuration.
   * @return The sampling frequency.
   * @throws ParserException If the audio specific configuration is invalid.
   */
  private static int getSamplingFrequency(ParsableBitArray bitArray) throws ParserException {
    int samplingFrequency;
    int frequencyIndex = bitArray.readBits(4);
    if (frequencyIndex == AUDIO_SPECIFIC_CONFIG_FREQUENCY_INDEX_ARBITRARY) {
      if (bitArray.bitsLeft() < 24) {
        throw ParserException.createForMalformedContainer(
            /* message= */ "AAC header insufficient data", /* cause= */ null);
      }
      samplingFrequency = bitArray.readBits(24);
    } else if (frequencyIndex < 13) {
      samplingFrequency = AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE[frequencyIndex];
    } else {
      throw ParserException.createForMalformedContainer(
          /* message= */ "AAC header wrong Sampling Frequency Index", /* cause= */ null);
    }
    return samplingFrequency;
  }

  private static void parseGaSpecificConfig(
      ParsableBitArray bitArray, int audioObjectType, int channelConfiguration) {
    boolean frameLengthFlag = bitArray.readBit();
    if (frameLengthFlag) {
      Log.w(TAG, "Unexpected frameLengthFlag = 1");
    }
    boolean dependsOnCoreDecoder = bitArray.readBit();
    if (dependsOnCoreDecoder) {
      bitArray.skipBits(14); // coreCoderDelay.
    }
    boolean extensionFlag = bitArray.readBit();
    if (channelConfiguration == 0) {
      throw new UnsupportedOperationException(); // TODO: Implement programConfigElement();
    }
    if (audioObjectType == 6 || audioObjectType == 20) {
      bitArray.skipBits(3); // layerNr.
    }
    if (extensionFlag) {
      if (audioObjectType == 22) {
        bitArray.skipBits(16); // numOfSubFrame (5), layer_length(11).
      }
      if (audioObjectType == 17
          || audioObjectType == 19
          || audioObjectType == 20
          || audioObjectType == 23) {
        // aacSectionDataResilienceFlag, aacScalefactorDataResilienceFlag,
        // aacSpectralDataResilienceFlag.
        bitArray.skipBits(3);
      }
      bitArray.skipBits(1); // extensionFlag3.
    }
  }

  private AacUtil() {}
}
