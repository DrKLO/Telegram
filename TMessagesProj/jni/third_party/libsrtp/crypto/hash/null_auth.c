/*
 * null_auth.c
 *
 * implements the do-nothing auth algorithm
 *
 * David A. McGrew
 * Cisco Systems, Inc.
 *
 */

/*
 *
 * Copyright (c) 2001-2017, Cisco Systems, Inc.
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

#include "null_auth.h"
#include "err.h" /* for srtp_debug */
#include "alloc.h"
#include "cipher_types.h"

static srtp_err_status_t srtp_null_auth_alloc(srtp_auth_t **a,
                                              int key_len,
                                              int out_len)
{
    extern const srtp_auth_type_t srtp_null_auth;
    uint8_t *pointer;

    debug_print(srtp_mod_auth, "allocating auth func with key length %d",
                key_len);
    debug_print(srtp_mod_auth, "                          tag length %d",
                out_len);

    /* allocate memory for auth and srtp_null_auth_ctx_t structures */
    pointer = (uint8_t *)srtp_crypto_alloc(sizeof(srtp_null_auth_ctx_t) +
                                           sizeof(srtp_auth_t));
    if (pointer == NULL) {
        return srtp_err_status_alloc_fail;
    }

    /* set pointers */
    *a = (srtp_auth_t *)pointer;
    (*a)->type = &srtp_null_auth;
    (*a)->state = pointer + sizeof(srtp_auth_t);
    (*a)->out_len = out_len;
    (*a)->prefix_len = out_len;
    (*a)->key_len = key_len;

    return srtp_err_status_ok;
}

static srtp_err_status_t srtp_null_auth_dealloc(srtp_auth_t *a)
{
    extern const srtp_auth_type_t srtp_null_auth;

    /* zeroize entire state*/
    octet_string_set_to_zero(a, sizeof(srtp_null_auth_ctx_t) +
                                    sizeof(srtp_auth_t));

    /* free memory */
    srtp_crypto_free(a);

    return srtp_err_status_ok;
}

static srtp_err_status_t srtp_null_auth_init(void *statev,
                                             const uint8_t *key,
                                             int key_len)
{
    /* srtp_null_auth_ctx_t *state = (srtp_null_auth_ctx_t *)statev; */
    /* accept any length of key, and do nothing */

    return srtp_err_status_ok;
}

static srtp_err_status_t srtp_null_auth_compute(void *statev,
                                                const uint8_t *message,
                                                int msg_octets,
                                                int tag_len,
                                                uint8_t *result)
{
    /* srtp_null_auth_ctx_t *state = (srtp_null_auth_ctx_t *)statev; */

    return srtp_err_status_ok;
}

static srtp_err_status_t srtp_null_auth_update(void *statev,
                                               const uint8_t *message,
                                               int msg_octets)
{
    /* srtp_null_auth_ctx_t *state = (srtp_null_auth_ctx_t *)statev; */

    return srtp_err_status_ok;
}

static srtp_err_status_t srtp_null_auth_start(void *statev)
{
    /* srtp_null_auth_ctx_t *state = (srtp_null_auth_ctx_t *)statev; */

    return srtp_err_status_ok;
}

/*
 * srtp_auth_type_t - defines description, test case, and null_auth
 * metaobject
 */

/* begin test case 0 */

static const srtp_auth_test_case_t srtp_null_auth_test_case_0 = {
    0,    /* octets in key            */
    NULL, /* key                      */
    0,    /* octets in data           */
    NULL, /* data                     */
    0,    /* octets in tag            */
    NULL, /* tag                      */
    NULL  /* pointer to next testcase */
};

/* end test case 0 */

static const char srtp_null_auth_description[] = "null authentication function";

const srtp_auth_type_t srtp_null_auth = {
    srtp_null_auth_alloc,        /* */
    srtp_null_auth_dealloc,      /* */
    srtp_null_auth_init,         /* */
    srtp_null_auth_compute,      /* */
    srtp_null_auth_update,       /* */
    srtp_null_auth_start,        /* */
    srtp_null_auth_description,  /* */
    &srtp_null_auth_test_case_0, /* */
    SRTP_NULL_AUTH               /* */
};
