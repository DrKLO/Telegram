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
package org.telegram.messenger.exoplayer.extractor.ts;

import android.util.SparseArray;
import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.extractor.TrackOutput;
import org.telegram.messenger.exoplayer.util.MimeTypes;
import org.telegram.messenger.exoplayer.util.NalUnitUtil;
import org.telegram.messenger.exoplayer.util.NalUnitUtil.SpsData;
import org.telegram.messenger.exoplayer.util.ParsableBitArray;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parses a continuous H264 byte stream and extracts individual frames.
 */
/* package */ final class H264Reader extends ElementaryStreamReader {

  private static final int NAL_UNIT_TYPE_SEI = 6; // Supplemental enhancement information
  private static final int NAL_UNIT_TYPE_SPS = 7; // Sequence parameter set
  private static final int NAL_UNIT_TYPE_PPS = 8; // Picture parameter set

  // State that should not be reset on seek.
  private boolean hasOutputFormat;

  // State that should be reset on seek.
  private final SeiReader seiReader;
  private final boolean[] prefixFlags;
  private final SampleReader sampleReader;
  private final NalUnitTargetBuffer sps;
  private final NalUnitTargetBuffer pps;
  private final NalUnitTargetBuffer sei;
  private long totalBytesWritten;

  // Per packet state that gets reset at the start of each packet.
  private long pesTimeUs;

  // Scratch variables to avoid allocations.
  private final ParsableByteArray seiWrapper;

  /**
   * @param output A {@link TrackOutput} to which H.264 samples should be written.
   * @param seiReader A reader for EIA-608 samples in SEI NAL units.
   * @param allowNonIdrKeyframes Whether to treat samples consisting of non-IDR I slices as
   *     synchronization samples (key-frames).
   * @param detectAccessUnits Whether to split the input stream into access units (samples) based on
   *     slice headers. Pass {@code false} if the stream contains access unit delimiters (AUDs).
   */
  public H264Reader(TrackOutput output, SeiReader seiReader, boolean allowNonIdrKeyframes,
      boolean detectAccessUnits) {
    super(output);
    this.seiReader = seiReader;
    prefixFlags = new boolean[3];
    sampleReader = new SampleReader(output, allowNonIdrKeyframes, detectAccessUnits);
    sps = new NalUnitTargetBuffer(NAL_UNIT_TYPE_SPS, 128);
    pps = new NalUnitTargetBuffer(NAL_UNIT_TYPE_PPS, 128);
    sei = new NalUnitTargetBuffer(NAL_UNIT_TYPE_SEI, 128);
    seiWrapper = new ParsableByteArray();
  }

  @Override
  public void seek() {
    NalUnitUtil.clearPrefixFlags(prefixFlags);
    sps.reset();
    pps.reset();
    sei.reset();
    sampleReader.reset();
    totalBytesWritten = 0;
  }

  @Override
  public void packetStarted(long pesTimeUs, boolean dataAlignmentIndicator) {
    this.pesTimeUs = pesTimeUs;
  }

  @Override
  public void consume(ParsableByteArray data) {
    while (data.bytesLeft() > 0) {
      int offset = data.getPosition();
      int limit = data.limit();
      byte[] dataArray = data.data;

      // Append the data to the buffer.
      totalBytesWritten += data.bytesLeft();
      output.sampleData(data, data.bytesLeft());

      // Scan the appended data, processing NAL units as they are encountered
      while (true) {
        int nalUnitOffset = NalUnitUtil.findNalUnit(dataArray, offset, limit, prefixFlags);

        if (nalUnitOffset == limit) {
          // We've scanned to the end of the data without finding the start of another NAL unit.
          nalUnitData(dataArray, offset, limit);
          return;
        }

        // We've seen the start of a NAL unit of the following type.
        int nalUnitType = NalUnitUtil.getNalUnitType(dataArray, nalUnitOffset);

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
        endNalUnit(absolutePosition, bytesWrittenPastPosition,
            lengthToNalUnit < 0 ? -lengthToNalUnit : 0, pesTimeUs);
        // Indicate the start of the next NAL unit.
        startNalUnit(absolutePosition, nalUnitType, pesTimeUs);
        // Continue scanning the data.
        offset = nalUnitOffset + 3;
      }
    }
  }

  @Override
  public void packetFinished() {
    // Do nothing.
  }

  private void startNalUnit(long position, int nalUnitType, long pesTimeUs) {
    if (!hasOutputFormat || sampleReader.needsSpsPps()) {
      sps.startNalUnit(nalUnitType);
      pps.startNalUnit(nalUnitType);
    }
    sei.startNalUnit(nalUnitType);
    sampleReader.startNalUnit(position, nalUnitType, pesTimeUs);
  }

  private void nalUnitData(byte[] dataArray, int offset, int limit) {
    if (!hasOutputFormat || sampleReader.needsSpsPps()) {
      sps.appendToNalUnit(dataArray, offset, limit);
      pps.appendToNalUnit(dataArray, offset, limit);
    }
    sei.appendToNalUnit(dataArray, offset, limit);
    sampleReader.appendToNalUnit(dataArray, offset, limit);
  }

  private void endNalUnit(long position, int offset, int discardPadding, long pesTimeUs) {
    if (!hasOutputFormat || sampleReader.needsSpsPps()) {
      sps.endNalUnit(discardPadding);
      pps.endNalUnit(discardPadding);
      if (!hasOutputFormat) {
        if (sps.isCompleted() && pps.isCompleted()) {
          List<byte[]> initializationData = new ArrayList<>();
          initializationData.add(Arrays.copyOf(sps.nalData, sps.nalLength));
          initializationData.add(Arrays.copyOf(pps.nalData, pps.nalLength));
          NalUnitUtil.SpsData spsData = NalUnitUtil.parseSpsNalUnit(unescape(sps));
          NalUnitUtil.PpsData ppsData = NalUnitUtil.parsePpsNalUnit(unescape(pps));
          output.format(MediaFormat.createVideoFormat(null, MimeTypes.VIDEO_H264,
              MediaFormat.NO_VALUE, MediaFormat.NO_VALUE, C.UNKNOWN_TIME_US, spsData.width,
              spsData.height, initializationData, MediaFormat.NO_VALUE,
              spsData.pixelWidthAspectRatio));
          hasOutputFormat = true;
          sampleReader.putSps(spsData);
          sampleReader.putPps(ppsData);
          sps.reset();
          pps.reset();
        }
      } else if (sps.isCompleted()) {
        NalUnitUtil.SpsData spsData = NalUnitUtil.parseSpsNalUnit(unescape(sps));
        sampleReader.putSps(spsData);
        sps.reset();
      } else if (pps.isCompleted()) {
        NalUnitUtil.PpsData ppsData = NalUnitUtil.parsePpsNalUnit(unescape(pps));
        sampleReader.putPps(ppsData);
        pps.reset();
      }
    }
    if (sei.endNalUnit(discardPadding)) {
      int unescapedLength = NalUnitUtil.unescapeStream(sei.nalData, sei.nalLength);
      seiWrapper.reset(sei.nalData, unescapedLength);
      seiWrapper.setPosition(4); // NAL prefix and nal_unit() header.
      seiReader.consume(pesTimeUs, seiWrapper);
    }
    sampleReader.endNalUnit(position, offset);
  }

  private static ParsableBitArray unescape(NalUnitTargetBuffer buffer) {
    int length = NalUnitUtil.unescapeStream(buffer.nalData, buffer.nalLength);
    ParsableBitArray bitArray = new ParsableBitArray(buffer.nalData, length);
    bitArray.skipBits(32); // NAL header
    return bitArray;
  }

  /**
   * Consumes a stream of NAL units and outputs samples.
   */
  private static final class SampleReader {

    private static final int DEFAULT_BUFFER_SIZE = 128;

    private static final int NAL_UNIT_TYPE_NON_IDR = 1; // Coded slice of a non-IDR picture
    private static final int NAL_UNIT_TYPE_PARTITION_A = 2; // Coded slice data partition A
    private static final int NAL_UNIT_TYPE_IDR = 5; // Coded slice of an IDR picture
    private static final int NAL_UNIT_TYPE_AUD = 9; // Access unit delimiter

    private final TrackOutput output;
    private final boolean allowNonIdrKeyframes;
    private final boolean detectAccessUnits;
    private final ParsableBitArray scratch;
    private final SparseArray<NalUnitUtil.SpsData> sps;
    private final SparseArray<NalUnitUtil.PpsData> pps;

    private byte[] buffer;
    private int bufferLength;

    // Per NAL unit state. A sample consists of one or more NAL units.
    private int nalUnitType;
    private long nalUnitStartPosition;
    private boolean isFilling;
    private long nalUnitTimeUs;
    private SliceHeaderData previousSliceHeader;
    private SliceHeaderData sliceHeader;

    // Per sample state that gets reset at the start of each sample.
    private boolean readingSample;
    private long samplePosition;
    private long sampleTimeUs;
    private boolean sampleIsKeyframe;

    public SampleReader(TrackOutput output, boolean allowNonIdrKeyframes,
        boolean detectAccessUnits) {
      this.output = output;
      this.allowNonIdrKeyframes = allowNonIdrKeyframes;
      this.detectAccessUnits = detectAccessUnits;
      sps = new SparseArray<>();
      pps = new SparseArray<>();
      previousSliceHeader = new SliceHeaderData();
      sliceHeader = new SliceHeaderData();
      scratch = new ParsableBitArray();
      buffer = new byte[DEFAULT_BUFFER_SIZE];
      reset();
    }

    public boolean needsSpsPps() {
      return detectAccessUnits;
    }

    public void putSps(NalUnitUtil.SpsData spsData) {
      sps.append(spsData.seqParameterSetId, spsData);
    }

    public void putPps(NalUnitUtil.PpsData ppsData) {
      pps.append(ppsData.picParameterSetId, ppsData);
    }

    public void reset() {
      isFilling = false;
      readingSample = false;
      sliceHeader.clear();
    }

    public void startNalUnit(long position, int type, long pesTimeUs) {
      nalUnitType = type;
      nalUnitTimeUs = pesTimeUs;
      nalUnitStartPosition = position;
      if ((allowNonIdrKeyframes && nalUnitType == NAL_UNIT_TYPE_NON_IDR)
          || (detectAccessUnits && (nalUnitType == NAL_UNIT_TYPE_IDR
              || nalUnitType == NAL_UNIT_TYPE_NON_IDR
              || nalUnitType == NAL_UNIT_TYPE_PARTITION_A))) {
        // Store the previous header and prepare to populate the new one.
        SliceHeaderData newSliceHeader = previousSliceHeader;
        previousSliceHeader = sliceHeader;
        sliceHeader = newSliceHeader;
        sliceHeader.clear();
        bufferLength = 0;
        isFilling = true;
      }
    }

    /**
     * Invoked to pass stream data. The data passed should not include the 3 byte start code.
     *
     * @param data Holds the data being passed.
     * @param offset The offset of the data in {@code data}.
     * @param limit The limit (exclusive) of the data in {@code data}.
     */
    public void appendToNalUnit(byte[] data, int offset, int limit) {
      if (!isFilling) {
        return;
      }
      int readLength = limit - offset;
      if (buffer.length < bufferLength + readLength) {
        buffer = Arrays.copyOf(buffer, (bufferLength + readLength) * 2);
      }
      System.arraycopy(data, offset, buffer, bufferLength, readLength);
      bufferLength += readLength;

      scratch.reset(buffer, bufferLength);
      if (scratch.bitsLeft() < 8) {
        return;
      }
      scratch.skipBits(1); // forbidden_zero_bit
      int nalRefIdc = scratch.readBits(2);
      scratch.skipBits(5); // nal_unit_type

      // Read the slice header using the syntax defined in ITU-T Recommendation H.264 (2013)
      // subsection 7.3.3.
      if (!scratch.canReadExpGolombCodedNum()) {
        return;
      }
      scratch.readUnsignedExpGolombCodedInt(); // first_mb_in_slice
      if (!scratch.canReadExpGolombCodedNum()) {
        return;
      }
      int sliceType = scratch.readUnsignedExpGolombCodedInt();
      if (!detectAccessUnits) {
        // There are AUDs in the stream so the rest of the header can be ignored.
        isFilling = false;
        sliceHeader.setSliceType(sliceType);
        return;
      }
      if (!scratch.canReadExpGolombCodedNum()) {
        return;
      }
      int picParameterSetId = scratch.readUnsignedExpGolombCodedInt();
      if (pps.indexOfKey(picParameterSetId) < 0) {
        // We have not seen the PPS yet, so don't try to parse the slice header.
        isFilling = false;
        return;
      }
      NalUnitUtil.PpsData ppsData = pps.get(picParameterSetId);
      NalUnitUtil.SpsData spsData = sps.get(ppsData.seqParameterSetId);
      if (spsData.separateColorPlaneFlag) {
        if (scratch.bitsLeft() < 2) {
          return;
        }
        scratch.skipBits(2); // colour_plane_id
      }
      if (scratch.bitsLeft() < spsData.frameNumLength) {
        return;
      }
      boolean fieldPicFlag = false;
      boolean bottomFieldFlagPresent = false;
      boolean bottomFieldFlag = false;
      int frameNum = scratch.readBits(spsData.frameNumLength);
      if (!spsData.frameMbsOnlyFlag) {
        if (scratch.bitsLeft() < 1) {
          return;
        }
        fieldPicFlag = scratch.readBit();
        if (fieldPicFlag) {
          if (scratch.bitsLeft() < 1) {
            return;
          }
          bottomFieldFlag = scratch.readBit();
          bottomFieldFlagPresent = true;
        }
      }
      boolean idrPicFlag = nalUnitType == NAL_UNIT_TYPE_IDR;
      int idrPicId = 0;
      if (idrPicFlag) {
        if (!scratch.canReadExpGolombCodedNum()) {
          return;
        }
        idrPicId = scratch.readUnsignedExpGolombCodedInt();
      }
      int picOrderCntLsb = 0;
      int deltaPicOrderCntBottom = 0;
      int deltaPicOrderCnt0 = 0;
      int deltaPicOrderCnt1 = 0;
      if (spsData.picOrderCountType == 0) {
        if (scratch.bitsLeft() < spsData.picOrderCntLsbLength) {
          return;
        }
        picOrderCntLsb = scratch.readBits(spsData.picOrderCntLsbLength);
        if (ppsData.bottomFieldPicOrderInFramePresentFlag && !fieldPicFlag) {
          if (!scratch.canReadExpGolombCodedNum()) {
            return;
          }
          deltaPicOrderCntBottom = scratch.readSignedExpGolombCodedInt();
        }
      } else if (spsData.picOrderCountType == 1
          && !spsData.deltaPicOrderAlwaysZeroFlag) {
        if (!scratch.canReadExpGolombCodedNum()) {
          return;
        }
        deltaPicOrderCnt0 = scratch.readSignedExpGolombCodedInt();
        if (ppsData.bottomFieldPicOrderInFramePresentFlag && !fieldPicFlag) {
          if (!scratch.canReadExpGolombCodedNum()) {
            return;
          }
          deltaPicOrderCnt1 = scratch.readSignedExpGolombCodedInt();
        }
      }
      sliceHeader.setAll(spsData, nalRefIdc, sliceType, frameNum, picParameterSetId, fieldPicFlag,
          bottomFieldFlagPresent, bottomFieldFlag, idrPicFlag, idrPicId, picOrderCntLsb,
          deltaPicOrderCntBottom, deltaPicOrderCnt0, deltaPicOrderCnt1);
      isFilling = false;
    }

    public void endNalUnit(long position, int offset) {
      if (nalUnitType == NAL_UNIT_TYPE_AUD
          || (detectAccessUnits && sliceHeader.isFirstVclNalUnitOfPicture(previousSliceHeader))) {
        // If the NAL unit ending is the start of a new sample, output the previous one.
        if (readingSample) {
          int nalUnitLength = (int) (position - nalUnitStartPosition);
          outputSample(offset + nalUnitLength);
        }
        samplePosition = nalUnitStartPosition;
        sampleTimeUs = nalUnitTimeUs;
        sampleIsKeyframe = false;
        readingSample = true;
      }
      sampleIsKeyframe |= nalUnitType == NAL_UNIT_TYPE_IDR || (allowNonIdrKeyframes
          && nalUnitType == NAL_UNIT_TYPE_NON_IDR && sliceHeader.isISlice());
    }

    private void outputSample(int offset) {
      int flags = sampleIsKeyframe ? C.SAMPLE_FLAG_SYNC : 0;
      int size = (int) (nalUnitStartPosition - samplePosition);
      output.sampleMetadata(sampleTimeUs, flags, size, offset, null);
    }

    private static final class SliceHeaderData {

      private static final int SLICE_TYPE_I = 2;
      private static final int SLICE_TYPE_ALL_I = 7;

      private boolean isComplete;
      private boolean hasSliceType;

      private SpsData spsData;
      private int nalRefIdc;
      private int sliceType;
      private int frameNum;
      private int picParameterSetId;
      private boolean fieldPicFlag;
      private boolean bottomFieldFlagPresent;
      private boolean bottomFieldFlag;
      private boolean idrPicFlag;
      private int idrPicId;
      private int picOrderCntLsb;
      private int deltaPicOrderCntBottom;
      private int deltaPicOrderCnt0;
      private int deltaPicOrderCnt1;

      public void clear() {
        hasSliceType = false;
        isComplete = false;
      }

      public void setSliceType(int sliceType) {
        this.sliceType = sliceType;
        hasSliceType = true;
      }

      public void setAll(SpsData spsData, int nalRefIdc, int sliceType, int frameNum,
          int picParameterSetId, boolean fieldPicFlag, boolean bottomFieldFlagPresent,
          boolean bottomFieldFlag, boolean idrPicFlag, int idrPicId, int picOrderCntLsb,
          int deltaPicOrderCntBottom, int deltaPicOrderCnt0, int deltaPicOrderCnt1) {
        this.spsData = spsData;
        this.nalRefIdc = nalRefIdc;
        this.sliceType = sliceType;
        this.frameNum = frameNum;
        this.picParameterSetId = picParameterSetId;
        this.fieldPicFlag = fieldPicFlag;
        this.bottomFieldFlagPresent = bottomFieldFlagPresent;
        this.bottomFieldFlag = bottomFieldFlag;
        this.idrPicFlag = idrPicFlag;
        this.idrPicId = idrPicId;
        this.picOrderCntLsb = picOrderCntLsb;
        this.deltaPicOrderCntBottom = deltaPicOrderCntBottom;
        this.deltaPicOrderCnt0 = deltaPicOrderCnt0;
        this.deltaPicOrderCnt1 = deltaPicOrderCnt1;
        isComplete = true;
        hasSliceType = true;
      }

      public boolean isISlice() {
        return hasSliceType && (sliceType == SLICE_TYPE_ALL_I || sliceType == SLICE_TYPE_I);
      }

      private boolean isFirstVclNalUnitOfPicture(SliceHeaderData other) {
        // See ISO 14496-10 subsection 7.4.1.2.4.
        return isComplete && (!other.isComplete || frameNum != other.frameNum
            || picParameterSetId != other.picParameterSetId || fieldPicFlag != other.fieldPicFlag
            || (bottomFieldFlagPresent && other.bottomFieldFlagPresent
                && bottomFieldFlag != other.bottomFieldFlag)
            || (nalRefIdc != other.nalRefIdc && (nalRefIdc == 0 || other.nalRefIdc == 0))
            || (spsData.picOrderCountType == 0 && other.spsData.picOrderCountType == 0
                && (picOrderCntLsb != other.picOrderCntLsb
                    || deltaPicOrderCntBottom != other.deltaPicOrderCntBottom))
            || (spsData.picOrderCountType == 1 && other.spsData.picOrderCountType == 1
                && (deltaPicOrderCnt0 != other.deltaPicOrderCnt0
                    || deltaPicOrderCnt1 != other.deltaPicOrderCnt1))
            || idrPicFlag != other.idrPicFlag
            || (idrPicFlag && other.idrPicFlag && idrPicId != other.idrPicId));
      }

    }

  }

}
