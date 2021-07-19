// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TRACE_EVENT_TRACE_CONFIG_CATEGORY_FILTER_H_
#define BASE_TRACE_EVENT_TRACE_CONFIG_CATEGORY_FILTER_H_

#include <string>
#include <vector>

#include "base/base_export.h"
#include "base/strings/string_piece.h"
#include "base/values.h"

namespace base {
namespace trace_event {

// Configuration of categories enabled and disabled in TraceConfig.
class BASE_EXPORT TraceConfigCategoryFilter {
 public:
  using StringList = std::vector<std::string>;

  TraceConfigCategoryFilter();
  TraceConfigCategoryFilter(const TraceConfigCategoryFilter& other);
  ~TraceConfigCategoryFilter();

  TraceConfigCategoryFilter& operator=(const TraceConfigCategoryFilter& rhs);

  // Initializes from category filter string. See TraceConfig constructor for
  // description of how to write category filter string.
  void InitializeFromString(const StringPiece& category_filter_string);

  // Initializes TraceConfigCategoryFilter object from the config dictionary.
  void InitializeFromConfigDict(const Value& dict);

  // Merges this with category filter config.
  void Merge(const TraceConfigCategoryFilter& config);
  void Clear();

  // Returns true if at least one category in the list is enabled by this
  // trace config. This is used to determine if the category filters are
  // enabled in the TRACE_* macros.
  bool IsCategoryGroupEnabled(const StringPiece& category_group_name) const;

  // Returns true if the category is enabled according to this trace config.
  // This tells whether a category is enabled from the TraceConfig's
  // perspective. Please refer to IsCategoryGroupEnabled() to determine if a
  // category is enabled from the tracing runtime's perspective.
  bool IsCategoryEnabled(const StringPiece& category_name) const;

  void ToDict(Value* dict) const;

  std::string ToFilterString() const;

  // Returns true if category name is a valid string.
  static bool IsCategoryNameAllowed(StringPiece str);

  const StringList& included_categories() const { return included_categories_; }
  const StringList& excluded_categories() const { return excluded_categories_; }

 private:
  void SetCategoriesFromIncludedList(const Value& included_list);
  void SetCategoriesFromExcludedList(const Value& excluded_list);

  void AddCategoriesToDict(const StringList& categories,
                           const char* param,
                           Value* dict) const;

  void WriteCategoryFilterString(const StringList& values,
                                 std::string* out,
                                 bool included) const;

  StringList included_categories_;
  StringList disabled_categories_;
  StringList excluded_categories_;
};

}  // namespace trace_event
}  // namespace base

#endif  // BASE_TRACE_EVENT_TRACE_CONFIG_CATEGORY_FILTER_H_
