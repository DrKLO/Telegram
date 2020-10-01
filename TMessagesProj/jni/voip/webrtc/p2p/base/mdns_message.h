/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_MDNS_MESSAGE_H_
#define P2P_BASE_MDNS_MESSAGE_H_

// This file contains classes to read and write mDNSs message defined in RFC
// 6762 and RFC 1025 (DNS messages). Note that it is recommended by RFC 6762 to
// use the name compression scheme defined in RFC 1035 whenever possible. We
// currently only implement the capability of reading compressed names in mDNS
// messages in MdnsMessage::Read(); however, the MdnsMessage::Write() does not
// support name compression yet.
//
// Fuzzer tests (test/fuzzers/mdns_parser_fuzzer.cc) MUST always be performed
// after changes made to this file.

#include <stdint.h>

#include <string>
#include <vector>

#include "rtc_base/byte_buffer.h"
#include "rtc_base/ip_address.h"
#include "rtc_base/message_buffer_reader.h"

namespace webrtc {

// We use "section entry" to denote either a question or a resource record.
//
// RFC 1035 Section 3.2.2.
enum class SectionEntryType {
  kA,
  kAAAA,
  // Only the above types are processed in the current implementation.
  kUnsupported,
};

// RFC 1035 Section 3.2.4.
enum class SectionEntryClass {
  kIN,
  kUnsupported,
};

// RFC 1035, Section 4.1.1.
class MdnsHeader final {
 public:
  bool Read(MessageBufferReader* buf);
  void Write(rtc::ByteBufferWriter* buf) const;

  void SetQueryOrResponse(bool is_query);
  bool IsQuery() const;
  void SetAuthoritative(bool is_authoritative);
  bool IsAuthoritative() const;

  uint16_t id = 0;
  uint16_t flags = 0;
  // Number of entries in the question section.
  uint16_t qdcount = 0;
  // Number of resource records in the answer section.
  uint16_t ancount = 0;
  // Number of name server resource records in the authority records section.
  uint16_t nscount = 0;
  // Number of resource records in the additional records section.
  uint16_t arcount = 0;
};

// Entries in each section after the header share a common structure. Note that
// this is not a concept defined in RFC 1035.
class MdnsSectionEntry {
 public:
  MdnsSectionEntry();
  MdnsSectionEntry(const MdnsSectionEntry& other);
  virtual ~MdnsSectionEntry();
  virtual bool Read(MessageBufferReader* buf) = 0;
  virtual bool Write(rtc::ByteBufferWriter* buf) const = 0;

  void SetName(const std::string& name) { name_ = name; }
  // Returns the fully qualified domain name in the section entry, i.e., QNAME
  // in a question or NAME in a resource record.
  std::string GetName() const { return name_; }

  void SetType(SectionEntryType type);
  SectionEntryType GetType() const;
  void SetClass(SectionEntryClass cls);
  SectionEntryClass GetClass() const;

 protected:
  std::string name_;  // Fully qualified domain name.
  uint16_t type_ = 0;
  uint16_t class_ = 0;
};

// RFC 1035, Section 4.1.2.
class MdnsQuestion final : public MdnsSectionEntry {
 public:
  MdnsQuestion();
  MdnsQuestion(const MdnsQuestion& other);
  ~MdnsQuestion() override;

  bool Read(MessageBufferReader* buf) override;
  bool Write(rtc::ByteBufferWriter* buf) const override;

  void SetUnicastResponse(bool should_unicast);
  bool ShouldUnicastResponse() const;
};

// RFC 1035, Section 4.1.3.
class MdnsResourceRecord final : public MdnsSectionEntry {
 public:
  MdnsResourceRecord();
  MdnsResourceRecord(const MdnsResourceRecord& other);
  ~MdnsResourceRecord() override;

  bool Read(MessageBufferReader* buf) override;
  bool Write(rtc::ByteBufferWriter* buf) const override;

  void SetTtlSeconds(uint32_t ttl_seconds) { ttl_seconds_ = ttl_seconds; }
  uint32_t GetTtlSeconds() const { return ttl_seconds_; }
  // Returns true if |address| is in the address family AF_INET or AF_INET6 and
  // |address| has a valid IPv4 or IPv6 address; false otherwise.
  bool SetIPAddressInRecordData(const rtc::IPAddress& address);
  // Returns true if the record is of type A or AAAA and the record has a valid
  // IPv4 or IPv6 address; false otherwise. Stores the valid IP in |address|.
  bool GetIPAddressFromRecordData(rtc::IPAddress* address) const;

 private:
  // The list of methods reading and writing rdata can grow as we support more
  // types of rdata.
  bool ReadARData(MessageBufferReader* buf);
  void WriteARData(rtc::ByteBufferWriter* buf) const;

  bool ReadQuadARData(MessageBufferReader* buf);
  void WriteQuadARData(rtc::ByteBufferWriter* buf) const;

  uint32_t ttl_seconds_ = 0;
  uint16_t rdlength_ = 0;
  std::string rdata_;
};

class MdnsMessage final {
 public:
  // RFC 1035, Section 4.1.
  enum class Section { kQuestion, kAnswer, kAuthority, kAdditional };

  MdnsMessage();
  ~MdnsMessage();
  // Reads the mDNS message in |buf| and populates the corresponding fields in
  // MdnsMessage.
  bool Read(MessageBufferReader* buf);
  // Write an mDNS message to |buf| based on the fields in MdnsMessage.
  //
  // TODO(qingsi): Implement name compression when writing mDNS messages.
  bool Write(rtc::ByteBufferWriter* buf) const;

  void SetId(uint16_t id) { header_.id = id; }
  uint16_t GetId() const { return header_.id; }

  void SetQueryOrResponse(bool is_query) {
    header_.SetQueryOrResponse(is_query);
  }
  bool IsQuery() const { return header_.IsQuery(); }

  void SetAuthoritative(bool is_authoritative) {
    header_.SetAuthoritative(is_authoritative);
  }
  bool IsAuthoritative() const { return header_.IsAuthoritative(); }

  // Returns true if the message is a query and the unicast response is
  // preferred. False otherwise.
  bool ShouldUnicastResponse() const;

  void AddQuestion(const MdnsQuestion& question);
  // TODO(qingsi): Implement AddXRecord for name server and additional records.
  void AddAnswerRecord(const MdnsResourceRecord& answer);

  const std::vector<MdnsQuestion>& question_section() const {
    return question_section_;
  }
  const std::vector<MdnsResourceRecord>& answer_section() const {
    return answer_section_;
  }
  const std::vector<MdnsResourceRecord>& authority_section() const {
    return authority_section_;
  }
  const std::vector<MdnsResourceRecord>& additional_section() const {
    return additional_section_;
  }

 private:
  MdnsHeader header_;
  std::vector<MdnsQuestion> question_section_;
  std::vector<MdnsResourceRecord> answer_section_;
  std::vector<MdnsResourceRecord> authority_section_;
  std::vector<MdnsResourceRecord> additional_section_;
};

}  // namespace webrtc

#endif  // P2P_BASE_MDNS_MESSAGE_H_
