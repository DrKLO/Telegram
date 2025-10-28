/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/test/videocodec_test_fixture_impl.h"

#include <stdint.h>
#include <stdio.h>

#include <algorithm>
#include <cmath>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/strings/str_replace.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/environment/environment.h"
#include "api/environment/environment_factory.h"
#include "api/test/metrics/global_metrics_logger_and_exporter.h"
#include "api/test/metrics/metric.h"
#include "api/transport/field_trial_based_config.h"
#include "api/video/video_bitrate_allocation.h"
#include "api/video_codecs/h264_profile_level_id.h"
#include "api/video_codecs/sdp_video_format.h"
#include "api/video_codecs/video_codec.h"
#include "api/video_codecs/video_decoder.h"
#include "api/video_codecs/video_decoder_factory_template.h"
#include "api/video_codecs/video_decoder_factory_template_dav1d_adapter.h"
#include "api/video_codecs/video_decoder_factory_template_libvpx_vp8_adapter.h"
#include "api/video_codecs/video_decoder_factory_template_libvpx_vp9_adapter.h"
#include "api/video_codecs/video_decoder_factory_template_open_h264_adapter.h"
#include "api/video_codecs/video_encoder_factory.h"
#include "api/video_codecs/video_encoder_factory_template.h"
#include "api/video_codecs/video_encoder_factory_template_libaom_av1_adapter.h"
#include "api/video_codecs/video_encoder_factory_template_libvpx_vp8_adapter.h"
#include "api/video_codecs/video_encoder_factory_template_libvpx_vp9_adapter.h"
#include "api/video_codecs/video_encoder_factory_template_open_h264_adapter.h"
#include "common_video/h264/h264_common.h"
#include "media/base/media_constants.h"
#include "modules/video_coding/codecs/h264/include/h264_globals.h"
#include "modules/video_coding/codecs/vp9/svc_config.h"
#include "modules/video_coding/utility/ivf_file_writer.h"
#include "rtc_base/checks.h"
#include "rtc_base/cpu_time.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/cpu_info.h"
#include "system_wrappers/include/sleep.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"
#include "test/testsupport/frame_writer.h"
#include "test/video_codec_settings.h"
#include "video/config/simulcast.h"
#include "video/config/video_encoder_config.h"

namespace webrtc {
namespace test {
namespace {

using VideoStatistics = VideoCodecTestStats::VideoStatistics;

const int kBaseKeyFrameInterval = 3000;
const double kBitratePriority = 1.0;
const int kDefaultMaxFramerateFps = 30;
const int kMaxQp = 56;

void ConfigureSimulcast(VideoCodec* codec_settings) {
  FieldTrialBasedConfig trials;
  const std::vector<webrtc::VideoStream> streams = cricket::GetSimulcastConfig(
      /*min_layer=*/1, codec_settings->numberOfSimulcastStreams,
      codec_settings->width, codec_settings->height, kBitratePriority, kMaxQp,
      /* is_screenshare = */ false, true, trials);

  for (size_t i = 0; i < streams.size(); ++i) {
    SimulcastStream* ss = &codec_settings->simulcastStream[i];
    ss->width = static_cast<uint16_t>(streams[i].width);
    ss->height = static_cast<uint16_t>(streams[i].height);
    ss->numberOfTemporalLayers =
        static_cast<unsigned char>(*streams[i].num_temporal_layers);
    ss->maxBitrate = streams[i].max_bitrate_bps / 1000;
    ss->targetBitrate = streams[i].target_bitrate_bps / 1000;
    ss->minBitrate = streams[i].min_bitrate_bps / 1000;
    ss->qpMax = streams[i].max_qp;
    ss->active = true;
  }
}

void ConfigureSvc(VideoCodec* codec_settings) {
  RTC_CHECK_EQ(kVideoCodecVP9, codec_settings->codecType);

  const std::vector<SpatialLayer> layers = GetSvcConfig(
      codec_settings->width, codec_settings->height, kDefaultMaxFramerateFps,
      /*first_active_layer=*/0, codec_settings->VP9()->numberOfSpatialLayers,
      codec_settings->VP9()->numberOfTemporalLayers,
      /* is_screen_sharing = */ false);
  ASSERT_EQ(codec_settings->VP9()->numberOfSpatialLayers, layers.size())
      << "GetSvcConfig returned fewer spatial layers than configured.";

  for (size_t i = 0; i < layers.size(); ++i) {
    codec_settings->spatialLayers[i] = layers[i];
  }
}

std::string CodecSpecificToString(const VideoCodec& codec) {
  char buf[1024];
  rtc::SimpleStringBuilder ss(buf);
  switch (codec.codecType) {
    case kVideoCodecVP8:
      ss << "\nnum_temporal_layers: "
         << static_cast<int>(codec.VP8().numberOfTemporalLayers);
      ss << "\ndenoising: " << codec.VP8().denoisingOn;
      ss << "\nautomatic_resize: " << codec.VP8().automaticResizeOn;
      ss << "\nkey_frame_interval: " << codec.VP8().keyFrameInterval;
      break;
    case kVideoCodecVP9:
      ss << "\nnum_temporal_layers: "
         << static_cast<int>(codec.VP9().numberOfTemporalLayers);
      ss << "\nnum_spatial_layers: "
         << static_cast<int>(codec.VP9().numberOfSpatialLayers);
      ss << "\ndenoising: " << codec.VP9().denoisingOn;
      ss << "\nkey_frame_interval: " << codec.VP9().keyFrameInterval;
      ss << "\nadaptive_qp_mode: " << codec.VP9().adaptiveQpMode;
      ss << "\nautomatic_resize: " << codec.VP9().automaticResizeOn;
      ss << "\nflexible_mode: " << codec.VP9().flexibleMode;
      break;
    case kVideoCodecH264:
      ss << "\nkey_frame_interval: " << codec.H264().keyFrameInterval;
      ss << "\nnum_temporal_layers: "
         << static_cast<int>(codec.H264().numberOfTemporalLayers);
      break;
    case kVideoCodecH265:
      // TODO(bugs.webrtc.org/13485)
      break;
    default:
      break;
  }
  return ss.str();
}

bool RunEncodeInRealTime(const VideoCodecTestFixtureImpl::Config& config) {
  if (config.measure_cpu || config.encode_in_real_time) {
    return true;
  }
  return false;
}

std::string FilenameWithParams(
    const VideoCodecTestFixtureImpl::Config& config) {
  return config.filename + "_" + config.CodecName() + "_" +
         std::to_string(config.codec_settings.startBitrate);
}

SdpVideoFormat CreateSdpVideoFormat(
    const VideoCodecTestFixtureImpl::Config& config) {
  if (config.codec_settings.codecType == kVideoCodecH264) {
    const char* packetization_mode =
        config.h264_codec_settings.packetization_mode ==
                H264PacketizationMode::NonInterleaved
            ? "1"
            : "0";
    CodecParameterMap codec_params = {
        {cricket::kH264FmtpProfileLevelId,
         *H264ProfileLevelIdToString(H264ProfileLevelId(
             config.h264_codec_settings.profile, H264Level::kLevel3_1))},
        {cricket::kH264FmtpPacketizationMode, packetization_mode},
        {cricket::kH264FmtpLevelAsymmetryAllowed, "1"}};

    return SdpVideoFormat(config.codec_name, codec_params);
  } else if (config.codec_settings.codecType == kVideoCodecVP9) {
    return SdpVideoFormat(config.codec_name, {{"profile-id", "0"}});
  }

  return SdpVideoFormat(config.codec_name);
}

}  // namespace

VideoCodecTestFixtureImpl::Config::Config() = default;

void VideoCodecTestFixtureImpl::Config::SetCodecSettings(
    std::string codec_name,
    size_t num_simulcast_streams,
    size_t num_spatial_layers,
    size_t num_temporal_layers,
    bool denoising_on,
    bool frame_dropper_on,
    bool spatial_resize_on,
    size_t width,
    size_t height) {
  this->codec_name = codec_name;
  VideoCodecType codec_type = PayloadStringToCodecType(codec_name);
  webrtc::test::CodecSettings(codec_type, &codec_settings);

  // TODO(brandtr): Move the setting of `width` and `height` to the tests, and
  // DCHECK that they are set before initializing the codec instead.
  codec_settings.width = static_cast<uint16_t>(width);
  codec_settings.height = static_cast<uint16_t>(height);

  RTC_CHECK(num_simulcast_streams >= 1 &&
            num_simulcast_streams <= kMaxSimulcastStreams);
  RTC_CHECK(num_spatial_layers >= 1 && num_spatial_layers <= kMaxSpatialLayers);
  RTC_CHECK(num_temporal_layers >= 1 &&
            num_temporal_layers <= kMaxTemporalStreams);

  // Simulcast is only available with VP8.
  RTC_CHECK(num_simulcast_streams < 2 || codec_type == kVideoCodecVP8);

  // Spatial scalability is only available with VP9.
  RTC_CHECK(num_spatial_layers < 2 || codec_type == kVideoCodecVP9);

  // Some base code requires numberOfSimulcastStreams to be set to zero
  // when simulcast is not used.
  codec_settings.numberOfSimulcastStreams =
      num_simulcast_streams <= 1 ? 0
                                 : static_cast<uint8_t>(num_simulcast_streams);

  codec_settings.SetFrameDropEnabled(frame_dropper_on);
  switch (codec_settings.codecType) {
    case kVideoCodecVP8:
      codec_settings.VP8()->numberOfTemporalLayers =
          static_cast<uint8_t>(num_temporal_layers);
      codec_settings.VP8()->denoisingOn = denoising_on;
      codec_settings.VP8()->automaticResizeOn = spatial_resize_on;
      codec_settings.VP8()->keyFrameInterval = kBaseKeyFrameInterval;
      break;
    case kVideoCodecVP9:
      codec_settings.VP9()->numberOfTemporalLayers =
          static_cast<uint8_t>(num_temporal_layers);
      codec_settings.VP9()->denoisingOn = denoising_on;
      codec_settings.VP9()->keyFrameInterval = kBaseKeyFrameInterval;
      codec_settings.VP9()->automaticResizeOn = spatial_resize_on;
      codec_settings.VP9()->numberOfSpatialLayers =
          static_cast<uint8_t>(num_spatial_layers);
      break;
    case kVideoCodecAV1:
      codec_settings.qpMax = 63;
      break;
    case kVideoCodecH264:
      codec_settings.H264()->keyFrameInterval = kBaseKeyFrameInterval;
      codec_settings.H264()->numberOfTemporalLayers =
          static_cast<uint8_t>(num_temporal_layers);
      break;
    case kVideoCodecH265:
      // TODO(bugs.webrtc.org/13485)
      break;
    default:
      break;
  }

  if (codec_settings.numberOfSimulcastStreams > 1) {
    ConfigureSimulcast(&codec_settings);
  } else if (codec_settings.codecType == kVideoCodecVP9 &&
             codec_settings.VP9()->numberOfSpatialLayers > 1) {
    ConfigureSvc(&codec_settings);
  }
}

size_t VideoCodecTestFixtureImpl::Config::NumberOfCores() const {
  return use_single_core ? 1 : CpuInfo::DetectNumberOfCores();
}

size_t VideoCodecTestFixtureImpl::Config::NumberOfTemporalLayers() const {
  if (codec_settings.codecType == kVideoCodecVP8) {
    return codec_settings.VP8().numberOfTemporalLayers;
  } else if (codec_settings.codecType == kVideoCodecVP9) {
    return codec_settings.VP9().numberOfTemporalLayers;
  } else if (codec_settings.codecType == kVideoCodecH264) {
    return codec_settings.H264().numberOfTemporalLayers;
  } else {
    return 1;
  }
}

size_t VideoCodecTestFixtureImpl::Config::NumberOfSpatialLayers() const {
  if (codec_settings.codecType == kVideoCodecVP9) {
    return codec_settings.VP9().numberOfSpatialLayers;
  } else {
    return 1;
  }
}

size_t VideoCodecTestFixtureImpl::Config::NumberOfSimulcastStreams() const {
  return codec_settings.numberOfSimulcastStreams;
}

std::string VideoCodecTestFixtureImpl::Config::ToString() const {
  std::string codec_type = CodecTypeToPayloadString(codec_settings.codecType);
  rtc::StringBuilder ss;
  ss << "test_name: " << test_name;
  ss << "\nfilename: " << filename;
  ss << "\nnum_frames: " << num_frames;
  ss << "\nmax_payload_size_bytes: " << max_payload_size_bytes;
  ss << "\ndecode: " << decode;
  ss << "\nuse_single_core: " << use_single_core;
  ss << "\nmeasure_cpu: " << measure_cpu;
  ss << "\nnum_cores: " << NumberOfCores();
  ss << "\ncodec_type: " << codec_type;
  ss << "\n\n--> codec_settings";
  ss << "\nwidth: " << codec_settings.width;
  ss << "\nheight: " << codec_settings.height;
  ss << "\nmax_framerate_fps: " << codec_settings.maxFramerate;
  ss << "\nstart_bitrate_kbps: " << codec_settings.startBitrate;
  ss << "\nmax_bitrate_kbps: " << codec_settings.maxBitrate;
  ss << "\nmin_bitrate_kbps: " << codec_settings.minBitrate;
  ss << "\nmax_qp: " << codec_settings.qpMax;
  ss << "\nnum_simulcast_streams: "
     << static_cast<int>(codec_settings.numberOfSimulcastStreams);
  ss << "\n\n--> codec_settings." << codec_type;
  ss << "complexity: "
     << static_cast<int>(codec_settings.GetVideoEncoderComplexity());
  ss << "\nframe_dropping: " << codec_settings.GetFrameDropEnabled();
  ss << "\n" << CodecSpecificToString(codec_settings);
  if (codec_settings.numberOfSimulcastStreams > 1) {
    for (int i = 0; i < codec_settings.numberOfSimulcastStreams; ++i) {
      ss << "\n\n--> codec_settings.simulcastStream[" << i << "]";
      const SimulcastStream& simulcast_stream =
          codec_settings.simulcastStream[i];
      ss << "\nwidth: " << simulcast_stream.width;
      ss << "\nheight: " << simulcast_stream.height;
      ss << "\nnum_temporal_layers: "
         << static_cast<int>(simulcast_stream.numberOfTemporalLayers);
      ss << "\nmin_bitrate_kbps: " << simulcast_stream.minBitrate;
      ss << "\ntarget_bitrate_kbps: " << simulcast_stream.targetBitrate;
      ss << "\nmax_bitrate_kbps: " << simulcast_stream.maxBitrate;
      ss << "\nmax_qp: " << simulcast_stream.qpMax;
      ss << "\nactive: " << simulcast_stream.active;
    }
  }
  ss << "\n";
  return ss.Release();
}

std::string VideoCodecTestFixtureImpl::Config::CodecName() const {
  std::string name = codec_name;
  if (name.empty()) {
    name = CodecTypeToPayloadString(codec_settings.codecType);
  }
  if (codec_settings.codecType == kVideoCodecH264) {
    if (h264_codec_settings.profile == H264Profile::kProfileConstrainedHigh) {
      return name + "-CHP";
    } else {
      RTC_DCHECK_EQ(h264_codec_settings.profile,
                    H264Profile::kProfileConstrainedBaseline);
      return name + "-CBP";
    }
  }
  return name;
}

// TODO(kthelgason): Move this out of the test fixture impl and
// make available as a shared utility class.
void VideoCodecTestFixtureImpl::H264KeyframeChecker::CheckEncodedFrame(
    webrtc::VideoCodecType codec,
    const EncodedImage& encoded_frame) const {
  EXPECT_EQ(kVideoCodecH264, codec);
  bool contains_sps = false;
  bool contains_pps = false;
  bool contains_idr = false;
  const std::vector<webrtc::H264::NaluIndex> nalu_indices =
      webrtc::H264::FindNaluIndices(encoded_frame.data(), encoded_frame.size());
  for (const webrtc::H264::NaluIndex& index : nalu_indices) {
    webrtc::H264::NaluType nalu_type = webrtc::H264::ParseNaluType(
        encoded_frame.data()[index.payload_start_offset]);
    if (nalu_type == webrtc::H264::NaluType::kSps) {
      contains_sps = true;
    } else if (nalu_type == webrtc::H264::NaluType::kPps) {
      contains_pps = true;
    } else if (nalu_type == webrtc::H264::NaluType::kIdr) {
      contains_idr = true;
    }
  }
  if (encoded_frame._frameType == VideoFrameType::kVideoFrameKey) {
    EXPECT_TRUE(contains_sps) << "Keyframe should contain SPS.";
    EXPECT_TRUE(contains_pps) << "Keyframe should contain PPS.";
    EXPECT_TRUE(contains_idr) << "Keyframe should contain IDR.";
  } else if (encoded_frame._frameType == VideoFrameType::kVideoFrameDelta) {
    EXPECT_FALSE(contains_sps) << "Delta frame should not contain SPS.";
    EXPECT_FALSE(contains_pps) << "Delta frame should not contain PPS.";
    EXPECT_FALSE(contains_idr) << "Delta frame should not contain IDR.";
  } else {
    RTC_DCHECK_NOTREACHED();
  }
}

class VideoCodecTestFixtureImpl::CpuProcessTime final {
 public:
  explicit CpuProcessTime(const Config& config) : config_(config) {}
  ~CpuProcessTime() {}

  void Start() {
    if (config_.measure_cpu) {
      cpu_time_ -= rtc::GetProcessCpuTimeNanos();
      wallclock_time_ -= rtc::SystemTimeNanos();
    }
  }
  void Stop() {
    if (config_.measure_cpu) {
      cpu_time_ += rtc::GetProcessCpuTimeNanos();
      wallclock_time_ += rtc::SystemTimeNanos();
    }
  }
  void Print() const {
    if (config_.measure_cpu) {
      RTC_LOG(LS_INFO) << "cpu_usage_percent: "
                       << GetUsagePercent() / config_.NumberOfCores();
    }
  }

 private:
  double GetUsagePercent() const {
    return static_cast<double>(cpu_time_) / wallclock_time_ * 100.0;
  }

  const Config config_;
  int64_t cpu_time_ = 0;
  int64_t wallclock_time_ = 0;
};

VideoCodecTestFixtureImpl::VideoCodecTestFixtureImpl(Config config)
    : encoder_factory_(std::make_unique<webrtc::VideoEncoderFactoryTemplate<
                           webrtc::LibvpxVp8EncoderTemplateAdapter,
                           webrtc::LibvpxVp9EncoderTemplateAdapter,
                           webrtc::OpenH264EncoderTemplateAdapter,
                           webrtc::LibaomAv1EncoderTemplateAdapter>>()),
      decoder_factory_(std::make_unique<webrtc::VideoDecoderFactoryTemplate<
                           webrtc::LibvpxVp8DecoderTemplateAdapter,
                           webrtc::LibvpxVp9DecoderTemplateAdapter,
                           webrtc::OpenH264DecoderTemplateAdapter,
                           webrtc::Dav1dDecoderTemplateAdapter>>()),
      config_(config) {}

VideoCodecTestFixtureImpl::VideoCodecTestFixtureImpl(
    Config config,
    std::unique_ptr<VideoDecoderFactory> decoder_factory,
    std::unique_ptr<VideoEncoderFactory> encoder_factory)
    : encoder_factory_(std::move(encoder_factory)),
      decoder_factory_(std::move(decoder_factory)),
      config_(config) {}

VideoCodecTestFixtureImpl::~VideoCodecTestFixtureImpl() = default;

// Processes all frames in the clip and verifies the result.
void VideoCodecTestFixtureImpl::RunTest(
    const std::vector<RateProfile>& rate_profiles,
    const std::vector<RateControlThresholds>* rc_thresholds,
    const std::vector<QualityThresholds>* quality_thresholds,
    const BitstreamThresholds* bs_thresholds) {
  RTC_DCHECK(!rate_profiles.empty());

  // To emulate operation on a production VideoStreamEncoder, we call the
  // codecs on a task queue.
  TaskQueueForTest task_queue("VidProc TQ");

  bool is_setup_succeeded = SetUpAndInitObjects(
      &task_queue, rate_profiles[0].target_kbps, rate_profiles[0].input_fps);
  EXPECT_TRUE(is_setup_succeeded);
  if (!is_setup_succeeded) {
    ReleaseAndCloseObjects(&task_queue);
    return;
  }

  PrintSettings(&task_queue);
  ProcessAllFrames(&task_queue, rate_profiles);
  ReleaseAndCloseObjects(&task_queue);

  AnalyzeAllFrames(rate_profiles, rc_thresholds, quality_thresholds,
                   bs_thresholds);
}

void VideoCodecTestFixtureImpl::ProcessAllFrames(
    TaskQueueForTest* task_queue,
    const std::vector<RateProfile>& rate_profiles) {
  // Set initial rates.
  auto rate_profile = rate_profiles.begin();
  task_queue->PostTask([this, rate_profile] {
    processor_->SetRates(rate_profile->target_kbps, rate_profile->input_fps);
  });

  cpu_process_time_->Start();

  for (size_t frame_num = 0; frame_num < config_.num_frames; ++frame_num) {
    auto next_rate_profile = std::next(rate_profile);
    if (next_rate_profile != rate_profiles.end() &&
        frame_num == next_rate_profile->frame_num) {
      rate_profile = next_rate_profile;
      task_queue->PostTask([this, rate_profile] {
        processor_->SetRates(rate_profile->target_kbps,
                             rate_profile->input_fps);
      });
    }

    task_queue->PostTask([this] { processor_->ProcessFrame(); });

    if (RunEncodeInRealTime(config_)) {
      // Roughly pace the frames.
      const int frame_duration_ms =
          std::ceil(rtc::kNumMillisecsPerSec / rate_profile->input_fps);
      SleepMs(frame_duration_ms);
    }
  }

  task_queue->PostTask([this] { processor_->Finalize(); });

  // Wait until we know that the last frame has been sent for encode.
  task_queue->SendTask([] {});

  // Give the VideoProcessor pipeline some time to process the last frame,
  // and then release the codecs.
  SleepMs(1 * rtc::kNumMillisecsPerSec);
  cpu_process_time_->Stop();
}

void VideoCodecTestFixtureImpl::AnalyzeAllFrames(
    const std::vector<RateProfile>& rate_profiles,
    const std::vector<RateControlThresholds>* rc_thresholds,
    const std::vector<QualityThresholds>* quality_thresholds,
    const BitstreamThresholds* bs_thresholds) {
  for (size_t rate_profile_idx = 0; rate_profile_idx < rate_profiles.size();
       ++rate_profile_idx) {
    const size_t first_frame_num = rate_profiles[rate_profile_idx].frame_num;
    const size_t last_frame_num =
        rate_profile_idx + 1 < rate_profiles.size()
            ? rate_profiles[rate_profile_idx + 1].frame_num - 1
            : config_.num_frames - 1;
    RTC_CHECK(last_frame_num >= first_frame_num);

    VideoStatistics send_stat = stats_.SliceAndCalcAggregatedVideoStatistic(
        first_frame_num, last_frame_num);
    RTC_LOG(LS_INFO) << "==> Send stats";
    RTC_LOG(LS_INFO) << send_stat.ToString("send_") << "\n";

    std::vector<VideoStatistics> layer_stats =
        stats_.SliceAndCalcLayerVideoStatistic(first_frame_num, last_frame_num);
    RTC_LOG(LS_INFO) << "==> Receive stats";
    for (const auto& layer_stat : layer_stats) {
      RTC_LOG(LS_INFO) << layer_stat.ToString("recv_") << "\n";

      // For perf dashboard.
      char modifier_buf[256];
      rtc::SimpleStringBuilder modifier(modifier_buf);
      modifier << "_r" << rate_profile_idx << "_sl" << layer_stat.spatial_idx;

      auto PrintResultHelper = [&modifier, this](
                                   absl::string_view measurement, double value,
                                   Unit unit,
                                   absl::string_view non_standard_unit_suffix,
                                   ImprovementDirection improvement_direction) {
        rtc::StringBuilder metric_name(measurement);
        metric_name << modifier.str() << non_standard_unit_suffix;
        GetGlobalMetricsLogger()->LogSingleValueMetric(
            metric_name.str(), config_.test_name, value, unit,
            improvement_direction);
      };

      if (layer_stat.temporal_idx == config_.NumberOfTemporalLayers() - 1) {
        PrintResultHelper("enc_speed", layer_stat.enc_speed_fps,
                          Unit::kUnitless, /*non_standard_unit_suffix=*/"_fps",
                          ImprovementDirection::kBiggerIsBetter);
        PrintResultHelper("avg_key_frame_size",
                          layer_stat.avg_key_frame_size_bytes, Unit::kBytes,
                          /*non_standard_unit_suffix=*/"",
                          ImprovementDirection::kNeitherIsBetter);
        PrintResultHelper("num_key_frames", layer_stat.num_key_frames,
                          Unit::kCount,
                          /*non_standard_unit_suffix=*/"",
                          ImprovementDirection::kNeitherIsBetter);
        printf("\n");
      }

      modifier << "tl" << layer_stat.temporal_idx;
      PrintResultHelper("dec_speed", layer_stat.dec_speed_fps, Unit::kUnitless,
                        /*non_standard_unit_suffix=*/"_fps",
                        ImprovementDirection::kBiggerIsBetter);
      PrintResultHelper("avg_delta_frame_size",
                        layer_stat.avg_delta_frame_size_bytes, Unit::kBytes,
                        /*non_standard_unit_suffix=*/"",
                        ImprovementDirection::kNeitherIsBetter);
      PrintResultHelper("bitrate", layer_stat.bitrate_kbps,
                        Unit::kKilobitsPerSecond,
                        /*non_standard_unit_suffix=*/"",
                        ImprovementDirection::kNeitherIsBetter);
      PrintResultHelper("framerate", layer_stat.framerate_fps, Unit::kUnitless,
                        /*non_standard_unit_suffix=*/"_fps",
                        ImprovementDirection::kNeitherIsBetter);
      PrintResultHelper("avg_psnr_y", layer_stat.avg_psnr_y, Unit::kUnitless,
                        /*non_standard_unit_suffix=*/"_dB",
                        ImprovementDirection::kBiggerIsBetter);
      PrintResultHelper("avg_psnr_u", layer_stat.avg_psnr_u, Unit::kUnitless,
                        /*non_standard_unit_suffix=*/"_dB",
                        ImprovementDirection::kBiggerIsBetter);
      PrintResultHelper("avg_psnr_v", layer_stat.avg_psnr_v, Unit::kUnitless,
                        /*non_standard_unit_suffix=*/"_dB",
                        ImprovementDirection::kBiggerIsBetter);
      PrintResultHelper("min_psnr_yuv", layer_stat.min_psnr, Unit::kUnitless,
                        /*non_standard_unit_suffix=*/"_dB",
                        ImprovementDirection::kBiggerIsBetter);
      PrintResultHelper("avg_qp", layer_stat.avg_qp, Unit::kUnitless,
                        /*non_standard_unit_suffix=*/"",
                        ImprovementDirection::kSmallerIsBetter);
      printf("\n");
      if (layer_stat.temporal_idx == config_.NumberOfTemporalLayers() - 1) {
        printf("\n");
      }
    }

    const RateControlThresholds* rc_threshold =
        rc_thresholds ? &(*rc_thresholds)[rate_profile_idx] : nullptr;
    const QualityThresholds* quality_threshold =
        quality_thresholds ? &(*quality_thresholds)[rate_profile_idx] : nullptr;

    VerifyVideoStatistic(send_stat, rc_threshold, quality_threshold,
                         bs_thresholds,
                         rate_profiles[rate_profile_idx].target_kbps,
                         rate_profiles[rate_profile_idx].input_fps);
  }

  if (config_.print_frame_level_stats) {
    RTC_LOG(LS_INFO) << "==> Frame stats";
    std::vector<VideoCodecTestStats::FrameStatistics> frame_stats =
        stats_.GetFrameStatistics();
    for (const auto& frame_stat : frame_stats) {
      RTC_LOG(LS_INFO) << frame_stat.ToString();
    }
  }

  cpu_process_time_->Print();
}

void VideoCodecTestFixtureImpl::VerifyVideoStatistic(
    const VideoStatistics& video_stat,
    const RateControlThresholds* rc_thresholds,
    const QualityThresholds* quality_thresholds,
    const BitstreamThresholds* bs_thresholds,
    size_t target_bitrate_kbps,
    double input_framerate_fps) {
  if (rc_thresholds) {
    const float bitrate_mismatch_percent =
        100 * std::fabs(1.0f * video_stat.bitrate_kbps - target_bitrate_kbps) /
        target_bitrate_kbps;
    const float framerate_mismatch_percent =
        100 * std::fabs(video_stat.framerate_fps - input_framerate_fps) /
        input_framerate_fps;
    EXPECT_LE(bitrate_mismatch_percent,
              rc_thresholds->max_avg_bitrate_mismatch_percent);
    EXPECT_LE(video_stat.time_to_reach_target_bitrate_sec,
              rc_thresholds->max_time_to_reach_target_bitrate_sec);
    EXPECT_LE(framerate_mismatch_percent,
              rc_thresholds->max_avg_framerate_mismatch_percent);
    EXPECT_LE(video_stat.avg_delay_sec,
              rc_thresholds->max_avg_buffer_level_sec);
    EXPECT_LE(video_stat.max_key_frame_delay_sec,
              rc_thresholds->max_max_key_frame_delay_sec);
    EXPECT_LE(video_stat.max_delta_frame_delay_sec,
              rc_thresholds->max_max_delta_frame_delay_sec);
    EXPECT_LE(video_stat.num_spatial_resizes,
              rc_thresholds->max_num_spatial_resizes);
    EXPECT_LE(video_stat.num_key_frames, rc_thresholds->max_num_key_frames);
  }

  if (quality_thresholds) {
    EXPECT_GT(video_stat.avg_psnr, quality_thresholds->min_avg_psnr);
    EXPECT_GT(video_stat.min_psnr, quality_thresholds->min_min_psnr);

    // SSIM calculation is not optimized and thus it is disabled in real-time
    // mode.
    if (!config_.encode_in_real_time) {
      EXPECT_GT(video_stat.avg_ssim, quality_thresholds->min_avg_ssim);
      EXPECT_GT(video_stat.min_ssim, quality_thresholds->min_min_ssim);
    }
  }

  if (bs_thresholds) {
    EXPECT_LE(video_stat.max_nalu_size_bytes,
              bs_thresholds->max_max_nalu_size_bytes);
  }
}

bool VideoCodecTestFixtureImpl::CreateEncoderAndDecoder() {
  const Environment env = CreateEnvironment();

  SdpVideoFormat encoder_format(CreateSdpVideoFormat(config_));
  SdpVideoFormat decoder_format = encoder_format;

  // Override encoder and decoder formats with explicitly provided ones.
  if (config_.encoder_format) {
    RTC_DCHECK_EQ(config_.encoder_format->name, config_.codec_name);
    encoder_format = *config_.encoder_format;
  }

  if (config_.decoder_format) {
    RTC_DCHECK_EQ(config_.decoder_format->name, config_.codec_name);
    decoder_format = *config_.decoder_format;
  }

  encoder_ = encoder_factory_->CreateVideoEncoder(encoder_format);
  EXPECT_TRUE(encoder_) << "Encoder not successfully created.";
  if (encoder_ == nullptr) {
    return false;
  }

  const size_t num_simulcast_or_spatial_layers = std::max(
      config_.NumberOfSimulcastStreams(), config_.NumberOfSpatialLayers());
  for (size_t i = 0; i < num_simulcast_or_spatial_layers; ++i) {
    std::unique_ptr<VideoDecoder> decoder =
        decoder_factory_->Create(env, decoder_format);
    EXPECT_TRUE(decoder) << "Decoder not successfully created.";
    if (decoder == nullptr) {
      return false;
    }
    decoders_.push_back(std::move(decoder));
  }

  return true;
}

void VideoCodecTestFixtureImpl::DestroyEncoderAndDecoder() {
  decoders_.clear();
  encoder_.reset();
}

VideoCodecTestStats& VideoCodecTestFixtureImpl::GetStats() {
  return stats_;
}

bool VideoCodecTestFixtureImpl::SetUpAndInitObjects(
    TaskQueueForTest* task_queue,
    size_t initial_bitrate_kbps,
    double initial_framerate_fps) {
  config_.codec_settings.minBitrate = 0;
  config_.codec_settings.startBitrate = static_cast<int>(initial_bitrate_kbps);
  config_.codec_settings.maxFramerate = std::ceil(initial_framerate_fps);

  int clip_width = config_.clip_width.value_or(config_.codec_settings.width);
  int clip_height = config_.clip_height.value_or(config_.codec_settings.height);

  // Create file objects for quality analysis.
  source_frame_reader_ = CreateYuvFrameReader(
      config_.filepath,
      Resolution({.width = clip_width, .height = clip_height}),
      YuvFrameReaderImpl::RepeatMode::kPingPong);

  RTC_DCHECK(encoded_frame_writers_.empty());
  RTC_DCHECK(decoded_frame_writers_.empty());

  stats_.Clear();

  cpu_process_time_.reset(new CpuProcessTime(config_));

  bool is_codec_created = false;
  task_queue->SendTask([this, &is_codec_created]() {
    is_codec_created = CreateEncoderAndDecoder();
  });

  if (!is_codec_created) {
    return false;
  }

  if (config_.visualization_params.save_encoded_ivf ||
      config_.visualization_params.save_decoded_y4m) {
    std::string encoder_name = GetCodecName(task_queue, /*is_encoder=*/true);
    encoder_name = absl::StrReplaceAll(encoder_name, {{":", ""}, {" ", "-"}});

    const size_t num_simulcast_or_spatial_layers = std::max(
        config_.NumberOfSimulcastStreams(), config_.NumberOfSpatialLayers());
    const size_t num_temporal_layers = config_.NumberOfTemporalLayers();
    for (size_t simulcast_svc_idx = 0;
         simulcast_svc_idx < num_simulcast_or_spatial_layers;
         ++simulcast_svc_idx) {
      const std::string output_filename_base =
          JoinFilename(config_.output_path,
                       FilenameWithParams(config_) + "_" + encoder_name +
                           "_sl" + std::to_string(simulcast_svc_idx));

      if (config_.visualization_params.save_encoded_ivf) {
        for (size_t temporal_idx = 0; temporal_idx < num_temporal_layers;
             ++temporal_idx) {
          const std::string output_file_path = output_filename_base + "tl" +
                                               std::to_string(temporal_idx) +
                                               ".ivf";
          FileWrapper ivf_file = FileWrapper::OpenWriteOnly(output_file_path);

          const VideoProcessor::LayerKey layer_key(simulcast_svc_idx,
                                                   temporal_idx);
          encoded_frame_writers_[layer_key] =
              IvfFileWriter::Wrap(std::move(ivf_file), /*byte_limit=*/0);
        }
      }

      if (config_.visualization_params.save_decoded_y4m) {
        FrameWriter* decoded_frame_writer = new Y4mFrameWriterImpl(
            output_filename_base + ".y4m", config_.codec_settings.width,
            config_.codec_settings.height, config_.codec_settings.maxFramerate);
        EXPECT_TRUE(decoded_frame_writer->Init());
        decoded_frame_writers_.push_back(
            std::unique_ptr<FrameWriter>(decoded_frame_writer));
      }
    }
  }

  task_queue->SendTask([this]() {
    processor_ = std::make_unique<VideoProcessor>(
        encoder_.get(), &decoders_, source_frame_reader_.get(), config_,
        &stats_, &encoded_frame_writers_,
        decoded_frame_writers_.empty() ? nullptr : &decoded_frame_writers_);
  });
  return true;
}

void VideoCodecTestFixtureImpl::ReleaseAndCloseObjects(
    TaskQueueForTest* task_queue) {
  task_queue->SendTask([this]() {
    processor_.reset();
    // The VideoProcessor must be destroyed before the codecs.
    DestroyEncoderAndDecoder();
  });

  source_frame_reader_.reset();

  // Close visualization files.
  for (auto& encoded_frame_writer : encoded_frame_writers_) {
    EXPECT_TRUE(encoded_frame_writer.second->Close());
  }
  encoded_frame_writers_.clear();
  for (auto& decoded_frame_writer : decoded_frame_writers_) {
    decoded_frame_writer->Close();
  }
  decoded_frame_writers_.clear();
}

std::string VideoCodecTestFixtureImpl::GetCodecName(
    TaskQueueForTest* task_queue,
    bool is_encoder) const {
  std::string codec_name;
  task_queue->SendTask([this, is_encoder, &codec_name] {
    if (is_encoder) {
      codec_name = encoder_->GetEncoderInfo().implementation_name;
    } else {
      codec_name = decoders_.at(0)->ImplementationName();
    }
  });
  return codec_name;
}

void VideoCodecTestFixtureImpl::PrintSettings(
    TaskQueueForTest* task_queue) const {
  RTC_LOG(LS_INFO) << "==> Config";
  RTC_LOG(LS_INFO) << config_.ToString();

  RTC_LOG(LS_INFO) << "==> Codec names";
  RTC_LOG(LS_INFO) << "enc_impl_name: "
                   << GetCodecName(task_queue, /*is_encoder=*/true);
  RTC_LOG(LS_INFO) << "dec_impl_name: "
                   << GetCodecName(task_queue, /*is_encoder=*/false);
}

}  // namespace test
}  // namespace webrtc
