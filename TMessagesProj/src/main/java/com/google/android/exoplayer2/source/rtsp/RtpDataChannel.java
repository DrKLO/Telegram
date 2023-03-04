/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.source.rtsp;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.rtsp.RtspMessageChannel.InterleavedBinaryDataListener;
import com.google.android.exoplayer2.upstream.DataSource;
import java.io.IOException;

/** An RTP {@link DataSource}. */
/* package */ interface RtpDataChannel extends DataSource {

  /** Creates {@link RtpDataChannel} for RTSP streams. */
  interface Factory {

    /**
     * Creates a new {@link RtpDataChannel} instance for RTP data transfer.
     *
     * @param trackId The track ID.
     * @throws IOException If the data channels failed to open.
     */
    RtpDataChannel createAndOpenDataChannel(int trackId) throws IOException;

    /** Returns a fallback {@code Factory}, {@code null} when there is no fallback available. */
    @Nullable
    default Factory createFallbackDataChannelFactory() {
      return null;
    }
  }

  /** Returns the RTSP transport header for this {@link RtpDataChannel} */
  String getTransport();

  /**
   * Returns the receiving port or channel used by the underlying transport protocol, {@link
   * C#INDEX_UNSET} if the data channel is not opened.
   */
  int getLocalPort();

  /**
   * Returns a {@link InterleavedBinaryDataListener} if the implementation supports receiving RTP
   * packets on a side-band protocol, for example RTP-over-RTSP; otherwise {@code null}.
   */
  @Nullable
  InterleavedBinaryDataListener getInterleavedBinaryDataListener();
}
