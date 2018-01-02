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
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.ParserException;
import org.telegram.messenger.exoplayer2.drm.DrmInitData;
import org.telegram.messenger.exoplayer2.drm.DrmInitData.SchemeData;
import org.telegram.messenger.exoplayer2.extractor.ChunkIndex;
import org.telegram.messenger.exoplayer2.extractor.Extractor;
import org.telegram.messenger.exoplayer2.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer2.extractor.ExtractorOutput;
import org.telegram.messenger.exoplayer2.extractor.ExtractorsFactory;
import org.telegram.messenger.exoplayer2.extractor.PositionHolder;
import org.telegram.messenger.exoplayer2.extractor.SeekMap;
import org.telegram.messenger.exoplayer2.extractor.TrackOutput;
import org.telegram.messenger.exoplayer2.extractor.mp4.Atom.ContainerAtom;
import org.telegram.messenger.exoplayer2.extractor.mp4.Atom.LeafAtom;
import org.telegram.messenger.exoplayer2.text.cea.CeaUtil;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import org.telegram.messenger.exoplayer2.util.NalUnitUtil;
import org.telegram.messenger.exoplayer2.util.ParsableByteArray;
import org.telegram.messenger.exoplayer2.util.TimestampAdjuster;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.UUID;

/**
 * Facilitates the extraction of data from the fragmented mp4 container format.
 */
public final class FragmentedMp4Extractor implements Extractor {

  /**
   * Factory for {@link FragmentedMp4Extractor} instances.
   */
  public static final ExtractorsFactory FACTORY = new ExtractorsFactory() {

    @Override
    public Extractor[] createExtractors() {
      return new Extractor[] {new FragmentedMp4Extractor()};
    }

  };

  /**
   * Flags controlling the behavior of the extractor.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(flag = true, value = {FLAG_WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME,
      FLAG_WORKAROUND_IGNORE_TFDT_BOX, FLAG_ENABLE_EMSG_TRACK, FLAG_ENABLE_CEA608_TRACK,
      FLAG_SIDELOADED, FLAG_WORKAROUND_IGNORE_EDIT_LISTS})
  public @interface Flags {}
  /**
   * Flag to work around an issue in some video streams where every frame is marked as a sync frame.
   * The workaround overrides the sync frame flags in the stream, forcing them to false except for
   * the first sample in each segment.
   * <p>
   * This flag does nothing if the stream is not a video stream.
   */
  public static final int FLAG_WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME = 1;
  /**
   * Flag to ignore any tfdt boxes in the stream.
   */
  public static final int FLAG_WORKAROUND_IGNORE_TFDT_BOX = 2;
  /**
   * Flag to indicate that the extractor should output an event message metadata track. Any event
   * messages in the stream will be delivered as samples to this track.
   */
  public static final int FLAG_ENABLE_EMSG_TRACK = 4;
  /**
   * Flag to indicate that the extractor should output a CEA-608 text track. Any CEA-608 messages
   * contained within SEI NAL units in the stream will be delivered as samples to this track.
   */
  public static final int FLAG_ENABLE_CEA608_TRACK = 8;
  /**
   * Flag to indicate that the {@link Track} was sideloaded, instead of being declared by the MP4
   * container.
   */
  private static final int FLAG_SIDELOADED = 16;
  /**
   * Flag to ignore any edit lists in the stream.
   */
  public static final int FLAG_WORKAROUND_IGNORE_EDIT_LISTS = 32;

  private static final String TAG = "FragmentedMp4Extractor";
  private static final int SAMPLE_GROUP_TYPE_seig = Util.getIntegerCodeForString("seig");
  private static final byte[] PIFF_SAMPLE_ENCRYPTION_BOX_EXTENDED_TYPE =
      new byte[] {-94, 57, 79, 82, 90, -101, 79, 20, -94, 68, 108, 66, 124, 100, -115, -12};

  // Parser states.
  private static final int STATE_READING_ATOM_HEADER = 0;
  private static final int STATE_READING_ATOM_PAYLOAD = 1;
  private static final int STATE_READING_ENCRYPTION_DATA = 2;
  private static final int STATE_READING_SAMPLE_START = 3;
  private static final int STATE_READING_SAMPLE_CONTINUE = 4;

  // Workarounds.
  @Flags private final int flags;
  private final Track sideloadedTrack;

  // Track-linked data bundle, accessible as a whole through trackID.
  private final SparseArray<TrackBundle> trackBundles;

  // Temporary arrays.
  private final ParsableByteArray nalStartCode;
  private final ParsableByteArray nalPrefix;
  private final ParsableByteArray nalBuffer;
  private final ParsableByteArray encryptionSignalByte;
  private final ParsableByteArray defaultInitializationVector;

  // Adjusts sample timestamps.
  private final TimestampAdjuster timestampAdjuster;

  // Parser state.
  private final ParsableByteArray atomHeader;
  private final byte[] extendedTypeScratch;
  private final Stack<ContainerAtom> containerAtoms;
  private final LinkedList<MetadataSampleInfo> pendingMetadataSampleInfos;

  private int parserState;
  private int atomType;
  private long atomSize;
  private int atomHeaderBytesRead;
  private ParsableByteArray atomData;
  private long endOfMdatPosition;
  private int pendingMetadataSampleBytes;

  private long durationUs;
  private long segmentIndexEarliestPresentationTimeUs;
  private TrackBundle currentTrackBundle;
  private int sampleSize;
  private int sampleBytesWritten;
  private int sampleCurrentNalBytesRemaining;
  private boolean processSeiNalUnitPayload;

  // Extractor output.
  private ExtractorOutput extractorOutput;
  private TrackOutput eventMessageTrackOutput;
  private TrackOutput[] cea608TrackOutputs;

  // Whether extractorOutput.seekMap has been called.
  private boolean haveOutputSeekMap;

  public FragmentedMp4Extractor() {
    this(0);
  }

  /**
   * @param flags Flags that control the extractor's behavior.
   */
  public FragmentedMp4Extractor(@Flags int flags) {
    this(flags, null);
  }

  /**
   * @param flags Flags that control the extractor's behavior.
   * @param timestampAdjuster Adjusts sample timestamps. May be null if no adjustment is needed.
   */
  public FragmentedMp4Extractor(@Flags int flags, TimestampAdjuster timestampAdjuster) {
    this(flags, timestampAdjuster, null);
  }

  /**
   * @param flags Flags that control the extractor's behavior.
   * @param timestampAdjuster Adjusts sample timestamps. May be null if no adjustment is needed.
   * @param sideloadedTrack Sideloaded track information, in the case that the extractor
   *     will not receive a moov box in the input data.
   */
  public FragmentedMp4Extractor(@Flags int flags, TimestampAdjuster timestampAdjuster,
      Track sideloadedTrack) {
    this.flags = flags | (sideloadedTrack != null ? FLAG_SIDELOADED : 0);
    this.timestampAdjuster = timestampAdjuster;
    this.sideloadedTrack = sideloadedTrack;
    atomHeader = new ParsableByteArray(Atom.LONG_HEADER_SIZE);
    nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);
    nalPrefix = new ParsableByteArray(5);
    nalBuffer = new ParsableByteArray();
    encryptionSignalByte = new ParsableByteArray(1);
    defaultInitializationVector = new ParsableByteArray();
    extendedTypeScratch = new byte[16];
    containerAtoms = new Stack<>();
    pendingMetadataSampleInfos = new LinkedList<>();
    trackBundles = new SparseArray<>();
    durationUs = C.TIME_UNSET;
    segmentIndexEarliestPresentationTimeUs = C.TIME_UNSET;
    enterReadingAtomHeaderState();
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    return Sniffer.sniffFragmented(input);
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
    if (sideloadedTrack != null) {
      TrackBundle bundle = new TrackBundle(output.track(0, sideloadedTrack.type));
      bundle.init(sideloadedTrack, new DefaultSampleValues(0, 0, 0, 0));
      trackBundles.put(0, bundle);
      maybeInitExtraTracks();
      extractorOutput.endTracks();
    }
  }

  @Override
  public void seek(long position, long timeUs) {
    int trackCount = trackBundles.size();
    for (int i = 0; i < trackCount; i++) {
      trackBundles.valueAt(i).reset();
    }
    pendingMetadataSampleInfos.clear();
    pendingMetadataSampleBytes = 0;
    containerAtoms.clear();
    enterReadingAtomHeaderState();
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

    long atomPosition = input.getPosition() - atomHeaderBytesRead;
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
      if (!haveOutputSeekMap) {
        extractorOutput.seekMap(new SeekMap.Unseekable(durationUs));
        haveOutputSeekMap = true;
      }
      parserState = STATE_READING_ENCRYPTION_DATA;
      return true;
    }

    if (shouldParseContainerAtom(atomType)) {
      long endPosition = input.getPosition() + atomSize - Atom.HEADER_SIZE;
      containerAtoms.add(new ContainerAtom(atomType, endPosition));
      if (atomSize == atomHeaderBytesRead) {
        processAtomEnded(endPosition);
      } else {
        // Start reading the first child atom.
        enterReadingAtomHeaderState();
      }
    } else if (shouldParseLeafAtom(atomType)) {
      if (atomHeaderBytesRead != Atom.HEADER_SIZE) {
        throw new ParserException("Leaf atom defines extended atom size (unsupported).");
      }
      if (atomSize > Integer.MAX_VALUE) {
        throw new ParserException("Leaf atom with length > 2147483647 (unsupported).");
      }
      atomData = new ParsableByteArray((int) atomSize);
      System.arraycopy(atomHeader.data, 0, atomData.data, 0, Atom.HEADER_SIZE);
      parserState = STATE_READING_ATOM_PAYLOAD;
    } else {
      if (atomSize > Integer.MAX_VALUE) {
        throw new ParserException("Skipping atom with length > 2147483647 (unsupported).");
      }
      atomData = null;
      parserState = STATE_READING_ATOM_PAYLOAD;
    }

    return true;
  }

  private void readAtomPayload(ExtractorInput input) throws IOException, InterruptedException {
    int atomPayloadSize = (int) atomSize - atomHeaderBytesRead;
    if (atomData != null) {
      input.readFully(atomData.data, Atom.HEADER_SIZE, atomPayloadSize);
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
    Assertions.checkState(sideloadedTrack == null, "Unexpected moov box.");

    DrmInitData drmInitData = getDrmInitDataFromAtoms(moov.leafChildren);

    // Read declaration of track fragments in the Moov box.
    ContainerAtom mvex = moov.getContainerAtomOfType(Atom.TYPE_mvex);
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

    // Construction of tracks.
    SparseArray<Track> tracks = new SparseArray<>();
    int moovContainerChildrenSize = moov.containerChildren.size();
    for (int i = 0; i < moovContainerChildrenSize; i++) {
      Atom.ContainerAtom atom = moov.containerChildren.get(i);
      if (atom.type == Atom.TYPE_trak) {
        Track track = AtomParsers.parseTrak(atom, moov.getLeafAtomOfType(Atom.TYPE_mvhd), duration,
            drmInitData, (flags & FLAG_WORKAROUND_IGNORE_EDIT_LISTS) != 0, false);
        if (track != null) {
          tracks.put(track.id, track);
        }
      }
    }

    int trackCount = tracks.size();
    if (trackBundles.size() == 0) {
      // We need to create the track bundles.
      for (int i = 0; i < trackCount; i++) {
        Track track = tracks.valueAt(i);
        TrackBundle trackBundle = new TrackBundle(extractorOutput.track(i, track.type));
        trackBundle.init(track, defaultSampleValuesArray.get(track.id));
        trackBundles.put(track.id, trackBundle);
        durationUs = Math.max(durationUs, track.durationUs);
      }
      maybeInitExtraTracks();
      extractorOutput.endTracks();
    } else {
      Assertions.checkState(trackBundles.size() == trackCount);
      for (int i = 0; i < trackCount; i++) {
        Track track = tracks.valueAt(i);
        trackBundles.get(track.id).init(track, defaultSampleValuesArray.get(track.id));
      }
    }
  }

  private void onMoofContainerAtomRead(ContainerAtom moof) throws ParserException {
    parseMoof(moof, trackBundles, flags, extendedTypeScratch);
    DrmInitData drmInitData = getDrmInitDataFromAtoms(moof.leafChildren);
    if (drmInitData != null) {
      int trackCount = trackBundles.size();
      for (int i = 0; i < trackCount; i++) {
        trackBundles.valueAt(i).updateDrmInitData(drmInitData);
      }
    }
  }

  private void maybeInitExtraTracks() {
    if ((flags & FLAG_ENABLE_EMSG_TRACK) != 0 && eventMessageTrackOutput == null) {
      eventMessageTrackOutput = extractorOutput.track(trackBundles.size(), C.TRACK_TYPE_METADATA);
      eventMessageTrackOutput.format(Format.createSampleFormat(null, MimeTypes.APPLICATION_EMSG,
          Format.OFFSET_SAMPLE_RELATIVE));
    }
    if ((flags & FLAG_ENABLE_CEA608_TRACK) != 0 && cea608TrackOutputs == null) {
      TrackOutput cea608TrackOutput = extractorOutput.track(trackBundles.size() + 1,
          C.TRACK_TYPE_TEXT);
      cea608TrackOutput.format(Format.createTextSampleFormat(null, MimeTypes.APPLICATION_CEA608, 0,
          null));
      cea608TrackOutputs = new TrackOutput[] {cea608TrackOutput};
    }
  }

  /**
   * Handles an emsg atom (defined in 23009-1).
   */
  private void onEmsgLeafAtomRead(ParsableByteArray atom) {
    if (eventMessageTrackOutput == null) {
      return;
    }
    // Parse the event's presentation time delta.
    atom.setPosition(Atom.FULL_HEADER_SIZE);
    atom.readNullTerminatedString(); // schemeIdUri
    atom.readNullTerminatedString(); // value
    long timescale = atom.readUnsignedInt();
    long presentationTimeDeltaUs =
        Util.scaleLargeTimestamp(atom.readUnsignedInt(), C.MICROS_PER_SECOND, timescale);
    // Output the sample data.
    atom.setPosition(Atom.FULL_HEADER_SIZE);
    int sampleSize = atom.bytesLeft();
    eventMessageTrackOutput.sampleData(atom, sampleSize);
    // Output the sample metadata.
    if (segmentIndexEarliestPresentationTimeUs != C.TIME_UNSET) {
      // We can output the sample metadata immediately.
      eventMessageTrackOutput.sampleMetadata(
          segmentIndexEarliestPresentationTimeUs + presentationTimeDeltaUs,
          C.BUFFER_FLAG_KEY_FRAME, sampleSize, 0 /* offset */, null);
    } else {
      // We need the first sample timestamp in the segment before we can output the metadata.
      pendingMetadataSampleInfos.addLast(
          new MetadataSampleInfo(presentationTimeDeltaUs, sampleSize));
      pendingMetadataSampleBytes += sampleSize;
    }
  }

  /**
   * Parses a trex atom (defined in 14496-12).
   */
  private static Pair<Integer, DefaultSampleValues> parseTrex(ParsableByteArray trex) {
    trex.setPosition(Atom.FULL_HEADER_SIZE);
    int trackId = trex.readInt();
    int defaultSampleDescriptionIndex = trex.readUnsignedIntToInt() - 1;
    int defaultSampleDuration = trex.readUnsignedIntToInt();
    int defaultSampleSize = trex.readUnsignedIntToInt();
    int defaultSampleFlags = trex.readInt();

    return Pair.create(trackId, new DefaultSampleValues(defaultSampleDescriptionIndex,
        defaultSampleDuration, defaultSampleSize, defaultSampleFlags));
  }

  /**
   * Parses an mehd atom (defined in 14496-12).
   */
  private static long parseMehd(ParsableByteArray mehd) {
    mehd.setPosition(Atom.HEADER_SIZE);
    int fullAtom = mehd.readInt();
    int version = Atom.parseFullAtomVersion(fullAtom);
    return version == 0 ? mehd.readUnsignedInt() : mehd.readUnsignedLongToLong();
  }

  private static void parseMoof(ContainerAtom moof, SparseArray<TrackBundle> trackBundleArray,
      @Flags int flags, byte[] extendedTypeScratch) throws ParserException {
    int moofContainerChildrenSize = moof.containerChildren.size();
    for (int i = 0; i < moofContainerChildrenSize; i++) {
      Atom.ContainerAtom child = moof.containerChildren.get(i);
      // TODO: Support multiple traf boxes per track in a single moof.
      if (child.type == Atom.TYPE_traf) {
        parseTraf(child, trackBundleArray, flags, extendedTypeScratch);
      }
    }
  }

  /**
   * Parses a traf atom (defined in 14496-12).
   */
  private static void parseTraf(ContainerAtom traf, SparseArray<TrackBundle> trackBundleArray,
      @Flags int flags, byte[] extendedTypeScratch) throws ParserException {
    LeafAtom tfhd = traf.getLeafAtomOfType(Atom.TYPE_tfhd);
    TrackBundle trackBundle = parseTfhd(tfhd.data, trackBundleArray, flags);
    if (trackBundle == null) {
      return;
    }

    TrackFragment fragment = trackBundle.fragment;
    long decodeTime = fragment.nextFragmentDecodeTime;
    trackBundle.reset();

    LeafAtom tfdtAtom = traf.getLeafAtomOfType(Atom.TYPE_tfdt);
    if (tfdtAtom != null && (flags & FLAG_WORKAROUND_IGNORE_TFDT_BOX) == 0) {
      decodeTime = parseTfdt(traf.getLeafAtomOfType(Atom.TYPE_tfdt).data);
    }

    parseTruns(traf, trackBundle, decodeTime, flags);

    TrackEncryptionBox encryptionBox = trackBundle.track
        .getSampleDescriptionEncryptionBox(fragment.header.sampleDescriptionIndex);

    LeafAtom saiz = traf.getLeafAtomOfType(Atom.TYPE_saiz);
    if (saiz != null) {
      parseSaiz(encryptionBox, saiz.data, fragment);
    }

    LeafAtom saio = traf.getLeafAtomOfType(Atom.TYPE_saio);
    if (saio != null) {
      parseSaio(saio.data, fragment);
    }

    LeafAtom senc = traf.getLeafAtomOfType(Atom.TYPE_senc);
    if (senc != null) {
      parseSenc(senc.data, fragment);
    }

    LeafAtom sbgp = traf.getLeafAtomOfType(Atom.TYPE_sbgp);
    LeafAtom sgpd = traf.getLeafAtomOfType(Atom.TYPE_sgpd);
    if (sbgp != null && sgpd != null) {
      parseSgpd(sbgp.data, sgpd.data, encryptionBox != null ? encryptionBox.schemeType : null,
          fragment);
    }

    int leafChildrenSize = traf.leafChildren.size();
    for (int i = 0; i < leafChildrenSize; i++) {
      LeafAtom atom = traf.leafChildren.get(i);
      if (atom.type == Atom.TYPE_uuid) {
        parseUuid(atom.data, fragment, extendedTypeScratch);
      }
    }
  }

  private static void parseTruns(ContainerAtom traf, TrackBundle trackBundle, long decodeTime,
      @Flags int flags) {
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
        trunStartPosition = parseTrun(trackBundle, trunIndex++, decodeTime, flags, trun.data,
            trunStartPosition);
      }
    }
  }

  private static void parseSaiz(TrackEncryptionBox encryptionBox, ParsableByteArray saiz,
      TrackFragment out) throws ParserException {
    int vectorSize = encryptionBox.initializationVectorSize;
    saiz.setPosition(Atom.HEADER_SIZE);
    int fullAtom = saiz.readInt();
    int flags = Atom.parseFullAtomFlags(fullAtom);
    if ((flags & 0x01) == 1) {
      saiz.skipBytes(8);
    }
    int defaultSampleInfoSize = saiz.readUnsignedByte();

    int sampleCount = saiz.readUnsignedIntToInt();
    if (sampleCount != out.sampleCount) {
      throw new ParserException("Length mismatch: " + sampleCount + ", " + out.sampleCount);
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
    out.initEncryptionData(totalSize);
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
      throw new ParserException("Unexpected saio entry count: " + entryCount);
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
   * @return The {@link TrackBundle} to which the {@link TrackFragment} belongs, or null if the tfhd
   *     does not refer to any {@link TrackBundle}.
   */
  private static TrackBundle parseTfhd(ParsableByteArray tfhd,
      SparseArray<TrackBundle> trackBundles, int flags) {
    tfhd.setPosition(Atom.HEADER_SIZE);
    int fullAtom = tfhd.readInt();
    int atomFlags = Atom.parseFullAtomFlags(fullAtom);
    int trackId = tfhd.readInt();
    TrackBundle trackBundle = trackBundles.get((flags & FLAG_SIDELOADED) == 0 ? trackId : 0);
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
            ? tfhd.readUnsignedIntToInt() - 1 : defaultSampleValues.sampleDescriptionIndex;
    int defaultSampleDuration = ((atomFlags & 0x08 /* default_sample_duration_present */) != 0)
        ? tfhd.readUnsignedIntToInt() : defaultSampleValues.duration;
    int defaultSampleSize = ((atomFlags & 0x10 /* default_sample_size_present */) != 0)
        ? tfhd.readUnsignedIntToInt() : defaultSampleValues.size;
    int defaultSampleFlags = ((atomFlags & 0x20 /* default_sample_flags_present */) != 0)
        ? tfhd.readUnsignedIntToInt() : defaultSampleValues.flags;
    trackBundle.fragment.header = new DefaultSampleValues(defaultSampleDescriptionIndex,
        defaultSampleDuration, defaultSampleSize, defaultSampleFlags);
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
   * @param trackBundle The {@link TrackBundle} that contains the {@link TrackFragment} into
   *     which parsed data should be placed.
   * @param index Index of the track run in the fragment.
   * @param decodeTime The decode time of the first sample in the fragment run.
   * @param flags Flags to allow any required workaround to be executed.
   * @param trun The trun atom to decode.
   * @return The starting position of samples for the next run.
   */
  private static int parseTrun(TrackBundle trackBundle, int index, long decodeTime,
      @Flags int flags, ParsableByteArray trun, int trackRunStart) {
    trun.setPosition(Atom.HEADER_SIZE);
    int fullAtom = trun.readInt();
    int atomFlags = Atom.parseFullAtomFlags(fullAtom);

    Track track = trackBundle.track;
    TrackFragment fragment = trackBundle.fragment;
    DefaultSampleValues defaultSampleValues = fragment.header;

    fragment.trunLength[index] = trun.readUnsignedIntToInt();
    fragment.trunDataPosition[index] = fragment.dataPosition;
    if ((atomFlags & 0x01 /* data_offset_present */) != 0) {
      fragment.trunDataPosition[index] += trun.readInt();
    }

    boolean firstSampleFlagsPresent = (atomFlags & 0x04 /* first_sample_flags_present */) != 0;
    int firstSampleFlags = defaultSampleValues.flags;
    if (firstSampleFlagsPresent) {
      firstSampleFlags = trun.readUnsignedIntToInt();
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
    if (track.editListDurations != null && track.editListDurations.length == 1
        && track.editListDurations[0] == 0) {
      edtsOffset = Util.scaleLargeTimestamp(track.editListMediaTimes[0], 1000, track.timescale);
    }

    int[] sampleSizeTable = fragment.sampleSizeTable;
    int[] sampleCompositionTimeOffsetTable = fragment.sampleCompositionTimeOffsetTable;
    long[] sampleDecodingTimeTable = fragment.sampleDecodingTimeTable;
    boolean[] sampleIsSyncFrameTable = fragment.sampleIsSyncFrameTable;

    boolean workaroundEveryVideoFrameIsSyncFrame = track.type == C.TRACK_TYPE_VIDEO
        && (flags & FLAG_WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME) != 0;

    int trackRunEnd = trackRunStart + fragment.trunLength[index];
    long timescale = track.timescale;
    long cumulativeTime = index > 0 ? fragment.nextFragmentDecodeTime : decodeTime;
    for (int i = trackRunStart; i < trackRunEnd; i++) {
      // Use trun values if present, otherwise tfhd, otherwise trex.
      int sampleDuration = sampleDurationsPresent ? trun.readUnsignedIntToInt()
          : defaultSampleValues.duration;
      int sampleSize = sampleSizesPresent ? trun.readUnsignedIntToInt() : defaultSampleValues.size;
      int sampleFlags = (i == 0 && firstSampleFlagsPresent) ? firstSampleFlags
          : sampleFlagsPresent ? trun.readInt() : defaultSampleValues.flags;
      if (sampleCompositionTimeOffsetsPresent) {
        // The BMFF spec (ISO 14496-12) states that sample offsets should be unsigned integers in
        // version 0 trun boxes, however a significant number of streams violate the spec and use
        // signed integers instead. It's safe to always decode sample offsets as signed integers
        // here, because unsigned integers will still be parsed correctly (unless their top bit is
        // set, which is never true in practice because sample offsets are always small).
        int sampleOffset = trun.readInt();
        sampleCompositionTimeOffsetTable[i] = (int) ((sampleOffset * 1000L) / timescale);
      } else {
        sampleCompositionTimeOffsetTable[i] = 0;
      }
      sampleDecodingTimeTable[i] =
          Util.scaleLargeTimestamp(cumulativeTime, 1000, timescale) - edtsOffset;
      sampleSizeTable[i] = sampleSize;
      sampleIsSyncFrameTable[i] = ((sampleFlags >> 16) & 0x1) == 0
          && (!workaroundEveryVideoFrameIsSyncFrame || i == 0);
      cumulativeTime += sampleDuration;
    }
    fragment.nextFragmentDecodeTime = cumulativeTime;
    return trackRunEnd;
  }

  private static void parseUuid(ParsableByteArray uuid, TrackFragment out,
      byte[] extendedTypeScratch) throws ParserException {
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
      throw new ParserException("Overriding TrackEncryptionBox parameters is unsupported.");
    }

    boolean subsampleEncryption = (flags & 0x02 /* use_subsample_encryption */) != 0;
    int sampleCount = senc.readUnsignedIntToInt();
    if (sampleCount != out.sampleCount) {
      throw new ParserException("Length mismatch: " + sampleCount + ", " + out.sampleCount);
    }

    Arrays.fill(out.sampleHasSubsampleEncryptionTable, 0, sampleCount, subsampleEncryption);
    out.initEncryptionData(senc.bytesLeft());
    out.fillEncryptionData(senc);
  }

  private static void parseSgpd(ParsableByteArray sbgp, ParsableByteArray sgpd, String schemeType,
      TrackFragment out) throws ParserException {
    sbgp.setPosition(Atom.HEADER_SIZE);
    int sbgpFullAtom = sbgp.readInt();
    if (sbgp.readInt() != SAMPLE_GROUP_TYPE_seig) {
      // Only seig grouping type is supported.
      return;
    }
    if (Atom.parseFullAtomVersion(sbgpFullAtom) == 1) {
      sbgp.skipBytes(4); // default_length.
    }
    if (sbgp.readInt() != 1) { // entry_count.
      throw new ParserException("Entry count in sbgp != 1 (unsupported).");
    }

    sgpd.setPosition(Atom.HEADER_SIZE);
    int sgpdFullAtom = sgpd.readInt();
    if (sgpd.readInt() != SAMPLE_GROUP_TYPE_seig) {
      // Only seig grouping type is supported.
      return;
    }
    int sgpdVersion = Atom.parseFullAtomVersion(sgpdFullAtom);
    if (sgpdVersion == 1) {
      if (sgpd.readUnsignedInt() == 0) {
        throw new ParserException("Variable length description in sgpd found (unsupported)");
      }
    } else if (sgpdVersion >= 2) {
      sgpd.skipBytes(4); // default_sample_description_index.
    }
    if (sgpd.readUnsignedInt() != 1) { // entry_count.
      throw new ParserException("Entry count in sgpd != 1 (unsupported).");
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
    byte[] constantIv = null;
    if (isProtected && perSampleIvSize == 0) {
      int constantIvSize = sgpd.readUnsignedByte();
      constantIv = new byte[constantIvSize];
      sgpd.readBytes(constantIv, 0, constantIvSize);
    }
    out.definesEncryptionData = true;
    out.trackEncryptionBox = new TrackEncryptionBox(isProtected, schemeType, perSampleIvSize, keyId,
        cryptByteBlock, skipByteBlock, constantIv);
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
    long earliestPresentationTimeUs = Util.scaleLargeTimestamp(earliestPresentationTime,
        C.MICROS_PER_SECOND, timescale);

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
        throw new ParserException("Unhandled indirect reference");
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

    return Pair.create(earliestPresentationTimeUs,
        new ChunkIndex(sizes, offsets, durationsUs, timesUs));
  }

  private void readEncryptionData(ExtractorInput input) throws IOException, InterruptedException {
    TrackBundle nextTrackBundle = null;
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
      throw new ParserException("Offset to encryption data was negative.");
    }
    input.skipFully(bytesToSkip);
    nextTrackBundle.fragment.fillEncryptionData(input);
  }

  /**
   * Attempts to extract the next sample in the current mdat atom.
   * <p>
   * If there are no more samples in the current mdat atom then the parser state is transitioned
   * to {@link #STATE_READING_ATOM_HEADER} and {@code false} is returned.
   * <p>
   * It is possible for a sample to be extracted in part in the case that an exception is thrown. In
   * this case the method can be called again to extract the remainder of the sample.
   *
   * @param input The {@link ExtractorInput} from which to read data.
   * @return Whether a sample was extracted.
   * @throws IOException If an error occurs reading from the input.
   * @throws InterruptedException If the thread is interrupted.
   */
  private boolean readSample(ExtractorInput input) throws IOException, InterruptedException {
    if (parserState == STATE_READING_SAMPLE_START) {
      if (currentTrackBundle == null) {
        TrackBundle currentTrackBundle = getNextFragmentRun(trackBundles);
        if (currentTrackBundle == null) {
          // We've run out of samples in the current mdat. Discard any trailing data and prepare to
          // read the header of the next atom.
          int bytesToSkip = (int) (endOfMdatPosition - input.getPosition());
          if (bytesToSkip < 0) {
            throw new ParserException("Offset to end of mdat was negative.");
          }
          input.skipFully(bytesToSkip);
          enterReadingAtomHeaderState();
          return false;
        }

        long nextDataPosition = currentTrackBundle.fragment
            .trunDataPosition[currentTrackBundle.currentTrackRunIndex];
        // We skip bytes preceding the next sample to read.
        int bytesToSkip = (int) (nextDataPosition - input.getPosition());
        if (bytesToSkip < 0) {
          // Assume the sample data must be contiguous in the mdat with no preceding data.
          Log.w(TAG, "Ignoring negative offset to sample data.");
          bytesToSkip = 0;
        }
        input.skipFully(bytesToSkip);
        this.currentTrackBundle = currentTrackBundle;
      }
      sampleSize = currentTrackBundle.fragment
          .sampleSizeTable[currentTrackBundle.currentSampleIndex];
      if (currentTrackBundle.fragment.definesEncryptionData) {
        sampleBytesWritten = appendSampleEncryptionData(currentTrackBundle);
        sampleSize += sampleBytesWritten;
      } else {
        sampleBytesWritten = 0;
      }
      if (currentTrackBundle.track.sampleTransformation == Track.TRANSFORMATION_CEA608_CDAT) {
        sampleSize -= Atom.HEADER_SIZE;
        input.skipFully(Atom.HEADER_SIZE);
      }
      parserState = STATE_READING_SAMPLE_CONTINUE;
      sampleCurrentNalBytesRemaining = 0;
    }

    TrackFragment fragment = currentTrackBundle.fragment;
    Track track = currentTrackBundle.track;
    TrackOutput output = currentTrackBundle.output;
    int sampleIndex = currentTrackBundle.currentSampleIndex;
    if (track.nalUnitLengthFieldLength != 0) {
      // Zero the top three bytes of the array that we'll use to decode nal unit lengths, in case
      // they're only 1 or 2 bytes long.
      byte[] nalPrefixData = nalPrefix.data;
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
          sampleCurrentNalBytesRemaining = nalPrefix.readUnsignedIntToInt() - 1;
          // Write a start code for the current NAL unit.
          nalStartCode.setPosition(0);
          output.sampleData(nalStartCode, 4);
          // Write the NAL unit type byte.
          output.sampleData(nalPrefix, 1);
          processSeiNalUnitPayload = cea608TrackOutputs != null
              && NalUnitUtil.isNalUnitSei(track.format.sampleMimeType, nalPrefixData[4]);
          sampleBytesWritten += 5;
          sampleSize += nalUnitLengthFieldLengthDiff;
        } else {
          int writtenBytes;
          if (processSeiNalUnitPayload) {
            // Read and write the payload of the SEI NAL unit.
            nalBuffer.reset(sampleCurrentNalBytesRemaining);
            input.readFully(nalBuffer.data, 0, sampleCurrentNalBytesRemaining);
            output.sampleData(nalBuffer, sampleCurrentNalBytesRemaining);
            writtenBytes = sampleCurrentNalBytesRemaining;
            // Unescape and process the SEI NAL unit.
            int unescapedLength = NalUnitUtil.unescapeStream(nalBuffer.data, nalBuffer.limit());
            // If the format is H.265/HEVC the NAL unit header has two bytes so skip one more byte.
            nalBuffer.setPosition(MimeTypes.VIDEO_H265.equals(track.format.sampleMimeType) ? 1 : 0);
            nalBuffer.setLimit(unescapedLength);
            CeaUtil.consume(fragment.getSamplePresentationTime(sampleIndex) * 1000L, nalBuffer,
                cea608TrackOutputs);
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

    long sampleTimeUs = fragment.getSamplePresentationTime(sampleIndex) * 1000L;
    if (timestampAdjuster != null) {
      sampleTimeUs = timestampAdjuster.adjustSampleTimestamp(sampleTimeUs);
    }

    @C.BufferFlags int sampleFlags = fragment.sampleIsSyncFrameTable[sampleIndex]
        ? C.BUFFER_FLAG_KEY_FRAME : 0;

    // Encryption data.
    TrackOutput.CryptoData cryptoData = null;
    if (fragment.definesEncryptionData) {
      sampleFlags |= C.BUFFER_FLAG_ENCRYPTED;
      TrackEncryptionBox encryptionBox = fragment.trackEncryptionBox != null
          ? fragment.trackEncryptionBox
          : track.getSampleDescriptionEncryptionBox(fragment.header.sampleDescriptionIndex);
      cryptoData = encryptionBox.cryptoData;
    }

    output.sampleMetadata(sampleTimeUs, sampleFlags, sampleSize, 0, cryptoData);

    while (!pendingMetadataSampleInfos.isEmpty()) {
      MetadataSampleInfo sampleInfo = pendingMetadataSampleInfos.removeFirst();
      pendingMetadataSampleBytes -= sampleInfo.size;
      eventMessageTrackOutput.sampleMetadata(
          sampleTimeUs + sampleInfo.presentationTimeDeltaUs,
          C.BUFFER_FLAG_KEY_FRAME, sampleInfo.size, pendingMetadataSampleBytes, null);
    }

    currentTrackBundle.currentSampleIndex++;
    currentTrackBundle.currentSampleInTrackRun++;
    if (currentTrackBundle.currentSampleInTrackRun
        == fragment.trunLength[currentTrackBundle.currentTrackRunIndex]) {
      currentTrackBundle.currentTrackRunIndex++;
      currentTrackBundle.currentSampleInTrackRun = 0;
      currentTrackBundle = null;
    }
    parserState = STATE_READING_SAMPLE_START;
    return true;
  }

  /**
   * Returns the {@link TrackBundle} whose fragment run has the earliest file position out of those
   * yet to be consumed, or null if all have been consumed.
   */
  private static TrackBundle getNextFragmentRun(SparseArray<TrackBundle> trackBundles) {
    TrackBundle nextTrackBundle = null;
    long nextTrackRunOffset = Long.MAX_VALUE;

    int trackBundlesSize = trackBundles.size();
    for (int i = 0; i < trackBundlesSize; i++) {
      TrackBundle trackBundle = trackBundles.valueAt(i);
      if (trackBundle.currentTrackRunIndex == trackBundle.fragment.trunCount) {
        // This track fragment contains no more runs in the next mdat box.
      } else {
        long trunOffset = trackBundle.fragment.trunDataPosition[trackBundle.currentTrackRunIndex];
        if (trunOffset < nextTrackRunOffset) {
          nextTrackBundle = trackBundle;
          nextTrackRunOffset = trunOffset;
        }
      }
    }
    return nextTrackBundle;
  }

  /**
   * Appends the corresponding encryption data to the {@link TrackOutput} contained in the given
   * {@link TrackBundle}.
   *
   * @param trackBundle The {@link TrackBundle} that contains the {@link Track} for which the
   *     Sample encryption data must be output.
   * @return The number of written bytes.
   */
  private int appendSampleEncryptionData(TrackBundle trackBundle) {
    TrackFragment trackFragment = trackBundle.fragment;
    int sampleDescriptionIndex = trackFragment.header.sampleDescriptionIndex;
    TrackEncryptionBox encryptionBox = trackFragment.trackEncryptionBox != null
        ? trackFragment.trackEncryptionBox
        : trackBundle.track.getSampleDescriptionEncryptionBox(sampleDescriptionIndex);

    ParsableByteArray initializationVectorData;
    int vectorSize;
    if (encryptionBox.initializationVectorSize != 0) {
      initializationVectorData = trackFragment.sampleEncryptionData;
      vectorSize = encryptionBox.initializationVectorSize;
    } else {
      // The default initialization vector should be used.
      byte[] initVectorData = encryptionBox.defaultInitializationVector;
      defaultInitializationVector.reset(initVectorData, initVectorData.length);
      initializationVectorData = defaultInitializationVector;
      vectorSize = initVectorData.length;
    }

    boolean subsampleEncryption = trackFragment
        .sampleHasSubsampleEncryptionTable[trackBundle.currentSampleIndex];

    // Write the signal byte, containing the vector size and the subsample encryption flag.
    encryptionSignalByte.data[0] = (byte) (vectorSize | (subsampleEncryption ? 0x80 : 0));
    encryptionSignalByte.setPosition(0);
    TrackOutput output = trackBundle.output;
    output.sampleData(encryptionSignalByte, 1);
    // Write the vector.
    output.sampleData(initializationVectorData, vectorSize);
    // If we don't have subsample encryption data, we're done.
    if (!subsampleEncryption) {
      return 1 + vectorSize;
    }
    // Write the subsample encryption data.
    ParsableByteArray subsampleEncryptionData = trackFragment.sampleEncryptionData;
    int subsampleCount = subsampleEncryptionData.readUnsignedShort();
    subsampleEncryptionData.skipBytes(-2);
    int subsampleDataLength = 2 + 6 * subsampleCount;
    output.sampleData(subsampleEncryptionData, subsampleDataLength);
    return 1 + vectorSize + subsampleDataLength;
  }

  /** Returns DrmInitData from leaf atoms. */
  private static DrmInitData getDrmInitDataFromAtoms(List<Atom.LeafAtom> leafChildren) {
    ArrayList<SchemeData> schemeDatas = null;
    int leafChildrenSize = leafChildren.size();
    for (int i = 0; i < leafChildrenSize; i++) {
      LeafAtom child = leafChildren.get(i);
      if (child.type == Atom.TYPE_pssh) {
        if (schemeDatas == null) {
          schemeDatas = new ArrayList<>();
        }
        byte[] psshData = child.data.data;
        UUID uuid = PsshAtomUtil.parseUuid(psshData);
        if (uuid == null) {
          Log.w(TAG, "Skipped pssh atom (failed to extract uuid)");
        } else {
          schemeDatas.add(new SchemeData(uuid, null, MimeTypes.VIDEO_MP4, psshData));
        }
      }
    }
    return schemeDatas == null ? null : new DrmInitData(schemeDatas);
  }

  /** Returns whether the extractor should decode a leaf atom with type {@code atom}. */
  private static boolean shouldParseLeafAtom(int atom) {
    return atom == Atom.TYPE_hdlr || atom == Atom.TYPE_mdhd || atom == Atom.TYPE_mvhd
        || atom == Atom.TYPE_sidx || atom == Atom.TYPE_stsd || atom == Atom.TYPE_tfdt
        || atom == Atom.TYPE_tfhd || atom == Atom.TYPE_tkhd || atom == Atom.TYPE_trex
        || atom == Atom.TYPE_trun || atom == Atom.TYPE_pssh || atom == Atom.TYPE_saiz
        || atom == Atom.TYPE_saio || atom == Atom.TYPE_senc || atom == Atom.TYPE_uuid
        || atom == Atom.TYPE_sbgp || atom == Atom.TYPE_sgpd || atom == Atom.TYPE_elst
        || atom == Atom.TYPE_mehd || atom == Atom.TYPE_emsg;
  }

  /** Returns whether the extractor should decode a container atom with type {@code atom}. */
  private static boolean shouldParseContainerAtom(int atom) {
    return atom == Atom.TYPE_moov || atom == Atom.TYPE_trak || atom == Atom.TYPE_mdia
        || atom == Atom.TYPE_minf || atom == Atom.TYPE_stbl || atom == Atom.TYPE_moof
        || atom == Atom.TYPE_traf || atom == Atom.TYPE_mvex || atom == Atom.TYPE_edts;
  }

  /**
   * Holds data corresponding to a metadata sample.
   */
  private static final class MetadataSampleInfo {

    public final long presentationTimeDeltaUs;
    public final int size;

    public MetadataSampleInfo(long presentationTimeDeltaUs, int size) {
      this.presentationTimeDeltaUs = presentationTimeDeltaUs;
      this.size = size;
    }

  }

  /**
   * Holds data corresponding to a single track.
   */
  private static final class TrackBundle {

    public final TrackFragment fragment;
    public final TrackOutput output;

    public Track track;
    public DefaultSampleValues defaultSampleValues;
    public int currentSampleIndex;
    public int currentSampleInTrackRun;
    public int currentTrackRunIndex;

    public TrackBundle(TrackOutput output) {
      fragment = new TrackFragment();
      this.output = output;
    }

    public void init(Track track, DefaultSampleValues defaultSampleValues) {
      this.track = Assertions.checkNotNull(track);
      this.defaultSampleValues = Assertions.checkNotNull(defaultSampleValues);
      output.format(track.format);
      reset();
    }

    public void reset() {
      fragment.reset();
      currentSampleIndex = 0;
      currentTrackRunIndex = 0;
      currentSampleInTrackRun = 0;
    }

    public void updateDrmInitData(DrmInitData drmInitData) {
      TrackEncryptionBox encryptionBox =
          track.getSampleDescriptionEncryptionBox(fragment.header.sampleDescriptionIndex);
      String schemeType = encryptionBox != null ? encryptionBox.schemeType : null;
      output.format(track.format.copyWithDrmInitData(drmInitData.copyWithSchemeType(schemeType)));
    }

  }

}
