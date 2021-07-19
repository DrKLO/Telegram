/* Copyright (c) 2016, Google Inc.
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
 * OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE. */

#include <limits.h>
#include <stdio.h>

#include <vector>

#include <gtest/gtest.h>

#include <openssl/asn1.h>
#include <openssl/asn1t.h>
#include <openssl/bytestring.h>
#include <openssl/err.h>
#include <openssl/mem.h>
#include <openssl/obj.h>
#include <openssl/span.h>

#include "../test/test_util.h"


// kTag128 is an ASN.1 structure with a universal tag with number 128.
static const uint8_t kTag128[] = {
    0x1f, 0x81, 0x00, 0x01, 0x00,
};

// kTag258 is an ASN.1 structure with a universal tag with number 258.
static const uint8_t kTag258[] = {
    0x1f, 0x82, 0x02, 0x01, 0x00,
};

static_assert(V_ASN1_NEG_INTEGER == 258,
              "V_ASN1_NEG_INTEGER changed. Update kTag258 to collide with it.");

// kTagOverflow is an ASN.1 structure with a universal tag with number 2^35-1,
// which will not fit in an int.
static const uint8_t kTagOverflow[] = {
    0x1f, 0xff, 0xff, 0xff, 0xff, 0x7f, 0x01, 0x00,
};

TEST(ASN1Test, LargeTags) {
  const uint8_t *p = kTag258;
  bssl::UniquePtr<ASN1_TYPE> obj(d2i_ASN1_TYPE(NULL, &p, sizeof(kTag258)));
  EXPECT_FALSE(obj) << "Parsed value with illegal tag" << obj->type;
  ERR_clear_error();

  p = kTagOverflow;
  obj.reset(d2i_ASN1_TYPE(NULL, &p, sizeof(kTagOverflow)));
  EXPECT_FALSE(obj) << "Parsed value with tag overflow" << obj->type;
  ERR_clear_error();

  p = kTag128;
  obj.reset(d2i_ASN1_TYPE(NULL, &p, sizeof(kTag128)));
  ASSERT_TRUE(obj);
  EXPECT_EQ(128, obj->type);
  const uint8_t kZero = 0;
  EXPECT_EQ(Bytes(&kZero, 1), Bytes(obj->value.asn1_string->data,
                                    obj->value.asn1_string->length));
}

TEST(ASN1Test, IntegerSetting) {
  bssl::UniquePtr<ASN1_INTEGER> by_bn(M_ASN1_INTEGER_new());
  bssl::UniquePtr<ASN1_INTEGER> by_long(M_ASN1_INTEGER_new());
  bssl::UniquePtr<ASN1_INTEGER> by_uint64(M_ASN1_INTEGER_new());
  bssl::UniquePtr<BIGNUM> bn(BN_new());

  const std::vector<int64_t> kValues = {
      LONG_MIN, -2, -1, 0, 1, 2, 0xff, 0x100, 0xffff, 0x10000, LONG_MAX,
  };
  for (const auto &i : kValues) {
    SCOPED_TRACE(i);

    ASSERT_EQ(1, ASN1_INTEGER_set(by_long.get(), i));
    const uint64_t abs = i < 0 ? (0 - (uint64_t) i) : i;
    ASSERT_TRUE(BN_set_u64(bn.get(), abs));
    BN_set_negative(bn.get(), i < 0);
    ASSERT_TRUE(BN_to_ASN1_INTEGER(bn.get(), by_bn.get()));

    EXPECT_EQ(0, ASN1_INTEGER_cmp(by_bn.get(), by_long.get()));

    if (i >= 0) {
      ASSERT_EQ(1, ASN1_INTEGER_set_uint64(by_uint64.get(), i));
      EXPECT_EQ(0, ASN1_INTEGER_cmp(by_bn.get(), by_uint64.get()));
    }
  }
}

typedef struct asn1_linked_list_st {
  struct asn1_linked_list_st *next;
} ASN1_LINKED_LIST;

DECLARE_ASN1_ITEM(ASN1_LINKED_LIST)
DECLARE_ASN1_FUNCTIONS(ASN1_LINKED_LIST)

ASN1_SEQUENCE(ASN1_LINKED_LIST) = {
  ASN1_OPT(ASN1_LINKED_LIST, next, ASN1_LINKED_LIST),
} ASN1_SEQUENCE_END(ASN1_LINKED_LIST)

IMPLEMENT_ASN1_FUNCTIONS(ASN1_LINKED_LIST)

static bool MakeLinkedList(bssl::UniquePtr<uint8_t> *out, size_t *out_len,
                           size_t count) {
  bssl::ScopedCBB cbb;
  std::vector<CBB> cbbs(count);
  if (!CBB_init(cbb.get(), 2 * count) ||
      !CBB_add_asn1(cbb.get(), &cbbs[0], CBS_ASN1_SEQUENCE)) {
    return false;
  }
  for (size_t i = 1; i < count; i++) {
    if (!CBB_add_asn1(&cbbs[i - 1], &cbbs[i], CBS_ASN1_SEQUENCE)) {
      return false;
    }
  }
  uint8_t *ptr;
  if (!CBB_finish(cbb.get(), &ptr, out_len)) {
    return false;
  }
  out->reset(ptr);
  return true;
}

TEST(ASN1Test, Recursive) {
  bssl::UniquePtr<uint8_t> data;
  size_t len;

  // Sanity-check that MakeLinkedList can be parsed.
  ASSERT_TRUE(MakeLinkedList(&data, &len, 5));
  const uint8_t *ptr = data.get();
  ASN1_LINKED_LIST *list = d2i_ASN1_LINKED_LIST(nullptr, &ptr, len);
  EXPECT_TRUE(list);
  ASN1_LINKED_LIST_free(list);

  // Excessively deep structures are rejected.
  ASSERT_TRUE(MakeLinkedList(&data, &len, 100));
  ptr = data.get();
  list = d2i_ASN1_LINKED_LIST(nullptr, &ptr, len);
  EXPECT_FALSE(list);
  // Note checking the error queue here does not work. The error "stack trace"
  // is too deep, so the |ASN1_R_NESTED_TOO_DEEP| entry drops off the queue.
  ASN1_LINKED_LIST_free(list);
}

template <typename T>
void TestSerialize(T obj, int (*i2d_func)(T a, uint8_t **pp),
                   bssl::Span<const uint8_t> expected) {
  int len = static_cast<int>(expected.size());
  ASSERT_EQ(i2d_func(obj, nullptr), len);

  std::vector<uint8_t> buf(expected.size());
  uint8_t *ptr = buf.data();
  ASSERT_EQ(i2d_func(obj, &ptr), len);
  EXPECT_EQ(ptr, buf.data() + buf.size());
  EXPECT_EQ(Bytes(expected), Bytes(buf));

  // Test the allocating version.
  ptr = nullptr;
  ASSERT_EQ(i2d_func(obj, &ptr), len);
  EXPECT_EQ(Bytes(expected), Bytes(ptr, expected.size()));
  OPENSSL_free(ptr);
}

TEST(ASN1Test, SerializeObject) {
  static const uint8_t kDER[] = {0x06, 0x09, 0x2a, 0x86, 0x48, 0x86,
                                 0xf7, 0x0d, 0x01, 0x01, 0x01};
  const ASN1_OBJECT *obj = OBJ_nid2obj(NID_rsaEncryption);
  TestSerialize(const_cast<ASN1_OBJECT *>(obj), i2d_ASN1_OBJECT, kDER);
}

TEST(ASN1Test, SerializeBoolean) {
  static const uint8_t kTrue[] = {0x01, 0x01, 0xff};
  TestSerialize(0xff, i2d_ASN1_BOOLEAN, kTrue);

  static const uint8_t kFalse[] = {0x01, 0x01, 0x00};
  TestSerialize(0x00, i2d_ASN1_BOOLEAN, kFalse);
}
