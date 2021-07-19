// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/metrics/field_trial.h"

#include <algorithm>
#include <utility>

#include "base/base_switches.h"
#include "base/command_line.h"
#include "base/debug/activity_tracker.h"
#include "base/logging.h"
#include "base/metrics/field_trial_param_associator.h"
#include "base/metrics/histogram_macros.h"
#include "base/process/memory.h"
#include "base/process/process_handle.h"
#include "base/process/process_info.h"
#include "base/rand_util.h"
#include "base/strings/string_number_conversions.h"
#include "base/strings/string_split.h"
#include "base/strings/string_util.h"
#include "base/strings/stringprintf.h"
#include "base/strings/utf_string_conversions.h"
#include "base/unguessable_token.h"

// On POSIX, the fd is shared using the mapping in GlobalDescriptors.
#if defined(OS_POSIX) && !defined(OS_NACL)
#include "base/posix/global_descriptors.h"
#endif

namespace base {

namespace {

// Define a separator character to use when creating a persistent form of an
// instance.  This is intended for use as a command line argument, passed to a
// second process to mimic our state (i.e., provide the same group name).
const char kPersistentStringSeparator = '/';  // Currently a slash.

// Define a marker character to be used as a prefix to a trial name on the
// command line which forces its activation.
const char kActivationMarker = '*';

// Constants for the field trial allocator.
const char kAllocatorName[] = "FieldTrialAllocator";

// We allocate 128 KiB to hold all the field trial data. This should be enough,
// as most people use 3 - 25 KiB for field trials (as of 11/25/2016).
// This also doesn't allocate all 128 KiB at once -- the pages only get mapped
// to physical memory when they are touched. If the size of the allocated field
// trials does get larger than 128 KiB, then we will drop some field trials in
// child processes, leading to an inconsistent view between browser and child
// processes and possibly causing crashes (see crbug.com/661617).
const size_t kFieldTrialAllocationSize = 128 << 10;  // 128 KiB

#if defined(OS_MACOSX) && !defined(OS_IOS)
constexpr MachPortsForRendezvous::key_type kFieldTrialRendezvousKey = 'fldt';
#endif

// Writes out string1 and then string2 to pickle.
void WriteStringPair(Pickle* pickle,
                     const StringPiece& string1,
                     const StringPiece& string2) {
  pickle->WriteString(string1);
  pickle->WriteString(string2);
}

// Writes out the field trial's contents (via trial_state) to the pickle. The
// format of the pickle looks like:
// TrialName, GroupName, ParamKey1, ParamValue1, ParamKey2, ParamValue2, ...
// If there are no parameters, then it just ends at GroupName.
void PickleFieldTrial(const FieldTrial::State& trial_state, Pickle* pickle) {
  WriteStringPair(pickle, *trial_state.trial_name, *trial_state.group_name);

  // Get field trial params.
  std::map<std::string, std::string> params;
  FieldTrialParamAssociator::GetInstance()->GetFieldTrialParamsWithoutFallback(
      *trial_state.trial_name, *trial_state.group_name, &params);

  // Write params to pickle.
  for (const auto& param : params)
    WriteStringPair(pickle, param.first, param.second);
}

// Returns the boundary value for comparing against the FieldTrial's added
// groups for a given |divisor| (total probability) and |entropy_value|.
FieldTrial::Probability GetGroupBoundaryValue(
    FieldTrial::Probability divisor,
    double entropy_value) {
  // Add a tiny epsilon value to get consistent results when converting floating
  // points to int. Without it, boundary values have inconsistent results, e.g.:
  //
  //   static_cast<FieldTrial::Probability>(100 * 0.56) == 56
  //   static_cast<FieldTrial::Probability>(100 * 0.57) == 56
  //   static_cast<FieldTrial::Probability>(100 * 0.58) == 57
  //   static_cast<FieldTrial::Probability>(100 * 0.59) == 59
  const double kEpsilon = 1e-8;
  const FieldTrial::Probability result =
      static_cast<FieldTrial::Probability>(divisor * entropy_value + kEpsilon);
  // Ensure that adding the epsilon still results in a value < |divisor|.
  return std::min(result, divisor - 1);
}

// Separate type from FieldTrial::State so that it can use StringPieces.
struct FieldTrialStringEntry {
  StringPiece trial_name;
  StringPiece group_name;
  bool activated = false;
};

// Parses the --force-fieldtrials string |trials_string| into |entries|.
// Returns true if the string was parsed correctly. On failure, the |entries|
// array may end up being partially filled.
bool ParseFieldTrialsString(const std::string& trials_string,
                            std::vector<FieldTrialStringEntry>* entries) {
  const StringPiece trials_string_piece(trials_string);

  size_t next_item = 0;
  while (next_item < trials_string.length()) {
    size_t name_end = trials_string.find(kPersistentStringSeparator, next_item);
    if (name_end == trials_string.npos || next_item == name_end)
      return false;
    size_t group_name_end =
        trials_string.find(kPersistentStringSeparator, name_end + 1);
    if (name_end + 1 == group_name_end)
      return false;
    if (group_name_end == trials_string.npos)
      group_name_end = trials_string.length();

    FieldTrialStringEntry entry;
    // Verify if the trial should be activated or not.
    if (trials_string[next_item] == kActivationMarker) {
      // Name cannot be only the indicator.
      if (name_end - next_item == 1)
        return false;
      next_item++;
      entry.activated = true;
    }
    entry.trial_name =
        trials_string_piece.substr(next_item, name_end - next_item);
    entry.group_name =
        trials_string_piece.substr(name_end + 1, group_name_end - name_end - 1);
    next_item = group_name_end + 1;

    entries->push_back(std::move(entry));
  }
  return true;
}

void AddFeatureAndFieldTrialFlags(const char* enable_features_switch,
                                  const char* disable_features_switch,
                                  CommandLine* cmd_line) {
  std::string enabled_features;
  std::string disabled_features;
  FeatureList::GetInstance()->GetFeatureOverrides(&enabled_features,
                                                  &disabled_features);

  if (!enabled_features.empty())
    cmd_line->AppendSwitchASCII(enable_features_switch, enabled_features);
  if (!disabled_features.empty())
    cmd_line->AppendSwitchASCII(disable_features_switch, disabled_features);

  std::string field_trial_states;
  FieldTrialList::AllStatesToString(&field_trial_states, false);
  if (!field_trial_states.empty()) {
    cmd_line->AppendSwitchASCII(switches::kForceFieldTrials,
                                field_trial_states);
  }
}

void OnOutOfMemory(size_t size) {
#if defined(OS_NACL)
  NOTREACHED();
#else
  TerminateBecauseOutOfMemory(size);
#endif
}

#if !defined(OS_NACL)
// Returns whether the operation succeeded.
bool DeserializeGUIDFromStringPieces(StringPiece first,
                                     StringPiece second,
                                     UnguessableToken* guid) {
  uint64_t high = 0;
  uint64_t low = 0;
  if (!StringToUint64(first, &high) || !StringToUint64(second, &low))
    return false;

  *guid = UnguessableToken::Deserialize(high, low);
  return true;
}
#endif  // !defined(OS_NACL)

}  // namespace

// statics
const int FieldTrial::kNotFinalized = -1;
const int FieldTrial::kDefaultGroupNumber = 0;
bool FieldTrial::enable_benchmarking_ = false;

//------------------------------------------------------------------------------
// FieldTrial methods and members.

FieldTrial::EntropyProvider::~EntropyProvider() = default;

FieldTrial::State::State() = default;

FieldTrial::State::State(const State& other) = default;

FieldTrial::State::~State() = default;

bool FieldTrial::FieldTrialEntry::GetTrialAndGroupName(
    StringPiece* trial_name,
    StringPiece* group_name) const {
  PickleIterator iter = GetPickleIterator();
  return ReadStringPair(&iter, trial_name, group_name);
}

bool FieldTrial::FieldTrialEntry::GetParams(
    std::map<std::string, std::string>* params) const {
  PickleIterator iter = GetPickleIterator();
  StringPiece tmp;
  // Skip reading trial and group name.
  if (!ReadStringPair(&iter, &tmp, &tmp))
    return false;

  while (true) {
    StringPiece key;
    StringPiece value;
    if (!ReadStringPair(&iter, &key, &value))
      return key.empty();  // Non-empty is bad: got one of a pair.
    (*params)[key.as_string()] = value.as_string();
  }
}

PickleIterator FieldTrial::FieldTrialEntry::GetPickleIterator() const {
  const char* src =
      reinterpret_cast<const char*>(this) + sizeof(FieldTrialEntry);

  Pickle pickle(src, pickle_size);
  return PickleIterator(pickle);
}

bool FieldTrial::FieldTrialEntry::ReadStringPair(
    PickleIterator* iter,
    StringPiece* trial_name,
    StringPiece* group_name) const {
  if (!iter->ReadStringPiece(trial_name))
    return false;
  if (!iter->ReadStringPiece(group_name))
    return false;
  return true;
}

void FieldTrial::Disable() {
  DCHECK(!group_reported_);
  enable_field_trial_ = false;

  // In case we are disabled after initialization, we need to switch
  // the trial to the default group.
  if (group_ != kNotFinalized) {
    // Only reset when not already the default group, because in case we were
    // forced to the default group, the group number may not be
    // kDefaultGroupNumber, so we should keep it as is.
    if (group_name_ != default_group_name_)
      SetGroupChoice(default_group_name_, kDefaultGroupNumber);
  }
}

int FieldTrial::AppendGroup(const std::string& name,
                            Probability group_probability) {
  // When the group choice was previously forced, we only need to return the
  // the id of the chosen group, and anything can be returned for the others.
  if (forced_) {
    DCHECK(!group_name_.empty());
    if (name == group_name_) {
      // Note that while |group_| may be equal to |kDefaultGroupNumber| on the
      // forced trial, it will not have the same value as the default group
      // number returned from the non-forced |FactoryGetFieldTrial()| call,
      // which takes care to ensure that this does not happen.
      return group_;
    }
    DCHECK_NE(next_group_number_, group_);
    // We still return different numbers each time, in case some caller need
    // them to be different.
    return next_group_number_++;
  }

  DCHECK_LE(group_probability, divisor_);
  DCHECK_GE(group_probability, 0);

  if (enable_benchmarking_ || !enable_field_trial_)
    group_probability = 0;

  accumulated_group_probability_ += group_probability;

  DCHECK_LE(accumulated_group_probability_, divisor_);
  if (group_ == kNotFinalized && accumulated_group_probability_ > random_) {
    // This is the group that crossed the random line, so we do the assignment.
    SetGroupChoice(name, next_group_number_);
  }
  return next_group_number_++;
}

int FieldTrial::group() {
  FinalizeGroupChoice();
  if (trial_registered_)
    FieldTrialList::NotifyFieldTrialGroupSelection(this);
  return group_;
}

const std::string& FieldTrial::group_name() {
  // Call |group()| to ensure group gets assigned and observers are notified.
  group();
  DCHECK(!group_name_.empty());
  return group_name_;
}

const std::string& FieldTrial::GetGroupNameWithoutActivation() {
  FinalizeGroupChoice();
  return group_name_;
}

void FieldTrial::SetForced() {
  // We might have been forced before (e.g., by CreateFieldTrial) and it's
  // first come first served, e.g., command line switch has precedence.
  if (forced_)
    return;

  // And we must finalize the group choice before we mark ourselves as forced.
  FinalizeGroupChoice();
  forced_ = true;
}

// static
void FieldTrial::EnableBenchmarking() {
  DCHECK_EQ(0u, FieldTrialList::GetFieldTrialCount());
  enable_benchmarking_ = true;
}

// static
FieldTrial* FieldTrial::CreateSimulatedFieldTrial(
    const std::string& trial_name,
    Probability total_probability,
    const std::string& default_group_name,
    double entropy_value) {
  return new FieldTrial(trial_name, total_probability, default_group_name,
                        entropy_value);
}

FieldTrial::FieldTrial(const std::string& trial_name,
                       const Probability total_probability,
                       const std::string& default_group_name,
                       double entropy_value)
    : trial_name_(trial_name),
      divisor_(total_probability),
      default_group_name_(default_group_name),
      random_(GetGroupBoundaryValue(total_probability, entropy_value)),
      accumulated_group_probability_(0),
      next_group_number_(kDefaultGroupNumber + 1),
      group_(kNotFinalized),
      enable_field_trial_(true),
      forced_(false),
      group_reported_(false),
      trial_registered_(false),
      ref_(FieldTrialList::FieldTrialAllocator::kReferenceNull) {
  DCHECK_GT(total_probability, 0);
  DCHECK(!trial_name_.empty());
  DCHECK(!default_group_name_.empty())
      << "Trial " << trial_name << " is missing a default group name.";
}

FieldTrial::~FieldTrial() = default;

void FieldTrial::SetTrialRegistered() {
  DCHECK_EQ(kNotFinalized, group_);
  DCHECK(!trial_registered_);
  trial_registered_ = true;
}

void FieldTrial::SetGroupChoice(const std::string& group_name, int number) {
  group_ = number;
  if (group_name.empty())
    StringAppendF(&group_name_, "%d", group_);
  else
    group_name_ = group_name;
  DVLOG(1) << "Field trial: " << trial_name_ << " Group choice:" << group_name_;
}

void FieldTrial::FinalizeGroupChoice() {
  FinalizeGroupChoiceImpl(false);
}

void FieldTrial::FinalizeGroupChoiceImpl(bool is_locked) {
  if (group_ != kNotFinalized)
    return;
  accumulated_group_probability_ = divisor_;
  // Here it's OK to use |kDefaultGroupNumber| since we can't be forced and not
  // finalized.
  DCHECK(!forced_);
  SetGroupChoice(default_group_name_, kDefaultGroupNumber);

  // Add the field trial to shared memory.
  if (trial_registered_)
    FieldTrialList::OnGroupFinalized(is_locked, this);
}

bool FieldTrial::GetActiveGroup(ActiveGroup* active_group) const {
  if (!group_reported_ || !enable_field_trial_)
    return false;
  DCHECK_NE(group_, kNotFinalized);
  active_group->trial_name = trial_name_;
  active_group->group_name = group_name_;
  return true;
}

bool FieldTrial::GetStateWhileLocked(State* field_trial_state,
                                     bool include_disabled) {
  if (!include_disabled && !enable_field_trial_)
    return false;
  FinalizeGroupChoiceImpl(true);
  field_trial_state->trial_name = &trial_name_;
  field_trial_state->group_name = &group_name_;
  field_trial_state->activated = group_reported_;
  return true;
}

//------------------------------------------------------------------------------
// FieldTrialList methods and members.

// static
FieldTrialList* FieldTrialList::global_ = nullptr;

// static
bool FieldTrialList::used_without_global_ = false;

FieldTrialList::Observer::~Observer() = default;

FieldTrialList::FieldTrialList(
    std::unique_ptr<const FieldTrial::EntropyProvider> entropy_provider)
    : entropy_provider_(std::move(entropy_provider)),
      observer_list_(new ObserverListThreadSafe<FieldTrialList::Observer>(
          ObserverListPolicy::EXISTING_ONLY)) {
  DCHECK(!global_);
  DCHECK(!used_without_global_);
  global_ = this;
}

FieldTrialList::~FieldTrialList() {
  AutoLock auto_lock(lock_);
  while (!registered_.empty()) {
    auto it = registered_.begin();
    it->second->Release();
    registered_.erase(it->first);
  }
  // Note: If this DCHECK fires in a test that uses ScopedFeatureList, it is
  // likely caused by nested ScopedFeatureLists being destroyed in a different
  // order than they are initialized.
  DCHECK_EQ(this, global_);
  global_ = nullptr;
}

// static
FieldTrial* FieldTrialList::FactoryGetFieldTrial(
    const std::string& trial_name,
    FieldTrial::Probability total_probability,
    const std::string& default_group_name,
    FieldTrial::RandomizationType randomization_type,
    int* default_group_number) {
  return FactoryGetFieldTrialWithRandomizationSeed(
      trial_name, total_probability, default_group_name, randomization_type, 0,
      default_group_number, nullptr);
}

// static
FieldTrial* FieldTrialList::FactoryGetFieldTrialWithRandomizationSeed(
    const std::string& trial_name,
    FieldTrial::Probability total_probability,
    const std::string& default_group_name,
    FieldTrial::RandomizationType randomization_type,
    uint32_t randomization_seed,
    int* default_group_number,
    const FieldTrial::EntropyProvider* override_entropy_provider) {
  if (default_group_number)
    *default_group_number = FieldTrial::kDefaultGroupNumber;
  // Check if the field trial has already been created in some other way.
  FieldTrial* existing_trial = Find(trial_name);
  if (existing_trial) {
    CHECK(existing_trial->forced_);
    // If the default group name differs between the existing forced trial
    // and this trial, then use a different value for the default group number.
    if (default_group_number &&
        default_group_name != existing_trial->default_group_name()) {
      // If the new default group number corresponds to the group that was
      // chosen for the forced trial (which has been finalized when it was
      // forced), then set the default group number to that.
      if (default_group_name == existing_trial->group_name_internal()) {
        *default_group_number = existing_trial->group_;
      } else {
        // Otherwise, use |kNonConflictingGroupNumber| (-2) for the default
        // group number, so that it does not conflict with the |AppendGroup()|
        // result for the chosen group.
        const int kNonConflictingGroupNumber = -2;
        static_assert(
            kNonConflictingGroupNumber != FieldTrial::kDefaultGroupNumber,
            "The 'non-conflicting' group number conflicts");
        static_assert(kNonConflictingGroupNumber != FieldTrial::kNotFinalized,
                      "The 'non-conflicting' group number conflicts");
        *default_group_number = kNonConflictingGroupNumber;
      }
    }
    return existing_trial;
  }

  double entropy_value;
  if (randomization_type == FieldTrial::ONE_TIME_RANDOMIZED) {
    // If an override entropy provider is given, use it.
    const FieldTrial::EntropyProvider* entropy_provider =
        override_entropy_provider ? override_entropy_provider
                                  : GetEntropyProviderForOneTimeRandomization();
    CHECK(entropy_provider);
    entropy_value = entropy_provider->GetEntropyForTrial(trial_name,
                                                         randomization_seed);
  } else {
    DCHECK_EQ(FieldTrial::SESSION_RANDOMIZED, randomization_type);
    DCHECK_EQ(0U, randomization_seed);
    entropy_value = RandDouble();
  }

  FieldTrial* field_trial = new FieldTrial(trial_name, total_probability,
                                           default_group_name, entropy_value);
  FieldTrialList::Register(field_trial);
  return field_trial;
}

// static
FieldTrial* FieldTrialList::Find(const std::string& trial_name) {
  if (!global_)
    return nullptr;
  AutoLock auto_lock(global_->lock_);
  return global_->PreLockedFind(trial_name);
}

// static
int FieldTrialList::FindValue(const std::string& trial_name) {
  FieldTrial* field_trial = Find(trial_name);
  if (field_trial)
    return field_trial->group();
  return FieldTrial::kNotFinalized;
}

// static
std::string FieldTrialList::FindFullName(const std::string& trial_name) {
  FieldTrial* field_trial = Find(trial_name);
  if (field_trial)
    return field_trial->group_name();
  return std::string();
}

// static
bool FieldTrialList::TrialExists(const std::string& trial_name) {
  return Find(trial_name) != nullptr;
}

// static
bool FieldTrialList::IsTrialActive(const std::string& trial_name) {
  FieldTrial* field_trial = Find(trial_name);
  FieldTrial::ActiveGroup active_group;
  return field_trial && field_trial->GetActiveGroup(&active_group);
}

// static
void FieldTrialList::StatesToString(std::string* output) {
  FieldTrial::ActiveGroups active_groups;
  GetActiveFieldTrialGroups(&active_groups);
  for (const auto& active_group : active_groups) {
    DCHECK_EQ(std::string::npos,
              active_group.trial_name.find(kPersistentStringSeparator));
    DCHECK_EQ(std::string::npos,
              active_group.group_name.find(kPersistentStringSeparator));
    output->append(active_group.trial_name);
    output->append(1, kPersistentStringSeparator);
    output->append(active_group.group_name);
    output->append(1, kPersistentStringSeparator);
  }
}

// static
void FieldTrialList::AllStatesToString(std::string* output,
                                       bool include_disabled) {
  if (!global_)
    return;
  AutoLock auto_lock(global_->lock_);

  for (const auto& registered : global_->registered_) {
    FieldTrial::State trial;
    if (!registered.second->GetStateWhileLocked(&trial, include_disabled))
      continue;
    DCHECK_EQ(std::string::npos,
              trial.trial_name->find(kPersistentStringSeparator));
    DCHECK_EQ(std::string::npos,
              trial.group_name->find(kPersistentStringSeparator));
    if (trial.activated)
      output->append(1, kActivationMarker);
    output->append(*trial.trial_name);
    output->append(1, kPersistentStringSeparator);
    output->append(*trial.group_name);
    output->append(1, kPersistentStringSeparator);
  }
}

// static
std::string FieldTrialList::AllParamsToString(bool include_disabled,
                                              EscapeDataFunc encode_data_func) {
  FieldTrialParamAssociator* params_associator =
      FieldTrialParamAssociator::GetInstance();
  std::string output;
  for (const auto& registered : GetRegisteredTrials()) {
    FieldTrial::State trial;
    if (!registered.second->GetStateWhileLocked(&trial, include_disabled))
      continue;
    DCHECK_EQ(std::string::npos,
              trial.trial_name->find(kPersistentStringSeparator));
    DCHECK_EQ(std::string::npos,
              trial.group_name->find(kPersistentStringSeparator));
    std::map<std::string, std::string> params;
    if (params_associator->GetFieldTrialParamsWithoutFallback(
            *trial.trial_name, *trial.group_name, &params)) {
      if (params.size() > 0) {
        // Add comma to seprate from previous entry if it exists.
        if (!output.empty())
          output.append(1, ',');

        output.append(encode_data_func(*trial.trial_name));
        output.append(1, '.');
        output.append(encode_data_func(*trial.group_name));
        output.append(1, ':');

        std::string param_str;
        for (const auto& param : params) {
          // Add separator from previous param information if it exists.
          if (!param_str.empty())
            param_str.append(1, kPersistentStringSeparator);
          param_str.append(encode_data_func(param.first));
          param_str.append(1, kPersistentStringSeparator);
          param_str.append(encode_data_func(param.second));
        }

        output.append(param_str);
      }
    }
  }
  return output;
}

// static
void FieldTrialList::GetActiveFieldTrialGroups(
    FieldTrial::ActiveGroups* active_groups) {
  DCHECK(active_groups->empty());
  if (!global_)
    return;
  AutoLock auto_lock(global_->lock_);

  for (const auto& registered : global_->registered_) {
    FieldTrial::ActiveGroup active_group;
    if (registered.second->GetActiveGroup(&active_group))
      active_groups->push_back(active_group);
  }
}

// static
void FieldTrialList::GetActiveFieldTrialGroupsFromString(
    const std::string& trials_string,
    FieldTrial::ActiveGroups* active_groups) {
  std::vector<FieldTrialStringEntry> entries;
  if (!ParseFieldTrialsString(trials_string, &entries))
    return;

  for (const auto& entry : entries) {
    if (entry.activated) {
      FieldTrial::ActiveGroup group;
      group.trial_name = entry.trial_name.as_string();
      group.group_name = entry.group_name.as_string();
      active_groups->push_back(group);
    }
  }
}

// static
void FieldTrialList::GetInitiallyActiveFieldTrials(
    const CommandLine& command_line,
    FieldTrial::ActiveGroups* active_groups) {
  DCHECK(global_);
  DCHECK(global_->create_trials_from_command_line_called_);

  if (!global_->field_trial_allocator_) {
    GetActiveFieldTrialGroupsFromString(
        command_line.GetSwitchValueASCII(switches::kForceFieldTrials),
        active_groups);
    return;
  }

  FieldTrialAllocator* allocator = global_->field_trial_allocator_.get();
  FieldTrialAllocator::Iterator mem_iter(allocator);
  const FieldTrial::FieldTrialEntry* entry;
  while ((entry = mem_iter.GetNextOfObject<FieldTrial::FieldTrialEntry>()) !=
         nullptr) {
    StringPiece trial_name;
    StringPiece group_name;
    if (subtle::NoBarrier_Load(&entry->activated) &&
        entry->GetTrialAndGroupName(&trial_name, &group_name)) {
      FieldTrial::ActiveGroup group;
      group.trial_name = trial_name.as_string();
      group.group_name = group_name.as_string();
      active_groups->push_back(group);
    }
  }
}

// static
bool FieldTrialList::CreateTrialsFromString(
    const std::string& trials_string,
    const std::set<std::string>& ignored_trial_names) {
  DCHECK(global_);
  if (trials_string.empty() || !global_)
    return true;

  std::vector<FieldTrialStringEntry> entries;
  if (!ParseFieldTrialsString(trials_string, &entries))
    return false;

  for (const auto& entry : entries) {
    const std::string trial_name = entry.trial_name.as_string();
    const std::string group_name = entry.group_name.as_string();

    if (Contains(ignored_trial_names, trial_name)) {
      // This is to warn that the field trial forced through command-line
      // input is unforcable.
      // Use --enable-logging or --enable-logging=stderr to see this warning.
      LOG(WARNING) << "Field trial: " << trial_name << " cannot be forced.";
      continue;
    }

    FieldTrial* trial = CreateFieldTrial(trial_name, group_name);
    if (!trial)
      return false;
    if (entry.activated) {
      // Call |group()| to mark the trial as "used" and notify observers, if
      // any. This is useful to ensure that field trials created in child
      // processes are properly reported in crash reports.
      trial->group();
    }
  }
  return true;
}

// static
void FieldTrialList::CreateTrialsFromCommandLine(
    const CommandLine& cmd_line,
    const char* field_trial_handle_switch,
    int fd_key) {
  global_->create_trials_from_command_line_called_ = true;

#if defined(OS_WIN) || defined(OS_FUCHSIA) || \
    (defined(OS_MACOSX) && !defined(OS_IOS))
  if (cmd_line.HasSwitch(field_trial_handle_switch)) {
    std::string switch_value =
        cmd_line.GetSwitchValueASCII(field_trial_handle_switch);
    bool result = CreateTrialsFromSwitchValue(switch_value);
    UMA_HISTOGRAM_BOOLEAN("ChildProcess.FieldTrials.CreateFromShmemSuccess",
                          result);
  }
#elif defined(OS_POSIX) && !defined(OS_NACL)
  // On POSIX, we check if the handle is valid by seeing if the browser process
  // sent over the switch (we don't care about the value). Invalid handles
  // occur in some browser tests which don't initialize the allocator.
  if (cmd_line.HasSwitch(field_trial_handle_switch)) {
    std::string switch_value =
        cmd_line.GetSwitchValueASCII(field_trial_handle_switch);
    bool result = CreateTrialsFromDescriptor(fd_key, switch_value);
    UMA_HISTOGRAM_BOOLEAN("ChildProcess.FieldTrials.CreateFromShmemSuccess",
                          result);
    DCHECK(result);
  }
#endif

  if (cmd_line.HasSwitch(switches::kForceFieldTrials)) {
    bool result = FieldTrialList::CreateTrialsFromString(
        cmd_line.GetSwitchValueASCII(switches::kForceFieldTrials),
        std::set<std::string>());
    UMA_HISTOGRAM_BOOLEAN("ChildProcess.FieldTrials.CreateFromSwitchSuccess",
                          result);
    DCHECK(result);
  }
}

// static
void FieldTrialList::CreateFeaturesFromCommandLine(
    const CommandLine& command_line,
    const char* enable_features_switch,
    const char* disable_features_switch,
    FeatureList* feature_list) {
  // Fallback to command line if not using shared memory.
  if (!global_->field_trial_allocator_.get()) {
    return feature_list->InitializeFromCommandLine(
        command_line.GetSwitchValueASCII(enable_features_switch),
        command_line.GetSwitchValueASCII(disable_features_switch));
  }

  feature_list->InitializeFromSharedMemory(
      global_->field_trial_allocator_.get());
}

#if defined(OS_WIN)
// static
void FieldTrialList::AppendFieldTrialHandleIfNeeded(
    HandlesToInheritVector* handles) {
  if (!global_)
    return;
  InstantiateFieldTrialAllocatorIfNeeded();
  if (global_->readonly_allocator_region_.IsValid())
    handles->push_back(global_->readonly_allocator_region_.GetPlatformHandle());
}
#elif defined(OS_FUCHSIA)
// TODO(fuchsia): Implement shared-memory configuration (crbug.com/752368).
#elif defined(OS_MACOSX) && !defined(OS_IOS)
// static
void FieldTrialList::InsertFieldTrialHandleIfNeeded(
    MachPortsForRendezvous* rendezvous_ports) {
  if (!global_)
    return;
  InstantiateFieldTrialAllocatorIfNeeded();
  if (global_->readonly_allocator_region_.IsValid()) {
    rendezvous_ports->emplace(
        kFieldTrialRendezvousKey,
        MachRendezvousPort(
            global_->readonly_allocator_region_.GetPlatformHandle(),
            MACH_MSG_TYPE_COPY_SEND));
  }
}
#elif defined(OS_POSIX) && !defined(OS_NACL)
// static
int FieldTrialList::GetFieldTrialDescriptor() {
  InstantiateFieldTrialAllocatorIfNeeded();
  if (!global_ || !global_->readonly_allocator_region_.IsValid())
    return -1;

#if defined(OS_ANDROID)
  return global_->readonly_allocator_region_.GetPlatformHandle();
#else
  return global_->readonly_allocator_region_.GetPlatformHandle().fd;
#endif
}
#endif

// static
ReadOnlySharedMemoryRegion
FieldTrialList::DuplicateFieldTrialSharedMemoryForTesting() {
  if (!global_)
    return ReadOnlySharedMemoryRegion();

  return global_->readonly_allocator_region_.Duplicate();
}

// static
void FieldTrialList::CopyFieldTrialStateToFlags(
    const char* field_trial_handle_switch,
    const char* enable_features_switch,
    const char* disable_features_switch,
    CommandLine* cmd_line) {
#if !defined(OS_FUCHSIA)  // TODO(752368): Not yet supported on Fuchsia.
  // Use shared memory to communicate field trial state to child processes.
  // The browser is the only process that has write access to the shared memory.
  InstantiateFieldTrialAllocatorIfNeeded();
#endif  // !defined(OS_FUCHSIA)

  // If the readonly handle did not get created, fall back to flags.
  if (!global_ || !global_->readonly_allocator_region_.IsValid()) {
    AddFeatureAndFieldTrialFlags(enable_features_switch,
                                 disable_features_switch, cmd_line);
    return;
  }

  global_->field_trial_allocator_->UpdateTrackingHistograms();
  std::string switch_value =
      SerializeSharedMemoryRegionMetadata(global_->readonly_allocator_region_);
  cmd_line->AppendSwitchASCII(field_trial_handle_switch, switch_value);

  // Append --enable-features and --disable-features switches corresponding
  // to the features enabled on the command-line, so that child and browser
  // process command lines match and clearly show what has been specified
  // explicitly by the user.
  std::string enabled_features;
  std::string disabled_features;
  FeatureList::GetInstance()->GetCommandLineFeatureOverrides(
      &enabled_features, &disabled_features);

  if (!enabled_features.empty())
    cmd_line->AppendSwitchASCII(enable_features_switch, enabled_features);
  if (!disabled_features.empty())
    cmd_line->AppendSwitchASCII(disable_features_switch, disabled_features);
}

// static
FieldTrial* FieldTrialList::CreateFieldTrial(
    const std::string& name,
    const std::string& group_name) {
  DCHECK(global_);
  DCHECK_GE(name.size(), 0u);
  DCHECK_GE(group_name.size(), 0u);
  if (name.empty() || group_name.empty() || !global_)
    return nullptr;

  FieldTrial* field_trial = FieldTrialList::Find(name);
  if (field_trial) {
    // In single process mode, or when we force them from the command line,
    // we may have already created the field trial.
    if (field_trial->group_name_internal() != group_name)
      return nullptr;
    return field_trial;
  }
  const int kTotalProbability = 100;
  field_trial = new FieldTrial(name, kTotalProbability, group_name, 0);
  FieldTrialList::Register(field_trial);
  // Force the trial, which will also finalize the group choice.
  field_trial->SetForced();
  return field_trial;
}

// static
bool FieldTrialList::AddObserver(Observer* observer) {
  if (!global_)
    return false;
  global_->observer_list_->AddObserver(observer);
  return true;
}

// static
void FieldTrialList::RemoveObserver(Observer* observer) {
  if (!global_)
    return;
  global_->observer_list_->RemoveObserver(observer);
}

// static
void FieldTrialList::SetSynchronousObserver(Observer* observer) {
  DCHECK(!global_->synchronous_observer_);
  global_->synchronous_observer_ = observer;
}

// static
void FieldTrialList::RemoveSynchronousObserver(Observer* observer) {
  DCHECK_EQ(global_->synchronous_observer_, observer);
  global_->synchronous_observer_ = nullptr;
}

// static
void FieldTrialList::OnGroupFinalized(bool is_locked, FieldTrial* field_trial) {
  if (!global_)
    return;
  if (is_locked) {
    AddToAllocatorWhileLocked(global_->field_trial_allocator_.get(),
                              field_trial);
  } else {
    AutoLock auto_lock(global_->lock_);
    AddToAllocatorWhileLocked(global_->field_trial_allocator_.get(),
                              field_trial);
  }
}

// static
void FieldTrialList::NotifyFieldTrialGroupSelection(FieldTrial* field_trial) {
  if (!global_)
    return;

  {
    AutoLock auto_lock(global_->lock_);
    if (field_trial->group_reported_)
      return;
    field_trial->group_reported_ = true;

    if (!field_trial->enable_field_trial_)
      return;

    ActivateFieldTrialEntryWhileLocked(field_trial);
  }

  if (global_->synchronous_observer_) {
    global_->synchronous_observer_->OnFieldTrialGroupFinalized(
        field_trial->trial_name(), field_trial->group_name_internal());
  }

  global_->observer_list_->NotifySynchronously(
      FROM_HERE, &FieldTrialList::Observer::OnFieldTrialGroupFinalized,
      field_trial->trial_name(), field_trial->group_name_internal());
}

// static
size_t FieldTrialList::GetFieldTrialCount() {
  if (!global_)
    return 0;
  AutoLock auto_lock(global_->lock_);
  return global_->registered_.size();
}

// static
bool FieldTrialList::GetParamsFromSharedMemory(
    FieldTrial* field_trial,
    std::map<std::string, std::string>* params) {
  DCHECK(global_);
  // If the field trial allocator is not set up yet, then there are several
  // cases:
  //   - We are in the browser process and the allocator has not been set up
  //   yet. If we got here, then we couldn't find the params in
  //   FieldTrialParamAssociator, so it's definitely not here. Return false.
  //   - Using shared memory for field trials is not enabled. If we got here,
  //   then there's nothing in shared memory. Return false.
  //   - We are in the child process and the allocator has not been set up yet.
  //   If this is the case, then you are calling this too early. The field trial
  //   allocator should get set up very early in the lifecycle. Try to see if
  //   you can call it after it's been set up.
  AutoLock auto_lock(global_->lock_);
  if (!global_->field_trial_allocator_)
    return false;

  // If ref_ isn't set, then the field trial data can't be in shared memory.
  if (!field_trial->ref_)
    return false;

  const FieldTrial::FieldTrialEntry* entry =
      global_->field_trial_allocator_->GetAsObject<FieldTrial::FieldTrialEntry>(
          field_trial->ref_);

  size_t allocated_size =
      global_->field_trial_allocator_->GetAllocSize(field_trial->ref_);
  size_t actual_size = sizeof(FieldTrial::FieldTrialEntry) + entry->pickle_size;
  if (allocated_size < actual_size)
    return false;

  return entry->GetParams(params);
}

// static
void FieldTrialList::ClearParamsFromSharedMemoryForTesting() {
  if (!global_)
    return;

  AutoLock auto_lock(global_->lock_);
  if (!global_->field_trial_allocator_)
    return;

  // To clear the params, we iterate through every item in the allocator, copy
  // just the trial and group name into a newly-allocated segment and then clear
  // the existing item.
  FieldTrialAllocator* allocator = global_->field_trial_allocator_.get();
  FieldTrialAllocator::Iterator mem_iter(allocator);

  // List of refs to eventually be made iterable. We can't make it in the loop,
  // since it would go on forever.
  std::vector<FieldTrial::FieldTrialRef> new_refs;

  FieldTrial::FieldTrialRef prev_ref;
  while ((prev_ref = mem_iter.GetNextOfType<FieldTrial::FieldTrialEntry>()) !=
         FieldTrialAllocator::kReferenceNull) {
    // Get the existing field trial entry in shared memory.
    const FieldTrial::FieldTrialEntry* prev_entry =
        allocator->GetAsObject<FieldTrial::FieldTrialEntry>(prev_ref);
    StringPiece trial_name;
    StringPiece group_name;
    if (!prev_entry->GetTrialAndGroupName(&trial_name, &group_name))
      continue;

    // Write a new entry, minus the params.
    Pickle pickle;
    pickle.WriteString(trial_name);
    pickle.WriteString(group_name);
    size_t total_size = sizeof(FieldTrial::FieldTrialEntry) + pickle.size();
    FieldTrial::FieldTrialEntry* new_entry =
        allocator->New<FieldTrial::FieldTrialEntry>(total_size);
    subtle::NoBarrier_Store(&new_entry->activated,
                            subtle::NoBarrier_Load(&prev_entry->activated));
    new_entry->pickle_size = pickle.size();

    // TODO(lawrencewu): Modify base::Pickle to be able to write over a section
    // in memory, so we can avoid this memcpy.
    char* dst = reinterpret_cast<char*>(new_entry) +
                sizeof(FieldTrial::FieldTrialEntry);
    memcpy(dst, pickle.data(), pickle.size());

    // Update the ref on the field trial and add it to the list to be made
    // iterable.
    FieldTrial::FieldTrialRef new_ref = allocator->GetAsReference(new_entry);
    FieldTrial* trial = global_->PreLockedFind(trial_name.as_string());
    trial->ref_ = new_ref;
    new_refs.push_back(new_ref);

    // Mark the existing entry as unused.
    allocator->ChangeType(prev_ref, 0,
                          FieldTrial::FieldTrialEntry::kPersistentTypeId,
                          /*clear=*/false);
  }

  for (const auto& ref : new_refs) {
    allocator->MakeIterable(ref);
  }
}

// static
void FieldTrialList::DumpAllFieldTrialsToPersistentAllocator(
    PersistentMemoryAllocator* allocator) {
  if (!global_)
    return;
  AutoLock auto_lock(global_->lock_);
  for (const auto& registered : global_->registered_) {
    AddToAllocatorWhileLocked(allocator, registered.second);
  }
}

// static
std::vector<const FieldTrial::FieldTrialEntry*>
FieldTrialList::GetAllFieldTrialsFromPersistentAllocator(
    PersistentMemoryAllocator const& allocator) {
  std::vector<const FieldTrial::FieldTrialEntry*> entries;
  FieldTrialAllocator::Iterator iter(&allocator);
  const FieldTrial::FieldTrialEntry* entry;
  while ((entry = iter.GetNextOfObject<FieldTrial::FieldTrialEntry>()) !=
         nullptr) {
    entries.push_back(entry);
  }
  return entries;
}

// static
FieldTrialList* FieldTrialList::GetInstance() {
  return global_;
}

// static
FieldTrialList* FieldTrialList::BackupInstanceForTesting() {
  FieldTrialList* instance = global_;
  global_ = nullptr;
  return instance;
}

// static
void FieldTrialList::RestoreInstanceForTesting(FieldTrialList* instance) {
  global_ = instance;
}

// static
std::string FieldTrialList::SerializeSharedMemoryRegionMetadata(
    const ReadOnlySharedMemoryRegion& shm) {
  std::stringstream ss;
#if defined(OS_WIN)
  // Tell the child process the name of the inherited HANDLE.
  uintptr_t uintptr_handle =
      reinterpret_cast<uintptr_t>(shm.GetPlatformHandle());
  ss << uintptr_handle << ",";
#elif defined(OS_FUCHSIA)
  ss << shm.GetPlatformHandle()->get() << ",";
#elif defined(OS_MACOSX) && !defined(OS_IOS)
  // The handle on Mac is looked up directly by the child, rather than being
  // transferred to the child over the command line.
  ss << kFieldTrialRendezvousKey << ",";
#elif !defined(OS_POSIX)
#error Unsupported OS
#endif

  UnguessableToken guid = shm.GetGUID();
  ss << guid.GetHighForSerialization() << "," << guid.GetLowForSerialization();
  ss << "," << shm.GetSize();
  return ss.str();
}

#if defined(OS_WIN) || defined(OS_FUCHSIA) || \
    (defined(OS_MACOSX) && !defined(OS_IOS))

// static
ReadOnlySharedMemoryRegion
FieldTrialList::DeserializeSharedMemoryRegionMetadata(
    const std::string& switch_value) {
  std::vector<StringPiece> tokens =
      SplitStringPiece(switch_value, ",", KEEP_WHITESPACE, SPLIT_WANT_ALL);

  if (tokens.size() != 4)
    return ReadOnlySharedMemoryRegion();

  int field_trial_handle = 0;
  if (!StringToInt(tokens[0], &field_trial_handle))
    return ReadOnlySharedMemoryRegion();
#if defined(OS_FUCHSIA)
  zx_handle_t handle = static_cast<zx_handle_t>(field_trial_handle);
  zx::vmo scoped_handle = zx::vmo(handle);
#elif defined(OS_WIN)
  HANDLE handle = reinterpret_cast<HANDLE>(field_trial_handle);
  if (IsCurrentProcessElevated()) {
    // LaunchElevatedProcess doesn't have a way to duplicate the handle,
    // but this process can since by definition it's not sandboxed.
    ProcessId parent_pid = GetParentProcessId(GetCurrentProcess());
    HANDLE parent_handle = OpenProcess(PROCESS_ALL_ACCESS, FALSE, parent_pid);
    // TODO(https://crbug.com/916461): Duplicating the handle is known to fail
    // with ERROR_ACCESS_DENIED when the parent process is being torn down. This
    // should be handled elegantly somehow.
    DuplicateHandle(parent_handle, handle, GetCurrentProcess(), &handle, 0,
                    FALSE, DUPLICATE_SAME_ACCESS);
    CloseHandle(parent_handle);
  }
  win::ScopedHandle scoped_handle(handle);
#elif defined(OS_MACOSX) && !defined(OS_IOS)
  auto* rendezvous = MachPortRendezvousClient::GetInstance();
  if (!rendezvous)
    return ReadOnlySharedMemoryRegion();
  mac::ScopedMachSendRight scoped_handle =
      rendezvous->TakeSendRight(field_trial_handle);
  if (!scoped_handle.is_valid())
    return ReadOnlySharedMemoryRegion();
#endif

  UnguessableToken guid;
  if (!DeserializeGUIDFromStringPieces(tokens[1], tokens[2], &guid))
    return ReadOnlySharedMemoryRegion();

  int size;
  if (!StringToInt(tokens[3], &size))
    return ReadOnlySharedMemoryRegion();

  auto platform_handle = subtle::PlatformSharedMemoryRegion::Take(
      std::move(scoped_handle),
      subtle::PlatformSharedMemoryRegion::Mode::kReadOnly,
      static_cast<size_t>(size), guid);
  return ReadOnlySharedMemoryRegion::Deserialize(std::move(platform_handle));
}

#elif defined(OS_POSIX) && !defined(OS_NACL)

// static
ReadOnlySharedMemoryRegion
FieldTrialList::DeserializeSharedMemoryRegionMetadata(
    int fd,
    const std::string& switch_value) {
  std::vector<StringPiece> tokens =
      SplitStringPiece(switch_value, ",", KEEP_WHITESPACE, SPLIT_WANT_ALL);

  if (tokens.size() != 3)
    return ReadOnlySharedMemoryRegion();

  UnguessableToken guid;
  if (!DeserializeGUIDFromStringPieces(tokens[0], tokens[1], &guid))
    return ReadOnlySharedMemoryRegion();

  int size;
  if (!StringToInt(tokens[2], &size))
    return ReadOnlySharedMemoryRegion();

  auto platform_region = subtle::PlatformSharedMemoryRegion::Take(
      ScopedFD(fd), subtle::PlatformSharedMemoryRegion::Mode::kReadOnly,
      static_cast<size_t>(size), guid);
  return ReadOnlySharedMemoryRegion::Deserialize(std::move(platform_region));
}

#endif

#if defined(OS_WIN) || defined(OS_FUCHSIA) || \
    (defined(OS_MACOSX) && !defined(OS_IOS))
// static
bool FieldTrialList::CreateTrialsFromSwitchValue(
    const std::string& switch_value) {
  ReadOnlySharedMemoryRegion shm =
      DeserializeSharedMemoryRegionMetadata(switch_value);
  if (!shm.IsValid())
    return false;
  return FieldTrialList::CreateTrialsFromSharedMemoryRegion(shm);
}
#elif defined(OS_POSIX) && !defined(OS_NACL)
// static
bool FieldTrialList::CreateTrialsFromDescriptor(
    int fd_key,
    const std::string& switch_value) {
  if (fd_key == -1)
    return false;

  int fd = GlobalDescriptors::GetInstance()->MaybeGet(fd_key);
  if (fd == -1)
    return false;

  ReadOnlySharedMemoryRegion shm =
      DeserializeSharedMemoryRegionMetadata(fd, switch_value);
  if (!shm.IsValid())
    return false;

  bool result = FieldTrialList::CreateTrialsFromSharedMemoryRegion(shm);
  DCHECK(result);
  return true;
}
#endif  // defined(OS_POSIX) && !defined(OS_NACL)

// static
bool FieldTrialList::CreateTrialsFromSharedMemoryRegion(
    const ReadOnlySharedMemoryRegion& shm_region) {
  ReadOnlySharedMemoryMapping shm_mapping =
      shm_region.MapAt(0, kFieldTrialAllocationSize);
  if (!shm_mapping.IsValid())
    OnOutOfMemory(kFieldTrialAllocationSize);

  return FieldTrialList::CreateTrialsFromSharedMemoryMapping(
      std::move(shm_mapping));
}

// static
bool FieldTrialList::CreateTrialsFromSharedMemoryMapping(
    ReadOnlySharedMemoryMapping shm_mapping) {
  global_->field_trial_allocator_ =
      std::make_unique<ReadOnlySharedPersistentMemoryAllocator>(
          std::move(shm_mapping), 0, kAllocatorName);
  FieldTrialAllocator* shalloc = global_->field_trial_allocator_.get();
  FieldTrialAllocator::Iterator mem_iter(shalloc);

  const FieldTrial::FieldTrialEntry* entry;
  while ((entry = mem_iter.GetNextOfObject<FieldTrial::FieldTrialEntry>()) !=
         nullptr) {
    StringPiece trial_name;
    StringPiece group_name;
    if (!entry->GetTrialAndGroupName(&trial_name, &group_name))
      return false;

    // TODO(lawrencewu): Convert the API for CreateFieldTrial to take
    // StringPieces.
    FieldTrial* trial =
        CreateFieldTrial(trial_name.as_string(), group_name.as_string());

    trial->ref_ = mem_iter.GetAsReference(entry);
    if (subtle::NoBarrier_Load(&entry->activated)) {
      // Call |group()| to mark the trial as "used" and notify observers, if
      // any. This is useful to ensure that field trials created in child
      // processes are properly reported in crash reports.
      trial->group();
    }
  }
  return true;
}

// static
void FieldTrialList::InstantiateFieldTrialAllocatorIfNeeded() {
  if (!global_)
    return;

  AutoLock auto_lock(global_->lock_);
  // Create the allocator if not already created and add all existing trials.
  if (global_->field_trial_allocator_ != nullptr)
    return;

  MappedReadOnlyRegion shm =
      ReadOnlySharedMemoryRegion::Create(kFieldTrialAllocationSize);

  if (!shm.IsValid())
    OnOutOfMemory(kFieldTrialAllocationSize);

  global_->field_trial_allocator_ =
      std::make_unique<WritableSharedPersistentMemoryAllocator>(
          std::move(shm.mapping), 0, kAllocatorName);
  global_->field_trial_allocator_->CreateTrackingHistograms(kAllocatorName);

  // Add all existing field trials.
  for (const auto& registered : global_->registered_) {
    AddToAllocatorWhileLocked(global_->field_trial_allocator_.get(),
                              registered.second);
  }

  // Add all existing features.
  FeatureList::GetInstance()->AddFeaturesToAllocator(
      global_->field_trial_allocator_.get());

#if !defined(OS_NACL)
  global_->readonly_allocator_region_ = std::move(shm.region);
#endif
}

// static
void FieldTrialList::AddToAllocatorWhileLocked(
    PersistentMemoryAllocator* allocator,
    FieldTrial* field_trial) {
  // Don't do anything if the allocator hasn't been instantiated yet.
  if (allocator == nullptr)
    return;

  // Or if the allocator is read only, which means we are in a child process and
  // shouldn't be writing to it.
  if (allocator->IsReadonly())
    return;

  FieldTrial::State trial_state;
  if (!field_trial->GetStateWhileLocked(&trial_state, false))
    return;

  // Or if we've already added it. We must check after GetState since it can
  // also add to the allocator.
  if (field_trial->ref_)
    return;

  Pickle pickle;
  PickleFieldTrial(trial_state, &pickle);

  size_t total_size = sizeof(FieldTrial::FieldTrialEntry) + pickle.size();
  FieldTrial::FieldTrialRef ref = allocator->Allocate(
      total_size, FieldTrial::FieldTrialEntry::kPersistentTypeId);
  if (ref == FieldTrialAllocator::kReferenceNull) {
    NOTREACHED();
    return;
  }

  FieldTrial::FieldTrialEntry* entry =
      allocator->GetAsObject<FieldTrial::FieldTrialEntry>(ref);
  subtle::NoBarrier_Store(&entry->activated, trial_state.activated);
  entry->pickle_size = pickle.size();

  // TODO(lawrencewu): Modify base::Pickle to be able to write over a section in
  // memory, so we can avoid this memcpy.
  char* dst =
      reinterpret_cast<char*>(entry) + sizeof(FieldTrial::FieldTrialEntry);
  memcpy(dst, pickle.data(), pickle.size());

  allocator->MakeIterable(ref);
  field_trial->ref_ = ref;
}

// static
void FieldTrialList::ActivateFieldTrialEntryWhileLocked(
    FieldTrial* field_trial) {
  FieldTrialAllocator* allocator = global_->field_trial_allocator_.get();

  // Check if we're in the child process and return early if so.
  if (!allocator || allocator->IsReadonly())
    return;

  FieldTrial::FieldTrialRef ref = field_trial->ref_;
  if (ref == FieldTrialAllocator::kReferenceNull) {
    // It's fine to do this even if the allocator hasn't been instantiated
    // yet -- it'll just return early.
    AddToAllocatorWhileLocked(allocator, field_trial);
  } else {
    // It's also okay to do this even though the callee doesn't have a lock --
    // the only thing that happens on a stale read here is a slight performance
    // hit from the child re-synchronizing activation state.
    FieldTrial::FieldTrialEntry* entry =
        allocator->GetAsObject<FieldTrial::FieldTrialEntry>(ref);
    subtle::NoBarrier_Store(&entry->activated, 1);
  }
}

// static
const FieldTrial::EntropyProvider*
    FieldTrialList::GetEntropyProviderForOneTimeRandomization() {
  if (!global_) {
    used_without_global_ = true;
    return nullptr;
  }

  return global_->entropy_provider_.get();
}

FieldTrial* FieldTrialList::PreLockedFind(const std::string& name) {
  auto it = registered_.find(name);
  if (registered_.end() == it)
    return nullptr;
  return it->second;
}

// static
void FieldTrialList::Register(FieldTrial* trial) {
  if (!global_) {
    used_without_global_ = true;
    return;
  }
  AutoLock auto_lock(global_->lock_);
  CHECK(!global_->PreLockedFind(trial->trial_name())) << trial->trial_name();
  trial->AddRef();
  trial->SetTrialRegistered();
  global_->registered_[trial->trial_name()] = trial;
}

// static
FieldTrialList::RegistrationMap FieldTrialList::GetRegisteredTrials() {
  RegistrationMap output;
  if (global_) {
    AutoLock auto_lock(global_->lock_);
    output = global_->registered_;
  }
  return output;
}

}  // namespace base
