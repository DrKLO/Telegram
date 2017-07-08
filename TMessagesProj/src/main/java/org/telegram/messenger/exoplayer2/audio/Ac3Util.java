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
package org.telegram.messenger.exoplayer2.audio;

import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.drm.DrmInitData;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import org.telegram.messenger.exoplayer2.util.ParsableBitArray;
import org.telegram.messenger.exoplayer2.util.ParsableByteArray;
import java.nio.ByteBuffer;

/**
 * Utility methods for parsing (E-)AC-3 syncframes, which are access units in (E-)AC-3 bitstreams.
 */
public final class Ac3Util {

  /**
   * Holds sample format information as presented by a syncframe header.
   */
  public static final class Ac3SyncFrameInfo {

    /**
     * The sample mime type of the bitstream. One of {@link MimeTypes#AUDIO_AC3} and
     * {@link MimeTypes#AUDIO_E_AC3}.
     */
    public final String mimeType;
    /**
     * The audio sampling rate in Hz.
     */
    public final int sampleRate;
    /**
     * The number of audio channels
     */
    public final int channelCount;
    /**
     * The size of the frame.
     */
    public final int frameSize;
    /**
     * Number of audio samples in the frame.
     */
    public final int sampleCount;

    private Ac3SyncFrameInfo(String mimeType, int channelCount, int sampleRate, int frameSize,
        int sampleCount) {
      this.mimeType = mimeType;
      this.channelCount = channelCount;
      this.sampleRate = sampleRate;
      this.frameSize = frameSize;
      this.sampleCount = sampleCount;
    }

  }

  /**
   * The number of new samples per (E-)AC-3 audio block.
   */
  private static final int AUDIO_SAMPLES_PER_AUDIO_BLOCK = 256;
  /**
   * Each syncframe has 6 blocks that provide 256 new audio samples. See ETSI TS 102 366 4.1.
   */
  private static final int AC3_SYNCFRAME_AUDIO_SAMPLE_COUNT = 6 * AUDIO_SAMPLES_PER_AUDIO_BLOCK;
  /**
   * Number of audio blocks per E-AC-3 syncframe, indexed by numblkscod.
   */
  private static final int[] BLOCKS_PER_SYNCFRAME_BY_NUMBLKSCOD = new int[] {1, 2, 3, 6};
  /**
   * Sample rates, indexed by fscod.
   */
  private static final int[] SAMPLE_RATE_BY_FSCOD = new int[] {48000, 44100, 32000};
  /**
   * Sample rates, indexed by fscod2 (E-AC-3).
   */
  private static final int[] SAMPLE_RATE_BY_FSCOD2 = new int[] {24000, 22050, 16000};
  /**
   * Channel counts, indexed by acmod.
   */
  private static final int[] CHANNEL_COUNT_BY_ACMOD = new int[] {2, 1, 2, 3, 3, 4, 4, 5};
  /**
   * Nominal bitrates in kbps, indexed by frmsizecod / 2. (See ETSI TS 102 366 table 4.13.)
   */
  private static final int[] BITRATE_BY_HALF_FRMSIZECOD = new int[] {32, 40, 48, 56, 64, 80, 96,
      112, 128, 160, 192, 224, 256, 320, 384, 448, 512, 576, 640};
  /**
   * 16-bit words per syncframe, indexed by frmsizecod / 2. (See ETSI TS 102 366 table 4.13.)
   */
  private static final int[] SYNCFRAME_SIZE_WORDS_BY_HALF_FRMSIZECOD_44_1 = new int[] {69, 87, 104,
      121, 139, 174, 208, 243, 278, 348, 417, 487, 557, 696, 835, 975, 1114, 1253, 1393};

  /**
   * Returns the AC-3 format given {@code data} containing the AC3SpecificBox according to
   * ETSI TS 102 366 Annex F. The reading position of {@code data} will be modified.
   *
   * @param data The AC3SpecificBox to parse.
   * @param trackId The track identifier to set on the format, or null.
   * @param language The language to set on the format.
   * @param drmInitData {@link DrmInitData} to be included in the format.
   * @return The AC-3 format parsed from data in the header.
   */
  public static Format parseAc3AnnexFFormat(ParsableByteArray data, String trackId,
      String language, DrmInitData drmInitData) {
    int fscod = (data.readUnsignedByte() & 0xC0) >> 6;
    int sampleRate = SAMPLE_RATE_BY_FSCOD[fscod];
    int nextByte = data.readUnsignedByte();
    int channelCount = CHANNEL_COUNT_BY_ACMOD[(nextByte & 0x38) >> 3];
    if ((nextByte & 0x04) != 0) { // lfeon
      channelCount++;
    }
    return Format.createAudioSampleFormat(trackId, MimeTypes.AUDIO_AC3, null, Format.NO_VALUE,
        Format.NO_VALUE, channelCount, sampleRate, null, drmInitData, 0, language);
  }

  /**
   * Returns the E-AC-3 format given {@code data} containing the EC3SpecificBox according to
   * ETSI TS 102 366 Annex F. The reading position of {@code data} will be modified.
   *
   * @param data The EC3SpecificBox to parse.
   * @param trackId The track identifier to set on the format, or null.
   * @param language The language to set on the format.
   * @param drmInitData {@link DrmInitData} to be included in the format.
   * @return The E-AC-3 format parsed from data in the header.
   */
  public static Format parseEAc3AnnexFFormat(ParsableByteArray data, String trackId,
      String language, DrmInitData drmInitData) {
    data.skipBytes(2); // data_rate, num_ind_sub

    // Read only the first substream.
    // TODO: Read later substreams?
    int fscod = (data.readUnsignedByte() & 0xC0) >> 6;
    int sampleRate = SAMPLE_RATE_BY_FSCOD[fscod];
    int nextByte = data.readUnsignedByte();
    int channelCount = CHANNEL_COUNT_BY_ACMOD[(nextByte & 0x0E) >> 1];
    if ((nextByte & 0x01) != 0) { // lfeon
      channelCount++;
    }
    return Format.createAudioSampleFormat(trackId, MimeTypes.AUDIO_E_AC3, null, Format.NO_VALUE,
        Format.NO_VALUE, channelCount, sampleRate, null, drmInitData, 0, language);
  }

  /**
   * Returns (E-)AC-3 format information given {@code data} containing a syncframe. The reading
   * position of {@code data} will be modified.
   *
   * @param data The data to parse, positioned at the start of the syncframe.
   * @return The (E-)AC-3 format data parsed from the header.
   */
  public static Ac3SyncFrameInfo parseAc3SyncframeInfo(ParsableBitArray data) {
    int initialPosition = data.getPosition();
    data.skipBits(40);
    boolean isEac3 = data.readBits(5) == 16;
    data.setPosition(initialPosition);
    String mimeType;
    int sampleRate;
    int acmod;
    int frameSize;
    int sampleCount;
    if (isEac3) {
      mimeType = MimeTypes.AUDIO_E_AC3;
      data.skipBits(16 + 2 + 3); // syncword, strmtype, substreamid
      frameSize = (data.readBits(11) + 1) * 2;
      int fscod = data.readBits(2);
      int audioBlocks;
      if (fscod == 3) {
        sampleRate = SAMPLE_RATE_BY_FSCOD2[data.readBits(2)];
        audioBlocks = 6;
      } else {
        int numblkscod = data.readBits(2);
        audioBlocks = BLOCKS_PER_SYNCFRAME_BY_NUMBLKSCOD[numblkscod];
        sampleRate = SAMPLE_RATE_BY_FSCOD[fscod];
      }
      sampleCount = AUDIO_SAMPLES_PER_AUDIO_BLOCK * audioBlocks;
      acmod = data.readBits(3);
    } else /* is AC-3 */ {
      mimeType = MimeTypes.AUDIO_AC3;
      data.skipBits(16 + 16); // syncword, crc1
      int fscod = data.readBits(2);
      int frmsizecod = data.readBits(6);
      frameSize = getAc3SyncframeSize(fscod, frmsizecod);
      data.skipBits(5 + 3); // bsid, bsmod
      acmod = data.readBits(3);
      if ((acmod & 0x01) != 0 && acmod != 1) {
        data.skipBits(2); // cmixlev
      }
      if ((acmod & 0x04) != 0) {
        data.skipBits(2); // surmixlev
      }
      if (acmod == 2) {
        data.skipBits(2); // dsurmod
      }
      sampleRate = SAMPLE_RATE_BY_FSCOD[fscod];
      sampleCount = AC3_SYNCFRAME_AUDIO_SAMPLE_COUNT;
    }
    boolean lfeon = data.readBit();
    int channelCount = CHANNEL_COUNT_BY_ACMOD[acmod] + (lfeon ? 1 : 0);
    return new Ac3SyncFrameInfo(mimeType, channelCount, sampleRate, frameSize, sampleCount);
  }

  /**
   * Returns the size in bytes of the given AC-3 syncframe.
   *
   * @param data The syncframe to parse.
   * @return The syncframe size in bytes. {@link C#LENGTH_UNSET} if the input is invalid.
   */
  public static int parseAc3SyncframeSize(byte[] data) {
    if (data.length < 5) {
      return C.LENGTH_UNSET;
    }
    int fscod = (data[4] & 0xC0) >> 6;
    int frmsizecod = data[4] & 0x3F;
    return getAc3SyncframeSize(fscod, frmsizecod);
  }

  /**
   * Returns the number of audio samples in an AC-3 syncframe.
   */
  public static int getAc3SyncframeAudioSampleCount() {
    return AC3_SYNCFRAME_AUDIO_SAMPLE_COUNT;
  }

  /**
   * Reads the number of audio samples represented by the given E-AC-3 syncframe. The buffer's
   * position is not modified.
   *
   * @param buffer The {@link ByteBuffer} from which to read the syncframe.
   * @return The number of audio samples represented by the syncframe.
   */
  public static int parseEAc3SyncframeAudioSampleCount(ByteBuffer buffer) {
    // See ETSI TS 102 366 subsection E.1.2.2.
    int fscod = (buffer.get(buffer.position() + 4) & 0xC0) >> 6;
    return AUDIO_SAMPLES_PER_AUDIO_BLOCK * (fscod == 0x03 ? 6
        : BLOCKS_PER_SYNCFRAME_BY_NUMBLKSCOD[(buffer.get(buffer.position() + 4) & 0x30) >> 4]);
  }

  private static int getAc3SyncframeSize(int fscod, int frmsizecod) {
    int halfFrmsizecod = frmsizecod / 2;
    if (fscod < 0 || fscod >= SAMPLE_RATE_BY_FSCOD.length || frmsizecod < 0
        || halfFrmsizecod >= SYNCFRAME_SIZE_WORDS_BY_HALF_FRMSIZECOD_44_1.length) {
      // Invalid values provided.
      return C.LENGTH_UNSET;
    }
    int sampleRate = SAMPLE_RATE_BY_FSCOD[fscod];
    if (sampleRate == 44100) {
      return 2 * (SYNCFRAME_SIZE_WORDS_BY_HALF_FRMSIZECOD_44_1[halfFrmsizecod] + (frmsizecod % 2));
    }
    int bitrate = BITRATE_BY_HALF_FRMSIZECOD[halfFrmsizecod];
    if (sampleRate == 32000) {
      return 6 * bitrate;
    } else { // sampleRate == 48000
      return 4 * bitrate;
    }
  }

  private Ac3Util() {}

}
