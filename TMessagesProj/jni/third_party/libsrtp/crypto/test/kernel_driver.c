/*
 * kernel_driver.c
 *
 * a test driver for the crypto_kernel
 *
 * David A. McGrew
 * Cisco Systems, Inc.
 */
/*
 *
 * Copyright(c) 2001-2017 Cisco Systems, Inc.
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

#include <stdio.h> /* for printf() */
#include "getopt_s.h"
#include "crypto_kernel.h"

void usage(char *prog_name)
{
    printf("usage: %s [ -v ][ -d debug_module ]*\n", prog_name);
    exit(255);
}

int main(int argc, char *argv[])
{
    int q;
    int do_validation = 0;
    srtp_err_status_t status;

    if (argc == 1)
        usage(argv[0]);

    /* initialize kernel - we need to do this before anything else */
    status = srtp_crypto_kernel_init();
    if (status) {
        printf("error: srtp_crypto_kernel init failed\n");
        exit(1);
    }
    printf("srtp_crypto_kernel successfully initalized\n");

    /* process input arguments */
    while (1) {
        q = getopt_s(argc, argv, "vd:");
        if (q == -1)
            break;
        switch (q) {
        case 'v':
            do_validation = 1;
            break;
        case 'd':
            status = srtp_crypto_kernel_set_debug_module(optarg_s, 1);
            if (status) {
                printf("error: set debug module (%s) failed\n", optarg_s);
                exit(1);
            }
            break;
        default:
            usage(argv[0]);
        }
    }

    if (do_validation) {
        printf("checking srtp_crypto_kernel status...\n");
        status = srtp_crypto_kernel_status();
        if (status) {
            printf("failed\n");
            exit(1);
        }
        printf("srtp_crypto_kernel passed self-tests\n");
    }

    status = srtp_crypto_kernel_shutdown();
    if (status) {
        printf("error: srtp_crypto_kernel shutdown failed\n");
        exit(1);
    }
    printf("srtp_crypto_kernel successfully shut down\n");

    return 0;
}

/*
 * crypto_kernel_cipher_test() is a test of the cipher interface
 * of the crypto_kernel
 */

srtp_err_status_t crypto_kernel_cipher_test(void)
{
    /* not implemented yet! */

    return srtp_err_status_ok;
}
