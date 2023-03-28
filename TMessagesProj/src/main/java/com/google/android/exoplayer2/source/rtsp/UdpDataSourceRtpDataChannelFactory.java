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

import com.google.android.exoplayer2.upstream.DataSourceUtil;
import java.io.IOException;

/** Factory for {@link UdpDataSourceRtpDataChannel}. */
/* package */ final class UdpDataSourceRtpDataChannelFactory implements RtpDataChannel.Factory {

  private final long socketTimeoutMs;

  /**
   * Creates a new instance.
   *
   * @param socketTimeoutMs A positive number of milliseconds to wait before lack of received RTP
   *     packets is treated as the end of input.
   */
  public UdpDataSourceRtpDataChannelFactory(long socketTimeoutMs) {
    this.socketTimeoutMs = socketTimeoutMs;
  }

  @Override
  public RtpDataChannel createAndOpenDataChannel(int trackId) throws IOException {
    UdpDataSourceRtpDataChannel firstChannel = new UdpDataSourceRtpDataChannel(socketTimeoutMs);
    UdpDataSourceRtpDataChannel secondChannel = new UdpDataSourceRtpDataChannel(socketTimeoutMs);

    try {
      // From RFC3550 Section 11: "For UDP and similar protocols, RTP SHOULD use an even destination
      // port number and the corresponding RTCP stream SHOULD use the next higher (odd) destination
      // port number". Some RTSP servers are strict about this rule. We open a data channel first,
      // and depending its port number, open the next data channel with a port number that is either
      // the higher or the lower.

      // Using port zero will cause the system to generate a port.
      firstChannel.open(RtpUtils.getIncomingRtpDataSpec(/* portNumber= */ 0));
      int firstPort = firstChannel.getLocalPort();
      boolean isFirstPortEven = firstPort % 2 == 0;
      int portToOpen = isFirstPortEven ? firstPort + 1 : firstPort - 1;
      secondChannel.open(RtpUtils.getIncomingRtpDataSpec(/* portNumber= */ portToOpen));

      if (isFirstPortEven) {
        firstChannel.setRtcpChannel(secondChannel);
        return firstChannel;
      } else {
        secondChannel.setRtcpChannel(firstChannel);
        return secondChannel;
      }
    } catch (IOException e) {
      DataSourceUtil.closeQuietly(firstChannel);
      DataSourceUtil.closeQuietly(secondChannel);
      throw e;
    }
  }

  @Override
  public RtpDataChannel.Factory createFallbackDataChannelFactory() {
    return new TransferRtpDataChannelFactory(/* timeoutMs= */ socketTimeoutMs);
  }
}
