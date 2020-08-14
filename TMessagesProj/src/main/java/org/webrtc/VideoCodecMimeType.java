/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

/** Enumeration of supported video codec types. */
enum VideoCodecMimeType {
  VP8("video/x-vnd.on2.vp8"),
  VP9("video/x-vnd.on2.vp9"),
  H264("video/avc"),
  H265("video/hevc");

  private final String mimeType;

  private VideoCodecMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  String mimeType() {
    return mimeType;
  }
}
