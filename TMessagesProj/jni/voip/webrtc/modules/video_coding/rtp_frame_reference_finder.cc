/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/rtp_frame_reference_finder.h"

#include <algorithm>
#include <limits>

#include "absl/base/macros.h"
#include "absl/types/variant.h"
#include "modules/video_coding/frame_object.h"
#include "modules/video_coding/packet_buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace video_coding {

RtpFrameReferenceFinder::RtpFrameReferenceFinder(
    OnCompleteFrameCallback* frame_callback)
    : RtpFrameReferenceFinder(frame_callback, 0) {}

RtpFrameReferenceFinder::RtpFrameReferenceFinder(
    OnCompleteFrameCallback* frame_callback,
    int64_t picture_id_offset)
    : last_picture_id_(-1),
      current_ss_idx_(0),
      cleared_to_seq_num_(-1),
      frame_callback_(frame_callback),
      picture_id_offset_(picture_id_offset) {}

RtpFrameReferenceFinder::~RtpFrameReferenceFinder() = default;

void RtpFrameReferenceFinder::ManageFrame(
    std::unique_ptr<RtpFrameObject> frame) {
  // If we have cleared past this frame, drop it.
  if (cleared_to_seq_num_ != -1 &&
      AheadOf<uint16_t>(cleared_to_seq_num_, frame->first_seq_num())) {
    return;
  }

  FrameDecision decision = ManageFrameInternal(frame.get());

  switch (decision) {
    case kStash:
      if (stashed_frames_.size() > kMaxStashedFrames)
        stashed_frames_.pop_back();
      stashed_frames_.push_front(std::move(frame));
      break;
    case kHandOff:
      HandOffFrame(std::move(frame));
      RetryStashedFrames();
      break;
    case kDrop:
      break;
  }
}

void RtpFrameReferenceFinder::RetryStashedFrames() {
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
          HandOffFrame(std::move(*frame_it));
          ABSL_FALLTHROUGH_INTENDED;
        case kDrop:
          frame_it = stashed_frames_.erase(frame_it);
      }
    }
  } while (complete_frame);
}

void RtpFrameReferenceFinder::HandOffFrame(
    std::unique_ptr<RtpFrameObject> frame) {
  frame->id.picture_id += picture_id_offset_;
  for (size_t i = 0; i < frame->num_references; ++i) {
    frame->references[i] += picture_id_offset_;
  }

  frame_callback_->OnCompleteFrame(std::move(frame));
}

RtpFrameReferenceFinder::FrameDecision
RtpFrameReferenceFinder::ManageFrameInternal(RtpFrameObject* frame) {
  if (const absl::optional<RTPVideoHeader::GenericDescriptorInfo>&
          generic_descriptor = frame->GetRtpVideoHeader().generic) {
    return ManageFrameGeneric(frame, *generic_descriptor);
  }

  switch (frame->codec_type()) {
    case kVideoCodecVP8:
      return ManageFrameVp8(frame);
    case kVideoCodecVP9:
      return ManageFrameVp9(frame);
    case kVideoCodecGeneric:
      if (auto* generic_header = absl::get_if<RTPVideoHeaderLegacyGeneric>(
              &frame->GetRtpVideoHeader().video_type_header)) {
        return ManageFramePidOrSeqNum(frame, generic_header->picture_id);
      }
      ABSL_FALLTHROUGH_INTENDED;
    default:
      return ManageFramePidOrSeqNum(frame, kNoPictureId);
  }
}

void RtpFrameReferenceFinder::PaddingReceived(uint16_t seq_num) {
  auto clean_padding_to =
      stashed_padding_.lower_bound(seq_num - kMaxPaddingAge);
  stashed_padding_.erase(stashed_padding_.begin(), clean_padding_to);
  stashed_padding_.insert(seq_num);
  UpdateLastPictureIdWithPadding(seq_num);
  RetryStashedFrames();
}

void RtpFrameReferenceFinder::ClearTo(uint16_t seq_num) {
  cleared_to_seq_num_ = seq_num;

  auto it = stashed_frames_.begin();
  while (it != stashed_frames_.end()) {
    if (AheadOf<uint16_t>(cleared_to_seq_num_, (*it)->first_seq_num())) {
      it = stashed_frames_.erase(it);
    } else {
      ++it;
    }
  }
}

void RtpFrameReferenceFinder::UpdateLastPictureIdWithPadding(uint16_t seq_num) {
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

RtpFrameReferenceFinder::FrameDecision
RtpFrameReferenceFinder::ManageFrameGeneric(
    RtpFrameObject* frame,
    const RTPVideoHeader::GenericDescriptorInfo& descriptor) {
  frame->id.picture_id = descriptor.frame_id;
  frame->id.spatial_layer = descriptor.spatial_index;

  if (EncodedFrame::kMaxFrameReferences < descriptor.dependencies.size()) {
    RTC_LOG(LS_WARNING) << "Too many dependencies in generic descriptor.";
    return kDrop;
  }

  frame->num_references = descriptor.dependencies.size();
  for (size_t i = 0; i < descriptor.dependencies.size(); ++i)
    frame->references[i] = descriptor.dependencies[i];

  return kHandOff;
}

RtpFrameReferenceFinder::FrameDecision
RtpFrameReferenceFinder::ManageFramePidOrSeqNum(RtpFrameObject* frame,
                                                int picture_id) {
  // If |picture_id| is specified then we use that to set the frame references,
  // otherwise we use sequence number.
  if (picture_id != kNoPictureId) {
    frame->id.picture_id = unwrapper_.Unwrap(picture_id & 0x7FFF);
    frame->num_references =
        frame->frame_type() == VideoFrameType::kVideoFrameKey ? 0 : 1;
    frame->references[0] = frame->id.picture_id - 1;
    return kHandOff;
  }

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
  frame->id.picture_id = frame->last_seq_num();
  frame->num_references =
      frame->frame_type() == VideoFrameType::kVideoFrameDelta;
  frame->references[0] = rtp_seq_num_unwrapper_.Unwrap(last_picture_id_gop);
  if (AheadOf<uint16_t>(frame->id.picture_id, last_picture_id_gop)) {
    seq_num_it->second.first = frame->id.picture_id;
    seq_num_it->second.second = frame->id.picture_id;
  }

  UpdateLastPictureIdWithPadding(frame->id.picture_id);
  frame->id.picture_id = rtp_seq_num_unwrapper_.Unwrap(frame->id.picture_id);
  return kHandOff;
}

RtpFrameReferenceFinder::FrameDecision RtpFrameReferenceFinder::ManageFrameVp8(
    RtpFrameObject* frame) {
  const RTPVideoHeader& video_header = frame->GetRtpVideoHeader();
  const RTPVideoHeaderVP8& codec_header =
      absl::get<RTPVideoHeaderVP8>(video_header.video_type_header);

  if (codec_header.pictureId == kNoPictureId ||
      codec_header.temporalIdx == kNoTemporalIdx ||
      codec_header.tl0PicIdx == kNoTl0PicIdx) {
    return ManageFramePidOrSeqNum(frame, codec_header.pictureId);
  }

  // Protect against corrupted packets with arbitrary large temporal idx.
  if (codec_header.temporalIdx >= kMaxTemporalLayers)
    return kDrop;

  frame->id.picture_id = codec_header.pictureId & 0x7FFF;

  if (last_picture_id_ == -1)
    last_picture_id_ = frame->id.picture_id;

  // Clean up info about not yet received frames that are too old.
  uint16_t old_picture_id =
      Subtract<kPicIdLength>(frame->id.picture_id, kMaxNotYetReceivedFrames);
  auto clean_frames_to = not_yet_received_frames_.lower_bound(old_picture_id);
  not_yet_received_frames_.erase(not_yet_received_frames_.begin(),
                                 clean_frames_to);
  // Avoid re-adding picture ids that were just erased.
  if (AheadOf<uint16_t, kPicIdLength>(old_picture_id, last_picture_id_)) {
    last_picture_id_ = old_picture_id;
  }
  // Find if there has been a gap in fully received frames and save the picture
  // id of those frames in |not_yet_received_frames_|.
  if (AheadOf<uint16_t, kPicIdLength>(frame->id.picture_id, last_picture_id_)) {
    do {
      last_picture_id_ = Add<kPicIdLength>(last_picture_id_, 1);
      not_yet_received_frames_.insert(last_picture_id_);
    } while (last_picture_id_ != frame->id.picture_id);
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
    if (AheadOrAt<uint16_t, kPicIdLength>(last_pid_on_layer,
                                          frame->id.picture_id)) {
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
        AheadOrAt<uint16_t, kPicIdLength>(last_pid_on_layer,
                                          frame->id.picture_id)) {
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
    if (AheadOf<uint16_t, kPicIdLength>(layer_info_it->second[layer],
                                        frame->id.picture_id)) {
      return kDrop;
    }

    // If we have not yet received a frame between this frame and the referenced
    // frame then we have to wait for that frame to be completed first.
    auto not_received_frame_it =
        not_yet_received_frames_.upper_bound(layer_info_it->second[layer]);
    if (not_received_frame_it != not_yet_received_frames_.end() &&
        AheadOf<uint16_t, kPicIdLength>(frame->id.picture_id,
                                        *not_received_frame_it)) {
      return kStash;
    }

    if (!(AheadOf<uint16_t, kPicIdLength>(frame->id.picture_id,
                                          layer_info_it->second[layer]))) {
      RTC_LOG(LS_WARNING) << "Frame with picture id " << frame->id.picture_id
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

void RtpFrameReferenceFinder::UpdateLayerInfoVp8(RtpFrameObject* frame,
                                                 int64_t unwrapped_tl0,
                                                 uint8_t temporal_idx) {
  auto layer_info_it = layer_info_.find(unwrapped_tl0);

  // Update this layer info and newer.
  while (layer_info_it != layer_info_.end()) {
    if (layer_info_it->second[temporal_idx] != -1 &&
        AheadOf<uint16_t, kPicIdLength>(layer_info_it->second[temporal_idx],
                                        frame->id.picture_id)) {
      // The frame was not newer, then no subsequent layer info have to be
      // update.
      break;
    }

    layer_info_it->second[temporal_idx] = frame->id.picture_id;
    ++unwrapped_tl0;
    layer_info_it = layer_info_.find(unwrapped_tl0);
  }
  not_yet_received_frames_.erase(frame->id.picture_id);

  UnwrapPictureIds(frame);
}

RtpFrameReferenceFinder::FrameDecision RtpFrameReferenceFinder::ManageFrameVp9(
    RtpFrameObject* frame) {
  const RTPVideoHeader& video_header = frame->GetRtpVideoHeader();
  const RTPVideoHeaderVP9& codec_header =
      absl::get<RTPVideoHeaderVP9>(video_header.video_type_header);

  if (codec_header.picture_id == kNoPictureId ||
      codec_header.temporal_idx == kNoTemporalIdx) {
    return ManageFramePidOrSeqNum(frame, codec_header.picture_id);
  }

  // Protect against corrupted packets with arbitrary large temporal idx.
  if (codec_header.temporal_idx >= kMaxTemporalLayers ||
      codec_header.spatial_idx >= kMaxSpatialLayers)
    return kDrop;

  frame->id.spatial_layer = codec_header.spatial_idx;
  frame->inter_layer_predicted = codec_header.inter_layer_predicted;
  frame->id.picture_id = codec_header.picture_id & 0x7FFF;

  if (last_picture_id_ == -1)
    last_picture_id_ = frame->id.picture_id;

  if (codec_header.flexible_mode) {
    if (codec_header.num_ref_pics > EncodedFrame::kMaxFrameReferences) {
      return kDrop;
    }
    frame->num_references = codec_header.num_ref_pics;
    for (size_t i = 0; i < frame->num_references; ++i) {
      frame->references[i] = Subtract<kPicIdLength>(frame->id.picture_id,
                                                    codec_header.pid_diff[i]);
    }

    UnwrapPictureIds(frame);
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
      scalability_structures_[current_ss_idx_].pid_start = frame->id.picture_id;
      gof_info_.emplace(unwrapped_tl0,
                        GofInfo(&scalability_structures_[current_ss_idx_],
                                frame->id.picture_id));
    }

    const auto gof_info_it = gof_info_.find(unwrapped_tl0);
    if (gof_info_it == gof_info_.end())
      return kStash;

    info = &gof_info_it->second;

    if (frame->frame_type() == VideoFrameType::kVideoFrameKey) {
      frame->num_references = 0;
      FrameReceivedVp9(frame->id.picture_id, info);
      UnwrapPictureIds(frame);
      return kHandOff;
    }
  } else if (frame->frame_type() == VideoFrameType::kVideoFrameKey) {
    if (frame->id.spatial_layer == 0) {
      RTC_LOG(LS_WARNING) << "Received keyframe without scalability structure";
      return kDrop;
    }
    const auto gof_info_it = gof_info_.find(unwrapped_tl0);
    if (gof_info_it == gof_info_.end())
      return kStash;

    info = &gof_info_it->second;

    if (frame->frame_type() == VideoFrameType::kVideoFrameKey) {
      frame->num_references = 0;
      FrameReceivedVp9(frame->id.picture_id, info);
      UnwrapPictureIds(frame);
      return kHandOff;
    }
  } else {
    auto gof_info_it = gof_info_.find(
        (codec_header.temporal_idx == 0) ? unwrapped_tl0 - 1 : unwrapped_tl0);

    // Gof info for this frame is not available yet, stash this frame.
    if (gof_info_it == gof_info_.end())
      return kStash;

    if (codec_header.temporal_idx == 0) {
      gof_info_it = gof_info_
                        .emplace(unwrapped_tl0, GofInfo(gof_info_it->second.gof,
                                                        frame->id.picture_id))
                        .first;
    }

    info = &gof_info_it->second;
  }

  // Clean up info for base layers that are too old.
  int64_t old_tl0_pic_idx = unwrapped_tl0 - kMaxGofSaved;
  auto clean_gof_info_to = gof_info_.lower_bound(old_tl0_pic_idx);
  gof_info_.erase(gof_info_.begin(), clean_gof_info_to);

  FrameReceivedVp9(frame->id.picture_id, info);

  // Make sure we don't miss any frame that could potentially have the
  // up switch flag set.
  if (MissingRequiredFrameVp9(frame->id.picture_id, *info))
    return kStash;

  if (codec_header.temporal_up_switch)
    up_switch_.emplace(frame->id.picture_id, codec_header.temporal_idx);

  // Clean out old info about up switch frames.
  uint16_t old_picture_id = Subtract<kPicIdLength>(frame->id.picture_id, 50);
  auto up_switch_erase_to = up_switch_.lower_bound(old_picture_id);
  up_switch_.erase(up_switch_.begin(), up_switch_erase_to);

  size_t diff = ForwardDiff<uint16_t, kPicIdLength>(info->gof->pid_start,
                                                    frame->id.picture_id);
  size_t gof_idx = diff % info->gof->num_frames_in_gof;

  if (info->gof->num_ref_pics[gof_idx] > EncodedFrame::kMaxFrameReferences) {
    return kDrop;
  }
  // Populate references according to the scalability structure.
  frame->num_references = info->gof->num_ref_pics[gof_idx];
  for (size_t i = 0; i < frame->num_references; ++i) {
    frame->references[i] = Subtract<kPicIdLength>(
        frame->id.picture_id, info->gof->pid_diff[gof_idx][i]);

    // If this is a reference to a frame earlier than the last up switch point,
    // then ignore this reference.
    if (UpSwitchInIntervalVp9(frame->id.picture_id, codec_header.temporal_idx,
                              frame->references[i])) {
      --frame->num_references;
    }
  }

  // Override GOF references.
  if (!codec_header.inter_pic_predicted) {
    frame->num_references = 0;
  }

  UnwrapPictureIds(frame);
  return kHandOff;
}

bool RtpFrameReferenceFinder::MissingRequiredFrameVp9(uint16_t picture_id,
                                                      const GofInfo& info) {
  size_t diff =
      ForwardDiff<uint16_t, kPicIdLength>(info.gof->pid_start, picture_id);
  size_t gof_idx = diff % info.gof->num_frames_in_gof;
  size_t temporal_idx = info.gof->temporal_idx[gof_idx];

  if (temporal_idx >= kMaxTemporalLayers) {
    RTC_LOG(LS_WARNING) << "At most " << kMaxTemporalLayers
                        << " temporal "
                           "layers are supported.";
    return true;
  }

  // For every reference this frame has, check if there is a frame missing in
  // the interval (|ref_pid|, |picture_id|) in any of the lower temporal
  // layers. If so, we are missing a required frame.
  uint8_t num_references = info.gof->num_ref_pics[gof_idx];
  for (size_t i = 0; i < num_references; ++i) {
    uint16_t ref_pid =
        Subtract<kPicIdLength>(picture_id, info.gof->pid_diff[gof_idx][i]);
    for (size_t l = 0; l < temporal_idx; ++l) {
      auto missing_frame_it = missing_frames_for_layer_[l].lower_bound(ref_pid);
      if (missing_frame_it != missing_frames_for_layer_[l].end() &&
          AheadOf<uint16_t, kPicIdLength>(picture_id, *missing_frame_it)) {
        return true;
      }
    }
  }
  return false;
}

void RtpFrameReferenceFinder::FrameReceivedVp9(uint16_t picture_id,
                                               GofInfo* info) {
  int last_picture_id = info->last_picture_id;
  size_t gof_size = std::min(info->gof->num_frames_in_gof, kMaxVp9FramesInGof);

  // If there is a gap, find which temporal layer the missing frames
  // belong to and add the frame as missing for that temporal layer.
  // Otherwise, remove this frame from the set of missing frames.
  if (AheadOf<uint16_t, kPicIdLength>(picture_id, last_picture_id)) {
    size_t diff = ForwardDiff<uint16_t, kPicIdLength>(info->gof->pid_start,
                                                      last_picture_id);
    size_t gof_idx = diff % gof_size;

    last_picture_id = Add<kPicIdLength>(last_picture_id, 1);
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
      last_picture_id = Add<kPicIdLength>(last_picture_id, 1);
    }

    info->last_picture_id = last_picture_id;
  } else {
    size_t diff =
        ForwardDiff<uint16_t, kPicIdLength>(info->gof->pid_start, picture_id);
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

bool RtpFrameReferenceFinder::UpSwitchInIntervalVp9(uint16_t picture_id,
                                                    uint8_t temporal_idx,
                                                    uint16_t pid_ref) {
  for (auto up_switch_it = up_switch_.upper_bound(pid_ref);
       up_switch_it != up_switch_.end() &&
       AheadOf<uint16_t, kPicIdLength>(picture_id, up_switch_it->first);
       ++up_switch_it) {
    if (up_switch_it->second < temporal_idx)
      return true;
  }

  return false;
}

void RtpFrameReferenceFinder::UnwrapPictureIds(RtpFrameObject* frame) {
  for (size_t i = 0; i < frame->num_references; ++i)
    frame->references[i] = unwrapper_.Unwrap(frame->references[i]);
  frame->id.picture_id = unwrapper_.Unwrap(frame->id.picture_id);
}

void RtpFrameReferenceFinder::UpdateLastPictureIdWithPaddingH264() {
  auto seq_num_it = last_seq_num_gop_.begin();

  // Check if next sequence number is in a stashed padding packet.
  uint16_t next_padded_seq_num = seq_num_it->second.second + 1;
  auto padding_seq_num_it = stashed_padding_.lower_bound(next_padded_seq_num);

  // Check for more consecutive padding packets to increment
  // the "last-picture-id-with-padding" and remove the stashed packets.
  while (padding_seq_num_it != stashed_padding_.end() &&
         *padding_seq_num_it == next_padded_seq_num) {
    seq_num_it->second.second = next_padded_seq_num;
    ++next_padded_seq_num;
    padding_seq_num_it = stashed_padding_.erase(padding_seq_num_it);
  }
}

void RtpFrameReferenceFinder::UpdateLayerInfoH264(RtpFrameObject* frame,
                                                  int64_t unwrapped_tl0,
                                                  uint8_t temporal_idx) {
  auto layer_info_it = layer_info_.find(unwrapped_tl0);

  // Update this layer info and newer.
  while (layer_info_it != layer_info_.end()) {
    if (layer_info_it->second[temporal_idx] != -1 &&
        AheadOf<uint16_t>(layer_info_it->second[temporal_idx],
                          frame->id.picture_id)) {
      // Not a newer frame. No subsequent layer info needs update.
      break;
    }

    layer_info_it->second[temporal_idx] = frame->id.picture_id;
    ++unwrapped_tl0;
    layer_info_it = layer_info_.find(unwrapped_tl0);
  }

  for (size_t i = 0; i < frame->num_references; ++i)
    frame->references[i] = rtp_seq_num_unwrapper_.Unwrap(frame->references[i]);
  frame->id.picture_id = rtp_seq_num_unwrapper_.Unwrap(frame->id.picture_id);
}

void RtpFrameReferenceFinder::UpdateDataH264(RtpFrameObject* frame,
                                             int64_t unwrapped_tl0,
                                             uint8_t temporal_idx) {
  // Update last_seq_num_gop_ entry for last picture id.
  auto seq_num_it = last_seq_num_gop_.begin();
  uint16_t last_pic_id = seq_num_it->second.first;
  if (AheadOf<uint16_t>(frame->id.picture_id, last_pic_id)) {
    seq_num_it->second.first = frame->id.picture_id;
    seq_num_it->second.second = frame->id.picture_id;
  }
  UpdateLastPictureIdWithPaddingH264();

  UpdateLayerInfoH264(frame, unwrapped_tl0, temporal_idx);

  // Remove any current packets from |not_yet_received_seq_num_|.
  uint16_t last_seq_num_padded = seq_num_it->second.second;
  for (uint16_t n = frame->first_seq_num(); AheadOrAt(last_seq_num_padded, n);
       ++n) {
    not_yet_received_seq_num_.erase(n);
  }
}

}  // namespace video_coding
}  // namespace webrtc
