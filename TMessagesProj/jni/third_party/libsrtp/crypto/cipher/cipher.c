/*
 * cipher.c
 *
 * cipher meta-functions
 *
 * David A. McGrew
 * Cisco Systems, Inc.
 *
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

#include "cipher.h"
#include "crypto_types.h"
#include "err.h"   /* for srtp_debug */
#include "alloc.h" /* for crypto_alloc(), crypto_free()  */

srtp_debug_module_t srtp_mod_cipher = {
    0,       /* debugging is off by default */
    "cipher" /* printable module name       */
};

srtp_err_status_t srtp_cipher_type_alloc(const srtp_cipher_type_t *ct,
                                         srtp_cipher_t **c,
                                         int key_len,
                                         int tlen)
{
    if (!ct || !ct->alloc) {
        return (srtp_err_status_bad_param);
    }
    return ((ct)->alloc((c), (key_len), (tlen)));
}

srtp_err_status_t srtp_cipher_dealloc(srtp_cipher_t *c)
{
    if (!c || !c->type) {
        return (srtp_err_status_bad_param);
    }
    return (((c)->type)->dealloc(c));
}

srtp_err_status_t srtp_cipher_init(srtp_cipher_t *c, const uint8_t *key)
{
    if (!c || !c->type || !c->state) {
        return (srtp_err_status_bad_param);
    }
    return (((c)->type)->init(((c)->state), (key)));
}

srtp_err_status_t srtp_cipher_set_iv(srtp_cipher_t *c,
                                     uint8_t *iv,
                                     int direction)
{
    if (!c || !c->type || !c->state) {
        return (srtp_err_status_bad_param);
    }

    return (((c)->type)->set_iv(((c)->state), iv, direction));
}

srtp_err_status_t srtp_cipher_output(srtp_cipher_t *c,
                                     uint8_t *buffer,
                                     uint32_t *num_octets_to_output)
{
    /* zeroize the buffer */
    octet_string_set_to_zero(buffer, *num_octets_to_output);

    /* exor keystream into buffer */
    return (((c)->type)->encrypt(((c)->state), buffer, num_octets_to_output));
}

srtp_err_status_t srtp_cipher_encrypt(srtp_cipher_t *c,
                                      uint8_t *buffer,
                                      uint32_t *num_octets_to_output)
{
    if (!c || !c->type || !c->state) {
        return (srtp_err_status_bad_param);
    }

    return (((c)->type)->encrypt(((c)->state), buffer, num_octets_to_output));
}

srtp_err_status_t srtp_cipher_decrypt(srtp_cipher_t *c,
                                      uint8_t *buffer,
                                      uint32_t *num_octets_to_output)
{
    if (!c || !c->type || !c->state) {
        return (srtp_err_status_bad_param);
    }

    return (((c)->type)->decrypt(((c)->state), buffer, num_octets_to_output));
}

srtp_err_status_t srtp_cipher_get_tag(srtp_cipher_t *c,
                                      uint8_t *buffer,
                                      uint32_t *tag_len)
{
    if (!c || !c->type || !c->state) {
        return (srtp_err_status_bad_param);
    }
    if (!((c)->type)->get_tag) {
        return (srtp_err_status_no_such_op);
    }

    return (((c)->type)->get_tag(((c)->state), buffer, tag_len));
}

srtp_err_status_t srtp_cipher_set_aad(srtp_cipher_t *c,
                                      const uint8_t *aad,
                                      uint32_t aad_len)
{
    if (!c || !c->type || !c->state) {
        return (srtp_err_status_bad_param);
    }
    if (!((c)->type)->set_aad) {
        return (srtp_err_status_no_such_op);
    }

    return (((c)->type)->set_aad(((c)->state), aad, aad_len));
}

/* some bookkeeping functions */

int srtp_cipher_get_key_length(const srtp_cipher_t *c)
{
    return c->key_len;
}

/*
 * A trivial platform independent random source.  The random
 * data is used for some of the cipher self-tests.
 */
static srtp_err_status_t srtp_cipher_rand(void *dest, uint32_t len)
{
#if defined(HAVE_RAND_S)
    uint8_t *dst = (uint8_t *)dest;
    while (len) {
        unsigned int val;
        errno_t err = rand_s(&val);

        if (err != 0)
            return srtp_err_status_fail;

        *dst++ = val & 0xff;
        len--;
    }
#else
    /* Generic C-library (rand()) version */
    /* This is a random source of last resort */
    uint8_t *dst = (uint8_t *)dest;
    while (len) {
        int val = rand();
        /* rand() returns 0-32767 (ugh) */
        /* Is this a good enough way to get random bytes?
           It is if it passes FIPS-140... */
        *dst++ = val & 0xff;
        len--;
    }
#endif
    return srtp_err_status_ok;
}

#define SELF_TEST_BUF_OCTETS 128
#define NUM_RAND_TESTS 128
#define MAX_KEY_LEN 64
/*
 * srtp_cipher_type_test(ct, test_data) tests a cipher of type ct against
 * test cases provided in a list test_data of values of key, salt, iv,
 * plaintext, and ciphertext that is known to be good
 */
srtp_err_status_t srtp_cipher_type_test(
    const srtp_cipher_type_t *ct,
    const srtp_cipher_test_case_t *test_data)
{
    const srtp_cipher_test_case_t *test_case = test_data;
    srtp_cipher_t *c;
    srtp_err_status_t status;
    uint8_t buffer[SELF_TEST_BUF_OCTETS];
    uint8_t buffer2[SELF_TEST_BUF_OCTETS];
    uint32_t tag_len;
    unsigned int len;
    int i, j, case_num = 0;
    unsigned k = 0;

    debug_print(srtp_mod_cipher, "running self-test for cipher %s",
                ct->description);

    /*
     * check to make sure that we have at least one test case, and
     * return an error if we don't - we need to be paranoid here
     */
    if (test_case == NULL) {
        return srtp_err_status_cant_check;
    }

    /*
     * loop over all test cases, perform known-answer tests of both the
     * encryption and decryption functions
     */
    while (test_case != NULL) {
        /* allocate cipher */
        status = srtp_cipher_type_alloc(ct, &c, test_case->key_length_octets,
                                        test_case->tag_length_octets);
        if (status) {
            return status;
        }

        /*
         * test the encrypt function
         */
        debug_print(srtp_mod_cipher, "testing encryption", NULL);

        /* initialize cipher */
        status = srtp_cipher_init(c, test_case->key);
        if (status) {
            srtp_cipher_dealloc(c);
            return status;
        }

        /* copy plaintext into test buffer */
        if (test_case->ciphertext_length_octets > SELF_TEST_BUF_OCTETS) {
            srtp_cipher_dealloc(c);
            return srtp_err_status_bad_param;
        }
        for (k = 0; k < test_case->plaintext_length_octets; k++) {
            buffer[k] = test_case->plaintext[k];
        }

        debug_print(srtp_mod_cipher, "plaintext:    %s",
                    srtp_octet_string_hex_string(
                        buffer, test_case->plaintext_length_octets));

        /* set the initialization vector */
        status = srtp_cipher_set_iv(c, (uint8_t *)test_case->idx,
                                    srtp_direction_encrypt);
        if (status) {
            srtp_cipher_dealloc(c);
            return status;
        }

        if (c->algorithm == SRTP_AES_GCM_128 ||
            c->algorithm == SRTP_AES_GCM_256) {
            debug_print(srtp_mod_cipher, "IV:    %s",
                        srtp_octet_string_hex_string(test_case->idx, 12));

            /*
             * Set the AAD
             */
            status = srtp_cipher_set_aad(c, test_case->aad,
                                         test_case->aad_length_octets);
            if (status) {
                srtp_cipher_dealloc(c);
                return status;
            }
            debug_print(srtp_mod_cipher, "AAD:    %s",
                        srtp_octet_string_hex_string(
                            test_case->aad, test_case->aad_length_octets));
        }

        /* encrypt */
        len = test_case->plaintext_length_octets;
        status = srtp_cipher_encrypt(c, buffer, &len);
        if (status) {
            srtp_cipher_dealloc(c);
            return status;
        }

        if (c->algorithm == SRTP_AES_GCM_128 ||
            c->algorithm == SRTP_AES_GCM_256) {
            /*
             * Get the GCM tag
             */
            status = srtp_cipher_get_tag(c, buffer + len, &tag_len);
            if (status) {
                srtp_cipher_dealloc(c);
                return status;
            }
            len += tag_len;
        }

        debug_print(srtp_mod_cipher, "ciphertext:   %s",
                    srtp_octet_string_hex_string(
                        buffer, test_case->ciphertext_length_octets));

        /* compare the resulting ciphertext with that in the test case */
        if (len != test_case->ciphertext_length_octets) {
            srtp_cipher_dealloc(c);
            return srtp_err_status_algo_fail;
        }
        status = srtp_err_status_ok;
        for (k = 0; k < test_case->ciphertext_length_octets; k++) {
            if (buffer[k] != test_case->ciphertext[k]) {
                status = srtp_err_status_algo_fail;
                debug_print(srtp_mod_cipher, "test case %d failed", case_num);
                debug_print(srtp_mod_cipher, "(failure at byte %u)", k);
                break;
            }
        }
        if (status) {
            debug_print(srtp_mod_cipher, "c computed: %s",
                        srtp_octet_string_hex_string(
                            buffer, 2 * test_case->plaintext_length_octets));
            debug_print(srtp_mod_cipher, "c expected: %s",
                        srtp_octet_string_hex_string(
                            test_case->ciphertext,
                            2 * test_case->plaintext_length_octets));

            srtp_cipher_dealloc(c);
            return srtp_err_status_algo_fail;
        }

        /*
         * test the decrypt function
         */
        debug_print(srtp_mod_cipher, "testing decryption", NULL);

        /* re-initialize cipher for decryption */
        status = srtp_cipher_init(c, test_case->key);
        if (status) {
            srtp_cipher_dealloc(c);
            return status;
        }

        /* copy ciphertext into test buffer */
        if (test_case->ciphertext_length_octets > SELF_TEST_BUF_OCTETS) {
            srtp_cipher_dealloc(c);
            return srtp_err_status_bad_param;
        }
        for (k = 0; k < test_case->ciphertext_length_octets; k++) {
            buffer[k] = test_case->ciphertext[k];
        }

        debug_print(srtp_mod_cipher, "ciphertext:    %s",
                    srtp_octet_string_hex_string(
                        buffer, test_case->plaintext_length_octets));

        /* set the initialization vector */
        status = srtp_cipher_set_iv(c, (uint8_t *)test_case->idx,
                                    srtp_direction_decrypt);
        if (status) {
            srtp_cipher_dealloc(c);
            return status;
        }

        if (c->algorithm == SRTP_AES_GCM_128 ||
            c->algorithm == SRTP_AES_GCM_256) {
            /*
             * Set the AAD
             */
            status = srtp_cipher_set_aad(c, test_case->aad,
                                         test_case->aad_length_octets);
            if (status) {
                srtp_cipher_dealloc(c);
                return status;
            }
            debug_print(srtp_mod_cipher, "AAD:    %s",
                        srtp_octet_string_hex_string(
                            test_case->aad, test_case->aad_length_octets));
        }

        /* decrypt */
        len = test_case->ciphertext_length_octets;
        status = srtp_cipher_decrypt(c, buffer, &len);
        if (status) {
            srtp_cipher_dealloc(c);
            return status;
        }

        debug_print(srtp_mod_cipher, "plaintext:   %s",
                    srtp_octet_string_hex_string(
                        buffer, test_case->plaintext_length_octets));

        /* compare the resulting plaintext with that in the test case */
        if (len != test_case->plaintext_length_octets) {
            srtp_cipher_dealloc(c);
            return srtp_err_status_algo_fail;
        }
        status = srtp_err_status_ok;
        for (k = 0; k < test_case->plaintext_length_octets; k++) {
            if (buffer[k] != test_case->plaintext[k]) {
                status = srtp_err_status_algo_fail;
                debug_print(srtp_mod_cipher, "test case %d failed", case_num);
                debug_print(srtp_mod_cipher, "(failure at byte %u)", k);
            }
        }
        if (status) {
            debug_print(srtp_mod_cipher, "p computed: %s",
                        srtp_octet_string_hex_string(
                            buffer, 2 * test_case->plaintext_length_octets));
            debug_print(srtp_mod_cipher, "p expected: %s",
                        srtp_octet_string_hex_string(
                            test_case->plaintext,
                            2 * test_case->plaintext_length_octets));

            srtp_cipher_dealloc(c);
            return srtp_err_status_algo_fail;
        }

        /* deallocate the cipher */
        status = srtp_cipher_dealloc(c);
        if (status) {
            return status;
        }

        /*
         * the cipher passed the test case, so move on to the next test
         * case in the list; if NULL, we'l proceed to the next test
         */
        test_case = test_case->next_test_case;
        ++case_num;
    }

    /* now run some random invertibility tests */

    /* allocate cipher, using paramaters from the first test case */
    test_case = test_data;
    status = srtp_cipher_type_alloc(ct, &c, test_case->key_length_octets,
                                    test_case->tag_length_octets);
    if (status) {
        return status;
    }

    for (j = 0; j < NUM_RAND_TESTS; j++) {
        unsigned int length;
        unsigned int plaintext_len;
        uint8_t key[MAX_KEY_LEN];
        uint8_t iv[MAX_KEY_LEN];

        /* choose a length at random (leaving room for IV and padding) */
        length = rand() % (SELF_TEST_BUF_OCTETS - 64);
        debug_print(srtp_mod_cipher, "random plaintext length %d\n", length);
        status = srtp_cipher_rand(buffer, length);
        if (status) {
            srtp_cipher_dealloc(c);
            return status;
        }

        debug_print(srtp_mod_cipher, "plaintext:    %s",
                    srtp_octet_string_hex_string(buffer, length));

        /* copy plaintext into second buffer */
        for (i = 0; (unsigned int)i < length; i++) {
            buffer2[i] = buffer[i];
        }

        /* choose a key at random */
        if (test_case->key_length_octets > MAX_KEY_LEN) {
            srtp_cipher_dealloc(c);
            return srtp_err_status_cant_check;
        }
        status = srtp_cipher_rand(key, test_case->key_length_octets);
        if (status) {
            srtp_cipher_dealloc(c);
            return status;
        }

        /* chose a random initialization vector */
        status = srtp_cipher_rand(iv, MAX_KEY_LEN);
        if (status) {
            srtp_cipher_dealloc(c);
            return status;
        }

        /* initialize cipher */
        status = srtp_cipher_init(c, key);
        if (status) {
            srtp_cipher_dealloc(c);
            return status;
        }

        /* set initialization vector */
        status = srtp_cipher_set_iv(c, (uint8_t *)test_case->idx,
                                    srtp_direction_encrypt);
        if (status) {
            srtp_cipher_dealloc(c);
            return status;
        }

        if (c->algorithm == SRTP_AES_GCM_128 ||
            c->algorithm == SRTP_AES_GCM_256) {
            /*
             * Set the AAD
             */
            status = srtp_cipher_set_aad(c, test_case->aad,
                                         test_case->aad_length_octets);
            if (status) {
                srtp_cipher_dealloc(c);
                return status;
            }
            debug_print(srtp_mod_cipher, "AAD:    %s",
                        srtp_octet_string_hex_string(
                            test_case->aad, test_case->aad_length_octets));
        }

        /* encrypt buffer with cipher */
        plaintext_len = length;
        status = srtp_cipher_encrypt(c, buffer, &length);
        if (status) {
            srtp_cipher_dealloc(c);
            return status;
        }
        if (c->algorithm == SRTP_AES_GCM_128 ||
            c->algorithm == SRTP_AES_GCM_256) {
            /*
             * Get the GCM tag
             */
            status = srtp_cipher_get_tag(c, buffer + length, &tag_len);
            if (status) {
                srtp_cipher_dealloc(c);
                return status;
            }
            length += tag_len;
        }
        debug_print(srtp_mod_cipher, "ciphertext:   %s",
                    srtp_octet_string_hex_string(buffer, length));

        /*
         * re-initialize cipher for decryption, re-set the iv, then
         * decrypt the ciphertext
         */
        status = srtp_cipher_init(c, key);
        if (status) {
            srtp_cipher_dealloc(c);
            return status;
        }
        status = srtp_cipher_set_iv(c, (uint8_t *)test_case->idx,
                                    srtp_direction_decrypt);
        if (status) {
            srtp_cipher_dealloc(c);
            return status;
        }
        if (c->algorithm == SRTP_AES_GCM_128 ||
            c->algorithm == SRTP_AES_GCM_256) {
            /*
             * Set the AAD
             */
            status = srtp_cipher_set_aad(c, test_case->aad,
                                         test_case->aad_length_octets);
            if (status) {
                srtp_cipher_dealloc(c);
                return status;
            }
            debug_print(srtp_mod_cipher, "AAD:    %s",
                        srtp_octet_string_hex_string(
                            test_case->aad, test_case->aad_length_octets));
        }
        status = srtp_cipher_decrypt(c, buffer, &length);
        if (status) {
            srtp_cipher_dealloc(c);
            return status;
        }

        debug_print(srtp_mod_cipher, "plaintext[2]: %s",
                    srtp_octet_string_hex_string(buffer, length));

        /* compare the resulting plaintext with the original one */
        if (length != plaintext_len) {
            srtp_cipher_dealloc(c);
            return srtp_err_status_algo_fail;
        }
        status = srtp_err_status_ok;
        for (k = 0; k < plaintext_len; k++) {
            if (buffer[k] != buffer2[k]) {
                status = srtp_err_status_algo_fail;
                debug_print(srtp_mod_cipher, "random test case %d failed",
                            case_num);
                debug_print(srtp_mod_cipher, "(failure at byte %u)", k);
            }
        }
        if (status) {
            srtp_cipher_dealloc(c);
            return srtp_err_status_algo_fail;
        }
    }

    status = srtp_cipher_dealloc(c);
    if (status) {
        return status;
    }

    return srtp_err_status_ok;
}

/*
 * srtp_cipher_type_self_test(ct) performs srtp_cipher_type_test on ct's
 * internal list of test data.
 */
srtp_err_status_t srtp_cipher_type_self_test(const srtp_cipher_type_t *ct)
{
    return srtp_cipher_type_test(ct, ct->test_data);
}

/*
 * cipher_bits_per_second(c, l, t) computes (an estimate of) the
 * number of bits that a cipher implementation can encrypt in a second
 *
 * c is a cipher (which MUST be allocated and initialized already), l
 * is the length in octets of the test data to be encrypted, and t is
 * the number of trials
 *
 * if an error is encountered, the value 0 is returned
 */
uint64_t srtp_cipher_bits_per_second(srtp_cipher_t *c,
                                     int octets_in_buffer,
                                     int num_trials)
{
    int i;
    v128_t nonce;
    clock_t timer;
    unsigned char *enc_buf;
    unsigned int len = octets_in_buffer;

    enc_buf = (unsigned char *)srtp_crypto_alloc(octets_in_buffer);
    if (enc_buf == NULL) {
        return 0; /* indicate bad parameters by returning null */
    }
    /* time repeated trials */
    v128_set_to_zero(&nonce);
    timer = clock();
    for (i = 0; i < num_trials; i++, nonce.v32[3] = i) {
        if (srtp_cipher_set_iv(c, (uint8_t *)&nonce, srtp_direction_encrypt) !=
            srtp_err_status_ok) {
            srtp_crypto_free(enc_buf);
            return 0;
        }
        if (srtp_cipher_encrypt(c, enc_buf, &len) != srtp_err_status_ok) {
            srtp_crypto_free(enc_buf);
            return 0;
        }
    }
    timer = clock() - timer;

    srtp_crypto_free(enc_buf);

    if (timer == 0) {
        /* Too fast! */
        return 0;
    }

    return (uint64_t)CLOCKS_PER_SEC * num_trials * 8 * octets_in_buffer / timer;
}
