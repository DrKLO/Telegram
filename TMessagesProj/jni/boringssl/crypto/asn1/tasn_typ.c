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

#include <openssl/asn1t.h>

/* Declarations for string types */

#define IMPLEMENT_ASN1_STRING_FUNCTIONS(sname) \
        IMPLEMENT_ASN1_TYPE(sname) \
        IMPLEMENT_ASN1_ENCODE_FUNCTIONS_fname(sname, sname, sname) \
        sname *sname##_new(void) \
        { \
                return ASN1_STRING_type_new(V_##sname); \
        } \
        void sname##_free(sname *x) \
        { \
                ASN1_STRING_free(x); \
        }

IMPLEMENT_ASN1_STRING_FUNCTIONS(ASN1_OCTET_STRING)
IMPLEMENT_ASN1_STRING_FUNCTIONS(ASN1_INTEGER)
IMPLEMENT_ASN1_STRING_FUNCTIONS(ASN1_ENUMERATED)
IMPLEMENT_ASN1_STRING_FUNCTIONS(ASN1_BIT_STRING)
IMPLEMENT_ASN1_STRING_FUNCTIONS(ASN1_UTF8STRING)
IMPLEMENT_ASN1_STRING_FUNCTIONS(ASN1_PRINTABLESTRING)
IMPLEMENT_ASN1_STRING_FUNCTIONS(ASN1_T61STRING)
IMPLEMENT_ASN1_STRING_FUNCTIONS(ASN1_IA5STRING)
IMPLEMENT_ASN1_STRING_FUNCTIONS(ASN1_GENERALSTRING)
IMPLEMENT_ASN1_STRING_FUNCTIONS(ASN1_UTCTIME)
IMPLEMENT_ASN1_STRING_FUNCTIONS(ASN1_GENERALIZEDTIME)
IMPLEMENT_ASN1_STRING_FUNCTIONS(ASN1_VISIBLESTRING)
IMPLEMENT_ASN1_STRING_FUNCTIONS(ASN1_UNIVERSALSTRING)
IMPLEMENT_ASN1_STRING_FUNCTIONS(ASN1_BMPSTRING)

IMPLEMENT_ASN1_TYPE(ASN1_NULL)
IMPLEMENT_ASN1_FUNCTIONS(ASN1_NULL)

IMPLEMENT_ASN1_TYPE(ASN1_OBJECT)

IMPLEMENT_ASN1_TYPE(ASN1_ANY)

/* Just swallow an ASN1_SEQUENCE in an ASN1_STRING */
IMPLEMENT_ASN1_TYPE(ASN1_SEQUENCE)

IMPLEMENT_ASN1_FUNCTIONS_fname(ASN1_TYPE, ASN1_ANY, ASN1_TYPE)

/* Multistring types */

IMPLEMENT_ASN1_MSTRING(ASN1_PRINTABLE, B_ASN1_PRINTABLE)
IMPLEMENT_ASN1_FUNCTIONS_name(ASN1_STRING, ASN1_PRINTABLE)

IMPLEMENT_ASN1_MSTRING(DISPLAYTEXT, B_ASN1_DISPLAYTEXT)
IMPLEMENT_ASN1_FUNCTIONS_name(ASN1_STRING, DISPLAYTEXT)

IMPLEMENT_ASN1_MSTRING(DIRECTORYSTRING, B_ASN1_DIRECTORYSTRING)
IMPLEMENT_ASN1_FUNCTIONS_name(ASN1_STRING, DIRECTORYSTRING)

/* Three separate BOOLEAN type: normal, DEFAULT TRUE and DEFAULT FALSE */
IMPLEMENT_ASN1_TYPE_ex(ASN1_BOOLEAN, ASN1_BOOLEAN, -1)
IMPLEMENT_ASN1_TYPE_ex(ASN1_TBOOLEAN, ASN1_BOOLEAN, 1)
IMPLEMENT_ASN1_TYPE_ex(ASN1_FBOOLEAN, ASN1_BOOLEAN, 0)

/* Special, OCTET STRING with indefinite length constructed support */

IMPLEMENT_ASN1_TYPE_ex(ASN1_OCTET_STRING_NDEF, ASN1_OCTET_STRING, ASN1_TFLG_NDEF)

ASN1_ITEM_TEMPLATE(ASN1_SEQUENCE_ANY) =
        ASN1_EX_TEMPLATE_TYPE(ASN1_TFLG_SEQUENCE_OF, 0, ASN1_SEQUENCE_ANY, ASN1_ANY)
ASN1_ITEM_TEMPLATE_END(ASN1_SEQUENCE_ANY)

ASN1_ITEM_TEMPLATE(ASN1_SET_ANY) =
        ASN1_EX_TEMPLATE_TYPE(ASN1_TFLG_SET_OF, 0, ASN1_SET_ANY, ASN1_ANY)
ASN1_ITEM_TEMPLATE_END(ASN1_SET_ANY)

IMPLEMENT_ASN1_ENCODE_FUNCTIONS_const_fname(ASN1_SEQUENCE_ANY, ASN1_SEQUENCE_ANY, ASN1_SEQUENCE_ANY)
IMPLEMENT_ASN1_ENCODE_FUNCTIONS_const_fname(ASN1_SEQUENCE_ANY, ASN1_SET_ANY, ASN1_SET_ANY)
