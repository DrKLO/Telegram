/*
 *  Copyright 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/external_hmac.h"

#include <stdlib.h>  // For malloc/free.
#include <string.h>

#include "rtc_base/logging.h"
#include "rtc_base/zero_memory.h"
#include "third_party/libsrtp/include/srtp.h"

// Begin test case 0 */
static const uint8_t kExternalHmacTestCase0Key[20] = {
    0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b,
    0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b};

static const uint8_t kExternalHmacTestCase0Data[8] = {
    0x48, 0x69, 0x20, 0x54, 0x68, 0x65, 0x72, 0x65  // "Hi There"
};

static const uint8_t kExternalHmacFakeTag[10] = {0xba, 0xdd, 0xba, 0xdd, 0xba,
                                                 0xdd, 0xba, 0xdd, 0xba, 0xdd};

static const srtp_auth_test_case_t kExternalHmacTestCase0 = {
    20,                                                // Octets in key
    const_cast<uint8_t*>(kExternalHmacTestCase0Key),   // Key
    8,                                                 // Octets in data
    const_cast<uint8_t*>(kExternalHmacTestCase0Data),  // Data
    10,                                                // Octets in tag
    const_cast<uint8_t*>(kExternalHmacFakeTag),        // Tag
    NULL                                               // Pointer to next
                                                       // testcase
};

static const char kExternalHmacDescription[] =
    "external hmac sha-1 authentication";

// srtp_auth_type_t external_hmac is the hmac metaobject

static const srtp_auth_type_t external_hmac = {
    external_hmac_alloc,
    external_hmac_dealloc,
    external_hmac_init,
    external_hmac_compute,
    external_hmac_update,
    external_hmac_start,
    const_cast<char*>(kExternalHmacDescription),
    const_cast<srtp_auth_test_case_t*>(&kExternalHmacTestCase0),
    EXTERNAL_HMAC_SHA1};

srtp_err_status_t external_hmac_alloc(srtp_auth_t** a,
                                      int key_len,
                                      int out_len) {
  uint8_t* pointer;

  // Check key length - note that we don't support keys larger
  // than 20 bytes yet
  if (key_len > 20)
    return srtp_err_status_bad_param;

  // Check output length - should be less than 20 bytes/
  if (out_len > 20)
    return srtp_err_status_bad_param;

  // Allocate memory for auth and hmac_ctx_t structures.
  pointer = new uint8_t[(sizeof(ExternalHmacContext) + sizeof(srtp_auth_t))];
  if (pointer == NULL)
    return srtp_err_status_alloc_fail;

  // Set pointers
  *a = reinterpret_cast<srtp_auth_t*>(pointer);
  // `external_hmac` is const and libsrtp expects `type` to be non-const.
  // const conversion is required. `external_hmac` is constant because we don't
  // want to increase global count in Chrome.
  (*a)->type = const_cast<srtp_auth_type_t*>(&external_hmac);
  (*a)->state = pointer + sizeof(srtp_auth_t);
  (*a)->out_len = out_len;
  (*a)->key_len = key_len;
  (*a)->prefix_len = 0;

  return srtp_err_status_ok;
}

srtp_err_status_t external_hmac_dealloc(srtp_auth_t* a) {
  rtc::ExplicitZeroMemory(a, sizeof(ExternalHmacContext) + sizeof(srtp_auth_t));

  // Free memory
  delete[] a;

  return srtp_err_status_ok;
}

srtp_err_status_t external_hmac_init(void* state,
                                     const uint8_t* key,
                                     int key_len) {
  if (key_len > HMAC_KEY_LENGTH)
    return srtp_err_status_bad_param;

  ExternalHmacContext* context = static_cast<ExternalHmacContext*>(state);
  memcpy(context->key, key, key_len);
  context->key_length = key_len;
  return srtp_err_status_ok;
}

srtp_err_status_t external_hmac_start(void* /*state*/) {
  return srtp_err_status_ok;
}

srtp_err_status_t external_hmac_update(void* /*state*/,
                                       const uint8_t* /*message*/,
                                       int /*msg_octets*/) {
  return srtp_err_status_ok;
}

srtp_err_status_t external_hmac_compute(void* /*state*/,
                                        const uint8_t* /*message*/,
                                        int /*msg_octets*/,
                                        int tag_len,
                                        uint8_t* result) {
  memcpy(result, kExternalHmacFakeTag, tag_len);
  return srtp_err_status_ok;
}

srtp_err_status_t external_crypto_init() {
  // `external_hmac` is const. const_cast is required as libsrtp expects
  // non-const.
  srtp_err_status_t status = srtp_replace_auth_type(
      const_cast<srtp_auth_type_t*>(&external_hmac), EXTERNAL_HMAC_SHA1);
  if (status) {
    RTC_LOG(LS_ERROR) << "Error in replacing default auth module, error: "
                      << status;
    return srtp_err_status_fail;
  }
  return srtp_err_status_ok;
}
