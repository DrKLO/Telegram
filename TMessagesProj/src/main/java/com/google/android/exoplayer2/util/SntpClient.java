/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.util;

import android.os.SystemClock;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.Loader.LoadErrorAction;
import com.google.android.exoplayer2.upstream.Loader.Loadable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.ConcurrentModificationException;

/**
 * Static utility to retrieve the device time offset using SNTP.
 *
 * <p>Based on the <a
 * href="https://cs.android.com/android/_/android/platform/frameworks/base/+/ea1b85a5eb6e9dcddf7abc6c74479bfadf9017c7:core/java/android/net/SntpClient.java">Android
 * framework SntpClient</a>.
 */
public final class SntpClient {

  /** The default NTP host address used to retrieve {@link #getElapsedRealtimeOffsetMs()}. */
  public static final String DEFAULT_NTP_HOST = "time.android.com";

  /** Callback for calls to {@link #initialize(Loader, InitializationCallback)}. */
  public interface InitializationCallback {

    /** Called when the device time offset has been initialized. */
    void onInitialized();

    /**
     * Called when the device time offset failed to initialize.
     *
     * @param error The error that caused the initialization failure.
     */
    void onInitializationFailed(IOException error);
  }

  private static final int TIMEOUT_MS = 10_000;

  private static final int ORIGINATE_TIME_OFFSET = 24;
  private static final int RECEIVE_TIME_OFFSET = 32;
  private static final int TRANSMIT_TIME_OFFSET = 40;
  private static final int NTP_PACKET_SIZE = 48;

  private static final int NTP_PORT = 123;
  private static final int NTP_MODE_CLIENT = 3;
  private static final int NTP_MODE_SERVER = 4;
  private static final int NTP_MODE_BROADCAST = 5;
  private static final int NTP_VERSION = 3;

  private static final int NTP_LEAP_NOSYNC = 3;
  private static final int NTP_STRATUM_DEATH = 0;
  private static final int NTP_STRATUM_MAX = 15;

  private static final long OFFSET_1900_TO_1970 = ((365L * 70L) + 17L) * 24L * 60L * 60L;

  private static final Object loaderLock = new Object();
  private static final Object valueLock = new Object();

  @GuardedBy("valueLock")
  private static boolean isInitialized;

  @GuardedBy("valueLock")
  private static long elapsedRealtimeOffsetMs;

  @GuardedBy("valueLock")
  private static String ntpHost = DEFAULT_NTP_HOST;

  private SntpClient() {}

  /** Returns the NTP host address used to retrieve {@link #getElapsedRealtimeOffsetMs()}. */
  public static String getNtpHost() {
    synchronized (valueLock) {
      return ntpHost;
    }
  }

  /**
   * Sets the NTP host address used to retrieve {@link #getElapsedRealtimeOffsetMs()}.
   *
   * <p>The default is {@link #DEFAULT_NTP_HOST}.
   *
   * <p>If the new host address is different from the previous one, the NTP client will be {@link
   * #isInitialized()} uninitialized} again.
   *
   * @param ntpHost The NTP host address.
   */
  public static void setNtpHost(String ntpHost) {
    synchronized (valueLock) {
      if (!SntpClient.ntpHost.equals(ntpHost)) {
        SntpClient.ntpHost = ntpHost;
        isInitialized = false;
      }
    }
  }

  /**
   * Returns whether the device time offset has already been loaded.
   *
   * <p>If {@code false}, use {@link #initialize(Loader, InitializationCallback)} to start the
   * initialization.
   */
  public static boolean isInitialized() {
    synchronized (valueLock) {
      return isInitialized;
    }
  }

  /**
   * Returns the offset between {@link SystemClock#elapsedRealtime()} and the NTP server time in
   * milliseconds, or {@link C#TIME_UNSET} if {@link #isInitialized()} returns false.
   *
   * <p>The offset is calculated as {@code ntpServerTime - deviceElapsedRealTime}.
   */
  public static long getElapsedRealtimeOffsetMs() {
    synchronized (valueLock) {
      return isInitialized ? elapsedRealtimeOffsetMs : C.TIME_UNSET;
    }
  }

  /**
   * Starts loading the device time offset.
   *
   * @param loader A {@link Loader} to use for loading the time offset, or null to create a new one.
   * @param callback An optional {@link InitializationCallback} to be notified when the time offset
   *     has been initialized or initialization failed.
   */
  public static void initialize(
      @Nullable Loader loader, @Nullable InitializationCallback callback) {
    if (isInitialized()) {
      if (callback != null) {
        callback.onInitialized();
      }
      return;
    }
    if (loader == null) {
      loader = new Loader("SntpClient");
    }
    loader.startLoading(
        new NtpTimeLoadable(), new NtpTimeCallback(callback), /* defaultMinRetryCount= */ 1);
  }

  private static long loadNtpTimeOffsetMs() throws IOException {
    InetAddress address = InetAddress.getByName(getNtpHost());
    try (DatagramSocket socket = new DatagramSocket()) {
      socket.setSoTimeout(TIMEOUT_MS);
      byte[] buffer = new byte[NTP_PACKET_SIZE];
      DatagramPacket request = new DatagramPacket(buffer, buffer.length, address, NTP_PORT);

      // Set mode = 3 (client) and version = 3. Mode is in low 3 bits of the first byte and Version
      // is in bits 3-5 of the first byte.
      buffer[0] = NTP_MODE_CLIENT | (NTP_VERSION << 3);

      // Get current time and write it to the request packet.
      long requestTime = System.currentTimeMillis();
      long requestTicks = SystemClock.elapsedRealtime();
      writeTimestamp(buffer, TRANSMIT_TIME_OFFSET, requestTime);

      socket.send(request);

      // Read the response.
      DatagramPacket response = new DatagramPacket(buffer, buffer.length);
      socket.receive(response);
      final long responseTicks = SystemClock.elapsedRealtime();
      final long responseTime = requestTime + (responseTicks - requestTicks);

      // Extract the results.
      final byte leap = (byte) ((buffer[0] >> 6) & 0x3);
      final byte mode = (byte) (buffer[0] & 0x7);
      final int stratum = (int) (buffer[1] & 0xff);
      final long originateTime = readTimestamp(buffer, ORIGINATE_TIME_OFFSET);
      final long receiveTime = readTimestamp(buffer, RECEIVE_TIME_OFFSET);
      final long transmitTime = readTimestamp(buffer, TRANSMIT_TIME_OFFSET);

      // Check server reply validity according to RFC.
      checkValidServerReply(leap, mode, stratum, transmitTime);

      // receiveTime = originateTime + transit + skew
      // responseTime = transmitTime + transit - skew
      // clockOffset = ((receiveTime - originateTime) + (transmitTime - responseTime))/2
      //             = ((originateTime + transit + skew - originateTime) +
      //                (transmitTime - (transmitTime + transit - skew)))/2
      //             = ((transit + skew) + (transmitTime - transmitTime - transit + skew))/2
      //             = (transit + skew - transit + skew)/2
      //             = (2 * skew)/2 = skew
      long clockOffset = ((receiveTime - originateTime) + (transmitTime - responseTime)) / 2;

      // Save our results using the times on this side of the network latency (i.e. response rather
      // than request time)
      long ntpTime = responseTime + clockOffset;
      long ntpTimeReference = responseTicks;

      return ntpTime - ntpTimeReference;
    }
  }

  private static long readTimestamp(byte[] buffer, int offset) {
    long seconds = read32(buffer, offset);
    long fraction = read32(buffer, offset + 4);
    // Special case: zero means zero.
    if (seconds == 0 && fraction == 0) {
      return 0;
    }
    return ((seconds - OFFSET_1900_TO_1970) * 1000) + ((fraction * 1000L) / 0x100000000L);
  }

  private static void writeTimestamp(byte[] buffer, int offset, long time) {
    // Special case: zero means zero.
    if (time == 0) {
      Arrays.fill(buffer, offset, offset + 8, (byte) 0x00);
      return;
    }

    long seconds = time / 1000L;
    long milliseconds = time - seconds * 1000L;
    seconds += OFFSET_1900_TO_1970;

    // Write seconds in big endian format.
    buffer[offset++] = (byte) (seconds >> 24);
    buffer[offset++] = (byte) (seconds >> 16);
    buffer[offset++] = (byte) (seconds >> 8);
    buffer[offset++] = (byte) (seconds >> 0);

    long fraction = milliseconds * 0x100000000L / 1000L;
    // Write fraction in big endian format.
    buffer[offset++] = (byte) (fraction >> 24);
    buffer[offset++] = (byte) (fraction >> 16);
    buffer[offset++] = (byte) (fraction >> 8);
    // Low order bits should be random data.
    buffer[offset++] = (byte) (Math.random() * 255.0);
  }

  private static long read32(byte[] buffer, int offset) {
    byte b0 = buffer[offset];
    byte b1 = buffer[offset + 1];
    byte b2 = buffer[offset + 2];
    byte b3 = buffer[offset + 3];

    // Convert signed bytes to unsigned values.
    int i0 = ((b0 & 0x80) == 0x80 ? (b0 & 0x7F) + 0x80 : b0);
    int i1 = ((b1 & 0x80) == 0x80 ? (b1 & 0x7F) + 0x80 : b1);
    int i2 = ((b2 & 0x80) == 0x80 ? (b2 & 0x7F) + 0x80 : b2);
    int i3 = ((b3 & 0x80) == 0x80 ? (b3 & 0x7F) + 0x80 : b3);

    return ((long) i0 << 24) + ((long) i1 << 16) + ((long) i2 << 8) + (long) i3;
  }

  private static void checkValidServerReply(byte leap, byte mode, int stratum, long transmitTime)
      throws IOException {
    if (leap == NTP_LEAP_NOSYNC) {
      throw new IOException("SNTP: Unsynchronized server");
    }
    if ((mode != NTP_MODE_SERVER) && (mode != NTP_MODE_BROADCAST)) {
      throw new IOException("SNTP: Untrusted mode: " + mode);
    }
    if ((stratum == NTP_STRATUM_DEATH) || (stratum > NTP_STRATUM_MAX)) {
      throw new IOException("SNTP: Untrusted stratum: " + stratum);
    }
    if (transmitTime == 0) {
      throw new IOException("SNTP: Zero transmitTime");
    }
  }

  private static final class NtpTimeLoadable implements Loadable {

    @Override
    public void cancelLoad() {}

    @Override
    public void load() throws IOException {
      // Synchronized to prevent redundant parallel requests.
      synchronized (loaderLock) {
        synchronized (valueLock) {
          if (isInitialized) {
            return;
          }
        }
        long offsetMs = loadNtpTimeOffsetMs();
        synchronized (valueLock) {
          elapsedRealtimeOffsetMs = offsetMs;
          isInitialized = true;
        }
      }
    }
  }

  private static final class NtpTimeCallback implements Loader.Callback<Loadable> {

    @Nullable private final InitializationCallback callback;

    public NtpTimeCallback(@Nullable InitializationCallback callback) {
      this.callback = callback;
    }

    @Override
    public void onLoadCompleted(Loadable loadable, long elapsedRealtimeMs, long loadDurationMs) {
      if (callback != null) {
        if (!SntpClient.isInitialized()) {
          // This may happen in the unlikely edge case of someone calling setNtpHost between the end
          // of the load method and this callback.
          callback.onInitializationFailed(new IOException(new ConcurrentModificationException()));
        } else {
          callback.onInitialized();
        }
      }
    }

    @Override
    public void onLoadCanceled(
        Loadable loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released) {
      // Ignore.
    }

    @Override
    public LoadErrorAction onLoadError(
        Loadable loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        IOException error,
        int errorCount) {
      if (callback != null) {
        callback.onInitializationFailed(error);
      }
      return Loader.DONT_RETRY;
    }
  }
}
