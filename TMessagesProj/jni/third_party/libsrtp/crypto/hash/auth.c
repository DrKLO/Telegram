/*
 * auth.c
 *
 * some bookkeeping functions for authentication functions
 *
 * David A. McGrew
 * Cisco Systems, Inc.
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

#include "auth.h"
#include "err.h"       /* for srtp_debug */
#include "datatypes.h" /* for octet_string */

/* the debug module for authentiation */

srtp_debug_module_t srtp_mod_auth = {
    0,          /* debugging is off by default */
    "auth func" /* printable name for module   */
};

int srtp_auth_get_key_length(const srtp_auth_t *a)
{
    return a->key_len;
}

int srtp_auth_get_tag_length(const srtp_auth_t *a)
{
    return a->out_len;
}

int srtp_auth_get_prefix_length(const srtp_auth_t *a)
{
    return a->prefix_len;
}

/*
 * srtp_auth_type_test() tests an auth function of type ct against
 * test cases provided in a list test_data of values of key, data, and tag
 * that is known to be good
 */

/* should be big enough for most occasions */
#define SELF_TEST_TAG_BUF_OCTETS 32

srtp_err_status_t srtp_auth_type_test(const srtp_auth_type_t *at,
                                      const srtp_auth_test_case_t *test_data)
{
    const srtp_auth_test_case_t *test_case = test_data;
    srtp_auth_t *a;
    srtp_err_status_t status;
    uint8_t tag[SELF_TEST_TAG_BUF_OCTETS];
    int i, case_num = 0;

    debug_print(srtp_mod_auth, "running self-test for auth function %s",
                at->description);

    /*
     * check to make sure that we have at least one test case, and
     * return an error if we don't - we need to be paranoid here
     */
    if (test_case == NULL) {
        return srtp_err_status_cant_check;
    }

    /* loop over all test cases */
    while (test_case != NULL) {
        /* check test case parameters */
        if (test_case->tag_length_octets > SELF_TEST_TAG_BUF_OCTETS) {
            return srtp_err_status_bad_param;
        }

        /* allocate auth */
        status = srtp_auth_type_alloc(at, &a, test_case->key_length_octets,
                                      test_case->tag_length_octets);
        if (status) {
            return status;
        }

        /* initialize auth */
        status = srtp_auth_init(a, test_case->key);
        if (status) {
            srtp_auth_dealloc(a);
            return status;
        }

        /* zeroize tag then compute */
        octet_string_set_to_zero(tag, test_case->tag_length_octets);
        status = srtp_auth_compute(a, test_case->data,
                                   test_case->data_length_octets, tag);
        if (status) {
            srtp_auth_dealloc(a);
            return status;
        }

        debug_print(srtp_mod_auth, "key: %s",
                    srtp_octet_string_hex_string(test_case->key,
                                                 test_case->key_length_octets));
        debug_print(srtp_mod_auth, "data: %s",
                    srtp_octet_string_hex_string(
                        test_case->data, test_case->data_length_octets));
        debug_print(
            srtp_mod_auth, "tag computed: %s",
            srtp_octet_string_hex_string(tag, test_case->tag_length_octets));
        debug_print(srtp_mod_auth, "tag expected: %s",
                    srtp_octet_string_hex_string(test_case->tag,
                                                 test_case->tag_length_octets));

        /* check the result */
        status = srtp_err_status_ok;
        for (i = 0; i < test_case->tag_length_octets; i++) {
            if (tag[i] != test_case->tag[i]) {
                status = srtp_err_status_algo_fail;
                debug_print(srtp_mod_auth, "test case %d failed", case_num);
                debug_print(srtp_mod_auth, "  (mismatch at octet %d)", i);
            }
        }
        if (status) {
            srtp_auth_dealloc(a);
            return srtp_err_status_algo_fail;
        }

        /* deallocate the auth function */
        status = srtp_auth_dealloc(a);
        if (status) {
            return status;
        }

        /*
         * the auth function passed the test case, so move on to the next test
         * case in the list; if NULL, we'll quit and return an OK
         */
        test_case = test_case->next_test_case;
        ++case_num;
    }

    return srtp_err_status_ok;
}

/*
 * srtp_auth_type_self_test(at) performs srtp_auth_type_test on at's internal
 * list of test data.
 */

srtp_err_status_t srtp_auth_type_self_test(const srtp_auth_type_t *at)
{
    return srtp_auth_type_test(at, at->test_data);
}
