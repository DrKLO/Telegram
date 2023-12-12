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
package com.google.android.exoplayer2.upstream;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.Math.min;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;

/** A UDP {@link DataSource}. */
public final class UdpDataSource extends BaseDataSource {

  /** Thrown when an error is encountered when trying to read from a {@link UdpDataSource}. */
  public static final class UdpDataSourceException extends DataSourceException {

    /**
     * Creates a {@code UdpDataSourceException}.
     *
     * @param cause The error cause.
     * @param errorCode Reason of the error, should be one of the {@code ERROR_CODE_IO_*} in {@link
     *     PlaybackException.ErrorCode}.
     */
    public UdpDataSourceException(Throwable cause, @PlaybackException.ErrorCode int errorCode) {
      super(cause, errorCode);
    }
  }

  /** The default maximum datagram packet size, in bytes. */
  public static final int DEFAULT_MAX_PACKET_SIZE = 2000;

  /** The default socket timeout, in milliseconds. */
  public static final int DEFAULT_SOCKET_TIMEOUT_MILLIS = 8 * 1000;

  public static final int UDP_PORT_UNSET = -1;

  private final int socketTimeoutMillis;
  private final byte[] packetBuffer;
  private final DatagramPacket packet;

  @Nullable private Uri uri;
  @Nullable private DatagramSocket socket;
  @Nullable private MulticastSocket multicastSocket;
  @Nullable private InetAddress address;
  private boolean opened;

  private int packetRemaining;

  public UdpDataSource() {
    this(DEFAULT_MAX_PACKET_SIZE);
  }

  /**
   * Constructs a new instance.
   *
   * @param maxPacketSize The maximum datagram packet size, in bytes.
   */
  public UdpDataSource(int maxPacketSize) {
    this(maxPacketSize, DEFAULT_SOCKET_TIMEOUT_MILLIS);
  }

  /**
   * Constructs a new instance.
   *
   * @param maxPacketSize The maximum datagram packet size, in bytes.
   * @param socketTimeoutMillis The socket timeout in milliseconds. A timeout of zero is interpreted
   *     as an infinite timeout.
   */
  public UdpDataSource(int maxPacketSize, int socketTimeoutMillis) {
    super(/* isNetwork= */ true);
    this.socketTimeoutMillis = socketTimeoutMillis;
    packetBuffer = new byte[maxPacketSize];
    packet = new DatagramPacket(packetBuffer, 0, maxPacketSize);
  }

  @Override
  public long open(DataSpec dataSpec) throws UdpDataSourceException {
    uri = dataSpec.uri;
    String host = checkNotNull(uri.getHost());
    int port = uri.getPort();
    transferInitializing(dataSpec);
    try {
      address = InetAddress.getByName(host);
      InetSocketAddress socketAddress = new InetSocketAddress(address, port);
      if (address.isMulticastAddress()) {
        multicastSocket = new MulticastSocket(socketAddress);
        multicastSocket.joinGroup(address);
        socket = multicastSocket;
      } else {
        socket = new DatagramSocket(socketAddress);
      }
      socket.setSoTimeout(socketTimeoutMillis);
    } catch (SecurityException e) {
      throw new UdpDataSourceException(e, PlaybackException.ERROR_CODE_IO_NO_PERMISSION);
    } catch (IOException e) {
      throw new UdpDataSourceException(
          e, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED);
    }

    opened = true;
    transferStarted(dataSpec);
    return C.LENGTH_UNSET;
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws UdpDataSourceException {
    if (length == 0) {
      return 0;
    }

    if (packetRemaining == 0) {
      // We've read all of the data from the current packet. Get another.
      try {
        checkNotNull(socket).receive(packet);
      } catch (SocketTimeoutException e) {
        throw new UdpDataSourceException(
            e, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT);
      } catch (IOException e) {
        throw new UdpDataSourceException(
            e, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED);
      }
      packetRemaining = packet.getLength();
      bytesTransferred(packetRemaining);
    }

    int packetOffset = packet.getLength() - packetRemaining;
    int bytesToRead = min(packetRemaining, length);
    System.arraycopy(packetBuffer, packetOffset, buffer, offset, bytesToRead);
    packetRemaining -= bytesToRead;
    return bytesToRead;
  }

  @Override
  @Nullable
  public Uri getUri() {
    return uri;
  }

  @Override
  public void close() {
    uri = null;
    if (multicastSocket != null) {
      try {
        multicastSocket.leaveGroup(checkNotNull(address));
      } catch (IOException e) {
        // Do nothing.
      }
      multicastSocket = null;
    }
    if (socket != null) {
      socket.close();
      socket = null;
    }
    address = null;
    packetRemaining = 0;
    if (opened) {
      opened = false;
      transferEnded();
    }
  }

  /**
   * Returns the local port number opened for the UDP connection, or {@link #UDP_PORT_UNSET} if no
   * connection is open
   */
  public int getLocalPort() {
    if (socket == null) {
      return UDP_PORT_UNSET;
    }
    return socket.getLocalPort();
  }
}
