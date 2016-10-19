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
package org.telegram.messenger.exoplayer.extractor.flv;

import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.ParserException;
import org.telegram.messenger.exoplayer.extractor.TrackOutput;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.MimeTypes;
import org.telegram.messenger.exoplayer.util.NalUnitUtil;
import org.telegram.messenger.exoplayer.util.ParsableBitArray;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses video tags from an FLV stream and extracts H.264 nal units.
 */
/* package */ final class VideoTagPayloadReader extends TagPayloadReader {

  // Video codec.
  private static final int VIDEO_CODEC_AVC = 7;

  // Frame types.
  private static final int VIDEO_FRAME_KEYFRAME = 1;
  private static final int VIDEO_FRAME_VIDEO_INFO = 5;

  // Packet types.
  private static final int AVC_PACKET_TYPE_SEQUENCE_HEADER = 0;
  private static final int AVC_PACKET_TYPE_AVC_NALU = 1;

  // Temporary arrays.
  private final ParsableByteArray nalStartCode;
  private final ParsableByteArray nalLength;
  private int nalUnitLengthFieldLength;

  // State variables.
  private boolean hasOutputFormat;
  private int frameType;

  /**
   * @param output A {@link TrackOutput} to which samples should be written.
   */
  public VideoTagPayloadReader(TrackOutput output) {
    super(output);
    nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);
    nalLength = new ParsableByteArray(4);
  }

  @Override
  public void seek() {
    // Do nothing.
  }

  @Override
  protected boolean parseHeader(ParsableByteArray data) throws UnsupportedFormatException {
    int header = data.readUnsignedByte();
    int frameType = (header >> 4) & 0x0F;
    int videoCodec = (header & 0x0F);
    // Support just H.264 encoded content.
    if (videoCodec != VIDEO_CODEC_AVC) {
      throw new UnsupportedFormatException("Video format not supported: " + videoCodec);
    }
    this.frameType = frameType;
    return (frameType != VIDEO_FRAME_VIDEO_INFO);
  }

  @Override
  protected void parsePayload(ParsableByteArray data, long timeUs) throws ParserException {
    int packetType = data.readUnsignedByte();
    int compositionTimeMs = data.readUnsignedInt24();
    timeUs += compositionTimeMs * 1000L;
    // Parse avc sequence header in case this was not done before.
    if (packetType == AVC_PACKET_TYPE_SEQUENCE_HEADER && !hasOutputFormat) {
      ParsableByteArray videoSequence = new ParsableByteArray(new byte[data.bytesLeft()]);
      data.readBytes(videoSequence.data, 0, data.bytesLeft());

      AvcSequenceHeaderData avcData = parseAvcCodecPrivate(videoSequence);
      nalUnitLengthFieldLength = avcData.nalUnitLengthFieldLength;

      // Construct and output the format.
      MediaFormat mediaFormat = MediaFormat.createVideoFormat(null, MimeTypes.VIDEO_H264,
          MediaFormat.NO_VALUE, MediaFormat.NO_VALUE, getDurationUs(), avcData.width,
          avcData.height, avcData.initializationData, MediaFormat.NO_VALUE,
          avcData.pixelWidthAspectRatio);
      output.format(mediaFormat);
      hasOutputFormat = true;
    } else if (packetType == AVC_PACKET_TYPE_AVC_NALU) {
      // TODO: Deduplicate with Mp4Extractor.
      // Zero the top three bytes of the array that we'll use to parse nal unit lengths, in case
      // they're only 1 or 2 bytes long.
      byte[] nalLengthData = nalLength.data;
      nalLengthData[0] = 0;
      nalLengthData[1] = 0;
      nalLengthData[2] = 0;
      int nalUnitLengthFieldLengthDiff = 4 - nalUnitLengthFieldLength;
      // NAL units are length delimited, but the decoder requires start code delimited units.
      // Loop until we've written the sample to the track output, replacing length delimiters with
      // start codes as we encounter them.
      int bytesWritten = 0;
      int bytesToWrite;
      while (data.bytesLeft() > 0) {
        // Read the NAL length so that we know where we find the next one.
        data.readBytes(nalLength.data, nalUnitLengthFieldLengthDiff, nalUnitLengthFieldLength);
        nalLength.setPosition(0);
        bytesToWrite = nalLength.readUnsignedIntToInt();

        // Write a start code for the current NAL unit.
        nalStartCode.setPosition(0);
        output.sampleData(nalStartCode, 4);
        bytesWritten += 4;

        // Write the payload of the NAL unit.
        output.sampleData(data, bytesToWrite);
        bytesWritten += bytesToWrite;
      }
      output.sampleMetadata(timeUs, frameType == VIDEO_FRAME_KEYFRAME ? C.SAMPLE_FLAG_SYNC : 0,
          bytesWritten, 0, null);
    }
  }

  /**
   * Builds initialization data for a {@link MediaFormat} from H.264 (AVC) codec private data.
   *
   * @return The AvcSequenceHeader data needed to initialize the video codec.
   * @throws ParserException If the initialization data could not be built.
   */
  private AvcSequenceHeaderData parseAvcCodecPrivate(ParsableByteArray buffer)
      throws ParserException {
    // TODO: Deduplicate with AtomParsers.parseAvcCFromParent.
    buffer.setPosition(4);
    int nalUnitLengthFieldLength = (buffer.readUnsignedByte() & 0x03) + 1;
    Assertions.checkState(nalUnitLengthFieldLength != 3);
    List<byte[]> initializationData = new ArrayList<>();
    int numSequenceParameterSets = buffer.readUnsignedByte() & 0x1F;
    for (int i = 0; i < numSequenceParameterSets; i++) {
      initializationData.add(NalUnitUtil.parseChildNalUnit(buffer));
    }
    int numPictureParameterSets = buffer.readUnsignedByte();
    for (int j = 0; j < numPictureParameterSets; j++) {
      initializationData.add(NalUnitUtil.parseChildNalUnit(buffer));
    }

    float pixelWidthAspectRatio = 1;
    int width = MediaFormat.NO_VALUE;
    int height = MediaFormat.NO_VALUE;
    if (numSequenceParameterSets > 0) {
      // Parse the first sequence parameter set to obtain pixelWidthAspectRatio.
      ParsableBitArray spsDataBitArray = new ParsableBitArray(initializationData.get(0));
      // Skip the NAL header consisting of the nalUnitLengthField and the type (1 byte).
      spsDataBitArray.setPosition(8 * (nalUnitLengthFieldLength + 1));
      NalUnitUtil.SpsData sps = NalUnitUtil.parseSpsNalUnit(spsDataBitArray);
      width = sps.width;
      height = sps.height;
      pixelWidthAspectRatio = sps.pixelWidthAspectRatio;
    }

    return new AvcSequenceHeaderData(initializationData, nalUnitLengthFieldLength,
        width, height, pixelWidthAspectRatio);
  }

  /**
   * Holds data parsed from an Sequence Header video tag atom.
   */
  private static final class AvcSequenceHeaderData {

    public final List<byte[]> initializationData;
    public final int nalUnitLengthFieldLength;
    public final float pixelWidthAspectRatio;
    public final int width;
    public final int height;

    public AvcSequenceHeaderData(List<byte[]> initializationData, int nalUnitLengthFieldLength,
        int width, int height, float pixelWidthAspectRatio) {
      this.initializationData = initializationData;
      this.nalUnitLengthFieldLength = nalUnitLengthFieldLength;
      this.pixelWidthAspectRatio = pixelWidthAspectRatio;
      this.width = width;
      this.height = height;
    }

  }

}
