/*
 * null_cipher.c
 *
 * A null cipher implementation.  This cipher leaves the plaintext
 * unchanged.
 *
 * David A. McGrew
 * Cisco Systems, Inc.
 */

/*
 *
 * Copyright (c) 2001-2017 Cisco Systems, Inc.
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

#include "datatypes.h"
#include "null_cipher.h"
#include "err.h" /* for srtp_debug */
#include "alloc.h"
#include "cipher_types.h"

static srtp_err_status_t srtp_null_cipher_alloc(srtp_cipher_t **c,
                                                int key_len,
                                                int tlen)
{
    extern const srtp_cipher_type_t srtp_null_cipher;

    debug_print(srtp_mod_cipher, "allocating cipher with key length %d",
                key_len);

    /* allocate memory a cipher of type null_cipher */
    *c = (srtp_cipher_t *)srtp_crypto_alloc(sizeof(srtp_cipher_t));
    if (*c == NULL) {
        return srtp_err_status_alloc_fail;
    }

    /* set pointers */
    (*c)->algorithm = SRTP_NULL_CIPHER;
    (*c)->type = &srtp_null_cipher;
    (*c)->state = (void *)0x1; /* The null cipher does not maintain state */

    /* set key size */
    (*c)->key_len = key_len;

    return srtp_err_status_ok;
}

static srtp_err_status_t srtp_null_cipher_dealloc(srtp_cipher_t *c)
{
    extern const srtp_cipher_type_t srtp_null_cipher;

    /* zeroize entire state*/
    octet_string_set_to_zero(c, sizeof(srtp_cipher_t));

    /* free memory of type null_cipher */
    srtp_crypto_free(c);

    return srtp_err_status_ok;
}

static srtp_err_status_t srtp_null_cipher_init(void *cv, const uint8_t *key)
{
    /* srtp_null_cipher_ctx_t *c = (srtp_null_cipher_ctx_t *)cv; */

    debug_print(srtp_mod_cipher, "initializing null cipher", NULL);

    return srtp_err_status_ok;
}

static srtp_err_status_t srtp_null_cipher_set_iv(void *cv,
                                                 uint8_t *iv,
                                                 srtp_cipher_direction_t dir)
{
    /* srtp_null_cipher_ctx_t *c = (srtp_null_cipher_ctx_t *)cv; */
    return srtp_err_status_ok;
}

static srtp_err_status_t srtp_null_cipher_encrypt(void *cv,
                                                  unsigned char *buf,
                                                  unsigned int *bytes_to_encr)
{
    /* srtp_null_cipher_ctx_t *c = (srtp_null_cipher_ctx_t *)cv; */
    return srtp_err_status_ok;
}

static const char srtp_null_cipher_description[] = "null cipher";

static const srtp_cipher_test_case_t srtp_null_cipher_test_0 = {
    0,    /* octets in key            */
    NULL, /* key                      */
    0,    /* packet index             */
    0,    /* octets in plaintext      */
    NULL, /* plaintext                */
    0,    /* octets in plaintext      */
    NULL, /* ciphertext               */
    0,    /* */
    NULL, /* */
    0,    /* */
    NULL  /* pointer to next testcase */
};

/*
 * note: the decrypt function is idential to the encrypt function
 */

const srtp_cipher_type_t srtp_null_cipher = {
    srtp_null_cipher_alloc,       /* */
    srtp_null_cipher_dealloc,     /* */
    srtp_null_cipher_init,        /* */
    0,                            /* set_aad */
    srtp_null_cipher_encrypt,     /* */
    srtp_null_cipher_encrypt,     /* */
    srtp_null_cipher_set_iv,      /* */
    0,                            /* get_tag */
    srtp_null_cipher_description, /* */
    &srtp_null_cipher_test_0,     /* */
    SRTP_NULL_CIPHER              /* */
};
