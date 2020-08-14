// Copyright 2019 The Abseil Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
#ifndef ABSL_STATUS_STATUS_H_
#define ABSL_STATUS_STATUS_H_

#include <iostream>
#include <string>

#include "absl/container/inlined_vector.h"
#include "absl/strings/cord.h"
#include "absl/types/optional.h"

namespace absl {
ABSL_NAMESPACE_BEGIN

enum class StatusCode : int {
  kOk = 0,
  kCancelled = 1,
  kUnknown = 2,
  kInvalidArgument = 3,
  kDeadlineExceeded = 4,
  kNotFound = 5,
  kAlreadyExists = 6,
  kPermissionDenied = 7,
  kResourceExhausted = 8,
  kFailedPrecondition = 9,
  kAborted = 10,
  kOutOfRange = 11,
  kUnimplemented = 12,
  kInternal = 13,
  kUnavailable = 14,
  kDataLoss = 15,
  kUnauthenticated = 16,
  kDoNotUseReservedForFutureExpansionUseDefaultInSwitchInstead_ = 20
};

// Returns the name for the status code, or "" if it is an unknown value.
std::string StatusCodeToString(StatusCode code);

// Streams StatusCodeToString(code) to `os`.
std::ostream& operator<<(std::ostream& os, StatusCode code);

namespace status_internal {

// Container for status payloads.
struct Payload {
  std::string type_url;
  absl::Cord payload;
};

using Payloads = absl::InlinedVector<Payload, 1>;

// Reference-counted representation of Status data.
struct StatusRep {
  std::atomic<int32_t> ref;
  absl::StatusCode code;
  std::string message;
  std::unique_ptr<status_internal::Payloads> payloads;
};

absl::StatusCode MapToLocalCode(int value);
}  // namespace status_internal

class ABSL_MUST_USE_RESULT Status final {
 public:
  // Creates an OK status with no message or payload.
  Status();

  // Create a status in the canonical error space with the specified code and
  // error message.  If `code == util::error::OK`, `msg` is ignored and an
  // object identical to an OK status is constructed.
  //
  // `msg` must be in UTF-8. The implementation may complain (e.g.,
  // by printing a warning) if it is not.
  Status(absl::StatusCode code, absl::string_view msg);

  Status(const Status&);
  Status& operator=(const Status& x);

  // Move operations.
  // The moved-from state is valid but unspecified.
  Status(Status&&) noexcept;
  Status& operator=(Status&&);

  ~Status();

  // If `this->ok()`, stores `new_status` into *this. If `!this->ok()`,
  // preserves the current data. May, in the future, augment the current status
  // with additional information about `new_status`.
  //
  // Convenient way of keeping track of the first error encountered.
  // Instead of:
  //   if (overall_status.ok()) overall_status = new_status
  // Use:
  //   overall_status.Update(new_status);
  //
  // Style guide exception for rvalue reference granted in CL 153567220.
  void Update(const Status& new_status);
  void Update(Status&& new_status);

  // Returns true if the Status is OK.
  ABSL_MUST_USE_RESULT bool ok() const;

  // Returns the (canonical) error code.
  absl::StatusCode code() const;

  // Returns the raw (canonical) error code which could be out of the range of
  // the local `absl::StatusCode` enum. NOTE: This should only be called when
  // converting to wire format. Use `code` for error handling.
  int raw_code() const;

  // Returns the error message.  Note: prefer ToString() for debug logging.
  // This message rarely describes the error code.  It is not unusual for the
  // error message to be the empty string.
  absl::string_view message() const;

  friend bool operator==(const Status&, const Status&);
  friend bool operator!=(const Status&, const Status&);

  // Returns a combination of the error code name, the message and the payloads.
  // You can expect the code name and the message to be substrings of the
  // result, and the payloads to be printed by the registered printer extensions
  // if they are recognized.
  // WARNING: Do not depend on the exact format of the result of `ToString()`
  // which is subject to change.
  std::string ToString() const;

  // Ignores any errors. This method does nothing except potentially suppress
  // complaints from any tools that are checking that errors are not dropped on
  // the floor.
  void IgnoreError() const;

  // Swap the contents of `a` with `b`
  friend void swap(Status& a, Status& b);

  // Payload management APIs

  // Type URL should be unique and follow the naming convention below:
  // The idea of type URL comes from `google.protobuf.Any`
  // (https://developers.google.com/protocol-buffers/docs/proto3#any). The
  // type URL should be globally unique and follow the format of URL
  // (https://en.wikipedia.org/wiki/URL). The default type URL for a given
  // protobuf message type is "type.googleapis.com/packagename.messagename". For
  // other custom wire formats, users should define the format of type URL in a
  // similar practice so as to minimize the chance of conflict between type
  // URLs. Users should make sure that the type URL can be mapped to a concrete
  // C++ type if they want to deserialize the payload and read it effectively.

  // Gets the payload based for `type_url` key, if it is present.
  absl::optional<absl::Cord> GetPayload(absl::string_view type_url) const;

  // Sets the payload for `type_url` key for a non-ok status, overwriting any
  // existing payload for `type_url`.
  //
  // NOTE: Does nothing if the Status is ok.
  void SetPayload(absl::string_view type_url, absl::Cord payload);

  // Erases the payload corresponding to the `type_url` key.  Returns true if
  // the payload was present.
  bool ErasePayload(absl::string_view type_url);

  // Iterates over the stored payloads and calls `visitor(type_key, payload)`
  // for each one.
  //
  // NOTE: The order of calls to `visitor` is not specified and may change at
  // any time.
  //
  // NOTE: Any mutation on the same 'Status' object during visitation is
  // forbidden and could result in undefined behavior.
  void ForEachPayload(
      const std::function<void(absl::string_view, const absl::Cord&)>& visitor)
      const;

 private:
  friend Status CancelledError();

  // Creates a status in the canonical error space with the specified
  // code, and an empty error message.
  explicit Status(absl::StatusCode code);

  static void UnrefNonInlined(uintptr_t rep);
  static void Ref(uintptr_t rep);
  static void Unref(uintptr_t rep);

  // REQUIRES: !ok()
  // Ensures rep_ is not shared with any other Status.
  void PrepareToModify();

  const status_internal::Payloads* GetPayloads() const;
  status_internal::Payloads* GetPayloads();

  // Takes ownership of payload.
  static uintptr_t NewRep(absl::StatusCode code, absl::string_view msg,
                          std::unique_ptr<status_internal::Payloads> payload);
  static bool EqualsSlow(const absl::Status& a, const absl::Status& b);

  // MSVC 14.0 limitation requires the const.
  static constexpr const char kMovedFromString[] =
      "Status accessed after move.";

  static const std::string* EmptyString();
  static const std::string* MovedFromString();

  // Returns whether rep contains an inlined representation.
  // See rep_ for details.
  static bool IsInlined(uintptr_t rep);

  // Indicates whether this Status was the rhs of a move operation. See rep_
  // for details.
  static bool IsMovedFrom(uintptr_t rep);
  static uintptr_t MovedFromRep();

  // Convert between error::Code and the inlined uintptr_t representation used
  // by rep_. See rep_ for details.
  static uintptr_t CodeToInlinedRep(absl::StatusCode code);
  static absl::StatusCode InlinedRepToCode(uintptr_t rep);

  // Converts between StatusRep* and the external uintptr_t representation used
  // by rep_. See rep_ for details.
  static uintptr_t PointerToRep(status_internal::StatusRep* r);
  static status_internal::StatusRep* RepToPointer(uintptr_t r);

  // Returns string for non-ok Status.
  std::string ToStringSlow() const;

  // Status supports two different representations.
  //  - When the low bit is off it is an inlined representation.
  //    It uses the canonical error space, no message or payload.
  //    The error code is (rep_ >> 2).
  //    The (rep_ & 2) bit is the "moved from" indicator, used in IsMovedFrom().
  //  - When the low bit is on it is an external representation.
  //    In this case all the data comes from a heap allocated Rep object.
  //    (rep_ - 1) is a status_internal::StatusRep* pointer to that structure.
  uintptr_t rep_;
};

// Returns an OK status, equivalent to a default constructed instance.
Status OkStatus();

// Prints a human-readable representation of `x` to `os`.
std::ostream& operator<<(std::ostream& os, const Status& x);

// -----------------------------------------------------------------
// Implementation details follow

inline Status::Status() : rep_(CodeToInlinedRep(absl::StatusCode::kOk)) {}

inline Status::Status(absl::StatusCode code) : rep_(CodeToInlinedRep(code)) {}

inline Status::Status(const Status& x) : rep_(x.rep_) { Ref(rep_); }

inline Status& Status::operator=(const Status& x) {
  uintptr_t old_rep = rep_;
  if (x.rep_ != old_rep) {
    Ref(x.rep_);
    rep_ = x.rep_;
    Unref(old_rep);
  }
  return *this;
}

inline Status::Status(Status&& x) noexcept : rep_(x.rep_) {
  x.rep_ = MovedFromRep();
}

inline Status& Status::operator=(Status&& x) {
  uintptr_t old_rep = rep_;
  rep_ = x.rep_;
  x.rep_ = MovedFromRep();
  Unref(old_rep);
  return *this;
}

inline void Status::Update(const Status& new_status) {
  if (ok()) {
    *this = new_status;
  }
}

inline void Status::Update(Status&& new_status) {
  if (ok()) {
    *this = std::move(new_status);
  }
}

inline Status::~Status() { Unref(rep_); }

inline bool Status::ok() const {
  return rep_ == CodeToInlinedRep(absl::StatusCode::kOk);
}

inline absl::string_view Status::message() const {
  return !IsInlined(rep_)
             ? RepToPointer(rep_)->message
             : (IsMovedFrom(rep_) ? absl::string_view(kMovedFromString)
                                  : absl::string_view());
}

inline bool operator==(const Status& lhs, const Status& rhs) {
  return lhs.rep_ == rhs.rep_ || Status::EqualsSlow(lhs, rhs);
}

inline bool operator!=(const Status& lhs, const Status& rhs) {
  return !(lhs == rhs);
}

inline std::string Status::ToString() const {
  return ok() ? "OK" : ToStringSlow();
}

inline void Status::IgnoreError() const {
  // no-op
}

inline void swap(absl::Status& a, absl::Status& b) {
  using std::swap;
  swap(a.rep_, b.rep_);
}

inline const status_internal::Payloads* Status::GetPayloads() const {
  return IsInlined(rep_) ? nullptr : RepToPointer(rep_)->payloads.get();
}

inline status_internal::Payloads* Status::GetPayloads() {
  return IsInlined(rep_) ? nullptr : RepToPointer(rep_)->payloads.get();
}

inline bool Status::IsInlined(uintptr_t rep) { return (rep & 1) == 0; }

inline bool Status::IsMovedFrom(uintptr_t rep) {
  return IsInlined(rep) && (rep & 2) != 0;
}

inline uintptr_t Status::MovedFromRep() {
  return CodeToInlinedRep(absl::StatusCode::kInternal) | 2;
}

inline uintptr_t Status::CodeToInlinedRep(absl::StatusCode code) {
  return static_cast<uintptr_t>(code) << 2;
}

inline absl::StatusCode Status::InlinedRepToCode(uintptr_t rep) {
  assert(IsInlined(rep));
  return static_cast<absl::StatusCode>(rep >> 2);
}

inline status_internal::StatusRep* Status::RepToPointer(uintptr_t rep) {
  assert(!IsInlined(rep));
  return reinterpret_cast<status_internal::StatusRep*>(rep - 1);
}

inline uintptr_t Status::PointerToRep(status_internal::StatusRep* rep) {
  return reinterpret_cast<uintptr_t>(rep) + 1;
}

inline void Status::Ref(uintptr_t rep) {
  if (!IsInlined(rep)) {
    RepToPointer(rep)->ref.fetch_add(1, std::memory_order_relaxed);
  }
}

inline void Status::Unref(uintptr_t rep) {
  if (!IsInlined(rep)) {
    UnrefNonInlined(rep);
  }
}

inline Status OkStatus() { return Status(); }

// Each of the functions below creates a Status object with a particular error
// code and the given message. The error code of the returned status object
// matches the name of the function.
Status AbortedError(absl::string_view message);
Status AlreadyExistsError(absl::string_view message);
Status CancelledError(absl::string_view message);
Status DataLossError(absl::string_view message);
Status DeadlineExceededError(absl::string_view message);
Status FailedPreconditionError(absl::string_view message);
Status InternalError(absl::string_view message);
Status InvalidArgumentError(absl::string_view message);
Status NotFoundError(absl::string_view message);
Status OutOfRangeError(absl::string_view message);
Status PermissionDeniedError(absl::string_view message);
Status ResourceExhaustedError(absl::string_view message);
Status UnauthenticatedError(absl::string_view message);
Status UnavailableError(absl::string_view message);
Status UnimplementedError(absl::string_view message);
Status UnknownError(absl::string_view message);

// Creates a `Status` object with the `absl::StatusCode::kCancelled` error code
// and an empty message. It is provided only for efficiency, given that
// message-less kCancelled errors are common in the infrastructure.
inline Status CancelledError() { return Status(absl::StatusCode::kCancelled); }

// Each of the functions below returns true if the given status matches the
// error code implied by the function's name.
ABSL_MUST_USE_RESULT bool IsAborted(const Status& status);
ABSL_MUST_USE_RESULT bool IsAlreadyExists(const Status& status);
ABSL_MUST_USE_RESULT bool IsCancelled(const Status& status);
ABSL_MUST_USE_RESULT bool IsDataLoss(const Status& status);
ABSL_MUST_USE_RESULT bool IsDeadlineExceeded(const Status& status);
ABSL_MUST_USE_RESULT bool IsFailedPrecondition(const Status& status);
ABSL_MUST_USE_RESULT bool IsInternal(const Status& status);
ABSL_MUST_USE_RESULT bool IsInvalidArgument(const Status& status);
ABSL_MUST_USE_RESULT bool IsNotFound(const Status& status);
ABSL_MUST_USE_RESULT bool IsOutOfRange(const Status& status);
ABSL_MUST_USE_RESULT bool IsPermissionDenied(const Status& status);
ABSL_MUST_USE_RESULT bool IsResourceExhausted(const Status& status);
ABSL_MUST_USE_RESULT bool IsUnauthenticated(const Status& status);
ABSL_MUST_USE_RESULT bool IsUnavailable(const Status& status);
ABSL_MUST_USE_RESULT bool IsUnimplemented(const Status& status);
ABSL_MUST_USE_RESULT bool IsUnknown(const Status& status);

ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_STATUS_STATUS_H_
