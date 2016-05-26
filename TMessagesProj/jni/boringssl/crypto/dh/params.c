/* ====================================================================
 * Copyright (c) 2011 The OpenSSL Project.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. All advertising materials mentioning features or use of this
 *    software must display the following acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit. (http://www.OpenSSL.org/)"
 *
 * 4. The names "OpenSSL Toolkit" and "OpenSSL Project" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    licensing@OpenSSL.org.
 *
 * 5. Products derived from this software may not be called "OpenSSL"
 *    nor may "OpenSSL" appear in their names without prior written
 *    permission of the OpenSSL Project.
 *
 * 6. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit (http://www.OpenSSL.org/)"
 *
 * THIS SOFTWARE IS PROVIDED BY THE OpenSSL PROJECT ``AS IS'' AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE OpenSSL PROJECT OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 *
 * This product includes cryptographic software written by Eric Young
 * (eay@cryptsoft.com).  This product includes software written by Tim
 * Hudson (tjh@cryptsoft.com). */

#include <openssl/dh.h>

#include <openssl/bn.h>

#include "internal.h"


#if BN_BITS2 == 32
#define TOBN(lo, hi) lo, hi
#elif BN_BITS2 == 64
#define TOBN(lo, hi) ((BN_ULONG)hi << 32 | lo)
#else
#error "unsupported BN_BITS2"
#endif

static const BN_ULONG dh1024_160_p[] = {
    TOBN(0x2E4A4371, 0xDF1FB2BC), TOBN(0x6D4DA708, 0xE68CFDA7),
    TOBN(0x365C1A65, 0x45BF37DF), TOBN(0x0DC8B4BD, 0xA151AF5F),
    TOBN(0xF55BCCC0, 0xFAA31A4F), TOBN(0xE5644738, 0x4EFFD6FA),
    TOBN(0x219A7372, 0x98488E9C), TOBN(0x90C4BD70, 0xACCBDD7D),
    TOBN(0xD49B83BF, 0x24975C3C), TOBN(0xA9061123, 0x13ECB4AE),
    TOBN(0x2EE652C0, 0x9838EF1E), TOBN(0x75A23D18, 0x6073E286),
    TOBN(0x52D23B61, 0x9A6A9DCA), TOBN(0xFB06A3C6, 0x52C99FBC),
    TOBN(0xAE5D54EC, 0xDE92DE5E), TOBN(0xA080E01D, 0xB10B8F96),
};
static const BN_ULONG dh1024_160_g[] = {
    TOBN(0x22B3B2E5, 0x855E6EEB), TOBN(0xF97C2A24, 0x858F4DCE),
    TOBN(0x18D08BC8, 0x2D779D59), TOBN(0x8E73AFA3, 0xD662A4D1),
    TOBN(0x69B6A28A, 0x1DBF0A01), TOBN(0x7A091F53, 0xA6A24C08),
    TOBN(0x63F80A76, 0x909D0D22), TOBN(0xB9A92EE1, 0xD7FBD7D3),
    TOBN(0x9E2749F4, 0x5E91547F), TOBN(0xB01B886A, 0x160217B4),
    TOBN(0x5504F213, 0x777E690F), TOBN(0x5C41564B, 0x266FEA1E),
    TOBN(0x14266D31, 0xD6406CFF), TOBN(0x58AC507F, 0xF8104DD2),
    TOBN(0xEFB99905, 0x6765A442), TOBN(0xC3FD3412, 0xA4D1CBD5),
};
static const BN_ULONG dh1024_160_q[] = {
    TOBN(0x49462353, 0x64B7CB9D), TOBN(0x8ABA4E7D, 0x81A8DF27), 0xF518AA87,
};

static const BN_ULONG dh2048_224_p[] = {
    TOBN(0x0C10E64F, 0x0AC4DFFE), TOBN(0x4E71B81C, 0xCF9DE538),
    TOBN(0xFFA31F71, 0x7EF363E2), TOBN(0x6B8E75B9, 0xE3FB73C1),
    TOBN(0x4BA80A29, 0xC9B53DCF), TOBN(0x16E79763, 0x23F10B0E),
    TOBN(0x13042E9B, 0xC52172E4), TOBN(0xC928B2B9, 0xBE60E69C),
    TOBN(0xB9E587E8, 0x80CD86A1), TOBN(0x98C641A4, 0x315D75E1),
    TOBN(0x44328387, 0xCDF93ACC), TOBN(0xDC0A486D, 0x15987D9A),
    TOBN(0x1FD5A074, 0x7310F712), TOBN(0xDE31EFDC, 0x278273C7),
    TOBN(0x415D9330, 0x1602E714), TOBN(0xBC8985DB, 0x81286130),
    TOBN(0x70918836, 0xB3BF8A31), TOBN(0xB9C49708, 0x6A00E0A0),
    TOBN(0x8BBC27BE, 0xC6BA0B2C), TOBN(0xED34DBF6, 0xC9F98D11),
    TOBN(0xB6C12207, 0x7AD5B7D0), TOBN(0x55B7394B, 0xD91E8FEF),
    TOBN(0xEFDA4DF8, 0x9037C9ED), TOBN(0xAD6AC212, 0x6D3F8152),
    TOBN(0x1274A0A6, 0x1DE6B85A), TOBN(0x309C180E, 0xEB3D688A),
    TOBN(0x7BA1DF15, 0xAF9A3C40), TOBN(0xF95A56DB, 0xE6FA141D),
    TOBN(0xB61D0A75, 0xB54B1597), TOBN(0x683B9FD1, 0xA20D64E5),
    TOBN(0x9559C51F, 0xD660FAA7), TOBN(0x9123A9D0, 0xAD107E1E),
};

static const BN_ULONG dh2048_224_g[] = {
    TOBN(0x191F2BFA, 0x84B890D3), TOBN(0x2A7065B3, 0x81BC087F),
    TOBN(0xF6EC0179, 0x19C418E1), TOBN(0x71CFFF4C, 0x7B5A0F1C),
    TOBN(0x9B6AA4BD, 0xEDFE72FE), TOBN(0x94B30269, 0x81E1BCFE),
    TOBN(0x8D6C0191, 0x566AFBB4), TOBN(0x409D13CD, 0xB539CCE3),
    TOBN(0x5F2FF381, 0x6AA21E7F), TOBN(0x770589EF, 0xD9E263E4),
    TOBN(0xD19963DD, 0x10E183ED), TOBN(0x150B8EEB, 0xB70A8137),
    TOBN(0x28C8F8AC, 0x051AE3D4), TOBN(0x0C1AB15B, 0xBB77A86F),
    TOBN(0x16A330EF, 0x6E3025E3), TOBN(0xD6F83456, 0x19529A45),
    TOBN(0x118E98D1, 0xF180EB34), TOBN(0x50717CBE, 0xB5F6C6B2),
    TOBN(0xDA7460CD, 0x09939D54), TOBN(0x22EA1ED4, 0xE2471504),
    TOBN(0x521BC98A, 0xB8A762D0), TOBN(0x5AC1348B, 0xF4D02727),
    TOBN(0x1999024A, 0xC1766910), TOBN(0xA8D66AD7, 0xBE5E9001),
    TOBN(0x620A8652, 0xC57DB17C), TOBN(0x00C29F52, 0xAB739D77),
    TOBN(0xA70C4AFA, 0xDD921F01), TOBN(0x10B9A6F0, 0xA6824A4E),
    TOBN(0xCFE4FFE3, 0x74866A08), TOBN(0x89998CAF, 0x6CDEBE7B),
    TOBN(0x8FFDAC50, 0x9DF30B5C), TOBN(0x4F2D9AE3, 0xAC4032EF),
};

static const BN_ULONG dh2048_224_q[] = {
    TOBN(0xB36371EB, 0xBF389A99), TOBN(0x4738CEBC, 0x1F80535A),
    TOBN(0x99717710, 0xC58D93FE), 0x801C0D34,
};

static const BN_ULONG dh2048_256_p[] = {
    TOBN(0x1E1A1597, 0xDB094AE9), TOBN(0xD7EF09CA, 0x693877FA),
    TOBN(0x6E11715F, 0x6116D227), TOBN(0xC198AF12, 0xA4B54330),
    TOBN(0xD7014103, 0x75F26375), TOBN(0x54E710C3, 0xC3A3960A),
    TOBN(0xBD0BE621, 0xDED4010A), TOBN(0x89962856, 0xC0B857F6),
    TOBN(0x71506026, 0xB3CA3F79), TOBN(0xE6B486F6, 0x1CCACB83),
    TOBN(0x14056425, 0x67E144E5), TOBN(0xA41825D9, 0xF6A167B5),
    TOBN(0x96524D8E, 0x3AD83477), TOBN(0x51BFA4AB, 0xF13C6D9A),
    TOBN(0x35488A0E, 0x2D525267), TOBN(0xCAA6B790, 0xB63ACAE1),
    TOBN(0x81B23F76, 0x4FDB70C5), TOBN(0x12307F5C, 0xBC39A0BF),
    TOBN(0xB1E59BB8, 0xB941F54E), TOBN(0xD45F9088, 0x6C5BFC11),
    TOBN(0x4275BF7B, 0x22E0B1EF), TOBN(0x5B4758C0, 0x91F9E672),
    TOBN(0x6BCF67ED, 0x5A8A9D30), TOBN(0x97517ABD, 0x209E0C64),
    TOBN(0x830E9A7C, 0x3BF4296D), TOBN(0x34096FAA, 0x16C3D911),
    TOBN(0x61B2AA30, 0xFAF7DF45), TOBN(0xD61957D4, 0xE00DF8F1),
    TOBN(0x435E3B00, 0x5D2CEED4), TOBN(0x660DD0F2, 0x8CEEF608),
    TOBN(0x65195999, 0xFFBBD19C), TOBN(0xB4B6663C, 0x87A8E61D),
};
static const BN_ULONG dh2048_256_g[] = {
    TOBN(0x6CC41659, 0x664B4C0F), TOBN(0xEF98C582, 0x5E2327CF),
    TOBN(0xD4795451, 0xD647D148), TOBN(0x90F00EF8, 0x2F630784),
    TOBN(0x1DB246C3, 0x184B523D), TOBN(0xCDC67EB6, 0xC7891428),
    TOBN(0x0DF92B52, 0x7FD02837), TOBN(0x64E0EC37, 0xB3353BBB),
    TOBN(0x57CD0915, 0xECD06E15), TOBN(0xDF016199, 0xB7D2BBD2),
    TOBN(0x052588B9, 0xC8484B1E), TOBN(0x13D3FE14, 0xDB2A3B73),
    TOBN(0xD182EA0A, 0xD052B985), TOBN(0xE83B9C80, 0xA4BD1BFF),
    TOBN(0xFB3F2E55, 0xDFC967C1), TOBN(0x767164E1, 0xB5045AF2),
    TOBN(0x6F2F9193, 0x1D14348F), TOBN(0x428EBC83, 0x64E67982),
    TOBN(0x82D6ED38, 0x8AC376D2), TOBN(0xAAB8A862, 0x777DE62A),
    TOBN(0xE9EC144B, 0xDDF463E5), TOBN(0xC77A57F2, 0x0196F931),
    TOBN(0x41000A65, 0xA55AE313), TOBN(0xC28CBB18, 0x901228F8),
    TOBN(0x7E8C6F62, 0xBC3773BF), TOBN(0x0C6B47B1, 0xBE3A6C1B),
    TOBN(0xAC0BB555, 0xFF4FED4A), TOBN(0x77BE463F, 0x10DBC150),
    TOBN(0x1A0BA125, 0x07F4793A), TOBN(0x21EF2054, 0x4CA7B18F),
    TOBN(0x60EDBD48, 0x2E775066), TOBN(0x73134D0B, 0x3FB32C9B),
};
static const BN_ULONG dh2048_256_q[] = {
    TOBN(0x64F5FBD3, 0xA308B0FE), TOBN(0x1EB3750B, 0x99B1A47D),
    TOBN(0x40129DA2, 0xB4479976), TOBN(0xA709A097, 0x8CF83642),
};

/* dh1024_safe_prime_1 is hard-coded in Apache httpd 2.2,
 * modules/ssl/ssl_engine_dh.c. */
static const BN_ULONG dh1024_safe_prime_1[] = {
    TOBN(0x24218EB3, 0xE7393E0F), TOBN(0xE2BD68B0, 0x7DE0F4D6),
    TOBN(0x88AEAA74, 0x07DD62DB), TOBN(0x9DDD3305, 0x10EA9FCC),
    TOBN(0x74087D15, 0xA7DBCA78), TOBN(0x78045B07, 0xDAE88600),
    TOBN(0x1AAD3B72, 0x33168A46), TOBN(0x7BEDDCFD, 0xFF590137),
    TOBN(0x7A635E81, 0xFE324A46), TOBN(0x420B2A29, 0x5AC179BA),
    TOBN(0x177E16D5, 0x13B4B4D7), TOBN(0x639C72FB, 0x849F912E),
    TOBN(0x98BCE951, 0xB88174CB), TOBN(0xA45F520B, 0x0C84D239),
    TOBN(0x4AFD0AD5, 0x36D693D3), TOBN(0xCBBBDC19, 0xD67DE440),
};

/* dh1024_safe_prime_2 is hard-coded in nginx,
 * src/event/ngx_event_openssl.c. */
static const BN_ULONG dh1024_safe_prime_2[] = {
    TOBN(0xCFE16B9B, 0x071DF045), TOBN(0x146757DA, 0x88D0F65D),
    TOBN(0x58FAFD49, 0x4A63AB1E), TOBN(0xEF9EA027, 0x35D8CECE),
    TOBN(0x70CC9A50, 0x25ECE662), TOBN(0x81DC2CA7, 0xF29BA5DF),
    TOBN(0xF7D36CC8, 0x8F68B076), TOBN(0xA757E304, 0x60E91A92),
    TOBN(0x9BE67780, 0x87A2BC04), TOBN(0xA5FDF1D2, 0xBEECA565),
    TOBN(0x922614C5, 0x5CCBBAA8), TOBN(0xE710800C, 0x6C030276),
    TOBN(0x0FB3504C, 0x08EED4EB), TOBN(0x68B42D4B, 0xD958A3F5),
    TOBN(0x80E9CFDB, 0x7C43FCF5), TOBN(0xD8467490, 0xBBBC2DCA),
};

/* dh1024_safe_prime_3 is offered as a parameter by several high-traffic sites,
 * including mozilla.org, as of Jan 2015. */
static const BN_ULONG dh1024_safe_prime_3[] = {
    TOBN(0x349E721B, 0x671746AE), TOBN(0xD75E93B2, 0x258A0655),
    TOBN(0x25592EB6, 0xD425E6FB), TOBN(0xBF7CDD9A, 0x0C46AB04),
    TOBN(0x28968680, 0x0AD0BC99), TOBN(0xD0B7EB49, 0xF53907FB),
    TOBN(0xEBC85C1D, 0x202EABB3), TOBN(0x364D8C71, 0x3129C693),
    TOBN(0x2D46F195, 0x53728351), TOBN(0x8C76CC85, 0xDF326DD6),
    TOBN(0x9188E24E, 0xF898B3F9), TOBN(0x2855DFD2, 0x95EFB13C),
    TOBN(0x7B2241FE, 0x1F5DAC48), TOBN(0x99A13D9F, 0x117B6BF7),
    TOBN(0x3A3468C7, 0x0F97CDDA), TOBN(0x74A8297B, 0xC9BBF5F7)};

/* dh1024_safe_prime_4 is hard-coded in Apache httpd 2.0,
 * modules/ssl/ssl_engine_dh.c. */
static const BN_ULONG dh1024_safe_prime_4[] = {
    TOBN(0x0DD5C86B, 0x5085E21F), TOBN(0xD823C650, 0x871538DF),
    TOBN(0x262E56A8, 0x125136F7), TOBN(0x839EB5DB, 0x974E9EF1),
    TOBN(0x1B13A63C, 0xEA9BAD99), TOBN(0x3D76E05E, 0x6044CF02),
    TOBN(0x1BAC9B5C, 0x611EBBBE), TOBN(0x4E5327DF, 0x3E371D79),
    TOBN(0x061CBC05, 0x000E6EDD), TOBN(0x20129B48, 0x2F971F3C),
    TOBN(0x3048D5A2, 0xA6EF09C4), TOBN(0xCBD523A6, 0xFA15A259),
    TOBN(0x4A79A770, 0x2A206490), TOBN(0x51BB055E, 0x91B78182),
    TOBN(0xBDD4798E, 0x7CF180C3), TOBN(0x495BE32C, 0xE6969D3D)};

static const BN_ULONG bn_two_data[] = {2};

#define STATIC_BIGNUM(x)                                     \
  {                                                          \
    (BN_ULONG *) x, sizeof(x) / sizeof(BN_ULONG),            \
        sizeof(x) / sizeof(BN_ULONG), 0, BN_FLG_STATIC_DATA \
  }

struct standard_parameters {
  BIGNUM p, q, g;
};

static const struct standard_parameters dh1024_160 = {
  STATIC_BIGNUM(dh1024_160_p),
  STATIC_BIGNUM(dh1024_160_q),
  STATIC_BIGNUM(dh1024_160_g),
};

static const struct standard_parameters dh2048_224 = {
  STATIC_BIGNUM(dh2048_224_p),
  STATIC_BIGNUM(dh2048_224_q),
  STATIC_BIGNUM(dh2048_224_g),
};

static const struct standard_parameters dh2048_256 = {
  STATIC_BIGNUM(dh2048_256_p),
  STATIC_BIGNUM(dh2048_256_q),
  STATIC_BIGNUM(dh2048_256_g),
};

static const BIGNUM dh1024_safe_prime[] = {
  STATIC_BIGNUM(dh1024_safe_prime_1),
  STATIC_BIGNUM(dh1024_safe_prime_2),
  STATIC_BIGNUM(dh1024_safe_prime_3),
  STATIC_BIGNUM(dh1024_safe_prime_4)
};

BIGNUM bn_two = STATIC_BIGNUM(bn_two_data);

static DH *get_standard_parameters(const struct standard_parameters *params,
                                   const ENGINE *engine) {
  DH *dh;

  dh = DH_new_method(engine);
  if (!dh) {
    return NULL;
  }

  dh->p = BN_dup(&params->p);
  dh->q = BN_dup(&params->q);
  dh->g = BN_dup(&params->g);
  if (!dh->p || !dh->q || !dh->g) {
    DH_free(dh);
    return NULL;
  }

  return dh;
}

DH *DH_get_1024_160(const ENGINE *engine) {
  return get_standard_parameters(&dh1024_160, engine);
}

DH *DH_get_2048_224(const ENGINE *engine) {
  return get_standard_parameters(&dh2048_224, engine);
}

DH *DH_get_2048_256(const ENGINE *engine) {
  return get_standard_parameters(&dh2048_256, engine);
}

void DH_check_standard_parameters(DH *dh) {
  int i;

  if (dh->p == NULL ||
      dh->g == NULL ||
      BN_num_bytes(dh->p) != (1024 / 8) ||
      BN_cmp(dh->g, &bn_two) != 0) {
    return;
  }

  for (i = 0; i < sizeof(dh1024_safe_prime) / sizeof(dh1024_safe_prime[0]);
       i++) {
    if (BN_cmp(dh->p, &dh1024_safe_prime[i]) == 0) {
      /* The well-known DH groups are known to have safe primes. In this case
       * we can safely reduce the size of the private key. */
      dh->priv_length = 161;
      break;
    }
  }
}
