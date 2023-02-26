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
package com.google.android.exoplayer2.extractor.ts;

import static java.lang.Math.min;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.CodecSpecificDataUtil;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.ParsableNalUnitBitArray;
import com.google.android.exoplayer2.util.Util;
import java.util.Collections;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** Parses a continuous H.265 byte stream and extracts individual frames. */
public final class H265Reader implements ElementaryStreamReader {

  private static final String TAG = "H265Reader";

  // nal_unit_type values from H.265/HEVC (2014) Table 7-1.
  private static final int RASL_R = 9;
  private static final int BLA_W_LP = 16;
  private static final int CRA_NUT = 21;
  private static final int VPS_NUT = 32;
  private static final int SPS_NUT = 33;
  private static final int PPS_NUT = 34;
  private static final int AUD_NUT = 35;
  private static final int PREFIX_SEI_NUT = 39;
  private static final int SUFFIX_SEI_NUT = 40;

  private final SeiReader seiReader;

  private @MonotonicNonNull String formatId;
  private @MonotonicNonNull TrackOutput output;
  private @MonotonicNonNull SampleReader sampleReader;

  // State that should not be reset on seek.
  private boolean hasOutputFormat;

  // State that should be reset on seek.
  private final boolean[] prefixFlags;
  private final NalUnitTargetBuffer vps;
  private final NalUnitTargetBuffer sps;
  private final NalUnitTargetBuffer pps;
  private final NalUnitTargetBuffer prefixSei;
  private final NalUnitTargetBuffer suffixSei;
  private long totalBytesWritten;

  // Per packet state that gets reset at the start of each packet.
  private long pesTimeUs;

  // Scratch variables to avoid allocations.
  private final ParsableByteArray seiWrapper;

  /**
   * @param seiReader An SEI reader for consuming closed caption channels.
   */
  public H265Reader(SeiReader seiReader) {
    this.seiReader = seiReader;
    prefixFlags = new boolean[3];
    vps = new NalUnitTargetBuffer(VPS_NUT, 128);
    sps = new NalUnitTargetBuffer(SPS_NUT, 128);
    pps = new NalUnitTargetBuffer(PPS_NUT, 128);
    prefixSei = new NalUnitTargetBuffer(PREFIX_SEI_NUT, 128);
    suffixSei = new NalUnitTargetBuffer(SUFFIX_SEI_NUT, 128);
    pesTimeUs = C.TIME_UNSET;
    seiWrapper = new ParsableByteArray();
  }

  @Override
  public void seek() {
    totalBytesWritten = 0;
    pesTimeUs = C.TIME_UNSET;
    NalUnitUtil.clearPrefixFlags(prefixFlags);
    vps.reset();
    sps.reset();
    pps.reset();
    prefixSei.reset();
    suffixSei.reset();
    if (sampleReader != null) {
      sampleReader.reset();
    }
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
    idGenerator.generateNewId();
    formatId = idGenerator.getFormatId();
    output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_VIDEO);
    sampleReader = new SampleReader(output);
    seiReader.createTracks(extractorOutput, idGenerator);
  }

  @Override
  public void packetStarted(long pesTimeUs, @TsPayloadReader.Flags int flags) {
    // TODO (Internal b/32267012): Consider using random access indicator.
    if (pesTimeUs != C.TIME_UNSET) {
      this.pesTimeUs = pesTimeUs;
    }
  }

  @Override
  public void consume(ParsableByteArray data) {
    assertTracksCreated();

    while (data.bytesLeft() > 0) {
      int offset = data.getPosition();
      int limit = data.limit();
      byte[] dataArray = data.getData();

      // Append the data to the buffer.
      totalBytesWritten += data.bytesLeft();
      output.sampleData(data, data.bytesLeft());

      // Scan the appended data, processing NAL units as they are encountered
      while (offset < limit) {
        int nalUnitOffset = NalUnitUtil.findNalUnit(dataArray, offset, limit, prefixFlags);

        if (nalUnitOffset == limit) {
          // We've scanned to the end of the data without finding the start of another NAL unit.
          nalUnitData(dataArray, offset, limit);
          return;
        }

        // We've seen the start of a NAL unit of the following type.
        int nalUnitType = NalUnitUtil.getH265NalUnitType(dataArray, nalUnitOffset);

        // This is the number of bytes from the current offset to the start of the next NAL unit.
        // It may be negative if the NAL unit started in the previously consumed data.
        int lengthToNalUnit = nalUnitOffset - offset;
        if (lengthToNalUnit > 0) {
          nalUnitData(dataArray, offset, nalUnitOffset);
        }

        int bytesWrittenPastPosition = limit - nalUnitOffset;
        long absolutePosition = totalBytesWritten - bytesWrittenPastPosition;
        // Indicate the end of the previous NAL unit. If the length to the start of the next unit
        // is negative then we wrote too many bytes to the NAL buffers. Discard the excess bytes
        // when notifying that the unit has ended.
        endNalUnit(
            absolutePosition,
            bytesWrittenPastPosition,
            lengthToNalUnit < 0 ? -lengthToNalUnit : 0,
            pesTimeUs);
        // Indicate the start of the next NAL unit.
        startNalUnit(absolutePosition, bytesWrittenPastPosition, nalUnitType, pesTimeUs);
        // Continue scanning the data.
        offset = nalUnitOffset + 3;
      }
    }
  }

  @Override
  public void packetFinished() {
    // Do nothing.
  }

  @RequiresNonNull("sampleReader")
  private void startNalUnit(long position, int offset, int nalUnitType, long pesTimeUs) {
    sampleReader.startNalUnit(position, offset, nalUnitType, pesTimeUs, hasOutputFormat);
    if (!hasOutputFormat) {
      vps.startNalUnit(nalUnitType);
      sps.startNalUnit(nalUnitType);
      pps.startNalUnit(nalUnitType);
    }
    prefixSei.startNalUnit(nalUnitType);
    suffixSei.startNalUnit(nalUnitType);
  }

  @RequiresNonNull("sampleReader")
  private void nalUnitData(byte[] dataArray, int offset, int limit) {
    sampleReader.readNalUnitData(dataArray, offset, limit);
    if (!hasOutputFormat) {
      vps.appendToNalUnit(dataArray, offset, limit);
      sps.appendToNalUnit(dataArray, offset, limit);
      pps.appendToNalUnit(dataArray, offset, limit);
    }
    prefixSei.appendToNalUnit(dataArray, offset, limit);
    suffixSei.appendToNalUnit(dataArray, offset, limit);
  }

  @RequiresNonNull({"output", "sampleReader"})
  private void endNalUnit(long position, int offset, int discardPadding, long pesTimeUs) {
    sampleReader.endNalUnit(position, offset, hasOutputFormat);
    if (!hasOutputFormat) {
      vps.endNalUnit(discardPadding);
      sps.endNalUnit(discardPadding);
      pps.endNalUnit(discardPadding);
      if (vps.isCompleted() && sps.isCompleted() && pps.isCompleted()) {
        output.format(parseMediaFormat(formatId, vps, sps, pps));
        hasOutputFormat = true;
      }
    }
    if (prefixSei.endNalUnit(discardPadding)) {
      int unescapedLength = NalUnitUtil.unescapeStream(prefixSei.nalData, prefixSei.nalLength);
      seiWrapper.reset(prefixSei.nalData, unescapedLength);

      // Skip the NAL prefix and type.
      seiWrapper.skipBytes(5);
      seiReader.consume(pesTimeUs, seiWrapper);
    }
    if (suffixSei.endNalUnit(discardPadding)) {
      int unescapedLength = NalUnitUtil.unescapeStream(suffixSei.nalData, suffixSei.nalLength);
      seiWrapper.reset(suffixSei.nalData, unescapedLength);

      // Skip the NAL prefix and type.
      seiWrapper.skipBytes(5);
      seiReader.consume(pesTimeUs, seiWrapper);
    }
  }

  private static Format parseMediaFormat(
      @Nullable String formatId,
      NalUnitTargetBuffer vps,
      NalUnitTargetBuffer sps,
      NalUnitTargetBuffer pps) {
    // Build codec-specific data.
    byte[] csdData = new byte[vps.nalLength + sps.nalLength + pps.nalLength];
    System.arraycopy(vps.nalData, 0, csdData, 0, vps.nalLength);
    System.arraycopy(sps.nalData, 0, csdData, vps.nalLength, sps.nalLength);
    System.arraycopy(pps.nalData, 0, csdData, vps.nalLength + sps.nalLength, pps.nalLength);

    // Parse the SPS NAL unit, as per H.265/HEVC (2014) 7.3.2.2.1.
    ParsableNalUnitBitArray bitArray = new ParsableNalUnitBitArray(sps.nalData, 0, sps.nalLength);
    bitArray.skipBits(40 + 4); // NAL header, sps_video_parameter_set_id
    int maxSubLayersMinus1 = bitArray.readBits(3);
    bitArray.skipBit(); // sps_temporal_id_nesting_flag
    int generalProfileSpace = bitArray.readBits(2);
    boolean generalTierFlag = bitArray.readBit();
    int generalProfileIdc = bitArray.readBits(5);
    int generalProfileCompatibilityFlags = 0;
    for (int i = 0; i < 32; i++) {
      if (bitArray.readBit()) {
        generalProfileCompatibilityFlags |= (1 << i);
      }
    }
    int[] constraintBytes = new int[6];
    for (int i = 0; i < constraintBytes.length; ++i) {
      constraintBytes[i] = bitArray.readBits(8);
    }
    int generalLevelIdc = bitArray.readBits(8);
    int toSkip = 0;
    for (int i = 0; i < maxSubLayersMinus1; i++) {
      if (bitArray.readBit()) { // sub_layer_profile_present_flag[i]
        toSkip += 89;
      }
      if (bitArray.readBit()) { // sub_layer_level_present_flag[i]
        toSkip += 8;
      }
    }
    bitArray.skipBits(toSkip);
    if (maxSubLayersMinus1 > 0) {
      bitArray.skipBits(2 * (8 - maxSubLayersMinus1));
    }

    bitArray.readUnsignedExpGolombCodedInt(); // sps_seq_parameter_set_id
    int chromaFormatIdc = bitArray.readUnsignedExpGolombCodedInt();
    if (chromaFormatIdc == 3) {
      bitArray.skipBit(); // separate_colour_plane_flag
    }
    int picWidthInLumaSamples = bitArray.readUnsignedExpGolombCodedInt();
    int picHeightInLumaSamples = bitArray.readUnsignedExpGolombCodedInt();
    if (bitArray.readBit()) { // conformance_window_flag
      int confWinLeftOffset = bitArray.readUnsignedExpGolombCodedInt();
      int confWinRightOffset = bitArray.readUnsignedExpGolombCodedInt();
      int confWinTopOffset = bitArray.readUnsignedExpGolombCodedInt();
      int confWinBottomOffset = bitArray.readUnsignedExpGolombCodedInt();
      // H.265/HEVC (2014) Table 6-1
      int subWidthC = chromaFormatIdc == 1 || chromaFormatIdc == 2 ? 2 : 1;
      int subHeightC = chromaFormatIdc == 1 ? 2 : 1;
      picWidthInLumaSamples -= subWidthC * (confWinLeftOffset + confWinRightOffset);
      picHeightInLumaSamples -= subHeightC * (confWinTopOffset + confWinBottomOffset);
    }
    bitArray.readUnsignedExpGolombCodedInt(); // bit_depth_luma_minus8
    bitArray.readUnsignedExpGolombCodedInt(); // bit_depth_chroma_minus8
    int log2MaxPicOrderCntLsbMinus4 = bitArray.readUnsignedExpGolombCodedInt();
    // for (i = sps_sub_layer_ordering_info_present_flag ? 0 : sps_max_sub_layers_minus1; ...)
    for (int i = bitArray.readBit() ? 0 : maxSubLayersMinus1; i <= maxSubLayersMinus1; i++) {
      bitArray.readUnsignedExpGolombCodedInt(); // sps_max_dec_pic_buffering_minus1[i]
      bitArray.readUnsignedExpGolombCodedInt(); // sps_max_num_reorder_pics[i]
      bitArray.readUnsignedExpGolombCodedInt(); // sps_max_latency_increase_plus1[i]
    }
    bitArray.readUnsignedExpGolombCodedInt(); // log2_min_luma_coding_block_size_minus3
    bitArray.readUnsignedExpGolombCodedInt(); // log2_diff_max_min_luma_coding_block_size
    bitArray.readUnsignedExpGolombCodedInt(); // log2_min_luma_transform_block_size_minus2
    bitArray.readUnsignedExpGolombCodedInt(); // log2_diff_max_min_luma_transform_block_size
    bitArray.readUnsignedExpGolombCodedInt(); // max_transform_hierarchy_depth_inter
    bitArray.readUnsignedExpGolombCodedInt(); // max_transform_hierarchy_depth_intra
    // if (scaling_list_enabled_flag) { if (sps_scaling_list_data_present_flag) {...}}
    boolean scalingListEnabled = bitArray.readBit();
    if (scalingListEnabled && bitArray.readBit()) {
      skipScalingList(bitArray);
    }
    bitArray.skipBits(2); // amp_enabled_flag (1), sample_adaptive_offset_enabled_flag (1)
    if (bitArray.readBit()) { // pcm_enabled_flag
      // pcm_sample_bit_depth_luma_minus1 (4), pcm_sample_bit_depth_chroma_minus1 (4)
      bitArray.skipBits(8);
      bitArray.readUnsignedExpGolombCodedInt(); // log2_min_pcm_luma_coding_block_size_minus3
      bitArray.readUnsignedExpGolombCodedInt(); // log2_diff_max_min_pcm_luma_coding_block_size
      bitArray.skipBit(); // pcm_loop_filter_disabled_flag
    }
    // Skips all short term reference picture sets.
    skipShortTermRefPicSets(bitArray);
    if (bitArray.readBit()) { // long_term_ref_pics_present_flag
      // num_long_term_ref_pics_sps
      for (int i = 0; i < bitArray.readUnsignedExpGolombCodedInt(); i++) {
        int ltRefPicPocLsbSpsLength = log2MaxPicOrderCntLsbMinus4 + 4;
        // lt_ref_pic_poc_lsb_sps[i], used_by_curr_pic_lt_sps_flag[i]
        bitArray.skipBits(ltRefPicPocLsbSpsLength + 1);
      }
    }
    bitArray.skipBits(2); // sps_temporal_mvp_enabled_flag, strong_intra_smoothing_enabled_flag
    float pixelWidthHeightRatio = 1;
    if (bitArray.readBit()) { // vui_parameters_present_flag
      if (bitArray.readBit()) { // aspect_ratio_info_present_flag
        int aspectRatioIdc = bitArray.readBits(8);
        if (aspectRatioIdc == NalUnitUtil.EXTENDED_SAR) {
          int sarWidth = bitArray.readBits(16);
          int sarHeight = bitArray.readBits(16);
          if (sarWidth != 0 && sarHeight != 0) {
            pixelWidthHeightRatio = (float) sarWidth / sarHeight;
          }
        } else if (aspectRatioIdc < NalUnitUtil.ASPECT_RATIO_IDC_VALUES.length) {
          pixelWidthHeightRatio = NalUnitUtil.ASPECT_RATIO_IDC_VALUES[aspectRatioIdc];
        } else {
          Log.w(TAG, "Unexpected aspect_ratio_idc value: " + aspectRatioIdc);
        }
      }
      if (bitArray.readBit()) { // overscan_info_present_flag
        bitArray.skipBit(); // overscan_appropriate_flag
      }
      if (bitArray.readBit()) { // video_signal_type_present_flag
        bitArray.skipBits(4); // video_format, video_full_range_flag
        if (bitArray.readBit()) { // colour_description_present_flag
          // colour_primaries, transfer_characteristics, matrix_coeffs
          bitArray.skipBits(24);
        }
      }
      if (bitArray.readBit()) { // chroma_loc_info_present_flag
        bitArray.readUnsignedExpGolombCodedInt(); // chroma_sample_loc_type_top_field
        bitArray.readUnsignedExpGolombCodedInt(); // chroma_sample_loc_type_bottom_field
      }
      bitArray.skipBit(); // neutral_chroma_indication_flag
      if (bitArray.readBit()) { // field_seq_flag
        // field_seq_flag equal to 1 indicates that the coded video sequence conveys pictures that
        // represent fields, which means that frame height is double the picture height.
        picHeightInLumaSamples *= 2;
      }
    }

    String codecs =
        CodecSpecificDataUtil.buildHevcCodecString(
            generalProfileSpace,
            generalTierFlag,
            generalProfileIdc,
            generalProfileCompatibilityFlags,
            constraintBytes,
            generalLevelIdc);

    return new Format.Builder()
        .setId(formatId)
        .setSampleMimeType(MimeTypes.VIDEO_H265)
        .setCodecs(codecs)
        .setWidth(picWidthInLumaSamples)
        .setHeight(picHeightInLumaSamples)
        .setPixelWidthHeightRatio(pixelWidthHeightRatio)
        .setInitializationData(Collections.singletonList(csdData))
        .build();
  }

  /** Skips scaling_list_data(). See H.265/HEVC (2014) 7.3.4. */
  private static void skipScalingList(ParsableNalUnitBitArray bitArray) {
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
   * Reads the number of short term reference picture sets in a SPS as ue(v), then skips all of
   * them. See H.265/HEVC (2014) 7.3.7.
   */
  private static void skipShortTermRefPicSets(ParsableNalUnitBitArray bitArray) {
    int numShortTermRefPicSets = bitArray.readUnsignedExpGolombCodedInt();
    boolean interRefPicSetPredictionFlag = false;
    int numNegativePics;
    int numPositivePics;
    // As this method applies in a SPS, the only element of NumDeltaPocs accessed is the previous
    // one, so we just keep track of that rather than storing the whole array.
    // RefRpsIdx = stRpsIdx - (delta_idx_minus1 + 1) and delta_idx_minus1 is always zero in SPS.
    int previousNumDeltaPocs = 0;
    for (int stRpsIdx = 0; stRpsIdx < numShortTermRefPicSets; stRpsIdx++) {
      if (stRpsIdx != 0) {
        interRefPicSetPredictionFlag = bitArray.readBit();
      }
      if (interRefPicSetPredictionFlag) {
        bitArray.skipBit(); // delta_rps_sign
        bitArray.readUnsignedExpGolombCodedInt(); // abs_delta_rps_minus1
        for (int j = 0; j <= previousNumDeltaPocs; j++) {
          if (bitArray.readBit()) { // used_by_curr_pic_flag[j]
            bitArray.skipBit(); // use_delta_flag[j]
          }
        }
      } else {
        numNegativePics = bitArray.readUnsignedExpGolombCodedInt();
        numPositivePics = bitArray.readUnsignedExpGolombCodedInt();
        previousNumDeltaPocs = numNegativePics + numPositivePics;
        for (int i = 0; i < numNegativePics; i++) {
          bitArray.readUnsignedExpGolombCodedInt(); // delta_poc_s0_minus1[i]
          bitArray.skipBit(); // used_by_curr_pic_s0_flag[i]
        }
        for (int i = 0; i < numPositivePics; i++) {
          bitArray.readUnsignedExpGolombCodedInt(); // delta_poc_s1_minus1[i]
          bitArray.skipBit(); // used_by_curr_pic_s1_flag[i]
        }
      }
    }
  }

  @EnsuresNonNull({"output", "sampleReader"})
  private void assertTracksCreated() {
    Assertions.checkStateNotNull(output);
    Util.castNonNull(sampleReader);
  }

  private static final class SampleReader {

    /**
     * Offset in bytes of the first_slice_segment_in_pic_flag in a NAL unit containing a
     * slice_segment_layer_rbsp.
     */
    private static final int FIRST_SLICE_FLAG_OFFSET = 2;

    private final TrackOutput output;

    // Per NAL unit state. A sample consists of one or more NAL units.
    private long nalUnitPosition;
    private boolean nalUnitHasKeyframeData;
    private int nalUnitBytesRead;
    private long nalUnitTimeUs;
    private boolean lookingForFirstSliceFlag;
    private boolean isFirstSlice;
    private boolean isFirstPrefixNalUnit;

    // Per sample state that gets reset at the start of each sample.
    private boolean readingSample;
    private boolean readingPrefix;
    private long samplePosition;
    private long sampleTimeUs;
    private boolean sampleIsKeyframe;

    public SampleReader(TrackOutput output) {
      this.output = output;
    }

    public void reset() {
      lookingForFirstSliceFlag = false;
      isFirstSlice = false;
      isFirstPrefixNalUnit = false;
      readingSample = false;
      readingPrefix = false;
    }

    public void startNalUnit(
        long position, int offset, int nalUnitType, long pesTimeUs, boolean hasOutputFormat) {
      isFirstSlice = false;
      isFirstPrefixNalUnit = false;
      nalUnitTimeUs = pesTimeUs;
      nalUnitBytesRead = 0;
      nalUnitPosition = position;

      if (!isVclBodyNalUnit(nalUnitType)) {
        if (readingSample && !readingPrefix) {
          if (hasOutputFormat) {
            outputSample(offset);
          }
          readingSample = false;
        }
        if (isPrefixNalUnit(nalUnitType)) {
          isFirstPrefixNalUnit = !readingPrefix;
          readingPrefix = true;
        }
      }

      // Look for the first slice flag if this NAL unit contains a slice_segment_layer_rbsp.
      nalUnitHasKeyframeData = (nalUnitType >= BLA_W_LP && nalUnitType <= CRA_NUT);
      lookingForFirstSliceFlag = nalUnitHasKeyframeData || nalUnitType <= RASL_R;
    }

    public void readNalUnitData(byte[] data, int offset, int limit) {
      if (lookingForFirstSliceFlag) {
        int headerOffset = offset + FIRST_SLICE_FLAG_OFFSET - nalUnitBytesRead;
        if (headerOffset < limit) {
          isFirstSlice = (data[headerOffset] & 0x80) != 0;
          lookingForFirstSliceFlag = false;
        } else {
          nalUnitBytesRead += limit - offset;
        }
      }
    }

    public void endNalUnit(long position, int offset, boolean hasOutputFormat) {
      if (readingPrefix && isFirstSlice) {
        // This sample has parameter sets. Reset the key-frame flag based on the first slice.
        sampleIsKeyframe = nalUnitHasKeyframeData;
        readingPrefix = false;
      } else if (isFirstPrefixNalUnit || isFirstSlice) {
        // This NAL unit is at the start of a new sample (access unit).
        if (hasOutputFormat && readingSample) {
          // Output the sample ending before this NAL unit.
          int nalUnitLength = (int) (position - nalUnitPosition);
          outputSample(offset + nalUnitLength);
        }
        samplePosition = nalUnitPosition;
        sampleTimeUs = nalUnitTimeUs;
        sampleIsKeyframe = nalUnitHasKeyframeData;
        readingSample = true;
      }
    }

    private void outputSample(int offset) {
      if (sampleTimeUs == C.TIME_UNSET) {
        return;
      }
      @C.BufferFlags int flags = sampleIsKeyframe ? C.BUFFER_FLAG_KEY_FRAME : 0;
      int size = (int) (nalUnitPosition - samplePosition);
      output.sampleMetadata(sampleTimeUs, flags, size, offset, null);
    }

    /** Returns whether a NAL unit type is one that occurs before any VCL NAL units in a sample. */
    private static boolean isPrefixNalUnit(int nalUnitType) {
      return (VPS_NUT <= nalUnitType && nalUnitType <= AUD_NUT) || nalUnitType == PREFIX_SEI_NUT;
    }

    /** Returns whether a NAL unit type is one that occurs in the VLC body of a sample. */
    private static boolean isVclBodyNalUnit(int nalUnitType) {
      return nalUnitType < VPS_NUT || nalUnitType == SUFFIX_SEI_NUT;
    }
  }
}
