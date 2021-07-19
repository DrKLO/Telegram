/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_BASE_MEDIA_CONFIG_H_
#define MEDIA_BASE_MEDIA_CONFIG_H_

namespace cricket {

// Construction-time settings, passed on when creating
// MediaChannels.
struct MediaConfig {
  // Set DSCP value on packets. This flag comes from the
  // PeerConnection constraint 'googDscp'.
  bool enable_dscp = false;

  // Video-specific config.
  struct Video {
    // Enable WebRTC CPU Overuse Detection. This flag comes from the
    // PeerConnection constraint 'googCpuOveruseDetection'.
    bool enable_cpu_adaptation = true;

    // Enable WebRTC suspension of video. No video frames will be sent
    // when the bitrate is below the configured minimum bitrate. This
    // flag comes from the PeerConnection constraint
    // 'googSuspendBelowMinBitrate', and WebRtcVideoChannel copies it
    // to VideoSendStream::Config::suspend_below_min_bitrate.
    bool suspend_below_min_bitrate = false;

    // Enable buffering and playout timing smoothing of decoded frames.
    // If set to true, then WebRTC will buffer and potentially drop decoded
    // frames in order to keep a smooth rendering.
    // If set to false, then WebRTC will hand over the frame from the decoder
    // to the renderer as soon as possible, meaning that the renderer is
    // responsible for smooth rendering.
    // Note that even if this flag is set to false, dropping of frames can
    // still happen pre-decode, e.g., dropping of higher temporal layers.
    // This flag comes from the PeerConnection RtcConfiguration.
    bool enable_prerenderer_smoothing = true;

    // Enables periodic bandwidth probing in application-limited region.
    bool periodic_alr_bandwidth_probing = false;

    // Enables the new method to estimate the cpu load from encoding, used for
    // cpu adaptation. This flag is intended to be controlled primarily by a
    // Chrome origin-trial.
    // TODO(bugs.webrtc.org/8504): If all goes well, the flag will be removed
    // together with the old method of estimation.
    bool experiment_cpu_load_estimator = false;

    // Time interval between RTCP report for video
    int rtcp_report_interval_ms = 1000;
  } video;

  // Audio-specific config.
  struct Audio {
    // Time interval between RTCP report for audio
    int rtcp_report_interval_ms = 5000;
  } audio;

  bool operator==(const MediaConfig& o) const {
    return enable_dscp == o.enable_dscp &&
           video.enable_cpu_adaptation == o.video.enable_cpu_adaptation &&
           video.suspend_below_min_bitrate ==
               o.video.suspend_below_min_bitrate &&
           video.enable_prerenderer_smoothing ==
               o.video.enable_prerenderer_smoothing &&
           video.periodic_alr_bandwidth_probing ==
               o.video.periodic_alr_bandwidth_probing &&
           video.experiment_cpu_load_estimator ==
               o.video.experiment_cpu_load_estimator &&
           video.rtcp_report_interval_ms == o.video.rtcp_report_interval_ms &&
           audio.rtcp_report_interval_ms == o.audio.rtcp_report_interval_ms;
  }

  bool operator!=(const MediaConfig& o) const { return !(*this == o); }
};

}  // namespace cricket

#endif  // MEDIA_BASE_MEDIA_CONFIG_H_
