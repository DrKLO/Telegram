/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/strings/json.h"

#include <errno.h>
#include <limits.h>
#include <stdlib.h>

#include "rtc_base/string_encode.h"

namespace rtc {

bool GetStringFromJson(const Json::Value& in, std::string* out) {
  if (!in.isString()) {
    if (in.isBool()) {
      *out = rtc::ToString(in.asBool());
    } else if (in.isInt()) {
      *out = rtc::ToString(in.asInt());
    } else if (in.isUInt()) {
      *out = rtc::ToString(in.asUInt());
    } else if (in.isDouble()) {
      *out = rtc::ToString(in.asDouble());
    } else {
      return false;
    }
  } else {
    *out = in.asString();
  }
  return true;
}

bool GetIntFromJson(const Json::Value& in, int* out) {
  bool ret;
  if (!in.isString()) {
    ret = in.isConvertibleTo(Json::intValue);
    if (ret) {
      *out = in.asInt();
    }
  } else {
    long val;  // NOLINT
    const char* c_str = in.asCString();
    char* end_ptr;
    errno = 0;
    val = strtol(c_str, &end_ptr, 10);  // NOLINT
    ret = (end_ptr != c_str && *end_ptr == '\0' && !errno && val >= INT_MIN &&
           val <= INT_MAX);
    *out = val;
  }
  return ret;
}

bool GetUIntFromJson(const Json::Value& in, unsigned int* out) {
  bool ret;
  if (!in.isString()) {
    ret = in.isConvertibleTo(Json::uintValue);
    if (ret) {
      *out = in.asUInt();
    }
  } else {
    unsigned long val;  // NOLINT
    const char* c_str = in.asCString();
    char* end_ptr;
    errno = 0;
    val = strtoul(c_str, &end_ptr, 10);  // NOLINT
    ret = (end_ptr != c_str && *end_ptr == '\0' && !errno && val <= UINT_MAX);
    *out = val;
  }
  return ret;
}

bool GetBoolFromJson(const Json::Value& in, bool* out) {
  bool ret;
  if (!in.isString()) {
    ret = in.isConvertibleTo(Json::booleanValue);
    if (ret) {
      *out = in.asBool();
    }
  } else {
    if (in.asString() == "true") {
      *out = true;
      ret = true;
    } else if (in.asString() == "false") {
      *out = false;
      ret = true;
    } else {
      ret = false;
    }
  }
  return ret;
}

bool GetDoubleFromJson(const Json::Value& in, double* out) {
  bool ret;
  if (!in.isString()) {
    ret = in.isConvertibleTo(Json::realValue);
    if (ret) {
      *out = in.asDouble();
    }
  } else {
    double val;
    const char* c_str = in.asCString();
    char* end_ptr;
    errno = 0;
    val = strtod(c_str, &end_ptr);
    ret = (end_ptr != c_str && *end_ptr == '\0' && !errno);
    *out = val;
  }
  return ret;
}

namespace {
template <typename T>
bool JsonArrayToVector(const Json::Value& value,
                       bool (*getter)(const Json::Value& in, T* out),
                       std::vector<T>* vec) {
  vec->clear();
  if (!value.isArray()) {
    return false;
  }

  for (Json::Value::ArrayIndex i = 0; i < value.size(); ++i) {
    T val;
    if (!getter(value[i], &val)) {
      return false;
    }
    vec->push_back(val);
  }

  return true;
}
// Trivial getter helper
bool GetValueFromJson(const Json::Value& in, Json::Value* out) {
  *out = in;
  return true;
}
}  // unnamed namespace

bool JsonArrayToValueVector(const Json::Value& in,
                            std::vector<Json::Value>* out) {
  return JsonArrayToVector(in, GetValueFromJson, out);
}

bool JsonArrayToIntVector(const Json::Value& in, std::vector<int>* out) {
  return JsonArrayToVector(in, GetIntFromJson, out);
}

bool JsonArrayToUIntVector(const Json::Value& in,
                           std::vector<unsigned int>* out) {
  return JsonArrayToVector(in, GetUIntFromJson, out);
}

bool JsonArrayToStringVector(const Json::Value& in,
                             std::vector<std::string>* out) {
  return JsonArrayToVector(in, GetStringFromJson, out);
}

bool JsonArrayToBoolVector(const Json::Value& in, std::vector<bool>* out) {
  return JsonArrayToVector(in, GetBoolFromJson, out);
}

bool JsonArrayToDoubleVector(const Json::Value& in, std::vector<double>* out) {
  return JsonArrayToVector(in, GetDoubleFromJson, out);
}

namespace {
template <typename T>
Json::Value VectorToJsonArray(const std::vector<T>& vec) {
  Json::Value result(Json::arrayValue);
  for (size_t i = 0; i < vec.size(); ++i) {
    result.append(Json::Value(vec[i]));
  }
  return result;
}
}  // unnamed namespace

Json::Value ValueVectorToJsonArray(const std::vector<Json::Value>& in) {
  return VectorToJsonArray(in);
}

Json::Value IntVectorToJsonArray(const std::vector<int>& in) {
  return VectorToJsonArray(in);
}

Json::Value UIntVectorToJsonArray(const std::vector<unsigned int>& in) {
  return VectorToJsonArray(in);
}

Json::Value StringVectorToJsonArray(const std::vector<std::string>& in) {
  return VectorToJsonArray(in);
}

Json::Value BoolVectorToJsonArray(const std::vector<bool>& in) {
  return VectorToJsonArray(in);
}

Json::Value DoubleVectorToJsonArray(const std::vector<double>& in) {
  return VectorToJsonArray(in);
}

bool GetValueFromJsonArray(const Json::Value& in, size_t n, Json::Value* out) {
  if (!in.isArray() || !in.isValidIndex(static_cast<int>(n))) {
    return false;
  }

  *out = in[static_cast<Json::Value::ArrayIndex>(n)];
  return true;
}

bool GetIntFromJsonArray(const Json::Value& in, size_t n, int* out) {
  Json::Value x;
  return GetValueFromJsonArray(in, n, &x) && GetIntFromJson(x, out);
}

bool GetUIntFromJsonArray(const Json::Value& in, size_t n, unsigned int* out) {
  Json::Value x;
  return GetValueFromJsonArray(in, n, &x) && GetUIntFromJson(x, out);
}

bool GetStringFromJsonArray(const Json::Value& in, size_t n, std::string* out) {
  Json::Value x;
  return GetValueFromJsonArray(in, n, &x) && GetStringFromJson(x, out);
}

bool GetBoolFromJsonArray(const Json::Value& in, size_t n, bool* out) {
  Json::Value x;
  return GetValueFromJsonArray(in, n, &x) && GetBoolFromJson(x, out);
}

bool GetDoubleFromJsonArray(const Json::Value& in, size_t n, double* out) {
  Json::Value x;
  return GetValueFromJsonArray(in, n, &x) && GetDoubleFromJson(x, out);
}

bool GetValueFromJsonObject(const Json::Value& in,
                            const std::string& k,
                            Json::Value* out) {
  if (!in.isObject() || !in.isMember(k)) {
    return false;
  }

  *out = in[k];
  return true;
}

bool GetIntFromJsonObject(const Json::Value& in,
                          const std::string& k,
                          int* out) {
  Json::Value x;
  return GetValueFromJsonObject(in, k, &x) && GetIntFromJson(x, out);
}

bool GetUIntFromJsonObject(const Json::Value& in,
                           const std::string& k,
                           unsigned int* out) {
  Json::Value x;
  return GetValueFromJsonObject(in, k, &x) && GetUIntFromJson(x, out);
}

bool GetStringFromJsonObject(const Json::Value& in,
                             const std::string& k,
                             std::string* out) {
  Json::Value x;
  return GetValueFromJsonObject(in, k, &x) && GetStringFromJson(x, out);
}

bool GetBoolFromJsonObject(const Json::Value& in,
                           const std::string& k,
                           bool* out) {
  Json::Value x;
  return GetValueFromJsonObject(in, k, &x) && GetBoolFromJson(x, out);
}

bool GetDoubleFromJsonObject(const Json::Value& in,
                             const std::string& k,
                             double* out) {
  Json::Value x;
  return GetValueFromJsonObject(in, k, &x) && GetDoubleFromJson(x, out);
}

std::string JsonValueToString(const Json::Value& json) {
  Json::FastWriter w;
  std::string value = w.write(json);
  return value.substr(0, value.size() - 1);  // trim trailing newline
}

}  // namespace rtc
