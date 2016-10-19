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

import org.telegram.messenger.exoplayer.dash.mpd.MediaPresentationDescription;
import org.telegram.messenger.exoplayer.dash.mpd.Period;
import java.io.IOException;

/**
 * Specifies a track selection from a {@link Period} of a media presentation description.
 */
public interface DashTrackSelector {

  /**
   * Defines a selector output.
   */
  interface Output {

    /**
     * Outputs an adaptive track, covering the specified representations in the specified
     * adaptation set.
     *
     * @param manifest The media presentation description being processed.
     * @param periodIndex The index of the period being processed.
     * @param adaptationSetIndex The index of the adaptation set within which the representations
     *     are located.
     * @param representationIndices The indices of the track within the element.
     */
    void adaptiveTrack(MediaPresentationDescription manifest, int periodIndex,
        int adaptationSetIndex, int[] representationIndices);

    /**
     * Outputs an fixed track corresponding to the specified representation in the specified
     * adaptation set.
     * 
     * @param manifest The media presentation description being processed.
     * @param periodIndex The index of the period being processed.
     * @param adaptationSetIndex The index of the adaptation set within which the track is located.
     * @param representationIndex The index of the representation within the adaptation set.
     */
    void fixedTrack(MediaPresentationDescription manifest, int periodIndex, int adaptationSetIndex,
        int representationIndex);

  }

  /**
   * Outputs a track selection for a given period.
   *
   * @param manifest the media presentation description to process.
   * @param periodIndex The index of the period to process.
   * @param output The output to receive tracks.
   * @throws IOException If an error occurs processing the period.
   */
  void selectTracks(MediaPresentationDescription manifest, int periodIndex, Output output)
      throws IOException;

}
