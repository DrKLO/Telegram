/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import org.webrtc.Logging.Severity;

/**
 * Java interface for WebRTC logging. The default implementation uses webrtc.Logging.
 *
 * When injected, the Loggable will receive logging from both Java and native.
 */
public interface Loggable {
  public void onLogMessage(String message, Severity severity, String tag);
}
