// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/feature_list.h"

#include <stddef.h>

#include <utility>
#include <vector>

#include "base/debug/alias.h"
#include "base/logging.h"
#include "base/memory/ptr_util.h"
#include "base/metrics/field_trial.h"
#include "base/pickle.h"
#include "base/strings/string_split.h"
#include "base/strings/string_util.h"
#include "build/build_config.h"

namespace base {

namespace {

// Pointer to the FeatureList instance singleton that was set via
// FeatureList::SetInstance(). Does not use base/memory/singleton.h in order to
// have more control over initialization timing. Leaky.
FeatureList* g_feature_list_instance = nullptr;

// Tracks whether the FeatureList instance was initialized via an accessor, and
// which Feature that accessor was for, if so.
const Feature* g_initialized_from_accessor = nullptr;

#if DCHECK_IS_ON()
const char* g_reason_overrides_disallowed = nullptr;

void DCheckOverridesAllowed() {
  const bool feature_overrides_allowed = !g_reason_overrides_disallowed;
  DCHECK(feature_overrides_allowed) << g_reason_overrides_disallowed;
}
#else
void DCheckOverridesAllowed() {}
#endif

// An allocator entry for a feature in shared memory. The FeatureEntry is
// followed by a base::Pickle object that contains the feature and trial name.
struct FeatureEntry {
  // SHA1(FeatureEntry): Increment this if structure changes!
  static constexpr uint32_t kPersistentTypeId = 0x06567CA6 + 1;

  // Expected size for 32/64-bit check.
  static constexpr size_t kExpectedInstanceSize = 8;

  // Specifies whether a feature override enables or disables the feature. Same
  // values as the OverrideState enum in feature_list.h
  uint32_t override_state;

  // Size of the pickled structure, NOT the total size of this entry.
  uint32_t pickle_size;

  // Reads the feature and trial name from the pickle. Calling this is only
  // valid on an initialized entry that's in shared memory.
  bool GetFeatureAndTrialName(StringPiece* feature_name,
                              StringPiece* trial_name) const {
    const char* src =
        reinterpret_cast<const char*>(this) + sizeof(FeatureEntry);

    Pickle pickle(src, pickle_size);
    PickleIterator pickle_iter(pickle);

    if (!pickle_iter.ReadStringPiece(feature_name))
      return false;

    // Return true because we are not guaranteed to have a trial name anyways.
    auto sink = pickle_iter.ReadStringPiece(trial_name);
    ALLOW_UNUSED_LOCAL(sink);
    return true;
  }
};

// Some characters are not allowed to appear in feature names or the associated
// field trial names, as they are used as special characters for command-line
// serialization. This function checks that the strings are ASCII (since they
// are used in command-line API functions that require ASCII) and whether there
// are any reserved characters present, returning true if the string is valid.
// Only called in DCHECKs.
bool IsValidFeatureOrFieldTrialName(const std::string& name) {
  return IsStringASCII(name) && name.find_first_of(",<*") == std::string::npos;
}

}  // namespace

#if defined(DCHECK_IS_CONFIGURABLE)
const Feature kDCheckIsFatalFeature{"DcheckIsFatal",
                                    FEATURE_DISABLED_BY_DEFAULT};
#endif  // defined(DCHECK_IS_CONFIGURABLE)

FeatureList::FeatureList() = default;

FeatureList::~FeatureList() = default;

FeatureList::ScopedDisallowOverrides::ScopedDisallowOverrides(
    const char* reason)
#if DCHECK_IS_ON()
    : previous_reason_(g_reason_overrides_disallowed) {
  g_reason_overrides_disallowed = reason;
}
#else
{
}
#endif

FeatureList::ScopedDisallowOverrides::~ScopedDisallowOverrides() {
#if DCHECK_IS_ON()
  g_reason_overrides_disallowed = previous_reason_;
#endif
}

void FeatureList::InitializeFromCommandLine(
    const std::string& enable_features,
    const std::string& disable_features) {
  DCHECK(!initialized_);

  // Process disabled features first, so that disabled ones take precedence over
  // enabled ones (since RegisterOverride() uses insert()).
  RegisterOverridesFromCommandLine(disable_features, OVERRIDE_DISABLE_FEATURE);
  RegisterOverridesFromCommandLine(enable_features, OVERRIDE_ENABLE_FEATURE);

  initialized_from_command_line_ = true;
}

void FeatureList::InitializeFromSharedMemory(
    PersistentMemoryAllocator* allocator) {
  DCHECK(!initialized_);

  PersistentMemoryAllocator::Iterator iter(allocator);
  const FeatureEntry* entry;
  while ((entry = iter.GetNextOfObject<FeatureEntry>()) != nullptr) {
    OverrideState override_state =
        static_cast<OverrideState>(entry->override_state);

    StringPiece feature_name;
    StringPiece trial_name;
    if (!entry->GetFeatureAndTrialName(&feature_name, &trial_name))
      continue;

    FieldTrial* trial = FieldTrialList::Find(trial_name.as_string());
    RegisterOverride(feature_name, override_state, trial);
  }
}

bool FeatureList::IsFeatureOverriddenFromCommandLine(
    const std::string& feature_name,
    OverrideState state) const {
  auto it = overrides_.find(feature_name);
  return it != overrides_.end() && it->second.overridden_state == state &&
         !it->second.overridden_by_field_trial;
}

void FeatureList::AssociateReportingFieldTrial(
    const std::string& feature_name,
    OverrideState for_overridden_state,
    FieldTrial* field_trial) {
  DCHECK(
      IsFeatureOverriddenFromCommandLine(feature_name, for_overridden_state));

  // Only one associated field trial is supported per feature. This is generally
  // enforced server-side.
  OverrideEntry* entry = &overrides_.find(feature_name)->second;
  if (entry->field_trial) {
    NOTREACHED() << "Feature " << feature_name
                 << " already has trial: " << entry->field_trial->trial_name()
                 << ", associating trial: " << field_trial->trial_name();
    return;
  }

  entry->field_trial = field_trial;
}

void FeatureList::RegisterFieldTrialOverride(const std::string& feature_name,
                                             OverrideState override_state,
                                             FieldTrial* field_trial) {
  DCHECK(field_trial);
  DCHECK(!Contains(overrides_, feature_name) ||
         !overrides_.find(feature_name)->second.field_trial)
      << "Feature " << feature_name
      << " has conflicting field trial overrides: "
      << overrides_.find(feature_name)->second.field_trial->trial_name()
      << " / " << field_trial->trial_name()
      << ". Please make sure that the trial (study) name is consistent across:"
      << " (1)The server config, (2)The fieldtrial_testing_config, and"
      << " (3) The about_flags.cc";

  RegisterOverride(feature_name, override_state, field_trial);
}

void FeatureList::RegisterExtraFeatureOverrides(
    const std::vector<FeatureOverrideInfo>& extra_overrides) {
  for (const FeatureOverrideInfo& override_info : extra_overrides) {
    RegisterOverride(override_info.first.get().name, override_info.second,
                     /* field_trial = */ nullptr);
  }
}

void FeatureList::AddFeaturesToAllocator(PersistentMemoryAllocator* allocator) {
  DCHECK(initialized_);

  for (const auto& override : overrides_) {
    Pickle pickle;
    pickle.WriteString(override.first);
    if (override.second.field_trial)
      pickle.WriteString(override.second.field_trial->trial_name());

    size_t total_size = sizeof(FeatureEntry) + pickle.size();
    FeatureEntry* entry = allocator->New<FeatureEntry>(total_size);
    if (!entry)
      return;

    entry->override_state = override.second.overridden_state;
    entry->pickle_size = pickle.size();

    char* dst = reinterpret_cast<char*>(entry) + sizeof(FeatureEntry);
    memcpy(dst, pickle.data(), pickle.size());

    allocator->MakeIterable(entry);
  }
}

void FeatureList::GetFeatureOverrides(std::string* enable_overrides,
                                      std::string* disable_overrides) {
  GetFeatureOverridesImpl(enable_overrides, disable_overrides, false);
}

void FeatureList::GetCommandLineFeatureOverrides(
    std::string* enable_overrides,
    std::string* disable_overrides) {
  GetFeatureOverridesImpl(enable_overrides, disable_overrides, true);
}

// static
bool FeatureList::IsEnabled(const Feature& feature) {
  if (!g_feature_list_instance) {
    g_initialized_from_accessor = &feature;
    return feature.default_state == FEATURE_ENABLED_BY_DEFAULT;
  }
  return g_feature_list_instance->IsFeatureEnabled(feature);
}

// static
FieldTrial* FeatureList::GetFieldTrial(const Feature& feature) {
  if (!g_feature_list_instance) {
    g_initialized_from_accessor = &feature;
    return nullptr;
  }
  return g_feature_list_instance->GetAssociatedFieldTrial(feature);
}

// static
std::vector<StringPiece> FeatureList::SplitFeatureListString(
    StringPiece input) {
  return SplitStringPiece(input, ",", TRIM_WHITESPACE, SPLIT_WANT_NONEMPTY);
}

// static
bool FeatureList::InitializeInstance(const std::string& enable_features,
                                     const std::string& disable_features) {
  return InitializeInstance(enable_features, disable_features,
                            std::vector<FeatureOverrideInfo>());
}

// static
bool FeatureList::InitializeInstance(
    const std::string& enable_features,
    const std::string& disable_features,
    const std::vector<FeatureOverrideInfo>& extra_overrides) {
  // We want to initialize a new instance here to support command-line features
  // in testing better. For example, we initialize a dummy instance in
  // base/test/test_suite.cc, and override it in content/browser/
  // browser_main_loop.cc.
  // On the other hand, we want to avoid re-initialization from command line.
  // For example, we initialize an instance in chrome/browser/
  // chrome_browser_main.cc and do not override it in content/browser/
  // browser_main_loop.cc.
  // If the singleton was previously initialized from within an accessor, we
  // want to prevent callers from reinitializing the singleton and masking the
  // accessor call(s) which likely returned incorrect information.
  if (g_initialized_from_accessor) {
    DEBUG_ALIAS_FOR_CSTR(accessor_name, g_initialized_from_accessor->name, 128);
    CHECK(!g_initialized_from_accessor);
  }
  bool instance_existed_before = false;
  if (g_feature_list_instance) {
    if (g_feature_list_instance->initialized_from_command_line_)
      return false;

    delete g_feature_list_instance;
    g_feature_list_instance = nullptr;
    instance_existed_before = true;
  }

  std::unique_ptr<FeatureList> feature_list(new FeatureList);
  feature_list->InitializeFromCommandLine(enable_features, disable_features);
  feature_list->RegisterExtraFeatureOverrides(extra_overrides);
  FeatureList::SetInstance(std::move(feature_list));
  return !instance_existed_before;
}

// static
FeatureList* FeatureList::GetInstance() {
  return g_feature_list_instance;
}

// static
void FeatureList::SetInstance(std::unique_ptr<FeatureList> instance) {
  DCHECK(!g_feature_list_instance);
  instance->FinalizeInitialization();

  // Note: Intentional leak of global singleton.
  g_feature_list_instance = instance.release();

#if defined(DCHECK_IS_CONFIGURABLE)
  // Update the behaviour of LOG_DCHECK to match the Feature configuration.
  // DCHECK is also forced to be FATAL if we are running a death-test.
  // TODO(asvitkine): If we find other use-cases that need integrating here
  // then define a proper API/hook for the purpose.
  if (FeatureList::IsEnabled(kDCheckIsFatalFeature) ||
      CommandLine::ForCurrentProcess()->HasSwitch(
          "gtest_internal_run_death_test")) {
    logging::LOG_DCHECK = logging::LOG_FATAL;
  } else {
    logging::LOG_DCHECK = logging::LOG_INFO;
  }
#endif  // defined(DCHECK_IS_CONFIGURABLE)
}

// static
std::unique_ptr<FeatureList> FeatureList::ClearInstanceForTesting() {
  FeatureList* old_instance = g_feature_list_instance;
  g_feature_list_instance = nullptr;
  g_initialized_from_accessor = nullptr;
  return WrapUnique(old_instance);
}

// static
void FeatureList::RestoreInstanceForTesting(
    std::unique_ptr<FeatureList> instance) {
  DCHECK(!g_feature_list_instance);
  // Note: Intentional leak of global singleton.
  g_feature_list_instance = instance.release();
}

void FeatureList::FinalizeInitialization() {
  DCHECK(!initialized_);
  // Store the field trial list pointer for DCHECKing.
  field_trial_list_ = FieldTrialList::GetInstance();
  initialized_ = true;
}

bool FeatureList::IsFeatureEnabled(const Feature& feature) {
  DCHECK(initialized_);
  DCHECK(IsValidFeatureOrFieldTrialName(feature.name)) << feature.name;
  DCHECK(CheckFeatureIdentity(feature)) << feature.name;

  auto it = overrides_.find(feature.name);
  if (it != overrides_.end()) {
    const OverrideEntry& entry = it->second;

    // Activate the corresponding field trial, if necessary.
    if (entry.field_trial)
      entry.field_trial->group();

    // TODO(asvitkine) Expand this section as more support is added.

    // If marked as OVERRIDE_USE_DEFAULT, simply return the default state below.
    if (entry.overridden_state != OVERRIDE_USE_DEFAULT)
      return entry.overridden_state == OVERRIDE_ENABLE_FEATURE;
  }
  // Otherwise, return the default state.
  return feature.default_state == FEATURE_ENABLED_BY_DEFAULT;
}

FieldTrial* FeatureList::GetAssociatedFieldTrial(const Feature& feature) {
  DCHECK(initialized_);
  DCHECK(IsValidFeatureOrFieldTrialName(feature.name)) << feature.name;
  DCHECK(CheckFeatureIdentity(feature)) << feature.name;

  auto it = overrides_.find(feature.name);
  if (it != overrides_.end()) {
    const OverrideEntry& entry = it->second;
    return entry.field_trial;
  }

  return nullptr;
}

void FeatureList::RegisterOverridesFromCommandLine(
    const std::string& feature_list,
    OverrideState overridden_state) {
  for (const auto& value : SplitFeatureListString(feature_list)) {
    StringPiece feature_name = value;
    FieldTrial* trial = nullptr;

    // The entry may be of the form FeatureName<FieldTrialName - in which case,
    // this splits off the field trial name and associates it with the override.
    std::string::size_type pos = feature_name.find('<');
    if (pos != std::string::npos) {
      feature_name = StringPiece(value.data(), pos);
      trial = FieldTrialList::Find(value.substr(pos + 1).as_string());
#if !defined(OS_NACL)
      // If the below DCHECK fires, it means a non-existent trial name was
      // specified via the "Feature<Trial" command-line syntax.
      DCHECK(trial) << "trial=" << value.substr(pos + 1);
#endif  // !defined(OS_NACL)
    }

    RegisterOverride(feature_name, overridden_state, trial);
  }
}

void FeatureList::RegisterOverride(StringPiece feature_name,
                                   OverrideState overridden_state,
                                   FieldTrial* field_trial) {
  DCHECK(!initialized_);
  DCheckOverridesAllowed();
  if (field_trial) {
    DCHECK(IsValidFeatureOrFieldTrialName(field_trial->trial_name()))
        << field_trial->trial_name();
  }
  if (feature_name.starts_with("*")) {
    feature_name = feature_name.substr(1);
    overridden_state = OVERRIDE_USE_DEFAULT;
  }

  // Note: The semantics of insert() is that it does not overwrite the entry if
  // one already exists for the key. Thus, only the first override for a given
  // feature name takes effect.
  overrides_.insert(std::make_pair(
      feature_name.as_string(), OverrideEntry(overridden_state, field_trial)));
}

void FeatureList::GetFeatureOverridesImpl(std::string* enable_overrides,
                                          std::string* disable_overrides,
                                          bool command_line_only) {
  DCHECK(initialized_);

  // Check that the FieldTrialList this is associated with, if any, is the
  // active one. If not, it likely indicates that this FeatureList has override
  // entries from a freed FieldTrial, which may be caused by an incorrect test
  // set up.
  if (field_trial_list_)
    DCHECK_EQ(field_trial_list_, FieldTrialList::GetInstance());

  enable_overrides->clear();
  disable_overrides->clear();

  // Note: Since |overrides_| is a std::map, iteration will be in alphabetical
  // order. This is not guaranteed to users of this function, but is useful for
  // tests to assume the order.
  for (const auto& entry : overrides_) {
    if (command_line_only &&
        (entry.second.field_trial != nullptr ||
         entry.second.overridden_state == OVERRIDE_USE_DEFAULT)) {
      continue;
    }

    std::string* target_list = nullptr;
    switch (entry.second.overridden_state) {
      case OVERRIDE_USE_DEFAULT:
      case OVERRIDE_ENABLE_FEATURE:
        target_list = enable_overrides;
        break;
      case OVERRIDE_DISABLE_FEATURE:
        target_list = disable_overrides;
        break;
    }

    if (!target_list->empty())
      target_list->push_back(',');
    if (entry.second.overridden_state == OVERRIDE_USE_DEFAULT)
      target_list->push_back('*');
    target_list->append(entry.first);
    if (entry.second.field_trial) {
      target_list->push_back('<');
      target_list->append(entry.second.field_trial->trial_name());
    }
  }
}

bool FeatureList::CheckFeatureIdentity(const Feature& feature) {
  AutoLock auto_lock(feature_identity_tracker_lock_);

  auto it = feature_identity_tracker_.find(feature.name);
  if (it == feature_identity_tracker_.end()) {
    // If it's not tracked yet, register it.
    feature_identity_tracker_[feature.name] = &feature;
    return true;
  }
  // Compare address of |feature| to the existing tracked entry.
  return it->second == &feature;
}

FeatureList::OverrideEntry::OverrideEntry(OverrideState overridden_state,
                                          FieldTrial* field_trial)
    : overridden_state(overridden_state),
      field_trial(field_trial),
      overridden_by_field_trial(field_trial != nullptr) {}

}  // namespace base
