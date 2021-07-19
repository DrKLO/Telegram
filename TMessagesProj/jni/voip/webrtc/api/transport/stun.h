/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_TRANSPORT_STUN_H_
#define API_TRANSPORT_STUN_H_

// This file contains classes for dealing with the STUN protocol, as specified
// in RFC 5389, and its descendants.

#include <stddef.h>
#include <stdint.h>

#include <functional>
#include <memory>
#include <string>
#include <vector>

#include "absl/strings/string_view.h"
#include "api/array_view.h"
#include "rtc_base/byte_buffer.h"
#include "rtc_base/ip_address.h"
#include "rtc_base/socket_address.h"

namespace cricket {

// These are the types of STUN messages defined in RFC 5389.
enum StunMessageType {
  STUN_BINDING_REQUEST = 0x0001,
  STUN_BINDING_INDICATION = 0x0011,
  STUN_BINDING_RESPONSE = 0x0101,
  STUN_BINDING_ERROR_RESPONSE = 0x0111,

  // Method 0x80, GOOG-PING is a variant of STUN BINDING
  // that is sent instead of a STUN BINDING if the binding
  // was identical to the one before.
  GOOG_PING_REQUEST = 0x200,
  GOOG_PING_RESPONSE = 0x300,
  GOOG_PING_ERROR_RESPONSE = 0x310,
};

// These are all known STUN attributes, defined in RFC 5389 and elsewhere.
// Next to each is the name of the class (T is StunTAttribute) that implements
// that type.
// RETRANSMIT_COUNT is the number of outstanding pings without a response at
// the time the packet is generated.
enum StunAttributeType {
  STUN_ATTR_MAPPED_ADDRESS = 0x0001,      // Address
  STUN_ATTR_USERNAME = 0x0006,            // ByteString
  STUN_ATTR_MESSAGE_INTEGRITY = 0x0008,   // ByteString, 20 bytes
  STUN_ATTR_ERROR_CODE = 0x0009,          // ErrorCode
  STUN_ATTR_UNKNOWN_ATTRIBUTES = 0x000a,  // UInt16List
  STUN_ATTR_REALM = 0x0014,               // ByteString
  STUN_ATTR_NONCE = 0x0015,               // ByteString
  STUN_ATTR_XOR_MAPPED_ADDRESS = 0x0020,  // XorAddress
  STUN_ATTR_SOFTWARE = 0x8022,            // ByteString
  STUN_ATTR_ALTERNATE_SERVER = 0x8023,    // Address
  STUN_ATTR_FINGERPRINT = 0x8028,         // UInt32
  STUN_ATTR_ORIGIN = 0x802F,              // ByteString
  STUN_ATTR_RETRANSMIT_COUNT = 0xFF00     // UInt32
};

// These are the types of the values associated with the attributes above.
// This allows us to perform some basic validation when reading or adding
// attributes. Note that these values are for our own use, and not defined in
// RFC 5389.
enum StunAttributeValueType {
  STUN_VALUE_UNKNOWN = 0,
  STUN_VALUE_ADDRESS = 1,
  STUN_VALUE_XOR_ADDRESS = 2,
  STUN_VALUE_UINT32 = 3,
  STUN_VALUE_UINT64 = 4,
  STUN_VALUE_BYTE_STRING = 5,
  STUN_VALUE_ERROR_CODE = 6,
  STUN_VALUE_UINT16_LIST = 7
};

// These are the types of STUN addresses defined in RFC 5389.
enum StunAddressFamily {
  // NB: UNDEF is not part of the STUN spec.
  STUN_ADDRESS_UNDEF = 0,
  STUN_ADDRESS_IPV4 = 1,
  STUN_ADDRESS_IPV6 = 2
};

// These are the types of STUN error codes defined in RFC 5389.
enum StunErrorCode {
  STUN_ERROR_TRY_ALTERNATE = 300,
  STUN_ERROR_BAD_REQUEST = 400,
  STUN_ERROR_UNAUTHORIZED = 401,
  STUN_ERROR_UNKNOWN_ATTRIBUTE = 420,
  STUN_ERROR_STALE_CREDENTIALS = 430,  // GICE only
  STUN_ERROR_STALE_NONCE = 438,
  STUN_ERROR_SERVER_ERROR = 500,
  STUN_ERROR_GLOBAL_FAILURE = 600
};

// Strings for the error codes above.
extern const char STUN_ERROR_REASON_TRY_ALTERNATE_SERVER[];
extern const char STUN_ERROR_REASON_BAD_REQUEST[];
extern const char STUN_ERROR_REASON_UNAUTHORIZED[];
extern const char STUN_ERROR_REASON_UNKNOWN_ATTRIBUTE[];
extern const char STUN_ERROR_REASON_STALE_CREDENTIALS[];
extern const char STUN_ERROR_REASON_STALE_NONCE[];
extern const char STUN_ERROR_REASON_SERVER_ERROR[];

// The mask used to determine whether a STUN message is a request/response etc.
const uint32_t kStunTypeMask = 0x0110;

// STUN Attribute header length.
const size_t kStunAttributeHeaderSize = 4;

// Following values correspond to RFC5389.
const size_t kStunHeaderSize = 20;
const size_t kStunTransactionIdOffset = 8;
const size_t kStunTransactionIdLength = 12;
const uint32_t kStunMagicCookie = 0x2112A442;
constexpr size_t kStunMagicCookieLength = sizeof(kStunMagicCookie);

// Following value corresponds to an earlier version of STUN from
// RFC3489.
const size_t kStunLegacyTransactionIdLength = 16;

// STUN Message Integrity HMAC length.
const size_t kStunMessageIntegritySize = 20;
// Size of STUN_ATTR_MESSAGE_INTEGRITY_32
const size_t kStunMessageIntegrity32Size = 4;

class StunAddressAttribute;
class StunAttribute;
class StunByteStringAttribute;
class StunErrorCodeAttribute;
class StunUInt16ListAttribute;
class StunUInt32Attribute;
class StunUInt64Attribute;
class StunXorAddressAttribute;

// Records a complete STUN/TURN message.  Each message consists of a type and
// any number of attributes.  Each attribute is parsed into an instance of an
// appropriate class (see above).  The Get* methods will return instances of
// that attribute class.
class StunMessage {
 public:
  StunMessage();
  virtual ~StunMessage();

  // The verification status of the message. This is checked on parsing,
  // or set by AddMessageIntegrity.
  enum class IntegrityStatus {
    kNotSet,
    kNoIntegrity,   // Message-integrity attribute missing
    kIntegrityOk,   // Message-integrity checked OK
    kIntegrityBad,  // Message-integrity verification failed
  };

  int type() const { return type_; }
  size_t length() const { return length_; }
  const std::string& transaction_id() const { return transaction_id_; }
  uint32_t reduced_transaction_id() const { return reduced_transaction_id_; }

  // Returns true if the message confirms to RFC3489 rather than
  // RFC5389. The main difference between the two versions of the STUN
  // protocol is the presence of the magic cookie and different length
  // of transaction ID. For outgoing packets the version of the protocol
  // is determined by the lengths of the transaction ID.
  bool IsLegacy() const;

  void SetType(int type) { type_ = static_cast<uint16_t>(type); }
  bool SetTransactionID(const std::string& str);

  // Get a list of all of the attribute types in the "comprehension required"
  // range that were not recognized.
  std::vector<uint16_t> GetNonComprehendedAttributes() const;

  // Gets the desired attribute value, or NULL if no such attribute type exists.
  const StunAddressAttribute* GetAddress(int type) const;
  const StunUInt32Attribute* GetUInt32(int type) const;
  const StunUInt64Attribute* GetUInt64(int type) const;
  const StunByteStringAttribute* GetByteString(int type) const;
  const StunUInt16ListAttribute* GetUInt16List(int type) const;

  // Gets these specific attribute values.
  const StunErrorCodeAttribute* GetErrorCode() const;
  // Returns the code inside the error code attribute, if present, and
  // STUN_ERROR_GLOBAL_FAILURE otherwise.
  int GetErrorCodeValue() const;
  const StunUInt16ListAttribute* GetUnknownAttributes() const;

  // Takes ownership of the specified attribute and adds it to the message.
  void AddAttribute(std::unique_ptr<StunAttribute> attr);

  // Remove the last occurrence of an attribute.
  std::unique_ptr<StunAttribute> RemoveAttribute(int type);

  // Remote all attributes and releases them.
  void ClearAttributes();

  // Validates that a STUN message has a correct MESSAGE-INTEGRITY value.
  // This uses the buffered raw-format message stored by Read().
  IntegrityStatus ValidateMessageIntegrity(const std::string& password);

  // Returns the current integrity status of the message.
  IntegrityStatus integrity() const { return integrity_; }

  // Shortcut for checking if integrity is verified.
  bool IntegrityOk() const {
    return integrity_ == IntegrityStatus::kIntegrityOk;
  }

  // Returns the password attribute used to set or check the integrity.
  // Can only be called after adding or checking the integrity.
  std::string password() const {
    RTC_DCHECK(integrity_ != IntegrityStatus::kNotSet);
    return password_;
  }

  // Adds a MESSAGE-INTEGRITY attribute that is valid for the current message.
  bool AddMessageIntegrity(const std::string& password);

  // Adds a STUN_ATTR_GOOG_MESSAGE_INTEGRITY_32 attribute that is valid for the
  // current message.
  bool AddMessageIntegrity32(absl::string_view password);

  // Verify that a buffer has stun magic cookie and one of the specified
  // methods. Note that it does not check for the existance of FINGERPRINT.
  static bool IsStunMethod(rtc::ArrayView<int> methods,
                           const char* data,
                           size_t size);

  // Verifies that a given buffer is STUN by checking for a correct FINGERPRINT.
  static bool ValidateFingerprint(const char* data, size_t size);

  // Adds a FINGERPRINT attribute that is valid for the current message.
  bool AddFingerprint();

  // Parses the STUN packet in the given buffer and records it here. The
  // return value indicates whether this was successful.
  bool Read(rtc::ByteBufferReader* buf);

  // Writes this object into a STUN packet. The return value indicates whether
  // this was successful.
  bool Write(rtc::ByteBufferWriter* buf) const;

  // Creates an empty message. Overridable by derived classes.
  virtual StunMessage* CreateNew() const;

  // Modify the stun magic cookie used for this STUN message.
  // This is used for testing.
  void SetStunMagicCookie(uint32_t val);

  // Contruct a copy of |this|.
  std::unique_ptr<StunMessage> Clone() const;

  // Check if the attributes of this StunMessage equals those of |other|
  // for all attributes that |attribute_type_mask| return true
  bool EqualAttributes(const StunMessage* other,
                       std::function<bool(int type)> attribute_type_mask) const;

  // Expose raw-buffer ValidateMessageIntegrity function for testing.
  static bool ValidateMessageIntegrityForTesting(const char* data,
                                                 size_t size,
                                                 const std::string& password) {
    return ValidateMessageIntegrity(data, size, password);
  }
  // Expose raw-buffer ValidateMessageIntegrity function for testing.
  static bool ValidateMessageIntegrity32ForTesting(
      const char* data,
      size_t size,
      const std::string& password) {
    return ValidateMessageIntegrity32(data, size, password);
  }
  // Validates that a STUN message in byte buffer form
  // has a correct MESSAGE-INTEGRITY value.
  // These functions are not recommended and will be deprecated; use
  // ValidateMessageIntegrity(password) on the parsed form instead.
  static bool ValidateMessageIntegrity(const char* data,
                                       size_t size,
                                       const std::string& password);
  static bool ValidateMessageIntegrity32(const char* data,
                                         size_t size,
                                         const std::string& password);

 protected:
  // Verifies that the given attribute is allowed for this message.
  virtual StunAttributeValueType GetAttributeValueType(int type) const;

  std::vector<std::unique_ptr<StunAttribute>> attrs_;

 private:
  StunAttribute* CreateAttribute(int type, size_t length) /* const*/;
  const StunAttribute* GetAttribute(int type) const;
  static bool IsValidTransactionId(const std::string& transaction_id);
  bool AddMessageIntegrityOfType(int mi_attr_type,
                                 size_t mi_attr_size,
                                 const char* key,
                                 size_t keylen);
  static bool ValidateMessageIntegrityOfType(int mi_attr_type,
                                             size_t mi_attr_size,
                                             const char* data,
                                             size_t size,
                                             const std::string& password);

  uint16_t type_;
  uint16_t length_;
  std::string transaction_id_;
  uint32_t reduced_transaction_id_;
  uint32_t stun_magic_cookie_;
  // The original buffer for messages created by Read().
  std::string buffer_;
  IntegrityStatus integrity_ = IntegrityStatus::kNotSet;
  std::string password_;
};

// Base class for all STUN/TURN attributes.
class StunAttribute {
 public:
  virtual ~StunAttribute() {}

  int type() const { return type_; }
  size_t length() const { return length_; }

  // Return the type of this attribute.
  virtual StunAttributeValueType value_type() const = 0;

  // Only XorAddressAttribute needs this so far.
  virtual void SetOwner(StunMessage* owner) {}

  // Reads the body (not the type or length) for this type of attribute from
  // the given buffer.  Return value is true if successful.
  virtual bool Read(rtc::ByteBufferReader* buf) = 0;

  // Writes the body (not the type or length) to the given buffer.  Return
  // value is true if successful.
  virtual bool Write(rtc::ByteBufferWriter* buf) const = 0;

  // Creates an attribute object with the given type and smallest length.
  static StunAttribute* Create(StunAttributeValueType value_type,
                               uint16_t type,
                               uint16_t length,
                               StunMessage* owner);
  // TODO(?): Allow these create functions to take parameters, to reduce
  // the amount of work callers need to do to initialize attributes.
  static std::unique_ptr<StunAddressAttribute> CreateAddress(uint16_t type);
  static std::unique_ptr<StunXorAddressAttribute> CreateXorAddress(
      uint16_t type);
  static std::unique_ptr<StunUInt32Attribute> CreateUInt32(uint16_t type);
  static std::unique_ptr<StunUInt64Attribute> CreateUInt64(uint16_t type);
  static std::unique_ptr<StunByteStringAttribute> CreateByteString(
      uint16_t type);
  static std::unique_ptr<StunUInt16ListAttribute> CreateUInt16ListAttribute(
      uint16_t type);
  static std::unique_ptr<StunErrorCodeAttribute> CreateErrorCode();
  static std::unique_ptr<StunUInt16ListAttribute> CreateUnknownAttributes();

 protected:
  StunAttribute(uint16_t type, uint16_t length);
  void SetLength(uint16_t length) { length_ = length; }
  void WritePadding(rtc::ByteBufferWriter* buf) const;
  void ConsumePadding(rtc::ByteBufferReader* buf) const;

 private:
  uint16_t type_;
  uint16_t length_;
};

// Implements STUN attributes that record an Internet address.
class StunAddressAttribute : public StunAttribute {
 public:
  static const uint16_t SIZE_UNDEF = 0;
  static const uint16_t SIZE_IP4 = 8;
  static const uint16_t SIZE_IP6 = 20;
  StunAddressAttribute(uint16_t type, const rtc::SocketAddress& addr);
  StunAddressAttribute(uint16_t type, uint16_t length);

  StunAttributeValueType value_type() const override;

  StunAddressFamily family() const {
    switch (address_.ipaddr().family()) {
      case AF_INET:
        return STUN_ADDRESS_IPV4;
      case AF_INET6:
        return STUN_ADDRESS_IPV6;
    }
    return STUN_ADDRESS_UNDEF;
  }

  const rtc::SocketAddress& GetAddress() const { return address_; }
  const rtc::IPAddress& ipaddr() const { return address_.ipaddr(); }
  uint16_t port() const { return address_.port(); }

  void SetAddress(const rtc::SocketAddress& addr) {
    address_ = addr;
    EnsureAddressLength();
  }
  void SetIP(const rtc::IPAddress& ip) {
    address_.SetIP(ip);
    EnsureAddressLength();
  }
  void SetPort(uint16_t port) { address_.SetPort(port); }

  bool Read(rtc::ByteBufferReader* buf) override;
  bool Write(rtc::ByteBufferWriter* buf) const override;

 private:
  void EnsureAddressLength() {
    switch (family()) {
      case STUN_ADDRESS_IPV4: {
        SetLength(SIZE_IP4);
        break;
      }
      case STUN_ADDRESS_IPV6: {
        SetLength(SIZE_IP6);
        break;
      }
      default: {
        SetLength(SIZE_UNDEF);
        break;
      }
    }
  }
  rtc::SocketAddress address_;
};

// Implements STUN attributes that record an Internet address. When encoded
// in a STUN message, the address contained in this attribute is XORed with the
// transaction ID of the message.
class StunXorAddressAttribute : public StunAddressAttribute {
 public:
  StunXorAddressAttribute(uint16_t type, const rtc::SocketAddress& addr);
  StunXorAddressAttribute(uint16_t type, uint16_t length, StunMessage* owner);

  StunAttributeValueType value_type() const override;
  void SetOwner(StunMessage* owner) override;
  bool Read(rtc::ByteBufferReader* buf) override;
  bool Write(rtc::ByteBufferWriter* buf) const override;

 private:
  rtc::IPAddress GetXoredIP() const;
  StunMessage* owner_;
};

// Implements STUN attributes that record a 32-bit integer.
class StunUInt32Attribute : public StunAttribute {
 public:
  static const uint16_t SIZE = 4;
  StunUInt32Attribute(uint16_t type, uint32_t value);
  explicit StunUInt32Attribute(uint16_t type);

  StunAttributeValueType value_type() const override;

  uint32_t value() const { return bits_; }
  void SetValue(uint32_t bits) { bits_ = bits; }

  bool GetBit(size_t index) const;
  void SetBit(size_t index, bool value);

  bool Read(rtc::ByteBufferReader* buf) override;
  bool Write(rtc::ByteBufferWriter* buf) const override;

 private:
  uint32_t bits_;
};

class StunUInt64Attribute : public StunAttribute {
 public:
  static const uint16_t SIZE = 8;
  StunUInt64Attribute(uint16_t type, uint64_t value);
  explicit StunUInt64Attribute(uint16_t type);

  StunAttributeValueType value_type() const override;

  uint64_t value() const { return bits_; }
  void SetValue(uint64_t bits) { bits_ = bits; }

  bool Read(rtc::ByteBufferReader* buf) override;
  bool Write(rtc::ByteBufferWriter* buf) const override;

 private:
  uint64_t bits_;
};

// Implements STUN attributes that record an arbitrary byte string.
class StunByteStringAttribute : public StunAttribute {
 public:
  explicit StunByteStringAttribute(uint16_t type);
  StunByteStringAttribute(uint16_t type, const std::string& str);
  StunByteStringAttribute(uint16_t type, const void* bytes, size_t length);
  StunByteStringAttribute(uint16_t type, uint16_t length);
  ~StunByteStringAttribute() override;

  StunAttributeValueType value_type() const override;

  const char* bytes() const { return bytes_; }
  std::string GetString() const { return std::string(bytes_, length()); }

  void CopyBytes(const char* bytes);  // uses strlen
  void CopyBytes(const void* bytes, size_t length);

  uint8_t GetByte(size_t index) const;
  void SetByte(size_t index, uint8_t value);

  bool Read(rtc::ByteBufferReader* buf) override;
  bool Write(rtc::ByteBufferWriter* buf) const override;

 private:
  void SetBytes(char* bytes, size_t length);

  char* bytes_;
};

// Implements STUN attributes that record an error code.
class StunErrorCodeAttribute : public StunAttribute {
 public:
  static const uint16_t MIN_SIZE;
  StunErrorCodeAttribute(uint16_t type, int code, const std::string& reason);
  StunErrorCodeAttribute(uint16_t type, uint16_t length);
  ~StunErrorCodeAttribute() override;

  StunAttributeValueType value_type() const override;

  // The combined error and class, e.g. 0x400.
  int code() const;
  void SetCode(int code);

  // The individual error components.
  int eclass() const { return class_; }
  int number() const { return number_; }
  const std::string& reason() const { return reason_; }
  void SetClass(uint8_t eclass) { class_ = eclass; }
  void SetNumber(uint8_t number) { number_ = number; }
  void SetReason(const std::string& reason);

  bool Read(rtc::ByteBufferReader* buf) override;
  bool Write(rtc::ByteBufferWriter* buf) const override;

 private:
  uint8_t class_;
  uint8_t number_;
  std::string reason_;
};

// Implements STUN attributes that record a list of attribute names.
class StunUInt16ListAttribute : public StunAttribute {
 public:
  StunUInt16ListAttribute(uint16_t type, uint16_t length);
  ~StunUInt16ListAttribute() override;

  StunAttributeValueType value_type() const override;

  size_t Size() const;
  uint16_t GetType(int index) const;
  void SetType(int index, uint16_t value);
  void AddType(uint16_t value);
  void AddTypeAtIndex(uint16_t index, uint16_t value);

  bool Read(rtc::ByteBufferReader* buf) override;
  bool Write(rtc::ByteBufferWriter* buf) const override;

 private:
  std::vector<uint16_t>* attr_types_;
};

// Return a string e.g "STUN BINDING request".
std::string StunMethodToString(int msg_type);

// Returns the (successful) response type for the given request type.
// Returns -1 if |request_type| is not a valid request type.
int GetStunSuccessResponseType(int request_type);

// Returns the error response type for the given request type.
// Returns -1 if |request_type| is not a valid request type.
int GetStunErrorResponseType(int request_type);

// Returns whether a given message is a request type.
bool IsStunRequestType(int msg_type);

// Returns whether a given message is an indication type.
bool IsStunIndicationType(int msg_type);

// Returns whether a given response is a success type.
bool IsStunSuccessResponseType(int msg_type);

// Returns whether a given response is an error type.
bool IsStunErrorResponseType(int msg_type);

// Computes the STUN long-term credential hash.
bool ComputeStunCredentialHash(const std::string& username,
                               const std::string& realm,
                               const std::string& password,
                               std::string* hash);

// Make a copy af |attribute| and return a new StunAttribute.
//   This is useful if you don't care about what kind of attribute you
//   are handling.
//
// The implementation copies by calling Write() followed by Read().
//
// If |tmp_buffer| is supplied this buffer will be used, otherwise
// a buffer will created in the method.
std::unique_ptr<StunAttribute> CopyStunAttribute(
    const StunAttribute& attribute,
    rtc::ByteBufferWriter* tmp_buffer_ptr = 0);

// TODO(?): Move the TURN/ICE stuff below out to separate files.
extern const char TURN_MAGIC_COOKIE_VALUE[4];

// "GTURN" STUN methods.
// TODO(?): Rename these methods to GTURN_ to make it clear they aren't
// part of standard STUN/TURN.
enum RelayMessageType {
  // For now, using the same defs from TurnMessageType below.
  // STUN_ALLOCATE_REQUEST              = 0x0003,
  // STUN_ALLOCATE_RESPONSE             = 0x0103,
  // STUN_ALLOCATE_ERROR_RESPONSE       = 0x0113,
  STUN_SEND_REQUEST = 0x0004,
  STUN_SEND_RESPONSE = 0x0104,
  STUN_SEND_ERROR_RESPONSE = 0x0114,
  STUN_DATA_INDICATION = 0x0115,
};

// "GTURN"-specific STUN attributes.
// TODO(?): Rename these attributes to GTURN_ to avoid conflicts.
enum RelayAttributeType {
  STUN_ATTR_LIFETIME = 0x000d,             // UInt32
  STUN_ATTR_MAGIC_COOKIE = 0x000f,         // ByteString, 4 bytes
  STUN_ATTR_BANDWIDTH = 0x0010,            // UInt32
  STUN_ATTR_DESTINATION_ADDRESS = 0x0011,  // Address
  STUN_ATTR_SOURCE_ADDRESS2 = 0x0012,      // Address
  STUN_ATTR_DATA = 0x0013,                 // ByteString
  STUN_ATTR_OPTIONS = 0x8001,              // UInt32
};

// A "GTURN" STUN message.
class RelayMessage : public StunMessage {
 protected:
  StunAttributeValueType GetAttributeValueType(int type) const override;
  StunMessage* CreateNew() const override;
};

// Defined in TURN RFC 5766.
enum TurnMessageType {
  STUN_ALLOCATE_REQUEST = 0x0003,
  STUN_ALLOCATE_RESPONSE = 0x0103,
  STUN_ALLOCATE_ERROR_RESPONSE = 0x0113,
  TURN_REFRESH_REQUEST = 0x0004,
  TURN_REFRESH_RESPONSE = 0x0104,
  TURN_REFRESH_ERROR_RESPONSE = 0x0114,
  TURN_SEND_INDICATION = 0x0016,
  TURN_DATA_INDICATION = 0x0017,
  TURN_CREATE_PERMISSION_REQUEST = 0x0008,
  TURN_CREATE_PERMISSION_RESPONSE = 0x0108,
  TURN_CREATE_PERMISSION_ERROR_RESPONSE = 0x0118,
  TURN_CHANNEL_BIND_REQUEST = 0x0009,
  TURN_CHANNEL_BIND_RESPONSE = 0x0109,
  TURN_CHANNEL_BIND_ERROR_RESPONSE = 0x0119,
};

enum TurnAttributeType {
  STUN_ATTR_CHANNEL_NUMBER = 0x000C,    // UInt32
  STUN_ATTR_TURN_LIFETIME = 0x000d,     // UInt32
  STUN_ATTR_XOR_PEER_ADDRESS = 0x0012,  // XorAddress
  // TODO(mallinath) - Uncomment after RelayAttributes are renamed.
  // STUN_ATTR_DATA                     = 0x0013,  // ByteString
  STUN_ATTR_XOR_RELAYED_ADDRESS = 0x0016,  // XorAddress
  STUN_ATTR_EVEN_PORT = 0x0018,            // ByteString, 1 byte.
  STUN_ATTR_REQUESTED_TRANSPORT = 0x0019,  // UInt32
  STUN_ATTR_DONT_FRAGMENT = 0x001A,        // No content, Length = 0
  STUN_ATTR_RESERVATION_TOKEN = 0x0022,    // ByteString, 8 bytes.
  // TODO(mallinath) - Rename STUN_ATTR_TURN_LIFETIME to STUN_ATTR_LIFETIME and
  // STUN_ATTR_TURN_DATA to STUN_ATTR_DATA. Also rename RelayMessage attributes
  // by appending G to attribute name.
};

// RFC 5766-defined errors.
enum TurnErrorType {
  STUN_ERROR_FORBIDDEN = 403,
  STUN_ERROR_ALLOCATION_MISMATCH = 437,
  STUN_ERROR_WRONG_CREDENTIALS = 441,
  STUN_ERROR_UNSUPPORTED_PROTOCOL = 442
};

extern const int SERVER_NOT_REACHABLE_ERROR;

extern const char STUN_ERROR_REASON_FORBIDDEN[];
extern const char STUN_ERROR_REASON_ALLOCATION_MISMATCH[];
extern const char STUN_ERROR_REASON_WRONG_CREDENTIALS[];
extern const char STUN_ERROR_REASON_UNSUPPORTED_PROTOCOL[];
class TurnMessage : public StunMessage {
 protected:
  StunAttributeValueType GetAttributeValueType(int type) const override;
  StunMessage* CreateNew() const override;
};

enum IceAttributeType {
  // RFC 5245 ICE STUN attributes.
  STUN_ATTR_PRIORITY = 0x0024,         // UInt32
  STUN_ATTR_USE_CANDIDATE = 0x0025,    // No content, Length = 0
  STUN_ATTR_ICE_CONTROLLED = 0x8029,   // UInt64
  STUN_ATTR_ICE_CONTROLLING = 0x802A,  // UInt64
  // The following attributes are in the comprehension-optional range
  // (0xC000-0xFFFF) and are not registered with IANA. These STUN attributes are
  // intended for ICE and should NOT be used in generic use cases of STUN
  // messages.
  //
  // Note that the value 0xC001 has already been assigned by IANA to
  // ENF-FLOW-DESCRIPTION
  // (https://www.iana.org/assignments/stun-parameters/stun-parameters.xml).
  STUN_ATTR_NOMINATION = 0xC001,  // UInt32
  // UInt32. The higher 16 bits are the network ID. The lower 16 bits are the
  // network cost.
  STUN_ATTR_GOOG_NETWORK_INFO = 0xC057,
  // Experimental: Transaction ID of the last connectivity check received.
  STUN_ATTR_GOOG_LAST_ICE_CHECK_RECEIVED = 0xC058,
  // Uint16List. Miscellaneous attributes for future extension.
  STUN_ATTR_GOOG_MISC_INFO = 0xC059,
  // Obsolete.
  STUN_ATTR_GOOG_OBSOLETE_1 = 0xC05A,
  STUN_ATTR_GOOG_CONNECTION_ID = 0xC05B,  // Not yet implemented.
  STUN_ATTR_GOOG_DELTA = 0xC05C,          // Not yet implemented.
  STUN_ATTR_GOOG_DELTA_ACK = 0xC05D,      // Not yet implemented.
  // MESSAGE-INTEGRITY truncated to 32-bit.
  STUN_ATTR_GOOG_MESSAGE_INTEGRITY_32 = 0xC060,
};

// When adding new attributes to STUN_ATTR_GOOG_MISC_INFO
// (which is a list of uint16_t), append the indices of these attributes below
// and do NOT change the existing indices. The indices of attributes must be
// consistent with those used in ConnectionRequest::Prepare when forming a STUN
// message for the ICE connectivity check, and they are used when parsing a
// received STUN message.
enum class IceGoogMiscInfoBindingRequestAttributeIndex {
  SUPPORT_GOOG_PING_VERSION = 0,
};

enum class IceGoogMiscInfoBindingResponseAttributeIndex {
  SUPPORT_GOOG_PING_VERSION = 0,
};

// RFC 5245-defined errors.
enum IceErrorCode {
  STUN_ERROR_ROLE_CONFLICT = 487,
};
extern const char STUN_ERROR_REASON_ROLE_CONFLICT[];

// A RFC 5245 ICE STUN message.
class IceMessage : public StunMessage {
 protected:
  StunAttributeValueType GetAttributeValueType(int type) const override;
  StunMessage* CreateNew() const override;
};

}  // namespace cricket

#endif  // API_TRANSPORT_STUN_H_
