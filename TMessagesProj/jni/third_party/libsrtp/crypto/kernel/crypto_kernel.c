/*
 * crypto_kernel.c
 *
 * header for the cryptographic kernel
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

#include "alloc.h"

#include "crypto_kernel.h"
#include "cipher_types.h"

/* the debug module for the crypto_kernel */

srtp_debug_module_t srtp_mod_crypto_kernel = {
    0,              /* debugging is off by default */
    "crypto kernel" /* printable name for module   */
};

/* crypto_kernel is a global variable, the only one of its datatype */

srtp_crypto_kernel_t crypto_kernel = {
    srtp_crypto_kernel_state_insecure, /* start off in insecure state */
    NULL,                              /* no cipher types yet         */
    NULL,                              /* no auth types yet           */
    NULL                               /* no debug modules yet        */
};

#define MAX_RNG_TRIALS 25

srtp_err_status_t srtp_crypto_kernel_init()
{
    srtp_err_status_t status;

    /* check the security state */
    if (crypto_kernel.state == srtp_crypto_kernel_state_secure) {
        /*
         * we're already in the secure state, but we've been asked to
         * re-initialize, so we just re-run the self-tests and then return
         */
        return srtp_crypto_kernel_status();
    }

    /* initialize error reporting system */
    status = srtp_err_reporting_init();
    if (status) {
        return status;
    }

    /* load debug modules */
    status = srtp_crypto_kernel_load_debug_module(&srtp_mod_crypto_kernel);
    if (status) {
        return status;
    }
    status = srtp_crypto_kernel_load_debug_module(&srtp_mod_auth);
    if (status) {
        return status;
    }
    status = srtp_crypto_kernel_load_debug_module(&srtp_mod_cipher);
    if (status) {
        return status;
    }
    status = srtp_crypto_kernel_load_debug_module(&srtp_mod_stat);
    if (status) {
        return status;
    }
    status = srtp_crypto_kernel_load_debug_module(&srtp_mod_alloc);
    if (status) {
        return status;
    }

    /* load cipher types */
    status = srtp_crypto_kernel_load_cipher_type(&srtp_null_cipher,
                                                 SRTP_NULL_CIPHER);
    if (status) {
        return status;
    }
    status = srtp_crypto_kernel_load_cipher_type(&srtp_aes_icm_128,
                                                 SRTP_AES_ICM_128);
    if (status) {
        return status;
    }
    status = srtp_crypto_kernel_load_cipher_type(&srtp_aes_icm_256,
                                                 SRTP_AES_ICM_256);
    if (status) {
        return status;
    }
    status = srtp_crypto_kernel_load_debug_module(&srtp_mod_aes_icm);
    if (status) {
        return status;
    }
#ifdef OPENSSL
    status = srtp_crypto_kernel_load_cipher_type(&srtp_aes_icm_192,
                                                 SRTP_AES_ICM_192);
    if (status) {
        return status;
    }
    status = srtp_crypto_kernel_load_cipher_type(&srtp_aes_gcm_128_openssl,
                                                 SRTP_AES_GCM_128);
    if (status) {
        return status;
    }
    status = srtp_crypto_kernel_load_cipher_type(&srtp_aes_gcm_256_openssl,
                                                 SRTP_AES_GCM_256);
    if (status) {
        return status;
    }
    status = srtp_crypto_kernel_load_debug_module(&srtp_mod_aes_gcm);
    if (status) {
        return status;
    }
#endif

    /* load auth func types */
    status = srtp_crypto_kernel_load_auth_type(&srtp_null_auth, SRTP_NULL_AUTH);
    if (status) {
        return status;
    }
    status = srtp_crypto_kernel_load_auth_type(&srtp_hmac, SRTP_HMAC_SHA1);
    if (status) {
        return status;
    }
    status = srtp_crypto_kernel_load_debug_module(&srtp_mod_hmac);
    if (status) {
        return status;
    }

    /* change state to secure */
    crypto_kernel.state = srtp_crypto_kernel_state_secure;

    return srtp_err_status_ok;
}

srtp_err_status_t srtp_crypto_kernel_status()
{
    srtp_err_status_t status;
    srtp_kernel_cipher_type_t *ctype = crypto_kernel.cipher_type_list;
    srtp_kernel_auth_type_t *atype = crypto_kernel.auth_type_list;

    /* for each cipher type, describe and test */
    while (ctype != NULL) {
        srtp_err_report(srtp_err_level_info, "cipher: %s\n",
                        ctype->cipher_type->description);
        srtp_err_report(srtp_err_level_info, "  self-test: ");
        status = srtp_cipher_type_self_test(ctype->cipher_type);
        if (status) {
            srtp_err_report(srtp_err_level_error, "failed with error code %d\n",
                            status);
            exit(status);
        }
        srtp_err_report(srtp_err_level_info, "passed\n");
        ctype = ctype->next;
    }

    /* for each auth type, describe and test */
    while (atype != NULL) {
        srtp_err_report(srtp_err_level_info, "auth func: %s\n",
                        atype->auth_type->description);
        srtp_err_report(srtp_err_level_info, "  self-test: ");
        status = srtp_auth_type_self_test(atype->auth_type);
        if (status) {
            srtp_err_report(srtp_err_level_error, "failed with error code %d\n",
                            status);
            exit(status);
        }
        srtp_err_report(srtp_err_level_info, "passed\n");
        atype = atype->next;
    }

    srtp_crypto_kernel_list_debug_modules();

    return srtp_err_status_ok;
}

srtp_err_status_t srtp_crypto_kernel_list_debug_modules()
{
    srtp_kernel_debug_module_t *dm = crypto_kernel.debug_module_list;

    /* describe each debug module */
    srtp_err_report(srtp_err_level_info, "debug modules loaded:\n");
    while (dm != NULL) {
        srtp_err_report(srtp_err_level_info, "  %s ", dm->mod->name);
        if (dm->mod->on) {
            srtp_err_report(srtp_err_level_info, "(on)\n");
        } else {
            srtp_err_report(srtp_err_level_info, "(off)\n");
        }
        dm = dm->next;
    }

    return srtp_err_status_ok;
}

srtp_err_status_t srtp_crypto_kernel_shutdown()
{
    /*
     * free dynamic memory used in crypto_kernel at present
     */

    /* walk down cipher type list, freeing memory */
    while (crypto_kernel.cipher_type_list != NULL) {
        srtp_kernel_cipher_type_t *ctype = crypto_kernel.cipher_type_list;
        crypto_kernel.cipher_type_list = ctype->next;
        debug_print(srtp_mod_crypto_kernel, "freeing memory for cipher %s",
                    ctype->cipher_type->description);
        srtp_crypto_free(ctype);
    }

    /* walk down authetication module list, freeing memory */
    while (crypto_kernel.auth_type_list != NULL) {
        srtp_kernel_auth_type_t *atype = crypto_kernel.auth_type_list;
        crypto_kernel.auth_type_list = atype->next;
        debug_print(srtp_mod_crypto_kernel,
                    "freeing memory for authentication %s",
                    atype->auth_type->description);
        srtp_crypto_free(atype);
    }

    /* walk down debug module list, freeing memory */
    while (crypto_kernel.debug_module_list != NULL) {
        srtp_kernel_debug_module_t *kdm = crypto_kernel.debug_module_list;
        crypto_kernel.debug_module_list = kdm->next;
        debug_print(srtp_mod_crypto_kernel,
                    "freeing memory for debug module %s", kdm->mod->name);
        srtp_crypto_free(kdm);
    }

    /* return to insecure state */
    crypto_kernel.state = srtp_crypto_kernel_state_insecure;

    return srtp_err_status_ok;
}

static inline srtp_err_status_t srtp_crypto_kernel_do_load_cipher_type(
    const srtp_cipher_type_t *new_ct,
    srtp_cipher_type_id_t id,
    int replace)
{
    srtp_kernel_cipher_type_t *ctype, *new_ctype;
    srtp_err_status_t status;

    /* defensive coding */
    if (new_ct == NULL) {
        return srtp_err_status_bad_param;
    }

    if (new_ct->id != id) {
        return srtp_err_status_bad_param;
    }

    /* check cipher type by running self-test */
    status = srtp_cipher_type_self_test(new_ct);
    if (status) {
        return status;
    }

    /* walk down list, checking if this type is in the list already  */
    ctype = crypto_kernel.cipher_type_list;
    while (ctype != NULL) {
        if (id == ctype->id) {
            if (!replace) {
                return srtp_err_status_bad_param;
            }
            status =
                srtp_cipher_type_test(new_ct, ctype->cipher_type->test_data);
            if (status) {
                return status;
            }
            new_ctype = ctype;
            break;
        } else if (new_ct == ctype->cipher_type) {
            return srtp_err_status_bad_param;
        }
        ctype = ctype->next;
    }

    /* if not found, put new_ct at the head of the list */
    if (ctype == NULL) {
        /* allocate memory */
        new_ctype = (srtp_kernel_cipher_type_t *)srtp_crypto_alloc(
            sizeof(srtp_kernel_cipher_type_t));
        if (new_ctype == NULL) {
            return srtp_err_status_alloc_fail;
        }
        new_ctype->next = crypto_kernel.cipher_type_list;

        /* set head of list to new cipher type */
        crypto_kernel.cipher_type_list = new_ctype;
    }

    /* set fields */
    new_ctype->cipher_type = new_ct;
    new_ctype->id = id;

    return srtp_err_status_ok;
}

srtp_err_status_t srtp_crypto_kernel_load_cipher_type(
    const srtp_cipher_type_t *new_ct,
    srtp_cipher_type_id_t id)
{
    return srtp_crypto_kernel_do_load_cipher_type(new_ct, id, 0);
}

srtp_err_status_t srtp_replace_cipher_type(const srtp_cipher_type_t *new_ct,
                                           srtp_cipher_type_id_t id)
{
    return srtp_crypto_kernel_do_load_cipher_type(new_ct, id, 1);
}

srtp_err_status_t srtp_crypto_kernel_do_load_auth_type(
    const srtp_auth_type_t *new_at,
    srtp_auth_type_id_t id,
    int replace)
{
    srtp_kernel_auth_type_t *atype, *new_atype;
    srtp_err_status_t status;

    /* defensive coding */
    if (new_at == NULL) {
        return srtp_err_status_bad_param;
    }

    if (new_at->id != id) {
        return srtp_err_status_bad_param;
    }

    /* check auth type by running self-test */
    status = srtp_auth_type_self_test(new_at);
    if (status) {
        return status;
    }

    /* walk down list, checking if this type is in the list already  */
    atype = crypto_kernel.auth_type_list;
    while (atype != NULL) {
        if (id == atype->id) {
            if (!replace) {
                return srtp_err_status_bad_param;
            }
            status = srtp_auth_type_test(new_at, atype->auth_type->test_data);
            if (status) {
                return status;
            }
            new_atype = atype;
            break;
        } else if (new_at == atype->auth_type) {
            return srtp_err_status_bad_param;
        }
        atype = atype->next;
    }

    /* if not found, put new_at at the head of the list */
    if (atype == NULL) {
        /* allocate memory */
        new_atype = (srtp_kernel_auth_type_t *)srtp_crypto_alloc(
            sizeof(srtp_kernel_auth_type_t));
        if (new_atype == NULL) {
            return srtp_err_status_alloc_fail;
        }

        new_atype->next = crypto_kernel.auth_type_list;
        /* set head of list to new auth type */
        crypto_kernel.auth_type_list = new_atype;
    }

    /* set fields */
    new_atype->auth_type = new_at;
    new_atype->id = id;

    return srtp_err_status_ok;
}

srtp_err_status_t srtp_crypto_kernel_load_auth_type(
    const srtp_auth_type_t *new_at,
    srtp_auth_type_id_t id)
{
    return srtp_crypto_kernel_do_load_auth_type(new_at, id, 0);
}

srtp_err_status_t srtp_replace_auth_type(const srtp_auth_type_t *new_at,
                                         srtp_auth_type_id_t id)
{
    return srtp_crypto_kernel_do_load_auth_type(new_at, id, 1);
}

const srtp_cipher_type_t *srtp_crypto_kernel_get_cipher_type(
    srtp_cipher_type_id_t id)
{
    srtp_kernel_cipher_type_t *ctype;

    /* walk down list, looking for id  */
    ctype = crypto_kernel.cipher_type_list;
    while (ctype != NULL) {
        if (id == ctype->id) {
            return ctype->cipher_type;
        }
        ctype = ctype->next;
    }

    /* haven't found the right one, indicate failure by returning NULL */
    return NULL;
}

srtp_err_status_t srtp_crypto_kernel_alloc_cipher(srtp_cipher_type_id_t id,
                                                  srtp_cipher_pointer_t *cp,
                                                  int key_len,
                                                  int tag_len)
{
    const srtp_cipher_type_t *ct;

    /*
     * if the crypto_kernel is not yet initialized, we refuse to allocate
     * any ciphers - this is a bit extra-paranoid
     */
    if (crypto_kernel.state != srtp_crypto_kernel_state_secure) {
        return srtp_err_status_init_fail;
    }

    ct = srtp_crypto_kernel_get_cipher_type(id);
    if (!ct) {
        return srtp_err_status_fail;
    }

    return ((ct)->alloc(cp, key_len, tag_len));
}

const srtp_auth_type_t *srtp_crypto_kernel_get_auth_type(srtp_auth_type_id_t id)
{
    srtp_kernel_auth_type_t *atype;

    /* walk down list, looking for id  */
    atype = crypto_kernel.auth_type_list;
    while (atype != NULL) {
        if (id == atype->id) {
            return atype->auth_type;
        }
        atype = atype->next;
    }

    /* haven't found the right one, indicate failure by returning NULL */
    return NULL;
}

srtp_err_status_t srtp_crypto_kernel_alloc_auth(srtp_auth_type_id_t id,
                                                srtp_auth_pointer_t *ap,
                                                int key_len,
                                                int tag_len)
{
    const srtp_auth_type_t *at;

    /*
     * if the crypto_kernel is not yet initialized, we refuse to allocate
     * any auth functions - this is a bit extra-paranoid
     */
    if (crypto_kernel.state != srtp_crypto_kernel_state_secure) {
        return srtp_err_status_init_fail;
    }

    at = srtp_crypto_kernel_get_auth_type(id);
    if (!at) {
        return srtp_err_status_fail;
    }

    return ((at)->alloc(ap, key_len, tag_len));
}

srtp_err_status_t srtp_crypto_kernel_load_debug_module(
    srtp_debug_module_t *new_dm)
{
    srtp_kernel_debug_module_t *kdm, *new;

    /* defensive coding */
    if (new_dm == NULL || new_dm->name == NULL) {
        return srtp_err_status_bad_param;
    }

    /* walk down list, checking if this type is in the list already  */
    kdm = crypto_kernel.debug_module_list;
    while (kdm != NULL) {
        if (strncmp(new_dm->name, kdm->mod->name, 64) == 0) {
            return srtp_err_status_bad_param;
        }
        kdm = kdm->next;
    }

    /* put new_dm at the head of the list */
    /* allocate memory */
    new = (srtp_kernel_debug_module_t *)srtp_crypto_alloc(
        sizeof(srtp_kernel_debug_module_t));
    if (new == NULL) {
        return srtp_err_status_alloc_fail;
    }

    /* set fields */
    new->mod = new_dm;
    new->next = crypto_kernel.debug_module_list;

    /* set head of list to new cipher type */
    crypto_kernel.debug_module_list = new;

    return srtp_err_status_ok;
}

srtp_err_status_t srtp_crypto_kernel_set_debug_module(const char *name, int on)
{
    srtp_kernel_debug_module_t *kdm;

    /* walk down list, checking if this type is in the list already  */
    kdm = crypto_kernel.debug_module_list;
    while (kdm != NULL) {
        if (strncmp(name, kdm->mod->name, 64) == 0) {
            kdm->mod->on = on;
            return srtp_err_status_ok;
        }
        kdm = kdm->next;
    }

    return srtp_err_status_fail;
}
