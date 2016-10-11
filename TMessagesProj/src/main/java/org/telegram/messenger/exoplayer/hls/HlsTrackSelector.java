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
package org.telegram.messenger.exoplayer.hls;

import java.io.IOException;

/**
 * Specifies a track selection from an {@link HlsMasterPlaylist}.
 */
public interface HlsTrackSelector {

  /**
   * Defines a selector output.
   */
  interface Output {

    /**
     * Outputs an adaptive track, covering the specified representations in the specified
     * adaptation set.
     *
     * @param playlist The master playlist being processed.
     * @param variants The variants to use for the adaptive track.
     */
    void adaptiveTrack(HlsMasterPlaylist playlist, Variant[] variants);

    /**
     * Outputs an fixed track corresponding to the specified representation in the specified
     * adaptation set.
     *
     * @param playlist The master playlist being processed.
     * @param variant The variant to use for the track.
     */
    void fixedTrack(HlsMasterPlaylist playlist, Variant variant);

  }

  /**
   * Outputs a track selection for a given period.
   *
   * @param playlist The master playlist to process.
   * @param output The output to receive tracks.
   * @throws IOException If an error occurs processing the period.
   */
  void selectTracks(HlsMasterPlaylist playlist, Output output) throws IOException;

}
