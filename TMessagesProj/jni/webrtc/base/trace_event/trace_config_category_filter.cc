// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/trace_event/trace_config_category_filter.h"

#include "base/memory/ptr_util.h"
#include "base/strings/pattern.h"
#include "base/strings/string_split.h"
#include "base/strings/string_tokenizer.h"
#include "base/strings/string_util.h"
#include "base/strings/stringprintf.h"
#include "base/trace_event/trace_event.h"

namespace base {
namespace trace_event {

namespace {
const char kIncludedCategoriesParam[] = "included_categories";
const char kExcludedCategoriesParam[] = "excluded_categories";
}

TraceConfigCategoryFilter::TraceConfigCategoryFilter() = default;

TraceConfigCategoryFilter::TraceConfigCategoryFilter(
    const TraceConfigCategoryFilter& other) = default;

TraceConfigCategoryFilter::~TraceConfigCategoryFilter() = default;

TraceConfigCategoryFilter& TraceConfigCategoryFilter::operator=(
    const TraceConfigCategoryFilter& rhs) = default;

void TraceConfigCategoryFilter::InitializeFromString(
    const StringPiece& category_filter_string) {
  std::vector<StringPiece> split = SplitStringPiece(
      category_filter_string, ",", TRIM_WHITESPACE, SPLIT_WANT_ALL);
  for (const StringPiece& category : split) {
    // Ignore empty categories.
    if (category.empty())
      continue;
    if (category.front() == '-') {
      // Excluded categories start with '-'.
      // Remove '-' from category string.
      excluded_categories_.push_back(category.substr(1).as_string());
    } else if (category.starts_with(TRACE_DISABLED_BY_DEFAULT(""))) {
      disabled_categories_.push_back(category.as_string());
    } else {
      included_categories_.push_back(category.as_string());
    }
  }
}

void TraceConfigCategoryFilter::InitializeFromConfigDict(const Value& dict) {
  const Value* included_category_list =
      dict.FindListKey(kIncludedCategoriesParam);
  if (included_category_list)
    SetCategoriesFromIncludedList(*included_category_list);
  const Value* excluded_category_list =
      dict.FindListKey(kExcludedCategoriesParam);
  if (excluded_category_list)
    SetCategoriesFromExcludedList(*excluded_category_list);
}

bool TraceConfigCategoryFilter::IsCategoryGroupEnabled(
    const StringPiece& category_group_name) const {
  bool had_enabled_by_default = false;
  DCHECK(!category_group_name.empty());
  CStringTokenizer category_group_tokens(category_group_name.begin(),
                                         category_group_name.end(), ",");
  while (category_group_tokens.GetNext()) {
    StringPiece category_group_token = category_group_tokens.token_piece();
    // Don't allow empty tokens, nor tokens with leading or trailing space.
    DCHECK(IsCategoryNameAllowed(category_group_token))
        << "Disallowed category string";
    if (IsCategoryEnabled(category_group_token))
      return true;

    if (!MatchPattern(category_group_token, TRACE_DISABLED_BY_DEFAULT("*")))
      had_enabled_by_default = true;
  }
  // Do a second pass to check for explicitly disabled categories
  // (those explicitly enabled have priority due to first pass).
  category_group_tokens.Reset();
  bool category_group_disabled = false;
  while (category_group_tokens.GetNext()) {
    StringPiece category_group_token = category_group_tokens.token_piece();
    for (const std::string& category : excluded_categories_) {
      if (MatchPattern(category_group_token, category)) {
        // Current token of category_group_name is present in excluded_list.
        // Flag the exclusion and proceed further to check if any of the
        // remaining categories of category_group_name is not present in the
        // excluded_ list.
        category_group_disabled = true;
        break;
      }
      // One of the category of category_group_name is not present in
      // excluded_ list. So, if it's not a disabled-by-default category,
      // it has to be included_ list. Enable the category_group_name
      // for recording.
      if (!MatchPattern(category_group_token, TRACE_DISABLED_BY_DEFAULT("*")))
        category_group_disabled = false;
    }
    // One of the categories present in category_group_name is not present in
    // excluded_ list. Implies this category_group_name group can be enabled
    // for recording, since one of its groups is enabled for recording.
    if (!category_group_disabled)
      break;
  }
  // If the category group is not excluded, and there are no included patterns
  // we consider this category group enabled, as long as it had categories
  // other than disabled-by-default.
  return !category_group_disabled && had_enabled_by_default &&
         included_categories_.empty();
}

bool TraceConfigCategoryFilter::IsCategoryEnabled(
    const StringPiece& category_name) const {
  // Check the disabled- filters and the disabled-* wildcard first so that a
  // "*" filter does not include the disabled.
  for (const std::string& category : disabled_categories_) {
    if (MatchPattern(category_name, category))
      return true;
  }

  if (MatchPattern(category_name, TRACE_DISABLED_BY_DEFAULT("*")))
    return false;

  for (const std::string& category : included_categories_) {
    if (MatchPattern(category_name, category))
      return true;
  }

  return false;
}

void TraceConfigCategoryFilter::Merge(const TraceConfigCategoryFilter& config) {
  // Keep included patterns only if both filters have an included entry.
  // Otherwise, one of the filter was specifying "*" and we want to honor the
  // broadest filter.
  if (!included_categories_.empty() && !config.included_categories_.empty()) {
    included_categories_.insert(included_categories_.end(),
                                config.included_categories_.begin(),
                                config.included_categories_.end());
  } else {
    included_categories_.clear();
  }

  disabled_categories_.insert(disabled_categories_.end(),
                              config.disabled_categories_.begin(),
                              config.disabled_categories_.end());
  excluded_categories_.insert(excluded_categories_.end(),
                              config.excluded_categories_.begin(),
                              config.excluded_categories_.end());
}

void TraceConfigCategoryFilter::Clear() {
  included_categories_.clear();
  disabled_categories_.clear();
  excluded_categories_.clear();
}

void TraceConfigCategoryFilter::ToDict(Value* dict) const {
  StringList categories(included_categories_);
  categories.insert(categories.end(), disabled_categories_.begin(),
                    disabled_categories_.end());
  AddCategoriesToDict(categories, kIncludedCategoriesParam, dict);
  AddCategoriesToDict(excluded_categories_, kExcludedCategoriesParam, dict);
}

std::string TraceConfigCategoryFilter::ToFilterString() const {
  std::string filter_string;
  WriteCategoryFilterString(included_categories_, &filter_string, true);
  WriteCategoryFilterString(disabled_categories_, &filter_string, true);
  WriteCategoryFilterString(excluded_categories_, &filter_string, false);
  return filter_string;
}

void TraceConfigCategoryFilter::SetCategoriesFromIncludedList(
    const Value& included_list) {
  included_categories_.clear();
  for (const Value& item : included_list.GetList()) {
    if (!item.is_string())
      continue;
    const std::string& category = item.GetString();
    if (category.compare(0, strlen(TRACE_DISABLED_BY_DEFAULT("")),
                         TRACE_DISABLED_BY_DEFAULT("")) == 0) {
      disabled_categories_.push_back(category);
    } else {
      included_categories_.push_back(category);
    }
  }
}

void TraceConfigCategoryFilter::SetCategoriesFromExcludedList(
    const Value& excluded_list) {
  excluded_categories_.clear();
  for (const Value& item : excluded_list.GetList()) {
    if (item.is_string())
      excluded_categories_.push_back(item.GetString());
  }
}

void TraceConfigCategoryFilter::AddCategoriesToDict(
    const StringList& categories,
    const char* param,
    Value* dict) const {
  if (categories.empty())
    return;

  std::vector<base::Value> list;
  for (const std::string& category : categories)
    list.emplace_back(category);
  dict->SetKey(param, base::Value(std::move(list)));
}

void TraceConfigCategoryFilter::WriteCategoryFilterString(
    const StringList& values,
    std::string* out,
    bool included) const {
  bool prepend_comma = !out->empty();
  int token_cnt = 0;
  for (const std::string& category : values) {
    if (token_cnt > 0 || prepend_comma)
      StringAppendF(out, ",");
    StringAppendF(out, "%s%s", (included ? "" : "-"), category.c_str());
    ++token_cnt;
  }
}

// static
bool TraceConfigCategoryFilter::IsCategoryNameAllowed(StringPiece str) {
  return !str.empty() && str.front() != ' ' && str.back() != ' ';
}

}  // namespace trace_event
}  // namespace base
