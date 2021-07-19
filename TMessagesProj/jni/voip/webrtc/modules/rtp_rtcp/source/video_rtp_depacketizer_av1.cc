/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/video_rtp_depacketizer_av1.h"

#include <stddef.h>
#include <stdint.h>

#include <utility>

#include "modules/rtp_rtcp/source/rtp_video_header.h"
#include "rtc_base/byte_buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"

namespace webrtc {
namespace {
// AV1 format:
//
// RTP payload syntax:
//     0 1 2 3 4 5 6 7
//    +-+-+-+-+-+-+-+-+
//    |Z|Y| W |N|-|-|-| (REQUIRED)
//    +=+=+=+=+=+=+=+=+ (REPEATED W-1 times, or any times if W = 0)
//    |1|             |
//    +-+ OBU fragment|
//    |1|             | (REQUIRED, leb128 encoded)
//    +-+    size     |
//    |0|             |
//    +-+-+-+-+-+-+-+-+
//    |  OBU fragment |
//    |     ...       |
//    +=+=+=+=+=+=+=+=+
//    |     ...       |
//    +=+=+=+=+=+=+=+=+ if W > 0, last fragment MUST NOT have size field
//    |  OBU fragment |
//    |     ...       |
//    +=+=+=+=+=+=+=+=+
//
//
// OBU syntax:
//     0 1 2 3 4 5 6 7
//    +-+-+-+-+-+-+-+-+
//    |0| type  |X|S|-| (REQUIRED)
//    +-+-+-+-+-+-+-+-+
// X: | TID |SID|-|-|-| (OPTIONAL)
//    +-+-+-+-+-+-+-+-+
//    |1|             |
//    +-+ OBU payload |
// S: |1|             | (OPTIONAL, variable length leb128 encoded)
//    +-+    size     |
//    |0|             |
//    +-+-+-+-+-+-+-+-+
//    |  OBU payload  |
//    |     ...       |
class ArrayOfArrayViews {
 public:
  class const_iterator;
  ArrayOfArrayViews() = default;
  ArrayOfArrayViews(const ArrayOfArrayViews&) = default;
  ArrayOfArrayViews& operator=(const ArrayOfArrayViews&) = default;
  ~ArrayOfArrayViews() = default;

  const_iterator begin() const;
  const_iterator end() const;
  bool empty() const { return data_.empty(); }
  size_t size() const { return size_; }
  void CopyTo(uint8_t* destination, const_iterator first) const;

  void Append(const uint8_t* data, size_t size) {
    data_.emplace_back(data, size);
    size_ += size;
  }

 private:
  using Storage = absl::InlinedVector<rtc::ArrayView<const uint8_t>, 2>;

  size_t size_ = 0;
  Storage data_;
};

class ArrayOfArrayViews::const_iterator {
 public:
  const_iterator() = default;
  const_iterator(const const_iterator&) = default;
  const_iterator& operator=(const const_iterator&) = default;

  const_iterator& operator++() {
    if (++inner_ == outer_->size()) {
      ++outer_;
      inner_ = 0;
    }
    return *this;
  }
  uint8_t operator*() const { return (*outer_)[inner_]; }

  friend bool operator==(const const_iterator& lhs, const const_iterator& rhs) {
    return lhs.outer_ == rhs.outer_ && lhs.inner_ == rhs.inner_;
  }

 private:
  friend ArrayOfArrayViews;
  const_iterator(ArrayOfArrayViews::Storage::const_iterator outer, size_t inner)
      : outer_(outer), inner_(inner) {}

  Storage::const_iterator outer_;
  size_t inner_;
};

ArrayOfArrayViews::const_iterator ArrayOfArrayViews::begin() const {
  return const_iterator(data_.begin(), 0);
}

ArrayOfArrayViews::const_iterator ArrayOfArrayViews::end() const {
  return const_iterator(data_.end(), 0);
}

void ArrayOfArrayViews::CopyTo(uint8_t* destination,
                               const_iterator first) const {
  if (first == end()) {
    // Empty OBU payload. E.g. Temporal Delimiters are always empty.
    return;
  }
  size_t first_chunk_size = first.outer_->size() - first.inner_;
  memcpy(destination, first.outer_->data() + first.inner_, first_chunk_size);
  destination += first_chunk_size;
  for (auto it = std::next(first.outer_); it != data_.end(); ++it) {
    memcpy(destination, it->data(), it->size());
    destination += it->size();
  }
}

struct ObuInfo {
  // Size of the obu_header and obu_size fields in the ouput frame.
  size_t prefix_size = 0;
  // obu_header() and obu_size (leb128 encoded payload_size).
  // obu_header can be up to 2 bytes, obu_size - up to 5.
  std::array<uint8_t, 7> prefix;
  // Size of the obu payload in the output frame, i.e. excluding header
  size_t payload_size = 0;
  // iterator pointing to the beginning of the obu payload.
  ArrayOfArrayViews::const_iterator payload_offset;
  // OBU payloads as written in the rtp packet payloads.
  ArrayOfArrayViews data;
};
// Expect that majority of the frame won't use more than 4 obus.
// In a simple stream delta frame consist of single Frame OBU, while key frame
// also has Sequence Header OBU.
using VectorObuInfo = absl::InlinedVector<ObuInfo, 4>;

constexpr uint8_t kObuSizePresentBit = 0b0'0000'010;

bool ObuHasExtension(uint8_t obu_header) {
  return obu_header & 0b0'0000'100u;
}

bool ObuHasSize(uint8_t obu_header) {
  return obu_header & kObuSizePresentBit;
}

bool RtpStartsWithFragment(uint8_t aggregation_header) {
  return aggregation_header & 0b1000'0000u;
}
bool RtpEndsWithFragment(uint8_t aggregation_header) {
  return aggregation_header & 0b0100'0000u;
}
int RtpNumObus(uint8_t aggregation_header) {  // 0 for any number of obus.
  return (aggregation_header & 0b0011'0000u) >> 4;
}
int RtpStartsNewCodedVideoSequence(uint8_t aggregation_header) {
  return aggregation_header & 0b0000'1000u;
}

// Reorgonizes array of rtp payloads into array of obus:
// fills ObuInfo::data field.
// Returns empty vector on error.
VectorObuInfo ParseObus(
    rtc::ArrayView<const rtc::ArrayView<const uint8_t>> rtp_payloads) {
  VectorObuInfo obu_infos;
  bool expect_continues_obu = false;
  for (rtc::ArrayView<const uint8_t> rtp_payload : rtp_payloads) {
    rtc::ByteBufferReader payload(
        reinterpret_cast<const char*>(rtp_payload.data()), rtp_payload.size());
    uint8_t aggregation_header;
    if (!payload.ReadUInt8(&aggregation_header)) {
      RTC_DLOG(WARNING) << "Failed to find aggregation header in the packet.";
      return {};
    }
    // Z-bit: 1 if the first OBU contained in the packet is a continuation of a
    // previous OBU.
    bool continues_obu = RtpStartsWithFragment(aggregation_header);
    if (continues_obu != expect_continues_obu) {
      RTC_DLOG(WARNING) << "Unexpected Z-bit " << continues_obu;
      return {};
    }
    int num_expected_obus = RtpNumObus(aggregation_header);
    if (payload.Length() == 0) {
      // rtp packet has just the aggregation header. That may be valid only when
      // there is exactly one fragment in the packet of size 0.
      if (num_expected_obus != 1) {
        RTC_DLOG(WARNING) << "Invalid packet with just an aggregation header.";
        return {};
      }
      if (!continues_obu) {
        // Empty packet just to notify there is a new OBU.
        obu_infos.emplace_back();
      }
      expect_continues_obu = RtpEndsWithFragment(aggregation_header);
      continue;
    }

    for (int obu_index = 1; payload.Length() > 0; ++obu_index) {
      ObuInfo& obu_info = (obu_index == 1 && continues_obu)
                              ? obu_infos.back()
                              : obu_infos.emplace_back();
      uint64_t fragment_size;
      // When num_expected_obus > 0, last OBU (fragment) is not preceeded by
      // the size field. See W field in
      // https://aomediacodec.github.io/av1-rtp-spec/#43-av1-aggregation-header
      bool has_fragment_size = (obu_index != num_expected_obus);
      if (has_fragment_size) {
        if (!payload.ReadUVarint(&fragment_size)) {
          RTC_DLOG(WARNING) << "Failed to read fragment size for obu #"
                            << obu_index << "/" << num_expected_obus;
          return {};
        }
        if (fragment_size > payload.Length()) {
          // Malformed input: written size is larger than remaining buffer.
          RTC_DLOG(WARNING) << "Malformed fragment size " << fragment_size
                            << " is larger than remaining size "
                            << payload.Length() << " while reading obu #"
                            << obu_index << "/" << num_expected_obus;
          return {};
        }
      } else {
        fragment_size = payload.Length();
      }
      // While it is in-practical to pass empty fragments, it is still possible.
      if (fragment_size > 0) {
        obu_info.data.Append(reinterpret_cast<const uint8_t*>(payload.Data()),
                             fragment_size);
        payload.Consume(fragment_size);
      }
    }
    // Z flag should be same as Y flag of the next packet.
    expect_continues_obu = RtpEndsWithFragment(aggregation_header);
  }
  if (expect_continues_obu) {
    RTC_DLOG(WARNING) << "Last packet shouldn't have last obu fragmented.";
    return {};
  }
  return obu_infos;
}

// Returns number of bytes consumed.
int WriteLeb128(uint32_t value, uint8_t* buffer) {
  int size = 0;
  while (value >= 0x80) {
    buffer[size] = 0x80 | (value & 0x7F);
    ++size;
    value >>= 7;
  }
  buffer[size] = value;
  ++size;
  return size;
}

// Calculates sizes for the Obu, i.e. base on ObuInfo::data field calculates
// all other fields in the ObuInfo structure.
// Returns false if obu found to be misformed.
bool CalculateObuSizes(ObuInfo* obu_info) {
  if (obu_info->data.empty()) {
    RTC_DLOG(WARNING) << "Invalid bitstream: empty obu provided.";
    return false;
  }
  auto it = obu_info->data.begin();
  uint8_t obu_header = *it;
  obu_info->prefix[0] = obu_header | kObuSizePresentBit;
  obu_info->prefix_size = 1;
  ++it;
  if (ObuHasExtension(obu_header)) {
    if (it == obu_info->data.end()) {
      return false;
    }
    obu_info->prefix[1] = *it;  // obu_extension_header
    obu_info->prefix_size = 2;
    ++it;
  }
  // Read, validate, and skip size, if present.
  if (!ObuHasSize(obu_header)) {
    obu_info->payload_size = obu_info->data.size() - obu_info->prefix_size;
  } else {
    // Read leb128 encoded field obu_size.
    uint64_t obu_size_bytes = 0;
    // Number of bytes obu_size field occupy in the bitstream.
    int size_of_obu_size_bytes = 0;
    uint8_t leb128_byte;
    do {
      if (it == obu_info->data.end() || size_of_obu_size_bytes >= 8) {
        RTC_DLOG(WARNING)
            << "Failed to read obu_size. obu_size field is too long: "
            << size_of_obu_size_bytes << " bytes processed.";
        return false;
      }
      leb128_byte = *it;
      obu_size_bytes |= uint64_t{leb128_byte & 0x7Fu}
                        << (size_of_obu_size_bytes * 7);
      ++size_of_obu_size_bytes;
      ++it;
    } while ((leb128_byte & 0x80) != 0);

    obu_info->payload_size =
        obu_info->data.size() - obu_info->prefix_size - size_of_obu_size_bytes;
    if (obu_size_bytes != obu_info->payload_size) {
      // obu_size was present in the bitstream and mismatches calculated size.
      RTC_DLOG(WARNING) << "Mismatch in obu_size. signaled: " << obu_size_bytes
                        << ", actual: " << obu_info->payload_size;
      return false;
    }
  }
  obu_info->payload_offset = it;
  obu_info->prefix_size +=
      WriteLeb128(rtc::dchecked_cast<uint32_t>(obu_info->payload_size),
                  obu_info->prefix.data() + obu_info->prefix_size);
  return true;
}

}  // namespace

rtc::scoped_refptr<EncodedImageBuffer> VideoRtpDepacketizerAv1::AssembleFrame(
    rtc::ArrayView<const rtc::ArrayView<const uint8_t>> rtp_payloads) {
  VectorObuInfo obu_infos = ParseObus(rtp_payloads);
  if (obu_infos.empty()) {
    return nullptr;
  }

  size_t frame_size = 0;
  for (ObuInfo& obu_info : obu_infos) {
    if (!CalculateObuSizes(&obu_info)) {
      return nullptr;
    }
    frame_size += (obu_info.prefix_size + obu_info.payload_size);
  }

  rtc::scoped_refptr<EncodedImageBuffer> bitstream =
      EncodedImageBuffer::Create(frame_size);
  uint8_t* write_at = bitstream->data();
  for (const ObuInfo& obu_info : obu_infos) {
    // Copy the obu_header and obu_size fields.
    memcpy(write_at, obu_info.prefix.data(), obu_info.prefix_size);
    write_at += obu_info.prefix_size;
    // Copy the obu payload.
    obu_info.data.CopyTo(write_at, obu_info.payload_offset);
    write_at += obu_info.payload_size;
  }
  RTC_CHECK_EQ(write_at - bitstream->data(), bitstream->size());
  return bitstream;
}

absl::optional<VideoRtpDepacketizer::ParsedRtpPayload>
VideoRtpDepacketizerAv1::Parse(rtc::CopyOnWriteBuffer rtp_payload) {
  if (rtp_payload.size() == 0) {
    RTC_DLOG(LS_ERROR) << "Empty rtp payload.";
    return absl::nullopt;
  }
  uint8_t aggregation_header = rtp_payload.cdata()[0];
  if (RtpStartsNewCodedVideoSequence(aggregation_header) &&
      RtpStartsWithFragment(aggregation_header)) {
    // new coded video sequence can't start from an OBU fragment.
    return absl::nullopt;
  }
  absl::optional<ParsedRtpPayload> parsed(absl::in_place);

  // To assemble frame, all of the rtp payload is required, including
  // aggregation header.
  parsed->video_payload = std::move(rtp_payload);

  parsed->video_header.codec = VideoCodecType::kVideoCodecAV1;
  // These are not accurate since frame may consist of several packet aligned
  // chunks of obus, but should be good enough for most cases. It might produce
  // frame that do not map to any real frame, but av1 decoder should be able to
  // handle it since it promise to handle individual obus rather than full
  // frames.
  parsed->video_header.is_first_packet_in_frame =
      !RtpStartsWithFragment(aggregation_header);
  parsed->video_header.is_last_packet_in_frame =
      !RtpEndsWithFragment(aggregation_header);

  parsed->video_header.frame_type =
      RtpStartsNewCodedVideoSequence(aggregation_header)
          ? VideoFrameType::kVideoFrameKey
          : VideoFrameType::kVideoFrameDelta;
  return parsed;
}

}  // namespace webrtc
