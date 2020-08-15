/*
 *  Copyright 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import android.graphics.Matrix;

interface CameraSession {
  enum FailureType { ERROR, DISCONNECTED }

  // Callbacks are fired on the camera thread.
  interface CreateSessionCallback {
    void onDone(CameraSession session);
    void onFailure(FailureType failureType, String error);
  }

  // Events are fired on the camera thread.
  interface Events {
    void onCameraOpening();
    void onCameraError(CameraSession session, String error);
    void onCameraDisconnected(CameraSession session);
    void onCameraClosed(CameraSession session);
    void onFrameCaptured(CameraSession session, VideoFrame frame);
  }

  /**
   * Stops the capture. Waits until no more calls to capture observer will be made.
   * If waitCameraStop is true, also waits for the camera to stop.
   */
  void stop();

  static VideoFrame.TextureBuffer createTextureBufferWithModifiedTransformMatrix(
      TextureBufferImpl buffer, boolean mirror, int rotation) {
    final Matrix transformMatrix = new Matrix();
    // Perform mirror and rotation around (0.5, 0.5) since that is the center of the texture.
    transformMatrix.preTranslate(/* dx= */ 0.5f, /* dy= */ 0.5f);
    if (mirror) {
      transformMatrix.preScale(/* sx= */ -1f, /* sy= */ 1f);
    }
    transformMatrix.preRotate(rotation);
    transformMatrix.preTranslate(/* dx= */ -0.5f, /* dy= */ -0.5f);

    // The width and height are not affected by rotation since Camera2Session has set them to the
    // value they should be after undoing the rotation.
    return buffer.applyTransformMatrix(transformMatrix, buffer.getWidth(), buffer.getHeight());
  }
}
