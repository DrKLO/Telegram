// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/profiler/module_cache.h"

#include <algorithm>
#include <iterator>
#include <utility>

namespace base {

namespace {

// Supports heterogeneous comparisons on modules and addresses, for use in
// binary searching modules sorted by range for a contained address.
struct ModuleAddressCompare {
  bool operator()(const std::unique_ptr<const ModuleCache::Module>& module,
                  uintptr_t address) const {
    return module->GetBaseAddress() + module->GetSize() <= address;
  }

  bool operator()(
      uintptr_t address,
      const std::unique_ptr<const ModuleCache::Module>& module) const {
    return address < module->GetBaseAddress();
  }
};

}  // namespace

ModuleCache::ModuleCache() = default;
ModuleCache::~ModuleCache() = default;

const ModuleCache::Module* ModuleCache::GetModuleForAddress(uintptr_t address) {
  const auto non_native_module_loc = non_native_modules_.find(address);
  if (non_native_module_loc != non_native_modules_.end())
    return non_native_module_loc->get();

  const auto native_module_loc = native_modules_.find(address);
  if (native_module_loc != native_modules_.end())
    return native_module_loc->get();

  std::unique_ptr<const Module> new_module = CreateModuleForAddress(address);
  if (!new_module)
    return nullptr;
  const auto loc = native_modules_.insert(std::move(new_module));
  return loc.first->get();
}

std::vector<const ModuleCache::Module*> ModuleCache::GetModules() const {
  std::vector<const Module*> result;
  result.reserve(native_modules_.size());
  for (const std::unique_ptr<const Module>& module : native_modules_)
    result.push_back(module.get());
  for (const std::unique_ptr<const Module>& module : non_native_modules_)
    result.push_back(module.get());
  return result;
}

void ModuleCache::UpdateNonNativeModules(
    const std::vector<const Module*>& to_remove,
    std::vector<std::unique_ptr<const Module>> to_add) {
  // Insert the modules to remove into a set to support O(log(n)) lookup below.
  flat_set<const Module*> to_remove_set(to_remove.begin(), to_remove.end());

  // Reorder the modules to be removed to the last slots in the set, then move
  // them to the inactive modules, then erase the moved-from modules from the
  // set. The flat_set docs endorse using base::EraseIf() which performs the
  // same operations -- exclusive of the moves -- so this is OK even though it
  // might seem like we're messing with the internal set representation.
  //
  // remove_if is O(m*log(r)) where m is the number of current modules and r is
  // the number of modules to remove. insert and erase are both O(r).
  auto first_module_to_remove = std::remove_if(
      non_native_modules_.begin(), non_native_modules_.end(),
      [&to_remove_set](const std::unique_ptr<const Module>& module) {
        return to_remove_set.find(module.get()) != to_remove_set.end();
      });
  // All modules requested to be removed should have been found.
  DCHECK_EQ(static_cast<ptrdiff_t>(to_remove.size()),
            std::distance(first_module_to_remove, non_native_modules_.end()));
  inactive_non_native_modules_.insert(
      inactive_non_native_modules_.end(),
      std::make_move_iterator(first_module_to_remove),
      std::make_move_iterator(non_native_modules_.end()));
  non_native_modules_.erase(first_module_to_remove, non_native_modules_.end());

  // Insert the modules to be added. This operation is O((m + a) + a*log(a))
  // where m is the number of current modules and a is the number of modules to
  // be added.
  non_native_modules_.insert(std::make_move_iterator(to_add.begin()),
                             std::make_move_iterator(to_add.end()));
}

void ModuleCache::AddCustomNativeModule(std::unique_ptr<const Module> module) {
  native_modules_.insert(std::move(module));
}

bool ModuleCache::ModuleAndAddressCompare::operator()(
    const std::unique_ptr<const Module>& m1,
    const std::unique_ptr<const Module>& m2) const {
  return m1->GetBaseAddress() < m2->GetBaseAddress();
}

bool ModuleCache::ModuleAndAddressCompare::operator()(
    const std::unique_ptr<const Module>& m1,
    uintptr_t address) const {
  return m1->GetBaseAddress() + m1->GetSize() <= address;
}

bool ModuleCache::ModuleAndAddressCompare::operator()(
    uintptr_t address,
    const std::unique_ptr<const Module>& m2) const {
  return address < m2->GetBaseAddress();
}

}  // namespace base
