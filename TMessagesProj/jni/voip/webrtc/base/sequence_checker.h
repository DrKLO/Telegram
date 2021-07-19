// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_SEQUENCE_CHECKER_H_
#define BASE_SEQUENCE_CHECKER_H_

#include "base/compiler_specific.h"
#include "base/logging.h"
#include "base/sequence_checker_impl.h"
#include "base/strings/string_piece.h"
#include "build/build_config.h"

// SequenceChecker is a helper class used to help verify that some methods of a
// class are called sequentially (for thread-safety). It supports thread safety
// annotations (see base/thread_annotations.h).
//
// Use the macros below instead of the SequenceChecker directly so that the
// unused member doesn't result in an extra byte (four when padded) per
// instance in production.
//
// This class is much prefered to ThreadChecker for thread-safety checks.
// ThreadChecker should only be used for classes that are truly thread-affine
// (use thread-local-storage or a third-party API that does).
//
// Usage:
//   class MyClass {
//    public:
//     MyClass() {
//       // It's sometimes useful to detach on construction for objects that are
//       // constructed in one place and forever after used from another
//       // sequence.
//       DETACH_FROM_SEQUENCE(my_sequence_checker_);
//     }
//
//     ~MyClass() {
//       // SequenceChecker doesn't automatically check it's destroyed on origin
//       // sequence for the same reason it's sometimes detached in the
//       // constructor. It's okay to destroy off sequence if the owner
//       // otherwise knows usage on the associated sequence is done. If you're
//       // not detaching in the constructor, you probably want to explicitly
//       // check in the destructor.
//       DCHECK_CALLED_ON_VALID_SEQUENCE(my_sequence_checker_);
//     }
//     void MyMethod() {
//       DCHECK_CALLED_ON_VALID_SEQUENCE(my_sequence_checker_);
//       ... (do stuff) ...
//       MyOtherMethod();
//     }
//
//     void MyOtherMethod()
//         VALID_CONTEXT_REQUIRED(my_sequence_checker_) {
//       foo_ = 42;
//     }
//
//    private:
//      // GUARDED_BY_CONTEXT() enforces that this member is only
//      // accessed from a scope that invokes DCHECK_CALLED_ON_VALID_SEQUENCE()
//      // or from a function annotated with VALID_CONTEXT_REQUIRED(). A
//      // DCHECK build will not compile if the member is accessed and these
//      // conditions are not met.
//     int foo_ GUARDED_BY_CONTEXT(my_sequence_checker_);
//
//     SEQUENCE_CHECKER(my_sequence_checker_);
//   }

#define SEQUENCE_CHECKER_INTERNAL_CONCAT2(a, b) a##b
#define SEQUENCE_CHECKER_INTERNAL_CONCAT(a, b) \
  SEQUENCE_CHECKER_INTERNAL_CONCAT2(a, b)
#define SEQUENCE_CHECKER_INTERNAL_UID(prefix) \
  SEQUENCE_CHECKER_INTERNAL_CONCAT(prefix, __LINE__)

#if DCHECK_IS_ON()
#define SEQUENCE_CHECKER(name) base::SequenceChecker name
#define DCHECK_CALLED_ON_VALID_SEQUENCE(name, ...)                   \
  base::ScopedValidateSequenceChecker SEQUENCE_CHECKER_INTERNAL_UID( \
      scoped_validate_sequence_checker_)(name, ##__VA_ARGS__);
#define DETACH_FROM_SEQUENCE(name) (name).DetachFromSequence()
#else  // DCHECK_IS_ON()
#if __OBJC__ && defined(OS_IOS) && !HAS_FEATURE(objc_cxx_static_assert)
// TODO(thakis): Remove this branch once Xcode's clang has clang r356148.
#define SEQUENCE_CHECKER(name)
#else
#define SEQUENCE_CHECKER(name) static_assert(true, "")
#endif
#define DCHECK_CALLED_ON_VALID_SEQUENCE(name, ...) EAT_STREAM_PARAMETERS
#define DETACH_FROM_SEQUENCE(name)
#endif  // DCHECK_IS_ON()

namespace base {

// Do nothing implementation, for use in release mode.
//
// Note: You should almost always use the SequenceChecker class (through the
// above macros) to get the right version for your build configuration.
// Note: This is only a check, not a "lock". It is marked "LOCKABLE" only in
// order to support thread_annotations.h.
class LOCKABLE SequenceCheckerDoNothing {
 public:
  SequenceCheckerDoNothing() = default;

  // Moving between matching sequences is allowed to help classes with
  // SequenceCheckers that want a default move-construct/assign.
  SequenceCheckerDoNothing(SequenceCheckerDoNothing&& other) = default;
  SequenceCheckerDoNothing& operator=(SequenceCheckerDoNothing&& other) =
      default;

  bool CalledOnValidSequence() const WARN_UNUSED_RESULT { return true; }
  void DetachFromSequence() {}

 private:
  DISALLOW_COPY_AND_ASSIGN(SequenceCheckerDoNothing);
};

#if DCHECK_IS_ON()
class SequenceChecker : public SequenceCheckerImpl {
};
#else
class SequenceChecker : public SequenceCheckerDoNothing {
};
#endif  // DCHECK_IS_ON()

class SCOPED_LOCKABLE ScopedValidateSequenceChecker {
 public:
  explicit ScopedValidateSequenceChecker(const SequenceChecker& checker)
      EXCLUSIVE_LOCK_FUNCTION(checker) {
    DCHECK(checker.CalledOnValidSequence());
  }

  explicit ScopedValidateSequenceChecker(const SequenceChecker& checker,
                                         const StringPiece& msg)
      EXCLUSIVE_LOCK_FUNCTION(checker) {
    DCHECK(checker.CalledOnValidSequence()) << msg;
  }

  ~ScopedValidateSequenceChecker() UNLOCK_FUNCTION() {}

 private:
};

}  // namespace base

#endif  // BASE_SEQUENCE_CHECKER_H_
