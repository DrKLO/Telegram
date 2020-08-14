/*
 * aes_gcm_ossl.c
 *
 * AES Galois Counter Mode
 *
 * John A. Foley
 * Cisco Systems, Inc.
 *
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
#include "aes_gcm_ossl.h"
#include "alloc.h"
#include "err.h" /* for srtp_debug */
#include "crypto_types.h"
#include "cipher_types.h"

srtp_debug_module_t srtp_mod_aes_gcm = {
    0,        /* debugging is off by default */
    "aes gcm" /* printable module name       */
};

/*
 * For now we only support 8 and 16 octet tags.  The spec allows for
 * optional 12 byte tag, which may be supported in the future.
 */
#define GCM_AUTH_TAG_LEN 16
#define GCM_AUTH_TAG_LEN_8 8

/*
 * This function allocates a new instance of this crypto engine.
 * The key_len parameter should be one of 28 or 44 for
 * AES-128-GCM or AES-256-GCM respectively.  Note that the
 * key length includes the 14 byte salt value that is used when
 * initializing the KDF.
 */
static srtp_err_status_t srtp_aes_gcm_openssl_alloc(srtp_cipher_t **c,
                                                    int key_len,
                                                    int tlen)
{
    srtp_aes_gcm_ctx_t *gcm;

    debug_print(srtp_mod_aes_gcm, "allocating cipher with key length %d",
                key_len);
    debug_print(srtp_mod_aes_gcm, "allocating cipher with tag length %d", tlen);

    /*
     * Verify the key_len is valid for one of: AES-128/256
     */
    if (key_len != SRTP_AES_GCM_128_KEY_LEN_WSALT &&
        key_len != SRTP_AES_GCM_256_KEY_LEN_WSALT) {
        return (srtp_err_status_bad_param);
    }

    if (tlen != GCM_AUTH_TAG_LEN && tlen != GCM_AUTH_TAG_LEN_8) {
        return (srtp_err_status_bad_param);
    }

    /* allocate memory a cipher of type aes_gcm */
    *c = (srtp_cipher_t *)srtp_crypto_alloc(sizeof(srtp_cipher_t));
    if (*c == NULL) {
        return (srtp_err_status_alloc_fail);
    }

    gcm = (srtp_aes_gcm_ctx_t *)srtp_crypto_alloc(sizeof(srtp_aes_gcm_ctx_t));
    if (gcm == NULL) {
        srtp_crypto_free(*c);
        *c = NULL;
        return (srtp_err_status_alloc_fail);
    }

    gcm->ctx = EVP_CIPHER_CTX_new();
    if (gcm->ctx == NULL) {
        srtp_crypto_free(gcm);
        srtp_crypto_free(*c);
        *c = NULL;
        return srtp_err_status_alloc_fail;
    }

    /* set pointers */
    (*c)->state = gcm;

    /* setup cipher attributes */
    switch (key_len) {
    case SRTP_AES_GCM_128_KEY_LEN_WSALT:
        (*c)->type = &srtp_aes_gcm_128_openssl;
        (*c)->algorithm = SRTP_AES_GCM_128;
        gcm->key_size = SRTP_AES_128_KEY_LEN;
        gcm->tag_len = tlen;
        break;
    case SRTP_AES_GCM_256_KEY_LEN_WSALT:
        (*c)->type = &srtp_aes_gcm_256_openssl;
        (*c)->algorithm = SRTP_AES_GCM_256;
        gcm->key_size = SRTP_AES_256_KEY_LEN;
        gcm->tag_len = tlen;
        break;
    }

    /* set key size        */
    (*c)->key_len = key_len;

    return (srtp_err_status_ok);
}

/*
 * This function deallocates a GCM session
 */
static srtp_err_status_t srtp_aes_gcm_openssl_dealloc(srtp_cipher_t *c)
{
    srtp_aes_gcm_ctx_t *ctx;

    ctx = (srtp_aes_gcm_ctx_t *)c->state;
    if (ctx) {
        EVP_CIPHER_CTX_free(ctx->ctx);
        /* zeroize the key material */
        octet_string_set_to_zero(ctx, sizeof(srtp_aes_gcm_ctx_t));
        srtp_crypto_free(ctx);
    }

    /* free memory */
    srtp_crypto_free(c);

    return (srtp_err_status_ok);
}

/*
 * aes_gcm_openssl_context_init(...) initializes the aes_gcm_context
 * using the value in key[].
 *
 * the key is the secret key
 */
static srtp_err_status_t srtp_aes_gcm_openssl_context_init(void *cv,
                                                           const uint8_t *key)
{
    srtp_aes_gcm_ctx_t *c = (srtp_aes_gcm_ctx_t *)cv;
    const EVP_CIPHER *evp;

    c->dir = srtp_direction_any;

    debug_print(srtp_mod_aes_gcm, "key:  %s",
                srtp_octet_string_hex_string(key, c->key_size));

    switch (c->key_size) {
    case SRTP_AES_256_KEY_LEN:
        evp = EVP_aes_256_gcm();
        break;
    case SRTP_AES_128_KEY_LEN:
        evp = EVP_aes_128_gcm();
        break;
    default:
        return (srtp_err_status_bad_param);
        break;
    }

    if (!EVP_CipherInit_ex(c->ctx, evp, NULL, key, NULL, 0)) {
        return (srtp_err_status_init_fail);
    }

    return (srtp_err_status_ok);
}

/*
 * aes_gcm_openssl_set_iv(c, iv) sets the counter value to the exor of iv with
 * the offset
 */
static srtp_err_status_t srtp_aes_gcm_openssl_set_iv(
    void *cv,
    uint8_t *iv,
    srtp_cipher_direction_t direction)
{
    srtp_aes_gcm_ctx_t *c = (srtp_aes_gcm_ctx_t *)cv;

    if (direction != srtp_direction_encrypt &&
        direction != srtp_direction_decrypt) {
        return (srtp_err_status_bad_param);
    }
    c->dir = direction;

    debug_print(srtp_mod_aes_gcm, "setting iv: %s",
                v128_hex_string((v128_t *)iv));

    if (!EVP_CIPHER_CTX_ctrl(c->ctx, EVP_CTRL_GCM_SET_IVLEN, 12, 0)) {
        return (srtp_err_status_init_fail);
    }

    if (!EVP_CipherInit_ex(c->ctx, NULL, NULL, NULL, iv,
                           (c->dir == srtp_direction_encrypt ? 1 : 0))) {
        return (srtp_err_status_init_fail);
    }

    return (srtp_err_status_ok);
}

/*
 * This function processes the AAD
 *
 * Parameters:
 *	c	Crypto context
 *	aad	Additional data to process for AEAD cipher suites
 *	aad_len	length of aad buffer
 */
static srtp_err_status_t srtp_aes_gcm_openssl_set_aad(void *cv,
                                                      const uint8_t *aad,
                                                      uint32_t aad_len)
{
    srtp_aes_gcm_ctx_t *c = (srtp_aes_gcm_ctx_t *)cv;
    int rv;

    /*
     * Set dummy tag, OpenSSL requires the Tag to be set before
     * processing AAD
     */

    /*
     * OpenSSL never write to address pointed by the last parameter of
     * EVP_CIPHER_CTX_ctrl while EVP_CTRL_GCM_SET_TAG (in reality,
     * OpenSSL copy its content to the context), so we can make
     * aad read-only in this function and all its wrappers.
     */
    unsigned char dummy_tag[GCM_AUTH_TAG_LEN];
    memset(dummy_tag, 0x0, GCM_AUTH_TAG_LEN);
    EVP_CIPHER_CTX_ctrl(c->ctx, EVP_CTRL_GCM_SET_TAG, c->tag_len, &dummy_tag);

    rv = EVP_Cipher(c->ctx, NULL, aad, aad_len);
    if (rv != aad_len) {
        return (srtp_err_status_algo_fail);
    } else {
        return (srtp_err_status_ok);
    }
}

/*
 * This function encrypts a buffer using AES GCM mode
 *
 * Parameters:
 *	c	Crypto context
 *	buf	data to encrypt
 *	enc_len	length of encrypt buffer
 */
static srtp_err_status_t srtp_aes_gcm_openssl_encrypt(void *cv,
                                                      unsigned char *buf,
                                                      unsigned int *enc_len)
{
    srtp_aes_gcm_ctx_t *c = (srtp_aes_gcm_ctx_t *)cv;
    if (c->dir != srtp_direction_encrypt && c->dir != srtp_direction_decrypt) {
        return (srtp_err_status_bad_param);
    }

    /*
     * Encrypt the data
     */
    EVP_Cipher(c->ctx, buf, buf, *enc_len);

    return (srtp_err_status_ok);
}

/*
 * This function calculates and returns the GCM tag for a given context.
 * This should be called after encrypting the data.  The *len value
 * is increased by the tag size.  The caller must ensure that *buf has
 * enough room to accept the appended tag.
 *
 * Parameters:
 *	c	Crypto context
 *	buf	data to encrypt
 *	len	length of encrypt buffer
 */
static srtp_err_status_t srtp_aes_gcm_openssl_get_tag(void *cv,
                                                      uint8_t *buf,
                                                      uint32_t *len)
{
    srtp_aes_gcm_ctx_t *c = (srtp_aes_gcm_ctx_t *)cv;
    /*
     * Calculate the tag
     */
    EVP_Cipher(c->ctx, NULL, NULL, 0);

    /*
     * Retreive the tag
     */
    EVP_CIPHER_CTX_ctrl(c->ctx, EVP_CTRL_GCM_GET_TAG, c->tag_len, buf);

    /*
     * Increase encryption length by desired tag size
     */
    *len = c->tag_len;

    return (srtp_err_status_ok);
}

/*
 * This function decrypts a buffer using AES GCM mode
 *
 * Parameters:
 *	c	Crypto context
 *	buf	data to encrypt
 *	enc_len	length of encrypt buffer
 */
static srtp_err_status_t srtp_aes_gcm_openssl_decrypt(void *cv,
                                                      unsigned char *buf,
                                                      unsigned int *enc_len)
{
    srtp_aes_gcm_ctx_t *c = (srtp_aes_gcm_ctx_t *)cv;
    if (c->dir != srtp_direction_encrypt && c->dir != srtp_direction_decrypt) {
        return (srtp_err_status_bad_param);
    }

    /*
     * Set the tag before decrypting
     */
    EVP_CIPHER_CTX_ctrl(c->ctx, EVP_CTRL_GCM_SET_TAG, c->tag_len,
                        buf + (*enc_len - c->tag_len));
    EVP_Cipher(c->ctx, buf, buf, *enc_len - c->tag_len);

    /*
     * Check the tag
     */
    if (EVP_Cipher(c->ctx, NULL, NULL, 0)) {
        return (srtp_err_status_auth_fail);
    }

    /*
     * Reduce the buffer size by the tag length since the tag
     * is not part of the original payload
     */
    *enc_len -= c->tag_len;

    return (srtp_err_status_ok);
}

/*
 * Name of this crypto engine
 */
static const char srtp_aes_gcm_128_openssl_description[] =
    "AES-128 GCM using openssl";
static const char srtp_aes_gcm_256_openssl_description[] =
    "AES-256 GCM using openssl";

/*
 * KAT values for AES self-test.  These
 * values we're derived from independent test code
 * using OpenSSL.
 */
/* clang-format off */
static const uint8_t srtp_aes_gcm_test_case_0_key[SRTP_AES_GCM_128_KEY_LEN_WSALT] = {
    0xfe, 0xff, 0xe9, 0x92, 0x86, 0x65, 0x73, 0x1c,
    0x6d, 0x6a, 0x8f, 0x94, 0x67, 0x30, 0x83, 0x08,
    0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
    0x09, 0x0a, 0x0b, 0x0c,
};
/* clang-format on */

/* clang-format off */
static uint8_t srtp_aes_gcm_test_case_0_iv[12] = {
    0xca, 0xfe, 0xba, 0xbe, 0xfa, 0xce, 0xdb, 0xad,
    0xde, 0xca, 0xf8, 0x88
};
/* clang-format on */

/* clang-format off */
static const uint8_t srtp_aes_gcm_test_case_0_plaintext[60] =  {
    0xd9, 0x31, 0x32, 0x25, 0xf8, 0x84, 0x06, 0xe5,
    0xa5, 0x59, 0x09, 0xc5, 0xaf, 0xf5, 0x26, 0x9a,
    0x86, 0xa7, 0xa9, 0x53, 0x15, 0x34, 0xf7, 0xda,
    0x2e, 0x4c, 0x30, 0x3d, 0x8a, 0x31, 0x8a, 0x72,
    0x1c, 0x3c, 0x0c, 0x95, 0x95, 0x68, 0x09, 0x53,
    0x2f, 0xcf, 0x0e, 0x24, 0x49, 0xa6, 0xb5, 0x25,
    0xb1, 0x6a, 0xed, 0xf5, 0xaa, 0x0d, 0xe6, 0x57,
    0xba, 0x63, 0x7b, 0x39
};

/* clang-format off */
static const uint8_t srtp_aes_gcm_test_case_0_aad[20] = {
    0xfe, 0xed, 0xfa, 0xce, 0xde, 0xad, 0xbe, 0xef,
    0xfe, 0xed, 0xfa, 0xce, 0xde, 0xad, 0xbe, 0xef,
    0xab, 0xad, 0xda, 0xd2
};
/* clang-format on */

/* clang-format off */
static const uint8_t srtp_aes_gcm_test_case_0_ciphertext[76] = {
    0x42, 0x83, 0x1e, 0xc2, 0x21, 0x77, 0x74, 0x24,
    0x4b, 0x72, 0x21, 0xb7, 0x84, 0xd0, 0xd4, 0x9c,
    0xe3, 0xaa, 0x21, 0x2f, 0x2c, 0x02, 0xa4, 0xe0,
    0x35, 0xc1, 0x7e, 0x23, 0x29, 0xac, 0xa1, 0x2e,
    0x21, 0xd5, 0x14, 0xb2, 0x54, 0x66, 0x93, 0x1c,
    0x7d, 0x8f, 0x6a, 0x5a, 0xac, 0x84, 0xaa, 0x05,
    0x1b, 0xa3, 0x0b, 0x39, 0x6a, 0x0a, 0xac, 0x97,
    0x3d, 0x58, 0xe0, 0x91,
    /* the last 16 bytes are the tag */
    0x5b, 0xc9, 0x4f, 0xbc, 0x32, 0x21, 0xa5, 0xdb,
    0x94, 0xfa, 0xe9, 0x5a, 0xe7, 0x12, 0x1a, 0x47,
};
/* clang-format on */

static const srtp_cipher_test_case_t srtp_aes_gcm_test_case_0a = {
    SRTP_AES_GCM_128_KEY_LEN_WSALT,      /* octets in key            */
    srtp_aes_gcm_test_case_0_key,        /* key                      */
    srtp_aes_gcm_test_case_0_iv,         /* packet index             */
    60,                                  /* octets in plaintext      */
    srtp_aes_gcm_test_case_0_plaintext,  /* plaintext                */
    68,                                  /* octets in ciphertext     */
    srtp_aes_gcm_test_case_0_ciphertext, /* ciphertext  + tag        */
    20,                                  /* octets in AAD            */
    srtp_aes_gcm_test_case_0_aad,        /* AAD                      */
    GCM_AUTH_TAG_LEN_8,                  /* */
    NULL                                 /* pointer to next testcase */
};

static const srtp_cipher_test_case_t srtp_aes_gcm_test_case_0 = {
    SRTP_AES_GCM_128_KEY_LEN_WSALT,      /* octets in key            */
    srtp_aes_gcm_test_case_0_key,        /* key                      */
    srtp_aes_gcm_test_case_0_iv,         /* packet index             */
    60,                                  /* octets in plaintext      */
    srtp_aes_gcm_test_case_0_plaintext,  /* plaintext                */
    76,                                  /* octets in ciphertext     */
    srtp_aes_gcm_test_case_0_ciphertext, /* ciphertext  + tag        */
    20,                                  /* octets in AAD            */
    srtp_aes_gcm_test_case_0_aad,        /* AAD                      */
    GCM_AUTH_TAG_LEN,                    /* */
    &srtp_aes_gcm_test_case_0a           /* pointer to next testcase */
};

/* clang-format off */
static const uint8_t srtp_aes_gcm_test_case_1_key[SRTP_AES_GCM_256_KEY_LEN_WSALT] = {
    0xfe, 0xff, 0xe9, 0x92, 0x86, 0x65, 0x73, 0x1c,
    0xa5, 0x59, 0x09, 0xc5, 0x54, 0x66, 0x93, 0x1c,
    0xaf, 0xf5, 0x26, 0x9a, 0x21, 0xd5, 0x14, 0xb2,
    0x6d, 0x6a, 0x8f, 0x94, 0x67, 0x30, 0x83, 0x08,
    0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
    0x09, 0x0a, 0x0b, 0x0c,
};
/* clang-format on */

/* clang-format off */
static uint8_t srtp_aes_gcm_test_case_1_iv[12] = {
    0xca, 0xfe, 0xba, 0xbe, 0xfa, 0xce, 0xdb, 0xad,
    0xde, 0xca, 0xf8, 0x88
};
/* clang-format on */

/* clang-format off */
static const uint8_t srtp_aes_gcm_test_case_1_plaintext[60] =  {
    0xd9, 0x31, 0x32, 0x25, 0xf8, 0x84, 0x06, 0xe5,
    0xa5, 0x59, 0x09, 0xc5, 0xaf, 0xf5, 0x26, 0x9a,
    0x86, 0xa7, 0xa9, 0x53, 0x15, 0x34, 0xf7, 0xda,
    0x2e, 0x4c, 0x30, 0x3d, 0x8a, 0x31, 0x8a, 0x72,
    0x1c, 0x3c, 0x0c, 0x95, 0x95, 0x68, 0x09, 0x53,
    0x2f, 0xcf, 0x0e, 0x24, 0x49, 0xa6, 0xb5, 0x25,
    0xb1, 0x6a, 0xed, 0xf5, 0xaa, 0x0d, 0xe6, 0x57,
    0xba, 0x63, 0x7b, 0x39
};
/* clang-format on */

/* clang-format off */
static const uint8_t srtp_aes_gcm_test_case_1_aad[20] = {
    0xfe, 0xed, 0xfa, 0xce, 0xde, 0xad, 0xbe, 0xef,
    0xfe, 0xed, 0xfa, 0xce, 0xde, 0xad, 0xbe, 0xef,
    0xab, 0xad, 0xda, 0xd2
};
/* clang-format on */

/* clang-format off */
static const uint8_t srtp_aes_gcm_test_case_1_ciphertext[76] = {
    0x0b, 0x11, 0xcf, 0xaf, 0x68, 0x4d, 0xae, 0x46,
    0xc7, 0x90, 0xb8, 0x8e, 0xb7, 0x6a, 0x76, 0x2a,
    0x94, 0x82, 0xca, 0xab, 0x3e, 0x39, 0xd7, 0x86,
    0x1b, 0xc7, 0x93, 0xed, 0x75, 0x7f, 0x23, 0x5a,
    0xda, 0xfd, 0xd3, 0xe2, 0x0e, 0x80, 0x87, 0xa9,
    0x6d, 0xd7, 0xe2, 0x6a, 0x7d, 0x5f, 0xb4, 0x80,
    0xef, 0xef, 0xc5, 0x29, 0x12, 0xd1, 0xaa, 0x10,
    0x09, 0xc9, 0x86, 0xc1,
    /* the last 16 bytes are the tag */
    0x45, 0xbc, 0x03, 0xe6, 0xe1, 0xac, 0x0a, 0x9f,
    0x81, 0xcb, 0x8e, 0x5b, 0x46, 0x65, 0x63, 0x1d,
};
/* clang-format on */

static const srtp_cipher_test_case_t srtp_aes_gcm_test_case_1a = {
    SRTP_AES_GCM_256_KEY_LEN_WSALT,      /* octets in key            */
    srtp_aes_gcm_test_case_1_key,        /* key                      */
    srtp_aes_gcm_test_case_1_iv,         /* packet index             */
    60,                                  /* octets in plaintext      */
    srtp_aes_gcm_test_case_1_plaintext,  /* plaintext                */
    68,                                  /* octets in ciphertext     */
    srtp_aes_gcm_test_case_1_ciphertext, /* ciphertext  + tag        */
    20,                                  /* octets in AAD            */
    srtp_aes_gcm_test_case_1_aad,        /* AAD                      */
    GCM_AUTH_TAG_LEN_8,                  /* */
    NULL                                 /* pointer to next testcase */
};

static const srtp_cipher_test_case_t srtp_aes_gcm_test_case_1 = {
    SRTP_AES_GCM_256_KEY_LEN_WSALT,      /* octets in key            */
    srtp_aes_gcm_test_case_1_key,        /* key                      */
    srtp_aes_gcm_test_case_1_iv,         /* packet index             */
    60,                                  /* octets in plaintext      */
    srtp_aes_gcm_test_case_1_plaintext,  /* plaintext                */
    76,                                  /* octets in ciphertext     */
    srtp_aes_gcm_test_case_1_ciphertext, /* ciphertext  + tag        */
    20,                                  /* octets in AAD            */
    srtp_aes_gcm_test_case_1_aad,        /* AAD                      */
    GCM_AUTH_TAG_LEN,                    /* */
    &srtp_aes_gcm_test_case_1a           /* pointer to next testcase */
};

/*
 * This is the vector function table for this crypto engine.
 */
const srtp_cipher_type_t srtp_aes_gcm_128_openssl = {
    srtp_aes_gcm_openssl_alloc,
    srtp_aes_gcm_openssl_dealloc,
    srtp_aes_gcm_openssl_context_init,
    srtp_aes_gcm_openssl_set_aad,
    srtp_aes_gcm_openssl_encrypt,
    srtp_aes_gcm_openssl_decrypt,
    srtp_aes_gcm_openssl_set_iv,
    srtp_aes_gcm_openssl_get_tag,
    srtp_aes_gcm_128_openssl_description,
    &srtp_aes_gcm_test_case_0,
    SRTP_AES_GCM_128
};

/*
 * This is the vector function table for this crypto engine.
 */
const srtp_cipher_type_t srtp_aes_gcm_256_openssl = {
    srtp_aes_gcm_openssl_alloc,
    srtp_aes_gcm_openssl_dealloc,
    srtp_aes_gcm_openssl_context_init,
    srtp_aes_gcm_openssl_set_aad,
    srtp_aes_gcm_openssl_encrypt,
    srtp_aes_gcm_openssl_decrypt,
    srtp_aes_gcm_openssl_set_iv,
    srtp_aes_gcm_openssl_get_tag,
    srtp_aes_gcm_256_openssl_description,
    &srtp_aes_gcm_test_case_1,
    SRTP_AES_GCM_256
};
