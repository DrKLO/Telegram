/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

public final class IceCandidateErrorEvent {
  /** The local IP address used to communicate with the STUN or TURN server. */
  public final String address;
  /** The port used to communicate with the STUN or TURN server. */
  public final int port;
  /**
   * The STUN or TURN URL that identifies the STUN or TURN server for which the failure occurred.
   */
  public final String url;
  /**
   * The numeric STUN error code returned by the STUN or TURN server. If no host candidate can reach
   * the server, errorCode will be set to the value 701 which is outside the STUN error code range.
   * This error is only fired once per server URL while in the RTCIceGatheringState of "gathering".
   */
  public final int errorCode;
  /**
   * The STUN reason text returned by the STUN or TURN server. If the server could not be reached,
   * errorText will be set to an implementation-specific value providing details about the error.
   */
  public final String errorText;

  @CalledByNative
  public IceCandidateErrorEvent(
      String address, int port, String url, int errorCode, String errorText) {
    this.address = address;
    this.port = port;
    this.url = url;
    this.errorCode = errorCode;
    this.errorText = errorText;
  }
}
