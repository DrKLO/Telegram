// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/values.h"

#include <string.h>

#include <algorithm>
#include <cmath>
#include <new>
#include <ostream>
#include <utility>

#include "base/bit_cast.h"
#include "base/containers/checked_iterators.h"
#include "base/json/json_writer.h"
#include "base/logging.h"
#include "base/memory/ptr_util.h"
#include "base/stl_util.h"
#include "base/strings/string_util.h"
#include "base/strings/utf_string_conversions.h"
#include "base/trace_event/memory_usage_estimator.h"

namespace base {

// base::Value must be standard layout to guarantee that writing to
// |bool_type_| then reading |type_| is defined behaviour. See:
//
// [class.union]:
//   If a standard-layout union contains several standard-layout structs that
//   share a common initial sequence (9.2), and if an object of this
//   standard-layout union type contains one of the standard-layout structs,
//   it is permitted to inspect the common initial sequence of any of
//   standard-layout struct members;
//
static_assert(std::is_standard_layout<Value>::value,
              "base::Value should be a standard-layout C++ class in order "
              "to avoid undefined behaviour in its implementation!");

static_assert(sizeof(Value::DoubleStorage) == sizeof(double),
              "The double and DoubleStorage types should have the same size");

namespace {

const char* const kTypeNames[] = {"null",   "boolean", "integer",    "double",
                                  "string", "binary",  "dictionary", "list"};
static_assert(base::size(kTypeNames) ==
                  static_cast<size_t>(Value::Type::LIST) + 1,
              "kTypeNames Has Wrong Size");

std::unique_ptr<Value> CopyWithoutEmptyChildren(const Value& node);

// Make a deep copy of |node|, but don't include empty lists or dictionaries
// in the copy. It's possible for this function to return NULL and it
// expects |node| to always be non-NULL.
std::unique_ptr<Value> CopyListWithoutEmptyChildren(const Value& list) {
  Value copy(Value::Type::LIST);
  for (const auto& entry : list.GetList()) {
    std::unique_ptr<Value> child_copy = CopyWithoutEmptyChildren(entry);
    if (child_copy)
      copy.Append(std::move(*child_copy));
  }
  return copy.GetList().empty() ? nullptr
                                : std::make_unique<Value>(std::move(copy));
}

std::unique_ptr<DictionaryValue> CopyDictionaryWithoutEmptyChildren(
    const DictionaryValue& dict) {
  std::unique_ptr<DictionaryValue> copy;
  for (const auto& it : dict.DictItems()) {
    std::unique_ptr<Value> child_copy = CopyWithoutEmptyChildren(it.second);
    if (child_copy) {
      if (!copy)
        copy = std::make_unique<DictionaryValue>();
      copy->SetKey(it.first, std::move(*child_copy));
    }
  }
  return copy;
}

std::unique_ptr<Value> CopyWithoutEmptyChildren(const Value& node) {
  switch (node.type()) {
    case Value::Type::LIST:
      return CopyListWithoutEmptyChildren(static_cast<const ListValue&>(node));

    case Value::Type::DICTIONARY:
      return CopyDictionaryWithoutEmptyChildren(
          static_cast<const DictionaryValue&>(node));

    default:
      return std::make_unique<Value>(node.Clone());
  }
}

// Helper class to enumerate the path components from a StringPiece
// without performing heap allocations. Components are simply separated
// by single dots (e.g. "foo.bar.baz"  -> ["foo", "bar", "baz"]).
//
// Usage example:
//    PathSplitter splitter(some_path);
//    while (splitter.HasNext()) {
//       StringPiece component = splitter.Next();
//       ...
//    }
//
class PathSplitter {
 public:
  explicit PathSplitter(StringPiece path) : path_(path) {}

  bool HasNext() const { return pos_ < path_.size(); }

  StringPiece Next() {
    DCHECK(HasNext());
    size_t start = pos_;
    size_t pos = path_.find('.', start);
    size_t end;
    if (pos == path_.npos) {
      end = path_.size();
      pos_ = end;
    } else {
      end = pos;
      pos_ = pos + 1;
    }
    return path_.substr(start, end - start);
  }

 private:
  StringPiece path_;
  size_t pos_ = 0;
};

}  // namespace

// static
std::unique_ptr<Value> Value::CreateWithCopiedBuffer(const char* buffer,
                                                     size_t size) {
  return std::make_unique<Value>(BlobStorage(buffer, buffer + size));
}

// static
Value Value::FromUniquePtrValue(std::unique_ptr<Value> val) {
  return std::move(*val);
}

// static
std::unique_ptr<Value> Value::ToUniquePtrValue(Value val) {
  return std::make_unique<Value>(std::move(val));
}

// static
const DictionaryValue& Value::AsDictionaryValue(const Value& val) {
  CHECK(val.is_dict());
  return static_cast<const DictionaryValue&>(val);
}

// static
const ListValue& Value::AsListValue(const Value& val) {
  CHECK(val.is_list());
  return static_cast<const ListValue&>(val);
}

Value::Value(Value&& that) noexcept {
  InternalMoveConstructFrom(std::move(that));
}

Value::Value(Type type) : type_(type) {
  // Initialize with the default value.
  switch (type_) {
    case Type::NONE:
      return;

    case Type::BOOLEAN:
      bool_value_ = false;
      return;
    case Type::INTEGER:
      int_value_ = 0;
      return;
    case Type::DOUBLE:
      double_value_ = bit_cast<DoubleStorage>(0.0);
      return;
    case Type::STRING:
      new (&string_value_) std::string();
      return;
    case Type::BINARY:
      new (&binary_value_) BlobStorage();
      return;
    case Type::DICTIONARY:
      new (&dict_) DictStorage();
      return;
    case Type::LIST:
      new (&list_) ListStorage();
      return;
    // TODO(crbug.com/859477): Remove after root cause is found.
    case Type::DEAD:
      CHECK(false);
      return;
  }

  // TODO(crbug.com/859477): Revert to NOTREACHED() after root cause is found.
  CHECK(false);
}

Value::Value(bool in_bool) : type_(Type::BOOLEAN), bool_value_(in_bool) {}

Value::Value(int in_int) : type_(Type::INTEGER), int_value_(in_int) {}

Value::Value(double in_double)
    : type_(Type::DOUBLE), double_value_(bit_cast<DoubleStorage>(in_double)) {
  if (!std::isfinite(in_double)) {
    NOTREACHED() << "Non-finite (i.e. NaN or positive/negative infinity) "
                 << "values cannot be represented in JSON";
    double_value_ = bit_cast<DoubleStorage>(0.0);
  }
}

Value::Value(const char* in_string) : Value(std::string(in_string)) {}

Value::Value(StringPiece in_string) : Value(std::string(in_string)) {}

Value::Value(std::string&& in_string) noexcept
    : type_(Type::STRING), string_value_(std::move(in_string)) {
  DCHECK(IsStringUTF8AllowingNoncharacters(string_value_));
}

Value::Value(const char16* in_string16) : Value(StringPiece16(in_string16)) {}

Value::Value(StringPiece16 in_string16) : Value(UTF16ToUTF8(in_string16)) {}

Value::Value(const std::vector<char>& in_blob)
    : type_(Type::BINARY), binary_value_(in_blob.begin(), in_blob.end()) {}

Value::Value(base::span<const uint8_t> in_blob)
    : type_(Type::BINARY), binary_value_(in_blob.begin(), in_blob.end()) {}

Value::Value(BlobStorage&& in_blob) noexcept
    : type_(Type::BINARY), binary_value_(std::move(in_blob)) {}

Value::Value(const DictStorage& in_dict) : type_(Type::DICTIONARY), dict_() {
  dict_.reserve(in_dict.size());
  for (const auto& it : in_dict) {
    dict_.try_emplace(dict_.end(), it.first,
                      std::make_unique<Value>(it.second->Clone()));
  }
}

Value::Value(DictStorage&& in_dict) noexcept
    : type_(Type::DICTIONARY), dict_(std::move(in_dict)) {}

Value::Value(span<const Value> in_list) : type_(Type::LIST), list_() {
  list_.reserve(in_list.size());
  for (const auto& val : in_list)
    list_.emplace_back(val.Clone());
}

Value::Value(ListStorage&& in_list) noexcept
    : type_(Type::LIST), list_(std::move(in_list)) {}

Value& Value::operator=(Value&& that) noexcept {
  InternalCleanup();
  InternalMoveConstructFrom(std::move(that));

  return *this;
}

double Value::AsDoubleInternal() const {
  return bit_cast<double>(double_value_);
}

Value Value::Clone() const {
  switch (type_) {
    case Type::NONE:
      return Value();
    case Type::BOOLEAN:
      return Value(bool_value_);
    case Type::INTEGER:
      return Value(int_value_);
    case Type::DOUBLE:
      return Value(AsDoubleInternal());
    case Type::STRING:
      return Value(string_value_);
    case Type::BINARY:
      return Value(binary_value_);
    case Type::DICTIONARY:
      return Value(dict_);
    case Type::LIST:
      return Value(list_);
      // TODO(crbug.com/859477): Remove after root cause is found.
    case Type::DEAD:
      CHECK(false);
      return Value();
  }

  // TODO(crbug.com/859477): Revert to NOTREACHED() after root cause is found.
  CHECK(false);
  return Value();
}

Value::~Value() {
  InternalCleanup();
  // TODO(crbug.com/859477): Remove after root cause is found.
  type_ = Type::DEAD;
}

// static
const char* Value::GetTypeName(Value::Type type) {
  DCHECK_GE(static_cast<int>(type), 0);
  DCHECK_LT(static_cast<size_t>(type), base::size(kTypeNames));
  return kTypeNames[static_cast<size_t>(type)];
}

bool Value::GetBool() const {
  CHECK(is_bool());
  return bool_value_;
}

int Value::GetInt() const {
  CHECK(is_int());
  return int_value_;
}

double Value::GetDouble() const {
  if (is_double())
    return AsDoubleInternal();
  if (is_int())
    return int_value_;
  CHECK(false);
  return 0.0;
}

const std::string& Value::GetString() const {
  CHECK(is_string());
  return string_value_;
}

std::string& Value::GetString() {
  CHECK(is_string());
  return string_value_;
}

const Value::BlobStorage& Value::GetBlob() const {
  CHECK(is_blob());
  return binary_value_;
}

Value::ListView Value::GetList() {
  CHECK(is_list());
  return list_;
}

Value::ConstListView Value::GetList() const {
  CHECK(is_list());
  return list_;
}

Value::ListStorage Value::TakeList() {
  CHECK(is_list());
  return std::exchange(list_, ListStorage());
}

void Value::Append(bool value) {
  CHECK(is_list());
  list_.emplace_back(value);
}

void Value::Append(int value) {
  CHECK(is_list());
  list_.emplace_back(value);
}

void Value::Append(double value) {
  CHECK(is_list());
  list_.emplace_back(value);
}

void Value::Append(const char* value) {
  CHECK(is_list());
  list_.emplace_back(value);
}

void Value::Append(StringPiece value) {
  CHECK(is_list());
  list_.emplace_back(value);
}

void Value::Append(std::string&& value) {
  CHECK(is_list());
  list_.emplace_back(std::move(value));
}

void Value::Append(const char16* value) {
  CHECK(is_list());
  list_.emplace_back(value);
}

void Value::Append(StringPiece16 value) {
  CHECK(is_list());
  list_.emplace_back(value);
}

void Value::Append(Value&& value) {
  CHECK(is_list());
  list_.emplace_back(std::move(value));
}

CheckedContiguousIterator<Value> Value::Insert(
    CheckedContiguousConstIterator<Value> pos,
    Value&& value) {
  CHECK(is_list());
  const auto offset = pos - make_span(list_).begin();
  list_.insert(list_.begin() + offset, std::move(value));
  return make_span(list_).begin() + offset;
}

bool Value::EraseListIter(CheckedContiguousConstIterator<Value> iter) {
  CHECK(is_list());
  const auto offset = iter - ListView(list_).begin();
  auto list_iter = list_.begin() + offset;
  if (list_iter == list_.end())
    return false;

  list_.erase(list_iter);
  return true;
}

size_t Value::EraseListValue(const Value& val) {
  return EraseListValueIf([&val](const Value& other) { return val == other; });
}

void Value::ClearList() {
  CHECK(is_list());
  list_.clear();
}

Value* Value::FindKey(StringPiece key) {
  return const_cast<Value*>(as_const(*this).FindKey(key));
}

const Value* Value::FindKey(StringPiece key) const {
  CHECK(is_dict());
  auto found = dict_.find(key);
  if (found == dict_.end())
    return nullptr;
  return found->second.get();
}

Value* Value::FindKeyOfType(StringPiece key, Type type) {
  return const_cast<Value*>(as_const(*this).FindKeyOfType(key, type));
}

const Value* Value::FindKeyOfType(StringPiece key, Type type) const {
  const Value* result = FindKey(key);
  if (!result || result->type() != type)
    return nullptr;
  return result;
}

base::Optional<bool> Value::FindBoolKey(StringPiece key) const {
  const Value* result = FindKeyOfType(key, Type::BOOLEAN);
  return result ? base::make_optional(result->bool_value_) : base::nullopt;
}

base::Optional<int> Value::FindIntKey(StringPiece key) const {
  const Value* result = FindKeyOfType(key, Type::INTEGER);
  return result ? base::make_optional(result->int_value_) : base::nullopt;
}

base::Optional<double> Value::FindDoubleKey(StringPiece key) const {
  const Value* result = FindKey(key);
  if (result) {
    if (result->is_int())
      return static_cast<double>(result->int_value_);
    if (result->is_double()) {
      return result->AsDoubleInternal();
    }
  }
  return base::nullopt;
}

const std::string* Value::FindStringKey(StringPiece key) const {
  const Value* result = FindKeyOfType(key, Type::STRING);
  return result ? &result->string_value_ : nullptr;
}

std::string* Value::FindStringKey(StringPiece key) {
  Value* result = FindKeyOfType(key, Type::STRING);
  return result ? &result->string_value_ : nullptr;
}

const Value::BlobStorage* Value::FindBlobKey(StringPiece key) const {
  const Value* value = FindKeyOfType(key, Type::BINARY);
  return value ? &value->binary_value_ : nullptr;
}

const Value* Value::FindDictKey(StringPiece key) const {
  return FindKeyOfType(key, Type::DICTIONARY);
}

Value* Value::FindDictKey(StringPiece key) {
  return FindKeyOfType(key, Type::DICTIONARY);
}

const Value* Value::FindListKey(StringPiece key) const {
  return FindKeyOfType(key, Type::LIST);
}

Value* Value::FindListKey(StringPiece key) {
  return FindKeyOfType(key, Type::LIST);
}

Value* Value::SetKey(StringPiece key, Value&& value) {
  return SetKeyInternal(key, std::make_unique<Value>(std::move(value)));
}

Value* Value::SetKey(std::string&& key, Value&& value) {
  CHECK(is_dict());
  return dict_
      .insert_or_assign(std::move(key),
                        std::make_unique<Value>(std::move(value)))
      .first->second.get();
}

Value* Value::SetKey(const char* key, Value&& value) {
  return SetKeyInternal(key, std::make_unique<Value>(std::move(value)));
}

Value* Value::SetBoolKey(StringPiece key, bool value) {
  return SetKeyInternal(key, std::make_unique<Value>(value));
}

Value* Value::SetIntKey(StringPiece key, int value) {
  return SetKeyInternal(key, std::make_unique<Value>(value));
}

Value* Value::SetDoubleKey(StringPiece key, double value) {
  return SetKeyInternal(key, std::make_unique<Value>(value));
}

Value* Value::SetStringKey(StringPiece key, StringPiece value) {
  return SetKeyInternal(key, std::make_unique<Value>(value));
}

Value* Value::SetStringKey(StringPiece key, const char* value) {
  return SetKeyInternal(key, std::make_unique<Value>(value));
}

Value* Value::SetStringKey(StringPiece key, std::string&& value) {
  return SetKeyInternal(key, std::make_unique<Value>(std::move(value)));
}

Value* Value::SetStringKey(StringPiece key, StringPiece16 value) {
  return SetKeyInternal(key, std::make_unique<Value>(value));
}

bool Value::RemoveKey(StringPiece key) {
  CHECK(is_dict());
  return dict_.erase(key) != 0;
}

Optional<Value> Value::ExtractKey(StringPiece key) {
  CHECK(is_dict());
  auto found = dict_.find(key);
  if (found == dict_.end())
    return nullopt;

  Value value = std::move(*found->second);
  dict_.erase(found);
  return std::move(value);
}

Value* Value::FindPath(StringPiece path) {
  return const_cast<Value*>(as_const(*this).FindPath(path));
}

const Value* Value::FindPath(StringPiece path) const {
  CHECK(is_dict());
  const Value* cur = this;
  PathSplitter splitter(path);
  while (splitter.HasNext()) {
    if (!cur->is_dict() || (cur = cur->FindKey(splitter.Next())) == nullptr)
      return nullptr;
  }
  return cur;
}

Value* Value::FindPathOfType(StringPiece path, Type type) {
  return const_cast<Value*>(as_const(*this).FindPathOfType(path, type));
}

const Value* Value::FindPathOfType(StringPiece path, Type type) const {
  const Value* cur = FindPath(path);
  if (!cur || cur->type() != type)
    return nullptr;
  return cur;
}

base::Optional<bool> Value::FindBoolPath(StringPiece path) const {
  const Value* cur = FindPath(path);
  if (!cur || !cur->is_bool())
    return base::nullopt;
  return cur->bool_value_;
}

base::Optional<int> Value::FindIntPath(StringPiece path) const {
  const Value* cur = FindPath(path);
  if (!cur || !cur->is_int())
    return base::nullopt;
  return cur->int_value_;
}

base::Optional<double> Value::FindDoublePath(StringPiece path) const {
  const Value* cur = FindPath(path);
  if (cur) {
    if (cur->is_int())
      return static_cast<double>(cur->int_value_);
    if (cur->is_double())
      return cur->AsDoubleInternal();
  }
  return base::nullopt;
}

const std::string* Value::FindStringPath(StringPiece path) const {
  const Value* cur = FindPath(path);
  if (!cur || !cur->is_string())
    return nullptr;
  return &cur->string_value_;
}

std::string* Value::FindStringPath(StringPiece path) {
  return const_cast<std::string*>(as_const(*this).FindStringPath(path));
}

const Value::BlobStorage* Value::FindBlobPath(StringPiece path) const {
  const Value* cur = FindPath(path);
  if (!cur || !cur->is_blob())
    return nullptr;
  return &cur->binary_value_;
}

const Value* Value::FindDictPath(StringPiece path) const {
  return FindPathOfType(path, Type::DICTIONARY);
}

Value* Value::FindDictPath(StringPiece path) {
  return FindPathOfType(path, Type::DICTIONARY);
}

const Value* Value::FindListPath(StringPiece path) const {
  return FindPathOfType(path, Type::LIST);
}

Value* Value::FindListPath(StringPiece path) {
  return FindPathOfType(path, Type::LIST);
}

Value* Value::SetPath(StringPiece path, Value&& value) {
  return SetPathInternal(path, std::make_unique<Value>(std::move(value)));
}

Value* Value::SetBoolPath(StringPiece path, bool value) {
  return SetPathInternal(path, std::make_unique<Value>(value));
}

Value* Value::SetIntPath(StringPiece path, int value) {
  return SetPathInternal(path, std::make_unique<Value>(value));
}

Value* Value::SetDoublePath(StringPiece path, double value) {
  return SetPathInternal(path, std::make_unique<Value>(value));
}

Value* Value::SetStringPath(StringPiece path, StringPiece value) {
  return SetPathInternal(path, std::make_unique<Value>(value));
}

Value* Value::SetStringPath(StringPiece path, std::string&& value) {
  return SetPathInternal(path, std::make_unique<Value>(std::move(value)));
}

Value* Value::SetStringPath(StringPiece path, const char* value) {
  return SetPathInternal(path, std::make_unique<Value>(value));
}

Value* Value::SetStringPath(StringPiece path, StringPiece16 value) {
  return SetPathInternal(path, std::make_unique<Value>(value));
}

bool Value::RemovePath(StringPiece path) {
  return ExtractPath(path).has_value();
}

Optional<Value> Value::ExtractPath(StringPiece path) {
  if (!is_dict() || path.empty())
    return nullopt;

  // NOTE: PathSplitter is not being used here because recursion is used to
  // ensure that dictionaries that become empty due to this operation are
  // removed automatically.
  size_t pos = path.find('.');
  if (pos == path.npos)
    return ExtractKey(path);

  auto found = dict_.find(path.substr(0, pos));
  if (found == dict_.end() || !found->second->is_dict())
    return nullopt;

  Optional<Value> extracted = found->second->ExtractPath(path.substr(pos + 1));
  if (extracted && found->second->dict_.empty())
    dict_.erase(found);

  return extracted;
}

// DEPRECATED METHODS
Value* Value::FindPath(std::initializer_list<StringPiece> path) {
  return const_cast<Value*>(as_const(*this).FindPath(path));
}

Value* Value::FindPath(span<const StringPiece> path) {
  return const_cast<Value*>(as_const(*this).FindPath(path));
}

const Value* Value::FindPath(std::initializer_list<StringPiece> path) const {
  DCHECK_GE(path.size(), 2u) << "Use FindKey() for a path of length 1.";
  return FindPath(make_span(path.begin(), path.size()));
}

const Value* Value::FindPath(span<const StringPiece> path) const {
  const Value* cur = this;
  for (const StringPiece component : path) {
    if (!cur->is_dict() || (cur = cur->FindKey(component)) == nullptr)
      return nullptr;
  }
  return cur;
}

Value* Value::FindPathOfType(std::initializer_list<StringPiece> path,
                             Type type) {
  return const_cast<Value*>(as_const(*this).FindPathOfType(path, type));
}

Value* Value::FindPathOfType(span<const StringPiece> path, Type type) {
  return const_cast<Value*>(as_const(*this).FindPathOfType(path, type));
}

const Value* Value::FindPathOfType(std::initializer_list<StringPiece> path,
                                   Type type) const {
  DCHECK_GE(path.size(), 2u) << "Use FindKeyOfType() for a path of length 1.";
  return FindPathOfType(make_span(path.begin(), path.size()), type);
}

const Value* Value::FindPathOfType(span<const StringPiece> path,
                                   Type type) const {
  const Value* result = FindPath(path);
  if (!result || result->type() != type)
    return nullptr;
  return result;
}

Value* Value::SetPath(std::initializer_list<StringPiece> path, Value&& value) {
  DCHECK_GE(path.size(), 2u) << "Use SetKey() for a path of length 1.";
  return SetPath(make_span(path.begin(), path.size()), std::move(value));
}

Value* Value::SetPath(span<const StringPiece> path, Value&& value) {
  DCHECK(path.begin() != path.end());  // Can't be empty path.

  // Walk/construct intermediate dictionaries. The last element requires
  // special handling so skip it in this loop.
  Value* cur = this;
  auto cur_path = path.begin();
  for (; (cur_path + 1) < path.end(); ++cur_path) {
    if (!cur->is_dict())
      return nullptr;

    // Use lower_bound to avoid doing the search twice for missing keys.
    const StringPiece path_component = *cur_path;
    auto found = cur->dict_.lower_bound(path_component);
    if (found == cur->dict_.end() || found->first != path_component) {
      // No key found, insert one.
      auto inserted = cur->dict_.try_emplace(
          found, path_component, std::make_unique<Value>(Type::DICTIONARY));
      cur = inserted->second.get();
    } else {
      cur = found->second.get();
    }
  }

  // "cur" will now contain the last dictionary to insert or replace into.
  if (!cur->is_dict())
    return nullptr;
  return cur->SetKey(*cur_path, std::move(value));
}

bool Value::RemovePath(std::initializer_list<StringPiece> path) {
  DCHECK_GE(path.size(), 2u) << "Use RemoveKey() for a path of length 1.";
  return RemovePath(make_span(path.begin(), path.size()));
}

bool Value::RemovePath(span<const StringPiece> path) {
  if (!is_dict() || path.empty())
    return false;

  if (path.size() == 1)
    return RemoveKey(path[0]);

  auto found = dict_.find(path[0]);
  if (found == dict_.end() || !found->second->is_dict())
    return false;

  bool removed = found->second->RemovePath(path.subspan(1));
  if (removed && found->second->dict_.empty())
    dict_.erase(found);

  return removed;
}

Value::dict_iterator_proxy Value::DictItems() {
  CHECK(is_dict());
  return dict_iterator_proxy(&dict_);
}

Value::const_dict_iterator_proxy Value::DictItems() const {
  CHECK(is_dict());
  return const_dict_iterator_proxy(&dict_);
}

size_t Value::DictSize() const {
  CHECK(is_dict());
  return dict_.size();
}

bool Value::DictEmpty() const {
  CHECK(is_dict());
  return dict_.empty();
}

void Value::MergeDictionary(const Value* dictionary) {
  CHECK(is_dict());
  CHECK(dictionary->is_dict());
  for (const auto& pair : dictionary->dict_) {
    const auto& key = pair.first;
    const auto& val = pair.second;
    // Check whether we have to merge dictionaries.
    if (val->is_dict()) {
      auto found = dict_.find(key);
      if (found != dict_.end() && found->second->is_dict()) {
        found->second->MergeDictionary(val.get());
        continue;
      }
    }

    // All other cases: Make a copy and hook it up.
    SetKey(key, val->Clone());
  }
}

bool Value::GetAsBoolean(bool* out_value) const {
  if (out_value && is_bool()) {
    *out_value = bool_value_;
    return true;
  }
  return is_bool();
}

bool Value::GetAsInteger(int* out_value) const {
  if (out_value && is_int()) {
    *out_value = int_value_;
    return true;
  }
  return is_int();
}

bool Value::GetAsDouble(double* out_value) const {
  if (out_value && is_double()) {
    *out_value = AsDoubleInternal();
    return true;
  }
  if (out_value && is_int()) {
    // Allow promotion from int to double.
    *out_value = int_value_;
    return true;
  }
  return is_double() || is_int();
}

bool Value::GetAsString(std::string* out_value) const {
  if (out_value && is_string()) {
    *out_value = string_value_;
    return true;
  }
  return is_string();
}

bool Value::GetAsString(string16* out_value) const {
  if (out_value && is_string()) {
    *out_value = UTF8ToUTF16(string_value_);
    return true;
  }
  return is_string();
}

bool Value::GetAsString(const Value** out_value) const {
  if (out_value && is_string()) {
    *out_value = this;
    return true;
  }
  return is_string();
}

bool Value::GetAsString(StringPiece* out_value) const {
  if (out_value && is_string()) {
    *out_value = string_value_;
    return true;
  }
  return is_string();
}

bool Value::GetAsList(ListValue** out_value) {
  if (out_value && is_list()) {
    *out_value = static_cast<ListValue*>(this);
    return true;
  }
  return is_list();
}

bool Value::GetAsList(const ListValue** out_value) const {
  if (out_value && is_list()) {
    *out_value = static_cast<const ListValue*>(this);
    return true;
  }
  return is_list();
}

bool Value::GetAsDictionary(DictionaryValue** out_value) {
  if (out_value && is_dict()) {
    *out_value = static_cast<DictionaryValue*>(this);
    return true;
  }
  return is_dict();
}

bool Value::GetAsDictionary(const DictionaryValue** out_value) const {
  if (out_value && is_dict()) {
    *out_value = static_cast<const DictionaryValue*>(this);
    return true;
  }
  return is_dict();
}

Value* Value::DeepCopy() const {
  return new Value(Clone());
}

std::unique_ptr<Value> Value::CreateDeepCopy() const {
  return std::make_unique<Value>(Clone());
}

bool operator==(const Value& lhs, const Value& rhs) {
  if (lhs.type_ != rhs.type_)
    return false;

  switch (lhs.type_) {
    case Value::Type::NONE:
      return true;
    case Value::Type::BOOLEAN:
      return lhs.bool_value_ == rhs.bool_value_;
    case Value::Type::INTEGER:
      return lhs.int_value_ == rhs.int_value_;
    case Value::Type::DOUBLE:
      return lhs.AsDoubleInternal() == rhs.AsDoubleInternal();
    case Value::Type::STRING:
      return lhs.string_value_ == rhs.string_value_;
    case Value::Type::BINARY:
      return lhs.binary_value_ == rhs.binary_value_;
    // TODO(crbug.com/646113): Clean this up when DictionaryValue and ListValue
    // are completely inlined.
    case Value::Type::DICTIONARY:
      if (lhs.dict_.size() != rhs.dict_.size())
        return false;
      return std::equal(std::begin(lhs.dict_), std::end(lhs.dict_),
                        std::begin(rhs.dict_),
                        [](const auto& u, const auto& v) {
                          return std::tie(u.first, *u.second) ==
                                 std::tie(v.first, *v.second);
                        });
    case Value::Type::LIST:
      return lhs.list_ == rhs.list_;
      // TODO(crbug.com/859477): Remove after root cause is found.
    case Value::Type::DEAD:
      CHECK(false);
      return false;
  }

  // TODO(crbug.com/859477): Revert to NOTREACHED() after root cause is found.
  CHECK(false);
  return false;
}

bool operator!=(const Value& lhs, const Value& rhs) {
  return !(lhs == rhs);
}

bool operator<(const Value& lhs, const Value& rhs) {
  if (lhs.type_ != rhs.type_)
    return lhs.type_ < rhs.type_;

  switch (lhs.type_) {
    case Value::Type::NONE:
      return false;
    case Value::Type::BOOLEAN:
      return lhs.bool_value_ < rhs.bool_value_;
    case Value::Type::INTEGER:
      return lhs.int_value_ < rhs.int_value_;
    case Value::Type::DOUBLE:
      return lhs.AsDoubleInternal() < rhs.AsDoubleInternal();
    case Value::Type::STRING:
      return lhs.string_value_ < rhs.string_value_;
    case Value::Type::BINARY:
      return lhs.binary_value_ < rhs.binary_value_;
    // TODO(crbug.com/646113): Clean this up when DictionaryValue and ListValue
    // are completely inlined.
    case Value::Type::DICTIONARY:
      return std::lexicographical_compare(
          std::begin(lhs.dict_), std::end(lhs.dict_), std::begin(rhs.dict_),
          std::end(rhs.dict_),
          [](const Value::DictStorage::value_type& u,
             const Value::DictStorage::value_type& v) {
            return std::tie(u.first, *u.second) < std::tie(v.first, *v.second);
          });
    case Value::Type::LIST:
      return lhs.list_ < rhs.list_;
      // TODO(crbug.com/859477): Remove after root cause is found.
    case Value::Type::DEAD:
      CHECK(false);
      return false;
  }

  // TODO(crbug.com/859477): Revert to NOTREACHED() after root cause is found.
  CHECK(false);
  return false;
}

bool operator>(const Value& lhs, const Value& rhs) {
  return rhs < lhs;
}

bool operator<=(const Value& lhs, const Value& rhs) {
  return !(rhs < lhs);
}

bool operator>=(const Value& lhs, const Value& rhs) {
  return !(lhs < rhs);
}

bool Value::Equals(const Value* other) const {
  DCHECK(other);
  return *this == *other;
}

size_t Value::EstimateMemoryUsage() const {
  switch (type_) {
    case Type::STRING:
      return base::trace_event::EstimateMemoryUsage(string_value_);
    case Type::BINARY:
      return base::trace_event::EstimateMemoryUsage(binary_value_);
    case Type::DICTIONARY:
      return base::trace_event::EstimateMemoryUsage(dict_);
    case Type::LIST:
      return base::trace_event::EstimateMemoryUsage(list_);
    default:
      return 0;
  }
}

void Value::InternalMoveConstructFrom(Value&& that) {
  type_ = that.type_;

  switch (type_) {
    case Type::NONE:
      return;
    case Type::BOOLEAN:
      bool_value_ = that.bool_value_;
      return;
    case Type::INTEGER:
      int_value_ = that.int_value_;
      return;
    case Type::DOUBLE:
      double_value_ = that.double_value_;
      return;
    case Type::STRING:
      new (&string_value_) std::string(std::move(that.string_value_));
      return;
    case Type::BINARY:
      new (&binary_value_) BlobStorage(std::move(that.binary_value_));
      return;
    case Type::DICTIONARY:
      new (&dict_) DictStorage(std::move(that.dict_));
      return;
    case Type::LIST:
      new (&list_) ListStorage(std::move(that.list_));
      return;
      // TODO(crbug.com/859477): Remove after root cause is found.
    case Type::DEAD:
      CHECK(false);
      return;
  }

  // TODO(crbug.com/859477): Revert to NOTREACHED() after root cause is found.
  CHECK(false);
}

void Value::InternalCleanup() {
  switch (type_) {
    case Type::NONE:
    case Type::BOOLEAN:
    case Type::INTEGER:
    case Type::DOUBLE:
      // Nothing to do
      return;

    case Type::STRING:
      string_value_.~basic_string();
      return;
    case Type::BINARY:
      binary_value_.~BlobStorage();
      return;
    case Type::DICTIONARY:
      dict_.~DictStorage();
      return;
    case Type::LIST:
      list_.~ListStorage();
      return;
      // TODO(crbug.com/859477): Remove after root cause is found.
    case Type::DEAD:
      CHECK(false);
      return;
  }

  // TODO(crbug.com/859477): Revert to NOTREACHED() after root cause is found.
  CHECK(false);
}

Value* Value::SetKeyInternal(StringPiece key,
                             std::unique_ptr<Value>&& val_ptr) {
  CHECK(is_dict());
  // NOTE: We can't use |insert_or_assign| here, as only |try_emplace| does
  // an explicit conversion from StringPiece to std::string if necessary.
  auto result = dict_.try_emplace(key, std::move(val_ptr));
  if (!result.second) {
    // val_ptr is guaranteed to be still intact at this point.
    result.first->second = std::move(val_ptr);
  }
  return result.first->second.get();
}

Value* Value::SetPathInternal(StringPiece path,
                              std::unique_ptr<Value>&& value_ptr) {
  PathSplitter splitter(path);
  DCHECK(splitter.HasNext()) << "Cannot call SetPath() with empty path";
  // Walk/construct intermediate dictionaries. The last element requires
  // special handling so skip it in this loop.
  Value* cur = this;
  StringPiece path_component = splitter.Next();
  while (splitter.HasNext()) {
    if (!cur->is_dict())
      return nullptr;

    // Use lower_bound to avoid doing the search twice for missing keys.
    auto found = cur->dict_.lower_bound(path_component);
    if (found == cur->dict_.end() || found->first != path_component) {
      // No key found, insert one.
      auto inserted = cur->dict_.try_emplace(
          found, path_component, std::make_unique<Value>(Type::DICTIONARY));
      cur = inserted->second.get();
    } else {
      cur = found->second.get();
    }
    path_component = splitter.Next();
  }

  // "cur" will now contain the last dictionary to insert or replace into.
  if (!cur->is_dict())
    return nullptr;
  return cur->SetKeyInternal(path_component, std::move(value_ptr));
}

///////////////////// DictionaryValue ////////////////////

// static
std::unique_ptr<DictionaryValue> DictionaryValue::From(
    std::unique_ptr<Value> value) {
  DictionaryValue* out;
  if (value && value->GetAsDictionary(&out)) {
    ignore_result(value.release());
    return WrapUnique(out);
  }
  return nullptr;
}

DictionaryValue::DictionaryValue() : Value(Type::DICTIONARY) {}
DictionaryValue::DictionaryValue(const DictStorage& in_dict) : Value(in_dict) {}
DictionaryValue::DictionaryValue(DictStorage&& in_dict) noexcept
    : Value(std::move(in_dict)) {}

bool DictionaryValue::HasKey(StringPiece key) const {
  DCHECK(IsStringUTF8AllowingNoncharacters(key));
  auto current_entry = dict_.find(key);
  DCHECK((current_entry == dict_.end()) || current_entry->second);
  return current_entry != dict_.end();
}

void DictionaryValue::Clear() {
  dict_.clear();
}

Value* DictionaryValue::Set(StringPiece path, std::unique_ptr<Value> in_value) {
  DCHECK(IsStringUTF8AllowingNoncharacters(path));
  DCHECK(in_value);

  // IMPORTANT NOTE: Do not replace with SetPathInternal() yet, because the
  // latter fails when over-writing a non-dict intermediate node, while this
  // method just replaces it with one. This difference makes some tests actually
  // fail (http://crbug.com/949461).
  StringPiece current_path(path);
  Value* current_dictionary = this;
  for (size_t delimiter_position = current_path.find('.');
       delimiter_position != StringPiece::npos;
       delimiter_position = current_path.find('.')) {
    // Assume that we're indexing into a dictionary.
    StringPiece key = current_path.substr(0, delimiter_position);
    Value* child_dictionary =
        current_dictionary->FindKeyOfType(key, Type::DICTIONARY);
    if (!child_dictionary) {
      child_dictionary =
          current_dictionary->SetKey(key, Value(Type::DICTIONARY));
    }

    current_dictionary = child_dictionary;
    current_path = current_path.substr(delimiter_position + 1);
  }

  return static_cast<DictionaryValue*>(current_dictionary)
      ->SetWithoutPathExpansion(current_path, std::move(in_value));
}

Value* DictionaryValue::SetBoolean(StringPiece path, bool in_value) {
  return Set(path, std::make_unique<Value>(in_value));
}

Value* DictionaryValue::SetInteger(StringPiece path, int in_value) {
  return Set(path, std::make_unique<Value>(in_value));
}

Value* DictionaryValue::SetDouble(StringPiece path, double in_value) {
  return Set(path, std::make_unique<Value>(in_value));
}

Value* DictionaryValue::SetString(StringPiece path, StringPiece in_value) {
  return Set(path, std::make_unique<Value>(in_value));
}

Value* DictionaryValue::SetString(StringPiece path, const string16& in_value) {
  return Set(path, std::make_unique<Value>(in_value));
}

DictionaryValue* DictionaryValue::SetDictionary(
    StringPiece path,
    std::unique_ptr<DictionaryValue> in_value) {
  return static_cast<DictionaryValue*>(Set(path, std::move(in_value)));
}

ListValue* DictionaryValue::SetList(StringPiece path,
                                    std::unique_ptr<ListValue> in_value) {
  return static_cast<ListValue*>(Set(path, std::move(in_value)));
}

Value* DictionaryValue::SetWithoutPathExpansion(
    StringPiece key,
    std::unique_ptr<Value> in_value) {
  // NOTE: We can't use |insert_or_assign| here, as only |try_emplace| does
  // an explicit conversion from StringPiece to std::string if necessary.
  auto result = dict_.try_emplace(key, std::move(in_value));
  if (!result.second) {
    // in_value is guaranteed to be still intact at this point.
    result.first->second = std::move(in_value);
  }
  return result.first->second.get();
}

bool DictionaryValue::Get(StringPiece path,
                          const Value** out_value) const {
  DCHECK(IsStringUTF8AllowingNoncharacters(path));
  const Value* value = FindPath(path);
  if (!value)
    return false;
  if (out_value)
    *out_value = value;
  return true;
}

bool DictionaryValue::Get(StringPiece path, Value** out_value)  {
  return as_const(*this).Get(path, const_cast<const Value**>(out_value));
}

bool DictionaryValue::GetBoolean(StringPiece path, bool* bool_value) const {
  const Value* value;
  if (!Get(path, &value))
    return false;

  return value->GetAsBoolean(bool_value);
}

bool DictionaryValue::GetInteger(StringPiece path, int* out_value) const {
  const Value* value;
  if (!Get(path, &value))
    return false;

  return value->GetAsInteger(out_value);
}

bool DictionaryValue::GetDouble(StringPiece path, double* out_value) const {
  const Value* value;
  if (!Get(path, &value))
    return false;

  return value->GetAsDouble(out_value);
}

bool DictionaryValue::GetString(StringPiece path,
                                std::string* out_value) const {
  const Value* value;
  if (!Get(path, &value))
    return false;

  return value->GetAsString(out_value);
}

bool DictionaryValue::GetString(StringPiece path, string16* out_value) const {
  const Value* value;
  if (!Get(path, &value))
    return false;

  return value->GetAsString(out_value);
}

bool DictionaryValue::GetStringASCII(StringPiece path,
                                     std::string* out_value) const {
  std::string out;
  if (!GetString(path, &out))
    return false;

  if (!IsStringASCII(out)) {
    NOTREACHED();
    return false;
  }

  out_value->assign(out);
  return true;
}

bool DictionaryValue::GetBinary(StringPiece path,
                                const Value** out_value) const {
  const Value* value;
  bool result = Get(path, &value);
  if (!result || !value->is_blob())
    return false;

  if (out_value)
    *out_value = value;

  return true;
}

bool DictionaryValue::GetBinary(StringPiece path, Value** out_value) {
  return as_const(*this).GetBinary(path, const_cast<const Value**>(out_value));
}

bool DictionaryValue::GetDictionary(StringPiece path,
                                    const DictionaryValue** out_value) const {
  const Value* value;
  bool result = Get(path, &value);
  if (!result || !value->is_dict())
    return false;

  if (out_value)
    *out_value = static_cast<const DictionaryValue*>(value);

  return true;
}

bool DictionaryValue::GetDictionary(StringPiece path,
                                    DictionaryValue** out_value) {
  return as_const(*this).GetDictionary(
      path, const_cast<const DictionaryValue**>(out_value));
}

bool DictionaryValue::GetList(StringPiece path,
                              const ListValue** out_value) const {
  const Value* value;
  bool result = Get(path, &value);
  if (!result || !value->is_list())
    return false;

  if (out_value)
    *out_value = static_cast<const ListValue*>(value);

  return true;
}

bool DictionaryValue::GetList(StringPiece path, ListValue** out_value) {
  return as_const(*this).GetList(path,
                                 const_cast<const ListValue**>(out_value));
}

bool DictionaryValue::GetWithoutPathExpansion(StringPiece key,
                                              const Value** out_value) const {
  DCHECK(IsStringUTF8AllowingNoncharacters(key));
  auto entry_iterator = dict_.find(key);
  if (entry_iterator == dict_.end())
    return false;

  if (out_value)
    *out_value = entry_iterator->second.get();
  return true;
}

bool DictionaryValue::GetWithoutPathExpansion(StringPiece key,
                                              Value** out_value) {
  return as_const(*this).GetWithoutPathExpansion(
      key, const_cast<const Value**>(out_value));
}

bool DictionaryValue::GetBooleanWithoutPathExpansion(StringPiece key,
                                                     bool* out_value) const {
  const Value* value;
  if (!GetWithoutPathExpansion(key, &value))
    return false;

  return value->GetAsBoolean(out_value);
}

bool DictionaryValue::GetIntegerWithoutPathExpansion(StringPiece key,
                                                     int* out_value) const {
  const Value* value;
  if (!GetWithoutPathExpansion(key, &value))
    return false;

  return value->GetAsInteger(out_value);
}

bool DictionaryValue::GetDoubleWithoutPathExpansion(StringPiece key,
                                                    double* out_value) const {
  const Value* value;
  if (!GetWithoutPathExpansion(key, &value))
    return false;

  return value->GetAsDouble(out_value);
}

bool DictionaryValue::GetStringWithoutPathExpansion(
    StringPiece key,
    std::string* out_value) const {
  const Value* value;
  if (!GetWithoutPathExpansion(key, &value))
    return false;

  return value->GetAsString(out_value);
}

bool DictionaryValue::GetStringWithoutPathExpansion(StringPiece key,
                                                    string16* out_value) const {
  const Value* value;
  if (!GetWithoutPathExpansion(key, &value))
    return false;

  return value->GetAsString(out_value);
}

bool DictionaryValue::GetDictionaryWithoutPathExpansion(
    StringPiece key,
    const DictionaryValue** out_value) const {
  const Value* value;
  bool result = GetWithoutPathExpansion(key, &value);
  if (!result || !value->is_dict())
    return false;

  if (out_value)
    *out_value = static_cast<const DictionaryValue*>(value);

  return true;
}

bool DictionaryValue::GetDictionaryWithoutPathExpansion(
    StringPiece key,
    DictionaryValue** out_value) {
  return as_const(*this).GetDictionaryWithoutPathExpansion(
      key, const_cast<const DictionaryValue**>(out_value));
}

bool DictionaryValue::GetListWithoutPathExpansion(
    StringPiece key,
    const ListValue** out_value) const {
  const Value* value;
  bool result = GetWithoutPathExpansion(key, &value);
  if (!result || !value->is_list())
    return false;

  if (out_value)
    *out_value = static_cast<const ListValue*>(value);

  return true;
}

bool DictionaryValue::GetListWithoutPathExpansion(StringPiece key,
                                                  ListValue** out_value) {
  return as_const(*this).GetListWithoutPathExpansion(
      key, const_cast<const ListValue**>(out_value));
}

bool DictionaryValue::Remove(StringPiece path,
                             std::unique_ptr<Value>* out_value) {
  DCHECK(IsStringUTF8AllowingNoncharacters(path));
  StringPiece current_path(path);
  DictionaryValue* current_dictionary = this;
  size_t delimiter_position = current_path.rfind('.');
  if (delimiter_position != StringPiece::npos) {
    if (!GetDictionary(current_path.substr(0, delimiter_position),
                       &current_dictionary))
      return false;
    current_path = current_path.substr(delimiter_position + 1);
  }

  return current_dictionary->RemoveWithoutPathExpansion(current_path,
                                                        out_value);
}

bool DictionaryValue::RemoveWithoutPathExpansion(
    StringPiece key,
    std::unique_ptr<Value>* out_value) {
  DCHECK(IsStringUTF8AllowingNoncharacters(key));
  auto entry_iterator = dict_.find(key);
  if (entry_iterator == dict_.end())
    return false;

  if (out_value)
    *out_value = std::move(entry_iterator->second);
  dict_.erase(entry_iterator);
  return true;
}

bool DictionaryValue::RemovePath(StringPiece path,
                                 std::unique_ptr<Value>* out_value) {
  bool result = false;
  size_t delimiter_position = path.find('.');

  if (delimiter_position == std::string::npos)
    return RemoveWithoutPathExpansion(path, out_value);

  StringPiece subdict_path = path.substr(0, delimiter_position);
  DictionaryValue* subdict = nullptr;
  if (!GetDictionary(subdict_path, &subdict))
    return false;
  result = subdict->RemovePath(path.substr(delimiter_position + 1),
                               out_value);
  if (result && subdict->empty())
    RemoveWithoutPathExpansion(subdict_path, nullptr);

  return result;
}

std::unique_ptr<DictionaryValue> DictionaryValue::DeepCopyWithoutEmptyChildren()
    const {
  std::unique_ptr<DictionaryValue> copy =
      CopyDictionaryWithoutEmptyChildren(*this);
  if (!copy)
    copy = std::make_unique<DictionaryValue>();
  return copy;
}

void DictionaryValue::Swap(DictionaryValue* other) {
  CHECK(other->is_dict());
  dict_.swap(other->dict_);
}

DictionaryValue::Iterator::Iterator(const DictionaryValue& target)
    : target_(target), it_(target.dict_.begin()) {}

DictionaryValue::Iterator::Iterator(const Iterator& other) = default;

DictionaryValue::Iterator::~Iterator() = default;

DictionaryValue* DictionaryValue::DeepCopy() const {
  return new DictionaryValue(dict_);
}

std::unique_ptr<DictionaryValue> DictionaryValue::CreateDeepCopy() const {
  return std::make_unique<DictionaryValue>(dict_);
}

///////////////////// ListValue ////////////////////

// static
std::unique_ptr<ListValue> ListValue::From(std::unique_ptr<Value> value) {
  ListValue* out;
  if (value && value->GetAsList(&out)) {
    ignore_result(value.release());
    return WrapUnique(out);
  }
  return nullptr;
}

ListValue::ListValue() : Value(Type::LIST) {}
ListValue::ListValue(span<const Value> in_list) : Value(in_list) {}
ListValue::ListValue(ListStorage&& in_list) noexcept
    : Value(std::move(in_list)) {}

void ListValue::Clear() {
  list_.clear();
}

void ListValue::Reserve(size_t n) {
  list_.reserve(n);
}

bool ListValue::Set(size_t index, std::unique_ptr<Value> in_value) {
  if (!in_value)
    return false;

  if (index >= list_.size())
    list_.resize(index + 1);

  list_[index] = std::move(*in_value);
  return true;
}

bool ListValue::Get(size_t index, const Value** out_value) const {
  if (index >= list_.size())
    return false;

  if (out_value)
    *out_value = &list_[index];

  return true;
}

bool ListValue::Get(size_t index, Value** out_value) {
  return as_const(*this).Get(index, const_cast<const Value**>(out_value));
}

bool ListValue::GetBoolean(size_t index, bool* bool_value) const {
  const Value* value;
  if (!Get(index, &value))
    return false;

  return value->GetAsBoolean(bool_value);
}

bool ListValue::GetInteger(size_t index, int* out_value) const {
  const Value* value;
  if (!Get(index, &value))
    return false;

  return value->GetAsInteger(out_value);
}

bool ListValue::GetDouble(size_t index, double* out_value) const {
  const Value* value;
  if (!Get(index, &value))
    return false;

  return value->GetAsDouble(out_value);
}

bool ListValue::GetString(size_t index, std::string* out_value) const {
  const Value* value;
  if (!Get(index, &value))
    return false;

  return value->GetAsString(out_value);
}

bool ListValue::GetString(size_t index, string16* out_value) const {
  const Value* value;
  if (!Get(index, &value))
    return false;

  return value->GetAsString(out_value);
}

bool ListValue::GetDictionary(size_t index,
                              const DictionaryValue** out_value) const {
  const Value* value;
  bool result = Get(index, &value);
  if (!result || !value->is_dict())
    return false;

  if (out_value)
    *out_value = static_cast<const DictionaryValue*>(value);

  return true;
}

bool ListValue::GetDictionary(size_t index, DictionaryValue** out_value) {
  return as_const(*this).GetDictionary(
      index, const_cast<const DictionaryValue**>(out_value));
}

bool ListValue::GetList(size_t index, const ListValue** out_value) const {
  const Value* value;
  bool result = Get(index, &value);
  if (!result || !value->is_list())
    return false;

  if (out_value)
    *out_value = static_cast<const ListValue*>(value);

  return true;
}

bool ListValue::GetList(size_t index, ListValue** out_value) {
  return as_const(*this).GetList(index,
                                 const_cast<const ListValue**>(out_value));
}

bool ListValue::Remove(size_t index, std::unique_ptr<Value>* out_value) {
  if (index >= list_.size())
    return false;

  if (out_value)
    *out_value = std::make_unique<Value>(std::move(list_[index]));

  list_.erase(list_.begin() + index);
  return true;
}

bool ListValue::Remove(const Value& value, size_t* index) {
  auto it = std::find(list_.begin(), list_.end(), value);

  if (it == list_.end())
    return false;

  if (index)
    *index = std::distance(list_.begin(), it);

  list_.erase(it);
  return true;
}

ListValue::iterator ListValue::Erase(iterator iter,
                                     std::unique_ptr<Value>* out_value) {
  if (out_value)
    *out_value = std::make_unique<Value>(std::move(*iter));

  auto list_iter = list_.begin() + (iter - GetList().begin());
  CHECK(list_iter != list_.end());
  list_iter = list_.erase(list_iter);
  return GetList().begin() + (list_iter - list_.begin());
}

void ListValue::Append(std::unique_ptr<Value> in_value) {
  list_.push_back(std::move(*in_value));
}

void ListValue::AppendBoolean(bool in_value) {
  list_.emplace_back(in_value);
}

void ListValue::AppendInteger(int in_value) {
  list_.emplace_back(in_value);
}

void ListValue::AppendDouble(double in_value) {
  list_.emplace_back(in_value);
}

void ListValue::AppendString(StringPiece in_value) {
  list_.emplace_back(in_value);
}

void ListValue::AppendString(const string16& in_value) {
  list_.emplace_back(in_value);
}

void ListValue::AppendStrings(const std::vector<std::string>& in_values) {
  list_.reserve(list_.size() + in_values.size());
  for (const auto& in_value : in_values)
    list_.emplace_back(in_value);
}

void ListValue::AppendStrings(const std::vector<string16>& in_values) {
  list_.reserve(list_.size() + in_values.size());
  for (const auto& in_value : in_values)
    list_.emplace_back(in_value);
}

bool ListValue::AppendIfNotPresent(std::unique_ptr<Value> in_value) {
  DCHECK(in_value);
  if (Contains(list_, *in_value))
    return false;

  list_.push_back(std::move(*in_value));
  return true;
}

bool ListValue::Insert(size_t index, std::unique_ptr<Value> in_value) {
  DCHECK(in_value);
  if (index > list_.size())
    return false;

  list_.insert(list_.begin() + index, std::move(*in_value));
  return true;
}

ListValue::const_iterator ListValue::Find(const Value& value) const {
  return std::find(GetList().begin(), GetList().end(), value);
}

void ListValue::Swap(ListValue* other) {
  CHECK(other->is_list());
  list_.swap(other->list_);
}

ListValue* ListValue::DeepCopy() const {
  return new ListValue(list_);
}

std::unique_ptr<ListValue> ListValue::CreateDeepCopy() const {
  return std::make_unique<ListValue>(list_);
}

ValueSerializer::~ValueSerializer() = default;

ValueDeserializer::~ValueDeserializer() = default;

std::ostream& operator<<(std::ostream& out, const Value& value) {
  std::string json;
  JSONWriter::WriteWithOptions(value, JSONWriter::OPTIONS_PRETTY_PRINT, &json);
  return out << json;
}

std::ostream& operator<<(std::ostream& out, const Value::Type& type) {
  if (static_cast<int>(type) < 0 ||
      static_cast<size_t>(type) >= base::size(kTypeNames))
    return out << "Invalid Type (index = " << static_cast<int>(type) << ")";
  return out << Value::GetTypeName(type);
}

}  // namespace base
