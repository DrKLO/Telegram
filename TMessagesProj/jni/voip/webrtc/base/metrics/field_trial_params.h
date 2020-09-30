// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_METRICS_FIELD_TRIAL_PARAMS_H_
#define BASE_METRICS_FIELD_TRIAL_PARAMS_H_

#include <map>
#include <string>

#include "base/base_export.h"
#include "base/logging.h"

namespace base {

struct Feature;

// Key-value mapping type for field trial parameters.
typedef std::map<std::string, std::string> FieldTrialParams;

// Param string decoding function for AssociateFieldTrialParamsFromString().
typedef std::string (*FieldTrialParamsDecodeStringFunc)(const std::string& str);

// Associates the specified set of key-value |params| with the field trial
// specified by |trial_name| and |group_name|. Fails and returns false if the
// specified field trial already has params associated with it or the trial
// is already active (group() has been called on it). Thread safe.
BASE_EXPORT bool AssociateFieldTrialParams(const std::string& trial_name,
                                           const std::string& group_name,
                                           const FieldTrialParams& params);

// Provides a mechanism to associate multiple set of params to multiple groups
// with a formatted string as returned by FieldTrialList::AllParamsToString().
// |decode_data_func| allows specifying a custom decoding function.
BASE_EXPORT bool AssociateFieldTrialParamsFromString(
    const std::string& params_string,
    FieldTrialParamsDecodeStringFunc decode_data_func);

// Retrieves the set of key-value |params| for the specified field trial, based
// on its selected group. If the field trial does not exist or its selected
// group does not have any parameters associated with it, returns false and
// does not modify |params|. Calling this function will result in the field
// trial being marked as active if found (i.e. group() will be called on it),
// if it wasn't already. Thread safe.
BASE_EXPORT bool GetFieldTrialParams(const std::string& trial_name,
                                     FieldTrialParams* params);

// Retrieves the set of key-value |params| for the field trial associated with
// the specified |feature|. A feature is associated with at most one field
// trial and selected group. See  base/feature_list.h for more information on
// features. If the feature is not enabled, or if there's no associated params,
// returns false and does not modify |params|. Calling this function will
// result in the associated field trial being marked as active if found (i.e.
// group() will be called on it), if it wasn't already. Thread safe.
BASE_EXPORT bool GetFieldTrialParamsByFeature(const base::Feature& feature,
                                              FieldTrialParams* params);

// Retrieves a specific parameter value corresponding to |param_name| for the
// specified field trial, based on its selected group. If the field trial does
// not exist or the specified parameter does not exist, returns an empty
// string. Calling this function will result in the field trial being marked as
// active if found (i.e. group() will be called on it), if it wasn't already.
// Thread safe.
BASE_EXPORT std::string GetFieldTrialParamValue(const std::string& trial_name,
                                                const std::string& param_name);

// Retrieves a specific parameter value corresponding to |param_name| for the
// field trial associated with the specified |feature|. A feature is associated
// with at most one field trial and selected group. See base/feature_list.h for
// more information on features. If the feature is not enabled, or the
// specified parameter does not exist, returns an empty string. Calling this
// function will result in the associated field trial being marked as active if
// found (i.e. group() will be called on it), if it wasn't already. Thread safe.
BASE_EXPORT std::string GetFieldTrialParamValueByFeature(
    const base::Feature& feature,
    const std::string& param_name);

// Same as GetFieldTrialParamValueByFeature(). On top of that, it converts the
// string value into an int using base::StringToInt() and returns it, if
// successful. Otherwise, it returns |default_value|. If the string value is not
// empty and the conversion does not succeed, it produces a warning to LOG.
BASE_EXPORT int GetFieldTrialParamByFeatureAsInt(const base::Feature& feature,
                                                 const std::string& param_name,
                                                 int default_value);

// Same as GetFieldTrialParamValueByFeature(). On top of that, it converts the
// string value into a double using base::StringToDouble() and returns it, if
// successful. Otherwise, it returns |default_value|. If the string value is not
// empty and the conversion does not succeed, it produces a warning to LOG.
BASE_EXPORT double GetFieldTrialParamByFeatureAsDouble(
    const base::Feature& feature,
    const std::string& param_name,
    double default_value);

// Same as GetFieldTrialParamValueByFeature(). On top of that, it converts the
// string value into a boolean and returns it, if successful. Otherwise, it
// returns |default_value|. The only string representations accepted here are
// "true" and "false". If the string value is not empty and the conversion does
// not succeed, it produces a warning to LOG.
BASE_EXPORT bool GetFieldTrialParamByFeatureAsBool(
    const base::Feature& feature,
    const std::string& param_name,
    bool default_value);

// Shared declaration for various FeatureParam<T> types.
//
// This template is defined for the following types T:
//   bool
//   int
//   double
//   std::string
//   enum types
//
// See the individual definitions below for the appropriate interfaces.
// Attempting to use it with any other type is a compile error.
template <typename T, bool IsEnum = std::is_enum<T>::value>
struct FeatureParam {
  // Prevent use of FeatureParam<> with unsupported types (e.g. void*). Uses T
  // in its definition so that evaluation is deferred until the template is
  // instantiated.
  static_assert(!std::is_same<T, T>::value, "unsupported FeatureParam<> type");
};

// Declares a string-valued parameter. Example:
//
//     constexpr FeatureParam<string> kAssistantName{
//         &kAssistantFeature, "assistant_name", "HAL"};
//
// If the feature is not set, or set to the empty string, then Get() will return
// the default value.
template <>
struct FeatureParam<std::string> {
  constexpr FeatureParam(const Feature* feature,
                         const char* name,
                         const char* default_value)
      : feature(feature), name(name), default_value(default_value) {}

  BASE_EXPORT std::string Get() const;

  const Feature* const feature;
  const char* const name;
  const char* const default_value;
};

// Declares a double-valued parameter. Example:
//
//     constexpr FeatureParam<double> kAssistantTriggerThreshold{
//         &kAssistantFeature, "trigger_threshold", 0.10};
//
// If the feature is not set, or set to an invalid double value, then Get() will
// return the default value.
template <>
struct FeatureParam<double> {
  constexpr FeatureParam(const Feature* feature,
                         const char* name,
                         double default_value)
      : feature(feature), name(name), default_value(default_value) {}

  BASE_EXPORT double Get() const;

  const Feature* const feature;
  const char* const name;
  const double default_value;
};

// Declares an int-valued parameter. Example:
//
//     constexpr FeatureParam<int> kAssistantParallelism{
//         &kAssistantFeature, "parallelism", 4};
//
// If the feature is not set, or set to an invalid int value, then Get() will
// return the default value.
template <>
struct FeatureParam<int> {
  constexpr FeatureParam(const Feature* feature,
                         const char* name,
                         int default_value)
      : feature(feature), name(name), default_value(default_value) {}

  BASE_EXPORT int Get() const;

  const Feature* const feature;
  const char* const name;
  const int default_value;
};

// Declares a bool-valued parameter. Example:
//
//     constexpr FeatureParam<int> kAssistantIsHelpful{
//         &kAssistantFeature, "is_helpful", true};
//
// If the feature is not set, or set to value other than "true" or "false", then
// Get() will return the default value.
template <>
struct FeatureParam<bool> {
  constexpr FeatureParam(const Feature* feature,
                         const char* name,
                         bool default_value)
      : feature(feature), name(name), default_value(default_value) {}

  BASE_EXPORT bool Get() const;

  const Feature* const feature;
  const char* const name;
  const bool default_value;
};

BASE_EXPORT void LogInvalidEnumValue(const Feature& feature,
                                     const std::string& param_name,
                                     const std::string& value_as_string,
                                     int default_value_as_int);

// Feature param declaration for an enum, with associated options. Example:
//
//     constexpr FeatureParam<ShapeEnum>::Option kShapeParamOptions[] = {
//         {SHAPE_CIRCLE, "circle"},
//         {SHAPE_CYLINDER, "cylinder"},
//         {SHAPE_PAPERCLIP, "paperclip"}};
//     constexpr FeatureParam<ShapeEnum> kAssistantShapeParam{
//         &kAssistantFeature, "shape", SHAPE_CIRCLE, &kShapeParamOptions};
//
// With this declaration, the parameter may be set to "circle", "cylinder", or
// "paperclip", and that will be translated to one of the three enum values. By
// default, or if the param is set to an unknown value, the parameter will be
// assumed to be SHAPE_CIRCLE.
template <typename Enum>
struct FeatureParam<Enum, true> {
  struct Option {
    constexpr Option(Enum value, const char* name) : value(value), name(name) {}

    const Enum value;
    const char* const name;
  };

  template <size_t option_count>
  constexpr FeatureParam(const Feature* feature,
                         const char* name,
                         const Enum default_value,
                         const Option (*options)[option_count])
      : feature(feature),
        name(name),
        default_value(default_value),
        options(*options),
        option_count(option_count) {
    static_assert(option_count >= 1, "FeatureParam<enum> has no options");
  }

  Enum Get() const {
    std::string value = GetFieldTrialParamValueByFeature(*feature, name);
    if (value.empty())
      return default_value;
    for (size_t i = 0; i < option_count; ++i) {
      if (value == options[i].name)
        return options[i].value;
    }
    LogInvalidEnumValue(*feature, name, value, static_cast<int>(default_value));
    return default_value;
  }

  // Returns the param-string for the given enum value.
  std::string GetName(Enum value) const {
    for (size_t i = 0; i < option_count; ++i) {
      if (value == options[i].value)
        return options[i].name;
    }
    NOTREACHED();
    return "";
  }

  const base::Feature* const feature;
  const char* const name;
  const Enum default_value;
  const Option* const options;
  const size_t option_count;
};

}  // namespace base

#endif  // BASE_METRICS_FIELD_TRIAL_PARAMS_H_
