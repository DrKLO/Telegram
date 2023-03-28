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

import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.rtsp.RtspMessageChannel.InterleavedBinaryDataListener;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Util;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

/** An {@link RtpDataChannel} that transfers received data in-memory. */
/* package */ final class TransferRtpDataChannel extends BaseDataSource
    implements RtpDataChannel, RtspMessageChannel.InterleavedBinaryDataListener {

  private static final String DEFAULT_TCP_TRANSPORT_FORMAT =
      "RTP/AVP/TCP;unicast;interleaved=%d-%d";

  private final LinkedBlockingQueue<byte[]> packetQueue;
  private final long pollTimeoutMs;

  private byte[] unreadData;
  private int channelNumber;

  /**
   * Creates a new instance.
   *
   * @param pollTimeoutMs The number of milliseconds which {@link #read} waits for a packet to be
   *     available. After the time has expired, {@link C#RESULT_END_OF_INPUT} is returned.
   */
  public TransferRtpDataChannel(long pollTimeoutMs) {
    super(/* isNetwork= */ true);
    this.pollTimeoutMs = pollTimeoutMs;
    packetQueue = new LinkedBlockingQueue<>();
    unreadData = new byte[0];
    channelNumber = C.INDEX_UNSET;
  }

  @Override
  public String getTransport() {
    checkState(channelNumber != C.INDEX_UNSET); // Assert open() is called.
    return Util.formatInvariant(DEFAULT_TCP_TRANSPORT_FORMAT, channelNumber, channelNumber + 1);
  }

  @Override
  public int getLocalPort() {
    return channelNumber;
  }

  @Override
  public InterleavedBinaryDataListener getInterleavedBinaryDataListener() {
    return this;
  }

  @Override
  public long open(DataSpec dataSpec) {
    this.channelNumber = dataSpec.uri.getPort();
    return C.LENGTH_UNSET;
  }

  @Override
  public void close() {}

  @Nullable
  @Override
  public Uri getUri() {
    return null;
  }

  @Override
  public int read(byte[] buffer, int offset, int length) {
    if (length == 0) {
      return 0;
    }

    int bytesRead = 0;
    int bytesToRead = min(length, unreadData.length);
    System.arraycopy(unreadData, /* srcPos= */ 0, buffer, offset, bytesToRead);
    bytesRead += bytesToRead;
    unreadData = Arrays.copyOfRange(unreadData, bytesToRead, unreadData.length);

    if (bytesRead == length) {
      return bytesRead;
    }

    @Nullable byte[] data;
    try {
      data = packetQueue.poll(pollTimeoutMs, MILLISECONDS);
      if (data == null) {
        return C.RESULT_END_OF_INPUT;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return C.RESULT_END_OF_INPUT;
    }

    bytesToRead = min(length - bytesRead, data.length);
    System.arraycopy(data, /* srcPos= */ 0, buffer, offset + bytesRead, bytesToRead);
    if (bytesToRead < data.length) {
      unreadData = Arrays.copyOfRange(data, bytesToRead, data.length);
    }
    return bytesRead + bytesToRead;
  }

  @Override
  public void onInterleavedBinaryDataReceived(byte[] data) {
    packetQueue.add(data);
  }
}
