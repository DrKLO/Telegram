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
package org.telegram.messenger.exoplayer.smoothstreaming;

import android.content.Context;
import org.telegram.messenger.exoplayer.chunk.VideoFormatSelectorUtil;
import org.telegram.messenger.exoplayer.smoothstreaming.SmoothStreamingManifest.StreamElement;
import org.telegram.messenger.exoplayer.smoothstreaming.SmoothStreamingManifest.TrackElement;
import org.telegram.messenger.exoplayer.util.Util;
import java.io.IOException;
import java.util.Arrays;

/**
 * A default {@link SmoothStreamingTrackSelector} implementation.
 */
// TODO: Add more configuration options (e.g. ability to disable adaptive track output).
public final class DefaultSmoothStreamingTrackSelector implements SmoothStreamingTrackSelector {

  private final int streamElementType;

  private final Context context;
  private final boolean filterVideoRepresentations;
  private final boolean filterProtectedHdContent;

  /**
   * @param context A context. May be null if {@code filterVideoRepresentations == false}.
   * @param filterVideoRepresentations Whether video representations should be filtered according to
   *     the capabilities of the device. It is strongly recommended to set this to {@code true},
   *     unless the application has already verified that all representations are playable.
   * @param filterProtectedHdContent Whether video representations that are both drm protected and
   *     high definition should be filtered when tracks are built. If
   *     {@code filterVideoRepresentations == false} then this parameter is ignored.
   */
  public static DefaultSmoothStreamingTrackSelector newVideoInstance(Context context,
      boolean filterVideoRepresentations, boolean filterProtectedHdContent) {
    return new DefaultSmoothStreamingTrackSelector(StreamElement.TYPE_VIDEO, context,
        filterVideoRepresentations, filterProtectedHdContent);
  }

  public static DefaultSmoothStreamingTrackSelector newAudioInstance() {
    return new DefaultSmoothStreamingTrackSelector(StreamElement.TYPE_AUDIO, null, false, false);
  }

  public static DefaultSmoothStreamingTrackSelector newTextInstance() {
    return new DefaultSmoothStreamingTrackSelector(StreamElement.TYPE_TEXT, null, false, false);
  }

  private DefaultSmoothStreamingTrackSelector(int streamElementType, Context context,
      boolean filterVideoRepresentations, boolean filterProtectedHdContent) {
    this.context = context;
    this.streamElementType = streamElementType;
    this.filterVideoRepresentations = filterVideoRepresentations;
    this.filterProtectedHdContent = filterProtectedHdContent;
  }

  @Override
  public void selectTracks(SmoothStreamingManifest manifest, Output output) throws IOException {
    for (int i = 0; i < manifest.streamElements.length; i++) {
      TrackElement[] tracks = manifest.streamElements[i].tracks;
      if (manifest.streamElements[i].type == streamElementType) {
        if (streamElementType == StreamElement.TYPE_VIDEO) {
          int[] trackIndices;
          if (filterVideoRepresentations) {
            trackIndices = VideoFormatSelectorUtil.selectVideoFormatsForDefaultDisplay(
                context, Arrays.asList(tracks), null,
                filterProtectedHdContent && manifest.protectionElement != null);
          } else {
            trackIndices = Util.firstIntegersArray(tracks.length);
          }
          int trackCount = trackIndices.length;
          if (trackCount > 1) {
            output.adaptiveTrack(manifest, i, trackIndices);
          }
          for (int j = 0; j < trackCount; j++) {
            output.fixedTrack(manifest, i, trackIndices[j]);
          }
        } else {
          for (int j = 0; j < tracks.length; j++) {
            output.fixedTrack(manifest, i, j);
          }
        }
      }
    }
  }

}
