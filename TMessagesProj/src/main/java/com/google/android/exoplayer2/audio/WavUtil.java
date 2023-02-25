/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Util;

/** Utilities for handling WAVE files. */
public final class WavUtil {

  /** Four character code for "RIFF". */
  public static final int RIFF_FOURCC = 0x52494646;
  /** Four character code for "WAVE". */
  public static final int WAVE_FOURCC = 0x57415645;
  /** Four character code for "fmt ". */
  public static final int FMT_FOURCC = 0x666d7420;
  /** Four character code for "data". */
  public static final int DATA_FOURCC = 0x64617461;
  /** Four character code for "RF64". */
  public static final int RF64_FOURCC = 0x52463634;
  /** Four character code for "ds64". */
  public static final int DS64_FOURCC = 0x64733634;

  /** WAVE type value for integer PCM audio data. */
  public static final int TYPE_PCM = 0x0001;
  /** WAVE type value for float PCM audio data. */
  public static final int TYPE_FLOAT = 0x0003;
  /** WAVE type value for 8-bit ITU-T G.711 A-law audio data. */
  public static final int TYPE_ALAW = 0x0006;
  /** WAVE type value for 8-bit ITU-T G.711 mu-law audio data. */
  public static final int TYPE_MLAW = 0x0007;
  /** WAVE type value for IMA ADPCM audio data. */
  public static final int TYPE_IMA_ADPCM = 0x0011;
  /** WAVE type value for extended WAVE format. */
  public static final int TYPE_WAVE_FORMAT_EXTENSIBLE = 0xFFFE;

  /**
   * Returns the WAVE format type value for the given {@link C.PcmEncoding}.
   *
   * @param pcmEncoding The {@link C.PcmEncoding} value.
   * @return The corresponding WAVE format type.
   * @throws IllegalArgumentException If {@code pcmEncoding} is not a {@link C.PcmEncoding}, or if
   *     it's {@link C#ENCODING_INVALID} or {@link Format#NO_VALUE}.
   */
  public static int getTypeForPcmEncoding(@C.PcmEncoding int pcmEncoding) {
    switch (pcmEncoding) {
      case C.ENCODING_PCM_8BIT:
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_PCM_24BIT:
      case C.ENCODING_PCM_32BIT:
        return TYPE_PCM;
      case C.ENCODING_PCM_FLOAT:
        return TYPE_FLOAT;
      case C.ENCODING_PCM_16BIT_BIG_ENDIAN: // Not TYPE_PCM, because TYPE_PCM is little endian.
      case C.ENCODING_INVALID:
      case Format.NO_VALUE:
      default:
        throw new IllegalArgumentException();
    }
  }

  /**
   * Returns the {@link C.PcmEncoding} for the given WAVE format type value, or {@link
   * C#ENCODING_INVALID} if the type is not a known PCM type.
   */
  public static @C.PcmEncoding int getPcmEncodingForType(int type, int bitsPerSample) {
    switch (type) {
      case TYPE_PCM:
      case TYPE_WAVE_FORMAT_EXTENSIBLE:
        return Util.getPcmEncoding(bitsPerSample);
      case TYPE_FLOAT:
        return bitsPerSample == 32 ? C.ENCODING_PCM_FLOAT : C.ENCODING_INVALID;
      default:
        return C.ENCODING_INVALID;
    }
  }

  private WavUtil() {
    // Prevent instantiation.
  }
}
