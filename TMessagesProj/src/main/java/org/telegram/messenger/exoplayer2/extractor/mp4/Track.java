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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Encapsulates information describing an MP4 track.
 */
public final class Track {

  /**
   * The transformation to apply to samples in the track, if any.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({TRANSFORMATION_NONE, TRANSFORMATION_CEA608_CDAT})
  public @interface Transformation {}
  /**
   * A no-op sample transformation.
   */
  public static final int TRANSFORMATION_NONE = 0;
  /**
   * A transformation for caption samples in cdat atoms.
   */
  public static final int TRANSFORMATION_CEA608_CDAT = 1;

  /**
   * The track identifier.
   */
  public final int id;

  /**
   * One of {@link C#TRACK_TYPE_AUDIO}, {@link C#TRACK_TYPE_VIDEO} and {@link C#TRACK_TYPE_TEXT}.
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
   * The duration of the track in microseconds, or {@link C#TIME_UNSET} if unknown.
   */
  public final long durationUs;

  /**
   * The format.
   */
  public final Format format;

  /**
   * One of {@code TRANSFORMATION_*}. Defines the transformation to apply before outputting each
   * sample.
   */
  @Transformation public final int sampleTransformation;

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
   * For H264 video tracks, the length in bytes of the NALUnitLength field in each sample. 0 for
   * other track types.
   */
  public final int nalUnitLengthFieldLength;

  public Track(int id, int type, long timescale, long movieTimescale, long durationUs,
      Format format, @Transformation int sampleTransformation,
      TrackEncryptionBox[] sampleDescriptionEncryptionBoxes, int nalUnitLengthFieldLength,
      long[] editListDurations, long[] editListMediaTimes) {
    this.id = id;
    this.type = type;
    this.timescale = timescale;
    this.movieTimescale = movieTimescale;
    this.durationUs = durationUs;
    this.format = format;
    this.sampleTransformation = sampleTransformation;
    this.sampleDescriptionEncryptionBoxes = sampleDescriptionEncryptionBoxes;
    this.nalUnitLengthFieldLength = nalUnitLengthFieldLength;
    this.editListDurations = editListDurations;
    this.editListMediaTimes = editListMediaTimes;
  }

}
