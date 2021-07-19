/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import android.opengl.EGLContext;
import android.view.Surface;

/** EGL 1.4 implementation of EglBase. */
public interface EglBase14 extends EglBase {

  interface Context extends EglBase.Context {
    EGLContext getRawContext();
  }
}
