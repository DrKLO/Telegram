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

#ifdef TGCALLS_ENABLE_X264

#include "h264_encoder_impl.h"

#include <limits>

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "common_video/libyuv/include/webrtc_libyuv.h"

namespace webrtc {

namespace {

int NumberOfThreads(int width, int height, int number_of_cores) {
    if (width * height >= 1920 * 1080 && number_of_cores > 8) {
        return 8;  // 8 threads for 1080p on high perf machines.
    } else if (width * height > 1280 * 960 && number_of_cores >= 6) {
        return 3;  // 3 threads for 1080p.
    } else if (width * height > 640 * 480 && number_of_cores >= 3) {
        return 2;  // 2 threads for qHD/HD.
    } else {
        return 1;  // 1 thread for VGA or less.
    }
}

}  // namespace

H264EncoderX264Impl::H264EncoderX264Impl(cricket::VideoCodec const &videoCodec) :
    packetization_mode_(H264PacketizationMode::SingleNalUnit),
    encoded_image_callback_(nullptr),
    inited_(false),
    encoder_(nullptr) {
}

H264EncoderX264Impl::~H264EncoderX264Impl() {
    Release();
}

int32_t H264EncoderX264Impl::InitEncode(const VideoCodec* inst,
                                        int32_t number_of_cores,
                                        size_t max_payload_size) {
    
    if (inst == NULL) {
        return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
    }
    if (inst->maxFramerate < 1) {
        return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
    }
    // allow zero to represent an unspecified maxBitRate
    if (inst->maxBitrate > 0 && inst->startBitrate > inst->maxBitrate) {
        return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
    }
    if (inst->width < 1 || inst->height < 1) {
        return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
    }
    if (number_of_cores < 1) {
        return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
    }
    
    int ret_val = Release();
    if (ret_val < 0) {
        return ret_val;
    }
    codec_settings_ = *inst;
    /* Get default params for preset/tuning */
    x264_param_t param;
    memset(&param, 0, sizeof(param));
    x264_param_default(&param);
    ret_val = x264_param_default_preset(&param, "fast", "zerolatency");
    if (ret_val != 0) {
        RTC_LOG(LS_ERROR) << "H264EncoderX264Impl::InitEncode() fails to initialize encoder ret_val " << ret_val;
        x264_encoder_close(encoder_);
        encoder_ = NULL;
        return WEBRTC_VIDEO_CODEC_ERROR;
    }
    
    param.i_log_level = X264_LOG_WARNING;
    param.i_threads = 1;
    param.i_width = inst->width;
    param.i_height = inst->height;
    param.i_frame_total = 0;
    
    param.b_sliced_threads = 0;
    //param.i_keyint_min = 5000;
    param.i_keyint_max = 60;
    
    /*param.i_bframe = 0;
    param.b_open_gop = 0;
    param.i_bframe_pyramid = 0;
    param.i_bframe_adaptive = X264_B_ADAPT_TRELLIS;
    param.i_nal_hrd = 0;
    
    param.i_fps_den = 1;
    param.i_fps_num = inst->maxFramerate;
    param.i_timebase_den = 1;
    param.i_timebase_num = inst->maxFramerate;
    param.b_intra_refresh = 0;
    param.i_frame_reference = 1;
    param.b_annexb = 1;
    param.i_csp = X264_CSP_I420;
    param.b_aud = 1;
    
    param.b_vfr_input = 0;
    param.b_repeat_headers = 1;*/
    
    param.rc.i_rc_method = X264_RC_ABR;
    param.rc.i_bitrate = codec_settings_.maxBitrate;
    param.rc.i_vbv_max_bitrate = param.rc.i_bitrate;
    param.rc.i_vbv_buffer_size = param.rc.i_bitrate * 2;
    param.rc.f_vbv_buffer_init = 1.0;
    //param.rc.i_qp_max = codec_settings_.qpMax;
    
    /*param.rc.i_rc_method = X264_RC_CRF;
    param.rc.f_rf_constant_max = 20;
    param.rc.f_rf_constant_max = 25;*/
    
    //param.rc.b_filler = 0;
    
    param.i_slice_max_size = (int)max_payload_size;
    param.i_sps_id = sps_id_;
    
    ret_val = x264_param_apply_profile(&param, "high");
    if (ret_val != 0) {
        RTC_LOG(LS_ERROR) << "H264EncoderX264Impl::InitEncode() fails to initialize encoder ret_val " << ret_val;
        x264_encoder_close(encoder_);
        encoder_ = NULL;
        return WEBRTC_VIDEO_CODEC_ERROR;
    }
    
    param.analyse.i_weighted_pred = X264_WEIGHTP_NONE;
    
    ret_val = x264_picture_alloc(&pic_, param.i_csp, param.i_width, param.i_height);
    if (ret_val != 0) {
        RTC_LOG(LS_ERROR) << "H264EncoderX264Impl::InitEncode() fails to initialize encoder ret_val " << ret_val;
        x264_encoder_close(encoder_);
        encoder_ = NULL;
        return WEBRTC_VIDEO_CODEC_ERROR;
    }
    
    encoder_ = x264_encoder_open(&param);
    if (!encoder_){
        RTC_LOG(LS_ERROR) << "H264EncoderX264Impl::InitEncode() fails to initialize encoder ret_val " << ret_val;
        x264_encoder_close(encoder_);
        x264_picture_clean(&pic_);
        encoder_ = NULL;
        return WEBRTC_VIDEO_CODEC_ERROR;
    }
    
    const size_t new_capacity = CalcBufferSize(VideoType::kI420, codec_settings_.width, codec_settings_.height);
    encoded_image_.SetEncodedData(EncodedImageBuffer::Create(new_capacity));
    encoded_image_._encodedWidth = codec_settings_.width;
    encoded_image_._encodedHeight = codec_settings_.height;
    encoded_image_.ClearEncodedData();
    
    inited_ = true;
    RTC_LOG(LS_INFO) << "H264EncoderX264Impl::InitEncode(width: " << inst->width << ", height: " << inst->height << ", framerate: " << inst->maxFramerate << ", start_bitrate: " << inst->startBitrate << ", max_bitrate: " << inst->maxBitrate << ")";
    
    sps_id_++;
    
    return WEBRTC_VIDEO_CODEC_OK;
}


int32_t H264EncoderX264Impl::Release() {
    if (encoder_) {
        encoder_ = nullptr;
    }
    encoded_image_.ClearEncodedData();
    return WEBRTC_VIDEO_CODEC_OK;
}

int32_t H264EncoderX264Impl::RegisterEncodeCompleteCallback(EncodedImageCallback* callback) {
    encoded_image_callback_ = callback;
    return WEBRTC_VIDEO_CODEC_OK;
}

void H264EncoderX264Impl::SetRates(const RateControlParameters& parameters) {
    codec_settings_.maxBitrate = parameters.bitrate.GetSpatialLayerSum(0);
    codec_settings_.maxFramerate = (uint32_t)parameters.framerate_fps;
}

int32_t H264EncoderX264Impl::Encode(const VideoFrame& frame, const std::vector<VideoFrameType>* frame_types) {
    if (!inited_) {
        return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
    }
    if (frame.size() == 0) {
        return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
    }
    if (encoded_image_callback_ == NULL) {
        return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
    }
    
    VideoFrameType frame_type = VideoFrameType::kVideoFrameDelta;
    if (frame_types && frame_types->size() > 0) {
        frame_type = (*frame_types)[0];
    }
    
    pic_.img.i_csp = X264_CSP_I420;
    pic_.img.i_plane = 3;
    switch (frame_type) {
        case VideoFrameType::kEmptyFrame: {
            pic_.i_type = X264_TYPE_AUTO;
            break;
        }
        case VideoFrameType::kVideoFrameDelta: {
            pic_.i_type = X264_TYPE_AUTO;
            break;
        }
        case VideoFrameType::kVideoFrameKey: {
            pic_.i_type = X264_TYPE_AUTO;
            x264_encoder_intra_refresh(encoder_);
            break;
        }
        default: {
            pic_.i_type = X264_TYPE_AUTO;
            break;
        }
    }
    
    webrtc::scoped_refptr<I420BufferInterface> frame_buffer = frame.video_frame_buffer()->ToI420();
    
    pic_.img.plane[0] = (uint8_t *)frame_buffer->DataY();
    pic_.img.i_stride[0] = frame_buffer->StrideY();
    pic_.img.plane[1] = (uint8_t *)frame_buffer->DataU();
    pic_.img.i_stride[1] = frame_buffer->StrideU();
    pic_.img.plane[2] = (uint8_t *)frame_buffer->DataV();
    pic_.img.i_stride[2] = frame_buffer->StrideV();
    
    pic_.i_pts++;
    
    int n_nal = 0;
    int i_frame_size = x264_encoder_encode(encoder_, &nal_t_, &n_nal, &pic_, &pic_out_);
    if (i_frame_size < 0) {
        RTC_LOG(LS_ERROR) << "H264EncoderX264Impl::Encode() fails to encode " << i_frame_size;
        x264_encoder_close(encoder_);
        x264_picture_clean(&pic_);
        encoder_ = NULL;
        return WEBRTC_VIDEO_CODEC_ERROR;
    }
    
    if (i_frame_size) {
        if (n_nal == 0) {
            return WEBRTC_VIDEO_CODEC_OK;
        }
        
        // Encoder can skip frames to save bandwidth in which case
        // `encoded_images_[i]._length` == 0.
        /*if (encoded_images_[i].size() > 0) {
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
         if (codec_specific.codecSpecific.H264.base_layer_sync) {
         tl0sync_limit_[i] = tid;
         }
         if (tid == 0) {
         tl0sync_limit_[i] = configurations_[i].num_temporal_layers;
         }
         }
         encoded_image_callback_->OnEncodedImage(encoded_images_[i],
         &codec_specific);
         }
         }*/
        
        size_t required_capacity = 0;
        size_t fragments_count = 0;
        for (int layer = 0; layer < 1; ++layer) {
            for (int nal = 0; nal < n_nal; ++nal, ++fragments_count) {
                int currentNaluSize = nal_t_[nal].i_payload;
                RTC_CHECK_GE(currentNaluSize, 0);
                // Ensure `required_capacity` will not overflow.
                RTC_CHECK_LE(currentNaluSize,
                             std::numeric_limits<size_t>::max() - required_capacity);
                required_capacity += currentNaluSize;
            }
        }
        // TODO(nisse): Use a cache or buffer pool to avoid allocation?
        auto buffer = EncodedImageBuffer::Create(required_capacity);
        encoded_image_.SetEncodedData(buffer);
        
        int currentBufferLength = 0;
        uint32_t totalNaluIndex = 0;
        for (int nal_index = 0; nal_index < n_nal; nal_index++) {
            uint32_t currentNaluSize = 0;
            currentNaluSize = nal_t_[nal_index].i_payload;
            memcpy(buffer->data() + currentBufferLength, nal_t_[nal_index].p_payload, currentNaluSize);
            currentBufferLength += currentNaluSize;
            
            totalNaluIndex++;
        }
    }
    i_frame++;
    if (encoded_image_.size() > 0) {
        encoded_image_._encodedWidth = codec_settings_.width;
        encoded_image_._encodedHeight = codec_settings_.height;
        encoded_image_.SetTimestamp(frame.timestamp());
        encoded_image_._frameType = frame_type;
        encoded_image_.SetSpatialIndex(0);
        
        CodecSpecificInfo codec_specific;
        codec_specific.codecType = kVideoCodecH264;
        codec_specific.codecSpecific.H264.packetization_mode = packetization_mode_;
        codec_specific.codecSpecific.H264.temporal_idx = kNoTemporalIdx;
        
        if (pic_out_.i_type == X264_TYPE_IDR) {
            codec_specific.codecSpecific.H264.idr_frame = true;
        } else {
            codec_specific.codecSpecific.H264.idr_frame = false;
        }
        codec_specific.codecSpecific.H264.base_layer_sync = false;
        encoded_image_callback_->OnEncodedImage(encoded_image_, &codec_specific);
    }
    return WEBRTC_VIDEO_CODEC_OK;
}

bool H264EncoderX264Impl::IsInitialized() const {
    return encoder_ != nullptr;
}

}  // namespace webrtc

#endif
