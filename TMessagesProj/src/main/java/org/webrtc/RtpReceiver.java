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
import org.webrtc.MediaStreamTrack;

/** Java wrapper for a C++ RtpReceiverInterface. */
public class RtpReceiver {
  /** Java wrapper for a C++ RtpReceiverObserverInterface*/
  public static interface Observer {
    // Called when the first audio or video packet is received.
    @CalledByNative("Observer")
    public void onFirstPacketReceived(MediaStreamTrack.MediaType media_type);
  }

  private long nativeRtpReceiver;
  private long nativeObserver;

  @Nullable private MediaStreamTrack cachedTrack;

  @CalledByNative
  public RtpReceiver(long nativeRtpReceiver) {
    this.nativeRtpReceiver = nativeRtpReceiver;
    long nativeTrack = nativeGetTrack(nativeRtpReceiver);
    cachedTrack = MediaStreamTrack.createMediaStreamTrack(nativeTrack);
  }

  @Nullable
  public MediaStreamTrack track() {
    return cachedTrack;
  }

  public RtpParameters getParameters() {
    checkRtpReceiverExists();
    return nativeGetParameters(nativeRtpReceiver);
  }

  public String id() {
    checkRtpReceiverExists();
    return nativeGetId(nativeRtpReceiver);
  }

  /** Returns a pointer to webrtc::RtpReceiverInterface. */
  long getNativeRtpReceiver() {
    checkRtpReceiverExists();
    return nativeRtpReceiver;
  }

  @CalledByNative
  public void dispose() {
    checkRtpReceiverExists();
    cachedTrack.dispose();
    if (nativeObserver != 0) {
      nativeUnsetObserver(nativeRtpReceiver, nativeObserver);
      nativeObserver = 0;
    }
    JniCommon.nativeReleaseRef(nativeRtpReceiver);
    nativeRtpReceiver = 0;
  }

  public void SetObserver(Observer observer) {
    checkRtpReceiverExists();
    // Unset the existing one before setting a new one.
    if (nativeObserver != 0) {
      nativeUnsetObserver(nativeRtpReceiver, nativeObserver);
    }
    nativeObserver = nativeSetObserver(nativeRtpReceiver, observer);
  }

  public void setFrameDecryptor(FrameDecryptor frameDecryptor) {
    checkRtpReceiverExists();
    nativeSetFrameDecryptor(nativeRtpReceiver, frameDecryptor.getNativeFrameDecryptor());
  }

  private void checkRtpReceiverExists() {
    if (nativeRtpReceiver == 0) {
      throw new IllegalStateException("RtpReceiver has been disposed.");
    }
  }

  // This should increment the reference count of the track.
  // Will be released in dispose().
  private static native long nativeGetTrack(long rtpReceiver);
  private static native RtpParameters nativeGetParameters(long rtpReceiver);
  private static native String nativeGetId(long rtpReceiver);
  private static native long nativeSetObserver(long rtpReceiver, Observer observer);
  private static native void nativeUnsetObserver(long rtpReceiver, long nativeObserver);
  private static native void nativeSetFrameDecryptor(long rtpReceiver, long nativeFrameDecryptor);
};
