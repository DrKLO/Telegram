// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/trace_event/blame_context.h"

#include "base/strings/stringprintf.h"
#include "base/trace_event/trace_event.h"
#include "base/trace_event/traced_value.h"

namespace base {
namespace trace_event {

BlameContext::BlameContext(const char* category,
                           const char* name,
                           const char* type,
                           const char* scope,
                           int64_t id,
                           const BlameContext* parent_context)
    : category_(category),
      name_(name),
      type_(type),
      scope_(scope),
      id_(id),
      parent_scope_(parent_context ? parent_context->scope() : nullptr),
      parent_id_(parent_context ? parent_context->id() : 0),
      category_group_enabled_(nullptr) {
  DCHECK(!parent_context || !std::strcmp(name_, parent_context->name()))
      << "Parent blame context must have the same name";
}

BlameContext::~BlameContext() {
  DCHECK(thread_checker_.CalledOnValidThread());
  DCHECK(WasInitialized());
  TRACE_EVENT_API_ADD_TRACE_EVENT(TRACE_EVENT_PHASE_DELETE_OBJECT,
                                  category_group_enabled_, type_, scope_, id_,
                                  nullptr, TRACE_EVENT_FLAG_HAS_ID);
  trace_event::TraceLog::GetInstance()->RemoveAsyncEnabledStateObserver(this);
}

void BlameContext::Enter() {
  DCHECK(WasInitialized());
  if (LIKELY(!*category_group_enabled_))
    return;
  TRACE_EVENT_API_ADD_TRACE_EVENT(TRACE_EVENT_PHASE_ENTER_CONTEXT,
                                  category_group_enabled_, name_, scope_, id_,
                                  nullptr, TRACE_EVENT_FLAG_HAS_ID);
}

void BlameContext::Leave() {
  DCHECK(WasInitialized());
  if (LIKELY(!*category_group_enabled_))
    return;
  TRACE_EVENT_API_ADD_TRACE_EVENT(TRACE_EVENT_PHASE_LEAVE_CONTEXT,
                                  category_group_enabled_, name_, scope_, id_,
                                  nullptr, TRACE_EVENT_FLAG_HAS_ID);
}

void BlameContext::TakeSnapshot() {
  DCHECK(thread_checker_.CalledOnValidThread());
  DCHECK(WasInitialized());
  if (LIKELY(!*category_group_enabled_))
    return;
  std::unique_ptr<trace_event::TracedValue> snapshot(
      new trace_event::TracedValue);
  AsValueInto(snapshot.get());
  TraceArguments args("snapshot", std::move(snapshot));
  TRACE_EVENT_API_ADD_TRACE_EVENT(TRACE_EVENT_PHASE_SNAPSHOT_OBJECT,
                                  category_group_enabled_, type_, scope_, id_,
                                  &args, TRACE_EVENT_FLAG_HAS_ID);
}

void BlameContext::OnTraceLogEnabled() {
  DCHECK(WasInitialized());
  TakeSnapshot();
}

void BlameContext::OnTraceLogDisabled() {}

void BlameContext::AsValueInto(trace_event::TracedValue* state) {
  DCHECK(WasInitialized());
  if (!parent_id_)
    return;
  state->BeginDictionary("parent");
  state->SetString("id_ref", StringPrintf("0x%" PRIx64, parent_id_));
  state->SetString("scope", parent_scope_);
  state->EndDictionary();
}

void BlameContext::Initialize() {
  DCHECK(thread_checker_.CalledOnValidThread());
  category_group_enabled_ =
      TRACE_EVENT_API_GET_CATEGORY_GROUP_ENABLED(category_);
  TRACE_EVENT_API_ADD_TRACE_EVENT(TRACE_EVENT_PHASE_CREATE_OBJECT,
                                  category_group_enabled_, type_, scope_, id_,
                                  nullptr, TRACE_EVENT_FLAG_HAS_ID);
  trace_event::TraceLog::GetInstance()->AddAsyncEnabledStateObserver(
      weak_factory_.GetWeakPtr());
  TakeSnapshot();
}

bool BlameContext::WasInitialized() const {
  return category_group_enabled_ != nullptr;
}

}  // namespace trace_event
}  // namespace base
