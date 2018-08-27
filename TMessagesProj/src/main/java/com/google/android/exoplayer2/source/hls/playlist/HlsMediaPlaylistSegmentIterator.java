/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.source.hls.playlist;

import android.net.Uri;
import com.google.android.exoplayer2.source.chunk.BaseMediaChunkIterator;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.UriUtil;

/** {@link MediaChunkIterator} wrapping a {@link HlsMediaPlaylist}. */
public final class HlsMediaPlaylistSegmentIterator extends BaseMediaChunkIterator {

  private final HlsMediaPlaylist playlist;
  private final long startOfPlaylistInPeriodUs;

  /**
   * Creates iterator.
   *
   * @param playlist The {@link HlsMediaPlaylist} to wrap.
   * @param startOfPlaylistInPeriodUs The start time of the playlist in the period, in microseconds.
   * @param chunkIndex The chunk index in the playlist at which the iterator will start.
   */
  public HlsMediaPlaylistSegmentIterator(
      HlsMediaPlaylist playlist, long startOfPlaylistInPeriodUs, int chunkIndex) {
    super(/* fromIndex= */ chunkIndex, /* toIndex= */ playlist.segments.size() - 1);
    this.playlist = playlist;
    this.startOfPlaylistInPeriodUs = startOfPlaylistInPeriodUs;
  }

  @Override
  public DataSpec getDataSpec() {
    checkInBounds();
    HlsMediaPlaylist.Segment segment = playlist.segments.get((int) getCurrentIndex());
    Uri chunkUri = UriUtil.resolveToUri(playlist.baseUri, segment.url);
    return new DataSpec(
        chunkUri, segment.byterangeOffset, segment.byterangeLength, /* key= */ null);
  }

  @Override
  public long getChunkStartTimeUs() {
    checkInBounds();
    HlsMediaPlaylist.Segment segment = playlist.segments.get((int) getCurrentIndex());
    return startOfPlaylistInPeriodUs + segment.relativeStartTimeUs;
  }

  @Override
  public long getChunkEndTimeUs() {
    checkInBounds();
    HlsMediaPlaylist.Segment segment = playlist.segments.get((int) getCurrentIndex());
    long segmentStartTimeInPeriodUs = startOfPlaylistInPeriodUs + segment.relativeStartTimeUs;
    return segmentStartTimeInPeriodUs + segment.durationUs;
  }
}
