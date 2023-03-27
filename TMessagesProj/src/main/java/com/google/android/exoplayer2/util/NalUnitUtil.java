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

import static java.lang.Math.min;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Utility methods for handling H.264/AVC and H.265/HEVC NAL units. */
public final class NalUnitUtil {

  private static final String TAG = "NalUnitUtil";

  /** Coded slice of a non-IDR picture. */
  public static final int NAL_UNIT_TYPE_NON_IDR = 1;
  /** Coded slice data partition A. */
  public static final int NAL_UNIT_TYPE_PARTITION_A = 2;
  /** Coded slice of an IDR picture. */
  public static final int NAL_UNIT_TYPE_IDR = 5;
  /** Supplemental enhancement information. */
  public static final int NAL_UNIT_TYPE_SEI = 6;
  /** Sequence parameter set. */
  public static final int NAL_UNIT_TYPE_SPS = 7;
  /** Picture parameter set. */
  public static final int NAL_UNIT_TYPE_PPS = 8;
  /** Access unit delimiter. */
  public static final int NAL_UNIT_TYPE_AUD = 9;

  /** Holds data parsed from a H.264 sequence parameter set NAL unit. */
  public static final class SpsData {

    public final int profileIdc;
    public final int constraintsFlagsAndReservedZero2Bits;
    public final int levelIdc;
    public final int seqParameterSetId;
    public final int maxNumRefFrames;
    public final int width;
    public final int height;
    public final float pixelWidthHeightRatio;
    public final boolean separateColorPlaneFlag;
    public final boolean frameMbsOnlyFlag;
    public final int frameNumLength;
    public final int picOrderCountType;
    public final int picOrderCntLsbLength;
    public final boolean deltaPicOrderAlwaysZeroFlag;

    public SpsData(
        int profileIdc,
        int constraintsFlagsAndReservedZero2Bits,
        int levelIdc,
        int seqParameterSetId,
        int maxNumRefFrames,
        int width,
        int height,
        float pixelWidthHeightRatio,
        boolean separateColorPlaneFlag,
        boolean frameMbsOnlyFlag,
        int frameNumLength,
        int picOrderCountType,
        int picOrderCntLsbLength,
        boolean deltaPicOrderAlwaysZeroFlag) {
      this.profileIdc = profileIdc;
      this.constraintsFlagsAndReservedZero2Bits = constraintsFlagsAndReservedZero2Bits;
      this.levelIdc = levelIdc;
      this.seqParameterSetId = seqParameterSetId;
      this.maxNumRefFrames = maxNumRefFrames;
      this.width = width;
      this.height = height;
      this.pixelWidthHeightRatio = pixelWidthHeightRatio;
      this.separateColorPlaneFlag = separateColorPlaneFlag;
      this.frameMbsOnlyFlag = frameMbsOnlyFlag;
      this.frameNumLength = frameNumLength;
      this.picOrderCountType = picOrderCountType;
      this.picOrderCntLsbLength = picOrderCntLsbLength;
      this.deltaPicOrderAlwaysZeroFlag = deltaPicOrderAlwaysZeroFlag;
    }
  }

  /** Holds data parsed from a H.265 sequence parameter set NAL unit. */
  public static final class H265SpsData {

    public final int generalProfileSpace;
    public final boolean generalTierFlag;
    public final int generalProfileIdc;
    public final int generalProfileCompatibilityFlags;
    public final int[] constraintBytes;
    public final int generalLevelIdc;
    public final int seqParameterSetId;
    public final int width;
    public final int height;
    public final float pixelWidthHeightRatio;

    public H265SpsData(
        int generalProfileSpace,
        boolean generalTierFlag,
        int generalProfileIdc,
        int generalProfileCompatibilityFlags,
        int[] constraintBytes,
        int generalLevelIdc,
        int seqParameterSetId,
        int width,
        int height,
        float pixelWidthHeightRatio) {
      this.generalProfileSpace = generalProfileSpace;
      this.generalTierFlag = generalTierFlag;
      this.generalProfileIdc = generalProfileIdc;
      this.generalProfileCompatibilityFlags = generalProfileCompatibilityFlags;
      this.constraintBytes = constraintBytes;
      this.generalLevelIdc = generalLevelIdc;
      this.seqParameterSetId = seqParameterSetId;
      this.width = width;
      this.height = height;
      this.pixelWidthHeightRatio = pixelWidthHeightRatio;
    }
  }

  /** Holds data parsed from a picture parameter set NAL unit. */
  public static final class PpsData {

    public final int picParameterSetId;
    public final int seqParameterSetId;
    public final boolean bottomFieldPicOrderInFramePresentFlag;

    public PpsData(
        int picParameterSetId,
        int seqParameterSetId,
        boolean bottomFieldPicOrderInFramePresentFlag) {
      this.picParameterSetId = picParameterSetId;
      this.seqParameterSetId = seqParameterSetId;
      this.bottomFieldPicOrderInFramePresentFlag = bottomFieldPicOrderInFramePresentFlag;
    }
  }

  /** Four initial bytes that must prefix NAL units for decoding. */
  public static final byte[] NAL_START_CODE = new byte[] {0, 0, 0, 1};

  /** Value for aspect_ratio_idc indicating an extended aspect ratio, in H.264 and H.265 SPSs. */
  public static final int EXTENDED_SAR = 0xFF;
  /** Aspect ratios indexed by aspect_ratio_idc, in H.264 and H.265 SPSs. */
  public static final float[] ASPECT_RATIO_IDC_VALUES =
      new float[] {
        1f /* Unspecified. Assume square */,
        1f,
        12f / 11f,
        10f / 11f,
        16f / 11f,
        40f / 33f,
        24f / 11f,
        20f / 11f,
        32f / 11f,
        80f / 33f,
        18f / 11f,
        15f / 11f,
        64f / 33f,
        160f / 99f,
        4f / 3f,
        3f / 2f,
        2f
      };

  private static final int H264_NAL_UNIT_TYPE_SEI = 6; // Supplemental enhancement information
  private static final int H264_NAL_UNIT_TYPE_SPS = 7; // Sequence parameter set
  private static final int H265_NAL_UNIT_TYPE_PREFIX_SEI = 39;

  private static final Object scratchEscapePositionsLock = new Object();

  /**
   * Temporary store for positions of escape codes in {@link #unescapeStream(byte[], int)}. Guarded
   * by {@link #scratchEscapePositionsLock}.
   */
  private static int[] scratchEscapePositions = new int[10];

  /**
   * Unescapes {@code data} up to the specified limit, replacing occurrences of [0, 0, 3] with [0,
   * 0]. The unescaped data is returned in-place, with the return value indicating its length.
   *
   * <p>Executions of this method are mutually exclusive, so it should not be called with very large
   * buffers.
   *
   * @param data The data to unescape.
   * @param limit The limit (exclusive) of the data to unescape.
   * @return The length of the unescaped data.
   */
  public static int unescapeStream(byte[] data, int limit) {
    synchronized (scratchEscapePositionsLock) {
      int position = 0;
      int scratchEscapeCount = 0;
      while (position < limit) {
        position = findNextUnescapeIndex(data, position, limit);
        if (position < limit) {
          if (scratchEscapePositions.length <= scratchEscapeCount) {
            // Grow scratchEscapePositions to hold a larger number of positions.
            scratchEscapePositions =
                Arrays.copyOf(scratchEscapePositions, scratchEscapePositions.length * 2);
          }
          scratchEscapePositions[scratchEscapeCount++] = position;
          position += 3;
        }
      }

      int unescapedLength = limit - scratchEscapeCount;
      int escapedPosition = 0; // The position being read from.
      int unescapedPosition = 0; // The position being written to.
      for (int i = 0; i < scratchEscapeCount; i++) {
        int nextEscapePosition = scratchEscapePositions[i];
        int copyLength = nextEscapePosition - escapedPosition;
        System.arraycopy(data, escapedPosition, data, unescapedPosition, copyLength);
        unescapedPosition += copyLength;
        data[unescapedPosition++] = 0;
        data[unescapedPosition++] = 0;
        escapedPosition += copyLength + 3;
      }

      int remainingLength = unescapedLength - unescapedPosition;
      System.arraycopy(data, escapedPosition, data, unescapedPosition, remainingLength);
      return unescapedLength;
    }
  }

  /**
   * Discards data from the buffer up to the first SPS, where {@code data.position()} is interpreted
   * as the length of the buffer.
   *
   * <p>When the method returns, {@code data.position()} will contain the new length of the buffer.
   * If the buffer is not empty it is guaranteed to start with an SPS.
   *
   * @param data Buffer containing start code delimited NAL units.
   */
  public static void discardToSps(ByteBuffer data) {
    int length = data.position();
    int consecutiveZeros = 0;
    int offset = 0;
    while (offset + 1 < length) {
      int value = data.get(offset) & 0xFF;
      if (consecutiveZeros == 3) {
        if (value == 1 && (data.get(offset + 1) & 0x1F) == H264_NAL_UNIT_TYPE_SPS) {
          // Copy from this NAL unit onwards to the start of the buffer.
          ByteBuffer offsetData = data.duplicate();
          offsetData.position(offset - 3);
          offsetData.limit(length);
          data.position(0);
          data.put(offsetData);
          return;
        }
      } else if (value == 0) {
        consecutiveZeros++;
      }
      if (value != 0) {
        consecutiveZeros = 0;
      }
      offset++;
    }
    // Empty the buffer if the SPS NAL unit was not found.
    data.clear();
  }

  /**
   * Returns whether the NAL unit with the specified header contains supplemental enhancement
   * information.
   *
   * @param mimeType The sample MIME type, or {@code null} if unknown.
   * @param nalUnitHeaderFirstByte The first byte of nal_unit().
   * @return Whether the NAL unit with the specified header is an SEI NAL unit. False is returned if
   *     the {@code MimeType} is {@code null}.
   */
  public static boolean isNalUnitSei(@Nullable String mimeType, byte nalUnitHeaderFirstByte) {
    return (MimeTypes.VIDEO_H264.equals(mimeType)
            && (nalUnitHeaderFirstByte & 0x1F) == H264_NAL_UNIT_TYPE_SEI)
        || (MimeTypes.VIDEO_H265.equals(mimeType)
            && ((nalUnitHeaderFirstByte & 0x7E) >> 1) == H265_NAL_UNIT_TYPE_PREFIX_SEI);
  }

  /**
   * Returns the type of the NAL unit in {@code data} that starts at {@code offset}.
   *
   * @param data The data to search.
   * @param offset The start offset of a NAL unit. Must lie between {@code -3} (inclusive) and
   *     {@code data.length - 3} (exclusive).
   * @return The type of the unit.
   */
  public static int getNalUnitType(byte[] data, int offset) {
    return data[offset + 3] & 0x1F;
  }

  /**
   * Returns the type of the H.265 NAL unit in {@code data} that starts at {@code offset}.
   *
   * @param data The data to search.
   * @param offset The start offset of a NAL unit. Must lie between {@code -3} (inclusive) and
   *     {@code data.length - 3} (exclusive).
   * @return The type of the unit.
   */
  public static int getH265NalUnitType(byte[] data, int offset) {
    return (data[offset + 3] & 0x7E) >> 1;
  }

  /**
   * Parses a SPS NAL unit using the syntax defined in ITU-T Recommendation H.264 (2013) subsection
   * 7.3.2.1.1.
   *
   * @param nalData A buffer containing escaped SPS data.
   * @param nalOffset The offset of the NAL unit header in {@code nalData}.
   * @param nalLimit The limit of the NAL unit in {@code nalData}.
   * @return A parsed representation of the SPS data.
   */
  public static SpsData parseSpsNalUnit(byte[] nalData, int nalOffset, int nalLimit) {
    return parseSpsNalUnitPayload(nalData, nalOffset + 1, nalLimit);
  }

  /**
   * Parses a SPS NAL unit payload (excluding the NAL unit header) using the syntax defined in ITU-T
   * Recommendation H.264 (2013) subsection 7.3.2.1.1.
   *
   * @param nalData A buffer containing escaped SPS data.
   * @param nalOffset The offset of the NAL unit payload in {@code nalData}.
   * @param nalLimit The limit of the NAL unit in {@code nalData}.
   * @return A parsed representation of the SPS data.
   */
  public static SpsData parseSpsNalUnitPayload(byte[] nalData, int nalOffset, int nalLimit) {
    ParsableNalUnitBitArray data = new ParsableNalUnitBitArray(nalData, nalOffset, nalLimit);
    int profileIdc = data.readBits(8);
    int constraintsFlagsAndReservedZero2Bits = data.readBits(8);
    int levelIdc = data.readBits(8);
    int seqParameterSetId = data.readUnsignedExpGolombCodedInt();

    int chromaFormatIdc = 1; // Default is 4:2:0
    boolean separateColorPlaneFlag = false;
    if (profileIdc == 100
        || profileIdc == 110
        || profileIdc == 122
        || profileIdc == 244
        || profileIdc == 44
        || profileIdc == 83
        || profileIdc == 86
        || profileIdc == 118
        || profileIdc == 128
        || profileIdc == 138) {
      chromaFormatIdc = data.readUnsignedExpGolombCodedInt();
      if (chromaFormatIdc == 3) {
        separateColorPlaneFlag = data.readBit();
      }
      data.readUnsignedExpGolombCodedInt(); // bit_depth_luma_minus8
      data.readUnsignedExpGolombCodedInt(); // bit_depth_chroma_minus8
      data.skipBit(); // qpprime_y_zero_transform_bypass_flag
      boolean seqScalingMatrixPresentFlag = data.readBit();
      if (seqScalingMatrixPresentFlag) {
        int limit = (chromaFormatIdc != 3) ? 8 : 12;
        for (int i = 0; i < limit; i++) {
          boolean seqScalingListPresentFlag = data.readBit();
          if (seqScalingListPresentFlag) {
            skipScalingList(data, i < 6 ? 16 : 64);
          }
        }
      }
    }

    int frameNumLength = data.readUnsignedExpGolombCodedInt() + 4; // log2_max_frame_num_minus4 + 4
    int picOrderCntType = data.readUnsignedExpGolombCodedInt();
    int picOrderCntLsbLength = 0;
    boolean deltaPicOrderAlwaysZeroFlag = false;
    if (picOrderCntType == 0) {
      // log2_max_pic_order_cnt_lsb_minus4 + 4
      picOrderCntLsbLength = data.readUnsignedExpGolombCodedInt() + 4;
    } else if (picOrderCntType == 1) {
      deltaPicOrderAlwaysZeroFlag = data.readBit(); // delta_pic_order_always_zero_flag
      data.readSignedExpGolombCodedInt(); // offset_for_non_ref_pic
      data.readSignedExpGolombCodedInt(); // offset_for_top_to_bottom_field
      long numRefFramesInPicOrderCntCycle = data.readUnsignedExpGolombCodedInt();
      for (int i = 0; i < numRefFramesInPicOrderCntCycle; i++) {
        data.readUnsignedExpGolombCodedInt(); // offset_for_ref_frame[i]
      }
    }
    int maxNumRefFrames = data.readUnsignedExpGolombCodedInt(); // max_num_ref_frames
    data.skipBit(); // gaps_in_frame_num_value_allowed_flag

    int picWidthInMbs = data.readUnsignedExpGolombCodedInt() + 1;
    int picHeightInMapUnits = data.readUnsignedExpGolombCodedInt() + 1;
    boolean frameMbsOnlyFlag = data.readBit();
    int frameHeightInMbs = (2 - (frameMbsOnlyFlag ? 1 : 0)) * picHeightInMapUnits;
    if (!frameMbsOnlyFlag) {
      data.skipBit(); // mb_adaptive_frame_field_flag
    }

    data.skipBit(); // direct_8x8_inference_flag
    int frameWidth = picWidthInMbs * 16;
    int frameHeight = frameHeightInMbs * 16;
    boolean frameCroppingFlag = data.readBit();
    if (frameCroppingFlag) {
      int frameCropLeftOffset = data.readUnsignedExpGolombCodedInt();
      int frameCropRightOffset = data.readUnsignedExpGolombCodedInt();
      int frameCropTopOffset = data.readUnsignedExpGolombCodedInt();
      int frameCropBottomOffset = data.readUnsignedExpGolombCodedInt();
      int cropUnitX;
      int cropUnitY;
      if (chromaFormatIdc == 0) {
        cropUnitX = 1;
        cropUnitY = 2 - (frameMbsOnlyFlag ? 1 : 0);
      } else {
        int subWidthC = (chromaFormatIdc == 3) ? 1 : 2;
        int subHeightC = (chromaFormatIdc == 1) ? 2 : 1;
        cropUnitX = subWidthC;
        cropUnitY = subHeightC * (2 - (frameMbsOnlyFlag ? 1 : 0));
      }
      frameWidth -= (frameCropLeftOffset + frameCropRightOffset) * cropUnitX;
      frameHeight -= (frameCropTopOffset + frameCropBottomOffset) * cropUnitY;
    }

    float pixelWidthHeightRatio = 1;
    boolean vuiParametersPresentFlag = data.readBit();
    if (vuiParametersPresentFlag) {
      boolean aspectRatioInfoPresentFlag = data.readBit();
      if (aspectRatioInfoPresentFlag) {
        int aspectRatioIdc = data.readBits(8);
        if (aspectRatioIdc == NalUnitUtil.EXTENDED_SAR) {
          int sarWidth = data.readBits(16);
          int sarHeight = data.readBits(16);
          if (sarWidth != 0 && sarHeight != 0) {
            pixelWidthHeightRatio = (float) sarWidth / sarHeight;
          }
        } else if (aspectRatioIdc < NalUnitUtil.ASPECT_RATIO_IDC_VALUES.length) {
          pixelWidthHeightRatio = NalUnitUtil.ASPECT_RATIO_IDC_VALUES[aspectRatioIdc];
        } else {
          Log.w(TAG, "Unexpected aspect_ratio_idc value: " + aspectRatioIdc);
        }
      }
    }

    return new SpsData(
        profileIdc,
        constraintsFlagsAndReservedZero2Bits,
        levelIdc,
        seqParameterSetId,
        maxNumRefFrames,
        frameWidth,
        frameHeight,
        pixelWidthHeightRatio,
        separateColorPlaneFlag,
        frameMbsOnlyFlag,
        frameNumLength,
        picOrderCntType,
        picOrderCntLsbLength,
        deltaPicOrderAlwaysZeroFlag);
  }

  /**
   * Parses a H.265 SPS NAL unit using the syntax defined in ITU-T Recommendation H.265 (2019)
   * subsection 7.3.2.2.1.
   *
   * @param nalData A buffer containing escaped SPS data.
   * @param nalOffset The offset of the NAL unit header in {@code nalData}.
   * @param nalLimit The limit of the NAL unit in {@code nalData}.
   * @return A parsed representation of the SPS data.
   */
  public static H265SpsData parseH265SpsNalUnit(byte[] nalData, int nalOffset, int nalLimit) {
    return parseH265SpsNalUnitPayload(nalData, nalOffset + 2, nalLimit);
  }

  /**
   * Parses a H.265 SPS NAL unit payload (excluding the NAL unit header) using the syntax defined in
   * ITU-T Recommendation H.265 (2019) subsection 7.3.2.2.1.
   *
   * @param nalData A buffer containing escaped SPS data.
   * @param nalOffset The offset of the NAL unit payload in {@code nalData}.
   * @param nalLimit The limit of the NAL unit in {@code nalData}.
   * @return A parsed representation of the SPS data.
   */
  public static H265SpsData parseH265SpsNalUnitPayload(
      byte[] nalData, int nalOffset, int nalLimit) {
    ParsableNalUnitBitArray data = new ParsableNalUnitBitArray(nalData, nalOffset, nalLimit);
    data.skipBits(4); // sps_video_parameter_set_id
    int maxSubLayersMinus1 = data.readBits(3);
    data.skipBit(); // sps_temporal_id_nesting_flag
    int generalProfileSpace = data.readBits(2);
    boolean generalTierFlag = data.readBit();
    int generalProfileIdc = data.readBits(5);
    int generalProfileCompatibilityFlags = 0;
    for (int i = 0; i < 32; i++) {
      if (data.readBit()) {
        generalProfileCompatibilityFlags |= (1 << i);
      }
    }
    int[] constraintBytes = new int[6];
    for (int i = 0; i < constraintBytes.length; ++i) {
      constraintBytes[i] = data.readBits(8);
    }
    int generalLevelIdc = data.readBits(8);
    int toSkip = 0;
    for (int i = 0; i < maxSubLayersMinus1; i++) {
      if (data.readBit()) { // sub_layer_profile_present_flag[i]
        toSkip += 89;
      }
      if (data.readBit()) { // sub_layer_level_present_flag[i]
        toSkip += 8;
      }
    }
    data.skipBits(toSkip);
    if (maxSubLayersMinus1 > 0) {
      data.skipBits(2 * (8 - maxSubLayersMinus1));
    }
    int seqParameterSetId = data.readUnsignedExpGolombCodedInt();
    int chromaFormatIdc = data.readUnsignedExpGolombCodedInt();
    if (chromaFormatIdc == 3) {
      data.skipBit(); // separate_colour_plane_flag
    }
    int frameWidth = data.readUnsignedExpGolombCodedInt();
    int frameHeight = data.readUnsignedExpGolombCodedInt();
    if (data.readBit()) { // conformance_window_flag
      int confWinLeftOffset = data.readUnsignedExpGolombCodedInt();
      int confWinRightOffset = data.readUnsignedExpGolombCodedInt();
      int confWinTopOffset = data.readUnsignedExpGolombCodedInt();
      int confWinBottomOffset = data.readUnsignedExpGolombCodedInt();
      // H.265/HEVC (2014) Table 6-1
      int subWidthC = chromaFormatIdc == 1 || chromaFormatIdc == 2 ? 2 : 1;
      int subHeightC = chromaFormatIdc == 1 ? 2 : 1;
      frameWidth -= subWidthC * (confWinLeftOffset + confWinRightOffset);
      frameHeight -= subHeightC * (confWinTopOffset + confWinBottomOffset);
    }
    data.readUnsignedExpGolombCodedInt(); // bit_depth_luma_minus8
    data.readUnsignedExpGolombCodedInt(); // bit_depth_chroma_minus8
    int log2MaxPicOrderCntLsbMinus4 = data.readUnsignedExpGolombCodedInt();
    // for (i = sps_sub_layer_ordering_info_present_flag ? 0 : sps_max_sub_layers_minus1; ...)
    for (int i = data.readBit() ? 0 : maxSubLayersMinus1; i <= maxSubLayersMinus1; i++) {
      data.readUnsignedExpGolombCodedInt(); // sps_max_dec_pic_buffering_minus1[i]
      data.readUnsignedExpGolombCodedInt(); // sps_max_num_reorder_pics[i]
      data.readUnsignedExpGolombCodedInt(); // sps_max_latency_increase_plus1[i]
    }
    data.readUnsignedExpGolombCodedInt(); // log2_min_luma_coding_block_size_minus3
    data.readUnsignedExpGolombCodedInt(); // log2_diff_max_min_luma_coding_block_size
    data.readUnsignedExpGolombCodedInt(); // log2_min_luma_transform_block_size_minus2
    data.readUnsignedExpGolombCodedInt(); // log2_diff_max_min_luma_transform_block_size
    data.readUnsignedExpGolombCodedInt(); // max_transform_hierarchy_depth_inter
    data.readUnsignedExpGolombCodedInt(); // max_transform_hierarchy_depth_intra
    // if (scaling_list_enabled_flag) { if (sps_scaling_list_data_present_flag) {...}}
    boolean scalingListEnabled = data.readBit();
    if (scalingListEnabled && data.readBit()) {
      skipH265ScalingList(data);
    }
    data.skipBits(2); // amp_enabled_flag (1), sample_adaptive_offset_enabled_flag (1)
    if (data.readBit()) { // pcm_enabled_flag
      // pcm_sample_bit_depth_luma_minus1 (4), pcm_sample_bit_depth_chroma_minus1 (4)
      data.skipBits(8);
      data.readUnsignedExpGolombCodedInt(); // log2_min_pcm_luma_coding_block_size_minus3
      data.readUnsignedExpGolombCodedInt(); // log2_diff_max_min_pcm_luma_coding_block_size
      data.skipBit(); // pcm_loop_filter_disabled_flag
    }
    skipShortTermReferencePictureSets(data);
    if (data.readBit()) { // long_term_ref_pics_present_flag
      // num_long_term_ref_pics_sps
      for (int i = 0; i < data.readUnsignedExpGolombCodedInt(); i++) {
        int ltRefPicPocLsbSpsLength = log2MaxPicOrderCntLsbMinus4 + 4;
        // lt_ref_pic_poc_lsb_sps[i], used_by_curr_pic_lt_sps_flag[i]
        data.skipBits(ltRefPicPocLsbSpsLength + 1);
      }
    }
    data.skipBits(2); // sps_temporal_mvp_enabled_flag, strong_intra_smoothing_enabled_flag
    float pixelWidthHeightRatio = 1;
    if (data.readBit()) { // vui_parameters_present_flag
      if (data.readBit()) { // aspect_ratio_info_present_flag
        int aspectRatioIdc = data.readBits(8);
        if (aspectRatioIdc == NalUnitUtil.EXTENDED_SAR) {
          int sarWidth = data.readBits(16);
          int sarHeight = data.readBits(16);
          if (sarWidth != 0 && sarHeight != 0) {
            pixelWidthHeightRatio = (float) sarWidth / sarHeight;
          }
        } else if (aspectRatioIdc < NalUnitUtil.ASPECT_RATIO_IDC_VALUES.length) {
          pixelWidthHeightRatio = NalUnitUtil.ASPECT_RATIO_IDC_VALUES[aspectRatioIdc];
        } else {
          Log.w(TAG, "Unexpected aspect_ratio_idc value: " + aspectRatioIdc);
        }
      }
      if (data.readBit()) { // overscan_info_present_flag
        data.skipBit(); // overscan_appropriate_flag
      }
      if (data.readBit()) { // video_signal_type_present_flag
        data.skipBits(4); // video_format, video_full_range_flag
        if (data.readBit()) { // colour_description_present_flag
          // colour_primaries, transfer_characteristics, matrix_coeffs
          data.skipBits(24);
        }
      }
      if (data.readBit()) { // chroma_loc_info_present_flag
        data.readUnsignedExpGolombCodedInt(); // chroma_sample_loc_type_top_field
        data.readUnsignedExpGolombCodedInt(); // chroma_sample_loc_type_bottom_field
      }
      data.skipBit(); // neutral_chroma_indication_flag
      if (data.readBit()) { // field_seq_flag
        // field_seq_flag equal to 1 indicates that the coded video sequence conveys pictures that
        // represent fields, which means that frame height is double the picture height.
        frameHeight *= 2;
      }
    }

    return new H265SpsData(
        generalProfileSpace,
        generalTierFlag,
        generalProfileIdc,
        generalProfileCompatibilityFlags,
        constraintBytes,
        generalLevelIdc,
        seqParameterSetId,
        frameWidth,
        frameHeight,
        pixelWidthHeightRatio);
  }

  /**
   * Parses a PPS NAL unit using the syntax defined in ITU-T Recommendation H.264 (2013) subsection
   * 7.3.2.2.
   *
   * @param nalData A buffer containing escaped PPS data.
   * @param nalOffset The offset of the NAL unit header in {@code nalData}.
   * @param nalLimit The limit of the NAL unit in {@code nalData}.
   * @return A parsed representation of the PPS data.
   */
  public static PpsData parsePpsNalUnit(byte[] nalData, int nalOffset, int nalLimit) {
    return parsePpsNalUnitPayload(nalData, nalOffset + 1, nalLimit);
  }

  /**
   * Parses a PPS NAL unit payload (excluding the NAL unit header) using the syntax defined in ITU-T
   * Recommendation H.264 (2013) subsection 7.3.2.2.
   *
   * @param nalData A buffer containing escaped PPS data.
   * @param nalOffset The offset of the NAL unit payload in {@code nalData}.
   * @param nalLimit The limit of the NAL unit in {@code nalData}.
   * @return A parsed representation of the PPS data.
   */
  public static PpsData parsePpsNalUnitPayload(byte[] nalData, int nalOffset, int nalLimit) {
    ParsableNalUnitBitArray data = new ParsableNalUnitBitArray(nalData, nalOffset, nalLimit);
    int picParameterSetId = data.readUnsignedExpGolombCodedInt();
    int seqParameterSetId = data.readUnsignedExpGolombCodedInt();
    data.skipBit(); // entropy_coding_mode_flag
    boolean bottomFieldPicOrderInFramePresentFlag = data.readBit();
    return new PpsData(picParameterSetId, seqParameterSetId, bottomFieldPicOrderInFramePresentFlag);
  }

  /**
   * Finds the first NAL unit in {@code data}.
   *
   * <p>If {@code prefixFlags} is null then the first three bytes of a NAL unit must be entirely
   * contained within the part of the array being searched in order for it to be found.
   *
   * <p>When {@code prefixFlags} is non-null, this method supports finding NAL units whose first
   * four bytes span {@code data} arrays passed to successive calls. To use this feature, pass the
   * same {@code prefixFlags} parameter to successive calls. State maintained in this parameter
   * enables the detection of such NAL units. Note that when using this feature, the return value
   * may be 3, 2 or 1 less than {@code startOffset}, to indicate a NAL unit starting 3, 2 or 1 bytes
   * before the first byte in the current array.
   *
   * @param data The data to search.
   * @param startOffset The offset (inclusive) in the data to start the search.
   * @param endOffset The offset (exclusive) in the data to end the search.
   * @param prefixFlags A boolean array whose first three elements are used to store the state
   *     required to detect NAL units where the NAL unit prefix spans array boundaries. The array
   *     must be at least 3 elements long.
   * @return The offset of the NAL unit, or {@code endOffset} if a NAL unit was not found.
   */
  public static int findNalUnit(
      byte[] data, int startOffset, int endOffset, boolean[] prefixFlags) {
    int length = endOffset - startOffset;

    Assertions.checkState(length >= 0);
    if (length == 0) {
      return endOffset;
    }

    if (prefixFlags[0]) {
      clearPrefixFlags(prefixFlags);
      return startOffset - 3;
    } else if (length > 1 && prefixFlags[1] && data[startOffset] == 1) {
      clearPrefixFlags(prefixFlags);
      return startOffset - 2;
    } else if (length > 2
        && prefixFlags[2]
        && data[startOffset] == 0
        && data[startOffset + 1] == 1) {
      clearPrefixFlags(prefixFlags);
      return startOffset - 1;
    }

    int limit = endOffset - 1;
    // We're looking for the NAL unit start code prefix 0x000001. The value of i tracks the index of
    // the third byte.
    for (int i = startOffset + 2; i < limit; i += 3) {
      if ((data[i] & 0xFE) != 0) {
        // There isn't a NAL prefix here, or at the next two positions. Do nothing and let the
        // loop advance the index by three.
      } else if (data[i - 2] == 0 && data[i - 1] == 0 && data[i] == 1) {
        clearPrefixFlags(prefixFlags);
        return i - 2;
      } else {
        // There isn't a NAL prefix here, but there might be at the next position. We should
        // only skip forward by one. The loop will skip forward by three, so subtract two here.
        i -= 2;
      }
    }

    // True if the last three bytes in the data seen so far are {0,0,1}.
    prefixFlags[0] =
        length > 2
            ? (data[endOffset - 3] == 0 && data[endOffset - 2] == 0 && data[endOffset - 1] == 1)
            : length == 2
                ? (prefixFlags[2] && data[endOffset - 2] == 0 && data[endOffset - 1] == 1)
                : (prefixFlags[1] && data[endOffset - 1] == 1);
    // True if the last two bytes in the data seen so far are {0,0}.
    prefixFlags[1] =
        length > 1
            ? data[endOffset - 2] == 0 && data[endOffset - 1] == 0
            : prefixFlags[2] && data[endOffset - 1] == 0;
    // True if the last byte in the data seen so far is {0}.
    prefixFlags[2] = data[endOffset - 1] == 0;

    return endOffset;
  }

  /**
   * Clears prefix flags, as used by {@link #findNalUnit(byte[], int, int, boolean[])}.
   *
   * @param prefixFlags The flags to clear.
   */
  public static void clearPrefixFlags(boolean[] prefixFlags) {
    prefixFlags[0] = false;
    prefixFlags[1] = false;
    prefixFlags[2] = false;
  }

  private static int findNextUnescapeIndex(byte[] bytes, int offset, int limit) {
    for (int i = offset; i < limit - 2; i++) {
      if (bytes[i] == 0x00 && bytes[i + 1] == 0x00 && bytes[i + 2] == 0x03) {
        return i;
      }
    }
    return limit;
  }

  private static void skipScalingList(ParsableNalUnitBitArray bitArray, int size) {
    int lastScale = 8;
    int nextScale = 8;
    for (int i = 0; i < size; i++) {
      if (nextScale != 0) {
        int deltaScale = bitArray.readSignedExpGolombCodedInt();
        nextScale = (lastScale + deltaScale + 256) % 256;
      }
      lastScale = (nextScale == 0) ? lastScale : nextScale;
    }
  }

  private static void skipH265ScalingList(ParsableNalUnitBitArray bitArray) {
    for (int sizeId = 0; sizeId < 4; sizeId++) {
      for (int matrixId = 0; matrixId < 6; matrixId += sizeId == 3 ? 3 : 1) {
        if (!bitArray.readBit()) { // scaling_list_pred_mode_flag[sizeId][matrixId]
          // scaling_list_pred_matrix_id_delta[sizeId][matrixId]
          bitArray.readUnsignedExpGolombCodedInt();
        } else {
          int coefNum = min(64, 1 << (4 + (sizeId << 1)));
          if (sizeId > 1) {
            // scaling_list_dc_coef_minus8[sizeId - 2][matrixId]
            bitArray.readSignedExpGolombCodedInt();
          }
          for (int i = 0; i < coefNum; i++) {
            bitArray.readSignedExpGolombCodedInt(); // scaling_list_delta_coef
          }
        }
      }
    }
  }

  /**
   * Skips any short term reference picture sets contained in a SPS.
   *
   * <p>Note: The st_ref_pic_set parsing in this method is simplified for the case where they're
   * contained in a SPS, and would need generalizing for use elsewhere.
   */
  private static void skipShortTermReferencePictureSets(ParsableNalUnitBitArray bitArray) {
    int numShortTermRefPicSets = bitArray.readUnsignedExpGolombCodedInt();
    // As this method applies in a SPS, each short term reference picture set only accesses data
    // from the previous one. This is because RefRpsIdx = stRpsIdx - (delta_idx_minus1 + 1), and
    // delta_idx_minus1 is always zero in a SPS. Hence we just keep track of variables from the
    // previous one as we iterate.
    int previousNumNegativePics = C.INDEX_UNSET;
    int previousNumPositivePics = C.INDEX_UNSET;
    int[] previousDeltaPocS0 = new int[0];
    int[] previousDeltaPocS1 = new int[0];
    for (int stRpsIdx = 0; stRpsIdx < numShortTermRefPicSets; stRpsIdx++) {
      int numNegativePics;
      int numPositivePics;
      int[] deltaPocS0;
      int[] deltaPocS1;

      boolean interRefPicSetPredictionFlag = stRpsIdx != 0 && bitArray.readBit();
      if (interRefPicSetPredictionFlag) {
        int previousNumDeltaPocs = previousNumNegativePics + previousNumPositivePics;

        int deltaRpsSign = bitArray.readBit() ? 1 : 0;
        int absDeltaRps = bitArray.readUnsignedExpGolombCodedInt() + 1;
        int deltaRps = (1 - 2 * deltaRpsSign) * absDeltaRps;

        boolean[] useDeltaFlags = new boolean[previousNumDeltaPocs + 1];
        for (int j = 0; j <= previousNumDeltaPocs; j++) {
          if (!bitArray.readBit()) { // used_by_curr_pic_flag[j]
            useDeltaFlags[j] = bitArray.readBit();
          } else {
            // When use_delta_flag[j] is not present, its value is 1.
            useDeltaFlags[j] = true;
          }
        }

        // Derive numNegativePics, numPositivePics, deltaPocS0 and deltaPocS1 as per Rec. ITU-T
        // H.265 v6 (06/2019) Section 7.4.8
        int i = 0;
        deltaPocS0 = new int[previousNumDeltaPocs + 1];
        deltaPocS1 = new int[previousNumDeltaPocs + 1];
        for (int j = previousNumPositivePics - 1; j >= 0; j--) {
          int dPoc = previousDeltaPocS1[j] + deltaRps;
          if (dPoc < 0 && useDeltaFlags[previousNumNegativePics + j]) {
            deltaPocS0[i++] = dPoc;
          }
        }
        if (deltaRps < 0 && useDeltaFlags[previousNumDeltaPocs]) {
          deltaPocS0[i++] = deltaRps;
        }
        for (int j = 0; j < previousNumNegativePics; j++) {
          int dPoc = previousDeltaPocS0[j] + deltaRps;
          if (dPoc < 0 && useDeltaFlags[j]) {
            deltaPocS0[i++] = dPoc;
          }
        }
        numNegativePics = i;
        deltaPocS0 = Arrays.copyOf(deltaPocS0, numNegativePics);

        i = 0;
        for (int j = previousNumNegativePics - 1; j >= 0; j--) {
          int dPoc = previousDeltaPocS0[j] + deltaRps;
          if (dPoc > 0 && useDeltaFlags[j]) {
            deltaPocS1[i++] = dPoc;
          }
        }
        if (deltaRps > 0 && useDeltaFlags[previousNumDeltaPocs]) {
          deltaPocS1[i++] = deltaRps;
        }
        for (int j = 0; j < previousNumPositivePics; j++) {
          int dPoc = previousDeltaPocS1[j] + deltaRps;
          if (dPoc > 0 && useDeltaFlags[previousNumNegativePics + j]) {
            deltaPocS1[i++] = dPoc;
          }
        }
        numPositivePics = i;
        deltaPocS1 = Arrays.copyOf(deltaPocS1, numPositivePics);
      } else {
        numNegativePics = bitArray.readUnsignedExpGolombCodedInt();
        numPositivePics = bitArray.readUnsignedExpGolombCodedInt();
        deltaPocS0 = new int[numNegativePics];
        for (int i = 0; i < numNegativePics; i++) {
          deltaPocS0[i] = bitArray.readUnsignedExpGolombCodedInt() + 1;
          bitArray.skipBit(); // used_by_curr_pic_s0_flag[i]
        }
        deltaPocS1 = new int[numPositivePics];
        for (int i = 0; i < numPositivePics; i++) {
          deltaPocS1[i] = bitArray.readUnsignedExpGolombCodedInt() + 1;
          bitArray.skipBit(); // used_by_curr_pic_s1_flag[i]
        }
      }
      previousNumNegativePics = numNegativePics;
      previousNumPositivePics = numPositivePics;
      previousDeltaPocS0 = deltaPocS0;
      previousDeltaPocS1 = deltaPocS1;
    }
  }

  private NalUnitUtil() {
    // Prevent instantiation.
  }
}
