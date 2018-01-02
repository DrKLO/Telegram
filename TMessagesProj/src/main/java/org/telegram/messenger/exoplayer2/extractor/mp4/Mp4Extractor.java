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
package org.telegram.messenger.exoplayer2.extractor.mp4;

import android.support.annotation.IntDef;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.ParserException;
import org.telegram.messenger.exoplayer2.extractor.Extractor;
import org.telegram.messenger.exoplayer2.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer2.extractor.ExtractorOutput;
import org.telegram.messenger.exoplayer2.extractor.ExtractorsFactory;
import org.telegram.messenger.exoplayer2.extractor.GaplessInfoHolder;
import org.telegram.messenger.exoplayer2.extractor.PositionHolder;
import org.telegram.messenger.exoplayer2.extractor.SeekMap;
import org.telegram.messenger.exoplayer2.extractor.TrackOutput;
import org.telegram.messenger.exoplayer2.extractor.mp4.Atom.ContainerAtom;
import org.telegram.messenger.exoplayer2.extractor.mp4.FragmentedMp4Extractor.Flags;
import org.telegram.messenger.exoplayer2.metadata.Metadata;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.NalUnitUtil;
import org.telegram.messenger.exoplayer2.util.ParsableByteArray;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Extracts data from an unfragmented MP4 file.
 */
public final class Mp4Extractor implements Extractor, SeekMap {

  /**
   * Factory for {@link Mp4Extractor} instances.
   */
  public static final ExtractorsFactory FACTORY = new ExtractorsFactory() {

    @Override
    public Extractor[] createExtractors() {
      return new Extractor[] {new Mp4Extractor()};
    }

  };

  /**
   * Flags controlling the behavior of the extractor.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(flag = true, value = {FLAG_WORKAROUND_IGNORE_EDIT_LISTS})
  public @interface Flags {}
  /**
   * Flag to ignore any edit lists in the stream.
   */
  public static final int FLAG_WORKAROUND_IGNORE_EDIT_LISTS = 1;

  /**
   * Parser states.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({STATE_READING_ATOM_HEADER, STATE_READING_ATOM_PAYLOAD, STATE_READING_SAMPLE})
  private @interface State {}
  private static final int STATE_READING_ATOM_HEADER = 0;
  private static final int STATE_READING_ATOM_PAYLOAD = 1;
  private static final int STATE_READING_SAMPLE = 2;

  // Brand stored in the ftyp atom for QuickTime media.
  private static final int BRAND_QUICKTIME = Util.getIntegerCodeForString("qt  ");

  /**
   * When seeking within the source, if the offset is greater than or equal to this value (or the
   * offset is negative), the source will be reloaded.
   */
  private static final long RELOAD_MINIMUM_SEEK_DISTANCE = 256 * 1024;

  private final @Flags int flags;

  // Temporary arrays.
  private final ParsableByteArray nalStartCode;
  private final ParsableByteArray nalLength;

  private final ParsableByteArray atomHeader;
  private final Stack<ContainerAtom> containerAtoms;

  @State private int parserState;
  private int atomType;
  private long atomSize;
  private int atomHeaderBytesRead;
  private ParsableByteArray atomData;

  private int sampleBytesWritten;
  private int sampleCurrentNalBytesRemaining;

  // Extractor outputs.
  private ExtractorOutput extractorOutput;
  private Mp4Track[] tracks;
  private long durationUs;
  private boolean isQuickTime;

  /**
   * Creates a new extractor for unfragmented MP4 streams.
   */
  public Mp4Extractor() {
    this(0);
  }

  /**
   * Creates a new extractor for unfragmented MP4 streams, using the specified flags to control the
   * extractor's behavior.
   *
   * @param flags Flags that control the extractor's behavior.
   */
  public Mp4Extractor(@Flags int flags) {
    this.flags = flags;
    atomHeader = new ParsableByteArray(Atom.LONG_HEADER_SIZE);
    containerAtoms = new Stack<>();
    nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);
    nalLength = new ParsableByteArray(4);
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    return Sniffer.sniffUnfragmented(input);
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
  }

  @Override
  public void seek(long position, long timeUs) {
    containerAtoms.clear();
    atomHeaderBytesRead = 0;
    sampleBytesWritten = 0;
    sampleCurrentNalBytesRemaining = 0;
    if (position == 0) {
      enterReadingAtomHeaderState();
    } else if (tracks != null) {
      updateSampleIndices(timeUs);
    }
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    while (true) {
      switch (parserState) {
        case STATE_READING_ATOM_HEADER:
          if (!readAtomHeader(input)) {
            return RESULT_END_OF_INPUT;
          }
          break;
        case STATE_READING_ATOM_PAYLOAD:
          if (readAtomPayload(input, seekPosition)) {
            return RESULT_SEEK;
          }
          break;
        case STATE_READING_SAMPLE:
          return readSample(input, seekPosition);
        default:
          throw new IllegalStateException();
      }
    }
  }

  // SeekMap implementation.

  @Override
  public boolean isSeekable() {
    return true;
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  @Override
  public long getPosition(long timeUs) {
    long earliestSamplePosition = Long.MAX_VALUE;
    for (Mp4Track track : tracks) {
      TrackSampleTable sampleTable = track.sampleTable;
      int sampleIndex = sampleTable.getIndexOfEarlierOrEqualSynchronizationSample(timeUs);
      if (sampleIndex == C.INDEX_UNSET) {
        // Handle the case where the requested time is before the first synchronization sample.
        sampleIndex = sampleTable.getIndexOfLaterOrEqualSynchronizationSample(timeUs);
      }
      long offset = sampleTable.offsets[sampleIndex];
      if (offset < earliestSamplePosition) {
        earliestSamplePosition = offset;
      }
    }
    return earliestSamplePosition;
  }

  // Private methods.

  private void enterReadingAtomHeaderState() {
    parserState = STATE_READING_ATOM_HEADER;
    atomHeaderBytesRead = 0;
  }

  private boolean readAtomHeader(ExtractorInput input) throws IOException, InterruptedException {
    if (atomHeaderBytesRead == 0) {
      // Read the standard length atom header.
      if (!input.readFully(atomHeader.data, 0, Atom.HEADER_SIZE, true)) {
        return false;
      }
      atomHeaderBytesRead = Atom.HEADER_SIZE;
      atomHeader.setPosition(0);
      atomSize = atomHeader.readUnsignedInt();
      atomType = atomHeader.readInt();
    }

    if (atomSize == Atom.DEFINES_LARGE_SIZE) {
      // Read the large size.
      int headerBytesRemaining = Atom.LONG_HEADER_SIZE - Atom.HEADER_SIZE;
      input.readFully(atomHeader.data, Atom.HEADER_SIZE, headerBytesRemaining);
      atomHeaderBytesRead += headerBytesRemaining;
      atomSize = atomHeader.readUnsignedLongToLong();
    } else if (atomSize == Atom.EXTENDS_TO_END_SIZE) {
      // The atom extends to the end of the file. Note that if the atom is within a container we can
      // work out its size even if the input length is unknown.
      long endPosition = input.getLength();
      if (endPosition == C.LENGTH_UNSET && !containerAtoms.isEmpty()) {
        endPosition = containerAtoms.peek().endPosition;
      }
      if (endPosition != C.LENGTH_UNSET) {
        atomSize = endPosition - input.getPosition() + atomHeaderBytesRead;
      }
    }

    if (atomSize < atomHeaderBytesRead) {
      throw new ParserException("Atom size less than header length (unsupported).");
    }

    if (shouldParseContainerAtom(atomType)) {
      long endPosition = input.getPosition() + atomSize - atomHeaderBytesRead;
      containerAtoms.add(new ContainerAtom(atomType, endPosition));
      if (atomSize == atomHeaderBytesRead) {
        processAtomEnded(endPosition);
      } else {
        // Start reading the first child atom.
        enterReadingAtomHeaderState();
      }
    } else if (shouldParseLeafAtom(atomType)) {
      // We don't support parsing of leaf atoms that define extended atom sizes, or that have
      // lengths greater than Integer.MAX_VALUE.
      Assertions.checkState(atomHeaderBytesRead == Atom.HEADER_SIZE);
      Assertions.checkState(atomSize <= Integer.MAX_VALUE);
      atomData = new ParsableByteArray((int) atomSize);
      System.arraycopy(atomHeader.data, 0, atomData.data, 0, Atom.HEADER_SIZE);
      parserState = STATE_READING_ATOM_PAYLOAD;
    } else {
      atomData = null;
      parserState = STATE_READING_ATOM_PAYLOAD;
    }

    return true;
  }

  /**
   * Processes the atom payload. If {@link #atomData} is null and the size is at or above the
   * threshold {@link #RELOAD_MINIMUM_SEEK_DISTANCE}, {@code true} is returned and the caller should
   * restart loading at the position in {@code positionHolder}. Otherwise, the atom is read/skipped.
   */
  private boolean readAtomPayload(ExtractorInput input, PositionHolder positionHolder)
      throws IOException, InterruptedException {
    long atomPayloadSize = atomSize - atomHeaderBytesRead;
    long atomEndPosition = input.getPosition() + atomPayloadSize;
    boolean seekRequired = false;
    if (atomData != null) {
      input.readFully(atomData.data, atomHeaderBytesRead, (int) atomPayloadSize);
      if (atomType == Atom.TYPE_ftyp) {
        isQuickTime = processFtypAtom(atomData);
      } else if (!containerAtoms.isEmpty()) {
        containerAtoms.peek().add(new Atom.LeafAtom(atomType, atomData));
      }
    } else {
      // We don't need the data. Skip or seek, depending on how large the atom is.
      if (atomPayloadSize < RELOAD_MINIMUM_SEEK_DISTANCE) {
        input.skipFully((int) atomPayloadSize);
      } else {
        positionHolder.position = input.getPosition() + atomPayloadSize;
        seekRequired = true;
      }
    }
    processAtomEnded(atomEndPosition);
    return seekRequired && parserState != STATE_READING_SAMPLE;
  }

  private void processAtomEnded(long atomEndPosition) throws ParserException {
    while (!containerAtoms.isEmpty() && containerAtoms.peek().endPosition == atomEndPosition) {
      Atom.ContainerAtom containerAtom = containerAtoms.pop();
      if (containerAtom.type == Atom.TYPE_moov) {
        // We've reached the end of the moov atom. Process it and prepare to read samples.
        processMoovAtom(containerAtom);
        containerAtoms.clear();
        parserState = STATE_READING_SAMPLE;
      } else if (!containerAtoms.isEmpty()) {
        containerAtoms.peek().add(containerAtom);
      }
    }
    if (parserState != STATE_READING_SAMPLE) {
      enterReadingAtomHeaderState();
    }
  }

  /**
   * Process an ftyp atom to determine whether the media is QuickTime.
   *
   * @param atomData The ftyp atom data.
   * @return Whether the media is QuickTime.
   */
  private static boolean processFtypAtom(ParsableByteArray atomData) {
    atomData.setPosition(Atom.HEADER_SIZE);
    int majorBrand = atomData.readInt();
    if (majorBrand == BRAND_QUICKTIME) {
      return true;
    }
    atomData.skipBytes(4); // minor_version
    while (atomData.bytesLeft() > 0) {
      if (atomData.readInt() == BRAND_QUICKTIME) {
        return true;
      }
    }
    return false;
  }

  /**
   * Updates the stored track metadata to reflect the contents of the specified moov atom.
   */
  private void processMoovAtom(ContainerAtom moov) throws ParserException {
    long durationUs = C.TIME_UNSET;
    List<Mp4Track> tracks = new ArrayList<>();
    long earliestSampleOffset = Long.MAX_VALUE;

    Metadata metadata = null;
    GaplessInfoHolder gaplessInfoHolder = new GaplessInfoHolder();
    Atom.LeafAtom udta = moov.getLeafAtomOfType(Atom.TYPE_udta);
    if (udta != null) {
      metadata = AtomParsers.parseUdta(udta, isQuickTime);
      if (metadata != null) {
        gaplessInfoHolder.setFromMetadata(metadata);
      }
    }

    for (int i = 0; i < moov.containerChildren.size(); i++) {
      Atom.ContainerAtom atom = moov.containerChildren.get(i);
      if (atom.type != Atom.TYPE_trak) {
        continue;
      }

      Track track = AtomParsers.parseTrak(atom, moov.getLeafAtomOfType(Atom.TYPE_mvhd),
          C.TIME_UNSET, null, (flags & FLAG_WORKAROUND_IGNORE_EDIT_LISTS) != 0, isQuickTime);
      if (track == null) {
        continue;
      }

      Atom.ContainerAtom stblAtom = atom.getContainerAtomOfType(Atom.TYPE_mdia)
          .getContainerAtomOfType(Atom.TYPE_minf).getContainerAtomOfType(Atom.TYPE_stbl);
      TrackSampleTable trackSampleTable = AtomParsers.parseStbl(track, stblAtom, gaplessInfoHolder);
      if (trackSampleTable.sampleCount == 0) {
        continue;
      }

      Mp4Track mp4Track = new Mp4Track(track, trackSampleTable,
          extractorOutput.track(i, track.type));
      // Each sample has up to three bytes of overhead for the start code that replaces its length.
      // Allow ten source samples per output sample, like the platform extractor.
      int maxInputSize = trackSampleTable.maximumSize + 3 * 10;
      Format format = track.format.copyWithMaxInputSize(maxInputSize);
      if (track.type == C.TRACK_TYPE_AUDIO) {
        if (gaplessInfoHolder.hasGaplessInfo()) {
          format = format.copyWithGaplessInfo(gaplessInfoHolder.encoderDelay,
              gaplessInfoHolder.encoderPadding);
        }
        if (metadata != null) {
          format = format.copyWithMetadata(metadata);
        }
      }
      mp4Track.trackOutput.format(format);

      durationUs = Math.max(durationUs, track.durationUs);
      tracks.add(mp4Track);

      long firstSampleOffset = trackSampleTable.offsets[0];
      if (firstSampleOffset < earliestSampleOffset) {
        earliestSampleOffset = firstSampleOffset;
      }
    }
    this.durationUs = durationUs;
    this.tracks = tracks.toArray(new Mp4Track[tracks.size()]);
    extractorOutput.endTracks();
    extractorOutput.seekMap(this);
  }

  /**
   * Attempts to extract the next sample in the current mdat atom for the specified track.
   * <p>
   * Returns {@link #RESULT_SEEK} if the source should be reloaded from the position in
   * {@code positionHolder}.
   * <p>
   * Returns {@link #RESULT_END_OF_INPUT} if no samples are left. Otherwise, returns
   * {@link #RESULT_CONTINUE}.
   *
   * @param input The {@link ExtractorInput} from which to read data.
   * @param positionHolder If {@link #RESULT_SEEK} is returned, this holder is updated to hold the
   *     position of the required data.
   * @return One of the {@code RESULT_*} flags in {@link Extractor}.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread is interrupted.
   */
  private int readSample(ExtractorInput input, PositionHolder positionHolder)
      throws IOException, InterruptedException {
    int trackIndex = getTrackIndexOfEarliestCurrentSample();
    if (trackIndex == C.INDEX_UNSET) {
      return RESULT_END_OF_INPUT;
    }
    Mp4Track track = tracks[trackIndex];
    TrackOutput trackOutput = track.trackOutput;
    int sampleIndex = track.sampleIndex;
    long position = track.sampleTable.offsets[sampleIndex];
    int sampleSize = track.sampleTable.sizes[sampleIndex];
    if (track.track.sampleTransformation == Track.TRANSFORMATION_CEA608_CDAT) {
      // The sample information is contained in a cdat atom. The header must be discarded for
      // committing.
      position += Atom.HEADER_SIZE;
      sampleSize -= Atom.HEADER_SIZE;
    }
    long skipAmount = position - input.getPosition() + sampleBytesWritten;
    if (skipAmount < 0 || skipAmount >= RELOAD_MINIMUM_SEEK_DISTANCE) {
      positionHolder.position = position;
      return RESULT_SEEK;
    }
    input.skipFully((int) skipAmount);
    if (track.track.nalUnitLengthFieldLength != 0) {
      // Zero the top three bytes of the array that we'll use to decode nal unit lengths, in case
      // they're only 1 or 2 bytes long.
      byte[] nalLengthData = nalLength.data;
      nalLengthData[0] = 0;
      nalLengthData[1] = 0;
      nalLengthData[2] = 0;
      int nalUnitLengthFieldLength = track.track.nalUnitLengthFieldLength;
      int nalUnitLengthFieldLengthDiff = 4 - track.track.nalUnitLengthFieldLength;
      // NAL units are length delimited, but the decoder requires start code delimited units.
      // Loop until we've written the sample to the track output, replacing length delimiters with
      // start codes as we encounter them.
      while (sampleBytesWritten < sampleSize) {
        if (sampleCurrentNalBytesRemaining == 0) {
          // Read the NAL length so that we know where we find the next one.
          input.readFully(nalLength.data, nalUnitLengthFieldLengthDiff, nalUnitLengthFieldLength);
          nalLength.setPosition(0);
          sampleCurrentNalBytesRemaining = nalLength.readUnsignedIntToInt();
          // Write a start code for the current NAL unit.
          nalStartCode.setPosition(0);
          trackOutput.sampleData(nalStartCode, 4);
          sampleBytesWritten += 4;
          sampleSize += nalUnitLengthFieldLengthDiff;
        } else {
          // Write the payload of the NAL unit.
          int writtenBytes = trackOutput.sampleData(input, sampleCurrentNalBytesRemaining, false);
          sampleBytesWritten += writtenBytes;
          sampleCurrentNalBytesRemaining -= writtenBytes;
        }
      }
    } else {
      while (sampleBytesWritten < sampleSize) {
        int writtenBytes = trackOutput.sampleData(input, sampleSize - sampleBytesWritten, false);
        sampleBytesWritten += writtenBytes;
        sampleCurrentNalBytesRemaining -= writtenBytes;
      }
    }
    trackOutput.sampleMetadata(track.sampleTable.timestampsUs[sampleIndex],
        track.sampleTable.flags[sampleIndex], sampleSize, 0, null);
    track.sampleIndex++;
    sampleBytesWritten = 0;
    sampleCurrentNalBytesRemaining = 0;
    return RESULT_CONTINUE;
  }

  /**
   * Returns the index of the track that contains the earliest current sample, or
   * {@link C#INDEX_UNSET} if no samples remain.
   */
  private int getTrackIndexOfEarliestCurrentSample() {
    int earliestSampleTrackIndex = C.INDEX_UNSET;
    long earliestSampleOffset = Long.MAX_VALUE;
    for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
      Mp4Track track = tracks[trackIndex];
      int sampleIndex = track.sampleIndex;
      if (sampleIndex == track.sampleTable.sampleCount) {
        continue;
      }

      long trackSampleOffset = track.sampleTable.offsets[sampleIndex];
      if (trackSampleOffset < earliestSampleOffset) {
        earliestSampleOffset = trackSampleOffset;
        earliestSampleTrackIndex = trackIndex;
      }
    }

    return earliestSampleTrackIndex;
  }

  /**
   * Updates every track's sample index to point its latest sync sample before/at {@code timeUs}.
   */
  private void updateSampleIndices(long timeUs) {
    for (Mp4Track track : tracks) {
      TrackSampleTable sampleTable = track.sampleTable;
      int sampleIndex = sampleTable.getIndexOfEarlierOrEqualSynchronizationSample(timeUs);
      if (sampleIndex == C.INDEX_UNSET) {
        // Handle the case where the requested time is before the first synchronization sample.
        sampleIndex = sampleTable.getIndexOfLaterOrEqualSynchronizationSample(timeUs);
      }
      track.sampleIndex = sampleIndex;
    }
  }

  /**
   * Returns whether the extractor should decode a leaf atom with type {@code atom}.
   */
  private static boolean shouldParseLeafAtom(int atom) {
    return atom == Atom.TYPE_mdhd || atom == Atom.TYPE_mvhd || atom == Atom.TYPE_hdlr
        || atom == Atom.TYPE_stsd || atom == Atom.TYPE_stts || atom == Atom.TYPE_stss
        || atom == Atom.TYPE_ctts || atom == Atom.TYPE_elst || atom == Atom.TYPE_stsc
        || atom == Atom.TYPE_stsz || atom == Atom.TYPE_stz2 || atom == Atom.TYPE_stco
        || atom == Atom.TYPE_co64 || atom == Atom.TYPE_tkhd || atom == Atom.TYPE_ftyp
        || atom == Atom.TYPE_udta;
  }

  /**
   * Returns whether the extractor should decode a container atom with type {@code atom}.
   */
  private static boolean shouldParseContainerAtom(int atom) {
    return atom == Atom.TYPE_moov || atom == Atom.TYPE_trak || atom == Atom.TYPE_mdia
        || atom == Atom.TYPE_minf || atom == Atom.TYPE_stbl || atom == Atom.TYPE_edts;
  }

  private static final class Mp4Track {

    public final Track track;
    public final TrackSampleTable sampleTable;
    public final TrackOutput trackOutput;

    public int sampleIndex;

    public Mp4Track(Track track, TrackSampleTable sampleTable, TrackOutput trackOutput) {
      this.track = track;
      this.sampleTable = sampleTable;
      this.trackOutput = trackOutput;
    }

  }

}
