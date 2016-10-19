/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.extractor.ogg;

import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.ParserException;
import org.telegram.messenger.exoplayer.extractor.Extractor;
import org.telegram.messenger.exoplayer.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer.extractor.PositionHolder;
import org.telegram.messenger.exoplayer.extractor.SeekMap;
import org.telegram.messenger.exoplayer.extractor.ogg.VorbisUtil.Mode;
import org.telegram.messenger.exoplayer.util.MimeTypes;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import java.io.IOException;
import java.util.ArrayList;

/**
 * {@link StreamReader} to extract Vorbis data out of Ogg byte stream.
 */
/* package */ final class VorbisReader extends StreamReader implements SeekMap {

  private static final long LARGEST_EXPECTED_PAGE_SIZE = 8000;

  private VorbisSetup vorbisSetup;
  private int previousPacketBlockSize;
  private long elapsedSamples;
  private boolean seenFirstAudioPacket;

  private final OggSeeker oggSeeker = new OggSeeker();
  private long targetGranule = -1;

  private VorbisUtil.VorbisIdHeader vorbisIdHeader;
  private VorbisUtil.CommentHeader commentHeader;
  private long inputLength;
  private long audioStartPosition;
  private long totalSamples;
  private long duration;

  /* package */ static boolean verifyBitstreamType(ParsableByteArray data) {
    try {
      return VorbisUtil.verifyVorbisHeaderCapturePattern(0x01, data, true);
    } catch (ParserException e) {
      return false;
    }
  }

  @Override
  public void seek() {
    super.seek();
    previousPacketBlockSize = 0;
    elapsedSamples = 0;
    seenFirstAudioPacket = false;
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {

    // setup
    if (totalSamples == 0) {
      if (vorbisSetup == null) {
        inputLength = input.getLength();
        vorbisSetup = readSetupHeaders(input, scratch);
        audioStartPosition = input.getPosition();
        extractorOutput.seekMap(this);
        if (inputLength != C.LENGTH_UNBOUNDED) {
          // seek to the end just before the last page of stream to get the duration
          seekPosition.position = Math.max(0, input.getLength() - LARGEST_EXPECTED_PAGE_SIZE);
          return Extractor.RESULT_SEEK;
        }
      }
      totalSamples = inputLength == C.LENGTH_UNBOUNDED ? -1
          : oggParser.readGranuleOfLastPage(input);

      ArrayList<byte[]> codecInitialisationData = new ArrayList<>();
      codecInitialisationData.add(vorbisSetup.idHeader.data);
      codecInitialisationData.add(vorbisSetup.setupHeaderData);

      duration = inputLength == C.LENGTH_UNBOUNDED ? C.UNKNOWN_TIME_US
          : totalSamples * C.MICROS_PER_SECOND / vorbisSetup.idHeader.sampleRate;
      trackOutput.format(MediaFormat.createAudioFormat(null, MimeTypes.AUDIO_VORBIS,
          this.vorbisSetup.idHeader.bitrateNominal, OggParser.OGG_MAX_SEGMENT_SIZE * 255, duration,
          this.vorbisSetup.idHeader.channels, (int) this.vorbisSetup.idHeader.sampleRate,
          codecInitialisationData, null));

      if (inputLength != C.LENGTH_UNBOUNDED) {
        oggSeeker.setup(inputLength - audioStartPosition, totalSamples);
        // seek back to resume from where we finished reading vorbis headers
        seekPosition.position = audioStartPosition;
        return Extractor.RESULT_SEEK;
      }
    }

    // seeking requested
    if (!seenFirstAudioPacket && targetGranule > -1) {
      OggUtil.skipToNextPage(input);
      long position = oggSeeker.getNextSeekPosition(targetGranule, input);
      if (position != -1) {
        seekPosition.position = position;
        return Extractor.RESULT_SEEK;
      } else {
        elapsedSamples = oggParser.skipToPageOfGranule(input, targetGranule);
        previousPacketBlockSize = vorbisIdHeader.blockSize0;
        // we're never at the first packet after seeking
        seenFirstAudioPacket = true;
      }
    }

    // playback
    if (oggParser.readPacket(input, scratch)) {
      // if this is an audio packet...
      if ((scratch.data[0] & 0x01) != 1) {
        // ... we need to decode the block size
        int packetBlockSize = decodeBlockSize(scratch.data[0], vorbisSetup);
        // a packet contains samples produced from overlapping the previous and current frame data
        // (https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-350001.3.2)
        int samplesInPacket = seenFirstAudioPacket ? (packetBlockSize + previousPacketBlockSize) / 4
            : 0;
        if (elapsedSamples + samplesInPacket >= targetGranule) {
          // codec expects the number of samples appended to audio data
          appendNumberOfSamples(scratch, samplesInPacket);
          // calculate time and send audio data to codec
          long timeUs = elapsedSamples * C.MICROS_PER_SECOND / vorbisSetup.idHeader.sampleRate;
          trackOutput.sampleData(scratch, scratch.limit());
          trackOutput.sampleMetadata(timeUs, C.SAMPLE_FLAG_SYNC, scratch.limit(), 0, null);
          targetGranule = -1;
        }
        // update state in members for next iteration
        seenFirstAudioPacket = true;
        elapsedSamples += samplesInPacket;
        previousPacketBlockSize = packetBlockSize;
      }
      scratch.reset();
      return Extractor.RESULT_CONTINUE;
    }
    return Extractor.RESULT_END_OF_INPUT;
  }

  //@VisibleForTesting
  /* package */ VorbisSetup readSetupHeaders(ExtractorInput input,  ParsableByteArray scratch)
      throws IOException, InterruptedException {

    if (vorbisIdHeader == null) {
      oggParser.readPacket(input, scratch);
      vorbisIdHeader = VorbisUtil.readVorbisIdentificationHeader(scratch);
      scratch.reset();
    }

    if (commentHeader == null) {
      oggParser.readPacket(input, scratch);
      commentHeader = VorbisUtil.readVorbisCommentHeader(scratch);
      scratch.reset();
    }

    oggParser.readPacket(input, scratch);
    // the third packet contains the setup header
    byte[] setupHeaderData = new byte[scratch.limit()];
    // raw data of vorbis setup header has to be passed to decoder as CSD buffer #2
    System.arraycopy(scratch.data, 0, setupHeaderData, 0, scratch.limit());
    // partially decode setup header to get the modes
    Mode[] modes = VorbisUtil.readVorbisModes(scratch, vorbisIdHeader.channels);
    // we need the ilog of modes all the time when extracting, so we compute it once
    int iLogModes = VorbisUtil.iLog(modes.length - 1);
    scratch.reset();

    return new VorbisSetup(vorbisIdHeader, commentHeader, setupHeaderData, modes, iLogModes);
  }

  //@VisibleForTesting
  /* package */ static void appendNumberOfSamples(ParsableByteArray buffer,
      long packetSampleCount) {

    buffer.setLimit(buffer.limit() + 4);
    // The vorbis decoder expects the number of samples in the packet
    // to be appended to the audio data as an int32
    buffer.data[buffer.limit() - 4] = (byte) ((packetSampleCount) & 0xFF);
    buffer.data[buffer.limit() - 3] = (byte) ((packetSampleCount >>> 8) & 0xFF);
    buffer.data[buffer.limit() - 2] = (byte) ((packetSampleCount >>> 16) & 0xFF);
    buffer.data[buffer.limit() - 1] = (byte) ((packetSampleCount >>> 24) & 0xFF);
  }

  private static int decodeBlockSize(byte firstByteOfAudioPacket, VorbisSetup vorbisSetup) {
    // read modeNumber (https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-730004.3.1)
    int modeNumber = OggUtil.readBits(firstByteOfAudioPacket, vorbisSetup.iLogModes, 1);
    int currentBlockSize;
    if (!vorbisSetup.modes[modeNumber].blockFlag) {
      currentBlockSize = vorbisSetup.idHeader.blockSize0;
    } else {
      currentBlockSize = vorbisSetup.idHeader.blockSize1;
    }
    return currentBlockSize;
  }

  @Override
  public boolean isSeekable() {
    return vorbisSetup != null && inputLength != C.LENGTH_UNBOUNDED;
  }

  @Override
  public long getPosition(long timeUs) {
    if (timeUs == 0) {
      targetGranule = -1;
      return audioStartPosition;
    }
    targetGranule = vorbisSetup.idHeader.sampleRate * timeUs / C.MICROS_PER_SECOND;
    return Math.max(audioStartPosition, ((inputLength - audioStartPosition) * timeUs
        / duration) - 4000);
  }

  /**
   * Class to hold all data read from Vorbis setup headers.
   */
  /* package */ static final class VorbisSetup {

    public final VorbisUtil.VorbisIdHeader idHeader;
    public final VorbisUtil.CommentHeader commentHeader;
    public final byte[] setupHeaderData;
    public final Mode[] modes;
    public final int iLogModes;

    public VorbisSetup(VorbisUtil.VorbisIdHeader idHeader, VorbisUtil.CommentHeader
        commentHeader, byte[] setupHeaderData, Mode[] modes, int iLogModes) {
      this.idHeader = idHeader;
      this.commentHeader = commentHeader;
      this.setupHeaderData = setupHeaderData;
      this.modes = modes;
      this.iLogModes = iLogModes;
    }

  }

}
