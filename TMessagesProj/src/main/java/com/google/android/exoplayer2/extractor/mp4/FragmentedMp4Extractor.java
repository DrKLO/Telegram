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
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Util.castNonNull;
import static com.google.android.exoplayer2.util.Util.nullSafeArrayCopy;
import static java.lang.Math.max;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.util.Pair;
import android.util.SparseArray;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.audio.Ac4Util;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;
import com.google.android.exoplayer2.extractor.CeaUtil;
import com.google.android.exoplayer2.extractor.ChunkIndex;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.GaplessInfoHolder;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.mp4.Atom.ContainerAtom;
import com.google.android.exoplayer2.extractor.mp4.Atom.LeafAtom;
import com.google.android.exoplayer2.metadata.emsg.EventMessage;
import com.google.android.exoplayer2.metadata.emsg.EventMessageEncoder;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Extracts data from the FMP4 container format. */
@SuppressWarnings("ConstantField")
public class FragmentedMp4Extractor implements Extractor {

  /** Factory for {@link FragmentedMp4Extractor} instances. */
  public static final ExtractorsFactory FACTORY =
      () -> new Extractor[] {new FragmentedMp4Extractor()};

  /**
   * Flags controlling the behavior of the extractor. Possible flag values are {@link
   * #FLAG_WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME}, {@link #FLAG_WORKAROUND_IGNORE_TFDT_BOX},
   * {@link #FLAG_ENABLE_EMSG_TRACK} and {@link #FLAG_WORKAROUND_IGNORE_EDIT_LISTS}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef(
      flag = true,
      value = {
        FLAG_WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME,
        FLAG_WORKAROUND_IGNORE_TFDT_BOX,
        FLAG_ENABLE_EMSG_TRACK,
        FLAG_WORKAROUND_IGNORE_EDIT_LISTS
      })
  public @interface Flags {}
  /**
   * Flag to work around an issue in some video streams where every frame is marked as a sync frame.
   * The workaround overrides the sync frame flags in the stream, forcing them to false except for
   * the first sample in each segment.
   *
   * <p>This flag does nothing if the stream is not a video stream.
   */
  public static final int FLAG_WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME = 1;
  /** Flag to ignore any tfdt boxes in the stream. */
  public static final int FLAG_WORKAROUND_IGNORE_TFDT_BOX = 1 << 1; // 2
  /**
   * Flag to indicate that the extractor should output an event message metadata track. Any event
   * messages in the stream will be delivered as samples to this track.
   */
  public static final int FLAG_ENABLE_EMSG_TRACK = 1 << 2; // 4

  /** Flag to ignore any edit lists in the stream. */
  public static final int FLAG_WORKAROUND_IGNORE_EDIT_LISTS = 1 << 4; // 16

  private static final String TAG = "FragmentedMp4Extractor";

  @SuppressWarnings("ConstantCaseForConstants")
  private static final int SAMPLE_GROUP_TYPE_seig = 0x73656967;

  private static final byte[] PIFF_SAMPLE_ENCRYPTION_BOX_EXTENDED_TYPE =
      new byte[] {-94, 57, 79, 82, 90, -101, 79, 20, -94, 68, 108, 66, 124, 100, -115, -12};

  // Extra tracks constants.
  private static final Format EMSG_FORMAT =
      new Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_EMSG).build();
  private static final int EXTRA_TRACKS_BASE_ID = 100;

  // Parser states.
  private static final int STATE_READING_ATOM_HEADER = 0;
  private static final int STATE_READING_ATOM_PAYLOAD = 1;
  private static final int STATE_READING_ENCRYPTION_DATA = 2;
  private static final int STATE_READING_SAMPLE_START = 3;
  private static final int STATE_READING_SAMPLE_CONTINUE = 4;

  // Workarounds.
  private final @Flags int flags;
  @Nullable private final Track sideloadedTrack;

  // Sideloaded data.
  private final List<Format> closedCaptionFormats;

  // Track-linked data bundle, accessible as a whole through trackID.
  private final SparseArray<TrackBundle> trackBundles;

  // Temporary arrays.
  private final ParsableByteArray nalStartCode;
  private final ParsableByteArray nalPrefix;
  private final ParsableByteArray nalBuffer;
  private final byte[] scratchBytes;
  private final ParsableByteArray scratch;

  // Adjusts sample timestamps.
  @Nullable private final TimestampAdjuster timestampAdjuster;

  private final EventMessageEncoder eventMessageEncoder;

  // Parser state.
  private final ParsableByteArray atomHeader;
  private final ArrayDeque<ContainerAtom> containerAtoms;
  private final ArrayDeque<MetadataSampleInfo> pendingMetadataSampleInfos;
  @Nullable private final TrackOutput additionalEmsgTrackOutput;

  private int parserState;
  private int atomType;
  private long atomSize;
  private int atomHeaderBytesRead;
  @Nullable private ParsableByteArray atomData;
  private long endOfMdatPosition;
  private int pendingMetadataSampleBytes;
  private long pendingSeekTimeUs;

  private long durationUs;
  private long segmentIndexEarliestPresentationTimeUs;
  @Nullable private TrackBundle currentTrackBundle;
  private int sampleSize;
  private int sampleBytesWritten;
  private int sampleCurrentNalBytesRemaining;
  private boolean processSeiNalUnitPayload;

  // Outputs.
  private ExtractorOutput extractorOutput;
  private TrackOutput[] emsgTrackOutputs;
  private TrackOutput[] ceaTrackOutputs;

  // Whether extractorOutput.seekMap has been called.
  private boolean haveOutputSeekMap;

  public FragmentedMp4Extractor() {
    this(0);
  }

  /**
   * @param flags Flags that control the extractor's behavior.
   */
  public FragmentedMp4Extractor(@Flags int flags) {
    this(flags, /* timestampAdjuster= */ null);
  }

  /**
   * @param flags Flags that control the extractor's behavior.
   * @param timestampAdjuster Adjusts sample timestamps. May be null if no adjustment is needed.
   */
  public FragmentedMp4Extractor(@Flags int flags, @Nullable TimestampAdjuster timestampAdjuster) {
    this(flags, timestampAdjuster, /* sideloadedTrack= */ null, Collections.emptyList());
  }

  /**
   * @param flags Flags that control the extractor's behavior.
   * @param timestampAdjuster Adjusts sample timestamps. May be null if no adjustment is needed.
   * @param sideloadedTrack Sideloaded track information, in the case that the extractor will not
   *     receive a moov box in the input data. Null if a moov box is expected.
   */
  public FragmentedMp4Extractor(
      @Flags int flags,
      @Nullable TimestampAdjuster timestampAdjuster,
      @Nullable Track sideloadedTrack) {
    this(flags, timestampAdjuster, sideloadedTrack, Collections.emptyList());
  }

  /**
   * @param flags Flags that control the extractor's behavior.
   * @param timestampAdjuster Adjusts sample timestamps. May be null if no adjustment is needed.
   * @param sideloadedTrack Sideloaded track information, in the case that the extractor will not
   *     receive a moov box in the input data. Null if a moov box is expected.
   * @param closedCaptionFormats For tracks that contain SEI messages, the formats of the closed
   *     caption channels to expose.
   */
  public FragmentedMp4Extractor(
      @Flags int flags,
      @Nullable TimestampAdjuster timestampAdjuster,
      @Nullable Track sideloadedTrack,
      List<Format> closedCaptionFormats) {
    this(
        flags,
        timestampAdjuster,
        sideloadedTrack,
        closedCaptionFormats,
        /* additionalEmsgTrackOutput= */ null);
  }

  /**
   * @param flags Flags that control the extractor's behavior.
   * @param timestampAdjuster Adjusts sample timestamps. May be null if no adjustment is needed.
   * @param sideloadedTrack Sideloaded track information, in the case that the extractor will not
   *     receive a moov box in the input data. Null if a moov box is expected.
   * @param closedCaptionFormats For tracks that contain SEI messages, the formats of the closed
   *     caption channels to expose.
   * @param additionalEmsgTrackOutput An extra track output that will receive all emsg messages
   *     targeting the player, even if {@link #FLAG_ENABLE_EMSG_TRACK} is not set. Null if special
   *     handling of emsg messages for players is not required.
   */
  public FragmentedMp4Extractor(
      @Flags int flags,
      @Nullable TimestampAdjuster timestampAdjuster,
      @Nullable Track sideloadedTrack,
      List<Format> closedCaptionFormats,
      @Nullable TrackOutput additionalEmsgTrackOutput) {
    this.flags = flags;
    this.timestampAdjuster = timestampAdjuster;
    this.sideloadedTrack = sideloadedTrack;
    this.closedCaptionFormats = Collections.unmodifiableList(closedCaptionFormats);
    this.additionalEmsgTrackOutput = additionalEmsgTrackOutput;
    eventMessageEncoder = new EventMessageEncoder();
    atomHeader = new ParsableByteArray(Atom.LONG_HEADER_SIZE);
    nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);
    nalPrefix = new ParsableByteArray(5);
    nalBuffer = new ParsableByteArray();
    scratchBytes = new byte[16];
    scratch = new ParsableByteArray(scratchBytes);
    containerAtoms = new ArrayDeque<>();
    pendingMetadataSampleInfos = new ArrayDeque<>();
    trackBundles = new SparseArray<>();
    durationUs = C.TIME_UNSET;
    pendingSeekTimeUs = C.TIME_UNSET;
    segmentIndexEarliestPresentationTimeUs = C.TIME_UNSET;
    extractorOutput = ExtractorOutput.PLACEHOLDER;
    emsgTrackOutputs = new TrackOutput[0];
    ceaTrackOutputs = new TrackOutput[0];
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    return Sniffer.sniffFragmented(input);
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
    enterReadingAtomHeaderState();
    initExtraTracks();
    if (sideloadedTrack != null) {
      TrackBundle bundle =
          new TrackBundle(
              output.track(0, sideloadedTrack.type),
              new TrackSampleTable(
                  sideloadedTrack,
                  /* offsets= */ new long[0],
                  /* sizes= */ new int[0],
                  /* maximumSize= */ 0,
                  /* timestampsUs= */ new long[0],
                  /* flags= */ new int[0],
                  /* durationUs= */ 0),
              new DefaultSampleValues(
                  /* sampleDescriptionIndex= */ 0,
                  /* duration= */ 0,
                  /* size= */ 0,
                  /* flags= */ 0));
      trackBundles.put(0, bundle);
      extractorOutput.endTracks();
    }
  }

  @Override
  public void seek(long position, long timeUs) {
    int trackCount = trackBundles.size();
    for (int i = 0; i < trackCount; i++) {
      trackBundles.valueAt(i).resetFragmentInfo();
    }
    pendingMetadataSampleInfos.clear();
    pendingMetadataSampleBytes = 0;
    pendingSeekTimeUs = timeUs;
    containerAtoms.clear();
    enterReadingAtomHeaderState();
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
            return Extractor.RESULT_END_OF_INPUT;
          }
          break;
        case STATE_READING_ATOM_PAYLOAD:
          readAtomPayload(input);
          break;
        case STATE_READING_ENCRYPTION_DATA:
          readEncryptionData(input);
          break;
        default:
          if (readSample(input)) {
            return RESULT_CONTINUE;
          }
      }
    }
  }

  private void enterReadingAtomHeaderState() {
    parserState = STATE_READING_ATOM_HEADER;
    atomHeaderBytesRead = 0;
  }

  private boolean readAtomHeader(ExtractorInput input) throws IOException {
    if (atomHeaderBytesRead == 0) {
      // Read the standard length atom header.
      if (!input.readFully(atomHeader.getData(), 0, Atom.HEADER_SIZE, true)) {
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
      if (endPosition == C.LENGTH_UNSET && !containerAtoms.isEmpty()) {
        endPosition = containerAtoms.peek().endPosition;
      }
      if (endPosition != C.LENGTH_UNSET) {
        atomSize = endPosition - input.getPosition() + atomHeaderBytesRead;
      }
    }

    if (atomSize < atomHeaderBytesRead) {
      throw ParserException.createForUnsupportedContainerFeature(
          "Atom size less than header length (unsupported).");
    }

    long atomPosition = input.getPosition() - atomHeaderBytesRead;
    if (atomType == Atom.TYPE_moof || atomType == Atom.TYPE_mdat) {
      if (!haveOutputSeekMap) {
        // This must be the first moof or mdat in the stream.
        extractorOutput.seekMap(new SeekMap.Unseekable(durationUs, atomPosition));
        haveOutputSeekMap = true;
      }
    }

    if (atomType == Atom.TYPE_moof) {
      // The data positions may be updated when parsing the tfhd/trun.
      int trackCount = trackBundles.size();
      for (int i = 0; i < trackCount; i++) {
        TrackFragment fragment = trackBundles.valueAt(i).fragment;
        fragment.atomPosition = atomPosition;
        fragment.auxiliaryDataPosition = atomPosition;
        fragment.dataPosition = atomPosition;
      }
    }

    if (atomType == Atom.TYPE_mdat) {
      currentTrackBundle = null;
      endOfMdatPosition = atomPosition + atomSize;
      parserState = STATE_READING_ENCRYPTION_DATA;
      return true;
    }

    if (shouldParseContainerAtom(atomType)) {
      long endPosition = input.getPosition() + atomSize - Atom.HEADER_SIZE;
      containerAtoms.push(new ContainerAtom(atomType, endPosition));
      if (atomSize == atomHeaderBytesRead) {
        processAtomEnded(endPosition);
      } else {
        // Start reading the first child atom.
        enterReadingAtomHeaderState();
      }
    } else if (shouldParseLeafAtom(atomType)) {
      if (atomHeaderBytesRead != Atom.HEADER_SIZE) {
        throw ParserException.createForUnsupportedContainerFeature(
            "Leaf atom defines extended atom size (unsupported).");
      }
      if (atomSize > Integer.MAX_VALUE) {
        throw ParserException.createForUnsupportedContainerFeature(
            "Leaf atom with length > 2147483647 (unsupported).");
      }
      ParsableByteArray atomData = new ParsableByteArray((int) atomSize);
      System.arraycopy(atomHeader.getData(), 0, atomData.getData(), 0, Atom.HEADER_SIZE);
      this.atomData = atomData;
      parserState = STATE_READING_ATOM_PAYLOAD;
    } else {
      if (atomSize > Integer.MAX_VALUE) {
        throw ParserException.createForUnsupportedContainerFeature(
            "Skipping atom with length > 2147483647 (unsupported).");
      }
      atomData = null;
      parserState = STATE_READING_ATOM_PAYLOAD;
    }

    return true;
  }

  private void readAtomPayload(ExtractorInput input) throws IOException {
    int atomPayloadSize = (int) atomSize - atomHeaderBytesRead;
    @Nullable ParsableByteArray atomData = this.atomData;
    if (atomData != null) {
      input.readFully(atomData.getData(), Atom.HEADER_SIZE, atomPayloadSize);
      onLeafAtomRead(new LeafAtom(atomType, atomData), input.getPosition());
    } else {
      input.skipFully(atomPayloadSize);
    }
    processAtomEnded(input.getPosition());
  }

  private void processAtomEnded(long atomEndPosition) throws ParserException {
    while (!containerAtoms.isEmpty() && containerAtoms.peek().endPosition == atomEndPosition) {
      onContainerAtomRead(containerAtoms.pop());
    }
    enterReadingAtomHeaderState();
  }

  private void onLeafAtomRead(LeafAtom leaf, long inputPosition) throws ParserException {
    if (!containerAtoms.isEmpty()) {
      containerAtoms.peek().add(leaf);
    } else if (leaf.type == Atom.TYPE_sidx) {
      Pair<Long, ChunkIndex> result = parseSidx(leaf.data, inputPosition);
      segmentIndexEarliestPresentationTimeUs = result.first;
      extractorOutput.seekMap(result.second);
      haveOutputSeekMap = true;
    } else if (leaf.type == Atom.TYPE_emsg) {
      onEmsgLeafAtomRead(leaf.data);
    }
  }

  private void onContainerAtomRead(ContainerAtom container) throws ParserException {
    if (container.type == Atom.TYPE_moov) {
      onMoovContainerAtomRead(container);
    } else if (container.type == Atom.TYPE_moof) {
      onMoofContainerAtomRead(container);
    } else if (!containerAtoms.isEmpty()) {
      containerAtoms.peek().add(container);
    }
  }

  private void onMoovContainerAtomRead(ContainerAtom moov) throws ParserException {
    checkState(sideloadedTrack == null, "Unexpected moov box.");

    @Nullable DrmInitData drmInitData = getDrmInitDataFromAtoms(moov.leafChildren);

    // Read declaration of track fragments in the moov box.
    ContainerAtom mvex = checkNotNull(moov.getContainerAtomOfType(Atom.TYPE_mvex));
    SparseArray<DefaultSampleValues> defaultSampleValuesArray = new SparseArray<>();
    long duration = C.TIME_UNSET;
    int mvexChildrenSize = mvex.leafChildren.size();
    for (int i = 0; i < mvexChildrenSize; i++) {
      Atom.LeafAtom atom = mvex.leafChildren.get(i);
      if (atom.type == Atom.TYPE_trex) {
        Pair<Integer, DefaultSampleValues> trexData = parseTrex(atom.data);
        defaultSampleValuesArray.put(trexData.first, trexData.second);
      } else if (atom.type == Atom.TYPE_mehd) {
        duration = parseMehd(atom.data);
      }
    }

    // Construction of tracks and sample tables.
    List<TrackSampleTable> sampleTables =
        parseTraks(
            moov,
            new GaplessInfoHolder(),
            duration,
            drmInitData,
            /* ignoreEditLists= */ (flags & FLAG_WORKAROUND_IGNORE_EDIT_LISTS) != 0,
            /* isQuickTime= */ false,
            this::modifyTrack);

    int trackCount = sampleTables.size();
    if (trackBundles.size() == 0) {
      // We need to create the track bundles.
      for (int i = 0; i < trackCount; i++) {
        TrackSampleTable sampleTable = sampleTables.get(i);
        Track track = sampleTable.track;
        TrackBundle trackBundle =
            new TrackBundle(
                extractorOutput.track(i, track.type),
                sampleTable,
                getDefaultSampleValues(defaultSampleValuesArray, track.id));
        trackBundles.put(track.id, trackBundle);
        durationUs = max(durationUs, track.durationUs);
      }
      extractorOutput.endTracks();
    } else {
      checkState(trackBundles.size() == trackCount);
      for (int i = 0; i < trackCount; i++) {
        TrackSampleTable sampleTable = sampleTables.get(i);
        Track track = sampleTable.track;
        trackBundles
            .get(track.id)
            .reset(sampleTable, getDefaultSampleValues(defaultSampleValuesArray, track.id));
      }
    }
  }

  @Nullable
  protected Track modifyTrack(@Nullable Track track) {
    return track;
  }

  private DefaultSampleValues getDefaultSampleValues(
      SparseArray<DefaultSampleValues> defaultSampleValuesArray, int trackId) {
    if (defaultSampleValuesArray.size() == 1) {
      // Ignore track id if there is only one track to cope with non-matching track indices.
      // See https://github.com/google/ExoPlayer/issues/4477.
      return defaultSampleValuesArray.valueAt(/* index= */ 0);
    }
    return checkNotNull(defaultSampleValuesArray.get(trackId));
  }

  private void onMoofContainerAtomRead(ContainerAtom moof) throws ParserException {
    parseMoof(moof, trackBundles, sideloadedTrack != null, flags, scratchBytes);

    @Nullable DrmInitData drmInitData = getDrmInitDataFromAtoms(moof.leafChildren);
    if (drmInitData != null) {
      int trackCount = trackBundles.size();
      for (int i = 0; i < trackCount; i++) {
        trackBundles.valueAt(i).updateDrmInitData(drmInitData);
      }
    }
    // If we have a pending seek, advance tracks to their preceding sync frames.
    if (pendingSeekTimeUs != C.TIME_UNSET) {
      int trackCount = trackBundles.size();
      for (int i = 0; i < trackCount; i++) {
        trackBundles.valueAt(i).seek(pendingSeekTimeUs);
      }
      pendingSeekTimeUs = C.TIME_UNSET;
    }
  }

  private void initExtraTracks() {
    int nextExtraTrackId = EXTRA_TRACKS_BASE_ID;

    emsgTrackOutputs = new TrackOutput[2];
    int emsgTrackOutputCount = 0;
    if (additionalEmsgTrackOutput != null) {
      emsgTrackOutputs[emsgTrackOutputCount++] = additionalEmsgTrackOutput;
    }
    if ((flags & FLAG_ENABLE_EMSG_TRACK) != 0) {
      emsgTrackOutputs[emsgTrackOutputCount++] =
          extractorOutput.track(nextExtraTrackId++, C.TRACK_TYPE_METADATA);
    }
    emsgTrackOutputs = nullSafeArrayCopy(emsgTrackOutputs, emsgTrackOutputCount);
    for (TrackOutput eventMessageTrackOutput : emsgTrackOutputs) {
      eventMessageTrackOutput.format(EMSG_FORMAT);
    }

    ceaTrackOutputs = new TrackOutput[closedCaptionFormats.size()];
    for (int i = 0; i < ceaTrackOutputs.length; i++) {
      TrackOutput output = extractorOutput.track(nextExtraTrackId++, C.TRACK_TYPE_TEXT);
      output.format(closedCaptionFormats.get(i));
      ceaTrackOutputs[i] = output;
    }
  }

  /** Handles an emsg atom (defined in 23009-1). */
  private void onEmsgLeafAtomRead(ParsableByteArray atom) {
    if (emsgTrackOutputs.length == 0) {
      return;
    }
    atom.setPosition(Atom.HEADER_SIZE);
    int fullAtom = atom.readInt();
    int version = Atom.parseFullAtomVersion(fullAtom);
    String schemeIdUri;
    String value;
    long timescale;
    long presentationTimeDeltaUs = C.TIME_UNSET; // Only set if version == 0
    long sampleTimeUs = C.TIME_UNSET;
    long durationMs;
    long id;
    switch (version) {
      case 0:
        schemeIdUri = checkNotNull(atom.readNullTerminatedString());
        value = checkNotNull(atom.readNullTerminatedString());
        timescale = atom.readUnsignedInt();
        presentationTimeDeltaUs =
            Util.scaleLargeTimestamp(atom.readUnsignedInt(), C.MICROS_PER_SECOND, timescale);
        if (segmentIndexEarliestPresentationTimeUs != C.TIME_UNSET) {
          sampleTimeUs = segmentIndexEarliestPresentationTimeUs + presentationTimeDeltaUs;
        }
        durationMs =
            Util.scaleLargeTimestamp(atom.readUnsignedInt(), C.MILLIS_PER_SECOND, timescale);
        id = atom.readUnsignedInt();
        break;
      case 1:
        timescale = atom.readUnsignedInt();
        sampleTimeUs =
            Util.scaleLargeTimestamp(atom.readUnsignedLongToLong(), C.MICROS_PER_SECOND, timescale);
        durationMs =
            Util.scaleLargeTimestamp(atom.readUnsignedInt(), C.MILLIS_PER_SECOND, timescale);
        id = atom.readUnsignedInt();
        schemeIdUri = checkNotNull(atom.readNullTerminatedString());
        value = checkNotNull(atom.readNullTerminatedString());
        break;
      default:
        Log.w(TAG, "Skipping unsupported emsg version: " + version);
        return;
    }

    byte[] messageData = new byte[atom.bytesLeft()];
    atom.readBytes(messageData, /*offset=*/ 0, atom.bytesLeft());
    EventMessage eventMessage = new EventMessage(schemeIdUri, value, durationMs, id, messageData);
    ParsableByteArray encodedEventMessage =
        new ParsableByteArray(eventMessageEncoder.encode(eventMessage));
    int sampleSize = encodedEventMessage.bytesLeft();

    // Output the sample data.
    for (TrackOutput emsgTrackOutput : emsgTrackOutputs) {
      encodedEventMessage.setPosition(0);
      emsgTrackOutput.sampleData(encodedEventMessage, sampleSize);
    }

    // Output the sample metadata.
    if (sampleTimeUs == C.TIME_UNSET) {
      // We're processing a v0 emsg atom, which contains a presentation time delta, and cannot yet
      // calculate its absolute sample timestamp. Defer outputting the metadata until we can.
      pendingMetadataSampleInfos.addLast(
          new MetadataSampleInfo(
              presentationTimeDeltaUs, /* sampleTimeIsRelative= */ true, sampleSize));
      pendingMetadataSampleBytes += sampleSize;
    } else if (!pendingMetadataSampleInfos.isEmpty()) {
      // We also need to defer outputting metadata if pendingMetadataSampleInfos is non-empty, else
      // we will output metadata for samples in the wrong order. See:
      // https://github.com/google/ExoPlayer/issues/9996.
      pendingMetadataSampleInfos.addLast(
          new MetadataSampleInfo(sampleTimeUs, /* sampleTimeIsRelative= */ false, sampleSize));
      pendingMetadataSampleBytes += sampleSize;
    } else {
      // We can output the sample metadata immediately.
      if (timestampAdjuster != null) {
        sampleTimeUs = timestampAdjuster.adjustSampleTimestamp(sampleTimeUs);
      }
      for (TrackOutput emsgTrackOutput : emsgTrackOutputs) {
        emsgTrackOutput.sampleMetadata(
            sampleTimeUs, C.BUFFER_FLAG_KEY_FRAME, sampleSize, /* offset= */ 0, null);
      }
    }
  }

  /** Parses a trex atom (defined in 14496-12). */
  private static Pair<Integer, DefaultSampleValues> parseTrex(ParsableByteArray trex) {
    trex.setPosition(Atom.FULL_HEADER_SIZE);
    int trackId = trex.readInt();
    int defaultSampleDescriptionIndex = trex.readInt() - 1;
    int defaultSampleDuration = trex.readInt();
    int defaultSampleSize = trex.readInt();
    int defaultSampleFlags = trex.readInt();

    return Pair.create(
        trackId,
        new DefaultSampleValues(
            defaultSampleDescriptionIndex,
            defaultSampleDuration,
            defaultSampleSize,
            defaultSampleFlags));
  }

  /** Parses an mehd atom (defined in 14496-12). */
  private static long parseMehd(ParsableByteArray mehd) {
    mehd.setPosition(Atom.HEADER_SIZE);
    int fullAtom = mehd.readInt();
    int version = Atom.parseFullAtomVersion(fullAtom);
    return version == 0 ? mehd.readUnsignedInt() : mehd.readUnsignedLongToLong();
  }

  private static void parseMoof(
      ContainerAtom moof,
      SparseArray<TrackBundle> trackBundles,
      boolean haveSideloadedTrack,
      @Flags int flags,
      byte[] extendedTypeScratch)
      throws ParserException {
    int moofContainerChildrenSize = moof.containerChildren.size();
    for (int i = 0; i < moofContainerChildrenSize; i++) {
      Atom.ContainerAtom child = moof.containerChildren.get(i);
      // TODO: Support multiple traf boxes per track in a single moof.
      if (child.type == Atom.TYPE_traf) {
        parseTraf(child, trackBundles, haveSideloadedTrack, flags, extendedTypeScratch);
      }
    }
  }

  /** Parses a traf atom (defined in 14496-12). */
  private static void parseTraf(
      ContainerAtom traf,
      SparseArray<TrackBundle> trackBundles,
      boolean haveSideloadedTrack,
      @Flags int flags,
      byte[] extendedTypeScratch)
      throws ParserException {
    LeafAtom tfhd = checkNotNull(traf.getLeafAtomOfType(Atom.TYPE_tfhd));
    @Nullable TrackBundle trackBundle = parseTfhd(tfhd.data, trackBundles, haveSideloadedTrack);
    if (trackBundle == null) {
      return;
    }

    TrackFragment fragment = trackBundle.fragment;
    long fragmentDecodeTime = fragment.nextFragmentDecodeTime;
    boolean fragmentDecodeTimeIncludesMoov = fragment.nextFragmentDecodeTimeIncludesMoov;
    trackBundle.resetFragmentInfo();
    trackBundle.currentlyInFragment = true;
    @Nullable LeafAtom tfdtAtom = traf.getLeafAtomOfType(Atom.TYPE_tfdt);
    if (tfdtAtom != null && (flags & FLAG_WORKAROUND_IGNORE_TFDT_BOX) == 0) {
      fragment.nextFragmentDecodeTime = parseTfdt(tfdtAtom.data);
      fragment.nextFragmentDecodeTimeIncludesMoov = true;
    } else {
      fragment.nextFragmentDecodeTime = fragmentDecodeTime;
      fragment.nextFragmentDecodeTimeIncludesMoov = fragmentDecodeTimeIncludesMoov;
    }

    parseTruns(traf, trackBundle, flags);

    @Nullable
    TrackEncryptionBox encryptionBox =
        trackBundle.moovSampleTable.track.getSampleDescriptionEncryptionBox(
            checkNotNull(fragment.header).sampleDescriptionIndex);

    @Nullable LeafAtom saiz = traf.getLeafAtomOfType(Atom.TYPE_saiz);
    if (saiz != null) {
      parseSaiz(checkNotNull(encryptionBox), saiz.data, fragment);
    }

    @Nullable LeafAtom saio = traf.getLeafAtomOfType(Atom.TYPE_saio);
    if (saio != null) {
      parseSaio(saio.data, fragment);
    }

    @Nullable LeafAtom senc = traf.getLeafAtomOfType(Atom.TYPE_senc);
    if (senc != null) {
      parseSenc(senc.data, fragment);
    }

    parseSampleGroups(traf, encryptionBox != null ? encryptionBox.schemeType : null, fragment);

    int leafChildrenSize = traf.leafChildren.size();
    for (int i = 0; i < leafChildrenSize; i++) {
      LeafAtom atom = traf.leafChildren.get(i);
      if (atom.type == Atom.TYPE_uuid) {
        parseUuid(atom.data, fragment, extendedTypeScratch);
      }
    }
  }

  private static void parseTruns(ContainerAtom traf, TrackBundle trackBundle, @Flags int flags)
      throws ParserException {
    int trunCount = 0;
    int totalSampleCount = 0;
    List<LeafAtom> leafChildren = traf.leafChildren;
    int leafChildrenSize = leafChildren.size();
    for (int i = 0; i < leafChildrenSize; i++) {
      LeafAtom atom = leafChildren.get(i);
      if (atom.type == Atom.TYPE_trun) {
        ParsableByteArray trunData = atom.data;
        trunData.setPosition(Atom.FULL_HEADER_SIZE);
        int trunSampleCount = trunData.readUnsignedIntToInt();
        if (trunSampleCount > 0) {
          totalSampleCount += trunSampleCount;
          trunCount++;
        }
      }
    }
    trackBundle.currentTrackRunIndex = 0;
    trackBundle.currentSampleInTrackRun = 0;
    trackBundle.currentSampleIndex = 0;
    trackBundle.fragment.initTables(trunCount, totalSampleCount);

    int trunIndex = 0;
    int trunStartPosition = 0;
    for (int i = 0; i < leafChildrenSize; i++) {
      LeafAtom trun = leafChildren.get(i);
      if (trun.type == Atom.TYPE_trun) {
        trunStartPosition =
            parseTrun(trackBundle, trunIndex++, flags, trun.data, trunStartPosition);
      }
    }
  }

  private static void parseSaiz(
      TrackEncryptionBox encryptionBox, ParsableByteArray saiz, TrackFragment out)
      throws ParserException {
    int vectorSize = encryptionBox.perSampleIvSize;
    saiz.setPosition(Atom.HEADER_SIZE);
    int fullAtom = saiz.readInt();
    int flags = Atom.parseFullAtomFlags(fullAtom);
    if ((flags & 0x01) == 1) {
      saiz.skipBytes(8);
    }
    int defaultSampleInfoSize = saiz.readUnsignedByte();

    int sampleCount = saiz.readUnsignedIntToInt();
    if (sampleCount > out.sampleCount) {
      throw ParserException.createForMalformedContainer(
          "Saiz sample count "
              + sampleCount
              + " is greater than fragment sample count"
              + out.sampleCount,
          /* cause= */ null);
    }

    int totalSize = 0;
    if (defaultSampleInfoSize == 0) {
      boolean[] sampleHasSubsampleEncryptionTable = out.sampleHasSubsampleEncryptionTable;
      for (int i = 0; i < sampleCount; i++) {
        int sampleInfoSize = saiz.readUnsignedByte();
        totalSize += sampleInfoSize;
        sampleHasSubsampleEncryptionTable[i] = sampleInfoSize > vectorSize;
      }
    } else {
      boolean subsampleEncryption = defaultSampleInfoSize > vectorSize;
      totalSize += defaultSampleInfoSize * sampleCount;
      Arrays.fill(out.sampleHasSubsampleEncryptionTable, 0, sampleCount, subsampleEncryption);
    }
    Arrays.fill(out.sampleHasSubsampleEncryptionTable, sampleCount, out.sampleCount, false);
    if (totalSize > 0) {
      out.initEncryptionData(totalSize);
    }
  }

  /**
   * Parses a saio atom (defined in 14496-12).
   *
   * @param saio The saio atom to decode.
   * @param out The {@link TrackFragment} to populate with data from the saio atom.
   */
  private static void parseSaio(ParsableByteArray saio, TrackFragment out) throws ParserException {
    saio.setPosition(Atom.HEADER_SIZE);
    int fullAtom = saio.readInt();
    int flags = Atom.parseFullAtomFlags(fullAtom);
    if ((flags & 0x01) == 1) {
      saio.skipBytes(8);
    }

    int entryCount = saio.readUnsignedIntToInt();
    if (entryCount != 1) {
      // We only support one trun element currently, so always expect one entry.
      throw ParserException.createForMalformedContainer(
          "Unexpected saio entry count: " + entryCount, /* cause= */ null);
    }

    int version = Atom.parseFullAtomVersion(fullAtom);
    out.auxiliaryDataPosition +=
        version == 0 ? saio.readUnsignedInt() : saio.readUnsignedLongToLong();
  }

  /**
   * Parses a tfhd atom (defined in 14496-12), updates the corresponding {@link TrackFragment} and
   * returns the {@link TrackBundle} of the corresponding {@link Track}. If the tfhd does not refer
   * to any {@link TrackBundle}, {@code null} is returned and no changes are made.
   *
   * @param tfhd The tfhd atom to decode.
   * @param trackBundles The track bundles, one of which corresponds to the tfhd atom being parsed.
   * @param haveSideloadedTrack Whether {@code trackBundles} contains a single bundle corresponding
   *     to a side-loaded track.
   * @return The {@link TrackBundle} to which the {@link TrackFragment} belongs, or null if the tfhd
   *     does not refer to any {@link TrackBundle}.
   */
  @Nullable
  private static TrackBundle parseTfhd(
      ParsableByteArray tfhd, SparseArray<TrackBundle> trackBundles, boolean haveSideloadedTrack) {
    tfhd.setPosition(Atom.HEADER_SIZE);
    int fullAtom = tfhd.readInt();
    int atomFlags = Atom.parseFullAtomFlags(fullAtom);
    int trackId = tfhd.readInt();
    @Nullable
    TrackBundle trackBundle =
        haveSideloadedTrack ? trackBundles.valueAt(0) : trackBundles.get(trackId);
    if (trackBundle == null) {
      return null;
    }
    if ((atomFlags & 0x01 /* base_data_offset_present */) != 0) {
      long baseDataPosition = tfhd.readUnsignedLongToLong();
      trackBundle.fragment.dataPosition = baseDataPosition;
      trackBundle.fragment.auxiliaryDataPosition = baseDataPosition;
    }

    DefaultSampleValues defaultSampleValues = trackBundle.defaultSampleValues;
    int defaultSampleDescriptionIndex =
        ((atomFlags & 0x02 /* default_sample_description_index_present */) != 0)
            ? tfhd.readInt() - 1
            : defaultSampleValues.sampleDescriptionIndex;
    int defaultSampleDuration =
        ((atomFlags & 0x08 /* default_sample_duration_present */) != 0)
            ? tfhd.readInt()
            : defaultSampleValues.duration;
    int defaultSampleSize =
        ((atomFlags & 0x10 /* default_sample_size_present */) != 0)
            ? tfhd.readInt()
            : defaultSampleValues.size;
    int defaultSampleFlags =
        ((atomFlags & 0x20 /* default_sample_flags_present */) != 0)
            ? tfhd.readInt()
            : defaultSampleValues.flags;
    trackBundle.fragment.header =
        new DefaultSampleValues(
            defaultSampleDescriptionIndex,
            defaultSampleDuration,
            defaultSampleSize,
            defaultSampleFlags);
    return trackBundle;
  }

  /**
   * Parses a tfdt atom (defined in 14496-12).
   *
   * @return baseMediaDecodeTime The sum of the decode durations of all earlier samples in the
   *     media, expressed in the media's timescale.
   */
  private static long parseTfdt(ParsableByteArray tfdt) {
    tfdt.setPosition(Atom.HEADER_SIZE);
    int fullAtom = tfdt.readInt();
    int version = Atom.parseFullAtomVersion(fullAtom);
    return version == 1 ? tfdt.readUnsignedLongToLong() : tfdt.readUnsignedInt();
  }

  /**
   * Parses a trun atom (defined in 14496-12).
   *
   * @param trackBundle The {@link TrackBundle} that contains the {@link TrackFragment} into which
   *     parsed data should be placed.
   * @param index Index of the track run in the fragment.
   * @param flags Flags to allow any required workaround to be executed.
   * @param trun The trun atom to decode.
   * @return The starting position of samples for the next run.
   */
  private static int parseTrun(
      TrackBundle trackBundle,
      int index,
      @Flags int flags,
      ParsableByteArray trun,
      int trackRunStart)
      throws ParserException {
    trun.setPosition(Atom.HEADER_SIZE);
    int fullAtom = trun.readInt();
    int atomFlags = Atom.parseFullAtomFlags(fullAtom);

    Track track = trackBundle.moovSampleTable.track;
    TrackFragment fragment = trackBundle.fragment;
    DefaultSampleValues defaultSampleValues = castNonNull(fragment.header);

    fragment.trunLength[index] = trun.readUnsignedIntToInt();
    fragment.trunDataPosition[index] = fragment.dataPosition;
    if ((atomFlags & 0x01 /* data_offset_present */) != 0) {
      fragment.trunDataPosition[index] += trun.readInt();
    }

    boolean firstSampleFlagsPresent = (atomFlags & 0x04 /* first_sample_flags_present */) != 0;
    int firstSampleFlags = defaultSampleValues.flags;
    if (firstSampleFlagsPresent) {
      firstSampleFlags = trun.readInt();
    }

    boolean sampleDurationsPresent = (atomFlags & 0x100 /* sample_duration_present */) != 0;
    boolean sampleSizesPresent = (atomFlags & 0x200 /* sample_size_present */) != 0;
    boolean sampleFlagsPresent = (atomFlags & 0x400 /* sample_flags_present */) != 0;
    boolean sampleCompositionTimeOffsetsPresent =
        (atomFlags & 0x800 /* sample_composition_time_offsets_present */) != 0;

    // Offset to the entire video timeline. In the presence of B-frames this is usually used to
    // ensure that the first frame's presentation timestamp is zero.
    long edtsOffset = 0;

    // Currently we only support a single edit that moves the entire media timeline (indicated by
    // duration == 0). Other uses of edit lists are uncommon and unsupported.
    if (track.editListDurations != null
        && track.editListDurations.length == 1
        && track.editListDurations[0] == 0) {
      edtsOffset = castNonNull(track.editListMediaTimes)[0];
    }

    int[] sampleSizeTable = fragment.sampleSizeTable;
    long[] samplePresentationTimesUs = fragment.samplePresentationTimesUs;
    boolean[] sampleIsSyncFrameTable = fragment.sampleIsSyncFrameTable;

    boolean workaroundEveryVideoFrameIsSyncFrame =
        track.type == C.TRACK_TYPE_VIDEO
            && (flags & FLAG_WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME) != 0;

    int trackRunEnd = trackRunStart + fragment.trunLength[index];
    long timescale = track.timescale;
    long cumulativeTime = fragment.nextFragmentDecodeTime;
    for (int i = trackRunStart; i < trackRunEnd; i++) {
      // Use trun values if present, otherwise tfhd, otherwise trex.
      int sampleDuration =
          checkNonNegative(sampleDurationsPresent ? trun.readInt() : defaultSampleValues.duration);
      int sampleSize =
          checkNonNegative(sampleSizesPresent ? trun.readInt() : defaultSampleValues.size);
      int sampleFlags =
          sampleFlagsPresent
              ? trun.readInt()
              : (i == 0 && firstSampleFlagsPresent) ? firstSampleFlags : defaultSampleValues.flags;
      int sampleCompositionTimeOffset = 0;
      if (sampleCompositionTimeOffsetsPresent) {
        // The BMFF spec (ISO 14496-12) states that sample offsets should be unsigned integers in
        // version 0 trun boxes, however a significant number of streams violate the spec and use
        // signed integers instead. It's safe to always decode sample offsets as signed integers
        // here, because unsigned integers will still be parsed correctly (unless their top bit is
        // set, which is never true in practice because sample offsets are always small).
        sampleCompositionTimeOffset = trun.readInt();
      }
      long samplePresentationTime = cumulativeTime + sampleCompositionTimeOffset - edtsOffset;
      samplePresentationTimesUs[i] =
          Util.scaleLargeTimestamp(samplePresentationTime, C.MICROS_PER_SECOND, timescale);
      if (!fragment.nextFragmentDecodeTimeIncludesMoov) {
        samplePresentationTimesUs[i] += trackBundle.moovSampleTable.durationUs;
      }
      sampleSizeTable[i] = sampleSize;
      sampleIsSyncFrameTable[i] =
          ((sampleFlags >> 16) & 0x1) == 0 && (!workaroundEveryVideoFrameIsSyncFrame || i == 0);
      cumulativeTime += sampleDuration;
    }
    fragment.nextFragmentDecodeTime = cumulativeTime;
    return trackRunEnd;
  }

  private static int checkNonNegative(int value) throws ParserException {
    if (value < 0) {
      throw ParserException.createForMalformedContainer(
          "Unexpected negative value: " + value, /* cause= */ null);
    }
    return value;
  }

  private static void parseUuid(
      ParsableByteArray uuid, TrackFragment out, byte[] extendedTypeScratch)
      throws ParserException {
    uuid.setPosition(Atom.HEADER_SIZE);
    uuid.readBytes(extendedTypeScratch, 0, 16);

    // Currently this parser only supports Microsoft's PIFF SampleEncryptionBox.
    if (!Arrays.equals(extendedTypeScratch, PIFF_SAMPLE_ENCRYPTION_BOX_EXTENDED_TYPE)) {
      return;
    }

    // Except for the extended type, this box is identical to a SENC box. See "Portable encoding of
    // audio-video objects: The Protected Interoperable File Format (PIFF), John A. Bocharov et al,
    // Section 5.3.2.1."
    parseSenc(uuid, 16, out);
  }

  private static void parseSenc(ParsableByteArray senc, TrackFragment out) throws ParserException {
    parseSenc(senc, 0, out);
  }

  private static void parseSenc(ParsableByteArray senc, int offset, TrackFragment out)
      throws ParserException {
    senc.setPosition(Atom.HEADER_SIZE + offset);
    int fullAtom = senc.readInt();
    int flags = Atom.parseFullAtomFlags(fullAtom);

    if ((flags & 0x01 /* override_track_encryption_box_parameters */) != 0) {
      // TODO: Implement this.
      throw ParserException.createForUnsupportedContainerFeature(
          "Overriding TrackEncryptionBox parameters is unsupported.");
    }

    boolean subsampleEncryption = (flags & 0x02 /* use_subsample_encryption */) != 0;
    int sampleCount = senc.readUnsignedIntToInt();
    if (sampleCount == 0) {
      // Samples are unencrypted.
      Arrays.fill(out.sampleHasSubsampleEncryptionTable, 0, out.sampleCount, false);
      return;
    } else if (sampleCount != out.sampleCount) {
      throw ParserException.createForMalformedContainer(
          "Senc sample count "
              + sampleCount
              + " is different from fragment sample count"
              + out.sampleCount,
          /* cause= */ null);
    }

    Arrays.fill(out.sampleHasSubsampleEncryptionTable, 0, sampleCount, subsampleEncryption);
    out.initEncryptionData(senc.bytesLeft());
    out.fillEncryptionData(senc);
  }

  private static void parseSampleGroups(
      ContainerAtom traf, @Nullable String schemeType, TrackFragment out) throws ParserException {
    // Find sbgp and sgpd boxes with grouping_type == seig.
    @Nullable ParsableByteArray sbgp = null;
    @Nullable ParsableByteArray sgpd = null;
    for (int i = 0; i < traf.leafChildren.size(); i++) {
      LeafAtom leafAtom = traf.leafChildren.get(i);
      ParsableByteArray leafAtomData = leafAtom.data;
      if (leafAtom.type == Atom.TYPE_sbgp) {
        leafAtomData.setPosition(Atom.FULL_HEADER_SIZE);
        if (leafAtomData.readInt() == SAMPLE_GROUP_TYPE_seig) {
          sbgp = leafAtomData;
        }
      } else if (leafAtom.type == Atom.TYPE_sgpd) {
        leafAtomData.setPosition(Atom.FULL_HEADER_SIZE);
        if (leafAtomData.readInt() == SAMPLE_GROUP_TYPE_seig) {
          sgpd = leafAtomData;
        }
      }
    }
    if (sbgp == null || sgpd == null) {
      return;
    }

    sbgp.setPosition(Atom.HEADER_SIZE);
    int sbgpVersion = Atom.parseFullAtomVersion(sbgp.readInt());
    sbgp.skipBytes(4); // grouping_type == seig.
    if (sbgpVersion == 1) {
      sbgp.skipBytes(4); // grouping_type_parameter.
    }
    if (sbgp.readInt() != 1) { // entry_count.
      throw ParserException.createForUnsupportedContainerFeature(
          "Entry count in sbgp != 1 (unsupported).");
    }

    sgpd.setPosition(Atom.HEADER_SIZE);
    int sgpdVersion = Atom.parseFullAtomVersion(sgpd.readInt());
    sgpd.skipBytes(4); // grouping_type == seig.
    if (sgpdVersion == 1) {
      if (sgpd.readUnsignedInt() == 0) {
        throw ParserException.createForUnsupportedContainerFeature(
            "Variable length description in sgpd found (unsupported)");
      }
    } else if (sgpdVersion >= 2) {
      sgpd.skipBytes(4); // default_sample_description_index.
    }
    if (sgpd.readUnsignedInt() != 1) { // entry_count.
      throw ParserException.createForUnsupportedContainerFeature(
          "Entry count in sgpd != 1 (unsupported).");
    }

    // CencSampleEncryptionInformationGroupEntry
    sgpd.skipBytes(1); // reserved = 0.
    int patternByte = sgpd.readUnsignedByte();
    int cryptByteBlock = (patternByte & 0xF0) >> 4;
    int skipByteBlock = patternByte & 0x0F;
    boolean isProtected = sgpd.readUnsignedByte() == 1;
    if (!isProtected) {
      return;
    }
    int perSampleIvSize = sgpd.readUnsignedByte();
    byte[] keyId = new byte[16];
    sgpd.readBytes(keyId, 0, keyId.length);
    @Nullable byte[] constantIv = null;
    if (perSampleIvSize == 0) {
      int constantIvSize = sgpd.readUnsignedByte();
      constantIv = new byte[constantIvSize];
      sgpd.readBytes(constantIv, 0, constantIvSize);
    }
    out.definesEncryptionData = true;
    out.trackEncryptionBox =
        new TrackEncryptionBox(
            isProtected,
            schemeType,
            perSampleIvSize,
            keyId,
            cryptByteBlock,
            skipByteBlock,
            constantIv);
  }

  /**
   * Parses a sidx atom (defined in 14496-12).
   *
   * @param atom The atom data.
   * @param inputPosition The input position of the first byte after the atom.
   * @return A pair consisting of the earliest presentation time in microseconds, and the parsed
   *     {@link ChunkIndex}.
   */
  private static Pair<Long, ChunkIndex> parseSidx(ParsableByteArray atom, long inputPosition)
      throws ParserException {
    atom.setPosition(Atom.HEADER_SIZE);
    int fullAtom = atom.readInt();
    int version = Atom.parseFullAtomVersion(fullAtom);

    atom.skipBytes(4);
    long timescale = atom.readUnsignedInt();
    long earliestPresentationTime;
    long offset = inputPosition;
    if (version == 0) {
      earliestPresentationTime = atom.readUnsignedInt();
      offset += atom.readUnsignedInt();
    } else {
      earliestPresentationTime = atom.readUnsignedLongToLong();
      offset += atom.readUnsignedLongToLong();
    }
    long earliestPresentationTimeUs =
        Util.scaleLargeTimestamp(earliestPresentationTime, C.MICROS_PER_SECOND, timescale);

    atom.skipBytes(2);

    int referenceCount = atom.readUnsignedShort();
    int[] sizes = new int[referenceCount];
    long[] offsets = new long[referenceCount];
    long[] durationsUs = new long[referenceCount];
    long[] timesUs = new long[referenceCount];

    long time = earliestPresentationTime;
    long timeUs = earliestPresentationTimeUs;
    for (int i = 0; i < referenceCount; i++) {
      int firstInt = atom.readInt();

      int type = 0x80000000 & firstInt;
      if (type != 0) {
        throw ParserException.createForMalformedContainer(
            "Unhandled indirect reference", /* cause= */ null);
      }
      long referenceDuration = atom.readUnsignedInt();

      sizes[i] = 0x7FFFFFFF & firstInt;
      offsets[i] = offset;

      // Calculate time and duration values such that any rounding errors are consistent. i.e. That
      // timesUs[i] + durationsUs[i] == timesUs[i + 1].
      timesUs[i] = timeUs;
      time += referenceDuration;
      timeUs = Util.scaleLargeTimestamp(time, C.MICROS_PER_SECOND, timescale);
      durationsUs[i] = timeUs - timesUs[i];

      atom.skipBytes(4);
      offset += sizes[i];
    }

    return Pair.create(
        earliestPresentationTimeUs, new ChunkIndex(sizes, offsets, durationsUs, timesUs));
  }

  private void readEncryptionData(ExtractorInput input) throws IOException {
    @Nullable TrackBundle nextTrackBundle = null;
    long nextDataOffset = Long.MAX_VALUE;
    int trackBundlesSize = trackBundles.size();
    for (int i = 0; i < trackBundlesSize; i++) {
      TrackFragment trackFragment = trackBundles.valueAt(i).fragment;
      if (trackFragment.sampleEncryptionDataNeedsFill
          && trackFragment.auxiliaryDataPosition < nextDataOffset) {
        nextDataOffset = trackFragment.auxiliaryDataPosition;
        nextTrackBundle = trackBundles.valueAt(i);
      }
    }
    if (nextTrackBundle == null) {
      parserState = STATE_READING_SAMPLE_START;
      return;
    }
    int bytesToSkip = (int) (nextDataOffset - input.getPosition());
    if (bytesToSkip < 0) {
      throw ParserException.createForMalformedContainer(
          "Offset to encryption data was negative.", /* cause= */ null);
    }
    input.skipFully(bytesToSkip);
    nextTrackBundle.fragment.fillEncryptionData(input);
  }

  /**
   * Attempts to read the next sample in the current mdat atom. The read sample may be output or
   * skipped.
   *
   * <p>If there are no more samples in the current mdat atom then the parser state is transitioned
   * to {@link #STATE_READING_ATOM_HEADER} and {@code false} is returned.
   *
   * <p>It is possible for a sample to be partially read in the case that an exception is thrown. In
   * this case the method can be called again to read the remainder of the sample.
   *
   * @param input The {@link ExtractorInput} from which to read data.
   * @return Whether a sample was read. The read sample may have been output or skipped. False
   *     indicates that there are no samples left to read in the current mdat.
   * @throws IOException If an error occurs reading from the input.
   */
  private boolean readSample(ExtractorInput input) throws IOException {
    @Nullable TrackBundle trackBundle = currentTrackBundle;
    if (trackBundle == null) {
      trackBundle = getNextTrackBundle(trackBundles);
      if (trackBundle == null) {
        // We've run out of samples in the current mdat. Discard any trailing data and prepare to
        // read the header of the next atom.
        int bytesToSkip = (int) (endOfMdatPosition - input.getPosition());
        if (bytesToSkip < 0) {
          throw ParserException.createForMalformedContainer(
              "Offset to end of mdat was negative.", /* cause= */ null);
        }
        input.skipFully(bytesToSkip);
        enterReadingAtomHeaderState();
        return false;
      }

      long nextDataPosition = trackBundle.getCurrentSampleOffset();
      // We skip bytes preceding the next sample to read.
      int bytesToSkip = (int) (nextDataPosition - input.getPosition());
      if (bytesToSkip < 0) {
        // Assume the sample data must be contiguous in the mdat with no preceding data.
        Log.w(TAG, "Ignoring negative offset to sample data.");
        bytesToSkip = 0;
      }
      input.skipFully(bytesToSkip);
      currentTrackBundle = trackBundle;
    }
    if (parserState == STATE_READING_SAMPLE_START) {
      sampleSize = trackBundle.getCurrentSampleSize();

      if (trackBundle.currentSampleIndex < trackBundle.firstSampleToOutputIndex) {
        input.skipFully(sampleSize);
        trackBundle.skipSampleEncryptionData();
        if (!trackBundle.next()) {
          currentTrackBundle = null;
        }
        parserState = STATE_READING_SAMPLE_START;
        return true;
      }

      if (trackBundle.moovSampleTable.track.sampleTransformation
          == Track.TRANSFORMATION_CEA608_CDAT) {
        sampleSize -= Atom.HEADER_SIZE;
        input.skipFully(Atom.HEADER_SIZE);
      }

      if (MimeTypes.AUDIO_AC4.equals(trackBundle.moovSampleTable.track.format.sampleMimeType)) {
        // AC4 samples need to be prefixed with a clear sample header.
        sampleBytesWritten =
            trackBundle.outputSampleEncryptionData(sampleSize, Ac4Util.SAMPLE_HEADER_SIZE);
        Ac4Util.getAc4SampleHeader(sampleSize, scratch);
        trackBundle.output.sampleData(scratch, Ac4Util.SAMPLE_HEADER_SIZE);
        sampleBytesWritten += Ac4Util.SAMPLE_HEADER_SIZE;
      } else {
        sampleBytesWritten =
            trackBundle.outputSampleEncryptionData(sampleSize, /* clearHeaderSize= */ 0);
      }
      sampleSize += sampleBytesWritten;
      parserState = STATE_READING_SAMPLE_CONTINUE;
      sampleCurrentNalBytesRemaining = 0;
    }

    Track track = trackBundle.moovSampleTable.track;
    TrackOutput output = trackBundle.output;
    long sampleTimeUs = trackBundle.getCurrentSamplePresentationTimeUs();
    if (timestampAdjuster != null) {
      sampleTimeUs = timestampAdjuster.adjustSampleTimestamp(sampleTimeUs);
    }
    if (track.nalUnitLengthFieldLength != 0) {
      // Zero the top three bytes of the array that we'll use to decode nal unit lengths, in case
      // they're only 1 or 2 bytes long.
      byte[] nalPrefixData = nalPrefix.getData();
      nalPrefixData[0] = 0;
      nalPrefixData[1] = 0;
      nalPrefixData[2] = 0;
      int nalUnitPrefixLength = track.nalUnitLengthFieldLength + 1;
      int nalUnitLengthFieldLengthDiff = 4 - track.nalUnitLengthFieldLength;
      // NAL units are length delimited, but the decoder requires start code delimited units.
      // Loop until we've written the sample to the track output, replacing length delimiters with
      // start codes as we encounter them.
      while (sampleBytesWritten < sampleSize) {
        if (sampleCurrentNalBytesRemaining == 0) {
          // Read the NAL length so that we know where we find the next one, and its type.
          input.readFully(nalPrefixData, nalUnitLengthFieldLengthDiff, nalUnitPrefixLength);
          nalPrefix.setPosition(0);
          int nalLengthInt = nalPrefix.readInt();
          if (nalLengthInt < 1) {
            throw ParserException.createForMalformedContainer(
                "Invalid NAL length", /* cause= */ null);
          }
          sampleCurrentNalBytesRemaining = nalLengthInt - 1;
          // Write a start code for the current NAL unit.
          nalStartCode.setPosition(0);
          output.sampleData(nalStartCode, 4);
          // Write the NAL unit type byte.
          output.sampleData(nalPrefix, 1);
          processSeiNalUnitPayload =
              ceaTrackOutputs.length > 0
                  && NalUnitUtil.isNalUnitSei(track.format.sampleMimeType, nalPrefixData[4]);
          sampleBytesWritten += 5;
          sampleSize += nalUnitLengthFieldLengthDiff;
        } else {
          int writtenBytes;
          if (processSeiNalUnitPayload) {
            // Read and write the payload of the SEI NAL unit.
            nalBuffer.reset(sampleCurrentNalBytesRemaining);
            input.readFully(nalBuffer.getData(), 0, sampleCurrentNalBytesRemaining);
            output.sampleData(nalBuffer, sampleCurrentNalBytesRemaining);
            writtenBytes = sampleCurrentNalBytesRemaining;
            // Unescape and process the SEI NAL unit.
            int unescapedLength =
                NalUnitUtil.unescapeStream(nalBuffer.getData(), nalBuffer.limit());
            // If the format is H.265/HEVC the NAL unit header has two bytes so skip one more byte.
            nalBuffer.setPosition(MimeTypes.VIDEO_H265.equals(track.format.sampleMimeType) ? 1 : 0);
            nalBuffer.setLimit(unescapedLength);
            CeaUtil.consume(sampleTimeUs, nalBuffer, ceaTrackOutputs);
          } else {
            // Write the payload of the NAL unit.
            writtenBytes = output.sampleData(input, sampleCurrentNalBytesRemaining, false);
          }
          sampleBytesWritten += writtenBytes;
          sampleCurrentNalBytesRemaining -= writtenBytes;
        }
      }
    } else {
      while (sampleBytesWritten < sampleSize) {
        int writtenBytes = output.sampleData(input, sampleSize - sampleBytesWritten, false);
        sampleBytesWritten += writtenBytes;
      }
    }

    @C.BufferFlags int sampleFlags = trackBundle.getCurrentSampleFlags();

    // Encryption data.
    @Nullable TrackOutput.CryptoData cryptoData = null;
    @Nullable TrackEncryptionBox encryptionBox = trackBundle.getEncryptionBoxIfEncrypted();
    if (encryptionBox != null) {
      cryptoData = encryptionBox.cryptoData;
    }

    output.sampleMetadata(sampleTimeUs, sampleFlags, sampleSize, 0, cryptoData);

    // After we have the sampleTimeUs, we can commit all the pending metadata samples
    outputPendingMetadataSamples(sampleTimeUs);
    if (!trackBundle.next()) {
      currentTrackBundle = null;
    }
    parserState = STATE_READING_SAMPLE_START;
    return true;
  }

  /**
   * Called immediately after outputting a non-metadata sample, to output any pending metadata
   * samples.
   *
   * @param sampleTimeUs The timestamp of the non-metadata sample that was just output.
   */
  private void outputPendingMetadataSamples(long sampleTimeUs) {
    while (!pendingMetadataSampleInfos.isEmpty()) {
      MetadataSampleInfo metadataSampleInfo = pendingMetadataSampleInfos.removeFirst();
      pendingMetadataSampleBytes -= metadataSampleInfo.size;
      long metadataSampleTimeUs = metadataSampleInfo.sampleTimeUs;
      if (metadataSampleInfo.sampleTimeIsRelative) {
        // The metadata sample timestamp is relative to the timestamp of the non-metadata sample
        // that was just output. Make it absolute.
        metadataSampleTimeUs += sampleTimeUs;
      }
      if (timestampAdjuster != null) {
        metadataSampleTimeUs = timestampAdjuster.adjustSampleTimestamp(metadataSampleTimeUs);
      }
      for (TrackOutput emsgTrackOutput : emsgTrackOutputs) {
        emsgTrackOutput.sampleMetadata(
            metadataSampleTimeUs,
            C.BUFFER_FLAG_KEY_FRAME,
            metadataSampleInfo.size,
            pendingMetadataSampleBytes,
            null);
      }
    }
  }

  /**
   * Returns the {@link TrackBundle} whose sample has the earliest file position out of those yet to
   * be consumed, or null if all have been consumed.
   */
  @Nullable
  private static TrackBundle getNextTrackBundle(SparseArray<TrackBundle> trackBundles) {
    @Nullable TrackBundle nextTrackBundle = null;
    long nextSampleOffset = Long.MAX_VALUE;

    int trackBundlesSize = trackBundles.size();
    for (int i = 0; i < trackBundlesSize; i++) {
      TrackBundle trackBundle = trackBundles.valueAt(i);
      if ((!trackBundle.currentlyInFragment
              && trackBundle.currentSampleIndex == trackBundle.moovSampleTable.sampleCount)
          || (trackBundle.currentlyInFragment
              && trackBundle.currentTrackRunIndex == trackBundle.fragment.trunCount)) {
        // This track sample table or fragment contains no more runs in the next mdat box.
      } else {
        long sampleOffset = trackBundle.getCurrentSampleOffset();
        if (sampleOffset < nextSampleOffset) {
          nextTrackBundle = trackBundle;
          nextSampleOffset = sampleOffset;
        }
      }
    }
    return nextTrackBundle;
  }

  /** Returns DrmInitData from leaf atoms. */
  @Nullable
  private static DrmInitData getDrmInitDataFromAtoms(List<Atom.LeafAtom> leafChildren) {
    @Nullable ArrayList<SchemeData> schemeDatas = null;
    int leafChildrenSize = leafChildren.size();
    for (int i = 0; i < leafChildrenSize; i++) {
      LeafAtom child = leafChildren.get(i);
      if (child.type == Atom.TYPE_pssh) {
        if (schemeDatas == null) {
          schemeDatas = new ArrayList<>();
        }
        byte[] psshData = child.data.getData();
        @Nullable UUID uuid = PsshAtomUtil.parseUuid(psshData);
        if (uuid == null) {
          Log.w(TAG, "Skipped pssh atom (failed to extract uuid)");
        } else {
          schemeDatas.add(new SchemeData(uuid, MimeTypes.VIDEO_MP4, psshData));
        }
      }
    }
    return schemeDatas == null ? null : new DrmInitData(schemeDatas);
  }

  /** Returns whether the extractor should decode a leaf atom with type {@code atom}. */
  private static boolean shouldParseLeafAtom(int atom) {
    return atom == Atom.TYPE_hdlr
        || atom == Atom.TYPE_mdhd
        || atom == Atom.TYPE_mvhd
        || atom == Atom.TYPE_sidx
        || atom == Atom.TYPE_stsd
        || atom == Atom.TYPE_stts
        || atom == Atom.TYPE_ctts
        || atom == Atom.TYPE_stsc
        || atom == Atom.TYPE_stsz
        || atom == Atom.TYPE_stz2
        || atom == Atom.TYPE_stco
        || atom == Atom.TYPE_co64
        || atom == Atom.TYPE_stss
        || atom == Atom.TYPE_tfdt
        || atom == Atom.TYPE_tfhd
        || atom == Atom.TYPE_tkhd
        || atom == Atom.TYPE_trex
        || atom == Atom.TYPE_trun
        || atom == Atom.TYPE_pssh
        || atom == Atom.TYPE_saiz
        || atom == Atom.TYPE_saio
        || atom == Atom.TYPE_senc
        || atom == Atom.TYPE_uuid
        || atom == Atom.TYPE_sbgp
        || atom == Atom.TYPE_sgpd
        || atom == Atom.TYPE_elst
        || atom == Atom.TYPE_mehd
        || atom == Atom.TYPE_emsg;
  }

  /** Returns whether the extractor should decode a container atom with type {@code atom}. */
  private static boolean shouldParseContainerAtom(int atom) {
    return atom == Atom.TYPE_moov
        || atom == Atom.TYPE_trak
        || atom == Atom.TYPE_mdia
        || atom == Atom.TYPE_minf
        || atom == Atom.TYPE_stbl
        || atom == Atom.TYPE_moof
        || atom == Atom.TYPE_traf
        || atom == Atom.TYPE_mvex
        || atom == Atom.TYPE_edts;
  }

  /** Holds data corresponding to a metadata sample. */
  private static final class MetadataSampleInfo {

    public final long sampleTimeUs;
    public final boolean sampleTimeIsRelative;
    public final int size;

    public MetadataSampleInfo(long sampleTimeUs, boolean sampleTimeIsRelative, int size) {
      this.sampleTimeUs = sampleTimeUs;
      this.sampleTimeIsRelative = sampleTimeIsRelative;
      this.size = size;
    }
  }

  /** Holds data corresponding to a single track. */
  private static final class TrackBundle {

    private static final int SINGLE_SUBSAMPLE_ENCRYPTION_DATA_LENGTH = 8;

    public final TrackOutput output;
    public final TrackFragment fragment;
    public final ParsableByteArray scratch;

    public TrackSampleTable moovSampleTable;
    public DefaultSampleValues defaultSampleValues;
    public int currentSampleIndex;
    public int currentSampleInTrackRun;
    public int currentTrackRunIndex;
    public int firstSampleToOutputIndex;

    private final ParsableByteArray encryptionSignalByte;
    private final ParsableByteArray defaultInitializationVector;

    private boolean currentlyInFragment;

    public TrackBundle(
        TrackOutput output,
        TrackSampleTable moovSampleTable,
        DefaultSampleValues defaultSampleValues) {
      this.output = output;
      this.moovSampleTable = moovSampleTable;
      this.defaultSampleValues = defaultSampleValues;
      fragment = new TrackFragment();
      scratch = new ParsableByteArray();
      encryptionSignalByte = new ParsableByteArray(1);
      defaultInitializationVector = new ParsableByteArray();
      reset(moovSampleTable, defaultSampleValues);
    }

    public void reset(TrackSampleTable moovSampleTable, DefaultSampleValues defaultSampleValues) {
      this.moovSampleTable = moovSampleTable;
      this.defaultSampleValues = defaultSampleValues;
      output.format(moovSampleTable.track.format);
      resetFragmentInfo();
    }

    public void updateDrmInitData(DrmInitData drmInitData) {
      @Nullable
      TrackEncryptionBox encryptionBox =
          moovSampleTable.track.getSampleDescriptionEncryptionBox(
              castNonNull(fragment.header).sampleDescriptionIndex);
      @Nullable String schemeType = encryptionBox != null ? encryptionBox.schemeType : null;
      DrmInitData updatedDrmInitData = drmInitData.copyWithSchemeType(schemeType);
      Format format =
          moovSampleTable.track.format.buildUpon().setDrmInitData(updatedDrmInitData).build();
      output.format(format);
    }

    /** Resets the current fragment, sample indices and {@link #currentlyInFragment} boolean. */
    public void resetFragmentInfo() {
      fragment.reset();
      currentSampleIndex = 0;
      currentTrackRunIndex = 0;
      currentSampleInTrackRun = 0;
      firstSampleToOutputIndex = 0;
      currentlyInFragment = false;
    }

    /**
     * Advances {@link #firstSampleToOutputIndex} to point to the sync sample at or before the
     * specified seek time in the current fragment.
     *
     * @param timeUs The seek time, in microseconds.
     */
    public void seek(long timeUs) {
      int searchIndex = currentSampleIndex;
      while (searchIndex < fragment.sampleCount
          && fragment.getSamplePresentationTimeUs(searchIndex) <= timeUs) {
        if (fragment.sampleIsSyncFrameTable[searchIndex]) {
          firstSampleToOutputIndex = searchIndex;
        }
        searchIndex++;
      }
    }

    /** Returns the presentation time of the current sample in microseconds. */
    public long getCurrentSamplePresentationTimeUs() {
      return !currentlyInFragment
          ? moovSampleTable.timestampsUs[currentSampleIndex]
          : fragment.getSamplePresentationTimeUs(currentSampleIndex);
    }

    /** Returns the byte offset of the current sample. */
    public long getCurrentSampleOffset() {
      return !currentlyInFragment
          ? moovSampleTable.offsets[currentSampleIndex]
          : fragment.trunDataPosition[currentTrackRunIndex];
    }

    /** Returns the size of the current sample in bytes. */
    public int getCurrentSampleSize() {
      return !currentlyInFragment
          ? moovSampleTable.sizes[currentSampleIndex]
          : fragment.sampleSizeTable[currentSampleIndex];
    }

    /** Returns the {@link C.BufferFlags} corresponding to the current sample. */
    public @C.BufferFlags int getCurrentSampleFlags() {
      int flags =
          !currentlyInFragment
              ? moovSampleTable.flags[currentSampleIndex]
              : (fragment.sampleIsSyncFrameTable[currentSampleIndex] ? C.BUFFER_FLAG_KEY_FRAME : 0);
      if (getEncryptionBoxIfEncrypted() != null) {
        flags |= C.BUFFER_FLAG_ENCRYPTED;
      }
      return flags;
    }

    /**
     * Advances the indices in the bundle to point to the next sample in the sample table (if it has
     * not reached the fragments yet) or in the current fragment.
     *
     * <p>If the current sample is the last one in the sample table, then the advanced state will be
     * {@code currentSampleIndex == moovSampleTable.sampleCount}. If the current sample is the last
     * one in the current fragment, then the advanced state will be {@code currentSampleIndex ==
     * fragment.sampleCount}, {@code currentTrackRunIndex == fragment.trunCount} and {@code
     * #currentSampleInTrackRun == 0}.
     *
     * @return Whether this {@link TrackBundle} can be used to read the next sample without
     *     recomputing the next {@link TrackBundle}.
     */
    public boolean next() {
      currentSampleIndex++;
      if (!currentlyInFragment) {
        return false;
      }
      currentSampleInTrackRun++;
      if (currentSampleInTrackRun == fragment.trunLength[currentTrackRunIndex]) {
        currentTrackRunIndex++;
        currentSampleInTrackRun = 0;
        return false;
      }
      return true;
    }

    /**
     * Outputs the encryption data for the current sample.
     *
     * <p>This is not supported yet for samples specified in the sample table.
     *
     * @param sampleSize The size of the current sample in bytes, excluding any additional clear
     *     header that will be prefixed to the sample by the extractor.
     * @param clearHeaderSize The size of a clear header that will be prefixed to the sample by the
     *     extractor, or 0.
     * @return The number of written bytes.
     */
    public int outputSampleEncryptionData(int sampleSize, int clearHeaderSize) {
      @Nullable TrackEncryptionBox encryptionBox = getEncryptionBoxIfEncrypted();
      if (encryptionBox == null) {
        return 0;
      }

      ParsableByteArray initializationVectorData;
      int vectorSize;
      if (encryptionBox.perSampleIvSize != 0) {
        initializationVectorData = fragment.sampleEncryptionData;
        vectorSize = encryptionBox.perSampleIvSize;
      } else {
        // The default initialization vector should be used.
        byte[] initVectorData = castNonNull(encryptionBox.defaultInitializationVector);
        defaultInitializationVector.reset(initVectorData, initVectorData.length);
        initializationVectorData = defaultInitializationVector;
        vectorSize = initVectorData.length;
      }

      boolean haveSubsampleEncryptionTable =
          fragment.sampleHasSubsampleEncryptionTable(currentSampleIndex);
      boolean writeSubsampleEncryptionData = haveSubsampleEncryptionTable || clearHeaderSize != 0;

      // Write the signal byte, containing the vector size and the subsample encryption flag.
      encryptionSignalByte.getData()[0] =
          (byte) (vectorSize | (writeSubsampleEncryptionData ? 0x80 : 0));
      encryptionSignalByte.setPosition(0);
      output.sampleData(encryptionSignalByte, 1, TrackOutput.SAMPLE_DATA_PART_ENCRYPTION);
      // Write the vector.
      output.sampleData(
          initializationVectorData, vectorSize, TrackOutput.SAMPLE_DATA_PART_ENCRYPTION);

      if (!writeSubsampleEncryptionData) {
        return 1 + vectorSize;
      }

      if (!haveSubsampleEncryptionTable) {
        // The sample is fully encrypted, except for the additional clear header that the extractor
        // is going to prefix. We need to synthesize subsample encryption data that takes the header
        // into account.
        scratch.reset(SINGLE_SUBSAMPLE_ENCRYPTION_DATA_LENGTH);
        // subsampleCount = 1 (unsigned short)
        byte[] data = scratch.getData();
        data[0] = (byte) 0;
        data[1] = (byte) 1;
        // clearDataSize = clearHeaderSize (unsigned short)
        data[2] = (byte) ((clearHeaderSize >> 8) & 0xFF);
        data[3] = (byte) (clearHeaderSize & 0xFF);
        // encryptedDataSize = sampleSize (unsigned int)
        data[4] = (byte) ((sampleSize >> 24) & 0xFF);
        data[5] = (byte) ((sampleSize >> 16) & 0xFF);
        data[6] = (byte) ((sampleSize >> 8) & 0xFF);
        data[7] = (byte) (sampleSize & 0xFF);
        output.sampleData(
            scratch,
            SINGLE_SUBSAMPLE_ENCRYPTION_DATA_LENGTH,
            TrackOutput.SAMPLE_DATA_PART_ENCRYPTION);
        return 1 + vectorSize + SINGLE_SUBSAMPLE_ENCRYPTION_DATA_LENGTH;
      }

      ParsableByteArray subsampleEncryptionData = fragment.sampleEncryptionData;
      int subsampleCount = subsampleEncryptionData.readUnsignedShort();
      subsampleEncryptionData.skipBytes(-2);
      int subsampleDataLength = 2 + 6 * subsampleCount;

      if (clearHeaderSize != 0) {
        // We need to account for the additional clear header by adding clearHeaderSize to
        // clearDataSize for the first subsample specified in the subsample encryption data.
        scratch.reset(subsampleDataLength);
        byte[] scratchData = scratch.getData();
        subsampleEncryptionData.readBytes(scratchData, /* offset= */ 0, subsampleDataLength);

        int clearDataSize = (scratchData[2] & 0xFF) << 8 | (scratchData[3] & 0xFF);
        int adjustedClearDataSize = clearDataSize + clearHeaderSize;
        scratchData[2] = (byte) ((adjustedClearDataSize >> 8) & 0xFF);
        scratchData[3] = (byte) (adjustedClearDataSize & 0xFF);
        subsampleEncryptionData = scratch;
      }

      output.sampleData(
          subsampleEncryptionData, subsampleDataLength, TrackOutput.SAMPLE_DATA_PART_ENCRYPTION);
      return 1 + vectorSize + subsampleDataLength;
    }

    /**
     * Skips the encryption data for the current sample.
     *
     * <p>This is not supported yet for samples specified in the sample table.
     */
    public void skipSampleEncryptionData() {
      @Nullable TrackEncryptionBox encryptionBox = getEncryptionBoxIfEncrypted();
      if (encryptionBox == null) {
        return;
      }

      ParsableByteArray sampleEncryptionData = fragment.sampleEncryptionData;
      if (encryptionBox.perSampleIvSize != 0) {
        sampleEncryptionData.skipBytes(encryptionBox.perSampleIvSize);
      }
      if (fragment.sampleHasSubsampleEncryptionTable(currentSampleIndex)) {
        sampleEncryptionData.skipBytes(6 * sampleEncryptionData.readUnsignedShort());
      }
    }

    @Nullable
    public TrackEncryptionBox getEncryptionBoxIfEncrypted() {
      if (!currentlyInFragment) {
        // Encryption is not supported yet for samples specified in the sample table.
        return null;
      }
      int sampleDescriptionIndex = castNonNull(fragment.header).sampleDescriptionIndex;
      @Nullable
      TrackEncryptionBox encryptionBox =
          fragment.trackEncryptionBox != null
              ? fragment.trackEncryptionBox
              : moovSampleTable.track.getSampleDescriptionEncryptionBox(sampleDescriptionIndex);
      return encryptionBox != null && encryptionBox.isEncrypted ? encryptionBox : null;
    }
  }
}
