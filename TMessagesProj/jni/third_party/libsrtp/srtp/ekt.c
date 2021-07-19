/*
 * ekt.c
 *
 * Encrypted Key Transport for SRTP
 *
 * David McGrew
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

#include "srtp_priv.h"
#include "err.h"
#include "ekt.h"

extern srtp_debug_module_t mod_srtp;

/*
 *  The EKT Authentication Tag format.
 *
 *    0                   1                   2                   3
 *    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *   :                   Base Authentication Tag                     :
 *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *   :                     Encrypted Master Key                      :
 *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *   |                       Rollover Counter                        |
 *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *   |    Initial Sequence Number    |   Security Parameter Index    |
 *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 */

#define EKT_OCTETS_AFTER_BASE_TAG 24
#define EKT_OCTETS_AFTER_EMK 8
#define EKT_OCTETS_AFTER_ROC 4
#define EKT_SPI_LEN 2

unsigned srtp_ekt_octets_after_base_tag(srtp_ekt_stream_t ekt)
{
    /*
     * if the pointer ekt is NULL, then EKT is not in effect, so we
     * indicate this by returning zero
     */
    if (!ekt)
        return 0;

    switch (ekt->data->ekt_cipher_type) {
    case SRTP_EKT_CIPHER_AES_128_ECB:
        return 16 + EKT_OCTETS_AFTER_EMK;
        break;
    default:
        break;
    }
    return 0;
}

static inline srtp_ekt_spi_t srtcp_packet_get_ekt_spi(
    const uint8_t *packet_start,
    unsigned pkt_octet_len)
{
    const uint8_t *spi_location;

    spi_location = packet_start + (pkt_octet_len - EKT_SPI_LEN);

    return *((const srtp_ekt_spi_t *)spi_location);
}

static inline uint32_t srtcp_packet_get_ekt_roc(const uint8_t *packet_start,
                                                unsigned pkt_octet_len)
{
    const uint8_t *roc_location;

    roc_location = packet_start + (pkt_octet_len - EKT_OCTETS_AFTER_ROC);

    return *((const uint32_t *)roc_location);
}

static inline const uint8_t *srtcp_packet_get_emk_location(
    const uint8_t *packet_start,
    unsigned pkt_octet_len)
{
    const uint8_t *location;

    location = packet_start + (pkt_octet_len - EKT_OCTETS_AFTER_BASE_TAG);

    return location;
}

srtp_err_status_t srtp_ekt_alloc(srtp_ekt_stream_t *stream_data,
                                 srtp_ekt_policy_t policy)
{
    /*
     * if the policy pointer is NULL, then EKT is not in use
     * so we just set the EKT stream data pointer to NULL
     */
    if (!policy) {
        *stream_data = NULL;
        return srtp_err_status_ok;
    }

    /* TODO */
    *stream_data = NULL;

    return srtp_err_status_ok;
}

srtp_err_status_t srtp_ekt_stream_init_from_policy(
    srtp_ekt_stream_t stream_data,
    srtp_ekt_policy_t policy)
{
    if (!stream_data)
        return srtp_err_status_ok;

    return srtp_err_status_ok;
}

void aes_decrypt_with_raw_key(void *ciphertext, const void *key, int key_len)
{
#ifndef OPENSSL
    // FIXME: need to get this working through the crypto module interface
    srtp_aes_expanded_key_t expanded_key;

    srtp_aes_expand_decryption_key(key, key_len, &expanded_key);
    srtp_aes_decrypt(ciphertext, &expanded_key);
#endif
}

/*
 * The function srtp_stream_init_from_ekt() initializes a stream using
 * the EKT data from an SRTCP trailer.
 */

srtp_err_status_t srtp_stream_init_from_ekt(srtp_stream_t stream,
                                            const void *srtcp_hdr,
                                            unsigned pkt_octet_len)
{
    srtp_err_status_t err;
    const uint8_t *master_key;
    srtp_policy_t srtp_policy;
    uint32_t roc;

    /*
     * NOTE: at present, we only support a single ekt_policy at a time.
     */
    if (stream->ekt->data->spi !=
        srtcp_packet_get_ekt_spi(srtcp_hdr, pkt_octet_len))
        return srtp_err_status_no_ctx;

    if (stream->ekt->data->ekt_cipher_type != SRTP_EKT_CIPHER_AES_128_ECB)
        return srtp_err_status_bad_param;

    /* decrypt the Encrypted Master Key field */
    master_key = srtcp_packet_get_emk_location(srtcp_hdr, pkt_octet_len);
    /* FIX!? This decrypts the master key in-place, and never uses it */
    /* FIX!? It's also passing to ekt_dec_key (which is an aes_expanded_key_t)
     * to a function which expects a raw (unexpanded) key */
    aes_decrypt_with_raw_key((void *)master_key,
                             &stream->ekt->data->ekt_dec_key, 16);

    /* set the SRTP ROC */
    roc = srtcp_packet_get_ekt_roc(srtcp_hdr, pkt_octet_len);
    err = srtp_rdbx_set_roc(&stream->rtp_rdbx, roc);
    if (err)
        return err;

    err = srtp_stream_init(stream, &srtp_policy);
    if (err)
        return err;

    return srtp_err_status_ok;
}

void srtp_ekt_write_data(srtp_ekt_stream_t ekt,
                         uint8_t *base_tag,
                         unsigned base_tag_len,
                         int *packet_len,
                         srtp_xtd_seq_num_t pkt_index)
{
    uint32_t roc;
    uint16_t isn;
    unsigned emk_len;
    uint8_t *packet;

    /* if the pointer ekt is NULL, then EKT is not in effect */
    if (!ekt) {
        debug_print(mod_srtp, "EKT not in use", NULL);
        return;
    }

    /* write zeros into the location of the base tag */
    octet_string_set_to_zero(base_tag, base_tag_len);
    packet = base_tag + base_tag_len;

    /* copy encrypted master key into packet */
    emk_len = srtp_ekt_octets_after_base_tag(ekt);
    memcpy(packet, ekt->encrypted_master_key, emk_len);
    debug_print(mod_srtp, "writing EKT EMK: %s,",
                srtp_octet_string_hex_string(packet, emk_len));
    packet += emk_len;

    /* copy ROC into packet */
    roc = (uint32_t)(pkt_index >> 16);
    *((uint32_t *)packet) = be32_to_cpu(roc);
    debug_print(mod_srtp, "writing EKT ROC: %s,",
                srtp_octet_string_hex_string(packet, sizeof(roc)));
    packet += sizeof(roc);

    /* copy ISN into packet */
    isn = (uint16_t)pkt_index;
    *((uint16_t *)packet) = htons(isn);
    debug_print(mod_srtp, "writing EKT ISN: %s,",
                srtp_octet_string_hex_string(packet, sizeof(isn)));
    packet += sizeof(isn);

    /* copy SPI into packet */
    *((uint16_t *)packet) = htons(ekt->data->spi);
    debug_print(mod_srtp, "writing EKT SPI: %s,",
                srtp_octet_string_hex_string(packet, sizeof(ekt->data->spi)));

    /* increase packet length appropriately */
    *packet_len += EKT_OCTETS_AFTER_EMK + emk_len;
}

/*
 * The function call srtcp_ekt_trailer(ekt, auth_len, auth_tag   )
 *
 * If the pointer ekt is NULL, then the other inputs are unaffected.
 *
 * auth_tag is a pointer to the pointer to the location of the
 * authentication tag in the packet.  If EKT is in effect, then the
 * auth_tag pointer is set to the location
 */

void srtcp_ekt_trailer(srtp_ekt_stream_t ekt,
                       unsigned *auth_len,
                       void **auth_tag,
                       void *tag_copy)
{
    /*
     * if there is no EKT policy, then the other inputs are unaffected
     */
    if (!ekt)
        return;

    /* copy auth_tag into temporary location */
}
