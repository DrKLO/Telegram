// Copyright 2017 The Abseil Authors.
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

#include "absl/strings/ascii.h"

#include <cctype>
#include <clocale>
#include <cstring>
#include <string>

#include "gtest/gtest.h"
#include "absl/base/macros.h"
#include "absl/base/port.h"

namespace {

TEST(AsciiIsFoo, All) {
  for (int i = 0; i < 256; i++) {
    if ((i >= 'a' && i <= 'z') || (i >= 'A' && i <= 'Z'))
      EXPECT_TRUE(absl::ascii_isalpha(i)) << ": failed on " << i;
    else
      EXPECT_TRUE(!absl::ascii_isalpha(i)) << ": failed on " << i;
  }
  for (int i = 0; i < 256; i++) {
    if ((i >= '0' && i <= '9'))
      EXPECT_TRUE(absl::ascii_isdigit(i)) << ": failed on " << i;
    else
      EXPECT_TRUE(!absl::ascii_isdigit(i)) << ": failed on " << i;
  }
  for (int i = 0; i < 256; i++) {
    if (absl::ascii_isalpha(i) || absl::ascii_isdigit(i))
      EXPECT_TRUE(absl::ascii_isalnum(i)) << ": failed on " << i;
    else
      EXPECT_TRUE(!absl::ascii_isalnum(i)) << ": failed on " << i;
  }
  for (int i = 0; i < 256; i++) {
    if (i != '\0' && strchr(" \r\n\t\v\f", i))
      EXPECT_TRUE(absl::ascii_isspace(i)) << ": failed on " << i;
    else
      EXPECT_TRUE(!absl::ascii_isspace(i)) << ": failed on " << i;
  }
  for (int i = 0; i < 256; i++) {
    if (i >= 32 && i < 127)
      EXPECT_TRUE(absl::ascii_isprint(i)) << ": failed on " << i;
    else
      EXPECT_TRUE(!absl::ascii_isprint(i)) << ": failed on " << i;
  }
  for (int i = 0; i < 256; i++) {
    if (absl::ascii_isprint(i) && !absl::ascii_isspace(i) &&
        !absl::ascii_isalnum(i))
      EXPECT_TRUE(absl::ascii_ispunct(i)) << ": failed on " << i;
    else
      EXPECT_TRUE(!absl::ascii_ispunct(i)) << ": failed on " << i;
  }
  for (int i = 0; i < 256; i++) {
    if (i == ' ' || i == '\t')
      EXPECT_TRUE(absl::ascii_isblank(i)) << ": failed on " << i;
    else
      EXPECT_TRUE(!absl::ascii_isblank(i)) << ": failed on " << i;
  }
  for (int i = 0; i < 256; i++) {
    if (i < 32 || i == 127)
      EXPECT_TRUE(absl::ascii_iscntrl(i)) << ": failed on " << i;
    else
      EXPECT_TRUE(!absl::ascii_iscntrl(i)) << ": failed on " << i;
  }
  for (int i = 0; i < 256; i++) {
    if (absl::ascii_isdigit(i) || (i >= 'A' && i <= 'F') ||
        (i >= 'a' && i <= 'f'))
      EXPECT_TRUE(absl::ascii_isxdigit(i)) << ": failed on " << i;
    else
      EXPECT_TRUE(!absl::ascii_isxdigit(i)) << ": failed on " << i;
  }
  for (int i = 0; i < 256; i++) {
    if (i > 32 && i < 127)
      EXPECT_TRUE(absl::ascii_isgraph(i)) << ": failed on " << i;
    else
      EXPECT_TRUE(!absl::ascii_isgraph(i)) << ": failed on " << i;
  }
  for (int i = 0; i < 256; i++) {
    if (i >= 'A' && i <= 'Z')
      EXPECT_TRUE(absl::ascii_isupper(i)) << ": failed on " << i;
    else
      EXPECT_TRUE(!absl::ascii_isupper(i)) << ": failed on " << i;
  }
  for (int i = 0; i < 256; i++) {
    if (i >= 'a' && i <= 'z')
      EXPECT_TRUE(absl::ascii_islower(i)) << ": failed on " << i;
    else
      EXPECT_TRUE(!absl::ascii_islower(i)) << ": failed on " << i;
  }
  for (int i = 0; i < 128; i++) {
    EXPECT_TRUE(absl::ascii_isascii(i)) << ": failed on " << i;
  }
  for (int i = 128; i < 256; i++) {
    EXPECT_TRUE(!absl::ascii_isascii(i)) << ": failed on " << i;
  }

  // The official is* functions don't accept negative signed chars, but
  // our absl::ascii_is* functions do.
  for (int i = 0; i < 256; i++) {
    signed char sc = static_cast<signed char>(static_cast<unsigned char>(i));
    EXPECT_EQ(absl::ascii_isalpha(i), absl::ascii_isalpha(sc)) << i;
    EXPECT_EQ(absl::ascii_isdigit(i), absl::ascii_isdigit(sc)) << i;
    EXPECT_EQ(absl::ascii_isalnum(i), absl::ascii_isalnum(sc)) << i;
    EXPECT_EQ(absl::ascii_isspace(i), absl::ascii_isspace(sc)) << i;
    EXPECT_EQ(absl::ascii_ispunct(i), absl::ascii_ispunct(sc)) << i;
    EXPECT_EQ(absl::ascii_isblank(i), absl::ascii_isblank(sc)) << i;
    EXPECT_EQ(absl::ascii_iscntrl(i), absl::ascii_iscntrl(sc)) << i;
    EXPECT_EQ(absl::ascii_isxdigit(i), absl::ascii_isxdigit(sc)) << i;
    EXPECT_EQ(absl::ascii_isprint(i), absl::ascii_isprint(sc)) << i;
    EXPECT_EQ(absl::ascii_isgraph(i), absl::ascii_isgraph(sc)) << i;
    EXPECT_EQ(absl::ascii_isupper(i), absl::ascii_isupper(sc)) << i;
    EXPECT_EQ(absl::ascii_islower(i), absl::ascii_islower(sc)) << i;
    EXPECT_EQ(absl::ascii_isascii(i), absl::ascii_isascii(sc)) << i;
  }
}

// Checks that absl::ascii_isfoo returns the same value as isfoo in the C
// locale.
TEST(AsciiIsFoo, SameAsIsFoo) {
#ifndef __ANDROID__
  // temporarily change locale to C. It should already be C, but just for safety
  const char* old_locale = setlocale(LC_CTYPE, "C");
  ASSERT_TRUE(old_locale != nullptr);
#endif

  for (int i = 0; i < 256; i++) {
    EXPECT_EQ(isalpha(i) != 0, absl::ascii_isalpha(i)) << i;
    EXPECT_EQ(isdigit(i) != 0, absl::ascii_isdigit(i)) << i;
    EXPECT_EQ(isalnum(i) != 0, absl::ascii_isalnum(i)) << i;
    EXPECT_EQ(isspace(i) != 0, absl::ascii_isspace(i)) << i;
    EXPECT_EQ(ispunct(i) != 0, absl::ascii_ispunct(i)) << i;
    EXPECT_EQ(isblank(i) != 0, absl::ascii_isblank(i)) << i;
    EXPECT_EQ(iscntrl(i) != 0, absl::ascii_iscntrl(i)) << i;
    EXPECT_EQ(isxdigit(i) != 0, absl::ascii_isxdigit(i)) << i;
    EXPECT_EQ(isprint(i) != 0, absl::ascii_isprint(i)) << i;
    EXPECT_EQ(isgraph(i) != 0, absl::ascii_isgraph(i)) << i;
    EXPECT_EQ(isupper(i) != 0, absl::ascii_isupper(i)) << i;
    EXPECT_EQ(islower(i) != 0, absl::ascii_islower(i)) << i;
    EXPECT_EQ(isascii(i) != 0, absl::ascii_isascii(i)) << i;
  }

#ifndef __ANDROID__
  // restore the old locale.
  ASSERT_TRUE(setlocale(LC_CTYPE, old_locale));
#endif
}

TEST(AsciiToFoo, All) {
#ifndef __ANDROID__
  // temporarily change locale to C. It should already be C, but just for safety
  const char* old_locale = setlocale(LC_CTYPE, "C");
  ASSERT_TRUE(old_locale != nullptr);
#endif

  for (int i = 0; i < 256; i++) {
    if (absl::ascii_islower(i))
      EXPECT_EQ(absl::ascii_toupper(i), 'A' + (i - 'a')) << i;
    else
      EXPECT_EQ(absl::ascii_toupper(i), static_cast<char>(i)) << i;

    if (absl::ascii_isupper(i))
      EXPECT_EQ(absl::ascii_tolower(i), 'a' + (i - 'A')) << i;
    else
      EXPECT_EQ(absl::ascii_tolower(i), static_cast<char>(i)) << i;

    // These CHECKs only hold in a C locale.
    EXPECT_EQ(static_cast<char>(tolower(i)), absl::ascii_tolower(i)) << i;
    EXPECT_EQ(static_cast<char>(toupper(i)), absl::ascii_toupper(i)) << i;

    // The official to* functions don't accept negative signed chars, but
    // our absl::ascii_to* functions do.
    signed char sc = static_cast<signed char>(static_cast<unsigned char>(i));
    EXPECT_EQ(absl::ascii_tolower(i), absl::ascii_tolower(sc)) << i;
    EXPECT_EQ(absl::ascii_toupper(i), absl::ascii_toupper(sc)) << i;
  }
#ifndef __ANDROID__
  // restore the old locale.
  ASSERT_TRUE(setlocale(LC_CTYPE, old_locale));
#endif
}

TEST(AsciiStrTo, Lower) {
  const char buf[] = "ABCDEF";
  const std::string str("GHIJKL");
  const std::string str2("MNOPQR");
  const absl::string_view sp(str2);
  std::string mutable_str("STUVWX");

  EXPECT_EQ("abcdef", absl::AsciiStrToLower(buf));
  EXPECT_EQ("ghijkl", absl::AsciiStrToLower(str));
  EXPECT_EQ("mnopqr", absl::AsciiStrToLower(sp));

  absl::AsciiStrToLower(&mutable_str);
  EXPECT_EQ("stuvwx", mutable_str);

  char mutable_buf[] = "Mutable";
  std::transform(mutable_buf, mutable_buf + strlen(mutable_buf),
                 mutable_buf, absl::ascii_tolower);
  EXPECT_STREQ("mutable", mutable_buf);
}

TEST(AsciiStrTo, Upper) {
  const char buf[] = "abcdef";
  const std::string str("ghijkl");
  const std::string str2("mnopqr");
  const absl::string_view sp(str2);

  EXPECT_EQ("ABCDEF", absl::AsciiStrToUpper(buf));
  EXPECT_EQ("GHIJKL", absl::AsciiStrToUpper(str));
  EXPECT_EQ("MNOPQR", absl::AsciiStrToUpper(sp));

  char mutable_buf[] = "Mutable";
  std::transform(mutable_buf, mutable_buf + strlen(mutable_buf),
                 mutable_buf, absl::ascii_toupper);
  EXPECT_STREQ("MUTABLE", mutable_buf);
}

TEST(StripLeadingAsciiWhitespace, FromStringView) {
  EXPECT_EQ(absl::string_view{},
            absl::StripLeadingAsciiWhitespace(absl::string_view{}));
  EXPECT_EQ("foo", absl::StripLeadingAsciiWhitespace({"foo"}));
  EXPECT_EQ("foo", absl::StripLeadingAsciiWhitespace({"\t  \n\f\r\n\vfoo"}));
  EXPECT_EQ("foo foo\n ",
            absl::StripLeadingAsciiWhitespace({"\t  \n\f\r\n\vfoo foo\n "}));
  EXPECT_EQ(absl::string_view{}, absl::StripLeadingAsciiWhitespace(
                                     {"\t  \n\f\r\v\n\t  \n\f\r\v\n"}));
}

TEST(StripLeadingAsciiWhitespace, InPlace) {
  std::string str;

  absl::StripLeadingAsciiWhitespace(&str);
  EXPECT_EQ("", str);

  str = "foo";
  absl::StripLeadingAsciiWhitespace(&str);
  EXPECT_EQ("foo", str);

  str = "\t  \n\f\r\n\vfoo";
  absl::StripLeadingAsciiWhitespace(&str);
  EXPECT_EQ("foo", str);

  str = "\t  \n\f\r\n\vfoo foo\n ";
  absl::StripLeadingAsciiWhitespace(&str);
  EXPECT_EQ("foo foo\n ", str);

  str = "\t  \n\f\r\v\n\t  \n\f\r\v\n";
  absl::StripLeadingAsciiWhitespace(&str);
  EXPECT_EQ(absl::string_view{}, str);
}

TEST(StripTrailingAsciiWhitespace, FromStringView) {
  EXPECT_EQ(absl::string_view{},
            absl::StripTrailingAsciiWhitespace(absl::string_view{}));
  EXPECT_EQ("foo", absl::StripTrailingAsciiWhitespace({"foo"}));
  EXPECT_EQ("foo", absl::StripTrailingAsciiWhitespace({"foo\t  \n\f\r\n\v"}));
  EXPECT_EQ(" \nfoo foo",
            absl::StripTrailingAsciiWhitespace({" \nfoo foo\t  \n\f\r\n\v"}));
  EXPECT_EQ(absl::string_view{}, absl::StripTrailingAsciiWhitespace(
                                     {"\t  \n\f\r\v\n\t  \n\f\r\v\n"}));
}

TEST(StripTrailingAsciiWhitespace, InPlace) {
  std::string str;

  absl::StripTrailingAsciiWhitespace(&str);
  EXPECT_EQ("", str);

  str = "foo";
  absl::StripTrailingAsciiWhitespace(&str);
  EXPECT_EQ("foo", str);

  str = "foo\t  \n\f\r\n\v";
  absl::StripTrailingAsciiWhitespace(&str);
  EXPECT_EQ("foo", str);

  str = " \nfoo foo\t  \n\f\r\n\v";
  absl::StripTrailingAsciiWhitespace(&str);
  EXPECT_EQ(" \nfoo foo", str);

  str = "\t  \n\f\r\v\n\t  \n\f\r\v\n";
  absl::StripTrailingAsciiWhitespace(&str);
  EXPECT_EQ(absl::string_view{}, str);
}

TEST(StripAsciiWhitespace, FromStringView) {
  EXPECT_EQ(absl::string_view{},
            absl::StripAsciiWhitespace(absl::string_view{}));
  EXPECT_EQ("foo", absl::StripAsciiWhitespace({"foo"}));
  EXPECT_EQ("foo",
            absl::StripAsciiWhitespace({"\t  \n\f\r\n\vfoo\t  \n\f\r\n\v"}));
  EXPECT_EQ("foo foo", absl::StripAsciiWhitespace(
                           {"\t  \n\f\r\n\vfoo foo\t  \n\f\r\n\v"}));
  EXPECT_EQ(absl::string_view{},
            absl::StripAsciiWhitespace({"\t  \n\f\r\v\n\t  \n\f\r\v\n"}));
}

TEST(StripAsciiWhitespace, InPlace) {
  std::string str;

  absl::StripAsciiWhitespace(&str);
  EXPECT_EQ("", str);

  str = "foo";
  absl::StripAsciiWhitespace(&str);
  EXPECT_EQ("foo", str);

  str = "\t  \n\f\r\n\vfoo\t  \n\f\r\n\v";
  absl::StripAsciiWhitespace(&str);
  EXPECT_EQ("foo", str);

  str = "\t  \n\f\r\n\vfoo foo\t  \n\f\r\n\v";
  absl::StripAsciiWhitespace(&str);
  EXPECT_EQ("foo foo", str);

  str = "\t  \n\f\r\v\n\t  \n\f\r\v\n";
  absl::StripAsciiWhitespace(&str);
  EXPECT_EQ(absl::string_view{}, str);
}

TEST(RemoveExtraAsciiWhitespace, InPlace) {
  const char* inputs[] = {"No extra space",
                          "  Leading whitespace",
                          "Trailing whitespace  ",
                          "  Leading and trailing  ",
                          " Whitespace \t  in\v   middle  ",
                          "'Eeeeep!  \n Newlines!\n",
                          "nospaces",
                          "",
                          "\n\t a\t\n\nb \t\n"};

  const char* outputs[] = {
      "No extra space",
      "Leading whitespace",
      "Trailing whitespace",
      "Leading and trailing",
      "Whitespace in middle",
      "'Eeeeep! Newlines!",
      "nospaces",
      "",
      "a\nb",
  };
  const int NUM_TESTS = ABSL_ARRAYSIZE(inputs);

  for (int i = 0; i < NUM_TESTS; i++) {
    std::string s(inputs[i]);
    absl::RemoveExtraAsciiWhitespace(&s);
    EXPECT_EQ(outputs[i], s);
  }
}

}  // namespace
