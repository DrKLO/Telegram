/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stddef.h>
#include <stdint.h>

#include <map>
#include <memory>
#include <ostream>
#include <tuple>
#include <vector>

#include "absl/types/optional.h"
#include "api/units/data_size.h"
#include "api/units/time_delta.h"
#include "api/video_codecs/video_codec.h"
#include "api/video_codecs/video_encoder.h"
#include "modules/video_coding/codecs/av1/dav1d_decoder.h"
#include "modules/video_coding/codecs/av1/libaom_av1_encoder.h"
#include "modules/video_coding/codecs/test/encoded_video_frame_producer.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "modules/video_coding/include/video_error_codes.h"
#include "modules/video_coding/svc/create_scalability_structure.h"
#include "modules/video_coding/svc/scalability_mode_util.h"
#include "modules/video_coding/svc/scalable_video_controller.h"
#include "modules/video_coding/svc/scalable_video_controller_no_layering.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {
namespace {

using ::testing::ContainerEq;
using ::testing::Each;
using ::testing::ElementsAreArray;
using ::testing::Ge;
using ::testing::IsEmpty;
using ::testing::Not;
using ::testing::NotNull;
using ::testing::Optional;
using ::testing::Pointwise;
using ::testing::SizeIs;
using ::testing::Truly;
using ::testing::Values;

// Use small resolution for this test to make it faster.
constexpr int kWidth = 320;
constexpr int kHeight = 180;
constexpr int kFramerate = 30;

VideoCodec DefaultCodecSettings() {
  VideoCodec codec_settings;
  codec_settings.SetScalabilityMode(ScalabilityMode::kL1T1);
  codec_settings.width = kWidth;
  codec_settings.height = kHeight;
  codec_settings.maxFramerate = kFramerate;
  codec_settings.maxBitrate = 1000;
  codec_settings.startBitrate = 1;
  codec_settings.qpMax = 63;
  return codec_settings;
}
VideoEncoder::Settings DefaultEncoderSettings() {
  return VideoEncoder::Settings(
      VideoEncoder::Capabilities(/*loss_notification=*/false),
      /*number_of_cores=*/1, /*max_payload_size=*/1200);
}

class TestAv1Decoder {
 public:
  explicit TestAv1Decoder(int decoder_id)
      : decoder_id_(decoder_id), decoder_(CreateDav1dDecoder()) {
    if (decoder_ == nullptr) {
      ADD_FAILURE() << "Failed to create a decoder#" << decoder_id_;
      return;
    }
    EXPECT_TRUE(decoder_->Configure({}));
    EXPECT_EQ(decoder_->RegisterDecodeCompleteCallback(&callback_),
              WEBRTC_VIDEO_CODEC_OK);
  }
  // This class requires pointer stability and thus not copyable nor movable.
  TestAv1Decoder(const TestAv1Decoder&) = delete;
  TestAv1Decoder& operator=(const TestAv1Decoder&) = delete;

  void Decode(int64_t frame_id, const EncodedImage& image) {
    ASSERT_THAT(decoder_, NotNull());
    int32_t error =
        decoder_->Decode(image, /*render_time_ms=*/image.capture_time_ms_);
    if (error != WEBRTC_VIDEO_CODEC_OK) {
      ADD_FAILURE() << "Failed to decode frame id " << frame_id
                    << " with error code " << error << " by decoder#"
                    << decoder_id_;
      return;
    }
    decoded_ids_.push_back(frame_id);
  }

  const std::vector<int64_t>& decoded_frame_ids() const { return decoded_ids_; }
  size_t num_output_frames() const { return callback_.num_called(); }

 private:
  // Decoder callback that only counts how many times it was called.
  // While it is tempting to replace it with a simple mock, that one requires
  // to set expectation on number of calls in advance. Tests below unsure about
  // expected number of calls until after calls are done.
  class DecoderCallback : public DecodedImageCallback {
   public:
    size_t num_called() const { return num_called_; }

   private:
    int32_t Decoded(VideoFrame& /*decoded_image*/) override {
      ++num_called_;
      return 0;
    }
    void Decoded(VideoFrame& /*decoded_image*/,
                 absl::optional<int32_t> /*decode_time_ms*/,
                 absl::optional<uint8_t> /*qp*/) override {
      ++num_called_;
    }

    int num_called_ = 0;
  };

  const int decoder_id_;
  std::vector<int64_t> decoded_ids_;
  DecoderCallback callback_;
  const std::unique_ptr<VideoDecoder> decoder_;
};

TEST(LibaomAv1Test, EncodeDecode) {
  TestAv1Decoder decoder(0);
  std::unique_ptr<VideoEncoder> encoder = CreateLibaomAv1Encoder();
  VideoCodec codec_settings = DefaultCodecSettings();
  ASSERT_EQ(encoder->InitEncode(&codec_settings, DefaultEncoderSettings()),
            WEBRTC_VIDEO_CODEC_OK);

  VideoBitrateAllocation allocation;
  allocation.SetBitrate(0, 0, 300000);
  encoder->SetRates(VideoEncoder::RateControlParameters(
      allocation, codec_settings.maxFramerate));

  std::vector<EncodedVideoFrameProducer::EncodedFrame> encoded_frames =
      EncodedVideoFrameProducer(*encoder).SetNumInputFrames(4).Encode();
  for (size_t frame_id = 0; frame_id < encoded_frames.size(); ++frame_id) {
    decoder.Decode(static_cast<int64_t>(frame_id),
                   encoded_frames[frame_id].encoded_image);
  }

  // Check encoder produced some frames for decoder to decode.
  ASSERT_THAT(encoded_frames, Not(IsEmpty()));
  // Check decoder found all of them valid.
  EXPECT_THAT(decoder.decoded_frame_ids(), SizeIs(encoded_frames.size()));
  // Check each of them produced an output frame.
  EXPECT_EQ(decoder.num_output_frames(), decoder.decoded_frame_ids().size());
}

struct LayerId {
  friend bool operator==(const LayerId& lhs, const LayerId& rhs) {
    return std::tie(lhs.spatial_id, lhs.temporal_id) ==
           std::tie(rhs.spatial_id, rhs.temporal_id);
  }
  friend bool operator<(const LayerId& lhs, const LayerId& rhs) {
    return std::tie(lhs.spatial_id, lhs.temporal_id) <
           std::tie(rhs.spatial_id, rhs.temporal_id);
  }
  friend std::ostream& operator<<(std::ostream& s, const LayerId& layer) {
    return s << "S" << layer.spatial_id << "T" << layer.temporal_id;
  }

  int spatial_id = 0;
  int temporal_id = 0;
};

struct SvcTestParam {
  ScalabilityMode GetScalabilityMode() const {
    absl::optional<ScalabilityMode> scalability_mode =
        ScalabilityModeFromString(name);
    RTC_CHECK(scalability_mode.has_value());
    return *scalability_mode;
  }

  std::string name;
  int num_frames_to_generate;
  std::map<LayerId, DataRate> configured_bitrates;
};

class LibaomAv1SvcTest : public ::testing::TestWithParam<SvcTestParam> {};

TEST_P(LibaomAv1SvcTest, EncodeAndDecodeAllDecodeTargets) {
  const SvcTestParam param = GetParam();
  std::unique_ptr<ScalableVideoController> svc_controller =
      CreateScalabilityStructure(param.GetScalabilityMode());
  ASSERT_TRUE(svc_controller);
  VideoBitrateAllocation allocation;
  if (param.configured_bitrates.empty()) {
    ScalableVideoController::StreamLayersConfig config =
        svc_controller->StreamConfig();
    for (int sid = 0; sid < config.num_spatial_layers; ++sid) {
      for (int tid = 0; tid < config.num_temporal_layers; ++tid) {
        allocation.SetBitrate(sid, tid, 100'000);
      }
    }
  } else {
    for (const auto& kv : param.configured_bitrates) {
      allocation.SetBitrate(kv.first.spatial_id, kv.first.temporal_id,
                            kv.second.bps());
    }
  }

  size_t num_decode_targets =
      svc_controller->DependencyStructure().num_decode_targets;

  std::unique_ptr<VideoEncoder> encoder = CreateLibaomAv1Encoder();
  VideoCodec codec_settings = DefaultCodecSettings();
  codec_settings.SetScalabilityMode(GetParam().GetScalabilityMode());
  ASSERT_EQ(encoder->InitEncode(&codec_settings, DefaultEncoderSettings()),
            WEBRTC_VIDEO_CODEC_OK);
  encoder->SetRates(VideoEncoder::RateControlParameters(
      allocation, codec_settings.maxFramerate));
  std::vector<EncodedVideoFrameProducer::EncodedFrame> encoded_frames =
      EncodedVideoFrameProducer(*encoder)
          .SetNumInputFrames(GetParam().num_frames_to_generate)
          .SetResolution({kWidth, kHeight})
          .Encode();

  ASSERT_THAT(
      encoded_frames,
      Each(Truly([&](const EncodedVideoFrameProducer::EncodedFrame& frame) {
        return frame.codec_specific_info.generic_frame_info &&
               frame.codec_specific_info.generic_frame_info
                       ->decode_target_indications.size() == num_decode_targets;
      })));

  for (size_t dt = 0; dt < num_decode_targets; ++dt) {
    TestAv1Decoder decoder(dt);
    std::vector<int64_t> requested_ids;
    for (int64_t frame_id = 0;
         frame_id < static_cast<int64_t>(encoded_frames.size()); ++frame_id) {
      const EncodedVideoFrameProducer::EncodedFrame& frame =
          encoded_frames[frame_id];
      if (frame.codec_specific_info.generic_frame_info
              ->decode_target_indications[dt] !=
          DecodeTargetIndication::kNotPresent) {
        requested_ids.push_back(frame_id);
        decoder.Decode(frame_id, frame.encoded_image);
      }
      EXPECT_THAT(frame.codec_specific_info.scalability_mode,
                  Optional(param.GetScalabilityMode()));
    }

    ASSERT_THAT(requested_ids, SizeIs(Ge(2u)));
    // Check decoder found all of them valid.
    EXPECT_THAT(decoder.decoded_frame_ids(), ContainerEq(requested_ids))
        << "Decoder#" << dt;
    // Check each of them produced an output frame.
    EXPECT_EQ(decoder.num_output_frames(), decoder.decoded_frame_ids().size())
        << "Decoder#" << dt;
  }
}

MATCHER(SameLayerIdAndBitrateIsNear, "") {
  // First check if layer id is the same.
  return std::get<0>(arg).first == std::get<1>(arg).first &&
         // check measured bitrate is not much lower than requested.
         std::get<0>(arg).second >= std::get<1>(arg).second * 0.75 &&
         // check measured bitrate is not much larger than requested.
         std::get<0>(arg).second <= std::get<1>(arg).second * 1.25;
}

TEST_P(LibaomAv1SvcTest, SetRatesMatchMeasuredBitrate) {
  const SvcTestParam param = GetParam();
  if (param.configured_bitrates.empty()) {
    // Rates are not configured for this particular structure, skip the test.
    return;
  }
  constexpr TimeDelta kDuration = TimeDelta::Seconds(5);

  VideoBitrateAllocation allocation;
  for (const auto& kv : param.configured_bitrates) {
    allocation.SetBitrate(kv.first.spatial_id, kv.first.temporal_id,
                          kv.second.bps());
  }

  std::unique_ptr<VideoEncoder> encoder = CreateLibaomAv1Encoder();
  ASSERT_TRUE(encoder);
  VideoCodec codec_settings = DefaultCodecSettings();
  codec_settings.SetScalabilityMode(param.GetScalabilityMode());
  codec_settings.maxBitrate = allocation.get_sum_kbps();
  codec_settings.maxFramerate = 30;
  ASSERT_EQ(encoder->InitEncode(&codec_settings, DefaultEncoderSettings()),
            WEBRTC_VIDEO_CODEC_OK);

  encoder->SetRates(VideoEncoder::RateControlParameters(
      allocation, codec_settings.maxFramerate));

  std::vector<EncodedVideoFrameProducer::EncodedFrame> encoded_frames =
      EncodedVideoFrameProducer(*encoder)
          .SetNumInputFrames(codec_settings.maxFramerate * kDuration.seconds())
          .SetResolution({codec_settings.width, codec_settings.height})
          .SetFramerateFps(codec_settings.maxFramerate)
          .Encode();

  // Calculate size of each layer.
  std::map<LayerId, DataSize> layer_size;
  for (const auto& frame : encoded_frames) {
    ASSERT_TRUE(frame.codec_specific_info.generic_frame_info);
    const auto& layer = *frame.codec_specific_info.generic_frame_info;
    LayerId layer_id = {layer.spatial_id, layer.temporal_id};
    // This is almost same as
    // layer_size[layer_id] += DataSize::Bytes(frame.encoded_image.size());
    // but avoids calling deleted default constructor for DataSize.
    layer_size.emplace(layer_id, DataSize::Zero()).first->second +=
        DataSize::Bytes(frame.encoded_image.size());
  }
  // Convert size of the layer into bitrate of that layer.
  std::vector<std::pair<LayerId, DataRate>> measured_bitrates;
  for (const auto& kv : layer_size) {
    measured_bitrates.emplace_back(kv.first, kv.second / kDuration);
  }
  EXPECT_THAT(measured_bitrates, Pointwise(SameLayerIdAndBitrateIsNear(),
                                           param.configured_bitrates));
}

INSTANTIATE_TEST_SUITE_P(
    Svc,
    LibaomAv1SvcTest,
    Values(SvcTestParam{"L1T1", /*num_frames_to_generate=*/4},
           SvcTestParam{"L1T2",
                        /*num_frames_to_generate=*/4,
                        /*configured_bitrates=*/
                        {{{0, 0}, DataRate::KilobitsPerSec(60)},
                         {{0, 1}, DataRate::KilobitsPerSec(40)}}},
           SvcTestParam{"L1T3", /*num_frames_to_generate=*/8},
           SvcTestParam{"L2T1",
                        /*num_frames_to_generate=*/3,
                        /*configured_bitrates=*/
                        {{{0, 0}, DataRate::KilobitsPerSec(30)},
                         {{1, 0}, DataRate::KilobitsPerSec(70)}}},
           SvcTestParam{"L2T1h",
                        /*num_frames_to_generate=*/3,
                        /*configured_bitrates=*/
                        {{{0, 0}, DataRate::KilobitsPerSec(30)},
                         {{1, 0}, DataRate::KilobitsPerSec(70)}}},
           SvcTestParam{"L2T1_KEY", /*num_frames_to_generate=*/3},
           SvcTestParam{"L3T1", /*num_frames_to_generate=*/3},
           SvcTestParam{"L3T3", /*num_frames_to_generate=*/8},
           SvcTestParam{"S2T1", /*num_frames_to_generate=*/3},
           // TODO: bugs.webrtc.org/15715 - Re-enable once AV1 is fixed.
           // SvcTestParam{"S3T3", /*num_frames_to_generate=*/8},
           SvcTestParam{"L2T2", /*num_frames_to_generate=*/4},
           SvcTestParam{"L2T2_KEY", /*num_frames_to_generate=*/4},
           SvcTestParam{"L2T2_KEY_SHIFT",
                        /*num_frames_to_generate=*/4,
                        /*configured_bitrates=*/
                        {{{0, 0}, DataRate::KilobitsPerSec(70)},
                         {{0, 1}, DataRate::KilobitsPerSec(30)},
                         {{1, 0}, DataRate::KilobitsPerSec(110)},
                         {{1, 1}, DataRate::KilobitsPerSec(80)}}}),
    [](const testing::TestParamInfo<SvcTestParam>& info) {
      return info.param.name;
    });

}  // namespace
}  // namespace webrtc
