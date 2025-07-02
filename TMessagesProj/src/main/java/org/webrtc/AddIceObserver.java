/*
 *  Copyright 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

/** Interface to handle completion of addIceCandidate  */
public interface AddIceObserver {
  /** Called when ICE candidate added successfully.*/
  @CalledByNative public void onAddSuccess();

  /** Called when ICE candidate addition failed.*/
  @CalledByNative public void onAddFailure(String error);
}
