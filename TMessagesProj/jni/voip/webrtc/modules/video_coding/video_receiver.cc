/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stddef.h>

#include <cstdint>
#include <vector>

#include "api/rtp_headers.h"
#include "api/sequence_checker.h"
#include "api/video_codecs/video_codec.h"
#include "api/video_codecs/video_decoder.h"
#include "modules/video_coding/decoder_database.h"
#include "modules/video_coding/deprecated/jitter_buffer.h"
#include "modules/video_coding/deprecated/packet.h"
#include "modules/video_coding/deprecated/receiver.h"
#include "modules/video_coding/encoded_frame.h"
#include "modules/video_coding/generic_decoder.h"
#include "modules/video_coding/include/video_coding.h"
#include "modules/video_coding/include/video_coding_defines.h"
#include "modules/video_coding/internal_defines.h"
#include "modules/video_coding/media_opt_util.h"
#include "modules/video_coding/timing/timing.h"
#include "modules/video_coding/video_coding_impl.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/one_time_event.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {
namespace vcm {

VideoReceiver::VideoReceiver(Clock* clock,
                             VCMTiming* timing,
                             const FieldTrialsView& field_trials)
    : clock_(clock),
      _timing(timing),
      _receiver(_timing, clock_, field_trials),
      _decodedFrameCallback(_timing, clock_, field_trials),
      _frameTypeCallback(nullptr),
      _packetRequestCallback(nullptr),
      _scheduleKeyRequest(false),
      drop_frames_until_keyframe_(false),
      max_nack_list_size_(0),
      _codecDataBase(),
      _retransmissionTimer(10, clock_),
      _keyRequestTimer(500, clock_) {
  decoder_thread_checker_.Detach();
  module_thread_checker_.Detach();
}

VideoReceiver::~VideoReceiver() {
  RTC_DCHECK_RUN_ON(&construction_thread_checker_);
}

void VideoReceiver::Process() {
  RTC_DCHECK_RUN_ON(&module_thread_checker_);

  // Key frame requests
  if (_keyRequestTimer.TimeUntilProcess() == 0) {
    _keyRequestTimer.Processed();
    bool request_key_frame = _frameTypeCallback != nullptr;
    if (request_key_frame) {
      MutexLock lock(&process_mutex_);
      request_key_frame = _scheduleKeyRequest;
    }
    if (request_key_frame)
      RequestKeyFrame();
  }

  // Packet retransmission requests
  // TODO(holmer): Add API for changing Process interval and make sure it's
  // disabled when NACK is off.
  if (_retransmissionTimer.TimeUntilProcess() == 0) {
    _retransmissionTimer.Processed();
    bool callback_registered = _packetRequestCallback != nullptr;
    uint16_t length = max_nack_list_size_;
    if (callback_registered && length > 0) {
      // Collect sequence numbers from the default receiver.
      bool request_key_frame = false;
      std::vector<uint16_t> nackList = _receiver.NackList(&request_key_frame);
      int32_t ret = VCM_OK;
      if (request_key_frame) {
        ret = RequestKeyFrame();
      }
      if (ret == VCM_OK && !nackList.empty()) {
        MutexLock lock(&process_mutex_);
        if (_packetRequestCallback != nullptr) {
          _packetRequestCallback->ResendPackets(&nackList[0], nackList.size());
        }
      }
    }
  }
}

// Register a receive callback. Will be called whenever there is a new frame
// ready for rendering.
int32_t VideoReceiver::RegisterReceiveCallback(
    VCMReceiveCallback* receiveCallback) {
  RTC_DCHECK_RUN_ON(&construction_thread_checker_);
  // This value is set before the decoder thread starts and unset after
  // the decoder thread has been stopped.
  _decodedFrameCallback.SetUserReceiveCallback(receiveCallback);
  return VCM_OK;
}

// Register an externally defined decoder object.
void VideoReceiver::RegisterExternalDecoder(VideoDecoder* externalDecoder,
                                            uint8_t payloadType) {
  RTC_DCHECK_RUN_ON(&construction_thread_checker_);
  if (externalDecoder == nullptr) {
    RTC_CHECK(_codecDataBase.DeregisterExternalDecoder(payloadType));
    return;
  }
  _codecDataBase.RegisterExternalDecoder(payloadType, externalDecoder);
}

// Register a frame type request callback.
int32_t VideoReceiver::RegisterFrameTypeCallback(
    VCMFrameTypeCallback* frameTypeCallback) {
  RTC_DCHECK_RUN_ON(&construction_thread_checker_);
  // This callback is used on the module thread, but since we don't get
  // callbacks on the module thread while the decoder thread isn't running
  // (and this function must not be called when the decoder is running),
  // we don't need a lock here.
  _frameTypeCallback = frameTypeCallback;
  return VCM_OK;
}

int32_t VideoReceiver::RegisterPacketRequestCallback(
    VCMPacketRequestCallback* callback) {
  RTC_DCHECK_RUN_ON(&construction_thread_checker_);
  // This callback is used on the module thread, but since we don't get
  // callbacks on the module thread while the decoder thread isn't running
  // (and this function must not be called when the decoder is running),
  // we don't need a lock here.
  _packetRequestCallback = callback;
  return VCM_OK;
}

// Decode next frame, blocking.
// Should be called as often as possible to get the most out of the decoder.
int32_t VideoReceiver::Decode(uint16_t maxWaitTimeMs) {
  RTC_DCHECK_RUN_ON(&decoder_thread_checker_);
  VCMEncodedFrame* frame = _receiver.FrameForDecoding(maxWaitTimeMs, true);

  if (!frame)
    return VCM_FRAME_NOT_READY;

  bool drop_frame = false;
  {
    MutexLock lock(&process_mutex_);
    if (drop_frames_until_keyframe_) {
      // Still getting delta frames, schedule another keyframe request as if
      // decode failed.
      if (frame->FrameType() != VideoFrameType::kVideoFrameKey) {
        drop_frame = true;
        _scheduleKeyRequest = true;
      } else {
        drop_frames_until_keyframe_ = false;
      }
    }
  }

  if (drop_frame) {
    _receiver.ReleaseFrame(frame);
    return VCM_FRAME_NOT_READY;
  }

  // If this frame was too late, we should adjust the delay accordingly
  if (frame->RenderTimeMs() > 0)
    _timing->UpdateCurrentDelay(Timestamp::Millis(frame->RenderTimeMs()),
                                clock_->CurrentTime());

  if (first_frame_received_()) {
    RTC_LOG(LS_INFO) << "Received first complete decodable video frame";
  }

  const int32_t ret = Decode(*frame);
  _receiver.ReleaseFrame(frame);
  return ret;
}

int32_t VideoReceiver::RequestKeyFrame() {
  RTC_DCHECK_RUN_ON(&module_thread_checker_);

  TRACE_EVENT0("webrtc", "RequestKeyFrame");
  if (_frameTypeCallback != nullptr) {
    const int32_t ret = _frameTypeCallback->RequestKeyFrame();
    if (ret < 0) {
      return ret;
    }
    MutexLock lock(&process_mutex_);
    _scheduleKeyRequest = false;
  } else {
    return VCM_MISSING_CALLBACK;
  }
  return VCM_OK;
}

// Must be called from inside the receive side critical section.
int32_t VideoReceiver::Decode(const VCMEncodedFrame& frame) {
  RTC_DCHECK_RUN_ON(&decoder_thread_checker_);
  TRACE_EVENT0("webrtc", "VideoReceiver::Decode");
  // Change decoder if payload type has changed
  VCMGenericDecoder* decoder =
      _codecDataBase.GetDecoder(frame, &_decodedFrameCallback);
  if (decoder == nullptr) {
    return VCM_NO_CODEC_REGISTERED;
  }
  return decoder->Decode(frame, clock_->CurrentTime());
}

// Register possible receive codecs, can be called multiple times
void VideoReceiver::RegisterReceiveCodec(
    uint8_t payload_type,
    const VideoDecoder::Settings& settings) {
  RTC_DCHECK_RUN_ON(&construction_thread_checker_);
  _codecDataBase.RegisterReceiveCodec(payload_type, settings);
}

// Incoming packet from network parsed and ready for decode, non blocking.
int32_t VideoReceiver::IncomingPacket(const uint8_t* incomingPayload,
                                      size_t payloadLength,
                                      const RTPHeader& rtp_header,
                                      const RTPVideoHeader& video_header) {
  RTC_DCHECK_RUN_ON(&module_thread_checker_);
  if (video_header.frame_type == VideoFrameType::kVideoFrameKey) {
    TRACE_EVENT1("webrtc", "VCM::PacketKeyFrame", "seqnum",
                 rtp_header.sequenceNumber);
  }
  if (incomingPayload == nullptr) {
    // The jitter buffer doesn't handle non-zero payload lengths for packets
    // without payload.
    // TODO(holmer): We should fix this in the jitter buffer.
    payloadLength = 0;
  }
  // Callers don't provide any ntp time.
  const VCMPacket packet(incomingPayload, payloadLength, rtp_header,
                         video_header, /*ntp_time_ms=*/0,
                         clock_->CurrentTime());
  int32_t ret = _receiver.InsertPacket(packet);

  // TODO(holmer): Investigate if this somehow should use the key frame
  // request scheduling to throttle the requests.
  if (ret == VCM_FLUSH_INDICATOR) {
    {
      MutexLock lock(&process_mutex_);
      drop_frames_until_keyframe_ = true;
    }
    RequestKeyFrame();
  } else if (ret < 0) {
    return ret;
  }
  return VCM_OK;
}

void VideoReceiver::SetNackSettings(size_t max_nack_list_size,
                                    int max_packet_age_to_nack,
                                    int max_incomplete_time_ms) {
  RTC_DCHECK_RUN_ON(&construction_thread_checker_);
  if (max_nack_list_size != 0) {
    max_nack_list_size_ = max_nack_list_size;
  }
  _receiver.SetNackSettings(max_nack_list_size, max_packet_age_to_nack,
                            max_incomplete_time_ms);
}

}  // namespace vcm
}  // namespace webrtc
