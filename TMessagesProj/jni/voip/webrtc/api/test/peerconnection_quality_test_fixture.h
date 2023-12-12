/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef API_TEST_PEERCONNECTION_QUALITY_TEST_FIXTURE_H_
#define API_TEST_PEERCONNECTION_QUALITY_TEST_FIXTURE_H_

#include <map>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/memory/memory.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/async_resolver_factory.h"
#include "api/audio/audio_mixer.h"
#include "api/call/call_factory_interface.h"
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
#include "media/base/media_constants.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "rtc_base/network.h"
#include "rtc_base/rtc_certificate_generator.h"
#include "rtc_base/ssl_certificate.h"
#include "rtc_base/thread.h"

namespace webrtc {
namespace webrtc_pc_e2e {

constexpr size_t kDefaultSlidesWidth = 1850;
constexpr size_t kDefaultSlidesHeight = 1110;

// API is in development. Can be changed/removed without notice.
class PeerConnectionE2EQualityTestFixture {
 public:
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
    ScrollingParams(TimeDelta duration,
                    size_t source_width,
                    size_t source_height)
        : duration(duration),
          source_width(source_width),
          source_height(source_height) {
      RTC_CHECK_GT(duration.ms(), 0);
    }

    // Duration of scrolling.
    TimeDelta duration;
    // Width of source slides video.
    size_t source_width;
    // Height of source slides video.
    size_t source_height;
  };

  // Contains screen share video stream properties.
  struct ScreenShareConfig {
    explicit ScreenShareConfig(TimeDelta slide_change_interval)
        : slide_change_interval(slide_change_interval) {
      RTC_CHECK_GT(slide_change_interval.ms(), 0);
    }

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
    explicit VideoSimulcastConfig(int simulcast_streams_count)
        : simulcast_streams_count(simulcast_streams_count) {
      RTC_CHECK_GT(simulcast_streams_count, 1);
    }

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
    EmulatedSFUConfig() {}
    explicit EmulatedSFUConfig(int target_layer_index)
        : target_layer_index(target_layer_index) {
      RTC_CHECK_GE(target_layer_index, 0);
    }

    EmulatedSFUConfig(absl::optional<int> target_layer_index,
                      absl::optional<int> target_temporal_index)
        : target_layer_index(target_layer_index),
          target_temporal_index(target_temporal_index) {
      RTC_CHECK_GE(target_temporal_index.value_or(0), 0);
      if (target_temporal_index)
        RTC_CHECK_GE(*target_temporal_index, 0);
    }

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
    bool operator!=(const VideoResolution& other) const {
      return !(*this == other);
    }

    size_t width() const { return width_; }
    void set_width(size_t width) { width_ = width; }
    size_t height() const { return height_; }
    void set_height(size_t height) { height_ = height; }
    int32_t fps() const { return fps_; }
    void set_fps(int32_t fps) { fps_ = fps; }

    // Returns if it is a regular resolution or not. The resolution is regular
    // if it's spec is `Spec::kNone`.
    bool IsRegular() const { return spec_ == Spec::kNone; }

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
    VideoConfig(size_t width, size_t height, int32_t fps)
        : width(width), height(height), fps(fps) {}
    VideoConfig(std::string stream_label,
                size_t width,
                size_t height,
                int32_t fps)
        : width(width),
          height(height),
          fps(fps),
          stream_label(std::move(stream_label)) {}

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
    enum Mode {
      kGenerated,
      kFile,
    };

    AudioConfig() = default;
    explicit AudioConfig(std::string stream_label)
        : stream_label(std::move(stream_label)) {}

    // Have to be unique among all specified configs for all peers in the call.
    // Will be auto generated if omitted.
    absl::optional<std::string> stream_label;
    Mode mode = kGenerated;
    // Have to be specified only if mode = kFile
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
    explicit VideoCodecConfig(std::string name)
        : name(std::move(name)), required_params() {}
    VideoCodecConfig(std::string name,
                     std::map<std::string, std::string> required_params)
        : name(std::move(name)), required_params(std::move(required_params)) {}
    // Next two fields are used to specify concrete video codec, that should be
    // used in the test. Video code will be negotiated in SDP during offer/
    // answer exchange.
    // Video codec name. You can find valid names in
    // media/base/media_constants.h
    std::string name = cricket::kVp8CodecName;
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
    bool operator!=(const VideoSubscription& other) const {
      return !(*this == other);
    }

    // Subscribes receiver to all streams sent by the specified peer with
    // specified resolution. It will override any resolution that was used in
    // `SubscribeToAll` independently from methods call order.
    VideoSubscription& SubscribeToPeer(
        absl::string_view peer_name,
        VideoResolution resolution =
            VideoResolution(VideoResolution::Spec::kMaxFromSender)) {
      peers_resolution_[std::string(peer_name)] = resolution;
      return *this;
    }

    // Subscribes receiver to the all sent streams with specified resolution.
    // If any stream was subscribed to with `SubscribeTo` method that will
    // override resolution passed to this function independently from methods
    // call order.
    VideoSubscription& SubscribeToAllPeers(
        VideoResolution resolution =
            VideoResolution(VideoResolution::Spec::kMaxFromSender)) {
      default_resolution_ = resolution;
      return *this;
    }

    // Returns resolution for specific sender. If no specific resolution was
    // set for this sender, then will return resolution used for all streams.
    // If subscription doesn't subscribe to all streams, `absl::nullopt` will be
    // returned.
    absl::optional<VideoResolution> GetResolutionForPeer(
        absl::string_view peer_name) const {
      auto it = peers_resolution_.find(std::string(peer_name));
      if (it == peers_resolution_.end()) {
        return default_resolution_;
      }
      return it->second;
    }

    // Returns a maybe empty list of senders for which peer explicitly
    // subscribed to with specific resolution.
    std::vector<std::string> GetSubscribedPeers() const {
      std::vector<std::string> subscribed_streams;
      subscribed_streams.reserve(peers_resolution_.size());
      for (const auto& entry : peers_resolution_) {
        subscribed_streams.push_back(entry.first);
      }
      return subscribed_streams;
    }

    std::string ToString() const;

   private:
    absl::optional<VideoResolution> default_resolution_ = absl::nullopt;
    std::map<std::string, VideoResolution> peers_resolution_;
  };

  // This class is used to fully configure one peer inside the call.
  class PeerConfigurer {
   public:
    virtual ~PeerConfigurer() = default;

    // Sets peer name that will be used to report metrics related to this peer.
    // If not set, some default name will be assigned. All names have to be
    // unique.
    virtual PeerConfigurer* SetName(absl::string_view name) = 0;

    // The parameters of the following 9 methods will be passed to the
    // PeerConnectionFactoryInterface implementation that will be created for
    // this peer.
    virtual PeerConfigurer* SetTaskQueueFactory(
        std::unique_ptr<TaskQueueFactory> task_queue_factory) = 0;
    virtual PeerConfigurer* SetCallFactory(
        std::unique_ptr<CallFactoryInterface> call_factory) = 0;
    virtual PeerConfigurer* SetEventLogFactory(
        std::unique_ptr<RtcEventLogFactoryInterface> event_log_factory) = 0;
    virtual PeerConfigurer* SetFecControllerFactory(
        std::unique_ptr<FecControllerFactoryInterface>
            fec_controller_factory) = 0;
    virtual PeerConfigurer* SetNetworkControllerFactory(
        std::unique_ptr<NetworkControllerFactoryInterface>
            network_controller_factory) = 0;
    virtual PeerConfigurer* SetVideoEncoderFactory(
        std::unique_ptr<VideoEncoderFactory> video_encoder_factory) = 0;
    virtual PeerConfigurer* SetVideoDecoderFactory(
        std::unique_ptr<VideoDecoderFactory> video_decoder_factory) = 0;
    // Set a custom NetEqFactory to be used in the call.
    virtual PeerConfigurer* SetNetEqFactory(
        std::unique_ptr<NetEqFactory> neteq_factory) = 0;
    virtual PeerConfigurer* SetAudioProcessing(
        rtc::scoped_refptr<webrtc::AudioProcessing> audio_processing) = 0;
    virtual PeerConfigurer* SetAudioMixer(
        rtc::scoped_refptr<webrtc::AudioMixer> audio_mixer) = 0;

    // Forces the Peerconnection to use the network thread as the worker thread.
    // Ie, worker thread and the network thread is the same thread.
    virtual PeerConfigurer* SetUseNetworkThreadAsWorkerThread() = 0;

    // The parameters of the following 4 methods will be passed to the
    // PeerConnectionInterface implementation that will be created for this
    // peer.
    virtual PeerConfigurer* SetAsyncResolverFactory(
        std::unique_ptr<webrtc::AsyncResolverFactory>
            async_resolver_factory) = 0;
    virtual PeerConfigurer* SetRTCCertificateGenerator(
        std::unique_ptr<rtc::RTCCertificateGeneratorInterface>
            cert_generator) = 0;
    virtual PeerConfigurer* SetSSLCertificateVerifier(
        std::unique_ptr<rtc::SSLCertificateVerifier> tls_cert_verifier) = 0;
    virtual PeerConfigurer* SetIceTransportFactory(
        std::unique_ptr<IceTransportFactory> factory) = 0;
    // Flags to set on `cricket::PortAllocator`. These flags will be added
    // to the default ones that are presented on the port allocator.
    // For possible values check p2p/base/port_allocator.h.
    virtual PeerConfigurer* SetPortAllocatorExtraFlags(
        uint32_t extra_flags) = 0;

    // Add new video stream to the call that will be sent from this peer.
    // Default implementation of video frames generator will be used.
    virtual PeerConfigurer* AddVideoConfig(VideoConfig config) = 0;
    // Add new video stream to the call that will be sent from this peer with
    // provided own implementation of video frames generator.
    virtual PeerConfigurer* AddVideoConfig(
        VideoConfig config,
        std::unique_ptr<test::FrameGeneratorInterface> generator) = 0;
    // Add new video stream to the call that will be sent from this peer.
    // Capturing device with specified index will be used to get input video.
    virtual PeerConfigurer* AddVideoConfig(
        VideoConfig config,
        CapturingDeviceIndex capturing_device_index) = 0;
    // Sets video subscription for the peer. By default subscription will
    // include all streams with `VideoSubscription::kSameAsSendStream`
    // resolution. To override this behavior use this method.
    virtual PeerConfigurer* SetVideoSubscription(
        VideoSubscription subscription) = 0;
    // Set the list of video codecs used by the peer during the test. These
    // codecs will be negotiated in SDP during offer/answer exchange. The order
    // of these codecs during negotiation will be the same as in `video_codecs`.
    // Codecs have to be available in codecs list provided by peer connection to
    // be negotiated. If some of specified codecs won't be found, the test will
    // crash.
    virtual PeerConfigurer* SetVideoCodecs(
        std::vector<VideoCodecConfig> video_codecs) = 0;
    // Set the audio stream for the call from this peer. If this method won't
    // be invoked, this peer will send no audio.
    virtual PeerConfigurer* SetAudioConfig(AudioConfig config) = 0;

    // Set if ULP FEC should be used or not. False by default.
    virtual PeerConfigurer* SetUseUlpFEC(bool value) = 0;
    // Set if Flex FEC should be used or not. False by default.
    // Client also must enable `enable_flex_fec_support` in the `RunParams` to
    // be able to use this feature.
    virtual PeerConfigurer* SetUseFlexFEC(bool value) = 0;
    // Specifies how much video encoder target bitrate should be different than
    // target bitrate, provided by WebRTC stack. Must be greater than 0. Can be
    // used to emulate overshooting of video encoders. This multiplier will
    // be applied for all video encoder on both sides for all layers. Bitrate
    // estimated by WebRTC stack will be multiplied by this multiplier and then
    // provided into VideoEncoder::SetRates(...). 1.0 by default.
    virtual PeerConfigurer* SetVideoEncoderBitrateMultiplier(
        double multiplier) = 0;

    // If is set, an RTCEventLog will be saved in that location and it will be
    // available for further analysis.
    virtual PeerConfigurer* SetRtcEventLogPath(std::string path) = 0;
    // If is set, an AEC dump will be saved in that location and it will be
    // available for further analysis.
    virtual PeerConfigurer* SetAecDumpPath(std::string path) = 0;
    virtual PeerConfigurer* SetRTCConfiguration(
        PeerConnectionInterface::RTCConfiguration configuration) = 0;
    virtual PeerConfigurer* SetRTCOfferAnswerOptions(
        PeerConnectionInterface::RTCOfferAnswerOptions options) = 0;
    // Set bitrate parameters on PeerConnection. This constraints will be
    // applied to all summed RTP streams for this peer.
    virtual PeerConfigurer* SetBitrateSettings(
        BitrateSettings bitrate_settings) = 0;
  };

  // Contains configuration for echo emulator.
  struct EchoEmulationConfig {
    // Delay which represents the echo path delay, i.e. how soon rendered signal
    // should reach capturer.
    TimeDelta echo_delay = TimeDelta::Millis(50);
  };

  // Contains parameters, that describe how long framework should run quality
  // test.
  struct RunParams {
    explicit RunParams(TimeDelta run_duration) : run_duration(run_duration) {}

    // Specifies how long the test should be run. This time shows how long
    // the media should flow after connection was established and before
    // it will be shut downed.
    TimeDelta run_duration;

    // If set to true peers will be able to use Flex FEC, otherwise they won't
    // be able to negotiate it even if it's enabled on per peer level.
    bool enable_flex_fec_support = false;
    // If true will set conference mode in SDP media section for all video
    // tracks for all peers.
    bool use_conference_mode = false;
    // If specified echo emulation will be done, by mixing the render audio into
    // the capture signal. In such case input signal will be reduced by half to
    // avoid saturation or compression in the echo path simulation.
    absl::optional<EchoEmulationConfig> echo_emulation_config;
  };

  // Represent an entity that will report quality metrics after test.
  class QualityMetricsReporter : public StatsObserverInterface {
   public:
    virtual ~QualityMetricsReporter() = default;

    // Invoked by framework after peer connection factory and peer connection
    // itself will be created but before offer/answer exchange will be started.
    // `test_case_name` is name of test case, that should be used to report all
    // metrics.
    // `reporter_helper` is a pointer to a class that will allow track_id to
    // stream_id matching. The caller is responsible for ensuring the
    // TrackIdStreamInfoMap will be valid from Start() to
    // StopAndReportResults().
    virtual void Start(absl::string_view test_case_name,
                       const TrackIdStreamInfoMap* reporter_helper) = 0;

    // Invoked by framework after call is ended and peer connection factory and
    // peer connection are destroyed.
    virtual void StopAndReportResults() = 0;
  };

  // Represents single participant in call and can be used to perform different
  // in-call actions. Might be extended in future.
  class PeerHandle {
   public:
    virtual ~PeerHandle() = default;
  };

  virtual ~PeerConnectionE2EQualityTestFixture() = default;

  // Add activity that will be executed on the best effort at least after
  // `target_time_since_start` after call will be set up (after offer/answer
  // exchange, ICE gathering will be done and ICE candidates will passed to
  // remote side). `func` param is amount of time spent from the call set up.
  virtual void ExecuteAt(TimeDelta target_time_since_start,
                         std::function<void(TimeDelta)> func) = 0;
  // Add activity that will be executed every `interval` with first execution
  // on the best effort at least after `initial_delay_since_start` after call
  // will be set up (after all participants will be connected). `func` param is
  // amount of time spent from the call set up.
  virtual void ExecuteEvery(TimeDelta initial_delay_since_start,
                            TimeDelta interval,
                            std::function<void(TimeDelta)> func) = 0;

  // Add stats reporter entity to observe the test.
  virtual void AddQualityMetricsReporter(
      std::unique_ptr<QualityMetricsReporter> quality_metrics_reporter) = 0;

  // Add a new peer to the call and return an object through which caller
  // can configure peer's behavior.
  // `network_dependencies` are used to provide networking for peer's peer
  // connection. Members must be non-null.
  // `configurer` function will be used to configure peer in the call.
  virtual PeerHandle* AddPeer(
      const PeerNetworkDependencies& network_dependencies,
      rtc::FunctionView<void(PeerConfigurer*)> configurer) = 0;

  // Runs the media quality test, which includes setting up the call with
  // configured participants, running it according to provided `run_params` and
  // terminating it properly at the end. During call duration media quality
  // metrics are gathered, which are then reported to stdout and (if configured)
  // to the json/protobuf output file through the WebRTC perf test results
  // reporting system.
  virtual void Run(RunParams run_params) = 0;

  // Returns real test duration - the time of test execution measured during
  // test. Client must call this method only after test is finished (after
  // Run(...) method returned). Test execution time is time from end of call
  // setup (offer/answer, ICE candidates exchange done and ICE connected) to
  // start of call tear down (PeerConnection closed).
  virtual TimeDelta GetRealTestDuration() const = 0;
};

}  // namespace webrtc_pc_e2e
}  // namespace webrtc

#endif  // API_TEST_PEERCONNECTION_QUALITY_TEST_FIXTURE_H_
