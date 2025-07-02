/*
 *  Copyright 2023 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import androidx.annotation.Nullable;
import java.util.List;
import java.util.Map;
import org.webrtc.MediaStreamTrack;

public class RtpCapabilities {
  public static class CodecCapability {
    public int preferredPayloadType;
    // Name used to identify the codec. Equivalent to MIME subtype.
    public String name;
    // The media type of this codec. Equivalent to MIME top-level type.
    public MediaStreamTrack.MediaType kind;
    // Clock rate in Hertz.
    public Integer clockRate;
    // The number of audio channels used. Set to null for video codecs.
    public Integer numChannels;
    // The "format specific parameters" field from the "a=fmtp" line in the SDP
    public Map<String, String> parameters;
    // The MIME type of the codec. This is a convenience field.
    public String mimeType;

    public CodecCapability() {}

    @CalledByNative("CodecCapability")
    CodecCapability(int preferredPayloadType, String name, MediaStreamTrack.MediaType kind,
        Integer clockRate, Integer numChannels, String mimeType, Map<String, String> parameters) {
      this.preferredPayloadType = preferredPayloadType;
      this.name = name;
      this.kind = kind;
      this.clockRate = clockRate;
      this.numChannels = numChannels;
      this.parameters = parameters;
      this.mimeType = mimeType;
    }

    @CalledByNative("CodecCapability")
    int getPreferredPayloadType() {
      return preferredPayloadType;
    }

    @CalledByNative("CodecCapability")
    String getName() {
      return name;
    }

    @CalledByNative("CodecCapability")
    MediaStreamTrack.MediaType getKind() {
      return kind;
    }

    @CalledByNative("CodecCapability")
    Integer getClockRate() {
      return clockRate;
    }

    @CalledByNative("CodecCapability")
    Integer getNumChannels() {
      return numChannels;
    }

    @CalledByNative("CodecCapability")
    Map getParameters() {
      return parameters;
    }
  }

  public static class HeaderExtensionCapability {
    private final String uri;
    private final int preferredId;
    private final boolean preferredEncrypted;

    @CalledByNative("HeaderExtensionCapability")
    HeaderExtensionCapability(String uri, int preferredId, boolean preferredEncrypted) {
      this.uri = uri;
      this.preferredId = preferredId;
      this.preferredEncrypted = preferredEncrypted;
    }

    @CalledByNative("HeaderExtensionCapability")
    public String getUri() {
      return uri;
    }

    @CalledByNative("HeaderExtensionCapability")
    public int getPreferredId() {
      return preferredId;
    }

    @CalledByNative("HeaderExtensionCapability")
    public boolean getPreferredEncrypted() {
      return preferredEncrypted;
    }
  }

  public List<CodecCapability> codecs;
  public List<HeaderExtensionCapability> headerExtensions;

  @CalledByNative
  RtpCapabilities(List<CodecCapability> codecs, List<HeaderExtensionCapability> headerExtensions) {
    this.headerExtensions = headerExtensions;
    this.codecs = codecs;
  }

  @CalledByNative
  public List<HeaderExtensionCapability> getHeaderExtensions() {
    return headerExtensions;
  }

  @CalledByNative
  List<CodecCapability> getCodecs() {
    return codecs;
  }
}
