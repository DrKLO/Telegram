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
package org.telegram.messenger.exoplayer.extractor.mp4;

import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.util.Util;

/**
 * Encapsulates information describing an MP4 track.
 */
public final class Track {

  public static final int TYPE_vide = Util.getIntegerCodeForString("vide");
  public static final int TYPE_soun = Util.getIntegerCodeForString("soun");
  public static final int TYPE_text = Util.getIntegerCodeForString("text");
  public static final int TYPE_sbtl = Util.getIntegerCodeForString("sbtl");
  public static final int TYPE_subt = Util.getIntegerCodeForString("subt");

  /**
   * The track identifier.
   */
  public final int id;

  /**
   * One of {@link #TYPE_vide}, {@link #TYPE_soun}, {@link #TYPE_text} and {@link #TYPE_sbtl} and
   * {@link #TYPE_subt}.
   */
  public final int type;

  /**
   * The track timescale, defined as the number of time units that pass in one second.
   */
  public final long timescale;

  /**
   * The movie timescale.
   */
  public final long movieTimescale;

  /**
   * The duration of the track in microseconds, or {@link C#UNKNOWN_TIME_US} if unknown.
   */
  public final long durationUs;

  /**
   * The media format.
   */
  public final MediaFormat mediaFormat;

  /**
   * Track encryption boxes for the different track sample descriptions. Entries may be null.
   */
  public final TrackEncryptionBox[] sampleDescriptionEncryptionBoxes;

  /**
   * Durations of edit list segments in the movie timescale. Null if there is no edit list.
   */
  public final long[] editListDurations;

  /**
   * Media times for edit list segments in the track timescale. Null if there is no edit list.
   */
  public final long[] editListMediaTimes;

  /**
   * For H264 video tracks, the length in bytes of the NALUnitLength field in each sample. -1 for
   * other track types.
   */
  public final int nalUnitLengthFieldLength;

  public Track(int id, int type, long timescale, long movieTimescale, long durationUs,
      MediaFormat mediaFormat, TrackEncryptionBox[] sampleDescriptionEncryptionBoxes,
      int nalUnitLengthFieldLength, long[] editListDurations, long[] editListMediaTimes) {
    this.id = id;
    this.type = type;
    this.timescale = timescale;
    this.movieTimescale = movieTimescale;
    this.durationUs = durationUs;
    this.mediaFormat = mediaFormat;
    this.sampleDescriptionEncryptionBoxes = sampleDescriptionEncryptionBoxes;
    this.nalUnitLengthFieldLength = nalUnitLengthFieldLength;
    this.editListDurations = editListDurations;
    this.editListMediaTimes = editListMediaTimes;
  }

}
