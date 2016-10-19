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
package org.telegram.messenger.exoplayer.extractor.mp3;

import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.ParserException;
import org.telegram.messenger.exoplayer.extractor.Extractor;
import org.telegram.messenger.exoplayer.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer.extractor.ExtractorOutput;
import org.telegram.messenger.exoplayer.extractor.GaplessInfo;
import org.telegram.messenger.exoplayer.extractor.PositionHolder;
import org.telegram.messenger.exoplayer.extractor.SeekMap;
import org.telegram.messenger.exoplayer.extractor.TrackOutput;
import org.telegram.messenger.exoplayer.util.MpegAudioHeader;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import org.telegram.messenger.exoplayer.util.Util;
import java.io.EOFException;
import java.io.IOException;

/**
 * Extracts data from an MP3 file.
 */
public final class Mp3Extractor implements Extractor {

  /**
   * The maximum number of bytes to search when synchronizing, before giving up.
   */
  private static final int MAX_SYNC_BYTES = 128 * 1024;
  /**
   * The maximum number of bytes to peek when sniffing, excluding the ID3 header, before giving up.
   */
  private static final int MAX_SNIFF_BYTES = MpegAudioHeader.MAX_FRAME_SIZE_BYTES;

  /**
   * Mask that includes the audio header values that must match between frames.
   */
  private static final int HEADER_MASK = 0xFFFE0C00;
  private static final int XING_HEADER = Util.getIntegerCodeForString("Xing");
  private static final int INFO_HEADER = Util.getIntegerCodeForString("Info");
  private static final int VBRI_HEADER = Util.getIntegerCodeForString("VBRI");

  private final long forcedFirstSampleTimestampUs;
  private final ParsableByteArray scratch;
  private final MpegAudioHeader synchronizedHeader;

  // Extractor outputs.
  private ExtractorOutput extractorOutput;
  private TrackOutput trackOutput;

  private int synchronizedHeaderData;

  private GaplessInfo gaplessInfo;
  private Seeker seeker;
  private long basisTimeUs;
  private long samplesRead;
  private int sampleBytesRemaining;

  /**
   * Constructs a new {@link Mp3Extractor}.
   */
  public Mp3Extractor() {
    this(-1);
  }

  /**
   * Constructs a new {@link Mp3Extractor}.
   *
   * @param forcedFirstSampleTimestampUs A timestamp to force for the first sample, or -1 if forcing
   *     is not required.
   */
  public Mp3Extractor(long forcedFirstSampleTimestampUs) {
    this.forcedFirstSampleTimestampUs = forcedFirstSampleTimestampUs;
    scratch = new ParsableByteArray(4);
    synchronizedHeader = new MpegAudioHeader();
    basisTimeUs = -1;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    return synchronize(input, true);
  }

  @Override
  public void init(ExtractorOutput extractorOutput) {
    this.extractorOutput = extractorOutput;
    trackOutput = extractorOutput.track(0);
    extractorOutput.endTracks();
  }

  @Override
  public void seek() {
    synchronizedHeaderData = 0;
    samplesRead = 0;
    basisTimeUs = -1;
    sampleBytesRemaining = 0;
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    if (synchronizedHeaderData == 0 && !synchronizeCatchingEndOfInput(input)) {
      return RESULT_END_OF_INPUT;
    }
    if (seeker == null) {
      setupSeeker(input);
      extractorOutput.seekMap(seeker);
      MediaFormat mediaFormat = MediaFormat.createAudioFormat(null, synchronizedHeader.mimeType,
          MediaFormat.NO_VALUE, MpegAudioHeader.MAX_FRAME_SIZE_BYTES, seeker.getDurationUs(),
          synchronizedHeader.channels, synchronizedHeader.sampleRate, null, null);
      if (gaplessInfo != null) {
        mediaFormat =
            mediaFormat.copyWithGaplessInfo(gaplessInfo.encoderDelay, gaplessInfo.encoderPadding);
      }
      trackOutput.format(mediaFormat);
    }
    return readSample(input);
  }

  private int readSample(ExtractorInput extractorInput) throws IOException, InterruptedException {
    if (sampleBytesRemaining == 0) {
      if (!maybeResynchronize(extractorInput)) {
        return RESULT_END_OF_INPUT;
      }
      if (basisTimeUs == -1) {
        basisTimeUs = seeker.getTimeUs(extractorInput.getPosition());
        if (forcedFirstSampleTimestampUs != -1) {
          long embeddedFirstSampleTimestampUs = seeker.getTimeUs(0);
          basisTimeUs += forcedFirstSampleTimestampUs - embeddedFirstSampleTimestampUs;
        }
      }
      sampleBytesRemaining = synchronizedHeader.frameSize;
    }
    int bytesAppended = trackOutput.sampleData(extractorInput, sampleBytesRemaining, true);
    if (bytesAppended == C.RESULT_END_OF_INPUT) {
      return RESULT_END_OF_INPUT;
    }
    sampleBytesRemaining -= bytesAppended;
    if (sampleBytesRemaining > 0) {
      return RESULT_CONTINUE;
    }
    long timeUs = basisTimeUs + (samplesRead * C.MICROS_PER_SECOND / synchronizedHeader.sampleRate);
    trackOutput.sampleMetadata(timeUs, C.SAMPLE_FLAG_SYNC, synchronizedHeader.frameSize, 0, null);
    samplesRead += synchronizedHeader.samplesPerFrame;
    sampleBytesRemaining = 0;
    return RESULT_CONTINUE;
  }

  /**
   * Attempts to read an MPEG audio header at the current offset, resynchronizing if necessary.
   */
  private boolean maybeResynchronize(ExtractorInput extractorInput)
      throws IOException, InterruptedException {
    extractorInput.resetPeekPosition();
    if (!extractorInput.peekFully(scratch.data, 0, 4, true)) {
      return false;
    }

    scratch.setPosition(0);
    int sampleHeaderData = scratch.readInt();
    if ((sampleHeaderData & HEADER_MASK) == (synchronizedHeaderData & HEADER_MASK)) {
      int frameSize = MpegAudioHeader.getFrameSize(sampleHeaderData);
      if (frameSize != -1) {
        MpegAudioHeader.populateHeader(sampleHeaderData, synchronizedHeader);
        return true;
      }
    }

    synchronizedHeaderData = 0;
    extractorInput.skipFully(1);
    return synchronizeCatchingEndOfInput(extractorInput);
  }

  private boolean synchronizeCatchingEndOfInput(ExtractorInput input)
      throws IOException, InterruptedException {
    // An EOFException will be raised if any peek operation was partially satisfied. If a seek
    // operation resulted in reading from within the last frame, we may try to peek past the end of
    // the file in a partially-satisfied read operation, so we need to catch the exception.
    try {
      return synchronize(input, false);
    } catch (EOFException e) {
      return false;
    }
  }

  private boolean synchronize(ExtractorInput input, boolean sniffing)
      throws IOException, InterruptedException {
    int searched = 0;
    int validFrameCount = 0;
    int candidateSynchronizedHeaderData = 0;
    int peekedId3Bytes = 0;
    input.resetPeekPosition();
    if (input.getPosition() == 0) {
      gaplessInfo = Id3Util.parseId3(input);
      peekedId3Bytes = (int) input.getPeekPosition();
      if (!sniffing) {
        input.skipFully(peekedId3Bytes);
      }
    }
    while (true) {
      if (sniffing && searched == MAX_SNIFF_BYTES) {
        return false;
      }
      if (!sniffing && searched == MAX_SYNC_BYTES) {
        throw new ParserException("Searched too many bytes.");
      }
      if (!input.peekFully(scratch.data, 0, 4, true)) {
        return false;
      }
      scratch.setPosition(0);
      int headerData = scratch.readInt();
      int frameSize;
      if ((candidateSynchronizedHeaderData != 0
          && (headerData & HEADER_MASK) != (candidateSynchronizedHeaderData & HEADER_MASK))
          || (frameSize = MpegAudioHeader.getFrameSize(headerData)) == -1) {
        // The header is invalid or doesn't match the candidate header. Try the next byte offset.
        validFrameCount = 0;
        candidateSynchronizedHeaderData = 0;
        searched++;
        if (sniffing) {
          input.resetPeekPosition();
          input.advancePeekPosition(peekedId3Bytes + searched);
        } else {
          input.skipFully(1);
        }
      } else {
        // The header is valid and matches the candidate header.
        validFrameCount++;
        if (validFrameCount == 1) {
          MpegAudioHeader.populateHeader(headerData, synchronizedHeader);
          candidateSynchronizedHeaderData = headerData;
        } else if (validFrameCount == 4) {
          break;
        }
        input.advancePeekPosition(frameSize - 4);
      }
    }
    // Prepare to read the synchronized frame.
    if (sniffing) {
      input.skipFully(peekedId3Bytes + searched);
    } else {
      input.resetPeekPosition();
    }
    synchronizedHeaderData = candidateSynchronizedHeaderData;
    return true;
  }

  /**
   * Sets {@link #seeker} to seek using metadata read from {@code input}, which should provide data
   * from the start of the first frame in the stream. On returning, the input's position will be set
   * to the start of the first frame of audio.
   *
   * @param input The {@link ExtractorInput} from which to read.
   * @throws IOException Thrown if there was an error reading from the stream. Not expected if the
   *     next two frames were already peeked during synchronization.
   * @throws InterruptedException Thrown if reading from the stream was interrupted. Not expected if
   *     the next two frames were already peeked during synchronization.
   */
  private void setupSeeker(ExtractorInput input) throws IOException, InterruptedException {
    // Read the first frame which may contain a Xing or VBRI header with seeking metadata.
    ParsableByteArray frame = new ParsableByteArray(synchronizedHeader.frameSize);
    input.peekFully(frame.data, 0, synchronizedHeader.frameSize);

    long position = input.getPosition();
    long length = input.getLength();

    // Check if there is a Xing header.
    int xingBase = (synchronizedHeader.version & 1) != 0
        ? (synchronizedHeader.channels != 1 ? 36 : 21) // MPEG 1
        : (synchronizedHeader.channels != 1 ? 21 : 13); // MPEG 2 or 2.5
    frame.setPosition(xingBase);
    int headerData = frame.readInt();
    if (headerData == XING_HEADER || headerData == INFO_HEADER) {
      seeker = XingSeeker.create(synchronizedHeader, frame, position, length);
      if (seeker != null && gaplessInfo == null) {
        // If there is a Xing header, read gapless playback metadata at a fixed offset.
        input.resetPeekPosition();
        input.advancePeekPosition(xingBase + 141);
        input.peekFully(scratch.data, 0, 3);
        scratch.setPosition(0);
        gaplessInfo = GaplessInfo.createFromXingHeaderValue(scratch.readUnsignedInt24());
      }
      input.skipFully(synchronizedHeader.frameSize);
    } else {
      // Check if there is a VBRI header.
      frame.setPosition(36); // MPEG audio header (4 bytes) + 32 bytes.
      headerData = frame.readInt();
      if (headerData == VBRI_HEADER) {
        seeker = VbriSeeker.create(synchronizedHeader, frame, position, length);
        input.skipFully(synchronizedHeader.frameSize);
      }
    }

    if (seeker == null) {
      // Repopulate the synchronized header in case we had to skip an invalid seeking header, which
      // would give an invalid CBR bitrate.
      input.resetPeekPosition();
      input.peekFully(scratch.data, 0, 4);
      scratch.setPosition(0);
      MpegAudioHeader.populateHeader(scratch.readInt(), synchronizedHeader);
      seeker = new ConstantBitrateSeeker(input.getPosition(), synchronizedHeader.bitrate, length);
    }
  }

  /**
   * {@link SeekMap} that also allows mapping from position (byte offset) back to time, which can be
   * used to work out the new sample basis timestamp after seeking and resynchronization.
   */
  /* package */ interface Seeker extends SeekMap {

    /**
     * Maps a position (byte offset) to a corresponding sample timestamp.
     *
     * @param position A seek position (byte offset) relative to the start of the stream.
     * @return The corresponding timestamp of the next sample to be read, in microseconds.
     */
    long getTimeUs(long position);

    /** Returns the duration of the source, in microseconds. */
    long getDurationUs();

  }

}
