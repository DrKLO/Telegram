/*
 *  Copyright 2011 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/third_party/base64/base64.h"

#include <stdio.h>
#include <string.h>

#include <algorithm>

#include "rtc_base/logging.h"
#include "rtc_base/test_base64.h"
#include "test/gtest.h"

namespace rtc {
namespace {

static struct {
  size_t plain_length;
  const char* plaintext;
  const char* cyphertext;
} base64_tests[] = {

    // Basic bit patterns;
    // values obtained with "echo -n '...' | uuencode -m test"

    {1, "\000", "AA=="},
    {1, "\001", "AQ=="},
    {1, "\002", "Ag=="},
    {1, "\004", "BA=="},
    {1, "\010", "CA=="},
    {1, "\020", "EA=="},
    {1, "\040", "IA=="},
    {1, "\100", "QA=="},
    {1, "\200", "gA=="},

    {1, "\377", "/w=="},
    {1, "\376", "/g=="},
    {1, "\375", "/Q=="},
    {1, "\373", "+w=="},
    {1, "\367", "9w=="},
    {1, "\357", "7w=="},
    {1, "\337", "3w=="},
    {1, "\277", "vw=="},
    {1, "\177", "fw=="},
    {2, "\000\000", "AAA="},
    {2, "\000\001", "AAE="},
    {2, "\000\002", "AAI="},
    {2, "\000\004", "AAQ="},
    {2, "\000\010", "AAg="},
    {2, "\000\020", "ABA="},
    {2, "\000\040", "ACA="},
    {2, "\000\100", "AEA="},
    {2, "\000\200", "AIA="},
    {2, "\001\000", "AQA="},
    {2, "\002\000", "AgA="},
    {2, "\004\000", "BAA="},
    {2, "\010\000", "CAA="},
    {2, "\020\000", "EAA="},
    {2, "\040\000", "IAA="},
    {2, "\100\000", "QAA="},
    {2, "\200\000", "gAA="},

    {2, "\377\377", "//8="},
    {2, "\377\376", "//4="},
    {2, "\377\375", "//0="},
    {2, "\377\373", "//s="},
    {2, "\377\367", "//c="},
    {2, "\377\357", "/+8="},
    {2, "\377\337", "/98="},
    {2, "\377\277", "/78="},
    {2, "\377\177", "/38="},
    {2, "\376\377", "/v8="},
    {2, "\375\377", "/f8="},
    {2, "\373\377", "+/8="},
    {2, "\367\377", "9/8="},
    {2, "\357\377", "7/8="},
    {2, "\337\377", "3/8="},
    {2, "\277\377", "v/8="},
    {2, "\177\377", "f/8="},

    {3, "\000\000\000", "AAAA"},
    {3, "\000\000\001", "AAAB"},
    {3, "\000\000\002", "AAAC"},
    {3, "\000\000\004", "AAAE"},
    {3, "\000\000\010", "AAAI"},
    {3, "\000\000\020", "AAAQ"},
    {3, "\000\000\040", "AAAg"},
    {3, "\000\000\100", "AABA"},
    {3, "\000\000\200", "AACA"},
    {3, "\000\001\000", "AAEA"},
    {3, "\000\002\000", "AAIA"},
    {3, "\000\004\000", "AAQA"},
    {3, "\000\010\000", "AAgA"},
    {3, "\000\020\000", "ABAA"},
    {3, "\000\040\000", "ACAA"},
    {3, "\000\100\000", "AEAA"},
    {3, "\000\200\000", "AIAA"},
    {3, "\001\000\000", "AQAA"},
    {3, "\002\000\000", "AgAA"},
    {3, "\004\000\000", "BAAA"},
    {3, "\010\000\000", "CAAA"},
    {3, "\020\000\000", "EAAA"},
    {3, "\040\000\000", "IAAA"},
    {3, "\100\000\000", "QAAA"},
    {3, "\200\000\000", "gAAA"},

    {3, "\377\377\377", "////"},
    {3, "\377\377\376", "///+"},
    {3, "\377\377\375", "///9"},
    {3, "\377\377\373", "///7"},
    {3, "\377\377\367", "///3"},
    {3, "\377\377\357", "///v"},
    {3, "\377\377\337", "///f"},
    {3, "\377\377\277", "//+/"},
    {3, "\377\377\177", "//9/"},
    {3, "\377\376\377", "//7/"},
    {3, "\377\375\377", "//3/"},
    {3, "\377\373\377", "//v/"},
    {3, "\377\367\377", "//f/"},
    {3, "\377\357\377", "/+//"},
    {3, "\377\337\377", "/9//"},
    {3, "\377\277\377", "/7//"},
    {3, "\377\177\377", "/3//"},
    {3, "\376\377\377", "/v//"},
    {3, "\375\377\377", "/f//"},
    {3, "\373\377\377", "+///"},
    {3, "\367\377\377", "9///"},
    {3, "\357\377\377", "7///"},
    {3, "\337\377\377", "3///"},
    {3, "\277\377\377", "v///"},
    {3, "\177\377\377", "f///"},

    // Random numbers: values obtained with
    //
    //  #! /bin/bash
    //  dd bs=$1 count=1 if=/dev/random of=/tmp/bar.random
    //  od -N $1 -t o1 /tmp/bar.random
    //  uuencode -m test < /tmp/bar.random
    //
    // where $1 is the number of bytes (2, 3)

    {2, "\243\361", "o/E="},
    {2, "\024\167", "FHc="},
    {2, "\313\252", "y6o="},
    {2, "\046\041", "JiE="},
    {2, "\145\236", "ZZ4="},
    {2, "\254\325", "rNU="},
    {2, "\061\330", "Mdg="},
    {2, "\245\032", "pRo="},
    {2, "\006\000", "BgA="},
    {2, "\375\131", "/Vk="},
    {2, "\303\210", "w4g="},
    {2, "\040\037", "IB8="},
    {2, "\261\372", "sfo="},
    {2, "\335\014", "3Qw="},
    {2, "\233\217", "m48="},
    {2, "\373\056", "+y4="},
    {2, "\247\232", "p5o="},
    {2, "\107\053", "Rys="},
    {2, "\204\077", "hD8="},
    {2, "\276\211", "vok="},
    {2, "\313\110", "y0g="},
    {2, "\363\376", "8/4="},
    {2, "\251\234", "qZw="},
    {2, "\103\262", "Q7I="},
    {2, "\142\312", "Yso="},
    {2, "\067\211", "N4k="},
    {2, "\220\001", "kAE="},
    {2, "\152\240", "aqA="},
    {2, "\367\061", "9zE="},
    {2, "\133\255", "W60="},
    {2, "\176\035", "fh0="},
    {2, "\032\231", "Gpk="},

    {3, "\013\007\144", "Cwdk"},
    {3, "\030\112\106", "GEpG"},
    {3, "\047\325\046", "J9Um"},
    {3, "\310\160\022", "yHAS"},
    {3, "\131\100\237", "WUCf"},
    {3, "\064\342\134", "NOJc"},
    {3, "\010\177\004", "CH8E"},
    {3, "\345\147\205", "5WeF"},
    {3, "\300\343\360", "wOPw"},
    {3, "\061\240\201", "MaCB"},
    {3, "\225\333\044", "ldsk"},
    {3, "\215\137\352", "jV/q"},
    {3, "\371\147\160", "+Wdw"},
    {3, "\030\320\051", "GNAp"},
    {3, "\044\174\241", "JHyh"},
    {3, "\260\127\037", "sFcf"},
    {3, "\111\045\033", "SSUb"},
    {3, "\202\114\107", "gkxH"},
    {3, "\057\371\042", "L/ki"},
    {3, "\223\247\244", "k6ek"},
    {3, "\047\216\144", "J45k"},
    {3, "\203\070\327", "gzjX"},
    {3, "\247\140\072", "p2A6"},
    {3, "\124\115\116", "VE1O"},
    {3, "\157\162\050", "b3Io"},
    {3, "\357\223\004", "75ME"},
    {3, "\052\117\156", "Kk9u"},
    {3, "\347\154\000", "52wA"},
    {3, "\303\012\142", "wwpi"},
    {3, "\060\035\362", "MB3y"},
    {3, "\130\226\361", "WJbx"},
    {3, "\173\013\071", "ews5"},
    {3, "\336\004\027", "3gQX"},
    {3, "\357\366\234", "7/ac"},
    {3, "\353\304\111", "68RJ"},
    {3, "\024\264\131", "FLRZ"},
    {3, "\075\114\251", "PUyp"},
    {3, "\315\031\225", "zRmV"},
    {3, "\154\201\276", "bIG+"},
    {3, "\200\066\072", "gDY6"},
    {3, "\142\350\267", "Yui3"},
    {3, "\033\000\166", "GwB2"},
    {3, "\210\055\077", "iC0/"},
    {3, "\341\037\124", "4R9U"},
    {3, "\161\103\152", "cUNq"},
    {3, "\270\142\131", "uGJZ"},
    {3, "\337\076\074", "3z48"},
    {3, "\375\106\362", "/Uby"},
    {3, "\227\301\127", "l8FX"},
    {3, "\340\002\234", "4AKc"},
    {3, "\121\064\033", "UTQb"},
    {3, "\157\134\143", "b1xj"},
    {3, "\247\055\327", "py3X"},
    {3, "\340\142\005", "4GIF"},
    {3, "\060\260\143", "MLBj"},
    {3, "\075\203\170", "PYN4"},
    {3, "\143\160\016", "Y3AO"},
    {3, "\313\013\063", "ywsz"},
    {3, "\174\236\135", "fJ5d"},
    {3, "\103\047\026", "QycW"},
    {3, "\365\005\343", "9QXj"},
    {3, "\271\160\223", "uXCT"},
    {3, "\362\255\172", "8q16"},
    {3, "\113\012\015", "SwoN"},

    // various lengths, generated by this python script:
    //
    // from string import lowercase as lc
    // for i in range(27):
    //   print '{ %2d, "%s",%s "%s" },' % (i, lc[:i], ' ' * (26-i),
    //                                     lc[:i].encode('base64').strip())

    {0, "abcdefghijklmnopqrstuvwxyz", ""},
    {1, "abcdefghijklmnopqrstuvwxyz", "YQ=="},
    {2, "abcdefghijklmnopqrstuvwxyz", "YWI="},
    {3, "abcdefghijklmnopqrstuvwxyz", "YWJj"},
    {4, "abcdefghijklmnopqrstuvwxyz", "YWJjZA=="},
    {5, "abcdefghijklmnopqrstuvwxyz", "YWJjZGU="},
    {6, "abcdefghijklmnopqrstuvwxyz", "YWJjZGVm"},
    {7, "abcdefghijklmnopqrstuvwxyz", "YWJjZGVmZw=="},
    {8, "abcdefghijklmnopqrstuvwxyz", "YWJjZGVmZ2g="},
    {9, "abcdefghijklmnopqrstuvwxyz", "YWJjZGVmZ2hp"},
    {10, "abcdefghijklmnopqrstuvwxyz", "YWJjZGVmZ2hpag=="},
    {11, "abcdefghijklmnopqrstuvwxyz", "YWJjZGVmZ2hpams="},
    {12, "abcdefghijklmnopqrstuvwxyz", "YWJjZGVmZ2hpamts"},
    {13, "abcdefghijklmnopqrstuvwxyz", "YWJjZGVmZ2hpamtsbQ=="},
    {14, "abcdefghijklmnopqrstuvwxyz", "YWJjZGVmZ2hpamtsbW4="},
    {15, "abcdefghijklmnopqrstuvwxyz", "YWJjZGVmZ2hpamtsbW5v"},
    {16, "abcdefghijklmnopqrstuvwxyz", "YWJjZGVmZ2hpamtsbW5vcA=="},
    {17, "abcdefghijklmnopqrstuvwxyz", "YWJjZGVmZ2hpamtsbW5vcHE="},
    {18, "abcdefghijklmnopqrstuvwxyz", "YWJjZGVmZ2hpamtsbW5vcHFy"},
    {19, "abcdefghijklmnopqrstuvwxyz", "YWJjZGVmZ2hpamtsbW5vcHFycw=="},
    {20, "abcdefghijklmnopqrstuvwxyz", "YWJjZGVmZ2hpamtsbW5vcHFyc3Q="},
    {21, "abcdefghijklmnopqrstuvwxyz", "YWJjZGVmZ2hpamtsbW5vcHFyc3R1"},
    {22, "abcdefghijklmnopqrstuvwxyz", "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dg=="},
    {23, "abcdefghijklmnopqrstuvwxyz", "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnc="},
    {24, "abcdefghijklmnopqrstuvwxyz", "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4"},
    {25, "abcdefghijklmnopqrstuvwxy", "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eQ=="},
    {26, "abcdefghijklmnopqrstuvwxyz", "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo="},
};
#if 0
static struct {
  const char* plaintext;
  const char* cyphertext;
} base64_strings[] = {

  // The first few Google quotes
  // Cyphertext created with "uuencode - GNU sharutils 4.2.1"
  {
    "Everyone!  We're teetering on the brink of disaster."
    " - Sergey Brin, 6/24/99, regarding the company's state "
    "after the unleashing of Netscape/Google search",

    "RXZlcnlvbmUhICBXZSdyZSB0ZWV0ZXJpbmcgb24gdGhlIGJyaW5rIG9mIGRp"
    "c2FzdGVyLiAtIFNlcmdleSBCcmluLCA2LzI0Lzk5LCByZWdhcmRpbmcgdGhl"
    "IGNvbXBhbnkncyBzdGF0ZSBhZnRlciB0aGUgdW5sZWFzaGluZyBvZiBOZXRz"
    "Y2FwZS9Hb29nbGUgc2VhcmNo" },

  {
    "I'm not sure why we're still alive, but we seem to be."
    " - Larry Page, 6/24/99, while hiding in the kitchenette "
    "during the Netscape traffic overflow",

    "SSdtIG5vdCBzdXJlIHdoeSB3ZSdyZSBzdGlsbCBhbGl2ZSwgYnV0IHdlIHNl"
    "ZW0gdG8gYmUuIC0gTGFycnkgUGFnZSwgNi8yNC85OSwgd2hpbGUgaGlkaW5n"
    "IGluIHRoZSBraXRjaGVuZXR0ZSBkdXJpbmcgdGhlIE5ldHNjYXBlIHRyYWZm"
    "aWMgb3ZlcmZsb3c" },

  {
    "I think kids want porn."
    " - Sergey Brin, 6/99, on why Google shouldn't prioritize a "
    "filtered search for children and families",

    "SSB0aGluayBraWRzIHdhbnQgcG9ybi4gLSBTZXJnZXkgQnJpbiwgNi85OSwg"
    "b24gd2h5IEdvb2dsZSBzaG91bGRuJ3QgcHJpb3JpdGl6ZSBhIGZpbHRlcmVk"
    "IHNlYXJjaCBmb3IgY2hpbGRyZW4gYW5kIGZhbWlsaWVz" },
};
#endif
// Compare bytes 0..len-1 of x and y.  If not equal, abort with verbose error
// message showing position and numeric value that differed.
// Handles embedded nulls just like any other byte.
// Only added because string.compare() in gcc-3.3.3 seems to misbehave with
// embedded nulls.
// TODO: switch back to string.compare() if/when gcc is fixed
#define EXPECT_EQ_ARRAY(len, x, y, msg)                        \
  for (size_t j = 0; j < len; ++j) {                           \
    if (x[j] != y[j]) {                                        \
      RTC_LOG(LS_ERROR) << "" #x << " != " #y << " byte " << j \
                        << " msg: " << msg;                    \
    }                                                          \
  }

size_t Base64Escape(const unsigned char* src,
                    size_t szsrc,
                    char* dest,
                    size_t szdest) {
  std::string escaped;
  Base64::EncodeFromArray((const char*)src, szsrc, &escaped);
  memcpy(dest, escaped.data(), std::min(escaped.size(), szdest));
  return escaped.size();
}

size_t Base64Unescape(const char* src,
                      size_t szsrc,
                      char* dest,
                      size_t szdest) {
  std::string unescaped;
  EXPECT_TRUE(
      Base64::DecodeFromArray(src, szsrc, Base64::DO_LAX, &unescaped, nullptr));
  memcpy(dest, unescaped.data(), std::min(unescaped.size(), szdest));
  return unescaped.size();
}

size_t Base64Unescape(const char* src, size_t szsrc, std::string* s) {
  EXPECT_TRUE(Base64::DecodeFromArray(src, szsrc, Base64::DO_LAX, s, nullptr));
  return s->size();
}

TEST(Base64, EncodeDecodeBattery) {
  RTC_LOG(LS_VERBOSE) << "Testing base-64";

  size_t i;

  // Check the short strings; this tests the math (and boundaries)
  for (i = 0; i < sizeof(base64_tests) / sizeof(base64_tests[0]); ++i) {
    char encode_buffer[100];
    size_t encode_length;
    char decode_buffer[100];
    size_t decode_length;
    size_t cypher_length;

    RTC_LOG(LS_VERBOSE) << "B64: " << base64_tests[i].cyphertext;

    const unsigned char* unsigned_plaintext =
        reinterpret_cast<const unsigned char*>(base64_tests[i].plaintext);

    cypher_length = strlen(base64_tests[i].cyphertext);

    // The basic escape function:
    memset(encode_buffer, 0, sizeof(encode_buffer));
    encode_length =
        Base64Escape(unsigned_plaintext, base64_tests[i].plain_length,
                     encode_buffer, sizeof(encode_buffer));
    //    Is it of the expected length?
    EXPECT_EQ(encode_length, cypher_length);

    //    Is it the expected encoded value?
    EXPECT_STREQ(encode_buffer, base64_tests[i].cyphertext);

    // If we encode it into a buffer of exactly the right length...
    memset(encode_buffer, 0, sizeof(encode_buffer));
    encode_length =
        Base64Escape(unsigned_plaintext, base64_tests[i].plain_length,
                     encode_buffer, cypher_length);
    //    Is it still of the expected length?
    EXPECT_EQ(encode_length, cypher_length);

    //    And is the value still correct?  (i.e., not losing the last byte)
    EXPECT_STREQ(encode_buffer, base64_tests[i].cyphertext);

    // If we decode it back:
    memset(decode_buffer, 0, sizeof(decode_buffer));
    decode_length = Base64Unescape(encode_buffer, cypher_length, decode_buffer,
                                   sizeof(decode_buffer));

    //    Is it of the expected length?
    EXPECT_EQ(decode_length, base64_tests[i].plain_length);

    //    Is it the expected decoded value?
    EXPECT_EQ(0,
              memcmp(decode_buffer, base64_tests[i].plaintext, decode_length));

    // Our decoder treats the padding '=' characters at the end as
    // optional.  If encode_buffer has any, run some additional
    // tests that fiddle with them.
    char* first_equals = strchr(encode_buffer, '=');
    if (first_equals) {
      // How many equals signs does the string start with?
      int equals = (*(first_equals + 1) == '=') ? 2 : 1;

      // Try chopping off the equals sign(s) entirely.  The decoder
      // should still be okay with this.
      std::string decoded2("this junk should also be ignored");
      *first_equals = '\0';
      EXPECT_NE(0U, Base64Unescape(encode_buffer, first_equals - encode_buffer,
                                   &decoded2));
      EXPECT_EQ(decoded2.size(), base64_tests[i].plain_length);
      EXPECT_EQ_ARRAY(decoded2.size(), decoded2.data(),
                      base64_tests[i].plaintext, i);

      size_t len;

      // try putting some extra stuff after the equals signs, or in between them
      if (equals == 2) {
        snprintf(first_equals, 6, " = = ");
        len = first_equals - encode_buffer + 5;
      } else {
        snprintf(first_equals, 6, " = ");
        len = first_equals - encode_buffer + 3;
      }
      decoded2.assign("this junk should be ignored");
      EXPECT_NE(0U, Base64Unescape(encode_buffer, len, &decoded2));
      EXPECT_EQ(decoded2.size(), base64_tests[i].plain_length);
      EXPECT_EQ_ARRAY(decoded2.size(), decoded2, base64_tests[i].plaintext, i);
    }
  }
}

// here's a weird case: a giant base64 encoded stream which broke our base64
// decoding.  Let's test it explicitly.
const char SpecificTest[] =
    "/9j/4AAQSkZJRgABAgEASABIAAD/"
    "4Q0HRXhpZgAATU0AKgAAAAgADAEOAAIAAAAgAAAAngEPAAI\n"
    "AAAAFAAAAvgEQAAIAAAAJAAAAwwESAAMAAAABAAEAAAEaAAUAAAABAAAAzAEbAAUAAAABAAAA1"
    "A\n"
    "EoAAMAAAABAAIAAAExAAIAAAAUAAAA3AEyAAIAAAAUAAAA8AE8AAIAAAAQAAABBAITAAMAAAAB"
    "A\n"
    "AIAAIdpAAQAAAABAAABFAAAAsQgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgAFNPTlk"
    "A\n"
    "RFNDLVAyMDAAAAAASAAAAAEAAABIAAAAAUFkb2JlIFBob3Rvc2hvcCA3LjAAMjAwNzowMTozMC"
    "A\n"
    "yMzoxMDowNABNYWMgT1MgWCAxMC40LjgAAByCmgAFAAAAAQAAAmqCnQAFAAAAAQAAAnKIIgADA"
    "A\n"
    "AAAQACAACIJwADAAAAAQBkAACQAAAHAAAABDAyMjCQAwACAAAAFAAAAnqQBAACAAAAFAAAAo6R"
    "A\n"
    "QAHAAAABAECAwCRAgAFAAAAAQAAAqKSBAAKAAAAAQAAAqqSBQAFAAAAAQAAArKSBwADAAAAAQA"
    "F\n"
    "AACSCAADAAAAAQAAAACSCQADAAAAAQAPAACSCgAFAAAAAQAAArqgAAAHAAAABDAxMDCgAQADAA"
    "A\n"
    "AAf//"
    "AACgAgAEAAAAAQAAAGSgAwAEAAAAAQAAAGSjAAAHAAAAAQMAAACjAQAHAAAAAQEAAACkAQ\n"
    "ADAAAAAQAAAACkAgADAAAAAQAAAACkAwADAAAAAQAAAACkBgADAAAAAQAAAACkCAADAAAAAQAA"
    "A\n"
    "ACkCQADAAAAAQAAAACkCgADAAAAAQAAAAAAAAAAAAAACgAAAZAAAAAcAAAACjIwMDc6MDE6MjA"
    "g\n"
    "MjM6MDU6NTIAMjAwNzowMToyMCAyMzowNTo1MgAAAAAIAAAAAQAAAAAAAAAKAAAAMAAAABAAAA"
    "B\n"
    "PAAAACgAAAAYBAwADAAAAAQAGAAABGgAFAAAAAQAAAxIBGwAFAAAAAQAAAxoBKAADAAAAAQACA"
    "A\n"
    "ACAQAEAAAAAQAAAyICAgAEAAAAAQAACd0AAAAAAAAASAAAAAEAAABIAAAAAf/Y/"
    "+AAEEpGSUYAA\n"
    "QIBAEgASAAA/+0ADEFkb2JlX0NNAAL/7gAOQWRvYmUAZIAAAAAB/"
    "9sAhAAMCAgICQgMCQkMEQsK\n"
    "CxEVDwwMDxUYExMVExMYEQwMDAwMDBEMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMAQ0LCw"
    "0\n"
    "ODRAODhAUDg4OFBQODg4OFBEMDAwMDBERDAwMDAwMEQwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMD"
    "A\n"
    "wMDAz/wAARCABkAGQDASIAAhEBAxEB/90ABAAH/"
    "8QBPwAAAQUBAQEBAQEAAAAAAAAAAwABAgQFB\n"
    "gcICQoLAQABBQEBAQEBAQAAAAAAAAABAAIDBAUGBwgJCgsQAAEEAQMCBAIFBwYIBQMMMwEAAhE"
    "D\n"
    "BCESMQVBUWETInGBMgYUkaGxQiMkFVLBYjM0coLRQwclklPw4fFjczUWorKDJkSTVGRFwqN0Nh"
    "f\n"
    "SVeJl8rOEw9N14/"
    "NGJ5SkhbSVxNTk9KW1xdXl9VZmdoaWprbG1ub2N0dXZ3eHl6e3x9fn9xEAAg\n"
    "IBAgQEAwQFBgcHBgU1AQACEQMhMRIEQVFhcSITBTKBkRShsUIjwVLR8DMkYuFygpJDUxVjczTx"
    "J\n"
    "QYWorKDByY1wtJEk1SjF2RFVTZ0ZeLys4TD03Xj80aUpIW0lcTU5PSltcXV5fVWZnaGlqa2xtb"
    "m\n"
    "9ic3R1dnd4eXp7fH/"
    "9oADAMBAAIRAxEAPwDy7bKNTUXNLz9EaJPDWMjxH4ozhtpYwaACT8ShaaW\n"
    "bW0uEc9/JFfjj0Q4Hk/PRDxwX7y47W9z/"
    "AN9Cv4+O3ILK2DcRqT2CaSvEbcl1Jbz37KG1dBldLo\n"
    "qaS4l9xGjG9v6yoDAdYIaIjUk+AREgo4y5sapirb8Yl0NHHdKvBNm4yA1o5Pc+"
    "SPEFvCWqB3HZF\n"
    "Hj2SbWQ/"
    "afGFP0bHP8ATY0uc4w1o1JPkkimGiS2KvqlnmBkOZQTyydzgPMM9v8A0lp4v1Nx9gF1\n"
    "tpdqJaGtH/S3I0i3lISXW/8AMqnd/O2bfg2eUkqVYf/"
    "Q8zuncO4Bj7lZ+n7f5Mj5KsJcY8NUZ4d\n"
    "uEDVo1HkeU0rg3Om4H2rabCWUN7DQuK1n5FWKW4uCwG92gDRJBS6exhxmMboQI+"
    "Cv4WFTQ42Bs2\n"
    "fvnkkqEmy2YxoMMbpVzaz6jt+RbpHZs8lzkHqrasKkYOKP0jgDfZ4N/"
    "wDM1tNrcWfSPmRyq9uNV\n"
    "DnFg2s97i7UkjxKVrq0eVz3spZsja+ASDzwsh9jnOk/"
    "JFzb3XZD3v1c4yT8UACTCniKDUnKz5Nj\n"
    "G33XV1DV73BrT8dF23SejV4zg9g33cOsPb+SxVvqv9ViwNy8vS0iWs/"
    "daf8A0Y5dpTi1sADGxCR\n"
    "K1o0YBEmInlXWYbDBcDLdPJXa8f71Yrx2jnUoAqLnfZK5hJaW2vdwEk5a/wD/0fN6Ia/"
    "e76IiVf\n"
    "xavUL7CPpnT4LNbYXAVjuQt/AqDmNYO/"
    "Kjnoy4hr5J8SwMhrRMaeSvbsxrfUazcOw4UX0Cisem2\n"
    "SBoD4+"
    "Kz8nC6llbSLCRrubJA8kwUWbUDa29X1PMa7aQWjuDC0MXMdbDbhI7eazBiUfZ6GOYRe1s\n"
    "WvGgJ8Vbw2+m4Bx9s6JpNHuuGo1FF53r/"
    "SHYua61gLse0lzXeBP5rkvqx0o5vVWz7WY49QkiQSP\n"
    "oN/tLoevW/ogxv0HA7tJ0AnhT+pdDGYVl/wCdcTPkGn2NU0JWNWvlgAbHV6fEqdu2gR/"
    "r2WlWwt\n"
    "AA5VXAEsLXTqJafArQY5rRr9LiPBJiZsZCI1pJjxCi0j4oncSICSkWwzwkjeaSch//"
    "0vO7sP7Lm\n"
    "enO9ogtd5FbPT3Q5pCpZVc4ld3Lmn3O8j9EI2BYdunKjOobMQIyI+rusc2wx4d0eutwGnHh/"
    "uQc\n"
    "Ha7ladj6mVANGvcqOgz0Go7HJ12/GEHcwvB/dPY6ImbbaMaASGuIBjkN7qofs9Ubg9g7OI9p/"
    "t/\n"
    "RTSmhTHr0v6eSz6UgCPP2/wAVu9Ex2V49dVY2iACB4BZeVXQ/"
    "AJ3gzGnnOi2+kACpru8flUsNmt\n"
    "zHRf6xfWCnoeAfTh2ZaQKazx/"
    "Ke7+QxcKz61fWA2uuObaC4zGhaPJrXBL64ZFmR124O09ENraPK\n"
    "N3/AH5GqxIrZVUyp2K2vfdkENsDnxuex9m4Ox9n82xSgNd9D+p/XR1npgseR9ppOy4Dx/NfH/"
    "CL\n"
    "oQJGunmvMv8AFq3KHVcq3HkYQbD2nuSf0I/rMavSg6TLjLigQhJ7Z58v9QkmlsTOqSCn/"
    "9PzL7R\n"
    "d6Qq3n0wZ2zotXpT9xLfFYvkr/S7jXeB8E0jRkhKpC3q8LcJ/kmCrTnkuAPCq4do9Q/"
    "ytVbuAeY\n"
    "Gg5lQybQK+"
    "82GBqEQUA1kOHPYf3LLsoyN36G5w8iUfHxepbXE2l0cApALgLHzBq9UxhTXU5hMC1\n"
    "ktnSCup6S4Ctk+C5XqVGcaHPfuiuHkeTTuWz0+9zaKiH6CC0/yXBSQ2a/"
    "MxojV57634rq+v2PLY\n"
    "be1r2nsYG13/"
    "AFKxbfCBMcr0brGAzrGEwCG31ncx0SfBzf7S4+zoHUWWsJq3hz9oLfcBH77R9H+\n"
    "0pA13u/qPgDp/Q6ri39JlfpXkDx+h/"
    "msWn1L6wdO6bSbcrIbU2Q0xLnSe21kuVejJspbVS5+4bd\n"
    "ocBAkD/orG+tP1ar67Wy7GtZTm1SCXfRsb+a18fRe38x6SG3/44H1Z3f0y2I+l6DoSXD/"
    "8xPrDs\n"
    "3enVu3bdnqN3R+//USSVo//"
    "1PLohhce+gRWS0Nsby3lRgFkKxQyW7SgUh3em5Tbq2uB9wWw1wey\n"
    "J1XGV2XYdm5k7e4WzidXY9oMwo5RZ4T6Hd1ixwfp96PWbAJBVTHzK7O6Ky5oJB1HZMqmUEFlkG"
    "y\n"
    "xpa4zI1Hkq31dy7bMN9BAc3HeWAnnbyxEycmuup1jiAGglZ31PyrmZ9tQg1WtNj54EHR3/"
    "S2qTH\n"
    "1Yc5GgD1FFtzPdWGkd2AyflogZmRmsz6PSrbXbdo+"
    "txOrP337f3fzVo15DK2uyrTtqpBOnBKx6b\n"
    "7MjJsz7tHWOAYP3WD6LU6cqGjFCNl1MmvLcxv6YtDTLSAqP27LrdtYHXFnJZI+"
    "Tp3MWg68OpDPv\n"
    "UMUM2lkQBoouKQ6swjE9Nml+1sz1PW+z6xt27zuj+skrX2ZvqR5z8kkuOfdPt43/1fMm/"
    "grFG6f\n"
    "Lss9JA7JG7tnZs/SfJUrfS3foJ9TvHCopJsV8nWx/t24bJn8Fo/5TjWJXMJIS+i+G36TsZ/"
    "7Q9P\n"
    "8ATfzfeOFofVSZv2/zvt+O3X/v65dJPjt/BiyfN1/wn0zre79nVej/ADG8ep4x2/"
    "6Srjd6TdviF\n"
    "52ko8m6/Ht9X1KnftEo+POwxzK8mSTF46vrH6T1/OEl5Okkl//Z/"
    "+0uHFBob3Rvc2hvcCAzLjAA\n"
    "OEJJTQQEAAAAAAArHAIAAAIAAhwCeAAfICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIA"
    "A\n"
    "4QklNBCUAAAAAABD7Caa9B0wqNp2P4sxXqayFOEJJTQPqAAAAAB2wPD94bWwgdmVyc2lvbj0iM"
    "S\n"
    "4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPCFET0NUWVBFIHBsaXN0IFBVQkxJQyAiLS8vQXBwbGUg"
    "Q\n"
    "29tcHV0ZXIvL0RURCBQTElTVCAxLjAvL0VOIiAiaHR0cDovL3d3dy5hcHBsZS5jb20vRFREcy9"
    "Q\n"
    "cm9wZXJ0eUxpc3QtMS4wLmR0ZCI+"
    "CjxwbGlzdCB2ZXJzaW9uPSIxLjAiPgo8ZGljdD4KCTxrZXk\n"
    "+Y29tLmFwcGxlLnByaW50LlBhZ2VGb3JtYXQuUE1Ib3Jpem9udGFsUmVzPC9rZXk+"
    "Cgk8ZGljdD\n"
    "4KCQk8a2V5PmNvbS5hcHBsZS5wcmludC50aWNrZXQuY3JlYXRvcjwva2V5PgoJCTxzdHJpbmc+"
    "Y\n"
    "29tLmFwcGxlLnByaW50aW5nbWFuYWdlcjwvc3RyaW5nPgoJCTxrZXk+"
    "Y29tLmFwcGxlLnByaW50\n"
    "LnRpY2tldC5pdGVtQXJyYXk8L2tleT4KCQk8YXJyYXk+"
    "CgkJCTxkaWN0PgoJCQkJPGtleT5jb20\n"
    "uYXBwbGUucHJpbnQuUGFnZUZvcm1hdC5QTUhvcml6b250YWxSZXM8L2tleT4KCQkJCTxyZWFsP"
    "j\n"
    "cyPC9yZWFsPgoJCQkJPGtleT5jb20uYXBwbGUucHJpbnQudGlja2V0LmNsaWVudDwva2V5PgoJ"
    "C\n"
    "QkJPHN0cmluZz5jb20uYXBwbGUucHJpbnRpbmdtYW5hZ2VyPC9zdHJpbmc+"
    "CgkJCQk8a2V5PmNv\n"
    "bS5hcHBsZS5wcmludC50aWNrZXQubW9kRGF0ZTwva2V5PgoJCQkJPGRhdGU+"
    "MjAwNy0wMS0zMFQ\n"
    "yMjowODo0MVo8L2RhdGU+"
    "CgkJCQk8a2V5PmNvbS5hcHBsZS5wcmludC50aWNrZXQuc3RhdGVGbG\n"
    "FnPC9rZXk+CgkJCQk8aW50ZWdlcj4wPC9pbnRlZ2VyPgoJCQk8L2RpY3Q+"
    "CgkJPC9hcnJheT4KC\n"
    "TwvZGljdD4KCTxrZXk+"
    "Y29tLmFwcGxlLnByaW50LlBhZ2VGb3JtYXQuUE1PcmllbnRhdGlvbjwv\n"
    "a2V5PgoJPGRpY3Q+"
    "CgkJPGtleT5jb20uYXBwbGUucHJpbnQudGlja2V0LmNyZWF0b3I8L2tleT4\n"
    "KCQk8c3RyaW5nPmNvbS5hcHBsZS5wcmludGluZ21hbmFnZXI8L3N0cmluZz4KCQk8a2V5PmNvb"
    "S\n"
    "5hcHBsZS5wcmludC50aWNrZXQuaXRlbUFycmF5PC9rZXk+"
    "CgkJPGFycmF5PgoJCQk8ZGljdD4KC\n"
    "QkJCTxrZXk+"
    "Y29tLmFwcGxlLnByaW50LlBhZ2VGb3JtYXQuUE1PcmllbnRhdGlvbjwva2V5PgoJ\n"
    "CQkJPGludGVnZXI+MTwvaW50ZWdlcj4KCQkJCTxrZXk+"
    "Y29tLmFwcGxlLnByaW50LnRpY2tldC5\n"
    "jbGllbnQ8L2tleT4KCQkJCTxzdHJpbmc+"
    "Y29tLmFwcGxlLnByaW50aW5nbWFuYWdlcjwvc3RyaW\n"
    "5nPgoJCQkJPGtleT5jb20uYXBwbGUucHJpbnQudGlja2V0Lm1vZERhdGU8L2tleT4KCQkJCTxk"
    "Y\n"
    "XRlPjIwMDctMDEtMzBUMjI6MDg6NDFaPC9kYXRlPgoJCQkJPGtleT5jb20uYXBwbGUucHJpbnQ"
    "u\n"
    "dGlja2V0LnN0YXRlRmxhZzwva2V5PgoJCQkJPGludGVnZXI+"
    "MDwvaW50ZWdlcj4KCQkJPC9kaWN\n"
    "0PgoJCTwvYXJyYXk+Cgk8L2RpY3Q+"
    "Cgk8a2V5PmNvbS5hcHBsZS5wcmludC5QYWdlRm9ybWF0Ll\n"
    "BNU2NhbGluZzwva2V5PgoJPGRpY3Q+"
    "CgkJPGtleT5jb20uYXBwbGUucHJpbnQudGlja2V0LmNyZ\n"
    "WF0b3I8L2tleT4KCQk8c3RyaW5nPmNvbS5hcHBsZS5wcmludGluZ21hbmFnZXI8L3N0cmluZz4"
    "K\n"
    "CQk8a2V5PmNvbS5hcHBsZS5wcmludC50aWNrZXQuaXRlbUFycmF5PC9rZXk+"
    "CgkJPGFycmF5Pgo\n"
    "JCQk8ZGljdD4KCQkJCTxrZXk+"
    "Y29tLmFwcGxlLnByaW50LlBhZ2VGb3JtYXQuUE1TY2FsaW5nPC\n"
    "9rZXk+"
    "CgkJCQk8cmVhbD4xPC9yZWFsPgoJCQkJPGtleT5jb20uYXBwbGUucHJpbnQudGlja2V0L\n"
    "mNsaWVudDwva2V5PgoJCQkJPHN0cmluZz5jb20uYXBwbGUucHJpbnRpbmdtYW5hZ2VyPC9zdHJ"
    "p\n"
    "bmc+"
    "CgkJCQk8a2V5PmNvbS5hcHBsZS5wcmludC50aWNrZXQubW9kRGF0ZTwva2V5PgoJCQkJPGR\n"
    "hdGU+MjAwNy0wMS0zMFQyMjowODo0MVo8L2RhdGU+"
    "CgkJCQk8a2V5PmNvbS5hcHBsZS5wcmludC\n"
    "50aWNrZXQuc3RhdGVGbGFnPC9rZXk+"
    "CgkJCQk8aW50ZWdlcj4wPC9pbnRlZ2VyPgoJCQk8L2RpY\n"
    "3Q+CgkJPC9hcnJheT4KCTwvZGljdD4KCTxrZXk+"
    "Y29tLmFwcGxlLnByaW50LlBhZ2VGb3JtYXQu\n"
    "UE1WZXJ0aWNhbFJlczwva2V5PgoJPGRpY3Q+"
    "CgkJPGtleT5jb20uYXBwbGUucHJpbnQudGlja2V\n"
    "0LmNyZWF0b3I8L2tleT4KCQk8c3RyaW5nPmNvbS5hcHBsZS5wcmludGluZ21hbmFnZXI8L3N0c"
    "m\n"
    "luZz4KCQk8a2V5PmNvbS5hcHBsZS5wcmludC50aWNrZXQuaXRlbUFycmF5PC9rZXk+"
    "CgkJPGFyc\n"
    "mF5PgoJCQk8ZGljdD4KCQkJCTxrZXk+"
    "Y29tLmFwcGxlLnByaW50LlBhZ2VGb3JtYXQuUE1WZXJ0\n"
    "aWNhbFJlczwva2V5PgoJCQkJPHJlYWw+NzI8L3JlYWw+"
    "CgkJCQk8a2V5PmNvbS5hcHBsZS5wcml\n"
    "udC50aWNrZXQuY2xpZW50PC9rZXk+"
    "CgkJCQk8c3RyaW5nPmNvbS5hcHBsZS5wcmludGluZ21hbm\n"
    "FnZXI8L3N0cmluZz4KCQkJCTxrZXk+"
    "Y29tLmFwcGxlLnByaW50LnRpY2tldC5tb2REYXRlPC9rZ\n"
    "Xk+CgkJCQk8ZGF0ZT4yMDA3LTAxLTMwVDIyOjA4OjQxWjwvZGF0ZT4KCQkJCTxrZXk+"
    "Y29tLmFw\n"
    "cGxlLnByaW50LnRpY2tldC5zdGF0ZUZsYWc8L2tleT4KCQkJCTxpbnRlZ2VyPjA8L2ludGVnZX"
    "I\n"
    "+CgkJCTwvZGljdD4KCQk8L2FycmF5PgoJPC9kaWN0PgoJPGtleT5jb20uYXBwbGUucHJpbnQuU"
    "G\n"
    "FnZUZvcm1hdC5QTVZlcnRpY2FsU2NhbGluZzwva2V5PgoJPGRpY3Q+"
    "CgkJPGtleT5jb20uYXBwb\n"
    "GUucHJpbnQudGlja2V0LmNyZWF0b3I8L2tleT4KCQk8c3RyaW5nPmNvbS5hcHBsZS5wcmludGl"
    "u\n"
    "Z21hbmFnZXI8L3N0cmluZz4KCQk8a2V5PmNvbS5hcHBsZS5wcmludC50aWNrZXQuaXRlbUFycm"
    "F\n"
    "5PC9rZXk+CgkJPGFycmF5PgoJCQk8ZGljdD4KCQkJCTxrZXk+"
    "Y29tLmFwcGxlLnByaW50LlBhZ2\n"
    "VGb3JtYXQuUE1WZXJ0aWNhbFNjYWxpbmc8L2tleT4KCQkJCTxyZWFsPjE8L3JlYWw+"
    "CgkJCQk8a\n"
    "2V5PmNvbS5hcHBsZS5wcmludC50aWNrZXQuY2xpZW50PC9rZXk+"
    "CgkJCQk8c3RyaW5nPmNvbS5h\n"
    "cHBsZS5wcmludGluZ21hbmFnZXI8L3N0cmluZz4KCQkJCTxrZXk+"
    "Y29tLmFwcGxlLnByaW50LnR\n"
    "pY2tldC5tb2REYXRlPC9rZXk+"
    "CgkJCQk8ZGF0ZT4yMDA3LTAxLTMwVDIyOjA4OjQxWjwvZGF0ZT\n"
    "4KCQkJCTxrZXk+"
    "Y29tLmFwcGxlLnByaW50LnRpY2tldC5zdGF0ZUZsYWc8L2tleT4KCQkJCTxpb\n"
    "nRlZ2VyPjA8L2ludGVnZXI+"
    "CgkJCTwvZGljdD4KCQk8L2FycmF5PgoJPC9kaWN0PgoJPGtleT5j\n"
    "b20uYXBwbGUucHJpbnQuc3ViVGlja2V0LnBhcGVyX2luZm9fdGlja2V0PC9rZXk+"
    "Cgk8ZGljdD4\n"
    "KCQk8a2V5PmNvbS5hcHBsZS5wcmludC5QYWdlRm9ybWF0LlBNQWRqdXN0ZWRQYWdlUmVjdDwva"
    "2\n"
    "V5PgoJCTxkaWN0PgoJCQk8a2V5PmNvbS5hcHBsZS5wcmludC50aWNrZXQuY3JlYXRvcjwva2V5"
    "P\n"
    "goJCQk8c3RyaW5nPmNvbS5hcHBsZS5wcmludGluZ21hbmFnZXI8L3N0cmluZz4KCQkJPGtleT5"
    "j\n"
    "b20uYXBwbGUucHJpbnQudGlja2V0Lml0ZW1BcnJheTwva2V5PgoJCQk8YXJyYXk+"
    "CgkJCQk8ZGl\n"
    "jdD4KCQkJCQk8a2V5PmNvbS5hcHBsZS5wcmludC5QYWdlRm9ybWF0LlBNQWRqdXN0ZWRQYWdlU"
    "m\n"
    "VjdDwva2V5PgoJCQkJCTxhcnJheT4KCQkJCQkJPHJlYWw+"
    "MC4wPC9yZWFsPgoJCQkJCQk8cmVhb\n"
    "D4wLjA8L3JlYWw+CgkJCQkJCTxyZWFsPjczNDwvcmVhbD4KCQkJCQkJPHJlYWw+"
    "NTc2PC9yZWFs\n"
    "PgoJCQkJCTwvYXJyYXk+"
    "CgkJCQkJPGtleT5jb20uYXBwbGUucHJpbnQudGlja2V0LmNsaWVudDw\n"
    "va2V5PgoJCQkJCTxzdHJpbmc+"
    "Y29tLmFwcGxlLnByaW50aW5nbWFuYWdlcjwvc3RyaW5nPgoJCQ\n"
    "kJCTxrZXk+Y29tLmFwcGxlLnByaW50LnRpY2tldC5tb2REYXRlPC9rZXk+CgkJCQkJPGRhdGU+"
    "M\n"
    "jAwNy0wMS0zMFQyMjowODo0MVo8L2RhdGU+"
    "CgkJCQkJPGtleT5jb20uYXBwbGUucHJpbnQudGlj\n"
    "a2V0LnN0YXRlRmxhZzwva2V5PgoJCQkJCTxpbnRlZ2VyPjA8L2ludGVnZXI+"
    "CgkJCQk8L2RpY3Q\n"
    "+CgkJCTwvYXJyYXk+CgkJPC9kaWN0PgoJCTxrZXk+"
    "Y29tLmFwcGxlLnByaW50LlBhZ2VGb3JtYX\n"
    "QuUE1BZGp1c3RlZFBhcGVyUmVjdDwva2V5PgoJCTxkaWN0PgoJCQk8a2V5PmNvbS5hcHBsZS5w"
    "c\n"
    "mludC50aWNrZXQuY3JlYXRvcjwva2V5PgoJCQk8c3RyaW5nPmNvbS5hcHBsZS5wcmludGluZ21"
    "h\n"
    "bmFnZXI8L3N0cmluZz4KCQkJPGtleT5jb20uYXBwbGUucHJpbnQudGlja2V0Lml0ZW1BcnJheT"
    "w\n"
    "va2V5PgoJCQk8YXJyYXk+"
    "CgkJCQk8ZGljdD4KCQkJCQk8a2V5PmNvbS5hcHBsZS5wcmludC5QYW\n"
    "dlRm9ybWF0LlBNQWRqdXN0ZWRQYXBlclJlY3Q8L2tleT4KCQkJCQk8YXJyYXk+"
    "CgkJCQkJCTxyZ\n"
    "WFsPi0xODwvcmVhbD4KCQkJCQkJPHJlYWw+"
    "LTE4PC9yZWFsPgoJCQkJCQk8cmVhbD43NzQ8L3Jl\n"
    "YWw+CgkJCQkJCTxyZWFsPjU5NDwvcmVhbD4KCQkJCQk8L2FycmF5PgoJCQkJCTxrZXk+"
    "Y29tLmF\n"
    "wcGxlLnByaW50LnRpY2tldC5jbGllbnQ8L2tleT4KCQkJCQk8c3RyaW5nPmNvbS5hcHBsZS5wc"
    "m\n"
    "ludGluZ21hbmFnZXI8L3N0cmluZz4KCQkJCQk8a2V5PmNvbS5hcHBsZS5wcmludC50aWNrZXQu"
    "b\n"
    "W9kRGF0ZTwva2V5PgoJCQkJCTxkYXRlPjIwMDctMDEtMzBUMjI6MDg6NDFaPC9kYXRlPgoJCQk"
    "J\n"
    "CTxrZXk+"
    "Y29tLmFwcGxlLnByaW50LnRpY2tldC5zdGF0ZUZsYWc8L2tleT4KCQkJCQk8aW50ZWd\n"
    "lcj4wPC9pbnRlZ2VyPgoJCQkJPC9kaWN0PgoJCQk8L2FycmF5PgoJCTwvZGljdD4KCQk8a2V5P"
    "m\n"
    "NvbS5hcHBsZS5wcmludC5QYXBlckluZm8uUE1QYXBlck5hbWU8L2tleT4KCQk8ZGljdD4KCQkJ"
    "P\n"
    "GtleT5jb20uYXBwbGUucHJpbnQudGlja2V0LmNyZWF0b3I8L2tleT4KCQkJPHN0cmluZz5jb20"
    "u\n"
    "YXBwbGUucHJpbnQucG0uUG9zdFNjcmlwdDwvc3RyaW5nPgoJCQk8a2V5PmNvbS5hcHBsZS5wcm"
    "l\n"
    "udC50aWNrZXQuaXRlbUFycmF5PC9rZXk+"
    "CgkJCTxhcnJheT4KCQkJCTxkaWN0PgoJCQkJCTxrZX\n"
    "k+"
    "Y29tLmFwcGxlLnByaW50LlBhcGVySW5mby5QTVBhcGVyTmFtZTwva2V5PgoJCQkJCTxzdHJpb"
    "\n"
    "mc+bmEtbGV0dGVyPC9zdHJpbmc+"
    "CgkJCQkJPGtleT5jb20uYXBwbGUucHJpbnQudGlja2V0LmNs\n"
    "aWVudDwva2V5PgoJCQkJCTxzdHJpbmc+"
    "Y29tLmFwcGxlLnByaW50LnBtLlBvc3RTY3JpcHQ8L3N\n"
    "0cmluZz4KCQkJCQk8a2V5PmNvbS5hcHBsZS5wcmludC50aWNrZXQubW9kRGF0ZTwva2V5PgoJC"
    "Q\n"
    "kJCTxkYXRlPjIwMDMtMDctMDFUMTc6NDk6MzZaPC9kYXRlPgoJCQkJCTxrZXk+"
    "Y29tLmFwcGxlL\n"
    "nByaW50LnRpY2tldC5zdGF0ZUZsYWc8L2tleT4KCQkJCQk8aW50ZWdlcj4xPC9pbnRlZ2VyPgo"
    "J\n"
    "CQkJPC9kaWN0PgoJCQk8L2FycmF5PgoJCTwvZGljdD4KCQk8a2V5PmNvbS5hcHBsZS5wcmludC"
    "5\n"
    "QYXBlckluZm8uUE1VbmFkanVzdGVkUGFnZVJlY3Q8L2tleT4KCQk8ZGljdD4KCQkJPGtleT5jb"
    "2\n"
    "0uYXBwbGUucHJpbnQudGlja2V0LmNyZWF0b3I8L2tleT4KCQkJPHN0cmluZz5jb20uYXBwbGUu"
    "c\n"
    "HJpbnQucG0uUG9zdFNjcmlwdDwvc3RyaW5nPgoJCQk8a2V5PmNvbS5hcHBsZS5wcmludC50aWN"
    "r\n"
    "ZXQuaXRlbUFycmF5PC9rZXk+CgkJCTxhcnJheT4KCQkJCTxkaWN0PgoJCQkJCTxrZXk+"
    "Y29tLmF\n"
    "wcGxlLnByaW50LlBhcGVySW5mby5QTVVuYWRqdXN0ZWRQYWdlUmVjdDwva2V5PgoJCQkJCTxhc"
    "n\n"
    "JheT4KCQkJCQkJPHJlYWw+MC4wPC9yZWFsPgoJCQkJCQk8cmVhbD4wLjA8L3JlYWw+"
    "CgkJCQkJC\n"
    "TxyZWFsPjczNDwvcmVhbD4KCQkJCQkJPHJlYWw+NTc2PC9yZWFsPgoJCQkJCTwvYXJyYXk+"
    "CgkJ\n"
    "CQkJPGtleT5jb20uYXBwbGUucHJpbnQudGlja2V0LmNsaWVudDwva2V5PgoJCQkJCTxzdHJpbm"
    "c\n"
    "+Y29tLmFwcGxlLnByaW50aW5nbWFuYWdlcjwvc3RyaW5nPgoJCQkJCTxrZXk+"
    "Y29tLmFwcGxlLn\n"
    "ByaW50LnRpY2tldC5tb2REYXRlPC9rZXk+CgkJCQkJPGRhdGU+"
    "MjAwNy0wMS0zMFQyMjowODo0M\n"
    "Vo8L2RhdGU+"
    "CgkJCQkJPGtleT5jb20uYXBwbGUucHJpbnQudGlja2V0LnN0YXRlRmxhZzwva2V5\n"
    "PgoJCQkJCTxpbnRlZ2VyPjA8L2ludGVnZXI+CgkJCQk8L2RpY3Q+CgkJCTwvYXJyYXk+"
    "CgkJPC9\n"
    "kaWN0PgoJCTxrZXk+"
    "Y29tLmFwcGxlLnByaW50LlBhcGVySW5mby5QTVVuYWRqdXN0ZWRQYXBlcl\n"
    "JlY3Q8L2tleT4KCQk8ZGljdD4KCQkJPGtleT5jb20uYXBwbGUucHJpbnQudGlja2V0LmNyZWF0"
    "b\n"
    "3I8L2tleT4KCQkJPHN0cmluZz5jb20uYXBwbGUucHJpbnQucG0uUG9zdFNjcmlwdDwvc3RyaW5"
    "n\n"
    "PgoJCQk8a2V5PmNvbS5hcHBsZS5wcmludC50aWNrZXQuaXRlbUFycmF5PC9rZXk+"
    "CgkJCTxhcnJ\n"
    "heT4KCQkJCTxkaWN0PgoJCQkJCTxrZXk+"
    "Y29tLmFwcGxlLnByaW50LlBhcGVySW5mby5QTVVuYW\n"
    "RqdXN0ZWRQYXBlclJlY3Q8L2tleT4KCQkJCQk8YXJyYXk+"
    "CgkJCQkJCTxyZWFsPi0xODwvcmVhb\n"
    "D4KCQkJCQkJPHJlYWw+LTE4PC9yZWFsPgoJCQkJCQk8cmVhbD43NzQ8L3JlYWw+"
    "CgkJCQkJCTxy\n"
    "ZWFsPjU5NDwvcmVhbD4KCQkJCQk8L2FycmF5PgoJCQkJCTxrZXk+"
    "Y29tLmFwcGxlLnByaW50LnR\n"
    "pY2tldC5jbGllbnQ8L2tleT4KCQkJCQk8c3RyaW5nPmNvbS5hcHBsZS5wcmludGluZ21hbmFnZ"
    "X\n"
    "I8L3N0cmluZz4KCQkJCQk8a2V5PmNvbS5hcHBsZS5wcmludC50aWNrZXQubW9kRGF0ZTwva2V5"
    "P\n"
    "goJCQkJCTxkYXRlPjIwMDctMDEtMzBUMjI6MDg6NDFaPC9kYXRlPgoJCQkJCTxrZXk+"
    "Y29tLmFw\n"
    "cGxlLnByaW50LnRpY2tldC5zdGF0ZUZsYWc8L2tleT4KCQkJCQk8aW50ZWdlcj4wPC9pbnRlZ2"
    "V\n"
    "yPgoJCQkJPC9kaWN0PgoJCQk8L2FycmF5PgoJCTwvZGljdD4KCQk8a2V5PmNvbS5hcHBsZS5wc"
    "m\n"
    "ludC5QYXBlckluZm8ucHBkLlBNUGFwZXJOYW1lPC9rZXk+CgkJPGRpY3Q+CgkJCTxrZXk+"
    "Y29tL\n"
    "mFwcGxlLnByaW50LnRpY2tldC5jcmVhdG9yPC9rZXk+CgkJCTxzdHJpbmc+"
    "Y29tLmFwcGxlLnBy\n"
    "aW50LnBtLlBvc3RTY3JpcHQ8L3N0cmluZz4KCQkJPGtleT5jb20uYXBwbGUucHJpbnQudGlja2"
    "V\n"
    "0Lml0ZW1BcnJheTwva2V5PgoJCQk8YXJyYXk+"
    "CgkJCQk8ZGljdD4KCQkJCQk8a2V5PmNvbS5hcH\n"
    "BsZS5wcmludC5QYXBlckluZm8ucHBkLlBNUGFwZXJOYW1lPC9rZXk+"
    "CgkJCQkJPHN0cmluZz5VU\n"
    "yBMZXR0ZXI8L3N0cmluZz4KCQkJCQk8a2V5PmNvbS5hcHBsZS5wcmludC50aWNrZXQuY2xpZW5"
    "0\n"
    "PC9rZXk+"
    "CgkJCQkJPHN0cmluZz5jb20uYXBwbGUucHJpbnQucG0uUG9zdFNjcmlwdDwvc3RyaW5\n"
    "nPgoJCQkJCTxrZXk+Y29tLmFwcGxlLnByaW50LnRpY2tldC5tb2REYXRlPC9rZXk+"
    "CgkJCQkJPG\n"
    "RhdGU+MjAwMy0wNy0wMVQxNzo0OTozNlo8L2RhdGU+"
    "CgkJCQkJPGtleT5jb20uYXBwbGUucHJpb\n"
    "nQudGlja2V0LnN0YXRlRmxhZzwva2V5PgoJCQkJCTxpbnRlZ2VyPjE8L2ludGVnZXI+"
    "CgkJCQk8\n"
    "L2RpY3Q+CgkJCTwvYXJyYXk+CgkJPC9kaWN0PgoJCTxrZXk+"
    "Y29tLmFwcGxlLnByaW50LnRpY2t\n"
    "ldC5BUElWZXJzaW9uPC9rZXk+CgkJPHN0cmluZz4wMC4yMDwvc3RyaW5nPgoJCTxrZXk+"
    "Y29tLm\n"
    "FwcGxlLnByaW50LnRpY2tldC5wcml2YXRlTG9jazwva2V5PgoJCTxmYWxzZS8+"
    "CgkJPGtleT5jb\n"
    "20uYXBwbGUucHJpbnQudGlja2V0LnR5cGU8L2tleT4KCQk8c3RyaW5nPmNvbS5hcHBsZS5wcml"
    "u\n"
    "dC5QYXBlckluZm9UaWNrZXQ8L3N0cmluZz4KCTwvZGljdD4KCTxrZXk+"
    "Y29tLmFwcGxlLnByaW5\n"
    "0LnRpY2tldC5BUElWZXJzaW9uPC9rZXk+Cgk8c3RyaW5nPjAwLjIwPC9zdHJpbmc+"
    "Cgk8a2V5Pm\n"
    "NvbS5hcHBsZS5wcmludC50aWNrZXQucHJpdmF0ZUxvY2s8L2tleT4KCTxmYWxzZS8+"
    "Cgk8a2V5P\n"
    "mNvbS5hcHBsZS5wcmludC50aWNrZXQudHlwZTwva2V5PgoJPHN0cmluZz5jb20uYXBwbGUucHJ"
    "p\n"
    "bnQuUGFnZUZvcm1hdFRpY2tldDwvc3RyaW5nPgo8L2RpY3Q+CjwvcGxpc3Q+"
    "CjhCSU0D6QAAAAA\n"
    "AeAADAAAASABIAAAAAALeAkD/7v/uAwYCUgNnBSgD/"
    "AACAAAASABIAAAAAALYAigAAQAAAGQAAA\n"
    "ABAAMDAwAAAAF//"
    "wABAAEAAAAAAAAAAAAAAABoCAAZAZAAAAAAACAAAAAAAAAAAAAAAAAAAAAAA\n"
    "AAAAAAAAAAAADhCSU0D7QAAAAAAEABIAAAAAQABAEgAAAABAAE4QklNBCYAAAAAAA4AAAAAAAA"
    "A\n"
    "AAAAP4AAADhCSU0EDQAAAAAABAAAAB44QklNBBkAAAAAAAQAAAAeOEJJTQPzAAAAAAAJAAAAAA"
    "A\n"
    "AAAABADhCSU0ECgAAAAAAAQAAOEJJTScQAAAAAAAKAAEAAAAAAAAAAThCSU0D9QAAAAAASAAvZ"
    "m\n"
    "YAAQBsZmYABgAAAAAAAQAvZmYAAQChmZoABgAAAAAAAQAyAAAAAQBaAAAABgAAAAAAAQA1AAAA"
    "A\n"
    "QAtAAAABgAAAAAAAThCSU0D+AAAAAAAcAAA/////////////////////////////wPoAAAAAP/"
    "/\n"
    "//////////////////////////8D6AAAAAD/////////////////////////////A+gAAAAA//"
    "/\n"
    "//////////////////////////"
    "wPoAAA4QklNBAgAAAAAABAAAAABAAACQAAAAkAAAAAAOEJJTQ\n"
    "QeAAAAAAAEAAAAADhCSU0EGgAAAAADRQAAAAYAAAAAAAAAAAAAAGQAAABkAAAACABEAFMAQwAw"
    "A\n"
    "DIAMwAyADUAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAGQAAABkAAAAAAAAAAA"
    "A\n"
    "AAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAEAAAAAAABudWxsAAAAAgAAAAZib3VuZH"
    "N\n"
    "PYmpjAAAAAQAAAAAAAFJjdDEAAAAEAAAAAFRvcCBsb25nAAAAAAAAAABMZWZ0bG9uZwAAAAAAA"
    "A\n"
    "AAQnRvbWxvbmcAAABkAAAAAFJnaHRsb25nAAAAZAAAAAZzbGljZXNWbExzAAAAAU9iamMAAAAB"
    "A\n"
    "AAAAAAFc2xpY2UAAAASAAAAB3NsaWNlSURsb25nAAAAAAAAAAdncm91cElEbG9uZwAAAAAAAAA"
    "G\n"
    "b3JpZ2luZW51bQAAAAxFU2xpY2VPcmlnaW4AAAANYXV0b0dlbmVyYXRlZAAAAABUeXBlZW51bQ"
    "A\n"
    "AAApFU2xpY2VUeXBlAAAAAEltZyAAAAAGYm91bmRzT2JqYwAAAAEAAAAAAABSY3QxAAAABAAAA"
    "A\n"
    "BUb3AgbG9uZwAAAAAAAAAATGVmdGxvbmcAAAAAAAAAAEJ0b21sb25nAAAAZAAAAABSZ2h0bG9u"
    "Z\n"
    "wAAAGQAAAADdXJsVEVYVAAAAAEAAAAAAABudWxsVEVYVAAAAAEAAAAAAABNc2dlVEVYVAAAAAE"
    "A\n"
    "AAAAAAZhbHRUYWdURVhUAAAAAQAAAAAADmNlbGxUZXh0SXNIVE1MYm9vbAEAAAAIY2VsbFRleH"
    "R\n"
    "URVhUAAAAAQAAAAAACWhvcnpBbGlnbmVudW0AAAAPRVNsaWNlSG9yekFsaWduAAAAB2RlZmF1b"
    "H\n"
    "QAAAAJdmVydEFsaWduZW51bQAAAA9FU2xpY2VWZXJ0QWxpZ24AAAAHZGVmYXVsdAAAAAtiZ0Nv"
    "b\n"
    "G9yVHlwZWVudW0AAAARRVNsaWNlQkdDb2xvclR5cGUAAAAATm9uZQAAAAl0b3BPdXRzZXRsb25"
    "n\n"
    "AAAAAAAAAApsZWZ0T3V0c2V0bG9uZwAAAAAAAAAMYm90dG9tT3V0c2V0bG9uZwAAAAAAAAALcm"
    "l\n"
    "naHRPdXRzZXRsb25nAAAAAAA4QklNBBEAAAAAAAEBADhCSU0EFAAAAAAABAAAAAE4QklNBAwAA"
    "A\n"
    "AACfkAAAABAAAAZAAAAGQAAAEsAAB1MAAACd0AGAAB/9j/4AAQSkZJRgABAgEASABIAAD/"
    "7QAMQ\n"
    "WRvYmVfQ00AAv/uAA5BZG9iZQBkgAAAAAH/"
    "2wCEAAwICAgJCAwJCQwRCwoLERUPDAwPFRgTExUT\n"
    "ExgRDAwMDAwMEQwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwBDQsLDQ4NEA4OEBQODg4UFA"
    "4\n"
    "ODg4UEQwMDAwMEREMDAwMDAwRDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDP/"
    "AABEIAGQAZA\n"
    "MBIgACEQEDEQH/3QAEAAf/xAE/"
    "AAABBQEBAQEBAQAAAAAAAAADAAECBAUGBwgJCgsBAAEFAQEBA\n"
    "QEBAAAAAAAAAAEAAgMEBQYHCAkKCxAAAQQBAwIEAgUHBggFAwwzAQACEQMEIRIxBUFRYRMicYE"
    "y\n"
    "BhSRobFCIyQVUsFiMzRygtFDByWSU/"
    "Dh8WNzNRaisoMmRJNUZEXCo3Q2F9JV4mXys4TD03Xj80Y\n"
    "nlKSFtJXE1OT0pbXF1eX1VmZ2hpamtsbW5vY3R1dnd4eXp7fH1+"
    "f3EQACAgECBAQDBAUGBwcGBT\n"
    "UBAAIRAyExEgRBUWFxIhMFMoGRFKGxQiPBUtHwMyRi4XKCkkNTFWNzNPElBhaisoMHJjXC0kST"
    "V\n"
    "KMXZEVVNnRl4vKzhMPTdePzRpSkhbSVxNTk9KW1xdXl9VZmdoaWprbG1ub2JzdHV2d3h5ent8f"
    "/\n"
    "2gAMAwEAAhEDEQA/"
    "APLtso1NRc0vP0Rok8NYyPEfijOG2ljBoAJPxKFppZtbS4Rz38kV+OPRDge\n"
    "T89EPHBfvLjtb3P8A30K/j47cgsrYNxGpPYJpK8RtyXUlvPfsobV0GV0uippLiX3EaMb2/"
    "rKgMB\n"
    "1ghoiNST4BESCjjLmxqmKtvxiXQ0cd0q8E2bjIDWjk9z5I8QW8JaoHcdkUePZJtZD9p8YU/"
    "Rsc/\n"
    "wBNjS5zjDWjUk+SSKYaJLYq+qWeYGQ5lBPLJ3OA8wz2/wDSWni/"
    "U3H2AXW2l2oloa0f9LcjSLeU\n"
    "hJdb/wAyqd387Zt+DZ5SSpVh/9DzO6dw7gGPuVn6ft/"
    "kyPkqwlxjw1Rnh24QNWjUeR5TSuDc6bg\n"
    "fatpsJZQ3sNC4rWfkVYpbi4LAb3aANEkFLp7GHGYxuhAj4K/"
    "hYVNDjYGzZ++eSSoSbLZjGgwxul\n"
    "XNrPqO35FukdmzyXOQeqtqwqRg4o/SOAN9ng3/"
    "AMzW02txZ9I+ZHKr241UOcWDaz3uLtSSPEpWu\n"
    "rR5XPeylmyNr4BIPPCyH2Oc6T8kXNvddkPe/"
    "VzjJPxQAJMKeIoNScrPk2MbfddXUNXvcGtPx0Xb\n"
    "dJ6NXjOD2Dfdw6w9v5LFW+q/1WLA3Ly9LSJaz91p/"
    "wDRjl2lOLWwAMbEJErWjRgESYieVdZhsMF\n"
    "wMt08ldrx/vVivHaOdSgCoud9krmElpba93ASTlr/AP/R83ohr97voiJV/"
    "Fq9QvsI+mdPgs1thc\n"
    "BWO5C38CoOY1g78qOejLiGvknxLAyGtExp5K9uzGt9RrNw7DhRfQKKx6bZIGgPj4rPycLqWVtI"
    "s\n"
    "JGu5skDyTBRZtQNrb1fU8xrtpBaO4MLQxcx1sNuEjt5rMGJR9noY5hF7Wxa8aAnxVvDb6bgHH2"
    "z\n"
    "omk0e64ajUUXnev9Idi5rrWAux7SXNd4E/muS+rHSjm9VbPtZjj1CSJBI+g3+0uh69b+iDG/"
    "QcD\n"
    "u0nQCeFP6l0MZhWX/"
    "AJ1xM+QafY1TQlY1a+WABsdXp8Sp27aBH+vZaVbC0ADlVcASwtdOolp8Ct\n"
    "BjmtGv0uI8EmJmxkIjWkmPEKLSPiidxIgJKRbDPCSN5pJyH//S87uw/"
    "suZ6c72iC13kVs9PdDmk\n"
    "KllVziV3cuafc7yP0QjYFh26cqM6hsxAjIj6u6xzbDHh3R663AaceH+"
    "5BwdruVp2PqZUA0a9yo6\n"
    "DPQajscnXb8YQdzC8H909joiZttoxoBIa4gGOQ3uqh+z1RuD2Ds4j2n+39FNKaFMevS/"
    "p5LPpSA\n"
    "I8/b/ABW70THZXj11VjaIAIHgFl5VdD8AneDMaec6Lb6QAKmu7x+VSw2a3MdF/"
    "rF9YKeh4B9OHZ\n"
    "lpAprPH8p7v5DFwrPrV9YDa645toLjMaFo8mtcEvrhkWZHXbg7T0Q2to8o3f8AfkarEitlVTKn"
    "Y\n"
    "ra992QQ2wOfG57H2bg7H2fzbFKA130P6n9dHWemCx5H2mk7LgPH818f8IuhAka6ea8y/"
    "wAWrcod\n"
    "VyrceRhBsPae5J/Qj+sxq9KDpMuMuKBCEntnny/1CSaWxM6pIKf/0/"
    "MvtF3pCrefTBnbOi1elP3\n"
    "Et8Vi+Sv9LuNd4HwTSNGSEqkLerwtwn+SYKtOeS4A8Krh2j1D/"
    "K1Vu4B5gaDmVDJtAr7zYYGoRB\n"
    "QDWQ4c9h/"
    "csuyjI3fobnDyJR8fF6ltcTaXRwCkAuAsfMGr1TGFNdTmEwLWS2dIK6npLgK2T4Lle\n"
    "pUZxoc9+6K4eR5NO5bPT73NoqIfoILT/JcFJDZr8zGiNXnvrfiur6/"
    "Y8tht7WvaexgbXf8AUrFt\n"
    "8IExyvRusYDOsYTAIbfWdzHRJ8HN/"
    "tLj7OgdRZawmreHP2gt9wEfvtH0f7SkDXe7+o+AOn9DquL\n"
    "f0mV+leQPH6H+axafUvrB07ptJtyshtTZDTEudJ7bWS5V6MmyltVLn7ht2hwECQP+isb60/"
    "Vqvr\n"
    "tbLsa1lObVIJd9Gxv5rXx9F7fzHpIbf/jgfVnd/TLYj6XoOhJcP/zE+sOzd6dW7dt2eo3dH7/"
    "9R\n"
    "JJWj//"
    "U8uiGFx76BFZLQ2xvLeVGAWQrFDJbtKBSHd6blNura4H3BbDXB7InVcZXZdh2bmTt7hbO\n"
    "J1dj2gzCjlFnhPod3WLHB+"
    "n3o9ZsAkFVMfMrs7orLmgkHUdkyqZQQWWQbLGlrjMjUeSrfV3Ltsw\n"
    "30EBzcd5YCedvLETJya66nWOIAaCVnfU/"
    "KuZn21CDVa02PngQdHf9LapMfVhzkaAPUUW3M91YaR\n"
    "3YDJ+WiBmZGazPo9Kttdt2j63E6s/fft/d/NWjXkMra7KtO2qkE6cErHpvsyMmzPu0dY4Bg/"
    "dYP\n"
    "otTpyoaMUI2XUya8tzG/pi0NMtICo/"
    "bsut21gdcWclkj5OncxaDrw6kM+9QxQzaWRAGii4pDqzC\n"
    "MT02aX7WzPU9b7PrG3bvO6P6yStfZm+pHnPySS4590+3jf/"
    "V8yb+CsUbp8uyz0kDskbu2dmz9J8\n"
    "lSt9Ld+gn1O8cKikmxXydbH+3bhsmfwWj/lONYlcwkhL6L4bfpOxn/tD0/wBN/N944Wh9VJm/"
    "b/\n"
    "O+347df+/rl0k+O38GLJ83X/CfTOt7v2dV6P8AMbx6njHb/"
    "pKuN3pN2+IXnaSjybr8e31fUqd+0\n"
    "Sj487DHMryZJMXjq+sfpPX84SXk6SSX/"
    "9kAOEJJTQQhAAAAAABVAAAAAQEAAAAPAEEAZABvAGIA\n"
    "ZQAgAFAAaABvAHQAbwBzAGgAbwBwAAAAEwBBAGQAbwBiAGUAIABQAGgAbwB0AG8AcwBoAG8AcA"
    "A\n"
    "gADcALgAwAAAAAQA4QklNBAYAAAAAAAcABQAAAAEBAP/"
    "hFWdodHRwOi8vbnMuYWRvYmUuY29tL3\n"
    "hhcC8xLjAvADw/eHBhY2tldCBiZWdpbj0n77u/"
    "JyBpZD0nVzVNME1wQ2VoaUh6cmVTek5UY3prY\n"
    "zlkJz8+Cjw/YWRvYmUteGFwLWZpbHRlcnMgZXNjPSJDUiI/"
    "Pgo8eDp4YXBtZXRhIHhtbG5zOng9\n"
    "J2Fkb2JlOm5zOm1ldGEvJyB4OnhhcHRrPSdYTVAgdG9vbGtpdCAyLjguMi0zMywgZnJhbWV3b3"
    "J\n"
    "rIDEuNSc+"
    "CjxyZGY6UkRGIHhtbG5zOnJkZj0naHR0cDovL3d3dy53My5vcmcvMTk5OS8wMi8yMi\n"
    "1yZGYtc3ludGF4LW5zIycgeG1sbnM6aVg9J2h0dHA6Ly9ucy5hZG9iZS5jb20vaVgvMS4wLyc+"
    "C\n"
    "gogPHJkZjpEZXNjcmlwdGlvbiBhYm91dD0ndXVpZDoyMmQwMmIwYS1iMjQ5LTExZGItOGFmOC0"
    "5\n"
    "MWQ1NDAzZjkyZjknCiAgeG1sbnM6cGRmPSdodHRwOi8vbnMuYWRvYmUuY29tL3BkZi8xLjMvJz"
    "4\n"
    "KICA8IS0tIHBkZjpTdWJqZWN0IGlzIGFsaWFzZWQgLS0+"
    "CiA8L3JkZjpEZXNjcmlwdGlvbj4KCi\n"
    "A8cmRmOkRlc2NyaXB0aW9uIGFib3V0PSd1dWlkOjIyZDAyYjBhLWIyNDktMTFkYi04YWY4LTkx"
    "Z\n"
    "DU0MDNmOTJmOScKICB4bWxuczpwaG90b3Nob3A9J2h0dHA6Ly9ucy5hZG9iZS5jb20vcGhvdG9"
    "z\n"
    "aG9wLzEuMC8nPgogIDwhLS0gcGhvdG9zaG9wOkNhcHRpb24gaXMgYWxpYXNlZCAtLT4KIDwvcm"
    "R\n"
    "mOkRlc2NyaXB0aW9uPgoKIDxyZGY6RGVzY3JpcHRpb24gYWJvdXQ9J3V1aWQ6MjJkMDJiMGEtY"
    "j\n"
    "I0OS0xMWRiLThhZjgtOTFkNTQwM2Y5MmY5JwogIHhtbG5zOnhhcD0naHR0cDovL25zLmFkb2Jl"
    "L\n"
    "mNvbS94YXAvMS4wLyc+"
    "CiAgPCEtLSB4YXA6RGVzY3JpcHRpb24gaXMgYWxpYXNlZCAtLT4KIDwv\n"
    "cmRmOkRlc2NyaXB0aW9uPgoKIDxyZGY6RGVzY3JpcHRpb24gYWJvdXQ9J3V1aWQ6MjJkMDJiMG"
    "E\n"
    "tYjI0OS0xMWRiLThhZjgtOTFkNTQwM2Y5MmY5JwogIHhtbG5zOnhhcE1NPSdodHRwOi8vbnMuY"
    "W\n"
    "RvYmUuY29tL3hhcC8xLjAvbW0vJz4KICA8eGFwTU06RG9jdW1lbnRJRD5hZG9iZTpkb2NpZDpw"
    "a\n"
    "G90b3Nob3A6MjJkMDJiMDYtYjI0OS0xMWRiLThhZjgtOTFkNTQwM2Y5MmY5PC94YXBNTTpEb2N"
    "1\n"
    "bWVudElEPgogPC9yZGY6RGVzY3JpcHRpb24+"
    "CgogPHJkZjpEZXNjcmlwdGlvbiBhYm91dD0ndXV\n"
    "pZDoyMmQwMmIwYS1iMjQ5LTExZGItOGFmOC05MWQ1NDAzZjkyZjknCiAgeG1sbnM6ZGM9J2h0d"
    "H\n"
    "A6Ly9wdXJsLm9yZy9kYy9lbGVtZW50cy8xLjEvJz4KICA8ZGM6ZGVzY3JpcHRpb24+"
    "CiAgIDxyZ\n"
    "GY6QWx0PgogICAgPHJkZjpsaSB4bWw6bGFuZz0neC1kZWZhdWx0Jz4gICAgICAgICAgICAgICA"
    "g\n"
    "ICAgICAgICAgICAgICAgPC9yZGY6bGk+"
    "CiAgIDwvcmRmOkFsdD4KICA8L2RjOmRlc2NyaXB0aW9\n"
    "uPgogPC9yZGY6RGVzY3JpcHRpb24+Cgo8L3JkZjpSREY+"
    "CjwveDp4YXBtZXRhPgogICAgICAgIC\n"
    "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAg"
    "I\n"
    "CAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICA"
    "g\n"
    "ICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIC"
    "A\n"
    "gICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI"
    "C\n"
    "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAg"
    "I\n"
    "CAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICA"
    "g\n"
    "ICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIC"
    "A\n"
    "gCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI"
    "C\n"
    "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAg"
    "I\n"
    "CAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICA"
    "g\n"
    "ICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgIC"
    "A\n"
    "gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI"
    "C\n"
    "AgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAg"
    "I\n"
    "CAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICA"
    "g\n"
    "ICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIC"
    "A\n"
    "gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI"
    "A\n"
    "ogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAg"
    "I\n"
    "CAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICA"
    "g\n"
    "ICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIC"
    "A\n"
    "gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgI"
    "C\n"
    "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAg"
    "I\n"
    "CAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICA"
    "g\n"
    "ICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIC"
    "A\n"
    "gICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI"
    "C\n"
    "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAK"
    "I\n"
    "CAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICA"
    "g\n"
    "ICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgIC"
    "A\n"
    "gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI"
    "C\n"
    "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAg"
    "I\n"
    "CAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICA"
    "g\n"
    "ICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIC"
    "A\n"
    "gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI"
    "C\n"
    "AgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAg"
    "I\n"
    "CAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiA"
    "g\n"
    "ICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIC"
    "A\n"
    "gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgI"
    "C\n"
    "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAg"
    "I\n"
    "CAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICA"
    "g\n"
    "ICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIC"
    "A\n"
    "gICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI"
    "C\n"
    "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAg"
    "I\n"
    "CAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICA"
    "g\n"
    "ICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogIC"
    "A\n"
    "gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI"
    "C\n"
    "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAg"
    "I\n"
    "CAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICA"
    "g\n"
    "ICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgIC"
    "A\n"
    "gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI"
    "C\n"
    "AgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAg"
    "I\n"
    "CAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICA"
    "g\n"
    "ICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIC"
    "A\n"
    "gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgI"
    "C\n"
    "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAg"
    "I\n"
    "CAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICA"
    "g\n"
    "ICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIC"
    "A\n"
    "gICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgI"
    "C\n"
    "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAg"
    "I\n"
    "CAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICA"
    "g\n"
    "ICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIC"
    "A\n"
    "gICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI"
    "C\n"
    "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAg"
    "I\n"
    "CAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICA"
    "g\n"
    "ICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKICAgICAgICAgICAgICAgIC"
    "A\n"
    "gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI"
    "C\n"
    "AgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICAgICAgICAgICAgICAgICAgICAgICAg"
    "I\n"
    "CAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICA"
    "g\n"
    "ICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIC"
    "A\n"
    "gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI"
    "C\n"
    "AgICAKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAg"
    "I\n"
    "CAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIAogICAgICA"
    "g\n"
    "ICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIC"
    "A\n"
    "gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgCiAgICAgICAgICAgICAgICAgI"
    "C\n"
    "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAKPD94cGFja2V0IGVuZD0ndyc/"
    "P\n"
    "v/uAA5BZG9iZQBkQAAAAAH/"
    "2wCEAAQDAwMDAwQDAwQGBAMEBgcFBAQFBwgGBgcGBggKCAkJCQkI\n"
    "CgoMDAwMDAoMDAwMDAwMDAwMDAwMDAwMDAwMDAwBBAUFCAcIDwoKDxQODg4UFA4ODg4UEQwMDA"
    "w\n"
    "MEREMDAwMDAwRDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDP/"
    "AABEIAGQAZAMBEQACEQEDEQ\n"
    "H/3QAEAA3/"
    "xAGiAAAABwEBAQEBAAAAAAAAAAAEBQMCBgEABwgJCgsBAAICAwEBAQEBAAAAAAAAA\n"
    "AEAAgMEBQYHCAkKCxAAAgEDAwIEAgYHAwQCBgJzAQIDEQQABSESMUFRBhNhInGBFDKRoQcVsUI"
    "j\n"
    "wVLR4TMWYvAkcoLxJUM0U5KismNzwjVEJ5OjszYXVGR0w9LiCCaDCQoYGYSURUaktFbTVSga8u"
    "P\n"
    "zxNTk9GV1hZWltcXV5fVmdoaWprbG1ub2N0dXZ3eHl6e3x9fn9zhIWGh4iJiouMjY6PgpOUlZa"
    "X\n"
    "mJmam5ydnp+"
    "So6SlpqeoqaqrrK2ur6EQACAgECAwUFBAUGBAgDA20BAAIRAwQhEjFBBVETYSIGc\n"
    "YGRMqGx8BTB0eEjQhVSYnLxMyQ0Q4IWklMlomOywgdz0jXiRIMXVJMICQoYGSY2RRonZHRVN/"
    "Kj\n"
    "s8MoKdPj84SUpLTE1OT0ZXWFlaW1xdXl9UZWZnaGlqa2xtbm9kdXZ3eHl6e3x9fn9zhIWGh4iJ"
    "i\n"
    "ouMjY6Pg5SVlpeYmZqbnJ2en5KjpKWmp6ipqqusra6vr/2gAMAwEAAhEDEQA/"
    "APBnplwPAdR+GB\n"
    "KY6dYtNG1w39yh4+xb+zIksgEfFaRSSoIx8f7RPRRkSWQimM+lRmwWVXFWYigHxUUVoMiJM+"
    "Fj0\n"
    "tg0RBegLE0Wu+3c+GTBazFCGI7HtSp9slbFYYzyoBsegw2hY1Afl3wqqRqahk+"
    "0tDgKpgu4DAUU\n"
    "+HY+GRS2ePiMKtUB3G+KGuONq//"
    "Q8OzpFbW5WnxMop4k9crG5ZnZNJkEOn21utVRYw7HxZtz+OR\n"
    "vdsrZ2lRtci4aVxFEQA0neg/"
    "ZXxJpTITNNuOFss0vSotYNvZ2qGRkPKSTqiU8Sdqk5SZU5Ix8XJ\n"
    "NNZ8k6bp8TtM73OputUtYq0Unux/"
    "hkRkJOzZLCAN2KR+VpbtSkCBaDnIzdlWu59u+XeJTjeASk8\n"
    "+juZOESEAVqx8BvU/"
    "PJibScTrTy09560hkWOGFd2YgFnPQKD19zhOSkxw2l8Vm6XAiYb8gg+k5O\n"
    "9mnhoon9H3cs5s7WF5pp29OGGMFndyaAKBuTiEEPQLD8h/"
    "NDmNdYlttNkYjlbFjcXCr3LLH8II8\n"
    "C2WUGviZvon/OPWkm3RNSv72SYllMkKxQRV67CQMSKYQAxMkR/"
    "wBC56d61P0heel4cYuVOXWvTp\n"
    "h4Qjjf/9Hw5qBYyISaqjBV+QpvkAzKcki4HomnIxck/"
    "wBhtlR2bhunvlDywddMUl4zW+kQ9FQ8X\n"
    "nfuSewrtmPkycPvc/"
    "DhMhvyegXOrWWhmLQPKlsj6xIAiLCoZkY96nv7npmJvI2XOjQFMl0fyRqM\n"
    "NoxvZvrGt33wlATwiMnVnY1LEdSfuyXF3KIDmUu88w2XlnTl8raAlb2ZFfVL0jdYRtQnxc7BfD"
    "C\n"
    "OaJR7nm3me5tdOtjbMvp3ZRXkV6chVQRX79hmVjgZG+"
    "jgZ5jHGhzecXF5LPL6jEjstSSaDM51Ka\n"
    "6MZ9S1C0sEBe8uZo4YCBXdjxGw60wEWyEqfUHkT8vLXRJFuLdTcaqfhlvWUErtukZ3ABPUjIXT"
    "E\n"
    "m3rGmeV2Tk5UKz/AG/E/wAcgZKya20C3b02kjYtH8AqCygbkUH0nLYlgUb+gbWtPbpXt/"
    "n2ybB/\n"
    "/9Lw4oaVxGd+PxH3qBkGaY3KyiSP01IkiUclH8sg+LKydm6INvZvKsFu+"
    "kWtvD8LRoFNRup6moO\n"
    "aqd277HsGW+XPLmn6XM17FF6l7vW4fd2Zuu+"
    "RFls2tmUNrLJb7TSBertGQGqetDkxE0na0pvtHs\n"
    "QkszWyiGAG5laYlnkeMVHJj8sA5rPk+SvMepTalqlxd3B5zTOXdj/"
    "MxqafLpm5xioh5nPK5kpRG\n"
    "pkcKAST0A6k5NpfUP5K/ki1ssHmHzF+71KRQ8Nud/Qibb/kYw6/"
    "yjbrXISlSH07YaHbWyxx2kXE\n"
    "KACB2zHJtLI7XSelBRvH2xCpvaaTDHXkOTVBPcUG2479RlsdmJVPRtvV+ylenQ0y62FP/"
    "9PxRpo\n"
    "WG5FxKKxKFDA+GVS5NsebLdFsRePc3siVW4f4QR0QVAGYeSXR2unhtZ6s60K6jt+MMSFwtF2+"
    "xX\n"
    "wr7eGUGLlRPQMsE2vxQm7itxKg3VCfT2+"
    "nb8cDYaCDtfOXmCCcROrQrUhkkCHYn6emRMqZxjbLd\n"
    "F1+W/"
    "4xajHzjNCtQKMffETWUdngX5p+QZ9A8xS6hbo0ui37NNDPT7DOalHpsCD08Rmyw5ARTpdV\n"
    "gIPEF35MeRn80ed4S5EdrpKm9kZ15K0iH92hB7Me/tmS60vt/"
    "QrCYyekiBdgSTXcjqV9q9MokFD\n"
    "N7S3aFVVR8RoK9zldqndvAY6nffr/AGYQqLhjdpCoIAZW22HavU/LJBUP9WblX0xTw7fOmWsX/"
    "9\n"
    "Tw7FdvMqWkQ3Z1qfED+mQIbI77PX/"
    "LFis9vBajZm2Y+x65rMh3t30Bsze400aVaIbSLk6r8CMRT\n"
    "l/"
    "NmOcllnGDD9Y8uecNfEEiXrMgDGWAyGOOu5WlB+"
    "vMrHODTlxZCdjsyFdB006VpVtLasurQxBL\n"
    "64WiLI4/"
    "aFT1ANOXemV5piR2b9NiljB4yyHy9CLOVI5GJhB+CvXY9R8xmINzs5HNZ+Z96BZpbxA\n"
    "fVJo39UFefwopYgL4nMiMd2qZoIn/AJx00u3t/"
    "Lt7qpp9Yv5GLf5MUTERqfbvmzBeezjd9H+VlL\n"
    "wSQzBqsvOGQD7L12rXsemPNxmXQSxxIPU2nFV4HYqR1xEUWj4ZAxBryr2G+"
    "J2VGDZlLrxUH6KZA\n"
    "Fkqb15VFelfwy+2FP8A/"
    "9Xxlf6AdA182Yk9eFeLxSjoVfcfSMo4uIOfkweFOnpvlWYrLEwNFAA+\n"
    "nMOYdrhFvQLeSO7coBXiK8iKiv07Zj8Ac4QtNrW1njUcKcT+yAR/"
    "xGmR4WcsStLpTuPU9IFaEsV\n"
    "BP3k4m2AgBzSwyQNcIwNTE1aI3wnam9O2Ug7s5Ckk/"
    "NDndeVXa2H78MqqV6jmeBp9+ZWKXqDjZ4\n"
    "+gvVvy30qCy0qzsLRBCnBI2VdgUTqPvOZ7y+Q7pz+bn5q6d+VflZxZlJ/"
    "NN4ypptk5qtB9qRwDX\n"
    "gn/AAx2y2ItpfKFv+eH5qNeTajJ5ovVaVywSqvEtTUKqupAA6D2y0BNPtv/AJx//"
    "M5PzL8mJeXT\n"
    "L+ndPf6rqarSpkAqsnEAAeoN6DpkJRYci9lROSgSUUH9o9K5Tw0ztfSHnXkOtK9q+PHwydq//"
    "9b\n"
    "yxrVoZNBtNSA5zRMPXmH8j0CLXuBmHE+"
    "qneamHpEuqYeV7pzFVTRgQK5XMNmnlb1vyyY5QA1OwJ\n"
    "+eUF2seTOLu5s7azVIVAkpVn/"
    "hhnIALG73Yz5jvb1dICqzpDNIqyFD8SxH7R28cxibZCiWOsdJs\n"
    "PTM6XNstPhnkjIhcHuJBVfvOCiUSn0TfWrTTLjyw8guA/PifTO3xcxxA8a5ZAbimvJP0m3p/"
    "kFF\n"
    "WxhmpWQJ9NW3zZPHz5vlb/nIDVbrWfzO1RJhxGnpDaRL/"
    "khA1T7ktmSOTAJhZaAUtLawsbayl8v\n"
    "xWi3Gpay0cF3HPcFRJJHJMXVrcJ8UaAFG5LWjF8tAYW9H/wCcOo9bTzxrt/"
    "owkTyksZW5gkIKvI\n"
    "7k26nvyReRJHyyBWT7dWQyOWlbnK2526e1O1MqIUFE84uPLkOdK9RXI0E2/wD/1/DA1bURZLY/"
    "W\n"
    "ZDZqwb0eXw7dMgIi7bjllVXsz7yNcfWC0Vd3Ip92Y2UOz0cnsPlwyx8xQ/"
    "u24sMxCadoJp9LOXk\n"
    "VX/"
    "uwRUE0BI8cokbLMyoKouHu2MaKGXw7fLDwgoGSkbHpaNZyLLHRSKcFFQQRvUdMlwUFOQyLzr\n"
    "ztpCaba6fPau4ijv4OURY8AjVFKV7ZZiO+7Vnh6XvXkSWNbW2WTb92KDxIFMzwHlZc3zX+"
    "fuizW\n"
    "f5p3ty8XGDU4YLmCQiisyII3+4rvl8UB5ffEghRGvOm7AbnvWvjk1fen/"
    "ONPldPKP5aWOpPCfr2\n"
    "uE31y6q2wbaMEn+VAMDSdyzrzj+avlHyTp0l/r2rxWFuHWJuIeacu4qFCRgsajfBwsty89/"
    "6Gr/\n"
    "ACa9an+JL/hSnrfoubhXwpXpjwhaL//"
    "Q8E1AqtcAZMs8l6i1nqMa1oSVP0VynKLDmaWdSfQXl69\n"
    "jF1Jv8MhDb5rpB3AO7INRRLhhGp4R05FgaGvTMU8200xS70zVDMRp2pTIOvBmB3PgQP15kxIcn"
    "D\n"
    "LH/"
    "EEz0rRvOJhldr9pQtCqyd6VrShGTqw5d4ARv9jHfOGl+ZJNMluLkyenaFbiRdqFYW5nrWuwO\n"
    "MKB5MdSMRxnhlu9N8p6lLFpti63FUjCtFJTrDKvse2bEDZ4XJ9RZB+YPli2/"
    "Mjy5bxoUi1a0YS2\n"
    "85UOwIXiy9jRu+TBppfOF1+V3m22vrdpNPM8cs/oo0VJlUqQPjValR3+IZNNvtLS9Yu9Mi0/"
    "TJr\n"
    "kyp6QhWVVCIWRATsKBemwwFrDzT87fybs/"
    "wA1bW21PRb+DTvNlgGSRp6iC8i3KJJx+y6n7D0Pwm\n"
    "hxBZXT55/6Fi/Nf0PW+qWXq+t6X1X67F6vD/ftK04V/wBl344U8b//"
    "0fBapxheVh9ocV+nviqY2\n"
    "/qQJDew/"
    "bioWHiuQ8m0bbvaPKGtQ6jaxSo9JloCK75gZI0Xb4sgkHo8MouoAvP94BsRmGY7uWJU\n"
    "gzbypOQpNOvIdK4Nw2WCE2tXulTkjEEbdafgclxMhFBas93dwyQzsWDghlJFONKHJCZtjOFBJf"
    "y\n"
    "j1y9vPL9zpbIs0WkXL2sUjA8hDXlGCRXtt07ZuYvL5KJeo6bfajbkzWkcToR8dqshZ6in2fhNK"
    "/\n"
    "PDTUlXmHVvMdr5o0v9H2kdrqGpfu7m0nkY87Uf7tkKAU4/"
    "s03ynLkEBbfihx7dGT6va67LbRMNR\n"
    "aKOBuUTKgIBXoK1BOYR1M3aQ0mOt9yxUeZNdtJhFapLqMluSXkg5oxJrUMW5KevQ9MmNXXNqOi"
    "H\n"
    "Rr/Hmv8A1r9I/oj95w+r+j9Yf1+NP5+nXtTD+dF8tkfkOlv/0vC3ph7f0/"
    "alcVTbS4A8QibuKb5\n"
    "RI05EBYRFpdX3ly79a2qYCavH/"
    "EY7TCYyMD5PSdD8+wXUSn1ArDqOhBzFlipz4ZwWbaV5htbsgF\n"
    "qg9crMXKErGyYwajFGzxyHlGSePbbwyqg5UZlCaxrFpaWU95LIqrEjMAT4Dp9OShGy1ZslBhv/"
    "A\n"
    "Dj9rd/a+aL+xUK+m38L3d0HrxRo2HFtu5D8c27y8t30raarbWkU+u6g4gsNORn+EcUaSh2Pc0/"
    "4\n"
    "lgtAjezzbT9SutY1i782al8Nxdyotqh6xWybIg+jc5q8s+I27bFDgFPQp9RE+nrag70+"
    "L6crrZu\n"
    "4jajokdv6LW/Dii1Wo61PXKQN3KPK0L+h4/rnD/K5V78a5LhXxd3/0/"
    "DMXXtwxVNtL9Xkaf3f7N\n"
    "etfbKMjdjtkZ9D6ufrlK0+HpX8coF9HJ26sXvfqXrf7i/U+uften/d/"
    "wCyrmQL6uOav0pvpP8Ai\n"
    "b1F+rV59+vH6a5XLhcjH4nRmY/xpxHP0/UptWvT6Mx/RbmjxWK+aP8AFf1M/"
    "pCv1Kvxen9inavf\n"
    "MrFwXtzcLUeLXq5Mv/I3nz1b0v8AjofuKVry9KrUpTanOlf9jmQ68va/zH9b/COn/o7/"
    "AI431mP\n"
    "65SvLh+zWvbl9rMfNfC34K4kmj9T6lD6FKclp/DNYXZx5srsPrHor6nXvkgxTPS/"
    "U+rv6dPU5mt\n"
    "fngFN5ulv+l/pL/Lp/scerHo//2Q==\n";

static std::string gCommandLine;

TEST(Base64, LargeSample) {
  RTC_LOG(LS_VERBOSE) << "Testing specific base64 file";

  char unescaped[64 * 1024];

  // unescape that massive blob above
  size_t size = Base64Unescape(SpecificTest, sizeof(SpecificTest), unescaped,
                               sizeof(unescaped));

  EXPECT_EQ(size, sizeof(testbase64));
  EXPECT_EQ(0, memcmp(testbase64, unescaped, sizeof(testbase64)));
}

bool DecodeTest(const char* encoded,
                size_t expect_unparsed,
                const char* decoded,
                Base64::DecodeFlags flags) {
  std::string result;
  size_t consumed = 0, encoded_len = strlen(encoded);
  bool success =
      Base64::DecodeFromArray(encoded, encoded_len, flags, &result, &consumed);
  size_t unparsed = encoded_len - consumed;
  EXPECT_EQ(expect_unparsed, unparsed)
      << "\"" << encoded << "\" -> \"" << decoded << "\"";
  EXPECT_STREQ(decoded, result.c_str());
  return success;
}

#define Flags(x, y, z) \
  Base64::DO_PARSE_##x | Base64::DO_PAD_##y | Base64::DO_TERM_##z

TEST(Base64, DecodeParseOptions) {
  // Trailing whitespace
  EXPECT_TRUE(DecodeTest("YWJjZA== ", 1, "abcd", Flags(STRICT, YES, CHAR)));
  EXPECT_TRUE(DecodeTest("YWJjZA== ", 0, "abcd", Flags(WHITE, YES, CHAR)));
  EXPECT_TRUE(DecodeTest("YWJjZA== ", 0, "abcd", Flags(ANY, YES, CHAR)));

  // Embedded whitespace
  EXPECT_FALSE(DecodeTest("YWJjZA= =", 3, "abcd", Flags(STRICT, YES, CHAR)));
  EXPECT_TRUE(DecodeTest("YWJjZA= =", 0, "abcd", Flags(WHITE, YES, CHAR)));
  EXPECT_TRUE(DecodeTest("YWJjZA= =", 0, "abcd", Flags(ANY, YES, CHAR)));

  // Embedded non-base64 characters
  EXPECT_FALSE(DecodeTest("YWJjZA=*=", 3, "abcd", Flags(STRICT, YES, CHAR)));
  EXPECT_FALSE(DecodeTest("YWJjZA=*=", 3, "abcd", Flags(WHITE, YES, CHAR)));
  EXPECT_TRUE(DecodeTest("YWJjZA=*=", 0, "abcd", Flags(ANY, YES, CHAR)));

  // Unexpected padding characters
  EXPECT_FALSE(DecodeTest("YW=JjZA==", 7, "a", Flags(STRICT, YES, CHAR)));
  EXPECT_FALSE(DecodeTest("YW=JjZA==", 7, "a", Flags(WHITE, YES, CHAR)));
  EXPECT_TRUE(DecodeTest("YW=JjZA==", 0, "abcd", Flags(ANY, YES, CHAR)));
}

TEST(Base64, DecodePadOptions) {
  // Padding
  EXPECT_TRUE(DecodeTest("YWJjZA==", 0, "abcd", Flags(STRICT, YES, CHAR)));
  EXPECT_TRUE(DecodeTest("YWJjZA==", 0, "abcd", Flags(STRICT, ANY, CHAR)));
  EXPECT_TRUE(DecodeTest("YWJjZA==", 2, "abcd", Flags(STRICT, NO, CHAR)));

  // Incomplete padding
  EXPECT_FALSE(DecodeTest("YWJjZA=", 1, "abcd", Flags(STRICT, YES, CHAR)));
  EXPECT_TRUE(DecodeTest("YWJjZA=", 1, "abcd", Flags(STRICT, ANY, CHAR)));
  EXPECT_TRUE(DecodeTest("YWJjZA=", 1, "abcd", Flags(STRICT, NO, CHAR)));

  // No padding
  EXPECT_FALSE(DecodeTest("YWJjZA", 0, "abcd", Flags(STRICT, YES, CHAR)));
  EXPECT_TRUE(DecodeTest("YWJjZA", 0, "abcd", Flags(STRICT, ANY, CHAR)));
  EXPECT_TRUE(DecodeTest("YWJjZA", 0, "abcd", Flags(STRICT, NO, CHAR)));
}

TEST(Base64, DecodeTerminateOptions) {
  // Complete quantum
  EXPECT_TRUE(DecodeTest("YWJj", 0, "abc", Flags(STRICT, NO, BUFFER)));
  EXPECT_TRUE(DecodeTest("YWJj", 0, "abc", Flags(STRICT, NO, CHAR)));
  EXPECT_TRUE(DecodeTest("YWJj", 0, "abc", Flags(STRICT, NO, ANY)));

  // Complete quantum with trailing data
  EXPECT_FALSE(DecodeTest("YWJj*", 1, "abc", Flags(STRICT, NO, BUFFER)));
  EXPECT_TRUE(DecodeTest("YWJj*", 1, "abc", Flags(STRICT, NO, CHAR)));
  EXPECT_TRUE(DecodeTest("YWJj*", 1, "abc", Flags(STRICT, NO, ANY)));

  // Incomplete quantum
  EXPECT_FALSE(DecodeTest("YWJ", 0, "ab", Flags(STRICT, NO, BUFFER)));
  EXPECT_FALSE(DecodeTest("YWJ", 0, "ab", Flags(STRICT, NO, CHAR)));
  EXPECT_TRUE(DecodeTest("YWJ", 0, "ab", Flags(STRICT, NO, ANY)));
}

TEST(Base64, GetNextBase64Char) {
  // The table looks like this:
  // "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
  char next_char;
  EXPECT_TRUE(Base64::GetNextBase64Char('A', &next_char));
  EXPECT_EQ('B', next_char);
  EXPECT_TRUE(Base64::GetNextBase64Char('Z', &next_char));
  EXPECT_EQ('a', next_char);
  EXPECT_TRUE(Base64::GetNextBase64Char('/', &next_char));
  EXPECT_EQ('A', next_char);
  EXPECT_FALSE(Base64::GetNextBase64Char('&', &next_char));
  EXPECT_FALSE(Base64::GetNextBase64Char('Z', nullptr));
}

}  // namespace
}  // namespace rtc
