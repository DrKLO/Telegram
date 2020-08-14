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
#include "api/async_resolver_factory.h"
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
#include "api/test/simulated_network.h"
#include "api/test/stats_observer_interface.h"
#include "api/test/track_id_stream_info_map.h"
#include "api/test/video_quality_analyzer_interface.h"
#include "api/transport/network_control.h"
#include "api/units/time_delta.h"
#include "api/video_codecs/video_decoder_factory.h"
#include "api/video_codecs/video_encoder.h"
#include "api/video_codecs/video_encoder_factory.h"
#include "media/base/media_constants.h"
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
  // window dimensions. So |source_width| and |source_height| are the dimensions
  // of the source frame, while |VideoConfig::width| and |VideoConfig::height|
  // are the dimensions of the sliding window.
  //
  // Because |source_width| and |source_height| are dimensions of the source
  // frame, they have to be width and height of videos from
  // |ScreenShareConfig::slides_yuv_file_names|.
  //
  // Because scrolling have to be done on single slide it also requires, that
  // |duration| must be less or equal to
  // |ScreenShareConfig::slide_change_interval|.
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
    // |slides_yuv_file_names| for scrolling.
    absl::optional<ScrollingParams> scrolling_params;
    // Contains list of yuv files with slides.
    //
    // If empty, default set of slides will be used. In such case
    // |VideoConfig::width| must be equal to |kDefaultSlidesWidth| and
    // |VideoConfig::height| must be equal to |kDefaultSlidesHeight| or if
    // |scrolling_params| are specified, then |ScrollingParams::source_width|
    // must be equal to |kDefaultSlidesWidth| and
    // |ScrollingParams::source_height| must be equal to |kDefaultSlidesHeight|.
    std::vector<std::string> slides_yuv_file_names;
  };

  // Config for Vp8 simulcast or Vp9 SVC testing.
  //
  // SVC support is limited:
  // During SVC testing there is no SFU, so framework will try to emulate SFU
  // behavior in regular p2p call. Because of it there are such limitations:
  //  * if |target_spatial_index| is not equal to the highest spatial layer
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
    VideoSimulcastConfig(int simulcast_streams_count, int target_spatial_index)
        : simulcast_streams_count(simulcast_streams_count),
          target_spatial_index(target_spatial_index) {
      RTC_CHECK_GT(simulcast_streams_count, 1);
      RTC_CHECK_GE(target_spatial_index, 0);
      RTC_CHECK_LT(target_spatial_index, simulcast_streams_count);
    }

    // Specified amount of simulcast streams/SVC layers, depending on which
    // encoder is used.
    int simulcast_streams_count;
    // Specifies spatial index of the video stream to analyze.
    // There are 2 cases:
    // 1. simulcast encoder is used:
    //    in such case |target_spatial_index| will specify the index of
    //    simulcast stream, that should be analyzed. Other streams will be
    //    dropped.
    // 2. SVC encoder is used:
    //    in such case |target_spatial_index| will specify the top interesting
    //    spatial layer and all layers below, including target one will be
    //    processed. All layers above target one will be dropped.
    // If not specified than whatever stream will be received will be analyzed.
    // It requires Selective Forwarding Unit (SFU) to be configured in the
    // network.
    absl::optional<int> target_spatial_index;

    // Encoding parameters per simulcast layer. If not empty, |encoding_params|
    // size have to be equal to |simulcast_streams_count|. Will be used to set
    // transceiver send encoding params for simulcast layers. Applicable only
    // for codecs that support simulcast (ex. Vp8) and will be ignored
    // otherwise. RtpEncodingParameters::rid may be changed by fixture
    // implementation to ensure signaling correctness.
    std::vector<RtpEncodingParameters> encoding_params;
  };

  // Contains properties of single video stream.
  struct VideoConfig {
    VideoConfig(size_t width, size_t height, int32_t fps)
        : width(width), height(height), fps(fps) {}

    // Video stream width.
    const size_t width;
    // Video stream height.
    const size_t height;
    const int32_t fps;
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
    // Count of temporal layers for video stream. This value will be set into
    // each RtpEncodingParameters of RtpParameters of corresponding
    // RtpSenderInterface for this video stream.
    absl::optional<int> temporal_layers_count;
    // Sets the maximum encode bitrate in bps. If this value is not set, the
    // encoder will be capped at an internal maximum value around 2 Mbps
    // depending on the resolution. This means that it will never be able to
    // utilize a high bandwidth link.
    absl::optional<int> max_encode_bitrate_bps;
    // Sets the minimum encode bitrate in bps. If this value is not set, the
    // encoder will use an internal minimum value. Please note that if this
    // value is set higher than the bandwidth of the link, the encoder will
    // generate more data than the link can handle regardless of the bandwidth
    // estimation.
    absl::optional<int> min_encode_bitrate_bps;
    // If specified the input stream will be also copied to specified file.
    // It is actually one of the test's output file, which contains copy of what
    // was captured during the test for this video stream on sender side.
    // It is useful when generator is used as input.
    absl::optional<std::string> input_dump_file_name;
    // If specified this file will be used as output on the receiver side for
    // this stream. If multiple streams will be produced by input stream,
    // output files will be appended with indexes. The produced files contains
    // what was rendered for this video stream on receiver side.
    absl::optional<std::string> output_dump_file_name;
    // If true will display input and output video on the user's screen.
    bool show_on_screen = false;
    // If specified, determines a sync group to which this video stream belongs.
    // According to bugs.webrtc.org/4762 WebRTC supports synchronization only
    // for pair of single audio and single video stream.
    absl::optional<std::string> sync_group;
  };

  // Contains properties for audio in the call.
  struct AudioConfig {
    enum Mode {
      kGenerated,
      kFile,
    };
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
    // Set the audio stream for the call from this peer. If this method won't
    // be invoked, this peer will send no audio.
    virtual PeerConfigurer* SetAudioConfig(AudioConfig config) = 0;
    // If is set, an RTCEventLog will be saved in that location and it will be
    // available for further analysis.
    virtual PeerConfigurer* SetRtcEventLogPath(std::string path) = 0;
    // If is set, an AEC dump will be saved in that location and it will be
    // available for further analysis.
    virtual PeerConfigurer* SetAecDumpPath(std::string path) = 0;
    virtual PeerConfigurer* SetRTCConfiguration(
        PeerConnectionInterface::RTCConfiguration configuration) = 0;
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
    // map if and only if for each key from |required_params| there will be
    // a parameter with name equal to this key and parameter value will be equal
    // to the value from |required_params| for this key.
    // If empty then only name will be used to match the codec.
    std::map<std::string, std::string> required_params;
  };

  // Contains parameters, that describe how long framework should run quality
  // test.
  struct RunParams {
    explicit RunParams(TimeDelta run_duration) : run_duration(run_duration) {}

    // Specifies how long the test should be run. This time shows how long
    // the media should flow after connection was established and before
    // it will be shut downed.
    TimeDelta run_duration;

    // List of video codecs to use during the test. These codecs will be
    // negotiated in SDP during offer/answer exchange. The order of these codecs
    // during negotiation will be the same as in |video_codecs|. Codecs have
    // to be available in codecs list provided by peer connection to be
    // negotiated. If some of specified codecs won't be found, the test will
    // crash.
    // If list is empty Vp8 with no required_params will be used.
    std::vector<VideoCodecConfig> video_codecs;
    bool use_ulp_fec = false;
    bool use_flex_fec = false;
    // Specifies how much video encoder target bitrate should be different than
    // target bitrate, provided by WebRTC stack. Must be greater then 0. Can be
    // used to emulate overshooting of video encoders. This multiplier will
    // be applied for all video encoder on both sides for all layers. Bitrate
    // estimated by WebRTC stack will be multiplied on this multiplier and then
    // provided into VideoEncoder::SetRates(...).
    double video_encoder_bitrate_multiplier = 1.0;
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
    // |test_case_name| is name of test case, that should be used to report all
    // metrics.
    // |reporter_helper| is a pointer to a class that will allow track_id to
    // stream_id matching. The caller is responsible for ensuring the
    // TrackIdStreamInfoMap will be valid from Start() to
    // StopAndReportResults().
    virtual void Start(absl::string_view test_case_name,
                       const TrackIdStreamInfoMap* reporter_helper) = 0;

    // Invoked by framework after call is ended and peer connection factory and
    // peer connection are destroyed.
    virtual void StopAndReportResults() = 0;
  };

  virtual ~PeerConnectionE2EQualityTestFixture() = default;

  // Add activity that will be executed on the best effort at least after
  // |target_time_since_start| after call will be set up (after offer/answer
  // exchange, ICE gathering will be done and ICE candidates will passed to
  // remote side). |func| param is amount of time spent from the call set up.
  virtual void ExecuteAt(TimeDelta target_time_since_start,
                         std::function<void(TimeDelta)> func) = 0;
  // Add activity that will be executed every |interval| with first execution
  // on the best effort at least after |initial_delay_since_start| after call
  // will be set up (after all participants will be connected). |func| param is
  // amount of time spent from the call set up.
  virtual void ExecuteEvery(TimeDelta initial_delay_since_start,
                            TimeDelta interval,
                            std::function<void(TimeDelta)> func) = 0;

  // Add stats reporter entity to observe the test.
  virtual void AddQualityMetricsReporter(
      std::unique_ptr<QualityMetricsReporter> quality_metrics_reporter) = 0;

  // Add a new peer to the call and return an object through which caller
  // can configure peer's behavior.
  // |network_thread| will be used as network thread for peer's peer connection
  // |network_manager| will be used to provide network interfaces for peer's
  // peer connection.
  // |configurer| function will be used to configure peer in the call.
  virtual void AddPeer(rtc::Thread* network_thread,
                       rtc::NetworkManager* network_manager,
                       rtc::FunctionView<void(PeerConfigurer*)> configurer) = 0;
  // Runs the media quality test, which includes setting up the call with
  // configured participants, running it according to provided |run_params| and
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
