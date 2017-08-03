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
package org.telegram.messenger.exoplayer2.source.hls.playlist;

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import org.telegram.messenger.exoplayer2.C;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;

/**
 * Represents an HLS media playlist.
 */
public final class HlsMediaPlaylist extends HlsPlaylist {

  /**
   * Media segment reference.
   */
  public static final class Segment implements Comparable<Long> {

    public final String url;
    public final long durationUs;
    public final int relativeDiscontinuitySequence;
    public final long relativeStartTimeUs;
    public final boolean isEncrypted;
    public final String encryptionKeyUri;
    public final String encryptionIV;
    public final long byterangeOffset;
    public final long byterangeLength;

    public Segment(String uri, long byterangeOffset, long byterangeLength) {
      this(uri, 0, -1, C.TIME_UNSET, false, null, null, byterangeOffset, byterangeLength);
    }

    public Segment(String uri, long durationUs, int relativeDiscontinuitySequence,
        long relativeStartTimeUs, boolean isEncrypted, String encryptionKeyUri, String encryptionIV,
        long byterangeOffset, long byterangeLength) {
      this.url = uri;
      this.durationUs = durationUs;
      this.relativeDiscontinuitySequence = relativeDiscontinuitySequence;
      this.relativeStartTimeUs = relativeStartTimeUs;
      this.isEncrypted = isEncrypted;
      this.encryptionKeyUri = encryptionKeyUri;
      this.encryptionIV = encryptionIV;
      this.byterangeOffset = byterangeOffset;
      this.byterangeLength = byterangeLength;
    }

    @Override
    public int compareTo(@NonNull Long relativeStartTimeUs) {
      return this.relativeStartTimeUs > relativeStartTimeUs
          ? 1 : (this.relativeStartTimeUs < relativeStartTimeUs ? -1 : 0);
    }

  }

  /**
   * Type of the playlist as specified by #EXT-X-PLAYLIST-TYPE.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({PLAYLIST_TYPE_UNKNOWN, PLAYLIST_TYPE_VOD, PLAYLIST_TYPE_EVENT})
  public @interface PlaylistType {}
  public static final int PLAYLIST_TYPE_UNKNOWN = 0;
  public static final int PLAYLIST_TYPE_VOD = 1;
  public static final int PLAYLIST_TYPE_EVENT = 2;

  @PlaylistType public final int playlistType;
  public final long startOffsetUs;
  public final long startTimeUs;
  public final boolean hasDiscontinuitySequence;
  public final int discontinuitySequence;
  public final int mediaSequence;
  public final int version;
  public final long targetDurationUs;
  public final boolean hasEndTag;
  public final boolean hasProgramDateTime;
  public final Segment initializationSegment;
  public final List<Segment> segments;
  public final long durationUs;

  public HlsMediaPlaylist(@PlaylistType int playlistType, String baseUri, long startOffsetUs,
      long startTimeUs, boolean hasDiscontinuitySequence, int discontinuitySequence,
      int mediaSequence, int version, long targetDurationUs, boolean hasEndTag,
      boolean hasProgramDateTime, Segment initializationSegment, List<Segment> segments) {
    super(baseUri);
    this.playlistType = playlistType;
    this.startTimeUs = startTimeUs;
    this.hasDiscontinuitySequence = hasDiscontinuitySequence;
    this.discontinuitySequence = discontinuitySequence;
    this.mediaSequence = mediaSequence;
    this.version = version;
    this.targetDurationUs = targetDurationUs;
    this.hasEndTag = hasEndTag;
    this.hasProgramDateTime = hasProgramDateTime;
    this.initializationSegment = initializationSegment;
    this.segments = Collections.unmodifiableList(segments);
    if (!segments.isEmpty()) {
      Segment last = segments.get(segments.size() - 1);
      durationUs = last.relativeStartTimeUs + last.durationUs;
    } else {
      durationUs = 0;
    }
    this.startOffsetUs = startOffsetUs == C.TIME_UNSET ? C.TIME_UNSET
        : startOffsetUs >= 0 ? startOffsetUs : durationUs + startOffsetUs;
  }

  /**
   * Returns whether this playlist is newer than {@code other}.
   *
   * @param other The playlist to compare.
   * @return Whether this playlist is newer than {@code other}.
   */
  public boolean isNewerThan(HlsMediaPlaylist other) {
    if (other == null || mediaSequence > other.mediaSequence) {
      return true;
    }
    if (mediaSequence < other.mediaSequence) {
      return false;
    }
    // The media sequences are equal.
    int segmentCount = segments.size();
    int otherSegmentCount = other.segments.size();
    return segmentCount > otherSegmentCount
        || (segmentCount == otherSegmentCount && hasEndTag && !other.hasEndTag);
  }

  public long getEndTimeUs() {
    return startTimeUs + durationUs;
  }

  /**
   * Returns a playlist identical to this one except for the start time, the discontinuity sequence
   * and {@code hasDiscontinuitySequence} values. The first two are set to the specified values,
   * {@code hasDiscontinuitySequence} is set to true.
   *
   * @param startTimeUs The start time for the returned playlist.
   * @param discontinuitySequence The discontinuity sequence for the returned playlist.
   * @return The playlist.
   */
  public HlsMediaPlaylist copyWith(long startTimeUs, int discontinuitySequence) {
    return new HlsMediaPlaylist(playlistType, baseUri, startOffsetUs, startTimeUs, true,
        discontinuitySequence, mediaSequence, version, targetDurationUs, hasEndTag,
        hasProgramDateTime, initializationSegment, segments);
  }

  /**
   * Returns a playlist identical to this one except that an end tag is added. If an end tag is
   * already present then the playlist will return itself.
   *
   * @return The playlist.
   */
  public HlsMediaPlaylist copyWithEndTag() {
    if (this.hasEndTag) {
      return this;
    }
    return new HlsMediaPlaylist(playlistType, baseUri, startOffsetUs, startTimeUs,
        hasDiscontinuitySequence, discontinuitySequence, mediaSequence, version, targetDurationUs,
        true, hasProgramDateTime, initializationSegment, segments);
  }

}
