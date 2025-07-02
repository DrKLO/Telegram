/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef API_TEST_PCLF_MEDIA_CONFIGURATION_H_
#define API_TEST_PCLF_MEDIA_CONFIGURATION_H_

#include <stddef.h>
#include <stdint.h>

#include <functional>
#include <map>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/memory/memory.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/audio/audio_mixer.h"
#include "api/audio_options.h"
#include "api/fec_controller.h"
#include "api/function_view.h"
#include "api/media_stream_interface.h"
#include "api/peer_connection_interface.h"
#include "api/rtc_event_log/rtc_event_log_factory_interface.h"
#include "api/rtp_parameters.h"
#include "api/task_queue/task_queue_factory.h"
#include "api/test/audio_quality_analyzer_interface.h"
#include "api/test/frame_generator_interface.h"
#include "api/test/peer_network_dependencies.h"
#include "api/test/simulated_network.h"
#include "api/test/stats_observer_interface.h"
#include "api/test/track_id_stream_info_map.h"
#include "api/test/video/video_frame_writer.h"
#include "api/test/video_quality_analyzer_interface.h"
#include "api/transport/network_control.h"
#include "api/units/time_delta.h"
#include "api/video_codecs/video_decoder_factory.h"
#include "api/video_codecs/video_encoder.h"
#include "api/video_codecs/video_encoder_factory.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "rtc_base/checks.h"
#include "rtc_base/network.h"
#include "rtc_base/rtc_certificate_generator.h"
#include "rtc_base/ssl_certificate.h"
#include "rtc_base/thread.h"

namespace webrtc {
namespace webrtc_pc_e2e {

constexpr size_t kDefaultSlidesWidth = 1850;
constexpr size_t kDefaultSlidesHeight = 1110;

// The index of required capturing device in OS provided list of video
// devices. On Linux and Windows the list will be obtained via
// webrtc::VideoCaptureModule::DeviceInfo, on Mac OS via
// [RTCCameraVideoCapturer captureDevices].
enum class CapturingDeviceIndex : size_t {};

// Contains parameters for screen share scrolling.
//
// If scrolling is enabled, then it will be done by putting sliding window
// on source video and moving this window from top left corner to the
// bottom right corner of the picture.
//
// In such case source dimensions must be greater or equal to the sliding
// window dimensions. So `source_width` and `source_height` are the dimensions
// of the source frame, while `VideoConfig::width` and `VideoConfig::height`
// are the dimensions of the sliding window.
//
// Because `source_width` and `source_height` are dimensions of the source
// frame, they have to be width and height of videos from
// `ScreenShareConfig::slides_yuv_file_names`.
//
// Because scrolling have to be done on single slide it also requires, that
// `duration` must be less or equal to
// `ScreenShareConfig::slide_change_interval`.
struct ScrollingParams {
  // Duration of scrolling.
  TimeDelta duration;
  // Width of source slides video.
  size_t source_width = kDefaultSlidesWidth;
  // Height of source slides video.
  size_t source_height = kDefaultSlidesHeight;
};

// Contains screen share video stream properties.
struct ScreenShareConfig {
  explicit ScreenShareConfig(TimeDelta slide_change_interval);

  // Shows how long one slide should be presented on the screen during
  // slide generation.
  TimeDelta slide_change_interval;
  // If true, slides will be generated programmatically. No scrolling params
  // will be applied in such case.
  bool generate_slides = false;
  // If present scrolling will be applied. Please read extra requirement on
  // `slides_yuv_file_names` for scrolling.
  absl::optional<ScrollingParams> scrolling_params;
  // Contains list of yuv files with slides.
  //
  // If empty, default set of slides will be used. In such case
  // `VideoConfig::width` must be equal to `kDefaultSlidesWidth` and
  // `VideoConfig::height` must be equal to `kDefaultSlidesHeight` or if
  // `scrolling_params` are specified, then `ScrollingParams::source_width`
  // must be equal to `kDefaultSlidesWidth` and
  // `ScrollingParams::source_height` must be equal to `kDefaultSlidesHeight`.
  std::vector<std::string> slides_yuv_file_names;
};

// Config for Vp8 simulcast or non-standard Vp9 SVC testing.
//
// To configure standard SVC setting, use `scalability_mode` in the
// `encoding_params` array.
// This configures Vp9 SVC by requesting simulcast layers, the request is
// internally converted to a request for SVC layers.
//
// SVC support is limited:
// During SVC testing there is no SFU, so framework will try to emulate SFU
// behavior in regular p2p call. Because of it there are such limitations:
//  * if `target_spatial_index` is not equal to the highest spatial layer
//    then no packet/frame drops are allowed.
//
//    If there will be any drops, that will affect requested layer, then
//    WebRTC SVC implementation will continue decoding only the highest
//    available layer and won't restore lower layers, so analyzer won't
//    receive required data which will cause wrong results or test failures.
struct VideoSimulcastConfig {
  explicit VideoSimulcastConfig(int simulcast_streams_count);

  // Specified amount of simulcast streams/SVC layers, depending on which
  // encoder is used.
  int simulcast_streams_count;
};

// Configuration for the emulated Selective Forward Unit (SFU)
//
// The framework can optionally filter out frames that are decoded
// using an emulated SFU.
// When using simulcast or SVC, it's not always desirable to receive
// all frames. In a real world call, a SFU will only forward a subset
// of the frames.
// The emulated SFU is not able to change its configuration dynamically,
// if adaptation happens during the call, layers may be dropped and the
// analyzer won't receive the required data which will cause wrong results or
// test failures.
struct EmulatedSFUConfig {
  EmulatedSFUConfig() = default;
  explicit EmulatedSFUConfig(int target_layer_index);
  EmulatedSFUConfig(absl::optional<int> target_layer_index,
                    absl::optional<int> target_temporal_index);

  // Specifies simulcast or spatial index of the video stream to analyze.
  // There are 2 cases:
  // 1. simulcast encoding is used:
  //    in such case `target_layer_index` will specify the index of
  //    simulcast stream, that should be analyzed. Other streams will be
  //    dropped.
  // 2. SVC encoding is used:
  //    in such case `target_layer_index` will specify the top interesting
  //    spatial layer and all layers below, including target one will be
  //    processed. All layers above target one will be dropped.
  // If not specified then all streams will be received and analyzed.
  // When set, it instructs the framework to create an emulated Selective
  // Forwarding Unit (SFU) that will propagate only the requested layers.
  absl::optional<int> target_layer_index;
  // Specifies the index of the maximum temporal unit to keep.
  // If not specified then all temporal layers will be received and analyzed.
  // When set, it instructs the framework to create an emulated Selective
  // Forwarding Unit (SFU) that will propagate only up to the requested layer.
  absl::optional<int> target_temporal_index;
};

class VideoResolution {
 public:
  // Determines special resolutions, which can't be expressed in terms of
  // width, height and fps.
  enum class Spec {
    // No extra spec set. It describes a regular resolution described by
    // width, height and fps.
    kNone,
    // Describes resolution which contains max value among all sender's
    // video streams in each dimension (width, height, fps).
    kMaxFromSender
  };

  VideoResolution(size_t width, size_t height, int32_t fps);
  explicit VideoResolution(Spec spec = Spec::kNone);

  bool operator==(const VideoResolution& other) const;
  bool operator!=(const VideoResolution& other) const;

  size_t width() const { return width_; }
  void set_width(size_t width) { width_ = width; }
  size_t height() const { return height_; }
  void set_height(size_t height) { height_ = height; }
  int32_t fps() const { return fps_; }
  void set_fps(int32_t fps) { fps_ = fps; }

  // Returns if it is a regular resolution or not. The resolution is regular
  // if it's spec is `Spec::kNone`.
  bool IsRegular() const;

  std::string ToString() const;

 private:
  size_t width_ = 0;
  size_t height_ = 0;
  int32_t fps_ = 0;
  Spec spec_ = Spec::kNone;
};

class VideoDumpOptions {
 public:
  static constexpr int kDefaultSamplingModulo = 1;

  // output_directory - the output directory where stream will be dumped. The
  // output files' names will be constructed as
  // <stream_name>_<receiver_name>_<resolution>.<extension> for output dumps
  // and <stream_name>_<resolution>.<extension> for input dumps.
  // By default <extension> is "y4m". Resolution is in the format
  // <width>x<height>_<fps>.
  // sampling_modulo - the module for the video frames to be dumped. Modulo
  // equals X means every Xth frame will be written to the dump file. The
  // value must be greater than 0. (Default: 1)
  // export_frame_ids - specifies if frame ids should be exported together
  // with content of the stream. If true, an output file with the same name as
  // video dump and suffix ".frame_ids.txt" will be created. It will contain
  // the frame ids in the same order as original frames in the output
  // file with stream content. File will contain one frame id per line.
  // (Default: false)
  // `video_frame_writer_factory` - factory function to create a video frame
  // writer for input and output video files. (Default: Y4M video writer
  // factory).
  explicit VideoDumpOptions(
      absl::string_view output_directory,
      int sampling_modulo = kDefaultSamplingModulo,
      bool export_frame_ids = false,
      std::function<std::unique_ptr<test::VideoFrameWriter>(
          absl::string_view file_name_prefix,
          const VideoResolution& resolution)> video_frame_writer_factory =
          Y4mVideoFrameWriterFactory);
  VideoDumpOptions(absl::string_view output_directory, bool export_frame_ids);

  VideoDumpOptions(const VideoDumpOptions&) = default;
  VideoDumpOptions& operator=(const VideoDumpOptions&) = default;
  VideoDumpOptions(VideoDumpOptions&&) = default;
  VideoDumpOptions& operator=(VideoDumpOptions&&) = default;

  std::string output_directory() const { return output_directory_; }
  int sampling_modulo() const { return sampling_modulo_; }
  bool export_frame_ids() const { return export_frame_ids_; }

  std::unique_ptr<test::VideoFrameWriter> CreateInputDumpVideoFrameWriter(
      absl::string_view stream_label,
      const VideoResolution& resolution) const;

  std::unique_ptr<test::VideoFrameWriter> CreateOutputDumpVideoFrameWriter(
      absl::string_view stream_label,
      absl::string_view receiver,
      const VideoResolution& resolution) const;

  std::string ToString() const;

 private:
  static std::unique_ptr<test::VideoFrameWriter> Y4mVideoFrameWriterFactory(
      absl::string_view file_name_prefix,
      const VideoResolution& resolution);
  std::string GetInputDumpFileName(absl::string_view stream_label,
                                   const VideoResolution& resolution) const;
  // Returns file name for input frame ids dump if `export_frame_ids()` is
  // true, absl::nullopt otherwise.
  absl::optional<std::string> GetInputFrameIdsDumpFileName(
      absl::string_view stream_label,
      const VideoResolution& resolution) const;
  std::string GetOutputDumpFileName(absl::string_view stream_label,
                                    absl::string_view receiver,
                                    const VideoResolution& resolution) const;
  // Returns file name for output frame ids dump if `export_frame_ids()` is
  // true, absl::nullopt otherwise.
  absl::optional<std::string> GetOutputFrameIdsDumpFileName(
      absl::string_view stream_label,
      absl::string_view receiver,
      const VideoResolution& resolution) const;

  std::string output_directory_;
  int sampling_modulo_ = 1;
  bool export_frame_ids_ = false;
  std::function<std::unique_ptr<test::VideoFrameWriter>(
      absl::string_view file_name_prefix,
      const VideoResolution& resolution)>
      video_frame_writer_factory_;
};

// Contains properties of single video stream.
struct VideoConfig {
  explicit VideoConfig(const VideoResolution& resolution);
  VideoConfig(size_t width, size_t height, int32_t fps);
  VideoConfig(absl::string_view stream_label,
              size_t width,
              size_t height,
              int32_t fps);

  // Video stream width.
  size_t width;
  // Video stream height.
  size_t height;
  int32_t fps;
  VideoResolution GetResolution() const {
    return VideoResolution(width, height, fps);
  }

  // Have to be unique among all specified configs for all peers in the call.
  // Will be auto generated if omitted.
  absl::optional<std::string> stream_label;
  // Will be set for current video track. If equals to kText or kDetailed -
  // screencast in on.
  absl::optional<VideoTrackInterface::ContentHint> content_hint;
  // If presented video will be transfered in simulcast/SVC mode depending on
  // which encoder is used.
  //
  // Simulcast is supported only from 1st added peer. For VP8 simulcast only
  // without RTX is supported so it will be automatically disabled for all
  // simulcast tracks. For VP9 simulcast enables VP9 SVC mode and support RTX,
  // but only on non-lossy networks. See more in documentation to
  // VideoSimulcastConfig.
  absl::optional<VideoSimulcastConfig> simulcast_config;
  // Configuration for the emulated Selective Forward Unit (SFU).
  absl::optional<EmulatedSFUConfig> emulated_sfu_config;
  // Encoding parameters for both singlecast and per simulcast layer.
  // If singlecast is used, if not empty, a single value can be provided.
  // If simulcast is used, if not empty, `encoding_params` size have to be
  // equal to `simulcast_config.simulcast_streams_count`. Will be used to set
  // transceiver send encoding params for each layer.
  // RtpEncodingParameters::rid may be changed by fixture implementation to
  // ensure signaling correctness.
  std::vector<RtpEncodingParameters> encoding_params;
  // Count of temporal layers for video stream. This value will be set into
  // each RtpEncodingParameters of RtpParameters of corresponding
  // RtpSenderInterface for this video stream.
  absl::optional<int> temporal_layers_count;
  // If specified defines how input should be dumped. It is actually one of
  // the test's output file, which contains copy of what was captured during
  // the test for this video stream on sender side. It is useful when
  // generator is used as input.
  absl::optional<VideoDumpOptions> input_dump_options;
  // If specified defines how output should be dumped on the receiver side for
  // this stream. The produced files contain what was rendered for this video
  // stream on receiver side per each receiver.
  absl::optional<VideoDumpOptions> output_dump_options;
  // If set to true uses fixed frame rate while dumping output video to the
  // file. Requested `VideoSubscription::fps()` will be used as frame rate.
  bool output_dump_use_fixed_framerate = false;
  // If true will display input and output video on the user's screen.
  bool show_on_screen = false;
  // If specified, determines a sync group to which this video stream belongs.
  // According to bugs.webrtc.org/4762 WebRTC supports synchronization only
  // for pair of single audio and single video stream.
  absl::optional<std::string> sync_group;
  // If specified, it will be set into RtpParameters of corresponding
  // RtpSenderInterface for this video stream.
  // Note that this setting takes precedence over `content_hint`.
  absl::optional<DegradationPreference> degradation_preference;
};

// Contains properties for audio in the call.
struct AudioConfig {
  AudioConfig() = default;
  explicit AudioConfig(absl::string_view stream_label);

  // Have to be unique among all specified configs for all peers in the call.
  // Will be auto generated if omitted.
  absl::optional<std::string> stream_label;
  // If no file is specified an audio will be generated.
  absl::optional<std::string> input_file_name;
  // If specified the input stream will be also copied to specified file.
  absl::optional<std::string> input_dump_file_name;
  // If specified the output stream will be copied to specified file.
  absl::optional<std::string> output_dump_file_name;

  // Audio options to use.
  cricket::AudioOptions audio_options;
  // Sampling frequency of input audio data (from file or generated).
  int sampling_frequency_in_hz = 48000;
  // If specified, determines a sync group to which this audio stream belongs.
  // According to bugs.webrtc.org/4762 WebRTC supports synchronization only
  // for pair of single audio and single video stream.
  absl::optional<std::string> sync_group;
};

struct VideoCodecConfig {
  explicit VideoCodecConfig(absl::string_view name);
  VideoCodecConfig(absl::string_view name,
                   std::map<std::string, std::string> required_params);
  // Next two fields are used to specify concrete video codec, that should be
  // used in the test. Video code will be negotiated in SDP during offer/
  // answer exchange.
  // Video codec name. You can find valid names in
  // media/base/media_constants.h
  std::string name;
  // Map of parameters, that have to be specified on SDP codec. Each parameter
  // is described by key and value. Codec parameters will match the specified
  // map if and only if for each key from `required_params` there will be
  // a parameter with name equal to this key and parameter value will be equal
  // to the value from `required_params` for this key.
  // If empty then only name will be used to match the codec.
  std::map<std::string, std::string> required_params;
};

// Subscription to the remote video streams. It declares which remote stream
// peer should receive and in which resolution (width x height x fps).
class VideoSubscription {
 public:
  // Returns the resolution constructed as maximum from all resolution
  // dimensions: width, height and fps.
  static absl::optional<VideoResolution> GetMaxResolution(
      rtc::ArrayView<const VideoConfig> video_configs);
  static absl::optional<VideoResolution> GetMaxResolution(
      rtc::ArrayView<const VideoResolution> resolutions);

  bool operator==(const VideoSubscription& other) const;
  bool operator!=(const VideoSubscription& other) const;

  // Subscribes receiver to all streams sent by the specified peer with
  // specified resolution. It will override any resolution that was used in
  // `SubscribeToAll` independently from methods call order.
  VideoSubscription& SubscribeToPeer(
      absl::string_view peer_name,
      VideoResolution resolution =
          VideoResolution(VideoResolution::Spec::kMaxFromSender));

  // Subscribes receiver to the all sent streams with specified resolution.
  // If any stream was subscribed to with `SubscribeTo` method that will
  // override resolution passed to this function independently from methods
  // call order.
  VideoSubscription& SubscribeToAllPeers(
      VideoResolution resolution =
          VideoResolution(VideoResolution::Spec::kMaxFromSender));

  // Returns resolution for specific sender. If no specific resolution was
  // set for this sender, then will return resolution used for all streams.
  // If subscription doesn't subscribe to all streams, `absl::nullopt` will be
  // returned.
  absl::optional<VideoResolution> GetResolutionForPeer(
      absl::string_view peer_name) const;

  // Returns a maybe empty list of senders for which peer explicitly
  // subscribed to with specific resolution.
  std::vector<std::string> GetSubscribedPeers() const;

  std::string ToString() const;

 private:
  absl::optional<VideoResolution> default_resolution_ = absl::nullopt;
  std::map<std::string, VideoResolution> peers_resolution_;
};

// Contains configuration for echo emulator.
struct EchoEmulationConfig {
  // Delay which represents the echo path delay, i.e. how soon rendered signal
  // should reach capturer.
  TimeDelta echo_delay = TimeDelta::Millis(50);
};

}  // namespace webrtc_pc_e2e
}  // namespace webrtc

#endif  // API_TEST_PCLF_MEDIA_CONFIGURATION_H_
