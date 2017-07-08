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

import android.util.Log;
import android.util.Pair;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.ParserException;
import org.telegram.messenger.exoplayer2.audio.Ac3Util;
import org.telegram.messenger.exoplayer2.drm.DrmInitData;
import org.telegram.messenger.exoplayer2.extractor.GaplessInfoHolder;
import org.telegram.messenger.exoplayer2.metadata.Metadata;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.CodecSpecificDataUtil;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import org.telegram.messenger.exoplayer2.util.ParsableByteArray;
import org.telegram.messenger.exoplayer2.util.Util;
import org.telegram.messenger.exoplayer2.video.AvcConfig;
import org.telegram.messenger.exoplayer2.video.HevcConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Utility methods for parsing MP4 format atom payloads according to ISO 14496-12.
 */
/* package */ final class AtomParsers {

  private static final String TAG = "AtomParsers";

  private static final int TYPE_vide = Util.getIntegerCodeForString("vide");
  private static final int TYPE_soun = Util.getIntegerCodeForString("soun");
  private static final int TYPE_text = Util.getIntegerCodeForString("text");
  private static final int TYPE_sbtl = Util.getIntegerCodeForString("sbtl");
  private static final int TYPE_subt = Util.getIntegerCodeForString("subt");
  private static final int TYPE_clcp = Util.getIntegerCodeForString("clcp");
  private static final int TYPE_cenc = Util.getIntegerCodeForString("cenc");
  private static final int TYPE_meta = Util.getIntegerCodeForString("meta");

  /**
   * Parses a trak atom (defined in 14496-12).
   *
   * @param trak Atom to decode.
   * @param mvhd Movie header atom, used to get the timescale.
   * @param duration The duration in units of the timescale declared in the mvhd atom, or
   *     {@link C#TIME_UNSET} if the duration should be parsed from the tkhd atom.
   * @param drmInitData {@link DrmInitData} to be included in the format.
   * @param isQuickTime True for QuickTime media. False otherwise.
   * @return A {@link Track} instance, or {@code null} if the track's type isn't supported.
   */
  public static Track parseTrak(Atom.ContainerAtom trak, Atom.LeafAtom mvhd, long duration,
      DrmInitData drmInitData, boolean isQuickTime) throws ParserException {
    Atom.ContainerAtom mdia = trak.getContainerAtomOfType(Atom.TYPE_mdia);
    int trackType = parseHdlr(mdia.getLeafAtomOfType(Atom.TYPE_hdlr).data);
    if (trackType == C.TRACK_TYPE_UNKNOWN) {
      return null;
    }

    TkhdData tkhdData = parseTkhd(trak.getLeafAtomOfType(Atom.TYPE_tkhd).data);
    if (duration == C.TIME_UNSET) {
      duration = tkhdData.duration;
    }
    long movieTimescale = parseMvhd(mvhd.data);
    long durationUs;
    if (duration == C.TIME_UNSET) {
      durationUs = C.TIME_UNSET;
    } else {
      durationUs = Util.scaleLargeTimestamp(duration, C.MICROS_PER_SECOND, movieTimescale);
    }
    Atom.ContainerAtom stbl = mdia.getContainerAtomOfType(Atom.TYPE_minf)
        .getContainerAtomOfType(Atom.TYPE_stbl);

    Pair<Long, String> mdhdData = parseMdhd(mdia.getLeafAtomOfType(Atom.TYPE_mdhd).data);
    StsdData stsdData = parseStsd(stbl.getLeafAtomOfType(Atom.TYPE_stsd).data, tkhdData.id,
        tkhdData.rotationDegrees, mdhdData.second, drmInitData, isQuickTime);
    Pair<long[], long[]> edtsData = parseEdts(trak.getContainerAtomOfType(Atom.TYPE_edts));
    return stsdData.format == null ? null
        : new Track(tkhdData.id, trackType, mdhdData.first, movieTimescale, durationUs,
            stsdData.format, stsdData.requiredSampleTransformation, stsdData.trackEncryptionBoxes,
            stsdData.nalUnitLengthFieldLength, edtsData.first, edtsData.second);
  }

  /**
   * Parses an stbl atom (defined in 14496-12).
   *
   * @param track Track to which this sample table corresponds.
   * @param stblAtom stbl (sample table) atom to decode.
   * @param gaplessInfoHolder Holder to populate with gapless playback information.
   * @return Sample table described by the stbl atom.
   * @throws ParserException If the resulting sample sequence does not contain a sync sample.
   */
  public static TrackSampleTable parseStbl(Track track, Atom.ContainerAtom stblAtom,
      GaplessInfoHolder gaplessInfoHolder) throws ParserException {
    SampleSizeBox sampleSizeBox;
    Atom.LeafAtom stszAtom = stblAtom.getLeafAtomOfType(Atom.TYPE_stsz);
    if (stszAtom != null) {
      sampleSizeBox = new StszSampleSizeBox(stszAtom);
    } else {
      Atom.LeafAtom stz2Atom = stblAtom.getLeafAtomOfType(Atom.TYPE_stz2);
      if (stz2Atom == null) {
        throw new ParserException("Track has no sample table size information");
      }
      sampleSizeBox = new Stz2SampleSizeBox(stz2Atom);
    }

    int sampleCount = sampleSizeBox.getSampleCount();
    if (sampleCount == 0) {
      return new TrackSampleTable(new long[0], new int[0], 0, new long[0], new int[0]);
    }

    // Entries are byte offsets of chunks.
    boolean chunkOffsetsAreLongs = false;
    Atom.LeafAtom chunkOffsetsAtom = stblAtom.getLeafAtomOfType(Atom.TYPE_stco);
    if (chunkOffsetsAtom == null) {
      chunkOffsetsAreLongs = true;
      chunkOffsetsAtom = stblAtom.getLeafAtomOfType(Atom.TYPE_co64);
    }
    ParsableByteArray chunkOffsets = chunkOffsetsAtom.data;
    // Entries are (chunk number, number of samples per chunk, sample description index).
    ParsableByteArray stsc = stblAtom.getLeafAtomOfType(Atom.TYPE_stsc).data;
    // Entries are (number of samples, timestamp delta between those samples).
    ParsableByteArray stts = stblAtom.getLeafAtomOfType(Atom.TYPE_stts).data;
    // Entries are the indices of samples that are synchronization samples.
    Atom.LeafAtom stssAtom = stblAtom.getLeafAtomOfType(Atom.TYPE_stss);
    ParsableByteArray stss = stssAtom != null ? stssAtom.data : null;
    // Entries are (number of samples, timestamp offset).
    Atom.LeafAtom cttsAtom = stblAtom.getLeafAtomOfType(Atom.TYPE_ctts);
    ParsableByteArray ctts = cttsAtom != null ? cttsAtom.data : null;

    // Prepare to read chunk information.
    ChunkIterator chunkIterator = new ChunkIterator(stsc, chunkOffsets, chunkOffsetsAreLongs);

    // Prepare to read sample timestamps.
    stts.setPosition(Atom.FULL_HEADER_SIZE);
    int remainingTimestampDeltaChanges = stts.readUnsignedIntToInt() - 1;
    int remainingSamplesAtTimestampDelta = stts.readUnsignedIntToInt();
    int timestampDeltaInTimeUnits = stts.readUnsignedIntToInt();

    // Prepare to read sample timestamp offsets, if ctts is present.
    int remainingSamplesAtTimestampOffset = 0;
    int remainingTimestampOffsetChanges = 0;
    int timestampOffset = 0;
    if (ctts != null) {
      ctts.setPosition(Atom.FULL_HEADER_SIZE);
      remainingTimestampOffsetChanges = ctts.readUnsignedIntToInt();
    }

    int nextSynchronizationSampleIndex = C.INDEX_UNSET;
    int remainingSynchronizationSamples = 0;
    if (stss != null) {
      stss.setPosition(Atom.FULL_HEADER_SIZE);
      remainingSynchronizationSamples = stss.readUnsignedIntToInt();
      if (remainingSynchronizationSamples > 0) {
        nextSynchronizationSampleIndex = stss.readUnsignedIntToInt() - 1;
      } else {
        // Ignore empty stss boxes, which causes all samples to be treated as sync samples.
        stss = null;
      }
    }

    // True if we can rechunk fixed-sample-size data. Note that we only rechunk raw audio.
    boolean isRechunkable = sampleSizeBox.isFixedSampleSize()
        && MimeTypes.AUDIO_RAW.equals(track.format.sampleMimeType)
        && remainingTimestampDeltaChanges == 0 && remainingTimestampOffsetChanges == 0
        && remainingSynchronizationSamples == 0;

    long[] offsets;
    int[] sizes;
    int maximumSize = 0;
    long[] timestamps;
    int[] flags;
    long timestampTimeUnits = 0;

    if (!isRechunkable) {
      offsets = new long[sampleCount];
      sizes = new int[sampleCount];
      timestamps = new long[sampleCount];
      flags = new int[sampleCount];
      long offset = 0;
      int remainingSamplesInChunk = 0;

      for (int i = 0; i < sampleCount; i++) {
        // Advance to the next chunk if necessary.
        while (remainingSamplesInChunk == 0) {
          Assertions.checkState(chunkIterator.moveNext());
          offset = chunkIterator.offset;
          remainingSamplesInChunk = chunkIterator.numSamples;
        }

        // Add on the timestamp offset if ctts is present.
        if (ctts != null) {
          while (remainingSamplesAtTimestampOffset == 0 && remainingTimestampOffsetChanges > 0) {
            remainingSamplesAtTimestampOffset = ctts.readUnsignedIntToInt();
            // The BMFF spec (ISO 14496-12) states that sample offsets should be unsigned integers
            // in version 0 ctts boxes, however some streams violate the spec and use signed
            // integers instead. It's safe to always decode sample offsets as signed integers here,
            // because unsigned integers will still be parsed correctly (unless their top bit is
            // set, which is never true in practice because sample offsets are always small).
            timestampOffset = ctts.readInt();
            remainingTimestampOffsetChanges--;
          }
          remainingSamplesAtTimestampOffset--;
        }

        offsets[i] = offset;
        sizes[i] = sampleSizeBox.readNextSampleSize();
        if (sizes[i] > maximumSize) {
          maximumSize = sizes[i];
        }
        timestamps[i] = timestampTimeUnits + timestampOffset;

        // All samples are synchronization samples if the stss is not present.
        flags[i] = stss == null ? C.BUFFER_FLAG_KEY_FRAME : 0;
        if (i == nextSynchronizationSampleIndex) {
          flags[i] = C.BUFFER_FLAG_KEY_FRAME;
          remainingSynchronizationSamples--;
          if (remainingSynchronizationSamples > 0) {
            nextSynchronizationSampleIndex = stss.readUnsignedIntToInt() - 1;
          }
        }

        // Add on the duration of this sample.
        timestampTimeUnits += timestampDeltaInTimeUnits;
        remainingSamplesAtTimestampDelta--;
        if (remainingSamplesAtTimestampDelta == 0 && remainingTimestampDeltaChanges > 0) {
          remainingSamplesAtTimestampDelta = stts.readUnsignedIntToInt();
          timestampDeltaInTimeUnits = stts.readUnsignedIntToInt();
          remainingTimestampDeltaChanges--;
        }

        offset += sizes[i];
        remainingSamplesInChunk--;
      }

      Assertions.checkArgument(remainingSamplesAtTimestampOffset == 0);
      // Remove trailing ctts entries with 0-valued sample counts.
      while (remainingTimestampOffsetChanges > 0) {
        Assertions.checkArgument(ctts.readUnsignedIntToInt() == 0);
        ctts.readInt(); // Ignore offset.
        remainingTimestampOffsetChanges--;
      }

      // If the stbl's child boxes are not consistent the container is malformed, but the stream may
      // still be playable.
      if (remainingSynchronizationSamples != 0 || remainingSamplesAtTimestampDelta != 0
          || remainingSamplesInChunk != 0 || remainingTimestampDeltaChanges != 0) {
        Log.w(TAG, "Inconsistent stbl box for track " + track.id
            + ": remainingSynchronizationSamples " + remainingSynchronizationSamples
            + ", remainingSamplesAtTimestampDelta " + remainingSamplesAtTimestampDelta
            + ", remainingSamplesInChunk " + remainingSamplesInChunk
            + ", remainingTimestampDeltaChanges " + remainingTimestampDeltaChanges);
      }
    } else {
      long[] chunkOffsetsBytes = new long[chunkIterator.length];
      int[] chunkSampleCounts = new int[chunkIterator.length];
      while (chunkIterator.moveNext()) {
        chunkOffsetsBytes[chunkIterator.index] = chunkIterator.offset;
        chunkSampleCounts[chunkIterator.index] = chunkIterator.numSamples;
      }
      int fixedSampleSize = sampleSizeBox.readNextSampleSize();
      FixedSampleSizeRechunker.Results rechunkedResults = FixedSampleSizeRechunker.rechunk(
          fixedSampleSize, chunkOffsetsBytes, chunkSampleCounts, timestampDeltaInTimeUnits);
      offsets = rechunkedResults.offsets;
      sizes = rechunkedResults.sizes;
      maximumSize = rechunkedResults.maximumSize;
      timestamps = rechunkedResults.timestamps;
      flags = rechunkedResults.flags;
    }

    if (track.editListDurations == null || gaplessInfoHolder.hasGaplessInfo()) {
      // There is no edit list, or we are ignoring it as we already have gapless metadata to apply.
      // This implementation does not support applying both gapless metadata and an edit list.
      Util.scaleLargeTimestampsInPlace(timestamps, C.MICROS_PER_SECOND, track.timescale);
      return new TrackSampleTable(offsets, sizes, maximumSize, timestamps, flags);
    }

    // See the BMFF spec (ISO 14496-12) subsection 8.6.6. Edit lists that require prerolling from a
    // sync sample after reordering are not supported. Partial audio sample truncation is only
    // supported in edit lists with one edit that removes less than one sample from the start/end of
    // the track, for gapless audio playback. This implementation handles simple discarding/delaying
    // of samples. The extractor may place further restrictions on what edited streams are playable.

    if (track.editListDurations.length == 1 && track.type == C.TRACK_TYPE_AUDIO
        && timestamps.length >= 2) {
      // Handle the edit by setting gapless playback metadata, if possible. This implementation
      // assumes that only one "roll" sample is needed, which is the case for AAC, so the start/end
      // points of the edit must lie within the first/last samples respectively.
      long editStartTime = track.editListMediaTimes[0];
      long editEndTime = editStartTime + Util.scaleLargeTimestamp(track.editListDurations[0],
          track.timescale, track.movieTimescale);
      long lastSampleEndTime = timestampTimeUnits;
      if (timestamps[0] <= editStartTime && editStartTime < timestamps[1]
          && timestamps[timestamps.length - 1] < editEndTime && editEndTime <= lastSampleEndTime) {
        long paddingTimeUnits = lastSampleEndTime - editEndTime;
        long encoderDelay = Util.scaleLargeTimestamp(editStartTime - timestamps[0],
            track.format.sampleRate, track.timescale);
        long encoderPadding = Util.scaleLargeTimestamp(paddingTimeUnits,
            track.format.sampleRate, track.timescale);
        if ((encoderDelay != 0 || encoderPadding != 0) && encoderDelay <= Integer.MAX_VALUE
            && encoderPadding <= Integer.MAX_VALUE) {
          gaplessInfoHolder.encoderDelay = (int) encoderDelay;
          gaplessInfoHolder.encoderPadding = (int) encoderPadding;
          Util.scaleLargeTimestampsInPlace(timestamps, C.MICROS_PER_SECOND, track.timescale);
          return new TrackSampleTable(offsets, sizes, maximumSize, timestamps, flags);
        }
      }
    }

    if (track.editListDurations.length == 1 && track.editListDurations[0] == 0) {
      // The current version of the spec leaves handling of an edit with zero segment_duration in
      // unfragmented files open to interpretation. We handle this as a special case and include all
      // samples in the edit.
      for (int i = 0; i < timestamps.length; i++) {
        timestamps[i] = Util.scaleLargeTimestamp(timestamps[i] - track.editListMediaTimes[0],
            C.MICROS_PER_SECOND, track.timescale);
      }
      return new TrackSampleTable(offsets, sizes, maximumSize, timestamps, flags);
    }

    // Omit any sample at the end point of an edit for audio tracks.
    boolean omitClippedSample = track.type == C.TRACK_TYPE_AUDIO;

    // Count the number of samples after applying edits.
    int editedSampleCount = 0;
    int nextSampleIndex = 0;
    boolean copyMetadata = false;
    for (int i = 0; i < track.editListDurations.length; i++) {
      long mediaTime = track.editListMediaTimes[i];
      if (mediaTime != -1) {
        long duration = Util.scaleLargeTimestamp(track.editListDurations[i], track.timescale,
            track.movieTimescale);
        int startIndex = Util.binarySearchCeil(timestamps, mediaTime, true, true);
        int endIndex = Util.binarySearchCeil(timestamps, mediaTime + duration, omitClippedSample,
            false);
        editedSampleCount += endIndex - startIndex;
        copyMetadata |= nextSampleIndex != startIndex;
        nextSampleIndex = endIndex;
      }
    }
    copyMetadata |= editedSampleCount != sampleCount;

    // Calculate edited sample timestamps and update the corresponding metadata arrays.
    long[] editedOffsets = copyMetadata ? new long[editedSampleCount] : offsets;
    int[] editedSizes = copyMetadata ? new int[editedSampleCount] : sizes;
    int editedMaximumSize = copyMetadata ? 0 : maximumSize;
    int[] editedFlags = copyMetadata ? new int[editedSampleCount] : flags;
    long[] editedTimestamps = new long[editedSampleCount];
    long pts = 0;
    int sampleIndex = 0;
    for (int i = 0; i < track.editListDurations.length; i++) {
      long mediaTime = track.editListMediaTimes[i];
      long duration = track.editListDurations[i];
      if (mediaTime != -1) {
        long endMediaTime = mediaTime + Util.scaleLargeTimestamp(duration, track.timescale,
            track.movieTimescale);
        int startIndex = Util.binarySearchCeil(timestamps, mediaTime, true, true);
        int endIndex = Util.binarySearchCeil(timestamps, endMediaTime, omitClippedSample, false);
        if (copyMetadata) {
          int count = endIndex - startIndex;
          System.arraycopy(offsets, startIndex, editedOffsets, sampleIndex, count);
          System.arraycopy(sizes, startIndex, editedSizes, sampleIndex, count);
          System.arraycopy(flags, startIndex, editedFlags, sampleIndex, count);
        }
        for (int j = startIndex; j < endIndex; j++) {
          long ptsUs = Util.scaleLargeTimestamp(pts, C.MICROS_PER_SECOND, track.movieTimescale);
          long timeInSegmentUs = Util.scaleLargeTimestamp(timestamps[j] - mediaTime,
              C.MICROS_PER_SECOND, track.timescale);
          editedTimestamps[sampleIndex] = ptsUs + timeInSegmentUs;
          if (copyMetadata && editedSizes[sampleIndex] > editedMaximumSize) {
            editedMaximumSize = sizes[j];
          }
          sampleIndex++;
        }
      }
      pts += duration;
    }

    boolean hasSyncSample = false;
    for (int i = 0; i < editedFlags.length && !hasSyncSample; i++) {
      hasSyncSample |= (editedFlags[i] & C.BUFFER_FLAG_KEY_FRAME) != 0;
    }
    if (!hasSyncSample) {
      throw new ParserException("The edited sample sequence does not contain a sync sample.");
    }

    return new TrackSampleTable(editedOffsets, editedSizes, editedMaximumSize, editedTimestamps,
        editedFlags);
  }

  /**
   * Parses a udta atom.
   *
   * @param udtaAtom The udta (user data) atom to decode.
   * @param isQuickTime True for QuickTime media. False otherwise.
   * @return Parsed metadata, or null.
   */
  public static Metadata parseUdta(Atom.LeafAtom udtaAtom, boolean isQuickTime) {
    if (isQuickTime) {
      // Meta boxes are regular boxes rather than full boxes in QuickTime. For now, don't try and
      // decode one.
      return null;
    }
    ParsableByteArray udtaData = udtaAtom.data;
    udtaData.setPosition(Atom.HEADER_SIZE);
    while (udtaData.bytesLeft() >= Atom.HEADER_SIZE) {
      int atomPosition = udtaData.getPosition();
      int atomSize = udtaData.readInt();
      int atomType = udtaData.readInt();
      if (atomType == Atom.TYPE_meta) {
        udtaData.setPosition(atomPosition);
        return parseMetaAtom(udtaData, atomPosition + atomSize);
      }
      udtaData.skipBytes(atomSize - Atom.HEADER_SIZE);
    }
    return null;
  }

  private static Metadata parseMetaAtom(ParsableByteArray meta, int limit) {
    meta.skipBytes(Atom.FULL_HEADER_SIZE);
    while (meta.getPosition() < limit) {
      int atomPosition = meta.getPosition();
      int atomSize = meta.readInt();
      int atomType = meta.readInt();
      if (atomType == Atom.TYPE_ilst) {
        meta.setPosition(atomPosition);
        return parseIlst(meta, atomPosition + atomSize);
      }
      meta.skipBytes(atomSize - Atom.HEADER_SIZE);
    }
    return null;
  }

  private static Metadata parseIlst(ParsableByteArray ilst, int limit) {
    ilst.skipBytes(Atom.HEADER_SIZE);
    ArrayList<Metadata.Entry> entries = new ArrayList<>();
    while (ilst.getPosition() < limit) {
      Metadata.Entry entry = MetadataUtil.parseIlstElement(ilst);
      if (entry != null) {
        entries.add(entry);
      }
    }
    return entries.isEmpty() ? null : new Metadata(entries);
  }

  /**
   * Parses a mvhd atom (defined in 14496-12), returning the timescale for the movie.
   *
   * @param mvhd Contents of the mvhd atom to be parsed.
   * @return Timescale for the movie.
   */
  private static long parseMvhd(ParsableByteArray mvhd) {
    mvhd.setPosition(Atom.HEADER_SIZE);
    int fullAtom = mvhd.readInt();
    int version = Atom.parseFullAtomVersion(fullAtom);
    mvhd.skipBytes(version == 0 ? 8 : 16);
    return mvhd.readUnsignedInt();
  }

  /**
   * Parses a tkhd atom (defined in 14496-12).
   *
   * @return An object containing the parsed data.
   */
  private static TkhdData parseTkhd(ParsableByteArray tkhd) {
    tkhd.setPosition(Atom.HEADER_SIZE);
    int fullAtom = tkhd.readInt();
    int version = Atom.parseFullAtomVersion(fullAtom);

    tkhd.skipBytes(version == 0 ? 8 : 16);
    int trackId = tkhd.readInt();

    tkhd.skipBytes(4);
    boolean durationUnknown = true;
    int durationPosition = tkhd.getPosition();
    int durationByteCount = version == 0 ? 4 : 8;
    for (int i = 0; i < durationByteCount; i++) {
      if (tkhd.data[durationPosition + i] != -1) {
        durationUnknown = false;
        break;
      }
    }
    long duration;
    if (durationUnknown) {
      tkhd.skipBytes(durationByteCount);
      duration = C.TIME_UNSET;
    } else {
      duration = version == 0 ? tkhd.readUnsignedInt() : tkhd.readUnsignedLongToLong();
      if (duration == 0) {
        // 0 duration normally indicates that the file is fully fragmented (i.e. all of the media
        // samples are in fragments). Treat as unknown.
        duration = C.TIME_UNSET;
      }
    }

    tkhd.skipBytes(16);
    int a00 = tkhd.readInt();
    int a01 = tkhd.readInt();
    tkhd.skipBytes(4);
    int a10 = tkhd.readInt();
    int a11 = tkhd.readInt();

    int rotationDegrees;
    int fixedOne = 65536;
    if (a00 == 0 && a01 == fixedOne && a10 == -fixedOne && a11 == 0) {
      rotationDegrees = 90;
    } else if (a00 == 0 && a01 == -fixedOne && a10 == fixedOne && a11 == 0) {
      rotationDegrees = 270;
    } else if (a00 == -fixedOne && a01 == 0 && a10 == 0 && a11 == -fixedOne) {
      rotationDegrees = 180;
    } else {
      // Only 0, 90, 180 and 270 are supported. Treat anything else as 0.
      rotationDegrees = 0;
    }

    return new TkhdData(trackId, duration, rotationDegrees);
  }

  /**
   * Parses an hdlr atom.
   *
   * @param hdlr The hdlr atom to decode.
   * @return The track type.
   */
  private static int parseHdlr(ParsableByteArray hdlr) {
    hdlr.setPosition(Atom.FULL_HEADER_SIZE + 4);
    int trackType = hdlr.readInt();
    if (trackType == TYPE_soun) {
      return C.TRACK_TYPE_AUDIO;
    } else if (trackType == TYPE_vide) {
      return C.TRACK_TYPE_VIDEO;
    } else if (trackType == TYPE_text || trackType == TYPE_sbtl || trackType == TYPE_subt
        || trackType == TYPE_clcp) {
      return C.TRACK_TYPE_TEXT;
    } else if (trackType == TYPE_meta) {
      return C.TRACK_TYPE_METADATA;
    } else {
      return C.TRACK_TYPE_UNKNOWN;
    }
  }

  /**
   * Parses an mdhd atom (defined in 14496-12).
   *
   * @param mdhd The mdhd atom to decode.
   * @return A pair consisting of the media timescale defined as the number of time units that pass
   * in one second, and the language code.
   */
  private static Pair<Long, String> parseMdhd(ParsableByteArray mdhd) {
    mdhd.setPosition(Atom.HEADER_SIZE);
    int fullAtom = mdhd.readInt();
    int version = Atom.parseFullAtomVersion(fullAtom);
    mdhd.skipBytes(version == 0 ? 8 : 16);
    long timescale = mdhd.readUnsignedInt();
    mdhd.skipBytes(version == 0 ? 4 : 8);
    int languageCode = mdhd.readUnsignedShort();
    String language = "" + (char) (((languageCode >> 10) & 0x1F) + 0x60)
        + (char) (((languageCode >> 5) & 0x1F) + 0x60)
        + (char) (((languageCode) & 0x1F) + 0x60);
    return Pair.create(timescale, language);
  }

  /**
   * Parses a stsd atom (defined in 14496-12).
   *
   * @param stsd The stsd atom to decode.
   * @param trackId The track's identifier in its container.
   * @param rotationDegrees The rotation of the track in degrees.
   * @param language The language of the track.
   * @param drmInitData {@link DrmInitData} to be included in the format.
   * @param isQuickTime True for QuickTime media. False otherwise.
   * @return An object containing the parsed data.
   */
  private static StsdData parseStsd(ParsableByteArray stsd, int trackId, int rotationDegrees,
      String language, DrmInitData drmInitData, boolean isQuickTime) throws ParserException {
    stsd.setPosition(Atom.FULL_HEADER_SIZE);
    int numberOfEntries = stsd.readInt();
    StsdData out = new StsdData(numberOfEntries);
    for (int i = 0; i < numberOfEntries; i++) {
      int childStartPosition = stsd.getPosition();
      int childAtomSize = stsd.readInt();
      Assertions.checkArgument(childAtomSize > 0, "childAtomSize should be positive");
      int childAtomType = stsd.readInt();
      if (childAtomType == Atom.TYPE_avc1 || childAtomType == Atom.TYPE_avc3
          || childAtomType == Atom.TYPE_encv || childAtomType == Atom.TYPE_mp4v
          || childAtomType == Atom.TYPE_hvc1 || childAtomType == Atom.TYPE_hev1
          || childAtomType == Atom.TYPE_s263 || childAtomType == Atom.TYPE_vp08
          || childAtomType == Atom.TYPE_vp09) {
        parseVideoSampleEntry(stsd, childAtomType, childStartPosition, childAtomSize, trackId,
            rotationDegrees, drmInitData, out, i);
      } else if (childAtomType == Atom.TYPE_mp4a || childAtomType == Atom.TYPE_enca
          || childAtomType == Atom.TYPE_ac_3 || childAtomType == Atom.TYPE_ec_3
          || childAtomType == Atom.TYPE_dtsc || childAtomType == Atom.TYPE_dtse
          || childAtomType == Atom.TYPE_dtsh || childAtomType == Atom.TYPE_dtsl
          || childAtomType == Atom.TYPE_samr || childAtomType == Atom.TYPE_sawb
          || childAtomType == Atom.TYPE_lpcm || childAtomType == Atom.TYPE_sowt
          || childAtomType == Atom.TYPE__mp3 || childAtomType == Atom.TYPE_alac) {
        parseAudioSampleEntry(stsd, childAtomType, childStartPosition, childAtomSize, trackId,
            language, isQuickTime, drmInitData, out, i);
      } else if (childAtomType == Atom.TYPE_TTML || childAtomType == Atom.TYPE_tx3g
          || childAtomType == Atom.TYPE_wvtt || childAtomType == Atom.TYPE_stpp
          || childAtomType == Atom.TYPE_c608) {
        parseTextSampleEntry(stsd, childAtomType, childStartPosition, childAtomSize, trackId,
            language, drmInitData, out);
      } else if (childAtomType == Atom.TYPE_camm) {
        out.format = Format.createSampleFormat(Integer.toString(trackId),
            MimeTypes.APPLICATION_CAMERA_MOTION, null, Format.NO_VALUE, drmInitData);
      }
      stsd.setPosition(childStartPosition + childAtomSize);
    }
    return out;
  }

  private static void parseTextSampleEntry(ParsableByteArray parent, int atomType, int position,
      int atomSize, int trackId, String language, DrmInitData drmInitData, StsdData out)
      throws ParserException {
    parent.setPosition(position + Atom.HEADER_SIZE + StsdData.STSD_HEADER_SIZE);

    // Default values.
    List<byte[]> initializationData = null;
    long subsampleOffsetUs = Format.OFFSET_SAMPLE_RELATIVE;

    String mimeType;
    if (atomType == Atom.TYPE_TTML) {
      mimeType = MimeTypes.APPLICATION_TTML;
    } else if (atomType == Atom.TYPE_tx3g) {
      mimeType = MimeTypes.APPLICATION_TX3G;
      int sampleDescriptionLength = atomSize - Atom.HEADER_SIZE - 8;
      byte[] sampleDescriptionData = new byte[sampleDescriptionLength];
      parent.readBytes(sampleDescriptionData, 0, sampleDescriptionLength);
      initializationData = Collections.singletonList(sampleDescriptionData);
    } else if (atomType == Atom.TYPE_wvtt) {
      mimeType = MimeTypes.APPLICATION_MP4VTT;
    } else if (atomType == Atom.TYPE_stpp) {
      mimeType = MimeTypes.APPLICATION_TTML;
      subsampleOffsetUs = 0; // Subsample timing is absolute.
    } else if (atomType == Atom.TYPE_c608) {
      // Defined by the QuickTime File Format specification.
      mimeType = MimeTypes.APPLICATION_MP4CEA608;
      out.requiredSampleTransformation = Track.TRANSFORMATION_CEA608_CDAT;
    } else {
      // Never happens.
      throw new IllegalStateException();
    }

    out.format = Format.createTextSampleFormat(Integer.toString(trackId), mimeType, null,
        Format.NO_VALUE, 0, language, Format.NO_VALUE, drmInitData, subsampleOffsetUs,
        initializationData);
  }

  private static void parseVideoSampleEntry(ParsableByteArray parent, int atomType, int position,
      int size, int trackId, int rotationDegrees, DrmInitData drmInitData, StsdData out,
      int entryIndex) throws ParserException {
    parent.setPosition(position + Atom.HEADER_SIZE + StsdData.STSD_HEADER_SIZE);

    parent.skipBytes(16);
    int width = parent.readUnsignedShort();
    int height = parent.readUnsignedShort();
    boolean pixelWidthHeightRatioFromPasp = false;
    float pixelWidthHeightRatio = 1;
    parent.skipBytes(50);

    int childPosition = parent.getPosition();
    if (atomType == Atom.TYPE_encv) {
      atomType = parseSampleEntryEncryptionData(parent, position, size, out, entryIndex);
      parent.setPosition(childPosition);
    }

    List<byte[]> initializationData = null;
    String mimeType = null;
    byte[] projectionData = null;
    @C.StereoMode
    int stereoMode = Format.NO_VALUE;
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childStartPosition = parent.getPosition();
      int childAtomSize = parent.readInt();
      if (childAtomSize == 0 && parent.getPosition() - position == size) {
        // Handle optional terminating four zero bytes in MOV files.
        break;
      }
      Assertions.checkArgument(childAtomSize > 0, "childAtomSize should be positive");
      int childAtomType = parent.readInt();
      if (childAtomType == Atom.TYPE_avcC) {
        Assertions.checkState(mimeType == null);
        mimeType = MimeTypes.VIDEO_H264;
        parent.setPosition(childStartPosition + Atom.HEADER_SIZE);
        AvcConfig avcConfig = AvcConfig.parse(parent);
        initializationData = avcConfig.initializationData;
        out.nalUnitLengthFieldLength = avcConfig.nalUnitLengthFieldLength;
        if (!pixelWidthHeightRatioFromPasp) {
          pixelWidthHeightRatio = avcConfig.pixelWidthAspectRatio;
        }
      } else if (childAtomType == Atom.TYPE_hvcC) {
        Assertions.checkState(mimeType == null);
        mimeType = MimeTypes.VIDEO_H265;
        parent.setPosition(childStartPosition + Atom.HEADER_SIZE);
        HevcConfig hevcConfig = HevcConfig.parse(parent);
        initializationData = hevcConfig.initializationData;
        out.nalUnitLengthFieldLength = hevcConfig.nalUnitLengthFieldLength;
      } else if (childAtomType == Atom.TYPE_vpcC) {
        Assertions.checkState(mimeType == null);
        mimeType = (atomType == Atom.TYPE_vp08) ? MimeTypes.VIDEO_VP8 : MimeTypes.VIDEO_VP9;
      } else if (childAtomType == Atom.TYPE_d263) {
        Assertions.checkState(mimeType == null);
        mimeType = MimeTypes.VIDEO_H263;
      } else if (childAtomType == Atom.TYPE_esds) {
        Assertions.checkState(mimeType == null);
        Pair<String, byte[]> mimeTypeAndInitializationData =
            parseEsdsFromParent(parent, childStartPosition);
        mimeType = mimeTypeAndInitializationData.first;
        initializationData = Collections.singletonList(mimeTypeAndInitializationData.second);
      } else if (childAtomType == Atom.TYPE_pasp) {
        pixelWidthHeightRatio = parsePaspFromParent(parent, childStartPosition);
        pixelWidthHeightRatioFromPasp = true;
      } else if (childAtomType == Atom.TYPE_sv3d) {
        projectionData = parseProjFromParent(parent, childStartPosition, childAtomSize);
      } else if (childAtomType == Atom.TYPE_st3d) {
        int version = parent.readUnsignedByte();
        parent.skipBytes(3); // Flags.
        if (version == 0) {
          int layout = parent.readUnsignedByte();
          switch (layout) {
            case 0:
              stereoMode = C.STEREO_MODE_MONO;
              break;
            case 1:
              stereoMode = C.STEREO_MODE_TOP_BOTTOM;
              break;
            case 2:
              stereoMode = C.STEREO_MODE_LEFT_RIGHT;
              break;
            case 3:
              stereoMode = C.STEREO_MODE_STEREO_MESH;
              break;
            default:
              break;
          }
        }
      }
      childPosition += childAtomSize;
    }

    // If the media type was not recognized, ignore the track.
    if (mimeType == null) {
      return;
    }

    out.format = Format.createVideoSampleFormat(Integer.toString(trackId), mimeType, null,
        Format.NO_VALUE, Format.NO_VALUE, width, height, Format.NO_VALUE, initializationData,
        rotationDegrees, pixelWidthHeightRatio, projectionData, stereoMode, null, drmInitData);
  }

  /**
   * Parses the edts atom (defined in 14496-12 subsection 8.6.5).
   *
   * @param edtsAtom edts (edit box) atom to decode.
   * @return Pair of edit list durations and edit list media times, or a pair of nulls if they are
   * not present.
   */
  private static Pair<long[], long[]> parseEdts(Atom.ContainerAtom edtsAtom) {
    Atom.LeafAtom elst;
    if (edtsAtom == null || (elst = edtsAtom.getLeafAtomOfType(Atom.TYPE_elst)) == null) {
      return Pair.create(null, null);
    }
    ParsableByteArray elstData = elst.data;
    elstData.setPosition(Atom.HEADER_SIZE);
    int fullAtom = elstData.readInt();
    int version = Atom.parseFullAtomVersion(fullAtom);
    int entryCount = elstData.readUnsignedIntToInt();
    long[] editListDurations = new long[entryCount];
    long[] editListMediaTimes = new long[entryCount];
    for (int i = 0; i < entryCount; i++) {
      editListDurations[i] =
          version == 1 ? elstData.readUnsignedLongToLong() : elstData.readUnsignedInt();
      editListMediaTimes[i] = version == 1 ? elstData.readLong() : elstData.readInt();
      int mediaRateInteger = elstData.readShort();
      if (mediaRateInteger != 1) {
        // The extractor does not handle dwell edits (mediaRateInteger == 0).
        throw new IllegalArgumentException("Unsupported media rate.");
      }
      elstData.skipBytes(2);
    }
    return Pair.create(editListDurations, editListMediaTimes);
  }

  private static float parsePaspFromParent(ParsableByteArray parent, int position) {
    parent.setPosition(position + Atom.HEADER_SIZE);
    int hSpacing = parent.readUnsignedIntToInt();
    int vSpacing = parent.readUnsignedIntToInt();
    return (float) hSpacing / vSpacing;
  }

  private static void parseAudioSampleEntry(ParsableByteArray parent, int atomType, int position,
      int size, int trackId, String language, boolean isQuickTime, DrmInitData drmInitData,
      StsdData out, int entryIndex) {
    parent.setPosition(position + Atom.HEADER_SIZE + StsdData.STSD_HEADER_SIZE);

    int quickTimeSoundDescriptionVersion = 0;
    if (isQuickTime) {
      quickTimeSoundDescriptionVersion = parent.readUnsignedShort();
      parent.skipBytes(6);
    } else {
      parent.skipBytes(8);
    }

    int channelCount;
    int sampleRate;

    if (quickTimeSoundDescriptionVersion == 0 || quickTimeSoundDescriptionVersion == 1) {
      channelCount = parent.readUnsignedShort();
      parent.skipBytes(6);  // sampleSize, compressionId, packetSize.
      sampleRate = parent.readUnsignedFixedPoint1616();

      if (quickTimeSoundDescriptionVersion == 1) {
        parent.skipBytes(16);
      }
    } else if (quickTimeSoundDescriptionVersion == 2) {
      parent.skipBytes(16);  // always[3,16,Minus2,0,65536], sizeOfStructOnly

      sampleRate = (int) Math.round(parent.readDouble());
      channelCount = parent.readUnsignedIntToInt();

      // Skip always7F000000, sampleSize, formatSpecificFlags, constBytesPerAudioPacket,
      // constLPCMFramesPerAudioPacket.
      parent.skipBytes(20);
    } else {
      // Unsupported version.
      return;
    }

    int childPosition = parent.getPosition();
    if (atomType == Atom.TYPE_enca) {
      atomType = parseSampleEntryEncryptionData(parent, position, size, out, entryIndex);
      parent.setPosition(childPosition);
    }

    // If the atom type determines a MIME type, set it immediately.
    String mimeType = null;
    if (atomType == Atom.TYPE_ac_3) {
      mimeType = MimeTypes.AUDIO_AC3;
    } else if (atomType == Atom.TYPE_ec_3) {
      mimeType = MimeTypes.AUDIO_E_AC3;
    } else if (atomType == Atom.TYPE_dtsc) {
      mimeType = MimeTypes.AUDIO_DTS;
    } else if (atomType == Atom.TYPE_dtsh || atomType == Atom.TYPE_dtsl) {
      mimeType = MimeTypes.AUDIO_DTS_HD;
    } else if (atomType == Atom.TYPE_dtse) {
      mimeType = MimeTypes.AUDIO_DTS_EXPRESS;
    } else if (atomType == Atom.TYPE_samr) {
      mimeType = MimeTypes.AUDIO_AMR_NB;
    } else if (atomType == Atom.TYPE_sawb) {
      mimeType = MimeTypes.AUDIO_AMR_WB;
    } else if (atomType == Atom.TYPE_lpcm || atomType == Atom.TYPE_sowt) {
      mimeType = MimeTypes.AUDIO_RAW;
    } else if (atomType == Atom.TYPE__mp3) {
      mimeType = MimeTypes.AUDIO_MPEG;
    } else if (atomType == Atom.TYPE_alac) {
      mimeType = MimeTypes.AUDIO_ALAC;
    }

    byte[] initializationData = null;
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childAtomSize = parent.readInt();
      Assertions.checkArgument(childAtomSize > 0, "childAtomSize should be positive");
      int childAtomType = parent.readInt();
      if (childAtomType == Atom.TYPE_esds || (isQuickTime && childAtomType == Atom.TYPE_wave)) {
        int esdsAtomPosition = childAtomType == Atom.TYPE_esds ? childPosition
            : findEsdsPosition(parent, childPosition, childAtomSize);
        if (esdsAtomPosition != C.POSITION_UNSET) {
          Pair<String, byte[]> mimeTypeAndInitializationData =
              parseEsdsFromParent(parent, esdsAtomPosition);
          mimeType = mimeTypeAndInitializationData.first;
          initializationData = mimeTypeAndInitializationData.second;
          if (MimeTypes.AUDIO_AAC.equals(mimeType)) {
            // TODO: Do we really need to do this? See [Internal: b/10903778]
            // Update sampleRate and channelCount from the AudioSpecificConfig initialization data.
            Pair<Integer, Integer> audioSpecificConfig =
                CodecSpecificDataUtil.parseAacAudioSpecificConfig(initializationData);
            sampleRate = audioSpecificConfig.first;
            channelCount = audioSpecificConfig.second;
          }
        }
      } else if (childAtomType == Atom.TYPE_dac3) {
        parent.setPosition(Atom.HEADER_SIZE + childPosition);
        out.format = Ac3Util.parseAc3AnnexFFormat(parent, Integer.toString(trackId), language,
            drmInitData);
      } else if (childAtomType == Atom.TYPE_dec3) {
        parent.setPosition(Atom.HEADER_SIZE + childPosition);
        out.format = Ac3Util.parseEAc3AnnexFFormat(parent, Integer.toString(trackId), language,
            drmInitData);
      } else if (childAtomType == Atom.TYPE_ddts) {
        out.format = Format.createAudioSampleFormat(Integer.toString(trackId), mimeType, null,
            Format.NO_VALUE, Format.NO_VALUE, channelCount, sampleRate, null, drmInitData, 0,
            language);
      } else if (childAtomType == Atom.TYPE_alac) {
        initializationData = new byte[childAtomSize];
        parent.setPosition(childPosition);
        parent.readBytes(initializationData, 0, childAtomSize);
      }
      childPosition += childAtomSize;
    }

    if (out.format == null && mimeType != null) {
      // TODO: Determine the correct PCM encoding.
      @C.PcmEncoding int pcmEncoding =
          MimeTypes.AUDIO_RAW.equals(mimeType) ? C.ENCODING_PCM_16BIT : Format.NO_VALUE;
      out.format = Format.createAudioSampleFormat(Integer.toString(trackId), mimeType, null,
          Format.NO_VALUE, Format.NO_VALUE, channelCount, sampleRate, pcmEncoding,
          initializationData == null ? null : Collections.singletonList(initializationData),
          drmInitData, 0, language);
    }
  }

  /**
   * Returns the position of the esds box within a parent, or {@link C#POSITION_UNSET} if no esds
   * box is found
   */
  private static int findEsdsPosition(ParsableByteArray parent, int position, int size) {
    int childAtomPosition = parent.getPosition();
    while (childAtomPosition - position < size) {
      parent.setPosition(childAtomPosition);
      int childAtomSize = parent.readInt();
      Assertions.checkArgument(childAtomSize > 0, "childAtomSize should be positive");
      int childType = parent.readInt();
      if (childType == Atom.TYPE_esds) {
        return childAtomPosition;
      }
      childAtomPosition += childAtomSize;
    }
    return C.POSITION_UNSET;
  }

  /**
   * Returns codec-specific initialization data contained in an esds box.
   */
  private static Pair<String, byte[]> parseEsdsFromParent(ParsableByteArray parent, int position) {
    parent.setPosition(position + Atom.HEADER_SIZE + 4);
    // Start of the ES_Descriptor (defined in 14496-1)
    parent.skipBytes(1); // ES_Descriptor tag
    parseExpandableClassSize(parent);
    parent.skipBytes(2); // ES_ID

    int flags = parent.readUnsignedByte();
    if ((flags & 0x80 /* streamDependenceFlag */) != 0) {
      parent.skipBytes(2);
    }
    if ((flags & 0x40 /* URL_Flag */) != 0) {
      parent.skipBytes(parent.readUnsignedShort());
    }
    if ((flags & 0x20 /* OCRstreamFlag */) != 0) {
      parent.skipBytes(2);
    }

    // Start of the DecoderConfigDescriptor (defined in 14496-1)
    parent.skipBytes(1); // DecoderConfigDescriptor tag
    parseExpandableClassSize(parent);

    // Set the MIME type based on the object type indication (14496-1 table 5).
    int objectTypeIndication = parent.readUnsignedByte();
    String mimeType;
    switch (objectTypeIndication) {
      case 0x6B:
        mimeType = MimeTypes.AUDIO_MPEG;
        return Pair.create(mimeType, null);
      case 0x20:
        mimeType = MimeTypes.VIDEO_MP4V;
        break;
      case 0x21:
        mimeType = MimeTypes.VIDEO_H264;
        break;
      case 0x23:
        mimeType = MimeTypes.VIDEO_H265;
        break;
      case 0x40:
      case 0x66:
      case 0x67:
      case 0x68:
        mimeType = MimeTypes.AUDIO_AAC;
        break;
      case 0xA5:
        mimeType = MimeTypes.AUDIO_AC3;
        break;
      case 0xA6:
        mimeType = MimeTypes.AUDIO_E_AC3;
        break;
      case 0xA9:
      case 0xAC:
        mimeType = MimeTypes.AUDIO_DTS;
        return Pair.create(mimeType, null);
      case 0xAA:
      case 0xAB:
        mimeType = MimeTypes.AUDIO_DTS_HD;
        return Pair.create(mimeType, null);
      default:
        mimeType = null;
        break;
    }

    parent.skipBytes(12);

    // Start of the AudioSpecificConfig.
    parent.skipBytes(1); // AudioSpecificConfig tag
    int initializationDataSize = parseExpandableClassSize(parent);
    byte[] initializationData = new byte[initializationDataSize];
    parent.readBytes(initializationData, 0, initializationDataSize);
    return Pair.create(mimeType, initializationData);
  }

  /**
   * Parses encryption data from an audio/video sample entry, populating {@code out} and returning
   * the unencrypted atom type, or 0 if no common encryption sinf atom was present.
   */
  private static int parseSampleEntryEncryptionData(ParsableByteArray parent, int position,
      int size, StsdData out, int entryIndex) {
    int childPosition = parent.getPosition();
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childAtomSize = parent.readInt();
      Assertions.checkArgument(childAtomSize > 0, "childAtomSize should be positive");
      int childAtomType = parent.readInt();
      if (childAtomType == Atom.TYPE_sinf) {
        Pair<Integer, TrackEncryptionBox> result = parseSinfFromParent(parent, childPosition,
            childAtomSize);
        if (result != null) {
          out.trackEncryptionBoxes[entryIndex] = result.second;
          return result.first;
        }
      }
      childPosition += childAtomSize;
    }
    // This enca/encv box does not have a data format so return an invalid atom type.
    return 0;
  }

  private static Pair<Integer, TrackEncryptionBox> parseSinfFromParent(ParsableByteArray parent,
      int position, int size) {
    int childPosition = position + Atom.HEADER_SIZE;

    boolean isCencScheme = false;
    TrackEncryptionBox trackEncryptionBox = null;
    Integer dataFormat = null;
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childAtomSize = parent.readInt();
      int childAtomType = parent.readInt();
      if (childAtomType == Atom.TYPE_frma) {
        dataFormat = parent.readInt();
      } else if (childAtomType == Atom.TYPE_schm) {
        parent.skipBytes(4);
        isCencScheme = parent.readInt() == TYPE_cenc;
      } else if (childAtomType == Atom.TYPE_schi) {
        trackEncryptionBox = parseSchiFromParent(parent, childPosition, childAtomSize);
      }
      childPosition += childAtomSize;
    }

    if (isCencScheme) {
      Assertions.checkArgument(dataFormat != null, "frma atom is mandatory");
      Assertions.checkArgument(trackEncryptionBox != null, "schi->tenc atom is mandatory");
      return Pair.create(dataFormat, trackEncryptionBox);
    } else {
      return null;
    }
  }

  private static TrackEncryptionBox parseSchiFromParent(ParsableByteArray parent, int position,
      int size) {
    int childPosition = position + Atom.HEADER_SIZE;
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childAtomSize = parent.readInt();
      int childAtomType = parent.readInt();
      if (childAtomType == Atom.TYPE_tenc) {
        parent.skipBytes(6);
        boolean defaultIsEncrypted = parent.readUnsignedByte() == 1;
        int defaultInitVectorSize = parent.readUnsignedByte();
        byte[] defaultKeyId = new byte[16];
        parent.readBytes(defaultKeyId, 0, defaultKeyId.length);
        return new TrackEncryptionBox(defaultIsEncrypted, defaultInitVectorSize, defaultKeyId);
      }
      childPosition += childAtomSize;
    }
    return null;
  }

  /**
   * Parses the proj box from sv3d box, as specified by https://github.com/google/spatial-media
   */
  private static byte[] parseProjFromParent(ParsableByteArray parent, int position, int size) {
    int childPosition = position + Atom.HEADER_SIZE;
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childAtomSize = parent.readInt();
      int childAtomType = parent.readInt();
      if (childAtomType == Atom.TYPE_proj) {
        return Arrays.copyOfRange(parent.data, childPosition, childPosition + childAtomSize);
      }
      childPosition += childAtomSize;
    }
    return null;
  }

  /**
   * Parses the size of an expandable class, as specified by ISO 14496-1 subsection 8.3.3.
   */
  private static int parseExpandableClassSize(ParsableByteArray data) {
    int currentByte = data.readUnsignedByte();
    int size = currentByte & 0x7F;
    while ((currentByte & 0x80) == 0x80) {
      currentByte = data.readUnsignedByte();
      size = (size << 7) | (currentByte & 0x7F);
    }
    return size;
  }

  private AtomParsers() {
    // Prevent instantiation.
  }

  private static final class ChunkIterator {

    public final int length;

    public int index;
    public int numSamples;
    public long offset;

    private final boolean chunkOffsetsAreLongs;
    private final ParsableByteArray chunkOffsets;
    private final ParsableByteArray stsc;

    private int nextSamplesPerChunkChangeIndex;
    private int remainingSamplesPerChunkChanges;

    public ChunkIterator(ParsableByteArray stsc, ParsableByteArray chunkOffsets,
        boolean chunkOffsetsAreLongs) {
      this.stsc = stsc;
      this.chunkOffsets = chunkOffsets;
      this.chunkOffsetsAreLongs = chunkOffsetsAreLongs;
      chunkOffsets.setPosition(Atom.FULL_HEADER_SIZE);
      length = chunkOffsets.readUnsignedIntToInt();
      stsc.setPosition(Atom.FULL_HEADER_SIZE);
      remainingSamplesPerChunkChanges = stsc.readUnsignedIntToInt();
      Assertions.checkState(stsc.readInt() == 1, "first_chunk must be 1");
      index = C.INDEX_UNSET;
    }

    public boolean moveNext() {
      if (++index == length) {
        return false;
      }
      offset = chunkOffsetsAreLongs ? chunkOffsets.readUnsignedLongToLong()
          : chunkOffsets.readUnsignedInt();
      if (index == nextSamplesPerChunkChangeIndex) {
        numSamples = stsc.readUnsignedIntToInt();
        stsc.skipBytes(4); // Skip sample_description_index
        nextSamplesPerChunkChangeIndex = --remainingSamplesPerChunkChanges > 0
            ? (stsc.readUnsignedIntToInt() - 1) : C.INDEX_UNSET;
      }
      return true;
    }

  }

  /**
   * Holds data parsed from a tkhd atom.
   */
  private static final class TkhdData {

    private final int id;
    private final long duration;
    private final int rotationDegrees;

    public TkhdData(int id, long duration, int rotationDegrees) {
      this.id = id;
      this.duration = duration;
      this.rotationDegrees = rotationDegrees;
    }

  }

  /**
   * Holds data parsed from an stsd atom and its children.
   */
  private static final class StsdData {

    public static final int STSD_HEADER_SIZE = 8;

    public final TrackEncryptionBox[] trackEncryptionBoxes;

    public Format format;
    public int nalUnitLengthFieldLength;
    @Track.Transformation
    public int requiredSampleTransformation;

    public StsdData(int numberOfEntries) {
      trackEncryptionBoxes = new TrackEncryptionBox[numberOfEntries];
      requiredSampleTransformation = Track.TRANSFORMATION_NONE;
    }

  }

  /**
   * A box containing sample sizes (e.g. stsz, stz2).
   */
  private interface SampleSizeBox {

    /**
     * Returns the number of samples.
     */
    int getSampleCount();

    /**
     * Returns the size for the next sample.
     */
    int readNextSampleSize();

    /**
     * Returns whether samples have a fixed size.
     */
    boolean isFixedSampleSize();

  }

  /**
   * An stsz sample size box.
   */
  /* package */ static final class StszSampleSizeBox implements SampleSizeBox {

    private final int fixedSampleSize;
    private final int sampleCount;
    private final ParsableByteArray data;

    public StszSampleSizeBox(Atom.LeafAtom stszAtom) {
      data = stszAtom.data;
      data.setPosition(Atom.FULL_HEADER_SIZE);
      fixedSampleSize = data.readUnsignedIntToInt();
      sampleCount = data.readUnsignedIntToInt();
    }

    @Override
    public int getSampleCount() {
      return sampleCount;
    }

    @Override
    public int readNextSampleSize() {
      return fixedSampleSize == 0 ? data.readUnsignedIntToInt() : fixedSampleSize;
    }

    @Override
    public boolean isFixedSampleSize() {
      return fixedSampleSize != 0;
    }

  }

  /**
   * An stz2 sample size box.
   */
  /* package */ static final class Stz2SampleSizeBox implements SampleSizeBox {

    private final ParsableByteArray data;
    private final int sampleCount;
    private final int fieldSize; // Can be 4, 8, or 16.

    // Used only if fieldSize == 4.
    private int sampleIndex;
    private int currentByte;

    public Stz2SampleSizeBox(Atom.LeafAtom stz2Atom) {
      data = stz2Atom.data;
      data.setPosition(Atom.FULL_HEADER_SIZE);
      fieldSize = data.readUnsignedIntToInt() & 0x000000FF;
      sampleCount = data.readUnsignedIntToInt();
    }

    @Override
    public int getSampleCount() {
      return sampleCount;
    }

    @Override
    public int readNextSampleSize() {
      if (fieldSize == 8) {
        return data.readUnsignedByte();
      } else if (fieldSize == 16) {
        return data.readUnsignedShort();
      } else {
        // fieldSize == 4.
        if ((sampleIndex++ % 2) == 0) {
          // Read the next byte into our cached byte when we are reading the upper bits.
          currentByte = data.readUnsignedByte();
          // Read the upper bits from the byte and shift them to the lower 4 bits.
          return (currentByte & 0xF0) >> 4;
        } else {
          // Mask out the upper 4 bits of the last byte we read.
          return currentByte & 0x0F;
        }
      }
    }

    @Override
    public boolean isFixedSampleSize() {
      return false;
    }

  }

}
