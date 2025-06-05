/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/rtp_rtcp/source/rtp_dependency_descriptor_reader.h"

#include <memory>
#include <utility>
#include <vector>

#include "api/transport/rtp/dependency_descriptor.h"
#include "rtc_base/bitstream_reader.h"
#include "rtc_base/checks.h"

namespace webrtc {

RtpDependencyDescriptorReader::RtpDependencyDescriptorReader(
    rtc::ArrayView<const uint8_t> raw_data,
    const FrameDependencyStructure* structure,
    DependencyDescriptor* descriptor)
    : descriptor_(descriptor), buffer_(raw_data) {
  RTC_DCHECK(descriptor);

  ReadMandatoryFields();
  if (raw_data.size() > 3)
    ReadExtendedFields();

  structure_ = descriptor->attached_structure
                   ? descriptor->attached_structure.get()
                   : structure;
  if (structure_ == nullptr) {
    buffer_.Invalidate();
    return;
  }
  if (active_decode_targets_present_flag_) {
    descriptor->active_decode_targets_bitmask =
        buffer_.ReadBits(structure_->num_decode_targets);
  }

  ReadFrameDependencyDefinition();
}

void RtpDependencyDescriptorReader::ReadTemplateDependencyStructure() {
  descriptor_->attached_structure =
      std::make_unique<FrameDependencyStructure>();
  descriptor_->attached_structure->structure_id = buffer_.ReadBits(6);
  descriptor_->attached_structure->num_decode_targets = buffer_.ReadBits(5) + 1;

  ReadTemplateLayers();
  ReadTemplateDtis();
  ReadTemplateFdiffs();
  ReadTemplateChains();

  if (buffer_.Read<bool>())
    ReadResolutions();
}

void RtpDependencyDescriptorReader::ReadTemplateLayers() {
  enum NextLayerIdc {
    kSameLayer = 0,
    kNextTemporalLayer = 1,
    kNextSpatialLayer = 2,
    kNoMoreTemplates = 3,
  };
  std::vector<FrameDependencyTemplate> templates;

  int temporal_id = 0;
  int spatial_id = 0;
  NextLayerIdc next_layer_idc;
  do {
    if (templates.size() == DependencyDescriptor::kMaxTemplates) {
      buffer_.Invalidate();
      break;
    }
    templates.emplace_back();
    FrameDependencyTemplate& last_template = templates.back();
    last_template.temporal_id = temporal_id;
    last_template.spatial_id = spatial_id;

    next_layer_idc = static_cast<NextLayerIdc>(buffer_.ReadBits(2));
    if (next_layer_idc == kNextTemporalLayer) {
      temporal_id++;
      if (temporal_id >= DependencyDescriptor::kMaxTemporalIds) {
        buffer_.Invalidate();
        break;
      }
    } else if (next_layer_idc == kNextSpatialLayer) {
      temporal_id = 0;
      spatial_id++;
      if (spatial_id >= DependencyDescriptor::kMaxSpatialIds) {
        buffer_.Invalidate();
        break;
      }
    }
  } while (next_layer_idc != kNoMoreTemplates && buffer_.Ok());

  descriptor_->attached_structure->templates = std::move(templates);
}

void RtpDependencyDescriptorReader::ReadTemplateDtis() {
  FrameDependencyStructure* structure = descriptor_->attached_structure.get();
  for (FrameDependencyTemplate& current_template : structure->templates) {
    current_template.decode_target_indications.resize(
        structure->num_decode_targets);
    for (int i = 0; i < structure->num_decode_targets; ++i) {
      current_template.decode_target_indications[i] =
          static_cast<DecodeTargetIndication>(buffer_.ReadBits(2));
    }
  }
}

void RtpDependencyDescriptorReader::ReadTemplateFdiffs() {
  for (FrameDependencyTemplate& current_template :
       descriptor_->attached_structure->templates) {
    for (bool fdiff_follows = buffer_.Read<bool>(); fdiff_follows;
         fdiff_follows = buffer_.Read<bool>()) {
      uint64_t fdiff_minus_one = buffer_.ReadBits(4);
      current_template.frame_diffs.push_back(fdiff_minus_one + 1);
    }
  }
}

void RtpDependencyDescriptorReader::ReadTemplateChains() {
  FrameDependencyStructure* structure = descriptor_->attached_structure.get();
  structure->num_chains =
      buffer_.ReadNonSymmetric(structure->num_decode_targets + 1);
  if (structure->num_chains == 0)
    return;
  for (int i = 0; i < structure->num_decode_targets; ++i) {
    uint32_t protected_by_chain =
        buffer_.ReadNonSymmetric(structure->num_chains);
    structure->decode_target_protected_by_chain.push_back(protected_by_chain);
  }
  for (FrameDependencyTemplate& frame_template : structure->templates) {
    for (int chain_id = 0; chain_id < structure->num_chains; ++chain_id) {
      frame_template.chain_diffs.push_back(buffer_.ReadBits(4));
    }
  }
}

void RtpDependencyDescriptorReader::ReadResolutions() {
  FrameDependencyStructure* structure = descriptor_->attached_structure.get();
  // The way templates are bitpacked, they are always ordered by spatial_id.
  int spatial_layers = structure->templates.back().spatial_id + 1;
  structure->resolutions.reserve(spatial_layers);
  for (int sid = 0; sid < spatial_layers; ++sid) {
    uint16_t width_minus_1 = buffer_.Read<uint16_t>();
    uint16_t height_minus_1 = buffer_.Read<uint16_t>();
    structure->resolutions.emplace_back(width_minus_1 + 1, height_minus_1 + 1);
  }
}

void RtpDependencyDescriptorReader::ReadMandatoryFields() {
  descriptor_->first_packet_in_frame = buffer_.Read<bool>();
  descriptor_->last_packet_in_frame = buffer_.Read<bool>();
  frame_dependency_template_id_ = buffer_.ReadBits(6);
  descriptor_->frame_number = buffer_.Read<uint16_t>();
}

void RtpDependencyDescriptorReader::ReadExtendedFields() {
  bool template_dependency_structure_present_flag = buffer_.Read<bool>();
  active_decode_targets_present_flag_ = buffer_.Read<bool>();
  custom_dtis_flag_ = buffer_.Read<bool>();
  custom_fdiffs_flag_ = buffer_.Read<bool>();
  custom_chains_flag_ = buffer_.Read<bool>();
  if (template_dependency_structure_present_flag) {
    ReadTemplateDependencyStructure();
    RTC_DCHECK(descriptor_->attached_structure);
    descriptor_->active_decode_targets_bitmask =
        (uint64_t{1} << descriptor_->attached_structure->num_decode_targets) -
        1;
  }
}

void RtpDependencyDescriptorReader::ReadFrameDependencyDefinition() {
  size_t template_index =
      (frame_dependency_template_id_ + DependencyDescriptor::kMaxTemplates -
       structure_->structure_id) %
      DependencyDescriptor::kMaxTemplates;

  if (template_index >= structure_->templates.size()) {
    buffer_.Invalidate();
    return;
  }

  // Copy all the fields from the matching template
  descriptor_->frame_dependencies = structure_->templates[template_index];

  if (custom_dtis_flag_)
    ReadFrameDtis();
  if (custom_fdiffs_flag_)
    ReadFrameFdiffs();
  if (custom_chains_flag_)
    ReadFrameChains();

  if (structure_->resolutions.empty()) {
    descriptor_->resolution = absl::nullopt;
  } else {
    // Format guarantees that if there were resolutions in the last structure,
    // then each spatial layer got one.
    RTC_DCHECK_LE(descriptor_->frame_dependencies.spatial_id,
                  structure_->resolutions.size());
    descriptor_->resolution =
        structure_->resolutions[descriptor_->frame_dependencies.spatial_id];
  }
}

void RtpDependencyDescriptorReader::ReadFrameDtis() {
  RTC_DCHECK_EQ(
      descriptor_->frame_dependencies.decode_target_indications.size(),
      structure_->num_decode_targets);
  for (auto& dti : descriptor_->frame_dependencies.decode_target_indications) {
    dti = static_cast<DecodeTargetIndication>(buffer_.ReadBits(2));
  }
}

void RtpDependencyDescriptorReader::ReadFrameFdiffs() {
  descriptor_->frame_dependencies.frame_diffs.clear();
  for (uint64_t next_fdiff_size = buffer_.ReadBits(2); next_fdiff_size > 0;
       next_fdiff_size = buffer_.ReadBits(2)) {
    uint64_t fdiff_minus_one = buffer_.ReadBits(4 * next_fdiff_size);
    descriptor_->frame_dependencies.frame_diffs.push_back(fdiff_minus_one + 1);
  }
}

void RtpDependencyDescriptorReader::ReadFrameChains() {
  RTC_DCHECK_EQ(descriptor_->frame_dependencies.chain_diffs.size(),
                structure_->num_chains);
  for (auto& chain_diff : descriptor_->frame_dependencies.chain_diffs) {
    chain_diff = buffer_.Read<uint8_t>();
  }
}

}  // namespace webrtc
