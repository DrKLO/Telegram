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
package org.telegram.messenger.exoplayer.dash;

import android.content.Context;
import org.telegram.messenger.exoplayer.chunk.VideoFormatSelectorUtil;
import org.telegram.messenger.exoplayer.dash.mpd.AdaptationSet;
import org.telegram.messenger.exoplayer.dash.mpd.MediaPresentationDescription;
import org.telegram.messenger.exoplayer.dash.mpd.Period;
import org.telegram.messenger.exoplayer.util.Util;
import java.io.IOException;

/**
 * A default {@link DashTrackSelector} implementation.
 */
// TODO: Add more configuration options (e.g. ability to disable adaptive track output).
public final class DefaultDashTrackSelector implements DashTrackSelector {

  private final int adaptationSetType;

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
  public static DefaultDashTrackSelector newVideoInstance(Context context,
      boolean filterVideoRepresentations, boolean filterProtectedHdContent) {
    return new DefaultDashTrackSelector(AdaptationSet.TYPE_VIDEO, context,
        filterVideoRepresentations, filterProtectedHdContent);
  }

  public static DefaultDashTrackSelector newAudioInstance() {
    return new DefaultDashTrackSelector(AdaptationSet.TYPE_AUDIO, null, false, false);
  }

  public static DefaultDashTrackSelector newTextInstance() {
    return new DefaultDashTrackSelector(AdaptationSet.TYPE_TEXT, null, false, false);
  }

  private DefaultDashTrackSelector(int adaptationSetType, Context context,
      boolean filterVideoRepresentations, boolean filterProtectedHdContent) {
    this.adaptationSetType = adaptationSetType;
    this.context = context;
    this.filterVideoRepresentations = filterVideoRepresentations;
    this.filterProtectedHdContent = filterProtectedHdContent;
  }

  @Override
  public void selectTracks(MediaPresentationDescription manifest, int periodIndex, Output output)
      throws IOException {
    Period period = manifest.getPeriod(periodIndex);
    for (int i = 0; i < period.adaptationSets.size(); i++) {
      AdaptationSet adaptationSet = period.adaptationSets.get(i);
      if (adaptationSet.type == adaptationSetType) {
        if (adaptationSetType == AdaptationSet.TYPE_VIDEO) {
          int[] representations;
          if (filterVideoRepresentations) {
            representations = VideoFormatSelectorUtil.selectVideoFormatsForDefaultDisplay(
                context, adaptationSet.representations, null,
                filterProtectedHdContent && adaptationSet.hasContentProtection());
          } else {
            representations = Util.firstIntegersArray(adaptationSet.representations.size());
          }
          int representationCount = representations.length;
          if (representationCount > 1) {
            output.adaptiveTrack(manifest, periodIndex, i, representations);
          }
          for (int j = 0; j < representationCount; j++) {
            output.fixedTrack(manifest, periodIndex, i, representations[j]);
          }
        } else {
          for (int j = 0; j < adaptationSet.representations.size(); j++) {
            output.fixedTrack(manifest, periodIndex, i, j);
          }
        }
      }
    }
  }

}
