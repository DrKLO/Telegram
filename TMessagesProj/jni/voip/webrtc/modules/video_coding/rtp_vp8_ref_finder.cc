/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/rtp_vp8_ref_finder.h"

#include <utility>

#include "rtc_base/logging.h"

namespace webrtc {

RtpFrameReferenceFinder::ReturnVector RtpVp8RefFinder::ManageFrame(
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

RtpVp8RefFinder::FrameDecision RtpVp8RefFinder::ManageFrameInternal(
    RtpFrameObject* frame) {
  const RTPVideoHeader& video_header = frame->GetRtpVideoHeader();
  const RTPVideoHeaderVP8& codec_header =
      absl::get<RTPVideoHeaderVP8>(video_header.video_type_header);

  // Protect against corrupted packets with arbitrary large temporal idx.
  if (codec_header.temporalIdx >= kMaxTemporalLayers)
    return kDrop;

  frame->SetSpatialIndex(0);
  frame->SetId(codec_header.pictureId & 0x7FFF);

  if (last_picture_id_ == -1)
    last_picture_id_ = frame->Id();

  // Clean up info about not yet received frames that are too old.
  uint16_t old_picture_id =
      Subtract<kFrameIdLength>(frame->Id(), kMaxNotYetReceivedFrames);
  auto clean_frames_to = not_yet_received_frames_.lower_bound(old_picture_id);
  not_yet_received_frames_.erase(not_yet_received_frames_.begin(),
                                 clean_frames_to);
  // Avoid re-adding picture ids that were just erased.
  if (AheadOf<uint16_t, kFrameIdLength>(old_picture_id, last_picture_id_)) {
    last_picture_id_ = old_picture_id;
  }
  // Find if there has been a gap in fully received frames and save the picture
  // id of those frames in `not_yet_received_frames_`.
  if (AheadOf<uint16_t, kFrameIdLength>(frame->Id(), last_picture_id_)) {
    do {
      last_picture_id_ = Add<kFrameIdLength>(last_picture_id_, 1);
      not_yet_received_frames_.insert(last_picture_id_);
    } while (last_picture_id_ != frame->Id());
  }

  int64_t unwrapped_tl0 = tl0_unwrapper_.Unwrap(codec_header.tl0PicIdx & 0xFF);

  // Clean up info for base layers that are too old.
  int64_t old_tl0_pic_idx = unwrapped_tl0 - kMaxLayerInfo;
  auto clean_layer_info_to = layer_info_.lower_bound(old_tl0_pic_idx);
  layer_info_.erase(layer_info_.begin(), clean_layer_info_to);

  if (frame->frame_type() == VideoFrameType::kVideoFrameKey) {
    if (codec_header.temporalIdx != 0) {
      return kDrop;
    }
    frame->num_references = 0;
    layer_info_[unwrapped_tl0].fill(-1);
    UpdateLayerInfoVp8(frame, unwrapped_tl0, codec_header.temporalIdx);
    return kHandOff;
  }

  auto layer_info_it = layer_info_.find(
      codec_header.temporalIdx == 0 ? unwrapped_tl0 - 1 : unwrapped_tl0);

  // If we don't have the base layer frame yet, stash this frame.
  if (layer_info_it == layer_info_.end())
    return kStash;

  // A non keyframe base layer frame has been received, copy the layer info
  // from the previous base layer frame and set a reference to the previous
  // base layer frame.
  if (codec_header.temporalIdx == 0) {
    layer_info_it =
        layer_info_.emplace(unwrapped_tl0, layer_info_it->second).first;
    frame->num_references = 1;
    int64_t last_pid_on_layer = layer_info_it->second[0];

    // Is this an old frame that has already been used to update the state? If
    // so, drop it.
    if (AheadOrAt<uint16_t, kFrameIdLength>(last_pid_on_layer, frame->Id())) {
      return kDrop;
    }

    frame->references[0] = last_pid_on_layer;
    UpdateLayerInfoVp8(frame, unwrapped_tl0, codec_header.temporalIdx);
    return kHandOff;
  }

  // Layer sync frame, this frame only references its base layer frame.
  if (codec_header.layerSync) {
    frame->num_references = 1;
    int64_t last_pid_on_layer = layer_info_it->second[codec_header.temporalIdx];

    // Is this an old frame that has already been used to update the state? If
    // so, drop it.
    if (last_pid_on_layer != -1 &&
        AheadOrAt<uint16_t, kFrameIdLength>(last_pid_on_layer, frame->Id())) {
      return kDrop;
    }

    frame->references[0] = layer_info_it->second[0];
    UpdateLayerInfoVp8(frame, unwrapped_tl0, codec_header.temporalIdx);
    return kHandOff;
  }

  // Find all references for this frame.
  frame->num_references = 0;
  for (uint8_t layer = 0; layer <= codec_header.temporalIdx; ++layer) {
    // If we have not yet received a previous frame on this temporal layer,
    // stash this frame.
    if (layer_info_it->second[layer] == -1)
      return kStash;

    // If the last frame on this layer is ahead of this frame it means that
    // a layer sync frame has been received after this frame for the same
    // base layer frame, drop this frame.
    if (AheadOf<uint16_t, kFrameIdLength>(layer_info_it->second[layer],
                                          frame->Id())) {
      return kDrop;
    }

    // If we have not yet received a frame between this frame and the referenced
    // frame then we have to wait for that frame to be completed first.
    auto not_received_frame_it =
        not_yet_received_frames_.upper_bound(layer_info_it->second[layer]);
    if (not_received_frame_it != not_yet_received_frames_.end() &&
        AheadOf<uint16_t, kFrameIdLength>(frame->Id(),
                                          *not_received_frame_it)) {
      return kStash;
    }

    if (!(AheadOf<uint16_t, kFrameIdLength>(frame->Id(),
                                            layer_info_it->second[layer]))) {
      RTC_LOG(LS_WARNING) << "Frame with picture id " << frame->Id()
                          << " and packet range [" << frame->first_seq_num()
                          << ", " << frame->last_seq_num()
                          << "] already received, "
                             " dropping frame.";
      return kDrop;
    }

    ++frame->num_references;
    frame->references[layer] = layer_info_it->second[layer];
  }

  UpdateLayerInfoVp8(frame, unwrapped_tl0, codec_header.temporalIdx);
  return kHandOff;
}

void RtpVp8RefFinder::UpdateLayerInfoVp8(RtpFrameObject* frame,
                                         int64_t unwrapped_tl0,
                                         uint8_t temporal_idx) {
  auto layer_info_it = layer_info_.find(unwrapped_tl0);

  // Update this layer info and newer.
  while (layer_info_it != layer_info_.end()) {
    if (layer_info_it->second[temporal_idx] != -1 &&
        AheadOf<uint16_t, kFrameIdLength>(layer_info_it->second[temporal_idx],
                                          frame->Id())) {
      // The frame was not newer, then no subsequent layer info have to be
      // update.
      break;
    }

    layer_info_it->second[temporal_idx] = frame->Id();
    ++unwrapped_tl0;
    layer_info_it = layer_info_.find(unwrapped_tl0);
  }
  not_yet_received_frames_.erase(frame->Id());

  UnwrapPictureIds(frame);
}

void RtpVp8RefFinder::RetryStashedFrames(
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
          ABSL_FALLTHROUGH_INTENDED;
        case kDrop:
          frame_it = stashed_frames_.erase(frame_it);
      }
    }
  } while (complete_frame);
}

void RtpVp8RefFinder::UnwrapPictureIds(RtpFrameObject* frame) {
  for (size_t i = 0; i < frame->num_references; ++i)
    frame->references[i] = unwrapper_.Unwrap(frame->references[i]);
  frame->SetId(unwrapper_.Unwrap(frame->Id()));
}

void RtpVp8RefFinder::ClearTo(uint16_t seq_num) {
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
