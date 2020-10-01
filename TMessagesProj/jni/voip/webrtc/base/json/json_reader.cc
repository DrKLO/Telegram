// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/json/json_reader.h"

#include <utility>
#include <vector>

#include "base/json/json_parser.h"
#include "base/logging.h"
#include "base/optional.h"

namespace base {

// Values 1000 and above are used by JSONFileValueSerializer::JsonFileError.
static_assert(JSONReader::JSON_PARSE_ERROR_COUNT < 1000,
              "JSONReader error out of bounds");

const char JSONReader::kInvalidEscape[] =
    "Invalid escape sequence.";
const char JSONReader::kSyntaxError[] =
    "Syntax error.";
const char JSONReader::kUnexpectedToken[] =
    "Unexpected token.";
const char JSONReader::kTrailingComma[] =
    "Trailing comma not allowed.";
const char JSONReader::kTooMuchNesting[] =
    "Too much nesting.";
const char JSONReader::kUnexpectedDataAfterRoot[] =
    "Unexpected data after root element.";
const char JSONReader::kUnsupportedEncoding[] =
    "Unsupported encoding. JSON must be UTF-8.";
const char JSONReader::kUnquotedDictionaryKey[] =
    "Dictionary keys must be quoted.";
const char JSONReader::kInputTooLarge[] =
    "Input string is too large (>2GB).";
const char JSONReader::kUnrepresentableNumber[] =
    "Number cannot be represented.";

JSONReader::ValueWithError::ValueWithError() = default;

JSONReader::ValueWithError::ValueWithError(ValueWithError&& other) = default;

JSONReader::ValueWithError::~ValueWithError() = default;

JSONReader::ValueWithError& JSONReader::ValueWithError::operator=(
    ValueWithError&& other) = default;

JSONReader::JSONReader(int options, size_t max_depth)
    : parser_(new internal::JSONParser(options, max_depth)) {}

JSONReader::~JSONReader() = default;

// static
Optional<Value> JSONReader::Read(StringPiece json,
                                 int options,
                                 size_t max_depth) {
  internal::JSONParser parser(options, max_depth);
  return parser.Parse(json);
}

std::unique_ptr<Value> JSONReader::ReadDeprecated(StringPiece json,
                                                  int options,
                                                  size_t max_depth) {
  Optional<Value> value = Read(json, options, max_depth);
  return value ? Value::ToUniquePtrValue(std::move(*value)) : nullptr;
}

// static
JSONReader::ValueWithError JSONReader::ReadAndReturnValueWithError(
    StringPiece json,
    int options) {
  ValueWithError ret;
  internal::JSONParser parser(options);
  ret.value = parser.Parse(json);
  if (!ret.value) {
    ret.error_message = parser.GetErrorMessage();
    ret.error_code = parser.error_code();
    ret.error_line = parser.error_line();
    ret.error_column = parser.error_column();
  }
  return ret;
}

// static
std::unique_ptr<Value> JSONReader::ReadAndReturnErrorDeprecated(
    StringPiece json,
    int options,
    int* error_code_out,
    std::string* error_msg_out,
    int* error_line_out,
    int* error_column_out) {
  ValueWithError ret = ReadAndReturnValueWithError(json, options);
  if (ret.value)
    return Value::ToUniquePtrValue(std::move(*ret.value));

  if (error_code_out)
    *error_code_out = ret.error_code;
  if (error_msg_out)
    *error_msg_out = ret.error_message;
  if (error_line_out)
    *error_line_out = ret.error_line;
  if (error_column_out)
    *error_column_out = ret.error_column;
  return nullptr;
}

// static
std::string JSONReader::ErrorCodeToString(JsonParseError error_code) {
  switch (error_code) {
    case JSON_NO_ERROR:
      return std::string();
    case JSON_INVALID_ESCAPE:
      return kInvalidEscape;
    case JSON_SYNTAX_ERROR:
      return kSyntaxError;
    case JSON_UNEXPECTED_TOKEN:
      return kUnexpectedToken;
    case JSON_TRAILING_COMMA:
      return kTrailingComma;
    case JSON_TOO_MUCH_NESTING:
      return kTooMuchNesting;
    case JSON_UNEXPECTED_DATA_AFTER_ROOT:
      return kUnexpectedDataAfterRoot;
    case JSON_UNSUPPORTED_ENCODING:
      return kUnsupportedEncoding;
    case JSON_UNQUOTED_DICTIONARY_KEY:
      return kUnquotedDictionaryKey;
    case JSON_TOO_LARGE:
      return kInputTooLarge;
    case JSON_UNREPRESENTABLE_NUMBER:
      return kUnrepresentableNumber;
    case JSON_PARSE_ERROR_COUNT:
      break;
  }
  NOTREACHED();
  return std::string();
}

Optional<Value> JSONReader::ReadToValue(StringPiece json) {
  return parser_->Parse(json);
}

std::unique_ptr<Value> JSONReader::ReadToValueDeprecated(StringPiece json) {
  Optional<Value> value = parser_->Parse(json);
  return value ? std::make_unique<Value>(std::move(*value)) : nullptr;
}

JSONReader::JsonParseError JSONReader::error_code() const {
  return parser_->error_code();
}

std::string JSONReader::GetErrorMessage() const {
  return parser_->GetErrorMessage();
}

}  // namespace base
