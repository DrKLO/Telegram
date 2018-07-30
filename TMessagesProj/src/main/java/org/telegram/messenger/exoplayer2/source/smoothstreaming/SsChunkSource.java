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
package org.telegram.messenger.exoplayer2.source.smoothstreaming;

import org.telegram.messenger.exoplayer2.extractor.mp4.TrackEncryptionBox;
import org.telegram.messenger.exoplayer2.source.chunk.ChunkSource;
import org.telegram.messenger.exoplayer2.source.smoothstreaming.manifest.SsManifest;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelection;
import org.telegram.messenger.exoplayer2.upstream.LoaderErrorThrower;

/**
 * A {@link ChunkSource} for SmoothStreaming.
 */
public interface SsChunkSource extends ChunkSource {

  /** Factory for {@link SsChunkSource}s. */
  interface Factory {

    /**
     * Creates a new {@link SsChunkSource}.
     *
     * @param manifestLoaderErrorThrower Throws errors affecting loading of manifests.
     * @param manifest The initial manifest.
     * @param streamElementIndex The index of the corresponding stream element in the manifest.
     * @param trackSelection The track selection.
     * @param trackEncryptionBoxes Track encryption boxes for the stream.
     * @return The created {@link SsChunkSource}.
     */
    SsChunkSource createChunkSource(
        LoaderErrorThrower manifestLoaderErrorThrower,
        SsManifest manifest,
        int streamElementIndex,
        TrackSelection trackSelection,
        TrackEncryptionBox[] trackEncryptionBoxes);
  }

  /**
   * Updates the manifest.
   *
   * @param newManifest The new manifest.
   */
  void updateManifest(SsManifest newManifest);
}
