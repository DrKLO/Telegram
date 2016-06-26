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

import java.io.IOException;

/**
 * Specifies a track selection from a {@link SmoothStreamingManifest}.
 */
public interface SmoothStreamingTrackSelector {

  /**
   * Defines a selector output.
   */
  interface Output {

    /**
     * Outputs an adaptive track, covering the specified tracks in the specified element.
     *
     * @param manifest The manifest being processed.
     * @param element The index of the element within which the adaptive tracks are located.
     * @param tracks The indices of the tracks within the element.
     */
    void adaptiveTrack(SmoothStreamingManifest manifest, int element, int[] tracks);

    /**
     * Outputs a fixed track corresponding to the specified track in the specified element.
     *
     * @param manifest The manifest being processed.
     * @param element The index of the element within which the track is located.
     * @param track The index of the track within the element.
     */
    void fixedTrack(SmoothStreamingManifest manifest, int element, int track);

  }

  /**
   * Outputs a track selection for a given manifest.
   *
   * @param manifest The manifest to process.
   * @param output The output to receive tracks.
   * @throws IOException If an error occurs processing the manifest.
   */
  void selectTracks(SmoothStreamingManifest manifest, Output output) throws IOException;

}
