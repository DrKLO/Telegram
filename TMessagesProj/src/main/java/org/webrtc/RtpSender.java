/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import androidx.annotation.Nullable;
import java.util.List;
import org.webrtc.MediaStreamTrack;

/** Java wrapper for a C++ RtpSenderInterface. */
public class RtpSender {
  private long nativeRtpSender;

  @Nullable private MediaStreamTrack cachedTrack;
  private boolean ownsTrack = true;
  private final @Nullable DtmfSender dtmfSender;

  @CalledByNative
  public RtpSender(long nativeRtpSender) {
    this.nativeRtpSender = nativeRtpSender;
    long nativeTrack = nativeGetTrack(nativeRtpSender);
    cachedTrack = MediaStreamTrack.createMediaStreamTrack(nativeTrack);

    if (nativeGetMediaType(nativeRtpSender).equalsIgnoreCase(MediaStreamTrack.AUDIO_TRACK_KIND)) {
      long nativeDtmfSender = nativeGetDtmfSender(nativeRtpSender);
      dtmfSender = (nativeDtmfSender != 0) ? new DtmfSender(nativeDtmfSender) : null;
    } else {
      dtmfSender = null;
    }
  }

  /**
   * Starts sending a new track, without requiring additional SDP negotiation.
   * <p>
   * Note: This is equivalent to replaceTrack in the official WebRTC API. It
   * was just implemented before the standards group settled on a name.
   *
   * @param takeOwnership If true, the RtpSender takes ownership of the track
   *                      from the caller, and will auto-dispose of it when no
   *                      longer needed. `takeOwnership` should only be used if
   *                      the caller owns the track; it is not appropriate when
   *                      the track is owned by, for example, another RtpSender
   *                      or a MediaStream.
   * @return              true on success and false on failure.
   */
  public boolean setTrack(@Nullable MediaStreamTrack track, boolean takeOwnership) {
    checkRtpSenderExists();
    if (!nativeSetTrack(nativeRtpSender, (track == null) ? 0 : track.getNativeMediaStreamTrack())) {
      return false;
    }
    if (cachedTrack != null && ownsTrack) {
      cachedTrack.dispose();
    }
    cachedTrack = track;
    ownsTrack = takeOwnership;
    return true;
  }

  @Nullable
  public MediaStreamTrack track() {
    return cachedTrack;
  }

  public void setStreams(List<String> streamIds) {
    checkRtpSenderExists();
    nativeSetStreams(nativeRtpSender, streamIds);
  }

  public List<String> getStreams() {
    checkRtpSenderExists();
    return nativeGetStreams(nativeRtpSender);
  }

  public boolean setParameters(RtpParameters parameters) {
    checkRtpSenderExists();
    return nativeSetParameters(nativeRtpSender, parameters);
  }

  public RtpParameters getParameters() {
    checkRtpSenderExists();
    return nativeGetParameters(nativeRtpSender);
  }

  public String id() {
    checkRtpSenderExists();
    return nativeGetId(nativeRtpSender);
  }

  @Nullable
  public DtmfSender dtmf() {
    return dtmfSender;
  }

  public void setFrameEncryptor(FrameEncryptor frameEncryptor) {
    checkRtpSenderExists();
    nativeSetFrameEncryptor(nativeRtpSender, frameEncryptor.getNativeFrameEncryptor());
  }

  public void dispose() {
    checkRtpSenderExists();
    if (dtmfSender != null) {
      dtmfSender.dispose();
    }
    if (cachedTrack != null && ownsTrack) {
      cachedTrack.dispose();
    }
    JniCommon.nativeReleaseRef(nativeRtpSender);
    nativeRtpSender = 0;
  }

  /** Returns a pointer to webrtc::RtpSenderInterface. */
  long getNativeRtpSender() {
    checkRtpSenderExists();
    return nativeRtpSender;
  }

  private void checkRtpSenderExists() {
    if (nativeRtpSender == 0) {
      throw new IllegalStateException("RtpSender has been disposed.");
    }
  }

  private static native boolean nativeSetTrack(long rtpSender, long nativeTrack);

  // This should increment the reference count of the track.
  // Will be released in dispose() or setTrack().
  private static native long nativeGetTrack(long rtpSender);

  private static native void nativeSetStreams(long rtpSender, List<String> streamIds);

  private static native List<String> nativeGetStreams(long rtpSender);

  // This should increment the reference count of the DTMF sender.
  // Will be released in dispose().
  private static native long nativeGetDtmfSender(long rtpSender);

  private static native boolean nativeSetParameters(long rtpSender, RtpParameters parameters);

  private static native RtpParameters nativeGetParameters(long rtpSender);

  private static native String nativeGetId(long rtpSender);

  private static native void nativeSetFrameEncryptor(long rtpSender, long nativeFrameEncryptor);

  private static native String nativeGetMediaType(long rtpSender);
};
