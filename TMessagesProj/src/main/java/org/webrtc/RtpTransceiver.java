/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.webrtc.MediaStreamTrack;
import org.webrtc.RtpParameters;

/**
 * Java wrapper for a C++ RtpTransceiverInterface.
 *
 * <p>The RTCRtpTransceiver maps to the RTCRtpTransceiver defined by the WebRTC
 * specification. A transceiver represents a combination of an RTCRtpSender
 * and an RTCRtpReceiver that share a common mid. As defined in JSEP, an
 * RTCRtpTransceiver is said to be associated with a media description if its
 * mid property is non-nil; otherwise, it is said to be disassociated.
 * JSEP: https://tools.ietf.org/html/draft-ietf-rtcweb-jsep-24
 *
 * <p>Note that RTCRtpTransceivers are only supported when using
 * RTCPeerConnection with Unified Plan SDP.
 *
 * <p>WebRTC specification for RTCRtpTransceiver, the JavaScript analog:
 * https://w3c.github.io/webrtc-pc/#dom-rtcrtptransceiver
 */
public class RtpTransceiver {
  /** Java version of webrtc::RtpTransceiverDirection - the ordering must be kept in sync. */
  public enum RtpTransceiverDirection {
    SEND_RECV(0),
    SEND_ONLY(1),
    RECV_ONLY(2),
    INACTIVE(3);

    private final int nativeIndex;

    private RtpTransceiverDirection(int nativeIndex) {
      this.nativeIndex = nativeIndex;
    }

    @CalledByNative("RtpTransceiverDirection")
    int getNativeIndex() {
      return nativeIndex;
    }

    @CalledByNative("RtpTransceiverDirection")
    static RtpTransceiverDirection fromNativeIndex(int nativeIndex) {
      for (RtpTransceiverDirection type : RtpTransceiverDirection.values()) {
        if (type.getNativeIndex() == nativeIndex) {
          return type;
        }
      }
      throw new IllegalArgumentException(
          "Uknown native RtpTransceiverDirection type" + nativeIndex);
    }
  }

  /**
   * Tracks webrtc::RtpTransceiverInit. https://w3c.github.io/webrtc-pc/#dom-rtcrtptransceiverinit
   * A structure for initializing an RtpTransceiver in a call to addTransceiver.
   * Note: This does not contain a list of encoding parameters, because they are currently
   * not being used natively.
   */
  public static final class RtpTransceiverInit {
    private final RtpTransceiverDirection direction;
    private final List<String> streamIds;
    private final List<RtpParameters.Encoding> sendEncodings;

    public RtpTransceiverInit() {
      this(RtpTransceiverDirection.SEND_RECV);
    }

    public RtpTransceiverInit(RtpTransceiverDirection direction) {
      this(direction, Collections.emptyList(), Collections.emptyList());
    }

    public RtpTransceiverInit(RtpTransceiverDirection direction, List<String> streamIds) {
      this(direction, streamIds, Collections.emptyList());
    }

    public RtpTransceiverInit(RtpTransceiverDirection direction, List<String> streamIds,
        List<RtpParameters.Encoding> sendEncodings) {
      this.direction = direction;
      this.streamIds = new ArrayList<String>(streamIds);
      this.sendEncodings = new ArrayList<RtpParameters.Encoding>(sendEncodings);
    }

    @CalledByNative("RtpTransceiverInit")
    int getDirectionNativeIndex() {
      return direction.getNativeIndex();
    }

    @CalledByNative("RtpTransceiverInit")
    List<String> getStreamIds() {
      return new ArrayList<String>(this.streamIds);
    }

    @CalledByNative("RtpTransceiverInit")
    List<RtpParameters.Encoding> getSendEncodings() {
      return new ArrayList<RtpParameters.Encoding>(this.sendEncodings);
    }
  }

  private long nativeRtpTransceiver;
  private RtpSender cachedSender;
  private RtpReceiver cachedReceiver;

  @CalledByNative
  protected RtpTransceiver(long nativeRtpTransceiver) {
    this.nativeRtpTransceiver = nativeRtpTransceiver;
    cachedSender = nativeGetSender(nativeRtpTransceiver);
    cachedReceiver = nativeGetReceiver(nativeRtpTransceiver);
  }

  /**
   * Media type of the transceiver. Any sender(s)/receiver(s) will have this
   * type as well.
   */
  public MediaStreamTrack.MediaType getMediaType() {
    checkRtpTransceiverExists();
    return nativeGetMediaType(nativeRtpTransceiver);
  }

  /**
   * The mid attribute is the mid negotiated and present in the local and
   * remote descriptions. Before negotiation is complete, the mid value may be
   * null. After rollbacks, the value may change from a non-null value to null.
   * https://w3c.github.io/webrtc-pc/#dom-rtcrtptransceiver-mid
   */
  public String getMid() {
    checkRtpTransceiverExists();
    return nativeGetMid(nativeRtpTransceiver);
  }

  /**
   * The sender attribute exposes the RtpSender corresponding to the RTP media
   * that may be sent with the transceiver's mid. The sender is always present,
   * regardless of the direction of media.
   * https://w3c.github.io/webrtc-pc/#dom-rtcrtptransceiver-sender
   */
  public RtpSender getSender() {
    return cachedSender;
  }

  /**
   * The receiver attribute exposes the RtpReceiver corresponding to the RTP
   * media that may be received with the transceiver's mid. The receiver is
   * always present, regardless of the direction of media.
   * https://w3c.github.io/webrtc-pc/#dom-rtcrtptransceiver-receiver
   */
  public RtpReceiver getReceiver() {
    return cachedReceiver;
  }

  /**
   * The stopped attribute indicates that the sender of this transceiver will no
   * longer send, and that the receiver will no longer receive. It is true if
   * either stop has been called or if setting the local or remote description
   * has caused the RtpTransceiver to be stopped.
   * https://w3c.github.io/webrtc-pc/#dom-rtcrtptransceiver-stopped
   */
  public boolean isStopped() {
    checkRtpTransceiverExists();
    return nativeStopped(nativeRtpTransceiver);
  }

  /**
   * The direction attribute indicates the preferred direction of this
   * transceiver, which will be used in calls to CreateOffer and CreateAnswer.
   * https://w3c.github.io/webrtc-pc/#dom-rtcrtptransceiver-direction
   */
  public RtpTransceiverDirection getDirection() {
    checkRtpTransceiverExists();
    return nativeDirection(nativeRtpTransceiver);
  }

  /**
   * The current_direction attribute indicates the current direction negotiated
   * for this transceiver. If this transceiver has never been represented in an
   * offer/answer exchange, or if the transceiver is stopped, the value is null.
   * https://w3c.github.io/webrtc-pc/#dom-rtcrtptransceiver-currentdirection
   */
  public RtpTransceiverDirection getCurrentDirection() {
    checkRtpTransceiverExists();
    return nativeCurrentDirection(nativeRtpTransceiver);
  }

  /**
   * Sets the preferred direction of this transceiver. An update of
   * directionality does not take effect immediately. Instead, future calls to
   * CreateOffer and CreateAnswer mark the corresponding media descriptions as
   * sendrecv, sendonly, recvonly, or inactive.
   * https://w3c.github.io/webrtc-pc/#dom-rtcrtptransceiver-direction
   */
  public boolean setDirection(RtpTransceiverDirection rtpTransceiverDirection) {
    checkRtpTransceiverExists();
    return nativeSetDirection(nativeRtpTransceiver, rtpTransceiverDirection);
  }

  /**
   * The Stop method will for the time being call the StopInternal method.
   * After a migration procedure, stop() will be equivalent to StopStandard.
   */
  public void stop() {
    checkRtpTransceiverExists();
    nativeStopInternal(nativeRtpTransceiver);
  }

  /**
   * The StopInternal method stops the RtpTransceiver, like Stop, but goes
   * immediately to Stopped state.
   */
  public void stopInternal() {
    checkRtpTransceiverExists();
    nativeStopInternal(nativeRtpTransceiver);
  }

  /**
   * The StopStandard method irreversibly stops the RtpTransceiver. The sender
   * of this transceiver will no longer send, the receiver will no longer
   * receive.
   *
   * <p>The transceiver will enter Stopping state and signal NegotiationNeeded.
   * https://w3c.github.io/webrtc-pc/#dom-rtcrtptransceiver-stop
   */
  public void stopStandard() {
    checkRtpTransceiverExists();
    nativeStopStandard(nativeRtpTransceiver);
  }

  @CalledByNative
  public void dispose() {
    checkRtpTransceiverExists();
    cachedSender.dispose();
    cachedReceiver.dispose();
    JniCommon.nativeReleaseRef(nativeRtpTransceiver);
    nativeRtpTransceiver = 0;
  }

  private void checkRtpTransceiverExists() {
    if (nativeRtpTransceiver == 0) {
      throw new IllegalStateException("RtpTransceiver has been disposed.");
    }
  }

  private static native MediaStreamTrack.MediaType nativeGetMediaType(long rtpTransceiver);
  private static native String nativeGetMid(long rtpTransceiver);
  private static native RtpSender nativeGetSender(long rtpTransceiver);
  private static native RtpReceiver nativeGetReceiver(long rtpTransceiver);
  private static native boolean nativeStopped(long rtpTransceiver);
  private static native RtpTransceiverDirection nativeDirection(long rtpTransceiver);
  private static native RtpTransceiverDirection nativeCurrentDirection(long rtpTransceiver);
  private static native void nativeStopInternal(long rtpTransceiver);
  private static native void nativeStopStandard(long rtpTransceiver);
  private static native boolean nativeSetDirection(
      long rtpTransceiver, RtpTransceiverDirection rtpTransceiverDirection);
}
