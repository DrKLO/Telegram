/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef RTC_BASE_EXPERIMENTS_FIELD_TRIAL_PARSER_H_
#define RTC_BASE_EXPERIMENTS_FIELD_TRIAL_PARSER_H_

#include <stdint.h>

#include <initializer_list>
#include <map>
#include <set>
#include <string>
#include <vector>

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"

// Field trial parser functionality. Provides funcitonality to parse field trial
// argument strings in key:value format. Each parameter is described using
// key:value, parameters are separated with a ,. Values can't include the comma
// character, since there's no quote facility. For most types, white space is
// ignored. Parameters are declared with a given type for which an
// implementation of ParseTypedParameter should be provided. The
// ParseTypedParameter implementation is given whatever is between the : and the
// ,. If the key is provided without : a FieldTrialOptional will use nullopt.

// Example string: "my_optional,my_int:3,my_string:hello"

// For further description of usage and behavior, see the examples in the unit
// tests.

namespace webrtc {
class FieldTrialParameterInterface {
 public:
  virtual ~FieldTrialParameterInterface();
  std::string key() const { return key_; }

 protected:
  // Protected to allow implementations to provide assignment and copy.
  FieldTrialParameterInterface(const FieldTrialParameterInterface&) = default;
  FieldTrialParameterInterface& operator=(const FieldTrialParameterInterface&) =
      default;
  explicit FieldTrialParameterInterface(std::string key);
  friend void ParseFieldTrial(
      std::initializer_list<FieldTrialParameterInterface*> fields,
      absl::string_view trial_string);
  void MarkAsUsed() { used_ = true; }
  virtual bool Parse(absl::optional<std::string> str_value) = 0;

  virtual void ParseDone() {}

  std::vector<FieldTrialParameterInterface*> sub_parameters_;

 private:
  std::string key_;
  bool used_ = false;
};

// ParseFieldTrial function parses the given string and fills the given fields
// with extracted values if available.
void ParseFieldTrial(
    std::initializer_list<FieldTrialParameterInterface*> fields,
    absl::string_view trial_string);

// Specialize this in code file for custom types. Should return absl::nullopt if
// the given string cannot be properly parsed.
template <typename T>
absl::optional<T> ParseTypedParameter(std::string);

// This class uses the ParseTypedParameter function to implement a parameter
// implementation with an enforced default value.
template <typename T>
class FieldTrialParameter : public FieldTrialParameterInterface {
 public:
  FieldTrialParameter(std::string key, T default_value)
      : FieldTrialParameterInterface(key), value_(default_value) {}
  T Get() const { return value_; }
  operator T() const { return Get(); }
  const T* operator->() const { return &value_; }

  void SetForTest(T value) { value_ = value; }

 protected:
  bool Parse(absl::optional<std::string> str_value) override {
    if (str_value) {
      absl::optional<T> value = ParseTypedParameter<T>(*str_value);
      if (value.has_value()) {
        value_ = value.value();
        return true;
      }
    }
    return false;
  }

 private:
  T value_;
};

// This class uses the ParseTypedParameter function to implement a parameter
// implementation with an enforced default value and a range constraint. Values
// outside the configured range will be ignored.
template <typename T>
class FieldTrialConstrained : public FieldTrialParameterInterface {
 public:
  FieldTrialConstrained(std::string key,
                        T default_value,
                        absl::optional<T> lower_limit,
                        absl::optional<T> upper_limit)
      : FieldTrialParameterInterface(key),
        value_(default_value),
        lower_limit_(lower_limit),
        upper_limit_(upper_limit) {}
  T Get() const { return value_; }
  operator T() const { return Get(); }
  const T* operator->() const { return &value_; }

 protected:
  bool Parse(absl::optional<std::string> str_value) override {
    if (str_value) {
      absl::optional<T> value = ParseTypedParameter<T>(*str_value);
      if (value && (!lower_limit_ || *value >= *lower_limit_) &&
          (!upper_limit_ || *value <= *upper_limit_)) {
        value_ = *value;
        return true;
      }
    }
    return false;
  }

 private:
  T value_;
  absl::optional<T> lower_limit_;
  absl::optional<T> upper_limit_;
};

class AbstractFieldTrialEnum : public FieldTrialParameterInterface {
 public:
  AbstractFieldTrialEnum(std::string key,
                         int default_value,
                         std::map<std::string, int> mapping);
  ~AbstractFieldTrialEnum() override;
  AbstractFieldTrialEnum(const AbstractFieldTrialEnum&);

 protected:
  bool Parse(absl::optional<std::string> str_value) override;

 protected:
  int value_;
  std::map<std::string, int> enum_mapping_;
  std::set<int> valid_values_;
};

// The FieldTrialEnum class can be used to quickly define a parser for a
// specific enum. It handles values provided as integers and as strings if a
// mapping is provided.
template <typename T>
class FieldTrialEnum : public AbstractFieldTrialEnum {
 public:
  FieldTrialEnum(std::string key,
                 T default_value,
                 std::map<std::string, T> mapping)
      : AbstractFieldTrialEnum(key,
                               static_cast<int>(default_value),
                               ToIntMap(mapping)) {}
  T Get() const { return static_cast<T>(value_); }
  operator T() const { return Get(); }

 private:
  static std::map<std::string, int> ToIntMap(std::map<std::string, T> mapping) {
    std::map<std::string, int> res;
    for (const auto& it : mapping)
      res[it.first] = static_cast<int>(it.second);
    return res;
  }
};

// This class uses the ParseTypedParameter function to implement an optional
// parameter implementation that can default to absl::nullopt.
template <typename T>
class FieldTrialOptional : public FieldTrialParameterInterface {
 public:
  explicit FieldTrialOptional(std::string key)
      : FieldTrialParameterInterface(key) {}
  FieldTrialOptional(std::string key, absl::optional<T> default_value)
      : FieldTrialParameterInterface(key), value_(default_value) {}
  absl::optional<T> GetOptional() const { return value_; }
  const T& Value() const { return value_.value(); }
  const T& operator*() const { return value_.value(); }
  const T* operator->() const { return &value_.value(); }
  explicit operator bool() const { return value_.has_value(); }

 protected:
  bool Parse(absl::optional<std::string> str_value) override {
    if (str_value) {
      absl::optional<T> value = ParseTypedParameter<T>(*str_value);
      if (!value.has_value())
        return false;
      value_ = value.value();
    } else {
      value_ = absl::nullopt;
    }
    return true;
  }

 private:
  absl::optional<T> value_;
};

// Equivalent to a FieldTrialParameter<bool> in the case that both key and value
// are present. If key is missing, evaluates to false. If key is present, but no
// explicit value is provided, the flag evaluates to true.
class FieldTrialFlag : public FieldTrialParameterInterface {
 public:
  explicit FieldTrialFlag(std::string key);
  FieldTrialFlag(std::string key, bool default_value);
  bool Get() const;
  operator bool() const;

 protected:
  bool Parse(absl::optional<std::string> str_value) override;

 private:
  bool value_;
};

template <typename T>
absl::optional<absl::optional<T>> ParseOptionalParameter(std::string str) {
  if (str.empty())
    return absl::optional<T>();
  auto parsed = ParseTypedParameter<T>(str);
  if (parsed.has_value())
    return parsed;
  return absl::nullopt;
}

template <>
absl::optional<bool> ParseTypedParameter<bool>(std::string str);
template <>
absl::optional<double> ParseTypedParameter<double>(std::string str);
template <>
absl::optional<int> ParseTypedParameter<int>(std::string str);
template <>
absl::optional<unsigned> ParseTypedParameter<unsigned>(std::string str);
template <>
absl::optional<std::string> ParseTypedParameter<std::string>(std::string str);

template <>
absl::optional<absl::optional<bool>> ParseTypedParameter<absl::optional<bool>>(
    std::string str);
template <>
absl::optional<absl::optional<int>> ParseTypedParameter<absl::optional<int>>(
    std::string str);
template <>
absl::optional<absl::optional<unsigned>>
ParseTypedParameter<absl::optional<unsigned>>(std::string str);
template <>
absl::optional<absl::optional<double>>
ParseTypedParameter<absl::optional<double>>(std::string str);

// Accepts true, false, else parsed with sscanf %i, true if != 0.
extern template class FieldTrialParameter<bool>;
// Interpreted using sscanf %lf.
extern template class FieldTrialParameter<double>;
// Interpreted using sscanf %i.
extern template class FieldTrialParameter<int>;
// Interpreted using sscanf %u.
extern template class FieldTrialParameter<unsigned>;
// Using the given value as is.
extern template class FieldTrialParameter<std::string>;

extern template class FieldTrialConstrained<double>;
extern template class FieldTrialConstrained<int>;
extern template class FieldTrialConstrained<unsigned>;

extern template class FieldTrialOptional<double>;
extern template class FieldTrialOptional<int>;
extern template class FieldTrialOptional<unsigned>;
extern template class FieldTrialOptional<bool>;
extern template class FieldTrialOptional<std::string>;

}  // namespace webrtc

#endif  // RTC_BASE_EXPERIMENTS_FIELD_TRIAL_PARSER_H_
