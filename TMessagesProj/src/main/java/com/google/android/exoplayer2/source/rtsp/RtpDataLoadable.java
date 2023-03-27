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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.DefaultExtractorInput;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.upstream.DataSourceUtil;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link Loader.Loadable} that uses two {@link RtpDataChannel} instances to listen on incoming
 * RTP and RTCP packets.
 *
 * <ul>
 *   <li>When using UDP as RTP transport, the local RTP UDP port number is selected by the runtime
 *       on opening the first {@link RtpDataChannel}; the second {@link RtpDataChannel} for RTCP
 *       uses the port number that is the RTP UDP port number plus one.
 *   <li>When using TCP as RTP transport, the first {@link RtpDataChannel} for RTP uses the {@link
 *       #trackId} as its interleaved channel number; the second {@link RtpDataChannel} for RTCP
 *       uses the interleaved channel number that is the RTP interleaved channel number plus one.
 * </ul>
 *
 * <p>Pass a listener via the constructor to receive a callback when the RTSP transport is ready.
 * {@link #load} will throw an {@link IOException} if either of the two data channels fails to open.
 *
 * <p>Received RTP packets' payloads will be extracted by an {@link RtpExtractor}, and will be
 * written to the {@link ExtractorOutput} instance provided at construction.
 */
/* package */ final class RtpDataLoadable implements Loader.Loadable {

  /** Called on loadable events. */
  public interface EventListener {
    /**
     * Called when the transport information for receiving incoming RTP and RTCP packets is ready.
     *
     * @param transport The RTSP transport (RFC2326 Section 12.39) including the client data port
     *     and RTCP port.
     * @param rtpDataChannel The {@link RtpDataChannel} associated with the transport.
     */
    void onTransportReady(String transport, RtpDataChannel rtpDataChannel);
  }

  /** The track ID associated with the Loadable. */
  public final int trackId;
  /** The {@link RtspMediaTrack} to load. */
  public final RtspMediaTrack rtspMediaTrack;

  private final EventListener eventListener;
  private final ExtractorOutput output;
  private final Handler playbackThreadHandler;
  private final RtpDataChannel.Factory rtpDataChannelFactory;

  private @MonotonicNonNull RtpExtractor extractor;

  private volatile boolean loadCancelled;
  private volatile long pendingSeekPositionUs;
  private volatile long nextRtpTimestamp;

  /**
   * Creates an {@link RtpDataLoadable} that listens on incoming RTP traffic.
   *
   * <p>Caller of this constructor must be on playback thread.
   *
   * @param trackId The track ID associated with the Loadable.
   * @param rtspMediaTrack The {@link RtspMediaTrack} to load.
   * @param eventListener The {@link EventListener}.
   * @param output A {@link ExtractorOutput} instance to which the received and extracted data will
   * @param rtpDataChannelFactory A {@link RtpDataChannel.Factory} for {@link RtpDataChannel}.
   */
  public RtpDataLoadable(
      int trackId,
      RtspMediaTrack rtspMediaTrack,
      EventListener eventListener,
      ExtractorOutput output,
      RtpDataChannel.Factory rtpDataChannelFactory) {
    this.trackId = trackId;
    this.rtspMediaTrack = rtspMediaTrack;
    this.eventListener = eventListener;
    this.output = output;
    this.playbackThreadHandler = Util.createHandlerForCurrentLooper();
    this.rtpDataChannelFactory = rtpDataChannelFactory;
    pendingSeekPositionUs = C.TIME_UNSET;
  }

  /**
   * Sets the timestamp of an RTP packet to arrive.
   *
   * @param timestamp The timestamp of the RTP packet to arrive. Supply {@link C#TIME_UNSET} if its
   *     unavailable.
   */
  public void setTimestamp(long timestamp) {
    if (timestamp != C.TIME_UNSET) {
      if (!checkNotNull(extractor).hasReadFirstRtpPacket()) {
        extractor.setFirstTimestamp(timestamp);
      }
    }
  }

  /**
   * Sets the timestamp of an RTP packet to arrive.
   *
   * @param sequenceNumber The sequence number of the RTP packet to arrive. Supply {@link
   *     C#INDEX_UNSET} if its unavailable.
   */
  public void setSequenceNumber(int sequenceNumber) {
    if (!checkNotNull(extractor).hasReadFirstRtpPacket()) {
      extractor.setFirstSequenceNumber(sequenceNumber);
    }
  }

  @Override
  public void cancelLoad() {
    loadCancelled = true;
  }

  @Override
  public void load() throws IOException {
    @Nullable RtpDataChannel dataChannel = null;
    try {
      dataChannel = rtpDataChannelFactory.createAndOpenDataChannel(trackId);
      String transport = dataChannel.getTransport();

      RtpDataChannel finalDataChannel = dataChannel;
      playbackThreadHandler.post(() -> eventListener.onTransportReady(transport, finalDataChannel));

      // Sets up the extractor.
      ExtractorInput extractorInput =
          new DefaultExtractorInput(
              checkNotNull(dataChannel), /* position= */ 0, /* length= */ C.LENGTH_UNSET);
      extractor = new RtpExtractor(rtspMediaTrack.payloadFormat, trackId);
      extractor.init(output);

      while (!loadCancelled) {
        if (pendingSeekPositionUs != C.TIME_UNSET) {
          extractor.seek(nextRtpTimestamp, pendingSeekPositionUs);
          pendingSeekPositionUs = C.TIME_UNSET;
        }

        @Extractor.ReadResult
        int readResult = extractor.read(extractorInput, /* seekPosition= */ new PositionHolder());
        if (readResult == Extractor.RESULT_END_OF_INPUT) {
          // Loading is finished.
          break;
        }
      }
    } finally {
      DataSourceUtil.closeQuietly(dataChannel);
    }
  }

  /**
   * Signals when performing an RTSP seek that involves RTSP message exchange.
   *
   * <p>{@link #seekToUs} must be called after the seek is successful.
   */
  public void resetForSeek() {
    checkNotNull(extractor).preSeek();
  }

  /**
   * Sets the correct start position and RTP timestamp after a successful RTSP seek.
   *
   * @param positionUs The position in microseconds from the start, from which the server starts
   *     play.
   * @param nextRtpTimestamp The first RTP packet's timestamp after the seek.
   */
  public void seekToUs(long positionUs, long nextRtpTimestamp) {
    pendingSeekPositionUs = positionUs;
    this.nextRtpTimestamp = nextRtpTimestamp;
  }
}
