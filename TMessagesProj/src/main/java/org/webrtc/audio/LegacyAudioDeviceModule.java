/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc.audio;

import org.webrtc.voiceengine.WebRtcAudioRecord;
import org.webrtc.voiceengine.WebRtcAudioTrack;

/**
 * This class represents the legacy AudioDeviceModule that is currently hardcoded into C++ WebRTC.
 * It will return a null native AudioDeviceModule pointer, leading to an internal object being
 * created inside WebRTC that is controlled by static calls to the classes under the voiceengine
 * package. Please use the new JavaAudioDeviceModule instead of this class.
 */
@Deprecated
public class LegacyAudioDeviceModule implements AudioDeviceModule {
  @Override
  public long getNativeAudioDeviceModulePointer() {
    // Returning a null pointer will make WebRTC construct the built-in legacy AudioDeviceModule for
    // Android internally.
    return 0;
  }

  @Override
  public void release() {
    // All control for this ADM goes through static global methods and the C++ object is owned
    // internally by WebRTC.
  }

  @Override
  public void setSpeakerMute(boolean mute) {
    WebRtcAudioTrack.setSpeakerMute(mute);
  }

  @Override
  public void setMicrophoneMute(boolean mute) {
    WebRtcAudioRecord.setMicrophoneMute(mute);
  }
}
