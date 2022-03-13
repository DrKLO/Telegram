/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/rtp_vp9_ref_finder.h"

#include <algorithm>
#include <utility>

#include "rtc_base/logging.h"

namespace webrtc {

RtpFrameReferenceFinder::ReturnVector RtpVp9RefFinder::ManageFrame(
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

RtpVp9RefFinder::FrameDecision RtpVp9RefFinder::ManageFrameInternal(
    RtpFrameObject* frame) {
  const RTPVideoHeader& video_header = frame->GetRtpVideoHeader();
  const RTPVideoHeaderVP9& codec_header =
      absl::get<RTPVideoHeaderVP9>(video_header.video_type_header);

  // Protect against corrupted packets with arbitrary large temporal idx.
  if (codec_header.temporal_idx >= kMaxTemporalLayers ||
      codec_header.spatial_idx >= kMaxSpatialLayers)
    return kDrop;

  frame->SetSpatialIndex(codec_header.spatial_idx);
  frame->SetId(codec_header.picture_id & (kFrameIdLength - 1));

  if (last_picture_id_ == -1)
    last_picture_id_ = frame->Id();

  if (codec_header.flexible_mode) {
    if (codec_header.num_ref_pics > EncodedFrame::kMaxFrameReferences) {
      return kDrop;
    }
    frame->num_references = codec_header.num_ref_pics;
    for (size_t i = 0; i < frame->num_references; ++i) {
      frame->references[i] =
          Subtract<kFrameIdLength>(frame->Id(), codec_header.pid_diff[i]);
    }

    FlattenFrameIdAndRefs(frame, codec_header.inter_layer_predicted);
    return kHandOff;
  }

  if (codec_header.tl0_pic_idx == kNoTl0PicIdx) {
    RTC_LOG(LS_WARNING) << "TL0PICIDX is expected to be present in "
                           "non-flexible mode.";
    return kDrop;
  }

  GofInfo* info;
  int64_t unwrapped_tl0 =
      tl0_unwrapper_.Unwrap(codec_header.tl0_pic_idx & 0xFF);
  if (codec_header.ss_data_available) {
    if (codec_header.temporal_idx != 0) {
      RTC_LOG(LS_WARNING) << "Received scalability structure on a non base "
                             "layer frame. Scalability structure ignored.";
    } else {
      if (codec_header.gof.num_frames_in_gof > kMaxVp9FramesInGof) {
        return kDrop;
      }

      for (size_t i = 0; i < codec_header.gof.num_frames_in_gof; ++i) {
        if (codec_header.gof.num_ref_pics[i] > kMaxVp9RefPics) {
          return kDrop;
        }
      }

      GofInfoVP9 gof = codec_header.gof;
      if (gof.num_frames_in_gof == 0) {
        RTC_LOG(LS_WARNING) << "Number of frames in GOF is zero. Assume "
                               "that stream has only one temporal layer.";
        gof.SetGofInfoVP9(kTemporalStructureMode1);
      }

      current_ss_idx_ = Add<kMaxGofSaved>(current_ss_idx_, 1);
      scalability_structures_[current_ss_idx_] = gof;
      scalability_structures_[current_ss_idx_].pid_start = frame->Id();
      gof_info_.emplace(
          unwrapped_tl0,
          GofInfo(&scalability_structures_[current_ss_idx_], frame->Id()));
    }

    const auto gof_info_it = gof_info_.find(unwrapped_tl0);
    if (gof_info_it == gof_info_.end())
      return kStash;

    info = &gof_info_it->second;

    if (frame->frame_type() == VideoFrameType::kVideoFrameKey) {
      frame->num_references = 0;
      FrameReceivedVp9(frame->Id(), info);
      FlattenFrameIdAndRefs(frame, codec_header.inter_layer_predicted);
      return kHandOff;
    }
  } else if (frame->frame_type() == VideoFrameType::kVideoFrameKey) {
    if (frame->SpatialIndex() == 0) {
      RTC_LOG(LS_WARNING) << "Received keyframe without scalability structure";
      return kDrop;
    }
    const auto gof_info_it = gof_info_.find(unwrapped_tl0);
    if (gof_info_it == gof_info_.end())
      return kStash;

    info = &gof_info_it->second;

    frame->num_references = 0;
    FrameReceivedVp9(frame->Id(), info);
    FlattenFrameIdAndRefs(frame, codec_header.inter_layer_predicted);
    return kHandOff;
  } else {
    auto gof_info_it = gof_info_.find(
        (codec_header.temporal_idx == 0) ? unwrapped_tl0 - 1 : unwrapped_tl0);

    // Gof info for this frame is not available yet, stash this frame.
    if (gof_info_it == gof_info_.end())
      return kStash;

    if (codec_header.temporal_idx == 0) {
      gof_info_it = gof_info_
                        .emplace(unwrapped_tl0,
                                 GofInfo(gof_info_it->second.gof, frame->Id()))
                        .first;
    }

    info = &gof_info_it->second;
  }

  // Clean up info for base layers that are too old.
  int64_t old_tl0_pic_idx = unwrapped_tl0 - kMaxGofSaved;
  auto clean_gof_info_to = gof_info_.lower_bound(old_tl0_pic_idx);
  gof_info_.erase(gof_info_.begin(), clean_gof_info_to);

  FrameReceivedVp9(frame->Id(), info);

  // Make sure we don't miss any frame that could potentially have the
  // up switch flag set.
  if (MissingRequiredFrameVp9(frame->Id(), *info))
    return kStash;

  if (codec_header.temporal_up_switch)
    up_switch_.emplace(frame->Id(), codec_header.temporal_idx);

  // Clean out old info about up switch frames.
  uint16_t old_picture_id = Subtract<kFrameIdLength>(frame->Id(), 50);
  auto up_switch_erase_to = up_switch_.lower_bound(old_picture_id);
  up_switch_.erase(up_switch_.begin(), up_switch_erase_to);

  size_t diff =
      ForwardDiff<uint16_t, kFrameIdLength>(info->gof->pid_start, frame->Id());
  size_t gof_idx = diff % info->gof->num_frames_in_gof;

  if (info->gof->num_ref_pics[gof_idx] > EncodedFrame::kMaxFrameReferences) {
    return kDrop;
  }
  // Populate references according to the scalability structure.
  frame->num_references = info->gof->num_ref_pics[gof_idx];
  for (size_t i = 0; i < frame->num_references; ++i) {
    frame->references[i] =
        Subtract<kFrameIdLength>(frame->Id(), info->gof->pid_diff[gof_idx][i]);

    // If this is a reference to a frame earlier than the last up switch point,
    // then ignore this reference.
    if (UpSwitchInIntervalVp9(frame->Id(), codec_header.temporal_idx,
                              frame->references[i])) {
      --frame->num_references;
    }
  }

  // Override GOF references.
  if (!codec_header.inter_pic_predicted) {
    frame->num_references = 0;
  }

  FlattenFrameIdAndRefs(frame, codec_header.inter_layer_predicted);
  return kHandOff;
}

bool RtpVp9RefFinder::MissingRequiredFrameVp9(uint16_t picture_id,
                                              const GofInfo& info) {
  size_t diff =
      ForwardDiff<uint16_t, kFrameIdLength>(info.gof->pid_start, picture_id);
  size_t gof_idx = diff % info.gof->num_frames_in_gof;
  size_t temporal_idx = info.gof->temporal_idx[gof_idx];

  if (temporal_idx >= kMaxTemporalLayers) {
    RTC_LOG(LS_WARNING) << "At most " << kMaxTemporalLayers
                        << " temporal "
                           "layers are supported.";
    return true;
  }

  // For every reference this frame has, check if there is a frame missing in
  // the interval (`ref_pid`, `picture_id`) in any of the lower temporal
  // layers. If so, we are missing a required frame.
  uint8_t num_references = info.gof->num_ref_pics[gof_idx];
  for (size_t i = 0; i < num_references; ++i) {
    uint16_t ref_pid =
        Subtract<kFrameIdLength>(picture_id, info.gof->pid_diff[gof_idx][i]);
    for (size_t l = 0; l < temporal_idx; ++l) {
      auto missing_frame_it = missing_frames_for_layer_[l].lower_bound(ref_pid);
      if (missing_frame_it != missing_frames_for_layer_[l].end() &&
          AheadOf<uint16_t, kFrameIdLength>(picture_id, *missing_frame_it)) {
        return true;
      }
    }
  }
  return false;
}

void RtpVp9RefFinder::FrameReceivedVp9(uint16_t picture_id, GofInfo* info) {
  int last_picture_id = info->last_picture_id;
  size_t gof_size = std::min(info->gof->num_frames_in_gof, kMaxVp9FramesInGof);

  // If there is a gap, find which temporal layer the missing frames
  // belong to and add the frame as missing for that temporal layer.
  // Otherwise, remove this frame from the set of missing frames.
  if (AheadOf<uint16_t, kFrameIdLength>(picture_id, last_picture_id)) {
    size_t diff = ForwardDiff<uint16_t, kFrameIdLength>(info->gof->pid_start,
                                                        last_picture_id);
    size_t gof_idx = diff % gof_size;

    last_picture_id = Add<kFrameIdLength>(last_picture_id, 1);
    while (last_picture_id != picture_id) {
      gof_idx = (gof_idx + 1) % gof_size;
      RTC_CHECK(gof_idx < kMaxVp9FramesInGof);

      size_t temporal_idx = info->gof->temporal_idx[gof_idx];
      if (temporal_idx >= kMaxTemporalLayers) {
        RTC_LOG(LS_WARNING) << "At most " << kMaxTemporalLayers
                            << " temporal "
                               "layers are supported.";
        return;
      }

      missing_frames_for_layer_[temporal_idx].insert(last_picture_id);
      last_picture_id = Add<kFrameIdLength>(last_picture_id, 1);
    }

    info->last_picture_id = last_picture_id;
  } else {
    size_t diff =
        ForwardDiff<uint16_t, kFrameIdLength>(info->gof->pid_start, picture_id);
    size_t gof_idx = diff % gof_size;
    RTC_CHECK(gof_idx < kMaxVp9FramesInGof);

    size_t temporal_idx = info->gof->temporal_idx[gof_idx];
    if (temporal_idx >= kMaxTemporalLayers) {
      RTC_LOG(LS_WARNING) << "At most " << kMaxTemporalLayers
                          << " temporal "
                             "layers are supported.";
      return;
    }

    missing_frames_for_layer_[temporal_idx].erase(picture_id);
  }
}

bool RtpVp9RefFinder::UpSwitchInIntervalVp9(uint16_t picture_id,
                                            uint8_t temporal_idx,
                                            uint16_t pid_ref) {
  for (auto up_switch_it = up_switch_.upper_bound(pid_ref);
       up_switch_it != up_switch_.end() &&
       AheadOf<uint16_t, kFrameIdLength>(picture_id, up_switch_it->first);
       ++up_switch_it) {
    if (up_switch_it->second < temporal_idx)
      return true;
  }

  return false;
}

void RtpVp9RefFinder::RetryStashedFrames(
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

void RtpVp9RefFinder::FlattenFrameIdAndRefs(RtpFrameObject* frame,
                                            bool inter_layer_predicted) {
  for (size_t i = 0; i < frame->num_references; ++i) {
    frame->references[i] =
        unwrapper_.Unwrap(frame->references[i]) * kMaxSpatialLayers +
        *frame->SpatialIndex();
  }
  frame->SetId(unwrapper_.Unwrap(frame->Id()) * kMaxSpatialLayers +
               *frame->SpatialIndex());

  if (inter_layer_predicted &&
      frame->num_references + 1 <= EncodedFrame::kMaxFrameReferences) {
    frame->references[frame->num_references] = frame->Id() - 1;
    ++frame->num_references;
  }
}

void RtpVp9RefFinder::ClearTo(uint16_t seq_num) {
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
