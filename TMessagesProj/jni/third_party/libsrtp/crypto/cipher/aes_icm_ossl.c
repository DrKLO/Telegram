/*
 * aes_icm_ossl.c
 *
 * AES Integer Counter Mode
 *
 * John A. Foley
 * Cisco Systems, Inc.
 *
 * 2/24/2012:  This module was modified to use CiscoSSL for AES counter
 *             mode.  Eddy Lem contributed the code to allow this.
 *
 * 12/20/2012: Added support for AES-192 and AES-256.
 */

/*
 *
 * Copyright (c) 2013-2017, Cisco Systems, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 *   Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 *   Neither the name of the Cisco Systems, Inc. nor the names of its
 *   contributors may be used to endorse or promote products derived
 *   from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDERS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

#include "config.h"

#include <openssl/evp.h>
#include "aes_icm_ossl.h"
#include "crypto_types.h"
#include "err.h" /* for srtp_debug */
#include "alloc.h"
#include "cipher_types.h"

srtp_debug_module_t srtp_mod_aes_icm = {
    0,             /* debugging is off by default */
    "aes icm ossl" /* printable module name       */
};

/*
 * integer counter mode works as follows:
 *
 * 16 bits
 * <----->
 * +------+------+------+------+------+------+------+------+
 * |           nonce           |    packet index    |  ctr |---+
 * +------+------+------+------+------+------+------+------+   |
 *                                                             |
 * +------+------+------+------+------+------+------+------+   v
 * |                      salt                      |000000|->(+)
 * +------+------+------+------+------+------+------+------+   |
 *                                                             |
 *                                                        +---------+
 *							  | encrypt |
 *							  +---------+
 *							       |
 * +------+------+------+------+------+------+------+------+   |
 * |                    keystream block                    |<--+
 * +------+------+------+------+------+------+------+------+
 *
 * All fields are big-endian
 *
 * ctr is the block counter, which increments from zero for
 * each packet (16 bits wide)
 *
 * packet index is distinct for each packet (48 bits wide)
 *
 * nonce can be distinct across many uses of the same key, or
 * can be a fixed value per key, or can be per-packet randomness
 * (64 bits)
 *
 */

/*
 * This function allocates a new instance of this crypto engine.
 * The key_len parameter should be one of 30, 38, or 46 for
 * AES-128, AES-192, and AES-256 respectively.  Note, this key_len
 * value is inflated, as it also accounts for the 112 bit salt
 * value.  The tlen argument is for the AEAD tag length, which
 * isn't used in counter mode.
 */
static srtp_err_status_t srtp_aes_icm_openssl_alloc(srtp_cipher_t **c,
                                                    int key_len,
                                                    int tlen)
{
    srtp_aes_icm_ctx_t *icm;

    debug_print(srtp_mod_aes_icm, "allocating cipher with key length %d",
                key_len);

    /*
     * Verify the key_len is valid for one of: AES-128/192/256
     */
    if (key_len != SRTP_AES_ICM_128_KEY_LEN_WSALT &&
        key_len != SRTP_AES_ICM_192_KEY_LEN_WSALT &&
        key_len != SRTP_AES_ICM_256_KEY_LEN_WSALT) {
        return srtp_err_status_bad_param;
    }

    /* allocate memory a cipher of type aes_icm */
    *c = (srtp_cipher_t *)srtp_crypto_alloc(sizeof(srtp_cipher_t));
    if (*c == NULL) {
        return srtp_err_status_alloc_fail;
    }

    icm = (srtp_aes_icm_ctx_t *)srtp_crypto_alloc(sizeof(srtp_aes_icm_ctx_t));
    if (icm == NULL) {
        srtp_crypto_free(*c);
        *c = NULL;
        return srtp_err_status_alloc_fail;
    }

    icm->ctx = EVP_CIPHER_CTX_new();
    if (icm->ctx == NULL) {
        srtp_crypto_free(icm);
        srtp_crypto_free(*c);
        *c = NULL;
        return srtp_err_status_alloc_fail;
    }

    /* set pointers */
    (*c)->state = icm;

    /* setup cipher parameters */
    switch (key_len) {
    case SRTP_AES_ICM_128_KEY_LEN_WSALT:
        (*c)->algorithm = SRTP_AES_ICM_128;
        (*c)->type = &srtp_aes_icm_128;
        icm->key_size = SRTP_AES_128_KEY_LEN;
        break;
    case SRTP_AES_ICM_192_KEY_LEN_WSALT:
        (*c)->algorithm = SRTP_AES_ICM_192;
        (*c)->type = &srtp_aes_icm_192;
        icm->key_size = SRTP_AES_192_KEY_LEN;
        break;
    case SRTP_AES_ICM_256_KEY_LEN_WSALT:
        (*c)->algorithm = SRTP_AES_ICM_256;
        (*c)->type = &srtp_aes_icm_256;
        icm->key_size = SRTP_AES_256_KEY_LEN;
        break;
    }

    /* set key size        */
    (*c)->key_len = key_len;

    return srtp_err_status_ok;
}

/*
 * This function deallocates an instance of this engine
 */
static srtp_err_status_t srtp_aes_icm_openssl_dealloc(srtp_cipher_t *c)
{
    srtp_aes_icm_ctx_t *ctx;

    if (c == NULL) {
        return srtp_err_status_bad_param;
    }

    /*
     * Free the EVP context
     */
    ctx = (srtp_aes_icm_ctx_t *)c->state;
    if (ctx != NULL) {
        EVP_CIPHER_CTX_free(ctx->ctx);
        /* zeroize the key material */
        octet_string_set_to_zero(ctx, sizeof(srtp_aes_icm_ctx_t));
        srtp_crypto_free(ctx);
    }

    /* free memory */
    srtp_crypto_free(c);

    return srtp_err_status_ok;
}

/*
 * aes_icm_openssl_context_init(...) initializes the aes_icm_context
 * using the value in key[].
 *
 * the key is the secret key
 *
 * the salt is unpredictable (but not necessarily secret) data which
 * randomizes the starting point in the keystream
 */
static srtp_err_status_t srtp_aes_icm_openssl_context_init(void *cv,
                                                           const uint8_t *key)
{
    srtp_aes_icm_ctx_t *c = (srtp_aes_icm_ctx_t *)cv;
    const EVP_CIPHER *evp;

    /*
     * set counter and initial values to 'offset' value, being careful not to
     * go past the end of the key buffer
     */
    v128_set_to_zero(&c->counter);
    v128_set_to_zero(&c->offset);
    memcpy(&c->counter, key + c->key_size, SRTP_SALT_LEN);
    memcpy(&c->offset, key + c->key_size, SRTP_SALT_LEN);

    /* force last two octets of the offset to zero (for srtp compatibility) */
    c->offset.v8[SRTP_SALT_LEN] = c->offset.v8[SRTP_SALT_LEN + 1] = 0;
    c->counter.v8[SRTP_SALT_LEN] = c->counter.v8[SRTP_SALT_LEN + 1] = 0;

    debug_print(srtp_mod_aes_icm, "key:  %s",
                srtp_octet_string_hex_string(key, c->key_size));
    debug_print(srtp_mod_aes_icm, "offset: %s", v128_hex_string(&c->offset));

    switch (c->key_size) {
    case SRTP_AES_256_KEY_LEN:
        evp = EVP_aes_256_ctr();
        break;
    case SRTP_AES_192_KEY_LEN:
        evp = EVP_aes_192_ctr();
        break;
    case SRTP_AES_128_KEY_LEN:
        evp = EVP_aes_128_ctr();
        break;
    default:
        return srtp_err_status_bad_param;
        break;
    }

    if (!EVP_EncryptInit_ex(c->ctx, evp, NULL, key, NULL)) {
        return srtp_err_status_fail;
    } else {
        return srtp_err_status_ok;
    }

    return srtp_err_status_ok;
}

/*
 * aes_icm_set_iv(c, iv) sets the counter value to the exor of iv with
 * the offset
 */
static srtp_err_status_t srtp_aes_icm_openssl_set_iv(
    void *cv,
    uint8_t *iv,
    srtp_cipher_direction_t dir)
{
    srtp_aes_icm_ctx_t *c = (srtp_aes_icm_ctx_t *)cv;
    v128_t nonce;

    /* set nonce (for alignment) */
    v128_copy_octet_string(&nonce, iv);

    debug_print(srtp_mod_aes_icm, "setting iv: %s", v128_hex_string(&nonce));

    v128_xor(&c->counter, &c->offset, &nonce);

    debug_print(srtp_mod_aes_icm, "set_counter: %s",
                v128_hex_string(&c->counter));

    if (!EVP_EncryptInit_ex(c->ctx, NULL, NULL, NULL, c->counter.v8)) {
        return srtp_err_status_fail;
    } else {
        return srtp_err_status_ok;
    }
}

/*
 * This function encrypts a buffer using AES CTR mode
 *
 * Parameters:
 *	c	Crypto context
 *	buf	data to encrypt
 *	enc_len	length of encrypt buffer
 */
static srtp_err_status_t srtp_aes_icm_openssl_encrypt(void *cv,
                                                      unsigned char *buf,
                                                      unsigned int *enc_len)
{
    srtp_aes_icm_ctx_t *c = (srtp_aes_icm_ctx_t *)cv;
    int len = 0;

    debug_print(srtp_mod_aes_icm, "rs0: %s", v128_hex_string(&c->counter));

    if (!EVP_EncryptUpdate(c->ctx, buf, &len, buf, *enc_len)) {
        return srtp_err_status_cipher_fail;
    }
    *enc_len = len;

    if (!EVP_EncryptFinal_ex(c->ctx, buf, &len)) {
        return srtp_err_status_cipher_fail;
    }
    *enc_len += len;

    return srtp_err_status_ok;
}

/*
 * Name of this crypto engine
 */
static const char srtp_aes_icm_128_openssl_description[] =
    "AES-128 counter mode using openssl";
static const char srtp_aes_icm_192_openssl_description[] =
    "AES-192 counter mode using openssl";
static const char srtp_aes_icm_256_openssl_description[] =
    "AES-256 counter mode using openssl";

/*
 * KAT values for AES self-test.  These
 * values came from the legacy libsrtp code.
 */
/* clang-format off */
static const uint8_t srtp_aes_icm_128_test_case_0_key[SRTP_AES_ICM_128_KEY_LEN_WSALT] = {
    0x2b, 0x7e, 0x15, 0x16, 0x28, 0xae, 0xd2, 0xa6,
    0xab, 0xf7, 0x15, 0x88, 0x09, 0xcf, 0x4f, 0x3c,
    0xf0, 0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7,
    0xf8, 0xf9, 0xfa, 0xfb, 0xfc, 0xfd
};
/* clang-format on */

/* clang-format off */
static uint8_t srtp_aes_icm_128_test_case_0_nonce[16] = {
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
};
/* clang-format on */

/* clang-format off */
static const uint8_t srtp_aes_icm_128_test_case_0_plaintext[32] =  {
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
};
/* clang-format on */

/* clang-format off */
static const uint8_t srtp_aes_icm_128_test_case_0_ciphertext[32] = {
    0xe0, 0x3e, 0xad, 0x09, 0x35, 0xc9, 0x5e, 0x80,
    0xe1, 0x66, 0xb1, 0x6d, 0xd9, 0x2b, 0x4e, 0xb4,
    0xd2, 0x35, 0x13, 0x16, 0x2b, 0x02, 0xd0, 0xf7,
    0x2a, 0x43, 0xa2, 0xfe, 0x4a, 0x5f, 0x97, 0xab
};
/* clang-format on */

static const srtp_cipher_test_case_t srtp_aes_icm_128_test_case_0 = {
    SRTP_AES_ICM_128_KEY_LEN_WSALT,          /* octets in key            */
    srtp_aes_icm_128_test_case_0_key,        /* key                      */
    srtp_aes_icm_128_test_case_0_nonce,      /* packet index             */
    32,                                      /* octets in plaintext      */
    srtp_aes_icm_128_test_case_0_plaintext,  /* plaintext                */
    32,                                      /* octets in ciphertext     */
    srtp_aes_icm_128_test_case_0_ciphertext, /* ciphertext               */
    0,                                       /* */
    NULL,                                    /* */
    0,                                       /* */
    NULL                                     /* pointer to next testcase */
};

/*
 * KAT values for AES-192-CTR self-test.  These
 * values came from section 7 of RFC 6188.
 */
/* clang-format off */
static const uint8_t srtp_aes_icm_192_test_case_0_key[SRTP_AES_ICM_192_KEY_LEN_WSALT] = {
    0xea, 0xb2, 0x34, 0x76, 0x4e, 0x51, 0x7b, 0x2d,
    0x3d, 0x16, 0x0d, 0x58, 0x7d, 0x8c, 0x86, 0x21,
    0x97, 0x40, 0xf6, 0x5f, 0x99, 0xb6, 0xbc, 0xf7,
    0xf0, 0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7,
    0xf8, 0xf9, 0xfa, 0xfb, 0xfc, 0xfd
};
/* clang-format on */

/* clang-format off */
static uint8_t srtp_aes_icm_192_test_case_0_nonce[16] = {
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
};
/* clang-format on */

/* clang-format off */
static const uint8_t srtp_aes_icm_192_test_case_0_plaintext[32] =  {
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
};
/* clang-format on */

/* clang-format off */
static const uint8_t srtp_aes_icm_192_test_case_0_ciphertext[32] = {
    0x35, 0x09, 0x6c, 0xba, 0x46, 0x10, 0x02, 0x8d,
    0xc1, 0xb5, 0x75, 0x03, 0x80, 0x4c, 0xe3, 0x7c,
    0x5d, 0xe9, 0x86, 0x29, 0x1d, 0xcc, 0xe1, 0x61,
    0xd5, 0x16, 0x5e, 0xc4, 0x56, 0x8f, 0x5c, 0x9a
};
/* clang-format on */

static const srtp_cipher_test_case_t srtp_aes_icm_192_test_case_0 = {
    SRTP_AES_ICM_192_KEY_LEN_WSALT,          /* octets in key            */
    srtp_aes_icm_192_test_case_0_key,        /* key                      */
    srtp_aes_icm_192_test_case_0_nonce,      /* packet index             */
    32,                                      /* octets in plaintext      */
    srtp_aes_icm_192_test_case_0_plaintext,  /* plaintext                */
    32,                                      /* octets in ciphertext     */
    srtp_aes_icm_192_test_case_0_ciphertext, /* ciphertext               */
    0,                                       /* */
    NULL,                                    /* */
    0,                                       /* */
    NULL                                     /* pointer to next testcase */
};

/*
 * KAT values for AES-256-CTR self-test.  These
 * values came from section 7 of RFC 6188.
 */
/* clang-format off */
static const uint8_t srtp_aes_icm_256_test_case_0_key[SRTP_AES_ICM_256_KEY_LEN_WSALT] = {
    0x57, 0xf8, 0x2f, 0xe3, 0x61, 0x3f, 0xd1, 0x70,
    0xa8, 0x5e, 0xc9, 0x3c, 0x40, 0xb1, 0xf0, 0x92,
    0x2e, 0xc4, 0xcb, 0x0d, 0xc0, 0x25, 0xb5, 0x82,
    0x72, 0x14, 0x7c, 0xc4, 0x38, 0x94, 0x4a, 0x98,
    0xf0, 0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7,
    0xf8, 0xf9, 0xfa, 0xfb, 0xfc, 0xfd
};
/* clang-format on */

/* clang-format off */
static uint8_t srtp_aes_icm_256_test_case_0_nonce[16] = {
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
};
/* clang-format on */

/* clang-format off */
static const uint8_t srtp_aes_icm_256_test_case_0_plaintext[32] =  {
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
};
/* clang-format on */

/* clang-format off */
static const uint8_t srtp_aes_icm_256_test_case_0_ciphertext[32] = {
    0x92, 0xbd, 0xd2, 0x8a, 0x93, 0xc3, 0xf5, 0x25,
    0x11, 0xc6, 0x77, 0xd0, 0x8b, 0x55, 0x15, 0xa4,
    0x9d, 0xa7, 0x1b, 0x23, 0x78, 0xa8, 0x54, 0xf6,
    0x70, 0x50, 0x75, 0x6d, 0xed, 0x16, 0x5b, 0xac
};
/* clang-format on */

static const srtp_cipher_test_case_t srtp_aes_icm_256_test_case_0 = {
    SRTP_AES_ICM_256_KEY_LEN_WSALT,          /* octets in key            */
    srtp_aes_icm_256_test_case_0_key,        /* key                      */
    srtp_aes_icm_256_test_case_0_nonce,      /* packet index             */
    32,                                      /* octets in plaintext      */
    srtp_aes_icm_256_test_case_0_plaintext,  /* plaintext                */
    32,                                      /* octets in ciphertext     */
    srtp_aes_icm_256_test_case_0_ciphertext, /* ciphertext               */
    0,                                       /* */
    NULL,                                    /* */
    0,                                       /* */
    NULL                                     /* pointer to next testcase */
};

/*
 * This is the function table for this crypto engine.
 * note: the encrypt function is identical to the decrypt function
 */
const srtp_cipher_type_t srtp_aes_icm_128 = {
    srtp_aes_icm_openssl_alloc,           /* */
    srtp_aes_icm_openssl_dealloc,         /* */
    srtp_aes_icm_openssl_context_init,    /* */
    0,                                    /* set_aad */
    srtp_aes_icm_openssl_encrypt,         /* */
    srtp_aes_icm_openssl_encrypt,         /* */
    srtp_aes_icm_openssl_set_iv,          /* */
    0,                                    /* get_tag */
    srtp_aes_icm_128_openssl_description, /* */
    &srtp_aes_icm_128_test_case_0,        /* */
    SRTP_AES_ICM_128                      /* */
};

/*
 * This is the function table for this crypto engine.
 * note: the encrypt function is identical to the decrypt function
 */
const srtp_cipher_type_t srtp_aes_icm_192 = {
    srtp_aes_icm_openssl_alloc,           /* */
    srtp_aes_icm_openssl_dealloc,         /* */
    srtp_aes_icm_openssl_context_init,    /* */
    0,                                    /* set_aad */
    srtp_aes_icm_openssl_encrypt,         /* */
    srtp_aes_icm_openssl_encrypt,         /* */
    srtp_aes_icm_openssl_set_iv,          /* */
    0,                                    /* get_tag */
    srtp_aes_icm_192_openssl_description, /* */
    &srtp_aes_icm_192_test_case_0,        /* */
    SRTP_AES_ICM_192                      /* */
};

/*
 * This is the function table for this crypto engine.
 * note: the encrypt function is identical to the decrypt function
 */
const srtp_cipher_type_t srtp_aes_icm_256 = {
    srtp_aes_icm_openssl_alloc,           /* */
    srtp_aes_icm_openssl_dealloc,         /* */
    srtp_aes_icm_openssl_context_init,    /* */
    0,                                    /* set_aad */
    srtp_aes_icm_openssl_encrypt,         /* */
    srtp_aes_icm_openssl_encrypt,         /* */
    srtp_aes_icm_openssl_set_iv,          /* */
    0,                                    /* get_tag */
    srtp_aes_icm_256_openssl_description, /* */
    &srtp_aes_icm_256_test_case_0,        /* */
    SRTP_AES_ICM_256                      /* */
};
