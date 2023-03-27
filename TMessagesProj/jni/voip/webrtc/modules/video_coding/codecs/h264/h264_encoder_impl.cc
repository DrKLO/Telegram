/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 *
 */

// Everything declared/defined in this header is only required when WebRTC is
// build with H264 support, please do not move anything out of the
// #ifdef unless needed and tested.
#ifdef WEBRTC_USE_H264

#include "modules/video_coding/codecs/h264/h264_encoder_impl.h"

#include <algorithm>
#include <limits>
#include <string>

#include "absl/strings/match.h"
#include "common_video/libyuv/include/webrtc_libyuv.h"
#include "modules/video_coding/svc/create_scalability_structure.h"
#include "modules/video_coding/utility/simulcast_rate_allocator.h"
#include "modules/video_coding/utility/simulcast_utility.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/metrics.h"
#include "third_party/libyuv/include/libyuv/convert.h"
#include "third_party/libyuv/include/libyuv/scale.h"
#include "third_party/openh264/src/codec/api/svc/codec_api.h"
#include "third_party/openh264/src/codec/api/svc/codec_app_def.h"
#include "third_party/openh264/src/codec/api/svc/codec_def.h"
#include "third_party/openh264/src/codec/api/svc/codec_ver.h"

namespace webrtc {

namespace {

const bool kOpenH264EncoderDetailedLogging = false;

// QP scaling thresholds.
static const int kLowH264QpThreshold = 24;
static const int kHighH264QpThreshold = 37;

// Used by histograms. Values of entries should not be changed.
enum H264EncoderImplEvent {
  kH264EncoderEventInit = 0,
  kH264EncoderEventError = 1,
  kH264EncoderEventMax = 16,
};

int NumberOfThreads(int width, int height, int number_of_cores) {
  // TODO(hbos): In Chromium, multiple threads do not work with sandbox on Mac,
  // see crbug.com/583348. Until further investigated, only use one thread.
  //  if (width * height >= 1920 * 1080 && number_of_cores > 8) {
  //    return 8;  // 8 threads for 1080p on high perf machines.
  //  } else if (width * height > 1280 * 960 && number_of_cores >= 6) {
  //    return 3;  // 3 threads for 1080p.
  //  } else if (width * height > 640 * 480 && number_of_cores >= 3) {
  //    return 2;  // 2 threads for qHD/HD.
  //  } else {
  //    return 1;  // 1 thread for VGA or less.
  //  }
  // TODO(sprang): Also check sSliceArgument.uiSliceNum om GetEncoderPrams(),
  //               before enabling multithreading here.
  return 1;
}

VideoFrameType ConvertToVideoFrameType(EVideoFrameType type) {
  switch (type) {
    case videoFrameTypeIDR:
      return VideoFrameType::kVideoFrameKey;
    case videoFrameTypeSkip:
    case videoFrameTypeI:
    case videoFrameTypeP:
    case videoFrameTypeIPMixed:
      return VideoFrameType::kVideoFrameDelta;
    case videoFrameTypeInvalid:
      break;
  }
  RTC_DCHECK_NOTREACHED() << "Unexpected/invalid frame type: " << type;
  return VideoFrameType::kEmptyFrame;
}

}  // namespace

// Helper method used by H264EncoderImpl::Encode.
// Copies the encoded bytes from `info` to `encoded_image`. The
// `encoded_image->_buffer` may be deleted and reallocated if a bigger buffer is
// required.
//
// After OpenH264 encoding, the encoded bytes are stored in `info` spread out
// over a number of layers and "NAL units". Each NAL unit is a fragment starting
// with the four-byte start code {0,0,0,1}. All of this data (including the
// start codes) is copied to the `encoded_image->_buffer`.
static void RtpFragmentize(EncodedImage* encoded_image, SFrameBSInfo* info) {
  // Calculate minimum buffer size required to hold encoded data.
  size_t required_capacity = 0;
  size_t fragments_count = 0;
  for (int layer = 0; layer < info->iLayerNum; ++layer) {
    const SLayerBSInfo& layerInfo = info->sLayerInfo[layer];
    for (int nal = 0; nal < layerInfo.iNalCount; ++nal, ++fragments_count) {
      RTC_CHECK_GE(layerInfo.pNalLengthInByte[nal], 0);
      // Ensure `required_capacity` will not overflow.
      RTC_CHECK_LE(layerInfo.pNalLengthInByte[nal],
                   std::numeric_limits<size_t>::max() - required_capacity);
      required_capacity += layerInfo.pNalLengthInByte[nal];
    }
  }
  auto buffer = EncodedImageBuffer::Create(required_capacity);
  encoded_image->SetEncodedData(buffer);

  // Iterate layers and NAL units, note each NAL unit as a fragment and copy
  // the data to `encoded_image->_buffer`.
  const uint8_t start_code[4] = {0, 0, 0, 1};
  size_t frag = 0;
  encoded_image->set_size(0);
  for (int layer = 0; layer < info->iLayerNum; ++layer) {
    const SLayerBSInfo& layerInfo = info->sLayerInfo[layer];
    // Iterate NAL units making up this layer, noting fragments.
    size_t layer_len = 0;
    for (int nal = 0; nal < layerInfo.iNalCount; ++nal, ++frag) {
      // Because the sum of all layer lengths, `required_capacity`, fits in a
      // `size_t`, we know that any indices in-between will not overflow.
      RTC_DCHECK_GE(layerInfo.pNalLengthInByte[nal], 4);
      RTC_DCHECK_EQ(layerInfo.pBsBuf[layer_len + 0], start_code[0]);
      RTC_DCHECK_EQ(layerInfo.pBsBuf[layer_len + 1], start_code[1]);
      RTC_DCHECK_EQ(layerInfo.pBsBuf[layer_len + 2], start_code[2]);
      RTC_DCHECK_EQ(layerInfo.pBsBuf[layer_len + 3], start_code[3]);
      layer_len += layerInfo.pNalLengthInByte[nal];
    }
    // Copy the entire layer's data (including start codes).
    memcpy(buffer->data() + encoded_image->size(), layerInfo.pBsBuf, layer_len);
    encoded_image->set_size(encoded_image->size() + layer_len);
  }
}

H264EncoderImpl::H264EncoderImpl(const cricket::VideoCodec& codec)
    : packetization_mode_(H264PacketizationMode::SingleNalUnit),
      max_payload_size_(0),
      number_of_cores_(0),
      encoded_image_callback_(nullptr),
      has_reported_init_(false),
      has_reported_error_(false) {
  RTC_CHECK(absl::EqualsIgnoreCase(codec.name, cricket::kH264CodecName));
  std::string packetization_mode_string;
  if (codec.GetParam(cricket::kH264FmtpPacketizationMode,
                     &packetization_mode_string) &&
      packetization_mode_string == "1") {
    packetization_mode_ = H264PacketizationMode::NonInterleaved;
  }
  downscaled_buffers_.reserve(kMaxSimulcastStreams - 1);
  encoded_images_.reserve(kMaxSimulcastStreams);
  encoders_.reserve(kMaxSimulcastStreams);
  configurations_.reserve(kMaxSimulcastStreams);
  tl0sync_limit_.reserve(kMaxSimulcastStreams);
  svc_controllers_.reserve(kMaxSimulcastStreams);
}

H264EncoderImpl::~H264EncoderImpl() {
  Release();
}

int32_t H264EncoderImpl::InitEncode(const VideoCodec* inst,
                                    const VideoEncoder::Settings& settings) {
  ReportInit();
  if (!inst || inst->codecType != kVideoCodecH264) {
    ReportError();
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }
  if (inst->maxFramerate == 0) {
    ReportError();
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }
  if (inst->width < 1 || inst->height < 1) {
    ReportError();
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }

  int32_t release_ret = Release();
  if (release_ret != WEBRTC_VIDEO_CODEC_OK) {
    ReportError();
    return release_ret;
  }

  int number_of_streams = SimulcastUtility::NumberOfSimulcastStreams(*inst);
  bool doing_simulcast = (number_of_streams > 1);

  if (doing_simulcast &&
      !SimulcastUtility::ValidSimulcastParameters(*inst, number_of_streams)) {
    return WEBRTC_VIDEO_CODEC_ERR_SIMULCAST_PARAMETERS_NOT_SUPPORTED;
  }
  downscaled_buffers_.resize(number_of_streams - 1);
  encoded_images_.resize(number_of_streams);
  encoders_.resize(number_of_streams);
  pictures_.resize(number_of_streams);
  svc_controllers_.resize(number_of_streams);
  configurations_.resize(number_of_streams);
  tl0sync_limit_.resize(number_of_streams);

  number_of_cores_ = settings.number_of_cores;
  max_payload_size_ = settings.max_payload_size;
  codec_ = *inst;

  // Code expects simulcastStream resolutions to be correct, make sure they are
  // filled even when there are no simulcast layers.
  if (codec_.numberOfSimulcastStreams == 0) {
    codec_.simulcastStream[0].width = codec_.width;
    codec_.simulcastStream[0].height = codec_.height;
  }

  for (int i = 0, idx = number_of_streams - 1; i < number_of_streams;
       ++i, --idx) {
    ISVCEncoder* openh264_encoder;
    // Create encoder.
    if (WelsCreateSVCEncoder(&openh264_encoder) != 0) {
      // Failed to create encoder.
      RTC_LOG(LS_ERROR) << "Failed to create OpenH264 encoder";
      RTC_DCHECK(!openh264_encoder);
      Release();
      ReportError();
      return WEBRTC_VIDEO_CODEC_ERROR;
    }
    RTC_DCHECK(openh264_encoder);
    if (kOpenH264EncoderDetailedLogging) {
      int trace_level = WELS_LOG_DETAIL;
      openh264_encoder->SetOption(ENCODER_OPTION_TRACE_LEVEL, &trace_level);
    }
    // else WELS_LOG_DEFAULT is used by default.

    // Store h264 encoder.
    encoders_[i] = openh264_encoder;

    // Set internal settings from codec_settings
    configurations_[i].simulcast_idx = idx;
    configurations_[i].sending = false;
    configurations_[i].width = codec_.simulcastStream[idx].width;
    configurations_[i].height = codec_.simulcastStream[idx].height;
    configurations_[i].max_frame_rate = static_cast<float>(codec_.maxFramerate);
    configurations_[i].frame_dropping_on = codec_.GetFrameDropEnabled();
    configurations_[i].key_frame_interval = codec_.H264()->keyFrameInterval;
    configurations_[i].num_temporal_layers =
        std::max(codec_.H264()->numberOfTemporalLayers,
                 codec_.simulcastStream[idx].numberOfTemporalLayers);

    // Create downscaled image buffers.
    if (i > 0) {
      downscaled_buffers_[i - 1] = I420Buffer::Create(
          configurations_[i].width, configurations_[i].height,
          configurations_[i].width, configurations_[i].width / 2,
          configurations_[i].width / 2);
    }

    // Codec_settings uses kbits/second; encoder uses bits/second.
    configurations_[i].max_bps = codec_.maxBitrate * 1000;
    configurations_[i].target_bps = codec_.startBitrate * 1000;

    // Create encoder parameters based on the layer configuration.
    SEncParamExt encoder_params = CreateEncoderParams(i);

    // Initialize.
    if (openh264_encoder->InitializeExt(&encoder_params) != 0) {
      RTC_LOG(LS_ERROR) << "Failed to initialize OpenH264 encoder";
      Release();
      ReportError();
      return WEBRTC_VIDEO_CODEC_ERROR;
    }
    // TODO(pbos): Base init params on these values before submitting.
    int video_format = EVideoFormatType::videoFormatI420;
    openh264_encoder->SetOption(ENCODER_OPTION_DATAFORMAT, &video_format);

    // Initialize encoded image. Default buffer size: size of unencoded data.

    const size_t new_capacity =
        CalcBufferSize(VideoType::kI420, codec_.simulcastStream[idx].width,
                       codec_.simulcastStream[idx].height);
    encoded_images_[i].SetEncodedData(EncodedImageBuffer::Create(new_capacity));
    encoded_images_[i]._encodedWidth = codec_.simulcastStream[idx].width;
    encoded_images_[i]._encodedHeight = codec_.simulcastStream[idx].height;
    encoded_images_[i].set_size(0);

    tl0sync_limit_[i] = configurations_[i].num_temporal_layers;
    absl::optional<ScalabilityMode> scalability_mode;
    switch (configurations_[i].num_temporal_layers) {
      case 0:
        break;
      case 1:
        scalability_mode = ScalabilityMode::kL1T1;
        break;
      case 2:
        scalability_mode = ScalabilityMode::kL1T2;
        break;
      case 3:
        scalability_mode = ScalabilityMode::kL1T3;
        break;
      default:
        RTC_DCHECK_NOTREACHED();
    }
    if (scalability_mode.has_value()) {
      svc_controllers_[i] =
          CreateScalabilityStructure(scalability_mode.value());
      if (svc_controllers_[i] == nullptr) {
        RTC_LOG(LS_ERROR) << "Failed to create scalability structure";
        Release();
        ReportError();
        return WEBRTC_VIDEO_CODEC_ERROR;
      }
    }
  }

  SimulcastRateAllocator init_allocator(codec_);
  VideoBitrateAllocation allocation =
      init_allocator.Allocate(VideoBitrateAllocationParameters(
          DataRate::KilobitsPerSec(codec_.startBitrate), codec_.maxFramerate));
  SetRates(RateControlParameters(allocation, codec_.maxFramerate));
  return WEBRTC_VIDEO_CODEC_OK;
}

int32_t H264EncoderImpl::Release() {
  while (!encoders_.empty()) {
    ISVCEncoder* openh264_encoder = encoders_.back();
    if (openh264_encoder) {
      RTC_CHECK_EQ(0, openh264_encoder->Uninitialize());
      WelsDestroySVCEncoder(openh264_encoder);
    }
    encoders_.pop_back();
  }
  downscaled_buffers_.clear();
  configurations_.clear();
  encoded_images_.clear();
  pictures_.clear();
  tl0sync_limit_.clear();
  svc_controllers_.clear();
  return WEBRTC_VIDEO_CODEC_OK;
}

int32_t H264EncoderImpl::RegisterEncodeCompleteCallback(
    EncodedImageCallback* callback) {
  encoded_image_callback_ = callback;
  return WEBRTC_VIDEO_CODEC_OK;
}

void H264EncoderImpl::SetRates(const RateControlParameters& parameters) {
  if (encoders_.empty()) {
    RTC_LOG(LS_WARNING) << "SetRates() while uninitialized.";
    return;
  }

  if (parameters.framerate_fps < 1.0) {
    RTC_LOG(LS_WARNING) << "Invalid frame rate: " << parameters.framerate_fps;
    return;
  }

  if (parameters.bitrate.get_sum_bps() == 0) {
    // Encoder paused, turn off all encoding.
    for (size_t i = 0; i < configurations_.size(); ++i) {
      configurations_[i].SetStreamState(false);
    }
    return;
  }

  codec_.maxFramerate = static_cast<uint32_t>(parameters.framerate_fps);

  size_t stream_idx = encoders_.size() - 1;
  for (size_t i = 0; i < encoders_.size(); ++i, --stream_idx) {
    // Update layer config.
    configurations_[i].target_bps =
        parameters.bitrate.GetSpatialLayerSum(stream_idx);
    configurations_[i].max_frame_rate = parameters.framerate_fps;

    if (configurations_[i].target_bps) {
      configurations_[i].SetStreamState(true);

      // Update h264 encoder.
      SBitrateInfo target_bitrate;
      memset(&target_bitrate, 0, sizeof(SBitrateInfo));
      target_bitrate.iLayer = SPATIAL_LAYER_ALL,
      target_bitrate.iBitrate = configurations_[i].target_bps;
      encoders_[i]->SetOption(ENCODER_OPTION_BITRATE, &target_bitrate);
      encoders_[i]->SetOption(ENCODER_OPTION_FRAME_RATE,
                              &configurations_[i].max_frame_rate);
    } else {
      configurations_[i].SetStreamState(false);
    }
  }
}

int32_t H264EncoderImpl::Encode(
    const VideoFrame& input_frame,
    const std::vector<VideoFrameType>* frame_types) {
  if (encoders_.empty()) {
    ReportError();
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  }
  if (!encoded_image_callback_) {
    RTC_LOG(LS_WARNING)
        << "InitEncode() has been called, but a callback function "
           "has not been set with RegisterEncodeCompleteCallback()";
    ReportError();
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  }

  rtc::scoped_refptr<I420BufferInterface> frame_buffer =
      input_frame.video_frame_buffer()->ToI420();
  if (!frame_buffer) {
    RTC_LOG(LS_ERROR) << "Failed to convert "
                      << VideoFrameBufferTypeToString(
                             input_frame.video_frame_buffer()->type())
                      << " image to I420. Can't encode frame.";
    return WEBRTC_VIDEO_CODEC_ENCODER_FAILURE;
  }
  RTC_CHECK(frame_buffer->type() == VideoFrameBuffer::Type::kI420 ||
            frame_buffer->type() == VideoFrameBuffer::Type::kI420A);

  bool send_key_frame = false;
  for (size_t i = 0; i < configurations_.size(); ++i) {
    if (configurations_[i].key_frame_request && configurations_[i].sending) {
      send_key_frame = true;
      break;
    }
  }

  if (!send_key_frame && frame_types) {
    for (size_t i = 0; i < configurations_.size(); ++i) {
      const size_t simulcast_idx =
          static_cast<size_t>(configurations_[i].simulcast_idx);
      if (configurations_[i].sending && simulcast_idx < frame_types->size() &&
          (*frame_types)[simulcast_idx] == VideoFrameType::kVideoFrameKey) {
        send_key_frame = true;
        break;
      }
    }
  }

  RTC_DCHECK_EQ(configurations_[0].width, frame_buffer->width());
  RTC_DCHECK_EQ(configurations_[0].height, frame_buffer->height());

  // Encode image for each layer.
  for (size_t i = 0; i < encoders_.size(); ++i) {
    // EncodeFrame input.
    pictures_[i] = {0};
    pictures_[i].iPicWidth = configurations_[i].width;
    pictures_[i].iPicHeight = configurations_[i].height;
    pictures_[i].iColorFormat = EVideoFormatType::videoFormatI420;
    pictures_[i].uiTimeStamp = input_frame.ntp_time_ms();
    // Downscale images on second and ongoing layers.
    if (i == 0) {
      pictures_[i].iStride[0] = frame_buffer->StrideY();
      pictures_[i].iStride[1] = frame_buffer->StrideU();
      pictures_[i].iStride[2] = frame_buffer->StrideV();
      pictures_[i].pData[0] = const_cast<uint8_t*>(frame_buffer->DataY());
      pictures_[i].pData[1] = const_cast<uint8_t*>(frame_buffer->DataU());
      pictures_[i].pData[2] = const_cast<uint8_t*>(frame_buffer->DataV());
    } else {
      pictures_[i].iStride[0] = downscaled_buffers_[i - 1]->StrideY();
      pictures_[i].iStride[1] = downscaled_buffers_[i - 1]->StrideU();
      pictures_[i].iStride[2] = downscaled_buffers_[i - 1]->StrideV();
      pictures_[i].pData[0] =
          const_cast<uint8_t*>(downscaled_buffers_[i - 1]->DataY());
      pictures_[i].pData[1] =
          const_cast<uint8_t*>(downscaled_buffers_[i - 1]->DataU());
      pictures_[i].pData[2] =
          const_cast<uint8_t*>(downscaled_buffers_[i - 1]->DataV());
      // Scale the image down a number of times by downsampling factor.
      libyuv::I420Scale(pictures_[i - 1].pData[0], pictures_[i - 1].iStride[0],
                        pictures_[i - 1].pData[1], pictures_[i - 1].iStride[1],
                        pictures_[i - 1].pData[2], pictures_[i - 1].iStride[2],
                        configurations_[i - 1].width,
                        configurations_[i - 1].height, pictures_[i].pData[0],
                        pictures_[i].iStride[0], pictures_[i].pData[1],
                        pictures_[i].iStride[1], pictures_[i].pData[2],
                        pictures_[i].iStride[2], configurations_[i].width,
                        configurations_[i].height, libyuv::kFilterBox);
    }

    if (!configurations_[i].sending) {
      continue;
    }
    if (frame_types != nullptr) {
      // Skip frame?
      if ((*frame_types)[i] == VideoFrameType::kEmptyFrame) {
        continue;
      }
    }
    if (send_key_frame) {
      // API doc says ForceIntraFrame(false) does nothing, but calling this
      // function forces a key frame regardless of the `bIDR` argument's value.
      // (If every frame is a key frame we get lag/delays.)
      encoders_[i]->ForceIntraFrame(true);
      configurations_[i].key_frame_request = false;
    }
    // EncodeFrame output.
    SFrameBSInfo info;
    memset(&info, 0, sizeof(SFrameBSInfo));

    std::vector<ScalableVideoController::LayerFrameConfig> layer_frames;
    if (svc_controllers_[i]) {
      layer_frames = svc_controllers_[i]->NextFrameConfig(send_key_frame);
      RTC_CHECK_EQ(layer_frames.size(), 1);
    }

    // Encode!
    int enc_ret = encoders_[i]->EncodeFrame(&pictures_[i], &info);
    if (enc_ret != 0) {
      RTC_LOG(LS_ERROR)
          << "OpenH264 frame encoding failed, EncodeFrame returned " << enc_ret
          << ".";
      ReportError();
      return WEBRTC_VIDEO_CODEC_ERROR;
    }

    encoded_images_[i]._encodedWidth = configurations_[i].width;
    encoded_images_[i]._encodedHeight = configurations_[i].height;
    encoded_images_[i].SetTimestamp(input_frame.timestamp());
    encoded_images_[i].SetColorSpace(input_frame.color_space());
    encoded_images_[i]._frameType = ConvertToVideoFrameType(info.eFrameType);
    encoded_images_[i].SetSpatialIndex(configurations_[i].simulcast_idx);

    // Split encoded image up into fragments. This also updates
    // `encoded_image_`.
    RtpFragmentize(&encoded_images_[i], &info);

    // Encoder can skip frames to save bandwidth in which case
    // `encoded_images_[i]._length` == 0.
    if (encoded_images_[i].size() > 0) {
      // Parse QP.
      h264_bitstream_parser_.ParseBitstream(encoded_images_[i]);
      encoded_images_[i].qp_ =
          h264_bitstream_parser_.GetLastSliceQp().value_or(-1);

      // Deliver encoded image.
      CodecSpecificInfo codec_specific;
      codec_specific.codecType = kVideoCodecH264;
      codec_specific.codecSpecific.H264.packetization_mode =
          packetization_mode_;
      codec_specific.codecSpecific.H264.temporal_idx = kNoTemporalIdx;
      codec_specific.codecSpecific.H264.idr_frame =
          info.eFrameType == videoFrameTypeIDR;
      codec_specific.codecSpecific.H264.base_layer_sync = false;
      if (configurations_[i].num_temporal_layers > 1) {
        const uint8_t tid = info.sLayerInfo[0].uiTemporalId;
        codec_specific.codecSpecific.H264.temporal_idx = tid;
        codec_specific.codecSpecific.H264.base_layer_sync =
            tid > 0 && tid < tl0sync_limit_[i];
        if (svc_controllers_[i]) {
          if (layer_frames[0].TemporalId() != tid) {
            RTC_LOG(LS_WARNING)
                << "Encoder produced a frame for layer S" << (i + 1) << "T"
                << tid + 1 << " that wasn't requested.";
            continue;
          }
          encoded_images_[i].SetTemporalIndex(tid);
        }
        if (codec_specific.codecSpecific.H264.base_layer_sync) {
          tl0sync_limit_[i] = tid;
        }
        if (tid == 0) {
          tl0sync_limit_[i] = configurations_[i].num_temporal_layers;
        }
      }
      if (svc_controllers_[i]) {
        codec_specific.generic_frame_info =
            svc_controllers_[i]->OnEncodeDone(layer_frames[0]);
        if (send_key_frame && codec_specific.generic_frame_info.has_value()) {
          codec_specific.template_structure =
              svc_controllers_[i]->DependencyStructure();
        }
      }
      encoded_image_callback_->OnEncodedImage(encoded_images_[i],
                                              &codec_specific);
    }
  }
  return WEBRTC_VIDEO_CODEC_OK;
}

// Initialization parameters.
// There are two ways to initialize. There is SEncParamBase (cleared with
// memset(&p, 0, sizeof(SEncParamBase)) used in Initialize, and SEncParamExt
// which is a superset of SEncParamBase (cleared with GetDefaultParams) used
// in InitializeExt.
SEncParamExt H264EncoderImpl::CreateEncoderParams(size_t i) const {
  SEncParamExt encoder_params;
  encoders_[i]->GetDefaultParams(&encoder_params);
  if (codec_.mode == VideoCodecMode::kRealtimeVideo) {
    encoder_params.iUsageType = CAMERA_VIDEO_REAL_TIME;
  } else if (codec_.mode == VideoCodecMode::kScreensharing) {
    encoder_params.iUsageType = SCREEN_CONTENT_REAL_TIME;
  } else {
    RTC_DCHECK_NOTREACHED();
  }
  encoder_params.iPicWidth = configurations_[i].width;
  encoder_params.iPicHeight = configurations_[i].height;
  encoder_params.iTargetBitrate = configurations_[i].target_bps;
  // Keep unspecified. WebRTC's max codec bitrate is not the same setting
  // as OpenH264's iMaxBitrate. More details in https://crbug.com/webrtc/11543
  encoder_params.iMaxBitrate = UNSPECIFIED_BIT_RATE;
  // Rate Control mode
  encoder_params.iRCMode = RC_BITRATE_MODE;
  encoder_params.fMaxFrameRate = configurations_[i].max_frame_rate;

  // The following parameters are extension parameters (they're in SEncParamExt,
  // not in SEncParamBase).
  encoder_params.bEnableFrameSkip = configurations_[i].frame_dropping_on;
  // `uiIntraPeriod`    - multiple of GOP size
  // `keyFrameInterval` - number of frames
  encoder_params.uiIntraPeriod = configurations_[i].key_frame_interval;
  // Reuse SPS id if possible. This helps to avoid reset of chromium HW decoder
  // on each key-frame.
  // Note that WebRTC resets encoder on resolution change which makes all
  // EParameterSetStrategy modes except INCREASING_ID (default) essentially
  // equivalent to CONSTANT_ID.
  encoder_params.eSpsPpsIdStrategy = SPS_LISTING;
  encoder_params.uiMaxNalSize = 0;
  // Threading model: use auto.
  //  0: auto (dynamic imp. internal encoder)
  //  1: single thread (default value)
  // >1: number of threads
  encoder_params.iMultipleThreadIdc = NumberOfThreads(
      encoder_params.iPicWidth, encoder_params.iPicHeight, number_of_cores_);
  // The base spatial layer 0 is the only one we use.
  encoder_params.sSpatialLayers[0].iVideoWidth = encoder_params.iPicWidth;
  encoder_params.sSpatialLayers[0].iVideoHeight = encoder_params.iPicHeight;
  encoder_params.sSpatialLayers[0].fFrameRate = encoder_params.fMaxFrameRate;
  encoder_params.sSpatialLayers[0].iSpatialBitrate =
      encoder_params.iTargetBitrate;
  encoder_params.sSpatialLayers[0].iMaxSpatialBitrate =
      encoder_params.iMaxBitrate;
  encoder_params.iTemporalLayerNum = configurations_[i].num_temporal_layers;
  if (encoder_params.iTemporalLayerNum > 1) {
    // iNumRefFrame specifies total number of reference buffers to allocate.
    // For N temporal layers we need at least (N - 1) buffers to store last
    // encoded frames of all reference temporal layers.
    // Note that there is no API in OpenH264 encoder to specify exact set of
    // references to be used to prediction of a given frame. Encoder can
    // theoretically use all available reference buffers.
    encoder_params.iNumRefFrame = encoder_params.iTemporalLayerNum - 1;
  }
  RTC_LOG(LS_INFO) << "OpenH264 version is " << OPENH264_MAJOR << "."
                   << OPENH264_MINOR;
  switch (packetization_mode_) {
    case H264PacketizationMode::SingleNalUnit:
      // Limit the size of the packets produced.
      encoder_params.sSpatialLayers[0].sSliceArgument.uiSliceNum = 1;
      encoder_params.sSpatialLayers[0].sSliceArgument.uiSliceMode =
          SM_SIZELIMITED_SLICE;
      encoder_params.sSpatialLayers[0].sSliceArgument.uiSliceSizeConstraint =
          static_cast<unsigned int>(max_payload_size_);
      RTC_LOG(LS_INFO) << "Encoder is configured with NALU constraint: "
                       << max_payload_size_ << " bytes";
      break;
    case H264PacketizationMode::NonInterleaved:
      // When uiSliceMode = SM_FIXEDSLCNUM_SLICE, uiSliceNum = 0 means auto
      // design it with cpu core number.
      // TODO(sprang): Set to 0 when we understand why the rate controller borks
      //               when uiSliceNum > 1.
      encoder_params.sSpatialLayers[0].sSliceArgument.uiSliceNum = 1;
      encoder_params.sSpatialLayers[0].sSliceArgument.uiSliceMode =
          SM_FIXEDSLCNUM_SLICE;
      break;
  }
  return encoder_params;
}

void H264EncoderImpl::ReportInit() {
  if (has_reported_init_)
    return;
  RTC_HISTOGRAM_ENUMERATION("WebRTC.Video.H264EncoderImpl.Event",
                            kH264EncoderEventInit, kH264EncoderEventMax);
  has_reported_init_ = true;
}

void H264EncoderImpl::ReportError() {
  if (has_reported_error_)
    return;
  RTC_HISTOGRAM_ENUMERATION("WebRTC.Video.H264EncoderImpl.Event",
                            kH264EncoderEventError, kH264EncoderEventMax);
  has_reported_error_ = true;
}

VideoEncoder::EncoderInfo H264EncoderImpl::GetEncoderInfo() const {
  EncoderInfo info;
  info.supports_native_handle = false;
  info.implementation_name = "OpenH264";
  info.scaling_settings =
      VideoEncoder::ScalingSettings(kLowH264QpThreshold, kHighH264QpThreshold);
  info.is_hardware_accelerated = false;
  info.supports_simulcast = true;
  info.preferred_pixel_formats = {VideoFrameBuffer::Type::kI420};
  return info;
}

void H264EncoderImpl::LayerConfig::SetStreamState(bool send_stream) {
  if (send_stream && !sending) {
    // Need a key frame if we have not sent this stream before.
    key_frame_request = true;
  }
  sending = send_stream;
}

}  // namespace webrtc

#endif  // WEBRTC_USE_H264
