/*
 * cipher_driver.c
 *
 * A driver for the generic cipher type
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

#include <stdio.h>  /* for printf() */
#include <stdlib.h> /* for rand() */
#include "getopt_s.h"
#include "cipher.h"
#ifdef OPENSSL
#include "aes_icm_ossl.h"
#include "aes_gcm_ossl.h"
#else
#include "aes_icm.h"
#endif

#define PRINT_DEBUG 0

void cipher_driver_test_throughput(srtp_cipher_t *c);

srtp_err_status_t cipher_driver_self_test(srtp_cipher_type_t *ct);

/*
 * cipher_driver_test_buffering(ct) tests the cipher's output
 * buffering for correctness by checking the consistency of succesive
 * calls
 */

srtp_err_status_t cipher_driver_test_buffering(srtp_cipher_t *c);

/*
 * functions for testing cipher cache thrash
 */
srtp_err_status_t cipher_driver_test_array_throughput(srtp_cipher_type_t *ct,
                                                      int klen,
                                                      int num_cipher);

void cipher_array_test_throughput(srtp_cipher_t *ca[], int num_cipher);

uint64_t cipher_array_bits_per_second(srtp_cipher_t *cipher_array[],
                                      int num_cipher,
                                      unsigned octets_in_buffer,
                                      int num_trials);

srtp_err_status_t cipher_array_delete(srtp_cipher_t *cipher_array[],
                                      int num_cipher);

srtp_err_status_t cipher_array_alloc_init(srtp_cipher_t ***cipher_array,
                                          int num_ciphers,
                                          srtp_cipher_type_t *ctype,
                                          int klen);

void usage(char *prog_name)
{
    printf("usage: %s [ -t | -v | -a ]\n", prog_name);
    exit(255);
}

void check_status(srtp_err_status_t s)
{
    if (s) {
        printf("error (code %d)\n", s);
        exit(s);
    }
    return;
}

/*
 * null_cipher and srtp_aes_icm are the cipher meta-objects
 * defined in the files in crypto/cipher subdirectory.  these are
 * declared external so that we can use these cipher types here
 */

extern srtp_cipher_type_t srtp_null_cipher;
extern srtp_cipher_type_t srtp_aes_icm_128;
extern srtp_cipher_type_t srtp_aes_icm_256;
#ifdef OPENSSL
extern srtp_cipher_type_t srtp_aes_icm_192;
extern srtp_cipher_type_t srtp_aes_gcm_128_openssl;
extern srtp_cipher_type_t srtp_aes_gcm_256_openssl;
#endif

int main(int argc, char *argv[])
{
    srtp_cipher_t *c = NULL;
    srtp_err_status_t status;
    /* clang-format off */
    unsigned char test_key[48] = {
        0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
        0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
        0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
        0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f,
        0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
        0x28, 0x29, 0x2a, 0x2b, 0x2c, 0x2d, 0x2e, 0x2f,
    };
    /* clang-format on */
    int q;
    unsigned do_timing_test = 0;
    unsigned do_validation = 0;
    unsigned do_array_timing_test = 0;

    /* process input arguments */
    while (1) {
        q = getopt_s(argc, argv, "tva");
        if (q == -1)
            break;
        switch (q) {
        case 't':
            do_timing_test = 1;
            break;
        case 'v':
            do_validation = 1;
            break;
        case 'a':
            do_array_timing_test = 1;
            break;
        default:
            usage(argv[0]);
        }
    }

    printf("cipher test driver\n"
           "David A. McGrew\n"
           "Cisco Systems, Inc.\n");

    if (!do_validation && !do_timing_test && !do_array_timing_test)
        usage(argv[0]);

    /* arry timing (cache thrash) test */
    if (do_array_timing_test) {
        int max_num_cipher = 1 << 16; /* number of ciphers in cipher_array */
        int num_cipher;

        for (num_cipher = 1; num_cipher < max_num_cipher; num_cipher *= 8)
            cipher_driver_test_array_throughput(&srtp_null_cipher, 0,
                                                num_cipher);

        for (num_cipher = 1; num_cipher < max_num_cipher; num_cipher *= 8)
            cipher_driver_test_array_throughput(
                &srtp_aes_icm_128, SRTP_AES_ICM_128_KEY_LEN_WSALT, num_cipher);

        for (num_cipher = 1; num_cipher < max_num_cipher; num_cipher *= 8)
            cipher_driver_test_array_throughput(
                &srtp_aes_icm_256, SRTP_AES_ICM_256_KEY_LEN_WSALT, num_cipher);

#ifdef OPENSSL
        for (num_cipher = 1; num_cipher < max_num_cipher; num_cipher *= 8)
            cipher_driver_test_array_throughput(
                &srtp_aes_icm_192, SRTP_AES_ICM_192_KEY_LEN_WSALT, num_cipher);

        for (num_cipher = 1; num_cipher < max_num_cipher; num_cipher *= 8) {
            cipher_driver_test_array_throughput(&srtp_aes_gcm_128_openssl,
                                                SRTP_AES_GCM_128_KEY_LEN_WSALT,
                                                num_cipher);
        }

        for (num_cipher = 1; num_cipher < max_num_cipher; num_cipher *= 8) {
            cipher_driver_test_array_throughput(&srtp_aes_gcm_256_openssl,
                                                SRTP_AES_GCM_256_KEY_LEN_WSALT,
                                                num_cipher);
        }
#endif
    }

    if (do_validation) {
        cipher_driver_self_test(&srtp_null_cipher);
        cipher_driver_self_test(&srtp_aes_icm_128);
        cipher_driver_self_test(&srtp_aes_icm_256);
#ifdef OPENSSL
        cipher_driver_self_test(&srtp_aes_icm_192);
        cipher_driver_self_test(&srtp_aes_gcm_128_openssl);
        cipher_driver_self_test(&srtp_aes_gcm_256_openssl);
#endif
    }

    /* do timing and/or buffer_test on srtp_null_cipher */
    status = srtp_cipher_type_alloc(&srtp_null_cipher, &c, 0, 0);
    check_status(status);

    status = srtp_cipher_init(c, NULL);
    check_status(status);

    if (do_timing_test)
        cipher_driver_test_throughput(c);
    if (do_validation) {
        status = cipher_driver_test_buffering(c);
        check_status(status);
    }
    status = srtp_cipher_dealloc(c);
    check_status(status);

    /* run the throughput test on the aes_icm cipher (128-bit key) */
    status = srtp_cipher_type_alloc(&srtp_aes_icm_128, &c,
                                    SRTP_AES_ICM_128_KEY_LEN_WSALT, 0);
    if (status) {
        fprintf(stderr, "error: can't allocate cipher\n");
        exit(status);
    }

    status = srtp_cipher_init(c, test_key);
    check_status(status);

    if (do_timing_test)
        cipher_driver_test_throughput(c);

    if (do_validation) {
        status = cipher_driver_test_buffering(c);
        check_status(status);
    }

    status = srtp_cipher_dealloc(c);
    check_status(status);

    /* repeat the tests with 256-bit keys */
    status = srtp_cipher_type_alloc(&srtp_aes_icm_256, &c,
                                    SRTP_AES_ICM_256_KEY_LEN_WSALT, 0);
    if (status) {
        fprintf(stderr, "error: can't allocate cipher\n");
        exit(status);
    }

    status = srtp_cipher_init(c, test_key);
    check_status(status);

    if (do_timing_test)
        cipher_driver_test_throughput(c);

    if (do_validation) {
        status = cipher_driver_test_buffering(c);
        check_status(status);
    }

    status = srtp_cipher_dealloc(c);
    check_status(status);

#ifdef OPENSSL
    /* run the throughput test on the aes_gcm_128_openssl cipher */
    status = srtp_cipher_type_alloc(&srtp_aes_gcm_128_openssl, &c,
                                    SRTP_AES_GCM_128_KEY_LEN_WSALT, 8);
    if (status) {
        fprintf(stderr, "error: can't allocate GCM 128 cipher\n");
        exit(status);
    }
    status = srtp_cipher_init(c, test_key);
    check_status(status);
    if (do_timing_test) {
        cipher_driver_test_throughput(c);
    }

    if (do_validation) {
        status = cipher_driver_test_buffering(c);
        check_status(status);
    }
    status = srtp_cipher_dealloc(c);
    check_status(status);

    /* run the throughput test on the aes_gcm_256_openssl cipher */
    status = srtp_cipher_type_alloc(&srtp_aes_gcm_256_openssl, &c,
                                    SRTP_AES_GCM_256_KEY_LEN_WSALT, 16);
    if (status) {
        fprintf(stderr, "error: can't allocate GCM 256 cipher\n");
        exit(status);
    }
    status = srtp_cipher_init(c, test_key);
    check_status(status);
    if (do_timing_test) {
        cipher_driver_test_throughput(c);
    }

    if (do_validation) {
        status = cipher_driver_test_buffering(c);
        check_status(status);
    }
    status = srtp_cipher_dealloc(c);
    check_status(status);
#endif

    return 0;
}

void cipher_driver_test_throughput(srtp_cipher_t *c)
{
    int i;
    int min_enc_len = 32;
    int max_enc_len = 2048; /* should be a power of two */
    int num_trials = 1000000;

    printf("timing %s throughput, key length %d:\n", c->type->description,
           c->key_len);
    fflush(stdout);
    for (i = min_enc_len; i <= max_enc_len; i = i * 2)
        printf("msg len: %d\tgigabits per second: %f\n", i,
               srtp_cipher_bits_per_second(c, i, num_trials) / 1e9);
}

srtp_err_status_t cipher_driver_self_test(srtp_cipher_type_t *ct)
{
    srtp_err_status_t status;

    printf("running cipher self-test for %s...", ct->description);
    status = srtp_cipher_type_self_test(ct);
    if (status) {
        printf("failed with error code %d\n", status);
        exit(status);
    }
    printf("passed\n");

    return srtp_err_status_ok;
}

/*
 * cipher_driver_test_buffering(ct) tests the cipher's output
 * buffering for correctness by checking the consistency of succesive
 * calls
 */

#define INITIAL_BUFLEN 1024
srtp_err_status_t cipher_driver_test_buffering(srtp_cipher_t *c)
{
    int i, j, num_trials = 1000;
    unsigned len, buflen = INITIAL_BUFLEN;
    uint8_t buffer0[INITIAL_BUFLEN], buffer1[INITIAL_BUFLEN], *current, *end;
    uint8_t idx[16] = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x12, 0x34 };
    srtp_err_status_t status;

    printf("testing output buffering for cipher %s...", c->type->description);

    for (i = 0; i < num_trials; i++) {
        /* set buffers to zero */
        for (j = 0; j < (int)buflen; j++) {
            buffer0[j] = buffer1[j] = 0;
        }

        /* initialize cipher  */
        status = srtp_cipher_set_iv(c, (uint8_t *)idx, srtp_direction_encrypt);
        if (status)
            return status;

        /* generate 'reference' value by encrypting all at once */
        status = srtp_cipher_encrypt(c, buffer0, &buflen);
        if (status)
            return status;

        /* re-initialize cipher */
        status = srtp_cipher_set_iv(c, (uint8_t *)idx, srtp_direction_encrypt);
        if (status)
            return status;

        /* now loop over short lengths until buffer1 is encrypted */
        current = buffer1;
        end = buffer1 + buflen;
        while (current < end) {
            /* choose a short length */
            len = rand() & 0x01f;

            /* make sure that len doesn't cause us to overreach the buffer */
            if (current + len > end)
                len = end - current;

            status = srtp_cipher_encrypt(c, current, &len);
            if (status)
                return status;

            /* advance pointer into buffer1 to reflect encryption */
            current += len;

            /* if buffer1 is all encrypted, break out of loop */
            if (current == end)
                break;
        }

        /* compare buffers */
        for (j = 0; j < (int)buflen; j++) {
            if (buffer0[j] != buffer1[j]) {
#if PRINT_DEBUG
                printf("test case %d failed at byte %d\n", i, j);
                printf("computed: %s\n",
                       octet_string_hex_string(buffer1, buflen));
                printf("expected: %s\n",
                       octet_string_hex_string(buffer0, buflen));
#endif
                return srtp_err_status_algo_fail;
            }
        }
    }

    printf("passed\n");

    return srtp_err_status_ok;
}

/*
 * The function cipher_test_throughput_array() tests the effect of CPU
 * cache thrash on cipher throughput.
 *
 * cipher_array_alloc_init(ctype, array, num_ciphers) creates an array
 * of srtp_cipher_t of type ctype
 */

srtp_err_status_t cipher_array_alloc_init(srtp_cipher_t ***ca,
                                          int num_ciphers,
                                          srtp_cipher_type_t *ctype,
                                          int klen)
{
    int i, j;
    srtp_err_status_t status;
    uint8_t *key;
    srtp_cipher_t **cipher_array;
    /* pad klen allocation, to handle aes_icm reading 16 bytes for the
       14-byte salt */
    int klen_pad = ((klen + 15) >> 4) << 4;

    /* allocate array of pointers to ciphers */
    cipher_array = (srtp_cipher_t **)srtp_crypto_alloc(sizeof(srtp_cipher_t *) *
                                                       num_ciphers);
    if (cipher_array == NULL)
        return srtp_err_status_alloc_fail;

    /* set ca to location of cipher_array */
    *ca = cipher_array;

    /* allocate key */
    key = srtp_crypto_alloc(klen_pad);
    if (key == NULL) {
        srtp_crypto_free(cipher_array);
        return srtp_err_status_alloc_fail;
    }

    /* allocate and initialize an array of ciphers */
    for (i = 0; i < num_ciphers; i++) {
        /* allocate cipher */
        status = srtp_cipher_type_alloc(ctype, cipher_array, klen, 16);
        if (status)
            return status;

        /* generate random key and initialize cipher */
        for (j = 0; j < klen; j++)
            key[j] = (uint8_t)rand();
        for (; j < klen_pad; j++)
            key[j] = 0;
        status = srtp_cipher_init(*cipher_array, key);
        if (status)
            return status;

        /*     printf("%dth cipher is at %p\n", i, *cipher_array); */
        /*     printf("%dth cipher description: %s\n", i,  */
        /* 	   (*cipher_array)->type->description); */

        /* advance cipher array pointer */
        cipher_array++;
    }

    srtp_crypto_free(key);

    return srtp_err_status_ok;
}

srtp_err_status_t cipher_array_delete(srtp_cipher_t *cipher_array[],
                                      int num_cipher)
{
    int i;

    for (i = 0; i < num_cipher; i++) {
        srtp_cipher_dealloc(cipher_array[i]);
    }

    srtp_crypto_free(cipher_array);

    return srtp_err_status_ok;
}

/*
 * cipher_array_bits_per_second(c, l, t) computes (an estimate of) the
 * number of bits that a cipher implementation can encrypt in a second
 * when distinct keys are used to encrypt distinct messages
 *
 * c is a cipher (which MUST be allocated an initialized already), l
 * is the length in octets of the test data to be encrypted, and t is
 * the number of trials
 *
 * if an error is encountered, the value 0 is returned
 */

uint64_t cipher_array_bits_per_second(srtp_cipher_t *cipher_array[],
                                      int num_cipher,
                                      unsigned octets_in_buffer,
                                      int num_trials)
{
    int i;
    v128_t nonce;
    clock_t timer;
    unsigned char *enc_buf;
    int cipher_index = rand() % num_cipher;

    /* Over-alloc, for NIST CBC padding */
    enc_buf = srtp_crypto_alloc(octets_in_buffer + 17);
    if (enc_buf == NULL)
        return 0; /* indicate bad parameters by returning null */

    /* time repeated trials */
    v128_set_to_zero(&nonce);
    timer = clock();
    for (i = 0; i < num_trials; i++, nonce.v32[3] = i) {
        /* length parameter to srtp_cipher_encrypt is in/out -- out is total,
         * padded
         * length -- so reset it each time. */
        unsigned octets_to_encrypt = octets_in_buffer;

        /* encrypt buffer with cipher */
        srtp_cipher_set_iv(cipher_array[cipher_index], (uint8_t *)&nonce,
                           srtp_direction_encrypt);
        srtp_cipher_encrypt(cipher_array[cipher_index], enc_buf,
                            &octets_to_encrypt);

        /* choose a cipher at random from the array*/
        cipher_index = (*((uint32_t *)enc_buf)) % num_cipher;
    }
    timer = clock() - timer;

    srtp_crypto_free(enc_buf);

    if (timer == 0) {
        /* Too fast! */
        return 0;
    }

    return (uint64_t)CLOCKS_PER_SEC * num_trials * 8 * octets_in_buffer / timer;
}

void cipher_array_test_throughput(srtp_cipher_t *ca[], int num_cipher)
{
    int i;
    int min_enc_len = 16;
    int max_enc_len = 2048; /* should be a power of two */
    int num_trials = 1000000;

    printf("timing %s throughput with key length %d, array size %d:\n",
           (ca[0])->type->description, (ca[0])->key_len, num_cipher);
    fflush(stdout);
    for (i = min_enc_len; i <= max_enc_len; i = i * 4)
        printf("msg len: %d\tgigabits per second: %f\n", i,
               cipher_array_bits_per_second(ca, num_cipher, i, num_trials) /
                   1e9);
}

srtp_err_status_t cipher_driver_test_array_throughput(srtp_cipher_type_t *ct,
                                                      int klen,
                                                      int num_cipher)
{
    srtp_cipher_t **ca = NULL;
    srtp_err_status_t status;

    status = cipher_array_alloc_init(&ca, num_cipher, ct, klen);
    if (status) {
        printf("error: cipher_array_alloc_init() failed with error code %d\n",
               status);
        return status;
    }

    cipher_array_test_throughput(ca, num_cipher);

    cipher_array_delete(ca, num_cipher);

    return srtp_err_status_ok;
}
