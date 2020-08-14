//
//  Copyright 2019 The Abseil Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "absl/flags/flag.h"

#include <stdint.h>

#include <cmath>
#include <string>
#include <vector>

#include "gtest/gtest.h"
#include "absl/base/attributes.h"
#include "absl/flags/config.h"
#include "absl/flags/declare.h"
#include "absl/flags/internal/commandlineflag.h"
#include "absl/flags/internal/flag.h"
#include "absl/flags/internal/registry.h"
#include "absl/flags/usage_config.h"
#include "absl/strings/match.h"
#include "absl/strings/numbers.h"
#include "absl/strings/str_cat.h"
#include "absl/strings/str_split.h"
#include "absl/strings/string_view.h"

ABSL_DECLARE_FLAG(int64_t, mistyped_int_flag);
ABSL_DECLARE_FLAG(std::vector<std::string>, mistyped_string_flag);

namespace {

namespace flags = absl::flags_internal;

std::string TestHelpMsg() { return "dynamic help"; }
template <typename T>
void* TestMakeDflt() {
  return new T{};
}
void TestCallback() {}

struct UDT {
  UDT() = default;
  UDT(const UDT&) = default;
};
bool AbslParseFlag(absl::string_view, UDT*, std::string*) { return true; }
std::string AbslUnparseFlag(const UDT&) { return ""; }

class FlagTest : public testing::Test {
 protected:
  static void SetUpTestSuite() {
    // Install a function to normalize filenames before this test is run.
    absl::FlagsUsageConfig default_config;
    default_config.normalize_filename = &FlagTest::NormalizeFileName;
    absl::SetFlagsUsageConfig(default_config);
  }

 private:
  static std::string NormalizeFileName(absl::string_view fname) {
#ifdef _WIN32
    std::string normalized(fname);
    std::replace(normalized.begin(), normalized.end(), '\\', '/');
    fname = normalized;
#endif
    return std::string(fname);
  }
};

struct S1 {
  S1() = default;
  S1(const S1&) = default;
  int32_t f1;
  int64_t f2;
};

struct S2 {
  S2() = default;
  S2(const S2&) = default;
  int64_t f1;
  double f2;
};

TEST_F(FlagTest, Traits) {
  EXPECT_EQ(flags::StorageKind<int>(),
            flags::FlagValueStorageKind::kOneWordAtomic);
  EXPECT_EQ(flags::StorageKind<bool>(),
            flags::FlagValueStorageKind::kOneWordAtomic);
  EXPECT_EQ(flags::StorageKind<double>(),
            flags::FlagValueStorageKind::kOneWordAtomic);
  EXPECT_EQ(flags::StorageKind<int64_t>(),
            flags::FlagValueStorageKind::kOneWordAtomic);

#if defined(ABSL_FLAGS_INTERNAL_ATOMIC_DOUBLE_WORD)
  EXPECT_EQ(flags::StorageKind<S1>(),
            flags::FlagValueStorageKind::kTwoWordsAtomic);
  EXPECT_EQ(flags::StorageKind<S2>(),
            flags::FlagValueStorageKind::kTwoWordsAtomic);
#else
  EXPECT_EQ(flags::StorageKind<S1>(),
            flags::FlagValueStorageKind::kHeapAllocated);
  EXPECT_EQ(flags::StorageKind<S2>(),
            flags::FlagValueStorageKind::kHeapAllocated);
#endif

  EXPECT_EQ(flags::StorageKind<std::string>(),
            flags::FlagValueStorageKind::kHeapAllocated);
  EXPECT_EQ(flags::StorageKind<std::vector<std::string>>(),
            flags::FlagValueStorageKind::kHeapAllocated);
}

// --------------------------------------------------------------------

constexpr flags::FlagHelpArg help_arg{flags::FlagHelpMsg("literal help"),
                                      flags::FlagHelpKind::kLiteral};

using String = std::string;

#define DEFINE_CONSTRUCTED_FLAG(T)                                          \
  constexpr flags::Flag<T> f1##T("f1", "file", help_arg, &TestMakeDflt<T>); \
  ABSL_CONST_INIT flags::Flag<T> f2##T(                                     \
      "f2", "file",                                                         \
      {flags::FlagHelpMsg(&TestHelpMsg), flags::FlagHelpKind::kGenFunc},    \
      &TestMakeDflt<T>)

#define TEST_CONSTRUCTED_FLAG(T) TestConstructionFor(f1##T, &f2##T);

DEFINE_CONSTRUCTED_FLAG(bool);
DEFINE_CONSTRUCTED_FLAG(int16_t);
DEFINE_CONSTRUCTED_FLAG(uint16_t);
DEFINE_CONSTRUCTED_FLAG(int32_t);
DEFINE_CONSTRUCTED_FLAG(uint32_t);
DEFINE_CONSTRUCTED_FLAG(int64_t);
DEFINE_CONSTRUCTED_FLAG(uint64_t);
DEFINE_CONSTRUCTED_FLAG(float);
DEFINE_CONSTRUCTED_FLAG(double);
DEFINE_CONSTRUCTED_FLAG(String);
DEFINE_CONSTRUCTED_FLAG(UDT);

template <typename T>
bool TestConstructionFor(const flags::Flag<T>& f1, flags::Flag<T>* f2) {
  EXPECT_EQ(f1.Name(), "f1");
  EXPECT_EQ(f1.Help(), "literal help");
  EXPECT_EQ(f1.Filename(), "file");

  flags::FlagRegistrar<T, false>(f2).OnUpdate(TestCallback);

  EXPECT_EQ(f2->Name(), "f2");
  EXPECT_EQ(f2->Help(), "dynamic help");
  EXPECT_EQ(f2->Filename(), "file");

  return true;
}

TEST_F(FlagTest, TestConstruction) {
  TEST_CONSTRUCTED_FLAG(bool);
  TEST_CONSTRUCTED_FLAG(int16_t);
  TEST_CONSTRUCTED_FLAG(uint16_t);
  TEST_CONSTRUCTED_FLAG(int32_t);
  TEST_CONSTRUCTED_FLAG(uint32_t);
  TEST_CONSTRUCTED_FLAG(int64_t);
  TEST_CONSTRUCTED_FLAG(uint64_t);
  TEST_CONSTRUCTED_FLAG(float);
  TEST_CONSTRUCTED_FLAG(double);
  TEST_CONSTRUCTED_FLAG(String);
  TEST_CONSTRUCTED_FLAG(UDT);
}

// --------------------------------------------------------------------

}  // namespace

ABSL_DECLARE_FLAG(bool, test_flag_01);
ABSL_DECLARE_FLAG(int, test_flag_02);
ABSL_DECLARE_FLAG(int16_t, test_flag_03);
ABSL_DECLARE_FLAG(uint16_t, test_flag_04);
ABSL_DECLARE_FLAG(int32_t, test_flag_05);
ABSL_DECLARE_FLAG(uint32_t, test_flag_06);
ABSL_DECLARE_FLAG(int64_t, test_flag_07);
ABSL_DECLARE_FLAG(uint64_t, test_flag_08);
ABSL_DECLARE_FLAG(double, test_flag_09);
ABSL_DECLARE_FLAG(float, test_flag_10);
ABSL_DECLARE_FLAG(std::string, test_flag_11);

namespace {

#if !ABSL_FLAGS_STRIP_NAMES

TEST_F(FlagTest, TestFlagDeclaration) {
  // test that we can access flag objects.
  EXPECT_EQ(FLAGS_test_flag_01.Name(), "test_flag_01");
  EXPECT_EQ(FLAGS_test_flag_02.Name(), "test_flag_02");
  EXPECT_EQ(FLAGS_test_flag_03.Name(), "test_flag_03");
  EXPECT_EQ(FLAGS_test_flag_04.Name(), "test_flag_04");
  EXPECT_EQ(FLAGS_test_flag_05.Name(), "test_flag_05");
  EXPECT_EQ(FLAGS_test_flag_06.Name(), "test_flag_06");
  EXPECT_EQ(FLAGS_test_flag_07.Name(), "test_flag_07");
  EXPECT_EQ(FLAGS_test_flag_08.Name(), "test_flag_08");
  EXPECT_EQ(FLAGS_test_flag_09.Name(), "test_flag_09");
  EXPECT_EQ(FLAGS_test_flag_10.Name(), "test_flag_10");
  EXPECT_EQ(FLAGS_test_flag_11.Name(), "test_flag_11");
}
#endif  // !ABSL_FLAGS_STRIP_NAMES

// --------------------------------------------------------------------

}  // namespace

ABSL_FLAG(bool, test_flag_01, true, "test flag 01");
ABSL_FLAG(int, test_flag_02, 1234, "test flag 02");
ABSL_FLAG(int16_t, test_flag_03, -34, "test flag 03");
ABSL_FLAG(uint16_t, test_flag_04, 189, "test flag 04");
ABSL_FLAG(int32_t, test_flag_05, 10765, "test flag 05");
ABSL_FLAG(uint32_t, test_flag_06, 40000, "test flag 06");
ABSL_FLAG(int64_t, test_flag_07, -1234567, "test flag 07");
ABSL_FLAG(uint64_t, test_flag_08, 9876543, "test flag 08");
ABSL_FLAG(double, test_flag_09, -9.876e-50, "test flag 09");
ABSL_FLAG(float, test_flag_10, 1.234e12f, "test flag 10");
ABSL_FLAG(std::string, test_flag_11, "", "test flag 11");

namespace {

#if !ABSL_FLAGS_STRIP_NAMES
TEST_F(FlagTest, TestFlagDefinition) {
  absl::string_view expected_file_name = "absl/flags/flag_test.cc";

  EXPECT_EQ(FLAGS_test_flag_01.Name(), "test_flag_01");
  EXPECT_EQ(FLAGS_test_flag_01.Help(), "test flag 01");
  EXPECT_TRUE(absl::EndsWith(FLAGS_test_flag_01.Filename(), expected_file_name))
      << FLAGS_test_flag_01.Filename();

  EXPECT_EQ(FLAGS_test_flag_02.Name(), "test_flag_02");
  EXPECT_EQ(FLAGS_test_flag_02.Help(), "test flag 02");
  EXPECT_TRUE(absl::EndsWith(FLAGS_test_flag_02.Filename(), expected_file_name))
      << FLAGS_test_flag_02.Filename();

  EXPECT_EQ(FLAGS_test_flag_03.Name(), "test_flag_03");
  EXPECT_EQ(FLAGS_test_flag_03.Help(), "test flag 03");
  EXPECT_TRUE(absl::EndsWith(FLAGS_test_flag_03.Filename(), expected_file_name))
      << FLAGS_test_flag_03.Filename();

  EXPECT_EQ(FLAGS_test_flag_04.Name(), "test_flag_04");
  EXPECT_EQ(FLAGS_test_flag_04.Help(), "test flag 04");
  EXPECT_TRUE(absl::EndsWith(FLAGS_test_flag_04.Filename(), expected_file_name))
      << FLAGS_test_flag_04.Filename();

  EXPECT_EQ(FLAGS_test_flag_05.Name(), "test_flag_05");
  EXPECT_EQ(FLAGS_test_flag_05.Help(), "test flag 05");
  EXPECT_TRUE(absl::EndsWith(FLAGS_test_flag_05.Filename(), expected_file_name))
      << FLAGS_test_flag_05.Filename();

  EXPECT_EQ(FLAGS_test_flag_06.Name(), "test_flag_06");
  EXPECT_EQ(FLAGS_test_flag_06.Help(), "test flag 06");
  EXPECT_TRUE(absl::EndsWith(FLAGS_test_flag_06.Filename(), expected_file_name))
      << FLAGS_test_flag_06.Filename();

  EXPECT_EQ(FLAGS_test_flag_07.Name(), "test_flag_07");
  EXPECT_EQ(FLAGS_test_flag_07.Help(), "test flag 07");
  EXPECT_TRUE(absl::EndsWith(FLAGS_test_flag_07.Filename(), expected_file_name))
      << FLAGS_test_flag_07.Filename();

  EXPECT_EQ(FLAGS_test_flag_08.Name(), "test_flag_08");
  EXPECT_EQ(FLAGS_test_flag_08.Help(), "test flag 08");
  EXPECT_TRUE(absl::EndsWith(FLAGS_test_flag_08.Filename(), expected_file_name))
      << FLAGS_test_flag_08.Filename();

  EXPECT_EQ(FLAGS_test_flag_09.Name(), "test_flag_09");
  EXPECT_EQ(FLAGS_test_flag_09.Help(), "test flag 09");
  EXPECT_TRUE(absl::EndsWith(FLAGS_test_flag_09.Filename(), expected_file_name))
      << FLAGS_test_flag_09.Filename();

  EXPECT_EQ(FLAGS_test_flag_10.Name(), "test_flag_10");
  EXPECT_EQ(FLAGS_test_flag_10.Help(), "test flag 10");
  EXPECT_TRUE(absl::EndsWith(FLAGS_test_flag_10.Filename(), expected_file_name))
      << FLAGS_test_flag_10.Filename();

  EXPECT_EQ(FLAGS_test_flag_11.Name(), "test_flag_11");
  EXPECT_EQ(FLAGS_test_flag_11.Help(), "test flag 11");
  EXPECT_TRUE(absl::EndsWith(FLAGS_test_flag_11.Filename(), expected_file_name))
      << FLAGS_test_flag_11.Filename();
}
#endif  // !ABSL_FLAGS_STRIP_NAMES

// --------------------------------------------------------------------

TEST_F(FlagTest, TestDefault) {
  EXPECT_EQ(FLAGS_test_flag_01.DefaultValue(), "true");
  EXPECT_EQ(FLAGS_test_flag_02.DefaultValue(), "1234");
  EXPECT_EQ(FLAGS_test_flag_03.DefaultValue(), "-34");
  EXPECT_EQ(FLAGS_test_flag_04.DefaultValue(), "189");
  EXPECT_EQ(FLAGS_test_flag_05.DefaultValue(), "10765");
  EXPECT_EQ(FLAGS_test_flag_06.DefaultValue(), "40000");
  EXPECT_EQ(FLAGS_test_flag_07.DefaultValue(), "-1234567");
  EXPECT_EQ(FLAGS_test_flag_08.DefaultValue(), "9876543");
  EXPECT_EQ(FLAGS_test_flag_09.DefaultValue(), "-9.876e-50");
  EXPECT_EQ(FLAGS_test_flag_10.DefaultValue(), "1.234e+12");
  EXPECT_EQ(FLAGS_test_flag_11.DefaultValue(), "");

  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_01), true);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_02), 1234);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_03), -34);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_04), 189);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_05), 10765);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_06), 40000);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_07), -1234567);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_08), 9876543);
  EXPECT_NEAR(absl::GetFlag(FLAGS_test_flag_09), -9.876e-50, 1e-55);
  EXPECT_NEAR(absl::GetFlag(FLAGS_test_flag_10), 1.234e12f, 1e5f);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_11), "");
}

// --------------------------------------------------------------------

struct NonTriviallyCopyableAggregate {
  NonTriviallyCopyableAggregate() = default;
  NonTriviallyCopyableAggregate(const NonTriviallyCopyableAggregate& rhs)
      : value(rhs.value) {}
  NonTriviallyCopyableAggregate& operator=(
      const NonTriviallyCopyableAggregate& rhs) {
    value = rhs.value;
    return *this;
  }

  int value;
};
bool AbslParseFlag(absl::string_view src, NonTriviallyCopyableAggregate* f,
                   std::string* e) {
  return absl::ParseFlag(src, &f->value, e);
}
std::string AbslUnparseFlag(const NonTriviallyCopyableAggregate& ntc) {
  return absl::StrCat(ntc.value);
}

bool operator==(const NonTriviallyCopyableAggregate& ntc1,
                const NonTriviallyCopyableAggregate& ntc2) {
  return ntc1.value == ntc2.value;
}

}  // namespace

ABSL_FLAG(bool, test_flag_eb_01, {}, "");
ABSL_FLAG(int32_t, test_flag_eb_02, {}, "");
ABSL_FLAG(int64_t, test_flag_eb_03, {}, "");
ABSL_FLAG(double, test_flag_eb_04, {}, "");
ABSL_FLAG(std::string, test_flag_eb_05, {}, "");
ABSL_FLAG(NonTriviallyCopyableAggregate, test_flag_eb_06, {}, "");

namespace {

TEST_F(FlagTest, TestEmptyBracesDefault) {
  EXPECT_EQ(FLAGS_test_flag_eb_01.DefaultValue(), "false");
  EXPECT_EQ(FLAGS_test_flag_eb_02.DefaultValue(), "0");
  EXPECT_EQ(FLAGS_test_flag_eb_03.DefaultValue(), "0");
  EXPECT_EQ(FLAGS_test_flag_eb_04.DefaultValue(), "0");
  EXPECT_EQ(FLAGS_test_flag_eb_05.DefaultValue(), "");
  EXPECT_EQ(FLAGS_test_flag_eb_06.DefaultValue(), "0");

  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_eb_01), false);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_eb_02), 0);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_eb_03), 0);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_eb_04), 0.0);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_eb_05), "");
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_eb_06),
            NonTriviallyCopyableAggregate{});
}

// --------------------------------------------------------------------

TEST_F(FlagTest, TestGetSet) {
  absl::SetFlag(&FLAGS_test_flag_01, false);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_01), false);

  absl::SetFlag(&FLAGS_test_flag_02, 321);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_02), 321);

  absl::SetFlag(&FLAGS_test_flag_03, 67);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_03), 67);

  absl::SetFlag(&FLAGS_test_flag_04, 1);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_04), 1);

  absl::SetFlag(&FLAGS_test_flag_05, -908);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_05), -908);

  absl::SetFlag(&FLAGS_test_flag_06, 4001);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_06), 4001);

  absl::SetFlag(&FLAGS_test_flag_07, -23456);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_07), -23456);

  absl::SetFlag(&FLAGS_test_flag_08, 975310);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_08), 975310);

  absl::SetFlag(&FLAGS_test_flag_09, 1.00001);
  EXPECT_NEAR(absl::GetFlag(FLAGS_test_flag_09), 1.00001, 1e-10);

  absl::SetFlag(&FLAGS_test_flag_10, -3.54f);
  EXPECT_NEAR(absl::GetFlag(FLAGS_test_flag_10), -3.54f, 1e-6f);

  absl::SetFlag(&FLAGS_test_flag_11, "asdf");
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_11), "asdf");
}

// --------------------------------------------------------------------

int GetDflt1() { return 1; }

}  // namespace

ABSL_FLAG(int, test_flag_12, GetDflt1(), "test flag 12");
ABSL_FLAG(std::string, test_flag_13, absl::StrCat("AAA", "BBB"),
          "test flag 13");

namespace {

TEST_F(FlagTest, TestNonConstexprDefault) {
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_12), 1);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_13), "AAABBB");
}

// --------------------------------------------------------------------

}  // namespace

ABSL_FLAG(bool, test_flag_14, true, absl::StrCat("test ", "flag ", "14"));

namespace {

#if !ABSL_FLAGS_STRIP_HELP
TEST_F(FlagTest, TestNonConstexprHelp) {
  EXPECT_EQ(FLAGS_test_flag_14.Help(), "test flag 14");
}
#endif  //! ABSL_FLAGS_STRIP_HELP

// --------------------------------------------------------------------

int cb_test_value = -1;
void TestFlagCB();

}  // namespace

ABSL_FLAG(int, test_flag_with_cb, 100, "").OnUpdate(TestFlagCB);

ABSL_FLAG(int, test_flag_with_lambda_cb, 200, "").OnUpdate([]() {
  cb_test_value = absl::GetFlag(FLAGS_test_flag_with_lambda_cb) +
                  absl::GetFlag(FLAGS_test_flag_with_cb);
});

namespace {

void TestFlagCB() { cb_test_value = absl::GetFlag(FLAGS_test_flag_with_cb); }

// Tests side-effects of callback invocation.
TEST_F(FlagTest, CallbackInvocation) {
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_with_cb), 100);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_with_lambda_cb), 200);
  EXPECT_EQ(cb_test_value, 300);

  absl::SetFlag(&FLAGS_test_flag_with_cb, 1);
  EXPECT_EQ(cb_test_value, 1);

  absl::SetFlag(&FLAGS_test_flag_with_lambda_cb, 3);
  EXPECT_EQ(cb_test_value, 4);
}

// --------------------------------------------------------------------

struct CustomUDT {
  CustomUDT() : a(1), b(1) {}
  CustomUDT(int a_, int b_) : a(a_), b(b_) {}

  friend bool operator==(const CustomUDT& f1, const CustomUDT& f2) {
    return f1.a == f2.a && f1.b == f2.b;
  }

  int a;
  int b;
};
bool AbslParseFlag(absl::string_view in, CustomUDT* f, std::string*) {
  std::vector<absl::string_view> parts =
      absl::StrSplit(in, ':', absl::SkipWhitespace());

  if (parts.size() != 2) return false;

  if (!absl::SimpleAtoi(parts[0], &f->a)) return false;

  if (!absl::SimpleAtoi(parts[1], &f->b)) return false;

  return true;
}
std::string AbslUnparseFlag(const CustomUDT& f) {
  return absl::StrCat(f.a, ":", f.b);
}

}  // namespace

ABSL_FLAG(CustomUDT, test_flag_15, CustomUDT(), "test flag 15");

namespace {

TEST_F(FlagTest, TestCustomUDT) {
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_15), CustomUDT(1, 1));
  absl::SetFlag(&FLAGS_test_flag_15, CustomUDT(2, 3));
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_15), CustomUDT(2, 3));
}

// MSVC produces link error on the type mismatch.
// Linux does not have build errors and validations work as expected.
#if !defined(_WIN32) && GTEST_HAS_DEATH_TEST

using FlagDeathTest = FlagTest;

TEST_F(FlagDeathTest, TestTypeMismatchValidations) {
#if !defined(NDEBUG)
  EXPECT_DEATH(static_cast<void>(absl::GetFlag(FLAGS_mistyped_int_flag)),
               "Flag 'mistyped_int_flag' is defined as one type and declared "
               "as another");
  EXPECT_DEATH(static_cast<void>(absl::GetFlag(FLAGS_mistyped_string_flag)),
               "Flag 'mistyped_string_flag' is defined as one type and "
               "declared as another");
#endif

  EXPECT_DEATH(absl::SetFlag(&FLAGS_mistyped_int_flag, 1),
               "Flag 'mistyped_int_flag' is defined as one type and declared "
               "as another");
  EXPECT_DEATH(
      absl::SetFlag(&FLAGS_mistyped_string_flag, std::vector<std::string>{}),
      "Flag 'mistyped_string_flag' is defined as one type and declared as "
      "another");
}

#endif

// --------------------------------------------------------------------

// A contrived type that offers implicit and explicit conversion from specific
// source types.
struct ConversionTestVal {
  ConversionTestVal() = default;
  explicit ConversionTestVal(int a_in) : a(a_in) {}

  enum class ViaImplicitConv { kTen = 10, kEleven };
  // NOLINTNEXTLINE
  ConversionTestVal(ViaImplicitConv from) : a(static_cast<int>(from)) {}

  int a;
};

bool AbslParseFlag(absl::string_view in, ConversionTestVal* val_out,
                   std::string*) {
  if (!absl::SimpleAtoi(in, &val_out->a)) {
    return false;
  }
  return true;
}
std::string AbslUnparseFlag(const ConversionTestVal& val) {
  return absl::StrCat(val.a);
}

}  // namespace

// Flag default values can be specified with a value that converts to the flag
// value type implicitly.
ABSL_FLAG(ConversionTestVal, test_flag_16,
          ConversionTestVal::ViaImplicitConv::kTen, "test flag 16");

namespace {

TEST_F(FlagTest, CanSetViaImplicitConversion) {
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_16).a, 10);
  absl::SetFlag(&FLAGS_test_flag_16,
                ConversionTestVal::ViaImplicitConv::kEleven);
  EXPECT_EQ(absl::GetFlag(FLAGS_test_flag_16).a, 11);
}

// --------------------------------------------------------------------

struct NonDfltConstructible {
 public:
  // This constructor tests that we can initialize the flag with int value
  NonDfltConstructible(int i) : value(i) {}  // NOLINT

  // This constructor tests that we can't initialize the flag with char value
  // but can with explicitly constructed NonDfltConstructible.
  explicit NonDfltConstructible(char c) : value(100 + static_cast<int>(c)) {}

  int value;
};

bool AbslParseFlag(absl::string_view in, NonDfltConstructible* ndc_out,
                   std::string*) {
  return absl::SimpleAtoi(in, &ndc_out->value);
}
std::string AbslUnparseFlag(const NonDfltConstructible& ndc) {
  return absl::StrCat(ndc.value);
}

}  // namespace

ABSL_FLAG(NonDfltConstructible, ndc_flag1, NonDfltConstructible('1'),
          "Flag with non default constructible type");
ABSL_FLAG(NonDfltConstructible, ndc_flag2, 0,
          "Flag with non default constructible type");

namespace {

TEST_F(FlagTest, TestNonDefaultConstructibleType) {
  EXPECT_EQ(absl::GetFlag(FLAGS_ndc_flag1).value, '1' + 100);
  EXPECT_EQ(absl::GetFlag(FLAGS_ndc_flag2).value, 0);

  absl::SetFlag(&FLAGS_ndc_flag1, NonDfltConstructible('A'));
  absl::SetFlag(&FLAGS_ndc_flag2, 25);

  EXPECT_EQ(absl::GetFlag(FLAGS_ndc_flag1).value, 'A' + 100);
  EXPECT_EQ(absl::GetFlag(FLAGS_ndc_flag2).value, 25);
}

}  // namespace

// --------------------------------------------------------------------

ABSL_RETIRED_FLAG(bool, old_bool_flag, true, "old descr");
ABSL_RETIRED_FLAG(int, old_int_flag, (int)std::sqrt(10), "old descr");
ABSL_RETIRED_FLAG(std::string, old_str_flag, "", absl::StrCat("old ", "descr"));

namespace {

TEST_F(FlagTest, TestRetiredFlagRegistration) {
  bool is_bool = false;
  EXPECT_TRUE(flags::IsRetiredFlag("old_bool_flag", &is_bool));
  EXPECT_TRUE(is_bool);
  EXPECT_TRUE(flags::IsRetiredFlag("old_int_flag", &is_bool));
  EXPECT_FALSE(is_bool);
  EXPECT_TRUE(flags::IsRetiredFlag("old_str_flag", &is_bool));
  EXPECT_FALSE(is_bool);
  EXPECT_FALSE(flags::IsRetiredFlag("some_other_flag", &is_bool));
}

}  // namespace
