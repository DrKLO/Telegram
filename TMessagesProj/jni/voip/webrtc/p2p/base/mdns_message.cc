/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/mdns_message.h"

#include "rtc_base/logging.h"
#include "rtc_base/net_helpers.h"
#include "rtc_base/string_encode.h"

namespace webrtc {

namespace {
// RFC 1035, Section 4.1.1.
//
// QR bit.
constexpr uint16_t kMdnsFlagMaskQueryOrResponse = 0x8000;
// AA bit.
constexpr uint16_t kMdnsFlagMaskAuthoritative = 0x0400;
// RFC 1035, Section 4.1.2, QCLASS and RFC 6762, Section 18.12, repurposing of
// top bit of QCLASS as the unicast response bit.
constexpr uint16_t kMdnsQClassMaskUnicastResponse = 0x8000;
constexpr size_t kMdnsHeaderSizeBytes = 12;

bool ReadDomainName(MessageBufferReader* buf, std::string* name) {
  size_t name_start_pos = buf->CurrentOffset();
  uint8_t label_length;
  if (!buf->ReadUInt8(&label_length)) {
    return false;
  }
  // RFC 1035, Section 4.1.4.
  //
  // If the first two bits of the length octet are ones, the name is compressed
  // and the rest six bits with the next octet denotes its position in the
  // message by the offset from the start of the message.
  auto is_pointer = [](uint8_t octet) {
    return (octet & 0x80) && (octet & 0x40);
  };
  while (label_length && !is_pointer(label_length)) {
    // RFC 1035, Section 2.3.1, labels are restricted to 63 octets or less.
    if (label_length > 63) {
      return false;
    }
    std::string label;
    if (!buf->ReadString(&label, label_length)) {
      return false;
    }
    (*name) += label + ".";
    if (!buf->ReadUInt8(&label_length)) {
      return false;
    }
  }
  if (is_pointer(label_length)) {
    uint8_t next_octet;
    if (!buf->ReadUInt8(&next_octet)) {
      return false;
    }
    size_t pos_jump_to = ((label_length & 0x3f) << 8) | next_octet;
    // A legitimate pointer only refers to a prior occurrence of the same name,
    // and we should only move strictly backward to a prior name field after the
    // header.
    if (pos_jump_to >= name_start_pos || pos_jump_to < kMdnsHeaderSizeBytes) {
      return false;
    }
    MessageBufferReader new_buf(buf->MessageData(), buf->MessageLength());
    if (!new_buf.Consume(pos_jump_to)) {
      return false;
    }
    return ReadDomainName(&new_buf, name);
  }
  return true;
}

void WriteDomainName(rtc::ByteBufferWriter* buf, const std::string& name) {
  std::vector<std::string> labels;
  rtc::tokenize(name, '.', &labels);
  for (const auto& label : labels) {
    buf->WriteUInt8(label.length());
    buf->WriteString(label);
  }
  buf->WriteUInt8(0);
}

}  // namespace

void MdnsHeader::SetQueryOrResponse(bool is_query) {
  if (is_query) {
    flags &= ~kMdnsFlagMaskQueryOrResponse;
  } else {
    flags |= kMdnsFlagMaskQueryOrResponse;
  }
}

void MdnsHeader::SetAuthoritative(bool is_authoritative) {
  if (is_authoritative) {
    flags |= kMdnsFlagMaskAuthoritative;
  } else {
    flags &= ~kMdnsFlagMaskAuthoritative;
  }
}

bool MdnsHeader::IsAuthoritative() const {
  return flags & kMdnsFlagMaskAuthoritative;
}

bool MdnsHeader::Read(MessageBufferReader* buf) {
  if (!buf->ReadUInt16(&id) || !buf->ReadUInt16(&flags) ||
      !buf->ReadUInt16(&qdcount) || !buf->ReadUInt16(&ancount) ||
      !buf->ReadUInt16(&nscount) || !buf->ReadUInt16(&arcount)) {
    RTC_LOG(LS_ERROR) << "Invalid mDNS header.";
    return false;
  }
  return true;
}

void MdnsHeader::Write(rtc::ByteBufferWriter* buf) const {
  buf->WriteUInt16(id);
  buf->WriteUInt16(flags);
  buf->WriteUInt16(qdcount);
  buf->WriteUInt16(ancount);
  buf->WriteUInt16(nscount);
  buf->WriteUInt16(arcount);
}

bool MdnsHeader::IsQuery() const {
  return !(flags & kMdnsFlagMaskQueryOrResponse);
}

MdnsSectionEntry::MdnsSectionEntry() = default;
MdnsSectionEntry::~MdnsSectionEntry() = default;
MdnsSectionEntry::MdnsSectionEntry(const MdnsSectionEntry& other) = default;

void MdnsSectionEntry::SetType(SectionEntryType type) {
  switch (type) {
    case SectionEntryType::kA:
      type_ = 1;
      return;
    case SectionEntryType::kAAAA:
      type_ = 28;
      return;
    default:
      RTC_NOTREACHED();
  }
}

SectionEntryType MdnsSectionEntry::GetType() const {
  switch (type_) {
    case 1:
      return SectionEntryType::kA;
    case 28:
      return SectionEntryType::kAAAA;
    default:
      return SectionEntryType::kUnsupported;
  }
}

void MdnsSectionEntry::SetClass(SectionEntryClass cls) {
  switch (cls) {
    case SectionEntryClass::kIN:
      class_ = 1;
      return;
    default:
      RTC_NOTREACHED();
  }
}

SectionEntryClass MdnsSectionEntry::GetClass() const {
  switch (class_) {
    case 1:
      return SectionEntryClass::kIN;
    default:
      return SectionEntryClass::kUnsupported;
  }
}

MdnsQuestion::MdnsQuestion() = default;
MdnsQuestion::MdnsQuestion(const MdnsQuestion& other) = default;
MdnsQuestion::~MdnsQuestion() = default;

bool MdnsQuestion::Read(MessageBufferReader* buf) {
  if (!ReadDomainName(buf, &name_)) {
    RTC_LOG(LS_ERROR) << "Invalid name.";
    return false;
  }
  if (!buf->ReadUInt16(&type_) || !buf->ReadUInt16(&class_)) {
    RTC_LOG(LS_ERROR) << "Invalid type and class.";
    return false;
  }
  return true;
}

bool MdnsQuestion::Write(rtc::ByteBufferWriter* buf) const {
  WriteDomainName(buf, name_);
  buf->WriteUInt16(type_);
  buf->WriteUInt16(class_);
  return true;
}

void MdnsQuestion::SetUnicastResponse(bool should_unicast) {
  if (should_unicast) {
    class_ |= kMdnsQClassMaskUnicastResponse;
  } else {
    class_ &= ~kMdnsQClassMaskUnicastResponse;
  }
}

bool MdnsQuestion::ShouldUnicastResponse() const {
  return class_ & kMdnsQClassMaskUnicastResponse;
}

MdnsResourceRecord::MdnsResourceRecord() = default;
MdnsResourceRecord::MdnsResourceRecord(const MdnsResourceRecord& other) =
    default;
MdnsResourceRecord::~MdnsResourceRecord() = default;

bool MdnsResourceRecord::Read(MessageBufferReader* buf) {
  if (!ReadDomainName(buf, &name_)) {
    return false;
  }
  if (!buf->ReadUInt16(&type_) || !buf->ReadUInt16(&class_) ||
      !buf->ReadUInt32(&ttl_seconds_) || !buf->ReadUInt16(&rdlength_)) {
    return false;
  }

  switch (GetType()) {
    case SectionEntryType::kA:
      return ReadARData(buf);
    case SectionEntryType::kAAAA:
      return ReadQuadARData(buf);
    case SectionEntryType::kUnsupported:
      return false;
    default:
      RTC_NOTREACHED();
  }
  return false;
}
bool MdnsResourceRecord::ReadARData(MessageBufferReader* buf) {
  // A RDATA contains a 32-bit IPv4 address.
  return buf->ReadString(&rdata_, 4);
}

bool MdnsResourceRecord::ReadQuadARData(MessageBufferReader* buf) {
  // AAAA RDATA contains a 128-bit IPv6 address.
  return buf->ReadString(&rdata_, 16);
}

bool MdnsResourceRecord::Write(rtc::ByteBufferWriter* buf) const {
  WriteDomainName(buf, name_);
  buf->WriteUInt16(type_);
  buf->WriteUInt16(class_);
  buf->WriteUInt32(ttl_seconds_);
  buf->WriteUInt16(rdlength_);
  switch (GetType()) {
    case SectionEntryType::kA:
      WriteARData(buf);
      return true;
    case SectionEntryType::kAAAA:
      WriteQuadARData(buf);
      return true;
    case SectionEntryType::kUnsupported:
      return false;
    default:
      RTC_NOTREACHED();
  }
  return true;
}

void MdnsResourceRecord::WriteARData(rtc::ByteBufferWriter* buf) const {
  buf->WriteString(rdata_);
}

void MdnsResourceRecord::WriteQuadARData(rtc::ByteBufferWriter* buf) const {
  buf->WriteString(rdata_);
}

bool MdnsResourceRecord::SetIPAddressInRecordData(
    const rtc::IPAddress& address) {
  int af = address.family();
  if (af != AF_INET && af != AF_INET6) {
    return false;
  }
  char out[16] = {0};
  if (!rtc::inet_pton(af, address.ToString().c_str(), out)) {
    return false;
  }
  rdlength_ = (af == AF_INET) ? 4 : 16;
  rdata_ = std::string(out, rdlength_);
  return true;
}

bool MdnsResourceRecord::GetIPAddressFromRecordData(
    rtc::IPAddress* address) const {
  if (GetType() != SectionEntryType::kA &&
      GetType() != SectionEntryType::kAAAA) {
    return false;
  }
  if (rdata_.size() != 4 && rdata_.size() != 16) {
    return false;
  }
  char out[INET6_ADDRSTRLEN] = {0};
  int af = (GetType() == SectionEntryType::kA) ? AF_INET : AF_INET6;
  if (!rtc::inet_ntop(af, rdata_.data(), out, sizeof(out))) {
    return false;
  }
  return rtc::IPFromString(std::string(out), address);
}

MdnsMessage::MdnsMessage() = default;
MdnsMessage::~MdnsMessage() = default;

bool MdnsMessage::Read(MessageBufferReader* buf) {
  RTC_DCHECK_EQ(0u, buf->CurrentOffset());
  if (!header_.Read(buf)) {
    return false;
  }

  auto read_question = [&buf](std::vector<MdnsQuestion>* section,
                              uint16_t count) {
    section->resize(count);
    for (auto& question : (*section)) {
      if (!question.Read(buf)) {
        return false;
      }
    }
    return true;
  };
  auto read_rr = [&buf](std::vector<MdnsResourceRecord>* section,
                        uint16_t count) {
    section->resize(count);
    for (auto& rr : (*section)) {
      if (!rr.Read(buf)) {
        return false;
      }
    }
    return true;
  };

  if (!read_question(&question_section_, header_.qdcount) ||
      !read_rr(&answer_section_, header_.ancount) ||
      !read_rr(&authority_section_, header_.nscount) ||
      !read_rr(&additional_section_, header_.arcount)) {
    return false;
  }
  return true;
}

bool MdnsMessage::Write(rtc::ByteBufferWriter* buf) const {
  header_.Write(buf);

  auto write_rr = [&buf](const std::vector<MdnsResourceRecord>& section) {
    for (const auto& rr : section) {
      if (!rr.Write(buf)) {
        return false;
      }
    }
    return true;
  };

  for (const auto& question : question_section_) {
    if (!question.Write(buf)) {
      return false;
    }
  }
  if (!write_rr(answer_section_) || !write_rr(authority_section_) ||
      !write_rr(additional_section_)) {
    return false;
  }

  return true;
}

bool MdnsMessage::ShouldUnicastResponse() const {
  bool should_unicast = false;
  for (const auto& question : question_section_) {
    should_unicast |= question.ShouldUnicastResponse();
  }
  return should_unicast;
}

void MdnsMessage::AddQuestion(const MdnsQuestion& question) {
  question_section_.push_back(question);
  header_.qdcount = question_section_.size();
}

void MdnsMessage::AddAnswerRecord(const MdnsResourceRecord& answer) {
  answer_section_.push_back(answer);
  header_.ancount = answer_section_.size();
}

}  // namespace webrtc
