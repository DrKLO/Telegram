/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/rtp_seq_num_only_ref_finder.h"

#include <utility>

#include "rtc_base/logging.h"

namespace webrtc {

RtpFrameReferenceFinder::ReturnVector RtpSeqNumOnlyRefFinder::ManageFrame(
    std::unique_ptr<RtpFrameObject> frame) {
  FrameDecision decision = ManageFrameInternal(frame.get());

  RtpFrameReferenceFinder::ReturnVector res;
  switch (decision) {
    case kStash:
      if (stashed_frames_.size() > kMaxStashedFrames)
        stashed_frames_.pop_back();
      stashed_frames_.push_front(std::move(frame));
      return res;
    case kHandOff:
      res.push_back(std::move(frame));
      RetryStashedFrames(res);
      return res;
    case kDrop:
      return res;
  }

  return res;
}

RtpSeqNumOnlyRefFinder::FrameDecision
RtpSeqNumOnlyRefFinder::ManageFrameInternal(RtpFrameObject* frame) {
  if (frame->frame_type() == VideoFrameType::kVideoFrameKey) {
    last_seq_num_gop_.insert(std::make_pair(
        frame->last_seq_num(),
        std::make_pair(frame->last_seq_num(), frame->last_seq_num())));
  }

  // We have received a frame but not yet a keyframe, stash this frame.
  if (last_seq_num_gop_.empty())
    return kStash;

  // Clean up info for old keyframes but make sure to keep info
  // for the last keyframe.
  auto clean_to = last_seq_num_gop_.lower_bound(frame->last_seq_num() - 100);
  for (auto it = last_seq_num_gop_.begin();
       it != clean_to && last_seq_num_gop_.size() > 1;) {
    it = last_seq_num_gop_.erase(it);
  }

  // Find the last sequence number of the last frame for the keyframe
  // that this frame indirectly references.
  auto seq_num_it = last_seq_num_gop_.upper_bound(frame->last_seq_num());
  if (seq_num_it == last_seq_num_gop_.begin()) {
    RTC_LOG(LS_WARNING) << "Generic frame with packet range ["
                        << frame->first_seq_num() << ", "
                        << frame->last_seq_num()
                        << "] has no GoP, dropping frame.";
    return kDrop;
  }
  seq_num_it--;

  // Make sure the packet sequence numbers are continuous, otherwise stash
  // this frame.
  uint16_t last_picture_id_gop = seq_num_it->second.first;
  uint16_t last_picture_id_with_padding_gop = seq_num_it->second.second;
  if (frame->frame_type() == VideoFrameType::kVideoFrameDelta) {
    uint16_t prev_seq_num = frame->first_seq_num() - 1;

    if (prev_seq_num != last_picture_id_with_padding_gop)
      return kStash;
  }

  RTC_DCHECK(AheadOrAt(frame->last_seq_num(), seq_num_it->first));

  // Since keyframes can cause reordering we can't simply assign the
  // picture id according to some incrementing counter.
  frame->SetId(frame->last_seq_num());
  frame->num_references =
      frame->frame_type() == VideoFrameType::kVideoFrameDelta;
  frame->references[0] = rtp_seq_num_unwrapper_.Unwrap(last_picture_id_gop);
  if (AheadOf<uint16_t>(frame->Id(), last_picture_id_gop)) {
    seq_num_it->second.first = frame->Id();
    seq_num_it->second.second = frame->Id();
  }

  UpdateLastPictureIdWithPadding(frame->Id());
  frame->SetSpatialIndex(0);
  frame->SetId(rtp_seq_num_unwrapper_.Unwrap(frame->Id()));
  return kHandOff;
}

void RtpSeqNumOnlyRefFinder::RetryStashedFrames(
    RtpFrameReferenceFinder::ReturnVector& res) {
  bool complete_frame = false;
  do {
    complete_frame = false;
    for (auto frame_it = stashed_frames_.begin();
         frame_it != stashed_frames_.end();) {
      FrameDecision decision = ManageFrameInternal(frame_it->get());

      switch (decision) {
        case kStash:
          ++frame_it;
          break;
        case kHandOff:
          complete_frame = true;
          res.push_back(std::move(*frame_it));
          [[fallthrough]];
        case kDrop:
          frame_it = stashed_frames_.erase(frame_it);
      }
    }
  } while (complete_frame);
}

void RtpSeqNumOnlyRefFinder::UpdateLastPictureIdWithPadding(uint16_t seq_num) {
  auto gop_seq_num_it = last_seq_num_gop_.upper_bound(seq_num);

  // If this padding packet "belongs" to a group of pictures that we don't track
  // anymore, do nothing.
  if (gop_seq_num_it == last_seq_num_gop_.begin())
    return;
  --gop_seq_num_it;

  // Calculate the next contiuous sequence number and search for it in
  // the padding packets we have stashed.
  uint16_t next_seq_num_with_padding = gop_seq_num_it->second.second + 1;
  auto padding_seq_num_it =
      stashed_padding_.lower_bound(next_seq_num_with_padding);

  // While there still are padding packets and those padding packets are
  // continuous, then advance the "last-picture-id-with-padding" and remove
  // the stashed padding packet.
  while (padding_seq_num_it != stashed_padding_.end() &&
         *padding_seq_num_it == next_seq_num_with_padding) {
    gop_seq_num_it->second.second = next_seq_num_with_padding;
    ++next_seq_num_with_padding;
    padding_seq_num_it = stashed_padding_.erase(padding_seq_num_it);
  }

  // In the case where the stream has been continuous without any new keyframes
  // for a while there is a risk that new frames will appear to be older than
  // the keyframe they belong to due to wrapping sequence number. In order
  // to prevent this we advance the picture id of the keyframe every so often.
  if (ForwardDiff(gop_seq_num_it->first, seq_num) > 10000) {
    auto save = gop_seq_num_it->second;
    last_seq_num_gop_.clear();
    last_seq_num_gop_[seq_num] = save;
  }
}

RtpFrameReferenceFinder::ReturnVector RtpSeqNumOnlyRefFinder::PaddingReceived(
    uint16_t seq_num) {
  auto clean_padding_to =
      stashed_padding_.lower_bound(seq_num - kMaxPaddingAge);
  stashed_padding_.erase(stashed_padding_.begin(), clean_padding_to);
  stashed_padding_.insert(seq_num);
  UpdateLastPictureIdWithPadding(seq_num);
  RtpFrameReferenceFinder::ReturnVector res;
  RetryStashedFrames(res);
  return res;
}

void RtpSeqNumOnlyRefFinder::ClearTo(uint16_t seq_num) {
  auto it = stashed_frames_.begin();
  while (it != stashed_frames_.end()) {
    if (AheadOf<uint16_t>(seq_num, (*it)->first_seq_num())) {
      it = stashed_frames_.erase(it);
    } else {
      ++it;
    }
  }
}

}  // namespace webrtc
