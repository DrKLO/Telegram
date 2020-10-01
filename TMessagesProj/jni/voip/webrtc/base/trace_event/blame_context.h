// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TRACE_EVENT_BLAME_CONTEXT_H_
#define BASE_TRACE_EVENT_BLAME_CONTEXT_H_

#include <inttypes.h>

#include "base/base_export.h"
#include "base/macros.h"
#include "base/threading/thread_checker.h"
#include "base/trace_event/trace_log.h"

namespace base {
namespace trace_event {
class TracedValue;
}

namespace trace_event {

// A blame context represents a logical unit to which we want to attribute
// different costs (e.g., CPU, network, or memory usage). An example of a blame
// context is an <iframe> element on a web page. Different subsystems can
// "enter" and "leave" blame contexts to indicate that they are doing work which
// should be accounted against this blame context.
//
// A blame context can optionally have a parent context, forming a blame context
// tree. When work is attributed to a particular blame context, it is considered
// to count against all of that context's children too. This is useful when work
// cannot be exactly attributed into a more specific context. For example,
// Javascript garbage collection generally needs to inspect all objects on a
// page instead looking at each <iframe> individually. In this case the work
// should be attributed to a blame context which is the parent of all <iframe>
// blame contexts.
class BASE_EXPORT BlameContext
    : public trace_event::TraceLog::AsyncEnabledStateObserver {
 public:
  // Construct a blame context belonging to the blame context tree |name|, using
  // the tracing category |category|, identified by |id| from the |scope|
  // namespace. |type| identifies the type of this object snapshot in the blame
  // context tree. |parent_context| is the parent of this blame context or
  // null. Note that all strings must have application lifetime.
  //
  // For example, a blame context which represents a specific <iframe> in a
  // browser frame tree could be specified with:
  //
  //   category="blink",
  //   name="FrameTree",
  //   type="IFrame",
  //   scope="IFrameIdentifier",
  //   id=1234.
  //
  // Each <iframe> blame context could have another <iframe> context as a
  // parent, or a top-level context which represents the entire browser:
  //
  //   category="blink",
  //   name="FrameTree",
  //   type="Browser",
  //   scope="BrowserIdentifier",
  //   id=1.
  //
  // Note that the |name| property is identical, signifying that both context
  // types are part of the same tree.
  //
  BlameContext(const char* category,
               const char* name,
               const char* type,
               const char* scope,
               int64_t id,
               const BlameContext* parent_context);
  ~BlameContext() override;

  // Initialize the blame context, automatically taking a snapshot if tracing is
  // enabled. Must be called before any other methods on this class.
  void Initialize();

  // Indicate that the current thread is now doing work which should count
  // against this blame context.  This function is allowed to be called in a
  // thread different from where the blame context was created; However, any
  // client doing that must be fully responsible for ensuring thready safety.
  void Enter();

  // Leave and stop doing work for a previously entered blame context. If
  // another blame context belonging to the same tree was entered prior to this
  // one, it becomes the active blame context for this thread again.  Similar
  // to Enter(), this function can be called in a thread different from where
  // the blame context was created, and the same requirement on thread safety
  // must be satisfied.
  void Leave();

  // Record a snapshot of the blame context. This is normally only needed if a
  // blame context subclass defines custom properties (see AsValueInto) and one
  // or more of those properties have changed.
  void TakeSnapshot();

  const char* category() const { return category_; }
  const char* name() const { return name_; }
  const char* type() const { return type_; }
  const char* scope() const { return scope_; }
  int64_t id() const { return id_; }

  // trace_event::TraceLog::EnabledStateObserver implementation:
  void OnTraceLogEnabled() override;
  void OnTraceLogDisabled() override;

 protected:
  // Serialize the properties of this blame context into |state|. Subclasses can
  // override this method to record additional properties (e.g, the URL for an
  // <iframe> blame context). Note that an overridden implementation must still
  // call this base method.
  virtual void AsValueInto(trace_event::TracedValue* state);

 private:
  bool WasInitialized() const;

  // The following string pointers have application lifetime.
  const char* category_;
  const char* name_;
  const char* type_;
  const char* scope_;
  const int64_t id_;

  const char* parent_scope_;
  const int64_t parent_id_;

  const unsigned char* category_group_enabled_;

  ThreadChecker thread_checker_;
  WeakPtrFactory<BlameContext> weak_factory_{this};

  DISALLOW_COPY_AND_ASSIGN(BlameContext);
};

}  // namespace trace_event
}  // namespace base

#endif  // BASE_TRACE_EVENT_BLAME_CONTEXT_H_
