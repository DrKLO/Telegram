
//*********************************************************************
//* Base64 - a simple base64 encoder and decoder.
//*
//*     Copyright (c) 1999, Bob Withers - bwit@pobox.com
//*
//* This code may be freely used for any purpose, either personal
//* or commercial, provided the authors copyright notice remains
//* intact.
//*
//* Enhancements by Stanley Yamane:
//*     o reverse lookup table for the decode function
//*     o reserve string buffer space in advance
//*
//*********************************************************************

#include "rtc_base/third_party/base64/base64.h"

#include <string.h>

#include "rtc_base/checks.h"

using std::vector;

namespace rtc {

static const char kPad = '=';
static const unsigned char pd = 0xFD;  // Padding
static const unsigned char sp = 0xFE;  // Whitespace
static const unsigned char il = 0xFF;  // Illegal base64 character

const char Base64::Base64Table[] =
    // 0000000000111111111122222222223333333333444444444455555555556666
    // 0123456789012345678901234567890123456789012345678901234567890123
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

// Decode Table gives the index of any valid base64 character in the
// Base64 table
// 65 == A, 97 == a, 48 == 0, 43 == +, 47 == /

const unsigned char Base64::DecodeTable[] = {
    // 0  1  2  3  4  5  6  7  8  9
    il, il, il, il, il, il, il, il, il, sp,  //   0 -   9
    sp, sp, sp, sp, il, il, il, il, il, il,  //  10 -  19
    il, il, il, il, il, il, il, il, il, il,  //  20 -  29
    il, il, sp, il, il, il, il, il, il, il,  //  30 -  39
    il, il, il, 62, il, il, il, 63, 52, 53,  //  40 -  49
    54, 55, 56, 57, 58, 59, 60, 61, il, il,  //  50 -  59
    il, pd, il, il, il, 0,  1,  2,  3,  4,   //  60 -  69
    5,  6,  7,  8,  9,  10, 11, 12, 13, 14,  //  70 -  79
    15, 16, 17, 18, 19, 20, 21, 22, 23, 24,  //  80 -  89
    25, il, il, il, il, il, il, 26, 27, 28,  //  90 -  99
    29, 30, 31, 32, 33, 34, 35, 36, 37, 38,  // 100 - 109
    39, 40, 41, 42, 43, 44, 45, 46, 47, 48,  // 110 - 119
    49, 50, 51, il, il, il, il, il, il, il,  // 120 - 129
    il, il, il, il, il, il, il, il, il, il,  // 130 - 139
    il, il, il, il, il, il, il, il, il, il,  // 140 - 149
    il, il, il, il, il, il, il, il, il, il,  // 150 - 159
    il, il, il, il, il, il, il, il, il, il,  // 160 - 169
    il, il, il, il, il, il, il, il, il, il,  // 170 - 179
    il, il, il, il, il, il, il, il, il, il,  // 180 - 189
    il, il, il, il, il, il, il, il, il, il,  // 190 - 199
    il, il, il, il, il, il, il, il, il, il,  // 200 - 209
    il, il, il, il, il, il, il, il, il, il,  // 210 - 219
    il, il, il, il, il, il, il, il, il, il,  // 220 - 229
    il, il, il, il, il, il, il, il, il, il,  // 230 - 239
    il, il, il, il, il, il, il, il, il, il,  // 240 - 249
    il, il, il, il, il, il                   // 250 - 255
};

bool Base64::IsBase64Char(char ch) {
  return (('A' <= ch) && (ch <= 'Z')) || (('a' <= ch) && (ch <= 'z')) ||
         (('0' <= ch) && (ch <= '9')) || (ch == '+') || (ch == '/');
}

bool Base64::GetNextBase64Char(char ch, char* next_ch) {
  if (next_ch == nullptr) {
    return false;
  }
  const char* p = strchr(Base64Table, ch);
  if (!p)
    return false;
  ++p;
  *next_ch = (*p) ? *p : Base64Table[0];
  return true;
}

bool Base64::IsBase64Encoded(const std::string& str) {
  for (size_t i = 0; i < str.size(); ++i) {
    if (!IsBase64Char(str.at(i)))
      return false;
  }
  return true;
}

void Base64::EncodeFromArray(const void* data,
                             size_t len,
                             std::string* result) {
  RTC_DCHECK(result);
  result->clear();
  result->resize(((len + 2) / 3) * 4);
  const unsigned char* byte_data = static_cast<const unsigned char*>(data);

  unsigned char c;
  size_t i = 0;
  size_t dest_ix = 0;
  while (i < len) {
    c = (byte_data[i] >> 2) & 0x3f;
    (*result)[dest_ix++] = Base64Table[c];

    c = (byte_data[i] << 4) & 0x3f;
    if (++i < len) {
      c |= (byte_data[i] >> 4) & 0x0f;
    }
    (*result)[dest_ix++] = Base64Table[c];

    if (i < len) {
      c = (byte_data[i] << 2) & 0x3f;
      if (++i < len) {
        c |= (byte_data[i] >> 6) & 0x03;
      }
      (*result)[dest_ix++] = Base64Table[c];
    } else {
      (*result)[dest_ix++] = kPad;
    }

    if (i < len) {
      c = byte_data[i] & 0x3f;
      (*result)[dest_ix++] = Base64Table[c];
      ++i;
    } else {
      (*result)[dest_ix++] = kPad;
    }
  }
}

size_t Base64::GetNextQuantum(DecodeFlags parse_flags,
                              bool illegal_pads,
                              const char* data,
                              size_t len,
                              size_t* dpos,
                              unsigned char qbuf[4],
                              bool* padded) {
  size_t byte_len = 0, pad_len = 0, pad_start = 0;
  for (; (byte_len < 4) && (*dpos < len); ++*dpos) {
    qbuf[byte_len] = DecodeTable[static_cast<unsigned char>(data[*dpos])];
    if ((il == qbuf[byte_len]) || (illegal_pads && (pd == qbuf[byte_len]))) {
      if (parse_flags != DO_PARSE_ANY)
        break;
      // Ignore illegal characters
    } else if (sp == qbuf[byte_len]) {
      if (parse_flags == DO_PARSE_STRICT)
        break;
      // Ignore spaces
    } else if (pd == qbuf[byte_len]) {
      if (byte_len < 2) {
        if (parse_flags != DO_PARSE_ANY)
          break;
        // Ignore unexpected padding
      } else if (byte_len + pad_len >= 4) {
        if (parse_flags != DO_PARSE_ANY)
          break;
        // Ignore extra pads
      } else {
        if (1 == ++pad_len) {
          pad_start = *dpos;
        }
      }
    } else {
      if (pad_len > 0) {
        if (parse_flags != DO_PARSE_ANY)
          break;
        // Ignore pads which are followed by data
        pad_len = 0;
      }
      ++byte_len;
    }
  }
  for (size_t i = byte_len; i < 4; ++i) {
    qbuf[i] = 0;
  }
  if (4 == byte_len + pad_len) {
    *padded = true;
  } else {
    *padded = false;
    if (pad_len) {
      // Roll back illegal padding
      *dpos = pad_start;
    }
  }
  return byte_len;
}

bool Base64::DecodeFromArray(const char* data,
                             size_t len,
                             DecodeFlags flags,
                             std::string* result,
                             size_t* data_used) {
  return DecodeFromArrayTemplate<std::string>(data, len, flags, result,
                                              data_used);
}

bool Base64::DecodeFromArray(const char* data,
                             size_t len,
                             DecodeFlags flags,
                             vector<char>* result,
                             size_t* data_used) {
  return DecodeFromArrayTemplate<vector<char>>(data, len, flags, result,
                                               data_used);
}

bool Base64::DecodeFromArray(const char* data,
                             size_t len,
                             DecodeFlags flags,
                             vector<uint8_t>* result,
                             size_t* data_used) {
  return DecodeFromArrayTemplate<vector<uint8_t>>(data, len, flags, result,
                                                  data_used);
}

template <typename T>
bool Base64::DecodeFromArrayTemplate(const char* data,
                                     size_t len,
                                     DecodeFlags flags,
                                     T* result,
                                     size_t* data_used) {
  RTC_DCHECK(result);
  RTC_DCHECK_LE(flags, (DO_PARSE_MASK | DO_PAD_MASK | DO_TERM_MASK));

  const DecodeFlags parse_flags = flags & DO_PARSE_MASK;
  const DecodeFlags pad_flags = flags & DO_PAD_MASK;
  const DecodeFlags term_flags = flags & DO_TERM_MASK;
  RTC_DCHECK_NE(0, parse_flags);
  RTC_DCHECK_NE(0, pad_flags);
  RTC_DCHECK_NE(0, term_flags);

  result->clear();
  result->reserve(len);

  size_t dpos = 0;
  bool success = true, padded;
  unsigned char c, qbuf[4];
  while (dpos < len) {
    size_t qlen = GetNextQuantum(parse_flags, (DO_PAD_NO == pad_flags), data,
                                 len, &dpos, qbuf, &padded);
    c = (qbuf[0] << 2) | ((qbuf[1] >> 4) & 0x3);
    if (qlen >= 2) {
      result->push_back(c);
      c = ((qbuf[1] << 4) & 0xf0) | ((qbuf[2] >> 2) & 0xf);
      if (qlen >= 3) {
        result->push_back(c);
        c = ((qbuf[2] << 6) & 0xc0) | qbuf[3];
        if (qlen >= 4) {
          result->push_back(c);
          c = 0;
        }
      }
    }
    if (qlen < 4) {
      if ((DO_TERM_ANY != term_flags) && (0 != c)) {
        success = false;  // unused bits
      }
      if ((DO_PAD_YES == pad_flags) && !padded) {
        success = false;  // expected padding
      }
      break;
    }
  }
  if ((DO_TERM_BUFFER == term_flags) && (dpos != len)) {
    success = false;  // unused chars
  }
  if (data_used) {
    *data_used = dpos;
  }
  return success;
}

}  // namespace rtc
