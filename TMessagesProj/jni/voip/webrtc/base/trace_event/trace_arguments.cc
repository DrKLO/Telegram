// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/trace_event/trace_arguments.h"

#include <inttypes.h>
#include <stddef.h>
#include <stdio.h>
#include <string.h>

#include <cmath>

#include "base/json/string_escape.h"
#include "base/logging.h"
#include "base/stl_util.h"
#include "base/strings/string_number_conversions.h"
#include "base/strings/string_util.h"
#include "base/strings/stringprintf.h"
#include "base/strings/utf_string_conversions.h"

namespace base {
namespace trace_event {

namespace {

size_t GetAllocLength(const char* str) {
  return str ? strlen(str) + 1 : 0;
}

// Copies |*member| into |*buffer|, sets |*member| to point to this new
// location, and then advances |*buffer| by the amount written.
void CopyTraceEventParameter(char** buffer,
                             const char** member,
                             const char* end) {
  if (*member) {
    size_t written = strlcpy(*buffer, *member, end - *buffer) + 1;
    DCHECK_LE(static_cast<int>(written), end - *buffer);
    *member = *buffer;
    *buffer += written;
  }
}

// Append |val| as a JSON output value to |*out|.
void AppendDouble(double val, bool as_json, std::string* out) {
  // FIXME: base/json/json_writer.cc is using the same code,
  //        should be made into a common method.
  std::string real;
  if (std::isfinite(val)) {
    real = NumberToString(val);
    // Ensure that the number has a .0 if there's no decimal or 'e'.  This
    // makes sure that when we read the JSON back, it's interpreted as a
    // real rather than an int.
    if (real.find('.') == std::string::npos &&
        real.find('e') == std::string::npos &&
        real.find('E') == std::string::npos) {
      real.append(".0");
    }
    // The JSON spec requires that non-integer values in the range (-1,1)
    // have a zero before the decimal point - ".52" is not valid, "0.52" is.
    if (real[0] == '.') {
      real.insert(0, "0");
    } else if (real.length() > 1 && real[0] == '-' && real[1] == '.') {
      // "-.1" bad "-0.1" good
      real.insert(1, "0");
    }
  } else if (std::isnan(val)) {
    // The JSON spec doesn't allow NaN and Infinity (since these are
    // objects in EcmaScript).  Use strings instead.
    real = as_json ? "\"NaN\"" : "NaN";
  } else if (val < 0) {
    real = as_json ? "\"-Infinity\"" : "-Infinity";
  } else {
    real = as_json ? "\"Infinity\"" : "Infinity";
  }
  StringAppendF(out, "%s", real.c_str());
}

const char* TypeToString(char arg_type) {
  switch (arg_type) {
    case TRACE_VALUE_TYPE_INT:
      return "int";
    case TRACE_VALUE_TYPE_UINT:
      return "uint";
    case TRACE_VALUE_TYPE_DOUBLE:
      return "double";
    case TRACE_VALUE_TYPE_BOOL:
      return "bool";
    case TRACE_VALUE_TYPE_POINTER:
      return "pointer";
    case TRACE_VALUE_TYPE_STRING:
      return "string";
    case TRACE_VALUE_TYPE_COPY_STRING:
      return "copy_string";
    case TRACE_VALUE_TYPE_CONVERTABLE:
      return "convertable";
    default:
      NOTREACHED();
      return "UNKNOWN_TYPE";
  }
}

void AppendValueDebugString(const TraceArguments& args,
                            size_t idx,
                            std::string* out) {
  *out += (args.names()[idx] ? args.names()[idx] : "NULL_NAME");
  *out += "=";
  *out += TypeToString(args.types()[idx]);
  *out += "(";
  args.values()[idx].AppendAsJSON(args.types()[idx], out);
  *out += ")";
}

}  // namespace

void StringStorage::Reset(size_t alloc_size) {
  if (!alloc_size) {
    if (data_)
      ::free(data_);
    data_ = nullptr;
  } else if (!data_ || alloc_size != data_->size) {
    data_ = static_cast<Data*>(::realloc(data_, sizeof(size_t) + alloc_size));
    data_->size = alloc_size;
  }
}

bool StringStorage::Contains(const TraceArguments& args) const {
  for (size_t n = 0; n < args.size(); ++n) {
    if (args.types()[n] == TRACE_VALUE_TYPE_COPY_STRING &&
        !Contains(args.values()[n].as_string)) {
      return false;
    }
  }
  return true;
}

static_assert(
    std::is_pod<TraceValue>::value,
    "TraceValue must be plain-old-data type for performance reasons!");

void TraceValue::AppendAsJSON(unsigned char type, std::string* out) const {
  Append(type, true, out);
}

void TraceValue::AppendAsString(unsigned char type, std::string* out) const {
  Append(type, false, out);
}

void TraceValue::Append(unsigned char type,
                        bool as_json,
                        std::string* out) const {
  switch (type) {
    case TRACE_VALUE_TYPE_BOOL:
      *out += this->as_bool ? "true" : "false";
      break;
    case TRACE_VALUE_TYPE_UINT:
      StringAppendF(out, "%" PRIu64, static_cast<uint64_t>(this->as_uint));
      break;
    case TRACE_VALUE_TYPE_INT:
      StringAppendF(out, "%" PRId64, static_cast<int64_t>(this->as_int));
      break;
    case TRACE_VALUE_TYPE_DOUBLE:
      AppendDouble(this->as_double, as_json, out);
      break;
    case TRACE_VALUE_TYPE_POINTER: {
      // JSON only supports double and int numbers.
      // So as not to lose bits from a 64-bit pointer, output as a hex string.
      // For consistency, do the same for non-JSON strings, but without the
      // surrounding quotes.
      const char* format_string = as_json ? "\"0x%" PRIx64 "\"" : "0x%" PRIx64;
      StringAppendF(
          out, format_string,
          static_cast<uint64_t>(reinterpret_cast<uintptr_t>(this->as_pointer)));
    } break;
    case TRACE_VALUE_TYPE_STRING:
    case TRACE_VALUE_TYPE_COPY_STRING:
      if (as_json)
        EscapeJSONString(this->as_string ? this->as_string : "NULL", true, out);
      else
        *out += this->as_string ? this->as_string : "NULL";
      break;
    case TRACE_VALUE_TYPE_CONVERTABLE:
      this->as_convertable->AppendAsTraceFormat(out);
      break;
    default:
      NOTREACHED() << "Don't know how to print this value";
      break;
  }
}

TraceArguments& TraceArguments::operator=(TraceArguments&& other) noexcept {
  if (this != &other) {
    this->~TraceArguments();
    new (this) TraceArguments(std::move(other));
  }
  return *this;
}

TraceArguments::TraceArguments(int num_args,
                               const char* const* arg_names,
                               const unsigned char* arg_types,
                               const unsigned long long* arg_values) {
  if (num_args > static_cast<int>(kMaxSize))
    num_args = static_cast<int>(kMaxSize);

  size_ = static_cast<unsigned char>(num_args);
  for (size_t n = 0; n < size_; ++n) {
    types_[n] = arg_types[n];
    names_[n] = arg_names[n];
    values_[n].as_uint = arg_values[n];
  }
}

void TraceArguments::Reset() {
  for (size_t n = 0; n < size_; ++n) {
    if (types_[n] == TRACE_VALUE_TYPE_CONVERTABLE)
      delete values_[n].as_convertable;
  }
  size_ = 0;
}

void TraceArguments::CopyStringsTo(StringStorage* storage,
                                   bool copy_all_strings,
                                   const char** extra_string1,
                                   const char** extra_string2) {
  // First, compute total allocation size.
  size_t alloc_size = 0;

  if (copy_all_strings) {
    alloc_size +=
        GetAllocLength(*extra_string1) + GetAllocLength(*extra_string2);
    for (size_t n = 0; n < size_; ++n)
      alloc_size += GetAllocLength(names_[n]);
  }
  for (size_t n = 0; n < size_; ++n) {
    if (copy_all_strings && types_[n] == TRACE_VALUE_TYPE_STRING)
      types_[n] = TRACE_VALUE_TYPE_COPY_STRING;
    if (types_[n] == TRACE_VALUE_TYPE_COPY_STRING)
      alloc_size += GetAllocLength(values_[n].as_string);
  }

  if (alloc_size) {
    storage->Reset(alloc_size);
    char* ptr = storage->data();
    const char* end = ptr + alloc_size;
    if (copy_all_strings) {
      CopyTraceEventParameter(&ptr, extra_string1, end);
      CopyTraceEventParameter(&ptr, extra_string2, end);
      for (size_t n = 0; n < size_; ++n)
        CopyTraceEventParameter(&ptr, &names_[n], end);
    }
    for (size_t n = 0; n < size_; ++n) {
      if (types_[n] == TRACE_VALUE_TYPE_COPY_STRING)
        CopyTraceEventParameter(&ptr, &values_[n].as_string, end);
    }
#if DCHECK_IS_ON()
    DCHECK_EQ(end, ptr) << "Overrun by " << ptr - end;
    if (copy_all_strings) {
      if (extra_string1 && *extra_string1)
        DCHECK(storage->Contains(*extra_string1));
      if (extra_string2 && *extra_string2)
        DCHECK(storage->Contains(*extra_string2));
      for (size_t n = 0; n < size_; ++n)
        DCHECK(storage->Contains(names_[n]));
    }
    for (size_t n = 0; n < size_; ++n) {
      if (types_[n] == TRACE_VALUE_TYPE_COPY_STRING)
        DCHECK(storage->Contains(values_[n].as_string));
    }
#endif  // DCHECK_IS_ON()
  } else {
    storage->Reset();
  }
}

void TraceArguments::AppendDebugString(std::string* out) {
  *out += "TraceArguments(";
  for (size_t n = 0; n < size_; ++n) {
    if (n > 0)
      *out += ", ";
    AppendValueDebugString(*this, n, out);
  }
  *out += ")";
}

}  // namespace trace_event
}  // namespace base
