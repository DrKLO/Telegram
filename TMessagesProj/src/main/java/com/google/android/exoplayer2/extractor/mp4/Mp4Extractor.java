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
package com.google.android.exoplayer2.extractor.mp4;

import static com.google.android.exoplayer2.extractor.mp4.AtomParsers.parseTraks;
import static com.google.android.exoplayer2.extractor.mp4.Sniffer.BRAND_HEIC;
import static com.google.android.exoplayer2.extractor.mp4.Sniffer.BRAND_QUICKTIME;
import static com.google.android.exoplayer2.util.Util.castNonNull;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.util.Pair;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.audio.Ac3Util;
import com.google.android.exoplayer2.audio.Ac4Util;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.GaplessInfoHolder;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.TrueHdSampleRechunker;
import com.google.android.exoplayer2.extractor.mp4.Atom.ContainerAtom;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.mp4.MotionPhotoMetadata;
import com.google.android.exoplayer2.metadata.mp4.SlowMotionData;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Extracts data from the MP4 container format. */
public final class Mp4Extractor implements Extractor, SeekMap {

  /** Factory for {@link Mp4Extractor} instances. */
  public static final ExtractorsFactory FACTORY = () -> new Extractor[] {new Mp4Extractor()};

  /**
   * Flags controlling the behavior of the extractor. Possible flag values are {@link
   * #FLAG_WORKAROUND_IGNORE_EDIT_LISTS}, {@link #FLAG_READ_MOTION_PHOTO_METADATA} and {@link
   * #FLAG_READ_SEF_DATA}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef(
      flag = true,
      value = {
        FLAG_WORKAROUND_IGNORE_EDIT_LISTS,
        FLAG_READ_MOTION_PHOTO_METADATA,
        FLAG_READ_SEF_DATA
      })
  public @interface Flags {}
  /** Flag to ignore any edit lists in the stream. */
  public static final int FLAG_WORKAROUND_IGNORE_EDIT_LISTS = 1;
  /**
   * Flag to extract {@link MotionPhotoMetadata} from HEIC motion photos following the Google Photos
   * Motion Photo File Format V1.1.
   *
   * <p>As playback is not supported for motion photos, this flag should only be used for metadata
   * retrieval use cases.
   */
  public static final int FLAG_READ_MOTION_PHOTO_METADATA = 1 << 1;
  /**
   * Flag to extract {@link SlowMotionData} metadata from Samsung Extension Format (SEF) slow motion
   * videos.
   */
  public static final int FLAG_READ_SEF_DATA = 1 << 2;

  /** Parser states. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    STATE_READING_ATOM_HEADER,
    STATE_READING_ATOM_PAYLOAD,
    STATE_READING_SAMPLE,
    STATE_READING_SEF,
  })
  private @interface State {}

  private static final int STATE_READING_ATOM_HEADER = 0;
  private static final int STATE_READING_ATOM_PAYLOAD = 1;
  private static final int STATE_READING_SAMPLE = 2;
  private static final int STATE_READING_SEF = 3;

  /** Supported file types. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({FILE_TYPE_MP4, FILE_TYPE_QUICKTIME, FILE_TYPE_HEIC})
  private @interface FileType {}

  private static final int FILE_TYPE_MP4 = 0;
  private static final int FILE_TYPE_QUICKTIME = 1;
  private static final int FILE_TYPE_HEIC = 2;

  /**
   * When seeking within the source, if the offset is greater than or equal to this value (or the
   * offset is negative), the source will be reloaded.
   */
  private static final long RELOAD_MINIMUM_SEEK_DISTANCE = 256 * 1024;

  /**
   * For poorly interleaved streams, the maximum byte difference one track is allowed to be read
   * ahead before the source will be reloaded at a new position to read another track.
   */
  private static final long MAXIMUM_READ_AHEAD_BYTES_STREAM = 512 * 1024;

  private final @Flags int flags;

  // Temporary arrays.
  private final ParsableByteArray nalStartCode;
  private final ParsableByteArray nalLength;
  private final ParsableByteArray scratch;

  private final ParsableByteArray atomHeader;
  private final ArrayDeque<ContainerAtom> containerAtoms;
  private final SefReader sefReader;
  private final List<Metadata.Entry> slowMotionMetadataEntries;

  private @State int parserState;
  private int atomType;
  private long atomSize;
  private int atomHeaderBytesRead;
  @Nullable private ParsableByteArray atomData;

  private int sampleTrackIndex;
  private int sampleBytesRead;
  private int sampleBytesWritten;
  private int sampleCurrentNalBytesRemaining;

  // Extractor outputs.
  private ExtractorOutput extractorOutput;
  private Mp4Track[] tracks;

  private long @MonotonicNonNull [][] accumulatedSampleSizes;
  private int firstVideoTrackIndex;
  private long durationUs;
  private @FileType int fileType;
  @Nullable private MotionPhotoMetadata motionPhotoMetadata;

  /** Creates a new extractor for unfragmented MP4 streams. */
  public Mp4Extractor() {
    this(/* flags= */ 0);
  }

  /**
   * Creates a new extractor for unfragmented MP4 streams, using the specified flags to control the
   * extractor's behavior.
   *
   * @param flags Flags that control the extractor's behavior.
   */
  public Mp4Extractor(@Flags int flags) {
    this.flags = flags;
    parserState =
        ((flags & FLAG_READ_SEF_DATA) != 0) ? STATE_READING_SEF : STATE_READING_ATOM_HEADER;
    sefReader = new SefReader();
    slowMotionMetadataEntries = new ArrayList<>();
    atomHeader = new ParsableByteArray(Atom.LONG_HEADER_SIZE);
    containerAtoms = new ArrayDeque<>();
    nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);
    nalLength = new ParsableByteArray(4);
    scratch = new ParsableByteArray();
    sampleTrackIndex = C.INDEX_UNSET;
    extractorOutput = ExtractorOutput.PLACEHOLDER;
    tracks = new Mp4Track[0];
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    return Sniffer.sniffUnfragmented(
        input, /* acceptHeic= */ (flags & FLAG_READ_MOTION_PHOTO_METADATA) != 0);
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
  }

  @Override
  public void seek(long position, long timeUs) {
    containerAtoms.clear();
    atomHeaderBytesRead = 0;
    sampleTrackIndex = C.INDEX_UNSET;
    sampleBytesRead = 0;
    sampleBytesWritten = 0;
    sampleCurrentNalBytesRemaining = 0;
    if (position == 0) {
      // Reading the SEF data occurs before normal MP4 parsing. Therefore we can not transition to
      // reading the atom header until that has completed.
      if (parserState != STATE_READING_SEF) {
        enterReadingAtomHeaderState();
      } else {
        sefReader.reset();
        slowMotionMetadataEntries.clear();
      }
    } else {
      for (Mp4Track track : tracks) {
        updateSampleIndex(track, timeUs);
        if (track.trueHdSampleRechunker != null) {
          track.trueHdSampleRechunker.reset();
        }
      }
    }
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
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
        case STATE_READING_SEF:
          return readSefData(input, seekPosition);
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
  public SeekPoints getSeekPoints(long timeUs) {
    return getSeekPoints(timeUs, /* trackId= */ C.INDEX_UNSET);
  }

  // Non-inherited public methods.

  /**
   * Equivalent to {@link SeekMap#getSeekPoints(long)}, except it adds the {@code trackId}
   * parameter.
   *
   * @param timeUs A seek time in microseconds.
   * @param trackId The id of the track on which to seek for {@link SeekPoints}. May be {@link
   *     C#INDEX_UNSET} if the extractor is expected to define the strategy for generating {@link
   *     SeekPoints}.
   * @return The corresponding seek points.
   */
  public SeekPoints getSeekPoints(long timeUs, int trackId) {
    if (tracks.length == 0) {
      return new SeekPoints(SeekPoint.START);
    }

    long firstTimeUs;
    long firstOffset;
    long secondTimeUs = C.TIME_UNSET;
    long secondOffset = C.POSITION_UNSET;

    // Note that the id matches the index in tracks.
    int mainTrackIndex = trackId != C.INDEX_UNSET ? trackId : firstVideoTrackIndex;
    // If we have a video track, use it to establish one or two seek points.
    if (mainTrackIndex != C.INDEX_UNSET) {
      TrackSampleTable sampleTable = tracks[mainTrackIndex].sampleTable;
      int sampleIndex = getSynchronizationSampleIndex(sampleTable, timeUs);
      if (sampleIndex == C.INDEX_UNSET) {
        return new SeekPoints(SeekPoint.START);
      }
      long sampleTimeUs = sampleTable.timestampsUs[sampleIndex];
      firstTimeUs = sampleTimeUs;
      firstOffset = sampleTable.offsets[sampleIndex];
      if (sampleTimeUs < timeUs && sampleIndex < sampleTable.sampleCount - 1) {
        int secondSampleIndex = sampleTable.getIndexOfLaterOrEqualSynchronizationSample(timeUs);
        if (secondSampleIndex != C.INDEX_UNSET && secondSampleIndex != sampleIndex) {
          secondTimeUs = sampleTable.timestampsUs[secondSampleIndex];
          secondOffset = sampleTable.offsets[secondSampleIndex];
        }
      }
    } else {
      firstTimeUs = timeUs;
      firstOffset = Long.MAX_VALUE;
    }

    if (trackId == C.INDEX_UNSET) {
      // Take into account other tracks, but only if the caller has not specified a trackId.
      for (int i = 0; i < tracks.length; i++) {
        if (i != firstVideoTrackIndex) {
          TrackSampleTable sampleTable = tracks[i].sampleTable;
          firstOffset = maybeAdjustSeekOffset(sampleTable, firstTimeUs, firstOffset);
          if (secondTimeUs != C.TIME_UNSET) {
            secondOffset = maybeAdjustSeekOffset(sampleTable, secondTimeUs, secondOffset);
          }
        }
      }
    }

    SeekPoint firstSeekPoint = new SeekPoint(firstTimeUs, firstOffset);
    if (secondTimeUs == C.TIME_UNSET) {
      return new SeekPoints(firstSeekPoint);
    } else {
      SeekPoint secondSeekPoint = new SeekPoint(secondTimeUs, secondOffset);
      return new SeekPoints(firstSeekPoint, secondSeekPoint);
    }
  }

  // Private methods.

  private void enterReadingAtomHeaderState() {
    parserState = STATE_READING_ATOM_HEADER;
    atomHeaderBytesRead = 0;
  }

  private boolean readAtomHeader(ExtractorInput input) throws IOException {
    if (atomHeaderBytesRead == 0) {
      // Read the standard length atom header.
      if (!input.readFully(atomHeader.getData(), 0, Atom.HEADER_SIZE, true)) {
        processEndOfStreamReadingAtomHeader();
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
      input.readFully(atomHeader.getData(), Atom.HEADER_SIZE, headerBytesRemaining);
      atomHeaderBytesRead += headerBytesRemaining;
      atomSize = atomHeader.readUnsignedLongToLong();
    } else if (atomSize == Atom.EXTENDS_TO_END_SIZE) {
      // The atom extends to the end of the file. Note that if the atom is within a container we can
      // work out its size even if the input length is unknown.
      long endPosition = input.getLength();
      if (endPosition == C.LENGTH_UNSET) {
        @Nullable ContainerAtom containerAtom = containerAtoms.peek();
        if (containerAtom != null) {
          endPosition = containerAtom.endPosition;
        }
      }
      if (endPosition != C.LENGTH_UNSET) {
        atomSize = endPosition - input.getPosition() + atomHeaderBytesRead;
      }
    }

    if (atomSize < atomHeaderBytesRead) {
      throw ParserException.createForUnsupportedContainerFeature(
          "Atom size less than header length (unsupported).");
    }

    if (shouldParseContainerAtom(atomType)) {
      long endPosition = input.getPosition() + atomSize - atomHeaderBytesRead;
      if (atomSize != atomHeaderBytesRead && atomType == Atom.TYPE_meta) {
        maybeSkipRemainingMetaAtomHeaderBytes(input);
      }
      containerAtoms.push(new ContainerAtom(atomType, endPosition));
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
      ParsableByteArray atomData = new ParsableByteArray((int) atomSize);
      System.arraycopy(atomHeader.getData(), 0, atomData.getData(), 0, Atom.HEADER_SIZE);
      this.atomData = atomData;
      parserState = STATE_READING_ATOM_PAYLOAD;
    } else {
      processUnparsedAtom(input.getPosition() - atomHeaderBytesRead);
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
      throws IOException {
    long atomPayloadSize = atomSize - atomHeaderBytesRead;
    long atomEndPosition = input.getPosition() + atomPayloadSize;
    boolean seekRequired = false;
    @Nullable ParsableByteArray atomData = this.atomData;
    if (atomData != null) {
      input.readFully(atomData.getData(), atomHeaderBytesRead, (int) atomPayloadSize);
      if (atomType == Atom.TYPE_ftyp) {
        fileType = processFtypAtom(atomData);
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

  private @ReadResult int readSefData(ExtractorInput input, PositionHolder seekPosition)
      throws IOException {
    @ReadResult int result = sefReader.read(input, seekPosition, slowMotionMetadataEntries);
    if (result == RESULT_SEEK && seekPosition.position == 0) {
      enterReadingAtomHeaderState();
    }
    return result;
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

  /** Updates the stored track metadata to reflect the contents of the specified moov atom. */
  private void processMoovAtom(ContainerAtom moov) throws ParserException {
    int firstVideoTrackIndex = C.INDEX_UNSET;
    long durationUs = C.TIME_UNSET;
    List<Mp4Track> tracks = new ArrayList<>();

    // Process metadata.
    @Nullable Metadata udtaMetaMetadata = null;
    @Nullable Metadata smtaMetadata = null;
    boolean isQuickTime = fileType == FILE_TYPE_QUICKTIME;
    GaplessInfoHolder gaplessInfoHolder = new GaplessInfoHolder();
    @Nullable Atom.LeafAtom udta = moov.getLeafAtomOfType(Atom.TYPE_udta);
    if (udta != null) {
      Pair<@NullableType Metadata, @NullableType Metadata> udtaMetadata =
          AtomParsers.parseUdta(udta);
      udtaMetaMetadata = udtaMetadata.first;
      smtaMetadata = udtaMetadata.second;
      if (udtaMetaMetadata != null) {
        gaplessInfoHolder.setFromMetadata(udtaMetaMetadata);
      }
    }
    @Nullable Metadata mdtaMetadata = null;
    @Nullable Atom.ContainerAtom meta = moov.getContainerAtomOfType(Atom.TYPE_meta);
    if (meta != null) {
      mdtaMetadata = AtomParsers.parseMdtaFromMeta(meta);
    }

    boolean ignoreEditLists = (flags & FLAG_WORKAROUND_IGNORE_EDIT_LISTS) != 0;
    List<TrackSampleTable> trackSampleTables =
        parseTraks(
            moov,
            gaplessInfoHolder,
            /* duration= */ C.TIME_UNSET,
            /* drmInitData= */ null,
            ignoreEditLists,
            isQuickTime,
            /* modifyTrackFunction= */ track -> track);

    int trackCount = trackSampleTables.size();
    for (int i = 0; i < trackCount; i++) {
      TrackSampleTable trackSampleTable = trackSampleTables.get(i);
      if (trackSampleTable.sampleCount == 0) {
        continue;
      }
      Track track = trackSampleTable.track;
      long trackDurationUs =
          track.durationUs != C.TIME_UNSET ? track.durationUs : trackSampleTable.durationUs;
      durationUs = max(durationUs, trackDurationUs);
      Mp4Track mp4Track =
          new Mp4Track(track, trackSampleTable, extractorOutput.track(i, track.type));

      int maxInputSize;
      if (MimeTypes.AUDIO_TRUEHD.equals(track.format.sampleMimeType)) {
        // TrueHD groups samples per chunks of TRUEHD_RECHUNK_SAMPLE_COUNT samples.
        maxInputSize = trackSampleTable.maximumSize * Ac3Util.TRUEHD_RECHUNK_SAMPLE_COUNT;
      } else {
        // Each sample has up to three bytes of overhead for the start code that replaces its
        // length. Allow ten source samples per output sample, like the platform extractor.
        maxInputSize = trackSampleTable.maximumSize + 3 * 10;
      }

      Format.Builder formatBuilder = track.format.buildUpon();
      formatBuilder.setMaxInputSize(maxInputSize);
      if (track.type == C.TRACK_TYPE_VIDEO
          && trackDurationUs > 0
          && trackSampleTable.sampleCount > 1) {
        float frameRate = trackSampleTable.sampleCount / (trackDurationUs / 1000000f);
        formatBuilder.setFrameRate(frameRate);
      }

      MetadataUtil.setFormatGaplessInfo(track.type, gaplessInfoHolder, formatBuilder);
      MetadataUtil.setFormatMetadata(
          track.type,
          udtaMetaMetadata,
          mdtaMetadata,
          formatBuilder,
          smtaMetadata,
          slowMotionMetadataEntries.isEmpty() ? null : new Metadata(slowMotionMetadataEntries));
      mp4Track.trackOutput.format(formatBuilder.build());

      if (track.type == C.TRACK_TYPE_VIDEO && firstVideoTrackIndex == C.INDEX_UNSET) {
        firstVideoTrackIndex = tracks.size();
      }
      tracks.add(mp4Track);
    }
    this.firstVideoTrackIndex = firstVideoTrackIndex;
    this.durationUs = durationUs;
    this.tracks = tracks.toArray(new Mp4Track[0]);
    accumulatedSampleSizes = calculateAccumulatedSampleSizes(this.tracks);

    extractorOutput.endTracks();
    extractorOutput.seekMap(this);
  }

  /**
   * Attempts to extract the next sample in the current mdat atom for the specified track.
   *
   * <p>Returns {@link #RESULT_SEEK} if the source should be reloaded from the position in {@code
   * positionHolder}.
   *
   * <p>Returns {@link #RESULT_END_OF_INPUT} if no samples are left. Otherwise, returns {@link
   * #RESULT_CONTINUE}.
   *
   * @param input The {@link ExtractorInput} from which to read data.
   * @param positionHolder If {@link #RESULT_SEEK} is returned, this holder is updated to hold the
   *     position of the required data.
   * @return One of the {@code RESULT_*} flags in {@link Extractor}.
   * @throws IOException If an error occurs reading from the input.
   */
  private int readSample(ExtractorInput input, PositionHolder positionHolder) throws IOException {
    long inputPosition = input.getPosition();
    if (sampleTrackIndex == C.INDEX_UNSET) {
      sampleTrackIndex = getTrackIndexOfNextReadSample(inputPosition);
      if (sampleTrackIndex == C.INDEX_UNSET) {
        return RESULT_END_OF_INPUT;
      }
    }
    Mp4Track track = tracks[sampleTrackIndex];
    TrackOutput trackOutput = track.trackOutput;
    int sampleIndex = track.sampleIndex;
    long position = track.sampleTable.offsets[sampleIndex];
    int sampleSize = track.sampleTable.sizes[sampleIndex];
    @Nullable TrueHdSampleRechunker trueHdSampleRechunker = track.trueHdSampleRechunker;
    long skipAmount = position - inputPosition + sampleBytesRead;
    if (skipAmount < 0 || skipAmount >= RELOAD_MINIMUM_SEEK_DISTANCE) {
      positionHolder.position = position;
      return RESULT_SEEK;
    }
    if (track.track.sampleTransformation == Track.TRANSFORMATION_CEA608_CDAT) {
      // The sample information is contained in a cdat atom. The header must be discarded for
      // committing.
      skipAmount += Atom.HEADER_SIZE;
      sampleSize -= Atom.HEADER_SIZE;
    }
    input.skipFully((int) skipAmount);
    if (track.track.nalUnitLengthFieldLength != 0) {
      // Zero the top three bytes of the array that we'll use to decode nal unit lengths, in case
      // they're only 1 or 2 bytes long.
      byte[] nalLengthData = nalLength.getData();
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
          input.readFully(nalLengthData, nalUnitLengthFieldLengthDiff, nalUnitLengthFieldLength);
          sampleBytesRead += nalUnitLengthFieldLength;
          nalLength.setPosition(0);
          int nalLengthInt = nalLength.readInt();
          if (nalLengthInt < 0) {
            throw ParserException.createForMalformedContainer(
                "Invalid NAL length", /* cause= */ null);
          }
          sampleCurrentNalBytesRemaining = nalLengthInt;
          // Write a start code for the current NAL unit.
          nalStartCode.setPosition(0);
          trackOutput.sampleData(nalStartCode, 4);
          sampleBytesWritten += 4;
          sampleSize += nalUnitLengthFieldLengthDiff;
        } else {
          // Write the payload of the NAL unit.
          int writtenBytes = trackOutput.sampleData(input, sampleCurrentNalBytesRemaining, false);
          sampleBytesRead += writtenBytes;
          sampleBytesWritten += writtenBytes;
          sampleCurrentNalBytesRemaining -= writtenBytes;
        }
      }
    } else {
      if (MimeTypes.AUDIO_AC4.equals(track.track.format.sampleMimeType)) {
        if (sampleBytesWritten == 0) {
          Ac4Util.getAc4SampleHeader(sampleSize, scratch);
          trackOutput.sampleData(scratch, Ac4Util.SAMPLE_HEADER_SIZE);
          sampleBytesWritten += Ac4Util.SAMPLE_HEADER_SIZE;
        }
        sampleSize += Ac4Util.SAMPLE_HEADER_SIZE;
      } else if (trueHdSampleRechunker != null) {
        trueHdSampleRechunker.startSample(input);
      }

      while (sampleBytesWritten < sampleSize) {
        int writtenBytes = trackOutput.sampleData(input, sampleSize - sampleBytesWritten, false);
        sampleBytesRead += writtenBytes;
        sampleBytesWritten += writtenBytes;
        sampleCurrentNalBytesRemaining -= writtenBytes;
      }
    }

    long timeUs = track.sampleTable.timestampsUs[sampleIndex];
    @C.BufferFlags int flags = track.sampleTable.flags[sampleIndex];
    if (trueHdSampleRechunker != null) {
      trueHdSampleRechunker.sampleMetadata(
          trackOutput, timeUs, flags, sampleSize, /* offset= */ 0, /* cryptoData= */ null);
      if (sampleIndex + 1 == track.sampleTable.sampleCount) {
        trueHdSampleRechunker.outputPendingSampleMetadata(trackOutput, /* cryptoData= */ null);
      }
    } else {
      trackOutput.sampleMetadata(
          timeUs, flags, sampleSize, /* offset= */ 0, /* cryptoData= */ null);
    }

    track.sampleIndex++;
    sampleTrackIndex = C.INDEX_UNSET;
    sampleBytesRead = 0;
    sampleBytesWritten = 0;
    sampleCurrentNalBytesRemaining = 0;
    return RESULT_CONTINUE;
  }

  /**
   * Returns the index of the track that contains the next sample to be read, or {@link
   * C#INDEX_UNSET} if no samples remain.
   *
   * <p>The preferred choice is the sample with the smallest offset not requiring a source reload,
   * or if not available the sample with the smallest overall offset to avoid subsequent source
   * reloads.
   *
   * <p>To deal with poor sample interleaving, we also check whether the required memory to catch up
   * with the next logical sample (based on sample time) exceeds {@link
   * #MAXIMUM_READ_AHEAD_BYTES_STREAM}. If this is the case, we continue with this sample even
   * though it may require a source reload.
   */
  private int getTrackIndexOfNextReadSample(long inputPosition) {
    long preferredSkipAmount = Long.MAX_VALUE;
    boolean preferredRequiresReload = true;
    int preferredTrackIndex = C.INDEX_UNSET;
    long preferredAccumulatedBytes = Long.MAX_VALUE;
    long minAccumulatedBytes = Long.MAX_VALUE;
    boolean minAccumulatedBytesRequiresReload = true;
    int minAccumulatedBytesTrackIndex = C.INDEX_UNSET;
    for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
      Mp4Track track = tracks[trackIndex];
      int sampleIndex = track.sampleIndex;
      if (sampleIndex == track.sampleTable.sampleCount) {
        continue;
      }
      long sampleOffset = track.sampleTable.offsets[sampleIndex];
      long sampleAccumulatedBytes = castNonNull(accumulatedSampleSizes)[trackIndex][sampleIndex];
      long skipAmount = sampleOffset - inputPosition;
      boolean requiresReload = skipAmount < 0 || skipAmount >= RELOAD_MINIMUM_SEEK_DISTANCE;
      if ((!requiresReload && preferredRequiresReload)
          || (requiresReload == preferredRequiresReload && skipAmount < preferredSkipAmount)) {
        preferredRequiresReload = requiresReload;
        preferredSkipAmount = skipAmount;
        preferredTrackIndex = trackIndex;
        preferredAccumulatedBytes = sampleAccumulatedBytes;
      }
      if (sampleAccumulatedBytes < minAccumulatedBytes) {
        minAccumulatedBytes = sampleAccumulatedBytes;
        minAccumulatedBytesRequiresReload = requiresReload;
        minAccumulatedBytesTrackIndex = trackIndex;
      }
    }
    return minAccumulatedBytes == Long.MAX_VALUE
            || !minAccumulatedBytesRequiresReload
            || preferredAccumulatedBytes < minAccumulatedBytes + MAXIMUM_READ_AHEAD_BYTES_STREAM
        ? preferredTrackIndex
        : minAccumulatedBytesTrackIndex;
  }

  /** Updates a track's sample index to point its latest sync sample before/at {@code timeUs}. */
  private void updateSampleIndex(Mp4Track track, long timeUs) {
    TrackSampleTable sampleTable = track.sampleTable;
    int sampleIndex = sampleTable.getIndexOfEarlierOrEqualSynchronizationSample(timeUs);
    if (sampleIndex == C.INDEX_UNSET) {
      // Handle the case where the requested time is before the first synchronization sample.
      sampleIndex = sampleTable.getIndexOfLaterOrEqualSynchronizationSample(timeUs);
    }
    track.sampleIndex = sampleIndex;
  }

  /** Processes the end of stream in case there is not atom left to read. */
  private void processEndOfStreamReadingAtomHeader() {
    if (fileType == FILE_TYPE_HEIC && (flags & FLAG_READ_MOTION_PHOTO_METADATA) != 0) {
      // Add image track and prepare media.
      TrackOutput trackOutput = extractorOutput.track(/* id= */ 0, C.TRACK_TYPE_IMAGE);
      @Nullable
      Metadata metadata = motionPhotoMetadata == null ? null : new Metadata(motionPhotoMetadata);
      trackOutput.format(new Format.Builder().setMetadata(metadata).build());
      extractorOutput.endTracks();
      extractorOutput.seekMap(new SeekMap.Unseekable(/* durationUs= */ C.TIME_UNSET));
    }
  }

  private void maybeSkipRemainingMetaAtomHeaderBytes(ExtractorInput input) throws IOException {
    scratch.reset(8);
    input.peekFully(scratch.getData(), 0, 8);
    AtomParsers.maybeSkipRemainingMetaAtomHeaderBytes(scratch);
    input.skipFully(scratch.getPosition());
    input.resetPeekPosition();
  }

  /** Processes an atom whose payload does not need to be parsed. */
  private void processUnparsedAtom(long atomStartPosition) {
    if (atomType == Atom.TYPE_mpvd) {
      // The input is an HEIC motion photo following the Google Photos Motion Photo File Format
      // V1.1.
      motionPhotoMetadata =
          new MotionPhotoMetadata(
              /* photoStartPosition= */ 0,
              /* photoSize= */ atomStartPosition,
              /* photoPresentationTimestampUs= */ C.TIME_UNSET,
              /* videoStartPosition= */ atomStartPosition + atomHeaderBytesRead,
              /* videoSize= */ atomSize - atomHeaderBytesRead);
    }
  }

  /**
   * For each sample of each track, calculates accumulated size of all samples which need to be read
   * before this sample can be used.
   */
  private static long[][] calculateAccumulatedSampleSizes(Mp4Track[] tracks) {
    long[][] accumulatedSampleSizes = new long[tracks.length][];
    int[] nextSampleIndex = new int[tracks.length];
    long[] nextSampleTimesUs = new long[tracks.length];
    boolean[] tracksFinished = new boolean[tracks.length];
    for (int i = 0; i < tracks.length; i++) {
      accumulatedSampleSizes[i] = new long[tracks[i].sampleTable.sampleCount];
      nextSampleTimesUs[i] = tracks[i].sampleTable.timestampsUs[0];
    }
    long accumulatedSampleSize = 0;
    int finishedTracks = 0;
    while (finishedTracks < tracks.length) {
      long minTimeUs = Long.MAX_VALUE;
      int minTimeTrackIndex = -1;
      for (int i = 0; i < tracks.length; i++) {
        if (!tracksFinished[i] && nextSampleTimesUs[i] <= minTimeUs) {
          minTimeTrackIndex = i;
          minTimeUs = nextSampleTimesUs[i];
        }
      }
      int trackSampleIndex = nextSampleIndex[minTimeTrackIndex];
      accumulatedSampleSizes[minTimeTrackIndex][trackSampleIndex] = accumulatedSampleSize;
      accumulatedSampleSize += tracks[minTimeTrackIndex].sampleTable.sizes[trackSampleIndex];
      nextSampleIndex[minTimeTrackIndex] = ++trackSampleIndex;
      if (trackSampleIndex < accumulatedSampleSizes[minTimeTrackIndex].length) {
        nextSampleTimesUs[minTimeTrackIndex] =
            tracks[minTimeTrackIndex].sampleTable.timestampsUs[trackSampleIndex];
      } else {
        tracksFinished[minTimeTrackIndex] = true;
        finishedTracks++;
      }
    }
    return accumulatedSampleSizes;
  }

  /**
   * Adjusts a seek point offset to take into account the track with the given {@code sampleTable},
   * for a given {@code seekTimeUs}.
   *
   * @param sampleTable The sample table to use.
   * @param seekTimeUs The seek time in microseconds.
   * @param offset The current offset.
   * @return The adjusted offset.
   */
  private static long maybeAdjustSeekOffset(
      TrackSampleTable sampleTable, long seekTimeUs, long offset) {
    int sampleIndex = getSynchronizationSampleIndex(sampleTable, seekTimeUs);
    if (sampleIndex == C.INDEX_UNSET) {
      return offset;
    }
    long sampleOffset = sampleTable.offsets[sampleIndex];
    return min(sampleOffset, offset);
  }

  /**
   * Returns the index of the synchronization sample before or at {@code timeUs}, or the index of
   * the first synchronization sample if located after {@code timeUs}, or {@link C#INDEX_UNSET} if
   * there are no synchronization samples in the table.
   *
   * @param sampleTable The sample table in which to locate a synchronization sample.
   * @param timeUs A time in microseconds.
   * @return The index of the synchronization sample before or at {@code timeUs}, or the index of
   *     the first synchronization sample if located after {@code timeUs}, or {@link C#INDEX_UNSET}
   *     if there are no synchronization samples in the table.
   */
  private static int getSynchronizationSampleIndex(TrackSampleTable sampleTable, long timeUs) {
    int sampleIndex = sampleTable.getIndexOfEarlierOrEqualSynchronizationSample(timeUs);
    if (sampleIndex == C.INDEX_UNSET) {
      // Handle the case where the requested time is before the first synchronization sample.
      sampleIndex = sampleTable.getIndexOfLaterOrEqualSynchronizationSample(timeUs);
    }
    return sampleIndex;
  }

  /**
   * Process an ftyp atom to determine the corresponding {@link FileType}.
   *
   * @param atomData The ftyp atom data.
   * @return The {@link FileType}.
   */
  private static @FileType int processFtypAtom(ParsableByteArray atomData) {
    atomData.setPosition(Atom.HEADER_SIZE);
    int majorBrand = atomData.readInt();
    @FileType int fileType = brandToFileType(majorBrand);
    if (fileType != FILE_TYPE_MP4) {
      return fileType;
    }
    atomData.skipBytes(4); // minor_version
    while (atomData.bytesLeft() > 0) {
      fileType = brandToFileType(atomData.readInt());
      if (fileType != FILE_TYPE_MP4) {
        return fileType;
      }
    }
    return FILE_TYPE_MP4;
  }

  private static @FileType int brandToFileType(int brand) {
    switch (brand) {
      case BRAND_QUICKTIME:
        return FILE_TYPE_QUICKTIME;
      case BRAND_HEIC:
        return FILE_TYPE_HEIC;
      default:
        return FILE_TYPE_MP4;
    }
  }

  /** Returns whether the extractor should decode a leaf atom with type {@code atom}. */
  private static boolean shouldParseLeafAtom(int atom) {
    return atom == Atom.TYPE_mdhd
        || atom == Atom.TYPE_mvhd
        || atom == Atom.TYPE_hdlr
        || atom == Atom.TYPE_stsd
        || atom == Atom.TYPE_stts
        || atom == Atom.TYPE_stss
        || atom == Atom.TYPE_ctts
        || atom == Atom.TYPE_elst
        || atom == Atom.TYPE_stsc
        || atom == Atom.TYPE_stsz
        || atom == Atom.TYPE_stz2
        || atom == Atom.TYPE_stco
        || atom == Atom.TYPE_co64
        || atom == Atom.TYPE_tkhd
        || atom == Atom.TYPE_ftyp
        || atom == Atom.TYPE_udta
        || atom == Atom.TYPE_keys
        || atom == Atom.TYPE_ilst;
  }

  /** Returns whether the extractor should decode a container atom with type {@code atom}. */
  private static boolean shouldParseContainerAtom(int atom) {
    return atom == Atom.TYPE_moov
        || atom == Atom.TYPE_trak
        || atom == Atom.TYPE_mdia
        || atom == Atom.TYPE_minf
        || atom == Atom.TYPE_stbl
        || atom == Atom.TYPE_edts
        || atom == Atom.TYPE_meta;
  }

  private static final class Mp4Track {

    public final Track track;
    public final TrackSampleTable sampleTable;
    public final TrackOutput trackOutput;
    @Nullable public final TrueHdSampleRechunker trueHdSampleRechunker;

    public int sampleIndex;

    public Mp4Track(Track track, TrackSampleTable sampleTable, TrackOutput trackOutput) {
      this.track = track;
      this.sampleTable = sampleTable;
      this.trackOutput = trackOutput;
      trueHdSampleRechunker =
          MimeTypes.AUDIO_TRUEHD.equals(track.format.sampleMimeType)
              ? new TrueHdSampleRechunker()
              : null;
    }
  }
}
