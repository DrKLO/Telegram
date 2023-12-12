/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/adaptation/resource_adaptation_processor.h"

#include <algorithm>
#include <string>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/strings/string_view.h"
#include "api/sequence_checker.h"
#include "api/video/video_adaptation_counters.h"
#include "call/adaptation/video_stream_adapter.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"

namespace webrtc {

ResourceAdaptationProcessor::ResourceListenerDelegate::ResourceListenerDelegate(
    ResourceAdaptationProcessor* processor)
    : task_queue_(TaskQueueBase::Current()), processor_(processor) {
  RTC_DCHECK(task_queue_);
}

void ResourceAdaptationProcessor::ResourceListenerDelegate::
    OnProcessorDestroyed() {
  RTC_DCHECK_RUN_ON(task_queue_);
  processor_ = nullptr;
}

void ResourceAdaptationProcessor::ResourceListenerDelegate::
    OnResourceUsageStateMeasured(rtc::scoped_refptr<Resource> resource,
                                 ResourceUsageState usage_state) {
  if (!task_queue_->IsCurrent()) {
    task_queue_->PostTask(
        [this_ref = rtc::scoped_refptr<ResourceListenerDelegate>(this),
         resource, usage_state] {
          this_ref->OnResourceUsageStateMeasured(resource, usage_state);
        });
    return;
  }
  RTC_DCHECK_RUN_ON(task_queue_);
  if (processor_) {
    processor_->OnResourceUsageStateMeasured(resource, usage_state);
  }
}

ResourceAdaptationProcessor::MitigationResultAndLogMessage::
    MitigationResultAndLogMessage()
    : result(MitigationResult::kAdaptationApplied), message() {}

ResourceAdaptationProcessor::MitigationResultAndLogMessage::
    MitigationResultAndLogMessage(MitigationResult result,
                                  absl::string_view message)
    : result(result), message(message) {}

ResourceAdaptationProcessor::ResourceAdaptationProcessor(
    VideoStreamAdapter* stream_adapter)
    : task_queue_(TaskQueueBase::Current()),
      resource_listener_delegate_(
          rtc::make_ref_counted<ResourceListenerDelegate>(this)),
      resources_(),
      stream_adapter_(stream_adapter),
      last_reported_source_restrictions_(),
      previous_mitigation_results_() {
  RTC_DCHECK(task_queue_);
  stream_adapter_->AddRestrictionsListener(this);
}

ResourceAdaptationProcessor::~ResourceAdaptationProcessor() {
  RTC_DCHECK_RUN_ON(task_queue_);
  RTC_DCHECK(resources_.empty())
      << "There are resource(s) attached to a ResourceAdaptationProcessor "
      << "being destroyed.";
  stream_adapter_->RemoveRestrictionsListener(this);
  resource_listener_delegate_->OnProcessorDestroyed();
}

void ResourceAdaptationProcessor::AddResourceLimitationsListener(
    ResourceLimitationsListener* limitations_listener) {
  RTC_DCHECK_RUN_ON(task_queue_);
  RTC_DCHECK(std::find(resource_limitations_listeners_.begin(),
                       resource_limitations_listeners_.end(),
                       limitations_listener) ==
             resource_limitations_listeners_.end());
  resource_limitations_listeners_.push_back(limitations_listener);
}

void ResourceAdaptationProcessor::RemoveResourceLimitationsListener(
    ResourceLimitationsListener* limitations_listener) {
  RTC_DCHECK_RUN_ON(task_queue_);
  auto it =
      std::find(resource_limitations_listeners_.begin(),
                resource_limitations_listeners_.end(), limitations_listener);
  RTC_DCHECK(it != resource_limitations_listeners_.end());
  resource_limitations_listeners_.erase(it);
}

void ResourceAdaptationProcessor::AddResource(
    rtc::scoped_refptr<Resource> resource) {
  RTC_DCHECK(resource);
  {
    MutexLock crit(&resources_lock_);
    RTC_DCHECK(absl::c_find(resources_, resource) == resources_.end())
        << "Resource \"" << resource->Name() << "\" was already registered.";
    resources_.push_back(resource);
  }
  resource->SetResourceListener(resource_listener_delegate_.get());
  RTC_LOG(LS_INFO) << "Registered resource \"" << resource->Name() << "\".";
}

std::vector<rtc::scoped_refptr<Resource>>
ResourceAdaptationProcessor::GetResources() const {
  MutexLock crit(&resources_lock_);
  return resources_;
}

void ResourceAdaptationProcessor::RemoveResource(
    rtc::scoped_refptr<Resource> resource) {
  RTC_DCHECK(resource);
  RTC_LOG(LS_INFO) << "Removing resource \"" << resource->Name() << "\".";
  resource->SetResourceListener(nullptr);
  {
    MutexLock crit(&resources_lock_);
    auto it = absl::c_find(resources_, resource);
    RTC_DCHECK(it != resources_.end()) << "Resource \"" << resource->Name()
                                       << "\" was not a registered resource.";
    resources_.erase(it);
  }
  RemoveLimitationsImposedByResource(std::move(resource));
}

void ResourceAdaptationProcessor::RemoveLimitationsImposedByResource(
    rtc::scoped_refptr<Resource> resource) {
  if (!task_queue_->IsCurrent()) {
    task_queue_->PostTask(
        [this, resource]() { RemoveLimitationsImposedByResource(resource); });
    return;
  }
  RTC_DCHECK_RUN_ON(task_queue_);
  auto resource_adaptation_limits =
      adaptation_limits_by_resources_.find(resource);
  if (resource_adaptation_limits != adaptation_limits_by_resources_.end()) {
    VideoStreamAdapter::RestrictionsWithCounters adaptation_limits =
        resource_adaptation_limits->second;
    adaptation_limits_by_resources_.erase(resource_adaptation_limits);
    if (adaptation_limits_by_resources_.empty()) {
      // Only the resource being removed was adapted so clear restrictions.
      stream_adapter_->ClearRestrictions();
      return;
    }

    VideoStreamAdapter::RestrictionsWithCounters most_limited =
        FindMostLimitedResources().second;

    if (adaptation_limits.counters.Total() <= most_limited.counters.Total()) {
      // The removed limitations were less limited than the most limited
      // resource. Don't change the current restrictions.
      return;
    }

    // Apply the new most limited resource as the next restrictions.
    Adaptation adapt_to = stream_adapter_->GetAdaptationTo(
        most_limited.counters, most_limited.restrictions);
    RTC_DCHECK_EQ(adapt_to.status(), Adaptation::Status::kValid);
    stream_adapter_->ApplyAdaptation(adapt_to, nullptr);

    RTC_LOG(LS_INFO)
        << "Most limited resource removed. Restoring restrictions to "
           "next most limited restrictions: "
        << most_limited.restrictions.ToString() << " with counters "
        << most_limited.counters.ToString();
  }
}

void ResourceAdaptationProcessor::OnResourceUsageStateMeasured(
    rtc::scoped_refptr<Resource> resource,
    ResourceUsageState usage_state) {
  RTC_DCHECK_RUN_ON(task_queue_);
  RTC_DCHECK(resource);
  // `resource` could have been removed after signalling.
  {
    MutexLock crit(&resources_lock_);
    if (absl::c_find(resources_, resource) == resources_.end()) {
      RTC_LOG(LS_INFO) << "Ignoring signal from removed resource \""
                       << resource->Name() << "\".";
      return;
    }
  }
  MitigationResultAndLogMessage result_and_message;
  switch (usage_state) {
    case ResourceUsageState::kOveruse:
      result_and_message = OnResourceOveruse(resource);
      break;
    case ResourceUsageState::kUnderuse:
      result_and_message = OnResourceUnderuse(resource);
      break;
  }
  // Maybe log the result of the operation.
  auto it = previous_mitigation_results_.find(resource.get());
  if (it != previous_mitigation_results_.end() &&
      it->second == result_and_message.result) {
    // This resource has previously reported the same result and we haven't
    // successfully adapted since - don't log to avoid spam.
    return;
  }
  RTC_LOG(LS_INFO) << "Resource \"" << resource->Name() << "\" signalled "
                   << ResourceUsageStateToString(usage_state) << ". "
                   << result_and_message.message;
  if (result_and_message.result == MitigationResult::kAdaptationApplied) {
    previous_mitigation_results_.clear();
  } else {
    previous_mitigation_results_.insert(
        std::make_pair(resource.get(), result_and_message.result));
  }
}

ResourceAdaptationProcessor::MitigationResultAndLogMessage
ResourceAdaptationProcessor::OnResourceUnderuse(
    rtc::scoped_refptr<Resource> reason_resource) {
  RTC_DCHECK_RUN_ON(task_queue_);
  // How can this stream be adapted up?
  Adaptation adaptation = stream_adapter_->GetAdaptationUp();
  if (adaptation.status() != Adaptation::Status::kValid) {
    rtc::StringBuilder message;
    message << "Not adapting up because VideoStreamAdapter returned "
            << Adaptation::StatusToString(adaptation.status());
    return MitigationResultAndLogMessage(MitigationResult::kRejectedByAdapter,
                                         message.Release());
  }
  // Check that resource is most limited.
  std::vector<rtc::scoped_refptr<Resource>> most_limited_resources;
  VideoStreamAdapter::RestrictionsWithCounters most_limited_restrictions;
  std::tie(most_limited_resources, most_limited_restrictions) =
      FindMostLimitedResources();

  // If the most restricted resource is less limited than current restrictions
  // then proceed with adapting up.
  if (!most_limited_resources.empty() &&
      most_limited_restrictions.counters.Total() >=
          stream_adapter_->adaptation_counters().Total()) {
    // If `reason_resource` is not one of the most limiting resources then abort
    // adaptation.
    if (absl::c_find(most_limited_resources, reason_resource) ==
        most_limited_resources.end()) {
      rtc::StringBuilder message;
      message << "Resource \"" << reason_resource->Name()
              << "\" was not the most limited resource.";
      return MitigationResultAndLogMessage(
          MitigationResult::kNotMostLimitedResource, message.Release());
    }

    if (most_limited_resources.size() > 1) {
      // If there are multiple most limited resources, all must signal underuse
      // before the adaptation is applied.
      UpdateResourceLimitations(reason_resource, adaptation.restrictions(),
                                adaptation.counters());
      rtc::StringBuilder message;
      message << "Resource \"" << reason_resource->Name()
              << "\" was not the only most limited resource.";
      return MitigationResultAndLogMessage(
          MitigationResult::kSharedMostLimitedResource, message.Release());
    }
  }
  // Apply adaptation.
  stream_adapter_->ApplyAdaptation(adaptation, reason_resource);
  rtc::StringBuilder message;
  message << "Adapted up successfully. Unfiltered adaptations: "
          << stream_adapter_->adaptation_counters().ToString();
  return MitigationResultAndLogMessage(MitigationResult::kAdaptationApplied,
                                       message.Release());
}

ResourceAdaptationProcessor::MitigationResultAndLogMessage
ResourceAdaptationProcessor::OnResourceOveruse(
    rtc::scoped_refptr<Resource> reason_resource) {
  RTC_DCHECK_RUN_ON(task_queue_);
  // How can this stream be adapted up?
  Adaptation adaptation = stream_adapter_->GetAdaptationDown();
  if (adaptation.status() == Adaptation::Status::kLimitReached) {
    // Add resource as most limited.
    VideoStreamAdapter::RestrictionsWithCounters restrictions;
    std::tie(std::ignore, restrictions) = FindMostLimitedResources();
    UpdateResourceLimitations(reason_resource, restrictions.restrictions,
                              restrictions.counters);
  }
  if (adaptation.status() != Adaptation::Status::kValid) {
    rtc::StringBuilder message;
    message << "Not adapting down because VideoStreamAdapter returned "
            << Adaptation::StatusToString(adaptation.status());
    return MitigationResultAndLogMessage(MitigationResult::kRejectedByAdapter,
                                         message.Release());
  }
  // Apply adaptation.
  UpdateResourceLimitations(reason_resource, adaptation.restrictions(),
                            adaptation.counters());
  stream_adapter_->ApplyAdaptation(adaptation, reason_resource);
  rtc::StringBuilder message;
  message << "Adapted down successfully. Unfiltered adaptations: "
          << stream_adapter_->adaptation_counters().ToString();
  return MitigationResultAndLogMessage(MitigationResult::kAdaptationApplied,
                                       message.Release());
}

std::pair<std::vector<rtc::scoped_refptr<Resource>>,
          VideoStreamAdapter::RestrictionsWithCounters>
ResourceAdaptationProcessor::FindMostLimitedResources() const {
  std::vector<rtc::scoped_refptr<Resource>> most_limited_resources;
  VideoStreamAdapter::RestrictionsWithCounters most_limited_restrictions{
      VideoSourceRestrictions(), VideoAdaptationCounters()};

  for (const auto& resource_and_adaptation_limit_ :
       adaptation_limits_by_resources_) {
    const auto& restrictions_with_counters =
        resource_and_adaptation_limit_.second;
    if (restrictions_with_counters.counters.Total() >
        most_limited_restrictions.counters.Total()) {
      most_limited_restrictions = restrictions_with_counters;
      most_limited_resources.clear();
      most_limited_resources.push_back(resource_and_adaptation_limit_.first);
    } else if (most_limited_restrictions.counters ==
               restrictions_with_counters.counters) {
      most_limited_resources.push_back(resource_and_adaptation_limit_.first);
    }
  }
  return std::make_pair(std::move(most_limited_resources),
                        most_limited_restrictions);
}

void ResourceAdaptationProcessor::UpdateResourceLimitations(
    rtc::scoped_refptr<Resource> reason_resource,
    const VideoSourceRestrictions& restrictions,
    const VideoAdaptationCounters& counters) {
  auto& adaptation_limits = adaptation_limits_by_resources_[reason_resource];
  if (adaptation_limits.restrictions == restrictions &&
      adaptation_limits.counters == counters) {
    return;
  }
  adaptation_limits = {restrictions, counters};

  std::map<rtc::scoped_refptr<Resource>, VideoAdaptationCounters> limitations;
  for (const auto& p : adaptation_limits_by_resources_) {
    limitations.insert(std::make_pair(p.first, p.second.counters));
  }
  for (auto limitations_listener : resource_limitations_listeners_) {
    limitations_listener->OnResourceLimitationChanged(reason_resource,
                                                      limitations);
  }
}

void ResourceAdaptationProcessor::OnVideoSourceRestrictionsUpdated(
    VideoSourceRestrictions restrictions,
    const VideoAdaptationCounters& adaptation_counters,
    rtc::scoped_refptr<Resource> reason,
    const VideoSourceRestrictions& unfiltered_restrictions) {
  RTC_DCHECK_RUN_ON(task_queue_);
  if (reason) {
    UpdateResourceLimitations(reason, unfiltered_restrictions,
                              adaptation_counters);
  } else if (adaptation_counters.Total() == 0) {
    // Adaptations are cleared.
    adaptation_limits_by_resources_.clear();
    previous_mitigation_results_.clear();
    for (auto limitations_listener : resource_limitations_listeners_) {
      limitations_listener->OnResourceLimitationChanged(nullptr, {});
    }
  }
}

}  // namespace webrtc
