/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_VIDEO_RECEIVER2_H_
#define MODULES_VIDEO_CODING_VIDEO_RECEIVER2_H_

#include "api/sequence_checker.h"
#include "api/video_codecs/video_decoder.h"
#include "modules/video_coding/decoder_database.h"
#include "modules/video_coding/encoded_frame.h"
#include "modules/video_coding/generic_decoder.h"
#include "modules/video_coding/timing.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

// This class is a copy of vcm::VideoReceiver, trimmed down to what's used by
// VideoReceive stream, with the aim to incrementally trim it down further and
// ultimately delete it. It's difficult to do this incrementally with the
// original VideoReceiver class, since it is used by the legacy
// VideoCodingModule api.
class VideoReceiver2 {
 public:
  VideoReceiver2(Clock* clock, VCMTiming* timing);
  ~VideoReceiver2();

  void RegisterReceiveCodec(uint8_t payload_type,
                            const VideoDecoder::Settings& decoder_settings);

  void RegisterExternalDecoder(VideoDecoder* externalDecoder,
                               uint8_t payloadType);
  bool IsExternalDecoderRegistered(uint8_t payloadType) const;
  int32_t RegisterReceiveCallback(VCMReceiveCallback* receiveCallback);

  int32_t Decode(const webrtc::VCMEncodedFrame* frame);

  // Notification methods that are used to check our internal state and validate
  // threading assumptions. These are called by VideoReceiveStream.
  // See `IsDecoderThreadRunning()` for more details.
  void DecoderThreadStarting();
  void DecoderThreadStopped();

 private:
  // Used for DCHECKing thread correctness.
  // In build where DCHECKs are enabled, will return false before
  // DecoderThreadStarting is called, then true until DecoderThreadStopped
  // is called.
  // In builds where DCHECKs aren't enabled, it will return true.
  bool IsDecoderThreadRunning();

  SequenceChecker construction_sequence_checker_;
  SequenceChecker decoder_sequence_checker_;
  Clock* const clock_;
  VCMTiming* timing_;
  VCMDecodedFrameCallback decodedFrameCallback_;

  // Callbacks are set before the decoder thread starts.
  // Once the decoder thread has been started, usage of `_codecDataBase` moves
  // over to the decoder thread.
  VCMDecoderDataBase codecDataBase_;

#if RTC_DCHECK_IS_ON
  bool decoder_thread_is_running_ = false;
#endif
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_VIDEO_RECEIVER2_H_
