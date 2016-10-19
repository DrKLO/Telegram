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
package org.telegram.messenger.exoplayer.extractor;

import org.telegram.messenger.exoplayer.drm.DrmInitData;

/**
 * Receives stream level data extracted by an {@link Extractor}.
 */
public interface ExtractorOutput {

  /**
   * Invoked when the {@link Extractor} identifies the existence of a track in the stream.
   * <p>
   * Returns a {@link TrackOutput} that will receive track level data belonging to the track.
   *
   * @param trackId A track identifier.
   * @return The {@link TrackOutput} that should receive track level data belonging to the track.
   */
  TrackOutput track(int trackId);

  /**
   * Invoked when all tracks have been identified, meaning that {@link #track(int)} will not be
   * invoked again.
   */
  void endTracks();

  /**
   * Invoked when a {@link SeekMap} has been extracted from the stream.
   *
   * @param seekMap The extracted {@link SeekMap}.
   */
  void seekMap(SeekMap seekMap);

  /**
   * Invoked when {@link DrmInitData} has been extracted from the stream.
   *
   * @param drmInitData The extracted {@link DrmInitData}.
   */
  void drmInitData(DrmInitData drmInitData);

}
