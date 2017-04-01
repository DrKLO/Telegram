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

import org.telegram.messenger.exoplayer2.C;
import java.util.ArrayList;
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
    public final int discontinuitySequenceNumber;
    public final long startTimeUs;
    public final boolean isEncrypted;
    public final String encryptionKeyUri;
    public final String encryptionIV;
    public final long byterangeOffset;
    public final long byterangeLength;

    public Segment(String uri, long byterangeOffset, long byterangeLength) {
      this(uri, 0, -1, C.TIME_UNSET, false, null, null, byterangeOffset, byterangeLength);
    }

    public Segment(String uri, long durationUs, int discontinuitySequenceNumber,
        long startTimeUs, boolean isEncrypted, String encryptionKeyUri, String encryptionIV,
        long byterangeOffset, long byterangeLength) {
      this.url = uri;
      this.durationUs = durationUs;
      this.discontinuitySequenceNumber = discontinuitySequenceNumber;
      this.startTimeUs = startTimeUs;
      this.isEncrypted = isEncrypted;
      this.encryptionKeyUri = encryptionKeyUri;
      this.encryptionIV = encryptionIV;
      this.byterangeOffset = byterangeOffset;
      this.byterangeLength = byterangeLength;
    }

    @Override
    public int compareTo(Long startTimeUs) {
      return this.startTimeUs > startTimeUs ? 1 : (this.startTimeUs < startTimeUs ? -1 : 0);
    }

    public Segment copyWithStartTimeUs(long startTimeUs) {
      return new Segment(url, durationUs, discontinuitySequenceNumber, startTimeUs, isEncrypted,
          encryptionKeyUri, encryptionIV, byterangeOffset, byterangeLength);
    }

  }

  public final int mediaSequence;
  public final int version;
  public final Segment initializationSegment;
  public final List<Segment> segments;
  public final boolean hasEndTag;
  public final long durationUs;

  public HlsMediaPlaylist(String baseUri, int mediaSequence, int version,
      boolean hasEndTag, Segment initializationSegment, List<Segment> segments) {
    super(baseUri, HlsPlaylist.TYPE_MEDIA);
    this.mediaSequence = mediaSequence;
    this.version = version;
    this.hasEndTag = hasEndTag;
    this.initializationSegment = initializationSegment;
    this.segments = Collections.unmodifiableList(segments);

    if (!segments.isEmpty()) {
      Segment first = segments.get(0);
      Segment last = segments.get(segments.size() - 1);
      durationUs = last.startTimeUs + last.durationUs - first.startTimeUs;
    } else {
      durationUs = 0;
    }
  }

  public long getStartTimeUs() {
    return segments.isEmpty() ? 0 : segments.get(0).startTimeUs;
  }

  public long getEndTimeUs() {
    return getStartTimeUs() + durationUs;
  }

  public HlsMediaPlaylist copyWithStartTimeUs(long newStartTimeUs) {
    long startTimeOffsetUs = newStartTimeUs - getStartTimeUs();
    int segmentsSize = segments.size();
    List<Segment> newSegments = new ArrayList<>(segmentsSize);
    for (int i = 0; i < segmentsSize; i++) {
      Segment segment = segments.get(i);
      newSegments.add(segment.copyWithStartTimeUs(segment.startTimeUs + startTimeOffsetUs));
    }
    return copyWithSegments(newSegments);
  }

  public HlsMediaPlaylist copyWithSegments(List<Segment> segments) {
    return new HlsMediaPlaylist(baseUri, mediaSequence, version, hasEndTag,
        initializationSegment, segments);
  }

}
