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

import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SoftwareVideoDecoderFactory implements VideoDecoderFactory {
  @Deprecated
  @Nullable
  @Override
  public VideoDecoder createDecoder(String codecType) {
    return createDecoder(new VideoCodecInfo(codecType, new HashMap<>()));
  }

  @Nullable
  @Override
  public VideoDecoder createDecoder(VideoCodecInfo codecType) {
    if (codecType.getName().equalsIgnoreCase("VP8")) {
      return new LibvpxVp8Decoder();
    }
    if (codecType.getName().equalsIgnoreCase("VP9") && LibvpxVp9Decoder.nativeIsSupported()) {
      return new LibvpxVp9Decoder();
    }
    if (codecType.getName().equalsIgnoreCase("H264")) {
      return new OpenH264Decoder();
    }

    return null;
  }

  @Override
  public VideoCodecInfo[] getSupportedCodecs() {
    return supportedCodecs();
  }

  static VideoCodecInfo[] supportedCodecs() {
    List<VideoCodecInfo> codecs = new ArrayList<VideoCodecInfo>();

    codecs.add(new VideoCodecInfo("VP8", new HashMap<>()));
    if (LibvpxVp9Decoder.nativeIsSupported()) {
      codecs.add(new VideoCodecInfo("VP9", new HashMap<>()));
    }
    codecs.add(new VideoCodecInfo("H264", new HashMap<>()));

    return codecs.toArray(new VideoCodecInfo[codecs.size()]);
  }
}
