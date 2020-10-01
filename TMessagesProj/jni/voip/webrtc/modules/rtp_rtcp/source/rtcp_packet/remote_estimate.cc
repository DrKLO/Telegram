/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/rtp_rtcp/source/rtcp_packet/remote_estimate.h"

#include <algorithm>
#include <cmath>
#include <type_traits>
#include <utility>
#include <vector>

#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/rtcp_packet/common_header.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace rtcp {
namespace {

static constexpr int kFieldValueSize = 3;
static constexpr int kFieldSize = 1 + kFieldValueSize;
static constexpr DataRate kDataRateResolution = DataRate::KilobitsPerSec(1);
constexpr int64_t kMaxEncoded = (1 << (kFieldValueSize * 8)) - 1;

class DataRateSerializer {
 public:
  DataRateSerializer(
      uint8_t id,
      std::function<DataRate*(NetworkStateEstimate*)> field_getter)
      : id_(id), field_getter_(field_getter) {}

  uint8_t id() const { return id_; }

  void Read(const uint8_t* src, NetworkStateEstimate* target) const {
    int64_t scaled = ByteReader<uint32_t, kFieldValueSize>::ReadBigEndian(src);
    if (scaled == kMaxEncoded) {
      *field_getter_(target) = DataRate::PlusInfinity();
    } else {
      *field_getter_(target) = kDataRateResolution * scaled;
    }
  }

  bool Write(const NetworkStateEstimate& src, uint8_t* target) const {
    auto value = *field_getter_(const_cast<NetworkStateEstimate*>(&src));
    if (value.IsMinusInfinity()) {
      RTC_LOG(LS_WARNING) << "Trying to serialize MinusInfinity";
      return false;
    }
    ByteWriter<uint8_t>::WriteBigEndian(target++, id_);
    int64_t scaled;
    if (value.IsPlusInfinity()) {
      scaled = kMaxEncoded;
    } else {
      scaled = value / kDataRateResolution;
      if (scaled >= kMaxEncoded) {
        scaled = kMaxEncoded;
        RTC_LOG(LS_WARNING) << ToString(value) << " is larger than max ("
                            << ToString(kMaxEncoded * kDataRateResolution)
                            << "), encoded as PlusInfinity.";
      }
    }
    ByteWriter<uint32_t, kFieldValueSize>::WriteBigEndian(target, scaled);
    return true;
  }

 private:
  const uint8_t id_;
  const std::function<DataRate*(NetworkStateEstimate*)> field_getter_;
};

class RemoteEstimateSerializerImpl : public RemoteEstimateSerializer {
 public:
  explicit RemoteEstimateSerializerImpl(std::vector<DataRateSerializer> fields)
      : fields_(fields) {}

  rtc::Buffer Serialize(const NetworkStateEstimate& src) const override {
    size_t max_size = fields_.size() * kFieldSize;
    size_t size = 0;
    rtc::Buffer buf(max_size);
    for (const auto& field : fields_) {
      if (field.Write(src, buf.data() + size)) {
        size += kFieldSize;
      }
    }
    buf.SetSize(size);
    return buf;
  }

  bool Parse(rtc::ArrayView<const uint8_t> src,
             NetworkStateEstimate* target) const override {
    if (src.size() % kFieldSize != 0)
      return false;
    RTC_DCHECK_EQ(src.size() % kFieldSize, 0);
    for (const uint8_t* data_ptr = src.data(); data_ptr < src.end();
         data_ptr += kFieldSize) {
      uint8_t field_id = ByteReader<uint8_t>::ReadBigEndian(data_ptr);
      for (const auto& field : fields_) {
        if (field.id() == field_id) {
          field.Read(data_ptr + 1, target);
          break;
        }
      }
    }
    return true;
  }

 private:
  const std::vector<DataRateSerializer> fields_;
};

}  // namespace

const RemoteEstimateSerializer* GetRemoteEstimateSerializer() {
  using E = NetworkStateEstimate;
  static auto* serializer = new RemoteEstimateSerializerImpl({
      {1, [](E* e) { return &e->link_capacity_lower; }},
      {2, [](E* e) { return &e->link_capacity_upper; }},
  });
  return serializer;
}

RemoteEstimate::RemoteEstimate() : serializer_(GetRemoteEstimateSerializer()) {
  SetSubType(kSubType);
  SetName(kName);
  SetSenderSsrc(0);
}

RemoteEstimate::RemoteEstimate(App&& app)
    : App(std::move(app)), serializer_(GetRemoteEstimateSerializer()) {}

bool RemoteEstimate::ParseData() {
  return serializer_->Parse({data(), data_size()}, &estimate_);
}

void RemoteEstimate::SetEstimate(NetworkStateEstimate estimate) {
  estimate_ = estimate;
  auto buf = serializer_->Serialize(estimate);
  SetData(buf.data(), buf.size());
}

}  // namespace rtcp
}  // namespace webrtc
