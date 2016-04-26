/* Copyright (C) 1995-1998 Eric Young (eay@cryptsoft.com)
 * All rights reserved.
 *
 * This package is an SSL implementation written
 * by Eric Young (eay@cryptsoft.com).
 * The implementation was written so as to conform with Netscapes SSL.
 *
 * This library is free for commercial and non-commercial use as long as
 * the following conditions are aheared to.  The following conditions
 * apply to all code found in this distribution, be it the RC4, RSA,
 * lhash, DES, etc., code; not just the SSL code.  The SSL documentation
 * included with this distribution is covered by the same copyright terms
 * except that the holder is Tim Hudson (tjh@cryptsoft.com).
 *
 * Copyright remains Eric Young's, and as such any Copyright notices in
 * the code are not to be removed.
 * If this package is used in a product, Eric Young should be given attribution
 * as the author of the parts of the library used.
 * This can be in the form of a textual message at program startup or
 * in documentation (online or textual) provided with the package.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgement:
 *    "This product includes cryptographic software written by
 *     Eric Young (eay@cryptsoft.com)"
 *    The word 'cryptographic' can be left out if the rouines from the library
 *    being used are not cryptographic related :-).
 * 4. If you include any Windows specific code (or a derivative thereof) from
 *    the apps directory (application code) you must include an acknowledgement:
 *    "This product includes software written by Tim Hudson (tjh@cryptsoft.com)"
 *
 * THIS SOFTWARE IS PROVIDED BY ERIC YOUNG ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * The licence and distribution terms for any publically available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution licence
 * [including the GNU Public Licence.] */

#include <openssl/asn1.h>

#include <string.h>

#include <openssl/asn1t.h>
#include <openssl/bn.h>
#include <openssl/err.h>
#include <openssl/mem.h>


/* Custom primitive type for long handling. This converts between an ASN1_INTEGER
 * and a long directly.
 */


static int long_new(ASN1_VALUE **pval, const ASN1_ITEM *it);
static void long_free(ASN1_VALUE **pval, const ASN1_ITEM *it);

static int long_i2c(ASN1_VALUE **pval, unsigned char *cont, int *putype, const ASN1_ITEM *it);
static int long_c2i(ASN1_VALUE **pval, const unsigned char *cont, int len, int utype, char *free_cont, const ASN1_ITEM *it);
static int long_print(BIO *out, ASN1_VALUE **pval, const ASN1_ITEM *it, int indent, const ASN1_PCTX *pctx);

static const ASN1_PRIMITIVE_FUNCS long_pf = {
	NULL, 0,
	long_new,
	long_free,
	long_free,	/* Clear should set to initial value */
	long_c2i,
	long_i2c,
	long_print
};

ASN1_ITEM_start(LONG)
	ASN1_ITYPE_PRIMITIVE, V_ASN1_INTEGER, NULL, 0, &long_pf, ASN1_LONG_UNDEF, "LONG"
ASN1_ITEM_end(LONG)

ASN1_ITEM_start(ZLONG)
	ASN1_ITYPE_PRIMITIVE, V_ASN1_INTEGER, NULL, 0, &long_pf, 0, "ZLONG"
ASN1_ITEM_end(ZLONG)

static int long_new(ASN1_VALUE **pval, const ASN1_ITEM *it)
{
	*(long *)pval = it->size;
	return 1;
}

static void long_free(ASN1_VALUE **pval, const ASN1_ITEM *it)
{
	*(long *)pval = it->size;
}

static int long_i2c(ASN1_VALUE **pval, unsigned char *cont, int *putype, const ASN1_ITEM *it)
{
	long ltmp;
	unsigned long utmp;
	int clen, pad, i;
	/* this exists to bypass broken gcc optimization */
	char *cp = (char *)pval;

	/* use memcpy, because we may not be long aligned */
	memcpy(&ltmp, cp, sizeof(long));

	if(ltmp == it->size) return -1;
	/* Convert the long to positive: we subtract one if negative so
	 * we can cleanly handle the padding if only the MSB of the leading
	 * octet is set. 
	 */
	if(ltmp < 0) utmp = -ltmp - 1;
	else utmp = ltmp;
	clen = BN_num_bits_word(utmp);
	/* If MSB of leading octet set we need to pad */
	if(!(clen & 0x7)) pad = 1;
	else pad = 0;

	/* Convert number of bits to number of octets */
	clen = (clen + 7) >> 3;

	if(cont) {
		if(pad) *cont++ = (ltmp < 0) ? 0xff : 0;
		for(i = clen - 1; i >= 0; i--) {
			cont[i] = (unsigned char)(utmp & 0xff);
			if(ltmp < 0) cont[i] ^= 0xff;
			utmp >>= 8;
		}
	}
	return clen + pad;
}

static int long_c2i(ASN1_VALUE **pval, const unsigned char *cont, int len,
		    int utype, char *free_cont, const ASN1_ITEM *it)
{
	int neg, i;
	long ltmp;
	unsigned long utmp = 0;
	char *cp = (char *)pval;
	if(len > (int)sizeof(long)) {
		OPENSSL_PUT_ERROR(ASN1, ASN1_R_INTEGER_TOO_LARGE_FOR_LONG);
		return 0;
	}
	/* Is it negative? */
	if(len && (cont[0] & 0x80)) neg = 1;
	else neg = 0;
	utmp = 0;
	for(i = 0; i < len; i++) {
		utmp <<= 8;
		if(neg) utmp |= cont[i] ^ 0xff;
		else utmp |= cont[i];
	}
	ltmp = (long)utmp;
	if(neg) {
		ltmp++;
		ltmp = -ltmp;
	}
	if(ltmp == it->size) {
		OPENSSL_PUT_ERROR(ASN1, ASN1_R_INTEGER_TOO_LARGE_FOR_LONG);
		return 0;
	}
	memcpy(cp, &ltmp, sizeof(long));
	return 1;
}

static int long_print(BIO *out, ASN1_VALUE **pval, const ASN1_ITEM *it,
			int indent, const ASN1_PCTX *pctx)
	{
	return BIO_printf(out, "%ld\n", *(long *)pval);
	}
