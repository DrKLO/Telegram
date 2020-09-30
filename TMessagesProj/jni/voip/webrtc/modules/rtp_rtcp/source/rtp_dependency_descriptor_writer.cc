/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/rtp_rtcp/source/rtp_dependency_descriptor_writer.h"

#include <bitset>
#include <cstddef>
#include <cstdint>
#include <iterator>
#include <vector>

#include "absl/algorithm/container.h"
#include "api/array_view.h"
#include "api/transport/rtp/dependency_descriptor.h"
#include "rtc_base/bit_buffer.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {

enum class NextLayerIdc : uint64_t {
  kSameLayer = 0,
  kNextTemporal = 1,
  kNewSpatial = 2,
  kNoMoreLayers = 3,
  kInvalid = 4
};

NextLayerIdc GetNextLayerIdc(const FrameDependencyTemplate& previous,
                             const FrameDependencyTemplate& next) {
  RTC_DCHECK_LT(next.spatial_id, DependencyDescriptor::kMaxSpatialIds);
  RTC_DCHECK_LT(next.temporal_id, DependencyDescriptor::kMaxTemporalIds);

  if (next.spatial_id == previous.spatial_id &&
      next.temporal_id == previous.temporal_id) {
    return NextLayerIdc::kSameLayer;
  } else if (next.spatial_id == previous.spatial_id &&
             next.temporal_id == previous.temporal_id + 1) {
    return NextLayerIdc::kNextTemporal;
  } else if (next.spatial_id == previous.spatial_id + 1 &&
             next.temporal_id == 0) {
    return NextLayerIdc::kNewSpatial;
  }
  // Everything else is unsupported.
  return NextLayerIdc::kInvalid;
}

}  // namespace

RtpDependencyDescriptorWriter::RtpDependencyDescriptorWriter(
    rtc::ArrayView<uint8_t> data,
    const FrameDependencyStructure& structure,
    std::bitset<32> active_chains,
    const DependencyDescriptor& descriptor)
    : descriptor_(descriptor),
      structure_(structure),
      active_chains_(active_chains),
      bit_writer_(data.data(), data.size()) {
  FindBestTemplate();
}

bool RtpDependencyDescriptorWriter::Write() {
  WriteMandatoryFields();
  if (HasExtendedFields()) {
    WriteExtendedFields();
    WriteFrameDependencyDefinition();
  }
  size_t remaining_bits = bit_writer_.RemainingBitCount();
  // Zero remaining memory to avoid leaving it uninitialized.
  if (remaining_bits % 64 != 0) {
    WriteBits(/*val=*/0, remaining_bits % 64);
  }
  for (size_t i = 0; i < remaining_bits / 64; ++i) {
    WriteBits(/*val=*/0, 64);
  }
  return !build_failed_;
}

int RtpDependencyDescriptorWriter::ValueSizeBits() const {
  static constexpr int kMandatoryFields = 1 + 1 + 6 + 16;
  int value_size_bits = kMandatoryFields + best_template_.extra_size_bits;
  if (HasExtendedFields()) {
    value_size_bits += 5;
    if (descriptor_.attached_structure)
      value_size_bits += StructureSizeBits();
    if (ShouldWriteActiveDecodeTargetsBitmask())
      value_size_bits += structure_.num_decode_targets;
  }
  return value_size_bits;
}

int RtpDependencyDescriptorWriter::StructureSizeBits() const {
  // template_id offset (6 bits) and number of decode targets (5 bits)
  int bits = 11;
  // template layers.
  bits += 2 * structure_.templates.size();
  // dtis.
  bits += 2 * structure_.templates.size() * structure_.num_decode_targets;
  // fdiffs. each templates uses 1 + 5 * sizeof(fdiff) bits.
  bits += structure_.templates.size();
  for (const FrameDependencyTemplate& frame_template : structure_.templates) {
    bits += 5 * frame_template.frame_diffs.size();
  }
  bits += rtc::BitBufferWriter::SizeNonSymmetricBits(
      structure_.num_chains, structure_.num_decode_targets + 1);
  if (structure_.num_chains > 0) {
    for (int protected_by : structure_.decode_target_protected_by_chain) {
      bits += rtc::BitBufferWriter::SizeNonSymmetricBits(protected_by,
                                                         structure_.num_chains);
    }
    bits += 4 * structure_.templates.size() * structure_.num_chains;
  }
  // Resolutions.
  bits += 1 + 32 * structure_.resolutions.size();
  return bits;
}

RtpDependencyDescriptorWriter::TemplateMatch
RtpDependencyDescriptorWriter::CalculateMatch(
    TemplateIterator frame_template) const {
  TemplateMatch result;
  result.template_position = frame_template;
  result.need_custom_fdiffs =
      descriptor_.frame_dependencies.frame_diffs != frame_template->frame_diffs;
  result.need_custom_dtis =
      descriptor_.frame_dependencies.decode_target_indications !=
      frame_template->decode_target_indications;
  result.need_custom_chains = false;
  for (int i = 0; i < structure_.num_chains; ++i) {
    if (active_chains_[i] && descriptor_.frame_dependencies.chain_diffs[i] !=
                                 frame_template->chain_diffs[i]) {
      result.need_custom_chains = true;
      break;
    }
  }

  result.extra_size_bits = 0;
  if (result.need_custom_fdiffs) {
    result.extra_size_bits +=
        2 * (1 + descriptor_.frame_dependencies.frame_diffs.size());
    for (int fdiff : descriptor_.frame_dependencies.frame_diffs) {
      if (fdiff <= (1 << 4))
        result.extra_size_bits += 4;
      else if (fdiff <= (1 << 8))
        result.extra_size_bits += 8;
      else
        result.extra_size_bits += 12;
    }
  }
  if (result.need_custom_dtis) {
    result.extra_size_bits +=
        2 * descriptor_.frame_dependencies.decode_target_indications.size();
  }
  if (result.need_custom_chains)
    result.extra_size_bits += 8 * structure_.num_chains;
  return result;
}

void RtpDependencyDescriptorWriter::FindBestTemplate() {
  const std::vector<FrameDependencyTemplate>& templates = structure_.templates;
  // Find range of templates with matching spatial/temporal id.
  auto same_layer = [&](const FrameDependencyTemplate& frame_template) {
    return descriptor_.frame_dependencies.spatial_id ==
               frame_template.spatial_id &&
           descriptor_.frame_dependencies.temporal_id ==
               frame_template.temporal_id;
  };
  auto first = absl::c_find_if(templates, same_layer);
  RTC_CHECK(first != templates.end());
  auto last = std::find_if_not(first, templates.end(), same_layer);

  best_template_ = CalculateMatch(first);
  // Search if there any better template than the first one.
  for (auto next = std::next(first); next != last; ++next) {
    TemplateMatch match = CalculateMatch(next);
    if (match.extra_size_bits < best_template_.extra_size_bits)
      best_template_ = match;
  }
}

bool RtpDependencyDescriptorWriter::ShouldWriteActiveDecodeTargetsBitmask()
    const {
  if (!descriptor_.active_decode_targets_bitmask)
    return false;
  const uint64_t all_decode_targets_bitmask =
      (uint64_t{1} << structure_.num_decode_targets) - 1;
  if (descriptor_.attached_structure &&
      descriptor_.active_decode_targets_bitmask == all_decode_targets_bitmask)
    return false;
  return true;
}

bool RtpDependencyDescriptorWriter::HasExtendedFields() const {
  return best_template_.extra_size_bits > 0 || descriptor_.attached_structure ||
         descriptor_.active_decode_targets_bitmask;
}

uint64_t RtpDependencyDescriptorWriter::TemplateId() const {
  return (best_template_.template_position - structure_.templates.begin() +
          structure_.structure_id) %
         DependencyDescriptor::kMaxTemplates;
}

void RtpDependencyDescriptorWriter::WriteBits(uint64_t val, size_t bit_count) {
  if (!bit_writer_.WriteBits(val, bit_count))
    build_failed_ = true;
}

void RtpDependencyDescriptorWriter::WriteNonSymmetric(uint32_t value,
                                                      uint32_t num_values) {
  if (!bit_writer_.WriteNonSymmetric(value, num_values))
    build_failed_ = true;
}

void RtpDependencyDescriptorWriter::WriteTemplateDependencyStructure() {
  RTC_DCHECK_GE(structure_.structure_id, 0);
  RTC_DCHECK_LT(structure_.structure_id, DependencyDescriptor::kMaxTemplates);
  RTC_DCHECK_GT(structure_.num_decode_targets, 0);
  RTC_DCHECK_LE(structure_.num_decode_targets,
                DependencyDescriptor::kMaxDecodeTargets);

  WriteBits(structure_.structure_id, 6);
  WriteBits(structure_.num_decode_targets - 1, 5);
  WriteTemplateLayers();
  WriteTemplateDtis();
  WriteTemplateFdiffs();
  WriteTemplateChains();
  uint64_t has_resolutions = structure_.resolutions.empty() ? 0 : 1;
  WriteBits(has_resolutions, 1);
  if (has_resolutions)
    WriteResolutions();
}

void RtpDependencyDescriptorWriter::WriteTemplateLayers() {
  const auto& templates = structure_.templates;
  RTC_DCHECK(!templates.empty());
  RTC_DCHECK_LE(templates.size(), DependencyDescriptor::kMaxTemplates);
  RTC_DCHECK_EQ(templates[0].spatial_id, 0);
  RTC_DCHECK_EQ(templates[0].temporal_id, 0);

  for (size_t i = 1; i < templates.size(); ++i) {
    uint64_t next_layer_idc =
        static_cast<uint64_t>(GetNextLayerIdc(templates[i - 1], templates[i]));
    RTC_DCHECK_LE(next_layer_idc, 3);
    WriteBits(next_layer_idc, 2);
  }
  WriteBits(static_cast<uint64_t>(NextLayerIdc::kNoMoreLayers), 2);
}

void RtpDependencyDescriptorWriter::WriteTemplateDtis() {
  for (const FrameDependencyTemplate& current_template : structure_.templates) {
    RTC_DCHECK_EQ(current_template.decode_target_indications.size(),
                  structure_.num_decode_targets);
    for (DecodeTargetIndication dti :
         current_template.decode_target_indications) {
      WriteBits(static_cast<uint32_t>(dti), 2);
    }
  }
}

void RtpDependencyDescriptorWriter::WriteTemplateFdiffs() {
  for (const FrameDependencyTemplate& current_template : structure_.templates) {
    for (int fdiff : current_template.frame_diffs) {
      RTC_DCHECK_GE(fdiff - 1, 0);
      RTC_DCHECK_LT(fdiff - 1, 1 << 4);
      WriteBits((1u << 4) | (fdiff - 1), 1 + 4);
    }
    // No more diffs for current template.
    WriteBits(/*val=*/0, /*bit_count=*/1);
  }
}

void RtpDependencyDescriptorWriter::WriteTemplateChains() {
  RTC_DCHECK_GE(structure_.num_chains, 0);
  RTC_DCHECK_LE(structure_.num_chains, structure_.num_decode_targets);

  WriteNonSymmetric(structure_.num_chains, structure_.num_decode_targets + 1);
  if (structure_.num_chains == 0)
    return;

  RTC_DCHECK_EQ(structure_.decode_target_protected_by_chain.size(),
                structure_.num_decode_targets);
  for (int protected_by : structure_.decode_target_protected_by_chain) {
    RTC_DCHECK_GE(protected_by, 0);
    RTC_DCHECK_LT(protected_by, structure_.num_chains);
    WriteNonSymmetric(protected_by, structure_.num_chains);
  }
  for (const auto& frame_template : structure_.templates) {
    RTC_DCHECK_EQ(frame_template.chain_diffs.size(), structure_.num_chains);
    for (int chain_diff : frame_template.chain_diffs) {
      RTC_DCHECK_GE(chain_diff, 0);
      RTC_DCHECK_LT(chain_diff, 1 << 4);
      WriteBits(chain_diff, 4);
    }
  }
}

void RtpDependencyDescriptorWriter::WriteResolutions() {
  int max_spatial_id = structure_.templates.back().spatial_id;
  RTC_DCHECK_EQ(structure_.resolutions.size(), max_spatial_id + 1);
  for (const RenderResolution& resolution : structure_.resolutions) {
    RTC_DCHECK_GT(resolution.Width(), 0);
    RTC_DCHECK_LE(resolution.Width(), 1 << 16);
    RTC_DCHECK_GT(resolution.Height(), 0);
    RTC_DCHECK_LE(resolution.Height(), 1 << 16);

    WriteBits(resolution.Width() - 1, 16);
    WriteBits(resolution.Height() - 1, 16);
  }
}

void RtpDependencyDescriptorWriter::WriteMandatoryFields() {
  WriteBits(descriptor_.first_packet_in_frame, 1);
  WriteBits(descriptor_.last_packet_in_frame, 1);
  WriteBits(TemplateId(), 6);
  WriteBits(descriptor_.frame_number, 16);
}

void RtpDependencyDescriptorWriter::WriteExtendedFields() {
  uint64_t template_dependency_structure_present_flag =
      descriptor_.attached_structure ? 1u : 0u;
  WriteBits(template_dependency_structure_present_flag, 1);
  uint64_t active_decode_targets_present_flag =
      ShouldWriteActiveDecodeTargetsBitmask() ? 1u : 0u;
  WriteBits(active_decode_targets_present_flag, 1);
  WriteBits(best_template_.need_custom_dtis, 1);
  WriteBits(best_template_.need_custom_fdiffs, 1);
  WriteBits(best_template_.need_custom_chains, 1);
  if (template_dependency_structure_present_flag)
    WriteTemplateDependencyStructure();
  if (active_decode_targets_present_flag)
    WriteBits(*descriptor_.active_decode_targets_bitmask,
              structure_.num_decode_targets);
}

void RtpDependencyDescriptorWriter::WriteFrameDependencyDefinition() {
  if (best_template_.need_custom_dtis)
    WriteFrameDtis();
  if (best_template_.need_custom_fdiffs)
    WriteFrameFdiffs();
  if (best_template_.need_custom_chains)
    WriteFrameChains();
}

void RtpDependencyDescriptorWriter::WriteFrameDtis() {
  RTC_DCHECK_EQ(descriptor_.frame_dependencies.decode_target_indications.size(),
                structure_.num_decode_targets);
  for (DecodeTargetIndication dti :
       descriptor_.frame_dependencies.decode_target_indications) {
    WriteBits(static_cast<uint32_t>(dti), 2);
  }
}

void RtpDependencyDescriptorWriter::WriteFrameFdiffs() {
  for (int fdiff : descriptor_.frame_dependencies.frame_diffs) {
    RTC_DCHECK_GT(fdiff, 0);
    RTC_DCHECK_LE(fdiff, 1 << 12);
    if (fdiff <= (1 << 4))
      WriteBits((1u << 4) | (fdiff - 1), 2 + 4);
    else if (fdiff <= (1 << 8))
      WriteBits((2u << 8) | (fdiff - 1), 2 + 8);
    else  // fdiff <= (1 << 12)
      WriteBits((3u << 12) | (fdiff - 1), 2 + 12);
  }
  // No more diffs.
  WriteBits(/*val=*/0, /*bit_count=*/2);
}

void RtpDependencyDescriptorWriter::WriteFrameChains() {
  RTC_DCHECK_EQ(descriptor_.frame_dependencies.chain_diffs.size(),
                structure_.num_chains);
  for (int i = 0; i < structure_.num_chains; ++i) {
    int chain_diff =
        active_chains_[i] ? descriptor_.frame_dependencies.chain_diffs[i] : 0;
    RTC_DCHECK_GE(chain_diff, 0);
    RTC_DCHECK_LT(chain_diff, 1 << 8);
    WriteBits(chain_diff, 8);
  }
}

}  // namespace webrtc
